# Shining Coins — party-sync aggregate v3

2026-11-03 · Addendum to `PARTY_SYNC.md` and `PARTY_SYNC_REFERENCE.md`.

Coins are part of the **full `A` payload**, not a separate message or optional field.
Authentication, room identity, roster mapping, sequence rules, hotspot coordination,
rate limits and `N/U/S/B/H` messages are unchanged.

## Required changes

| Item | Before | Now |
| --- | --- | --- |
| `A` integer count | 38 | **39** |
| Protocol version, index `33` | 2 | **3** |
| Index `38` | Absent | **Held Shining Coins**, integer `0–64` |

All indexes `0–37` keep their original meaning except the version value at `33`.
In particular, **index 4 stays cumulative personal Shining Coin discoveries**
(starting supply + personally collected floor drops). Do not use it for held coins.

Example envelope: zeroed objectives, version 3, two coins currently held:

```json
{"sequence":1,"kind":"A","payload":"0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,3,0,0,0,0,2"}
```

## Sending and applying

- Read the current held count from your existing inventory tracker. Include it in
  every `A` snapshot, including zero. Do not infer consumption from Gimmiegold catches:
  dropping or picking up a coin also changes inventory.
- A coin-count change schedules the normal **live** aggregate update. Use existing
  500 ms coalescing, duplicate suppression and the shared increasing sequence.
  If a public-chat-only update is also pending, the inventory change takes live priority.
- Resend your full current aggregate after reconnecting. The backend retains the
  latest `A` per active sender and replays it to joining peers, including coins.
- Replace each sender's held count; do not add snapshots or take the maximum.
  `3 → 1 → 0` must clear the count. Receiving the same snapshot twice must not add coins.
- Counts belong to the current visit and sender. Clear them with your other objective
  state on visit exit; don't carry them into another run or derive catches/stats from them.

## Compatibility

Backend and updated SafariUtils accept exactly **38 fields/version 2** or
**39 fields/version 3**. Reject mismatched lengths/versions and out-of-range coin counts.
For v2, coins are **unknown**, never zero and never inferred from index 4. A later v2
snapshot replaces a previous known coin count with unknown.

Existing v2-only clients do not understand v3 aggregates. Both mods should update
before testing the full shared inventory; there is no automatic down-conversion.

## SafariUtils HUD behavior (your visuals can differ)

Haunted player rows show **Incense → Coins**, using their item icons. A player appears
when at least one held count is known and positive. A v2 player with incense can show
`?` for coins; an entirely unknown/empty player does not create a row. Coins remain
visible after Doomspiral completion because they are a separate usable objective item.
Coin possession does not change Doomspiral's completion status.

## Quick mixed-mod test

1. Join the same run with both updated mods; verify starting coins beside the right player.
2. Pick up, drop/re-pick up, and spend a coin. Both clients should follow the inventory,
   including the final zero, without adding successive counts together.
3. Reconnect one client while the other holds coins; verify the replayed count.
4. Complete Doomspiral while coins remain; verify the coin row stays visible.
5. Leave and enter another run; verify old counts do not survive.

Reference implementation: `PartySyncProtocol.java`, `PartyItemSync.java`,
`RemotePartySyncProvider.java`, `PartyObjectiveHud.java`. No new endpoint or client
request type is required. Live cross-mod verification is still pending.
