const assert = require('node:assert/strict');
const test = require('node:test');
const { Midi } = require('@tonejs/midi');
const { fixture, projectData } = require('../support/project-fixture.cjs');
const { assertRejectedProjectImport } = require('../support/project-import-assertions.cjs');

test('global generated notes mark differing section harmony without claiming section track settings', () => {
  const { service, phrase } = fixture();
  const project = service.project(), track = project.tracks.find(track => track.role === 'bass');
  const section = { ...project.arrangement[0], measures: 1, chordDegrees: [2], customDurations: [2], trackPresence: { [track.id]: true } };
  assert.equal(phrase.trackPhraseSource(track, section, project), 'global-section-harmony');
  assert.equal(phrase.trackPhraseSource(track, { ...section, trackPartOverrides: { [track.id]: { transpose: 12 } } }, project), 'section-generated');
  assert.equal(phrase.trackPhraseSource({ ...track, editedPhrase: [] }, section, project), 'global-edited');
  assert.equal(phrase.trackPhraseSource(track, { ...section, trackPhrases: { [track.id]: [] } }, project), 'section-edited');
  const notes = phrase.layOutTrackPhrase({ ...project, arrangement: [section] }, track, true);
  assert.ok(notes.length > 0); assert.ok(notes.every(note => note.phraseSource === 'global-section-harmony'));
});

test('score phrase sources follow local edits, forced generation and inherited global edits without changing layout output', () => {
  const { service, phrase } = fixture();
  const project = service.project(), track = project.tracks.find(track => track.role === 'bass');
  const note = { id: 'source-note', midi: 48, startBeat: 0, durationBeats: 1, velocity: 90 };
  const edited = { ...track, editedPhrase: [note] };
  const section = { ...project.arrangement[0], measures: 1, trackPresence: { [track.id]: true } };
  assert.equal(phrase.trackPhraseSource(track, section), 'global-generated');
  assert.equal(phrase.trackPhraseSource(edited, section), 'global-edited');
  assert.equal(phrase.trackPhraseSource(edited, { ...section, generatedTrackIds: [track.id] }), 'global-generated');
  assert.equal(phrase.trackPhraseSource(track, { ...section, trackPartOverrides: { [track.id]: { transpose: 12 } } }), 'section-generated');
  assert.equal(phrase.trackPhraseSource(track, { ...section, rolePartOverrides: { bass: { transpose: 12 } } }), 'section-generated');
  assert.equal(phrase.trackPhraseSource(edited, { ...section, generatedTrackIds: [track.id],
    trackPartOverrides: { [track.id]: { transpose: 12 } } }), 'section-generated');
  for (const context of [
    { context: { key: 'D', swingPercent: 60 } }, { chordDegrees: [1, 4] },
    { customChords: [null] }, { customDurations: [2] }, { useMelodyHarmony: true }, { swapMelody: true },
    { trackPartOverrides: { [track.id]: {} }, rolePartOverrides: { bass: {} } },
    { trackPartOverrides: { 'another-track': { transpose: 12 } }, rolePartOverrides: { melody: { transpose: 12 } } },
  ]) {
    assert.equal(phrase.trackPhraseSource(track, { ...section, ...context }), 'global-generated', JSON.stringify(context));
    assert.equal(phrase.trackPhraseSource(edited, { ...section, ...context }), 'global-edited', JSON.stringify(context));
  }
  assert.equal(phrase.trackPhraseSource(edited, { ...section, trackPhrases: { [track.id]: [note] }, generatedTrackIds: [track.id] }), 'section-edited');
  const p = { ...project, arrangement: [section, { ...section, id: 'local', trackPhrases: { [track.id]: [note] } }] };
  const labeled = phrase.layOutTrackPhrase(p, edited, true);
  assert.deepEqual(labeled.map(note => note.phraseSource), ['global-edited', 'section-edited']);
  assert.deepEqual(labeled.map(({ phraseSource, ...note }) => note), phrase.layOutTrackPhrase(p, edited));
});

test('score combines drums on a shared pitch axis while preserving note ownership and individual visibility', () => {
  const { service, workspace, ui, load } = fixture();
  const kick = 'track-drums-1', snare = service.addTrack('drums'), otherKick = service.addTrack('drums');
  service.updateTrackGeneratorSettings(snare, { pitch: 38 });
  for (const id of [kick, snare, otherKick]) service.setSectionTrackPresence(1, id, true);
  const rows = workspace.scoreTracks(), drumRows = rows.filter(row => row.color === 'drums');
  const drumRow = { id: 'role:drums', name: 'Drums', color: 'drums', trackIds: [kick, snare, otherKick] };
  assert.equal(rows.length, 7);
  assert.deepEqual(drumRows.map(row => row.id), [kick, snare, otherKick]);
  assert.equal(workspace.arrangedTracks().length, 7, 'arrangement and independent tracks stay intact');
  const notes = workspace.scoreNotes();
  const kickNote = notes.find(note => note.part === kick), snareNote = notes.find(note => note.part === snare);
  assert.ok(kickNote.topPercent > snareNote.topPercent);
  assert.equal(notes.find(note => note.part === otherKick).topPercent, kickNote.topPercent);
  ui.selectTrack(snare); assert.equal(workspace.isScoreTrackSelected(drumRow), true);
  workspace.toggleVisibility(kick); assert.equal(workspace.isScoreTrackVisible(drumRow), true);
  workspace.toggleVisibility(snare); workspace.toggleVisibility(otherKick);
  assert.equal(workspace.isScoreTrackVisible(drumRow), false);
  workspace.toggleVisibility(snare);
  const { ScoreCanvasComponent } = load('shared/score-canvas.component.ts');
  const score = new ScoreCanvasComponent();
  score.model = { tracks: rows, notes, sections: [], totalBeats: workspace.totalBeats(),
    selectedTrackId: snare, hiddenTrackIds: workspace.hiddenTracks() };
  score.width = 800; score.height = 400;
  score.app = { render() {}, canvas: { style: {}, setAttribute() {}, getBoundingClientRect: () => ({ left: 0, top: 0, width: 800, height: 400 }) } };
  score.staticGraphics = { clear() {}, rect() { return this; }, fill() { return this; },
    moveTo() { return this; }, lineTo() { return this; }, stroke() { return this; }, roundRect() { return this; } };
  score.colorClass = class { alpha = 1; toNumber() { return 0; } };
  const savedStyle = global.getComputedStyle, savedDocument = global.document;
  global.getComputedStyle = () => ({ getPropertyValue: () => '#000000' });
  global.document = { documentElement: {} };
  try {
    score.drawStatic();
    const drumHits = score.hitAreas.filter(area => area.note.color === 'drums');
    assert.ok(drumHits.length > 0);
    assert.ok(drumHits.every(area => area.trackIndex === 1 && area.note.part === snare));
    const area = drumHits[0];
    let selected;
    score.noteSelected.emit = note => { selected = note; workspace.openNoteEditor(note); };
    score.onCanvasClick({ clientX: area.x + 1, clientY: area.y + area.height / 2 });
    assert.equal(selected.part, snare);
    assert.equal(workspace.selectedTrackId(), snare);
    assert.equal(workspace.editingNoteId(), selected.id);
    score.model.hiddenTrackIds = new Set(); score.drawStatic();
    assert.ok(score.hitAreas.filter(area => area.note.color === 'drums').every(area => area.trackIndex === 1));
  } finally { global.getComputedStyle = savedStyle; global.document = savedDocument; }
  for (const id of [kick, snare, otherKick]) service.removeTrack(id);
  assert.equal(workspace.scoreTracks().some(row => row.color === 'drums'), false);
});

test('drum identities are independent, named, fixed to channel 10 and preserved by history and manual editing', () => {
  const { service, phrase, workspace, ui, load, settings } = fixture();
  const first = 'track-drums-1', track = id => service.project().tracks.find(track => track.id === id);
  assert.equal(track(first).generatorSettings.pitch, 36);
  assert.ok(phrase.phraseForTrack(service.project(), track(first)).every(note => note.midi === 36));
  const before = service.exportProjectJson();
  service.updateTrackGeneratorSettings(first, { pitch: 38 });
  assert.equal(workspace.instrumentName(track(first)), 'Snare · 38');
  assert.ok(phrase.phraseForTrack(service.project(), track(first)).every(note => note.midi === 38));
  const after = service.exportProjectJson();
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.redo(); assert.equal(service.exportProjectJson(), after);
  const manual = [{ id: 'manual', midi: 39, startBeat: 0, durationBeats: 0.2, velocity: 95 }];
  service.updateTrackPhrase(first, manual);
  service.updateTrackGeneratorSettings(first, { pitch: 42 });
  assert.deepEqual(phrase.phraseForTrack(service.project(), track(first)), manual);
  const added = service.addTrack('drums');
  assert.equal(track(added).generatorSettings.pitch, 36);
  assert.equal(track(added).editedPhrase, undefined);
  const duplicate = service.duplicateTrack(first);
  assert.equal(track(duplicate).generatorSettings.pitch, 42);
  assert.notStrictEqual(track(duplicate).editedPhrase, track(first).editedPhrase);
  assert.ok(service.project().tracks.filter(track => track.role === 'drums').every(track => track.midiChannel === 10));
  ui.selectTrack(first);
  assert.equal(workspace.hasChannelCollision(), false);
  const { EditWorkspaceComponent } = load('features/edit/edit-workspace.component.ts');
  const editor = new EditWorkspaceComponent(); editor.ngOnInit();
  assert.deepEqual(editor.drumVoices(), [{ midi: 36, label: 'Kick · 36' }, { midi: 39, label: 'Clap · 39' }, { midi: 42, label: 'Closed hi-hat · 42' }]);
  const saved = service.exportProjectJson();
  service.updateTrack(first, { midiChannel: 1 }); service.updateTrack(first, { mix: { program: 5 } });
  service.updateRoleGeneratorSettings('drums', { pitch: 38 });
  assert.equal(service.exportProjectJson(), saved);
  assert.equal(settings.PART_CONTROLS.drums.some(control => ['pitch', 'groove', 'transpose', 'accents'].includes(control.key)), false);
  assert.equal(service.importProjectJson(saved), true);
  const { ProjectService } = load('core/project/project.service.ts');
  assert.deepEqual(projectData(new ProjectService().exportProjectJson()), projectData(saved));
});

test('restoring generated melody in the note editor is a draft until Apply and is one undo action', () => {
  const { service, workspace, phrase, load } = fixture();
  const trackId = 'track-melody-1';
  const manual = [{ id: 'manual', midi: 72, startBeat: 0, durationBeats: 1, velocity: 90 }];
  service.updateTrackPhrase(trackId, manual);
  service.updatePartSettings({ kind: 'global-track', trackId }, { speed: 100 });
  workspace.openTrackEditor(trackId);
  const { EditWorkspaceComponent } = load('features/edit/edit-workspace.component.ts');
  const editor = new EditWorkspaceComponent(); editor.ngOnInit();
  const before = service.exportProjectJson();
  editor.restoreGenerated();
  assert.notDeepEqual(editor.draftNotes(), manual);
  assert.equal(service.exportProjectJson(), before);
  editor.applyChanges();
  const track = service.project().tracks.find(track => track.id === trackId);
  assert.equal(track.editedPhrase, undefined);
  assert.deepEqual(phrase.phraseForTrack(service.project(), track), editor.draftNotes().map(({ trackId, ...note }) => note));
  service.undo(); assert.equal(service.exportProjectJson(), before);
});

test('manual note edits remain explicit replacements and are not discarded by generator patches', () => {
  const { service, phrase } = fixture();
  const id = 'track-bass-1';
  const manual = [{ id: 'manual', midi: 40, startBeat: 0, durationBeats: 1, velocity: 91 }];
  service.updateTrackPhrase(id, manual);
  service.setSectionTrackPresence(1, id, true);
  const track = () => service.project().tracks.find((candidate) => candidate.id === id);
  const before = phrase.layOutTrackPhrase(service.project(), track());
  service.updatePartSettings({ kind: 'section-track', sectionId: service.project().arrangement[1].id, trackId: id },
    { transpose: 24, octaveInterval: true, noteLengthMultiplier: 50 });
  assert.deepEqual(phrase.layOutTrackPhrase(service.project(), track()), before);
  assert.deepEqual(track().editedPhrase, manual);
});

test('manual phrases follow global Mode by degree only when the entire phrase matches, with atomic undo', () => {
  const { service, uiPreferences } = fixture();
  const bass = 'track-bass-1', chords = 'track-chords-1', drums = 'track-drums-1';
  const notes = pitches => pitches.map((midi, index) => ({ id: 'mode-' + index, midi, startBeat: index, durationBeats: .5, velocity: 90 + index }));
  service.updateTrackPhrase(bass, notes([60,64,67,71]));
  service.updateTrackPhrase(chords, notes([60,61,64]));
  service.updateTrackPhrase(drums, notes([60,64]));
  service.updateSettings({ transposeSemitones: 7 });
  const before = service.exportProjectJson();
  assert.equal(uiPreferences.manualPhrasesFollowMode(), true);
  service.updateSettings({ scale: 'natural-minor' });
  const track = id => service.project().tracks.find(track => track.id === id);
  assert.deepEqual(track(bass).editedPhrase.map(note => note.midi), [60,63,67,70]);
  assert.deepEqual(track(chords).editedPhrase, notes([60,61,64]), 'one chromatic note preserves the whole phrase');
  assert.deepEqual(track(drums).editedPhrase, notes([60,64]), 'drums remain unchanged');
  assert.deepEqual(track(bass).editedPhrase.map(({ midi, ...note }) => note), notes([60,64,67,71]).map(({ midi, ...note }) => note));
  assert.deepEqual(service.project().editedPhrases.bass, track(bass).editedPhrase, 'primary role cache follows its track');
  const after = service.exportProjectJson();
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.redo(); assert.equal(service.exportProjectJson(), after);
  uiPreferences.toggleManualPhrasesFollowMode();
  service.updateSettings({ scale: 'major' });
  assert.deepEqual(track(bass).editedPhrase.map(note => note.midi), [60,63,67,70], 'disabled setting preserves manual pitches');
});

test('manual section phrases inherit Mode while explicit section modes and chromatic phrases retain ownership', () => {
  const { service } = fixture();
  const notes = (midi, id = 'section-mode') => [{ id, midi, startBeat: 0, durationBeats: 1, velocity: 90 }];
  const [a,b,c] = service.project().arrangement;
  service.updateSectionTrackPhrase(a.id, 'track-bass-1', notes(64));
  service.updateSectionTrackPhrase(a.id, 'track-chords-1', notes(61));
  service.updateSectionTrackPhrase(a.id, 'track-drums-1', notes(64));
  service.updateSectionContext(b.id, { scale: 'major' });
  service.updateSectionTrackPhrase(b.id, 'track-bass-1', notes(64));
  service.updateSectionContext(c.id, { key: 'D' });
  service.updateSectionTrackPhrase(c.id, 'track-bass-1', notes(66));
  service.updateSettings({ scale: 'natural-minor' });
  const [first, fixed, differentKey] = service.project().arrangement;
  assert.equal(first.trackPhrases['track-bass-1'][0].midi, 63);
  assert.equal(first.trackPhrases['track-chords-1'][0].midi, 61);
  assert.equal(first.trackPhrases['track-drums-1'][0].midi, 64);
  assert.equal(fixed.trackPhrases['track-bass-1'][0].midi, 64);
  assert.equal(differentKey.trackPhrases['track-bass-1'][0].midi, 65);
});

test('free phrase transpose accepts semitones, ignores scale snapping, records once and resets on reopening', () => {
  const { service, ui, load } = fixture();
  const id = 'track-bass-1';
  service.updateTrackPhrase(id, [{ id: 'free', midi: 48, startBeat: 0, durationBeats: 1, velocity: 90 }]);
  service.updateSettings({ transposeSemitones: 7 });
  ui.selectedTrackId.set(id);
  const Editor = load('features/edit/edit-workspace.component.ts').EditWorkspaceComponent;
  const editor = new Editor(); editor.ngOnInit();
  assert.equal(editor.freeTranspose(), 0);
  assert.equal(editor.notes()[0].midi, 55);
  editor.transposePhrase(5);
  assert.equal(editor.draftNotes()[0].midi, 53);
  assert.equal(editor.notes()[0].midi, 60);
  editor.undo();
  assert.equal(editor.freeTranspose(), 0);
  assert.equal(editor.draftNotes()[0].midi, 48);
  assert.equal(editor.canUndo(), false);
  editor.redo();
  assert.equal(editor.freeTranspose(), 5);
  editor.transposePhrase(6);
  assert.equal(editor.draftNotes()[0].midi, 54, 'each edit applies only the knob delta');
  editor.transposePhrase(-36);
  assert.equal(editor.draftNotes()[0].midi, 12);
  editor.transposePhrase(36);
  assert.equal(editor.draftNotes()[0].midi, 84);
  editor.applyChanges();
  const reopened = new Editor(); reopened.ngOnInit();
  assert.equal(reopened.freeTranspose(), 0);
  assert.equal(reopened.draftNotes()[0].midi, 84);
});

test('manual melody notes work through the editor, scoped presence, history, persistence, and parsed MIDI', () => {
  const { service, ui, workspace, phrase, midi, stored, load } = fixture();
  const id = 'track-melody-1';
  service.updateSettings({ seed: '9223372036854775807' });
  service.project().arrangement.forEach((_, index) => service.setSectionTrackPresence(index, id, index === 0));
  workspace.openTrackEditor(id);
  assert.equal(workspace.editing(), true);
  assert.equal(ui.selectedTrackId(), id);
  const { EditWorkspaceComponent } = load('features/edit/edit-workspace.component.ts');
  const editor = new EditWorkspaceComponent();
  editor.ngOnInit();
  assert.equal(editor.generationAvailable(), true);
  assert.equal(editor.tracks()[0].role, 'melody');
  const before = service.exportProjectJson();
  editor.addNoteAt({ midi: 60, startBeat: 15.5 });
  assert.ok(editor.noteCount() > 0);
  assert.equal(service.exportProjectJson(), before, 'draft edits do not write project history');
  const draft = editor.notes().map(({ trackId, ...note }) => note);
  editor.applyChanges();
  workspace.closeEditor();
  const track = () => service.project().tracks.find((candidate) => candidate.id === id);
  assert.deepEqual(track().editedPhrase, draft);
  const laidOut = phrase.layOutTrackPhrase(service.project(), track());
  assert.ok(laidOut.length > 0);
  const exported = new Midi(midi.generateCompositionMidi(service.project()));
  assert.equal(exported.tracks[0].name, 'M1');
  assert.equal(exported.tracks[0].channel, 3);
  assert.equal(exported.tracks[0].instrument.number, 73);
  assert.equal(exported.tracks[0].notes[0].midi, draft[0].midi);
  assert.equal(exported.tracks[0].notes.length, laidOut.length);
  service.undo();
  assert.equal(service.exportProjectJson(), before, 'Apply writes exactly one history entry');
  service.redo();
  assert.deepEqual(track().editedPhrase, draft);
  const saved = service.exportProjectJson();
  assert.equal(service.importProjectJson(saved), true);
  const { ProjectService } = load('core/project/project.service.ts');
  const restored = new ProjectService();
  assert.deepEqual(restored.project(), service.project());
  assert.equal(restored.project().seed, '9223372036854775807');
  assert.ok([...stored.values()].some((value) => value.includes('track-melody-1')));
});

test('melody duplication keeps independent notes/mix and removing its final role member is undoable', () => {
  const { service, workspace, ui } = fixture();
  const id = 'track-melody-1';
  const notes = [{ id: 'melody-note', midi: 72, startBeat: 1, durationBeats: 0.5, velocity: 90 }];
  service.updateTrackPhrase(id, notes);
  service.updateTrack(id, { mix: { program: 40, panPercent: -20 } });
  const copy = service.duplicateTrack(id);
  const duplicate = service.project().tracks.find((track) => track.id === copy);
  assert.deepEqual(duplicate.editedPhrase, notes);
  assert.equal(duplicate.mix.program, 40);
  assert.notEqual(duplicate.midiChannel, 10);
  assert.notEqual(duplicate.midiChannel, 4);
  service.updateTrack(copy, { mix: { panPercent: 50 } });
  assert.equal(service.project().tracks.find((track) => track.id === id).mix.panPercent, -20);
  ui.selectTrack(copy);
  workspace.restoreTrackPhrase();
  assert.equal(service.project().tracks.find((track) => track.id === copy).editedPhrase, undefined);
  service.undo();
  service.removeTrack(copy);
  service.removeTrack(id);
  assert.equal(service.project().tracks.some((track) => track.role === 'melody'), false);
  assert.equal(ui.settingsTarget().kind, 'global-track');
  service.undo();
  assert.deepEqual(service.project().tracks.find((track) => track.id === id).editedPhrase, notes);
});

test('section phrase edits retain independent ownership through export, playback layout, undo and reload', () => {
  const { service, phrase, midi, load } = fixture();
  const id = 'track-bass-1';
  const section = service.project().arrangement[1];
  service.setSectionTrackPresence(1, id, true);
  service.setSectionTrackPresence(2, id, true);
  const track = () => service.project().tracks.find(track => track.id === id);
  const before = service.exportProjectJson();
  const global = phrase.phraseForTrack(service.project(), track());
  const local = [{ id: 'local-edit', midi: 51, startBeat: 1.25, durationBeats: .7, velocity: 113 }];
  service.updateSectionTrackPhrase(section.id, id, local);
  const after = service.exportProjectJson();
  assert.deepEqual(phrase.phraseForTrack(service.project(), track()), global);
  assert.deepEqual(phrase.layOutTrackPhrase({ ...service.project(), arrangement: [service.project().arrangement[1]] }, track()), local);
  assert.ok(new Midi(midi.generateCompositionMidi(service.project())).tracks.find(track => track.name === 'B1').notes.some(note => note.midi === 51));
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.redo(); assert.equal(service.exportProjectJson(), after);
  assert.equal(service.importProjectJson(after), true);
  assert.deepEqual(service.project().arrangement[1].trackPhrases[id], local);
  const invalid = JSON.parse(after); invalid.arrangement[1].trackPhrases[id][0].midi = 128;
  assertRejectedProjectImport(service, JSON.stringify(invalid));
  const duplicate = service.duplicateTrack(id);
  service.removeTrack(id);
  assert.equal(service.importProjectJson(service.exportProjectJson()), true, 'deleting a track removes its section phrases');
});

test('contextual Local Apply preserves other phrase cycles and leaves the global track untouched', () => {
  const { service, ui, phrase, load } = fixture();
  const id = 'track-drums-1';
  const section = service.project().arrangement[1];
  service.setSectionTrackPresence(1, id, true);
  service.updateTrackPhrase(id, [{ id: 'kick', midi: 36, startBeat: 0, durationBeats: .2, velocity: 90 }]);
  ui.selectedTrackId.set(id);
  const track = service.project().tracks.find(track => track.id === id);
  const before = phrase.layOutTrackPhrase({ ...service.project(), arrangement: [service.project().arrangement[1]] }, track);
  const Editor = load('features/edit/edit-workspace.component.ts').EditWorkspaceComponent;
  const editor = new Editor();
  editor.sectionId = section.id;
  editor.phraseStartBeat = 0;
  editor.phraseLength = 4;
  editor.ngOnInit();
  const history = service.exportProjectJson();
  editor.selectNote('kick'); editor.updateSelected('velocity', { target: { value: '117' } });
  assert.equal(service.exportProjectJson(), history);
  editor.applyLocalChanges();
  assert.deepEqual(service.project().tracks.find(track => track.id === id).editedPhrase, track.editedPhrase);
  const after = phrase.layOutTrackPhrase({ ...service.project(), arrangement: [service.project().arrangement[1]] }, track);
  assert.equal(after.find(note => note.startBeat === 0).velocity, 117);
  assert.deepEqual(after.filter(note => note.startBeat >= 4).map(({ id, ...note }) => note), before.filter(note => note.startBeat >= 4).map(({ id, ...note }) => note));
  service.undo(); assert.equal(service.exportProjectJson(), history, 'Local Apply is one undo entry');
});

test('multi-drum drafts keep ownership, smart-exit destinations and one atomic undo', () => {
  const { service, ui, load } = fixture();
  service.setRoleTrackCount('drums', 3);
  const tracks = service.project().tracks.filter(track => track.role === 'drums');
  const sectionId = service.project().arrangement[0].id;
  tracks.forEach((track, index) => service.updateTrackGeneratorSettings(track.id, { pitch: [36, 38, 42][index] }));
  const phrase = (midi, velocity) => [{ id: 'shared', midi, startBeat: 0, durationBeats: 1, velocity }];
  assert.equal(service.applyPhraseEdits([
    ...tracks.map((track, index) => ({ trackId: track.id, notes: phrase([36,38,42][index], 90) })),
    { trackId: tracks[1].id, sectionId, notes: phrase(38, 100) },
  ]), true);
  const before = service.exportProjectJson();
  const { EditWorkspaceComponent } = load('features/edit/edit-workspace.component.ts');
  ui.selectedTrackId.set(tracks[0].id);
  const editor = new EditWorkspaceComponent(); editor.sectionId = sectionId; editor.phraseLength = 16; editor.ngOnInit();
  assert.equal(editor.editedTracks().length, 3);
  assert.equal(new Set(editor.notes().map(note => note.id)).size, editor.notes().length, 'editor IDs are unique across tracks');
  assert.equal(editor.hasChanges(), false);
  editor.smartExit(); assert.equal(editor.cancelled.values.length, 1);
  assert.equal(service.exportProjectJson(), before, 'unchanged smart exit does not save');
  editor.selectAll();
  editor.replaceNotes(editor.notes().map(note => ({ ...note, velocity: note.velocity + 5 })));
  editor.undo(); assert.equal(editor.hasChanges(), false);
  editor.redo(); assert.equal(editor.hasChanges(), true);
  editor.smartExit(); assert.equal(editor.applied.values.length, 1);
  assert.equal(service.project().tracks.find(track => track.id === tracks[0].id).editedPhrase[0].velocity, 95);
  assert.equal(service.project().tracks.find(track => track.id === tracks[1].id).editedPhrase[0].velocity, 90);
  assert.equal(service.project().tracks.find(track => track.id === tracks[2].id).editedPhrase[0].velocity, 95);
  assert.equal(service.project().arrangement[0].trackPhrases[tracks[1].id][0].velocity, 105);
  service.undo(); assert.equal(service.exportProjectJson(), before, 'one project undo restores all drum phrases');
  const invalidBefore = service.exportProjectJson();
  assert.equal(service.applyPhraseEdits([{ trackId: tracks[0].id, notes: phrase(36, 110) },
    { trackId: tracks[2].id, notes: phrase(42, 200) }]), false);
  assert.equal(service.exportProjectJson(), invalidBefore, 'an invalid phrase aborts the whole batch');
});
