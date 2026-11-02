const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const { findBrowser, requireProductionBuild, createBrowserServer, listen, launchBrowser,
  connectToPage, watchBrowser, closeBrowser } = require('./support/browser-harness.cjs');

const browser = findBrowser();
for (const [width, height] of [[1440, 900], [1920, 1200], [2560, 1600], [780, 2160]]) {
  test(`workspace fits ${width}×${height} and quick settings separate categories`, { skip: !browser }, async () => {
    const root = requireProductionBuild();
    const script = `<script>(async () => {
      const wait = async (read, label) => {
        for (let i=0;i<200;i++) { if(read())return; await new Promise(resolve=>setTimeout(resolve,25)); }
        throw new Error(label);
      };
      const ensure = (value, label) => { if(!value)throw new Error(label); };
      const settle = async () => {
        await new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)));
        await new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)));
      };
      try {
        await wait(()=>innerWidth===${width} && innerHeight===${height} && document.querySelector('.track-select'),'workspace');
        await document.fonts.ready; await settle();
        const heightBounds = () => ({ viewport: innerHeight, document: document.documentElement.scrollHeight,
          root: document.querySelector('vc-root').getBoundingClientRect().toJSON(),
          frame: document.querySelector('.app-frame').getBoundingClientRect().toJSON() });
        ensure(document.documentElement.scrollHeight<=innerHeight,'unnecessary page scrollbar: '+JSON.stringify(heightBounds()));
        const gaps = { within: 0, between: 0 };
        for (const role of ['melody','bass','chords','arpeggio','drums']) {
          document.querySelector('.track-group.'+role+' .track-select').click();
          await wait(()=>document.querySelector('.part-settings-content vc-part-settings-editor')?.getAttribute('data-control-role')===role,'quick settings role');
          await settle();
          const controls = [...document.querySelectorAll('.part-settings-content .part-control')].map(control=>({
            category: control.dataset.controlCategory??'seed', box: control.getBoundingClientRect() }));
          for (let index=1;index<controls.length;index++) {
            const previous=controls[index-1], current=controls[index];
            if(Math.abs(previous.box.top-current.box.top)>1)continue;
            const same=previous.category===current.category, expected=same?6:12;
            const gap=current.box.left-previous.box.right;
            ensure(Math.abs(gap-expected)<=1,role+' category gap: '+JSON.stringify({previous:previous.category,current:current.category,gap,expected}));
            gaps[same?'within':'between']++;
          }
          ensure(document.documentElement.scrollWidth<=innerWidth,'quick settings cause page overflow');
          ensure(document.documentElement.scrollHeight<=innerHeight,'selection adds page scrollbar: '+JSON.stringify(heightBounds()));
        }
        ensure(gaps.between>0,'category boundaries exercised');
        if(innerWidth>=1920)ensure(gaps.within>0,'controls within a category exercised');
        await fetch('/result',{method:'POST',body:JSON.stringify({ok:true,gaps})});
      } catch(error) { await fetch('/result',{method:'POST',body:JSON.stringify({error:error.stack})}); }
    })();</script>`;
    const html = fs.readFileSync(path.join(root, 'index.html'), 'utf8').replace('</body>', script + '</body>');
    let resolve, reject;
    const result = new Promise((done, fail) => { resolve=done; reject=fail; });
    result.catch(()=>{});
    const server = createBrowserServer({ root, html, resolve, reject });
    await listen(server);
    const profile = fs.mkdtempSync(path.resolve(__dirname, '../.angular/workspace-layout-'));
    const child = launchBrowser(browser, ['--headless', '--disable-gpu', '--no-first-run',
      '--disable-background-networking', '--remote-debugging-port=0', '--window-size='+width+','+height,
      '--user-data-dir='+profile, 'http://127.0.0.1:'+server.address().port+'/'], { stdio: ['ignore', 'ignore', 'pipe'] });
    const watchdog = watchBrowser(child, reject, 30000);
    let socket;
    try {
      const connection = await connectToPage(child);
      socket = connection.socket;
      await connection.command('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: false });
      const output = await result;
      assert.equal(output.error, undefined, JSON.stringify(output));
      assert.equal(output.ok, true);
      const screenshot = await connection.command('Page.captureScreenshot', { format: 'png' });
      fs.writeFileSync(path.resolve(__dirname, '../.angular/workspace-layout-'+width+'x'+height+'.png'), Buffer.from(screenshot.data, 'base64'));
      // A genuinely short stacked layout must remain scrollable rather than clip its panels.
      await connection.command('Emulation.setDeviceMetricsOverride', { width: 780, height: 700, deviceScaleFactor: 1, mobile: false });
      const scrolling = await connection.command('Runtime.evaluate', { awaitPromise: true, returnByValue: true,
        expression: `(async () => {
          await new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)));
          return document.documentElement.scrollHeight>innerHeight
            && !['hidden','clip'].includes(getComputedStyle(document.documentElement).overflowY)
            && !['hidden','clip'].includes(getComputedStyle(document.body).overflowY);
        })()` });
      assert.equal(scrolling.result.value, true, 'short windows retain access to overflowing content');
    } finally {
      await closeBrowser({ child, server, socket, watchdog });
    }
  });
}
