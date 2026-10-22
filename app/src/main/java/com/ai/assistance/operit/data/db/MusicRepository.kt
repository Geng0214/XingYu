package com.ai.assistance.operit.data.db

import androidx.room.*
import com.ai.assistance.operit.data.model.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface MusicDao {
    @Query("SELECT t.* FROM music_tracks t JOIN roles r ON r.id=t.roleId AND r.spaceId=t.spaceId WHERE r.id=:roleId AND r.spaceId=:spaceId ORDER BY t.createdAt DESC,t.id")
    fun tracks(roleId:String,spaceId:String):Flow<List<MusicTrack>>
    @Query("SELECT t.* FROM music_tracks t JOIN roles r ON r.id=t.roleId AND r.spaceId=t.spaceId WHERE r.id=:roleId AND r.spaceId=:spaceId AND t.id=:id")
    suspend fun track(roleId:String,spaceId:String,id:String):MusicTrack?
    @Query("SELECT s.* FROM music_sessions s JOIN roles r ON r.id=s.roleId AND r.spaceId=s.spaceId JOIN chats c ON c.id=s.chatId AND c.roleId=r.id WHERE r.id=:roleId AND r.spaceId=:spaceId ORDER BY s.updatedAt DESC,s.id")
    fun sessions(roleId:String,spaceId:String):Flow<List<MusicSession>>
    @Query("SELECT s.* FROM music_sessions s JOIN roles r ON r.id=s.roleId AND r.spaceId=s.spaceId JOIN chats c ON c.id=s.chatId AND c.roleId=r.id WHERE r.id=:roleId AND r.spaceId=:spaceId AND c.id=:chatId AND s.id=:id")
    suspend fun session(roleId:String,spaceId:String,chatId:String,id:String):MusicSession?
    @Query("SELECT COUNT(*) FROM music_tracks WHERE roleId=:roleId") suspend fun trackCount(roleId:String):Int
    @Query("SELECT COUNT(*) FROM music_sessions WHERE trackId=:id") suspend fun references(id:String):Int
    @Query("SELECT DISTINCT sourceUri FROM music_tracks WHERE sourceUri IS NOT NULL") suspend fun sources():List<String>
    @Insert suspend fun insert(track:MusicTrack)
    @Insert suspend fun insert(session:MusicSession)
    @Update suspend fun update(track:MusicTrack):Int
    @Update suspend fun update(session:MusicSession):Int
    @Delete suspend fun delete(track:MusicTrack):Int
    @Delete suspend fun delete(session:MusicSession):Int
    @Query("UPDATE music_sessions SET state='paused',playbackToken=NULL,version=version+1 WHERE playbackToken IS NOT NULL") suspend fun recover():Int
}

class MusicRepository(private val db:AppDatabase) {
    private suspend fun checked(scope:RoleAccessScope,chat:String,id:String,version:Long?=null):Pair<MusicSession,MusicTrack> {
        val s=db.music().session(scope.roleId,scope.spaceId,chat,id)?:throw ChatFailure("ACCESS")
        if(version!=null&&s.version!=version)throw ChatFailure("ACTIVITY_CHANGED")
        val t=db.music().track(scope.roleId,scope.spaceId,s.trackId)?:throw ChatFailure("ACCESS")
        LocalMusic.validate(s,t);return s to t
    }
    suspend fun add(track:MusicTrack)=db.withTransaction {
        if(db.dao().role(track.roleId)?.spaceId!=track.spaceId)throw ChatFailure("ACCESS")
        if(db.music().trackCount(track.roleId)>=LocalMusic.MAX_TRACKS)throw ChatFailure("MUSIC_LIMIT")
        LocalMusic.validate(track);db.music().insert(track)
    }
    suspend fun relink(track:MusicTrack,uri:String)=db.withTransaction {
        val old=db.music().track(track.roleId,track.spaceId,track.id)?:throw ChatFailure("ACCESS")
        if(old!=track)throw ChatFailure("ACTIVITY_CHANGED")
        val next=old.copy(sourceUri=uri,version=old.version+1);LocalMusic.validate(next);check(db.music().update(next)==1)
    }
    suspend fun create(scope:RoleAccessScope,chat:String,trackId:String)=db.withTransaction {
        db.dao().chat(scope.roleId,scope.spaceId,chat)?:throw ChatFailure("ACCESS")
        val t=db.music().track(scope.roleId,scope.spaceId,trackId)?:throw ChatFailure("ACCESS")
        MusicSession(UUID.randomUUID().toString(),scope.roleId,scope.spaceId,chat,trackId).also {LocalMusic.validate(it,t);db.music().insert(it)}
    }
    suspend fun begin(item:MusicSession,token:String)=db.withTransaction {
        require(token.isNotBlank())
        val (old,t)=checked(RoleAccessScope(item.roleId,item.spaceId),item.chatId,item.id,item.version)
        if(old.state!="paused"||t.sourceUri==null)throw ChatFailure("MUSIC_SOURCE")
        val next=old.copy(state="playing",playbackToken=token,version=old.version+1)
        LocalMusic.validate(next,t);check(db.music().update(next)==1);next to t
    }
    // The token is owned by one playback run. A cancelled run must never update its successor.
    suspend fun progress(item:MusicSession,token:String,position:Long,finish:Boolean=false,completed:Boolean=false):Boolean=db.withTransaction {
        val old=db.music().session(item.roleId,item.spaceId,item.chatId,item.id)?:return@withTransaction false
        if(old.state!="playing"||old.playbackToken!=token)return@withTransaction false
        val t=db.music().track(item.roleId,item.spaceId,old.trackId)?:throw ChatFailure("ACCESS")
        val next=old.copy(positionMs=position,state=if(finish){if(completed)"completed" else "paused"}else"playing",
            playbackToken=if(finish)null else token,version=if(finish)old.version+1 else old.version,updatedAt=maxOf(old.updatedAt,System.currentTimeMillis()))
        LocalMusic.validate(next,t);check(db.music().update(next)==1);true
    }
    suspend fun change(item:MusicSession,position:Long?=null,note:String?=null)=db.withTransaction {
        val (old,t)=checked(RoleAccessScope(item.roleId,item.spaceId),item.chatId,item.id,item.version)
        if(old.state=="playing")throw ChatFailure("MUSIC_BUSY")
        val next=old.copy(positionMs=position?:old.positionMs,note=note?:old.note,state=if(position!=null)"paused" else old.state,
            version=old.version+1,updatedAt=maxOf(old.updatedAt,System.currentTimeMillis()))
        LocalMusic.validate(next,t);check(db.music().update(next)==1)
    }
    suspend fun summary(item:MusicSession)=db.withTransaction {
        val (s,t)=checked(RoleAccessScope(item.roleId,item.spaceId),item.chatId,item.id,item.version)
        if(s.state=="playing")throw ChatFailure("MUSIC_BUSY")
        LocalMusic.summary(s,t)
    }
    suspend fun delete(item:MusicSession)=db.withTransaction {
        val (s,_)=checked(RoleAccessScope(item.roleId,item.spaceId),item.chatId,item.id,item.version)
        if(s.state=="playing")throw ChatFailure("MUSIC_BUSY")
        check(db.music().delete(s)==1)
    }
    suspend fun delete(track:MusicTrack)=db.withTransaction {
        val t=db.music().track(track.roleId,track.spaceId,track.id)?:throw ChatFailure("ACCESS")
        if(t!=track)throw ChatFailure("ACTIVITY_CHANGED")
        if(db.music().references(t.id)>0)throw ChatFailure("MUSIC_REFERENCED")
        check(db.music().delete(t)==1)
    }
}
