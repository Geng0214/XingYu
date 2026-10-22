package com.ai.assistance.operit.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object ImageGenerationMigration:Migration(13,14) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.query("SELECT (SELECT COUNT(*) FROM messages m LEFT JOIN chats c ON c.id=m.chatId WHERE c.id IS NULL) + (SELECT COUNT(*) FROM message_attachments a LEFT JOIN messages m ON m.id=a.messageId AND m.chatId=a.chatId AND m.requestId=a.requestId WHERE m.id IS NULL OR m.kind!='user') + (SELECT COUNT(*) FROM message_materials a LEFT JOIN messages m ON m.id=a.messageId AND m.chatId=a.chatId AND m.requestId=a.requestId WHERE m.id IS NULL OR m.kind!='user') + (SELECT COUNT(*) FROM message_stickers a LEFT JOIN messages m ON m.id=a.messageId AND m.chatId=a.chatId AND m.requestId=a.requestId WHERE m.id IS NULL OR m.kind!='user') + (SELECT COUNT(*) FROM sticker_assets s LEFT JOIN roles r ON r.id=s.roleId AND r.spaceId=s.spaceId WHERE r.id IS NULL)").use {
            check(it.moveToFirst()&&it.getLong(0)==0L){"Invalid ownership before image generation upgrade"}
        }
        db.execSQL("CREATE TABLE IF NOT EXISTS image_generation_configs (id TEXT NOT NULL, name TEXT NOT NULL, endpoint TEXT NOT NULL, model TEXT NOT NULL, timeoutSeconds INTEGER NOT NULL, credentialRef TEXT NOT NULL, version INTEGER NOT NULL, size TEXT NOT NULL, priceMicros INTEGER, priceCurrency TEXT NOT NULL, PRIMARY KEY(id))")
        db.execSQL("CREATE TABLE IF NOT EXISTS generated_images (id TEXT NOT NULL, roleId TEXT NOT NULL, spaceId TEXT NOT NULL, chatId TEXT NOT NULL, configId TEXT NOT NULL, configVersion INTEGER NOT NULL, model TEXT NOT NULL, prompt TEXT NOT NULL, requestedSize TEXT NOT NULL, priceMicros INTEGER, priceCurrency TEXT NOT NULL, state TEXT NOT NULL, errorCode TEXT NOT NULL, mimeType TEXT NOT NULL, width INTEGER NOT NULL, height INTEGER NOT NULL, sha256 TEXT NOT NULL, data BLOB, createdAt INTEGER NOT NULL, finishedAt INTEGER NOT NULL, elapsedMs INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(chatId) REFERENCES chats(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_generated_images_chatId ON generated_images(chatId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_generated_images_roleId_spaceId ON generated_images(roleId,spaceId)")
    }
}
