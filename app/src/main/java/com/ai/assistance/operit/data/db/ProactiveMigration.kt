package com.ai.assistance.operit.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object ProactiveMigration:Migration(21,22) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.query("SELECT (SELECT COUNT(*) FROM planner_entries p LEFT JOIN roles r ON r.id=p.roleId AND r.spaceId=p.spaceId WHERE r.id IS NULL) + (SELECT COUNT(*) FROM cycle_records c LEFT JOIN roles r ON r.id=c.roleId AND r.spaceId=c.spaceId WHERE r.id IS NULL)").use {
            check(it.moveToFirst()&&it.getLong(0)==0L){"Invalid ownership before proactive messages upgrade"}
        }
        db.execSQL("CREATE TABLE IF NOT EXISTS proactive_role_settings (roleId TEXT NOT NULL, spaceId TEXT NOT NULL, enabled INTEGER NOT NULL, chatId TEXT NOT NULL, configId TEXT NOT NULL, quietStartMinute INTEGER NOT NULL, quietEndMinute INTEGER NOT NULL, minIntervalHours INTEGER NOT NULL, dailyLimit INTEGER NOT NULL, monthlyBudgetMicros INTEGER NOT NULL, budgetCurrency TEXT NOT NULL, nextEligibleAt INTEGER NOT NULL, version INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(roleId))")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_proactive_role_settings_roleId_spaceId ON proactive_role_settings(roleId,spaceId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_proactive_role_settings_chatId ON proactive_role_settings(chatId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_proactive_role_settings_configId ON proactive_role_settings(configId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_proactive_role_settings_nextEligibleAt ON proactive_role_settings(nextEligibleAt)")
        db.execSQL("CREATE TABLE IF NOT EXISTS proactive_requests (requestId TEXT NOT NULL, roleId TEXT NOT NULL, spaceId TEXT NOT NULL, chatId TEXT NOT NULL, assistantId TEXT NOT NULL, configId TEXT NOT NULL, configVersion INTEGER NOT NULL, dueAt INTEGER NOT NULL, dayKey TEXT NOT NULL, monthKey TEXT NOT NULL, reservedCostMicros INTEGER NOT NULL, currency TEXT NOT NULL, state TEXT NOT NULL, errorCode TEXT NOT NULL, notificationSent INTEGER NOT NULL, createdAt INTEGER NOT NULL, finishedAt INTEGER NOT NULL, PRIMARY KEY(requestId))")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_proactive_requests_roleId_dueAt ON proactive_requests(roleId,dueAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_proactive_requests_roleId_spaceId ON proactive_requests(roleId,spaceId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_proactive_requests_chatId ON proactive_requests(chatId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_proactive_requests_assistantId ON proactive_requests(assistantId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_proactive_requests_configId ON proactive_requests(configId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_proactive_requests_createdAt ON proactive_requests(createdAt)")
    }
}
