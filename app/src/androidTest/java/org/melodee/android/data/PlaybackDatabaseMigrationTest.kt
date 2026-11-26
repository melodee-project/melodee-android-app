package org.melodee.android.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.melodee.android.MIGRATION_1_2

@RunWith(AndroidJUnit4::class)
class PlaybackDatabaseMigrationTest {
    private val databaseName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PlaybackDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test fun migrationOneToTwoKeepsDemoBookmarkAndAddsLiveTables() {
        helper.createDatabase(databaseName, 1).apply {
            execSQL(
                "INSERT INTO playback_bookmark (id, trackIds, currentIndex, positionMs, shuffle, repeatMode, shuffleOrder) " +
                    "VALUES (1, 'first-light|open-road', 1, 1200, 0, 0, '')",
            )
            close()
        }
        val upgraded = helper.runMigrationsAndValidate(databaseName, 2, true, MIGRATION_1_2)
        upgraded.query("SELECT trackIds, currentIndex FROM playback_bookmark WHERE id = 1").use { cursor ->
            cursor.moveToFirst()
            assertEquals("first-light|open-road", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
        }
        upgraded.execSQL("INSERT INTO cached_catalog (accountKey, kind, id, payload, updatedAt) VALUES ('s|a', 'album', 'x', '{}', 1)")
        upgraded.execSQL("INSERT INTO queue_snapshot (accountKey, payload, updatedAt) VALUES ('s|a', '{}', 1)")
        upgraded.close()
    }
}
