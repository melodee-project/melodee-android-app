package org.melodee.android.live.auth

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.melodee.android.live.api.MelodeeApi
import org.melodee.android.live.api.OriginPolicy
import org.melodee.core.auth.AccountScope
import org.melodee.core.auth.ReauthenticationReason
import org.melodee.core.auth.RefreshResult
import org.melodee.core.auth.TokenBundle

class HttpTokenRefresherTest {
    private lateinit var server: MockWebServer
    private lateinit var originId: String
    private lateinit var refresher: HttpTokenRefresher

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        val origin = OriginPolicy.normalize(server.url("/").toString(), allowInsecureLoopback = true).getOrThrow()
        originId = origin.id
        refresher = HttpTokenRefresher(
            api = MelodeeApi(OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()),
            deviceId = { "test-device" },
            allowInsecureLoopback = true,
        )
    }

    @After fun tearDown() {
        server.shutdown()
    }

    private fun tokens(): TokenBundle = TokenBundle(
        account = AccountScope(originId, "user-1"),
        accessToken = "access-old",
        refreshToken = "refresh-old",
        expiresAtMillis = 1_000,
    )

    @Test fun `rotated pair becomes accepted refresh result`() = runTest {
        server.enqueue(json("""{"user":{"id":"user-1","username":"alice"},"serverVersion":"1",
            "token":"access-new","expiresAt":"2030-01-01T00:00:00Z","refreshToken":"refresh-new"}"""))
        val result = refresher.refresh(tokens())
        assertTrue(result is RefreshResult.Accepted)
        result as RefreshResult.Accepted
        assertEquals("access-new", result.accessToken)
        assertEquals("refresh-new", result.refreshToken)
    }

    @Test fun `replayed refresh token requires reauthentication`() = runTest {
        server.enqueue(json("""{"code":"refresh_token_replayed","message":"revoked"}""", 401))
        val result = refresher.refresh(tokens())
        assertEquals(RefreshResult.Rejected(ReauthenticationReason.REVOKED), result)
    }

    @Test fun `server error after dispatch is ambiguous and never replayed`() = runTest {
        server.enqueue(json("""{"code":"server_error","message":"failed"}""", 500))
        assertEquals(RefreshResult.OutcomeUnknown, refresher.refresh(tokens()))
    }

    @Test fun `connection failure before request is written is retryable`() = runTest {
        server.shutdown()
        assertTrue(refresher.refresh(tokens()) is RefreshResult.Retryable)
    }

    @Test fun `incomplete success response is ambiguous`() = runTest {
        server.enqueue(json("""{"user":{"id":"user-1"},"token":"access-new","expiresAt":"bad"}"""))
        assertEquals(RefreshResult.OutcomeUnknown, refresher.refresh(tokens()))
    }

    private fun json(body: String, status: Int = 200): MockResponse = MockResponse()
        .setResponseCode(status)
        .addHeader("Content-Type", "application/json")
        .setBody(body.trimIndent())
}
