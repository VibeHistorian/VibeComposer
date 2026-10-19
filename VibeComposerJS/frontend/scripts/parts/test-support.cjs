const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const { fixture } = require('../support/project-fixture.cjs');
const { assertRejectedProjectImport } = require('../support/project-import-assertions.cjs');

test('project fixtures reuse transpilation while isolating evaluated modules, signals, history and storage', () => {
  const originalRead = fs.readFileSync;
  const originalRandom = Math.random;
  let sourceReads = 0;
  fs.readFileSync = (...args) => {
    if (String(args[0]).endsWith('.ts')) sourceReads++;
    return originalRead(...args);
  };
  Math.random = () => 0.5;
  try {
    const first = fixture();
    const initialProject = first.service.exportProjectJson();
    const initialHistory = first.service.historyEntries();
    const originalLayout = first.phrase.layOutTrackPhrase;
    first.phrase.layOutTrackPhrase = () => [];
    first.service.updateSettings({ name: 'First fixture only' });
    first.uiPreferences.toggleScoreChordGrid();
    const firstStorage = [...first.stored];
    const firstUiStorage = [...first.uiStored];
    const compiled = sourceReads;
    assert.ok(compiled > 0, 'the first fixture reads TypeScript sources for compilation');
    const second = fixture();
    assert.equal(sourceReads, compiled, 'fresh evaluation reuses compiled text without rereading sources');
    assert.notEqual(first.load('core/music/phrase.ts'), second.load('core/music/phrase.ts'));
    assert.notEqual(second.phrase.layOutTrackPhrase, first.phrase.layOutTrackPhrase);
    assert.notEqual(second.phrase.layOutTrackPhrase, originalLayout);
    assert.notEqual(first.service.project, second.service.project);
    assert.equal(second.service.exportProjectJson(), initialProject);
    assert.deepEqual(second.service.historyEntries(), initialHistory);
    assert.equal(second.uiPreferences.scoreChordGrid(), false);
    assert.notEqual(first.stored, second.stored);
    assert.notEqual(first.uiStored, second.uiStored);
    second.service.updateSettings({ name: 'Second fixture only' });
    second.uiPreferences.toggleScoreChordGrid();
    assert.equal(first.service.project().name, 'First fixture only');
    assert.deepEqual([...first.stored], firstStorage);
    assert.deepEqual([...first.uiStored], firstUiStorage);
  } finally {
    fs.readFileSync = originalRead;
    Math.random = originalRandom;
  }
});

test('negative-import assertions require property diagnostics and always restore warning handling', () => {
  const originalWarn = console.warn;
  assertRejectedProjectImport({ importProjectJson() {
    console.warn('Could not load project from imported JSON.', ['project.name: missing required property']);
    return false;
  } }, '{}');
  assert.equal(console.warn, originalWarn);
  assert.throws(() => assertRejectedProjectImport({ importProjectJson: () => false }, '{}'), /one diagnostic/);
  assert.equal(console.warn, originalWarn);
  assert.throws(() => assertRejectedProjectImport({ importProjectJson() { throw new Error('unexpected failure'); } }, '{}'), /unexpected failure/);
  assert.equal(console.warn, originalWarn);
});

test('negative-import assertions forward unexpected warnings and reject unrelated diagnostics', () => {
  const forwarded = [];
  const originalWarn = console.warn;
  const warn = (...args) => forwarded.push(args);
  console.warn = warn;
  try {
    assert.throws(() => assertRejectedProjectImport({ importProjectJson() {
      console.warn('Unexpected storage warning', new Error('storage unavailable'));
      return false;
    } }, '{}'));
    assert.equal(console.warn, warn);
    assert.equal(forwarded.length, 1);
    assert.equal(forwarded[0][0], 'Unexpected storage warning');
    assert.match(forwarded[0][1].message, /storage unavailable/);
    assert.throws(() => assertRejectedProjectImport({ importProjectJson() {
      console.warn('Could not load project from imported JSON.', []);
      return false;
    } }, '{}'), /property-path diagnostics/);
    assert.equal(console.warn, warn);
  } finally {
    console.warn = originalWarn;
  }
});
