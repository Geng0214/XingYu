package com.ai.assistance.operit.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DiaryMigration:Migration(18,19) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.query("SELECT COUNT(*) FROM memory_entries m LEFT JOIN roles r ON r.id=m.roleId AND r.spaceId=m.spaceId WHERE r.id IS NULL OR m.recallMode NOT IN ('always','auto') OR (m.replacedById IS NOT NULL AND NOT EXISTS (SELECT 1 FROM memory_entries n WHERE n.id=m.replacedById AND n.roleId=m.roleId AND n.spaceId=m.spaceId))").use {
            check(it.moveToFirst()&&it.getLong(0)==0L){"Invalid ownership before diary upgrade"}
        }
        db.execSQL("CREATE TABLE IF NOT EXISTS diary_entries (id TEXT NOT NULL, roleId TEXT NOT NULL, spaceId TEXT NOT NULL, notebook TEXT NOT NULL, title TEXT NOT NULL, content TEXT NOT NULL, mood TEXT NOT NULL, source TEXT NOT NULL, sourceModel TEXT, sourceConfigVersion INTEGER, generationPrompt TEXT, occurredAt INTEGER NOT NULL, version INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id))")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_diary_entries_roleId_spaceId ON diary_entries(roleId,spaceId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_diary_entries_roleId_spaceId_notebook_occurredAt ON diary_entries(roleId,spaceId,notebook,occurredAt)")
    }
}
