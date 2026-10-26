// Runs entirely inside the agent sandbox against the separately started broker.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const http = require('node:http');
const test = require('node:test');
const { once } = require('node:events');
const { brokerBrowser, connectionFile } = require('../../VibeComposerJS/frontend/scripts/support/browser-broker.cjs');
const { createBrowserServer, listen, launchBrowser, connectToPage, closeBrowser, readBrowserDownload } =
  require('../../VibeComposerJS/frontend/scripts/support/browser-harness.cjs');

test('protected broker supports the suites and rejects filesystem, process, and network escape routes', { timeout: 45000 }, async () => {
  const connection = JSON.parse(fs.readFileSync(connectionFile, 'utf8'));
  const request = (route, body, headers = {}) => fetch(connection.url + route, {
    method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + connection.token, ...headers }, body: JSON.stringify(body),
  });
  for (const args of [
    ['--renderer-cmd-prefix=pwsh.exe', 'http://127.0.0.1:54000/'],
    ['--load-extension=C:/evil', 'http://127.0.0.1:54000/'],
    ['--no-sandbox', 'http://127.0.0.1:54000/'],
    ['--headless', 'file:///C:/Windows/win.ini'],
  ]) {
    const response = await request('/launch', { args });
    assert.equal(response.status, 400, await response.text());
  }
  const csrf = await request('/launch', {}, { Origin: 'http://evil.example' });
  assert.equal(csrf.status, 403); await csrf.text();
  let outsideRequests = 0;
  const outside = http.createServer((req, res) => { outsideRequests++; res.end('outside'); });
  outside.on('upgrade', (_req, socket) => { outsideRequests++; socket.destroy(); });
  outside.listen(0, '127.0.0.1');
  await once(outside, 'listening');
  const server = createBrowserServer({ html: '<!doctype html><title>Protected browser</title><button id="probe">Probe</button>' });
  await listen(server);
  const child = launchBrowser(brokerBrowser, ['--headless', '--disable-gpu', '--window-size=1440,1100',
    '--remote-debugging-port=0', 'http://127.0.0.1:' + server.address().port]);
  let socket;
  try {
    const connected = await connectToPage(child); socket = connected.socket;
    const { command } = connected;
    const page = await command('Runtime.evaluate', { expression: '(async () => { for (let i=0;i<100;i++) { if(document.querySelector("#probe")) return document.querySelector("#probe").textContent; await new Promise(r=>setTimeout(r,20)); } })()', awaitPromise: true, returnByValue: true });
    assert.equal(page.result.value, 'Probe', 'the fixture page must actually load');
    const workerSource = `(async () => {
      const fixture = await fetch('http://127.0.0.1:${server.address().port}/').then(r => r.text()).then(text => text.includes('Probe'));
      const blocked = await fetch('http://127.0.0.1:${outside.address().port}/').then(() => false, () => true);
      postMessage({ fixture, blocked });
    })()`;
    const worker = await command('Runtime.evaluate', { expression: `new Promise(resolve => {
      const worker = new Worker(URL.createObjectURL(new Blob([${JSON.stringify(workerSource)}], { type: 'text/javascript' })));
      worker.onmessage = e => { worker.terminate(); resolve(e.data); };
      worker.onerror = e => { worker.terminate(); resolve('error: '+e.message); };
      setTimeout(() => { worker.terminate(); resolve('worker timed out'); }, 3000);
    })`, awaitPromise: true, returnByValue: true });
    assert.deepEqual(worker.result.value, { fixture: true, blocked: true }, 'workers must resume with the same network boundary: ' + child.testStderr);
    const value = await command('Runtime.evaluate', { expression: '1+2', returnByValue: true });
    assert.equal(value.result.value, 3);
    await command('Page.enable');
    const loaded = new Promise(resolve => {
      socket.addEventListener('message', function listener(event) {
        if (JSON.parse(event.data).method === 'Page.loadEventFired') { socket.removeEventListener('message', listener); resolve(); }
      });
    });
    await command('Page.reload');
    await loaded;
    for (const method of ['DOM.setFileInputFiles', 'Browser.setDownloadBehavior', 'Page.navigate', 'Target.createTarget', 'IO.read']) {
      await assert.rejects(command(method, {}), /403/);
    }
    const blocked = await command('Runtime.evaluate', { expression: `(async () => {
      const file = await fetch('file:///C:/Windows/win.ini').then(() => false, () => true);
      const other = await fetch('http://127.0.0.1:${outside.address().port}/').then(() => false, () => true);
      const ws = await new Promise(resolve => {
        const socket = new WebSocket('ws://127.0.0.1:${outside.address().port}/');
        socket.onopen = () => { socket.close(); resolve(false); };
        socket.onerror = () => resolve(true);
        setTimeout(() => { socket.close(); resolve(true); }, 2000);
      });
      return { file, other, ws };
    })()`, awaitPromise: true, returnByValue: true });
    assert.equal(blocked.exceptionDetails, undefined, JSON.stringify(blocked));
    assert.deepEqual(blocked.result.value, { file: true, other: true, ws: true });
    assert.equal(outsideRequests, 0, 'browser must not contact a different loopback service');
    await command('Runtime.evaluate', { expression: `(() => {
      const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob(['{"ok":true}'], { type: 'application/json' }));
      a.download = 'broker-verification.json'; document.body.append(a); a.click();
    })()` });
    assert.deepEqual(JSON.parse(await readBrowserDownload(child, 'broker-verification.json')), { ok: true });
    const screenshot = await command('Page.captureScreenshot', { format: 'png' });
    assert.ok(Buffer.from(screenshot.data, 'base64').length > 100);
  } finally {
    outside.closeAllConnections(); outside.close();
    await closeBrowser({ child, server, socket });
  }
});
