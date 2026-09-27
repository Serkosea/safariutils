# Safari Utils v2.2.0

## Performance

- Cached and frame-limited Sparkling alert, HUD, beacon, hitbox, label, and star rendering
- Reduced automatic diagnostic overhead with buffered, event-driven developer logging
- Reused pairing and waypoint state across rendering consumers to keep crowded runs smooth

## Changes & Additions

- Added a full Sparkling catch-alert editor with named presets, responsive previews, effects, songs, intensity, duration, volume, and custom text
- Added animated Sparkling hitboxes, through-wall Extra Mode beacons, moving gradient labels, and provisional long-range markers
- Added Eagle rarity support for guaranteed catches, pity labels, and recatch pins
- Added conservative Safe Mode candidate cleanup using known per-run critter ranges
- Added Ticket Trading for trusted-player invites, timed warping, automatic acceptance, and cleanup when the host leaves the Safari instance
- Added independent total and biome Progress HUD toggles and a persistent zero-profit line
- Improved Join countdown timing using the destination play connection and authoritative Safari Manager closure

## Fixes

- Stabilized moving, far-away, and post-catch waypoints without stale or duplicate markers
- Prevented nearby bodies and capture entities from stealing critter or Sparkling pairings
- Hid vanilla nametags only for the exact critters currently using mod-rendered hitboxes
- Preserved custom Hideon and Hideyho sizing during movement, Sparkling rendering, and recatch pins
- Delayed Sparkling Hideyho completion until its authoritative completion message
- Corrected alert-editor focus, saved presets, preview timing, clean audio fades, and control alignment
- Corrected player lookup casing/rank colors and Ticket Trading field behavior
- Kept successfully warped Ticket Trading parties together for the host's complete Safari run

## Downloads

Choose one jar for your Minecraft version:

- `safariutils-2.2.0+mc26.1.2.jar`
- `safariutils-2.2.0-extra+mc26.1.2.jar`
- `safariutils-2.2.0+mc26.2.jar`
- `safariutils-2.2.0-extra+mc26.2.jar`

The regular jars use Safe Mode. Extra includes features in Advanced that may provide information the player cannot directly see and may not be safe to use. Minecraft 26.1.2 builds have received limited testing compared with 26.2.
