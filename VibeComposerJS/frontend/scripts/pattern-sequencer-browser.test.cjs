const { findBrowser, createBrowserServer, listen, launchBrowser, closeBrowser, watchBrowser, requireProductionBuild } = require('./support/browser-harness.cjs');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const browser = findBrowser();

test('compact pattern popup edits independent drum rows and commits velocities once', {
  skip: browser ? false : 'Set OVERVIEW_TEST_BROWSER to a Chromium executable',
}, async () => {
  const root = requireProductionBuild();
  const index = fs.readFileSync(path.join(root, 'index.html'), 'utf8');
  const script = `<script>
  (async () => {
    const wait = async (fn, label) => { for(let i=0;i<300;i++){ const value=fn(); if(value) return value; await new Promise(r=>setTimeout(r,30)); } throw Error(label); };
    const read = () => JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));
    const check = (value, label) => { if(!value) throw Error(label); };
    try {
      sessionStorage.clear();
      await wait(()=>document.querySelector('.track-group.drums .group-select'), 'initial');
      for (const role of ['bass', 'chords', 'arpeggio', 'drums']) {
        document.querySelector('.track-group.' + role + ' .track-select').click();
        const editor = await wait(()=>document.querySelector('vc-part-settings-editor[data-control-role="' + role + '"] [data-control-key="rhythm"]'), role + ' rhythm');
        const selector = editor.querySelector('vc-select');
        await changeControl(selector, 'half');
        const preview = await wait(()=>editor.querySelector('.rhythm-preview-grid'), role + ' preview');
        check(!document.querySelector('.part-control[data-control-key="customPattern"]'), role + ' has one combined control');
        check(editor.querySelectorAll(':scope > .control-content > vc-control-heading').length===1 && editor.querySelectorAll(':scope > .control-content > vc-control-heading .inherit-snowflake').length<=1, role + ' shared inheritance');
        const selectBox=selector.getBoundingClientRect(), gridBox=preview.getBoundingClientRect(), box=editor.getBoundingClientRect();
        check(selectBox.height<=22 && gridBox.height<=18 && gridBox.top>=selectBox.bottom && gridBox.right<=box.right && gridBox.bottom<=box.bottom, role + ' compact stacked layout');
        const flip=editor.querySelector('.flip-button');
        check(flip && flip.getBoundingClientRect().height<=22, role + ' compact flip');
      }
      document.querySelector('.track-group.drums .group-select').click();
      const count = document.querySelector('input[aria-label="Drums track count"]');
      await changeControl(count, '3');
      await wait(()=>document.querySelectorAll('.track-group.drums .track-select').length===3,'three drums');
      const ids = read().tracks.filter(t=>t.role==='drums').map(t=>t.id);
      document.querySelector('.track-group.drums .track-select').click();
      await wait(()=>document.querySelector('.track-group.drums .track-select').getAttribute('aria-pressed')==='true','selected drum track');
      await wait(()=>document.querySelector('button[aria-label="Edit rhythm pattern"]'),'preview');
      check(!document.querySelector('.part-settings-panel [aria-label="Multiply Hits"], #part-inspector [aria-label="Multiply Hits"]'), 'multiplier belongs only in popup');
      check(document.querySelector('[aria-label="Toggle loop"]').getAttribute('aria-pressed') === 'true', 'loop enabled by default');
      const launcher=document.querySelector('button[aria-label="Edit rhythm pattern"]');
      check(launcher.getBoundingClientRect().width<=112 && launcher.getBoundingClientRect().height<=18, 'tiny preview');
      check(!document.querySelector('.custom-pattern-grid, .velocity-pattern-grid'),'no inline editors');
      launcher.click();
      await wait(()=>document.querySelectorAll('vc-pattern-sequencer .track').length===3,'all drum rows');
      const popup=document.querySelector('vc-pattern-sequencer');
      check(!popup.querySelector('.velocity-pattern-grid'),'velocities hidden');
      const storedDrums=read().tracks.filter(t=>t.role==='drums');
      const percussion=popup.querySelectorAll('.pattern-row .identity > span');
      check([...percussion].every(span=>!span.textContent.includes('NaN')), 'valid percussion names');
      check([...percussion].every(span=>span.textContent.trim().length>0), 'percussion names present');
      const multiply=popup.querySelector('[aria-label="Multiply Hits"]');
      check(multiply && multiply.getAttribute("aria-disabled") !== "true", 'popup multiplier enabled for active track');
      const oldHits=storedDrums[0].generatorSettings.hitsPerPattern ?? 4;
      await changeControl(multiply, '0.5');
      await wait(()=>read().tracks.find(t=>t.id===ids[0]).generatorSettings.hitsPerPattern===Math.max(1,Math.trunc(oldHits*0.5)),'multiply selected drum');
      check(multiply.getAttribute("value")==='', 'multiplier resets after action');
      check(JSON.stringify(read().tracks.filter(t=>t.role==='drums').slice(1))===JSON.stringify(storedDrums.slice(1)), 'multiplier retains other drum tracks');
      const row=id=>popup.querySelector('[data-track-id="'+id+'"]');
      const setSteps = async (id, count) => {
        const input = row(id).querySelector('input[type="number"]');
        await changeControl(input, String(count));
        await wait(()=>row(id).querySelectorAll('.step').length===count, 'set steps ' + count);
      };
      const checkPreview = async count => {
        await setSteps(ids[0], count);
        await wait(()=>launcher.querySelectorAll('.rhythm-preview-slot').length===count,'compact preview step count');
        const bounds=launcher.getBoundingClientRect();
        const parent=launcher.closest('.part-control').getBoundingClientRect();
        check(bounds.left>=parent.left && bounds.right<=parent.right, 'preview fits its control');
        const cells=[...launcher.querySelectorAll('.rhythm-preview-slot')].map(c=>c.getBoundingClientRect());
        cells.forEach((cell,i)=>{
          check(cell.left>=bounds.left && cell.right<=bounds.right && cell.top>=bounds.top && cell.bottom<=bounds.bottom, 'preview step contained');
          if (i) check(Math.abs(cell.left-cells[0].left-i*(launcher.clientWidth-6)/count)<1, 'preview onset time '+count+'/'+i);
        });
        if(count===4) check(Math.abs((cells[1].left-cells[0].left)/(cells[0].width+1)-2)<.1,'four hits alternate with spaces');
      };
      for(const count of [4,5,8,12,16,31,32]) await checkPreview(count);
      await setSteps(ids[2], 4);
      const checkPositions = (selector, positions) => {
        const reference = [...row(ids[0]).querySelectorAll(selector)].map(cell=>cell.getBoundingClientRect());
        const sparse = [...row(ids[2]).querySelectorAll(selector)].map(cell=>cell.getBoundingClientRect());
        check(sparse.length===positions.length, 'sparse lane cell count');
        sparse.forEach((cell, index)=>{
          check(Math.abs(cell.left-reference[positions[index]].left)<1, selector + ' time position ' + index);
          check(Math.abs(cell.width-reference[positions[index]].width)<1, selector + ' shared cell width');
        });
      };
      checkPositions('.step', [0,8,16,24]);
      await setSteps(ids[2], 5);
      const referenceLane = row(ids[0]).querySelector('.custom-pattern-grid').getBoundingClientRect();
      [...row(ids[2]).querySelectorAll('.step')].forEach((cell,index)=>{
        check(Math.abs(cell.getBoundingClientRect().left-referenceLane.left-index*(referenceLane.width+3)/5)<1, 'fractional subdivision position');
      });
      await setSteps(ids[2], 4);
      const before=JSON.stringify(read().tracks.find(t=>t.id===ids[0]));
      row(ids[1]).querySelector('input[type="number"]').value='8';
      row(ids[1]).querySelector('input[type="number"]').dispatchEvent(new Event('change',{bubbles:true}));
      await wait(()=>row(ids[1]).querySelectorAll('.step').length===8,'independent steps');
      const original=row(ids[1]).querySelector('.step').getAttribute('aria-pressed');
      row(ids[1]).querySelector('.step').click();
      await wait(()=>row(ids[1]).querySelector('.step').getAttribute('aria-pressed')!==original,'step edit');
      check(JSON.stringify(read().tracks.find(t=>t.id===ids[0]))===before,'other track retained');
      const settings=read().tracks.find(t=>t.id===ids[1]).generatorSettings;
      check(settings.rhythm==='custom' && settings.customPattern.length===32,'bake rhythm');
      popup.querySelector('header button[aria-pressed]').click();
      await wait(()=>popup.querySelectorAll('.velocity-pattern-grid').length===3,'velocity toggle');
      checkPositions('.velocity-slot', [0,8,16,24]);
      const velocity=row(ids[1]).querySelector('input[type="range"]');
      velocity.value='22'; velocity.dispatchEvent(new Event('input',{bubbles:true}));
      await wait(()=>row(ids[1]).querySelector('output').textContent==='22','velocity draft');
      check(read().tracks.find(t=>t.id===ids[1]).generatorSettings.customVelocities?.[0]!==22,'no commit during drag');
      velocity.dispatchEvent(new Event('change',{bubbles:true}));
      await wait(()=>read().tracks.find(t=>t.id===ids[1]).generatorSettings.customVelocities?.[0]===22,'velocity commit');
      check(read().tracks.find(t=>t.id===ids[1]).generatorSettings.useCustomVelocities===true,'enable custom velocities');
      row(ids[1]).querySelector('input[type="number"]').value='4';
      row(ids[1]).querySelector('input[type="number"]').dispatchEvent(new Event('change',{bubbles:true}));
      await wait(()=>row(ids[1]).querySelectorAll('.step').length===4,'shrink');
      check(read().tracks.find(t=>t.id===ids[1]).generatorSettings.customVelocities.length===32,'hidden velocities retained');
      popup.querySelector('button[aria-label="Close pattern sequencer"]').click();
      await wait(()=>!document.querySelector('vc-pattern-sequencer'),'close');
      document.querySelector('.project-tools button[aria-label="Undo"]').click();
      await wait(()=>read().tracks.find(t=>t.id===ids[1]).generatorSettings.hitsPerPattern===8,'undo hits');
      document.querySelector('.project-tools button[aria-label="Undo"]').click();
      await wait(()=>read().tracks.find(t=>t.id===ids[1]).generatorSettings.customVelocities?.[0]!==22,'one undo velocity');
      document.querySelector('.track-group.melody .track-select').click();
      const melodyControl=await wait(()=>document.querySelector('vc-part-settings-editor[data-control-role="melody"] [data-control-key="rhythm"]'),'melody rhythm selector');
      check(melodyControl.querySelector('vc-select').getAttribute("value")==='melody1','normal melody rhythm default');
      check(!melodyControl.querySelector('.flip-button, .mini-preview'),'normal melody hides flip and preview');
      const melodySettings=()=>read().tracks.find(t=>t.role==='melody').generatorSettings;
      const rhythm=melodyControl.querySelector('vc-select');
      await changeControl(rhythm, 'durations');
      const durationsPreview=await wait(()=>melodyControl.querySelector('[aria-label="Edit custom melody durations"]'),'custom melody preview');
      check(melodyControl.querySelector('.flip-button'),'custom melody flip');
      const beforeDraft=sessionStorage.getItem('vibecomposer.project.v12');
      durationsPreview.click();
      let durations=await wait(()=>document.querySelector('vc-durations-dialog'),'duration popup');
      const edit=(label,value)=>{
        const input=durations.querySelector('[aria-label="'+label+'"]'); input.value=value; input.dispatchEvent(new Event('input',{bubbles:true}));
      };
      const durationButton=label=>[...durations.querySelectorAll('button')].find(button=>button.textContent.trim()===label);
      await wait(()=>durations.querySelectorAll('.duration-row').length===10,'ten duration lanes');
      edit('Lane 1 durations','-4');
      await wait(()=>durations.querySelector('.error')?.textContent.includes('only pauses'),'invalid durations explained');
      check(durationButton('Save durations').disabled,'invalid durations cannot save');
      check(sessionStorage.getItem('vibecomposer.project.v12')===beforeDraft,'draft does not mutate project');
      edit('Lane 1 durations','1, -1, 1, -1');
      await wait(()=>!durationButton('Validate durations').disabled,'valid draft can be checked');
      durationButton('Validate durations').click();
      await wait(()=>!durationButton('Save durations').disabled,'valid durations can save');
      edit('Lane 1 durations','0.5, -0.5, 1, -2');
      await wait(()=>durationButton('Save durations').disabled,'edits invalidate generation check');
      durationButton('Cancel').click();
      await wait(()=>!document.querySelector('vc-durations-dialog'),'cancel durations');
      check(sessionStorage.getItem('vibecomposer.project.v12')===beforeDraft,'cancel preserves project');
      durationsPreview.click();
      durations=await wait(()=>document.querySelector('vc-durations-dialog'),'reopen durations');
      await wait(()=>durations.querySelector('[aria-label="Lane 1 durations"]'),'reopened duration input');
      edit('Lane 1 durations','1, -1, 1, -1');
      durationButton('Validate durations').click();
      await wait(()=>!durationButton('Save durations').disabled,'check before commit');
      durationButton('Save durations').click();
      await wait(()=>!document.querySelector('vc-durations-dialog'),'save closes durations');
      check(JSON.stringify(melodySettings().customDurations[0])==='[1,-1,1,-1]','durations saved');
      document.querySelector('.project-tools button[aria-label="Undo"]').click();
      await wait(()=>!melodySettings().customDurations,'one undo for duration save');
      await wait(()=>!document.querySelector('.project-tools button[aria-label="Redo"]').disabled,'redo enabled');
      document.querySelector('.project-tools button[aria-label="Redo"]').click();
      await wait(()=>!!melodySettings().customDurations,'redo durations');
      melodyControl.querySelector('.flip-button').click();
      await wait(()=>melodySettings().patternFlip===true,'flip saved');
      await wait(()=>[...melodyControl.querySelectorAll('.duration-preview > span')].map(slot=>slot.classList.contains('active')).join(',')==='false,true,false,true','flip preview complements notes');
      await changeControl(rhythm, 'melody1');
      await wait(()=>!melodyControl.querySelector('.flip-button, .mini-preview'),'normal melody hides custom widgets again');
      check(melodySettings().customDurations[0][0]===1,'normal retains custom duration data');
      check((await selectOptions(rhythm)).find(option=>option.value==='durations').textContent.trim()==='durations','melody option renamed');
      for (const role of ['bass','arpeggio']) {
        document.querySelector('.track-group.'+role+' .track-select').click();
        const control=await wait(()=>document.querySelector('vc-part-settings-editor[data-control-role="'+role+'"] [data-control-key="rhythm"]'),role+' rhythm');
        const select=control.querySelector('vc-select');
        check((await selectOptions(select)).some(option=>option.value==='custom'),'custom grid retained');
        await changeControl(select, 'durations');
        const preview=await wait(()=>control.querySelector('.duration-preview'),role+' compact durations');
        check(!control.querySelector('.rhythm-preview-grid'),role+' replaces grid');
        const settings=()=>read().tracks.find(track=>track.role===role).generatorSettings;
        const before=sessionStorage.getItem('vibecomposer.project.v12');
        preview.click(); durations=await wait(()=>document.querySelector('vc-durations-dialog'),role+' shared editor');
        await wait(()=>durations.querySelectorAll('.duration-row').length===10,role+' lanes');
        edit('Lane 1 durations','1, -1, 0.5, -1.5');
        durationButton('Cancel').click();
        await wait(()=>!document.querySelector('vc-durations-dialog'),role+' cancel');
        check(sessionStorage.getItem('vibecomposer.project.v12')===before,role+' cancel preserves state');
        preview.click(); durations=await wait(()=>document.querySelector('vc-durations-dialog'),role+' reopen');
        await wait(()=>durations.querySelector('[aria-label="Lane 1 durations"]'),role+' ready');
        edit('Lane 1 durations','1, -1, 0.5, -1.5');
        durationButton('Validate durations').click();
        await wait(()=>!durationButton('Save durations').disabled,role+' valid');
        durationButton('Save durations').click();
        await wait(()=>!document.querySelector('vc-durations-dialog'),role+' save');
        check(JSON.stringify(settings().customDurations[0])==='[1,-1,0.5,-1.5]',role+' persisted');
        document.querySelector('.project-tools button[aria-label="Undo"]').click();
        await wait(()=>!settings().customDurations,role+' single undo');
      }
      document.querySelector('.track-group.arpeggio .track-select').click();
      const arpControl = await wait(()=>document.querySelector('vc-part-settings-editor[data-control-role="arpeggio"] [data-control-key="pattern"]'),'arp direction');
      await changeControl(arpControl.querySelector('vc-select'), 'random');
      const arpLauncher = await wait(()=>arpControl.querySelector('[aria-label="Edit arpeggio pattern"]'),'arp launcher');
      const arpSettings = ()=>read().tracks.find(track=>track.role==='arpeggio').generatorSettings;
      const arpBefore = sessionStorage.getItem('vibecomposer.project.v12');
      arpLauncher.click();
      let arpPopup = await wait(()=>document.querySelector('vc-arpeggio-pattern-dialog'),'arp popup');
      const arpSteps = ()=>arpPopup.querySelectorAll('.steps input');
      await wait(()=>arpSteps().length>0,'random pattern visible');
      check(!arpPopup.querySelector('textarea'),'no duplicated textarea');
      check(arpLauncher.getBoundingClientRect().height<=18 && arpControl.querySelector('vc-select').getBoundingClientRect().height<=22,'compact arp control');
      check([...arpPopup.querySelectorAll('.point-label')].every((label,index)=>label.textContent.trim()===arpSteps()[index].value),'point values match steps');
      check(arpPopup.querySelector('polyline').getAttribute('points').length>0,'random plotted');
      const arpButton = label=>[...arpPopup.querySelectorAll('button')].find(button=>button.textContent.trim()===label);
      await changeControl(arpPopup.querySelector('[aria-label="Load arpeggio direction"]'),'down');
      check(sessionStorage.getItem('vibecomposer.project.v12')===arpBefore,'loading direction stays draft');
      arpButton('Cancel').click();
      await wait(()=>!document.querySelector('vc-arpeggio-pattern-dialog'),'arp cancel');
      check(sessionStorage.getItem('vibecomposer.project.v12')===arpBefore,'arp cancel preserves state');
      arpLauncher.click();
      arpPopup=await wait(()=>document.querySelector('vc-arpeggio-pattern-dialog'),'arp reopen');
      await changeControl(arpPopup.querySelector('[aria-label="Arpeggio pattern step count"]'),'4');
      await wait(()=>arpPopup.querySelectorAll('.steps input').length===4,'arp step fields');
      [-2,0,4,7].forEach((value,index)=>{ const input=arpSteps()[index]; input.value=String(value); input.dispatchEvent(new Event('input',{bubbles:true})); });
      await wait(()=>[...arpPopup.querySelectorAll('.point-label')].map(label=>label.textContent.trim()).join(',')==='-2,0,4,7','graph labels update');
      check(!arpButton('Save pattern').disabled,'arp save enabled');
      arpButton('Save pattern').click();
      await wait(()=>!document.querySelector('vc-arpeggio-pattern-dialog'),'arp save closes');
      check(arpSettings().pattern==='custom' && JSON.stringify(arpSettings().arpPatternCustom)==='[-2,0,4,7]','arp save custom');
      document.querySelector('.project-tools button[aria-label="Undo"]').click();
      await wait(()=>arpSettings().pattern==='random','arp one undo');
      (await wait(()=>{ const button=document.querySelector('.project-tools button[aria-label="Redo"]'); return button && !button.disabled ? button : null; },'arp redo enabled')).click();
      await wait(()=>arpSettings().pattern==='custom','arp redo');
      await fetch('/result',{method:'POST',body:JSON.stringify({passed:true})});
    } catch(error){await fetch('/result',{method:'POST',body:JSON.stringify({error:error.message})});}
  })();</script>`;
  let resolve, reject;
  const result = new Promise((done, fail) => { resolve = done; reject = fail; });
  result.catch(() => {}); // CDP setup can fail before the result is awaited.
  const server = createBrowserServer({ root, html: index.replace('</body>', script + '</body>'), resolve, reject });
  await listen(server);
  const profile = fs.mkdtempSync(path.resolve(__dirname, '../.angular/pattern-sequencer-browser-'));
  const child = launchBrowser(browser, ['--headless', '--disable-gpu', '--no-first-run', '--disable-background-networking',
    '--disable-background-timer-throttling', '--disable-renderer-backgrounding', '--no-default-browser-check',
    '--user-data-dir=' + profile, 'http://127.0.0.1:' + server.address().port + '/'], { stdio: ['ignore', 'ignore', 'pipe'] });
  const watchdog = watchBrowser(child, reject, 30000);
  try {
    const actual = await result;
    assert.equal(actual.error, undefined, JSON.stringify(actual));
    assert.equal(actual.passed, true);
  } finally {
    await closeBrowser({ child, server, watchdog });
  }
});
