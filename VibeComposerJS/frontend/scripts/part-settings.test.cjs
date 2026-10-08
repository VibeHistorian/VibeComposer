const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const ts = require('typescript');
const { Midi } = require('@tonejs/midi');

// The decoder materializes optional empty maps; compare musical data rather than property order/omission.
function projectData(json) {
  const project = JSON.parse(json);
  for (const section of project.arrangement) {
    section.rolePartOverrides ??= {};
    section.trackPartOverrides ??= {};
  }
  return project;
}

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
    if (absolute.endsWith('.component.ts') && !['workspace-canvas.component.ts', 'edit-workspace.component.ts', 'mix-workspace.component.ts', 'part-settings-editor.component.ts']
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
  assert.equal(service.freezePartSettings({ ...cell, role: 'melody' }), 'invalid');
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

test('all fill masks match the actual Java enum, including odd halves, flipped silence and repeated slots', () => {
  const { load } = fixture();
  const { chordSpanFillMask, CHORD_SPAN_FILLS } = load('core/music/chord-span-fill.ts');
  const expected = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/chord-span-fill.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(Object.keys(expected).length, 264);
  for (const [key, mask] of Object.entries(expected)) {
    const [fill, length, flipped] = key.split('/');
    assert.deepEqual(chordSpanFillMask(Number(length), fill, flipped === 'true'), mask, key);
  }
  assert.equal(CHORD_SPAN_FILLS.length, 12);
  assert.deepEqual(chordSpanFillMask(5, 'HALF1'), [1, 1, 0, 0, 0]);
  assert.deepEqual(chordSpanFillMask(5, 'HALF2'), [0, 0, 1, 1, 1]);
  assert.deepEqual(chordSpanFillMask(3, 'ODD'), [0, 1, 0]);
  assert.deepEqual(chordSpanFillMask(3, 'EVEN'), [1, 0, 1]);
  for (const args of [[33], [-1], [1.5], [3, 'UNKNOWN'], [3, 'ALL', 1]]) {
    assert.throws(() => chordSpanFillMask(...args), RangeError);
  }
});

test('fill produces rests in every supported generator and preserves role-specific random consumption', () => {
  const { service, load } = fixture();
  const seed = 9223372036854775807n;
  const progression = [1, 1, 1, 1, 1];
  const { generateBassline } = load('core/music/bass-generator.ts');
  const { generateChordPart } = load('core/music/chord-generator.ts');
  const { generateArpeggio } = load('core/music/arpeggio-generator.ts');
  const { generateDrumPart } = load('core/music/drum-generator.ts');
  const calls = {
    bass: (settings) => generateBassline(seed, 'C', 'major', progression, 'full', 100, settings),
    chords: (settings) => generateChordPart(seed, 'C', 'major', progression, settings),
    arpeggio: (settings) => generateArpeggio(seed, 'C', 'major', progression, { ...settings, pattern: 'random' }),
    drums: (settings) => generateDrumPart(seed, progression.length, settings),
  };
  for (const [role, generate] of Object.entries(calls)) {
    const settings = service.project()[role];
    const baseline = generate(settings);
    assert.deepEqual(generate({ ...settings, chordSpanFill: 'ALL', fillFlip: false }), baseline, role + ' defaults');
    assert.deepEqual(generate({ ...settings, chordSpanFill: 'ALL', fillFlip: true }), [], role + ' silence');
    const filled = generate({ ...settings, chordSpanFill: 'ODD' });
    const slot = (event) => event.chordIndex ?? event.barIndex;
    assert.ok(filled.length > 0);
    assert.ok(filled.every((event) => slot(event) === 1 || slot(event) === 3), role);
    if (role !== 'bass') assert.deepEqual(filled, baseline.filter((event) => slot(event) % 2 === 1), role + ' rests keep streams');
    else {
      const strip = (event) => ({ midi: event.midi, duration: event.durationBeats, velocity: event.velocity });
      assert.deepEqual(filled.filter((event) => slot(event) === 1).map(strip),
        baseline.filter((event) => slot(event) === 0).map(strip), 'bass skips dynamics/variation for silent chords');
    }
  }
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

test('invalid fills fail at every scope and on import; omitted fields keep older projects readable', () => {
  const { service } = fixture();
  const sectionId = service.project().arrangement[0].id;
  for (const role of ['bass', 'chords', 'arpeggio', 'drums']) {
    const trackId = service.project().tracks.find((track) => track.role === role).id;
    const before = service.exportProjectJson();
    const scopes = [{ kind: 'global-role', role }, { kind: 'global-track', trackId },
      { kind: 'section-role', sectionId, role }, { kind: 'section-track', sectionId, trackId }];
    for (const scope of scopes) {
      for (const patch of [{ chordSpanFill: 'half' }, { chordSpanFill: null }, { fillFlip: 1 }]) {
        service.updatePartSettings(scope, patch);
        assert.equal(service.exportProjectJson(), before);
      }
    }
    const invalid = JSON.parse(before);
    invalid.tracks.find((track) => track.id === trackId).generatorSettings.chordSpanFill = 'UNKNOWN';
    assert.equal(service.importProjectJson(JSON.stringify(invalid)), false);
    assert.equal(service.importProjectJson(before), true);
    assert.equal(service.project().tracks.find((track) => track.id === trackId).generatorSettings.chordSpanFill, undefined);
  }
  const before = service.exportProjectJson();
  service.updatePartSettings({ kind: 'section-role', sectionId, role: 'melody' }, { chordSpanFill: 'ODD' });
  assert.equal(service.exportProjectJson(), before);
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
  service.updatePartSettings({ kind: 'global-track', trackId }, { transpose: -12, rate: 'sixteenth' });
  const track = service.project().tracks.find((track) => track.id === trackId);
  const local = settings.resolvePartTrack(track, service.project().arrangement[0]).generatorSettings;
  assert.equal(local.transpose, -12, 'manually restored transpose now follows global changes');
  assert.equal(local.rate, 'eighth', 'untouched frozen fields remain explicit');
  assert.equal(settings.resolvePartTrack(track, service.project().arrangement[1]).generatorSettings.transpose, 0);
});

test('static rhythm rotation matches Java before truncation, including short and non-eight-multiple grids', () => {
  const { load } = fixture();
  const { rhythmPatternMask } = load('core/music/rhythm-pattern.ts');
  const expected = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/rhythm-pattern.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(Object.keys(expected).length, 192);
  for (const [key, mask] of Object.entries(expected)) {
    const [pattern, hits, shift] = key.split('/');
    assert.deepEqual(rhythmPatternMask(pattern, Number(hits), Number(shift)), mask, key);
    assert.deepEqual(rhythmPatternMask(pattern, Number(hits), Number(shift), true), mask.map((value) => 1 - value), key + ' flip');
  }
  assert.deepEqual(rhythmPatternMask('ALT', 3, 1), [0, 1, 0], 'rotate the padded eight cells rather than a three-cell slice');
  assert.deepEqual(rhythmPatternMask('ONESIX', 8), [1, 0, 0, 0, 0, 1, 0, 0]);
  for (const args of [['CUSTOM', 8], ['FULL', 0], ['FULL', 33], ['ALT', 1.5], ['ALT', 8, -1], ['ALT', 8, 9], ['ALT', 8, 0, 1]]) {
    assert.throws(() => rhythmPatternMask(...args), RangeError);
  }
});

test('chord rhythm hits, shift and flip affect note onsets/durations while retaining every grid-step velocity draw', () => {
  const { service, load } = fixture();
  const { generateChordPart } = load('core/music/chord-generator.ts');
  const { JavaRandom } = load('core/music/java-random.ts');
  const seed = 9223372036854775807n;
  const settings = service.project().chords;
  const generate = (patch) => generateChordPart(seed, 'C', 'major', [1, 5], { ...settings, ...patch });
  const baseline = generate({});
  assert.deepEqual(generate({ hitsPerPattern: 8, patternShift: 0, patternFlip: false }), baseline);
  const expectedMasks = {
    full: [1, 1, 1, 1, 1, 1, 1, 1], half: [1, 0, 1, 0, 1, 0, 1, 0],
    tresillo: [1, 0, 0, 1, 0, 0, 1, 0], sparse: [1, 0, 0, 0, 1, 0, 0, 0], single: [1, 0, 0, 0, 0, 0, 0, 0],
  };
  for (const [rhythm, mask] of Object.entries(expectedMasks)) {
    const notes = generate({ rhythm });
    assert.deepEqual(notes.map((note) => note.startBeat), [0, 4].flatMap((start) => mask.flatMap((value, index) => value ? [start + index / 2] : [])));
    assert.ok(notes.every((note) => note.durationBeats === 0.5));
  }
  assert.deepEqual(generate({ rhythm: 'one-six' }).map((note) => note.startBeat), [0, 2.5, 4, 6.5]);
  const shifted = generate({ rhythm: 'half', hitsPerPattern: 3, patternShift: 1, noteLengthPercent: 75 });
  assert.deepEqual(shifted.map((note) => note.startBeat), [4 / 3, 4 + 4 / 3]);
  assert.ok(shifted.every((note) => note.durationBeats === 1));
  const random = new JavaRandom(BigInt.asIntN(32, BigInt.asIntN(32, seed) + 20000n));
  random.nextInt(21);
  assert.equal(shifted[0].velocity, random.nextInt(21) + 69, 'rest at step 0 consumes its velocity draw');
  const dense = generate({ hitsPerPattern: 32 });
  assert.equal(dense.length, 64);
  assert.equal(dense.at(-1).startBeat, 7.875);
  assert.ok(dense.every((note) => note.durationBeats === 0.125));
  assert.deepEqual(generate({ patternFlip: true }), []);
  assert.deepEqual(generate({ rhythm: 'half', patternFlip: true, chordSpanFill: 'EVEN' }).map((note) => note.startBeat), [0.5, 1.5, 2.5, 3.5]);
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

test('unsupported roles and invalid rhythm controls reject edits/imports without discarding older settings', () => {
  const { service } = fixture();
  const sectionId = service.project().arrangement[0].id;
  const trackId = 'track-chords-1';
  const before = service.exportProjectJson();
  const scopes = [{ kind: 'global-role', role: 'chords' }, { kind: 'global-track', trackId },
    { kind: 'section-role', sectionId, role: 'chords' }, { kind: 'section-track', sectionId, trackId }];
  const invalid = [{ hitsPerPattern: 0 }, { hitsPerPattern: 33 }, { hitsPerPattern: 1.5 },
    { patternShift: -1 }, { patternShift: 9 }, { patternFlip: 1 }, { rhythm: 'custom' }, { rhythm: 'unknown' },
    { euclideanPulses: -1 }, { euclideanPulses: 33 }, { euclideanPulses: 1.5 }, { euclideanPulses: '4' }];
  for (const scope of scopes) for (const patch of invalid) {
    service.updatePartSettings(scope, patch);
    assert.equal(service.exportProjectJson(), before);
  }
  for (const role of ['bass', 'arpeggio', 'drums', 'melody']) {
    service.updatePartSettings({ kind: 'section-role', sectionId, role }, { hitsPerPattern: 16 });
    assert.equal(service.exportProjectJson(), before);
    service.updatePartSettings({ kind: 'section-role', sectionId, role }, { euclideanPulses: 4 });
    assert.equal(service.exportProjectJson(), before);
  }
  for (const patch of invalid) {
    const project = JSON.parse(before);
    Object.assign(project.tracks.find((track) => track.id === trackId).generatorSettings, patch);
    assert.equal(service.importProjectJson(JSON.stringify(project)), false);
  }
  assert.equal(service.importProjectJson(before), true);
  assert.equal(service.project().tracks.find((track) => track.id === trackId).generatorSettings.hitsPerPattern, undefined);
});

test('Euclidean grids match production Java for all supported hits, pulse counts, shifts and flipped complements', () => {
  const { load } = fixture();
  const { euclideanPatternMask } = load('core/music/rhythm-pattern.ts');
  const fixtures = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/euclidean-pattern.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(Object.keys(fixtures).length, 5040);
  for (const [key, expected] of Object.entries(fixtures)) {
    const args = key.split('/').map(Number);
    assert.deepEqual(euclideanPatternMask(...args), expected, key);
    assert.deepEqual(euclideanPatternMask(...args, true), expected.map((value) => 1 - value), key + ' flipped');
    assert.equal(expected.length, args[0]);
  }
  for (const args of [[0, 0], [33, 4], [8, 9], [8, -1], [8, 1.5], [8, 3, 9], [8, 3, 0, 1]]) {
    assert.throws(() => euclideanPatternMask(...args), RangeError);
  }
});

test('Euclidean chord generation uses shifted/flipped subdivisions and preserves velocity draws for rests', () => {
  const { service, load } = fixture();
  const { generateChordPart } = load('core/music/chord-generator.ts');
  const { chordRhythmMask } = load('core/music/rhythm-pattern.ts');
  const { JavaRandom } = load('core/music/java-random.ts');
  const seed = 9223372036854775807n;
  const base = service.project().chords;
  const settings = { ...base, rhythm: 'euclid', hitsPerPattern: 8, euclideanPulses: 3, patternShift: 1, noteLengthPercent: 75 };
  const mask = chordRhythmMask(settings);
  const notes = generateChordPart(seed, 'C', 'major', [1, 5], settings);
  assert.deepEqual(notes.map((note) => note.startBeat), [0, 4].flatMap((start) => mask.flatMap((value, slot) => value ? [start + slot / 2] : [])));
  assert.ok(notes.every((note) => note.durationBeats === 0.375));
  const random = new JavaRandom(BigInt.asIntN(32, BigInt.asIntN(32, seed) + 20000n));
  const velocities = Array.from({ length: 8 }, () => random.nextInt(21) + 69);
  assert.deepEqual(notes.filter((note) => note.chordIndex === 0).map((note) => note.velocity), velocities.filter((_, slot) => mask[slot]));
  const flipped = generateChordPart(seed, 'C', 'major', [1, 5], { ...settings, patternFlip: true, chordSpanFill: 'EVEN' });
  assert.deepEqual(flipped.map((note) => note.startBeat), mask.flatMap((value, slot) => value ? [] : [slot / 2]));
  assert.deepEqual(generateChordPart(seed, 'C', 'major', [1], { ...settings, euclideanPulses: 0 }), []);
  assert.equal(generateChordPart(seed, 'C', 'major', [1], { ...settings, euclideanPulses: 0, patternFlip: true }).length, 8);
  assert.deepEqual(chordRhythmMask({ ...settings, hitsPerPattern: 3, euclideanPulses: 32 }), [1, 1, 1]);
  assert.equal(generateChordPart(seed, 'C', 'major', [1], { ...settings, hitsPerPattern: 3, euclideanPulses: 32 }).length, 3);
  assert.deepEqual(generateChordPart(seed, 'C', 'major', [1], { ...base, euclideanPulses: 7 }),
    generateChordPart(seed, 'C', 'major', [1], base), 'Pulses has no effect on static rhythms');
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

test('the chord rhythm preview shares generation masks and shows mixed values only for active rhythm inputs', () => {
  const { service, load, settings } = fixture();
  const { PartSettingsEditorComponent } = load('shared/part-settings-editor.component.ts');
  const { chordRhythmMask } = load('core/music/rhythm-pattern.ts');
  const editor = new PartSettingsEditorComponent();
  editor.role.set('chords');
  const values = settings.settingsValues(service.project().chords, 'chords');
  editor.values.set(values);
  assert.deepEqual(editor.rhythmPreview(), chordRhythmMask(service.project().chords));
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
