# Changelog

All notable changes to the FlightStats Android application will be documented in this file.

## [1.3.0] - 2026-07-16

### Added
- **Dynamic Contextual Theming**: Carousel statistics cards automatically compile travel patterns (Spring/Summer/Autumn/Winter flight counts and top-visited countries) and adapt cards with custom themed colorways (e.g. Delft Blue & Orange for Netherlands, Crimson & Indigo for US, Sakura Pink & Zen Red for Japan, Lavender & Riviera Blue for France) for both Light and Dark modes.
- **Corner Style & Radius Slider Controls**: Dynamic shape controls under *Theme & Units* in Settings allowing real-time switching between **Rounded (Squircle)** and **Cut (Beveled)** shape families, driven by a corner radius slider (`0dp` to `36dp`) that scales outer cards, list rows, subcards, and carousel mask clips proportionally.
- **Fluid Map Micro-Interactions**:
  - **Animated Geodesic Paths**: Progressive flight path drawing using a `ValueAnimator` over `1.8s` with `DecelerateInterpolator` to animate flight paths flowing outward.
  - **Fade-In Airport Nodes**: Airport code markers fade in seamlessly synchronized with the path drawing.
  - **FAB-to-Card Morphing Transition**: A search FAB on the map morphs smoothly into a **Quick Flight Tools** card holding boarding pass scanning and manual input buttons via `MaterialContainerTransform`.
- **Dedicated Categorized Settings Activity**: Pushed Settings into a dedicated screen categorized with Material 3 outline cards, with custom status bar padding (`fitsSystemWindows="true"`).
- **Google App-Style Profile Switcher**: A profile button on the map tab opens a modal dialog listing travel stats (flights, countries, airports, routes count) and a direct shortcut to settings.

### Changed
- **Navigation Modernization**: Replaced custom FloatingToolbarLayout with official Material 3 `BottomNavigationView` synced to fragment screens.
- **Font & Typography overhaul**: Bundled and integrated the variable **Roboto Flex** font family as the default layout typeface.
- **AI Summary Prompts**: Bounded Gemini Nano generation prompt limits (max 8 lines, under 500 characters) and suppressed markdown header generation.
- **Map Tile Source Fallbacks**: Integrated automatic Satellite imagery and light/dark theme-matching tile overlays.

---
