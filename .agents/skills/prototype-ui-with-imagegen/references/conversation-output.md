# Conversation output contract

Return a self-contained product-design review, not a gallery caption.

## 1. Current-state analysis

When screenshot or runtime evidence exists, provide evidence-backed bullets:

- strengths worth preserving
- confirmed visual or interaction problems
- accessibility, adaptation, localization, and state-coverage risks
- source/runtime facts
- hypotheses that still require verification

When there is no visual baseline, use:

- `Source-informed current state` when repository or implementation evidence exists
- `Brief-informed current state` when the user's brief is the only evidence

Do not claim pixel-level defects in either case.

## 2. Prototype brief

State the platform, viewport, exact state, primary job, allowed controls, selected component strategies, shell scope, and hard invariants.

## 3. Runtime baseline

Name the build/runtime evidence and capture conditions. Include concrete blockers and their consequence for recommendation confidence.

Also name the image-generation backend used for the run — built-in or delegated `codex exec` — and confirm that all directions came from the same one. Report any backend failure or retry rather than hiding it.

## 4. Preservation map

Use a compact table:

```text
Surface or token | Preserve / Allowed to change | Evidence or reason
```

## 5. Design sheet

Show the labeled contact sheet inline and link its local artifact. Explain that it is for comparison and that the full-resolution images below remain authoritative.

## 6. Generated directions

Show every direction inline at useful size. For each include:

- thesis and what it tests
- improvement over the current state
- component strategy and shell scope
- native component map and justified custom primitives
- product, accessibility, and adaptation tradeoffs
- implementation cost, dependencies, and likely failure modes

## 7. Comparative evaluation

Use `Pass`, `Concern`, or `Fail` for:

- product fit
- preservation/invariants
- native platform fit
- visual craft
- accessibility/adaptation
- implementation feasibility

Explain every Concern or Fail. Do not average hard failures into a winner.

## 8. Independent subagent review

Summarize:

- strongest agreement
- invariant violations or risks the parent missed
- material disagreement
- literal-image eligibility

Do not paste the subagent's entire response.

## 9. Recommendation and pitfalls

State:

- recommended direction, or explicitly no winner
- why it best serves the primary job
- what should be borrowed from other directions
- what must be corrected before implementation
- main UX, native-component, architecture, accessibility, and runtime risks
- confidence and which missing evidence could change the decision

## 10. Detailed next steps and skill handoff

Give ordered, decision-ready steps rather than “implement and test.” Include:

1. the smallest next design decision
2. whether to `riff`, `keep`, or stop
3. required product clarification or missing state
4. native component spike or technical proof needed
5. implementation slices and checkpoints if Keep is chosen
6. runtime flows, devices, appearances, accessibility settings, and captures to verify
7. regression and performance gates

Recommend installed skills by purpose, using exact available names when known:

- `riff <name>` with this skill for unresolved hierarchy or visual direction
- `keep <name>` with this skill for approved native implementation
- `apple-design` or `android-design` for platform-native implementation and review
- `audit-ios-app-redesign` when the Apple app needs a broader read-only redesign audit before implementation
- `find-apple-animation-opportunities` or `find-android-animation-opportunities` to discover motion opportunities after the static hierarchy is approved
- `review-apple-animations` or `review-android-animations` after motion is implemented
- `implementation-orchestrator` for a separately approved multi-step implementation plan

Recommend only skills actually available in the session. Explain why each recommended skill is the next fit; do not list the catalog.

## 11. What image generation did not verify

Separate still-image uncertainty from actual failures. Cover motion, gestures, haptics, accessibility semantics/order, state restoration, localization, dynamic content, runtime performance, and platform API behavior as applicable.

## 12. Choose next step

End with explicit commands:

- `riff <name>` — refine one or two unresolved aspects
- `keep <name>` — rebuild the selected direction natively and validate it
- `stop` — preserve the exploration without changing the app
