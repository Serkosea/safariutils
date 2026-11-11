# Party sync update — 3 November 2026

For peer / Safari peer client. Same endpoint, authentication, roster rules and outbound
`A/N/U/S/H/B` envelopes. These are additions to the existing two protocol documents.

## 1. Lobby identity

The backend now treats `mini147AH`, `m147AH` and case variants as the same lobby.
Either tab-list or scoreboard ID is accepted. Send the ID alone, without `Server:`.
Different numeric/suffix IDs remain separate; `mega` IDs are not collapsed.

## 2. Ticket use and reconnects

Include **`ticketed`** in every `H` hello:

```json
{"version":1,"action":"hello","display":true,"ticketed":false,"leader":false,"present":["<UUID>"],"locked":{}}
```

- Send `false` before confirmed ticket use, then immediately resend with `true`
  when the ticket is accepted. Only send your actual hotspot once ticketed.
- `display` still means your mod displays backend assignments; do not turn it off
  merely because the player has not used a ticket yet.
- Known unticketed players are excluded from allocation and assignment notices.
  Omission is accepted for older clients, so explicitly send the new field.
- Keep shared Sparkling reception active before ticket use.
- A socket reconnect/lease renewal is **not** a Safari departure. Retain local
  visit facts, sequence, finalized assignments and sent notification IDs; reconnect,
  send hello with existing `locked` choices, and republish current local snapshots.
- Send `H` `leave` on actual Safari exit, including exits without ticket use.
  Stop accepting visit updates during transfer; reset local visit data for a new visit.
- The backend retains knowledge that a connected display already received its role.
  Renewal no longer makes that player eligible for a repeated party-chat instruction.
  Notices remain leader-only, with their existing stable notification IDs.

## 3. Atomic Sparkling catch-up

Welcome now advertises **`room-sparklings-v1`**. When present, consume this separate
server envelope instead of individual relayed `state` messages with `kind:"S"`:

```json
{"type":"sparklings","payload":"3,0,1,0"}
```

The four comma-separated integers retain the existing `S` order:
**found low, found high, caught low, caught high** (30 low bits, remaining species
in high bits). This example has species bits 0 and 1 found, bit 0 caught; only bit 1
is active. This server envelope has no sender or sequence. It belongs to the current
validated socket/visit; reject callbacks from replaced sockets.

- It arrives immediately after welcome, **before** individual replay, and whenever
  the room union changes. Apply found AND caught together before producing alerts.
- Merge monotonically: `active = found & ~caught`. A stale found-only snapshot must
  never restore a caught species. Continue publishing your own local `S` facts using
  the normal shared sequence; do not republish received facts as local observations.
- The backend carries the combined state on matching active party connections, so
  catches survive the reporter leaving while another participant remains connected.
- Late guests get uncaught discoveries immediately. A discovery caught before their
  arrival produces no found alert or use-ticket reminder. Clear reminders when the
  discovery is caught, a ticket is used, the entry window ends, or the player leaves.
- A later local sighting of an already-shared discovery must not announce it again.
  Do not treat your own echoed local discovery as someone else's earlier discovery.
  Individual markers remain locally tracked: species masks do not identify two
  separate Sparkling individuals of the same species.
- Without this advertised feature, retain the previous per-sender `S` receiver.

## 4. Feeder and coins

No new aggregate layout: **A v3 has 39 integers**, version at index **33**, current
held Shining Coins at index **38** (0–64). Zero means none, not unknown. Legacy v2
has 38 integers and does not report held coins; index 4 is not a held-coin count.

When the final feed is consumed, publish consumed feed count at index **13**,
feeder type **17** (`-1` when empty), and feeder count **18** (`0` when empty).
Receivers must apply zero at a newer consumption count, or a positive-to-zero
transition from the same reporter at the current count. An unobserved empty
snapshot must not erase a newer known fill; older consumption counts stay stale.
