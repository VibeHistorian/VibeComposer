const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

function fixture(pitches) {
  const started = [];
  const sampler = { ready: Promise.resolve(), start: options => { started.push(options.note); return () => {}; }, dispose() {} };
  const module = { exports: {} };
  const source = fs.readFileSync(path.join(__dirname, '../src/app/core/audio/sf2-instrument-engine.ts'), 'utf8');
  const compiled = ts.transpileModule(source, { compilerOptions: {
    target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS,
  } }).outputText;
  new Function('require', 'module', 'exports', compiled)(() => ({
    Instrument: () => () => sampler, Scheduler: () => ({}),
  }), module, module.exports);
  const regions = pitches.map(pitch => ({ sample: String(pitch), sampleRate: 22050,
    keyRange: [pitch, pitch], velRange: [1, 127] }));
  const bank = { resolve: () => ({ program: 0, regions }), toSmplr: () => ({}), pcm: () => new Float32Array(8) };
  const context = { createBuffer: () => ({ copyToChannel() {} }) };
  return { engine: new module.exports.Sf2InstrumentEngine(context, bank), started };
}

test('SF2 preserves native shaker regions instead of substituting maracas', async () => {
  const { engine, started } = fixture([70, 82]);
  assert.equal(engine.schedule({}, 0, true, 82, 100, 0, .25).length, 1);
  await engine.flush();
  assert.deepEqual(started, [82]);
  engine.dispose();
});

test('SF2 approximates missing shaker with maracas for percussion only', async () => {
  const { engine, started } = fixture([70]);
  assert.equal(engine.schedule({}, 0, true, 82, 100, 0, .25).length, 1);
  assert.equal(engine.schedule({}, 0, false, 82, 100, 0, .25).length, 0);
  await engine.flush();
  assert.deepEqual(started, [70]);
  engine.dispose();
});

test('SF2 remains silent when neither shaker nor its fallback has a region', async () => {
  const { engine, started } = fixture([81]);
  assert.equal(engine.schedule({}, 0, true, 82, 100, 0, .25).length, 0);
  await engine.flush();
  assert.deepEqual(started, []);
  engine.dispose();
});
