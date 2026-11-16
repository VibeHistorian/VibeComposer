const assert = require('node:assert/strict');
const path = require('node:path');
const test = require('node:test');
const { findBrowser, createBrowserServer, listen, launchBrowser, closeBrowser, watchBrowser } = require('./support/browser-harness.cjs');

test('dense chip output is protected, quiet stereo is unchanged and voice endings fade', { skip: !findBrowser() }, async t => {
  const esbuild = require(require.resolve('esbuild', { paths: [path.dirname(require.resolve('@angular/build/package.json'))] }));
  const bundle = esbuild.buildSync({ stdin: { contents: `
    export { TinySynthEngine } from './src/app/core/audio/tiny-synth-engine';
    export { createInstrumentEngine } from './src/app/core/audio/create-instrument-engine';
    export { protectedAudioOutput } from './src/app/core/audio/output-limiter';`,
    resolveDir: path.join(__dirname, '..'), loader: 'ts' }, bundle: true, write: false,
    format: 'iife', globalName: 'Audio', platform: 'browser' }).outputFiles[0].text;
  const html = '<!doctype html><script>' + bundle + '</script><script>(' + (async () => {
    try {
      const rate = 48000;
      const peak = pcm => pcm.reduce((p, x) => Math.max(p, Math.abs(x)), 0);
      const dense = async limited => {
        const ctx = new OfflineAudioContext(2, rate * 3, rate);
        const synth = await Audio.createInstrumentEngine(ctx, 'chiptune', undefined, true);
        const master = ctx.createGain(); master.gain.value = .8;
        master.connect(limited ? Audio.protectedAudioOutput(ctx) : ctx.destination);
        const voices = [];
        for (let step = 0; step < 12; step++) {
          for (const program of [0, 46, 80, 81, 88]) for (const midi of [48, 60, 64, 67, 72]) {
            for (let echo = 0; echo < 3; echo++) voices.push(...synth.schedule(master,
              program, false, midi, 110 - echo * 20, .05 + step * .1 + echo * .07, .25));
          }
          for (const drum of [36, 38, 42]) voices.push(...synth.schedule(master, 0, true, drum, 120, .05 + step * .1, .1));
        }
        const data = await ctx.startRendering();
        voices.forEach(v => v.dispose()); synth.dispose();
        return peak(data.getChannelData(0));
      };
      const raw = await dense(false), limited = await dense(true);
      const ctx = new OfflineAudioContext(2, 1024, rate);
      const values = [.25, -.5, .7, -.7, 2, -2, 20, -20];
      const buffer = ctx.createBuffer(2, 1024, rate);
      for (let ch = 0; ch < 2; ch++) for (let i = 0; i < 1024; i++) buffer.getChannelData(ch)[i] = values[Math.floor(i / 128)] * (ch ? -1 : 1);
      const source = ctx.createBufferSource(); source.buffer = buffer;
      const bus = Audio.protectedAudioOutput(ctx), shared = bus === Audio.protectedAudioOutput(ctx);
      source.connect(bus); source.start();
      const pcm = await ctx.startRendering();
      const actual = [0, 1].map(ch => values.map((_, i) => pcm.getChannelData(ch)[i * 128 + 64]));
      const tailContext = new OfflineAudioContext(1, rate * 2, rate);
      const synth = new Audio.TinySynthEngine(tailContext);
      synth.synth.setTimbre(0, 0, [{ w: 'sine', v: 1, a: .001, d: .01, s: 1, r: .2 }]);
      const voices = synth.schedule(tailContext.destination, 0, false, 61, 127, .05, .1);
      const end = Math.round(voices[0].stopTime * rate);
      const data = (await tailContext.startRendering()).getChannelData(0);
      const before = peak(data.subarray(end - 480, end - 240));
      const ending = Math.abs(data[end - 1]), after = peak(data.subarray(end));
      voices.forEach(v => v.dispose()); synth.dispose();
      await fetch('/result', { method: 'POST', body: JSON.stringify({ raw, limited, values, actual, shared, before, ending, after }) });
    } catch (e) { await fetch('/result', { method: 'POST', body: JSON.stringify({ error: e.stack }) }); }
  }).toString() + ')();</script>';
  let resolve, reject;
  const result = new Promise((done, fail) => { resolve = done; reject = fail; }); result.catch(() => {});
  const server = createBrowserServer({ html, resolve, reject }); await listen(server);
  const child = launchBrowser(findBrowser(), ['--headless', '--disable-gpu', '--no-first-run',
    '--autoplay-policy=no-user-gesture-required', 'http://127.0.0.1:' + server.address().port], { stdio: ['ignore', 'ignore', 'pipe'] });
  const watchdog = watchBrowser(child, reject, 45000);
  try {
    const out = await result;
    assert.equal(out.error, undefined);
    t.diagnostic(JSON.stringify(out));
    assert.ok(out.raw > 1, 'fixture overloads unprotected output');
    assert.ok(out.limited <= .98001 && out.limited > .8, 'dense mix peaks protected');
    assert.equal(out.shared, true, 'previews and playback share the final stage');
    for (let ch = 0; ch < 2; ch++) for (let i = 0; i < out.values.length; i++) {
      const actual = out.actual[ch][i], input = out.values[i] * (ch ? -1 : 1);
      assert.ok(Math.abs(actual) <= .98001);
      if (Math.abs(input) < .8) assert.ok(Math.abs(actual - input) < 1e-6, 'quiet stereo unchanged');
      assert.equal(Math.sign(actual), Math.sign(input));
    }
    assert.ok(out.before > .01 && out.ending < out.before / 100, 'residual fades to silence before stop');
    assert.equal(out.after, 0);
  } finally { await closeBrowser({ child, server, watchdog }); }
});
