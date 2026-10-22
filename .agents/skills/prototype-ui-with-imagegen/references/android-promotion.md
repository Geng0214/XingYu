# Promote a Selected Android Direction

Load the installed `android-design` skill before implementation. Its current
Google I/O and Android platform baseline and freshness checks take precedence
over this concise handoff. Verify experimental or version-dependent APIs
against official Android documentation and the project's actual dependencies.

## Convert image decisions into Compose

Start from the approved native component map. Reuse existing composables and
the current app shell directly; do not recreate how image generation drew them.

Extract:

- semantic content hierarchy and traversal order
- adaptive pane, navigation, and back-stack model
- Material type roles, scalable spacing, shapes, and semantic colors
- component state layers, emphasis, and depth relationships
- reusable composable boundaries and explicit UI states
- intended spatial continuity, not invented timing from a still

Implement new interface work in Jetpack Compose using the project's existing
architecture. Prefer Material components, adaptive layout APIs, semantic
actions, system back and predictive-back behavior, and stable state ownership.
Use Material 3 Expressive selectively rather than restyling every component.

For System-native, map common surfaces to components supported by the project's
actual dependency baseline: existing navigation/scaffold, adaptive navigation
suite or panes, top app bar, Material buttons, menus, progress indicators, lazy
lists, and semantic icons.

For Hybrid-native or Custom-native, implement approved original visuals with
real composables, custom `Layout`, modifiers, `Canvas`/`DrawScope`, AGSL,
`RuntimeShader`, or native graphics. Preserve navigation state, predictive back,
semantics, focus, input, adaptive behavior, and accessibility unless the shell
is explicitly in scope. A custom visual control must retain correct roles,
state descriptions, actions, target sizes, and animation-disabled fallbacks.

Use Canvas, `DrawScope`, AGSL, `RuntimeShader`, `RenderEffect`, blur, or custom
graphics only for a named content or interaction purpose. Bound layers,
sampling, overdraw, and allocations; provide API-level and
animation-disabled fallbacks.

## Required checks

- phones, tablets, foldables, resizable windows, insets, keyboard, and posture
  changes where supported
- large font scale, display size, TalkBack order, Switch Access, keyboard,
  mouse, trackpad, high contrast, and Remove animations
- standard target sizes and alternatives for drag-only or swipe-only actions
- light and dark themes, localization, long content, loading, empty, error,
  disabled, and restored states as relevant
- predictive back, transition interruption, rapid repeat, and state restoration
- release-build jank and rendering behavior when custom graphics or motion are
  involved

Do not add XML layouts, browser styling, CSS, HTML, or WebViews to reproduce the
mockup. Preserve an existing View interoperability boundary only when the
project requires it.
