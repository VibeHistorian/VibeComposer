# Frontend tests

Run tests from `frontend/` using the `test:*` scripts in `package.json`. Production browser suites use `dist/browser`, so run `npm run build` after changing application code. Browser tests detect Chromium automatically; suites that support `OVERVIEW_TEST_BROWSER` also accept an explicit executable path. Supported viewport widths start at 700 CSS pixels.

## Overview layout matrix

`npm run test:overview:browser` runs all 21 layout scenarios at normal page zoom and display scale. Eight scenarios also run at every combination of page zoom (0.8, 1, 1.25, 1.5) and display scale (1, 1.25, 1.5). These cover fractional-pixel rounding, fitted and overflowing timelines, fractional timeline zoom, dense track buttons, remaining score space, and gap sections.

The default has 109 cases instead of crossing all 21 layouts with all 12 scale combinations (252 cases). Both modes retain 12 rounds of alternating track/role edits per case, two real animation frames between rounds, and the final four-sample stability assertion. Return-to-fit scenarios remain in the baseline sequence. Per-display-scale diagnostics report case counts and elapsed time.

For exhaustive checks after changing layout or rounding behavior, set `OVERVIEW_TEST_FULL_MATRIX=1`. In PowerShell:

```powershell
$env:OVERVIEW_TEST_FULL_MATRIX = '1'
try { npm run test:overview:browser }
finally { Remove-Item Env:OVERVIEW_TEST_FULL_MATRIX }
```

Measured on 2026-10-16: the focused matrix passed in 44.9 seconds; the preceding full-matrix run took 102.6 seconds. Real animation-frame waits dominate this suite, so reducing only the failure timeout would not improve successful runs.

## Project behavior tests and fixture support

`npm run test:parts` also runs `project-codec.test.cjs`, which exercises project decoding, validation, diagnostics, defaults, and arrangement candidates without Angular or browser storage. Module ownership is documented in [the project-state guide](../src/app/core/project/README.md).

`scripts/part-settings.test.cjs` is the aggregate entry point. It requires the behavior suites under `scripts/parts/` in one Node test process, preserving the shared transpilation cache. Keep these tests sequential: fixtures install global session/local-storage stand-ins. Do not replace the aggregate command with a file glob or parallel suite processes; those processes each pay the compilation cost.

| Behavior | Focused command | Suite under `scripts/` |
| --- | --- | --- |
| Project imports, validation and atomic rejection | `npm run test:parts:imports` | `parts/imports-validation.cjs`, `project-codec.test.cjs` |
| History, gestures, audition and arrangement commands | `npm run test:parts:history` | `parts/history.cjs` |
| Scoped settings, inheritance, copy/freeze and editors | `npm run test:parts:scopes` | `parts/scoped-settings.cjs` |
| Compose/reroll policies and Java generation parity | `npm run test:parts:generation` | `parts-generation.test.cjs` loads `parts/generation-policy.cjs` and `parts/generation-parity.cjs` |
| Manual phrase ownership, drafts and local Apply | `npm run test:parts:phrases` | `parts/manual-phrases.cjs` |
| UI preferences, appearance and control placement | `npm run test:parts:preferences` | `parts/preferences.cjs` |
| Fixture isolation, cache reuse and diagnostic handling | `npm run test:parts:support` | `parts/test-support.cjs` |

For a narrower selection, use Node's test-name filter, for example `node --test --test-name-pattern='catalogue' scripts/parts-generation.test.cjs`. Cross-cutting tests stay together under their primary behavior so their history, persistence, MIDI and ownership assertions remain intact.

`support/project-fixture.cjs` caches only transpiled TypeScript text within the process. Each fixture evaluates fresh modules, creates fresh services and signals, and owns separate project/history/storage state. Its Angular stand-ins and manually flushed effects retain the existing harness behavior; it does not replace browser coverage. The cache does not share evaluated module exports, skip tests, reduce Java parity matrices, or persist across processes. `support/project-import-assertions.cjs` captures only an expected rejection operation, asserts a single import warning with nonempty property-path diagnostics, and forwards unexpected diagnostics. The imports suite retains detailed field-path, malformed-JSON, session-load and successful-load warning assertions.

Measured on 2026-10-19: before splitting, all 249 part tests plus 5 codec tests passed in 14.57 seconds. The final split aggregate passed all 257 tests in 12.04 seconds (an earlier split run took 14.70 seconds): the same 254 checks plus three fixture/diagnostic support tests. All six focused commands also pass independently. Local reports are `.angular/parts-cleanup-before.tap`, `.angular/parts-cleanup-after.tap`, and `.angular/parts-cleanup-<behavior>.tap` when retained. Application code is unchanged, so this cleanup does not require rebuilding browser assets.
