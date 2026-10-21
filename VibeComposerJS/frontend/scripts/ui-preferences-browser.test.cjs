const { findBrowser, connectToPage, createBrowserServer, listen, launchBrowser, closeBrowser, watchBrowser, requireProductionBuild } = require('./support/browser-harness.cjs');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const browser = findBrowser();

// Stay tall enough to exercise the quick-settings panel rather than its inspector layout.
for (const width of [1440, 1400, 780]) test(`quick-settings placement and UI preference transfer at ${width}px`, { skip: !browser }, async () => {
  const root = requireProductionBuild();
  const index = fs.readFileSync(path.join(root, 'index.html'), 'utf8');
  const script = `<script>(async () => {
    const wait = async (read, label) => {
      for (let i=0;i<200;i++) { const value=read(); if(value) return value; await new Promise(r=>setTimeout(r,30)); }
      throw new Error('Timed out: '+label);
    };
    const ensure = (value, label) => { if(!value) throw new Error(label); };
    const button = label => [...document.querySelectorAll('button')].find(b=>b.textContent.trim()===label || b.getAttribute('aria-label')===label);
    const key = 'vibecomposer.ui-preferences.v1';
    const quick = () => document.querySelector('.part-settings-content vc-part-settings-editor');
    const inspector = () => document.querySelector('#part-inspector vc-part-settings-editor');
    const control = (editor, key) => editor.querySelector('[data-control-key="'+key+'"]');
    try {
      await wait(()=>innerWidth===${width},'viewport');
      await wait(()=>button('Edit quick settings'),'workspace');
      for (const [family, weights] of [['DM Sans', [400, 500, 600, 700]], ['DM Mono', [400, 500]]]) {
        for (const weight of weights) {
          const faces = await document.fonts.load(weight + ' 12px "' + family + '"', 'Árvíztűrő tükörfúrógép');
          ensure(faces.length > 0 && faces.every(face => face.status === 'loaded'), 'bundled font loaded: ' + family + ' ' + weight);
        }
      }
      if (sessionStorage.getItem('ui-preferences-reloaded')) {
        ensure(getComputedStyle(document.documentElement).getPropertyValue('--accent-primary').trim()==='#dd88ee','main theme restored after reload');
        ensure(getComputedStyle(document.querySelector('.context-inspector')).backgroundColor==='rgb(33, 24, 49)','inspector background restored after reload');
        ensure(getComputedStyle(document.querySelector('.track-group')).backgroundColor==='rgb(80, 64, 96)','role group background restored after reload');
        ensure(getComputedStyle(document.documentElement).getPropertyValue('--role-bass').trim()==='#8855cc', 'role color restored after reload');
        ensure(getComputedStyle(document.documentElement).getPropertyValue('--font-ui').trim()==='Verdana, sans-serif', 'font restored after reload');
        ensure(control(quick(),'feedbackCount'), 'persisted placement after reload');
        ensure(!control(quick(),'transpose'), 'removed quick control after reload');
        button('Settings').click();
        (await wait(()=>button('Appearance General'),'appearance after reload')).click();
        await wait(()=>document.querySelector('[aria-label="Interface text and controls font"]'),'font selectors after reload');
        const checkFonts=()=>{
          ensure(document.querySelector('[aria-label="Interface text and controls font"]').value==='Verdana, sans-serif','saved interface font selected');
          ensure(document.querySelector('[aria-label="Headings and track groups font"]').value==='Georgia, serif','saved heading font selected');
          ensure(document.querySelector('[aria-label="Numbers and music labels font"]').value==="'Courier New', monospace",'saved numeric font selected');
        };
        checkFonts();
        button('Project').click();
        await wait(()=>!document.querySelector('[aria-label="Interface text and controls font"]'),'leave appearance');
        const fields = await wait(()=>document.querySelector('.export-name-fields'),'export filename fields');
        ensure(fields.querySelectorAll('button').length===5,'five export fields');
        const preview = () => document.querySelector('.export-name-preview').textContent.trim();
        const seedToggle = [...fields.querySelectorAll('button')].find(b=>b.textContent.trim()==='Seed');
        ensure(seedToggle.getAttribute('aria-pressed')==='true','export naming restored after reload');
        const originalPreview = preview();
        seedToggle.click();
        await wait(()=>preview()!==originalPreview,'filename preview updates');
        ensure(!JSON.parse(localStorage.getItem(key)).exportNameFields.includes('seed'),'filename toggle saved');
        seedToggle.click();
        await wait(()=>seedToggle.getAttribute('aria-pressed')==='true','restore seed field');
        for (const toggle of fields.querySelectorAll('button')) toggle.click();
        await wait(()=>JSON.parse(localStorage.getItem(key)).exportNameFields.length===0,'disable all export fields');
        await wait(()=>preview().endsWith('.vibe.json') && !/^\\d{8}_/.test(preview()),'project name only preview');
        for (const toggle of fields.querySelectorAll('button')) toggle.click();
        await wait(()=>JSON.parse(localStorage.getItem(key)).exportNameFields.length===5,'restore export fields');
        button('Appearance General').click();
        await wait(()=>document.querySelector('[aria-label="Interface text and controls font"]'),'return to appearance');
        checkFonts();
        document.querySelector('[aria-label="Close settings"]').click();
        button('Settings').click();
        await wait(()=>document.querySelector('#appearance-title'),'Appearance reopens by default');
        ensure(button('Preview').getAttribute('aria-pressed')==='false','preview resets when reopened');
        ensure(!document.querySelector('.cdk-overlay-backdrop.appearance-preview'),'reopened dialog dims workspace');
        ensure(button('All').getAttribute('aria-pressed')==='true','hue role inclusion restored after reload');
        await wait(()=>document.querySelector('[aria-label="Interface text and controls font"]'),'reopened font selectors');
        checkFonts();
        document.querySelector('[aria-label="Close settings"]').click();
      } else {
        ensure([...document.querySelectorAll('.group-policy-toggle')].every(b=>b.getAttribute('aria-pressed')==='true'), 'role Compose defaults on');
        const tempoStar = button('Randomize BPM on Compose');
        const transposeStar = button('Randomize Transpose on Compose');
        ensure(tempoStar?.getAttribute('aria-pressed')==='true' && transposeStar?.getAttribute('aria-pressed')==='true', 'global Compose stars default on');
        const tempo = document.querySelector('input[aria-label="Tempo in beats per minute"]').value;
        const transpose = document.querySelector('input[aria-label="Transpose in semitones"]').value;
        tempoStar.click(); transposeStar.click();
        await wait(()=>tempoStar.getAttribute('aria-pressed')==='false','star disabled');
        const seedInput = document.querySelector('input[aria-label="Generation seed"]');
        seedInput.value='0'; seedInput.dispatchEvent(new Event('change',{bubbles:true}));
        button('Compose').click();
        await wait(()=>{
          const generated = JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));
          return generated?.seed!=='0' && document.querySelector('.transport-project span').textContent.trim()==='ID '+generated?.seed;
        },'generated ID summary');
        ensure(seedInput.value==='0','automatic seed input remains zero');
        ensure(document.querySelector('input[aria-label="Tempo in beats per minute"]').value===tempo,'disabled BPM star preserves tempo');
        ensure(document.querySelector('input[aria-label="Transpose in semitones"]').value===transpose,'disabled transpose star preserves transpose');
        const name = document.querySelector('textarea[aria-label="Project name"]');
        ensure(document.querySelector('.transport-project').getBoundingClientRect().width===140,'compact project name width');
        ensure(name.rows===2 && getComputedStyle(name).textAlign==='center','centered two-line project name');
        const summary = document.querySelector('.transport-project span');
        const generatedProject = JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));
        ensure(summary.textContent.trim()==='ID '+generatedProject.seed,'ID hint shows actual generation seed');
        ensure(summary.title.endsWith(generatedProject.tempoBpm+' BPM | SEED '+generatedProject.seed),'seed hint retains full song tooltip');
        const box = label => button(label).getBoundingClientRect();
        for (const label of ['History', 'Settings']) {
          ensure(button(label).textContent.trim()===label, label+' uses a text label');
          ensure(button(label).scrollWidth<=button(label).clientWidth, label+' text fits');
        }
        const midi = document.querySelector('.midi-port-button').getBoundingClientRect();
        ensure(box('Settings').top > midi.top && box('Settings').width===midi.width,'MIDI and settings stack with matching widths');
        ensure(box('Undo').top===box('Redo').top && box('History').top>box('Undo').top,'history below undo and redo');
        ensure(box('History').width===box('Redo').right-box('Undo').left,'history spans undo and redo');
        const files = [...document.querySelectorAll('.project-file-tools > button')].map(element => element.getBoundingClientRect());
        ensure(files[1].top>files[0].top && files[0].width===files[1].width,'matching stacked file actions');
        const dock = document.querySelector('.transport-dock').getBoundingClientRect();
        ensure([...document.querySelectorAll('.transport-tools > *')].every(element => {
          const box = element.getBoundingClientRect();
          return box.left >= dock.left && box.right <= dock.right;
        }), 'project actions fit inside transport');
        const seed=await wait(()=>document.querySelector('[aria-label="Generation seed"]'),'seed');
        seed.value='123'; seed.dispatchEvent(new Event('change',{bubbles:true}));
        await wait(()=>sessionStorage.getItem('vibecomposer.project.v12'),'project saved');
        const before=sessionStorage.getItem('vibecomposer.project.v12');
        button('Edit quick settings').click();
        await wait(()=>button('Save quick settings'),'edit mode');
        ensure(document.querySelector('.workspace-canvas.quick-layout-editing'),'focused workspace');
        const target=control(quick(),'transpose').querySelector('.placement-target');
        target.focus();
        ensure(getComputedStyle(target).boxShadow!=='none','focus glow');
        ensure(control(quick(),'transpose').querySelector('.control-content').inert,'parameter subtree inert');
        target.click();
        await wait(()=>control(inspector(),'transpose'),'move quick to inspector');
        control(inspector(),'feedbackCount').querySelector('.placement-target').click();
        await wait(()=>control(quick(),'feedbackCount'),'move inspector to quick');
        ensure(sessionStorage.getItem('vibecomposer.project.v12')===before,'placement changed musical project');
        ensure(!localStorage.getItem(key),'draft persisted before save');
        button('Save quick settings').click();
        await wait(()=>button('Edit quick settings'),'save mode');
        ensure(JSON.parse(localStorage.getItem(key)).quickSettings.bass.includes('feedbackCount'),'saved preference');
        const areas=quick().querySelectorAll('.part-control-area');
        ensure([...areas].every(area=>getComputedStyle(area).display==='contents'),'quick categories must flow');
        ensure([...inspector().querySelectorAll('.part-control-area')].every(area=>getComputedStyle(area).display!=='contents'),'inspector category areas');
        const velocities=[...quick().querySelectorAll('[data-control-key]')].map(c=>c.dataset.controlKey).filter(k=>k.startsWith('velocity'));
        ensure(velocities.join(',')==='velocityMin,velocityMax','fixed velocity order');
        document.querySelector('.track-group.bass .group-select').click();
        await wait(()=>document.querySelector('.track-group.bass.group-selected'),'selected bass role');
        button('Settings').click();
        const dialog=await wait(()=>document.querySelector('vc-settings-dialog'),'settings dialog');
        await wait(()=>dialog.querySelector('#appearance-title'),'Appearance opens by default');
        ensure(dialog.querySelector('#appearance-title'),'Appearance opens by default');
        const beforeRandomize = JSON.parse(localStorage.getItem(key));
        const projectBeforeRandomize = sessionStorage.getItem('vibecomposer.project.v12');
        button('Randomize colors').click();
        const randomized = await wait(()=>{
          const appearance = JSON.parse(localStorage.getItem(key)).appearance;
          return appearance.themeColors.background !== '#080e16' && appearance;
        },'random palette saved');
        ensure(new Set(Object.values(randomized.roleColors)).size===5,'five randomized track colors');
        ensure(JSON.stringify(randomized.fonts)===JSON.stringify(beforeRandomize.appearance.fonts),'randomizer preserves fonts');
        ensure(Object.values(randomized.colorAdjustments).every(value=>value===0),'randomizer neutralizes adjustments');
        const randomStyle = getComputedStyle(document.documentElement);
        ensure(randomStyle.getPropertyValue('--accent-primary').trim()===randomized.themeColors.accent,'random theme applied immediately');
        for (const [role,color] of Object.entries(randomized.roleColors)) {
          ensure(randomStyle.getPropertyValue('--role-'+role).trim()===color,'random track color applied: '+role);
        }
        button('Randomize colors').click();
        await wait(()=>JSON.stringify(JSON.parse(localStorage.getItem(key)).appearance.themeColors)!==JSON.stringify(randomized.themeColors),'reroll creates another palette');
        ensure(sessionStorage.getItem('vibecomposer.project.v12')===projectBeforeRandomize,'randomizer preserves musical project');
        button('Reset appearance to defaults').click();
        await wait(()=>getComputedStyle(document.documentElement).getPropertyValue('--accent-primary').trim()==='#32d7f4','reset randomized palette');
        button('Appearance Score').click();
        const ghostToggle = await wait(()=>document.querySelector('[aria-label="Ghost notes from other tracks"]'),'ghost preference');
        ensure(ghostToggle.checked, 'ghost notes default on');
        ghostToggle.click();
        await wait(()=>JSON.parse(localStorage.getItem(key)).scoreGhostNotes===false,'ghost preference off persisted');
        ghostToggle.click();
        await wait(()=>JSON.parse(localStorage.getItem(key)).scoreGhostNotes===true,'ghost preference on persisted');
        button('Appearance General').click();
        await wait(()=>dialog.querySelector('#appearance-title'),'return to general appearance');
        const backdrop=()=>document.querySelector('.cdk-overlay-backdrop');
        ensure(button('Preview').getAttribute('aria-pressed')==='false','preview defaults off');
        button('Preview').click();
        await wait(()=>getComputedStyle(backdrop()).backgroundColor==='rgba(0, 0, 0, 0)','preview clears dimming');
        button('Behavior General').click();
        const restart=await wait(()=>button('Restart playback on Compose'),'restart Compose preference');
        ensure(restart.getAttribute('aria-pressed')==='true','restart defaults on');
        restart.click();
        await wait(()=>restart.getAttribute('aria-pressed')==='false','restart toggles off');
        ensure(JSON.parse(localStorage.getItem(key)).restartPlaybackOnCompose===false,'restart preference persisted');
        const poolToggle=button('Limit instrument options to selected pool');
        const bassInstrument=()=>document.querySelector('.track-group.bass select.track-instrument');
        ensure(poolToggle?.getAttribute('aria-pressed')==='false','instrument pool filtering defaults off');
        ensure(bassInstrument().options.length===128,'all GM instruments available by default');
        const selectedProgram=bassInstrument().value;
        const projectBeforePoolToggle=sessionStorage.getItem('vibecomposer.project.v12');
        poolToggle.click();
        const bassPool=[0,1,4,53,54,73,74,75,82,85,91,102];
        await wait(()=>bassInstrument().options.length<128,'instrument options filtered immediately');
        const expected=[...new Set([...bassPool,Number(selectedProgram)])].sort((a,b)=>a-b);
        ensure(JSON.stringify([...bassInstrument().options].map(option=>Number(option.value)))===JSON.stringify(expected),'bass pool plus selected instrument');
        ensure(bassInstrument().value===selectedProgram,'filter retains selected instrument');
        ensure(sessionStorage.getItem('vibecomposer.project.v12')===projectBeforePoolToggle,'filter does not edit musical project');
        ensure(JSON.parse(localStorage.getItem(key)).limitInstrumentsToPool===true,'pool filter preference persisted');
        poolToggle.click();
        await wait(()=>bassInstrument().options.length===128,'toggle restores full instrument catalog');
        await wait(()=>!backdrop().classList.contains('appearance-preview'),'preview only applies in Appearance');
        ensure(getComputedStyle(backdrop()).backgroundColor!=='rgba(0, 0, 0, 0)','other categories dim workspace');
        button('Appearance General').click();
        await wait(()=>backdrop().classList.contains('appearance-preview'),'returning to Appearance restores preview');
        button('Preview').click();
        await wait(()=>!backdrop().classList.contains('appearance-preview'),'preview can be disabled');
        const colorInput=await wait(()=>dialog.querySelector('[aria-label="Bass color"]'),'appearance colors');
        ensure(dialog.querySelectorAll('input[type="color"]').length===12,'seven theme and five role colors');
        const style=()=>getComputedStyle(document.documentElement);
        const originalSurface=style().getPropertyValue('--surface-base');
        const surfaceInput=dialog.querySelector('[aria-label="Surface theme color"]');
        const textInput=dialog.querySelector('[aria-label="Text theme color"]');
        surfaceInput.value='#ffffff'; surfaceInput.dispatchEvent(new Event('input',{bubbles:true}));
        textInput.value='#000000'; textInput.dispatchEvent(new Event('input',{bubbles:true}));
        await wait(()=>style().getPropertyValue('--role-bass-header').trim()==='#cbd8e0','role header follows light surface');
        ensure(getComputedStyle(document.querySelector('.track-group.bass .group-heading')).backgroundColor==='rgb(203, 216, 224)','light role header rendered');
        ensure(style().getPropertyValue('--role-bass-cell-surface').trim()==='#e7edf1','arrangement cell follows light surface');
        ensure(style().getPropertyValue('--role-bass-text').trim()==='#1f2529','role text follows dark theme text');
        button('Reset appearance to defaults').click();
        await wait(()=>style().getPropertyValue('--surface-base')===originalSurface,'restore dark surface');
        const changeAdjustment=(label,value)=>{
          const knob=dialog.querySelector('vc-compact-knob input[aria-label="'+label+'"]');
          ensure(knob && knob.min==='-50' && knob.max==='50','adjustment knob range');
          knob.value=String(value); knob.dispatchEvent(new Event('change',{bubbles:true}));
        };
        changeAdjustment('Brightness',50);
        await wait(()=>style().getPropertyValue('--accent-primary').trim()==='#4bffff','brightness scales and clamps');
        ensure(style().getPropertyValue('--role-melody').trim()==='#ff6060','brightness adjusts role colors');
        changeAdjustment('Brightness',0);
        await wait(()=>style().getPropertyValue('--accent-primary').trim()==='#32d7f4','brightness returns to original');
        changeAdjustment('Hue shift',50);
        await wait(()=>style().getPropertyValue('--accent-primary').trim()!=='#32d7f4','hue shifts main theme');
        ensure(button('All').getAttribute('aria-pressed')==='false','role hue shift defaults off');
        ensure(style().getPropertyValue('--role-melody').trim()==='#d44040','default hue shift excludes role colors');
        button('All').click();
        await wait(()=>style().getPropertyValue('--role-melody').trim()==='#40d4d4','hue shifts role colors');
        button('All').click();
        await wait(()=>style().getPropertyValue('--role-melody').trim()==='#d44040','turning All off restores role hue');
        changeAdjustment('Brightness',50);
        await wait(()=>style().getPropertyValue('--role-melody').trim()==='#ff6060','brightness still applies with role hue excluded');
        changeAdjustment('Brightness',0);
        button('All').click();
        await wait(()=>style().getPropertyValue('--role-melody').trim()==='#40d4d4','All can be reenabled');
        changeAdjustment('Hue shift',0);
        changeAdjustment('Saturation',-50);
        await wait(()=>style().getPropertyValue('--role-melody').trim()==='#d48a8a','saturation adjusts role colors');
        ensure(JSON.parse(localStorage.getItem(key)).appearance.colorAdjustments.saturation===-50,'adjustment persisted');
        ensure(JSON.parse(localStorage.getItem(key)).appearance.roleColors.melody==='#d44040','chosen color retained');
        changeAdjustment('Saturation',0);
        for (const [label,value] of [['Background','#100818'],['Surface','#281838'],['Inspector background','#211831'],['Role groups background','#504060'],['Text','#e8dfef'],['Border','#78618a'],['Accent','#dd88ee']]) {
          const input=dialog.querySelector('[aria-label="'+label+' theme color"]');
          input.value=value; input.dispatchEvent(new Event('input',{bubbles:true}));
        }
        await wait(()=>style().getPropertyValue('--accent-primary').trim()==='#dd88ee','live main accent');
        ensure(style().getPropertyValue('--surface-base')!==originalSurface,'live main surface');
        ensure(style().getPropertyValue('--surface-canvas').trim()==='#100818','live background');
        ensure(getComputedStyle(document.querySelector('.context-inspector')).backgroundColor==='rgb(33, 24, 49)','live inspector background');
        ensure(getComputedStyle(document.querySelector('.track-group')).backgroundColor==='rgb(80, 64, 96)','live role group background');
        ensure(style().getPropertyValue('--surface-role-group-selected').trim()==='#3d2f4a','selected role settings blend toward the page background');
        ensure(/^#[0-9a-f]{6}$/i.test(style().getPropertyValue('--grid-measure').trim()),'concrete canvas palette');
        const previousBassSurface=style().getPropertyValue('--role-bass-surface');
        const previousDrumSurface=style().getPropertyValue('--role-drum-surface');
        colorInput.value='#8855cc'; colorInput.dispatchEvent(new Event('input',{bubbles:true}));
        await wait(()=>style().getPropertyValue('--role-bass').trim()==='#8855cc','live role color');
        ensure(style().getPropertyValue('--role-bass-surface')!==previousBassSurface,'derived bass surface');
        ensure(style().getPropertyValue('--role-drum-surface')===previousDrumSurface,'other role retained');
        const drums=dialog.querySelector('[aria-label="Drums color"]');
        drums.value='#44bb99'; drums.dispatchEvent(new Event('input',{bubbles:true}));
        await wait(()=>style().getPropertyValue('--role-drums').trim()==='#44bb99','live drums');
        ensure(style().getPropertyValue('--role-drum-surface')!==previousDrumSurface,'derived drum surface');
        ensure(style().getPropertyValue('--role-kick').trim()==='#44bb99','derived percussion colors');
        const font=dialog.querySelector('[aria-label="Interface text and controls font"]');
        font.value='Verdana, sans-serif'; font.dispatchEvent(new Event('change',{bubbles:true}));
        await wait(()=>getComputedStyle(dialog).fontFamily.includes('Verdana'),'live interface font');
        const headings=dialog.querySelector('[aria-label="Headings and track groups font"]');
        headings.value='Georgia, serif'; headings.dispatchEvent(new Event('change',{bubbles:true}));
        await wait(()=>getComputedStyle(dialog.querySelector('h2')).fontFamily.includes('Georgia'),'live heading font');
        const numbers=dialog.querySelector('[aria-label="Numbers and music labels font"]');
        numbers.value="'Courier New', monospace"; numbers.dispatchEvent(new Event('change',{bubbles:true}));
        await wait(()=>style().getPropertyValue('--font-mono').includes('Courier New'),'live numeric font');
        ensure(sessionStorage.getItem('vibecomposer.project.v12')===before,'appearance changed musical project');
        const footer=dialog.querySelector('.preferences-transfer');
        ensure(footer.querySelectorAll('button').length===2,'transfer buttons');
        let exported;
        const create=URL.createObjectURL;
        URL.createObjectURL=blob=>{exported=blob;return create(blob);};
        button('Export UI preferences').click();
        const json=await exported.text();
        ensure(JSON.parse(json).appearance.themeColors.accent==='#dd88ee','exported main theme');
        ensure(JSON.parse(json).appearance.themeColors.inspector==='#211831','exported inspector background');
        ensure(JSON.parse(json).appearance.themeColors.roleGroups==='#504060','exported role group background');
        ensure(JSON.parse(json).appearance.roleColors.bass==='#8855cc','exported appearance');
        ensure(JSON.parse(json).kind==='vibecomposer-ui-preferences','UI export kind');
        ensure(!JSON.parse(json).tracks,'project excluded from UI export');
        const fileInput=footer.querySelector('input');
        const upload=async text=>{
          const data=new DataTransfer(); data.items.add(new File([text],'preferences.json',{type:'application/json'}));
          fileInput.files=data.files; fileInput.dispatchEvent(new Event('change',{bubbles:true}));
        };
        await upload(before);
        await wait(()=>footer.textContent.includes('Invalid UI preferences'),'reject project as UI preferences');
        ensure(localStorage.getItem(key)===JSON.stringify(JSON.parse(json)),'invalid import changed preferences');
        await upload(json);
        await wait(()=>footer.textContent.includes('UI preferences imported.'),'valid UI import');
        const imported=localStorage.getItem(key);
        await upload(JSON.stringify({...JSON.parse(json),appearance:{...JSON.parse(json).appearance,roleColors:{...JSON.parse(json).appearance.roleColors,bass:'invalid'}}}));
        await wait(()=>footer.textContent.includes('Invalid UI preferences'),'invalid appearance rejected');
        ensure(localStorage.getItem(key)===imported,'invalid appearance import atomic');
        await upload(JSON.stringify({...JSON.parse(json),appearance:{...JSON.parse(json).appearance,themeColors:{...JSON.parse(json).appearance.themeColors,accent:'invalid'}}}));
        await wait(()=>footer.textContent.includes('Invalid UI preferences'),'invalid main theme rejected');
        ensure(localStorage.getItem(key)===imported,'invalid main theme import atomic');
        button('Reset appearance to defaults').click();
        await wait(()=>style().getPropertyValue('--role-bass').trim()==='#8ca9ba','reset appearance');
        ensure(style().getPropertyValue('--accent-primary').trim()==='#32d7f4','main theme reset');
        await wait(()=>button('All').getAttribute('aria-pressed')==='false','reset excludes role hues');
        await upload(json);
        await wait(()=>style().getPropertyValue('--role-bass').trim()==='#8855cc','import reapplies appearance');
        ensure(sessionStorage.getItem('vibecomposer.project.v12')===before,'UI import changed musical project');
        dialog.querySelector('[aria-label="Close settings"]').click();
        button('Edit quick settings').click();
        await wait(()=>button('Reset to default'),'reset button');
        button('Reset to default').click();
        await wait(()=>control(quick(),'transpose')&&!control(quick(),'feedbackCount'),'reset predefined defaults');
        // Reapply customized placement and verify browser storage restoration on reload.
        control(quick(),'transpose').querySelector('.placement-target').click();
        await wait(()=>control(inspector(),'transpose'),'move after reset');
        control(inspector(),'feedbackCount').querySelector('.placement-target').click();
        await wait(()=>control(quick(),'feedbackCount'),'delay after reset');
        button('Save quick settings').click();
        await wait(()=>!quick().querySelector('.placement-target'),'saved');
        sessionStorage.setItem('ui-preferences-reloaded','true'); location.reload(); return;
      }
      await fetch('/result',{method:'POST',body:JSON.stringify({ok:true})});
    } catch(error) { await fetch('/result',{method:'POST',body:JSON.stringify({error:String(error),stack:error.stack})}); }
  })();</script>`;
  let resolve, reject;
  const result = new Promise((done, fail) => { resolve = done; reject = fail; });
  result.catch(() => {}); // CDP setup can fail before the result is awaited.
  const server = createBrowserServer({ root, html: index.replace('</body>', script + '</body>'), resolve, reject });
  await listen(server);
  const profile = fs.mkdtempSync(path.resolve(__dirname, '../.angular/ui-preferences-browser-'));
  const downloadPath = path.join(profile, 'downloads');
  fs.mkdirSync(downloadPath);
  fs.mkdirSync(path.join(profile, 'Default'));
  // Configure before launch so the injected workflow cannot export to the user's Downloads.
  fs.writeFileSync(path.join(profile, 'Default/Preferences'), JSON.stringify({
    download: { default_directory: downloadPath, prompt_for_download: false },
  }));
  const child = launchBrowser(browser, ['--headless', '--remote-debugging-port=0', '--window-size='+width+',1100', '--disable-gpu', '--no-first-run', '--disable-background-networking',
    '--disable-background-timer-throttling', '--disable-renderer-backgrounding', '--no-default-browser-check',
    '--user-data-dir=' + profile, 'http://127.0.0.1:' + server.address().port + '/'], { stdio: ['ignore', 'ignore', 'pipe'] });
  const watchdog = watchBrowser(child, reject, 30000);
  let socket;
  try {
    const connection = await connectToPage(child);
    socket = connection.socket;
    const command = connection.command;
    await command('Emulation.setDeviceMetricsOverride', { width, height: 1100, deviceScaleFactor: 1, mobile: false });
    const actual = await result;
    assert.equal(actual.error, undefined, JSON.stringify(actual));
    assert.equal(actual.ok, true);
    const exportedFile = path.join(downloadPath, 'vibecomposer-ui-preferences.json');
    for (let i = 0; i < 100 && !fs.existsSync(exportedFile); i++) {
      await new Promise(resolve => setTimeout(resolve, 50));
    }
    const exported = JSON.parse(fs.readFileSync(exportedFile, 'utf8'));
    assert.equal(exported.kind, 'vibecomposer-ui-preferences');
    assert.equal(exported.appearance.themeColors.accent, '#dd88ee');
    const screenshot = await command('Page.captureScreenshot', { format: 'png' });
    fs.writeFileSync(path.resolve(__dirname, '../.angular/quick-settings-' + width + '.png'), Buffer.from(screenshot.data, 'base64'));
    await command('Runtime.evaluate', { expression: "[...document.querySelectorAll('button')].find(b=>b.textContent.trim()==='Edit quick settings').click()" });
    await new Promise(resolve => setTimeout(resolve, 100));
    const layout = await command('Runtime.evaluate', { expression: `(() => {
      const arrangement=document.querySelector('vc-arrangement-overview .arrangement-panel').getBoundingClientRect();
      const heading=document.querySelector('.part-settings-heading').getBoundingClientRect();
      return { arrangementBottom: arrangement.bottom, headingTop: heading.top };
    })()`, returnByValue: true });
    assert.ok(layout.result.value.headingTop >= layout.result.value.arrangementBottom, 'arrangement overlaps save/reset header');
    const editingScreenshot = await command('Page.captureScreenshot', { format: 'png' });
    fs.writeFileSync(path.resolve(__dirname, '../.angular/quick-settings-edit-' + width + '.png'), Buffer.from(editingScreenshot.data, 'base64'));

  } finally {
    await closeBrowser({ child, server, socket, watchdog });
  }
});
