# Safari Utils

Safari Utils is a Fabric mod made for Hypixel SkyBlock's Critter Safari. It keeps the useful parts of a run in one place: catches, missing critters, profit, Sparkling progress, party readiness, and Miria's Contest information.

## What it includes

- Movable HUDs for run progress, missing critters, and contest status.
- Run history, lifetime totals, Safari Essence, Rainbow Feathers, and Bazaar profit.
- A dedicated Sparkling collection page, Sparkling Mode, shared-party lists, and special catch effects.
- Helpful markers for Safari objectives and encounters.
- Party-ready alerts before a run starts.
- A real-time Miria's Contest timer with bracket, score, and ticket tracking.
- A custom settings screen with search, themes, sounds, colors, and editable alert text.
- A Safe Mode edition for ordinary use and an Extra edition with additional information features.

## Downloads

Choose the jar that matches your Minecraft version.

| Minecraft | Safe Mode | Extra |
|---|---|---|
| 26.1.2 | `safariutils-1.1.2+mc26.1.2.jar` | `safariutils-1.1.2-extra+mc26.1.2.jar` |
| 26.2 | `safariutils-1.1.2+mc26.2.jar` | `safariutils-1.1.2-extra+mc26.2.jar` |

The Safe Mode edition is the recommended download. Extra includes features that may provide information the player cannot directly see and may not be safe to use.

The Minecraft 26.2 builds have received limited testing compared with the 26.1.2 builds.

Safari Utils requires Java 25, Fabric Loader 0.19 or newer, and Fabric API. Mod Menu is optional.

## Install

1. Install Fabric Loader and Fabric API for your Minecraft version.
2. Put the matching Safari Utils jar in the instance's `mods` folder.
3. Start Minecraft and enter `/su` to open the settings.

Existing Safari Utils settings and history are moved into `config/safariutils/` automatically when possible.

## Commands

| Command | Usage |
|---|---|
| `/su`, `/safari`, `/safariutils` | Opens Safari Utils settings. |
| `/su gui` | Opens the HUD editor. |
| `/safari stats` | Opens run, history, statistics, and Sparkling pages. |
| `/safari stats reset` | Resets the current tracked run. |
| `/safari stats sparkling` | Shows saved Sparkling totals. |
| `/safari stats sparkling set <species> <count>` | Corrects a Sparkling species total. |
| `/safari stats sparkling feathers <count>` | Corrects the Rainbow Feather total. |
| `/safari stats sparkling shared` | Shows the saved shared-party list. |
| `/safari stats sparkling shared <species, ...>` | Replaces the shared list. |
| `/safari stats sparkling shared reset` | Clears the shared list. |

The `/su` and `/safariutils` aliases support the same subcommands.

## License

Safari Utils is available under the [MIT License](LICENSE).

Credits: Safari Utils began with the initial framework from MrCloudy2's CritterMod, but almost everything has since been substantially changed, revamped, fixed, or improved.

This is an independent community project. It is not affiliated with or endorsed by Hypixel.
