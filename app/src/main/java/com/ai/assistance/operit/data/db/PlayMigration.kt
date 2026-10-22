package com.ai.assistance.operit.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object PlayMigration:Migration(28,29) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.query("PRAGMA foreign_key_check").use {check(!it.moveToFirst()){ "Invalid ownership before play upgrade" }}
        db.query("SELECT COUNT(*) FROM chats c LEFT JOIN roles r ON r.id=c.roleId LEFT JOIN model_configs m ON m.id=c.configId WHERE r.id IS NULL OR m.id IS NULL").use {check(it.moveToFirst()&&it.getLong(0)==0L){"Invalid chat ownership before play upgrade"}}
        db.execSQL("CREATE UNIQUE INDEX index_roles_id_spaceId ON roles(id,spaceId)")
        db.execSQL("CREATE UNIQUE INDEX index_chats_id_roleId ON chats(id,roleId)")
        db.execSQL("CREATE TABLE play_sessions (id TEXT NOT NULL PRIMARY KEY, roleId TEXT NOT NULL, spaceId TEXT NOT NULL, chatId TEXT NOT NULL, title TEXT NOT NULL, stateJson TEXT NOT NULL, version INTEGER NOT NULL, status TEXT NOT NULL, requestId TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, FOREIGN KEY(roleId,spaceId) REFERENCES roles(id,spaceId) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(chatId,roleId) REFERENCES chats(id,roleId) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_play_sessions_roleId_spaceId ON play_sessions(roleId,spaceId)")
        db.execSQL("CREATE INDEX index_play_sessions_chatId_roleId ON play_sessions(chatId,roleId)")
        db.execSQL("CREATE UNIQUE INDEX index_play_sessions_id_roleId_spaceId_chatId ON play_sessions(id,roleId,spaceId,chatId)")
        db.execSQL("CREATE TABLE play_requests (requestId TEXT NOT NULL PRIMARY KEY, activityId TEXT NOT NULL, roleId TEXT NOT NULL, spaceId TEXT NOT NULL, chatId TEXT NOT NULL, activityVersion INTEGER NOT NULL, roleVersion INTEGER NOT NULL, configId TEXT NOT NULL, configVersion INTEGER NOT NULL, provider TEXT NOT NULL, model TEXT NOT NULL, assistantId TEXT NOT NULL, beforeState TEXT NOT NULL, contextJson TEXT NOT NULL, billingJson TEXT NOT NULL, createdAt INTEGER NOT NULL, state TEXT NOT NULL, content TEXT NOT NULL, errorCode TEXT NOT NULL, firstTokenMs INTEGER NOT NULL, elapsedMs INTEGER NOT NULL, FOREIGN KEY(activityId,roleId,spaceId,chatId) REFERENCES play_sessions(id,roleId,spaceId,chatId) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_play_requests_activityId_roleId_spaceId_chatId ON play_requests(activityId,roleId,spaceId,chatId)")
        db.execSQL("CREATE UNIQUE INDEX index_play_requests_assistantId ON play_requests(assistantId)")
    }
}
