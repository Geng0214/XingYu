package com.ai.assistance.operit.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object GroupMigration:Migration(27,28) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.query("PRAGMA foreign_key_check").use {check(!it.moveToFirst()){ "Invalid ownership before group upgrade" }}
        db.execSQL("CREATE TABLE group_rooms (groupId TEXT NOT NULL PRIMARY KEY, spaceId TEXT NOT NULL, title TEXT NOT NULL, userName TEXT NOT NULL, version INTEGER NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX index_group_rooms_spaceId ON group_rooms(spaceId)")
        db.execSQL("CREATE UNIQUE INDEX index_group_rooms_groupId_spaceId ON group_rooms(groupId,spaceId)")
        db.execSQL("CREATE TABLE group_members (id TEXT NOT NULL PRIMARY KEY, groupId TEXT NOT NULL, spaceId TEXT NOT NULL, roleId TEXT NOT NULL, roleSpaceId TEXT NOT NULL, name TEXT NOT NULL, configId TEXT NOT NULL, FOREIGN KEY(groupId,spaceId) REFERENCES group_rooms(groupId,spaceId) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(roleId) REFERENCES roles(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(configId) REFERENCES model_configs(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE INDEX index_group_members_groupId_spaceId ON group_members(groupId,spaceId)")
        db.execSQL("CREATE UNIQUE INDEX index_group_members_groupId_roleId ON group_members(groupId,roleId)")
        db.execSQL("CREATE INDEX index_group_members_roleId ON group_members(roleId)")
        db.execSQL("CREATE INDEX index_group_members_configId ON group_members(configId)")
        db.execSQL("CREATE TABLE group_requests (requestId TEXT NOT NULL PRIMARY KEY, groupId TEXT NOT NULL, spaceId TEXT NOT NULL, groupVersion INTEGER NOT NULL, participantId TEXT NOT NULL, roleId TEXT NOT NULL, roleSpaceId TEXT NOT NULL, roleVersion INTEGER NOT NULL, configId TEXT NOT NULL, configVersion INTEGER NOT NULL, provider TEXT NOT NULL, model TEXT NOT NULL, userId TEXT NOT NULL, assistantId TEXT NOT NULL, createdAt INTEGER NOT NULL, estimatedPromptTokens INTEGER NOT NULL, inputPriceMicrosPerMillion INTEGER, outputPriceMicrosPerMillion INTEGER, currency TEXT NOT NULL, terminalState TEXT NOT NULL, errorCode TEXT NOT NULL, modelRounds INTEGER NOT NULL, reportedRounds INTEGER NOT NULL, promptTokens INTEGER, completionTokens INTEGER, totalTokens INTEGER, estimatedCompletionTokens INTEGER, inputCostMicros INTEGER, outputCostMicros INTEGER, FOREIGN KEY(groupId,spaceId) REFERENCES group_rooms(groupId,spaceId) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_group_requests_groupId_spaceId ON group_requests(groupId,spaceId)")
        db.execSQL("CREATE UNIQUE INDEX index_group_requests_requestId_groupId_spaceId ON group_requests(requestId,groupId,spaceId)")
        db.execSQL("CREATE UNIQUE INDEX index_group_requests_userId ON group_requests(userId)")
        db.execSQL("CREATE UNIQUE INDEX index_group_requests_assistantId ON group_requests(assistantId)")
        db.execSQL("CREATE TABLE group_messages (id TEXT NOT NULL PRIMARY KEY, groupId TEXT NOT NULL, spaceId TEXT NOT NULL, requestId TEXT NOT NULL, kind TEXT NOT NULL, participantId TEXT, speakerName TEXT NOT NULL, content TEXT NOT NULL, state TEXT NOT NULL, createdAt INTEGER NOT NULL, errorCode TEXT NOT NULL, firstTokenMs INTEGER NOT NULL, elapsedMs INTEGER NOT NULL, FOREIGN KEY(requestId,groupId,spaceId) REFERENCES group_requests(requestId,groupId,spaceId) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_group_messages_requestId_groupId_spaceId ON group_messages(requestId,groupId,spaceId)")
        db.execSQL("CREATE UNIQUE INDEX index_group_messages_requestId_kind ON group_messages(requestId,kind)")
        db.execSQL("CREATE INDEX index_group_messages_groupId_spaceId ON group_messages(groupId,spaceId)")
    }
}
