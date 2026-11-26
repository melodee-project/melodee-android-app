package org.melodee.core.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrefetchPlannerTest {
    private val planner = PrefetchPlanner()
    private fun tracks(count: Int, duration: Long = 4 * 60_000, bytes: Long = 10) =
        (1..count).map { QueueTrack("track-$it", duration, bytes) }

    @Test fun `album targets every remaining track in order`() {
        val plan = planner.plan(tracks(12), 2, PlaybackContext.ALBUM)
        assertEquals((3..12).map { "track-$it" }, plan.fetchTrackIds)
        assertEquals(36 * 60_000L, plan.plannedForwardDurationMs)
        assertFalse(plan.budgetLimited)
    }

    @Test fun `five hundred track playlist plans a bounded duration window`() {
        val plan = planner.plan(tracks(500), 10, PlaybackContext.PLAYLIST)
        assertEquals((11..16).map { "track-$it" }, plan.fetchTrackIds)
        assertEquals(20 * 60_000L, plan.plannedForwardDurationMs)
        assertTrue(plan.fetchTrackIds.size < 500)
    }

    @Test fun `count floor keeps three long tracks even when duration already met`() {
        val plan = planner.plan(tracks(40, duration = 12 * 60_000), 0, PlaybackContext.PLAYLIST)
        assertEquals(listOf("track-1", "track-2", "track-3", "track-4"), plan.fetchTrackIds)
    }

    @Test fun `retains three cached previous tracks without fetching missing previous songs`() {
        val cached = mapOf("track-3" to CachedTrack(10, true), "track-5" to CachedTrack(10, true))
        val plan = planner.plan(tracks(15), 5, PlaybackContext.PLAYLIST, cached)
        assertEquals(setOf("track-3", "track-5"), plan.retainedTrackIds)
        assertFalse(plan.fetchTrackIds.contains("track-4"))
        assertFalse(plan.fetchTrackIds.contains("track-3"))
    }

    @Test fun `forward playback takes priority over retaining old cache`() {
        val cached = (1..4).associate { "track-$it" to CachedTrack(10, true) }
        val plan = planner.plan(
            tracks(20), 4, PlaybackContext.PLAYLIST, cached,
            PrefetchPolicy(maxCacheBytes = 40, forwardDurationMs = 0),
        )
        assertEquals(listOf("track-5", "track-6", "track-7", "track-8"), plan.fetchTrackIds)
        assertTrue(plan.retainedTrackIds.isEmpty())
        assertEquals(40L, plan.estimatedReservedBytes)
    }

    @Test fun `oversized album stops at contiguous capacity boundary`() {
        val plan = planner.plan(tracks(12), 0, PlaybackContext.ALBUM, policy = PrefetchPolicy(maxCacheBytes = 35))
        assertEquals(listOf("track-1", "track-2", "track-3"), plan.fetchTrackIds)
        assertTrue(plan.budgetLimited)
        assertEquals(30L, plan.estimatedReservedBytes)
    }

    @Test fun `oversized current track streams in spans instead of scheduling whole file`() {
        val plan = planner.plan(tracks(12, bytes = 1_000), 0, PlaybackContext.ALBUM, policy = PrefetchPolicy(maxCacheBytes = 100))
        assertTrue(plan.fetchTrackIds.isEmpty())
        assertEquals(setOf("track-1"), plan.protectedTrackIds)
        assertEquals(100L, plan.estimatedReservedBytes)
        assertTrue(plan.budgetLimited)
    }

    @Test fun `uses actual supplied shuffle order and does not sort by album`() {
        val shuffled = listOf(QueueTrack("c", 60_000, 10), QueueTrack("a", 60_000, 10), QueueTrack("b", 60_000, 10))
        assertEquals(listOf("c", "a", "b"), planner.plan(shuffled, 0, PlaybackContext.PLAYLIST).fetchTrackIds)
    }

    @Test fun `duplicate queue entries share one audio fetch`() {
        val repeated = listOf(QueueTrack("a", 60_000, 10), QueueTrack("b", 60_000, 10), QueueTrack("a", 60_000, 10))
        val plan = planner.plan(repeated, 0, PlaybackContext.ALBUM)
        assertEquals(listOf("a", "b"), plan.fetchTrackIds)
        assertEquals(20L, plan.estimatedReservedBytes)
        assertEquals(120_000L, plan.plannedForwardDurationMs)
    }

    @Test fun `complete cached tracks are protected without refetch`() {
        val plan = planner.plan(tracks(3), 0, PlaybackContext.ALBUM, mapOf("track-2" to CachedTrack(10, true)))
        assertEquals(listOf("track-1", "track-3"), plan.fetchTrackIds)
        assertTrue(plan.protectedTrackIds.contains("track-2"))
    }

    @Test fun `unknown byte sizes use conservative bounded estimate`() {
        val queue = listOf(QueueTrack("a", 60_000), QueueTrack("b", 60_000))
        val plan = planner.plan(queue, 0, PlaybackContext.ALBUM, policy = PrefetchPolicy(maxCacheBytes = 50, unknownTrackBytes = 30))
        assertEquals(listOf("a"), plan.fetchTrackIds)
        assertTrue(plan.budgetLimited)
    }

    @Test fun `empty queue has no work`() {
        val plan = planner.plan(emptyList(), 0, PlaybackContext.ALBUM)
        assertTrue(plan.fetchTrackIds.isEmpty())
        assertEquals(null, plan.currentTrackId)
    }

    @Test fun `audio keys are isolated by account revision and rendition`() {
        val key = TrackKey("server-a", "alice", "track", "rev-1")
        assertEquals(key.cacheKey(), key.copy().cacheKey())
        assertTrue(key.cacheKey() != key.copy(accountId = "bob").cacheKey())
        assertTrue(key.cacheKey() != key.copy(serverId = "server-b").cacheKey())
        assertTrue(key.cacheKey() != key.copy(revision = "rev-2").cacheKey())
        assertTrue(key.cacheKey() != key.copy(rendition = "aac-128").cacheKey())
        assertTrue(key.cacheKey().matches(Regex("audio-v1-[0-9a-f]{64}")))
    }

    @Test fun `length framing prevents delimiter ambiguity`() {
        val first = TrackKey("a|b", "c", "d", "e")
        val second = TrackKey("a", "b|c", "d", "e")
        assertTrue(first.cacheKey() != second.cacheKey())
    }
}
