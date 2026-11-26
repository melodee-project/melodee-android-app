package org.melodee.core.cache

enum class PlaybackContext { ALBUM, PLAYLIST, ARTIST, RADIO, MANUAL }

/** Input order MUST be the player's actual next/previous order, including shuffle. */
data class QueueTrack(val id: String, val durationMs: Long, val estimatedBytes: Long? = null) {
    init {
        require(id.isNotBlank())
        require(durationMs >= 0)
        require(estimatedBytes == null || estimatedBytes > 0)
    }
}

data class CachedTrack(val bytes: Long, val complete: Boolean) {
    init { require(bytes >= 0) }
}

/** Initial engineering defaults; tune against real devices and lossless libraries. */
data class PrefetchPolicy(
    val previousTracks: Int = 3,
    val nextTracks: Int = 3,
    val forwardDurationMs: Long = 20 * 60 * 1_000L,
    val maxCacheBytes: Long = 1_024 * 1_024 * 1_024L,
    val unknownTrackBytes: Long = 32 * 1_024 * 1_024L,
    val smallManualQueueSize: Int = 12,
) {
    init {
        require(previousTracks >= 0 && nextTracks >= 0)
        require(forwardDurationMs >= 0 && maxCacheBytes > 0 && unknownTrackBytes > 0)
        require(smallManualQueueSize >= 0)
    }
}

data class PrefetchPlan(
    val currentTrackId: String?,
    /** Whole-track fetches, current first. The executor must throttle behind active playback. */
    val fetchTrackIds: List<String>,
    /** Current, admitted upcoming, then already-cached previous tracks. */
    val protectedTrackIds: Set<String>,
    /** Previous tracks already present in cache: NEVER instructions to fetch them again. */
    val retainedTrackIds: Set<String>,
    val budgetLimited: Boolean,
    /** Planned future coverage, not a claim about bytes already available offline. */
    val plannedForwardDurationMs: Long,
    val estimatedReservedBytes: Long,
)

/**
 * Pure policy, deliberately separate from Media3/networking/storage execution.
 *
 * Album context targets all remaining tracks. Bounded contexts target both count
 * and duration. Backward retention is lower priority than upcoming audio. Unknown
 * sizes are estimated; the executor MUST enforce the actual byte cap and react to
 * free-space pressure. An oversized current track reserves the budget but is not
 * scheduled as a complete fetch: the player uses bounded streaming spans instead.
 */
class PrefetchPlanner {
    fun plan(
        queue: List<QueueTrack>,
        currentIndex: Int,
        context: PlaybackContext,
        cached: Map<String, CachedTrack> = emptyMap(),
        policy: PrefetchPolicy = PrefetchPolicy(),
    ): PrefetchPlan {
        if (queue.isEmpty()) return PrefetchPlan(null, emptyList(), emptySet(), emptySet(), false, 0, 0)
        require(currentIndex in queue.indices) { "Current index must belong to the queue" }

        val current = queue[currentIndex]
        val selected = linkedSetOf(current.id)
        val retained = linkedSetOf<String>()
        val fetch = mutableListOf<String>()
        var reserved = 0L
        var limited = false
        var futureDuration = 0L

        fun fullSize(track: QueueTrack): Long = if (cached[track.id]?.complete == true) {
            cached.getValue(track.id).bytes.coerceAtLeast(1)
        } else {
            maxOf(track.estimatedBytes ?: policy.unknownTrackBytes, cached[track.id]?.bytes ?: 0)
        }

        val currentSize = fullSize(current)
        reserved = minOf(currentSize, policy.maxCacheBytes)
        if (currentSize > policy.maxCacheBytes) {
            limited = true
        } else if (cached[current.id]?.complete != true) {
            fetch += current.id
        }

        val allRemaining = context == PlaybackContext.ALBUM ||
            (context == PlaybackContext.MANUAL && queue.size - currentIndex <= policy.smallManualQueueSize)
        var futureCount = 0
        for (index in currentIndex + 1 until queue.size) {
            if (!allRemaining && futureCount >= policy.nextTracks && futureDuration >= policy.forwardDurationMs) break
            val track = queue[index]
            if (track.id !in selected) {
                val size = fullSize(track)
                if (size > policy.maxCacheBytes - reserved) {
                    limited = true
                    break // Preserve contiguous coverage; do not skip ahead to distant small tracks.
                }
                selected += track.id
                reserved += size
                if (cached[track.id]?.complete != true) fetch += track.id
            }
            futureCount++
            futureDuration = saturatingAdd(futureDuration, track.durationMs)
        }

        val start = if (context == PlaybackContext.ALBUM) 0 else maxOf(0, currentIndex - policy.previousTracks)
        for (index in currentIndex - 1 downTo start) {
            val track = queue[index]
            val existing = cached[track.id] ?: continue
            if (existing.bytes == 0L) continue
            if (track.id in selected) {
                retained += track.id
                continue
            }
            if (existing.bytes <= policy.maxCacheBytes - reserved) {
                selected += track.id
                retained += track.id
                reserved += existing.bytes
            }
        }

        return PrefetchPlan(current.id, fetch, selected, retained, limited, futureDuration, reserved)
    }

    private fun saturatingAdd(a: Long, b: Long): Long = if (b > Long.MAX_VALUE - a) Long.MAX_VALUE else a + b
}
