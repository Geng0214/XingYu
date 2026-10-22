# Native Component Gate

Create this map before prompting and verify it again after generation.

## Select the strategy

Use one strategy per direction:

- **System-native:** resolve in order: existing project component, current
  semantic platform component, then a narrowly justified custom primitive.
- **Hybrid-native:** preserve shell and standard interaction behavior; freely
  propose custom native content components where they create meaningful product
  hierarchy or identity.
- **Custom-native:** permit an original native visual system, custom layouts,
  drawing, shaders, transitions, and styled controls throughout the approved
  scope. Preserve native semantics, state, input, accessibility, adaptation,
  and platform integration.

Custom-native does not mean avoiding all system APIs. Reuse system behavior and
infrastructure where they are invisible or beneficial; customize the visual and
interaction layer only where the design thesis calls for it.

## Select shell scope

- **Preserve shell:** keep top-level navigation, tabs/navigation suite,
  destinations, labels, ordering, selected state, back behavior, and
  out-of-scope brand identity.
- **Redesign shell:** allow a new native shell only when the user explicitly
  placed it in scope. Map all destinations and behaviors, including back,
  restoration, deep links, accessibility, and adaptive presentation.

Never infer Redesign shell from Custom-native.

## Component map

Record one row for every visible region:

| Region | Current source/component | Strategy | Proposed native component | Preserve/change | Native contract |
| --- | --- | --- | --- | --- | --- |
| App shell | `<source symbol>` | `<strategy>` | same project component | Preserve | existing behavior |
| Primary action | `<source symbol>` | `<strategy>` | `<system or custom native>` | `<scope>` | semantics + state |
| Content visualization | `<source symbol>` | `<strategy>` | `<platform API or custom>` | Allowed to change | adaptation + fallback |

Include navigation, tabs or navigation suite, toolbars/top app bar, sheets,
lists, buttons, menus, progress, charts, search, input, symbols/icons,
materials, and custom drawing when visible.

## Preserve product truth by default

Unless the user explicitly names them as redesign targets, preserve:

- navigation destinations, back behavior, tab destinations, labels, ordering,
  and selected state
- the current shell presentation when shell scope is Preserve
- existing product behavior and interaction semantics
- brand identity unless the user includes visual identity in scope
- all visible real content modules, actions, states, and monetization surfaces

System-native also preserves project component identity by default.
Hybrid-native and Custom-native may replace the visual construction of
in-scope components, but not their product behavior or data without permission.

## Custom-native budget

A custom primitive in Hybrid-native or Custom-native must include:

- the design thesis and why custom treatment adds product value
- the native implementation approach: SwiftUI/Compose view, Layout, Canvas,
  shader, Metal/AGSL, style, or other platform-native primitive
- semantic and accessibility behavior
- adaptive and localization behavior
- reduced-motion or animation-disabled behavior when animated
- availability and performance fallback

Custom visual styling of buttons, progress, charts, cards, and content controls
is allowed in Hybrid-native and Custom-native. Preserve their correct semantics,
state, focus, target size, and input behavior.

Custom navigation, tabs, fields, menus, toggles, or presentations require
Redesign shell or explicit component scope plus a complete behavior contract.

## Post-generation gate

Inspect the raster rather than trusting the prompt. Fail a literal direction
when it:

- replaces current platform chrome with a flat, generic, fabricated, or
  obsolete-looking imitation while shell scope is Preserve
- changes project symbols, tab icons, labels, ordering, accent, type roles, or
  materials outside the approved strategy and scope
- drops or invents a visible content module or control
- cannot be mapped to the declared native components
- uses a raster-only effect with no credible native implementation
- loses semantics, accessibility, adaptation, state, or input behavior in the
  name of custom visuals

A structurally promising failed direction may be riffed after restoring the
preserved contract. It cannot win as rendered.
