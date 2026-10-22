package com.ai.assistance.operit.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object SharedActivityMigration:Migration(23,24) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.query("PRAGMA foreign_key_check").use {check(!it.moveToFirst()){ "Invalid ownership before activity upgrade" }}
        db.execSQL("CREATE TABLE IF NOT EXISTS shared_activities (id TEXT NOT NULL, roleId TEXT NOT NULL, spaceId TEXT NOT NULL, chatId TEXT NOT NULL, readingDocumentId TEXT NOT NULL, kind TEXT NOT NULL, state TEXT NOT NULL, chapter INTEGER NOT NULL, offset INTEGER NOT NULL, note TEXT NOT NULL, version INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(roleId) REFERENCES roles(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(chatId) REFERENCES chats(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(readingDocumentId) REFERENCES reading_documents(id) ON UPDATE NO ACTION ON DELETE NO ACTION)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_shared_activities_roleId ON shared_activities(roleId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_shared_activities_chatId ON shared_activities(chatId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_shared_activities_readingDocumentId ON shared_activities(readingDocumentId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_shared_activities_roleId_spaceId ON shared_activities(roleId,spaceId)")
    }
}
