const { findBrowser, createBrowserServer, listen, launchBrowser, closeBrowser, watchBrowser, requireProductionBuild } = require('./support/browser-harness.cjs');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const browser = findBrowser();

test('compact pattern editors commit drum velocities and validate Melody grids', {
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
        const selector = editor.querySelector(':is(vc-select, vc-menu-select)');
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
      await wait(()=>read()?.tracks.filter(t=>t.role==='drums').length===3,'three drums persisted');
      const ids = read().tracks.filter(t=>t.role==='drums').map(t=>t.id);
      document.querySelector('.track-group.drums .track-select').click();
      await wait(()=>document.querySelector('.track-group.drums .track-select').getAttribute('aria-pressed')==='true','selected drum track');
      await wait(()=>document.querySelector('button[aria-label="Edit rhythm pattern"]'),'preview');
      check(!document.querySelector('.part-settings-panel [aria-label="Multiply Hits"], #part-inspector [aria-label="Multiply Hits"]'), 'multiplier belongs only in popup');
      check(document.querySelector('[aria-label="Toggle loop"]').getAttribute('aria-pressed') === 'true', 'loop enabled by default');
      const launcher=document.querySelector('button[aria-label="Edit rhythm pattern"]');
      check(launcher.getBoundingClientRect().width<=112 && launcher.getBoundingClientRect().height<=18, 'tiny preview');
      check(!document.querySelector('.custom-pattern-grid'),'no inline editors');
      launcher.click();
      await wait(()=>document.querySelectorAll('vc-pattern-sequencer .track').length===3,'all drum rows');
      const popup=document.querySelector('vc-pattern-sequencer');
      check(!popup.querySelector('.velocity-editing'),'velocities hidden');
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
        await wait(()=>read().tracks.find(t=>t.id===id).generatorSettings.hitsPerPattern===count, 'saved steps ' + count);
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
      const beforeStep=JSON.stringify(read().tracks.find(t=>t.id===ids[1]));
      row(ids[1]).querySelector('.step').click();
      await wait(()=>row(ids[1]).querySelector('.step').getAttribute('aria-pressed')!==original,'step edit');
      await wait(()=>JSON.stringify(read().tracks.find(t=>t.id===ids[1]))!==beforeStep,'step edit persisted');
      check(JSON.stringify(read().tracks.find(t=>t.id===ids[0]))===before,'other track retained');
      const settings=read().tracks.find(t=>t.id===ids[1]).generatorSettings;
      check(settings.rhythm==='custom' && settings.customPattern.length===32,'bake rhythm');
      const editedRow = row(ids[1]);
      const lane = editedRow.querySelector('.custom-pattern-grid');
      const checkbox = editedRow.querySelector('input[type="checkbox"]');
      const storedSettings = () => read().tracks.find(t=>t.id===ids[1]).generatorSettings;
      const cell = index => lane.querySelector('[data-step="'+index+'"]');
      const initialBounds = cell(0).getBoundingClientRect();
      check(checkbox.closest('label').textContent.trim()==='V'
        && checkbox.getBoundingClientRect().left > editedRow.querySelector('input[type="number"]').getBoundingClientRect().right, 'inline V checkbox after steps');
      checkbox.click();
      await wait(()=>lane.classList.contains('velocity-editing') && storedSettings().useCustomVelocities, 'velocity toggle');
      const expandedBounds = cell(0).getBoundingClientRect();
      check(expandedBounds.height === initialBounds.height * 2 && expandedBounds.width === initialBounds.width, 'same cells double in height');
      check(!editedRow.querySelector('input[type="range"]') && popup.querySelectorAll('.velocity-editing').length===1,'one integrated velocity grid');
      checkPositions('.step', [0,8,16,24]);

      // Synthetic pointer events do not own browser pointer capture.
      lane.setPointerCapture = () => {};
      const pointer = (type, index, value, options = {}) => {
        const target = cell(index);
        const bounds = target.getBoundingClientRect();
        const button = options.button ?? 0;
        (type === 'pointerdown' ? target : lane).dispatchEvent(new PointerEvent(type, {
          pointerId: 1, pointerType: 'mouse', button: type === 'pointermove' ? -1 : button,
          buttons: type === 'pointerup' || options.ctrlKey ? 0 : button === 1 ? 4 : 1,
          shiftKey: options.shiftKey, ctrlKey: options.ctrlKey,
          clientX: bounds.left + bounds.width / 2, clientY: options.clientY ?? bounds.bottom - bounds.height * value / 127,
          bubbles: true, cancelable: true,
        }));
      };
      const active = index => cell(index).classList.contains('active');
      const paintHits = async (from, to, on) => {
        const options = { shiftKey: true };
        pointer('pointerdown', from, 64, options);
        pointer('pointermove', to, 64, options);
        pointer('pointerup', to, 64, options);
        await wait(()=>Array.from({length: to - from + 1}, (_, i) => active(from + i) === on).every(Boolean), 'Shift paints hits');
      };
      if (active(0)) await paintHits(0, 0, false);
      await paintHits(0, 3, true);
      await paintHits(1, 2, false);
      await wait(()=>storedSettings().customPattern.slice(0, 4).join() === '1,0,0,1', 'hit strokes persisted');
      const patternBeforeVelocity = storedSettings().customPattern.join();
      pointer('pointerdown', 1, 100);
      pointer('pointerup', 1, 100);
      check(!active(1) && !cell(1).hasAttribute('aria-valuenow'), 'plain click leaves a rest disabled');
      pointer('pointermove', 0, 56, { ctrlKey: true });
      await wait(()=>cell(0).getAttribute('aria-valuenow')==='56','Ctrl hover previews velocity');
      document.dispatchEvent(new KeyboardEvent('keyup', { key: 'Control', bubbles: true }));
      await wait(()=>storedSettings().customVelocities?.[0]===56,'releasing Ctrl commits the stroke');
      const dragBounds = cell(0).getBoundingClientRect();
      const startY = dragBounds.bottom - dragBounds.height * 100 / 127;
      pointer('pointerdown', 0, 100);
      await new Promise(resolve => requestAnimationFrame(resolve));
      check(cell(0).getAttribute('aria-valuenow')==='56', 'left press starts at the stored velocity');
      pointer('pointermove', 3, 0, { clientY: startY });
      await new Promise(resolve => requestAnimationFrame(resolve));
      check(cell(0).getAttribute('aria-valuenow')==='56' && cell(3).getAttribute('aria-valuenow')==='79', 'horizontal left drag leaves both values intact');
      pointer('pointermove', 3, 0, { clientY: startY + 100 });
      await wait(()=>cell(0).getAttribute('aria-valuenow')==='0', 'left drag clamps below the cell');
      pointer('pointermove', 3, 0, { clientY: startY - 60 });
      await wait(()=>cell(0).getAttribute('aria-valuenow')==='107', 'left drag continues above the cell');
      check(cell(3).getAttribute('aria-valuenow')==='79' && storedSettings().customVelocities?.[0]===56, 'left drag previews the starting cell only');
      pointer('pointerup', 3, 0, { clientY: startY - 60 });
      await wait(()=>storedSettings().customVelocities?.[0]===107,'left drag commits');
      pointer('pointerdown', 0, 22, { button: 1 });
      pointer('pointermove', 3, 110, { button: 1 });
      await wait(()=>cell(0).getAttribute('aria-valuenow')==='22' && cell(3).getAttribute('aria-valuenow')==='110','velocity stroke preview');
      check(getComputedStyle(cell(0)).backgroundImage.startsWith('linear-gradient') &&
        Math.abs(parseFloat(cell(0).style.getPropertyValue('--velocity-fill')) - 22 * 100 / 127) < .01, 'velocity fill matches MIDI value');
      check(storedSettings().customVelocities?.[0]!==22,'no commit during drag');
      pointer('pointerup', 3, 110, { button: 1 });
      await wait(()=>storedSettings().customVelocities?.[0]===22,'velocity commit');
      check(storedSettings().customVelocities.slice(0, 4).join() === '22,79,79,110', 'stroke leaves rest velocities intact');
      check(storedSettings().customPattern.join() === patternBeforeVelocity, 'velocity stroke leaves rhythm intact');
      const addedBounds = cell(1).getBoundingClientRect();
      const addedY = addedBounds.bottom - addedBounds.height * 40 / 127;
      pointer('pointerdown', 1, 40, { shiftKey: true });
      await wait(()=>active(1) && cell(1).getAttribute('aria-valuenow')==='40', 'Shift-add previews a hit at the click height');
      pointer('pointermove', 1, 0, { shiftKey: true, clientY: addedY - 12 });
      await wait(()=>cell(1).getAttribute('aria-valuenow')==='50', 'Shift drag fine-adjusts the new hit');
      check(storedSettings().customPattern[1]===0 && storedSettings().customVelocities[1]===79, 'new hit and velocity stay in preview');
      pointer('pointerup', 1, 0, { shiftKey: true, clientY: addedY - 12 });
      await wait(()=>storedSettings().customPattern[1]===1 && storedSettings().customVelocities[1]===50, 'new hit and velocity commit together');
      row(ids[1]).querySelector('input[type="number"]').value='4';
      row(ids[1]).querySelector('input[type="number"]').dispatchEvent(new Event('change',{bubbles:true}));
      await wait(()=>row(ids[1]).querySelectorAll('.step').length===4,'shrink');
      check(read().tracks.find(t=>t.id===ids[1]).generatorSettings.customVelocities.length===32,'hidden velocities retained');
      popup.querySelector('button[aria-label="Close pattern sequencer"]').click();
      await wait(()=>!document.querySelector('vc-pattern-sequencer'),'close');
      document.querySelector('.project-tools button[aria-label="Undo"]').click();
      await wait(()=>read().tracks.find(t=>t.id===ids[1]).generatorSettings.hitsPerPattern===8,'undo hits');
      document.querySelector('.project-tools button[aria-label="Undo"]').click();
      await wait(()=>storedSettings().customPattern[1]===0 && storedSettings().customVelocities[1]===79,'one undo restores both the rest and its velocity');
      document.querySelector('.project-tools button[aria-label="Undo"]').click();
      await wait(()=>storedSettings().customVelocities?.[0]===107,'one undo velocity');
      check(storedSettings().customVelocities?.[3] !== 110 && storedSettings().customPattern.join() === patternBeforeVelocity, 'one undo restores full stroke only');
      document.querySelector('.track-group.melody .track-select').click();
      const melodyControl=await wait(()=>document.querySelector('vc-part-settings-editor[data-control-role="melody"] [data-control-key="rhythm"]'),'melody rhythm selector');
      check(melodyControl.querySelector(':is(vc-select, vc-menu-select)').getAttribute("value")==='melody1','normal melody rhythm default');
      check(!melodyControl.querySelector('.flip-button, .mini-preview'),'normal melody hides flip and preview');
      const melodySettings=()=>read().tracks.find(t=>t.role==='melody').generatorSettings;
      const rhythm=melodyControl.querySelector(':is(vc-select, vc-menu-select)');
      await changeControl(rhythm, 'durations');
      await wait(()=>melodySettings().rhythm==='durations','melody rhythm saved before drafting');
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
      const drawingLane = durations.querySelector('[aria-label="Lane 1 duration drawing area"]');
      const laneInput = durations.querySelector('[aria-label="Lane 1 durations"]');
      // Synthetic pointer events have no active pointer for the browser to capture.
      drawingLane.setPointerCapture = () => {};
      const bounds = drawingLane.getBoundingClientRect();
      const drag = (button, buttons, from, to) => {
        const pointer = (type, cell, changedButton, heldButtons) => drawingLane.dispatchEvent(new PointerEvent(type, {
          pointerId: 1, pointerType: 'mouse', button: changedButton, buttons: heldButtons,
          clientX: bounds.left + (cell + 0.5) * bounds.width / 16, bubbles: true, cancelable: true,
        }));
        pointer('pointerdown', from, button, buttons);
        pointer('pointermove', to, -1, buttons);
        pointer('pointerup', to, button, 0);
      };
      edit('Lane 1 durations','4');
      drag(2, 2, 7, 4);
      await wait(()=>laneInput.value==='1, -1, 2','right drag draws a pause');
      drag(0, 1, 8, 11);
      await wait(()=>laneInput.value==='1, -1, 1, 1','left drag draws a separate note');
      drag(2, 2, 8, 11);
      const drawnNotes = () => [...drawingLane.querySelectorAll('.duration-event')];
      await wait(()=>laneInput.value==='1, -2, 1' && drawnNotes().map(note => note.style.left).join(',')==='0%,75%','replacing a note with a pause joins the silence and leaves the grid clear');
      const flipDurations = durations.querySelector('[aria-label="Flip custom durations"]');
      flipDurations.click();
      await wait(()=>drawnNotes().length===1 && drawnNotes()[0].style.left==='25%' && drawnNotes()[0].style.width==='50%','joined pauses produce one note when flipped');
      flipDurations.click();
      edit('Lane 1 durations','1, -0.5, -0.5, 2');
      laneInput.dispatchEvent(new FocusEvent('blur'));
      await wait(()=>laneInput.value==='1, -1, 2','text entry joins pauses when leaving the field');
      const contextMenu = new MouseEvent('contextmenu', { button: 2, bubbles: true, cancelable: true });
      check(!drawingLane.dispatchEvent(contextMenu),'the duration lane suppresses the browser context menu');
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
      await wait(()=>!!melodySettings().customDurations,'durations persisted');
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
      await changeControl(rhythm, 'half');
      await wait(()=>melodySettings().rhythm==='half','Melody grid chosen');
      await wait(()=>melodyControl.querySelector('[data-control-key="hitsPerPattern"]'),'Melody subdivision control');
      const melodyShift=await wait(()=>melodyControl.querySelector('vc-select[aria-label="Shift"]'),'Melody shift control');
      await changeControl(melodyShift, '1');
      await wait(()=>melodySettings().patternShift===1,'Melody shift saved');
      const join=await wait(()=>document.querySelector('vc-part-settings-editor[data-control-role="melody"] vc-select[aria-label="Pattern Join"]'),'Melody Pattern Join');
      await changeControl(join, 'JOIN');
      await wait(()=>melodySettings().patternJoinMode==='JOIN','Melody Join saved');
      await changeControl(rhythm, 'full');
      await wait(()=>rhythm.getAttribute('value')==='half' && document.body.textContent.includes('at least two attacks'),'invalid joined pattern rejected and selection restored');
      check(melodySettings().rhythm==='half','invalid choice left project intact');
      const melodyPreview=await wait(()=>melodyControl.querySelector('.rhythm-preview-grid'),'Melody grid preview');
      melodyPreview.click();
      const melodyGrid=await wait(()=>document.querySelector('vc-pattern-sequencer'),'Melody pattern popup');
      const melodySteps=await wait(()=>melodyGrid.querySelector('input[type="number"]'),'Melody grid subdivision input');
      await changeControl(melodySteps, '2');
      await wait(()=>melodyGrid.querySelector('[role="alert"]')?.textContent.includes('at least two attacks'),'invalid subdivision count explained in popup');
      check(melodySteps.value==='8' && melodyGrid.querySelectorAll('.step').length===8,'rejected subdivision count restored');
      melodyGrid.querySelector('.step.active').click();
      await wait(()=>melodySettings().rhythm==='custom','grid editing saves custom Melody hits');
      melodyGrid.querySelector('button[aria-label="Close pattern sequencer"]').click();
      await wait(()=>!document.querySelector('vc-pattern-sequencer'),'Melody grid closed');
      for (const role of ['bass','arpeggio']) {
        document.querySelector('.track-group.'+role+' .track-select').click();
        const control=await wait(()=>document.querySelector('vc-part-settings-editor[data-control-role="'+role+'"] [data-control-key="rhythm"]'),role+' rhythm');
        const select=control.querySelector(':is(vc-select, vc-menu-select)');
        check((await selectOptions(select)).some(option=>option.value==='custom'),'custom grid retained');
        await changeControl(select, 'durations');
        const preview=await wait(()=>control.querySelector('.duration-preview'),role+' compact durations');
        check(!control.querySelector('.rhythm-preview-grid'),role+' replaces grid');
        const settings=()=>read().tracks.find(track=>track.role===role).generatorSettings;
        await wait(()=>settings().rhythm==='durations',role+' rhythm persisted before drafting');
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
        await wait(()=>!!settings().customDurations,role+' durations persisted');
        check(JSON.stringify(settings().customDurations[0])==='[1,-1,0.5,-1.5]',role+' persisted');
        document.querySelector('.project-tools button[aria-label="Undo"]').click();
        await wait(()=>!settings().customDurations,role+' single undo');
      }
      document.querySelector('.track-group.arpeggio .track-select').click();
      const arpControl = await wait(()=>document.querySelector('vc-part-settings-editor[data-control-role="arpeggio"] [data-control-key="pattern"]'),'arp direction');
      await changeControl(arpControl.querySelector(':is(vc-select, vc-menu-select)'), 'random');
      const arpLauncher = await wait(()=>arpControl.querySelector('[aria-label="Edit arpeggio pattern"]'),'arp launcher');
      const arpSettings = ()=>read().tracks.find(track=>track.role==='arpeggio').generatorSettings;
      await wait(()=>arpSettings().pattern==='random','arpeggio direction persisted before drafting');
      const arpBefore = sessionStorage.getItem('vibecomposer.project.v12');
      arpLauncher.click();
      let arpPopup = await wait(()=>document.querySelector('vc-arpeggio-pattern-dialog'),'arp popup');
      const arpSteps = ()=>arpPopup.querySelectorAll('.steps input');
      await wait(()=>arpSteps().length>0,'random pattern visible');
      check(!arpPopup.querySelector('textarea'),'no duplicated textarea');
      check(arpLauncher.getBoundingClientRect().height<=18 && arpControl.querySelector(':is(vc-select, vc-menu-select)').getBoundingClientRect().height<=22,'compact arp control');
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
      await wait(()=>arpSettings().pattern==='custom','arpeggio pattern persisted');
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
