# Hideonfloor neighbor briefly disappears

Source: Prism Skyblock 26.2 `minecraft/config/serkoaddons/logs/SerkoAddons_November-18th-2026_18H-29M_P2_events.jsonl.gz`, connection 52, visit 23, run 1. Times below are November 18 America/Denver (UTC minus seven hours).

- 18:59:12.673: StillCritters remembers `2deb6b6f` at (28,79,59) and `0f2e2f62` at (37,80,50).
- 18:59:15.581: A2 selects `0f2e2f62`, `target=inferred`, and creates its provisional recatch pin.
- 18:59:15.675: RENDERSTATE reports `pins=[0f2e2f62]`, `native=[2deb6b6f]`. DRAW suppresses the shrinking `2deb6b6f` two milliseconds later.
- 18:59:16.021: capture label rejected with `noLocalCandidate=1`; the provisional target is still wrong.
- 18:59:17.847: A2 resolves CAUGHT against `0f2e2f62`; its static spawn closes despite that body remaining live.
- 18:59:19.569: `CAUGHT-CONTRADICTION` restores `0f2e2f62` to LIVE, 1.722 seconds after the wrong catch resolution.
- 18:59:20.688: A3 targets that surviving body again, incorrectly starting at pity 2.

Root cause: `confirmTargetsFromNativeShrink` required a guaranteed catch before considering native shrink as ownership evidence. Ordinary capsule attempts could therefore pin and resolve against a provisional neighbor even when a different body's uniform shrink established the actual target. The existing correction path already transfers the presentation and pity correctly; removing the guarantee gate enables it for ordinary capsules as well. Guarantee still determines presentation mode. Renderer changes are unnecessary for this sequence.

Regression: `CaptureObservationRegression.hideonfloorNativeCorrection` uses the two recorded spawn coordinates with synthetic timestamps, labels and body bounds. It exercises inferred wrong-neighbor selection, native shrink without capsule/label support, pin transfer without guaranteed animation, single pity transfer, catch and breakout return, and both scan orders. This is a focused reproduction, not a byte-for-byte replay of this log segment.

Validation: the new regression failed before the production fix at the pin-transfer assertion. After the fix, `gradlew.bat coreRegressionTest '-PminecraftProfile=26.2' --offline` passed with 166,365 checks, including the suite's existing 332 recorded capture inputs. Existing workspace changes were preserved. No full profile matrix, publishing or deployment was run. In-game validation remains outstanding.
