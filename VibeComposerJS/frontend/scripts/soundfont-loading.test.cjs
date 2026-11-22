const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');
const { Sf2Bank } = require('./music-loader.cjs').loadMusic('../audio/sf2-resolver.ts');
const bytes = fs.readFileSync(path.join(__dirname, '../../Super_Nintendo_Unofficial_update.sf2'));
function fixture(calibrateInstruments = async () => ({})) {
  const signal = value => {
    const read = () => value;
    read.set = next => { value = next; };
    read.update = fn => { value = fn(value); };
    read.asReadonly = () => read;
    return read;
  };
  const project = signal({});
  const projectActivation = signal(0);
  const projects = { project, projectActivation, setSoundbankName: soundbankName => project.set({ soundbankName }) };
  let runEffect;
  const module = { exports: {} };
  const source = fs.readFileSync(path.join(__dirname, '../src/app/core/audio/instrument-settings.service.ts'), 'utf8');
  const compiled = ts.transpileModule(source, {compilerOptions: {target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, experimentalDecorators: true}}).outputText;
  const dependencies = {
    '@angular/core': { signal, computed: fn => fn, Injectable: () => type => type, inject: () => projects,
      effect: fn => { runEffect = fn; }, untracked: fn => fn() },
    '../project/project.service': { ProjectService: class {} },
    './instrument-loudness': { calibrateInstruments },
    './sf2-resolver': { Sf2Bank },
  };
  new Function('require', 'module', 'exports', compiled)(name => dependencies[name], module, module.exports);
  const service = new module.exports.InstrumentSettingsService();
  runEffect();
  return { service, projects, runEffect, max: module.exports.MAX_SOUNDFONT_BYTES };
}
const file = name => new File([bytes], name);

test('enabling normalization prepares immediately and retains readiness without playing', async () => {
  let finish, report, measuredBank;
  const {service} = fixture((bank, progress) => {
    measuredBank = bank; report = progress;
    return new Promise(resolve => { finish = resolve; });
  });
  await service.load(file('one.sf2'));
  const preparing = service.setNormalization(true);
  await new Promise(setImmediate);
  assert.equal(measuredBank, service.selectedBank());
  report('Calibrating instrument loudness: 1/128');
  assert.match(service.calibrationStatus(), /one.sf2: Calibrating/);
  finish({}); await preparing;
  assert.equal(service.calibrationStatus(), 'Loudness normalization ready for one.sf2.');
});

test('disabling or switching banks ignores obsolete calibration progress and completion', async () => {
  const jobs = [];
  const {service, runEffect} = fixture((bank, progress) => new Promise((resolve, reject) => jobs.push({bank, progress, resolve, reject})));
  await service.load(file('one.sf2'));
  const first = service.setNormalization(true);
  await new Promise(setImmediate);
  await service.load(file('two.sf2'));
  runEffect();
  await new Promise(setImmediate);
  assert.equal(jobs.length, 2, 'new bank prepares without Play');
  const before = service.calibrationStatus();
  jobs[0].progress('obsolete progress'); jobs[0].resolve({}); await first;
  assert.equal(service.calibrationStatus(), before);
  await service.setNormalization(false);
  jobs[1].progress('obsolete progress'); jobs[1].reject(Error('obsolete failure'));
  await new Promise(setImmediate);
  assert.equal(service.calibrationStatus(), '');
});

test('calibration errors remain visible and enabling again retries', async () => {
  let attempts = 0;
  const {service} = fixture(async () => { if (++attempts === 1) throw Error('render failed'); return {}; });
  await service.setNormalization(true);
  assert.match(service.calibrationStatus(), /render failed.*retry/);
  await service.setNormalization(true);
  assert.equal(service.calibrationStatus(), 'Loudness normalization ready for TinySynth.');
  assert.equal(attempts, 2);
});
test('loaded soundfonts appear in transport options, select by stable identity and evict oldest on fourth success', async () => {
  const {service} = fixture();
  for (const name of ['one.sf2', 'two.sf2', 'three.sf2']) await service.load(file(name));
  const ids = service.loaded().map(b => b.id);
  service.select(ids[0]);
  await service.load(file('four.sf2'));
  assert.deepEqual(service.loaded().map(b => b.name), ['two.sf2','three.sf2','four.sf2']);
  assert.equal(service.options().length, 5);
  assert.equal(service.selectedBank(), service.loaded()[2].bank);
  assert.match(service.message(), /Unloaded one.sf2/);
  service.select(ids[0]);
  assert.equal(service.kind(), service.loaded()[2].id, 'evicted IDs cannot be selected');
  service.unload(service.kind());
  assert.equal(service.kind(), 'synth');
  assert.equal(service.selectedBank(), undefined);
});

test('bundled chiptune selects without a soundfont and prepares its own normalization', async () => {
  let selectedKind;
  const {service} = fixture(async (bank, progress, kind) => { assert.equal(bank, undefined); selectedKind = kind; });
  service.select('chiptune');
  assert.equal(service.kind(), 'chiptune');
  assert.equal(service.selectedBank(), undefined);
  assert.ok(service.options().some(option => option.value === 'chiptune'));
  await service.setNormalization(true);
  assert.equal(selectedKind, 'chiptune');
  assert.equal(service.calibrationStatus(), 'Loudness normalization ready for Chiptune (TinySynth).');
});
test('projects resolve their saved soundfont by filename as banks become available', async () => {
  const { service, projects } = fixture();
  await service.load(file('other.sf2'));
  const other = projects.project();
  projects.project.set({ soundbankName: 'saved.sf2' });
  assert.equal(service.kind(), 'synth', 'a different loaded bank cannot satisfy the saved name');

  const loading = service.load(file('saved.sf2'));
  projects.projectActivation.update(n => n + 1);
  projects.project.set(other);
  await loading;
  assert.equal(service.selectedBank(), service.loaded()[0].bank, 'pending load does not change the newly active project');
  projects.project.set({ soundbankName: 'saved.sf2' });
  assert.equal(service.selectedBank(), service.loaded()[1].bank);

  const oldId = service.kind();
  await service.load(file('saved.sf2'));
  assert.notEqual(service.kind(), oldId);
  assert.deepEqual(service.loaded().map(bank => bank.name), ['other.sf2', 'saved.sf2']);
  service.unload(service.kind());
  assert.equal(service.kind(), 'synth');
  assert.equal(projects.project().soundbankName, 'saved.sf2');
});

test('250 MiB is accepted; oversized files are rejected before reading or evicting a bank', async () => {
  const {service,max} = fixture();let read = false;
  await service.load({name:'large.sf2',size:max+1,arrayBuffer:async()=>{read=true;throw Error('must not read');}});
  assert.equal(read,false);assert.equal(service.loaded().length,0);assert.match(service.message(),/250 MiB/);
  const data = bytes.buffer.slice(bytes.byteOffset,bytes.byteOffset+bytes.byteLength);
  await service.load({name:'boundary.sf2',size:max,arrayBuffer:async()=>data});
  assert.equal(service.loaded().length,1);
  assert.equal(service.loaded()[0].size,max);
});
test('bad files and read failures preserve all loaded banks and the selection', async () => {
  const {service} = fixture();
  for (const name of ['one.sf2','two.sf2','three.sf2']) await service.load(file(name));
  const previous = service.loaded(), selected = service.kind();
  await service.load(new File(['invalid'], 'broken.sf2'));
  assert.equal(service.loaded(),previous);assert.equal(service.kind(),selected);assert.match(service.message(),/Could not load/);
  await service.load({name:'unreadable.sf2',size:100,arrayBuffer:async()=>{throw Error('read failed');}});
  assert.equal(service.loaded(),previous);assert.equal(service.kind(),selected);assert.equal(service.busy(),false);
  await service.load(new File([bytes], 'wrong.wav'));
  assert.match(service.message(),/SF2/);assert.equal(service.loaded(),previous);
});
