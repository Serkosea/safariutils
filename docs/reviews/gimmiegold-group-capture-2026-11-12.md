# Grouped Gimmiegold capture review

The November 12 20:00 automatic segments show overlapping local Gimmiegold attempts. A130 and A131 began with exact target evidence, then disappearance-based correction reassigned them to nearby peers while other attempts were pending. A later replacement body was treated as an initial observation instead of the expected breakout. The queued breakout eventually fell back to A130, leaving a recatch pin for 40 seconds after the other catches resolved.

The shared capture tracker now refuses disappearance-only correction of exact targets during overlapping same-species attempts, and refuses stale impact correction after confirmed reappearance. Single-attempt/inferred correction and native-shrink evidence remain available. No texture identification, party-sync protocol, backend usage or saved catch totals are changed.

Regression coverage uses a grouped Gimmiegold sequence: overlapping exact throws, neighboring disappearance, breakout replacement, successful peer catch and final retry. It asserts stable target ownership and no remaining recatch pins. Existing capture tests pass. The recorded capture-input journal begins after the affected catches, so this is a targeted sequence regression, not an exact replay of that run. Live group retesting is still needed.
