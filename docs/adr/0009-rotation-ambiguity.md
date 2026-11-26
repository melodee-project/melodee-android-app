# ADR-0009 — Explicit handling of uncertain refresh rotation

- **Date:** 2026-11-26
- **Status:** Accepted client behavior; server-supported recovery contract open
- **Related:** ADR-0003

## Context

A server can rotate a refresh token, then the response can be lost. The client still holds the consumed token. With strict reuse detection, retrying that token may revoke the entire family. A crash after receipt but before durable storage has similar consequences. Single-flight prevents concurrent exchanges, but cannot make server/client persistence atomic.

## Decision

Persist a refresh attempt marker before dispatch; clear/replace it only with an atomic committed result. An uncertain result or recovered in-flight marker becomes an explicit unknown-outcome state. Do not blindly replay the old refresh token. Preserve allowed buffered listening and queue state while preventing unauthorized new retrieval.

A live integration must audit whether the exact Melodee deployment offers safe idempotency/recovery or a bounded rotation-recovery contract. Any new server capability requires a separate reviewed decision. Without it, reauthentication may be necessary; do not promise uninterrupted renewal for this failure class.

## Consequences

The core coordinator can model and test uncertainty conservatively. A durable store adapter and real transport classification remain essential; an in-memory test store is not production safety. Failures known to occur before dispatch can be retried appropriately, but transport exceptions after dispatch must not automatically be classified as safe.

## Alternatives considered

Repeated old-token retries risk family revocation. Disabling replay protection weakens authorization without resolving the transaction model. Treating every timeout as logout erases useful session continuity unnecessarily.

## Verification

Inject failures before dispatch, after server rotation, before durable commit, on process recreation and during account change. Verify no unsafe replay and no late credential installation. Test the eventual server recovery contract using real captured fixtures.
