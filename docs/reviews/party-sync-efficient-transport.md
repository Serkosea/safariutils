# Party sync transport update — implementation guide

This extends the existing run reconciliation, completion and aggregate protocols.
The 39-value aggregate (including Shining Coins), species order, discovery/caught
masks, run IDs and assignment rules are unchanged. Old clients remain supported.

## Connect and negotiate

Send `X-SafariUtils-Sync-Protocol: 3` on `/v1/sync`. This is the **transport**
version, separate from the aggregate payload version. Continue sending the same
authenticated identity, lobby and roster headers and the existing `H` hello.

Enable the extensions below only when `welcome.features` includes
`efficient-transport-v1`. Without it, retain transport-2 behavior. Do not treat
the WebSocket opening as readiness: apply matching reconciliation and ordered
replay through `replay-complete` before publishing current-run state.

## Routine update batching

Collect routine updates produced in one client tick into one message:

```json
{"type":"batch","frames":[
  {"sequence":21,"kind":"N","payload":"3"},
  {"sequence":22,"kind":"U","payload":"1,0,0,0,-1,0"}
]}
```

- At most 8 frames and **4096 UTF-8 bytes total**, including the envelope.
- Allowed children: `A`, `N`, `U`, `C`, `F`. Existing payload validation applies.
- Each child has its own strictly increasing connection sequence. Use one
  serialized sender; do not reuse a sequence after recovery.
- Flush at the next client tick (normally about 50 ms). Keep the existing 500 ms
  state sampling/coalescing; do not introduce continuous per-tick snapshots.
- Flush pending routine frames **before** sending an immediate `S`, `H` or `B`.
  Never delay discoveries, terminal catches or coordination to fill a batch.
- Clear unsent batches on disconnect/visit replacement. Rebuild from current
  state after replay. The server relays ordinary frames to older peers unchanged.

## Completion refresh

Send a full `C` when rows change, on a new connection, or when requested below.
Remember that full frame's sequence as its baseline revision. For an unchanged
snapshot, every **20 seconds** send:

```json
{"sequence":23,"kind":"F","payload":"{\"version\":1,\"runId\":\"CURRENT-RUN-UUID\",\"visitId\":\"CURRENT-VISIT-UUID\",\"revision\":22}"}
```

The revision must reference a full `C`, not a previous `F` (22 above is illustrative).
The server retains the rows and sends ordinary full `C` frames to peers, preserving
the **45-second freshness expiry**. `F` does not reduce freshness-message count
by itself; it reduces uploaded data and parsing.

If the server sends `{"type":"completion-resync","runId":"…","visitId":"…"}`,
verify both IDs and immediately resend current full `C` with a **new** sequence.
Do not wait for the next 20-second refresh. Clear the baseline on reconnect.

## Hello/report retries and pending reconciliation

- Send hello immediately for actual changes to ticket admission, leader, present
  members, display capability or locked choices. After successful replay, do not
  repeatedly send an identical hello on transport 3. WebSocket protocol ping/pong
  remains the liveness check; it is not an application heartbeat.
- Pending `reconcile` frames include `retryAfterMillis` (currently 4000). Respect
  it for identical retries; changed hello information may be sent immediately.
  The server can push successful reconciliation when another admission resolves
  the pending run. Retain bounded connection/replay failure recovery.
- `hotspots.payload.reports` acknowledges accepted player→biome reports. Stop
  retrying those reports, even before assignments finalize. Restore acknowledgments
  from replay, clear on new connection/run, and accept them on equal-revision
  snapshots. Older servers without this field retain the previous retry behavior.

## Renew the current connection

The socket lease lasts ten minutes. Around **8 minutes 5 seconds** after opening
or renewal, obtain a valid session token through the existing shared session
cache and send:

```json
{"type":"renew","token":"CURRENT_SESSION_TOKEN"}
```

This control message has no state sequence and is not batched. Never log its token.
The response is `{"type":"renewed","expiresInMillis":600000}` (remaining lifetime
may be smaller). Schedule the next renewal approximately 115 seconds before that
remaining lifetime ends. Authenticate the same account; no reconnect, new lease
slot or room replay is needed. Early repeated renewals do not write storage.

Use a bounded acknowledgment timeout (20 seconds in SafariUtils). On failure,
close the connection, clear readiness/connected status and reconnect with backoff.
Ignore responses from replaced sockets. Existing application data must not be
presented as live while a required service is unavailable.

## Backend authority and compatibility

The lobby room now owns live reports, presence, run reconciliation, completion and
notification state. The shared authority retains cross-lobby membership/fairness
and performs idempotent allocation commits. A typical four-member run uses one
history admission and one allocation call; late allocation batches and actual
membership changes may require additional calls. All clients display the same
backend result. No client-side fairness calculation or extra HTTP endpoint is needed.

Current server minimum transport is **3**, enabled on 11 November 2026 after the
operator confirmed both mods had updated. Older sync upgrades receive HTTP **426**,
`x-safari-sync-update-required: true`, and `sync_update_required`. Stop automatic
sync retries for that application session and show an update-required status.
Keep unrelated lookup/market functionality available. This version gate is a
compatibility cutoff, not proof of client identity. Old clients may still send
rejected HTTP requests or obtain sessions; they no longer enter sync Durable Objects.

Retain the existing caught-over-found precedence, visit/generation checks,
inventory freshness rules, fixed assignments and leader-only announcements.
