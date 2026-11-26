# ADR-0008 — Runnable demo with explicit production boundaries

- **Date:** 2026-11-26
- **Status:** Accepted starter delivery

## Context

The user must be able to run the starter in an emulator without providing a private Melodee account. A static mockup cannot validate media controls, while silently simulated live services would misrepresent readiness.

## Decision

Ship an explicitly identified Demo library with original bundled preview audio and a real service-owned Media3 player. Demo content is development fixture material, not downloaded user-library content. Screens and controls must operate their real starter behavior. UI must not claim a successful server connection, silent refresh or full-album network cache when those adapters are absent.

Live API integration remains a separate repository adapter, auth store and streaming resolver, with contract tests before activation. The PRD and SPEC describe the full target; IMPLEMENTATION and VALIDATION state the actual delivered scope and proof.

## Consequences

A user can evaluate branding, navigation and playback immediately. Bundled previews cannot demonstrate mobile-network resilience, production auth recovery or server range behavior. A clear next-phase plan is essential. Do not hard-code credentials or a private endpoint for convenience.

## Alternatives considered

A network-only starter is blocked by environment/configuration. A fake moving scrubber validates no audio behavior. Pretending an unimplemented login succeeded would hide the highest-risk integration work.

## Verification

Open the demo, play audible previews, use actual media controls and inspect service state. Confirm planned capabilities are not presented as complete. Tests and diagnostics label fixture behavior separately from live data.
