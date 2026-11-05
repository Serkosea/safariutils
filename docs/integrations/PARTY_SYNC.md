# Shared Safari party sync — implementation guide

Current lifecycle/replay additions: [concise 3 November update](PARTY_SYNC_UPDATE_2026-11-03.md).

Agreed with JohnCEO on 2026-11-02. Both mods keep their own UI and trackers.
The service is `https://api-dev.serkosea.dev`; no hosting, API secret, or SafariUtils
library is needed by Mutuals. Mixed-mod in-game validation is still required.

## Connect once, share existing observations

1. POST `{"uuid":"<compact lowercase player UUID>"}` to `/v1/auth/uuid/session`.
   Reuse the returned short-lived bearer token.
2. Open `wss://api-dev.serkosea.dev/v1/sync` with the authorization, lobby, player,
   and complete party-roster headers documented in [the wire reference](PARTY_SYNC_REFERENCE.md).
   Roster UUID sets must match; roster membership alone is not run attendance.
3. Validate `welcome.self` and `welcome.roster` against your submitted roster/order.
   The welcome now advertises `features: ["room-sparklings-v1", "hotspots-v1", "biome-reports-v1"]`.
4. Keep one increasing `sequence` across **all** kinds and reconnects within the visit
   (positive JavaScript-safe integers). Resetting on reconnect can collide with peer deduplication. Send
   `{"sequence":1,"kind":"H","payload":"<JSON encoded as a string>"}` for the
   coordination operations below. `A` now uses aggregate v3 (39 integers, held Shining Coins
   appended at index 38); `N/U/S` and coordination are unchanged. See the
   [Shining Coins addendum](PARTY_SYNC_SHINING_COINS.md).

The objective protocol shares held quest items, source progress, checked nests,
resolved walls/mounds, gem/candle progress and Sparkling discoveries. Public encounter
chat is processed locally; `A` uploads with `catchUpOnly:true` preserve that state for
late joiners without repeating it to current peers. See the reference for exact indices,
bit order and merge rules. Never turn shared progress into fabricated catches or PBs.

## Hotspot operations (`H`, version 1)

Send `hello` first on every connection, then resend known actual reports; afterward send
changes only. UUIDs below are compact lowercase hex. These examples are inner payloads,
each serialized as a JSON **string** in the outer `H` envelope.

```json
{"version":1,"action":"hello","display":true,"leader":false,"present":["<confirmed-present UUID>"],"locked":{}}
{"version":1,"action":"report","player":"<UUID>","biome":"FOREST"}
{"version":1,"action":"ack","id":"<run ID>:<assigned player UUID>"}
```

- `display`: this client will display and obey backend assignments. False means it
  may need a party-chat instruction. Do not claim true while running an independent allocator.
- `leader`: whether the local player is the actual party leader. Send changes promptly.
- `present`: only roster members positively confirmed in this Safari, not the whole
  party list. The sender is confirmed by its own hello. Repeat hello on changes,
  reconnect, or about every 15 seconds; it is not a per-tick heartbeat. This is additive
  attendance evidence: omitting someone later does not remove them. Never submit an
  uncertain/transitional party roster as a confirmed membership change.
- `locked`: finalized choices already displayed through backend or chat fallback,
  mapping UUID to `FOREST`, `CAVERN`, `ICY`, or `HAUNTED`. Send these **before** reports
  on recovery. Never replace them with new local calculations.
- `report`: own actual hotspot or a correctly parsed party member's actual hotspot.
  First valid report per member wins. Reports alone do not confirm attendance.
- `leave`: send `{"version":1,"action":"leave"}` only on an explicit Safari visit exit,
  flushing it before closing the socket. A transport failure/reconnect is not a leave.
- `ack`: leader acknowledges that the instruction was actually submitted to game chat.
  Do not acknowledge merely queuing it behind another command.

The server sends a separate envelope (not a peer `state`):

```json
{"type":"hotspots","payload":{"version":1,"run":"<server run ID>","revision":7,"assignments":{"<UUID>":"FOREST"},"fairness":{"<UUID>":2},"leader":null,"notifications":[],"conflict":false,"initialDone":true}}
```

Here `payload` is an object, not a JSON string; `leader` is a UUID string or JSON null.
Validate UUIDs, bounds, biome values and increasing revision for the same run. Render
`assignments` directly. Reconnect hello returns current assignments and fairness.
Fairness counts confirmed hotspot wins, not total visits; unknown-at-allocation reports
are not credited retroactively. Imported fallback locks do not award wins. Missing counts
mean zero. Keep local finalized locks even when a snapshot omits them; reject a moved
player or a different player taking a locked biome. An unexpected run ID during the same
visit needs reconciliation, not an automatic reset of displayed assignments.

## Allocation rules

- Start a two-second window after the **third distinct confirmed player's report**,
  or everyone reports for parties of one/two. Allocate no later than five seconds
  after the first confirmed report. Duplicate reports never restart the clock. These
  deadlines use server receipt time; without any confirmed report the timer has not started.
- Contested hotspot: fewest prior wins, then Java signed `String.hashCode()` of the
  compact lowercase UUID, then UUID lexical order. Fill remaining biomes in
  `FOREST, CAVERN, ICY, HAUNTED` order, with displaced/unknown players in UUID-hash order.
- Only confirmed attendees receive a biome. Unknown hotspots receive free biomes,
  without hotspot priority. Late arrivals are batched for 250 ms.
- Final choices never move, including when a late player has higher fairness priority.
- Fairness resets on confirmed party membership changes. A new Safari lobby or a
  transport reconnect with unchanged membership retains history. Stale older roster
  connections cannot roll membership back. Do not infer membership changes from
  temporarily missing entities, unknown roster state, or a socket disconnect.
- Solo clients need no coordination socket operations for assignment.

## Leader-only instructions and fallback

Each notification is `{"id":"<run>:<player>","player":"<UUID>","biome":"ICY"}`.
The server supplies them only while the leader is connected and the target lacks an
active assignment display and has not already received its assignment through a display
in this run. Ticketless/departed targets are excluded. A two-second display grace after each player's assignment avoids racing delayed hello messages, including late arrivals.
Snapshots are broadcast to all matching clients. Gate announcements on **both** local
party leadership and `snapshot.leader == self`; receiving a notification is not permission
for a non-leader to send it. Only the current leader sends the instruction, using either mod's wording and the
normal shared command cooldown. Remember sent IDs locally and acknowledge them.
A reconnect does not transfer announcement duty; only an actual leadership change does.
IDs provide duplicate suppression, not an impossible exactly-once chat guarantee if
someone disconnects between submitting chat and acknowledgment.

While backend authority is active, do not send hotspot reports or completion codes
into party chat. Only send requested non-display assignment instructions.
If unavailable before adopting backend authority, the client can use chat fallback
(SafariUtils waits up to seven seconds for initial authority). Once either route
finalizes choices, keep them fixed. A later backend recovery imports those choices;
it must not replace them. After a nonconflicting recovery accepts those locks, use backend
coordination and stop chat coordination while keeping the same assignments.
If `conflict:true` is returned, preserve shown assignments
and stop new automatic assignments/instructions. Do not silently pick a winning client.
If a backend-owned run disconnects, retain its result and reconnect; do not start a
competing chat allocation midway through that run.

Missing advertised capabilities or `{"type":"hotspots-unavailable"}` means coordination
is unavailable. Keep finalized choices, use bounded reconnect/backoff, and only start
chat fallback if backend authority was never adopted. Ignore unknown extension frames.

Older Mutuals builds that calculate independently have not adopted this contract.
Do not assume their local assignment display obeys the backend just because they send
`[SM]` or `[SU]` chat messages. Test the new adapter in both mods together.

## Completion reports (`B`)

Send `{"sequence":8,"kind":"B","payload":"fd"}` (also `cd`, `id`, `hd`).
These use ordinary peer `state` envelopes and are retained per reporter/code for replay
while that reporter remains connected. They are not durable completion history.
Apply them as coordination evidence only. Keep completion criteria mode-aware and
unchanged from our agreed chat protocol; backend transport does not declare a biome
finished on its own. Receive catch-up reports before ticket use without inventing catches.
Use chat fallback only when backend coordination was not adopted for this visit.

## Bounds, privacy and testing

Reuse shared auth, reconnect backoff and bounded network queues. Serialize asynchronous
socket sends (including leave before close); do not block the game thread. Existing socket/message
budgets apply to H/B too: 4096 bytes/message, 40 messages/10 seconds, payload <=3072
characters. These are ceilings, not targets. No extra Hypixel API requests are made.
Account UUIDs are public identity claims, **not verified ownership**. H snapshots contain
roster UUIDs; tokens, IPs and Minecraft session credentials must never appear in logs.

The authority currently caps retained groups at 4096, 32 retained lobby records per group,
45 minutes of inactivity per run, and 30 days of group history. Timers query indexed due records in bounded batches, rather than scanning histories.
This is bounded coordination storage, not a permanent game record. Lobby plus roster identifies the
retained run; the server-issued run ID distinguishes the snapshot generation. Clear
visit data when leaving/changing Safari. Explicit departure by all confirmed attendees
retires the assignment run, allowing a later visit to reuse the same lobby ID. If a participant disappeared without an explicit departure, a reused server ID within
the retention window cannot independently prove a new Hypixel run; do not invent or silently reset a generation.
`leave` only removes its sender. Non-mod attendance added through `present` cannot be
cleared by another player's leave or by omission from hello, so it can also prevent
all-left retirement until expiry. Test reused lobby IDs explicitly; this remains a
protocol limitation rather than a guaranteed fresh-run detector.
Service/admission failure must preserve local gameplay and finalized choices.

Test: same-hotspot parties, staggered reports, unknown/non-mod arrivals, late arrivals,
leader changes, reconnects before/after allocation, unchanged/new rosters across lobbies,
and fallback recovery. Check both mods display the same result and only the leader
announces each non-display assignment. Transport tests cover timing, alarms, membership,
privacy, conflicts and replay; they do not replace mixed-mod in-game testing.


## Reconnect and ticket eligibility clarification (2026-11-03)

`hello` may now include `"ticketed": false` while visiting before ticket use, then
`true` immediately after activation. Presence still permits Sparkling sync, but a
known ticketless player is excluded from biome allocation/instructions. Omission
retains legacy eligibility; both mods should provide the field for precise behavior.
An explicit `leave` stops instructions and stale peer presence cannot undo it.
Assignments already supplied to an enabled display remain known during socket
renewal, so the backend does not request another party-chat instruction on reconnect.
No changes to locked assignments, fairness history, notification IDs, or the A v3 layout.
