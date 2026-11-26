# ADR-0005 — One service-owned playback session

- **Date:** 2026-11-26
- **Status:** Accepted architecture

## Context

Phone UI recreation, Android Auto connection and media buttons must not create separate players or competing queues. The phone activity is not a reliable owner for long-running listening.

## Decision

One MediaLibraryService owns one ExoPlayer and MediaLibrarySession for the active account. Phone Compose, Android Auto, system media controls and Bluetooth controllers operate that session. The service publishes authoritative playback state and serves a bounded browse hierarchy.

The queue stores stable media IDs and playback context independently of transport URLs. Phone UI state mirrors the controller. Connecting a host or opening a screen does not recreate the queue, restart a song or force playback. The application respects audio focus, user pause/stop and host launch/resume policy.

## Consequences

The application needs explicit service/controller lifecycle handling and account-scoped production queue persistence beyond the starter demo bookmarks. Android Auto UI is host-rendered; the mockup's car panes are conceptual. No Car tab, manual car mode or car-only player is introduced. The service's exported browser entry point must validate controller behavior and reject unknown media IDs/arbitrary sources appropriately.

## Alternatives considered

An Activity-owned player loses reliable background ownership. Separate phone/car players multiply state and authorization races. A custom phone driving UI does not implement Android Auto.

## Verification

Play from the phone, lock it, connect a controller and verify the same queue/position. Test activity recreation, browse-only connections, audio focus, pause intent during recovery and process resumption. DHU and real-device evidence are separate release gates.

## References

- [Background playback](https://developer.android.com/media/media3/session/background-playback)
- [Serve a content library](https://developer.android.com/media/media3/session/serve-content)
