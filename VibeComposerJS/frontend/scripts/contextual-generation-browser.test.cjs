const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const { spawn } = require('node:child_process');
const test = require('node:test');
const browser = process.env.OVERVIEW_TEST_BROWSER || ['C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', '/usr/bin/chromium'].find(file => fs.existsSync(file));

for (const width of [1440,780]) test(`contextual generation settings and visibility at ${width}px`, { skip: !browser }, async () => {
  const root = path.resolve(__dirname,'../dist/browser'), index = fs.readFileSync(path.join(root,'index.html'),'utf8');
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
  const result = new Promise((done,fail)=>{resolve=done;reject=fail;}); result.catch(()=>{});
  const server = http.createServer((request,response)=>{
    if(request.url==='/result') {let body='';request.on('data',chunk=>body+=chunk);request.on('end',()=>{response.end('OK');resolve(JSON.parse(body));});return;}
    if(request.url==='/') {response.setHeader('Content-Type','text/html');response.end(index.replace('</body>',script+'</body>'));return;}
    const file=path.resolve(root,'.'+new URL(request.url,'http://localhost').pathname);
    if(path.relative(root,file).startsWith('..')||!fs.existsSync(file)||!fs.statSync(file).isFile()){response.writeHead(404);response.end();return;}
    response.setHeader('Content-Type',file.endsWith('.js')?'text/javascript':file.endsWith('.css')?'text/css':'application/octet-stream');fs.createReadStream(file).pipe(response);
  });
  await new Promise(done=>server.listen(0,'127.0.0.1',done));
  const profile=fs.mkdtempSync(path.resolve(__dirname,'../.angular/contextual-generation-browser-'));
  const child=spawn(browser,['--headless','--disable-gpu','--no-first-run','--disable-background-networking','--no-default-browser-check',
    '--disable-background-timer-throttling','--disable-renderer-backgrounding','--remote-debugging-port=0','--window-size='+width+',1100',
    '--user-data-dir='+profile,'http://127.0.0.1:'+server.address().port+'/'],{stdio:['ignore','ignore','pipe']});
  let errors='',socket;child.stderr.on('data',chunk=>errors=(errors+chunk).slice(-1500));
  child.on('error',reject);child.on('exit',code=>reject(new Error('Browser exited '+code+': '+errors)));
  const timer=setTimeout(()=>reject(new Error('Browser timed out: '+errors)),45000);
  try {
    let port;
    for(let i=0;!port&&i<100;i++) {
      try {port=Number(fs.readFileSync(path.join(profile,'DevToolsActivePort'),'utf8').split('\n')[0]);}
      catch(error){if(!['ENOENT','EBUSY','EPERM'].includes(error.code))throw error;}
      if(!port)await new Promise(done=>setTimeout(done,50));
    }
    if(!port)throw new Error('Browser debugging port unavailable: '+errors);
    const target=(await(await fetch('http://127.0.0.1:'+port+'/json/list',{signal:AbortSignal.timeout(5000)})).json()).find(t=>t.type==='page');
    socket=new WebSocket(target.webSocketDebuggerUrl);
    await new Promise((done,fail)=>{socket.addEventListener('open',done,{once:true});socket.addEventListener('error',fail,{once:true});});
    await new Promise((done,fail)=>{
      const timeout=setTimeout(()=>fail(new Error('Viewport command timed out')),10000);
      socket.addEventListener('message',event=>{const message=JSON.parse(event.data);if(message.id===1){clearTimeout(timeout);message.error?fail(new Error(JSON.stringify(message.error))):done();}});
      socket.send(JSON.stringify({id:1,method:'Emulation.setDeviceMetricsOverride',params:{width,height:1100,deviceScaleFactor:1,mobile:false}}));
    });
    const actual=await result;assert.equal(actual.error,undefined,JSON.stringify(actual));assert.equal(actual.ok,true);
  } finally {
    socket?.close();clearTimeout(timer);child.kill();server.close();
    await new Promise(done=>child.exitCode!==null||child.signalCode!==null?done():child.once('exit',done));
    if(!path.resolve(profile).startsWith(path.resolve(__dirname,'../.angular')+path.sep))throw new Error('Browser profile outside test workspace');
    await fs.promises.rm(profile,{recursive:true,force:true,maxRetries:10,retryDelay:150});
  }
});
