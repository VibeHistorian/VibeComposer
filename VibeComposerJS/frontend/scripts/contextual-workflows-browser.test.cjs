const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const { spawn } = require('node:child_process');
const test = require('node:test');
const browser = process.env.OVERVIEW_TEST_BROWSER || [
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', '/usr/bin/chromium',
].find(file => fs.existsSync(file));

for (const width of [1440, 780, 390]) test(`production contextual workflows with 30 tracks at ${width}px`, {
  skip: browser ? false : 'Chromium unavailable',
}, async () => {
  const root = path.resolve(__dirname, '../dist/browser');
  const index = fs.readFileSync(path.join(root, 'index.html'), 'utf8');
  const script = `<script>(async () => {
    const wait = async (read, label) => {
      for (let i = 0; i < 300; i++) { const value = read(); if (value) return value; await new Promise(r => setTimeout(r, 50)); }
      throw new Error('Timed out: ' + label);
    };
    const read = () => JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));
    const change = (input, value) => { input.value = value; input.dispatchEvent(new Event('change', { bubbles: true })); };
    try {
      await wait(() => innerWidth === ${width}, 'exact viewport');
      const seed = await wait(() => document.querySelector('[aria-label="Generation seed"]'), 'seed');
      if (!sessionStorage.getItem('contextual-fixture')) {
        change(seed, '9007199254740993');
        const project = await wait(read, 'saved project');
        for (const role of ['melody','bass','chords','arpeggio','drums']) {
          const base = project.tracks.find(track => track.role === role);
          for (let i = 2; i <= 6; i++) project.tracks.push({ ...base, id: 'track-' + role + '-' + i, name: base.name[0] + i,
            generatorSettings: { ...base.generatorSettings, ...(role === 'bass' && i === 2 ? { transpose: 12 } : {}) } });
        }
        project.arrangement[0].rolePartOverrides = { bass: { noteVariation: 35 } };
        project.arrangement[0].trackPartOverrides = { 'track-bass-2': { noteVariation: 45 } };
        sessionStorage.setItem('vibecomposer.project.v12', JSON.stringify(project));
        sessionStorage.setItem('contextual-fixture', 'ready'); location.reload(); return;
      }
      await wait(() => document.querySelectorAll('.track-line').length === 30, '30 track rows');
      // Persist the decoded fixture before comparing a policy-only action.
      change(seed, '9007199254740995');
      await wait(() => read().seed === '9007199254740995', 'canonical project');
      const result = { width: innerWidth };
      const beforeRules = JSON.stringify(read().tracks);
      const melody = document.querySelector('.track-group.melody');
      melody.querySelector('[aria-label="Reroll On Regenerate"]').click();
      await wait(() => read().trackGenerationPolicies?.melody?.onRegenerate, 'regenerate rule');
      result.rulesPreserveTracks = beforeRules === JSON.stringify(read().tracks);
      document.querySelector('[aria-label="Regenerate"]').click();
      await wait(() => read().tracks[0].generatorSettings.patternSeed, 'regenerate seed');
      result.regenerated = read().tracks.filter(track => track.role === 'melody').every(track => Number.isInteger(track.generatorSettings.patternSeed));
      document.querySelector('.track-group.arpeggio [aria-label="Reroll On Compose"]').click();
      const beforeCompose = JSON.stringify(read());
      document.querySelector('[aria-label="Compose"]').click();
      await wait(() => JSON.stringify(read()) !== beforeCompose, 'compose');
      result.composed = read().trackRerollCounts.arpeggio === 1;
      // Traverse the canvas's real keyboard targets, then activate the first Bass cell.
      const canvas = document.querySelector('.arrangement-panel canvas[tabindex]');
      canvas.focus();
      for (let i = 0; i < 100; i++) {
        canvas.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
        await new Promise(r => requestAnimationFrame(r));
        if (canvas.getAttribute('aria-label').startsWith('Select Bass settings in INTRO')) break;
      }
      canvas.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
      const copy = await wait(() => [...document.querySelectorAll('vc-part-scope-actions button')].find(button => button.textContent.trim() === 'Copy to Globals'), 'local cell actions');
      result.keyboardCell = document.querySelector('.part-context-label').textContent.includes('Bass');
      result.mixed = [...document.querySelectorAll('.part-settings-panel [aria-label]')].some(element => element.getAttribute('aria-valuetext')?.includes('Mixed') || element.placeholder === 'Mixed');
      const previous = read(); copy.click();
      await wait(() => read().tracks.find(track => track.id === 'track-bass-1').generatorSettings.noteVariation === 35, 'global copy');
      const after = read();
      result.copied = after.tracks.filter(track => track.role === 'bass').map(track => track.generatorSettings.noteVariation);
      result.localRetained = JSON.stringify(after.arrangement) === JSON.stringify(previous.arrangement);
      result.noopDisabled = await wait(() => copy.disabled, 'copy no-op disabled');
      document.querySelector('[aria-label="Undo"]').click();
      await wait(() => read().tracks.find(track => track.id === 'track-bass-1').generatorSettings.noteVariation === 20, 'copy undo');
      const redo = await wait(() => { const button = document.querySelector('[aria-label="Redo"]'); return button && !button.disabled ? button : null; }, 'redo enabled');
      redo.click();
      await wait(() => read().tracks.find(track => track.id === 'track-bass-1').generatorSettings.noteVariation === 35, 'copy redo');
      document.querySelector('.part-inspector-link').click();
      result.inspectorFocused = document.activeElement.id === 'part-inspector';
      const fits = element => { const box = element.getBoundingClientRect(); return box.left >= -1 && box.right <= innerWidth + 1; };
      result.actionsFit = [...document.querySelectorAll('.scope-actions button, .transport-actions button, .track-inline-select')].every(fits);
      result.trackRowsFit = [...document.querySelectorAll('.track-line')].every(row => {
        const bounds = row.getBoundingClientRect();
        return [...row.children].every(child => { const box = child.getBoundingClientRect(); return box.left >= bounds.left - 1 && box.right <= bounds.right + 1 && box.width >= 8; });
      });
      result.groupNamesVisible = [...document.querySelectorAll('.group-select strong')].every(label => label.clientWidth > 0 && label.scrollWidth <= label.clientWidth + 1);
      result.pageFits = document.documentElement.scrollWidth <= innerWidth + 1;
      result.trackScroll = document.querySelector('.track-groups').scrollHeight > document.querySelector('.track-groups').clientHeight || document.documentElement.scrollHeight > innerHeight;
      await fetch('/result', { method: 'POST', body: JSON.stringify(result) });
    } catch(error) { await fetch('/result', { method: 'POST', body: JSON.stringify({ error: error.message, context: document.querySelector('.part-context-label')?.textContent,
      canvas: document.querySelector('.arrangement-panel canvas[tabindex]')?.getAttribute('aria-label') }) }); }
  })();</script>`;
  let resolve, reject;
  const result = new Promise((done, fail) => { resolve = done; reject = fail; });
  const server = http.createServer((request, response) => {
    if (request.url === '/result') {
      let body = ''; request.on('data', chunk => body += chunk);
      request.on('end', () => { response.end('OK'); try { resolve(JSON.parse(body)); } catch (error) { reject(error); } }); return;
    }
    if (request.url === '/') { response.setHeader('Content-Type', 'text/html'); response.end(index.replace('</body>', script + '</body>')); return; }
    const file = path.resolve(root, '.' + new URL(request.url, 'http://localhost').pathname);
    if (path.relative(root, file).startsWith('..') || !fs.existsSync(file) || !fs.statSync(file).isFile()) { response.writeHead(404); response.end(); return; }
    response.setHeader('Content-Type', file.endsWith('.js') ? 'text/javascript' : file.endsWith('.css') ? 'text/css' : 'application/octet-stream');
    fs.createReadStream(file).pipe(response);
  });
  await new Promise(done => server.listen(0, '127.0.0.1', done));
  const profile = fs.mkdtempSync(path.resolve(__dirname, '../.angular/contextual-browser-'));
  const child = spawn(browser, ['--headless', '--disable-gpu', '--no-first-run', '--disable-background-networking',
    '--disable-background-timer-throttling', '--disable-renderer-backgrounding', '--no-default-browser-check',
    '--remote-debugging-port=0', '--window-size=' + width + ',1000', '--user-data-dir=' + profile, 'http://127.0.0.1:' + server.address().port + '/'], { stdio: ['ignore', 'ignore', 'pipe'] });
  let errors = ''; child.stderr.on('data', chunk => errors = (errors + chunk).slice(-1000));
  child.on('error', reject); child.on('exit', code => reject(new Error('Chromium exited ' + code + ': ' + errors)));
  const timer = setTimeout(() => reject(new Error('Browser timed out: ' + errors)), 45000);
  let socket;
  try {
    const portFile = path.join(profile, 'DevToolsActivePort');
    for (let i = 0; !fs.existsSync(portFile) && i < 100; i++) await new Promise(done => setTimeout(done, 50));
    const port = Number(fs.readFileSync(portFile, 'utf8').split('\n')[0]);
    let target;
    for (let i = 0; !target && i < 100; i++) {
      target = (await (await fetch('http://127.0.0.1:' + port + '/json/list')).json()).find(item => item.type === 'page');
      if (!target) await new Promise(done => setTimeout(done, 50));
    }
    socket = new WebSocket(target.webSocketDebuggerUrl);
    await new Promise((done, fail) => { socket.addEventListener('open', done, { once: true }); socket.addEventListener('error', fail, { once: true }); });
    let commandId = 0;
    const command = (method, params = {}) => new Promise((done, fail) => {
      const id = ++commandId;
      const listener = event => { const message = JSON.parse(event.data); if (message.id !== id) return;
        socket.removeEventListener('message', listener); message.error ? fail(new Error(JSON.stringify(message.error))) : done(message.result); };
      socket.addEventListener('message', listener); socket.send(JSON.stringify({ id, method, params }));
    });
    await command('Emulation.setDeviceMetricsOverride', { width, height: 1000, deviceScaleFactor: 1, mobile: false });
    const actual = await result;
    const screenshot = await command('Page.captureScreenshot', { format: 'png' });
    fs.writeFileSync(path.resolve(__dirname, '../.angular/contextual-' + width + '.png'), Buffer.from(screenshot.data, 'base64'));
    assert.equal(actual.error, undefined, JSON.stringify(actual));
    assert.equal(actual.width, width);
    assert.deepEqual(actual.copied, [35,45,35,35,35,35]);
    for (const key of ['rulesPreserveTracks','regenerated','composed','keyboardCell','mixed','localRetained','noopDisabled','inspectorFocused','actionsFit','trackRowsFit','groupNamesVisible','pageFits','trackScroll']) assert.equal(actual[key], true, key + ': ' + JSON.stringify(actual));
  } finally {
    socket?.close();
    clearTimeout(timer); child.kill(); server.close();
    await new Promise(done => child.exitCode !== null ? done() : child.once('exit', done));
    fs.rmSync(profile, { recursive: true, force: true, maxRetries: 3, retryDelay: 100 });
  }
});
