package com.example.bubbleb.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bubbleb.data.db.AppProfileEntity
import com.example.bubbleb.data.db.BubbleBDatabase
import com.example.bubbleb.data.db.RoomAppProfileRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AppProfileDaoTest {
    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(), BubbleBDatabase::class.java,
    ).allowMainThreadQueries().build()
    private val dao = db.appProfileDao()

    @After
    fun tearDown() = db.close()

    @Test
    fun excludedPackages_onlyReturnsExcluded() = runTest {
        dao.upsert(AppProfileEntity("com.a", isExcluded = true, updatedAtMs = 1))
        dao.upsert(AppProfileEntity("com.b", isExcluded = false, updatedAtMs = 1))
        assertEquals(setOf("com.a"), RoomAppProfileRepository(dao).excludedPackages.first())
    }

    @Test
    fun upsert_updatesExisting() = runTest {
        dao.upsert(AppProfileEntity("com.a", isExcluded = true, updatedAtMs = 1))
        dao.upsert(AppProfileEntity("com.a", isExcluded = false, updatedAtMs = 2))
        assertEquals(emptyList<String>(), dao.observeExcludedPackages().first())
        assertEquals(1, dao.observeAll().first().size)
    }
}
