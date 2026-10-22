package com.ai.assistance.operit.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object AttachmentMigration:Migration(10,11) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.query("SELECT (SELECT COUNT(*) FROM model_configs WHERE id='' OR version<1) + (SELECT COUNT(*) FROM messages m LEFT JOIN chats c ON c.id=m.chatId WHERE c.id IS NULL)").use {
            check(it.moveToFirst()&&it.getLong(0)==0L){"Invalid ownership before attachment upgrade"}
        }
        db.execSQL("ALTER TABLE model_configs ADD COLUMN visionEnabled INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE TABLE IF NOT EXISTS message_attachments (messageId TEXT NOT NULL, chatId TEXT NOT NULL, requestId TEXT NOT NULL, mimeType TEXT NOT NULL, width INTEGER NOT NULL, height INTEGER NOT NULL, sha256 TEXT NOT NULL, data BLOB NOT NULL, PRIMARY KEY(messageId), FOREIGN KEY(messageId) REFERENCES messages(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_message_attachments_chatId ON message_attachments(chatId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_message_attachments_messageId_chatId_requestId ON message_attachments(messageId,chatId,requestId)")
    }
}
