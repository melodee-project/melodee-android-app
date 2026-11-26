package org.melodee.core.auth

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionCoordinatorTest {
    private fun tokens(expiry: Long = 1_000) = TokenBundle(
        AccountScope("server", "alice"), "access-old", "refresh-old", expiry,
    )

    @Test fun `valid session returns token without contacting server`() = runTest {
        val store = MemoryStore(StoredSession(tokens(1_000_000)))
        var calls = 0
        val coordinator = SessionCoordinator(store, { calls++; error("Unexpected refresh") }, this, { 10 })
        val result = coordinator.accessToken() as AccessResult.Available
        assertEquals("access-old", result.accessToken)
        assertEquals(0, calls)
    }

    @Test fun `fifty concurrent requests perform one refresh and share new credentials`() = runTest {
        val store = MemoryStore(StoredSession(tokens()))
        var calls = 0
        val coordinator = SessionCoordinator(store, {
            calls++
            delay(100)
            RefreshResult.Accepted("access-new", "refresh-new", 1_000_000)
        }, this, { 2_000 })
        val results = List(50) { async { coordinator.accessToken() } }.awaitAll()
        assertEquals(1, calls)
        assertTrue(results.all { (it as AccessResult.Available).accessToken == "access-new" })
        assertEquals(1L, store.value!!.tokens.generation)
        assertEquals("refresh-new", store.value!!.tokens.refreshToken)
    }

    @Test fun `near expiry renews before token expires`() = runTest {
        var calls = 0
        val coordinator = SessionCoordinator(MemoryStore(StoredSession(tokens(200_000))), {
            calls++
            RefreshResult.Accepted("new", "rotated", 1_000_000)
        }, this, { 100_000 }, renewalMarginMillis = 180_000)
        assertEquals("new", (coordinator.accessToken() as AccessResult.Available).accessToken)
        assertEquals(1, calls)
    }

    @Test fun `late unauthorized response reuses already rotated token`() = runTest {
        var calls = 0
        val coordinator = SessionCoordinator(MemoryStore(StoredSession(tokens())), {
            calls++
            RefreshResult.Accepted("access-new", "refresh-new", 1_000_000)
        }, this, { 2_000 })
        coordinator.accessToken()
        assertEquals("access-new", (coordinator.accessToken("access-old") as AccessResult.Available).accessToken)
        assertEquals(1, calls)
    }

    @Test fun `explicit rejection of current unexpired token triggers one renewal`() = runTest {
        var calls = 0
        val coordinator = SessionCoordinator(MemoryStore(StoredSession(tokens(1_000_000))), {
            calls++
            RefreshResult.Accepted("new", "rotated", 2_000_000)
        }, this, { 2_000 })
        assertEquals("new", (coordinator.accessToken("access-old") as AccessResult.Available).accessToken)
        assertEquals(1, calls)
    }

    @Test fun `known not applied temporary failure preserves credentials and uses cooldown`() = runTest {
        val store = MemoryStore(StoredSession(tokens()))
        var now = 2_000L
        var calls = 0
        val coordinator = SessionCoordinator(store, {
            calls++
            if (calls == 1) RefreshResult.Retryable(5_000)
            else RefreshResult.Accepted("new", "rotated", 1_000_000)
        }, this, { now })
        assertTrue(coordinator.accessToken() is AccessResult.RetryLater)
        assertTrue(coordinator.accessToken() is AccessResult.RetryLater)
        assertEquals(1, calls)
        assertEquals("refresh-old", store.value!!.tokens.refreshToken)
        assertEquals(AuthenticationState.TEMPORARILY_UNAVAILABLE, coordinator.state.value)
        now += 6_000
        assertTrue(coordinator.accessToken() is AccessResult.Available)
        assertEquals(2, calls)
    }

    @Test fun `temporary early renewal failure can continue using unexpired access token`() = runTest {
        val coordinator = SessionCoordinator(MemoryStore(StoredSession(tokens(200_000))), {
            RefreshResult.Retryable()
        }, this, { 100_000 })
        assertEquals("access-old", (coordinator.accessToken() as AccessResult.Available).accessToken)
    }

    @Test fun `lost refresh response never retries the rotating refresh token`() = runTest {
        val store = MemoryStore(StoredSession(tokens()))
        var calls = 0
        val coordinator = SessionCoordinator(store, {
            calls++
            throw IOException("Response lost")
        }, this, { 2_000 })
        assertEquals(AccessResult.RefreshOutcomeUnknown, coordinator.accessToken())
        assertEquals(AccessResult.RefreshOutcomeUnknown, coordinator.accessToken())
        assertEquals(1, calls)
        assertEquals(PersistedRefreshState.OUTCOME_UNKNOWN, store.value!!.refreshState)
    }

    @Test fun `process death with pending rotation restores as uncertain without replay`() = runTest {
        val store = MemoryStore(StoredSession(tokens(), PersistedRefreshState.IN_FLIGHT))
        var calls = 0
        val coordinator = SessionCoordinator(store, { calls++; error("Must not replay") }, this, { 2_000 })
        assertEquals(AccessResult.RefreshOutcomeUnknown, coordinator.accessToken())
        assertEquals(0, calls)
        assertEquals(AuthenticationState.REFRESH_OUTCOME_UNKNOWN, coordinator.state.value)
    }

    @Test fun `rotated token pair commits atomically after durable pending marker`() = runTest {
        val store = MemoryStore(StoredSession(tokens()))
        val coordinator = SessionCoordinator(store, {
            assertEquals(PersistedRefreshState.IN_FLIGHT, store.value!!.refreshState)
            RefreshResult.Accepted("new-access", "new-refresh", 1_000_000)
        }, this, { 2_000 })
        coordinator.accessToken()
        assertEquals(2, store.writes.size)
        assertEquals(PersistedRefreshState.IN_FLIGHT, store.writes[0]!!.refreshState)
        assertEquals("new-access", store.writes[1]!!.tokens.accessToken)
        assertEquals("new-refresh", store.writes[1]!!.tokens.refreshToken)
        assertEquals(PersistedRefreshState.READY, store.writes[1]!!.refreshState)
    }

    @Test fun `cancelling one waiter does not cancel shared refresh`() = runTest {
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val coordinator = SessionCoordinator(MemoryStore(StoredSession(tokens())), {
            calls++
            release.await()
            RefreshResult.Accepted("new", "rotated", 1_000_000)
        }, this, { 2_000 })
        val first = async { coordinator.accessToken() }
        runCurrent()
        val second = async { coordinator.accessToken() }
        runCurrent()
        first.cancel()
        release.complete(Unit)
        assertTrue(second.await() is AccessResult.Available)
        assertEquals(1, calls)
    }

    @Test fun `account switch rejects late completion from previous account`() = runTest {
        val store = MemoryStore(StoredSession(tokens()))
        val release = CompletableDeferred<Unit>()
        val coordinator = SessionCoordinator(store, {
            withContext(NonCancellable) { release.await() }
            RefreshResult.Accepted("alice-new", "alice-rotated", 1_000_000)
        }, this, { 2_000 })
        val oldRequest = async { coordinator.accessToken() }
        runCurrent()
        coordinator.establish(TokenBundle(AccountScope("server", "bob"), "bob-access", "bob-refresh", 1_000_000))
        release.complete(Unit)
        oldRequest.await()
        assertEquals("bob", store.value!!.tokens.account.accountId)
        assertEquals("bob-access", (coordinator.accessToken() as AccessResult.Available).accessToken)
    }

    @Test fun `revoked refresh requires sign in instead of pretending transient outage`() = runTest {
        val store = MemoryStore(StoredSession(tokens()))
        val coordinator = SessionCoordinator(store, {
            RefreshResult.Rejected(ReauthenticationReason.REVOKED)
        }, this, { 2_000 })
        assertEquals(AccessResult.Reauthenticate(ReauthenticationReason.REVOKED), coordinator.accessToken())
        assertNull(store.value)
        assertEquals(AuthenticationState.REAUTHENTICATION_REQUIRED, coordinator.state.value)
        assertTrue(coordinator.accessToken() is AccessResult.Reauthenticate)
    }

    @Test fun `failed final persistence leaves durable marker preventing token replay`() = runTest {
        val store = MemoryStore(StoredSession(tokens()), failWriteNumber = 2)
        var calls = 0
        val refresher = TokenRefresher {
            calls++
            RefreshResult.Accepted("new", "rotated", 1_000_000)
        }
        val coordinator = SessionCoordinator(store, refresher, this, { 2_000 })
        assertEquals(AccessResult.StorageUnavailable, coordinator.accessToken())
        assertEquals(PersistedRefreshState.IN_FLIGHT, store.value!!.refreshState)
        val restored = SessionCoordinator(store, refresher, this, { 2_000 })
        assertEquals(AccessResult.RefreshOutcomeUnknown, restored.accessToken())
        assertEquals(1, calls)
    }

    @Test fun `failed preflight persistence does not dispatch refresh`() = runTest {
        val store = MemoryStore(StoredSession(tokens()), failWriteNumber = 1)
        var calls = 0
        val coordinator = SessionCoordinator(store, { calls++; error("Must not dispatch") }, this, { 2_000 })
        assertEquals(AccessResult.StorageUnavailable, coordinator.accessToken())
        assertEquals(0, calls)
    }

    @Test fun `logout removes credentials`() = runTest {
        val store = MemoryStore(StoredSession(tokens()))
        val coordinator = SessionCoordinator(store, { error("Must not refresh") }, this)
        coordinator.logout()
        assertNull(store.value)
        assertEquals(AccessResult.SignedOut, coordinator.accessToken())
    }

    @Test fun `credential objects never expose tokens through toString`() {
        assertFalse(tokens().toString().contains("access-old"))
        assertFalse(tokens().toString().contains("refresh-old"))
        assertFalse(RefreshResult.Accepted("secret-access", "secret-refresh", 1_000).toString().contains("secret"))
        assertFalse(AccessResult.Available("secret-access", 1_000, 1).toString().contains("secret"))
    }

    private class MemoryStore(initial: StoredSession?, private val failWriteNumber: Int? = null) : SessionStore {
        var value = initial
        val writes = mutableListOf<StoredSession?>()
        private var attempts = 0
        override suspend fun read() = value
        override suspend fun write(session: StoredSession?) {
            attempts++
            if (attempts == failWriteNumber) throw IOException("Simulated atomic write failure")
            writes += session
            value = session
        }
    }
}
