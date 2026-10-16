const assert = require('node:assert/strict');
const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');
const { spawn } = require('node:child_process');
const test = require('node:test');
const { DEFAULT_PROJECT } = require('./music-loader.cjs').loadMusic('../project/project.model.ts');
const browser = process.env.OVERVIEW_TEST_BROWSER || [
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', '/usr/bin/chromium',
].find(file => fs.existsSync(file));

for (const width of [1440, 780]) test(`production export menu and offline Opus download at ${width}px`, { skip: !browser }, async () => {
  const root = path.resolve(__dirname, '../dist/browser');
  const trackId = 'track-bass-1';
  const presence = Object.fromEntries(DEFAULT_PROJECT.tracks.map(track => [track.id, true]));
  const project = { ...DEFAULT_PROJECT, name: 'Export: test', tracks: DEFAULT_PROJECT.tracks.map(track => ({
    ...track, mix: { ...track.mix, program: 19, solo: track.id === trackId, panPercent: -100 },
  })), arrangement: [
    { id: 'first', type: 'VERSE1', measures: 1, trackPresence: presence, context: { tempoBpm: 120 },
      trackPhrases: { [trackId]: [{ id: 'sustain', midi: 48, startBeat: .5, durationBeats: 5, velocity: 100 }] } },
    { id: 'second', type: 'VERSE2', measures: 1, trackPresence: presence, context: { tempoBpm: 60 }, trackPhrases: { [trackId]: [] } },
  ] };
  const script = `<script>
    sessionStorage.setItem('vibecomposer.project.v12', ${JSON.stringify(JSON.stringify(project))});
    (async () => {
      const wait = async (read, label) => {
        for (let i=0;i<400;i++) { const value=read(); if(value) return value; await new Promise(r=>setTimeout(r,25)); }
        throw Error('Timed out: '+label);
      };
      const ensure = (value,label) => { if(!value) throw Error(label); };
      const downloads = [];
      const unhandledRejections = [];
      addEventListener('unhandledrejection', event => unhandledRejections.push(String(event.reason)));
      let offlineResumes = 0;
      const nativeOfflineResume = OfflineAudioContext.prototype.resume;
      OfflineAudioContext.prototype.resume = function(...args) {
        offlineResumes++;
        return nativeOfflineResume.apply(this, args);
      };
      HTMLAnchorElement.prototype.click = function() { downloads.push({ name: this.download, blob: fetch(this.href).then(r=>r.blob()) }); };
      const menu = () => document.querySelector('[role="menu"][aria-label="Export"]');
      const item = label => [...menu().querySelectorAll('button')].find(button=>button.textContent.trim()===label);
      const key = (target, value) => target.dispatchEvent(new KeyboardEvent('keydown', {
        key:value, keyCode: {ArrowDown:40,Escape:27}[value], bubbles:true,cancelable:true,
      }));
      const NativeAudioContext = window.AudioContext;
      let realtimeContexts = 0;
      window.AudioContext = class { constructor() { realtimeContexts++; throw Error('Export opened a realtime context'); } };
      try {
        const trigger = await wait(()=>document.querySelector('[title="Export"]'), 'export trigger');
        trigger.focus(); key(trigger,'ArrowDown');
        await wait(()=>menu() && menu().contains(document.activeElement),'keyboard menu focus');
        ensure(menu().querySelectorAll('button').length===3,'three export actions');
        const rect=menu().getBoundingClientRect();
        ensure(rect.left>=0 && rect.right<=innerWidth && rect.top>=0 && rect.bottom<=innerHeight,'menu inside viewport');
        key(document.activeElement,'ArrowDown');
        ensure(document.activeElement===item('Export Project'),'arrow navigation');
        key(document.activeElement,'Escape');
        await wait(()=>!menu() && document.activeElement===trigger,'Escape restores focus');
        trigger.click(); await wait(menu,'project menu'); item('Export Project').click();
        await wait(()=>downloads.length===1,'project download');
        const saved=JSON.parse(await (await downloads[0].blob).text());
        ensure(saved.name==='Export: test' && downloads[0].name==='Export- test.vibecomposer.json','project export preserved');
        trigger.click(); await wait(menu,'MIDI menu'); item('Export MIDI').click();
        await wait(()=>downloads.length===2,'MIDI download');
        const midi=await (await downloads[1].blob).arrayBuffer();
        ensure(new TextDecoder().decode(midi.slice(0,4))==='MThd' && downloads[1].name==='Export- test.mid','MIDI export');
        trigger.click(); await wait(menu,'audio menu'); item('Export Audio').click();
        trigger.click(); await wait(menu,'busy menu');
        ensure([...menu().querySelectorAll('button')].some(b=>b.disabled && b.textContent.includes('Audio')),'duplicate audio export disabled');
        key(document.activeElement,'Escape');
        await wait(()=>downloads.length===3,'audio download');
        ensure(realtimeContexts===0,'offline export never opens audio device');
        const audio=await downloads[2].blob;
        ensure(downloads[2].name==='Export- test.opus' && audio.type.includes('audio/ogg'),'Opus filename and MIME');
        const bytes=await audio.arrayBuffer();
        ensure(new TextDecoder().decode(bytes.slice(0,4))==='OggS','Ogg container');
        const context=new NativeAudioContext({sampleRate:48000});
        try {
          const decoded=await context.decodeAudioData(bytes);
          ensure(decoded.numberOfChannels===2 && decoded.duration>6 && decoded.duration<6.1,'full tempo-mapped arrangement including silence');
          const rms=(channel,from,to)=> {
            const data=decoded.getChannelData(channel).subarray(from*48000,to*48000);
            return Math.sqrt(data.reduce((sum,v)=>sum+v*v,0)/data.length);
          };
          ensure(rms(0,1,1.5)>.001 && rms(0,3,3.25)>.001,'sustain crosses section tempo change');
          ensure(rms(1,1,1.5)<.00001,'pan and solo exclude other tracks');
          ensure(rms(0,5,5.5)<.00001,'voice release ends and trailing section stays silent');
        } finally { await context.close(); }
        ensure(offlineResumes===0,'offline export must start rendering without calling resume');
        ensure(unhandledRejections.length===0,'unhandled export rejections: '+unhandledRejections.join('; '));
        // Mute every track; report failure and allow retry.
        for (const label of ['M1','B1','C1','A1','D1']) {
          const mute=document.querySelector('[aria-label="Mute '+label+'"]');
          ensure(mute,'track mute control '+label); mute.click();
          await wait(()=>document.querySelector('[aria-label="Unmute '+label+'"]'),'track muted '+label);
        }
        trigger.click(); await wait(menu,'silent audio menu'); item('Export Audio').click();
        await wait(()=>document.querySelector('.project-message')?.textContent.includes('no audible notes'),'export failure feedback');
        ensure(downloads.length===3,'failed export does not download');
        trigger.click(); await wait(menu,'retry menu'); ensure(!item('Export Audio').disabled,'failure clears busy state');
        await fetch('/result',{method:'POST',body:JSON.stringify({ok:true})});
      } catch(error) { await fetch('/result',{method:'POST',body:JSON.stringify({error:error.stack,
        notice:document.querySelector('.project-message')?.textContent,downloads:downloads.map(d=>d.name),
        project:sessionStorage.getItem('vibecomposer.project.v12')})}); }
      finally { window.AudioContext=NativeAudioContext; }
    })();
  </script>`;
  const index = fs.readFileSync(path.join(root,'index.html'),'utf8').replace('</body>',script+'</body>');
  let resolve, reject;
  const result = new Promise((done,fail)=>{resolve=done;reject=fail;});
  const server = http.createServer((request,response)=>{
    if(request.url==='/result') {
      let body=''; request.on('data',chunk=>body+=chunk); request.on('end',()=>{
        response.end('OK'); try {resolve(JSON.parse(body));} catch(error) {reject(error);}
      }); return;
    }
    const pathname=new URL(request.url,'http://localhost').pathname;
    if(pathname==='/') {response.setHeader('Content-Type','text/html');response.end(index);return;}
    const file=path.resolve(root,'.'+pathname);
    if(path.relative(root,file).startsWith('..') || !fs.existsSync(file) || !fs.statSync(file).isFile()) {response.writeHead(404);response.end();return;}
    response.setHeader('Content-Type',file.endsWith('.js')?'text/javascript':file.endsWith('.css')?'text/css':'application/octet-stream');
    fs.createReadStream(file).pipe(response);
  });
  await new Promise(done=>server.listen(0,'127.0.0.1',done));
  const profile=fs.mkdtempSync(path.resolve(__dirname,'../.angular/export-browser-'));
  const child=spawn(browser,['--headless','--disable-gpu','--no-first-run','--disable-background-networking',
    '--disable-background-timer-throttling','--disable-renderer-backgrounding','--no-default-browser-check',
    '--user-data-dir='+profile,
    '--autoplay-policy=no-user-gesture-required','--window-size='+width+',1000',
    'http://127.0.0.1:'+server.address().port],{stdio:['ignore','ignore','pipe']});
  let errors=''; child.stderr.on('data',chunk=>errors=(errors+chunk).slice(-2000));
  child.on('error',reject); child.on('exit',code=>reject(Error('Chromium exited '+code+': '+errors)));
  const timeout=setTimeout(()=>reject(Error('Export test timed out: '+errors)),45000);
  try { const output=await result; assert.equal(output.error,undefined,JSON.stringify(output)); assert.equal(output.ok,true); }
  finally {
    clearTimeout(timeout);child.kill();server.close();
    await new Promise(done=>child.exitCode!==null?done():child.once('exit',done));
    await fs.promises.rm(profile,{recursive:true,force:true,maxRetries:10,retryDelay:200});
  }
});
