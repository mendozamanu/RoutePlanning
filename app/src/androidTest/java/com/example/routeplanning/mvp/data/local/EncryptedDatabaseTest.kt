package com.example.routeplanning.mvp.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.routeplanning.mvp.domain.SavedCommute
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun clearDatabase() {
        context.deleteDatabase(RoutePlanningDatabase.DATABASE_NAME)
    }

    @After
    fun cleanup() {
        context.deleteDatabase(RoutePlanningDatabase.DATABASE_NAME)
    }

    @Test
    fun savedCommuteSurvivesEncryptedDatabaseReopen() = runBlocking {
        val original = SavedCommute(
            id = "encrypted-commute",
            originLabel = "Casa",
            destinationLabel = "Rabanales",
            departureHour = 7,
            departureMinute = 30,
            createdAtEpochMillis = 1L
        )

        RoutePlanningDatabase.create(context).also { database ->
            database.savedCommuteDao().upsert(SavedCommuteEntity.fromDomain(original))
            database.close()
        }

        val databaseFile = context.getDatabasePath(RoutePlanningDatabase.DATABASE_NAME)
        val header = databaseFile.inputStream().use { input ->
            ByteArray(SQLITE_HEADER.size).also { input.read(it) }
        }
        assertFalse("Database must not expose a plain SQLite header", header.contentEquals(SQLITE_HEADER))

        RoutePlanningDatabase.create(context).also { reopened ->
            val restored = reopened.savedCommuteDao().observeAll().first().single().toDomain()
            assertEquals(original, restored)
            val restoredById = reopened.savedCommuteDao().findById(original.id)?.toDomain()
            assertEquals(original, restoredById)
            reopened.close()
        }
    }

    private companion object {
        val SQLITE_HEADER = "SQLite format 3\u0000".encodeToByteArray()
    }
}
