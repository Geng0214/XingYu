package com.ai.assistance.operit.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object MemorySourceMigration:Migration(16,17) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.query("SELECT (SELECT COUNT(*) FROM memory_entries m LEFT JOIN roles r ON r.id=m.roleId AND r.spaceId=m.spaceId WHERE r.id IS NULL OR m.source!='manual') + (SELECT COUNT(*) FROM chats c LEFT JOIN roles r ON r.id=c.roleId LEFT JOIN model_configs x ON x.id=c.configId WHERE r.id IS NULL OR x.id IS NULL) + (SELECT COUNT(*) FROM messages m LEFT JOIN chats c ON c.id=m.chatId WHERE c.id IS NULL)").use {
            check(it.moveToFirst()&&it.getLong(0)==0L){"Invalid ownership before memory source upgrade"}
        }
        db.execSQL("ALTER TABLE memory_entries ADD COLUMN sourceChatId TEXT")
        db.execSQL("ALTER TABLE memory_entries ADD COLUMN sourceRequestId TEXT")
        db.execSQL("ALTER TABLE memory_entries ADD COLUMN sourceUserMessageId TEXT")
        db.execSQL("ALTER TABLE memory_entries ADD COLUMN sourceAssistantMessageId TEXT")
        db.execSQL("ALTER TABLE memory_entries ADD COLUMN sourceModel TEXT")
        db.execSQL("ALTER TABLE memory_entries ADD COLUMN sourceConfigVersion INTEGER")
        db.execSQL("ALTER TABLE memory_entries ADD COLUMN sourceMessageAt INTEGER")
        db.execSQL("ALTER TABLE memory_entries ADD COLUMN extractedAt INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_entries_sourceChatId ON memory_entries(sourceChatId)")
    }
}
