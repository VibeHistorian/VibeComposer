const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const { findBrowser, requireProductionBuild, createBrowserServer, listen, launchBrowser,
  connectToPage, watchBrowser, closeBrowser } = require('./support/browser-harness.cjs');

const browser = findBrowser();
for (const width of [700, 780, 1050, 1400, 1440, 1920, 2560]) {
  test(`global root and spice menus align with their chord at ${width}px`, { skip: !browser }, async () => {
    const root = requireProductionBuild();
    const server = createBrowserServer({ root, html: fs.readFileSync(path.join(root, 'index.html'), 'utf8') });
    await listen(server);
    const profile = fs.mkdtempSync(path.resolve(__dirname, '../.angular/chordlet-popup-'));
    const child = launchBrowser(browser, ['--headless', '--disable-gpu', '--no-first-run',
      '--disable-background-networking', '--no-default-browser-check', '--remote-debugging-port=0',
      '--window-size=' + width + ',1000', '--user-data-dir=' + profile,
      'http://127.0.0.1:' + server.address().port + '/'], { stdio: ['ignore', 'ignore', 'pipe'] });
    let reject;
    const failure = new Promise((_, fail) => { reject = fail; });
    failure.catch(() => {});
    const watchdog = watchBrowser(child, reject, 30000);
    let socket;
    try {
      await Promise.race([(async () => {
        const connection = await connectToPage(child);
        socket = connection.socket;
        const command = connection.command;
        const evaluate = async expression => {
          const output = await command('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
          assert.equal(output.exceptionDetails, undefined, JSON.stringify(output));
          return output.result.value;
        };
        await command('Emulation.setDeviceMetricsOverride', { width, height: 1000, deviceScaleFactor: 1, mobile: false });
        await evaluate(`(async () => {
          for (let i = 0; i < 200; i++) {
            if (document.querySelector('.global-harmony .chordlet-label')) {
              await document.fonts.ready;
              await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
              return;
            }
            await new Promise(resolve => setTimeout(resolve, 25));
          }
          throw new Error('Global harmony did not render');
        })()`);
        const spice = await evaluate(`(async () => {
          const input = document.querySelector('input[aria-label="Harmony spiciness"]');
          const transpose = document.querySelector('input[aria-label="Transpose in semitones"]');
          const a = input.getBoundingClientRect(), b = transpose.getBoundingClientRect();
          const heading = input.closest('vc-compact-knob').querySelector('vc-control-heading');
          const h = heading.getBoundingClientRect();
          input.value = '100';
          input.dispatchEvent(new Event('input', { bubbles: true }));
          input.dispatchEvent(new Event('change', { bubbles: true }));
          await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
          document.querySelector('.global-harmony button[aria-label="Generate chord progression"]').click();
          await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
          return { ordered: a.left >= b.right, labelLeft: h.right <= a.left + 1, visible: a.right <= innerWidth,
            label: heading.textContent.trim(), value: input.value,
            symbols: [...document.querySelectorAll('.global-harmony .chordlet-label')].map(el => el.textContent.trim()) };
        })()`);
        assert.ok(spice.ordered && spice.labelLeft && spice.visible, JSON.stringify(spice));
        assert.equal(spice.label, '🌶');
        assert.equal(spice.value, '100');
        assert.ok(spice.symbols.every(symbol => /[679\[]/.test(symbol)), JSON.stringify(spice));
        const markers = await evaluate(`(() => [...document.querySelectorAll('.global-harmony .chordlet')].map(chord => {
          const marker = chord.querySelector('.chordlet-function');
          const box = chord.getBoundingClientRect(), label = marker.getBoundingClientRect();
          const list = chord.closest('.chordlet-list').getBoundingClientRect();
          return { text: marker.textContent, crosses: label.top < box.bottom && label.bottom > box.bottom,
            visible: label.bottom <= list.bottom, centered: Math.abs((label.left + label.right) / 2 - (box.left + box.right) / 2) < 1,
            mask: getComputedStyle(chord, '::before').maskImage };
        }))()`);
        assert.ok(markers.length > 0);
        for (const marker of markers) {
          assert.match(marker.text, /[IViv]/);
          assert.ok(marker.crosses && marker.visible && marker.centered, JSON.stringify(marker));
          assert.notEqual(marker.mask, 'none');
        }
        for (const kind of ['root', 'spice']) {
          const point = await evaluate(`(() => {
            const chord = document.querySelector('.global-harmony .chordlet-label').getBoundingClientRect();
            return { x: chord.left + chord.width / 2, y: chord.top + chord.height / 2 };
          })()`);
          const button = kind === 'root' ? 'right' : 'middle';
          await command('Input.dispatchMouseEvent', { type: 'mousePressed', ...point, button, clickCount: 1 });
          await command('Input.dispatchMouseEvent', { type: 'mouseReleased', ...point, button, clickCount: 1 });
          const bounds = await evaluate(`(async () => {
            for (let i = 0; i < 100; i++) {
              const menu = document.querySelector('.global-harmony [role="menu"]');
              if (menu) {
                await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
                return { menu: menu.getBoundingClientRect().toJSON(),
                  chord: document.querySelector('.global-harmony .chordlet-label').getBoundingClientRect().toJSON(),
                  viewport: { width: innerWidth, height: innerHeight } };
              }
              await new Promise(resolve => setTimeout(resolve, 25));
            }
            throw new Error('${kind} menu did not open');
          })()`);
          if (kind === 'root') {
            const choices = await evaluate(`(() => [...document.querySelectorAll('.global-harmony .root-choice')].map(button => {
              const marker = button.querySelector('.chordlet-function');
              const box = button.getBoundingClientRect(), label = marker.getBoundingClientRect();
              const menu = button.closest('[role="menu"]').getBoundingClientRect();
              const warning = button.querySelector('.root-scale-warning');
              const warningBox = warning?.getBoundingClientRect();
              return { text: marker.textContent, accessible: button.getAttribute('aria-label').includes(marker.textContent),
                root: button.dataset.root, warning: !!warning,
                warningCrosses: !warningBox || warningBox.top < box.top && warningBox.bottom > box.top && warningBox.left > box.left + box.width / 2,
                warningAccessible: !warning || button.getAttribute('aria-label').includes('no spice'),
                crosses: label.top < box.bottom && label.bottom > box.bottom,
                visible: label.bottom < menu.bottom, mask: getComputedStyle(button, '::before').maskImage };
            }))()`);
            assert.equal(choices.length, 12);
            assert.deepEqual(choices.filter(choice => choice.warning).map(choice => choice.root), ['C♯', 'E♭', 'F♯', 'A♭', 'B♭']);
            assert.deepEqual(choices.map(choice => choice.text), ['I', '♭II', 'II', '♭III', 'III', 'IV', '♯IV', 'V', '♭VI', 'VI', '♭VII', 'VII']);
            for (const choice of choices) {
              assert.ok(choice.accessible && choice.crosses && choice.visible, JSON.stringify(choice));
              assert.ok(choice.warningCrosses && choice.warningAccessible, JSON.stringify(choice));
              assert.notEqual(choice.mask, 'none');
            }
          }
          const screenshot = await command('Page.captureScreenshot', { format: 'png' });
          fs.writeFileSync(path.resolve(__dirname, '../.angular/global-harmony-' + kind + '-' + width + '.png'), Buffer.from(screenshot.data, 'base64'));
          const { menu, chord, viewport } = bounds;
          assert.ok(menu.left >= 0 && menu.right <= viewport.width && menu.top >= 0 && menu.bottom <= viewport.height,
            kind + ' stays within viewport: ' + JSON.stringify(bounds));
          assert.ok(Math.abs(menu.left - Math.max(8, Math.min(viewport.width - menu.width - 8, chord.left))) <= 1,
            kind + ' aligns horizontally with its chord: ' + JSON.stringify(bounds));
          assert.ok(Math.abs(menu.bottom - (chord.top - 6)) <= 1,
            kind + ' opens immediately above its chord: ' + JSON.stringify(bounds));
          if (kind === 'root') {
            await evaluate(`document.querySelector('.global-harmony .root-choice[data-root="C"]').click()`);
          } else {
            const minorNinth = await evaluate(`(async () => {
              const choice = [...document.querySelectorAll('.global-harmony .spice-choice')].find(button => button.textContent === 'm9');
              const overlap = Number(choice.dataset.keyOverlap);
              choice.click();
              await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
              const chord = document.querySelector('.global-harmony .chordlet');
              return { overlap, symbol: chord.querySelector('.chordlet-label').textContent,
                function: chord.querySelector('.chordlet-function').textContent };
            })()`);
            assert.equal(minorNinth.overlap, 3 / 5, 'Cm9 has Eb and Bb outside C major');
            assert.equal(minorNinth.symbol, 'Cm9');
            assert.equal(minorNinth.function, 'i');
          }
          await command('Input.dispatchKeyEvent', { type: 'keyDown', key: 'Escape', code: 'Escape', windowsVirtualKeyCode: 27 });
          await command('Input.dispatchKeyEvent', { type: 'keyUp', key: 'Escape', code: 'Escape', windowsVirtualKeyCode: 27 });
        }
        const layout = await evaluate(`(async () => {
          const dock = document.querySelector('.transport-dock');
          const list = document.querySelector('.global-harmony .chordlet-list');
          const progress = document.querySelector('.transport-progress');
          const before = { height: dock.offsetHeight, progress: progress.offsetWidth, harmony: list.clientWidth };
          for (let i = 0; i < 32; i++) {
            const add = document.querySelector('.global-harmony [aria-label="Add chord"]');
            if (add.disabled) break;
            add.click();
            await new Promise(resolve => requestAnimationFrame(resolve));
          }
          await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
          list.scrollLeft = list.scrollWidth;
          return { before, height: dock.offsetHeight, progress: progress.offsetWidth, harmony: list.clientWidth,
            scrolling: list.scrollWidth > list.clientWidth, reachable: list.scrollLeft > 0,
            scrollbarInside: list.getBoundingClientRect().bottom <= dock.getBoundingClientRect().bottom,
            noOverlap: innerWidth <= 1050 || document.querySelector('.score-panel').getBoundingClientRect().bottom <= dock.getBoundingClientRect().top };
        })()`);
        assert.equal(layout.height, layout.before.height, JSON.stringify(layout));
        assert.ok((!layout.scrolling || layout.reachable) && layout.scrollbarInside && layout.noOverlap, JSON.stringify(layout));
        if (width <= 1920) assert.ok(layout.scrolling, JSON.stringify(layout));
        if (width >= 1920) {
          assert.ok(layout.harmony > layout.before.harmony, JSON.stringify(layout));
          assert.ok(layout.progress < layout.before.progress, JSON.stringify(layout));
        }
      })(), failure]);
    } finally {
      await closeBrowser({ child, server, socket, watchdog });
    }
  });
}
