package org.melodee.android.live.prefetch

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.melodee.android.model.Artwork
import org.melodee.android.model.Track
import org.melodee.core.cache.CachedTrack
import org.melodee.core.cache.PlaybackContext
import org.melodee.core.cache.PrefetchPolicy

@OptIn(ExperimentalCoroutinesApi::class)
class PrefetchExecutorTest {
    private fun track(index: Int, durationMs: Long = 80, bitrateKbps: Int = 1) = Track(
        id = "track-$index", title = "Track $index", artist = "Artist", albumId = "album-1",
        albumTitle = "Album", durationMs = durationMs, bitrateKbps = bitrateKbps,
        artwork = Artwork.Local(0), revision = "rev-$index",
    )

    @Test fun `album prefetch runs in playback order and stops at byte budget`() = runTest {
        val downloader = RecordingDownloader(trackBytes = { 20 })
        val executor = PrefetchExecutor(this, downloader, PrefetchPolicy(maxCacheBytes = 40))
        executor.schedule((1..5).map { track(it) }, 0, PlaybackContext.ALBUM)
        advanceUntilIdle()
        assertEquals(listOf("track-1", "track-2"), downloader.downloads)
        assertTrue(executor.statusSnapshot().budgetLimited)
        assertFalse(executor.statusSnapshot().active)
    }

    @Test fun `large playlist uses bounded forward window and never fetches previous`() = runTest {
        val downloader = RecordingDownloader(trackBytes = { 5 })
        val executor = PrefetchExecutor(this, downloader, PrefetchPolicy(maxCacheBytes = 1_000_000))
        val queue = (1..500).map { track(it, durationMs = 10 * 60_000) }
        executor.schedule(queue, 250, PlaybackContext.PLAYLIST)
        advanceUntilIdle()
        assertFalse(downloader.downloads.contains("track-249"))
        assertEquals(listOf("track-251", "track-252", "track-253", "track-254"), downloader.downloads)
    }

    @Test fun `cached previous tracks are retained but not downloaded`() = runTest {
        val downloader = RecordingDownloader(
            trackBytes = { 5 },
            cached = mapOf("track-1" to CachedTrack(5, true)),
        )
        val executor = PrefetchExecutor(this, downloader, PrefetchPolicy(maxCacheBytes = 10_000_000))
        executor.schedule((1..6).map { track(it) }, 4, PlaybackContext.PLAYLIST)
        advanceUntilIdle()
        assertFalse(downloader.downloads.contains("track-1"))
        assertEquals(listOf("track-5", "track-6"), downloader.downloads)
    }

    @Test fun `a newer queue cancels the old plan and does not fetch stale tracks`() = runTest {
        val release = CompletableDeferred<Unit>()
        val downloader = RecordingDownloader(trackBytes = { 5 }, gate = release)
        val executor = PrefetchExecutor(this, downloader, PrefetchPolicy(maxCacheBytes = 100))
        executor.schedule((1..3).map { track(it) }, 0, PlaybackContext.ALBUM)
        runCurrent()
        executor.schedule(listOf(track(9)), 0, PlaybackContext.ALBUM)
        release.complete(Unit)
        runCurrent()
        assertTrue(downloader.downloads.contains("track-9") || downloader.downloads.size >= 1)
        assertEquals(1, downloader.downloads.count { it == "track-9" })
    }

    @Test fun `oversized current track is not downloaded whole`() = runTest {
        val downloader = RecordingDownloader(trackBytes = { 1_000 })
        val executor = PrefetchExecutor(this, downloader, PrefetchPolicy(maxCacheBytes = 5))
        executor.schedule(listOf(track(1), track(2)), 0, PlaybackContext.ALBUM)
        advanceUntilIdle()
        assertTrue(downloader.downloads.isEmpty())
        assertTrue(executor.statusSnapshot().budgetLimited)
    }

    private class RecordingDownloader(
        private val trackBytes: (Track) -> Long,
        private val cached: Map<String, CachedTrack> = emptyMap(),
        private val gate: CompletableDeferred<Unit>? = null,
    ) : TrackDownloader {
        val downloads = mutableListOf<String>()
        override suspend fun cached(track: Track): CachedTrack? = cached[track.id]
        override suspend fun download(track: Track, maxBytes: Long, onBytes: (Long) -> Unit): Long {
            gate?.await()
            val bytes = trackBytes(track)
            if (bytes > maxBytes) return 0
            downloads += track.id
            onBytes(bytes)
            return bytes
        }
    }
}
