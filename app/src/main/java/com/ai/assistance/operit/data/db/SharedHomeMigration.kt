package com.ai.assistance.operit.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** The retired home scheme was never released; only its development tables are discarded. */
object SharedHomeMigration:Migration(33,34) {
    override fun migrate(db:SupportSQLiteDatabase) {
        // The retired migration also enforced chat ownership. These core tables have no SQL foreign key,
        // so keep that useful invariant here; otherwise removing the old home code accepts orphaned conversations.
        db.query("SELECT COUNT(*) FROM chats c LEFT JOIN roles r ON c.roleId=r.id LEFT JOIN model_configs m ON c.configId=m.id WHERE r.id IS NULL OR m.id IS NULL").use {
            check(it.moveToFirst()&&it.getLong(0)==0L){"Invalid chat ownership"}
        }
        db.query("SELECT COUNT(*) FROM messages m LEFT JOIN chats c ON m.chatId=c.id WHERE c.id IS NULL").use {
            check(it.moveToFirst()&&it.getLong(0)==0L){"Invalid message ownership"}
        }
        val retired=mutableSetOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('homes','home_commands')").use {cursor->
            while(cursor.moveToNext())retired+=cursor.getString(0)
        }
        // Child before parent. No world conversion or old home runtime is retained.
        listOf("home_commands","homes").filter {it in retired}.forEach {db.execSQL("DROP TABLE `$it`")}
        db.execSQL("CREATE TABLE home_world (id TEXT NOT NULL PRIMARY KEY, initialJson TEXT NOT NULL, stateJson TEXT NOT NULL, version INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE home_events (commandId TEXT NOT NULL PRIMARY KEY, homeId TEXT NOT NULL, beforeVersion INTEGER NOT NULL, actionJson TEXT NOT NULL, description TEXT NOT NULL, createdAt INTEGER NOT NULL, FOREIGN KEY(homeId) REFERENCES home_world(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_home_events_homeId ON home_events(homeId)")
        db.execSQL("CREATE UNIQUE INDEX index_home_events_homeId_beforeVersion ON home_events(homeId,beforeVersion)")
    }
}
