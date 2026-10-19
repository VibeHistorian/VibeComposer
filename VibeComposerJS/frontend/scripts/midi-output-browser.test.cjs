const { findBrowser, createBrowserServer, listen, launchBrowser, closeBrowser, watchBrowser, requireProductionBuild } = require('./support/browser-harness.cjs');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const browser = findBrowser();

for (const width of [1440, 780, 700]) test(`MIDI dropdown permissions, keyboard, selection and hotplug at ${width}px`, { skip: !browser }, async () => {
  const root = requireProductionBuild();
  const script = `<script>
    let requests=0, denied=true, changed;
    const output={id:'test',name:'External synthesizer with a very long device name',manufacturer:'Test',state:'connected',
      async open(){},async close(){},clear(){},send(){}};
    Object.defineProperty(navigator,'requestMIDIAccess',{value:async options=>{
      requests++; if(options.sysex!==false) throw Error('Unexpected SysEx');
      if(denied) throw new DOMException('Denied','NotAllowedError');
      return {outputs:new Map([[output.id,output]]),addEventListener(type,callback){changed=callback;}};
    }});
    (async()=>{
      const wait=async(read,label)=>{for(let i=0;i<400;i++){const value=read();if(value)return value;await new Promise(r=>setTimeout(r,25));}throw Error('Timed out: '+label);};
      const ensure=(value,label)=>{if(!value)throw Error(label);};
      const menu=()=>document.querySelector('[role="menu"][aria-label="MIDI output"]');
      const item=label=>[...menu().querySelectorAll('button')].find(button=>button.textContent.includes(label));
      const key=(target,value)=>target.dispatchEvent(new KeyboardEvent('keydown',{key:value,keyCode:{ArrowDown:40,Escape:27}[value],bubbles:true,cancelable:true}));
      try{
        const trigger=await wait(()=>document.querySelector('.midi-port-button'),'MIDI trigger');
        ensure(requests===0,'no permission prompt on page load');
        ensure(trigger.textContent.includes('MIDI Port...') && !trigger.classList.contains('midi-connected'),'inactive label and style');
        const settings=document.querySelector('[aria-label="Settings"]');
        ensure(trigger.nextElementSibling===settings,'selector immediately before Settings');
        const rect=trigger.getBoundingClientRect();
        ensure(rect.left>=0 && rect.right<=innerWidth,'button inside viewport');
        trigger.focus();key(trigger,'ArrowDown');
        await wait(()=>menu()?.textContent.includes('site settings'),'denial shown');
        ensure(menu().contains(document.activeElement),'keyboard opens menu and focuses item');
        key(document.activeElement,'Escape');
        await wait(()=>!menu() && document.activeElement===trigger,'Escape restores focus');
        denied=false;trigger.click();
        await wait(()=>menu() && item('External synthesizer'),'output listed after retry');
        const bounds=menu().getBoundingClientRect();
        ensure(bounds.left>=0 && bounds.right<=innerWidth && bounds.top>=0 && bounds.bottom<=innerHeight,'menu inside viewport');
        item('External synthesizer').click();
        await wait(()=>trigger.classList.contains('midi-connected'),'connected glow');
        ensure(trigger.textContent.includes(output.name),'selected name');
        const selectedBounds=trigger.getBoundingClientRect();
        ensure(selectedBounds.left>=0 && selectedBounds.right<=innerWidth,'long device name stays inside viewport');
        const files=document.querySelector('[aria-label="Project files"]').getBoundingClientRect();
        ensure(files.left>=0 && files.right<=innerWidth,'project file actions remain inside viewport');
        const glowing=getComputedStyle(trigger).boxShadow;
        ensure(glowing!=='none','accent glow visible');
        const drumOption=()=>[...document.querySelectorAll('option')].find(option=>option.textContent.includes('Kick ·'));
        ensure(drumOption()?.textContent.trim()==='Kick · 37','Auto displays chromatic drum pitch for external device');
        settings.click();
        const midiTab=await wait(()=>[...document.querySelectorAll('.settings-sidebar button')].find(button=>button.textContent.trim()==='MIDI'),'MIDI settings tab');
        midiTab.click();
        const mapping=await wait(()=>document.querySelector('[aria-label="Drum pitch mapping"]'),'drum mapping setting');
        ensure(mapping.value==='auto','Auto is default');
        const setMode=async(mode,label)=>{mapping.value=mode;mapping.dispatchEvent(new Event('change',{bubbles:true}));await wait(()=>drumOption()?.textContent.trim()===label,'mapping updates drum labels: '+mode);};
        await setMode('general-midi','Kick · 36');
        ensure(JSON.parse(localStorage.getItem('vibecomposer.ui-preferences.v1')).drumMapping==='general-midi','mapping persisted');
        await setMode('chromatic','Kick · 37');
        await setMode('auto','Kick · 37');
        document.querySelector('[aria-label="Close settings"]').click();
        await wait(()=>!document.querySelector('[aria-label="Drum pitch mapping"]'),'settings closed');
        trigger.click();await wait(menu,'selected menu');
        ensure(item('External synthesizer').getAttribute('aria-checked')==='true','selected output accessible');
        item('Internal synth').click();
        await wait(()=>!trigger.classList.contains('midi-connected'),'internal synth clears glow');
        ensure(trigger.textContent.includes('MIDI Port...'),'empty label restored');
        await wait(()=>drumOption()?.textContent.trim()==='Kick · 36','Auto restores GM labels for internal synth');
        trigger.click();await wait(menu,'reselect menu');item('External synthesizer').click();
        await wait(()=>trigger.classList.contains('midi-connected'),'reselected');
        output.state='disconnected';changed();
        await wait(()=>!trigger.classList.contains('midi-connected'),'disconnect resets selector');
        trigger.click();await wait(()=>menu()?.textContent.includes('disconnected'),'disconnect status');
        ensure(!item('External synthesizer'),'disconnected output removed');
        output.state='connected';changed();
        await wait(()=>item('External synthesizer'),'hotplug output appears');
        await fetch('/result',{method:'POST',body:JSON.stringify({ok:true})});
      }catch(error){await fetch('/result',{method:'POST',body:JSON.stringify({error:error.stack})});}
    })();
  </script>`;
  const index = fs.readFileSync(path.join(root, 'index.html'), 'utf8').replace('</body>', script + '</body>');
  let resolve, reject;
  const result = new Promise((done, fail) => { resolve = done; reject = fail; });
  result.catch(() => {}); // CDP setup can fail before the result is awaited.
  const server = createBrowserServer({ root, html: index, resolve, reject });
  await listen(server);
  const profile = fs.mkdtempSync(path.resolve(__dirname, '../.angular/midi-browser-'));
  const child = launchBrowser(browser, ['--headless', '--disable-gpu', '--no-first-run', '--disable-background-networking',
    '--disable-background-timer-throttling', '--disable-renderer-backgrounding', '--no-default-browser-check',
    '--user-data-dir=' + profile, '--autoplay-policy=no-user-gesture-required', '--window-size=' + width + ',1000',
    'http://127.0.0.1:' + server.address().port], { stdio: ['ignore', 'ignore', 'pipe'] });
  const watchdog = watchBrowser(child, reject, 25000);
  try { const output = await result; assert.equal(output.error, undefined, JSON.stringify(output)); assert.equal(output.ok, true); }
  finally {
    await closeBrowser({ child, server, watchdog });
  }
});
