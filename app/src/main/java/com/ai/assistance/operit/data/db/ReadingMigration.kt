package com.ai.assistance.operit.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object ReadingMigration:Migration(14,15) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.query("SELECT (SELECT COUNT(*) FROM messages m LEFT JOIN chats c ON c.id=m.chatId WHERE c.id IS NULL) + (SELECT COUNT(*) FROM message_materials a LEFT JOIN messages m ON m.id=a.messageId AND m.chatId=a.chatId AND m.requestId=a.requestId WHERE m.id IS NULL OR m.kind!='user') + (SELECT COUNT(*) FROM generated_images g LEFT JOIN chats c ON c.id=g.chatId LEFT JOIN roles r ON r.id=g.roleId AND r.spaceId=g.spaceId WHERE c.id IS NULL OR r.id IS NULL OR c.roleId!=g.roleId)").use {
            check(it.moveToFirst()&&it.getLong(0)==0L){"Invalid ownership before reading upgrade"}
        }
        db.execSQL("CREATE TABLE IF NOT EXISTS reading_documents (id TEXT NOT NULL, roleId TEXT NOT NULL, spaceId TEXT NOT NULL, displayName TEXT NOT NULL, mimeType TEXT NOT NULL, text TEXT NOT NULL, chaptersJson TEXT NOT NULL, currentChapter INTEGER NOT NULL, currentOffset INTEGER NOT NULL, sha256 TEXT NOT NULL, version INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(roleId) REFERENCES roles(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_reading_documents_roleId ON reading_documents(roleId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_reading_documents_roleId_spaceId ON reading_documents(roleId,spaceId)")
    }
}
