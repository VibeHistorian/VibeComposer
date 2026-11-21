const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const { findBrowser, requireProductionBuild, createBrowserServer, listen, launchBrowser, closeBrowser, watchBrowser } = require('./support/browser-harness.cjs');
const { readWorkspace, readSetting } = require('./support/workspace-browser.cjs');
const { fixture } = require('./support/project-fixture.cjs');

const browser = findBrowser();
async function runPersistenceScenario(script, setup) {
  const root = requireProductionBuild();
  let resolve, reject;
  const result = new Promise((done, fail) => { resolve = done; reject = fail; });
  result.catch(() => {});
  const html = fs.readFileSync(path.join(root, 'index.html'), 'utf8').replace('</body>', script + '</body>');
  const server = createBrowserServer({ root, html, resolve, reject });
  if (setup) {
    const serve = server.listeners('request')[0];
    server.removeAllListeners('request');
    server.on('request', (request, response) => {
      if (request.url !== '/') return serve(request, response);
      response.setHeader('Content-Type', 'text/html');
      response.end(setup);
    });
  }
  await listen(server);
  const profile = fs.mkdtempSync(path.resolve(__dirname, '../.angular/project-persistence-browser-'));
  const child = launchBrowser(browser, ['--headless', '--disable-gpu', '--no-first-run', '--window-size=1440,1000', '--user-data-dir=' + profile,
    'http://127.0.0.1:' + server.address().port], { stdio: ['ignore', 'ignore', 'pipe'] });
  const watchdog = watchBrowser(child, reject, 30000);
  try { const value = await result; assert.equal(value.error, undefined, value.error); assert.equal(value.ok, true); }
  finally { await closeBrowser({ child, server, profile, watchdog }); }
}

test('production project edits render before storage writes and immediate reload flushes pending history', { skip: !browser }, async () => {
  const script = `<script>(async () => {
    const wait = async (read, label) => {
      for (let index = 0; index < 250; index++) { const value = await read(); if (value) return value; await new Promise(resolve => setTimeout(resolve, 20)); }
      throw Error('Timed out: ' + label);
    };
    const ensure = (value, label) => { if (!value) throw Error(label); };
    const pill = () => document.querySelector('button.project-pill.active');
    const project = () => JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));
    const workspace = ${readWorkspace};
    try {
      await wait(pill, 'project UI');
      if (sessionStorage.getItem('persistence-reload')) {
        ensure(pill().textContent.trim() === 'Pending reload save', 'reload restores the final pending edit');
        ensure(project().name === 'Pending reload save', 'session and active workspace agree');
        const undo = document.querySelector('[aria-label="Undo"]');
        ensure(!undo.disabled, 'reload retains history');
        undo.click();
        await wait(() => [sessionStorage.getItem('persistence-original-name'), 'Deferred save'].includes(pill()?.textContent.trim()), 'undo restored edit');
        document.querySelector('[aria-label="Redo"]').click();
        await wait(() => pill()?.textContent.trim() === 'Pending reload save', 'redo restored edit');
        await fetch('/result', { method: 'POST', body: JSON.stringify({ ok: true }) });
        return;
      }
      await wait(async () => project() && await workspace(), 'initial persistence');
      sessionStorage.setItem('persistence-original-name', pill().textContent.trim());
      let sessionWrites = 0, workspaceWrites = 0;
      const setItem = Storage.prototype.setItem;
      Storage.prototype.setItem = function(key, value) {
        if (key === 'vibecomposer.project.v12') sessionWrites++;
        return setItem.call(this, key, value);
      };
      const put = IDBObjectStore.prototype.put;
      IDBObjectStore.prototype.put = function(value, key) {
        if (this.name === 'workspaces' && key === 'current') workspaceWrites++;
        return put.call(this, value, key);
      };
      const rename = async name => {
        pill().click();
        const input = await wait(() => document.querySelector('input[aria-label="Project name"]'), 'rename input');
        await wait(() => document.activeElement === input, 'rename focus');
        input.value = name;
        input.dispatchEvent(new Event('input', { bubbles: true }));
        const before = [sessionWrites, workspaceWrites];
        input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
        ensure(sessionWrites === before[0] && workspaceWrites === before[1], 'edit must not synchronously write storage');
      };
      await rename('Deferred save');
      await wait(() => pill()?.textContent.trim() === 'Deferred save', 'immediate rendered edit');
      await wait(async () => project().name === 'Deferred save'
        && (await workspace())?.tabs.some(tab => tab.project.name === 'Deferred save'), 'deferred save');
      ensure(sessionWrites === 1 && workspaceWrites === 1, 'one write to each store');
      await rename('Pending reload save');
      sessionStorage.setItem('persistence-reload', 'true');
      location.reload();
    } catch (error) { await fetch('/result', { method: 'POST', body: JSON.stringify({ error: error.stack }) }); }
  })();</script>`;
  await runPersistenceScenario(script);
});

test('database upgrade retains the workspace and migrates preferences, section chances and presets', { skip: !browser }, async () => {
  const { service, uiPreferences } = fixture();
  const project = JSON.parse(service.exportProjectJson());
  const preferences = JSON.parse(uiPreferences.exportJson());
  delete preferences.sectionTypeChances;
  const preset = { name: 'Legacy bass', role: 'bass', tracks: project.tracks.filter(track => track.role === 'bass') };
  const setup = `<script>(async () => {
    try {
      localStorage.setItem('vibecomposer.ui-preferences.v1', JSON.stringify(${JSON.stringify({ ...preferences, midiPpq: 240 })}));
      localStorage.setItem('vibecomposer.section-type-settings.v1', JSON.stringify({ INTRO: { bass: 67, drums: 102 } }));
      localStorage.setItem('vibecomposer.track-presets', JSON.stringify([${JSON.stringify(preset)}]));
      await new Promise((resolve, reject) => {
        const request = indexedDB.open('vibecomposer', 1);
        request.onerror = () => reject(request.error);
        request.onupgradeneeded = () => request.result.createObjectStore('workspaces').put({
          activeId: 'original', revision: 'version-one', tabs: [{
            id: 'original', project: ${JSON.stringify({ ...project, name: 'Version one workspace' })},
            past: [${JSON.stringify(project)}], future: [], automaticSeed: true
          }]
        }, 'current');
        request.onsuccess = () => { request.result.close(); resolve(); };
      });
      location.replace('/?seeded=1');
    } catch (error) { await fetch('/result', { method: 'POST', body: JSON.stringify({ error: error.stack }) }); }
  })();</script>`;
  const script = `<script>(async () => {
    const report = result => fetch('/result', { method: 'POST', body: JSON.stringify(result) });
    const readSetting = ${readSetting};
    const preferencesKey = 'vibecomposer.ui-preferences.v1';
    const presetsKey = 'vibecomposer.track-presets';
    const chancesKey = 'vibecomposer.section-type-settings.v1';
    try {
      for (let index = 0; index < 200 && !document.querySelector('.project-pill.active'); index++) {
        await new Promise(resolve => setTimeout(resolve, 25));
      }
      const workspace = await (${readWorkspace})();
      if (workspace.activeId !== 'original' || workspace.tabs[0]?.past.length !== 1
        || document.querySelector('.project-pill.active')?.textContent.trim() !== 'Version one workspace') {
        throw Error('Version one workspace or history was lost');
      }
      const preferences = await readSetting(preferencesKey);
      if (preferences?.midiPpq !== 240 || preferences.sectionTypeChances.INTRO.bass !== 67
        || preferences.sectionTypeChances.INTRO.melody !== 20 || preferences.sectionTypeChances.INTRO.drums !== 20
        || await readSetting(chancesKey) !== undefined) throw Error('Section chances were not merged into preferences');
      const presets = await readSetting(presetsKey);
      if (presets?.length !== 1 || presets[0].name !== 'Legacy bass' || !presets[0].tracks.length) {
        throw Error('Legacy preset was lost');
      }
      if ([preferencesKey, presetsKey, chancesKey].some(key => localStorage.getItem(key) !== null)) {
        throw Error('Migrated entries remain in localStorage');
      }
      if (!sessionStorage.getItem('settings-upgrade-reloaded')) {
        sessionStorage.setItem('settings-upgrade-reloaded', '1');
        location.reload();
        return;
      }
      await report({ ok: true });
    } catch (error) { await report({ error: error.stack }); }
  })();</script>`;
  await runPersistenceScenario(script, setup);
});

test('an aborted workspace transaction shows a warning and reload recovers the latest active edit', { skip: !browser }, async () => {
  const script = `<script>(async () => {
    const wait = async (read, label) => {
      for (let index = 0; index < 250; index++) { const value = await read(); if (value) return value; await new Promise(resolve => setTimeout(resolve, 20)); }
      throw Error('Timed out: ' + label);
    };
    const readWorkspace = ${readWorkspace};
    const savedSeed = async () => {
      const workspace = await readWorkspace();
      return workspace?.tabs.find(tab => tab.id === workspace.activeId)?.project.seed;
    };
    const sessionSeed = () => JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'))?.seed;
    const warning = () => document.querySelector('.workspace-alerts [role="alert"]')?.textContent;
    const report = result => fetch('/result', { method: 'POST', body: JSON.stringify(result) });
    let abortWrites = sessionStorage.getItem('quota-reload') === 'true';
    const put = IDBObjectStore.prototype.put;
    IDBObjectStore.prototype.put = function(value, key) {
      // Adding an existing key causes a request failure and aborts the transaction.
      if (abortWrites && this.name === 'workspaces' && key === 'current') return this.add(value, key);
      return put.call(this, value, key);
    };
    try {
      const input = await wait(() => document.querySelector('[aria-label="Generation seed"]'), 'seed control');
      const setSeed = value => {
        input.focus();
        input.value = value;
        input.dispatchEvent(new Event('change', { bubbles: true }));
        input.blur();
      };
      if (sessionStorage.getItem('quota-reload')) {
        await wait(() => input.value === '44', 'recovered parameter');
        await wait(() => warning()?.includes('Reloading this tab can recover the active project'), 'warning after reload');
        if (await savedSeed() !== '42' || sessionSeed() !== '44') throw Error('expected the recovered session copy to survive reload');
        abortWrites = false;
        setSeed('45');
        await wait(async () => await savedSeed() === '45' && !warning(), 'successful save clears the warning');
        await report({ ok: true });
        return;
      }
      await wait(savedSeed, 'initial workspace');
      setSeed('42');
      await wait(async () => await savedSeed() === '42', 'parameter saved to IndexedDB');
      abortWrites = true;
      setSeed('43');
      await wait(() => warning()?.includes('Reloading this tab can recover the active project'), 'autosave warning');
      if (await savedSeed() !== '42' || sessionSeed() !== '43') throw Error('expected a newer session copy');
      sessionStorage.setItem('quota-reload', 'true');
      setSeed('44');
      location.reload();
    } catch (error) { await report({ error: error.stack }); }
  })();</script>`;
  await runPersistenceScenario(script);
});

test('legacy tabs migrate with their history and grow past the Web Storage quota', { skip: !browser }, async () => {
  const { service } = fixture();
  const project = JSON.parse(service.exportProjectJson());
  const script = `<script>(async () => {
    const key = 'vibecomposer.project-tabs.v1';
    const reloading = sessionStorage.getItem('large-workspace-reload');
    if (!reloading) {
      const project = ${JSON.stringify({ ...project, name: 'Migrated project', seed: '9223372036854775807' })};
      localStorage.setItem(key, JSON.stringify({ activeId: 'legacy-active', revision: 'legacy-revision', tabs: [
        { id: 'legacy-inactive', project: {...project, name: 'Other tab'}, past: [], future: [], automaticSeed: true },
        { id: 'legacy-active', project, past: [{ ...project, name: 'x'.repeat(2800000) }],
          future: [{ ...project, name: 'Redo state' }], automaticSeed: false }
      ] }));
    }
    const read = ${readWorkspace};
    const wait = async (check, label) => {
      for (let index = 0; index < 250; index++) {
        const value = await check();
        if (value) return value;
        await new Promise(resolve => setTimeout(resolve, 20));
      }
      throw Error('Timed out: ' + label);
    };
    const report = result => fetch('/result', { method: 'POST', body: JSON.stringify(result) });
    try {
      const active = await wait(() => document.querySelector('.project-pill.active'), 'active project');
      const expectedName = reloading ? reloading : 'Migrated project';
      if (active.textContent.trim() !== expectedName) throw Error('active workspace was not restored');
      const migrated = await wait(async () => {
        const saved = await read();
        return saved?.tabs.length === (reloading ? 3 : 2) && !localStorage.getItem(key) && saved;
      }, 'committed migration');
      const tab = migrated.tabs.find(tab => tab.id === migrated.activeId);
      if (tab.project.seed !== '9223372036854775807' || tab.past[0]?.name.length !== 2800000
        || tab.future[0]?.name !== 'Redo state' || tab.automaticSeed
        || !migrated.tabs.find(tab => tab.id === 'legacy-inactive')?.automaticSeed) {
        throw Error('migration or reload changed the workspace state');
      }
      if (reloading) {
        if (JSON.stringify(migrated).length <= 5 * 1024 * 1024) throw Error('workspace must exceed the Web Storage quota');
        await report({ ok: true });
        return;
      }
      document.querySelector('[aria-label="Branch project"]').click();
      const saved = await wait(async () => {
        const workspace = await read();
        return workspace?.tabs.length === 3 && workspace;
      }, 'save large workspace');
      sessionStorage.setItem('large-workspace-reload', saved.tabs.find(tab => tab.id === saved.activeId).project.name);
      // Exercise durable reload without the session copy.
      sessionStorage.removeItem('vibecomposer.project.v12');
      location.reload();
    } catch (error) { await report({ error: error.stack }); }
  })();</script>`;
  await runPersistenceScenario(script);
});
