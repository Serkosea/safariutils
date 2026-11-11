# SafariUtils shared-party protocol — objective wire reference

Current negotiated run/completion changes: [3.8.0 implementation guide](PARTY_SYNC_3.8_IMPLEMENTATION.md). This supersedes older lifecycle/timing descriptions below where they differ.

Current lifecycle/replay additions: [concise 3 November update](PARTY_SYNC_UPDATE_2026-11-03.md).

Updated 2026-11-03. Wire protocol: aggregate version **3**, with optional
`catchUpOnly` delivery hint. This describes implemented behavior, not a requirement
to copy SafariUtils's UI, architecture, or run-mode logic. Backend delivery support was deployed on 2026-11-02. Mixed-mod live testing
with peer client has begun; lifecycle fixes still need a mixed-mod retest. No backend credentials are needed in either mod.

## What adopting this would provide

Both mods can connect to the existing service during the same Safari visit and
share observations without putting synchronization payloads into party chat.
A small adapter can feed received information into each mod's existing objective
trackers and HUDs. No additional hosting or bundled SafariUtils dependency is needed.

| Shared information | Meaning |
| --- | --- |
| Quest-item discoveries | Per-player starting supply plus personally collected quest drops |
| Held items | Current bird feed, gems, incense, and Shining Coins; not a complete inventory dump |
| Bird-feeder progress | Feed consumed, current feeder type/stack, remaining observed spawned birds |
| Nests | Canonical nests positively checked/resolved |
| Walls and mounds | Canonical source slots confirmed resolved; see mask semantics below |
| Gems and candles | Confirmed gem placements, door-open state, candle progress |
| Major encounters | Gemzie, Wumpa, and Doomspiral state used for catch-up/objective display |
| Sparklings | Species-level found/caught facts for the current visit |

This is a temporary observation relay, **not an authoritative Hypixel run database**.
It does not know information unless a participant reports it. It does not share
individual critter entity IDs/positions, all ordinary catches, PBs, saved collection
history. Collection API lookups are a separate feature.
Hotspot authority and completion coordination are specified in [the integration guide](PARTY_SYNC.md).

## Delivery model: local chat first, snapshots for catch-up

Process public Hypixel chat locally as usual. Do not invent catches from snapshots.
Public Gemzie/Wumpa/Doomspiral catch announcements and run-wide encounter messages
update local state immediately and schedule a **catch-up-only** aggregate upload.
The service retains that snapshot without broadcasting it to players already connected.
When another participant connects, it receives the retained state as an ordinary `A`
message and can recover the objective state it missed.

Private messages and world observations still need live sharing: held inventory,
placed gems, wall/mound/nest confirmations, the summoner's `Your ritual...` message,
and a player's private Wumpa faint/retreat message, for example. Do not classify every
encounter-related update as chat-only merely because it has an encounter field.

Coalesce updates for about 500 ms. If a private/live observation occurs in the same
batch as public chat, send **one normal live aggregate** containing both. Live delivery
wins. This avoids splitting the same snapshot into multiple requests. A reconnect or
first active snapshot is always live. Skip identical aggregate uploads while connected.
Repeated updates must not keep postponing the first batch's deadline indefinitely.

This still requires an upload to preserve central catch-up state; it removes redundant
fanout and peer processing, not all network traffic from public events. Multiple clients
can each retain their view. The service does not run an elected reporter or reconcile
one authoritative global catch ledger. A normal later snapshot may also carry those
completion bits; clients merge them idempotently without playing another catch alert.

## Session and room lifecycle

1. Use the logged-in player's UUID, lowercased with hyphens removed.
2. Obtain one short-lived service token; reuse it across reconnects until near expiry.
3. Resolve the complete Safari party roster, including members using neither mod.
   The roster must contain 1–4 distinct UUIDs and include the local player.
4. Connect with the current Safari server/lobby ID and that roster. The lobby ID is the
   shared Hypixel server identifier, not a client-generated random run ID or biome.
5. Keep the visit roster stable when someone is temporarily absent, hasn't used a
   ticket yet, or the host disbands a ticket-trading party. Confirmed late party
   arrivals can expand it; reconnect with the expanded roster. **Exact UUID sets must
   match** before two sockets exchange state. Different roster sizes do not merge.
6. Clear all run observations on leaving Safari or changing Safari lobby. Disconnect
   the socket and reject asynchronous callbacks from the previous visit. Never reuse
   a previous run's masks just because the party members are the same.

SafariUtils freezes the complete roster confirmed after visit entry, then admits only
centrally confirmed party members physically present in that Safari. It does not build
a room from an arbitrary subset of nearby players. Ticket-trading roster expansion can
briefly separate peers until their rosters agree. This edge case needs mixed-mod testing.
For hotspot `hello`, additionally require the complete current party UUID set to match
the transport roster. A frozen objective room during disband/reformation is not proof
of current membership; pause hotspot publication while the roster is uncertain or differs.
Resume with the confirmed matching roster rather than resetting fairness from partial data.

The backend groups by lobby plus exact roster. Active sockets retain their latest
`A`, `N`, `S`, and individual `B` completion reports; new connections replay other active participants' snapshots.
This survives Cloudflare hibernation, but is **not permanent storage**. A disconnected or
expired reporter's snapshot is not promised to later joiners, and an entirely empty
room does not guarantee objective recovery. The new hotspot authority separately stores
assignments and fairness; see the integration guide for its retention and run identity.
Correct visit reset and disconnect behavior are required on both clients.

## Connect and authenticate

Service origin: `https://api-dev.serkosea.dev` (the hostname is currently used by the
public client too; `dev` does not imply a different protocol).

```http
POST /v1/auth/uuid/session
Content-Type: application/json

{"uuid":"<32 lowercase hexadecimal characters>"}
```

Response includes `token` and `expiresInSeconds` (currently 900). Refresh shortly
before expiry, e.g. with two minutes remaining. Use a single-flight request and retry
backoff; do not request a token every tick or for every message.

```http
GET /v1/sync
Upgrade: websocket
Authorization: Bearer <service-token>
X-SafariUtils-Lobby: <shared-lobby-id>
X-SafariUtils-Player: <local-uuid>
X-SafariUtils-Roster: <uuid1>,<uuid2>,...
```

Connect using `wss://api-dev.serkosea.dev/v1/sync`. Lobby IDs allow 1–64 ASCII
letters, digits, underscores, and hyphens. UUID roster order may differ between clients;
retain **your own submitted order** to interpret the welcome mapping.

The token represents a **claimed UUID for service budgets**, not verified Microsoft
account ownership. It does not require secure-chat keys, Minecraft access tokens,
or Mojang verification. Never send Minecraft session credentials or private keys.
Roster isolation and rate limits are not proof that a modified client is truthful.

```json
{"type":"welcome","self":"<opaque-self-tag>","roster":["<tag1>","<tag2>"],"features":["hotspots-v1","biome-reports-v1"]}
```

The returned tag array corresponds positionally to your submitted UUID list. Validate
its size, uniqueness, and that `self` maps to your UUID before accepting state. Subsequent
`state` messages identify senders by these opaque lobby-specific tags. They contain neither
session tokens nor raw network information. Never guess or compute those tags yourself.

## Message envelopes

All application messages are JSON text frames. Outgoing sequences are positive safe
integers, strictly increasing **across every kind and reconnect within the visit**.
The server checks the current connection, but peers retain deduplication state across
reconnects: resetting the counter can silently discard otherwise valid updates.

```json
{"sequence":1,"kind":"A","payload":"<39 comma-separated integers>"}
```

Public-chat-only state uses the same aggregate format:

```json
{"sequence":2,"kind":"A","payload":"<39 comma-separated integers>","catchUpOnly":true}
```

`catchUpOnly` is optional, boolean, and accepted only on `A`. Omit it or use false
for live updates. It is a sender classification; the server cannot inspect Hypixel
chat to verify its provenance. Do not use it to hide inventory or newly observed facts.

Incoming/replayed state:

```json
{"type":"state","sender":"<opaque-tag>","sequence":2,"kind":"A","payload":"..."}
```

The delivery hint is not relayed. Clients supporting the aggregate version can consume catch-up snapshots;
old senders without the hint continue broadcasting as before. Old backends ignore the
new optional field and retain their previous broadcast behavior. The delivery hint is independent of aggregate versioning. Version 3 appends held coins
at index 38; all existing field indexes are unchanged.

| Kind | Payload | Retained for replay? |
| --- | --- | --- |
| `A` | 39-integer v3 aggregate below (38-integer v2 also accepted) | Latest per active sender |
| `N` | One nest bitmask | OR-merged per sender |
| `U` | Six integers for feeder usage | No; also include current usage in `A` |
| `S` | Four Sparkling mask words | OR-merged per sender |
| `B` | `fd`, `cd`, `id`, or `hd` | Latest per code per active sender |
| `H` | JSON-string coordination operation | Separate authoritative snapshot; see guide |

`B` and `H` require the advertised capabilities. Their full semantics and the separate
object-valued `hotspots` response are in [the integration guide](PARTY_SYNC.md).
Neither kind accepts `catchUpOnly`. Numeric field validation below applies to `A/N/U/S`.

Validate lengths, integer bounds, sender membership and version before mutation. Never
use one global incoming sequence watermark: different senders have independent sequences,
and replayed kinds may arrive in a different sequence order. Dedupe by sender/sequence;
use per-sender/per-kind freshness for replaceable inventory and monotonic union for facts.
On socket replacement, invalidate callbacks/queues from the old socket.

## Aggregate `A`: exact zero-based field layout

Inventory counts are latest snapshots, not deltas. Discovery counts are per player,
starting supply plus personally collected floor drops, not totals copied from teammates.
An absent `A` means **unknown inventory**, not zero items. Neither version can express
unknown values within a partially implemented aggregate: do not send dummy zero-filled
inventories for fields your adapter does not track. Start with `N`/`S` only if needed,
or agree on a future capability/version extension for partial aggregates.

| Index | Field | Accepted range / interpretation |
| --- | --- | --- |
| 0 | Lime Gem | 0–256; personal discoveries |
| 1 | Orange Gem | 0–256; personal discoveries |
| 2 | Purple Gem | 0–256; personal discoveries |
| 3 | Icebreaker | 0–256; personal discoveries |
| 4 | Shining Coin | 0–256; personal discoveries |
| 5 | Soothing Incense | 0–256; personal discoveries |
| 6 | Yogi Berry | 0–256; personal discoveries |
| 7 | Wriggleworm | 0–256; personal discoveries |
| 8 | Bag of Seeds | 0–256; personal discoveries |
| 9 | Forest feed floor drops collected | 0–64; excludes starting feed |
| 10 | Held Bag of Seeds | 0–64 |
| 11 | Held Wriggleworm | 0–64 |
| 12 | Held Yogi Berry | 0–64 |
| 13 | Feed consumed / spawn-cycle counter | 0–256; shared progress, not sum across reporters |
| 14 | Remaining observed Bluebirds | 0–256 |
| 15 | Remaining observed Parakeets | 0–256 |
| 16 | Remaining observed Macaws | 0–256 |
| 17 | Feeder type | -1 empty, 0 seeds, 1 worm, 2 berry |
| 18 | Feeder stack count | 0–64 |
| 19 | Held Lime Gems | 0–64 |
| 20 | Held Orange Gems | 0–64 |
| 21 | Held Purple Gems | 0–64 |
| 22 | Held Soothing Incense | 0–64 |
| 23 | Placed-gem mask | 0–7; bit 0 lime, bit 1 orange, bit 2 purple |
| 24 | Candles lit | 0–4; shared progress, take maximum |
| 25 | Gemzie door open | 0 or 1 |
| 26 | Doomspiral spawned / objective complete | 0 or 1 |
| 27 | Doomspiral caught | 0 or 1 |
| 28 | Wumpa spawned / awake | 0 or 1 |
| 29 | Wumpa caught | 0 or 1 |
| 30 | Doomspiral retreated | 0 or 1 |
| 31 | Gemzie caught | 0 or 1; at least one catch, not three Gemzie catches |
| 32 | Wumpa retreated | 0 or 1 |
| 33 | Protocol version | Exactly 3 for 39 fields; legacy 2 for 38 fields |
| 34 | Snoozle wall completion mask | 0–31; 5 canonical slots |
| 35 | Troodon wall completion mask | 0–7; 3 canonical slots |
| 36 | Mound completion slots 0–15 | Unsigned 16-bit word, 0–65535 |
| 37 | Mound completion slots 16–31 | Unsigned 16-bit word, 0–65535 |
| 38 | Held Shining Coins | 0–64; current inventory, v3 only |

Version 3 includes coins in every `A` snapshot. For a legacy v2 sender, held coins
are unknown: index 4 is cumulative personal discoveries and must not substitute
for index 38. Backend and updated SafariUtils accept both exact version/length pairs.
V2-only clients cannot read v3 aggregates; update both mods before testing full sharing.
See [the Shining Coins addendum](PARTY_SYNC_SHINING_COINS.md) for the migration checklist.

### Merge rules

- Store inventories/discovery counters by reporter; replace their current inventory,
  never add successive snapshots together. Sum distinct personal feed supplies where
  appropriate. Retain unknown/known status separately.
- OR masks and terminal encounter flags within the visit. A later zero cannot reopen
  a completed objective. Shared candles use maximum, not the sum of every report.
- `U` is `[used, bluebirds, parakeets, macaws, feederType, feederCount]`, with the same
  bounds as indexes 13–18. More `used` advances the feeder generation and replaces
  bird counts. At equal `used`, take minimum bird counts so delayed snapshots cannot
  resurrect caught birds; older usage must not replace newer feeder state.
- Terminal encounter state affects objective display only. It must not fabricate a
  personal catch, reward, bestiary increment, PB, or a complete biome. Gemzie's caught
  flag does not mean all three Gemzies were caught. Retreat is not a successful catch.
- Preserve source provenance. Do not echo received discoveries as newly local facts.
  Shared progress fields intentionally describe the same objective and are merged,
  not counted separately for each person who heard the same event.
- SafariUtils treats received party observations as logically known information in
  Safe Mode. Local unseen world state still follows local visibility rules.

## Canonical masks

`N` is a decimal integer nest mask (0–8191). A set bit means confirmed checked/resolved
for that visit. Walls/mounds similarly mean the source slot has been resolved, not just
that a block briefly disappeared outside render distance. Preserve existing local
completion criteria: wall completion can follow a resident catch or positive empty
confirmation; mound completion uses positively confirmed source resolution.

Nest and mound slots sort integer block positions by **x, then y, then z**, ascending.
Bundled half-block centers are floored to integer coordinates first (including negatives).
Wall slots use their declared order, not sorting. Do not derive bits from proximity,
entity IDs, insertion order, or whichever positions loaded first.

### Nest slots

| Bit index | Block position (x, y, z) |
| --- | --- |
| 0 | -22, 68, 39 |
| 1 | -17, 68, 41 |
| 2 | -13, 103, 21 |
| 3 | -11, 103, 13 |
| 4 | -9, 81, 58 |
| 5 | -1, 81, 60 |
| 6 | -1, 84, 9 |
| 7 | 15, 88, 31 |
| 8 | 16, 114, 87 |
| 9 | 18, 114, 79 |
| 10 | 20, 88, 33 |
| 11 | 23, 70, 46 |
| 12 | 25, 70, 41 |

### Mound slots

| Bit index | Block position (x, y, z) |
| --- | --- |
| 0 | -137, 60, 57 |
| 1 | -135, 40, 53 |
| 2 | -130, 62, 42 |
| 3 | -129, 60, 47 |
| 4 | -128, 41, 60 |
| 5 | -128, 58, 66 |
| 6 | -127, 62, 41 |
| 7 | -122, 40, 55 |
| 8 | -121, 43, 24 |
| 9 | -120, 41, 64 |
| 10 | -119, 39, 78 |
| 11 | -118, 40, 50 |
| 12 | -114, 43, 32 |
| 13 | -113, 46, 35 |
| 14 | -110, 62, 31 |
| 15 | -108, 41, 23 |
| 16 | -107, 61, 50 |
| 17 | -104, 62, 42 |
| 18 | -99, 39, 79 |
| 19 | -99, 48, 94 |
| 20 | -99, 62, 40 |
| 21 | -97, 40, 26 |
| 22 | -88, 39, 65 |
| 23 | -84, 40, 30 |
| 24 | -82, 60, 64 |
| 25 | -80, 57, 61 |
| 26 | -80, 62, 65 |
| 27 | -70, 40, 62 |
| 28 | -70, 56, 58 |
| 29 | -67, 51, 45 |
| 30 | -61, 45, 57 |
| 31 | -56, 61, 61 |

### Wall slots

| Bit index | Snoozle position | Troodon position |
| --- | --- | --- |
| 0 | -126, 39, 74 | -104, 81, -95 |
| 1 | -114, 39, 87 | -131, 79, -62 |
| 2 | -70, 39, 68 | -109, 90, -27 |
| 3 | -96, 40, 17 | — |
| 4 | -95, 40, 42 | — |

## Sparkling `S`

Four decimal words: `[foundLow, foundHigh, caughtLow, caughtHigh]`.
Low words cover species 0–29 (0–1073741823); high words cover 30–36 (0–127).
Union found/caught masks monotonically for the visit. Species-level outstanding state
is `found & ~caught`. Publish only local discovery/catch evidence, not re-exported masks.

This is species-level synchronization, **not individual Sparkling tracking**. Two
Sparklings of one species cannot be represented separately. SafariUtils retains its
local per-entity tracking: a remote species catch must not delete another individually
observed live Sparkling. Never use this mask to increment permanent collection counts.

| Species bit | Species |
| --- | --- |
| 0 | Cavernfish |
| 1 | Flitter |
| 2 | Shyworm |
| 3 | Driftling |
| 4 | Chuckwalla |
| 5 | Rockmite |
| 6 | Scrappy |
| 7 | Snoozle |
| 8 | Gemzie |
| 9 | Strongarm |
| 10 | Tepid |
| 11 | Polaris |
| 12 | Shuddersquid |
| 13 | Billygoat |
| 14 | Mantis Shrimp |
| 15 | Nozzlenose |
| 16 | Troodon |
| 17 | Wumpa |
| 18 | Areita |
| 19 | Bloodbat |
| 20 | Duplico |
| 21 | Gazer |
| 22 | Litterbug |
| 23 | Solsnatcher |
| 24 | Gimmiegold |
| 25 | Hideonwall |
| 26 | Hideyho |
| 27 | Doomspiral |
| 28 | Foxtrot |
| 29 | Bluebird |
| 30 | Honeybug |
| 31 | Treefrog |
| 32 | Woodchucker |
| 33 | Fluffling |
| 34 | Hideonfloor |
| 35 | Parakeet |
| 36 | Macaw |

## Performance, failure handling, and privacy

- Use one shared asynchronous network client/socket, bounded incoming queues, and
  game-thread application of validated messages. Never scan world entities or fetch
  API data in response to every relay packet.
- SafariUtils samples existing objective caches twice per second and coalesces outgoing
  aggregate changes for 500 ms. Nest and Sparkling facts use compact bounded masks.
- Current server bounds: 4096 UTF-8 bytes per outgoing message; payload at most 3072
  characters; 40 messages per 10 seconds per connection; at most 128 active sockets
  per lobby object. Global/account admission adds further limits. These are ceilings,
  not targets; normal traffic should be far lower.
- Socket leases expire within 10 minutes. Reconnect using the cached valid token (or
  refresh if needed), then publish current local snapshots. Ping approximately every
  25 seconds. Back off after failures (SafariUtils uses 2 seconds up to 30 seconds);
  optional jitter is fine. Do not tight-loop 401/403/429/503 failures.
- On 401 refresh the service token with backoff. On 403 check local UUID/token identity;
  on 400 validate lobby/roster. On 429/503 respect Retry-After when supplied and retry
  later. Other account/global limits can reject a connection without breaking gameplay.
- Distinguish unknown inventory from confirmed empty inventory. Losing the socket must
  not freeze the game or reset locally observed progress. Mark stale inventory as such;
  irreversible objective evidence may remain valid for the current visit.
- Do not log bearer tokens, complete headers, private session credentials, or network
  addresses. Diagnostics should identify stage, message kind, delivery class, counts,
  reconnects, and validation failures without needing complete payload dumps.
- New clients must continue working locally if the backend is unavailable. Never wait
  for the relay to process a catch already received directly from Hypixel.

## Suggested integration order

1. Implement a small transport adapter, independent from rendering. Follow the new
   authority/fallback rules in the integration guide; do not run competing allocators.
2. Validate the welcome mapping and exact-roster behavior in a two-player mixed party.
3. Add `N` and/or `S` if those existing trackers can supply the exact semantics. This
   provides useful sharing without pretending to know unimplemented inventory fields.
4. Add full `A`/`U` support after mapping all fields to real local state. Apply idempotent
   merges and the public-chat/catch-up distinction. A future partial-state protocol
   would require coordinated versioning; it is not part of version 3.
5. Test late join, reconnect, ticket-trading roster expansion, and transitions between
   visits before enabling broadly. Public rollout should be coordinated with Serkosea
   so capacity and protocol support are agreed rather than assumed.

## Mixed-mod acceptance checklist

- Same lobby and roster connects; different lobby or mismatched roster does not share.
- Unknown/nonparticipating players do not appear to hold zero confirmed items.
- Two clients observing one event do not double counts or replay alerts.
- Private inventory/objective observations update the other mod live.
- Public encounter chat updates local state immediately; catch-up-only uploads do not
  create redundant peer messages. A private inventory change in that same batch still
  reaches peers normally.
- Join/reconnect after a completion receives the retained snapshot while reporters are
  connected. Reconnect restores state without inventing personal catches or PBs.
- A late snapshot cannot restore already-consumed progress or a caught local entity.
- A Sparkling found before ticket use reaches the other participant; using a ticket,
  catching it, or leaving ends the appropriate reminder. Duplicate-species local
  entities stay separate from the coarse species mask.
- Disconnect, close the game, rejoin, and start another run: no prior-visit state leaks.
- Simulate malformed fields, out-of-order/duplicate messages, offline backend, and rate
  limiting: local gameplay remains usable and reconnect attempts stay bounded.

## Reference implementation locations

Within the SafariUtils source tree:

- `src/main/java/dev/serko/safariutils/api/PartySyncProtocol.java`: sizes/version.
- `.../backend/RemotePartySyncProvider.java`: WebSocket transport and validation.
- `.../backend/RemoteSession.java`: shared claimed-UUID service session.
- `.../client/PartySyncRoster.java` and `PartyItemSync.java`: visit membership, aggregate
  production and application, objective HUD integration.
- `.../client/PartySnapshotBatch.java`: bounded coalescing and live-delivery precedence.
- `.../client/PartySparklingState.java`: species masks and local/remote provenance.
- `.../client/WallTracker.java`, `MoundSpotter.java`, `StaticWaypointCatalog.java`:
  canonical objective slots and evidence rules.
- `src/main/resources/assets/safariutils/static-waypoints.json`: position catalog.
- `.../data/Critters.java`: species-bit ordering (this document lists the current order).

The hosted service implements validation, exact-roster room separation, admission,
retention, deduplication, and relay delivery. peer does not need its private source or
any service secret to implement a client. The optional delivery hint must remain
backward compatible; field reordering or changed mask semantics require a coordinated
protocol revision and tests on both implementations.


## Verification of the delivery extension

The implementation has backend regression and local Cloudflare Durable
Object/WebSocket runtime coverage for live delivery, silent catch-up retention,
reconnect replay, and authoritative hotspot coordination. All nine standalone build variants and SerkoAddons checks
passed, including shared-class parity. These checks do not replace a mixed-party
in-game test with peer client; this document is an invitation to review and integrate,
not a claim that peer client already implements this protocol.

## Optional room presence (2026-11-03)

The server sends `{"type":"presence","members":["<roster tag>"]}` after a socket joins or leaves. Members are currently active sockets in the same party room, including the receiver; they are not the full party roster. Resolve these tags using the welcome mapping. The list contains at most four unique tags. Ignore unknown message types on clients that do not display presence. This does not require new client requests or change the aggregate/assignment protocol. Clear displayed presence when the local connection closes; disconnects are observed by the server or lease expiry, not guaranteed instantaneous.


## Assignment eligibility and renewal (2026-11-03)

Optional boolean `ticketed` in the `H` hello distinguishes attendance from ticket
activation. Send false before activation and true afterward (resend hello immediately
on the change). Omission preserves legacy behavior. Backend allocation and party-chat
notifications exclude explicitly ticketless or departed members; Sparkling and other
room traffic remain available. A completed assignment shown by a connected display is
remembered for this run across lease renewal/disconnect, avoiding redundant instructions.
An explicit leave cannot be undone by another client's stale `present` list.
