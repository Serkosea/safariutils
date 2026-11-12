# Server-only capacity improvements — 2026-11-11

These changes preserve the client protocol, alert timing, assignment rules,
five-minute auction-listing freshness, thirty-second ended-auction polling,
price selection and historical price fallback. Clients need no update.

Deployed to `api-dev.serkosea.dev`, version
`28023171-3516-4f26-b390-208802ff8c86`.

## Implemented

- Removed the persistent usage ledger from normal SQL/KV operations. Diagnostics
  now sample completed writes in memory, emit on existing traffic at most once
  per minute, and reset their day at UTC midnight. Samples restart on eviction;
  they are not billing totals. No internal daily component cutoffs remain.
- Identical accepted hotspot reports reuse the last authority snapshot instead
  of calling the coordinator again. Changes, failures, new visits and runs still
  go through the authority. After hibernation the first retry revalidates it.
- Empty disconnect checkpoints are omitted. Unchanged retained checkpoints avoid
  repeated writes within five minutes; their retention age can consequently be
  up to five minutes older than the latest identical close. Completion timestamps
  remain unchanged, and new caught-Sparkling evidence is merged immediately.
  Same-key close writes are serialized to prevent stale overwrites.
- Socket leases now have one authoritative indexed table. Releasing a lease
  no longer reads/replaces the account JSON. Existing leases migrate through
  the existing migration, while stale legacy JSON cannot revive released leases.
  Expired leases are cleaned in bounded batches; counts ignore expired rows.
- Auction checkpoints place growing page summaries before mutable counters, so
  completed chunks survive subsequent checkpoints. Existing saved scans still load.
- Repeated ended-auction snapshots do not rewrite history just to persist another
  polling deadline. The durable alarm retains the schedule. History evidence and
  failure/backoff changes are still persisted. Duplicate responses also skip the
  history-bucket serialization work.

## Measurements

Storage figures below use an isolated Workers runtime and its actual SQLite
cursor counters, including indexes. Initialization/schema costs are excluded.
These are repeatable scenarios, not account-wide before/after production totals.

| Workload | Metric | Before | After | Reduction |
|---|---|---:|---:|---:|
| 100 indexed inserts + 100 indexed updates | Rows written | 600 | 400 | 33.3% |
| Same | Rows read | 600 | 200 | 66.7% |
| Same | SQL calls | 600 | 200 | 66.7% |
| 4 players, 10 connect/release cycles each | Rows written | 484 | 284 | 41.3% |
| Same | Rows read | 622 | 232 | 62.7% |
| Same | SQL calls | 700 | 260 | 62.9% |
| 1 hotspot report + 20 identical retries, resident room | Coordinator requests | 21 | 1 | 95.2% |

The lease workload includes normal expiration cleanup and persisted connection
abuse guards. It does not include room joins, assignment RPCs, replay or ordinary
gameplay state, so it is not the cost of ten complete runs.

Auction checkpoint measurements use a 47-page scan with six incremental saves
through page 44. They count persisted keys and serialized bytes, not billed CPU.

| Relevant price entries/page | Checkpoint rows, before → after | Reduction | Serialized bytes reduction |
|---:|---:|---:|---:|
| 10 | 7 → 7 | 0% | 0% |
| 100 | 20 → 15 | 25% | 43.2% |
| 220 | 30 → 21 | 30% | 44.9% |

Small scans already fit in one chunk and gain nothing from chunk ordering.
Generation churn or filling earlier holes can reduce these savings. Duplicate
sale polls now write zero history keys instead of one manifest plus the former
ledger update; the alarm still has its normal write cost. Polling frequency and
sale coverage are unchanged.

## Capacity estimate and limits

A reasonable **planning estimate** is roughly **20–40% fewer total backend writes**
if the workload remains dominated by the affected lookup/coordination/admission
paths. That would yield approximately **1.25–1.67× write-limited capacity**.
This is not a measured daily result or a guarantee: traffic mix, indexes, market
history activity, reconnects and other applications on the account can change it.

The removed ledger eliminates one bookkeeping read and write per measured
mutation, but whole-service read savings cannot be inferred from the two test
workloads. Coordinator-request savings depend on how often clients retry accepted
reports and whether the room hibernates. Client HTTP requests, incoming WebSocket
frames and Hypixel polling frequency have not changed, so do not assign them a
percentage reduction. No new batching delay was introduced for gameplay events.

CPU should benefit from fewer SQL calls and less duplicate serialization, but no
CPU-time or memory percentage has been measured. Capacity is ultimately bounded
by whichever Cloudflare quota is reached first, not only written rows.

## Verification and operating notes

- All 125 backend Node regressions passed, including empty/repeated close checkpoints, caught
  evidence, report retries, changed reports, legacy lease migration, UTC sample
  reset, auction restart and duplicate sales polling.
- Real Workers runtime: mixed legacy/current visits, discovery/catch replay,
  reused-lobby recovery, nonce/auth admission and cross-room socket limits.
- Before/after harness: backend `test/capacity-measure.mjs`; saved baseline
  fixtures are test-only, never exported by the deployment entrypoint.
- Live isolated two-client verification passed: authentication, lobby aliases,
  late-join discovery replay, terminal catches, finalized hotspot assignments and
  report retries. The auction endpoint returned fresh data for 171 products.
- Backend source lives in the private checkout's `backend/` directory and is
  ignored by Git; a public mod-repository push alone does not deploy this work.

Compare Cloudflare account metrics per comparable player-hour/run over subsequent
days. Old ledger rows may remain unused; they incur no continuing hot-path IO.
Rolling back to a lease implementation that trusts the old account JSON requires
care, because the indexed table is now authoritative.
