const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const { findBrowser, requireProductionBuild, createBrowserServer, listen, launchBrowser, connectToPage, watchBrowser, closeBrowser } = require('./support/browser-harness.cjs');
const browser = findBrowser();

for (const width of [1440, 780]) test(`variation drafts, scopes and history with 30 tracks at ${width}px`, { skip: !browser }, async () => {
  const root = requireProductionBuild();
  const index = fs.readFileSync(path.join(root, 'index.html'), 'utf8');
  const script = `<script>(async()=>{
    const wait=async(read,label)=>{for(let i=0;i<400;i++){const value=read();if(value){await new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)));return value;}await new Promise(r=>setTimeout(r,20));}throw new Error(label);};
    const read=()=>JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));
    const ensure=(value,label)=>{if(!value)throw new Error(label);};
    const button=(root,text)=>[...root.querySelectorAll('button')].find(b=>b.textContent.trim()===text);
    const dialog=()=>document.querySelector('vc-arrangement-variations-dialog');
    try{
      await wait(()=>innerWidth===${width},'viewport');
      const seed=await wait(()=>document.querySelector('[aria-label="Generation seed"]'),'app');
      if(!sessionStorage.getItem('variation-fixture')){
        await changeControl(seed,'42');const p=await wait(read,'project');
        p.melodyGlobal={...p.melodyGlobal,contextualMelody:false};
        for(const role of ['melody','bass','chords','arpeggio','drums']){
          const base=p.tracks.find(t=>t.role===role);
          for(let i=2;i<=6;i++)p.tracks.push({...base,id:'track-'+role+'-'+i,name:base.name[0]+i,generatorSettings:{...base.generatorSettings}});
        }
        p.arrangement=['BUILDUP2','CLIMAX'].map((type,i)=>({id:'section-'+(i+1),type,measures:4,
          trackPresence:Object.fromEntries(['melody','bass','chords','arpeggio','drums'].map(role=>[role,Array(6).fill(true)]))}));
        sessionStorage.setItem('vibecomposer.project.v12',JSON.stringify(p));sessionStorage.setItem('variation-fixture','1');location.reload();return;
      }
      await changeControl(seed,'43');await wait(()=>read()?.seed==='43','canonical project');
      const original=JSON.stringify(read());
      const opener=await wait(()=>document.querySelector('[aria-label="Inspect whole arrangement variations"]'),'variations button');
      opener.focus();opener.click();
      let d=await wait(dialog,'dialog');
      ensure(d.contains(document.activeElement),'dialog focus');
      let box=d.getBoundingClientRect();ensure(box.left>=0&&box.right<=innerWidth&&box.top>=0&&box.bottom<=innerHeight,'dialog fits');
      button(d,'Reroll variations').click();await wait(()=>d.querySelectorAll('article').length>0,'draft choices');
      ensure(JSON.stringify(read())===original,'reroll stays local');
      const toggle=d.querySelector('article input[type="checkbox"]');toggle.focus();toggle.click();
      await wait(()=>d.querySelector('[data-variation-status="disabled"]'),'disabled status');
      ensure(JSON.stringify(read())===original,'toggle stays local');
      button(d,'Cancel').click();await wait(()=>!dialog(),'cancel closes');
      ensure(JSON.stringify(read())===original,'cancel preserves project');
      ensure(document.activeElement===opener,'restore opener focus');
      opener.click();d=await wait(dialog,'reopen');button(d,'Reroll variations').click();
      await wait(()=>d.querySelector('article'),'new choices');button(d,'Apply variations').click();
      await wait(()=>!dialog(),'apply closes');
      const saved=JSON.stringify(read());ensure(saved!==original,'apply saves choices');
      const strip=p=>p.arrangement.map(({variations,...rest})=>rest);
      ensure(JSON.stringify(strip(read()))===JSON.stringify(strip(JSON.parse(original))),'order and presence intact');
      document.querySelector('[aria-label="Undo"]').click();await wait(()=>JSON.stringify(read())===original,'single undo');
      document.querySelector('[aria-label="Redo"]').click();await wait(()=>JSON.stringify(read())===saved,'redo');
      const canvas=document.querySelector('vc-arrangement-overview canvas');const rect=canvas.getBoundingClientRect();
      canvas.dispatchEvent(new MouseEvent('click',{bubbles:true,clientX:rect.left+70,clientY:rect.top+12}));
      const openInspector=document.querySelector('[aria-label="Open inspector"]');if(openInspector)openInspector.click();
      const scoped=await wait(()=>document.querySelector('[aria-label="Inspect selected arrangement variations"]'),'scoped control');
      scoped.click();d=await wait(dialog,'scoped dialog');
      ensure(d.querySelectorAll('.variation-section').length===1,'section scope');
      button(d,'Reset variations').click();ensure(JSON.stringify(read())===saved,'reset stays local');
      button(d,'Apply variations').click();await wait(()=>!dialog(),'reset applied');
      ensure(!read().arrangement[0].variations&&read().arrangement[1].variations,'reset leaves other section');
      document.querySelector('[aria-label="Undo"]').click();await wait(()=>JSON.stringify(read())===saved,'undo reset');
      opener.click();d=await wait(dialog,'escape dialog');
      d.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',code:'Escape',keyCode:27,bubbles:true,cancelable:true}));
      await wait(()=>!dialog(),'escape cancel');ensure(JSON.stringify(read())===saved,'escape preserves state');
      opener.click();d=await wait(dialog,'combined draft');
      d.querySelector('[aria-label="Generate arrangement variations"]').click();
      d.querySelector('[aria-label="Reroll presence too"]').click();
      button(d,'Reroll variations').click();await wait(()=>d.querySelector('.presence-changes'),'presence preview');
      ensure(!d.querySelector('[data-variation-status="absent"]'),'variations use draft presence');
      ensure(JSON.stringify(read())===saved,'combined draft stays local');
      button(d,'Cancel').click();await wait(()=>!dialog(),'combined cancel');ensure(JSON.stringify(read())===saved,'combined cancel restores both');
      opener.click();d=await wait(dialog,'combined apply');
      ensure(!d.querySelector('[aria-label="Reroll presence too"]').checked,'presence reroll defaults off');
      d.querySelector('[aria-label="Generate arrangement variations"]').click();
      d.querySelector('[aria-label="Reroll presence too"]').click();
      button(d,'Reroll variations').click();await wait(()=>d.querySelector('.presence-changes'),'combined presence');
      button(d,'Apply variations').click();await wait(()=>!dialog(),'combined saved');
      const combined=JSON.stringify(read());ensure(read().arrangementVariationGeneration.enabled,'generation policy saved');
      const structural=p=>p.arrangement.map(({trackPresence,variations,...rest})=>rest);
      ensure(JSON.stringify(structural(read()))===JSON.stringify(structural(JSON.parse(saved))),'combined keeps structure');
      ensure(JSON.stringify(read().arrangement.map(s=>s.trackPresence))!==JSON.stringify(JSON.parse(saved).arrangement.map(s=>s.trackPresence)),'combined updates presence');
      document.querySelector('[aria-label="Undo"]').click();await wait(()=>JSON.stringify(read())===saved,'combined single undo');
      document.querySelector('[aria-label="Redo"]').click();await wait(()=>JSON.stringify(read())===combined,'combined redo');
      opener.click();d=await wait(dialog,'disable generation');d.querySelector('[aria-label="Generate arrangement variations"]').click();
      button(d,'Apply variations').click();await wait(()=>!dialog(),'generation off');
      ensure(JSON.stringify(read().arrangement)===JSON.stringify(JSON.parse(combined).arrangement),'disabling keeps authored choices');
      document.querySelector('[aria-label="Reroll arrangement"]').click();
      await wait(()=>read().arrangement.every(s=>!s.variations),'generate bare arrangement');
      opener.click();d=await wait(dialog,'enable generation');d.querySelector('[aria-label="Generate arrangement variations"]').click();
      button(d,'Apply variations').click();await wait(()=>!dialog(),'generation enabled');
      document.querySelector('[aria-label="Reroll arrangement"]').click();
      await wait(()=>read().arrangement.every(s=>!!s.variations),'generate varied arrangement');
      await fetch('/result',{method:'POST',body:JSON.stringify({ok:true})});
    }catch(error){await fetch('/result',{method:'POST',body:JSON.stringify({error:error.message,stack:error.stack})});}
  })();</script>`;
  let resolve, reject;
  const result = new Promise((done, fail) => { resolve = done; reject = fail; });
  result.catch(() => {});
  const server = createBrowserServer({ root, html: index.replace('</body>', script + '</body>'), resolve, reject });
  await listen(server);
  const profile = fs.mkdtempSync(path.resolve(__dirname, '../.angular/variation-browser-'));
  const child = launchBrowser(browser, ['--headless', '--disable-gpu', '--no-first-run', '--disable-background-networking', '--no-default-browser-check',
    '--disable-background-timer-throttling', '--disable-renderer-backgrounding', '--remote-debugging-port=0', '--window-size=' + width + ',1100',
    '--user-data-dir=' + profile, 'http://127.0.0.1:' + server.address().port + '/'], { stdio: ['ignore', 'ignore', 'pipe'] });
  const watchdog = watchBrowser(child, reject, 45000);
  let socket;
  try {
    const connection = await connectToPage(child); socket = connection.socket;
    await connection.command('Emulation.setDeviceMetricsOverride', { width, height: 1100, deviceScaleFactor: 1, mobile: false });
    const actual = await result; assert.equal(actual.error, undefined, JSON.stringify(actual)); assert.equal(actual.ok, true);
  } finally { await closeBrowser({ child, server, socket, watchdog }); }
});
