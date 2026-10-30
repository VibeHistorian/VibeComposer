const { findBrowser, connectToPage, createBrowserServer, listen, launchBrowser, closeBrowser, watchBrowser, requireProductionBuild, readBrowserDownload } = require('./support/browser-harness.cjs');
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
      for (let i=0;i<200;i++) { const value=await read(); if(value) return value; await new Promise(r=>setTimeout(r,30)); }
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
      const focusedSelect = document.querySelector('vc-select.track-instrument');
      focusedSelect.focus();
      ensure(focusedSelect.matches(':focus-visible'),'instrument vc-select shows keyboard focus');
      const focusProbe = document.createElement('span');
      focusProbe.style.color='var(--border-focus)';
      focusedSelect.parentElement.append(focusProbe);
      ensure(getComputedStyle(focusedSelect).outlineColor===getComputedStyle(focusProbe).color,'vc-select focus outline follows the active theme');
      ensure(getComputedStyle(focusedSelect).outlineStyle!=='none' && parseFloat(getComputedStyle(focusedSelect).outlineWidth)>0,'vc-select has a visible focus outline');
      focusProbe.remove();
      focusedSelect.blur();
      for (const [family, weights] of [['DM Sans', [400, 500, 600, 700]], ['DM Mono', [400, 500]]]) {
        for (const weight of weights) {
          const faces = await document.fonts.load(weight + ' 12px "' + family + '"', 'Árvíztűrő tükörfúrógép');
          ensure(faces.length > 0 && faces.every(face => face.status === 'loaded'), 'bundled font loaded: ' + family + ' ' + weight);
        }
      }
      if (sessionStorage.getItem('ui-preferences-reloaded')) {
        ensure(button('Harmony On Compose').getAttribute('aria-pressed')==='false','harmony Compose toggle restored after reload');
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
          ensure(document.querySelector('[aria-label="Interface text and controls font"]').getAttribute("value")==='Verdana, sans-serif','saved interface font selected');
          ensure(document.querySelector('[aria-label="Headings and track groups font"]').getAttribute("value")==='Georgia, serif','saved heading font selected');
          ensure(document.querySelector('[aria-label="Numbers and music labels font"]').getAttribute("value")==="'Courier New', monospace",'saved numeric font selected');
        };
        checkFonts();
        button('Project').click();
        await wait(()=>!document.querySelector('[aria-label="Interface text and controls font"]'),'leave appearance');
        const fields = await wait(()=>document.querySelector('.export-name-fields'),'export filename fields');
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
        const transposeStar = document.querySelector('input[aria-label="Transpose in semitones"]').closest('vc-compact-knob').querySelector('button[aria-pressed]');
        const harmonyStar = button('Harmony On Compose');
        ensure(harmonyStar?.getAttribute('aria-pressed')==='true','harmony Compose defaults on');
        const harmony = () => {
          const project = JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));
          return JSON.stringify([project.progression, project.progressionChords]);
        };
        harmonyStar.click();
        await wait(()=>harmonyStar.getAttribute('aria-pressed')==='false','harmony Compose disabled');
        const beforeHarmony = harmony();
        ensure(tempoStar?.getAttribute('aria-pressed')==='true' && transposeStar?.getAttribute('aria-pressed')==='true', 'global Compose stars default on');
        const tempo = document.querySelector('input[aria-label="Tempo in beats per minute"]').value;
        const transpose = document.querySelector('input[aria-label="Transpose in semitones"]').value;
        tempoStar.click(); transposeStar.click();
        await wait(()=>tempoStar.getAttribute('aria-pressed')==='false','star disabled');
        const seedInput = document.querySelector('input[aria-label="Generation seed"]');
        await changeControl(seedInput, '0');
        button('Compose').click();
        await wait(()=>{
          const generated = JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));
          return generated?.seed!=='0' && document.querySelector('.transport-project > span').textContent.trim()==='SEED '+generated?.seed;
        },'generated seed summary');
        ensure(seedInput.value==='0','automatic seed input remains zero');
        ensure(harmony()===beforeHarmony,'disabled harmony star preserves chords on Compose');
        ensure(document.querySelector('input[aria-label="Tempo in beats per minute"]').value===tempo,'disabled BPM star preserves tempo');
        ensure(document.querySelector('input[aria-label="Transpose in semitones"]').value===transpose,'disabled transpose star preserves transpose');
        const summary = document.querySelector('.transport-project > span');
        const generatedProject = JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));
        ensure(summary.textContent.trim()==='SEED '+generatedProject.seed,'Seed hint shows actual generation seed');
        ensure(summary.title.endsWith(generatedProject.tempoBpm+' BPM | SEED '+generatedProject.seed),'seed hint retains full song tooltip');
        for (const label of ['History', 'Settings']) {
          ensure(button(label).scrollWidth<=button(label).clientWidth, label+' text fits');
        }
        const dock = document.querySelector('.transport-dock').getBoundingClientRect();
        ensure([...document.querySelectorAll('.transport-tools > *')].every(element => {
          const box = element.getBoundingClientRect();
          return box.left >= dock.left && box.right <= dock.right;
        }), 'project actions fit inside transport');
        const seed=await wait(()=>document.querySelector('[aria-label="Generation seed"]'),'seed');
        await changeControl(seed, '123');
        await wait(()=>sessionStorage.getItem('vibecomposer.project.v12'),'project saved');
        let before=sessionStorage.getItem('vibecomposer.project.v12');
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
        control(quick(),'rhythm').querySelector('.placement-target').click();
        const inspectorPattern=await wait(()=>control(inspector(),'rhythm'),'move Pattern to inspector');
        inspectorPattern.querySelector('.placement-target').click();
        await wait(()=>control(quick(),'rhythm'),'restore quick Pattern');
        await new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)));
        ensure(sessionStorage.getItem('vibecomposer.project.v12')===before,'placement changed musical project');
        ensure(!localStorage.getItem(key),'draft persisted before save');
        button('Save quick settings').click();
        await wait(()=>button('Edit quick settings'),'save mode');
        ensure(JSON.parse(localStorage.getItem(key)).quickSettings.bass.includes('feedbackCount'),'saved preference');
        const areas=quick().querySelectorAll('.part-control-area');
        ensure([...areas].every(area=>getComputedStyle(area).display==='contents'),'quick categories must flow');
        ensure([...inspector().querySelectorAll('.part-control-area')].every(area=>getComputedStyle(area).display!=='contents'),'inspector category areas');
        const velocities=[...quick().querySelectorAll('.joined-member[data-control-key]')].map(c=>c.dataset.controlKey).filter(k=>k.startsWith('velocity'));
        ensure(velocities.join(',')==='velocityMin,velocityMax','fixed velocity order');
        const delay=control(quick(),'feedbackCount');
        const delaySelect=delay.querySelector('vc-select');
        ensure(delaySelect.matches('vc-select[role="combobox"]'),'shared select uses an ARIA combobox trigger');
        let delayChanges=0;
        delaySelect.addEventListener('change',()=>delayChanges++);
        ensure((await selectOptions(delaySelect)).map(o=>o.value).join(',')==='0,1,2,3,4,5','delay count choices');
        for (const select of quick().querySelectorAll('vc-select')) {
          const value=select.querySelector('.select-value');
          ensure(value.scrollWidth<=value.clientWidth,'quick combobox shows its full text: '+value.textContent);
        }
        for (const choices of quick().querySelectorAll('.choice-controls')) {
          const panel=choices.closest('.part-control').getBoundingClientRect();
          for (const child of choices.children) {
            const bounds=child.getBoundingClientRect();
            ensure(bounds.left>=panel.left && bounds.right<=panel.right,'selector and flip button fit inside the control');
          }
        }
        ensure(delay.querySelector('[aria-label="FB duration"]'),'delay time shares count panel');
        delaySelect.dispatchEvent(new WheelEvent('wheel',{deltaY:1,bubbles:true,cancelable:true}));
        await wait(()=>delaySelect.getAttribute("value")==='1','scroll delay count');
        delaySelect.dispatchEvent(new MouseEvent('contextmenu',{button:2,bubbles:true,cancelable:true}));
        await wait(()=>delaySelect.getAttribute("value")==='0','reset delay count');
        ensure(delayChanges===0,'wheel and reset update bindings without synthetic DOM change events');
        delaySelect.dispatchEvent(new MouseEvent('contextmenu',{button:2,bubbles:true,cancelable:true}));
        const selectZoom=new WheelEvent('wheel',{deltaY:1,ctrlKey:true,bubbles:true,cancelable:true});
        delaySelect.dispatchEvent(selectZoom);
        ensure(delayChanges===0 && !selectZoom.defaultPrevented && delaySelect.getAttribute("value")==='0','default reset is a no-op and modified wheel preserves zoom');
        const rhythm=control(quick(),'rhythm'), rhythmSelect=rhythm.querySelector('vc-select');
        await changeControl(rhythmSelect, 'full');
        const shift=await wait(()=>rhythm.querySelector('[aria-label="Shift"]'),'combined pattern shift');
        ensure((await selectOptions(shift)).map(o=>o.value).join(',')==='0,1,2,3,4,5,6,7,8','shift choices');
        shift.dispatchEvent(new WheelEvent('wheel',{deltaY:1,bubbles:true,cancelable:true}));
        await wait(()=>shift.getAttribute("value")==='1','scroll shift');
        shift.dispatchEvent(new MouseEvent('contextmenu',{button:2,bubbles:true,cancelable:true}));
        await changeControl(rhythmSelect, 'bass1');
        await wait(()=>JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')).tracks.find(t=>t.role==='bass').generatorSettings.rhythm==='bass1','restore bass rhythm');
        before=sessionStorage.getItem('vibecomposer.project.v12');
        document.querySelector('.track-group.bass .group-select').click();
        await wait(()=>document.querySelector('.track-group.bass.group-selected'),'selected bass role');
        button('Settings').click();
        const dialog=await wait(()=>document.querySelector('vc-settings-dialog'),'settings dialog');
        await wait(()=>dialog.querySelector('#appearance-title'),'Appearance opens by default');
        ensure(dialog.querySelector('#appearance-title'),'Appearance opens by default');
        ensure(dialog.querySelectorAll('.scope-legend vc-settings-scope-indicator').length===3,'scope legend');
        const scopeColors=['ui','compose','sound'].map(scope=>getComputedStyle(dialog.querySelector('.scope-legend [data-scope="'+scope+'"]')).color);
        ensure(new Set(scopeColors).size===3,'distinct muted UI and musical scope colors');
        const beforeLayoutCheck=sessionStorage.getItem('vibecomposer.project.v12');
        for (const category of [...dialog.querySelectorAll('.settings-sidebar button')]) {
          ensure(category.querySelector('vc-settings-scope-indicator'),'category scope indicator');
          category.click(); await new Promise(done=>requestAnimationFrame(()=>requestAnimationFrame(done)));
          const rows=[...dialog.querySelectorAll('.settings-content .setting-row')];
          ensure(rows.length>0,'every category has settings rows: '+category.textContent.trim());
          for (const row of rows) {
            const copy=row.querySelector('.setting-copy').getBoundingClientRect(), controls=row.querySelector('.setting-controls').getBoundingClientRect();
            ensure(copy.right<=controls.left,'labels left of controls: '+category.textContent.trim());
            ensure(controls.right<=dialog.querySelector('.settings-content').getBoundingClientRect().right,'controls inside settings');
          }
          const content=dialog.querySelector('.settings-content');
          ensure(content.scrollWidth<=content.clientWidth+1,'settings fit without horizontal overflow: '+category.textContent.trim());
          if (category.getAttribute('aria-label')?.startsWith('Shortcuts ')) {
            ensure(category.querySelectorAll('[data-scope="ui"]').length===1 && category.querySelectorAll('vc-settings-scope-indicator').length===1,'shortcuts are UI only');
            ensure(!button('Record Undo') && !button('Record Play / pause') && !button('Record Extend / trim sections'),'universal shortcuts stay hidden');
            const record = async (label, event, id) => {
              button('Record '+label).click();
              await wait(()=>button('Record '+label).getAttribute('aria-pressed')==='true','capture starts');
              document.dispatchEvent(event);
              await wait(()=>button('Record '+label).getAttribute('aria-pressed')==='false','capture ends');
              await new Promise(resolve=>setTimeout(resolve,550));
              return JSON.parse(localStorage.getItem(key)).shortcuts[id][0];
            };
            if (category.getAttribute('aria-label')==='Shortcuts General') {
              ensure(button('Record Reset value') && button('Record Fine knob dragging'),'general includes controls and modifiers');
              const fine=await record('Fine knob dragging',new KeyboardEvent('keyup',{key:'Alt',shiftKey:true,bubbles:true,cancelable:true}),'fineDrag');
              ensure(fine.kind==='modifier' && fine.shift && fine.alt,'capture modifier combination');
            }
            if (category.getAttribute('aria-label')==='Shortcuts Arrangement') {
              ensure(button('Record Duplicate section') && !button('Record Zoom time'),'arrangement includes section headers only');
              const reroll=await record('Reroll settings',new PointerEvent('pointerdown',{button:0,ctrlKey:true,altKey:true,bubbles:true,cancelable:true}),'reroll');
              ensure(reroll.kind==='mouse' && reroll.input==='0' && reroll.ctrl && reroll.alt,'capture mouse combination');
            }
            if (category.getAttribute('aria-label')==='Shortcuts Score') {
              const zoom=await record('Zoom time',new WheelEvent('wheel',{deltaY:-100,shiftKey:true,altKey:true,bubbles:true,cancelable:true}),'zoomTime');
              ensure(zoom.kind==='wheel' && zoom.shift && zoom.alt,'capture wheel combination');
            }
            if (category.getAttribute('aria-label')==='Shortcuts Phrase editor') {
              ensure(button('Record Split note') && button('Record Bypass time grid'),'phrase editor includes gestures and modifiers');
              const transpose=await record('Open transpose',new KeyboardEvent('keydown',{key:'u',ctrlKey:true,shiftKey:true,altKey:true,bubbles:true,cancelable:true}),'transposeNotes');
              ensure(transpose.kind==='key' && transpose.input==='u' && transpose.ctrl && transpose.shift && transpose.alt,'capture full key combination');
              button('Record Delete selected notes').click();
              await wait(()=>button('Record Delete selected notes').getAttribute('aria-pressed')==='true','record conflict');
              document.dispatchEvent(new KeyboardEvent('keydown',{key:'u',ctrlKey:true,shiftKey:true,altKey:true,bubbles:true,cancelable:true}));
              await wait(()=>dialog.textContent.includes('Already used by Open transpose'),'conflict explained');
              document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true,cancelable:true}));
              await wait(()=>button('Record Delete selected notes').getAttribute('aria-pressed')==='false','escape cancels');
              button('Clear Open transpose').click();
              await wait(()=>JSON.parse(localStorage.getItem(key)).shortcuts.transposeNotes.length===0,'clear binding');
            }
            button('Reset to app defaults').click();
            await wait(()=>JSON.parse(localStorage.getItem(key)).shortcuts.transposeNotes[0].input==='t','reset shortcuts');
            ensure(JSON.parse(localStorage.getItem(key)).shortcuts.reroll[0].input==='1','mouse defaults restored');
          }
          if (category.getAttribute('aria-label')==='Behavior Melody') {
            ensure(dialog.querySelector('[aria-label="Contextual Melody"]').closest('.setting-row').querySelector('[data-scope="sound"]'),'algorithm affects track sound');
            ensure(dialog.querySelector('[aria-label="Randomize range on Compose"]').closest('.setting-row').querySelector('[data-scope="compose"]'),'range randomization applies on Compose');
          }
          if (category.getAttribute('aria-label')==='Behavior Section Types') {
            const setChance=(input,value)=>{input.value=String(value);input.dispatchEvent(new Event('input',{bubbles:true}));};
            const chance=dialog.querySelector('[aria-label="INTRO Melody preference"]');
            ensure(chance?.type==='range','section chance slider');
            await setChance(chance,'37');
            await wait(()=>JSON.parse(localStorage.getItem('vibecomposer.section-type-settings.v1'))?.INTRO.melody===37,'section chance persists');
            const roles=['melody','bass','chords','arpeggio','drums'];
            const row=dialog.querySelector('[data-section-type="INTRO"]');
            const sliders=[...row.querySelectorAll('input[type="range"]')];
            const distribution=row.querySelector('.chance-distribution');
            const segment=part=>row.querySelector('.share-segment[data-role="'+part+'"]');
            for (const [index,value] of [0,1,50,100,25].entries()) await setChance(sliders[index],String(value));
            await new Promise(done=>requestAnimationFrame(()=>requestAnimationFrame(done)));
            const name=row.querySelector('.setting-copy').getBoundingClientRect(), bar=distribution.getBoundingClientRect(), controls=row.querySelector('.chance-sliders').getBoundingClientRect();
            ensure(name.right<=bar.left&&bar.right<=controls.left,'proportional bar sits between section name and sliders');
            const values=[0,1,50,100,25], brightness=[0.25,0.5,0.5+49/99*0.5,1,0.5+24/99*0.5];
            const shares=[0.1,0.1,80*50/175/100,80*100/175/100,80*25/175/100];
            for (const [index,part] of roles.entries()) {
              ensure(Math.abs(segment(part).getBoundingClientRect().width/bar.width-shares[index])<0.001,'bar width respects minimum and remaining proportions for '+part);
              const fill=segment(part).querySelector('.share-color').getBoundingClientRect();
              ensure(Math.abs(fill.height/bar.height-values[index]/100)<0.001,'fill height follows '+part+' chance');
              ensure(Math.abs(fill.bottom-bar.bottom)<1,'fill anchored to bar bottom');
              const label=segment(part).querySelector('.share-value').getBoundingClientRect();
              ensure(Math.abs((label.top+label.bottom-bar.top-bar.bottom)/2)<1,'graph label centered in full height');
              const sliderBox=sliders[index].getBoundingClientRect(), percent=sliders[index].closest('label').querySelector('output').getBoundingClientRect();
              ensure(percent.bottom<=sliderBox.top,'slider percent above control');
              ensure(!row.querySelector('.role-label'),'no visible role name labels');
              const filter=getComputedStyle(segment(part).querySelector('.share-color')).filter;
              ensure(Math.abs(Number(filter.match(/[0-9.]+/)[0])-brightness[index])<0.00001,'exact brightness curve for '+part);
              ensure(getComputedStyle(sliders[index]).getPropertyValue('--role-color').trim()===getComputedStyle(document.documentElement).getPropertyValue('--role-'+part).trim(),'slider uses themed role color');
              ensure(sliders[index].getAttribute('aria-valuetext')===values[index]+' percent','accessible slider percent');
            }
            for (const slider of sliders) await setChance(slider,'0');
            await new Promise(done=>requestAnimationFrame(()=>requestAnimationFrame(done)));
            ensure(distribution.classList.contains('empty'),'all-zero distribution');
            for (const part of roles) {
              ensure(Math.abs(segment(part).getBoundingClientRect().width/distribution.getBoundingClientRect().width-0.2)<0.001,'all-zero shares have equal widths');
              ensure(getComputedStyle(segment(part).querySelector('.share-color')).filter==='brightness(0.25)','zero brightness');
              ensure(segment(part).querySelector('.share-color').getBoundingClientRect().height===0,'zero chance has no filled height');
            }
            await setChance(sliders[0],'100');
            await new Promise(done=>requestAnimationFrame(()=>requestAnimationFrame(done)));
            ensure(Math.abs(segment('melody').getBoundingClientRect().width/distribution.getBoundingClientRect().width-0.6)<0.001,'single active role leaves 10% for each other role');
            button('Reset defaults').click();
            await wait(()=>JSON.parse(localStorage.getItem('vibecomposer.section-type-settings.v1')).INTRO.melody===20,'section chances reset');
          }
        }
        ensure(sessionStorage.getItem('vibecomposer.project.v12')===beforeLayoutCheck,'browsing categories and section defaults preserves project');
        button('Appearance General').click();
        await wait(()=>dialog.querySelector('#appearance-title'),'return from layout checks');
        const beforeRandomize = JSON.parse(localStorage.getItem(key));
        const projectBeforeRandomize = sessionStorage.getItem('vibecomposer.project.v12');
        button('Random dark mode').click();
        const randomized = await wait(()=>{
          const appearance = JSON.parse(localStorage.getItem(key)).appearance;
          return appearance.themeColors.background !== '#080e16' && appearance;
        },'random palette saved');
        const brightness = color => [1,3,5].reduce((sum,offset)=>sum+parseInt(color.slice(offset,offset+2),16),0)/3;
        ensure(brightness(randomized.themeColors.background)<80,'dark button produces dark background');
        ensure(new Set(Object.values(randomized.roleColors)).size===5,'five randomized track colors');
        ensure(JSON.stringify(randomized.fonts)===JSON.stringify(beforeRandomize.appearance.fonts),'randomizer preserves fonts');
        ensure(Object.values(randomized.colorAdjustments).every(value=>value===0),'randomizer neutralizes adjustments');
        const randomStyle = getComputedStyle(document.documentElement);
        ensure(randomStyle.getPropertyValue('--accent-primary').trim()===randomized.themeColors.accent,'random theme applied immediately');
        for (const [role,color] of Object.entries(randomized.roleColors)) {
          ensure(randomStyle.getPropertyValue('--role-'+role).trim()===color,'random track color applied: '+role);
        }
        button('Random light mode').click();
        await wait(()=>brightness(JSON.parse(localStorage.getItem(key)).appearance.themeColors.background)>220,'light button produces light background');
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
        const bassInstrument=()=>document.querySelector('.track-group.bass vc-select.track-instrument');
        ensure(poolToggle?.getAttribute('aria-pressed')==='false','instrument pool filtering defaults off');
        ensure((await selectOptions(bassInstrument())).length===128,'all GM instruments available by default');
        const selectedProgram=bassInstrument().getAttribute("value");
        const projectBeforePoolToggle=sessionStorage.getItem('vibecomposer.project.v12');
        poolToggle.click();
        const bassPool=[0,1,4,53,54,73,74,75,82,85,91,102];
        await wait(async()=>(await selectOptions(bassInstrument())).length<128,'instrument options filtered immediately');
        const expected=[...new Set([...bassPool,Number(selectedProgram)])].sort((a,b)=>a-b);
        ensure(JSON.stringify((await selectOptions(bassInstrument())).map(option=>Number(option.value)))===JSON.stringify(expected),'bass pool plus selected instrument');
        ensure(bassInstrument().getAttribute("value")===selectedProgram,'filter retains selected instrument');
        ensure(sessionStorage.getItem('vibecomposer.project.v12')===projectBeforePoolToggle,'filter does not edit musical project');
        ensure(JSON.parse(localStorage.getItem(key)).limitInstrumentsToPool===true,'pool filter preference persisted');
        poolToggle.click();
        await wait(async()=>(await selectOptions(bassInstrument())).length===128,'toggle restores full instrument catalog');
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
        const originalCellSurface=style().getPropertyValue('--role-bass-cell-surface');
        const surfaceInput=dialog.querySelector('[aria-label="Surface theme color"]');
        const textInput=dialog.querySelector('[aria-label="Text theme color"]');
        surfaceInput.value='#ffffff'; surfaceInput.dispatchEvent(new Event('input',{bubbles:true}));
        textInput.value='#000000'; textInput.dispatchEvent(new Event('input',{bubbles:true}));
        await wait(()=>style().getPropertyValue('--role-bass-header').trim()==='#cbd8e0','role header follows light surface');
        ensure(getComputedStyle(document.querySelector('.track-group.bass .group-heading')).backgroundColor==='rgb(203, 216, 224)','light role header rendered');
        ensure(style().getPropertyValue('--role-bass-cell-surface')===originalCellSurface,'arrangement cell retains its background blend when surface changes');
        ensure(style().getPropertyValue('--role-bass-text').trim()==='#1f2529','role text follows dark theme text');
        button('Reset appearance to defaults').click();
        await wait(()=>style().getPropertyValue('--surface-base')===originalSurface,'restore dark surface');
        const changeAdjustment=async(label,value)=>{
          const knob=dialog.querySelector('vc-compact-knob input[aria-label="'+label+'"]');
          ensure(knob && knob.min==='-50' && knob.max==='50','adjustment knob range');
          await changeControl(knob, String(value));
        };
        await changeAdjustment('Brightness',50);
        await wait(()=>style().getPropertyValue('--accent-primary').trim()==='#4bffff','brightness scales and clamps');
        ensure(style().getPropertyValue('--role-melody').trim()==='#ff6060','brightness adjusts role colors');
        await changeAdjustment('Brightness',0);
        await wait(()=>style().getPropertyValue('--accent-primary').trim()==='#32d7f4','brightness returns to original');
        await changeAdjustment('Hue shift',50);
        await wait(()=>style().getPropertyValue('--accent-primary').trim()!=='#32d7f4','hue shifts main theme');
        ensure(button('All').getAttribute('aria-pressed')==='false','role hue shift defaults off');
        ensure(style().getPropertyValue('--role-melody').trim()==='#d44040','default hue shift excludes role colors');
        button('All').click();
        await wait(()=>style().getPropertyValue('--role-melody').trim()==='#40d4d4','hue shifts role colors');
        button('All').click();
        await wait(()=>style().getPropertyValue('--role-melody').trim()==='#d44040','turning All off restores role hue');
        await changeAdjustment('Brightness',50);
        await wait(()=>style().getPropertyValue('--role-melody').trim()==='#ff6060','brightness still applies with role hue excluded');
        await changeAdjustment('Brightness',0);
        button('All').click();
        await wait(()=>style().getPropertyValue('--role-melody').trim()==='#40d4d4','All can be reenabled');
        await changeAdjustment('Hue shift',0);
        await changeAdjustment('Saturation',-50);
        await wait(()=>style().getPropertyValue('--role-melody').trim()==='#d48a8a','saturation adjusts role colors');
        ensure(JSON.parse(localStorage.getItem(key)).appearance.colorAdjustments.saturation===-50,'adjustment persisted');
        ensure(JSON.parse(localStorage.getItem(key)).appearance.roleColors.melody==='#d44040','chosen color retained');
        await changeAdjustment('Saturation',0);
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
        await changeControl(font, 'Verdana, sans-serif');
        await wait(()=>getComputedStyle(dialog).fontFamily.includes('Verdana'),'live interface font');
        const headings=dialog.querySelector('[aria-label="Headings and track groups font"]');
        await changeControl(headings, 'Georgia, serif');
        await wait(()=>getComputedStyle(dialog.querySelector('h2')).fontFamily.includes('Georgia'),'live heading font');
        const numbers=dialog.querySelector('[aria-label="Numbers and music labels font"]');
        await changeControl(numbers, "'Courier New', monospace");
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
    // Use trusted mouse input: synthetic change events cannot exercise native popup lifetime.
    const instrumentPoint = await command('Runtime.evaluate', { expression: `(() => {
      const select = document.querySelector('vc-select.track-instrument:not([aria-disabled="true"])');
      const rect = select.getBoundingClientRect();
      return { x: rect.x + rect.width / 2, y: rect.y + rect.height / 2, value: select.getAttribute("value") };
    })()`, returnByValue: true });
    const { x, y, value: beforeWheel } = instrumentPoint.result.value;
    await command('Input.dispatchMouseEvent', { type: 'mouseMoved', x, y });
    const clickInstrument = async () => {
      await command('Input.dispatchMouseEvent', { type: 'mousePressed', x, y, button: 'left', clickCount: 1 });
      await command('Input.dispatchMouseEvent', { type: 'mouseReleased', x, y, button: 'left', clickCount: 1 });
    };
    await clickInstrument();
    await new Promise(resolve => setTimeout(resolve, 100));
    await clickInstrument();
    await command('Input.dispatchMouseEvent', { type: 'mouseWheel', x, y, deltaX: 0, deltaY: 100 });
    await clickInstrument();
    await new Promise(resolve => setTimeout(resolve, 250));
    const wheelSelection = await command('Runtime.evaluate', { expression: `document.querySelector('vc-select.track-instrument:not([aria-disabled="true"])').getAttribute("value")`, returnByValue: true });
    assert.notEqual(wheelSelection.result.value, beforeWheel, 'trusted wheel changes the remembered instrument');
    const popup = await command('Runtime.evaluate', { expression: `document.querySelector('vc-select.track-instrument:not([aria-disabled="true"])').getAttribute('aria-expanded')`, returnByValue: true });
    assert.equal(popup.result.value, 'true', 'ARIA listbox stays open after open-close-wheel-immediate-reopen');
    await command('Input.dispatchKeyEvent', { type: 'keyDown', key: 'Escape', code: 'Escape', windowsVirtualKeyCode: 27 });
    await command('Input.dispatchKeyEvent', { type: 'keyUp', key: 'Escape', code: 'Escape', windowsVirtualKeyCode: 27 });
    const press = async (key, code, windowsVirtualKeyCode) => {
      await command('Input.dispatchKeyEvent', { type: 'keyDown', key, code, windowsVirtualKeyCode });
      await command('Input.dispatchKeyEvent', { type: 'keyUp', key, code, windowsVirtualKeyCode });
    };
    await command('Input.dispatchMouseEvent', { type: 'mouseWheel', x, y, deltaX: 0, deltaY: 100 });
    await press(' ', 'Space', 32);
    await new Promise(resolve => setTimeout(resolve, 250));
    const keyboardPopup = await command('Runtime.evaluate', { expression: `(() => {
      const trigger = document.querySelector('vc-select.track-instrument:not([aria-disabled="true"])');
      return { expanded: trigger.getAttribute('aria-expanded'), value: trigger.getAttribute("value") };
    })()`, returnByValue: true });
    assert.equal(keyboardPopup.result.value.expanded, 'true', 'Space opens a stable popup immediately after wheel input');
    await press('ArrowDown', 'ArrowDown', 40);
    await press('Escape', 'Escape', 27);
    const cancelledSelection = await command('Runtime.evaluate', { expression: `document.querySelector('vc-select.track-instrument:not([aria-disabled="true"])').getAttribute("value")`, returnByValue: true });
    assert.equal(cancelledSelection.result.value, keyboardPopup.result.value.value, 'Escape cancels keyboard browsing without changing selection');
    await press(' ', 'Space', 32);
    await new Promise(resolve => setTimeout(resolve, 40));
    await press('End', 'End', 35);
    const activeChoice = await command('Runtime.evaluate', { expression: `new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(() => {
      const trigger = document.querySelector('vc-select.track-instrument:not([aria-disabled="true"])');
      const active = document.getElementById(trigger.getAttribute('aria-activedescendant'));
      resolve({ value: trigger.getAttribute("value"), next: active?.getAttribute('data-value') });
    })))`, awaitPromise: true, returnByValue: true });
    assert.equal(activeChoice.result.value.value, cancelledSelection.result.value, 'keyboard navigation alone does not commit');
    assert.ok(activeChoice.result.value.next, 'keyboard active option is exposed through ARIA');
    await press('Enter', 'Enter', 13);
    await new Promise(resolve => setTimeout(resolve, 40));
    const committedChoice = await command('Runtime.evaluate', { expression: `(() => {
      const trigger = document.querySelector('vc-select.track-instrument:not([aria-disabled="true"])');
      return { value: trigger.getAttribute("value"), expanded: trigger.getAttribute('aria-expanded') };
    })()`, returnByValue: true });
    assert.equal(committedChoice.result.value.value, activeChoice.result.value.next, 'Enter commits the activated option');
    assert.equal(committedChoice.result.value.expanded, 'false', 'Enter closes the popup');
    const popupGeometry = await command('Runtime.evaluate', { expression: `(async () => {
      const trigger = document.querySelector('vc-select[aria-label="Mode"]');
      const previous = trigger.getAttribute('style');
      const results = [];
      for (const edge of ['bottom', 'top']) {
        Object.assign(trigger.style, { position: 'fixed', left: '12px', top: edge === 'bottom' ? (innerHeight - 36) + 'px' : '12px', width: '140px', zIndex: '9999' });
        const panel = await selectPopup(trigger);
        await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
        const anchor = trigger.getBoundingClientRect(), bounds = panel.getBoundingClientRect();
        results.push({ edge, fits: bounds.left >= 0 && bounds.right <= innerWidth && bounds.top >= 0 && bounds.bottom <= innerHeight,
          correctSide: edge === 'bottom' ? bounds.bottom <= anchor.top : bounds.top >= anchor.bottom,
          scrolls: panel.scrollHeight > panel.clientHeight });
        trigger.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }));
        await new Promise(resolve => requestAnimationFrame(resolve));
      }
      if (previous === null) trigger.removeAttribute('style'); else trigger.setAttribute('style', previous);
      return results;
    })()`, awaitPromise: true, returnByValue: true });
    assert.equal(popupGeometry.exceptionDetails, undefined, JSON.stringify(popupGeometry));
    assert.ok(popupGeometry.result.value.every(result => result.fits && result.correctSide && result.scrolls), JSON.stringify(popupGeometry.result.value));
    await command('Input.dispatchMouseEvent', { type: 'mouseMoved', x: 0, y: 0 });
    const restoredTitle = await command('Runtime.evaluate', { expression: `new Promise(resolve => requestAnimationFrame(() => resolve(document.querySelector('vc-select.track-instrument:not([aria-disabled="true"])').title)))`, awaitPromise: true, returnByValue: true });
    assert.ok(restoredTitle.result.value.startsWith('Instrument:'), 'instrument tooltip remains available');
    const exported = JSON.parse(await readBrowserDownload(child, 'vibecomposer-ui-preferences.json'));
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
