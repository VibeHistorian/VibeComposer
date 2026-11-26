const { clearWorkspace } = require('./support/workspace-browser.cjs');
const { findBrowser, connectToPage, createBrowserServer, listen, launchBrowser, closeBrowser, requireProductionBuild } = require('./support/browser-harness.cjs');
const assert=require('node:assert/strict');
const fs=require('node:fs'); const path=require('node:path'); const test=require('node:test');
const browser=findBrowser();
test('phrase MIDI drop, clipboard, transpose and separate draft and project history work in the production browser', {skip:!browser,timeout:60000},async()=>{
 const root=requireProductionBuild();
 const server=createBrowserServer({ root });
 await listen(server);
 const profile=fs.mkdtempSync(path.resolve(__dirname,'../.angular/phrase-import-'));
 const child=launchBrowser(browser,['--headless','--disable-background-timer-throttling','--disable-renderer-backgrounding','--disable-gpu','--no-first-run','--disable-background-networking','--remote-debugging-port=0','--window-size=1600,1100','--user-data-dir='+profile,'http://127.0.0.1:'+server.address().port]);
 let socket;
 try {
  const connection = await connectToPage(child);
  socket = connection.socket;
  const command = connection.command;
  const evaluate=async expression=>{let result;for(let retry=0;retry<3;retry++){try{result=await command('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});break;}catch(error){if(!error.message.includes('Execution context was destroyed'))throw error;await new Promise(r=>setTimeout(r,500));}}assert.equal(result.exceptionDetails,undefined,JSON.stringify(result));return result.result.value;};
  const waitCode=`const wait=async f=>{for(let i=0;i<200;i++){const v=f();if(v)return v;await new Promise(r=>setTimeout(r,25));}throw Error('Timed out waiting for score');};`;
  await command('Page.enable');
  const reload = async () => {
    const loaded = new Promise(resolve => {
      const listener = event => { if (JSON.parse(event.data).method !== 'Page.loadEventFired') return;
        socket.removeEventListener('message', listener); resolve(); };
      socket.addEventListener('message', listener);
    });
    await command('Page.reload'); await loaded;
  };
  await reload();

  const { Midi } = require('@tonejs/midi');
  const midi = new Midi();
  midi.addTrack().addNote({ midi: 61, ticks: 0, durationTicks: 480, velocity: 100/127 })
    .addNote({ midi: 64, ticks: 15 * 480, durationTicks: 3 * 480 })
    .addNote({ midi: 65, ticks: 20 * 480, durationTicks: 480 });
  midi.addTrack().addNote({ midi: 90, ticks: 0, durationTicks: 480 });
  const bytes = JSON.stringify([...midi.toArray()]);
  const shortMidi = new Midi(); shortMidi.addTrack().addNote({ midi: 61, ticks: 0, durationTicks: 480 });
  const shortBytes = JSON.stringify([...shortMidi.toArray()]);
  const inKeyMidi = new Midi(); inKeyMidi.addTrack().addNote({ midi: 67, ticks: 0, durationTicks: 480 });
  const inKeyBytes = JSON.stringify([...inKeyMidi.toArray()]);
  await evaluate(`(async()=>{${waitCode}
    const seed=await wait(()=>document.querySelector('[aria-label="Generation seed"]'));
    await changeControl(seed, '43');
    window.dispatchEvent(new Event('pagehide')); // Flush before replacing the saved fixture.
    const project=await wait(()=>JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')));
    project.transposeSemitones=7;
    const drum=project.tracks.find(track=>track.role==='drums');
    project.tracks.push({...drum,id:'track-drums-extra',name:'D2',generatorSettings:{...drum.generatorSettings,pitch:38}});
    project.progression=[1,5,6,4];
    project.arrangement=[{...project.arrangement[0],measures:4,trackPresence:Object.fromEntries(project.tracks.map(t=>[t.id,true]))}];
    for(const track of project.tracks)track.editedPhrase=[{id:track.id+'-old',midi:track.role==='drums'?36:48,startBeat:2,durationBeats:1,velocity:90}];
    await (${clearWorkspace})();sessionStorage.setItem('vibecomposer.project.v12',JSON.stringify(project));
  })()`);
  await reload();
  const setup=`
    const read=()=>JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));
    const button=text=>[...document.querySelectorAll('button')].find(b=>b.textContent.trim()===text);
    const change = changeControl;
    const settings=async()=>{(await wait(()=>document.querySelector('[aria-label="Settings"]'))).click();await wait(()=>document.querySelector('vc-settings-dialog'));(await wait(()=>button('MIDI'))).click();return await wait(()=>document.querySelector('[aria-label="Discard phrase notes on MIDI drop"]'));};
    const closeSettings=()=>document.querySelector('[aria-label="Close settings"]').click();
    const open=async role=>{const row=await wait(()=>document.querySelector('.track-group.'+role+' .track-line'));row.querySelector('.track-select').click();(await wait(()=>[...document.querySelectorAll('#part-inspector button')].find(b=>b.textContent.trim().startsWith('Edit notes')))).click();await wait(()=>document.querySelector('#phrase-grid'));};
    const startDrop=()=>{const panel=document.querySelector('.score-panel'),data=new DataTransfer();data.items.add(new File([new Uint8Array(${bytes})],'phrase.mid',{type:'audio/midi'}));panel.dispatchEvent(new DragEvent('drop',{bubbles:true,cancelable:true,dataTransfer:data}));};
    const drop=async()=>{startDrop();const popup=await wait(()=>document.querySelector('vc-midi-import-dialog button[type="submit"]'));popup.click();await wait(()=>!document.querySelector('vc-midi-import-dialog'));await wait(()=>document.querySelector('#note-pitch')?.value==='61');};
  `;
  const result=await evaluate(`(async()=>{${waitCode}${setup}
    const preference=await settings(), defaultOff=!preference.checked;closeSettings();
    await open('bass');
    const knob=()=>document.querySelector('[aria-label="Transpose phrase"]');
    const initialZero=knob().value==='0'&&knob().min==='-36'&&knob().max==='36';
    const noAddNote=!button('＋ Note');
    const data=new DataTransfer();data.items.add(new File([new Uint8Array(${bytes})],'phrase.mid'));
    const panel=document.querySelector('.score-panel');panel.dispatchEvent(new DragEvent('dragenter',{bubbles:true,cancelable:true,dataTransfer:data}));
    await wait(()=>panel.classList.contains('midi-drag-over'));
    const glow=getComputedStyle(panel).boxShadow!=='none';
    panel.dispatchEvent(new DragEvent('dragleave',{bubbles:true}));await wait(()=>!panel.classList.contains('midi-drag-over'));
    await drop();
    const noGlow=!panel.classList.contains('midi-drag-over');
    const freePitch=document.querySelector('#note-pitch').value==='61';
    await change(knob(),'5');await wait(()=>document.querySelector('#note-pitch').value==='66');
    const savedBeforeExport=sessionStorage.getItem('vibecomposer.project.v12');
    const exportButton=button('Export Phrase');
    const originalClick=HTMLAnchorElement.prototype.click;
    let download;
    HTMLAnchorElement.prototype.click=function(){download={name:this.download,blob:fetch(this.href).then(r=>r.arrayBuffer())};};
    try { exportButton.click(); } finally { HTMLAnchorElement.prototype.click=originalClick; }
    window.phraseExport={
      name:download.name,bytes:[...new Uint8Array(await download.blob)],
      unchanged:savedBeforeExport===sessionStorage.getItem('vibecomposer.project.v12'),
      stillEditing:!!document.querySelector('#phrase-grid'),draggable:exportButton.draggable,
      firstAction:document.querySelector('.score-display-header').firstElementChild===exportButton,
      hint:exportButton.title};
    button('Undo phrase').click();await wait(()=>knob().value==='0'&&document.querySelector('#note-pitch').value==='61');
    button('Redo phrase').click();await wait(()=>knob().value==='5'&&document.querySelector('#note-pitch').value==='66');
    button('Undo phrase').click();await wait(()=>knob().value==='0');
    {const beforeApply=sessionStorage.getItem('vibecomposer.project.v12');button('Apply').click();await wait(()=>!document.querySelector('#phrase-grid'));await wait(()=>sessionStorage.getItem('vibecomposer.project.v12')!==beforeApply);}
    const notes=read().tracks.find(t=>t.role==='bass').phraseEdits.at(-1).notes;
    const compensation=notes.some(n=>n.midi===54&&n.startBeat===0);
    const preserved=notes.some(n=>n.id.endsWith('-old'));
    const preservedTail=notes.some(n=>n.startBeat===15&&n.durationBeats===3)&&notes.length===3;
    await open('bass');const reopenedZero=knob().value==='0';button('Cancel').click();await wait(()=>!document.querySelector('#phrase-grid'));
    const checkbox=await settings();checkbox.click();closeSettings();
    await open('bass');await drop();{const beforeApply=sessionStorage.getItem('vibecomposer.project.v12');button('Apply').click();await wait(()=>!document.querySelector('#phrase-grid'));await wait(()=>sessionStorage.getItem('vibecomposer.project.v12')!==beforeApply);}
    const replaced=read().tracks.find(t=>t.role==='bass').phraseEdits.at(-1).notes.length===2;
    await open('drums');await drop();{const beforeApply=sessionStorage.getItem('vibecomposer.project.v12');button('Apply').click();await wait(()=>!document.querySelector('#phrase-grid'));await wait(()=>sessionStorage.getItem('vibecomposer.project.v12')!==beforeApply);}
    const drumsUntransposed=read().tracks.find(t=>t.role==='drums').phraseEdits.at(-1).notes.some(n=>n.midi===61);
    const oldDrumsCleared=read().tracks.find(t=>t.id==='track-drums-extra').phraseEdits.at(-1).notes.length===0;
    return {defaultOff,initialZero,noAddNote,glow,noGlow,freePitch,compensation,preserved,preservedTail,reopenedZero,replaced,drumsUntransposed,oldDrumsCleared};
  })()`);
  for(const [key,value] of Object.entries(result))assert.equal(value,true,key);
  const exported=await evaluate('window.phraseExport');
  assert.equal(exported.unchanged,true,'export leaves project and unapplied draft open');
  assert.equal(exported.stillEditing,true);
  assert.equal(exported.draggable,false);
  assert.equal(exported.firstAction,true);
  assert.equal(exported.hint,'Download the current phrase as a MIDI file, including unapplied edits.');
  assert.ok(exported.name.endsWith('_phrase.mid'));
  assert.ok(!/[<>:"/\\|?*]/.test(exported.name));
  const phrase=new Midi(Buffer.from(exported.bytes));
  assert.equal(phrase.tracks.length,1,'only edited instrument exported');
  assert.ok(phrase.tracks[0].notes.some(n=>n.midi===66&&n.ticks===0),'unapplied pitch edits exported in sounding pitch');
  assert.ok(phrase.tracks[0].notes.some(n=>n.ticks/phrase.header.ppq===15&&n.durationTicks/phrase.header.ppq===3),'phrase-relative timing and preserved spillover duration');
  await reload();
  assert.equal(await evaluate(`(async()=>{${waitCode}${setup}return (await settings()).checked;})()`),true,'discard preference survives reload');
  const options=await evaluate(`(async()=>{${waitCode}${setup}
    closeSettings();await open('bass');
    const before=read().tracks.find(t=>t.role==='bass').phraseEdits.at(-1).notes;
    startDrop();const popup=await wait(()=>document.querySelector('vc-midi-import-dialog #midi-import-duration'));
    const dialog=popup.closest('vc-midi-import-dialog'),apply=dialog.querySelector('button[type="submit"]');
    const combined=!!dialog.querySelector('#midi-import-scale')&&dialog.textContent.includes('21 beats')&&dialog.textContent.includes('20 beats');
    const emptyDefault=popup.value===''&&dialog.querySelector('#midi-import-scale').getAttribute("value")==='';
    popup.value='20';popup.dispatchEvent(new Event('input',{bubbles:true}));await wait(()=>apply.disabled);
    const rejectsEqual=dialog.querySelector('[role="alert"]').textContent.includes('less than 20');
    popup.value='0';popup.dispatchEvent(new Event('input',{bubbles:true}));await new Promise(r=>setTimeout(r,30));const rejectsZero=apply.disabled;
    popup.value='12';popup.dispatchEvent(new Event('input',{bubbles:true}));await wait(()=>!apply.disabled);
    const select=dialog.querySelector('#midi-import-scale');const choice=(await selectOptions(select))[1];await change(select,choice.value);
    apply.click();await wait(()=>!document.querySelector('vc-midi-import-dialog'));
    await wait(()=>document.querySelector('.score-display-header button.active'));
    button('Undo phrase').click();await wait(()=>!button('Redo phrase').disabled);
    const singleUndo=button('Undo phrase').disabled;
    button('Redo phrase').click();await wait(()=>!button('Undo phrase').disabled && !button('Apply').disabled);
    {const beforeApply=sessionStorage.getItem('vibecomposer.project.v12');button('Apply').click();await wait(()=>!document.querySelector('#phrase-grid'));await wait(()=>sessionStorage.getItem('vibecomposer.project.v12')!==beforeApply);}
    const notes=read().tracks.find(t=>t.role==='bass').phraseEdits.at(-1).notes;
    const scaled=notes.length===3&&Math.abs(Math.max(...notes.map(n=>n.startBeat+n.durationBeats))-12)<1e-8;
    const translated=notes.every(n=>[0,2,4,5,7,9,11].includes(n.midi%12));
    await open('bass');startDrop();await wait(()=>document.querySelector('vc-midi-import-dialog button[type="submit"]'));
    document.querySelector('[aria-label="Cancel MIDI import"]').click();await wait(()=>!document.querySelector('vc-midi-import-dialog'));
    const cancelClean=button('Undo phrase').disabled;
    button('Cancel').click();await wait(()=>!document.querySelector('#phrase-grid'));
    await open('bass');const snap=document.querySelector('vc-select[aria-label="Harmony snapping"]');await change(snap,'off');
    await wait(()=>snap.getAttribute('value')==='off');
    startDrop();await wait(()=>document.querySelector('vc-midi-import-dialog button[type="submit"]'));
    const snapOffNoTranslation=!document.querySelector('#midi-import-scale')&&!!document.querySelector('#midi-import-duration');
    document.querySelector('[aria-label="Cancel MIDI import"]').click();
    await wait(()=>!document.querySelector('vc-midi-import-dialog'));await change(snap,'global');
    await wait(()=>snap.getAttribute('value')==='global');
    const dropBytes=bytes=>{const data=new DataTransfer();data.items.add(new File([new Uint8Array(bytes)],'short.mid'));document.querySelector('.score-panel').dispatchEvent(new DragEvent('drop',{bubbles:true,cancelable:true,dataTransfer:data}));};
    dropBytes(${shortBytes});await wait(()=>document.querySelector('#midi-import-scale'));
    const translationOnly=!document.querySelector('#midi-import-duration');
    document.querySelector('[aria-label="Cancel MIDI import"]').click();await wait(()=>!document.querySelector('vc-midi-import-dialog'));
    dropBytes(${inKeyBytes});await wait(()=>document.querySelector('#note-pitch')?.value==='67');
    const noPopupForFittingMidi=!document.querySelector('vc-midi-import-dialog');
    return {combined,emptyDefault,rejectsEqual,rejectsZero,singleUndo,scaled,translated,cancelClean,snapOffNoTranslation,translationOnly,noPopupForFittingMidi};
  })()`);
  for(const [key,value] of Object.entries(options))assert.equal(value,true,key);

  await evaluate(`(async () => {
    window.dispatchEvent(new Event('pagehide'));
    const project = JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));
    project.scale = 'major';
    project.transposeSemitones = 0;
    const { id, type, measures, trackPresence } = project.arrangement[0];
    project.arrangement = [
      { id, type, measures, trackPresence },
      { id: 'clipboard-destination', type: 'OUTRO', measures, trackPresence, context: { scale: 'natural-minor', keyChange: 2 } },
    ];
    const bass = project.tracks.find(track => track.role === 'bass');
    delete bass.phraseEdits;
    bass.editedPhrase = [
      { id: 'clipboard-first', midi: 64, startBeat: 1, durationBeats: 0.5, velocity: 79 },
      { id: 'clipboard-second', midi: 67, startBeat: 3, durationBeats: 0.75, velocity: 108 },
    ];
    await (${clearWorkspace})();
    sessionStorage.setItem('vibecomposer.project.v12', JSON.stringify(project));
  })()`);
  await reload();
  const clipboard = await evaluate(`(async()=>{ ${waitCode} ${setup}
    const key = (target, key, modifiers = {}) => target.dispatchEvent(new KeyboardEvent('keydown',
      { key, bubbles: true, cancelable: true, ...modifiers }));
    const selected = count => [...document.querySelectorAll('.inspector-copy')].some(p => p.textContent.trim() === count + ' notes selected');
    const field = id => document.querySelector('#note-' + id)?.value;
    const position = document.querySelector('[aria-label="Song position"]');
    const seekForward = count => { for (let i = 0; i < count; i++) key(position, 'ArrowRight'); };
    await open('bass');
    const initiallyEmpty = button('Paste notes').disabled;
    key(document.body, 'a', { ctrlKey: true }); await wait(() => selected(2));
    const inputCopyIsNative = key(document.querySelector('#note-pitch'), 'c', { ctrlKey: true });
    key(document.body, 'c', { ctrlKey: true });
    await wait(() => !button('Paste notes').disabled);
    const copyLeavesUndoEmpty = button('Undo phrase').disabled;
    button('Remove selected notes').click();
    await wait(() => !field('pitch'));
    seekForward(2);
    const inputPasteIsNative = key(document.querySelector('[aria-label="Generation seed"]'), 'v', { ctrlKey: true });
    key(document.body, 'v', { ctrlKey: true });
    await wait(() => selected(2) && field('start') === '3');
    const detachedSnapshot = field('pitch') === '64' && field('length') === '0.5' && field('velocity') === '79';
    seekForward(2);
    button('Paste notes').click();
    await wait(() => selected(2) && field('start') === '5');
    button('Undo phrase').click(); await wait(() => selected(2) && field('start') === '3');
    button('Redo phrase').click(); await wait(() => selected(2) && field('start') === '5');
    const originalClick = HTMLAnchorElement.prototype.click;
    let exportBytes;
    HTMLAnchorElement.prototype.click = function() { exportBytes = fetch(this.href).then(r => r.arrayBuffer()); };
    try { button('Export Phrase').click(); } finally { HTMLAnchorElement.prototype.click = originalClick; }
    const bytes = [...new Uint8Array(await exportBytes)];
    button('Cancel').click(); await wait(() => !document.querySelector('#phrase-grid'));
    (await wait(() => document.querySelector('[aria-label="Select OUTRO section"]'))).click();
    await open('bass');
    button('Paste notes').click();
    const outsidePhrase = await wait(() => [...document.querySelectorAll('[role="alert"]')].some(p => p.textContent.includes('Move the playhead')));
    seekForward(14);
    button('Paste notes').click();
    const pasteDialog = () => document.querySelector('vc-midi-import-dialog');
    const dialog = await wait(pasteDialog);
    const clipboardHarmony = dialog.querySelector('h2').textContent === 'Paste notes'
      && (await selectOptions(dialog.querySelector('#midi-import-scale')))[1].textContent.trim().startsWith('C Major → D Natural minor');
    dialog.querySelector('[aria-label="Cancel paste"]').click();
    await wait(() => !pasteDialog());
    const cancelLeavesUndoEmpty = button('Undo phrase').disabled;
    button('Paste notes').click();
    (await wait(pasteDialog)).querySelector('button[type="submit"]').click();
    await wait(() => selected(2) && field('start') === '3');
    const keepCopiedPitch = field('pitch') === '64';
    button('Paste notes').click();
    const translation = (await wait(pasteDialog)).querySelector('#midi-import-scale');
    await change(translation, (await selectOptions(translation))[1].value);
    pasteDialog().querySelector('button[type="submit"]').click();
    await wait(() => selected(2) && field('pitch') === '65');
    button('Copy notes').click();
    button('Remove selected notes').click();
    await wait(() => !field('pitch'));
    button('Paste notes').click();
    await wait(() => selected(2) && field('pitch') === '65');
    const newCopyReplacesClipboard = !pasteDialog() && field('start') === '3';
    return { bytes, initiallyEmpty, inputCopyIsNative, inputPasteIsNative, copyLeavesUndoEmpty,
      detachedSnapshot, outsidePhrase, clipboardHarmony, cancelLeavesUndoEmpty, keepCopiedPitch, newCopyReplacesClipboard };
  })()`);
  const { bytes: clipboardBytes, ...clipboardChecks } = clipboard;
  for (const [key, value] of Object.entries(clipboardChecks)) assert.equal(value, true, key);
  const pasted = new Midi(Buffer.from(clipboardBytes));
  assert.deepEqual(pasted.tracks[0].notes.map(note => [
    note.midi, note.ticks / pasted.header.ppq, note.durationTicks / pasted.header.ppq, Math.round(note.velocity * 127),
  ]).sort((a, b) => a[1] - b[1] || a[0] - b[0]), [[64, 2, 0.5, 79], [64, 4, 0.5, 79], [67, 4, 0.75, 108], [67, 6, 0.75, 108]]);

  await evaluate(`(async () => { ${waitCode} ${setup}
    const control = label => document.querySelector('[aria-label="' + label + '"]');
    const undo = control('Undo'), redo = control('Redo');
    const knob = (row, label) => row.querySelector('input[aria-label^="' + label + ' "]');
    const row = document.querySelector('.track-group.bass .track-row-card');
    const pan = knob(row, 'Pan'), volume = knob(row, 'Volume');
    const transpose = control('Transpose in semitones');
    const instrument = row.querySelector('.track-instrument');
    const earlierProgram = instrument.getAttribute('value');
    const program = earlierProgram === '32' ? '33' : '32';
    button('Cancel').click();
    await wait(() => !document.querySelector('#phrase-grid'));
    await change(pan, '20');
    await wait(() => !undo.disabled);
    await change(transpose, '3');
    undo.click();
    await wait(() => transpose.value === '0' && !redo.disabled);
    await open('bass');
    if (!undo.disabled || !redo.disabled || row.querySelector('.track-channel').getAttribute('aria-disabled') !== 'true') {
      throw Error('Opening a phrase must lock earlier history and the MIDI channel');
    }
    button('Cancel').click();
    await wait(() => !undo.disabled && !redo.disabled);
    await open('bass');
    const draftShortcut = key => document.body.dispatchEvent(new KeyboardEvent('keydown', { key, ctrlKey: true, bubbles: true, cancelable: true }));
    const draftTranspose = control('Transpose phrase');
    const openingVolume = volume.value;
    const quieterVolume = String(Number(openingVolume) - 10);
    await change(pan, '40');
    await change(volume, quieterVolume);
    await wait(() => pan.value === '40' && volume.value === quieterVolume);
    await change(instrument, program);
    await wait(() => instrument.getAttribute('value') === program);
    const attack = await wait(() => knob(row, 'Attack'));
    const defaultAttack = attack.getAttribute('aria-valuetext');
    await change(attack, '500');
    await wait(() => attack.getAttribute('aria-valuetext') !== defaultAttack);
    await change(transpose, '4');
    await change(draftTranspose, '2');
    draftShortcut('z');
    await wait(() => draftTranspose.value === '0' && transpose.value === '4');
    draftShortcut('y');
    await wait(() => draftTranspose.value === '2');
    undo.click();
    await wait(() => transpose.value === '0' && draftTranspose.value === '2' && !redo.disabled);
    undo.click();
    await wait(() => attack.getAttribute('aria-valuetext') === defaultAttack);
    redo.click();
    await wait(() => attack.getAttribute('aria-valuetext') !== defaultAttack);
    undo.click();
    await wait(() => attack.getAttribute('aria-valuetext') === defaultAttack);
    undo.click();
    await wait(() => instrument.getAttribute('value') === earlierProgram);
    undo.click();
    await wait(() => volume.value === openingVolume);
    undo.click();
    await wait(() => pan.value === '20' && undo.disabled);
    button('Cancel').click();
    await wait(() => !undo.disabled && !redo.disabled);
  })()`);
 } finally {
    await closeBrowser({ child, server, socket });
  }
});
