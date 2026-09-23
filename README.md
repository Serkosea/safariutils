# Safari Utils

Safari Utils is a Fabric mod made for Hypixel SkyBlock's Critter Safari. It keeps the useful parts of a run in one place: catches, missing critters, profit, Sparkling progress, party readiness, and Miria's Contest information.

## What it includes

- Movable HUDs for run progress, missing critters, biome objectives, and contest status.
- Confirmed Gemzie, Icy Wumpa, Haunted Doomspiral, and Forest Birdfeeder progress with selectable biome-completion marks in the Party Objective HUD.
- Run history, lifetime totals, Safari Essence, Rainbow Feathers, and Bazaar profit.
- A dedicated Sparkling menu with editable collection totals, party-shared lists, Sparkling Mode, selectable always-active critters and waypoints, and special catch effects.
- Helpful markers for Safari objectives and encounters.
- Party-ready alerts and ticket protection while waiting for everyone to arrive.
- Optional Party Sync for sharing objective state through parsed party chat when every member uses Safari Utils and explicitly enables it.
- Selective hiding for processed general chat and independently grouped Cavern, Icy, Haunted, and Forest objective messages.
- Automatic Hideyho acceptance, Birdfeeder inventory/empty alerts, and configurable starting-item party messages.
- A real-time Miria's Contest timer with bracket, score, and ticket tracking.
- A custom settings screen with search, themes, sounds, colors, and editable alert text.
- Banner-only, sound-only, or combined playback for each banner alert.
- A Safe Mode edition for ordinary use and an Extra edition with additional information features.

## Downloads

Choose the jar that matches your Minecraft version.

| Minecraft | Safe Mode | Extra |
|---|---|---|
| 26.1.2 | `safariutils-2.1.0+mc26.1.2.jar` | `safariutils-2.1.0-extra+mc26.1.2.jar` |
| 26.2 | `safariutils-2.1.0+mc26.2.jar` | `safariutils-2.1.0-extra+mc26.2.jar` |

The Safe Mode edition is the recommended download. Extra includes features that may provide information the player cannot directly see and may not be safe to use.

The Minecraft 26.2 builds have received limited testing compared with the 26.1.2 builds.

Safari Utils requires Java 25, Fabric Loader 0.19 or newer, and Fabric API. Mod Menu is optional.

## Install

1. Install Fabric Loader and Fabric API for your Minecraft version.
2. Put the matching Safari Utils jar in the instance's `mods` folder.
3. Start Minecraft and enter `/su` to open the settings.

Existing Safari Utils settings and history are moved into `config/safariutils/` automatically when possible.
Important mod errors are recorded automatically in `config/safariutils/logs/safariutils.log`; verbose debug logging remains optional.

Party Sync is disabled on every launch. When enabled under Advanced, it sends one compact visible verification token for a new stable Safari party and exchanges parsed objective updates only after every member confirms the same capability. Verification and shutdown tokens are bound to their displayed sender and current Safari lobby. Disabling it during an active synchronized run visibly notifies the party and stops synchronization for everyone.

## Commands

| Command | Usage |
|---|---|
| `/su`, `/safari`, `/safariutils` | Opens Safari Utils settings. |
| `/su gui` | Opens the HUD editor. |
| `/safari stats` | Opens run history and statistics. |
| `/sparkling` | Opens the Sparkling collection and party menu. |

The `/su`, `/safari`, and `/safariutils` aliases support the same subcommands.

## License

Safari Utils is available under the [MIT License](LICENSE).

Credits: Safari Utils began with the initial framework from MrCloudy2's CritterMod, but almost everything has since been substantially changed, revamped, fixed, or improved.

This is an independent community project. It is not affiliated with or endorsed by Hypixel.
