package com.ai.assistance.operit.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Room wraps this migration in a transaction; reject inconsistent legacy ownership before any DDL. */
object ExtensionMigration:Migration(4,5) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.query("SELECT (SELECT COUNT(*) FROM chats c LEFT JOIN roles r ON r.id=c.roleId LEFT JOIN model_configs m ON m.id=c.configId WHERE r.id IS NULL OR m.id IS NULL) + (SELECT COUNT(*) FROM messages m LEFT JOIN chats c ON c.id=m.chatId WHERE c.id IS NULL) + (SELECT COUNT(*) FROM role_appearance a LEFT JOIN roles r ON r.id=a.roleId AND r.spaceId=a.spaceId WHERE r.id IS NULL) + (SELECT COUNT(*) FROM role_portraits p LEFT JOIN roles r ON r.id=p.roleId AND r.spaceId=p.spaceId WHERE r.id IS NULL) + (SELECT COUNT(*) FROM roles WHERE spaceId='')").use {
            check(it.moveToFirst()&&it.getLong(0)==0L) {"Invalid ownership before extension upgrade"}
        }
        db.execSQL("CREATE TABLE IF NOT EXISTS mcp_extensions (id TEXT NOT NULL, name TEXT NOT NULL, endpoint TEXT NOT NULL, credentialRef TEXT, timeoutMs INTEGER NOT NULL, enabled INTEGER NOT NULL, version INTEGER NOT NULL, PRIMARY KEY(id))")
        db.execSQL("CREATE TABLE IF NOT EXISTS extension_tools (extensionId TEXT NOT NULL, name TEXT NOT NULL, description TEXT NOT NULL, inputSchema TEXT NOT NULL, PRIMARY KEY(extensionId,name), FOREIGN KEY(extensionId) REFERENCES mcp_extensions(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE TABLE IF NOT EXISTS role_tool_grants (roleId TEXT NOT NULL, spaceId TEXT NOT NULL, extensionId TEXT NOT NULL, toolName TEXT NOT NULL, enabled INTEGER NOT NULL, version INTEGER NOT NULL, PRIMARY KEY(roleId,spaceId,extensionId,toolName), FOREIGN KEY(roleId) REFERENCES roles(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(extensionId,toolName) REFERENCES extension_tools(extensionId,name) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_role_tool_grants_extensionId_toolName ON role_tool_grants(extensionId,toolName)")
        db.execSQL("CREATE TABLE IF NOT EXISTS request_tool_permissions (requestId TEXT NOT NULL, extensionId TEXT NOT NULL, toolName TEXT NOT NULL, roleId TEXT NOT NULL, spaceId TEXT NOT NULL, chatId TEXT NOT NULL, assistantId TEXT NOT NULL, configVersion INTEGER NOT NULL, grantVersion INTEGER NOT NULL, endpoint TEXT NOT NULL, credentialRef TEXT, timeoutMs INTEGER NOT NULL, description TEXT NOT NULL, inputSchema TEXT NOT NULL, PRIMARY KEY(requestId,extensionId,toolName), FOREIGN KEY(assistantId) REFERENCES messages(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_request_tool_permissions_assistantId ON request_tool_permissions(assistantId)")
        db.execSQL("CREATE TABLE IF NOT EXISTS tool_invocations (requestId TEXT NOT NULL, callId TEXT NOT NULL, roleId TEXT NOT NULL, spaceId TEXT NOT NULL, chatId TEXT NOT NULL, assistantId TEXT NOT NULL, extensionId TEXT NOT NULL, toolName TEXT NOT NULL, arguments TEXT NOT NULL, result TEXT NOT NULL, state TEXT NOT NULL, errorCode TEXT NOT NULL, createdAt INTEGER NOT NULL, finishedAt INTEGER NOT NULL, PRIMARY KEY(requestId,callId), FOREIGN KEY(assistantId) REFERENCES messages(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_tool_invocations_assistantId ON tool_invocations(assistantId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_tool_invocations_extensionId ON tool_invocations(extensionId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_tool_invocations_chatId ON tool_invocations(chatId)")
    }
}
