package org.melodee.android

import android.content.ComponentName
import android.os.SystemClock
import androidx.media3.common.Player
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.melodee.android.live.auth.LiveLoginResult
import org.melodee.android.testing.MockMelodeeServer
import org.melodee.android.playback.PlaybackService

@RunWith(AndroidJUnit4::class)
class LivePlaybackSmokeTest {
    private lateinit var server: MockMelodeeServer
    private lateinit var app: MelodeeApplication

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        server = MockMelodeeServer(InstrumentationRegistry.getInstrumentation().context)
        runBlocking { app.container.session.logout() }
        val login = runBlocking {
            app.container.session.login(server.url, "alice", "secret")
        }
        assertTrue(login is LiveLoginResult.Success)
    }

    @After fun tearDown() {
        runBlocking { app.container.session.logout() }
        server.close()
    }

    @Test fun liveAlbumPlaysThroughBrowserAndSharedSession() {
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
                assertEquals("live:root", root.value!!.mediaId)
                val albums = onMain { browser.getChildren("live:albums", 1, 20, null) }.get(10, TimeUnit.SECONDS)
                assertEquals(listOf("live:album:album-1"), albums.value!!.map { it.mediaId })
                val tracks = onMain { browser.getChildren("live:album:album-1", 1, 20, null) }.get(10, TimeUnit.SECONDS)
                assertEquals(2, tracks.value!!.size)
                assertTrue(tracks.value!!.all { it.mediaMetadata.isPlayable == true })

                onMain {
                    browser.setMediaItems(tracks.value!!.toList(), 0, 0)
                    browser.prepare()
                    browser.play()
                }
                awaitCondition("Live signed audio never reached active playback") {
                    onMain { browser.playbackState == Player.STATE_READY && browser.currentPosition > 100 }
                }
                assertNull(onMain { browser.playerError })
                assertEquals("Solar Wind", onMain { browser.mediaMetadata.title.toString() })
                assertEquals(2, onMain { browser.mediaItemCount })
            } finally {
                onMain { browser.pause(); browser.release() }
            }
        }
    }

    private fun awaitCondition(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 20_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        assertTrue(message, condition())
    }

    private fun <T> onMain(block: () -> T): T {
        val result = AtomicReference<T>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result.set(block()) }
        return result.get()
    }

}
