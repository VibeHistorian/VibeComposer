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

const fullMatrix = process.env.OVERVIEW_TEST_FULL_MATRIX === '1';
// Every layout runs at normal zoom/DPI. Cross the scale matrix only for cases
// that exercise rounding, overflow, dense buttons, spare score space or gaps.
const layouts = [
  { name: 'integer fit', width: 1218, height: 384, scale: 1 },
  { name: 'half-pixel fit', width: 1217.5, height: 383.5, scale: 1, scaled: true },
  { name: 'fractional fit', width: 1217.6, height: 383.6, scale: 1, scaled: true },
  { name: 'half-pixel overflow', width: 1217.5, height: 383.5, scale: 2, scaled: true },
  { name: 'shorter fit', width: 1217.6, height: 365.6, scale: 1 },
  { name: 'narrow overflow', width: 830.5, height: 350.5, scale: 2 },
  { name: 'narrow fit', width: 830.5, height: 350.5, scale: 1 },
  { name: 'restore integer fit', width: 1218, height: 384, scale: 1 },
  { name: 'fractional overflow', width: 1217.6, height: 365.6, scale: 2.137, scaled: true },
  { name: 'medium fit', width: 1217.5, height: 500.5, scale: 1 },
  { name: 'medium overflow', width: 1217.5, height: 500.5, scale: 2 },
  { name: '64 tracks', width: 1217.5, height: 383.5, scale: 1, trackCount: 64 },
  { name: 'narrow dense buttons', width: 700.5, height: 383.5, scale: 1, trackCount: 32, scaled: true },
  { name: 'short narrow dense buttons', width: 700.5, height: 363.5, scale: 1, trackCount: 32 },
  { name: 'two tracks', width: 1217.5, height: 500.5, scale: 1, trackCount: 2 },
  { name: 'spare score space', width: 1218, height: 800, scale: 1, scaled: true },
  { name: 'tall fit', width: 1218, height: 1000, scale: 1 },
  { name: 'tall overflow', width: 1218, height: 1000, scale: 2 },
  { name: 'restore tall fit', width: 1218, height: 1000, scale: 1 },
  { name: 'gap before section', width: 1217.5, height: 383.5, scale: 1, trackCount: 32, gaps: true, scaled: true },
  { name: 'only gap', width: 700.5, height: 383.5, scale: 1, trackCount: 64, gaps: 'all', scaled: true },
];

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
  // Keep the CSS viewport above 700px throughout the display-scale matrix.
  const child = spawn(browser, ['--headless', '--window-size=1920,1200', '--disable-gpu', '--no-first-run',
    '--disable-background-networking', '--disable-component-update', '--disable-sync',
    '--disable-background-timer-throttling', '--disable-renderer-backgrounding',
    '--force-device-scale-factor=' + displayScale, '--no-default-browser-check',
    `http://127.0.0.1:${server.address().port}/`], { stdio: ['ignore', 'ignore', 'pipe'] });
  let errors = '';
  child.stderr.on('data', (chunk) => { errors = (errors + chunk).slice(-4000); });
  child.on('error', reject);
  child.on('exit', (code) => reject(new Error(`Chromium exited (${code}): ${errors}`)));
  const timeout = setTimeout(() => reject(new Error(`Chromium test timed out: ${errors}`)), 45000);
  try { return await result; } finally {
    clearTimeout(timeout); child.kill(); server.close();
  }
}

test('arrangement scrollbar layout settles after local edits, zoom, and resizing', {
  skip: browser ? false : 'Set OVERVIEW_TEST_BROWSER to a Chromium executable',
}, async (context) => {
    const source = fs.readFileSync(path.join(__dirname, '../src/app/features/arrangement/arrangement-overview.component.ts'), 'utf8');
    const compiled = ts.transpileModule(source, { compilerOptions: {
      target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, experimentalDecorators: true,
    } }).outputText;
    const css = fs.readFileSync(path.join(__dirname, '../src/app/features/arrangement/arrangement-overview.component.css'), 'utf8');
    const contextCode = ts.transpileModule(fs.readFileSync(path.join(__dirname, '../src/app/core/music/section-context.ts'), 'utf8'), {
      compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS },
    }).outputText;
    const phraseInputsCode = ts.transpileModule(fs.readFileSync(path.join(__dirname, '../src/app/core/music/phrase-inputs.ts'), 'utf8'), {
      compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS },
    }).outputText;
    const theme = fs.readFileSync(path.join(__dirname, '../src/styles.css'), 'utf8');
    const workspaceCss = fs.readFileSync(path.join(__dirname, '../src/app/features/workspace/workspace-canvas.component.css'), 'utf8');
    const html = displayScale => `<!doctype html><html><head><style>${theme}\n${workspaceCss}\n${css}
      .fixture { width: 1218px; height: 384px; }
      .fixture > vc-arrangement-overview { display: block; min-width: 0; min-height: 0; }
      .fixture > .part-settings-panel { height: 40px; }
    </style></head><body><div class="fixture song-canvas"><vc-arrangement-overview><section class="arrangement-panel">
      <header class="canvas-heading">Arrangement</header>
      <div class="overview-scroll"><div class="overview-content"><div class="overview-viewport">
        <canvas id="score"></canvas><canvas id="playhead"></canvas>
      </div></div></div>
    </section></vc-arrangement-overview><section class="part-settings-panel"></section>
      <section class="score-panel"></section></div><pre id="result">pending</pre><script>
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
      computed: read => read, effect: () => {}, input, signal, inject: () => ({ appearance: () => ({}) }), output: () => ({ emit() {} }),
      viewChild: { required: name => () => ({ nativeElement: refs.get(name) }) } };
    const dependencies = { '@angular/core': angular,
      '../../core/music/phrase-inputs': (() => { const module={exports:{}}; const exports=module.exports; ${phraseInputsCode}; return module.exports; })(),
      '../../core/ui/ui-preferences.service': { UiPreferencesService: class UiPreferencesService {} },
      '../../core/music/section-context': (() => { const module={exports:{}}; const exports=module.exports; const require=()=>({}); ${contextCode}; return module.exports; })(),
      '../../core/project/project.model': { ARRANGED_PARTS: ['melody', 'bass', 'chords', 'arpeggio', 'drums'] },
      '../../core/music/arrangement-generator': { shouldGenerateTrackInSection: (section, track) => section.trackPresence?.[track.id] ?? true },
      '../../core/music/phrase': { layOutTrackPhrase: () => [] } };
    const module = { exports: {} }; const exports = module.exports;
    const require = name => dependencies[name];
    ${compiled}
    const component = new module.exports.ArrangementOverviewComponent();
    const project = { progression:[1,5,6,4], tracks: [{ id: 'bass-1', role: 'bass' }],
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
      for (const { name, width, height, scale, trackCount = 1, gaps = false, scaled = false } of ${JSON.stringify(layouts)}) {
        if (!${fullMatrix} && !(pageZoom === 1 && ${displayScale} === 1) && !scaled) continue;
        fixture.style.width = width + 'px'; fixture.style.height = height + 'px';
        component.horizontalScale = scale;
        const currentProject = { ...project,
          tracks: Array.from({ length: trackCount }, (_, index) => ({ id: 'bass-' + (index + 1), role: 'bass' })),
          arrangement: gaps ? [
            { id:'gap',type:'INTRO',measures:1,customDurations:[.5],trackPresence:Object.fromEntries(Array.from({length:trackCount},(_,i)=>['bass-'+(i+1),i===1||i===2])) },
            ...(gaps==='all'?[]:[{id:'verse',type:'VERSE1',measures:10}]),
          ] : trackCount > 1
            ? [{ id: 'short', type: 'INTRO', measures: 1 }, { id: 'verse', type: 'VERSE1', measures: 10 }]
            : project.arrangement };
        const samples = [];
        for (let draw = 0; draw < 12; draw++) {
          component.project.set({ ...currentProject, arrangement: currentProject.arrangement.map(section => ({ ...section,
            ...(draw % 2 ? { trackPartOverrides: { 'bass-1': { transpose: 12 } } }
              : { rolePartOverrides: { bass: { transpose: 12 } } }) })) });
          component.drawOverview();
          if (draw === 0) { host.scrollLeft = host.scrollWidth; host.scrollTop = host.scrollHeight; }
          await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
          samples.push(sample());
        }
        const buttonRows = currentProject.arrangement.map(section =>
          Math.ceil(component.overviewTracks(section,{role:'bass',tracks:currentProject.tracks}).length / Math.max(1, Math.floor((dependencies['../../core/music/section-context'].sectionDurationBeats(currentProject,section) * component.pixelsPerBeat - 10 + 1e-7) / 19))));
        if(gaps) {
          const gapButtons=component.hits.filter(hit=>hit.key.startsWith('toggle:gap:'));
          if(component.effectiveHorizontalScale!==1||gapButtons.length!==1||gapButtons[0].key!=='toggle:gap:bass-2') throw new Error('gap sections must fit and show only their first included track');
        }
        cases.push({ name, width, height, scale, pageZoom, samples, buttonRows,
          gridHeight: host.clientHeight, scrollbarHeight: host.offsetHeight - host.clientHeight,
          arrangementHeight: document.querySelector('vc-arrangement-overview').offsetHeight,
          settingsHeight: document.querySelector('.part-settings-panel').offsetHeight,
          scoreHeight: document.querySelector('.score-panel').offsetHeight,
          effectiveScale: component.effectiveHorizontalScale });
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
      const started = performance.now();
      const result = await render(html(displayScale), displayScale);
      assert.equal(result.error, undefined, result.error);
      context.diagnostic(`${fullMatrix ? 'Full' : 'Focused'} matrix, display scale ${displayScale}: ${result.cases.length} cases in ${Math.round(performance.now() - started)}ms`);
      for (const item of result.cases) {
        const last = item.samples.at(-1);
        const message = `${item.name} at ${item.width}×${item.height}, timeline zoom ${item.scale}, page zoom ${item.pageZoom}, display scale ${displayScale}: ${JSON.stringify(item.samples)}`;
        assert.ok(parseFloat(last[2]) > 0 && parseFloat(last[3]) > 0, 'The canvas must actually render');
        for (const sample of item.samples.slice(-4)) assert.deepEqual(sample, last, message);
        assert.ok(parseFloat(last[4]) <= last[0] && parseFloat(last[5]) <= last[1], message);
        assert.equal(last[6], Math.round(parseFloat(last[4]) * displayScale));
        assert.equal(last[7], Math.round(parseFloat(last[5]) * displayScale));
        assert.equal(last[8], last[6]); assert.equal(last[9], last[7]);
        assert.ok(item.buttonRows.every(rows => rows <= 2), 'Track buttons must fit within two rows');
        assert.ok(Math.abs(item.gridHeight - 284) <= 1, 'Arrangement fits the ruler and five rows, including when zoomed');
        assert.equal(last[11], last[1], 'The last row fits without vertical scrolling or empty background');
        if (item.height >= 800) {
          assert.ok(Math.abs(item.scoreHeight - (item.height - item.arrangementHeight - item.settingsHeight - 24)) <= 2,
            'The score receives all space remaining after the arrangement, settings, and two gaps: ' + JSON.stringify(item));
        }
        if (item.effectiveScale === 1) assert.equal(last[10], last[0], 'Fitted content must not overflow horizontally');
        else assert.ok(last[10] > last[0], 'Zoomed content must remain scrollable');
      }
    }
});
