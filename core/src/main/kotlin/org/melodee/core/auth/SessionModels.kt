package org.melodee.core.auth

data class AccountScope(val serverId: String, val accountId: String) {
    init { require(serverId.isNotBlank() && accountId.isNotBlank()) }
}

/** Access and rotating refresh credentials are one atomic record, never separate writes. */
data class TokenBundle(
    val account: AccountScope,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long,
    val generation: Long = 0,
) {
    init {
        require(accessToken.isNotBlank() && refreshToken.isNotBlank())
        require(expiresAtMillis > 0 && generation >= 0)
    }

    override fun toString(): String = "TokenBundle(account=$account, credentials=<redacted>, generation=$generation)"
}

enum class PersistedRefreshState { READY, IN_FLIGHT, OUTCOME_UNKNOWN }

data class StoredSession(
    val tokens: TokenBundle,
    val refreshState: PersistedRefreshState = PersistedRefreshState.READY,
)

/**
 * Implement with Keystore-backed encryption and atomic whole-record replacement.
 * IN_FLIGHT is written BEFORE contacting the server. Restoring it after a process
 * death means rotation might have succeeded: the old refresh token is not retried.
 */
interface SessionStore {
    suspend fun read(): StoredSession?
    suspend fun write(session: StoredSession?)
}

enum class AuthenticationState {
    SIGNED_OUT,
    READY,
    REFRESHING,
    TEMPORARILY_UNAVAILABLE,
    REFRESH_OUTCOME_UNKNOWN,
    REAUTHENTICATION_REQUIRED,
}

enum class ReauthenticationReason { REFRESH_EXPIRED, REVOKED, INVALID_GRANT, ACCOUNT_DISABLED }

sealed interface RefreshResult {
    data class Accepted(val accessToken: String, val refreshToken: String, val expiresAtMillis: Long) : RefreshResult {
        override fun toString() = "RefreshResult.Accepted(credentials=<redacted>)"
    }

    /** Only when the transport knows the server did NOT apply token rotation. */
    data class Retryable(val retryAfterMillis: Long = 5_000) : RefreshResult

    data class Rejected(val reason: ReauthenticationReason) : RefreshResult

    /** Includes response loss/timeouts where the server may already have rotated. */
    data object OutcomeUnknown : RefreshResult
}

fun interface TokenRefresher {
    suspend fun refresh(tokens: TokenBundle): RefreshResult
}

sealed interface AccessResult {
    data class Available(val accessToken: String, val expiresAtMillis: Long, val generation: Long) : AccessResult {
        override fun toString() = "AccessResult.Available(token=<redacted>, generation=$generation)"
    }

    data object SignedOut : AccessResult
    data class RetryLater(val retryAtMillis: Long) : AccessResult
    data class Reauthenticate(val reason: ReauthenticationReason?) : AccessResult
    data object RefreshOutcomeUnknown : AccessResult
    data object StorageUnavailable : AccessResult
    data object SessionChanged : AccessResult
}
