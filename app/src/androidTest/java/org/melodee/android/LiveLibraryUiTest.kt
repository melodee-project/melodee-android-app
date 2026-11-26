package org.melodee.android

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.melodee.android.live.auth.LiveLoginResult
import org.melodee.android.testing.MockMelodeeServer

@RunWith(AndroidJUnit4::class)
class LiveLibraryUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private lateinit var server: MockMelodeeServer
    private lateinit var app: MelodeeApplication

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        server = MockMelodeeServer(InstrumentationRegistry.getInstrumentation().targetContext)
        runBlocking { app.container.session.logout() }
        val result = runBlocking { app.container.session.login(server.url, "alice", "secret") }
        assertTrue(result is LiveLoginResult.Success)
    }

    @After fun tearDown() {
        runBlocking { app.container.session.logout() }
        server.close()
    }

    @Test fun connectedLibraryRendersOnPhone() {
        compose.waitUntil(20_000) {
            compose.onAllNodesWithText("Northern Lights").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Northern Lights").assertExists()
        compose.onNodeWithText("Melodee").assertExists()

        runBlocking { app.container.session.logout() }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Sign in to Melodee").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(compose.onAllNodesWithText(server.url.trimEnd('/')).fetchSemanticsNodes().isNotEmpty())
    }
}
