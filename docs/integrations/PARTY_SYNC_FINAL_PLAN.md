> Historical planning record. The implemented contract is [the 3.8.0 guide](PARTY_SYNC_3.8_IMPLEMENTATION.md).

# Party sync implementation plan

Reviewed against PARTY_SYNC_FOLLOW_UP.md and the current backend/client source on 10 November 2026. This is the proposed implementation contract, not deployed behavior. John must implement the new run fields and readiness handshake before enabling the new completion transport.

## Agreed behavior

- Keep the existing service, authenticated socket, roster mapping, species order and shared outgoing sequence.
- Initial assignments finalize when every expected roster member is present, admitted and has reported, or 30 seconds after the first eligible report. At the deadline only admitted attendees receive assignments. Late eligible arrivals are batched for 250 ms. Final assignments never reshuffle.
- Unknown hotspot does not prevent an admitted player receiving a remaining biome at the deadline; it earns no hotspot fairness win.
- Use complete 37-row completion snapshots, latest per reporter, 45-second expiry from server receipt and server-computed replay age.
- Use qualified completion flags for decisions. `checks` is informational: observed individuals/captures, with the current Gazer cap of four and birds reflecting captures. It is not a common source-count metric. Walls, mounds and nests retain their existing source protocols.
- Keep local and remote evidence separate. Remote completion cannot create catches, rewards, collection entries, PBs or historical records.

## 1. Exact proposed handshake

Advertise `run-reconciliation-v1` and `critter-completion-v1` in welcome when implemented, alongside existing features. Add a server-generated `connectionId` UUID to welcome. It identifies this socket, not the player or Safari visit.

New clients opt in through H hello. Preserve the existing H version and fields, adding:

```json
{"version":1,"action":"hello","capabilities":["run-reconciliation-v1","critter-completion-v1"],"visitId":"<visit UUID>","resumeRunId":null,"ticketed":false,"display":true,"leader":false,"present":[],"locked":{}}
```

Generate `visitId` on a genuine new Safari visit, not socket reconnect or lease renewal. Retain it and the accepted `runId` through reconnects. `ticketed` must be explicit: missing/false is not admission. A validated actual-hotspot report plus confirmed attendance can establish admission for a manually reporting member.

The server binds the socket to the authoritative UUID already represented by `hotspots.run`. It sends one of these responses (placeholders stand for actual UUIDs):

```json
{"type":"reconcile","version":1,"connectionId":"<socket UUID>","visitId":"<visit UUID>","status":"resumed","runId":"<existing run UUID>"}
{"type":"reconcile","version":1,"connectionId":"<socket UUID>","visitId":"<visit UUID>","status":"joined","runId":"<existing run UUID>"}
{"type":"reconcile","version":1,"connectionId":"<socket UUID>","visitId":"<visit UUID>","status":"new","runId":"<new run UUID>"}
{"type":"reconcile","version":1,"connectionId":"<socket UUID>","visitId":"<visit UUID>","status":"pending","runId":null,"reason":"run-unconfirmed"}
```

- **resumed:** server-known actor/visit binding and matching resume ID, within the correct party and lobby.
- **joined:** a new actor/visit joins a live run confirmed by an already reconciled participant. The claimed UUID alone never grants access.
- **new:** no retained candidate exists, or the previous run has positively ended and a fresh visit begins. Create a new UUID while preserving applicable group fairness.
- **pending:** retained state exists but continuity cannot be established. A lobby name, silence or a timer alone cannot distinguish a reconnect from a reused lobby. Wait for a valid resuming participant or positive visit-ending/new-visit evidence; otherwise continue local behavior without importing retained state. Do not invent a new run merely to end the wait.

Validate `visitId` on every subsequent H operation. Require `runId` on reports, acknowledgements, leave and hello updates after reconciliation. Initial/pre-reconciliation reports may have null `runId`, but must carry `visitId`; retain only bounded, latest reports per roster member for that socket/visit. Drop them on visit replacement, expiry or contradictory reconciliation. Apply the current report-window rules before replaying them. Imported locks from a hello stay quarantined until reconciliation succeeds.

## 2. Ordered replay and readiness

After successful reconciliation, send a consistent replay batch of current assignments/fairness, run-scoped objective/Sparkling state and unexpired completion snapshots. Then send:

```json
{"type":"replay-complete","version":1,"connectionId":"<socket UUID>","visitId":"<visit UUID>","runId":"<run UUID>"}
```

Deliver subsequent live updates after this boundary. Queue live changes that arrive while reconciliation awaits another object; do not interleave them ahead of the snapshot or drop them. Client readiness changes only when its ordered delivery queue has applied the batch and matching marker, not when the network thread receives it. Stale socket/visit markers are ignored. Queue overflow requires fresh reconciliation, never partial readiness.

This gate controls decisions dependent on shared state, including assignment notifications and shared Sparkling reconciliation. Local tracking and local visuals continue. Existing caught facts and finalized roles remain fixed during reconnect; pending ordinary discovery alerts are reconciled rather than discarded.

## 3. Completion wire shape

Continue the proposed version-1 row format, with required `runId` and `visitId` additions. This is a coordinated extension: the old rows-only decoder is not sufficient. Enable C only when both capabilities are advertised and the client opts into the new handshake; older clients keep their existing behavior and are not sent C.

Inner C payload:

```json
{"version":1,"runId":"<run UUID>","visitId":"<sender visit UUID>","rows":[[0,0,0],[3,1,1]]}
```

The example abbreviates the array; actual payloads require exactly 37 `[flags, checks, captures]` rows in the existing species order. Retain the original agreed numeric bounds, reject nonintegers/unknown flag bits, and validate the complete payload before replacing any state.

Use the existing `state` relay envelope and JSON-string payload convention, adding authoritative run and replay-age fields:

```json
{"type":"state","sender":"<existing mapped sender tag>","sequence":42,"kind":"C","runId":"<server-bound run UUID>","ageMillis":1200,"payload":"<serialized inner C JSON>"}
```

The server verifies the inner IDs against the socket binding and supplies the outer run ID itself. Completion replacement is keyed by `(runId, reporter UUID)`, not socket. Reject old-connection deliveries after a replacement connection takes ownership; a reconnect may restart its transport sequence without making stale old traffic newer.

Flags: 1 = qualified Unique completion, 2 = qualified Sparkling/source checks, 4 = qualified Full Clear completion. Combine valid flags by OR and counts by maximum, not sum. Capture evidence plus flag 2 may satisfy Unique only where the species-specific source rules permit it. Positive local unfinished-source/live-critter/uncaught-Sparkling evidence overrides remote completion; lack of a local sighting does not.

Each fresh accepted snapshot replaces all prior rows, including lower counts and cleared flags. Expiry removes that reporter contribution and recomputes the result. Replay never renews it; receivers subtract replay age and local queue delay. Expiring C must not undo catches, assignments or other terminal facts. Fresh unchanged refreshes renew evidence; duplicate sequences/replays do not.

## 4. Backend changes

1. Extend contract validation, feature negotiation and socket metadata with the handshake above; keep old clients working without accepting them as new-run proof.
2. Make PartyCoordination own visit-to-run reconciliation. Separate group fairness from run-scoped reports, locks, acknowledgement IDs and progress. Scope retained objectives and Sparkling replay to the reconciled run too: protecting only C would leave the same stale-state issue elsewhere.
3. Replace the old threshold timer with all-expected-ready/30-second behavior. Preserve first eligible report time through retries and reconnects. Require positive admission and handle confirmed roster departures, not temporary socket absence.
4. Select new instruction recipients using active assignment-display capability, not manual-report origin. Keep the existing two-second notification grace separate from allocation timing. Preserve already-delivered assignment/acknowledgement state through reconnects; leader alone submits and acknowledges actual chat sends.
5. Add bounded per-reporter C retention, validation, expiry and ordered replay. Reuse existing transport and storage lifecycle mechanisms; avoid parallel completion rooms or periodic entity work on the backend.

## 5. SafariUtils changes

- Extend RemotePartySyncProvider and PartySnapshotBatch with capability negotiation, connection/visit/run IDs, ordered replay readiness and stale-delivery rejection.
- Add a pure local completion snapshot builder and expiring remote evidence store. Reuse current trackers; no additional world scan. Evaluate at most every 500 ms, coalesce one pending C snapshot, and refresh unchanged evidence every ten seconds. Allocate sequence at actual send time.
- Centralize the combined completion view for all three modes, Missing HUD and biome reporting. Keep outgoing C based strictly on local evidence to prevent feedback loops.
- Update backend and chat-fallback allocation timing together, retry eligible unassigned reports every two seconds, and display finalized assignments even when own hotspot is unknown. Preserve locked fallback assignments on recovery; conflicts freeze new decisions.
- Extend developer diagnostics with reconciliation status, replay readiness, run identity changes, C reporter age and rejection reasons, without exposing session credentials.

## 6. Implementation and rollout order

First freeze these exact wire examples with John. Then implement backend state/contract tests, client protocol and completion tests, and mixed-version migration tests. Deploy additive backend support before enabling the new client path. Existing clients remain usable; both updated clients must agree on the run fields and replay gate before shared C is enabled for them. Update the concise integration reference and provide matching sample frames.

Required tests: all-ready and 30-second allocation; pre-hello/manual reports; late arrivals; missing ticket proof; reconnect/lease renewal; reused lobby with ambiguous continuity; stale leave/callback; replay/live race; expiry with queue delay; corrected/lower snapshots; two reporters observing the same catch; local contradictions; leadership change and duplicate instruction prevention; no remote-to-local evidence feedback; all three modes; older clients; standalone and combined builds.

No runtime code or backend deployment is included in this planning change. Separate backup hosting remains outside scope.
