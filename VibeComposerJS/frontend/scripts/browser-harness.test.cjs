const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const { EventEmitter, once } = require('node:events');
const test = require('node:test');
const { createBrowserServer, listen, launchBrowser, watchBrowser, closeBrowser,
  requireProductionBuild, connectToPage, preflightBrowser } = require('./support/browser-harness.cjs');
const { silenceBrowserAudio } = require('./support/browser-audio.cjs');

test('audio isolation uses a silent sink or a final zero-gain boundary without altering offline output', () => {
  for (const mode of ['silent-sink', 'ignored-option', 'rejected-option']) {
    const gains = [];
    class AudioNode {
      constructor(context) { this.context = context; this.connections = []; }
      connect(destination, ...ports) { this.connections.push({ destination, ports }); return destination; }
      disconnect(...args) { this.disconnected = args; }
    }
    class NativeContext {
      constructor(options) {
        if (mode === 'rejected-option' && options?.sinkId) throw new TypeError('Unsupported sink');
        this.options = options;
        this.destination = new AudioNode(this);
        if (mode === 'silent-sink') this.sinkId = options.sinkId;
      }
      createGain() { const node = new AudioNode(this); node.gain = { value: 1 }; gains.push(node); return node; }
    }
    class OfflineAudioContext {}
    const sandbox = { AudioContext: NativeContext, AudioNode, OfflineAudioContext };
    require('node:vm').runInNewContext('(' + silenceBrowserAudio.toString() + ')()', sandbox);
    const options = { sampleRate: 22050 };
    const context = new sandbox.AudioContext(options);
    assert.ok(context instanceof NativeContext);
    assert.ok(context instanceof sandbox.AudioContext);
    assert.equal(context.options.sampleRate, 22050);
    assert.equal(options.sinkId, undefined, 'caller options are not mutated');
    const source = new AudioNode(context);
    assert.equal(source.connect(context.destination, 1, 0), context.destination, 'connect retains its return value');
    assert.deepEqual(source.connections[0].ports, [1, 0]);
    source.disconnect(context.destination, 1, 0);
    assert.equal(source.disconnected[0], source.connections[0].destination, 'disconnect uses the same safe route as connect');
    source.disconnect(0);
    assert.deepEqual(source.disconnected, [0], 'numeric disconnect overload is preserved');
    if (mode === 'silent-sink') {
      assert.equal(context.sinkId.type, 'none');
      assert.equal(gains.length, 0, 'native silent output needs no gain modification');
      assert.equal(source.connections[0].destination, context.destination);
    } else {
      assert.equal(gains.length, 1);
      assert.equal(gains[0].gain.value, 0, 'fallback is fully muted, not just quiet');
      assert.equal(source.connections[0].destination, gains[0]);
      assert.equal(gains[0].connections[0].destination, context.destination);
    }
    const offlineDestination = new AudioNode({ offline: true });
    source.connect(offlineDestination);
    assert.equal(source.connections.at(-1).destination, offlineDestination);
    assert.equal(sandbox.OfflineAudioContext, OfflineAudioContext);
    class ChildContext extends sandbox.AudioContext {}
    assert.ok(new ChildContext(options) instanceof ChildContext, 'context subclassing still works');
  }
});

test('audio isolation precedes fixture scripts while preserving the doctype and production helpers', async () => {
  const html = '<!doctype html><html><head><script>window.fixtureStarted = true</script></head><body>fixture</body></html>';
  const server = createBrowserServer({ html });
  await listen(server);
  try {
    const page = await (await fetch(`http://127.0.0.1:${server.address().port}/`)).text();
    assert.ok(page.startsWith('<!doctype html>'));
    assert.ok(page.indexOf('silenceBrowserAudio') < page.indexOf('window.fixtureStarted'));
  } finally {
    await new Promise(resolve => { server.close(resolve); server.closeAllConnections(); });
  }
});

test('browser server serves injected pages, assets and JSON results; rejects malformed results and escaped paths', async () => {
  const parent = path.resolve(__dirname, '../.angular');
  fs.mkdirSync(parent, { recursive: true });
  const fixture = fs.mkdtempSync(path.join(parent, 'harness-test-'));
  const root = path.join(fixture, 'browser');
  fs.mkdirSync(root);
  fs.writeFileSync(path.join(root, 'index.html'), '<p>production</p>');
  fs.writeFileSync(path.join(root, 'main.js'), 'window.loaded = true;');
  fs.writeFileSync(path.join(root, 'font.woff2'), Buffer.from('wOF2'));
  fs.writeFileSync(path.join(fixture, 'secret'), 'outside');
  let result, failure;
  const server = createBrowserServer({ root, html: '<p>injected</p>',
    resolve: value => { result = value; }, reject: error => { failure = error; } });
  await listen(server);
  const url = `http://127.0.0.1:${server.address().port}`;
  try {
    const page = await (await fetch(url)).text();
    assert.ok(page.includes('silenceBrowserAudio'));
    assert.ok(page.endsWith('<p>injected</p>'));
    const asset = await fetch(url + '/main.js?v=1');
    assert.equal(asset.headers.get('content-type'), 'text/javascript');
    assert.equal(await asset.text(), 'window.loaded = true;');
    const font = await fetch(url + '/font.woff2');
    assert.equal(font.headers.get('content-type'), 'font/woff2');
    assert.equal(await font.text(), 'wOF2');
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
    /Run pnpm run build from frontend\/ first/);
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

test('a lost broker connection still closes the fixture server and removes local artifacts', async () => {
  const parent = path.resolve(__dirname, '../.angular');
  fs.mkdirSync(parent, { recursive: true });
  const profile = fs.mkdtempSync(path.join(parent, 'broker-cleanup-'));
  const server = createBrowserServer({ html: 'fixture' });
  await listen(server);
  const child = { testBroker: true, testProfile: profile,
    closeBroker: async () => { throw new Error('broker unavailable'); } };
  await assert.rejects(closeBrowser({ child, server }), /broker unavailable/);
  assert.equal(server.listening, false);
  assert.equal(fs.existsSync(profile), false);
});

test('debugging startup failures retain browser stderr and preflight cleans up a failed launch', async () => {
  await assert.rejects(connectToPage({ exitCode: 1, signalCode: null, testStderr: 'GPU exit code 0xC0000022' }),
    /Chromium exited before debugging connected\nGPU exit code 0xC0000022/);
  const root = path.resolve(__dirname, '../.angular');
  const profiles = () => fs.readdirSync(root).filter(name => name.startsWith('browser-harness-')).sort();
  const before = profiles();
  await assert.rejects(preflightBrowser(path.join(__dirname, 'missing-chromium-executable')), /Browser preflight failed.*\n.*ENOENT/);
  assert.deepEqual(profiles(), before, 'failed preflight removes its temporary browser profile');
});
