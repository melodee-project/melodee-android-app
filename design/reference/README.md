# Approved visual reference

`approved-concept.png` is the selected earlier mockup. Preserve its calm, dark, artwork-led visual family and coherent phone screens. This image is a visual reference, not a feature or API specification.

## Later decisions supersede text in the image

- **Brand:** The installed and in-app name is **Melodee**, replacing StreamBeat and PulseDrive. The supplied circular Melodee badge is authoritative.
- **Downloads:** Offline listening, Download controls, downloaded playlists and persistent offline libraries are out of scope. Temporary bounded streaming cache/prefetch remains required.
- **Android Auto:** No Car phone tab. Host connection and preferences govern availability/launch/resume; do not claim the app can force automatic launch or playback.
- **Data:** Illustrative artists, account tiers, recommendations and quality labels are placeholders. Live UI must use actual server capabilities and data.
- **Car layout:** Android Auto renders its own supported media surface. These car images guide hierarchy and simplicity, not arbitrary pixel-level layout.

Use [DESIGN-SYSTEM.md](../../docs/design/DESIGN-SYSTEM.md), [UX-SPEC.md](../../docs/design/UX-SPEC.md), PRD and ADRs for implementation decisions. If a developer-facing screenshot differs from the implemented starter, label it as concept art, never as runtime evidence.
