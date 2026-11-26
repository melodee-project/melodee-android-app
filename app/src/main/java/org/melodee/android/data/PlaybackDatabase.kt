package org.melodee.android.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

/** Stable demo IDs only. Future live bookmarks must also include server/account identity. */
@Entity(tableName = "playback_bookmark")
data class PlaybackBookmark(
    @PrimaryKey val id: Int = 1,
    val trackIds: String,
    val currentIndex: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeatMode: Int,
    /** Timeline indices in actual shuffle traversal order, not a newly generated order. */
    val shuffleOrder: String = "",
)

@Dao
interface PlaybackDao {
    @Query("SELECT * FROM playback_bookmark WHERE id = 1")
    suspend fun load(): PlaybackBookmark?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(bookmark: PlaybackBookmark)

    @Query("DELETE FROM playback_bookmark")
    suspend fun clear()
}

@Database(entities = [PlaybackBookmark::class], version = 1, exportSchema = true)
abstract class PlaybackDatabase : RoomDatabase() {
    abstract fun playbackDao(): PlaybackDao
}

