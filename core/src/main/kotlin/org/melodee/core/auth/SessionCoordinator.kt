package org.melodee.core.auth

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One instance per application process, shared by UI, playback and background API clients.
 *
 * The supplied scope must outlive an individual screen/request. Cancelling one caller
 * does not cancel the shared refresh. No timers are created here: the playback/service
 * integration calls [accessToken] before API operations and near token expiration.
 * This is transport independent; the Android starter does not yet wire a live server.
 */
class SessionCoordinator(
    private val store: SessionStore,
    private val refresher: TokenRefresher,
    private val scope: CoroutineScope,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val renewalMarginMillis: Long = 3 * 60 * 1_000L,
) {
    init { require(renewalMarginMillis >= 0) }

    private val mutex = Mutex()
    private var initialized = false
    private var record: StoredSession? = null
    private var accountEpoch = 0L
    private var inFlight: Deferred<AccessResult>? = null
    private var nextRetryMillis = 0L
    private var reauthenticationReason: ReauthenticationReason? = null
    private val mutableState = MutableStateFlow(AuthenticationState.SIGNED_OUT)
    val state: StateFlow<AuthenticationState> = mutableState.asStateFlow()

    /** Called only after an explicit successful login; never stores a password. */
    suspend fun establish(tokens: TokenBundle) = mutex.withLock {
        val replacement = StoredSession(tokens)
        store.write(replacement)
        accountEpoch++
        inFlight?.cancel()
        inFlight = null
        record = replacement
        initialized = true
        nextRetryMillis = 0
        reauthenticationReason = null
        mutableState.value = AuthenticationState.READY
    }

    /**
     * Clears local credentials before returning. The returned pair is only for a
     * best-effort server revocation in the caller; never log or persist it after this.
     * The Android integration also cancels playback and deletes the account's audio cache.
     */
    suspend fun logout(): TokenBundle? = mutex.withLock {
        val previous = record?.tokens
        store.write(null)
        accountEpoch++
        inFlight?.cancel()
        inFlight = null
        record = null
        initialized = true
        nextRetryMillis = 0
        reauthenticationReason = null
        mutableState.value = AuthenticationState.SIGNED_OUT
        previous
    }

    /**
     * Pass the token used by a rejected request for bounded 401 recovery. A late 401
     * for an older token reuses the newer credentials instead of rotating them again.
     * Stream URL authorization failures have a separate resolver; do not route every
     * stream 401 through access-token renewal.
     */
    suspend fun accessToken(rejectedAccessToken: String? = null): AccessResult {
        val decision = mutex.withLock {
            if (!initialized) {
                try {
                    record = store.read()
                    initialized = true
                    mutableState.value = when (record?.refreshState) {
                        null -> AuthenticationState.SIGNED_OUT
                        PersistedRefreshState.READY -> AuthenticationState.READY
                        else -> AuthenticationState.REFRESH_OUTCOME_UNKNOWN
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    return@withLock Decision.Return(AccessResult.StorageUnavailable)
                }
            }

            val snapshot = record ?: return@withLock Decision.Return(
                if (mutableState.value == AuthenticationState.REAUTHENTICATION_REQUIRED) {
                    AccessResult.Reauthenticate(reauthenticationReason)
                } else {
                    AccessResult.SignedOut
                },
            )
            // Join before checking the persisted pending marker.
            inFlight?.let { return@withLock Decision.Await(it) }
            if (snapshot.refreshState != PersistedRefreshState.READY) {
                return@withLock Decision.Return(AccessResult.RefreshOutcomeUnknown)
            }

            val tokens = snapshot.tokens
            val now = nowMillis()
            val rejectedCurrentToken = rejectedAccessToken == tokens.accessToken
            val nearExpiry = tokens.expiresAtMillis - now <= renewalMarginMillis
            if (!rejectedCurrentToken && !nearExpiry) {
                return@withLock Decision.Return(tokens.available())
            }
            if (now < nextRetryMillis) {
                return@withLock Decision.Return(
                    if (!rejectedCurrentToken && now < tokens.expiresAtMillis) tokens.available()
                    else AccessResult.RetryLater(nextRetryMillis),
                )
            }

            // Crash-safe marker: an old rotating token is never silently retried after
            // an ambiguous process death or lost response.
            try {
                val pending = snapshot.copy(refreshState = PersistedRefreshState.IN_FLIGHT)
                store.write(pending)
                record = pending
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return@withLock Decision.Return(AccessResult.StorageUnavailable)
            }

            val epoch = accountEpoch
            mutableState.value = AuthenticationState.REFRESHING
            val operation = scope.async(start = CoroutineStart.LAZY) {
                refresh(snapshot, epoch, rejectedCurrentToken)
            }
            inFlight = operation
            operation.start()
            Decision.Await(operation)
        }
        return when (decision) {
            is Decision.Return -> decision.result
            is Decision.Await -> try {
                decision.operation.await()
            } catch (cancelled: CancellationException) {
                // Account changes cancel old refresh work; cancellation of this caller
                // still propagates normally and cannot cancel other waiting callers.
                currentCoroutineContext().ensureActive()
                AccessResult.SessionChanged
            }
        }
    }

    private suspend fun refresh(snapshot: StoredSession, epoch: Long, rejectedCurrentToken: Boolean): AccessResult {
        val result = try {
            refresher.refresh(snapshot.tokens)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A generic I/O exception says nothing about whether rotation happened.
            RefreshResult.OutcomeUnknown
        }
        return mutex.withLock {
            if (epoch != accountEpoch) return@withLock AccessResult.SessionChanged
            inFlight = null
            try {
                when (result) {
                    is RefreshResult.Accepted -> {
                        if (result.accessToken.isBlank() || result.refreshToken.isBlank() ||
                            result.expiresAtMillis <= nowMillis()
                        ) return@withLock uncertainLocked(snapshot)
                        val replacement = StoredSession(
                            snapshot.tokens.copy(
                                accessToken = result.accessToken,
                                refreshToken = result.refreshToken,
                                expiresAtMillis = result.expiresAtMillis,
                                generation = snapshot.tokens.generation + 1,
                            ),
                        )
                        store.write(replacement)
                        record = replacement
                        nextRetryMillis = 0
                        mutableState.value = AuthenticationState.READY
                        replacement.tokens.available()
                    }
                    is RefreshResult.Retryable -> {
                        val ready = snapshot.copy(refreshState = PersistedRefreshState.READY)
                        store.write(ready)
                        record = ready
                        nextRetryMillis = nowMillis() + result.retryAfterMillis.coerceIn(1_000, 5 * 60 * 1_000)
                        mutableState.value = AuthenticationState.TEMPORARILY_UNAVAILABLE
                        if (!rejectedCurrentToken && nowMillis() < snapshot.tokens.expiresAtMillis) {
                            snapshot.tokens.available()
                        } else {
                            AccessResult.RetryLater(nextRetryMillis)
                        }
                    }
                    is RefreshResult.Rejected -> {
                        store.write(null)
                        record = null
                        reauthenticationReason = result.reason
                        mutableState.value = AuthenticationState.REAUTHENTICATION_REQUIRED
                        AccessResult.Reauthenticate(result.reason)
                    }
                    RefreshResult.OutcomeUnknown -> uncertainLocked(snapshot)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The preflight IN_FLIGHT marker remains durable if final persistence
                // fails. Mark memory uncertain too, preventing reuse of the old token.
                record = snapshot.copy(refreshState = PersistedRefreshState.OUTCOME_UNKNOWN)
                mutableState.value = AuthenticationState.REFRESH_OUTCOME_UNKNOWN
                AccessResult.StorageUnavailable
            }
        }
    }

    private suspend fun uncertainLocked(snapshot: StoredSession): AccessResult {
        val unknown = snapshot.copy(refreshState = PersistedRefreshState.OUTCOME_UNKNOWN)
        store.write(unknown)
        record = unknown
        mutableState.value = AuthenticationState.REFRESH_OUTCOME_UNKNOWN
        return AccessResult.RefreshOutcomeUnknown
    }

    private fun TokenBundle.available() = AccessResult.Available(accessToken, expiresAtMillis, generation)

    private sealed interface Decision {
        data class Return(val result: AccessResult) : Decision
        data class Await(val operation: Deferred<AccessResult>) : Decision
    }
}
