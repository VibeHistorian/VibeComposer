const assert = require('node:assert/strict');
const test = require('node:test');
const { fixture } = require('./support/project-fixture.cjs');

test('factory library covers 24 complete styles with valid, deterministic generated layers', () => {
  const { service, load } = fixture();
  const { BUILTIN_TRACK_PRESETS: library } = load('core/project/builtin-track-presets.ts');
  const { decodeTrackPresets } = load('core/project/track-presets.ts');
  const { generateTrackPhrase } = load('core/music/phrase.ts');
  assert.equal(library.length, 120);
  assert.ok(decodeTrackPresets(JSON.parse(JSON.stringify(library))));
  const names = new Set(library.map(preset => preset.name));
  assert.equal(names.size, 24);
  const original = JSON.stringify(library);
  for (const name of names) {
    const style = library.filter(preset => preset.name === name);
    assert.equal(new Set(style.map(preset => preset.role)).size, 5);
    const before = service.exportProjectJson();
    for (const preset of style) {
      assert.ok(preset.tracks.length >= 2);
      assert.equal(service.loadTrackPreset(preset.role, preset.tracks, true), 'changed', name);
      for (const track of service.project().tracks.filter(track => track.role === preset.role)) {
        assert.equal(track.editedPhrase, undefined);
        assert.equal(track.phraseEdits, undefined);
        const notes = generateTrackPhrase(service.project(), track);
        assert.ok(notes.length, `${name}: ${track.name} must sound`);
        assert.deepEqual(generateTrackPhrase(service.project(), track), notes);
        assert.ok(notes.every(note => Number.isFinite(note.startBeat) && note.durationBeats > 0
          && note.midi >= 0 && note.midi <= 127 && note.velocity >= 1 && note.velocity <= 127));
      }
    }
    for (let index = 0; index < 5; index++) service.undo();
    assert.equal(service.exportProjectJson(), before);
  }
  assert.equal(JSON.stringify(library), original, 'loads never mutate factory data');
});

test('preset replacement preserves locked tracks and occupied slots, cleans surplus references and undoes once', () => {
  const { service } = fixture();
  const lockedId = service.project().tracks.find(track => track.role === 'bass').id;
  service.setTrackRerollLock(lockedId, true);
  const replacedId = service.addTrack('bass');
  const preset = structuredClone(service.project().tracks.filter(track => track.id === replacedId));
  preset[0].generatorSettings.noteVariation = 55;
  const removedId = service.addTrack('bass');
  service.updatePartSettings({ kind: 'section-track', sectionId: service.project().arrangement[0].id, trackId: replacedId }, { noteVariation: 61 });
  service.updatePartSettings({ kind: 'section-track', sectionId: service.project().arrangement[0].id, trackId: removedId }, { noteVariation: 71 });
  const before = service.exportProjectJson();
  const locked = service.project().tracks.find(track => track.id === lockedId);
  assert.equal(service.loadTrackPreset('bass', preset, true), 'changed');
  const after = service.exportProjectJson();
  const tracks = service.project().tracks.filter(track => track.role === 'bass');
  assert.equal(tracks.length, 2);
  assert.deepEqual(tracks.find(track => track.id === lockedId), locked);
  assert.equal(tracks.find(track => track.id === replacedId).generatorSettings.noteVariation, 55);
  assert.equal(service.project().arrangement[0].trackPartOverrides[replacedId].noteVariation, 61);
  assert.ok(!tracks.some(track => track.id === removedId));
  for (const section of service.project().arrangement) {
    assert.ok(!Object.hasOwn(section.trackPresence, removedId));
    assert.ok(!Object.hasOwn(section.trackPartOverrides, removedId));
    assert.equal(typeof section.trackPresence[tracks.find(track => track.id !== lockedId).id], 'boolean');
  }
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.redo(); assert.equal(service.exportProjectJson(), after);
  assert.deepEqual(preset[0].id, replacedId, 'loading does not mutate the library');
});

test('saving and replacing six drum tracks preserves the complete project and never rerolls presence', () => {
  const { service, load } = fixture();
  service.setRoleTrackCount('drums', 6);
  const project = JSON.parse(service.exportProjectJson());
  const drumIds = project.tracks.filter(track => track.role === 'drums').map(track => track.id);
  project.arrangement.forEach((section, index) => {
    section.trackPresence.drums = drumIds.flatMap((_, track) => (index + track) % 3 === 0 ? [track + 1] : []);
    section.trackPartOverrides = { ...section.trackPartOverrides, [drumIds[0]]: { hitsPerPattern: 8 } };
    section.trackPhrases = { ...section.trackPhrases, [drumIds[1]]: [] };
    section.generatedTrackIds = [drumIds[2]];
  });
  assert.equal(service.importProjectJson(JSON.stringify(project)), true);
  const before = service.exportProjectJson();
  const saved = JSON.parse(JSON.stringify(service.project().tracks.filter(track => track.role === 'drums')));
  const { loadTrackPreset } = load('core/project/track-presets.ts');
  const candidate = loadTrackPreset(service.project(), 'drums', saved, true, () => {
    assert.fail('existing drum slots must not request new presence');
  });
  assert.deepEqual(candidate, service.project());
  assert.equal(service.loadTrackPreset('drums', saved, true), 'unchanged');
  assert.equal(service.exportProjectJson(), before);
});

test('preset append allocates independent identities and rejects invalid batches atomically', () => {
  const { service } = fixture();
  const tracks = structuredClone(service.project().tracks.filter(track => track.role === 'drums'));
  const beforeTracks = service.project().tracks;
  assert.equal(service.loadTrackPreset('drums', tracks, false), 'changed');
  const next = service.project().tracks;
  assert.equal(next.length, beforeTracks.length + tracks.length);
  assert.deepEqual(next.slice(0, beforeTracks.length), beforeTracks);
  assert.equal(new Set(next.map(track => track.id)).size, next.length);
  assert.equal(next.at(-1).midiChannel, 10);
  const before = service.exportProjectJson();
  for (const invalid of [[], [{ ...tracks[0], role: 'bass' }], [{ ...tracks[0], mix: {} }], null]) {
    assert.equal(service.loadTrackPreset('drums', invalid, true), 'invalid');
    assert.equal(service.exportProjectJson(), before);
  }
  const tooMany = Array.from({ length: 64 }, (_, index) => ({ ...tracks[0], id: 'preset-' + index }));
  assert.equal(service.loadTrackPreset('drums', tooMany, false), 'invalid');
  assert.equal(service.exportProjectJson(), before);
});

test('preset JSON round trips all roles and rejects mixed roles, duplicate names and bad track data', () => {
  const { service, load } = fixture();
  const { decodeTrackPresets } = load('core/project/track-presets.ts');
  const library = service.project().tracks.map(track => ({ name: 'Shared name', role: track.role, tracks: [track] }));
  assert.deepEqual(decodeTrackPresets(JSON.parse(JSON.stringify(library))), decodeTrackPresets(library));
  for (const invalid of [{}, [null], [{ ...library[0], tracks: [] }], [...library, library[0]],
    [{ ...library[0], name: ' ' }], [{ ...library[0], tracks: [library[1].tracks[0]] }]]) {
    assert.equal(decodeTrackPresets(invalid), undefined);
  }
});
