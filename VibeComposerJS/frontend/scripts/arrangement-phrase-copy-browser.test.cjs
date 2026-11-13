const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const test = require('node:test');
const { findBrowser, requireProductionBuild, createBrowserServer, listen, launchBrowser, connectToPage, closeBrowser } = require('./support/browser-harness.cjs');
const browser = findBrowser();
for (const width of [1440, 780]) test('real Ctrl mouse copy drag at '+width+'px', { skip: !browser, timeout: 60000 }, async () => {
  const root = requireProductionBuild();
  const server = createBrowserServer({root,html:fs.readFileSync(path.join(root,'index.html'),'utf8'),onResult() {}});
  await listen(server);
  const profile=fs.mkdtempSync(path.resolve(__dirname,'../.angular/copy-drag-browser-'));
  const child=launchBrowser(browser,['--headless','--disable-gpu','--no-first-run','--remote-debugging-port=0','--window-size='+width+',1000','--user-data-dir='+profile,'http://127.0.0.1:'+server.address().port+'/'],{stdio:['ignore','ignore','pipe']});
  let socket;
  try {
    const connection=await connectToPage(child);socket=connection.socket;const command=connection.command;
    await command('Emulation.setDeviceMetricsOverride',{width,height:1000,deviceScaleFactor:1,mobile:false});
    const evaluate=async expression=>{
      const result=await command('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});
      assert.equal(result.exceptionDetails,undefined,JSON.stringify(result.exceptionDetails));return result.result.value;
    };
    const wait=async expression=>{
      for(let i=0;i<200;i++){const result=await evaluate(expression);if(result)return result;await new Promise(r=>setTimeout(r,30));}
      assert.fail('Timed out: '+expression);
    };
    await wait(`!!document.querySelector('[aria-label="Generation seed"]')`);
    await evaluate(`(async()=>{await changeControl(document.querySelector('[aria-label="Generation seed"]'),'42');
      window.dispatchEvent(new Event('pagehide')); // Flush the running app before replacing its saved fixture.
      const p=JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')),track=p.tracks.find(t=>t.role==='melody');
      p.arrangement=['INTRO','VERSE1'].map((type,i)=>({id:'copy-section-'+i,type,measures:4,
        trackPresence:Object.fromEntries(p.tracks.map(t=>[t.id,i===0&&t.id===track.id])),
        ...(i===0?{trackPhrases:{[track.id]:[{id:'source',midi:64,startBeat:1,durationBeats:2,velocity:90}]}}:{})}));
      localStorage.removeItem('vibecomposer.project-tabs.v1');sessionStorage.setItem('vibecomposer.project.v12',JSON.stringify(p));
      window.copyDragReloadPending=true;location.reload();})()`);
    // Wait for a new document: sessionStorage already contains the fixture before reload starts.
    await wait(`!window.copyDragReloadPending&&JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')||'null')?.arrangement[0].id==='copy-section-0'&&!!document.querySelector('vc-arrangement-overview canvas[tabindex]')`);
    await evaluate(`new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)))`);
    const points=await evaluate(`(()=>{const c=document.querySelector('vc-arrangement-overview canvas[tabindex]');c.scrollIntoView({block:'center'});const b=c.getBoundingClientRect();
      return {source:{x:b.left+24+(b.width-24)/4,y:b.top+55},track:{x:b.left+32,y:b.top+84},target:{x:b.left+24+(b.width-24)/2+8,y:b.top+84}};})()`);
    // Keep the pressed button on movement events so Chromium retains pointer capture.
    const mouse=(type,point,pressed=false)=>command('Input.dispatchMouseEvent',{type,...point,button:type==='mouseMoved'&&!pressed?'none':'left',buttons:pressed?1:0,modifiers:2,clickCount:type==='mouseMoved'?0:1});
    for (const source of [points.source,points.track]) {
      await mouse('mouseMoved',source);
      await evaluate(`new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)))`);
      await mouse('mousePressed',source,true);
      await mouse('mouseMoved',{x:source.x+6,y:source.y},true);
      await evaluate(`new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)))`);
      await mouse('mouseMoved',points.target,true);
      await evaluate(`new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)))`);
      assert.ok(await evaluate(`document.querySelector('vc-arrangement-overview canvas[tabindex]').getAttribute('data-copy-target')`),'real mouse movement highlights the destination');
      await mouse('mouseReleased',points.target);
      await wait(`Object.keys(JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')).arrangement[1].trackPhrases??{}).length===1`);
      const saved=await evaluate(`(()=>{const p=JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));return Object.values(p.arrangement[1].trackPhrases)[0]})()`);
      assert.deepEqual(saved.map(n=>[n.midi,n.startBeat,n.durationBeats,n.velocity]),[[64,1,2,90]]);
      await wait(`!document.querySelector('[aria-label="Undo"]').disabled`);
      await evaluate(`document.querySelector('[aria-label="Undo"]').click()`);
      await wait(`Object.keys(JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')).arrangement[1].trackPhrases??{}).length===0`);
    }
  } finally {await closeBrowser({child,server,socket,profile});}
});
