# Party sync request reduction

Superseded for the new rollout by [the transport-3 implementation guide](party-sync-efficient-transport.md).
The deployment details below describe the earlier intermediate rollout.

Backend deployed as `a7abd930-7565-436e-ad56-a802d7a211eb` on
`api-dev.serkosea.dev`. Live isolated checks passed for discovery replay,
terminal catches, assignments and duplicate reports.

## Server changes

The existing `hotspots` payload now includes an optional `reports` object:

```json
{
  "type": "hotspots",
  "payload": {
    "version": 1,
    "run": "current-run-id",
    "revision": 7,
    "assignments": {},
    "fairness": {},
    "leader": null,
    "notifications": [],
    "conflict": false,
    "initialDone": false,
    "reports": {
      "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa": "FOREST"
    }
  }
}
```

`reports` acknowledges hotspots already accepted by the authority. It is not the
assignment map: someone's requested hotspot and assigned biome can differ.
There are at most four roster UUID keys; values are FOREST, CAVERN, ICY or HAUNTED.
Assignments, fairness, deadlines, notification ownership and run identity are unchanged.

The room can answer retries from any connected party member using the latest
accepted snapshot without another coordinator call. Changed reports still reach
the authority. After hibernation, missing cached snapshots are fetched normally.
Unchanged successfully reconciled hellos reuse the current transport lease;
new connections and semantic changes still reconcile immediately. Confirmed
leave followed by socket close no longer produces a redundant coordinator close.

## Client changes / implementation guidance

1. Validate the snapshot's run, revision, roster keys and biome values as usual.
   Retain `reports` separately from assignments. Stop retrying reports for players
   already acknowledged, even while allocation is pending. Equal-revision replay
   can supply acknowledgments; older revisions cannot. Clear acknowledgments on
   a new run/reconnection and restore them from replay. Missing `reports` means
   use the existing retry path, preserving older-server compatibility.
2. Send unchanged healthy hellos every **60 seconds**, instead of 15. Send actual
   changes in ticket admission, leadership, presence or locked choices immediately.
   Keep initial/pending hello retries at **2 seconds**; this release does not change
   reconciliation deadlines, watchdogs or fallback timing.
3. Sample completion changes at the existing **500 ms** interval. Send changes as
   before, but refresh an unchanged full completion snapshot every **20 seconds**,
   instead of 10. Keep the existing **45-second expiry** on server and clients.
   A single missed refresh still leaves room for the following refresh.
4. Keep immediate Sparkling discovery/catch messages, existing inventory coalescing,
   and protocol pings unchanged. Do not turn a protocol ping into an application
   message or disconnect/reconnect just to save heartbeat messages.

Existing clients may ignore `reports` and continue their current cadence; the
backend remains compatible. They receive the server-side reductions, but obtaining
the client-side reductions requires updating their sender logic too.

## Expected effect

Per player over ten steady minutes, unchanged completion + hello traffic drops
from roughly **100 to 40 messages (60%)**, excluding initial handshakes and actual
gameplay changes. Across four players this is approximately **400 to 160**.
Accepted-report retries stop on updated clients. For a report waiting thirty
seconds for allocation, this can replace roughly fifteen attempts with one
accepted attempt, plus any retry needed before its acknowledgment arrives.

For an unchanged ten-minute transport, central hello calls fall from approximately
three (initial, four minutes, eight minutes) to one. Changed hellos are additional.
Each successful explicit leave saves one subsequent central close call.

These are path-specific reductions, not a measured reduction of the entire
account's request quota. Incoming client messages cannot be eliminated by
discarding them after they have already reached the server. WebSocket message
analytics and billed request equivalents are also different counters.

## Verification

128 backend tests, the mixed-client Workers runtime suite, and 166,231 client
regression checks passed. Tests cover report validation, older snapshots without
acknowledgments, cross-member duplicate suppression, changed admission, hibernation,
reconnect, explicit leave, caught discoveries and reused lobby reconciliation.
