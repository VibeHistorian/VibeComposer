const assert = require('node:assert/strict');
const test = require('node:test');
const { Midi } = require('@tonejs/midi');
const { fixture } = require('../support/project-fixture.cjs');

test('arrangement toggles persist, validate and undo; Preview rerolls preserve sections', () => {
  const { service } = fixture();
  const initial = service.exportProjectJson();
  service.updateSettings({ randomizeArrangementOnCompose: true, arrangementPreview: true, arrangementManual: true });
  const enabled = service.exportProjectJson();
  service.undo(); assert.equal(service.exportProjectJson(), initial);
  service.redo(); assert.equal(service.exportProjectJson(), enabled);
  assert.equal(service.importProjectJson(enabled), true);
  assert.equal(service.project().randomizeArrangementOnCompose, true);
  assert.equal(service.project().arrangementPreview, true);
  const restored = service.exportProjectJson();
  service.randomizeArrangementPresence();
  const preview = service.project();
  const structure = project => project.arrangement.map(({ trackPresence, ...section }) => section);
  assert.equal(JSON.stringify(structure(preview)), JSON.stringify(structure(JSON.parse(restored))));
  // A constrained presence reroll may legitimately reproduce the same orchestration.
  if (service.exportProjectJson() !== restored) {
    service.undo(); assert.equal(service.exportProjectJson(), restored);
    service.redo(); assert.deepEqual(service.project(), preview);
  }
  const { load } = fixture();
  const { decodeProject, isProject } = load('core/project/project-codec.ts');
  for (const field of ['randomizeArrangementOnCompose', 'arrangementPreview', 'arrangementManual']) {
    const malformed = { ...JSON.parse(enabled), [field]: 'true' };
    assert.equal(decodeProject(malformed, {}, Math.random), undefined);
    assert.equal(isProject(malformed, {}, Math.random), false);
  }
});

test('arrangement On Compose gates rerolls, excludes Regenerate and commits with Compose once', () => {
  const { service } = fixture();
  service.updateSettings({ seed: '42', arrangementPreview: true });
  const original = service.project().arrangement;
  service.generateComposition('compose');
  assert.deepEqual(service.project().arrangement, original, 'Preview alone does not reroll');
  service.updateSettings({ randomizeArrangementOnCompose: true, arrangementManual: true });
  service.generateComposition('regenerate');
  assert.deepEqual(service.project().arrangement, original, 'Regenerate does not reroll');
  const before = service.exportProjectJson();
  assert.equal(service.generateComposition('compose'), 'changed');
  assert.deepEqual(service.project().arrangement.map(section => [section.id, section.type, section.measures]),
    original.map(section => [section.id, section.type, section.measures]));
  const after = service.exportProjectJson();
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.redo(); assert.equal(service.exportProjectJson(), after);
  service.updateSettings({ arrangementPreview: false });
  service.generateComposition('compose');
  const rolled = service.project().arrangement;
  service.undo();
  service.generateComposition('compose');
  assert.deepEqual(service.project().arrangement, rolled, 'same seed reproduces presence');
});

test('automatic arrangement Compose replaces sections deterministically and restores them in one undo', () => {
  const { service } = fixture();
  assert.equal(service.project().arrangementManual ?? false, false);
  service.updateSettings({ seed: '9223372036854775807', randomizeArrangementOnCompose: true });
  const original = service.project().arrangement;
  const before = service.exportProjectJson();
  assert.equal(service.generateComposition('compose'), 'changed');
  const rolled = service.project().arrangement;
  assert.notDeepEqual(rolled, original);
  assert.ok(rolled.length > 1);
  for (const section of rolled) {
    assert.equal(section.measures, 4);
    assert.deepEqual(Object.keys(section.trackPresence).sort(), service.project().tracks.map(track => track.id).sort());
  }
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.generateComposition('compose');
  assert.deepEqual(service.project().arrangement, rolled, 'exact long seed reproduces structure and presence');
  service.updateSettings({ arrangementManual: true });
  const structure = project => project.arrangement.map(({ trackPresence, ...section }) => section);
  const manualStructure = structure(service.project());
  service.generateComposition('compose');
  assert.deepEqual(structure(service.project()), manualStructure);
  service.updateSettings({ randomizeArrangementOnCompose: false, arrangementManual: false });
  const unchanged = service.project().arrangement;
  service.generateComposition('compose');
  assert.deepEqual(service.project().arrangement, unchanged, 'On Compose off gates automatic structure');
});

test('Preview triggers after global Reroll and Compose even with restart disabled, but not local rerolls or failed generation', () => {
  const { service, workspace, load } = fixture();
  service.updateSettings({ arrangementPreview: true, seed: '42' });
  const { TransportDockComponent } = load('features/transport/transport-dock.component.ts');
  const transport = new TransportDockComponent();
  transport.preferences.restartPlaybackOnCompose = () => false;
  const previews = [];
  transport.playback.loopEnabled = () => true;
  workspace.playback.loopEnabled = () => true;
  const recordPreview = project => { previews.push(project); return project.arrangement[1].id; };
  transport.playback.previewArrangement = recordPreview;
  workspace.playback.previewArrangement = recordPreview;
  workspace.randomizeArrangement();
  assert.deepEqual(previews, [service.project()]);
  assert.deepEqual(workspace.workspaceUi.selectedSectionIds(), [service.project().arrangement[1].id]);
  service.randomizeArrangementPresence(service.project().arrangement[0].id);
  assert.equal(previews.length, 1);
  transport.generate('compose');
  assert.equal(previews.length, 2);
  assert.equal(previews[1], service.project(), 'seeks using the composed arrangement');
  assert.deepEqual(transport.workspaceUi.selectedSectionIds(), [service.project().arrangement[1].id]);
  transport.generate('regenerate');
  assert.equal(previews.length, 2);
  service.generateComposition = () => 'invalid';
  transport.generate('compose');
  assert.equal(previews.length, 2);
  service.updateSettings({ arrangementPreview: false });
  workspace.randomizeArrangement();
  assert.equal(previews.length, 2);
});

test('overview role presence clears mixed or full groups and fills empty groups in one undo step', () => {
  const { service } = fixture();
  service.duplicateTrack('track-bass-1');
  const tracks = service.project().tracks.filter(track => track.role === 'bass');
  assert.equal(tracks.length, 2);
  const sectionId = service.project().arrangement[0].id;
  for (const flags of [[true, false], [false, true], [true, true], [false, false]]) {
    tracks.forEach((track, index) => service.setSectionTrackPresence(0, track.id, flags[index]));
    const before = service.exportProjectJson();
    const section = service.project().arrangement[0];
    const otherSections = service.project().arrangement.slice(1);
    service.toggleSectionRolePresence(sectionId, 'bass');
    const updated = service.project().arrangement[0];
    for (const track of service.project().tracks) {
      assert.equal(updated.trackPresence[track.id], track.role === 'bass' ? !flags.some(Boolean) : section.trackPresence[track.id]);
    }
    assert.deepEqual(service.project().arrangement.slice(1), otherSections);
    const after = service.exportProjectJson();
    service.undo(); assert.equal(service.exportProjectJson(), before);
    service.redo(); assert.equal(service.exportProjectJson(), after);
  }
  const unchanged = service.project();
  service.toggleSectionRolePresence('missing-section', 'bass');
  assert.strictEqual(service.project(), unchanged);
});

test('compact history groups same-control bursts while keeping live state, persistence and final redo', (t) => {
  let now = 0;
  t.mock.method(performance, 'now', () => now);
  const { service, stored } = fixture({ compactHistory: true });
  const before = service.exportProjectJson();
  assert.equal(service.compactHistory(), true);
  assert.equal(service.canUndo(), false, 'preference does not create project history');
  for (const tempoBpm of [130, 131, 132]) {
    now += 200;
    service.updateSettings({ tempoBpm });
    assert.equal(service.project().tempoBpm, tempoBpm);
    assert.equal(JSON.parse(stored.get('vibecomposer.project.v12')).tempoBpm, tempoBpm);
  }
  const after = service.exportProjectJson();
  service.undo(); assert.equal(service.exportProjectJson(), before); assert.equal(service.canUndo(), false);
  service.redo(); assert.equal(service.exportProjectJson(), after);
  service.updateSettings({ tempoBpm: 133 });
  service.undo(); assert.equal(service.exportProjectJson(), after, 'redo ends a burst');
  service.updateSettings({ tempoBpm: 134 });
  assert.equal(service.canRedo(), false, 'new edits clear the redo branch');
  service.undo(); assert.equal(service.exportProjectJson(), after, 'undo ends a burst');
});

test('compact history separates fields, scopes, delayed edits, actions and multi-control patches', (t) => {
  let now = 0;
  t.mock.method(performance, 'now', () => now);
  const { service } = fixture({ compactHistory: true });
  const track = { kind: 'global-track', trackId: 'track-chords-1' };
  const snapshots = [service.exportProjectJson()];
  const step = (edit) => { edit(); snapshots.push(service.exportProjectJson()); };
  step(() => {
    service.updatePartSettings(track, { strum: 100 });
    now += 100; service.updatePartSettings(track, { strum: 200 });
  });
  step(() => service.updatePartSettings(track, { pauseChance: 10 }));
  step(() => service.updatePartSettings({ kind: 'global-role', role: 'chords' }, { pauseChance: 20 }));
  step(() => service.updatePartSettings({ kind: 'section-track', sectionId: service.project().arrangement[0].id,
    trackId: 'track-chords-1' }, { pauseChance: 30 }));
  step(() => service.updateSettings({ tempoBpm: 130 }));
  now += 1000;
  step(() => service.updateSettings({ tempoBpm: 140 }));
  step(() => service.addChord());
  step(() => service.updateSettings({ tempoBpm: 150 }));
  step(() => service.updateSettings({ tempoBpm: 160, transposeSemitones: 1 }));
  step(() => service.updateSettings({ tempoBpm: 170, transposeSemitones: 2 }));
  for (let i = snapshots.length - 2; i >= 0; i--) {
    service.undo(); assert.equal(service.exportProjectJson(), snapshots[i], 'separate undo step ' + i);
  }
  assert.equal(service.canUndo(), false);
  for (const snapshot of snapshots.slice(1)) { service.redo(); assert.equal(service.exportProjectJson(), snapshot); }
});

test('compact history covers mixer and policies and switching it off preserves individual edits', (t) => {
  t.mock.method(performance, 'now', () => 0);
  const { service } = fixture({ compactHistory: true });
  const before = service.exportProjectJson();
  service.updateTrack('track-bass-1', { mix: { volumePercent: 90 } });
  service.updateTrack('track-bass-1', { mix: { volumePercent: 80 } });
  const mixed = service.exportProjectJson();
  assert.equal(service.updateTrackGenerationPolicy('arpeggio', { maxSplit: 10 }), 'changed');
  assert.equal(service.updateTrackGenerationPolicy('arpeggio', { maxSplit: 20 }), 'changed');
  service.undo(); assert.equal(service.exportProjectJson(), mixed);
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.toggleCompactHistory();
  service.updateSettings({ tempoBpm: 130 });
  service.updateSettings({ tempoBpm: 140 });
  service.undo(); assert.equal(service.project().tempoBpm, 130);
  service.toggleCompactHistory();
  service.updateSettings({ tempoBpm: 150 });
  service.undo(); assert.equal(service.project().tempoBpm, 130, 'toggling starts a fresh burst');
});

test('Chordlet edits follow reorder/add/remove through history, persistence and pitched MIDI', () => {
  const { service, phrase, midi, load } = fixture();
  service.project().tracks.forEach(track => service.project().arrangement.forEach((_, index) => service.setSectionTrackPresence(index, track.id, true)));
  service.setProgression([1]);
  service.updatePartSettings({ kind: 'global-role', role: 'chords' }, { rhythm: 'single', voicing: 'close' });
  service.updatePartSettings({ kind: 'global-role', role: 'arpeggio' }, { pattern: 'up', stretchEnabled: false, octaves: 1 });
  service.updatePartSettings({ kind: 'global-role', role: 'bass' }, { rhythm: 'full', noteVariation: 0 });
  const chord = { root: 1, spice: '7', inversion: 1 };
  const before = service.exportProjectJson();
  service.setProgressionChord(0, chord);
  const after = service.exportProjectJson();
  const byRole = role => service.project().tracks.find(track => track.role === role);
  assert.deepEqual(phrase.phraseForTrack(service.project(), byRole('chords')).slice(0, 4).map(note => note.midi), [65,68,71,73]);
  assert.deepEqual(phrase.phraseForTrack(service.project(), byRole('arpeggio')).slice(0, 4).map(note => note.midi), [41,44,47,49]);
  assert.ok(phrase.phraseForTrack(service.project(), byRole('bass')).every(note => note.midi === 37));
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.redo(); assert.equal(service.exportProjectJson(), after);
  service.setSectionChordDegree(0, 0, 1);
  assert.ok(service.project().arrangement[0].chordDegrees, 'explicit local degree overrides a custom primary chord');
  assert.deepEqual(phrase.layOutTrackPhrase(service.project(), byRole('chords')).slice(0,3).map(note => note.midi), [60,64,67]);
  service.clearSectionChordOverrides(0);
  service.updateSettings({ transposeSemitones: 5, tempoBpm: 150 });
  const parsed = new Midi(midi.generateCompositionMidi(service.project()));
  assert.deepEqual(parsed.tracks.find(track => track.name === byRole('chords').name).notes.slice(0,4).map(note => note.midi).sort((a,b)=>a-b), [70,73,76,78]);
  const drums = phrase.layOutTrackPhrase(service.project(), byRole('drums'));
  service.updateSettings({ transposeSemitones: -5 });
  assert.deepEqual(phrase.layOutTrackPhrase(service.project(), byRole('drums')), drums);
  service.addChord(); service.addChord();
  service.setProgressionChord(2, { root: 7, spice: 'sus4', inversion: -2 });
  service.moveProgressionChord(0, 2);
  assert.equal(service.project().progressionChords[2].spice, '7');
  assert.equal(service.project().progressionChords[1].spice, 'sus4');
  service.removeChord(1); assert.equal(service.project().progressionChords.length, 2);
  const saved = service.exportProjectJson();
  assert.equal(service.importProjectJson(saved), true);
  assert.equal(new (load('core/project/project.service.ts').ProjectService)().project().progressionChords[1].root, 1);
  service.setChordDegree(1, 5); assert.equal(service.project().progressionChords[1], null);
  service.setProgression([1,6,4,5]); assert.equal(service.project().progressionChords, undefined);
});

test('history navigation preserves audition switches, redo branches and abbreviated diffs', () => {
  const { service } = fixture();
  const start = service.project().tempoBpm;
  const track = service.project().tracks[0];
  service.updateTrack(track.id, { mix: { muted: true } });
  assert.equal(service.canUndo(), false);
  service.updateSettings({ tempoBpm: 130 });
  service.updateSettings({ tempoBpm: 140 });
  assert.equal(service.historyEntries().length, 3);
  assert.ok(service.historyEntries()[2].changes.some(diff => diff.includes('Tempo Bpm: 130 → 140')));
  service.undo();
  service.toggleTrackMix([track.id], 'solo', true);
  assert.equal(service.canRedo(), true, 'audition does not discard redo');
  service.redo();
  assert.equal(service.project().tempoBpm, 140);
  assert.equal(service.project().tracks[0].mix.solo, true);
  assert.equal(service.project().tracks[0].mix.muted, false);
  service.goToHistory(0);
  assert.equal(service.project().tempoBpm, start);
  assert.equal(service.project().tracks[0].mix.solo, true);
  assert.equal(service.historyPosition(), 0);
  service.goToHistory(2);
  assert.equal(service.project().tempoBpm, 140);
  service.goToHistory(1);
  service.updateSettings({ tempoBpm: 150 });
  assert.equal(service.canRedo(), false);
  assert.equal(service.historyEntries().length, 3);
  service.goToHistory(-1); service.goToHistory(100); service.goToHistory(0.5);
  assert.equal(service.project().tempoBpm, 150);
  assert.ok(service.historyEntries().every(entry => entry.changes.every(diff => !/muted|solo/i.test(diff))));
  assert.equal(JSON.parse(service.exportProjectJson()).tracks[0].mix.solo, true);
});

test('mixed audition and volume patches undo only volume; compact history shows the final value', (t) => {
  t.mock.method(performance, 'now', () => 0);
  const { service } = fixture({ compactHistory: true });
  const track = service.project().tracks[0];
  const volume = track.mix.volumePercent;
  service.updateTrack(track.id, { mix: { muted: true, volumePercent: 42 } });
  service.undo();
  assert.equal(service.project().tracks[0].mix.volumePercent, volume);
  assert.equal(service.project().tracks[0].mix.muted, true);
  service.redo();
  assert.equal(service.project().tracks[0].mix.volumePercent, 42);
  service.updateSettings({ tempoBpm: 130 });
  service.toggleTrackMix([track.id], 'solo');
  service.updateSettings({ tempoBpm: 140 });
  assert.equal(service.historyEntries().length, 3);
  assert.ok(service.historyEntries()[2].changes.some(diff => diff.endsWith('→ 140')));
  service.goToHistory(0);
  assert.equal(service.project().tracks[0].mix.solo, true);
});

test('mute/solo controls clear the opposite state across track mix updates', () => {
  const { service } = fixture();
  const track = service.project().tracks[0];
  service.updateTrack(track.id, { mix: { muted: true } });
  service.updateTrack(track.id, { mix: { solo: true } });
  assert.equal(service.project().tracks[0].mix.muted, false);
  service.updateTrack(track.id, { mix: { muted: true } });
  assert.equal(service.project().tracks[0].mix.solo, false);
});

test('role/global mute and solo clear partial states without entering undo history', () => {
  const { service } = fixture();
  service.addTrack('bass');
  const ids = service.project().tracks.filter(track => track.role === 'bass').map(track => track.id);
  service.updateTrack(ids[0], { mix: { muted: true } });
  service.toggleTrackMix(ids, 'muted');
  assert.ok(service.project().tracks.filter(track => ids.includes(track.id)).every(track => !track.mix.muted));
  const position = service.historyPosition();
  service.toggleTrackMix(ids, 'solo');
  assert.ok(service.project().tracks.filter(track => ids.includes(track.id)).every(track => track.mix.solo && !track.mix.muted));
  assert.equal(service.historyPosition(), position);
  const all = service.project().tracks.map(track => track.id);
  service.toggleTrackMix(all, 'solo');
  assert.ok(service.project().tracks.every(track => !track.mix.solo));
  service.toggleTrackMix(all, 'muted');
  assert.ok(service.project().tracks.every(track => track.mix.muted && !track.mix.solo));
  service.toggleTrackMix(all, 'solo');
  assert.ok(service.project().tracks.every(track => track.mix.solo && !track.mix.muted));
  const unchanged = service.project();
  service.toggleTrackMix([], 'solo');
  assert.strictEqual(service.project(), unchanged);
});

test('middle-click solos only the selected track/group, unmutes it, and ignores other buttons', () => {
  const { service, load } = fixture();
  service.addTrack('bass');
  const bass = service.project().tracks.filter(track => track.role === 'bass').map(track => track.id);
  service.toggleTrackMix(service.project().tracks.map(track => track.id), 'muted');
  const { MixButtonsComponent } = load('features/tracks/mix-buttons.component.ts');
  const buttons = new MixButtonsComponent();
  buttons.trackIds.set(bass); buttons.label.set('Bass tracks');
  let updates = 0, prevented = 0, stopped = 0;
  buttons.playback = { updateMix: () => updates++ };
  const click = button => ({ button, preventDefault: () => prevented++, stopPropagation: () => stopped++ });
  const before = service.exportProjectJson();
  buttons.exclusiveSolo(click(2)); assert.equal(service.exportProjectJson(), before);
  buttons.exclusiveSolo(click(1));
  assert.ok(service.project().tracks.every(track => track.mix.solo === bass.includes(track.id)));
  assert.ok(service.project().tracks.filter(track => bass.includes(track.id)).every(track => !track.mix.muted));
  assert.equal(buttons.state('solo'), 'true');
  buttons.trackIds.set([bass[0]]); buttons.exclusiveSolo(click(1));
  assert.deepEqual(service.project().tracks.filter(track => track.mix.solo).map(track => track.id), [bass[0]]);
  buttons.trackIds.set(bass); assert.equal(buttons.state('solo'), 'mixed');
  assert.equal(updates, 2); assert.equal(prevented, 2); assert.equal(stopped, 2);
  buttons.allowExclusive.set(false);
  const unchanged = service.project(); buttons.exclusiveSolo(click(1));
  assert.strictEqual(service.project(), unchanged);
});

test('track movement stays within its role and ignores unavailable moves without consuming history', () => {
  const { service, workspace } = fixture();
  const original = service.project();
  for (const track of original.tracks) {
    for (const offset of [-1, 1]) {
      assert.equal(workspace.canMoveTrack(track.id, offset), false);
      service.reorderTrack(track.id, offset);
      assert.strictEqual(service.project(), original);
    }
  }
  for (const offset of [-1, 1]) assert.equal(workspace.canMoveTrack('missing', offset), false);

  // New tracks are appended, so other roles separate these two Bass tracks.
  const first = 'track-bass-1', second = service.addTrack('bass');
  const before = service.exportProjectJson(), position = service.historyPosition();
  const otherTracks = service.project().tracks.filter(track => track.role !== 'bass');
  assert.equal(workspace.canMoveTrack(first, -1), false);
  assert.equal(workspace.canMoveTrack(first, 1), true);
  assert.equal(workspace.canMoveTrack(second, -1), true);
  assert.equal(workspace.canMoveTrack(second, 1), false);
  service.reorderTrack(second, -1);
  assert.deepEqual(service.project().tracks.filter(track => track.role === 'bass').map(track => track.id), [second, first]);
  assert.deepEqual(service.project().tracks.filter(track => track.role !== 'bass'), otherTracks);
  assert.equal(service.historyPosition(), position + 1);
  assert.equal(workspace.canMoveTrack(second, -1), false);
  assert.equal(workspace.canMoveTrack(first, 1), false);
  const after = service.exportProjectJson();
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.reorderTrack(first, -1);
  service.reorderTrack(second, 1);
  service.reorderTrack('missing', 1);
  assert.equal(service.exportProjectJson(), before);
  assert.equal(service.canRedo(), true);
  service.redo(); assert.equal(service.exportProjectJson(), after);
  service.reorderTrack(second, 1);
  assert.equal(service.exportProjectJson(), before);
});

test('role totals shrink highest numbers first, preserve locks and references, and undo atomically', () => {
  const { service } = fixture();
  service.setRoleTrackCount('chords', 5);
  service.setTrackRerollLock('track-chords-4', true);
  const section = service.project().arrangement[0];
  service.updatePartSettings({ kind: 'section-track', sectionId: section.id, trackId: 'track-chords-5' }, { transpose: 12 });
  service.reorderTrack('track-chords-5', -1);
  const before = service.exportProjectJson();
  const locked = service.project().tracks.find(track => track.id === 'track-chords-4');
  assert.equal(service.setRoleTrackCount('chords', 3), 'changed');
  assert.deepEqual(service.project().tracks.filter(track => track.role === 'chords').map(track => track.id),
    ['track-chords-1', 'track-chords-2', 'track-chords-4']);
  for (const section of service.project().arrangement) {
    assert.equal('track-chords-5' in section.trackPresence, false);
    assert.equal('track-chords-5' in (section.trackPartOverrides ?? {}), false);
  }
  service.undo(); assert.equal(service.exportProjectJson(), before);
  assert.equal(service.canRedo(), true);
  assert.equal(service.setRoleTrackCount('chords', 5), 'unchanged');
  assert.equal(service.canRedo(), true);
  service.redo();
  assert.equal(service.setRoleTrackCount('chords', 0), 'changed');
  assert.deepEqual(service.project().tracks.filter(track => track.role === 'chords'), [locked]);
  const unchanged = service.exportProjectJson();
  assert.equal(service.setRoleTrackCount('chords', 0), 'unchanged');
  assert.equal(service.exportProjectJson(), unchanged);
  service.setRoleTrackCount('chords', 2);
  assert.equal(service.project().tracks.at(-1).id, 'track-chords-5');
});

test('role totals preserve the final track and enforce capacity without consuming redo', () => {
  const { service, load } = fixture();
  for (const role of ['melody', 'bass', 'chords', 'arpeggio']) assert.equal(service.setRoleTrackCount(role, 0), 'changed');
  const before = service.exportProjectJson();
  assert.equal(service.setRoleTrackCount('drums', 0), 'unchanged');
  assert.equal(service.exportProjectJson(), before);
  assert.equal(service.project().tracks.length, 1);
  assert.equal(service.importProjectJson(before), true);
  const restored = new (load('core/project/project.service.ts').ProjectService)();
  assert.equal(restored.project().tracks.length, 1);
  assert.equal(service.setRoleTrackCount('drums', 64), 'changed');
  assert.equal(service.project().tracks.length, 64);
  assert.equal(service.setRoleTrackCount('bass', 1), 'invalid');
  service.undo(); assert.equal(service.project().tracks.length, 1);
  for (const count of [-1, 65, 1.5, NaN]) assert.equal(service.setRoleTrackCount('drums', count), 'invalid');
  assert.equal(service.setRoleTrackCount('drums', 0), 'unchanged');
  assert.equal(service.canRedo(), true);
});

test('the only populated role clamps to one and can be cleared after another role gains tracks', () => {
  for (const role of ['melody', 'bass', 'chords', 'arpeggio', 'drums']) {
    const { service } = fixture();
    for (const other of ['melody', 'bass', 'chords', 'arpeggio', 'drums']) {
      if (other !== role) service.setRoleTrackCount(other, 0);
    }
    service.setRoleTrackCount(role, 3);
    const before = service.exportProjectJson();
    assert.equal(service.setRoleTrackCount(role, 0), 'changed');
    assert.equal(service.project().tracks.length, 1);
    assert.equal(service.project().tracks[0].role, role);
    service.undo(); assert.equal(service.exportProjectJson(), before);
    service.redo();
    assert.equal(service.setRoleTrackCount(role, 0), 'unchanged');
    const other = role === 'bass' ? 'chords' : 'bass';
    assert.equal(service.setRoleTrackCount(other, 1), 'changed');
    assert.equal(service.setRoleTrackCount(role, 0), 'changed');
    assert.equal(service.project().tracks.length, 1);
    assert.equal(service.project().tracks[0].role, other);
  }
});

test('generated tracks append atomically with one history entry for every role', () => {
  for (const role of ['melody', 'bass', 'chords', 'arpeggio', 'drums']) {
    const { service, load } = fixture();
    service.updateTrackPhrase(`track-${role}-1`, [{ id:'manual', midi:60, startBeat:0, durationBeats:1, velocity:80 }]);
    const before = service.exportProjectJson(), existing = service.project().tracks;
    assert.equal(service.generateRoleTracks(role, 3), 'changed');
    const created = service.project().tracks.slice(existing.length);
    assert.equal(created.length, 3); assert.equal(service.project().trackRerollCounts[role], 1);
    assert.deepEqual(service.project().tracks.slice(0, existing.length), existing);
    assert.ok(created.every(track => track.role === role && !track.editedPhrase && track.rerollLocked === false));
    assert.equal(new Set(service.project().tracks.map(track => track.id)).size, service.project().tracks.length);
    if (role === 'drums') assert.ok(created.every(track => track.midiChannel === 10));
    const after = service.exportProjectJson(); service.undo(); assert.equal(service.exportProjectJson(), before);
    service.redo(); assert.equal(service.exportProjectJson(), after);
    service.undo(); service.generateRoleTracks(role, 3); assert.equal(service.exportProjectJson(), after);
    assert.equal(service.importProjectJson(after), true);
    const restored = new (load('core/project/project.service.ts').ProjectService)();
    assert.deepEqual(restored.project(), service.project());
    service.updateSettings({ name: 'redo' }); service.undo(); const state = service.exportProjectJson();
    for (const count of [0, 65, 1.5, NaN]) assert.equal(service.generateRoleTracks(role, count), 'invalid');
    assert.equal(service.exportProjectJson(), state); assert.equal(service.canRedo(), true);
  }
});

test('generation handles empty roles, project capacity and no-op rerolls without consuming history', () => {
  const { service, load } = fixture();
  service.updateInstrumentPolicy('chords', { randomize: false });
  service.removeTrack('track-melody-1');
  assert.equal(service.generateRoleTracks('melody', 2), 'changed');
  assert.ok(service.project().tracks.some(track => track.id === 'track-melody-1'));
  service.updateTrackGenerationPolicy('chords', { fills: false, patterns: false, transpose: false, varyLength: false, delay: false,
    voicesMode: 'NONE', strum: false, maxSplit: 0, split: false, expandChance: 0 });
  service.rerollRoleTracks('chords'); service.updateSettings({ name: 'redo' }); service.undo();
  const before = service.exportProjectJson();
  assert.equal(service.rerollRoleTracks('chords'), 'unchanged');
  assert.equal(service.exportProjectJson(), before); assert.equal(service.canRedo(), true);
  const core = load('core/music/track-generation.ts');
  const source = service.project().tracks.find(track => track.role === 'bass');
  for (let seed = 0; seed < 1000; seed++) {
    const [settings] = core.rerollRoleSettings(BigInt(seed), 'bass', {}, [source], [1]);
    assert.ok(settings.velocityMin <= settings.velocityMax, 'Java independently chooses crossing Bass bounds; normalize safely without redrawing');
  }
  const project = JSON.parse(before), original = project.tracks[0];
  project.tracks = [...project.tracks, ...Array.from({ length: 63 - project.tracks.length }, (_, index) => ({
    ...original, id: 'capacity-' + index, name: 'Capacity ' + index,
  }))];
  for (const section of project.arrangement) section.trackPresence[original.role] = project.tracks
    .filter(track => track.role === original.role).map((_, index) => section.trackPresence[original.role][index] ?? false);
  assert.equal(service.importProjectJson(JSON.stringify(project)), true);
  service.updateSettings({ name: 'redo-capacity' }); service.undo(); const full = service.exportProjectJson();
  assert.equal(service.generateRoleTracks('drums', 2), 'invalid'); assert.equal(service.exportProjectJson(), full);
  assert.equal(service.canRedo(), true); assert.equal(service.generateRoleTracks('drums', 1), 'changed');
  assert.equal(service.project().tracks.length, 64);
});

test('knobs drag vertically, commit once, cancel safely, and scroll with stepped/fine bounds', () => {
  const { load } = fixture();
  const { CompactKnobComponent } = load('shared/compact-knob.component.ts');
  const knob = new CompactKnobComponent();
  knob.value.set(50); knob.minimum.set(0); knob.maximum.set(100); knob.label.set('Length');
  const commits = [], previews = [];
  knob.valueCommit.emit = value => commits.push(value);
  knob.valuePreview.emit = value => previews.push(value);
  const control = { focus() {}, setPointerCapture() {} };
  const event = (y, extra = {}) => ({ pointerId: 1, button: 0, clientY: y, clientX: 0,
    currentTarget: control, preventDefault() {}, stopPropagation() {}, shiftKey: false, ...extra });
  knob.startDrag(event(100));
  knob.moveDrag(event(100, { clientX: 200 }));
  assert.equal(knob.shownValue(), 50, 'horizontal movement cannot change the knob');
  knob.moveDrag(event(25));
  assert.equal(knob.shownValue(), 100);
  assert.deepEqual(commits, []);
  knob.finishDrag(event(25));
  assert.deepEqual(commits, [100]);
  knob.startDrag(event(100)); knob.moveDrag(event(175)); knob.cancelDrag();
  assert.equal(knob.shownValue(), 50);
  assert.equal(previews.at(-1), 50, 'cancel restores audio preview');
  knob.finishDrag(event(175));
  assert.deepEqual(commits, [100]);
  knob.startDrag(event(100)); knob.moveDrag(event(25)); knob.value.set(60); knob.finishDrag(event(25));
  assert.deepEqual(commits, [100], 'external changes cancel pending drag');
  knob.onWheel(event(0, { deltaY: -1 }));
  assert.equal(commits.at(-1), 65);
  knob.onWheel(event(0, { deltaY: 1, shiftKey: true }));
  assert.equal(commits.at(-1), 59);
  const count = commits.length;
  knob.onWheel(event(0, { deltaY: 1, ctrlKey: true }));
  assert.equal(commits.length, count, 'browser zoom stays available');
  knob.minimum.set(-36); knob.maximum.set(36); knob.step.set(12); knob.value.set(0);
  knob.onWheel(event(0, { deltaY: -1 }));
  assert.equal(commits.at(-1), 12, 'transpose respects octave steps');
  knob.value.set(36); knob.onWheel(event(0, { deltaY: -1 }));
  assert.equal(commits.at(-1), 12, 'bound does not create another history entry');
});

test('wheel selects remember values without DOM writes or synthetic change events, wrap, and preserve zoom', () => {
  const { load } = fixture();
  const { SelectComponent } = load('shared/select.component.ts');
  const component = new SelectComponent(), changes = [];
  const select = { disabled: false,
    set selectedIndex(value) { assert.fail('wheel must not set selectedIndex'); },
    set value(value) { assert.fail('wheel must not set DOM value'); },
    dispatchEvent() { assert.fail('wheel must not dispatch a synthetic DOM event'); } };
  component.element = { nativeElement: select };
  component.options.set([{ value: '', label: 'Mixed', disabled: true }, 'A', 'B']);
  component.value.set('A');
  component.valueChange.emit = value => changes.push(value);
  const event = (deltaY, extra = {}) => ({ deltaY, preventDefault() {}, stopPropagation() {}, ...extra });
  component.onWheel(event(1)); assert.equal(component.selectedValue(), 'B');
  component.onWheel(event(1)); assert.equal(component.selectedValue(), 'A');
  component.onWheel(event(-1)); assert.equal(component.selectedValue(), 'B');
  assert.deepEqual(changes, ['B', 'A', 'B']);
  component.onWheel(event(1, { ctrlKey: true }));
  component.onWheel(event(1, { metaKey: true }));
  component.onWheel(event(0));
  component.combobox.disabled.set(true); component.onWheel(event(1));
  assert.equal(changes.length, 3);
  component.value.set('A'); // Same bound value does not overwrite a remembered gesture.
  assert.equal(component.selectedValue(), 'B');
  component.value.set('B'); component.value.set('A');
  component.restoreValue(); assert.equal(component.selectedValue(), 'A');
});

test('combobox selection commits a remembered draft once and restores one-shot values without DOM writes', () => {
  const { load } = fixture();
  const { SelectComponent } = load('shared/select.component.ts');
  const component = new SelectComponent();
  const select = { disabled: false };
  component.element = { nativeElement: select };
  component.options.set(['', 'A', 'B']); component.value.set('');
  const changes = []; component.valueChange.emit = value => changes.push(value);
  component.combobox.expanded.set(true);
  component.draft.set(['B']);
  assert.deepEqual(changes, [], 'browsing is not a project command');
  component.commit({ type: 'keydown' });
  assert.equal(component.selectedValue(), 'B'); assert.deepEqual(changes, ['B']);
  assert.equal(component.combobox.expanded(), false);
  component.restoreValue(); assert.equal(component.selectedValue(), '');
  assert.deepEqual(changes, ['B'], 'restoring an action placeholder emits no command');
});

test('quick mixer knobs format linear gain and pan, audition without history, and commit one track only', () => {
  const { load, service, workspace } = fixture();
  const { panLabel, volumeDecibels } = load('core/audio/mix-values.ts');
  assert.deepEqual([-100, -50, 0, 50, 100].map(panLabel), ['100% L', '50% L', 'C', '50% R', '100% R']);
  assert.deepEqual([100, 50, 1, 0].map(volumeDecibels), ['-0.0', '-6.0', '-40.0', '-Inf']);
  const heard = [];
  workspace.playback.updateMix = project => heard.push(project);
  const before = service.exportProjectJson();
  workspace.previewTrackMix('track-bass-1', 'panPercent', -50);
  assert.equal(service.exportProjectJson(), before);
  assert.equal(heard.at(-1).tracks.find(track => track.id === 'track-bass-1').mix.panPercent, -50);
  workspace.updateTrackMix('track-bass-1', 'panPercent', -50);
  assert.equal(service.project().tracks.find(track => track.id === 'track-bass-1').mix.panPercent, -50);
  assert.equal(service.project().tracks.find(track => track.id === 'track-chords-1').mix.panPercent, 0);
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.redo();
  workspace.updateTrackMix('track-bass-1', 'volumePercent', 50);
  assert.equal(service.project().tracks.find(track => track.role === 'bass').mix.volumePercent, 50);
  assert.equal(heard.at(-1).tracks.find(track => track.id === 'track-bass-1').mix.volumePercent, 50);
});

test('track gain supports +6 dB, fractional attenuation, history and project round trips', () => {
  const { load, service, workspace } = fixture();
  workspace.playback.updateMix = () => {};
  const { MAX_VOLUME_PERCENT, volumeDecibels } = load('core/audio/mix-values.ts');
  assert.equal(volumeDecibels(MAX_VOLUME_PERCENT), '+6.0');
  assert.equal(volumeDecibels(100 * 10 ** (-0.1 / 20)), '-0.1');
  workspace.updateTrackMix('track-bass-1', 'volumePercent', MAX_VOLUME_PERCENT);
  const saved = service.exportProjectJson();
  assert.equal(service.project().tracks.find(track => track.id === 'track-bass-1').mix.volumePercent, MAX_VOLUME_PERCENT);
  service.undo();
  assert.equal(service.project().tracks.find(track => track.role === 'bass').mix.volumePercent, 100);
  service.redo();
  assert.equal(service.exportProjectJson(), saved);
  assert.equal(service.importProjectJson(saved), true);
  const imported = service.exportProjectJson();
  for (const value of [MAX_VOLUME_PERCENT + 0.01, -0.1, NaN, Infinity]) {
    workspace.updateTrackMix('track-bass-1', 'volumePercent', value);
    assert.equal(service.exportProjectJson(), imported);
  }
});

test('volume rounds displayed zero to unity and scrolls by exact 1% or 10% steps', () => {
  const { load, service } = fixture();
  const { MAX_VOLUME_PERCENT, normalizeVolumePercent, volumeDecibels } = load('core/audio/mix-values.ts');
  const { CompactKnobComponent } = load('shared/compact-knob.component.ts');
  const knob = new CompactKnobComponent();
  knob.mini.set(true); knob.minimum.set(0); knob.maximum.set(MAX_VOLUME_PERCENT);
  knob.step.set(0.1); knob.valueFormat.set('decibels');
  const commits = [], previews = [];
  knob.valueCommit.emit = value => commits.push(value);
  knob.valuePreview.emit = value => previews.push(value);
  for (const value of [99.5, 100, 100.5]) {
    assert.equal(normalizeVolumePercent(value), 100);
    assert.equal(volumeDecibels(value), '-0.0');
    knob.value.set(value);
    assert.equal(knob.shownValue(), 100);
    assert.match(knob.mixAccent(), / 0%,/);
    knob.previewValue({ target: { value: String(value) } });
    assert.equal(previews.at(-1), 100);
    knob.commitValue({ target: { value: String(value) } });
    assert.equal(commits.at(-1), 100);
    service.updateTrack('track-bass-1', { mix: { volumePercent: value } });
    assert.equal(service.project().tracks.find(track => track.role === 'bass').mix.volumePercent, 100);
  }
  assert.equal(volumeDecibels(99.4), '-0.1');
  assert.equal(volumeDecibels(100.6), '+0.1');
  const wheel = (deltaY, shiftKey = false) => ({ deltaY, shiftKey, preventDefault() {}, stopPropagation() {} });
  for (const start of [50, 100, 150]) {
    knob.value.set(start);
    for (const shift of [false, true]) for (const direction of [-1, 1]) {
      knob.onWheel(wheel(-direction, shift));
      assert.equal(commits.at(-1), start + direction * (shift ? 1 : 10));
    }
  }
  knob.value.set(195); knob.onWheel(wheel(-1)); assert.equal(commits.at(-1), MAX_VOLUME_PERCENT);
  knob.value.set(5); knob.onWheel(wheel(1)); assert.equal(commits.at(-1), 0);
});

test('mixer accents mute at defaults, jump for tiny edits, reach both endpoints and invert only boosts', () => {
  const { load } = fixture();
  const { MAX_VOLUME_PERCENT } = load('core/audio/mix-values.ts');
  const { CompactKnobComponent } = load('shared/compact-knob.component.ts');
  const knob = new CompactKnobComponent();
  knob.mini.set(true); knob.minimum.set(-100); knob.maximum.set(100); knob.valueFormat.set('pan');
  knob.value.set(0);
  assert.match(knob.mixAccent(), / 0%,/);
  for (const value of [-100, 100]) { knob.value.set(value); assert.match(knob.mixAccent(), / 100%,/); }
  knob.value.set(1); assert.match(knob.mixAccent(), / 32.5%,/);
  knob.minimum.set(0); knob.maximum.set(MAX_VOLUME_PERCENT); knob.valueFormat.set('decibels');
  knob.value.set(100); assert.match(knob.mixAccent(), / 0%,/);
  knob.value.set(100 * 10 ** (-0.1 / 20));
  assert.ok(parseFloat(knob.mixAccent().match(/ ([\d.]+)%,/)[1]) > 25);
  assert.ok(!knob.mixAccent().includes('255 -'));
  knob.value.set(0); assert.match(knob.mixAccent(), / 100%,/);
  knob.value.set(MAX_VOLUME_PERCENT);
  assert.match(knob.mixAccent(), /255 - r/); assert.match(knob.mixAccent(), / 100%,/);
  knob.preview.set(100); assert.match(knob.mixAccent(), / 0%,/);
  knob.mini.set(false); assert.equal(knob.mixAccent(), null);
});

test('popup velocity previews commit once, preserve hidden cells and cancel stale gestures', () => {
  const { service, load } = fixture();
  const { PatternSequencerComponent } = load('features/part-settings/pattern-sequencer.component.ts');
  const editor = new PatternSequencerComponent();
  const before = service.exportProjectJson();
  const input = { value: '110' }, event = { target: input };
  editor.previewVelocity('track-chords-1', 1, event);
  assert.equal(editor.rows()[0].velocities[1], 110);
  assert.equal(service.exportProjectJson(), before);
  editor.commitVelocity('track-chords-1', 1, event);
  assert.equal(service.project().tracks.find(track => track.role === 'chords').generatorSettings.customVelocities[1], 110);
  assert.equal(service.project().tracks.find(track => track.role === 'chords').generatorSettings.customVelocities.length, 32);
  service.undo(); assert.equal(service.exportProjectJson(), before);
  editor.previewVelocity('track-chords-1', 2, event); editor.cancel();
  assert.equal(editor.rows()[0].velocities[2], 79);
  editor.previewVelocity('track-chords-1', 2, event);
  service.updateSettings({ tempoBpm: 111 });
  const changed = service.exportProjectJson();
  editor.commitVelocity('track-chords-1', 2, event);
  assert.equal(service.exportProjectJson(), changed);
  editor.wheelVelocity('track-chords-1', 3, { deltaY: -1, shiftKey: true, preventDefault() {}, stopPropagation() {} });
  assert.equal(service.project().tracks.find(track => track.role === 'chords').generatorSettings.customVelocities[3], 80);
});

test('popup painting commits one gesture in audible coordinates and rejects stale project state', () => {
  const { service, load } = fixture();
  const { PatternSequencerComponent } = load('features/part-settings/pattern-sequencer.component.ts');
  service.updatePartSettings({ kind: 'global-track', trackId: 'track-chords-1' }, { rhythm: 'custom', patternShift: 3, patternFlip: true });
  const editor = new PatternSequencerComponent();
  const before = service.exportProjectJson();
  const button = { focus() {}, setPointerCapture() {} };
  editor.start({ button: 0, pointerId: 9, target: button, currentTarget: button, preventDefault() {} }, 'track-chords-1', 0);
  assert.equal(editor.rows()[0].pattern[0], 1);
  const slot = { dataset: { step: '2' } };
  const grid = { ownerDocument: { elementFromPoint: () => ({ closest: () => slot }) }, contains: () => true };
  editor.move({ pointerId: 9, currentTarget: grid, clientX: 1, clientY: 1 });
  assert.equal(service.exportProjectJson(), before);
  editor.finish({ pointerId: 9 });
  assert.deepEqual(service.project().tracks.find(track => track.role === 'chords').generatorSettings.customPattern.slice(29), [0, 0, 0]);
  service.undo(); assert.equal(service.exportProjectJson(), before);
  editor.start({ button: 0, pointerId: 10, target: button, currentTarget: button, preventDefault() {} }, 'track-chords-1', 0);
  editor.cancel(); editor.finish({ pointerId: 10 });
  assert.equal(service.exportProjectJson(), before);
  editor.start({ button: 0, pointerId: 11, target: button, currentTarget: button, preventDefault() {} }, 'track-chords-1', 0);
  service.updateSettings({ tempoBpm: 111 });
  const changed = service.exportProjectJson();
  editor.finish({ pointerId: 11 }); assert.equal(service.exportProjectJson(), changed);
});

test('overview reset restores globals and generated notes locally, preserves siblings, and is one undo entry', () => {
  const { service, settings, phrase, load } = fixture();
  const trackId = 'track-bass-1', siblingId = service.duplicateTrack(trackId);
  const sectionId = service.project().arrangement[0].id;
  service.project().arrangement.forEach((_, index) => service.setSectionTrackPresence(index, trackId, true));
  const manual = [{ id: 'manual', midi: 1, startBeat: 0, durationBeats: 1, velocity: 90 }];
  service.updateTrackPhrase(trackId, manual);
  service.updateSectionTrackPhrase(sectionId, trackId, manual);
  service.updatePartSettings({ kind: 'section-role', sectionId, role: 'bass' }, { transpose: 12 });
  const before = service.exportProjectJson();
  const position = service.historyPosition();
  const scope = { kind: 'section-track', sectionId, trackId };
  assert.equal(service.changeOverviewTracks(scope, 'reset'), 'changed');
  assert.equal(service.historyPosition(), position + 1);
  const project = service.project(), section = project.arrangement[0];
  const track = project.tracks.find(t => t.id === trackId);
  assert.deepEqual(track.editedPhrase, manual);
  assert.equal(section.trackPhrases[trackId], undefined);
  assert.deepEqual(section.generatedTrackIds, [trackId]);
  assert.deepEqual(settings.resolvePartTrack(track, section).generatorSettings, track.generatorSettings);
  assert.equal(settings.resolvePartTrack(project.tracks.find(t => t.id === siblingId), section).generatorSettings.transpose, 12);
  const notes = phrase.layOutTrackPhrase(project, track);
  assert.ok(notes.some(n => n.startBeat < section.measures * 4 && n.midi !== 1));
  assert.ok(notes.some(n => n.startBeat >= section.measures * 4 && n.midi === 1));
  const after = service.exportProjectJson();
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.redo(); assert.equal(service.exportProjectJson(), after);
  assert.equal(service.changeOverviewTracks(scope, 'reset'), 'unchanged');
  assert.equal(service.exportProjectJson(), after);
  const { ProjectService } = load('core/project/project.service.ts');
  const restored = new ProjectService();
  assert.equal(restored.importProjectJson(after), true);
  assert.deepEqual(restored.project().arrangement[0].generatedTrackIds, [trackId]);
});

test('overview cell and section rerolls and resets stay local and undo atomically', () => {
  for (const kind of ['section-track', 'section-role', 'section']) {
    const { service } = fixture();
    const sectionId = service.project().arrangement[0].id;
    const scope = { kind, sectionId, ...(kind === 'section-track' ? { trackId: 'track-bass-1' } : kind === 'section-role' ? { role: 'bass' } : {}) };
    const before = service.exportProjectJson(), globals = JSON.stringify(service.project().tracks);
    const other = JSON.stringify(service.project().arrangement.slice(1));
    assert.equal(service.changeOverviewTracks(scope, 'reroll'), 'changed');
    const after = service.exportProjectJson();
    assert.equal(JSON.stringify(service.project().tracks), globals);
    assert.equal(JSON.stringify(service.project().arrangement.slice(1)), other);
    assert.ok(Object.keys(service.project().arrangement[0].trackPartOverrides).length);
    service.undo(); assert.equal(service.exportProjectJson(), before);
    service.redo(); assert.equal(service.exportProjectJson(), after);
    assert.equal(service.changeOverviewTracks(scope, 'reset'), 'changed');
    service.undo(); assert.equal(service.exportProjectJson(), after);
  }
});

test('overview header shortcuts edit the clicked section and undo duplication/removal individually', () => {
  const { service, workspace } = fixture();
  const clicked = service.project().arrangement[1].id;
  const before = service.exportProjectJson();
  workspace.duplicateSection(clicked);
  const duplicated = service.exportProjectJson();
  assert.equal(service.project().arrangement[2].type, service.project().arrangement[1].type);
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.redo(); assert.equal(service.exportProjectJson(), duplicated);
  workspace.removeSection(clicked);
  assert.ok(!service.project().arrangement.some(section => section.id === clicked));
  const removed = service.exportProjectJson();
  service.undo(); assert.equal(service.exportProjectJson(), duplicated);
  service.redo(); assert.equal(service.exportProjectJson(), removed);
});

test('overview section reset clears every role and manual phrase; rerolls preserve locks and manual notes', () => {
  const { service, workspace, ui } = fixture();
  const sectionId = service.project().arrangement[0].id;
  const manual = [{ id: 'manual', midi: 60, startBeat: 0, durationBeats: 1, velocity: 90 }];
  for (const track of service.project().tracks) {
    service.updateTrackPhrase(track.id, manual);
    service.updateSectionTrackPhrase(sectionId, track.id, manual);
    service.updatePartSettings({ kind: 'section-role', sectionId, role: track.role }, { transpose: 12 });
    service.updatePartSettings({ kind: 'section-track', sectionId, trackId: track.id }, { transpose: -12 });
  }
  const scope = { kind: 'section', sectionId };
  service.setTrackRerollLock('track-bass-1', true);
  const locked = JSON.stringify(service.project().arrangement[0].trackPartOverrides['track-bass-1']);
  assert.equal(service.changeOverviewTracks(scope, 'reroll'), 'changed');
  assert.equal(JSON.stringify(service.project().arrangement[0].trackPartOverrides['track-bass-1']), locked);
  assert.deepEqual(service.project().arrangement[0].trackPhrases['track-bass-1'], manual);
  const before = service.exportProjectJson();
  assert.equal(service.changeOverviewTracks(scope, 'reset'), 'changed');
  const section = service.project().arrangement[0];
  assert.deepEqual(section.rolePartOverrides, {});
  assert.deepEqual(section.trackPartOverrides, {});
  assert.deepEqual(section.trackPhrases, {});
  assert.deepEqual(new Set(section.generatedTrackIds), new Set(service.project().tracks.map(track => track.id)));
  ui.selectSectionTrack(sectionId, 'track-bass-1');
  assert.equal(workspace.manualTrackNames(), '');
  const after = service.exportProjectJson();
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.redo(); assert.equal(service.exportProjectJson(), after);
  service.duplicateSection(0);
  assert.deepEqual(service.project().arrangement[1].generatedTrackIds, section.generatedTrackIds);
  service.removeTrack('track-bass-1');
  assert.ok(service.project().arrangement.every(item => !item.generatedTrackIds?.includes('track-bass-1')));
});

test('toolbar reroll replaces structure and presence in one undo, independent of Compose switches', () => {
  const { service, workspace, load } = fixture();
  service.updateSettings({ arrangementManual: true, randomizeArrangementOnCompose: false, seed: '9223372036854775807' });
  const before = service.exportProjectJson();
  const project = service.project();
  const { randomizeArrangement } = load('core/project/project-arrangement-commands.ts');
  const { DEFAULT_SECTION_TYPE_CHANCES } = load('core/project/section-type-chances.ts');
  const originalRandom = Math.random;
  try {
    Math.random = () => .25;
    workspace.randomizeArrangement();
  } finally { Math.random = originalRandom; }
  const rolled = service.exportProjectJson();
  assert.notEqual(rolled, before);
  assert.deepEqual(service.project().arrangement,
    randomizeArrangement(project, DEFAULT_SECTION_TYPE_CHANCES, BigInt(0x1000000000000 / 4)).arrangement);
  assert.notEqual(service.project().arrangement.length, project.arrangement.length);
  assert.deepEqual(service.project().tracks, project.tracks);
  assert.equal(service.project().seed, project.seed);
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.redo(); assert.equal(service.exportProjectJson(), rolled);
});


test('arrangement inspector shares values, preserves other section fields and commits once', () => {
  const { service, load, ui } = fixture();
  const { SectionContextEditorComponent } = load('features/arrangement/section-context-editor.component.ts');
  const editor = new SectionContextEditorComponent();
  editor.allSections.set(true);
  editor.section.set(service.project().arrangement[0]);
  const sections = service.project().arrangement;
  service.updateSectionContext(sections[1].id, { swingPercent: 31, keyChange: 2 });
  editor.section.set(service.project().arrangement[0]);
  assert.equal(editor.contextMixed('swingPercent'), true);
  const before = service.exportProjectJson();
  editor.choose('swingPercent', '58', true);
  assert.ok(service.project().arrangement.every(section => section.context.swingPercent === 58));
  assert.equal(service.project().arrangement[1].context.keyChange, 2);
  editor.section.set(service.project().arrangement[0]);
  assert.equal(editor.contextMixed('swingPercent'), false);
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.redo();
  const shared = service.exportProjectJson();
  editor.section.set(service.project().arrangement[0]);
  editor.setDuration(0, { target: { value: '3' } });
  assert.ok(service.project().arrangement.every(section => section.customDurations[0] === 3));
  service.undo(); assert.equal(service.exportProjectJson(), shared);
  assert.equal(service.editAllSectionMusic(section => ({ ...section, context: { swingPercent: 999 } })), 'invalid');
  assert.equal(service.exportProjectJson(), shared);
});


test('arrangement combined section controls and chord edits update all destinations atomically', () => {
  const { service, load } = fixture();
  const editor = new (load('features/arrangement/section-context-editor.component.ts').SectionContextEditorComponent)();
  editor.allSections.set(true); editor.section.set(service.project().arrangement[0]);
  editor.setAllType('VERSE1');
  assert.ok(service.project().arrangement.every(section => section.type === 'VERSE1'));
  const before = service.exportProjectJson();
  editor.setAllMeasures({ target: { value: '128' } });
  assert.equal(service.exportProjectJson(), before);
  editor.setPresence(service.project().tracks[0].id, { target: { checked: false } });
  assert.ok(service.project().arrangement.every(section => section.trackPresence[service.project().tracks[0].id] === false));
  service.undo(); assert.equal(service.exportProjectJson(), before);
  const chords = new (load('features/harmony/chordlet-editor.component.ts').ChordletEditorComponent)();
  chords.allSections.set(true); chords.section.set(service.project().arrangement[0]);
  chords.setChord(0, { root: 2, spice: 'm', inversion: 0 });
  assert.ok(service.project().arrangement.every(section => section.customChords[0].root === 2));
  service.undo(); assert.equal(service.exportProjectJson(), before);
});


test('section velocity knobs reset the last override to 0 dB with RMB in single and all-section scopes', () => {
  for (const all of [false, true]) {
    const { service, load } = fixture();
    const editor = new (load('features/arrangement/section-context-editor.component.ts').SectionContextEditorComponent)();
    editor.allSections.set(all);
    editor.section.set(service.project().arrangement[0]);
    editor.setVelocity('melody', 140);
    editor.section.set(service.project().arrangement[0]);
    const before = service.exportProjectJson();
    const knob = new (load('shared/compact-knob.component.ts').CompactKnobComponent)();
    knob.value.set(140); knob.minimum.set(0); knob.maximum.set(editor.maximumVelocity);
    knob.defaultValue.set(100); knob.step.set(0.1); knob.valueFormat.set('decibels');
    knob.valueCommit.emit = value => editor.setVelocity('melody', value);
    knob.resetDefault({ button: 2, ctrlKey: false, shiftKey: false, altKey: false, metaKey: false, preventDefault() {}, stopPropagation() {} });
    const targets = all ? service.project().arrangement : [service.project().arrangement[0]];
    assert.ok(targets.every(section => section.context?.velocityPercent === undefined));
    assert.notEqual(service.exportProjectJson(), before);
    service.undo(); assert.equal(service.exportProjectJson(), before);
  }
});
