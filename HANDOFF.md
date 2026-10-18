# SafariUtils developer handoff

This document records the invariants needed to maintain the current codebase. User-facing features, installation, and commands belong in [README.md](README.md); release history belongs in [CHANGELOG.md](CHANGELOG.md).

Workspace layout and verified build commands: [development workflow](docs/development/WORKSPACE.md).

Current overhaul progress and outstanding validation gates: [v3 implementation ledger](IMPLEMENTATION_V3.md).
Settings imports preserve both `original.json` (unaltered disk bytes, when present) and
`settings.json` (current working settings) under a unique `backups/settings/settings-import-*`
directory before replacement. Protected-source working snapshots are recovery data, not an
automatic replacement for newer-schema originals. Transient deadlines use `MonotonicTime`;
never serialize those process-relative values as saved dates.

Capture reconciliation samples immutable body/capsule/label observations before applying
identity decisions. Label fallback must consider the complete observed-body batch: a label
moving to a newcomer cannot replace its prior owner's identity while that body remains present.
Hideons have both concealed shulker and awakened silverfish forms; Duplicos use interaction
and silverfish bodies. Waking/settling is not capture evidence and does not increment pity.
An active attempt's body-form replacement without an observed absence remains ATTEMPTING,
not BREAKOUT. `IDENTITY/BODY-FORM` distinguishes it from a returning body; otherwise a valid
later catch can be excluded by the outcome selector and leave an orphaned recatch pin.
When separate Hideon labels simultaneously pair with a shulker and silverfish at the exact
same position, `CritterScan` publishes only the shulker representation. Standalone mobile
forms and nearby distinct peers remain eligible. Collapse this compound representation before
identity/pity and marker consumers, never by hiding one marker after splitting its identity.
A previously body-owned label becoming bodyless must not be assigned as a new capsule label
to another attempt. Mobile concealed-species carriers are rendered live, never remembered as
additional stationary spawns. `IDENTITY/LABEL-FORM` reports same-label body substitutions.
A guaranteed retry may acquire its first delayed body within the existing short form-rekey
window and distance; this is not a breakout. Retire any unowned prior breakout hold during
that identity handoff, preserving other attempts' ownership and the accumulated pity.
Resolved body-form and same-label replacements must also notify `CaptureEffects.bodyReplaced`:
retire the unowned old breakout/remembered projection even when there is no active attempt.
Do not clear another attempt's owned body or infer retirement from proximity in the renderer.
Deferred attempts retain the original attempt's camera ray; later camera movement is not
targeting evidence for an earlier throw. Diagnostic replay runs separately from HTTP workers
and must report skipped/omitted/corrupt records as incomplete rather than silently passing.
Developer `ci` journals replay the production capture tracker through the offline `replayCaptures`
task. `CaptureEffects` owns the projection boundary; only the headless harness substitutes inert
effects. The harness never ships in jars. This is model replay, not complete entity-scan/render replay.
Confirmed species completion belongs to CritterState; callers must not independently clear each
projection before settling the authoritative attempts. Preserve partial-total behavior and idempotence.

## Project and builds

SafariUtils is a client-side Fabric mod for Hypixel SkyBlock's Critter Safari. It targets Java 25, Fabric Loader 0.19+, and the Minecraft profiles under `gradle/versions/`.

- Shared code: `src/main/java`
- Profile compatibility code: `src/<profile>/java`
- Shared Ticket Trading: `src/main/java/dev/serko/safariutils/client/TicketTrading*.java`
- Shared key-free API client: `src/main/java/dev/serko/safariutils/backend/`
- Developer-only simulator: `src/developer/`
- Safe build: `./gradlew build`
- Extra build: `./gradlew build -PextraBuild=true`
- Other profile: add `-PminecraftProfile=<profile>`
- Private edition is retired for v3; `-PincludePrivateApi=true` is rejected
- Developer build: add `-PdeveloperBuild=true` (Testing UI, commands, and automatic diagnostics)

Build type alone controls the Safe Mode and Developer settings categories. Ordinary
settings remain visible regardless of the configuration class that owns their persisted
field. Safe builds force Safe Mode; Extra adds configurable Safe Mode options; all editions
include the key-free API provider. Developer adds diagnostics and the command-free Ticket Trading simulator.

SafariUtils diagnostic files and their controls are Developer-only. Release variants
still report failures to Minecraft's standard log without starting the custom writer.

Build variants use separate output directories. `DeployBuilds` verifies and deploys the 26.1.2 and 26.2 Developer jars, then mirrors SafariUtils configuration from the 26.2 main instance to the 26.2 Ticket Collecting instance. `BuildArtifacts` verifies all six profile/variant combinations sequentially and synchronizes only those current jars directly into the flat `release-artifacts/` folder.

Artifact names place the variant after the version: no suffix for normal Safe Mode,
`-extra` or `-developer`. Clean-build all six profile/variant
combinations before copying release artifacts so deleted classes cannot survive an incremental build.

`check` includes headless regression checks and jar edition-boundary verification. Shared
scripts under `gradle/` are also applied by SerkoAddons against its live SafariUtils source root.
Standalone packaging/deployment orchestration lives in `gradle/distribution.gradle`.
Routine verification is incremental; use `-PcleanArtifacts=true` for an explicit clean matrix.
Both jars include `safariutils-provenance.properties`; the source digest identifies selected
SafariUtils source/resources and is logged at startup. It contains no machine paths or secrets.
Ordinary builds do not rewrite the checked-in component snapshot; `SyncSafariComponents` is explicit.

At the v3 release checkpoint, current-client settings, saved data, lookups,
solo transitions, and grouped/distant captures have received in-game testing. The October 14
22H-18M journal strictly replayed all 4,855 capture inputs. Developer two-client sync and broader
Bridge runtime testing remain separate limitations; see `docs/development/V3_VALIDATION.md`.

## Runtime and lifecycle

`SafariUtils.onInitializeClient` owns registration. Each client tick deliberately follows this order:

1. Cache location, scoreboard, and tab-list state
2. Update menus and party observers
3. Update Sparkling state and optional providers
4. Update starting inventory and objective state, then publish optional shared snapshots
5. Scan entities once and update entity-dependent trackers
6. Update session state, markers, catalogs, prices, chat, and configuration

All server-derived tracking, rendering, chat processing, packet handling, and automatic
commands are gated by an active `hypixel.net` connection. Automatic party-roster commands
are narrower still and run only after SkyBlock or Safari is identified. Leaving Hypixel
clears pending commands and transient alert state. The Contest HUD's Show Everywhere option
may render its local timer in any world or server, but contest-score parsing remains Hypixel-only.

Reuse `SafariLocation`, `WorldEntities`, `CritterEntities`, and existing tracker caches. Do not add independent scoreboard, tab-list, or world-wide entity scans.

Entering Safari creates a transient visit immediately. Scouting, objective observation, and optional synchronization may run before ticket use, but the visit is not saved as a run. A run starts only when one or more normal Critter Capsules appear in inventory, proving that the server accepted a ticket. Manager interactions are hints, not activation requirements. Catches, drops, essence changes, and feathers never activate a run.

`StartingItemsWatch` owns activation. It waits 250 ms after capsule allocation before freezing one complete inventory snapshot so later drops and inventory movement cannot change Starting Items. Before activation, the Progress HUD may show attendance, the conservative `Join` countdown, Sparkling detections, and detected Hideonfloor waypoints, but not run timing or statistics.

The countdown begins at the destination play-connection event and uses the server's 30-second ticket window. Queue or party-entry chat is diagnostic context rather than elapsed ticket time; scoreboard, area, and lobby-ID state can arrive several seconds late. The connection anchor also covers late party warps without a local queue line. It displays `Closing` at zero, then `Closed` once the Manager's lockout dialogue begins. Developer `JoinWindowDiagnostics` compares every anchor without sending or modifying network traffic.

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

Safe Mode exposes visible or otherwise player-observable evidence. Extra Mode may expose additional internal detections. Its feature toggles apply independently; the former master switch is retained only as a one-time configuration migration source. Presentation settings never discard tracker state: switching modes or toggles mid-run must immediately render the appropriate already-known subset. Caches that affect presentation include the configuration revision.

Validated party-sync facts count as logically known information in Safe Mode. This grants
only the facts received: a species discovery does not supply an entity identity or position,
so it must not authorize guessing a hidden body or creating a location marker. Local discovery
still follows the sender's Safe Mode policy. Authentication/context/payload checks remain mandatory.

- Bee Nests clear after a left/right interaction is followed within five seconds by either a new nearby Honeybug or Hypixel's empty-hive confirmation
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

All editions include the shared key-free backend provider for automatic party collection and player lookup, alongside manual Shared/Missing controls. Provider failure must preserve local functionality and cached data. The approved Hypixel key stays exclusively in the Worker secret, never in a client artifact.

Manual profile lookups have a ten-second request cooldown and a five-minute result cache. Automatic party caching does not consume the manual cooldown. Screen-facing provider state must be lock-free; opening a UI may never wait on an HTTP worker or rate-limit sleep.

Automatic shared Sparkling loading retains the first fresh complete central party roster
for the visit, including confirmation before ticket use, and starts once the ticket activates
the run. Ticket Trading host qualification, invited guests, warps, and the ticket-window
deadline must not delay it or replace that composition. Each visit selects once; existing
composition/player caches remain reusable and there is no periodic reload.

Local party objective tracking is shared. Authenticated remote Party Sync is shared by all editions and engages automatically for applicable run/lobby context; it does not require party-chat verification messages or an enable toggle. PartySyncNetworks isolates the optional provider. Connection loss preserves local evidence; lifecycle exit disconnects transport. Incoming data must pass sender, context, sequence and payload validation before changing shared state. Do not describe transport privacy as an absolute guarantee or expose authentication credentials in diagnostics.

Transport may connect during the pre-ticket visit; objective inventory/run activation remain
ticket-gated. `PartySparklingState` owns visit-scoped monotone found/caught species masks;
only locally observed facts are sent as type `S` snapshots (four bounded integer words).
Received facts never echo into transport or chat. Terminal catches dominate stale discoveries.
The relay retains/merges snapshots for late arrivals within the same exact roster/lobby group.
`PartySparklingDiscoveries` drives one persistent reminder for any available Sparkling while
the local player has not used a ticket and the join window is open, independently of trading.
It stops after ticket use, catch, window expiry or visit exit. Ordinary optional chat alerts
remain human-readable; the old `SUT1` chat protocol is removed. Trading reads this discovery
store directly; its existing invite/warp deadlines and co-host protections are unchanged.

Ticket Trading is public, opt-in, and resets off each launch. The shared implementation owns
 trusted profiles, ordered invite and receive-invite lists, timed party commands, and Sparkling filters.
Host automation requires a fresh authoritative roster proving
local leadership; confirmed solo guests accept directly, confirmed party guests leave before
accepting Sparkling invites, and unknown party state retries only after Hypixel proves a party
conflict. Failed server joins suspend guest acceptance for 45 seconds. The command-free simulator
and randomized invariant audit compile only into Developer jars from `src/developer/`.

Player names shown by the mod use authoritative capitalization from the live player list,
backend lookup results, or the cached unauthenticated Minecraft profile lookup. Rank colors
come from the centralized identity cache, live observations or the shared backend provider; unknown colors fall back
to white while a confirmed no-rank player remains gray. The UUID-backed owner style remains
rainbow. Profile requests run off-thread, deduplicate in-flight names, and back off after
failures; rendering never waits on network I/O.

Chat-message hiding is display-only. Its stable bit mask is serialized as `hiddenChatMessageGroups`, defaults to zero, and is grouped into General, Cavern, Icy, Haunted, and Forest columns. Visible order may differ from persisted bit order through `SettingMultiChoice.bits`; never shift existing persisted meanings just to insert an option. Selected Safari lines must pass through the complete tracker pipeline before `ALLOW_GAME` rejects them; never move the filter ahead of parsing or suppress player-written chat by keyword. The NPC option dynamically captures each Shard Trader's offered species, requested item, and server-provided colors. It retains the original clickable selector components and adds a non-clickable `Trade: cost -> shard` line through `MODIFY_GAME`; successful receipt lines remain visible and add the shard to run income exactly once.

## UI and performance

Shared UI implementation rules and validation: [settings presentation](docs/development/SETTINGS_UI.md).
`SettingsMotion` is screen-owned and bounded; its animation is presentation-only. Menu particles
cover the full menu in one shared batch, and menu motion is always enabled without changing gameplay or HUD behavior.
Settings categories separate HUD/theme, runs/tickets, markers, alerts, Sparkling and visual comfort.
Redundant single-root tab accordions are flattened; other groups remain expandable and diagnostic hitboxes are static. Section state and per-tab scroll are
retained when navigating; search context headers jump to and highlight the original control.

`SafariConfig` fields annotated with `@Expose` are persistent keys. Rename them only with `@SerializedName` aliases or explicit `ConfigManager` migration. Deliberately transient session state must remain unexposed and be reset explicitly on load. Existing run history and settings must remain forward-compatible.

`SafariSettingsScreen` presents one compact category sidebar plus a fixed wrapping tab header and collapsed, nested section accordions over the unchanged `SafariConfig` schema. Each logical section shares a card; collections of distinct waypoint features split into one toggle-and-color card per feature. Search follows the same area/tab/page/card order, preserves relevant shared cards, and appends feature names to broad section breadcrumbs. Changing tabs preserves scroll and section state; explicitly collapsing a large parent collapses its descendants. Areas, tabs, and sections may reorganize controls across their source config classes, but they must retain the original owner and reflected `Field`; never move persisted fields merely to change navigation. Global search, tab reset, build visibility, warnings, and custom editors all reuse those same controls. Safe Mode controls follow the build variant, and Developer exists only in developer builds. Clicking the SafariUtils title opens the repeatable constellation Easter egg; completing it temporarily themes all SafariUtils UI and permanently unlocks the Astral special-theme choice.

Display's Safari tab owns presentation-only chat filtering and screen-effect removal. Gameplay's Safari tab owns Hideyho convenience, ticket protection/trading, and Party Sync; Profit remains separate. These are navigation groupings only, and persisted fields remain in their existing config owners.

Responsive screens reflow before they scale. Settings narrows its single navigation rail, wraps meaningful tabs, stacks controls below descriptions, and caches static text layouts by reflected field and available width; Sparkling collection, party, and lookup views switch from four biome columns to two. Dense dashboard tables may use content-measured fallback scaling only when their real minimum width or height does not fit. Keep scrolling content clipped below fixed headers and off-screen rows culled without removing them from tab reset coverage.

UI centering always means optical/geometric centering of the visible pixels. Do not include glyph advance padding, transparent space, or text shadows when calculating centered positions; draw shadows only after the primary shape is centered.

The settings footer shows the result of one asynchronous GitHub release check per launch. Release notes are wrapped and cached only when their source or width changes. Update links open the SafariUtils website; public download links should continue pointing to GitHub release assets. Private builds are retired for v3; removing old hosted private artifacts is separate release work. Data Tools performs compact versioned settings transfer and on-demand run-history checks. Imports and repairs validate first and preserve original settings in typed backup directories before replacement.

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
- Operational logging is asynchronous, bounded, deduplicated, and rolled at 4 MiB
- Developer diagnostics are automatic and asynchronous; keep snapshots change-only and aggregate high-volume packet streams

Keep live entity interpolation at frame frequency. Safe Mode evidence, depth behavior, marker eligibility, and exact labels are logic constraints, not optimization opportunities.

## Persistence and public boundary

Normal installations keep:

```text
config/safariutils/
├── safariutils.json
├── safariutils-runs.json
├── safariutils-sparkling.json
├── backups/
│   ├── runs/
│   └── settings/
└── logs/
    ├── SafariUtils_<timestamp>_events.jsonl.gz
    └── SafariUtils_<timestamp>_P2_events.jsonl.gz
```

The optional static-entities JSON appears only when Hideonfloor research saves a new candidate. Settings, history, and research data use protected atomic replacement. Remote Party Sync has no persisted connection state; it reconnects only in eligible context. Diagnostic segments are compressed JSONL, not separate human-readable log files.

Public jars include Ticket Trading, authenticated party networking and the key-free API provider, but not Developer tools, offline regression harnesses. No build may contain API secrets, user configuration/history/logs or generated key payloads. Never commit `private-api/`, key files, generated private sources, user data, or built jars. Artifact verification enforces edition and regression-class boundaries.

## Release checklist

1. Run `git diff --check` and validate JSON/resources
2. Clean-build Safe, Extra, and Developer jars for every supported profile, then verify integrated SerkoAddons parity
3. Inspect jar names, metadata, class lists, services, and public-key absence
4. Test lifecycle, Starting Items, objectives, mode/settings toggles, HUD editing, Sparkling UI, and Contest visibility
5. Keep release notes concise, use only non-empty New, Changes & Additions, Fixes, and Downloads sections, and preserve the established Downloads wording unless the user changes it
6. Synchronize README, CHANGELOG, RELEASE_NOTES, and this handoff
7. Push or publish only after explicit user approval
