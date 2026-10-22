# Variant Prompting

Use this guide for Explore and Riff image-generation calls.

## Prompt contract

Keep the shared product facts identical across directions. Change the design
thesis deliberately.

```text
Use case: ui-mockup
Asset type: native <Apple or Android> UI concept for design exploration
Primary request: <one-sentence direction thesis>
Input images: <Image 1: edit target or reference, as applicable>
Platform: <OS, native framework, device or adaptive window class>
State: <exact product state being shown>
User job: <what the person is trying to accomplish>
Component strategy: <system-native, hybrid-native, or custom-native>
Shell scope: <preserve or redesign>
Required content: <source-backed content and verbatim copy>
Allowed controls: <complete allowlist of confirmed actions and affordances>
Preserved contract: <shell, product behavior, content, and protected tokens>
Native component map: <region -> existing, system, or custom native component>
Structure: <direction-specific hierarchy and composition>
Visual language: <platform-native material, type, color, and depth intent>
Interaction cues: <only interactions the real product supports>
Invariants: <facts, controls, identity, safe areas, and content to preserve>
Constraints: realistic native implementation; readable hierarchy; accessible
  contrast; sufficient control size; localization resilience
Avoid: invented features or data; generic AI gradients; decorative glow;
  card soup; gratuitous glass; unrequested back, hamburger, overflow, chevron,
  tab, filter, badge, row action, or floating control; inferred weekday or
  progress value; violation of selected strategy or shell scope; fabricated or
  obsolete preserved platform chrome; unapproved palette, symbol, tab, or
  typography changes; raster-only controls; fake browser chrome; watermark;
  device frame
Output: one full-size, straight-on UI screen at the specified viewport; no
  collage, comparison board, annotations, marketing scene, or tilted device
```

Quote important copy verbatim, but assume generated lettering may be imperfect.
Use source text during evaluation and implementation.

List every permitted visible control. An ordinary platform icon still implies
behavior and must not appear unless confirmed. Represent unknown activity
names, times, locations, values, or dates with user-approved placeholders or
neutral non-text blocks; never manufacture realistic content.

For an existing app, include the current screenshot as an edit target for the
preserved contract.

For **System-native**, add:

```text
Use existing project components and current semantic platform components first.
Preserve their identity and behavior. Add a custom primitive only when required
by the named design thesis.
```

For **Hybrid-native**, add:

```text
Preserve the native app shell and product behavior. Create an ownable custom
native treatment for the in-scope content using implementable SwiftUI/Compose
views, layouts, drawing, or effects. Do not merely restyle stock cards.
```

For **Custom-native**, add:

```text
Create a genuinely original native visual system inside the approved scope.
Custom layouts, components, drawing, shaders, materials, and transitions are
welcome when implementable in SwiftUI/Compose. Preserve the product model,
content, semantics, accessibility, adaptation, and shell scope. Do not imitate
web UI or rely on raster-only controls.
```

## Choose meaningful axes

Prefer axes that test a product decision:

- linear versus spatial hierarchy
- immediate controls versus progressive disclosure
- focused single task versus information-dense overview
- list, grouped detail, pane, or canvas organization
- persistent versus contextual navigation
- direct manipulation versus explicit controls
- compact versus editorial information rhythm

Do not create directions that differ only by:

- accent color or gradient
- corner radius, shadows, glass, blur, or glow
- illustration style
- icon family
- headline wording

## Edit target versus reference

For an **edit target**, repeat the invariants in every prompt:

```text
Change only <allowed properties>. Keep the information architecture, content,
control count, navigation, product identity, viewport, and state unchanged.
```

For a **reference**, say what may be borrowed and what must not be copied:

```text
Use the reference only for <context/compositional principle/material mood>.
Create an original native layout for this product. Do not reproduce branding,
copy, proprietary artwork, or unsupported controls from the reference.
```

## Preserve comparability

- Keep viewport, appearance, content, and UI state fixed.
- Keep the allowed-control list fixed.
- Keep every item in the preserved contract fixed.
- Make the three default strategies visibly and structurally distinct.
- Use the same fidelity and full-screen framing.
- Give each direction one dominant thesis.
- Issue a separate built-in image-generation call per direction.
- Inspect images at full size; thumbnails conceal hierarchy and clipping.

## Correct a failed render

Retry at most once before discarding a direction. Change one defect:

```text
Keep the previous design thesis and all invariants. Correct only <defect>.
Do not introduce new content, controls, decoration, or layout changes.
```

If the correction still violates a hard invariant, mark the direction `Fail`.
