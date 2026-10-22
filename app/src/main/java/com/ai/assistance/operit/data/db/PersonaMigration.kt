package com.ai.assistance.operit.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Existing data keeps its identity and role version; no default persona is invented during upgrade. */
object PersonaMigration:Migration(6,7) {
    override fun migrate(db:SupportSQLiteDatabase) {
        checkOwnership(db)
        db.execSQL("ALTER TABLE roles ADD COLUMN userName TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE roles ADD COLUMN userPersona TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE roles ADD COLUMN personaEnabled INTEGER NOT NULL DEFAULT 0")
    }
    internal fun checkOwnership(db:SupportSQLiteDatabase) {
        db.query("PRAGMA foreign_key_check").use {check(!it.moveToFirst()){"Invalid associations before persona upgrade"}}
        val checks=listOf(
            "SELECT COUNT(*) FROM chats c LEFT JOIN roles r ON r.id=c.roleId LEFT JOIN model_configs m ON m.id=c.configId WHERE r.id IS NULL OR m.id IS NULL",
            "SELECT COUNT(*) FROM messages m LEFT JOIN chats c ON c.id=m.chatId WHERE c.id IS NULL",
            "SELECT COUNT(*) FROM roles r LEFT JOIN model_configs m ON m.id=r.defaultConfigId WHERE r.defaultConfigId IS NOT NULL AND m.id IS NULL",
            "SELECT COUNT(*) FROM role_appearance a LEFT JOIN roles r ON r.id=a.roleId AND r.spaceId=a.spaceId WHERE r.id IS NULL",
            "SELECT COUNT(*) FROM role_portraits p LEFT JOIN roles r ON r.id=p.roleId AND r.spaceId=p.spaceId WHERE r.id IS NULL",
            "SELECT COUNT(*) FROM role_tool_grants g LEFT JOIN roles r ON r.id=g.roleId AND r.spaceId=g.spaceId WHERE r.id IS NULL",
            "SELECT COUNT(*) FROM request_tool_permissions p LEFT JOIN roles r ON r.id=p.roleId AND r.spaceId=p.spaceId LEFT JOIN chats c ON c.id=p.chatId AND c.roleId=r.id LEFT JOIN messages m ON m.id=p.assistantId AND m.chatId=c.id AND m.requestId=p.requestId AND m.kind='assistant' WHERE r.id IS NULL OR c.id IS NULL OR m.id IS NULL",
            "SELECT COUNT(*) FROM tool_invocations i LEFT JOIN roles r ON r.id=i.roleId AND r.spaceId=i.spaceId LEFT JOIN chats c ON c.id=i.chatId AND c.roleId=r.id LEFT JOIN messages m ON m.id=i.assistantId AND m.chatId=c.id AND m.requestId=i.requestId AND m.kind='assistant' WHERE r.id IS NULL OR c.id IS NULL OR m.id IS NULL"
        )
        checks.forEach {query->db.query(query).use {check(it.moveToFirst()&&it.getLong(0)==0L){"Invalid ownership before persona upgrade"}}}
    }
}
