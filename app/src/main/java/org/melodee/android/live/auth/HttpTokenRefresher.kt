package org.melodee.android.live.auth

import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import org.melodee.android.live.api.MelodeeApi
import org.melodee.android.live.api.OriginPolicy
import org.melodee.android.live.api.RefreshCallResult
import org.melodee.core.auth.ReauthenticationReason
import org.melodee.core.auth.RefreshResult
import org.melodee.core.auth.TokenBundle
import org.melodee.core.auth.TokenRefresher

/**
 * HTTP adapter for the rotating refresh exchange. Ambiguity is classified
 * conservatively: after request bytes may have reached the server, a lost
 * response never becomes a retry of the same rotating refresh token.
 */
class HttpTokenRefresher(
    private val api: MelodeeApi,
    private val deviceId: suspend () -> String,
    private val allowInsecureLoopback: Boolean,
) : TokenRefresher {
    override suspend fun refresh(tokens: TokenBundle): RefreshResult {
        val origin = OriginPolicy.normalize(tokens.account.serverId, allowInsecureLoopback).getOrElse {
            return RefreshResult.OutcomeUnknown
        }
        return when (val result = api.refreshToken(origin, tokens.refreshToken, deviceId())) {
            is RefreshCallResult.Success -> accepted(tokens, result)
            is RefreshCallResult.Failure -> failure(result)
            is RefreshCallResult.Network -> if (result.requestWritten) {
                RefreshResult.OutcomeUnknown
            } else {
                RefreshResult.Retryable()
            }
        }
    }

    private fun accepted(tokens: TokenBundle, result: RefreshCallResult.Success): RefreshResult {
        val auth = result.authentication
        val access = auth.token
        val refresh = auth.refreshToken
        val expiresAt = parseEpochMillis(auth.expiresAt)
        if (access.isBlank() || refresh.isNullOrBlank() || expiresAt == null) {
            return RefreshResult.OutcomeUnknown
        }
        if (auth.user.id.isNotBlank() && auth.user.id != tokens.account.accountId) {
            // A refresh must never install credentials for another account.
            return RefreshResult.OutcomeUnknown
        }
        return RefreshResult.Accepted(access, refresh, expiresAt)
    }

    private fun failure(result: RefreshCallResult.Failure): RefreshResult = when {
        result.statusCode == 401 || result.statusCode == 403 -> {
            val reason = when (result.code.lowercase()) {
                "refresh_token_expired", "token_expired", "expired" -> ReauthenticationReason.REFRESH_EXPIRED
                "account_disabled", "user_disabled", "account_suspended" -> ReauthenticationReason.ACCOUNT_DISABLED
                "refresh_token_replayed", "refresh_token_revoked", "revoked",
                "invalid_grant", "invalid_refresh_token" -> ReauthenticationReason.REVOKED
                else -> ReauthenticationReason.INVALID_GRANT
            }
            RefreshResult.Rejected(reason)
        }
        result.statusCode == 400 || result.statusCode == 422 -> RefreshResult.Rejected(ReauthenticationReason.INVALID_GRANT)
        result.statusCode == 429 -> RefreshResult.Retryable()
        result.statusCode >= 500 -> RefreshResult.OutcomeUnknown
        result.requestWritten -> RefreshResult.OutcomeUnknown
        else -> RefreshResult.Retryable()
    }

    private fun parseEpochMillis(value: String): Long? {
        if (value.isBlank()) return null
        return try {
            Instant.parse(value).toEpochMilli()
        } catch (_: DateTimeParseException) {
            try {
                OffsetDateTime.parse(value).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }
}

internal fun parseAuthExpiry(value: String): Long? = try {
    Instant.parse(value).toEpochMilli()
} catch (_: DateTimeParseException) {
    try {
        OffsetDateTime.parse(value).toInstant().toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }
}
