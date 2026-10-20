const assert = require('node:assert/strict');
const test = require('node:test');
const { fixture } = require('./support/project-fixture.cjs');

test('storage role arrays follow track order, including arbitrary IDs, empty roles and all-false groups', () => {
  const { service, load } = fixture();
  const { encodeProjectStorage } = load('core/project/project-storage.ts');
  const { decodeProject, isProject } = load('core/project/project-codec.ts');
  const { DEFAULT_SECTION_TYPE_CHANCES: chances } = load('core/project/section-type-chances.ts');
  const { formatProjectJson } = load('core/project/project-json.ts');
  const { generateCompositionMidi } = load('core/music/midi-export.ts');
  const base = service.project(), drum = base.tracks.find(track => track.role === 'drums');
  const tracks = [
    { ...drum, id: 'percussion-z' },
    base.tracks.find(track => track.role === 'bass'),
    { ...drum, id: 'percussion-a' },
  ];
  const presence = { 'percussion-z': false, 'track-bass-1': false, 'percussion-a': true };
  const project = { ...base, tracks, arrangement: base.arrangement.map(section => ({ ...section, trackPresence: { ...presence } })) };
  const before = JSON.stringify(project), encoded = encodeProjectStorage(project);
  assert.deepEqual(encoded.arrangement[0].trackPresence, {
    melody: [], bass: [false], chords: [], arpeggio: [], drums: [false, true],
  });
  assert.ok(formatProjectJson(project).includes('"drums": [false, true]'));
  assert.equal(JSON.stringify(project), before, 'serialization leaves app maps untouched');
  const decoded = decodeProject(encoded, chances, () => 0.5);
  assert.deepEqual(decoded.arrangement.map(section => section.trackPresence), project.arrangement.map(section => section.trackPresence));
  assert.deepEqual(generateCompositionMidi(decoded), generateCompositionMidi(project));
  assert.equal(isProject(encoded, chances, () => 0.5), false, 'app candidates must use ID maps');
  // The presence decoder itself never fills short arrays or invents values.
  const { decodeStoredTrackPresence } = load('core/project/project-storage.ts');
  assert.deepEqual(decodeStoredTrackPresence(encoded.arrangement[0].trackPresence, tracks), presence);
  assert.deepEqual(decodeProject(project, chances, () => 0.5).arrangement[0].trackPresence, presence, 'existing ID-map files still load');
});

test('malformed role arrays reject atomically with presence diagnostics and preserve redo', () => {
  const { service } = fixture();
  service.updateSettings({ name: 'redo' }); service.undo();
  const saved = service.exportProjectJson();
  const flags = JSON.parse(saved).arrangement[0].trackPresence;
  const warnings = [], originalWarn = console.warn;
  console.warn = (...args) => warnings.push(args);
  try {
    for (const presence of [
      { ...flags, bass: [] }, { ...flags, bass: [true, false] }, { ...flags, bass: ['true'] },
      { ...flags, bass: [1] }, { ...flags, bass: null }, { ...flags, bass: true },
      { ...flags, bass: undefined }, { ...flags, extra: [] }, { ...flags, 'track-bass-1': true },
    ]) {
      const input = JSON.parse(saved); input.arrangement[0].trackPresence = presence;
      assert.equal(service.importProjectJson(JSON.stringify(input)), false);
      assert.equal(service.exportProjectJson(), saved);
      assert.equal(service.canRedo(), true);
      assert.ok(warnings.at(-1)[1].some(issue => issue.includes('trackPresence')));
    }
  } finally { console.warn = originalWarn; }
});

test('reorder, duplicate, deletion, undo and session reload preserve track identities through role arrays', () => {
  const { service, stored, load } = fixture();
  const first = 'track-drums-1', second = service.addTrack('drums');
  const section = service.project().arrangement[0];
  service.setSectionTrackPresence(0, first, true);
  service.setSectionTrackPresence(0, second, false);
  service.reorderTrack(second, -1);
  let saved = JSON.parse(stored.get('vibecomposer.project.v12'));
  assert.deepEqual(saved.arrangement[0].trackPresence.drums, [false, true]);
  assert.equal(service.project().arrangement[0].trackPresence[first], true);
  const duplicate = service.duplicateTrack(second);
  assert.equal(service.project().arrangement[0].trackPresence[duplicate], false);
  service.removeTrack(first);
  assert.deepEqual(JSON.parse(service.exportProjectJson()).arrangement[0].trackPresence.drums, [false, false]);
  service.undo();
  assert.equal(service.project().arrangement[0].trackPresence[first], true);
  const exported = service.exportProjectJson();
  const expected = { ...service.project().arrangement[0].trackPresence };
  assert.equal(service.importProjectJson(exported), true);
  assert.deepEqual(service.project().arrangement[0].trackPresence, expected);
  // Existing sessions are rewritten to role arrays immediately on restore.
  stored.set('vibecomposer.project.v12', JSON.stringify(service.project()));
  const restored = new (load('core/project/project.service.ts').ProjectService)();
  assert.deepEqual(restored.project().arrangement.find(item => item.id === section.id).trackPresence, expected);
  saved = JSON.parse(stored.get('vibecomposer.project.v12'));
  assert.ok(Object.values(saved.arrangement[0].trackPresence).every(Array.isArray));
});
