# Visual and Interaction Validation

Validate the native implementation, not the generated image.

## Establish comparable evidence

Record:

- selected concept and its extracted design rules
- app build, commit or working-tree state, target, device, OS, appearance, text
  size, locale, and exact content state
- repeatable navigation or launch sequence to the surface
- baseline screenshot or recording when one exists
- preservation map and approved native component map
- selected component strategy and shell scope

Capture the implementation at the same viewport, state, appearance, and content
as the concept. Compare full screens first, then crop or zoom only where a
specific discrepancy needs inspection.

## Compare in this order

1. Primary job, hierarchy, navigation, and action placement
2. Preserved contract: shell, destinations, product behavior, content, tokens,
   and any project components marked Preserve
3. Native component map versus the components actually implemented
4. Content completeness, wrapping, clipping, scrolling, and safe areas
5. Alignment, spacing rhythm, type roles, target sizes, and grouping
6. Semantic color, materials, elevation, symbols, and custom rendering
7. Alternate data states, themes, text sizes, locales, and adaptive windows
8. Motion, gestures, focus, haptics, interruption, and runtime performance

Do not chase raster-identical pixels when the generated concept conflicts with
native rendering, accessibility, real content, or platform behavior. Document
intentional differences.

## Validate motion with motion

Record every important transition or gesture. Inspect start, intermediate, and
end frames plus reversal, cancellation, rapid repeat, keyboard movement, and
interactive dismissal. A screenshot cannot establish smoothness or continuity.

Use reduced-motion or animation-disabled settings and confirm that content,
state, and navigation remain correct.

## Completion gate

Do not declare completion until:

- the actual app builds and launches, or the environment limitation is stated
- the main flow and affected alternate states are exercised
- accessibility and adaptive layout checks relevant to the change pass
- the selected design thesis remains recognizable without shipping a raster
- preserved project/system components remain real components rather than
  raster-inspired replicas
- Hybrid-native and Custom-native visuals are real native primitives with
  verified semantics and fallbacks rather than static imitations
- visible regressions introduced by the rebuild are fixed or explicitly blocked
- custom effects have a purpose, fallback, and proportionate performance check

Report untested hardware, OS versions, locales, states, and performance risks
instead of implying full coverage.
