const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const ts = require('typescript');

function fixture(request, preferences = { drumMapping: () => 'auto' }) {
  const signal = value => {
    const read = () => value;
    read.set = next => { value = next; };
    read.asReadonly = () => read;
    return read;
  };
  const angular = { signal, computed: read => read, inject: () => preferences, Injectable: () => type => type };
  const source = fs.readFileSync(path.join(__dirname, '../src/app/core/audio/midi-output.service.ts'), 'utf8');
  const compiled = ts.transpileModule(source, { compilerOptions: {
    target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, experimentalDecorators: true,
  } }).outputText;
  const module = { exports: {} };
  new Function('require', 'module', 'exports', 'navigator', compiled)(name => name.includes('drum-instruments')
    ? require('./music-loader.cjs').loadMusic('drum-instruments.ts')
    : name === './mix-values' ? require('./music-loader.cjs').loadMusic('../audio/mix-values.ts') : angular, module, module.exports,
    request ? { requestMIDIAccess: request } : {});
  return new module.exports.MidiOutputService();
}

function port(id) {
  return { id, name: `Synth ${id}`, state: 'connected', messages: [], cleared: 0, closed: 0,
    async open() {}, async close() { this.closed++; }, clear() { this.cleared++; },
    send(data, timestamp) { this.messages.push({ data, timestamp }); },
  };
}

test('expanded drum identities use project slots for both MIDI messages and follow Auto without editing them', async () => {
  const output = port('expanded');
  const service = fixture(async () => ({ outputs: new Map([[output.id, output]]), addEventListener() {}, removeEventListener() {} }));
  const slots = { 49: 37, 64: 50 };
  const track = { midiChannel: 10, role: 'drums', mix: { program: 0, volumePercent: 100, panPercent: 0 } };
  assert.equal(service.drumLabel(49, slots), 'Crash cymbal · 49');
  await service.refresh(); await service.select(output.id);
  service.schedule(track, 49, 100, 1000, .25, slots);
  assert.deepEqual(output.messages.slice(-2).map(message => message.data), [[0x99, 37, 100], [0x89, 37, 0]]);
  assert.equal(service.drumLabel(49, slots), 'Crash cymbal · 37');
  await service.select(null);
  assert.equal(service.drumLabel(49, slots), 'Crash cymbal · 49');
  assert.deepEqual(slots, { 49: 37, 64: 50 });
  service.ngOnDestroy();
});

test('permission is requested on demand without SysEx, ports update, selection opens and disconnect resets routing', async () => {
  const one = port('one'), two = port('two');
  let changed, calls = 0;
  const service = fixture(async options => {
    calls++;
    assert.deepEqual(options, { sysex: false });
    return { outputs: new Map([[one.id, one], [two.id, two]]),
      addEventListener(type, callback) { assert.equal(type, 'statechange'); changed = callback; },
      removeEventListener() {},
    };
  });
  assert.equal(calls, 0);
  assert.equal(service.label(), 'MIDI Port...');
  await service.refresh();
  await service.refresh();
  assert.equal(calls, 1, 'reuse granted access');
  assert.equal(service.outputs().length, 2);
  await service.select('one');
  assert.equal(service.label(), 'Synth one');
  const track = { midiChannel: 3, role: 'bass', mix: { program: 32, volumePercent: 80, panPercent: 0 } };
  service.schedule(track, 48, 100, 1000, .5);
  assert.deepEqual(one.messages, [
    { data: [0xc2, 32], timestamp: undefined },
    { data: [0xb2, 7, 102], timestamp: undefined },
    { data: [0xb2, 10, 64], timestamp: undefined },
    { data: [0x92, 48, 100], timestamp: 1000 },
    { data: [0x82, 48, 0], timestamp: 1500 },
  ]);
  await service.select('two');
  assert.equal(one.cleared, 1);
  assert.equal(one.closed, 1);
  assert.deepEqual(one.messages.slice(-3).map(message => message.data), [[0xb2, 64, 0], [0xb2, 123, 0], [0xb2, 120, 0]]);
  two.state = 'disconnected'; changed();
  assert.equal(service.outputs().length, 1);
  assert.equal(service.selectedOutput(), null);
  assert.match(service.error(), /disconnected/);
  service.ngOnDestroy();
});

test('Java drum mapping covers the ordered pool, Auto follows routing, and note-on/off and export agree', async () => {
  const { CLASSIC_DRUM_INSTRUMENTS, mapDrumPitch, resolveDrumMapping } = require('./music-loader.cjs').loadMusic('drum-instruments.ts');
  for (const [index, instrument] of CLASSIC_DRUM_INSTRUMENTS.entries()) {
    assert.equal(mapDrumPitch(instrument.midi, 'general-midi'), instrument.midi);
    assert.equal(mapDrumPitch(instrument.midi, 'chromatic'), 36 + index);
  }
  assert.equal(mapDrumPitch(99, 'chromatic'), 99, 'custom pitches outside the pool are preserved');
  assert.equal(resolveDrumMapping('general-midi', true), 'general-midi');
  assert.equal(resolveDrumMapping('chromatic', false), 'chromatic');
  let mode = 'auto';
  const output = port('drums');
  const service = fixture(async () => ({ outputs: new Map([[output.id, output]]), addEventListener() {}, removeEventListener() {} }),
    { drumMapping: () => mode });
  assert.equal(service.drumMapping(), 'general-midi');
  await service.refresh(); await service.select(output.id);
  assert.equal(service.drumMapping(), 'chromatic');
  assert.equal(service.drumLabel(38), 'Snare · 39');
  const track = { midiChannel: 10, role: 'drums', mix: { program: 0, volumePercent: 100, panPercent: 0 } };
  service.schedule(track, 38, 100, 1000, .5);
  assert.deepEqual(output.messages.slice(-2).map(message => message.data), [[0x99, 39, 100], [0x89, 39, 0]]);
  mode = 'general-midi'; service.schedule(track, 38, 100, 2000, .5);
  assert.deepEqual(output.messages.slice(-2).map(message => message.data), [[0x99, 38, 100], [0x89, 38, 0]]);
  mode = 'auto'; await service.select(null);
  assert.equal(service.drumMapping(), 'general-midi');
  service.ngOnDestroy();
  const { Midi } = require('@tonejs/midi');
  const { generateCompositionMidi } = require('./music-loader.cjs').loadMusic('midi-export.ts');
  const { DEFAULT_PROJECT } = require('./music-loader.cjs').loadMusic('../project/project-defaults.ts');
  const original = JSON.stringify(DEFAULT_PROJECT);
  const gm = new Midi(generateCompositionMidi(DEFAULT_PROJECT, 'general-midi'));
  const chromatic = new Midi(generateCompositionMidi(DEFAULT_PROJECT, 'chromatic'));
  for (const [index, exported] of chromatic.tracks.entries()) {
    assert.deepEqual(exported.notes.map(note => note.midi), gm.tracks[index].notes.map(note =>
      exported.channel === 9 ? mapDrumPitch(note.midi, 'chromatic') : note.midi));
  }
  assert.equal(JSON.stringify(DEFAULT_PROJECT), original, 'export preserves stored pitches');
});

test('MIDI volume clamps boosted gain while preserving notes and stored mix', async () => {
  const { loadMusic } = require('./music-loader.cjs');
  const { Midi } = require('@tonejs/midi');
  const { generateCompositionMidi } = loadMusic('midi-export.ts');
  const { DEFAULT_PROJECT } = loadMusic('../project/project-defaults.ts');
  const output = port('volume');
  const service = fixture(async () => ({ outputs: new Map([[output.id, output]]), addEventListener() {}, removeEventListener() {} }));
  await service.refresh();
  await service.select(output.id);
  const baseline = new Midi(generateCompositionMidi(DEFAULT_PROJECT));
  for (const [volumePercent, expected] of [[-10, 0], [0, 0], [50, 64], [100, 127], [100 * 10 ** (6 / 20), 127]]) {
    const project = { ...DEFAULT_PROJECT, tracks: DEFAULT_PROJECT.tracks.map(track => ({
      ...track, mix: { ...track.mix, volumePercent },
    })) };
    const before = JSON.stringify(project);
    output.messages.length = 0;
    service.silence();
    output.messages.length = 0;
    service.configure(project.tracks[0]);
    assert.deepEqual(output.messages.find(message => message.data[1] === 7).data.slice(1), [7, expected]);
    const exported = new Midi(generateCompositionMidi(project));
    for (const [index, track] of exported.tracks.entries()) {
      assert.ok(Math.abs(track.controlChanges[7][0].value * 127 - expected) <= 1);
      assert.deepEqual(track.notes, baseline.tracks[index].notes);
    }
    assert.equal(JSON.stringify(project), before);
  }
  service.ngOnDestroy();
});

test('denial and unsupported browsers report actionable status; retry works and failed opens never select a device', async () => {
  let deny = true;
  const output = port('one');
  const service = fixture(async () => {
    if (deny) throw new DOMException('Denied', 'NotAllowedError');
    return { outputs: new Map([[output.id, output]]), addEventListener() {}, removeEventListener() {} };
  });
  await service.refresh();
  assert.match(service.error(), /site settings/);
  assert.equal(service.busy(), false);
  deny = false;
  await service.refresh();
  assert.equal(service.error(), '');
  output.open = async () => { throw Error('Busy'); };
  await service.select('one');
  assert.equal(service.selectedOutput(), null);
  assert.match(service.error(), /Could not open/);
  service.ngOnDestroy();
  const unsupported = fixture();
  await unsupported.refresh();
  assert.match(unsupported.error(), /unavailable|HTTPS/);
  unsupported.ngOnDestroy();
});
test('phrase MIDI exports draft tracks despite mute/solo and preserves notes beyond phrase bounds', () => {
  const { Midi } = require('@tonejs/midi');
  const { loadMusic } = require('./music-loader.cjs');
  const { generatePhraseMidi } = loadMusic('midi-export.ts');
  const { DEFAULT_PROJECT } = loadMusic('../project/project-defaults.ts');
  const bass = DEFAULT_PROJECT.tracks.find(t => t.role === 'bass');
  const drum = DEFAULT_PROJECT.tracks.find(t => t.role === 'drums');
  const notes = [{ id:'draft', midi:66, startBeat:1, durationBeats:2, velocity:93 },
    { id:'tail', midi:67, startBeat:7, durationBeats:4, velocity:100 },
    { id:'outside', midi:68, startBeat:9, durationBeats:1, velocity:100 }];
  const midi = new Midi(generatePhraseMidi('Draft', 87, [
    { track:{...bass,mix:{...bass.mix,muted:true,solo:false}}, notes },
    { track:drum,notes:[{id:'hit',midi:38,startBeat:0,durationBeats:.25,velocity:100}] },
    { track:{...drum,id:'empty',name:'Empty drum'},notes:[] },
  ], 8, 'chromatic'));
  assert.ok(Math.abs(midi.header.tempos[0].bpm-87)<.001);
  assert.equal(midi.tracks.length,3);
  assert.equal(midi.tracks[0].channel,bass.midiChannel-1);
  assert.equal(midi.tracks[0].instrument.number,bass.mix.program);
  assert.deepEqual(midi.tracks[0].notes.map(n=>[n.midi,n.ticks/midi.header.ppq,n.durationTicks/midi.header.ppq]),[[66,1,2],[67,7,4],[68,9,1]]);
  assert.ok(Math.abs(midi.tracks[0].notes[0].velocity-93/127)<.01);
  assert.equal(midi.tracks[1].channel,drum.midiChannel-1);
  assert.equal(midi.tracks[1].notes[0].midi,39);
});

test('song and phrase MIDI exports use the requested PPQ without changing musical positions', () => {
  const { loadMusic } = require('./music-loader.cjs');
  const { Midi } = require('@tonejs/midi');
  const { generateCompositionMidi, generatePhraseMidi } = loadMusic('midi-export.ts');
  const { DEFAULT_PROJECT } = loadMusic('../project/project-defaults.ts');
  for (const ppq of [96, 480, 960, 1920]) {
    const song = new Midi(generateCompositionMidi(DEFAULT_PROJECT, 'general-midi', ppq));
    assert.equal(song.header.ppq, ppq);
    const phrase = new Midi(generatePhraseMidi('Test', 120, [{ track: DEFAULT_PROJECT.tracks[0], notes: [
      { startBeat: 1.5, durationBeats: .25, midi: 60, velocity: 100 }
    ] }], 4, 'general-midi', ppq));
    assert.equal(phrase.header.ppq, ppq);
    const note = phrase.tracks[0].notes[0];
    assert.equal(note.ticks, 1.5 * ppq);
    assert.equal(note.durationTicks, .25 * ppq);
  }
});
