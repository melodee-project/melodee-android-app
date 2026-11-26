# ADR-0004 — Native Android application stack

- **Date:** 2026-11-26
- **Status:** Accepted for this starter; review choices as requirements evolve

## Context

This is a greenfield Android product with background audio, native media controls and Android Auto as core requirements. No legacy code or technology choice is inherited. The UI must closely follow the selected polished concept while media behavior stays native.

## Decision

Use Kotlin, Jetpack Compose with Material 3 primitives, Media3/ExoPlayer and MediaLibraryService. The starter uses Navigation Compose for a compatible, established route structure. Navigation 3 remains an explicit future evaluation, rather than an untested requirement. Use coroutines/Flow and manual constructor-based dependency injection. Keep `:app` and an Android-independent `:core` module.

The target live data stack is Retrofit/OkHttp/kotlinx.serialization, Room, DataStore and Coil where useful. Add each dependency with its actual adapter/feature. Do not add an unused framework merely because it appears in a technology plan. Tests use the smallest appropriate tools; release build dependencies remain pinned.

## Consequences

Android media/session integration does not require a cross-platform bridge. More work remains if iOS becomes a requirement. Material primitives do not dictate generic visual styling: a documented Melodee design system controls component appearance.

## Alternatives considered

Flutter, React Native and MAUI introduce an additional native-media integration layer without a current cross-platform requirement. Legacy XML remains possible but does not advance the selected declarative UI goal. Hilt/Koin can be reconsidered when a measured dependency/lifecycle problem outweighs their cost.

## Verification

Build the starter, inspect dependency usage and ensure the service owns the only player. Keep versions in the catalog and update this ADR if a framework decision changes.

## References

- [Compose](https://developer.android.com/compose)
- [Media3](https://developer.android.com/media/media3)
- [Navigation Compose](https://developer.android.com/develop/ui/compose/navigation)
