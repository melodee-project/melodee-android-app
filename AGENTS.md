# Agent Instructions — Melodee Android

## Purpose and scope

This is a completely greenfield Android application for Melodee. Read README, PRD, SPEC, IMPLEMENTATION and the ADR index before substantive changes. The installed name is exactly **Melodee**; use the supplied logo. Never port legacy player source or assume its architecture is approved.

## Non-negotiable product decisions

- No persistent user-managed downloads, offline-library UI, audio export or DownloadManager/DownloadService subsystem.
- Temporary bounded audio caching and prefetching are required for uninterrupted streaming.
- Albums target all remaining tracks where resources permit. Long playlists use a rolling window; previous tracks are retained if cached, not fetched merely for history.
- Authentication refresh is centralized, proactive, single-flight and independent of phone UI.
- Android Auto, phone and system controls share one service-owned player and authoritative queue.
- Phone navigation has Home / Search / Library / Settings, with no Car tab.
- Preserve the selected uncluttered dark visual family. Later PRD/ADR decisions supersede text in the mockup.

## Implementation rules

Use explicit constructor dependencies and cohesive packages. Keep core policy logic Android independent. Do not create dozens of modules or layers without concrete need. Add dependencies with the features that use them. Pin compatible versions in the version catalog and include upgrade evidence.

Build baseline: AGP 9.5.3 with AGP built-in Kotlin, Gradle 9.6.1 through the checked-in wrapper, JDK 21, Kotlin 2.4.21 and KSP 2.3.14. Compile and target API 36, minimum API 29. Do not reintroduce the `kotlin-android` plugin, legacy AGP DSL opt-outs, `kotlinOptions`, or preview toolchain versions. Record build-baseline changes in ADR-0010 or a superseding ADR.

The UI cannot own an ExoPlayer, token renewal timer or competing queue. Stable IDs—not signed URLs—identify tracks/cache entries. Include server/account/representation/revision in cache isolation. Guard asynchronous results with account/queue generations. User pause/stop must win over automatic reconnection.

Do not treat every timeout or 401 as logout. A lost refresh response after server-side rotation is ambiguous; no blind replay of consumed credentials. Audit the exact server contract before implementing recovery. No code can assert a server operation succeeded solely because a timeout occurred.

## Security and data hygiene

No passwords, tokens, private endpoints or signed media URLs in commits, logs or screenshots. Exclude SDK paths, build products, credential stores, keystores and local machine files. Keystore protects encryption keys; token records need encrypted atomic storage. Validate allowed URI/origin boundaries. Do not accept arbitrary external media URIs from a controller.

## Demo honesty

Bundled demo previews are original development fixtures. Label the demo clearly. Do not claim live API integration, actual network prefetch, real authentication or real-car qualification until implemented and evidenced. A pure policy test is not an end-to-end streaming test.

## Validation and reporting

Run targeted relevant tests, then required build/lint gates. Test fault behavior, not only successful paths. Preserve exact command results. Distinguish not run, blocked, failed and passed. Update IMPLEMENTATION and VALIDATION with evidence and remaining requirements. Do not invent emulator/DHU results or quietly weaken acceptance criteria to obtain green checks.

## Changes and decisions

Use ADRs for meaningful architecture changes. Preserve the first three accepted decisions. Numeric cache/auth defaults remain proposals until measured and accepted. Describe actual behavior and consequences in commits. Publishing, external messages and production deployment require task authorization; building/reviewing locally does not imply public release approval.
