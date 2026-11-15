const { findBrowser, createBrowserServer, listen, launchBrowser, closeBrowser, watchBrowser } = require('./support/browser-harness.cjs');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const browser = findBrowser();

test('real PCM calibration, normalized rendering, content cache reuse and disabled bypass', { skip: !browser }, async t => {
  const esbuild = require(require.resolve('esbuild', { paths: [path.dirname(require.resolve('@angular/build/package.json'))] }));
  const bundle = esbuild.buildSync({ stdin: {
    contents: `export { calibrateInstruments } from './src/app/core/audio/instrument-loudness';
      export { TINY_SYNTH_LOUDNESS_PROFILE } from './src/app/core/audio/tiny-synth-loudness';
      export { createInstrumentEngine } from './src/app/core/audio/create-instrument-engine';
      export { Sf2Bank } from './src/app/core/audio/sf2-resolver';`,
    resolveDir: path.resolve(__dirname, '..'), loader: 'ts',
  }, bundle: true, write: false, format: 'iife', globalName: 'Calibration', platform: 'browser' }).outputFiles[0].text;
  const sf2 = fs.readFileSync(path.join(__dirname, '../../Super_Nintendo_Unofficial_update.sf2')).toString('base64');
  const html = '<!doctype html><script>' + bundle + '</script><script>' + `
  (async () => { try {
    localStorage.clear();
    const ensure = (ok, message) => { if (!ok) throw Error(message); };
    const NativeOffline = OfflineAudioContext;
    let renders = 0;
    OfflineAudioContext = class extends NativeOffline { constructor(...args) { super(...args); renders++; } };
    const bytes = Uint8Array.from(atob('${sf2}'), c => c.charCodeAt(0)).buffer;
    const bank = new Calibration.Sf2Bank(bytes);
    const started = performance.now();
    const pending = Calibration.calibrateInstruments(bank);
    const concurrent = Calibration.calibrateInstruments(bank);
    const profile = await pending;
    ensure(profile === await concurrent, 'concurrent calibration deduplicated');
    const seconds = (performance.now() - started) / 1000;
    ensure(profile.melodic.some(g => g !== 1), 'melodic corrections measured');
    ensure(profile.drums.some(g => g !== 1), 'drum corrections measured');
    const count = renders;
    const reloaded = await Calibration.calibrateInstruments(new Calibration.Sf2Bank(bytes.slice(0)));
    ensure(renders === count, 'identical file reuses persistent cache without rendering');
    ensure(JSON.stringify(profile) === JSON.stringify(reloaded), 'cached values identical');
    const render = async (normalize, percussion, midi) => {
      const context = new NativeOffline(2, 24000, 24000);
      const engine = await Calibration.createInstrumentEngine(context, 'sf2:1', bank, normalize);
      const voices = engine.schedule(context.destination, 0, percussion, midi, 100, .05, .4);
      await engine.flush();
      const buffer = await context.startRendering();
      let power = 0;
      for (const channel of [buffer.getChannelData(0), buffer.getChannelData(1)]) for (const value of channel) power += value * value;
      voices.forEach(v => v.dispose()); engine.dispose();
      return Math.sqrt(power);
    };
    for (const [percussion, midi] of [[false, 60], [true, 36], [true, 38]]) {
      const raw = await render(false, percussion, midi), corrected = await render(true, percussion, midi);
      const gain = percussion ? profile.drums[midi] : profile.melodic[0];
      ensure(raw > 0 && Math.abs(corrected / raw - gain) < .0001, 'PCM follows measured gain for ' + percussion + ':' + midi);
    }
    // Disabled normalization must never request calibration, even for a new bank.
    const bypassBank = new Calibration.Sf2Bank(bytes.slice(0));
    bypassBank.contentHash = () => { throw Error('disabled mode requested calibration'); };
    const bypass = await Calibration.createInstrumentEngine(new NativeOffline(2, 24000, 24000), 'sf2:2', bypassBank, false);
    bypass.dispose();
    const tinyStart = performance.now();
    const rendersBeforeTiny = renders;
    // Old user caches must never mask edits to the bundled table.
    localStorage.setItem('vibecomposer.instrument-loudness.v1', JSON.stringify({
      'tinysynth-1.1.3': { melodic: Array(128).fill(1), drums: Array(128).fill(1) }
    }));
    OfflineAudioContext = class { constructor() { throw Error('TinySynth attempted runtime calibration'); } };
    const tiny = await Calibration.calibrateInstruments();
    ensure(tiny === Calibration.TINY_SYNTH_LOUDNESS_PROFILE, 'bundled table overrides old browser cache');
    ensure(tiny.melodic.length === 128 && tiny.drums[82] !== 1, 'TinySynth and shaker bundled');
    const tinyEngine = await Calibration.createInstrumentEngine(new NativeOffline(2, 24000, 24000), 'synth', undefined, true);
    tinyEngine.dispose();
    ensure(renders === rendersBeforeTiny, 'TinySynth uses no calibration renders');
    await fetch('/result', { method: 'POST', body: JSON.stringify({ seconds, tinySeconds: (performance.now()-tinyStart)/1000, renders }) });
  } catch (error) { await fetch('/result', { method: 'POST', body: JSON.stringify({ error: String(error.stack || error) }) }); } })();
  </script>`;
  let resolve, reject;
  const result = new Promise((done, fail) => { resolve = done; reject = fail; });
  result.catch(() => {});
  const server = createBrowserServer({ html, resolve, reject });
  await listen(server);
  const child = launchBrowser(browser, ['--headless', '--disable-gpu', '--no-first-run',
    '--autoplay-policy=no-user-gesture-required', 'http://127.0.0.1:' + server.address().port], { stdio: ['ignore', 'ignore', 'pipe'] });
  const watchdog = watchBrowser(child, reject, 45000);
  try {
    const output = await result;
    assert.equal(output.error, undefined);
    t.diagnostic('Calibration: SF2 ' + output.seconds.toFixed(2) + 's, TinySynth ' + output.tinySeconds.toFixed(2) + 's');
  } finally { await closeBrowser({ child, server, watchdog }); }
});
