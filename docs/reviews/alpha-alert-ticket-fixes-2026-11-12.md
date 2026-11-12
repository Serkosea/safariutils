# Alpha alert and entry follow-up

Reviewed the November 12 15:28 automatic log and its second part, plus saved visit
14 (22:45 UTC). These changes are local follow-up fixes to 3.8.2, not a new public
release.

## Evidence and changes

- Driftling's local discovery preceded its displayed banner by 1,894 ms. Local
  presentation shared the outgoing-chat snapshot gate. Presentation now happens
  before that gate; per-individual presentation ownership transfers across body
  replacements. Shared echoes retain their existing deduplication.
- Saved visit 14 received the shared discovery and use-ticket reminder normally.
  Manager lookup reported three nearby entities, one label, and no eligible player
  or interaction body, then abandoned entry at its short lookup deadline. The tab
  list temporarily contained version-2 NPC profiles during this interval. The
  resolver now permits those temporary NPC profiles, still excludes local/party
  players and real listed accounts, and still requires an unambiguous body paired
  with the Manager label. Label-only data is never clicked.
- A delayed body can now become eligible later in the existing ticket window.
  This does not extend the three-second movement failsafe or change reach.
- Confirmed automatic Sparkling entry sends `[SU] Ticket Used, In Run Now` to
  party chat, or a styled local success message when solo. Opening/clicking a menu
  alone does not send it. Debug tests, failed clicks, canceled attempts and old
  world/visit state cannot generate a success notice.

No sync failures or warning/error records were found in the reviewed automatic
session. Profiling contained occasional long whole-frame/tick intervals, not a
repeated slow Sparkling/automatic-entry stage; these logs alone do not establish
the cause of every frame stall.

## Live confirmation

Use automatic logging for a local Sparkling find and a late party warp into a
known Sparkling. Check immediate local presentation, successful automatic entry
and exactly one party confirmation after ticket acceptance. NPC skin-list timing
and actual server interaction still require that live retest.

## Connection notices

Party-sync transport failures now produce one prefixed local warning per visit and one recovery message after the Sparkling snapshot and run replay are ready. Repeated retries/connection flapping remain in diagnostics without flooding chat. Expected departures are silent. Callback generation checks and visit scoping prevent old-room errors reaching a later run.

Warnings distinguish rate limits, rejected sessions, update requirements, temporary server errors, timeouts, and socket interruption. Only fixed descriptions and HTTP status numbers reach chat; raw server bodies, close reasons, credentials, and network details do not. Existing lookup-command errors remain request-specific to avoid duplicate notifications. No extra network requests or storage writes are introduced.

Regression coverage checks visit resets, reconnect suppression, recovery, known close reasons, and private exception/close text.

## Live follow-up

The November 12 16:16 automatic log contains 3,663 events and no warning/error-level entries. Automatic entry interacted after 299 ms of approach, confirmed ticket activation, then sent the party confirmation once. Four socket handshakes across three visits (including a roster update) completed run reconciliation/replay, with no transport failures. This confirms the successful path; outage/recovery notice behavior is regression-tested rather than deliberately interrupting the live service.
