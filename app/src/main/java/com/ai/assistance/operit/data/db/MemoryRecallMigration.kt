package com.ai.assistance.operit.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object MemoryRecallMigration:Migration(17,18) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.query("SELECT COUNT(*) FROM memory_entries m LEFT JOIN roles r ON r.id=m.roleId AND r.spaceId=m.spaceId WHERE r.id IS NULL OR m.source NOT IN ('manual','extracted') OR m.version<=0 OR m.updatedAt<m.createdAt OR (m.source='manual' AND (m.sourceChatId IS NOT NULL OR m.sourceRequestId IS NOT NULL OR m.sourceUserMessageId IS NOT NULL OR m.sourceAssistantMessageId IS NOT NULL OR m.sourceModel IS NOT NULL OR m.sourceConfigVersion IS NOT NULL OR m.sourceMessageAt IS NOT NULL OR m.extractedAt IS NOT NULL)) OR (m.source='extracted' AND (m.sourceModel IS NULL OR m.sourceConfigVersion IS NULL OR m.sourceMessageAt IS NULL OR m.extractedAt IS NULL OR ((m.sourceChatId IS NULL OR m.sourceRequestId IS NULL OR m.sourceUserMessageId IS NULL OR m.sourceAssistantMessageId IS NULL) AND (m.sourceChatId IS NOT NULL OR m.sourceRequestId IS NOT NULL OR m.sourceUserMessageId IS NOT NULL OR m.sourceAssistantMessageId IS NOT NULL))))").use {
            check(it.moveToFirst()&&it.getLong(0)==0L){"Invalid memory ownership before recall upgrade"}
        }
        db.execSQL("ALTER TABLE memory_entries ADD COLUMN recallMode TEXT NOT NULL DEFAULT 'always'")
        db.execSQL("ALTER TABLE memory_entries ADD COLUMN expiresAt INTEGER")
        db.execSQL("ALTER TABLE memory_entries ADD COLUMN forgottenAt INTEGER")
        db.execSQL("ALTER TABLE memory_entries ADD COLUMN replacedById TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_entries_roleId_spaceId_forgottenAt ON memory_entries(roleId,spaceId,forgottenAt)")
    }
}
