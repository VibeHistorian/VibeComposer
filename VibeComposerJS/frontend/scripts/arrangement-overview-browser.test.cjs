const assert = require('node:assert/strict');
const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');
const { spawn } = require('node:child_process');
const test = require('node:test');
const ts = require('typescript');

// Real Chromium layout is necessary here: DOM stubs cannot model scrollbar reflow.
const browser = process.env.OVERVIEW_TEST_BROWSER || [
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  '/usr/bin/chromium', '/usr/bin/chromium-browser', '/usr/bin/google-chrome',
].find((file) => fs.existsSync(file));

async function render(html, displayScale) {
  let finish;
  let reject;
  const result = new Promise((resolve, fail) => { finish = resolve; reject = fail; });
  const server = http.createServer((request, response) => {
    if (request.url === '/result') {
      let body = '';
      request.on('data', (chunk) => { body += chunk; });
      request.on('end', () => {
        response.end('OK');
        try { finish(JSON.parse(body)); } catch (error) { reject(error); }
      });
    } else {
      response.setHeader('Content-Type', 'text/html'); response.end(html);
    }
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  // Run in real time: virtual-time dump-dom can finish before animation frames
  // and ResizeObserver callbacks run, giving false confidence about resize loops.
  const child = spawn(browser, ['--headless', '--disable-gpu', '--no-first-run',
    '--disable-background-networking', '--disable-component-update', '--disable-sync',
    '--disable-background-timer-throttling', '--disable-renderer-backgrounding',
    '--force-device-scale-factor=' + displayScale, '--no-default-browser-check',
    `http://127.0.0.1:${server.address().port}/`], { stdio: ['ignore', 'ignore', 'pipe'] });
  let errors = '';
  child.stderr.on('data', (chunk) => { errors = (errors + chunk).slice(-4000); });
  child.on('error', reject);
  child.on('exit', (code) => reject(new Error(`Chromium exited (${code}): ${errors}`)));
  const timeout = setTimeout(() => reject(new Error(`Chromium test timed out: ${errors}`)), 30000);
  try { return await result; } finally {
    clearTimeout(timeout); child.kill(); server.close();
  }
}

test('arrangement scrollbar layout settles after local edits, zoom, and resizing', {
  skip: browser ? false : 'Set OVERVIEW_TEST_BROWSER to a Chromium executable',
}, async () => {
    const source = fs.readFileSync(path.join(__dirname, '../src/app/shared/arrangement-overview.component.ts'), 'utf8');
    const compiled = ts.transpileModule(source, { compilerOptions: {
      target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, experimentalDecorators: true,
    } }).outputText;
    const css = fs.readFileSync(path.join(__dirname, '../src/app/shared/arrangement-overview.component.css'), 'utf8');
    const theme = fs.readFileSync(path.join(__dirname, '../src/styles.css'), 'utf8');
    const html = `<!doctype html><html><head><style>${theme}\n${css}
      .fixture { width: 1218px; height: 384px; }
    </style></head><body><div class="fixture"><section class="arrangement-panel">
      <header class="canvas-heading">Arrangement</header>
      <div class="overview-scroll"><div class="overview-content"><div class="overview-viewport">
        <canvas id="score"></canvas><canvas id="playhead"></canvas>
      </div></div></div>
    </section></div><pre id="result">pending</pre><script>
    const refs = new Map([
      ['scrollHost', document.querySelector('.overview-scroll')],
      ['scrollContent', document.querySelector('.overview-content')],
      ['viewport', document.querySelector('.overview-viewport')],
      ['scoreCanvas', document.querySelector('#score')],
      ['playheadCanvas', document.querySelector('#playhead')],
    ]);
    const signal = value => Object.assign(() => value, { set: next => { value = next; } });
    const input = value => signal(value); input.required = () => signal(undefined);
    const angular = { Component: () => type => type, ChangeDetectionStrategy: { OnPush: 0 },
      computed: read => read, effect: () => {}, input, output: () => ({ emit() {} }),
      viewChild: { required: name => () => ({ nativeElement: refs.get(name) }) } };
    const dependencies = { '@angular/core': angular,
      '../core/project/project.model': { ARRANGED_PARTS: ['bass', 'chords', 'arpeggio', 'drums'] },
      '../core/music/arrangement-generator': { shouldGenerateTrackInSection: () => true },
      '../core/music/phrase': { layOutTrackPhrase: () => [] } };
    const module = { exports: {} }; const exports = module.exports;
    const require = name => dependencies[name];
    ${compiled}
    const component = new module.exports.ArrangementOverviewComponent();
    const project = { tracks: [{ id: 'bass-1', role: 'bass' }],
      arrangement: [{ id: 'verse', type: 'VERSE1', measures: 11 }] };
    component.project.set(project);
    const host = refs.get('scrollHost');
    host.addEventListener('scroll', () => component.onScroll());
    component.ngAfterViewInit();
    const fixture = document.querySelector('.fixture');
    const cases = [];
    const sample = () => [host.clientWidth, host.clientHeight,
      refs.get('scrollContent').style.width, refs.get('scrollContent').style.height,
      refs.get('viewport').style.width, refs.get('viewport').style.height,
      refs.get('scoreCanvas').width, refs.get('scoreCanvas').height,
      refs.get('playheadCanvas').width, refs.get('playheadCanvas').height,
      host.scrollWidth, host.scrollHeight];
    (async () => { try {
      for (const pageZoom of [0.8, 1, 1.25, 1.5]) {
      document.documentElement.style.zoom = pageZoom;
      for (const [width, height, scale] of [
        [1218, 384, 1], [1217.5, 383.5, 1], [1217.6, 383.6, 1],
        [1217.5, 383.5, 2], [1217.6, 365.6, 1], [830.5, 350.5, 2],
        [830.5, 350.5, 1], [1218, 384, 1], [1217.6, 365.6, 2.137],
      ]) {
        fixture.style.width = width + 'px'; fixture.style.height = height + 'px';
        component.horizontalScale = scale;
        const samples = [];
        for (let draw = 0; draw < 12; draw++) {
          component.project.set({ ...project, arrangement: [{ ...project.arrangement[0],
            ...(draw % 2 ? { trackPartOverrides: { 'bass-1': { transpose: 12 } } }
              : { rolePartOverrides: { bass: { transpose: 12 } } }) }] });
          component.drawOverview();
          if (draw === 0) { host.scrollLeft = host.scrollWidth; host.scrollTop = host.scrollHeight; }
          await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
          samples.push(sample());
        }
        cases.push({ width, height, scale, pageZoom, samples });
      }
      }
      document.querySelector('#result').textContent = JSON.stringify({ cases });
      await fetch('/result', { method: 'POST', body: JSON.stringify({ cases }) });
      component.ngOnDestroy();
    } catch (error) {
      document.querySelector('#result').textContent = JSON.stringify({ error: error.stack });
      await fetch('/result', { method: 'POST', body: JSON.stringify({ error: error.stack }) });
    } })();
    </script></body></html>`;
    for (const displayScale of [1, 1.25, 1.5]) {
      const result = await render(html, displayScale);
      assert.equal(result.error, undefined, result.error);
      for (const item of result.cases) {
        const last = item.samples.at(-1);
        const message = `Layout at ${item.width}×${item.height}, timeline zoom ${item.scale}, page zoom ${item.pageZoom}, display scale ${displayScale}: ${JSON.stringify(item.samples)}`;
        assert.ok(parseFloat(last[2]) > 0 && parseFloat(last[3]) > 0, 'The canvas must actually render');
        for (const sample of item.samples.slice(-4)) assert.deepEqual(sample, last, message);
        assert.ok(parseFloat(last[4]) <= last[0] && parseFloat(last[5]) <= last[1], message);
        assert.equal(last[6], Math.round(parseFloat(last[4]) * displayScale));
        assert.equal(last[7], Math.round(parseFloat(last[5]) * displayScale));
        assert.equal(last[8], last[6]); assert.equal(last[9], last[7]);
        if (item.scale === 1) assert.equal(last[10], last[0], 'Fitted content must not overflow horizontally');
        else assert.ok(last[10] > last[0], 'Zoomed content must remain scrollable');
      }
    }
});
