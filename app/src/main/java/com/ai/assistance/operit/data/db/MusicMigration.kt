package com.ai.assistance.operit.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object MusicMigration:Migration(24,25) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.query("PRAGMA foreign_key_check").use {check(!it.moveToFirst()){ "Invalid ownership before music upgrade" }}
        db.execSQL("CREATE TABLE IF NOT EXISTS music_tracks (id TEXT NOT NULL, roleId TEXT NOT NULL, spaceId TEXT NOT NULL, displayName TEXT NOT NULL, mimeType TEXT NOT NULL, durationMs INTEGER NOT NULL, byteCount INTEGER NOT NULL, sha256 TEXT NOT NULL, sourceUri TEXT, version INTEGER NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(roleId) REFERENCES roles(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_music_tracks_roleId ON music_tracks(roleId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_music_tracks_roleId_spaceId ON music_tracks(roleId,spaceId)")
        db.execSQL("CREATE TABLE IF NOT EXISTS music_sessions (id TEXT NOT NULL, roleId TEXT NOT NULL, spaceId TEXT NOT NULL, chatId TEXT NOT NULL, trackId TEXT NOT NULL, state TEXT NOT NULL, positionMs INTEGER NOT NULL, note TEXT NOT NULL, playbackToken TEXT, version INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(roleId) REFERENCES roles(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(chatId) REFERENCES chats(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(trackId) REFERENCES music_tracks(id) ON UPDATE NO ACTION ON DELETE NO ACTION)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_music_sessions_roleId ON music_sessions(roleId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_music_sessions_chatId ON music_sessions(chatId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_music_sessions_trackId ON music_sessions(trackId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_music_sessions_roleId_spaceId ON music_sessions(roleId,spaceId)")
    }
}
