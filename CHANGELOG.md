# Changelog

## [1.1.1] - 2026-08-30

### Fixes

- The `/safari stats sparkling` summary now shows how many saved runs have passed since the last Sparkling run.
- Rockmite mound waypoints now disappear immediately for local breaks and promptly when entering a Cavern already cleared by party members.
- Floor-drop waypoints now disappear promptly when a nearby teammate collects them.

## [1.1.0] - 2026-08-30

### Fixes and improvements

- Added shared banner appearance settings for font style, text shadow, configurable or alert-matched backgrounds, borders, and timer bars.
- Blank custom chat-alert text now suppresses only that message while preserving banners and sounds.
- Ignore Uniques now hides ordinary hitboxes and markers for shared Sparkling species without hiding Sparkling duplicates.
- Inline setting edits no longer block unrelated clicks; manually edited slider values still guard their own slider.
- Sound pickers now explain right-click previews and use each alert's configured volume and pitch.
- Missing HUD objectives are grouped together, with a separate Unique Only display option.
- Added an option to remove the Icy cold overlay throughout the Safari.

## [1.0.0] - 2026-08-29

The first standalone Safari Utils release.

### Safari tracking

- Reliable run start and end detection from Safari Manager messages, activity fallbacks, reward summaries, and lobby IDs.
- Current-run, history, lifetime, Safari Essence, Rainbow Feather, and Bazaar profit tracking.
- Movable Progress, Missing, and Contest HUDs with directional expansion.
- Waypoints and objectives for floor drops, nests, mounds, walls, recatch spots, and special encounters.
- Bundled static-location catalogs with runtime discovery for previously unknown positions.

### Sparkling critters

- Per-species totals, duplicate counts, Rainbow Feather totals, history markers, filtering, and manual corrections.
- Sparkling Mode with shared-list filtering, unique catches, objective handling, and Ignore Uniques.
- Sparkling tracking that survives entity changes, with biome-aware chat messages.
- Rainbow HUD styling, a collection view, detection banner, catch messages, and a fullscreen catch celebration.

### Contest and party tools

- Real-time Miria's Contest countdown, bracket and score parsing, ticket tracking, persistence, and alerts.
- Pre-run party count and stable full-party notifications.
- Cached shared-Sparkling support for optional private profile providers, with no credentials in public builds.

### Interface and customization

- Dependency-free settings with search, nested sections, full-row controls, custom themes, and live previews.
- Themed HUD editor with snapping, scaling, banner placement, and saved layouts.
- Custom alert text, duration, color, sound, volume, and pitch, plus a larger alphabetized sound list.
- Redesigned statistics pages with themed controls, sortable history, scrolling, balanced layouts, and cached totals.
- Special themes and individual Safe Mode controls where applicable.
- Safe Mode and Extra jars for both supported Minecraft versions.
- Extra may show information the player cannot directly see and may not be safe to use.
- Minecraft 26.2 support is available but has received limited testing.

### Reliability and performance

- Shared per-tick caches for scoreboard, tab-list, entity, HUD, and statistics data.
- Separate rendering backends for Minecraft 26.1.2 and 26.2.
- Better entity pairing, static-object checks, stale waypoint cleanup, party roster stability, and Sparkling deduplication.
- A bundled baseline of observed static locations, without full-biome discovery sweeps.
- Runtime files grouped under `config/safariutils/`, with automatic migration of compatible data.
