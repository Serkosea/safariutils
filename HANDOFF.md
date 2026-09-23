# Safari Utils developer handoff

This document records the invariants needed to maintain the v2.1 codebase. User-facing features, installation, and commands belong in [README.md](README.md); release history belongs in [CHANGELOG.md](CHANGELOG.md).

## Project and builds

Safari Utils is a client-side Fabric mod for Hypixel SkyBlock's Critter Safari. It targets Java 25, Fabric Loader 0.19+, and the Minecraft profiles under `gradle/versions/`.

- Shared code: `src/main/java`
- Profile compatibility code: `src/<profile>/java`
- Optional ignored extension: `private-api/`
- Safe build: `./gradlew build`
- Extra build: `./gradlew build -PextraBuild=true`
- Other profile: add `-PminecraftProfile=<profile>`
- Private build: add `-PincludePrivateApi=true` (also selects Extra behavior)

Build variants use separate output directories so stale private classes cannot enter public jars. `deployToInstance` copies the selected jar to configured Prism instances and mirrors Safari Utils configuration only from the configured source instance to the target.

## Runtime and lifecycle

`SafariUtils.onInitializeClient` owns registration. Each client tick deliberately follows this order:

1. Cache location, scoreboard, and tab-list state
2. Update menus and party observers
3. Update Sparkling state and optional providers
4. Update starting inventory and objective state, then publish optional shared snapshots
5. Scan entities once and update entity-dependent trackers
6. Update session state, markers, catalogs, prices, chat, and configuration

Reuse `SafariLocation`, `WorldEntities`, `CritterEntities`, and existing tracker caches. Do not add independent scoreboard, tab-list, or world-wide entity scans.

Entering Safari creates a transient visit immediately. Scouting, objective observation, and optional synchronization may run before ticket use, but the visit is not saved as a run. A run starts only when one or more normal Critter Capsules appear in inventory, proving that the server accepted a ticket. Manager interactions are hints, not activation requirements. Catches, drops, essence changes, and feathers never activate a run.

`StartingItemsWatch` owns activation. It waits 250 ms after capsule allocation before freezing one complete inventory snapshot so later drops and inventory movement cannot change Starting Items. Before activation, the Progress HUD may show attendance, the conservative `Join` countdown, Sparkling detections, and detected Hideonfloor waypoints, but not run timing or statistics.

The countdown begins from the earliest local queue notice or confirmed party-entry notice for the attempt and uses a conservative 33-second deadline. It displays `Closing` at zero or after the Manager's lockout dialogue begins. This is an estimate, especially when another member reaches the instance first. `JoinWindowDiagnostics` and read-only packet diagnostics exist to refine it without sending, modifying, retaining, cancelling, or delaying network traffic.

The first fresh `/party list` response after entry freezes the visit roster. Leaves, kicks, crashes, delayed arrivals, and party changes do not rewrite that roster; a new composition applies on the next Safari entry. Attendance is tracked separately. Reward summary or confirmed lobby transition ends a run. Empty visits are never persisted.

## Objectives, Safe Mode, and waypoints

Inventory totals include cursor-held items so rearranging an item never looks like using it. Objective consumption is chat-authoritative:

- Gem podium messages set individual placement bits
- The chamber-rumbling line confirms the Gemzie door is open
- Incense-use messages light candles
- Bird-spawn messages prove feed consumption
- Catch and terminal encounter messages complete Gemzie, Wumpa, and Doomspiral states

The Party Objectives title remains visible throughout Safari and shows the enabled biome icons. Detail lines are biome-local. Cavern completes on a Gemzie catch, Icy on a Wumpa catch, Haunted on a Doomspiral catch or terminal retreat, and synchronized Forest after every discovered feed has produced a bird. Unsynchronized Forest completion is unknown. Completed biomes collapse to their terminal row; Forest may retain Birds. Player names use live tab-list rank colors, with UUID-backed owner styling applied afterward.

Forest retains its nine-drop count because every feed matters. Cavern and Haunted intentionally have no floor-drop count. Their floor-drop guidance stops only when the objective is complete or this client personally holds every remaining required item.

Safe Mode exposes visible or otherwise player-observable evidence. Extra Mode may expose additional internal detections. Presentation settings never discard tracker state: switching modes or toggles mid-run must immediately render the appropriate already-known subset. Caches that affect presentation include the configuration revision.

- Bee Nests clear only after a left/right interaction is followed by a new nearby Honeybug within five seconds
- Loaded air alone never completes an objective candidate
- Ordinary capsule failures against non-Common capturable critters create recatch pins
- Commons, Masterful Capsule attempts, confirmed catches, and Hideyho do not retain pins
- Always Active Critters bypass ordinary Sparkling/unique filtering for selected species
- Always Active Waypoints do the same for selected objective sources
- Neither checker bypasses global display, biome, completion, or Safe Mode rules

Static catalogs use centered coordinate keys but decode to the same block-aligned runtime boxes. Ordinary entries store the cube center. Hideyho stores the upper cube center of its two-block box. Older integer/local schemas migrate without affecting rendering. Bundled catalogs are authoritative; optional local static JSON files are research overlays and need not exist.

`Save Learned Locations` is opt-in for any stable party size and records only conservative Hideonfloor block centers after repeated stationary observations. `Show Learned Candidates` renders only local Hideonfloor positions absent from the bundled entity catalog. Stationary objective catalogs are bundled-only. Candidate data always requires in-game review before being baked into assets.

## Sparkling and party behavior

`/sparkling` opens the complete Sparkling UI. `SparklingStats` persists per-species counts, Rainbow Feathers, and the imported duplicate baseline. Tracked duplicate catches advance both duplicate totals and their comparison baseline; unique catches do not.

The expected roster remains fixed for the visit. A party catch becomes newly shared only when everyone who needed that unique received it; cached ownership may prove that an absent original member already owned it. A newly recorded Sparkling delays API-import comparison until a response at least ten minutes later so stale profile data cannot trigger a false import prompt.

Public builds provide manual Shared/Missing controls. The ignored private provider may add automatic party collection and player lookup through the API; the public UI and fallback behavior must remain complete without it.

Manual profile lookups have a ten-second request cooldown and a five-minute result cache. Automatic party caching does not consume the manual cooldown. Screen-facing provider state must be lock-free; opening a UI may never wait on an HTTP worker or rate-limit sleep.

Party Sync is public, opt-in, and party-chat-backed. Its transient setting resets off each launch. A client sends one compact visible verification token only after the complete Safari roster is present and stable; the token is derived from its displayed username, the current lobby ID, and its action. Parsed objective traffic remains disabled until every current member has confirmed the same protocol. Local state is tracked before confirmation, then sent as a coalesced authoritative snapshot with batched confirmed hives. Disabling sync during an active synchronized run sends the matching sender/lobby-bound shutdown token, immediately stopping transport for every client while preserving local state. Departures retain confirmed remaining members, while a newly added member requires a fresh readiness check on the next stable Safari visit. Solo has complete local state without sending messages. Any future remote transport remains deferred and must preserve the documented privacy design: short-lived end-to-end-encrypted rooms, no credentials or private account data, and no developer/user access to connection metadata beyond what a trusted provider must process.

Chat-message hiding is display-only. Its finalized bit mask is serialized as `hiddenChatMessageGroups`, defaults to zero, and is grouped into General, Cavern, Icy, Haunted, and Forest columns. Selected Safari lines must pass through the complete tracker pipeline before `ALLOW_GAME` rejects them; never move the filter ahead of parsing or suppress player-written chat by keyword.

## UI and performance

`SafariConfig` fields annotated with `@Expose` are persistent keys. Rename them only with `@SerializedName` aliases or explicit `ConfigManager` migration. Deliberately transient session settings such as Party Sync must remain unexposed and be reset explicitly on load. Existing run history and settings must remain forward-compatible.

Use `ResponsiveUI` for fixed logical canvases and convert mouse coordinates for scaled widgets. `HudBox` is the source of truth for live/editor positioning. The HUD editor keeps outlines one pixel inside each screen edge and supports unsnapped one-pixel arrow adjustments.

The Contest HUD's Show Everywhere option means all SkyBlock locations, including Dungeons and Kuudra. Show Outside SkyBlock is separate. SkyBlock detection uses the sidebar title and must not require an Area row.

Performance rules:

- `RainbowColours` supplies one frame-limited clock; `UIDraw` and `SpecialTheme` cache bounded geometry/text by phase and position
- Chat rainbow components are immutable snapshots, not animated retained messages
- `HudPanel` caches measured layouts and icon quads
- `RunHistory` revisions invalidate dashboard caches only when history changes
- `BazaarPrices` values runs once per price snapshot/source and totals once per history revision
- `Markers` builds one marker list per tick/config revision
- `WaypointRenderer` batches geometry, culls off-screen markers, and bounds label caches
- `WorldEntities` provides one entity snapshot per tick; `CritterEntities` scans once every five ticks and spatially indexes pairing
- Operational logging is asynchronous, bounded, deduplicated, and rolled at 1 MiB
- Diagnostic logging is opt-in, buffered, and must aggregate high-volume packet streams

Keep live entity interpolation at frame frequency. Safe Mode evidence, depth behavior, marker eligibility, and exact labels are logic constraints, not optimization opportunities.

## Persistence and public boundary

Normal installations keep:

```text
config/safariutils/
├── safariutils.json
├── safariutils-runs.json
├── safariutils-sparkling.json
└── logs/
    ├── safariutils.log
    └── safariutils.previous.log
```

The optional static-entities JSON appears only when Hideonfloor research saves a new candidate. Settings, history, and research data use atomic replacement. Diagnostic and Party Sync settings reset each launch.

Public jars may contain only the public party-sync service. They must contain no private API classes or services, saved owner identities or UUIDs, API keys, or generated key payloads. Public documentation and release notes must not advertise private-only API behavior. Never commit `private-api/`, key files, generated private sources, user configuration/history/logs, or built jars.

## Release checklist

1. Run `git diff --check` and validate JSON/resources
2. Build Safe, Extra, and private jars for every supported profile
3. Inspect jar names, metadata, class lists, services, and public-key absence
4. Test lifecycle, Starting Items, objectives, mode/settings toggles, HUD editing, Sparkling UI, and Contest visibility
5. Synchronize README, CHANGELOG, RELEASE_NOTES, and this handoff
6. Push or publish only after explicit user approval
