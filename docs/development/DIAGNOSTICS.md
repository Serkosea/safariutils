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
