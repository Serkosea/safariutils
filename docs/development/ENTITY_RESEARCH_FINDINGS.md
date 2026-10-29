# Safari entity research checkpoint — 2026-10-29

Read-only analysis of the five October 28 launch groups: 01H-59M, 02H-39M,
03H-42M, 22H-28M and 22H-43M (including each P2). Saved-run exports duplicate
launch observations and must not be counted as independent evidence.

## Floor drops

All five runs have the same quest-item ordering by ascending minimum network ID
of the three string displays. Each quest item occupies three consecutive IDs;
quest groups are contiguous in these samples. Ordered items:

1. Lime, Purple, Orange, Lime, Purple, Orange gems.
2. Three Wriggleworms, three Yogi Berries, three Bags of Seeds.
3. Five Soothing Incense.
4. Variable Shining Coins, then variable Icebreakers.
5. Mixed shards, Hunting Experience and Safari Essence afterward.

| Launch | Quest rewards | Coins | Icebreakers | All observed floor rewards |
| --- | ---: | ---: | ---: | ---: |
| 01H-59M | 29 | 5 | 4 | 53 |
| 02H-39M | 29 | 4 | 5 | 55 |
| 03H-42M | 29 | 4 | 5 | 59 |
| 22H-28M | 30 | 5 | 5 | 54 |
| 22H-43M | 28 | 3 | 5 | 64 |

New sessions: 118 rewards, all with one pending click candidate, no research
sequence gaps. This is strong observed correlation, not a server guarantee.
Pickup messages do not carry the clicked entity ID. Do not infer an absolute
run baseline from whichever floor drop the client happens to load first.
The coin/icebreaker/random boundaries cannot be fixed offsets from a partial
baseline. No reliable split of XP vs essence vs shard was established; these
categories interleave. Reward quantities/shard species are not predicted.

## Scrappy

Both new runs resolve all three armadillos via nearby-in-time feeding replies:
ascending body IDs map to Wholesale Wheat, Flavor-packed Fish, Lush Lily Pad.
Consecutive Scrappy bodies are three IDs apart. The player tried foods in
different orders. Replies still contain no target IDs, so keep candidate
attribution explicit and do not assume the first loaded Scrappy is the first ID.

## Pairing / Gemzie

Body-label ID +1 is common but is not a universal relationship: compound bodies
show +2/+3 or larger offsets and the existing tracker can exchange nearby labels.
Do not replace species, ownership, geometry and lifecycle checks with ID arithmetic.

Second new run: A98 initially chose body 689b2d65, then correctly retargeted to
9b20e742 based on disappearance. Its anchor incorrectly stayed at the provisional
body. The returning 49713e74 was rejected at squared distance 16.5424 (limit16),
although it reappeared beside the actual body's last position. This created L225
and an orphan A98 pin. Updating the anchor atomically with target ownership fixes
this without widening the association radius. Counterfactual production-tracker
replay retains L222/L223/L224 only; final lives are CAUGHT, pity 1/2/2 before total
cleanup. This is offline evidence, not a substitute for an in-game grouped retry.

## Logging / profiling

All six supplied gzip files decode correctly. No warning/error-level records in
the four launch segments. No research sequence gaps or dropped profiling stages.
Focused visit windows have median ~240 FPS in both new sessions. Mean marker
rendering is ~0.136/0.121 ms; max ~7.76/14.16 ms. There are isolated frame spikes
and one >16 ms CHAT/HANDLE sample per launch (~83/~76 ms), both at run-end
reward summary finalization. RUN/SAVE_TIMING attributes ~71/~70 ms to save and
~9/~6 ms to stats. These are end-of-run persistence hitches, not sustained marker
or diagnostic-writer overhead; retain as a persistence optimization follow-up.
Avoid claiming every frame is smooth from median FPS alone. The saved-run audit
validated 51,662 records, 599 events and 25,267 capture inputs with zero warnings.

## Approved quest-item palette (ARGB alpha is FF)

| Item | RGB |
| --- | --- |
| Icebreaker | #FF5745 |
| Purple Gem / pedestal | #9D39DB |
| Lime Gem / pedestal | #9DFF00 |
| Orange Gem / pedestal | #FF5B24 |
| Yogi Berry | #3964DB |
| Wriggleworm | #FF94C7 |
| Bag of Seeds | #D6B596 |
| Soothing Incense | #37D3DB |
| Shining Coin | #FFEF63 |

Pedestal colors are active. Floor prediction labels/colors remain a subsequent
Extra-only implementation; safe-mode pairing improvements may be shared, but
hidden loot/food inference must not leak into the standard edition.


## Immediate-identification investigation — five saved runs

The arrival-order concern does NOT mean pickups or a full traversal are needed.
The logs show the floor displays loading across all four biomes within seconds.
A reward-independent candidate solver finds this signature at the minimum observed
floor-group ID: six Cavern groups, nine Forest groups, five Haunted groups, all
consecutive triplets. Locations use the same weighted nearest-node biome mapping
as SafariAreaMap. The solver checks the entire prefix, not merely the lowest ID.
It then enumerates 3–6 coins and 4–5 icebreakers, rejects candidates contradicted
by observed locations, and emits only names shared by every surviving candidate.

| Saved run | Prefix uniquely identified after first floor observation | First pickup |
| --- | ---: | ---: |
| Oct 28 08:19 UTC | 1.24 s | 21.9 s |
| Oct 28 09:01 UTC | 1.50 s | 10.2 s |
| Oct 28 10:03 UTC | 0.49 s | 22.1 s |
| Oct 29 04:42 UTC | 1.00 s | 38.3 s |
| Oct 29 05:08 UTC | 1.50 s | 12.1 s |

All five inferred bases match the pickup-grounded bases. Coin counts resolve as
5/4/4/5/3 using location constraints. Across the observation timeline there are no
incorrect consensus predictions among the collected rewards. Final coverage is
282 of 287 observed floor groups; 281 of those have reward confirmation, all match.
The five ambiguous groups are precisely the possible fifth icebreaker positions.
This is in-sample validation of a pattern discovered from these runs, not an
independent guarantee about a future server update or partial late joins.

**Correction to the earlier icebreaker interpretation:** four *recorded pickups*
in run 1 did not establish a four-icebreaker population. That run has an uncollected
Icy drop exactly in the fifth-icebreaker position. The other four runs confirm five
icebreakers each. A fixed-five hypothesis matches all 285 recorded rewards, but the
uncollected run-1 boundary cannot prove it. Preserving a Floor Drop fallback at that
one boundary avoids inventing certainty; the rest can be identified near-immediately.

The highest initial Fluffling label is always floor-base minus 8 in these samples.
It is useful corroboration, but waiting for it would delay identification by up to
19 minutes on these routes. No production algorithm should require it.

### Scrappy refinement

All 15 initial Scrappy body IDs form groups at offsets 0/3/6 and map to wheat/fish/
lily pad. Four confirmed responses precede awakening for every one (start, start,
more, awake). Do not deduplicate repeated start text or increment for unsuccessful
foods/off-hand duplicate interactions/overfeeding after awakening.

A candidate first-body ID can be each observed body's ID, ID-3 or ID-6. Intersect
candidates from observed initial bodies and reject a candidate if a required body
ID is positively observed as a different entity type. Unknown IDs are not negative
evidence. Replay of this observation-only rule established the correct food roles
before each of the 15 name labels first appeared. Some bodies loaded long before
labels, so single-body arrival alone was not always decisive. Later breakout bodies
must retain their logical food state; they must not redefine the initial trio.

### Implementation guardrails

- Extra-only inferred titles/colors. Standard and restricted safe-mode presentation
  must not expose hidden loot/food inference, including through sync publication.
- Compute on changed observations, not each rendered frame; bound groups/candidates.
- Reset on visit/world transitions; allow incomplete input to remain unidentified.
- Never compact missing numeric IDs into array positions. Later generic loot IDs
  contain gaps and, in some runs, cease matching the initial triplet modulo.
- Validate the signature and biome constraints before publishing names; a server
  contradiction must invalidate predictions and produce one concise diagnostic.
- Keep actual pickup/feeding evidence separate from inference and audit predictions.
- These findings refine the approved UI plan; no live prediction feature is added
  by this investigation.

Reproduce the floor candidate audit with `python tools/floor_pattern_audit.py
<saved-run.gz> ... --output <report.json>`. It reads the original gzip files and
uses reward text only to grade predictions, not to choose them.


## Runtime implementation (2026-10-29)

`FloorDropPattern` owns the bounded visit model. `FloorDrops` supplies consecutive
three-display groups from its existing scan and keeps the position-to-ID association.
Only consensus categories are exposed; a fresh, single-pending pickup may constrain
candidate counts. Multiple pending clicks remain research evidence only. Unsupported
or contradictory models fall back to Floor Drop. Five icebreakers remain a hypothesis,
not a hard-coded certainty. XP/essence/shards share the title Loot.

`SafeMode.floorDropContents()` gates inference before labels, colors, and per-category
Sparkling Mode filtering. Standard builds always enforce it. Both marker outlines and
floor faces use the same exposed kind and category policy. Unknown or possible drops
retain a generic label; selecting any applicable always-on category conservatively
retains unknown drops. Schema 7 expands old biome floor bits to Loot plus quest bits,
without moving the old mound/wall/nest/pedestal bits. New masks are not re-expanded.

`ScrappyFeeding` uses the shared world snapshot to establish the initial armadillo
trio once, then stops that discovery scan. Confirmed main-hand feeding responses own
progress; wrong foods consume interactions without incrementing, repeated successful
text is valid, and ambiguous targets do not update any Scrappy. Inferred food is
Extra-only, while confirmed food is allowed in Safe Mode. Rolled-up bodies receive
food titles; awake bodies and capture projections keep normal species/pity titles.
An unknown initial feeding count displays ?/4 rather than assuming no teammate fed it.
Food colors remain unchanged pending user preference. None of these predictions are
published as confirmed party objectives or used as capture identity evidence.

Regression fixtures contain normalized numeric IDs, biomes, and reward categories
from the five recorded runs, with no player identifiers. Every arrival prefix is
checked for incorrect confident predictions. Synthetic tests cover missing input,
all coin/ice candidates, contradictory rewards, resets and migration, plus Scrappy
food candidates and progress. This remains in-sample validation; live testing is needed.

Testing: enable logging before entering. In Extra/developer Safe Mode controls turn
off Floor Drop Contents (Static Objectives) and Scrappy Food (Critter Overlays).
Check item labels/colors and Loot labels against pickups, including the one possible
fifth-icebreaker Floor Drop; feed each sleeping Scrappy and verify the title returns
to normal after awakening. Test wrong food and repeated correct food. Use the existing
/safari debug save command for a compact full-run log. FLOOR_PATTERN logs candidate
changes and predicted/actual rewards; SCRAPPY_FOOD logs confirmed progress and ambiguous
or inconsistent evidence. Entity research now also captures armadillo state transitions.


### Settings consolidation (schema 8)

Extra/developer Safe Mode controls now default off, with five canonical controls:
Detection & HUD, Critter Markers, Sparkling Markers, World Objectives, and Predicted
Information. Standard builds enforce the SafeMode API regardless of persisted values.
Old detection/overlay/objective controls merge using any-enabled semantics; mixed
choices therefore become conservative. The two unreleased prediction toggles are
retired, and their combined control defaults off as requested. Removed fields have
no live readers. Current-schema choices are never expanded again on load.

Always Active Markers uses exact item names. Lime/Worm retain the former Gems/Bird
Feed bits; Purple/Orange/Berry/Seeds append bits 14–17. Schema 8 expands old group
selections once. Unknown drops conservatively honor any selected item in their biome.
Floor Drops, Item Colors, outline and face pickers share one card. Item faces are
precomputed 45%-white tints of the outline RGB, retaining the selected face opacity.


### October 29 validation and presentation refinement

The two independent saved runs starting 20:00 and 20:24 UTC both contain five
confirmed Icebreaker pickups and no research-journal gaps. The first has 29 quest
item rewards, the second 28 (four versus three Shining Coins). Neither produced
an incorrect confirmed prediction under the original conservative model.
Five Icebreakers is now the preferred candidate rule; retain four only when
positive evidence rejects five. A missing entity alone never proves a four-drop run.
The two additional sanitized arrival-order fixtures contain 55 and 62 observed
positions and validate predictions against actual rewards without player identities.

Schema 9 splits Floor Drops into Floor Drop Loot and Floor Drop Quest Items. Both
new defaults are on; migration copies the old preference to both, preserving any
explicit new preference. Unknown contents remain generic when either is enabled.
Safe Mode still gates classification before titles, colors and filtering. The
Missing HUD counts known remaining positions by exposed kind, never inventing a
full remaining total from the expected spawn counts. Its own visibility toggle is
independent of the marker toggles.

Sparkling Mode hides ordinary Loot unless always-active. Each gem independently
becomes unnecessary when held or placed; the opened door resolves all gem colors.
Incense and Icebreakers retain centralized objective/held-item sufficiency rules.
Coins and bird feed may produce multiple critters, so simply holding one is not
sufficient reason to hide every remaining spawn opportunity. Always-active category
choices override relevance filtering, not the user's main marker toggles.

Scrappy titles use Food (N/4), with the requested food colors. A stabilized solo
visit defaults an unobserved feeding count to zero; party uncertainty remains ?/4.
Awake bodies revert to ordinary critter presentation. The server's four intermediate
mound messages set stages 1-4 and its break messages set stage 5. Stages require one
recent unambiguous attacked mound; click spam cannot inflate the count. Unknown
teammate hits are not fabricated and a later server stage corrects local progress.

Marker geometry renders before entity models, without writing depth. Labels render
later; this prevents the mod's boxes/faces from covering its labels. The 26.2 adapter
uses an early solid feature phase while retaining blending; 26.1.2 uses immediate
geometry during COLLECT_SUBMITS and deferred labels. Native/model glow remains a
later pass. Replacement renderers that replace the standard submit collection use
the normal ordered submission fallback; exact layering needs live visual validation.
No GPU readback, entity bounding-box masking, or extra world scan was added.


### Follow-up: reversed depth and normal-mode Icebreakers

The 14:57 launch correctly identified ICE pickups, but its marker filter hid them.
Normal-mode sufficiency must count remaining Troodon walls, not subtract every absent
catalog position from three; a saved shared collection only reduces the requirement
while Sparkling Mode is enabled. Unknown wall states remain potentially needed.

26.2 uses reversed Z (GREATER_THAN_OR_EQUAL), while 26.1.2 uses LESS_THAN_OR_EQUAL.
All depth-tested marker pipelines now inherit DepthStencilState.DEFAULT.depthTest()
without depth writes. The previous early-solid submission workaround is removed.
Floor faces are double-sided single quads, depth-tested in Safe Mode and through-wall
in Extra. Their fixed translucent color is derived from the exposed item's outline
color; the independent face color control is retired. Labels retain the later phase.

Visible Extra critter boxes select scene depth using a per-tick, per-cell cached
terrain visibility check. Hidden boxes retain through-wall rendering. This is a
conservative whole-box decision: partially concealed boxes can depth-test against
terrain when the center is visible, rather than an entity-only per-pixel depth mask.
No GPU readback or extra entity draw pass was introduced. Test camera transitions
near corners and front/back edges of moving critters in-game.

Missing HUD objective numbers match their labels. Separate Floor Drop Types defaults
on; disabling it sums the same relevant, evidence-gated counts into a single row.

### October 29, 15:32 verification and presentation follow-up

Both launch parts contain 34 floor pickup predictions, all matching rewards, with no research journal gaps or warning/error entries. Icy initially shows all 12 observed drops; after the third Icebreaker pickup it shows two of four remaining drops, correctly hiding the two surplus Icebreakers. All five subsequently collected Icebreakers were classified ICE. Keep this relevance filtering in normal mode too.

Floor faces now mix 20% white rather than 45% into the selected outline RGB (opacity unchanged), and Scrappy food colors are wheat F0D943, fish 41CCE8, lily 34C421. Settings display Color before Item Colors. Focused biome averages were approximately 239–240 FPS, but isolated frame spikes remain; averages do not establish hitch-free rendering.
