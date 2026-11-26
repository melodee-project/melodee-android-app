# Coding Agent Prompt — Complete Melodee in One Phased Program

You are implementing **Melodee**, a greenfield native Android music application with first-class Android Auto integration. This repository is the runnable starter. Read `AGENTS.md`, `README.md`, `PRD.md`, `SPEC.md`, `IMPLEMENTATION.md`, all accepted ADRs and `docs/design/*` before changing behavior.

## Mission

Complete the production implementation as a coordinated series of buildable phases. Preserve the approved visual direction and focus engineering effort on correct queues, uninterrupted album playback, context-aware temporary caching and transparent authentication. Do not rebuild the old player or copy its source.

## Established user requirements

The Android launcher name is exactly **Melodee** and the supplied logo is authoritative. Phone tabs are Home, Search, Library and Settings; no Car tab. Android Auto is available through the media service when the host connects. Host policy controls selection/resume. No user-managed audio downloads, offline library or audio export is permitted.

Albums should prefetch remaining tracks while conditions permit. Large playlists use a bounded rolling window: previous tracks retained only if cached, current track prioritized, at least three upcoming tracks plus a duration target subject to capacity/network limits. Full-album targets are not permission to exceed hard byte ceilings. Cached bytes can protect an active authorized session during outages.

Ordinary access-token expiry must not interrupt listening or require the phone UI. One coordinator manages proactive/ reactive refresh, secure atomic storage and single-flight exchange. Access-token and signed-stream authorization lifecycles differ. A lost refresh response is an explicit ambiguous state; do not blindly retry a potentially consumed refresh token or weaken server replay checks.

## Start by inspecting and validating

1. Confirm clean baseline, dependency versions, source boundaries and actual starter behavior.
2. Build and test the root project. Run a phone emulator and inspect actual UI/audio behavior.
3. Audit the exact authorized Melodee API deployment/revision; create a contract matrix and sanitized fixtures.
4. Identify concrete API blockers, especially rotating refresh recovery and signed URLs bound to client address.
5. Continue through the phases in IMPLEMENTATION. Do not turn ordinary reversible implementation choices into repeated approval requests.

## Starter source map

Inspect `PlaybackService.kt` and `PlayerViewModel.kt` under `app/src/main/java/org/melodee/android/playback/` before touching playback. `data/PlaybackDatabase.kt` persists account-scoped queue snapshots; `data/UserPreferences.kt` stores preferences/account metadata. `data/DemoCatalog.kt` and bundled audio are automated-test fixtures only; the app requires authentication and must never show demo/mock catalog data. Compose screens live under `ui/`.

The core foundations are `core/src/main/kotlin/org/melodee/core/cache/PrefetchPlanner.kt`, `TrackKey.kt` and `auth/SessionCoordinator.kt` with their tests. Preserve useful contracts and extend them; do not assume their existence means the network/cache executor or encrypted credential store is integrated.

## Architecture constraints

There is one service-owned ExoPlayer, one MediaLibrarySession and one authoritative queue. Compose, Android Auto and system controls use it. Pure Kotlin policies remain in `:core`; Android adapters are in `:app`. Stable IDs include appropriate server/account/representation/revision scope. Queue-entry IDs remain distinct from track IDs. Account and queue generation barriers prevent stale completions.

Use the pinned native stack. Add Retrofit/OkHttp/serialization, persistence, images and background work only for tested use cases. Navigation Compose is the starter choice; an alternative requires an ADR and migration evidence. Do not casually introduce HLS/CDN/OAuth/telemetry infrastructure without a demonstrated requirement and API contract.

## Delivery phases

Follow the explicit tasks and exit evidence in IMPLEMENTATION:

- Phase 0: reproducible baseline.
- Phase 1: exact live API and failure contract.
- Phase 2: production auth, atomic credentials and networking.
- Phase 3: catalog, metadata, UI flows and persistent queue.
- Phase 4: live playback, range handling and stream resolution.
- Phase 5: cache executor, prioritization, eviction and network fault recovery.
- Phase 6: complete Android Auto browsing/search/voice/focus and DHU validation.
- Phase 7: cohesive UI polish, accessibility, performance and diagnostics.
- Phase 8: independent qualification and authorized release preparation.

Each phase leaves working code, targeted tests and an evidence update. Use reviewable commits. Parallel agents can own independent modules/tests/docs but must coordinate shared interfaces and never produce competing authoritative state.

## Mandatory acceptance focus

Qualify a cached complete album across cellular loss and track boundaries; a 500-track playlist without full fetching; exact storage limits; Wi-Fi/cellular URL renewal; token expiry mid-song and between songs; concurrent 401 bursts; uncertain refresh responses; logout/account changes during work; queue shuffle/reorder during prefetch; pause intent during recovery; car connection with the phone activity absent; navigation/call focus; large FLAC and gapless-compatible albums.

Use deterministic fault injection, real HTTP fixtures, Android instrumentation, DHU and supported physical devices as appropriate. Accelerate token expiry in tests. Measure bytes, actual gaps and timing. A successful build is necessary but does not prove playback resilience.

## User experience rules

Use the exact logo and shared theme/components. Keep the home screen sparse and artwork-led. Search, Library, details, player and settings must look like one product. Provide truthful loading, empty, disconnected and auth-required states. Supersede old mockup copy as recorded in `design/reference/README.md`. No fake connection success, fake cache progress or simulated playback timer is acceptable.

## Reporting and completion

Maintain a requirement-to-evidence matrix. Every item is complete, explicitly deferred with a product decision, or blocked by a named dependency with reproduction steps. Never report “partial” without the missing action and reason. Report exact tests/builds/devices, remaining risks and how to reproduce them. Keep code, README, SPEC, ADRs and status documentation consistent.

Stop optional testing after concrete risks and required gates are covered. Do not publish, message others, deploy servers or change server authorization policy without authorization for that action. Deliver the complete locally reviewable result before any final release approval step.
