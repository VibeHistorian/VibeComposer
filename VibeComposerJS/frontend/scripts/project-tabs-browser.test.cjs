const { findBrowser, createBrowserServer, listen, launchBrowser, closeBrowser, watchBrowser, requireProductionBuild } = require('./support/browser-harness.cjs');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const browser = findBrowser();
for (const width of [700, 1440]) test(`project pills, context actions, checkpoint history and reload at ${width}px`, {skip: !browser}, async () => {
  const root = requireProductionBuild();
  const script = `<script>(async () => {
    const wait = async (read, label) => { for(let i=0;i<250;i++) {const v=read();if(v)return v;await new Promise(r=>setTimeout(r,25));}throw Error('Timed out: '+label); };
    const ensure = (v,label) => {if(!v)throw Error(label);};
    const button = label => [...document.querySelectorAll('button')].find(b=>b.textContent.trim()===label || b.getAttribute('aria-label')===label);
    const pills = () => [...document.querySelectorAll('.project-pill')];
    const read = () => JSON.parse(localStorage.getItem('vibecomposer.project-tabs.v1'));
    const name = () => ({value: document.querySelector('button.project-pill.active')?.textContent.trim()});
    let checkpointName = sessionStorage.getItem('checkpoint-name');
    const editor = () => document.querySelector('input[aria-label="Project name"]');
    const editName = async (value, key='Enter') => {
      const input=await wait(editor,'inline name input');
      await wait(()=>document.activeElement===input && input.selectionStart===0 && input.selectionEnd===input.value.length,'selected whole name');
      input.value=value;input.dispatchEvent(new Event('input',{bubbles:true}));
      input.dispatchEvent(new KeyboardEvent('keydown',{key,bubbles:true,cancelable:true}));
      await wait(()=>!editor(),'name editor closed');
    };
    const rename = async value => {document.querySelector('button.project-pill.active').click();await editName(value);await wait(()=>pills().some(b=>b.textContent.trim()===value),'rename');};
    const context = async (index, label) => {
      pills()[index].dispatchEvent(new MouseEvent('contextmenu',{bubbles:true,cancelable:true,clientX:100,clientY:100,button:2}));
      (await wait(()=>[...document.querySelectorAll('[role="menuitem"]')].find(b=>b.textContent.trim()===label),label)).click();
    };
    try {
      await wait(()=>pills().length,'tabs');
      ensure(document.querySelector('.canvas-heading .brand').textContent.trim()==='V','logo only');
      ensure(!document.querySelector('.canvas-heading > input'),'redundant name field removed');
      const checkpoint=document.querySelector('.branch-project');
      ensure(getComputedStyle(checkpoint).display==='grid' && getComputedStyle(checkpoint).padding==='0px','checkpoint centering');
      if(sessionStorage.getItem('tabs-reload')) {
        ensure(pills().length===2,'restored tabs');
        ensure(name().value==='Original','restored active');
        pills()[1].click();await wait(()=>name().value===checkpointName,'switch restored copy');
        ensure(!button('Undo').disabled,'checkpoint retained undo after reload');
        await context(0,'Remove Project');await wait(()=>pills().length===1,'remove inactive');
        ensure(name().value===checkpointName,'removal keeps current project');
        await context(0,'Duplicate Project');await wait(()=>pills().length===2,'context duplicate');
        ensure(name().value===checkpointName+' (copy)','context duplicate activated');
        const downloads=[]; const native=HTMLAnchorElement.prototype.click;
        HTMLAnchorElement.prototype.click=function(){if(this.download)downloads.push({name:this.download,url:this.href});else native.call(this);};
        await context(0,'Export Project');await wait(()=>downloads.length,'export');
        const exported=await (await fetch(downloads[0].url)).json();
        ensure(exported.name===checkpointName,'exports clicked inactive tab');
        ensure(downloads[0].name.endsWith('.vibe.json'),'project extension');
        HTMLAnchorElement.prototype.click=native;
        const open = async project => {
          const transfer=new DataTransfer();transfer.items.add(new File([JSON.stringify(project)],'opened.vibe.json',{type:'application/json'}));
          const input=document.querySelector('input[type="file"][accept*=".xml-config"]');
          input.files=transfer.files;input.dispatchEvent(new Event('change',{bubbles:true}));
          await wait(()=>name().value===project.name,'file opened');
        };
        await open({...exported,name:'Opened in new tab'});
        ensure(pills().length===3,'file opens new tab by default');
        button('Settings').click();(await wait(()=>button('Project'),'project settings')).click();
        const setting=await wait(()=>document.querySelector('[aria-label="Replace current project tab when opening projects"]'),'replace setting');
        ensure(!setting.checked,'new tabs default');setting.click();
        await wait(()=>JSON.parse(localStorage.getItem('vibecomposer.ui-preferences.v1')).replaceCurrentProjectTab===true,'setting saved');
        button('Close settings').click();
        const active=read().activeId;
        await open({...exported,name:'Replaced current tab'});
        ensure(pills().length===3 && read().activeId===active,'preference replaces the current tab');
        ensure(button('Undo').disabled,'opened file starts its own history');
        ensure(document.documentElement.scrollWidth<=innerWidth,'no page overflow');
        await fetch('/result',{method:'POST',body:JSON.stringify({ok:true})});
      } else {
        await rename('Original');
        document.querySelector('button.project-pill.active').click();await editName('Discard this','Escape');
        ensure(name().value==='Original','Escape cancels rename');
        button('Branch project').click();await wait(()=>pills().length===2,'branch');
        checkpointName=name().value;
        ensure(/^Original \\(\\d{2}_\\d{2}\\)$/.test(checkpointName),'branch timestamp');
        sessionStorage.setItem('checkpoint-name',checkpointName);
        ensure(!button('Undo').disabled,'branch has source history');
        await rename('Independent edit');button('Undo').click();await wait(()=>name().value===checkpointName,'independent undo');
        pills()[0].click();await wait(()=>name().value==='Original','left click switching');
        const active=read().activeId;
        await context(1,'Rename Project');await editName('Renamed inactive');
        ensure(read().activeId===active && name().value==='Original','inactive rename keeps active tab');
        ensure(pills()[1].textContent.trim()==='Renamed inactive','inactive name changed');
        await context(1,'Rename Project');await editName(checkpointName);
        pills()[1].dispatchEvent(new MouseEvent('contextmenu',{bubbles:true,cancelable:true,clientX:100,clientY:100,button:2}));
        await wait(()=>button('Remove Project'),'right click menu');
        ensure(read().activeId===active,'right click does not switch');
        document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}));
        sessionStorage.setItem('tabs-reload','true');location.reload();
      }
    } catch(error) {await fetch('/result',{method:'POST',body:JSON.stringify({error:error.stack})});}
  })();</script>`;
  let resolve,reject;
  const result=new Promise((done,fail)=>{resolve=done;reject=fail;});result.catch(()=>{});
  const html=fs.readFileSync(path.join(root,'index.html'),'utf8').replace('</body>',script+'</body>');
  const server=createBrowserServer({root,html,resolve,reject});await listen(server);
  const profile=fs.mkdtempSync(path.resolve(__dirname,'../.angular/project-tabs-browser-'));
  const child=launchBrowser(browser,['--headless','--disable-gpu','--no-first-run','--disable-background-networking','--window-size='+width+',1000','--user-data-dir='+profile,'http://127.0.0.1:'+server.address().port],{stdio:['ignore','ignore','pipe']});
  const watchdog=watchBrowser(child,reject,45000);
  try {const output=await result;assert.equal(output.error,undefined,JSON.stringify(output));assert.equal(output.ok,true);}
  finally {await closeBrowser({child,server,watchdog});}
});
