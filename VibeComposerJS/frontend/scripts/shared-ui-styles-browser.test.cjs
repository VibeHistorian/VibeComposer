const assert = require('node:assert/strict');
const test = require('node:test');
const { findBrowser, connectToPage, createBrowserServer, listen, launchBrowser, closeBrowser, requireProductionBuild } = require('./support/browser-harness.cjs');
const browser = findBrowser();

test('shared button hover, disabled, toggle and settings-row styles work in production', { skip: !browser, timeout: 45000 }, async () => {
  const server = createBrowserServer({ root: requireProductionBuild() });
  await listen(server);
  const child = launchBrowser(browser, ['--headless', '--remote-debugging-port=0', '--no-first-run', '--disable-background-networking', '--window-size=1440,1100', 'http://127.0.0.1:' + server.address().port]);
  let socket;
  try {
    const connection = await connectToPage(child);
    socket = connection.socket;
    const evaluate = async expression => {
      const result = await connection.command('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
      assert.equal(result.exceptionDetails, undefined, JSON.stringify(result.exceptionDetails));
      return result.result.value;
    };
    await evaluate(`(async () => {
      window.uiStyleWait = async read => {
        for (let i = 0; i < 200; i++) { const value = read(); if (value) return value; await new Promise(r => setTimeout(r, 25)); }
        throw Error('UI style fixture did not become ready');
      };
      await uiStyleWait(() => document.querySelector('vc-compact-toggle button'));
      window.uiStyleProbe = (source, disabled = false) => {
        document.getElementById('ui-style-probe')?.remove();
        const probe = source.cloneNode(true);
        probe.id = 'ui-style-probe'; probe.disabled = disabled;
        probe.style.cssText = 'position:fixed;left:30px;top:30px;width:150px;height:35px;z-index:10000';
        source.parentElement.append(probe);
        return probe;
      };
      uiStyleProbe(document.querySelector('vc-compact-toggle button'));
      document.getElementById('ui-style-probe').setAttribute('aria-pressed','false');
    })()`);
    const move = async (x,y) => connection.command('Input.dispatchMouseEvent', { type:'mouseMoved', x, y });
    const style = () => evaluate(`(() => { const e=document.getElementById('ui-style-probe'), s=getComputedStyle(e); return { hovered:e.matches(':hover'), background:s.backgroundColor, border:s.borderTopStyle, color:s.color, shadow:s.boxShadow }; })()`);
    await move(500,20);
    const off = await style();
    await move(50,45);
    const hover = await style();
    assert.equal(hover.hovered, true);
    assert.notEqual(hover.background, off.background, 'enabled toggle has hover feedback');
    await evaluate(`document.getElementById('ui-style-probe').setAttribute('aria-pressed','true')`);
    const activeHover = await style();
    await move(500,20);
    assert.deepEqual(await style(), { ...activeHover, hovered:false }, 'steady active toggle retains its surface while hovered');
    await evaluate(`document.getElementById('ui-style-probe').setAttribute('aria-pressed','mixed')`);
    assert.equal((await style()).border, 'dashed');
    await evaluate(`uiStyleProbe(document.querySelector('.add-track-button'), true)`);
    const disabled = await style();
    await move(50,45);
    assert.deepEqual(await style(), { ...disabled, hovered:true }, 'disabled workspace action does not brighten');
    await evaluate(`(async () => {
      document.getElementById('ui-style-probe').remove();
      document.querySelector('[aria-label="Settings"]').click();
      (await uiStyleWait(() => document.querySelector('[aria-label="Behavior Melody"]'))).click();
      const contextual=await uiStyleWait(() => document.querySelector('[aria-label="Contextual Melody"]'));
      if(contextual.checked)contextual.click();
      const action=await uiStyleWait(() => document.querySelector('[aria-label="Default block change order"]'));
      uiStyleProbe(action);
    })()`);
    await move(500,20);
    const melodyOff = await style();
    await move(50,45);
    assert.notEqual((await style()).background, melodyOff.background, 'Melody preference actions share hover feedback');
    assert.equal(await evaluate(`getComputedStyle(document.querySelector('vc-melody-global-settings .setting-row')).display`), 'grid');
    await evaluate(`document.getElementById('ui-style-probe').focus()`);
    assert.equal(await evaluate(`getComputedStyle(document.getElementById('ui-style-probe')).outlineStyle`), 'solid');
    await evaluate(`document.querySelector('vc-settings-dialog').classList.add('explainers-as-tooltips')`);
    assert.equal(await evaluate(`getComputedStyle(document.querySelector('vc-melody-global-settings .setting-copy p')).position`), 'absolute', 'nested settings retain tooltip explainers');
    await evaluate(`(async () => {
      document.getElementById('ui-style-probe').remove();
      document.querySelector('[aria-label="Close settings"]').click();
      document.querySelector('.score-ruler-section').click();
      uiStyleProbe(await uiStyleWait(() => document.querySelector('.inspector-section-heading button')));
    })()`);
    await move(500,20);
    const rerollOff = await style();
    await move(50,45);
    assert.notEqual((await style()).background, rerollOff.background, 'quiet inspector Reroll action has hover feedback');
    const settings = await evaluate(`(async () => {
      document.getElementById('ui-style-probe').remove();
      document.querySelector('[aria-label="Settings"]').click();
      await uiStyleWait(() => document.querySelector('vc-settings-dialog'));
      window.dispatchEvent(new Event('pagehide'));
      const before = sessionStorage.getItem('vibecomposer.project.v12');
      const rows = [];
      for (const [category, label] of [['Behavior Arrangement','Generate arrangement variations'], ['Appearance Score','Highlight audible'], ['MIDI','Recording tick']]) {
        const navigation = await uiStyleWait(() => [...document.querySelectorAll('.settings-sidebar button')].find(b => b.getAttribute('aria-label') === category || b.querySelector('span')?.textContent.trim() === category));
        navigation.click();
        const input = await uiStyleWait(() => document.querySelector('input[aria-label="' + label + '"]'));
        const previous = input.checked;
        input.click();
        await new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)));
        const row = input.closest('.setting-row');
        const descriptionId = input.getAttribute('aria-describedby');
        rows.push({ changed: input.checked !== previous, heading: !!row.querySelector('.setting-copy h3'), description: descriptionId ? row.contains(document.getElementById(descriptionId)) : !row.querySelector('.setting-copy p').hasAttribute('id') });
        input.click();
      }
      window.dispatchEvent(new Event('pagehide'));
      const uiLeavesProjectUnchanged = sessionStorage.getItem('vibecomposer.project.v12') === before;
      document.querySelector('[aria-label="Behavior Harmony"]').click();
      const harmony = await uiStyleWait(() => document.querySelector('input[aria-label="Progressive voicing"]'));
      harmony.click();
      await new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)));
      window.dispatchEvent(new Event('pagehide'));
      const project = JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));
      return { rows, uiLeavesProjectUnchanged, harmonySaved: project.harmonySettings.progressiveVoicing === harmony.checked };
    })()`);
    assert.equal(settings.uiLeavesProjectUnchanged, true, 'preference rows retain UI-only ownership');
    assert.equal(settings.harmonySaved, true, 'musical rows retain project command ownership');
    assert.equal(settings.rows.length, 3);
    assert.ok(settings.rows.every(row => row.changed && row.heading && row.description), JSON.stringify(settings));
  } finally { await closeBrowser({ child, server, socket }); }
});
