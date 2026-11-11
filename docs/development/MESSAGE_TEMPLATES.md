# Configurable alert and chat templates

`AlertText` is the shared, event-time formatter for configurable Safari banner and
outgoing chat messages. It does not change the selected send channel, invent events,
reset cooldowns, perform network requests, or alter Safe Mode eligibility. Defaults and
saved user text remain unchanged. This is separate from decorative fullscreen artwork.

## Relevant event tags

There is no global player/mode/run-time/position context. Cards list only their relevant
values. Removed tags in existing custom text stay literal; defaults and saved text are
not overwritten.

- Hotspot and biome-unique completion: `<BIOME>`, `<BIOMELETTER>` (`f/c/i/h`),
  `<BIOME_UPPER>`, and `<BIOMELETTER_UPPER>`.
- Critter alerts: `<CRITTER>`, `<CRITTER_UPPER>`, `<RARITY>`; no derived biome or stage.
- Sparkling-found and bird-spawn templates additionally accept `<X>`, `<Y>`, `<Z>`.
  Coordinates are event positions rounded to one decimal, never the sender's position.
  A local Sparkling sighting uses its paired body (label if body unavailable).
  Server-only bird reports and shared Sparkling reports have no exact body association;
  their coordinates are `?`. Do not guess an existing nearby bird or delay normal alerts.
- Full-party alerts: `<PLAYERS>`, `<MAX>`.
- Sparkling catches: `<COUNT>` is the lifetime species count.
- Starting inventory: `<ITEMS>`.
- Forest feed: `<ALL_FEED>`, `<TOTAL>`, `<SEEDS>`, `<WORMS>`, `<BERRIES>`.
- Existing floor-drop completion banners retain `<BIOME>` for their default text.

Examples: `<BIOMELETTER>d` produces `cd`; `[SU] Hotspot: <BIOME>` produces
`[SU] Hotspot: Icy`. `Sparkling <CRITTER>! <X> <Y> <Z>` uses the detected critter's position.
Expansion is event-time, single-pass; values are never interpreted recursively and
queued messages retain their event context. Unsupported tags remain literal.

## Party coordination

Safari Runs / Party has independent Assign Hotspots and Biome Reports switches.
Each locks only its own party-message format without overwriting custom text.
Starting-item alerts remain independent. Schema 10 migrates the old combined setting
into both switches when it was enabled. The formats are:

- Hotspot: `[SU] Hotspot: <BIOME>` in Party chat. Accepted incoming prefixes are
  SU, SafariUtils, SM, CritterMod and CM, plus the unprefixed peer client forms.
- Biome completion: bare `fd`, `cd`, `hd`, `id` in Party chat. `BiomeCoordination`
  evaluates the effective RunMode independently of unique/PB announcements.
- Starting-item alerts are **not** managed by either feature. Their saved toggle,
  channel, item selection, template, and native-sync suppression remain unchanged.
  Teammate starting-item announcements do not contribute to reporting requirements.

Hotspot assignment also enables/locks its own report independently. Current-roster
reports start a two-second complete-roster / five-second partial-roster window.
Fewest prior retained hotspots wins a contested biome, then normalized UUID hash and
identity break ties. Losers use UUID order and Forest/Cavern/Icy/Haunted free-biome
order. Counters survive runs for the same roster, clear on roster change/disconnect,
and increment once when the assignment finalizes. Roster comparison uses normalized
UUIDs (name fallback), not IGN alone; resolving UUIDs must not discard current-run
reports or unfreeze an assignment. Announcements wait until the run is active for
0.5 seconds. Actual hotspot stays separate.

Full Clear requires minimum population, observed population, local zone coverage,
no live remainder, and exhausted spawn sources. One Macaw suffices even if more
ordinary Macaws remain; actual uncaught Sparklings still take priority. Unique requires unique catches (or
proven unavailable RNG species), plus required Sparkling source checks; bird feed
must be exhausted. Sparkling requires checks only for species missing from the
shared collection. All modes retain actual uncaught Sparkling evidence. Focused
modes suppress `id` after Wumpa is caught; Sparkling can send it before Wumpa only
when Wumpa is not needed and another Icy species is needed.

Reports require the current biome, a complete roster, and loaded party collections for Sparkling mode. Unique mode treats
unknown collections as requiring all relevant checks. They settle for 1.5 seconds, revalidate before sending, and use the
central command cooldowns with at least three seconds between reports. Incoming done
words only suppress redundant coordination, never fabricate catches, PBs, or collections.
Evidence uses existing scans/ledgers at four evaluations per second while enabled. Structural and
population evidence is intentionally conservative when coverage is incomplete; absent
out-of-range entities alone never imply a clear. `COORDINATION/BIOME` logs changing blockers
and sends only while automatic logging is enabled.

References: peer client 4.0 HotspotAssignments, HotspotAssignmentAlert, SafariCompletionEvents,
SparklingChecks and completion zones. Mixed-mod live testing remains required, especially
when a client joins/restarts mid-session and has different hotspot fairness history.

## Performance and tests

No parsing on render ticks. Tag-free text returns unchanged without world access.
Token scanning is linear, single-pass, and does not interpret replacement values as new
templates. No runtime/player lookup is performed. Coordinates are formatted only when requested. No unbounded parsed
text cache is retained. `AlertTextRegression` covers all biome codes, precedence, legacy
replacements, literal/unknown tags, malformed input, laziness and exact recorded wire formats.

protocol partner's supplied compatibility.md (2026-11-02) is the protocol reference. Forest
requirements use nine floor feeds plus personal starting feed. Confirmed bird spawn
counts inform full-clear targets. Coin requirements use known floor supply plus
personal starting coins, independent of other players' starting-item messages.
Known coin-source exhaustion does not require unrelated Haunted loot to be collected;
unknown ordering retains the conservative floor-clear fallback.


## Alert presentation and mode semantics

The Hotspot & Assignment banner displays the final assigned destination when Assign
Hotspots is enabled. Outgoing party hotspot messages still describe the actual Hypixel
hotspot. Actual and assigned banner texts are independent; both use the displayed
biome's color. The banner waits for assignment, with an actual-hotspot fallback after
6.5 seconds if unavailable. A delayed assignment can correct the fallback once.
Both forms have previews and share the existing sound, duration and color controls.

Biome Uniques Caught, All Uniques Caught, and All Uniques Except Macaw are real catch
milestones in every mode. They are not claims that Full Clear or Sparkling source checks
are finished. The chat Biome Completion card uses those unique catches only when Biome
Reports is off; when on, its managed party code comes from BiomeCoordination's current
mode policy. Managed controls name Assign Hotspots or Biome Reports as their owner.
Custom messages are retained when either feature is switched off.

Floor Drops Done may also mean the focused-mode objectives no longer need floor drops,
not that every physical drop was picked up. Sparkling alerts apply in every mode and
biome, including pre-ticket discoveries. No alert preference is changed automatically
when selecting a run mode.
