# Melodee Android — Product Requirements

**Document version:** 0.1 • **Date:** 2026-11-26 • **Product:** Melodee  
**Delivery:** Greenfield Android starter, followed by staged implementation.  
**Status:** User requirements accepted where identified; implementation proposals and tuning remain subject to validation.

## 1. Product outcome

Build a music application people enjoy using on their phone and trust during a drive. It must appear as **Melodee** in the Android launcher, use the supplied Melodee circular badge, and communicate a calm, premium visual identity. The phone experience follows the selected dark, artwork-led mockup: ample spacing, restrained purple accents, clear hierarchy and few simultaneous choices.

The defining journey is: select an album, enter the car, continue through network changes and coverage gaps, and finish the album without a preventable interruption. Ordinary access-token expiry must remain invisible. The product uses the native Melodee API. This is a new application with no inherited client architecture or source code.

The authenticated application establishes executable structure and a live-server listening experience. Bundled previews are test fixtures only; the app does not present a demo or offline library. Live-server robustness and the road-trip acceptance scenarios below still require qualification. [IMPLEMENTATION.md](IMPLEMENTATION.md) distinguishes delivered foundation from remaining work; [VALIDATION.md](VALIDATION.md) records actual verification.

## 2. Confirmed decisions

| Decision | Requirement |
|---|---|
| Greenfield | New source, build structure and architecture; existing player is not a code foundation. |
| Name and identity | Installed label, in-app identity and media-session application identity are Melodee. Use the supplied logo. |
| Streaming only | No user-managed downloads, offline library, downloaded playlists or audio export. |
| Temporary caching | Application-managed, bounded audio caching and prefetching are allowed for streaming continuity. |
| Album continuity | Target the remaining album for prefetching, subject to actual capacity, bandwidth and active playback needs. |
| Large queues | Use a focused rolling cache; three previously cached tracks and three upcoming tracks are the baseline, not the full playlist. |
| Authentication continuity | Renew credentials transparently during background playback without requiring the phone UI. |
| Android Auto | A first-class connected experience, with no persistent Car navigation tab. |
| Visual direction | Preserve the selected uncluttered dark design and common components across screens. |

The first three foundational architecture decisions are [ADR-0001](docs/adr/0001-streaming-only.md), [ADR-0002](docs/adr/0002-context-aware-prefetch.md) and [ADR-0003](docs/adr/0003-session-continuity.md). No setting may quietly reintroduce downloads or a dedicated phone Car mode.

## 3. Users and journeys

### Listener at home

Connect to a server once, browse a personal library, select an album or playlist, and use a familiar queue. The home page favors continue listening, recent items and a small number of relevant recommendations. If the server offers no recommendation capability, use truthful recent or curated content rather than fabricate personalization.

### Driver

Use Android Auto or voice to select music. The same queue and media session continue when connecting a vehicle. Navigation announcements, calls, transient coverage loss and ordinary token expiry do not corrupt the queue or require another login. Host rules govern whether the media app is selected or playback resumes automatically; the application must not force auto-start.

### Listener returning to the app

Restore queue identity and position after process recreation. Securely persisted credentials enable silent renewal when still valid. An active authorized session can consume buffered audio during an outage. Persisted cached bytes alone do not grant a fresh indefinite offline session.

### Maintainer

Diagnose failed playback using a local, sanitized diagnostic export showing state changes, timing and failure classifications. An exported bundle excludes credentials, signed URLs, request bodies containing secrets and library paths.

## 4. Product scope

### 4.1 Authenticated starter scope

The repository provides an Android Studio project, branded phone screens, a real Media3 service-backed live player and a mandatory Melodee sign-in gate. Original bundled preview audio exists only as an automated-test fixture; it is never shown as a user library. The app never falls back to mock catalog data and does not pretend to have an offline collection.

The app must authenticate before showing Home, Search, Library or Settings. A signed-in account sees live server data or a truthful empty/error state. It must not present a demo connection, a simulated renewal or fake catalog content as live data.

### 4.2 Initial production release

| ID | Area | Required outcome |
|---|---|---|
| P-01 | Connection | User enters an HTTPS server origin, authenticates, sees actionable validation errors and remains signed in while refresh credentials remain valid. |
| P-02 | Home | Continue listening, recent music and supported discovery sections appear without crowding. |
| P-03 | Search | Search artists, albums, tracks and playlists with cancellation, empty and unavailable states. |
| P-04 | Library | Browse artists, albums, tracks, favorites and playlists with sensible sorting and server pagination. |
| P-05 | Details | Album, artist and playlist pages support playback and queue actions. |
| P-06 | Player | Play/pause, seek, previous/next, shuffle, repeat, queue viewing/reordering and favorite where the API supports it. |
| P-07 | Background | Phone, lock screen, system media controls and Android Auto operate one playback session. |
| P-08 | Album caching | Prefetch upcoming album content in playback order without delaying first audio. |
| P-09 | Queue caching | Protect a rolling window; recompute immediately after skip, shuffle or edits. |
| P-10 | Auth | Proactive and reactive renewal share one concurrency-safe coordinator. |
| P-11 | Recovery | Preserve queue and position on network, server or stream-URL failures; resume when permitted. |
| P-12 | Auto | Browse, search, voice requests, metadata, media buttons and queue behavior work on the vehicle host. |
| P-13 | Settings | Playback, streaming data policy, temporary cache, appearance, server and connected-experience preferences are clear and accessible. |
| P-14 | Diagnostics | Local diagnostics and user-triggered sanitized export support issue investigation. |

### 4.3 Later, separately qualified capabilities

Lyrics, advanced recommendations, equalizer, ReplayGain, crossfade, casting, multiple simultaneous server profiles and server-synchronized queues require independent capability and UX decisions. Gapless album playback is a core acceptance goal; crossfade is a separate behavior and must not be substituted for it. Native Android Automotive OS distribution and templated car media UI are separate scope decisions.

### 4.4 Excluded

Persistent user-managed media downloads, an offline-library browser, audio export, a required external telemetry account, an assumed commercial recommendation service, a forced server HLS/CDN architecture and migration of legacy player code are excluded.

## 5. Experience requirements

### Navigation

Phone top-level navigation is **Home / Search / Library / Settings**. Now Playing is a full-screen destination reached from the mini-player. Settings contains connected-experience preferences. There is no Car tab, separate car queue, compulsory manual driving mode or launch button users must press before the vehicle can find the app.

The car browse hierarchy is deliberately shorter than the phone library. Candidate root sections are Home, Library, Playlists and Favorites, with a maximum of four app roots and compliance with host-provided limits. Recently played and resume live under Home. No Offline root is provided.

### States

Every network surface has loading, content, empty, disconnected and failure states. Recoverable faults preserve useful existing content. The player shows an unobtrusive reconnecting state when useful; ordinary refresh operations require no notification. A definitive login requirement is an account state, not a generic playback error.

### Accessibility

Use scalable text, sufficient contrast, meaningful content descriptions, adequate touch targets, logical TalkBack order and state announcements. Do not communicate availability only through color. Test large font and display sizes, short screens and landscape. Album names and artists may wrap or truncate predictably; controls must remain reachable.

## 6. Listening continuity requirements

### Queue

A queue snapshot contains stable track identities, context, order, shuffle ordering, repeat policy, current index and position. Signed URLs are resolved transport details, never queue IDs. Complete album ordering is established before dependent transitions. Metadata and authorization work must not be an unavoidable network dependency at the boundary between two already authorized, fully cached tracks.

Play Next modifies the known upcoming order. Repeated occurrences of the same track have separate queue-entry IDs. A shuffle algorithm materializes the actual upcoming sequence so prefetch can target it. An explicit user pause or stop always takes precedence over automatic recovery.

### Cache and prefetch

Album selection targets the current track first, then remaining album tracks in playback order. For a large playlist, retain up to three previous tracks only if they are already cached, and fetch a forward window of at least three where resources permit. A forward duration target may extend this window. Track count and duration are soft targets; the byte budget, current playback and explicit network policy are hard limits.

A 1 GiB cache budget, 20-minute forward target, two background fetches and a 24-hour maximum age are tuning candidates, not guaranteed coverage or final product promises. Lossless file sizes vary. Unknown track sizes require conservative admission and an enforced byte ceiling. Do not re-fetch an old track simply to satisfy a behind-window count.

Cached media is account/server scoped and app-private. It is not exposed as an offline catalog. Disconnects do not clear cached bytes; logout, account change and confirmed access withdrawal have explicit cleanup rules. Cache validity includes the media representation/revision when available, so newly transcoded or replaced content cannot accidentally reuse incompatible bytes.

### Authentication

Access-token expiry must not stop already authorized buffered playback. A coordinator refreshes ahead of expiry during useful work, checks validity before authenticated requests, and coordinates reactive 401 recovery. At most one refresh is in flight for an account/session generation. Requests that observed old credentials reuse an already-installed newer generation rather than trigger another rotation.

A transient outage does not erase credentials. Definitively revoked or expired refresh credentials require reauthentication. A response lost after server-side rotation creates an ambiguous credential state; the client must not repeatedly replay the old refresh token. Resolving that failure without another login may require a server-supported recovery/idempotency contract.

## 7. Nonfunctional goals

| Area | Acceptance direction |
|---|---|
| Startup | First useful screen without waiting for the full library; measure cold and warm starts on representative devices. |
| Responsiveness | No network, cache initialization or expensive image decode on the UI thread. |
| Playback | No avoidable inter-track gap for compatible media; validate codec/encoder metadata combinations. |
| Storage | Cache respects configured bytes, low-storage signals and account isolation. |
| Network | Active playback has priority; retries are bounded, observable and cancelled when obsolete. |
| Reliability | Process recreation, controller reconnection and vehicle transitions preserve authoritative state. |
| Privacy | No hidden remote diagnostics; secrets are redacted before persistence. |
| Maintainability | Small cohesive modules, explicit dependencies and meaningful behavior tests. |

Numeric performance budgets are set after measuring the first integrated build, not presented as completed results.

## 8. Release acceptance scenarios

| ID | Scenario | Pass condition |
|---|---|---|
| R-01 | Play a fully cached authorized album, remove connectivity | All remaining cached tracks play across boundaries without server calls. |
| R-02 | Play 500-song playlist | Cache stays bounded; only planned nearby content is fetched. |
| R-03 | Wi-Fi → cellular with signed URL invalidation | Fresh transport authorization restores missing bytes without resetting the queue. |
| R-04 | Token expires during song or cached transition | No pause, forced login or queue recreation. |
| R-05 | Concurrent API failures on old credentials | One refresh; requests reuse the installed generation. |
| R-06 | Coverage loss during refresh | No retry storm or credential deletion; ambiguous rotation is handled explicitly. |
| R-07 | Android Auto connects with phone UI closed | Host can find the media service and use valid persisted session state. |
| R-08 | Calls and navigation announcements | Focus and duck/resume behavior obey platform and user intent. |
| R-09 | Shuffle or queue edit during prefetch | Planned order changes; obsolete work is cancelled or deprioritized. |
| R-10 | Logout during refresh/prefetch | Old results cannot reinstate account credentials or refill its cache. |
| R-11 | Continuous live album | No artificial gaps for qualified media samples. |
| R-12 | Four-hour playback with accelerated expiry tests | No avoidable auth interruption; real-device endurance is also measured. |
| R-13 | Low storage / large FLAC album | Budget holds; current playback remains prioritized; reduced coverage is truthful. |
| R-14 | Cache runs out during outage | Position preserved; wait/recover without silently skipping tracks. |

Use automated integration tests, physical phones and DHU. Real-car validation is performed by a passenger or in a stationary controlled setup; DHU success does not prove every vehicle implementation.

## 9. Dependencies and open decisions

Pin the exact native API deployment and document its endpoints, pagination, token semantics, URL identity and stream-range behavior before coding live integration. Previously inspected `melodee-rs` documentation describes short-lived access JWTs, rotating refresh tokens and IP-bound signed media URLs; these are compatibility risks to verify against the selected server version, not immutable assumptions.

Confirm minimum Android support, distribution channels, application ID ownership, final licensing, retained-cache age and cellular policy. The installed label **Melodee** and no-download scope are already decided. The selected starter SDK floor is recorded in ADR-0007; changing it requires evidence and an ADR update.

## References

- [Android Media3 content library](https://developer.android.com/media/media3/session/serve-content)
- [Android background playback](https://developer.android.com/media/media3/session/background-playback)
- [Android Auto content hierarchy and limits](https://developer.android.com/training/cars/media/create-media-browser/content-hierarchy)
- [Melodee native API documentation, audit target](https://github.com/melodee-project/melodee-rs/blob/main/docs/src/content/docs/api/melodee-api.md)
