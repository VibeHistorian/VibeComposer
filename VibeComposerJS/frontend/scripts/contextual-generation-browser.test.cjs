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
      const all = document.querySelector('.all-role-group');
      const groups = document.querySelector('.track-browser > .track-groups');
      ensure(all && groups?.nextElementSibling === all && all.parentElement.lastElementChild === all, 'all-role panel follows the scrollable track groups');
      ensure(document.querySelectorAll('.all-role-group').length === 1, 'one all-role panel');
      for (const label of ['Reroll all tracks', 'Reroll all instruments']) {
        const action = button(label);
        ensure(action && !action.hasAttribute('aria-pressed'), label + ' is an action');
        action.click();
      }
      const heading = all.querySelector('.group-heading');
      ensure(all.querySelector('.all-role-icon').textContent.includes('🛠'), 'tool symbol');
      const level = all.querySelector('.master-volume');
      const face = level.querySelector('.knob-face').getBoundingClientRect();
      const value = level.querySelector('.knob-value').getBoundingClientRect();
      ensure(value.left >= face.right && Math.abs(value.top + value.height / 2 - face.top - face.height / 2) < 2, 'master readout beside knob');
      const roleHeading = document.querySelector('.track-group.melody .group-heading').getBoundingClientRect();
      ensure(Math.abs(heading.getBoundingClientRect().height - roleHeading.height) < 2, 'master header matches role height');
      ensure(button('Reroll all tracks').getBoundingClientRect().left > level.getBoundingClientRect().right, 'reroll actions to right of master');
      // Turn off both Melody permissions, then exercise each Shift override through the DOM.
      for (const label of ['On Compose', 'Randomize instruments']) {
        const toggle = document.querySelector('.track-group.melody [aria-label="' + label + '"]');
        if (toggle.getAttribute('aria-pressed') === 'true') toggle.click();
      }
      await wait(() => read()?.trackGenerationPolicies?.melody?.onCompose === false && read()?.instrumentGenerationPolicies?.melody?.randomize === false, 'disabled role policies');
      for (const label of ['Reroll all tracks', 'Reroll all instruments']) {
        const beforeCount = read().trackRerollCounts?.melody ?? 0;
        button(label).click();
        await new Promise(r => setTimeout(r, 250));
        ensure((read().trackRerollCounts?.melody ?? 0) === beforeCount, 'global reroll skips disabled melody');
        button(label).dispatchEvent(new MouseEvent('click', { bubbles: true, shiftKey: true }));
        await wait(() => (read().trackRerollCounts?.melody ?? 0) > beforeCount, 'Shift overrides role toggle');
      }
      const master = all.querySelector('vc-compact-knob input');
      ensure(master, 'master knob');
      master.value = '50'; master.dispatchEvent(new Event('input', { bubbles: true }));
      master.dispatchEvent(new Event('change', { bubbles: true }));
      await wait(() => read()?.masterVolumePercent === 50, 'master persisted');
      button('Expand all-role settings').click();
      await wait(() => all.querySelector('.all-role-settings'), 'expanded all-role heading');
      button('All roles preset manager').click();
      const presets = await wait(() => document.querySelector('vc-track-preset-dialog'), 'multi-role presets');
      const chooser = presets.querySelector('vc-select');
      const styles = await selectOptions(chooser);
      const samba = styles.find(option => option.value.startsWith('builtin:Samba'));
      ensure(samba, 'Samba all-role factory preset');
      await change(chooser, samba.value);
      button('Load preset').click();
      await wait(() => presets.textContent.includes('Preset loaded'), 'Samba loaded once');
      ensure(new Set(read().tracks.map(track => track.role)).size === 5, 'all roles loaded');
      presets.querySelector('[aria-label="Close preset manager"]').click();
      const bounds = all.getBoundingClientRect();
      ensure([...all.querySelectorAll('button, vc-compact-knob')].every(control => control.getBoundingClientRect().right <= bounds.right + 1), 'all-role controls fit');
      let dialog=await settings('Harmony');
      let generation=await wait(()=>dialog.querySelector('[aria-label="Harmony generation"]'),'harmony generation');
      ensure(generation.getAttribute("value")==='CURRENT','Legacy default');
      ensure((await selectOptions(generation)).map(o=>o.value).join(',')==='CURRENT,FUNCTIONAL,FIFTHS,POP_LOOP,MODAL,RANDOM','harmony choices');
      await change(generation,'FUNCTIONAL');await wait(()=>read()?.harmonySettings?.generation==='FUNCTIONAL','save harmony algorithm');
      dialog.querySelector('[aria-label="Close settings"]').click();
      button('Generate chord progression').click();
      await wait(()=>read().progressionChords?.length===4,'functional generation');
      document.querySelector('.track-group.melody .group-select').click();
      ensure(!document.querySelector('#part-inspector vc-melody-global-settings'),'globals removed from inspector');
      ensure(!document.querySelector('vc-part-settings-editor [aria-label="TONIC%"]'),'contextual controls by default');
      let globals;
      dialog=await settings('Melody');
      let toggle=await wait(()=>dialog.querySelector('[aria-label="Contextual Melody"]'),'contextual toggle');
      ensure(toggle.checked,'contextual default on');
      globals=await wait(()=>document.querySelector('vc-melody-global-settings [aria-label="Phrase Bars"]')?.closest('vc-melody-global-settings'),'contextual fields');
      ensure(!globals.querySelector('[aria-label="TONIC%"]')&&!globals.querySelector('[aria-label="Block target mode"]'),'hide obsolete globals');
      ensure(!document.querySelector('vc-part-settings-editor [data-control-key="patternFlexible"]'),'hide Flex');
      ensure(!globals.querySelector('[aria-label="Melody Ending"]')&&!globals.querySelector('[aria-label="Melody Style"]')&&!globals.querySelector('[aria-label="Melody Form"]'),'phrasing removed from global melody');
      for(const [label,value] of [['Phrase Bars','3'],['Beats per Bar','3']]) {
        await change(globals.querySelector('[aria-label="'+label+'"]'),value);await new Promise(r=>requestAnimationFrame(r));
      }
      await wait(()=>read().melodyGlobal?.beatsPerBar===3,'save contextual fields');
      for (const row of globals.querySelectorAll('.setting-row')) {
        const copy=row.querySelector('.setting-copy').getBoundingClientRect(), controls=row.querySelector('.setting-controls').getBoundingClientRect();
        ensure(copy.right<=controls.left,'descriptions stay left of controls');
        ensure(controls.right<=globals.getBoundingClientRect().right+1,'controls fit settings width');
      }
      ensure(!globals.querySelector('[aria-label="Melody pattern flip"]'),'global flip removed');
      ensure(!globals.querySelector('[aria-label="Melody Low MIDI"]'),'range removed from globals');
      dialog.querySelector('[aria-label="Close settings"]').click();
      const inspectorButton=button('Open inspector'); if(inspectorButton) inspectorButton.click();
      const melodyPattern=await wait(()=>document.querySelector('vc-part-settings-editor vc-menu-select[aria-label="PATTERN"]'),'melody pattern');
      const rhythmGradients=['Syncopation', 'Rhythmic Reuse', 'Entry Placement', 'Rhythmic Grouping', 'Motif Length', 'Variation Location']
        .map(label=>'vc-part-settings-editor [aria-label="'+label+'"]').join(', ');
      const performanceGradients='vc-part-settings-editor [aria-label="Articulation Contrast"], vc-part-settings-editor [aria-label="Velocity Contrast"]';
      const accent='vc-part-settings-editor [aria-label="Accent"]';
      ensure(!document.querySelector(rhythmGradients),'authored durations hide rhythm gradients');
      const pitchReuse=await wait(()=>document.querySelector('vc-part-settings-editor [aria-label="Pitch Reuse"]'),'pitch reuse with authored rhythm');
      await change(pitchReuse,'71');
      await wait(()=>read().tracks.filter(t=>t.role==='melody').every(t=>t.generatorSettings.pitchReuse===71),'pitch reuse saved');
      ensure(document.querySelectorAll(performanceGradients).length===2&&!document.querySelector(accent),'contextual performance controls also apply to authored rhythms');
      await change(melodyPattern,'melody1');
      await wait(()=>read().tracks.filter(t=>t.role==='melody').every(t=>t.generatorSettings.rhythm==='melody1'),'enable generated melody rhythm');
      for(const [label,value] of [['Entry Placement','65'],['Rhythmic Grouping','83']]) {
        await change(await wait(()=>document.querySelector('vc-part-settings-editor [aria-label="'+label+'"]'),'generated rhythm '+label),value);
        await new Promise(r=>requestAnimationFrame(r));
      }
      await wait(()=>read().tracks.filter(t=>t.role==='melody').every(t=>t.generatorSettings.entryPlacement===65&&t.generatorSettings.rhythmicGrouping===83),'entry and grouping saved');
      for(const [label,value] of [['Melody High MIDI','81'],['Melody Low MIDI','62'],['Melody Tension','50'],['Syncopation','-62'],['Rhythmic Reuse','74'],['Articulation Contrast','-68'],['Velocity Contrast','-79']]) {
        const input=await wait(()=>document.querySelector('vc-part-settings-editor [aria-label="'+label+'"]'),'track '+label);
        await change(input,value); await new Promise(r=>requestAnimationFrame(r));
      }
      await wait(()=>read().tracks.filter(t=>t.role==='melody').every(t=>t.generatorSettings.rangeMin===62&&t.generatorSettings.rangeMax===81&&t.generatorSettings.tension===50),'track fields saved');
      await wait(()=>read().tracks.filter(t=>t.role==='melody').every(t=>t.generatorSettings.syncopation===-62&&t.generatorSettings.rhythmicReuse===74),'rhythm gradients saved');
      for(const [label,value] of [['Motif Length','1'],['Variation Location','-73']]) {
        await change(await wait(()=>document.querySelector('vc-part-settings-editor [aria-label="'+label+'"]'),'motif '+label),value);
        await new Promise(r=>requestAnimationFrame(r));
      }
      await wait(()=>read().tracks.filter(t=>t.role==='melody').every(t=>t.generatorSettings.motifLength===1&&t.generatorSettings.variationLocation===-73),'motif controls saved');
      await wait(()=>read().tracks.filter(t=>t.role==='melody').every(t=>t.generatorSettings.articulationContrast===-68&&t.generatorSettings.velocityContrast===-79),'performance gradients saved');
      const phrasing=await wait(()=>document.querySelector('.part-settings-content [data-control-key="ending"]'),'track phrasing stack');
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
      await wait(()=>document.querySelector('vc-part-settings-editor [aria-label="TONIC%"]'),'restore track block controls');
      ensure(!document.querySelector(rhythmGradients),'hide rhythm gradients in block mode');
      ensure(!document.querySelector('vc-part-settings-editor [aria-label="Pitch Reuse"]'),'hide pitch reuse in block mode');
      ensure(!document.querySelector(performanceGradients)&&document.querySelector(accent),'block mode restores Accent');
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


for (const width of [1440,780]) test(`whole arrangement toolbar reroll and undo at ${width}px`, { skip: !browser }, async () => {
  const root = requireProductionBuild(), index = fs.readFileSync(path.join(root,'index.html'),'utf8');
  const script = `<script>(async () => {
    const wait=async(read)=>{for(let i=0;i<200;i++){if(read())return;await new Promise(r=>setTimeout(r,30));}throw new Error('arrangement reroll timed out');};
    const read=()=>JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));
    const button=label=>document.querySelector('[aria-label="'+label+'"]');
    try {
      await wait(()=>innerWidth===${width}&&button('Arrangement Manual'));
      await wait(read);
      button('Arrangement Manual').click();
      await wait(()=>read().arrangementManual===true);
      const before=read();
      const snapshot=project=>JSON.stringify(project.arrangement.map(({id,type,measures,trackPresence})=>({id,type,measures,trackPresence})));
      const structure=project=>JSON.stringify(project.arrangement.map(({trackPresence,...section})=>section));
      button('Reroll arrangement').click();
      await wait(()=>structure(read())!==structure(before));
      const rolled=read();
      if(rolled.seed!==before.seed||JSON.stringify(rolled.tracks)!==JSON.stringify(before.tracks))throw new Error('reroll changed seed or tracks');
      if(!rolled.arrangement.every(section=>Object.keys(section.trackPresence).length===rolled.tracks.length))throw new Error('incomplete presence');
      await new Promise(done=>requestAnimationFrame(()=>requestAnimationFrame(done)));
      await wait(()=>!button('Undo').disabled);
      button('Undo').click();
      await wait(()=>snapshot(read())===snapshot(before));
      await new Promise(done=>requestAnimationFrame(()=>requestAnimationFrame(done)));
      await wait(()=>!button('Redo').disabled);
      button('Redo').click();
      await wait(()=>snapshot(read())===snapshot(rolled));
      await fetch('/result',{method:'POST',body:JSON.stringify({ok:true})});
    } catch(error) {await fetch('/result',{method:'POST',body:JSON.stringify({error:error.message,stack:error.stack})});}
  })();</script>`;
  let resolve, reject;
  const result = new Promise((done,fail)=>{resolve=done;reject=fail;});
  result.catch(() => {}); // CDP setup can fail before the result is awaited.
  const server = createBrowserServer({ root, html: index.replace('</body>', script + '</body>'), resolve, reject });
  await listen(server);
  const profile=fs.mkdtempSync(path.resolve(__dirname,'../.angular/arrangement-reroll-browser-'));
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

for (const width of [1440,780]) test(`role reseeding controls at ${width}px`, { skip: !browser }, async () => {
  const root = requireProductionBuild(), index = fs.readFileSync(path.join(root,'index.html'),'utf8');
  const script = `<script>(async () => {
    const wait=async(read)=>{for(let i=0;i<200;i++){if(read())return;await new Promise(r=>setTimeout(r,30));}throw new Error('arrangement reroll timed out');};
    const read=()=>JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));
    const button=label=>document.querySelector('[aria-label="'+label+'"]');
    try {
      await wait(()=>innerWidth===${width}&&document.querySelector('.track-group.melody'));
      const ensure=(value,label)=>{if(!value)throw new Error(label);};
      ensure(!button('Regenerate'),'global regenerate removed');
      for(const role of ['melody','bass','chords','arpeggio','drums']) {
        const group=document.querySelector('.track-group.'+role);
        if(!group.querySelector('.seed-controls')) group.querySelector('.group-select').click();
        await wait(()=>group.querySelector('.seed-controls'));
        const controls=group.querySelector('.seed-controls'), checkbox=controls.querySelector('input'), reseed=controls.querySelector('button'), same=controls.querySelector('[aria-label="Same seed"]');
        ensure(checkbox.checked===(role==='melody'),role+' default checkbox');
        ensure(reseed.disabled===(role!=='melody')&&same.disabled===(role!=='melody'),role+' disabled controls');
        ensure(same.getAttribute('aria-pressed')==='false',role+' independent default');
        ensure(!group.querySelector('[aria-label="On Regenerate"]')&&!group.querySelector('[aria-label="Reroll seeds"]'),role+' old controls removed');
        if(!checkbox.checked) { checkbox.click(); await wait(()=>!reseed.disabled); }
        const before=read().tracks.find(track=>track.role===role).generatorSettings.patternSeed;
        reseed.click();
        await wait(()=>read().tracks.find(track=>track.role===role).generatorSettings.patternSeed!==before);
        same.click(); await wait(()=>same.getAttribute('aria-pressed')==='true');
        checkbox.click(); await wait(()=>reseed.disabled&&same.disabled);
        const row=controls.parentElement.getBoundingClientRect(), bounds=controls.getBoundingClientRect();
        ensure(bounds.right<=row.right+1&&bounds.left>=row.left-1,role+' fits pool row');
      }
      await fetch('/result',{method:'POST',body:JSON.stringify({ok:true})});
    } catch(error) {await fetch('/result',{method:'POST',body:JSON.stringify({error:error.message,stack:error.stack})});}
  })();</script>`;
  let resolve, reject;
  const result = new Promise((done,fail)=>{resolve=done;reject=fail;});
  result.catch(() => {}); // CDP setup can fail before the result is awaited.
  const server = createBrowserServer({ root, html: index.replace('</body>', script + '</body>'), resolve, reject });
  await listen(server);
  const profile=fs.mkdtempSync(path.resolve(__dirname,'../.angular/arrangement-reroll-browser-'));
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
