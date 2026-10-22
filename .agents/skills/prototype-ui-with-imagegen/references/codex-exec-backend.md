# Delegated `codex exec` image-generation backend

Use this backend when the host running the skill has no built-in image
generation of its own — for example Claude Code, another agent CLI, or a plain
shell — but the machine has an authenticated Codex CLI. The host stays the
orchestrator: it owns the audit, the frozen brief, the evaluation, the contact
sheet, and the conversation output. Codex is used only as an image-generation
process, one direction per call.

Never mix backends inside one run. All directions in a run must come from the
same backend so comparison stays fair.

## Preconditions

Check once, before the first generation call:

1. `codex --version` succeeds.
2. Codex is authenticated (a previously working `codex exec` run, or
   `codex login status` where available).
3. Image generation is enabled. Pass `--enable image_generation` on every call
   so the run does not depend on the user's `~/.codex/config.toml`.

If any precondition fails, say so plainly and stop before generation. Do not
substitute a local drawing tool, a stock image, or a hand-built mockup.

## Canonical invocation

One call per direction. Write the prompt to a file and pipe it on stdin.

```bash
codex exec \
  -C "$OUT_DIR" \
  --skip-git-repo-check \
  --enable image_generation \
  -s workspace-write \
  -o "$OUT_DIR/A.last-message.txt" \
  -i "$BASELINE_PNG" \
  - < "$OUT_DIR/prompt-A.txt"
```

- `-C "$OUT_DIR"` — the writable workspace. Point it at the artifact directory
  **outside the repository**, never at the project. This is what keeps Explore
  and Riff read-only with respect to the app.
- `--skip-git-repo-check` — the artifact directory is normally not a Git repo.
- `-s workspace-write` — Codex may write only inside `$OUT_DIR`.
- `-o` — captures the final message, which is the saved image path.
- `-i` — attaches the baseline screenshot or reference images. Omit it when
  there is no baseline.
- `--ephemeral` — optional; skips persisting a Codex session for the call.

`-i` accepts multiple values, so it swallows a positional prompt argument. The
prompt **must** come from stdin (`- < file`); passing it as an argument after
`-i` fails with `No prompt provided via stdin.`

The calls are independent processes and may be run concurrently. Budget roughly
one minute per image.

## Required prompt contract

Every delegated prompt must carry all of the following, in addition to the
direction's own brief, invariants, preservation map, and native component map:

```text
Generate one image and save it to <ABSOLUTE_OUTPUT_PATH>.

<direction brief: platform, device class, appearance, exact UI state, thesis,
component strategy, shell scope, verbatim copy, allowed controls, invariants>

HARD RULES
- Use your built-in image generation tool exactly once. This is the only
  permitted way to create the image.
- Do NOT use ImageMagick, magick, convert, sips, ffmpeg, Python, PIL,
  matplotlib, SVG, HTML, or any other local drawing, compositing or filtering
  tool to create or modify the image.
- Do not edit any file other than the output path above.
- If image generation is unavailable to you, create no file and reply with
  exactly IMAGE_GEN_UNAVAILABLE.
- Reply with only the absolute path of the saved file.
```

The tool ban is not optional. Without it a delegated Codex run will sometimes
satisfy the request by drawing the image with ImageMagick, which produces a
plausible-looking file that is not a generated concept at all.

When a baseline is attached, restate the labeling in the prompt: the attached
image is the **edit target** for everything marked `Preserve`, and only
**reference** context for areas marked `Allowed to change`.

## Verify every returned image

A delegated call is not trusted output. Before a direction enters evaluation:

1. The output file exists at the requested path and is a non-trivial image.
2. The last message is that path, not `IMAGE_GEN_UNAVAILABLE` and not an
   explanation.
3. No local drawing tool was used. Re-run with `--json` and inspect
   `command_execution` items if a result looks suspicious — flat fills, crisp
   vector-perfect edges, or a single system font are the usual tells.
4. Nothing outside `$OUT_DIR` changed. Confirm the project's Git status is
   still clean in Explore and Riff.
5. The host inspects the image itself at readable size, exactly as it would a
   built-in result, and applies the same rejection criteria from `SKILL.md`
   §3: invariant breaks, invented affordances, impossible geometry, clipped
   content, strategy or preservation-map violations, malformed text.

A file that fails 1–4 is a backend failure, not a weak direction. Report it and
retry the call once; do not silently accept it or hand-build a replacement.

## What does not change

- The contact sheet is still composed locally by
  `scripts/make_contact_sheet.py`. Codex never assembles it.
- The independent critic runs in the host's own subagent mechanism when it has
  one. Do not delegate the critique to the same `codex exec` process that
  generated the images.
- Keep and rebuild is native code written by the host. This backend generates
  images only.
