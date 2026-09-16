# Safari Utils v1.5.0

## Fixes

- Made Starting Items wait for a stable, capsule-populated inventory so slower items are included
- Prevented Starting Items party messages from being discarded while party-list data is briefly stale
- Kept imported Sparkling duplicate totals and comparison data synchronized after tracked catches
- Updated automatic Hideyho acceptance to use the current server-provided action instead of a fixed command
- Kept manual Hideyho choices visible when no valid automatic action can be found or sent
- Prevented No Feed and All Feed Used from both sending after Forest objectives are complete
- Prevented Only In That Biome encounter alerts from firing outside their detected biome

## Changes & Additions

- Added a configurable Starting Items chat alert with item selection, quantities, and correct plural names
- Replaced the Sparkling command collection with a dedicated `/sparkling` menu
- Added direct editing for Sparkling counts and Rainbow Feathers in the collection menu
- Added a Party tab for importing shared or missing Sparkling lists used by Sparkling Mode
- Improved Sparkling menu layout, rarity colors, completed-biome styling, inline editing, and status feedback

## Downloads

Choose one jar for your Minecraft version:

- `safariutils-1.5.0+mc26.1.2.jar`
- `safariutils-1.5.0-extra+mc26.1.2.jar`
- `safariutils-1.5.0+mc26.2.jar`
- `safariutils-1.5.0-extra+mc26.2.jar`

The regular jars use Safe Mode. Extra includes features in advanced section that provide information the player cannot directly see and may not be safe to use; Minecraft 26.2 builds have received limited testing compared with 26.1.2
