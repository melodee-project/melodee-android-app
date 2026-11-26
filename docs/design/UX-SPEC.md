# Melodee UX Specification

## 1. Global shell

Home / Search / Library / Settings are the four primary phone tabs. Keep their order stable and preserve each tab's useful state. A mini-player appears above navigation once a queue exists, with artwork, track title/artist, actual play/pause and access to Now Playing. It must not cover list content or system gesture areas.

The full player replaces the content surface rather than cramming a full control set into the home page. Back returns to the previous destination without changing music. A Queue view or sheet is reached from the player. No phone Car tab, Offline tab or Download action exists.

The starter identifies its content as **Demo library**. This is a concise state disclosure, not technical implementation copy scattered across ordinary listener flows. Live server connection is presented as pending work until implemented; it must not collect a password and imply success.

## 2. Home

Use a concise greeting or product title, a prominent but uncluttered artwork-led feature, a small recent/continue group and one or two supporting sections. Use a horizontal row where it avoids showing too much at once. Do not render every library category on the first screen. A recommendation label is truthful to its data source.

Tapping an album opens its details or plays according to the explicit affordance; the play control starts the album queue. Home must not automatically reset existing playback when revisited.

## 3. Search

A prominent search field and clear results grouped by useful content type. Debounce/cancel requests in live mode and preserve the user's query. Distinguish no matches from network failure. Keyboard and back behavior are predictable. Starter search filters the demo catalog and never appears to search a remote server.

## 4. Library and details

Prioritize albums, artists, playlists and favorites. Detail screens have artwork, title, useful metadata and one primary playback action, followed by ordered tracks. Track rows show title, supporting artist where needed and duration. Put secondary queue/playlist actions in an overflow. Preserve album-disc order. Long lists remain performant.

A cache status is not a download status. Do not add downloaded filters or offline badges. If a diagnostic view exposes cache coverage, it must be clearly about temporary playback protection.

## 5. Now Playing and queue

Give artwork breathing room. Show track title/artist, elapsed/total time, a usable seek control, previous/play-pause/next, shuffle and repeat. Keep tertiary actions secondary. Transport controls mirror Media3 state; no separate timer simulates playback. Favorite state is functional only if backed by an actual repository.

Queue order is the playback order, including shuffle. Reordering, Play Next and removing a track update the same service-owned queue and prefetch priorities. An active entry is identifiable without color alone. When a user pauses during a fault, reconnection must not override that choice.

## 6. Settings

Organize a short list of clear groups: connection/account, playback, temporary cache and network policy, appearance, connected experiences and diagnostics. Show advanced details only when useful. Proposed cache tuning does not need to become a page of expert controls. Normal token renewal has no switch; reliable authentication is baseline behavior.

Android Auto preferences explain automatic availability when connected and host-controlled launch/resume. The app must not offer a misleading guaranteed “launch automatically” behavior it cannot enforce. The user does not need to launch a car mode manually.

## 7. Failure and recovery language

| Situation | UX |
|---|---|
| Normal token refresh | Invisible; preserve controls and playback. |
| Brief connection loss with cached music | Continue; show a subtle status only if it helps. |
| Cache exhausted | “Waiting for connection” with retained position and a useful retry action. |
| Server unavailable | Explain server reachability; do not call it a wrong password. |
| Confirmed expired/revoked login | Clear account action; preserve queue identity where appropriate. |
| Missing artwork | Branded neutral placeholder with stable layout. |
| Empty library/search | Explain state and a relevant next action. |
| Demo content | Clearly identify the demo, without claiming a live connection. |

## 8. Android Auto information architecture

Candidate roots: Home, Library, Playlists and Favorites. Home includes Continue listening, recent items and a bounded set of useful shortcuts. Library groups large artist/album collections meaningfully. Search and voice resolve to a predictable queue rather than an arbitrary first match when ambiguity matters.

Android Auto chooses layouts, item limits and action presentation. Artwork, metadata, hierarchy and reliable session behavior are the app's main design responsibilities. Test with the DHU and real supported hosts; phone mockup fidelity is not evidence of car usability.
