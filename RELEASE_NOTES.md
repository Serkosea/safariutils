# Safari Utils v2.1.0

## Fixes

- Made solo Party Objective tracking complete without enabling Party Sync or sending party messages
- Prevented Birdfeeder Empty and All Feed Done from firing before every tracked feed item is consumed
- Completed Wumpa after either catching it or losing the one-attempt encounter, while allowing a later party catch to replace Retreated with Caught
- Based Cavern completion on catching a Gemzie and kept Door Open as an intermediate state
- Kept completed objective details concise while preserving useful Forest bird counts
- Matched objective player names to their tab-list colors
- Kept hidden Safari messages available to every tracker, alert, history, and objective feature
- Corrected mound waypoint height and removed rejected transition-only Hideyho positions
- Kept nearby critters paired to separate remembered positions
- Kept debug packet settings in their correct category and improved failed-join timing cleanup
- Started the pre-ticket Join timer from the earliest queue or party-entry signal

## Changes & Additions

- Added a Hide Chat Messages checker for critter catches, loot shares, floor drops, objective messages, and Safari Manager dialogue
- Expanded the Party Objective HUD with clearer Cavern, Icy, Haunted, and Forest terminal states
- Added pale biome-relative objective colors, green placed-gem checks, and yellow intermediate encounter states
- Added opt-in Party Sync with sender/lobby-bound verification, all-member confirmation, compact state snapshots, and queued recovery
- Added selectable Always Active Critters and Always Active Waypoints for Sparkling Mode
- Added verified Hideonfloor locations and an optional learned-candidate review overlay
- Stored bundled static locations at block centers and sorted each catalog from the Safari center
- Expanded focused debug presets and read-only server-packet diagnostics
- Removed the obsolete Testing Session mode
- Made each Runs-tab scroll move through 15 history entries

## Downloads

Choose one jar for your Minecraft version:

- `safariutils-2.1.0+mc26.1.2.jar`
- `safariutils-2.1.0-extra+mc26.1.2.jar`
- `safariutils-2.1.0+mc26.2.jar`
- `safariutils-2.1.0-extra+mc26.2.jar`

The regular jars use Safe Mode. Extra includes features in Advanced that may provide information the player cannot directly see and may not be safe to use. Minecraft 26.2 builds have received limited testing compared with 26.1.2
