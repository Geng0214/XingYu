package com.ai.assistance.operit.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object VideoMigration:Migration(25,26) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.query("PRAGMA foreign_key_check").use {check(!it.moveToFirst()){ "Invalid ownership before video upgrade" }}
        db.execSQL("ALTER TABLE music_tracks ADD COLUMN mediaKind TEXT NOT NULL DEFAULT 'audio'")
        db.execSQL("ALTER TABLE music_tracks ADD COLUMN videoWidth INTEGER")
        db.execSQL("ALTER TABLE music_tracks ADD COLUMN videoHeight INTEGER")
    }
}
