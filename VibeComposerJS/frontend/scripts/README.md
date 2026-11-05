# Frontend tests

Install from `frontend/` with `pnpm install --frozen-lockfile` using the version pinned in `package.json`; see [setup](../../README.md#setup-and-builds) and the [TinySynth patch contract](../patches/README.md).

Run tests from `frontend/` using the `test:*` scripts in `package.json`. `pnpm test` (or `pnpm run test:unit`) runs all functional suites without Chromium. `pnpm run test:browser` runs all browser suites sequentially, including the focused overview matrix. Focused commands remain available below. Append `--list` to either aggregate command to list its suites without running them; other arguments are forwarded to Node's test runner. With pnpm, pass script arguments directly, without npm's extra `--` separator.

Production browser suites use `dist/browser`: run `pnpm run build` first and rebuild after changing application code. The browser aggregate checks for the build and a browser before starting; focused production suites also report a missing build explicitly. Existing assets can be reused for test-only changes. The overview layout and TinySynth fixture suites read source directly and do not require a production build. Browser discovery accepts `OVERVIEW_TEST_BROWSER` as an explicit Chromium executable path, then checks common Chrome/Edge/Chromium locations on Windows, Linux and macOS. Focused suites skip when no browser is installed; the aggregate fails with a setup instruction. Supported viewport widths start at 700 CSS pixels.

## Test scope and runtime

During development, start with the affected functional suite and add the browser suite that exercises the changed interaction. The table below is a selection guide, not a checklist to run after every edit. Use the normal aggregates for broader validation when the change crosses owners or before finishing that work; do not repeat them after each small adjustment once relevant checks pass. Documentation-only edits do not require a build or application test run.

Approximate wall times below come from local Windows runs on 2026-11-05. They exclude dependency installation and the production build. Hardware, cold caches and failure paths change these times; these are planning estimates, not enforced performance budgets. Some aggregate measurements include known failing cases.

| Check | Approximate elapsed time | When to use |
| --- | --- | --- |
| `pnpm run test:harness` | Under 1 second | Browser harness, serving, cleanup or audio-isolation helpers. |
| `pnpm run test:playback` | 1–2 seconds | Playback logic and loop-time regression checks; simulated clock advances do not wait for minutes. |
| `pnpm run test:parts:<area>` | Usually a few seconds; depends on area | Focused project/settings behavior; use the commands below. |
| `node --test scripts/playback-recovery-browser.test.cjs` | About 8 seconds total | Realtime scheduling, hidden playhead behavior and fractional loops. The three-second clock window is only one phase of the case. |
| `pnpm run test:synth:browser` | About 3 seconds | TinySynth/SF2 rendering and PCM assertions. Offline audio duration is not elapsed test duration. |
| `pnpm run test:export:browser` | About 7–10 seconds | Production export and audio-rendering integration. |
| `pnpm test` / `pnpm run test:unit` | About 45–60 seconds | All functional suites, without Chromium. |
| `pnpm run test:overview:browser` | About 45 seconds | Scrollbar, timeline geometry, zoom or display-scale changes; even the default 109-case matrix is comparatively expensive. |
| `pnpm run test:browser` | About 3–4 minutes | All browser suites, sequentially, including the default overview matrix. Broad validation, not the default iteration loop for one feature. |
| `pnpm run test:overview:browser:full` | About 105 seconds | **Opt-in:** exhaustive 252-case layout coverage after changes to rounding, overflow or scale behavior. |
| Playback browser case with `PLAYBACK_CLOCK_TEST_SECONDS=150` | About 155–160 seconds | **Opt-in:** prolonged playback/loop-boundary investigation or scheduler changes requiring a soak run. Not for unrelated features. |

Leave `PLAYBACK_CLOCK_TEST_SECONDS` and `OVERVIEW_TEST_FULL_MATRIX` unset during routine work. Neither slow variant is enabled by the default aggregate commands, but both environment overrides are inherited by `test:browser`: a leftover 150-second playback override adds roughly 147 seconds to that aggregate, and the full overview override replaces its 45-second matrix with the longer one. Do not persist these settings in a shell profile, shared run configuration or normal CI command.

List suites without launching them with `pnpm run test:unit --list` or `pnpm run test:browser --list`. To narrow a functional suite further, use Node's filter before the file argument, for example `node --test --test-name-pattern='fractional' scripts/audio-playback.test.cjs`. Browser files can contain multiple viewport cases, so a whole file may take longer than any single case.

Keep the existing assertions, real animation-frame waits and failure timeouts intact; save development time by selecting the right suite. A 25–45 second watchdog is a per-case failure ceiling, not an expected duration for the whole suite. Java oracle regeneration is a separate, opt-in maintenance task; see [the fixture guide](java/README.md#regeneration).

## Change area to checks

Choose the functional commands and browser suites that match the changed behavior; a row lists candidates rather than requiring every listed command for every edit. Build first when application code changes and a production browser check needs the updated bundle. For changes across owners, use both normal aggregate commands for final validation; the opt-in soak and exhaustive modes remain separate.

| Change area | Functional commands (`pnpm run …`) | Browser commands (`pnpm run …`) |
| --- | --- | --- |
| Project decoding, persistence, Java XML import | `test:parts:imports`, `test:java-import` | `test:preferences:browser` |
| History, arrangement commands, selection | `test:parts:history`, `test:selection`, `test:overview` | `test:overview:browser`, `test:contextual:browser` |
| Arrangement variation inspection, drafts and indicators | `node --test scripts/arrangement-variations.test.cjs`, `test:overview` | `node --test scripts/arrangement-variations-browser.test.cjs`, `test:overview:browser`, `test:contextual:browser` |
| Settings inheritance, contextual generation, seeded parity | `test:parts:scopes`, `test:parts:generation`, `test:generation` | `test:generation:browser`, `test:contextual:browser` |
| Manual phrases, draft history, manual Mode preference | `test:parts:phrases`, `test:parts:preferences` | `test:manual:browser`, `test:score:browser` |
| MIDI import, phrase drop and transpose | `test:midi-import`, `test:parts:phrases` | `test:phrase-import:browser` |
| Score geometry, notes, piano roll and drum editing | `test:score`, `test:selection` | `test:score:browser`, `test:phrase-import:browser` |
| Rhythm controls and pattern sequencer | `test:parts:scopes`, `test:parts:phrases` | `test:rhythm:browser`, `test:patterns:browser` |
| Theme, appearance, quick settings and responsive workspace | `test:appearance`, `test:parts:preferences` | `test:preferences:browser`, `test:rhythm:browser`, `test:overview:browser`, `node --test scripts/workspace-layout-browser.test.cjs` |
| Global harmony popup alignment | `test:harness` | `node --test scripts/chordlet-popup-browser.test.cjs`, `test:contextual:browser` |
| Keyboard shortcuts | `test:keyboard` | `test:contextual:browser`, `test:export:browser` |
| Playback, scheduling, SF2 resolution and audio patches | `test:playback`, `node --test scripts/sf2-resolver.test.cjs` | `node --test scripts/playback-recovery-browser.test.cjs`, `test:synth:browser`, `test:export:browser` |
| MIDI routing/output and export | `test:midi`, `test:export` | `test:midi:browser`, `test:export:browser` |
| Test fixtures and browser infrastructure | `test:parts:support`, `test:harness`, `test:unit` | `test:browser` |

Score geometry, hit testing and gesture-preview helpers have direct checks alongside the component interaction cases in `score-canvas.test.cjs`. See [score ownership](../src/app/features/score/README.md) for the rendering/interaction boundary and draft-history checks.

The playback recovery browser suite checks the production worker clock with the window scheduler disabled and simulated hidden document visibility. Audio metering belongs only to the test fixture. The default three-second clock window uses a short fractional loop to cross the original failing boundary; the entire case takes about eight seconds. It also verifies that hidden scheduler ticks do not publish playhead updates and returning to the tab refreshes the playhead. This does not simulate OS window focus or Chrome's real hidden-tab policy.

For a playback-specific soak run, set `PLAYBACK_CLOCK_TEST_SECONDS=150` to check the original 89.033-beat loop. This waits for 150 real seconds, plus roughly five seconds of other phases/setup; allow about 2 minutes 35–40 seconds. Run only the playback file and clear the override afterward. In PowerShell, from `frontend/` (with the browser/broker already configured):

```powershell
try {
  $env:PLAYBACK_CLOCK_TEST_SECONDS = '150'
  node --test scripts/playback-recovery-browser.test.cjs
}
finally { Remove-Item Env:PLAYBACK_CLOCK_TEST_SECONDS -ErrorAction SilentlyContinue }
```

The protected broker caps each browser session at three minutes, including setup and the other recovery phases. Increasing this override can exceed that limit; it is not a way to request arbitrarily long broker runs. The limit applies per browser launch, not to the total duration of a multi-suite aggregate.

Prefer behavior over cosmetic snapshots: exercise settings changes, history, persistence, keyboard access, musical output and usable layout. Avoid pinning display labels, exact palette shades, decorative gaps, control variants or fixed pixel dimensions. Keep geometry checks that establish beat alignment, hit testing, viewport fit, clipping and layout stability; derive expected sizes from the viewport or musical coordinates where possible. Use option values when verifying available musical choices; labels may still locate controls during interactions.

`test:unit` discovers top-level `*.test.cjs` files, excluding browser suites and `parts-generation.test.cjs`: the latter already runs through `part-settings.test.cjs`. Parts stay in one process with the transpilation cache intact. Files run sequentially to limit compiler/browser contention. Keep new standalone suites at this level (or add them to an existing aggregate), and add focused commands as needed. `browser-harness.test.cjs` verifies aggregate coverage without running the suites twice.

## Shared browser infrastructure

`support/browser-harness.cjs` owns executable discovery, production-build preflight, loopback HTTP serving, JSON result parsing, Chromium launch, debugging-protocol connection/command timeouts, process watchdogs and cleanup. All browser suites use it. Direct launches use separate profiles under `.angular/`; broker launches use isolated profiles in the broker's runtime directory and retain only sandboxed artifacts under `.angular/`. Cleanup closes sockets/server connections and removes the corresponding profiles. Spawn errors, early exits and watchdog timeouts remain visible, with recent stderr on result-driven failures. `test:harness` covers serving/results, invalid JSON, asset path boundaries, missing-build diagnostics, watchdog failure handling, failed-launch cleanup and local cleanup after a lost broker connection.

All served test pages install `support/browser-audio.cjs` before fixture/application scripts. Realtime `AudioContext` instances use Chromium's [silent output device](https://developer.chrome.com/blog/audiocontext-setsinkid#render_audio_with_a_muted_audiocontext), so audio clocks and PCM assertions run without speaker output. If a browser rejects or ignores that option, connections to its realtime destination pass through a final zero-gain node; analyser inputs retain their original levels. `OfflineAudioContext`, exported audio, application mix settings, and the system output device are unchanged. This applies to both direct and broker browser runs; no installed launcher changes or virtual audio drivers are needed. The realtime recovery suite checks the native silent sink and still requires nonzero PCM.

Suites retain their HTML/fixtures, injected workflows, browser flags, viewport sizes, assertions and screenshots. Result-posting suites choose their existing 25–45 second watchdogs; protocol commands have a 20 second deadline. Page startup and DOM polling remain suite-specific. Layout checks continue to wait for real animation frames and ResizeObserver work; do not replace these waits with virtual time or shorten the stability sequence.

The UI preferences suite saves exports inside each temporary profile's `downloads/` folder instead of the user's Downloads. `readBrowserDownload` reads the file directly for regular launches or through the broker's restricted download endpoint. It checks the saved JSON, and profile cleanup removes the exported files after each viewport case.

Component fixtures and source-reading browser suites follow the [feature ownership map](../src/app/features/README.md). Domain components live under `src/app/features/`; workspace selection state lives in `src/app/core/ui/workspace-ui.service.ts`. Update fixture module paths and dependency stand-ins together when moving an owner. Templates and styles remain alongside their components; the old `shared/` paths have no forwarding modules.

Production pages served by the browser harness include `support/browser-select.cjs`. Its `selectPopup`, `selectOptions` and `changeControl` helpers exercise ARIA combobox triggers and rendered listbox options; they do not fake native select events. Use them when checking dropdown choices, while trusted protocol mouse/keyboard input verifies popup timing and viewport positioning.

Browser aggregates first check Chromium startup, Canvas 2D and WebGL2 in a temporary profile. A blocked browser fails once before launching the suites. Debugging-protocol failures include recent browser stderr, including GPU startup errors that otherwise appear only as `Target crashed`.

On this Windows host, both Chrome and Edge fail under the agent's restricted account with Chromium child-process exit code `0xC0000022` (access denied). Moving GPU work into the browser process with `--in-process-gpu` also leaves the page crashing, so the harness preserves Chromium's default process isolation. Use the [protected browser launcher](../../../tools/browser-launcher/README.md): start only the installed native broker outside the agent sandbox, then set `VIBE_BROWSER_BROKER=1` and run the normal test command inside the sandbox. The broker launches Chromium with validated options and exposes a restricted page protocol; Node and repository tests remain sandboxed. Do not retain outside-sandbox approvals for `node`, `pnpm`, `npm`, or the complete browser suites. Regular user terminals can still run the suites directly without this environment setting. Do not add `--no-sandbox` or `--disable-gpu-sandbox` to individual suites.

`CreateProcessWithLogonW failed: 1909` happens before pnpm or Chromium starts: the Windows sandbox account is locked. Workspace flags cannot repair that host account. Restart or repair the agent's Windows sandbox through its host, then retry a simple sandboxed shell command before retrying builds or tests. A GPU startup crash and a locked account are separate failures. Keep permission escalation explicit while the host is unavailable.

## Overview layout matrix

`pnpm run test:overview:browser` runs all 21 layout scenarios at normal page zoom and display scale. Eight scenarios also run at every combination of page zoom (0.8, 1, 1.25, 1.5) and display scale (1, 1.25, 1.5). These cover fractional-pixel rounding, fitted and overflowing timelines, fractional timeline zoom, dense track buttons, remaining score space, and gap sections.

The default has 109 cases instead of crossing all 21 layouts with all 12 scale combinations (252 cases). Both modes retain 12 rounds of alternating track/role edits per case, two real animation frames between rounds, and the final four-sample stability assertion. Return-to-fit scenarios remain in the baseline sequence. Per-display-scale diagnostics report case counts and elapsed time.

For exhaustive checks after changing layout or rounding behavior, explicitly run `pnpm run test:overview:browser:full` (252 cases, approximately 105 seconds). This command enables the full matrix only in its child process, so prefer it over setting an environment variable. It is not included by default in `test:browser` and is unnecessary for unrelated feature changes. The alternative `OVERVIEW_TEST_FULL_MATRIX=1` override still works; scope and clear it in PowerShell:

```powershell
try {
  $env:OVERVIEW_TEST_FULL_MATRIX = '1'
  pnpm run test:overview:browser
}
finally { Remove-Item Env:OVERVIEW_TEST_FULL_MATRIX -ErrorAction SilentlyContinue }
```

Measured on 2026-11-05 on Windows with headless Chrome 156.0.8078.79 through the protected browser broker: all 252 exhaustive cases passed in 103.1 seconds; the full `pnpm run test:overview:browser:full` command took 104.1 seconds including startup and preflight. Each display-scale batch contained 84 cases: scale 1 took 34.2 seconds, scale 1.25 took 34.4 seconds, and scale 1.5 took 34.5 seconds. These cases wait for real animation frames; lowering the failure timeout does not shorten successful runs.

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
