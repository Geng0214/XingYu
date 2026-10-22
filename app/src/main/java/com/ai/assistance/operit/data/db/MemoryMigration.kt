package com.ai.assistance.operit.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object MemoryMigration:Migration(15,16) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.query("SELECT (SELECT COUNT(*) FROM chats c LEFT JOIN roles r ON r.id=c.roleId WHERE r.id IS NULL) + (SELECT COUNT(*) FROM messages m LEFT JOIN chats c ON c.id=m.chatId WHERE c.id IS NULL) + (SELECT COUNT(*) FROM worldbook_entries w LEFT JOIN roles r ON r.id=w.roleId AND r.spaceId=w.spaceId WHERE r.id IS NULL) + (SELECT COUNT(*) FROM relationship_entries x LEFT JOIN roles r ON r.id=x.roleId AND r.spaceId=x.spaceId WHERE r.id IS NULL) + (SELECT COUNT(*) FROM reading_documents d LEFT JOIN roles r ON r.id=d.roleId AND r.spaceId=d.spaceId WHERE r.id IS NULL) + (SELECT COUNT(*) FROM sticker_assets s LEFT JOIN roles r ON r.id=s.roleId AND r.spaceId=s.spaceId WHERE r.id IS NULL) + (SELECT COUNT(*) FROM generated_images g LEFT JOIN chats c ON c.id=g.chatId LEFT JOIN roles r ON r.id=g.roleId AND r.spaceId=g.spaceId WHERE c.id IS NULL OR r.id IS NULL OR c.roleId!=g.roleId)").use {
            check(it.moveToFirst()&&it.getLong(0)==0L){"Invalid ownership before memory upgrade"}
        }
        db.execSQL("CREATE TABLE IF NOT EXISTS memory_entries (id TEXT NOT NULL, roleId TEXT NOT NULL, spaceId TEXT NOT NULL, kind TEXT NOT NULL, title TEXT NOT NULL, content TEXT NOT NULL, enabled INTEGER NOT NULL, priority INTEGER NOT NULL, source TEXT NOT NULL, version INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id))")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_entries_roleId_spaceId ON memory_entries(roleId,spaceId)")
    }
}
