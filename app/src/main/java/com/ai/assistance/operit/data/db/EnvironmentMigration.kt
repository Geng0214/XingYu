package com.ai.assistance.operit.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object EnvironmentMigration:Migration(19,20) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.query("SELECT COUNT(*) FROM diary_entries d LEFT JOIN roles r ON r.id=d.roleId AND r.spaceId=d.spaceId WHERE r.id IS NULL").use {
            check(it.moveToFirst()&&it.getLong(0)==0L){"Invalid ownership before environment upgrade"}
        }
        db.execSQL("CREATE TABLE IF NOT EXISTS environment_profiles (roleId TEXT NOT NULL, spaceId TEXT NOT NULL, timeContextEnabled INTEGER NOT NULL, weatherContextEnabled INTEGER NOT NULL, provider TEXT NOT NULL, locationId INTEGER, locationName TEXT, admin1 TEXT, country TEXT, countryCode TEXT, latitude REAL, longitude REAL, timezone TEXT, weatherTime TEXT, temperature REAL, apparentTemperature REAL, humidity INTEGER, precipitation REAL, weatherCode INTEGER, windSpeed REAL, todayMin REAL, todayMax REAL, todayPrecipitationProbability INTEGER, fetchedAt INTEGER, version INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(roleId))")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_environment_profiles_spaceId ON environment_profiles(spaceId)")
    }
}
