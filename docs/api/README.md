# Melodee native API — OpenAPI reference

This directory contains a reference copy of the Melodee **native v1 API** OpenAPI
document. It is provided to inform Android client design and DTO/route planning.

It is **not** an approved live-server contract and must not be treated as proof that
any route, authentication flow or streaming behaviour is production-ready. The live
Melodee contract still requires the audit described in
[IMPLEMENTATION.md — Phase 1](../../IMPLEMENTATION.md).

## Files

| File | Purpose |
|---|---|
| `openapi-v1.json` | OpenAPI 3.1.1 document for the native Melodee API (`/api/v1` plus related stream routes). |
| `README.md` | Provenance, scope, known gaps and usage rules for this reference copy. |

## Provenance

| Item | Value |
|---|---|
| Source repository | Local `melodee-rs` clone (API implementation/compatibility project) |
| Source path | `compatibility/melodee-api-v1/contracts/openapi-v1.json` |
| Source clone HEAD at copy time | `8a4cf6fa67d7977d6e1cf999dd2d66bb5a262c2c` |
| Last source change to this file | `e55b5bc` (2026-08-07) |
| Capture origin | The reference server's `/openapi/v1.json` |
| Upstream normalisation | `servers` is normalised to `https://{melodee-host}/` |
| Copied file SHA-256 | `9390aa64d6e4789f6966902495292e6b1a5c4a57936b4ce8bc55be890cf75619` |
| File size | 453,035 bytes |

The copy is byte-for-byte identical to the upstream contract file. The Android
project does not add, remove or rewrite API operations.

## Document structure

- OpenAPI version: **3.1.1**
- Native API version: **v1**
- Paths: **146**
- Operations: **181**
- Component schemas: **121**
- Tags: 26, including Auth, Albums, Artists, Songs, Playlists, Search, User,
  UserStats, PlaybackSettings, PlaybackBackend and System
- All 121 local `$ref` targets resolve within the document.
- The document declares no `securitySchemes`; authentication semantics must be
  derived from the live contract, not inferred from this file.

Relevant routes for the Android client include:

- `POST /api/v1/auth/authenticate`, `/api/v1/auth/refresh`, `/api/v1/auth/revoke`
- `GET /api/v1/albums`, `/api/v1/albums/{id}`, `/api/v1/albums/{id}/songs`
- `GET /api/v1/artists`, `/api/v1/artists/{id}/albums`, `/api/v1/artists/{id}/songs`
- `GET /api/v1/songs/{id}`, `/api/v1/search`, `/api/v1/search/advanced`
- `GET /api/v1/playlists`, `/api/v1/playlists/{id}`, `/api/v1/playlists/{id}/songs`
- Stream URL routes, including `/song/stream/{apiKey}/{userApiKey}/{authToken}`
- User/library state routes such as playlists, liked/recent/top-rated songs and stats

## Known limitations

The upstream compatibility notes describe this published OpenAPI document as
**known-incomplete**. In particular, the upstream route inventory states that party,
native podcast and queue routes are missing from the OpenAPI document, and that the
route manifest — not this file — is the authoritative route inventory.

The same upstream route inventory (source commit above) records 202 routes: 167
captured/implemented and 35 excluded from the build. Of the 54 routes absent from this
OpenAPI document:

- 18 podcast routes are implemented and captured upstream but omitted from the spec;
- 33 party-session/endpoint routes are marked excluded-from-build upstream; and
- 3 `/api/v1/queue` routes are marked excluded-from-build upstream.

The Android app does not use the server queue as its authoritative playback queue
(ADR-0005: the service-owned queue is authoritative), so missing `/api/v1/queue`
operations are not currently required by the client. Podcast and party features are
outside the current Android scope. Re-check this list if those features are introduced.

Consequences for this project:

1. Do not assume a route is unsupported merely because it is absent here.
2. Do not assume a route is implemented or stable merely because it is present here.
3. Queue and playback-backend behaviour must be reconciled against the live server
   and the upstream route inventory before integration.
4. The document's upstream description says "Complete API documentation"; that is
   upstream wording, not an Android-project claim of completeness.
5. This file does not define the signed-URL origin rules, token-rotation ambiguity
   behaviour, redirect policy or range semantics required by ADR-0003, ADR-0009 and
   the streaming architecture.

## Usage rules

- Use this document to understand paths, parameters and response shapes while
  designing adapters and fixtures.
- Prefer stable IDs from the contract; never persist signed media URLs or treat
  them as cache identity.
- Do not generate and ship a production client blindly from this document. Audit
  authentication refresh/rotation, catalog pagination, stream URL expiry and range
  responses against an authorised server first.
- Keep authentication tokens, cookies, private hosts and signed stream URLs out of
  source, logs, issues and screenshots.
- Treat this document as read-only reference material. Do not hand-edit it to make
  the Android client appear compatible.

## Updating the reference

Copy the contract from the upstream API project, verify the checksum and update the
provenance table:

```bash
cp <melodee-rs>/compatibility/melodee-api-v1/contracts/openapi-v1.json docs/api/openapi-v1.json
sha256sum docs/api/openapi-v1.json
python3 -c 'import json; d=json.load(open("docs/api/openapi-v1.json")); print(d["openapi"], len(d["paths"]))'
```

Record the source commit, capture origin and any upstream caveat in this README when
the file changes. A toolchain dependency upgrade is not a reason to refresh this
document; refresh it only from an audited upstream API revision.

## Security

The copied document contains only API shapes and the placeholder host
`https://{melodee-host}/`. It contains no real hostnames, credentials, tokens,
private endpoints, email addresses or signed media URLs. The upstream capture used a
sanitised public baseline; do not add environment-specific values here.
