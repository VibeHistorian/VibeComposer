const { findBrowser, createBrowserServer, listen, launchBrowser, closeBrowser, watchBrowser, requireProductionBuild } = require('./support/browser-harness.cjs');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const browser = findBrowser();

test('live recording captures phrase notes, MIDI velocity, held patterns and duration lanes', {skip:!browser,timeout:60000},async()=>{
  const root = requireProductionBuild();
  const index = fs.readFileSync(path.join(root, 'index.html'), 'utf8');
  const script = `<script>

(async()=>{
 const wait=async(fn,label,attempts=300)=>{for(let i=0;i<attempts;i++){const value=fn();if(value)return value;await new Promise(r=>setTimeout(r,25));}throw Error(label);};
 const check=(value,label)=>{if(!value)throw Error(label);};
 const button=text=>[...document.querySelectorAll('button')].find(b=>b.textContent.trim()===text);
 const pause=ms=>new Promise(r=>setTimeout(r,ms));
 const press=(type,code,key,shiftKey=false)=>(document.activeElement || window).dispatchEvent(new KeyboardEvent(type,{code,key,shiftKey,bubbles:true,cancelable:true}));
 try {
  const port={id:'test-input',name:'Test MIDI keys',state:'connected'};
  Object.defineProperty(navigator,'requestMIDIAccess',{configurable:true,value:async()=>({inputs:new Map([[port.id,port]]),addEventListener(){}})});
  (await wait(()=>document.querySelector('[aria-label="Settings"]'),'settings')).click();
  (await wait(()=>button('MIDI'),'MIDI page')).click();
  (await wait(()=>button('Find MIDI inputs'),'input discovery')).click();
  await pause(60);await changeControl(document.querySelector('[aria-label="MIDI input"]'),'test-input');
  document.querySelector('[aria-label="Close settings"]').click();
  (await wait(()=>document.querySelector('.track-group.bass .track-select'),'bass track')).click();
  (await wait(()=>[...document.querySelectorAll('#part-inspector button')].find(b=>b.textContent.trim().startsWith('Edit notes')),'edit')).click();
  await wait(()=>document.querySelector('#phrase-grid'),'phrase');
  await wait(()=>document.querySelector('.keyboard-label'),'keyboard labels while idle');
  button('Record live').focus();
  press('keydown','KeyQ','q');
  await wait(()=>document.querySelector('.pitch-key.sounding:not(.drum-label)[data-midi="53"]'),'idle keyboard key highlight');
  port.onmidimessage({data:[0x90,60,100]});
  await wait(()=>document.querySelector('.pitch-key.sounding:not(.drum-label)[data-midi="60"]'),'idle MIDI key highlight');
  press('keyup','KeyQ','q');
  await wait(()=>!document.querySelector('.pitch-key.sounding:not(.drum-label)[data-midi="53"]') && document.querySelector('.pitch-key.sounding:not(.drum-label)[data-midi="60"]'),'independent key release');
  port.onmidimessage({data:[0x80,60,0]});
  await wait(()=>!document.querySelector('.pitch-key.sounding'),'all idle keys released');
  check(button('Apply').disabled,'idle audition does not change the phrase');
  port.onmidimessage({data:[0x90,108,100]});
  await wait(()=>document.querySelector('.pitch-key.sounding:not(.drum-label)[data-midi="108"]'),'idle MIDI expands pitch view');
  await wait(()=>document.querySelector('.keyboard-label.column-two'),'dense labels use two columns');
  for(const second of [false,true]){
    const boxes=[...document.querySelectorAll('.keyboard-label')].filter(label=>label.classList.contains('column-two')===second).map(label=>label.getBoundingClientRect()).sort((a,b)=>a.top-b.top);
    for(let i=1;i<boxes.length;i++)check(boxes[i-1].bottom<=boxes[i].top+.1,'dense labels do not overlap');
  }
  port.onmidimessage({data:[0x80,108,0]});
  await wait(()=>!document.querySelector('.pitch-key.sounding'),'outlier release');
  check(button('Apply').disabled,'zooming and auditioning leave the draft unchanged');
  const discardLoop=await wait(()=>button('Discard on Loop'),'discard on loop');check(discardLoop.getAttribute('aria-pressed')==='true','discard on loop defaults on');
  discardLoop.click();await wait(()=>discardLoop.getAttribute('aria-pressed')==='false','keep loop passes');
  discardLoop.click();await wait(()=>discardLoop.getAttribute('aria-pressed')==='true','discard loop passes');
  const gridLock=await wait(()=>button('Lock Grid'),'grid lock');check(gridLock.getAttribute('aria-pressed')==='false','free timing defaults on');
  gridLock.click();await wait(()=>gridLock.getAttribute('aria-pressed')==='true','grid lock enabled');gridLock.click();
  await wait(()=>gridLock.getAttribute('aria-pressed')==='false','grid lock disabled');
  const before=sessionStorage.getItem('vibecomposer.project.v12');
  button('Record live').click();await wait(()=>document.querySelector('[aria-label="Recording count-in"]'),'count-in');
  const playheadFrames=new Set();
  for(let i=0;i<36;i++){playheadFrames.add(document.querySelector('.score-canvas-host canvas').toDataURL());await pause(20);}
  check(playheadFrames.size>=2,'stationary count-in playhead visibly blinks');
  press('keydown','Digit1','!',true);press('keyup','Digit1','!',true);await pause(150);
  check(!document.querySelector('#note-pitch'),'count-in ignores early input');
  await wait(()=>document.querySelector('[aria-label="Recording beat"]'),'capture after count-in',1000);
  press('keydown','Digit1','!',true);
  await wait(()=>document.querySelector('.pitch-key:not(.drum-label)[data-midi="37"]'),'live pitch fits before release');
  check(!!document.querySelector('[data-keyboard-midi="37"]'),'keyboard mapping appears on piano keys');
  const labels=[...document.querySelectorAll('.keyboard-label')];
  check(labels.length>1 && labels.every(label=>label.parentElement.classList.contains('score-gutter')),'labels belong to the common gutter');
  const right=labels.find(label=>!label.classList.contains('column-two')).getBoundingClientRect().right;
  check(labels.filter(label=>!label.classList.contains('column-two')).every(label=>Math.abs(label.getBoundingClientRect().right-right)<.1),'keyboard labels align in their column');
  await wait(()=>document.querySelector('.pitch-key.sounding:not(.drum-label)[data-midi="37"]'),'recorded input highlights piano key');
  await pause(220);press('keyup','Digit1','!',true);
  await wait(()=>!document.querySelector('.pitch-key.sounding:not(.drum-label)[data-midi="37"]'),'recorded key clears on release');
  await wait(()=>document.querySelector('#note-pitch')?.value==='37','C sharp keyboard');
  check(document.querySelector('#note-length').value>0,'held keyboard duration');
  press('keydown','KeyT','T',true);
  await wait(()=>document.querySelector('.pitch-key.sounding:not(.drum-label)[data-midi="61"]'),'Shift+T records its note despite selected notes');
  check(!document.querySelector('#phrase-transpose'),'recording does not open transpose');
  press('keyup','KeyT','T',true);
  await wait(()=>document.querySelector('#note-pitch')?.value==='61','shifted note committed on release');
  check(!!document.querySelector('vc-live-recording output'),'visible moving beat');
  port.onmidimessage({data:[0x90,63,111]});await pause(170);port.onmidimessage({data:[0x80,63,0]});
  await wait(()=>document.querySelector('#note-pitch')?.value==='63','MIDI pitch');
  check(document.querySelector('#note-velocity').value==='111','MIDI velocity');
  const input=document.querySelector('#note-start');input.focus();
  press('keydown','KeyQ','q');press('keyup','KeyQ','q');await pause(30);
  check(document.querySelector('#note-pitch').value==='63','text field does not record');
  input.blur();press('keydown','Digit2','2');await pause(120);
  check(sessionStorage.getItem('vibecomposer.project.v12')===before,'recording remains a draft');
  button('Apply').click();await wait(()=>!document.querySelector('#phrase-grid'),'applied');
  await wait(()=>sessionStorage.getItem('vibecomposer.project.v12')!==before,'saved');
  const track=JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')).tracks.find(t=>t.role==='bass');
  const notes=track.phraseEdits.flatMap(edit=>edit.notes);
  check(notes.some(n=>n.midi===37)&&notes.some(n=>n.midi===38)&&notes.some(n=>n.midi===63&&n.velocity===111),'recorded notes saved');
  check(!document.querySelector('vc-live-recording output'),'recorder cleaned up');
  check(!document.querySelector('.keyboard-label'),'keyboard labels removed after recording');

  (await wait(()=>document.querySelector('[aria-label="Settings"]'),'settings again')).click();
  (await wait(()=>button('MIDI'),'MIDI again')).click();
  (await wait(()=>document.querySelector('[aria-label="Recording count-in"]'),'count-in setting')).click();
  document.querySelector('[aria-label="Close settings"]').click();

  await changeControl(document.querySelector('[aria-label="Mode"]'),'natural-minor');
  await changeControl(document.querySelector('[aria-label="Transpose in semitones"]'),'12');
  document.querySelector('.track-group.bass .track-select').click();
  (await wait(()=>[...document.querySelectorAll('#part-inspector button')].find(b=>b.textContent.trim().startsWith('Edit notes')),'edit minor phrase')).click();
  await wait(()=>document.querySelector('#phrase-grid'),'minor phrase');
  button('Record live').focus();button('Record live').click();
  await wait(()=>document.querySelector('[aria-label="Recording beat"]'),'minor capture');
  await wait(()=>document.querySelector('[data-keyboard-midi="51"]')?.textContent.includes('3'),'mapped scale-degree label');
  press('keydown','Digit3','3');await pause(150);press('keyup','Digit3','3');
  await wait(()=>document.querySelector('#note-pitch')?.value==='51','scale-matched pitch');
  document.querySelector('vc-live-recording button[aria-label="Match scale/key"]').click();
  button('Stop recording').focus();
  press('keydown','Digit3','3');await pause(150);press('keyup','Digit3','3');
  await wait(()=>document.querySelector('#note-pitch')?.value==='40','optional matching off');
  await wait(()=>document.querySelector('[data-keyboard-midi="40"]')?.textContent.includes('3'),'unmatched C-major label');
  button('Stop recording').click();button('Cancel').click();
  await wait(()=>!document.querySelector('#phrase-grid'),'cancelled minor phrase');
  document.querySelector('.track-group.bass .track-select').click();
  (await wait(()=>[...document.querySelectorAll('#part-inspector button')].find(b=>b.textContent.trim().startsWith('Edit notes')),'armed editor')).click();
  await wait(()=>document.querySelector('#phrase-grid'),'armed phrase');
  await changeControl(document.querySelector('[aria-label="Recording mode"]'),'armed');
  button('Arm recording').focus();press('keydown','Backquote',String.fromCharCode(96));press('keyup','Backquote',String.fromCharCode(96));
  await wait(()=>button('Waiting for note'),'armed waiting');check(button('Apply').disabled,'arming leaves draft unchanged');
  press('keydown','KeyQ','q');await pause(180);press('keyup','KeyQ','q');
  await wait(()=>!button('Apply').disabled,'triggering note recorded');
  press('keydown','Backquote',String.fromCharCode(96));press('keyup','Backquote',String.fromCharCode(96));
  await wait(()=>button('Arm recording'),'shortcut stops');
  press('keydown','Backquote','~',true);press('keyup','Backquote','~',true);
  await wait(()=>button('Apply').disabled,'shortcut cancels last batch');
  button('Cancel').click();await wait(()=>!document.querySelector('#phrase-grid'),'armed editor closed');
  document.querySelector('.track-group.drums .track-select').click();
  (await wait(()=>document.querySelector('vc-part-settings-editor[data-control-role="drums"] button[aria-label="Edit rhythm pattern"]'),'pattern launcher')).click();
  const popup=await wait(()=>document.querySelector('vc-pattern-sequencer'),'pattern popup');
  const row=await wait(()=>popup.querySelector('.track'),'pattern row');
  await changeControl(row.querySelector('input[type="number"]'),'16');
  await wait(()=>row.querySelectorAll('.step').length===16,'16 hits');
  for(const cell of row.querySelectorAll('.step.active'))cell.click();
  await wait(()=>!row.querySelector('.step.active'),'cleared hits');
  button('Record live').focus();button('Record live').click();
  await wait(()=>row.querySelector('.recording-step'),'moving pattern highlight');
  press('keydown','Backquote','\u0060');await pause(350);press('keyup','Backquote','\u0060');
  await wait(()=>row.querySelector('.step.active'),'recorded hits');
  button('Stop recording').click();
  document.querySelector('[aria-label="Close pattern sequencer"]').click();
  document.querySelector('.track-group.melody .track-select').click();
  const rhythm=await wait(()=>document.querySelector('vc-part-settings-editor[data-control-role="melody"] [data-control-key="rhythm"] vc-menu-select, vc-part-settings-editor[data-control-role="melody"] [data-control-key="rhythm"] vc-select'),'melody rhythm');
  await changeControl(rhythm,'durations');
  const durationTrigger=await wait(()=>document.querySelector('vc-durations-dialog') || document.querySelector('[aria-label="Edit custom melody durations"]'),'duration launcher');
  if(durationTrigger.tagName.toLowerCase()!=='vc-durations-dialog')durationTrigger.click();
  const durations=await wait(()=>document.querySelector('vc-durations-dialog'),'durations popup');
  const lane=await wait(()=>durations.querySelector('[aria-label="Lane 1 durations"]'),'duration lane');
  await changeControl(lane,'4');
  button('Record live').focus();button('Record live').click();
  await wait(()=>durations.querySelector('.recording-cursor'),'moving duration cursor');
  press('keydown','Backquote','\u0060');await pause(300);press('keyup','Backquote','\u0060');
  await wait(()=>lane.value && lane.value.split(',').some(n=>Number(n)>0),'held duration recorded');
  await pause(250);
  button('Stop recording').click();
  check(lane.value.split(',').some(n=>Number(n)<0),'recorded silence becomes pauses');
  button('Cancel').click();

  const drumTrack=await wait(()=>document.querySelector('.track-group.drums .track-select'),'first drum track');
  drumTrack.click();
  await changeControl(document.querySelector('.track-group.drums .track-instrument'),'42');
  drumTrack.dispatchEvent(new MouseEvent('contextmenu',{bubbles:true,cancelable:true,clientX:120,clientY:180,button:2}));
  const duplicate=await wait(()=>button('Duplicate›'),'duplicate drum track menu');
  duplicate.dispatchEvent(new MouseEvent('mouseenter'));
  duplicate.dispatchEvent(new MouseEvent('mouseover',{bubbles:true}));
  (await wait(()=>button('Global (empty)'),'duplicate empty drum track')).click();
  await wait(()=>document.querySelectorAll('.track-group.drums .track-select').length===2,'two drum tracks');
  [...document.querySelectorAll('.track-group.drums .track-select')].at(-1).click();
  (await wait(()=>button('Edit notes'),'edit duplicate drum phrase')).click();
  await wait(()=>document.querySelector('#phrase-grid'),'drum phrase');
  const armDrums=await wait(()=>button('Arm recording'),'arm drum recording');
  armDrums.focus();press('keydown','Digit1','1');
  await wait(()=>document.querySelector('.drum-label.sounding'),'keyboard auditions the first drum while idle');
  press('keyup','Digit1','1');
  await wait(()=>!document.querySelector('.drum-label.sounding'),'idle drum releases');
  armDrums.click();
  await wait(()=>button('Waiting for note'),'drum recording armed');
  port.onmidimessage({data:[0x99,42,120]});port.onmidimessage({data:[0x89,42,0]});
  await pause(50);
  check(!!button('Waiting for note'),'instrument pitch outside the assigned MIDI range leaves drums armed');
  press('keydown','Numpad1','1');await pause(100);press('keyup','Numpad1','1');
  port.onmidimessage({data:[0x99,37,117]});await pause(100);port.onmidimessage({data:[0x89,37,0]});
  await wait(()=>document.querySelector('#note-velocity')?.value==='117','second drum records the MIDI input');
  check(document.querySelector('#note-pitch').value==='42','the track instrument pitch is recorded');
  button('Apply').click();await wait(()=>!document.querySelector('#phrase-grid'),'drum phrase applied');
  await wait(()=>{
    const [first, second]=JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')).tracks.filter(track=>track.role==='drums');
    return first.phraseEdits?.some(edit=>edit.notes.some(note=>note.midi===42&&note.velocity===90))
      && second.phraseEdits?.some(edit=>edit.notes.some(note=>note.midi===42&&note.velocity===117));
  },'duplicate drum instruments saved on their respective tracks');
  await fetch('/result',{method:'POST',body:JSON.stringify({passed:true})});
 }catch(error){await fetch('/result',{method:'POST',body:JSON.stringify({error:error.stack + " | controls: " + document.querySelector('vc-live-recording')?.textContent + " | alerts: " + [...document.querySelectorAll('[role=alert]')].map(e=>e.textContent).join(' / ')})});}
})();</script>`;
  let resolve, reject;
  const result = new Promise((done, fail) => { resolve = done; reject = fail; });
  result.catch(() => {}); // CDP setup can fail before the result is awaited.
  const server = createBrowserServer({ root, html: index.replace('</body>', script + '</body>'), resolve, reject });
  await listen(server);
  const profile = fs.mkdtempSync(path.resolve(__dirname, '../.angular/live-recording-browser-'));
  const child = launchBrowser(browser, ['--headless', '--autoplay-policy=no-user-gesture-required', '--disable-gpu', '--no-first-run', '--disable-background-networking',
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
