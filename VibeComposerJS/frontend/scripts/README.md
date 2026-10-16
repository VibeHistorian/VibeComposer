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

## Part-settings fixture compilation

`npm run test:parts` caches transpiled TypeScript text within the test process. Each fixture still evaluates fresh modules, creates fresh services and signals, and owns separate project/history/storage state. The cache does not share evaluated module exports, skip tests, reduce Java parity matrices, or persist across test processes.

Measured on 2026-10-16: caching reduced the 216-test suite from 107.9 to 5.3 seconds, with identical results at that checkpoint (213 passing and three existing failures). Those failures were subsequently fixed by testing mix synchronization through the preference service and its effect, and keeping default theme-color key order consistent with imported preferences. All 216 tests now pass. Raw timing reports from the original comparison are under `.angular/parts-timing-before.tap` and `.angular/parts-timing-after.tap` when retained locally.
