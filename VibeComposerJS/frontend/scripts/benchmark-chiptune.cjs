// Explicit, opt-in benchmark. No timing thresholds in the normal test suite.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { findBrowser, createBrowserServer, listen, launchBrowser, closeBrowser, watchBrowser } = require('./support/browser-harness.cjs');

(async () => {
  const browser = findBrowser();
  if (!browser) throw Error('Start the protected browser broker or set OVERVIEW_TEST_BROWSER.');
  const esbuild = require(require.resolve('esbuild', { paths: [path.dirname(require.resolve('@angular/build/package.json'))] }));
  const bundle = esbuild.buildSync({ stdin: { contents: `
    export { TinySynthEngine } from './src/app/core/audio/tiny-synth-engine';
    export { CHIPTUNE_PATCHES } from './src/app/core/audio/chiptune-patches';`,
    resolveDir: path.resolve(__dirname, '..'), loader: 'ts' }, bundle: true, write: false,
    format: 'iife', globalName: 'Chip', platform: 'browser' }).outputFiles[0].text;
  const html = '<!doctype html><script>' + bundle + '</script><script>(' + (async () => {
    try {
      const rate = 48000, full = Chip.CHIPTUNE_PATCHES;
      const harmonics = Object.values(full.waves)[0].real.length - 1;
      const baseline = { ...full, waves: Object.fromEntries(Object.entries(full.waves).map(([name, wave]) =>
        [name, { real: wave.real.slice(0, 65), imag: wave.imag.slice(0, 65) }])) };
      const variants = harmonics === 64 ? [{ harmonics: 64, patches: full }]
        : [{ harmonics: 64, patches: baseline }, { harmonics, patches: full }];
      const programs = full.melodic.flatMap((patch, program) => patch[0].w.startsWith('wChip') ? [program] : []);
      const results = Object.fromEntries(variants.map(v => [v.harmonics, { startup: [], offline: [], realtime: [] }]));
      const wait = ms => new Promise(resolve => setTimeout(resolve, ms));
      let rng = 0x51f15e;
      Math.random = () => { rng ^= rng << 13; rng ^= rng >>> 17; rng ^= rng << 5; return (rng >>> 0) / 4294967296; };
      const order = round => round % 2 ? [...variants].reverse() : variants;
      const buses = context => Array.from({ length: 16 }, () => {
        const bus = context.createGain(); bus.gain.value = .015; bus.connect(context.destination); return bus;
      });
      const batch = (engine, routes, step, start, handles, live = false) => {
        for (let track = 0; track < 16; track++) for (let chord = 0; chord < 4; chord++) {
          const voices = engine.schedule(routes[track], programs[(track * 5 + step) % programs.length], false,
            36 + track % 12 + [0, 4, 7, 12][chord], 80 + track % 4 * 10, start, .75);
          for (const voice of voices) {
            if (live) voice.onEnded(() => { voice.dispose(); handles.delete(voice); });
            if (live) handles.add(voice); else handles.push(voice);
          }
        }
      };
      // First construction is reported separately; two warm-up rounds are excluded.
      for (let round = -2; round < 16; round++) for (const variant of order(round)) {
        const context = new OfflineAudioContext(2, rate, rate);
        const native = context.createPeriodicWave.bind(context);
        let waveMs = 0;
        context.createPeriodicWave = (...args) => { const t = performance.now(); const wave = native(...args); waveMs += performance.now() - t; return wave; };
        const t = performance.now();
        const engine = new Chip.TinySynthEngine(context, variant.patches);
        const row = { totalMs: performance.now() - t, waveMs };
        if (round === -2) results[variant.harmonics].firstStartup = row;
        if (round >= 0) results[variant.harmonics].startup.push(row);
        engine.dispose();
        await wait(0);
      }
      // Same 1024 notes: 16 tracks x 4-note chords x 16 onsets, 256 notes/s.
      // 0.75s notes at 0.25s spacing give 192 held voices plus release tails.
      for (let round = -1; round < 5; round++) for (const variant of order(round)) {
        const context = new OfflineAudioContext(2, rate * 6, rate);
        const engine = new Chip.TinySynthEngine(context, variant.patches), routes = buses(context), handles = [];
        try {
          const t = performance.now();
          for (let step = 0; step < 16; step++) batch(engine, routes, step, .05 + step * .25, handles);
          const scheduleMs = performance.now() - t, renderStart = performance.now();
          const audio = await context.startRendering();
          const renderMs = performance.now() - renderStart;
          let peak = 0;
          for (const x of audio.getChannelData(0)) peak = Math.max(peak, Math.abs(x));
          if (round >= 0) results[variant.harmonics].offline.push({ scheduleMs, renderMs, peak });
        } finally { handles.forEach(v => v.dispose()); engine.dispose(); routes.forEach(b => b.disconnect()); }
      }
      // Actual audio-thread playback, replenished every 25ms with 125ms lookahead.
      // Alternating order reduces warm-up/thermal bias. The harness uses a silent sink.
      for (let round = 0; round < 3; round++) for (const variant of order(round)) {
        const context = new AudioContext({ sampleRate: rate });
        const engine = new Chip.TinySynthEngine(context, variant.patches), routes = buses(context), handles = new Set();
        const analyser = context.createAnalyser(); analyser.fftSize = 2048;
        routes.forEach(b => { b.disconnect(); b.connect(analyser); }); analyser.connect(context.destination);
        try {
          await context.resume();
          const origin = context.currentTime + .15, until = performance.now() + 3000;
          let next = 0, lateBatches = 0, peak = 0, ticks = 0, cpuMs = 0, maxCpuMs = 0, previous = performance.now();
          const tickGapsMs = [], samples = new Float32Array(2048);
          while (performance.now() < until) {
            const t = performance.now(); tickGapsMs.push(t - previous); previous = t;
            const now = context.currentTime;
            while (next < 12 && origin + next * .25 < now + .125) {
              const at = origin + next * .25;
              if (at < context.currentTime) lateBatches++;
              batch(engine, routes, next++, at, handles, true);
            }
            const elapsed = performance.now() - t; cpuMs += elapsed; maxCpuMs = Math.max(maxCpuMs, elapsed); ticks++;
            analyser.getFloatTimeDomainData(samples);
            for (const x of samples) peak = Math.max(peak, Math.abs(x));
            await wait(25);
          }
          if (context.currentTime < 2.8 || next !== 12) throw Error('Realtime audio clock did not advance');
          results[variant.harmonics].realtime.push({ ticks, notes: next * 64, lateBatches, peak, cpuMs, maxCpuMs, tickGapsMs });
        } finally {
          for (const voice of [...handles]) { voice.stop(); voice.dispose(); } handles.clear();
          engine.dispose(); routes.forEach(b => b.disconnect()); analyser.disconnect(); await context.close();
        }
      }
      await fetch('/result', { method: 'POST', body: JSON.stringify({ browser: navigator.userAgent, rate, results }) });
    } catch (error) { await fetch('/result', { method: 'POST', body: JSON.stringify({ error: error.stack }) }); }
  }).toString() + ')();</script>';
  let resolve, reject;
  const result = new Promise((done, fail) => { resolve = done; reject = fail; }); result.catch(() => {});
  const server = createBrowserServer({ html, resolve, reject }); await listen(server);
  const child = launchBrowser(browser, ['--headless', '--disable-gpu', '--no-first-run',
    '--disable-background-timer-throttling', '--disable-renderer-backgrounding',
    '--autoplay-policy=no-user-gesture-required', 'http://127.0.0.1:' + server.address().port], { stdio: ['ignore', 'ignore', 'pipe'] });
  const watchdog = watchBrowser(child, reject, 90000);
  try {
    const output = await result;
    if (output.error) throw Error(output.error);
    const percentile = (values, fraction) => [...values].sort((a, b) => a - b)[Math.floor((values.length - 1) * fraction)];
    const summary = {};
    for (const [harmonics, rows] of Object.entries(output.results)) {
      assert.ok([...rows.offline, ...rows.realtime].every(row => row.peak > .001), 'dense audio is audible');
      summary[harmonics] = {
        startupMedianMs: percentile(rows.startup.map(r => r.totalMs), .5), startupP95Ms: percentile(rows.startup.map(r => r.totalMs), .95),
        waveMedianMs: percentile(rows.startup.map(r => r.waveMs), .5),
        offlineScheduleMedianMs: percentile(rows.offline.map(r => r.scheduleMs), .5),
        offlineRenderMedianMs: percentile(rows.offline.map(r => r.renderMs), .5),
        realtimeScheduleMedianMs: percentile(rows.realtime.map(r => r.cpuMs), .5),
        realtimeMaxTickWorkMs: Math.max(...rows.realtime.map(r => r.maxCpuMs)),
        realtimeTickGapP95Ms: percentile(rows.realtime.flatMap(r => r.tickGapsMs).slice(1), .95),
        lateBatches: rows.realtime.reduce((sum, r) => sum + r.lateBatches, 0),
      };
    }
    const report = { measuredAt: new Date().toISOString(), ...output, summary,
      caveat: 'Headless browser, silent audio sink. Offline render time measures throughput; realtime checks report scheduling deadlines, not hardware underruns or OS-wide CPU usage. Startup excludes module loading/parsing and AudioContext construction.' };
    const target = path.resolve(__dirname, '../.angular/chiptune-benchmark-' + Math.max(...Object.keys(summary).map(Number)) + '.json');
    fs.mkdirSync(path.dirname(target), { recursive: true }); fs.writeFileSync(target, JSON.stringify(report, null, 2) + '\n');
    console.log(JSON.stringify({ report: target, summary }, null, 2));
  } finally { await closeBrowser({ child, server, watchdog }); }
})().catch(error => { console.error(error); process.exitCode = 1; });
