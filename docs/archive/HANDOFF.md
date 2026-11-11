# SafariUtils developer handoff

## Pending party capture fixes (2026-11-04)

Saved run 05-11-23 V58 proves peer Areita capsule head (item_display149320)
was rediscovered beside CAPTURING149318 / Sparkling label149319, making the shared
catch ambiguous. Capture props are excluded from live-body/texture pairing, retained
by exact UUID until unload, while bodyless labels remain for nametag hiding and capture
tracking. Appearance-only item displays use the existing 500ms metadata settling gate.
Clarification: local discoveries retain data immediately, but ordinary alerts wait for
the initial atomic party snapshot. Previously shared finds use only shared tracking and
ticket reminders; genuinely new local finds can alert before or after ticket use. Local
S publication waits for initial snapshot consumption; published ownership distinguishes
own echoes from discoveries that predate our connection. Active local or shared
Sparklings bypass ticket protection. Debug connected names use the frozen
visit roster. Guest trading rechecks solo state and bounds leave/retry transactions.
Regression fixture includes the recorded positions plus a real same-species neighbour.
Final BuildAllArtifacts passed all nine standalone variants and SerkoAddons26.2, with
633 byte-identical shared classes. Deployed SHA256-verified jars to Skyblock26.2 and
Ticket Collecting. Saved run has no ci capture inputs, so full capture replay is unavailable;
the regression instead uses its recorded entity layout. Live teammate-catch retest remains.
Changes are pending a future release; do not replace 3.5.0 public assets automatically.


peer client 4.1.0 review: local/reviews/MUTUALS_4_1_REVIEW.md (local integration docs).
The document now contains ONLY the confirmed held-coin payload issue: its count
incorrectly depends on the starting-item chat toggle. peer controls his own alert UI;
our alert deduplication must preserve shared discovery facts and ticket reminders. Relevant sync
and detector classes are identical across all three supplied Minecraft builds.

## SafariUtils 3.5.0 release review (2026-11-03)

Latest 17H31M live session: 26,160 records, no warn/error or rejected sync decisions;
peer feeder 2 -> 1 -> 0 accepted; peer mound masks remained zero (sender not implemented).
Lease renewal reconnected in ~2.6s. Capture replay verifies all 15,343 inputs; structured
audit now excludes buffered perf/er epochs and pre-visit-only journal wait notices,
with tests preserving detection of genuine epoch regressions/mid-run omissions.
Hotspot cleanup keeps unresolved/solo membership from latching network fallback and
keeps frozen-visit ticket hello updates alive across temporary disbands. Leadership
claims require a complete live party within the visit roster.
Backend health and deployment verified; historical observability returns403 under current
OAuth scopes, so no claim of server-side historical test-window coverage.


## Party lifecycle replay update (2026-11-03)

Backend deployed as `752f7b58-9185-4218-ad82-bafd5c459dbf`. Normalizes mini+digits
to m before deriving lobby/member keys. Welcome advertises room-sparklings-v1; combined
found/caught snapshot precedes legacy per-reporter replay and is retained on all active
matching-roster peers. Updated clients use S_ROOM internally (not an outbound kind),
ignore legacy S when advertised, merge catches before alerts, and exclude their own
local discoveries from remote-first alert suppression. Phase guards stop transfer alerts.
Prior ticketed hello/display retention and final-feed zero fixes remain in place.
89 backend tests, real Worker WebSocket replay smoke, and the full BuildAllArtifacts matrix
passed (633 byte-identical shared classes). SerkoAddons deployed with verified SHA256
to Skyblock 26.2 and Ticket Collecting; applies on next launch.
Send peer docs/integrations/PARTY_SYNC_UPDATE_2026-11-03.md; updated mixed-mod retest
is still needed. No release/version bump or GitHub/website publication requested here.


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
Sparkling parties may warp before 26 seconds only when invited guests have joined or
become unavailable and no remaining eligible queue entry fits; normal scouting keeps 26 seconds. Every Run invites begin at 5 seconds from the same connection-window clock. Host ticket qualification retains its separate 20-second cutoff. Invite waves stop at 22.9 seconds to reserve the existing 3.1-second command cooldown before warp; the 30-second ticket connection window is unchanged. The Invite Players header persists Disband On/Off (default On). Off suppresses both disband and guest-kick cleanup and includes eligible configured baseline members in warp scheduling without reinviting or double-counting capacity. CommandCoordinator owns a monotonic 5.1-second party-warp cooldown shared with observed manual warps and preserved across world transfers; server cooldown feedback extends it, and rejected automated warps retry only before the 27-second deadline.

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


## Entity research diagnostics (2026-10-28)

`EntityResearchLog` is developer-only, opt-in evidence collection, not a prediction/ownership
service. Its bounded network-ID/UUID index, client load/unload events, sampled metadata,
floor component groups/rewards and Scrappy interactions join the existing capture replay.
Client observation order and single-click reward candidates are not server truth. Preserve
world/visit scope, explicit gaps and partial baselines. See DIAGNOSTICS.md for commands,
bounds and test procedure; tools/entity_research.py is an offline summary only.
No floor/Scrappy prediction or live pairing rule is established by this change. Future
floor prediction must be Extra-only; validated non-revealing pairing improvements are shared.
General structured decisions encode on the writer while retaining enqueue-time context.

Shared opt-in PerformanceDiagnostics profiles runtime frame intervals and fixed operation
boundaries for standalone and integrated components without a Safari-presence requirement.
Reuse begin/end (finally), OperationalLog wrappers or endEvent for fixed event types. Do not
create parallel timing writers, log per frame, use user data as stage names, or sum nested
inclusive scopes as CPU usage. See DIAGNOSTICS.md for coverage and interpretation limits.

Standing user preference (2026-10-28): when future work exposes a diagnostic/profiling gap,
add useful bounded evidence through the shared services without requiring a separate feature
request. Preserve opt-in behavior, privacy, standalone compatibility and observation-only
semantics. Do not add parallel writers or per-frame dumps; measure real gameplay before
claiming overhead is negligible. Partial frame windows report unavailable FPS, not zero.

## October 28 label and research validation

CritterScan.parseLabel is the exact shared label grammar for scanning and pre-scan
vanilla-name replacement. Sleeping `Wumpa zZz` is Wumpa, including Sparkling labels.
The renderer may evaluate a temporary bodyless sighting before the next shared scan;
this uses existing visibility/settings gates and never publishes identity or pairs bodies.
Keep spawn-time suppression independent of scan cadence rather than forcing world scans.
SavedRunLog normalizes trailing CR before writing platform newlines; report tools tolerate
blank lines in older exports. Floor XP chat uses `Hunting Experience`.
First entity-research run (October 28, 01H-59M segments) has 53 single-candidate floor
rewards and all three successful Scrappy foods. Quest items have ordered three-ID groups;
Scrappy bodies sorted by ID map to wheat/fish/lily. These are provisional observations,
not validated prediction rules. Require another independent run before enabling predictions.

Second October 28 research run confirms the quest-item grouping and Scrappy food ordering,
but coin/icebreaker group lengths vary. Do not assign every item by fixed global offsets.
Ordinary body/label numeric IDs often differ by +1; transformed Hideons differ and no
universal replacement rule is established. Capsule Duplico labels borrowed the original
interaction body's silverfish carrier (+403/+407/+427 label offsets in this run). Compound
reservation now compares label identity, not species, so same-species capsule labels cannot
claim a better-owned carrier. The real carrier fallback and closer independent peers remain.
Gem Pedestals use the shared marker registry and Gemzie's ordinary-hitbox visibility policy.
Their Always Active selection is persisted bit 8, displayed in Cavern via explicit bit mapping;
never shift the existing eight saved objective bits. Non-critter marker titles ignore the
critter Display Nametags setting by design.

User-supplied floor-only quest-item ranges and starting-inventory distinction are recorded
in docs/development/DIAGNOSTICS.md under Floor quest-item bounds. Use them as research
constraints, not proven ID mappings or inventory caps. Both initial runs fit these ranges.

Gem pedestal completion combines mode-eligible local evidence with the active party-sync placement mask. Held inventory is not completion. Completed pedestals stay hidden even with Always Active selected; normal visit reset restores the next run's markers.

## Safe Mode evidence audit (October 28)

Known fixed objective positions (including Gem Pedestals) may be shown through terrain;
location knowledge is separate from evidence of this run's occupancy/completion. Pedestal
completion uses placement messages, confirmed Gemzie-door/catch state, and active party-sync
placements and visibly confirmed gem blocks, never held inventory or unseen block scans.
The verified block signature and separate Extra observation path are described below.
Party-confirmed walls, nests, and mounds are known in Safe Mode as required by EvidencePolicy;
do not restore legacy local-sight-only restrictions on received completion. Remote completion
must not be echoed as local wall/mound evidence. All state remains scoped to the current visit.
Candidate inspection requires an actual unobstructed ray or a ray hitting the target block
itself. Near distance and view direction alone do not prove an area was inspected. Paintings
remain occluders, including the decorated-entity surface fallback. A catalog candidate must
also have a detected floor drop/present nest before it can become visually confirmed.

### Verified pedestal observation (third Oct 28 research run)
Placed gems are PLAYER_HEAD blocks one block above their matching froglight base. GemPedestals owns geometry, colors and signature. SafariObjectives samples only these three loaded positions on its existing five-tick cadence. Safe completion accepts visible, server or party evidence; unseen loaded observations affect Extra only and are excluded from locally published party completion. Evidence resets per visit. This replaces the earlier note that visual detection was pending. Debug command reports are now also recorded through opt-in DebugLog (16 KiB cap), including block snapshots.

Third run: 59 attributed floor rewards (29 quest items, 12 essence, 10 Hunting XP, 8 shards), no research gaps. Quest-ID order repeats the earlier two runs: Lime/Purple/Orange twice; three worms, three berries, three seeds; five incense; four coins; five icebreakers. Scrappy bodies 297/300/303 accepted wheat/fish/lily respectively. XP/essence/shard order remains mixed; no speculative prediction or ID-only pairing is enabled.

Shared settings controls (2026-10-28): SettingsUiKit.slider/colour and SettingsColourControls own the canonical slider, swatch and HSV/opacity editor visuals and interactions. SafariSettingsScreen and integrated component adapters reuse these; new settings surfaces should reuse them rather than copying a picker implementation. These classes must stay standalone-safe, with no SerkoAddons dependencies.

Standing maintenance direction (2026-10-28): reuse central state/services and shared settings
controls on every entry path, including settings opened outside a hub. Preserve functionality,
use bounded caches and inexpensive render paths, and include concise opt-in diagnostics and
meaningful tests for new behavior. These are ongoing requirements without repeated reminders.

Capture ownership corrections must update Attempt.anchor to the confirmed body together
with logical/entity ownership. Otherwise an offset capsule label plus stale provisional
anchor can reject a real breakout and leave an orphan pin (Gemzie October 29 trace).
Research evidence and approved future floor palette: docs/development/ENTITY_RESEARCH_FINDINGS.md.


Floor/Scrappy inference (2026-10-29): see docs/development/ENTITY_RESEARCH_FINDINGS.md.
FloorDropPattern is the only floor-ID classifier; FloorDrops supplies scan observations.
SafeMode.floorDropContents gates labels/colors/visibility before any renderer consumes
them. ScrappyFeeding keeps initial-food inference separate from confirmed feeding and
capture identities. Never publish either prediction as confirmed party objective data.
Schema 7 migrates biome-wide always-active floors to Loot + relevant quest categories.

## New Year contest clock (2026-10-29)
ContestSchedule owns the shared HUD/alert cycle. The first contest on Early Spring 1
starts scoring at an estimated 8:40 remaining, based on user observation; its original
end time and 20-minute cycle ID do not shift. Countdown includes this delay even from
the preceding inactive gap and when launching during the wait. SkyblockCalendar owns
the fixed epoch/calendar used here and by private calendar tooltips (no IQ dependency).
Opt-in CONTEST server-result records observe changed score rows even during the wait;
accepted score records include ticket state. No per-frame logging or network calls.
The exact annual scoring boundary still needs future live validation.

## Model glow projection (2026-10-29)
SafariGlows consumes MarkerRegistry and WaypointRenderer.modelGlowEligible without
publishing detections, capture identity or pity. It outlines Hideonwall, Hideonfloor,
Duplico and Bloodbat bodies plus exact-texture live Rockmite mound displays. Duplicate
or ambiguous model associations are rejected; capsule/player-head and cross-Hideon
models are excluded. Colors reuse critterHitboxColour/moundColour. Capture/recatch
ownership suppresses a live model glow. World changes clear bounded caches.
Render Chest 1.0.3+26.2 supplies depth-tested model outlines; no server entity flags
are changed. Existing vanilla/server glow is untouched. Normal and Extra editions
share this renderer and their existing marker eligibility/evidence rules. The 26.1.2
backend deliberately retains existing boxes only because upstream has no compatible
library; do not bundle the 26.2 renderer or alter its Minecraft dependency declaration.


## October 29 marker presentation checkpoint

Floor-drop schema 9 separates Loot/Quest toggles, preserving the old toggle on migration.
Five Icebreakers is preferred with evidence-driven four-drop fallback. Relevance stays
in SparklingMode.hideFloorDrop; marker outlines, faces and Missing HUD use the exposed
SafeMode-gated kind. Scrappy food presentation and server-acknowledged mound hit stages
are visit-local, never capture-identity evidence. See ENTITY_RESEARCH_FINDINGS.md for
seven-run fixtures and render ordering. Live tests must check model/glow occlusion,
labels, Safe Mode, and per-item objective hiding before the next release.


## Rendering follow-up (October 29, 14:57 test)

Do not restore early-solid marker rendering: it hides front edges. Normal feature
submission and late labels are restored. Depth-tested pipelines must inherit
DepthStencilState.DEFAULT.depthTest(), not hard-code LESS_THAN_OR_EQUAL: 26.2 is
reversed-Z, unlike 26.1.2. Extra floor faces explicitly use ALWAYS_PASS; Safe Mode
uses native depth. Faces derive their colors and submit one double-sided quad.
Visible Extra boxes use scene depth and hidden centers retain through-wall rendering,
with one cached terrain test per tick/cell. See research findings for the partially
concealed-box limitation. Missing HUD has an independent separate-type toggle.

## v3.3.0 release review
Latest Oct 29 16:49 launch: 59 recorded floor rewards matched predictions, no research gaps or warning/error records, focused biome averages about 240 FPS. Continue treating inference as presentation-only, never capture identity. New Year scoring boundary remains estimated. reference client 6.11.0 for 26.1.2 was inspected from the user-provided ZIP: custom glow needs a depth texture and hooks across eight render mixin classes; no untested port is included. Its standard markers remain supported.

## Hunting mode terminology (2026-10-31)

The Unique Mode tab and toggle retain the persisted `sparklingMode` key. Its first
Unique Mode Options control is Sparkling Mode, retaining `sparklingIgnoreUniques`:
it only applies while Unique Mode is enabled. Missing HUD uses the effective mode's
name. Always Active selections remain explicit overrides. The retired
Only Show Sparkling field and filtering branch are removed; old JSON values are
ignored. Do not reintroduce that restriction through migration or legacy reads.

## Profit relevance (2026-11-01)

`RunRelevance` derives ordinary species visibility from existing run catches,
party collections, user overrides and `ProfitableCritters`; it owns no catch or
PB state. Live, tracked and recatch markers use the same decisions. Wumpa goals
retain uncaught Icy prerequisites. Existing Safe Mode evidence gates remain at
the detector/render boundaries. A sighting is not a complete population survey:
never turn one seen/caught body into a completed Sparkling check for the species.

`alwaysShowProfitableCritters` defaults off and only applies in Unique/Sparkling
Mode. It retains top-10 estimated earners except Rockmite, without backfilling
the excluded slot. The main Missing HUD list shows a green `$` on every retained profitable species,
including species also needed for unique or Sparkling goals. Exact finished quotas retire optional rows unless a
live remainder is known. The unique-only HUD option remains respected for normal
entries. PB/completion totals still come only from the existing session tracker.

`/safari profit` (also `/su` and `/safariutils`) shares BazaarPrices/BazaarMarket's
single-flight and refresh budget. Ranking caches by Bazaar revision, selected
price side and estimate ticket; it expires after 30 minutes and rejects products
omitted from the latest snapshot. It is estimated revenue, not coins/minute or
net capsule profit. Uses CritterCatchEstimates (ordinary range midpoints, mechanic-specific RNG expectations), a 1.5025 shard
assumption and rarity essence with Basic/Economy/Premium/First Class multipliers.
`Profit Estimate Ticket` affects estimates only, never saved rewards. No new
auction endpoint, third-party pricing service, or per-frame sorting was added.

Feature provenance: ranking/marker idea and shard/essence assumptions reviewed
from peer client 3.6.4 (`ProfitableCritters`, `SafariEspRules`, `SparklingChecks`),
user-provided CC0 jars. Implementation is native to our shared services; no
upstream runtime, networking, configuration dependency or population table copied.

## Sparkling captures by other players (2026-11-01)

CritterCapsules owns positive item-display capsule recognition, shared by identity,
stationary-body tracking, and the published entity scan. Bodyless labels directly above
confirmed capsules are scaffolding even when there is no local throw. SparklingWatch
remembers those labels for the visit; they cannot create a find after the capsule vanishes,
but a real paired body remains eligible after a breakout. New bodyless discoveries wait
500ms for packet/metadata ordering; real bodies have no added delay. Particle matching
must reject the nearest capsule label rather than redirect particles onto a nearby peer.
Sparkling label-to-individual aliases are exact; never transfer outstanding ownership to
the nearest same-species individual. Shared catch announcements remain conservative when
multiple genuine same-species individuals are outstanding. All transient watch deadlines
use monotonic time. Live remote-catch verification remains necessary (reported without logs).

## Central run mode (2026-11-01)

RunMode is the sole effective mode decoder: FULL_CLEAR, UNIQUE,
and SPARKLING. The existing sparklingMode/sparklingIgnoreUniques saved fields and settings
toggles are a compatibility adapter; do not add a second stored/cached mode. Runtime readers
use RunMode or SparklingMode's delegating facade. Commands select explicitly and idempotently,
save once when changed, and invalidate marker presentation; they never reset run identity,
catch evidence, PBs, collection/party state, or personal HUD/marker/profit preferences.
/safari (and /su, /safariutils) fc/fullclear, unique/uniques, and sparkling select modes;
mode reports the current selection and also accepts the same subcommands. /sparkling still
opens the existing collection UI. Screen consolidation and ticket preferences are separate work.
The design comparison used local peer client 3.6.4 SafariFullClear/SafariHelperRules: explicit mode
selection is useful, but its mode-switch overwrites of display preferences and PB type are
intentionally not adopted. Our catch-based completions remain independent of visibility mode.

/safari standard (including /safari mode standard) was removed at the user's request;
Full Clear is the only name. /safari help lists public commands and aliases with safe
suggest-command clicks. Developer builds link /safari help debug for their diagnostic
commands; public help has no developer entries. Help mode aliases come from RunMode.

## Message templates (2026-11-01)

See docs/development/MESSAGE_TEMPLATES.md. AlertText owns single-pass event-time tag
expansion for banner/chat settings, with explicit event context only (no player/mode/time fallback).
Do not format again at queue-send time or per render frame; inserted values are literal.
ChatTemplateFormats owns the reversible opt-in peer client compatibility overrides;
only individually verified peer client formats belong there. Preserve send-channel choices,
custom settings, lifecycle/command cooldowns, Safe Mode gates and catch-based completions.

Profit catch estimates are separate from population bounds/quotas. Snoozle=5*50%;
Rockmite=4.125 provisionally from peer client 3.6.4 spreadsheet data (mound range unverified).
One-player starting inventory assumes two uniform rolls over nine quest items: coins
4.5+2/9; each bird feed 3+2/9. User-supplied feed probabilities determine bird expectations,
including two Macaws per successful roll. peer client coin values match that model exactly;
its bird spreadsheet uses different assumptions and is not copied. These are estimates,
not learned catch totals. Profit hover shows shard and essence run values separately.
Only the report heading receives the client prefix. No price poller or quota changed.

## Hotspot coordination and mode tickets (2026-11-01)

Safari Runs navigation is General, Tickets, Party, Visual, Profit. Tickets owns protection,
then the three per-mode choices in one shared card, then Ticket Trading. Party owns
hotspot assignment and peer client compatibility. Keep future ticket/coordination controls
in these tabs using the existing card/navigation model; persistent config keys stay unchanged.


HotspotAllocation is the pure bounded allocation policy; HotspotWatch integrates it with
current PartyState, monotonic visit timing and ChatQueue. Do not overwrite the actual
hotspot with assigned biome. peer client 4 allocation uses retained-hotspot fairness and UUID tie-breaks;
no backend, HTTP request or additional roster cache is introduced. See MESSAGE_TEMPLATES.

TicketSelection is opt-in independently for each RunMode (Manual by default). It checks
only the exact Critter Safari Entry menu, four recognized ticket tiers, positive lore,
current player/menu/world, carried-stack emptiness and shared TicketProtection. It waits
150ms for initial menu data, retries only reads every100ms up to5s, submits once, and
leaves manual control available. Manual input cancels automation; no higher-tier fallback.
Capsule allocation still activates runs. ClientCompat caches the verified cross-version
container input method; headless tests assert that API exists for each built profile.
Logging records decisions, not credentials. Validate actual server menu lore, party
assignment and mixed-mod ordering in-game before treating interoperability as proven.


## Unified stats navigation and lookup commands (2026-11-01)

DashboardNavigation owns the ordered Stats, History, Profit, Sparklings, Lookup,
and Personal Bests rail and its renderer. SafariDashboardScreen owns run/history/profit views;
SparklingScreen retains collection editing and lookup widgets as dashboard views, using
that same rail. /sparkling opens Sparklings; no Party tab or manual party-list editor remains.
/safari shared reports current known shared species without initiating a refresh; incomplete
or changed-party API state returns a not-loaded notice instead of an unverified list. Keep history detail Back routing and collection edits intact.

PlayerLookupCoordinator owns bounded recent results and shared in-flight admission for
screen and /safari lookup, tickets, refresh commands. All use SharedSparklingProviders;
no alternate HTTP path. Successful results populate full profiles even for ticket-only
reports. Completion is published on the client thread only for the originating account;
same-account warps retain recent results, while command output stays tied to its originating
connection. Shared reports reject mismatched current/provider rosters. Source timestamps and successful refresh times retain LookupHistory's
separate semantics. Refresh remains subject to provider cache and cooldown, not a bypass.

SessionProfitSummary is a read-only projection of runs started since JVM launch, plus the
active run. The active copy replaces any saved copy at the same start boundary. Only an
open Profit view rebuilds its snapshot, at most once a second; market work stays in BazaarPrices
and AuctionMarketClient. Values are estimated loot proceeds, not realized sales or net ticket
profit. No secondary reward store or automatic price-fetch loop is introduced. Tests cover
command reachability, navigation and border ordering, and active/archive overlap.


Dashboard refinements: Saved Sessions is a local-day grouping over existing RunHistory
(peer-client model), not invented old launch boundaries. SavedProfitSummary caches by history,
market revision and timezone; it includes partial-price flags and never stores duplicate runs.
Current Session remains launch-scoped. The profile record owns species-ID normalization;
never compare API IDs directly to display names. LookupPresentation owns ticket colors and
collection summary formatting for UI and commands. Hunting level is optional in the compact
backend response, calculated from selected-profile SKILL_HUNTING experience with the
peer client 3.6.4 progression. Old cached responses show Unknown until refreshed, not zero.
Backend hypixel/cache fields and tests were updated and deployed to the existing Worker.

Dashboard command presentation: lookup places Hunting on the profile heading only, splits Unique Sparklings (hover contains biome ownership) from totals/duplicates, and shares a total-first numeric ticket row with /safari tickets using white separators and tier-colored counts. Successful manual cached lookups move the freshest retained profile to the front of LookupHistory; newly opened Lookup views select that entry without a request. Profit switch text describes the active view. Collection/Lookup content and responsive canvas heights reclaim the removed heading row together.

Lookup formatting refinement: Hunting is command-only and uses decimal points. Dashboard ticket labels and amounts both retain tier colors. /safari lookup shows a gold Tickets total with named tier details on hover; /safari tickets keeps the full numeric breakdown inline. Command dividers share LookupPresentation.DIVIDER (two spaces on each side, white).

## Canyon Honeyhives and candle targeting (2026-11-01)
Honeyhives owns a bounded HoneyhiveState and publishes immutable catalog-marker
snapshots every 500 ms in Torrhus Canyon/Springs. Unchecked and ready hives use
one configurable color with distinct labels; cooling hives are hidden until expiry
or Queen Bee. HIVE! requires a recent interaction; clicks alone never start timers.
Queen Bee may precede/follow loot, and confirmation guards stale holograms for two
seconds. World changes discard pending click ownership, not cooldowns.
HoneyhiveStore atomically persists collection/refill wall-clock times by account UUID
and SkyBlock profile ID in safariutils-honeyhives.json. Runtime deadlines are monotonic.
Profile discovery uses at most two /profileid requests through CommandCoordinator;
initial discovery preserves pending harvest attribution. Profile changes isolate data.
Invalid/newer ledgers are preserved without writes. No external requests are used.
WaypointRenderer draws user-colored vanilla-textured hive beams (full-alpha core, softer halo) without a distance cutoff,
extending beyond the visible sky. WorldOverlayProjection preserves their perspective
while avoiding the vanilla far plane. Fixed catalog geometry needs no distant chunks;
only loaded nearby holograms are scanned, never during rendering.
CandleTargetMixin changes getShape only (not collision/reach), only client red
candles in Haunted while either hand holds Soothing Incense and toggle is on.
The full-candle target also draws a bright red depth-tested block outline only for
vanilla's current in-range block pick while holding incense. CandleTarget owns both
shape and outline eligibility; the shared renderer adds no second raycast or entity scan.
Both settings use Safari Runs > General. Cross-version blocks are identified by
registry path to avoid the 26.2 DYED_CANDLE vs 26.1.2 RED_CANDLE API difference.
PlayerNameStyle.component applies the existing UUID-backed rainbow name rule
to command lookups/tickets, sharing identity evidence with screen/HUD names.
In-game validation: right-click edges of candle block with incense in each hand;
verify normal targeting without incense/outside Haunted; loot Canyon hive and
verify disappearance, Queen Bee instant reappearance, nearby Refills holograms,
and later cooldown expiry. Enable Automatic Logging for HONEYHIVE attribution.

Clear Water and Capsule Prediction (2026-11-01): Safari Runs > Visual owns both.
ClearWater publishes volatile rendering flags, adjusts water-only opacity/fog/overlay,
and coalesces geometry rebuilds on setting/area changes. Versioned overlay mixins and
optional SodiumWaterMixin cover vanilla/Sodium; no status effects are removed.
CapsulePrediction caches paired target boxes per tick and solves from current render-frame
aim/eye, reusing the result when both aim and scene revision are unchanged. The short
peer client curve is extrapolated to a bounded long-range horizon or world boundary; terrain
checks stop at unloaded chunks, but the visual continuation remains. All trajectories
remain estimates until validated against server flight samples. The old estimate gate,
tick-lag interpolation and contact text are removed. The whole path uses the configured
contact color when a known critter is the first predicted hit; normal color otherwise.
No catch guarantee or additional actions. CAPSULE_THROW/CAPSULE_FLIGHT record bounded
nearby visual flights only with automatic diagnostics on (64 traces/visit, 4 concurrent,
80 samples each). Candidate ownership is explicitly unverified. Profile render time under
render/capsule-prediction before adjusting budgets. Compare ordinary stationary throws,
steep/long throws, moving throws and Masterful separately; do not fit one universal
model from ambiguous samples.
Settings pointer capture resets on reinitialization, focus changes and new presses,
preventing a missed mouse release from leaving slider drag state behind. Modal flags
remain authoritative; stale hit-index barriers cannot block a non-modal screen.
/safari missing <name> uses PlayerLookupCoordinator and populates the same recents.
Missing and collected hover lists share the canonical species complement; full lookup
shows both counts with the standard white divider, missing-only omits Hunting/tickets.
Queen Bee prefers a pending harvest over the previous confirmed harvest, so consecutive
loots keep the correctly refilled hive ready without waiting for a hologram update.
Regression coverage includes persisted clocks, profile-discovery harvest races, curve
fixtures, projection and settings grouping. Fabric smoke covers water/candle targets
with and without Sodium. In-game checks: restart/profile switching after hive harvest,
Queen Bee refill, distant textured beacons, water slider transitions, and capsule contact
alignment at calibrated and estimated angles. Do not claim runtime visual validation
from the smoke tests alone.

## 3.4.0 release invariants

Public releases ship Normal and Extra for 26.1.2, 26.2 and 26.3; Developer artifacts remain private.
Full candle targeting is Extra-only and requires an unlit candle; outline and interaction share
that predicate. Capsule predictions require direct, painting-aware visibility for hidden species
in Safe Mode. Painting occlusion uses the shared tick cache, avoiding a stale one-second window.
Missing HUD profit indicators are independently controlled by `display.indicateProfitableCritters`
(default true). Retention still belongs to `sparkling.alwaysShowProfitableCritters`; full-clear
indicators never turn on retention. HUD label measurements include the dollar suffix.
Capsule contact radius is an estimate, not measured server physics. Landing indicators use
cached frame geometry; no additional render-time entity scans are introduced.

## Sparkling appearance evidence and focused modes (2026-11-02)

SparklingTextures identifies already-received profile/head textures using a bounded decoded-property
cache (512 entries, 8 KiB input limit), never skin downloads or profile resolution. Known texture
identifiers originate from Mutuals 4.0.0 (CC0; notice packaged). The shared CritterEntities scan merges
appearance evidence via SparklingTextureEvidence before publication: real players
are excluded; exact owners beat proximity; ambiguous same-species neighbours do not inherit
Sparkling status, and capsule-only labels cannot acquire an appearance body. Texture aliases follow
SparklingWatch's existing individual ownership, replacement and resolved-catch suppression so delayed
nametags do not create a second found alert. Safe Mode still requires visual confirmation. Existing
name and repeated-particle detection remain active. SPARKLING found diagnostics include source,
distance and ticket presence; normal runtime does not log or decode textures per rendered frame.

Missing party-shared collection membership is no longer an ordinary-marker/HUD/objective retention
reason. Unique Mode keeps uncaught run uniques; Sparkling Mode omits those ordinary targets. Both
preserve detected Sparkling individuals, explicit Always Active choices, profit choices and necessary
prerequisites. Wumpa prerequisites depend on run uniques, an explicit target or a detected Wumpa,
not missing collection membership. Shared collections, API sync and party discovery remain available.

## Ticket menu selection (2026-11-02)

TicketSelection owns one bounded action per entry menu, using the current mode preference.
Bare tier labels and ticket labels (including count suffixes) are recognized only inside
Critter Safari Entry; player inventory slots remain excluded. Selection waits for an
unambiguous requested option with known availability on two polls, not all four tiers.
Manual slot/hotbar interaction cancels automation; unrelated keys and background clicks do
not. Existing known-party protection still applies; unknown party state alone no longer
adds a second independent blockade. Five-second fallback diagnostics name the waiting
reason and selected tier, with a visible manual-selection message. No automatic NPC
interaction, ticket substitution, or repeat submission is introduced. Live party-menu
validation is still needed; regression coverage cannot reproduce the tester's incident.

## Alpha and developer test persistence

HypixelConnection caches Alpha identity from the actual server address (alpha.hypixel.net,
case/port/trailing-dot tolerant). SessionManager.persistenceSuppressed is the shared guard:
Alpha forces suppression without changing the saved/manual developer preference, and any
active test run retains suppression through disconnect or disabling the manual setting.
The developer setting renders locked On (Alpha). Run history/PBs/rewards remain excluded;
SparklingStats also guards API imports, manual edits and catch mutations before touching
memory or scheduling writes. The live profit aggregate excludes test runs. Diagnostic logs
and live gameplay tracking remain available. Suppression does not undo prior saved data.

## Visit-wide Sparkling visibility

Loaded Sparkling evidence and found alerts do not depend on the player's biome, ticket,
or the entity being inside its expected biome. Detection remains inside the Safari visit,
with real-player exclusion, ownership/capsule/caught suppression and Safe Mode visibility.
The Missing HUD lists all presentable outstanding local and party-shared Sparklings;
spawn/center/pre-ticket display uses a Sparkling-only panel with no last-run data. Ordinary
biome objectives retain their existing scope. World Sparkling markers already bypass biome
filtering. This does not extend the server's entity delivery range.

## peer client 4 coordination (2026-11-02)

Hotspot reports use [SU]; biome done words stay bare. Starting-item alerts are not
compatibility-managed (explicit user preference). See MESSAGE_TEMPLATES for wire and
mode semantics. BiomeCoordination consumes existing observation/objective state and
revalidates each report immediately through CommandCoordinator; PBs and actual catch
ledgers remain separate. No additional API requests or entity scans are introduced.
Current roster collection failures inhibit Sparkling reports; Unique mode conservatively
requires all checks rather than treating unknown collections as complete. Structural coverage and unseen populations remain conservative.
No deployment requested for this work; mixed-mod party testing is still outstanding.

Verification: final VerifyBuilds passed all nine standalone profile/flavor combinations;
BuildSerkoAddons 26.2 and integrated check 26.3 passed, including regression, artifact
boundary and IQ/mixin smoke checks. Game instance jars were not modified.

## Native party coordination / protocol partner protocol (2026-11-02)

User supplied Downloads/compatibility.md; use docs/development/MESSAGE_TEMPLATES.md
for the maintained protocol summary. Assign Hotspots and Biome Reports are now
independent native settings. Schema 10 migrates mutualsCompatibility to biomeReports
and enables assignHotspots when the old combined feature was enabled. No saved custom
alert text is overwritten. Outgoing hotspot prefix stays [SU]; incoming [SM] remains
supported; biome codes stay bare. Starting alerts are independent of both features.
One Macaw suffices for Full Clear; never-spawned birds/Rockmite/Snoozle are exempt only
with resolved sources. Bird/coin ledgers retain personal starting supply separately;
party starting-item announcements never raise report requirements. Existing gameplay
quota and party-objective sync retain their own behavior. UUID roster changes reset
fairness, not current-run reports/frozen assignments. No deployment requested.

Pre-release review: BiomeCoordination.Survey retains Full Clear and Sparkling survey
coverage independently across movement and resets both with the run. Full Clear must
not require the player to occupy every species zone simultaneously (Cavern regions
can be disjoint). Catch/source/outstanding-Sparkling gates still revalidate before send.


## Party snapshot delivery (2026-11-02)

Public encounter-chat changes retain their full aggregate through optional
catchUpOnly=true on A uploads. The room stores the latest A but skips current-peer
fanout; joining connections still replay ordinary A envelopes. Private ritual/faint
messages, inventory and world observations remain live. PartySnapshotBatch coalesces
for 500ms without postponing continuously; live evidence wins mixed batches.
Identical connected aggregate uploads are skipped; reconnect resets deduplication and
publishes a live snapshot. No protocol field reordering or version change. Old clients
remain supported. See docs/integrations/PARTY_SYNC.md for the shareable integration
contract, exact masks, source semantics, retention limits and mixed-mod test checklist.


## Alert semantics after mode/coordination cleanup (2026-11-02)

Hotspot & Assignment banner waits for the final assignment while Assign Hotspots is
active. Party chat always shares the actual hotspot. Separate hotspotAssignmentText
preserves existing custom hotspotText. Both use the destination's biome color and the
existing shared banner controls. HotspotBannerState prevents repeat alerts, resets on
visit reset, and falls back to the actual hotspot after 6.5s without an assignment;
a late assignment may correct that fallback once. Both previews are available.
Unique-catch banners remain strict party catch milestones in every mode, not mode
completion. Chat Biome Completion explicitly describes Biome Reports' mode-aware
replacement and names its controlling setting on managed controls. Existing custom
messages and playback preferences remain intact. Floor Drops Done describes both
collection and focused-mode objective sufficiency; Sparkling alerts remain mode- and
biome-independent. No new mode completion evaluator or duplicate event stream.

## Backend hotspot authority (2026-11-02)

PartyHotspotSync uses H/B operations on the existing PartySyncNetworks transport, with
PartyItemSync owning the one outbound sequence. HotspotSnapshot validates the bounded
server result; HotspotWatch displays that result without running its fallback allocator
in parallel. Finalized fallback assignments are uploaded as locks on recovery; conflicts
freeze new allocations. A backend-owned visit never switches to competing chat allocation
on a temporary outage. Only the actual leader sends non-display instructions and acks IDs.
The standalone implementation is also the integrated source; no IQ/private dependency.

Backend PartyCoordination owns durable fairness and frozen per-lobby assignments. Initial
allocation is 2 seconds after the third confirmed report (all for smaller parties), capped
at 5 seconds from the first. Late arrivals batch for 250ms. Membership epochs reset history
on actual roster changes; older connection leases cannot undo a newer membership. Indexed
SQLite due/expiry records keep allocation alarms independent of retained history size.

Explicit H leave means a real Safari exit, never a transport failure. After all confirmed
attendees explicitly depart, a reused lobby can create a fresh assignment generation.
Uncertain/disconnected attendance is retained conservatively rather than inventing an exit.
WebSocketOutbox serializes text sends with a 32-message limit and bounded graceful close,
without blocking the client thread. Tokens, IPs and session credentials stay out of logs.
Integration contract and retained-run limitations: docs/integrations/PARTY_SYNC.md.
Mixed-mod live testing remains required; backend tests do not validate another mod's adapter.

## Developer sync HUD (2026-11-02)

Developer > Diagnostics > Party Sync HUD is the sole enable switch. The developer-capability
guard also controls registration and HudBox editor visibility. It uses the shared HudPanel,
HudBox positioning/scaling/reset, and a cached panel rebuilt only after status/data changes.
No extra network requests, world scans, file logging, or API polling. The chronological
feed holds eight entries; its payload-change cache is capped at 32 and cleared per visit
or when disabled. It summarizes accepted local/remote objective data, nests, Sparklings,
completion reports and authoritative assignments. Automatic Logging is independent.

Review fixes: recovery after imported fallback choices adopts backend coordination transport
without moving finalized choices. HotspotSnapshot checks merged biome collisions, including
partial snapshots that omit a locally locked player. Do not reduce conflict checks to merely
comparing UUIDs present in both maps; another UUID may already claim the locked biome.

Ticket-trading guest warps wait two seconds after the guest group settles and three seconds after a current party member's server travel notification. The buffer is capped at 26.75s to allow tick delivery before the hard 27s cutoff; command cooldowns still win. Item presentation order is Purple/Lime/Orange, Berry/Worm/Seeds, Incense/Coin; persisted bit positions and entity-ID prediction ordering remain unchanged.


## Held Shining Coins in party sync (2026-11-03)

Aggregate v3 is 39 integers: version at 33, held Shining Coins appended at 38 (0–64).
Index 4 remains cumulative personal discoveries, not inventory. Backend and client
accept legacy v2/38 with unknown coins (-1 internally); v2-only clients cannot read v3.
Use existing inventory cache, 500 ms live snapshot coalescing, reconnect publication,
and replace-per-sender semantics. Haunted rows show incense then coins and survive
Doomspiral completion while either item remains. See docs/integrations/PARTY_SYNC_SHINING_COINS.md.


## Mixed-mod live test fixes (2026-11-03)

Ten-minute socket lease renewal is expected; backend remembers displayed assignments
and no longer treats a brief reconnect as an uninformed player. H hello has optional
ticketed boolean (legacy omitted = eligible); our client reports ticket activation and
suppresses local assignment display outside active ticketed phase. Keep pre-ticket
Sparkling sync available. Shared-first Sparkling finds suppress the first matching
local alert, not marker creation or a second distinct local individual. TRANSFERRING
cannot discover/announce, and expired ticket windows suppress new local found alerts.
A aggregate feeder snapshots can clear zero when consumption advances or the same
reporter empties its known fill; unrelated unknown zero snapshots do not clear a fill.
SYNC_OBJECTIVES diagnostics record received/applied feeder values and mound words.


## Automatic Sparkling ticket entry (2026-11-04)

Restored backend assignment-chat grace to 2 seconds per assigned player; allocation
and late-arrival timing are unchanged. Backend timing suite: 91 passing tests.
Deployed Worker version 91a2de74-7218-43c7-ab5d-1f32eb9e4680.

Safari Runs > Tickets has an opt-in Automatically Use Ticket for Sparklings card.
It uses the selected run mode’s ticket preference; Manual starts with Basic. AutomaticTicketEntry owns one
bounded attempt per visit, including pre-ticket local or shared active discoveries.
It reuses TicketSelection's availability checks and single submission, trying the
preferred tier, then lower tiers descending, then higher tiers ascending. Unknown or
ambiguous menu availability never triggers a fallback. Nearby Manager identity, normal reach, line of sight, safe
floor, focus, manual input, active discovery and ticket-window checks govern entry.
Approach input is injected only after vanilla physical input is read; it never
sets movement keys, teleports, expands reach or changes player velocity. Movement
eases and slows near reach, capped at three blocks and six seconds. Cancelled entry
blocks delayed menu auto-selection, while manual ticket use remains possible.

Developer-only /safari debug useticket basic|economy|premium|firstclass arms the same
flow without needing a Sparkling, but still requires a live pre-ticket Safari visit
and normal protection. It consumes a real ticket. /safari debug useticket cancel
cancels; moving, turning, clicking, opening another screen or losing focus also
cancels an active approach. TICKET/AUTO and TICKET/SELECT explain phase transitions
and fallbacks through centralized logging. In-game approach/selection testing remains
necessary; use Automatic Logging for success, cancellation and unavailable-tier tests.

Validation: BuildAllArtifacts passed all nine standalone variants and integrated checks,
including loader/mixin verification and 636 byte-identical shared classes. Collected
SerkoAddons 26.2 jar deployed with matching hashes to Skyblock 26.2 and Ticket Collecting.
No release assets, version numbers, website or GitHub pushes changed.

Automatic ticket entry is Extra/Developer ONLY (user clarification). Both settings
are hidden in Normal, and entry eligibility, tick, movement injection and selection
overrides are capability-gated. Imported enabled Extra configs cannot activate it
in Normal. Existing automatic ticket-menu selection remains in every edition.
Normal-edition regression exercises the imported-config runtime boundary without
a live Minecraft client, and navigation tests assert edition-specific card order.


## Mixin compatibility review (2026-11-04)

Automatic entry now requires both intact idle Input and zero movement vector before
injecting forward input. Null, invalid and foreign analog movement cancels the
approach instead of being overwritten or dereferenced. The Normal edition still
returns before touching client input. Regressions cover these boundary cases.

Integrated chat history replaces its exclusive Predicate.test redirect with a
composable ModifyExpressionValue hook. The optional drop-effect name overlay uses
require=0 if another renderer removes its invocation target; 26.3 also accepts a
missing player render state. Existing darkness null-effect guard, nullable entity
render-state handling, optional Sodium/water hooks and mod-ownership plugin retained.
Static inspection of installed Noamm 1.3.4 legit found its KeyboardInput transformer
returns its argument unchanged; installed Sodium 0.9.2 retains the expected single
FluidState argument on updateQuad. This is static inspection, not live pack validation.

Compatibility validation passed: all nine standalone variants, integrated 26.2 and
26.3 checks, Fabric Loader 0.19.5/0.20.0/0.20.1 transformation tests, and 636-class
standalone/integrated parity for 26.2. Deployed verified 26.2 collected artifact to
Skyblock 26.2 and Ticket Collecting. No public release or website changes.


## Mode-based automatic entry ticket fallback (2026-11-04)

Removed autoSparklingTicketTier (old saved values are ignored). autoSparklingTicket
still defaults false, is now titled Automatically Use Ticket for Sparklings, and
remains Extra/Developer only. Full auto uses the current mode's ticket choice; Manual
means Basic-first for this explicitly enabled full-auto path. Normal menu selection
retains its existing exact-preference/manual behavior. Developer useticket commands
set a preferred tier and exercise the same fallback rules.

Fallback order: Basic/Economy/Premium/First Class; Economy/Basic/Premium/First Class;
Premium/Economy/Basic/First Class; First Class/Premium/Economy/Basic. Availability
must be confirmed for each higher-priority choice before moving on; two polls must
agree on the full selected option. All tiers unavailable stops without a click.
Diagnostics record the actual submitted tier, preferred tier and fallback flag.
Regression covers all 64 preference/inventory combinations plus incomplete, unknown
and ambiguous menu data. Existing protected, single-click submission stays shared.


## Debug automatic-entry no-op fix (2026-11-04)

Latest 17H-38M log and latest.log showed debug armed, no approach, then ticket lockout.
The Manager name was armor-stand ID 149735 at -51.5/68/21.5, not the clickable player
body. The previous resolver required a Player named Safari Manager; the pending test
was also cleared before successful resolution, silently returning to Sparkling gating.

SafariManagerTarget now pairs the separate label with a nearby unnamed player body
(or interaction proxy), excluding self, listed players and party members, rejecting
ambiguous bodies and preferring one player body over its interaction proxy. It uses
EntityTypeIds and shares the spawn anchor with TicketProtection. Regression uses the
recorded label plus delayed body, participant, proxy and ambiguity fixtures.

Debug intent remains armed through NPC loading; it is consumed only when approach
starts. Ticket expiry/visit changes now explain cancellation of queued tests. Central
TICKET/AUTO diagnostics log arm, changed waiting reasons, lookup failure counts,
approach and stop. Still no Sparkling or enabled automatic-entry toggle required for
the developer test; live Safari/pre-ticket safety checks remain.


## Automatic-entry motion smoothing (2026-11-04)

Latest 17H-46M log confirms both debug approaches interacted with the Manager and
activated tickets (~700 ms from approach); party-leader preselection meant no in-run
ticket menu. This confirms the label/body and pending-debug fixes in those tests.

Replaced capped 20 Hz yaw/pitch steps with TicketTurnSmoother, an analytical critically
damped trajectory evaluated after vanilla mouse handling each frame. It eases in/out,
takes the shortest yaw path, caps stalled-frame catch-up and rejects non-finite targets.
No random offsets, target scanning, network work or interaction calls in the frame hook.
Normal builds return immediately. Mouse/input/context safeguards remain; interactions
stay on the tick. Walking eases in from its own start time; the follow-up below replaces
the initial settled-aim requirement.

The non-cancelling optional MouseHandler RETURN hook uses no local capture/redirect.
Verified handleAccumulatedMovement exists on all three supported profiles. Regressions
cover frame-rate equivalence, acceleration/deceleration, no overshoot, yaw wraparound,
settling and stall limits. Live feel remains subjective and requires another test.

## Overlapping automatic-entry approach (2026-11-04)

Turn response increased from 9 to 14 with the same critically damped, frame-rate
independent motion. Stalled-frame integration is capped at 1/30 second. Walking now
ramps over 200 ms up to ordinary walking speed (no sprint), overlaps turns within a
60-degree forward cone, and scales down smoothly near reach or poor alignment.
Collision checks cover the swept box in the actual facing direction, not the target
direction. Normal reach and a valid unobstructed view ray trigger interaction without
waiting for settled camera velocity or the NPC center to enter reach.

Manager interaction may retry twice, at least 450 ms apart, only while no screen or
ticket activation has arrived. Each attempt rechecks normal reach, target, occlusion,
party protection and player safety. Focus, movement and camera changes cancel pending
retries. The approach stops walking on the first successful ray interaction; ticket
menu submission remains single-shot. Diagnostics include attempt number and approach
elapsed time. Existing Extra-only/default-off and mode-based tier fallback remain.
Regression coverage includes concurrent heading control, walking ramp/cap, retry
spacing/cap/menu arrival, alongside the existing selection and cancellation checks.
Release remains pending in-game testing; no publishing is part of this change.

Validation: standalone 26.2 Developer check and BuildAllArtifacts passed (all nine
standalone variants, integrated 26.2 checks, 640 byte-identical shared classes).
Integrated 26.3 loaderCompatibilityTest also passed. The tested 26.2 integrated jar
was deployed and hash-verified in Skyblock 26.2 and Ticket Collecting; restart required.
Logs: build/ticket-overlap.log here; build/ticket-overlap-matrix.log and
build/ticket-overlap-26.3.log in SerkoAddons. No public release or GitHub push.

## 3.6.0 release preparation (2026-11-04)

User reports solo automatic entry and Alpha Sparkling tests working. New party testing
was unavailable. Fixed Sparkling live beacon rendering bypassing exact capture ownership:
the same immutable suppression snapshot now gates both live marker and beacon, including
guaranteed Driftling catches. Other individuals remain visible; capture completion and
Sparkling discovery state are unchanged. Capture/edition regressions passed before version
bump. Release includes the accumulated detection, party alert, ticket entry and compatibility
changes above. Keep public downloads Normal x3 then Extra x3; private developer jars stay private.

## Trading Sparkling Found chat timing (2026-11-05, pending next release)

Latest.log confirms Stuffy and _cj warped at 14:43:55 and both joined the Safari at
14:43:56; only the user's manual Sparkling line followed. User clarified that the
configured Sparkling Found chat alert must wait for trading guests to arrive.
SparklingWatch now retains that chat per individual while TicketTrading is preparing
the warp or awaiting invited guests. Local banner and sync publication stay immediate.
Arrival uses exact server notifications or SafariPartyWatch's in-run names, independent
of mod/network presence. Retain expected guest names despite stale/rate-limited party
refreshes. Caught individuals discard pending chat; body rekeys transfer it; visit reset
clears it. Released chat uses the existing destination/template and scoped ChatQueue.
Regression includes ranked arrivals, non-sync guests, partial arrivals, and rejecting
ordinary party joins or party-chat imitations. Public 3.6.0 assets are unchanged.

Finished 2026-11-07: standalone Developer 26.2 checks and integrated build/checks
passed; VerifySafariParity confirmed 640 byte-identical shared classes. Deployed
the tested integrated jar to Skyblock 26.2 and Ticket Collecting with matching hashes.
Restart required; mixed-party in-game verification remains pending. No release/push.

## Shared ordinary appearance and remote capture reconciliation (2026-11-07)

Shared appearance decoding now returns species, Sparkling status, and required entity
type. Ten ordinary texture identifiers were verified from peer client 4.2.1 bytecode:
Flitter, Shyworm, Driftling, Chuckwalla, Rockmite, Gazer, Gimmiegold, Hideyho,
Mantis Shrimp, Troodon. The existing bounded decode cache and scan/alias merge serve
both kinds; ordinary evidence cannot mark an individual Sparkling. Player-profile
filter now checks listed players rather than all player-info entries (NPCs need those
too), still excluding self and known party members. Capture visuals remain excluded.

New remote caught bits reconcile one unambiguous remembered individual after its
live representation disappears. Retire its known aliases without echoing remote data
as a local catch. Ambiguous same-species pairs consume no individual; discard that
pending inference so a later local catch cannot accidentally remove the survivor.
Regression covers ordinary classification/late-label alias merging and distant shared
catch cleanup without echo. No public release or backend protocol change.

Validation: focused Developer 26.2 check and BuildAllArtifacts passed all nine standalone
variants and integrated 26.2 checks; 641 byte-identical shared classes. Deployed and
hash-verified the integrated jar in Skyblock 26.2 and Ticket Collecting. Live ordinary
Hideyho range and cross-biome teammate-catch testing remain needed. Build logs:
build/texture-unification.log (public), build/texture-unification-matrix.log (private).

### 2026-11-07 sync visit expiry and live error attribution
- PartyItemSync now ends networking ten minutes after visit entry, latched until a new visit. Roster changes and socket reconnects cannot renew it. Retains learned objectives/assignments; no wire protocol change.
- Cloudflare current-day analytics showed 465 runtime exceptions on old worker, 16 on first reduced-write deploy and 5 on current deploy at query time. Bounded live tail positively classified current exceptions as storage-quota errors, with uuid-session/session-limit HTTP500s. Daily cap still exhausted; improvements cannot restore allowance before Nov8 00:00UTC. Do not claim all historic exceptions diagnosed or post-reset savings verified.


### 2026-11-07 renewal rollback / floor-marker investigation
- User requested reverting the client ten-minute cutoff; PartyItemSync restored to original renewal behavior. Backend write optimizations retained.
- Inspected latest three saved-run logs: Nov7 09:21 V28, 08:35 V3, 08:28 V41 UTC. Older two report Haunted known19/10 shown0 even before sufficient incense; latest reports shown5->4->3->2->0 with incense reaching4, expected eligibility. All33 pickup predictions match actual. No logged warning/error entries. Cannot determine older filter cause without historical config/per-kind reasons; do not claim fixed.
- Added throttled FLOOR diagnostics with per-kind shown/disabled/unneeded/possibleHidden counts, mode, toggles, always masks, incense/candles and party objective status. No work when logging disabled. Added regression retaining incense for Always Active Doomspiral until four held.

- User clarified Doomspiral was missing from own collection, not Always Active; profit enabled. Found policy mismatch: quest filtering reused ordinary-species suppression. SparklingMode now preserves spawn quest items for own missing Sparkling species independently of price freshness/ordinary-marker suppression. Still hides sufficient supplies/completed objectives and preserves profit overrides. Ordinary mob visibility and party-shared policy unchanged. Added incense, coin, bird-feed regressions; detailed diagnostics retained.


## 2026-11-07 connection recovery, peer client coordination, Litterbug timer
- RemotePartySyncProvider now uses monotonic deadlines: 12s welcome/atomic snapshot, 75s inbound silence, 45s connecting guard; 25s pings unchanged. Retry-After seconds/HTTP dates honored, 401 invalidates the rejected cached session, 2-30s backoff survives roster/context changes and resets after a usable snapshot. Generation checks isolate stale callbacks; old outboxes close. No new backend polling/writes.
- BiomeCoordination now emits delayed elected party-chat completion reports after network B reports (peer client-compatible UUID election), suppresses chat duplicates and rechecks mode/completion/shared held supplies. Forest feed and Haunted coin inventory consulted through the existing provider facade. Existing mode evidence/zone gates retained.
- Hotspot assignments remain immutable across reconnects/disbands; coordination pauses when actual party includes outsiders to the frozen room. Backend timing/protocol unchanged.
- LitterbugTimers consumes the existing paired-body scan. Timer starts only on continuously observed surface->underground transition, labels initial ~10s as estimate, learns bounded median of uninterrupted 4-30s observed cycles, shows Returning... when overdue, expires missing bodies after 1s, excludes capture artifacts. Initial underground sighting cannot invent a timer. Safe marker policy requires a visible prior surface observation. Markers > Critters > Litterbug Timers defaults on. Ordinary underground hitbox replaced by surface timer when tracked. Sparkling discovery/beacon lifecycle unchanged.
- Recent ci logs verify Litterbugs at Y=1 and surface Y=69, but measured continuous snapshot intervals range roughly 7.7-16s, so exact fixed ten seconds is NOT established. Needs in-game observation of several full cycles with logging.
- New regressions: PartyRecoveryRegression, LitterbugTimersRegression; Retry-After cases in BackendValuesRegression and elected reporter cases in CoordinationRegression.
- No public release or GitHub push authorized for this task.
- Final checks: 26.2 developer core/ticket regressions passed; standalone safe checks passed on 26.1.2 and 26.3. Integrated BuildSerkoAddons and VerifySafariParity passed (645 identical SafariUtils classes); final jar deployed with SHA-256 verification to Skyblock 26.2 and Ticket Collecting. No release/push.


## 2026-11-07 ordinary marker / entry follow-up
- Latest 14H-18M/P2 logs: 41 Litterbug hiding intervals ~7.9-16s, variable within each body UUID (not a fixed per-individual period); keep estimate, new duration diagnostics include body network ID. One API/PARTY_REFRESH temporary-unavailable event; no logged renderer/timer errors.
- Central CritterMarkerGeometry.visibleInBiome applies ordinary live/provisional markers, recatch pins, shrinking markers and Litterbug timers; Sparkling bypass retained. Existing tracked species already biome-gated.
- Texture-only item displays have zero collision bounds; presentedHitbox now uses stable visible appearance geometry (preserves custom Driftling/concealed shapes, Wumpa-sized fallback). Never cache zero/display geometry as a real provisional body shape.
- Hideyho player-skin anchor is feet while named label anchor is upper body; normalize player +2 blocks in solver and live Sparkling rendering before existing upper-anchor geometry. Regression checks both forms agree.
- AutomaticTicketEntry removes focus gating, advances view smoothing on tick as well as frame hook, closes menus through onClose while never closing a cursor-held item; background/focus-opened menus can resume approach. Toggle sprint alone no longer cancels; owned approach suppresses sprint without editing user toggle preferences. Known Manager targeting ignores overlapping players but preserves block obstruction/reach checks and bounded time/distance/retries. Real directional/action input still cancels.
- Sleeping Wumpa label recognition already covers ordinary/Sparkling zZz forms. Added full scan+texture regression for texture-first detection and merging ordinary sleep label + ravager + Sparkling skin as one individual.
- No new mixins, backend changes, push or release. Build/deploy test artifacts only.


## 2026-11-07 minimum Litterbug timers / complete geometry follow-up
- Litterbug timer now always estimates the earliest observed return (~8 seconds), never learns a longer interval. Returning... persists after zero until the hidden body returns. First-observed underground bodies have Hidden labels, not invented elapsed time. Extra can project their X/Z onto the recorded Haunted room Y=69 floor only when supporting terrain and clearance are loaded/verified. Safe visibility knowledge is separate from Extra projection.
- Native species dimensions replace generic square fallback in appearance, provisional-label, tracked-real-body and zero-sized recatch geometry. Existing custom sizes remain unchanged. Uses central verified body types plus cached native/observed dimensions; cache clears with renderer world state. Actual bodies retain their own geometry.
- Hideyho START/END and Sparkling visibility/beacons share a verified texture-player target when available. Candidate stability ignores harmless vertical label bobbing within the same block, preserving horizontal/teleport checks. Latest 14H-40M log already shows texture END detection 500ms before label, but label anchor swap restarted confirmation; no evidence that the server delivered END body much earlier.
- Pity Title/Eagle Rarity card restored; Litterbug Timers moved to a separate Special Critters card immediately below Critter Color Override. Existing fixed-size card slicing had also hidden Rarity Color when the extra field was inserted; removing that insertion restores it. Regression verifies all these cards.
- Tests extended for minimum countdown after long cycles, native bat dimensions, shared Hideyho anchors, settings membership. No release/push/backend changes.
- Final validation/deployment: all three profile checks passed; integrated build and 645-class parity passed. SHA-256-verified deployment to Skyblock 26.2 and Ticket Collecting completed; private component snapshot synchronized.

## 2026-11-07 Flitter alert outage / objective texture classification
- Reviewed saved run 22:10:48 V1. Flitter discovered by texture at 68 blocks with ticket active; no discovery alert because unconditional initial-room snapshot gate waited during unavailable backend. Solo run roster=1. Local confirmed-solo alerts now bypass optional transport; unknown/party waits for initial snapshot up to transport welcome timeout (12s), then falls back locally. Existing shared-first ownership suppresses duplicates when a snapshot arrives; identity replacements preserve wait start. Added fallback diagnostics and bounded visit-local wait state.
- Ordinary Rockmite item_display hash identifies unopened mounds, not live critters. It now has objective-only appearance identity, excluded from CritterEntities; shared scheduled MoundSpotter scan consumes it alongside interactions. Canonical mapping and coalescing prevent two carriers claiming two mound slots. Existing safe visibility, completion ledger, hits and party protocol remain authoritative. Sparkling Rockmite skin and actual body/name recognition preserved.
- Hideyho END texture sighting precedes label by ~752ms in this log; confirmation at 1002ms uses existing stability rule. No evidence of earlier available END body, so no speculative range/timing change.
- Regressions added for snapshot fallback/solo/ready, mound texture classification vs Sparkling Rockmite, canonical slot and duplicate-carrier merging.
- All three Minecraft profile checks passed. Integrated build/parity/deployment tracked below. No public release or push requested.
- Integrated build succeeded; 645 Safari classes byte-identical. Deployed and SHA-256 verified to Skyblock 26.2 and Ticket Collecting. Restart required to load updated jar.

## 2026-11-07 texture-first mound follow-up
- Mound texture identification now precedes interaction heuristics; weaker interaction carriers cannot claim neighbouring slots when they represent an already textured mound. Geometry fallback retained for delayed appearance metadata.
- Confirmed exact positions retained for the visit, avoiding marker anchor loss on entity unload. Local authoritative fifth-hit completion cannot be resurrected by lingering carriers beyond the short despawn suppression window.
- Positive texture detections remain unrestricted by the 48-block interaction absence radius. Distant missing textures are still unknown: no invented wider reliable absence radius, no false party completion/survey from unload. Existing safe inspection rules retained. Textures are server-sent item-display entities, not a way to inspect unloaded entities.
- Added texture-first duplicate/fallback regression coverage. No backend protocol changes or public release.
- Validation: all three profile checks passed (26.1.2 required clean regression recompilation after a missing test class during concurrent builds). Integrated 645-class parity passed. SHA256-verified deployment to both Skyblock 26.2 and Ticket Collecting.

## 2026-11-07 pre-ticket floor-drop evidence
- Verified FloorDrops ticks before ticket use and reset already schedules an immediate scan. SessionManager resets it at beginVisit only, not ticket submission; renderer remains session-gated.
- Restricted discovery to Safari proper instead of canyon entrance. Preserve prepared lobby identity through missing/blank sidebar ID so inferred patterns cannot reset mid-visit. Pending-to-known lobby attaches without clearing observations; real lobby changes/new visit still reset.
- Added ticket-used flag to candidate-count diagnostics and regression coverage for early/missing/changed lobby metadata. Existing pattern retains observed IDs after pickups; ambiguous late-arrival evidence remains Unknown, never guesses missing quest items.
- 26.2 developer regression/artifact check passed; integrated build/parity follows. No release/push.
- Integrated build and 645-class parity passed; SHA256-verified deployment to Skyblock 26.2 and Ticket Collecting completed.

## 2026-11-07 immediate Hideyho END detection
- Removed the one-second END position stability timer and its obsolete candidate state. First valid live END sighting is confirmed immediately after the server's hiding phase transition; the old START location remains excluded. Existing Safe Mode visibility and remembered-position behavior retained.
- The separate delay for speculative Safe Mode candidate locations is unchanged; it does not delay actual detected Hideyho.
- Regression checks pass for START rejection/new END acceptance/missing evidence. Existing debug command `/safari debug useticket basic` immediately arms full entry without requiring Sparkling discovery; user can Alt-Tab after sending it while pre-ticket at Safari spawn on alpha. Uses real ticket, existing cancel command supported.
- 26.2 checks and integrated 645-class parity passed; deployed and SHA256 verified to both test instances.

## 2026-11-07 v3.7.0 release review
- Latest 15H-33M log: both automatic ticket debug attempts reached ticket activated. Only two API/PARTY_REFRESH service-unavailable errors. Hideyho in both runs was falsely confirmed at upper Y=2.92 during the underground teleport, before actual final upper Y=70/87. Reject under-map sightings and old START anchor variations before any position update/alert; valid END detection remains immediate.
- Biome completion reports now use chat when the previously authoritative backend is disconnected. An empty backend allocation can fall back after seven seconds disconnected; any existing assignments remain immutable. Added deterministic-clock outage tests (negative process-relative test timestamps initially triggered unset handling; corrected fixture).
- Version prepared as 3.7.0; concise release notes, all Normal downloads before all Extra. Website only changes version/asset URLs, preserving existing layout and text. Integration reviews ignored rather than published.
- Backend existing protocol suite: 103/103 passed. Full matrix and integrated gates running; release/push results follow.
- Final release gates passed: nine standalone variants, integrated SerkoAddons checks, 645 byte-identical shared classes, and 103 backend tests. Publishing six Normal/Extra jars only; developer artifacts remain local.

## 2026-11-07 background automatic ticket test
- Latest 15H-59M log: debug Basic armed, approach started, Alt-Tab opened PauseScreen, automation closed it, then stopped for no movement progress at ~1.3 seconds. Background renderer ~10 FPS; log lacks actual input state, so the underlying movement failure is not conclusively identified.
- Corrected watchdog to count applied forward-input effort (14 full walking ticks) instead of 700ms wall time, excluding screen interruption, turn alignment and background scheduling delays. Six-second approach cap, collision/reach/manual cancellation unchanged.
- Added first movement-hook and approach-end diagnostics: focus, input class/vector, requested forward, applied ticks/effort and input/progress ages. Next tabbed-out debug test required; do not claim confirmed in-game resolution.
- 26.2 developer check/regressions/artifact boundaries passed. Integrated build and deployment pending. No public release changes authorized for this fix yet.
- Integrated checks and 645-class parity passed; deployed and SHA256 verified to Skyblock 26.2 and Ticket Collecting. Restart and repeat debug command while unfocused with automatic logs.

## 2026-11-07 confirmed unfocused ticket blocker
- 16H-09M log: two focused attempts activated tickets; unfocused attempt timed out after six seconds with appliedTicks=0, requested=0, no input hook calls. PauseScreen opened after focus loss and reappeared after stopping.
- Verified vanilla Minecraft.pauseIfInactive bytecode: after 500ms unfocused it calls pauseGame every frame. Closing the menu alone therefore produces a reopen/reclose loop that resets forward movement before input runs.
- Added TicketApproachFocusMixin on pauseIfInactive HEAD, narrowly cancelled while an eligible Extra automatic/debug ticket entry is pending/approaching/in its ticket menu. No options mutation, no changes to manual pause invocation; immediately inactive on finish/cancellation/ticket activation/outside entry context. Method verified present on all three supported Minecraft versions.
- Prior watchdog/input diagnostics retained. Cross-profile checks/build and integrated deployment pending; no public release or GitHub push.
- All three developer profile checks passed; integrated mixin/loader checks and 646-class parity passed. SHA256-verified deployment completed to Skyblock 26.2 and Ticket Collecting. Restart required, then repeat unfocused debug Basic entry with logging; runtime confirmation still pending.

## 2026-11-07 remove approach travel/time caps
- 16H-16M log: first two attempts succeeded (second unfocused), third unfocused attempt stopped after 1.297s with the old combined interruption/limit reason. Six-second timer ruled out; prior diagnostics cannot distinguish three-block displacement vs transient ground/player/manager state.
- User requests no arbitrary limit. Removed six-second approach and three-block displacement caps. Brief airborne states now pause movement and resume on landing, bounded by actual Safari entry window. Manager loss and invalid player state have distinct reasons; manual input, obstruction, actual stuck detection and menu submission bounds retained.
- 26.2 checks/build and integrated deployment pending. No publishing.
- User correction supersedes preceding limit behavior: restore approach timeout at 3 seconds (specific stop reason); remove onGround requirement and support-under-next-step check entirely, along with three-block travel cap. Normal interaction reach, collision/manual/state checks remain. Earlier build was not deployed; rebuilding corrected behavior.
- Revised 26.2 regression/artifact checks and integrated 646-class parity passed. SHA256-verified deployment to Skyblock 26.2 and Ticket Collecting complete; restart required. No release or pushes.

## 2026-11-07 16H-24M sparkling and shrimp observations
- Visits 3/4: same Driftling b24550dc and Mantis texture bdd025e0 are scanned pre-ticket as sparkling=false (ci body snapshots). First true evidence appears after ticket activation: Driftling ~150ms, Mantis ~1.2s. Scan/watch are visit-enabled and no ticket gate found; evidence is consistent with alpha changing appearances after admission, not delayed announcement. Logs do not contain raw profile changes, so avoid claiming universal server requirement. Added first-sighting sparkle/source and appearance-transition logging.
- Static shrimp texture carriers cc4d4106 and 6d6f781d remained after their tropical-fish bodies vanished during A5/A6 capture, becoming new texture-only sightings at the bottom. Added per-visit loaded texture ownership, retaining known owners amid crowded peers. Missing capture-associated owner suppresses independent texture fallback until a genuine unambiguous new body returns; existing peer bodies cannot inherit residue. Unloaded textures prune ownership; ordinary noncapture body unloading still permits distant detection.
- New regression cases cover ownership, crowding, capture residue, peer rejection, breakout recovery and normal distant fallback. 26.2 checks/build then integrated deployment pending. No release/push.
- Final 26.2 regression/artifact checks and integrated mixin/loader/parity checks passed (646 shared classes). Deployed with SHA256 verification to both 26.2 instances. User should retest clustered Mantis catches; no live confirmation yet.

## 2026-11-07 v3.7.1 release
- Reviewed texture capture ownership and grouped rebindings; retained conservative unique matching and distant noncapture fallback. Avoided repeated captured-peer set allocation with lazy insertion.
- Full nine-variant standalone matrix passed regression and artifact boundaries; integrated SerkoAddons mixin/loader checks and 646 byte-identical shared classes passed. Matrix log: private backend/release-3.7.1-matrix.log.
- Version 3.7.1, concise fixes-only notes, existing Normal-then-Extra six-download ordering. Website changes only version labels/asset links; existing layout and descriptions retained. Publishing and deployments follow.
