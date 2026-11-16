package com.example.contextbubble.data.db

import com.example.contextbubble.domain.repository.AppProfileRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomAppProfileRepository @Inject constructor(
    private val dao: AppProfileDao,
) : AppProfileRepository {
    override val excludedPackages: Flow<Set<String>> =
        dao.observeExcludedPackages().map { it.toSet() }
}
