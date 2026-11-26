# Sanitised native API fixtures

These are selected JSON bodies from the upstream `melodee-rs` golden compatibility corpus,
copied with the same placeholders used by the upstream provenance metadata. They are test
fixtures, not live captures from this Android project.

| File | Upstream golden path |
|---|---|
| `auth-login-success.json` | `native-auth/auth-login-success.json` |
| `auth-refresh-replayed.json` | `native-auth/auth-refresh-rotation-replay-rotated-token.json` |
| `albums-page.json` | `native-catalog/catalog-albums-list.json` |
| `album-songs.json` | `native-catalog/catalog-album-songs.json` |
| `search-response.json` | `native-catalog/catalog-search-post-dune.json` |
| `playlist-songs.json` | `native-catalog/catalog-playlist-songs.json` |
| `stream-range-initial.metadata.json` | `native-stream/stream-get-range-initial.metadata.json` |
| `stream-range-unsatisfiable.metadata.json` | `native-stream/stream-get-range-unsatisfiable.metadata.json` |

## Placeholders and safety

- `{melodee-origin}` is replaced by a test server origin at runtime.
- `<hmac-auth-token>` and `<volatile>` are sanitised values from upstream.
- User/account values are synthetic (`phase0-fixture` / `example.test`).
- No real passwords, access tokens, refresh tokens, private hosts or signed URLs are present.
- The fixture identities are synthetic UUIDs from the upstream deterministic catalog.

Do not add production captures, credentials, signed URLs or private hostnames here. The
instrumentation/unit tests use their own deterministic MockWebServer responses; these files are
for contract review and future fixture-backed tests.
