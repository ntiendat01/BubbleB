package com.example.contextbubble.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Skeleton per-app profile (schema v1). Persona/language/instruction columns are added in M5
 * with an explicit migration. Never stores screen content.
 */
@Entity(tableName = "app_profile")
data class AppProfileEntity(
    @PrimaryKey val packageName: String,
    val isExcluded: Boolean,
    val updatedAtMs: Long,
)
