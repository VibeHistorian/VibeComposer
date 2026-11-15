// Explicit maintenance command: overwrites the bundled table, including manual edits.
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { findBrowser, createBrowserServer, listen, launchBrowser, closeBrowser, watchBrowser } = require('./support/browser-harness.cjs');

(async () => {
  const browser = findBrowser();
  if (!browser) throw Error('Set OVERVIEW_TEST_BROWSER or start the protected browser broker.');
  const esbuild = require(require.resolve('esbuild', { paths: [path.dirname(require.resolve('@angular/build/package.json'))] }));
  const bundle = esbuild.buildSync({ stdin: {
    contents: `export { measureInstrumentLoudness } from './src/app/core/audio/instrument-calibration';
      export { GM_INSTRUMENTS } from './src/app/core/music/gm-instruments';
      export { DRUM_INSTRUMENTS } from './src/app/core/music/drum-instruments';`,
    resolveDir: path.resolve(__dirname, '..'), loader: 'ts',
  }, bundle: true, write: false, format: 'iife', globalName: 'Precompute', platform: 'browser' }).outputFiles[0].text;
  const html = '<!doctype html><script>' + bundle + '</script><script>' + `
    (async () => { try {
      // TinySynth uses random noise buffers; use fixed entropy only for this tool.
      let state = 0x51f15e;
      Math.random = () => { state ^= state << 13; state ^= state >>> 17; state ^= state << 5; return (state >>> 0) / 4294967296; };
      const started = performance.now();
      const profile = await Precompute.measureInstrumentLoudness();
      await fetch('/result', { method: 'POST', body: JSON.stringify({ profile,
        programs: Precompute.GM_INSTRUMENTS, drums: Precompute.DRUM_INSTRUMENTS,
        seconds: (performance.now() - started) / 1000 }) });
    } catch (error) { await fetch('/result', { method: 'POST', body: JSON.stringify({ error: String(error.stack || error) }) }); } })();
    </script>`;
  let resolve, reject;
  const result = new Promise((done, fail) => { resolve = done; reject = fail; });
  result.catch(() => {});
  const server = createBrowserServer({ html, resolve, reject });
  await listen(server);
  const child = launchBrowser(browser, ['--headless', '--disable-gpu', '--no-first-run',
    '--autoplay-policy=no-user-gesture-required', 'http://127.0.0.1:' + server.address().port], { stdio: ['ignore', 'ignore', 'pipe'] });
  const watchdog = watchBrowser(child, reject, 45000);
  try {
    const output = await result;
    if (output.error) throw Error(output.error);
    const { melodic, drums } = output.profile;
    for (const values of [melodic, drums]) {
      assert.equal(values.length, 128);
      assert.ok(values.every(value => Number.isFinite(value) && value > 0));
    }
    const rows = (values, names, keys) => keys.map(key =>
      `  ${key}: ${(20 * Math.log10(values[key])).toFixed(6)}, // ${names.get(key) ?? 'MIDI note ' + key}`);
    const text = [
      "import type { LoudnessProfile } from './normalized-instrument-engine';", '',
      '/** TinySynth 1.1.3, quality 1. Editable offsets in dB; programs are ZERO-BASED.',
      ' * Positive values boost, negative values attenuate. Drums use MIDI note numbers.',
      ' * Measured with @audio/loudness-lufs: -24 LUFS, +/-12 dB, -3 dBFS sample-peak ceiling.',
      ' * Probe details live in instrument-calibration.ts. Noise RNG seed: 0x51f15e.',
      ' * Regenerate: pnpm run calibrate:tinysynth (OVERWRITES manual edits).',
      ' * Runtime always uses this file; old browser calibration caches are ignored. */',
      'export const TINY_SYNTH_MELODIC_OFFSETS_DB: Readonly<Record<number, number>> = {',
      ...rows(melodic, new Map(output.programs.map(p => [p.program, p.name])), Array.from({ length: 128 }, (_, i) => i)),
      '};', '', 'export const TINY_SYNTH_DRUM_OFFSETS_DB: Readonly<Record<number, number>> = {',
      ...rows(drums, new Map(output.drums.map(d => [d.midi, d.name])), Array.from({ length: 48 }, (_, i) => i + 35)),
      '};', '',
      'const gains = (offsets: Readonly<Record<number, number>>) => Array.from({ length: 128 },',
      '  (_, key) => 10 ** ((offsets[key] ?? 0) / 20));', '',
      'export const TINY_SYNTH_LOUDNESS_PROFILE: LoudnessProfile = {',
      '  melodic: gains(TINY_SYNTH_MELODIC_OFFSETS_DB),',
      '  drums: gains(TINY_SYNTH_DRUM_OFFSETS_DB),', '};', '',
    ].join('\n');
    const target = path.resolve(__dirname, '../src/app/core/audio/tiny-synth-loudness.ts');
    fs.writeFileSync(target, text);
    console.log(`Wrote ${target} (${output.seconds.toFixed(2)}s measurement).`);
  } finally { await closeBrowser({ child, server, watchdog }); }
})().catch(error => { console.error(error); process.exitCode = 1; });
