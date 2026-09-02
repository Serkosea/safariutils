# Safari Utils developer handoff

This document covers the current v1.3.0 codebase. Installation, features, and commands are in [README.md](README.md).

## Project identity

Safari Utils is a client-side Fabric mod for Hypixel SkyBlock's Critter Safari. Its documentation, package names, assets, settings, and releases all use the Safari Utils name.

Public builds contain no private Hypixel API key. Bazaar prices use Hypixel's public endpoint and need no key. Chat announcements are only sent when their setting is enabled.

## Toolchain and version profiles

- Java 25
- Fabric Loader 0.19+
- Fabric API
- Safari Utils' dependency-free custom settings interface
- Minecraft's official/deobfuscated 26.x names; no separate mappings dependency
- Access widener namespace: `official`

The repository uses one shared source set plus small version-specific source directories:

| Profile | Version | Output |
|---|---|---|
| `26.1.2` | Safe Mode public | `safariutils-1.3.0+mc26.1.2.jar` |
| `26.1.2` | Extra public | `safariutils-1.3.0-extra+mc26.1.2.jar` |
| `26.2` | Safe Mode public | `safariutils-1.3.0+mc26.2.jar` |
| `26.2` | Extra public | `safariutils-1.3.0-extra+mc26.2.jar` |
| Configured deploy profile | Private developer build | `safariutils-private-1.3.0-extra+mc<version>.jar` |

The version-specific `WaypointRenderer` and `ClientCompat` implementations isolate rendering/API differences. Do not create version branches for normal compatibility work.

```powershell
.\gradlew.bat compileJava
.\gradlew.bat build
.\gradlew.bat build "-PextraBuild=true"
.\gradlew.bat compileJava "-PminecraftProfile=26.2"
.\gradlew.bat build "-PminecraftProfile=26.2"
.\gradlew.bat build "-PminecraftProfile=26.2" "-PextraBuild=true"
```

`build` only builds. `deployToInstance` copies the selected profile's jar when `-PdeployDir=<mods folder>` is supplied, and `deployToInstanceAndLaunch` deploys and launches it. Override `prismExecutable` and `prismInstance` when needed. Machine-specific paths belong on the command line or in the user's Gradle properties, never in committed source.

Parser changes should be checked against captured server messages, then verified in game for scoreboard, tab-list, entity, and rendering behavior.

## Runtime pipeline

`SafariUtils.onInitializeClient` registers the client tick, chat, rendering, commands, and HUD elements. The rough dependency order is:

1. `SafariLocation` parses cached scoreboard/tab-list state and identifies SkyBlock, area, sub-area, biome, and unique lobby ID.
2. `CritterEntities` performs the shared entity sweep consumed by detection, markers, hitboxes, and Sparkling logic.
3. `SessionManager` decides whether a run is pending, active, or finished.
4. Objective and encounter watchers update the session.
5. HUDs, alerts, and markers consume that state.
6. `ConfigManager` persists settings after the editor closes.

Read the scoreboard and tab list once per tick. Several features use the same lines, so separate scans waste work and can disagree within one tick.

## Run lifecycle

A Safari lobby is not automatically an active run.

- Entering `Area: Safari` creates pending arrival state and resets stale lobby information.
- The run normally activates from the exact Safari Manager completion line. Leader and party-member messages are both supported.
- Catches, loot shares, relevant floor-drop activity, or positive Safari Essence activity can activate the session as safety fallbacks. Pre-activation catch rewards are buffered and committed only if the run becomes active.
- The pre-run `Players (N)` tab-list value is used for the Progress HUD and full-party alert. It is ignored as an activation requirement once the run starts.
- A run ends from the Safari reward summary or a confirmed area/lobby-ID transition. The unique scoreboard lobby ID handles Safari-to-Safari warps and prevents a failed warp into the same lobby from ending the run.
- The reward summary's Safari Essence value confirms the final run total; live positive scoreboard deltas drive the running value. Decreases are treated as spending, not negative profit.

Keep matching exact and anchored. Location substrings, player-quoted messages, and capsule labels have all caused false positives in the past.

## State and persistence

`SafariPaths` owns all paths and migrates older layouts on startup:

```text
config/safariutils/
├── safariutils.json
├── safariutils-runs.json
├── safariutils-sparkling.json
├── safariutils-static-waypoints.json
└── safariutils-static-entities.json
```

The private developer build may also create timestamped files under `config/safariutils/logs/` while its output log is enabled.

Use `SafariPaths` rather than constructing any path manually. Never delete or overwrite a user's old root-level files during a migration until the replacement has been written successfully.

- `SafariSession` owns mutable current-run totals.
- `RunRecord` is the persisted finished-run representation.
- `RunHistory` loads, saves, and summarizes run records.
- `SparklingStats` owns cumulative per-species catches and Rainbow Feathers.
- `ContestTracker` persists the ongoing contest identity and last known standing in the settings data so restarts during the same contest do not replay one-time alerts.

Persisted fields must remain exposed to Gson. Renaming one without migration resets the user's value. The custom interface reads Safari Utils' local annotations through reflection; field names remain the stable persistence and UI identities.

## Major systems

| System | Main classes | Notes |
|---|---|---|
| Location and session | `SafariLocation`, `SessionManager`, `SafariSession` | Area is from tab list; sub-area is from scoreboard; lobby ID is scoreboard-derived. |
| Chat parsing | `ChatParser`, `CritterEvent` | Reject player-typed quotations unless a feature deliberately reads shared mod output. |
| Entity detection | `CritterEntities`, `CritterSpotter`, `DetectedCritters`, `StillCritters` | One shared sweep; preserve entity identity rules around captures and multi-part mobs. |
| Missing HUD/objectives | `MissingHud`, `SafariObjectives`, `FloorDrops`, `MoundSpotter`, `WallTracker`, `NestTracker` | Normal and Sparkling Mode have different completion and presentation rules. |
| Sparkling | `SparklingWatch`, `ParticleDiagnostics`, `SparklingMode`, `SparklingStats`, `FullScreenAlert` | Name tags and the validated repeated particle signature complement each other. A live Sparkling remains tracked until caught. |
| Profit | `BazaarPrices`, session/history models | Uses `ESSENCE_SAFARI`, `RAINBOW_FEATHER`, and shard product IDs. Network/cache failures must fail without corrupting run totals. |
| Contest | `ContestTracker`, `ProgressHud` | Real-time cycle is 20 minutes; contest duration is 19:33. Score and bracket come from tab list. |
| Alerts/chat | `EncounterAlerts`, `ChatQueue`, watcher classes | Alert processing cannot depend on a HUD being visible. Each alert owns text, duration, color, and sound; placement and scale are shared. |
| Rendering | `Markers`, versioned `WaypointRenderer`, `HudPanel`, `HudBorderStyle`, `SpecialTheme` | Minecraft 26.2 has a separate renderer. Test block faces while falling as well as standing. |
| Configuration | `SafariConfig`, `ConfigManager`, `SafariSettingsScreen`, `AdvancedUnlock` | Local annotations define cards and groups; verify the rendered hierarchy, editors, and search in game. |

Banner text, duration, color, and sound remain alert-specific. Position and scale are intentionally shared through `AlertConfig.alertHorizontalPosition`, `alertVerticalPosition`, and `alertScale`, edited through the Banner Alerts target in `HudEditorScreen`. Older per-alert placement fields remain persisted for compatibility but are hidden and must not drive rendering.

## Sparkling Mode invariants

- Public builds use `/sparkling shared <comma-separated species>`; no-argument `shared` displays the alphabetized list. `/sparkling missing` accepts the inverse list.
- Before a species' unique catch, Missing HUD shows `Near` only—never `N+ Left`.
- After its unique catch, a still-relevant species remains available for Sparkling hunting without a cumulative Seen counter.
- Shared species disappear after their unique catch; unshared species remain available for Sparkling hunting.
- Loot-shared catches count as the party's unique catch.
- If the current player count is lower than the run's expected party count when a Sparkling is caught, do not mutate the shared list; a disconnected member may not have received it.
- Reducing ordinary Sparkling Mode HUD information must not disable Sparkling catch tracking.
- `Ignore Uniques` hides shared species and their prerequisite objectives, but never disables detection of a Sparkling version.
- Objective items are hidden only after the requirement they enable is complete. Death can remove inventory, so re-evaluate live inventory after the faint message instead of trusting an accumulated counter.
- Multi-part or transformed mobs require special caps/buffers. Use species spawn maxima and the existing Shyworm cap; do not treat temporary capture-ball armor stands as new sightings.

## Build versions and Advanced

Every version includes a locked constellation in Settings. `SafariSettingsScreen` owns its node order, and completing it reveals Advanced for the current Minecraft session.

The default build is the Safe Mode edition. `-PextraBuild=true` adds the Extra features and exposes the Safe Mode controls. `BuildVersion.SAFE` keeps every Safe Mode decision enabled in the default edition, whose Advanced page contains only Special Themes.

Public jars do not register diagnostic commands, run debug collection, or show diagnostic settings. Those tools are available only when `-PincludePrivateApi=true` creates the private developer build. `BuildVersion.DEVELOPER` is the gate for this boundary.

Bundled static locations seed Safe Mode. Unknown positions can still be used during the current run; persistent learning is limited to clean solo Hideonfloor observations. Neither edition performs a full-biome discovery sweep.

## Gameplay and alert behavior

- `PartyRosterWatch` quietly verifies the party through `/party list`; only joining a party produces its client confirmation.
- `TicketProtection` blocks the leader's Manager interaction or a member's ticket selection while attendance is incomplete. Unknown roster data fails open. Keep the tested grace and stability timings unchanged.
- `HideyhoAutoAccept` consumes the current choice line and accepts it before it reaches chat.
- Feed-used alerts use inventory deposits, not spawn messages, which may be delivered only to the last person who added feed.
- `BirdfeederWatch.tickMenu` watches slot 22 for a loaded-to-empty transition in the open Birdfeeder menu. Opening an empty menu does not alert.
- Banner playback indices are 0 Off, 1 Banner, 2 Sound, 3 Banner + Sound. `ConfigManager` migrates old toggles; sound-only events must not replace a visible banner.
- All clipboard commands live under `/sparkling import`. The optional `shared` and `missing` branches accept plain lists; the bare command parses the formatted message. They all replace the same shared collection.
- Private refresh/lookup implementation and credentials remain ignored and are never release assets.

## Optional custom sounds

A future user-sound library should read `.ogg` files from `config/safariutils/sounds/` and expose them through a generated runtime resource pack plus Minecraft's normal sound manager. Refresh the pack only when files change or the player requests it; do not scan the folder every tick. This preserves Minecraft's device handling and volume categories. Direct OpenAL playback would offer independent gain but is intentionally avoided because it can conflict with Minecraft's mixer and audio-device lifecycle. Source audio can be normalized before loading to provide stronger volume without layering the same event repeatedly.

## Editing rules and known traps

- Use exact or anchored server-text patterns. Never let `Entry To Critter Safari` match `Safari`, or a player quotation impersonate a server event.
- The `entered Critter Safari!` line contains the party leader's name, not necessarily the local player's name.
- Player levels in tab list are optional. Parse names both with and without `[level]` prefixes.
- The scoreboard and tab list update asynchronously during lobby joins. Discard residual `Players` values above four and compare the stabilized `Players (N)` line with the verified party size before the full-party alert.
- Contest transitions are wall-clock driven; scoreboard seconds are display context, not the timer source. Reset score, bracket, and ticket state at the contest boundary.
- Safe Mode is unconditional in the default edition and configurable in Extra.
- Renderer line vertices require a width. For 26.2 block faces, test falling and slope movement; culling based on vertical motion previously hid top faces.
- Alert groups are intentionally nested per alert: test button, Play Alert, Appearance Settings, and Sound Settings. Verify the custom screen after changing accordion IDs.
- Keep comments concise and explain reasons or non-obvious server behavior, not line-by-line mechanics.

## Public/private boundary

The public repository includes only the `SharedSparklingProvider` boundary and manual shared-list behavior. Optional developer-only profile lookup sources and credentials belong under ignored `private-api/` and are included only with `-PincludePrivateApi=true`.

Never commit:

- `private-api/`
- `api-key.txt` anywhere in the tree
- `.env` files, PEM files, or key files
- Minecraft config, run history, or debug logs

Before every public push, verify the private directory and key files remain ignored, inspect the staged diff, and scan the public jars for private classes, provider registrations, and credentials. A personal Hypixel key must never be embedded in a public jar or repository.

## Release standard

For both profiles: compile, build, launch, open `/su`, render all HUDs, test waypoints while standing/jumping/falling, and complete a short Safari/contest smoke test. Confirm the jar metadata and names before publishing. GitHub generates the source zip and tar.gz automatically from the release tag.
