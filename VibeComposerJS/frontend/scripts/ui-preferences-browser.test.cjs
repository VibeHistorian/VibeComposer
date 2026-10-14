const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const { spawn } = require('node:child_process');
const test = require('node:test');
const browser = process.env.OVERVIEW_TEST_BROWSER || ['C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', '/usr/bin/chromium'].find(file => fs.existsSync(file));

for (const width of [1440, 390]) test(`quick-settings placement and UI preference transfer at ${width}px`, { skip: !browser }, async () => {
  const root = path.resolve(__dirname, '../dist/browser');
  const index = fs.readFileSync(path.join(root, 'index.html'), 'utf8');
  const script = `<script>(async () => {
    const wait = async (read, label) => {
      for (let i=0;i<200;i++) { const value=read(); if(value) return value; await new Promise(r=>setTimeout(r,30)); }
      throw new Error('Timed out: '+label);
    };
    const ensure = (value, label) => { if(!value) throw new Error(label); };
    const button = label => [...document.querySelectorAll('button')].find(b=>b.textContent.trim()===label);
    const key = 'vibecomposer.ui-preferences.v1';
    const quick = () => document.querySelector('.part-settings-content vc-part-settings-editor');
    const inspector = () => document.querySelector('#part-inspector vc-part-settings-editor');
    const control = (editor, key) => editor.querySelector('[data-control-key="'+key+'"]');
    try {
      await wait(()=>innerWidth===${width},'viewport');
      await wait(()=>button('Edit quick settings'),'workspace');
      if (sessionStorage.getItem('ui-preferences-reloaded')) {
        ensure(control(quick(),'feedbackCount'), 'persisted placement after reload');
        ensure(!control(quick(),'transpose'), 'removed quick control after reload');
      } else {
        const seed=await wait(()=>document.querySelector('[aria-label="Generation seed"]'),'seed');
        seed.value='123'; seed.dispatchEvent(new Event('change',{bubbles:true}));
        await wait(()=>sessionStorage.getItem('vibecomposer.project.v12'),'project saved');
        const before=sessionStorage.getItem('vibecomposer.project.v12');
        button('Edit quick settings').click();
        await wait(()=>button('Save quick settings'),'edit mode');
        ensure(document.querySelector('.workspace-canvas.quick-layout-editing'),'focused workspace');
        const target=control(quick(),'transpose').querySelector('.placement-target');
        target.focus();
        ensure(getComputedStyle(target).boxShadow!=='none','focus glow');
        ensure(control(quick(),'transpose').querySelector('.control-content').inert,'parameter subtree inert');
        target.click();
        await wait(()=>control(inspector(),'transpose'),'move quick to inspector');
        control(inspector(),'feedbackCount').querySelector('.placement-target').click();
        await wait(()=>control(quick(),'feedbackCount'),'move inspector to quick');
        ensure(sessionStorage.getItem('vibecomposer.project.v12')===before,'placement changed musical project');
        ensure(!localStorage.getItem(key),'draft persisted before save');
        button('Save quick settings').click();
        await wait(()=>button('Edit quick settings'),'save mode');
        ensure(JSON.parse(localStorage.getItem(key)).quickSettings.bass.includes('feedbackCount'),'saved preference');
        const areas=quick().querySelectorAll('.part-control-area');
        ensure([...areas].every(area=>getComputedStyle(area).display==='contents'),'quick categories must flow');
        ensure([...inspector().querySelectorAll('.part-control-area')].every(area=>getComputedStyle(area).display!=='contents'),'inspector category areas');
        const velocities=[...quick().querySelectorAll('[data-control-key]')].map(c=>c.dataset.controlKey).filter(k=>k.startsWith('velocity'));
        ensure(velocities.join(',')==='velocityMin,velocityMax','fixed velocity order');
        button('Settings').click();
        const dialog=await wait(()=>document.querySelector('vc-settings-dialog'),'settings dialog');
        const footer=dialog.querySelector('.preferences-transfer');
        ensure(footer.querySelectorAll('button').length===2,'transfer buttons');
        let exported;
        const create=URL.createObjectURL;
        URL.createObjectURL=blob=>{exported=blob;return create(blob);};
        button('Export UI preferences').click();
        const json=await exported.text();
        ensure(JSON.parse(json).kind==='vibecomposer-ui-preferences','UI export kind');
        ensure(!JSON.parse(json).tracks,'project excluded from UI export');
        const fileInput=footer.querySelector('input');
        const upload=async text=>{
          const data=new DataTransfer(); data.items.add(new File([text],'preferences.json',{type:'application/json'}));
          fileInput.files=data.files; fileInput.dispatchEvent(new Event('change',{bubbles:true}));
        };
        await upload(before);
        await wait(()=>footer.textContent.includes('Invalid UI preferences'),'reject project as UI preferences');
        ensure(localStorage.getItem(key)===JSON.stringify(JSON.parse(json)),'invalid import changed preferences');
        await upload(json);
        await wait(()=>footer.textContent.includes('UI preferences imported.'),'valid UI import');
        ensure(sessionStorage.getItem('vibecomposer.project.v12')===before,'UI import changed musical project');
        dialog.querySelector('[aria-label="Close settings"]').click();
        button('Edit quick settings').click();
        await wait(()=>button('Reset to default'),'reset button');
        button('Reset to default').click();
        await wait(()=>control(quick(),'transpose')&&!control(quick(),'feedbackCount'),'reset predefined defaults');
        // Reapply customized placement and verify browser storage restoration on reload.
        control(quick(),'transpose').querySelector('.placement-target').click();
        await wait(()=>control(inspector(),'transpose'),'move after reset');
        control(inspector(),'feedbackCount').querySelector('.placement-target').click();
        await wait(()=>control(quick(),'feedbackCount'),'delay after reset');
        button('Save quick settings').click();
        await wait(()=>!quick().querySelector('.placement-target'),'saved');
        sessionStorage.setItem('ui-preferences-reloaded','true'); location.reload(); return;
      }
      await fetch('/result',{method:'POST',body:JSON.stringify({ok:true})});
    } catch(error) { await fetch('/result',{method:'POST',body:JSON.stringify({error:String(error),stack:error.stack})}); }
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
  const profile = fs.mkdtempSync(path.resolve(__dirname, '../.angular/ui-preferences-browser-'));
  const child = spawn(browser, ['--headless', '--remote-debugging-port=0', '--window-size='+width+',1000', '--disable-gpu', '--no-first-run', '--disable-background-networking',
    '--disable-background-timer-throttling', '--disable-renderer-backgrounding', '--no-default-browser-check',
    '--user-data-dir=' + profile, 'http://127.0.0.1:' + server.address().port + '/'], { stdio: ['ignore', 'ignore', 'pipe'] });
  let errors = '';
  child.stderr.on('data', chunk => { errors = (errors + chunk).slice(-2000); });
  child.on('error', reject);
  child.on('exit', code => reject(new Error('Chromium exited (' + code + '): ' + errors)));
  const timeout = setTimeout(() => reject(new Error('Chromium timed out: ' + errors)), 30000);
  let socket;
  try {
    const portFile = path.join(profile, 'DevToolsActivePort');
    let port;
    for (let i = 0; !port && i < 100; i++) {
      try { port = Number(fs.readFileSync(portFile, 'utf8').split('\n')[0]); }
      catch (error) { if (!['ENOENT','EBUSY','EPERM'].includes(error.code)) throw error; }
      if (!port) await new Promise(done => setTimeout(done, 50));
    }
    if (!port) throw new Error('Chromium debugging port unavailable: ' + errors);
    let target;
    for (let i = 0; !target && i < 100; i++) {
      target = (await (await fetch('http://127.0.0.1:' + port + '/json/list', { signal: AbortSignal.timeout(5000) })).json()).find(item => item.type === 'page');
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
    fs.writeFileSync(path.resolve(__dirname, '../.angular/quick-settings-' + width + '.png'), Buffer.from(screenshot.data, 'base64'));
    await command('Runtime.evaluate', { expression: "[...document.querySelectorAll('button')].find(b=>b.textContent.trim()==='Edit quick settings').click()" });
    await new Promise(resolve => setTimeout(resolve, 100));
    const layout = await command('Runtime.evaluate', { expression: `(() => {
      const arrangement=document.querySelector('vc-arrangement-overview .arrangement-panel').getBoundingClientRect();
      const heading=document.querySelector('.part-settings-heading').getBoundingClientRect();
      return { arrangementBottom: arrangement.bottom, headingTop: heading.top };
    })()`, returnByValue: true });
    assert.ok(layout.result.value.headingTop >= layout.result.value.arrangementBottom, 'arrangement overlaps save/reset header');
    const editingScreenshot = await command('Page.captureScreenshot', { format: 'png' });
    fs.writeFileSync(path.resolve(__dirname, '../.angular/quick-settings-edit-' + width + '.png'), Buffer.from(editingScreenshot.data, 'base64'));

    assert.equal(actual.error, undefined, JSON.stringify(actual));
    assert.equal(actual.ok, true);
  } finally {
    socket?.close(); clearTimeout(timeout); child.kill(); server.close();
    await new Promise(done => child.exitCode !== null ? done() : child.once('exit', done));
    fs.rmSync(profile, { recursive: true, force: true, maxRetries: 3, retryDelay: 100 });
  }
});
