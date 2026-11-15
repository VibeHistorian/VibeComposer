const assert = require('node:assert/strict');
const test = require('node:test');
const { fixture } = require('./support/project-fixture.cjs');

function hosted(f, { config = {}, files = {}, query = '' } = {}) {
  const requests = [], messages = [];
  const { HostedContent } = f.load('core/ui/hosted-content.ts');
  const content = new HostedContent({
    baseUrl: 'https://example.test/VibeComposerJS/',
    pageUrl: 'https://example.test/VibeComposerJS/' + query,
    fetchText: async url => {
      requests.push(url);
      const key = url.replace('https://example.test/VibeComposerJS/', '');
      if (key === 'startup.json') {
        if (config instanceof Error) throw config;
        return typeof config === 'string' ? config : JSON.stringify(config);
      }
      if (!(key in files)) throw new Error('404');
      return typeof files[key] === 'string' ? files[key] : JSON.stringify(files[key]);
    },
    report: message => messages.push(message),
  });
  return { content, requests, messages };
}

test('first visit loads recommended settings and one demo tab; new empty/demo tabs have fresh independent histories', async () => {
  const f = fixture();
  f.uiStored.clear();
  const demo = { ...JSON.parse(f.service.exportProjectJson()), name: 'Hosted demo', seed: '9223372036854775806' };
  const recommended = { ...JSON.parse(f.uiPreferences.exportJson()), midiPpq: 480, compactHistory: true };
  const { content, requests, messages } = hosted(f, {
    config: { demoProject: 'projects/demo.json', recommendedSettings: 'settings/recommended.json' },
    files: { 'projects/demo.json': demo, 'settings/recommended.json': recommended },
  });
  assert.equal(f.service.hasSavedWorkspace, false);
  const first = f.service.activeProjectTabId();
  assert.deepEqual(await content.initialize(f.uiPreferences, () => {
    assert.equal(f.uiPreferences.midiPpq(), 480, 'settings applied before creating project state');
    return f.service;
  }), []);
  assert.deepEqual(messages, []);
  assert.equal(f.service.project().name, 'Hosted demo');
  assert.equal(f.service.project().seed, demo.seed);
  assert.equal(f.service.activeProjectTabId(), first);
  assert.equal(f.service.openProjects().length, 1);
  assert.equal(f.service.canUndo(), false);
  assert.equal(f.uiPreferences.midiPpq(), 480);
  f.service.updateSettings({ name: 'My edits' });
  const position = f.service.historyPosition();
  f.uiPreferences.setReplaceCurrentProjectTab(true);
  f.service.newEmptyProjectTab();
  assert.notEqual(f.service.project().name, demo.name);
  assert.equal(f.service.canUndo(), false);
  assert.equal(f.service.openProjects().length, 2);
  assert.equal(f.service.importProjectJson(await content.demoProject(), 'new-tab'), true);
  assert.equal(f.service.openProjects().length, 3);
  assert.equal(f.service.project().name, demo.name);
  f.service.switchProjectTab(first);
  assert.equal(f.service.project().name, 'My edits');
  assert.equal(f.service.historyPosition(), position);
  assert.equal(requests.filter(url => url.endsWith('/projects/demo.json')).length, 1);
});

test('saved workspaces and settings bypass hosted defaults; URL imports override settings, then appearance, and always add a project tab', async () => {
  const f = fixture();
  f.service.updateSettings({ name: 'Saved work' });
  f.service.flushPersistence();
  const restored = new (f.load('core/project/project.service.ts').ProjectService)();
  assert.equal(restored.hasSavedWorkspace, true);
  const json = JSON.parse(f.uiPreferences.exportJson());
  const files = {
    'linked.json': { ...JSON.parse(f.service.exportProjectJson()), name: 'Linked work' },
    'settings.json': { ...json, midiPpq: 480, replaceCurrentProjectTab: true },
    'theme.json': { ...json, midiPpq: 960, appearance: {
      ...json.appearance, roleColors: { ...json.appearance.roleColors, melody: '#123456' },
    } },
  };
  const { content, requests, messages } = hosted(f, {
    config: { demoProject: 'missing-demo.json', recommendedSettings: 'missing-settings.json' },
    files, query: '?project=linked.json&settings=settings.json&theme=theme.json',
  });
  assert.deepEqual((await content.initialize(f.uiPreferences, () => restored)).sort(), ['project', 'settings', 'theme']);
  assert.equal(requests.some(url => url.includes('missing-')), false);
  assert.deepEqual(messages, []);
  assert.equal(restored.openProjects().length, 2);
  assert.equal(restored.project().name, 'Linked work');
  assert.equal(restored.openProjects()[0].name, 'Saved work');
  assert.equal(restored.canUndo(), false);
  assert.equal(f.uiPreferences.midiPpq(), 480);
  assert.equal(f.uiPreferences.appearance().roleColors.melody, '#123456');
});

test('a valid legacy session also protects work when no saved tabs exist', async () => {
  const f = fixture();
  f.service.updateSettings({ name: 'Session work' });
  f.service.flushPersistence();
  f.uiStored.delete('vibecomposer.project-tabs.v1');
  const restored = new (f.load('core/project/project.service.ts').ProjectService)();
  assert.equal(restored.hasSavedWorkspace, true);
  const { content, requests } = hosted(f, { config: { demoProject: 'missing.json' } });
  await content.initialize(f.uiPreferences, () => restored);
  assert.equal(restored.project().name, 'Session work');
  assert.equal(requests.length, 1);
});

test('bad or missing hosted files leave defaults usable, report failures, and never consume failed links', async () => {
  const f = fixture();
  f.uiStored.clear();
  const before = f.service.exportProjectJson();
  const { content, messages } = hosted(f, {
    config: { demoProject: 'bad.json', recommendedSettings: 'missing.json' },
    files: { 'bad.json': '{}', 'bad-settings.json': '{}', 'bad-theme.json': '{}' },
    query: '?project=bad.json&settings=bad-settings.json&theme=bad-theme.json',
  });
  const warn = console.warn; console.warn = () => {};
  try { assert.deepEqual(await content.initialize(f.uiPreferences, () => f.service), []); }
  finally { console.warn = warn; }
  assert.equal(f.service.exportProjectJson(), before);
  assert.equal(f.service.openProjects().length, 1);
  assert.equal(f.service.canUndo(), false);
  assert.equal(f.uiPreferences.hasStoredPreferences(), false);
  assert.equal(messages.length, 5);
});

test('unavailable/invalid config does not block explicit links; relative and external URLs work, unsafe/empty URLs do not fetch', async () => {
  const f = fixture();
  for (const config of [new Error('offline'), '[]', '{"demoProject":42}']) {
    const { content, messages } = hosted(f, {
      config, query: '?project=https%3A%2F%2Fremote.test%2Fexample.json',
      files: { 'https://remote.test/example.json': { ...JSON.parse(f.service.exportProjectJson()), name: 'Remote' } },
    });
    assert.deepEqual(await content.initialize(f.uiPreferences, () => f.service), ['project']);
    assert.equal(f.service.project().name, 'Remote');
    assert.equal(content.hasDemo, false);
    assert.equal(messages.length, 1);
  }
  const { content, requests, messages } = hosted(f, { query: '?project=javascript%3Aalert(1)&settings=&theme=file%3A%2F%2F%2Fsecret' });
  assert.deepEqual(await content.initialize(f.uiPreferences, () => f.service), []);
  assert.equal(requests.length, 1);
  assert.equal(messages.length, 3);
});

test('a failed demo download can be retried on demand without changing a saved workspace', async () => {
  const f = fixture();
  f.service.flushPersistence();
  const restored = new (f.load('core/project/project.service.ts').ProjectService)();
  const files = {};
  const { content, messages } = hosted(f, { config: { demoProject: 'later.json' }, files });
  await content.initialize(f.uiPreferences, () => restored);
  assert.equal(content.hasDemo, true);
  assert.equal(await content.demoProject(), null);
  files['later.json'] = f.service.exportProjectJson();
  assert.equal(await content.demoProject(), files['later.json']);
  assert.equal(restored.openProjects().length, 1);
  assert.equal(messages.length, 1);
});
