const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const { EventEmitter, once } = require('node:events');
const test = require('node:test');
const { createBrowserServer, listen, launchBrowser, watchBrowser, closeBrowser,
  requireProductionBuild } = require('./support/browser-harness.cjs');

test('browser server serves injected pages, assets and JSON results; rejects malformed results and escaped paths', async () => {
  const parent = path.resolve(__dirname, '../.angular');
  fs.mkdirSync(parent, { recursive: true });
  const fixture = fs.mkdtempSync(path.join(parent, 'harness-test-'));
  const root = path.join(fixture, 'browser');
  fs.mkdirSync(root);
  fs.writeFileSync(path.join(root, 'index.html'), '<p>production</p>');
  fs.writeFileSync(path.join(root, 'main.js'), 'window.loaded = true;');
  fs.writeFileSync(path.join(fixture, 'secret'), 'outside');
  let result, failure;
  const server = createBrowserServer({ root, html: '<p>injected</p>',
    resolve: value => { result = value; }, reject: error => { failure = error; } });
  await listen(server);
  const url = `http://127.0.0.1:${server.address().port}`;
  try {
    assert.equal(await (await fetch(url)).text(), '<p>injected</p>');
    const asset = await fetch(url + '/main.js?v=1');
    assert.equal(asset.headers.get('content-type'), 'text/javascript');
    assert.equal(await asset.text(), 'window.loaded = true;');
    for (const route of ['/missing', '/%2e%2e%2fsecret', '/%2e%2e%5csecret']) {
      const response = await fetch(url + route);
      assert.equal(response.status, 404, route);
      await response.text();
    }
    await (await fetch(url + '/result', { method: 'POST', body: '{"ok":true}' })).text();
    assert.deepEqual(result, { ok: true });
    const invalid = await fetch(url + '/result', { method: 'POST', body: '{broken' });
    assert.equal(invalid.status, 400);
    await invalid.text();
    assert.ok(failure instanceof SyntaxError);
  } finally {
    await new Promise(resolve => { server.close(resolve); server.closeAllConnections(); });
    await fs.promises.rm(fixture, { recursive: true, force: true });
  }
});

test('missing production build gives an actionable prerequisite', () => {
  assert.throws(() => requireProductionBuild(path.join(__dirname, 'missing-production-build')),
    /Run npm run build from frontend\/ first/);
});

test('watchdog reports stderr on timeout and early exit and detaches listeners', async () => {
  const child = new EventEmitter();
  child.stderr = new EventEmitter();
  let fail;
  const failed = new Promise(resolve => { fail = resolve; });
  const watchdog = watchBrowser(child, fail, 10);
  child.stderr.emit('data', 'GPU startup failed');
  assert.match((await failed).message, /timed out.*GPU startup failed/);
  watchdog.stop();
  assert.equal(child.listenerCount('exit'), 0);
  assert.equal(child.stderr.listenerCount('data'), 0);
  let exitError;
  const exited = watchBrowser(child, error => { exitError = error; }, 1000);
  child.emit('exit', 7, null);
  assert.match(exitError.message, /Chromium exited \(7, null\)/);
  exited.stop();
});

test('failed Chromium launch still closes the server and removes its isolated profile', async () => {
  const server = createBrowserServer({ html: 'fixture' });
  await listen(server);
  const child = launchBrowser(path.join(__dirname, 'missing-chromium-executable'), []);
  const [error] = await once(child, 'error');
  assert.equal(error.code, 'ENOENT');
  const profile = child.testProfile;
  assert.ok(fs.existsSync(profile));
  await closeBrowser({ child, server });
  assert.equal(server.listening, false);
  assert.equal(fs.existsSync(profile), false);
});

test('aggregate discovery covers every top-level suite without duplicating generation tests', () => {
  const list = group => execFileSync(process.execPath, [path.join(__dirname, 'run-tests.cjs'), group, '--list'],
    { encoding: 'utf8' }).trim().split(/\r?\n/).map(file => path.basename(file));
  const unit = list('unit'), browser = list('browser');
  const all = fs.readdirSync(__dirname).filter(file => file.endsWith('.test.cjs')).sort();
  assert.deepEqual([...unit, ...browser, 'parts-generation.test.cjs'].sort(), all);
  assert.ok(unit.includes('part-settings.test.cjs'));
  assert.ok(!unit.includes('parts-generation.test.cjs'));
  assert.deepEqual(list('overview-full'), ['arrangement-overview-browser.test.cjs']);
});
