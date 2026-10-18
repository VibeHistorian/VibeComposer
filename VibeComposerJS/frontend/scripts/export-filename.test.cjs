const assert = require('node:assert/strict');
const test = require('node:test');
const { loadMusic } = require('./music-loader.cjs');
const { exportFilename, DEFAULT_EXPORT_NAME_FIELDS } = loadMusic('../project/export-filename.ts');
const { defaultUiPreferences, decodeUiPreferences } = loadMusic('../ui/ui-preferences.ts');
const project = { name: 'My composition', key: 'F#', tempoBpm: 128, seed: '9223372036854775807' };
const now = new Date(2026, 0, 2, 3, 4);

test('export fields use local date and time, fixed order, and preserve long seeds', () => {
  assert.equal(exportFilename(project, DEFAULT_EXPORT_NAME_FIELDS, now), '20260102_03-04_Fs_128_9223372036854775807_My composition');
  assert.equal(exportFilename(project, ['seed', 'key'], now), 'Fs_9223372036854775807_My composition');
  for (const key of ['Bb', 'B♭']) assert.equal(exportFilename({ ...project, key }, ['key'], now), 'Bb_My composition');
  assert.equal(exportFilename({ ...project, key: 'F♯' }, ['key'], now), 'Fs_My composition');
});

test('no enabled fields retains project name and invalid filename characters are sanitized', () => {
  assert.equal(exportFilename(project, [], now), 'My composition');
  assert.equal(exportFilename({ ...project, name: 'Export: test.' }, [], now), 'Export- test');
  assert.equal(exportFilename({ ...project, name: ' ' }, [], now), 'VibeComposer');
  assert.ok(exportFilename({ ...project, name: 'x'.repeat(100) }, DEFAULT_EXPORT_NAME_FIELDS, now).endsWith('_' + 'x'.repeat(80)));
});

test('filename preferences migrate old data and round-trip enabled and disabled fields', () => {
  const preferences = defaultUiPreferences();
  const { exportNameFields, ...legacy } = preferences;
  assert.deepEqual(decodeUiPreferences(legacy).exportNameFields, DEFAULT_EXPORT_NAME_FIELDS);
  for (const fields of [[], ['date', 'key'], DEFAULT_EXPORT_NAME_FIELDS]) {
    assert.deepEqual(decodeUiPreferences(JSON.parse(JSON.stringify({ ...preferences, exportNameFields: fields }))).exportNameFields, fields);
  }
  assert.equal(decodeUiPreferences({ ...preferences, exportNameFields: ['invalid'] }), null);
  assert.equal(decodeUiPreferences({ ...preferences, exportNameFields: 'date' }), null);
});
