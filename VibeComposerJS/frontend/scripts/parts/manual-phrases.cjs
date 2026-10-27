const assert = require('node:assert/strict');
const test = require('node:test');
const { Midi } = require('@tonejs/midi');
const { fixture, projectData } = require('../support/project-fixture.cjs');
const { assertRejectedProjectImport } = require('../support/project-import-assertions.cjs');

test('global range Apply from a short section preserves each longer section continuation through history, reload and MIDI', () => {
  const { service, ui, phrase, midi, load } = fixture();
  const id = 'track-bass-1';
  service.updateSection(0, { measures: 1 });
  service.updateSection(1, { measures: 8 });
  service.setSectionTrackPresence(0, id, true);
  service.setSectionTrackPresence(1, id, true);
  const sectionId = service.project().arrangement[0].id;
  const track = () => service.project().tracks.find(track => track.id === id);
  const layout = index => phrase.layOutTrackPhrase({ ...service.project(), arrangement: [service.project().arrangement[index]] }, track());
  const continuation = layout(1).filter(note => note.startBeat >= 4 && note.startBeat < 32);
  assert.ok(continuation.length > 0);
  const before = service.exportProjectJson();
  ui.selectedTrackId.set(id);
  const Editor = load('features/edit/edit-workspace.component.ts').EditWorkspaceComponent;
  const editor = new Editor(); editor.sectionId = sectionId; editor.phraseLength = 4; editor.ngOnInit();
  const first = editor.notes().find(note => note.startBeat >= 0);
  editor.selectNote(first.id); editor.updateSelected('velocity', { target: { value: '117' } });
  editor.applyChanges();
  assert.equal(editor.applyError(), null);
  assert.equal(layout(0).find(note => note.id === first.id).velocity, 117);
  assert.equal(layout(1).find(note => note.startBeat === first.startBeat).velocity, 117);
  assert.deepEqual(layout(1).filter(note => note.startBeat >= 4 && note.startBeat < 32), continuation,
    'later notes preserve pitches, timing, velocity and duration, including later phrase cycles');
  const after = service.exportProjectJson();
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.redo(); assert.equal(service.exportProjectJson(), after);
  assert.equal(service.importProjectJson(after), true);
  assert.deepEqual(layout(1).filter(note => note.startBeat >= 4 && note.startBeat < 32), continuation);
  const isolated = { ...service.project(), arrangement: [service.project().arrangement[1]] };
  const parsed = new Midi(midi.generateCompositionMidi(isolated));
  const exported = parsed.tracks.find(t => t.name === track().name);
  assert.ok(exported.notes.some(note => note.ticks / parsed.header.ppq >= 16), 'export retains the continuation too');
});

test('global range Apply from a long section excludes later body notes from short sections but retains explicit tails and sustains', () => {
  const { service, ui, phrase, load } = fixture();
  const id = 'track-bass-1';
  service.updateSection(0, { measures: 4 });
  service.updateSection(1, { measures: 1 });
  service.setSectionTrackPresence(0, id, true); service.setSectionTrackPresence(1, id, true);
  service.updateTrackPhrase(id, [
    { id: 'first', midi: 48, startBeat: 0, durationBeats: 1, velocity: 90 },
    { id: 'sustain', midi: 50, startBeat: 3, durationBeats: 3, velocity: 90 },
    { id: 'later-bar', midi: 52, startBeat: 4, durationBeats: 1, velocity: 90 },
    { id: 'later-still', midi: 53, startBeat: 7, durationBeats: 1, velocity: 90 },
  ]);
  ui.selectedTrackId.set(id);
  const Editor = load('features/edit/edit-workspace.component.ts').EditWorkspaceComponent;
  const editor = new Editor(); editor.sectionId = service.project().arrangement[0].id; editor.phraseLength = 16; editor.ngOnInit();
  editor.selectNote('first'); editor.updateSelected('velocity', { target: { value: '111' } });
  editor.addNoteAt({ midi: 55, startBeat: 17, durationBeats: 1 });
  editor.applyChanges(); assert.equal(editor.applyError(), null);
  const track = service.project().tracks.find(track => track.id === id);
  const short = phrase.layOutTrackPhrase({ ...service.project(), arrangement: [service.project().arrangement[1]] }, track);
  assert.deepEqual(short.map(note => [note.midi, note.startBeat, note.durationBeats]), [[48, 0, 1], [50, 3, 3], [55, 5, 1]]);
  assert.equal(short[0].velocity, 111);
});

test('empty global ranges suppress notes and later windows preserve previous edits without implicit repetition', () => {
  const { service, ui, phrase, load } = fixture();
  const id = 'track-bass-1';
  service.updateSection(0, { measures: 8 }); service.setSectionTrackPresence(0, id, true);
  const sectionId = service.project().arrangement[0].id;
  const track = () => service.project().tracks.find(track => track.id === id);
  const layout = () => phrase.layOutTrackPhrase({ ...service.project(), arrangement: [service.project().arrangement[0]] }, track());
  const continuation = layout().filter(note => note.startBeat >= 4);
  ui.selectedTrackId.set(id);
  const Editor = load('features/edit/edit-workspace.component.ts').EditWorkspaceComponent;
  const first = new Editor(); first.sectionId = sectionId; first.phraseLength = 4; first.ngOnInit();
  first.selectAll(); first.deleteSelected(); first.applyChanges();
  assert.equal(first.applyError(), null);
  assert.equal(layout().some(note => note.startBeat < 4), false);
  assert.deepEqual(layout(), continuation);
  const later = new Editor(); later.sectionId = sectionId; later.phraseStartBeat = 16; later.phraseLength = 4; later.ngOnInit();
  later.selectAll(); later.deleteSelected(); later.addNoteAt({ midi: 72, startBeat: 1, durationBeats: 1 });
  later.applyChanges(); assert.equal(later.applyError(), null);
  assert.equal(layout().some(note => note.startBeat < 4), false, 'earlier silence remains owned');
  assert.ok(layout().some(note => note.midi === 72 && note.startBeat === 17));
  assert.deepEqual(layout().filter(note => note.startBeat >= 4 && note.startBeat < 16), continuation.filter(note => note.startBeat < 16));
  assert.equal(service.importProjectJson(service.exportProjectJson()), true);
  assert.equal(layout().some(note => note.startBeat < 4), false, 'empty replacement survives reload');
});

test('range edits respect local and generated ownership, absence, duplication, restore and Mode conversion', () => {
  const { service, phrase, uiPreferences } = fixture();
  const id = 'track-bass-1';
  const notes = [{ id: 'range', midi: 64, startBeat: 0, durationBeats: 1, velocity: 111 }];
  const sectionId = service.project().arrangement[0].id;
  assert.equal(service.applyPhraseEdits([{ trackId: id, notes, range: { startBeat: 0, endBeat: 4 } }]), true);
  const track = () => service.project().tracks.find(track => track.id === id);
  const section = { ...service.project().arrangement[0], measures: 1, trackPresence: { [id]: true } };
  const layout = overrides => phrase.layOutTrackPhrase({ ...service.project(), arrangement: [{ ...section, ...overrides }] }, track());
  assert.deepEqual(layout({}), notes);
  assert.deepEqual(layout({ trackPresence: { [id]: false } }), []);
  assert.deepEqual(layout({ trackPhrases: { [id]: [] } }), []);
  assert.equal(layout({ generatedTrackIds: [id] }).some(note => note.id === 'range'), false);
  const copy = service.duplicateTrack(id);
  assert.deepEqual(service.project().tracks.find(track => track.id === copy).phraseEdits, track().phraseEdits);
  assert.notStrictEqual(service.project().tracks.find(track => track.id === copy).phraseEdits[0].notes, track().phraseEdits[0].notes);
  assert.equal(uiPreferences.manualPhrasesFollowMode(), true);
  service.updateSettings({ scale: 'natural-minor' });
  assert.equal(track().phraseEdits[0].notes[0].midi, 63);
  service.clearTrackPhrase(id);
  assert.equal(track().phraseEdits, undefined);
  service.undo(); assert.equal(track().phraseEdits[0].notes[0].midi, 63);
  service.clearEditedPhrase('bass'); assert.equal(track().phraseEdits, undefined);
  assert.equal(service.project().arrangement[0].id, sectionId);
});

test('range windows validate atomically and preserve notes sustaining past an interior edit boundary', () => {
  const { service, phrase } = fixture();
  const id = 'track-bass-1';
  const note = { id: 'crossing', midi: 50, startBeat: 3, durationBeats: 5, velocity: 90 };
  assert.equal(service.applyPhraseEdits([{ trackId: id, notes: [note], range: { startBeat: 0, endBeat: 4 } }]), true);
  const track = () => service.project().tracks.find(track => track.id === id);
  assert.equal(phrase.phraseForTrack(service.project(), track()).find(n => n.id === note.id).durationBeats, 5);
  const saved = service.exportProjectJson();
  for (const range of [{ startBeat: 4, endBeat: 4 }, { startBeat: -1, endBeat: 4 }, { startBeat: 0, endBeat: Infinity },
    { startBeat: 1, endBeat: 4, pickupBeats: 4 }, { startBeat: 0, endBeat: 4, tailBeats: -1 }]) {
    assert.equal(service.applyPhraseEdits([{ trackId: id, notes: [], range }]), false);
    assert.equal(service.exportProjectJson(), saved);
  }
  const invalid = JSON.parse(saved); invalid.tracks.find(track => track.id === id).phraseEdits[0].endBeat = 0;
  assertRejectedProjectImport(service, JSON.stringify(invalid));
  assert.equal(service.applyPhraseEdits([{ trackId: id, notes: [], range: { startBeat: 0, endBeat: 4 } }]), true);
  assert.equal(track().phraseEdits.length, 1, 'resaving the same window replaces its old data');
});

test('custom-duration range edits use actual beats and anchor explicit margins to each destination boundary', () => {
  const { service, ui, phrase, load } = fixture();
  const id = 'track-bass-1';
  service.updateSection(0, { measures: 1 }); service.updateSection(1, { measures: 4 });
  service.setSectionMusicalState(service.project().arrangement[0].id, { customDurations: [2] });
  service.setSectionTrackPresence(0, id, true); service.setSectionTrackPresence(1, id, true);
  const track = () => service.project().tracks.find(track => track.id === id);
  const layout = index => phrase.layOutTrackPhrase({ ...service.project(), arrangement: [service.project().arrangement[index]] }, track());
  const continuation = layout(1).filter(note => note.startBeat >= 2 && note.startBeat < 16);
  ui.selectedTrackId.set(id);
  const Editor = load('features/edit/edit-workspace.component.ts').EditWorkspaceComponent;
  const editor = new Editor(); editor.sectionId = service.project().arrangement[0].id; editor.phraseLength = 2; editor.ngOnInit();
  editor.selectAll(); editor.deleteSelected();
  for (const startBeat of [-1, 1, 3]) editor.addNoteAt({ midi: 60, startBeat, durationBeats: .5 });
  editor.applyChanges(); assert.equal(editor.applyError(), null);
  assert.deepEqual(layout(0).map(note => note.startBeat), [-1, 1, 3]);
  assert.deepEqual(layout(1).filter(note => note.startBeat >= 2 && note.startBeat < 16), continuation);
  assert.deepEqual(layout(1).filter(note => note.midi === 60).map(note => note.startBeat), [-1, 1, 17]);
});

test('melody consumers resolve global replacement windows even when the melody track is absent', () => {
  const { service, phrase } = fixture();
  const id = 'track-melody-1';
  const original = phrase.sectionMelodyContext(service.project(), service.project().arrangement[0]);
  const notes = [{ id: 'lead-edit', midi: 79, startBeat: 0, durationBeats: 1, velocity: 111 }];
  assert.equal(service.applyPhraseEdits([{ trackId: id, notes, range: { startBeat: 0, endBeat: 4 } }]), true);
  service.setSectionTrackPresence(0, id, false);
  const project = service.project(), section = project.arrangement[0];
  const context = phrase.sectionMelodyContext(project, section);
  assert.equal(context.pitches[0][0], 79);
  assert.deepEqual(context.pitches.slice(1), original.pitches.slice(1), 'outside the window the dependency keeps generated notes');
  const forced = phrase.sectionMelodyContext(project, { ...section, generatedTrackIds: [id] });
  assert.deepEqual(forced, original);
  const local = phrase.sectionMelodyContext(project, { ...section, trackPhrases: { [id]: [{ ...notes[0], midi: 57 }] } });
  assert.equal(local.pitches[0][0], 57, 'local replacement takes precedence over track-owned ranges');
  service.setSectionMusicalState(section.id, { customDurations: [2, 6, 3, 5] });
  assert.equal(service.applyPhraseEdits([{ trackId: id, notes: [{ ...notes[0], startBeat: 3 }],
    range: { startBeat: 2, endBeat: 8 } }]), true);
  const custom = phrase.sectionMelodyContext(service.project(), service.project().arrangement[0]);
  assert.equal(custom.pitches[1][2], 79, 'actual section beats map back to the dependent part chord grid');
});

test('section pickups and late notes survive editing, local apply, history, reload and MIDI export', () => {
  const { service, phrase, midi, ui, workspace, load } = fixture();
  const id = 'track-bass-1';
  service.updateSection(0, { measures: 1 });
  const section = service.project().arrangement[0];
  service.setSectionTrackPresence(0, id, true);
  const original = [
    { id: 'pickup', midi: 48, startBeat: -4, durationBeats: 1, velocity: 90 },
    { id: 'crossing', midi: 50, startBeat: 3, durationBeats: 3, velocity: 90 },
    { id: 'late', midi: 52, startBeat: 7, durationBeats: 1, velocity: 90 },
  ];
  assert.equal(service.updateSectionTrackPhrase(section.id, id, original), true);
  ui.selectedTrackId.set(id);
  const Editor = load('features/edit/edit-workspace.component.ts').EditWorkspaceComponent;
  const editor = new Editor(); editor.sectionId = section.id; editor.phraseLength = 4; editor.ngOnInit();
  assert.deepEqual(editor.editBounds(), { startBeat: -4, endBeat: 8 });
  assert.deepEqual(editor.draftNotes().map(({ trackId, ...note }) => note), original);
  editor.selectNote('pickup');
  editor.updateSelected('startBeat', { target: { value: '-4' } });
  assert.equal(editor.selectedNote().startBeat, -4, 'reject a start earlier than the extra measure');
  editor.updateSelected('velocity', { target: { value: '111' } });
  const before = service.exportProjectJson();
  editor.applyLocalChanges();
  assert.equal(editor.applyError(), null);
  const saved = service.project().arrangement[0].trackPhrases[id];
  assert.equal(saved.length, 3, 'saving does not duplicate pickups or late notes');
  assert.equal(saved.find(note => note.id === 'pickup').velocity, 111);
  assert.equal(saved.find(note => note.id === 'crossing').durationBeats, 3);
  const after = service.exportProjectJson();
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.redo(); assert.equal(service.exportProjectJson(), after);
  assert.equal(service.importProjectJson(after), true);
  const track = service.project().tracks.find(track => track.id === id);
  const owned = phrase.layOutTrackPhrase(service.project(), track, true, true).find(note => note.id === 'late');
  assert.equal(owned.sectionId, section.id);
  workspace.openNoteEditor({ ...owned, part: id, color: 'bass', opacity: 1, topPercent: 0 });
  assert.equal(workspace.editSectionId(), section.id, 'late onset still opens its owning section');
  assert.equal(workspace.editPhraseOffset(), 0);
  const parsed = new Midi(midi.generateCompositionMidi(service.project()));
  const exported = parsed.tracks.find(t => t.name === track.name).notes;
  assert.ok(exported.some(note => note.midi === 48 && note.ticks === 0));
  assert.ok(exported.some(note => note.midi === 52 && note.ticks / parsed.header.ppq === 11));
});

test('section allowance follows the first and last custom measure durations', () => {
  const { service, ui, load } = fixture();
  service.updateSection(0, { measures: 2 });
  const section = service.project().arrangement[0];
  service.setSectionMusicalState(section.id, { customDurations: [2, 6] });
  ui.selectedTrackId.set('track-bass-1');
  const Editor = load('features/edit/edit-workspace.component.ts').EditWorkspaceComponent;
  const editor = new Editor(); editor.sectionId = section.id; editor.phraseLength = 8; editor.ngOnInit();
  assert.deepEqual(editor.editBounds(), { startBeat: -2, endBeat: 14 });
  editor.addNoteAt({ midi: 48, startBeat: -2, durationBeats: 1 });
  editor.addNoteAt({ midi: 48, startBeat: 13, durationBeats: 2 });
  assert.equal(editor.selectedNote().durationBeats, 1);
  const count = editor.notes().length;
  editor.addNoteAt({ midi: 48, startBeat: -2.25 });
  editor.addNoteAt({ midi: 48, startBeat: 14 });
  assert.equal(editor.notes().length, count);
});

test('global Apply retains pickups and the extra measure when a section is shorter than the global phrase', () => {
  const { service, ui, phrase, load } = fixture();
  const id = 'track-bass-1';
  service.updateSection(0, { measures: 1 });
  const section = service.project().arrangement[0];
  service.setSectionTrackPresence(0, id, true);
  service.updateTrackPhrase(id, []);
  ui.selectedTrackId.set(id);
  const Editor = load('features/edit/edit-workspace.component.ts').EditWorkspaceComponent;
  const editor = new Editor(); editor.sectionId = section.id; editor.phraseLength = 4; editor.ngOnInit();
  editor.addNoteAt({ midi: 48, startBeat: -4, durationBeats: 1 });
  editor.addNoteAt({ midi: 50, startBeat: 7, durationBeats: 1 });
  editor.applyChanges();
  assert.equal(editor.applyError(), null);
  const track = service.project().tracks.find(track => track.id === id);
  assert.deepEqual(track.phraseEdits[0].notes.map(note => note.startBeat), [-4, 7]);
  const arranged = phrase.layOutTrackPhrase({ ...service.project(), arrangement: [service.project().arrangement[0]] }, track);
  assert.deepEqual(arranged.map(note => note.startBeat), [-4, 7]);
});

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
  const { ScoreCanvasComponent } = load('features/score/score-canvas.component.ts');
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
  assert.deepEqual(phrase.phraseForTrack(service.project(), track()), draft);
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
  assert.deepEqual(phrase.phraseForTrack(service.project(), track()), draft);
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
  assert.equal(service.project().tracks.find(track => track.id === tracks[0].id).phraseEdits[0].notes[0].velocity, 95);
  assert.equal(service.project().tracks.find(track => track.id === tracks[1].id).editedPhrase[0].velocity, 90);
  assert.equal(service.project().tracks.find(track => track.id === tracks[2].id).phraseEdits[0].notes[0].velocity, 95);
  assert.equal(service.project().arrangement[0].trackPhrases[tracks[1].id][0].velocity, 105);
  service.undo(); assert.equal(service.exportProjectJson(), before, 'one project undo restores all drum phrases');
  const invalidBefore = service.exportProjectJson();
  assert.equal(service.applyPhraseEdits([{ trackId: tracks[0].id, notes: phrase(36, 110) },
    { trackId: tracks[2].id, notes: phrase(42, 200) }]), false);
  assert.equal(service.exportProjectJson(), invalidBefore, 'an invalid phrase aborts the whole batch');
});
