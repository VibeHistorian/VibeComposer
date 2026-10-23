const { findBrowser, connectToPage, createBrowserServer, listen, launchBrowser, closeBrowser, requireProductionBuild } = require('./support/browser-harness.cjs');
const assert=require('node:assert/strict');
const fs=require('node:fs'); const path=require('node:path'); const test=require('node:test');
const browser=findBrowser();
test('unified score keeps the workspace, filters drums and applies contextual harmonic and drum edits', {skip:!browser,timeout:60000},async()=>{
 const root=requireProductionBuild();
 const server=createBrowserServer({ root });
 await listen(server);
 const profile=fs.mkdtempSync(path.resolve(__dirname,'../.angular/unified-score-'));
 const child=launchBrowser(browser,['--headless','--disable-gpu','--no-first-run','--disable-background-networking','--remote-debugging-port=0','--window-size=1600,1100','--user-data-dir='+profile,'http://127.0.0.1:'+server.address().port]);
 let socket;
 try {
  const connection = await connectToPage(child);
  socket = connection.socket;
  const command = connection.command;
  const evaluate=async expression=>{let result;for(let retry=0;retry<3;retry++){try{result=await command('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});break;}catch(error){if(!error.message.includes('Execution context was destroyed'))throw error;await new Promise(r=>setTimeout(r,500));}}assert.equal(result.exceptionDetails,undefined,JSON.stringify(result));return result.result.value;};
  const waitCode=`const wait=async f=>{for(let i=0;i<200;i++){const v=f();if(v)return v;await new Promise(r=>setTimeout(r,25));}throw Error('Timed out waiting for score');};`;
  const headerSelection=await evaluate(`(async()=>{${waitCode}
    await wait(()=>document.querySelectorAll('button.score-ruler-section').length>=2);
    const headers=()=>[...document.querySelectorAll('button.score-ruler-section')];
    const selected=()=>headers().flatMap((button,index)=>button.getAttribute('aria-pressed')==='true'?[index]:[]);
    const click=async(index,modifiers={})=>{headers()[index].dispatchEvent(new MouseEvent('click',{bubbles:true,...modifiers}));await new Promise(r=>setTimeout(r,60));return selected();};
    const plain=await click(0),shift=await click(1,{shiftKey:true}),ctrl=await click(0,{ctrlKey:true}),meta=await click(1,{metaKey:true}),cleared=await click(1);
    const b=headers()[0].getBoundingClientRect();
    return {plain,shift,ctrl,meta,cleared,background:getComputedStyle(headers()[0]).backgroundColor,x:b.left+Math.min(8,b.width/2),y:b.top+b.height/2};
  })()`);
  assert.deepEqual(headerSelection.plain,[0]);assert.deepEqual(headerSelection.shift,[1]);
  assert.deepEqual(headerSelection.ctrl,[0]);assert.deepEqual(headerSelection.meta,[1]);assert.deepEqual(headerSelection.cleared,[]);
  await command('Input.dispatchMouseEvent',{type:'mouseMoved',x:headerSelection.x,y:headerSelection.y});
  const hoveredHeader=await evaluate(`({background:getComputedStyle(document.querySelector('.score-ruler-section')).backgroundColor,
    expected:getComputedStyle(document.documentElement).getPropertyValue('--surface-hover').trim(),hovered:document.querySelector('.score-ruler-section').matches(':hover')})`);
  assert.equal(hoveredHeader.hovered,true);assert.notEqual(hoveredHeader.background,headerSelection.background);
  await evaluate(`(async()=>{${waitCode}await wait(()=>document.querySelector('vc-score-canvas canvas'));const seed=document.querySelector('[aria-label="Generation seed"]');await changeControl(seed, '43');const p=await wait(()=>JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')));p.arrangement=[{...p.arrangement[0],measures:4,trackPresence:Object.fromEntries(p.tracks.map(t=>[t.id,true]))}];for(const t of p.tracks)t.editedPhrase=[{id:t.id+'-fixture',midi:t.role==='drums'?36:t.role==='bass'?48:60,startBeat:0,durationBeats:1,velocity:90}];const bass=p.tracks.find(t=>t.role==='bass');bass.editedPhrase.push({id:'bass-low',midi:24,startBeat:4,durationBeats:1,velocity:90},{id:'bass-high',midi:96,startBeat:8,durationBeats:1,velocity:90});const drum=p.tracks.find(t=>t.role==='drums');for(const [index,pitch] of [[2,38],[3,42]])p.tracks.push({...drum,id:'track-drums-'+index,name:'D'+index,generatorSettings:{...drum.generatorSettings,pitch},editedPhrase:[{id:'shared-drum',midi:pitch,startBeat:0,durationBeats:1,velocity:90}]});sessionStorage.setItem('vibecomposer.project.v12',JSON.stringify(p));return true;})()`);
  await command('Page.reload');
  const initial=await evaluate(`(async()=>{${waitCode}await wait(()=>document.querySelector('.drum-label'));return {labels:document.querySelectorAll('.pitch-key').length,drums:document.querySelectorAll('.drum-label').length,arrangement:!!document.querySelector('vc-arrangement-overview canvas'),velocity:!!document.querySelector('.velocity-label')};})()`);
  assert.ok(initial.labels>=17);assert.equal(initial.drums,3);assert.equal(initial.arrangement,true);assert.equal(initial.velocity,false);
  const capture=async name=>{const png=await command('Page.captureScreenshot',{format:'png'});fs.writeFileSync(path.resolve(__dirname,'../.angular/'+name+'.png'),Buffer.from(png.data,'base64'));};
  await capture('unified-score');
  const tooltip = await evaluate(`(async()=>{${waitCode}
    const canvas=document.querySelector('vc-score-canvas canvas'),c=canvas.getBoundingClientRect();
    const row=document.querySelector('.pitch-key[title="MIDI 48"]').getBoundingClientRect();
    canvas.dispatchEvent(new PointerEvent('pointermove',{clientX:c.left+5,clientY:row.top+row.height/2}));
    const tip=await wait(()=>document.querySelector('.note-tooltip'));
    const result={text:tip.textContent,color:getComputedStyle(tip.querySelector('strong')).color,native:canvas.title,
      fits:tip.getBoundingClientRect().right<=c.right,pointerEvents:getComputedStyle(tip).pointerEvents};
    canvas.dispatchEvent(new PointerEvent('pointerleave'));
    await wait(()=>!document.querySelector('.note-tooltip'));return result;
  })()`);
  assert.match(tooltip.text,/B1/);assert.match(tooltip.text,/C3 \[48\]/);assert.match(tooltip.text,/V90/);
  assert.match(tooltip.text,/Global edited phrase/);assert.equal(tooltip.native,'');assert.equal(tooltip.fits,true);assert.equal(tooltip.pointerEvents,'none');
  const scrollbars=await evaluate(`(async()=>{
    const canvas=document.querySelector('vc-score-canvas canvas'),h=document.querySelector('.horizontal-scroll'),v=document.querySelector('.vertical-scroll');
    const wheel=extra=>canvas.dispatchEvent(new WheelEvent('wheel',{bubbles:true,cancelable:true,deltaY:-350,clientX:canvas.getBoundingClientRect().left,clientY:canvas.getBoundingClientRect().top,...extra}));
    const settle=()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)));
    await settle();
    const fit=h.scrollWidth<=h.clientWidth+1&&v.scrollHeight<=v.clientHeight+1;
    wheel({ctrlKey:true});wheel({altKey:true});await settle();
    const ranges=h.scrollWidth>h.clientWidth&&v.scrollHeight>v.clientHeight;
    const visible=v.offsetWidth-v.clientWidth>=6;
    wheel({shiftKey:true,deltaY:120});wheel({deltaY:80});await settle();
    const moved=h.scrollLeft>0&&v.scrollTop>0;
    h.scrollLeft=0;await settle();v.scrollTop=0;await settle();
    const reset=h.scrollLeft===0&&v.scrollTop===0;
    wheel({ctrlKey:true,deltaY:10000});wheel({altKey:true,deltaY:10000});await settle();
    const restored=h.scrollWidth<=h.clientWidth+1&&v.scrollHeight<=v.clientHeight+1;
    return {fit,ranges,visible,moved,reset,restored};
  })()`);
  assert.deepEqual(scrollbars,{fit:true,ranges:true,visible:true,moved:true,reset:true,restored:true},'native scrollbars are visible and track zoom and pan in both directions');
  await command('Page.reload');
  await evaluate(`(async()=>{${waitCode}await wait(()=>document.querySelector('.drum-label'));})()`);
  for (const [width,height,visible,padding] of [[1600,766,true,87],[1600,765,true,87],[1400,906,true,135],[1400,905,true,135],[1401,905,true,87],[1280,800,true,135]]) {
    await command('Emulation.setDeviceMetricsOverride',{width,height,deviceScaleFactor:1,mobile:false});
    const layout=await evaluate(`(async()=>{await new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)));
      const box=selector=>document.querySelector(selector).getBoundingClientRect();
      const canvas=box('vc-score-canvas canvas'), horizontal=box('.horizontal-scroll'), vertical=box('.vertical-scroll'), ruler=box('.score-ruler-viewport');
      const h=document.querySelector('.horizontal-scroll'),v=document.querySelector('.vertical-scroll');
      if(document.querySelectorAll('.score-role-controls button').length!==5 || [...document.querySelectorAll('.score-role-controls button')].some(b=>b.textContent.trim()))throw Error('Expected five dot-only role buttons');
      if(parseFloat(getComputedStyle(document.querySelector('.score-panel')).paddingTop)!==3)throw Error('Expected compact score padding');
      return {visible:getComputedStyle(document.querySelector('.score-role-controls')).display!=='none',padding:parseFloat(getComputedStyle(document.querySelector('.app-frame')).paddingBottom),
        horizontalGap:horizontal.bottom-canvas.bottom-(h.offsetHeight-h.clientHeight),verticalGap:vertical.right-canvas.right-(v.offsetWidth-v.clientWidth),rulerGap:ruler.right-canvas.right};
    })()`);
    assert.equal(layout.visible,visible,`role controls at ${width}x${height}`);
    assert.equal(layout.padding,padding);
    for(const key of ['horizontalGap','verticalGap','rulerGap'])assert.ok(Math.abs(layout[key])<=1,`${key} at ${width}x${height}: ${JSON.stringify(layout)}`);
  }
  await command('Emulation.setDeviceMetricsOverride',{width:1600,height:1100,deviceScaleFactor:1,mobile:false});
  await evaluate(`(async()=>{${waitCode}document.querySelector('.track-group.arpeggio .track-select').click();await wait(()=>document.querySelector('#part-inspector').dataset.controlRole==='arpeggio');})()`);
  const filters=await evaluate(`(async()=>{${waitCode}
    const role=document.querySelector('.score-role-controls [aria-label="Drums"]');
    const before=document.querySelector('vc-score-canvas canvas');
    const project=()=>sessionStorage.getItem('vibecomposer.project.v12');
    const original=project();
    const openScore=async()=>{
      document.querySelector('[aria-label="Settings"]').click();
      (await wait(()=>document.querySelector('.settings-sidebar [aria-label="Appearance Score"]'))).click();
      return await wait(()=>document.querySelector('[aria-label="Highlight audible"]'));
    };
    role.click();await wait(()=>!document.querySelector('.drum-label'));
    (await openScore()).click();
    document.querySelector('[aria-label="Close settings"]').click();
    if(project()!==original)throw Error('Score preference changed mix');
    role.click();await wait(()=>document.querySelectorAll('.drum-label').length===3);
    const mute=document.querySelector('.track-group.drums .group-mix-controls .mute-toggle');
    mute.click();await wait(()=>!document.querySelector('.drum-label'));
    mute.click();await wait(()=>document.querySelectorAll('.drum-label').length===3);
    const solo=document.querySelector('.track-group.bass .group-mix-controls .solo-toggle');
    solo.click();await wait(()=>!document.querySelector('.drum-label'));
    const soloed=project();role.click();role.click();
    if(project()!==soloed)throw Error('Score role buttons changed Solo/Mute');
    solo.click();await wait(()=>document.querySelectorAll('.drum-label').length===3);
    (await openScore()).click();document.querySelector('[aria-label="Close settings"]').click();
    role.click();await wait(()=>!document.querySelector('.drum-label'));
    return document.querySelector('vc-score-canvas canvas')===before;
  })()`);assert.equal(filters,true);
  const overviewViewport=await evaluate(`(()=>{const canvas=document.querySelector('vc-score-canvas canvas');const c=canvas.getBoundingClientRect();canvas.dispatchEvent(new WheelEvent('wheel',{deltaY:-346.57,ctrlKey:true,clientX:c.left,clientY:c.top+10,bubbles:true,cancelable:true}));const content=document.querySelector('vc-score-canvas .horizontal-scroll > div');return content.style.width;})()`);
  const point=await evaluate(`(async()=>{await new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)));const canvas=document.querySelector('vc-score-canvas canvas');canvas.scrollIntoView({block:'center'});const c=canvas.getBoundingClientRect(),key=document.querySelector('.pitch-key[title="MIDI 48"]').getBoundingClientRect();return {x:c.left+c.width/32,y:key.top+key.height/2};})()`);
  await command('Input.dispatchMouseEvent',{type:'mousePressed',...point,button:'left',clickCount:1});await command('Input.dispatchMouseEvent',{type:'mouseReleased',...point,button:'left',clickCount:1});
  const edit=await evaluate(`(async()=>{${waitCode}await wait(()=>document.querySelector('#note-velocity'));return {arrangement:!!document.querySelector('vc-arrangement-overview canvas'),settings:!!document.querySelector('.part-settings-panel'),local:[...document.querySelectorAll('.score-display-header button')].some(b=>b.textContent==='Local Apply'),noStepGrid:!document.querySelector('.drum-grid')};})()`);for(const v of Object.values(edit))assert.equal(v,true);
  await capture('unified-score-edit');
  assert.equal(await evaluate(`(() => {
    const canvas = document.querySelector('vc-score-canvas canvas').getBoundingClientRect();
    return [22, 48, 98].every(midi => {
      const row = document.querySelector('.pitch-key[title="MIDI ' + midi + '"]')?.getBoundingClientRect();
      return row && row.top >= canvas.top - 1 && row.bottom <= canvas.bottom + 1;
    });
  })()`), true, 'clicking a note fits the full phrase pitch range after the editor layout settles');
  assert.equal(await evaluate(`(() => {
    const inspector = document.querySelector('#part-inspector');
    const style = getComputedStyle(inspector);
    return inspector.dataset.controlRole === 'bass'
      && style.getPropertyValue('--accent-primary').trim() === style.getPropertyValue('--role-bass').trim()
      && getComputedStyle(document.querySelector('#note-velocity')).accentColor === getComputedStyle(inspector.querySelector('.edit-mark')).color;
  })()`), true, 'phrase inspector colors follow the edited bass track rather than the remembered arpeggio');
  const shortcuts = await evaluate(`(async()=>{${waitCode}
    if (document.querySelector('#phrase-grid').getAttribute("value") !== '0.25') throw Error('Initial grid does not match snapping');
    const key=(key,extra={})=>document.querySelector('vc-score-canvas canvas').dispatchEvent(new KeyboardEvent('keydown',{key,bubbles:true,cancelable:true,...extra}));
    const apply=()=>[...document.querySelectorAll('.score-display-header button')].find(b=>b.textContent==='Apply');
    if (!apply().disabled || apply().classList.contains('active')) throw Error('Unchanged Apply must be disabled');
    const snap=document.querySelector('[aria-label="Snap to scale/key"]');
    if (snap.getAttribute('aria-pressed') !== 'true' || !document.querySelector('.pitch-key.in-scale')) throw Error('Scale snapping must start enabled and highlight rows');
    const pitchEdit=()=>document.querySelector('#note-pitch');
    pitchEdit().value='49';pitchEdit().dispatchEvent(new Event('change',{bubbles:true}));
    await new Promise(r=>requestAnimationFrame(r));
    if (pitchEdit().value !== '48' || !apply().disabled) throw Error('Pitch input must snap without creating an edit');
    snap.click();await wait(()=>snap.getAttribute('aria-pressed')==='false');
    if (document.querySelector('.pitch-key.in-scale')) throw Error('Scale highlight must turn off');
    pitchEdit().value='49';pitchEdit().dispatchEvent(new Event('change',{bubbles:true}));
    await wait(()=>pitchEdit().value==='49' && !apply().disabled && apply().classList.contains('active'));
    key('z',{ctrlKey:true});await wait(()=>pitchEdit()?.value==='48' && apply().disabled);
    snap.click();await wait(()=>snap.getAttribute('aria-pressed')==='true');
    const oldShortcut=new KeyboardEvent('keydown',{key:'t',ctrlKey:true,bubbles:true,cancelable:true});
    document.querySelector('vc-score-canvas canvas').dispatchEvent(oldShortcut);
    if (oldShortcut.defaultPrevented || document.querySelector('#phrase-transpose')) throw Error('Ctrl+T must remain available to the browser');
    key('a',{ctrlKey:true});
    key('t',{shiftKey:true});
    const input=await wait(()=>{const field=document.querySelector('#phrase-transpose');return field && document.activeElement===field ? field : null;});
    input.value='12';input.dispatchEvent(new Event('input',{bubbles:true}));
    input.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',bubbles:true,cancelable:true}));
    await wait(()=>Number(document.querySelector('#note-pitch')?.value)===60);
    key('z',{ctrlKey:true});await wait(()=>Number(document.querySelector('#note-pitch')?.value)===48);
    key('y',{ctrlKey:true});await wait(()=>Number(document.querySelector('#note-pitch')?.value)===60);
    key('z',{ctrlKey:true});await wait(()=>Number(document.querySelector('#note-pitch')?.value)===48);
    key('Delete');await wait(()=>!document.querySelector('#note-pitch'));
    key('z',{ctrlKey:true});await wait(()=>Number(document.querySelector('#note-pitch')?.value)===48);
    const pitch=document.querySelector('#note-pitch');
    const typing=new KeyboardEvent('keydown',{key:'a',ctrlKey:true,bubbles:true,cancelable:true});pitch.dispatchEvent(typing);
    const persisted=JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));
    return {typingUnaffected:!typing.defaultPrevented,globalUntouched:persisted.tracks.find(t=>t.role==='bass').editedPhrase[0].midi===48,localUntouched:!persisted.arrangement[0].trackPhrases};
  })()`);
  assert.deepEqual(shortcuts,{typingUnaffected:true,globalUntouched:true,localUntouched:true});
  assert.equal(await evaluate(`(async()=>{${waitCode}
    const change = changeControl;
    const transpose=document.querySelector('[aria-label="Transpose in semitones"]');
    const pitch=()=>document.querySelector('#note-pitch');
    const apply=()=>[...document.querySelectorAll('.score-display-header button')].find(b=>b.textContent==='Apply');
    const key=key=>document.querySelector('vc-score-canvas canvas').dispatchEvent(new KeyboardEvent('keydown',{key,ctrlKey:true,bubbles:true,cancelable:true}));
    await change(transpose,'7');await wait(()=>pitch().value==='55');
    if(!apply().disabled || !document.querySelector('.pitch-key[title="MIDI 55"].in-scale')) throw Error('Transpose must update the roll and scale without dirtying the draft');
    await change(pitch(),'57');await wait(()=>!apply().disabled);
    await change(transpose,'-5');await wait(()=>pitch().value==='45');
    key('z');await wait(()=>pitch().value==='43' && apply().disabled);
    key('y');await wait(()=>pitch().value==='45' && !apply().disabled);
    await change(transpose,'0');await wait(()=>pitch().value==='50');
    key('z');await wait(()=>pitch().value==='48' && apply().disabled);
    return true;
  })()`),true,'open phrase follows global transpose while preserving draft edits and history');
  const middleStart=await evaluate(`(()=>{const canvas=document.querySelector('vc-score-canvas canvas');const c=canvas.getBoundingClientRect();const row=document.querySelector('.pitch-key[title="MIDI 48"]').getBoundingClientRect();return {x:c.left+c.width/32,y:row.top+row.height/2};})()`);
  await command('Input.dispatchMouseEvent',{type:'mousePressed',...middleStart,button:'middle',clickCount:1});
  const middleEnd={x:middleStart.x+100,y:middleStart.y-10};
  await command('Input.dispatchMouseEvent',{type:'mouseMoved',...middleEnd,button:'middle',buttons:4});
  await command('Input.dispatchMouseEvent',{type:'mouseReleased',...middleEnd,button:'middle',clickCount:1});
  const middleGuard=await evaluate(`(async()=>{await new Promise(r=>requestAnimationFrame(r));const p=JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));return {editing:!!document.querySelector('#note-velocity'),global:p.tracks.find(t=>t.role==='bass').editedPhrase[0].velocity,local:!!p.arrangement[0].trackPhrases};})()`);
  assert.deepEqual(middleGuard,{editing:true,global:90,local:false},'MMB velocity drag ending empty must not apply');
  const local=await evaluate(`(async()=>{${waitCode}
    const transpose=document.querySelector('[aria-label="Transpose in semitones"]');
    await changeControl(transpose, '7');
    await wait(()=>document.querySelector('#note-pitch').value==='55');
    const input=document.querySelector('#note-velocity');await changeControl(input, '115');
    [...document.querySelectorAll('.score-display-header button')].find(b=>b.textContent==='Local Apply').click();
    const p=await wait(()=>{const p=JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));return p.arrangement[0].trackPhrases?p:null;});
    await changeControl(transpose, '0');
    await wait(()=>JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')).transposeSemitones===0);
    return {global:p.tracks.find(t=>t.role==='bass').editedPhrase[0].velocity,local:p.arrangement[0].trackPhrases['track-bass-1'][0].velocity,pitch:p.arrangement[0].trackPhrases['track-bass-1'][0].midi};
  })()`);assert.deepEqual(local,{global:90,local:115,pitch:48});
  const restored=await evaluate(`(async()=>{await new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)));return document.querySelector('vc-score-canvas .horizontal-scroll > div').style.width;})()`);
  assert.equal(restored,overviewViewport,'closing the editor restores the overview time zoom');
  const smart=await evaluate(`(async()=>{${waitCode}
    const read=()=>JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));
    // Let the gutter labels catch up with the restored overview before measuring the click target.
    const open=async()=>{await new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)));const canvas=document.querySelector('vc-score-canvas canvas'),c=canvas.getBoundingClientRect(),row=document.querySelector('.pitch-key[title="MIDI 48"]').getBoundingClientRect();const point={bubbles:true,button:0,clientX:c.left+5,clientY:row.top+row.height/2};canvas.dispatchEvent(new PointerEvent('pointerdown',point));canvas.dispatchEvent(new MouseEvent('click',point));await wait(()=>document.querySelector('#note-velocity'));};
    const exit=()=>{const canvas=document.querySelector('vc-score-canvas canvas'),c=canvas.getBoundingClientRect(),p={bubbles:true,cancelable:true,button:1,pointerId:7,clientX:c.left+c.width*.7,clientY:c.top+20};canvas.dispatchEvent(new PointerEvent('pointerdown',p));canvas.dispatchEvent(new PointerEvent('pointerup',p));canvas.dispatchEvent(new MouseEvent('auxclick',p));};
    await open();const input=document.querySelector('#note-velocity');await changeControl(input, '119');exit();
    await wait(()=>!document.querySelector('#note-velocity'));
    const local=read().arrangement[0].trackPhrases['track-bass-1'][0].velocity===119 && read().tracks.find(t=>t.role==='bass').editedPhrase[0].velocity===90;
    await open();const before=JSON.stringify(read());exit();await wait(()=>!document.querySelector('#note-velocity'));
    return {local,unchanged:JSON.stringify(read())===before};
  })()`);assert.deepEqual(smart,{local:true,unchanged:true});
  await command('Page.reload');
  await evaluate(`(async()=>{${waitCode}await wait(()=>document.querySelector('.score-role-controls'));document.querySelector('.score-role-controls [aria-label="Drums"]').click();await wait(()=>!document.querySelector('.drum-label'));document.querySelector('.score-role-controls [aria-label="Drums"]').click();await wait(()=>document.querySelector('.drum-label'));})()`);
  const drumPoint=await evaluate(`(()=>{const canvas=document.querySelector('vc-score-canvas canvas');canvas.scrollIntoView({block:'center'});const c=canvas.getBoundingClientRect(),key=[...document.querySelectorAll('.drum-label')].find(row=>row.textContent.trim().toUpperCase().startsWith('KICK')).getBoundingClientRect();return {x:c.left+c.width/32,y:key.top+key.height/2};})()`);
  await command('Input.dispatchMouseEvent',{type:'mousePressed',...drumPoint,button:'left',clickCount:1});await command('Input.dispatchMouseEvent',{type:'mouseReleased',...drumPoint,button:'left',clickCount:1});
  await capture('unified-drums-unselected');
  const editingDrumPoint=await evaluate(`(async()=>{${waitCode}await wait(()=>document.querySelector('#note-velocity'));if(document.querySelectorAll('.drum-label').length!==3)throw Error('All drum rows must be visible');const canvas=document.querySelector('vc-score-canvas canvas');canvas.dispatchEvent(new KeyboardEvent('keydown',{key:'a',ctrlKey:true,bubbles:true,cancelable:true}));await new Promise(r=>requestAnimationFrame(r));if(!document.querySelector('.inspector-copy').textContent.includes('3 notes'))throw Error('All drum phrases must be selectable');const c=canvas.getBoundingClientRect(),row=[...document.querySelectorAll('.drum-label')].find(row=>row.textContent.trim().toUpperCase().startsWith('KICK')).getBoundingClientRect();return {x:c.left+c.width/32,y:row.top+row.height/2};})()`);
  await capture('unified-drums-edit');
  assert.equal(await evaluate(`(async()=>{${waitCode}
    const transpose=document.querySelector('[aria-label="Transpose in semitones"]');
    const pitch=document.querySelector('#note-pitch').value;
    await changeControl(transpose, '7');
    await wait(()=>JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')).transposeSemitones===7);
    const unchanged=document.querySelector('#note-pitch').value===pitch
      && [...document.querySelectorAll('.score-display-header button')].find(b=>b.textContent==='Apply').disabled;
    await changeControl(transpose, '0');
    await wait(()=>JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')).transposeSemitones===0);
    return unchanged;
  })()`),true,'global transpose leaves drum drafts unchanged');
  await command('Input.dispatchMouseEvent',{type:'mousePressed',...editingDrumPoint,button:'middle',clickCount:1});
  await command('Input.dispatchMouseEvent',{type:'mouseMoved',x:editingDrumPoint.x,y:editingDrumPoint.y-11,button:'middle',buttons:4});
  await command('Input.dispatchMouseEvent',{type:'mouseReleased',x:editingDrumPoint.x,y:editingDrumPoint.y-11,button:'middle',clickCount:1});
  const empty=await evaluate(`(()=>{const c=document.querySelector('vc-score-canvas canvas').getBoundingClientRect();return {x:c.left+c.width*.7,y:c.top+20};})()`);
  await command('Input.dispatchMouseEvent',{type:'mousePressed',...empty,button:'middle',clickCount:1});await command('Input.dispatchMouseEvent',{type:'mouseReleased',...empty,button:'middle',clickCount:1});
  const globalDrum=await evaluate(`(async()=>{${waitCode}return await wait(()=>{const p=JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));return p.tracks.filter(t=>t.role==='drums').every(t=>t.editedPhrase[0].velocity===101);});})()`);assert.equal(globalDrum,true);
  for (const width of [1024,780]) {
    await command('Emulation.setDeviceMetricsOverride',{width,height:1000,deviceScaleFactor:1,mobile:false});
    const layout=await evaluate(`(async()=>{await new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)));const score=document.querySelector('vc-score-canvas canvas');score.scrollIntoView({block:'center'});const box=score.getBoundingClientRect();return {width:box.width,height:box.height,fits:document.documentElement.scrollWidth<=innerWidth+1};})()`);
    assert.ok(layout.width>0 && layout.height>0);assert.equal(layout.fits,true,'workspace fits at '+width+'px');
    await capture('unified-score-'+width);
  }
  // Preserve touch zoom coverage on a supported landscape viewport.
  await command('Emulation.setDeviceMetricsOverride',{width:1024,height:768,deviceScaleFactor:2,mobile:false});
  await command('Emulation.setTouchEmulationEnabled',{enabled:true,maxTouchPoints:2});
  await evaluate(`(async()=>{${waitCode}[...document.querySelectorAll('#part-inspector button')].find(b=>b.textContent.trim().startsWith('Edit notes')).click();await wait(()=>document.querySelector('#phrase-grid'));})()`);
  const touchCanvas=await evaluate(`(async()=>{await new Promise(r=>setTimeout(r,400));const canvas=document.querySelector('vc-score-canvas canvas');canvas.scrollIntoView({block:'center'});await new Promise(r=>requestAnimationFrame(r));const c=canvas.getBoundingClientRect();return {x:c.left+c.width/2,y:c.top+c.height/2,height:c.height,width:c.width,range:parseFloat(document.querySelector('vc-score-canvas .horizontal-scroll > div').style.width),project:sessionStorage.getItem('vibecomposer.project.v12'),notes:document.querySelector('.inspector-copy').textContent};})()`);
  assert.ok(touchCanvas.height>100 && touchCanvas.height<=640,'landscape canvas height is bounded');
  const touch=async(type,distance)=>command('Input.dispatchTouchEvent',{type,touchPoints:type==='touchEnd'?[]:[{id:1,x:touchCanvas.x-distance/2,y:touchCanvas.y},{id:2,x:touchCanvas.x+distance/2,y:touchCanvas.y}]});
  await touch('touchStart',40);await touch('touchMove',80);await touch('touchMove',120);await touch('touchEnd',0);
  const zoomed=await evaluate(`({range:parseFloat(document.querySelector('vc-score-canvas .horizontal-scroll > div').style.width),height:document.querySelector('vc-score-canvas canvas').getBoundingClientRect().height})`);
  assert.ok(zoomed.range>touchCanvas.range*1.5,'pinch apart zooms into the phrase');
  await touch('touchStart',120);await touch('touchMove',80);await touch('touchMove',40);await touch('touchEnd',0);
  const zoomOut=await evaluate(`({range:parseFloat(document.querySelector('vc-score-canvas .horizontal-scroll > div').style.width),height:document.querySelector('vc-score-canvas canvas').getBoundingClientRect().height,project:sessionStorage.getItem('vibecomposer.project.v12'),notes:document.querySelector('.inspector-copy').textContent})`);
  assert.ok(zoomOut.range<zoomed.range*.75,'pinch together zooms out');
  assert.ok(Math.abs(zoomOut.height-touchCanvas.height)<=1,'canvas does not grow during rendering');
  assert.equal(zoomOut.project,touchCanvas.project,'pinching does not commit note edits');
  assert.equal(zoomOut.notes,touchCanvas.notes,'pinching does not add notes');
  await command('Emulation.setTouchEmulationEnabled',{enabled:false});
  await command('Emulation.setDeviceMetricsOverride',{width:1600,height:1100,deviceScaleFactor:1,mobile:false});
  await evaluate(`(()=>{
    const p=JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));
    p.key='C';p.scale='major';p.transposeSemitones=2;p.progression=[1,4];delete p.progressionChords;
    const bass=p.tracks.find(t=>t.role==='bass');
    const base={...p.arrangement[0],measures:2};
    delete base.trackPhrases;delete base.trackPartOverrides;delete base.rolePartOverrides;
    delete base.chordDegrees;delete base.customChords;delete base.customDurations;delete base.context;
    delete base.useMelodyHarmony;delete base.swapMelody;delete base.generatedTrackIds;
    p.arrangement=[base,{...base,id:'section-harmony',chordDegrees:[2,5],customDurations:[2,6],generatedTrackIds:[bass.id]}];
    sessionStorage.setItem('vibecomposer.project.v12',JSON.stringify(p));
  })()`);
  await command('Page.reload');
  const harmonyHeader=await evaluate(`(async()=>{${waitCode}
    await wait(()=>document.querySelectorAll('.score-chord-slot').length===4);
    await wait(()=>document.querySelector('vc-score-canvas canvas'));
    const slots=[...document.querySelectorAll('.score-chord-slot')];
    return {labels:slots.map(s=>s.querySelector('.score-chord-label').textContent),
      beats:slots.map(s=>[+s.dataset.startBeat,+s.dataset.endBeat]),
      measures:[...document.querySelectorAll('.measure-range')].map(s=>s.textContent),
      leftAligned:slots.every(s=>Math.abs(s.querySelector('.score-chord-label').getBoundingClientRect().left-s.getBoundingClientRect().left-4)<.1),
      rightAligned:[...document.querySelectorAll('.score-section-measure')].every(s=>Math.abs(s.parentElement.getBoundingClientRect().right-s.getBoundingClientRect().right-4)<.1),
      ratio:slots[3].getBoundingClientRect().width/slots[2].getBoundingClientRect().width,
      colors:slots.map(s=>getComputedStyle(s.querySelector('.score-chord-label')).color)};
  })()`);
  assert.deepEqual(harmonyHeader.labels,['D','G','Em','A']);
  assert.deepEqual(harmonyHeader.beats,[[0,4],[4,8],[8,10],[10,16]]);
  assert.deepEqual(harmonyHeader.measures,['01–02','03–04']);assert.ok(Math.abs(harmonyHeader.ratio-3)<.02);
  assert.equal(harmonyHeader.leftAligned,true);assert.equal(harmonyHeader.rightAligned,true);
  assert.notEqual(harmonyHeader.colors[0],harmonyHeader.colors[2]);
  const measureFallback=await evaluate(`(async()=>{${waitCode}
    const section=document.querySelector('.score-ruler-section'),slot=section.querySelector('.score-chord-slot:last-of-type');
    const label=slot.querySelector('.score-chord-label'),range=slot.querySelector('.measure-range'),end=slot.querySelector('.measure-end');
    const sectionStyle=section.style.cssText,slotStyle=slot.style.cssText;
    const resize=width=>{section.style.width=width+'px';slot.style.left='0px';slot.style.width=width+'px';};
    resize(label.offsetWidth+end.offsetWidth+17);
    await wait(()=>section.classList.contains('measures-compact'));
    const compact=getComputedStyle(range).visibility==='hidden'&&getComputedStyle(end).visibility==='visible'
      &&end.textContent==='02'&&getComputedStyle(label).visibility==='visible';
    resize(label.offsetWidth+range.offsetWidth+20);
    await wait(()=>!section.classList.contains('measures-compact'));
    const expanded=getComputedStyle(range).visibility==='visible'&&getComputedStyle(end).visibility==='hidden';
    section.style.cssText=sectionStyle;slot.style.cssText=slotStyle;
    return {compact,expanded};
  })()`);
  assert.deepEqual(measureFallback,{compact:true,expanded:true});
  const harmonyTooltip=await evaluate(`(async()=>{${waitCode}
    for(const name of ['Melody','Chords','Arps','Drums']){
      const b=document.querySelector('.score-role-controls [aria-label="'+name+'"]');
      if(b.getAttribute('aria-pressed')==='true')b.click();
    }
    await new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)));
    const canvas=document.querySelector('vc-score-canvas canvas'),c=canvas.getBoundingClientRect();
    for(const fraction of [.505,.52,.55,.6])for(const row of document.querySelectorAll('.pitch-key')){
      const box=row.getBoundingClientRect();
      canvas.dispatchEvent(new PointerEvent('pointermove',{clientX:c.left+c.width*fraction,clientY:box.top+box.height/2}));
      await new Promise(r=>requestAnimationFrame(r));
      const text=document.querySelector('.note-tooltip')?.textContent;
      if(text?.includes('Global track [Section harmony]'))return text;
    }
    throw Error('No generated bass note showed the section harmony source');
  })()`);
  assert.match(harmonyTooltip,/Global track \[Section harmony\]/);
  await capture('section-harmony-headers');
  const scoreSettings=await evaluate(`(async()=>{${waitCode}
    const button=text=>[...document.querySelectorAll('button')].find(b=>b.textContent.trim()===text||b.getAttribute('aria-label')===text);
    button('Settings').click();(await wait(()=>button('Score'))).click();
    const toggle=await wait(()=>document.querySelector('[aria-label="Chord-based grid subdivisions"]'));
    const defaultOff=!toggle.checked;toggle.click();
    await wait(()=>JSON.parse(localStorage.getItem('vibecomposer.ui-preferences.v1')).scoreChordGrid===true);
    button('Close settings').click();
    return {defaultOff,saved:JSON.parse(localStorage.getItem('vibecomposer.ui-preferences.v1')).scoreChordGrid,
      border:getComputedStyle(document.querySelector('.score-chord-label')).borderTopWidth,
      sectionSeparator:getComputedStyle(document.querySelector('.score-header-separator.section-end')).width,
      chordSeparator:getComputedStyle(document.querySelector('.score-header-separator')).width};
  })()`);
  assert.deepEqual(scoreSettings,{defaultOff:true,saved:true,border:'0px',sectionSeparator:'1px',chordSeparator:'1px'});
  for(const width of [1600,1023,781]) {
    await command('Emulation.setDeviceMetricsOverride',{width,height:1100,deviceScaleFactor:1,mobile:false});
    const alignment=await evaluate(`(async()=>{${waitCode}
      const settle=()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)));
      await settle();
      const canvas=document.querySelector('vc-score-canvas canvas'),h=document.querySelector('.horizontal-scroll');
      const check=()=>{
        const bounds=canvas.getBoundingClientRect(),content=parseFloat(document.querySelector('.horizontal-scroll > div').style.width);
        return [...document.querySelectorAll('.score-header-separator')].map(line=>{
          const expected=Math.floor(+line.dataset.beat/16*content-h.scrollLeft);
          return Math.abs(line.getBoundingClientRect().left-bounds.left-expected);
        });
      };
      const c=canvas.getBoundingClientRect();
      const wheel=deltaY=>canvas.dispatchEvent(new WheelEvent('wheel',{bubbles:true,cancelable:true,ctrlKey:true,deltaY,clientX:c.left,clientY:c.top}));
      wheel(10000);await settle();const fit=check();
      wheel(-350);await settle();h.scrollLeft=37;await settle();const zoomed=check();
      wheel(10000);await settle();
      return {fit,zoomed};
    })()`);
    assert.ok([...alignment.fit,...alignment.zoomed].every(error=>error<.02),'pixel-aligned separators at '+width+'px: '+JSON.stringify(alignment));
  }
  await command('Emulation.setDeviceMetricsOverride',{width:1600,height:1100,deviceScaleFactor:1,mobile:false});
  await evaluate(`(()=>{
    const p=JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));
    p.arrangement=[{...p.arrangement[0],measures:1,customDurations:[.25],chordDegrees:[1]},p.arrangement[1]];
    sessionStorage.setItem('vibecomposer.project.v12',JSON.stringify(p));
  })()`);
  await command('Page.reload');
  const shortSection=await evaluate(`(async()=>{${waitCode}
    const section=await wait(()=>document.querySelector('.score-ruler-section.chords-hidden'));
    const label=section.querySelector('.score-chord-label');
    const initial={hidden:getComputedStyle(label).visibility==='hidden',measure:section.querySelector('.measure-end').textContent,
      separator:getComputedStyle(section.querySelector('.score-header-separator')).backgroundColor,
      saved:JSON.parse(localStorage.getItem('vibecomposer.ui-preferences.v1')).scoreChordGrid};
    const canvas=await wait(()=>document.querySelector('vc-score-canvas canvas')),c=canvas.getBoundingClientRect();
    canvas.dispatchEvent(new WheelEvent('wheel',{bubbles:true,cancelable:true,ctrlKey:true,deltaY:-1000,clientX:c.left,clientY:c.top}));
    await wait(()=>!section.classList.contains('chords-hidden'));
    return {...initial,reappeared:getComputedStyle(label).visibility==='visible'};
  })()`);
  assert.equal(shortSection.hidden,true);assert.equal(shortSection.measure,'01');assert.equal(shortSection.saved,true);
  assert.equal(shortSection.reappeared,true);assert.notEqual(shortSection.separator,'rgba(0, 0, 0, 0)');
  await capture('score-chord-grid-short-sections');
 } finally {
    await closeBrowser({ child, server, socket });
  }
});
