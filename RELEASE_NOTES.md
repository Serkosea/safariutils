# Safari Utils v2.0.0

## Performance

- Reduced frame drops from the Special Rainbow theme, HUD sprites, alerts, and world markers
- Cached large saved-run histories so they are not reformatted, remeasured, or repriced every frame
- Added batched waypoint geometry, off-screen marker culling, cached waypoint labels, and spatially indexed critter pairing
- Added cached HUD layout, text, border, progress-bar, and objective-icon rendering
- Reused per-tick world entities, wall state, session aggregates, and decoded waypoint catalogs across consumers
- Batched Sparkling celebrations and encounter banner geometry for smoother alert rendering
- Unified animated rainbow colors around one shared, frame-limited clock and fixed screen-space wavelength

## Fixes

- Kept rainbow gradients continuous across changing text lengths and adjacent values
- Matched editable-text carets to the animated rainbow at their current position
- Made rainbow borders use one consistent horizontal gradient on every edge
- Kept large Sparkling collection totals comfortably inside the collection panel
- Prevented the optimized GUI batching layer from failing during client startup
- Kept History table edge spacing stable as run numbers and profit totals grow
- Kept History Runs, Catches, and Sparkling stars aligned regardless of text length
- Applied the Special Rainbow gradient consistently to interface status text
- Applied the Special Rainbow gradient to client messages and the SafariUtils chat tag
- Prevented moved, dropped, or lost gems from being mistaken for confirmed Gemzie podium placements
- Kept carried objective items in HUD totals while rearranging inventory
- Hid players with no currently held objective items from Party Objective HUD lists
- Required Forest feed consumption and Gemzie's 2.5-second door-opening delay before marking those objectives complete
- Completed Haunted after either catching Doomspiral or receiving its terminal retreat message
- Displayed Forest completion as unknown when run-wide feed totals cannot be confirmed
- Prevented caught Hideonwall markers from reappearing under lingering replacement entity IDs in Extra Mode
- Prevented failed learned-location saves from retrying every client tick

## Changes & Additions

- Added a pre-ticket Join countdown with gradual urgency coloring to show when Safari ticket use will lock
- Expanded the Bird Feed HUD into a biome-aware Party Objective HUD for Cavern, Icy, Haunted, and Forest progress
- Added reference-matched icons for Soothing Incense and the three Gemzie gems
- Added an efficient automatic error log for important mod failures without requiring debug logging
- Added objective-status and biome-color border options for the Party Objective HUD
- Added selectable Cavern, Icy, Haunted, and Forest completion marks to the Party Objectives title everywhere in the Safari
- Sorted displayed party-member names alphabetically across HUDs and Sparkling screens
- Enabled every Party Objective HUD option by default, with biome-colored borders and status coloring off
- Clarified Party Objective labels and kept Forest action labels consistently green
- Expanded the Special Rainbow theme across settings, interface accents, and every HUD
- Smoothed continuous rainbow borders, extended the gradient to editable text, and increased the cached star-field density
- Added four selectable Sparkling catch-alert intensities with a current-intensity preview and photosensitivity confirmation
- Kept Special as the original gentle celebration and added distinct optimized effects to the confirmed higher intensities

## Downloads

Choose one jar for your Minecraft version:

- `safariutils-2.0.0+mc26.1.2.jar`
- `safariutils-2.0.0-extra+mc26.1.2.jar`
- `safariutils-2.0.0+mc26.2.jar`
- `safariutils-2.0.0-extra+mc26.2.jar`

The regular jars use Safe Mode. Extra includes features in advanced section that provide information the player cannot directly see and may not be safe to use; Minecraft 26.2 builds have received limited testing compared with 26.1.2
