---
name: prototype-ui-with-imagegen
description: >-
  Explore distinct visual directions for one native Apple or Android UI surface
  with built-in or delegated Codex image generation, compare them against real
  product and platform constraints, and optionally rebuild a selected direction
  in SwiftUI or Jetpack Compose. Runs in Codex or in any host that can call
  `codex exec`. Use for UI concepts, image-generated redesign variants,
  native component or screen prototypes, riffs on a concept, or implementation
  of a selected direction. Support system-native, hybrid-native, and
  custom-native strategies while preserving behavior, accessibility, the app
  shell, project components, symbols, and design tokens unless explicitly in
  scope. Report a screenshot-backed current-state audit, independent subagent
  review, labeled contact sheet, tradeoffs, detailed next steps, and downstream
  skill recommendations. Keep exploration read-only until the user explicitly
  selects a variant. Do not use for web, HTML, CSS, or generated production UI
  assets.
---

# Prototype Native UI with Image Generation

Use image generation as a divergence tool, not as the implementation. Generate
visual hypotheses, judge them against the real product, then rebuild only an
explicitly selected direction with native code.

## Interpret the invocation

Support these modes:

- **Explore** — default; generate three directions, compare them, recommend one,
  and stop without changing the project.
- **Explore x5** — generate at most five directions when the user explicitly
  requests five or uses `x5`.
- **Riff `<variant>`** — generate three new directions around a named result
  while preserving its successful principles. Do not edit the project.
- **Keep `<variant>`** — implement the selected result in the real app, validate
  it at runtime, and report the changes.

Treat one component, screen, state, or short coherent flow as one run. If the
request spans many surfaces, select the highest-value representative surface
when that is safe; otherwise ask the user to narrow the target.

Resolve two independent design axes before exploration:

### Component strategy

- **System-native** — maximize reuse of current project and semantic platform
  components. Use for conservative, familiar, or low-risk requests.
- **Hybrid-native** — preserve the system shell and interaction behavior while
  allowing custom native content components and visualizations.
- **Custom-native** — allow an original visual language and custom SwiftUI or
  Compose primitives inside the approved scope. Keep native semantics,
  accessibility, adaptation, input behavior, and runtime implementation.

If the user explicitly asks for system, standard, custom, ownable, bespoke,
radical, or non-system visuals, apply the matching strategy to every direction.
Otherwise create a deliberate portfolio:

- three directions: one System-native, one Hybrid-native, one Custom-native
- five directions: one System-native, two Hybrid-native, two Custom-native

Every direction must still differ in hierarchy or interaction thesis; component
strategy alone is not sufficient divergence.

### Shell scope

- **Preserve shell** — default; keep navigation/tab destinations, behavior,
  labels, selected state, and out-of-scope project identity.
- **Redesign shell** — only when the user explicitly includes navigation, tabs,
  top-level structure, or the whole app shell in scope.

Component strategy does not imply shell scope. A Custom-native content surface
still preserves the app shell unless the user explicitly authorizes changing
it.

## Select the image-generation backend

Resolve this once, before any generation, and state the selected backend in the
run output.

- **Built-in** — default when the host has its own image generation, as Codex
  does. Call `image_gen` directly, one call per direction.
- **Delegated `codex exec`** — for a host without built-in image generation,
  such as Claude Code or another agent CLI, when an authenticated Codex CLI is
  available on the machine. Read
  [codex-exec-backend.md](references/codex-exec-backend.md) and follow its
  invocation, prompt contract, and verification steps exactly. The host still
  owns the audit, brief, evaluation, contact sheet, independent review, and any
  native implementation; Codex only produces images.

Use one backend for every direction in a run so comparison stays fair. Neither
backend requires a separate image-generation API key.

## Enforce hard boundaries

- Generate every image through the selected backend's real image-generation
  model. Never satisfy a generation step with ImageMagick, Python, PIL, SVG,
  HTML, a screenshot edit, or any other local drawing or compositing tool, and
  never present such a file as a generated direction.
- Do not require Fable, an external MCP server, or an image-generation API key.
- Do not create an HTML, CSS, JavaScript, or browser-based variant picker.
  Present images and the generated contact sheet directly in the conversation.
- In Explore and Riff, do not edit source, assets, documentation, project
  settings, or Git state. Keep preview artifacts outside the repository.
- Never ship a generated screenshot as the interface, a sliced mockup, a
  background that contains controls, or a substitute for native code.
- Do not invent product capabilities, data, navigation destinations, or copy to
  make a concept look complete.
- Preserve current system chrome, navigation and tab structure, project
  components, semantic colors, typography roles, symbols, and brand identity
  unless the user explicitly authorizes changing a named item.
- Follow the selected component strategy. Do not require a stock system
  component in Hybrid-native or Custom-native when a real native custom
  primitive better expresses the approved design thesis.
- Never confuse Custom-native with raster UI, web styling, or behaviorless
  imitation. Every custom element needs a credible native implementation map.
- Do not claim that a still image validates motion, gestures, haptics, focus,
  accessibility, dynamic content, localization, or runtime performance.
- Preserve unrelated user changes. Commit only when the user asked for a
  commit.

If the host has no built-in image generation, switch to the delegated
`codex exec` backend. If neither backend is available — no built-in generation
and no authenticated Codex CLI — state that image generation cannot run and
stop before generating. Offer the imagegen CLI fallback only as an explicit
opt-in because it requires an API key; never switch silently.

## 1. Reconstruct the real product

Before prompting:

1. Read repository instructions and inspect Git status without changing it.
2. Identify Apple versus Android, the real UI stack, deployment targets,
   supported devices, design tokens, reusable components, and current state
   management.
3. Load the applicable installed `apple-design` or `android-design` skill when
   available. Treat its current platform baseline as authoritative; verify
   volatile or prerelease APIs against first-party documentation and the
   installed SDK.
4. Inspect the target source and adjacent states: loading, empty, populated,
   error, disabled, permission, keyboard, large text, dark appearance, and
   adaptive layouts as relevant.
5. For an existing runnable app, build or launch it and capture the exact target
   state before the first image-generation call. Treat this runtime baseline as
   required, not optional. Inspect a local screenshot yourself before passing it
   to generation — `view_image` in Codex, the host's own image-reading tool
   elsewhere. On the delegated backend, attach it with `-i`.

Label every input image:

- **Edit target** when structure, content, and identity must remain and only
  specified visual properties may change.
- **Reference** when it communicates product context, composition, or style but
  genuine structural divergence is allowed.

If runtime capture is blocked after a concrete attempt, record the command or
tool, failure, and missing prerequisite. Source-informed exploration may
continue, but every result remains at least `Concern`; do not declare a winner
or enter Keep until a runtime baseline or user-provided current screenshot is
available.

### Report the current state before generation

When a runtime or user-provided screenshot exists, inspect it at full size and
use focused crops or zoom when needed. Post a concise evidence-backed bullet
audit in the conversation before or alongside the generated results:

- what already works and should be preserved
- hierarchy, spacing, typography, alignment, density, and affordance problems
- clipping, safe-area, keyboard, localization, accessibility, or adaptive risks
- inconsistent materials, depth, symbols, controls, or platform behavior
- motion or interaction observations only when recording/runtime evidence exists
- source/runtime facts versus hypotheses requiring later validation

Anchor each point to a visible region, state, source location, or runtime
observation. Do not turn personal taste into a confirmed defect. If there is no
screenshot or runnable baseline, say so and provide only a source-informed
current-state summary when repository evidence exists, or a brief-informed
summary when the user's brief is the only evidence.

## 2. Freeze the brief

Write a compact brief before generation:

- platform, device or window class, appearance, and exact UI state
- primary user job and primary action
- source-backed content and verbatim copy
- an allowlist of visible controls and interactive affordances confirmed by
  source or the user
- a **preservation map** of current system chrome, project components, symbols,
  semantic colors, typography roles, content, and states, with every item
  marked `Preserve` or explicitly `Allowed to change`
- selected component strategy and shell scope for each direction
- invariants that every direction must preserve
- freedoms the concepts may explore
- forbidden additions and known accessibility or localization constraints
- implementation constraints from the actual project

Do not use generated text as the source of truth. Repository copy and product
requirements win even when the image renders different text.

Treat every back arrow, hamburger, overflow menu, chevron, tab, filter, badge,
row action, and floating button as product behavior rather than harmless visual
decoration. Do not allow one unless source evidence or the user confirms it.
Do not infer calendar weekdays, progress values, destinations, or other facts
from incomplete inputs.

Treat the current screenshot as an **edit target** for everything marked
`Preserve`. Treat areas marked `Allowed to change` as reference context.
Layout divergence inside the target surface does not authorize redesigning the
shell or unrelated modules.

## 3. Generate real alternatives

Read [variant-prompting.md](references/variant-prompting.md) before generating.
Read [native-component-gate.md](references/native-component-gate.md) and create
a native component map for every direction before issuing its prompt.
Classify each request as `ui-mockup`.

Issue one generation call per direction through the selected backend, each with
a separate prompt. Do not use one multi-output request as a substitute for
intentionally different prompts. Generate three directions by default and no
more than five.

Give every direction:

- a memorable neutral name
- one primary design thesis
- its component strategy and shell scope
- at least one meaningful axis of divergence such as layout, density,
  hierarchy, navigation model, disclosure, spatial organization, or
  interaction model
- the shared brief and invariants
- a mapping from every visible interactive or structural element to an existing
  project component, a current platform component, or a justified custom native
  primitive
- platform, viewport, state, and output framing identical to the other
  directions so comparison remains fair

Do not count palette, gradient, corner-radius, glass, shadow, illustration, or
copy changes alone as a distinct direction.

Inspect every result at readable size. Reject or retry once with a single
targeted correction when a result breaks an invariant, invents functionality,
adds an unapproved interactive affordance, has impossible geometry, clips
essential content, violates its component strategy or preservation map,
regresses preserved platform chrome, changes protected brand tokens, or becomes
unusable because of malformed text. Keep the original direction thesis during
a correction.

On the delegated backend, first run the backend verification in
[codex-exec-backend.md](references/codex-exec-backend.md): confirm the file
exists at the requested path, that the call did not fall back to a local
drawing tool, and that nothing outside the artifact directory changed. A
backend failure is not a weak direction; report it rather than accepting the
file or hand-building a replacement.

### Create the labeled design sheet

After the final accepted images exist, create one comparison image with every
direction in the same order used in the conversation. Use the bundled script:

```text
python3 scripts/make_contact_sheet.py \
  --item "A · Quiet Ledger=/absolute/path/quiet.png" \
  --item "B · Daily Focus=/absolute/path/focus.png" \
  --item "C · Progress Trail=/absolute/path/trail.png" \
  --output /absolute/path/prototype-directions.png
```

Use short, exact variant names. Do not add verdicts, scores, or recommendation
badges to the sheet; those would bias comparison. Do not use image generation
to re-create or collage the screenshots because that can alter product details.
Keep the sheet outside the repository in Explore and Riff. The script requires
Pillow. If it is unavailable, report the missing local dependency and request
approval before installing it; still present the individual images, but do not
silently omit or fabricate the sheet.

## 4. Evaluate before recommending

Read [evaluation-rubric.md](references/evaluation-rubric.md). Evaluate each
direction as `Pass`, `Concern`, or `Fail`; do not manufacture numeric precision.
A hard failure cannot win.

When continuing from pre-generated directions, recover the original frozen
brief, prompts, native component maps, and baseline from the conversation or
artifacts. If any are unavailable, reconstruct only what explicit evidence
supports, list the missing provenance, downgrade every affected result to at
least `Concern`, and re-check generated facts and same-state comparability from
scratch. Do not inherit an earlier winner or verdict.

When subagents are available, assign one independent read-only critic after the
images and sheet exist. Give it only the frozen brief, preservation map,
baseline evidence, generated images, contact sheet, and evaluation rubric. Ask
it to identify invariant violations, unsupported affordances, relative
strengths, risks, and whether any literal image is eligible to win. Do not tell
it the parent's preferred direction or intended answer. The parent remains
responsible for the final recommendation and must note material disagreement.
If subagents are unavailable, perform the same second-pass review and disclose
that it was not independent.

Present each image inline at a useful size rather than only in a contact sheet.
Also present the labeled contact sheet inline as the fast comparison view.
For each direction, state:

- what it tests
- what improves over the current surface
- its component strategy and shell scope
- its native component map, including every custom primitive and justification
- its product and accessibility tradeoffs
- native implementation feasibility
- likely implementation cost or risk

Recommend one result only when it clearly fits the brief. Otherwise say that
there is no winner and identify the smallest useful next generation pass.

Read [conversation-output.md](references/conversation-output.md) and follow its
complete conversation contract. The response must include the current-state
audit, labeled design sheet, per-direction assessment, independent review,
recommendation rationale, risks, expanded next steps, and downstream-skill
handoff. Do not reduce the result to image links and a winner.

Then stop. Explore and Riff must not proceed into implementation.

## 5. Riff on a direction

Resolve the selected image from the current conversation or ask the user to
reattach it. Preserve the chosen direction's successful hierarchy, product
job, content, component strategy, shell scope, native component map, protected
brand tokens, and platform behavior.

Generate three fresh alternatives, each changing one or two named aspects.
Avoid cosmetic mutations. Re-run the current-state delta, contact sheet,
independent review, evaluation, and conversation output contract, then stop
again.

## 6. Keep and rebuild natively

Enter this phase only after an explicit `keep`, unambiguous selection, or direct
request to implement a named generated result.

Read:

- [apple-promotion.md](references/apple-promotion.md) for Apple projects, or
- [android-promotion.md](references/android-promotion.md) for Android projects,
- plus [visual-validation.md](references/visual-validation.md) in both cases.

Translate the chosen image into explicit rules for hierarchy, layout, spacing,
type roles, color roles, materials, component boundaries, states, and motion
intent. Treat the image as a design reference, not a pixel contract. Resolve
ambiguous or generated details using the product model, native conventions,
accessibility, and source copy.

Implement the approved native component map with the project's existing
architecture. Reuse preserved components directly. Implement approved custom
elements as real SwiftUI or Compose primitives rather than rebuilding the
raster literally.
Add a custom control, shader, blur, glass treatment, or transition only when it
has a named functional purpose and a graceful accessibility and availability
fallback.

Build and run the actual app. Capture the same state and viewport as the
reference, compare structure at full screen and in focused crops, and make
targeted corrections. Test real interaction, alternate states, large text,
dark appearance, reduced motion, and adaptive sizing as applicable. Record
motion when judging motion.

Finish with:

- selected direction and extracted design rules
- files changed
- build, test, and runtime validation performed
- visual differences intentionally retained and why
- unverified devices, states, or risks
- commit hash only when a commit was requested and succeeded

Do not call the result complete when the app was not run, unless the environment
made runtime validation impossible; state that limitation directly.
