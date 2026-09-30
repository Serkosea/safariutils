# Safari Utils v2.3.0

## Changes & Additions

- Rebuilt settings into a modern, responsive workspace with clearer categories, tabs, searchable controls, and collapsed nested sections
- Reorganized HUD, waypoint, theme, alert, Sparkling, gameplay, Safe Mode, and Developer settings while preserving existing saved settings
- Added responsive layouts across settings, Sparkling pages, Ticket Trading, HUD editing, and statistics screens
- Added Shard Trader chat filtering with colored trade summaries, preserved clickable choices, and traded shards included in run profit
- Added per-player Sparkling-only Ticket Trading with detection-gated host invites and safe leave-then-accept guest transfers
- Improved Ticket Trading visuals, guidance, field behavior, and responsive layout
- Replaced Safe Mode's master switch with independent feature controls in Extra builds and a clear read-only explanation in regular builds
- Refreshed default HUD positions and improved HUD editor layering while dragging panels

## Performance

- Cached responsive settings text layouts, shared-card organization, and search ordering
- Removed repeated per-frame settings traversal and theme-label allocations
- Bounded resize-sensitive UI caches and culled off-screen settings rows

## Downloads

Choose one jar for your Minecraft version:

- `safariutils-2.3.0+mc26.1.2.jar`
- `safariutils-2.3.0-extra+mc26.1.2.jar`
- `safariutils-2.3.0+mc26.2.jar`
- `safariutils-2.3.0-extra+mc26.2.jar`

The regular jars use Safe Mode; Extra includes toggles in the Safe Mode category that when turned off, provide information the player cannot directly see and may not be safe to use; Minecraft 26.1.2 builds have received limited testing compared to 26.2.
