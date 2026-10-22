# Evaluation Rubric

Judge the concept against the frozen brief, not against novelty. Use `Pass`,
`Concern`, or `Fail` with one sentence of evidence for each relevant category.

## Hard failures

A direction cannot win when it:

- invents a capability, data source, derived fact, navigation destination,
  visible control, or interactive affordance
- removes or obscures essential product content or the primary action
- removes a visible real content module or monetization surface without
  explicit permission
- depends on unreadable generated text to explain the design
- violates safe areas, clips essential content, or uses impossible geometry
- requires shipping the generated bitmap as interactive UI
- conflicts with the native platform's navigation or accessibility model
- violates the declared System-native, Hybrid-native, or Custom-native strategy
- replaces preserved platform chrome with a fabricated lookalike
- changes preserved navigation, tabs, symbols, labels, ordering, selected
  state, brand colors, typography roles, or materials without permission
- lacks a credible native component map, or needs an unjustified custom
  navigation/control primitive
- cannot adapt to required devices, large text, or localization
- copies distinctive third-party branding or artwork

Back arrows, hamburger menus, overflow icons, chevrons, tabs, filters, badges,
row actions, and floating controls count as interactive affordances even when
they are conventional platform symbols. Fail them unless source evidence or the
user's brief explicitly allows them.

## Product fit

- Does the hierarchy serve the primary user job?
- Is the next action clear without explanatory decoration?
- Does disclosure match task frequency, urgency, and risk?
- Does the concept preserve real product states and constraints?

## Native platform fit

- Does every visible region map to an existing project component, a current
  semantic SwiftUI/Compose component, or a justified custom native primitive?
- Does it follow the selected component strategy and shell scope?
- Are navigation, presentation, back behavior, controls, and input cues native?
- Are system materials and effects used for function rather than costume?
- Does the design avoid browser patterns and static mockup tricks?

Do not penalize a direction merely because it uses custom native components.
For Hybrid-native and Custom-native, judge whether the custom treatment is
meaningfully ownable, natively implementable, semantically complete, adaptive,
accessible, and worth its complexity.

## Visual craft

- Is alignment intentional and spacing rhythm coherent?
- Are type roles distinct without oversized marketing hierarchy?
- Are grouping and depth understandable without excessive containers?
- Is contrast adequate and color semantic?
- Does content remain primary?

## Accessibility and adaptation

- Can controls meet platform target-size expectations?
- Can text scale, wrap, and localize without destroying hierarchy?
- Is essential state conveyed without relying only on color, motion, glass,
  haptics, or a shader?
- Can the layout work in required window sizes, orientations, and input modes?

## AI-slop rejection

Treat these as concerns when decorative, repetitive, or unrelated to the
product:

- purple-blue gradients, aurora blobs, neon glow, sparkles, and fake AI marks
- nested rounded cards or pills around routine content
- arbitrary floating controls and oversized empty space
- glass applied to content rather than a functional control layer
- decorative charts, badges, avatars, or metrics unsupported by real data
- uniform "premium" shadows, blur, and highlights with no depth model
- verbose aspirational copy replacing clear product language

Do not reject an effect merely because it is common. Name the product or
usability harm.

## Feasibility and risk

Identify:

- reusable project components versus necessary new primitives
- exact system/project component mapping for navigation, tabs, controls,
  progress, charts, lists, and symbols
- custom SwiftUI/Compose primitives, rendering path, behavior contract, and
  fallback required by Hybrid-native or Custom-native
- state, navigation, data, and localization changes implied
- availability gates and fallbacks
- likely layout, rendering, shader, battery, or performance risk
- whether the direction is a focused change or an architecture rewrite

## Still-image limitation

Do not score animation smoothness, gesture quality, haptics, focus continuity,
screen-reader order, state restoration, or runtime performance from a still.
List those under **What image generation did not verify**.
