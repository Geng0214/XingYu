package com.ai.assistance.operit.data.db

import androidx.room.withTransaction
import com.ai.assistance.operit.data.model.*
import kotlinx.coroutines.flow.map
import java.util.UUID

data class HomeView(val home:HomeRow,val recent:List<HomeCommandRow>)
data class HomeReceipt(val home:HomeRow,val command:HomeCommandRow,val repeated:Boolean)
data class HomeRewardPreview(val home:HomeScope,val homeVersion:Long,val source:PlayAccessScope,val sourceVersion:Long,
    val sourceTitle:String,val action:HomeAction.AdventureReward,val claimed:Boolean)

class HomeRepository(private val db:AppDatabase,private val clock:()->Long={System.currentTimeMillis()}) {
    private val dao get()=db.home()
    private suspend fun read(scope:HomeScope)=dao.home()?.takeIf {it.scope()==scope}?.also {it.value()}?:throw ChatFailure("HOME_REQUIRED")
    suspend fun ensure():HomeRow=db.withTransaction {
        dao.home()?.let {it.value();return@withTransaction it}
        val roles=db.dao().allRoles().toMutableList()
        if(roles.isEmpty()) {
            val companion=RoleEntity(UUID.randomUUID().toString(),"小伴","你是用户的聊天伙伴，与用户一起在家园生活。自然、温暖地交流。")
            db.dao().saveRole(companion);roles+=companion
        }
        val home=HomeState.create("我们的家园",clock(),roles.map {it.scope()})
        HomeRow.from(home).also {dao.insert(it)}
    }
    suspend fun current():HomeView?=db.withTransaction {
        dao.home()?.let {row->row.value();HomeView(row,dao.recent(row.id).onEach {require(it.scope()==row.scope());it.action()})}
    }
    fun observe()=db.invalidationTracker.createFlow("home_world","home_events","roles").map {current()}
    suspend fun history(scope:HomeScope):HomeView=db.withTransaction {
        val row=read(scope);val commands=dao.commands(row.id);HomeStorageValidation.validate(row,commands);HomeView(row,commands)
    }
    suspend fun apply(scope:HomeScope,version:Long,commandId:String,action:HomeAction):HomeReceipt=db.withTransaction {
        if(action is HomeAction.AdventureReward||action is HomeAction.Join||action is HomeAction.Leave)throw ChatFailure("ACCESS")
        PlaySessions.id(commandId);val row=read(scope)
        dao.command(commandId)?.let {existing->
            if(!existing.matches(scope,version,action))throw ChatFailure("HOME_COMMAND_CONFLICT")
            return@withTransaction HomeReceipt(row,existing,true)
        }
        persist(row,version,commandId,action)
    }
    /** Stage a player's command from the latest world version inside the same Room write transaction.
     * Position saves and role membership changes cannot advance the version between staging and commit. */
    suspend fun submit(commandId:String,action:HomeAction,stage:(HomeScope,Long)->Unit):HomeReceipt=db.withTransaction {
        if(action is HomeAction.AdventureReward||action is HomeAction.Join||action is HomeAction.Leave)throw ChatFailure("ACCESS")
        PlaySessions.id(commandId);val row=read(HomeScope())
        require(dao.command(commandId)==null)
        stage(row.scope(),row.version)
        persist(row,row.version,commandId,action)
    }
    /** Called in the role transaction, so a saved role and its world resident cannot disagree. */
    internal suspend fun join(role:RoleAccessScope) {
        val row=dao.home()?:return
        if(db.dao().role(role.roleId)?.scope()!=role)throw ChatFailure("ACCESS")
        val existing=row.value().residents[role.roleId]
        if(existing!=null){require(existing.role==role);return}
        persist(row,row.version,UUID.randomUUID().toString(),HomeAction.Join(role))
    }
    internal suspend fun leave(role:RoleAccessScope) {
        val row=dao.home()?:return;val resident=row.value().residents[role.roleId]?:return
        require(resident.role==role)
        persist(row,row.version,UUID.randomUUID().toString(),HomeAction.Leave(role.roleId))
    }
    suspend fun move(action:HomeAction.Move,checkAccess:()->Unit):HomeView=db.withTransaction {
        checkAccess();val row=read(HomeScope());val saved=row.value()
        if(action.player!=saved.player||action.residents!=saved.residents.mapValues {it.value.tile}) {
            persist(row,row.version,UUID.randomUUID().toString(),action)
        }
        checkAccess();val next=read(HomeScope());HomeView(next,dao.recent(next.id))
    }
    private suspend fun persist(row:HomeRow,version:Long,id:String,action:HomeAction):HomeReceipt {
        val canonical=HomeActionCodec.encode(action);HomeActionCodec.decode(canonical)
        val change=row.value().apply(row.scope(),version,clock(),action);val next=row.changed(change.state)
        val command=HomeCommandRow(id,row.id,version,canonical,change.description,next.updatedAt);command.action()
        dao.insert(command)
        if(dao.update(row.id,version,next.stateJson,next.version,next.updatedAt)!=1)throw ChatFailure("HOME_CHANGED")
        return HomeReceipt(next,command,false)
    }
    private suspend fun rewardCommands(row:HomeRow):List<Pair<HomeCommandRow,HomeAction.AdventureReward>> {
        val commands=dao.commands(row.id);HomeStorageValidation.validate(row,commands)
        return commands.mapNotNull {c->(c.action() as? HomeAction.AdventureReward)?.let {c to it}}
    }
    suspend fun rewardPreview(source:PlayAccessScope,sourceVersion:Long,reward:HomeAdventureReward):HomeRewardPreview=db.withTransaction {
        if(db.dao().role(source.role.roleId)?.scope()!=source.role)throw ChatFailure("ACCESS")
        val home=read(HomeScope())
        if(home.value().residents[source.role.roleId]?.role!=source.role)throw ChatFailure("ACCESS")
        val activity=PlayRepository(db).timeline(source).activity
        if(activity.version!=sourceVersion)throw ChatFailure("PLAY_CHANGED")
        val game=(activity.value().state as? AdventurePlayState)?.game?:throw ChatFailure("ACCESS")
        if(!reward.earned(game))throw ChatFailure("HOME_REWARD_UNEARNED")
        val action=HomeAction.AdventureReward(source.activityId,reward,HomeAdventureProof.from(game),source.role)
        HomeRewardPreview(home.scope(),home.version,source,sourceVersion,activity.title,action,
            rewardCommands(home).any {it.second.sourceId==source.activityId&&it.second.reward==reward})
    }
    suspend fun claimReward(preview:HomeRewardPreview,commandId:String):HomeReceipt=db.withTransaction {
        PlaySessions.id(commandId)
        if(preview.action.sourceId!=preview.source.activityId||preview.action.sourceRole!=preview.source.role||db.dao().role(preview.source.role.roleId)?.scope()!=preview.source.role)throw ChatFailure("ACCESS")
        val home=read(preview.home);val commands=rewardCommands(home)
        dao.command(commandId)?.let {existing->
            if(!existing.matches(preview.home,preview.homeVersion,preview.action))throw ChatFailure("HOME_COMMAND_CONFLICT")
            return@withTransaction HomeReceipt(home,existing,true)
        }
        commands.firstOrNull {it.second.sourceId==preview.source.activityId&&it.second.reward==preview.action.reward}?.let {return@withTransaction HomeReceipt(home,it.first,true)}
        if(rewardPreview(preview.source,preview.sourceVersion,preview.action.reward)!=preview)throw ChatFailure("HOME_CHANGED")
        persist(home,preview.homeVersion,commandId,preview.action)
    }
}
