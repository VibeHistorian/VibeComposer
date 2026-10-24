const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const { Midi } = require('@tonejs/midi');
const { fixture, projectData } = require('../support/project-fixture.cjs');
const { assertRejectedProjectImport } = require('../support/project-import-assertions.cjs');

test('contextual phrasing resolves per melody track and section and persists with history', () => {
  const { service, settings, load } = fixture();
  const { resolveMelodyInputs } = load('core/music/melody-global-settings.ts');
  const first = 'track-melody-1', second = service.duplicateTrack(first);
  const sectionId = service.project().arrangement[0].id;
  const trackScope = { kind: 'global-track', trackId: first };
  const localScope = { kind: 'section-track', sectionId, trackId: first };
  service.updatePartSettings(trackScope, { ending: 'CLOSED', style: 'JAZZ', form: 'SENTENCE' });
  const base = service.exportProjectJson();
  service.updatePartSettings(localScope, { ending: 'OPEN', style: 'MODAL', form: 'QUESTION_ANSWER' });
  const project = service.project(), section = project.arrangement[0];
  const resolved = id => resolveMelodyInputs(settings.resolvePartTrack(project.tracks.find(t => t.id === id), section).generatorSettings, project.melodyGlobal);
  assert.equal(resolved(first).ending, 'OPEN');
  assert.equal(resolved(first).style, 'MODAL');
  assert.equal(resolved(first).form, 'QUESTION_ANSWER');
  assert.equal(resolved(second).ending, 'LOOP');
  assert.equal(resolved(second).style, 'TONAL');
  const saved = service.exportProjectJson();
  service.undo(); assert.equal(service.exportProjectJson(), base);
  service.redo(); assert.equal(service.exportProjectJson(), saved);
  service.resetPartSettings(localScope, ['ending', 'style', 'form']);
  const reset = settings.resolvePartTrack(service.project().tracks.find(t => t.id === first), service.project().arrangement[0]).generatorSettings;
  assert.equal(reset.ending, 'CLOSED'); assert.equal(reset.style, 'JAZZ'); assert.equal(reset.form, 'SENTENCE');
  assert.equal(service.importProjectJson(saved), true);
  assert.equal(service.project().tracks.find(t => t.id === first).generatorSettings.ending, 'CLOSED');
  assert.deepEqual(service.project().arrangement[0].trackPartOverrides[first], { ending: 'OPEN', style: 'MODAL', form: 'QUESTION_ANSWER' });
  for (const patch of [{ ending: 'INVALID' }, { style: 1 }, { form: 'OPEN' }]) assert.equal(settings.decodePartPatch('melody', patch), undefined);
  assert.equal(settings.decodePartPatch('bass', { ending: 'OPEN' }), undefined);
});

test('melody input scopes keep project controls separate from independent track and section settings', () => {
  const { service, settings, load } = fixture();
  const { decodeMelodyGlobal } = load('core/music/melody-global-settings.ts');
  const first = 'track-melody-1', second = service.duplicateTrack(first);
  const sectionId = service.project().arrangement[0].id;
  const patch = { targetDirection: 'DESC', tonicNoteTarget: 70, modeNoteTarget: 60, chordNoteTarget: 80,
    emphasizeKey: true, maxDirectionChanges: 6, newBlocksChance: 0, fillPausesPerChord: true };
  const globals = { blockTargetMode: 1, targetNoteVariation: 6, useDirectionsFromProgression: true,
    forceFirstOutline: true, replaceAvoidNotes: 2, patternEffect: 0, blockTypeWeights: [20,30,40,50,60,70,80],
    blockChangePreference: [7,6,5,4,3,2,1,0], patternFlip: true };
  assert.deepEqual(decodeMelodyGlobal(globals), globals);
  for (const [key, value] of Object.entries(patch)) {
    assert.equal(decodeMelodyGlobal({ [key]: value }), undefined, key + ' must belong to a track');
    assert.ok(settings.decodePartPatch('melody', { [key]: value }), key);
    assert.equal(settings.decodePartPatch('bass', { [key]: value }), undefined, key);
    assert.equal(settings.decodePartPatch('melody', { [key]: null }), undefined, key);
  }
  for (const [key, value] of Object.entries(globals)) {
    // The track's patternFlip is the distinct custom-duration/grid flip.
    if (key !== 'patternFlip') assert.equal(settings.decodePartPatch('melody', { [key]: value }), undefined, key);
  }
  for (const scope of [{ kind: 'global-track', trackId: first }, { kind: 'global-role', role: 'melody' },
    { kind: 'section-track', sectionId, trackId: first }, { kind: 'section-role', sectionId, role: 'melody' }]) {
    const before = service.exportProjectJson(), position = service.historyPosition();
    const globalBefore = service.project().melodyGlobal;
    service.updatePartSettings(scope, patch);
    assert.equal(service.historyPosition(), position + 1, scope.kind + ' commits once');
    const project = service.project(), section = project.arrangement[0];
    const resolve = id => settings.resolvePartTrack(project.tracks.find(track => track.id === id), section).generatorSettings;
    for (const [key, value] of Object.entries(patch)) assert.equal(resolve(first)[key], value, scope.kind + ' ' + key);
    if (scope.kind.endsWith('track')) assert.notEqual(resolve(second).targetDirection, 'DESC', 'other melody stays independent');
    assert.strictEqual(project.melodyGlobal, globalBefore);
    const saved = service.exportProjectJson();
    service.undo(); assert.equal(service.exportProjectJson(), before);
    service.redo(); assert.equal(service.exportProjectJson(), saved);
    service.undo();
  }
});

test('melody tracks prepare targets with independent directions and preserve local settings', () => {
  const { service, load } = fixture();
  const first = 'track-melody-1', second = service.duplicateTrack(first);
  service.updateSettings({ seed: '9007199254740993', randomizeTempoOnCompose: false, randomizeTransposeOnCompose: false, randomizeHarmonyOnCompose: false });
  for (const role of ['melody','bass','chords','arpeggio','drums']) service.updateTrackGenerationPolicy(role, { onCompose: false });
  service.updateTrackGenerationPolicy('melody', { targetsOnCompose: true, patternsOnCompose: false });
  service.updatePartSettings({ kind: 'global-track', trackId: first }, { targetDirection: 'ASC' });
  service.updatePartSettings({ kind: 'global-track', trackId: second }, { targetDirection: 'DESC' });
  const sectionId = service.project().arrangement[0].id;
  service.updatePartSettings({ kind: 'section-track', sectionId, trackId: second }, { newBlocksChance: 0, chordNoteTarget: 90 });
  service.updateMelodyGlobal({ targetNoteVariation: 6 });
  const before = service.project(), beforeJson = service.exportProjectJson();
  const { chooseMelodyTargets } = load('core/music/melody-targets.ts');
  const expected = before.tracks.filter(track => track.role === 'melody').map((track, index) =>
    chooseMelodyTargets((Number(BigInt.asIntN(32, BigInt(before.seed))) + index + 1) | 0,
      before.progression, before.melodyGlobal, before.key, before.scale, before.progressionChords, track.generatorSettings));
  assert.notDeepEqual(expected[0], expected[1]);
  assert.equal(service.generateComposition('compose'), 'changed');
  assert.deepEqual(service.project().tracks.filter(track => track.role === 'melody').map(track => track.generatorSettings.chordNoteChoices), expected);
  assert.strictEqual(service.project().arrangement, before.arrangement);
  service.undo(); assert.equal(service.exportProjectJson(), beforeJson);
});

test('relocated melody inputs isolate track and section generation and agree with exported MIDI', () => {
  const { service, phrase, midi } = fixture();
  service.updateSettings({ seed: '42' });
  const first = 'track-melody-1', second = service.duplicateTrack(first);
  service.project().arrangement.forEach((_, index) => {
    service.setSectionTrackPresence(index, first, true);
    service.setSectionTrackPresence(index, second, true);
  });
  const track = id => service.project().tracks.find(item => item.id === id);
  const other = phrase.layOutTrackPhrase(service.project(), track(second));
  const original = phrase.layOutTrackPhrase(service.project(), track(first));
  service.updatePartSettings({ kind: 'global-track', trackId: first }, {
    newBlocksChance: 100, maxDirectionChanges: 6, emphasizeKey: true,
    tonicNoteTarget: 70, modeNoteTarget: 60, chordNoteTarget: 80, fillPausesPerChord: true,
    fillPauses: true, pauseChance: 55,
  });
  const base = phrase.layOutTrackPhrase(service.project(), track(first));
  assert.notDeepEqual(base, original);
  assert.deepEqual(phrase.layOutTrackPhrase(service.project(), track(second)), other);
  const section = service.project().arrangement[1], start = service.project().arrangement[0].measures * 4;
  const end = start + section.measures * 4;
  service.updatePartSettings({ kind: 'section-track', sectionId: section.id, trackId: first }, {
    newBlocksChance: 0, maxDirectionChanges: 1, emphasizeKey: false,
    tonicNoteTarget: 0, modeNoteTarget: 0, chordNoteTarget: 0, fillPausesPerChord: false,
  });
  const local = phrase.layOutTrackPhrase(service.project(), track(first));
  assert.notDeepEqual(local, base);
  const outside = notes => notes.filter(note => note.startBeat < start || note.startBeat >= end);
  assert.deepEqual(outside(local), outside(base));
  assert.deepEqual(phrase.layOutTrackPhrase(service.project(), track(second)), other);
  const parsed = new Midi(midi.generateCompositionMidi(service.project()));
  const notes = parsed.tracks.find(item => item.name === track(first).name).notes;
  assert.equal(notes.length, local.length);
  notes.forEach((note, index) => {
    assert.equal(note.midi, local[index].midi);
    assert.ok(Math.abs(note.ticks / parsed.header.ppq - local[index].startBeat) < 0.003);
  });
});

test('an active chord drag accepts a new target near its starting point and commits once', () => {
  const { service, load } = fixture();
  const { ChordletEditorComponent } = load('features/harmony/chordlet-editor.component.ts');
  const editor = new ChordletEditorComponent();
  editor.focusChord = () => {};
  const before = service.exportProjectJson();
  const host = {};
  const currentTarget = { focus() {}, setPointerCapture() {}, closest: () => host };
  const event = x => ({ pointerId: 1, button: 0, clientX: x, clientY: 100, currentTarget, preventDefault() {} });
  let target = 1;
  const originalDocument = global.document;
  global.document = { elementFromPoint: () => ({ closest: () => ({ dataset: { chordIndex: String(target) }, closest: () => host }) }) };
  try {
    editor.start(0, event(100));
    editor.move(event(102));
    assert.equal(editor.dragging(), null, 'a click-sized movement does not activate dragging');
    target = 0;
    editor.move(event(106));
    assert.equal(editor.dragging(), 0);
    // Scrolling a clipped strip can reveal another chord at the original pointer position.
    target = 1;
    editor.move(event(100.1));
    assert.equal(editor.dropIndex(), 1);
    editor.finish(event(100.1));
    const after = service.exportProjectJson();
    assert.deepEqual(service.project().progression, [5, 1, 6, 4]);
    assert.equal(editor.dragging(), null);
    service.undo(); assert.equal(service.exportProjectJson(), before, 'one undo restores the entire gesture');
    service.redo(); assert.equal(service.exportProjectJson(), after);
  } finally {
    if (originalDocument === undefined) delete global.document;
    else global.document = originalDocument;
  }
});

test('harmony headers compare effective chords and durations and reuse transposed chordlet labels', () => {
  const { service } = fixture();
  const { sectionHarmonyDisplay, trackHasSectionHarmony } = require('../music-loader.cjs').loadMusic('harmony-display.ts');
  const project = { ...service.project(), key: 'C', scale: 'major', transposeSemitones: 2, progression: [1, 4], progressionChords: undefined };
  const section = { ...project.arrangement[0], measures: 2 };
  const inherited = sectionHarmonyDisplay(project, section);
  assert.deepEqual(inherited.chords.map(chord => chord.label), ['D', 'G']);
  assert.deepEqual(inherited.chords.map(chord => chord.hue), [0, 150]);
  assert.equal(inherited.chordsDiffer, false); assert.equal(inherited.durationsDiffer, false);
  const explicit = sectionHarmonyDisplay(project, { ...section, chordDegrees: [1, 4, 1, 4], customDurations: [4, 4, 4, 4] });
  assert.equal(explicit.chordsDiffer, false); assert.equal(explicit.durationsDiffer, false);
  const local = { ...section, chordDegrees: [2, 5], customDurations: [2, 6] };
  const display = sectionHarmonyDisplay(project, local);
  assert.deepEqual(display.chords.map(chord => chord.label), ['Em', 'A']);
  assert.deepEqual(display.durations, [2, 6]);
  assert.equal(display.chordsDiffer, true); assert.equal(display.durationsDiffer, true);
  const bass = project.tracks.find(track => track.role === 'bass'), drums = project.tracks.find(track => track.role === 'drums');
  assert.equal(trackHasSectionHarmony(project, bass, section), false);
  assert.equal(trackHasSectionHarmony(project, bass, local), true);
  assert.equal(trackHasSectionHarmony(project, drums, { ...section, chordDegrees: [2, 5] }), false);
  assert.equal(trackHasSectionHarmony(project, drums, local), true);
  assert.equal(trackHasSectionHarmony(project, bass, { ...section, context: { key: 'C', scale: 'major', tempoBpm: project.tempoBpm } }), false);
  assert.equal(trackHasSectionHarmony(project, bass, { ...section, context: { key: 'D' } }), true);
});

test('contextual Melody settings persist, undo, and filter controls by algorithm', () => {
  const { service, load, uiPreferences } = fixture();
  const { DEFAULT_MELODY_GLOBAL } = load('core/music/melody-global-settings.ts');
  const { DEFAULT_HARMONY_SETTINGS } = load('core/music/harmony-voicing.ts');
  assert.equal(DEFAULT_MELODY_GLOBAL.contextualMelody, false);
  assert.equal(DEFAULT_HARMONY_SETTINGS.generation, 'CURRENT');
  const { PartSettingsEditorComponent } = load('features/part-settings/part-settings-editor.component.ts');
  const editor = new PartSettingsEditorComponent();
  editor.role.set('melody'); editor.values.set({});
  assert.ok(editor.controls().some(c => c.key === 'patternFlexible'));
  const before = service.exportProjectJson();
  assert.equal(service.updateMelodyGlobal({ contextualMelody: true, rangeMin: 62, rangeMax: 81, tension: 50,
    ending: 'CLOSED', style: 'JAZZ', form: 'QUESTION_ANSWER', phraseBars: 3, beatsPerBar: 3, chromaticApproaches: true }), 'changed');
  assert.ok(!editor.controls().some(c => c.key === 'patternFlexible'));
  assert.ok(editor.controls().some(c => c.key === 'speed'));
  uiPreferences.beginQuickSettings();
  assert.ok(editor.controls().some(c => c.key === 'patternFlexible'));
  uiPreferences.saveQuickSettings();
  const saved = service.exportProjectJson();
  service.undo(); assert.equal(service.exportProjectJson(), before);
  assert.equal(service.importProjectJson(saved), true);
  assert.deepEqual(projectData(service.exportProjectJson()), projectData(saved));
  assert.equal(service.updateMelodyGlobal({ rangeMin: 90 }), 'invalid');
  assert.equal(service.updateHarmonySettings({ generation: 'RANDOM' }), 'changed');
  const harmonySaved = service.exportProjectJson();
  assert.equal(service.importProjectJson(harmonySaved), true);
  assert.equal(service.project().harmonySettings.generation, 'RANDOM');
});

test('P5f durations share chord boundaries across all roles, score ranges, tempo events and persisted history', () => {
  const { service, load, phrase, midi, ui, workspace, stored } = fixture();
  const timing = load('core/music/section-context.ts');
  const project = JSON.parse(service.exportProjectJson());
  project.tracks = project.tracks.map(track => track.role === 'arpeggio' || track.role === 'drums'
    ? { ...track,generatorSettings:{...track.generatorSettings,rhythm:'melody'} } : track);
  project.arrangement = project.arrangement.slice(0,2).map(section => ({ ...section, measures: 4,
    trackPresence: Object.fromEntries(project.tracks.map(track => [track.id, true])) }));
  assert.equal(service.importProjectJson(JSON.stringify(project)), true);
  const id = service.project().arrangement[0].id;
  const before = service.exportProjectJson();
  const baseline = Object.fromEntries(service.project().tracks.map(track => [track.id, phrase.layOutTrackPhrase(service.project(), track)]));
  assert.equal(service.setSectionMusicalState(id, { customDurations: [2,6,1,3], context: { tempoBpm: 90, beatDurationMultiplier: 2 } }), 'changed');
  const after = service.exportProjectJson();
  service.undo(); assert.equal(service.exportProjectJson(), before); service.redo(); assert.equal(service.exportProjectJson(), after);
  const current = service.project(), section = current.arrangement[0];
  assert.equal(timing.sectionDurationBeats(current, section), 24);
  assert.equal(workspace.totalBeats(), 40);
  ui.selectSection(id); assert.deepEqual(ui.sectionRange(), { startBeat: 0, endBeat: 24 });
  assert.equal(workspace.timelineSections()[1].startBeat, 24);
  for (const track of current.tracks) {
    const notes = phrase.layOutTrackPhrase(current, track);
    assert.deepEqual(notes.filter(note => note.startBeat < 24), timing.mapChordNotes(baseline[track.id].filter(note => note.startBeat < 16), [4,12,2,6]), track.role);
    assert.deepEqual(notes.filter(note => note.startBeat >= 24), baseline[track.id].filter(note => note.startBeat >= 16).map(note => ({ ...note, startBeat: note.startBeat + 8 })), track.role + ' following section');
  }
  const exported = new Midi(midi.generateCompositionMidi(current));
  assert.deepEqual(exported.header.tempos.map(event => [event.ticks / exported.header.ppq,Math.round(event.bpm)]), [[0,90],[24,current.tempoBpm]]);
  const map = timing.projectTempoMap(current);
  for (const track of current.tracks) {
    const notes = phrase.layOutTrackPhrase(current, track), output = exported.tracks.find(candidate => candidate.name === track.name).notes;
    for (const note of notes) {
      const matching = output.find(event => event.midi === note.midi && Math.abs(event.ticks / exported.header.ppq - note.startBeat) < 1 / exported.header.ppq);
      assert.ok(matching, track.role + ' MIDI onset');
      assert.ok(Math.abs(matching.time - timing.beatToSeconds(map,note.startBeat)) < 0.004);
    }
  }
  assert.equal(service.importProjectJson(after), true); assert.deepEqual(JSON.parse(service.exportProjectJson()), JSON.parse(after));
  const { ProjectService } = load('core/project/project.service.ts');
  const restored = new ProjectService(); assert.deepEqual(restored.project(),service.project());
  assert.equal(JSON.parse(stored.get('vibecomposer.project.v12')).arrangement[0].context.tempoBpm, 90);
});

test('P5f section Chordlets, timing and key edits preserve scopes, manual ownership and exact transposition', () => {
  const { service, load, phrase } = fixture();
  const project = JSON.parse(service.exportProjectJson());
  project.arrangement = project.arrangement.slice(0,2).map(section => ({ ...section, measures: 4,
    trackPresence: Object.fromEntries(project.tracks.map(track => [track.id,true])) }));
  assert.equal(service.importProjectJson(JSON.stringify(project)),true);
  const id = service.project().arrangement[0].id;
  const notes = role => phrase.layOutTrackPhrase(service.project(),service.project().tracks.find(track => track.role === role)).filter(note => note.startBeat < 16);
  const baseline = Object.fromEntries(['melody','bass','chords','arpeggio','drums'].map(role => [role,notes(role)]));
  assert.equal(service.updateSectionContext(id, { keyChange: -12 }), 'changed');
  for (const role of ['melody','bass','chords','arpeggio']) assert.deepEqual(notes(role),baseline[role].map(note => ({ ...note, midi: note.midi - 12 })),role);
  assert.deepEqual(notes('drums'),baseline.drums);
  const bases = JSON.stringify(service.project().tracks);
  service.updateSectionContext(id, { keyChange: null, swingPercent: 70, key: 'D', scale: 'natural-minor' });
  for (const role of ['melody','bass','chords','arpeggio']) assert.notDeepEqual(notes(role),baseline[role],role);
  assert.equal(JSON.stringify(service.project().tracks),bases);
  service.setSectionChord(id, 0, { root: 6, spice: 'maj7', inversion: 1 });
  const { sectionGenerationHarmony } = load('core/music/project-harmony.ts');
  const harmony = sectionGenerationHarmony(service.project(),service.project().arrangement[0],'chords');
  assert.equal(harmony.customChords[0].root,6);
  service.setSectionMusicalState(id,{ customDurations: [1,2,3,4] });
  service.editSectionChordList(id,'move',0,2);
  assert.deepEqual(service.project().arrangement[0].customDurations,[2,3,1,4]);
  assert.equal(service.project().arrangement[0].customChords[2].root,6);
  const state=service.exportProjectJson(); service.undo(); service.redo(); assert.equal(service.exportProjectJson(),state);
  const trackId='track-melody-1'; service.updateTrackPhrase(trackId,[{ id:'manual',midi:65,startBeat:0,durationBeats:1,velocity:90 }]);
  const manual=JSON.stringify(service.project().tracks.find(track=>track.id===trackId).editedPhrase);
  service.updateSectionContext(id,{ keyChange: 7 });
  assert.equal(JSON.stringify(service.project().tracks.find(track=>track.id===trackId).editedPhrase),manual);
  assert.equal(notes('melody')[0].midi,65);
});

test('P5e section selections share harmony across roles, preserve sparse overrides and replay history/JSON/session/MIDI', () => {
  const { service, load, phrase, midi, workspace } = fixture();
  const { projectMelodyHarmony, sectionGenerationHarmony } = load('core/music/project-harmony.ts');
  service.updateSettings({ seed: '9007199254740993' }); service.setProgression([1,5,6,4]);
  const original = JSON.parse(service.exportProjectJson());
  original.arrangement = original.arrangement.slice(0,2).map(section => ({ ...section, measures: 4,
    trackPresence: Object.fromEntries(original.tracks.map(track => [track.id,true])) }));
  assert.equal(service.importProjectJson(JSON.stringify(original)), true);
  const sectionId = service.project().arrangement[0].id;
  const chordId = 'track-chords-1', melodyId = 'track-melody-1';
  service.updatePartSettings({ kind: 'section-role', sectionId, role: 'chords' }, { transpose: 12 });
  service.updatePartSettings({ kind: 'section-track', sectionId, trackId: melodyId }, { speed: 100 });
  service.updatePartSettings({ kind: 'global-track', trackId: melodyId }, { chordNoteChoices: [0,2,2,4], patternSeed: 42 });
  const inferred = projectMelodyHarmony(service.project());
  assert.equal(inferred.available, true); assert.equal(inferred.sourceTrackId, melodyId);
  assert.notDeepEqual(inferred.harmony.degrees, service.project().progression);
  const patchState = JSON.stringify(service.project().arrangement.map(section => [section.rolePartOverrides, section.trackPartOverrides]));
  const notes = role => phrase.layOutTrackPhrase(service.project(), service.project().tracks.find(track => track.role === role));
  const before = service.exportProjectJson(), beforeNotes = Object.fromEntries(['melody','bass','chords','arpeggio'].map(role => [role,notes(role)]));
  assert.equal(service.updateSectionHarmony(sectionId, { useMelodyHarmony: true }), 'changed');
  const selected = service.exportProjectJson(); service.undo(); assert.equal(service.exportProjectJson(),before);
  service.redo(); assert.equal(service.exportProjectJson(),selected);
  assert.equal(patchState, JSON.stringify(service.project().arrangement.map(section => [section.rolePartOverrides, section.trackPartOverrides])));
  for (const role of ['bass','chords','arpeggio']) {
    assert.notDeepEqual(notes(role).filter(note => note.startBeat < 16), beforeNotes[role].filter(note => note.startBeat < 16));
    assert.deepEqual(notes(role).filter(note => note.startBeat >= 16), beforeNotes[role].filter(note => note.startBeat >= 16));
    assert.deepEqual(sectionGenerationHarmony(service.project(),service.project().arrangement[0],role).roots, inferred.harmony.roots);
  }
  const allHarmonyNotes = notes('melody');
  assert.equal(service.updateSectionHarmony(sectionId, { useMelodyHarmony: false, swapMelody: true }), 'changed');
  assert.notDeepEqual(notes('melody'), allHarmonyNotes);
  for (const role of ['bass','chords','arpeggio']) assert.deepEqual(notes(role),beforeNotes[role]);
  assert.equal(sectionGenerationHarmony(service.project(),service.project().arrangement[0],'melody').melodySeedOffset,100);
  service.updateSectionHarmony(sectionId, { useMelodyHarmony: true });
  const { AudioPlaybackService } = load('core/audio/audio-playback.service.ts');
  const playback = new AudioPlaybackService();
  const schedule = playback.createSchedule(service.project());
  const score = workspace.scoreNotes();
  for (const role of ['melody','bass','chords','arpeggio','drums']) {
    const expected = notes(role), exported = new Midi(midi.generateCompositionMidi(service.project()));
    const source = service.project().tracks.find(track => track.role === role);
    const strip = note => [note.midi,note.startBeat,note.durationBeats,note.velocity];
    const compare = values => values.map(strip).sort((a,b) => a[1]-b[1] || a[0]-b[0]);
    assert.deepEqual(compare(schedule.filter(note => note.trackId === source.id)),compare(expected));
    assert.deepEqual(compare(score.filter(note => note.part === source.id)),compare(expected));
    const track = exported.tracks.find(track => track.name === source.name);
    assert.equal(track.notes.length,expected.length);
    expected.forEach((note,index) => {
      assert.equal(track.notes[index].midi,note.midi);
      assert.ok(Math.abs(track.notes[index].ticks/exported.header.ppq - note.startBeat) < 0.003);
      assert.ok(Math.abs(track.notes[index].durationTicks/exported.header.ppq - note.durationBeats) < 0.005);
    });
  }
  service.updateHarmonySettings({ flattenVoicingChance: 35, flattenLargeChords: true, progressiveVoicing: true });
  const saved = service.exportProjectJson(), expected = notes('melody');
  assert.equal(service.importProjectJson(saved),true); assert.deepEqual(notes('melody'),expected);
  const restored = new (load('core/project/project.service.ts').ProjectService)();
  assert.deepEqual(JSON.parse(restored.exportProjectJson()),JSON.parse(service.exportProjectJson()));
  assert.equal(restored.project().seed,'9007199254740993');
  assert.deepEqual(phrase.layOutTrackPhrase(restored.project(),restored.project().tracks.find(track => track.id === melodyId)),expected);
});

test('P5e inference keeps base source ownership and explicit section harmony ahead of alternate selections', () => {
  const { service, load, phrase } = fixture();
  const { projectMelodyHarmony, sectionGenerationHarmony, baseGenerationHarmony } = load('core/music/project-harmony.ts');
  service.setProgression([1,5,6,4]); const sectionId = service.project().arrangement[0].id, id = 'track-melody-1';
  const track = () => service.project().tracks.find(track => track.id === id);
  const before = projectMelodyHarmony(service.project()).harmony;
  service.updatePartSettings({ kind: 'section-track', sectionId, trackId: id }, { speed: 100, chordNoteChoices: [12,7,0,5] });
  service.setSectionTrackPresence(0,id,false);
  assert.deepEqual(projectMelodyHarmony(service.project()).harmony,before, 'local patches and absent source do not re-infer base harmony');
  service.updatePartSettings({ kind: 'global-role', role: 'melody' }, { tonicNoteTarget: 100, chordNoteTarget: 100 });
  service.updateMelodyGlobal({ replaceAvoidNotes: 2, patternFlip: true });
  assert.deepEqual(projectMelodyHarmony(service.project()).harmony,before, 'later repairs/flip do not change skeleton inference');
  const manual = [{ id:'saved',midi:61,startBeat:0,durationBeats:3,velocity:95 }];
  service.updateTrackPhrase(id,manual);
  assert.equal(projectMelodyHarmony(service.project()).manualSource,true);
  assert.deepEqual(projectMelodyHarmony(service.project()).harmony,before);
  service.updateSectionHarmony(sectionId,{ useMelodyHarmony:true,swapMelody:true });
  service.setSectionTrackPresence(0,id,true);
  assert.deepEqual(phrase.phraseForTrack(service.project(),track()),manual);
  service.updateTrackPhrase(id,[]); assert.deepEqual(phrase.layOutTrackPhrase(service.project(),track()),[]);
  assert.deepEqual(projectMelodyHarmony(service.project()).harmony,before);
  service.setSectionChordDegree(0,1,2);
  const section = service.project().arrangement[0];
  assert.ok(section.chordDegrees);
  for (const role of ['melody','bass','chords','arpeggio','drums']) assert.deepEqual(sectionGenerationHarmony(service.project(),section,role),baseGenerationHarmony(service.project(),section));
  service.clearSectionChordOverrides(0); service.clearTrackPhrase(id);
  assert.equal(projectMelodyHarmony(service.project()).available,true);
  const secondId = service.addTrack('melody');
  assert.equal(projectMelodyHarmony(service.project()).sourceTrackId,id, 'first source never silently redirects');
  service.removeTrack(id);
  assert.equal(projectMelodyHarmony(service.project()).sourceTrackId,secondId);
  service.removeTrack(secondId); assert.equal(projectMelodyHarmony(service.project()).available,false);
  for (const progression of [[1],[1,5]]) {
    service.addTrack('melody'); service.setProgression(progression);
    assert.deepEqual(projectMelodyHarmony(service.project()).harmony.degrees,progression);
    assert.equal(projectMelodyHarmony(service.project()).harmony.roots.length,progression.length);
  }
});

test('matching explicit section chords share primary harmony checks in both editors and generation', () => {
  const { service, load } = fixture();
  const { sectionHasChordOverride } = load('core/music/section-context.ts');
  const { progressionChords } = load('core/music/harmony.ts');
  const { sectionGenerationHarmony } = load('core/music/project-harmony.ts');
  const harmonyEditor = new (load('features/harmony/harmony-settings.component.ts').HarmonySettingsComponent)();
  const contextEditor = new (load('features/arrangement/section-context-editor.component.ts').SectionContextEditorComponent)();
  service.setProgression([1,5,6,4]);
  const id = service.project().arrangement[0].id;
  const section = () => service.project().arrangement[0];
  const check = expected => {
    harmonyEditor.section.set(section()); contextEditor.section.set(section());
    assert.equal(sectionHasChordOverride(service.project(), section()), expected);
    assert.equal(harmonyEditor.hasChordOverride(), expected);
    assert.equal(contextEditor.hasChordOverride(), expected);
  };
  check(false);
  service.setSectionMusicalState(id, { chordDegrees: [1,5,6,4] }); check(false);
  const project = service.project(), chords = progressionChords(project.key, project.scale, project.progression);
  service.setSectionMusicalState(id, { customChords: chords }); check(false);
  service.updateSectionHarmony(id, { useMelodyHarmony: true, swapMelody: true });
  assert.equal(sectionGenerationHarmony(service.project(), section(), 'melody').inferred, true);
  assert.equal(sectionGenerationHarmony(service.project(), section(), 'melody').melodySeedOffset, 100);
  assert.equal(sectionGenerationHarmony(service.project(), section(), 'bass').inferred, true);
  const saved = service.exportProjectJson();
  for (const changed of [{ ...chords[0], inversion: 1 }, { ...chords[0], spice: '7' }, { ...chords[0], root: (chords[0].root + 1) % 12 }]) {
    service.setSectionChord(id, 0, changed); check(true);
    assert.equal(sectionGenerationHarmony(service.project(), section(), 'melody').inferred, false);
    service.setSectionChord(id, 0, chords[0]); check(false);
  }
  assert.equal(service.importProjectJson(saved), true); check(false);
  assert.deepEqual(section().customChords, chords, 'matching explicit choices remain stored');
  service.setProgression([1,2,6,4]); check(true);
  service.undo(); check(false);
  service.setSectionMusicalState(id, { context: { keyChangeType: 'two-five-one' } });
  assert.equal(sectionGenerationHarmony(service.project(), section(), 'melody').inferred, false);
});

test('section chord comparison resolves custom primary chords, modes and duration-only repetition', () => {
  const { service, load } = fixture();
  const { sectionHasChordOverride } = load('core/music/section-context.ts');
  const { progressionChords } = load('core/music/harmony.ts');
  const project = { ...service.project(), key: 'D', scale: 'dorian', progression: [1,4,5] };
  const section = { ...project.arrangement[0], chordDegrees: [1,4,5], customChords: undefined };
  assert.equal(sectionHasChordOverride(project, section), false);
  const contextual = { ...section, context: { key: 'F', scale: 'natural-minor' } };
  assert.equal(sectionHasChordOverride(project, contextual), false);
  const customChords = progressionChords('F', 'natural-minor', project.progression);
  assert.equal(sectionHasChordOverride(project, { ...contextual, customChords }), false);
  assert.equal(sectionHasChordOverride(project, { ...contextual, customChords, customDurations: [2,4,8] }), false);
  assert.equal(sectionHasChordOverride(project, { ...section, customDurations: [2,4,8,2,4,8] }), false);
  assert.equal(sectionHasChordOverride(project, { ...section, chordDegrees: [1,4] }), true);
  const primary = { ...project, progressionChords: [{ root: 2, spice: '7', inversion: 1 }, null, null] };
  assert.equal(sectionHasChordOverride(primary, section), true, 'explicit degrees replace custom primary chords');
  assert.equal(sectionHasChordOverride(primary, { ...section, customChords: primary.progressionChords }), false);
  assert.equal(sectionHasChordOverride(primary, { ...section, chordDegrees: undefined, customChords: primary.progressionChords }), false);
});

test('P5e Melody flip and key emphasis preserve ownership, update consumers and replay exported MIDI', () => {
  const { service, phrase, midi, load } = fixture();
  const id = 'track-melody-1', sectionId = service.project().arrangement[0].id;
  const track = role => service.project().tracks.find(item => item.role === role);
  service.setSectionTrackPresence(0, id, true);
  for (const role of ['drums', 'arpeggio']) {
    service.setSectionTrackPresence(0, track(role).id, true);
    service.updatePartSettings({ kind: 'global-track', trackId: track(role).id }, { rhythm: 'melody', swingPercent: 50 });
  }
  service.updatePartSettings({ kind: 'section-track', sectionId, trackId: id }, { speed: 50 });
  const notes = phrase.layOutTrackPhrase(service.project(), track('melody'));
  const context = phrase.sectionMelodyContext(service.project(), service.project().arrangement[0]);
  const drums = phrase.layOutTrackPhrase(service.project(), track('drums')), arp = phrase.layOutTrackPhrase(service.project(), track('arpeggio'));
  const before = service.exportProjectJson(), sections = service.project().arrangement;
  assert.equal(service.updateMelodyGlobal({ patternFlip: true }), 'changed');
  assert.deepEqual(phrase.layOutTrackPhrase(service.project(), track('melody')), notes);
  assert.deepEqual(phrase.sectionMelodyContext(service.project(), service.project().arrangement[0]).pitches,
    context.pitches.map(grid => grid.map(pitch => pitch > 0 ? 0 : 1)));
  assert.notDeepEqual(phrase.layOutTrackPhrase(service.project(), track('drums')), drums);
  assert.notDeepEqual(phrase.layOutTrackPhrase(service.project(), track('arpeggio')), arp);
  assert.deepEqual(service.project().arrangement, sections);
  const flipped = service.exportProjectJson();
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.redo(); assert.equal(service.exportProjectJson(), flipped);
  service.updatePartSettings({ kind: 'global-role', role: 'melody' }, { emphasizeKey: true });
  assert.notDeepEqual(phrase.layOutTrackPhrase(service.project(), track('melody')), notes);
  for (const role of ['melody', 'drums', 'arpeggio']) {
    const expected = phrase.layOutTrackPhrase(service.project(), track(role));
    const parsed = new Midi(midi.generateCompositionMidi(service.project()));
    const exported = parsed.tracks.find(item => item.name === track(role).name).notes;
    assert.equal(exported.length, expected.length);
    exported.forEach((note, index) => {
      assert.equal(note.midi, expected[index].midi);
      assert.ok(Math.abs(note.ticks / parsed.header.ppq - expected[index].startBeat) < 0.003);
    });
  }
  const saved = service.exportProjectJson(); service.updateSettings({ name: 'redo' }); service.undo();
  for (const key of ['patternFlip', 'emphasizeKey']) {
    assert.equal(service.updateMelodyGlobal({ [key]: 'true' }), 'invalid');
    const imported = JSON.parse(saved); imported.melodyGlobal[key] = 1;
    assertRejectedProjectImport(service, JSON.stringify(imported));
    for (const scope of [{ kind: 'global-role', role: 'melody' }, { kind: 'global-track', trackId: id },
      { kind: 'section-role', sectionId, role: 'melody' }, { kind: 'section-track', sectionId, trackId: id }]) {
      service.updatePartSettings(scope, { [key]: 'true' }); assert.equal(service.exportProjectJson(), saved);
    }
    assert.equal(service.canRedo(), true);
  }
  const { ProjectService } = load('core/project/project.service.ts');
  assert.deepEqual(projectData(new ProjectService().exportProjectJson()), projectData(saved));
  assert.equal(service.importProjectJson(saved), true);
  const manual = [{ id: 'flip-manual', midi: 62, startBeat: 1, durationBeats: 2, velocity: 90 }];
  service.updateTrackPhrase(id, manual);
  service.updatePartSettings({ kind: 'global-role', role: 'melody' }, { emphasizeKey: false });
  assert.deepEqual(phrase.phraseForTrack(service.project(), track('melody')), manual);
  assert.deepEqual(phrase.sectionMelodyContext(service.project()).pitches[0], Array.from({ length: 16 }, (_, slot) => slot === 4 ? 0 : 1));
  service.updateTrackPhrase(id, []);
  assert.deepEqual(phrase.sectionMelodyContext(service.project()).pitches.flat(), Array(service.project().progression.length * 16).fill(0));
  service.clearTrackPhrase(id);
  service.removeTrack(id);
  assert.equal(phrase.sectionMelodyContext(service.project()).available, false);
});

test('P5e tonal targets preserve track ownership, history, restore and MIDI', () => {
  const { service, phrase, midi, load } = fixture();
  const id = 'track-melody-1', sectionId = service.project().arrangement[0].id;
  const track = () => service.project().tracks.find(item => item.id === id);
  service.setSectionTrackPresence(0, id, true);
  service.updatePartSettings({ kind: 'section-track', sectionId, trackId: id }, { speed: 100 });
  const before = service.exportProjectJson(), patches = service.project().arrangement;
  const original = phrase.layOutTrackPhrase(service.project(), track());
  const originalContext = phrase.sectionMelodyContext(service.project(), service.project().arrangement[0]);
  service.updatePartSettings({ kind: 'global-role', role: 'melody' }, { tonicNoteTarget: 100 });
  assert.notDeepEqual(phrase.layOutTrackPhrase(service.project(), track()), original);
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.updatePartSettings({ kind: 'global-role', role: 'melody' }, { modeNoteTarget: 100 });
  assert.notDeepEqual(phrase.layOutTrackPhrase(service.project(), track()), original);
  assert.notDeepEqual(phrase.sectionMelodyContext(service.project(), service.project().arrangement[0]), originalContext);
  service.updatePartSettings({ kind: 'global-role', role: 'melody' }, { tonicNoteTarget: 70, modeNoteTarget: 60 });
  assert.deepEqual(service.project().arrangement, patches);
  let saved = service.exportProjectJson();
  const notes = phrase.layOutTrackPhrase(service.project(), track());
  const exported = new Midi(midi.generateCompositionMidi(service.project()));
  const output = exported.tracks.find(item => item.name === track().name).notes;
  assert.equal(output.length, notes.length);
  output.forEach((note, index) => {
    assert.equal(note.midi, notes[index].midi);
    assert.ok(Math.abs(note.ticks / exported.header.ppq - notes[index].startBeat) < 0.003);
    assert.ok(Math.abs(note.durationTicks / exported.header.ppq - notes[index].durationBeats) < 0.003);
  });
  service.undo(); service.redo(); assert.equal(service.exportProjectJson(), saved);
  const { ProjectService } = load('core/project/project.service.ts');
  assert.deepEqual(projectData(new ProjectService().exportProjectJson()), projectData(saved));
  assert.equal(service.importProjectJson(saved), true);
  saved = service.exportProjectJson();
  service.updateSettings({ name: 'redo' }); service.undo();
  for (const key of ['tonicNoteTarget', 'modeNoteTarget']) {
    key === 'replaceAvoidNotes' ? service.updateMelodyGlobal({ [key]: service.project().melodyGlobal[key] }) : service.updatePartSettings({ kind: 'global-role', role: 'melody' }, { [key]: track().generatorSettings[key] });
    for (const value of [-1, 101, 1.5, '50', null]) {
      key === 'replaceAvoidNotes' ? service.updateMelodyGlobal({ [key]: value }) : service.updatePartSettings({ kind: 'global-role', role: 'melody' }, { [key]: value });
      const project = JSON.parse(saved);
      if (key === 'replaceAvoidNotes') project.melodyGlobal[key] = value;
      else project.tracks.find(track => track.role === 'melody').generatorSettings[key] = value;
      assertRejectedProjectImport(service, JSON.stringify(project));
      assert.equal(service.exportProjectJson(), saved);
    }
    for (const scope of [{ kind: 'global-role', role: 'melody' }, { kind: 'global-track', trackId: id },
      { kind: 'section-role', sectionId, role: 'melody' }, { kind: 'section-track', sectionId, trackId: id }]) {
      service.updatePartSettings(scope, { [key]: 101 }); assert.equal(service.exportProjectJson(), saved);
    }
    assert.equal(service.canRedo(), true);
  }
  const manual = [{ id: 'tonal-manual', midi: 62, startBeat: 1, durationBeats: 2, velocity: 90 }];
  service.updateTrackPhrase(id, manual);
  service.updatePartSettings({ kind: 'global-role', role: 'melody' }, { tonicNoteTarget: 0, modeNoteTarget: 0 });
  assert.deepEqual(phrase.phraseForTrack(service.project(), track()), manual);
  service.clearTrackPhrase(id);
  assert.deepEqual(phrase.layOutTrackPhrase(service.project(), track()), original);
});

test('P5e chord targeting and avoid notes preserve scoped harmony, history, manual ownership and MIDI', () => {
  const { service, phrase, midi, load } = fixture();
  const id = 'track-melody-1', sectionId = service.project().arrangement[0].id;
  const track = () => service.project().tracks.find(item => item.id === id);
  service.setSectionTrackPresence(0, id, true);
  const original = phrase.layOutTrackPhrase(service.project(), track());
  service.updatePartSettings({ kind: 'global-role', role: 'melody' }, { chordNoteTarget: 100 });
  assert.notDeepEqual(phrase.layOutTrackPhrase(service.project(), track()), original);
  service.undo();
  assert.equal(service.updateMelodyGlobal({ replaceAvoidNotes: 2 }), 'changed');
  assert.notDeepEqual(phrase.layOutTrackPhrase(service.project(), track()), original);
  service.updatePartSettings({ kind: 'global-role', role: 'melody' }, { chordNoteTarget: 100 });
  const diatonic = phrase.phraseForTrack(service.project(), track());
  service.setProgressionChord(0, { root: 1, spice: 'maj7', inversion: -2 });
  assert.notDeepEqual(phrase.phraseForTrack(service.project(), track()), diatonic);
  service.updatePartSettings({ kind: 'global-role', role: 'melody' }, { tonicNoteTarget: 40, modeNoteTarget: 60 });
  const globalContext = phrase.sectionMelodyContext(service.project());
  service.setSectionChordDegree(0, 0, 2);
  const localSection = service.project().arrangement[0];
  const localContext = phrase.sectionMelodyContext(service.project(), localSection);
  assert.notDeepEqual(localContext, globalContext);
  const { generateMelody } = load('core/music/melody-generator.ts');
  let expectedContext;
  generateMelody(BigInt(service.project().seed), service.project().key, service.project().scale,
    localSection.chordDegrees, track().generatorSettings, 0,
    notes => { expectedContext = load('core/music/melody-context.ts').melodyContext(notes, localSection.chordDegrees.length); },
    service.project().melodyGlobal);
  assert.deepEqual(localContext, expectedContext, 'section degree progression replaces global Chordlets for dependency generation');
  service.setProgressionChord(0, { root: 6, spice: 'dim', inversion: 3 });
  assert.deepEqual(phrase.sectionMelodyContext(service.project(), service.project().arrangement[0]), localContext);
  const patches = service.project().arrangement;
  service.updatePartSettings({ kind: 'global-role', role: 'melody' }, { chordNoteTarget: 70 });
  assert.deepEqual(service.project().arrangement, patches);
  let saved = service.exportProjectJson();
  const notes = phrase.layOutTrackPhrase(service.project(), track());
  const exported = new Midi(midi.generateCompositionMidi(service.project()));
  const output = exported.tracks.find(item => item.name === track().name).notes;
  assert.equal(output.length, notes.length);
  output.forEach((note, index) => {
    assert.equal(note.midi, notes[index].midi);
    assert.ok(Math.abs(note.ticks / exported.header.ppq - notes[index].startBeat) < 0.003);
    assert.ok(Math.abs(note.durationTicks / exported.header.ppq - notes[index].durationBeats) < 0.003);
  });
  service.undo(); service.redo(); assert.equal(service.exportProjectJson(), saved);
  assert.deepEqual(projectData(new (load('core/project/project.service.ts').ProjectService)().exportProjectJson()), projectData(saved));
  assert.equal(service.importProjectJson(saved), true); saved = service.exportProjectJson();
  service.updateSettings({ name: 'redo' }); service.undo();
  for (const [key, invalid] of [['chordNoteTarget', [-1, 101, 1.5, '50', null]], ['replaceAvoidNotes', [-1, 3, 0.5, '1', null]]]) {
    key === 'replaceAvoidNotes' ? service.updateMelodyGlobal({ [key]: service.project().melodyGlobal[key] }) : service.updatePartSettings({ kind: 'global-role', role: 'melody' }, { [key]: track().generatorSettings[key] });
    for (const value of invalid) {
      key === 'replaceAvoidNotes' ? service.updateMelodyGlobal({ [key]: value }) : service.updatePartSettings({ kind: 'global-role', role: 'melody' }, { [key]: value });
      const project = JSON.parse(saved); project.melodyGlobal[key] = value;
      assertRejectedProjectImport(service, JSON.stringify(project));
      assert.equal(service.exportProjectJson(), saved);
    }
    for (const scope of [{ kind: 'global-role', role: 'melody' }, { kind: 'global-track', trackId: id },
      { kind: 'section-role', sectionId, role: 'melody' }, { kind: 'section-track', sectionId, trackId: id }]) {
      service.updatePartSettings(scope, { [key]: key === 'replaceAvoidNotes' ? 1 : 101 }); assert.equal(service.exportProjectJson(), saved);
    }
    assert.equal(service.canRedo(), true);
  }
  const manual = [{ id: 'chord-manual', midi: 61, startBeat: 1, durationBeats: 2, velocity: 90 }];
  service.updateTrackPhrase(id, manual);
  service.updatePartSettings({ kind: 'global-role', role: 'melody' }, { chordNoteTarget: 0 });
  service.updateMelodyGlobal({ replaceAvoidNotes: 0 });
  assert.deepEqual(phrase.phraseForTrack(service.project(), track()), manual);
  assert.deepEqual(phrase.sectionMelodyContext(service.project(), service.project().arrangement[0]).pitches,
    load('core/music/melody-context.ts').melodyContext(manual, localSection.chordDegrees.length).pitches);
});

test('right-click knob resets use explicit defaults, cancel previews and respect disabled/no-op controls', () => {
  const { load, service } = fixture(), { CompactKnobComponent } = load('shared/compact-knob.component.ts');
  const knob = new CompactKnobComponent(); knob.minimum.set(0); knob.maximum.set(100); knob.defaultValue.set(25);
  knob.value.set(80); const commits = [], previews = [];
  knob.valueCommit.emit = value => { commits.push(value); service.updatePartSettings({ kind: 'global-role', role: 'melody' }, { newBlocksChance: value }); knob.value.set(value); };
  knob.valuePreview.emit = value => previews.push(value);
  let prevented = 0; const event = { preventDefault() { prevented++; }, stopPropagation() {} };
  knob.startDrag({ button: 0, pointerId: 1, clientY: 100, preventDefault() {}, stopPropagation() {}, currentTarget: { focus() {}, setPointerCapture() {} } });
  knob.moveDrag({ pointerId: 1, clientY: 90 }); assert.notEqual(knob.preview(), null);
  knob.resetDefault(event); assert.equal(knob.preview(), null); assert.deepEqual(commits, [25]);
  knob.finishDrag({ pointerId: 1 }); assert.deepEqual(commits, [25]);
  const saved = service.exportProjectJson(); service.updateSettings({ name: 'redo' }); service.undo();
  knob.resetDefault(event); assert.deepEqual(commits, [25]); assert.equal(service.canRedo(), true);
  knob.disabled.set(true); knob.value.set(90); knob.resetDefault(event); assert.deepEqual(commits, [25]);
  knob.disabled.set(false); knob.mixed.set(true); knob.value.set(25); knob.resetDefault(event); assert.deepEqual(commits, [25,25]);
  assert.equal(service.exportProjectJson(), saved); assert.equal(prevented, 4);
});

test('right-click combobox resets remember enabled defaults and emit one value change', () => {
  const { load } = fixture(), { SelectComponent } = load('shared/select.component.ts');
  const component = new SelectComponent(), changes = [], select = { disabled: false };
  component.element = { nativeElement: select };
  component.options.set([{ value: '', label: 'Mixed', disabled: true }, 'ASC', 'DESC', 'ANY']);
  component.value.set('ANY'); component.defaultValue.set('ASC');
  component.valueChange.emit = value => changes.push(value);
  const event = { preventDefault() {}, stopPropagation() {} };
  component.resetDefault(event); assert.equal(component.selectedValue(), 'ASC');
  component.resetDefault(event); assert.deepEqual(changes, ['ASC']);
  component.combobox.disabled.set(true); component.value.set('DESC');
  component.resetDefault(event); assert.equal(component.selectedValue(), 'DESC');
  component.combobox.disabled.set(false); component.options.set([{ value: 'ASC', label: 'Ascending', disabled: true }, 'DESC', 'ANY']);
  component.resetDefault(event); assert.equal(component.selectedValue(), 'DESC');
  component.defaultValue.set('missing'); component.resetDefault(event); assert.equal(component.selectedValue(), 'DESC');
  component.defaultValue.set(undefined); component.value.set('ANY');
  component.resetDefault(event); assert.equal(component.selectedValue(), 'DESC');
  assert.deepEqual(changes, ['ASC', 'DESC']);
});

test('P5 dependencies use section melody settings, manual ownership and absent source presence through MIDI', () => {
  const { service, phrase, midi } = fixture();
  const project = JSON.parse(service.exportProjectJson());
  project.arrangement = project.arrangement.slice(0, 2).map(section => ({ ...section, measures: 4,
    trackPresence: { ...section.trackPresence, melody: [false], drums: [true], arpeggio: [true] } }));
  assert.equal(service.importProjectJson(JSON.stringify(project)), true);
  const first = service.project().arrangement[0], second = service.project().arrangement[1];
  service.updateTrackPhrase('track-melody-1', [
    { id: 'source-a', midi: 61, startBeat: 0, durationBeats: 1, velocity: 90 },
    { id: 'source-b', midi: 74, startBeat: 2, durationBeats: 1, velocity: 90 },
  ]);
  service.updatePartSettings({ kind: 'global-track', trackId: 'track-drums-1' }, { rhythm: 'melody', velocityMin: 80, velocityMax: 80, swingPercent: 50 });
  const drums = () => service.project().tracks.find(track => track.id === 'track-drums-1');
  const arp = () => service.project().tracks.find(track => track.id === 'track-arpeggio-1');
  assert.deepEqual(phrase.layOutTrackPhrase(service.project(), drums()).map(note => note.startBeat), [0, 2, 16, 18]);
  service.updatePartSettings({ kind: 'global-track', trackId: arp().id }, { pattern: 'up', rhythm: 'full', hitsPerPattern: 4,
    chordSpan: 1, patternRepeat: 1, exceptionChance: 0, useOctaveAdjustments: false });
  service.updateHarmonySettings({ flattenVoicingChance: 0 });
  assert.equal(phrase.generateTrackPhrase(service.project(), arp())[0].midi, 36);
  service.updateTrackPhrase('track-melody-1', [{ id: 'replacement', midi: 62, startBeat: 1, durationBeats: 1, velocity: 90 }]);
  assert.deepEqual(phrase.layOutTrackPhrase(service.project(), drums()).map(note => note.startBeat), [1, 17]);
  service.clearTrackPhrase('track-melody-1');
  const before = phrase.sectionMelodyContext(service.project(), first);
  const local = { kind: 'section-track', sectionId: first.id, trackId: 'track-melody-1' };
  service.updatePartSettings(local, { speed: 100, melodyPatternOffsets: [1, 2, 3, 4], pauseChance: 0 });
  const changed = phrase.sectionMelodyContext(service.project(), service.project().arrangement[0]);
  assert.notDeepEqual(changed, before);
  const unmodified = phrase.sectionMelodyContext(service.project(), service.project().arrangement[1]);
  assert.deepEqual(unmodified, phrase.sectionMelodyContext(service.project()));
  const gridStarts = changed.pitches.flat().flatMap((pitch, index) => pitch > 0 ? [index / 4] : []);
  const arranged = phrase.layOutTrackPhrase(service.project(), drums());
  assert.deepEqual(arranged.filter(note => note.startBeat < 16).map(note => note.startBeat), gridStarts);
  const exported = new Midi(midi.generateCompositionMidi(service.project()));
  const drumMidi = exported.tracks.find(track => track.name === drums().name);
  assert.equal(drumMidi.channel, 9); assert.equal(drumMidi.notes.length, arranged.length);
  arranged.forEach(note => assert.ok(drumMidi.notes.some(item => item.midi === note.midi && Math.abs(item.ticks / exported.header.ppq - note.startBeat) < 0.003)));
  service.setSectionTrackPresence(0, drums().id, false);
  assert.equal(phrase.layOutTrackPhrase(service.project(), drums()).some(note => note.startBeat < 16), false);
  assert.ok(phrase.layOutTrackPhrase(service.project(), drums()).some(note => note.startBeat >= 16));
  service.undo(); assert.deepEqual(phrase.layOutTrackPhrase(service.project(), drums()), arranged);
  service.updateTrackPhrase(drums().id, [{ id: 'manual-drum', midi: 38, startBeat: 0, durationBeats: 1, velocity: 100 }]);
  assert.equal(phrase.phraseForTrack(service.project(), drums())[0].id, 'manual-drum');
  assert.equal(service.importProjectJson(service.exportProjectJson()), true);
  service.removeTrack('track-melody-1');
  assert.equal(phrase.generateTrackPhrase(service.project(), arp())[0].midi, 36);
});

test('P5c/P5d controls resolve mixed scopes, snapshots, independent arrays, resets and strict imports', () => {
  const { service, settings, load, stored } = fixture();
  const first = service.project().arrangement[0], second = service.project().arrangement[1];
  for (const [role, patch] of [
    ['arpeggio', { pattern: 'custom', arpPatternRotate: 3, arpPatternCustom: [-1, 0, 4], arpContour: [0, 2, -1],
      arpContourChordMode: true, useOctaveAdjustments: true }],
    ['drums', { ghosts: true, humanize: 100, rhythm: 'melody' }],
  ]) {
    const primary = `track-${role}-1`, duplicate = service.duplicateTrack(primary);
    const cell = { kind: 'section-role', sectionId: first.id, role };
    const local = { kind: 'section-track', sectionId: first.id, trackId: duplicate };
    service.updatePartSettings(cell, patch);
    const effective = () => settings.resolvePartTrack(service.project().tracks.find(track => track.id === duplicate), service.project().arrangement[0]).generatorSettings;
    for (const [key, value] of Object.entries(patch)) assert.deepEqual(effective()[key], value);
    const field = role === 'arpeggio' ? 'arpContour' : 'ghosts', exception = role === 'arpeggio' ? [] : false;
    service.updatePartSettings(local, { [field]: exception }); assert.deepEqual(effective()[field], exception);
    service.freezePartSettings(cell);
    service.applyPartSettingsToSections(cell, [second.id], 'effective');
    const source = service.project().arrangement[0].trackPartOverrides[duplicate];
    const copied = service.project().arrangement[1].trackPartOverrides[duplicate];
    assert.deepEqual(source, copied);
    if (role === 'arpeggio') assert.notEqual(source.arpPatternCustom, copied.arpPatternCustom);
    service.copyPartSettingsToGlobals(local);
    assert.deepEqual(service.project().tracks.find(track => track.id === duplicate).generatorSettings[field], exception);
    service.resetPartSettings(local, field); assert.deepEqual(effective()[field], patch[field]);
    const before = service.exportProjectJson(); service.undo(); service.redo(); assert.equal(service.exportProjectJson(), before);
  }
  const before = service.exportProjectJson();
  for (const [role, bad] of [['arpeggio', { arpContour: [1.5] }], ['arpeggio', { arpPatternCustom: [] }],
    ['arpeggio', { arpPatternRotate: 9 }], ['arpeggio', { correctMelodyNotes: true }], ['drums', { humanize: 101 }],
    ['drums', { ghosts: 1 }], ['bass', { rhythm: 'melody' }], ['chords', { rhythm: 'melody' }]]) {
    service.updatePartSettings({ kind: 'global-role', role }, bad);
    const project = JSON.parse(before); Object.assign(project.tracks.find(track => track.role === role).generatorSettings, bad);
    assertRejectedProjectImport(service, JSON.stringify(project), JSON.stringify(bad));
  }
  assert.equal(service.exportProjectJson(), before);
  assert.equal(service.importProjectJson(before), true);
  const { ProjectService } = load('core/project/project.service.ts');
  assert.equal(new ProjectService().exportProjectJson(), service.exportProjectJson());
  assert.ok(stored.get('vibecomposer.project.v12'));
});

test('P5d Multiply Hits transforms mixed drums atomically at global/local scopes and preserves ownership', () => {
  const { service, phrase } = fixture();
  const first = service.project().arrangement[0];
  const id = service.duplicateTrack('track-drums-1');
  service.updatePartSettings({ kind: 'global-track', trackId: id }, { hitsPerPattern: 8, rhythm: 'custom', patternShift: 1,
    customPattern: Array.from({ length: 32 }, (_, index) => index % 4 === 0 || index % 4 === 3 ? 1 : 0) });
  service.updateTrackPhrase(id, [{ id: 'manual', midi: 38, startBeat: 0, durationBeats: 1, velocity: 90 }]);
  const before = service.exportProjectJson();
  assert.equal(service.multiplyDrumHits({ kind: 'global-role', role: 'drums' }, 1.5), 'changed');
  const settings = service.project().tracks.filter(track => track.role === 'drums').map(track => track.generatorSettings);
  assert.deepEqual(settings.map(part => part.hitsPerPattern), [6, 12]);
  assert.equal(settings[1].patternShift, 0);
  assert.deepEqual(settings[1].customPattern.slice(0, 8), [1, 1, 0, 0, 1, 1, 0, 0]);
  assert.equal(phrase.phraseForTrack(service.project(), service.project().tracks.find(track => track.id === id))[0].id, 'manual');
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.redo();
  const local = { kind: 'section-role', role: 'drums', sectionId: first.id };
  service.updatePartSettings(local, { ghosts: true });
  const otherBefore = JSON.stringify(service.project().arrangement[1]);
  const globalsBefore = JSON.stringify(service.project().tracks);
  assert.equal(service.multiplyDrumHits(local, 0.5), 'changed');
  assert.equal(JSON.stringify(service.project().tracks), globalsBefore);
  assert.equal(JSON.stringify(service.project().arrangement[1]), otherBefore);
  assert.deepEqual(service.project().arrangement[0].rolePartOverrides.drums, { ghosts: true });
  assert.deepEqual(service.project().arrangement[0].trackPartOverrides['track-drums-1'], { hitsPerPattern: 3 });
  assert.deepEqual(service.project().arrangement[0].trackPartOverrides[id], { hitsPerPattern: 6 });
  service.updatePartSettings({ kind: 'section-track', trackId: id, sectionId: first.id }, { rhythm: 'melody' });
  const blocked = service.exportProjectJson();
  assert.equal(service.multiplyDrumHits(local, 2), 'invalid');
  assert.equal(service.multiplyDrumHits({ kind: 'section-role', role: 'drums', sectionId: 'deleted' }, 2), 'invalid');
  assert.equal(service.multiplyDrumHits({ kind: 'global-role', role: 'bass' }, 2), 'invalid');
  assert.equal(service.exportProjectJson(), blocked);
  assert.equal(service.importProjectJson(blocked), true);
});

test('P5 editor supports empty contour and separates pitch direction from melody rhythm and hit actions', () => {
  const { load } = fixture();
  const { PartSettingsEditorComponent } = load('features/part-settings/part-settings-editor.component.ts');
  const editor = new PartSettingsEditorComponent(); editor.role.set('arpeggio');
  editor.values.set({ pattern: 'custom', rhythm: 'melody', arpContour: [1] });
  assert.ok(editor.controls().some(control => control.key === 'arpPatternCustom'));
  assert.equal(editor.controls().some(control => control.key === 'hitsPerPattern'), false);
  assert.equal(editor.rhythmPreview(), undefined);
  let change; editor.settingsChanged.emit = value => change = value;
  const input = { value: '', setCustomValidity(value) { assert.equal(value, ''); } };
  editor.commitText(editor.controls().find(control => control.key === 'arpContour'), { target: input });
  assert.deepEqual(change, { key: 'arpContour', value: [] });
});

test('chord articulation resolves scopes, mixed values, snapshots, manual ownership and MIDI', () => {
  const { service, settings, phrase, midi, ui, workspace, load } = fixture();
  const id = 'track-chords-1', track = () => service.project().tracks.find(track => track.id === id);
  const [first, second] = service.project().arrangement;
  const cell = { kind: 'section-role', sectionId: first.id, role: 'chords' };
  const part = { kind: 'section-track', sectionId: first.id, trackId: id };
  service.project().arrangement.forEach((_, index) => service.setSectionTrackPresence(index, id, true));
  service.updatePartSettings({ kind: 'global-role', role: 'chords' }, { rhythm: 'half', hitsPerPattern: 8 });
  const baseline = phrase.layOutTrackPhrase(service.project(), track());
  const patch = { patternJoinMode: 'EXPAND', transitionChance: 100, transitionSplit: 625, strum: 250,
    strumType: 'ARP_U', strumPauseChance: 0 };
  service.updatePartSettings(cell, patch);
  const notes = phrase.layOutTrackPhrase(service.project(), track());
  assert.notDeepEqual(notes.filter(note => note.startBeat < first.measures * 4), baseline.filter(note => note.startBeat < first.measures * 4));
  assert.deepEqual(notes.filter(note => note.startBeat >= first.measures * 4), baseline.filter(note => note.startBeat >= first.measures * 4));
  service.undo(); assert.deepEqual(phrase.layOutTrackPhrase(service.project(), track()), baseline);
  service.redo(); assert.deepEqual(phrase.layOutTrackPhrase(service.project(), track()), notes);
  const parsed = new Midi(midi.generateCompositionMidi(service.project()));
  const exported = parsed.tracks.find(candidate => candidate.name === track().name).notes;
  for (const note of notes) assert.ok(exported.some(candidate => candidate.midi === note.midi
    && Math.abs(candidate.ticks / parsed.header.ppq - note.startBeat) < 0.003
    && Math.abs(candidate.durationTicks / parsed.header.ppq - note.durationBeats) < 0.003));
  service.freezePartSettings(part);
  for (const [key, value] of Object.entries(patch)) assert.equal(service.project().arrangement[0].trackPartOverrides[id][key], value);
  service.applyPartSettingsToSections(part, [second.id], 'effective');
  service.updatePartSettings({ kind: 'global-track', trackId: id }, { strum: 1000, strumType: 'ARP_D' });
  assert.equal(settings.resolvePartTrack(track(), service.project().arrangement[0]).generatorSettings.strum, 250);
  const duplicate = service.duplicateTrack(id);
  service.updatePartSettings({ kind: 'section-track', sectionId: first.id, trackId: duplicate }, { strum: 500 });
  ui.selectCell(first.id, 'chords'); assert.equal(workspace.partValues().strum, null);
  assert.equal(service.copyPartSettingsToGlobals(cell), 'changed');
  assert.equal(track().generatorSettings.strum, 250);
  assert.equal(service.project().tracks.find(item => item.id === duplicate).generatorSettings.strum, 500);
  const saved = service.exportProjectJson(), beforeImport = phrase.layOutTrackPhrase(service.project(), track());
  assert.equal(service.importProjectJson(saved), true);
  assert.deepEqual(phrase.layOutTrackPhrase(service.project(), track()), beforeImport);
  const restored = new (load('core/project/project.service.ts').ProjectService)();
  assert.equal(restored.project().arrangement[0].trackPartOverrides[id].strumType, 'ARP_U');
  service.updateTrackPhrase(id, [{ id: 'manual', midi: 60, startBeat: 0, durationBeats: 1, velocity: 80 }]);
  service.updatePartSettings(part, { strumPauseChance: 100, transitionChance: 0 });
  assert.ok(phrase.layOutTrackPhrase(service.project(), track()).every(note => note.midi === 60 && note.durationBeats === 1));
});

test('copy to globals preserves mixed cell members, track exceptions and local ownership with one history entry', () => {
  const { service, settings, load } = fixture();
  service.addTrack('bass');
  const sectionId = service.project().arrangement[0].id;
  const cell = { kind: 'section-role', sectionId, role: 'bass' };
  service.updatePartSettings(cell, { transpose: -12 });
  service.updatePartSettings({ kind: 'section-track', sectionId, trackId: 'track-bass-2' }, { transpose: 12, octaveInterval: true });
  service.updateTrackPhrase('track-bass-1', [{ id: 'manual', midi: 60, startBeat: 0, durationBeats: 1, velocity: 80 }]);
  service.setTrackRerollLock('track-bass-2', true);
  const before = service.exportProjectJson(), previous = service.project();
  assert.equal(service.partSettingsGlobalsWouldChange(cell), true);
  assert.equal(service.copyPartSettingsToGlobals(cell), 'changed');
  const next = service.project(), after = service.exportProjectJson();
  assert.strictEqual(next.arrangement, previous.arrangement);
  for (const track of previous.tracks) {
    const result = next.tracks.find(item => item.id === track.id);
    if (track.role !== 'bass') assert.strictEqual(result, track);
    else {
      assert.deepEqual(settings.settingsValues(result.generatorSettings, 'bass'), settings.settingsValues(settings.resolvePartTrack(track, previous.arrangement[0]).generatorSettings, 'bass'));
      for (const key of ['mix', 'editedPhrase', 'rerollLocked', 'midiChannel']) assert.deepEqual(result[key], track[key]);
    }
  }
  assert.equal(next.tracks.find(track => track.role === 'bass').generatorSettings.transpose, -12);
  assert.equal(next.tracks.find(track => track.id === 'track-bass-2').generatorSettings.transpose, 12);
  assert.equal(service.partSettingsGlobalsWouldChange(cell), false);
  assert.equal(service.copyPartSettingsToGlobals(cell), 'unchanged');
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.redo(); assert.equal(service.exportProjectJson(), after);
  const { ProjectService } = load('core/project/project.service.ts');
  assert.deepEqual(projectData(new ProjectService().exportProjectJson()), projectData(after));
});

test('copy to globals rejects cross-section velocity conflicts atomically and stale targets safely', () => {
  const { service } = fixture();
  const [source, destination] = service.project().arrangement;
  const scope = { kind: 'section-track', sectionId: source.id, trackId: 'track-bass-1' };
  service.updatePartSettings(scope, { velocityMin: 80, velocityMax: 100 });
  service.updatePartSettings({ ...scope, sectionId: destination.id }, { velocityMax: 75 });
  const before = service.exportProjectJson();
  service.updateSettings({ tempoBpm: 131 }); service.undo();
  assert.equal(service.copyPartSettingsToGlobals(scope), 'invalid');
  assert.equal(service.exportProjectJson(), before); assert.equal(service.canRedo(), true);
  assert.equal(service.copyPartSettingsToGlobals({ ...scope, trackId: 'deleted' }), 'invalid');
  assert.equal(service.copyPartSettingsToGlobals({ ...scope, sectionId: 'deleted' }), 'invalid');
});

test('global copy ignores inherited defaults and a single-track copy leaves other mixed members intact', () => {
  const { service } = fixture();
  const sectionId = service.project().arrangement[0].id;
  for (const role of ['melody','bass','chords','arpeggio','drums']) {
    const cell = { kind: 'section-role', sectionId, role };
    assert.equal(service.partSettingsGlobalsWouldChange(cell), false);
    assert.equal(service.copyPartSettingsToGlobals(cell), 'unchanged');
  }
  service.addTrack('bass');
  const scope = { kind: 'section-track', sectionId, trackId: 'track-bass-2' };
  service.updatePartSettings({ kind: 'section-role', sectionId, role: 'bass' }, { noteVariation: 35 });
  service.updatePartSettings(scope, { noteVariation: 45 });
  const before = service.project();
  assert.equal(service.copyPartSettingsToGlobals(scope), 'changed');
  assert.strictEqual(service.project().tracks.find(track => track.id === 'track-bass-1'), before.tracks.find(track => track.id === 'track-bass-1'));
  assert.strictEqual(service.project().tracks.find(track => track.role === 'bass').generatorSettings, before.tracks.find(track => track.role === 'bass').generatorSettings);
  assert.equal(service.project().tracks.find(track => track.id === scope.trackId).generatorSettings.noteVariation, 45);
});

test('bass join articulation resolves at all scopes, persists snapshots and exports sustained MIDI', () => {
  const { service, settings, phrase, midi, ui, workspace, load } = fixture();
  const id = 'track-bass-1', track = () => service.project().tracks.find(track => track.id === id);
  const [first, second] = service.project().arrangement;
  const global = { kind: 'global-track', trackId: id };
  const cell = { kind: 'section-role', sectionId: first.id, role: 'bass' };
  const part = { kind: 'section-track', sectionId: first.id, trackId: id };
  service.project().arrangement.forEach((_, index) => service.setSectionTrackPresence(index, id, true));
  service.updatePartSettings({ kind: 'global-role', role: 'bass' }, { rhythm: 'half', hitsPerPattern: 8, noteVariation: 100 });
  assert.equal(settings.settingsValues(track().generatorSettings, 'bass').patternJoinMode, 'NOJOIN');
  const subdivisions = phrase.layOutTrackPhrase(service.project(), track());
  service.updatePartSettings(cell, { patternJoinMode: 'EXPAND' });
  const expanded = phrase.layOutTrackPhrase(service.project(), track());
  assert.ok(expanded.some(note => note.startBeat < first.measures * 4 && note.durationBeats === 1));
  assert.deepEqual(expanded.filter(note => note.startBeat >= first.measures * 4), subdivisions.filter(note => note.startBeat >= first.measures * 4));
  service.undo(); assert.deepEqual(phrase.layOutTrackPhrase(service.project(), track()), subdivisions);
  service.redo(); assert.deepEqual(phrase.layOutTrackPhrase(service.project(), track()), expanded);
  service.updatePartSettings(part, { patternJoinMode: 'JOIN', rhythm: 'full' });
  const joined = phrase.layOutTrackPhrase(service.project(), track());
  assert.equal(joined.filter(note => note.startBeat < first.measures * 4).length, first.measures);
  assert.ok(joined.filter(note => note.startBeat < first.measures * 4).every(note => note.durationBeats === 4));
  const parsed = new Midi(midi.generateCompositionMidi(service.project()));
  const exported = parsed.tracks.find(candidate => candidate.name === track().name).notes;
  for (const note of joined) assert.ok(exported.some(candidate => candidate.midi === note.midi
    && Math.abs(candidate.ticks / parsed.header.ppq - note.startBeat) < 0.003
    && Math.abs(candidate.durationTicks / parsed.header.ppq - note.durationBeats) < 0.003));
  service.freezePartSettings(part);
  assert.equal(service.project().arrangement[0].trackPartOverrides[id].patternJoinMode, 'JOIN');
  service.applyPartSettingsToSections(part, [second.id], 'overrides');
  assert.equal(service.project().arrangement[1].trackPartOverrides[id].patternJoinMode, 'JOIN');
  service.applyPartSettingsToSections(part, [second.id], 'effective');
  service.updatePartSettings(global, { patternJoinMode: 'EXPAND' });
  assert.equal(settings.resolvePartTrack(track(), service.project().arrangement[0]).generatorSettings.patternJoinMode, 'JOIN');
  const duplicate = service.duplicateTrack(id);
  service.updatePartSettings({ kind: 'section-track', sectionId: first.id, trackId: duplicate }, { patternJoinMode: 'NOJOIN' });
  ui.selectCell(first.id, 'bass'); assert.equal(workspace.partValues().patternJoinMode, null);
  const saved = service.exportProjectJson(), beforeImport = phrase.layOutTrackPhrase(service.project(), track());
  assert.equal(service.importProjectJson(saved), true);
  assert.deepEqual(service.project().arrangement[0].trackPartOverrides, JSON.parse(saved).arrangement[0].trackPartOverrides);
  assert.deepEqual(phrase.layOutTrackPhrase(service.project(), track()), beforeImport);
  const restored = new (load('core/project/project.service.ts').ProjectService)();
  assert.equal(restored.project().arrangement[0].trackPartOverrides[id].patternJoinMode, 'JOIN');
  service.resetPartSettings(part, 'patternJoinMode');
  assert.equal(settings.resolvePartTrack(track(), service.project().arrangement[0]).generatorSettings.patternJoinMode, 'EXPAND');
  const manual = [{ id: 'manual-bass', midi: 40, startBeat: 0, durationBeats: 0.75, velocity: 77 }];
  service.updateTrackPhrase(id, manual); service.updatePartSettings(part, { patternJoinMode: 'JOIN', octaveInterval: true });
  assert.deepEqual(phrase.phraseForTrack(service.project(), track()), manual);
  assert.ok(phrase.layOutTrackPhrase(service.project(), track()).every(note => note.midi === 40 && note.durationBeats === 0.75));
  const { PartSettingsEditorComponent } = load('features/part-settings/part-settings-editor.component.ts');
  const editor = new PartSettingsEditorComponent(); editor.role.set('bass'); editor.values.set({ rhythm: 'full' });
  assert.ok(editor.controls().some(control => control.key === 'patternJoinMode'));
  editor.advanced.set(false); assert.equal(editor.controls().some(control => control.key === 'patternJoinMode'), false);
  editor.advanced.set(true); editor.values.set({ rhythm: 'bass1' });
  assert.equal(editor.controls().some(control => control.key === 'patternJoinMode'), false);
});

test('all-role scoped timing, seeds and arrangement presence survive history, snapshots, JSON and manual replacements', () => {
  const { service, settings, phrase, midi, ui, workspace } = fixture();
  for (const role of ['melody', 'bass', 'chords', 'arpeggio', 'drums']) {
    const id = `track-${role}-1`, track = () => service.project().tracks.find(track => track.id === id);
    service.project().arrangement.forEach((_, index) => service.setSectionTrackPresence(index, id, true));
    const [first, second] = service.project().arrangement;
    const cell = { kind: 'section-role', sectionId: first.id, role };
    const part = { kind: 'section-track', sectionId: first.id, trackId: id };
    service.updatePartSettings({ kind: 'global-role', role }, { patternSeed: 123 });
    service.updatePartSettings({ kind: 'global-track', trackId: id }, { feedbackVol: 80 });
    const before = phrase.layOutTrackPhrase(service.project(), track());
    service.updatePartSettings(cell, { offset: 250, feedbackCount: 1, feedbackDuration: 500 });
    service.updatePartSettings(part, { feedbackVol: 50 });
    const after = phrase.layOutTrackPhrase(service.project(), track());
    const end = first.measures * 4;
    assert.deepEqual(after.filter(note => note.startBeat >= end + 1), before.filter(note => note.startBeat >= end + 1));
    assert.equal(after.length - before.length, before.filter(note => note.startBeat < end).length,
      'feedback survives even when its onset crosses the section boundary');
    const exported = new Midi(midi.generateCompositionMidi(service.project()));
    const notes = exported.tracks.find(midiTrack => midiTrack.name === track().name).notes;
    assert.equal(notes.length, after.length);
    for (const note of after) assert.ok(notes.some(exportedNote => exportedNote.midi === note.midi
      && Math.abs(exportedNote.ticks / exported.header.ppq - note.startBeat) < 0.003
      && Math.abs(exportedNote.velocity * 127 - note.velocity) < 0.001));
    service.updateTrack(id, { mix: { muted: true } });
    assert.deepEqual(phrase.layOutTrackPhrase(service.project(), track()), after, 'mute leaves score notes intact');
    service.updateTrack(id, { mix: { solo: true } });
    assert.deepEqual(phrase.layOutTrackPhrase(service.project(), track()), after, 'solo leaves score notes intact');
    service.updateTrack(id, { mix: { solo: false } });
    service.setSectionTrackPresence(0, id, false);
    assert.equal(service.project().arrangement[0].trackPresence[id], false);
    assert.equal(phrase.layOutTrackPhrase(service.project(), track()).some(note => note.startBeat < end), false);
    service.undo(); assert.deepEqual(phrase.layOutTrackPhrase(service.project(), track()), after);
    service.redo(); service.undo();
    service.freezePartSettings(part);
    const frozen = service.project().arrangement[0].trackPartOverrides[id];
    assert.equal(frozen.feedbackCount, 1); assert.equal(frozen.feedbackVol, 50); assert.equal(frozen.patternSeed, 123);
    service.applyPartSettingsToSections(part, [second.id], 'effective');
    assert.deepEqual(service.project().arrangement[1].trackPartOverrides[id], frozen);
    const added = service.duplicateTrack(id);
    service.updatePartSettings({ kind: 'section-track', sectionId: first.id, trackId: added }, { offset: -250 });
    ui.selectCell(first.id, role); assert.equal(workspace.partValues().offset, null);
    assert.equal(service.importProjectJson(service.exportProjectJson()), true);
    const manual = [{ id: 'manual', midi: role === 'drums' ? 38 : 60, startBeat: 0, durationBeats: 1, velocity: 90 }];
    service.updateTrackPhrase(id, manual);
    service.updatePartSettings(part, { feedbackCount: 5 });
    assert.deepEqual(phrase.phraseForTrack(service.project(), track()), manual);
    assert.ok(phrase.layOutTrackPhrase(service.project(), track()).some(note => note.startBeat === 0));
    assert.equal(service.resetCellPartSettings(cell), 'changed');
    assert.equal(service.project().arrangement[0].rolePartOverrides?.[role], undefined);
  }
});

test('drum rhythm and dynamics resolve all scopes, mixed values, frozen snapshots and parsed MIDI without changing identity', () => {
  const { service, settings, ui, workspace, phrase, midi } = fixture();
  const first = 'track-drums-1', second = service.duplicateTrack(first);
  service.updateTrackGeneratorSettings(second, { pitch: 38 });
  const sectionId = service.project().arrangement[1].id;
  const cell = { kind: 'section-role', sectionId, role: 'drums' }, scope = { kind: 'section-track', sectionId, trackId: second };
  service.updatePartSettings({ kind: 'global-role', role: 'drums' }, { rhythm: 'half', hitsPerPattern: 8 });
  service.updatePartSettings({ kind: 'global-track', trackId: second }, { rhythm: 'sparse', hitsPerPattern: 16 });
  ui.selectRole('drums'); assert.equal(workspace.partValues().rhythm, null);
  service.updatePartSettings(cell, { rhythm: 'custom', hitsPerPattern: 8, customPattern: Array.from({ length: 32 }, (_, index) => +(index % 3 === 0)),
    useCustomVelocities: true, customVelocities: Array.from({ length: 32 }, (_, index) => index === 0 ? 0 : 100), swingPercent: 66, noteLengthMultiplier: 50 });
  service.updatePartSettings(scope, { hitsPerPattern: 4 });
  ui.selectCell(sectionId, 'drums'); assert.equal(workspace.partValues().hitsPerPattern, null);
  const before = service.exportProjectJson();
  assert.equal(service.freezePartSettings(cell), 'changed');
  const frozen = service.project().arrangement[1];
  assert.equal(frozen.trackPartOverrides[first].hitsPerPattern, 8);
  assert.equal(frozen.trackPartOverrides[second].hitsPerPattern, 4);
  assert.equal(frozen.trackPartOverrides[first].pitch, undefined);
  assert.equal(settings.resolvePartTrack(service.project().tracks.find(track => track.id === second), frozen).generatorSettings.pitch, 38);
  service.undo(); assert.equal(service.exportProjectJson(), before); service.redo();
  assert.equal(service.applyPartSettingsToSections(cell, [service.project().arrangement[2].id], 'effective'), 'changed');
  service.project().arrangement.forEach((_, index) => {
    service.setSectionTrackPresence(index, first, true); service.setSectionTrackPresence(index, second, true);
  });
  const exported = new Midi(midi.generateCompositionMidi(service.project()));
  for (const id of [first, second]) {
    const track = service.project().tracks.find(track => track.id === id);
    const notes = phrase.layOutTrackPhrase(service.project(), track);
    assert.ok(notes.length > 0 && notes.every(note => note.midi === track.generatorSettings.pitch));
    const midiTrack = exported.tracks.find(item => item.name === track.name);
    assert.equal(midiTrack.channel, 9); assert.equal(midiTrack.notes.length, notes.length);
    midiTrack.notes.forEach((note, index) => {
      assert.equal(note.midi, notes[index].midi);
      assert.ok(Math.abs(note.ticks / exported.header.ppq - notes[index].startBeat) < 0.003);
      assert.ok(Math.abs(note.durationTicks / exported.header.ppq - notes[index].durationBeats) < 0.003,
        `${id} note ${index}: ${note.durationTicks / exported.header.ppq} vs ${notes[index].durationBeats}`);
      assert.ok(Math.abs(note.velocity * 127 - notes[index].velocity) < 0.001);
    });
    assert.equal(workspace.scoreNotes().filter(note => note.part === id).length, notes.length);
  }
  assert.equal(service.importProjectJson(service.exportProjectJson()), true);
  assert.equal(service.resetCellPartSettings(cell), 'changed');
  assert.equal(service.project().tracks.find(track => track.id === second).generatorSettings.pitch, 38);
});

test('P5e catalogue inputs preserve independent arrays, atomic history, scopes and manual ownership', () => {
  const { service, phrase, midi, load } = fixture();
  const id = 'track-melody-1', sectionId = service.project().arrangement[0].id;
  service.setSectionTrackPresence(0, id, true);
  service.updatePartSettings({ kind: 'section-track', sectionId, trackId: id }, { speed: 100 });
  const track = () => service.project().tracks.find(item => item.id === id);
  const tracks = service.project().tracks, sections = service.project().arrangement;
  const original = phrase.layOutTrackPhrase(service.project(), track()), before = service.exportProjectJson();
  const weights = [100, 20, 0, 50, 30, 100, 100], preference = [0, 7, 4, 3, 1, 2, 5, 6];
  assert.equal(service.updateMelodyGlobal({ blockTypeWeights: weights, blockChangePreference: preference }), 'changed');
  const saved = service.exportProjectJson(), notes = phrase.layOutTrackPhrase(service.project(), track());
  assert.notDeepEqual(notes, original);
  assert.deepEqual(service.project().tracks, tracks); assert.deepEqual(service.project().arrangement, sections);
  weights[0] = 0; preference.reverse();
  assert.equal(service.exportProjectJson(), saved, 'caller arrays cannot mutate the committed project');
  const parsed = new Midi(midi.generateCompositionMidi(service.project()));
  const exported = parsed.tracks.find(item => item.name === track().name).notes;
  assert.equal(exported.length, notes.length);
  exported.forEach((note, index) => {
    assert.equal(note.midi, notes[index].midi);
    assert.ok(Math.abs(note.ticks / parsed.header.ppq - notes[index].startBeat) < 0.003);
    assert.ok(Math.abs(note.durationTicks / parsed.header.ppq - notes[index].durationBeats) < 0.003);
  });
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.redo(); assert.equal(service.exportProjectJson(), saved);
  service.updateSettings({ name: 'redo' }); service.undo();
  assert.equal(service.updateMelodyGlobal(JSON.parse(saved).melodyGlobal), 'unchanged');
  assert.equal(service.canRedo(), true);
  const { generateMelody } = load('core/music/melody-generator.ts');
  for (const patch of [{ newBlocksChance: -1 }, { newBlocksChance: 101 }, { newBlocksChance: 1.5 }, { newBlocksChance: '0' },
    { blockTypeWeights: null }, { blockTypeWeights: [100] }, { blockTypeWeights: [100,100,100,100,100,100,101] },
    { blockTypeWeights: [100,100,100,100,100,100,0.5] }, { blockTypeWeights: Array(7) },
    { blockChangePreference: [0] }, { blockChangePreference: [0,1,2,3,4,5,6,6] },
    { blockChangePreference: [0,1,2,3,4,5,6,8] }, { blockChangePreference: '0,7,4,3,1,2,5,6' }]) {
    assert.equal(service.updateMelodyGlobal(patch), 'invalid');
    assert.throws(() => generateMelody(42n, 'C', 'major', [1], {}, 0, undefined, patch), RangeError);
    const imported = JSON.parse(saved); imported.melodyGlobal = { ...imported.melodyGlobal, ...patch };
    assertRejectedProjectImport(service, JSON.stringify(imported));
    assert.equal(service.exportProjectJson(), saved); assert.equal(service.canRedo(), true);
  }
  for (const scope of [{ kind: 'global-role', role: 'melody' }, { kind: 'global-track', trackId: id },
    { kind: 'section-role', sectionId, role: 'melody' }, { kind: 'section-track', sectionId, trackId: id }]) {
    service.updatePartSettings(scope, { newBlocksChance: 0, blockTypeWeights: [0,0,0,0,0,0,0], blockChangePreference: [] });
    assert.equal(service.exportProjectJson(), saved, 'global catalogue inputs cannot enter part patches');
  }
  const { ProjectService } = load('core/project/project.service.ts');
  assert.deepEqual(projectData(new ProjectService().exportProjectJson()), projectData(saved));
  assert.equal(service.importProjectJson(saved), true);
  assert.deepEqual(phrase.layOutTrackPhrase(service.project(), track()), notes);
  const manual = [{ id: 'catalogue-manual', midi: 72, startBeat: 1, durationBeats: 1, velocity: 95 }];
  service.updateTrackPhrase(id, manual); service.updatePartSettings({ kind: 'global-track', trackId: id }, { newBlocksChance: 50 });
  service.updateMelodyGlobal({ blockChangePreference: [] });
  assert.deepEqual(phrase.phraseForTrack(service.project(), track()), manual);
});

test('P5e catalogue preference editor rejects partial or duplicate lists without committing', () => {
  const { service, load } = fixture();
  const { MelodyGlobalSettingsComponent } = load('features/settings/melody-global-settings.component.ts');
  const editor = new MelodyGlobalSettingsComponent(), commits = [];
  editor.settingsChanged.emit = patch => { commits.push(patch); service.updateMelodyGlobal(patch); editor.values.set(service.project().melodyGlobal ?? {}); };
  editor.useBlockPreference(true);
  assert.deepEqual(service.project().melodyGlobal.blockChangePreference, [0,7,4,3,1,2,5,6]);
  for (const text of ['0,1', '0,1,2,3,4,5,6,6', '0,1,2,3,4,5,6,8', '0,1,2,3,4,5,6,7.0', '0,1,2,3,4,5,6,7,']) {
    const input = { value: text }; editor.changeBlockPreference({ target: input });
    assert.equal(commits.length, 1); assert.ok(editor.blockPreferenceError());
    assert.equal(input.value, '0, 7, 4, 3, 1, 2, 5, 6');
  }
  editor.changeBlockPreference({ target: { value: '7, 6,5,4,3,2,1,0' } });
  assert.deepEqual(service.project().melodyGlobal.blockChangePreference, [7,6,5,4,3,2,1,0]);
  assert.equal(editor.blockPreferenceError(), '');
  editor.useBlockPreference(false); assert.deepEqual(service.project().melodyGlobal.blockChangePreference, []);
  editor.changeBlockWeight(2, 40); assert.deepEqual(service.project().melodyGlobal.blockTypeWeights, [100,100,40,100,100,100,100]);
  editor.changeBlockPreference({ target: { value: '   ' } }); assert.deepEqual(commits.at(-1), { blockChangePreference: [] });
});

test('P5e pattern effects update section dependencies and MIDI', () => {
  const { service, phrase, midi, load } = fixture();
  const cases = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'fixtures/melody-effects.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  const sample = cases[8], project = JSON.parse(service.exportProjectJson());
  project.seed = sample.seed; project.progression = [1, 1, 1]; delete project.progressionChords;
  project.tracks.find(track => track.role === 'melody').generatorSettings = Object.fromEntries(Object.entries(sample.settings)
    .filter(([key]) => !['doubledRhythmChance', 'leadChordsChance', 'maxNoteExceptions', 'noteExceptionChance'].includes(key)));
  project.arrangement = [{ ...project.arrangement[0], measures: 3, trackPresence: {
    'track-melody-1': true, 'track-drums-1': true, 'track-arpeggio-1': true,
  } }];
  assert.equal(service.importProjectJson(JSON.stringify(project)), true);
  service.updatePartSettings({ kind: 'global-track', trackId: 'track-drums-1' }, { rhythm: 'melody', swingPercent: 50 });
  service.updatePartSettings({ kind: 'global-track', trackId: 'track-arpeggio-1' }, { rhythm: 'melody', swingPercent: 50 });
  const track = role => service.project().tracks.find(track => track.role === role);
  const context = () => phrase.sectionMelodyContext(service.project(), service.project().arrangement[0]);
  const original = phrase.generateTrackPhrase(service.project(), track('melody'));
  const originalContext = context(), before = service.exportProjectJson();
  assert.equal(service.updateMelodyGlobal({ patternEffect: 0 }), 'changed');
  const rhythm = phrase.generateTrackPhrase(service.project(), track('melody'));
  assert.notDeepEqual(rhythm.map(note => note.midi), original.map(note => note.midi));
  assert.notDeepEqual(context(), originalContext);
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.redo(); assert.deepEqual(phrase.generateTrackPhrase(service.project(), track('melody')), rhythm);
  for (const role of ['melody', 'arpeggio', 'drums']) {
    const expected = phrase.layOutTrackPhrase(service.project(), track(role));
    const exported = new Midi(midi.generateCompositionMidi(service.project()));
    const notes = exported.tracks.find(item => item.name === track(role).name).notes;
    assert.equal(notes.length, expected.length);
    notes.forEach((note, index) => {
      assert.equal(note.midi, expected[index].midi);
      assert.ok(Math.abs(note.ticks / exported.header.ppq - expected[index].startBeat) < 0.003);
      assert.ok(Math.abs(note.durationTicks / exported.header.ppq - expected[index].durationBeats) < 0.003);
    });
  }
  const saved = service.exportProjectJson(), { generateMelody } = load('core/music/melody-generator.ts');
  service.updateSettings({ name: 'redo' }); service.undo();
  for (const patch of [{ patternEffect: 3 }, { patternEffect: null }, { arpySurprises: true }, { arpySurprises: false }]) {
    assert.equal(service.updateMelodyGlobal(patch), 'invalid');
    assert.throws(() => generateMelody(42n, 'C', 'major', [1], track('melody').generatorSettings, 0, undefined, patch), RangeError);
    assert.equal(service.exportProjectJson(), saved); assert.equal(service.canRedo(), true);
  }
});

test('P5e global Melody settings preserve scope ownership, replay, manual notes and exported output', () => {
  const { service, phrase, midi, load } = fixture();
  const id = 'track-melody-1', duplicate = service.duplicateTrack(id);
  const sectionId = service.project().arrangement[0].id;
  service.setSectionTrackPresence(0, id, true);
  service.setSectionTrackPresence(0, duplicate, true);
  service.updatePartSettings({ kind: 'global-role', role: 'melody' }, {
    fillPauses: true, pauseChance: 100,
  });
  service.updatePartSettings({ kind: 'section-track', sectionId, trackId: id }, { speed: 100 });
  const before = service.exportProjectJson(), patches = service.project().arrangement, beforeTracks = service.project().tracks;
  const track = () => service.project().tracks.find(track => track.id === id);
  const global = { patternEffect: 0, targetNoteVariation: 6,
    useDirectionsFromProgression: true, forceFirstOutline: true };
  assert.equal(service.updateMelodyGlobal(global), 'changed');
  assert.deepEqual(service.project().arrangement, patches);
  assert.strictEqual(service.project().tracks, beforeTracks);
  let saved = service.exportProjectJson();
  const notes = phrase.layOutTrackPhrase(service.project(), track());
  const exported = new Midi(midi.generateCompositionMidi(service.project()));
  const melody = exported.tracks.find(item => item.name === track().name);
  assert.equal(melody.notes.length, notes.length);
  melody.notes.forEach((note, index) => {
    assert.equal(note.midi, notes[index].midi);
    assert.ok(Math.abs(note.ticks / exported.header.ppq - notes[index].startBeat) < 0.003);
    assert.ok(Math.abs(note.durationTicks / exported.header.ppq - notes[index].durationBeats) < 0.003);
  });
  service.undo(); assert.equal(service.exportProjectJson(), before);
  assert.equal(service.updateMelodyGlobal({ maxDirectionChanges: 7 }), 'invalid');
  assert.equal(service.canRedo(), true);
  service.redo(); assert.equal(service.exportProjectJson(), saved);
  assert.equal(service.updateMelodyGlobal(global), 'unchanged');
  const { ProjectService } = load('core/project/project.service.ts');
  assert.deepEqual(projectData(new ProjectService().exportProjectJson()), projectData(saved));
  assert.equal(service.importProjectJson(saved), true);
  saved = service.exportProjectJson();
  for (const malformed of [null, [], true, { unknown: true }, { singleNoteExceptions: true }, { singleNoteExceptions: false },
    { fillPausesPerChord: 'true' }, { maxDirectionChanges: 0 }, { maxDirectionChanges: 2.5 }, { maxDirectionChanges: 7 },
    { patternEffect: -1 }, { patternEffect: 3 }, { patternEffect: 1.5 }, { patternEffect: '0' }, { arpySurprises: true }, { arpySurprises: false },
    { targetNoteVariation: 0 }, { targetNoteVariation: 7 }, { targetNoteVariation: 1.5 }, { targetNoteVariation: '3' },
    { targetDirection: 'up' }, { targetDirection: 1 }, { useDirectionsFromProgression: 1 }, { forceFirstOutline: 'true' }]) {
    const project = JSON.parse(saved); project.melodyGlobal = malformed;
    assertRejectedProjectImport(service, JSON.stringify(project));
    assert.equal(service.exportProjectJson(), saved);
  }
  for (const scope of [{ kind: 'global-role', role: 'melody' }, { kind: 'global-track', trackId: id },
    { kind: 'section-role', sectionId, role: 'melody' }, { kind: 'section-track', sectionId, trackId: id }]) {
    for (const patch of [{ singleNoteExceptions: true }, { patternEffect: 1 }, { arpySurprises: true },
      { targetNoteVariation: 2 }, { useDirectionsFromProgression: false }, { forceFirstOutline: false }]) {
      service.updatePartSettings(scope, patch);
      assert.equal(service.exportProjectJson(), saved, 'globals cannot leak into part patches');
    }
  }
  const manual = [{ id: 'manual-global', midi: 72, startBeat: 1, durationBeats: 1, velocity: 95 }];
  service.updateTrackPhrase(id, manual);
  service.updatePartSettings({ kind: 'global-role', role: 'melody' }, { fillPausesPerChord: false });
  service.updateMelodyGlobal({ patternEffect: 1 });
  assert.deepEqual(phrase.phraseForTrack(service.project(), track()), manual);
  service.clearTrackPhrase(id);
  const source = phrase.sectionMelodyContext(service.project(), service.project().arrangement[0]);
  service.updatePartSettings({ kind: 'global-role', role: 'melody' }, { fillPausesPerChord: true });
  assert.notDeepEqual(phrase.sectionMelodyContext(service.project(), service.project().arrangement[0]), source);
});

test('melody scope resolution reaches parsed MIDI, isolates sections and survives history and restore', () => {
  const { service, phrase, midi, load, settings } = fixture();
  const trackId = 'track-melody-1', second = service.duplicateTrack(trackId);
  service.project().arrangement.forEach((_, index) => service.setSectionTrackPresence(index, trackId, true));
  const sectionId = service.project().arrangement[1].id;
  const scope = { kind: 'section-track', sectionId, trackId };
  const track = () => service.project().tracks.find((track) => track.id === trackId);
  const before = phrase.layOutTrackPhrase(service.project(), track());
  const savedBefore = service.exportProjectJson();
  service.updatePartSettings(scope, { speed: 100, chordNoteChoices: [0, 4, 1, 3], melodyPatternOffsets: [1, -1, 2, 1],
    splitChance: 100,
    startNoteChance: 0,
    velocityMin: 100, velocityMax: 100, accents: 0, noteLengthMultiplier: 50, transpose: 12 });
  const after = phrase.layOutTrackPhrase(service.project(), track());
  const start = service.project().arrangement[0].measures * 4, end = start + service.project().arrangement[1].measures * 4;
  const outside = notes => notes.filter(note => note.startBeat < start || note.startBeat >= end);
  assert.deepEqual(outside(after), outside(before));
  assert.notDeepEqual(after, before);
  assert.ok(after.filter(note => note.startBeat >= start && note.startBeat < end).every(note => [100, 90, 80].includes(note.velocity)));
  const section = service.project().arrangement[1];
  assert.equal(settings.resolvePartTrack(service.project().tracks.find(track => track.id === second), section).generatorSettings.speed, undefined);
  const exported = new Midi(midi.generateCompositionMidi(service.project()));
  const notes = exported.tracks.find(track => track.name === 'M1').notes;
  assert.equal(notes.length, after.length);
  notes.forEach((note, index) => {
    assert.equal(note.midi, after[index].midi);
    assert.ok(Math.abs(note.ticks / exported.header.ppq - after[index].startBeat) < 0.003);
    assert.ok(Math.abs(note.durationTicks / exported.header.ppq - after[index].durationBeats) < 0.003);
    assert.ok(Math.abs(note.velocity * 127 - after[index].velocity) < 0.001);
  });
  const saved = service.exportProjectJson();
  service.undo(); assert.equal(service.exportProjectJson(), savedBefore);
  service.redo(); assert.equal(service.exportProjectJson(), saved);
  assert.equal(service.importProjectJson(saved), true);
  const { ProjectService } = load('core/project/project.service.ts');
  const restored = new ProjectService();
  assert.deepEqual(phrase.layOutTrackPhrase(restored.project(), restored.project().tracks.find(track => track.id === trackId)), after);
  const manual = [{ id: 'manual', midi: 72, startBeat: 0, durationBeats: 1, velocity: 90 }];
  service.updateTrackPhrase(trackId, manual);
  service.updatePartSettings(scope, { speed: -100 });
  assert.deepEqual(phrase.phraseForTrack(service.project(), track()), manual);
  service.clearTrackPhrase(trackId);
  assert.notDeepEqual(phrase.phraseForTrack(service.project(), track()), manual);
});

test('melody target/block arrays support mixed groups, snapshots, independent duplication and inherited reset', () => {
  const { service, ui, workspace } = fixture();
  const trackId = 'track-melody-1', second = service.duplicateTrack(trackId);
  const sectionId = service.project().arrangement[1].id;
  const cell = { kind: 'section-role', sectionId, role: 'melody' }, scope = { kind: 'section-track', sectionId, trackId: second };
  service.updatePartSettings({ kind: 'global-track', trackId: second }, { chordNoteChoices: [0, 4] });
  ui.selectCell(sectionId, 'melody');
  assert.equal(workspace.partValues().chordNoteChoices, null);
  service.updatePartSettings(cell, { chordNoteChoices: [1, 3], melodyPatternOffsets: [1, -1, 2], speed: 25 });
  service.updatePartSettings(scope, { speed: 80 });
  const before = service.exportProjectJson();
  assert.equal(service.freezePartSettings(cell), 'changed');
  assert.equal(service.project().arrangement[1].trackPartOverrides[second].speed, 80);
  assert.equal(service.project().arrangement[1].trackPartOverrides[trackId].speed, 25);
  assert.notStrictEqual(service.project().arrangement[1].trackPartOverrides[second].chordNoteChoices,
    service.project().arrangement[1].trackPartOverrides[trackId].chordNoteChoices);
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.updatePartSettings(cell, { chordNoteChoices: [0, 2, 2, 4] });
  assert.ok(service.project().arrangement[1].rolePartOverrides.melody.chordNoteChoices, 'mixed global bases cannot clear the cell');
  service.resetPartSettings(scope, 'speed');
  assert.equal(workspace.partValues().speed, 25);
  const duplicate = service.duplicateTrack(second);
  assert.notStrictEqual(service.project().tracks.find(track => track.id === duplicate).generatorSettings.chordNoteChoices,
    service.project().tracks.find(track => track.id === second).generatorSettings.chordNoteChoices);
});

test('P5e part controls resolve every scope, mixed values, copy/freeze and inherited resets', () => {
  const { service, settings, ui, workspace } = fixture();
  const trackId = 'track-melody-1', second = service.duplicateTrack(trackId);
  const sectionId = service.project().arrangement[1].id, destinationId = service.project().arrangement[2].id;
  const role = { kind: 'global-role', role: 'melody' }, global = { kind: 'global-track', trackId };
  const cell = { kind: 'section-role', sectionId, role: 'melody' }, local = { kind: 'section-track', sectionId, trackId };
  const patch = { splitChance: 70, startNoteChance: 20 };
  const resolve = (id, section = sectionId) => settings.settingsValues(settings.resolvePartTrack(
    service.project().tracks.find(track => track.id === id), service.project().arrangement.find(item => item.id === section)).generatorSettings, 'melody');
  for (const scope of [role, global, cell, local]) {
    const before = service.exportProjectJson();
    service.updatePartSettings(scope, patch);
    for (const [key, value] of Object.entries(patch)) assert.equal(resolve(trackId)[key], value, `${scope.kind} ${key}`);
    const after = service.exportProjectJson();
    service.undo(); assert.equal(service.exportProjectJson(), before);
    service.redo(); assert.equal(service.exportProjectJson(), after);
    service.undo();
  }
  service.updatePartSettings(global, patch);
  ui.selectCell(sectionId, 'melody');
  for (const key of Object.keys(patch)) assert.equal(workspace.partValues()[key], null, `mixed ${key}`);
  service.updatePartSettings(cell, { splitChance: 30 });
  service.updatePartSettings(local, { splitChance: 100 });
  assert.equal(service.freezePartSettings(cell), 'changed');
  assert.equal(resolve(trackId).splitChance, 100); assert.equal(resolve(second).splitChance, 30);
  assert.equal(service.applyPartSettingsToSections(cell, [destinationId], 'effective'), 'changed');
  assert.equal(resolve(trackId, destinationId).splitChance, 100); assert.equal(resolve(second, destinationId).splitChance, 30);
  assert.equal(service.copyPartSettingsToGlobals(cell), 'changed');
  assert.equal(service.project().tracks.find(track => track.id === second).generatorSettings.splitChance, 30);
  service.resetPartSettings(local, 'splitChance');
  assert.equal(resolve(trackId).splitChance, 30);
  assert.equal(service.importProjectJson(service.exportProjectJson()), true);
  for (const role of ['bass', 'chords', 'arpeggio', 'drums']) assert.equal(settings.decodePartPatch(role, patch), undefined);
  const controls = settings.PART_CONTROLS.melody.filter(control => Object.hasOwn(patch, control.key));
  assert.equal(controls.length, 2); assert.ok(controls.every(control => control.advanced));
});

test('melody integer-list editors commit once and reject invalid or stale drafts', () => {
  const { service, settings, load } = fixture();
  const { PartSettingsEditorComponent } = load('features/part-settings/part-settings-editor.component.ts');
  const editor = new PartSettingsEditorComponent();
  const scope = { kind: 'section-track', sectionId: service.project().arrangement[1].id, trackId: 'track-melody-1' };
  editor.role.set('melody'); editor.values.set(settings.settingsValues({ algorithm: 'block' }, 'melody'));
  const targets = settings.PART_CONTROLS.melody.find(control => control.key === 'chordNoteChoices');
  const commits = [];
  editor.settingsChanged.emit = change => { commits.push(change); service.updatePartSettings(scope, { [change.key]: change.value }); };
  const input = { value: '0, 4, -2', setCustomValidity(value) { this.error = value; }, reportValidity() {} };
  const before = service.exportProjectJson();
  editor.startTextEdit();
  assert.equal(service.exportProjectJson(), before);
  editor.commitText(targets, { target: input });
  assert.equal(commits.length, 1);
  assert.deepEqual(service.project().arrangement[1].trackPartOverrides[scope.trackId].chordNoteChoices, [0, 4, -2]);
  service.undo(); assert.equal(service.exportProjectJson(), before);
  input.value = '1,,2'; editor.startTextEdit(); editor.commitText(targets, { target: input });
  assert.ok(input.error); assert.equal(commits.length, 1);
  input.value = '1, 2'; editor.startTextEdit(); editor.values.set({ ...editor.values(), speed: 100 });
  editor.commitText(targets, { target: input });
  assert.equal(commits.length, 1);
  assert.equal(input.value, '0, 2, 2, 4');
  assert.equal(input.error, '');
});

test('melody transpose knobs use discrete 5/7 offsets for keyboard, wheel and drag', () => {
  const { load, settings } = fixture();
  const { CompactKnobComponent } = load('shared/compact-knob.component.ts');
  const knob = new CompactKnobComponent();
  knob.minimum.set(-36); knob.maximum.set(36); knob.value.set(0); knob.label.set('Transpose');
  knob.allowedValues.set(settings.MELODY_TRANSPOSES);
  const commits = []; knob.valueCommit.emit = value => { commits.push(value); knob.value.set(value); };
  const wheel = { deltaY: -1, shiftKey: true, preventDefault() {}, stopPropagation() {} };
  knob.onWheel(wheel); knob.onWheel(wheel);
  assert.deepEqual(commits, [5, 7]);
  knob.commitValue({ target: { value: String(settings.MELODY_TRANSPOSES.indexOf(12)) } });
  assert.equal(knob.value(), 12);
  const element = { focus() {}, setPointerCapture() {} };
  knob.startDrag({ button: 0, pointerId: 11, currentTarget: element, clientY: 100, preventDefault() {}, stopPropagation() {} });
  knob.moveDrag({ pointerId: 11, clientY: 87, shiftKey: false });
  assert.equal(commits.length, 3);
  assert.ok(settings.MELODY_TRANSPOSES.includes(knob.preview()));
  knob.finishDrag({ pointerId: 11 });
  assert.ok(settings.MELODY_TRANSPOSES.includes(knob.value()));
});

test('custom melody durations copy nested lanes, persist every scope, group inheritance and reject malformed imports', () => {
  const { service, load } = fixture();
  const sectionId = service.project().arrangement[0].id, trackId = 'track-melody-1';
  const rows = [[1, -1, 1, -1], ...Array.from({ length: 9 }, () => [])];
  const scope = { kind: 'section-track', sectionId, trackId };
  const patch = { rhythm: 'durations', patternFlip: true, customDurations: rows, customDurationChances: Array(10).fill(50), customDurationRandomWeighting: false };
  service.updatePartSettings(scope, patch);
  rows[0][0] = 2;
  assert.equal(service.project().arrangement[0].trackPartOverrides[trackId].customDurations[0][0], 1);
  const saved = service.exportProjectJson();
  assert.equal(service.importProjectJson(saved), true);
  assert.deepEqual(projectData(service.exportProjectJson()), projectData(saved));
  const keys = load('core/ui/ui-preferences.ts').combinedControlKeys('rhythm', 'melody');
  service.resetPartSettings(scope, keys);
  assert.equal(service.project().arrangement[0].trackPartOverrides[trackId], undefined);
  service.undo();
  assert.deepEqual(projectData(service.exportProjectJson()), projectData(saved));
  const { decodePartPatch, copyPartSettings, partValuesEqual } = load('core/music/part-settings.ts');
  const copy = copyPartSettings(patch);
  assert.ok(partValuesEqual(patch.customDurations, copy.customDurations));
  copy.customDurations[0][0] = 3;
  assert.equal(patch.customDurations[0][0], 2);
  for (const invalid of [[[0], ...rows.slice(1)], [[5], ...rows.slice(1)], [[NaN], ...rows.slice(1)], [[Infinity], ...rows.slice(1)], [[3, 2], ...rows.slice(1)], rows.slice(1)])
    assert.equal(decodePartPatch('melody', { customDurations: invalid }), undefined);
  assert.equal(decodePartPatch('melody', { customDurationChances: [50] }), undefined);
  assert.equal(decodePartPatch('melody', { rhythm: 'custom' }), undefined);
  for (const role of ['melody', 'bass', 'arpeggio']) assert.deepEqual(decodePartPatch(role, { rhythm: 'durations' }), { rhythm: 'durations' });
  assert.equal(decodePartPatch('chords', { customDurations: rows }), undefined);
});

test('duration popup dry runs all lanes against contextual range and harmony without changing project or history', () => {
  const { service, load } = fixture();
  const { validateDurationDraft } = load('core/music/duration-validation.ts');
  const scope = { kind: 'global-track', trackId: 'track-melody-1' };
  const patch = { rhythm: 'durations', customDurations: [[1, -1, 1, -1], ...Array.from({ length: 9 }, () => [])] };
  const before = service.exportProjectJson(), history = service.historyPosition();
  assert.equal(validateDurationDraft(service.project(), scope, patch), null);
  const project = { ...service.project(), melodyGlobal: { contextualMelody: true, rangeMin: 60, rangeMax: 60 } };
  assert.match(validateDurationDraft(project, scope, patch), /No contextual melody/);
  assert.equal(service.exportProjectJson(), before); assert.equal(service.historyPosition(), history);
});

test('group melody duration edits preserve independent pitch settings and section exceptions', () => {
  const { service, load, uiPreferences: preferences } = fixture();
  const first = 'track-melody-1', second = service.duplicateTrack(first);
  const sectionId = service.project().arrangement[0].id;
  service.updatePartSettings({ kind: 'global-track', trackId: second }, { transpose: 12, patternSeed: 123 });
  service.updatePartSettings({ kind: 'section-track', sectionId, trackId: second }, { chordNoteChoices: [4, 0] });
  const patch = { rhythm: 'durations', customDurations: [[1, -1, 1, -1], ...Array.from({ length: 9 }, () => [])] };
  service.updatePartSettings({ kind: 'global-role', role: 'melody' }, patch);
  const tracks = service.project().tracks.filter(track => track.role === 'melody');
  assert.equal(tracks[0].generatorSettings.transpose, undefined);
  assert.equal(tracks[1].generatorSettings.transpose, 12);
  assert.equal(tracks[1].generatorSettings.patternSeed, 123);
  assert.deepEqual(service.project().arrangement[0].trackPartOverrides[second].chordNoteChoices, [4, 0]);
  preferences.beginQuickSettings();
  preferences.moveControl('melody', 'customDurations');
  for (const key of ['rhythm', 'patternFlip', 'customDurations', 'customDurationChances', 'customDurationRandomWeighting']) assert.equal(preferences.quickSettings().melody.includes(key), false);
  preferences.moveControl('melody', 'rhythm');
  for (const key of ['rhythm', 'patternFlip', 'customDurations', 'customDurationChances', 'customDurationRandomWeighting']) assert.equal(preferences.quickSettings().melody.includes(key), true);
});

test('melody duration drawing and validation stay in the draft, save once, and reject stale project state', async () => {
  const scope = { kind: 'global-track', trackId: 'track-melody-1' };
  const { service, load } = fixture({ dialogData: { scope } });
  const { DurationsDialogComponent } = load('features/part-settings/durations-dialog.component.ts');
  const popup = new DurationsDialogComponent();
  let closed = false; popup.dialog.close = () => { closed = true; };
  const before = service.exportProjectJson(), position = service.historyPosition();
  const area = { getBoundingClientRect: () => ({ left: 0, width: 400 }), setPointerCapture() {} };
  const event = x => ({ button: 0, pointerId: 1, clientX: x, currentTarget: area, preventDefault() {} });
  popup.start(event(0), 1); popup.move(event(199));
  assert.deepEqual(popup.rows()[1], [2, -2]);
  popup.cancel(); assert.deepEqual(popup.rows()[1], []);
  popup.flipped.set(true); popup.start(event(0), 1); popup.move(event(199)); popup.finish();
  assert.deepEqual(popup.rows()[1], [-2, -2], 'drawing notes while flipped stores underlying pauses');
  popup.editRow(1, ''); popup.flipped.set(false);
  popup.editRow(0, '-4'); assert.match(popup.error(), /only pauses/); assert.equal(popup.canSave(), false);
  popup.editRow(0, '1, -1, 1, -1');
  popup.validate(); await new Promise(done => setTimeout(done, 5));
  assert.equal(popup.canSave(), true);
  assert.equal(service.exportProjectJson(), before); assert.equal(service.historyPosition(), position);
  popup.save(); assert.equal(closed, true); assert.equal(service.historyPosition(), position + 1);
  const stale = new DurationsDialogComponent();
  stale.editRow(0, '0.5, -0.5, 1, -2'); stale.validate();
  service.updateSettings({ name: 'Changed while editing durations' });
  await new Promise(done => setTimeout(done, 5));
  assert.equal(stale.stale(), true); assert.equal(stale.canSave(), false);
  const saved = service.exportProjectJson(); stale.save(); assert.equal(service.exportProjectJson(), saved);
});

test('cell patches, individual exceptions, zero/false, and resets preserve inheritance', () => {
  const { service, settings } = fixture();
  const id = service.duplicateTrack('track-bass-1');
  const sectionId = service.project().arrangement[1].id;
  const cell = { kind: 'section-role', sectionId, role: 'bass' };
  const part = { kind: 'section-track', sectionId, trackId: id };
  service.updatePartSettings({ kind: 'global-track', trackId: id }, { transpose: 12, octaveInterval: true });
  service.updatePartSettings(cell, { transpose: -12, octaveInterval: false });
  service.updatePartSettings(part, { transpose: 0 });
  const effective = () => settings.resolvePartTrack(service.project().tracks.find((track) => track.id === id), service.project().arrangement[1]).generatorSettings;
  assert.equal(effective().transpose, 0);
  assert.equal(effective().octaveInterval, false);
  service.resetPartSettings(part, 'transpose');
  assert.equal(effective().transpose, -12);
  service.resetPartSettings(cell);
  assert.equal(effective().transpose, 12);
  assert.equal(effective().octaveInterval, true);
  assert.equal(service.project().tracks.find((track) => track.id === id).generatorSettings.transpose, 12);
});

test('a patch affects only its section while MIDI export uses the same resolved notes', () => {
  const { service, phrase, midi } = fixture();
  const trackId = 'track-chords-1';
  service.project().arrangement.forEach((_, index) => service.setSectionTrackPresence(index, trackId, true));
  const before = phrase.layOutTrackPhrase(service.project(), service.project().tracks.find((track) => track.id === trackId));
  const section = service.project().arrangement[1];
  service.updatePartSettings({ kind: 'section-track', sectionId: section.id, trackId },
    { transpose: 12, noteLengthPercent: 50, velocityMin: 110, velocityMax: 110 });
  const after = phrase.layOutTrackPhrase(service.project(), service.project().tracks.find((track) => track.id === trackId));
  const start = service.project().arrangement[0].measures * 4;
  const end = start + section.measures * 4;
  for (let index = 0; index < before.length; index++) {
    if (before[index].startBeat >= start && before[index].startBeat < end) {
      assert.equal(after[index].midi, before[index].midi + 12);
      assert.equal(after[index].durationBeats, before[index].durationBeats / 2);
      assert.equal(after[index].velocity, 110);
    } else assert.deepEqual(after[index], before[index]);
  }
  const exported = new Midi(midi.generateCompositionMidi(service.project()));
  const notes = exported.tracks.find((track) => track.name === 'C1').notes;
  assert.equal(notes.length, after.length);
  for (let index = 0; index < notes.length; index++) {
    assert.equal(notes[index].midi, after[index].midi);
    assert.ok(Math.abs(notes[index].ticks / exported.header.ppq - after[index].startBeat) < 0.003);
    assert.ok(Math.abs(notes[index].durationTicks / exported.header.ppq - after[index].durationBeats) < 0.003);
  }
});

test('mixed values are real aggregates and cell edits preserve track exceptions', () => {
  const { service, ui, workspace } = fixture();
  const id = service.duplicateTrack('track-chords-1');
  service.updatePartSettings({ kind: 'global-track', trackId: id }, { transpose: 12 });
  const sectionId = service.project().arrangement[1].id;
  ui.selectCell(sectionId, 'chords');
  assert.equal(workspace.partValues().transpose, null);
  service.updatePartSettings({ kind: 'section-track', sectionId, trackId: id }, { transpose: 24 });
  workspace.editPartSettings(workspace.partScope(), { key: 'transpose', value: -12 });
  assert.equal(workspace.partValues().transpose, null);
  assert.equal(workspace.trackExceptionCount(), 1);
  ui.selectSectionTrack(sectionId, id);
  assert.equal(workspace.partValues().transpose, 24);
  workspace.resetPartSettings(workspace.partScope());
  assert.equal(workspace.partValues().transpose, -12);
});

test('patches persist through history, JSON, presence rerolls, duplication, and track deletion', () => {
  const { service } = fixture();
  const sectionId = service.project().arrangement[1].id;
  const scope = { kind: 'section-track', sectionId, trackId: 'track-arpeggio-1' };
  service.updatePartSettings(scope, { transpose: 12, rate: 'sixteenth' });
  service.undo();
  assert.equal(service.project().arrangement[1].trackPartOverrides?.[scope.trackId], undefined);
  service.redo();
  const json = service.exportProjectJson();
  assert.equal(service.importProjectJson(json), true);
  assert.deepEqual(service.project().arrangement[1].trackPartOverrides[scope.trackId], { transpose: 12, rate: 'sixteenth' });
  service.randomizeArrangementPresence();
  assert.deepEqual(service.project().arrangement[1].trackPartOverrides[scope.trackId], { transpose: 12, rate: 'sixteenth' });
  const duplicate = service.duplicateTrack(scope.trackId);
  assert.deepEqual(service.project().arrangement[1].trackPartOverrides[duplicate], { transpose: 12, rate: 'sixteenth' });
  service.duplicateSection(1);
  assert.notEqual(service.project().arrangement[1].trackPartOverrides, service.project().arrangement[2].trackPartOverrides);
  service.removeTrack(duplicate);
  assert.equal(service.project().arrangement[1].trackPartOverrides[duplicate], undefined);
  assert.equal(service.importProjectJson(service.exportProjectJson()), true);
});

test('invalid field resets preserve the valid patch and provide feedback', () => {
  const { service, ui, workspace } = fixture();
  const trackId = 'track-chords-1';
  service.updatePartSettings({ kind: 'global-track', trackId }, { velocityMin: 100, velocityMax: 110 });
  const sectionId = service.project().arrangement[1].id;
  const scope = { kind: 'section-track', sectionId, trackId };
  service.updatePartSettings(scope, { velocityMin: 90, velocityMax: 90 });
  ui.selectSectionTrack(sectionId, trackId);
  workspace.resetPartSettings(scope, 'velocityMin');
  assert.equal(workspace.partValues().velocityMin, 90);
  assert.ok(workspace.partEditError());
  workspace.resetPartSettings(scope);
  assert.equal(workspace.partValues().velocityMin, 100);
  assert.equal(workspace.partValues().velocityMax, 110);
  assert.equal(workspace.partEditError(), null);
});

test('secondary track seeds are applied once and layout matches the editor phrase', () => {
  const { service, phrase } = fixture();
  const id = service.duplicateTrack('track-bass-1');
  service.setSectionTrackPresence(0, id, true);
  const track = service.project().tracks.find((candidate) => candidate.id === id);
  const generated = phrase.generateTrackPhrase(service.project(), track).filter((note) => note.startBeat < 8);
  const arranged = phrase.layOutTrackPhrase(service.project(), track).filter((note) => note.startBeat < 8);
  assert.deepEqual(arranged, generated);
});

test('part transpose edits use octave steps at every scope and preserve readable saved values', () => {
  const { service, settings } = fixture();
  const sectionId = service.project().arrangement[1].id;
  for (const role of ['bass', 'chords', 'arpeggio']) {
    assert.equal(settings.PART_CONTROLS[role].find((control) => control.key === 'transpose').step, 12);
    const trackId = `track-${role}-1`;
    const scopes = [{ kind: 'global-role', role }, { kind: 'global-track', trackId },
      { kind: 'section-role', sectionId, role }, { kind: 'section-track', sectionId, trackId }];
    for (const scope of scopes) {
      for (const transpose of [-36, -24, -12, 0, 12, 24, 36]) {
        service.updatePartSettings(scope, { transpose });
        const track = service.project().tracks.find((candidate) => candidate.id === trackId);
        const section = 'sectionId' in scope ? service.project().arrangement[1] : undefined;
        assert.equal(settings.resolvePartTrack(track, section).generatorSettings.transpose, transpose);
      }
      const before = service.exportProjectJson();
      for (const transpose of [-37, -7, -5, 1, 5, 7, 13, 37]) service.updatePartSettings(scope, { transpose });
      assert.equal(service.exportProjectJson(), before);
      if ('sectionId' in scope) service.resetPartSettings(scope);
    }
  }
  const saved = JSON.parse(service.exportProjectJson());
  const trackId = 'track-bass-1';
  saved.tracks.find((track) => track.id === trackId).generatorSettings.transpose = 5;
  saved.arrangement[1].trackPartOverrides = { [trackId]: { transpose: 7 } };
  assert.equal(service.importProjectJson(JSON.stringify(saved)), true, 'old saved values are not discarded');
  assert.equal(service.project().tracks.find((track) => track.id === trackId).generatorSettings.transpose, 5);
  assert.equal(service.project().arrangement[1].trackPartOverrides[trackId].transpose, 7);
  service.updatePartSettings({ kind: 'section-track', sectionId, trackId }, { transpose: 12 });
  assert.equal(service.project().arrangement[1].trackPartOverrides[trackId].transpose, 12);
});

test('freezing mixed cells preserves each effective track, including omitted defaults, with one undo step', () => {
  const { service, settings, phrase } = fixture();
  const first = 'track-bass-1';
  const second = service.duplicateTrack(first);
  const sectionId = service.project().arrangement[1].id;
  const cell = { kind: 'section-role', sectionId, role: 'bass' };
  service.updatePartSettings({ kind: 'global-track', trackId: second }, { transpose: 12, noteVariation: 80 });
  service.updatePartSettings(cell, { octaveInterval: false, velocityMin: 70 });
  service.updatePartSettings({ kind: 'section-track', sectionId, trackId: second }, { transpose: 24 });
  const original = service.exportProjectJson();
  const source = () => service.project().arrangement.find((section) => section.id === sectionId);
  const track = (id) => service.project().tracks.find((track) => track.id === id);
  const effective = (id) => settings.settingsValues(settings.resolvePartTrack(track(id), source()).generatorSettings, 'bass');
  const values = [effective(first), effective(second)];
  const notes = phrase.layOutTrackPhrase(service.project(), track(second));
  assert.equal(service.freezePartSettings(cell), 'changed');
  assert.deepEqual(source().trackPartOverrides[first], values[0]);
  assert.deepEqual(source().trackPartOverrides[second], values[1]);
  assert.notStrictEqual(source().trackPartOverrides[first], source().trackPartOverrides[second]);
  assert.deepEqual(phrase.layOutTrackPhrase(service.project(), track(second)), notes);
  service.undo();
  assert.equal(service.exportProjectJson(), original);
  service.redo();
  assert.equal(service.freezePartSettings(cell), 'unchanged');
  service.updatePartSettings({ kind: 'global-role', role: 'bass' }, { transpose: -12, noteVariation: 0, velocityMin: 75 });
  service.updatePartSettings(cell, { octaveInterval: true });
  assert.deepEqual([effective(first), effective(second)], values, 'snapshots shield every supported field');
  const third = service.addTrack('bass');
  assert.equal(effective(third).transpose, -12);
  assert.equal(effective(third).octaveInterval, true, 'new members keep inheriting the cell');
  const beforeReset = service.exportProjectJson();
  assert.equal(service.resetCellPartSettings(cell), 'changed');
  assert.equal(effective(first).transpose, -12);
  assert.equal(effective(first).velocityMin, 75);
  assert.equal(source().rolePartOverrides.bass, undefined);
  assert.equal(source().trackPartOverrides[first], undefined);
  service.undo();
  assert.equal(service.exportProjectJson(), beforeReset);
});

test('range override copying merges only its layer, preserves exceptions and presence, and is one undo step', () => {
  const { service, settings } = fixture();
  const first = 'track-bass-1';
  const second = service.duplicateTrack(first);
  const [source, destination, another] = service.project().arrangement;
  const cell = { kind: 'section-role', sectionId: source.id, role: 'bass' };
  service.updatePartSettings({ kind: 'global-role', role: 'bass' }, { transpose: 12, octaveInterval: true });
  service.updatePartSettings(cell, { transpose: 0, octaveInterval: false });
  service.updatePartSettings({ ...cell, sectionId: destination.id }, { transpose: 12, noteVariation: 75 });
  service.updatePartSettings({ kind: 'section-track', sectionId: destination.id, trackId: second }, { transpose: 24 });
  const before = service.exportProjectJson();
  const tracks = service.project().tracks;
  const presence = service.project().arrangement.map((section) => section.trackPresence);
  assert.equal(service.applyPartSettingsToSections(cell, [source.id, destination.id, another.id, destination.id], 'overrides'), 'changed');
  const sections = service.project().arrangement;
  assert.deepEqual(sections[1].rolePartOverrides.bass, { transpose: 0, noteVariation: 75, octaveInterval: false });
  assert.equal(settings.resolvePartTrack(tracks.find((track) => track.id === second), sections[1]).generatorSettings.transpose, 24);
  assert.strictEqual(service.project().tracks, tracks);
  assert.deepEqual(sections.map((section) => section.trackPresence), presence);
  assert.notStrictEqual(sections[0].rolePartOverrides.bass, sections[2].rolePartOverrides.bass);
  service.undo();
  assert.equal(service.exportProjectJson(), before);
  assert.equal(service.canRedo(), true);
  assert.equal(service.applyPartSettingsToSections(cell, [source.id], 'overrides'), 'unchanged');
  assert.equal(service.canRedo(), true, 'a no-op leaves redo intact');
  service.redo();
  const saved = service.exportProjectJson();
  assert.equal(service.importProjectJson(saved), true);
  assert.deepEqual(projectData(service.exportProjectJson()), projectData(saved));
  service.updatePartSettings({ ...cell, sectionId: another.id }, { transpose: -12 });
  assert.equal(service.project().arrangement[0].rolePartOverrides.bass.transpose, 0);
});

test('track copying distinguishes explicit overrides from effective snapshots and shares parsed MIDI output', () => {
  const { service, settings, phrase, midi, load } = fixture();
  const trackId = 'track-chords-1';
  const sections = service.project().arrangement;
  const source = { kind: 'section-track', sectionId: sections[0].id, trackId };
  const target = { ...source, sectionId: sections[1].id };
  service.project().arrangement.forEach((_, index) => service.setSectionTrackPresence(index, trackId, true));
  service.updatePartSettings({ kind: 'section-role', sectionId: source.sectionId, role: 'chords' }, { voicing: 'open', transpose: 12 });
  service.updatePartSettings(source, { noteLengthPercent: 50 });
  service.updatePartSettings(target, { transpose: -12, velocityMin: 110, velocityMax: 110 });
  assert.equal(service.applyPartSettingsToSections(source, [target.sectionId], 'overrides'), 'changed');
  assert.deepEqual(service.project().arrangement[1].trackPartOverrides[trackId],
    { transpose: -12, velocityMin: 110, velocityMax: 110, noteLengthPercent: 50 });
  const track = () => service.project().tracks.find((track) => track.id === trackId);
  const sourceValues = settings.settingsValues(settings.resolvePartTrack(track(), service.project().arrangement[0]).generatorSettings, 'chords');
  assert.equal(service.applyPartSettingsToSections(source, [target.sectionId], 'effective'), 'changed');
  assert.deepEqual(service.project().arrangement[1].trackPartOverrides[trackId], sourceValues);
  const notes = phrase.layOutTrackPhrase(service.project(), track());
  const exported = new Midi(midi.generateCompositionMidi(service.project())).tracks.find((track) => track.name === 'C1');
  assert.deepEqual(exported.notes.map((note) => note.midi), notes.map((note) => note.midi));
  const saved = service.exportProjectJson();
  const { ProjectService } = load('core/project/project.service.ts');
  assert.deepEqual(projectData(new ProjectService().exportProjectJson()), projectData(saved), 'snapshots survive session restore');
  service.resetPartSettings(target);
  assert.equal(settings.resolvePartTrack(track(), service.project().arrangement[1]).generatorSettings.transpose, undefined);
});

test('range conflicts and stale targets reject atomically without losing history or manual phrases', () => {
  const { service } = fixture();
  const trackId = 'track-bass-1';
  const sections = service.project().arrangement;
  const cell = { kind: 'section-role', sectionId: sections[0].id, role: 'bass' };
  service.updatePartSettings({ kind: 'global-track', trackId }, { velocityMax: 100 });
  service.updatePartSettings(cell, { velocityMin: 90 });
  service.updatePartSettings({ kind: 'section-track', sectionId: sections[1].id, trackId }, { velocityMax: 80 });
  service.updateTrackPhrase(trackId, [{ id: 'saved', midi: 48, startBeat: 0, durationBeats: 1, velocity: 80 }]);
  const before = service.exportProjectJson();
  const reference = service.project();
  assert.equal(service.applyPartSettingsToSections(cell, [sections[2].id, sections[1].id], 'overrides'), 'invalid');
  assert.strictEqual(service.project(), reference);
  assert.equal(service.applyPartSettingsToSections(cell, [sections[2].id, 'deleted'], 'effective'), 'invalid');
  assert.equal(service.freezePartSettings({ ...cell, sectionId: 'deleted' }), 'invalid');
  assert.equal(service.freezePartSettings({ kind: 'section-track', sectionId: cell.sectionId, trackId: 'deleted' }), 'invalid');
  assert.equal(service.exportProjectJson(), before);
  assert.equal(service.applyPartSettingsToSections(cell, [sections[1].id, sections[2].id], 'effective'), 'changed');
  assert.deepEqual(service.project().tracks.find((track) => track.id === trackId).editedPhrase,
    reference.tracks.find((track) => track.id === trackId).editedPhrase);
  service.undo();
  assert.equal(service.exportProjectJson(), before, 'a rejected action did not add a hidden history entry');
});

test('freezing imported semitone values retains their exact settings; empty and inherited scopes do not fabricate overrides', () => {
  const { service } = fixture();
  const saved = JSON.parse(service.exportProjectJson());
  const trackId = 'track-arpeggio-1';
  saved.tracks.find((track) => track.id === trackId).generatorSettings.transpose = 5;
  assert.equal(service.importProjectJson(JSON.stringify(saved)), true);
  const scope = { kind: 'section-track', sectionId: service.project().arrangement[0].id, trackId };
  assert.equal(service.applyPartSettingsToSections(scope, [service.project().arrangement[1].id], 'overrides'), 'unchanged');
  assert.equal(service.freezePartSettings(scope), 'changed');
  assert.equal(service.project().arrangement[0].trackPartOverrides[trackId].transpose, 5);
  assert.equal(service.importProjectJson(service.exportProjectJson()), true);
  service.removeTrack('track-drums-1');
  const empty = { kind: 'section-role', sectionId: scope.sectionId, role: 'drums' };
  const before = service.exportProjectJson();
  assert.equal(service.freezePartSettings(empty), 'invalid');
  assert.equal(service.resetCellPartSettings(empty), 'unchanged');
  assert.equal(service.exportProjectJson(), before);
});

test('workspace applies only to retained header destinations and reports the shared workflow result', () => {
  const { service, ui, workspace } = fixture();
  const sections = service.project().arrangement;
  ui.selectSection(sections[1].id);
  ui.selectSection(sections[2].id, { shiftKey: true });
  ui.selectCell(sections[0].id, 'chords');
  const selection = [...ui.selectedSectionIds()];
  assert.deepEqual(workspace.partDestinations().map((section) => section.id), selection);
  service.updatePartSettings(workspace.partScope(), { transpose: 12 });
  const before = service.exportProjectJson();
  workspace.runPartWorkflow('overrides');
  assert.match(workspace.partWorkflowMessage(), /2 selected section/);
  assert.deepEqual(ui.selectedSectionIds(), selection);
  assert.deepEqual(ui.settingsTarget(), { kind: 'section-role', sectionId: sections[0].id, role: 'chords' });
  service.undo();
  assert.equal(service.exportProjectJson(), before);
  assert.equal(workspace.partWorkflowMessage(), null, 'undo clears stale success feedback');
  workspace.runPartWorkflow('freeze');
  assert.match(workspace.partWorkflowMessage(), /frozen/);
  assert.equal(workspace.trackExceptionCount(), 1);
  workspace.runPartWorkflow('reset-cell');
  assert.equal(workspace.trackExceptionCount(), 0);
  assert.match(workspace.partWorkflowMessage(), /reset/);
  ui.selectCell(sections[1].id, 'chords');
  assert.equal(workspace.partDestinations().length, 1, 'the source is excluded even when inside the header range');
  assert.equal(workspace.partWorkflowMessage(), null);
});

test('copy previews follow stored patch changes without generation, history or property-order false positives', () => {
  const { service, ui, workspace } = fixture();
  const [source, destination, other] = service.project().arrangement;
  const scope = { kind: 'section-role', sectionId: source.id, role: 'bass' };
  ui.selectSection(destination.id);
  ui.selectCell(source.id, 'bass');
  const before = service.exportProjectJson();
  assert.deepEqual(workspace.partCopyAvailability(), { overrides: false, effective: true },
    'copy all still writes an explicit snapshot even when inherited effective values match');
  assert.equal(service.exportProjectJson(), before);
  assert.equal(service.canUndo(), false, 'preview is read-only');
  service.updatePartSettings(scope, { transpose: 12, octaveInterval: false });
  service.updatePartSettings({ ...scope, sectionId: destination.id }, { octaveInterval: false, transpose: 12 });
  assert.equal(workspace.partCopyAvailability().overrides, false);
  const same = service.project();
  assert.equal(service.applyPartSettingsToSections(scope, [destination.id], 'overrides'), 'unchanged');
  assert.strictEqual(service.project(), same, 'key ordering cannot add a bogus undo step');
  workspace.runPartWorkflow('effective');
  assert.deepEqual(workspace.partCopyAvailability(), { overrides: false, effective: false });
  service.undo();
  assert.equal(workspace.partCopyAvailability().effective, true);
  service.redo();
  assert.equal(workspace.partCopyAvailability().effective, false);
  service.updatePartSettings({ kind: 'section-track', sectionId: destination.id, trackId: 'track-bass-1' }, { transpose: 24 });
  assert.deepEqual(workspace.partCopyAvailability(), { overrides: false, effective: true });
  ui.selectSection(other.id);
  ui.selectCell(source.id, 'bass');
  assert.deepEqual(workspace.partCopyAvailability(), { overrides: true, effective: true });
  ui.clearSectionSelection();
  assert.deepEqual(workspace.partCopyAvailability(), { overrides: false, effective: false });
});

test('copy feedback is dismissible, clears for different destinations, and never displays a no-op checkmark', () => {
  const { service, ui, workspace } = fixture();
  const [source, destination, other] = service.project().arrangement;
  ui.selectSection(destination.id);
  ui.selectSectionTrack(source.id, 'track-chords-1');
  workspace.runPartWorkflow('overrides');
  assert.equal(workspace.partWorkflowMessage(), null);
  workspace.runPartWorkflow('effective');
  assert.match(workspace.partWorkflowMessage(), /applied/);
  workspace.dismissPartWorkflowFeedback();
  assert.equal(workspace.partWorkflowMessage(), null);
  service.undo();
  workspace.runPartWorkflow('effective');
  assert.match(workspace.partWorkflowMessage(), /applied/);
  ui.selectSection(other.id);
  ui.selectSectionTrack(source.id, 'track-chords-1');
  assert.equal(workspace.partWorkflowMessage(), null);
  workspace.runPartWorkflow('effective');
  assert.match(workspace.partWorkflowMessage(), /applied/);
  workspace.runPartWorkflow('effective');
  assert.equal(workspace.partWorkflowMessage(), null, 'a stale click or programmatic no-op clears feedback');
});

test('fill controls respect mixed scopes, track exceptions, flips and per-field inheritance', () => {
  const { service, ui, workspace, settings } = fixture();
  const first = 'track-arpeggio-1';
  const second = service.duplicateTrack(first);
  const sectionId = service.project().arrangement[1].id;
  service.updatePartSettings({ kind: 'global-track', trackId: second }, { chordSpanFill: 'EVEN' });
  ui.selectCell(sectionId, 'arpeggio');
  assert.equal(workspace.partValues().chordSpanFill, null);
  assert.equal(workspace.partValues().fillFlip, false);
  const cell = workspace.partScope();
  service.updatePartSettings(cell, { chordSpanFill: 'HALF1', fillFlip: true });
  service.updatePartSettings({ kind: 'section-track', sectionId, trackId: second }, { chordSpanFill: 'F23', fillFlip: false });
  assert.equal(workspace.partValues().chordSpanFill, null);
  assert.equal(workspace.partValues().fillFlip, null);
  service.resetPartSettings({ kind: 'section-track', sectionId, trackId: second }, 'fillFlip');
  assert.equal(workspace.partValues().fillFlip, true);
  const effective = (id) => settings.resolvePartTrack(service.project().tracks.find((track) => track.id === id),
    service.project().arrangement[1]).generatorSettings;
  assert.equal(effective(first).chordSpanFill, 'HALF1');
  assert.equal(effective(second).chordSpanFill, 'F23');
  service.resetPartSettings(cell);
  assert.equal(effective(first).chordSpanFill, undefined);
  assert.equal(effective(second).chordSpanFill, 'F23');
});

test('section fills repeat over the effective progression and reach score/preview/editor and MIDI without changing presence', () => {
  const { service, ui, workspace, phrase, midi } = fixture();
  const trackId = 'track-chords-1';
  const sectionId = service.project().arrangement[1].id;
  service.project().arrangement.forEach((_, index) => service.setSectionTrackPresence(index, trackId, true));
  service.updateSection(1, { measures: 8 });
  service.setSectionChordDegree(1, 0, 1);
  // The section override covers all eight measures; use an odd global progression separately below.
  const before = service.project();
  const track = () => service.project().tracks.find((track) => track.id === trackId);
  const base = phrase.layOutTrackPhrase(before, track());
  const source = { kind: 'section-track', sectionId, trackId };
  service.updatePartSettings(source, { chordSpanFill: 'ODD' });
  const start = before.arrangement[0].measures * 4;
  const end = start + before.arrangement[1].measures * 4;
  const notes = phrase.layOutTrackPhrase(service.project(), track());
  const expected = base.filter((note) => note.startBeat < start || note.startBeat >= end
    || Math.floor((note.startBeat - start) / 4) % 2 === 1);
  const withoutId = (notes) => notes.map(({ id, ...note }) => note);
  assert.deepEqual(withoutId(notes), withoutId(expected));
  assert.deepEqual(service.project().arrangement.map((section) => section.trackPresence), before.arrangement.map((section) => section.trackPresence));
  const exported = new Midi(midi.generateCompositionMidi(service.project()));
  const midiNotes = exported.tracks.find((track) => track.name === 'C1').notes;
  assert.equal(midiNotes.length, notes.length);
  midiNotes.forEach((note, index) => {
    assert.equal(note.midi, notes[index].midi);
    assert.ok(Math.abs(note.ticks / exported.header.ppq - notes[index].startBeat) < 0.003);
  });
  assert.equal(workspace.scoreNotes().filter((note) => note.part === trackId).length, notes.length);
  ui.selectSectionTrack(sectionId, trackId);
  assert.equal(workspace.partValues().chordSpanFill, 'ODD');
  service.clearSectionChordOverrides(1);
  service.setProgression([1, 5, 6]);
  service.updatePartSettings(source, { chordSpanFill: 'HALF2' });
  const local = phrase.layOutTrackPhrase(service.project(), track()).filter((note) => note.startBeat >= start && note.startBeat < end);
  assert.deepEqual([...new Set(local.map((note) => Math.floor((note.startBeat - start) / 4)))], [1, 2, 4, 5, 7]);
});

test('fill values survive copy/freeze, one-entry history, duplication and session/JSON; manual notes keep masking them', () => {
  const { service, phrase, load } = fixture();
  const trackId = 'track-drums-1';
  const [first, second] = service.project().arrangement;
  const source = { kind: 'section-track', sectionId: first.id, trackId };
  const before = service.exportProjectJson();
  service.updatePartSettings(source, { chordSpanFill: 'F34', fillFlip: true });
  service.undo();
  assert.equal(service.exportProjectJson(), before);
  service.redo();
  assert.equal(service.applyPartSettingsToSections(source, [second.id], 'overrides'), 'changed');
  assert.equal(service.project().arrangement[1].trackPartOverrides[trackId].fillFlip, true);
  assert.equal(service.freezePartSettings(source), 'changed');
  const copy = service.duplicateTrack(trackId);
  assert.equal(service.project().arrangement[0].trackPartOverrides[copy].chordSpanFill, 'F34');
  service.duplicateSection(0);
  assert.equal(service.project().arrangement[1].trackPartOverrides[trackId].chordSpanFill, 'F34');
  const saved = service.exportProjectJson();
  const { ProjectService } = load('core/project/project.service.ts');
  assert.deepEqual(projectData(new ProjectService().exportProjectJson()), projectData(saved));
  assert.equal(service.importProjectJson(saved), true);
  const manual = [{ id: 'manual', midi: 36, startBeat: 0, durationBeats: 0.5, velocity: 90 }];
  service.updateTrackPhrase(trackId, manual);
  service.updatePartSettings({ kind: 'global-track', trackId }, { chordSpanFill: 'ALL', fillFlip: true });
  assert.deepEqual(phrase.phraseForTrack(service.project(), service.project().tracks.find((track) => track.id === trackId)), manual);
});

test('reset emphasis follows effective differences after global copy, freeze and history', () => {
  const { service, ui, workspace } = fixture();
  const sectionId = service.project().arrangement[0].id;
  const trackId = 'track-bass-1';
  const second = service.duplicateTrack(trackId);
  const cell = { kind: 'section-role', sectionId, role: 'bass' };
  const part = { kind: 'section-track', sectionId, trackId: second };
  ui.selectCell(sectionId, 'bass');
  assert.equal(workspace.partResetChangesValues(), false);
  service.updatePartSettings(cell, { noteVariation: 35, customPattern: Array(32).fill(0) });
  service.updatePartSettings(part, { noteVariation: 45 });
  assert.equal(workspace.partResetChangesValues(), true);
  service.copyPartSettingsToGlobals(cell);
  assert.equal(workspace.partResetChangesValues(), false, 'track exceptions mask cell differences after copy');
  assert.ok(workspace.overriddenKeys().length, 'copy retains explicit inheritance boundaries');
  service.undo();
  assert.equal(workspace.partResetChangesValues(), true);
  service.redo();
  assert.equal(workspace.partResetChangesValues(), false);
  ui.selectSectionTrack(sectionId, second);
  assert.equal(workspace.partResetChangesValues(), true, 'track reset still inherits its cell, not globals');
  ui.selectSectionTrack(sectionId, trackId);
  service.freezePartSettings(workspace.partScope());
  assert.ok(workspace.overriddenKeys().length);
  assert.equal(workspace.partResetChangesValues(), false, 'equal frozen arrays do not emphasize reset');
  workspace.resetPartSettings(workspace.partScope());
  assert.deepEqual(workspace.overriddenKeys(), [], 'matching snapshots can still be cleared');
  ui.selectCell(sectionId, 'bass');
  workspace.resetPartSettings(cell);
  assert.equal(workspace.partResetChangesValues(), false);
  ui.selectSectionTrack(sectionId, second);
  assert.equal(workspace.partResetChangesValues(), false, 'track patch now matches its inherited global base');
});

test('manually restoring numeric, choice and checkbox defaults removes local overrides and customized UI', () => {
  const { service, ui, workspace, phrase } = fixture();
  const sectionId = service.project().arrangement[1].id;
  const trackId = 'track-chords-1';
  ui.selectSectionTrack(sectionId, trackId);
  const scope = workspace.partScope();
  const track = () => service.project().tracks.find((track) => track.id === trackId);
  const originalNotes = phrase.layOutTrackPhrase(service.project(), track());
  for (const [key, custom, inherited] of [['transpose', 12, 0], ['chordSpanFill', 'ODD', 'ALL'],
    ['fillFlip', true, false], ['velocityMin', 80, 69]]) {
    workspace.editPartSettings(scope, { key, value: custom });
    assert.deepEqual(workspace.overriddenKeys(), [key]);
    assert.equal(workspace.inheritanceLabel(), 'Customized track part');
    workspace.editPartSettings(scope, { key, value: inherited });
    assert.deepEqual(workspace.overriddenKeys(), []);
    assert.equal(workspace.inheritanceLabel(), 'Inherited from global tracks');
    assert.equal(workspace.partEditError(), null);
    assert.equal(service.project().arrangement[1].trackPartOverrides[trackId], undefined);
    service.undo();
    assert.equal(workspace.partValues()[key], custom);
    service.redo();
    assert.equal(workspace.partValues()[key], inherited);
  }
  assert.deepEqual(phrase.layOutTrackPhrase(service.project(), track()), originalNotes);
  const before = service.project();
  workspace.editPartSettings(scope, { key: 'transpose', value: 0 });
  assert.strictEqual(service.project(), before, 'choosing an already inherited value does not add history');
});

test('cell cleanup compares all bases while track cleanup inherits its cell, preserving unrelated exceptions', () => {
  const { service, ui, workspace } = fixture();
  const first = 'track-bass-1';
  const second = service.duplicateTrack(first);
  const sectionId = service.project().arrangement[1].id;
  const cell = { kind: 'section-role', sectionId, role: 'bass' };
  const part = { kind: 'section-track', sectionId, trackId: second };
  service.updatePartSettings({ kind: 'global-track', trackId: second }, { transpose: 12 });
  service.updatePartSettings(cell, { transpose: -12, chordSpanFill: 'ODD' });
  service.updatePartSettings(cell, { transpose: 0 });
  ui.selectCell(sectionId, 'bass');
  assert.ok(workspace.overriddenKeys().includes('transpose'), 'one matching base is insufficient in a mixed group');
  service.updatePartSettings(part, { transpose: 24, chordSpanFill: 'EVEN' });
  service.updatePartSettings(part, { transpose: 0 });
  ui.selectSectionTrack(sectionId, second);
  assert.deepEqual(workspace.overriddenKeys(), ['chordSpanFill'], 'track inherits cell transpose 0, rather than global 12');
  assert.equal(workspace.partValues().transpose, 0);
  service.updatePartSettings(part, { chordSpanFill: 'ODD' });
  assert.deepEqual(workspace.overriddenKeys(), []);
  assert.equal(workspace.inheritanceLabel(), 'Inherited from cell settings');
  service.updatePartSettings({ kind: 'global-track', trackId: second }, { transpose: 0 });
  service.updatePartSettings(cell, { transpose: 0 });
  ui.selectCell(sectionId, 'bass');
  assert.deepEqual(workspace.overriddenKeys(), ['chordSpanFill']);
  service.updatePartSettings(part, { transpose: 24 });
  service.updatePartSettings(cell, { chordSpanFill: 'ALL' });
  assert.deepEqual(workspace.overriddenKeys(), []);
  assert.equal(workspace.trackExceptionCount(), 1, 'clearing the role layer preserves individual track exceptions');
  const saved = service.exportProjectJson();
  assert.equal(service.importProjectJson(saved), true);
  assert.equal(workspace.trackExceptionCount(), 1);
});

test('manual cleanup does not normalize untouched frozen or copied snapshot fields', () => {
  const { service, settings } = fixture();
  const trackId = 'track-arpeggio-1';
  const [first, second] = service.project().arrangement;
  const scope = { kind: 'section-track', sectionId: first.id, trackId };
  service.freezePartSettings(scope);
  const snapshot = { ...service.project().arrangement[0].trackPartOverrides[trackId] };
  service.updatePartSettings(scope, { transpose: 12 });
  service.updatePartSettings(scope, { transpose: 0 });
  const restored = service.project().arrangement[0].trackPartOverrides[trackId];
  const { transpose, ...untouched } = snapshot;
  assert.deepEqual(restored, untouched, 'only the manually restored field resumes inheritance');
  assert.equal(service.applyPartSettingsToSections(scope, [second.id], 'effective'), 'changed');
  assert.deepEqual(service.project().arrangement[1].trackPartOverrides[trackId], snapshot);
  service.updatePartSettings({ kind: 'global-track', trackId }, { transpose: -12, hitsPerPattern: 16 });
  const track = service.project().tracks.find((track) => track.id === trackId);
  const local = settings.resolvePartTrack(track, service.project().arrangement[0]).generatorSettings;
  assert.equal(local.transpose, -12, 'manually restored transpose now follows global changes');
  assert.equal(local.hitsPerPattern, 8, 'untouched frozen fields remain explicit');
  assert.equal(settings.resolvePartTrack(track, service.project().arrangement[1]).generatorSettings.transpose, 0);
});

test('chord rhythm controls resolve mixed groups and scoped exceptions, including manual return to inherited defaults', () => {
  const { service, ui, workspace } = fixture();
  const first = 'track-chords-1';
  const second = service.duplicateTrack(first);
  const sectionId = service.project().arrangement[1].id;
  service.updatePartSettings({ kind: 'global-track', trackId: second }, { hitsPerPattern: 16 });
  ui.selectCell(sectionId, 'chords');
  assert.equal(workspace.partValues().hitsPerPattern, null);
  service.updatePartSettings(workspace.partScope(), { hitsPerPattern: 3, patternShift: 1 });
  service.updatePartSettings({ kind: 'section-track', sectionId, trackId: second }, { hitsPerPattern: 5, patternFlip: true });
  assert.equal(workspace.partValues().hitsPerPattern, null);
  assert.equal(workspace.trackExceptionCount(), 1);
  ui.selectSectionTrack(sectionId, second);
  assert.equal(workspace.partValues().hitsPerPattern, 5);
  service.updatePartSettings(workspace.partScope(), { hitsPerPattern: 3, patternFlip: false });
  assert.deepEqual(workspace.overriddenKeys(), []);
  assert.equal(workspace.partValues().hitsPerPattern, 3);
  service.resetPartSettings({ kind: 'section-role', sectionId, role: 'chords' });
  assert.equal(workspace.partValues().hitsPerPattern, 16);
  assert.equal(workspace.partValues().patternShift, 0);
  ui.selectSectionTrack(sectionId, first);
  assert.equal(workspace.partValues().hitsPerPattern, 8);
});

test('chord rhythm controls share resolved score/MIDI notes and survive history, copy/freeze and session reload', () => {
  const { service, ui, workspace, phrase, midi, load } = fixture();
  const trackId = 'track-chords-1';
  const sectionId = service.project().arrangement[1].id;
  service.project().arrangement.forEach((_, index) => service.setSectionTrackPresence(index, trackId, true));
  const track = () => service.project().tracks.find((track) => track.id === trackId);
  const original = phrase.layOutTrackPhrase(service.project(), track());
  const scope = { kind: 'section-track', sectionId, trackId };
  const before = service.exportProjectJson();
  service.updatePartSettings(scope, { rhythm: 'one-six', hitsPerPattern: 5, patternShift: 3, patternFlip: true });
  service.undo();
  assert.equal(service.exportProjectJson(), before);
  service.redo();
  const notes = phrase.layOutTrackPhrase(service.project(), track());
  const start = service.project().arrangement[0].measures * 4;
  const end = start + service.project().arrangement[1].measures * 4;
  assert.deepEqual(notes.filter((note) => note.startBeat < start || note.startBeat >= end),
    original.filter((note) => note.startBeat < start || note.startBeat >= end));
  assert.notDeepEqual(notes.filter((note) => note.startBeat >= start && note.startBeat < end),
    original.filter((note) => note.startBeat >= start && note.startBeat < end));
  assert.equal(workspace.scoreNotes().filter((note) => note.part === trackId).length, notes.length);
  const exported = new Midi(midi.generateCompositionMidi(service.project()));
  const midiNotes = exported.tracks.find((track) => track.name === 'C1').notes;
  assert.equal(midiNotes.length, notes.length);
  midiNotes.forEach((note, index) => {
    assert.ok(Math.abs(note.ticks / exported.header.ppq - notes[index].startBeat) < 0.003);
    assert.ok(Math.abs(note.durationTicks / exported.header.ppq - notes[index].durationBeats) < 0.003);
  });
  assert.equal(service.freezePartSettings(scope), 'changed');
  const other = service.project().arrangement[2].id;
  assert.equal(service.applyPartSettingsToSections(scope, [other], 'effective'), 'changed');
  assert.equal(service.project().arrangement[2].trackPartOverrides[trackId].hitsPerPattern, 5);
  const copy = service.duplicateTrack(trackId);
  assert.equal(service.project().arrangement[1].trackPartOverrides[copy].patternShift, 3);
  const saved = service.exportProjectJson();
  assert.equal(service.importProjectJson(saved), true);
  const { ProjectService } = load('core/project/project.service.ts');
  assert.deepEqual(projectData(new ProjectService().exportProjectJson()), projectData(saved));
  ui.selectSectionTrack(sectionId, trackId);
  assert.equal(workspace.partValues().patternFlip, true);
  const manual = [{ id: 'manual', midi: 72, startBeat: 0, durationBeats: 1, velocity: 90 }];
  service.updateTrackPhrase(trackId, manual);
  assert.deepEqual(phrase.phraseForTrack(service.project(), track()), manual);
});

test('Euclidean pulse edits retain mixed values and requested counts across scope inheritance and hit reductions', () => {
  const { service, ui, workspace } = fixture();
  const first = 'track-chords-1';
  const second = service.duplicateTrack(first);
  service.updatePartSettings({ kind: 'global-role', role: 'chords' }, { rhythm: 'euclid' });
  service.updatePartSettings({ kind: 'global-track', trackId: second }, { euclideanPulses: 6 });
  const sectionId = service.project().arrangement[1].id;
  const cell = { kind: 'section-role', sectionId, role: 'chords' };
  ui.selectCell(sectionId, 'chords');
  assert.equal(workspace.partValues().euclideanPulses, null);
  service.updatePartSettings(cell, { euclideanPulses: 5 });
  assert.equal(workspace.partValues().euclideanPulses, 5);
  const part = { kind: 'section-track', sectionId, trackId: first };
  service.updatePartSettings(part, { euclideanPulses: 7, hitsPerPattern: 3 });
  ui.selectSectionTrack(sectionId, first);
  assert.equal(workspace.partValues().euclideanPulses, 7, 'requested Pulses is retained even when Hits is smaller');
  service.updatePartSettings(part, { euclideanPulses: 5, hitsPerPattern: 8 });
  assert.deepEqual(workspace.overriddenKeys(), [], 'returning to inherited pulse count removes its override');
  service.resetPartSettings(cell);
  assert.equal(workspace.partValues().euclideanPulses, 4);
  ui.selectSectionTrack(sectionId, second);
  assert.equal(workspace.partValues().euclideanPulses, 6);
});

test('quick access and inspector partition musical controls across roles and rhythm modes', () => {
  const { service, load, settings } = fixture();
  const { PartSettingsEditorComponent } = load('features/part-settings/part-settings-editor.component.ts');
  const { partControlGroup } = load('features/part-settings/part-control-groups.ts');
  const quick = new PartSettingsEditorComponent(), inspector = new PartSettingsEditorComponent();
  quick.advanced.set(false);
  inspector.inspectorOnly.set(true);
  for (const role of ['melody', 'bass', 'chords', 'arpeggio', 'drums']) {
    for (const rhythm of ['full', 'euclid', 'custom', 'melody', 'bass1']) {
      const values = { ...settings.settingsValues(service.project().tracks.find(track => track.role === role).generatorSettings, role), rhythm,
        useCustomVelocities: true };
      const complete = new PartSettingsEditorComponent();
      for (const editor of [quick, inspector, complete]) {
        editor.role.set(role);
        editor.values.set(values);
      }
      const quickKeys = quick.controls().map(control => control.key);
      const inspectorKeys = inspector.controls().map(control => control.key);
      assert.equal(quickKeys.some(key => inspectorKeys.includes(key)), false);
      assert.deepEqual([...quickKeys, ...inspectorKeys].sort(), complete.controls().map(control => control.key).sort());
      for (const key of ['noteLengthMultiplier', 'noteLengthPercent', 'exceptionChance', 'offset']) {
        const inQuick = (role === 'bass' && key === 'noteLengthMultiplier')
          || (role === 'chords' && key === 'noteLengthPercent');
        assert.equal(quickKeys.includes(key), inQuick, `${role} ${key} quick placement`);
        if (settings.PART_CONTROLS[role].some(control => control.key === key)) {
          assert.equal(inspectorKeys.includes(key), !inQuick, `${role} ${key} inspector placement`);
        }
      }
      assert.equal(quick.controlGroups()[0].id, 'rhythm');
      assert.equal(quick.seedControl(), undefined);
      assert.equal(inspector.seedControl().key, 'patternSeed');
      assert.equal(inspector.controlGroups().some(group => group.controls.some(control => control.key === 'patternSeed')), false);
    }
  }
  for (const key of ['noteLengthMultiplier', 'noteLengthPercent', 'exceptionChance', 'swingPercent', 'offset', 'feedbackCount', 'feedbackDuration']) {
    assert.equal(partControlGroup(key), 'timing');
  }
  assert.equal(partControlGroup('hitsPerPattern'), 'rhythm');
});

test('the chord rhythm preview shares generation masks and shows mixed values only for active rhythm inputs', () => {
  const { service, load, settings } = fixture();
  const { PartSettingsEditorComponent } = load('features/part-settings/part-settings-editor.component.ts');
  const { chordRhythmMask } = load('core/music/rhythm-pattern.ts');
  const editor = new PartSettingsEditorComponent();
  editor.role.set('chords');
  const values = settings.settingsValues(service.project().tracks.find(track => track.role === 'chords').generatorSettings, 'chords');
  editor.values.set(values);
  assert.deepEqual(editor.rhythmPreview(), chordRhythmMask(service.project().tracks.find(track => track.role === 'chords').generatorSettings));
  assert.equal(editor.controls().some((control) => control.key === 'euclideanPulses'), false);
  editor.values.set({ ...values, rhythm: 'euclid', hitsPerPattern: 5, euclideanPulses: 2, patternShift: 1, patternFlip: true });
  assert.equal(editor.controls().some((control) => control.key === 'euclideanPulses'), true);
  assert.deepEqual(editor.rhythmPreview(), chordRhythmMask(editor.values()));
  editor.values.set({ ...editor.values(), euclideanPulses: null });
  assert.equal(editor.rhythmPreview(), null);
  assert.match(editor.rhythmPreviewLabel(), /Mixed/);
  editor.values.set({ ...editor.values(), rhythm: 'half' });
  assert.notEqual(editor.rhythmPreview(), null, 'mixed unused Pulses does not hide a static rhythm preview');
  editor.values.set({ ...editor.values(), rhythm: null });
  assert.equal(editor.rhythmPreview(), null);
  editor.role.set('bass');
  editor.values.set({ ...editor.values(), rhythm: 'bass1' });
  assert.equal(editor.rhythmPreview(), undefined);
});

test('Euclidean settings survive local generation, parsed MIDI, snapshots, duplication, undo and session restore', () => {
  const { service, phrase, midi, settings, load } = fixture();
  const trackId = 'track-chords-1';
  service.project().arrangement.forEach((_, index) => service.setSectionTrackPresence(index, trackId, true));
  const sectionId = service.project().arrangement[1].id;
  const scope = { kind: 'section-track', sectionId, trackId };
  const track = () => service.project().tracks.find((track) => track.id === trackId);
  const original = phrase.layOutTrackPhrase(service.project(), track());
  const before = service.exportProjectJson();
  service.updatePartSettings(scope, { rhythm: 'euclid', hitsPerPattern: 5, euclideanPulses: 2, patternShift: 1 });
  const after = phrase.layOutTrackPhrase(service.project(), track());
  const start = service.project().arrangement[0].measures * 4;
  const end = start + service.project().arrangement[1].measures * 4;
  const outside = (notes) => notes.filter((note) => note.startBeat < start || note.startBeat >= end);
  assert.deepEqual(outside(after), outside(original));
  assert.notDeepEqual(after, original);
  const exported = new Midi(midi.generateCompositionMidi(service.project()));
  const midiNotes = exported.tracks.find((track) => track.name === 'C1').notes;
  assert.equal(midiNotes.length, after.length);
  midiNotes.forEach((note, index) => {
    assert.ok(Math.abs(note.ticks / exported.header.ppq - after[index].startBeat) < 0.003);
    assert.ok(Math.abs(note.durationTicks / exported.header.ppq - after[index].durationBeats) < 0.003);
    assert.equal(note.midi, after[index].midi);
  });
  service.undo();
  assert.equal(service.exportProjectJson(), before);
  service.redo();
  assert.deepEqual(phrase.layOutTrackPhrase(service.project(), track()), after);
  assert.equal(service.freezePartSettings(scope), 'changed');
  assert.equal(service.freezePartSettings(scope), 'unchanged');
  const destination = service.project().arrangement[2].id;
  assert.equal(service.applyPartSettingsToSections(scope, [destination], 'effective'), 'changed');
  assert.equal(service.partSettingsCopyWouldChange(scope, [destination], 'effective'), false);
  assert.equal(service.project().arrangement[2].trackPartOverrides[trackId].euclideanPulses, 2);
  const copy = service.duplicateTrack(trackId);
  assert.equal(service.project().arrangement[1].trackPartOverrides[copy].euclideanPulses, 2);
  const saved = service.exportProjectJson();
  assert.equal(service.importProjectJson(saved), true);
  const { ProjectService } = load('core/project/project.service.ts');
  assert.deepEqual(projectData(new ProjectService().exportProjectJson()), projectData(saved));
  const importedTrack = service.project().tracks.find((track) => track.id === trackId);
  assert.equal(settings.resolvePartTrack(importedTrack, service.project().arrangement[1]).generatorSettings.euclideanPulses, 2);
  const manual = [{ id: 'manual', midi: 72, startBeat: 0, durationBeats: 1, velocity: 90 }];
  service.updateTrackPhrase(trackId, manual);
  assert.deepEqual(phrase.phraseForTrack(service.project(), track()), manual);
});

test('velocity enable and arrays preserve mixed scopes, inherited resets, snapshots and hidden subdivisions', () => {
  const { service, ui, workspace, settings } = fixture();
  const trackId = 'track-chords-1';
  const second = service.duplicateTrack(trackId);
  const sectionId = service.project().arrangement[1].id;
  const cell = { kind: 'section-role', sectionId, role: 'chords' };
  const part = { kind: 'section-track', sectionId, trackId: second };
  const grid = Array.from({ length: 32 }, (_, index) => 30 + index);
  service.updatePartSettings({ kind: 'global-role', role: 'chords' }, { useCustomVelocities: true, customVelocities: grid });
  grid[0] = 0;
  assert.equal(service.project().tracks.find(track => track.role === 'chords').generatorSettings.customVelocities[0], 30);
  const override = Array(32).fill(120);
  service.updatePartSettings(part, { customVelocities: override, useCustomVelocities: false });
  ui.selectCell(sectionId, 'chords');
  assert.equal(workspace.partValues().customVelocities, null);
  assert.equal(workspace.partValues().useCustomVelocities, null);
  service.updatePartSettings(cell, { customVelocities: Array(32).fill(60) });
  let effective = settings.resolvePartTrack(service.project().tracks.find(track => track.id === second), service.project().arrangement[1]).generatorSettings;
  assert.deepEqual(effective.customVelocities, override);
  assert.equal(effective.useCustomVelocities, false);
  service.updatePartSettings(part, { customVelocities: Array(32).fill(60), useCustomVelocities: true });
  assert.equal(service.project().arrangement[1].trackPartOverrides[second], undefined, 'same-content arrays and booleans restore inheritance');
  const source = { kind: 'section-track', sectionId, trackId };
  assert.equal(service.freezePartSettings(source), 'changed');
  assert.equal(service.freezePartSettings(source), 'unchanged');
  const destination = service.project().arrangement[2].id;
  assert.equal(service.applyPartSettingsToSections(source, [destination], 'effective'), 'changed');
  assert.equal(service.partSettingsCopyWouldChange(source, [destination], 'effective'), false);
  service.updatePartSettings(source, { hitsPerPattern: 3 });
  assert.equal(service.project().arrangement[1].trackPartOverrides[trackId].customVelocities.length, 32);
  service.updatePartSettings(source, { useCustomVelocities: false });
  assert.equal(service.project().arrangement[1].trackPartOverrides[trackId].customVelocities[31], 60);
  service.undo();
  assert.equal(service.project().arrangement[1].trackPartOverrides[trackId].useCustomVelocities, true);
  service.duplicateSection(1);
  const copy = service.duplicateTrack(trackId);
  assert.deepEqual(service.project().arrangement[1].trackPartOverrides[copy].customVelocities, Array(32).fill(60));
});

test('custom velocities survive section MIDI, history, session and JSON while manual notes keep masking them', () => {
  const { service, phrase, midi, load } = fixture();
  const trackId = 'track-chords-1';
  service.project().arrangement.forEach((_, index) => service.setSectionTrackPresence(index, trackId, true));
  const scope = { kind: 'section-track', sectionId: service.project().arrangement[1].id, trackId };
  const track = () => service.project().tracks.find(track => track.id === trackId);
  const original = phrase.layOutTrackPhrase(service.project(), track());
  const before = service.exportProjectJson();
  const grid = Array.from({ length: 32 }, (_, index) => index === 0 ? 0 : index % 2 === 0 ? 127 : 20);
  service.updatePartSettings(scope, { useCustomVelocities: true, customVelocities: grid });
  const after = phrase.layOutTrackPhrase(service.project(), track());
  const start = service.project().arrangement[0].measures * 4;
  const end = start + service.project().arrangement[1].measures * 4;
  const outside = notes => notes.filter(note => note.startBeat < start || note.startBeat >= end);
  assert.deepEqual(outside(after), outside(original));
  assert.notDeepEqual(after, original);
  const exported = new Midi(midi.generateCompositionMidi(service.project()));
  const notes = exported.tracks.find(track => track.name === 'C1').notes;
  assert.equal(notes.length, after.length);
  notes.forEach((note, index) => {
    assert.equal(Math.round(note.velocity * 127), after[index].velocity);
    assert.ok(Math.abs(note.ticks / exported.header.ppq - after[index].startBeat) < .003);
  });
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.redo(); assert.deepEqual(phrase.layOutTrackPhrase(service.project(), track()), after);
  const saved = service.exportProjectJson();
  assert.equal(service.importProjectJson(saved), true);
  const { ProjectService } = load('core/project/project.service.ts');
  assert.deepEqual(projectData(new ProjectService().exportProjectJson()), projectData(saved));
  const manual = [{ id: 'manual', midi: 72, startBeat: 0, durationBeats: 1, velocity: 90 }];
  service.updateTrackPhrase(trackId, manual);
  service.updatePartSettings(scope, { customVelocities: Array(32).fill(0) });
  assert.deepEqual(phrase.phraseForTrack(service.project(), track()), manual);
});

test('custom grid arrays resolve by content, replace whole values, and restore inheritance without losing exceptions', () => {
  const { service, ui, workspace, settings } = fixture();
  const trackId = 'track-chords-1';
  const second = service.duplicateTrack(trackId);
  const sectionId = service.project().arrangement[1].id;
  const cell = { kind: 'section-role', sectionId, role: 'chords' };
  const part = { kind: 'section-track', sectionId, trackId: second };
  const base = Array.from({ length: 32 }, (_, index) => +(index % 3 === 0));
  service.updatePartSettings({ kind: 'global-role', role: 'chords' }, { rhythm: 'custom', customPattern: base });
  base[0] = 0;
  assert.equal(service.project().tracks.find(track => track.role === 'chords').generatorSettings.customPattern[0], 1, 'caller array is copied');
  service.updatePartSettings({ kind: 'global-track', trackId: second }, { customPattern: [...service.project().tracks.find(track => track.role === 'chords').generatorSettings.customPattern] });
  ui.selectCell(sectionId, 'chords');
  assert.ok(Array.isArray(workspace.partValues().customPattern), 'separate equal arrays are not Mixed');
  const exception = Array(32).fill(0); exception[31] = 1;
  service.updatePartSettings(part, { customPattern: exception });
  assert.equal(workspace.partValues().customPattern, null);
  const common = Array(32).fill(1); common[4] = 0;
  service.updatePartSettings(cell, { customPattern: common });
  const effective = () => settings.resolvePartTrack(service.project().tracks.find((track) => track.id === second), service.project().arrangement[1]).generatorSettings;
  assert.deepEqual(effective().customPattern, exception, 'track array replaces role array in its entirety');
  service.updatePartSettings(part, { customPattern: [...common] });
  assert.equal(service.project().arrangement[1].trackPartOverrides[second], undefined);
  assert.deepEqual(effective().customPattern, common);
  service.updatePartSettings(cell, { customPattern: [...service.project().tracks.find(track => track.role === 'chords').generatorSettings.customPattern] });
  assert.equal(service.project().arrangement[1].rolePartOverrides.chords, undefined);
  assert.equal(service.freezePartSettings(part), 'changed');
  assert.equal(service.freezePartSettings(part), 'unchanged');
  const destination = service.project().arrangement[2].id;
  assert.equal(service.applyPartSettingsToSections(part, [destination], 'effective'), 'changed');
  assert.equal(service.partSettingsCopyWouldChange(part, [destination], 'effective'), false);
  const saved = service.exportProjectJson();
  const duplicate = service.duplicateTrack(second);
  service.updatePartSettings({ kind: 'section-track', sectionId, trackId: duplicate }, { customPattern: exception });
  assert.notDeepEqual(service.project().arrangement[1].trackPartOverrides[duplicate].customPattern,
    service.project().arrangement[1].trackPartOverrides[second].customPattern);
  service.undo(); service.undo();
  assert.equal(service.exportProjectJson(), saved);
});

test('custom chord grids retain seeded dynamics, hidden cells, section isolation and parsed MIDI across reload', () => {
  const { service, phrase, midi, load } = fixture();
  const { generateChordPart } = load('core/music/chord-generator.ts');
  const trackId = 'track-chords-1';
  service.project().arrangement.forEach((_, index) => service.setSectionTrackPresence(index, trackId, true));
  const track = () => service.project().tracks.find((track) => track.id === trackId);
  const base = track().generatorSettings;
  const grid = Array.from({ length: 32 }, (_, index) => +(index % 3 === 0));
  const full = generateChordPart(42n, 'C', 'major', [1], base);
  const sparse = generateChordPart(42n, 'C', 'major', [1], { ...base, rhythm: 'custom', customPattern: grid });
  assert.deepEqual(sparse, full.filter((_, index) => grid[index] > 0), 'rests still consume velocity draws');
  const original = phrase.layOutTrackPhrase(service.project(), track());
  const sectionId = service.project().arrangement[1].id;
  const scope = { kind: 'section-track', sectionId, trackId };
  service.updatePartSettings(scope, { rhythm: 'custom', customPattern: grid, hitsPerPattern: 5, patternShift: 1, patternFlip: true });
  const after = phrase.layOutTrackPhrase(service.project(), track());
  const start = service.project().arrangement[0].measures * 4;
  const end = start + service.project().arrangement[1].measures * 4;
  const outside = (notes) => notes.filter((note) => note.startBeat < start || note.startBeat >= end);
  assert.deepEqual(outside(after), outside(original));
  assert.notDeepEqual(after, original);
  const exported = new Midi(midi.generateCompositionMidi(service.project()));
  const notes = exported.tracks.find((track) => track.name === 'C1').notes;
  assert.equal(notes.length, after.length);
  notes.forEach((note, index) => {
    assert.equal(note.midi, after[index].midi);
    assert.ok(Math.abs(note.ticks / exported.header.ppq - after[index].startBeat) < 0.003);
    assert.ok(Math.abs(note.durationTicks / exported.header.ppq - after[index].durationBeats) < 0.003);
  });
  service.updatePartSettings(scope, { hitsPerPattern: 2 });
  assert.deepEqual(service.project().arrangement[1].trackPartOverrides[trackId].customPattern, grid);
  service.undo();
  assert.deepEqual(phrase.layOutTrackPhrase(service.project(), track()), after);
  const saved = service.exportProjectJson();
  assert.equal(service.importProjectJson(saved), true);
  const { ProjectService } = load('core/project/project.service.ts');
  const restored = new ProjectService();
  assert.deepEqual(projectData(restored.exportProjectJson()), projectData(saved));
  const manual = [{ id: 'manual', midi: 72, startBeat: 0, durationBeats: 1, velocity: 90 }];
  service.updateTrackPhrase(trackId, manual);
  service.updatePartSettings(scope, { customPattern: Array(32).fill(0) });
  assert.deepEqual(phrase.phraseForTrack(service.project(), track()), manual);
});

test('drum pattern popup resolves every row in the selected section and edits only its track layer', () => {
  const { service, load } = fixture();
  service.setRoleTrackCount('drums', 3);
  const tracks = service.project().tracks.filter(track => track.role === 'drums');
  const sectionId = service.project().arrangement[1].id;
  service.updatePartSettings({ kind: 'section-role', sectionId, role: 'drums' }, { hitsPerPattern: 12 });
  const before = service.project().tracks;
  const { PatternSequencerComponent } = load('features/part-settings/pattern-sequencer.component.ts');
  const popup = new PatternSequencerComponent();
  popup.data.role = 'drums';
  popup.data.scope = { kind: 'section-track', sectionId, trackId: tracks[0].id };
  assert.equal(popup.rows().length, 3);
  assert.ok(popup.rows().every(row => row.hits === 12));
  popup.patch(tracks[1].id, { customVelocities: Array(32).fill(40), useCustomVelocities: true });
  assert.deepEqual(service.project().tracks, before);
  const section = service.project().arrangement[1];
  assert.equal(section.trackPartOverrides[tracks[0].id], undefined);
  assert.equal(section.trackPartOverrides[tracks[2].id], undefined);
  assert.deepEqual(section.trackPartOverrides[tracks[1].id].customVelocities, Array(32).fill(40));
  popup.inherit(tracks[1].id, 'customVelocities');
  assert.equal(service.project().arrangement[1].trackPartOverrides[tracks[1].id].customVelocities, undefined);
});

test('drum sequencer resolves percussion names and multiplies only its selected scope', () => {
  const { service, load } = fixture();
  const first = 'track-drums-1', second = service.duplicateTrack(first);
  const sectionId = service.project().arrangement[0].id;
  const { PatternSequencerComponent } = load('features/part-settings/pattern-sequencer.component.ts');
  const { drumInstrumentLabel } = load('core/music/drum-instruments.ts');
  const popup = new PatternSequencerComponent();
  popup.data.role = 'drums';
  popup.data.scope = { kind: 'section-track', sectionId, trackId: first };
  for (const row of popup.rows()) {
    const track = service.project().tracks.find(track => track.id === row.id);
    assert.equal(row.instrument, drumInstrumentLabel(track.generatorSettings.pitch, 'general-midi'));
  }
  const before = service.project().tracks;
  const position = service.historyPosition();
  assert.equal(popup.canMultiplyHits(), true);
  popup.multiplyHits('2');
  assert.equal(popup.rows().find(row => row.id === first).hits, 8);
  assert.equal(popup.rows().find(row => row.id === second).hits, 4);
  assert.deepEqual(service.project().tracks, before, 'section action preserves global drums');
  assert.equal(service.historyPosition(), position + 1, 'one undo entry');
  service.undo();
  assert.equal(popup.rows().find(row => row.id === first).hits, 4);
  popup.patch(first, { rhythm: 'melody' });
  assert.equal(popup.canMultiplyHits(), false);
});

for (const role of ['bass', 'arpeggio']) {
  test(role + ' durations preserve signed timing, lanes, scoped inheritance, undo and JSON restoration', () => {
    const { service, load, phrase } = fixture();
    const track = service.project().tracks.find(track => track.role === role);
    const scope = { kind: 'global-track', trackId: track.id };
    const rows = [[0.5, -0.5, 1, -2], [2, -1, 1], ...Array.from({length: 8}, () => [])];
    const patch = { rhythm: 'durations', customDurations: rows, customDurationRandomWeighting: false,
      patternFlip: false, chordSpanFill: 'ALL', fillFlip: false, noteLengthMultiplier: 100, ...(role === 'arpeggio' ? { swingPercent: 50 } : {}) };
    const { validateDurationDraft } = load('core/music/duration-validation.ts');
    assert.equal(validateDurationDraft(service.project(), scope, patch), null);
    const position = service.historyPosition();
    service.updatePartSettings(scope, patch);
    assert.equal(service.historyPosition(), position + 1);
    const current = () => service.project().tracks.find(candidate => candidate.id === track.id);
    const notes = () => phrase.generateTrackPhrase(service.project(), current());
    const timing = notes().filter(note => note.startBeat < 8).map(note => [note.startBeat, note.durationBeats]);
    assert.deepEqual(timing, [[0, 0.5], [1, 1], [4, 2], [7, 1]]);
    assert.deepEqual(notes(), notes(), 'seeded output is repeatable');
    service.undo(); assert.notEqual(current().generatorSettings.rhythm, 'durations');
    service.redo(); assert.deepEqual(notes().filter(note => note.startBeat < 8).map(note => [note.startBeat, note.durationBeats]), timing);
    const sectionId = service.project().arrangement[0].id;
    const local = { kind: 'section-role', sectionId, role };
    assert.equal(validateDurationDraft(service.project(), local, { customDurations: [[-4], ...Array.from({length: 9}, () => [])] }).includes('only pauses'), true);
    service.updatePartSettings(local, { customDurations: [[1, -3], ...Array.from({length: 9}, () => [])] });
    assert.deepEqual(current().generatorSettings.customDurations, rows, 'local edits preserve base');
    const saved = service.exportProjectJson();
    assert.equal(service.importProjectJson(saved), true);
    assert.equal(current().generatorSettings.rhythm, 'durations');
    assert.deepEqual(current().generatorSettings.customDurations, rows);
  });
}

test('bass and arp duration selection honors weights and flip while ignoring stored grid shaping', () => {
  const { load } = fixture();
  const { generateBassline } = load('core/music/bass-generator.ts');
  const { generateArpeggio } = load('core/music/arpeggio-generator.ts');
  const common = { rhythm: 'durations', customDurations: [[1, -1, 1, -1], [0.5, -0.5, 1, -2], ...Array.from({length:8},()=>[])],
    customDurationRandomWeighting: true, customDurationChances: [0, 100, ...Array(8).fill(0)],
    patternFlip: true, chordSpan: 4, patternShift: 3, customPattern: Array(32).fill(0),
    useCustomVelocities: true, customVelocities: Array(32).fill(0), velocityMin: 80, velocityMax: 80 };
  const bass = { ...common, noteVariation: 0, octaveInterval: false, patternJoinMode: 'JOIN' };
  const arp = { ...common, patternRepeat: 4, pattern: 'up', rate: 'eighth', octaves: 1, swingPercent: 50, pauseChance: 100, exceptionChance: 100 };
  for (const generate of [()=>generateBassline(42n,'C','major',[1,4], 'durations',0,bass),
    ()=>generateArpeggio(42n,'C','major',[1,4],arp)]) {
    const notes=generate();
    assert.deepEqual(notes.map(note=>[note.startBeat,note.durationBeats]),[[0.5,0.5],[2,2],[4.5,0.5],[6,2]]);
    assert.ok(notes.every(note=>note.velocity===80));
  }
});
