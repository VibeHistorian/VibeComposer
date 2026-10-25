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
    const change = changeControl;
    const button=label=>[...document.querySelectorAll('button')].find(b=>b.textContent.trim()===label||b.getAttribute('aria-label')===label);
    const settings=async tab=>{
      button('Settings').click(); const dialog=await wait(()=>document.querySelector('vc-settings-dialog'),'settings dialog');
      (await wait(()=>[...dialog.querySelectorAll('.settings-sidebar button')].find(b=>(tab==='Behavior' ? b.getAttribute('aria-label')==='Behavior General' : b.textContent.trim()===tab)),'settings tab '+tab)).click();return dialog;
    };
    try {
      await wait(()=>innerWidth===${width}&&button('Settings'),'workspace');
      let dialog=await settings('Harmony');
      let generation=await wait(()=>dialog.querySelector('[aria-label="Harmony generation"]'),'harmony generation');
      ensure(generation.getAttribute("value")==='CURRENT','Legacy default');
      ensure((await selectOptions(generation)).map(o=>o.textContent.trim()).join(',')==='Legacy,Functional,Fifths,Pop loop,Modal,Random','harmony choices');
      await change(generation,'FUNCTIONAL');await wait(()=>read()?.harmonySettings?.generation==='FUNCTIONAL','save harmony algorithm');
      dialog.querySelector('[aria-label="Close settings"]').click();
      button('Generate chord progression').click();
      await wait(()=>read().progressionChords?.length===4,'functional generation');
      document.querySelector('.track-group.melody .group-select').click();
      ensure(!document.querySelector('#part-inspector vc-melody-global-settings'),'globals removed from inspector');
      ensure(!document.querySelector('vc-part-settings-editor [aria-label="TON%"]'),'contextual controls by default');
      let globals;
      dialog=await settings('Melody');
      let toggle=await wait(()=>dialog.querySelector('[aria-label="Contextual Melody"]'),'contextual toggle');
      ensure(toggle.checked,'contextual default on');
      globals=await wait(()=>document.querySelector('vc-melody-global-settings [aria-label="Phrase Bars"]')?.closest('vc-melody-global-settings'),'contextual fields');
      ensure(!globals.querySelector('[aria-label="TON%"]')&&!globals.querySelector('[aria-label="Block target mode"]'),'hide obsolete globals');
      ensure(!document.querySelector('vc-part-settings-editor [data-control-key="patternFlexible"]'),'hide Flex');
      ensure(!globals.querySelector('[aria-label="Melody Ending"]')&&!globals.querySelector('[aria-label="Melody Style"]')&&!globals.querySelector('[aria-label="Melody Form"]'),'phrasing removed from global melody');
      for(const [label,value] of [['Phrase Bars','3'],['Beats per Bar','3']]) {
        await change(globals.querySelector('[aria-label="'+label+'"]'),value);await new Promise(r=>requestAnimationFrame(r));
      }
      await wait(()=>read().melodyGlobal?.beatsPerBar===3,'save contextual fields');
      for (const knob of globals.querySelectorAll('vc-compact-knob')) {
        const label=knob.querySelector('vc-control-heading').getBoundingClientRect(), face=knob.querySelector('.knob-face').getBoundingClientRect();
        ensure(Math.abs((label.left+label.right-face.left-face.right)/2)<1,'centered global knob label');
      }
      for (const row of globals.querySelectorAll('.setting-row')) {
        const copy=row.querySelector('.setting-copy').getBoundingClientRect(), controls=row.querySelector('.setting-controls').getBoundingClientRect();
        ensure(copy.right<=controls.left,'descriptions stay left of controls');
        ensure(controls.right<=globals.getBoundingClientRect().right+1,'controls fit settings width');
      }
      const weights=globals.querySelector('.block-weights');
      ensure(getComputedStyle(weights).columnGap==='4px','compact catalogue spacing');
      ensure(!globals.querySelector('fieldset')&&!globals.textContent.includes('PROJECT SETTINGS'),'plain global layout');
      ensure(!globals.querySelector('[aria-label="Melody pattern flip"]'),'global flip removed');
      ensure(!globals.querySelector('[aria-label="Melody Low MIDI"]'),'range removed from globals');
      dialog.querySelector('[aria-label="Close settings"]').click();
      const inspectorButton=button('Open inspector'); if(inspectorButton) inspectorButton.click();
      for(const [label,value] of [['Melody High MIDI','81'],['Melody Low MIDI','62'],['Melody Tension','50']]) {
        const input=await wait(()=>document.querySelector('vc-part-settings-editor [aria-label="'+label+'"]'),'track '+label);
        await change(input,value); await new Promise(r=>requestAnimationFrame(r));
      }
      await wait(()=>read().tracks.filter(t=>t.role==='melody').every(t=>t.generatorSettings.rangeMin===62&&t.generatorSettings.rangeMax===81&&t.generatorSettings.tension===50),'track fields saved');
      const phrasing=await wait(()=>document.querySelector('.part-settings-content [data-control-key="ending"]'),'track phrasing stack');
      const selects=[...phrasing.querySelectorAll('vc-select')];
      ensure(selects.length===3,'three phrasing choices share one control');
      ensure(selects.every(s=>getComputedStyle(s).appearance==='none'),'phrasing choices are arrowless');
      ensure(phrasing.querySelector('.control-name')?.textContent==='SENTENCE','phrasing shares SENTENCE title');
      ensure(selects[0].getBoundingClientRect().top===selects[1].getBoundingClientRect().top&&selects[1].getBoundingClientRect().bottom<=selects[2].getBoundingClientRect().top,'ending and style share the first row with form below');
      for(const [label,value] of [['Melody Style','JAZZ'],['Melody Form','QUESTION_ANSWER'],['Melody Ending','CLOSED']]) {
        await change(phrasing.querySelector('[aria-label="'+label+'"]'),value);await new Promise(r=>requestAnimationFrame(r));
      }
      await wait(()=>read().tracks.filter(t=>t.role==='melody').every(t=>t.generatorSettings.style==='JAZZ'&&t.generatorSettings.form==='QUESTION_ANSWER'&&t.generatorSettings.ending==='CLOSED'),'save track phrasing');
      ensure(!read().melodyGlobal.style&&!read().melodyGlobal.ending&&!read().melodyGlobal.form,'track phrasing does not write globals');
      button('Edit quick settings').click();
      await wait(()=>document.querySelector('vc-part-settings-editor [data-control-key="patternFlexible"]'),'dormant controls while editing placement');
      button('Save quick settings').click();
      await wait(()=>!document.querySelector('vc-part-settings-editor [data-control-key="patternFlexible"]'),'hide dormant controls after placement');
      dialog=await settings('Melody');
      toggle=await wait(()=>dialog.querySelector('[aria-label="Contextual Melody"]'),'disable contextual');toggle.click();
      await wait(()=>read().melodyGlobal?.contextualMelody===false,'save disabled contextual');
      dialog.querySelector('[aria-label="Close settings"]').click();
      await wait(()=>document.querySelector('vc-part-settings-editor [aria-label="TON%"]'),'restore track block controls');
      ensure(!document.querySelector('vc-melody-global-settings [aria-label="Melody Low MIDI"]'),'hide contextual controls');
      dialog=await settings('Harmony');
      generation=await wait(()=>dialog.querySelector('[aria-label="Harmony generation"]'),'Random selector');await change(generation,'RANDOM');
      await wait(()=>read().harmonySettings?.generation==='RANDOM','save Random');
      dialog.querySelector('[aria-label="Close settings"]').click();button('Generate chord progression').click();
      await wait(()=>read().progression.length===4,'Random generation');
      ensure(read().tracks.filter(t=>t.role==='melody').every(t=>t.generatorSettings.rangeMin===62),'hidden track settings retained');
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
