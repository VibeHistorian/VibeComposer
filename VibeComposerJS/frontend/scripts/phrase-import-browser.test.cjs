const { findBrowser, connectToPage, createBrowserServer, listen, launchBrowser, closeBrowser, requireProductionBuild } = require('./support/browser-harness.cjs');
const assert=require('node:assert/strict');
const fs=require('node:fs'); const path=require('node:path'); const test=require('node:test');
const browser=findBrowser();
test('phrase MIDI drop, transpose, preferences and draft undo work in the production browser', {skip:!browser,timeout:60000},async()=>{
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
    seed.value='43';seed.dispatchEvent(new Event('change',{bubbles:true}));
    const project=await wait(()=>JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')));
    project.transposeSemitones=7;
    const drum=project.tracks.find(track=>track.role==='drums');
    project.tracks.push({...drum,id:'track-drums-extra',name:'D2',generatorSettings:{...drum.generatorSettings,pitch:38}});
    project.progression=[1,5,6,4];
    project.arrangement=[{...project.arrangement[0],measures:4,trackPresence:Object.fromEntries(project.tracks.map(t=>[t.id,true]))}];
    for(const track of project.tracks)track.editedPhrase=[{id:track.id+'-old',midi:track.role==='drums'?36:48,startBeat:2,durationBeats:1,velocity:90}];
    sessionStorage.setItem('vibecomposer.project.v12',JSON.stringify(project));
  })()`);
  await reload();
  const setup=`
    const read=()=>JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));
    const button=text=>[...document.querySelectorAll('button')].find(b=>b.textContent.trim()===text);
    const change=(input,value)=>{input.value=value;input.dispatchEvent(new Event('change',{bubbles:true}));};
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
    change(knob(),'5');await wait(()=>document.querySelector('#note-pitch').value==='66');
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
    button('Apply').click();await wait(()=>!document.querySelector('#phrase-grid'));
    const notes=read().tracks.find(t=>t.role==='bass').editedPhrase;
    const compensation=notes.some(n=>n.midi===54&&n.startBeat===0);
    const preserved=notes.some(n=>n.id.endsWith('-old'));
    const clipped=notes.some(n=>n.startBeat===15&&n.durationBeats===1)&&notes.length===3;
    await open('bass');const reopenedZero=knob().value==='0';button('Cancel').click();await wait(()=>!document.querySelector('#phrase-grid'));
    const checkbox=await settings();checkbox.click();closeSettings();
    await open('bass');await drop();button('Apply').click();await wait(()=>!document.querySelector('#phrase-grid'));
    const replaced=read().tracks.find(t=>t.role==='bass').editedPhrase.length===2;
    await open('drums');await drop();button('Apply').click();await wait(()=>!document.querySelector('#phrase-grid'));
    const drumsUntransposed=read().tracks.find(t=>t.role==='drums').editedPhrase.some(n=>n.midi===61);
    const oldDrumsCleared=read().tracks.find(t=>t.id==='track-drums-extra').editedPhrase.length===0;
    return {defaultOff,initialZero,noAddNote,glow,noGlow,freePitch,compensation,preserved,clipped,reopenedZero,replaced,drumsUntransposed,oldDrumsCleared};
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
  assert.ok(phrase.tracks[0].notes.some(n=>n.ticks/phrase.header.ppq===15&&n.durationTicks/phrase.header.ppq===1),'phrase-relative timing and clipped duration');
  await reload();
  assert.equal(await evaluate(`(async()=>{${waitCode}${setup}return (await settings()).checked;})()`),true,'discard preference survives reload');
  const options=await evaluate(`(async()=>{${waitCode}${setup}
    closeSettings();await open('bass');
    const before=read().tracks.find(t=>t.role==='bass').editedPhrase;
    startDrop();const popup=await wait(()=>document.querySelector('vc-midi-import-dialog #midi-import-duration'));
    const dialog=popup.closest('vc-midi-import-dialog'),apply=dialog.querySelector('button[type="submit"]');
    const combined=!!dialog.querySelector('#midi-import-scale')&&dialog.textContent.includes('21 beats')&&dialog.textContent.includes('16 beats');
    const emptyDefault=popup.value===''&&dialog.querySelector('#midi-import-scale').value==='';
    popup.value='16';popup.dispatchEvent(new Event('input',{bubbles:true}));await wait(()=>apply.disabled);
    const rejectsEqual=dialog.querySelector('[role="alert"]').textContent.includes('less than 16');
    popup.value='0';popup.dispatchEvent(new Event('input',{bubbles:true}));await new Promise(r=>setTimeout(r,30));const rejectsZero=apply.disabled;
    popup.value='12';popup.dispatchEvent(new Event('input',{bubbles:true}));await wait(()=>!apply.disabled);
    const select=dialog.querySelector('#midi-import-scale');const choice=select.options[1];change(select,choice.value);
    apply.click();await wait(()=>!document.querySelector('vc-midi-import-dialog'));
    await wait(()=>document.querySelector('.score-display-header button.active'));
    button('Undo phrase').click();await wait(()=>document.querySelector('[aria-label="Transpose phrase"]').value==='0');
    const singleUndo=button('Undo phrase').disabled;
    button('Redo phrase').click();await wait(()=>!button('Undo phrase').disabled);
    button('Apply').click();await wait(()=>!document.querySelector('#phrase-grid'));
    const notes=read().tracks.find(t=>t.role==='bass').editedPhrase;
    const scaled=notes.length===3&&Math.abs(Math.max(...notes.map(n=>n.startBeat+n.durationBeats))-12)<1e-8;
    const translated=notes.every(n=>[0,2,4,5,7,9,11].includes(n.midi%12));
    await open('bass');startDrop();await wait(()=>document.querySelector('vc-midi-import-dialog button[type="submit"]'));
    document.querySelector('[aria-label="Cancel MIDI import"]').click();await wait(()=>!document.querySelector('vc-midi-import-dialog'));
    const cancelClean=button('Undo phrase').disabled;
    button('Cancel').click();await wait(()=>!document.querySelector('#phrase-grid'));
    await open('bass');const snap=document.querySelector('[aria-label="Snap to scale/key"]');if(snap.getAttribute('aria-pressed')==='true')snap.click();
    await wait(()=>snap.getAttribute('aria-pressed')==='false');
    startDrop();await wait(()=>document.querySelector('vc-midi-import-dialog button[type="submit"]'));
    const snapOffNoTranslation=!document.querySelector('#midi-import-scale')&&!!document.querySelector('#midi-import-duration');
    document.querySelector('[aria-label="Cancel MIDI import"]').click();
    await wait(()=>!document.querySelector('vc-midi-import-dialog'));snap.click();
    await wait(()=>snap.getAttribute('aria-pressed')==='true');
    const dropBytes=bytes=>{const data=new DataTransfer();data.items.add(new File([new Uint8Array(bytes)],'short.mid'));document.querySelector('.score-panel').dispatchEvent(new DragEvent('drop',{bubbles:true,cancelable:true,dataTransfer:data}));};
    dropBytes(${shortBytes});await wait(()=>document.querySelector('#midi-import-scale'));
    const translationOnly=!document.querySelector('#midi-import-duration');
    document.querySelector('[aria-label="Cancel MIDI import"]').click();await wait(()=>!document.querySelector('vc-midi-import-dialog'));
    dropBytes(${inKeyBytes});await wait(()=>document.querySelector('#note-pitch')?.value==='67');
    const noPopupForFittingMidi=!document.querySelector('vc-midi-import-dialog');
    return {combined,emptyDefault,rejectsEqual,rejectsZero,singleUndo,scaled,translated,cancelClean,snapOffNoTranslation,translationOnly,noPopupForFittingMidi};
  })()`);
  for(const [key,value] of Object.entries(options))assert.equal(value,true,key);
 } finally {
    await closeBrowser({ child, server, socket });
  }
});
