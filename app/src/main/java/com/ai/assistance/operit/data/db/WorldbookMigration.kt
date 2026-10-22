package com.ai.assistance.operit.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object WorldbookMigration:Migration(7,8) {
    override fun migrate(db:SupportSQLiteDatabase) {
        PersonaMigration.checkOwnership(db)
        // Room's upgrade transaction leaves the original database intact if any check or DDL fails.
        db.execSQL("CREATE TABLE worldbook_entries (id TEXT NOT NULL, roleId TEXT NOT NULL, spaceId TEXT NOT NULL, chatId TEXT, title TEXT NOT NULL, content TEXT NOT NULL, `trigger` TEXT NOT NULL, keywords TEXT NOT NULL, enabled INTEGER NOT NULL, priority INTEGER NOT NULL, version INTEGER NOT NULL, PRIMARY KEY(id))")
        db.execSQL("CREATE INDEX index_worldbook_entries_roleId_spaceId ON worldbook_entries(roleId,spaceId)")
    }
}
