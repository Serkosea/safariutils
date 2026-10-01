# Changelog

## [2.4.0] - 2026-10-01

### Changes & Additions

- Replaced Ticket Trading's inline names with reusable saved-player profiles, three active slots, and three ordered backup slots
- Added per-player Sparkling species filters so each Sparkling-only trader is invited only for selected critters
- Migrated existing trusted names and Sparkling flags into profiles with all 37 species enabled
- Added cached, API-key-free Minecraft profile resolution so saved and displayed player names use canonical capitalization and authoritative rank colors where available
- Refined Ticket Trading with equal-width profile panels, shorter balanced active cards, compact dragged cards, and matching two-sided Sparkling styling
- Invited qualifying Sparkling-only active players immediately outside the 17–20 second batch window and replaced confirmed offline active players from the ordered backup slots after three seconds
- Made the Special Sparkling theme override player rank colors while retaining rainbow player rows for Sparkling-only trading profiles in every theme
- Made Ticket Trading and Sparkling screens choose compact layouts and fallback scaling from the actual GUI canvas after Minecraft GUI Scale is applied

### Fixes

- Kept ordinary settings sourced from the shared advanced configuration, including Special Themes and Party Sync, visible without the star unlock
- Restored All Feed Done banner, sound, and chat controls to every build while migrating their legacy private-prefixed setting keys
- Preserved the selected settings tab's expanded dropdowns when reopening settings within the existing ten-second memory window
- Restricted Ticket Trading invite, warp, and disband automation to the confirmed party leader
- Reduced the Sparkling guest leave-to-accept delay to 250 milliseconds while retaining immediate accepts when no party must be left
- Used Duplico's disguise-aware visibility check for its first visible Sparkling chat alert
- Delayed every user-facing Sparkling found banner, sound, and chat alert until the critter is first visually confirmed

## [2.3.0] - 2026-09-30

### Changes & Additions

- Rebuilt settings as a compact single-sidebar workspace with meaningful tabs and collapsed, nested section accordions
- Reorganized Alerts into General, Banners, and Chat, with Safari, Encounters, and Contest groups inside the latter two tabs
- Combined each logical settings section into a cohesive card while keeping distinct waypoint features in separate toggle-and-color cards
- Completed shared-card row and outer borders, reset accordions when changing tabs, and recursively collapse nested sections with their parent
- Matched settings scroll limits to the exact clipped viewport so final cards and dropdowns remain fully visible
- Cached responsive setting text layouts and removed per-frame shared-card and accordion traversal allocations
- Reused inline-editor hit bounds and removed repeated per-frame theme-label stream allocations
- Bounded width-specific settings layout caches during repeated live window resizing
- Reorganized Theme & Border Colors into HUD and Stats groups without changing any saved color keys
- Preserved normal shared-card grouping when related settings appear together in search results
- Matched search ordering to settings navigation, added feature-specific search breadcrumbs, and made complete inline-field frames reliably focusable
- Added a directly accessible Safe Mode Sparkling Critters toggle and flattened hidden-critter controls into the Critter Overlays accordion
- Kept Safe Mode visible in regular builds with a clear read-only explanation that its controls require Extra
- Added responsive navigation widths, wrapped fixed headers, stacked setting controls, adaptive collection/party columns, wrapped ticket-trading content, and content-aware dashboard scaling
- Grouped Safari convenience, ticket trading, and party sync under Gameplay while moving presentation-only chat and screen-effect controls under Display
- Clarified Sparkling Mode guidance and ordered its alert groups as Banner, Chat, and Special Catch
- Added optional NPC and expanded Scrappy dialogue filtering, with dynamic colored Shard Trader summaries, preserved clickable choices, and traded shards included in run profit
- Added per-player Sparkling-only ticket trading, including detection-gated host invites and safe leave-then-accept guest transfers
- Matched each enabled Sparkling-trading button and username field with one shared animated row tint
- Matched settings category and accordion typography and refreshed the default HUD layout from the maintained configuration
- Kept Reset Layout synchronized with canonical HUD defaults and layered recently moved HUDs above editor headings and controls until stationary
- Combined on-screen and outgoing chat alerts under Alerts while keeping unlock-gated Safe Mode and Developer tools isolated by build
- Replaced Safe Mode's master switch with independently active feature groups and migrated existing disabled configurations safely
- Paired waypoint toggles with their colors under one nested Waypoint Settings section and flattened redundant Developer navigation
- Preserved every existing setting key, control, theme, search result, warning, custom editor, and page-level reset
- Clipped scrolling content below the fixed tab header, culled off-screen rows, and retained tab-level resets
- Added dedicated DeployBuilds and BuildArtifacts workflows for dual-version testing and a clean flat eight-jar release set

## [2.2.1] - 2026-09-28

### Changes & Additions

- Added a Display Nametags picker for choosing None, Vanilla, Waypoint, or Both names on critters with rendered hitboxes and waypoints
- Defaulted critter nametags to Both while keeping hitbox visibility, detection, pairing, pity, and objective labels independent

## [2.2.0] - 2026-09-27

### Performance

- Kept animated Sparkling visuals on a shared 40 FPS clock with cached geometry, colors, labels, and alert state
- Reduced automatic diagnostic overhead through buffered, event-driven logging and a persistent developer-only disable option
- Reused critter pairing, marker geometry, and visibility state across world rendering and HUD consumers

### Changes & Additions

- Added an extensive Sparkling catch-alert editor with responsive previews, saved named presets, configurable effects, songs, intensity, duration, volume, text, and layered visual controls
- Added distinct escalating Sparkling discovery songs and compact bundled audio assets
- Added animated gradient Sparkling hitboxes, through-wall beacons in Extra Mode, world-space labels, and provisional markers before bodies finish loading
- Added Eagle rarity handling for guaranteed catches, pity labels, and recatch markers
- Added spawn-range data to conservatively retire exhausted Safe Mode candidate waypoints
- Added always-visible Progress HUD profit and independent total/biome progress toggles
- Added developer-only run-saving and learned-location controls plus optional automatic-log disabling
- Anchored the Join countdown to the destination play connection, displaying `Closing` at zero and `Closed` once the Safari Manager begins the lockout dialogue
- Added opt-in Ticket Trading with a 20-second one-command multi-invite, joined-player-only warping from 27–29 seconds, host-departure cleanup, exact-name invitation acceptance, and provisional late-arrival roster confirmation
- Limited routine `DeployBuilds` development deployment to Minecraft 26.2 while retaining an explicit 26.1.2 compatibility-build task

### Fixes

- Stabilized moving and far-away critter pairing, including Hideons, Duplico, Billygoat, Cavernfish, and capture transitions
- Kept static waypoints until their locations are authoritatively cleared and removed caught markers without requiring the player to approach them
- Prevented capture entities and nearby same-species bodies from stealing Sparkling or ordinary hitboxes
- Hid vanilla critter nametags only while that exact critter has a rendered mod hitbox
- Kept custom Hideon and Hideyho geometry consistent for movement, Sparkling states, and recatch pins
- Delayed Sparkling Hideyho completion alerts until the authoritative completion message
- Corrected player lookup casing, rank colors, focus behavior, and Ticket Trading text input
- Kept successfully warped Ticket Trading parties together until the host leaves the ticketed Safari instance
- Kept custom alert fields and preview state stable through focus changes and window switching

## [2.1.1] - 2026-09-23

### Fixes

- Kept remembered Duplico, Hideonwall, and Hideonfloor markers until their nearby loaded location is directly verified empty
- Removed a locally caught Hideonfloor marker through its exact aimed-at capsule target instead of waiting for a later inspection
- Retired one unverified static marker after a loot-shared catch without hiding matching critters currently detectable in Extra Mode or visible in Safe Mode
- Preserved Sparkling particle evidence while a named label pairs with or replaces its critter body
- Prevented temporary capture-animation labels from becoming provisional critter markers
- Kept Safari-only chat filtering and Hideyho auto-accept from affecting messages outside Safari
- Centered Hideyho markers on exact half-block and block-edge positions

### Changes & Additions

- Added animated multi-color gradients to Sparkling hitboxes, beacon beams, and world-space names
- Applied the complete Sparkling gradient and cached star treatment to every visible HUD while a Sparkling is active
- Added provisional moving hitboxes and names for detected critters before their bodies finish pairing
- Removed the hitbox distance limit so every currently detected critter can render
- Added independent Progress HUD toggles for total and biome progress bars
- Added a selectable Drop Items chat-hiding group

## [2.1.0] - 2026-09-22

### Fixes

- Made solo objective tracking authoritative without enabling Party Sync or sending sync messages
- Prevented Birdfeeder Empty and All Feed Done from firing before the tracked feeder stack is actually consumed
- Completed Wumpa after either a catch or a fatal attempt, while allowing a later party catch to replace Retreated with Caught
- Colored intermediate Gemzie, Wumpa, and Doomspiral states yellow and completed placed-gem checks green
- Gave Party Objective rows consistent pale biome-relative colors
- Kept hidden Safari chat messages fully available to trackers, alerts, history, and objective synchronization
- Limited Rockmite objective hiding to its six exact mound-progress and outcome messages
- Based Cavern completion on catching a Gemzie, while showing Door Open 2.5 seconds after its authoritative chat message
- Collapsed completed biome details to their terminal objective state and kept Forest bird counts available after all feed is done
- Matched Party Objective player-name colors to their live tab-list rank colors
- Cached player-name colors on player-list and world changes instead of rescanning the tab list during rendering
- Excluded two manually rejected, transition-only Hideyho positions from learned locations
- Drew mound waypoints above solid supporting blocks instead of boxing the floor, including the mound anchored at `-80,57,61`
- Let selected settings subtabs collapse when clicked again, at any nesting depth
- Kept nearby, simultaneously present critters as separate remembered individuals instead of repeatedly replacing each other's waypoints
- Started the pre-ticket Join countdown from the earliest relevant queue or party-entry notice, and changed its zero-state label to Closing
- Kept a partymate's early entry announcement from marking the local player as inside Safari before their own transfer
- Ended failed-join timing samples when the server reports a kick instead of carrying their timeline into a later lobby

### Changes & Additions

- Added a five-column Hide Chat Messages checker with independently selectable run, catch, objective, critter-interaction, and biome dialogue groups
- Increased the visual contrast between Haunted biome titles and their paler objective-detail lines
- Sorted every bundled static coordinate list by horizontal distance from the Safari center at `-49.5, 0.5`
- Renamed the terminal objective rows to Gemzie and Doomspiral, and made completed Forest progress read All Feed Done
- Stored static catalog keys at cube centers—including the upper cube of Hideyho's two-block box—migrated older local keys automatically, and made static waypoint rendering independent of local decimal observations
- Stopped saving redundant decimal observations and creating empty local static-catalog files when the bundled assets are sufficient
- Bundled four player-verified Hideonfloor locations from the opt-in research data
- Added an opt-in Show Learned Candidates overlay below Save Learned Locations; bright red-orange review waypoints show only new Hideonfloor blocks across the whole Safari
- Added four-column Always Active Critters and grouped Always Active Waypoints checkers, keeping selected critter hitboxes or objective waypoints visible through Sparkling Mode's ordinary filtering
- Limited opt-in location research to conservative Hideonfloor block centers in any stable Safari party size
- Made stationary objective catalogs bundled-only so obsolete local waypoint files cannot affect rendering
- Added opt-in party objective synchronization through parsed party chat, with compact sender/lobby-bound verification tokens and an all-members confirmation gate
- Reset Party Sync to off every Minecraft launch and added an activation warning explaining its party-wide requirement
- Preserved complete pre-confirmation run state, coalesced queued snapshots, batched hive coordinates, and announced mid-run sync shutdowns to the party
- Added change-only objective diagnostics and broadened the Safari Run Research preset for solo and party runs while keeping high-volume packet and particle streams off
- Buffered debug-log writes with bounded flush timing and prevented quick log toggles from overwriting an earlier file
- Rejected moved Hideonfloor sightings from location learning
- Added expanded developer diagnostics for measuring the complete pre-ticket Safari join window
- Reorganized output-log presets around focused debugging situations, including combined solo-run research, and added a live list of every enabled category
- Removed the obsolete isolated Testing Session mode
- Added independently selectable, read-only inbound server-packet diagnostics for transitions, HUD state, inventories, entities, world state, and custom channel identifiers
- Made each Runs-tab scroll input move through one full 15-row history page

## [2.0.0] - 2026-09-20

### Performance

- Reduced frame drops from the Special Rainbow theme, HUD sprites, alerts, and world markers
- Cached large saved-run histories so they are not reformatted, remeasured, or repriced every frame
- Added batched waypoint geometry, off-screen marker culling, cached waypoint labels, and spatially indexed critter pairing
- Added cached HUD layout, text, border, progress-bar, and objective-icon rendering
- Reused per-tick world entities, wall state, session aggregates, and decoded waypoint catalogs across consumers
- Batched Sparkling celebrations and encounter banner geometry for smoother alert rendering
- Unified animated rainbow colors around one shared, frame-limited clock and fixed screen-space wavelength

### Fixes

- Made display, waypoint, recatch, and Safe/Extra settings update correctly during an active run without losing tracked state
- Kept Extra-only detections available when returning to Extra Mode without treating them as visually confirmed in Safe Mode
- Preserved Party Objective HUD state and biome details when display options change or location text briefly refreshes
- Corrected Sparkling Hideyho detection, completion, run history, and HUD cleanup
- Kept rainbow gradients continuous across changing text lengths and adjacent values
- Matched editable-text carets to the animated rainbow at their current screen position
- Made every rainbow frame edge sample one consistent horizontal gradient
- Kept large Sparkling collection totals comfortably inside the collection panel
- Prevented the optimized GUI batching layer from failing during client startup
- Kept History table edge spacing stable as run numbers and profit totals grow
- Kept History Runs, Catches, and Sparkling stars on fixed outer anchors regardless of text length
- Applied the Special Rainbow gradient to complete interface status lines
- Applied the Special Rainbow gradient to complete client messages and the SafariUtils chat tag
- Made Gemzie podium chat messages the only source of placed gems, preventing cursor moves, drops, and losses from counting as placements
- Kept carried objective items in HUD totals while rearranging them
- Hid party members whose known objective-item totals are all zero
- Corrected objective completion to require every Forest feed spawn and a 2.5-second settled Gemzie door
- Treated Doomspiral's terminal retreat message as completed Haunted progress after a failed fight
- Rendered a yellow unknown Forest title state whenever run-wide feed totals cannot be confirmed
- Prevented caught Hideonwall markers from being restored by lingering replacement entity IDs in Extra Mode
- Prevented failed learned-location saves from retrying every client tick

### Changes & Additions

- Added rainbow Sparkling markers around completed biome titles and beside collected species on Safari Stats
- Kept Stats critter names in their rarity colors, made nonzero catch totals white, and ordered biomes as Cavern, Icy, Haunted, then Forest
- Added a conservative pre-ticket Join countdown with a gradual green-to-dark-red timer to the Progress HUD
- Expanded the Bird Feed HUD into a biome-aware Party Objective HUD for Cavern, Icy, Haunted, and Forest progress
- Added cached reference-matched icons for Soothing Incense and the three Gemzie gems
- Added an efficient always-on rolling error and lifecycle log without requiring verbose debug logging
- Added objective-status and biome-color border options for the Party Objective HUD
- Added configurable Cavern, Icy, Haunted, and Forest completion marks to the globally visible Party Objectives title
- Sorted party-member display lists alphabetically without changing internal roster order
- Defaulted Party Objective options on, with biome border coloring enabled and status coloring disabled
- Clarified Party Objective labels and kept Forest action labels consistently green
- Expanded the Special Rainbow theme across settings, interface accents, and every HUD
- Smoothed continuous rainbow borders, extended the gradient to editable text, and increased the cached star-field density
- Added four selectable intensities and a current-intensity preview for the special Sparkling catch alert
- Added photosensitivity confirmation for the higher Sparkling catch intensities
- Kept Special as the original gentle catch celebration and jingle while giving higher intensities distinct batched bursts, comets, orbits, and confetti
- Required photosensitivity confirmation whenever an intense catch alert is selected and reset rejected selections to Special
- Enhanced the Advanced unlock constellation with cached stars, orbiting sparks, and batched geometry

## [1.6.0] - 2026-09-18

### Fixes

- Used the server's Critter Capsule allocation to reliably start runs across every ticket submission path
- Kept the title-only party attendance HUD visible while waiting for Safari Tickets
- Kept Sparkling detection and detected Hideonfloor waypoints available before ticket use
- Made Starting Items wait for the complete capsule-populated inventory without including later floor drops
- Prevented cursor-held or rejected Bird Feed transfers from being treated as completed deposits
- Confirmed Bee Nest completion from a nearby Honeybug spawn and supported both left- and right-click interactions
- Restored recatch pins and pity progression for Doomspiral, Wumpa, and every other non-Common capsule catch
- Kept the Contest HUD visible in Dungeons and Kuudra when Show Everywhere is enabled
- Kept HUD panels and their editor labels aligned consistently against every screen edge
- Prevented interactions outside the Safari from reaching Safari-only objective trackers
- Improved Bird Feed HUD performance by caching panel state and batching icon rendering
- Kept rainbow text gradients continuous across adjacent values and changing text lengths
- Kept large Sparkling collection totals comfortably inside the collection panel

### Changes & Additions

- Added a movable Bird Feed HUD with feed, Birdfeeder, Forest-drop, and visible-bird status
- Added one-pixel arrow-key adjustments to the HUD editor
- Improved Safari party and run lifecycle tracking around delayed arrivals, disconnects, and lobby transitions
- Cleaned up shared trackers, settings migration, rendering helpers, and build organization

## [1.5.1] - 2026-09-17

### Fixes

- Made Starting Items use a capsule-gated run-start snapshot so all starting items are included without later floor drops

### Changes & Additions

- Improved Starting Items run-start detection for every ticket and perk combination

## [1.5.0] - 2026-09-15

### Fixes

- Made Starting Items wait for a stable, capsule-populated inventory so slower items are included
- Prevented Starting Items party messages from being discarded while party-list data is briefly stale
- Updated automatic Hideyho acceptance to use the current server-provided action instead of a fixed command
- Kept manual Hideyho choices visible when no valid automatic action can be found or sent

### Changes & Additions

- Added a configurable Starting Items chat alert with item selection, quantities, and correct plural names
- Replaced the Sparkling command collection with a dedicated `/sparkling` menu
- Added direct editing for Sparkling counts and Rainbow Feathers in the collection menu
- Added a Party tab for importing shared or missing Sparkling lists used by Sparkling Mode
- Improved Sparkling menu layout, rarity colors, completed-biome styling, inline editing, and status feedback

## [1.4.1] - 2026-09-08

### Fixes

- Prevented No Feed and All Feed Used from both sending after Forest objectives are complete
- Prevented Only In That Biome encounter alerts from firing in the Safari center or connecting paths

## [1.4.0] - 2026-09-08

### Fixes

- Fixed All Feed Used detection when closing the Birdfeeder immediately after depositing
- Allowed Total Feed messages to report No Feed after Forest objectives are complete
- Fixed Hideonfloor waypoints disappearing from ambiguous catch messages
- Fixed a crash when a Hideonfloor label loaded before its entity body
- Smoothed moving Duplico and Hideon waypoint rendering

## [1.3.3] - 2026-09-07

### Fixes

- Added clear client messages when Hypixel API requests fail and limited Bazaar refreshes to once every five minutes

## [1.3.2] - 2026-09-06

### Fixes

- Fixed Safe Mode Hideonwall detection and waypoint resolution issues
- Improved Birdfeeder completion tracking by reconciling personal deposits with bird spawns
- Prevented a new contest from completing from the previous contest's lingering result
- Kept HUD editor labels centered and visible near screen edges

## [1.3.1] - 2026-09-04

### Alerts and birds

- Added Mute Other Sounds to keep Safari Utils alerts and sound previews audible while muting other Minecraft audio
- Changed an empty `<ALL_FEED>` value to `No Feed`
- Removed the unintended local client-message copy of Biome Uniques Done chat alerts
- Split Contest warning suppression into separate banner and chat settings
- Applied Only In That Biome separately to Forest bird banners and chat alerts
- Removed the unreliable Feed Type Used alert and placed Birdfeeder Empty before All Feed Used
- Prevented rejected or cursor-held Bird Feed clicks from firing All Feed Used early

### Defaults and polish

- Updated fresh-install defaults to match the maintained configuration, with Safe Mode enabled and the high-intensity Sparkling catch disabled
- Prevented verified solo players from sending configured party alerts and refreshed party status quietly after reconnecting
- Made Reset Page affect only settings visible in the current expanded view
- Added confirmation before resetting the HUD layout
- Added scrollbar-free navigation scrolling for small windows
- Made on-screen HUDs, banner alerts, the HUD editor, and Safari stats scale consistently on smaller screens
- Kept the settings search prompt inside its field at narrow widths

## [1.3.0] - 2026-09-02

### Gameplay

- Added ticket protection while party members are still missing from the Safari, including ticket-menu number-key selections
- Added automatic Hideyho Hide 'N Seek acceptance
- Improved party-ready alerts for the current party size and automatic roster verification

### Birds and alerts

- Grouped bird alerts together and added feed totals, Feed Type Used, All Feed Used, and Birdfeeder Empty alerts
- Feed-used alerts track inventory deposits rather than bird-spawn messages
- Added compact feed-list text with correct plurals
- Combined banner playback into Off, Banner, Sound, or Banner + Sound, preserving existing preferences

### Commands and polish

- Moved Sparkling commands to `/sparkling` and removed the run-reset command
- Added missing-list entry/display and clipboard imports for shared lists, missing lists, and complete Sparkling chat messages
- Unified client-message styling and trimmed unnecessary message clutter
- Reduced repeated text parsing and ticket-menu field lookups without changing gameplay timing


## [1.2.0] - 2026-09-01

### Sparkling critters

- Added particle-backed Sparkling detection.
- Added a rainbow beacon, labels, hitboxes, and waypoint styling for a detected Sparkling.
- Added `Only Show Sparkling` to hide ordinary stationary-critter waypoints while Sparkling Mode is active.
- Added an optional high-intensity Sparkling catch celebration with a prominent photosensitivity warning and confirmation.
- Fixed duplicate detection alerts, transformed-entity tracking, stale Missing HUD entries, and leftover recatch markers after a Sparkling catch.
- Safe Mode now accepts a genuinely visible critter nametag while continuing to withhold hidden internal entity information.

### Waypoints and reliability

- Added `Hide Possible` for players who know the objective routes and do not want candidate waypoints displayed.
- Expanded the bundled stationary-critter and objective-location catalogs from verified solo runs.
- Improved Rockmite mound detection, close-position matching, party-break cleanup, entity-range persistence, and shared scan performance.
- Updated the Sparkling beacon to use a continuous translucent Minecraft-style beam.
- Moved Snoozle wall waypoints up to eye level and reorganized recatch settings with the critter waypoint options.

### Interface and polish

- Reorganized Safe Mode settings with clearer, alphabetized descriptions.
- Standardized `armor` spelling throughout the project.

## [1.1.2] - 2026-08-30

### Fixes

- Known Rockmite mound positions remain detectable when a Flavor Packed Fish overlaps their interaction box.
- Live Rockmite mounds now recover after a temporary interaction-entity loading gap in either mode.

## [1.1.1] - 2026-08-30

### Fixes

- The `/safari stats sparkling` summary now shows how many saved runs have passed since the last Sparkling run.
- Rockmite mound waypoints now disappear immediately for local breaks and promptly when entering a Cavern already cleared by party members.
- Floor-drop waypoints now disappear promptly when a nearby teammate collects them.

## [1.1.0] - 2026-08-30

### Fixes and improvements

- Added shared banner appearance settings for font style, text shadow, configurable or alert-matched backgrounds, borders, and timer bars.
- Blank custom chat-alert text now suppresses only that message while preserving banners and sounds.
- Ignore Uniques now hides ordinary hitboxes and markers for shared Sparkling species without hiding Sparkling duplicates.
- Inline setting edits no longer block unrelated clicks; manually edited slider values still guard their own slider.
- Sound pickers now explain right-click previews and use each alert's configured volume and pitch.
- Missing HUD objectives are grouped together, with a separate Unique Only display option.
- Added an option to remove the Icy cold overlay throughout the Safari.

## [1.0.0] - 2026-08-29

The first standalone Safari Utils release.

### Safari tracking

- Reliable run start and end detection from Safari Manager messages, activity fallbacks, reward summaries, and lobby IDs.
- Current-run, history, lifetime, Safari Essence, Rainbow Feather, and Bazaar profit tracking.
- Movable Progress, Missing, and Contest HUDs with directional expansion.
- Waypoints and objectives for floor drops, nests, mounds, walls, recatch spots, and special encounters.
- Bundled static-location catalogs with runtime discovery for previously unknown positions.

### Sparkling critters

- Per-species totals, duplicate counts, Rainbow Feather totals, history markers, filtering, and manual corrections.
- Sparkling Mode with shared-list filtering, unique catches, objective handling, and Ignore Uniques.
- Sparkling tracking that survives entity changes, with biome-aware chat messages.
- Rainbow HUD styling, a collection view, detection banner, catch messages, and a fullscreen catch celebration.

### Contest and party tools

- Real-time Miria's Contest countdown, bracket and score parsing, ticket tracking, persistence, and alerts.
- Pre-run party count and stable full-party notifications.

### Interface and customization

- Dependency-free settings with search, nested sections, full-row controls, custom themes, and live previews.
- Themed HUD editor with snapping, scaling, banner placement, and saved layouts.
- Custom alert text, duration, color, sound, volume, and pitch, plus a larger alphabetized sound list.
- Redesigned statistics pages with themed controls, sortable history, scrolling, balanced layouts, and cached totals.
- Special themes and individual Safe Mode controls where applicable.
- Safe Mode and Extra jars for both supported Minecraft versions.
- Extra may show information the player cannot directly see and may not be safe to use.
- Minecraft 26.2 support is available but has received limited testing.

### Reliability and performance

- Shared per-tick caches for scoreboard, tab-list, entity, HUD, and statistics data.
- Separate rendering backends for Minecraft 26.1.2 and 26.2.
- Better entity pairing, static-object checks, stale waypoint cleanup, party roster stability, and Sparkling deduplication.
- A bundled baseline of observed static locations, without full-biome discovery sweeps.
- Runtime files grouped under `config/safariutils/`, with automatic migration of compatible data.
