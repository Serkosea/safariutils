# SafariUtils developer handoff

## UUID service sessions (2026-10-22)

The user explicitly chose compatibility over verified Minecraft identity. Updated clients
POST only their UUID to /v1/auth/uuid/session; no profile keys, Minecraft credentials, join,
hasJoined, or external verification host. Signed service tokens prove service issuance, NOT
ownership of the claimed UUID. Anyone can claim another UUID; party-sync roster/lobby checks
isolate honest clients but cannot prevent intentional impersonation or fabricated updates.
Never use this identity for secrets, private account data, payments, or privileged actions.
Old backend authentication routes remain solely for installed-client compatibility and are
not called by updated clients. They must never become prerequisites for UUID sessions.
Existing shared API caches, service tokens, and HMAC subject budgets are preserved.
The local backend now lives in the sibling `SerkoAddons/backend/`; website source is
`SerkoAddons/website/`. See that repository's root WORKSPACE.md. Earlier cloudflare-api
paths below are historical, not the current service workspace.
Do not promise availability for every user or a hard billing cap: network failures and
resource budgets remain possible. See cloudflare-api/SECURITY.md in the local backend workspace.

This document records the invariants needed to maintain the current codebase. User-facing features, installation, and commands belong in [README.md](README.md); release history belongs in [CHANGELOG.md](CHANGELOG.md).

RemoteSession is the one worker-only authentication owner for lookups, market and Party Sync.
It owns UUID service session renewal for every consumer. Refreshing tokens does not reset
UUID-derived budgets, but changing/impersonating UUIDs is possible. Global admission budgets,
per-IP regional gates, and global socket/Hypixel caps must remain independent of claimed UUIDs.
Never read Minecraft access tokens or profile keys for this service. No credentials are sent
to our backend. Preserve no-redirect HTTP and bounded parsing for service tokens.
The client caches session TTL using monotonic time, suppresses repeated failures for 30 seconds,
and refuses publication after account/connection changes. Live login/warp testing is still required.

AuctionMarketClient is a demand-driven shared service on NetworkRuntime and RemoteSession.
It consumes only the authenticated backend's compact official-Hypixel snapshot; never scan
auction pages on clients or add third-party price feeds. Quotes are lowest active BIN
estimates, not sale guarantees. Fresh-loot armor keys include exact visible star tiers
(`_N_STAR`, no suffix for zero); ascended/recombined/enchanted variants are excluded.
Missing quotes remain Optional.empty and snapshots expire after 30 minutes. No shared
service depends on IQ; the private consumer owns its valuation adapter and incomplete-total notice.

Workspace layout and verified build commands: [development workflow](docs/development/WORKSPACE.md).

Sparkling API imports are additive: only missing ownership or a higher duplicate aggregate
is merged automatically, and the ledger never lowers either known duplicate total on import.
Ticketless visit exit clears Sparkling/watch/reminder state just as a ticketed run does;
Sparkling detection and HUD theme presentation require the current Safari visit.
Ticket-trading cleanup waits 1.1 seconds after exit, through the command coordinator.
All automatic command categories share a one-second minimum gap, including after observed
manual commands. Manual sends are never intercepted or delayed. Guest invite actions remain
pending across cooldowns with a bounded expiry; leave/accept keeps its existing settle delay.
Co-host cleanup accepts roster changes caused by its own kicks, but cancels on other changes.
Sparkling parties may warp before 27 seconds only when invited guests have joined or
become unavailable and no remaining eligible queue entry fits; normal scouting keeps 27 seconds.

Current overhaul progress and outstanding validation gates: [v3 implementation ledger](IMPLEMENTATION_V3.md).
Settings imports preserve both `original.json` (unaltered disk bytes, when present) and
`settings.json` (current working settings) under a unique `backups/settings/settings-import-*`
directory before replacement. Protected-source working snapshots are recovery data, not an
automatic replacement for newer-schema originals. Transient deadlines use `MonotonicTime`;
never serialize those process-relative values as saved dates.

CritterScan reserves Duplico compound-body evidence before generic pairing:
an interaction-backed Duplico's nearby silverfish carrier cannot serve a different species'
farther label (including a capsule label). Closer independent labels remain eligible, as does
Duplico's silverfish fallback when no interaction is present. This is per-scan evidence, not
persisted proximity ownership or a renderer-only suppression.
Capture reconciliation samples immutable body/capsule/label observations before applying
identity decisions. Label fallback must consider the complete observed-body batch: a label
moving to a newcomer cannot replace its prior owner's identity while that body remains present.
Hideons have both concealed shulker and awakened silverfish forms; Duplicos use interaction
and silverfish bodies. Waking/settling is not capture evidence and does not increment pity.
An active attempt's body-form replacement without an observed absence remains ATTEMPTING,
not BREAKOUT. `IDENTITY/BODY-FORM` distinguishes it from a returning body; otherwise a valid
later catch can be excluded by the outcome selector and leave an orphaned recatch pin.
When separate Hideon labels simultaneously pair with a shulker and silverfish at the exact
same position, `CritterScan` publishes only the shulker representation. A fresh offset carrier
inside the shell footprint at the same base height also collapses when that relationship is
mutually unique. Previously tracked mobile peers and ambiguous overlaps stay independent.
This handles block-snapped breakout shells without assigning pity to a duplicate carrier. Standalone mobile
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
Resolved Hideon breakout identities remain eligible for a tight spatial body-form handoff
throughout the visit, including when the observer leaves detection range. Delayed handoffs
require one eligible identity and one fresh body within the form radius; ambiguous clusters
remain unmatched. Other species retain the short form-rekey deadline. FORM-REKEY logs include
the observation gap; marker retirement still flows through CaptureEffects.bodyReplaced.
Guaranteed presentation cannot infer a sole target by excluding stale/out-of-range uncaught
identities. Freshness may rank provisional targets, but known peers require physical capture
evidence before shrinking. PRESENTATION-WAIT records this once at attempt creation; the
existing PRESENTATION/TARGET-RETARGET records identify the subsequent evidence and owner.
Deferred attempts retain the original attempt's camera ray; later camera movement is not
targeting evidence for an earlier throw. Diagnostic replay runs separately from HTTP workers
and must report skipped/omitted/corrupt records as incomplete rather than silently passing.

Capsules can be observed in flight before ATTEMPT chat. Older unassigned paths require a
fresh outside-to-inside contact sample within the bounded flight window; stationary or stale
segments must never be reused as retry evidence. Existing exclusive ownership and overlapping
peer checks still apply. CAPSULE diagnostics include flightMs/preImpact on assignment only.

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
Bridge runtime testing remain separate limitations. Review and validation reports are local-only.

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

Ticket Trading is public and opt-in, defaults off, and retains its enabled preference across launches.
Only the preference persists; pending commands and run state remain session-local. The shared implementation owns
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

Haunted darkness removal affects only local fog applicability, never status-effect storage
or packets. Other mods may return a null fog effect to disable an environment (NoammAddons
does this for No Blindness); DarknessFilter must exclude that environment before dereferencing
the holder or consulting client/settings state, including when our own option is disabled.

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
- `BazaarMarket` owns public Bazaar transport, a bounded immutable all-product snapshot,
  single-flight admission, shared five-minute success/failure caching and 429 backoff.
  Consumers project their own valuation semantics without owning duplicate HTTP requests.
  First-book prices and best-book prices are separate fields; do not interchange them.
  Shutdown rejects new demand and late publication through the shared network lifecycle.
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

## Emerald Dye and collection intervals

Rare drops use canonical Hypixel item IDs (`DYE_EMERALD`) in RunRecord. Only the current player's exact found announcement records a dye. Acquisition estimates are separate from current market valuation.
Sparkling API merges are automatic and additive. Runs-since intervals depend only on RunHistory revision and recorded catches; API imports and manual collection edits cannot invalidate them. Modern records explicitly retain newly collected species; legacy records infer uniques from first appearances in saved history. Explicit duplicate-only records never imply a unique in partial history. Without a qualifying recorded catch the corresponding interval is Unknown. Old undated API timestamp fields are retained for persistence compatibility but have no runtime effect.
The v2 auction contract carries source, sample count, confidence and expiration per quote. Missing prices remain unavailable, never a fabricated zero-valued quote. Historical values are estimates before auction fees.

## Strict Personal Bests

PersonalBestTracker consumes only confirmed catch events in SessionManager. Rule version 2 assigns each species to its first observed catcher (case-insensitive name). A biome split freezes the distinct contributors to its required species; ALL uses the union across all 37. Solo/Duo/Trio/Squad means 1/2/3/4 contributors, never attendance. Duplicate catches, including after a split, cannot change contributors. Personal/Team and Changing/Unknown have been removed. Rule-one attendance records lack attribution and cannot be converted to v2 PBs.

The clock is monotonic from ticket activation. Pre-ticket catches or developer reset sessions cannot qualify exact timing. First local biome entry is optional supporting information, not the main clock. No API, undated remote snapshot, RNG exhaustion, marker state or logging dependency can create a completion. Saved splits include rule version, target, exact contributors, elapsed time, timing confidence, entry duration, and preceding best. PersonalBests is a revision-cached RunHistory projection, never another saved ledger.

PB/START and PB/SPLIT log decisions only when diagnostics are enabled. Comparisons share one formatter/palette: faster green, equal cyan, under 5% slower yellow, under 15% orange, under 30% red, otherwise dark red. Preserve these semantic colors under special themes. Details opened from history return to history; from a PB card return to PBs; from Current Run return to History. Public and integrated builds share the same source.

The dashboard has History, Stats and Personal Bests navigation. A selected RunRecord is a detail subview of its origin, never another main tab. Current Run is available from History and refreshes a temporary snapshot once per second only while open. New RunRecords retain playerBiomeUniques; null means old data without attribution. Never infer other players' catches from the shared aggregate. Details use the same RunRecord path for saved/PB/live selections and existing market services for valuation.

Run detail presentation has Information/Personal Bests sections sharing a cached selected record, origin-aware Back, and a one-second live snapshot. Legacy per-player totals use unambiguous solo/duo attribution and retain own totals for larger groups; this is display-only and never rewrites history or qualifies a PB. Settings schema 6 changes only the former default History orange to green, preserving custom colors.

New run records persist playerCatches (player -> species -> successful catch count) from existing session evidence, including duplicates with case-insensitive player keys. Unique biome totals derive from this snapshot; old playerBiomeUniques remains readable. Legacy solo/duo totals are inferred where the recorded roster makes attribution unambiguous; larger/unknown rosters retain own totals and flag missing teammate attribution. No historical inference feeds PB eligibility.

PB cards show title/clear count and time; selected splits use two lines (biome/group + elapsed, comparison + since-entry). StatColours centralizes date/time/coins/catches/PB colors. Objective inventory rows require known positive inventory in all fallback and sync paths. SparklingWatch keeps distinct body keys rather than species-wide singletons; CaptureEffects forwards exact body replacement and catch outcomes. Species-only remote catch masks do not identify which local individual was caught, so cannot blanket-delete local observations. Repeat Sparkling occurrences remain separate within one RunRecord.

StatColours separates semantic heading hues from softer value tints, with pale gold coins and dark grey reserved for missing collection state. Formatted summary Components use a bounded 128-entry client-thread cache; font metrics and special-theme output remain live. PB projections continue to invalidate only on RunHistory revision changes.

## Party clear and shared Sparkling corrections (2026-10-26)

PersonalBestTracker binds the local player at ticket activation. A completed target
only produces a split if that player supplied a first unique catch; completion is
frozen even when excluded so later duplicates cannot qualify it. PersonalBests
also filters persisted splits by run.self. Group size remains unique contributors,
not attendees. Shared Sparkling catch announcements bypass local capsule resolution:
SparklingWatch consumes a sole matching outstanding individual; ambiguous multiple
individuals remain intact with a diagnostic rather than deleting an arbitrary peer.
Local catches continue using exact CaptureEffects ownership.


### Personal biome-mob PBs (2026-10-26)

PersonalBestTracker also records MOB_GEMZIE, MOB_DOOMSPIRAL, MOB_WUMPA and MOB_MACAW targets using the existing version-2 Split and RunRecord pipeline. These are the first confirmed local-player catches of those species, timed from ticket use; process them before the party first-unique deduplication, since a teammate may catch that species first. Mob targets require exactly the local player as contributor, independent of attendance or biome-clear contributors. Incomplete timing remains ineligible. Never infer historical mob times from aggregate catch counts. PersonalBests uses the same revision-cached projection; mob records do not inflate biome clear counts. Each biome card adds a clickable personal mob row below Squad, and run Completions includes those splits. Unique Clear PBs retains the existing personalBestMessages setting; Biome Mob PBs uses biomeMobBestMessages with All Catches/PBs Only/Off. PB/SPLIT diagnostics cover both.


### Coin quota and PB presentation (2026-10-26)
ShiningCoinWatch separates acquisition statistics from the Missing HUD quota. The quota is settled shared-inventory coins (including cursor) plus exact server-confirmed personal Gimmiegold spawns; dropping a coin without a spawn removes it, picking it back up restores it, and catches do not decrement the quota. Inventory/spawn ordering settles for 750 ms using MonotonicTime; late spawn evidence remains accepted. Do not infer quota from raw label peaks, which include temporary capture labels. COINS diagnostics log only quota changes and confirmed spawns. COUNT/EVIDENCE logs label/body identifiers at new species peaks, and RUN/SAVE_TIMING separates snapshot, aggregate rebuild, and persistence timings only with automatic logging enabled.
ClientMessages owns the selectable display.clientMessagePrefix (normal default or SU), including themed rendering and picker previews. PB client wording is First Clear!, First Catch!, PB! -m:ss.SS, Tied PB?!, +m:ss.SS, Timing Error; personal mob notices have no group suffix. No change to PB timing or eligibility.


### v3.2.0 release validation (2026-10-26)
PB notices now send one prefixed line per milestone, with white parentheses around the semantic result. Moving recatch pins carry explicit snapshot ownership into per-frame projection using the same body interpolation as guaranteed shrinking; resolved breakout pins stay frozen, and marker deduplication includes body identity. Normal live markers already use entity partial-tick positions. Server corrections are not smoothed by speculative movement. The final test log replay verified 10,709 capture inputs; its only lifecycle-audit notice was the explicitly omitted pre-visit journal, with no run inputs skipped. End-of-run history serialization remains synchronous for durability: the measured run used ~14 ms rebuilding aggregates and ~70 ms saving 1,886 records, not sustained capture work.

### Collection progress rendering (v3.2.0 artifact refresh)
The animated collection progress overlay uses CollectionProgressGeometry and the shared GuiQuadBatchRenderState, rather than submitting hundreds of overlapping fill elements. Preserve primitive order, alpha, full-rate timing, and exact geometry. Each submitted frame owns its packed array (do not mutate arrays referenced by deferred GUI states). CollectionProgressRegression compares the old draw sequence against packed output across dimensions and animation times, including bounds and frame isolation. History/collection interval projections remain revision-cached. Published v3.2.0 notes were user-edited and must not be overwritten by artifact-only updates.

### Capture name visibility follow-up
Vanilla-name suppression follows ordinary marker captures as well as dedicated waypoint species. WaypointRenderer.captureTransitionReplacesName uses exact presentation ownership for paired live bodies and active species transitions only for bodyless/capsule-scaffold labels; it respects explicit vanilla-name modes and ordinary Sparkling-mode filtering. This is render-only: no entity names, ownership, pity or capture timing are changed. Regression coverage includes pin/shrink/native capture, untouched neighbors, missing bodies, scaffold labels, name modes, disabled markers and Sparkling filtering.
