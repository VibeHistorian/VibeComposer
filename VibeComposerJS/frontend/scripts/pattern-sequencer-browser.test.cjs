const assert = require('node:assert/strict');
const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');
const { spawn } = require('node:child_process');
const test = require('node:test');

const browser = process.env.OVERVIEW_TEST_BROWSER || [
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  '/usr/bin/chromium', '/usr/bin/chromium-browser', '/usr/bin/google-chrome',
].find((file) => fs.existsSync(file));

test('compact pattern popup edits independent drum rows and commits velocities once', {
  skip: browser ? false : 'Set OVERVIEW_TEST_BROWSER to a Chromium executable',
}, async () => {
  const root = path.resolve(__dirname, '../dist/browser');
  const index = fs.readFileSync(path.join(root, 'index.html'), 'utf8');
  const script = `<script>
  (async () => {
    const wait = async (fn, label) => { for(let i=0;i<300;i++){ if(fn()) return; await new Promise(r=>setTimeout(r,30)); } throw Error(label); };
    const read = () => JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));
    const check = (value, label) => { if(!value) throw Error(label); };
    try {
      sessionStorage.clear();
      await wait(()=>document.querySelector('.track-group.drums .group-select'), 'initial');
      document.querySelector('.track-group.drums .group-select').click();
      const count = document.querySelector('input[aria-label="Drums track count"]');
      count.value='3'; count.dispatchEvent(new Event('change',{bubbles:true}));
      await wait(()=>document.querySelectorAll('.track-group.drums .track-select').length===3,'three drums');
      const ids = read().tracks.filter(t=>t.role==='drums').map(t=>t.id);
      document.querySelector('.track-group.drums .track-select').click();
      await wait(()=>document.querySelector('button[aria-label="Edit rhythm pattern"]'),'preview');
      const launcher=document.querySelector('button[aria-label="Edit rhythm pattern"]');
      check(launcher.getBoundingClientRect().width<=100 && launcher.getBoundingClientRect().height<=28, 'tiny preview');
      check(!document.querySelector('.custom-pattern-grid, .velocity-pattern-grid'),'no inline editors');
      launcher.click();
      await wait(()=>document.querySelectorAll('vc-pattern-sequencer .track').length===3,'all drum rows');
      const popup=document.querySelector('vc-pattern-sequencer');
      check(!popup.querySelector('.velocity-pattern-grid'),'velocities hidden');
      const row=id=>popup.querySelector('[data-track-id="'+id+'"]');
      const before=JSON.stringify(read().tracks.find(t=>t.id===ids[0]));
      row(ids[1]).querySelector('input[type="number"]').value='8';
      row(ids[1]).querySelector('input[type="number"]').dispatchEvent(new Event('change',{bubbles:true}));
      await wait(()=>row(ids[1]).querySelectorAll('.step').length===8,'independent steps');
      const original=row(ids[1]).querySelector('.step').getAttribute('aria-pressed');
      row(ids[1]).querySelector('.step').click();
      await wait(()=>row(ids[1]).querySelector('.step').getAttribute('aria-pressed')!==original,'step edit');
      check(JSON.stringify(read().tracks.find(t=>t.id===ids[0]))===before,'other track retained');
      const settings=read().tracks.find(t=>t.id===ids[1]).generatorSettings;
      check(settings.rhythm==='custom' && settings.customPattern.length===32,'bake rhythm');
      popup.querySelector('header button').click();
      await wait(()=>popup.querySelectorAll('.velocity-pattern-grid').length===3,'velocity toggle');
      const velocity=row(ids[1]).querySelector('input[type="range"]');
      velocity.value='22'; velocity.dispatchEvent(new Event('input',{bubbles:true}));
      await wait(()=>row(ids[1]).querySelector('output').textContent==='22','velocity draft');
      check(read().tracks.find(t=>t.id===ids[1]).generatorSettings.customVelocities?.[0]!==22,'no commit during drag');
      velocity.dispatchEvent(new Event('change',{bubbles:true}));
      await wait(()=>read().tracks.find(t=>t.id===ids[1]).generatorSettings.customVelocities?.[0]===22,'velocity commit');
      check(read().tracks.find(t=>t.id===ids[1]).generatorSettings.useCustomVelocities===true,'enable custom velocities');
      row(ids[1]).querySelector('input[type="number"]').value='4';
      row(ids[1]).querySelector('input[type="number"]').dispatchEvent(new Event('change',{bubbles:true}));
      await wait(()=>row(ids[1]).querySelectorAll('.step').length===4,'shrink');
      check(read().tracks.find(t=>t.id===ids[1]).generatorSettings.customVelocities.length===32,'hidden velocities retained');
      popup.querySelector('button[aria-label="Close pattern sequencer"]').click();
      await wait(()=>!document.querySelector('vc-pattern-sequencer'),'close');
      document.querySelector('.project-tools button[aria-label="Undo"]').click();
      await wait(()=>read().tracks.find(t=>t.id===ids[1]).generatorSettings.hitsPerPattern===8,'undo hits');
      document.querySelector('.project-tools button[aria-label="Undo"]').click();
      await wait(()=>read().tracks.find(t=>t.id===ids[1]).generatorSettings.customVelocities?.[0]!==22,'one undo velocity');
      await fetch('/result',{method:'POST',body:JSON.stringify({passed:true})});
    } catch(error){await fetch('/result',{method:'POST',body:JSON.stringify({error:error.message})});}
  })();</script>`;
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
  const profile = fs.mkdtempSync(path.resolve(__dirname, '../.angular/pattern-sequencer-browser-'));
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
    assert.equal(actual.error, undefined, JSON.stringify(actual));
    assert.equal(actual.passed, true);
  } finally {
    clearTimeout(timeout); child.kill(); server.close();
    // Remove only the fresh profile created by this test after Chromium exits.
    await new Promise(done => child.exitCode !== null ? done() : child.once('exit', done));
    fs.rmSync(profile, { recursive: true, force: true, maxRetries: 3, retryDelay: 100 });
  }
});
