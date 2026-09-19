# Safari Utils v1.6.0

## Fixes

- Used the server's Critter Capsule allocation to reliably start runs across every ticket submission path
- Kept the title-only party attendance HUD visible while waiting for Safari Tickets
- Kept Sparkling detection and detected Hideonfloor waypoints available before ticket use
- Made Starting Items wait for the complete capsule-populated inventory without including later floor drops
- Prevented cursor-held or rejected Bird Feed transfers from being treated as completed deposits
- Confirmed Bee Nest completion from a nearby Honeybug spawn and supported both left- and right-click interactions
- Restored recatch pins and pity progression for Doomspiral, Wumpa, and every other non-Common capsule catch
- Kept the Contest HUD visible in Dungeons and Kuudra when Show Everywhere is enabled
- Kept HUD panels and their editor labels aligned consistently against every screen edge

## Changes & Additions

- Added a movable Bird Feed HUD with feed, Birdfeeder, Forest-drop, and visible-bird status
- Added one-pixel arrow-key adjustments to the HUD editor
- Improved Safari party and run lifecycle tracking around delayed arrivals, disconnects, and lobby transitions
- Cleaned up shared trackers, settings migration, rendering helpers, and build organization

## Downloads

Choose one jar for your Minecraft version:

- `safariutils-1.6.0+mc26.1.2.jar`
- `safariutils-1.6.0-extra+mc26.1.2.jar`
- `safariutils-1.6.0+mc26.2.jar`
- `safariutils-1.6.0-extra+mc26.2.jar`

The regular jars use Safe Mode. Extra includes features in advanced section that provide information the player cannot directly see and may not be safe to use; Minecraft 26.2 builds have received limited testing compared with 26.1.2
