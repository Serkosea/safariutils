# v3 implementation ledger

Scope: the approved SafariUtils/SerkoAddons architecture review and public API transition.
Publishing requires subsequent user testing; this document tracks implementation, not completion claims.

Current review and validation reports are maintained locally and excluded from Git.
Older pending statements and counts below are historical, not a cumulative current checklist.

## Verified implementation progress

### Final v3 release cleanup

- Latest user solo test was visually successful; strict replay reproduces all 4,855 capture inputs.
- Reuse WorldEntities' tick-scoped UUID index in the scanner and CritterEntities' immutable
  species index in marker snapshots instead of rebuilding duplicate maps. Capture cadence,
  ownership decisions, Safe Mode checks, and animation clocks are unchanged.
- Added bounded, rate-limited Developer slow-operation timing diagnostics for the reported
  Cavern dips. No measured FPS improvement is claimed without another in-game sample.
- Retired duplicate private component sources and private website files are preserved locally
  outside build/deployment inputs. Settings/data migration compatibility remains intentionally
  present; deleting old-schema readers would break existing users' upgrades.

### Grouped Gimmiegold impact ownership

- October 14 22H-02M: the pre-fix tracker strictly replayed 3,812 inputs. A crosshair/interaction
  selection remained locked while a different body disappeared at the capture label. This
  misassociated the early breakout and created an extra logical identity. Two held pins remained.
- Disappearance evidence may now correct that initial selection only when the selected body is
  still visible and exactly one eligible, unowned peer disappeared near the impact. Ambiguous
  peers remain protected. Retargeting an already observed body no longer labels its next
  appearance as an initial observation; it is valid breakout evidence.
- Focused tests cover unique/ambiguous corrections, retained identity/pity, breakout recognition
  and untouched-peer visibility; the distant retry baseline tests remain passing.
- Counterfactual replay of the same 3,812 inputs with updated logic ends with all five Gimmiegolds
  caught, without the extra identity. Its 750 differing state fingerprints are expected changes,
  not strict replay verification or GPU-render verification. The separate `exploreCaptures` task
  explicitly labels this distinction and rejects input gaps/omissions. In-game group retest remains.

### Distant retry observation baseline

- October 14 21H-45M: 5,815 capture inputs replayed against the pre-fix tracker. Hideonfloor
  A11 started while A10's breakout body was still unobserved; its delayed first body was
  incorrectly classified as A11 breaking out. The catch notice then expired after ten seconds
  and the pin survived until attempt expiry. This was an observation-generation race, not lost pity.
- Attempts now distinguish an initially unobserved target from a body observed disappearing
  during that attempt. First observation/rekey establishes the baseline without marking a
  breakout. Both existing-label and fresh-body reconciliation use the same helper; ownership
  correction updates the baseline too. Added FIRST-BODY diagnostics and delayed retry tests
  with reused/new labels, retained pity and immediate caught-pin cleanup.
- The four logged Mantis Shrimp attempts all received catch resolutions and cleared their
  recatch pins. No additional persistent marker defect was established by this journal;
  transient visual correctness still needs observation. Runtime diagnostic audit flagged only
  the pre-visit waiting notice, not an in-run invariant or dropped-record warning.

### First solo-run feedback: capsule contact and Doomspiral feedback

- The October 14 21H-15M journal replayed 9,495 capture inputs against the pre-fix tracker.
  The strict diagnostic audit's sole warning was a pre-visit waiting notice, not missing
  in-run input. Capture replay now explicitly reports/excludes that exact notice only before
  its first reset/input; actual omissions, dropped records and missing boundaries still fail.
- Hideonwall attempt A34 correctly retained pity 3, but its presentation waited 439 ms for
  disappearance. Its first capsule sample was already inside the body at +39 ms. Contact
  now includes contained endpoints and first observations, not only segment crossings.
  Already-associated paths can also confirm contact on subsequent samples. First-sample
  proximity alone cannot assign a capsule; overlapping same-species contact cannot start
  a provisional shrink. No attempt timing or outgoing item-use behavior changed.
- Added first-contact ownership checks across the 36 catchable species plus overlap,
  inside/crossing/distant contact regressions. Added the exact enraged Doomspiral message
  to the existing optional Doomspiral filter, with player-message exclusion coverage.
- Integrated and 26.1.2 Developer checks pass 121,111 core and 67,007 ticket assertions;
  integrated chat/media remain 19/1,352. Actual visual timing needs the next run. No claim
  about right-click/network latency is possible from this journal alone.

### Pre-test saved-data and integration review

- Added opt-in `auditSavedData`, excluded from runtime jars. It initializes the registries
  required by sound-choice normalization, then runs production decoders against explicit
  in-memory file copies without invoking configuration load/save or import side effects.
- Nine real current/backup files passed (three each of settings, run histories and Sparkling
  collections). Settings were stable after migration and a second serialization cycle;
  no history entries or collection counters were rejected. Source bytes remained unchanged.
  This does not establish every historical setting's intended meaning or all-user migrations.
- Reviewed the live observation boundary: shared world snapshot -> immutable scanner samples
  -> shared sightings -> capture body observations; frame following resolves the same body UUID
  and uses fixed capture dimensions. No additional policy change was needed. Actual native
  entity extraction, timing and GPU appearance remain runtime validation gates.
- Clean verification passed all six standalone variants plus SerkoAddons: 121,069 shared,
  19 chat, 1,352 media and 67,007 Developer ticket checks; 484 byte-identical shared classes.
  Deployed SerkoAddons 0.3.0 (SafariUtils 3.0.0 Developer) to Skyblock 26.2 and Ticket Collecting
  with configuration mirroring disabled. Both installed jars match build SHA-256
  `f308339b2af37db332a35bb6363386b1e5c87d239a2834994d7f4222b42378db`;
  component source fingerprint `1156d6d94b875c30791ea837b9e92fe06e7b05bcce607b4f85cd3a99cd848620`.
  Previous installed jars preserved under ignored `local/pre-test-deploy-20261014/`.
  No GitHub/website/Worker publication or component snapshot synchronization performed.

### Complete scanner and capture-presentation fixtures

- Extracted the complete production label/body selector into `CritterScan`, operating on
  one immutable observation batch. The live adapter samples names/types/positions once,
  resolves selected identities back to entities and publishes through the existing shared
  scan snapshot. Removed the old parallel entity-based selection implementation.
- `CritterBodyTypes` owns eligibility and verification for both scanner and capture paths.
  Preserved retained ownership, strict radii, source-order ties, Duplico interaction-first
  fallback, Gazer unnamed stands, sparkling labels and Hideyho's self-body rule.
- Whole-scan fixtures cover 1,000 dense retained/fresh groups plus special pools, removed
  bodies, negative cell boundaries, exact-radius rejection and failure diagnostics. All 36
  catchable-species presentation fixtures now begin with actual scanner output before
  exercising capture ownership, live effects, animation clocks and frame geometry.
- No saved-data, timing-window, Safe Mode or capture-policy changes. These fixtures do not
  establish live entity extraction or GPU appearance; new-run validation remains required.
- Verification: all six standalone variants and SerkoAddons pass 121,069 shared checks;
  integrated chat/media pass 19/1,352 checks and Developer ticket simulation passes 67,007.
  Shared-class parity is 484 byte-identical classes. Existing darkness deprecation remains.
  No installation, publication, Worker update or component snapshot synchronization performed.

### Scanner entity classification cost

- Replaced the generic-body classifier's chained registry-ID reads with one type lookup
  per non-label entity and a shared exclusion set. Interaction and unnamed armor-stand pools
  keep their existing routing; recognized labels still bypass body classification entirely.
- Preserved all 26 excluded prop/projectile types, including happy-ghast props, and added
  checks for every exclusion plus 26 allowed body/unknown-type cases. Species eligibility
  remains a separate stricter gate; unknown entity types are not implicitly verified critters.
- No capture timing, type acceptance, saved data or settings behavior changed. This removes
  redundant registry lookups; no measured FPS or full live scanner correctness claim is made.
- All six variants and integrated checks pass: 104,902 shared, 19 chat, 1,352 media and
  Developer's 67,007 ticket checks. Parity confirms 478 identical shared classes. Existing
  darkness deprecation remains. No deployment/publication or component snapshot sync.

### Species body-policy consistency

- Found a Gazer mismatch: ranking/verification expected an armor stand, but the missing
  body-table entry let initial generic pairing accept unrelated nearby mobs before its
  armor-stand fallback. Added Gazer to the shared type table, preventing that cross-species
  assignment. Ranking now uses the same existing three-block Gazer radius as retention/fallback.
- Initial/retained pairing and capture verification share type evidence; removed duplicate
  special-case verification/priority branches. Duplico's interaction/silverfish support and
  unknown-species proximity fallback are preserved; unknown proximity is not verified evidence.
- Added 112 acceptance/verification checks for Gazer, Duplico, Bloodbat and both Hideons,
  including unrelated nearby mobs and unknown-species fallback. All six standalone variants
  and integrated checks pass: 104,850 shared, 19 chat, 1,352 media, and Developer's 67,007 ticket
  assertions. Parity confirms 478 identical classes. Existing darkness deprecation remains.
- This fixes a concrete scanner path, not proof that it explains every historical Gazer bug.
  Live grouped-Gazer/capture tests remain required. No deployment/publication or saved-data changes.

### Central scan assignment boundary

- Moved scan ranking/assignment/output ordering into ScanPairing, used directly by
  CritterEntities. Each label is ranked and resolved once. Stable priority/distance ordering
  preserves existing tie behavior; sightings are still published in original scan order.
- Species/type/distance and retained-pair rules remain in the existing entity adapters.
  The central claimed-body set is read-only to adapters. An invalid duplicate claim produces
  an unpaired result and a SCAN/PAIRING ownership-conflict diagnostic while retaining valid
  assignments, rather than publishing shared body ownership or aborting the scan.
- Added 38,249 assertions over 1,000 seeded competing-candidate scenarios against the previous
  full-list ordering/claim behavior, plus faulty-adapter isolation and immutable ownership/output.
- All six standalone variants and integrated checks pass: 104,738 shared, 19 chat, 1,352 media,
  and Developer's 67,007 ticket checks. Parity confirms 478 identical shared classes.
  This covers assignment orchestration, not live entity naming/type extraction or a full GPU
  replay. No deployment/publication, installed-data changes or component snapshot sync.

### Scan-local spatial candidate reuse

- Extracted CritterEntities' bucket lookup into NearbyIndex and reused immutable neighborhood
  lists within each scan. Retained-pair checks, ranking and final matching share the same index;
  repeated queries no longer gather/sort/allocate the same candidate list. Exact distance,
  body-type eligibility, source-order ties and claimed-body ownership rules remain unchanged.
- Indexes are rebuilt for every scan. Empty neighborhoods are not cached; each indexed entry
  can appear in at most 27 cached neighborhoods, so query repetition cannot grow storage.
  This is reduced duplicate work, not a measured in-game FPS claim.
- Added 12,010 checks against an independent full-list reference, with seeded mixed coordinates,
  negative/boundary positions, source ordering/duplicates, radius completeness, immutable output,
  repeated-query reuse, source-list mutation and fresh-scan isolation.
- Pairing diagnostic throttling/expiry now uses the existing monotonic clock; the five-second
  interval is unchanged. No extra logging and no settings/data-format changes.
- All six standalone variants and integrated checks pass: 66,489 shared, 19 chat, 1,352 media
  and Developer's 67,007 ticket assertions. Parity confirms 475 identical shared classes.
  Existing DarknessFilter deprecation remains. No deployment/publication or snapshot sync.

### Capture presentation through frame projection

- Extended the existing capture-to-presentation fixtures through the production frame loop.
  A package-scoped frame adapter supplies explicit body positions for headless checks; live
  rendering keeps the same world lookup, geometry, partial tick and monotonic timeline.
  The live adapter is shared, not allocated per frame. No capture selection/timing changed.
- Added 32,904 assertions from real tracker/effect snapshots across species and ordinary/master
  attempts: moving-body anchors, stepped native dimensions versus linear rendered size,
  untouched neighbor isolation, pin exclusion, immutable ownership/timelines and no projection
  work or tiny marker remnant after animation completion. Completed attempts emit no frame.
- This closes frame-loop coverage beyond isolated geometry tests, not entity scanning or GPU
  rendering. Existing change-only ownership diagnostics remain; no per-frame log volume added.
- All six standalone variants and integrated verification pass: 54,479 shared assertions,
  19 chat, 1,352 media and Developer's 67,007 ticket checks. Parity verifies 474 identical
  shared classes. The existing darkness deprecation warning remains. No deployment/publication.

### Verification-only build matrix and strict component inventory

- Added standalone `VerifyBuilds` and integrated `VerifySafariBuilds` / `VerifyAllBuilds`.
  They reuse the existing sequential variant checks without copying release artifacts,
  deploying jars or mirroring player configuration. BuildArtifacts uses the same matrix gate.
- Parity now rejects unexpected shared-package classes as well as missing/mismatched ones.
  The five intentional integrated UI adapter classes and their nested classes are explicitly
  allowed. Initial strict equality rejected these legitimate adapters; the final scoped policy
  passes with all 473 standalone classes byte-identical in SerkoAddons.
- Clean standalone builds passed for all six variants; clean integrated checks passed too.
  Final VerifyAllBuilds passed with 21,575 shared, 19 chat, 1,352 Bridge/media and 67,007
  ticket assertions. The existing DarknessFilter deprecation warning remains.
- Added a current validation map separate from this historical journal. World-scan/render,
  real authenticated client/party, peer sync, media/GPU and saved-data game checks remain open.
- No runtime behavior, saved settings/data, installed jars, Worker, website or published release
  changed. Component snapshot synchronization remains explicit and was not performed.

### Decoded preview handoff ownership

- Replaced the mutable pending Download field with a single-result ownership handoff. Rejected
  offers are disposed, successful takes transfer cleanup responsibility, and close atomically
  detaches/disposes pending work. Eviction, shutdown and render-dispatch rejection use the same path.
- Render dispatch failure now closes the handoff immediately, marks failure and emits a type-only
  diagnostic. Stale/released preview completion cannot start an upload. Native dimension reads
  are inside partial-upload cleanup, and pixel counts are captured before texture ownership transfer.
- Added 206 checks for duplicate/late offers, single-take and repeated-close semantics, plus
  concurrent offer/close and take/close ownership. Integrated checks pass 21,575 shared, 19 chat,
  1,352 Bridge/media and 67,007 ticket assertions. Shared-class parity remains verified (473).
- These verify handoff/cleanup logic, not actual GPU driver failures. No deployment/publication.

### Live preview TLS smoke verification

- Added explicit SerkoAddons `previewTransportSmoke`, separate from offline `check` and excluded
  from artifacts. It uses fixed public test URLs, reads no player settings/credentials and logs
  only hostnames, status codes and byte counts.
- Actual resolved-address transport completed HTTPS requests to example.com (200), tenor.com
  (200) and cdn.discordapp.com (403 at the root). Hostname-mismatch and expired certificates at
  BadSSL were rejected with SSLHandshakeException, not merely DNS/connection failures.
- This closes basic live TLS transport/certificate-rejection validation, not signed attachment
  preview, decoder/native upload, GPU or in-game lifecycle validation. No game deployment,
  release, GitHub push, website or Worker changes.

### Preview cleanup failure isolation

- Frame/texture batch release now attempts every resource even if an individual release throws.
  Failures produce one count-only diagnostic; decoded frame cleanup, partial-upload rollback,
  pending-result disposal and texture eviction share the policy. Pending ownership is detached
  before closing its resources so a cleanup failure cannot retain the same pending batch.
- Preview channel close now settles the response future even before handler initialization;
  request-construction failures also settle it, and timeout/interruption cancels the future before
  closing the channel. This avoids unnecessary timeout waits after early channel failure.
- Added 129 cleanup checks covering every failure combination in a six-resource batch plus an
  empty batch. These test cleanup orchestration, not actual GPU-driver failures.
- Integrated checks pass: 21,575 shared, 19 chat, 1,146 Bridge/media, 67,007 ticket checks, and
  473 byte-identical shared classes. No deployment/publication. Live TLS and native/GPU integration
  validation remain open; tests must not be described as proof against all runtime failures.

### Preview resolved-address transport

- Replaced Bridge preview HttpClient fetches with a preview-only Netty HTTP/1.1 transport using
  Minecraft's existing dependency. DNS results are validated together, then the connection uses
  the selected resolved InetSocketAddress directly. Every redirect/HTML candidate repeats this
  process. This closes the separate validation-versus-connection DNS lookup gap described below.
- TLS retains the original peer hostname and HTTPS endpoint identification with default JSSE trust;
  no trust-all manager or hostname-verification override. Raw signed paths/queries and the original
  Host header are preserved. No cookies, authentication headers, automatic redirects or proxy are
  inherited. NAT64/6to4/Teredo destinations are excluded by the conservative address policy.
- Transport has one daemon event loop, bounded header parsing and streaming body limits, timed
  worker waits, close-on-cancellation and explicit shutdown. Alternate addresses consume the same
  request budget. Informational responses are handled; unexpected compressed responses fail closed.
- Embedded-channel/request/TLS-configuration fixtures cover exact/oversize/declared-size/incomplete
  bodies, informational responses, redirects, encoding rejection and unmodified signed queries.
  Integrated checks pass 21,575 shared, 19 chat, 1,017 Bridge/media and 67,007 ticket assertions;
  parity confirms 473 byte-identical shared SafariUtils classes. No new dependency or warnings.
- Live TLS/CDN compatibility, certificate-failure integration and native/GPU failure injection remain
  validation work. Platform DNS and codec calls are cooperative rather than forcibly preemptible.
  This is not an absolute networking-security guarantee. No deployment or publication performed.

### Preview destination policy review

- Extracted Bridge's destination filter into a pure tested policy, used for every initial,
  redirected and HTML-derived request. Local names with trailing dots now normalize before
  checking; reserved IPv4, benchmarking/documentation ranges and documentation IPv6 are rejected
  alongside existing loopback/private/link-local/multicast checks. Signed URL queries are unchanged.
- Added 38 deterministic name/address checks using raw addresses without DNS or network access.
  Integrated checks pass: 21,575 shared, 19 chat, 1,000 Bridge/media, 67,007 ticket assertions.
- Explicit limitation: DNS validation and HttpClient connection resolution remain separate.
  This is not a DNS-rebinding-proof transport. Full closure requires connection-level address
  pinning with correct TLS hostname verification or an intentionally restricted preview policy;
  do not claim that the preflight alone guarantees local-network isolation.
- No game deployment, publication or backend/website changes.

### Bridge decode budgets and persistence follow-through

- Bridge now carries its existing shared 30-second request budget through still/GIF/video
  decoding, checking between codec calls and before publishing decoded frames. A cumulative
  one-billion-source-pixel ceiling includes video frames skipped for output and GIF restore
  copies; fallback candidates do not receive fresh budgets. This is cooperative containment,
  not hard preemption of a native or third-party codec call.
- Invalid/nonfinite video duration is rejected. Decoded video dimensions are checked before
  conversion; still images finished after expiry are closed. GIF/video failures release retained
  NativeImages and emit format/exception-class-only diagnostics, never signed URLs.
- Six deterministic checks cover cumulative decode exhaustion, overflow-sized dimensions,
  invalid dimensions, shared deadline and cancellation. Integrated media total is 962.
- Reviewed ChatHistoryStore's immutable checkpoints, failed-write dirty retention, protected-source
  recovery and OrderedSnapshotWriter shutdown ordering. Existing recovery/revision regressions
  cover retry and late-worker protection; no additional persistence behavior change was needed.
  Native/GPU failure injection, DNS/rebinding review and live media behavior remain separate gates.

### Live capture-to-presentation integration

- Headless fixtures now drive all 36 capsule-tracked species through the real tracker and live
  CaptureEffects adapter, with isolated settings and local catalog data (no user files). Both
  ordinary and Masterful attempts cover physical confirmation in groups, untouched-neighbor
  isolation, full-size species geometry, immutable snapshots, unchanged animation clocks and
  completed-species cleanup. These complement model replay and pure geometry tests; they do not
  simulate Minecraft's entity scanner or GPU.
- This exposed an exact-target path that skipped disappearance confirmation entirely. Exact
  ownership now bypasses only replacement-target selection, not confirmation of its own body.
  The existing presentation-commit guard still prevents double animation starts. Wall queries
  return an empty view when no client exists, matching their existing no-world behavior.
- Corrected obsolete handoff descriptions of private builds, API availability, remote sync,
  Developer source layout, ticket lists and compressed diagnostics. Historical ledger entries
  below describe their point in time, not a fresh outstanding-work list.
- Verification: all six standalone edition/profile checks and integrated checks pass with
  21,575 shared assertions, 19 chat, 956 Bridge/media and 67,007 ticket assertions. Parity verifies
  473 byte-identical shared classes. The existing DarknessFilter deprecation warning remains.
  No deployment, publication, website changes or backend changes in this continuation.

### Recorded capture inputs and centralized completion

- Confirmed species completion now has one capture-owned entry point. It settles logical
  attempts before clearing static/visual projections, retires queued retries/early acknowledgements,
  ignores late species events, and is idempotent. Partial totals do not settle attempts.
- `CaptureEffects` centralizes live projection calls while preserving their order and geometry.
  Recorded replay substitutes inert effects in a separate JVM; it cannot touch the running game.
  Remembered far-target evidence, interactions, and completion timestamps are explicit inputs.
- Developer diagnostics now record bounded versioned `ci` inputs: visit seeds, immutable bodies,
  capsule/label observations, attempt evidence, outcomes, interactions, and completed totals.
  Every input includes a deterministic logical-state fingerprint. Unchanged body batches reuse
  their baseline; inputs are bounded to 256 observations per list. Disabled logging or missing
  boundaries never silently claims a complete replay.
- JSON construction/serialization occurs on the existing bounded writer from immutable captured
  data. Fingerprinting reads model fields without allocating duplicate logical/attempt snapshots.
  Cross-species outcome/retry queues retain arrival order for deterministic processing.
- `replayCaptures` runs only from regression sources, excluded from artifacts. It rejects gaps,
  missing boundaries, malformed geometry, invalid sequences, and changed expected states.
  General diagnostic audit reports these inputs separately rather than claiming to validate them.
- Round-trip fixtures cover 36 capsule-tracked species, remembered far targets, capsule/label
  evidence, replacement bodies, retries and expiry: 332 recorded inputs reproduce their states,
  including serialization delayed until after subsequent model mutations.
- Render projection now uses one pure geometry operation for body following, interpolation offsets
  and linear shrinking. Another 1,665 checks cover stepped native dimensions, special anchors and
  body disappearance without adding a second animation clock or per-frame state. Shared total: 21,172.
- Final verification passes all six standalone profile/edition combinations and integrated checks:
  21,172 shared, 19 chat, 956 Bridge/media and 67,007 ticket assertions. SerkoAddons parity confirms
  473 byte-identical shared classes. Whitespace checks pass; the existing DarknessFilter deprecated
  API warning remains. Built artifacts were verified, not deployed to game instances or published.
- Scope boundary: this replays the authoritative capture model, not the Minecraft entity scanner,
  GPU frames, static-catalog effects or complete visual projection. Actual current-build logs are
  still needed to validate capture journaling in-game. No deployment, release or backend change.

### Deferred capture evidence and diagnostic replay hardening

- Deferred attempts retain their original camera ray instead of sampling the camera again
  when a body finally appears. This prevents subsequent camera movement from redirecting
  delayed ownership. Actual-tracker fixtures cover both body arrival orders without a client.
- Pairing traces now include optional body/logical axis identities, created only with diagnostics
  enabled. Replay validates dimensions and uniqueness while retaining compatibility with older
  matrix-only traces. These are critter entity IDs, not Minecraft account/session credentials.
- Replay limits retained record size to 256 KiB, drains oversized lines, rejects corrupt UTF-8
  and fractional/overflow/string sequence values, and explicitly warns about incomplete/empty
  input. Resolved target-correction bookkeeping is released. Cancellation preserves an incomplete
  result, and replay uses its own bounded worker rather than competing with HTTP requests.
- Added `replayDiagnostics` for offline audits of ordered sealed segments, using the production
  parser and policy validators without loading client settings. The October 14 02H-38M log
  parsed 7,196 records without tracked warnings; its lack of newer capture traces means this
  does not disprove its reported visual bugs or validate the new implementation in-game.
- Shared regression count is now 19,163. Full recorded-world/projection replay and current-build
  in-game testing remain separate open gates. No game deployment or publishing in this increment.

### Immutable observation and cross-component continuation

- Body identity reconciliation consumes immutable species/label/body/box observations rather
  than repeatedly reading entities. Capsule paths and temporary labels also enter through
  explicit observation lists; evidence application retains its native-shrink, capsule, label,
  correction, reconcile and prune ordering. Target scoring samples one camera ray per event.
- Headless replay now drives the actual tracker through body scans, rapid recatches, replacement
  bodies, terminal catches, native shrink repair, capsule intersection and disappearing-target
  correction. Tests preserve isolated in-memory settings and never load/write user settings.
- A new regression reproduced scan-order-dependent identity theft when a newcomer inherited
  the label of a body still in the same batch. Label fallback and coalescing now consider the
  complete observed-body set; new identities use the existing atomic label-binding path.
  All six three-body scan permutations preserve ownership. Rejections get concise diagnostics.
- Shared regression total: 19,151 assertions. Recorded-world replay remains incomplete: these
  are live-implementation observation fixtures, not proof that every in-game scan is correct.
- Bridge endpoint construction encodes credentials exactly once, retains mandatory TLS and
  rejects ambiguous schemes/userinfo/query/fragment input. Added 24 endpoint assertions;
  integrated Bridge/media total is 956.
- Removed the unused ChatUtils `ChatRecord` mirror buffer and its unused accessor. Vanilla chat
  retention and the canonical saved-player history remain unchanged. Channel confirmation expiry
  now uses subtraction for monotonic-clock rollover safety.
- Added integrated `VerifySafariParity` and wired it into `BuildAllArtifacts`. The explicit
  verification compares shared class hashes; routine `check` does not rebuild the standalone
  matrix. No game deployment, GitHub push/release, website or Worker change in this continuation.
- Final verification: all six standalone combinations pass; integrated checks pass 19,151
  shared, 19 chat, 956 Bridge/media and 67,007 ticket assertions. The new parity task confirms
  468 byte-identical shared classes. Removed ChatRecord is absent from the built integrated jar.

### Capture replay and production gateway continuation

- Capture pairing now emits bounded, typed pre-mutation cost matrices and assignments for
  breakout/form-rekey decisions. Offline replay uses the production matcher; fixtures cover
  overlapping groups, unmatched bodies, altered results and malformed trace indices. This
  extends policy replay but is not complete recorded world-observation replay.
- Special-shape and capture rendering now share the same bottom-anchored scaling function.
  Added 1,110 frame geometry assertions for stepped native sizes, stable dimensions and
  species offsets; integrated shared assertions now total 17,118. No timing or sizing policy changed.
- Authenticated lookup, authentication and room URLs now share `BackendEndpoints`, preserving
  the existing endpoint while preventing independent host drift between providers.
- Worker ingress enforces streaming byte limits and a body-read deadline for both authentication
  and lookups. Awaited route dispatch contains asynchronous failures in the JSON error contract.
  Room forwarding includes only upgrade and opaque membership metadata, not client cookies,
  authorization, IP headers or raw roster/lobby identifiers.
- Both authentication phases are rate-limited before storage/cryptographic work with separate
  phase budgets. Malformed session submissions cannot bypass challenge-request limits.
- Worker tests pass 26/26, including streamed UTF-8, oversized/stalled uploads, asynchronous
  errors and the room header allowlist. Integrated checks pass 16,008 shared assertions,
  19 chat, 932 media and 67,007 ticket checks; all six standalone combinations pass too.
- Approved production credential installed through Worker secret input without displaying it.
  Hardened Worker deployed as version `499c3387-8648-4456-8f30-a048cd8584e4`; configured quota
  remains 300/5 minutes with the existing 20 percent reserve. The existing service/domain retains
  its development name to preserve client compatibility; this does not select a different key.
- Live health/authentication-key readiness and unauthenticated 401 smoke checks pass. A real
  client-authenticated Hypixel lookup and party-room run remain required: health does not prove
  upstream credential acceptance or in-game behavior. No mod release, GitHub push, website
  update or game-jar deployment was performed in this continuation.
- Final geometry consolidation verified across all six standalone variants and the integrated
  build, with 17,118 shared assertions. All 464 standalone SafariUtils class entries are
  byte-identical in the final integrated jar. Final deployed health and malformed-session
  rejection smoke checks pass too.

### Latest cross-component pass

- Settings imports now preserve original disk bytes and the current in-memory settings
  in one uniquely named import-backup directory before replacing the source. Failed backup
  creation aborts import; import no longer relies on the best-effort normal save method.
- Protected Safari settings save working edits through the shared recovery writer while
  retaining the original source. Bridge/ChatUtils backup failures now retain valid loaded
  settings in protected mode instead of unnecessarily falling back to defaults.
- Transfer formats reject fractional, overflowing and nonnumeric version identifiers;
  fixtures cover malformed compressed input, expansion limits and immutable import backups.
- Banner/celebration clocks, temporary Astral duration and data-tool confirmations use
  monotonic elapsed time. Ticket reminders accept durations rather than cross-clock deadlines.
  Local sound playback also advances outside Hypixel for previews and optional contest alerts;
  connection cleanup and server/command guards are unchanged.
- CapturePresentationRegression exercises actual presentation mutations: duplicate starts,
  corrected targets, rekeyed bodies, new owners, stale outcomes, late expiry and visit cleanup.
  This complements policy replay; complete recorded world-observation replay remains open.
- Bridge media uses monotonic cache/playback timing and immutable cumulative frame boundaries
  with allocation-free binary lookup. Variable delays and loop boundaries are tested.
- Chat duplicate tracking now uses one bounded publication-ordered cache, removing the expiry
  queue that could retain evicted groups. Divider grouping is bounded too. Persisted chat dates
  remain wall-clock timestamps; compaction uses monotonic time and unchanged duplicate windows.
- Integrated checks: 15,505 shared assertions (including a 10,000-entry cache pressure fixture),
  19 chat checks, 932 media checks and the 67,007-check ticket simulation. Worker local tests: 22/22.
- All six standalone profile/edition checks also pass. All 462 standalone SafariUtils class
  entries are byte-identical in SerkoAddons; its additional shared-package classes belong to
  the five integrated UI sources, not duplicate or stale standalone component classes.
- No deployment, release, push, website change or production secret update in this pass.
  These automated checks do not substitute for game testing or production smoke tests.

- Shared API client sources now compile independently of the retired Private edition.
- Standalone Developer 26.2 and integrated SerkoAddons compilation/check tasks passed.
- Backend suite: 19 passing tests, including persistent quota accounting across object restart.
- Confirmed Production quota: 300 requests per five minutes; configured local budget reserves
  20 percent (240 requests), counts retries, and additionally respects upstream headers.
- Production key file located and matching local copy confirmed without displaying its contents.
  Secret rotation/deployment and production smoke testing remain pending.
- Compilation alone does not establish behavioral parity; remaining gates below still apply.

### Continuation: shared reliability and build verification

- Replaced unbounded API queue and ad-hoc async ownership with `BoundedRequests` and
  `SingleFlight`: bounded admission, failed futures on overload/shutdown, isolated subscriber
  cancellation, and no stale in-flight entries after synchronous completion.
- Centralized Minecraft profile HTTP lookup in `MinecraftProfiles`; UI and backend no longer
  issue independent identity requests. Persistent rank data remains in `PlayerIdentityStore`.
- Auth sessions are account-scoped, key preparation has a timeout, rejected old sessions cannot
  invalidate a newer token, and API/auth/profile response bodies have streaming byte limits.
- Party refresh application and failure updates are fenced by visit/connection identity.
- Capture presentation uses one immutable monotonic timeline; duplicate presentation commits
  cannot restart it. This is not yet a complete capture reducer/replay redesign.
- Chat-history encoding now fails the entire save instead of silently dropping failed entries.
- Both projects run the same headless core regression suite (36 checks), plus jar edition
  verification. Source provenance is generated identically and included in startup diagnostics.
- `BuildArtifacts` and deployment paths now require verification. Builds no longer implicitly
  copy component sources. No deployment, public release, or production secret update performed.
- Final local verification: all six standalone profile/edition combinations and SerkoAddons
  pass `check`, including 36 core regression assertions. Backend tests: 19/19 passed.
- Standalone 26.2 Developer and SerkoAddons have identical SafariUtils source provenance and
  all 409 SafariUtils class files are byte-identical. Seven jars scanned: no matches for either
  saved personal or production API key. Existing DarknessFilter deprecation warning remains.

Next: capture observation/reducer replay and ownership fixtures; remaining lifecycle/party-cache
audit; stable chat/session identities and structured Bridge/media refactor; persistence/UI audits;
production service provisioning/secret rotation and smoke tests; final source snapshot and game tests.

### Continuation: workspace organization and capture matching

- Extracted standalone packaging/deployment tasks from the main build file into
  `gradle/distribution.gradle`. Routine nested builds reuse verified incremental outputs;
  `cleanArtifacts=true` explicitly requests clean compilation. SerkoAddons artifact packaging
  now depends on `check`, not just `jar`, and forwards the clean-matrix option.
- Added `docs/development/WORKSPACE.md` documenting source ownership, local-only directories,
  source synchronization, verification and deployment. Added an ignored `local/` convention
  and key-filename exclusions so future scratch work does not clutter the root or leak keys.
- Removed four verified empty staging directories only. Saved credentials, backend/site data,
  Gradle caches, configurations, and existing release artifacts were preserved.
- Centralized both breakout and resolved-form assignment in `CapturePairing`, preserving
  existing spatial thresholds and singleton behavior. Invalid/non-finite observations remain
  unmatched instead of corrupting assignment calculations.
- Seven targeted pairing fixtures plus 500 deterministic generated groups compare the actual
  matcher with exhaustive optimal assignments, checking uniqueness and determinism. Combined
  headless suite: 543 checks. Full capture lifecycle replay remains an open gate below.
- Confirmed nested `buildDeveloper26_2` reruns verification with compilation/resources/jar
  up-to-date rather than cleaning and recompiling. No installation or publication performed.
- All six standalone edition/profile checks and the integrated SerkoAddons check pass with
  the expanded 543-check suite. The existing DarknessFilter deprecation warning is unchanged.

### Capture outcome continuation

- Extracted the live outcome-selection policy into a pure one-pass selector, without
  changing existing caught/breakout fallback windows or species-specific rules.
- Added 14 deterministic fixtures covering overlapping throws, visible breakout evidence,
  absent targets, delayed notices, resolved attempts, retry timestamps, same-tick ordering,
  and exact fallback boundaries. Combined headless suite now contains 557 checks.
- Made terminal attempt resolution idempotent. Outcome/expiry marker cleanup now preserves
  bodies owned by another active presentation rather than clearing/suppressing them via stale
  aliases. Added concise ownership-preservation diagnostics and active/caught invariant checks.
- Full observation-to-render replay, ownership transfer redesign, and runtime testing remain
  open; these fixtures validate selection policy, not the complete Minecraft capture lifecycle.

### Shared collection cache continuation

- Replaced unbounded player collections and separately stored party intersections with one
  bounded, synchronized per-player snapshot store. Party intersections derive from that store;
  UI readiness checks only inspect membership and do not calculate intersections.
- Party refreshes stage their complete collection batch before the existing client-thread
  connection/visit/composition fence; rejected or malformed batches do not partly publish it.
  Failure callbacks now use the matching inside-Safari and composition checks as well.
- Present members' catches update their own cached collections even when an absent member
  prevents the species from being shared. In-flight refreshes/manual lookups preserve catches
  observed after their request checkpoint; later independent snapshots may replace older data.
- Added 16 cache regressions (573 total): bounds/LRU, immutable snapshots, derived intersections,
  partial-party catches, missing versus empty data, atomic invalid-batch rejection and in-flight
  catch preservation. No periodic refresh or additional network request was introduced.
- Capture ownership transfer still needs deeper lifecycle replay; this continuation audited
  its existing paths but did not claim or complete that acceptance gate.

### Capture ownership registry continuation

- Consolidated presentation body aliases and reverse ownership into `CaptureOwnership`.
  Recatch rendering, live following, correction, rekeying, cleanup and invariant checks all
  consume this index instead of maintaining a separate alias set on each presentation.
- Intentional behavior refinement: newer attempt IDs may supersede older owners, but older
  claims cannot steal a body from a newer active presentation. Rejected corrections preserve
  their existing aliases; accepted corrections replace them atomically. Attempt IDs are
  monotonic and registry state is cleared on visit reset.
- Active ownership clears obsolete pin/caught suppression for its newly claimed body.
  Repeated claims retain the existing presentation clock. Rejected claims are logged once
  per attempt/body for diagnostics rather than repeated every frame.
- Added 12 targeted fixtures and 1,000 seeded ownership-operation sequences (1,585 total
  checks). This tests the production ownership index, not full entity observation replay.
- StillCritters' remembered-world state and full logical-attempt lifecycle remain audit work;
  do not mark the complete capture architecture gate as done on the strength of these tests.

### Diagnostic infrastructure continuation

- Fixed repeat tracking's unique-message early-return path by replacing it with a tested,
  hard-bounded 512-entry coalescer. Evictions/expiry retain summaries, text is bounded before
  caching, and cache locks no longer call the logging writer or initialization path.
- Added versioned capture outcome evidence (`co`, schema 1) and replay through the actual
  shared selector, including the direct-breakout prepass. Records are emitted on selection
  and carry attempt IDs/evidence/timing, without player identity or credentials.
- Replay now reports queue-drop warnings as data gaps. Added diagnostics documentation
  describing schema, bounds, interpretation and remaining coverage limits.
- Added 13 regressions, including a 10,000-unique-message burst and altered-decision replay;
  combined headless suite: 1,598 checks. Broader component diagnostic audits and full
  observation replay remain unfinished; this is not a claim of complete coverage.

### Persistence safety continuation

- Atomic JSON writers now use unique same-directory staging files and a fixed set of 32
  destination locks. Concurrent writes exposed Windows replacement failures in the new test;
  serialized same-destination writes resolve that without retries or an unbounded lock map.
  Failed writes clean their own staging file while retaining the original exception.
- Shared schema parsing distinguishes missing legacy versions from invalid/fractional/negative
  or overflowing versions. Unrecognized schemas are treated as read-only, not migrated as zero.
  Settings import rejects unsupported schemas before replacing live settings or writing files.
- Removed settings migration's duplicate permissive schema parser, connected the learned
  static catalog to the same parser, and retained read-only protection through recovery catches.
  Shared helpers also apply to existing SerkoAddons Bridge/ChatUtils persistence callers.
- Added 16 filesystem/schema regressions (1,614 total), including concurrent writes, failed
  replacement preservation, staging cleanup, immutable upgrade backup and unknown-field merge.
  Tests only use their own temporary directory; no installed settings/history were modified.
- These primitives prevent write interference, not stale-snapshot ordering across independent
  callers or cross-process writers. Per-store revision/lifecycle auditing remains open.

### ChatUtils provenance continuation

- Added a client-thread weak identity provenance index in SerkoAddons. Stored messages get
  stable in-launch origin IDs; restored render objects reuse that origin. Equality of content
  cannot confuse a newly received message with a historical one.
- Visible-history clearing no longer erases origin metadata for GuiMessage objects retained
  by Minecraft snapshots. Reference-queue cleanup releases metadata when objects are collected,
  instead of a separate strong previous-message set retaining discarded chat objects.
- Restored DMs no longer update rank observations or resolve pending command responses.
  Existing restoration exclusions for notifications and recent DM names remain intact.
- Added content-free load/save/restore/view-detach diagnostic counts. Existing disk schema,
  history retention and settings are unchanged. IDs are not yet persisted between launches.
- Added `chatHistoryRegressionTest` to SerkoAddons `check`: 8 provenance cases, including
  deterministic reference-queue cleanup, alongside the 1,614 shared regressions. No mixin
  signatures changed. Runtime restoration/compaction integration still needs game testing.

### Bridge media work lifecycle

- Separated two bounded download/decode workers (16 queued tasks) from HttpClient's
  transport executor. Workers capture their transport instead of recreating it after shutdown.
- Preview eviction cancels queued/running work; cancellation is checked between fallback
  requests, GIF/video frames and streamed body chunks. Shutdown settles queued task handles.
- Decoded frames awaiting client-thread upload now have explicit pending ownership and are
  closed on eviction/shutdown even if the upload callback has not run yet.
- Removed signed source URLs from texture debug labels and exception display text. Added
  content-free queue rejection, request error type, unavailable and frame/pixel diagnostics.
- SerkoAddons `check` passes 1,614 shared, 8 chat and 7 media lifecycle checks. Existing
  media URL resolution, signed queries, animation formats and settings are unchanged.
- This is not the full media acceptance gate: streaming-body deadlines, pre-allocation
  decoder limits, partial texture-upload failure cleanup and in-game lifecycle tests remain.
  No deployed jars, website, Worker secrets or repositories were published by this step.

### Bridge bounded responses and decode/upload safeguards

- Generalized shared `HttpBodies` streaming limits to binary responses without changing
  existing UTF-8 API callers. Bridge no longer blocks reading an InputStream after headers:
  complete response futures have a 10-second wait and cancellation on timeout/interruption.
- Added a monotonic 30-second network budget and 32-request ceiling shared by all fallback,
  redirect and HTML candidates. HTML bodies are limited to 512 KiB during receipt rather
  than after reading up to the image limit. Existing image/video byte ceilings remain.
- Still-image headers (STB and ImageIO paths) and individual GIF frame dimensions are
  checked before pixel allocation. Native conversion failures release their image.
- Partial texture registration failures release accepted textures and untransferred frames,
  report a generic UI failure and emit an exception-class-only diagnostic.
- Added 3 shared binary-stream regressions and 4 media budget cases. Integrated check passes
  1,617 shared + 8 chat + 11 media checks. Settings, URLs/signatures and format choices unchanged.
- Remaining media work includes decoder work budgets, DNS/rebinding review, native/GPU
  failure injection and in-game tests. The network budget is not a hard preemption guarantee
  for blocking DNS resolution or a native decoder. No deployment/push/release performed.

### Run-history partial-load protection

- The production decoder now recovers readable records from malformed neighbors and marks
  the source read-only whenever any entry was rejected. Raw rejected entries remain in the
  preserved document; no migration or subsequent normal save overwrites the partial source.
- Unsupported/invalid future envelopes with a different layout remain protected instead of
  entering unreadable-file quarantine. Legacy normalization and unknown-field retention remain.
- Integrity reads/writes reject partial or unsupported histories, and serialization rejects
  null records instead of silently dropping them. Successful legacy repair remains supported.
- New completed runs collected while a source is protected go to a separate current-schema
  recovery snapshot under backups/runs. The protected original is untouched. Recovery files
  are not automatically merged on restart: doing so requires an explicit repair/import policy.
- Added 18 production-decoder/filesystem regressions (1,635 shared checks), using only
  temporary files. Added count-only partial-load/recovery diagnostics; no installed user data
  was inspected, rewritten, migrated or deleted during this continuation.
- Further saved-store/revision auditing and in-game validation remain; this does not close
  the complete persistence acceptance gate. Nothing deployed, pushed or released.

### Capture terminal-policy and sequence continuation

- Central outcome attribution now excludes confirmed breakouts from catch candidates even
  when their earlier absent-body flag remains set. Breakout selection still uses that evidence;
  existing fallback timing is unchanged. Contradictory catch results wait rather than consume
  an escaped individual merely because the fallback delay elapsed.
- Added a pure terminal-transition policy used by CritterState: duplicate results are ignored,
  orphan/stale owners retire only their projection, and breakout-before-body explicitly retains
  its anchor. Older results cannot clear a newer retry's active logical link. Corrected the
  invariant audit to accept valid resolved-breakout anchors awaiting reappearance.
- Added transition diagnostics and outcome trace schema 2, retaining schema-1 replay with its
  original selector policy. Added 16 sequence/policy checks and one legacy trace check;
  shared suite now has 1,652 checks. These exercise production policies, not world rendering.
- Identified remaining species-scoped transient capture-artifact windows in RecatchSpots;
  their start/resolve queue needs attempt ownership before the rendering pipeline gate closes.
  Full entity observation/reducer replay and StillCritters reconciliation remain unfinished.
- No installed build, user data, published release or production service changed.

### Attempt-owned capture artifact windows

- Replaced RecatchSpots' species FIFO timers with `CaptureTransitions`, keyed by the actual
  attempt ID. Resolving/expiring one attempt never pops another same-species timer; duplicate
  terminal callbacks cannot extend a settled timer, and late results cannot revive expired ones.
- IDs are allocated once on the incoming attempt event and carried through deferred target
  binding into the authoritative Attempt. Delayed binding therefore cannot promote an older
  throw above a newer throw in presentation ownership merely by allocating its ID later.
- Preserved the 10-second pending and 5-second resolved windows and inclusive expiry boundary.
  Expiry scans all windows independently, fixing the old FIFO assumption after out-of-order
  results. Render snapshots still derive the species-level artifact-filter signal from active
  windows; verified-body exclusions and actual marker geometry are unchanged.
- Added start/settle diagnostics plus IDs for deferred/missed attempts. Added 15 production
  registry tests for overlaps, duplicate calls, retry, deferred binding, expiry and reset.
  Shared suite: 1,667 checks. Full world-observation replay and StillCritters audit remain open.
- No deployment, publication, saved-data changes or release actions in this continuation.

### Static projection attempt ownership

- Replaced StillCritters' per-species resolution deques with a tested exact-attempt registry.
  Provisional target correction now receives the authoritative attempt ID rather than selecting
  the oldest matching body. Rekey, outcome, expiry and species exhaustion share that registry.
- Duplicate starts do not reset age or position; duplicate/unknown outcomes and expiries cannot
  release unrelated suppression. Conflicting active body ownership preserves the newer/live
  projection rather than applying old caught-position cleanup or breakout restoration to it.
- Expiry examines every entry, independent of insertion order. Registry metadata is only the
  remembered-marker projection; logical identity/pity/outcome authority remains in CritterState.
- Added target-correction/owner-preservation diagnostics and 16 registry sequence checks.
  Shared suite: 1,683 checks. Existing static positions, observation rules and Safe Mode gates
  are unchanged. Spatial species-wide heuristics (including capsule/perch correlation), shared
  catch inference and complete observation-to-render replay still need audit/testing.
- No deployment, saved-data modification, publication or production-service change performed.

### Static projection spatial isolation

- New verified bodies are no longer excluded from remembered markers solely because another
  same-species attempt is pending. Attempt-owned bodies, unverified pairings, capture artifacts
  and unstable Duplico pairings retain their existing guards; visibility evidence is unchanged.
- Capsule/perch cleanup now queries the exact-attempt registry for matching species and position,
  using the existing spawn tolerance. A distant pending Hideonwall attempt no longer authorizes
  clearing this perch. Missing anchors provide no spatial evidence.
- Nine new spatial registry assertions cover species/location isolation, absent anchors,
  correction, caller tolerance and outcome removal. Shared suite: 1,692 checks; integrated
  SerkoAddons adds 8 chat and 11 media checks. No new per-frame registry or cache was introduced.
- Remaining audit: broad absence grace, ambiguous nearby supersession/shared-catch inference,
  and full world-observation/render replay. These changes do not establish in-game correctness.
- No deployment, publication, saved-data changes or production-service changes.

### Ambiguous remembered-marker retirement

- Spatial fallback now selects at most one absent, unowned remembered marker. Multiple nearby
  candidates or a live/suppressed/attempt-owned neighbor make replacement inconclusive instead
  of removing every nearby old ID. The policy is independent of map iteration order and uses
  constant selection state without creating another persistent identity index.
- Removed oldest-unverified retirement from species-only party catch messages. Such a message
  cannot identify a remembered individual (the caught peer may never have been observed).
  Existing direct inspection and confirmed species-total cleanup remain authoritative. This
  deliberately favors temporarily retaining uncertain memory over deleting another critter.
  A compact shared-catch-unlocated diagnostic records that decision without per-frame noise.
- Exact identity rekey retains old memory/absence state while another pending attempt still
  owns that old body. Removed unused shared-catch visibility heuristic and stale comments.
- Added 12 spatial-selection assertions, including order permutations and protected owners.
  Shared suite: 1,704 checks; SerkoAddons: 1,723 including chat/media checks. Full renderer/world
  replay, species-wide absence grace and additional spatial evidence refinement remain open.
- No deployment, saved-data changes, publication or production-service changes.

### Observation-scoped static absence

- Replaced species-wide capture pauses in remembered-static absence cleanup with exact body
  ownership or matching attempt-position protection. Distant same-species catches no longer
  indefinitely postpone inspection of an unrelated empty spawn.
- Encapsulated continuous absence evidence in ObservedAbsence. It uses monotonic nanoseconds,
  preserves the 1.5-second confirmation period, and restarts after observation gaps over one
  second (normal scan cadence is 250ms). Paused time, backward clocks and discarded identities
  cannot carry evidence into an unrelated cleanup. No second timer map was introduced.
- Bodyless labels remain uncertain occupancy in this absence-only path, rather than being
  discarded species-wide during captures. This does not promote labels into visible markers.
  Existing loaded/nearby/inspection checks, Troodon policy and Safe Mode gates remain unchanged.
- Added 15 headless checks for cadence, pause/restart, independent identities, lifecycle cleanup
  and nanoTime rollover. Shared suite: 1,719 checks; integrated suite adds 19 chat/media checks.
  Existing per-retirement diagnostics remain; no per-frame diagnostic stream added.
- Remaining: full observation-to-render replay and further authoritative state/presentation
  reconciliation. No deployment, publication, saved-data or production-service changes.

### Coherent entity-scan publication

- Entity scan identity is now an explicit increasing revision rather than wall-clock milliseconds.
  Every completed scan and world-clear publication advances it; resets do not reuse zero. All
  eight scan consumers were migrated, including capture reconciliation, recatch, remembered
  markers, counts, Sparkling detection and nest inspection. No scannedAt compatibility path remains.
- Sightings and the species/body/label indexes are built locally and published together only
  after all immutable copies succeed. Failed index construction does not expose a new revision
  alongside partial data. Minecraft entity objects remain live references, not frozen world state.
- MarkerRegistry includes scan revision in cache validity and change-only render diagnostics.
  This closes same-tick stale-scan reuse without forcing a new snapshot on every render frame.
- Added 7 publication checks: revision identity, retained old publications, rejected publication,
  world clear and empty scans. Shared suite: 1,726 checks; integrated adds 19 chat/media checks.
- This is not yet an atomic transaction across all trackers or a full observation/render replay.
  Those lifecycle and presentation checks remain open. No deployment, publication, saved-data
  changes or production-service changes in this continuation.

### Cache publication and mutation boundaries

- CritterState now invalidates marker snapshots directly before scan-driven mutations, parsed
  capture events, species completion and visit reset. StillCritters owns invalidation for its
  exact-attempt callbacks, corrections, rekeys, completion and reset rather than depending on
  another subsystem's later tick. Repeated invalidations only clear the existing cache.
- Fixed shared TickCache publication ordering: failed factories no longer mark stale values
  as valid under a new context. Invalidations raised during construction are retained; the same
  guard protects MarkerRegistry publication. No new persistent cache or tracker was introduced.
- TickCache is exercised through an explicit-context overload used by the runtime entry point.
  Ten tests cover reuse, failed factory/retry, world identity, configuration, explicit and
  mid-build invalidation, and cached null results. Shared suite: 1,736; integrated adds 19.
- Clarified MarkerRegistry's contract: frozen collections with live entity references, not an
  atomic transaction spanning every tracker. Full cross-tracker render replay remains open.
- No deployment, publication, saved-data changes or production-service changes.

### Sparkling ledger partial recovery

- Lifetime Sparkling counters now decode independently instead of letting one malformed entry
  discard all readable values. Exact integer validation rejects fractions, overflow, invalid
  shapes and out-of-range counters; legacy numeric strings and missing-field defaults remain
  compatible. Unknown species counters are retained for later versions.
- Any rejected known field protects the original source from overwrite. Future-schema protection
  still applies independently. Subsequent working counters save atomically to a per-launch
  recovery file under backups/sparkling rather than being silently unsaved. Recovery snapshots
  require deliberate reconciliation; they are not automatically merged on restart (double-count
  risk). Syntax-unreadable sources still use the existing quarantine/reseed path.
- Added 14 production-decoder regressions without opening installed data, including readable
  neighbors, scalar/sentinel bounds, source immutability and future fields. Shared suite: 1,750;
  SerkoAddons adds 19 chat/media checks. Existing AtomicFiles infrastructure handles writes.
- Remaining persistence work includes recovery UX/reconciliation and broader store validation;
  this does not complete the all-stores migration gate. No installed saved data was modified,
  and no deployment, publication or production-service changes were performed.

### Identity-cache partial recovery and name-index ownership

- Identity decoding now isolates malformed records and validates rank/timestamp fields with
  exact integer bounds. Bad current-rank fields no longer discard a valid permanent fallback;
  null stored UUIDs can use their valid map key. Any rejected data protects the source.
- Duplicate normalized UUID records prefer the newest timestamp and protect the source for
  review. Load indexing orders records by timestamp/UUID; older renamed/evicted records only
  remove name mappings they still own, avoiding accidental deletion of a newer mapping.
- Protected identity stores write subsequent working-cache changes to a separate atomic
  recovery snapshot, using existing delayed saves and bounded cache size. Recovery is not
  automatically loaded/merged; the protected original and its unknown fields remain untouched.
- Added 11 production-decoder regressions: malformed neighbors, signed permanent-rank fallback,
  numeric coercion rejection, duplicate UUIDs and source immutability. Shared suite: 1,761;
  SerkoAddons adds 19 chat/media checks. No installed data was opened or changed by these tests.
- Broader persistence/recovery UX and end-to-end rank lifecycle validation remain open.
  No deployment, publication or production-service changes.

### Static catalog partial recovery and schema shape validation

- Local and bundled location catalogs share field-level decoding. Malformed species lists or
  coordinates no longer discard readable neighboring positions. The decoder retains original
  keys for the existing migration/domain rules and does not mutate the source JSON.
- Partially readable local sources are protected from overwrite. Subsequent learned positions
  save to a separate atomic recovery snapshot using the existing coalesced save interval.
  Recovery is not automatically merged on restart; explicit reconciliation remains necessary.
- Shared schema validation now rejects single-element arrays rather than allowing Gson to
  coerce them into a schema number. Legacy numeric strings remain supported.
- Added 11 catalog checks (including the actual shipped resource) and one shared schema check.
  Shared suite: 1,773; integrated suite adds 19 chat/media checks. Initial verification was
  interrupted by the approval service usage limit, then resumed successfully without bypassing
  approval. No installed data was read or changed by the regression fixtures.
- Existing location-learning eligibility, coordinate migrations, Safe Mode rules and bundled
  exclusion rules are unchanged. This does not complete recovery UX or all-store validation.
  No deployment, publication or production-service changes.

### Shared recovery writer lifecycle

- Run history, Sparkling totals, player identities and learned locations now use RecoverySnapshot
  for recovery destination ownership and atomic writes instead of four filename/lifecycle copies.
  It owns one file per session, keeps retries on that destination, rejects directory changes
  without reset, validates filename stems and never deletes older snapshots when reset.
- Stores retain their own serialization and recovery semantics: run history contains only new
  runs; other stores contain working snapshots. Save delays, protection flags and normal-file
  writes are unchanged. The writer does not retain serialized contents or perform automatic merges.
- Added 11 isolated temporary-filesystem checks including failed-write retry, normalized paths,
  independent writers, resets, path rejection and unchanged protected source bytes. Shared suite:
  1,784 checks; integrated SerkoAddons adds 19 chat/media checks. Installed data was untouched.
- Recovery discovery/reconciliation and broader persistent-store acceptance remain open.
  No deployment, publication or production-service changes.

### SerkoAddons settings/history recovery and Bridge callback ownership

- Bridge and ChatUtils now decode exposed settings independently using shared SettingsFields.
  A malformed top-level field retains its default without resetting readable neighbors; source
  documents stay protected. Normal field normalization and legacy imports remain unchanged.
  Unknown fields remain in the original document and normal saves preserve them.
- Protected settings save working changes to the shared atomic recovery writer instead of
  silently dropping saves. Chat history does the same for current-session messages only;
  previously loaded messages remain in the protected original. A throwing message decoder no
  longer discards readable neighboring history. Retention remains capped at 1,000 messages.
- Recovery snapshots are local backups, not automatic migration/merge inputs. Nested invalid
  list entries and recovery discovery/reconciliation still need a broader policy; this does not
  complete all-store persistence acceptance.
- Bridge connection attempts and socket callbacks now apply state/auth/chat on the client
  executor through GenerationDispatch. Old-generation callbacks cannot publish after restart.
  Pending handshakes are cancelled during restart/shutdown; failed sockets are explicitly
  aborted. Context/config changes discard the previous Bridge authentication session.
- Incoming text requests the next socket delivery only after the current payload is applied,
  bounding queued chat delivery without a second unbounded client queue. Fragment size limits
  remain in place; oversized payloads reconnect rather than leaving a stuck connected status.
  Connection state diagnostics contain only state and generation, never endpoint/auth/payloads.
- Added 14 shared field-decoding checks, eight generation-delivery checks, and four actual
  Bridge/ChatUtils model round-trip/partial-load checks. All six standalone profile/edition
  checks passed with 1,806 shared assertions; integrated checks passed with 23 additional
  chat/media assertions (1,829 total). These are headless checks, not live socket/game tests.
- Runtime Bridge connect/reconnect/config edit/quit and chat history recovery behavior still
  require integrated testing. No installed data, deployment, publication or production-service
  changes were made.

### Derived detection snapshot ownership

- DetectedCritters builds both nearby maps before publishing an immutable Counts snapshot.
  Scan/configuration keys only advance after successful computation, so a failed tick cannot
  mark old counts as current or partially extend historical observations. Config revision is
  included to reevaluate Safe Mode visibility when its settings change on the same scan.
- CritterSpotter consumes the derived publication revision, not an independently sampled raw
  entity-scan revision. It marks that publication consumed only after updating the session.
  Existing Sparkling/resolved-body filters, visibility tests and singleton-species caps remain.
- Six snapshot regressions cover copied maps, independent visible counts, configuration-only
  publication, held snapshots, failed construction and reset. Shared suite: 1,812; integrated
  suite: 1,835 including component chat/media checks; all six standalone profile/edition checks
  passed after this change. This does not establish visual behavior
  or full observation-to-render replay. No deployments or installed-data changes.

### Shared command timing, lifecycle isolation and optional-service boundaries

- CommandCoordinator uses a shared monotonic cooldown ledger instead of wall time. Cooldown
  durations are unchanged, including party/invite/join/cleanup timing. ChatQueue now uses its
  CHAT category for queued commands and plain chat instead of a separate 1,200ms clock that
  could bypass other automatic chat senders. Delayed queue entries use monotonic elapsed time.
- Outgoing chat is capped at 128 entries. Overflow rejects a normal newest entry; an urgent
  ticket notice replaces the last entry. Queued entries retain connection/visit/run ownership
  and confirmed-party generation. Old-party/unverified confirmed-solo messages expire rather
  than blocking the queue or going to a new group. Independently verified notices still await initial
  roster confirmation. Party error suppression starts only after an actual send and clears
  at the connection boundary. Capacity/expiry diagnostics contain counts, not chat contents.
- Disconnect cleanup is isolated per subsystem while retaining its order. Bridge and ChatUtils
  shutdown saves and worker cleanup use the same failure boundary; public-network workers
  close even when HTTP shutdown throws. This is best-effort cleanup, not transactional rollback.
- DiagnosticQueue owns capacity, size and drop accounting under one short lock. Producers
  cannot reserve a slot then race queue clearing into negative counters. Shutdown closes
  admission before the final drain; each normal flush handles at most one queue capacity so
  a continuous producer cannot keep it running indefinitely. Unexpired repeat summaries are
  drained at shutdown. Writer availability/stopping publication is visible to background threads.
  Repeated errors are also coalesced in Normal/Extra builds, before expensive stack formatting,
  rather than flooding Minecraft's log if an optional provider fails repeatedly during rendering.
- Shared API and party-sync facades use ServiceAccess for service loading and calls, covering
  invalid service descriptors, linkage failures, synchronous request exceptions and null
  results. The lookup screen no longer bypasses the facade. Asynchronous failures remain
  exceptional futures, and failed shared-catch application does not claim roster completion.
  Capability gating, API quotas/caching and backend production settings were not changed.
- Added 27 timing/queue-owner checks, eight diagnostic queue/repeat checks (including a
  four-producer 40,000-record burst), and 12 provider-boundary checks. Integrated check passes:
  1,859 shared + 23 component assertions = 1,882. All six standalone profile/edition checks
  also passed on the final source state. Runtime party transitions, chat delivery and
  network behavior still need in-game validation. No deployments, installed-data edits, pushes,
  releases, website changes or production-service changes were made.

### Continuation: lookup presentation ownership and release metadata

- Lookup completions retain useful history but cannot clear newer input, steal focus,
  change a newer selection, or update a closed/replaced screen. Tab changes, edits,
  recent-player selection and screen removal invalidate presentation ownership only;
  shared API work is not cancelled. Same-player data may still refresh in place.
- Replaced the separate recent-result timestamp map with bounded immutable LookupHistory.
  Older/equal source snapshots cannot overwrite newer results or rejuvenate freshness;
  expiry uses the response fetchedAt value. Rendering reuses the published list without
  allocating snapshots each frame. Added ten deterministic history/freshness checks.
- Ticket player verification is fenced by editor generation and config identity. Cancelling
  and reopening an editor cannot apply an older save or clear a newer request's busy state.
  Canonical-name application rejects results for replaced settings or renamed entries.
  Lookup and ticket name widgets retain text/caret on layout rebuilds instead of resetting.
- Update checks use the shared streaming byte limiter (512 KiB), validate metadata before
  publishing a single immutable status/details snapshot, and keep one-request-per-launch
  behavior. Malformed metadata becomes unavailable, not partial details or false current
  status. Added 14 parser/version regressions and concise outcome diagnostics.
- Integrated validation passes: 1,883 shared plus 23 component checks (1,906 assertions).
  All six standalone profile/edition checks also pass on the final source state;
  git diff --check is clean. The existing DarknessFilter deprecation warning remains.
  UI lifecycle behavior still needs in-game testing; these headless fixtures cover the
  extracted history and release policies, not Minecraft screen interaction.
- Network scan identified BazaarPrices' InputStream response as a remaining transport
  audit item: preserve selective parsing while adding bounded body/deadline handling.
  No Bazaar behavior, production service, installed settings, deployment, push or release
  changed in this continuation.

### Continuation: bounded streaming and Bazaar lifecycle

- Bazaar uses the shared HTTP runtime's bounded InputStream subscriber (16 MiB maximum)
  and a 20-second total header/body budget. Selective JSON parsing still avoids materializing
  unrelated order books. A shared daemon watchdog closes stalled bodies; normal close removes
  its scheduled task, and shutdown races close responses that cannot register a deadline.
- Price parsing remains on bounded workers. Success/failure publication is serialized on the
  client thread, so history valuations cannot mix markets midway through one calculation.
  Shutdown rejects late publication. Refresh/retry keeps the existing five-minute cadence
  using MonotonicCooldowns, and cache revision is independent of wall-clock timestamps.
- Unsuccessful, truncated, trailing-data and non-finite price responses cannot replace cached
  data. Missing products retain last-known prices and empty order books remain zero. User
  failures stay generic; developer outcomes include failure type/cache count without payloads.
- BoundedRequests immediately removes cancelled queued work, freeing queue capacity without
  interrupting running shared work. Shutdown still settles queued/running caller futures.
- Added nine parser checks, six stream/deadline/shutdown checks, two cancellation checks,
  and three loopback HTTP smoke checks. The latter verifies an actual stalled body is stopped
  after headers and the shared client remains usable, without contacting external services.
- Integrated check passes with 1,903 shared + 23 component assertions (1,926 total).
  All six standalone profile/edition checks pass on the final source state, including the
  loopback transport checks, and git diff --check is clean.
  Existing DarknessFilter deprecation warning remains. The broad acceptance gates below
  remain open: this does not validate live Hypixel responses or full gameplay behavior.
- Bazaar, update and lookup diagnostic categories remain available outside Safari in automatic
  developer logging. No installed data, deployment, production Worker, push or release changed.

### Continuation: immutable backups and logical chat retention

- Migration/release backups now use AtomicFiles.copyOnce: copy into an owned same-directory
  staging file, then publish without replacement. Failed copying never creates the final backup
  name; existing backup files remain untouched. This removes direct writes to the filename
  that subsequent launches trust. Rename semantics remain filesystem-dependent; this is not
  a power-loss durability guarantee or validation of backups created by older versions.
- Added four backup checks for competing concurrent sources, immutable first publication,
  missing sources and destination conflicts; staging cleanup checks cover backup files too.
- SerkoAddons retained chat history uses bounded MessageHistory keyed by stable provenance ID.
  Removed the separate strong GUI-object index and stored GUI-object references. Compaction
  after view resets can remove the original retained record, and replayed objects cannot be
  recorded as new-session messages. The 1,000-message cap, styled content, previous-session
  filtering and protected-source recovery behavior remain unchanged.
- Added seven logical history checks, including representation rebinding, duplicate admission,
  oldest-entry eviction and unknown removals. Integrated check passes: 1,907 shared + 19 chat
  + 11 media assertions = 1,937. No user files were read or changed by these fixtures; they
  use temporary files or headless message identities.
- All six standalone profile/edition checks pass; integrated checks also pass on the final
  source state. Historical message reassociation is explicitly excluded from live command
  response handling and DM recipient/rank updates, matching the notification/history policy.
- Full Minecraft chat-view/compaction testing and recovery reconciliation remain open. History
  saves at shutdown and channel-availability/disconnect boundaries; periodic asynchronous
  checkpoints are not implemented in this stage.
  No installed-data changes, deployment, production-service updates, pushes or releases.

### Continuation: capture geometry and authenticated transport boundaries

- Capture scan updates, tick following and interpolated frames now share the same species
  anchor calculation. Driftling's custom vertical offset is retained rather than alternating
  between native-body and presentation origins. Active captures retain their full-size box
  dimensions; later scans cannot resize the baseline of an already-running animation.
- Active-body following uses the shared UUID index instead of another world-entity traversal.
  Attempt selection, guarantee rules, animation clock and outcome ownership are unchanged.
- Added 315 geometry assertions over moving/shrinking bodies for ordinary markers, both
  Hideons, Duplico and Driftling. These exercise production geometry, not live entity pairing;
  full observation replay and in-game rendering acceptance remain open.
- Authenticated HTTP and WebSocket construction now use a centrally owned no-redirect
  transport. Public HTTP keeps its existing redirect policy. Both clients are shut down by
  NetworkRuntime. A loopback 307 regression confirms proof/token requests do not reach the
  redirect destination; no production credentials or services are used by that test.
- Artifact verification checks exact API/party service registrations and implementation
  classes in addition to edition boundaries, so stale registrations fail the build.
- Final validation: all six standalone check combinations pass with 2,223 shared assertions;
  integrated SerkoAddons passes those plus 19 chat and 11 media assertions (2,253 total).
  Both working trees pass git diff --check. Existing DarknessFilter deprecation remains.
- No installed jars, user data, production Worker settings, website, pushes or releases changed.

### Continuation: replay robustness, nested settings and typed API collections

- Diagnostic replay recognizes retired capture attempts, resets evidence at connection/visit
  boundaries, retains the previous phase when optional records omit it, and continues after
  malformed fields. Six gzip-file fixtures cover rotation, cleanup, invalid fields and orphans.
- Developer replay runs through bounded background work with one outstanding request; UI
  feedback is client-thread and connection-guarded. Logging seals a gzip segment before replay
  and continues in a new one, preventing a normal open gzip trailer from looking corrupt.
  Size-based and replay-requested rotation share the same publication path.
- Nested Bridge/ChatUtils list settings now reject unknown enum values, unsupported fields,
  null rows and wrong scalar types instead of silently sanitizing away saved data. Existing
  protected-source recovery applies; unrelated fields retain their saved values. Five fixtures
  cover nested rejection and valid/missing defaults. No installed user settings were touched.
- Worker collection data now decodes once into immutable SafariPlayerData. Removed synthetic
  Hypixel profile JSON and the corresponding selected-profile/recursive duplicate heuristics
  from the provider. Manual lookups and party loading share this typed result. Rank storage,
  whole-batch commit, roster generation guards, cache cadence and request timing are unchanged.
  Missing counts still default to zero; fractional/overflow/malformed payloads fail without
  replacing cached data. Fifteen fixtures cover defaults, ordering, immutability and errors.
- This extends diagnostic policy/file replay, not full world-observation simulation. Production
  service and in-game behavior remain unverified; no deployment, push or release was performed.
- Final checks pass for all six standalone variants (2,249 shared assertions) and integrated
  SerkoAddons (2,279 including chat/media). The live-source Worker workspace passes 22 tests,
  including new cache coalescing, stale-deadline and no-redirect fixtures. Key-bearing upstream
  fetches now explicitly refuse redirect following in local Worker source; not yet deployed.
- The older ignored Worker copy inside SerkoAddons' component snapshot was inspected but not
  modified or used for validation. Backend work remains owned by the standalone cloudflare-api
  workspace; explicit component synchronization is still separate from builds.

### Bridge link classification follow-up

- Bridge labels and preview admission now share URI-path/host classification. Ordinary
  websites use their domain as a clickable label with no preview/download admission;
  YouTube/Twitch and IMAGE/GIF/VIDEO labels no longer receive numbered suffixes.
- Query strings cannot masquerade as media extensions, provider host checks respect domain
  boundaries, and signed Discord URLs retain their query parameters. Supported share-page
  routes remain previewable. Removed duplicate HTTP URI parsers and gated insertion previews
  through the same classification rather than trusting old insertion tags.
- Sixteen pure link fixtures extend the media suite. Settings reorganization and visual
  polish remain deferred until after the overhaul. This is local work, not deployment.

### Capture timing and history checkpoint continuation

- Capture artifact windows own an injected monotonic clock instead of receiving wall-clock
  timestamps from callers. Existing pending/resolved durations and inclusive boundaries are
  preserved; subtraction-based expiry also handles nanoTime wrap. Four added fixtures cover
  wrap and exact grace boundaries. Removed the unused wall-clock presentation snapshot input.
- Chat history now checkpoints dirty state at most once per minute with one outstanding
  copied snapshot on shared bounded infrastructure. Encoding and file writes run off-thread.
  A reusable ordered writer prevents a delayed checkpoint overwriting a newer lifecycle save;
  failures remain retryable and protected sources still save separately to recovery.
- Seven ordered-write fixtures cover duplicates, failure/retry, stale commits and delayed
  worker completion after a newer shutdown-style save. Integrated check passes with 2,260
  shared assertions plus 19 chat and 27 media assertions. In-game crash/reload checks and
  full capture observation replay remain open; no installed files or production were changed.

### Developer audit ownership and API wire validation

- Removed the developer randomized audit's common-pool submission. Work uses bounded shared
  infrastructure, generation-scoped application, queued cancellation and cooperative active
  cancellation. Resetting/restarting cannot let an old result replace the current audit.
- Audit limitation found: the randomized host model still describes active/backups rather
  than the current ordered invite queue. Its PASS is a timing-model result, not proof of the
  current full ticket-trading state machine. Updating that harness remains explicit work.
- Backend schema/rank fields now reject fractional, overflowing and incorrectly typed wire
  values instead of silently truncating/coercing them. Signed Java and unsigned JavaScript
  ARGB both remain supported; optional legacy permanent-rank defaults are preserved.
  Collection timestamps use the same exact parser. Malformed batches still publish nothing.
- Added 21 rank/wire regressions. Latest integrated check: 2,281 shared + 19 chat + 27 media
  checks. No network requests, installed-data migration, deployment or release in these tests.

### Ordered trading simulation and guest permission continuation

- Replaced the randomized host audit's active/backup model with an ordered queue of up to
  24 players, 1–4 baseline party members, offline/no-response waves and current invite/warp
  windows. Live trading, interactive simulation and randomized simulation use the same pure
  queue selector; independent assertions check order, capacity, qualification and timing.
- Added 2,000 seeded queue-selection fixtures (including duplicates/baseline members) and
  448 guest policy/boundary checks. Developer builds now run the seeded randomized audit in
  `check`; Normal/Extra do not compile or execute the developer-only simulator.
- Guest permissions are rechecked before deferred accepts and fallback leaves. Turning off
  the feature/player, removing trust or entering kick lockout prevents the delayed command.
  Failed command admission no longer consumes the successful-invite deduplication window.
  Existing host connection windows and guest leave-settle duration remain unchanged.
- Removed the obsolete backup deadline and renamed the retained 3-second retry delay for
  queue waves. The audit reports actual scenario counts, including an early failure exit.
- Pending lobby-context notices now use a bounded, deduplicating, monotonic inbox. Duplicates
  do not extend their lifetime; reset during delivery cannot resurrect messages or invalidate
  live iteration. Nine fixtures include expiry, reset callbacks and a 10,000-item burst.
  Protocol parsing is explicitly gated to Hypixel. No claim of authentication is made for
  the legacy notice fingerprint; the separate authenticated room transport remains distinct.
- These tests exercise selection/permission policies and simulated command timelines, not
  the complete live network/chat adapter. Full party/server integration still needs game tests.
- Final verification passes for all six standalone variants and integrated SerkoAddons:
  4,738 shared assertions, 19 chat and 27 media assertions; Developer additionally passes
  5,000 host/2,000 guest scenarios with 67,007 checks at seed 20261014. The existing darkness
  deprecation warning remains. No deployment, production service changes, push or release.

## Acceptance gates

### Render diagnostic ownership continuation

- Render diagnostic change detection now compares immutable full-ID sets and connection/visit/run
  identity instead of repeatedly sorting/stringifying shortened UUIDs. Text is formatted only
  after ownership changes. This avoids shortened-ID collisions hiding transitions and removes
  redundant formatting on unchanged render snapshots without changing live marker ownership.
- Added five fixtures for immutable inputs, same-prefix UUIDs, visit changes, unchanged state
  and new attempts on the same body. Integrated shared regression count is now 5,363.
- Beam scrolling shares the existing monotonic frame sample with capture animation; its
  two-second period is unchanged. No catch, pity, Safe Mode, settings or saved-data changes.
- Full observation-to-render replay and in-game testing remain open; these checks do not
  establish visual correctness for every live entity sequence. No deployment or release.
- Final validation passes in all six standalone variants and SerkoAddons: 5,363 shared checks,
  19 chat, 27 media, and Developer's 67,007-check ticket simulation. Whitespace checks pass;
  existing DarknessFilter deprecation warning remains unchanged.

### Shared service diagnostics and party stabilization continuation

- ServiceAccess now observes asynchronous request failures as well as synchronous faults.
  Original failures remain available to callers, normal cancellation is quiet, and consumer
  cancellation still cannot cancel shared upstream work. Five additional regression checks
  cover delayed faults, original cause preservation, null values and cancellation behavior.
- Party attendance grace/stability/refresh delays, location-learning stability and API roster
  stabilization now use the shared monotonic clock. Published party/attendance timestamps
  remain wall time to preserve compatibility with visit-entry comparisons. Existing thresholds
  and ticket-trading timings are unchanged; no persistent data or settings were migrated.
- No deployment, push, release or production service change in this continuation.
- Validation: all six standalone variants and integrated SerkoAddons pass, with 5,358 shared,
  19 chat and 27 media assertions; Developer ticket audit passes 67,007 checks. Whitespace
  checks pass. Existing DarknessFilter deprecation remains; live party validation is pending.

### Party API application and first-load evidence continuation

- Fixed loss of catches observed during a player's initial in-flight collection load. Partial
  observations are retained independently of complete snapshots, then merged only when newer
  than the request checkpoint. They never make a missing collection appear loaded. The evidence
  map is independently bounded to the existing player-cache capacity, including unknown players.
- Extended the existing owner-thread dispatch primitive with guarded value-returning application;
  party refresh no longer owns an ad-hoc future that could hang after an application exception.
  Success and failure use one visit/connection/composition/shutdown ownership predicate.
- Added first-load, absent-member, later-snapshot and 1,000-player evidence burst fixtures, plus
  value-dispatch success/stale/cancel/rejection/exception fixtures. Integrated check passes with
  5,353 shared assertions, 19 chat, 27 media and the 67,007-check ticket audit.
- No network cadence, refresh intervals, ticket timing or saved data formats changed. No
  deployment, production service update, push or release performed. Live party tests remain pending.
- All six standalone edition/profile checks also pass with 5,353 shared assertions and
  Developer's ticket audit. Whitespace checks pass; existing DarknessFilter deprecation remains.

### Capture clock and artifact verification continuation

- Capture attribution, interaction evidence, outcome queues, logical expiry, recatch pin holds,
  and remembered-static marker lifetimes now use a shared process-relative monotonic millisecond
  clock. All duration thresholds remain unchanged; animation timelines remain nanosecond based.
  This removes wall-clock adjustments from these transient lifetimes without migrating or
  changing any persistent timestamps, settings, ticket windows or run history.
- The clock reserves zero for unset state and handles nanoTime rollover. Added 24 regressions
  covering negative origins, rollover, sub-millisecond rounding and relevant duration boundaries.
  Outcome traces tag their clock domain; historical policy traces remain readable.
- Artifact validation now compares embedded provenance with the complete generated provenance
  for the current source/profile/edition instead of accepting file presence alone. This is
  shared by standalone and integrated verification, and does not replace class parity tests.
- No deployment, release, push or production service changes in this continuation.
- Verified all six standalone variants and integrated SerkoAddons: 5,341 shared assertions,
  19 chat, 27 media, and Developer's 67,007-check ticket audit. Diff whitespace checks pass;
  the existing DarknessFilter deprecation warning remains. In-game validation is still pending.

Latest capture verification increment:
- Terminal capture resolution now captures one immutable pre-mutation input and logs a typed,
  versioned `ct` trace instead of its previous text-only decision summary. Replay evaluates
  the same production policy without world access; runtime ownership/timing behavior is unchanged.
- Exhaustive coverage of 192 terminal input combinations adds policy, round-trip and altered-result
  checks. Three compressed-stream fixtures cover valid, mismatched and malformed terminal traces.
- Integrated verification passes: 5,317 shared, 19 chat and 27 media assertions, plus the
  67,007-check ticket simulation. Full world-observation/projection replay remains an open gate;
  policy replay must not be presented as proof that every in-game marker renders correctly.
- No deployment, push, release or production networking changes in this increment.
- All six standalone edition/profile checks also pass with the same 5,317 shared assertions;
  Developer variants run the ticket audit. The pre-existing DarknessFilter deprecation warning remains.


- [x] Normal, Extra, Developer only; public API independent of Safe Mode
- [ ] Audited shared API sources and service registrations; no embedded credentials/private download links
- [ ] Production Worker secret/quota verified and production service validated
- [ ] Generation-scoped async results and bounded network work
- [ ] Failed-load protection for all persistent component stores
- [ ] Deterministic capture observation replay and regression fixtures
- [ ] Single logical-critter presentation ownership and monotonic animation timing
- [ ] Ordered lifecycle/event application and effect execution
- [ ] Shared identity/cache semantics, party loading and alert lifecycle
- [ ] Stable chat message/session IDs and structured Bridge classification
- [ ] Media resolution/decoding/cache lifecycle separation
- [ ] Reusable settings bindings and focus behavior
- [x] Build provenance, standalone/integrated parity, explicit source synchronization
- [ ] Full profile/variant validation and integrated run testing

Preserve existing work, config keys, migrations, Safe Mode evidence policy, ticket timing,
and independently versioned SerkoAddons. Never equate compilation with runtime validation.
