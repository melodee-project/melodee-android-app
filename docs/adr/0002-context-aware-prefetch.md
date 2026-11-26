# ADR-0002 — Context-aware prefetching and resilient playback

- **Date:** 2026-11-26
- **Status:** Accepted core behavior; numeric defaults proposed
- **Related:** ADR-0001, ADR-0005, ADR-0006

## Context

Albums currently stop or transition poorly in the car during cellular dead spots. Prefetching a fixed small number of songs everywhere wastes the opportunity to protect an album; fetching an entire 500-song playlist wastes bandwidth and storage.

## Decision

Album playback targets the current track and all remaining album tracks in actual playback order. Large playlists and other potentially unbounded contexts use a rolling window. The initial policy retains three previous entries only when their data is already cached, fetches the current track and targets at least three upcoming tracks where resources permit. A duration target can expand the forward window.

The active player has first priority. Track/duration targets yield to a hard byte ceiling, low storage, data policy and active buffer health. Shuffle, skip and queue edits immediately recompute priorities; duplicate track occurrences retain distinct queue IDs. Cached audio and sufficient next-track metadata allow transitions without a mandatory network call.

Candidate values—1 GiB, 20 minutes ahead, two background fetches and a 24-hour inactive-entry age—remain tuning proposals. They are not guaranteed outage coverage. A pure planner produces intentions; an executor must account actual bytes, cancel obsolete work and enforce limits.

## Consequences

Context and authoritative upcoming order become part of the queue model. Track identity must be stable across signed URL changes. The system needs explicit completeness/range information, not merely a Boolean saying a media source was prepared. Large or unknown-size lossless files require conservative admission and nearest-track fallback.

## Alternatives considered

A universal three-track buffer underprotects many albums. Full-playlist fetching violates bounded use. Generic LRU alone cannot express which next track matters most.

## Verification

A cached album plays across track boundaries without connectivity. A 500-song playlist fetches only the planned window. Test actual shuffle order, duplicate entries, unknown metadata, huge files, exact byte boundaries and queue/account generations. Qualify the integrated executor separately from planner unit tests.
