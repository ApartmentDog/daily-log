package com.evyr.rads.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Real migrations only — never fallbackToDestructiveMigration().
 * v1 -> v2: goal weight and rate-of-change on user_profile.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE user_profile ADD COLUMN goalWeightKg REAL NOT NULL DEFAULT 0.0"
        )
        db.execSQL(
            "ALTER TABLE user_profile ADD COLUMN rateLbsPerWeek REAL NOT NULL DEFAULT 1.0"
        )
    }
}

/** v2 -> v3: extra nutrients and triggers per entry, health conditions on profile. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE food_log_entry ADD COLUMN saturatedFatGrams REAL")
        db.execSQL("ALTER TABLE food_log_entry ADD COLUMN sugarGrams REAL")
        db.execSQL("ALTER TABLE food_log_entry ADD COLUMN fiberGrams REAL")
        db.execSQL("ALTER TABLE food_log_entry ADD COLUMN sodiumMg REAL")
        db.execSQL("ALTER TABLE food_log_entry ADD COLUMN triggers TEXT")
        db.execSQL("ALTER TABLE user_profile ADD COLUMN conditions TEXT NOT NULL DEFAULT ''")
    }
}

/** v3 -> v4: allergens and custom allergens on profile. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE user_profile ADD COLUMN allergens TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE user_profile ADD COLUMN customAllergens TEXT NOT NULL DEFAULT ''")
    }
}

/** v4 -> v5: safe foods list. Columns match SafeFood exactly for Room's check. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `safe_food` (" +
                "`foodKey` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`calories` INTEGER NOT NULL, " +
                "`fatGrams` REAL NOT NULL, " +
                "`proteinGrams` REAL NOT NULL, " +
                "`carbGrams` REAL NOT NULL, " +
                "`saturatedFatGrams` REAL, " +
                "`sugarGrams` REAL, " +
                "`fiberGrams` REAL, " +
                "`sodiumMg` REAL, " +
                "`triggers` TEXT, " +
                "`source` TEXT NOT NULL, " +
                "`addedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`foodKey`))"
        )
    }
}

/** v5 -> v6: onboarding choices (focus areas, detail level, doctor-set fat limit). */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE user_profile ADD COLUMN focus TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE user_profile ADD COLUMN detailLevel TEXT NOT NULL DEFAULT 'detailed'")
        db.execSQL("ALTER TABLE user_profile ADD COLUMN fatLimitFromDoctor INTEGER NOT NULL DEFAULT 0")
    }
}

/** v6 -> v7: user-adjusted per-meal limits. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE user_profile ADD COLUMN limitOverrides TEXT NOT NULL DEFAULT ''")
    }
}
