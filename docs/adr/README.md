# Architecture Decision Records

ADRs record decisions, their rationale and consequences. **Accepted** records reflect explicit product direction or the starter implementation choice described in the record. A proposal is not an implemented guarantee. Amend a record only to clarify it; supersede it with a new numbered record when changing its decision.

| Record | Decision | Status |
|---|---|---|
| [0001](0001-streaming-only.md) | Streaming-only consumption | Accepted — user requirement |
| [0002](0002-context-aware-prefetch.md) | Context-aware prefetch and resilient playback | Accepted behavior; tuning proposed |
| [0003](0003-session-continuity.md) | Transparent authentication and session continuity | Accepted — user requirement |
| [0004](0004-native-stack.md) | Native Kotlin, Compose and Media3 starter | Accepted for starter; revisit with evidence |
| [0005](0005-one-playback-session.md) | Service-owned player and authoritative queue | Accepted architecture |
| [0006](0006-cache-identity-and-bounds.md) | Stable scoped identity and bounded temporary cache | Accepted architecture; integrated executor pending |
| [0007](0007-build-and-compatibility.md) | Pinned build baseline and SDK support | Superseded by [0010](0010-modern-build-toolchain.md); SDK floor retained |
| [0008](0008-demo-and-live-boundaries.md) | Honest runnable demo and explicit live integration boundary | Accepted starter delivery |
| [0009](0009-rotation-ambiguity.md) | Treat uncertain refresh rotation explicitly | Accepted client behavior; server recovery decision open |
| [0010](0010-modern-build-toolchain.md) | Modern AGP 9.5.3 / Gradle 9.6.1 toolchain and built-in Kotlin | Accepted — current build baseline |

## Template

Every new ADR includes date, status, context, decision, consequences, alternatives considered, verification and supersession links. Decisions that change user-confirmed scope require explicit product direction. Do not alter accepted requirements simply to make an implementation easier.

## Ordering and precedence

The current user's explicit requirements govern product scope. PRD is the product contract, SPEC is the technical target, ADRs explain why, and IMPLEMENTATION tracks work and evidence. A code comment or mockup cannot silently override them. Resolve a conflict in writing before implementing incompatible behavior.
