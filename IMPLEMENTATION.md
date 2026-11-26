# Melodee Android — Implementation Plan and Status

**Date:** 2026-11-26 • **App:** 0.1
**Read with:** [PRD.md](PRD.md), [SPEC.md](SPEC.md), [ADRs](docs/adr/README.md), [CODING_AGENT_PROMPT.md](CODING_AGENT_PROMPT.md)

## 1. What this repository delivers

The repository is an authenticated native Android client for Melodee. It has a branded Compose UI, service-owned Media3 player, live native API catalog, secure session storage and Android-independent policy foundations. Authentication is mandatory: the app has no offline catalog and never shows mock/demo library content. Bundled audio remains an automated-test fixture only.

| Area | Delivered | Production qualification remaining |
|---|---|---|
| Build | Root Android Studio/Gradle project, wrapper, version catalog, release assembly | Release signing, publishing and dependency update cadence |
| Branding | Melodee installed name and supplied circular badge | Final store artwork and package ownership confirmation |
| Phone UX | Home, Search, Library, Settings, artist/album/playlist details, player/queue, sign-in gate and paged live catalog | Accessibility/performance audit, advanced discovery and final visual review |
| Playback | Real Media3 service/controller live signed playback; account-scoped queue/position/shuffle bookmarks; foreground notification | Live range/redirect/IP/expiry faults, codec qualification and endurance |
| Android Auto | Live MediaLibraryService hierarchy, paged browse/search and shared queue; authenticated service | DHU/real-car qualification, in-car error copy and voice search |
| Prefetch | Context-aware planner + bounded `CacheWriter` executor using the shared cache | Real disk-full/large-FLAC/throughput/eviction measurements |
| Auth | Native API adapter, Keystore-backed atomic persistence, single-flight refresh and account lifecycle | Live rotation-recovery audit and fault-injection matrix |
| Cache identity | Account/server/track/revision-scoped cache keys, bounded cache and account cleanup | Low-storage policy and long-term revalidation tuning |
| Server connection | Live native API against the supplied production instance; username/password verified on emulator and physical device | Ongoing production regression qualification |
| Docs | PRD, SPEC, phased plan, ADRs, validation, API contract and fixtures | Update with implementation evidence after every phase |

This is the current authenticated boundary; [VALIDATION.md](VALIDATION.md) is authoritative for checks actually executed. A feature's presence in SPEC is not evidence that it is implemented. Do not convert a planned row into “done” based only on a class or a passing mock test.

### Current build baseline

Gradle 9.6.1 (official wrapper and checksum), Android Gradle Plugin 9.5.3 with built-in Kotlin, Kotlin 2.4.21, KSP 2.3.14, JDK 21, compile/target SDK 36 and minimum SDK 29. The 2026-11-26 toolchain modernization is recorded in [ADR-0010](docs/adr/0010-modern-build-toolchain.md) and [TOOLCHAIN-UPGRADE-RESULTS.md](TOOLCHAIN-UPGRADE-RESULTS.md); [VALIDATION.md](VALIDATION.md) records the re-run evidence. Release signing/publication, Android Auto DHU and physical-vehicle qualification, and live-server integration remain separate outstanding work.

### Live integration status (2026-11-26)

The native API integration is implemented on this branch against the captured contract and
sanitised fixtures. It is **not** a live-server or vehicle qualification.

| Phase | Status | Evidence / remaining work |
|---|---|---|
| 0 Baseline | Complete | Build, unit tests, lint, authenticated emulator smoke test. |
| 1 Contract | Local baseline complete; live audit blocked | [CONTRACT.md](docs/api/CONTRACT.md), [OpenAPI reference](docs/api/openapi-v1.json) and [fixtures](docs/api/fixtures/README.md). No authorised live deployment/account was supplied. |
| 2 Auth/networking | Username/password implemented and verified against prod; Google placeholder | OkHttp/kotlinx.serialization, `KeystoreSessionStore`, `HttpTokenRefresher`, single-flight `SessionCoordinator`, `LiveSessionManager`; 24 app unit tests cover API/refresh/session/prefetch; 4 Keystore instrumentation tests. A temporary Android instrumentation check against the supplied production instance passed login, live album/album-song loading, signed stream URL resolution and HEAD. Google UI is a truthful placeholder because production reports Google sign-in disabled; Google OAuth setup and account linking remain pending. Live refresh-rotation recovery remains an open server contract. |
| 3 Catalog/queue | Implemented for the core phone scope with incremental paging | Live albums/artists/songs/search/playlists/liked songs use bounded server pages (50/100) with explicit “Show more” controls; artist/playlist detail pages incrementally; search is paged per type; account-scoped Room metadata and queue snapshots; like/unlike and 1–5 star rating mutations for tracks, albums and artists. Production verification covered Home, Library albums/artists, Artist detail, Album detail, Search, Settings, Now Playing and Queue. Advanced discovery/lyrics remain deferred. |
| 4 Playback/streams | Implemented; production single-album playback verified | `AudioCache` with `SimpleCache`/`CacheDataSource`/`ResolvingDataSource`, stable track identity, signed URL re-resolution on cache miss, one service-owned ExoPlayer/session. Production audio decoded and progressed in the app and the live queue rendered. Expiry/IP/range/endurance and full codec matrix remain unqualified. |
| 5 Prefetch | Planner + executor implemented | `PrefetchExecutor` uses `CacheWriter`, hard byte ceiling, queue/account cancellation, current-first and bounded forward planning; unit-tested. Real disk-full/large-FLAC/dead-zone measurements remain. |
| 6 Android Auto | Live hierarchy/search implemented | Same service/session, live roots, album expansion, search and deterministic rejection of unknown IDs. DHU and physical vehicle not run. |
| 7 Polish/diagnostics | Partial | Connection/loading/empty/error/sign-out states and accessible labels added. Structured diagnostic export and measured accessibility/performance passes remain. |
| 8 Qualification/release | Not complete | Independent live/DHU/vehicle/endurance review, signing and publication remain outstanding. |

## 2. Delivery rules

Work through the following phases as one coordinated implementation program. Continue autonomously across normal implementation decisions. Pause only for a concrete missing contract/access decision that cannot be resolved from the authorized scope, or an incompatible product decision. Keep every phase buildable and maintain evidence; do not turn all phases into a list of claims at the end.

Use a new branch/worktree for substantive work. Never copy the legacy player. Respect the approved visual language, but apply later product corrections recorded in the design reference README. Do not add a Car tab, downloads or an offline music library.

If a requirement cannot be completed, record the exact missing dependency, failed scenario, attempted resolution and affected acceptance IDs. “Partial” without a reason and a concrete remaining action is not acceptable.

## 3. Phase 0 — Establish the reproducible baseline

**Inputs:** This repository and pinned tooling.  
**Tasks:** Open root in Android Studio, install required SDK, sync, run `:core:test`, `:app:assembleDebug`, Android Lint and an emulator smoke test. Inspect the actual service/controller code, sign-in gate and test fixtures. Confirm launcher label/icon, mandatory authentication and no hidden credentials. Record OS, JDK, SDK, emulator and results. The live integration validation in [VALIDATION.md](VALIDATION.md) now supplements this baseline.

**Exit evidence:** Reproducible debug APK, test reports, screenshots of actual app and validation record. Do not assume a historical validation result applies after changes.

## 4. Phase 1 — Freeze the live Melodee contract

**Inputs:** Target server origin/version or exact source revision; authorized test account supplied through a secure local mechanism. Do not request credentials in a committed document.

**Tasks:**

1. Produce `docs/api/CONTRACT.md` and a feature capability matrix with exact route/method, request/response schemas, authentication, errors, pagination and version provenance. A captured OpenAPI reference snapshot is available at [docs/api/openapi-v1.json](docs/api/openapi-v1.json) with provenance and known omissions in [docs/api/README.md](docs/api/README.md); it is an input to this audit, not a substitute for it.
2. Capture sanitized fixtures for login, renewal, revocation, catalog, search, playlists, favorites, artwork, stream URL retrieval, HEAD and range requests.
3. Verify URL expiry, client-IP binding, redirect behavior, accepted codecs, seek behavior and content identity/revision.
4. Resolve refresh-token rotation ambiguity with the server's documented behavior. Identify any required server change separately; never silently loosen replay protection.
5. Map UI features to real endpoints. Unsupported recommendations or lyrics remain hidden/explicitly unavailable rather than filled with invented live results.

**Exit evidence:** Contract tests against controlled fixtures plus a successful authorized integration run for one album. Schema differences become typed adapter behavior or a documented API gap.

## 5. Phase 2 — Production auth and networking

**Tasks:** Add Retrofit/OkHttp/kotlinx.serialization adapters. Implement a Keystore-backed encrypted atomic token store and account-generation lifecycle. Connect the core coordinator to the real refresh transport. Schedule proactive renewal from service/application scope while useful work requires auth. Add bounded reactive handling that reuses a new generation after simultaneous 401s.

Respect redirect/origin boundaries. Separate catalog auth, signed audio credentials and public artwork. Never log the signed URL or token. Cancel obsolete requests on account switch. Preserve transient failures as retryable states. Persist an in-flight refresh marker and distinguish an ambiguous rotation from a known pre-send failure.

**Exit scenarios:** PRD R-04/R-05/R-06/R-10 with accelerated expiry, storage failure and server-response loss injection. Assert exactly one exchange, atomic credentials and no old-account late installation. Explicitly report if server support prevents transparent ambiguous-rotation recovery.

## 6. Phase 3 — Catalog and persisted queue

**Tasks:** Extend the starter Room bookmarks and DataStore preferences into account-scoped live queue snapshots, metadata caches and repository adapters. Implement phone artist/album/playlist detail flows, search cancellation and favorite mutations. Establish complete album ordering before transitions; handle disc numbers and duplicate playlist entries. Persist actual shuffle order, current index and position. Implement robust service resumption and user intent handling.

Do not rely on browsing the whole library before playback. For large playlists, maintain sufficient ordered upcoming metadata independently from paged screen data. Capture event idempotency rules for favorite/playlist mutations and playback-history outbox.

**Exit scenarios:** Queue survives activity/process recreation; next-track metadata is available without an avoidable boundary request; shuffle, repeat, Play Next and reorder preserve identities. Offline network state renders cached metadata without creating an offline library feature.

## 7. Phase 4 — Live playback and stream resolution

**Tasks:** Keep live playback behind the repository/source boundary without changing the service ownership model. Resolve stable track IDs to allowed stream URIs just before missing-byte retrieval. Implement correct range, redirect, cancellation and retry semantics. Retain stable cache keys across access-token and signed-URL renewal. Test original formats and any supported transcoding representation independently.

Audio focus, becoming-noisy handling, calls, Bluetooth controls and notification state must reflect user intent. Never retry by recreating the entire queue. A fully cached next track must not be blocked by a renewal request required only for network bytes.

**Exit scenarios:** Seek, previous/next and continuous-album playback on qualified MP3/AAC/FLAC samples; Wi-Fi/cellular transition with forced signed-URL invalidation; auth renewals while the phone is locked. Report actual gaps/rebuffering rather than declaring “gapless” from configuration alone.

## 8. Phase 5 — Execute context-aware prefetch and eviction

**Tasks:** Integrate a single disk cache and execution strategy. Turn planner output into bounded cancellable fetches. Use actual byte accounting and complete/range metadata. Enforce current-playback bandwidth priority, low-storage responses, deduplication and account/queue generations. Define eviction priority beyond plain LRU. Add network callbacks and measured throughput/buffer health, without treating connectivity hints as a guarantee of server reachability.

Full-album targeting remains best effort. Retain previous tracks already cached; never backfill them just to satisfy count. For large playlists, respect the forward count/duration policy and hard capacity. Cancel or replan on queue changes, quality representation changes and account changes. Centralize candidate tuning values and record measurements before promoting defaults.

**Exit scenarios:** PRD R-01/R-02/R-03/R-09/R-13/R-14 with a network fault proxy or equivalent harness. Measure actual disk usage; inject unknown Content-Length, 1+ GiB tracks, partial ranges, server stalls and disk-full faults. Prove no parallel duplicate preloader fetches and no account-obsolete writes.

## 9. Phase 6 — Android Auto completion

**Tasks:** Implement a bounded root hierarchy, meaningful content groups, metadata/artwork pipeline, search callbacks and deterministic search-to-play resolution. Respect root/item hints and do not rely on host pagination. Reject unknown media IDs and untrusted arbitrary URIs. Supply actionable sign-in/server-error state while avoiding phone interaction prompts during normal operation.

Qualify direct service cold start with the activity absent. Ensure auto-resume follows user/host intent. Test the same queue from phone, car and system controls. Verify artwork remains available across a coverage outage and meets Binder/resource limits.

**Exit evidence:** DHU recordings/logs plus a device/host matrix for wired and wireless connections. Cover R-07/R-08 and all relevant cache/auth scenarios through car controls. Voice recognition/assistant behavior needs a real supported environment; a unit callback test alone is not equivalent.

## 10. Phase 7 — Polish, accessibility and diagnostics

**Tasks:** Align all implemented screens with shared Melodee tokens and component rules. Add useful empty/loading/error states, TalkBack semantics, text scaling, predictable focus, dynamic layout and reduced-motion handling. Keep gradients restrained and the home view sparse. Introduce local structured diagnostics and user-triggered sanitized export. Establish performance budgets using representative devices, Macrobenchmark and baseline profiles when justified.

**Exit evidence:** Actual screenshots at normal/large fonts and narrow/landscape sizes, accessibility findings resolved, measured startup/scroll/playback metrics and a diagnostic bundle containing no secrets. No fake data is labeled live.

## 11. Phase 8 — Independent qualification and release preparation

**Tasks:** Independent review against PRD/SPEC/ADRs, dependency audit, clean-checkout build, extended endurance, fault matrix, storage/account isolation and release signing configuration. Decide production licensing and public distribution before publication. Prepare version/tag/release notes only after explicit release scope is authorized; do not publish as part of a local starter task.

**Exit evidence:** Every production requirement linked to a concrete passing test or an explicitly accepted deferral. No unqualified “complete” status for live auth, dead-zone continuity or car support. Add a release checklist specific to observed remaining risk rather than boilerplate.

## 12. Evidence format

Each phase report records:

- Exact commit/build and server revision.
- Commands/scenarios executed and their results.
- Device, emulator, host and network conditions.
- Requirement IDs covered and evidence artifact locations.
- Known failures, reproducible steps and the exact remaining dependency.
- Updated ADRs/contract fixtures when a decision changed.

Write assertions narrowly. “Core planner tests passed” is valid evidence for planner behavior; it does not prove disk enforcement. “Debug APK assembled” does not prove an emulator launched it. “DHU browse works” does not prove every vehicle or voice assistant path.

## 13. Suggested issue groups

Use this plan as one phased operation with reviewable commits. Group work into Build/Branding, API/Auth, Catalog/Queue, Playback/Cache, Android Auto and UX/Qualification. Avoid fragmented throwaway implementations: each group must integrate with the one service-owned session and the shared account generation model.
