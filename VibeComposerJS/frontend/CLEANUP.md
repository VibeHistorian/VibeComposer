# Gradual frontend cleanup

Improve agent navigation, targeted reading, edit safety, and verification speed through small, independently reviewable changes. Preserve the existing architecture where it works; a broad rewrite is unnecessary.

Follow [the translation rules](../TRANSLATION_RULES.md) and [workspace contracts](../DAW_REDESIGN_PLAN.md). This checklist is maintenance work, not a replacement for the [translation backlog](../CONTEXTUAL_CONTROLS_PLAN.md).

Measurements below are a snapshot from 2026-10-19, not file-size targets.

## Six areas of improvement

### 1. Separate responsibilities in ProjectService

`src/app/core/project/project.service.ts` has 1,996 lines and roughly 124K characters. It combines validation, persistence, history, generation orchestration, and editing commands.

- [x] Extract pure project decoding and validation into focused modules, retaining value/reference validation without introducing schema-version gates or compatibility migrations.
- [x] Extract coherent command groups as their areas are changed, using explicit inputs and results. Ten arrangement-edit commands now build candidates outside the service; continue this pattern when other command groups need changes.
- [x] Keep the service as the signal owner and the single history/persistence commit boundary. Preserve atomic gestures and failed-operation behavior.

Start with validation: it provides a clear extraction boundary without changing UI ownership or command dispatch.

Delivered 2026-10-19: extracted codecs, diagnostics, default construction, section chance data, and arrangement commands. Section preferences and presence randomness are explicit inputs. `ProjectService` decreased from 1,996 to approximately 1,300 lines. Module ownership and verification entry points are documented in [the project-state guide](src/app/core/project/README.md).

Validation: 334 tests passed across part settings, the new codec checks, Java XML imports, section selection, arrangement overview, and contextual generation. The production build passed with the existing Pixi/xmldom CommonJS warning. Both contextual-generation browser cases and the 780px contextual workflow passed. The 1440px contextual workflow failed its real chord-drag assertion at `scripts/contextual-workflows-browser.test.cjs:562` (`5 !== 1`); the same failure reproduced against an isolated build of the pre-cleanup source. That browser failure remains outside this cleanup.

### 2. Split the broad part-settings test suite

`scripts/part-settings.test.cjs` has 6,483 lines and roughly 441K characters, covering substantially more than part settings. Its current run executes 249 tests.

- [x] Extract the fixture loader into shared test support.
- [x] Divide tests by behavior: imports/validation, history, scoped settings, generation, manual phrases, and preferences.
- [x] Preserve fresh module/service/storage state for each fixture and retain transpilation caching. Check aggregate results and runtime after splitting; separate Node processes do not share the current in-process cache.
- [x] Capture and assert expected invalid-import diagnostics so successful negative tests do not flood command output. Preserve visibility of unexpected diagnostics.

Baseline: `npm run test:parts` passed all 249 tests in approximately 14 seconds on 2026-10-19. This is evidence for comparison, not a permanent performance requirement.

Delivered 2026-10-19: `part-settings.test.cjs` now loads behavior suites in one process; generation policies and Java parity have separate files with a shared entry point. Fixtures and JSON comparison support live in `scripts/support/project-fixture.cjs`. Negative-import assertions capture and validate expected property diagnostics while forwarding unexpected warnings; detailed diagnostic-path tests remain intact. Focused commands and suite ownership are documented in [the test guide](scripts/README.md).

Validation: the baseline passed 254 tests (249 part tests and 5 codec tests) in 14.57 seconds. The final split aggregate passed all 257 tests in 12.04 seconds (an earlier split run took 14.70 seconds), retaining every existing test and adding three checks for fixture isolation/cache reuse and diagnostic handling. All six focused behavior commands passed independently. No application code changed.

### 3. Align feature ownership with folder structure

`shared/` contains 21 component classes, including the workspace, score, arrangement, and musical editors; `features/` contains three. This does not match the ownership guidance in the [feature README](src/app/features/README.md) and [shared UI README](src/app/shared/README.md).

- [ ] Move domain UI into cohesive feature folders incrementally when those areas are changed.
- [ ] Reserve `shared/` for controls and presentation helpers with concrete reuse across features.
- [ ] Update imports, test fixture paths, and ownership documentation with each move.

Feature folders do not imply additional routes: preserve the single persistent workspace. Avoid a large move-only reorganization as a prerequisite for useful work.

### 4. Extract score geometry and interaction boundaries

`src/app/shared/score-canvas.component.ts` has 1,086 lines combining viewport calculations, hit testing, gestures, drawing, and Pixi lifecycle management.

- [ ] Extract viewport geometry and coordinate transformations into focused, testable helpers.
- [ ] Extract hit testing using explicit geometry inputs.
- [ ] Define an explicit interface between editing gestures and rendering as interaction code is changed.
- [ ] Keep rendering lifecycle management cohesive and preserve viewport, selection, gesture cancellation, and one-commit-per-gesture behavior.

Prefer these concrete boundaries over splitting methods merely to meet a line limit.

### 5. Make tests discoverable and reuse browser infrastructure

Five test files currently have no corresponding package script: `appearance.test.cjs`, `keyboard-shortcuts.test.cjs`, `manual-phrase-mode-browser.test.cjs`, `midi-import.test.cjs`, and `phrase-import-browser.test.cjs`. Twelve browser suites contain HTTP-server setup.

- [ ] Provide discoverable unit and browser commands, including currently unmapped suites, while retaining focused commands.
- [ ] Add a change-area-to-checks table to `scripts/README.md` so targeted edits have an obvious verification path.
- [ ] Extract common browser discovery, server/process lifecycle, timeout, and result handling where suites share the same needs.
- [ ] Make production-build prerequisites explicit and preserve real animation-frame waits where layout correctness requires them.

Retain the supported viewport range of at least 700 CSS pixels and the distinction between focused and exhaustive browser matrices.

### 6. Make installation reproducible

The project README and Wails configuration use `npm install`, while the TinySynth patch is configured through `pnpm-workspace.yaml` alongside `pnpm-lock.yaml`.

- [ ] Choose and document one authoritative package manager and locked installation path.
- [ ] Align the README, package metadata, lockfile, and Wails install/build commands with that choice.
- [ ] Ensure a clean installation applies the TinySynth patch through the chosen mechanism.
- [ ] Verify the patched behavior with the relevant audio/browser checks; see `patches/README.md` for the patch contract.

## Documentation addition

- [ ] Add a short frontend `AGENTS.md` entry map linking existing contracts rather than duplicating them.

Include:

- Ownership paths for settings, generation, score interaction, persistence, playback, and MIDI/export.
- A pointer to the change-area-to-checks table.
- The authoritative package manager and installation command.
- Critical invariants: exact seeds and random draw order, one historical commit per gesture, track base → section role patch → section track patch precedence, manual-phrase ownership, and shared generated output across consumers.

Keep the entry map small and current. Product rules remain authoritative in the existing translation/workspace documents.

## Secondary improvements

### Clarify domain type ownership

`src/app/core/project/project.model.ts` has 75 importing source files and combines musical types, project structure, and defaults. Its location alone does not violate framework independence.

- [ ] Gradually separate musical types, project composition types, and default values when changes justify a clearer dependency boundary.
- [ ] Keep dependencies explicit and avoid introducing circular runtime imports or unnecessary forwarding modules.

### Optimize readability rather than line count

Some CSS and test code packs substantial behavior onto long lines.

- [ ] Expand dense code into predictable formatting when touching it, keeping unrelated formatting churn out of functional changes.
- [ ] Prefer coherent modules and easy-to-review patches over compressed source or arbitrary maximum file lengths.
- [ ] Preserve separate component templates/styles, semantic theme tokens, and CSS section comments.

## Working approach

Take one coherent item at a time, preserve behavior, update affected navigation references, and run the relevant functional/browser checks and production build for application changes. Preserve the framework-independent music library, existing Java parity coverage, and playback scheduling outside Angular rendering. Record completion here as cleanup is delivered.
