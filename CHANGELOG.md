# Changelog

## [1.3.1] - 2026-09-04

### Alerts and birds

- Added Mute Other Sounds to keep Safari Utils alerts and sound previews audible while muting other Minecraft audio
- Changed an empty `<ALL_FEED>` value to `No Feed`
- Removed the unintended local client-message copy of Biome Uniques Done chat alerts
- Split Contest warning suppression into separate banner and chat settings
- Applied Only In That Biome separately to Forest bird banners and chat alerts
- Removed the unreliable Feed Type Used alert and placed Birdfeeder Empty before All Feed Used
- Prevented rejected or cursor-held Bird Feed clicks from firing All Feed Used early

### Defaults and polish

- Updated fresh-install defaults to match the maintained configuration, with Safe Mode enabled and the high-intensity Sparkling catch disabled
- Prevented verified solo players from sending configured party alerts and refreshed party status quietly after reconnecting
- Made Reset Page affect only settings visible in the current expanded view
- Added confirmation before resetting the HUD layout
- Added scrollbar-free navigation scrolling for small windows
- Made on-screen HUDs, banner alerts, the HUD editor, and Safari stats scale consistently on smaller screens
- Kept the settings search prompt inside its field at narrow widths

## [1.3.0] - 2026-09-02

### Gameplay

- Added ticket protection while party members are still missing from the Safari, including ticket-menu number-key selections
- Added automatic Hideyho Hide 'N Seek acceptance
- Improved party-ready alerts for the current party size and automatic roster verification

### Birds and alerts

- Grouped bird alerts together and added feed totals, Feed Type Used, All Feed Used, and Birdfeeder Empty alerts
- Feed-used alerts track inventory deposits rather than bird-spawn messages
- Added compact feed-list text with correct plurals
- Combined banner playback into Off, Banner, Sound, or Banner + Sound, preserving existing preferences

### Commands and polish

- Moved Sparkling commands to `/sparkling` and removed the run-reset command
- Added missing-list entry/display and clipboard imports for shared lists, missing lists, and complete Sparkling chat messages
- Unified client-message styling and trimmed unnecessary message clutter
- Reduced repeated text parsing and ticket-menu field lookups without changing gameplay timing


## [1.2.0] - 2026-09-01

### Sparkling critters

- Added particle-backed Sparkling detection.
- Added a rainbow beacon, labels, hitboxes, and waypoint styling for a detected Sparkling.
- Added `Only Show Sparkling` to hide ordinary stationary-critter waypoints while Sparkling Mode is active.
- Added an optional high-intensity Sparkling catch celebration with a prominent photosensitivity warning and confirmation.
- Fixed duplicate detection alerts, transformed-entity tracking, stale Missing HUD entries, and leftover recatch markers after a Sparkling catch.
- Safe Mode now accepts a genuinely visible critter nametag while continuing to withhold hidden internal entity information.

### Waypoints and reliability

- Added `Hide Possible` for players who know the objective routes and do not want candidate waypoints displayed.
- Expanded the bundled stationary-critter and objective-location catalogs from verified solo runs.
- Improved Rockmite mound detection, close-position matching, party-break cleanup, entity-range persistence, and shared scan performance.
- Updated the Sparkling beacon to use a continuous translucent Minecraft-style beam.
- Moved Snoozle wall waypoints up to eye level and reorganized recatch settings with the critter waypoint options.

### Interface and polish

- Reorganized Safe Mode settings with clearer, alphabetized descriptions.
- Standardized `armor` spelling throughout the project.

## [1.1.2] - 2026-08-30

### Fixes

- Known Rockmite mound positions remain detectable when a Flavor Packed Fish overlaps their interaction box.
- Live Rockmite mounds now recover after a temporary interaction-entity loading gap in either mode.

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
