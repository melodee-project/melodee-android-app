package org.melodee.android.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

/** Legacy row retained by the v1 schema; live queues are stored per account in queue_snapshot. */
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

@Entity(tableName = "cached_catalog", primaryKeys = ["accountKey", "kind", "id"])
data class CachedCatalogEntity(
    val accountKey: String,
    val kind: String,
    val id: String,
    val payload: String,
    val updatedAt: Long,
)

@Dao
interface CatalogCacheDao {
    @Query("SELECT * FROM cached_catalog WHERE accountKey = :accountKey AND kind = :kind ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun loadKind(accountKey: String, kind: String, limit: Int): List<CachedCatalogEntity>

    @Query("SELECT * FROM cached_catalog WHERE accountKey = :accountKey AND kind = :kind AND id = :id")
    suspend fun load(accountKey: String, kind: String, id: String): CachedCatalogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entry: CachedCatalogEntity)

    @Query("DELETE FROM cached_catalog WHERE accountKey = :accountKey")
    suspend fun deleteAccount(accountKey: String)

    @Query("DELETE FROM cached_catalog")
    suspend fun clear()
}

@Dao
interface QueueSnapshotDao {
    @Query("SELECT * FROM queue_snapshot WHERE accountKey = :accountKey")
    suspend fun load(accountKey: String): QueueSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(snapshot: QueueSnapshotEntity)

    @Query("DELETE FROM queue_snapshot WHERE accountKey = :accountKey")
    suspend fun delete(accountKey: String)

    @Query("DELETE FROM queue_snapshot")
    suspend fun clear()
}

@Entity(tableName = "queue_snapshot")
data class QueueSnapshotEntity(
    @PrimaryKey val accountKey: String,
    val payload: String,
    val updatedAt: Long,
)

@Database(
    entities = [PlaybackBookmark::class, CachedCatalogEntity::class, QueueSnapshotEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class PlaybackDatabase : RoomDatabase() {
    abstract fun playbackDao(): PlaybackDao
    abstract fun catalogCacheDao(): CatalogCacheDao
    abstract fun queueSnapshotDao(): QueueSnapshotDao
}

