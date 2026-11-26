package org.melodee.android.live.auth

import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.melodee.android.live.api.ApiException
import org.melodee.android.live.api.ApiProtocolException
import org.melodee.android.live.api.MelodeeApi
import org.melodee.android.live.api.OriginPolicy
import org.melodee.android.live.api.ServerOrigin
import org.melodee.android.live.api.UnauthorizedApiException
import org.melodee.core.auth.AccessResult
import org.melodee.core.auth.AccountScope
import org.melodee.core.auth.AuthenticationState
import org.melodee.core.auth.ReauthenticationReason
import org.melodee.core.auth.SessionCoordinator
import org.melodee.core.auth.SessionStore
import org.melodee.core.auth.TokenBundle
import org.melodee.core.auth.TokenRefresher

sealed interface LiveLoginResult {
    data class Success(val account: LiveAccount) : LiveLoginResult
    data class Failure(val message: String) : LiveLoginResult
}

class NotSignedInException : Exception("Sign in to a Melodee server")
class ReauthenticationRequiredException(val reason: ReauthenticationReason?) :
    Exception("Sign in again to continue")
class SessionTemporarilyUnavailableException : Exception("The server is temporarily unavailable")
class SessionOutcomeUnknownException : Exception("Sign-in could not be confirmed; sign in again")
class SessionStorageUnavailableException : Exception("Secure session storage is unavailable")

/**
 * App-scoped owner of the one authentication coordinator. UI, catalog, stream
 * resolution and prefetch all share this instance; none of them owns a refresh
 * timer or credential store.
 */
class LiveSessionManager(
    private val api: MelodeeApi,
    private val store: SessionStore,
    private val accountStore: AccountMetadataStore,
    private val scope: CoroutineScope,
    private val allowInsecureLoopback: Boolean,
    private val onSessionCleared: suspend () -> Unit = {},
    refresher: TokenRefresher? = null,
    nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val coordinator = SessionCoordinator(
        store = store,
        refresher = refresher ?: HttpTokenRefresher(api, { accountStore.deviceId() }, allowInsecureLoopback),
        scope = scope,
        nowMillis = nowMillis,
    )

    val authState: StateFlow<AuthenticationState> = coordinator.state
    val account: Flow<LiveAccount?> = accountStore.accountFlow

    suspend fun currentAccount(): LiveAccount? = accountStore.current()

    /** Validate origin and credentials before establishing a session. */
    suspend fun login(rawOrigin: String, userName: String, password: String): LiveLoginResult {
        val origin = OriginPolicy.normalize(rawOrigin, allowInsecureLoopback).getOrElse {
            return LiveLoginResult.Failure(it.message ?: "Enter a valid HTTPS server address")
        }
        if (userName.isBlank() || password.isEmpty()) {
            return LiveLoginResult.Failure("Enter your username and password")
        }
        val authentication = try {
            api.authenticate(origin, userName.trim(), password)
        } catch (error: UnauthorizedApiException) {
            return LiveLoginResult.Failure("Incorrect username or password")
        } catch (error: ApiException) {
            return LiveLoginResult.Failure(
                if (error.statusCode >= 500) "The Melodee server is unavailable right now"
                else "The server rejected the connection (${error.code})",
            )
        } catch (error: ApiProtocolException) {
            return LiveLoginResult.Failure("The server returned an incompatible sign-in response")
        } catch (error: IOException) {
            return LiveLoginResult.Failure("Could not reach the Melodee server")
        }
        val access = authentication.token
        val refresh = authentication.refreshToken
        val expiresAt = parseAuthExpiry(authentication.expiresAt)
        if (access.isBlank() || refresh.isNullOrBlank() || expiresAt == null ||
            authentication.user.id.isBlank() || authentication.user.username.isBlank()
        ) {
            return LiveLoginResult.Failure("The server returned an incomplete session")
        }
        val next = LiveAccount(origin.id, authentication.user.id, authentication.user.username)
        val previous = accountStore.current()
        val accountChanged = previous != null &&
            (previous.serverId != next.serverId || previous.accountId != next.accountId)
        if (accountChanged) withContext(NonCancellable) { runCatching { onSessionCleared() } }
        coordinator.establish(
            TokenBundle(
                account = AccountScope(next.serverId, next.accountId),
                accessToken = access,
                refreshToken = refresh,
                expiresAtMillis = expiresAt,
            ),
        )
        accountStore.save(next)
        return LiveLoginResult.Success(next)
    }

    suspend fun logout() {
        val previousAccount = accountStore.current()
        val origin = previousAccount?.let {
            OriginPolicy.normalize(it.serverId, allowInsecureLoopback).getOrNull()
        }
        val pair = coordinator.logout()
        accountStore.clear()
        withContext(NonCancellable) { runCatching { onSessionCleared() } }
        if (origin != null && pair != null) {
            withContext(Dispatchers.IO + NonCancellable) {
                runCatching { api.revoke(origin, pair.refreshToken, accountStore.deviceId()) }
            }
        }
    }

    suspend fun accessToken(): AccessResult = coordinator.accessToken()

    /**
     * Runs an authenticated read. A 401 triggers at most one recovery using the
     * token that was rejected, so a late failure reuses newly installed credentials
     * instead of rotating again. Mutations must opt into their own replay policy.
     */
    suspend fun <T> authorized(block: suspend (ServerOrigin, String) -> T): T {
        val account = accountStore.current() ?: throw NotSignedInException()
        val origin = OriginPolicy.normalize(account.serverId, allowInsecureLoopback).getOrElse {
            throw NotSignedInException()
        }
        val first = coordinator.accessToken()
        val available = first as? AccessResult.Available ?: throw first.asException()
        return try {
            block(origin, available.accessToken)
        } catch (error: UnauthorizedApiException) {
            val recovered = coordinator.accessToken(available.accessToken)
            val retry = recovered as? AccessResult.Available ?: throw recovered.asException()
            try {
                block(origin, retry.accessToken)
            } catch (second: UnauthorizedApiException) {
                throw ReauthenticationRequiredException(null)
            }
        }
    }

    /** Keeps proactive renewal alive while useful work needs credentials. */
    fun startProactiveRenewal() {
        scope.launch {
            while (isActive) {
                when (coordinator.state.value) {
                    AuthenticationState.READY, AuthenticationState.TEMPORARILY_UNAVAILABLE -> {
                        runCatching { coordinator.accessToken() }
                    }
                    else -> Unit
                }
                delay(PROACTIVE_INTERVAL_MS)
            }
        }
    }

    private fun AccessResult.asException(): Exception = when (this) {
        is AccessResult.Available -> error("Available is not an error")
        AccessResult.SignedOut -> NotSignedInException()
        is AccessResult.Reauthenticate -> ReauthenticationRequiredException(reason)
        AccessResult.RefreshOutcomeUnknown -> SessionOutcomeUnknownException()
        AccessResult.StorageUnavailable -> SessionStorageUnavailableException()
        is AccessResult.RetryLater -> SessionTemporarilyUnavailableException()
        AccessResult.SessionChanged -> NotSignedInException()
    }

    private companion object {
        const val PROACTIVE_INTERVAL_MS = 30_000L
    }
}
