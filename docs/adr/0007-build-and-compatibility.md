# ADR-0007 — Reproducible build and initial compatibility floor

- **Date:** 2026-11-26
- **Status:** Superseded by [ADR-0010](0010-modern-build-toolchain.md); retained as historical baseline

## Context

The deliverable must open at repository root in Android Studio and run on a phone emulator. A moving dependency target undermines a reproducible starter. The product has not established an installed-device support matrix.

## Decision

Use a checked-in Gradle wrapper and version catalog. Initial baseline: Gradle 8.13, AGP 8.13.2, Kotlin 2.2.21, Compose BOM 2025.10.01, Media3 1.8.0 and Navigation Compose 2.9.5. Compile/target SDK 36, minimum SDK 29, compatible JDK 17+ (21 used in verification where available). These are selected stable versions, not a latest-version assertion.

App identity is `org.melodee.android`; launcher label is exactly **Melodee**. Confirm package ownership before public distribution. Local machine SDK paths and signing secrets stay untracked. A future dependency/platform upgrade is a distinct reviewed change with build and behavioral evidence.

## Consequences

The starter runs on Android 10+ emulators. Older devices are excluded until there is an explicit compatibility decision and test budget. Android Studio may need SDK 36 and an appropriate JDK installed before first sync. Release signing and store publication are separate tasks; a starter debug build is not a signed production release.

## Alternatives considered

Targeting every historical Android version without requirements expands testing. Dynamically choosing latest dependencies on every build harms reproducibility. Navigation 3 can be assessed later without blocking a runnable first delivery.

## Verification

Wrapper checksum, dependency resolution, compilation, core tests and debug assembly where the environment permits. Document exact outcomes in VALIDATION.md; lack of an emulator is not a successful emulator test.
