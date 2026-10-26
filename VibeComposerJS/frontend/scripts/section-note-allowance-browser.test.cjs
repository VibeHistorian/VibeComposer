const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const { findBrowser, connectToPage, createBrowserServer, listen, launchBrowser, closeBrowser, requireProductionBuild } = require('./support/browser-harness.cjs');

test('production score edits pickups and late notes with 00/END space and retains the owning section', { timeout: 60000 }, async () => {
  const server = createBrowserServer({ root: requireProductionBuild() });
  await listen(server);
  const profile = fs.mkdtempSync(path.resolve(__dirname, '../.angular/section-allowance-'));
  const child = launchBrowser(findBrowser(), ['--headless', '--disable-gpu', '--no-first-run', '--disable-background-networking',
    '--remote-debugging-port=0', '--window-size=1600,1100', '--user-data-dir=' + profile, 'http://127.0.0.1:' + server.address().port]);
  let socket;
  try {
    const connection = await connectToPage(child);
    socket = connection.socket;
    const command = connection.command;
    const evaluate = async expression => {
      const result = await command('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
      assert.equal(result.exceptionDetails, undefined, JSON.stringify(result));
      return result.result.value;
    };
    const helpers = `const wait=async f=>{for(let i=0;i<200;i++){const v=f();if(v)return v;await new Promise(r=>setTimeout(r,25));}throw Error('Timed out');};
      const read=()=>JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));`;
    await evaluate(`(async()=>{${helpers}
      const seed=await wait(()=>document.querySelector('[aria-label="Generation seed"]'));
      await changeControl(seed,'8675309');
      const p=await wait(read), bass=p.tracks.find(t=>t.role==='bass');
      p.arrangement=[{...p.arrangement[0],measures:1,trackPresence:Object.fromEntries(p.tracks.map(t=>[t.id,t.id===bass.id])),
        trackPhrases:{[bass.id]:[
          {id:'pickup',midi:48,startBeat:-4,durationBeats:1,velocity:90},
          {id:'cross',midi:50,startBeat:3,durationBeats:3,velocity:90},
          {id:'late',midi:52,startBeat:7,durationBeats:1,velocity:90}
        ]}}];
      sessionStorage.setItem('vibecomposer.project.v12',JSON.stringify(p));
    })()`);
    await command('Page.reload');
    const point = await evaluate(`(async()=>{${helpers}
      await wait(()=>document.querySelector('.score-ruler-overflow')?.textContent==='00');
      await wait(()=>[...document.querySelectorAll('.score-ruler-overflow')].some(e=>e.textContent==='END'));
      const canvas=await wait(()=>document.querySelector('vc-score-canvas canvas'));
      canvas.scrollIntoView({block:'center'});
      await new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)));
      const c=canvas.getBoundingClientRect(),key=document.querySelector('.pitch-key[title="MIDI 52"]').getBoundingClientRect();
      return {x:c.left+c.width*11.5/12,y:key.top+key.height/2};
    })()`);
    await command('Input.dispatchMouseEvent', { type: 'mousePressed', ...point, button: 'left', clickCount: 1 });
    await command('Input.dispatchMouseEvent', { type: 'mouseReleased', ...point, button: 'left', clickCount: 1 });
    const bounds = await evaluate(`(async()=>{${helpers}
      const start=await wait(()=>document.querySelector('#note-start'));
      const before={start:Number(start.value),minimum:Number(start.min),maximum:Number(start.max)};
      await changeControl(start,'7');
      await changeControl(document.querySelector('#note-length'),'2');
      (await wait(()=>[...document.querySelectorAll('.score-display-header button')].find(b=>b.textContent==='Local Apply'&&!b.disabled))).click();
      await wait(()=>!document.querySelector('#note-start'));
      const p=read(),id=p.tracks.find(t=>t.role==='bass').id;
      return {before,notes:p.arrangement[0].trackPhrases[id]};
    })()`);
    assert.deepEqual(bounds.before, { start: 8, minimum: -3, maximum: 8 });
    assert.equal(bounds.notes.length, 3);
    assert.equal(bounds.notes.find(note => note.id === 'pickup').startBeat, -4);
    assert.equal(bounds.notes.find(note => note.id === 'cross').durationBeats, 3);
    assert.equal(bounds.notes.find(note => note.id === 'late').startBeat, 6);
    assert.equal(bounds.notes.find(note => note.id === 'late').durationBeats, 2);
    const screenshot = await command('Page.captureScreenshot', { format: 'png' });
    fs.writeFileSync(path.resolve(__dirname, '../.angular/section-note-allowance.png'), Buffer.from(screenshot.data, 'base64'));
  } finally {
    await closeBrowser({ child, server, socket });
  }
});
