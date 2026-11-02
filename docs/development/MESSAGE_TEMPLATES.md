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

Examples: `<BIOMELETTER>d` produces `cd`; `[SM] Hotspot: <BIOME>` produces
`[SM] Hotspot: Icy`. `Sparkling <CRITTER>! <X> <Y> <Z>` uses the detected critter's position.
Expansion is event-time, single-pass; values are never interpreted recursively and
queued messages retain their event context. Unsupported tags remain literal.

## Mutuals compatibility

The opt-in Safari Runs / Party toggle provides reversible effective overrides. It
locks the affected settings controls without overwriting custom fields. Disabling it
restores the saved choices. The managed formats/channels are:

- Hotspot: `[SM] Hotspot: <BIOME>` in Party chat.
- Unique completion: `<BIOMELETTER>d` in Party chat, on actual all-species catches in
  focused modes only. Full Clear does not emit this code from an all-uniques event,
  because that would incorrectly imply all populations were caught.
- Starting items: `[SM] Starting items: Nx Shining Coin; Nx Yogi Berry; ...` in Party chat.
  Only coins and bird feed are included: Mutuals rejects the entire payload when it
  contains unsupported quest items. Native objective sync still owns our item state.

Hotspot assignment also enables/locks its own hotspot report when used independently.
Party reports are accepted only from the current known roster and current visit. The
server party-chat echo provides report order consistently for mixed-mod parties.
Allocation matches Mutuals: first report keeps a contested hotspot, duplicate holders
are ordered by normalized UUID string hash, then identity string, with name fallback.
Free biomes use Forest/Cavern/Icy/Haunted order. Assignment starts after all reports or
3.5 seconds, can revise until 10 seconds, and then freezes. Actual hotspot stays separate.

This is chat interoperability, not a claim that incoming messages prove catches or PBs.
Mutuals' spatial-zone/UUID-minimum checks are heuristics over client-loaded entities;
we do not import them as exhaustive population or catch proof. References reviewed:
HotspotAssignments, SafariCompletionEvents, StartingResourceNotice/Reminder, and
SparklingChecks from the local 3.6.4 review. Unimplemented report types are not advertised
as compatible. Existing ticket-trading/party networking remain independent.

## Performance and tests

No parsing on render ticks. Tag-free text returns unchanged without world access.
Token scanning is linear, single-pass, and does not interpret replacement values as new
templates. No runtime/player lookup is performed. Coordinates are formatted only when requested. No unbounded parsed
text cache is retained. `AlertTextRegression` covers all biome codes, precedence, legacy
replacements, literal/unknown tags, malformed input, laziness and exact recorded wire formats.
