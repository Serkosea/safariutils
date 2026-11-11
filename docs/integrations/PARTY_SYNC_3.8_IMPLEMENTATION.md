# Shared-party update for SafariUtils 3.8.0

Implementation reference, 10 November 2026. This extends the existing service at `wss://api-dev.serkosea.dev/v1/sync`; authentication, roster mapping, aggregate v3 (including coins), nests, walls, mounds and Sparkling masks remain unchanged. No separate backup service is involved.

The inspected 4.2.2 client predates these negotiated run/completion additions. If your development build already handles C rows, retain that work and add the identity, readiness and expiry rules below. Existing clients can continue using the legacy transport, but cannot supply or receive the new C evidence until they opt in.

## 1. Connect and reconcile before consuming shared state

Add `X-SafariUtils-Sync-Protocol: 2` to the WebSocket upgrade request. This is important: it prevents legacy replay being delivered before the run is confirmed. Keep existing authentication and room headers.

Welcome includes a per-socket `connectionId` UUID and these feature strings:

```json
["run-reconciliation-v1", "critter-completion-v1"]
```

Create one `visitId` UUID on entering a genuine new Safari visit. Keep it, and the last accepted run ID, across reconnects and lease renewal. Send the existing H envelope with this inner hello:

```json
{"version":1,"action":"hello","capabilities":["run-reconciliation-v1","critter-completion-v1"],"visitId":"<visit UUID>","resumeRunId":null,"ticketed":false,"display":true,"leader":false,"present":[],"locked":{}}
```

On reconnect, replace null with the previously accepted run UUID. `ticketed` means confirmed admission, not merely being in the lobby. Preserve the existing roster UUID, presence and lock formats. A confirmed attendee's actual hotspot report can also establish admission.

The response is:

```json
{"type":"reconcile","version":1,"connectionId":"<socket UUID>","visitId":"<visit UUID>","status":"joined","runId":"<run UUID>"}
```

Status is `new`, `joined`, `resumed`, or `pending`. Pending has null runId and means continuity is not yet established; do not import retained state or manufacture a new run. Continue local tracking and retry hello at a bounded cadence (SafariUtils uses two seconds while pending). A matching live participant or confirmed lifecycle evidence resolves the ambiguity.

The authoritative run ID is the same UUID as `hotspots.run`. Add `visitId` and `runId` to subsequent H reports, acknowledgements and leave messages. Hello continues to use `resumeRunId`. An early report can carry null runId and the current visitId; the server holds only the latest bounded report per member until reconciliation.

## 2. Apply the entire replay before becoming ready

Following successful reconciliation the server sends assignments/fairness, retained objectives, caught/found Sparkling state and fresh completion evidence, followed by:

```json
{"type":"replay-complete","version":1,"connectionId":"<socket UUID>","visitId":"<visit UUID>","runId":"<run UUID>"}
```

Process these in order on the client/game thread. Only become ready after applying the matching boundary, not when the network callback receives it. Reject callbacks and boundaries from an old connection or visit. Live updates are delivered after the boundary. If your receive queue overflows, reconnect and reconcile again instead of accepting partial state.

Until ready, keep local tracking/visuals, already-caught facts and finalized roles, but withhold decisions dependent on shared state. Reconcile pending discovery alerts and automatic ticket triggers against terminal caught evidence before acting. Send fresh local snapshots once ready. Never feed received data back into locally sourced completion evidence.

State frames and room Sparkling frames carry `runId`; hotspot frames carry `payload.run`. Ignore a mismatched run. A retained aggregate can have `inventoryFresh:false`: apply its durable objective progress, but do not present its held items or old feeder occupancy as current. Fresh A restores live inventory. All other aggregate field meanings remain as in the existing reference.

## 3. Send complete C snapshots

Enable C only after both features are negotiated and replay is applied. Use the existing shared outgoing sequence and JSON-string payload convention:

```json
{"sequence":42,"kind":"C","payload":"<serialized inner JSON>"}
```

Inner JSON (rows abbreviated here only):

```json
{"version":1,"runId":"<accepted run UUID>","visitId":"<sender visit UUID>","rows":[[0,0,0],[3,1,1]]}
```

Actual payloads must contain **exactly 37 rows** in the existing species-bit order from the wire reference. Every row is `[flags, checks, captures]` with integer bounds `0..7`, `0..256`, `0..256`. Flags are 1 = qualified Unique completion, 2 = qualified Sparkling/source checks, 4 = qualified Full Clear completion. Counts are observed individuals/captures, not a universal completion threshold; keep the agreed species-specific source rules (including Gazer's four checks and birds' captures).

Build snapshots from local observations only. SafariUtils reuses its existing trackers, samples at most every 500 ms, sends changes and refreshes unchanged evidence every ten seconds. Coalesce pending snapshots and allocate sequence numbers when actually sending. No additional entity scan is needed.

The relay validates the complete payload against the socket's visit/run and sends:

```json
{"type":"state","sender":"<mapped sender tag>","sequence":42,"kind":"C","runId":"<accepted run UUID>","ageMillis":1200,"payload":"<serialized inner JSON>"}
```

Store the latest complete snapshot per `(runId, reporter UUID)`, replacing every row, including lower counts and cleared flags. Evidence expires **45 seconds from server receipt**: subtract `ageMillis` and your own delivery-queue delay. Replay does not refresh its age. Reconnect sequence numbers may restart; stale replaced sockets cannot overwrite the active socket's evidence.

Combine counts by **maximum**, never sum, and qualified flags by OR. Positive local unfinished sources, live critters or uncaught Sparklings override a remote completion claim. Capture evidence plus qualified flag 2 can satisfy Unique where source rules permit. Remote C must never add personal catches, rewards, collection entries, PBs or saved run statistics. Expiry removes only that reporter's temporary assistance, not terminal caught facts or assignments.

## 4. Match assignment/reporting behavior

- Allocate immediately when every expected member is present, admitted and has reported; otherwise use 30 seconds from the first eligible report. Retries/reconnects do not restart that deadline.
- At the deadline, allocate only admitted attendees. Unknown hotspot can receive an unclaimed biome but earns no hotspot fairness win.
- Batch late arrivals for 250 ms; never reshuffle finalized assignments. Eligible unassigned clients may retry reports every two seconds.
- Accept manual reports for the first 30 seconds. Aliases: `forest/fprest/for/f`, `cavern/cav/c`, `icy/ice/i`, `haunted/haunt/haunte/hvant/h`. Single-letter reports must be the entire message; don't interpret arbitrary movement chatter as a report.
- Use backend assignments/fairness rather than independently allocating while connected. Preserve finalized chat-fallback locks when reconnecting; conflicting locks must not trigger a competing assignment.
- Only the leader announces instructions for players lacking a connected assignment display. Keep the two-second notification grace separate from allocation timing; acknowledge a notification only after its chat message is sent. Reconnect must not resend acknowledged instructions.
- Keep current mode-specific biome completion rules and report transport. C supplements those decisions; it does not replace source protocols or authorize reporting solely from raw check counts.

## Migration checklist

1. Add the upgrade header, stable visit identity, H fields and ordered readiness gate.
2. Read run IDs, retained-inventory freshness and replay ages; preserve terminal evidence through reconnect.
3. Add negotiated C publication/replacement/expiry using local-only evidence.
4. Match allocation timing and preserve existing locks/notification acknowledgements.
5. Verify reconnect, late arrival, a reused lobby, corrected lower C rows, expired C, and one member using an older client.

Reference source: `RemotePartySyncProvider`, `PartyItemSync`, `PartyHotspotSync`, `PartyCompletionSync`, `BiomeCoordination`, and `state/CompletionSnapshot`. See [the existing wire reference](PARTY_SYNC_REFERENCE.md) for the unchanged species order and aggregate/source fields.
