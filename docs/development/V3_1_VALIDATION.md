# v3.1.0 release preparation

Release validation checkpoint: 2026-10-18. Publication was authorized after the final
solo-run checks. Release version: 3.1.0.

## Review scope

- Capture form replacement, instant retries, pity ownership, and marker retirement use
  CritterState and CaptureEffects. Form substitution without observed absence is not a breakout.
- Hideyho alert eligibility shares marker policy; shrinking colors share the live marker resolver.
- Party discoveries use bounded, visit-scoped snapshots. Received facts are not echoed;
  unsynced objective inventories remain unknown rather than inferred from connectivity.
- Normal/Extra include the API and authenticated network party-sync providers. Developer-only
  controls, simulator and automatic diagnostic recording remain gated separately.
- Settings keep their persisted field ownership. UI transitions are bounded and screen-local;
  special themes have independent palettes and no additional background workers.
- Diagnostic recording remains opt-in and bounded. Saved-run extraction streams sealed logs
  on a separate worker rather than duplicating live capture work.

## Verification

The clean BuildAllArtifacts matrix passed for Normal, Extra and Developer on 26.1.2 and 26.2.
Artifact checks validate service registrations, edition metadata, source provenance and exclusion
of test harnesses. BuildSerkoAddons and VerifySafariParity verify the live-source integration.
The backend's local Node tests cover cache/request limits and party-relay validation/isolation.

At this checkpoint the shared core suite reports 121,604 checks, trading simulation 67,007,
ChatUtils 19, Bridge/media 1,360, ServerUtils 20,038 plus the Fabric mixin smoke test,
OtherUtils 10,026, and the local backend suite 28 tests. Integrated parity verified 489
byte-identical shared classes. These are automated checks, not a GPU/FPS benchmark.

The October 18 13H-42M and 14H-30M logs replayed 26,742 and 7,370 capture inputs
respectively without mismatches. The final log covers two completed runs with Hideon
breakouts/retries and no warning/error-level entries. The strict general diagnostic audit
flags the pre-visit "waiting for visit boundary" notice; no in-run inputs were skipped.
The earlier log includes a Sparkling Mantis Shrimp discovery and catch with outbound
network snapshots. A solo connection does not establish delivery to another client.

## Remaining release actions and limits

- Final artifacts and release notes are approved for publication.
- Commit/push SafariUtils, update the SerkoAddons component revision, and push private integration.
- Publish only the four Normal/Extra jars; keep developer artifacts local.
- Update and deploy the website's four direct GitHub download links after assets are available.
  A local website draft was prepared earlier; it has not been deployed.
- Two updated clients still need live party-sync validation. Solo/local relay tests cannot prove
  peer behavior end-to-end. Minecraft 26.1.2 has less runtime coverage than 26.2.
- Rendering and frame-time claims require in-game observation. Existing DarknessFilter API
  deprecation warnings are non-fatal; changing its visual-only behavior is not part of this pass.
