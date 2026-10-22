package com.ai.assistance.operit.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object UsageMigration:Migration(9,10) {
    override fun migrate(db:SupportSQLiteDatabase) {
        PersonaMigration.checkOwnership(db)
        db.query("SELECT (SELECT COUNT(*) FROM worldbook_entries w LEFT JOIN roles r ON r.id=w.roleId AND r.spaceId=w.spaceId LEFT JOIN chats c ON c.id=w.chatId AND c.roleId=w.roleId WHERE r.id IS NULL OR (w.chatId IS NOT NULL AND c.id IS NULL)) + (SELECT COUNT(*) FROM relationship_entries x LEFT JOIN roles r ON r.id=x.roleId AND r.spaceId=x.spaceId LEFT JOIN chats c ON c.id=x.chatId AND c.roleId=x.roleId WHERE r.id IS NULL OR (x.chatId IS NOT NULL AND c.id IS NULL))").use {
            check(it.moveToFirst()&&it.getLong(0)==0L){"Invalid scoped data before usage upgrade"}
        }
        db.execSQL("ALTER TABLE model_configs ADD COLUMN inputPriceMicrosPerMillion INTEGER")
        db.execSQL("ALTER TABLE model_configs ADD COLUMN outputPriceMicrosPerMillion INTEGER")
        db.execSQL("ALTER TABLE model_configs ADD COLUMN priceCurrency TEXT NOT NULL DEFAULT 'USD'")
        db.execSQL("CREATE TABLE request_usage (requestId TEXT NOT NULL, roleId TEXT NOT NULL, spaceId TEXT NOT NULL, chatId TEXT NOT NULL, assistantId TEXT NOT NULL, configId TEXT NOT NULL, configVersion INTEGER NOT NULL, provider TEXT NOT NULL, model TEXT NOT NULL, createdAt INTEGER NOT NULL, terminalState TEXT NOT NULL, errorCode TEXT NOT NULL, coverage TEXT NOT NULL, modelRounds INTEGER NOT NULL, reportedRounds INTEGER NOT NULL, promptTokens INTEGER, completionTokens INTEGER, totalTokens INTEGER, estimatedPromptTokens INTEGER NOT NULL, estimatedCompletionTokens INTEGER, inputPriceMicrosPerMillion INTEGER, outputPriceMicrosPerMillion INTEGER, currency TEXT NOT NULL, inputCostMicros INTEGER, outputCostMicros INTEGER, PRIMARY KEY(requestId))")
        db.execSQL("CREATE INDEX index_request_usage_roleId_spaceId ON request_usage(roleId,spaceId)")
        db.execSQL("CREATE INDEX index_request_usage_chatId ON request_usage(chatId)")
        db.execSQL("CREATE INDEX index_request_usage_configId ON request_usage(configId)")
        db.execSQL("CREATE INDEX index_request_usage_createdAt ON request_usage(createdAt)")
    }
}
