# Melodee native API v1 — implementation contract

- **Status:** Reference contract for the local implementation; **not** an authorised production deployment qualification.
- **OpenAPI input:** [openapi-v1.json](openapi-v1.json), captured snapshot with provenance in [README.md](README.md).
- **Client code:** `app/src/main/java/org/melodee/android/live/`.
- **Related:** [ADR-0003](../adr/0003-session-continuity.md), [ADR-0009](../adr/0009-rotation-ambiguity.md), [ADR-0011](../adr/0011-live-native-api-integration.md), [IMPLEMENTATION Phase 1](../../IMPLEMENTATION.md).

## 1. Provenance and scope

The route and schema information below comes from the captured OpenAPI 3.1.1 document and
the sanitised golden fixtures in the local `melodee-rs` compatibility project. No authorised
live Melodee deployment or account was supplied for this task. The implementation is therefore
qualified against deterministic fixture servers, not against a production origin.

The upstream OpenAPI document is explicitly known-incomplete: 54 of 202 inventory routes are
absent (18 implemented podcast routes, 33 excluded party routes and 3 excluded queue routes).
The Android scope uses authentication, catalog, search, playlists, liked songs, playback
settings and signed stream URLs. It does **not** depend on the missing server queue routes.

## 2. Transport and origin rules

- Base URL is an HTTPS origin (`scheme://host[:port]`), with no path, query, fragment or user-info.
- Cleartext loopback is accepted only in debug builds for emulator/MockWebServer tests.
- API requests use `Authorization: Bearer <access token>`; the token is never attached to
  artwork or signed stream origins outside the configured server origin.
- Redirects are not followed automatically by the Melodee API client. A stream URL is accepted
  only when it resolves to the same scheme/host/port as the configured origin.
- JSON is parsed with unknown fields ignored; numeric fields accept numbers or strings.

## 3. Route matrix

| Area | Method and path | Auth | Request / response |
|---|---|---|---|
| Login | `POST /api/v1/auth/authenticate` | none | `{userName, password}` → `AuthenticationResponse`; 400/401 `ApiError` |
| Refresh | `POST /api/v1/auth/refresh-token` | none | `{refreshToken, deviceId?}` → `AuthenticationResponse`; 401 `ApiError` |
| Revoke | `POST /api/v1/auth/revoke` | none | `{refreshToken, deviceId?}` → 200 |
| Logout | `POST /api/v1/auth/logout` | bearer | 200; revokes refresh family |
| Google login | `POST /api/v1/auth/google` | none | `{idToken, deviceId?}` → `AuthenticationResponse`; server must have Google enabled. Currently a placeholder in the Android UI. |
| Link Google | `POST /api/v1/user/me/link/google` | bearer | `{idToken}` → 200; 409 if already linked |
| Unlink Google | `DELETE /api/v1/user/me/link/google` | bearer | 200/404 |
| Linked providers | `GET /api/v1/user/me/linked-providers` | bearer | provider list |
| Current user | `GET /api/v1/user/me` | bearer | `User` |
| Albums | `GET /api/v1/albums?page&pageSize&orderBy&orderDirection&q` | bearer | `Paged<Album>` |
| Album | `GET /api/v1/albums/{uuid}` | bearer | `Album`; 404 `ApiError` |
| Album songs | `GET /api/v1/albums/{uuid}/songs?page&pageSize` | bearer | `Paged<Song>`; complete album ordering |
| Artists | `GET /api/v1/artists?q&page&pageSize&orderBy&orderDirection` | bearer | `Paged<Artist>` |
| Artist albums | `GET /api/v1/artists/{uuid}/albums?page&pageSize` | bearer | `Paged<Album>` |
| Artist songs | `GET /api/v1/artists/{uuid}/songs?q&page&pageSize` | bearer | `Paged<Song>` |
| Songs | `GET /api/v1/songs?page&pageSize&orderBy&orderDirection&q` | bearer | `Paged<Song>` |
| Song / stream URL | `GET /api/v1/songs/{uuid}` | bearer | `Song` with `streamUrl` |
| Search | `POST /api/v1/search` | bearer | `{query, pageSize, artistPage, albumPage, songPage}` → `SearchResultResponse` (grouped tracks/albums/artists/playlists) |
| Playlists | `GET /api/v1/playlists?page&pageSize` | bearer | `Paged<Playlist>` |
| Playlist | `GET /api/v1/playlists/{uuid}` | bearer | `Playlist` |
| Playlist songs | `GET /api/v1/playlists/{uuid}/songs?page&pageSize` | bearer | `Paged<Song>`; duplicate occurrences preserved in order |
| Liked songs | `GET /api/v1/user/songs/liked?page&pageSize` | bearer | `Paged<Song>` |
| Star song | `POST /api/v1/songs/starred/{uuid}/{true|false}` | bearer | 200; idempotent set |
| Star album | `POST /api/v1/albums/starred/{uuid}/{true|false}` | bearer | 200; idempotent set |
| Star artist | `POST /api/v1/artists/starred/{uuid}/{true|false}` | bearer | 200; idempotent set |
| Rate song | `POST /api/v1/songs/setrating/{uuid}/{0-5}` | bearer | 200; 0 clears |
| Rate album | `POST /api/v1/albums/setrating/{uuid}/{0-5}` | bearer | 200; 0 clears |
| Rate artist | `POST /api/v1/artists/setrating/{uuid}/{0-5}` | bearer | 200; 0 clears |
| Recently played | `GET /api/v1/user/last-played?page&pageSize` | bearer | `Paged<Song>` |
| Stream bytes | `GET/HEAD /song/stream/{trackUuid}/{userUuid}/{hmac}` | signed URL | 200/206/416; no bearer header |

Supported image URLs are anonymous same-origin `/images/{id}/{size}` paths. The client does
not send an access token to them.

## 4. Envelopes

`ApiError`:

```json
{ "code": "refresh_token_replayed", "message": "Refresh token has been revoked. Please re-authenticate.", "correlationId": "<opaque>" }
```

`PaginationMetadata` (`meta`) contains `totalCount`, `pageSize`, `currentPage`, `totalPages`,
`hasPrevious` and `hasNext`. Paged responses are `{ "meta": {...}, "data": [...] }`.

`AuthenticationResponse` contains `user`, `serverVersion`, `token`, `expiresAt`,
nullable `refreshToken` and nullable `refreshTokenExpiresAt`. The client rejects an incomplete
authentication response instead of inventing a token lifetime.

## 5. Authentication and rotation decisions

The upstream native API documentation describes:

- stateless access JWTs with a captured 15-minute lifetime;
- rotating refresh tokens with reuse detection, where replaying an old token revokes the family;
- logout/revocation invalidating refresh families;
- an access token remaining valid until its own expiry after logout.

The Android client therefore:

1. Uses server-provided `expiresAt`; it does not hard-code 15 minutes.
2. Refreshes proactively with a conservative margin before useful authenticated work.
3. Uses one `SessionCoordinator` and single-flight exchange for all callers.
4. Persists an `IN_FLIGHT` marker before dispatch and replaces the whole credential pair atomically
   after a successful rotation.
5. Treats a lost response after request bytes may have reached the server as an explicit
   `OUTCOME_UNKNOWN` state. It does not replay the old rotating token (ADR-0009).
6. Classifies a definitive refresh 401/revocation as reauthentication; 429 as cooldown;
   5xx after dispatch as ambiguous; pre-send connection failure as retryable.
7. Never logs or persists passwords, access/refresh tokens or signed URLs.

**Open blocker:** the exact deployment's refresh-rotation recovery/idempotency behaviour has not
been audited against a live server. Without a server-supported recovery contract, an ambiguous
lost-response state may require reauthentication rather than silent continuation.

## 6. Signed stream URLs

The fixture corpus and upstream documentation describe:

- HMAC-signed URLs bound to the user, track and resolved client address;
- approximately seven-day expiry;
- `GET`, `HEAD` and one RFC 7233 byte range per request;
- 401 for invalid credentials/tampering and 416 for unsatisfiable ranges.

The Android implementation resolves the URL immediately before a cache miss is fetched. A
fully cached track is read before URL resolution. Cache identity is derived from
server/account/track/revision/bitrate, never from the signed URL. On HTTP 401/403/410 or a
network-address change, a later read resolves a new URL while retaining the queue and position.

**Open blocker:** URL expiry, client-address binding and range behaviour have not been exercised
against an authorised live deployment in this task. Golden metadata describes the expected
behaviour; it is not live evidence.

## 7. Sanitised fixtures

[fixtures/](fixtures/) contains selected fixtures copied from the upstream golden corpus. They
retain placeholder values (`{melodee-origin}`, `<hmac-auth-token>`, `<volatile>`) and synthetic
fixture identities. They contain no real credentials or private hosts. See
[fixtures/README.md](fixtures/README.md).

## 8. Implemented client mapping

| Concern | Implementation |
|---|---|
| HTTP/JSON | `MelodeeApi`, `MelodeeJson`, flexible numeric serializers |
| Origin policy | `OriginPolicy` |
| Secure credentials | `KeystoreSessionStore` (AES-GCM, Android Keystore, atomic replace) |
| Refresh transport | `HttpTokenRefresher` |
| Single-flight/rotation | `SessionCoordinator` + `LiveSessionManager` |
| Catalog | `LiveMusicCatalog`, `LiveCatalogCache` (account-scoped Room metadata) |
| Queue snapshot | `LiveQueueStore` (account-scoped Room; no signed URLs) |
| Stream bytes | `AudioCache` (`SimpleCache` + `CacheDataSource` + `ResolvingDataSource`) |
| Prefetch | `PrefetchPlanner` + `PrefetchExecutor` + `Media3TrackDownloader` |
| Android Auto | `PlaybackService` live browse/search/resumption paths |

## 9. Production auth smoke test

`scripts/melodee_prod_smoke.py` performs an authorised, read-only username/password smoke test
against a deployment. It prints no credentials, access tokens, refresh tokens or signed URLs.

```bash
MELODEE_BASE_URL=https://music.example.com \
MELODEE_USERNAME=test-user \
MELODEE_PASSWORD='...' \
python3 scripts/melodee_prod_smoke.py
```

It checks `/readyz`, password login, `/user/me`, album listing, album songs, and a stream HEAD
plus a 1 KiB range GET. It does not rotate refresh tokens or mutate server state.

## 10. Google authentication status

The native route and request/response shape are confirmed from the local `melodee-rs` v0.3.0
source. Production currently returns:

```json
{"code":"BAD_REQUEST","message":"Google authentication is not enabled"}
```

for `/api/v1/auth/google`. The Android connection screen exposes a placeholder
“Continue with Google” action that explains the pending setup; it does not fake success. Google
requires the server to be configured with a Google web client ID, the Android app to use the same
client ID as `serverClientId`, and the user's Google identity to be linked to an existing Melodee
account. See [ADR-0012](../adr/0012-google-sign-in-plan.md).

## 11. Not yet qualified

- No authorised live server/account; all network evidence is fixture-based.
- No DHU or physical-vehicle run.
- No real token-expiry, IP-change, dead-zone, four-hour endurance, disk-full or large-FLAC run.
- No verified server-side refresh-rotation recovery contract.
- Reliability of ExoPlayer gapless behaviour on the target codec matrix is not measured.

These gaps are recorded in [VALIDATION.md](../../VALIDATION.md) and
[TOOLCHAIN-UPGRADE-RESULTS.md](../../TOOLCHAIN-UPGRADE-RESULTS.md) as applicable.
