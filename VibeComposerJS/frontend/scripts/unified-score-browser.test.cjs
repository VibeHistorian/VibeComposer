const assert=require('node:assert/strict');
const fs=require('node:fs'); const path=require('node:path'); const http=require('node:http');
const {spawn}=require('node:child_process'); const test=require('node:test');
const browser=['C:/Program Files/Google/Chrome/Application/chrome.exe','C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'].find(p=>fs.existsSync(p));
test('unified score keeps the workspace, filters drums and applies contextual harmonic and drum edits', {skip:!browser,timeout:60000},async()=>{
 const root=path.resolve(__dirname,'../dist/browser');
 const server=http.createServer((req,res)=>{const p=path.resolve(root, '.'+(req.url==='/'?'/index.html':req.url));if(path.relative(root,p).startsWith('..')||!fs.existsSync(p)){res.writeHead(404);res.end();return;}res.setHeader('Content-Type',p.endsWith('.js')?'text/javascript':p.endsWith('.css')?'text/css':'text/html');fs.createReadStream(p).pipe(res);});
 await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const profile=fs.mkdtempSync(path.resolve(__dirname,'../.angular/unified-score-'));
 const child=spawn(browser,['--headless','--disable-gpu','--no-first-run','--disable-background-networking','--remote-debugging-port=0','--window-size=1600,1100','--user-data-dir='+profile,'http://127.0.0.1:'+server.address().port]);
 let socket;
 try {
  let port; for(let i=0;i<100&&!port;i++){try{port=+fs.readFileSync(path.join(profile,'DevToolsActivePort'),'utf8').split('\n')[0];}catch{}if(!port)await new Promise(r=>setTimeout(r,50));}
  assert.ok(port);
  const target=(await(await fetch('http://127.0.0.1:'+port+'/json/list')).json()).find(t=>t.type==='page');
  socket=new WebSocket(target.webSocketDebuggerUrl);await new Promise((r,j)=>{socket.addEventListener('open',r,{once:true});socket.addEventListener('error',j,{once:true});});
  let id=0;const command=(method,params={})=>new Promise((r,j)=>{const n=++id;const listener=e=>{const m=JSON.parse(e.data);if(m.id!==n)return;socket.removeEventListener('message',listener);m.error?j(Error(JSON.stringify(m.error))):r(m.result);};socket.addEventListener('message',listener);socket.send(JSON.stringify({id:n,method,params}));});
  const evaluate=async expression=>{let result;for(let retry=0;retry<3;retry++){try{result=await command('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});break;}catch(error){if(!error.message.includes('Execution context was destroyed'))throw error;await new Promise(r=>setTimeout(r,500));}}assert.equal(result.exceptionDetails,undefined,JSON.stringify(result));return result.result.value;};
  const waitCode=`const wait=async f=>{for(let i=0;i<200;i++){const v=f();if(v)return v;await new Promise(r=>setTimeout(r,25));}throw Error('Timed out waiting for score');};`;
  await evaluate(`(async()=>{${waitCode}await wait(()=>document.querySelector('vc-score-canvas canvas'));const seed=document.querySelector('[aria-label="Generation seed"]');seed.value='43';seed.dispatchEvent(new Event('change',{bubbles:true}));const p=await wait(()=>JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')));p.arrangement=[{...p.arrangement[0],measures:4,trackPresence:Object.fromEntries(p.tracks.map(t=>[t.id,true]))}];for(const t of p.tracks)t.editedPhrase=[{id:t.id+'-fixture',midi:t.role==='drums'?36:t.role==='bass'?48:60,startBeat:0,durationBeats:1,velocity:90}];const drum=p.tracks.find(t=>t.role==='drums');for(const [index,pitch] of [[2,38],[3,42]])p.tracks.push({...drum,id:'track-drums-'+index,name:'D'+index,generatorSettings:{...drum.generatorSettings,pitch},editedPhrase:[{id:'shared-drum',midi:pitch,startBeat:0,durationBeats:1,velocity:90}]});sessionStorage.setItem('vibecomposer.project.v12',JSON.stringify(p));return true;})()`);
  await command('Page.reload');
  const initial=await evaluate(`(async()=>{${waitCode}await wait(()=>document.querySelector('.drum-label'));return {labels:document.querySelectorAll('.pitch-key').length,drums:document.querySelectorAll('.drum-label').length,arrangement:!!document.querySelector('vc-arrangement-overview canvas'),velocity:!!document.querySelector('.velocity-label')};})()`);
  assert.ok(initial.labels>=17);assert.equal(initial.drums,3);assert.equal(initial.arrangement,true);assert.equal(initial.velocity,false);
  const capture=async name=>{const png=await command('Page.captureScreenshot',{format:'png'});fs.writeFileSync(path.resolve(__dirname,'../.angular/'+name+'.png'),Buffer.from(png.data,'base64'));};
  await capture('unified-score');
  await evaluate(`(async()=>{${waitCode}document.querySelector('.track-group.arpeggio .track-select').click();await wait(()=>document.querySelector('#part-inspector').dataset.controlRole==='arpeggio');})()`);
  const filters=await evaluate(`(async()=>{${waitCode}const b=[...document.querySelectorAll('.score-display-header button')].find(b=>b.textContent.trim()==='Drums');const before=document.querySelector('vc-score-canvas canvas');b.click();await wait(()=>!document.querySelector('.drum-label'));const mix=[...document.querySelectorAll('.score-display-header button')].find(b=>b.textContent.includes('Sync'));mix.click();await wait(()=>JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')).tracks.find(t=>t.role==='drums').mix.muted);return document.querySelector('vc-score-canvas canvas')===before;})()`);assert.equal(filters,true);
  const overviewViewport=await evaluate(`(()=>{const canvas=document.querySelector('vc-score-canvas canvas');const c=canvas.getBoundingClientRect();canvas.dispatchEvent(new WheelEvent('wheel',{deltaY:-346.57,ctrlKey:true,clientX:c.left,clientY:c.top+10,bubbles:true,cancelable:true}));const content=document.querySelector('vc-score-canvas .horizontal-scroll > div');return content.style.width;})()`);
  const point=await evaluate(`(()=>{const canvas=document.querySelector('vc-score-canvas canvas');canvas.scrollIntoView({block:'center'});const c=canvas.getBoundingClientRect(),key=document.querySelector('.pitch-key[title="MIDI 48"]').getBoundingClientRect();return {x:c.left+c.width/32,y:key.top+key.height/2};})()`);
  await command('Input.dispatchMouseEvent',{type:'mousePressed',...point,button:'left',clickCount:1});await command('Input.dispatchMouseEvent',{type:'mouseReleased',...point,button:'left',clickCount:1});
  const edit=await evaluate(`(async()=>{${waitCode}await wait(()=>document.querySelector('#note-velocity'));return {arrangement:!!document.querySelector('vc-arrangement-overview canvas'),settings:!!document.querySelector('.part-settings-panel'),local:[...document.querySelectorAll('.score-display-header button')].some(b=>b.textContent==='Local Apply'),noStepGrid:!document.querySelector('.drum-grid')};})()`);for(const v of Object.values(edit))assert.equal(v,true);
  await capture('unified-score-edit');
  assert.equal(await evaluate(`(() => {
    const inspector = document.querySelector('#part-inspector');
    const style = getComputedStyle(inspector);
    return inspector.dataset.controlRole === 'bass'
      && style.getPropertyValue('--accent-primary').trim() === style.getPropertyValue('--role-bass').trim()
      && getComputedStyle(document.querySelector('#note-velocity')).accentColor === getComputedStyle(inspector.querySelector('.edit-mark')).color;
  })()`), true, 'phrase inspector colors follow the edited bass track rather than the remembered arpeggio');
  const shortcuts = await evaluate(`(async()=>{${waitCode}
    if (document.querySelector('#phrase-grid').value !== '0.25') throw Error('Initial grid does not match snapping');
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
  const middleStart=await evaluate(`(()=>{const canvas=document.querySelector('vc-score-canvas canvas');const c=canvas.getBoundingClientRect();const row=document.querySelector('.pitch-key[title="MIDI 48"]').getBoundingClientRect();return {x:c.left+c.width/32,y:row.top+row.height/2};})()`);
  await command('Input.dispatchMouseEvent',{type:'mousePressed',...middleStart,button:'middle',clickCount:1});
  const middleEnd={x:middleStart.x+100,y:middleStart.y-10};
  await command('Input.dispatchMouseEvent',{type:'mouseMoved',...middleEnd,button:'middle',buttons:4});
  await command('Input.dispatchMouseEvent',{type:'mouseReleased',...middleEnd,button:'middle',clickCount:1});
  const middleGuard=await evaluate(`(async()=>{await new Promise(r=>requestAnimationFrame(r));const p=JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));return {editing:!!document.querySelector('#note-velocity'),global:p.tracks.find(t=>t.role==='bass').editedPhrase[0].velocity,local:!!p.arrangement[0].trackPhrases};})()`);
  assert.deepEqual(middleGuard,{editing:true,global:90,local:false},'MMB velocity drag ending empty must not apply');
  const local=await evaluate(`(async()=>{${waitCode}const input=document.querySelector('#note-velocity');input.value='115';input.dispatchEvent(new Event('change',{bubbles:true}));[...document.querySelectorAll('.score-display-header button')].find(b=>b.textContent==='Local Apply').click();const p=await wait(()=>{const p=JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));return p.arrangement[0].trackPhrases?p:null;});return {global:p.tracks.find(t=>t.role==='bass').editedPhrase[0].velocity,local:p.arrangement[0].trackPhrases['track-bass-1'][0].velocity};})()`);assert.deepEqual(local,{global:90,local:115});
  const restored=await evaluate(`(async()=>{await new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)));return document.querySelector('vc-score-canvas .horizontal-scroll > div').style.width;})()`);
  assert.equal(restored,overviewViewport,'closing the editor restores the overview time zoom');
  const smart=await evaluate(`(async()=>{${waitCode}
    const read=()=>JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));
    const open=async()=>{const canvas=document.querySelector('vc-score-canvas canvas'),c=canvas.getBoundingClientRect(),row=document.querySelector('.pitch-key[title="MIDI 48"]').getBoundingClientRect();const point={bubbles:true,button:0,clientX:c.left+5,clientY:row.top+row.height/2};canvas.dispatchEvent(new PointerEvent('pointerdown',point));canvas.dispatchEvent(new MouseEvent('click',point));await wait(()=>document.querySelector('#note-velocity'));};
    const exit=()=>{const canvas=document.querySelector('vc-score-canvas canvas'),c=canvas.getBoundingClientRect(),p={bubbles:true,cancelable:true,button:1,pointerId:7,clientX:c.left+c.width*.7,clientY:c.top+20};canvas.dispatchEvent(new PointerEvent('pointerdown',p));canvas.dispatchEvent(new PointerEvent('pointerup',p));canvas.dispatchEvent(new MouseEvent('auxclick',p));};
    await open();const input=document.querySelector('#note-velocity');input.value='119';input.dispatchEvent(new Event('change',{bubbles:true}));exit();
    await wait(()=>!document.querySelector('#note-velocity'));
    const local=read().arrangement[0].trackPhrases['track-bass-1'][0].velocity===119 && read().tracks.find(t=>t.role==='bass').editedPhrase[0].velocity===90;
    await open();const before=JSON.stringify(read());exit();await wait(()=>!document.querySelector('#note-velocity'));
    return {local,unchanged:JSON.stringify(read())===before};
  })()`);assert.deepEqual(smart,{local:true,unchanged:true});
  await command('Page.reload');
  await evaluate(`(async()=>{${waitCode}await wait(()=>document.querySelector('.score-display-header'));[...document.querySelectorAll('.score-display-header button')].find(b=>b.textContent.trim()==='Drums').click();await wait(()=>!document.querySelector('.drum-label'));[...document.querySelectorAll('.score-display-header button')].find(b=>b.textContent.trim()==='Drums').click();await wait(()=>document.querySelector('.drum-label'));})()`);
  const drumPoint=await evaluate(`(()=>{const canvas=document.querySelector('vc-score-canvas canvas');canvas.scrollIntoView({block:'center'});const c=canvas.getBoundingClientRect(),key=[...document.querySelectorAll('.drum-label')].find(row=>row.textContent.trim()==='Kick').getBoundingClientRect();return {x:c.left+c.width/32,y:key.top+key.height/2};})()`);
  await command('Input.dispatchMouseEvent',{type:'mousePressed',...drumPoint,button:'left',clickCount:1});await command('Input.dispatchMouseEvent',{type:'mouseReleased',...drumPoint,button:'left',clickCount:1});
  await capture('unified-drums-unselected');
  const editingDrumPoint=await evaluate(`(async()=>{${waitCode}await wait(()=>document.querySelector('#note-velocity'));if(document.querySelectorAll('.drum-label').length!==3)throw Error('All drum rows must be visible');const canvas=document.querySelector('vc-score-canvas canvas');canvas.dispatchEvent(new KeyboardEvent('keydown',{key:'a',ctrlKey:true,bubbles:true,cancelable:true}));await new Promise(r=>requestAnimationFrame(r));if(!document.querySelector('.inspector-copy').textContent.includes('3 notes'))throw Error('All drum phrases must be selectable');const c=canvas.getBoundingClientRect(),row=[...document.querySelectorAll('.drum-label')].find(row=>row.textContent.trim()==='Kick').getBoundingClientRect();return {x:c.left+c.width/32,y:row.top+row.height/2};})()`);
  await capture('unified-drums-edit');
  await command('Input.dispatchMouseEvent',{type:'mousePressed',...editingDrumPoint,button:'middle',clickCount:1});
  await command('Input.dispatchMouseEvent',{type:'mouseMoved',x:editingDrumPoint.x,y:editingDrumPoint.y-11,button:'middle',buttons:4});
  await command('Input.dispatchMouseEvent',{type:'mouseReleased',x:editingDrumPoint.x,y:editingDrumPoint.y-11,button:'middle',clickCount:1});
  const empty=await evaluate(`(()=>{const c=document.querySelector('vc-score-canvas canvas').getBoundingClientRect();return {x:c.left+c.width*.7,y:c.top+20};})()`);
  await command('Input.dispatchMouseEvent',{type:'mousePressed',...empty,button:'middle',clickCount:1});await command('Input.dispatchMouseEvent',{type:'mouseReleased',...empty,button:'middle',clickCount:1});
  const globalDrum=await evaluate(`(async()=>{${waitCode}return await wait(()=>{const p=JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));return p.tracks.filter(t=>t.role==='drums').every(t=>t.editedPhrase[0].velocity===101);});})()`);assert.equal(globalDrum,true);
  for (const width of [780,390]) {
    await command('Emulation.setDeviceMetricsOverride',{width,height:1000,deviceScaleFactor:1,mobile:false});
    const layout=await evaluate(`(async()=>{await new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)));const score=document.querySelector('vc-score-canvas canvas');score.scrollIntoView({block:'center'});const box=score.getBoundingClientRect();return {width:box.width,height:box.height,fits:document.documentElement.scrollWidth<=innerWidth+1};})()`);
    assert.ok(layout.width>0 && layout.height>0);assert.equal(layout.fits,true,'workspace fits at '+width+'px');
    await capture('unified-score-'+width);
  }
 } finally {socket?.close();child.kill();server.close();await new Promise(r=>child.exitCode!==null||child.signalCode!==null?r():child.once('exit',r));await fs.promises.rm(profile,{recursive:true,force:true,maxRetries:10,retryDelay:150});}
});
