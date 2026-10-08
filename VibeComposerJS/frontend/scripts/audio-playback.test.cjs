const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const ts = require('typescript');

function fixture() {
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
  const angular = { Injectable: () => (type) => type, Component: () => (type) => type,
    signal, effect, untracked, computed: (read) => read, ChangeDetectionStrategy: { OnPush: 0 } };
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
    '@angular/core': angular,
    '../music/phrase': { layOutTrackPhrase: (project, track) =>
      project.arrangement[0].trackPresence[track.id] === false ? [] : track.notes },
  });
  const playback = new AudioPlaybackService();
  playback.context = context;
  const project = { name: 'Song', tempoBpm: 120, arrangement: [{ measures: 4, trackPresence: {} }],
    tracks: [{ id: 'bass', role: 'bass', notes: [
      { id: 'sustain', midi: 48, startBeat: 0, durationBeats: 8, velocity: 100 },
      { id: 'next', midi: 50, startBeat: 4, durationBeats: 1, velocity: 100 },
    ], mix: { program: 32, volumePercent: 80, panPercent: 0, muted: false, solo: false } }] };
  return { playback, context, sources, project, angular, load };
}

test('LIVE defaults on and disabling it leaves the playing schedule untouched on edits', async () => {
  const { playback, context, sources, project } = fixture();
  assert.equal(playback.liveEnabled(), true);
  playback.toggleLive();
  await playback.start(project);
  context.currentTime = 1;
  playback.reload({ ...project, tempoBpm: 60 });
  assert.equal(playback.liveEnabled(), false);
  assert.equal(playback.secondsPerBeat, 0.5);
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
  assert.equal(sources.at(-1).type, 'sine');
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
  assert.equal(playback.secondsPerBeat, 1);
  assert.equal(playback.beat(), 2);
  playback.stop();
  playback.reload(project);
  assert.equal(playback.state(), 'stopped');
  assert.equal(playback.beat(), 0);
});

test('transport watches project changes and enabling LIVE without subscribing to playback ticks', async () => {
  const { playback, project, angular, load, context } = fixture();
  const projectSignal = angular.signal(project);
  const projects = { project: projectSignal };
  const workspaceUi = { sectionRange: angular.signal(null), selectedSectionIds: () => [] };
  angular.inject = (type) => type === 'audio' ? playback : type === 'ui' ? workspaceUi : projects;
  const { TransportDockComponent } = load('shared/transport-dock.component.ts', {
    '@angular/core': angular, '../core/audio/audio-playback.service': { AudioPlaybackService: 'audio' },
    '../core/project/project.service': { ProjectService: 'projects' }, '../core/music/harmony': { KEYS: [] },
    './workspace-ui.service': { WorkspaceUiService: 'ui' },
  });
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
  assert.equal(playback.secondsPerBeat, 1);
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
  assert.equal(sources.length, 2, 'next loop is scheduled ahead');
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
