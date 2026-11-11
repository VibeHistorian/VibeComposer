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
    const menuItems=()=>[...document.querySelectorAll('.context-menu')].at(-1)?.querySelectorAll('[role="menuitem"]');
    // Dispatch directly to the submenu so Escape does not also close its parent.
    const escapeMenu=async()=>{[...document.querySelectorAll('.context-menu')].at(-1).dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',keyCode:27,bubbles:false}));await new Promise(r=>requestAnimationFrame(r));};
    const additionOptions=async(trigger)=>{
      if(trigger.disabled)return [];
      trigger.click();
      const roots=[...await wait(menuItems,'addition menu')],result=[];
      for(const root of roots){
        if(root.getAttribute('aria-haspopup')==='menu'){
          root.click();
          const children=[...await wait(()=>{const items=menuItems();return items&&items[0]!==roots[0]?items:null},'variation submenu')];
          ensure(children.length>1,'multiple variations grouped under track');
          for(const child of children)result.push({textContent:root.textContent.replace('›','').trim()+' · '+child.textContent,value:[root.textContent.trim(),child.textContent.trim()]});
          await escapeMenu();
        }else result.push({textContent:root.textContent,value:[root.textContent.trim()]});
      }
      await escapeMenu();return result;
    };
    const chooseAddition=async(trigger,path)=>{
      trigger.click();
      for(const label of path){
        const item=await wait(()=>[...menuItems()??[]].find(item=>item.textContent.trim()===label),'variation menu action '+JSON.stringify(path));
        item.click();await new Promise(r=>requestAnimationFrame(r));
      }
    };
    const prefs=()=>JSON.parse(localStorage.getItem('vibecomposer.ui-preferences.v1'));
    const setBehavior=async(generate,presence)=>{
      const before=JSON.stringify(read());
      [...document.querySelectorAll('button')].find(b=>b.textContent.trim()==='Settings'||b.getAttribute('aria-label')==='Settings').click();
      const settings=await wait(()=>document.querySelector('vc-settings-dialog'),'settings');
      settings.querySelector('[aria-label="Behavior Arrangement"]').click();
      const generation=await wait(()=>settings.querySelector('[aria-label="Generate arrangement variations"]'),'arrangement behavior');
      const presenceControl=settings.querySelector('[aria-label="Reroll presence too"]');
      if(generation.checked!==generate)generation.click();if(presenceControl.checked!==presence)presenceControl.click();
      await wait(()=>prefs()?.generateArrangementVariations===generate&&prefs()?.rerollPresenceWithVariations===presence,'preferences saved immediately');
      ensure(JSON.stringify(read())===before,'behavior settings never edit the project');
      const bounds=settings.getBoundingClientRect();ensure(bounds.left>=0&&bounds.right<=innerWidth,'settings fit');
      settings.querySelector('[aria-label="Close settings"]').click();await wait(()=>!document.querySelector('vc-settings-dialog'),'settings closed');
    };

    try{
      await wait(()=>innerWidth===${width},'viewport');
      const seed=await wait(()=>document.querySelector('[aria-label="Generation seed"]'),'app');
      if(!sessionStorage.getItem('variation-fixture')){
        await changeControl(seed,'42');const p=await wait(read,'project');
        p.melodyGlobal={...p.melodyGlobal,contextualMelody:true};
        for(const role of ['melody','chords','arpeggio','bass','drums']){
          const base=p.tracks.find(t=>t.role===role);
          for(let i=2;i<=6;i++)p.tracks.push({...base,id:'track-'+role+'-'+i,name:base.name[0]+i,generatorSettings:{...base.generatorSettings}});
        }
        for(const track of p.tracks) if(track.role==='arpeggio')Object.assign(track.generatorSettings,{rhythm:'euclid',hitsPerPattern:8,euclideanPulses:4});
        p.tracks.find(t=>t.role==='arpeggio').generatorSettings.rhythm='full';
        Object.assign(p.tracks.find(t=>t.role==='drums').generatorSettings,{pitch:38,rhythm:'full',hitsPerPattern:8,ghosts:false,pauseChance:0,exceptionChance:0,patternShift:0,swingPercent:50});
        p.arrangement=['VERSE1','BUILDUP2','CHORUS1','VERSE1','CLIMAX','OUTRO'].map((type,i)=>({id:'section-'+(i+1),type,measures:4,
          trackPresence:Object.fromEntries(['melody','chords','arpeggio','bass','drums'].map(role=>[role,Array(6).fill(true)]))}));
        p.arrangement[0].variations={seed:'1',policy:{chance:100,maxChanges:2},choices:[{trackId:p.tracks.find(t=>t.role==='melody').id,kind:'melody-range',amount:1,enabled:true}]};
        localStorage.removeItem('vibecomposer.project-tabs.v1');sessionStorage.setItem('vibecomposer.project.v12',JSON.stringify(p));sessionStorage.setItem('variation-fixture','1');location.reload();return;
      }
      await changeControl(seed,'43');await wait(()=>read()?.seed==='43','canonical project');
      const original=JSON.stringify(read());
      [...document.querySelectorAll('button')].find(b=>b.textContent.trim()==='Settings'||b.getAttribute('aria-label')==='Settings').click();
      const variationSettings=await wait(()=>document.querySelector('vc-settings-dialog'),'variation settings');
      variationSettings.querySelector('[aria-label="Behavior Variations"]').click();
      await wait(()=>variationSettings.querySelectorAll('.variation-settings-group').length===6,'variation groups rendered');
      ensure([...variationSettings.querySelectorAll('.variation-settings-group > h3')].map(h=>h.textContent.trim()).join(',')==='Shared,Melody,Chords,Arps,Bass,Drums','shared and role groups');
      ensure(variationSettings.querySelector('[aria-label="Behavior Variations"] [data-scope="compose"]'),'variation menu On Compose indicator');
      const groupedKinds=[...variationSettings.querySelectorAll('.variation-settings-group input[type="checkbox"]')].map(i=>i.getAttribute('aria-label'));
      ensure(groupedKinds.includes('Reroll Arp subdivision ramp')&&new Set(groupedKinds).size===groupedKinds.length,'each variation appears once');
      const speedToggle=await wait(()=>variationSettings.querySelector('[aria-label="Reroll Melody speed"]'),'variation rows');
      speedToggle.click();await wait(()=>!prefs().variationRerolls.allowedKinds.includes('melody-speed'),'disabled for future rerolls');
      ensure(JSON.stringify(read())===original,'eligibility preference leaves project untouched');
      speedToggle.click();
      const speedDelta=variationSettings.querySelector('[aria-label="Melody speed variation delta"]');
      speedDelta.value='30';speedDelta.dispatchEvent(new Event('input',{bubbles:true}));
      await wait(()=>prefs().variationRerolls.melodySpeedDelta===30,'strength saved immediately');
      const defaults=[...variationSettings.querySelectorAll('.variation-default')];
      ensure(defaults.length===14,'every configurable strength has a slider, including both velocity directions');
      const sliderEnds=[];
      for(const label of defaults) {
        const slider=label.querySelector('input'),output=label.querySelector('output'),text=label.querySelector('span');
        const originalValue=Number(slider.value),changed=Math.min(Number(slider.max),originalValue+1);
        slider.value=String(changed);slider.dispatchEvent(new Event('input',{bubbles:true}));
        await wait(()=>Number(output.textContent)===changed,'strength value updates immediately');
        slider.value=String(originalValue);slider.dispatchEvent(new Event('input',{bubbles:true}));
        await wait(()=>Number(output.textContent)===originalValue,'strength default restored');
        const l=text.getBoundingClientRect(),s=slider.getBoundingClientRect(),v=output.getBoundingClientRect();
        ensure(l.right<=s.left&&s.right<=v.left,'label, slider and value appear in order');
        ensure(Math.abs(s.top+s.height/2-(v.top+v.height/2))<3,'slider and value share a row');
        const canvas=document.createElement('canvas'),context=canvas.getContext('2d');context.font=getComputedStyle(output).font;
        ensure(v.width>=context.measureText('0000').width-.5,'value reserves four digits');
        sliderEnds.push(s.right);
        ensure(label.scrollWidth<=label.clientWidth,'strength control fits supported layout');
      }
      ensure(Math.max(...sliderEnds)-Math.min(...sliderEnds)<2,'sliders align on the right');
      ensure(JSON.stringify(read())===original,'strength preference leaves project untouched');
      variationSettings.querySelector('[aria-label="Close settings"]').click();await wait(()=>!document.querySelector('vc-settings-dialog'),'settings closed');
      const opener=await wait(()=>document.querySelector('[aria-label="Inspect whole arrangement variations"]'),'variations button');
      opener.focus();opener.click();
      let d=await wait(dialog,'dialog');
      ensure(d.contains(document.activeElement),'dialog focus');
      ensure(!d.querySelector('[aria-label="Generate arrangement variations"]')&&!d.querySelector('[aria-label="Reroll presence too"]'),'behavior toggles removed from popup');
      let box=d.getBoundingClientRect();ensure(box.left>=0&&box.right<=innerWidth&&box.top>=0&&box.bottom<=innerHeight,'dialog fits');
      const range=await wait(()=>[...d.querySelectorAll('article')].find(a=>a.textContent.includes('Contextual melody range')),'saved range row');
      const summary=range.querySelectorAll('.effect-value');
      ensure(summary.length===1&&summary[0].textContent.includes('48–72 → 46–74'),'combined MIDI range');
      const labelBox=range.querySelector('.variation-label').getBoundingClientRect(), effectBox=summary[0].getBoundingClientRect();
      ensure(Math.abs(labelBox.top-effectBox.top)<2&&effectBox.left>labelBox.right,'explanation beside track label');
      ensure(![...d.querySelectorAll('.effect-status')].some(s=>['Active','Disabled'].includes(s.textContent.trim())),'no redundant state text');
      ensure(range.scrollWidth<=range.clientWidth,'range row fits');
      ensure(!d.textContent.includes('No variations in this scope.'),'empty-state text removed');
      const heading=d.querySelector('.variation-section-heading'),add=heading.querySelector('.add-variation');
      ensure(Math.abs(add.getBoundingClientRect().right-heading.getBoundingClientRect().right)<2,'add pinned to right');
      const addStyle=getComputedStyle(add), groupButton=document.querySelector('.add-track-button');
      ensure(addStyle.backgroundColor===getComputedStyle(groupButton).backgroundColor,'add uses app button surface');
      ensure(addStyle.textAlign==='center'&&addStyle.paddingLeft==='0px'&&addStyle.display==='grid','plus centered without asymmetric padding');
      ensure(getComputedStyle(heading.closest('.variation-section')).borderTopStyle==='none','no section dividers');
      const labels=[heading.querySelector('h2'),heading.querySelector('.section-intent'),heading.querySelector('.variation-purpose')];
      const centers=labels.map(e=>{const r=e.getBoundingClientRect();return r.top+r.height/2});
      ensure(Math.max(...centers)-Math.min(...centers)<3,'section details share one row');
      const snareOption=(await additionOptions(add)).find(o=>o.textContent.includes('Snare section-end fill'));
      ensure(snareOption,'manual snare fill available');await chooseAddition(add,snareOption.value);
      const snareRow=await wait(()=>[...d.querySelectorAll('article')].find(a=>a.textContent.includes('Snare section-end fill')),'snare fill staged');
      await changeControl(snareRow.querySelector('input[type="number"]'),'2');
      await wait(()=>snareRow.textContent.includes('up to 2 quiet retriggers'),'snare fill limit explained');
      ensure(snareRow.querySelector('.variation-replace').textContent.endsWith('↑'),'snare fill direction');
      const manualOption=(await additionOptions(add)).find(o=>o.textContent.includes('M2 · Melody speed'));
      ensure(manualOption,'manual speed choice offered');
      await chooseAddition(add,manualOption.value);
      await wait(()=>[...d.querySelectorAll('article')].some(a=>a.textContent.includes('M2')&&a.textContent.includes('Melody speed')),'manual choice staged');
      const authoredRow=[...d.querySelectorAll('article')].find(a=>a.textContent.includes('M2')&&a.textContent.includes('Melody speed'));
      const replacement=authoredRow.querySelector('.variation-replace');
      const replacementOption=(await selectOptions(replacement)).find(o=>o.textContent.includes('Melody speed')&&o.textContent.includes('Decrease'));
      ensure(replacementOption,'compatible replacement available');await changeControl(replacement,replacementOption.value);
      await wait(()=>replacement.textContent.trim().endsWith('↓'),'decrease shown on trigger');
      const selectedPopup=await selectPopup(replacement);
      const selectedOption=await wait(()=>selectedPopup.querySelector('[aria-selected="true"]'),'current replacement selected');
      ensure(selectedOption.getAttribute('data-value')===replacementOption.value,'popup selects current kind and direction');
      replacement.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true,cancelable:true}));
      const increase=(await selectOptions(replacement)).find(o=>o.textContent.includes('Melody speed')&&o.textContent.includes('Increase'));
      await changeControl(replacement,increase.value);
      await wait(()=>replacement.textContent.trim().endsWith('↑'),'increase shown on trigger');
      const increasePopup=await selectPopup(replacement);
      const increaseSelected=await wait(()=>increasePopup.querySelector('[aria-selected="true"]'),'increase selected');
      ensure(increaseSelected.getAttribute('data-value')===increase.value&&increaseSelected!==increasePopup.querySelector('[role="option"]'),'non-first current option selected');
      replacement.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true,cancelable:true}));
      await changeControl(replacement,replacementOption.value);
      for(const article of d.querySelectorAll('article')) {
        const name=article.querySelector('.variation-label strong').getBoundingClientRect(),combo=article.querySelector('.variation-replace').getBoundingClientRect();
        ensure(Math.abs((name.top+name.height/2)-(combo.top+combo.height/2))<3,'track name and selector share one row');
        const target=article.querySelector('.variation-target').getBoundingClientRect();
        ensure(target.right<=article.getBoundingClientRect().right&&target.left>=article.getBoundingClientRect().left,'track controls fit card');
      }
      const strength=await wait(()=>authoredRow.querySelector('input[aria-label="M2 speed variation strength"]'),'choice speed strength');
      await changeControl(strength,'45');
      ensure(JSON.stringify(read())===original,'authoring stays local');
      button(d,'Apply variations').click();await wait(()=>!dialog(),'manual addition applied');
      const inserted=read().arrangement[0].variations.choices.find(c=>c.trackId==='track-melody-2');
      ensure(inserted?.origin==='authored'&&inserted.parameters.speedDelta===45,'manual strength and ownership saved');
      ensure(read().arrangement[0].variations.choices.some(c=>c.kind==='drum-end-fill'&&c.parameters.fillLimit===2),'snare fill saved with strength');
      document.querySelector('[aria-label="Undo"]').click();await wait(()=>JSON.stringify(read())===original,'manual insertion single undo');
      opener.click();d=await wait(dialog,'reopen after insertion undo');
      ensure(!d.querySelector('[aria-label="Melody speed variation delta"]'),'strength moved out of popup');
      const budget=d.querySelector('[aria-label="Variation change budget"]');
      ensure(Number(budget.max)===15,'30-track capacity is fifteen');
      await changeControl(budget,'16');
      await wait(()=>button(d,'Reroll variations').disabled,'above-capacity budget rejected');
      const disabledAdd=d.querySelector('.add-variation');
      ensure(disabledAdd.disabled&&Number(getComputedStyle(disabledAdd).opacity)<1&&getComputedStyle(disabledAdd).cursor==='default','disabled add visibly subdued');
      await changeControl(budget,'15');await wait(()=>!button(d,'Reroll variations').disabled,'valid capacity accepted');button(d,'Reroll variations').click();
      await wait(()=>[...d.querySelectorAll('.variation-section')].some(s=>s.querySelectorAll('article').length>4),'larger section budget');
      ensure(JSON.stringify(read())===original,'larger reroll stays local');
      const ending=await wait(()=>[...d.querySelectorAll('.variation-section')].find(s=>s.getAttribute('aria-label')==='OUTRO variations'),'ending variations');
      const ramp=await wait(()=>[...ending.querySelectorAll('article')].find(a=>a.textContent.includes('Accompaniment velocity ramp')),'velocity envelope explanation');
      ensure(ramp.textContent.includes('100% → 80%')&&ramp.textContent.includes('8–16'),'resolved envelope window and gain');
      const velocityStrength=ramp.querySelector('input[type="number"]');
      ensure(velocityStrength&&Number(velocityStrength.max)===50,'bounded velocity strength control');
      await changeControl(velocityStrength,'35');
      await wait(()=>ramp.textContent.includes('100% → 65%'),'edited velocity strength explained');
      await changeControl(velocityStrength,'0');
      ensure(velocityStrength.value==='35','invalid velocity strength restored');
      for(const kind of ['Late chord anticipation','Contextual melody range','Arp activity','Arp chord-relative contour','Hat / ride density','Cymbal activity']) {
        const section=d.querySelector('.variation-section'),adder=section.querySelector('.add-variation');
        const option=(await additionOptions(adder)).find(o=>o.textContent.includes(kind));
        if(option) {
          await chooseAddition(adder,option.value);
          const article=await wait(()=>[...section.querySelectorAll('article')].find(a=>a.textContent.includes(kind)),'parameter choice added');
          const input=article.querySelector('input[type="number"]');
          ensure(input,'parameter strength control');await changeControl(input,kind==='Late chord anticipation'?'40':'3');
          ensure(article.querySelector('.variation-lock').getAttribute('aria-pressed')==='true','strength edit locks choice');
        }
      }
      ensure(JSON.stringify(read())===original,'strength edits remain detached');
      const cut=await wait(()=>[...d.querySelectorAll('article')].find(a=>a.textContent.includes('Pre-drop accompaniment cut')),'timed cut explanation');
      ensure(cut.textContent.includes('15–16')&&cut.textContent.includes('stop sustains'),'actual final-beat window');
      const subdivision=await wait(()=>[...d.querySelectorAll('article')].find(a=>a.textContent.includes('Arp subdivision ramp')),'subdivision ramp explanation');
      ensure(subdivision.textContent.includes('8 → 8 → 16'),'staged subdivision rates');
      await changeControl(subdivision.querySelector('input[type="number"]'),'3');
      await wait(()=>subdivision.textContent.includes('up to 3 added attacks'),'subdivision limit explained');
      await changeControl(cut.querySelector('input[type="number"]'),'8');
      await wait(()=>cut.textContent.includes('14–16'),'expanded cut window explained');
      ensure(JSON.stringify(read())===original,'timed strength edits remain detached');
      cut.querySelector('.variation-lock').click();
      await wait(()=>cut.querySelector('.variation-lock').getAttribute('aria-pressed')==='false','cut released before allocation checks');
      const rampReplacement=subdivision.querySelector('.variation-replace');
      const fillOption=(await selectOptions(rampReplacement)).find(o=>o.textContent.includes('Arp section-end fill'));
      ensure(fillOption,'fill replacement compatible');await changeControl(rampReplacement,fillOption.value);
      const fill=await wait(()=>[...d.querySelectorAll('article')].find(a=>a.textContent.includes('Arp section-end fill')),'end-fill explanation');
      ensure(fill.textContent.includes('14–16')&&fill.textContent.includes('four quiet retriggers'),'actual final-slot fill window');
      await changeControl(fill.querySelector('input[type="number"]'),'2');
      await wait(()=>fill.textContent.includes('up to 2 quiet retriggers'),'fill limit explained');
      const keep=fill.querySelector('.variation-lock'),keepLabel=keep.getAttribute('aria-label');
      if(keep.getAttribute('aria-pressed')!=='true')keep.click();await wait(()=>keep.getAttribute('aria-pressed')==='true','fill marked for retention');
      button(d,'Reroll variations').click();
      const retained=await wait(()=>[...d.querySelectorAll('.variation-lock')].find(i=>i.getAttribute('aria-label')===keepLabel&&i.getAttribute('aria-pressed')==='true'),'authored fill retained');
      ensure(retained.closest('article').getAttribute('data-variation-status')==='active','authored choice remains active');
      retained.click();await wait(()=>retained.getAttribute('aria-pressed')==='false','ownership released explicitly');
      ensure(JSON.stringify(read())===original,'ownership changes stay local');
      await changeControl(budget,'2');
      button(d,'Reroll variations').click();await wait(()=>d.querySelectorAll('article').length>0,'draft choices');
      ensure([...d.querySelectorAll('.variation-purpose')].some(p=>p.textContent.includes('Build pulse')),'buildup purpose');
      ensure([...d.querySelectorAll('.variation-purpose')].some(p=>p.textContent.includes('Release tension')),'release purpose');
      // A random reroll need not choose either operation. Add them explicitly to check their explanations.
      for (const label of ['Arp rhythm density', 'Late chord anticipation']) {
        if (![...d.querySelectorAll('article')].some(article => article.textContent.includes(label))) {
          let added = false;
          for (const section of d.querySelectorAll('.variation-section')) {
            const adder = section.querySelector('.add-variation');
            const option = (await additionOptions(adder)).find(option => option.textContent.includes(label));
            if (!option) continue;
            await chooseAddition(adder, option.value);
            await wait(() => [...section.querySelectorAll('article')].some(article => article.textContent.includes(label)), label + ' added');
            added = true;
            break;
          }
          ensure(added, label + ' available for authored addition');
        }
      }
      ensure([...d.querySelectorAll('article')].some(article=>article.textContent.includes('Arp rhythm density')),'rhythm-aware density explanation');
      ensure([...d.querySelectorAll('article')].some(article=>article.textContent.includes('Late chord anticipation')),'chord transition explanation');
      ensure(JSON.stringify(read())===original,'reroll stays local');
      ensure(!d.querySelector('article input[type="checkbox"]'),'enable checkboxes removed');
      const remove=d.querySelector('.variation-delete'), removedRow=remove.closest('article');
      const section=removedRow.closest('.variation-section'), count=section.querySelectorAll('article').length;
      remove.focus();remove.click();
      await wait(()=>section.querySelectorAll('article').length===count-1,'choice deleted from draft');
      ensure(!section.querySelector('.add-variation').disabled,'addition pool available after deletion');
      ensure(JSON.stringify(read())===original,'deletion stays local');
      button(d,'Cancel').click();await wait(()=>!dialog(),'cancel closes');
      ensure(JSON.stringify(read())===original,'cancel preserves project');
      ensure(document.activeElement===opener,'restore opener focus');
      opener.click();d=await wait(dialog,'reopen');await changeControl(d.querySelector('[aria-label="Variation change budget"]'),'4');button(d,'Reroll variations').click();
      await wait(()=>d.querySelector('article'),'new choices');button(d,'Apply variations').click();
      await wait(()=>!dialog(),'apply closes');
      const saved=JSON.stringify(read());ensure(saved!==original,'apply saves choices');
      ensure(read().arrangementVariationPolicy.melodySpeedDelta===undefined,'strength remains outside project policy');
      ensure(read().arrangement.flatMap(s=>s.variations.choices).filter(c=>c.kind==='melody-speed').every(c=>c.parameters.speedDelta===30),'choices capture speed strength');
      ensure(read().arrangement[1].variations.choices.filter(c=>c.kind==='pre-drop-cut').length===1,'timed cut saved once');
      ensure(read().arrangement[0].variations.choices.filter(c=>c.kind==='arp-end-fill').length===1,'fill saved once before rise');
      ensure(read().arrangement[0].variations.choices.filter(c=>c.kind==='accompaniment-velocity-ramp').length===1,'one ramp saved with fill on another track');
      ensure(read().arrangement[0].variations.gesture==='build-pulse'&&read().arrangement[2].variations.gesture==='release-tension','purpose persists');
      const firstAnchor=read().arrangement[2].variations.choices.find(c=>c.kind==='bass-weight');
      const later=read().arrangement[4].variations.choices;
      ensure(firstAnchor&&later.some(c=>c.kind==='bass-weight'&&c.trackId===firstAnchor.trackId),'return keeps bass anchor');
      ensure(later.filter(c=>c.kind.endsWith('-register')).length===1,'one register lift saved');
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
      await setBehavior(true,true);
      opener.click();d=await wait(dialog,'combined draft');
      button(d,'Reroll variations').click();await wait(()=>d.querySelector('.presence-changes'),'presence preview');
      ensure(!d.querySelector('[data-variation-status="absent"]'),'variations use draft presence');
      ensure(JSON.stringify(read())===saved,'combined draft stays local');
      button(d,'Cancel').click();await wait(()=>!dialog(),'combined cancel');ensure(JSON.stringify(read())===saved,'combined cancel restores both');
      opener.click();d=await wait(dialog,'combined apply');
      ensure(prefs().rerollPresenceWithVariations,'presence behavior survives Cancel and reopen');
      button(d,'Reroll variations').click();await wait(()=>d.querySelector('.presence-changes'),'combined presence');
      button(d,'Apply variations').click();await wait(()=>!dialog(),'combined saved');
      const combined=JSON.stringify(read());ensure(prefs().generateArrangementVariations,'generation preference saved');
      ensure(!read().arrangementVariationGeneration,'project contains no generation switch');
      const structural=p=>p.arrangement.map(({trackPresence,variations,...rest})=>rest);
      ensure(JSON.stringify(structural(read()))===JSON.stringify(structural(JSON.parse(saved))),'combined keeps structure');
      ensure(JSON.stringify(read().arrangement.map(s=>s.trackPresence))!==JSON.stringify(JSON.parse(saved).arrangement.map(s=>s.trackPresence)),'combined updates presence');
      document.querySelector('[aria-label="Undo"]').click();await wait(()=>JSON.stringify(read())===saved,'combined single undo');
      document.querySelector('[aria-label="Redo"]').click();await wait(()=>JSON.stringify(read())===combined,'combined redo');
      await setBehavior(false,true);
      ensure(JSON.stringify(read().arrangement)===JSON.stringify(JSON.parse(combined).arrangement),'disabling keeps authored choices');
      document.querySelector('[aria-label="Reroll arrangement"]').click();
      await wait(()=>read().arrangement.every(s=>!s.variations),'generate bare arrangement');
      await setBehavior(true,false);
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

