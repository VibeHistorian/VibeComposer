const { findBrowser, connectToPage, createBrowserServer, listen, launchBrowser, closeBrowser, watchBrowser, requireProductionBuild } = require('./support/browser-harness.cjs');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const browser = findBrowser();

for (const width of [1440,780]) test(`contextual generation settings and visibility at ${width}px`, { skip: !browser }, async () => {
  const root = requireProductionBuild(), index = fs.readFileSync(path.join(root,'index.html'),'utf8');
  const script = `<script>(async () => {
    const wait = async (read,label) => { for(let i=0;i<200;i++){const value=read();if(value)return value;await new Promise(r=>setTimeout(r,30));}throw new Error(label); };
    const ensure=(value,label)=>{if(!value)throw new Error(label);};
    const read=()=>JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));
    const change=(input,value)=>{input.value=value;input.dispatchEvent(new Event('change',{bubbles:true}));};
    const button=label=>[...document.querySelectorAll('button')].find(b=>b.textContent.trim()===label||b.getAttribute('aria-label')===label);
    const settings=async tab=>{
      button('Settings').click(); const dialog=await wait(()=>document.querySelector('vc-settings-dialog'),'settings dialog');
      (await wait(()=>[...dialog.querySelectorAll('.settings-sidebar button')].find(b=>(tab==='Behavior' ? b.getAttribute('aria-label')==='Behavior General' : b.textContent.trim()===tab)),'settings tab '+tab)).click();return dialog;
    };
    try {
      await wait(()=>innerWidth===${width}&&button('Settings'),'workspace');
      let dialog=await settings('Behavior');
      let generation=await wait(()=>dialog.querySelector('[aria-label="Harmony generation"]'),'harmony generation');
      ensure(generation.value==='CURRENT','Legacy default');
      ensure([...generation.options].map(o=>o.textContent.trim()).join(',')==='Legacy,Functional,Fifths,Pop loop,Modal,Random','harmony choices');
      change(generation,'FUNCTIONAL');await wait(()=>read()?.harmonySettings?.generation==='FUNCTIONAL','save harmony algorithm');
      dialog.querySelector('[aria-label="Close settings"]').click();
      button('Generate chord progression').click();
      await wait(()=>read().progressionChords?.length===4,'functional generation');
      document.querySelector('.track-group.melody .group-select').click();
      let globals=await wait(()=>document.querySelector('vc-melody-global-settings'),'global melody controls');
      ensure(!globals.querySelector('[aria-label="Melody Low MIDI"]'),'hide contextual fields by default');
      ensure(globals.querySelector('[aria-label="Tonic note target"]'),'show block controls');
      dialog=await settings('Melody');
      let toggle=await wait(()=>dialog.querySelector('[aria-label="Contextual Melody"]'),'contextual toggle');
      ensure(!toggle.checked,'contextual default off');toggle.click();
      await wait(()=>read().melodyGlobal?.contextualMelody===true,'save contextual toggle');
      dialog.querySelector('[aria-label="Close settings"]').click();
      globals=await wait(()=>document.querySelector('vc-melody-global-settings [aria-label="Melody Low MIDI"]')?.closest('vc-melody-global-settings'),'contextual fields');
      ensure(!globals.querySelector('[aria-label="Tonic note target"]')&&!globals.querySelector('[aria-label="Block target mode"]'),'hide obsolete globals');
      ensure(!document.querySelector('vc-part-settings-editor [data-control-key="patternFlexible"]'),'hide Flex');
      for(const [label,value] of [['Melody Low MIDI','62'],['Melody High MIDI','81'],['Melody Tension','50'],['Phrase Bars','3'],['Beats per Bar','3'],['Melody Style','JAZZ'],['Melody Form','QUESTION_ANSWER'],['Melody Ending','CLOSED']]) {
        change(globals.querySelector('[aria-label="'+label+'"]'),value);await new Promise(r=>requestAnimationFrame(r));
      }
      await wait(()=>read().melodyGlobal?.style==='JAZZ'&&read().melodyGlobal?.rangeMin===62&&read().melodyGlobal?.beatsPerBar===3,'save contextual fields');
      button('Edit quick settings').click();
      await wait(()=>document.querySelector('vc-part-settings-editor [data-control-key="patternFlexible"]'),'dormant controls while editing placement');
      button('Save quick settings').click();
      await wait(()=>!document.querySelector('vc-part-settings-editor [data-control-key="patternFlexible"]'),'hide dormant controls after placement');
      dialog=await settings('Melody');
      toggle=await wait(()=>dialog.querySelector('[aria-label="Contextual Melody"]'),'disable contextual');toggle.click();
      await wait(()=>read().melodyGlobal?.contextualMelody===false,'save disabled contextual');
      dialog.querySelector('[aria-label="Close settings"]').click();
      await wait(()=>document.querySelector('vc-melody-global-settings [aria-label="Tonic note target"]'),'restore block controls');
      ensure(!document.querySelector('vc-melody-global-settings [aria-label="Melody Low MIDI"]'),'hide contextual controls');
      dialog=await settings('Behavior');
      generation=await wait(()=>dialog.querySelector('[aria-label="Harmony generation"]'),'Random selector');change(generation,'RANDOM');
      await wait(()=>read().harmonySettings?.generation==='RANDOM','save Random');
      dialog.querySelector('[aria-label="Close settings"]').click();button('Generate chord progression').click();
      await wait(()=>read().progression.length===4,'Random generation');
      ensure(read().melodyGlobal.rangeMin===62,'hidden settings retained');
      await fetch('/result',{method:'POST',body:JSON.stringify({ok:true})});
    } catch(error) {await fetch('/result',{method:'POST',body:JSON.stringify({error:error.message,stack:error.stack})});}
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
