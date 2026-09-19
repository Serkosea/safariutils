# Safari Utils developer handoff

This document describes the v1.6.0 codebase. User-facing features, installation, and commands are documented in [README.md](README.md).

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
3. Update Sparkling and optional provider state
4. Scan entities once through `CritterEntities`
5. Process encounter, starting-inventory, objective, and session state
6. Update markers, persistent catalogs, prices, chat, and configuration

Do not add independent scoreboard, tab-list, or world-wide entity scans when an existing cache can supply the same information.

## Safari visit and run lifecycle

Entering a Safari instance creates a transient visit context immediately. Objective trackers and optional synchronization may collect information during this pre-ticket period, but the visit is not yet a run and must not be saved.

A run begins when the server places one or more normal Critter Capsules in inventory. Their appearance is authoritative proof that a ticket was accepted. Manager interaction and ticket-menu selection are useful early signals, but are not required because Hypixel can vary or omit those client-visible paths.

Catches, floor drops, Rainbow Feathers, Safari Essence changes, and other activity never activate a run. `StartingItemsWatch` owns this capsule-gated transition and freezes one immutable full-inventory snapshot. Later floor drops and inventory movement cannot alter that snapshot.

`StartingItemsWatch` begins watching on Safari entry. It activates the run immediately when the first normal capsule appears, then waits 250 ms before freezing Starting Items so the remaining server-populated inventory can settle. `TicketProtection` still records Manager and entry-menu actions and prevents a leader from starting early when ticket protection applies. Before activation, the Progress HUD shows only the `N/N` attendance title and no timer or run statistics. Sparkling detection and detected Hideonfloor rendering intentionally remain active throughout the pre-ticket visit for scouting from the starting ship.

`PartyRosterWatch` requests `/party list` on Safari entry. The first fresh response after entry defines the complete visit roster. `SafariPartyWatch` tracks current instance attendance. The roster is immutable for the visit: leaves, kicks, crashes, delayed arrivals, and party changes do not redefine the current run. A changed party takes effect in the next Safari instance.

A run ends from its reward summary or a confirmed lobby transition. Empty visits are never persisted. `SafariSession` owns mutable run state, `RunRecord` is the persisted form, and `RunHistory` maintains aggregates only when history changes.

## Starting Items and Bird Feed

`StartingItemsWatch` counts every selected item anywhere in inventory after capsule allocation. It credits starting feed and Shining Coins to the same objective trackers that receive later floor drops.

`BirdfeederWatch` distinguishes:

- feed found or carried
- feed stably deposited by this player
- feed currently visible in Birdfeeder slot 22
- bird-spawn messages, which prove feed consumption
- cursor-held feed and rejected mismatched-stack interactions

A cursor-held stack remains held after the menu closes for a bounded resynchronization window. No Feed and All Feed Used remain mutually exclusive. Empty alerts require locally held feed and have a three-second duplicate guard.

The public Bird Feed HUD presents conservative local information and marks other loaded party members' feed as unknown. An optional provider may replace that panel with authoritative shared state. Keep public rendering and settings functional without a provider.

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

Player Lookup results are cached for five minutes. Hypixel profile requests are globally spaced by ten seconds in the private client. Automatic party loads happen on Safari entry; the same party refreshes only on a later Safari entry after five minutes have elapsed.

## Optional private party synchronization

The ignored private extension resolves the complete party from the fresh `/party list` response and enables transport only when every current party member is approved. Solo private runs are synchronized because the local client has complete information.

The original run roster remains immutable. If an outsider enters the Minecraft party, outgoing hidden messages stop and queued internal messages are discarded. Already confirmed facts remain. Transport resumes only after a newer party-list capture proves that the party is approved again; the provider then resends aggregate state and every locally confirmed nest.

Aggregate messages coalesce rapid inventory and objective changes. `ChatQueue` serializes outgoing lines with a 1.2-second gap. Distinct Bee Nest confirmations are retained and retried. A departed member's completed contributions remain, while unused feed still held by that member becomes unavailable after confirmed absence.

The synchronized Forest is done only after all nine floor drops are known and every discovered feed has produced a bird. Only the client receiving the final bird-spawn line may send the completion chat alert.

## UI and configuration

`SafariConfig` fields annotated with `@Expose` are persistent JSON keys. Renames require `@SerializedName` migration aliases or explicit migration in `ConfigManager`. Bird Feed HUD fields use public `birdFeed...` names while accepting the earlier `privateBirdFeed...` keys.

`SafariSettingsScreen.visibleInThisBuild` hides fields prefixed with `private` from public builds and hides diagnostic/Safe Mode controls where appropriate. Public documentation and release notes must never mention private functionality.

Custom screens use `ResponsiveUI` where a fixed reference canvas is required. Mouse events passed to scaled Minecraft widgets must be converted to logical coordinates as well as custom hit tests. `HudBox` is the single source for live and editor positioning. The HUD editor keeps its visible outline one pixel inside every edge and supports unsnapped one-pixel arrow-key adjustment.

The Contest HUD distinguishes three settings:

- ordinary Safari-only visibility
- Show Everywhere inside SkyBlock, including Dungeons and Kuudra
- Show Outside SkyBlock

`SafariLocation.findSkyblock` uses the sidebar objective title; an Area row is not required for Dungeons or Kuudra.

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
```

Settings, history, and learned catalogs use atomic writes. Migration never overwrites an existing destination. Diagnostic settings reset each launch and are not lasting preferences.

## Public/private boundary

Public jars must contain no private classes, service registrations, usernames, UUIDs, API keys, or generated key payloads. Public source may contain only provider interfaces and no-op facades.

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
5. Test the lifecycle, Starting Items, Bird Feed, objective confirmation, HUD editor, Sparkling UI, and Contest visibility in game
6. Keep README, CHANGELOG, RELEASE_NOTES, and this handoff synchronized
7. Push or publish only after the user explicitly requests it
