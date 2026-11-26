# ADR-0010 — Modern Android build toolchain and AGP built-in Kotlin

- **Date:** 2026-11-26
- **Status:** Accepted — supersedes the build baseline in [ADR-0007](0007-build-and-compatibility.md)
- **Related:** ADR-0004, ADR-0005, ADR-0007

## Context

ADR-0007 pinned a coherent Gradle 8.13 / AGP 8.13.2 / Kotlin 2.2.21 baseline so the greenfield starter could be reproduced. AGP 9 makes the legacy Kotlin Android plugin and the old `android` DSL implementations obsolete. Continuing on AGP 8 would require a second major migration before AGP 10 removes those opt-outs. This repository has no legacy build logic to preserve, so it should adopt the modern build model now rather than carry compatibility debt.

The required product constraints are unchanged: minimum API 29, compile/target API 36, `org.melodee.android`, the **Melodee** label and icon, one service-owned Media3 player/session and Android Auto media declarations.

## Previous technology baseline

| Component | Previous |
|---|---|
| Gradle | 8.13 |
| Android Gradle Plugin | 8.13.2 |
| Kotlin Gradle Plugin | 2.2.21 |
| Kotlin integration | `org.jetbrains.kotlin.android` |
| KSP | 2.2.21-2.0.4 |
| JDK for verification | 21 (documented as 17+) |
| Java/Kotlin bytecode target | 17 |
| SDK | compile/target 36, minimum 29 |

## New technology baseline

| Component | Selected |
|---|---|
| Gradle | 9.6.1, official wrapper distribution and SHA-256 checksum |
| Android Gradle Plugin | 9.5.3 |
| Kotlin Gradle Plugin | 2.4.21 |
| Kotlin integration | AGP built-in Kotlin (no `kotlin-android` plugin) |
| KSP | 2.3.14 |
| Compose compiler | Kotlin Compose compiler plugin 2.4.21 |
| JDK | 21 (Gradle daemon, toolchain and CI), Java/Kotlin bytecode target 17 |
| SDK | compile/target 36, minimum 29 |
| AGP DSL | Public `android` DSL, Gradle Kotlin DSL and version catalog |
| Legacy flags | None added or retained |

## Decision

### AGP 9.5.3 and Gradle 9.6.1

AGP 9.5.3 is the current stable patch in the AGP 9.5 line. AGP 9.5 supports compileSdk up to API 37, requires Gradle 9.6.0 or newer, defaults to Build Tools 36.0.0 and requires JDK 17 or newer. Gradle 9.6.1 is a patch release in the same line and was chosen over 9.6.0 for its fixes; Gradle 9.6.1 also runs correctly on JDK 21. Both versions are stable releases, not previews. The wrapper JAR and distribution were obtained from `services.gradle.org`; `distributionSha256Sum` is set to the official checksum.

### Built-in Kotlin

The Android application module no longer applies `org.jetbrains.kotlin.android`. AGP provides Kotlin compilation directly, so the Kotlin Android plugin alias was removed from the root build file and version catalog. KGP resolves to 2.4.21 because the Compose compiler plugin declares that version; the effective runtime classpath contains `kotlin-stdlib:2.4.21`. The pure JVM `:core` module continues to apply `org.jetbrains.kotlin.jvm` as required.

`kotlinOptions` is not used. Java/Kotlin output stays at bytecode 17 (the Android API 34+ level) while a JDK 21 toolchain compiles it. Built-in Kotlin defaults `jvmTarget` to `targetCompatibility`, so the two targets cannot drift.

### Kotlin, Compose, Room and KSP

Kotlin 2.4.21 is the current stable patch in the line that AGP 9.6 adopts by default, and its compatibility table covers Gradle up to 9.7.0. The Compose compiler plugin is version-aligned with Kotlin so no separate compiler artifact is managed. The existing Compose BOM 2025.10.01 and Media3 1.8.0 remain unchanged; no runtime dependency was upgraded for its own sake.

KSP 2.3.14 satisfies Google's AGP 9 guidance (2.3.6 minimum) and is independent of the Kotlin compiler version. Room 2.8.3 still generates the same schema; the checked-in `app/schemas/.../1.json` is unchanged and no destructive migration was introduced.

AndroidX Lifecycle was moved from 2.9.4 to 2.10.0 because its older lint issue registry referenced Kotlin compiler APIs removed from the AGP 9.5 lint runtime, which caused two lifecycle lint checks to be skipped. 2.10.0 is the newest release whose AAR metadata still permits compileSdk 36; 2.11.x requires compileSdk 37 and was therefore rejected.

### Modern DSL and compatibility flags

The build uses the public `android` DSL. There is no `BaseExtension`, `applicationVariants`, `libraryVariants`, variant filtering or eager task mutation. No module-specific legacy opt-outs were added. The build does not set `android.builtInKotlin`, `android.newDsl`, `android.uniquePackageNames` or `android.enableAppCompileTimeRClass`; AGP 9 defaults are used. Redundant `android.useAndroidX` and `android.nonTransitiveRClass` properties were removed because AGP 9 enables both by default.

`buildConfig` remains enabled because Settings displays `BuildConfig.VERSION_NAME`. `resValues` is not enabled because no resource values are generated. `targetSdk` is stated explicitly rather than relying on AGP 9's compile-SDK default. Release minification remains enabled with the same ProGuard file, and resource shrinking remains disabled; AGP 9's optimized resource-shrinking and strict R8 keep-rule defaults were left in effect but do not change the release policy until `isShrinkResources` is deliberately enabled in a separate decision.

### Android Auto and playback

No manifest, service, Media3 or queue source was changed for the toolchain migration. The merged manifest still contains the MediaLibraryService and MediaBrowserService intent filters, `MEDIA_PLAY_FROM_SEARCH`, `foregroundServiceType="mediaPlayback"`, MediaButtonReceiver, the allowlisted artwork provider and `com.google.android.gms.car.application` pointing at `@xml/automotive_app_desc` with `uses name="media"`. Runtime phone validation covered real demo audio, sequential transitions, background playback, queue/position restoration and the four navigation destinations.

## Alternatives considered

- **Stay on AGP 8.13.2.** Rejected: it retains the deprecated Kotlin Android plugin and old DSL, and defers an unavoidable AGP 10 migration.
- **AGP 9.6 alpha.** Rejected by the stable-only constraint; AGP 9.6.0 was not a stable release at the time of this migration.
- **Gradle 9.6.0.** Rejected in favor of the patched 9.6.1.
- **Keep Kotlin 2.2.21.** Rejected: its documented Gradle support ends at 8.14 and AGP support at 8.11.1, so it is not a supported pairing with Gradle 9.6.1 / AGP 9.5.3.
- **Use the newest Compose BOM, Media3 or lifecycle 2.11.** Rejected: it would expand the change into unrelated runtime upgrades, and lifecycle 2.11 requires compileSdk 37.
- **Opt out of built-in Kotlin or the new DSL.** Rejected: AGP 10 removes those opt-outs, and the repository has no third-party build plugin requiring them.

## Trade-offs

The Kotlin Gradle plugin compatibility table lists AGP 9.3.1 as the highest fully supported AGP for KGP 2.4.21, while this project uses AGP 9.5.3. AGP 9.5's built-in Kotlin requires KGP 2.2.10 or newer and AGP 9.6 moves its default to KGP 2.4.20, so this is a documented-table lag rather than a known incompatibility. Configuration, compilation, unit tests, lint, release R8 and device instrumentation all pass on this pairing; any future incompatibility must be handled by a reviewed version change, not by reintroducing a legacy opt-out.

AGP 9.5's lint warns that Gradle 9.9.2 is available. That is intentionally outside this baseline: AGP 9.5 requires Gradle 9.6.0+ and the requested baseline is 9.6.1. Lint also reports outdated runtime dependencies and target SDK 37; those are deliberate pins, not migration regressions.

A Java toolchain of 21 means a machine with only JDK 17 cannot run the build. This is intentional: JDK 21 is now the documented baseline for Gradle, Android Studio and CI.

## Validation evidence

Commands were run on Linux x86_64 with Temurin JDK 21.0.13.1, Android SDK Platform 36 and Build Tools 36.0.0:

- `./gradlew --version`, `help`, `projects`, `build --dry-run` — success.
- `./gradlew build assembleDebugAndroidTest --warning-mode=all` — success; 31 core unit tests passed; release R8 build passed; lint reported 0 errors / 25 warnings (dependency-version advisories, intentional exported media components, target/compile API 37 advisories, pre-existing icon/resource and KTX suggestions, and the unchanged resource-shrinking policy).
- `./gradlew :app:connectedDebugAndroidTest` — 1/1 passed on an API 37 phone emulator.
- Manual debug install: Home, Search, Library and Settings render as Melodee; demo album playback advanced from First Light to Open Road; background playback remained `PLAYING`; force-stop and relaunch restored the Open Road queue at its saved position in the paused state.
- Configuration cache was exercised successfully for assemble, test, lint, release and connected-test tasks but is not enabled globally.

Full command output, test counts and remaining warnings are recorded in [VALIDATION.md](../../VALIDATION.md) and [TOOLCHAIN-UPGRADE-RESULTS.md](../../TOOLCHAIN-UPGRADE-RESULTS.md). DHU and physical-vehicle qualification were not available and remain outstanding.

## Consequences and future upgrades

- Android Studio must use JDK 21; the minimum IDE release supporting AGP 9.5 is Rabbit 2 (2026.2.2).
- Future AGP 9.6+ upgrades can adopt its KGP default, but must not reintroduce `kotlin-android`, `android.newDsl=false`, or other removed opt-outs.
- Upgrading Lifecycle to 2.11+ requires a separate compileSdk 37 decision.
- Enabling resource shrinking or configuration cache is a separate, measurable build-policy decision.
- Android Auto DHU and real-vehicle behaviour still require qualification independent of this build change. Compilation and phone emulator tests do not replace that evidence.

## References

- [AGP 9 migration skill](https://developer.android.com/agents/skills/build-system/agp/agp-9-upgrade/skill)
- [AGP 9.5 release notes](https://developer.android.com/build/releases/agp-9-5-0-release-notes)
- [AGP 9.0 release notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes)
- [Migrate to built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin)
- [AGP DSL/API roadmap](https://developer.android.com/build/releases/gradle-plugin-roadmap)
- [Gradle 9.6.1 release notes](https://docs.gradle.org/9.6.1/release-notes.html)
- [Gradle compatibility matrix](https://docs.gradle.org/current/userguide/compatibility.html)
- [Kotlin Gradle plugin compatibility](https://kotlinlang.org/docs/gradle-configure-project.html)
- [KSP releases](https://github.com/google/ksp/releases)
- [Compose compiler](https://developer.android.com/develop/ui/compose/compiler)
- [Java versions in Android builds](https://developer.android.com/build/jdks)
