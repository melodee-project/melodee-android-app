package org.melodee.android

import android.content.ComponentName
import android.os.SystemClock
import androidx.media3.common.Player
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.melodee.android.playback.PlaybackService

/** Real Android service, real Media3 controller and real bundled audio; no mocked player. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class MediaLibrarySmokeTest {
    @Test
    fun browserResolvesCatalogAndPlaysAnAlbumQueue() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val connection = onMain {
                MediaBrowser.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java)))
                    .buildAsync()
            }
            val browser = connection.get(15, TimeUnit.SECONDS)
            try {
                val root = onMain { browser.getLibraryRoot(null) }.get(10, TimeUnit.SECONDS)
                assertEquals(LibraryResult.RESULT_SUCCESS, root.resultCode)
                assertEquals("Melodee", root.value!!.mediaMetadata.title.toString())
                val roots = onMain { browser.getChildren("root", 0, 20, null) }.get(10, TimeUnit.SECONDS)
                assertEquals(listOf("home", "albums", "playlists"), roots.value!!.map { item -> item.mediaId })
                val album = onMain { browser.getChildren("album:afterglow", 0, 20, null) }.get(10, TimeUnit.SECONDS)
                assertEquals(3, album.value!!.size)
                assertTrue(album.value!!.all { item -> item.mediaMetadata.isPlayable == true })
                assertEquals(LibraryResult.RESULT_SUCCESS,
                    onMain { browser.getItem("album:afterglow") }.get(10, TimeUnit.SECONDS).resultCode)

                onMain { browser.search("Daily Mix", null) }.get(10, TimeUnit.SECONDS)
                val search = onMain { browser.getSearchResult("Daily Mix", 0, 20, null) }.get(10, TimeUnit.SECONDS)
                assertEquals(6, search.value!!.size)

                onMain {
                    browser.shuffleModeEnabled = false
                    browser.repeatMode = Player.REPEAT_MODE_OFF
                    browser.setMediaItems(album.value!!.toList(), 0, 0)
                    browser.prepare()
                    browser.play()
                }
                awaitCondition("Bundled audio never reached active playback") {
                    onMain { browser.playbackState == Player.STATE_READY && browser.currentPosition > 100 }
                }
                assertNull(onMain { browser.playerError })
                assertEquals(3, onMain { browser.mediaItemCount })
                assertEquals("first-light", onMain { browser.currentMediaItem!!.mediaId })
                onMain { browser.seekToNextMediaItem() }
                awaitCondition("Next track was not selected") {
                    onMain { browser.currentMediaItem?.mediaId == "open-road" && browser.playbackState == Player.STATE_READY }
                }
                assertNull(onMain { browser.playerError })
            } finally {
                onMain { browser.pause(); browser.release() }
            }
        }
    }

    private fun awaitCondition(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(50)
        }
        assertTrue(message, condition())
    }

    private fun <T> onMain(block: () -> T): T {
        val result = AtomicReference<T>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result.set(block()) }
        return result.get()
    }
}
