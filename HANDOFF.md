# SafariUtils handoff

Start with [the documentation index](docs/README.md) and [workspace/build workflow](docs/development/WORKSPACE.md).

## Maintenance rules

- Shared runtime lives in `src/main`; version adapters are in `src/26.1.2`, `src/26.2`, `src/26.3` and `src/compat-pre26.3`.
- Keep private components, backend credentials and website deployment outside this repository.
- Preserve Normal/Extra/Developer boundaries. Regression sources never ship.
- Use canonical capture identity across texture evidence, labels, bodies and party observations. Caught evidence must not be revived by stale found-only snapshots.
- Keep discovery facts separate from local presentation and origin-owned party chat. A reminder and a normal alert must not duplicate the same discovery.
- Use shared API coordination, market snapshots, bounded work queues and opt-in diagnostics. No networking or file I/O in render paths.
- Preserve user histories/configurations; migrations retain unknown fields and protect unreadable or newer data.

## Verification

Run focused regressions, then `check`. For edition/version boundaries run `VerifyBuilds`; combined packaging is checked separately in SerkoAddons. Automated checks do not replace live rendering, party-catch or reconnect tests.

Nov 10 teammate-catch logs showed ordinary zero-scale Mantis carriers retiring, but a distant paper/head capsule and its breakout were counted as additional Sparkling discoveries. Capsule filtering now recognizes the tightly colocated paper/head pair without label stands; TextureLineage preserves an unambiguous observed visible-to-hidden-to-replacement identity. Regression coverage includes the recorded coordinates, repeated breakouts, ambiguous peers, unseen hidden models and expiry. A logged live teammate-catch retest is still needed; compilation is not visual verification.

[Historical implementation notes](docs/archive/HANDOFF.md) are an archive, not the current release checklist. Build configuration is authoritative for versions.

## 3.8.0 release verification

The complete Normal/Extra/Developer matrix passed for 26.1.2, 26.2 and 26.3, with integrated parity checks. Backend contract tests and local Cloudflare runtime join/resume/replay tests passed. Shared completion requires a peer implementing the new negotiated handshake; live mixed-client confirmation remains a separate in-game check. See docs/integrations/PARTY_SYNC_3.8_IMPLEMENTATION.md.

## 3.8.1 run persistence

Every server-confirmed paid visit is retained, including zero-catch disconnects. Manager acceptance and capsule allocation share one activation path. World exit freezes elapsed time and saves immediately; a bounded three-second reward tail can amend that same record until a new visit begins. Late rewards do not invent PB timing or reactivate gameplay alerts. Alpha exclusion is latched on the session in every edition and enforced again by history storage, including delayed rewards. Headless regressions cover empty runs, late catches/rewards, expiry, visit reset and excluded histories.

## 3.8.2 transport and Alpha isolation

Transport 3 batches routine state for one tick, keeps Sparkling events immediate,
references unchanged completion baselines and renews the existing socket. The
provider owns ordered wire sequences, including resync replies. See
docs/reviews/party-sync-efficient-transport.md. Backend minimum is now 3 following
the operator's explicit cutover on 11 November 2026; old sync upgrades are rejected.

Honeyhive persistence eligibility is captured per connection so Alpha observations
stay temporary even during disconnect/shutdown. Alpha never loads main-server
hive cooldowns. The integrated host independently scopes remembered typing channels
by account and Alpha/main while retaining existing main-account keys.

## 3.8.3 alerts and entry

Local Sparkling presentation is immediate and separate from coordinated outgoing chat. Automatic entry waits for the Manager within the existing entry window, recognizes temporary version-2 NPC tab-list entries, and announces only confirmed activation. Party-sync errors/recovery are bounded per visit and use sanitized descriptions. See docs/reviews/alpha-alert-ticket-fixes-2026-11-12.md.
