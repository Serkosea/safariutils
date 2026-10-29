# Automatic diagnostics

`MARKET/BAZAAR` reports one completion per actual shared public Bazaar request, with
product count or failure category/status. Consumer cache reads do not emit records.
No market response payloads are logged. Failures retain the last good price snapshot.

`party-sparkling` records changed local snapshots, accepted shared discovery counts and
reminder transitions. `party-network` and `party-sync` retain connection, roster and payload
rejection diagnostics. Type `S` carries found/caught species masks, not chat, coordinates or
credentials. With logging disabled these use the existing no-writer path.

Developer builds (including SerkoAddons) write the compressed JSONL stream only with Enable
Automatic Logging turned on. Fresh settings default off; schema 5 inverts the old disable
flag without changing existing preferences. Logging fails closed before settings load, so
disabled launches create no diagnostic file or writer executor. Disabling logging stops the
writer and clears queued diagnostics. Normal/Extra never write these files; Minecraft's
standard error reporting remains available independently.

## Capture outcome evidence

Records with `k: "co"`, `schema: 2` describe the inputs actually used for a selected queued
capture outcome. They include the normal connection/visit/run context plus:

- `observed`: server-outcome observation time in milliseconds
- `evaluated`: decision time in milliseconds
- `breakout`: whether this is a breakout rather than a catch
- `direct`: direct-reappearance prepass rather than fallback selection
- `fallback`: configured fallback delay in milliseconds
- `chosen`: selected attempt ID
- `candidates`: ordered arrays `[attemptId, startedAt, absent, brokeOut]`

Candidates are pending same-species attempts that started no later than the outcome.
Order matters for equal timestamps. No player names, UUIDs, tokens, or raw chat are included
in these records. Attempt IDs correlate with the existing identity lifecycle diagnostics.

Schema 2 excludes confirmed breakouts from catch attribution, including timed fallbacks.
Schema 1 remains replayable with its original policy; older logs are not judged against
changed attribution rules. `CAPTURE/TRANSITION` decisions record the attempt/logical/owner IDs
and accepted terminal action, including stale-owner retirement and awaiting-reappearance state.

`CAPTURE/WINDOW` records the start and settlement of attempt-owned artifact-filter windows.
The ID is allocated on the attempt event, including when target binding is deferred, so it
correlates with `ATTEMPT-MISSED`, `DEFERRED-ATTEMPT`, presentation and outcome diagnostics.
The retained count includes windows waiting for tick expiry; it is not a count of live critters.

`DiagnosticReplay` reruns the shared outcome selector and reports mismatches, malformed
records, or unsupported schema versions. Old logs without these records remain readable.
The record validates attribution policy against recorded evidence; it does **not** prove
the evidence itself was correctly associated with Minecraft entities. Immediate retry and
maximum-catch resolution paths still use their existing lifecycle records, not this schema.
Full entity-to-render replay remains unfinished.

## Bounds and data completeness

- The log queue remains capped at 2,048 records, with an explicit `LOG dropped=N` warning
  on overflow. Replay treats such warnings as data gaps, not successful validation.
- Queue admission, size and drop accounting share one short-lock owner. Each flush is capped
  at 2,048 records; shutdown closes admission before the final drain and includes pending
  repeat summaries. Disabled logging clears pending queue/drop state together.
- Debug repetition tracking has a hard 512-entry bound. Unique-message bursts cannot grow
  it indefinitely. Repeated messages retain summary counts on expiry or eviction.
- Debug text is bounded before it enters repeat tracking; disk writing, compression and
  rotation remain on the existing writer path. Repeat-cache locks never call logging/I/O.
- Outcome traces are emitted at selection, not during each frame or unresolved wait.

For future systems, prefer versioned decision inputs and lifecycle transitions over per-tick
state dumps. Record queue pressure, stale-result rejection, cache misses and invariant
violations at their shared ownership boundaries. Never pass credentials, authentication
proofs, private signed URLs, or full network payloads to diagnostic APIs. General text
logging is not a substitute for caller-side sensitive-data exclusion.

## Verification

Bridge connection ownership emits `BRIDGE/CONNECTION state` only when the status changes,
with previous/new states and a local generation counter. It does not record credentials,
endpoints or remote close reasons. `SETTINGS/BRIDGE` and `SETTINGS/CHATUTILS` load records
include rejected field counts and source protection; recovery writes use `recovery-saved`.
`CHAT/HISTORY recovery-saved` counts only this session's recoverable messages, not messages
retained in the protected source. Recovery snapshots require explicit reconciliation.

`CHAT/QUEUE capacity` reports a full 128-entry outgoing queue and whether the incoming
notice was urgent. `CHAT/QUEUE expired` reports discarded connection/visit/run/party-owned
entries (including unverified confirmed-solo party messages). Independently verified notices
may still wait for the roster to catch up. Neither record includes message contents.
Automatic command cooldowns and queue delays use monotonic time; diagnostic timestamps
continue to include wall time for correlation. Provider failure categories identify the
service and operation so an isolated failure remains observable.

`BAZAAR`, `UPDATES`, and `PLAYER_LOOKUP` outcomes are useful outside an active Safari world
and remain eligible for automatic developer logging there. Bazaar records loaded product
counts or failure type plus retained-cache count; update checks record status/HTTP failure
without release payloads; superseded lookup presentation records only success/failure.
These are completion events, not per-frame logs. The shared regression suite also uses a
loopback-only HTTP server to verify stalled streaming bodies and post-timeout transport reuse;
it never requests production API data.

Optional-service request boundaries report asynchronous failures under the same provider and
operation categories as synchronous failures. Null asynchronous results are invalid and reported;
normal cancellation is quiet. Callers still receive the original failure, and cancelling a
subscriber does not cancel shared upstream work. A provider may additionally log specific
details internally; the boundary record identifies which consumer-facing operation failed.

Capture outcome records now identify their timing domain as `clock=process-monotonic-ms`.
Attempt/observation timestamps in those records are transient process-relative milliseconds,
not dates; outer `t` remains wall time for human correlation. Older outcome traces without
this tag remain replayable because the selection policy uses relative durations and ordering.
Capture attribution, recatch-pin expiry and remembered-static lifetimes share that monotonic
time source; animation frames continue to use nanoseconds. No saved-data timestamp was migrated.

Capture terminal records (`k=ct`, schema 1) replace the old `CAPTURE/TRANSITION` text summary.
They contain session-local attempt/logical/owner IDs, pending/terminal/existence/caught/reappearance
flags captured before mutation, and the selected resolution. Replay executes the same terminal
policy and reports mismatches, unsupported schemas and malformed inputs. These records occur
only on terminal decisions, not frames, and contain no account identifiers. Outcome selection
and terminal policy replay do not yet constitute a complete world-observation/render replay.

`check` runs headless repeat-bound, burst, expiry, clock-regression and capture-policy replay
tests alongside the other core regressions. A passing replay/build is not an in-game test,
and missing records cannot establish that a subsystem behaved correctly.
## Saving one Safari run

Developer-only `/safari debug save` (also `/su debug save` and `/safariutils debug save`)
marks the current Safari visit for export. Automatic Logging must already be enabled; events
from before it was enabled cannot be recovered. There is deliberately no `/safari save` alias.
The existing writer seals its segment after the visit ends; a bounded local worker streams
only the selected visit into `safariutils/logs/saved-runs/` under the component config root
(`serkoaddons/safariutils/logs/saved-runs/` when embedded). Original session logs are unchanged.
The range includes pre-command evidence, capture replay inputs without epoch fields, rotations,
and the exit transition. A `saved-run` footer reports whether start/end boundaries were seen.
Stopping logging early produces a partial export; missing/dropped source records cannot be
reconstructed. This adds no duplicate live recording or work when automatic logging is off.
`SavedRunLogRegression` covers extraction, rotation, adjacent visits, provenance and partial logs.

## Performance notes

`network-auth` reports establishment via `uuid-claim` or the bounded failure stage
`session`. The current client does not obtain Minecraft profile keys or send Minecraft
credentials. A claimed UUID is a quota identity, not proof of account ownership. Never
record service tokens or request bodies. Legacy backend authentication routes may report
a verified username after successful proof; the UUID route must not label supplied names
or UUIDs as verified identities.
Missing backend events can still mean a local network failure, edge blocking or log limits.

`MARKET/AUCTION` records demand-driven auction snapshot status, usable state and product
count. Failure diagnostics contain only exception classes or numeric HTTP status, never
tokens or response bodies. `fresh` proves receipt of a valid complete backend snapshot,
not that every item has a price or that an estimate matches a completed sale. No collector
or price-render timing runs on the game thread; logging remains opt-in.

Developer builds time operations already routed through `OperationalLog.run` while in Safari.
An operation taking at least 5 ms emits a `PERF` record with its category, elapsed microseconds,
and biome, throttled to one record per category per 10 seconds. The category map is bounded.
This helps localize CPU stalls without per-frame log spam. It is not a frame-time or GPU profiler;
a missing record does not establish that the client maintained a particular FPS.


## Entity-ID research (developer, 2026-10-28)

With Automatic Logging enabled, `er` schema 1 batches record observations at the Safari
entrance and inside Safari. This is research instrumentation, **not a prediction feature**.
Public Normal and Extra editions do not register these listeners. Future floor-loot
prediction belongs only in Extra; validated identity/pairing improvements can be shared.

Each batch carries connection `c`, visit `v`, run `r`, and a client-world generation `world`.
Each event has sequence, wall time and monotonic nano time. Body records explicitly separate
integer `networkId` from entity `uuid`. Join UUIDs to the existing capture replay's body,
label and capsule UUIDs; logical critter IDs and attempt IDs in capture decisions are different
identifiers. Numeric IDs can be reused and must never be compared across world scopes.

- `load` / `unload` are client lifecycle observations, **not server creation/destruction**.
- `baseline` covers entities already present when scope/logging starts. Its iteration order
  is not spawn order. `observed` may also mean a missed load before scope recognition.
- `metadata` records changed custom name or item registry ID/count; late metadata is normal.
  Metadata is sampled every five ticks using the existing shared world snapshot. Movement
  and capture timing continue to use the existing capture journal, not this sampled stream.
- `id-reused` records a new UUID on an already observed network ID.
- `floor-components` records string-display IDs grouped by block position, including partial
  groups; the normal detector still requires its existing exact component count.
- `floor-click` records clicked and matched positions. `floor-reward` retains the server
  message and pending positions/ages. A single candidate is still only correlation; multiple
  candidates are explicitly ambiguous. `floor-not-observed` does not prove collection.
- `interaction` records target identity/position and held item registry ID, display name,
  count and hand. `scrappy-reply` records food response text with no invented target identity.
- `note` is a bounded manual annotation; `gap` marks exceeded research bounds. Existing writer
  queue overflow warnings also mean evidence is incomplete.

Limits: 4,096 identities and floor positions, 50,000 events per world/visit/logging scope,
64 events per batch, 256 characters per recorded name/message/note. Stable observations do
not emit rows. Encoding and gzip run on the existing bounded writer; only immutable records
cross threads. No additional per-frame traversal or movement dump is added. Disabled logging
performs no research scan, serialization or file work. The existing replay remains intact.
This reduces the cost of collecting the new evidence; it is not a guarantee that the complete
pre-existing debug stream has negligible cost. Compare live frame times with logging on/off.

Commands (standard aliases also apply):
- `/safari debug status`: recording scope, identity/event counts, omitted events and save hint.
- `/safari debug mark <note>`: annotate an experiment locally; never sends server chat.
- `/safari debug save`: export this visit, including earlier logged events, ending on exit.
  Logging enabled midvisit cannot recover earlier evidence; capture replay needs logging
  enabled before the visit boundary. Use the full launch log when investigating spawn order
  spanning entry because a saved-visit export excludes previsit observations.

Efficient first tests:
1. Enable logging **before entering**. On two or three runs, collect 8–12 floor drops one at
   a time, waiting for each server reward before the next click. Vary biome/route order on
   the next run; include any visually identical drops that give different rewards.
2. Interact with each Scrappy using one food at a time, waiting for the reply before another
   target/food. Test all three foods where practical; wrong-food responses are useful too.
3. Separately test nearby same-species breakouts and distant Hideons. Let one attempt resolve
   at a time for a clean baseline, then do a short spam/instant-recatch sequence.
4. Mark unusual moments and save the visit. Send the full launch log as well for the first
   pattern tests. No need to manually copy IDs. Keep some later runs untouched as validation.

Offline report: `python tools/entity_research.py <ordered-log.jsonl.gz> [...]` prints category
candidates, component offsets, Scrappy interactions/replies and gaps. It does not promote
correlations into rules, and must not treat filtered/sampled ordering as server truth.

General structured decision (`k: x`) JSON encoding also runs on the existing writer now.
Observation timestamps and lifecycle context are frozen at enqueue time, so a later warp
cannot relabel queued decisions. This avoids JSON tree/string construction on the game
thread for these existing decisions without dropping replay evidence.


## Shared frame and hot-path profiling (2026-10-28)

Automatic Logging also enables `PerformanceDiagnostics` in developer builds. It is not
restricted to Safari or Hypixel: menus, chat/Bridge, IQ and other integrated components use
one bounded collector and the existing writer. No additional executor, file, network upload
or per-frame JSON record is created. Public Normal/Extra remain logging-disabled.

`perf` schema 1 summaries are emitted every five seconds and at context/lifecycle boundaries:
- FPS derived from frame-start intervals, with mean, maximum and approximate p95 upper-bound
  frame time, plus counts over 16.67 ms and 50 ms. Histogram bins are 1 ms through 128 ms;
  the overflow bin uses the observed maximum as its conservative upper bound.
- Each instrumented stage reports call count, mean/max/p95 elapsed time and slow-call counts.
  Fixed names distinguish `client/` from `worker/`. At most 192 distinct names exist per
  window, with explicit overflow accounting. No input text, usernames, links or IDs are keys.
- Context includes screen class, world/menu, focus, and Safari biome only when applicable.
  World/visit changes split windows. Memory usage and cumulative JVM GC count/time help
  identify allocation/collection pressure; they are not proof that this mod caused a GC.
- Scope timing includes nested scopes; **never sum all stages as total mod CPU cost**.
  Frame intervals and frame-loop wall time include VSync/frame limiting/GPU waits. Worker
  media/DNS time can include network waits. This is not a GPU profiler or CPU stack sampler.

Coverage: Minecraft frame loop and tick, all initialized screen extraction callbacks,
existing OperationalLog run/get/HUD boundaries (Safari trackers and marker rendering),
entity research and log-writer flush. SerkoAddons adds chat preparation/ticks, Bridge preview
rendering/media requests/video decode, crosshair/calculator drawing, address resolution,
pack reload decisions/completion and the central IQ event bus. IQ's old separate timing
counters are removed. Missing stage instrumentation is not reported as zero work; broad
frame/tick/screen timings let us spot remaining unattributed costs for follow-up profiling.

Off means no timer sampling, histograms or reports (only cheap hook guards). Context strings
are rebuilt only on transitions, stage prefixes and percentiles at publication. Mutable game
objects never reach the writer. Component wrappers use finally without changing returns or
exception behavior; the Minecraft frame/tick hooks record completed calls at return. Epoch/time are captured before serialization. Disabled/re-enabled
sessions discard old aggregates and ignore work started before the new enabled interval.

Use `/safari debug performance` for the latest summary; integrated builds also expose
`/serko debug performance`. For a useful test, leave logging on while playing normally;
spend 15–30 seconds on the collection/settings/history screens, scroll chat and hover media
previews, then enter a Safari run. Alt-tab once so the focus split can be verified. Send the
full SerkoAddons log for non-Safari work; saved-run logs intentionally cover only that visit.
No need to send Discord messages or run Kuudra to exercise normal menus.

`python tools/performance_report.py <log.jsonl.gz> [...]` produces an offline overview.
Real FPS/overhead validation still requires gameplay; passing headless/loader tests does
not establish a numerical performance improvement.

Final review: windows without a frame-interval sample use `fps: null` rather than a false
zero-FPS stall. Entity research flushes pending observations on the client stopping event
before the shared writer closes; abrupt process termination can still lose unflushed data.
A research note rejected by the scope's event cap reports that limit instead of success.
Future additions should reuse these bounded, opt-in services and extend regression coverage.

### Floor quest-item bounds (user supplied, October 28, 2026)

These are expected floor-drop populations per run, not inventory limits and not yet
server-verified prediction rules:

| Reward | Minimum | Maximum |
| --- | ---: | ---: |
| Wriggleworm | 3 | 3 |
| Yogi Berry | 3 | 3 |
| Bag of Seeds | 3 | 3 |
| Lime Gem | 2 | 2 |
| Purple Gem | 2 | 2 |
| Orange Gem | 2 | 2 |
| Soothing Incense | 5 | 5 |
| Shining Coin | 3 | 6 |
| Icebreaker | 4 | 5 |

Total floor quest items: 27�31. Each player may additionally start with up to two
random quest items; these must never be counted as floor-drop observations. Use the
explicit FLOOR DROP reward journal for this analysis, not inventory totals or changes.
A partial route, missed reward, or logging gap can yield fewer observations than the
minimum; it is not evidence that the server violated the range. Do not infer exact
coin/icebreaker boundaries from these bounds alone.

Both October 28 research runs observed three of each feed, two of each gem, and five
incense. The first observed five coins/four icebreakers; the second four coins/five
icebreakers. Both fit these bounds (29 quest-item reward observations each).

Debug block/entity reports are included as DEBUG/REPORT records when automatic Safari logging is enabled (16 KiB per report). They retain block IDs/states and pairing IDs, so pedestal before/after samples no longer depend on latest.log or clipboard recovery.

Contest diagnostics: opt-in CONTEST/server-result records changed tab score rows in Torrhus Canyon even during the estimated New Year scoring delay. CONTEST/score records accepted score, bracket, ticket state and remaining time. These belong to the launch log, not a saved Safari run. The first annual scoring boundary is currently estimated from an observed 8:40 remaining; diagnostics can validate it on a future rollover.
