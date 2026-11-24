const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const { closeBrowser, connectToPage, createBrowserServer, findBrowser, launchBrowser, listen, requireProductionBuild } = require('./support/browser-harness.cjs');
const { readSessionProject } = require('./support/workspace-browser.cjs');
const { DEFAULT_PROJECT } = require('./music-loader.cjs').loadMusic('../project/project-defaults.ts');

const browser = findBrowser();
test('phrase lanes draw and save one undoable controller stroke through the score UI', { skip: !browser, timeout: 45000 }, async () => {
  const root = requireProductionBuild();
  const bass = DEFAULT_PROJECT.tracks.find(track => track.role === 'bass');
  const volume = value => [{ lane: 'volume', startBeat: 0, endBeat: 8,
    segments: [{ startBeat: 0, endBeat: 8, startValue: value, endValue: value }] }];
  const shared = { ...bass, id: 'shared-channel', name: 'Shared voice', phraseGlides: volume(.25) };
  const unrelated = { ...bass, id: 'different-channel', name: 'Different channel', midiChannel: 16, phraseGlides: volume(.7) };
  const fixture = { ...DEFAULT_PROJECT, tracks: [...DEFAULT_PROJECT.tracks, shared, unrelated],
    arrangement: DEFAULT_PROJECT.arrangement.map((section, index) => ({ ...section,
      trackPresence: { ...section.trackPresence, [bass.id]: true, [shared.id]: true, [unrelated.id]: true },
      ...(index === 1 ? { trackGlides: { [bass.id]: [{ lane: 'volume', startBeat: 0, endBeat: 4, pickupBeats: 2,
        segments: [{ startBeat: -2, endBeat: 0, startValue: .6, endValue: .6 }] }] } } : {}),
    })) };
  const html = fs.readFileSync(path.join(root, 'index.html'), 'utf8').replace('</head>',
    `<script>sessionStorage.setItem('vibecomposer.project.v12', ${JSON.stringify(JSON.stringify(fixture))})</script></head>`);
  const server = createBrowserServer({ root, html });
  await listen(server);
  const child = launchBrowser(browser, ['--headless', '--disable-gpu', '--no-first-run', '--disable-background-networking',
    '--remote-debugging-port=0', '--window-size=1280,940', 'http://127.0.0.1:' + server.address().port]);
  let socket;
  try {
    const connection = await connectToPage(child);
    socket = connection.socket;
    const { command } = connection;
    const evaluate = async body => {
      const result = await command('Runtime.evaluate', { awaitPromise: true, returnByValue: true, expression: `(async () => {
        const wait = async read => {
          for (let i = 0; i < 180; i++) {
            const value = read(); if (value) return value;
            await new Promise(resolve => setTimeout(resolve, 25));
          }
          throw Error('Timed out waiting for the phrase editor');
        };
        const project = ${readSessionProject};
        const buttons = (selector, label) => [...document.querySelectorAll(selector)].find(button => button.textContent.trim() === label);
        const edit = label => buttons('.score-display-header button', label);
        const tool = label => buttons('.score-lane-controls button', label);
        const lane = () => document.querySelector('vc-score-glide-lane canvas');
        const choose = async value => changeControl(await wait(() => document.querySelector('#phrase-lane')), value);
        const open = async () => {
          (await wait(() => document.querySelector('.track-group.bass .track-select'))).click();
          (await wait(() => [...document.querySelectorAll('#part-inspector button')].find(button => button.textContent.trim().startsWith('Edit notes')))).click();
          await wait(() => edit('Apply') && !edit('Export Phrase').disabled);
        };
        const ink = () => lane().getContext('2d').getImageData(0, 0, lane().width, lane().height).data
          .filter((value, index) => index % 4 === 3 && value > 20).length;
        ${body}
      })()` });
      assert.equal(result.exceptionDetails, undefined, JSON.stringify(result.exceptionDetails));
      return result.result.value;
    };
    const geometry = await evaluate(`
      await open();
      const defaultLane = document.querySelector('#phrase-lane').textContent.trim();
      await choose('pitch');
      await wait(lane);
      await wait(() => ink() > 0);
      await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
      lane().scrollIntoView({ block: 'nearest' });
      const rect = lane().getBoundingClientRect();
      return { defaultLane, initialInk: ink(), x0: rect.left + rect.width * .25, x1: rect.left + rect.width * .6,
        y0: rect.bottom - 5, y1: rect.top + 5 };
    `);
    assert.match(geometry.defaultLane, /Note velocity/);
    const { x0, x1, y0, y1, initialInk } = geometry;
    await command('Input.dispatchMouseEvent', { type: 'mousePressed', x: x0, y: y0, button: 'left', buttons: 1, clickCount: 1 });
    await command('Input.dispatchMouseEvent', { type: 'mouseMoved', x: (x0 + x1) / 2, y: (y0 + y1) / 2, buttons: 1 });
    assert.equal(await evaluate(`return edit('Apply').disabled;`), true, 'preview changes no draft history until release');
    await command('Input.dispatchMouseEvent', { type: 'mouseReleased', x: x1, y: y1, button: 'left', clickCount: 1 });
    assert.ok(await evaluate(`
      await wait(() => !edit('Apply').disabled && !tool('Clear lane').disabled);
      return ink();
    `) > initialInk + 30);
    const screenshot = await command('Page.captureScreenshot', { format: 'png' });
    fs.writeFileSync(path.resolve(__dirname, '../.angular/phrase-glides.png'), Buffer.from(screenshot.data, 'base64'));
    assert.equal(await evaluate(`
      edit('Undo phrase').click();
      await wait(() => edit('Apply').disabled && tool('Clear lane').disabled);
      edit('Redo phrase').click();
      await wait(() => !edit('Apply').disabled);
      await choose('volume');
      await wait(() => tool('Fade in') && tool('Clear lane').disabled);
      tool('Fade in').click();
      await wait(() => !tool('Clear lane').disabled);
      await wait(() => document.querySelector('.glide-legend'));
      const sectionRect = document.querySelector('.score-ruler-section').getBoundingClientRect();
      const laneRect = lane().getBoundingClientRect();
      const hoverAt = fraction => lane().dispatchEvent(new PointerEvent('pointermove', {
        clientX: sectionRect.left + sectionRect.width * fraction, clientY: laneRect.top + laneRect.height / 2,
      }));
      hoverAt(.5);
      const hover = await wait(() => document.querySelector('.glide-cursor')?.textContent);
      if (!hover.includes('Channel 25% · Shared voice') || hover.includes('Different channel')) {
        throw Error('Wrong shared MIDI channel output shown: ' + hover);
      }
      hoverAt(.875);
      await wait(() => document.querySelector('.glide-cursor')?.textContent.includes('VERSE1 · bar 3 60%'));
      edit('Local Apply').click();
      const section = await wait(() => project()?.arrangement.find(section => section.trackGlides?.['track-bass-1']
        ?.some(edit => edit.lane === 'pitch')));
      const glides = section.trackGlides['track-bass-1'];
      if (section.trackPhrases?.['track-bass-1'] || glides.length !== 2) throw Error('Unexpected saved edits ' + JSON.stringify(glides));
      const stroke = glides.find(edit => edit.lane === 'pitch').segments;
      if (!(stroke[0].startValue < 0 && stroke.at(-1).endValue > 0)) throw Error('Unexpected stroke ' + JSON.stringify(stroke));
      await open();
      await choose('volume');
      await wait(() => !tool('Clear lane').disabled);
      tool('Clear lane').click();
      edit('Cancel').click();
      await open();
      await choose('volume');
      await wait(() => !tool('Clear lane').disabled);
      return edit('Apply').disabled;
    `), true, 'local save kept both controllers and cancel discarded the cleared lane');
  } finally {
    await closeBrowser({ child, server, socket });
  }
});
