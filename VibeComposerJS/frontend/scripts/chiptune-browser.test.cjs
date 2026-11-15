const assert = require('node:assert/strict');
const path = require('node:path');
const test = require('node:test');
const { findBrowser, createBrowserServer, listen, launchBrowser, closeBrowser, watchBrowser } = require('./support/browser-harness.cjs');

test('bundled chiptune renders every GM/drum slot, keeps stock engines isolated and normalizes without calibration', { skip: !findBrowser() }, async () => {
  const esbuild = require(require.resolve('esbuild', { paths: [path.dirname(require.resolve('@angular/build/package.json'))] }));
  const bundle = esbuild.buildSync({ stdin: { contents: `
    export { createInstrumentEngine } from './src/app/core/audio/create-instrument-engine';
    export { CHIPTUNE_PATCHES } from './src/app/core/audio/chiptune-patches';`,
    resolveDir: path.join(__dirname, '..'), loader: 'ts' }, bundle: true, write: false,
    format: 'iife', globalName: 'Chip', platform: 'browser' }).outputFiles[0].text;
  const html = '<!doctype html><script>' + bundle + '</script><script>(' + (async () => {
    try {
      const rate = 22050, peaks = [], voices = [];
      const context = new OfflineAudioContext(1, rate * 39, rate);
      // Normalization must use its bundled table, without constructing probe contexts.
      const NativeOffline = OfflineAudioContext;
      window.OfflineAudioContext = class { constructor() { throw Error('runtime calibration'); } };
      const engine = await Chip.createInstrumentEngine(context, 'chiptune', undefined, true);
      window.OfflineAudioContext = NativeOffline;
      const stock = await Chip.createInstrumentEngine(context, 'synth');
      const stockWave = stock.synth.program[80].p[0].w;
      for (let program = 0; program < 128; program++) {
        const handles = engine.schedule(context.destination, program, false, 60, 100, program * .2, .12);
        if (!handles.length || handles.length > 4) throw Error('operator count ' + program);
        voices.push(...handles);
      }
      for (let note = 35; note <= 82; note++) voices.push(...engine.schedule(context.destination, 0, true, note, 100, 27 + (note - 35) * .2, .1));
      const silent = context.createGain(); silent.gain.value = 0; silent.connect(context.destination);
      const long = engine.schedule(silent, 80, false, 60, 100, 0, 1)[0];
      const short = engine.schedule(silent, 80, false, 60, 100, .1, .1)[0];
      if (long.stopTime <= short.stopTime) throw Error('independent releases');
      const cancelled = engine.schedule(context.destination, 80, false, 60, 100, 38.5, .1);
      cancelled.forEach(v => v.stop(38));
      voices.push(long, short, ...cancelled);
      const data = (await context.startRendering()).getChannelData(0);
      for (let i = 0; i < 128; i++) peaks.push(data.subarray(Math.floor(i * .2 * rate), Math.floor((i * .2 + .16) * rate)).reduce((p, x) => Math.max(p, Math.abs(x)), 0));
      const drums = Array.from({ length: 48 }, (_, i) => data.subarray(Math.floor((27 + i * .2) * rate), Math.floor((27.16 + i * .2) * rate)).reduce((p, x) => Math.max(p, Math.abs(x)), 0));
      const tail = data.subarray(38 * rate).reduce((p, x) => Math.max(p, Math.abs(x)), 0);
      const isolated = stock.synth.program[80].p[0].w === stockWave;
      voices.forEach(v => v.dispose()); engine.dispose(); stock.dispose();
      // Isolated renders prevent earlier release tails from masking a silent slot.
      const isolatedPeaks = [];
      for (let slot = 0; slot < 176; slot++) {
        const ctx = new NativeOffline(1, rate / 2, rate);
        const instrument = await Chip.createInstrumentEngine(ctx, 'chiptune');
        try {
          const handles = instrument.schedule(ctx.destination, slot < 128 ? slot : 0,
            slot >= 128, slot < 128 ? 60 : slot - 128 + 35, 100, .01, .2);
          const pcm = (await ctx.startRendering()).getChannelData(0);
          isolatedPeaks.push(pcm.reduce((p, x) => Math.max(p, Math.abs(x)), 0));
          handles.forEach(v => v.dispose());
        } finally { instrument.dispose(); }
      }
      await fetch('/result', { method: 'POST', body: JSON.stringify({ peaks, drums, tail, isolated, isolatedPeaks }) });
    } catch (e) { await fetch('/result', { method: 'POST', body: JSON.stringify({ error: e.stack }) }); }
  }).toString() + ')();</script>';
  let resolve, reject;
  const result = new Promise((done, fail) => { resolve = done; reject = fail; }); result.catch(() => {});
  const server = createBrowserServer({ html, resolve, reject }); await listen(server);
  const child = launchBrowser(findBrowser(), ['--headless', '--disable-gpu', '--no-first-run',
    '--autoplay-policy=no-user-gesture-required', 'http://127.0.0.1:' + server.address().port], { stdio: ['ignore', 'ignore', 'pipe'] });
  const watchdog = watchBrowser(child, reject, 30000);
  try {
    const output = await result;
    assert.equal(output.error, undefined);
    output.peaks.forEach((p, i) => assert.ok(Number.isFinite(p) && p > .001, 'GM ' + i));
    output.drums.forEach((p, i) => assert.ok(Number.isFinite(p) && p > .001, 'drum ' + (i + 35)));
    output.isolatedPeaks.forEach((p, i) => assert.ok(Number.isFinite(p) && p > .001, 'isolated slot ' + i));
    assert.ok(output.tail < 1e-6, 'cancelled notes stay silent');
    assert.equal(output.isolated, true);
  } finally { await closeBrowser({ child, server, watchdog }); }
});
