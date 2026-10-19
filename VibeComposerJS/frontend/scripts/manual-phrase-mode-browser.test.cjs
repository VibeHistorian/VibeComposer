const assert=require('node:assert/strict');
const fs=require('node:fs'); const path=require('node:path'); const http=require('node:http');
const {spawn}=require('node:child_process'); const test=require('node:test');
const browser=['C:/Program Files/Google/Chrome/Application/chrome.exe','C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'].find(p=>fs.existsSync(p));
test('manual Mode behavior setting changes matching phrases and persists in the production browser', {skip:!browser,timeout:60000},async()=>{
 const root=path.resolve(__dirname,'../dist/browser');
 const server=http.createServer((req,res)=>{const p=path.resolve(root, '.'+(req.url==='/'?'/index.html':req.url));if(path.relative(root,p).startsWith('..')||!fs.existsSync(p)){res.writeHead(404);res.end();return;}res.setHeader('Content-Type',p.endsWith('.js')?'text/javascript':p.endsWith('.css')?'text/css':'text/html');fs.createReadStream(p).pipe(res);});
 await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const profile=fs.mkdtempSync(path.resolve(__dirname,'../.angular/manual-mode-'));
 const child=spawn(browser,['--headless','--disable-background-timer-throttling','--disable-renderer-backgrounding','--disable-gpu','--no-first-run','--disable-background-networking','--remote-debugging-port=0','--window-size=1600,1100','--user-data-dir='+profile,'http://127.0.0.1:'+server.address().port]);
 let socket;
 try {
  let port; for(let i=0;i<100&&!port;i++){try{port=+fs.readFileSync(path.join(profile,'DevToolsActivePort'),'utf8').split('\n')[0];}catch{}if(!port)await new Promise(r=>setTimeout(r,50));}
  assert.ok(port);
  const target=(await(await fetch('http://127.0.0.1:'+port+'/json/list')).json()).find(t=>t.type==='page');
  socket=new WebSocket(target.webSocketDebuggerUrl);await new Promise((r,j)=>{socket.addEventListener('open',r,{once:true});socket.addEventListener('error',j,{once:true});});
  let id=0;const command=(method,params={})=>new Promise((r,j)=>{const n=++id;const timer=setTimeout(()=>j(Error('CDP timeout: '+method)),20000);const listener=e=>{const m=JSON.parse(e.data);if(m.id!==n)return;clearTimeout(timer);socket.removeEventListener('message',listener);m.error?j(Error(JSON.stringify(m.error))):r(m.result);};socket.addEventListener('message',listener);socket.send(JSON.stringify({id:n,method,params}));});
  const evaluate=async expression=>{let result;for(let retry=0;retry<3;retry++){try{result=await command('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});break;}catch(error){if(!error.message.includes('Execution context was destroyed'))throw error;await new Promise(r=>setTimeout(r,500));}}assert.equal(result.exceptionDetails,undefined,JSON.stringify(result));return result.result.value;};
  const waitCode=`const wait=async f=>{for(let i=0;i<200;i++){const v=f();if(v)return v;await new Promise(r=>setTimeout(r,25));}throw Error('Timed out waiting for score');};`;
  await command('Page.enable');
  const reload = async () => {
    const loaded = new Promise(resolve => {
      const listener = event => { if (JSON.parse(event.data).method !== 'Page.loadEventFired') return;
        socket.removeEventListener('message', listener); resolve(); };
      socket.addEventListener('message', listener);
    });
    await command('Page.reload'); await loaded;
  };
  await reload();

  const helpers = `
    const read=()=>JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));
    const change=(input,value)=>{input.value=value;input.dispatchEvent(new Event('change',{bubbles:true}));};
    const settings=async()=>{
      (await wait(()=>document.querySelector('[aria-label="Settings"]'))).click();
      (await wait(()=>document.querySelector('[aria-label="Behavior General"]'))).click();
      return await wait(()=>document.querySelector('[aria-label="Manual phrases follow global Mode"]'));
    };
    const close=()=>document.querySelector('[aria-label="Close settings"]').click();
  `;
  await evaluate(`(async()=>{${waitCode}${helpers}
    const seed=await wait(()=>document.querySelector('[aria-label="Generation seed"]'));change(seed,'43');
    const project=await wait(read);project.scale='major';project.key='D';project.transposeSemitones=7;
    const notes=pitches=>pitches.map((midi,i)=>({id:'manual-'+i,midi,startBeat:i,durationBeats:.5,velocity:90}));
    project.tracks.find(t=>t.role==='bass').editedPhrase=notes([62,66,69,73]);
    project.tracks.find(t=>t.role==='chords').editedPhrase=notes([62,63,66]);
    project.tracks.find(t=>t.role==='drums').editedPhrase=notes([38]);
    sessionStorage.setItem('vibecomposer.project.v12',JSON.stringify(project));
  })()`);
  await reload();
  const result=await evaluate(`(async()=>{${waitCode}${helpers}
    const checkbox=await settings(),defaultOn=checkbox.checked;close();
    const mode=await wait(()=>document.querySelector('[aria-label="Mode"]'));
    change(mode,'natural-minor');await wait(()=>read().scale==='natural-minor');
    const project=read();
    const converted=JSON.stringify(project.tracks.find(t=>t.role==='bass').editedPhrase.map(n=>n.midi))==='[62,65,69,72]';
    const chromaticPreserved=JSON.stringify(project.tracks.find(t=>t.role==='chords').editedPhrase.map(n=>n.midi))==='[62,63,66]';
    const drumsPreserved=project.tracks.find(t=>t.role==='drums').editedPhrase[0].midi===38;
    const off=await settings();off.click();close();
    change(mode,'major');await wait(()=>read().scale==='major');
    const disabledPreserved=JSON.stringify(read().tracks.find(t=>t.role==='bass').editedPhrase.map(n=>n.midi))==='[62,65,69,72]';
    return {defaultOn,converted,chromaticPreserved,drumsPreserved,disabledPreserved};
  })()`);
  for(const [key,value] of Object.entries(result))assert.equal(value,true,key);
  await reload();
  assert.equal(await evaluate(`(async()=>{${waitCode}${helpers}return (await settings()).checked;})()`),false,'explicit off survives reload');
 } finally {socket?.close();child.kill();server.close();await new Promise(r=>child.exitCode!==null||child.signalCode!==null?r():child.once('exit',r));await fs.promises.rm(profile,{recursive:true,force:true,maxRetries:10,retryDelay:150});}
});
