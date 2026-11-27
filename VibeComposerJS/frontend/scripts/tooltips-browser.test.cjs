const assert = require('node:assert/strict');
const test = require('node:test');
const { closeBrowser, connectToPage, createBrowserServer, findBrowser, launchBrowser, listen, requireProductionBuild } = require('./support/browser-harness.cjs');

const browser = findBrowser();
test('tooltips distinguish immediate readouts from delayed help and dismiss without trapping interaction', { skip: !browser, timeout: 35000 }, async () => {
  const server = createBrowserServer({ root: requireProductionBuild() });
  await listen(server);
  const child = launchBrowser(browser, ['--headless', '--remote-debugging-port=0', '--no-first-run',
    '--window-size=780,960', 'http://127.0.0.1:' + server.address().port]);
  let socket;
  try {
    const connection = await connectToPage(child);
    socket = connection.socket;
    const { command } = connection;
    const evaluate = async expression => {
      const response = await command('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
      assert.equal(response.exceptionDetails, undefined, JSON.stringify(response.exceptionDetails));
      return response.result.value;
    };
    await evaluate(`window.tooltipTest = {
      tip: () => document.querySelector('vc-tooltip-panel [role="tooltip"]'),
      frames: () => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))),
      wait: async (read, description) => {
        for (let attempt = 0; attempt < 150; attempt++) {
          const value = read(); if (value) return value;
          await new Promise(resolve => setTimeout(resolve, 20));
        }
        throw Error('Timed out waiting for ' + description);
      }
    }; true`);
    const move = async (x, y) => command('Input.dispatchMouseEvent', { type: 'mouseMoved', x, y });
    const hover = async selector => {
      const point = await evaluate(`(async () => {
        const element = await tooltipTest.wait(() => document.querySelector(${JSON.stringify(selector)}), 'tooltip trigger');
        element.scrollIntoView({ block: 'nearest', inline: 'nearest' });
        await tooltipTest.frames();
        const rect = element.getBoundingClientRect();
        return { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 };
      })()`);
      await move(point.x, point.y);
    };
    const text = () => evaluate(`tooltipTest.tip()?.textContent ?? null`);
    const afterFrames = () => evaluate('tooltipTest.frames()');
    const waitForTip = () => evaluate(`tooltipTest.wait(() => tooltipTest.tip()?.textContent, 'tooltip text')`);
    const key = async (key, windowsVirtualKeyCode) => {
      await command('Input.dispatchKeyEvent', { type: 'keyDown', key, windowsVirtualKeyCode });
      await command('Input.dispatchKeyEvent', { type: 'keyUp', key, windowsVirtualKeyCode });
    };

    const instrument = 'vc-menu-select.track-instrument';
    const engine = 'vc-menu-select.instrument-selector';
    const compose = 'button[aria-label="Compose"]';
    const engineName = () => evaluate(`document.querySelector(${JSON.stringify(engine)}).getAttribute('aria-description')`);
    await hover(engine);
    await afterFrames();
    assert.ok(await engineName());
    assert.equal(await text(), await engineName());
    const instantSurface = await evaluate(`(() => {
      const tip = tooltipTest.tip(), pane = tip.closest('.cdk-overlay-pane'), rect = tip.getBoundingClientRect();
      return { pointerEvents: getComputedStyle(pane).pointerEvents, visible: rect.left >= 0 && rect.right <= innerWidth,
        surface: getComputedStyle(tip).backgroundColor, native: document.querySelector(${JSON.stringify(engine)}).hasAttribute('title') };
    })()`);
    assert.equal(instantSurface.pointerEvents, 'none');
    assert.equal(instantSurface.visible, true);
    assert.equal(instantSurface.native, false);

    await hover(compose);
    assert.equal(await evaluate(`new Promise(resolve => setTimeout(() => resolve(tooltipTest.tip()?.textContent ?? null), 150))`), null);
    await hover(engine);
    assert.equal(await evaluate(`new Promise(resolve => setTimeout(() => resolve(tooltipTest.tip()?.textContent), 700))`), await engineName(),
      'leaving explanatory help cancels its timer');

    await hover(compose);
    assert.match(await waitForTip(), /^Compose using the current seed/);
    const help = await evaluate(`(() => {
      const tip = tooltipTest.tip(), rect = tip.getBoundingClientRect();
      return { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2, surface: getComputedStyle(tip).backgroundColor,
        visible: rect.top >= 0 && rect.bottom <= innerHeight, detail: tip.classList.contains('vc-tooltip-detail') };
    })()`);
    assert.equal(help.surface, instantSurface.surface);
    assert.equal(help.visible && help.detail, true);
    await move(help.x, help.y);
    assert.match(await evaluate(`new Promise(resolve => setTimeout(() => resolve(tooltipTest.tip()?.textContent), 180))`), /^Compose using/);
    await key('Escape', 27);
    assert.equal(await text(), null);

    for (const [selector, expected] of [
      [instrument, /^Instrument:/],
      ['input[aria-label="Volume M1"]', /^Volume M1/],
      ['.track-reroll-actions .mute-toggle', /^Mute /],
    ]) {
      await hover(selector);
      await afterFrames();
      assert.equal(await text(), null);
      assert.match(await waitForTip(), expected);
    }

    await move(2, 2);
    await evaluate(`document.querySelector('button[aria-label="Stop"]').focus()`);
    await key('Tab', 9);
    await afterFrames();
    assert.equal(await text(), null);
    assert.equal(await waitForTip(), 'Play');
    await key('Escape', 27);
    assert.equal(await text(), null);

    const channel = 'vc-select.track-channel:not([aria-disabled="true"])';
    const expanded = value => evaluate(`tooltipTest.wait(
      () => document.querySelector(${JSON.stringify(channel)}).getAttribute('aria-expanded') === ${JSON.stringify(value)},
      'MIDI channel popup ' + ${JSON.stringify(value)})`);
    await evaluate(`document.querySelector(${JSON.stringify(channel)}).focus()`);
    await afterFrames();
    assert.equal(await text(), null);
    assert.match(await waitForTip(), /^MIDI channel \d/);
    await key('ArrowDown', 40);
    await expanded('true');
    assert.equal(await text(), null);
    await key('Escape', 27);
    await expanded('false');

    const spice = '.transport-dock [aria-label="Chord spice"] [data-spice="m"]';
    await evaluate(`document.querySelector('.transport-dock .chordlet-label').dispatchEvent(
      new PointerEvent('pointerdown', { bubbles: true, cancelable: true, button: 1 }))`);
    await hover(spice);
    await afterFrames();
    assert.match(await text(), /\d of \d notes in/);
    await key('Escape', 27);
    await move(2, 2);

    const preference = 'input[aria-label="Settings explainers as tooltips"]';
    await evaluate(`(async () => {
      document.querySelector('button[aria-label="Settings"]').click();
      (await tooltipTest.wait(() => document.querySelector('[aria-label="Appearance General"]'), 'appearance navigation')).click();
      (await tooltipTest.wait(() => document.querySelector(${JSON.stringify(preference)}), 'explainer preference')).focus();
    })()`);
    await afterFrames();
    assert.equal(await text(), null);
    assert.match(await waitForTip(), /^Show explanations on hover or keyboard focus/);
    await evaluate(`(async () => {
      document.querySelector('[aria-label="Appearance Arrangement"]').click();
      (await tooltipTest.wait(() => document.querySelector('input[aria-label="Cell Brightness"]'), 'brightness control')).focus();
    })()`);
    await afterFrames();
    assert.equal(await text(), null);
    assert.match(await waitForTip(), /^Adjust arrangement cell backgrounds/);
    await evaluate(`(async () => {
      document.querySelector('[aria-label="Appearance General"]').click();
      await tooltipTest.wait(() => document.querySelector(${JSON.stringify(preference)}), 'explainer preference');
    })()`);
    await evaluate(`document.querySelector(${JSON.stringify(preference)}).click()`);
    await afterFrames();
    assert.equal(await text(), null);
    assert.equal(await evaluate(`getComputedStyle(document.querySelector(${JSON.stringify(preference)}).closest('.setting-row').querySelector('p')).position`), 'static');
  } finally { await closeBrowser({ child, server, socket }); }
});
