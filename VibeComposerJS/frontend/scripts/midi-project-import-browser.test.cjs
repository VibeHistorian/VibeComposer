const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const { Midi } = require('@tonejs/midi');
const { findBrowser, createBrowserServer, listen, launchBrowser, closeBrowser, watchBrowser, requireProductionBuild } = require('./support/browser-harness.cjs');
const { observeSettings, readWorkspace } = require('./support/workspace-browser.cjs');
const browser = findBrowser();

function midiBytes(title) {
  const midi = new Midi();
  midi.name = title;
  midi.header.setTempo(112);
  const bass = midi.addTrack();
  bass.name = 'Bass';
  bass.instrument.number = 33;
  bass.addNote({ midi: 36, ticks: 240, durationTicks: 480, velocity: 100 / 127 });
  const kit = midi.addTrack();
  kit.name = 'Kit';
  kit.channel = 9;
  for (let i = 0; i < 18; i++) kit.addNote({ midi: 36 + i, ticks: 960 + i * 120, durationTicks: 120 });
  return [...midi.toArray()];
}

test('Open loads a MIDI arrangement, retains it on reload and follows the project tab preference', { skip: !browser }, async () => {
  const root = requireProductionBuild();
  const script = `<script>${observeSettings}(async () => {
    const read = ${readWorkspace};
    const picker = () => document.querySelector('input[type=file][accept*=".midi"]');
    const button = label => [...document.querySelectorAll('button')].find(item =>
      item.textContent.trim() === label || item.getAttribute('aria-label') === label);
    const activeName = () => document.querySelector('button.project-pill.active')?.textContent.trim();
    const status = () => document.querySelector('.project-message')?.textContent;
    const saved = async name => {
      const workspace = await read();
      return workspace?.tabs.find(tab => tab.id === workspace.activeId && tab.project.name === name);
    };
    const ensure = (condition, label) => { if (!condition) throw Error(label); };
    const wait = async (read, label) => {
      for (let i = 0; i < 250; i++) {
        const value = await read();
        if (value) return value;
        await new Promise(resolve => setTimeout(resolve, 25));
      }
      throw Error('Timed out: ' + label);
    };
    const open = (filename, bytes) => {
      const transfer = new DataTransfer();
      transfer.items.add(new File([new Uint8Array(bytes)], filename, { type: 'application/octet-stream' }));
      picker().files = transfer.files;
      picker().dispatchEvent(new Event('change', { bubbles: true }));
    };
    try {
      await wait(picker, 'MIDI file picker');
      if (sessionStorage.getItem('midi-project-reload')) {
        await wait(() => activeName() === 'Imported arrangement', 'import restored as active tab');
        const { project, id } = await wait(() => saved('Imported arrangement'), 'saved MIDI project');
        ensure(project.tempoBpm === 112 && project.tracks.length === 17, 'tempo and track count');
        const [bass, kick] = project.tracks;
        const overflow = project.tracks.at(-1);
        ensure(bass.name === 'Bass' && bass.role === 'bass' && bass.mix.program === 33, 'bass metadata');
        ensure(kick.name === 'Kit · Kick' && kick.role === 'drums' && kick.midiChannel === 10, 'percussion metadata');
        const overflowNotes = project.arrangement.flatMap(section => section.trackPhrases[overflow.id]).map(note => note.midi);
        ensure(overflow.drumSlotPitch === 51 && overflowNotes.join(',') === '51,52,53', 'overflow pitches survive reload');
        const bassNote = project.arrangement[0].trackPhrases[bass.id][0];
        ensure(bassNote.midi + project.transposeSemitones === 36 && bassNote.startBeat === 0.5 && bassNote.durationBeats === 1
          && bassNote.velocity === 100, 'section-owned bass note');
        button('Settings').click();
        (await wait(() => button('Project'), 'project settings')).click();
        const replace = await wait(() => document.querySelector('[aria-label="Replace current project tab when opening projects"]'), 'open preference');
        replace.click();
        await wait(() => JSON.parse(storedSetting('vibecomposer.ui-preferences.v1'))?.replaceCurrentProjectTab, 'preference saved');
        button('Close settings').click();
        open('again.MID', ${JSON.stringify(midiBytes('Replaced with MIDI'))});
        const replacement = await wait(() => saved('Replaced with MIDI'), 'replacement saved');
        ensure(replacement.id === id && document.querySelectorAll('.project-pill').length === 2, 'MIDI respects replacement preference');
        ensure(replacement.past.length === 0 && replacement.future.length === 0, 'new history');
        await fetch('/result', { method: 'POST', body: JSON.stringify({ ok: true }) });
      } else {
        await wait(activeName, 'initial project tab');
        open('arrangement.bin', ${JSON.stringify(midiBytes('Imported arrangement'))});
        await wait(() => status()?.includes('from MIDI (17 tracks)'), 'successful file opening');
        const initial = await wait(() => saved('Imported arrangement'), 'parsed project persisted');
        ensure(document.querySelectorAll('.project-pill').length === 2, 'opens in a new tab by default');
        open('broken.midi', [0, 1, 2, 3]);
        await wait(() => status()?.includes('Could not import'), 'invalid file feedback');
        ensure((await saved('Imported arrangement'))?.id === initial.id, 'invalid MIDI leaves the existing tab active');
        sessionStorage.setItem('midi-project-reload', 'true');
        location.reload();
      }
    } catch (error) {
      await fetch('/result', { method: 'POST', body: JSON.stringify({ error: error.stack }) });
    }
  })();</script>`;
  let resolve, reject;
  const result = new Promise((done, fail) => { resolve = done; reject = fail; });
  result.catch(() => {});
  const html = fs.readFileSync(path.join(root, 'index.html'), 'utf8').replace('</body>', script + '</body>');
  const server = createBrowserServer({ root, html, resolve, reject });
  await listen(server);
  const profile = fs.mkdtempSync(path.resolve(__dirname, '../.angular/midi-project-import-browser-'));
  const child = launchBrowser(browser, ['--headless', '--disable-gpu', '--no-first-run', '--disable-background-networking',
    '--window-size=1000,900', '--user-data-dir=' + profile, 'http://127.0.0.1:' + server.address().port], { stdio: ['ignore', 'ignore', 'pipe'] });
  const watchdog = watchBrowser(child, reject, 45000);
  try {
    const output = await result;
    assert.equal(output.error, undefined, JSON.stringify(output));
    assert.equal(output.ok, true);
  } finally { await closeBrowser({ child, server, watchdog }); }
});
