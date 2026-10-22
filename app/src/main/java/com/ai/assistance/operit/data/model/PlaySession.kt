package com.ai.assistance.operit.data.model

data class PlayAccessScope(val role:RoleAccessScope,val chatId:String,val activityId:String)
enum class PlayStatus { ACTIVE, PAUSED, COMPLETE, INTERRUPTED }

/** The state is confirmed progress; an unfinished response must never be inserted into it. */
data class PlaySession(val scope:PlayAccessScope,val title:String,val state:PlayState,
    val version:Long=1,val status:PlayStatus=PlayStatus.ACTIVE,val requestId:String?=null)

data class PlayRequestIdentity(val scope:PlayAccessScope,val version:Long)

object PlaySessions {
    internal fun id(value:String) {PlayJson.text(value,128);require(value.none(Char::isISOControl))}
    fun validate(session:PlaySession) {
        try {
            listOf(session.scope.role.roleId,session.scope.role.spaceId,session.scope.chatId,session.scope.activityId).forEach(::id)
            PlayJson.text(session.title,120);require(session.version>0)
            require((session.status==PlayStatus.COMPLETE)==(session.state.result!=PlayResult.ACTIVE))
            session.requestId?.let {id(it);require(session.status==PlayStatus.ACTIVE&&canRequest(session.state))}
            require(session.state.encode().toByteArray(Charsets.UTF_8).size<=PlayJson.MAX_BYTES)
        }catch(_:IllegalArgumentException){throw ChatFailure("PLAY_FORMAT")}
    }
    fun checkOwner(session:PlaySession,scope:PlayAccessScope,role:RoleEntity,chat:ChatEntity) {
        validate(session)
        if(session.scope!=scope||role.scope()!=scope.role||chat.id!=scope.chatId||chat.roleId!=role.id)throw ChatFailure("ACCESS")
    }
    fun canRequest(state:PlayState)=state.turn==PlaySide.ROLE&&state.result==PlayResult.ACTIVE&&
        !(state is MemoryPairsState&&state.open.size==2)&&!(state is JourneyState&&!state.canAcceptReply())&&
        !(state is AdventurePlayState&&!state.game.canAcceptReply())

    private fun check(session:PlaySession,scope:PlayAccessScope,version:Long) {
        validate(session)
        if(session.scope!=scope)throw ChatFailure("ACCESS")
        if(session.version!=version)throw ChatFailure("PLAY_CHANGED")
        if(version==Long.MAX_VALUE)throw ChatFailure("PLAY_LIMIT")
    }
    fun userAction(session:PlaySession,scope:PlayAccessScope,version:Long,action:PlayAction):PlaySession {
        check(session,scope,version)
        if(session.status!=PlayStatus.ACTIVE||session.requestId!=null)throw ChatFailure("PLAY_CHANGED")
        val permitted=when(action) {
            is PlayAction.Place->action.side==PlaySide.USER
            is PlayAction.Flip->action.side==PlaySide.USER
            is PlayAction.Narrate->action.side==PlaySide.USER
            PlayAction.HidePair->true
            PlayAction.EndStory->session.state is DateStoryState||session.state is JourneyState
            is PlayAction.Visit->session.state is JourneyState
            is PlayAction.Word->action.move.side==PlaySide.USER
            is PlayAction.Card->action.move.side==PlaySide.USER
            is PlayAction.Chess->action.side==PlaySide.USER
            is PlayAction.ResignChess->action.side==PlaySide.USER
            is PlayAction.Adventure->action.move !is AdventureAction.Say||action.move.side==PlaySide.USER
        }
        if(!permitted)throw ChatFailure("PLAY_ACTION")
        return advanced(session,session.state.apply(action))
    }
    fun begin(session:PlaySession,scope:PlayAccessScope,version:Long,requestId:String):PlaySession {
        check(session,scope,version)
        if(version>=Long.MAX_VALUE-1)throw ChatFailure("PLAY_LIMIT")
        if(session.status!=PlayStatus.ACTIVE||session.requestId!=null||!canRequest(session.state))throw ChatFailure("PLAY_CHANGED")
        try {id(requestId)}catch(_:IllegalArgumentException){throw ChatFailure("PLAY_FORMAT")}
        return session.copy(version=version+1,requestId=requestId).also(::validate)
    }
    /** Compare-and-set is also required by the Room transaction that persists this result. */
    fun accepts(session:PlaySession,snapshot:RequestSnapshot,frozenState:String):Boolean {
        val binding=snapshot.play?:return false
        return session.scope==binding.scope&&session.version==binding.version&&session.requestId==snapshot.requestId&&
            session.status==PlayStatus.ACTIVE&&canRequest(session.state)&&session.state.encode()==frozenState&&
            snapshot.group==null&&snapshot.scope==session.scope.role&&snapshot.role.scope()==snapshot.scope&&
            snapshot.chatId==session.scope.chatId&&snapshot.tools.isEmpty()
    }
    fun commit(session:PlaySession,snapshot:RequestSnapshot,frozenState:String,action:PlayAction):PlaySession {
        validate(session)
        if(!accepts(session,snapshot,frozenState))throw ChatFailure("PLAY_CHANGED")
        val permitted=when(action) {
            is PlayAction.Place->action.side==PlaySide.ROLE
            is PlayAction.Flip->action.side==PlaySide.ROLE
            is PlayAction.Narrate->action.side==PlaySide.ROLE
            is PlayAction.Word->action.move.side==PlaySide.ROLE
            is PlayAction.Card->action.move.side==PlaySide.ROLE
            is PlayAction.Chess->action.side==PlaySide.ROLE
            is PlayAction.ResignChess->action.side==PlaySide.ROLE
            is PlayAction.Adventure->action.move is AdventureAction.Say&&action.move.side==PlaySide.ROLE
            else->false
        }
        if(!permitted)throw ChatFailure("PLAY_ACTION")
        return advanced(session,session.state.apply(action))
    }
    fun finishFailed(session:PlaySession,snapshot:RequestSnapshot,frozenState:String,interrupted:Boolean):PlaySession {
        validate(session)
        if(!accepts(session,snapshot,frozenState))throw ChatFailure("PLAY_CHANGED")
        return changed(session,if(interrupted)PlayStatus.INTERRUPTED else PlayStatus.ACTIVE)
    }
    fun pause(session:PlaySession,scope:PlayAccessScope,version:Long):PlaySession {
        check(session,scope,version)
        if(session.status==PlayStatus.COMPLETE)throw ChatFailure("PLAY_CHANGED")
        return changed(session,PlayStatus.PAUSED)
    }
    fun resume(session:PlaySession,scope:PlayAccessScope,version:Long):PlaySession {
        check(session,scope,version)
        if(session.status !in setOf(PlayStatus.PAUSED,PlayStatus.INTERRUPTED))throw ChatFailure("PLAY_CHANGED")
        return changed(session,PlayStatus.ACTIVE)
    }
    fun recover(session:PlaySession):PlaySession {
        validate(session)
        return if(session.requestId==null)session else changed(session,PlayStatus.INTERRUPTED)
    }
    private fun changed(session:PlaySession,status:PlayStatus):PlaySession {
        if(session.version==Long.MAX_VALUE)throw ChatFailure("PLAY_LIMIT")
        return session.copy(version=session.version+1,status=status,requestId=null).also(::validate)
    }
    private fun advanced(session:PlaySession,state:PlayState):PlaySession {
        if(session.version==Long.MAX_VALUE)throw ChatFailure("PLAY_LIMIT")
        return session.copy(version=session.version+1,state=state,requestId=null,
            status=if(state.result==PlayResult.ACTIVE)PlayStatus.ACTIVE else PlayStatus.COMPLETE).also(::validate)
    }
}
