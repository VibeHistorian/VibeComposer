const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const ts = require('typescript');
const { findBrowser, createBrowserServer, listen, launchBrowser, watchBrowser, closeBrowser } = require('./support/browser-harness.cjs');

// Retained baseline from the original renderer: compare actual Pixi tessellation,
// rather than timing just the JavaScript that emits drawing commands.
function legacyLogShape(graphics, x, y, width, height, plucky) {
  const k = 15, scale = Math.log1p(k), center = y + height / 2;
  const envelope = t => plucky ? 1 - Math.log1p(k * t) / scale : Math.log1p(k * (1 - t)) / scale;
  const slope = t => -k / ((1 + k * (plucky ? t : 1 - t)) * scale);
  graphics.moveTo(x, y);
  for (const side of [-1, 1]) for (let i = 0; i < 12; i++) {
    const a = side === -1 ? i / 12 : 1 - i / 12;
    const b = side === -1 ? (i + 1) / 12 : 1 - (i + 1) / 12;
    const d = b - a, half = side * height / 2;
    graphics.bezierCurveTo(x + (a + d / 3) * width, center + half * (envelope(a) + slope(a) * d / 3),
      x + (b - d / 3) * width, center + half * (envelope(b) - slope(b) * d / 3), x + b * width, center + half * envelope(b));
  }
  return graphics.closePath();
}

test('log note contours reduce WebGL geometry and preserve rendered envelopes', { skip: !findBrowser(), timeout: 45000 }, async t => {
  const compiled = ts.transpileModule(fs.readFileSync(path.join(__dirname, '../src/app/features/score/score-note-shape.ts'), 'utf8'), {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS },
  }).outputText;
  const html = `<!doctype html><script type="module">
    import { Application, Graphics, Rectangle } from '/pixi.mjs';
    const module = { exports: {} }; const exports = module.exports;
    ${compiled}
    ${legacyLogShape.toString()}
    let app;
    try {
      app = new Application();
      await app.init({ width: 1600, height: 480, resolution: 2, antialias: true, preference: 'webgl', autoStart: false, backgroundAlpha: 0 });
      document.body.appendChild(app.canvas);
      const graphics = new Graphics(); app.stage.addChild(graphics);
      const shape = (mode, x, y, width, height, plucky) => mode === 'classic'
        ? graphics.roundRect(x,y,width,height,Math.min(3,height/2))
        : mode === 'legacy' ? legacyLogShape(graphics,x,y,width,height,plucky)
        : graphics.poly(logNotePolygon(x,y,width,height,plucky));
      const scene = (mode, iteration) => {
        graphics.clear();
        const started = performance.now();
        for (let i=0;i<1280;i++) {
          const x=(i%32)*48+8,y=Math.floor(i/32)*11+10,width=12+(i%7)*8+iteration*.125,height=8;
          const plucky=i%2===0,color=plucky?0x32d7f4:0xf6a367;
          if (i%5===0) for(let r=6;r>=1;r--) {
            (mode==='classic'?graphics.rect(x-r,y-r,width+r*2,height+r*2):shape(mode,x-r,y-r,width+r*2,height+r*2,plucky))
              .fill({color,alpha:(7-r)/150});
          }
          shape(mode,x,y,width,height,plucky).fill({color,alpha:.65}).stroke({color,alpha:.8,width:1});
        }
        const commands = performance.now(); app.render();
        return { commands:commands-started, render:performance.now()-commands, total:performance.now()-started };
      };
      const results = { classic:[], legacy:[], polygon:[] }, modes=Object.keys(results);
      // Warm each path and alternate order to avoid always rewarding the last renderer.
      for(let round=0;round<15;round++) {
        for(const mode of round%2?modes:[...modes].reverse()) {
          const sample=scene(mode,round);
          if(round>=3)results[mode].push(sample);
        }
        await new Promise(requestAnimationFrame);
      }
      const points = mode => {
        scene(mode,0);
        return graphics.context.instructions.reduce((sum,instruction)=>sum+(instruction.data.path?.shapePath.shapePrimitives??[])
          .reduce((n,primitive)=>n+(primitive.shape.points?.length??0)/2,0),0);
      };
      const vertices={legacy:points('legacy'),polygon:points('polygon')};
      // Compare isolated filled/stroked notes at both envelope families, several lengths/heights,
      // and the same six-layer selected-note glow used in the score.
      const image = mode => {
        graphics.clear();
        for(let i=0;i<16;i++) {
          const x=10+(i%4)*260,y=12+Math.floor(i/4)*105,width=[3,24,100,230][i%4],height=[6,12,24,64][Math.floor(i/4)];
          const plucky=i%2===0;
          for(let r=6;r>=1;r--)shape(mode,x-r,y-r,width+r*2,height+r*2,plucky).fill({color:0x32d7f4,alpha:(7-r)/150});
          shape(mode,x,y,width,height,plucky).fill({color:0x32d7f4,alpha:.65}).stroke({color:0x32d7f4,alpha:.8,width:1});
        }
        app.render();
        return app.renderer.extract.pixels({target:app.stage,frame:new Rectangle(0,0,1100,430),resolution:2,antialias:true}).pixels;
      };
      const before=image('legacy'),after=image('polygon');
      let difference=0,energy=0;
      for(let i=0;i<before.length;i++){difference+=Math.abs(before[i]-after[i]);energy+=before[i];}
      const rasterError=difference/Math.max(1,energy);
      const median=values=>[...values].sort((a,b)=>a-b)[Math.floor(values.length/2)];
      const timings=Object.fromEntries(Object.entries(results).map(([mode,samples])=>[mode,
        Object.fromEntries(['commands','render','total'].map(key=>[key,median(samples.map(sample=>sample[key]))]))]));
      await fetch('/result',{method:'POST',body:JSON.stringify({timings,vertices,rasterError})});
    } catch(error) {await fetch('/result',{method:'POST',body:JSON.stringify({error:error.stack})});}
    finally {app?.destroy(true,{children:true});}
  </script>`;
  let resolve, reject;
  const result = new Promise((done, fail) => { resolve=done;reject=fail; });
  const server = createBrowserServer({ html, resolve, reject });
  const serve=server.listeners('request')[0];server.removeListener('request',serve);
  const pixiFile=path.resolve(path.dirname(require.resolve('pixi.js')),'../dist/pixi.mjs');
  server.on('request',(request,response)=>{
    if(request.url!=='/pixi.mjs')return serve(request,response);
    response.setHeader('Content-Type','text/javascript');fs.createReadStream(pixiFile).pipe(response);
  });
  await listen(server);
  const child=launchBrowser(findBrowser(),['--headless','--disable-gpu','--no-first-run','--disable-background-timer-throttling',
    '--window-size=1700,800', 'http://127.0.0.1:'+server.address().port+'/'],{stdio:['ignore','ignore','pipe']});
  const watchdog=watchBrowser(child,reject,40000);
  try {
    const actual=await result;
    assert.equal(actual.error,undefined,actual.error);
    t.diagnostic(JSON.stringify(actual));
    fs.mkdirSync(path.join(__dirname,'../.angular'),{recursive:true});
    fs.writeFileSync(path.join(__dirname,'../.angular/score-note-rendering.json'),JSON.stringify(actual,null,2));
    assert.ok(actual.vertices.polygon<actual.vertices.legacy*.35,'substantially fewer path vertices to triangulate and stroke');
    assert.ok(actual.rasterError<.06,'rasterized fills, strokes and glows remain close to the original curves');
    // Timings are diagnostics, not a machine-dependent pass/fail threshold.
  } finally {await closeBrowser({child,server,watchdog});}
});
