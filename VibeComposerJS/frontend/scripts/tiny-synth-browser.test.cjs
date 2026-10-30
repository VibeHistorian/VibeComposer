const { findBrowser, createBrowserServer, listen, launchBrowser, closeBrowser, watchBrowser } = require('./support/browser-harness.cjs');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const ts = require('typescript');

const browser = findBrowser();

test('TinySynth renders GM presets and drums with independent voices and track routing', {
  skip: browser ? false : 'Set OVERVIEW_TEST_BROWSER to a Chromium executable',
}, async () => {
  const library = fs.readFileSync(require.resolve('webaudio-tinysynth'), 'utf8');
  const adapter = ts.transpileModule(fs.readFileSync(path.join(__dirname,
    '../src/app/core/audio/tiny-synth-engine.ts'), 'utf8'), {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS },
  }).outputText;
  const html = `<!doctype html><script>${library}</script><script>(() => {
    const module = { exports: {} };
    const exports = module.exports;
    const require = () => window.WebAudioTinySynth;
    ${adapter}
    (async () => {
      try {
        const rate = 22050;
        const unhandled = [];
        window.addEventListener('unhandledrejection', event => {
          unhandled.push(String(event.reason));
        });
        const timers = new Set();
        const setTimer = window.setInterval.bind(window);
        const clearTimer = window.clearInterval.bind(window);
        window.setInterval = (...args) => {
          const id = setTimer(...args);
          timers.add(id);
          return id;
        };
        window.clearInterval = id => {
          timers.delete(id);
          clearTimer(id);
        };
        window.AudioContext = class {
          constructor() { throw Error('An offline synth must not create a realtime context'); }
        };
        const engine = new module.exports.TinySynthEngine(new OfflineAudioContext(2, rate * 12, rate));
        const context = engine.context;
        const timerExposed = timers.size === 1 && timers.has(engine.synth.timerId);
        const track = context.createGain(); track.connect(context.destination);
        const silent = context.createGain(); silent.gain.value = 0; silent.connect(context.destination);
        const finish = (voices) => {
          for (const voice of voices) {
            voice.onEnded(() => voice.dispose());
          }
          return voices;
        };
        // Every published GM program and percussion pitch must create usable voices.
        const programs = [], drums = [];
        let maxTail = 0;
        for (let program = 0; program < 128; program++) {
          const voices = finish(engine.schedule(silent, program, false, 48, 90, .01, .1));
          programs.push(voices.length);
          maxTail = Math.max(maxTail, ...voices.map(voice => voice.stopTime - .11));
        }
        for (let pitch = 35; pitch <= 81; pitch++) {
          const voices = finish(engine.schedule(silent, 0, true, pitch, 90, .01, .1));
          drums.push(voices.length);
          maxTail = Math.max(maxTail, ...voices.map(voice => voice.stopTime - .01));
        }
        const chosen = [0, 32, 33, 34, 36, 38, 40, 73];
        chosen.forEach((program, index) => finish(engine.schedule(track, program, false,
          48, 100, 1 + index, .25)));
        // Repeated pitches keep their own release times even when later scheduled
        // notes end earlier. Rendering another instrument never changes old voices.
        const long = finish(engine.schedule(silent, 32, false, 48, 100, 9, .8));
        const longStop = long[0].stopTime;
        const short = finish(engine.schedule(silent, 40, false, 48, 100, 9.1, .1));
        const independent = long[0].stopTime === longStop && short[0].stopTime < longStop;
        // Cancel a future drum and pitched voice exactly as transport stop/reload does.
        const cancelled = [
          ...finish(engine.schedule(track, 0, true, 38, 100, 10, .2)),
          ...finish(engine.schedule(track, 32, false, 48, 100, 10, .2)),
        ];
        cancelled.forEach(voice => voice.stop(9.5));
        const buffer = await context.startRendering();
        const data = buffer.getChannelData(0);
        const slices = chosen.map((_, index) => Array.from(data.slice(
          (1 + index) * rate, Math.floor((1.25 + index) * rate))));
        const rms = values => Math.sqrt(values.reduce((sum, value) => sum + value * value, 0) / values.length);
        const levels = slices.map(rms);
        const differences = slices.slice(1).map(values => rms(values.map((v, i) => v - slices[0][i])));
        const bassDifferences = slices.slice(1, 6).flatMap((values, index, bass) =>
          bass.slice(index + 1).map(other => rms(values.map((v, i) => v - other[i]))));
        const silence = rms(Array.from(data.slice(9 * rate)));
        engine.dispose();
        // Give rejected resume promises a task boundary to reach the listener.
        await new Promise(resolve => setTimeout(resolve, 0));
        await fetch('/result', { method: 'POST', body: JSON.stringify({
          programs, drums, levels, differences, bassDifferences, independent, silence, maxTail,
          timerExposed, timersRemaining: timers.size, unhandled,
          detached: engine.synth.notetab.length === 0,
        }) });
      } catch (error) {
        await fetch('/result', { method: 'POST', body: JSON.stringify({ error: error.stack }) });
      }
    })();
  })();</script>`;
  let resolve, reject;
  const result = new Promise((done, fail) => { resolve = done; reject = fail; });
  result.catch(() => {}); // CDP setup can fail before the result is awaited.
  const server = createBrowserServer({ html, resolve: resolve, reject });
  await listen(server);
  const child = launchBrowser(browser, ['--headless', '--disable-gpu', '--no-first-run',
    '--disable-background-networking', '--disable-component-update', '--disable-sync',
    '--no-default-browser-check', '--autoplay-policy=no-user-gesture-required',
    'http://127.0.0.1:' + server.address().port], { stdio: ['ignore', 'ignore', 'pipe'] });
  const watchdog = watchBrowser(child, reject, 30000);
  try {
    const output = await result;
    assert.equal(output.error, undefined);
    assert.equal(output.timerExposed, true, 'the patch exposes the sequencer timer');
    assert.equal(output.timersRemaining, 0, 'disposing the offline engine clears its timer');
    assert.deepEqual(output.unhandled, [], 'offline initialization must not reject resume promises');
    assert.equal(output.programs.length, 128);
    assert.ok(output.programs.every(count => count > 0));
    assert.equal(output.drums.length, 47);
    assert.ok(output.maxTail > 30 && output.maxTail <= 40, 'offline export reserves the longest GM and drum tails');
    assert.ok(output.drums.every(count => count > 0));
    assert.ok(output.levels.every(level => level > .001), 'selected presets render audible output');
    assert.ok(output.differences.every(level => level > .001), 'selected presets differ from piano');
    assert.ok(output.bassDifferences.every(level => level > .001), 'bass presets differ from one another');
    assert.equal(output.independent, true, 'same-pitch notes retain independent durations');
    assert.equal(output.detached, true, 'the adapter owns voices outside TinySynth sequencer cleanup');
    assert.ok(output.silence < .000001, 'silent buses and cancelled future voices emit no sound');
  } finally {
    await closeBrowser({ child, server, watchdog });
  }
});


test('SF2 GM sampler resolves and renders all 128 programs and Standard drums offline', {
  skip: browser ? false : 'Set OVERVIEW_TEST_BROWSER to a Chromium executable',
}, async () => {
  const esbuild = require(require.resolve('esbuild', { paths: [path.dirname(require.resolve('@angular/build/package.json'))] }));
  const bundle = esbuild.buildSync({ stdin: {
    contents: 'export { Sf2InstrumentEngine } from "./src/app/core/audio/sf2-instrument-engine"; export { Sf2Bank } from "./src/app/core/audio/sf2-resolver"; export { Instrument, Scheduler } from "smplr";',
    resolveDir: path.join(__dirname, '..'), loader: 'ts',
  }, bundle: true, write: false, format: 'iife', globalName: 'TestSampler', platform: 'browser' }).outputFiles[0].text;
  const sf2 = fs.readFileSync(path.join(__dirname, '../../Super_Nintendo_Unofficial_update.sf2')).toString('base64');
  const html = '<!doctype html><script>' + bundle + '</script><script>' +
    'fetch = async () => ({ ok: true, arrayBuffer: async () => Uint8Array.from(atob("' + sf2 + '"), c => c.charCodeAt(0)).buffer });' +
    '(' + (async () => {
      let engine;
      try {
        const rate = 22050;
        const context = new OfflineAudioContext(2, rate * 40, rate);
        const parsed = new TestSampler.Sf2Bank(await (await fetch('bank')).arrayBuffer());
        engine = new TestSampler.Sf2InstrumentEngine(context, parsed);
        await engine.prepare();
        const bus = context.createGain(); bus.connect(context.destination);
        const silent = context.createGain(); silent.gain.value = 0; silent.connect(context.destination);
        const finish = voices => voices.forEach(voice => voice.onEnded(() => voice.dispose()));
        for (let program = 0; program < 128; program++) {
          const preset = parsed.resolve(0, program);
          if (!preset.regions.length) throw Error('empty preset ' + program);
          const region = preset.regions.find(r => r.keyRange[0] <= 60 && r.keyRange[1] >= 60) || preset.regions[0];
          const note = Math.min(region.keyRange[1], Math.max(region.keyRange[0], 60));
          finish(engine.schedule(bus, program, false, note, 100, 4 + program * .25, .12));
        }
        for (let note = 35; note <= 81; note++) finish(engine.schedule(bus, 0, true, note, 100, 36 + (note - 35) * .06, .03));
        finish(engine.schedule(bus, 0, false, 60, 100, .05, .2));
        finish(engine.schedule(bus, 0, false, 60, 100, 2, .2));
        const cancelled = engine.schedule(bus, 0, false, 60, 100, 1, .2);
        finish(cancelled); cancelled.forEach(voice => voice.stop(0));
        finish(engine.schedule(silent, 0, false, 60, 100, 1.5, .2));
        await engine.flush();
        const rendered = await context.startRendering();
        const data = rendered.getChannelData(0);
        const peak = (a,b) => data.subarray(a*rate,b*rate).reduce((m,x) => Math.max(m,Math.abs(x)),0);
        const isolated = [];
        for (let program = 0; program < 129; program++) {
          const isolatedContext = new OfflineAudioContext(2, rate, rate);
          const isolatedEngine = new TestSampler.Sf2InstrumentEngine(isolatedContext, parsed);
          try {
            await isolatedEngine.prepare();
            const preset = parsed.resolve(program === 128 ? 128 : 0, program === 128 ? 0 : program);
            const region = preset.regions.find(r => r.keyRange[0] <= 60 && r.keyRange[1] >= 60) || preset.regions[0];
            const note = Math.min(region.keyRange[1], Math.max(region.keyRange[0], 60));
            const voices = isolatedEngine.schedule(isolatedContext.destination, program === 128 ? 0 : program, program === 128, note, 100, .01, .4);
            voices.forEach(v => v.onEnded(() => v.dispose()));
            await isolatedEngine.flush();
            const audio = await isolatedContext.startRendering();
            isolated.push(Math.max(...[0,1].map(c => audio.getChannelData(c).reduce((m,x) => Math.max(m,Math.abs(x)),0))));
          } finally { isolatedEngine.dispose(); }
        }
        // A controlled constant sample verifies the renderer patch independently of bank content.
        const envelopeContext = new OfflineAudioContext(2, rate, rate);
        const constant = envelopeContext.createBuffer(1, rate, rate);
        constant.getChannelData(0).fill(1);
        const factory = TestSampler.Instrument((_ctx, _opts, sampler) => sampler.loadInstrument({
          samples: { baseUrl: '', formats: [] }, groups: [{ regions: [{ sample: 'constant', keyRange: [0,127],
            pitch: 60, pan: 1, ampDelay: .02, ampAttack: .02, ampHold: .02, ampDecay: .02,
            ampSustain: .2, ampRelease: .1 }] }],
        }, new Map([['constant', constant]])));
        const controlled = factory(envelopeContext, { volume: 127 });
        await controlled.ready;
        controlled.start({ note: 60, time: 0, duration: .2 });
        const envelopeAudio = await envelopeContext.startRendering();
        const left = envelopeAudio.getChannelData(0), right = envelopeAudio.getChannelData(1);
        const at = t => right[Math.floor(t * rate)];
        const envelopeCheck = { left: left.reduce((m,x) => Math.max(m,Math.abs(x)),0), delay: at(.01),
          attack: at(.03), hold: at(.05), sustain: at(.15), release: at(.25), end: at(.4) };
        controlled.dispose();
        await fetch('/result', { method: 'POST', body: JSON.stringify({ envelopeCheck, isolated, early: peak(0,.5), late: peak(2,2.5), silent: peak(1.5,1.9), programs: Array.from({ length: 128 }, (_, i) => peak(4 + i * .25, 4.24 + i * .25)), drums: peak(36,39) }) });
      } catch (error) {
        await fetch('/result', { method: 'POST', body: JSON.stringify({ error: String(error.stack || error) }) });
      } finally { engine?.dispose(); }
    }).toString() + ')();</script>';
  // Restore native fetch for the harness result endpoint while intercepting the SF2 asset.
  const fixtureHtml = html.replace('fetch = async () =>', 'const nativeFetch = fetch; fetch = async (url, options) => url === "/result" ? nativeFetch(url, options) :');
  let resolve, reject;
  const result = new Promise((done, fail) => { resolve = done; reject = fail; });
  result.catch(() => {});
  const server = createBrowserServer({ html: fixtureHtml, resolve, reject });
  await listen(server);
  const child = launchBrowser(browser, ['--headless', '--disable-gpu', '--no-first-run',
    '--autoplay-policy=no-user-gesture-required', 'http://127.0.0.1:' + server.address().port], { stdio: ['ignore', 'ignore', 'pipe'] });
  const watchdog = watchBrowser(child, reject, 30000);
  try {
    const output = await result;
    assert.equal(output.error, undefined);
    assert.ok(output.early > .001, 'initial notes produce PCM');
    assert.ok(output.late > .001, 'future notes render without timer scheduling');
    assert.ok(output.silent < .000001, 'cancelled notes and muted buses remain silent');
    assert.equal(output.programs.length, 128);
    assert.ok(output.programs.every(p => p > 0.0000001), 'every GM slot produces audio');
    assert.ok(output.drums > .001, 'Standard drum kit produces audio');
    assert.equal(output.isolated.length, 129);
    output.isolated.forEach((peak, program) => assert.ok(peak > 1e-8, 'isolated bank/program ' + program + ' produces PCM'));
    const e = output.envelopeCheck;
    assert.ok(e.left < 1e-7, 'per-region pan routes exclusively right');
    assert.equal(e.delay, 0);
    assert.ok(e.attack > 0 && e.attack < e.hold);
    assert.ok(Math.abs(e.sustain / e.hold - .2) < .01, 'SF2 sustain attenuation is retained');
    assert.ok(e.release > 0 && e.release < e.sustain, 'release starts at envelope level, without resetting to one');
    assert.equal(e.end, 0);
  } finally { await closeBrowser({ child, server, watchdog }); }
});
