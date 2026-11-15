const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const { findBrowser, createBrowserServer, listen, launchBrowser, closeBrowser, watchBrowser } = require('./support/browser-harness.cjs');

test('Xylophone renders harmonic attack, slow decay, velocity-stable shimmer and note-off release', { skip: !findBrowser() }, async () => {
  const esbuild = require(require.resolve('esbuild', { paths: [path.dirname(require.resolve('@angular/build/package.json'))] }));
  const bundle = esbuild.buildSync({ stdin: { contents: `
    export { TinySynthEngine } from './src/app/core/audio/tiny-synth-engine';
    export { CHIPTUNE_PATCHES } from './src/app/core/audio/chiptune-patches';`,
    resolveDir: path.join(__dirname, '..'), loader: 'ts' }, bundle: true, write: false,
    format: 'iife', globalName: 'Chip', platform: 'browser' }).outputFiles[0].text;
  const html = '<!doctype html><script>' + bundle + '</script><script>(' + (async () => {
    try {
      const rate = 24000, original = Chip.CHIPTUNE_PATCHES.melodic[13];
      const frequency = 440 * 2 ** ((60 - 69 - .1) / 12);
      const render = async (velocity, duration, disabled = -1) => {
        const ctx = new OfflineAudioContext(1, rate * 5, rate);
        const patch = original.map((op, i) => i === disabled ? { ...op, v: 0 } : op);
        const engine = new Chip.TinySynthEngine(ctx, { ...Chip.CHIPTUNE_PATCHES,
          melodic: Chip.CHIPTUNE_PATCHES.melodic.map((p, i) => i === 13 ? patch : p) });
        const voices = engine.schedule(ctx.destination, 13, false, 60, velocity, .05, duration);
        const audio = (await ctx.startRendering()).getChannelData(0);
        const count = voices.length, stop = voices[0].stopTime;
        voices.forEach(v => v.dispose()); engine.dispose();
        return { audio, count, stop };
      };
      const component = (audio, time, duration, harmonic) => {
        let a = 0, b = 0, weights = 0;
        const begin = Math.floor((time + .05) * rate), length = Math.floor(duration * rate);
        for (let i = 0; i < length; i++) {
          const w = .5 - .5 * Math.cos(2 * Math.PI * i / (length - 1));
          const phase = 2 * Math.PI * frequency * harmonic * (begin + i) / rate;
          a += audio[begin + i] * w * Math.cos(phase); b += audio[begin + i] * w * Math.sin(phase); weights += w;
        }
        return 2 * Math.hypot(a, b) / weights;
      };
      const modulation = (audio, harmonic) => {
        const points = Array.from({ length: 80 }, (_, i) => {
          const t = .3 + i * .025;
          return { t: t + .04, amplitude: component(audio, t, .08, harmonic) * Math.exp((t + .04) / 1.2) };
        });
        const mean = points.reduce((sum, p) => sum + p.amplitude, 0) / points.length;
        let a = 0, b = 0;
        for (const p of points) {
          const residual = p.amplitude / mean - 1;
          a += residual * Math.cos(2 * Math.PI * 3.36 * p.t); b += residual * Math.sin(2 * Math.PI * 3.36 * p.t);
        }
        return 2 * Math.hypot(a, b) / points.length;
      };
      const normal = await render(100, 4), quiet = await render(40, 4), loud = await render(127, 4);
      const short = await render(100, .1), noAttack = await render(100, 4, 2), noMod = await render(100, 4, 3);
      const body = component(normal.audio, .3, .2, 1);
      const decayDbPerSecond = 20 * Math.log10(component(normal.audio, 2.3, .2, 1) / body) / 2;
      const harmonicRatios = [4, 6, 9, 11].map(h => component(normal.audio, .3, .2, h) / body);
      const attackThird = component(normal.audio, .008, .03, 3) / component(normal.audio, .008, .03, 1);
      const unmodulatedThird = component(noAttack.audio, .008, .03, 3) / component(noAttack.audio, .008, .03, 1);
      const lateThird = component(normal.audio, .4, .1, 3) / component(normal.audio, .4, .1, 1);
      const depths = [quiet, normal, loud].map(r => modulation(r.audio, 4));
      const silence = short.audio.subarray(rate).reduce((peak, x) => Math.max(peak, Math.abs(x)), 0);
      const heldLevel = component(normal.audio, 1, .1, 1);
      const velocityRatios = [quiet, loud].map(r => component(r.audio, .3, .2, 1) / body);
      await fetch('/result', { method: 'POST', body: JSON.stringify({ count: normal.count, decayDbPerSecond,
        harmonicRatios, attackThird, unmodulatedThird, lateThird, depths, velocityRatios,
        fundamentalDepth: modulation(normal.audio, 1), disabledDepth: modulation(noMod.audio, 4),
        silence, heldLevel, shortStop: short.stop, heldStop: normal.stop }) });
    } catch (error) { await fetch('/result', { method: 'POST', body: JSON.stringify({ error: error.stack }) }); }
  }).toString() + ')();</script>';
  let resolve, reject;
  const result = new Promise((done, fail) => { resolve = done; reject = fail; }); result.catch(() => {});
  const server = createBrowserServer({ html, resolve, reject }); await listen(server);
  const child = launchBrowser(findBrowser(), ['--headless', '--disable-gpu', '--no-first-run',
    '--autoplay-policy=no-user-gesture-required', 'http://127.0.0.1:' + server.address().port], { stdio: ['ignore', 'ignore', 'pipe'] });
  const watchdog = watchBrowser(child, reject, 30000);
  try {
    const out = await result;
    assert.equal(out.error, undefined);
    fs.writeFileSync(path.join(__dirname, '../.angular/xylophone-patch-measurements.json'), JSON.stringify(out, null, 2));
    assert.equal(out.count, 4);
    assert.ok(out.decayDbPerSecond > -8 && out.decayDbPerSecond < -6, 'reference-like body decay');
    out.harmonicRatios.forEach((r, i) => assert.ok(r > [.18, .16, .07, .06][i], 'audible upper harmonic ' + i));
    assert.ok(out.attackThird > .01 && out.attackThird > out.unmodulatedThird * 3, 'FM gives attack extra harmonics');
    assert.ok(out.lateThird < out.attackThird / 5, 'attack roughness fades');
    assert.ok(out.depths.every(d => d > .15 && d < .25), '3.36Hz overtone modulation');
    assert.ok(Math.max(...out.depths) - Math.min(...out.depths) < .015, 'quiet notes keep relative modulation depth');
    assert.ok(out.fundamentalDepth < .02 && out.disabledDepth < .02, 'modulation targets overtones');
    assert.ok(Math.abs(out.velocityRatios[0] - .16) < .005 && Math.abs(out.velocityRatios[1] - 1.6129) < .005, 'velocity controls carrier level');
    assert.ok(out.silence < 1e-7 && out.heldLevel > .001, 'short notes release while held notes ring');
    assert.ok(out.shortStop < 1 && out.heldStop > 4, 'durations retain independent releases');
  } finally { await closeBrowser({ child, server, watchdog }); }
});
