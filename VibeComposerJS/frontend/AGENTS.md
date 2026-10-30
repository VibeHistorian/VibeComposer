# Frontend entry map

Product contracts are authoritative in [translation rules](../TRANSLATION_RULES.md), [workspace contracts](../DAW_REDESIGN_PLAN.md) and the [contextual controls backlog](../CONTEXTUAL_CONTROLS_PLAN.md). Use the [control placement map](../CONTROL_PLACEMENT_MAP.md) for Java lookups. This file is a navigation aid; keep detailed rules with their existing owners.

## Find the owner

Paths below are relative to `src/app/`. See the [feature map](src/app/features/README.md), [shared UI guide](src/app/shared/README.md) and [project/domain ownership guide](src/app/core/project/README.md) for detailed boundaries. Import domain contracts directly from their owner.

| Area | Start here |
| --- | --- |
| Settings and scopes | `features/part-settings/`, `core/music/part-settings.ts`, `core/project/part-settings-scope.ts`; project commands in `core/project/project.service.ts`; app preferences in `core/ui/ui-preferences.service.ts`. |
| Generation | `core/music/phrase.ts` and role generators; `track-generation.ts` and `instrument-generation.ts` choose stored settings; `project-harmony.ts` and `section-context.ts` resolve shared dependencies. |
| Score interaction and manual editing | [Score ownership](src/app/features/score/README.md): `features/score/score-interaction.ts`, geometry/hit-testing helpers and canvas; `features/edit/edit-workspace.component.ts` owns phrase drafts and Apply/Local Apply/Cancel. |
| Persistence and history | [Project state](src/app/core/project/README.md): `project.service.ts` owns signals and commits; `project-codec.ts`, `project-storage.ts`, `project-json.ts` and `project-history.ts` own decoding, storage, export formatting and history helpers. |
| Playback | `core/audio/audio-playback.service.ts`, `tiny-synth-engine.ts`; transport UI in `features/transport/`. |
| MIDI and audio export | `core/music/midi-export.ts`, `core/audio/midi-output.service.ts`, `audio-export.service.ts` and `audio-export.ts`; MIDI import starts at `core/ui/midi-file-import.ts` and `features/midi-import/`. |

## Preserve the contracts

- Preserve exact Java long seeds (decimal strings in saved projects, `bigint` for computation), seed derivation and random draw order; use `core/music/java-random.ts`.
- Use historical project commands and commit each gesture once. `ProjectService` remains the history/persistence commit boundary; previews and cancelled drafts do not publish project edits.
- Resolve musical settings as track base → section role patch → section track patch. Keep presence, generation policy and instrument identity separate from musical patches.
- Preserve manual-phrase ownership: global Apply belongs to the independent track; Local Apply belongs to the section and track. Respect manual replacements and explicit generated-track selections when resolving phrases.
- Keep generated output consistent across score, dependent parts, playback and MIDI/audio export through the shared phrase/harmony pipeline. Keep generation framework-independent and playback scheduling outside Angular rendering.

## Install and verify

From `frontend/`, use the pnpm version pinned in [package.json](package.json) and `pnpm install --frozen-lockfile`; [setup](../README.md#setup-and-builds) explains Node requirements, Wails and mixed-install recovery. The authoritative lockfile is `pnpm-lock.yaml`; preserve the [TinySynth patch contract](patches/README.md).

Choose checks from the [change-area-to-checks table](scripts/README.md#change-area-to-checks). `pnpm test` runs functional checks. After application changes, run `pnpm run build` before the relevant production browser suites; use both test aggregates for changes across owners. Supported viewport widths start at 700 CSS pixels.

## Windows shell and sandbox recovery

PowerShell commands can fail before the shell starts with `CreateProcessWithLogonW failed: 1909` when the Windows sandbox account is locked. This startup failure does not establish that `rg`, `Get-Content` or another workspace command violates filesystem permissions.

For safe commands expected to be allowed by workspace access, retry normal sandbox execution before requesting escalation. Recheck normal sandbox execution on subsequent safe reads rather than carrying an earlier escalation across the session; the account can recover. Avoid repeated tight retries. If startup failures persist, report the exact error and follow the host's approval policy. Workspace access does not itself authorize execution outside the sandbox; actual network restrictions and Chromium sandbox crashes may require separate handling.

Production fonts are bundled locally, so `pnpm run build` needs no Google Fonts connection after dependencies are installed. Browser aggregates preflight Chromium, Canvas 2D and WebGL2 before running suites. On this host, sandboxed Chrome and Edge child processes fail with `0xC0000022` (access denied); `--in-process-gpu` does not fix the page crash. Use the [protected browser launcher](../../tools/browser-launcher/README.md): only `C:\Program Files\VibeComposerBrowserLauncher\BrowserLauncher.exe serve` runs outside the agent sandbox. Set `VIBE_BROWSER_BROKER=1` for browser test commands and keep Node/pnpm/test JavaScript inside the sandbox; a broker failure must not trigger outside-sandbox execution of a whole suite. Installation or changes to the installed executable require administrator elevation. Preserve Chromium sandboxing. See the [browser infrastructure notes](scripts/README.md#shared-browser-infrastructure).
