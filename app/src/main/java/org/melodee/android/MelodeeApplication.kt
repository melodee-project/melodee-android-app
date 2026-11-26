package org.melodee.android

import android.app.Application
import androidx.room.Room
import org.melodee.android.data.DemoCatalog
import org.melodee.android.data.PlaybackDatabase
import org.melodee.android.data.UserPreferences

class MelodeeApplication : Application() {
    val catalog by lazy { DemoCatalog(this) }
    val database by lazy {
        Room.databaseBuilder(this, PlaybackDatabase::class.java, "melodee.db").build()
    }
    val preferences by lazy { UserPreferences(this) }
}

