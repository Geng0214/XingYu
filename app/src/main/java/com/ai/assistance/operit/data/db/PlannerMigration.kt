package com.ai.assistance.operit.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object PlannerMigration:Migration(20,21) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.query("SELECT COUNT(*) FROM environment_profiles e LEFT JOIN roles r ON r.id=e.roleId AND r.spaceId=e.spaceId WHERE r.id IS NULL").use {
            check(it.moveToFirst()&&it.getLong(0)==0L){"Invalid ownership before planner upgrade"}
        }
        db.execSQL("CREATE TABLE IF NOT EXISTS planner_entries (id TEXT NOT NULL, roleId TEXT NOT NULL, spaceId TEXT NOT NULL, kind TEXT NOT NULL, title TEXT NOT NULL, note TEXT NOT NULL, startsAt INTEGER NOT NULL, endsAt INTEGER, zoneId TEXT NOT NULL, completedAt INTEGER, reminderMinutesBefore INTEGER, shareWithRole INTEGER NOT NULL, version INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id))")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_planner_entries_roleId_spaceId ON planner_entries(roleId,spaceId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_planner_entries_reminderMinutesBefore ON planner_entries(reminderMinutesBefore)")
        db.execSQL("CREATE TABLE IF NOT EXISTS cycle_records (id TEXT NOT NULL, roleId TEXT NOT NULL, spaceId TEXT NOT NULL, startedOn INTEGER NOT NULL, endedOn INTEGER, flowLevel TEXT NOT NULL, note TEXT NOT NULL, shareWithRole INTEGER NOT NULL, version INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id))")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_cycle_records_roleId_spaceId ON cycle_records(roleId,spaceId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_cycle_records_startedOn ON cycle_records(startedOn)")
    }
}
