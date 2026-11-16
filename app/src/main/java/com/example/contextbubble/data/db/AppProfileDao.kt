package com.example.contextbubble.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AppProfileDao {
    @Query("SELECT packageName FROM app_profile WHERE isExcluded = 1")
    fun observeExcludedPackages(): Flow<List<String>>

    @Query("SELECT * FROM app_profile ORDER BY packageName")
    fun observeAll(): Flow<List<AppProfileEntity>>

    @Upsert
    suspend fun upsert(profile: AppProfileEntity)
}
