const { findBrowser, createBrowserServer, listen, launchBrowser, closeBrowser, watchBrowser, requireProductionBuild } = require('./support/browser-harness.cjs');
const { fixture } = require('./support/project-fixture.cjs');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const browser = findBrowser();

for (const failing of [false, true]) test(`hosted startup and project menu under a Pages subpath${failing ? ' with invalid files' : ''}`, {skip:!browser}, async () => {
  const f=fixture(), project=JSON.parse(f.service.exportProjectJson()), preferences=JSON.parse(f.uiPreferences.exportJson());
  const resources={
    '/startup.json':{demoProject:'defaults/demo.json',recommendedSettings:'defaults/settings.json'},
    '/defaults/demo.json':failing?{}:{...project,name:'Hosted demo'},
    '/defaults/settings.json':failing?{}:{...preferences,compactHistory:true,midiPpq:480},
    '/defaults/linked.json':failing?{}:{...project,name:'Linked project'},
    '/defaults/linked-settings.json':{...preferences,midiPpq:960,replaceCurrentProjectTab:true},
    '/defaults/theme.json':{...preferences,midiPpq:240,appearance:{...preferences.appearance,roleColors:{...preferences.appearance.roleColors,melody:'#123456'}}},
  };
  const script=`<script>(async () => {
    const wait=async(read,label)=>{for(let i=0;i<250;i++){const v=read();if(v)return v;await new Promise(r=>setTimeout(r,25));}throw Error('Timed out: '+label);};
    const ensure=(v,label)=>{if(!v)throw Error(label);};
    const pills=()=>[...document.querySelectorAll('button.project-pill')];
    const active=()=>document.querySelector('button.project-pill.active')?.textContent.trim();
    const prefs=()=>JSON.parse(localStorage.getItem('vibecomposer.ui-preferences.v1'));
    const workspace=()=>JSON.parse(localStorage.getItem('vibecomposer.project-tabs.v1'));
    const menu=async label=>{
      document.querySelector('.branch-project').dispatchEvent(new MouseEvent('contextmenu',{bubbles:true,cancelable:true,button:2,clientX:200,clientY:60}));
      const item=await wait(()=>[...document.querySelectorAll('[role="menuitem"]')].find(item=>item.textContent.trim()===label),label);
      ensure(item.getAttribute('aria-disabled')!=='true',label+' enabled');item.click();
    };
    try {
      await wait(()=>pills().length,'application ready');
      if(${failing}) {
        ensure(pills().length===1 && active()!=='Hosted demo','invalid imports retain fallback');
        await wait(()=>document.querySelector('[role="status"]')?.textContent.includes('invalid file'),'error displayed');
        ensure(new URL(location.href).searchParams.has('project'),'failed link retained');
        const before=active();await menu('Demo Project');
        await wait(()=>document.querySelector('[role="status"]')?.textContent.includes('demo project'),'menu error');
        ensure(pills().length===1 && active()===before,'failed demo import atomic');
        await menu('Empty Project');await wait(()=>pills().length===2,'fallback usable');
        await fetch('/result',{method:'POST',body:JSON.stringify({ok:true})});return;
      }
      const phase=Number(sessionStorage.getItem('hosted-phase')||0);
      if(phase===0) {
        ensure(pills().length===1 && active()==='Hosted demo','demo replaces initial fallback');
        ensure(prefs().midiPpq===480 && prefs().compactHistory,'recommended settings');
        ensure(document.querySelector('[aria-label="Undo"]').disabled,'fresh history');
        await menu('Empty Project');await wait(()=>pills().length===2,'empty tab');
        ensure(active()!=='Hosted demo','empty uses built-in default');
        await menu('Demo Project');await wait(()=>pills().length===3 && active()==='Hosted demo','demo tab');
        document.querySelector('button.project-pill.active').click();
        const input=await wait(()=>document.querySelector('input[aria-label="Project name"]'),'rename');
        input.value='Saved demo edits';input.dispatchEvent(new Event('input',{bubbles:true}));
        input.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',bubbles:true,cancelable:true}));
        await wait(()=>active()==='Saved demo edits','rename applied');
        await wait(()=>workspace()?.tabs.length===3 && workspace().tabs.some(tab=>tab.project.name==='Saved demo edits'),'workspace saved');
        dispatchEvent(new Event('pagehide'));
        localStorage.setItem('vibecomposer.ui-preferences.v1',JSON.stringify({...prefs(),midiPpq:240,compactHistory:false}));
        sessionStorage.setItem('hosted-phase','1');location.reload();
      } else if(phase===1) {
        ensure(pills().length===3 && active()==='Saved demo edits','saved workspace wins');
        ensure(prefs().midiPpq===240 && !prefs().compactHistory,'stored settings win');
        sessionStorage.setItem('hosted-phase','2');
        location.href='?project=defaults/linked.json&settings=defaults/linked-settings.json&theme=defaults/theme.json&keep=1#preview';
      } else if(phase===2) {
        ensure(pills().length===4 && active()==='Linked project','URL adds a tab despite replace preference');
        ensure(pills().some(pill=>pill.textContent.trim()==='Saved demo edits'),'saved work preserved');
        ensure(prefs().midiPpq===960 && prefs().replaceCurrentProjectTab,'URL settings applied');
        ensure(prefs().appearance.roleColors.melody==='#123456','theme imports appearance only');
        const url=new URL(location.href);
        ensure(!['project','settings','theme'].some(key=>url.searchParams.has(key)),'successful imports consumed');
        ensure(url.searchParams.get('keep')==='1' && url.hash==='#preview','other URL state preserved');
        await wait(()=>workspace()?.tabs.length===4,'URL project saved');
        sessionStorage.setItem('hosted-phase','3');location.reload();
      } else {
        ensure(pills().length===4 && active()==='Linked project','reload does not duplicate imports');
        ensure(prefs().appearance.roleColors.melody==='#123456','theme persisted');
        await fetch('/result',{method:'POST',body:JSON.stringify({ok:true})});
      }
    } catch(error) {await fetch('/result',{method:'POST',body:JSON.stringify({error:error.stack})});}
  })();</script>`;
  const root=requireProductionBuild();
  // The protected broker initially accepts only a fixture root URL. Establish
  // the real Pages subpath before Angular starts; subsequent navigations use it.
  const setup=`<script>if(location.pathname==='/')history.replaceState(null,'','/VibeComposerJS/${failing?'?project=defaults/linked.json':''}');</script>`;
  const html=fs.readFileSync(path.join(root,'index.html'),'utf8').replace('<head>','<head>'+setup)
    .replace('<base href="/">','<base href="/VibeComposerJS/">').replace('</body>',script+'</body>');
  let resolve,reject;const result=new Promise((done,fail)=>{resolve=done;reject=fail;});result.catch(()=>{});
  const server=createBrowserServer({root,html,resolve,reject});
  const serve=server.listeners('request')[0];server.removeAllListeners('request');
  server.on('request',(request,response)=>{
    const url=new URL(request.url,'http://localhost'),resource=url.pathname.replace(/^\/VibeComposerJS(?=\/)/,'');
    if(resource in resources){response.setHeader('Content-Type','application/json');response.end(JSON.stringify(resources[resource]));return;}
    request.url=resource+url.search;serve(request,response);
  });
  await listen(server);
  const profile=fs.mkdtempSync(path.resolve(__dirname,'../.angular/hosted-content-browser-'));
  const child=launchBrowser(browser,['--headless','--disable-gpu','--no-first-run','--disable-background-networking','--window-size=1100,900',
    '--user-data-dir='+profile,'http://127.0.0.1:'+server.address().port]);
  const watchdog=watchBrowser(child,reject,45000);
  try{const output=await result;assert.equal(output.error,undefined,JSON.stringify(output));assert.equal(output.ok,true);}
  finally{await closeBrowser({child,server,watchdog});}
});
