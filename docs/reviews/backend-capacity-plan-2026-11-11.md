# Backend capacity review — 2026-11-11

Analysis only; this document does not change the service or protocol.

## Evidence and limits

Reviewed the current backend's admission, write instrumentation, party coordination,
room replay/retention and auction scheduling; current SafariUtils client producers;
and decompiled networking producers from the supplied 4.3.2 Minecraft 26.2 reference
jar. This is a focused architecture/code audit, not a production load benchmark or
a complete security review of that jar. Today's earlier diagnostic counters showed
coordination at 19,973 writes, cache near 23,245, market 1,214 and admission 3,026.
Those were application counters, not account billing or a per-run cost breakdown.

Cloudflare documentation distinguishes incoming Worker requests, Durable Object
requests, SQL rows, duration and storage. Incoming WebSocket messages receive a
20:1 compute-request billing ratio; outbound messages and protocol pings are not
charged as DO requests. Internal DO calls are separate requests. Consequently,
removing one repeated central RPC can matter more than removing one client frame.
Free DO quotas include 100,000 requests/day, 100,000 written rows/day, 5 million
read rows/day and 13,000 GB-s/day; daily limits reset at midnight UTC. Stored data
and per-invocation limits do not reset daily. Account usage may include other apps.

Sources:
- https://developers.cloudflare.com/durable-objects/platform/pricing/
- https://developers.cloudflare.com/workers/platform/limits/

## Priority 1: remove costs that provide no gameplay benefit

**Replace per-mutation durable usage bookkeeping.** `usage-budget.js` still reads
and updates `service_write_usage` around database mutations, although its cutoffs
are now disabled. Remove that ledger from the production hot path. Accumulate
bounded in-memory operation/row counters and emit periodic structured summaries
while handling existing traffic, not on a new recurring timer. Label these samples
as approximate across eviction. Use Cloudflare analytics for account totals.
One ledger write per measured mutation disappears; the percentage of total writes
saved depends on indexes, KV operations, cleanup and other writes. No game data is
lost, because only diagnostic persistence changes.

**Deduplicate hotspot reports before central RPC.** Both clients have roughly
two-second report retry paths. The server should remember successfully accepted
reports in the socket attachment, keyed by run, reporting member and biome.
Return/replay the accepted result on duplicates; never cache a failed acceptance.
Invalidate on run change, preserve conflict checking, and acknowledge acceptance
explicitly in a negotiated protocol so upgraded clients stop retrying. Keep old
clients working with server-side deduplication. Existing 240-second hello dedup
already helps: do not count every 15-second hello as a central database write.

**Avoid rewriting unchanged disconnect checkpoints.** Compare the retained version
before replacing it; do not persist empty completion/inventory records merely
because a connection closed. Never omit caught-Sparkling evidence or finalized
assignments needed by reconnecting/late clients. Cleanup should use bounded expiry
indexes rather than repeatedly listing unrelated retained runs.

## Priority 2: negotiated lower-frequency background traffic

Both inspected clients sample completion state every 500 ms, send changes, and
refresh full completion snapshots every 10 seconds. Healthy hellos repeat every
15 seconds; pending negotiation retries about every 2 seconds. Both use protocol
pings around every 25 seconds. Leave those pings alone initially.

Proposed capability: `efficient-sync-v1`, offered and accepted explicitly.

- Discovery, caught evidence, ticket admission, run changes and hotspot reports:
  send immediately. These must not wait for a background batching interval.
- Inventory/objective changes: retain 500 ms coalescing initially; optionally use
  750 ms after testing. This adds at most 250 ms to those updates, not Sparkling alerts.
- Completion changes: retain current responsiveness; unchanged full snapshots
  every 25 seconds instead of 10. Negotiate a 75-second completion TTL with this
  cadence. Keep the existing 10/45-second behavior for old clients. Disconnected
  inventory becomes unavailable immediately; completion evidence expiry is separate.
- Healthy hello: only on relevant change, plus a 60-second fallback refresh.
- Pending negotiation: 2, 4, 8 seconds with jitter, capped at 8; reset immediately
  for actual ticket/roster changes. Prefer server-pushed resolution over polling.
- Longer term: compact revision refreshes can replace unchanged full completion
  frames, but only with a defined missing-baseline resnapshot path.

For a steady ten-minute four-player run, excluding changes and reconnects:
completion refreshes fall from about 240 to 96 and healthy hellos from 160 to 40.
Combined background frames fall from 400 to 136 (66%). That is NOT a 66% total
billing reduction: it excludes actual events, admission, RPCs, alarms and SQL.
The 20:1 ratio makes these about 20 versus 6.8 DO request equivalents before
rounding. Avoid overselling heartbeat reductions as the principal write saving.

## Priority 3: separate live run coordination from persistent fairness

Current design: lobby relay -> global PartyCoordination DO -> indexed group JSON
and membership pointers. This serializes independent parties and couples temporary
run traffic to durable fairness storage.

Recommended target: keep sockets, the canonical run ID, replay state, assignments
and notification acknowledgments together in the lobby authority, scoped by exact
party identity and visit. A separate party-history authority handles membership
epochs and fairness once at run admission and once when finalized results commit.
Use an idempotent run-ID-based commit; reconnects must never increment wins again.
Do not route every report or transport close through the history authority.

Persist semantic transitions (new run, finalized allocation, durable catch evidence,
notification commitment) before acknowledging them. Coalesce reconstructible
inventory changes; use socket attachments for hibernation continuity. Attachments
are not a substitute for durable state across all-client departure or deployment.
Retain the current 45-minute recovery window initially; do not introduce arbitrary
ten-minute gameplay expiry. Reused lobbies and roster expansion remain explicit,
tested transitions, never inferred solely from a lobby name.

This is the largest structural opportunity but also the highest-risk change.
Benchmark it after priorities 1–2 rather than promise a multiplier in advance.

## Other service costs

- Admission stores leases in both per-account JSON and an indexed lease table.
  Prototype one authoritative indexed representation, preserving concurrency and
  reconnect abuse limits. Batch cleanup; do not scan all leases per connection.
- Ten-minute socket expiry causes re-admission/replay in long runs. Prefer an
  authenticated in-place renewal before considering longer leases. Most short
  runs gain little; never remove abandoned-connection cleanup or session expiry.
- Keep shared lookup caching, in-flight request coalescing and stale-on-error
  behavior. Avoid adding central admission RPCs for events already admitted on a
  WebSocket. Evaluate cache-hit admission separately from expensive upstream work.
- Auction listings already use five-minute freshness and demand activation. Keep
  complete-generation snapshots; a cheaper partial snapshot is not equivalent.
- Ended-auction history currently polls every 30 seconds even when listing demand
  has expired. This is up to 2,880 polls/day, plus alarms and metadata persistence.
  Do not blindly increase this interval: limited upstream result retention can
  cause missed sales. First verify coverage; offer demand-only history as an
  explicit tradeoff, retaining historical prices but allowing sale-history gaps.
- Store auction scan progress by stable page/bucket identifiers rather than
  repeatedly serialized shifting chunks where beneficial. Measure actual changed
  rows; batching many keys alone does not make their writes free.
- Keep bounded page batches, upstream timeouts and concurrency. Reducing billed
  requests by creating huge invocations can instead hit CPU, memory or subrequests.

## Availability and observability

One gameplay readiness boundary: transport open -> authenticated -> run reconciled
-> replay applied -> sync ready. A fatal dependency failure invalidates readiness
and stops claiming shared state is current. Local discovery remains independent.
No component daily allocations return. Cloudflare quotas can still be exhausted.

Record bounded reason codes for admission, reconciliation, replay, stale run,
upstream failure and quota rejection; include timings and operation counts, not
credentials, raw payloads or network identifiers. Aggregate by operation and UTC
day. Surface summary counts and the latest failure in the developer HUD without
adding polling requests. Account analytics are delayed; do not promise exact 99%.

## Rollout and acceptance

1. Baseline real-runtime rows read/written and internal calls for join, report,
   catch, late join, renewal, exit and reused lobby. Include indexes and alarms.
2. Ship server-only telemetry and duplicate-work reductions; old clients must pass.
3. Agree `efficient-sync-v1` cadence/expiry/ack semantics with both clients.
4. Test mixed versions, all-modern, solo, 2–4 players, 50 simulated users,
   hibernation, delayed joins, quota failures and reconnect storms.
5. Consider the authority split only after measured results from steps 1–4.

Acceptance: no extra batching delay for Sparkling finds/catches; no resurrected
caught state; no reshuffled finalized roles or duplicate fairness commits; no
unavailable service shown as ready. Compare usage per completed run and per player
hour, not just daily totals with different player counts. Publish measured savings
and capacity as the minimum headroom across writes, reads, requests and duration.
