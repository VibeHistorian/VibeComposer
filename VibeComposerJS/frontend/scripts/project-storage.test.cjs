const assert = require('node:assert/strict');
const test = require('node:test');
const { fixture } = require('./support/project-fixture.cjs');

test('all project commands publish immediately and coalesce encoding and both storage writes', () => {
  const { service, load, stored, uiStored, advancePersistence, persistenceTimers } = fixture();
  service.flushPersistence();
  const storage = load('core/project/project-storage.ts'), encode = storage.encodeProjectStorage;
  let encodes = 0, sessionWrites = 0, workspaceWrites = 0;
  storage.encodeProjectStorage = project => { encodes++; return encode(project); };
  global.sessionStorage.setItem = (key, value) => { sessionWrites++; stored.set(key, value); };
  global.localStorage.setItem = (key, value) => { workspaceWrites++; uiStored.set(key, value); };
  const commands = [
    () => service.updateSettings({ tempoBpm: 137 }),
    () => service.rerollRoleTracks('chords', 'track-chords-1'),
    () => service.rerollRoleTracks('bass'),
    () => service.generateComposition(),
  ];
  for (const command of commands) {
    const previous = service.project();
    command();
    assert.notEqual(service.project(), previous, 'command publishes before persistence');
    assert.equal(service.canUndo(), true);
    advancePersistence(30);
    assert.equal(encodes, 0, 'no encoding in the command or during the burst');
    assert.equal(sessionWrites + workspaceWrites, 0);
  }
  advancePersistence(119);
  assert.equal(sessionWrites, 0);
  advancePersistence(1);
  assert.ok(encodes > 0);
  assert.equal(sessionWrites, 1);
  assert.equal(workspaceWrites, 1);
  assert.equal(persistenceTimers.size, 0);
  assert.equal(stored.get('vibecomposer.project.v12'), JSON.stringify(encode(service.project())));
  const saved = JSON.parse(uiStored.get('vibecomposer.project-tabs.v1'));
  assert.equal(saved.tabs.find(tab => tab.id === saved.activeId).past.length, service.historyPosition());
  advancePersistence(2000);
  assert.equal(sessionWrites, 1, 'cancelled deadlines do not save again');
  service.ngOnDestroy();
});

test('sustained project changes save at the deadline and resume coalescing afterwards', () => {
  const { service, stored, advancePersistence } = fixture();
  service.flushPersistence();
  const original = stored.get('vibecomposer.project.v12');
  for (let index = 0; index < 10; index++) {
    service.updateSettings({ name: `Continuous ${index}` });
    advancePersistence(99);
    assert.equal(stored.get('vibecomposer.project.v12'), original);
  }
  advancePersistence(10);
  assert.equal(JSON.parse(stored.get('vibecomposer.project.v12')).name, 'Continuous 9');
  service.updateSettings({ name: 'After deadline' });
  advancePersistence(149);
  assert.equal(JSON.parse(stored.get('vibecomposer.project.v12')).name, 'Continuous 9');
  advancePersistence(1);
  assert.equal(JSON.parse(stored.get('vibecomposer.project.v12')).name, 'After deadline');
  service.ngOnDestroy();
});

test('pending saves retain both tabs, undo/redo, inactive edits and the latest active identity', () => {
  const { service, load, stored, uiStored, advancePersistence } = fixture();
  service.flushPersistence();
  const first = service.activeProjectTabId();
  service.updateSettings({ name: 'Original latest' });
  service.updateSettings({ tempoBpm: 137 });
  service.undo();
  service.duplicateProjectTab();
  const second = service.activeProjectTabId();
  service.redo();
  service.renameProjectTab(first, 'Inactive renamed');
  service.switchProjectTab(first);
  assert.equal(service.project().name, 'Inactive renamed');
  service.undo();
  service.switchProjectTab(second);
  assert.equal(service.project().tempoBpm, 137);
  advancePersistence(150);
  assert.equal(JSON.parse(stored.get('vibecomposer.project.v12')).name, service.project().name);
  const saved = JSON.parse(uiStored.get('vibecomposer.project-tabs.v1'));
  assert.equal(saved.activeId, second);
  const restored = new (load('core/project/project.service.ts').ProjectService)();
  restored.switchProjectTab(first);
  restored.redo();
  assert.equal(restored.project().name, 'Inactive renamed');
  restored.switchProjectTab(second);
  restored.undo();
  assert.notEqual(restored.project().tempoBpm, 137);
  restored.redo();
  assert.equal(restored.project().tempoBpm, 137);
  service.ngOnDestroy(); restored.ngOnDestroy();
});

test('visibility, pagehide and destruction flush the latest workspace and release callbacks', () => {
  const { service, stored, advancePersistence, persistencePage, persistenceDocument, persistenceTimers } = fixture();
  service.flushPersistence();
  service.updateSettings({ name: 'Hidden save' });
  persistenceDocument.dispatchEvent(new Event('visibilitychange'));
  assert.notEqual(JSON.parse(stored.get('vibecomposer.project.v12')).name, 'Hidden save');
  persistenceDocument.visibilityState = 'hidden';
  persistenceDocument.dispatchEvent(new Event('visibilitychange'));
  assert.equal(JSON.parse(stored.get('vibecomposer.project.v12')).name, 'Hidden save');
  service.updateSettings({ name: 'Pagehide save' });
  persistencePage.dispatchEvent(new Event('pagehide'));
  assert.equal(JSON.parse(stored.get('vibecomposer.project.v12')).name, 'Pagehide save');
  service.updateSettings({ name: 'Destroy save' });
  service.ngOnDestroy();
  assert.equal(JSON.parse(stored.get('vibecomposer.project.v12')).name, 'Destroy save');
  assert.equal(persistenceTimers.size, 0);
  let writes = 0;
  global.sessionStorage.setItem = () => writes++;
  persistencePage.dispatchEvent(new Event('pagehide'));
  persistenceDocument.dispatchEvent(new Event('visibilitychange'));
  advancePersistence(2000);
  assert.equal(writes, 0);
});

test('unavailable storage does not reject changes or prevent later persistence', () => {
  const { service, stored, uiStored, advancePersistence } = fixture();
  service.flushPersistence();
  global.sessionStorage.setItem = () => { throw new Error('Storage unavailable'); };
  service.updateSettings({ name: 'Workspace only' });
  advancePersistence(150);
  assert.equal(JSON.parse(uiStored.get('vibecomposer.project-tabs.v1')).tabs[0].project.name, 'Workspace only');
  global.localStorage.setItem = () => { throw new Error('Quota exceeded'); };
  service.updateSettings({ name: 'Memory only' });
  advancePersistence(150);
  assert.equal(service.project().name, 'Memory only');
  global.sessionStorage.setItem = (key, value) => stored.set(key, value);
  global.localStorage.setItem = (key, value) => uiStored.set(key, value);
  service.updateSettings({ name: 'Storage recovered' });
  advancePersistence(150);
  assert.equal(JSON.parse(stored.get('vibecomposer.project.v12')).name, 'Storage recovered');
  service.ngOnDestroy();
});

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
    melody: [], bass: [], chords: [], arpeggio: [], drums: [2],
  });
  assert.ok(formatProjectJson(project).includes('"drums": [2]'));
  assert.equal(JSON.stringify(project), before, 'serialization leaves app maps untouched');
  const decoded = decodeProject(encoded, chances, () => 0.5);
  assert.deepEqual(decoded.arrangement.map(section => section.trackPresence), project.arrangement.map(section => section.trackPresence));
  assert.deepEqual(generateCompositionMidi(decoded), generateCompositionMidi(project));
  assert.equal(isProject(encoded, chances, () => 0.5), false, 'app candidates must use ID maps');
  // Excluded members expand to explicit false entries.
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
      { ...flags, bass: [0] }, { ...flags, bass: [true, false] }, { ...flags, bass: ['true'] },
      { ...flags, bass: [2] }, { ...flags, bass: [1, 1] }, { ...flags, bass: [1.5] }, { ...flags, bass: null }, { ...flags, bass: true },
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
  service.flushPersistence();
  let saved = JSON.parse(stored.get('vibecomposer.project.v12'));
  assert.deepEqual(saved.arrangement[0].trackPresence.drums, [2]);
  assert.equal(service.project().arrangement[0].trackPresence[first], true);
  const duplicate = service.duplicateTrack(second);
  assert.equal(service.project().arrangement[0].trackPresence[duplicate], false);
  service.removeTrack(first);
  assert.deepEqual(JSON.parse(service.exportProjectJson()).arrangement[0].trackPresence.drums, []);
  service.undo();
  assert.equal(service.project().arrangement[0].trackPresence[first], true);
  const exported = service.exportProjectJson();
  const expected = { ...service.project().arrangement[0].trackPresence };
  assert.equal(service.importProjectJson(exported), true);
  assert.deepEqual(service.project().arrangement[0].trackPresence, expected);
  // Existing sessions are rewritten to role arrays when the restored workspace saves.
  service.flushPersistence();
  stored.set('vibecomposer.project.v12', JSON.stringify(service.project()));
  service.flushPersistence();
  const restored = new (load('core/project/project.service.ts').ProjectService)();
  assert.deepEqual(restored.project().arrangement.find(item => item.id === section.id).trackPresence, expected);
  restored.flushPersistence();
  saved = JSON.parse(stored.get('vibecomposer.project.v12'));
  assert.ok(Object.values(saved.arrangement[0].trackPresence).every(Array.isArray));
});

test('presence lists expand every excluded track to false and retain old complete boolean imports', () => {
  const { service, load } = fixture();
  const { decodeStoredTrackPresence, encodeProjectStorage } = load('core/project/project-storage.ts');
  const project = service.project();
  const roles = ['melody', 'bass', 'chords', 'arpeggio', 'drums'];
  const tracks = roles.flatMap(role => {
    const source = project.tracks.find(track => track.role === role);
    return Array.from({ length: role === 'drums' ? 6 : 1 }, (_, index) => ({ ...source, id: role + '-' + (index + 1) }));
  });
  const lists = { melody: [], bass: [], chords: [1], arpeggio: [1], drums: [4, 5] };
  const decoded = decodeStoredTrackPresence(lists, tracks);
  const expected = Object.fromEntries(tracks.map(track => [track.id, ['chords-1', 'arpeggio-1', 'drums-4', 'drums-5'].includes(track.id)]));
  assert.deepEqual(decoded, expected);
  const booleans = Object.fromEntries(roles.map(role => [role, tracks.filter(track => track.role === role).map(track => expected[track.id])]));
  assert.deepEqual(decodeStoredTrackPresence(booleans, tracks), expected);
  assert.equal(decodeStoredTrackPresence({ ...lists, drums: [true, false, false, true, true, false] }, tracks), undefined, 'mixed numeric/boolean encodings reject');
  assert.deepEqual(encodeProjectStorage({ ...project, tracks, arrangement: [{ ...project.arrangement[0], trackPresence: decoded }] }).arrangement[0].trackPresence, lists);
  const none = Object.fromEntries(roles.map(role => [role, []]));
  assert.deepEqual(decodeStoredTrackPresence(none, tracks), Object.fromEntries(tracks.map(track => [track.id, false])));
});
