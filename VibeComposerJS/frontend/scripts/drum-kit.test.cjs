const test = require('node:test');
const assert = require('node:assert/strict');
const { loadMusic } = require('./music-loader.cjs');
const { fixture } = require('./support/project-fixture.cjs');
const { DEFAULT_PROJECT } = loadMusic('../project/project-defaults.ts');
const { CLASSIC_DRUM_INSTRUMENTS, DRUM_INSTRUMENTS, mapDrumPitch, resolveDrumMapping } = loadMusic('drum-instruments.ts');
const { projectDrumPitches, reconcileDrumKit, resolvedDrumSlots, canSelectDrumPitch, drumSelectionSlot } = loadMusic('../project/project-drum-kit.ts');
const source = DEFAULT_PROJECT.tracks.find(track => track.role === 'drums');
function projectWith(pitches) {
  return reconcileDrumKit({ ...structuredClone(DEFAULT_PROJECT), drumSlots: undefined,
    arrangement: [{ ...structuredClone(DEFAULT_PROJECT.arrangement[0]), trackPresence: {} }],
    tracks: pitches.map((pitch, index) => ({ ...structuredClone(source), id: `drum-${index}`, name: `D${index}`,
      generatorSettings: { ...source.generatorSettings, pitch } })) });
}
const fullKit = () => projectWith(CLASSIC_DRUM_INSTRUMENTS.map(item => item.midi));
const replace = (project, id, pitch) => ({ ...project, tracks: project.tracks.map(track => track.id === id
  ? { ...track, generatorSettings: { ...track.generatorSettings, pitch } } : track) });

test('expanded catalogue covers standard percussion and uses legacy slots for classic sounds', () => {
  assert.equal(DRUM_INSTRUMENTS.length, 48);
  for (let pitch = 35; pitch <= 82; pitch++) assert.ok(DRUM_INSTRUMENTS.some(item => item.midi === pitch));
  const project = fullKit();
  for (const [index, instrument] of CLASSIC_DRUM_INSTRUMENTS.entries()) {
    assert.equal(project.drumSlots[instrument.midi], 36 + index);
  }
});

test('unique replacements transfer slots at capacity; shared sounds and manual final uses protect capacity', () => {
  const project = fullKit();
  assert.equal(canSelectDrumPitch(project, 'drum-0', 49), true);
  const changed = reconcileDrumKit(replace(project, 'drum-0', 49), project);
  assert.equal(changed.drumSlots[49], project.drumSlots[35]);
  assert.equal(changed.drumSlots[35], undefined);
  for (const pitch of projectDrumPitches(changed).filter(pitch => pitch !== 49)) assert.equal(changed.drumSlots[pitch], project.drumSlots[pitch]);
  const shared = { ...project, tracks: [...project.tracks, { ...project.tracks[0], id: 'duplicate' }] };
  assert.equal(canSelectDrumPitch(shared, 'drum-0', 49), false);
  assert.equal(canSelectDrumPitch(shared, 'drum-0', 38), true);
  const manual = { ...project, tracks: project.tracks.map((track, index) => index ? track : { ...track,
    editedPhrase: [{ id: 'manual', midi: 35, startBeat: 0, durationBeats: .25, velocity: 90 }] }) };
  assert.equal(canSelectDrumPitch(manual, 'drum-0', 49), false);
  assert.equal(reconcileDrumKit(replace(manual, 'drum-0', 49), manual), undefined);
});

test('track ordering, deletion, additions and retained phrase ownership never compact surviving slots', () => {
  const project = fullKit();
  const removed = reconcileDrumKit({ ...project, tracks: project.tracks.slice(1).reverse() }, project);
  for (const pitch of projectDrumPitches(removed)) assert.equal(removed.drumSlots[pitch], project.drumSlots[pitch]);
  const added = reconcileDrumKit({ ...removed, tracks: [...removed.tracks,
    { ...source, id: 'crash', generatorSettings: { ...source.generatorSettings, pitch: 49 } }] }, removed);
  assert.equal(added.drumSlots[49], project.drumSlots[35]);
  const manual = projectWith([36]);
  manual.tracks[0].mix.muted = true;
  manual.tracks[0].phraseEdits = [{ startBeat: 0, endBeat: 4, notes: [{ midi: 49 }] }];
  manual.arrangement = [{ trackPresence: {}, trackPhrases: { 'drum-0': [{ midi: 51 }] } }];
  manual.editedPhrases = { drums: [{ midi: 60 }] };
  assert.deepEqual(projectDrumPitches(manual), [36, 49, 51, 60]);
});

test('invalid saved slots reject collisions, invalid notes and out-of-range output', () => {
  for (const drumSlots of [{ 36: 35 }, { 36: 52 }, { 36: 37, 38: 37 }, { '-1': 36 }, { 128: 36 }, []]) {
    assert.equal(reconcileDrumKit({ ...projectWith([36]), drumSlots }), undefined);
  }
});

test('menu availability and predicted output notes match actual command candidates for every catalogue sound', () => {
  for (const count of [1, 15, 16]) {
    const project = projectWith(CLASSIC_DRUM_INSTRUMENTS.slice(0, count).map(item => item.midi));
    for (const shared of [false, true]) {
      const current = shared ? { ...project, tracks: [...project.tracks, { ...project.tracks[0], id: 'shared' }] } : project;
      const retained = projectDrumPitches(current, ['drum-0']);
      for (const { midi } of DRUM_INSTRUMENTS) {
        const predicted = drumSelectionSlot(midi, current.tracks[0].generatorSettings.pitch, retained, current.drumSlots);
        const actual = reconcileDrumKit(replace(current, 'drum-0', midi), current);
        assert.equal(predicted, actual?.drumSlots[midi], `${count} sounds, shared=${shared}, pitch=${midi}`);
      }
    }
  }
});

test('project commands enforce capacity atomically and preserve mappings through import, undo and redo', () => {
  const { service, workspace } = fixture();
  const project = fullKit();
  assert.equal(service.importProjectJson(JSON.stringify(project)), true);
  assert.equal(workspace.drumSelectOptions()['drum-0'].find(item => item.value === 49).disabled, false);
  const before = service.exportProjectJson();
  service.updateTrackGeneratorSettings('drum-0', { pitch: 49 });
  assert.equal(service.project().drumSlots[49], project.drumSlots[35]);
  const after = service.exportProjectJson();
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.redo(); assert.equal(service.exportProjectJson(), after);
  assert.equal(service.importProjectJson(after), true);
  assert.equal(service.exportProjectJson(), after);
  const duplicate = service.duplicateTrack('drum-0');
  assert.ok(duplicate);
  const shared = service.exportProjectJson();
  assert.equal(workspace.drumSelectOptions()['drum-0'].find(item => item.value === 35).disabled, true);
  service.updateTrackGeneratorSettings('drum-0', { pitch: 35 });
  assert.equal(service.exportProjectJson(), shared);
  assert.match(service.generationFailure(), /16 distinct/);
});

test('manual selection pool filtering is optional, and pool edits leave sounds and slots intact', () => {
  const { service, workspace, uiPreferences } = fixture();
  const id = 'track-drums-1';
  assert.equal(workspace.drumSelectOptions()[id].find(item => item.value === 49).disabled, false);
  uiPreferences.toggleLimitInstrumentsToPool();
  assert.equal(workspace.drumSelectOptions()[id].find(item => item.value === 49).disabled, true);
  const before = service.project();
  assert.equal(service.updateInstrumentPolicy('drums', { pool: 'DRUM_FULL' }), 'changed');
  assert.deepEqual(service.project().tracks, before.tracks);
  assert.deepEqual(service.project().drumSlots, before.drumSlots);
  assert.equal(workspace.drumSelectOptions()[id].find(item => item.value === 49).disabled, false);
});

test('manual and preset batches reject a seventeenth sound without changing history or saved slots', () => {
  const { service } = fixture();
  assert.equal(service.importProjectJson(JSON.stringify(fullKit())), true);
  const before = service.exportProjectJson();
  const notes = [{ id: 'extra', midi: 49, startBeat: 0, durationBeats: .25, velocity: 100 }];
  assert.equal(service.applyPhraseEdits([{ trackId: 'drum-0', notes }]), false);
  assert.equal(service.exportProjectJson(), before);
  const extra = { ...structuredClone(source), id: 'extra', generatorSettings: { ...source.generatorSettings, pitch: 49 } };
  assert.equal(service.loadTrackPreset('drums', [extra], false), 'invalid');
  assert.equal(service.exportProjectJson(), before);
  assert.equal(service.loadTrackPreset('drums', [{ ...extra, generatorSettings: { ...source.generatorSettings, pitch: 38 } }], false), 'changed');
  assert.equal(projectDrumPitches(service.project()).length, 16);
});

test('all drum pools generate deterministically within capacity, reserving locked and manual sounds', () => {
  const { rerollInstruments, INSTRUMENT_POOLS } = loadMusic('instrument-generation.ts');
  const seed = 9223372036854775807n;
  for (const pool of ['DRUM', 'DRUM_FULL', 'DRUM_ACOUSTIC', 'DRUM_HAND']) {
    const tracks = Array.from({ length: 64 }, (_, index) => ({ ...source, id: `d${index}` }));
    const run = () => rerollInstruments(seed, 'drums', { randomize: true, pool }, tracks, false, { reservedPitches: [71, 72] });
    const result = run();
    assert.deepEqual(result, run());
    assert.ok(new Set([71, 72, ...result.map(track => track.generatorSettings.pitch)]).size <= 16);
    assert.ok(result.every(track => INSTRUMENT_POOLS[pool].includes(track.generatorSettings.pitch)));
  }
  const reserved = projectDrumPitches(fullKit());
  const result = rerollInstruments(seed, 'drums', { randomize: true, pool: 'DRUM_HAND' }, [source], false, { reservedPitches: reserved });
  assert.ok(reserved.includes(result[0].generatorSettings.pitch));
});

test('expanded generation fills 64 tracks within the sound budget and replays its mapping exactly', () => {
  const { service } = fixture();
  for (const role of ['melody', 'bass', 'chords', 'arpeggio']) service.setRoleTrackCount(role, 0);
  service.updateInstrumentPolicy('drums', { pool: 'DRUM_FULL' });
  assert.equal(service.setRoleTrackCount('drums', 64), 'changed');
  assert.equal(service.project().tracks.length, 64);
  assert.ok(projectDrumPitches(service.project()).length <= 16);
  assert.ok(projectDrumPitches(service.project()).some(pitch => !CLASSIC_DRUM_INSTRUMENTS.some(item => item.midi === pitch)));
  const before = service.exportProjectJson();
  assert.equal(service.rerollRoleInstruments('drums'), 'changed');
  const after = service.exportProjectJson();
  assert.ok(projectDrumPitches(service.project()).length <= 16);
  service.undo(); assert.equal(service.exportProjectJson(), before);
  assert.equal(service.rerollRoleInstruments('drums'), 'changed');
  assert.equal(service.exportProjectJson(), after);
});

test('composition and phrase MIDI agree with saved slots while GM and Auto keep original identities', () => {
  const { Midi } = require('@tonejs/midi');
  const { generateCompositionMidi, generatePhraseMidi } = loadMusic('midi-export.ts');
  const project = projectWith([49, 51, 64]);
  project.arrangement = [{ ...structuredClone(DEFAULT_PROJECT.arrangement[0]), trackPresence: Object.fromEntries(project.tracks.map(track => [track.id, true])) }];
  for (const track of project.tracks) track.editedPhrase = [{ id: track.id, midi: track.generatorSettings.pitch,
    startBeat: 0, durationBeats: .25, velocity: 100 }];
  const slots = resolvedDrumSlots(project);
  const original = JSON.stringify(project);
  const gm = new Midi(generateCompositionMidi(project, resolveDrumMapping('auto', false)));
  const external = new Midi(generateCompositionMidi(project, resolveDrumMapping('auto', true)));
  const phrase = new Midi(generatePhraseMidi('Draft', 120, project.tracks.map(track => ({ track, notes: track.editedPhrase })), 4, 'chromatic', 480, slots));
  for (let index = 0; index < project.tracks.length; index++) {
    const pitch = project.tracks[index].generatorSettings.pitch;
    assert.equal(gm.tracks[index].notes[0].midi, pitch);
    assert.equal(external.tracks[index].notes[0].midi, mapDrumPitch(pitch, 'chromatic', slots));
    assert.equal(phrase.tracks[index].notes[0].midi, external.tracks[index].notes[0].midi);
    assert.ok(external.tracks[index].notes[0].midi >= 36 && external.tracks[index].notes[0].midi <= 51);
  }
  assert.equal(JSON.stringify(project), original);
});
