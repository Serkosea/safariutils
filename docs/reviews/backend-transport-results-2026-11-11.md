# Party sync capacity rollout — 2026-11-11

Backend deployment: `f2115174-7e68-45b5-b7e6-82eed20264a7`.
Endpoint: `api-dev.serkosea.dev`. Minimum transport remains **1**; legacy clients
are allowed. New clients negotiate transport **3**. Aggregate payload stays at 3.

Implementation guide: [Party sync efficient transport](party-sync-efficient-transport.md).
This supersedes the earlier request-reduction guide for clients adopting transport 3.

## Implemented

- Live coordination runs inside the lobby room, eliminating per-report, presence,
  acknowledgment and departure RPCs to the shared coordinator.
- Shared membership/fairness persists across rooms; allocation commits are
  idempotent. Existing runs migrate with retry receipts. Reconnects retain locked
  choices and progress; expired/new visits use the existing reconciliation rules.
- Routine client updates batch within one tick. Discoveries and terminal catches
  flush immediately. Completion freshness uses a compact baseline reference,
  with full-state repair when required and unchanged 45-second freshness expiry.
- Healthy unchanged hellos stop on the new transport. Pending reconciliation has
  bounded server retry guidance and can be pushed when another admission resolves it.
- Authenticated lease renewal keeps the same connection, avoiding ten-minute
  teardown, admission and replay. Early repeated renewal is storage-idempotent.
- One configuration value can later require transport 3. Rejected upgrades stop
  before authentication/DO access; updated clients stop retries on update-required.

## Verification

- 137 backend tests passed, including migration retry, idempotent fairness,
  mixed-version batch delivery, stale completion baseline repair, lease ownership
  and expiry, malformed/oversized batches and pre-admission version rejection.
- Workers runtime suite passed with room-local authority enabled, including
  repeated mixed-version visits, roster expansion, retained catches and reconnects.
- 166,239 client regression checks passed. Standalone 26.1.2 and 26.3 builds and
  integrated SerkoAddons 26.2 build passed, including artifact-boundary checks.
- Live isolated transport-3/transport-2 pair passed lobby alias matching,
  late-join discovery, terminal catch, completion batch/refresh, renewal response,
  assignment and duplicate-report checks. Market returned fresh prices for 171 products.

## Savings: measured scenarios versus estimates

| Area | Result |
|---|---|
| Shared coordination | Four hellos, four reports, reconnect and four leaves required **2 cross-DO calls** in the test; forwarding all 13 operations requires 13. This is **85% fewer** for that scenario. |
| Idle hello + completion traffic | Original 15-second hello/10-second completion cadence: about 100 messages per ten minutes. New transport: about 30 completion refreshes plus one renewal, excluding setup/changes: **about 69% fewer**. The immediately previous 60/20 cadence was already 40. |
| Same-tick routine pair | Two frames become one WebSocket message: **50% fewer** for that pair. Unaligned updates and urgent Sparkling frames do not receive this reduction. |
| Completion upload bytes | `F` sends run/visit/revision instead of 37 rows. Savings depend on row values; message count only falls when it shares a batch. |
| Ten-minute renewal | Avoids a new Worker upgrade, account connect/release cycle, full replay and replacement connection; one lease-store request remains. Short runs ending before renewal gain nothing here. |

For a fully updated client population, a reasonable planning range is **25–45%
fewer billed DO request equivalents** and **20–45% fewer raw DO invocations**,
relative to the traffic mix reviewed before this rollout. This is not a measured
production daily result. Central coordination itself should usually improve more
(roughly 60–85%), depending on roster changes and late allocation batches.

Read/write savings are smaller and workload-dependent: live run writes move into
rooms rather than disappear, and history admissions/commit receipts still persist.
Fewer membership refreshes and reconnection/replay operations help, but do not
interpret a drop in the coordinator's writes as the same drop account-wide.
The earlier indexed-lease and auction checkpoint savings remain in place.

Compare full UTC days after both clients update. Keep Worker requests, raw DO
invocations, WebSocket message request equivalents, rows read and rows written
separate. Incoming WebSocket messages have a 20:1 billing conversion; internal
HTTP/DO calls are not discounted that way. Older clients get the server-side
coordination savings now, but retain their older sender traffic until updated.
