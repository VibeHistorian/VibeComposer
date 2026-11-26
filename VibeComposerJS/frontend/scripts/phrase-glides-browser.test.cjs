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
        const channel = name => document.querySelector('vc-select[aria-label="MIDI channel ' + name + '"]');
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
    const hoverChannel = async name => {
      const point = await evaluate(`
        const trigger = await wait(() => channel(${JSON.stringify(name)}));
        if (trigger.hasAttribute('title')) throw Error('MIDI channel still has a native tooltip');
        trigger.scrollIntoView({ block: 'nearest' });
        await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
        const rect = trigger.getBoundingClientRect();
        return { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 };
      `);
      await command('Input.dispatchMouseEvent', { type: 'mouseMoved', ...point });
      return evaluate(`return (await wait(() => [...document.querySelectorAll('.vc-instant-tooltip')]
        .find(tip => tip.textContent.includes('MIDI channel')))).textContent.trim();`);
    };
    assert.equal(await hoverChannel('M1'), 'MIDI channel 4');
    assert.equal(await hoverChannel('B1'), 'MIDI channel 1 · Volume glide');
    assert.deepEqual(await evaluate(`
      const dot = (name, pseudo) => {
        const style = getComputedStyle(channel(name), pseudo);
        return [style.content, style.bottom, style.right];
      };
      return [dot('M1', '::before')[0], dot('B1', '::after')[0], dot('B1', '::before')];
    `), ['none', 'none', ['""', '-1px', '-1px']]);
    const knob = await evaluate(`
      const input = await wait(() => document.querySelector('input[aria-label="Decay B1"]'));
      input.scrollIntoView({ block: 'nearest' });
      await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
      const rect = input.getBoundingClientRect();
      return { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2, value: input.getAttribute('aria-valuetext') };
    `);
    await command('Input.dispatchMouseEvent', { type: 'mouseMoved', x: knob.x, y: knob.y });
    const tooltip = await evaluate(`return (await wait(() => document.querySelector('.knob-tooltip'))).textContent;`);
    assert.ok(tooltip.includes(knob.value) && tooltip.includes('Default'), 'hover shows the instrument default immediately');
    await command('Input.dispatchMouseEvent', { type: 'mousePressed', x: knob.x, y: knob.y, button: 'left', buttons: 1, clickCount: 1 });
    await command('Input.dispatchMouseEvent', { type: 'mouseMoved', x: knob.x, y: knob.y - 18, button: 'left', buttons: 1 });
    const preview = await evaluate(`
      return (await wait(() => [...document.querySelectorAll('.knob-tooltip')].find(tip => !tip.textContent.includes('Default')))).textContent;
    `);
    assert.equal(await evaluate(`return project()?.tracks.find(track => track.id === 'track-bass-1').instrumentTone ?? null;`), null);
    await command('Input.dispatchMouseEvent', { type: 'mouseReleased', x: knob.x, y: knob.y - 18, button: 'left', clickCount: 1 });
    const decay = await evaluate(`
      const track = () => project()?.tracks.find(track => track.id === 'track-bass-1');
      const amount = await wait(() => track()?.instrumentTone?.decayMs);
      document.querySelector('input[aria-label="Decay B1"]').dispatchEvent(new MouseEvent('contextmenu', { button: 2, bubbles: true, cancelable: true }));
      await wait(() => !track()?.instrumentTone);
      return amount;
    `);
    assert.ok(decay > 0 && preview.includes(String(decay)), 'drag commits the previewed value and reset removes the override');
    const pill = await evaluate(`
      const mode = await wait(() => document.querySelector('vc-select[aria-label="Voice mode B1"]'));
      await changeControl(mode, 'legato');
      const input = await wait(() => document.querySelector('input[aria-label="Glide time B1"]'));
      input.scrollIntoView({ block: 'nearest' });
      await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
      const rect = input.getBoundingClientRect();
      const channelRect = channel('B1').getBoundingClientRect();
      const instrument = document.querySelector('vc-menu-select[aria-label="Instrument B1"]').getBoundingClientRect();
      const lower = mode.getBoundingClientRect();
      return { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2,
        hit: document.elementFromPoint(rect.left + rect.width / 2, rect.top + rect.height / 2)?.outerHTML.slice(0, 200),
        aligned: rect.top >= channelRect.bottom && lower.top >= instrument.bottom && Math.abs(lower.right - instrument.right) < 1 };
    `);
    assert.ok(pill.aligned);
    assert.match(pill.hit, /Glide time B1/);
    for (const modifiers of [0, 8]) {
      await command('Input.dispatchMouseEvent', { type: 'mousePressed', x: pill.x, y: pill.y, button: 'left', buttons: 1, modifiers, clickCount: 1 });
      await command('Input.dispatchMouseEvent', { type: 'mouseMoved', x: pill.x, y: pill.y - 10, button: 'left', buttons: 1, modifiers });
      await command('Input.dispatchMouseEvent', { type: 'mouseReleased', x: pill.x, y: pill.y - 10, button: 'left', modifiers, clickCount: 1 });
      const glide = await evaluate(`
        const input = document.querySelector('input[aria-label="Glide time B1"]');
        const value = await wait(() => project()?.tracks.find(track => track.id === 'track-bass-1').glideMs);
        input.dispatchEvent(new MouseEvent('contextmenu', { button: 2, bubbles: true, cancelable: true }));
        await wait(() => project()?.tracks.find(track => track.id === 'track-bass-1').glideMs === 0);
        return value;
      `);
      assert.ok(modifiers ? glide < 60 : glide > 60, 'the pill uses the existing fine-drag modifier');
    }
    await evaluate(`await changeControl(document.querySelector('vc-select[aria-label="Voice mode B1"]'), 'poly');`);
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
    assert.equal(await evaluate(`return channel('B1').classList.contains('has-pitch-glide');`), false, 'drafts leave the committed indicator unchanged');
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
      const clean = edit('Apply').disabled;
      edit('Cancel').click();
      return clean;
    `), true, 'local save kept both controllers and cancel discarded the cleared lane');
    assert.equal(await hoverChannel('B1'), 'MIDI channel 1 · Pitch glide · Volume glide');
    assert.equal(await hoverChannel('Shared voice'), 'MIDI channel 1 · Pitch glide · Volume glide');
    assert.equal(await hoverChannel('Different channel'), 'MIDI channel 16 · Volume glide');
    assert.ok(await evaluate(`
      await document.fonts.ready;
      return ['M1', 'B1', 'Different channel'].every(name => {
        const trigger = channel(name);
        const button = trigger.getBoundingClientRect();
        const range = document.createRange();
        range.selectNodeContents(trigger.querySelector('.select-value'));
        const label = range.getBoundingClientRect();
        return Math.abs((label.left + label.right) - (button.left + button.right)) <= 1
          && Math.abs((label.top + label.bottom) - (button.top + button.bottom)) <= 1
          && label.left >= button.left + 6 && label.right <= button.right - 6;
      });
    `), 'channel labels are centered on both axes and clear the corner dots');
    assert.deepEqual(await evaluate(`
      const trigger = channel('B1');
      const pitch = getComputedStyle(trigger, '::after'), volume = getComputedStyle(trigger, '::before');
      const laneColor = property => {
        const swatch = document.createElement('span');
        swatch.style.color = 'var(' + property + ')';
        trigger.append(swatch);
        const color = getComputedStyle(swatch).color;
        swatch.remove();
        return color;
      };
      return [pitch.top, pitch.right, volume.bottom, volume.right,
        pitch.backgroundColor === laneColor('--pitch-glide-color'),
        volume.backgroundColor === laneColor('--volume-glide-color')];
    `), ['-1px', '-1px', '-1px', '-1px', true, true]);
  } finally {
    await closeBrowser({ child, server, socket });
  }
});
