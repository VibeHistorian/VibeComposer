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

test('production Angular rhythm pickers expose the catalogue and selecting custom opens both editable grids', {
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
  } finally {
    clearTimeout(timeout); child.kill(); server.close();
    // Remove only the fresh profile created by this test after Chromium exits.
    await new Promise(done => child.exitCode !== null ? done() : child.once('exit', done));
    fs.rmSync(profile, { recursive: true, force: true, maxRetries: 3, retryDelay: 100 });
  }
});
