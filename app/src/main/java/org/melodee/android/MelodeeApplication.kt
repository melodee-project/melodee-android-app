package org.melodee.android

import android.app.Application
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.melodee.android.data.PlaybackDatabase
import org.melodee.android.data.UserPreferences

class MelodeeApplication : Application() {
    val container by lazy { MelodeeContainer(this) }
    val database by lazy {
        Room.databaseBuilder(this, PlaybackDatabase::class.java, "melodee.db")
            .addMigrations(MIGRATION_1_2)
            .build()
    }
    val preferences by lazy { UserPreferences(this) }
}


/** Adds account-scoped metadata and live queue storage. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `cached_catalog` (`accountKey` TEXT NOT NULL, " +
                "`kind` TEXT NOT NULL, `id` TEXT NOT NULL, `payload` TEXT NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`accountKey`, `kind`, `id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `queue_snapshot` (`accountKey` TEXT NOT NULL, " +
                "`payload` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`accountKey`))",
        )
    }
}
