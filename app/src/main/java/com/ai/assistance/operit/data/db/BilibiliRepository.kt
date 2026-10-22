package com.ai.assistance.operit.data.db

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ai.assistance.operit.data.model.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

object BilibiliMigration:Migration(26,27) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.query("PRAGMA foreign_key_check").use {check(!it.moveToFirst()){ "Invalid ownership before online watching upgrade" }}
        db.execSQL("CREATE TABLE bilibili_watches (id TEXT NOT NULL PRIMARY KEY, roleId TEXT NOT NULL, spaceId TEXT NOT NULL, chatId TEXT NOT NULL, title TEXT NOT NULL, videoId TEXT NOT NULL, part INTEGER NOT NULL, positionMs INTEGER NOT NULL, durationMs INTEGER, note TEXT NOT NULL, playbackToken TEXT, version INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, FOREIGN KEY(roleId) REFERENCES roles(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(chatId) REFERENCES chats(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_bilibili_watches_roleId ON bilibili_watches(roleId)")
        db.execSQL("CREATE INDEX index_bilibili_watches_chatId ON bilibili_watches(chatId)")
        db.execSQL("CREATE INDEX index_bilibili_watches_roleId_spaceId ON bilibili_watches(roleId,spaceId)")
    }
}

@Dao interface BilibiliDao {
    @Query("SELECT w.* FROM bilibili_watches w JOIN roles r ON r.id=w.roleId AND r.spaceId=w.spaceId JOIN chats c ON c.id=w.chatId AND c.roleId=r.id WHERE r.id=:roleId AND r.spaceId=:spaceId ORDER BY w.updatedAt DESC,w.id")
    fun list(roleId:String,spaceId:String):Flow<List<BilibiliWatch>>
    @Query("SELECT w.* FROM bilibili_watches w JOIN roles r ON r.id=w.roleId AND r.spaceId=w.spaceId JOIN chats c ON c.id=w.chatId AND c.roleId=r.id WHERE r.id=:roleId AND r.spaceId=:spaceId AND c.id=:chatId AND w.id=:id")
    suspend fun get(roleId:String,spaceId:String,chatId:String,id:String):BilibiliWatch?
    @Insert suspend fun insert(w:BilibiliWatch)
    @Update suspend fun update(w:BilibiliWatch):Int
    @Delete suspend fun delete(w:BilibiliWatch):Int
    @Query("UPDATE bilibili_watches SET playbackToken=NULL,version=version+1 WHERE playbackToken IS NOT NULL") suspend fun recover():Int
}

class BilibiliRepository(private val db:AppDatabase) {
    private suspend fun checked(item:BilibiliWatch):BilibiliWatch {
        val w=db.bilibili().get(item.roleId,item.spaceId,item.chatId,item.id)?:throw ChatFailure("ACCESS")
        BilibiliWatching.validate(w)
        if(w!=item)throw ChatFailure("ACTIVITY_CHANGED")
        return w
    }
    suspend fun create(scope:RoleAccessScope,chat:String,title:String,video:BilibiliVideo)=db.withTransaction {
        db.dao().chat(scope.roleId,scope.spaceId,chat)?:throw ChatFailure("ACCESS")
        BilibiliWatch(UUID.randomUUID().toString(),scope.roleId,scope.spaceId,chat,title,video.videoId,video.part).also {BilibiliWatching.validate(it);db.bilibili().insert(it)}
    }
    suspend fun begin(item:BilibiliWatch,token:String)=db.withTransaction {
        val old=checked(item);if(old.playbackToken!=null)throw ChatFailure("MUSIC_BUSY")
        old.copy(playbackToken=token,version=old.version+1).also {BilibiliWatching.validate(it);check(db.bilibili().update(it)==1)}
    }
    suspend fun progress(item:BilibiliWatch,token:String,position:Long,duration:Long?,finish:Boolean=false):Boolean=db.withTransaction {
        val old=db.bilibili().get(item.roleId,item.spaceId,item.chatId,item.id)?:return@withTransaction false
        if(old.playbackToken!=token||old.videoId!=item.videoId||old.part!=item.part)return@withTransaction false
        val next=old.copy(positionMs=position,durationMs=duration,playbackToken=if(finish)null else token,
            version=if(finish)old.version+1 else old.version,updatedAt=maxOf(old.updatedAt,System.currentTimeMillis()))
        BilibiliWatching.validate(next);check(db.bilibili().update(next)==1);true
    }
    suspend fun note(item:BilibiliWatch,text:String)=db.withTransaction {
        val old=checked(item);if(old.playbackToken!=null)throw ChatFailure("MUSIC_BUSY")
        val next=old.copy(note=text,version=old.version+1,updatedAt=maxOf(old.updatedAt,System.currentTimeMillis()))
        BilibiliWatching.validate(next);check(db.bilibili().update(next)==1)
    }
    suspend fun summary(item:BilibiliWatch)=db.withTransaction {
        val w=checked(item);if(w.playbackToken!=null)throw ChatFailure("MUSIC_BUSY")
        BilibiliWatching.summary(w)
    }
    suspend fun delete(item:BilibiliWatch)=db.withTransaction {
        val w=checked(item);if(w.playbackToken!=null)throw ChatFailure("MUSIC_BUSY")
        check(db.bilibili().delete(w)==1)
    }
}
