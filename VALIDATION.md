# Starter Validation

**Date:** 2026-11-26  
**Build:** `0.1.0-starter` · `org.melodee.android` · installed label **Melodee**

This record describes checks executed while producing the starter. It distinguishes
local demo playback from production Melodee streaming and real-car qualification.
The source in the packaged Git repository is the baseline for subsequent work.

## Toolchain modernization revalidation (current baseline)

On 2026-11-26 the build toolchain was modernized on branch
`chore/upgrade-android-toolchain`. The checks below were executed after the change
and supersede the build-environment statement for current work. The original starter
validation remains recorded further down for history.

Environment: Linux x86_64; Eclipse Temurin JDK 21.0.13.1; Gradle 9.6.1 from the
checked-in wrapper with the official SHA-256 checksum; Android Gradle Plugin 9.5.3
with built-in Kotlin; Kotlin 2.4.21; KSP 2.3.14; Android SDK Platform 36 and Build
Tools 36.0.0; minimum API 29; Pixel 9 Pro XL AVD running API 37.

| Check | Result | Evidence / meaning |
|---|---|---|
| `./gradlew --version` | **PASS** | Gradle 9.6.1, launcher/daemon JVM Temurin 21.0.13.1. |
| `./gradlew help` | **PASS** | Configuration and built-in Kotlin setup load without error. |
| `./gradlew projects` | **PASS** | Root, `:app` and `:core` resolve. |
| `./gradlew build --dry-run` | **PASS** | Full task graph configures, including debug/release/lint/unit tests. |
| `./gradlew build assembleDebugAndroidTest --warning-mode=all` | **PASS** | Debug and R8 release builds, 31 core unit tests (0 failures/errors/skips), lint and instrumentation APK. No Gradle deprecation warnings or Kotlin compiler warnings. |
| `./gradlew :app:lintDebug` | **PASS — 0 errors, 25 warnings** | Warnings are dependency-version advisories, intentional exported media components, target/compile API 37 advisories, pre-existing icon/KTX suggestions and the unchanged release resource-shrinking policy. |
| `./gradlew :app:assembleRelease` | **PASS** | R8 minification and packaging completed; release APK remains unsigned by policy. |
| `./gradlew :app:connectedDebugAndroidTest` | **PASS — 1/1 test** | `MediaLibrarySmokeTest` on the API 37 emulator: browse roots/album, search, real audio playback, three-track queue and next track without player error. |
| Manual debug install and launch | **PASS** | Installed label **Melodee**; Home, Search, Library and Settings render correctly. |
| Manual demo playback | **PASS** | Album queue played **First Light**, advanced to **Open Road** with no player error; pause/next/seek/shuffle/repeat controls responded. |
| Background playback | **PASS** | Session remained `PLAYING` with the process backgrounded. |
| Queue/position restoration | **PASS** | After force-stop and relaunch the Open Road queue returned at its saved position, paused. |
| Configuration cache evaluation | **PASS, not enabled** | Entries stored and reused for assemble, test, lint, release and connected-test tasks; the flag is left off to preserve the documented conservative build defaults. |

Android Auto manifest/packaged-artifact checks passed: the merged and packaged manifest
retains the MediaLibraryService/MediaBrowserService declarations, `MEDIA_PLAY_FROM_SEARCH`,
media playback foreground service type, MediaButtonReceiver, artwork provider and the
`com.google.android.gms.car.application` metadata; `res/xml/automotive_app_desc.xml`
still declares `media`. DHU and physical-vehicle tests were **not run** and remain
outstanding qualification requirements. The unsigned release artifact was inspected
statically (manifest, resources, assets and classes present) but not installed.

## Build environment at original starter validation (historical)

- Linux x86_64, JDK 21.
- Gradle 8.13, Android Gradle Plugin 8.13.2, Kotlin 2.2.21.
- Android SDK Platform 36 and Build Tools 36.0.0; minimum supported API 29.
- An AOSP API 35 phone emulator, using software emulation without KVM acceleration.
- Visual inspection target: 540 × 1170 pixels at 240 dpi, equivalent to 360 × 780 dp.
- Initial dependencies required network access. The original audio demo has no runtime server dependency.

The first combined Gradle/emulator run exhausted the comfortable memory budget of
this workspace. The build was restarted with bounded memory and concurrency. Repo
defaults use a 2 GiB Gradle heap, two workers and in-process Kotlin compilation.
This is a development-tooling resource choice, not a measured application performance claim.

## Original starter validation checks

| Check | Result | Evidence / meaning |
|---|---|---|
| `:core:test` | **PASS — 31 tests** | 17 authentication tests and 14 prefetch/cache-identity tests; zero failures/skips. |
| `:app:assembleDebug` | **PASS** | Actual APK compiled, resources merged, Room schema generated and dex packaged. |
| `:app:assembleDebugAndroidTest` | **PASS** | Real service/browser instrumentation test compiled. |
| `:app:lintDebug` | **PASS — 0 errors/fatal issues** | Remaining warnings are recorded below; errors were fixed, not blanket-disabled. |
| Emulator installation | **PASS** | Debug app installed on the booted API 35 emulator. |
| Service instrumentation execution | **PASS — 1 Android test** | `MediaLibrarySmokeTest` completed after emulator recovery: browse root/album nodes, playlist search, actual audio progression, three-track queue and next track without a player error. |
| Visual inspection | **PASS — Home, Now Playing, Settings** | Clean actual screenshots under `design/screenshots/`; branding, layout and primary player controls reviewed at 360 dp width. |
| Manual demo playback | **PASS** | Home Play opened a three-track album queue; Now Playing progressed to 0:12 of the 0:32 first track with active pause control. |
| Background playback | **PASS** | After Android Home, the media session remained active/PLAYING with three queue entries, position 30,981 ms, buffered position 32,000 ms and no player error. |
| Branding identity | **PASS** | Launcher string is exactly `Melodee`; logo resource/reference copies match supplied bytes. |
| Local Git/archive integrity | Verified during final packaging | Initial `main` commit, clean worktree, Git object integrity and safe archive extraction checked before delivery. |

The Android tasks were invoked using a local environment wrapper supplying JDK/SDK
paths and the execution environment's network settings. That machine-specific
wrapper and toolchain are **not** part of the repository. Equivalent portable tasks
are the `./gradlew` commands documented in README.

### Software emulator recovery

The first instrumentation attempt was terminated by Android for failing to complete
process startup before its timeout; assertions had not started. The unaccelerated
emulator also experienced repeated Bluetooth/telephony process restarts and System UI
ANRs. The initial Melodee first-frame load triggered an ANR dialog in those conditions.
Bluetooth and telephony packages were disabled in this disposable emulator, dialogs
were dismissed, and clean rendering plus actual audio playback were then observed.
No Melodee Java exception was found in the captured failure logs. The bounded retry
then completed successfully: **OK (1 test)**, elapsed **106.6 seconds** in this slow
software emulator. This recovery does not qualify Bluetooth, calls, normal-device
startup latency or general ANR resilience.

The final small phone-controller change restarts the current item when Play is
pressed after its ended state. That change was compiled after the device smoke run;
the smoke test covers browse/start/next, not the ended-to-Play interaction.

## Core behaviors covered

Prefetch tests exercise album versus rolling-window selection, forward time/count
targets, cached-only backward retention, contiguous priority, byte budgets,
unknown/oversized media, actual supplied playback order and scoped cache identity.
They prove planning decisions; they do not execute a real network prefetch or enforce
a physical disk budget.

Authentication tests exercise concurrent single-flight renewal, credential rotation,
stale-token rejection, transient versus definitive failure, account changes,
cancellation, storage errors and uncertain refresh outcomes. The durable pre-refresh
marker prevents a restarted process from blindly reusing a possibly consumed refresh
token. The tests use controlled transport/store implementations; production API and
Keystore adapters are still required.

## Lint warning disposition

The final baseline reports 12 warnings:

- Six dependency-version advisories. The repository deliberately pins a compatible
  tested baseline rather than claiming all dependencies are the latest.
- Exported media service and artwork provider. Cross-process media browsing/artwork
  access is intentional. Session admission checks trusted/own controllers; the image
  provider is read-only and resolves only allowlisted demo album assets.
- A redundant API 26 resource-directory qualifier, harmless with minimum API 29.
- No monochrome adaptive icon. The supplied full-color logo is retained.
- Two Kotlin-extension style suggestions.

Future live data requires its own caller/privacy review; the allowlisted public demo
artwork is not a justification to expose private server files.

## Asset provenance and integrity

- Supplied logo SHA-256: `c16fa70821e9cab8bd229d2c630952728ec807414e661374190ca621fe05f554`.
- Exact winning concept retained in `design/reference/approved-concept.png`; its old
  brand/download text is superseded by current requirements.
- Six original 32-second Ogg previews were generated from mathematical synthesis by
  `scripts/generate_demo_audio.py`; no private library audio was used.
- Official Gradle wrapper scripts/JAR and distribution checksum are included.
  Upstream license/notice files are retained under `third_party/gradle/`.

## Not claimed by this starter

The following have **not** been qualified: live Melodee login/refresh, real signed
stream URL recovery, physical disk audio caching/eviction, network prefetch execution,
cellular dead-zone continuity, real-library gapless audio, production codec coverage,
Android Auto DHU or vehicle compatibility, assistant recognition, long-duration
endurance, release signing or public distribution.

Room demo queue persistence, DataStore preferences and phone/service playback are
implemented. Their existence does not complete the account-scoped live recovery
requirements in PRD. Follow `IMPLEMENTATION.md` to complete and measure those phases.

## Reproduce locally

```bash
./gradlew :core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
# With a booted phone emulator or connected test phone:
./gradlew :app:connectedDebugAndroidTest
```

Open the app, play an album, open Now Playing, switch tracks, inspect/edit the queue,
search for `Nightfall`, favorite a song and revisit Library. Verify Android Auto
separately using the official DHU setup linked from README.
