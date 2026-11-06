package com.evyr.rads.data.local

import android.content.Context
import androidx.room.Room

object DatabaseProvider {
    @Volatile
    private var instance: RadsDatabase? = null

    fun get(context: Context): RadsDatabase {
        return instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                RadsDatabase::class.java,
                "rads.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()
                .also { instance = it }
        }
    }
}
