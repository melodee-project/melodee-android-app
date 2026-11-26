package org.melodee.android.live.auth

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.melodee.android.live.api.MelodeeApi
import org.melodee.android.live.api.OriginPolicy
import org.melodee.core.auth.PersistedRefreshState
import org.melodee.core.auth.SessionStore
import org.melodee.core.auth.StoredSession

class LiveSessionManagerTest {
    private lateinit var server: MockWebServer
    private lateinit var api: MelodeeApi
    private lateinit var store: MemorySessionStore
    private lateinit var accounts: MemoryAccountStore
    private var cleared = 0

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        api = MelodeeApi(OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build())
        store = MemorySessionStore()
        accounts = MemoryAccountStore()
        cleared = 0
    }

    @After fun tearDown() {
        server.shutdown()
    }

    private fun manager(scope: kotlinx.coroutines.CoroutineScope) = LiveSessionManager(
        api = api,
        store = store,
        accountStore = accounts,
        scope = scope,
        allowInsecureLoopback = true,
        onSessionCleared = { cleared++ },
        nowMillis = { 1_000L },
    )

    @Test fun `login stores account and credentials only after a complete response`() = runTest {
        server.enqueue(authResponse())
        val manager = manager(this)
        val result = manager.login(server.url("/").toString(), "alice", "secret")
        assertTrue(result is LiveLoginResult.Success)
        val account = (result as LiveLoginResult.Success).account
        assertEquals("alice", account.username)
        assertEquals("user-1", store.value!!.tokens.account.accountId)
        assertEquals("access-1", store.value!!.tokens.accessToken)
        assertEquals(account, accounts.current())
    }

    @Test fun `login rejects cleartext remote origins without touching the network`() = runTest {
        val manager = manager(this)
        assertTrue(manager.login("http://music.example", "alice", "secret") is LiveLoginResult.Failure)
        assertEquals(0, server.requestCount)
    }

    @Test fun `401 is recovered once through refresh and the request is replayed`() = runTest {
        server.enqueue(authResponse())
        val manager = manager(this)
        manager.login(server.url("/").toString(), "alice", "secret")
        server.takeRequest() // login
        server.enqueue(json("""{"code":"access_token_expired","message":"expired"}""", 401))
        server.enqueue(authResponse(token = "access-2"))
        server.enqueue(json("""{"meta":{"totalCount":0},"data":[]}"""))
        val page = manager.authorized { origin, token -> api.albums(origin, token) }
        assertEquals(0, page.data.size)
        assertEquals(4, server.requestCount)
        assertEquals("access-2", store.value!!.tokens.accessToken)
    }

    @Test fun `logout clears local state and never leaves an account available`() = runTest {
        server.enqueue(authResponse())
        val manager = manager(this)
        manager.login(server.url("/").toString(), "alice", "secret")
        server.enqueue(MockResponse().setResponseCode(200))
        manager.logout()
        assertNull(store.value)
        assertNull(accounts.current())
        assertTrue(cleared >= 1)
    }

    @Test fun `last successful server is remembered across logout`() = runTest {
        server.enqueue(authResponse())
        val manager = manager(this)
        manager.login(server.url("/").toString(), "alice", "secret")
        val remembered = accounts.lastServerValue
        assertTrue(remembered != null && remembered.startsWith("http://localhost"))
        server.enqueue(MockResponse().setResponseCode(200))
        manager.logout()
        assertEquals(remembered, accounts.lastServerValue)
    }

    @Test fun `account switch clears old account state`() = runTest {
        server.enqueue(authResponse())
        val manager = manager(this)
        manager.login(server.url("/").toString(), "alice", "secret")
        server.enqueue(authResponse(userId = "user-2", username = "bob", token = "access-bob"))
        manager.login(server.url("/").toString(), "bob", "secret")
        assertEquals("user-2", store.value!!.tokens.account.accountId)
        assertEquals(1, cleared)
    }

    private fun authResponse(
        userId: String = "user-1",
        username: String = "alice",
        token: String = "access-1",
    ) = json("""{"user":{"id":"$userId","username":"$username"},"serverVersion":"1",
        "token":"$token","expiresAt":"2030-01-01T00:00:00Z","refreshToken":"refresh-1"}""")

    private fun json(body: String, status: Int = 200) = MockResponse()
        .setResponseCode(status)
        .addHeader("Content-Type", "application/json")
        .setBody(body.trimIndent())

    private class MemorySessionStore : SessionStore {
        var value: StoredSession? = null
        override suspend fun read(): StoredSession? = value
        override suspend fun write(session: StoredSession?) { value = session }
    }

    private class MemoryAccountStore : AccountMetadataStore {
        private val state = MutableStateFlow<LiveAccount?>(null)
        override val accountFlow: Flow<LiveAccount?> = state
        override suspend fun current(): LiveAccount? = state.value
        override suspend fun save(metadata: LiveAccount) { state.value = metadata }
        override suspend fun clear() { state.value = null }
        var lastServerValue: String? = null
        override suspend fun deviceId(): String = "device-1"
        override suspend fun lastServer(): String? = lastServerValue ?: state.value?.serverId
        override suspend fun saveLastServer(serverId: String) { lastServerValue = serverId }
    }
}
