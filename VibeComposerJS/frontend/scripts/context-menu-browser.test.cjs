const { findBrowser, connectToPage, createBrowserServer, listen, launchBrowser, closeBrowser, watchBrowser, requireProductionBuild } = require('./support/browser-harness.cjs');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const browser = findBrowser();


for (const width of [1440, 780]) test(`track context menu at ${width}px`, { skip: !browser }, async () => {
 const root=requireProductionBuild(), index=fs.readFileSync(path.join(root,'index.html'),'utf8');
 const script=`<script>(async()=>{
 const wait=async(fn,label)=>{for(let i=0;i<200;i++){const v=fn();if(v)return v;await new Promise(r=>setTimeout(r,25));}throw Error(label);};
 const ensure=(v,label)=>{if(!v)throw Error(label);};
 const read=()=>JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));
 const menuButton=text=>[...document.querySelectorAll('.context-menu button')].find(b=>b.textContent.trim()===text);
 const open=async(x=120,y=180)=>{const target=await wait(()=>document.querySelector('.track-select'),'track');target.dispatchEvent(new MouseEvent('contextmenu',{bubbles:true,cancelable:true,clientX:x,clientY:y,button:2}));const parent=await wait(()=>menuButton('Duplicate›'),'parent');parent.dispatchEvent(new MouseEvent('mouseenter'));parent.dispatchEvent(new MouseEvent('mouseover',{bubbles:true}));await wait(()=>menuButton('Global (empty)'),'submenu');};
 try{
 await wait(()=>read()?.tracks,'project');const before=JSON.stringify(read());
 await open(innerWidth-12,innerHeight-12);
 for(const panel of document.querySelectorAll('.context-menu')){const r=panel.getBoundingClientRect();ensure(r.left>=0&&r.top>=0&&r.right<=innerWidth+1&&r.bottom<=innerHeight+1,'menu fits viewport');}
 menuButton('Global (empty)').dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}));
 document.body.dispatchEvent(new MouseEvent('click',{bubbles:true}));

 await open();menuButton('Global + Events').click();
 const dialog=await wait(()=>document.querySelector('vc-duplicate-track-dialog'),'popup');
 const checks=dialog.querySelectorAll('input');ensure(checks.length===4,'checkbox count');await wait(()=>checks[1].checked,'initial checkbox');checks[1].click();await wait(()=>checks[2].disabled&&checks[3].disabled,'dependent disabled');
 ensure(JSON.stringify(read())===before,'draft unchanged');
 document.querySelector('.cdk-overlay-backdrop').click();await wait(()=>!document.querySelector('vc-duplicate-track-dialog'),'outside cancel');ensure(JSON.stringify(read())===before,'cancel unchanged');
 await open();menuButton('Global (empty)').click();await wait(()=>JSON.stringify(read())!==before,'empty duplicate');
 const p=read(),copy=p.tracks.at(-1);ensure(!copy.editedPhrase&&!copy.phraseEdits,'empty manual');ensure(p.arrangement.every(s=>!s.trackPhrases?.[copy.id]&&!s.trackPartOverrides?.[copy.id]&&!s.variations?.choices.some(c=>c.trackId===copy.id)),'empty events');
 await open();menuButton('Global + Events').click();const d=await wait(()=>document.querySelector('vc-duplicate-track-dialog'),'popup again');[...d.querySelectorAll('button')].find(b=>b.textContent==='OK').click();await wait(()=>read().tracks.length===p.tracks.length+1,'events duplicate');await wait(()=>!document.querySelector('vc-duplicate-track-dialog'),'popup closed');
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
