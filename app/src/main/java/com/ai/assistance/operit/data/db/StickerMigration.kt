package com.ai.assistance.operit.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object StickerMigration:Migration(12,13) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.query("SELECT (SELECT COUNT(*) FROM messages m LEFT JOIN chats c ON c.id=m.chatId WHERE c.id IS NULL) + (SELECT COUNT(*) FROM message_attachments a LEFT JOIN messages m ON m.id=a.messageId AND m.chatId=a.chatId AND m.requestId=a.requestId WHERE m.id IS NULL OR m.kind!='user') + (SELECT COUNT(*) FROM message_materials a LEFT JOIN messages m ON m.id=a.messageId AND m.chatId=a.chatId AND m.requestId=a.requestId WHERE m.id IS NULL OR m.kind!='user')").use {
            check(it.moveToFirst()&&it.getLong(0)==0L){"Invalid ownership before sticker upgrade"}
        }
        db.execSQL("CREATE TABLE IF NOT EXISTS sticker_assets (id TEXT NOT NULL, roleId TEXT NOT NULL, spaceId TEXT NOT NULL, displayName TEXT NOT NULL, mimeType TEXT NOT NULL, width INTEGER NOT NULL, height INTEGER NOT NULL, sha256 TEXT NOT NULL, data BLOB NOT NULL, favorite INTEGER NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(roleId) REFERENCES roles(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sticker_assets_roleId_spaceId ON sticker_assets(roleId,spaceId)")
        db.execSQL("CREATE TABLE IF NOT EXISTS message_stickers (messageId TEXT NOT NULL, chatId TEXT NOT NULL, requestId TEXT NOT NULL, displayName TEXT NOT NULL, mimeType TEXT NOT NULL, width INTEGER NOT NULL, height INTEGER NOT NULL, sha256 TEXT NOT NULL, data BLOB NOT NULL, PRIMARY KEY(messageId), FOREIGN KEY(messageId) REFERENCES messages(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_message_stickers_chatId ON message_stickers(chatId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_message_stickers_messageId_chatId_requestId ON message_stickers(messageId,chatId,requestId)")
    }
}
