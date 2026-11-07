const { findBrowser, connectToPage, createBrowserServer, listen, launchBrowser, closeBrowser, watchBrowser, requireProductionBuild } = require('./support/browser-harness.cjs');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const browser = findBrowser();


for (const width of [1440, 700]) test(`grouped selection menus at ${width}px`, { skip: !browser }, async () => {
 const root=requireProductionBuild(), index=fs.readFileSync(path.join(root,'index.html'),'utf8');
 const script=`<script>(async()=>{
 const wait=async(fn,label)=>{for(let i=0;i<200;i++){const v=fn();if(v)return v;await new Promise(r=>setTimeout(r,25));}throw Error(label);};
 const check=(v,label)=>{if(!v)throw Error(label);};
 const read=()=>JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));
 const fit=()=>{for(const panel of document.querySelectorAll('.selection-menu')){const r=panel.getBoundingClientRect();check(r.left>=0&&r.top>=0&&r.right<=innerWidth+1&&r.bottom<=innerHeight+1,'menu fits viewport');}};
 try{
 const mode=await wait(()=>document.querySelector('vc-menu-select[aria-label="Mode"]'),'mode');
 check((await selectOptions(mode)).length===17,'all scale families reachable');
 await changeControl(mode,'harmonic-minor');await wait(()=>read().scale==='harmonic-minor','mode selection');
 mode.dispatchEvent(new MouseEvent('contextmenu',{button:2,bubbles:true,cancelable:true}));
 await wait(()=>read().scale==='major','mode reset');
 const engine=document.querySelector('vc-menu-select[aria-label="Audio instrument engine"]');
 const input=document.querySelector('input[aria-label="Load soundfont file"]') || document.querySelector('vc-transport-dock input[type="file"]');
 let pickerOpens=0;input.click=()=>pickerOpens++;
 engine.dispatchEvent(new WheelEvent('wheel',{deltaY:100,bubbles:true,cancelable:true}));
 engine.dispatchEvent(new MouseEvent('contextmenu',{button:2,bubbles:true,cancelable:true}));
 check(pickerOpens===0,'engine gestures never invoke file actions');
 const engineMenu=await selectPopup(engine), load=engineMenu.querySelector('[data-action="load-soundfont"]');
 check(load?.getAttribute('role')==='menuitem','load soundfont is a command');
 load.click();await wait(()=>pickerOpens===1,'load action invoked');
 check(engine.getAttribute('value')==='synth','file action preserves selected engine');
 const picker=await wait(()=>document.querySelector('.track-group.melody vc-menu-select'),'instrument');
 check(picker.getAttribute('role')==='button','menu button semantics');
 const before=JSON.stringify(read());const options=await selectOptions(picker);
 check(options.length===128,'all GM instruments reachable');check(JSON.stringify(read())===before,'browsing never commits');
 await changeControl(picker,'127');await wait(()=>read().tracks.find(t=>t.role==='melody').mix.program===127,'instrument commit');
 picker.click();let root=await wait(()=>document.getElementById(picker.getAttribute('aria-controls')),'root');
 const effects=[...root.querySelectorAll('button')].find(b=>b.textContent.includes('Sound effects'));effects.click();
 const selected=await wait(()=>document.querySelector('.selection-menu [data-value="127"]'),'selected leaf');
 check(selected.getAttribute('aria-checked')==='true','selected radio exposed');fit();
 const plain=document.querySelector('.selection-menu [data-value="126"]');
 const normal=getComputedStyle(plain).color, accent=getComputedStyle(selected).color;
 check(normal!==accent,'only the selected leaf has accent text');
 plain.focus();check(getComputedStyle(plain).color===normal,'keyboard focus retains normal text color');
 check(getComputedStyle(plain).backgroundColor!==getComputedStyle(selected).backgroundColor,'keyboard focus changes the background');
 check(getComputedStyle(selected).color===accent,'selected leaf retains accent text');
 selected.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}));
 picker.dispatchEvent(new MouseEvent('contextmenu',{button:2,bubbles:true,cancelable:true}));
 await wait(()=>picker.getAttribute('value')!=='127','right click resets');
 const reset=picker.getAttribute('value');picker.dispatchEvent(new WheelEvent('wheel',{deltaY:100,bubbles:true,cancelable:true}));
 await wait(()=>picker.getAttribute('value')!==reset,'wheel steps');
 const drum=document.querySelector('.track-group.drums vc-menu-select');const drums=await selectOptions(drum);
 check(drums.length===16,'all drum pitches reachable');await changeControl(drum,'82');await wait(()=>drum.textContent.includes('Shaker'),'mapped drum label');
 document.querySelector('.track-group.chords .group-select').click();
 const strum=await wait(()=>document.querySelector('vc-part-settings-editor[data-control-role="chords"] vc-menu-select[data-parameter-key="strumType"]'),'strum');
 const types=await selectOptions(strum);check(types.length===9,'all strum variants reachable');
 await changeControl(strum,'HUMAN_D');await wait(()=>read().tracks.filter(t=>t.role==='chords').every(t=>t.generatorSettings.strumType==='HUMAN_D'),'strum commits scoped setting');
 check(strum.textContent.trim()==='Human.Down','compact selected strum label');
 for(const [value,label] of [['ARP_U','Arp.Up'],['ARP_D','Arp.Down'],['RAND_U','Rand.Up'],['RAND','Rand.Any'],['RAND_D','Rand.Down'],['RAND_WU','Rand.Spaced'],['HUMAN_U','Human.Up'],['HUMAN','Human.Any'],['HUMAN_D','Human.Down']]) {
   await changeControl(strum,value);await wait(()=>strum.textContent.trim()===label,'compact selected strum '+value);
 }
 for(const [role,count] of [['melody',2],['bass',10],['chords',8],['arpeggio',10],['drums',9]]) {
   document.querySelector('.track-group.'+role+' .group-select').click();
   const editor=await wait(()=>document.querySelector('vc-part-settings-editor[data-control-role="'+role+'"]'),'role editor '+role);
   const rhythm=editor.querySelector('vc-menu-select[data-parameter-key="rhythm"]');
   check((await selectOptions(rhythm)).length===count,'role rhythm catalogue '+role);
   const fill=editor.querySelector('vc-menu-select[data-parameter-key="chordSpanFill"]');
   check((await selectOptions(fill)).length===12,'all fill families reachable '+role);
   await changeControl(fill,'F23');await wait(()=>read().tracks.filter(t=>t.role===role).every(t=>t.generatorSettings.chordSpanFill==='F23'),'fill selection '+role);
 }
 document.querySelector('.track-group.chords .group-select').click();
 const reopened=await wait(()=>document.querySelector('vc-part-settings-editor[data-control-role="chords"] vc-menu-select[data-parameter-key="strumType"]'),'reopened strum');
 reopened.click();await wait(()=>document.querySelector('.selection-menu'),'strum menu');fit();
 await fetch('/result',{method:'POST',body:JSON.stringify({ok:true})});
 }catch(error){await fetch('/result',{method:'POST',body:JSON.stringify({error:error.message,stack:error.stack})});}
 })();</script>`;

  let resolve, reject;
  const result = new Promise((done,fail)=>{resolve=done;reject=fail;});
  result.catch(() => {}); // CDP setup can fail before the result is awaited.
  const server = createBrowserServer({ root, html: index.replace('</body>', script + '</body>'), resolve, reject });
  await listen(server);
  const profile=fs.mkdtempSync(path.resolve(__dirname,'../.angular/contextual-generation-browser-'));
  const child=launchBrowser(browser,['--headless','--disable-gpu','--no-first-run','--disable-background-networking','--no-default-browser-check',
    '--disable-background-timer-throttling','--disable-renderer-backgrounding','--remote-debugging-port=0','--window-size='+width+',1100',
    '--user-data-dir='+profile,'http://127.0.0.1:'+server.address().port+'/'],{stdio:['ignore','ignore','pipe']});
  const watchdog = watchBrowser(child, reject, 45000);
  let socket;
  try {
    const connection = await connectToPage(child);
    socket = connection.socket;
    const command = connection.command;
    await command('Emulation.setDeviceMetricsOverride', { width, height: 1100, deviceScaleFactor: 1, mobile: false });
    const actual=await result;assert.equal(actual.error,undefined,JSON.stringify(actual));assert.equal(actual.ok,true);
  } finally {
    await closeBrowser({ child, server, socket, watchdog });
  }
});

