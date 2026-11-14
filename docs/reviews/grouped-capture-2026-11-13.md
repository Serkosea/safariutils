# Grouped capture follow-up

The saved November 13 run shows two queued Mantis breakouts, only one observed returning body, later ATTEMPT-MISSED/DEFERRED-EXPIRE events, and catch results released together after the five-second breakout fallback. The final catch has no matching attempt.

Scanner review found that CAPTURING scaffolding classification used the broad appearance-association column (0.75 blocks), and retained armor-stand labels until unload. Nearby returning labels/skins could therefore be excluded from live-body matching. Capture classification now uses the narrow quantized assembly column; labels release classification when current evidence disappears. Copied item-display models remain retired until unload to prevent old capsule skins resurfacing.

Regression cases cover nearby returning labels/skins while existing compound-capsule tests retain suppression of the actual assembly. Shared capture tests pass, including the prior Gimmiegold ownership correction. This is not an exact replay of the saved run and still requires a live grouped-breakout retest. Outcome ordering is intentionally unchanged: clearing arbitrary same-species markers would risk losing uncaught critters.

Contest HUD minimum gap changes from 18 to 14 whole pixels (rounded from 18 × 0.8); inactive timer label is Starting.

## Live retest confirmed

Saved run SafariRun_2026-11-14T01-08-26.659256700Z_V4_16333414672991897424_events.jsonl.gz (November 13 local time) contains 6,362 events with no warning/error entries. Gimmiegold breakout A28 reassociated to its returning body and all five final catches cleared their recatch ownership. Mantis breakouts A38, A36 and A40 reassociated to returning bodies, including a repeated breakout on the same individual; final catches A41 and A42 cleared both remaining recatch pins. No five-second fallback, unmatched result, expired deferred attempt or unresolved recatch timeout appeared in these sequences. The user also confirmed no stuck markers visually. This validates these tested grouped sequences, not every possible packet ordering.
