package com.ai.assistance.operit.data.db

import com.ai.assistance.operit.api.chat.PlayGenerations
import com.ai.assistance.operit.data.model.*
import org.json.JSONArray
import org.json.JSONObject

object PlayStorageValidation {
    const val MAX_REQUESTS=2048
    fun validate(row:PlaySessionRow,requests:List<PlayRequestRow>) {
        val session=row.value()
        require(requests.size<=MAX_REQUESTS&&requests.map {it.requestId}.distinct().size==requests.size)
        require(requests.map {it.activityVersion}.distinct().size==requests.size&&requests.map {it.assistantId}.distinct().size==requests.size)
        val pending=requests.filter {it.state in activeStates}
        require(if(session.requestId==null)pending.isEmpty()else pending.size==1&&pending.single().requestId==session.requestId)
        var complete=0
        requests.forEach {q->
            q.validate();require(q.scope()==session.scope&&q.createdAt in row.createdAt..row.updatedAt)
            val before=PlayStates.decode(q.beforeState)
            require(prefix(before,session.state))
            if(q.state in activeStates)require(q.activityVersion==session.version&&q.beforeState==session.state.encode())
            else require(q.activityVersion<session.version)
            val sourceText=JSONArray(q.contextJson).getJSONObject(1).getString("content")
            // The nested source is JSON carried in a string; the outer depth check cannot validate it.
            StrictJson(sourceText).validate()
            val source=JSONObject(sourceText)
            PlayJson.keys(source,"participant","activity")
            val participant=source.getJSONObject("participant");PlayJson.keys(participant,"name","persona")
            PlayJson.text(PlayJson.string(participant,"name"),40);PlayJson.text(PlayJson.string(participant,"persona"),8000,true)
            require(canonical(source.get("activity"))==canonical(before.observation()))
            if(q.state==MessageState.COMPLETE) {
                complete++
                val after=before.apply(PlayGenerations.decode(before,q.content).action)
                require(prefix(after,session.state))
            }
        }
        require(complete==roleActions(session.state))
        // Two completed attempts cannot claim the same role action in the saved history.
        require(requests.filter {it.state==MessageState.COMPLETE}.map {roleActions(PlayStates.decode(it.beforeState))}.distinct().size==complete)
    }
    private fun roleActions(s:PlayState):Int=when(s) {
        is AdventurePlayState->s.game.roleActionCount
        is XiangqiPlayState->s.roleActionCount
        is WordPlayState->s.game.moves.count {it.side==PlaySide.ROLE}
        is CardPlayState->s.game.moves.count {it.side==PlaySide.ROLE}
        is GomokuState->s.moves.count {it.side==PlaySide.ROLE}
        is MemoryPairsState->s.actions.count {it is PlayAction.Flip&&it.side==PlaySide.ROLE}
        is DateStoryState->s.turns.count {it.side==PlaySide.ROLE}
        is JourneyState->s.events.count {it is PlayAction.Narrate&&it.side==PlaySide.ROLE}
    }
    private fun prefix(before:PlayState,after:PlayState):Boolean=when {
        before is AdventurePlayState&&after is AdventurePlayState->before.game.isPrefixOf(after.game)
        before is XiangqiPlayState&&after is XiangqiPlayState->before.isPrefixOf(after)
        before is WordPlayState&&after is WordPlayState->before.game.isPrefixOf(after.game)
        before is CardPlayState&&after is CardPlayState->before.game.isPrefixOf(after.game)
        before is GomokuState&&after is GomokuState->before.first==after.first&&after.moves.take(before.moves.size)==before.moves
        before is MemoryPairsState&&after is MemoryPairsState->before.first==after.first&&after.actions.take(before.actions.size)==before.actions&&
            JSONObject(before.encode()).getJSONArray("deck").toString()==JSONObject(after.encode()).getJSONArray("deck").toString()
        before is DateStoryState&&after is DateStoryState->before.outfits==after.outfits&&before.place==after.place&&before.premise==after.premise&&after.turns.take(before.turns.size)==before.turns&&(!before.ended||after.ended)
        before is JourneyState&&after is JourneyState->before.isPrefixOf(after)
        else->false
    }
    internal fun canonical(value:Any):String=when(value) {
        is JSONObject->value.keys().asSequence().sorted().joinToString(",","{","}"){JSONObject.quote(it)+":"+canonical(value.get(it))}
        is JSONArray->(0 until value.length()).joinToString(",","[","]"){canonical(value.get(it))}
        is String->JSONObject.quote(value)
        else->value.toString()
    }
}
