# Safari Utils developer handoff

This document describes the v2.0.0 codebase. User-facing features, installation, and commands are documented in [README.md](README.md).

## Project and toolchain

Safari Utils is a client-side Fabric mod for Hypixel SkyBlock's Critter Safari.

- Java 25
- Fabric Loader 0.19+
- Fabric API
- Minecraft 26.1.2 and 26.2 profiles under `gradle/versions/`
- Shared sources under `src/main/java`
- Small compatibility sources under `src/<profile>/java`
- Optional ignored private extension under `private-api/`

Public outputs for each profile are a Safe Mode jar and an Extra jar:

```powershell
.\gradlew.bat build
.\gradlew.bat build -PextraBuild=true
.\gradlew.bat build "-PminecraftProfile=26.2"
.\gradlew.bat build "-PminecraftProfile=26.2" -PextraBuild=true
```

Private builds use `-PincludePrivateApi=true`; that flag also selects Extra behavior. The private source directory and `private-api/api-key.txt` are ignored by Git. `deployToInstance` deploys to the configured primary and optional secondary Prism mods directories, then mirrors Safari Utils configuration only from the configured source instance to the configured target instance.

## Runtime order

`SafariUtils.onInitializeClient` owns registration. The important client-tick order is:

1. Cache location, scoreboard, and tab-list state in `SafariLocation`
2. Update Birdfeeder menus and the Minecraft/Safari party observers
3. Update Sparkling state and automatic shared-collection providers
4. Process starting-inventory and local objective state, then publish optional synchronized objective snapshots from those fresh caches
5. Scan entities once through `CritterEntities`, then update entity-dependent trackers and run/session state
6. Update markers, persistent catalogs, prices, chat, and configuration

Do not add independent scoreboard, tab-list, or world-wide entity scans when an existing cache can supply the same information.

## Safari visit and run lifecycle

Entering a Safari instance creates a transient visit context immediately. Objective trackers and optional synchronization may collect information during this pre-ticket period, but the visit is not yet a run and must not be saved.

Run Lifecycle debug logging records Safari visit recognition as `visitElapsed=0.000s`, the local player's entry chat, every Safari Manager line, and the eventual instance exit with millisecond elapsed time. Use these measurements rather than assuming the server's unticketed-player timeout when maintaining the pre-ticket join-window display.

A run begins when the server places one or more normal Critter Capsules in inventory. Their appearance is authoritative proof that a ticket was accepted. Manager interaction and ticket-menu selection are useful early signals, but are not required because Hypixel can vary or omit those client-visible paths.

Catches, floor drops, Rainbow Feathers, Safari Essence changes, and other activity never activate a run. `StartingItemsWatch` owns this capsule-gated transition and freezes one immutable full-inventory snapshot. Later floor drops and inventory movement cannot alter that snapshot.

`StartingItemsWatch` begins watching on Safari entry. It activates the run immediately when the first normal capsule appears, then waits 250 ms before freezing Starting Items so the remaining server-populated inventory can settle. `TicketProtection` still records Manager and entry-menu actions and prevents a leader from starting early when ticket protection applies. Before activation, the Progress HUD shows the `N/N` attendance title and a `Join` countdown, but no run timer or statistics. The countdown uses the local entry-chat timestamp and a conservative 33-second deadline derived from repeated server samples, gradually changes from green to dark red across the full window, and is forced to `Closed` by the first Manager lockout line. Sparkling detection and detected Hideonfloor rendering intentionally remain active throughout the pre-ticket visit for scouting from the starting ship.

`PartyRosterWatch` requests `/party list` on Safari entry. The first fresh response after entry defines the complete visit roster. `SafariPartyWatch` tracks current instance attendance. The roster is immutable for the visit: leaves, kicks, crashes, delayed arrivals, and party changes do not redefine the current run. A changed party takes effect in the next Safari instance.

A run ends from its reward summary or a confirmed lobby transition. Empty visits are never persisted. `SafariSession` owns mutable run state, `RunRecord` is the persisted form, and `RunHistory` maintains aggregates only when history changes.

## Starting Items and Party Objectives

`StartingItemsWatch` counts every selected item anywhere in inventory after capsule allocation. It credits starting feed and Shining Coins to the same objective trackers that receive later floor drops.

`BirdfeederWatch` distinguishes:

- feed found or carried
- feed stably deposited by this player
- feed currently visible in Birdfeeder slot 22
- bird-spawn messages, which prove feed consumption
- cursor-held feed and rejected mismatched-stack interactions

A cursor-held stack remains held after the menu closes for a bounded resynchronization window. The same cursor-safe inventory snapshot feeds local objective HUD values and optional synchronization, so moving an item never looks like using it. No Feed and All Feed Used remain mutually exclusive. Empty alerts require locally held feed and have a three-second duplicate guard.

The public Party Objective HUD presents Forest feed, Cavern gems, Icy catch progression, or Haunted incense according to the local biome and marks other loaded party members' item counts as unknown. Its title remains visible throughout the Safari, including the center, and shows the user-selected subset of mountain, snowflake, skull, and leaf completion symbols. Cavern completes 2.5 seconds after the authoritative Gemzie door message, Icy after Wumpa is caught, and Haunted after Doomspiral is caught or its terminal retreat line confirms that the failed encounter cannot be repeated. Forest uses a yellow `?` unless synchronized run-wide feed totals are authoritative; synchronized Forest completes after every run feed has produced its bird. The Icy detail rows show `Unique Catches N/8`, excluding Wumpa itself, followed by Wumpa's waiting, spawned, or caught state. Title entries measure each icon's visible pixels and distribute all gaps—including title-side and border-side gaps—evenly. These fixed-palette icons retain their semantic colors under the Special Rainbow theme. An optional provider may replace that panel with authoritative shared state. Keep public rendering and settings functional without a provider.

Forest keeps its nine-drop line because every feed matters. Cavern and Haunted intentionally have no floor-drop count line. Their Sparkling Mode floor drops stop only after the objective is globally complete or this specific client personally holds every remaining gem or enough incense to finish; loose items split across multiple players never satisfy that local shortcut.

Objective consumption is chat-authoritative: each exact podium message sets its Gemzie placement bit, each incense-use message lights one candle, the chamber-rumbling line confirms all gems, and encounter/catch messages distinguish Wumpa and Doomspiral spawning from being caught or retreating. Inventory decreases from cursor movement, dropping, or loss never mark an objective as used. Gem rows always render purple, lime, then orange; objective icons retain semantic colors while their adjacent counts join the Special Rainbow gradient.

## Objective confirmation and Safe Mode

Safe Mode uses visible or otherwise player-observable evidence. Extra mode can use additional internal information. Optional synchronized facts are considered authoritative because another approved client observed them.

- Bee Nests accept left- or right-click interaction but clear only after a newly appearing Honeybug is confirmed within 12 blocks and five seconds
- Floor drops, mounds, walls, and stationary critters retain their existing candidate-versus-confirmed distinction
- Synchronized Forest completion may clear the corresponding Missing HUD entries and waypoints
- A loaded absent Bee Nest candidate may be repaired as completed while synchronization is authoritative

Do not let a catalog candidate become a completed objective solely because an unloaded chunk reports air.

`RecatchSpots` records every ordinary-capsule attempt against a non-Common critter, including Doomspiral and Wumpa. Hideyho is excluded because it is not captured with capsules; Commons and Masterful Capsule attempts are excluded because they cannot escape. A confirmed catch clears its pin and pity state, while an escape can carry pity to the replacement entity ID.

## Sparkling behavior

`/sparkling` opens `SparklingScreen`; all edits and imports live in that UI. Public builds provide manual Shared/Missing imports. Optional providers add automatic party collection and Player Lookup. A private client entering with any non-whitelisted party member keeps the provider idle and exposes the public manual controls after the fresh party-list response confirms that roster.

`SparklingMode` keeps the visit's expected roster stable. A party catch becomes newly shared only when everyone who needed that unique received it. Optional providers may use cached ownership to prove that an absent original member already owned the species.

`SparklingStats` stores per-species counts, Rainbow Feathers, and an imported duplicate baseline. Tracked duplicate catches advance both the imported aggregate and its comparison baseline. Unique catches do not change duplicates.

Player Lookup results are cached for five minutes. Manual Hypixel profile lookups are globally spaced by ten seconds in the private client; automatic party-cache requests run back-to-back and do not consume that manual cooldown. Automatic party loads happen on Safari entry; the same party refreshes only on a later Safari entry after five minutes have elapsed.

Screen-facing API state must remain lock-free. In particular, Player Lookup's cooldown reads a volatile request timestamp and must never acquire the monitor held by a sleeping or in-flight HTTP request, or opening the tab can freeze the render thread during an automatic party refresh.

## Optional private party synchronization

The ignored private extension resolves the complete party from the fresh `/party list` response and enables transport only when every current party member is approved. Solo private runs are synchronized because the local client has complete information.

The original run roster remains immutable. If an outsider enters the Minecraft party, outgoing hidden messages stop and queued internal messages are discarded. Already confirmed facts remain. Transport resumes only after a newer party-list capture proves that the party is approved again; the provider then resends aggregate state and every locally confirmed nest.

Aggregate messages coalesce rapid inventory and objective changes. `ChatQueue` serializes outgoing lines with a 1.2-second gap. Distinct Bee Nest confirmations are retained and retried. A departed member's completed contributions remain, while unused feed still held by that member becomes unavailable after confirmed absence.

The synchronized Forest is done only after all nine floor drops are known and every discovered feed has produced a bird. Only the client receiving the final bird-spawn line may send the completion chat alert. Cavern, Icy, and Haunted snapshots additionally preserve held items, placements, spawn states, terminal Wumpa/Doomspiral catches, and Doomspiral retreats; the Gemzie title waits 2.5 seconds after the first synchronized open confirmation. Full aggregate retries make rapid objective changes converge even when party chat is rate-limited, and the aggregate parser continues accepting the older payload lengths.

Known party members with zero relevant held items are omitted from every objective-player list. Unknown public/fallback members may still display `?` because zero cannot be inferred without synchronization. A newly recorded Sparkling blocks API-import comparison until a profile response is fetched at least ten minutes later; this prevents Hypixel's pre-catch cached collection from immediately producing a false import prompt. The not-before timestamp persists with Sparkling stats and is cleared by an explicit API import.

All user-facing party-member lists sort names case-insensitively from top to bottom or left to right. Sorting is applied only to display copies; immutable run rosters, synchronization identity, and transport ordering retain their established behavior.

## UI and configuration

`SafariConfig` fields annotated with `@Expose` are persistent JSON keys. Renames require `@SerializedName` migration aliases or explicit migration in `ConfigManager`. Party Objective HUD fields use `partyObjective...` storage names and accept both the former public `birdFeed...` and private `privateBirdFeed...` keys so existing positions and preferences survive the rename.

`SafariSettingsScreen.visibleInThisBuild` hides fields prefixed with `private` from public builds and hides diagnostic/Safe Mode controls where appropriate. Public documentation and release notes must never mention private functionality.

Custom screens use `ResponsiveUI` where a fixed reference canvas is required. Mouse events passed to scaled Minecraft widgets must be converted to logical coordinates as well as custom hit tests. `HudBox` is the single source for live and editor positioning. The HUD editor keeps its visible outline one pixel inside every edge and supports unsnapped one-pixel arrow-key adjustment.

The Contest HUD distinguishes three settings:

- ordinary Safari-only visibility
- Show Everywhere inside SkyBlock, including Dungeons and Kuudra
- Show Outside SkyBlock

`SafariLocation.findSkyblock` uses the sidebar objective title; an Area row is not required for Dungeons or Kuudra.

## Rendering and performance

Animated rainbow rendering uses `RainbowColours` as one 25 FPS clock and palette source. `UIDraw` caches styled rainbow text by repeating animation phase and screen-space position, so changing text length never changes the gradient wavelength and old frames do not churn the cache forever. Editable-text carets sample that same gradient at their current cursor position. While enabled, `SpecialTheme.text` owns the complete text line before UUID-backed player-name styling so statuses do not retain fallback-colour fragments. `SpecialTheme` batches cached stars, borders, and bars through `GuiQuadBatchRenderState`; borders sample one horizontal screen-space gradient on both horizontal edges and hold each vertical edge at its corresponding x-position color. Semantic objective-item and bird colors remain unchanged by themes. The settings workspace uses the same special-theme clock, text cache, star batch, and in-place border buffers. The Advanced unlock constellation and Sparkling catch celebration also rebuild one bounded geometry batch only on that shared visual clock. History's outer Runs/Catches cells and Sparkling stars use fixed left/right anchors; variable-width values must grow inward rather than changing their panel-edge spacing.

`ClientMessages` snapshots the shared rainbow phase once when a themed chat line is created and builds one immutable component spanning both the SafariUtils tag and body. Chat lines intentionally do not animate or rebuild after insertion; this keeps their retained rendering cost independent of the theme clock.

`SparklingConfig.specialSparklingIntensity` stores the always-enabled catch celebration level from 0 through 3. Level 0 is the original gentle five-second celebration and jingle; levels 1–3 use the warned intense visuals and score. Older `specialSparklingCatch` Boolean values migrate to level 0 when false and level 1 when true. The settings picker previews the current level and requires the photosensitivity confirmation every time a level above 0 is selected, even when it is already active. Cancelling that confirmation resets the choice to level 0.

`RunHistory.revision()` invalidates screen-facing history caches only when persisted run data changes. `SafariDashboardScreen` caches its filtered ordering, year dividers, formatted rows, column measurements, Sparkling summaries, and Bazaar totals by the relevant history/price revisions. Keep the render path proportional to the 15 visible rows rather than the full saved history.

`BazaarPrices` keeps each immutable `RunRecord` valuation for the lifetime of one fetched price snapshot and selected price source. Its complete-history total is additionally keyed by `RunHistory.revision()`. Opening a screen must reuse these valuation caches; only a new Bazaar snapshot, a changed price source, or changed run history should trigger repricing.

`HudPanel` caches its measured layout and immutable progress geometry. Objective sprites are converted to cached horizontal quads per icon/color. Do not replace these paths with repeated per-pixel `fill` calls or remeasure completed panels every frame.

`WorldEntities` supplies one immutable rendered-entity snapshot per game tick to recurring trackers and render helpers. `WallTracker` likewise shares one state/visibility result per tracker and tick. Static waypoint/entity catalogs cache their decoded `BlockPos` sets until a learned position invalidates them; callers must not independently decode or rescan those sources.

`SafariSession` maintains catch, unique, biome, attempt, failure, and Sparkling aggregates incrementally. Screen and HUD code should use those constant-time accessors instead of streaming the detailed catch maps. Settings text cleanup, display names, wrapping, and search results are cached because the reflection metadata is stable while a screen is open.

`WaypointRenderBackend` uses Minecraft 26.1.2's immediate type buffers directly and pools Minecraft 26.2's deferred geometry into one submission per render type while preserving each element's copied pose. `WaypointRenderer` conservatively frustum-culls boxes, beams, faces, and labels; caches formatted labels; and keeps live entity interpolation at render-frame frequency. `CritterEntities` still scans only once every five ticks and uses four-block spatial buckets while preserving original entity-list order for equal-distance pairing. Safe Mode visibility, depth testing, marker eligibility, label text, and interpolation are behavior constraints rather than optimization opportunities.

## Persistence

`SafariPaths` owns every path:

```text
config/safariutils/
├── safariutils.json
├── safariutils-runs.json
├── safariutils-sparkling.json
├── safariutils-static-waypoints.json
├── safariutils-static-entities.json
└── logs/
    ├── safariutils.log
    └── safariutils.previous.log
```

Settings, history, and learned catalogs use atomic writes. Migration never overwrites an existing destination. `OperationalLog` queues writes, flushes once per second, deduplicates repeated failures, and rolls at 1 MiB. Diagnostic settings reset each launch and are not lasting preferences.

## Public/private boundary

Public jars must contain no private classes, service registrations, private-roster identities, UUIDs, API keys, or generated key payloads. The public author metadata naturally retains the project author's name; public source otherwise contains only provider interfaces and no-op facades for optional integrations.

Never commit:

- `private-api/`
- any `api-key.txt`
- generated private sources
- Minecraft configuration, histories, logs, or copied jars

Before release, inspect both the Git diff and jar contents. Confirm that only private jars contain `dev/serko/safariutils/privateapi`, provider service files, and `EmbeddedApiKey`.

## Release checklist

1. Run `git diff --check`
2. Build Safe and Extra public jars for Minecraft 26.1.2 and 26.2
3. Build private jars for both profiles
4. Inspect jar metadata, filenames, class lists, service files, and public-key absence
5. Test the lifecycle, Starting Items, Party Objective HUD, objective confirmation, HUD editor, Sparkling UI, and Contest visibility in game
6. Keep README, CHANGELOG, RELEASE_NOTES, and this handoff synchronized
7. Push or publish only after the user explicitly requests it
