package com.example.bubbleb.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/** Room skeleton (M0). exportSchema is off until the first real migration (M5); see DECISIONS.md. */
@Database(entities = [AppProfileEntity::class], version = 1, exportSchema = false)
abstract class BubbleBDatabase : RoomDatabase() {
    abstract fun appProfileDao(): AppProfileDao

    companion object {
        const val NAME = "bubbleb.db"
    }
}
