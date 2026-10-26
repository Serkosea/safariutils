# Workspace and build workflow

## Source ownership

- `src/main`: shared runtime and key-free API client
- `src/26.1.2`, `src/26.2`: Minecraft compatibility adapters
- `src/developer`: simulator and testing tools, excluded from public editions
- `src/regression`: headless checks, never packaged into the mod
- `gradle/versions`: supported Minecraft dependency profiles
- `gradle/regressions.gradle`, `provenance.gradle`, `distribution.gradle`: verification,
  component fingerprints, and artifact/deployment tasks
- `tools`: source-asset generation utilities
- `docs`: development documentation; `HANDOFF.md` and `IMPLEMENTATION_V3.md` remain the
  root entry points while the v3 migration is in progress

## Local-only files

`gradle-local.properties` selects deployment destinations and the live component root.
`private-api` currently holds ignored credentials, not compiled source. Do not delete it
or copy it into component snapshots/artifacts. The local backend has moved to the sibling
`SerkoAddons/backend/` workspace, with its own dependencies and secret deployment lifecycle.

`build`, `release-artifacts`, `.gradle`, and `.gradle-user` are generated/local caches.
Preserve caches during routine work. Future temporary work belongs under ignored `local/`,
not a new root staging folder. Never put a key in tracked source, build metadata, or logs.

## Website release updates

The authoritative local website is now `../SerkoAddons/website/`, moved out of
`private-api/serkosea-site/` and deployed to the existing Cloudflare Pages project `serkosea`.
The copy under SerkoAddons' `components/SafariUtils/private-api/` is an obsolete
v2.7.0 snapshot, not a deployment source. Never deploy the whole `private-api/`
directory: it contains credentials. The website folder contains public static assets only.
Update `safariutils/index.html` within the site folder after preparing release assets;
publish GitHub downloads before deploying the site, and verify all four public links.

## Ready-to-use artifacts

This workspace collects all seven current jars under SerkoAddons `release-artifacts/`:
`safariutils/` contains the six standalone variants; `serkoaddons/` contains the combined jar.
The ignored standalone `releaseArtifactsDir` setting selects that same destination when
running `BuildArtifacts` directly. Without an override, standalone checkouts continue to use
their own `release-artifacts/` directory. Collection synchronizes only the selected component
directory, so old versions disappear without touching the other component's jars.
`BuildAllArtifacts` refreshes both sets and verifies parity; SerkoAddons `DeployBuilds`
also refreshes its collected jar. None of these collection tasks publish a release.

`build/**/libs` are internal Gradle outputs, not additional release collections. Keep their
profile/edition separation and incremental caches. Regression runtime logs belong under
`build/**/test-runs/<task>/`, not the repository root. Real instance logs, saved runs,
settings recovery backups, source assets, IDE settings and credentials are not cleanup targets.

## Commands (PowerShell)

Quote profile arguments containing dots:

```powershell
.\gradlew.bat check '-PminecraftProfile=26.2' '-PdeveloperBuild=true'
.\gradlew.bat VerifyBuilds '-PcleanArtifacts=true'
.\gradlew.bat BuildArtifacts
.\gradlew.bat BuildArtifacts '-PcleanArtifacts=true'
.\gradlew.bat DeployBuilds
```

`check` runs headless regressions, validates the access widener, builds the jar, and checks
edition boundaries. `VerifyBuilds` verifies the six combinations sequentially without
touching deployment directories or collecting release artifacts. `BuildArtifacts` uses that
same verification task before collecting their jars. Normal builds reuse valid Gradle outputs; `cleanArtifacts=true`
is the explicit clean matrix/deployment option. No task here publishes a GitHub release.

From SerkoAddons, `check`, `BuildSerkoAddons`, and `DeployBuilds` consume the configured live
SafariUtils source; `BuildSafariUtilsArtifacts` builds the standalone matrix.
`SyncSafariComponents` is an explicit snapshot update, not a side effect of compilation.
SerkoAddons stays on Minecraft 26.2 and includes SafariUtils Developer functionality.

`BuildAllArtifacts` in SerkoAddons also runs `VerifySafariParity`, comparing every standalone
SafariUtils class with the integrated copy. Run `VerifySafariParity` directly when both jars
already exist; a stale standalone jar correctly fails instead of being silently accepted.
Routine `check` stays independent of the standalone build matrix.

`VerifyAllBuilds` in SerkoAddons runs `VerifySafariBuilds` (the standalone verification-only
matrix), integrated checks and parity without collecting/deploying jars. Parity requires exact
shared class-set equality as well as identical bytes, allowing only the explicitly named integrated
UI adapters (and their nested classes). Stale extra component classes fail too.
For a fully clean verification use `clean VerifyAllBuilds '-PcleanArtifacts=true'` there;
`clean` clears the integrated output and the property cleans each standalone variant.

SerkoAddons `previewTransportSmoke` explicitly contacts public HTTPS test endpoints and
checks hostname-mismatch/expired-certificate rejection. It is not part of `check`, does not
read player settings or credentials, and does not ship in the mod. It prints host/status/byte
counts only. A CDN root returning 403 verifies transport, not attachment-preview support.

`replayDiagnostics '-PreplayFiles=C:/path/first.jsonl.gz;C:/path/second.jsonl.gz'`
audits explicitly supplied, sealed segments in chronological order without starting Minecraft
or reading/writing player settings. Warnings fail the task so incomplete or contradictory
logs cannot look like a clean check. This validates recorded policy decisions and lifecycle
invariants, not a complete world/render replay. Use files from one session per invocation.

`replayCaptures` accepts the same `-PreplayFiles` argument and applies the new `ci` input
journal to the production capture tracker in an isolated JVM. Every input checks its recorded
logical-state fingerprint. It requires a visit boundary and contiguous inputs; old logs cannot
provide this test. The test harness substitutes inert effects and in-memory settings, and is
explicitly forbidden from packaged jars. This verifies capture-state reproduction, not GPU output,
static catalog projection, or animation interpolation. Those remain separate validation scopes.

Each jar carries `safariutils-provenance.properties`. Matching source fingerprints and
class hashes allow standalone/integrated parity checks without relying on version names.
Artifact verification compares the embedded provenance with this build's generated values,
including its source digest, profile, edition and component version; mere file presence is
not accepted. This checks provenance consistency, not byte-for-byte compiler reproducibility.
Local deployment requires in-game testing afterward; passing a build is not a release approval.

`auditSavedData '-PsavedDataFiles=C:/path/safariutils.json;C:/path/safariutils-runs.json'`
executes production settings/history/collection decoders on in-memory copies of explicitly
named SafariUtils files. Settings are checked for stable migration/serialization, histories
for rejected or lost records, and collections for rejected counters. Source bytes are checked
again afterward; no import/save path is called and no values are printed. This audit is opt-in,
not part of ordinary builds, and does not prove that every historical semantic choice is correct.
Use actual `safariutils.json` or the standard backup filenames for settings inputs.
