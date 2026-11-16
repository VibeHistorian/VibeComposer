const { findBrowser, createBrowserServer, listen, launchBrowser, closeBrowser, watchBrowser, requireProductionBuild } = require('./support/browser-harness.cjs');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const browser = findBrowser();

test('recording backing advances transport and arrangement while x/X release and melodic/drum highlights stay separate', {skip:!browser,timeout:60000},async()=>{
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
  (await wait(()=>document.querySelector('.track-group.drums .track-select'),'drum setup')).click();
  await changeControl(await wait(()=>document.querySelector('.track-group.drums .track-instrument'),'drum instrument'),'62');
  if(!sessionStorage.getItem('conga-fixture-ready')){
    window.dispatchEvent(new Event('pagehide'));
    const project=JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));
    const drum=project.tracks.find(track=>track.role==='drums');
    drum.editedPhrase=[{id:'conga-fixture',midi:62,startBeat:0,durationBeats:4,velocity:90}];
    project.key='C';project.scale='major';project.transposeSemitones=0;project.tempoBpm=120;project.progression=[1,5,6,4];
    project.arrangement=[{id:project.arrangement[0].id,type:'VERSE1',measures:4,trackPresence:Object.fromEntries(project.tracks.map(track=>[track.id,true])),trackPhrases:{[drum.id]:[{id:'conga-local',midi:62,startBeat:0,durationBeats:16,velocity:90}]}}];
    localStorage.removeItem('vibecomposer.project-tabs.v1');sessionStorage.setItem('vibecomposer.project.v12',JSON.stringify(project));
    sessionStorage.setItem('conga-fixture-ready','1');location.reload();return;
  }
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
  check(!document.querySelector('[aria-label="Play other tracks while recording"]').checked,'backing playback defaults off');
  for(const shifted of [false,true])for(let repeat=0;repeat<2;repeat++){
    press('keydown','KeyX',shifted?'X':'x',shifted);
    await wait(()=>document.querySelector('.pitch-key.sounding[title="MIDI '+(shifted?89:88)+'"]'),'x/X starts again');
    press('keyup','',shifted?'X':'x',shifted);
    await wait(()=>!document.querySelector('.pitch-key.sounding'),'x/X release clears highlight');
  }
  press('keydown','KeyY','y');
  await wait(()=>document.querySelector('.pitch-key.sounding[title="MIDI 62"]'),'y melodic highlight');
  check([...document.querySelectorAll('.drum-label')].some(label=>label.textContent.includes('CONGA')),'matching GM drum row is visible: '+[...document.querySelectorAll('.drum-label')].map(label=>label.textContent).join(','));
  check(!document.querySelector('.drum-label.sounding'),'y does not highlight the drum sharing its MIDI number');
  press('keyup','KeyY','y');
  press('keydown','KeyQ','q');
  await wait(()=>document.querySelector('.pitch-key.sounding[title="MIDI 53"]'),'idle keyboard key highlight');
  port.onmidimessage({data:[0x90,60,100]});
  await wait(()=>document.querySelector('.pitch-key.sounding[title="MIDI 60"]'),'idle MIDI key highlight');
  press('keyup','KeyQ','q');
  await wait(()=>!document.querySelector('.pitch-key.sounding[title="MIDI 53"]') && document.querySelector('.pitch-key.sounding[title="MIDI 60"]'),'independent key release');
  port.onmidimessage({data:[0x80,60,0]});
  await wait(()=>!document.querySelector('.pitch-key.sounding'),'all idle keys released');
  check(button('Apply').disabled,'idle audition does not change the phrase');
  port.onmidimessage({data:[0x90,108,100]});
  await wait(()=>document.querySelector('.pitch-key.sounding[title="MIDI 108"]'),'idle MIDI expands pitch view');
  await wait(()=>document.querySelector('.keyboard-label.column-two'),'dense labels use two columns');
  for(const second of [false,true]){
    const boxes=[...document.querySelectorAll('.keyboard-label')].filter(label=>label.classList.contains('column-two')===second).map(label=>label.getBoundingClientRect()).sort((a,b)=>a.top-b.top);
    for(let i=1;i<boxes.length;i++)check(boxes[i-1].bottom<=boxes[i].top+.1,'dense labels do not overlap');
  }
  port.onmidimessage({data:[0x80,108,0]});
  await wait(()=>!document.querySelector('.pitch-key.sounding'),'outlier release');
  check(button('Apply').disabled,'zooming and auditioning leave the draft unchanged');
  const before=sessionStorage.getItem('vibecomposer.project.v12');
  const countIn=document.querySelector('vc-live-recording input[type="checkbox"]');countIn.click();
  button('Record live').click();await wait(()=>document.querySelector('[aria-label="Recording count-in"]'),'count-in');
  press('keydown','Digit1','!',true);press('keyup','Digit1','!',true);await pause(150);
  check(!document.querySelector('#note-pitch'),'count-in ignores early input');
  await wait(()=>document.querySelector('[aria-label="Recording beat"]'),'capture after count-in',1000);
  press('keydown','Digit1','!',true);
  await wait(()=>document.querySelector('.pitch-key[title="MIDI 37"]'),'live pitch fits before release');
  check(!!document.querySelector('[data-keyboard-midi="37"]'),'keyboard mapping appears on piano keys');
  const labels=[...document.querySelectorAll('.keyboard-label')];
  check(labels.length>1 && labels.every(label=>label.parentElement.classList.contains('score-gutter')),'labels belong to the common gutter');
  const right=labels.find(label=>!label.classList.contains('column-two')).getBoundingClientRect().right;
  check(labels.filter(label=>!label.classList.contains('column-two')).every(label=>Math.abs(label.getBoundingClientRect().right-right)<.1),'keyboard labels align in their column');
  await wait(()=>document.querySelector('.pitch-key.sounding[title="MIDI 37"]'),'recorded input highlights piano key');
  await pause(220);press('keyup','Digit1','!',true);
  await wait(()=>!document.querySelector('.pitch-key.sounding[title="MIDI 37"]'),'recorded key clears on release');
  await wait(()=>document.querySelector('#note-pitch')?.value==='37','C sharp keyboard');
  check(document.querySelector('#note-length').value>0,'held keyboard duration');
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
  await wait(()=>document.querySelector('[data-keyboard-midi="51"]')?.textContent.includes('3'),'mapped scale-degree label');
  press('keydown','Digit3','3');await pause(150);press('keyup','Digit3','3');
  await wait(()=>document.querySelector('#note-pitch')?.value==='51','scale-matched pitch');
  document.querySelector('vc-live-recording [aria-label="Match keyboard scale/key"]').click();
  button('Stop recording').focus();
  press('keydown','Digit3','3');await pause(150);press('keyup','Digit3','3');
  await wait(()=>document.querySelector('#note-pitch')?.value==='40','optional matching off');
  await wait(()=>document.querySelector('[data-keyboard-midi="40"]')?.textContent.includes('3'),'unmatched C-major label');
  button('Stop recording').click();button('Cancel').click();
  await wait(()=>!document.querySelector('#phrase-grid'),'cancelled minor phrase');
  document.querySelector('.track-group.bass .track-select').click();
  (await wait(()=>[...document.querySelectorAll('#part-inspector button')].find(b=>b.textContent.trim().startsWith('Edit notes')),'backing editor')).click();
  await wait(()=>document.querySelector('#phrase-grid'),'backing phrase');
  const backingOption=await wait(()=>document.querySelector('[aria-label="Play other tracks while recording"]'),'backing checkbox');backingOption.click();
  button('Record live').focus();button('Record live').click();
  await wait(()=>document.querySelector('[aria-label="Recording beat"]'),'backing ready');
  const progress=()=>Number(document.querySelector('[aria-label="Song position"]').getAttribute('aria-valuenow'));
  const position=progress();const overview=document.querySelector('vc-arrangement-overview .playhead-canvas');const overviewBefore=overview.toDataURL();
  await pause(400);check(progress()!==position,'transport position advances during backing recording');
  check(overview.toDataURL()!==overviewBefore,'arrangement playhead advances during backing recording');
  check(button('Apply').disabled,'backing alone creates no phrase notes');
  button('Stop recording').click();await pause(60);const stopped=progress();await pause(160);check(progress()===stopped,'backing stops with recording');
  button('Cancel').click();await wait(()=>!document.querySelector('#phrase-grid'),'backing editor closed');
  await fetch('/result',{method:'POST',body:JSON.stringify({passed:true})});
 }catch(error){await fetch('/result',{method:'POST',body:JSON.stringify({error:error.stack + " | controls: " + document.querySelector('vc-live-recording')?.textContent + " | alerts: " + [...document.querySelectorAll('[role=alert]')].map(e=>e.textContent).join(' / ')})});}
})();</script>`;
  let resolve, reject;
  const result = new Promise((done, fail) => { resolve = done; reject = fail; });
  result.catch(() => {}); // CDP setup can fail before the result is awaited.
  const server = createBrowserServer({ root, html: index.replace('</body>', script + '</body>'), resolve, reject });
  await listen(server);
  const profile = fs.mkdtempSync(path.resolve(__dirname, '../.angular/recording-backing-browser-'));
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
