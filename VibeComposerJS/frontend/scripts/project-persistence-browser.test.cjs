const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const { findBrowser, requireProductionBuild, createBrowserServer, listen, launchBrowser, closeBrowser, watchBrowser } = require('./support/browser-harness.cjs');

const browser = findBrowser();
async function runPersistenceScenario(script) {
  const root = requireProductionBuild();
  let resolve, reject;
  const result = new Promise((done, fail) => { resolve = done; reject = fail; });
  result.catch(() => {});
  const html = fs.readFileSync(path.join(root, 'index.html'), 'utf8').replace('</body>', script + '</body>');
  const server = createBrowserServer({ root, html, resolve, reject });
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
      for (let index = 0; index < 250; index++) { const value = read(); if (value) return value; await new Promise(resolve => setTimeout(resolve, 20)); }
      throw Error('Timed out: ' + label);
    };
    const ensure = (value, label) => { if (!value) throw Error(label); };
    const pill = () => document.querySelector('button.project-pill.active');
    const project = () => JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));
    const workspace = () => JSON.parse(localStorage.getItem('vibecomposer.project-tabs.v1'));
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
      await wait(() => project() && workspace(), 'initial persistence');
      sessionStorage.setItem('persistence-original-name', pill().textContent.trim());
      let sessionWrites = 0, workspaceWrites = 0;
      const setItem = Storage.prototype.setItem;
      Storage.prototype.setItem = function(key, value) {
        if (key === 'vibecomposer.project.v12') sessionWrites++;
        if (key === 'vibecomposer.project-tabs.v1') workspaceWrites++;
        return setItem.call(this, key, value);
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
      await wait(() => project().name === 'Deferred save' && workspace().tabs.some(tab => tab.project.name === 'Deferred save'), 'deferred save');
      ensure(sessionWrites === 1 && workspaceWrites === 1, 'one write to each store');
      await rename('Pending reload save');
      sessionStorage.setItem('persistence-reload', 'true');
      location.reload();
    } catch (error) { await fetch('/result', { method: 'POST', body: JSON.stringify({ error: error.stack }) }); }
  })();</script>`;
  await runPersistenceScenario(script);
});

test('workspace quota failure shows a warning and reload recovers the latest committed parameter', { skip: !browser }, async () => {
  const script = `<script>(async () => {
    const wait = async (read, label) => {
      for (let index = 0; index < 250; index++) { const value = read(); if (value) return value; await new Promise(resolve => setTimeout(resolve, 20)); }
      throw Error('Timed out: ' + label);
    };
    const savedSeed = () => {
      const workspace = JSON.parse(localStorage.getItem('vibecomposer.project-tabs.v1'));
      return workspace?.tabs.find(tab => tab.id === workspace.activeId)?.project.seed;
    };
    const sessionSeed = () => JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'))?.seed;
    const warning = () => document.querySelector('.workspace-alerts [role="alert"]')?.textContent;
    const report = result => fetch('/result', { method: 'POST', body: JSON.stringify(result) });
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
        if (savedSeed() !== '42' || sessionSeed() !== '44') throw Error('expected the recovered session copy to survive reload');
        localStorage.removeItem('quota-padding');
        setSeed('45');
        await wait(() => savedSeed() === '45' && !warning(), 'successful save clears the warning');
        await report({ ok: true });
        return;
      }
      await wait(() => savedSeed() !== undefined, 'initial workspace');
      setSeed('42');
      await wait(() => savedSeed() === '42', 'parameter saved to localStorage');
      let low = 0, high = 8 * 1024 * 1024;
      while (low + 1 < high) {
        const size = Math.floor((low + high) / 2);
        try {
          localStorage.setItem('quota-padding', 'x'.repeat(size));
          low = size;
        } catch (error) {
          if (error.name !== 'QuotaExceededError') throw error;
          high = size;
        }
      }
      setSeed('43');
      await wait(() => warning()?.includes('Reloading this tab can recover the active project'), 'autosave warning');
      if (savedSeed() !== '42' || sessionSeed() !== '43') throw Error('expected a newer session copy');
      sessionStorage.setItem('quota-reload', 'true');
      setSeed('44');
      location.reload();
    } catch (error) { await report({ error: error.stack }); }
  })();</script>`;
  await runPersistenceScenario(script);
});
