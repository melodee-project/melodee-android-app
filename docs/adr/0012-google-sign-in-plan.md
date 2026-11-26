# ADR-0012 — Google sign-in integration plan and placeholder

- **Date:** 2026-11-26
- **Status:** Proposed; Android UI placeholder, production Google auth disabled
- **Related:** ADR-0003, ADR-0011

## Context

Android users should be able to sign in with their Google account in addition to a Melodee
username/password. The native Melodee API exposes `POST /api/v1/auth/google` with
`{idToken, deviceId?}` and returns the same `AuthenticationResponse` used by password login.
Google is disabled on the current production server, and the server requires an existing
Melodee account to be linked to the Google identity; it does not auto-provision accounts.

## Decision

Ship a truthful **placeholder** “Continue with Google” action now. It explains that Google
sign-in is pending OAuth/server setup and keeps username/password as the active path. Do not
display a fake Google success or collect a Google token while the server is disabled.

The eventual implementation will use Android Credential Manager with a configured Google web
client ID as `serverClientId`, send the ID token only to the selected server's
`/api/v1/auth/google`, and keep the returned Melodee access/refresh tokens in the existing
Keystore-backed session store. It will add an authenticated “Link Google account” action in
Settings for existing password accounts, because production does not auto-create/link accounts.

## Consequences

- Users cannot use Google sign-in until the server has Google enabled and a test account is
  linked. The UI must not imply otherwise.
- The Android app needs a public Google OAuth web client ID; no client secret belongs in the app.
- The server must configure `MELODEE__NATIVE__AUTH__GOOGLE__ENABLED`,
  `MELODEE__NATIVE__AUTH__GOOGLE__CLIENT_ID` and, if desired,
  `MELODEE__NATIVE__AUTH__GOOGLE__HOSTED_DOMAINS`.
- Google ID tokens must never be logged or persisted; only the Melodee session tokens are stored.
- Google-first account creation would require a separate server/security decision and is not
  assumed by this ADR.

## Alternatives considered

- Browser-cookie Google login: not suitable for the native Android client.
- Pretending Google login succeeds: rejected by the demo/integration honesty rules.
- Auto-creating Melodee accounts from Google: deferred; requires an explicit account-provisioning
  and abuse-prevention decision on the server.

## Verification

Current evidence: production `/api/v1/auth/google` returns “Google authentication is not enabled”;
Android shows only the placeholder. Before acceptance, verify a real Google ID token exchange,
linked-account behavior, token refresh, logout, and no token leakage on a device with Play
Services.
