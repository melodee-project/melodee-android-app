# ADR-0006 — Scoped cache identity and hard resource bounds

- **Date:** 2026-11-26
- **Status:** Accepted architecture; integrated cache executor pending

## Context

Signed URLs may expire or change when a phone switches networks. Using them as cache identity duplicates data or abandons valid bytes. User/account changes introduce a separate isolation boundary. Full album prefetch can exceed available space for large lossless media.

## Decision

Derive cache identity from server, account, stable track ID, representation and revision where available. Never key audio solely by an expiring URL. Queue entry identity remains distinct from underlying media identity. A bounded app-private cache enforces actual bytes, not just estimated durations or counts.

Protect the active track and near-future content preferentially. Album protection and retention windows remain subordinate to capacity and low-storage constraints. Inactive age limits and LRU can complement these priorities. A generation barrier prevents cancelled/account-obsolete fetches from committing after cleanup.

## Consequences

The planner can use estimates but cannot enforce the actual disk limit; the executor/evictor must. Unknown lengths and partial ranges need explicit handling. Logout, account change and definitive revocation have deterministic cleanup rules. Normal token rotation or network changes do not invalidate already authorized bytes by identity alone.

## Alternatives considered

URL keys fragment cache across authorization changes. A single shared user-agnostic key risks cross-account reuse. Unbounded whole-album fetching is inconsistent with a disposable streaming cache. Plain LRU can evict the next track in favor of less useful data.

## Verification

Verify identical media resolves to the same key across URL changes, and different account/revision/representation values produce distinct keys. Inject disk-full, unknown-size and huge-track scenarios. Observe actual cache size under concurrent reads/writes and account cleanup races.
