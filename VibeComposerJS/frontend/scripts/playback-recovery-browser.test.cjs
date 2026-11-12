const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const ts = require('typescript');
const { findBrowser, createBrowserServer, listen, launchBrowser, closeBrowser, watchBrowser, requireProductionBuild } = require('./support/browser-harness.cjs');
const clockTestSeconds = Number(process.env.PLAYBACK_CLOCK_TEST_SECONDS ?? 3);

test('realtime audio survives delays and fractional loops without hidden playhead updates', { skip: !findBrowser() }, async () => {
  const compile = file => ts.transpileModule(fs.readFileSync(path.join(__dirname, '../src/app/core/audio', file), 'utf8'), {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, experimentalDecorators: true },
  }).outputText;
  const html = `<!doctype html><script>${fs.readFileSync(require.resolve('webaudio-tinysynth'), 'utf8')}</script><script>
    (async () => {
      let playback, context, meter;
      try {
        const signal = value => { const read = () => value; read.set = next => value = next; read.asReadonly = () => read; return read; };
        const instruments = { kind: () => 'synth', selectedBank: () => undefined };
        const midi = { selectedOutput: () => null, drumMapping: () => 'general-midi', silence() {}, configure() {} };
        const angular = { Injectable: () => type => type, signal, effect: callback => callback(), untracked: callback => callback(),
          inject: token => token.name === 'InstrumentSettingsService' ? instruments : midi };
        const adapter = { exports: {} };
        new Function('require', 'module', 'exports', ${JSON.stringify(compile('tiny-synth-engine.ts'))})(() => WebAudioTinySynth, adapter, adapter.exports);
        const dependencies = {
          '@angular/core': angular,
          './instrument-settings.service': { InstrumentSettingsService: class InstrumentSettingsService {} },
          './midi-output.service': { MidiOutputService: class MidiOutputService {} },
          './create-instrument-engine': { createInstrumentEngine: async ctx => new adapter.exports.TinySynthEngine(ctx) },
          './mix-values': { mixEnabled: (mix, solo) => !mix.muted && (!solo || mix.solo) },
          '../music/phrase': { resolvedTrackPhrase: (project, track) => track.notes },
          '../music/phrase-bounds': { noteTimelineBounds: () => ({ startBeat: 0, endBeat: window.testDurationBeats ?? 16 }) },
          '../music/phrase-inputs': { samePhraseInputs: () => false },
          '../music/arrangement-generator': {},
          '../music/section-context': { projectTempoMap: () => [{ beat: 0, seconds: 0, bpm: 120 }],
            beatToSeconds: (_, beat) => beat / 2, secondsToBeat: (_, seconds) => seconds * 2,
            sectionChordDurations: () => [(window.testDurationBeats ?? 16) / 4],
            sectionDurationBeats: () => window.testDurationBeats ?? 16 },
        };
        const timelineModule = { exports: {} };
        new Function('require', 'module', 'exports', ${JSON.stringify(compile('../music/project-timeline.ts'))})(
          name => dependencies['../music/' + name.slice(2)], timelineModule, timelineModule.exports);
        dependencies['../music/project-timeline'] = timelineModule.exports;
        const service = { exports: {} };
        let forbidWindowScheduler = false;
        // Drive the service scheduler explicitly, while the real audio thread continues.
        new Function('require', 'module', 'exports', 'setInterval', 'clearInterval', ${JSON.stringify(compile('audio-playback.service.ts'))})(
          name => dependencies[name], service, service.exports,
          () => { if (forbidWindowScheduler) throw Error('Playback must not depend on the window timer'); return 1; }, () => {});
        playback = new service.exports.AudioPlaybackService();
        const startScheduler = playback.startScheduler.bind(playback);
        playback.startScheduler = () => {};
        const project = { tempoBpm: 120, arrangement: [{ measures: 4 }], tracks: [{ id: 'drums', role: 'drums',
          mix: { program: 0, volumePercent: 80, panPercent: 0 }, notes: Array.from({ length: 32 }, (_, i) => ({
            id: String(i), midi: 36, startBeat: i * .5, durationBeats: .1, velocity: 100 })) }] };
        await playback.start(project);
        context = playback.context;
        const attachMeter = () => {
          meter?.disconnect();
          meter = context.createAnalyser();
          meter.fftSize = 2048;
          playback.master.disconnect();
          playback.master.connect(meter);
          meter.connect(context.destination);
        };
        attachMeter();
        const wait = ms => new Promise(resolve => setTimeout(resolve, ms));
        const samples = new Float32Array(2048);
        const peak = () => { meter.getFloatTimeDomainData(samples); return samples.reduce((max, x) => Math.max(max, Math.abs(x)), 0); };
        const observePeak = async () => {
          let maximum = 0;
          const until = performance.now() + 350;
          while (performance.now() < until) { maximum = Math.max(maximum, peak()); await wait(10); }
          return maximum;
        };
        const peaks = [];
        const originalMaster = playback.master;
        let graphPreserved = true;
        for (let delayedTick = 0; delayedTick < 3; delayedTick++) {
          await wait(1000);
          playback.tick();
          graphPreserved &&= playback.master === originalMaster;
          // Sample across an onset interval; a single snapshot can legitimately
          // land in the silence between short drum hits.
          peaks.push(await observePeak());
        }
        playback.stop();
        playback.startScheduler = startScheduler;
        forbidWindowScheduler = true;
        // Both lengths reproduce the original floating-point boundary error.
        // The default run crosses that boundary in seconds; soak mode retains
        // the user's original length for a longer real-time check.
        const longRun = ${clockTestSeconds} > 3;
        window.testDurationBeats = longRun ? 89.033 : 0.89033;
        const loopNotes = Array.from({ length: longRun ? 178 : 4 }, (_, i) => ({
          id: String(i), midi: 36, startBeat: i * (longRun ? .5 : .25), durationBeats: .1, velocity: 100 }));
        let visibility = 'hidden';
        Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => visibility });
        let audioTicks = 0;
        const nativeTick = playback.tick.bind(playback);
        playback.tick = () => { audioTicks++; nativeTick(); };
        await playback.start({ ...project, tracks: [{ ...project.tracks[0], notes: loopNotes }] });
        attachMeter();
        const loopOrigin = playback.originTime;
        const boundaryDelays = [];
        const scheduleNote = playback.scheduleNote.bind(playback);
        playback.scheduleNote = (note, beat) => {
          if (note.id === '0') {
            const cycle = Math.round(beat / window.testDurationBeats);
            boundaryDelays.push(loopOrigin + playback.absoluteSeconds(beat) - (loopOrigin + cycle * window.testDurationBeats / 2));
          }
          scheduleNote(note, beat);
        };
        let publications = 0;
        const publish = playback.position.set;
        playback.position.set = value => { publications++; publish(value); };
        const audioClockMode = playback.clockMode;
        let clockPeak = Infinity;
        const until = performance.now() + ${clockTestSeconds} * 1000;
        while (performance.now() < until) {
          clockPeak = Math.min(clockPeak, await observePeak());
          await wait(100);
        }
        const audioClockPlaying = playback.state() === 'playing';
        const hiddenPublications = publications;
        visibility = 'visible';
        document.dispatchEvent(new Event('visibilitychange'));
        const playheadCaughtUp = publications > hiddenPublications && Math.abs(playback.beat() - playback.currentSongBeat()) < .1;
        const clock = playback.schedulerWorker;
        playback.pause();
        const clockReleased = playback.schedulerWorker === undefined && playback.clockMode === 'idle' && clock.onmessage === null;
        await fetch('/result', { method: 'POST', body: JSON.stringify({ peaks, graphPreserved,
          contextState: context.state, audioClockMode, audioTicks, clockPeak, audioClockPlaying, clockReleased,
          hiddenPublications, playheadCaughtUp, boundaryDelays, audioSinkType: context.sinkId?.type ?? null }) });
      } catch (error) {
        await fetch('/result', { method: 'POST', body: JSON.stringify({ error: String(error.stack || error) }) });
      } finally {
        meter?.disconnect(); playback?.stop(); playback?.synth?.dispose(); await context?.close();
      }
    })();
  </script>`;
  let resolve, reject;
  const result = new Promise((done, fail) => { resolve = done; reject = fail; });
  result.catch(() => {});
  const server = createBrowserServer({ root: requireProductionBuild(), html, resolve, reject });
  await listen(server);
  const child = launchBrowser(findBrowser(), ['--headless', '--disable-gpu', '--no-first-run',
    '--autoplay-policy=no-user-gesture-required', 'http://127.0.0.1:' + server.address().port], { stdio: ['ignore', 'ignore', 'pipe'] });
  const watchdog = watchBrowser(child, reject, 25000 + clockTestSeconds * 1000);
  try {
    const output = await result;
    assert.equal(output.error, undefined);
    assert.ok(output.peaks.every(peak => peak > 1e-7), 'delayed scheduling retains audible percussion');
    assert.equal(output.contextState, 'running');
    assert.equal(output.graphPreserved, true, 'late ticks do not tear down the working audio graph');
    assert.equal(output.audioClockMode, 'worker');
    assert.ok(output.audioTicks > clockTestSeconds * 10, 'the audio clock keeps the scheduler running without window timer callbacks');
    assert.equal(output.audioClockPlaying, true);
    assert.ok(output.clockPeak > 1e-7, 'the audio clock keeps producing PCM throughout the fractional loop');
    assert.equal(output.clockReleased, true, 'pause disconnects and releases the audio clock');
    assert.equal(output.hiddenPublications, 0, 'hidden audio ticks do not update the visual playhead');
    assert.equal(output.playheadCaughtUp, true, 'visibility restoration immediately refreshes the playhead');
    assert.ok(output.boundaryDelays.length >= 3, 'the test reaches the third fractional loop boundary');
    assert.ok(output.boundaryDelays.every(delay => Math.abs(delay) < 1e-8), 'boundary notes are never deferred by an extra loop');
    assert.equal(output.audioSinkType, 'none', 'realtime PCM renders to a silent device instead of speakers');
  } finally { await closeBrowser({ child, server, watchdog }); }
});
