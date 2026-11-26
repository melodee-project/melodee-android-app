# ADR-0003 — Transparent authentication and session continuity

- **Date:** 2026-11-26
- **Status:** Accepted — explicit user requirement
- **Related:** ADR-0009

## Context

Ordinary token expiry currently interrupts listening and may require the phone to be opened during a drive. A typical album outlasts a short-lived access token. Concurrent callers and rotating refresh credentials make independent refresh logic unsafe.

## Decision

One coordinator owns credential lifecycle for the account/session. It works independently of phone UI and is shared by catalog calls, source resolution, prefetch and background work. It renews proactively before expiry during useful work and provides bounded reactive recovery. At most one refresh exchange is in flight; waiters reuse the same result or already-installed newer credentials.

Persist the full rotated pair atomically with account-generation checks. Never store the user's password. Transient network/server failures preserve credentials and queue state. Access-token expiry alone does not stop already authorized cached playback. Confirmed refresh revocation/expiry requires reauthentication.

Access tokens and signed stream URLs have separate lifecycles. An IP-bound stream URL can need renewal while the access token is still valid. Stable media/cache identity must survive either renewal.

## Consequences

The UI is a consumer of auth state, not its owner. WorkManager periodic scheduling is not the clock for active playback renewal. Logout/account changes invalidate in-flight results so old credentials cannot return. Refresh-margin values are tuning parameters derived from server expiry, not a hard-coded assumed lifetime.

## Alternatives considered

Refresh only after every 401 risks track-boundary delay. Independent refreshers can consume the same rotating token. Automatically treating any timeout as logout produces avoidable failures.

## Verification

Concurrent callers produce one renewal. Expiry during a song, transition or coverage outage preserves allowed cached playback. Locked-phone and Android Auto cold-start paths restore credentials without the activity. Lost response after server rotation is covered by ADR-0009; the client cannot promise recovery without server cooperation.
