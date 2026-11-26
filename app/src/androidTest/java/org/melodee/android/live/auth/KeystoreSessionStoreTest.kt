package org.melodee.android.live.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.melodee.core.auth.AccountScope
import org.melodee.core.auth.PersistedRefreshState
import org.melodee.core.auth.StoredSession
import org.melodee.core.auth.TokenBundle

@RunWith(AndroidJUnit4::class)
class KeystoreSessionStoreTest {
    private lateinit var context: Context
    private lateinit var store: KeystoreSessionStore
    private lateinit var file: File
    private val keyAlias = "melodee.test.session"
    private val fileName = "session-test.bin"

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = KeystoreSessionStore(context, keyAlias, fileName)
        file = File(context.noBackupFilesDir, fileName)
        store.deleteForTest()
    }

    @After fun tearDown() {
        store.deleteForTest()
        java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(keyAlias)
    }

    private fun session(token: String, generation: Long = 1): StoredSession = StoredSession(
        tokens = TokenBundle(
            account = AccountScope("https://music.example", "user-1"),
            accessToken = token,
            refreshToken = "refresh-$token",
            expiresAtMillis = 4_000_000_000_000,
            generation = generation,
        ),
        refreshState = PersistedRefreshState.READY,
    )

    @Test fun encryptedRoundTripAndAtomicReplacement() = runBlocking {
        store.write(session("access-one"))
        assertTrue(file.exists())
        assertFalse(file.readBytes().decodeToString().contains("access-one"))
        assertFalse(file.readBytes().decodeToString().contains("refresh-access-one"))
        assertEquals("access-one", store.read()!!.tokens.accessToken)

        store.write(session("access-two", generation = 2))
        val restored = store.read()!!
        assertEquals("access-two", restored.tokens.accessToken)
        assertEquals(2, restored.tokens.generation)
        assertEquals(PersistedRefreshState.READY, restored.refreshState)
    }

    @Test fun pendingRotationMarkerSurvivesRoundTrip() = runBlocking {
        val pending = session("access-one").copy(refreshState = PersistedRefreshState.IN_FLIGHT)
        store.write(pending)
        assertEquals(PersistedRefreshState.IN_FLIGHT, store.read()!!.refreshState)
    }

    @Test fun clearDeletesCiphertext() = runBlocking {
        store.write(session("access-one"))
        store.write(null)
        assertFalse(file.exists())
        assertNull(store.read())
    }

    @Test fun corruptCiphertextFailsClosedAndIsRemoved() = runBlocking {
        file.writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7))
        assertNull(store.read())
        assertFalse(file.exists())
    }
}
