# SafariUtils

SafariUtils is a Fabric mod made for Hypixel SkyBlock's Critter Safari. It keeps the useful parts of a run in one place: catches, missing critters, profit, Sparkling progress, party readiness, and Miria's Contest information.

## What it includes

- Movable HUDs for run progress, missing critters, biome objectives, and contest status.
- Confirmed Gemzie, Icy Wumpa, Haunted Doomspiral, and Forest Birdfeeder progress with selectable biome-completion marks in the Party Objective HUD.
- Run history, lifetime totals, Safari Essence, Rainbow Feathers, and Bazaar profit.
- A dedicated Sparkling menu with editable collection totals, party-shared lists, Sparkling Mode, selectable always-active critters and waypoints, and special catch effects.
- Helpful markers for Safari objectives and encounters.
- Party-ready alerts and ticket protection while waiting for everyone to arrive.
- Optional Ticket Trading with draggable invite queues, per-player guest controls, Sparkling filters, and timed party automation.
- Player lookups for tickets and Sparkling collections, cached rank colors, and party-shared collections with no API key setup.
- Automatic network party sync for objectives and Sparkling discoveries, with ticket reminders and unknown-state indicators for unsynced teammates.
- Selective hiding for processed general chat and independently grouped Cavern, Icy, Haunted, and Forest objective messages.
- Automatic Hideyho acceptance, Birdfeeder inventory/empty alerts, and configurable starting-item party messages.
- A real-time Miria's Contest timer with bracket, score, and ticket tracking.
- A custom settings screen with search, themes, sounds, colors, and editable alert text.
- Launch-time update checks with release details and direct download links.
- Compact settings transfer plus safe run-history validation and repair tools.
- Banner-only, sound-only, or combined playback for each banner alert.
- A Safe Mode edition for ordinary use and an Extra edition with additional information features.

## Downloads

Choose the jar that matches your Minecraft version.

| Minecraft | Safe Mode | Extra |
|---|---|---|
| 26.1.2 | `safariutils-3.1.2+mc26.1.2.jar` | `safariutils-3.1.2-extra+mc26.1.2.jar` |
| 26.2 | `safariutils-3.1.2+mc26.2.jar` | `safariutils-3.1.2-extra+mc26.2.jar` |

The Safe Mode edition is the recommended download. Extra includes features that may provide information the player cannot directly see and may not be safe to use.

The Minecraft 26.1.2 builds have received limited testing compared with the 26.2 builds.

SafariUtils requires Java 25, Fabric Loader 0.19 or newer, and Fabric API. Mod Menu is optional.

## Install

1. Install Fabric Loader and Fabric API for your Minecraft version.
2. Put the matching SafariUtils jar in the instance's `mods` folder.
3. Start Minecraft and enter `/su` to open the settings.

Existing SafariUtils settings and history are moved into `config/safariutils/` automatically when possible.
Important mod errors appear in Minecraft's log. Developer builds additionally record compressed diagnostics under `config/safariutils/logs/`.

## Commands

| Command | Usage |
|---|---|
| `/su`, `/safari`, `/safariutils` | Opens SafariUtils settings. |
| `/su gui` | Opens the HUD editor. |
| `/safari stats` | Opens run history and statistics. |
| `/sparkling` | Opens the Sparkling collection and party menu. |

The `/su`, `/safari`, and `/safariutils` aliases support the same subcommands.

## License

SafariUtils is available under the [MIT License](LICENSE).

Credits: SafariUtils began with the initial framework from MrCloudy2's CritterMod, but almost everything has since been substantially changed, revamped, fixed, or improved.

This is an independent community project. It is not affiliated with or endorsed by Hypixel.
