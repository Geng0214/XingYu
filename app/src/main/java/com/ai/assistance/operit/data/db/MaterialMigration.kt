package com.ai.assistance.operit.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object MaterialMigration:Migration(11,12) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.query("SELECT (SELECT COUNT(*) FROM messages m LEFT JOIN chats c ON c.id=m.chatId WHERE c.id IS NULL) + (SELECT COUNT(*) FROM message_attachments a LEFT JOIN messages m ON m.id=a.messageId AND m.chatId=a.chatId AND m.requestId=a.requestId WHERE m.id IS NULL OR m.kind!='user')").use {
            check(it.moveToFirst()&&it.getLong(0)==0L){"Invalid ownership before material upgrade"}
        }
        db.execSQL("CREATE TABLE IF NOT EXISTS message_materials (messageId TEXT NOT NULL, chatId TEXT NOT NULL, requestId TEXT NOT NULL, kind TEXT NOT NULL, displayName TEXT NOT NULL, sourceUrl TEXT, mimeType TEXT NOT NULL, text TEXT NOT NULL, originalChars INTEGER NOT NULL, truncated INTEGER NOT NULL, sha256 TEXT NOT NULL, PRIMARY KEY(messageId), FOREIGN KEY(messageId) REFERENCES messages(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_message_materials_chatId ON message_materials(chatId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_message_materials_messageId_chatId_requestId ON message_materials(messageId,chatId,requestId)")
    }
}
