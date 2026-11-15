const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');
function load(name, dependencies = {}) {
  const source = fs.readFileSync(path.join(__dirname, '../src/app/core/audio', name + '.ts'), 'utf8');
  const module = { exports: {} };
  new Function('require', 'module', 'exports', ts.transpileModule(source, { compilerOptions: {
    target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS,
  } }).outputText)(id => dependencies[id], module, module.exports);
  return module.exports;
}

test('bundled TinySynth offsets cover every program and supported drum, using editable dB values', () => {
  const { TINY_SYNTH_LOUDNESS_PROFILE: profile, TINY_SYNTH_MELODIC_OFFSETS_DB: melodic,
    TINY_SYNTH_DRUM_OFFSETS_DB: drums } = load('tiny-synth-loudness');
  assert.equal(Object.keys(melodic).length, 128);
  assert.equal(Object.keys(drums).length, 48);
  for (let key = 0; key < 128; key++) {
    assert.ok(Number.isFinite(melodic[key]));
    assert.equal(profile.melodic[key], 10 ** (melodic[key] / 20));
    assert.equal(profile.drums[key], 10 ** ((drums[key] ?? 0) / 20));
  }
  assert.ok([...profile.melodic, ...profile.drums].every(g => Number.isFinite(g) && g > 0));
});
test('calibration offsets handle silence, cap boosts and attenuation, and respect sample peak headroom', () => {
  const { calibrationGain } = load('instrument-calibration');
  assert.equal(calibrationGain(null, 0), 1);
  assert.equal(calibrationGain(NaN, .1), 1);
  assert.equal(calibrationGain(-24, .1), 1);
  assert.ok(Math.abs(calibrationGain(-30, .1) - 10 ** (6 / 20)) < 1e-10);
  assert.equal(calibrationGain(-60, .001), 10 ** (12 / 20));
  assert.equal(calibrationGain(0, .1), 10 ** (-12 / 20));
  assert.ok(calibrationGain(-40, .8) * .8 <= 10 ** (-3 / 20));
});
test('shared output correction preserves note timing/velocity, separates drum notes and cleans up routes once', () => {
  const { NormalizedInstrumentEngine } = load('normalized-instrument-engine');
  const calls = [], gains = [];
  const context = { createGain: () => {
    const gain = { gain: { value: 1 }, disconnects: 0, connect(to) { this.to = to; }, disconnect() { this.disconnects++; } };
    gains.push(gain); return gain;
  } };
  let disposals = 0, stop, ended;
  const engine = { context, prepare: async () => {}, flush: async () => {}, dispose() {},
    schedule(...args) { calls.push(args); return [{ stopTime: 4, stop: time => { stop = time; },
      onEnded: callback => { ended = callback; }, dispose: () => { disposals++; } }]; } };
  const melodic = Array(128).fill(1), drums = Array(128).fill(1);
  melodic[32] = .5; drums[36] = .25; drums[38] = 2;
  const normalized = new NormalizedInstrumentEngine(engine, { melodic, drums });
  const destination = {};
  const a = normalized.schedule(destination, 32, false, 48, 80, 1, 2)[0];
  const b = normalized.schedule(destination, 32, false, 60, 110, 2, 1)[0];
  assert.equal(calls[0][0], calls[1][0]);
  assert.equal(calls[0][0].gain.value, .5);
  assert.deepEqual(calls[0].slice(1), [32, false, 48, 80, 1, 2]);
  const kick = normalized.schedule(destination, 0, true, 36, 100, 0, .2)[0];
  const snare = normalized.schedule(destination, 0, true, 38, 100, 0, .2)[0];
  assert.equal(calls[2][0].gain.value, .25);
  assert.equal(calls[3][0].gain.value, 2);
  assert.equal(a.stopTime, 4);
  a.stop(3); assert.equal(stop, 3);
  const callback = () => {}; a.onEnded(callback); assert.equal(ended, callback);
  a.dispose(); a.dispose(); assert.equal(disposals, 1); assert.equal(gains[0].disconnects, 0);
  b.dispose(); assert.equal(gains[0].disconnects, 1);
  kick.dispose(); snare.dispose();
  assert.ok(gains.every(g => g.disconnects === 1));
  normalized.schedule(destination, 0, false, 60, 100, 0, .2);
  assert.equal(calls.at(-1)[0], destination, 'unity correction passes through unchanged');
  normalized.dispose();
});
