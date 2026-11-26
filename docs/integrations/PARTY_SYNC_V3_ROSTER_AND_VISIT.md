# Party sync v3 update for Sparkling Mutuals

Existing v3 clients can keep connecting to the primary service. Two optional features in `welcome.features` save a second socket admission when a party grows and a redundant checkpoint when a visit ends. Each can ship independently. No transport-version bump, new endpoint, API key, backup-provider change, or change to the Sparkling, objective or completion payloads is needed.

## 1. Expand the roster on the open socket

Use `roster-expand-v1` after ordered replay has finished. The lobby, local player and visit must be the same. The proposed roster must contain every previous member plus at least one newly confirmed attendee, with at most four distinct player UUIDs. A new lobby, new visit, departed/replaced member, or ambiguous roster uses the existing reconnect path. Finish any lease renewal first.

1. Flush queued v3 frames through the existing ordered outbox. Capture the current welcome connection ID, reconciled run ID and local visit ID. Keep the socket's sequence counter.
2. Send this **top-level** JSON frame on that socket. Substitute real IDs. List raw player UUIDs in the order in which you will interpret the next welcome, lowercase and without hyphens.

```json
{
  "type": "expand",
  "connectionId": "<current connection ID>",
  "visitId": "<unchanged local visit ID>",
  "runId": "<reconciled run ID>",
  "roster": ["11111111111111111111111111111111", "22222222222222222222222222222222"]
}
```

3. Mark the connection as awaiting welcome/replay. Keep local observations, catches, assignment locks and already-issued notification IDs. Invalidate the previous tag mapping, hello deduplication and completion baseline. Pause ordinary sends and ignore old deliveries during the transition.
4. The server replies with another `welcome` on the **same WebSocket**. It carries a **new logical connection ID** and roster tags in request order. Validate count, uniqueness and the local player's tag again. Send the normal `H` hello with the same visit ID and the previous `resumeRunId`. Continue increasing wire sequence numbers. Refresh the preference against this welcome because its scope is the expanded party.
5. Apply reconciliation and ordered replay for the new connection and visit. Accept the authoritative run ID even if a guest established the expanded room first. Wait for matching `replay-complete` before acting on replayed Sparkling discoveries or resuming routine sends. Publish a fresh aggregate and full completion baseline as needed. Caught evidence takes precedence over found evidence.

If expansion is unavailable, rejected, or times out, reconnect with the complete confirmed roster and normal backoff. SafariUtils uses a 12-second welcome/replay watchdog. Preserve same-visit evidence through recovery. A successful expansion needs no HTTP request or second lease.

## 2. Mark a confirmed visit end

When `visit-ended-v1` is advertised and the player actually leaves the Safari visit, add `"visitEnded":true` to the **inner payload** of the existing sequenced `H` leave. Use the old visit/run IDs before clearing them. Send through the ordered outbox before closing the socket.

```json
{"version":1,"action":"leave","visitId":"<current visit ID>","runId":"<current run ID>","visitEnded":true}
```

A socket interruption, lease renewal, roster expansion or provider outage does not establish that the player left Safari. Use ordinary reconnect handling in those cases. The server independently verifies the departure and decides when the visit has ended for everyone. Older clients can continue sending their existing leave.

## Hotspot preferences

The primary implements the proposed `hotspot-preferences-v1` fields. Read the sender's `welcome.hotspotPreference` for this roster. Send the selected order on `H` hello when different or absent. Use all four biome identifiers exactly once. Omission preserves the saved order.

On hello, optional `startingItems.birdFood` and `startingItems.shiningCoins` are the sender's confirmed **initial grant** for this run. Send zero for a known zero, omit unknown counts, and exclude floor pickups. Continue displaying the backend's final assignments. Changed preferences or grants do not reopen locks.

## Errors and quick verification

Honor `Retry-After` on failed HTTP requests or WebSocket upgrades. A 429 may include `x-safari-limit-reason`, such as `burst-sync` (service-wide minute admission), `sync-reconnect` (per-player reconnect pacing), `sync-concurrent` (per-player leases), `sync-global` (service-wide leases), or `sync-room-full` (one lobby). A 503 can include `x-safari-error-reason: coordination-unavailable`. Treat absent or unrecognized codes as an unspecified failure at that stage. Mark shared state unavailable until replay recovers and deduplicate player-facing warnings. HTTP lookup limits are separate from WebSocket admission.
