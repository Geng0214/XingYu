package com.ai.assistance.operit.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

/** Merge private chats by role while keeping every message and chat-owned record reachable. */
object SingleConversationMigration : Migration(31, 32) {
    private data class ChatRow(
        val id: String,
        val roleId: String,
        val createdAt: Long,
        val openingMessage: String,
        val pinned: Boolean,
        val activityAt: Long
    )

    override fun migrate(db: SupportSQLiteDatabase) {
        // play_requests references play_sessions by a composite key that contains chatId.
        // Both rows must move in one transaction before foreign keys are checked.
        db.execSQL("PRAGMA defer_foreign_keys=ON")
        val chats = mutableListOf<ChatRow>()
        db.query("""SELECT c.id,c.roleId,c.createdAt,c.openingMessage,c.pinned,
            MAX(c.createdAt,
                COALESCE((SELECT MAX(m.createdAt) FROM messages m WHERE m.chatId=c.id),0),
                COALESCE((SELECT MAX(g.createdAt) FROM generated_images g WHERE g.chatId=c.id),0))
            FROM chats c ORDER BY c.roleId""").use { cursor ->
            while (cursor.moveToNext()) chats += ChatRow(
                cursor.getString(0), cursor.getString(1), cursor.getLong(2),
                cursor.getString(3), cursor.getLong(4) != 0L, cursor.getLong(5)
            )
        }
        val tablesWithChatId = listOf(
            "play_requests", "play_sessions", "generated_images", "messages",
            "bilibili_watches", "music_sessions", "shared_activities",
            "message_attachments", "message_materials", "message_stickers",
            "request_tool_permissions", "tool_invocations", "worldbook_entries",
            "relationship_entries", "proactive_role_settings", "proactive_requests",
            "request_usage"
        )
        chats.groupBy(ChatRow::roleId).values.forEach { roleChats ->
            val ordered = roleChats.sortedWith(
                compareByDescending<ChatRow> { it.activityAt }
                    .thenByDescending { it.createdAt }.thenByDescending { it.id }
            )
            val kept = ordered.first()
            if (ordered.any(ChatRow::pinned)) {
                db.execSQL("UPDATE chats SET pinned=1 WHERE id=?", arrayOf<Any?>(kept.id))
            }
            ordered.drop(1).forEach { old ->
                // Opening lines lived on the chat row, so save each older line as a visible message.
                if (old.openingMessage.isNotEmpty()) db.execSQL(
                    """INSERT INTO messages(id,chatId,requestId,kind,content,state,createdAt,errorCode,
                        firstTokenMs,elapsedMs,bubbleStyle,favorite,quotedMessageId,quotedSpeaker,quotedPreview)
                        VALUES(?,?,?,'assistant',?,'COMPLETE',?,'',-1,-1,'paragraph',0,NULL,'','')""",
                    arrayOf<Any?>(UUID.randomUUID().toString(), kept.id,
                        UUID.randomUUID().toString(), old.openingMessage, old.createdAt)
                )
                tablesWithChatId.forEach { table ->
                    db.execSQL("UPDATE `$table` SET chatId=? WHERE chatId=?", arrayOf<Any?>(kept.id, old.id))
                }
                db.execSQL("UPDATE memory_entries SET sourceChatId=? WHERE sourceChatId=?",
                    arrayOf<Any?>(kept.id, old.id))
                db.execSQL("DELETE FROM chats WHERE id=?", arrayOf<Any?>(old.id))
            }
        }
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_chats_roleId ON chats(roleId)")
        db.query("PRAGMA foreign_key_check").use { cursor ->
            check(!cursor.moveToFirst()) { "Private chat merge broke a foreign key" }
        }
    }
}
