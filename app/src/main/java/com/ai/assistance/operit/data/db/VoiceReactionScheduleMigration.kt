package com.ai.assistance.operit.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object VoiceReactionScheduleMigration:Migration(34,35) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.query("SELECT (SELECT COUNT(*) FROM messages m LEFT JOIN chats c ON c.id=m.chatId WHERE c.id IS NULL) + (SELECT COUNT(*) FROM chats c LEFT JOIN roles r ON c.roleId=r.id WHERE r.id IS NULL)").use {
            check(it.moveToFirst()&&it.getLong(0)==0L){"Invalid ownership before voice and reaction upgrade"}
        }
        db.execSQL("CREATE TABLE IF NOT EXISTS message_voices (messageId TEXT NOT NULL, chatId TEXT NOT NULL, requestId TEXT NOT NULL, sampleRate INTEGER NOT NULL, durationMs INTEGER NOT NULL, waveform BLOB NOT NULL, sha256 TEXT NOT NULL, data BLOB NOT NULL, PRIMARY KEY(messageId), FOREIGN KEY(messageId) REFERENCES messages(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_message_voices_chatId ON message_voices(chatId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_message_voices_messageId_chatId_requestId ON message_voices(messageId,chatId,requestId)")
        db.execSQL("CREATE TABLE IF NOT EXISTS message_reactions (messageId TEXT NOT NULL, side TEXT NOT NULL, emoji TEXT NOT NULL, chatId TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(messageId,side,emoji), FOREIGN KEY(messageId) REFERENCES messages(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_message_reactions_chatId ON message_reactions(chatId)")
        db.execSQL("CREATE TABLE IF NOT EXISTS scheduled_messages (id TEXT NOT NULL, roleId TEXT NOT NULL, spaceId TEXT NOT NULL, chatId TEXT NOT NULL, configId TEXT NOT NULL, configVersion INTEGER NOT NULL, dueAt INTEGER NOT NULL, mode TEXT NOT NULL, content TEXT NOT NULL, state TEXT NOT NULL, assistantId TEXT NOT NULL, errorCode TEXT NOT NULL, notificationSent INTEGER NOT NULL, createdAt INTEGER NOT NULL, finishedAt INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(roleId) REFERENCES roles(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(chatId) REFERENCES chats(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_scheduled_messages_roleId_dueAt ON scheduled_messages(roleId,dueAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_scheduled_messages_chatId ON scheduled_messages(chatId)")
    }
}
