# Melodee Design System

## Visual intent

A calm, premium music surface: deep neutral backgrounds, generous spacing, large artwork, strong titles and restrained violet emphasis. The user's rejected first concept was too busy. Do not reintroduce dense car-status chips, nested panels, gradients on every control or multiple competing hero sections.

Use the exact supplied mark at `design/branding/melodee-circular-badge-1024.png`. Preserve its aspect ratio and full color. The name is **Melodee** everywhere. Launcher icon padding must accommodate Android adaptive masks without clipping the circular edge or bars.

## Reference precedence

`design/reference/approved-concept.png` establishes the selected visual family. Its old StreamBeat text, download/offline promises, forced auto-launch language and illustrative artists/settings are superseded by current requirements. Consult [reference notes](../../design/reference/README.md). Car screen imagery is conceptual; the Android Auto host renders the production surface.

## Tokens

Treat these as the initial visual direction; the actual Compose theme is the executable source of truth.

| Token | Direction |
|---|---|
| Background | Near-black charcoal with a faint cool/purple cast |
| Surface | Slightly lifted dark neutral; avoid visible borders on every region |
| Primary | Soft violet for selected controls and the main playback action |
| Text primary | Warm-white, high contrast |
| Text secondary | Muted lavender/gray, readable at small sizes |
| Semantic success | Restrained green, only for actual successful state |
| Semantic error | Clear warm red with a text explanation |
| Artwork | Saturated visual interest belongs mainly to the music artwork |

## Typography and spacing

Use a readable Android sans-serif, with clear hierarchy between page titles, section headings, track titles and supporting labels. Avoid all-caps body text. Respect system font scaling. Use a consistent 4/8-based spacing rhythm and spacious screen gutters. Artwork and controls should align to shared edges.

Rounded surfaces are purposeful: medium rounding for controls/rows and larger rounding for artwork/cards. Keep shapes consistent. Shadows and glow should be subtle enough that text, content and affordance remain primary.

## Shared components

Define one top-level navigation treatment, one mini-player, one track row, one album card, one section heading and one settings row family. Reuse icons and content descriptions. The primary play/pause control is prominent; secondary actions can move into an overflow sheet. Do not add a new icon style per screen.

## Motion and feedback

Use small state transitions and predictable sheet/navigation movement. Playback position reflects actual player state. Loading indicators must not imitate playback. Reduce unnecessary motion and avoid ambient animations in functional settings. Button press feedback must not obscure artwork or labels.

## Accessibility

Aim for at least 48 dp touch targets on phone controls, scalable typography and contrast checked against actual surfaces. Announce toggle/state changes. Every icon-only actionable control has a meaningful accessible label. Decorative art is not redundantly announced. Selected navigation is identifiable by label/state, not just color.

## Acceptance

Review actual rendered screens side-by-side across Home, Search, Library, Settings and Now Playing. Check large font, narrow device, keyboard, long titles and missing art. Screens should belong to one system, with appropriate density for their purpose. A faithful visual direction does not justify copying incorrect mockup product promises.
