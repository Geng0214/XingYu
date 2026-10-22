package com.ai.assistance.operit.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** One-way cleanup of retired request metadata. Conversation content and service credentials stay intact. */
object RequestMetadataMigration : Migration(32,33) {
    internal val removedColumns=mapOf(
        "model_configs" to setOf("inputPriceMicrosPerMillion","outputPriceMicrosPerMillion","priceCurrency"),
        "image_generation_configs" to setOf("priceMicros","priceCurrency"),
        "generated_images" to setOf("priceMicros","priceCurrency"),
        "proactive_role_settings" to setOf("monthlyBudgetMicros","budgetCurrency"),
        "proactive_requests" to setOf("monthKey","reservedCostMicros","currency"),
        "group_requests" to setOf("estimatedPromptTokens","inputPriceMicrosPerMillion","outputPriceMicrosPerMillion","currency","modelRounds","reportedRounds","promptTokens","completionTokens","totalTokens","estimatedCompletionTokens","inputCostMicros","outputCostMicros"),
        "play_requests" to setOf("billingJson")
    )
    override fun migrate(db:SupportSQLiteDatabase) {
        // The bundled SQLCipher SQLite supports DROP COLUMN; rebuilding these tables would disturb foreign keys.
        removedColumns.forEach { (table,columns)->
            columns.forEach {column->db.execSQL("ALTER TABLE `$table` DROP COLUMN `$column`")}
        }
        db.execSQL("DROP TABLE request_usage")
        db.query("PRAGMA foreign_key_check").use {check(!it.moveToFirst())}
    }
}
