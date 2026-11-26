# ADR-0011 — Live native API integration, secure sessions and bounded streaming cache

- **Date:** 2026-11-26
- **Status:** Accepted for local implementation; live-server and vehicle qualification pending
- **Related:** ADR-0001, ADR-0002, ADR-0003, ADR-0005, ADR-0006, ADR-0008, ADR-0009

## Context

The starter had a complete demo player but no live Melodee account, catalog, signed stream
resolution or account-scoped persistence. The captured native-v1 OpenAPI reference and the
local `melodee-rs` compatibility corpus gave enough route/schema information to build a real
client without inventing endpoints. The production requirements still forbid download features,
allow only a temporary bounded cache, require transparent refresh during playback, and require
one service-owned queue shared by phone, Android Auto and system controls.

No authorised live deployment or account was supplied for this task. The implementation must
therefore be locally reviewable and testable against deterministic fixture servers, and must not
claim live-server or vehicle qualification.

## Decision

### Account and authentication

- Use one process-scoped `LiveSessionManager` wrapping the pure `SessionCoordinator`. UI, catalog,
  stream resolution and prefetch all obtain credentials through it.
- Store credentials in `KeystoreSessionStore`: Android Keystore AES-256-GCM, app-private
  `noBackupFilesDir`, whole-record atomic replace, `IN_FLIGHT` marker before refresh dispatch.
- Use `HttpTokenRefresher` for `/api/v1/auth/refresh-token` with conservative ambiguity
  classification: pre-send network failure → retryable; possible post-dispatch loss → explicit
  unknown outcome; definitive 401 → reauthentication; 429 → cooldown; 5xx → unknown.
- Keep non-secret account metadata and a per-install device ID in DataStore. The password is
  never stored.
- Logout clears local credentials before best-effort server revocation, cancels prefetch, stops
  playback and removes account audio; account switches do the same before installing the next
  account.

### Catalog and queue persistence

- Browse through `LiveMusicCatalog`, which uses the native routes in
  [CONTRACT.md](../api/CONTRACT.md). IDs are namespaced (`live:track:`, `live:album:` …) so demo
  and live items cannot collide.
- Cache metadata per server/account in Room (`cached_catalog`) and queue snapshots in
  `queue_snapshot`. Signed transport URLs are never persisted.
- Preserve the existing demo bookmark table and add a v1→v2 migration; demo behavior is not
  replaced by live behavior.
- Queue snapshots store entry IDs, track metadata, context, actual order, shuffle order, repeat,
  index and position. The service still owns the single authoritative player and queue.

### Streaming and cache

- Use one `SimpleCache` (candidate 1 GiB ceiling, LRU eviction) shared by playback and prefetch.
- Live MediaItems use stable `melodee://track/<id>?rev=…&bitrate=…` URIs. A
  `ResolvingDataSource` obtains a fresh signed URL only on a cache miss. `CacheDataSource` checks
  the cache first, so fully cached playback does not require URL renewal.
- Cache identity comes from `TrackKey(server, account, track, revision, rendition)`, not from
  URLs. Account logout/switch removes live cache keys.
- `PrefetchExecutor` consumes `PrefetchPlanner` output, uses `CacheWriter` through the same
  cache/data-source path, enforces the policy byte ceiling, deduplicates identical plans, fetches
  current first, never fetches previous tracks merely to satisfy a backward count, and cancels on
  queue/account changes.
- Demo assets continue to work through the same player; their cache keys are separate and are
  not treated as account data.

### Phone and Android Auto

- The phone UI observes `LibraryUiState` from the shared catalog. It adds truthful loading,
  empty, error and signed-out states, a Settings connection flow and sign-out.
- `PlaybackService` selects the demo or live catalog based on the current account. Android Auto
  uses the same live root hierarchy (`Home`, `Library`, `Playlists`, `Favorites`), live search and
  live album expansion. Unknown IDs return library errors; arbitrary external URIs are not
  accepted.
- Signed-out/demo mode remains fully usable without pretending to be a live connection.

## Consequences

- Live data is available when a server is supplied; otherwise the demo remains the honest
  fallback.
- A real live deployment still needs contract qualification, especially refresh-rotation
  ambiguity, IP-bound signed URLs, range behavior and token expiry.
- The metadata/queue Room schema is now version 2; a migration test preserves old demo bookmarks.
- The cache is temporary and disposable; it is not a download library and does not provide
  indefinite offline browsing.
- CacheWriter prefetch is bounded but has not been throughput-tuned against large lossless files,
  low storage or real dead zones.
- Debug loopback HTTP is intentionally accepted for emulator/MockWebServer tests; release builds
  require HTTPS.

## Alternatives considered

- Retrofit plus generated interfaces: rejected for this increment because a small typed OkHttp
  client with kotlinx.serialization is easier to audit and test; it can be introduced if the route
  surface grows.
- Persisting signed URLs in queue snapshots: rejected because they expire and are client-bound.
- Using the server queue as the authoritative queue: rejected; ADR-0005 makes the on-device
  service queue authoritative. The upstream `/api/v1/queue` routes are excluded from build.
- DownloadManager/DownloadService: rejected by ADR-0001.
- Treating any refresh timeout as logout: rejected; it would erase continuity unnecessarily and
  misclassify ambiguity.
- Fetching the whole album/playlist regardless of bytes: rejected by ADR-0002/0006; the executor
  enforces the budget.

## Verification

Local, deterministic evidence:

- `:app:testDebugUnitTest`: API/origin contract, refresh classification, single-flight session,
  prefetch planner/executor and cache-identity tests.
- Instrumentation: Keystore round-trip/atomic replacement/corrupt-record fail-closed, Room v1→v2
  migration, live catalog against MockWebServer, live signed audio through the real service, live
  phone library rendering, and the original demo service/controller smoke test.
- `:app:lintDebug` with no errors.

Not verified: authorised live server/account, real refresh rotation over the network, real
IP-bound URL invalidation, DHU/physical vehicle, four-hour endurance, low-storage/disk-full,
large FLAC/gapless matrix or release signing. See [VALIDATION.md](../../VALIDATION.md).
