# v3 validation status

This is the current validation map, not a release approval. `IMPLEMENTATION_V3.md` is a
historical implementation journal; its older counts and pending statements describe that
increment, not necessarily the present code.

## Automated coverage in place

- Latest grouped-capture correction: all six standalone variants and integrated checks pass
  (121,121 shared checks; 67,007 ticket simulation checks; 19 chat and 1,352 media checks).
  Integrated parity confirms 484 byte-identical shared classes. Updated-logic exploration of
  the October 14 22H-02M journal resolves all five Gimmiegolds as caught; this is counterfactual
  logical-state evidence, not a claim that the new marker rendering has been tested in game.

- Six standalone combinations: Normal, Extra and Developer on 26.1.2 and 26.2.
  Checks include regression fixtures, access-widener validation, generated provenance,
  service registration and edition/artifact boundaries.
- Integrated SerkoAddons checks cover shared SafariUtils, chat history, Bridge/media and
  the Developer ticket simulation. Parity compares every shared class byte-for-byte and
  rejects unexpected component classes, excluding the five named integrated UI adapters.
- Capture input replay executes the production tracker. Presentation fixtures exercise
  complete label/body selection, live effect application, the production frame projection loop and marker geometry with
  explicit moving-body inputs. Neither is a complete recorded entity-scan
  through GPU-render replay.
- Scanner candidate indexing and ranked assignment orchestration are checked against full-list
  references, including radius completeness, source-order ties, competing claims and invalid
  duplicate-claim isolation. Special-species type acceptance is tested consistently against
  capture verification, including Gazer's armor-stand-only rule. Complete scan fixtures cover
  retained and fresh dense groups, special pools, range boundaries and source-order ties.
  The live entity naming/type extraction and GPU rendering still require runtime validation.
- Persistence fixtures cover protected-source recovery, malformed/partial input, ordered
  writes and import backups. They do not prove every real user's historical data migration.
  The explicit offline audit also passed nine actual current/backup files (three settings,
  three histories and three collections), with unchanged source bytes and no rejected records.
- Bridge tests cover bounded work, destination policy, signed request preservation, streaming
  limits, playback clocks and resource ownership/cleanup. Explicit live TLS smoke checks
  have succeeded and rejected expired/hostname-mismatched certificates.

## Remaining validation gates

October 14 release checkpoint: the user verified settings/data retention, fast cached names,
player lookups, solo run transitions, and the latest grouped/distant captures in game. The
22H-18M log strictly reproduced 4,855 capture inputs with only the expected pre-visit waiting
notice excluded. No in-run input gaps were found. Public release is authorized; the list below
records coverage limits for continued testing, not a claim that every scenario was exercised.
Developer two-client sync and wider Bridge preview/runtime tests remain unverified.

The reported Cavern FPS dips were not measured in that journal. Final cleanup removes duplicate
entity/species indexing without changing sampling or capture rules; rate-limited Developer PERF
records now identify individual tick/waypoint operations taking at least 5 ms, at most once per
operation per 10 seconds. These are CPU wall-time diagnostics, not GPU/frame-time measurements.

1. Capture integration: static review confirms the world adapter feeds the shared scanner,
   tracker and body-ID frame lookup; actual entity extraction still needs runtime evidence.
   Use new-run journals to verify close same-species groups, immediate
   recatches, distant catches, guaranteed-catch animation and visit cleanup. GPU appearance
   still requires in-game observation; replay cannot establish that by itself.
2. API and party integration: test the current client against the production-key-backed
   service, including authentication, cached ranks/collections, rejected/offline requests,
   party changes and repeated ticket-trading members. Preserve the existing ticket windows.
3. Developer remote sync: test two updated clients, room isolation, reconnect/leave and
   objective/wall updates. Solo connectivity does not establish peer synchronization.
4. Bridge runtime: test current signed Discord attachments and GIF/video decoding, eviction,
   resource reload and shutdown in game. Public HTTPS smoke tests are not decoder/GPU tests.
5. Settings and saved data: offline current/backup decoder checks passed; test current
   restart behavior, focus changes and shared UI controls without overwriting originals.
6. Release preparation: explicitly synchronize component snapshots when ready, reverify the
   final sources and artifacts, then perform the separately authorized website/push/release.

## Verification without publishing or deployment

Standalone: `VerifyBuilds -PcleanArtifacts=true` verifies all six variants without collecting
release artifacts. From SerkoAddons: `clean VerifyAllBuilds -PcleanArtifacts=true` also cleans
and verifies the integrated build and checks parity. These commands do not install game jars,
copy player configuration, update the website or change Worker secrets.

Settings organization and visual redesign remain a later task, not an implicit part of these
validation gates. No percentage estimate should treat outstanding game/network tests as passed.
