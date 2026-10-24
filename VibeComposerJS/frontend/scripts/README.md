# Frontend tests

Install from `frontend/` with `pnpm install --frozen-lockfile` using the version pinned in `package.json`; see [setup](../../README.md#setup-and-builds) and the [TinySynth patch contract](../patches/README.md).

Run tests from `frontend/` using the `test:*` scripts in `package.json`. `pnpm test` (or `pnpm run test:unit`) runs all functional suites without Chromium. `pnpm run test:browser` runs all twelve browser suites sequentially, including the focused overview matrix. Focused commands remain available below. Append `--list` to either aggregate command to list its suites without running them; other arguments are forwarded to Node's test runner. With pnpm, pass script arguments directly, without npm's extra `--` separator.

Production browser suites use `dist/browser`: run `pnpm run build` first and rebuild after changing application code. The browser aggregate checks for the build and a browser before starting; focused production suites also report a missing build explicitly. Existing assets can be reused for test-only changes. The overview layout and TinySynth fixture suites read source directly and do not require a production build. Browser discovery accepts `OVERVIEW_TEST_BROWSER` as an explicit Chromium executable path, then checks common Chrome/Edge/Chromium locations on Windows, Linux and macOS. Focused suites skip when no browser is installed; the aggregate fails with a setup instruction. Supported viewport widths start at 700 CSS pixels.

## Change area to checks

Run the relevant functional commands, then the browser commands for affected interactions. Build first when application code changes. For changes across owners, use both aggregate commands.

| Change area | Functional commands (`pnpm run …`) | Browser commands (`pnpm run …`) |
| --- | --- | --- |
| Project decoding, persistence, Java XML import | `test:parts:imports`, `test:java-import` | `test:preferences:browser` |
| History, arrangement commands, selection | `test:parts:history`, `test:selection`, `test:overview` | `test:overview:browser`, `test:contextual:browser` |
| Settings inheritance, contextual generation, seeded parity | `test:parts:scopes`, `test:parts:generation`, `test:generation` | `test:generation:browser`, `test:contextual:browser` |
| Manual phrases, draft history, manual Mode preference | `test:parts:phrases`, `test:parts:preferences` | `test:manual:browser`, `test:score:browser` |
| MIDI import, phrase drop and transpose | `test:midi-import`, `test:parts:phrases` | `test:phrase-import:browser` |
| Score geometry, notes, piano roll and drum editing | `test:score`, `test:selection` | `test:score:browser`, `test:phrase-import:browser` |
| Rhythm controls and pattern sequencer | `test:parts:scopes`, `test:parts:phrases` | `test:rhythm:browser`, `test:patterns:browser` |
| Theme, appearance, quick settings and responsive workspace | `test:appearance`, `test:parts:preferences` | `test:preferences:browser`, `test:rhythm:browser`, `test:overview:browser` |
| Keyboard shortcuts | `test:keyboard` | `test:contextual:browser`, `test:export:browser` |
| Playback, scheduling and TinySynth adapter/patch | `test:playback` | `test:synth:browser`, `test:export:browser` |
| MIDI routing/output and export | `test:midi`, `test:export` | `test:midi:browser`, `test:export:browser` |
| Test fixtures and browser infrastructure | `test:parts:support`, `test:harness`, `test:unit` | `test:browser` |

Score geometry, hit testing and gesture-preview helpers have direct checks alongside the component interaction cases in `score-canvas.test.cjs`. See [score ownership](../src/app/features/score/README.md) for the rendering/interaction boundary and draft-history checks.

`test:unit` discovers top-level `*.test.cjs` files, excluding browser suites and `parts-generation.test.cjs`: the latter already runs through `part-settings.test.cjs`. Parts stay in one process with the transpilation cache intact. Files run sequentially to limit compiler/browser contention. Keep new standalone suites at this level (or add them to an existing aggregate), and add focused commands as needed. `browser-harness.test.cjs` verifies aggregate coverage without running the suites twice.

## Shared browser infrastructure

`support/browser-harness.cjs` owns executable discovery, production-build preflight, loopback HTTP serving, JSON result parsing, Chromium launch, debugging-protocol connection/command timeouts, process watchdogs and cleanup. All twelve browser suites use it. Each launch uses a separate profile under `.angular/`; cleanup closes sockets/server connections, waits for Chromium to exit, verifies the profile path and removes that profile. Spawn errors, early exits and watchdog timeouts remain visible, with recent stderr on result-driven failures. `test:harness` covers serving/results, invalid JSON, asset path boundaries, missing-build diagnostics, watchdog failure handling and failed-launch cleanup.

Suites retain their HTML/fixtures, injected workflows, browser flags, viewport sizes, assertions and screenshots. Result-posting suites choose their existing 25–45 second watchdogs; protocol commands have a 20 second deadline. Page startup and DOM polling remain suite-specific. Layout checks continue to wait for real animation frames and ResizeObserver work; do not replace these waits with virtual time or shorten the stability sequence.

The UI preferences suite configures downloads before Chromium starts, saving exports inside each temporary profile's `downloads/` folder instead of the user's Downloads. It checks the saved JSON, and profile cleanup removes the exported files after each viewport case.

Component fixtures and source-reading browser suites follow the [feature ownership map](../src/app/features/README.md). Domain components live under `src/app/features/`; workspace selection state lives in `src/app/core/ui/workspace-ui.service.ts`. Update fixture module paths and dependency stand-ins together when moving an owner. Templates and styles remain alongside their components; the old `shared/` paths have no forwarding modules.

Production pages served by the browser harness include `support/browser-select.cjs`. Its `selectPopup`, `selectOptions` and `changeControl` helpers exercise ARIA combobox triggers and rendered listbox options; they do not fake native select events. Use them when checking dropdown choices, while trusted protocol mouse/keyboard input verifies popup timing and viewport positioning.

Browser aggregates first check Chromium startup, Canvas 2D and WebGL2 in a temporary profile. A blocked browser fails once before launching the suites. Debugging-protocol failures include recent browser stderr, including GPU startup errors that otherwise appear only as `Target crashed`.

On this Windows host, both Chrome and Edge fail under the agent's restricted account with Chromium child-process exit code `0xC0000022` (access denied). Moving GPU work into the browser process with `--in-process-gpu` also leaves the page crashing, so the harness preserves Chromium's default process isolation. This is a host sandbox compatibility issue; changing fonts, browser choice or individual suite flags does not resolve it. Use the host's explicit approval flow for `pnpm run test:browser` (or a focused browser command), retaining a narrowly scoped command approval when available. The same tests run from a regular user terminal without administrator rights. Do not add `--no-sandbox` or `--disable-gpu-sandbox` to individual suites.

`CreateProcessWithLogonW failed: 1909` happens before pnpm or Chromium starts: the Windows sandbox account is locked. Workspace flags cannot repair that host account. Restart or repair the agent's Windows sandbox through its host, then retry a simple sandboxed shell command before retrying builds or tests. A GPU startup crash and a locked account are separate failures. Keep permission escalation explicit while the host is unavailable.

## Overview layout matrix

`pnpm run test:overview:browser` runs all 21 layout scenarios at normal page zoom and display scale. Eight scenarios also run at every combination of page zoom (0.8, 1, 1.25, 1.5) and display scale (1, 1.25, 1.5). These cover fractional-pixel rounding, fitted and overflowing timelines, fractional timeline zoom, dense track buttons, remaining score space, and gap sections.

The default has 109 cases instead of crossing all 21 layouts with all 12 scale combinations (252 cases). Both modes retain 12 rounds of alternating track/role edits per case, two real animation frames between rounds, and the final four-sample stability assertion. Return-to-fit scenarios remain in the baseline sequence. Per-display-scale diagnostics report case counts and elapsed time.

For exhaustive checks after changing layout or rounding behavior, run `pnpm run test:overview:browser:full` (252 cases). The existing `OVERVIEW_TEST_FULL_MATRIX=1` override still works. In PowerShell:

```powershell
$env:OVERVIEW_TEST_FULL_MATRIX = '1'
try { pnpm run test:overview:browser }
finally { Remove-Item Env:OVERVIEW_TEST_FULL_MATRIX }
```

Measured on 2026-10-16: the focused matrix passed in 44.9 seconds; the preceding full-matrix run took 102.6 seconds. Real animation-frame waits dominate this suite, so reducing only the failure timeout would not improve successful runs.

Historical Melody Java oracle cases with nonzero Doubled rhythm or Lead chords probabilities, enabled pitch exceptions, or Arpy surprises are excluded from current parity checks because those controls and consumers were removed. Removed part fields and the Single note exceptions and Arpy surprises global flags are stripped from the remaining oracle inputs; fixture files retain the original Java evidence.

The 256 Arpeggio phrase fixtures leave Java's removed Correct by melody feature disabled and omit its field. They include melody-following rhythms with seeded Pause values, including 100% silence after inversion. The saved-configuration oracle still strips the obsolete correction field before import.

Historical Arpeggio contour pitches use C-major intervals and snapping even in other keys and modes. Those cases retain timing, velocity and note-count checks; dedicated contour checks cover diatonic steps in every supported key and mode, chord-relative roots, negative steps, octave crossings and split-note exceptions.

## Project behavior tests and fixture support

`pnpm run test:parts` also runs `project-codec.test.cjs`, which exercises project decoding, validation, diagnostics, defaults, and arrangement candidates without Angular or browser storage. Module ownership is documented in [the project-state guide](../src/app/core/project/README.md).

`scripts/part-settings.test.cjs` is the aggregate entry point. It requires the behavior suites under `scripts/parts/` in one Node test process, preserving the shared transpilation cache. Keep these tests sequential: fixtures install global session/local-storage stand-ins. Do not replace the aggregate command with a file glob or parallel suite processes; those processes each pay the compilation cost.

| Behavior | Focused command | Suite under `scripts/` |
| --- | --- | --- |
| Project imports, validation and atomic rejection | `pnpm run test:parts:imports` | `parts/imports-validation.cjs`, `project-codec.test.cjs` |
| History, gestures, audition and arrangement commands | `pnpm run test:parts:history` | `parts/history.cjs` |
| Scoped settings, inheritance, copy/freeze and editors | `pnpm run test:parts:scopes` | `parts/scoped-settings.cjs` |
| Compose/reroll policies and Java generation parity | `pnpm run test:parts:generation` | `parts-generation.test.cjs` loads `parts/generation-policy.cjs` and `parts/generation-parity.cjs` |
| Manual phrase ownership, drafts and local Apply | `pnpm run test:parts:phrases` | `parts/manual-phrases.cjs` |
| UI preferences, appearance and control placement | `pnpm run test:parts:preferences` | `parts/preferences.cjs` |
| Fixture isolation, cache reuse and diagnostic handling | `pnpm run test:parts:support` | `parts/test-support.cjs` |

For a narrower selection, use Node's test-name filter, for example `node --test --test-name-pattern='catalogue' scripts/parts-generation.test.cjs`. Cross-cutting tests stay together under their primary behavior so their history, persistence, MIDI and ownership assertions remain intact.

`support/project-fixture.cjs` caches only transpiled TypeScript text within the process. Each fixture evaluates fresh modules, creates fresh services and signals, and owns separate project/history/storage state. Its Angular stand-ins and manually flushed effects retain the existing harness behavior; it does not replace browser coverage. The cache does not share evaluated module exports, skip tests, reduce Java parity matrices, or persist across processes. `support/project-import-assertions.cjs` captures only an expected rejection operation, asserts a single import warning with nonempty property-path diagnostics, and forwards unexpected diagnostics. The imports suite retains detailed field-path, malformed-JSON, session-load and successful-load warning assertions.

Measured on 2026-10-19: before splitting, all 249 part tests plus 5 codec tests passed in 14.57 seconds. The final split aggregate passed all 257 tests in 12.04 seconds (an earlier split run took 14.70 seconds): the same 254 checks plus three fixture/diagnostic support tests. All six focused commands also pass independently. Local reports are `.angular/parts-cleanup-before.tap`, `.angular/parts-cleanup-after.tap`, and `.angular/parts-cleanup-<behavior>.tap` when retained. Application code is unchanged, so this cleanup does not require rebuilding browser assets.
