package com.ai.assistance.operit.data.db

import androidx.room.*
import com.ai.assistance.operit.data.model.*

@Entity(tableName="home_world")
data class HomeRow(@PrimaryKey val id:String,val initialJson:String,val stateJson:String,val version:Long,val createdAt:Long,val updatedAt:Long) {
    fun scope()=HomeScope(id)
    fun value():HomeState=HomeState.decode(stateJson).also {
        require(it.scope==scope()&&it.version==version&&it.createdAt==createdAt&&it.updatedAt==updatedAt)
    }
    fun changed(state:HomeState)=copy(stateJson=state.encode(),version=state.version,updatedAt=state.updatedAt).also {it.value()}
    companion object {
        fun from(state:HomeState):HomeRow {
            require(state.version==1L)
            return HomeRow(state.scope.homeId,state.encode(),state.encode(),state.version,state.createdAt,state.updatedAt).also {it.value()}
        }
    }
}

@Entity(tableName="home_events",foreignKeys=[ForeignKey(entity=HomeRow::class,parentColumns=["id"],childColumns=["homeId"],onDelete=ForeignKey.CASCADE)],
    indices=[Index("homeId"),Index(value=["homeId","beforeVersion"],unique=true)])
data class HomeCommandRow(@PrimaryKey val commandId:String,val homeId:String,val beforeVersion:Long,
    val actionJson:String,val description:String,val createdAt:Long) {
    fun scope()=HomeScope(homeId)
    fun action():HomeAction {
        PlaySessions.id(commandId);scope()
        require(beforeVersion in 1 until Long.MAX_VALUE&&createdAt in 0..HomeState.MAX_TIME)
        PlayJson.text(description,256);return HomeActionCodec.decode(actionJson)
    }
    fun matches(scope:HomeScope,version:Long,action:HomeAction)=scope()==scope&&beforeVersion==version&&action()==action
}

object HomeStorageValidation {
    /** Replay from the initial shared world; restore never executes rewards a second time. */
    fun validate(row:HomeRow,commands:List<HomeCommandRow>) {
        val saved=row.value()
        require(saved.version-1==commands.size.toLong()&&commands.map {it.commandId}.distinct().size==commands.size)
        var state=HomeState.decode(row.initialJson)
        require(state.scope==row.scope()&&state.version==1L&&state.createdAt==row.createdAt&&state.updatedAt==row.createdAt)
        val rewards=mutableSetOf<Pair<String,HomeAdventureReward>>()
        commands.sortedBy {it.beforeVersion}.forEach {command->
            require(command.scope()==row.scope())
            val action=command.action()
            if(action is HomeAction.AdventureReward)require(rewards.add(action.sourceId to action.reward))
            val change=state.apply(command.scope(),command.beforeVersion,command.createdAt,action)
            require(change.description==command.description);state=change.state
        }
        require(state.encode()==saved.encode())
    }
}

@Dao interface HomeDao {
    @Query("SELECT * FROM home_world WHERE id='shared-home'") suspend fun home():HomeRow?
    @Query("SELECT * FROM home_events WHERE homeId=:home ORDER BY beforeVersion") suspend fun commands(home:String):List<HomeCommandRow>
    @Query("SELECT * FROM home_events WHERE homeId=:home AND actionJson NOT LIKE '%\"type\":\"move\"%' ORDER BY beforeVersion DESC LIMIT 20") suspend fun recent(home:String):List<HomeCommandRow>
    @Query("SELECT * FROM home_events WHERE commandId=:id") suspend fun command(id:String):HomeCommandRow?
    @Insert suspend fun insert(row:HomeRow)
    @Insert suspend fun insert(row:HomeCommandRow)
    @Query("UPDATE home_world SET stateJson=:json,version=:next,updatedAt=:now WHERE id=:home AND version=:previous")
    suspend fun update(home:String,previous:Long,json:String,next:Long,now:Long):Int
}
