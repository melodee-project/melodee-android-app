# ADR-0001 — Streaming-only music consumption

- **Date:** 2026-11-26
- **Status:** Accepted — explicit user requirement
- **Supersedes:** Earlier discussion of downloaded playlists and an Offline tab

## Context

The user wants to stream a Melodee library to authorized listeners and explicitly excludes persistent user-managed downloads. They permit caching for uninterrupted playback. Removing a Download button alone would not satisfy this boundary if the app retained a permanent downloadable library behind it.

## Decision

The app provides authenticated streaming with bounded, temporary application-managed audio caching and prefetching. It provides no album/playlist download feature, offline music collection, audio export or user-managed download queue. Metadata, artwork, settings and queue identity may persist where required.

An active authorized listening session may continue using cached audio during a temporary outage. New sessions and confirmed access rejection obey the authorization policy. Cached bytes are not an independent permission to start an unlimited offline session. Original bundled demo previews are development fixtures, never copies of the user's library.

## Consequences

Media3 DownloadManager and DownloadService are unnecessary. No download-status database, Offline root or download-quality UI is introduced. The cache can survive short process interruptions but remains disposable, bounded and account-scoped. The UI explains temporary storage as playback protection without promising offline availability.

## Alternatives considered

User-managed downloads and permanent offline playlists were explicitly rejected by the user. Memory-only buffering was also rejected as insufficient for album continuity through coverage gaps.

## Verification

Search UI, manifests and dependencies for unintended download features. Verify cache eviction/cleanup and account isolation. Exercise authorized cached playback during a network outage without exposing it as a browseable downloaded collection.
