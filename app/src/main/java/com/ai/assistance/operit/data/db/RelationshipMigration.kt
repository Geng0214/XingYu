package com.ai.assistance.operit.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object RelationshipMigration:Migration(8,9) {
    override fun migrate(db:SupportSQLiteDatabase) {
        PersonaMigration.checkOwnership(db)
        db.query("SELECT COUNT(*) FROM worldbook_entries w LEFT JOIN roles r ON r.id=w.roleId AND r.spaceId=w.spaceId LEFT JOIN chats c ON c.id=w.chatId AND c.roleId=w.roleId WHERE r.id IS NULL OR (w.chatId IS NOT NULL AND c.id IS NULL)").use {
            check(it.moveToFirst()&&it.getLong(0)==0L){"Invalid worldbook ownership before relationship upgrade"}
        }
        db.execSQL("ALTER TABLE roles ADD COLUMN conversationMode TEXT NOT NULL DEFAULT 'character'")
        db.execSQL("CREATE TABLE relationship_entries (id TEXT NOT NULL, roleId TEXT NOT NULL, spaceId TEXT NOT NULL, chatId TEXT, sourceName TEXT NOT NULL, targetName TEXT NOT NULL, label TEXT NOT NULL, description TEXT NOT NULL, enabled INTEGER NOT NULL, priority INTEGER NOT NULL, version INTEGER NOT NULL, PRIMARY KEY(id))")
        db.execSQL("CREATE INDEX index_relationship_entries_roleId_spaceId ON relationship_entries(roleId,spaceId)")
    }
}
