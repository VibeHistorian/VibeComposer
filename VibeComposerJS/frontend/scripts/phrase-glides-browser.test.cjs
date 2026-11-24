const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const { closeBrowser, connectToPage, createBrowserServer, findBrowser, launchBrowser, listen, requireProductionBuild } = require('./support/browser-harness.cjs');
const { readSessionProject } = require('./support/workspace-browser.cjs');

const browser = findBrowser();
test('phrase lanes draw and save one undoable controller stroke through the score UI', { skip: !browser, timeout: 45000 }, async () => {
  const server = createBrowserServer({ root: requireProductionBuild() });
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
      const rect = (await wait(lane)).getBoundingClientRect();
      await wait(() => ink() > 0);
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
      edit('Local Apply').click();
      const section = await wait(() => project()?.arrangement.find(section => section.trackGlides?.['track-bass-1']));
      const glides = section.trackGlides['track-bass-1'];
      if (section.trackPhrases?.['track-bass-1'] || glides.length !== 2) return false;
      const stroke = glides.find(edit => edit.lane === 'pitch').segments;
      if (!(stroke[0].startValue < 0 && stroke.at(-1).endValue > 0)) return false;
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
