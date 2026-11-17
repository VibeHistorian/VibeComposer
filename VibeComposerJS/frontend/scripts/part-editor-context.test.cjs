const assert = require('node:assert/strict');
const test = require('node:test');
const { fixture } = require('./support/project-fixture.cjs');

test('part editors resolve all four explicit scopes and retain member override provenance independently of selection', () => {
  const { service, load, ui } = fixture({ contextualMelody: false });
  const initial = service.project(), first = initial.tracks.find(track => track.role === 'chords');
  const sectionId = initial.arrangement[0].id, otherId = 'context-chords-2';
  service.state.set({ ...initial,
    tracks: [...initial.tracks.map(track => track.id === first.id
      ? { ...track, generatorSettings: { ...track.generatorSettings, transpose: 0 } } : track),
      { ...first, id: otherId, generatorSettings: { ...first.generatorSettings, transpose: 12 } }],
    arrangement: initial.arrangement.map(section => section.id === sectionId ? { ...section,
      rolePartOverrides: { ...section.rolePartOverrides, chords: { pauseChance: 20 } },
      trackPartOverrides: { ...section.trackPartOverrides, [first.id]: { transpose: 7 }, [otherId]: { pauseChance: 35 } } } : section),
  });
  const Editor = load('features/part-settings/part-settings-editor.component.ts').PartSettingsEditorComponent;
  const make = scope => { const editor = new Editor(); editor.scope.set(scope); return editor; };
  const globalTrack = make({ kind: 'global-track', trackId: first.id });
  const globalRole = make({ kind: 'global-role', role: 'chords' });
  const sectionTrack = make({ kind: 'section-track', sectionId, trackId: first.id });
  const sectionRole = make({ kind: 'section-role', sectionId, role: 'chords' });
  assert.equal(globalTrack.values().transpose, 0);
  assert.equal(globalRole.values().transpose, null);
  assert.equal(sectionTrack.values().transpose, 7);
  assert.equal(sectionTrack.values().pauseChance, 20);
  assert.deepEqual(sectionTrack.overriddenKeys(), ['transpose'], 'role patches remain inherited in an individual track editor');
  assert.equal(sectionRole.values().transpose, null);
  assert.equal(sectionRole.values().pauseChance, null);
  assert.deepEqual(new Set(sectionRole.overriddenKeys()), new Set(['transpose', 'pauseChance']));
  assert.equal(globalTrack.local(), false);
  assert.equal(sectionTrack.local(), true);
  ui.selectRole('bass');
  for (const editor of [globalTrack, globalRole, sectionTrack, sectionRole]) assert.equal(editor.role(), 'chords');
  service.updatePartSettings(sectionTrack.scope(), { transpose: 24 });
  assert.equal(sectionTrack.values().transpose, 24);
  assert.equal(globalTrack.values().transpose, 0);
  service.undo();
  assert.equal(sectionTrack.values().transpose, 7);
  service.redo();
  assert.equal(sectionTrack.values().transpose, 24);
});

test('quick and inspector reuse scope values while scope changes and project edits cancel stale text drafts', () => {
  const { service, load } = fixture({ contextualMelody: false });
  const Editor = load('features/part-settings/part-settings-editor.component.ts').PartSettingsEditorComponent;
  const scope = { kind: 'global-track', trackId: service.project().tracks.find(track => track.role === 'melody').id };
  const quick = new Editor(), inspector = new Editor();
  quick.scope.set(scope); inspector.scope.set({ ...scope });
  quick.advanced.set(false); inspector.inspectorOnly.set(true);
  assert.equal(quick.values(), inspector.values(), 'the same immutable project and scope share derived values');
  const target = load('core/music/part-settings.ts').PART_CONTROLS.melody.find(control => control.key === 'chordNoteChoices');
  const changes = []; inspector.settingsChanged.emit = change => changes.push(change);
  const input = { value: '0, 2', setCustomValidity() {}, reportValidity() {} };
  inspector.startTextEdit();
  inspector.scope.set({ kind: 'section-track', sectionId: service.project().arrangement[0].id, trackId: scope.trackId });
  inspector.commitText(target, { target: input });
  assert.deepEqual(changes, [], 'a draft must not commit into a different scope');
  inspector.startTextEdit();
  service.updatePartSettings(inspector.scope(), { speed: 100 });
  inspector.commitText(target, { target: input });
  assert.deepEqual(changes, [], 'a changed project invalidates the captured values');
});

test('melody hit counts follow explicit harmony scope and stay cached across mix-only updates', async () => {
  const reactivity = await import('@angular/core');
  const { service, load } = fixture({ contextualMelody: false, reactivity });
  const project = service.project(), trackId = project.tracks.find(track => track.role === 'bass').id;
  const sectionId = project.arrangement[0].id;
  service.state.set({ ...project, progression: [1, 4, 5, 1], arrangement: project.arrangement.map(section =>
    section.id === sectionId ? { ...section, chordDegrees: [1, 5], measures: 2 } : section) });
  const harmony = load('core/music/project-harmony.ts');
  const resolve = harmony.sectionGenerationHarmony;
  let resolutions = 0;
  harmony.sectionGenerationHarmony = (...args) => { resolutions++; return resolve(...args); };
  const Editor = load('features/part-settings/part-settings-editor.component.ts').PartSettingsEditorComponent;
  const editor = new Editor();
  editor.scope.set({ kind: 'global-track', trackId });
  assert.equal(editor.melodyHits(), 64);
  editor.scope.set({ kind: 'section-track', sectionId, trackId });
  assert.equal(editor.melodyHits(), 32);
  const count = resolutions;
  assert.equal(editor.melodyHits(), 32);
  service.updateTrack(trackId, { mix: { volumePercent: 75 } });
  assert.equal(editor.melodyHits(), 32);
  assert.equal(resolutions, count, 'mix edits must not resolve musical harmony again');
  service.updateSettings({ progression: [1, 5] });
  editor.scope.set({ kind: 'global-track', trackId });
  assert.equal(editor.melodyHits(), 32);
});
