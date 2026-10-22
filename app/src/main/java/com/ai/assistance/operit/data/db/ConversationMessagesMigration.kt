package com.ai.assistance.operit.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ai.assistance.operit.data.model.MessageParagraphs
import java.util.UUID

/** Preserves the original message id while making old displayed paragraphs independently actionable. */
object ConversationMessagesMigration : Migration(30, 31) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE messages ADD COLUMN quotedMessageId TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE messages ADD COLUMN quotedSpeaker TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE messages ADD COLUMN quotedPreview TEXT NOT NULL DEFAULT ''")

        val rows = mutableListOf<MessageRow>()
        db.query("SELECT id, chatId, requestId, content, createdAt, errorCode, firstTokenMs, elapsedMs, bubbleStyle, favorite FROM messages WHERE kind='assistant' AND state='COMPLETE' AND bubbleStyle='short' ORDER BY chatId, createdAt, id").use { cursor ->
            while (cursor.moveToNext()) rows += MessageRow(
                cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3),
                cursor.getLong(4), cursor.getString(5), cursor.getLong(6), cursor.getLong(7),
                cursor.getString(8), cursor.getLong(9) != 0L
            )
        }
        var lastTimestamp = mutableMapOf<String, Long>()
        rows.forEach { row ->
            val parts = MessageParagraphs.split(row.content)
            if (parts.size > 1) {
                val baseTimestamp = maxOf(row.createdAt, (lastTimestamp[row.chatId] ?: Long.MIN_VALUE) + 1)
                db.execSQL("UPDATE messages SET content=?, createdAt=? WHERE id=?", arrayOf<Any?>(parts.first(), baseTimestamp, row.id))
                parts.drop(1).forEachIndexed { index, part ->
                    db.execSQL(
                        "INSERT INTO messages(id, chatId, requestId, kind, content, state, createdAt, errorCode, firstTokenMs, elapsedMs, bubbleStyle, favorite, quotedMessageId, quotedSpeaker, quotedPreview) VALUES(?,?,?,'assistant',?,'COMPLETE',?,?,?,?,?,?,NULL,'','')",
                        arrayOf<Any?>(UUID.randomUUID().toString(), row.chatId, row.requestId, part, baseTimestamp + index + 1, row.errorCode, row.firstTokenMs, row.elapsedMs, row.bubbleStyle, if (row.favorite) 1 else 0)
                    )
                }
                lastTimestamp[row.chatId] = baseTimestamp + parts.size - 1
            } else {
                lastTimestamp[row.chatId] = maxOf(row.createdAt, lastTimestamp[row.chatId] ?: Long.MIN_VALUE)
            }
        }
    }

    private data class MessageRow(
        val id: String, val chatId: String, val requestId: String, val content: String,
        val createdAt: Long, val errorCode: String, val firstTokenMs: Long, val elapsedMs: Long,
        val bubbleStyle: String, val favorite: Boolean
    )
}
