const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const ts = require('typescript');

function fixture({ loop = false } = {}) {
  let runningEffect;
  const effect = (callback) => {
    const run = () => { runningEffect = run; try { callback(); } finally { runningEffect = undefined; } };
    run();
  };
  const signal = (value) => {
    const observers = new Set();
    const read = () => { if (runningEffect) observers.add(runningEffect); return value; };
    read.set = (next) => { if (value === next) return; value = next; for (const observer of observers) observer(); };
    read.asReadonly = () => read;
    return read;
  };
  const untracked = (callback) => {
    const previous = runningEffect;
    runningEffect = undefined;
    try { return callback(); } finally { runningEffect = previous; }
  };
  const parameter = () => ({ value: 0, events: [],
    setValueAtTime(value, time) { this.value = value; this.events.push({ value, time }); },
    linearRampToValueAtTime(value, time) { this.events.push({ value, time }); },
    exponentialRampToValueAtTime(value, time) { this.events.push({ value, time }); },
    setTargetAtTime(value, time) { this.events.push({ value, time }); }, cancelScheduledValues() {},
  });
  const sources = [];
  const node = () => ({ gain: parameter(), pan: parameter(), frequency: parameter(),
    connect() {}, disconnect() {},
    start(time) { this.startTime = time; }, stop(time) { this.stopTime = time; },
  });
  const context = { currentTime: 0, state: 'running', destination: {},
    createGain: node, createStereoPanner: node,
    createOscillator() { const source = node(); sources.push(source); return source; },
  };
  const instruments = { kind: signal('synth'), selectedBank: () => undefined };
  const midi = { selectedOutput: signal(null), drumMapping: signal('general-midi'), schedule() {}, configure() {}, silence() {} };
  const angular = { Injectable: () => (type) => type, Component: () => (type) => type,
    signal, effect, untracked, inject: (token) => token.name === 'InstrumentSettingsService' ? instruments : midi, computed: (read) => read, ChangeDetectionStrategy: { OnPush: 0 } };
  const load = (file, dependencies) => {
    const source = fs.readFileSync(path.join(__dirname, '../src/app', file), 'utf8');
    const compiled = ts.transpileModule(source, { compilerOptions: {
      target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, experimentalDecorators: true,
    } }).outputText;
    const module = { exports: {} };
    new Function('require', 'module', 'exports', 'setInterval', 'clearInterval', 'setTimeout', compiled)(
      (name) => dependencies[name], module, module.exports, () => 1, () => {}, () => 1);
    return module.exports;
  };
  const { AudioPlaybackService } = load('core/audio/audio-playback.service.ts', {
    './mix-values': require('./music-loader.cjs').loadMusic('../audio/mix-values.ts'),
    '../music/arrangement-generator': require('./music-loader.cjs').loadMusic('arrangement-generator.ts'),
    '../project/project-drum-kit': require('./music-loader.cjs').loadMusic('../project/project-drum-kit.ts'),
    '../music/phrase-inputs': require('./music-loader.cjs').loadMusic('phrase-inputs.ts'),
    '../music/phrase-bounds': require('./music-loader.cjs').loadMusic('phrase-bounds.ts'),
    '../music/section-context': require('./music-loader.cjs').loadMusic('section-context.ts'),
    '@angular/core': angular,
    './create-instrument-engine': {},
    './instrument-settings.service': { InstrumentSettingsService: class InstrumentSettingsService {} },
    './midi-output.service': { MidiOutputService: class {} },
    '../music/phrase': { layOutTrackPhrase: (project, track) =>
      !project.arrangement.length || project.arrangement[0].trackPresence[track.id] === false ? [] : track.notes },
  });
  const playback = new AudioPlaybackService();
  // Existing scenarios explicitly start without looping; the default is tested separately.
  if (!loop) playback.toggleLoop();
  playback.context = context;
  playback.engineKind = 'synth';
  playback.synth = { schedule(destination, program, percussion, midi, velocity, start, duration) {
    const source = context.createOscillator();
    source.program = program;
    source.percussion = percussion;
    source.destination = destination;
    source.frequency.value = 440 * 2 ** ((midi - 69) / 12);
    source.start(start);
    source.stop(start + duration + 0.02);
    return [{ stopTime: start + duration + 0.02, stop: time => source.stop(time),
      onEnded: callback => { source.onended = callback; }, dispose() {} }];
  } };
  const project = { name: 'Song', progression: [1,5,6,4], tempoBpm: 120, arrangement: [{ measures: 4, trackPresence: {} }],
    tracks: [{ id: 'bass', role: 'bass', notes: [
      { id: 'sustain', midi: 48, startBeat: 0, durationBeats: 8, velocity: 100 },
      { id: 'next', midi: 50, startBeat: 4, durationBeats: 1, velocity: 100 },
    ], mix: { program: 32, volumePercent: 80, panPercent: 0, muted: false, solo: false } }] };
  return { playback, context, sources, project, angular, load, midi, instruments };
}

test('routing from MIDI to the app prepares an unused engine and keeps the musical position', async () => {
  const { playback, context, project, midi } = fixture();
  const synth = playback.synth;
  playback.synth = undefined;
  let preparations = 0;
  playback.prepareInstrument = async () => { preparations++; playback.synth = synth; };
  midi.selectedOutput.set({ id: 'external' });
  await playback.start(project);
  assert.equal(preparations, 0);
  context.currentTime = 1;
  const beat = playback.currentSongBeat();
  midi.selectedOutput.set(null);
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(preparations, 1);
  assert.equal(playback.state(), 'playing');
  assert.equal(playback.beat(), beat);
  playback.stop();
});

test('draft playback and audition share temporary drum slots without editing project assignments', async () => {
  const { playback, project, midi } = fixture();
  const track = project.tracks[0];
  track.role = 'drums'; track.generatorSettings = { pitch: 36 }; track.midiChannel = 10;
  project.editedPhrases = {}; project.drumSlots = { 36: 37 };
  const notes = [49, 51].map((pitch, index) => ({ id: `draft-${index}`, trackId: track.id,
    midi: pitch, startBeat: 0, durationBeats: .25, velocity: 100 }));
  playback.setPhrasePreview(project, { startBeat: 0, endBeat: 4, trackIds: [track.id], notes });
  const sent = [];
  midi.schedule = (...args) => sent.push(args);
  midi.selectedOutput.set({ id: 'external' });
  await playback.start(project);
  assert.equal(sent.length, 2);
  const slots = sent[0][5];
  assert.notEqual(slots[49], slots[51]);
  await playback.audition(track, [notes[1]], project);
  assert.equal(sent.at(-1)[5][51], slots[51]);
  assert.deepEqual(project.drumSlots, { 36: 37 });
  playback.stop();
});

test('fractional loop boundaries do not move note times one entire cycle into the future', async () => {
  const { playback, context, project } = fixture({ loop: true });
  project.tempoBpm = 70;
  project.tracks[0].notes = [
    { id: 'start', midi: 60, startBeat: 0, durationBeats: 1, velocity: 100 },
    { id: 'tail', midi: 48, startBeat: 88, durationBeats: 1.033, velocity: 100 },
  ];
  await playback.start(project);
  const length = 89.033, seconds = length * 60 / 70;
  assert.equal(playback.durationBeats(), length);
  for (let cycle = 1; cycle <= 100; cycle++) {
    const beat = cycle * length;
    const boundaryTime = cycle * seconds;
    assert.ok(Math.abs(playback.absoluteSeconds(beat) - boundaryTime) < 1e-8, 'forward conversion at cycle ' + cycle);
    for (const delta of [-0.000001, 0, 0.000001]) {
      context.currentTime = boundaryTime + delta;
      const absoluteBeat = playback.currentAbsoluteBeat();
      assert.ok(Math.abs(playback.absoluteSeconds(absoluteBeat) - context.currentTime) < 1e-8, 'clock round trip at cycle ' + cycle);
      assert.ok(Math.abs(absoluteBeat - (beat + delta * 70 / 60)) < 1e-8, 'inverse conversion at cycle ' + cycle);
      assert.ok(playback.currentSongBeat() >= -1e-8 && playback.currentSongBeat() <= length + 1e-8);
    }
  }
});

test('fractional partial loops retain their phase through tempo boundaries and negative pickups', async () => {
  const { beatToSeconds } = require('./music-loader.cjs').loadMusic('section-context.ts');
  for (const startBeat of [-1.25, 0, 3.5]) {
    const { playback, context, project } = fixture({ loop: true });
    project.arrangement = [
      { measures: 4, trackPresence: {}, context: { tempoBpm: 70 } },
      { measures: 4, trackPresence: {}, context: { tempoBpm: 95 } },
    ];
    project.tracks[0].notes = [
      { id: 'pickup', midi: 60, startBeat: -2, durationBeats: .5, velocity: 100 },
      { id: 'tail', midi: 48, startBeat: 88, durationBeats: 1.033, velocity: 100 },
    ];
    playback.setLoopRange({ startBeat, endBeat: 89.033 });
    await playback.start(project);
    const length = 89.033 - startBeat;
    const firstSeconds = beatToSeconds(playback.tempoMap, startBeat);
    const cycleSeconds = beatToSeconds(playback.tempoMap, 89.033) - firstSeconds;
    for (let cycle = 1; cycle <= 100; cycle++) {
      const beat = startBeat + cycle * length;
      const seconds = firstSeconds + cycle * cycleSeconds;
      assert.ok(Math.abs(playback.absoluteSeconds(beat) - seconds) < 1e-8);
      context.currentTime = playback.originTime + seconds;
      assert.ok(Math.abs(playback.currentAbsoluteBeat() - beat) < 1e-8);
      assert.ok(Math.abs(playback.absoluteSeconds(playback.currentAbsoluteBeat()) - seconds) < 1e-8);
    }
  }
});

test('the first note of fractional loop three is queued before its boundary instead of waiting another loop', async () => {
  const { playback, context, sources, project } = fixture({ loop: true });
  project.tempoBpm = 70;
  project.tracks[0].notes = [
    { id: 'start', midi: 60, startBeat: 0, durationBeats: 1, velocity: 100 },
    { id: 'tail', midi: 48, startBeat: 88, durationBeats: 1.033, velocity: 100 },
  ];
  await playback.start(project);
  const boundaryTime = 3 * 89.033 * 60 / 70;
  context.currentTime = boundaryTime - 0.1;
  playback.tick();
  assert.ok(sources.some(source => Math.abs(source.startTime - boundaryTime) < 1e-8
    && source.frequency.value === 440 * 2 ** ((60 - 69) / 12)), 'loop-three note starts at the real boundary');
});

test('worker clock schedules playback, releases on pause, and reports worker failures', async (t) => {
  const { playback, context, project } = fixture({ loop: true });
  const previousWorker = globalThis.Worker, previousDocument = globalThis.document;
  t.after(() => { globalThis.Worker = previousWorker; globalThis.document = previousDocument; });
  const workers = [];
  globalThis.document = { baseURI: 'https://example.test/composer/' };
  globalThis.Worker = class {
    constructor(url) { this.url = url.href; this.messages = []; workers.push(this); }
    postMessage(message) { this.messages.push(message); }
    terminate() { this.terminated = true; }
  };
  await playback.start(project);
  assert.equal(playback.clockMode, 'worker');
  assert.equal(playback.scheduler, undefined, 'window intervals are not needed');
  assert.equal(workers[0].url, 'https://example.test/composer/audio-playback-clock.js');
  assert.deepEqual(workers[0].messages[0], { type: 'start', intervalMs: 25 });
  context.currentTime = .05;
  workers[0].onmessage({ data: 'tick' });
  assert.equal(playback.beat(), .1);
  assert.equal(workers[0].messages.at(-1), 'ack');
  const staleTick = workers[0].onmessage;
  playback.pause();
  assert.equal(workers[0].terminated, true);
  assert.equal(workers[0].onmessage, null);
  assert.equal(playback.clockMode, 'idle');
  await playback.toggle(project);
  assert.equal(workers.length, 2);
  const messages = workers[0].messages.length;
  staleTick({ data: 'tick' });
  assert.equal(workers[0].messages.length, messages, 'stale worker messages are ignored');
  workers[1].onerror();
  assert.equal(playback.state(), 'paused');
  assert.equal(workers[1].terminated, true);
  assert.match(playback.error(), /audio clock failed/i);
});

test('worker tick backlog stays bounded while the main thread is busy', () => {
  let interval;
  const messages = [];
  const self = { postMessage: message => messages.push(message) };
  require('node:vm').runInNewContext(fs.readFileSync(path.join(__dirname, '../public/audio-playback-clock.js'), 'utf8'), {
    self, setInterval: callback => { interval = callback; return 1; }, clearInterval() {},
  });
  self.onmessage({ data: { type: 'start', intervalMs: 25 } });
  for (let index = 0; index < 1000; index++) interval();
  assert.deepEqual(messages, ['tick'], 'a blocked UI does not accumulate thousands of callbacks');
  self.onmessage({ data: 'ack' });
  interval();
  assert.deepEqual(messages, ['tick', 'tick']);
});

test('context suspension is detected from its event without waiting for a timer tick', async (t) => {
  const { playback, context, project } = fixture();
  const previousContext = globalThis.AudioContext;
  t.after(() => { globalThis.AudioContext = previousContext; });
  let changed;
  context.addEventListener = (type, callback) => { assert.equal(type, 'statechange'); changed = callback; };
  globalThis.AudioContext = class { constructor() { return context; } };
  playback.context = undefined;
  await playback.start(project);
  context.state = 'suspended';
  changed();
  assert.equal(playback.state(), 'paused');
  assert.match(playback.error(), /interrupted/i);
});

test('delayed scheduler restores sustained notes at the current loop position without replaying missed cycles', async () => {
  const { playback, context, sources, project } = fixture({ loop: true });
  await playback.start(project);
  // A long background delay passes thousands of loops, then lands inside a sustain.
  context.currentTime = 8 * 10000 + 1;
  playback.tick();
  assert.equal(playback.state(), 'playing');
  assert.equal(playback.beat(), 2);
  assert.ok(sources.length >= 2 && sources.length <= 4, 'current sustain and bounded future notes replace the exhausted queue');
  assert.equal(sources[1].startTime, context.currentTime + 0.003);
  assert.ok(Math.abs(sources[1].stopTime - (context.currentTime + 3.023)) < 1e-8);
  const count = sources.length;
  context.currentTime += 0.025;
  playback.tick();
  assert.equal(sources.length, count, 'normal ticks do not duplicate the recovered sustain');
});

test('delayed scheduler does not replay missed drums or notes that have already ended', async () => {
  const { playback, context, sources, project } = fixture({ loop: true });
  project.tracks[0].role = 'drums';
  await playback.start(project);
  context.currentTime = 1;
  playback.tick();
  assert.equal(sources.filter(source => source.startTime <= context.currentTime).length, 1, 'missed drums are not replayed');
  context.currentTime = 2;
  playback.tick();
  assert.equal(sources.length, 2, 'the next drum onset still plays');
});

test('a 349ms underrun preserves the graph, sounding voices and clock', async () => {
  const { playback, context, sources, project } = fixture({ loop: true });
  await playback.start(project);
  const master = playback.master, origin = playback.originTime;
  const source = sources[0], stopTime = source.stopTime;
  context.currentTime = 0.34933333333333394;
  playback.tick();
  assert.equal(playback.master, master);
  assert.equal(playback.originTime, origin);
  assert.equal(source.stopTime, stopTime, 'held audio is not stopped to recover a late scheduler');
  assert.equal(sources.length, 1, 'a held note is not retriggered');
});

test('lookahead is capped after a stall and returns to normal without cancelling queued voices', async () => {
  const { playback, context, project, sources } = fixture({ loop: true });
  await playback.start(project);
  context.currentTime = 10;
  playback.tick();
  assert.equal(playback.scheduleAheadSeconds, 1, 'a long stall cannot allocate five seconds of future voices');
  assert.match(playback.error(), /scheduling was delayed/);
  const queued = sources.map(source => ({ source, stopTime: source.stopTime }));
  for (let tick = 1; tick <= 40; tick++) {
    context.currentTime = 10 + tick * 0.25;
    playback.tick();
  }
  assert.equal(playback.scheduleAheadSeconds, 1, 'persistently slow callbacks retain their scheduling headroom');
  for (let tick = 1; tick <= 400; tick++) {
    context.currentTime = 20 + tick * 0.025;
    playback.tick();
    if (tick < 200) assert.equal(playback.scheduleAheadSeconds, 1, 'decay requires five consecutive healthy seconds');
  }
  assert.ok(Math.abs(playback.scheduleAheadSeconds - 0.18) < 1e-8);
  assert.equal(playback.error(), '', 'the transient underrun message clears after healthy scheduling');
  for (const { source, stopTime } of queued) assert.equal(source.stopTime, stopTime, 'shrinking does not stop existing voices');
});

test('hidden playback keeps scheduling audio and refreshes the visual playhead on return', async (t) => {
  const { playback, context, project, sources } = fixture({ loop: true });
  const previousDocument = globalThis.document;
  t.after(() => { globalThis.document = previousDocument; });
  globalThis.document = { visibilityState: 'hidden' };
  await playback.start(project);
  const publish = playback.position.set;
  let publications = 0;
  playback.position.set = value => { publications++; publish(value); };
  for (let tick = 1; tick <= 90; tick++) {
    context.currentTime = tick * 0.025;
    playback.tick();
  }
  assert.equal(playback.state(), 'playing');
  assert.equal(publications, 0, 'hidden scheduler ticks do not drive UI signals');
  assert.equal(playback.beat(), 0);
  assert.equal(sources.length, 2, 'the later note still reaches the audio engine');
  globalThis.document.visibilityState = 'visible';
  playback.foreground();
  assert.equal(publications, 1);
  assert.equal(playback.beat(), 4.5, 'returning to the tab immediately publishes the real song position');
});

test('late MIDI scheduling preserves held notes and does not send all-notes-off', async () => {
  const { playback, context, project, midi } = fixture({ loop: true });
  const sent = [];
  let silenced = 0;
  midi.schedule = (...args) => sent.push(args);
  midi.silence = () => silenced++;
  midi.selectedOutput.set({ id: 'external' });
  await playback.start(project);
  const beforeSilenced = silenced;
  context.currentTime = 0.34933333333333394;
  playback.tick();
  assert.equal(sent.length, 1, 'the held MIDI note is not retriggered');
  assert.equal(silenced, beforeSilenced);
});

test('late scheduling in a later partial loop keeps its absolute phase without replaying missed cycles', async () => {
  const { playback, context, sources, project } = fixture({ loop: true });
  playback.setLoopRange({ startBeat: 4, endBeat: 8 });
  await playback.start(project);
  const origin = playback.originTime;
  context.currentTime = 20.25;
  playback.tick();
  assert.equal(playback.beat(), 4.5);
  assert.equal(playback.originTime, origin);
  assert.equal(sources[1].startTime, context.currentTime + 0.003);
  assert.ok(Math.abs(sources[1].stopTime - (context.currentTime + 0.273)) < 1e-8);
  const count = sources.length;
  context.currentTime += 0.025;
  playback.tick();
  assert.equal(sources.length, count, 'recovery does not duplicate future onsets');
});

test('repeated one-second timer delays expand the queue instead of losing percussion between ticks', async () => {
  const { playback, context, sources, project } = fixture({ loop: true });
  project.tracks[0].role = 'drums';
  project.tracks[0].notes = Array.from({ length: 32 }, (_, index) => ({
    id: String(index), midi: 36, startBeat: index * 0.5, durationBeats: 0.1, velocity: 100,
  }));
  await playback.start(project);
  for (let second = 1; second <= 10; second++) {
    context.currentTime = second;
    playback.tick();
    for (const offset of [0.25, 0.5, 0.75]) {
      assert.ok(sources.some(source => Math.abs(source.startTime - (second + offset)) < 1e-8
        && source.stopTime > source.startTime), 'future percussion is queued before the next delayed tick');
    }
  }
  assert.equal(playback.state(), 'playing');
  assert.ok(playback.scheduleAheadSeconds >= 1);
});

test('audio suspension and interruption pause the transport and Play can resume', async () => {
  for (const state of ['suspended', 'interrupted']) {
    const { playback, context, project } = fixture({ loop: true });
    await playback.start(project);
    context.currentTime = 1;
    context.state = state;
    playback.tick();
    assert.equal(playback.state(), 'paused');
    assert.equal(playback.beat(), 2);
    assert.match(playback.error(), /interrupted/i);
    context.resume = async () => { context.state = 'running'; };
    await playback.toggle(project);
    assert.equal(playback.state(), 'playing');
    assert.equal(playback.beat(), 2);
    assert.equal(playback.error(), '');
  }
});

test('scheduler failures pause playback and expose the error instead of silently advancing', async () => {
  const { playback, context, project } = fixture({ loop: true });
  await playback.start(project);
  playback.synth.schedule = () => { throw new Error('Audio renderer failed'); };
  context.currentTime = 2;
  playback.tick();
  assert.equal(playback.state(), 'paused');
  assert.match(playback.error(), /Audio renderer failed/);
});

test('phrase drafts replace only their track and absolute window on start, seek, live editing and cancel', async () => {
  const { playback, project, context, sources } = fixture();
  const saved = JSON.stringify(project);
  project.tracks.push({ ...project.tracks[0], id: 'other' });
  const draft = { startBeat: 0, endBeat: 4, trackIds: ['bass'], notes: [
    { id: 'draft', trackId: 'bass', midi: 60, startBeat: 0, durationBeats: 8, velocity: 87 },
  ] };
  playback.setPhrasePreview(project, draft);
  await playback.start(project);
  assert.deepEqual(playback.notes.filter(note => note.trackId === 'bass').map(n => n.midi), [60, 50]);
  assert.deepEqual(playback.notes.filter(note => note.trackId === 'other').map(n => n.midi), [48, 50]);
  assert.ok(sources.some(source => source.frequency.value === 440 * 2 ** ((60 - 69) / 12)), 'synth receives the sounding draft pitch');
  context.currentTime = 1;
  playback.toggleLive();
  playback.setPhrasePreview(project, { ...draft, notes: [{ ...draft.notes[0], midi: 62 }] });
  assert.equal(playback.state(), 'playing');
  assert.equal(playback.beat(), 2);
  assert.equal(playback.notes.find(n => n.id === 'draft').midi, 62, 'draft updates remain audible with LIVE disabled');
  playback.seek(project, 1);
  assert.equal(playback.notes.find(n => n.id === 'draft').midi, 62);
  playback.pause();
  playback.setPhrasePreview(project, { ...draft, notes: [] });
  assert.equal(playback.state(), 'paused');
  await playback.toggle(project);
  assert.deepEqual(playback.notes.filter(n => n.trackId === 'bass').map(n => n.midi), [50], 'deleting all draft notes means silence inside the window');
  playback.setPhrasePreview(project, null);
  assert.deepEqual(playback.notes.filter(n => n.trackId === 'bass').map(n => n.midi), [48, 50], 'cancel restores saved notes even with LIVE off');
  playback.stop();
  assert.equal(JSON.stringify({ ...project, tracks: project.tracks.slice(0, 1) }), saved, 'preview does not mutate the project');
});

test('playback includes pickups and post-end notes without shifting the song origin', async () => {
  const { playback, project, context, sources } = fixture();
  project.arrangement = [{ measures: 1, trackPresence: {} }];
  project.tracks[0].notes = [
    { id: 'pickup', midi: 48, startBeat: -2, durationBeats: 1, velocity: 100 },
    { id: 'late', midi: 50, startBeat: 5, durationBeats: 2, velocity: 100 },
  ];
  await playback.start(project);
  assert.equal(playback.beat(), -2);
  assert.equal(playback.durationBeats(), 7);
  assert.equal(sources.length, 1);
  context.currentTime = 1;
  playback.tick();
  assert.equal(playback.beat(), 0);
  context.currentTime = 3.5;
  playback.tick();
  assert.equal(playback.beat(), 5);
  assert.equal(sources.length, 2);
  context.currentTime = 4.6;
  playback.tick();
  assert.equal(playback.state(), 'stopped');
  assert.equal(playback.beat(), 7);
});

test('MIDI playback uses assigned tracks and timestamps, respects mute, and clears notes on pause and routing changes', async () => {
  const { playback, context, sources, project, midi } = fixture();
  const sent = [];
  let silenced = 0;
  midi.schedule = (...args) => sent.push(args);
  midi.silence = () => silenced++;
  midi.selectedOutput.set({ id: 'external' });
  const before = performance.now();
  await playback.start(project);
  assert.equal(sources.length, 0, 'external output replaces internal synth');
  assert.equal(sent.length, 1);
  assert.equal(sent[0][0], project.tracks[0]);
  assert.equal(sent[0][1], 48);
  assert.ok(sent[0][3] >= before && sent[0][3] < performance.now() + 20);
  assert.equal(sent[0][4], 4, 'sustain duration follows tempo map');
  project.tracks[0].mix.muted = true;
  playback.seek(project, 4);
  assert.equal(sent.length, 1, 'muted MIDI track emits no notes');
  project.tracks[0].mix.muted = false;
  context.currentTime = .2;
  const prior = silenced;
  playback.pause();
  assert.ok(silenced > prior, 'pause clears queued MIDI and held notes');
  await playback.start(project);
  midi.selectedOutput.set(null);
  assert.ok(sources.length > 0, 'routing back resumes internal audio');
  playback.stop();
});

test('P5f tempo boundaries integrate sustained notes, transport, partial loops and LIVE tempo reload', async () => {
  const { playback,project,context,sources }=fixture();
  project.arrangement=[{ measures:1,trackPresence:{},context:{tempoBpm:120} },{measures:1,trackPresence:{},context:{tempoBpm:60}}];
  project.tracks[0].notes=[{id:'crossing',midi:48,startBeat:2,durationBeats:4,velocity:100}];
  await playback.start(project,2);
  assert.equal(playback.state(),'playing');
  assert.equal(playback.durationBeats(),8);
  assert.ok(Math.abs(sources[0].stopTime-3.023)<1e-9,'note crosses both tempo segments');
  context.currentTime=1.5; playback.tick(); assert.equal(playback.beat(),4.5);
  playback.reload({ ...project, arrangement: project.arrangement.map((section, index) => index === 1
    ? { ...section, context: { ...section.context, tempoBpm: 120 } } : section) });
  assert.equal(playback.beat(),4.5); context.currentTime=2; playback.tick(); assert.equal(playback.beat(),5.5);
  playback.stop(); context.currentTime=0; project.arrangement[1].context.tempoBpm=60;
  playback.setLoopRange({startBeat:2,endBeat:6}); playback.toggleLoop(); await playback.start(project);
  context.currentTime=3.1; playback.tick(); assert.ok(Math.abs(playback.beat()-2.2)<1e-9);
  context.currentTime=6.1; playback.tick(); assert.ok(Math.abs(playback.beat()-2.2)<1e-9,'later cycles reuse the selected tempo segments');
  playback.stop();
});

test('seeking while stopped positions the song without audio and Play starts there', async () => {
  const { playback, project, sources } = fixture();
  playback.seek(project, 6);
  assert.equal(playback.state(), 'stopped');
  assert.equal(playback.beat(), 6);
  assert.equal(sources.length, 0);
  await playback.toggle(project);
  assert.equal(playback.beat(), 6);
  assert.equal(playback.state(), 'playing');
  assert.equal(sources.length, 1, 'the note sustained across the seek point resumes');
  playback.stop();
  await playback.toggle(project);
  assert.equal(playback.beat(), 0, 'Stop resets the next Play position');
});

test('seeking within a playing loop preserves it; seeking outside cancels queued voices and disables it', async () => {
  const { playback, project, context, sources } = fixture();
  playback.setLoopRange({ startBeat: 4, endBeat: 8 });
  playback.toggleLoop();
  await playback.start(project);
  playback.seek(project, 5);
  assert.equal(playback.loopEnabled(), true);
  assert.equal(playback.beat(), 5);
  assert.equal(playback.state(), 'playing');
  const oldSources = [...sources];
  playback.seek(project, 2);
  assert.equal(playback.loopEnabled(), false);
  assert.equal(playback.beat(), 2);
  assert.ok(oldSources.every(source => source.stopTime === .012));
  context.currentTime = .5;
  playback.tick();
  assert.equal(playback.beat(), 3, 'the playback clock advances from the seek target');
});

test('paused seeking keeps audio paused, clamps positions and treats the loop end as outside', async () => {
  const { playback, project, sources } = fixture();
  playback.setLoopRange({ startBeat: 4, endBeat: 8 });
  playback.toggleLoop();
  await playback.start(project);
  playback.pause();
  const count = sources.length;
  playback.seek(project, 4);
  assert.equal(playback.loopEnabled(), true);
  playback.seek(project, 8);
  assert.equal(playback.loopEnabled(), false);
  assert.equal(playback.state(), 'paused');
  assert.equal(sources.length, count);
  playback.seek(project, 999);
  assert.equal(playback.beat(), 16);
  playback.seek(project, -10);
  assert.equal(playback.beat(), 0);
  playback.seek(project, NaN);
  assert.equal(playback.beat(), 0);
});

test('LIVE defaults on and disabling it leaves the playing schedule untouched on edits', async () => {
  const { playback, context, sources, project } = fixture();
  assert.equal(playback.liveEnabled(), true);
  playback.toggleLive();
  await playback.start(project);
  context.currentTime = 1;
  playback.reload({ ...project, tempoBpm: 60 });
  assert.equal(playback.liveEnabled(), false);
  assert.equal(60 / playback.tempoMap[0].bpm, 0.5);
  assert.equal(sources[0].stopTime, 4.023);
  assert.equal(playback.state(), 'playing');
});

test('live note edits cancel queued voices, crossfade, and resume sustained notes at the current beat', async () => {
  const { playback, context, sources, project } = fixture();
  await playback.start(project);
  context.currentTime = 1.9;
  playback.tick(); // Queue the beat-four note before the edit.
  const oldSources = [...sources];
  const oldMaster = playback.master;
  context.currentTime = 2.01;
  const notes = project.tracks[0].notes.map((note) => ({ ...note, midi: note.midi + 12 }));
  playback.reload({ ...project, tracks: [{ ...project.tracks[0], notes }] });
  assert.equal(playback.state(), 'playing');
  assert.equal(playback.beat(), 4.02);
  for (const source of oldSources) assert.equal(source.stopTime, 2.022);
  assert.equal(oldMaster.gain.events.at(-1).value, 0);
  assert.equal(playback.master.gain.events.at(-1).value, 0.8);
  const resumed = sources.slice(oldSources.length);
  assert.equal(resumed.length, 2, 'both notes overlapping the playhead resume');
  assert.equal(resumed[0].frequency.value, 440 * 2 ** ((60 - 69) / 12));
  assert.ok(Math.abs(resumed[0].stopTime - (2.013 + (8 - 4.02) * 0.5 + 0.02)) < 1e-9);
});

test('tempo changes preserve the beat and update subsequent timing', async () => {
  const { playback, context, project } = fixture();
  await playback.start(project);
  context.currentTime = 1;
  playback.reload({ ...project, tempoBpm: 60 });
  assert.equal(playback.beat(), 2);
  context.currentTime = 2;
  playback.tick();
  assert.equal(playback.beat(), 3);
});

test('section presence edits remove notes and added tracks receive audio buses', async () => {
  const { playback, context, sources, project } = fixture();
  await playback.start(project);
  context.currentTime = 1;
  playback.reload({ ...project, arrangement: [{ measures: 4, trackPresence: { bass: false } }] });
  assert.equal(playback.notes.length, 0);
  assert.equal(sources.length, 1, 'removed section does not resume a sustained note');
  const added = { ...project.tracks[0], id: 'new-bass' };
  playback.reload({ ...project, tracks: [added] });
  assert.equal(playback.buses.has('new-bass'), true);
  assert.equal(playback.buses.has('bass'), false);
  assert.equal(playback.notes[0].trackId, 'new-bass');
});

test('mix and label edits do not restart unchanged notes, but program edits do', async () => {
  const { playback, context, sources, project } = fixture();
  project.tracks[0].role = 'chords';
  await playback.start(project);
  context.currentTime = 1;
  const track = { ...project.tracks[0], mix: { ...project.tracks[0].mix, muted: true } };
  const master = playback.master;
  playback.reload({ ...project, name: 'Renamed', tracks: [track] });
  assert.equal(playback.master, master);
  assert.equal(sources.length, 1);
  assert.equal(playback.buses.get('bass').gain.gain.events.at(-1).value, 0);
  playback.reload({ ...project, tracks: [{ ...track, mix: { ...track.mix, program: 0 } }] });
  assert.notEqual(playback.master, master);
  assert.equal(sources.at(-1).program, 0);
});

test('bass playback follows its selected program on start and during LIVE changes', async () => {
  const { playback, context, sources, project } = fixture();
  await playback.start(project);
  assert.equal(sources[0].program, 32);
  const frequency = sources[0].frequency.value;
  context.currentTime = 1;

  for (const program of [0, 16, 40, 32]) {
    const oldSource = sources.at(-1);
    playback.reload({ ...project, tracks: [{ ...project.tracks[0],
      mix: { ...project.tracks[0].mix, program } }] });
    assert.equal(oldSource.stopTime, 1.012, 'the previous sustained voice is cancelled');
    assert.equal(sources.at(-1).program, program, `bass uses program ${program}`);
    assert.equal(sources.at(-1).frequency.value, frequency, 'instrument changes preserve pitch');
    assert.equal(playback.beat(), 2, 'instrument changes preserve the playhead');
    assert.equal(playback.state(), 'playing');
  }
  playback.stop();

  await playback.start({ ...project, tracks: [{ ...project.tracks[0],
    mix: { ...project.tracks[0].mix, program: 40 } }] });
  assert.equal(sources.at(-1).program, 40, 'starting playback uses the selected bass program');
  playback.stop();
});

test('shortening stops at the new end or wraps when loop is enabled', async () => {
  for (const loop of [false, true]) {
    const { playback, context, project } = fixture();
    await playback.start(project);
    if (loop) playback.toggleLoop();
    context.currentTime = 5;
    playback.reload({ ...project, arrangement: [{ measures: 2, trackPresence: {} }] });
    assert.equal(playback.durationBeats(), 8);
    assert.equal(playback.state(), loop ? 'playing' : 'stopped');
    assert.equal(playback.beat(), loop ? 2 : 8);
  }
});

test('reload after disabling loop during a later cycle continues to the cycle end', async () => {
  const { playback, context, project } = fixture();
  await playback.start(project);
  playback.toggleLoop();
  context.currentTime = 10;
  playback.toggleLoop();
  playback.reload({ ...project, tempoBpm: 60 });
  assert.equal(playback.state(), 'playing');
  assert.equal(playback.beat(), 4);
  context.currentTime = 22;
  playback.tick();
  assert.equal(playback.state(), 'stopped');
});

test('paused and stopped playback stay inactive until resumed with the latest project', async () => {
  const { playback, context, project } = fixture();
  await playback.start(project);
  context.currentTime = 1;
  playback.pause();
  playback.reload({ ...project, tempoBpm: 60 });
  assert.equal(playback.state(), 'paused');
  assert.equal(playback.beat(), 2);
  await playback.toggle({ ...project, tempoBpm: 60 });
  assert.equal(60 / playback.tempoMap[0].bpm, 1);
  assert.equal(playback.beat(), 2);
  playback.stop();
  playback.reload(project);
  assert.equal(playback.state(), 'stopped');
  assert.equal(playback.beat(), 0);
});

function transportFixture() {
  const { playback, project, angular, load, context } = fixture();
  const projectSignal = angular.signal(project);
  const projects = { project: projectSignal };
  const workspaceUi = { sectionRange: angular.signal(null), selectedSectionIds: () => [] };
  angular.inject = (type) => type === 'audio' ? playback : type === 'ui' ? workspaceUi : projects;
  const { TransportDockComponent } = load('features/transport/transport-dock.component.ts', {
    '@angular/core': angular, '../../core/audio/audio-playback.service': { AudioPlaybackService: 'audio' },
    '../../core/audio/instrument-settings.service': { InstrumentSettingsService: 'instruments' },
    '../../core/project/project.service': { ProjectService: 'projects' }, '../../core/music/harmony': { KEYS: [] },
    '../../core/ui/ui-preferences.service': { UiPreferencesService: 'preferences' },
    '../../core/music/scales': require('./music-loader.cjs').loadMusic('scales.ts'),
    '../../core/ui/workspace-ui.service': { WorkspaceUiService: 'ui' },
    '../../shared/select.component': { SelectComponent: class {} },
    '../../shared/menu-select.component': { MenuSelectComponent: class {} },
    '../harmony/scale-menu-groups': { SCALE_MENU_GROUPS: [] },
    '../../core/music/section-context': require('./music-loader.cjs').loadMusic('section-context.ts'),
    '../../core/music/phrase-bounds': require('./music-loader.cjs').loadMusic('phrase-bounds.ts'),
    '../../core/music/phrase': { layOutTrackPhrase: (project, track) => project.arrangement.length ? track.notes : [] },
    '../../core/ui/musical-time': require('./music-loader.cjs').loadMusic('../ui/musical-time.ts'),
    '../tracks/mix-buttons.component': { MixButtonsComponent: class {} },
    '../../shared/compact-knob.component': { CompactKnobComponent: class {} },
    '../harmony/chordlet-editor.component': { ChordletEditorComponent: class {} },
  });
  return { playback, project, context, projectSignal, workspaceUi, TransportDockComponent };
}

for (const multiplier of [0.5, 1, 2]) test(`transport resolves arrangement-wide ${multiplier} beat durations before playback`, () => {
  const { project, projectSignal, TransportDockComponent } = transportFixture();
  projectSignal.set({ ...project, arrangement: project.arrangement.map(section => ({
    ...section, context: { beatDurationMultiplier: multiplier },
  })) });
  const transport = new TransportDockComponent();
  assert.equal(transport.totalBeats(), 16 * multiplier);
  assert.equal(transport.durationText(), multiplier === .5 ? '00:04.000' : multiplier === 1 ? '00:08.000' : '00:16.000');
  assert.equal(transport.currentTimeText(), '00:00.000');
});

test('transport progress, time, loop overlay and seeking share resolved section timing', () => {
  const { project, projectSignal, playback, workspaceUi, TransportDockComponent } = transportFixture();
  projectSignal.set({ ...project, arrangement: [
    { measures: 2, context: { beatDurationMultiplier: .5 }, trackPresence: {} },
    { measures: 2, context: { beatDurationMultiplier: 2, tempoBpm: 60 }, trackPresence: {} },
    { measures: 2, customDurations: [1, 3], trackPresence: {} },
  ] });
  const transport = new TransportDockComponent();
  playback.position.set(12);
  assert.equal(transport.totalBeats(), 24);
  assert.equal(transport.progressPercent(), 50);
  assert.equal(transport.currentTimeText(), '00:10.000');
  assert.equal(transport.durationText(), '00:20.000');
  assert.equal(transport.positionText(), '04.01');
  playback.toggleLoop();
  assert.deepEqual(transport.loopRegion(), { left: 0, width: 100 });
  workspaceUi.sectionRange.set({ startBeat: 4, endBeat: 20 });
  assert.deepEqual(transport.loopRegion(), { left: 4 / 24 * 100, width: 16 / 24 * 100 });
  const seeks = [];
  playback.seek = (_project, beat) => seeks.push(beat);
  transport.seek({ currentTarget: { getBoundingClientRect: () => ({ left: 100, width: 200 }) }, clientX: 250 });
  transport.seekKey({ key: 'End', preventDefault() {} });
  transport.seekKey({ key: 'Home', preventDefault() {} });
  assert.deepEqual(seeks, [18, 24, 0]);
  projectSignal.set({ ...project, arrangement: [] });
  assert.equal(transport.progressPercent(), 0);
  assert.equal(transport.durationText(), '00:00.000');
  assert.equal(transport.loopRegion(), null);
});

test('transport includes pickups and tails before playback and reaches its displayed end time', () => {
  const { project, projectSignal, playback, workspaceUi, TransportDockComponent } = transportFixture();
  const transport = new TransportDockComponent();
  projectSignal.set({ ...project, arrangement: [
    { measures: 1, context: { beatDurationMultiplier: .5 }, trackPresence: {} },
    { measures: 1, context: { beatDurationMultiplier: 2, tempoBpm: 60 }, trackPresence: {} },
  ], tracks: [{ ...project.tracks[0], notes: [
    { midi: 60, startBeat: -2, durationBeats: 1 },
    { midi: 60, startBeat: 9, durationBeats: 4 },
  ] }] });
  assert.deepEqual(transport.timelineBounds(), { startBeat: -2, endBeat: 13 });
  assert.equal(transport.durationText(), '00:13.000');
  playback.position.set(-2);
  assert.equal(transport.currentTimeText(), '00:00.000');
  assert.equal(transport.positionText(), '00.03');
  assert.equal(transport.progressPercent(), 0);
  playback.position.set(0);
  assert.equal(transport.currentTimeText(), '00:01.000');
  assert.equal(transport.progressPercent(), 2 / 15 * 100);
  playback.position.set(13);
  assert.equal(transport.currentTimeText(), transport.durationText());
  assert.equal(transport.progressPercent(), 100);
  playback.toggleLoop();
  assert.deepEqual(transport.loopRegion(), { left: 0, width: 100 });
  workspaceUi.sectionRange.set({ startBeat: 0, endBeat: 2 });
  assert.deepEqual(transport.loopRegion(), { left: 2 / 15 * 100, width: 2 / 15 * 100 });
  const seeks = [];
  playback.seek = (_project, beat) => seeks.push(beat);
  const currentTarget = { getBoundingClientRect: () => ({ left: 100, width: 200 }) };
  transport.seek({ currentTarget, clientX: 100 });
  transport.seek({ currentTarget, clientX: 200 });
  transport.seek({ currentTarget, clientX: 300 });
  transport.seekKey({ key: 'Home', preventDefault() {} });
  transport.seekKey({ key: 'End', preventDefault() {} });
  assert.deepEqual(seeks, [-2, 5.5, 13, -2, 13]);
});

test('transport watches project changes and enabling LIVE without subscribing to playback ticks', async () => {
  const { playback, project, context, projectSignal, TransportDockComponent } = transportFixture();
  let reloads = 0;
  const reload = playback.reload.bind(playback);
  playback.reload = (next) => { reloads++; reload(next); };
  playback.toggleLive(); // Explicitly disable the default before testing re-enabling.
  const transport = new TransportDockComponent();
  await playback.start(project);
  context.currentTime = 1;
  projectSignal.set({ ...project, tempoBpm: 60 });
  assert.equal(reloads, 0);
  transport.toggleLive();
  assert.equal(reloads, 1);
  assert.equal(60 / playback.tempoMap[0].bpm, 1);
  context.currentTime = 2;
  playback.tick();
  assert.equal(reloads, 1);
  projectSignal.set({ ...project, tempoBpm: 90 });
  assert.equal(reloads, 2);
  transport.toggleLive();
  projectSignal.set(project);
  assert.equal(reloads, 2);
});

test('looping selected sections starts at their first beat and wraps within their boundaries', async () => {
  const { playback, context, sources, project } = fixture();
  playback.setLoopRange({ startBeat: 4, endBeat: 8 });
  playback.toggleLoop();
  await playback.start(project);
  assert.equal(playback.beat(), 4);
  assert.equal(sources.length, 1, 'notes outside the selection are excluded');
  assert.equal(sources[0].frequency.value, 440 * 2 ** ((50 - 69) / 12));
  context.currentTime = 1.9;
  playback.tick();
  assert.ok(sources.length >= 2, 'next loop is scheduled ahead');
  assert.equal(sources[1].startTime, 2);
  context.currentTime = 2.1;
  playback.tick();
  assert.ok(Math.abs(playback.beat() - 4.2) < 1e-9);
  playback.pause();
  assert.ok(Math.abs(playback.beat() - 4.2) < 1e-9);
  await playback.toggle(project);
  assert.ok(Math.abs(playback.beat() - 4.2) < 1e-9, 'resume preserves selected-loop position');
});

test('changing or clearing the loop selection during playback updates the queued audio', async () => {
  const { playback, context, sources, project } = fixture();
  playback.setLoopRange({ startBeat: 4, endBeat: 8 });
  playback.toggleLoop();
  await playback.start(project);
  context.currentTime = 1;
  const oldSource = sources[0];
  playback.setLoopRange({ startBeat: 0, endBeat: 4 });
  assert.equal(playback.beat(), 0, 'moving to a different range seeks its start');
  assert.equal(oldSource.stopTime, 1.012, 'old queued audio is cancelled');
  assert.equal(playback.queuedNotes.length, 1);
  context.currentTime = 2;
  playback.tick();
  assert.equal(playback.beat(), 2);
  playback.setLoopRange(null);
  assert.equal(playback.beat(), 2, 'clearing selection keeps an in-range position');
  assert.equal(playback.queuedNotes.length, 2, 'whole arrangement is now looped');
  context.currentTime = 9.5;
  playback.tick();
  assert.equal(playback.beat(), 1);
});

test('a selected loop works through silence and clips sustained notes to the range end', async () => {
  const { playback, context, sources, project } = fixture();
  playback.setLoopRange({ startBeat: 0, endBeat: 4 });
  playback.toggleLoop();
  await playback.start(project);
  assert.equal(sources[0].stopTime, 2.023, 'the long note is clipped at the loop boundary');
  playback.setLoopRange({ startBeat: 8, endBeat: 12 });
  const count = sources.length;
  context.currentTime = 3;
  playback.tick();
  assert.equal(sources.length, count, 'silent loops do not schedule notes from elsewhere');
  assert.equal(playback.beat(), 10);
});

test('LIVE reload and disabling loop preserve the musical beat in a selected range', async () => {
  const { playback, context, project } = fixture();
  playback.setLoopRange({ startBeat: 4, endBeat: 8 });
  playback.toggleLoop();
  await playback.start(project);
  context.currentTime = 3;
  playback.reload({ ...project, tempoBpm: 60 });
  assert.equal(playback.beat(), 6);
  context.currentTime = 4;
  playback.tick();
  assert.equal(playback.beat(), 7);
  playback.toggleLoop();
  assert.equal(playback.beat(), 7);
  context.currentTime = 6;
  playback.tick();
  assert.equal(playback.beat(), 9, 'loop disabled continues beyond the selected range');
});

test('melody notes receive a pitched audio bus and retain normal mix routing', async () => {
  const { playback, sources, project } = fixture();
  const melody = { ...project.tracks[0], id: 'melody', role: 'melody',
    mix: { ...project.tracks[0].mix, program: 73, panPercent: -25 },
    notes: [{ id: 'melody-note', midi: 72, startBeat: 0, durationBeats: 1, velocity: 95 }] };
  await playback.start({ ...project, tracks: [melody] });
  assert.equal(playback.state(), 'playing');
  assert.equal(playback.notes[0].role, 'melody');
  assert.equal(playback.buses.has('melody'), true);
  assert.equal(playback.buses.get('melody').pan.pan.value, -0.25);
  assert.equal(sources[0].frequency.value, 440 * 2 ** ((72 - 69) / 12));
  playback.stop();
});

test('tracks sharing a MIDI channel retain independent programs and mixer buses', async () => {
  const { playback, sources, project } = fixture();
  const tracks = [
    { ...project.tracks[0], midiChannel: 1 },
    { ...project.tracks[0], id: 'other-bass', midiChannel: 1,
      mix: { ...project.tracks[0].mix, program: 38, panPercent: 50 } },
  ];
  await playback.start({ ...project, tracks });
  assert.deepEqual(sources.map(source => source.program), [32, 38]);
  assert.equal(sources[0].destination, playback.buses.get('bass').gain);
  assert.equal(sources[1].destination, playback.buses.get('other-bass').gain);
  assert.notEqual(sources[0].destination, sources[1].destination);
  assert.equal(playback.buses.get('other-bass').pan.pan.value, .5);
  playback.stop();
});

test('percussion pitches reach the synth drum bank instead of generic kick/snare/hat sounds', async () => {
  const { playback, sources, project } = fixture();
  const drums = { ...project.tracks[0], id: 'drums', role: 'drums', midiChannel: 10,
    notes: [36, 38, 42, 46, 49, 56, 81].map(midi => ({
      id: String(midi), midi, startBeat: 0, durationBeats: .25, velocity: 90,
    })) };
  await playback.start({ ...project, tracks: [drums] });
  assert.equal(sources.length, drums.notes.length);
  assert.ok(sources.every(source => source.percussion));
  playback.pause();
  assert.ok(sources.every(source => source.stopTime === undefined), 'pause cancels all drum operators');
});

test('transport cancellation and source completion clean up every synth operator', async () => {
  const { playback, context, sources, project } = fixture();
  const schedule = playback.synth.schedule;
  let disconnected = 0;
  playback.synth.schedule = (...args) => {
    const voices = [...schedule(...args), ...schedule(...args)];
    return voices.map(voice => ({ ...voice, dispose() { disconnected++; } }));
  };
  await playback.start(project);
  assert.equal(playback.activeSources.size, 2);
  context.currentTime = 1;
  playback.reload({ ...project, tempoBpm: 60 });
  assert.ok(sources.slice(0, 2).every(source => source.stopTime === 1.012));
  sources.slice(0, 2).forEach(source => source.onended());
  assert.equal(disconnected, 2);
  assert.equal(playback.activeSources.size, 2, 'only replacement operators remain');
  playback.stop();
  assert.equal(playback.activeSources.size, 0);
  sources.slice(2).forEach(source => source.onended());
  assert.equal(disconnected, 4);
});


test('looping starts enabled and wraps the whole arrangement by default', async () => {
  const { playback, project, context } = fixture({ loop: true });
  assert.equal(playback.loopEnabled(), true);
  await playback.start(project);
  context.currentTime = 8.5;
  playback.tick();
  assert.equal(playback.state(), 'playing');
  assert.equal(playback.beat(), 1);
  playback.stop();
});

test('pan and gain commits update active buses without generating a schedule', async () => {
  const { playback, project, sources } = fixture();
  await playback.start(project);
  const schedule = playback.createSchedule;
  playback.createSchedule = () => { throw Error('Mix changes must not rebuild notes'); };
  const master = playback.master;
  for (const panPercent of [-50, 0, 50]) {
    const updated = { ...project, tracks: project.tracks.map(track => ({ ...track,
      mix: { ...track.mix, panPercent, volumePercent: 150 },
    })) };
    playback.updateMix(updated); // Knob preview runs before the committed LIVE reload.
    playback.reload(updated);
    assert.equal(playback.buses.get('bass').gain.gain.events.at(-1).value, 1.5);
    assert.equal(playback.buses.get('bass').pan.pan.events.at(-1).value, panPercent / 100);
    assert.equal(playback.master, master);
    assert.equal(sources.length, 1);
  }
  playback.createSchedule = schedule;
  playback.reload({ ...project, tempoBpm: 60 });
  assert.notEqual(playback.master, master, 'musical edits still rebuild the playing schedule');
});

test('Compose restart uses zero or selected loop origin and keeps paused/stopped transport inactive', async () => {
  const { playback, project, context } = fixture();
  await playback.start(project);
  context.currentTime = 1;
  playback.restartAfterCompose({ ...project, tempoBpm: 100 });
  await Promise.resolve();
  assert.equal(playback.beat(), 0);
  assert.equal(playback.state(), 'playing');
  assert.equal(playback.tempoMap[0].bpm, 100);
  playback.setLoopRange({ startBeat: 4, endBeat: 8 });
  playback.toggleLoop();
  playback.restartAfterCompose(project);
  await Promise.resolve();
  assert.equal(playback.beat(), 4);
  playback.pause();
  playback.restartAfterCompose(project);
  assert.equal(playback.state(), 'paused');
  assert.equal(playback.beat(), 4);
  playback.stop();
  playback.restartAfterCompose(project);
  assert.equal(playback.state(), 'stopped');
  assert.equal(playback.beat(), 4);
});

test('arrangement Preview seeks to the first densest section with resolved timing and preserves transport state', async () => {
  const { playback, project } = fixture();
  const arrangement = [
    { id: 'intro', measures: 2, trackPresence: {}, customDurations: [3, 2], context: { beatDurationMultiplier: 2 } },
    { id: 'preview', measures: 4, trackPresence: { bass: true } },
    { id: 'tie', measures: 4, trackPresence: { bass: true } },
  ];
  const preview = { ...project, arrangement };
  assert.equal(playback.previewArrangement(preview), 'preview');
  assert.equal(playback.beat(), 10);
  assert.equal(playback.state(), 'stopped');
  assert.equal(playback.loopEnabled(), false, 'Preview does not enable looping');
  playback.setLoopRange({ startBeat: 0, endBeat: 4 });
  playback.toggleLoop();
  await playback.start(preview);
  playback.previewArrangement(preview);
  await Promise.resolve();
  assert.equal(playback.beat(), 10);
  assert.equal(playback.state(), 'playing');
  assert.equal(playback.loopEnabled(), true);
  assert.deepEqual(playback.selectedLoopRange, { startBeat: 10, endBeat: 26 });
  playback.pause();
  playback.previewArrangement(preview);
  assert.equal(playback.state(), 'paused');
  assert.equal(playback.beat(), 10);
  playback.previewArrangement({ ...preview, tracks: [] });
  assert.equal(playback.beat(), 0);
  assert.equal(playback.loopEnabled(), true);
  assert.deepEqual(playback.selectedLoopRange, { startBeat: 0, endBeat: 10 });
  playback.previewArrangement({ ...preview, arrangement: [] });
  assert.equal(playback.beat(), 0);
});

test('Preview moves an enabled loop beyond the old arrangement end when Compose lengthens the song', async () => {
  for (const playing of [false, true]) {
    const { playback, project } = fixture({ loop: true });
    if (playing) await playback.start(project);
    else playback.seek(project, 0);
    playback.setLoopRange({ startBeat: 0, endBeat: 4 });
    const longer = { ...project, arrangement: [
      { id: 'intro', measures: 8, trackPresence: {} },
      { id: 'preview', measures: 2, trackPresence: { bass: true } },
    ] };
    assert.equal(playback.previewArrangement(longer), 'preview');
    await Promise.resolve();
    assert.equal(playback.loopEnabled(), true);
    assert.equal(playback.beat(), 32);
    assert.deepEqual(playback.loopBounds(), { startBeat: 32, endBeat: 40 });
    assert.equal(playback.state(), playing ? 'playing' : 'stopped');
  }
});


test('changing the selected soundfont stops voices and disposes the old engine and decoded cache', async () => {
  const {playback, project, sources, instruments} = fixture();
  let disposed = 0;
  playback.synth.dispose = () => { disposed++; };
  await playback.start(project);
  instruments.kind.set('sf2:1');
  assert.equal(playback.state(), 'stopped');
  assert.equal(playback.synth, undefined);
  assert.equal(playback.activeSources.size, 0);
  assert.equal(disposed, 1);
  assert.ok(sources.every(s => s.stopTime === undefined));
});
