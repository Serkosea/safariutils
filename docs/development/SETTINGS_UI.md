# Shared settings presentation

Settings redesign changes presentation and navigation, not gameplay ownership or persisted keys.

## Reuse rules

- `SettingsUiKit` owns responsive control geometry, combined cards, toggles, picker cells,
  status surfaces, section accents, and selection indicators. Reuse these primitives rather
  than implementing component-specific approximations.
- Each screen owns one `SettingsMotion`. Call `SettingsUiKit.beginFrame` once before drawing.
  The layout signature includes size, scrolling, section/modal identity and search. A changed
  layout resets coordinate-keyed control transitions, preventing unrelated controls from
  borrowing animation state. The navigation indicator is independent of content layout.
- Motion uses one monotonic timestamp per screen frame, a 93.33 ms eased transition, and at most
  512 states. There are no animation workers, tick handlers, writes or network calls. Settled
  controls return their exact target. Screens always enable motion; gameplay/HUD effects and
  selected theme colors are unaffected.
- Use stable reflected fields and their original owners as control identities. Navigation is
  allowed to move a control; persisted data is not. Search reuses the same metadata and editors.
- Safari accordion sections retain explicit expand controls regardless of group size;
  diagnostic markers have no redundant section heading and always remain visible. Switching tabs preserves expanded sections and
  scroll positions rather than destroying the user's working context.
- A tab's sole root group is rendered directly, without an extra accordion. Marker navigation
  separates General, Critters and Objectives using the original fields. Critters contains
  collapsible Special Critters and Recatch groups, with no redundant root accordion.
- Search context headers are navigation links: they reveal ancestor sections, scroll to the
  matching control, and briefly highlight it. Search itself remains directly editable.
- Special-theme decorative stars cover the full menu in one shared batch, not a layer per card.
  Settings screens and the addon hub use `SpecialTheme.settingsStars` at 2.1 density;
  HUDs and other screens retain their existing densities.
  Card bounds never move on hover. Focus changes must not save settings or run configuration
  actions; resize/reinitialization must retain search values and entered data.
- Ordinary settings cards use a thin border, with no accent rail or extra hover line. Hover blends both the
  surface and full border toward the same accent as the contained control. Multi-setting cards use
  one whole-card border/divider hover state, including whitespace; moving between child controls
  must not restart the outer highlight. Surface gradients remain specific to the interactive row,
  extend across the full width beside inset dividers, and are painted before divider lines.
  Single-card gradients also require the caller's interactive hit target, not card whitespace.
  Toggle switches receive the same clipped row-hover state as their full-row click target,
  so their border highlights anywhere the toggle can be clicked, including in combined cards.
  Existing hub entries, section headings, navigation and status retain their accents.
  The shared primitive draws only a fixed number of quads; it adds no idle animation loop.
  Menu motion is always on; the temporary reduced-motion setting has been removed.
- `display.hitboxPityTitle` is the single persisted pity-title preference, enforced by
  `Markers.pityLabel` for live, remembered, shrinking and recatch markers. The old
  `recatchPityTitle` field remains serialized for compatibility only and has no runtime authority.

## Navigation and theme controls

HUDs has four per-HUD tabs plus Borders, each with Edit HUD Positions at the top. Safari Runs
contains General, Visual, and Profit. Marker General and Critters use intentional small groups;
Special Critters and Recatch remain collapsible. Bridge Appearance has Colors, Names, and
Emojis & Media dropdowns; ChatUtils/ServerUtils omit redundant category headings outside search.
Special Theme is integrated into every normal theme popup via `SettingsUiKit.specialThemeRow`.
It sits below normal themes with a rainbow heading; the live-preview popup closes with Done.
The same persisted choice/unlock state is used everywhere, including temporary Astral behavior.
Normal and special themes are mutually exclusive selections; there is no special None option.
Selecting a normal theme disables the saved special choice. Special themes use their own fixed
base palettes plus animated accents, ignoring the remembered normal/custom palette. Custom
preferences are retained while inactive. The temporary Easter-egg override still lasts 30 seconds
regardless of picker changes; those changes select what follows the override.
Normal and special theme selections preview live without dismissing the picker (Custom opens its editor).
Inline text controls use the shared 8px inset and picker-aligned optical baseline. Both Enter keys
finish editing through the existing validation/submission path, or blur search without clearing it.
Background search availability is derived from modal ownership during initialization, rendering and input;
focus/resize must never bring an underlying input above a popup. Shared `control` renders picker shells
with the same eased border transition in standalone and component settings.
Shared borders use the existing screen-space horizontal gradient under special themes and
retain 1px thickness otherwise. Explicit content clips also cover cached special-theme geometry.

## Validation

`SettingsMotionRegression` verifies snapping, interpolation, reversal, reduced motion, layout
invalidation, bounded storage, navigation continuity and long frame gaps. Shared standalone
and embedded classes must still pass the byte-parity gate.

In-game validation is necessary for font/resource-pack variation, optical spacing, GUI scales,
search jumps, modal stacking, inline caret placement, tab scrolling and theme appearance. The
headless regression suite does not render GPU output or establish FPS improvement.
