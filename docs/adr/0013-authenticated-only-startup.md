# ADR-0013 — Authenticated-only startup and no in-app demo catalog

- **Date:** 2026-11-26
- **Status:** Accepted — current product behavior
- **Supersedes:** [ADR-0008](0008-demo-and-live-boundaries.md) for in-app catalog behavior
- **Related:** ADR-0001, ADR-0003, ADR-0011

## Context

The product is streaming-only and has no offline music library. Showing a bundled demo catalog when no account exists makes the app look usable offline and obscures the required authentication boundary. The user must be authenticated before any catalog or playback screen is shown.

## Decision

On launch, check for a valid saved session. Until the check completes, show a neutral session-check surface. If no valid session exists, show the Melodee sign-in screen as the application root and do not compose the Home/Search/Library/Settings shell. The app never displays a demo/mock catalog, demo mini-player, or offline library. A signed-in account sees only live server data, or a truthful empty/error state when the server has no readable content.

Bundled audio previews and fixture catalog data may remain in automated tests, but are not a product state. The service has no unauthenticated demo fallback for phone or Android Auto. Signing out returns the app to the sign-in gate and clears account-scoped audio/queue state.

## Consequences

- A network/server account is required to use the app; there is no anonymous browsing.
- Android Auto controllers cannot browse a demo tree before authentication; they receive library errors until a valid session exists.
- Tests must authenticate against a deterministic fixture server rather than exercising a demo service tree.
- Missing artwork uses a neutral placeholder, not bundled demo album art.
- The app must keep the session-check state brief and actionable; failure to restore a session leads to sign-in, not a fake/local library.

## Alternatives considered

Keeping a demo library behind a signed-out state was rejected because it conflicts with the streaming-only, authenticated product boundary and can imply offline content. Showing an empty shell before sign-in was rejected because the required action is authentication.

## Verification

Launch with no session: the app shows only the sign-in screen. Complete sign-in: the live shell loads. Sign out: the app returns to the gate and account queue/audio state is removed. Instrumentation tests use an authenticated MockWebServer fixture and the original demo service test was converted to the authenticated live path.
