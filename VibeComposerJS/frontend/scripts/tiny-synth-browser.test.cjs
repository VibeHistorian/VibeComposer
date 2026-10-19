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
        window.AudioContext = class {
          constructor() { throw Error('An offline synth must not create a realtime context'); }
        };
        const engine = new module.exports.TinySynthEngine(new OfflineAudioContext(2, rate * 12, rate));
        const context = engine.context;
        const track = context.createGain(); track.connect(context.destination);
        const silent = context.createGain(); silent.gain.value = 0; silent.connect(context.destination);
        const finish = (voices) => {
          for (const voice of voices) {
            voice.source.onended = () => {
              voice.disconnect(); voice.source.disconnect(); voice.envelope.disconnect();
            };
            voice.source.stop(voice.stopTime);
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
        cancelled.forEach(voice => voice.source.stop(9.5));
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
        await fetch('/result', { method: 'POST', body: JSON.stringify({
          programs, drums, levels, differences, bassDifferences, independent, silence, maxTail,
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
