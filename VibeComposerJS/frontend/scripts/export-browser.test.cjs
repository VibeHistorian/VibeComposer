const { findBrowser, createBrowserServer, listen, launchBrowser, closeBrowser, watchBrowser, requireProductionBuild } = require('./support/browser-harness.cjs');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const { DEFAULT_PROJECT } = require('./music-loader.cjs').loadMusic('../project/project-defaults.ts');
const browser = findBrowser();

for (const { width, missingMethod, sampler } of [
  { width: 1440 }, { width: 780 }, { width: 1440, sampler: true },
  { width: 1440, missingMethod: 'suspend' },
  { width: 1440, missingMethod: 'resume' },
]) test(`production export menu and offline Opus download at ${width}px${sampler ? ' with GM sampler' : ''}${missingMethod ? ` without ${missingMethod}` : ''}`, { skip: !browser }, async () => {
  const root = requireProductionBuild();
  const sf2Base64 = sampler ? fs.readFileSync(path.join(__dirname, '../../Super_Nintendo_Unofficial_update.sf2')).toString('base64') : '';
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
      const renderProgress = [];
      let completionGlow = false;
      const noticeObserver = new MutationObserver(() => {
        const notice = document.querySelector('.project-message');
        const match = notice?.textContent.match(/Rendering audio: (\\d+)%/);
        if (match) {
          const percent = Number(match[1]);
          if (renderProgress.at(-1) !== percent) renderProgress.push(percent);
          if (percent === 100) {
            completionGlow ||= notice.classList.contains('export-finishing')
              && getComputedStyle(notice, '::after').animationName.includes('export-border-orbit');
          }
        }
      });
      noticeObserver.observe(document.body, { subtree: true, childList: true, characterData: true, attributes: true, attributeFilter: ['class'] });
      const unhandledRejections = [];
      addEventListener('unhandledrejection', event => unhandledRejections.push(String(event.reason)));
      let offlineResumes = 0;
      const nativeOfflineResume = OfflineAudioContext.prototype.resume;
      OfflineAudioContext.prototype.resume = function(...args) {
        offlineResumes++;
        return nativeOfflineResume.apply(this, args);
      };
      // Exercise Firefox's missing checkpoint APIs while retaining real audio rendering.
      const missingMethod = ${JSON.stringify(missingMethod ?? null)};
      if (missingMethod) Object.defineProperty(OfflineAudioContext.prototype, missingMethod, { value: undefined, configurable: true });
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
        if (${Boolean(sampler)}) {
          const engineSelect = await wait(() => document.querySelector('vc-select[aria-label="Audio instrument engine"]'), 'instrument selector');
          document.querySelector('[aria-label="Settings"]').click();
          const settings = await wait(() => document.querySelector('vc-settings-dialog'), 'soundfont settings');
          (await wait(() => [...settings.querySelectorAll('.settings-sidebar button')].find(b => b.querySelector('span')?.textContent.trim() === 'MIDI'), 'MIDI category')).click();
          const input = await wait(() => settings.querySelector('input[aria-label="Load soundfont"]'), 'soundfont picker');
          const bytes = Uint8Array.from(atob('${sf2Base64}'), c => c.charCodeAt(0));
          const transfer = new DataTransfer(); transfer.items.add(new File([bytes], 'SNES.sf2'));
          input.files = transfer.files; input.dispatchEvent(new Event('change', { bubbles: true }));
          await wait(() => !input.disabled && settings.textContent.includes('Loaded SNES.sf2.'), 'soundfont loaded');
          const upload = async (name, oversized = false) => {
            const selected = new File([bytes], name);
            if (oversized) Object.defineProperty(selected, 'size', { value: 250 * 1048576 + 1 });
            const items = new DataTransfer(); items.items.add(selected); input.files = items.files;
            input.dispatchEvent(new Event('change', { bubbles: true }));
            await wait(() => !input.disabled && settings.textContent.includes(oversized ? '250 MiB or smaller' : 'Loaded ' + name + '.'), name);
          };
          await upload('oversized.sf2', true);
          ensure(settings.querySelectorAll('[aria-label^="Unload "]').length === 1, 'oversized font does not evict');
          for (const name of ['Second.sf2', 'Third.sf2', 'Fourth.sf2']) await upload(name);
          const options = await selectOptions(engineSelect);
          ensure(options.length === 4 && !options.some(o => o.textContent.includes('SNES.sf2')), 'fourth load evicts oldest; three fonts plus synth');
          const second = options.find(o => o.textContent.includes('Second.sf2'));
          await changeControl(engineSelect, second.value);
          settings.querySelector('[aria-label="Unload Second.sf2"]').click();
          await wait(() => engineSelect.getAttribute('value') === 'synth', 'unloading selected font chooses synth');
          const fourth = (await selectOptions(engineSelect)).find(o => o.textContent.includes('Fourth.sf2'));
          await changeControl(engineSelect, fourth.value);
          settings.querySelector('[aria-label="Close settings"]').click();
        }
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
        const filenamePattern = /^\\d{8}_\\d{2}-\\d{2}_${project.key}_${project.tempoBpm}_${project.seed}_Export- test/;
        ensure(saved.name==='Export: test' && filenamePattern.test(downloads[0].name) && downloads[0].name.endsWith('.vibe.json'),'project export preserved with automatic filename');
        trigger.click(); await wait(menu,'MIDI menu'); item('Export MIDI').click();
        await wait(()=>downloads.length===2,'MIDI download');
        const midi=await (await downloads[1].blob).arrayBuffer();
        ensure(new TextDecoder().decode(midi.slice(0,4))==='MThd' && filenamePattern.test(downloads[1].name) && downloads[1].name.endsWith('.mid'),'MIDI export');
        trigger.click(); await wait(menu,'audio menu'); item('Export Audio').click();
        trigger.click(); await wait(menu,'busy menu');
        ensure([...menu().querySelectorAll('button')].some(b=>b.disabled && b.textContent.includes('Audio')),'duplicate audio export disabled');
        key(document.activeElement,'Escape');
        await wait(()=>downloads.length===3,'audio download');
        ensure(realtimeContexts===0,'offline export never opens audio device');
        const audio=await downloads[2].blob;
        ensure(filenamePattern.test(downloads[2].name) && downloads[2].name.endsWith('.opus') && audio.type.includes('audio/ogg'),'Opus filename and MIME');
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
        ensure(offlineResumes===(missingMethod ? 0 : 19),'only supported render checkpoints suspend and resume');
        ensure(renderProgress[0]===0 && renderProgress.at(-1)===100,'render progress runs from 0 to 100: '+renderProgress);
        if (!missingMethod) ensure(renderProgress.some(percent=>percent>0 && percent<100),'intermediate render progress visible');
        ensure(renderProgress.every((percent,index)=>!index || percent>renderProgress[index-1]),'render progress increases');
        ensure(completionGlow,'100% render progress shows animated completion border');
        document.querySelector('[aria-label="Settings"]').click();
        const settings = await wait(()=>document.querySelector('vc-settings-dialog'),'settings');
        (await wait(()=>[...settings.querySelectorAll('.settings-sidebar button')].find(b=>b.textContent.trim()==='Project'),'Project category')).click();
        const fields = await wait(()=>settings.querySelector('.export-name-fields'),'filename toggles');
        ensure(fields.querySelectorAll('button').length===5,'five filename fields enabled by default');
        const preview = () => settings.querySelector('.export-name-preview').textContent.trim();
        await wait(()=>filenamePattern.test(preview()),'current project filename preview');
        for (const toggle of fields.querySelectorAll('button')) toggle.click();
        await wait(()=>preview()==='Export- test.vibe.json','project name only preview');
        ensure(JSON.parse(localStorage.getItem('vibecomposer.ui-preferences.v1')).exportNameFields.length===0,'filename preferences saved');
        settings.querySelector('[aria-label="Close settings"]').click();
        trigger.click(); await wait(menu,'project menu after filename settings'); item('Export Project').click();
        await wait(()=>downloads.length===4,'project name only download');
        ensure(downloads[3].name==='Export- test.vibe.json','exports honor disabled fields');
        ensure(!document.querySelector('.export-finishing'),'completion clears animated border');
        ensure(unhandledRejections.length===0,'unhandled export rejections: '+unhandledRejections.join('; '));
        // Mute every track; report failure and allow retry.
        for (const label of ['M1','B1','C1','A1','D1']) {
          const mute=document.querySelector('[aria-label="Mute '+label+'"]');
          ensure(mute,'track mute control '+label); mute.click();
          await wait(()=>document.querySelector('[aria-label="Unmute '+label+'"]'),'track muted '+label);
        }
        trigger.click(); await wait(menu,'silent audio menu'); item('Export Audio').click();
        await wait(()=>document.querySelector('.project-message')?.textContent.includes('no audible notes'),'export failure feedback');
        ensure(!document.querySelector('.export-finishing'),'failure clears animated border');
        ensure(downloads.length===4,'failed export does not download');
        trigger.click(); await wait(menu,'retry menu'); ensure(!item('Export Audio').disabled,'failure clears busy state');
        await fetch('/result',{method:'POST',body:JSON.stringify({ok:true})});
      } catch(error) { await fetch('/result',{method:'POST',body:JSON.stringify({error:error.stack,
        notice:document.querySelector('.project-message')?.textContent,downloads:downloads.map(d=>d.name),
        project:sessionStorage.getItem('vibecomposer.project.v12')})}); }
      finally { noticeObserver.disconnect(); window.AudioContext=NativeAudioContext; }
    })();
  </script>`;
  const index = fs.readFileSync(path.join(root,'index.html'),'utf8').replace('</body>',script+'</body>');
  let resolve, reject;
  const result = new Promise((done,fail)=>{resolve=done;reject=fail;});
  result.catch(() => {}); // CDP setup can fail before the result is awaited.
  const server = createBrowserServer({ root, html: index, resolve, reject });
  await listen(server);
  const profile=fs.mkdtempSync(path.resolve(__dirname,'../.angular/export-browser-'));
  const child=launchBrowser(browser,['--headless','--disable-gpu','--no-first-run','--disable-background-networking',
    '--disable-background-timer-throttling','--disable-renderer-backgrounding','--no-default-browser-check',
    '--user-data-dir='+profile,
    '--autoplay-policy=no-user-gesture-required','--window-size='+width+',1000',
    'http://127.0.0.1:'+server.address().port],{stdio:['ignore','ignore','pipe']});
  const watchdog = watchBrowser(child, reject, 45000);
  try { const output=await result; assert.equal(output.error,undefined,JSON.stringify(output)); assert.equal(output.ok,true); }
  finally {
    await closeBrowser({ child, server, watchdog });
  }
});
