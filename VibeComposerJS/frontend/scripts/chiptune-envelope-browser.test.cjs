const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const { loadMusic } = require('./music-loader.cjs');
const { findBrowser, createBrowserServer, listen, launchBrowser, closeBrowser, watchBrowser } = require('./support/browser-harness.cjs');

test('Harp tuning and decay match the SF2 sampler and note-off clips linear envelope stages', { skip: !findBrowser() }, async () => {
  const { Sf2Bank } = loadMusic('../audio/sf2-resolver.ts');
  const bytes = fs.readFileSync(path.join(__dirname, '../../chiptune_soundfont_4.0.sf2'));
  const bank = new Sf2Bank(bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength));
  const region = bank.resolve(0, 46).regions[0];
  const esbuild = require(require.resolve('esbuild', { paths: [path.dirname(require.resolve('@angular/build/package.json'))] }));
  const bundle = esbuild.buildSync({ stdin: { contents: `
    export { TinySynthEngine } from './src/app/core/audio/tiny-synth-engine';
    export { Sf2InstrumentEngine } from './src/app/core/audio/sf2-instrument-engine';
    export { CHIPTUNE_PATCHES } from './src/app/core/audio/chiptune-patches';`,
    resolveDir: path.join(__dirname, '..'), loader: 'ts' }, bundle: true, write: false,
    format: 'iife', globalName: 'Chip', platform: 'browser' }).outputFiles[0].text;
  const html = '<!doctype html><script>' + bundle + '</script><script>const harpRegion=' + JSON.stringify(region)
    + ';const harpPcm=' + JSON.stringify(Array.from(bank.pcm(region))) + ';(' + (async () => {
    try {
      const rate = 24000, errors = [], ratios = [], cents = [];
      const harp = Chip.CHIPTUNE_PATCHES.melodic[46][0];
      const controlled = { delay: .05, attack: .1, hold: .1, decay: .3, sustain: .2, release: .15 };
      for (const [envelope, duration] of [[harp.envelope, 1.2], [controlled, .025], [controlled, .1],
        [controlled, .2], [controlled, .4], [controlled, .8]]) {
        const ctx = new OfflineAudioContext(2, rate * 2, rate);
        const left = ctx.createStereoPanner(); left.pan.value = -1; left.connect(ctx.destination);
        const right = ctx.createStereoPanner(); right.pan.value = 1; right.connect(ctx.destination);
        const r = { ...harpRegion, ampDelay: envelope.delay, ampAttack: envelope.attack,
          ampHold: envelope.hold, ampDecay: envelope.decay, ampSustain: envelope.sustain, ampRelease: envelope.release };
        const preset = { name: 'Harp', bank: 0, program: 46, regions: [r] };
        const sampler = new Chip.Sf2InstrumentEngine(ctx, { resolve: () => preset,
          pcm: () => new Float32Array(harpPcm), toSmplr: () => ({ samples: { baseUrl: '', formats: [] }, groups: [{ regions: [r] }] }) });
        const synth = new Chip.TinySynthEngine(ctx, { ...Chip.CHIPTUNE_PATCHES,
          melodic: Array.from({ length: 128 }, () => [{ ...harp, envelope }]) });
        const a = synth.schedule(left, 46, false, 60, 100, .01, duration);
        const b = sampler.schedule(right, 46, false, 60, 100, .01, duration);
        await sampler.flush();
        const audio = await ctx.startRendering();
        const rms = (channel, time) => {
          const values = audio.getChannelData(channel).subarray(Math.floor(time * rate), Math.floor((time + .02) * rate));
          return Math.sqrt(values.reduce((sum, x) => sum + x * x, 0) / values.length);
        };
        const times = Array.from({ length: 90 }, (_, i) => .01 + i * .02);
        const peakA = Math.max(...times.map(t => rms(0, t)));
        const level = t => {
          const attackEnd = envelope.delay + envelope.attack, holdEnd = attackEnd + envelope.hold;
          return t < envelope.delay ? 0 : t < attackEnd ? (t - envelope.delay) / envelope.attack
            : t < holdEnd ? 1 : t < holdEnd + envelope.decay
              ? 1 + (envelope.sustain - 1) * (t - holdEnd) / envelope.decay : envelope.sustain;
        };
        // Independently check interrupted ramps, including release during attack.
        const expected = time => Math.sqrt(Array.from({ length: 240 }, (_, i) => {
          const t = time + i / 12000 - .01;
          const gain = t < duration ? level(t) : level(duration) * Math.max(0, 1 - (t - duration) / envelope.release);
          return gain * gain;
        }).reduce((sum, x) => sum + x, 0) / 240);
        const reference = duration === 1.2 ? t => rms(1, t) : expected;
        const peakB = Math.max(...times.map(reference));
        errors.push(Math.max(...times.map(t => Math.abs(rms(0, t) / (peakA || 1) - reference(t) / (peakB || 1)))));
        if (duration === 1.2) {
          ratios.push(rms(0, .5) / rms(0, .05), rms(1, .5) / rms(1, .05));
          // Interpolated positive zero crossings measure actual rendered pitch.
          for (let channel = 0; channel < 2; channel++) {
            const pcm = audio.getChannelData(channel), crossings = [];
            for (let i = Math.floor(.1 * rate); i < Math.floor(.8 * rate); i++) {
              if (pcm[i] <= 0 && pcm[i + 1] > 0) crossings.push(i - pcm[i] / (pcm[i + 1] - pcm[i]));
            }
            const hz = (crossings.length - 1) * rate / (crossings.at(-1) - crossings[0]);
            cents.push(1200 * Math.log2(hz / (440 * 2 ** ((60 - 69) / 12))));
          }
        }
        [...a, ...b].forEach(v => v.dispose()); synth.dispose(); sampler.dispose();
      }
      await fetch('/result', { method: 'POST', body: JSON.stringify({ errors, ratios, cents }) });
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
    output.errors.forEach((error, index) => assert.ok(error < .04, `envelope scenario ${index}: ${error}`));
    assert.ok(output.ratios.every(r => r > .45 && r < .6), 'Harp retains roughly half its amplitude after half a second');
    assert.ok(output.cents.every(c => Math.abs(c - 7) < .1), 'both synth and SF2 Harp render at +7 cents: ' + output.cents);
  } finally { await closeBrowser({ child, server, watchdog }); }
});
