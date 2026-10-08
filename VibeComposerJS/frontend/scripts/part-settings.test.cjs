const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const ts = require('typescript');
const { Midi } = require('@tonejs/midi');

function fixture() {
  const cache = new Map();
  const signal = (value) => {
    const read = () => value;
    read.set = (next) => { value = next; };
    read.asReadonly = () => read;
    return read;
  };
  const input = (value) => signal(value);
  input.required = () => signal(undefined);
  let service, ui;
  const angular = { Injectable: () => (type) => type, Component: () => (type) => type,
    Input: () => () => {}, Output: () => () => {}, EventEmitter: class { values = []; emit(value) { this.values.push(value); } },
    signal, computed: (read) => read, effect: () => {}, input,
    output: () => ({ emit() {} }), ChangeDetectionStrategy: { OnPush: 0 },
    viewChild: Object.assign(() => () => undefined, { required: () => () => undefined }),
    inject: (type) => type.name === 'ProjectService' ? service : type.name === 'WorkspaceUiService' ? ui
      : type.name === 'AudioPlaybackService' ? {} : preferences,
  };
  const root = path.join(__dirname, '../src/app');
  function load(file) {
    const absolute = path.resolve(root, file);
    // Workspace state is real; child rendering components are covered by the Angular build and overview tests.
    if (absolute.endsWith('.component.ts') && !['workspace-canvas.component.ts', 'edit-workspace.component.ts', 'mix-workspace.component.ts']
      .some((name) => absolute.endsWith(name))) return {};
    if (cache.has(absolute)) return cache.get(absolute).exports;
    const module = { exports: {} };
    cache.set(absolute, module);
    const compiled = ts.transpileModule(fs.readFileSync(absolute, 'utf8'), { compilerOptions: {
      target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, experimentalDecorators: true,
    } }).outputText;
    new Function('require', 'module', 'exports', compiled)((name) => name === '@angular/core' ? angular
      : name === '@angular/common' ? { TitleCasePipe: class {} }
      : name.startsWith('.') ? load(path.resolve(path.dirname(absolute), name + '.ts')) : require(name), module, module.exports);
    return module.exports;
  }
  const stored = new Map();
  global.sessionStorage = { getItem: (key) => stored.get(key) ?? null, setItem: (key, value) => stored.set(key, value) };
  const sectionTypes = load('core/project/section-type-settings.service.ts');
  const preferences = { chances: () => sectionTypes.DEFAULT_SECTION_TYPE_CHANCES };
  const { ProjectService } = load('core/project/project.service.ts');
  service = new ProjectService();
  const { WorkspaceUiService } = load('shared/workspace-ui.service.ts');
  ui = new WorkspaceUiService();
  const { WorkspaceCanvasComponent } = load('shared/workspace-canvas.component.ts');
  const workspace = new WorkspaceCanvasComponent();
  const phrase = load('core/music/phrase.ts');
  const settings = load('core/music/part-settings.ts');
  const midi = load('core/music/midi-export.ts');
  return { service, ui, workspace, phrase, settings, midi, stored, load };
}

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

test('imports and mutations reject wrong-role fields, unknown tracks, and invalid effective ranges', () => {
  const { service } = fixture();
  const initial = service.exportProjectJson();
  const sectionId = service.project().arrangement[1].id;
  service.updatePartSettings({ kind: 'section-role', sectionId, role: 'bass' }, { voicing: 'open' });
  assert.equal(service.exportProjectJson(), initial);
  service.updatePartSettings({ kind: 'section-role', sectionId, role: 'bass' }, { velocityMin: 100 });
  assert.equal(service.exportProjectJson(), initial, 'effective min cannot exceed inherited max');
  for (const patch of [null, [], { rhythm: 'single' }, { octaveInterval: 0 }, { transpose: 99 }, { unknown: 1 }]) {
    const imported = JSON.parse(initial);
    imported.arrangement[1].rolePartOverrides = { bass: patch };
    assert.equal(service.importProjectJson(JSON.stringify(imported)), false);
  }
  const orphan = JSON.parse(initial);
  orphan.arrangement[1].trackPartOverrides = { missing: { transpose: 12 } };
  assert.equal(service.importProjectJson(JSON.stringify(orphan)), false);
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

test('melody has explicit type zero and manual-only tracks, with stable existing role seeds and channels', () => {
  const { service, ui, workspace, phrase, load } = fixture();
  const model = load('core/project/project.model.ts');
  assert.deepEqual(model.ARRANGED_PARTS.map((role) => model.PART_TYPES[role]), [0, 1, 2, 3, 4]);
  assert.equal(workspace.arrangedTracks()[0].role, 'melody');
  const melody = service.project().tracks.find((track) => track.role === 'melody');
  assert.equal(melody.midiChannel, 4);
  assert.equal(service.project().tracks.find((track) => track.role === 'drums').midiChannel, 10);
  assert.deepEqual(phrase.generateTrackPhrase(service.project(), melody), []);
  ui.selectRole('melody');
  assert.equal(workspace.partGenerationAvailable(), false);
  assert.deepEqual(workspace.editableScopes(), []);
  const existing = service.project().tracks.filter((track) => track.role !== 'melody');
  const notes = existing.map((track) => phrase.generateTrackPhrase(service.project(), track));
  const saved = JSON.parse(service.exportProjectJson());
  delete saved.melody; delete saved.mix.melody;
  saved.tracks = saved.tracks.filter((track) => track.role !== 'melody');
  for (const section of saved.arrangement) delete section.trackPresence[melody.id];
  assert.equal(service.importProjectJson(JSON.stringify(saved)), true);
  assert.deepEqual(JSON.parse(JSON.stringify(service.project().tracks)), existing, 'loading a four-role project preserves its track identities');
  for (let index = 0; index < existing.length; index++) {
    assert.deepEqual(phrase.generateTrackPhrase(service.project(), existing[index]), notes[index]);
  }
  assert.equal(workspace.partRole(), 'melody', 'an empty selected group must not borrow the bass context');
  const id = service.addTrack('melody');
  assert.equal(id, 'track-melody-1');
  assert.equal(service.project().tracks.find((track) => track.id === id).midiChannel, 4);
  assert.equal(workspace.arrangedTracks()[0].id, id);
});

test('empty roles can be recreated from their defaults, while the final project track is retained', () => {
  const { service, workspace, load } = fixture();
  const model = load('core/project/project.model.ts');
  for (const role of model.ARRANGED_PARTS) {
    const track = service.project().tracks.find((candidate) => candidate.role === role);
    const defaults = service.project()[role];
    service.removeTrack(track.id);
    assert.equal(service.project().tracks.some((candidate) => candidate.role === role), false);
    const id = service.addTrack(role);
    const added = service.project().tracks.find((candidate) => candidate.id === id);
    assert.equal(added.role, role);
    assert.deepEqual(added.generatorSettings, defaults);
    assert.equal(added.editedPhrase, undefined);
    assert.equal(added.midiChannel === 10, role === 'drums');
    for (const section of service.project().arrangement) assert.equal(typeof section.trackPresence[id], 'boolean');
  }
  for (const track of [...service.project().tracks].slice(0, -1)) service.removeTrack(track.id);
  const last = service.project().tracks[0];
  assert.equal(workspace.canRemoveTrack(last.id), false);
  const before = service.exportProjectJson();
  service.removeTrack(last.id);
  assert.equal(service.exportProjectJson(), before);
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
  assert.equal(editor.generationAvailable(), false);
  assert.equal(editor.tracks()[0].role, 'melody');
  const before = service.exportProjectJson();
  editor.addNote();
  assert.equal(editor.noteCount(), 1);
  assert.equal(service.exportProjectJson(), before, 'draft edits do not write project history');
  const draft = [...editor.draftNotes()];
  editor.restoreGenerated();
  assert.deepEqual(editor.draftNotes(), draft, 'unavailable generation cannot discard the draft');
  editor.applyChanges();
  workspace.closeEditor();
  const track = () => service.project().tracks.find((candidate) => candidate.id === id);
  assert.deepEqual(track().editedPhrase, draft);
  const laidOut = phrase.layOutTrackPhrase(service.project(), track());
  assert.equal(laidOut.length, 1);
  const exported = new Midi(midi.generateCompositionMidi(service.project()));
  assert.equal(exported.tracks[0].name, 'M1');
  assert.equal(exported.tracks[0].channel, 3);
  assert.equal(exported.tracks[0].instrument.number, 73);
  assert.equal(exported.tracks[0].notes[0].midi, draft[0].midi);
  assert.equal(exported.tracks[0].notes.length, laidOut.length);
  const { MixWorkspaceComponent } = load('features/mix/mix-workspace.component.ts');
  const mixer = new MixWorkspaceComponent();
  assert.equal(mixer.tracks()[0].role, 'melody');
  assert.equal(mixer.noteCount(id), 1);
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
  assert.deepEqual(service.project().tracks.find((track) => track.id === copy).editedPhrase, notes);
  service.removeTrack(copy);
  service.removeTrack(id);
  assert.equal(service.project().tracks.some((track) => track.role === 'melody'), false);
  assert.equal(ui.settingsTarget().kind, 'global-track');
  service.undo();
  assert.deepEqual(service.project().tracks.find((track) => track.id === id).editedPhrase, notes);
});

test('unavailable melody settings and legacy algorithms are rejected without changing the project', () => {
  const { service } = fixture();
  const sectionId = service.project().arrangement[0].id;
  const before = service.exportProjectJson();
  for (const scope of [{ kind: 'global-role', role: 'melody' }, { kind: 'global-track', trackId: 'track-melody-1' },
    { kind: 'section-role', sectionId, role: 'melody' }, { kind: 'section-track', sectionId, trackId: 'track-melody-1' }]) {
    service.updatePartSettings(scope, { transpose: 7 });
    assert.equal(service.exportProjectJson(), before);
  }
  for (const settings of [{ algorithm: 'legacy' }, {}, { algorithm: 'block', melodyLegacyMode: true }, { algorithm: 'block', transpose: 12 }]) {
    const saved = JSON.parse(before);
    saved.tracks.find((track) => track.role === 'melody').generatorSettings = settings;
    assert.equal(service.importProjectJson(JSON.stringify(saved)), false);
    assert.equal(service.exportProjectJson(), before);
  }
});

test('section chance preferences gain melody defaults while retaining saved probabilities for other roles', () => {
  const { load } = fixture();
  const previousStorage = global.localStorage;
  const values = new Map([['vibecomposer.section-type-settings.v1', JSON.stringify({
    CHORUS1: { bass: 13, chords: 72, arpeggio: 19, drums: 81 },
  })]]);
  global.localStorage = { getItem: (key) => values.get(key) ?? null, setItem: (key, value) => values.set(key, value) };
  try {
    const { SectionTypeSettingsService } = load('core/project/section-type-settings.service.ts');
    const preferences = new SectionTypeSettingsService();
    assert.equal(preferences.chances().INTRO.melody, 20);
    assert.equal(preferences.chances().HALF_CHORUS.melody, 0);
    assert.equal(preferences.chances().CLIMAX.melody, 100);
    assert.equal(preferences.chances().CHORUS1.melody, 50);
    assert.equal(preferences.chances().CHORUS1.bass, 13);
    preferences.setChance('CHORUS1', 'melody', 87);
    assert.equal(new SectionTypeSettingsService().chances().CHORUS1.melody, 87);
    assert.equal(preferences.chances().CHORUS1.bass, 13);
  } finally { global.localStorage = previousStorage; }
});
