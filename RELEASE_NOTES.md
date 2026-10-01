# Safari Utils v2.4.0

## Changes & Additions

- Replaced Ticket Trading's inline usernames with reusable saved-player profiles, three active slots, and three ordered backup slots
- Added per-player Sparkling filters so each Sparkling-only trader can be invited only for selected critters
- Added automatic backup invitations for confirmed offline active players
- Added cached canonical Minecraft usernames and rank-aware player-name colors where available
- Improved Ticket Trading profile editing, drag-and-drop assignment, information, theming, and responsive layouts
- Preserved the full settings workspace state when reopening it within the existing ten-second memory window
- Improved responsive behavior across Ticket Trading, Sparkling pages, settings, HUD editing, and statistics screens based on the available Minecraft GUI space

## Fixes

- Restricted Ticket Trading invite, warp, and disband automation to the confirmed party leader
- Reduced Sparkling guest leave-to-accept timing to 250 milliseconds while retaining immediate acceptance when no party must be left
- Kept successfully warped Ticket Trading parties together until the host leaves the ticketed Safari instance
- Delayed every user-facing Sparkling detection alert until the critter is visually confirmed and prevented duplicate alerts from replacement entity IDs
- Preserved legacy Ticket Trading players and settings through automatic profile migration
- Kept ordinary Party Sync and Special Theme settings visible without the advanced star unlock
- Restored All Feed Done alert controls consistently across every build

## Performance

- Cached canonical player lookups, profile colors, responsive layouts, and wrapped information text
- Kept Ticket Trading automation event-driven with bounded per-tick work and no rendering-time network waits
- Reused existing Sparkling scans and masks for per-player invite filtering

## Downloads

Choose one jar for your Minecraft version:

- `safariutils-2.4.0+mc26.1.2.jar`
- `safariutils-2.4.0-extra+mc26.1.2.jar`
- `safariutils-2.4.0+mc26.2.jar`
- `safariutils-2.4.0-extra+mc26.2.jar`

The regular jars use Safe Mode; Extra includes toggles in the Safe Mode category that when turned off, provide information the player cannot directly see and may not be safe to use; Minecraft 26.1.2 builds have received limited testing compared to 26.2.
