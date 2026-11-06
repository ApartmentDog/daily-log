package com.evyr.rads.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [UserProfile::class, FoodLogEntry::class, HealthSnapshot::class, SafeFood::class],
    version = 7,
    exportSchema = false
)
abstract class RadsDatabase : RoomDatabase() {
    abstract fun foodLogDao(): FoodLogDao
    abstract fun healthSnapshotDao(): HealthSnapshotDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun safeFoodDao(): SafeFoodDao
}

// IMPORTANT: no destructive fallback migrations.
// Every schema bump needs a real Migration added in Migrations.kt
// and registered in DatabaseProvider.
