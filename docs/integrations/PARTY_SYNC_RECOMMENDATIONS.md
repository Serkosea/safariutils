> Historical planning record. The implemented contract is [the 3.8.0 guide](PARTY_SYNC_3.8_IMPLEMENTATION.md).

# Party sync protocol recommendations

Proposed clarifications for `PARTY_SYNC_HOTSPOT_UPDATE.md` and `PARTY_SYNC_CRITTER_COMPLETION.md`.
These are recommendations for agreement, not changes already deployed. The intended behavior is the requirement; the proposed payload layouts and internal architecture are open to improvement. Both clients may use different internal trackers while sharing one precisely defined wire contract.

## 1. Hotspot assignment

### Expected players and allocation timing

- Use the confirmed party roster as the expected group. Remove a player from that group only on confirmed departure, not a temporary WebSocket disconnect or missing tab-list update.
- Allocate immediately once every expected player is confirmed present, admitted to the run, and has supplied an actual hotspot.
- Otherwise allocate 30 seconds after the first eligible actual-hotspot report. Duplicate reports, retries and reconnects never restart this deadline.
- At the deadline, assign only players with both attendance and admission evidence. Tab-list presence alone is not admission.
- A valid current-run actual-hotspot report establishes admission once attendance is confirmed. Retain reports arriving before hello until those conditions can be reconciled.
- After initial allocation, process late eligible arrivals in 250 ms batches, assigning only remaining biomes. Never reshuffle finalized assignments.
- Apply the same allocation timing to the chat fallback. Once either backend or fallback assignments are finalized, reconnecting must preserve them rather than calculate a competing result.

### Missing hotspot at the fallback deadline

An admitted, present player who has not reported an actual hotspot receives a remaining biome using the existing UUID ordering. They receive no hotspot fairness win unless their actual hotspot was known and matched the assigned biome when allocation finalized.

Display a finalized backend assignment even if the local actual hotspot is unknown. The own-hotspot guard should prevent premature local calculation, not hide a valid authoritative assignment. The UI can show the assigned biome and omit the unknown actual hotspot.

### Manual reports and announcements

- Keep the proposed aliases and the first-30-seconds parsing window. Single-letter aliases must occupy the entire message.
- Accept a standalone biome/alias, an explicit hotspot declaration, or a copied Hypixel hotspot line. Reject ambiguous multi-biome messages, generated assignment instructions, and ordinary movement messages such as “heading to forest.”
- Forward reports with the identified roster member, not the forwarding client's identity as the subject. The backend still validates the sender and reported player against the room roster.
- Retry valid unassigned reports every two seconds; stop once the assignment is confirmed, the visit ends, or the report is superseded. An identical retry must not change fairness or the allocation deadline.
- Determine instruction recipients from connected assignment-display capability, not whether their hotspot was reported manually.
- Keep instructions leader-only. Acknowledge a notification only after its chat message was actually submitted. Retain notification-ID deduplication through reconnects and leadership changes.
- Keep the existing fairness rule, UUID tie-break and remaining-biome order. Do not send fairness counters or a full assignment map in party chat while backend coordination is active.

## 2. Shared run identity

Use the backend's existing generated `hotspots.run` UUID as the authoritative `runId`. Do not derive it from client clocks, ticket-use timestamps or the lobby name alone.

Add two fields to the H hello:

```json
{
  "version": 1,
  "action": "hello",
  "visitId": "<client-generated UUID for this Safari visit>",
  "resumeRunId": "<previous server run UUID, or null>",
  "ticketed": true,
  "display": true,
  "leader": false,
  "present": [],
  "locked": {}
}
```

The example illustrates fields only; `present` and `locked` retain their existing validated roster-based contents in a real message.

- Generate `visitId` once on entering a genuinely new Safari visit. Keep it across socket reconnects and lease renewals. A transport interruption alone must not generate a new visit.
- Return the reconciled `runId` before clients consume run-scoped snapshots. The existing hotspot snapshot's `run` field carries this same identifier.
- A known visit with a matching `resumeRunId` resumes that run. A late arrival without a resume ID joins the run confirmed by current participants in the same lobby and party.
- A confirmed new run gets a new server UUID. Keep fairness history separate, keyed by party membership, so a new run does not reset fairness.
- Scope reports, completion evidence, assignment locks and notification IDs to `runId`. Carry `runId` in the C inner payload and its relayed state envelope; reject messages for a different run. Include it in subsequent H operations after the initial hello reconciliation.
- Bind pending pre-hello reports to the originating connection/visit and apply them only after reconciliation. Do not let delayed callbacks from an old visit modify the new run.
- If all participants disconnect and a new arrival cannot establish whether a retained room is the same run, withhold old assignments/completion evidence until reconciled. A reused lobby ID is not proof. A valid returning participant's resume information can resolve that uncertainty.

These run fields extend the proposed wire contract and need agreement on both clients before being required. Older clients must not be treated as providing current-run proof merely because they reuse the same lobby.

## 3. Per-critter completion: kind C

Use the existing authenticated connection, shared sequence, mapped sender, stable 37-species ordering and envelope limits. The recommended initial format remains 37 rows of `[flags, checks, captures]`, because it is small and easy to validate; it is not a requirement to preserve that layout if both implementations agree on a better versioned contract. Keep A/N/U/S/H/B behavior unchanged except for the explicitly agreed run reconciliation additions.

### Flag meanings

| Flag | Meaning |
| --- | --- |
| `1` | The sender independently proved the complete Unique requirement for this species, including required special-source checks or a valid zero-spawn exemption. |
| `2` | The sender independently completed the qualifying Sparkling/render/source check for this species. |
| `4` | The sender independently proved the Full Clear capture requirement, source checks and remaining-population conditions. |

Flags combine with bitwise OR. A report may have positive completion flags and zero captures when a valid source check or zero-spawn exemption permits it.

### Combining evidence

- Use maximum counts across reporters, never sums of potentially overlapping observations.
- A valid flag `1` can establish Unique completion. Alternatively, real capture evidence from one reporter and a qualifying flag `2` from another may establish it, subject to that species' source rules.
- Zero captures alone never prove a zero-spawn exemption.
- Sparkling mode uses flag `2` for the species relevant to the shared collection. Full Clear uses flag `4`.
- Positive current local evidence of unfinished required sources, remaining required critters, or an uncaught Sparkling overrides remote completion. Merely not having observed the species locally is not a contradiction.
- Export only local tracker evidence. Observed loot-share catches may count as local observations, but received completion messages must not become outgoing proof, fabricated catches, rewards, PBs or history entries.

### Meaning of checks

Do not use one generic numerical check threshold across species. Until each species' counting unit is explicitly defined, use `checks` for information only and use the qualified flags for completion decisions.

The definition table should distinguish ordinary observed individuals, checked nests, resolved mounds/walls, and bird-source checks. Repeated scans of the same individual or source must not increase the count; elapsed ticks must never count as checks. The exact species-specific mapping still needs confirmation rather than being inferred from the field name.

### Replacement and expiry

- Retain the latest complete snapshot per reporter. A newer snapshot replaces all previous rows, including cleared flags or lower counts.
- Expire evidence 45 seconds after server receipt. Replay carries server-computed `ageMillis` from that original receipt; replay does not renew evidence.
- Receivers account for time spent waiting in their own delivery queue before applying or expiring the snapshot.
- Reconnect publishes newly evaluated local evidence, not an old received snapshot relabeled as fresh.
- Expiry removes that reporter's contribution. Recompute combined evidence from the remaining valid snapshots rather than keeping a permanently OR-ed completion mask.
- Clear run-scoped evidence when `runId` changes. Invalid or ambiguous run identity must not restore a previous run's completion.

### Capability and migration

Advertise `critter-completion-v1` in the welcome features. Use shared C transport only when advertised, and keep exactly one completion transport active for a room.

This deliberately differs from the proposed “no welcome feature advertisement required” rule: an explicit capability avoids silent rejection on an older backend and makes switching away from the separate completion transport deterministic.

## 4. Recommended implementation refinements

These recommendations preserve the requested behavior without requiring either client to copy the other's architecture.

### Prefer small complete snapshots over deltas

Keep a complete C snapshot initially. All 37 rows at the maximum documented values serialize to only 466 ASCII bytes with compact JSON, before run identity and the outer envelope. A flat integer array would save just 74 bytes. That saving does not justify reduced readability or a new binary codec on its own.

Full replacement also makes corrections, reconnects and dropped intermediate updates straightforward. Delta messages would require baseline revisions, missing-update recovery and reset rules. Only introduce them if measurements demonstrate a worthwhile benefit.

### One shared run boundary, not parallel completion-room identity

Reuse the existing shared room and server run UUID. Prefer advertising the reconciled run once in the session handshake, then binding the connection to it and checking it on incoming completion messages. Clients must not treat a supplied UUID as authority to join another party or resurrect an ended run.

The server should attach the authoritative run ID to relayed C messages. Validate the sender's claimed run against the connection binding before accepting it; never merely copy the supplied ID into the relay. Apply the same check to imported assignment locks and pending reports.

The reconciliation response should explicitly distinguish `resumed`, `joined`, `new` and `pending`, rather than forcing clients to infer those outcomes from empty assignments. While pending, retain fresh local observations but do not apply old remote completion or locks.

### Explicit readiness on reconnect

Provide an end-of-replay marker tied to the connection generation and run ID after sending the current assignments and unexpired completion snapshots. A client applies the initial snapshot batch before making decisions that depend on remote evidence. Subsequent live updates remain ordered after that batch.

This avoids inventing a fixed delay to guess when catch-up has finished. Do not block independent local tracking or discard discoveries while waiting. A disconnected client can continue using its existing local/fallback behavior, with finalized assignments kept locked.

### Coalesce observations, preserve event ordering

Build completion snapshots from the existing trackers; do not add another entity scan. Evaluate at most once per 500 ms, with a dirty revision to skip unnecessary rebuilding. Keep the ten-second unchanged refresh and 45-second evidence lifetime from the proposal.

Maintain at most one pending unsent C snapshot and replace it with the newest state. Allocate its shared transport sequence when it is sent, or otherwise preserve globally increasing send order. Do not let replacing C reorder H acknowledgements, Sparkling catches or other message kinds.

### Separate unknown, negative proof and terminal facts

A zero completion flag means the reporter is not currently asserting completion. It does not prove a species is unfinished and must not cancel another reporter's valid proof. Explicit positive local contradictions still override remote completion.

Expired C evidence removes only temporary completion assistance. It must not erase independently observed catches, revive caught Sparklings, undo a finalized assignment or alter permanent history. Keep these state lifetimes separate.

### Use the right identity for each kind of deduplication

- Transport ordering: the existing increasing sequence, scoped to its validated sender connection/visit.
- Completion replacement: `(runId, reporter UUID)`; retain only the newest valid complete snapshot.
- Assignment instructions: the existing stable `(runId, recipient UUID)` notification identity, acknowledged after chat submission.
- Local observation ownership: keep local and received evidence separate so shared proof cannot circulate back as fresh local proof.

Do not add a second completion sequence counter unless it solves a demonstrated ordering problem. Keep unknown optional protocol features harmless and require explicit capability/version agreement before changing row meanings.

## 5. Current-build comparison

The supplied 4.2.2 jars were inspected on 10 November 2026. The 33 compared sync classes match the previously reviewed 4.2.2 copy. Its hello already includes `ticketed`, `display`, `present` and `locked`; its chat fallback still uses the older two-/five-second allocation timing and a shorter report acceptance window. The shared C completion transport is not implemented in that inspected build.

Its client-specific routing nonce is not a shared run identity and should not be reused as `runId`. The recommendations above concern the existing shared service; they do not require adopting or changing the separate backup service.

## Decisions to confirm

1. Expected-roster timing, the 30-second fallback, and 250 ms late-arrival batches.
2. Assign and display a remaining biome for admitted players whose actual hotspot is still unknown at the fallback deadline.
3. Server `runId`, client `visitId`/`resumeRunId`, and the reconciliation/rollout rules above.
4. Explicit flag meanings, cross-reporter proof combination, and species-specific units for `checks`.
5. The `critter-completion-v1` welcome capability, explicit replay completion, and single-transport migration.
6. Complete snapshots initially, with connection-bound run validation and the replacement/ordering rules above.
