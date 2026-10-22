package com.ai.assistance.operit.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object SpeechMigration:Migration(22,23) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.query("PRAGMA foreign_key_check").use {check(!it.moveToFirst()){ "Invalid ownership before speech upgrade" }}
        db.execSQL("CREATE TABLE IF NOT EXISTS speech_service_configs (id TEXT NOT NULL, name TEXT NOT NULL, kind TEXT NOT NULL, endpoint TEXT NOT NULL, model TEXT NOT NULL, voice TEXT NOT NULL, timeoutSeconds INTEGER NOT NULL, credentialRef TEXT NOT NULL, version INTEGER NOT NULL, PRIMARY KEY(id))")
    }
}
