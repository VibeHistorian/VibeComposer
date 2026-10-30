TinySynth 1.1.3 defines `internalcontext` but its constructor does not read that
option. The patch honors it so the adapter can bind an OfflineAudioContext without
opening a realtime audio device. It also exposes the existing sequencer timer as
`timerId` so disposing an offline engine clears it. The MIDI send path only
auto-resumes realtime contexts: offline contexts start via `startRendering()`.
This prevents rejected resume promises from the two initialization MIDI messages.
Synthesis is unchanged.

The `smplr@1.1.0` patch adds per-region pan and scale tuning, volume-envelope
delay/attack/hold/decay/sustain, and release from the envelope's computed level.
It also filters undefined preset defaults so they cannot overwrite numeric
defaults and produce NaN pitch parameters. Both ESM/CommonJS builds and both
declaration files are patched. SF2 parsing and bank/program resolution stay in
`src/app/core/audio/sf2-resolver.ts`; the package patch is only renderer support.
See `src/app/core/audio/README.md` for supported SF2 behavior and limitations.

After changing this patch, run `pnpm patch-commit`, review the workspace/lockfile
hash, and verify `pnpm install --frozen-lockfile`. Check `scripts/sf2-resolver.test.cjs`,
`scripts/tiny-synth-browser.test.cjs` and the production export suite after building.

The authoritative installer is pnpm 12.9.1. Run `pnpm install --frozen-lockfile`
from `frontend/`; `pnpm-workspace.yaml` registers this patch and
`pnpm-lock.yaml` records its content hash. Wails uses the same install command.
Do not apply the patch by hand or install with npm: npm does not apply pnpm patches.

After changing the patch, regenerate the lockfile with pnpm and review both files.
Verify a fresh installation with `pnpm run test:playback` and
`pnpm run test:synth:browser`. The Chromium test binds an OfflineAudioContext,
rejects any realtime AudioContext construction and unhandled resume rejection,
checks GM/drum rendering and voice cancellation, and disposes the engine.
Run `pnpm run build` followed by `pnpm run test:export:browser` to cover offline
WAV/MP3/Opus export through the production application as well.
