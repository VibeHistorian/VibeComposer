const assert = require('node:assert/strict');
const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');
const { spawn } = require('node:child_process');
const test = require('node:test');
const ts = require('typescript');

const browser = process.env.OVERVIEW_TEST_BROWSER || [
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  '/usr/bin/chromium', '/usr/bin/chromium-browser', '/usr/bin/google-chrome',
].find((file) => fs.existsSync(file));

test('production Angular controls support custom rhythm, wheel edits, vertical mix dragging and bright mute/solo states', {
  skip: browser ? false : 'Set OVERVIEW_TEST_BROWSER to a Chromium executable',
}, async () => {
  const root = path.resolve(__dirname, '../dist/browser');
  const source = fs.readFileSync(path.join(__dirname, '../src/app/core/music/rhythm-patterns.ts'), 'utf8');
  const module = { exports: {} };
  new Function('exports', ts.transpileModule(source, { compilerOptions: {
    target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS,
  } }).outputText)(module.exports);
  const expected = module.exports.RHYTHM_PATTERNS;
  const index = fs.readFileSync(path.join(root, 'index.html'), 'utf8');
  const script = `<script>
    (async () => {
      const waitFor = async read => {
        for (let attempt = 0; attempt < 300; attempt++) {
          const result = read(); if (result) return result;
          await new Promise(resolve => setTimeout(resolve, 50));
        }
        throw new Error('Angular UI did not reach expected state');
      };
      try {
        sessionStorage.clear();
        const group = await waitFor(() => document.querySelector('.track-group.chords .group-select'));
        group.click();
        const pickers = await waitFor(() => {
          const result = [...document.querySelectorAll('select[aria-label="Rhythm pattern"]')];
          return result.length === 2 ? result : null;
        });
        const options = pickers.map(select => [...select.options].map(option => option.value));
        pickers[0].value = 'custom';
        pickers[0].dispatchEvent(new Event('change', { bubbles: true }));
        const grids = await waitFor(() => {
          const result = [...document.querySelectorAll('.custom-pattern-grid')];
          return result.length === 2 ? result : null;
        });
        const counts = grids.map(grid => grid.querySelectorAll('button').length);
        grids[0].querySelector('button').click();
        await waitFor(() => [...document.querySelectorAll('.custom-pattern-grid button:first-child')]
          .every(button => button.getAttribute('aria-pressed') === 'false'));
        const project = JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));
        const result = { options, counts, selected: pickers.map(select => select.value),
          rhythm: project.chords.rhythm, grid: project.chords.customPattern };
        const wheel = (element, deltaY, shiftKey = false) => element.dispatchEvent(
          new WheelEvent('wheel', { deltaY, shiftKey, bubbles: true, cancelable: true }));
        wheel(pickers[0], 100);
        await waitFor(() => pickers.every(select => select.value === 'full'));
        result.wheelRhythm = pickers.map(select => select.value);
        const track = document.querySelector('.track-group.chords .track-row-card');
        const pan = track.querySelector('input[aria-label="Pan C1"]');
        const volume = track.querySelector('input[aria-label="Volume C1"]');
        wheel(pan, -100, true);
        await waitFor(() => pan.value === '1');
        // Synthetic pointer events exercise Angular's actual handlers and signals;
        // unit coverage separately checks pointer capture and vertical-only movement.
        volume.setPointerCapture = () => {};
        const pointer = (type, y) => volume.dispatchEvent(new PointerEvent(type,
          { pointerId: 17, button: 0, clientY: y, bubbles: true, cancelable: true }));
        pointer('pointerdown', 100); pointer('pointermove', 175);
        await waitFor(() => volume.closest('.compact-knob').querySelector('output').textContent.trim() === '-6.0 dB');
        result.volumeBeforeRelease = JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')).mix.chords.volumePercent;
        pointer('pointerup', 175);
        await waitFor(() => volume.value === '50');
        track.querySelector('.mute-toggle').click(); track.querySelector('.solo-toggle').click();
        await waitFor(() => track.querySelector('.mute-toggle').classList.contains('active')
          && track.querySelector('.solo-toggle').classList.contains('active'));
        result.quickMix = JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')).mix.chords;
        result.panLabel = pan.closest('.compact-knob').querySelector('output').textContent.trim();
        result.colors = ['.mute-toggle', '.solo-toggle'].map(selector => getComputedStyle(track.querySelector(selector)).backgroundColor);
        const bounds = track.getBoundingClientRect();
        result.controlsFit = track.querySelector('.track-mix-controls').getBoundingClientRect().right <= bounds.right;
        result.groupStillSelected = group.getAttribute('aria-pressed');
        await fetch('/result', { method: 'POST', body: JSON.stringify(result) });
      } catch (error) {
        await fetch('/result', { method: 'POST', body: JSON.stringify({ error: error.message }) });
      }
    })();
  </script>`;
  let resolve, reject;
  const result = new Promise((done, fail) => { resolve = done; reject = fail; });
  const server = http.createServer((request, response) => {
    if (request.url === '/result') {
      let body = '';
      request.on('data', chunk => { body += chunk; });
      request.on('end', () => {
        response.end('OK');
        try { resolve(JSON.parse(body)); } catch (error) { reject(error); }
      });
      return;
    }
    const pathname = new URL(request.url, 'http://localhost').pathname;
    if (pathname === '/') {
      response.setHeader('Content-Type', 'text/html');
      response.end(index.replace('</body>', script + '</body>'));
      return;
    }
    const file = path.resolve(root, '.' + pathname);
    if (path.relative(root, file).startsWith('..') || !fs.existsSync(file) || !fs.statSync(file).isFile()) {
      response.writeHead(404); response.end(); return;
    }
    response.setHeader('Content-Type', file.endsWith('.js') ? 'text/javascript'
      : file.endsWith('.css') ? 'text/css' : 'application/octet-stream');
    fs.createReadStream(file).pipe(response);
  });
  await new Promise(done => server.listen(0, '127.0.0.1', done));
  const profile = fs.mkdtempSync(path.resolve(__dirname, '../.angular/rhythm-picker-browser-'));
  const child = spawn(browser, ['--headless', '--disable-gpu', '--no-first-run', '--disable-background-networking',
    '--disable-background-timer-throttling', '--disable-renderer-backgrounding', '--no-default-browser-check',
    '--user-data-dir=' + profile, 'http://127.0.0.1:' + server.address().port + '/'], { stdio: ['ignore', 'ignore', 'pipe'] });
  let errors = '';
  child.stderr.on('data', chunk => { errors = (errors + chunk).slice(-2000); });
  child.on('error', reject);
  child.on('exit', code => reject(new Error('Chromium exited (' + code + '): ' + errors)));
  const timeout = setTimeout(() => reject(new Error('Chromium timed out: ' + errors)), 30000);
  try {
    const actual = await result;
    assert.equal(actual.error, undefined);
    assert.deepEqual(actual.options, [expected, expected]);
    assert.deepEqual(actual.counts, [8, 8]);
    assert.deepEqual(actual.selected, ['custom', 'custom']);
    assert.equal(actual.rhythm, 'custom');
    assert.equal(actual.grid.length, 32);
    assert.deepEqual(actual.grid.slice(0, 8), [0, 1, 1, 1, 1, 1, 1, 1]);
    assert.deepEqual(actual.wheelRhythm, ['full', 'full']);
    assert.equal(actual.volumeBeforeRelease, 100);
    assert.equal(actual.quickMix.volumePercent, 50);
    assert.equal(actual.quickMix.panPercent, 1);
    assert.equal(actual.panLabel, '1% R');
    assert.deepEqual(actual.colors, ['rgb(255, 255, 50)', 'rgb(145, 255, 40)']);
    assert.equal(actual.controlsFit, true);
    assert.equal(actual.groupStillSelected, 'true');
  } finally {
    clearTimeout(timeout); child.kill(); server.close();
    // Remove only the fresh profile created by this test after Chromium exits.
    await new Promise(done => child.exitCode !== null ? done() : child.once('exit', done));
    fs.rmSync(profile, { recursive: true, force: true, maxRetries: 3, retryDelay: 100 });
  }
});
