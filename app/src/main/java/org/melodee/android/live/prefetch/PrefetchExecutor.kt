package org.melodee.android.live.prefetch

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.melodee.android.model.Track
import org.melodee.core.cache.CachedTrack
import org.melodee.core.cache.PlaybackContext
import org.melodee.core.cache.PrefetchPlanner
import org.melodee.core.cache.PrefetchPolicy
import org.melodee.core.cache.QueueTrack

data class PrefetchStatus(
    val active: Boolean = false,
    val plannedTrackIds: List<String> = emptyList(),
    val completedTrackIds: List<String> = emptyList(),
    val bytesFetched: Long = 0,
    val currentTrackId: String? = null,
    val budgetLimited: Boolean = false,
    val lastError: String? = null,
)

interface TrackDownloader {
    suspend fun cached(track: Track): CachedTrack?
    suspend fun download(track: Track, maxBytes: Long, onBytes: (Long) -> Unit): Long
}

/**
 * Turns planner output into bounded, cancellable work. It never fetches previous
 * tracks and never exceeds the hard byte budget; queue/account generation changes
 * cancel the old plan before it can commit more speculative bytes.
 */
class PrefetchExecutor(
    private val scope: CoroutineScope,
    private val downloader: TrackDownloader,
    private val policy: PrefetchPolicy = PrefetchPolicy(),
    private val planner: PrefetchPlanner = PrefetchPlanner(),
) {
    private var job: Job? = null
    private var generation = 0L
    private var lastSignature: String? = null
    private val mutableState = MutableStateFlow(PrefetchStatus())
    val status: StateFlow<PrefetchStatus> = mutableState.asStateFlow()

    fun schedule(queue: List<Track>, currentIndex: Int, context: PlaybackContext) {
        if (queue.isEmpty() || currentIndex !in queue.indices) {
            cancel()
            return
        }
        val signature = queue.joinToString("|") { it.id } + "#" + currentIndex + "#" + context.name
        if (signature == lastSignature && job?.isActive == true) return
        lastSignature = signature
        job?.cancel()
        val planGeneration = ++generation
        job = scope.launch {
            mutableState.value = PrefetchStatus(active = true)
            val cached = buildMap {
                queue.forEach { track -> downloader.cached(track)?.let { put(track.id, it) } }
            }
            val plan = planner.plan(
                queue = queue.map { QueueTrack(it.id, it.durationMs, it.estimatedBytes()) },
                currentIndex = currentIndex,
                context = context,
                cached = cached,
                policy = policy,
            )
            mutableState.value = mutableState.value.copy(
                plannedTrackIds = plan.fetchTrackIds,
                currentTrackId = plan.currentTrackId,
                budgetLimited = plan.budgetLimited,
            )
            var bytes = 0L
            val completed = mutableListOf<String>()
            for (id in plan.fetchTrackIds) {
                if (planGeneration != generation || !isActive) break
                val track = queue.firstOrNull { it.id == id } ?: continue
                val remaining = policy.maxCacheBytes - bytes
                if (remaining <= 0) {
                    mutableState.value = mutableState.value.copy(budgetLimited = true)
                    break
                }
                try {
                    var downloaded = 0L
                    val fetched = downloader.download(track, remaining) { delta ->
                        downloaded += delta.coerceAtLeast(0)
                        mutableState.value = mutableState.value.copy(bytesFetched = bytes + downloaded)
                    }
                    bytes += fetched.coerceAtLeast(0)
                    completed += id
                    mutableState.value = mutableState.value.copy(
                        completedTrackIds = completed.toList(), bytesFetched = bytes,
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    if (planGeneration != generation) break
                    mutableState.value = mutableState.value.copy(
                        lastError = error.javaClass.simpleName, active = false,
                    )
                    break
                }
            }
            mutableState.value = mutableState.value.copy(active = false, bytesFetched = bytes)
        }
    }

    fun cancel() {
        generation++
        job?.cancel()
        job = null
        mutableState.value = mutableState.value.copy(active = false)
    }

    fun statusSnapshot(): PrefetchStatus = mutableState.value

    private fun Track.estimatedBytes(): Long? =
        if (durationMs > 0 && bitrateKbps > 0) (durationMs * bitrateKbps / 8).coerceAtLeast(1) else null
}
