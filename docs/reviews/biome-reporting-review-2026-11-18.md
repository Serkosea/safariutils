# Biome reporting review — 2026-11-18

Reviewed the latest downloaded 4.3.6 reference artifact for Minecraft 26.2 using static decompilation and bytecode inspection. No mod jar was executed.

## Corrections

- Unique biome reports now require sparkling coverage for every party-needed species, not only the special objective species. Bird feeding still must resolve even when birds are already shared.
- Unique and Sparkling reporting wait for a resolved party collection; an incomplete lookup cannot imply there are no missing species.
- Sparkling mode does not announce an empty target set.
- Icy participates in backend completion reporting in all modes. Only Unique-mode Icy chat (`id`) is suppressed. The previous Wumpa-based suppression applied to backend reporting too and was obsolete.
- Remote species-completion flags still assist relevance/HUD decisions but cannot alone generate a fresh outgoing biome report.
- Pending party-chat reports recheck completion and mode; already sent reports remain latched for the run. Switching to a stricter mode does not retract an earlier report.

## Mode policy

| Mode | Outgoing completion requirements |
| --- | --- |
| Full Clear | Required captures, source exhaustion and survey evidence; extra ordinary Macaws do not require catching after one capture. Outstanding sparklings still block reports. |
| Unique | Unique catches or proven non-spawn exceptions; bird feeding resolved; sparkling checks for every party-needed species. |
| Sparkling | Nonempty party-missing target set, with each required species checked; Wumpa/Doomspiral require catches. |

The existing C snapshot flags and payload shape were deliberately retained. They represent species evidence, not a promise that an entire biome satisfies the receiver's current mode. No backend or wire-format changes were introduced.

## Departure completion and settings follow-up

Added the two retained-evidence departure cases:

- Forest: while still present, qualify all non-bird targets and account for floor feed with no held feed. Retain the mode and needed-species set while final feeder spawns resolve. After departure, require completed feeding and the relevant birds' mode-specific completion. No short timeout.
- Haunted: qualify all non-Hideyho targets while present and require a Hideyho capture or NPC/MOB dialogue within 30 seconds. Retain this evidence for 30 seconds, then require an actual Hideyho catch to report from outside the biome.
- Both reject changed mode/needed-species sets, outstanding sparklings and unresolved held objectives. Reentry requalifies the evidence; new sessions/visits clear it. The center of Safari no longer blocks these pending reports.

Kept both user settings. Static inspection confirmed separate automatic biome-report and hotspot announcer/display controls in the reference. Biome Reports now controls public chat; eligible backend completion continues independently, as in the reference. Assign Hotspots continues to control assignment display and leader instructions, with the existing backend authority, no-reshuffle rules, notification deduplication and fallback lock preserved. No backend allocation changes were needed.

The departure cases use our existing source/survey trackers, not copied detector implementations. Thus incomplete local observations can still delay a report; the policy is aligned without claiming identical per-frame detection. C evidence flags and wire payloads remain unchanged. New B reports remain once per biome per run (including when chat is disabled); there is no new polling request or endpoint.

Preserved the 1.5-second settle check, existing chat election, outstanding-sparkling blockers, and once-per-run report ledger. No stored catch, PB or collection data is changed by reporting.

## Validation

- Minecraft 26.2 `check`: passed, including mode/chat-policy regressions and artifact boundary validation.
- Minecraft 26.3 `compileJava`: passed.
- Minecraft 26.1.2 `compileJava`: passed.
- Departure regression cases cover the 30-second boundary, Forest retention, changed modes/needs, reentry and reset.
- In-game mixed-party testing not performed. Nothing deployed, pushed or released.
