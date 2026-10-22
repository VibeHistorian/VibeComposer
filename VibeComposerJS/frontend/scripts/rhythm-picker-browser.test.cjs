const { findBrowser, createBrowserServer, listen, launchBrowser, closeBrowser, watchBrowser, requireProductionBuild } = require('./support/browser-harness.cjs');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const ts = require('typescript');

const browser = findBrowser();

test('production Angular controls initialize selects from settings and support rhythm, wheel and mix edits', {
  skip: browser ? false : 'Set OVERVIEW_TEST_BROWSER to a Chromium executable',
}, async () => {
  const root = requireProductionBuild();
  const source = fs.readFileSync(path.join(__dirname, '../src/app/core/music/rhythm-patterns.ts'), 'utf8');
  const module = { exports: {} };
  new Function('exports', ts.transpileModule(source, { compilerOptions: {
    target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS,
  } }).outputText)(module.exports);
  const expected = module.exports.RHYTHM_PATTERNS;
  const index = fs.readFileSync(path.join(root, 'index.html'), 'utf8');
  const script = `<script>
    (async () => {
      const waitFor = async (read, label = 'initial controls') => {
        const caller = new Error('Waiting for ' + label).stack;
        for (let attempt = 0; attempt < 300; attempt++) {
          const result = read(); if (result) return result;
          await new Promise(resolve => setTimeout(resolve, 50));
        }
        throw new Error('Angular UI did not reach expected state: ' + label + '\\n' + caller);
      };
      try {
        sessionStorage.clear();
        const initialMidi = await waitFor(() => {
          const selects = [...document.querySelectorAll('.track-group.bass .track-inline-select')];
          return selects.length === 2 && selects.every(select => select.options.length) ? selects : null;
        }, 'initial track instrument and channel');
        const initialSelection = initialMidi.map(select => select.value);
        const inlineActions = [...document.querySelectorAll('.track-line')].map(line => {
          const items = [...line.children];
          const bounds = items.map(item => item.getBoundingClientRect());
          const row = line.getBoundingClientRect();
          return { symbols: [...line.querySelectorAll('.track-reroll-actions button')].map(button => button.textContent.trim()),
            actionsBeforeInstrument: items[1].classList.contains('track-reroll-actions') && items[2].classList.contains('track-instrument'),
            oneLine: bounds.every(box => Math.abs(box.top + box.height / 2 - (row.top + row.height / 2)) < 1),
            fits: bounds.every(box => box.left >= row.left && box.right <= row.right + 1) };
        });
        const readTrack = role => JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')).tracks.find(track => track.role === role);
        const settingsButton = [...document.querySelectorAll('.project-tools button')].find(button => button.getAttribute('aria-label') === 'Settings');
        if (document.querySelector('.project-tools .compact-history')) throw new Error('Compact History remains on main screen');
        const openHistorySettings = async () => {
          settingsButton.focus(); settingsButton.click();
          const settings = await waitFor(() => document.querySelector('vc-settings-dialog'), 'settings popup');
          await waitFor(() => settings.contains(document.activeElement), 'settings focus');
          const bounds = settings.getBoundingClientRect();
          if (bounds.left < 0 || bounds.right > innerWidth || bounds.top < 0 || bounds.bottom > innerHeight) throw new Error('settings outside viewport');
          [...settings.querySelectorAll('.settings-sidebar button')].find(button => button.textContent.trim() === 'Section Types').click();
          await waitFor(() => settings.querySelector('vc-section-type-settings'), 'section types');
          if (!settings.querySelector('vc-section-type-settings input[type="range"]')) throw new Error('section types missing');
          const sidebar = settings.querySelector('.settings-sidebar');
          [...sidebar.querySelectorAll('button')].find(button => button.textContent.trim() === 'Project').click();
          const checkbox = await waitFor(() => settings.querySelector('input[type="checkbox"]'), 'Compact History setting');
          if (sidebar.getBoundingClientRect().right > settings.querySelector('.settings-content').getBoundingClientRect().left + 1) throw new Error('sidebar not on left');
          return checkbox;
        };
        const compactButton = await openHistorySettings();
        if (!compactButton.checked) throw new Error('Compact History must default on');
        compactButton.click(); await waitFor(() => !compactButton.checked, 'compact off');
        compactButton.click(); await waitFor(() => compactButton.checked, 'compact on');
        document.querySelector('[aria-label="Close settings"]').click();
        await waitFor(() => !document.querySelector('vc-settings-dialog') && document.activeElement === settingsButton, 'settings focus restored');
        const undo = document.querySelector('.project-tools button[aria-label="Undo"]');
        const redo = document.querySelector('.project-tools button[aria-label="Redo"]');
        const tempo = document.querySelector('input[aria-label="Tempo in beats per minute"]');
        const originalTempo = Number(tempo.value);
        for (let i = 1; i <= 3; i++) {
          tempo.dispatchEvent(new WheelEvent('wheel', { deltaY: -100, shiftKey: true, bubbles: true, cancelable: true }));
          await waitFor(() => Number(tempo.value) === originalTempo + i, 'live compact tempo edit');
        }
        undo.click();
        await waitFor(() => Number(tempo.value) === originalTempo, 'one undo restores compact burst');
        redo.click();
        await waitFor(() => Number(tempo.value) === originalTempo + 3, 'one redo restores final compact value');
        undo.click();
        await waitFor(() => Number(tempo.value) === originalTempo, 'restore tempo');
        const reopenedCompact = await openHistorySettings();
        if (!reopenedCompact.checked) throw new Error('Compact History lost when reopening settings');
        reopenedCompact.click(); await waitFor(() => !reopenedCompact.checked, 'compact history off');
        document.querySelector('[aria-label="Close settings"]').click();
        await waitFor(() => !document.querySelector('vc-settings-dialog'), 'settings closed');
        const initiallyCollapsed = [...document.querySelectorAll('.collapse-group-button')].every(button => button.getAttribute('aria-expanded') === 'false');
        const instrumentDefaultChecks = [];
        // Preserve parameter-only scenarios with explicit instrument opt-outs; selection opens each role's rules.
        for (const role of ['melody', 'bass', 'chords', 'arpeggio', 'drums']) {
          const group = document.querySelector('.track-group.' + role);
          group.querySelector('.group-select').click();
          const toggle = await waitFor(() => group.querySelector('button[aria-label="Randomize instruments"]'), role + ' policy opened');
          instrumentDefaultChecks.push(toggle.getAttribute('aria-pressed') === 'true');
          toggle.click();
          await waitFor(() => toggle.getAttribute('aria-pressed') === 'false', role + ' instrument opt-out');
        }
        document.querySelector('.track-group.bass .track-select').click();
        await waitFor(() => [...document.querySelectorAll('.collapse-group-button')].every(button => button.getAttribute('aria-expanded') === 'false'), 'track selection collapses roles');
        const melodyRow = document.querySelector('.track-group.melody');
        const melodyInstrument = melodyRow.querySelector('.track-instrument');
        const melodyChannel = melodyRow.querySelector('.track-channel');
        // Edit a row while the inspector still targets Bass; identity changes belong to that row.
        melodyInstrument.dispatchEvent(new MouseEvent('click', { bubbles: true }));
        melodyInstrument.value = '0'; melodyInstrument.dispatchEvent(new Event('change', { bubbles: true }));
        await waitFor(() => readTrack('melody').mix.program === 0, 'unselected row instrument commit');
        melodyChannel.value = '5'; melodyChannel.dispatchEvent(new Event('change', { bubbles: true }));
        await waitFor(() => readTrack('melody').midiChannel === 5, 'unselected row channel commit');
        const inlineIdentity = { program: readTrack('melody').mix.program, channel: readTrack('melody').midiChannel,
          bassProgram: readTrack('bass').mix.program, bassChannel: readTrack('bass').midiChannel,
          selectionPreserved: document.querySelector('.track-group.bass .track-select').getAttribute('aria-pressed') === 'true',
          noInspectorIdentity: !document.querySelector('#part-inspector .mix-quick-settings'),
          noG: !document.querySelector('.generation-toggle'),
          hiddenArrow: getComputedStyle(melodyInstrument).appearance === 'none' };
        melodyInstrument.value = '73'; melodyInstrument.dispatchEvent(new Event('change', { bubbles: true }));
        melodyChannel.value = '4'; melodyChannel.dispatchEvent(new Event('change', { bubbles: true }));
        await waitFor(() => readTrack('melody').mix.program === 73 && readTrack('melody').midiChannel === 4, 'restored melody identity');
        const initialChords = [...document.querySelectorAll('.chordlet-label')].map(button => button.textContent.trim());
        document.querySelector('.track-group.melody .track-select').click();
        await waitFor(() => document.querySelector('.track-name-field input')?.value === readTrack('melody').name, 'melody inspector');
        const melodySelection = [...document.querySelectorAll('.track-group.melody .track-inline-select')].map(select => select.value);
        const group = await waitFor(() => document.querySelector('.track-group.chords .group-select'));
        group.click();
        await waitFor(() => document.querySelector('#part-inspector vc-part-settings-editor[data-control-role="chords"]'), 'Chords musical inspector');
        let pickers = await waitFor(() => {
          const result = [...document.querySelectorAll('select[aria-label="PATTERN"]')];
          return result.length === 1 ? result : null;
        });
        const options = pickers.map(select => [...select.options].map(option => option.value));
        pickers[0].value = 'custom';
        pickers[0].dispatchEvent(new Event('change', { bubbles: true }));
        (await waitFor(() => document.querySelector('button[aria-label="Edit rhythm pattern"]'))).click();
        const grids = await waitFor(() => {
          const result = [...document.querySelectorAll('.custom-pattern-grid')];
          return result.length === 1 ? result : null;
        });
        const counts = grids.map(grid => grid.querySelectorAll('button').length);
        grids[0].querySelector('button').click();
        await waitFor(() => [...document.querySelectorAll('.custom-pattern-grid button:first-child')]
          .every(button => button.getAttribute('aria-pressed') === 'false'));
        const project = JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));
        const result = { instrumentDefaultChecks, inlineActions, inlineIdentity, initialSelection, melodySelection, initialChords, options, counts, selected: pickers.map(select => select.value),
          rhythm: project.tracks.find(track => track.role === 'chords').generatorSettings.rhythm, grid: project.tracks.find(track => track.role === 'chords').generatorSettings.customPattern };
        const wheel = (element, deltaY, shiftKey = false) => element.dispatchEvent(
          new WheelEvent('wheel', { deltaY, shiftKey, bubbles: true, cancelable: true }));
        document.querySelector('button[aria-label="Close pattern sequencer"]').click();
        await waitFor(() => !document.querySelector('vc-pattern-sequencer'));
        // Pattern commits rebuild the control groups. Use the mounted select rather
        // than the detached element captured before opening the sequencer.
        pickers = [...document.querySelectorAll('select[aria-label="PATTERN"]')];
        wheel(pickers[0], 100);
        await waitFor(() => document.querySelector('select[aria-label="PATTERN"]')?.value === 'full'
          && JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')).tracks.find(track => track.role === 'chords').generatorSettings.rhythm === 'full', 'rhythm wheel commit');
        pickers = [...document.querySelectorAll('select[aria-label="PATTERN"]')];
        result.wheelRhythm = pickers.map(select => select.value);
        const track = document.querySelector('.track-group.chords .track-row-card');
        const pan = track.querySelector('input[aria-label="Pan C1"]');
        const volume = track.querySelector('input[aria-label="Volume C1"]');
        const face = input => input.closest('.compact-knob').querySelector('.knob-face');
        const rgb = color => {
          const canvas = document.createElement('canvas'); canvas.width = canvas.height = 1;
          const context = canvas.getContext('2d'); context.fillStyle = color; context.fillRect(0, 0, 1, 1);
          return [...context.getImageData(0, 0, 1, 1).data].slice(0, 3);
        };
        const color = input => rgb(getComputedStyle(face(input)).borderColor);
        const setMix = async (input, value) => {
          input.value = String(value); input.dispatchEvent(new Event('change', { bubbles: true }));
          await waitFor(() => input.closest('.compact-knob').querySelector('output').textContent.trim()
            === (input === pan ? value === 0 ? 'C' : '100% ' + (value < 0 ? 'L' : 'R')
              : [99.5, 100, 100.5].includes(value) ? '-0.0' : value === 98.9 ? '-0.1' : value === 0 ? '-Inf' : '+6.0'));
          // Border colors animate for 120ms after Angular applies the new value.
          await new Promise(resolve => setTimeout(resolve, 180));
        };
        const muted = rgb(getComputedStyle(track).getPropertyValue('--text-muted'));
        const accent = rgb(getComputedStyle(track).getPropertyValue('--accent-primary'));
        result.mixDefaultsMuted = [pan, volume].every(input => JSON.stringify(color(input)) === JSON.stringify(muted));
        result.roundedUnityMuted = true;
        for (const value of [99.5, 100.5]) {
          await setMix(volume, value);
          result.roundedUnityMuted &&= JSON.stringify(color(volume)) === JSON.stringify(muted)
            && readTrack('chords').mix.volumePercent === 100;
        }
        wheel(volume, -100, true); await waitFor(() => volume.value === '101');
        wheel(volume, 100, true); await waitFor(() => volume.value === '100');
        wheel(volume, -100); await waitFor(() => volume.value === '110');
        wheel(volume, 100); await waitFor(() => volume.value === '100');
        result.volumeScrollSteps = true;
        await setMix(volume, 98.9);
        result.tinyGainVisible = color(volume).some((channel, index) => Math.abs(channel - muted[index]) > 10);
        await setMix(volume, 0);
        result.attenuationAccent = color(volume);
        await setMix(volume, Number(volume.max));
        result.boostAccent = color(volume); result.configuredAccent = accent;
        await setMix(volume, 100);
        result.panEndpointsAccent = true;
        for (const value of [-100, 100]) {
          await setMix(pan, value);
          result.panEndpointsAccent &&= JSON.stringify(color(pan)) === JSON.stringify(accent);
        }
        await setMix(pan, 0);
        wheel(pan, -100, true);
        await waitFor(() => pan.value === '1');
        // Synthetic pointer events exercise Angular's actual handlers and signals;
        // unit coverage separately checks pointer capture and vertical-only movement.
        volume.setPointerCapture = () => {};
        const pointer = (type, y) => volume.dispatchEvent(new PointerEvent(type,
          { pointerId: 17, button: 0, clientY: y, bubbles: true, cancelable: true }));
        const volumeDragEnd = 100 + 7500 / Number(volume.max);
        pointer('pointerdown', 100); pointer('pointermove', volumeDragEnd);
        await waitFor(() => volume.closest('.compact-knob').querySelector('output').textContent.trim() === '-6.0');
        result.volumeBeforeRelease = readTrack('chords').mix.volumePercent;
        pointer('pointerup', volumeDragEnd);
        await waitFor(() => volume.value === '50');
        track.querySelector('.mute-toggle').click();
        await waitFor(() => track.querySelector('.mute-toggle').classList.contains('active'));
        const muteColor = getComputedStyle(track.querySelector('.mute-toggle')).backgroundColor;
        track.querySelector('.solo-toggle').click();
        await waitFor(() => !track.querySelector('.mute-toggle').classList.contains('active')
          && track.querySelector('.solo-toggle').classList.contains('active'));
        result.quickMix = readTrack('chords').mix;
        result.panLabel = pan.closest('.compact-knob').querySelector('output').textContent.trim();
        result.miniWidths = [pan, volume].map(input => input.closest('.compact-knob').getBoundingClientRect().width);
        result.volumeCenter = volume.closest('.compact-knob').querySelector('.knob-face > span').textContent.trim();
        result.volumeAccessibleValue = volume.getAttribute('aria-valuetext');
        result.colors = [muteColor, getComputedStyle(track.querySelector('.solo-toggle')).backgroundColor].map(rgb);
        result.mixColors = ['--mix-track-mute-active', '--mix-track-solo-active']
          .map(token => rgb(getComputedStyle(track).getPropertyValue(token)));
        const readMixProject = () => JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));
        const globalMix = document.querySelector('.transport-actions vc-mix-buttons');
        await waitFor(() => globalMix.querySelector('.solo-toggle').getAttribute('aria-pressed') === 'mixed');
        result.globalAfterLive = globalMix.previousElementSibling.classList.contains('live-button');
        result.groupAfterName = document.querySelector('.track-group.chords .group-mix-controls').previousElementSibling.classList.contains('group-select');
        globalMix.querySelector('.solo-toggle').click();
        await waitFor(() => readMixProject().tracks.every(item => !item.mix.solo));
        globalMix.querySelector('.mute-toggle').click();
        await waitFor(() => readMixProject().tracks.every(item => item.mix.muted && !item.mix.solo));
        globalMix.querySelector('.solo-toggle').click();
        await waitFor(() => readMixProject().tracks.every(item => item.mix.solo && !item.mix.muted));
        const middle = element => element.dispatchEvent(new MouseEvent('auxclick', { button: 1, bubbles: true, cancelable: true }));
        middle(track.querySelector('.solo-toggle'));
        await waitFor(() => readMixProject().tracks.every(item => item.mix.solo === (item.role === 'chords')));
        middle(document.querySelector('.track-group.bass .group-select'));
        await waitFor(() => readMixProject().tracks.every(item => item.mix.solo === (item.role === 'bass')));
        middle(track.querySelector('.track-select'));
        await waitFor(() => readMixProject().tracks.every(item => item.mix.solo === (item.role === 'chords')));
        result.exclusiveMix = true;
        globalMix.querySelector('.solo-toggle').click();
        await waitFor(() => readMixProject().tracks.every(item => !item.mix.solo));
        const bounds = track.getBoundingClientRect();
        result.controlsFit = track.querySelector('.track-mix-controls').getBoundingClientRect().right <= bounds.right;
        result.groupStillSelected = group.getAttribute('aria-pressed');
        const velocitySwitches = [...document.querySelectorAll('button[aria-label="Custom velocities"]')];
        velocitySwitches[0].click();
        (await waitFor(() => document.querySelector('button[aria-label="Edit pattern velocities"]'))).click();
        const velocityGrids = await waitFor(() => {
          const result = [...document.querySelectorAll('.velocity-pattern-grid')];
          return result.length === 1 ? result : null;
        }, 'velocity grids enabled');
        result.velocityCounts = velocityGrids.map(grid => grid.querySelectorAll('input').length);
        const velocity = velocityGrids[0].querySelector('input');
        velocity.value = '22'; velocity.dispatchEvent(new Event('input', { bubbles: true }));
        await waitFor(() => velocityGrids[0].querySelector('output').textContent.trim() === '22', 'velocity preview');
        result.velocityBeforeCommit = JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')).tracks.find(track => track.role === 'chords').generatorSettings.customVelocities;
        result.storedBeforeCommit = JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')).tracks.find(track => track.role === 'chords').generatorSettings.customVelocities?.[0] ?? 79;
        velocity.dispatchEvent(new Event('change', { bubbles: true }));
        await waitFor(() => JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')).tracks.find(track => track.role === 'chords').generatorSettings.customVelocities?.[0] === 22, 'inspector velocity commit persisted');
        await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
        const secondVelocity = velocityGrids[0].querySelectorAll('input')[1];
        wheel(secondVelocity, -100, true);
        await waitFor(() => secondVelocity.value === '80', 'velocity wheel update');
        result.customVelocities = JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')).tracks.find(track => track.role === 'chords').generatorSettings.customVelocities;
        result.randomBoundsHidden = !document.querySelector('input[aria-label="Min velocity"]');
        document.querySelector('button[aria-label="Close pattern sequencer"]').click();
        await waitFor(() => !document.querySelector('vc-pattern-sequencer'));
        velocitySwitches[0].click();
        await waitFor(() => document.querySelector('#part-inspector button[aria-label="Custom velocities"]')?.getAttribute('aria-pressed') === 'false', 'velocity grids disabled');
        result.retainedVelocities = JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')).tracks.find(track => track.role === 'chords').generatorSettings.customVelocities;
        document.querySelector('#part-inspector button[aria-label="Custom velocities"]').click();
        (await waitFor(() => document.querySelector('button[aria-label="Edit pattern velocities"]'))).click();
        await waitFor(() => document.querySelector('.velocity-pattern-grid input')?.value === '22', 'velocity grids restored');
        result.velocitiesEnabled = JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')).tracks.find(track => track.role === 'chords').generatorSettings.useCustomVelocities;
        document.querySelector('button[aria-label="Close pattern sequencer"]').click();
        await waitFor(() => !document.querySelector('vc-pattern-sequencer'));
        document.querySelector('.track-group.melody .group-select').click();
        const targets = await waitFor(() => {
          const inputs = [...document.querySelectorAll('input[aria-label="TARGETS"]')];
          return inputs.length === 1 ? inputs : null;
        }, 'melody minimum editors');
        result.melodyInitialTargets = targets.map(input => input.value);
        targets[0].focus(); targets[0].value = '0, 4, -2';
        targets[0].dispatchEvent(new Event('change', { bubbles: true }));
        await waitFor(() => targets.every(input => input.value === '0, 4, -2'), 'shared melody target commit');
        const structure = document.querySelector('input[aria-label="WORDS"]');
        structure.focus(); structure.value = '1, -1, 2';
        structure.dispatchEvent(new Event('change', { bubbles: true }));
        const transpose = document.querySelector('input[aria-label="Transpose"]');
        wheel(transpose, -100, true);
        await waitFor(() => transpose.getAttribute('aria-valuetext') === '5 st', 'melody discrete transpose');
        wheel(transpose, -100, true);
        await waitFor(() => transpose.getAttribute('aria-valuetext') === '7 st', 'melody next discrete transpose');
        result.melody = JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')).tracks.find(track => track.role === 'melody').generatorSettings;
        result.melodyHasSpeed = document.querySelectorAll('input[aria-label="Speed"]').length;
        result.melodyHasAccent = document.querySelectorAll('input[aria-label="Accent"]').length;
        document.querySelector('.track-group.melody .track-select').click();
        await waitFor(() => document.querySelector('.track-name-field input')?.value === readTrack('melody').name, 'melody track context');
        result.recreatedSelection = [...document.querySelectorAll('.track-group.melody .track-inline-select')].map(select => select.value);
        result.sharedRoles = [];
        for (const role of ['bass', 'arpeggio']) {
          document.querySelector('.track-group.' + role + ' .group-select').click();
          await waitFor(() => [...document.querySelectorAll('vc-part-settings-editor')]
            .every(editor => editor.getAttribute('data-control-role') === role), role + ' editor context');
          const rhythms = await waitFor(() => {
            const controls = [...document.querySelectorAll('select[aria-label="PATTERN"]')];
            return controls.length === 1 ? controls : null;
          }, role + ' rhythm controls');
          rhythms[0].value = 'custom'; rhythms[0].dispatchEvent(new Event('change', { bubbles: true }));
          await waitFor(() => document.querySelectorAll('.rhythm-preview-grid').length === 1, role + ' grids');
          for (const [selectorLabel, flipLabel] of [['PATTERN', 'Pattern flip'], ['FILL', 'Fill flip']]) {
            const select = document.querySelector('select[aria-label="' + selectorLabel + '"]');
            const control = select.closest('.part-control');
            const flip = control.querySelector('button[aria-label="' + flipLabel + '"]');
            if (!flip || control.querySelectorAll('vc-control-heading').length !== 1) throw new Error(selectorLabel + ' and its flip must share one heading');
            if (control.querySelector('.inherit-snowflake')) throw new Error(selectorLabel + ' global settings must not show section inheritance');
            if (flip.textContent.trim() !== '↔' || flip.getBoundingClientRect().left < select.getBoundingClientRect().right) throw new Error(flipLabel + ' must be an icon to the right');
            if (getComputedStyle(select).appearance !== 'none') throw new Error(selectorLabel + ' must be arrowless');
            const before = flip.getAttribute('aria-pressed');
            flip.click();
            await waitFor(() => flip.getAttribute('aria-pressed') !== before, flipLabel + ' enabled');
            flip.click();
            await waitFor(() => flip.getAttribute('aria-pressed') === before, flipLabel + ' restored');
          }
          const hits = document.querySelector('input[aria-label="Hits"]');
          hits.value = '5'; hits.dispatchEvent(new Event('change', { bubbles: true }));
          await waitFor(() => [...document.querySelectorAll('.rhythm-preview-grid')].every(grid => grid.children.length === 5), role + ' Hits');
          const seeds = [...document.querySelectorAll('input[aria-label="Seed"]')];
          seeds[0].focus(); seeds[0].value = '-123'; seeds[0].dispatchEvent(new Event('change', { bubbles: true }));
          await waitFor(() => seeds.every(seed => seed.value === '-123'), role + ' seed sync');
          const delays = document.querySelector('input[aria-label="Delays"]');
          delays.value = '2'; delays.dispatchEvent(new Event('change', { bubbles: true }));
          await waitFor(() => [...document.querySelectorAll('input[aria-label="Delays"]')].every(input => input.value === '2'), role + ' feedback sync');
          if (document.querySelector('button[aria-label="Generate notes"]')) throw new Error(role + ' still has a generation toggle');
          [...document.querySelectorAll('button')].find(button => button.textContent.trim() === 'Clear seed').click();
          await waitFor(() => seeds.every(seed => seed.value === '0'), role + ' seed cleared');
          const state = readTrack(role).generatorSettings;
          result.sharedRoles.push({ role, hits: state.hitsPerPattern, seed: state.patternSeed, delays: state.feedbackCount,
            grids: document.querySelectorAll('.rhythm-preview-grid').length,
            pause: document.querySelectorAll('input[aria-label="Pause"]').length,
            split: document.querySelectorAll('input[aria-label="Split"]').length,
            repeat: document.querySelectorAll('input[aria-label="Repeat"]').length });
        }
        document.querySelector('.track-group.arpeggio .group-select').click();
        await waitFor(() => document.querySelector('#part-inspector vc-part-settings-editor[data-control-role="arpeggio"]'), 'Arpeggio musical inspector');
        const readArp = () => JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')).tracks.find(track => track.role === 'arpeggio');
        const beforePolicy = JSON.stringify(readArp().generatorSettings);
        const randomHits = [...document.querySelectorAll('button[aria-label="Random hits"]')];
        randomHits[0].click();
        await waitFor(() => !document.querySelector('input[aria-label="Reroll Arps hits"]').disabled, 'fixed hits enabled');
        const setPolicyNumber = async (label, value) => {
          const input = document.querySelector('input[aria-label="Reroll ' + label + '"]');
          input.value = String(value); input.dispatchEvent(new Event('change', { bubbles: true }));
          await waitFor(() => [...document.querySelectorAll('input[aria-label="Reroll ' + label + '"]')].every(input => input.value === String(value)), label);
        };
        await setPolicyNumber('Arps hits', 16);
        await setPolicyNumber('Max split %', 0);
        await setPolicyNumber('Min length %', 100);
        await setPolicyNumber('Max repeat', 1);
        document.querySelector('button[aria-label="Random span"]').click();
        result.rerollPolicyLeavesSettings = JSON.stringify(readArp().generatorSettings) === beforePolicy;
        result.rerollRuleCopies = randomHits.length;
        document.querySelector('button[aria-label="Reroll A1"]').click();
        await waitFor(() => JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')).trackRerollCounts?.arpeggio === 1, 'track reroll applied');
        result.rerollSettings = readArp().generatorSettings;
        result.rerollId = readArp().id;
        await waitFor(() => [...document.querySelectorAll('input[aria-label="Hits"]')].every(input => input.value === '16'), 'rerolled part controls');
        result.rerollDisplayedHits = [...document.querySelectorAll('input[aria-label="Hits"]')].map(input => input.value);
        document.querySelector('button[aria-label="Lock reroll for A1"]').click();
        await waitFor(() => document.querySelector('button[aria-label="Reroll A1"]').disabled, 'reroll lock');
        result.rerollAllLocked = document.querySelector('button[aria-label="Reroll Arps tracks"]').disabled;
        document.querySelector('button[aria-label="Unlock reroll for A1"]').click();
        await waitFor(() => !document.querySelector('button[aria-label="Reroll A1"]').disabled, 'reroll unlock');
        document.querySelector('.track-group.bass .group-select').click();
        const bassJoin = await waitFor(() => document.querySelector('select[aria-label="JOIN"]'), 'bass advanced join');
        result.bassJoinInitial = bassJoin.value;
        result.bassJoinOptions = [...bassJoin.options].map(option => option.value);
        result.bassJoinInspectorOnly = document.querySelectorAll('select[aria-label="JOIN"]').length === 1
          && !!bassJoin.closest('#part-inspector');
        bassJoin.value = 'JOIN'; bassJoin.dispatchEvent(new Event('change', { bubbles: true }));
        await waitFor(() => JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')).tracks.find(track => track.role === 'bass').generatorSettings.patternJoinMode === 'JOIN', 'persisted bass join');
        result.bassJoinSaved = JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')).tracks.find(track => track.role === 'bass').generatorSettings.patternJoinMode;
        const bassRhythm = document.querySelector('select[aria-label="PATTERN"]');
        bassRhythm.value = 'bass1'; bassRhythm.dispatchEvent(new Event('change', { bubbles: true }));
        await waitFor(() => !document.querySelector('select[aria-label="JOIN"]'), 'bass1 hides join');
        bassRhythm.value = 'custom'; bassRhythm.dispatchEvent(new Event('change', { bubbles: true }));
        result.bassJoinRetained = (await waitFor(() => document.querySelector('select[aria-label="JOIN"]'), 'grid restores join')).value;
        document.querySelector('.track-group.drums .track-select').click();
        await waitFor(() => document.querySelector('#part-inspector vc-part-settings-editor[data-control-role="drums"]'), 'drum musical context');
        const drumPitch = await waitFor(() => document.querySelector('.track-group.drums .track-instrument'), 'drum pitch picker');
        result.initialDrumPitch = drumPitch.value;
        drumPitch.value = '38'; drumPitch.dispatchEvent(new Event('change', { bubbles: true }));
        await waitFor(() => document.querySelector('.track-group.drums .track-instrument').selectedOptions[0].textContent === 'Snare · 38', 'drum track name');
        const drumRhythms = [...document.querySelectorAll('select[aria-label="PATTERN"]')];
        result.drumRhythmOptions = drumRhythms.map(select => [...select.options].map(option => option.value));
        drumRhythms[0].value = 'custom'; drumRhythms[0].dispatchEvent(new Event('change', { bubbles: true }));
        document.querySelector('button[aria-label="Edit rhythm pattern"]').click();
        const drumGrids = await waitFor(() => {
          const grids = [...document.querySelectorAll('.custom-pattern-grid')];
          return grids.length === 1 ? grids : null;
        }, 'drum custom rhythm');
        result.drumGridCounts = drumGrids.map(grid => grid.querySelectorAll('button').length);
        drumGrids[0].querySelector('button').click();
        await waitFor(() => drumGrids.every(grid => grid.querySelector('button').getAttribute('aria-pressed') === 'false'), 'shared drum custom grid edit');
        document.querySelector('button[aria-label="Close pattern sequencer"]').click();
        await waitFor(() => !document.querySelector('vc-pattern-sequencer'));
        const drumVelocities = [...document.querySelectorAll('button[aria-label="Custom velocities"]')];
        drumVelocities[0].click();
        (await waitFor(() => document.querySelector('button[aria-label="Edit pattern velocities"]'))).click();
        await waitFor(() => document.querySelectorAll('.velocity-pattern-grid').length === 1, 'drum inspector velocity grid');
        result.drumVelocityCounts = [...document.querySelectorAll('.velocity-pattern-grid')].map(grid => grid.querySelectorAll('input').length);
        document.querySelector('button[aria-label="Close pattern sequencer"]').click();
        await waitFor(() => !document.querySelector('vc-pattern-sequencer'));
        result.drumChannelLocked = document.querySelector('.track-group.drums .track-channel').disabled;
        result.drumSettings = JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')).tracks.find(track => track.role === 'drums').generatorSettings;
        const drumInstrument = document.querySelector('.track-group.drums .track-instrument');
        result.drumPitch = drumInstrument.value;
        result.drumName = drumInstrument.selectedOptions[0].textContent;
        document.querySelector('button[aria-label="Add Drums track"]').click();
        await waitFor(() => document.querySelectorAll('.track-group.drums .track-select').length === 2, 'second drum track');
        document.querySelector('button[aria-label="Add Drums track"]').click();
        await waitFor(() => document.querySelectorAll('.track-group.drums .track-select').length === 3, 'third drum track');
        result.otherRoleRerolls = [];
        for (const role of ['bass', 'chords', 'drums', 'melody']) {
          const group = document.querySelector('.track-group.' + role);
          group.querySelector('.group-select').click();
          await waitFor(() => document.querySelector('#part-inspector vc-part-settings-editor[data-control-role="' + role + '"]'), role + ' musical settings');
          const read = () => JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));
          const track = read().tracks.find(track => track.role === role);
          const before = JSON.stringify(track.generatorSettings);
          const toggleLabel = { chords: 'Vary length', drums: 'Patterns', melody: 'Reroll seeds' }[role];
          const checkbox = toggleLabel ? group.querySelector('button[aria-label="' + toggleLabel + '"]') : null;
          let unchanged = true, synced = true;
          if (checkbox) {
            const label = checkbox.getAttribute('aria-label'); checkbox.click();
            const key = { chords: 'varyLength', drums: 'patterns', melody: 'rerollSeeds' }[role];
            await waitFor(() => read().trackGenerationPolicies[role][key] === (checkbox.getAttribute('aria-pressed') === 'true'), role + ' stored policy');
            unchanged = JSON.stringify(read().tracks.find(item => item.id === track.id).generatorSettings) === before;
            synced = read().trackGenerationPolicies[role][key] === (checkbox.getAttribute('aria-pressed') === 'true');
            checkbox.click();
          }
          group.querySelector('button[aria-label="Lock reroll for ' + track.name + '"]').click();
          await waitFor(() => group.querySelector('button[aria-label="Reroll ' + track.name + '"]').disabled, role + ' lock');
          group.querySelector('button[aria-label="Unlock reroll for ' + track.name + '"]').click();
          await waitFor(() => !group.querySelector('button[aria-label="Reroll ' + track.name + '"]').disabled, role + ' unlock');
          group.querySelector('button[aria-label="Reroll ' + track.name + '"]').click();
          await waitFor(() => read().trackRerollCounts?.[role] === 1, role + ' rerolled');
          result.otherRoleRerolls.push({ role, unchanged, synced, changed: JSON.stringify(read().tracks.find(item => item.id === track.id).generatorSettings) !== before });
        }
        const rhythmColorProbe = document.createElement('span');
        rhythmColorProbe.style.color = 'color-mix(in srgb, #418c91 70%, #f2f7ff)';
        document.body.append(rhythmColorProbe);
        const expectedRhythmColor = getComputedStyle(rhythmColorProbe).color;
        rhythmColorProbe.remove();
        result.parameterAreas = [];
        result.generationMiniKnobs = [];
        for (const role of ['melody', 'bass', 'chords', 'arpeggio', 'drums']) {
          document.querySelector('.track-group.' + role + ' .group-select').click();
          await waitFor(() => document.querySelector('#part-inspector vc-part-settings-editor[data-control-role="' + role + '"]'), role + ' categories');
          const editors = [...document.querySelectorAll('vc-part-settings-editor')];
          for (const editor of editors.filter(editor => editor.classList.contains('quick-layout'))) {
            await document.fonts.ready;
            for (const label of editor.querySelectorAll('.control-name')) {
              const bounds = label.getBoundingClientRect();
              const card = label.closest('.part-control').getBoundingClientRect();
              if (label.scrollWidth > label.clientWidth + 1 || bounds.left < card.left || bounds.right > card.right) {
                throw new Error(role + ' quick title clipped: ' + label.textContent);
              }
            }
            for (const [key, title] of [['strumType', 'S.ARC'], ['patternJoinMode', 'JOIN']]) {
              const label = editor.querySelector('[data-control-key="' + key + '"] .control-name');
              if (label && label.textContent.trim() !== title) throw new Error(key + ' quick title should be ' + title);
            }
          }
          const pitch = editors.map(editor => editor.querySelector('[data-control-category="pitch"]'));
          const rhythm = editors.map(editor => editor.querySelector('[data-control-category="rhythm"]'));
          result.generationMiniKnobs.push(...[...document.querySelectorAll('.track-group.' + role + ' .named-mini')].map(knob => ({
            role, type: knob.querySelector('input').type, width: knob.getBoundingClientRect().width,
            diameter: knob.querySelector('.knob-face').getBoundingClientRect().width,
            code: knob.querySelector('.control-name').textContent.trim(),
            value: knob.querySelector('.knob-face > span').textContent.trim(), tooltip: knob.title,
          })));
          result.parameterAreas.push({ role, pitch: pitch.map(area => area ? getComputedStyle(area).getPropertyValue('--accent-primary').trim() : null),
            rhythm: rhythm.filter(Boolean).map(area => getComputedStyle(area).getPropertyValue('--accent-primary').trim()),
            backgrounds: rhythm.filter(Boolean).map(area => getComputedStyle(area).backgroundColor),
            knobs: rhythm.filter(Boolean).flatMap(area => [...area.querySelectorAll('.knob-face')])
              .map(knob => getComputedStyle(knob).backgroundImage.includes(expectedRhythmColor)) });
        }
        result.inspectorPolicyRemoved = !document.querySelector('#part-inspector vc-track-generation-policy');
        result.bassColor = getComputedStyle(document.querySelector('.track-group.bass .role-dot')).backgroundColor;
        const countBox = document.querySelector('.track-group.bass .group-count');
        result.countNearName = countBox.getBoundingClientRect().left - countBox.previousElementSibling.getBoundingClientRect().right < 10;
        result.countBox = getComputedStyle(countBox).borderTopStyle;
        const generateGroup = document.querySelector('.track-group.chords');
        generateGroup.querySelector('.group-select').click();
        await waitFor(() => generateGroup.querySelector('.policy-controls'), 'chords expanded by selection');
        const countInput = generateGroup.querySelector('input[aria-label="Chords track count"]');
        const visibleChordTracks = generateGroup.querySelectorAll('.track-select').length;
        generateGroup.querySelector('button[aria-label="Collapse Chords randomization settings"]').click();
        await waitFor(() => !generateGroup.querySelector('.policy-controls') && generateGroup.querySelectorAll('.track-select').length === visibleChordTracks, 'collapsed settings keep tracks visible');
        countInput.focus(); countInput.value = '3'; countInput.dispatchEvent(new Event('change', { bubbles: true }));
        await waitFor(() => JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')).tracks.filter(track => track.role === 'chords').length === 3, 'generate while collapsed');
        result.collapsedGenerate = countInput.value;
        await waitFor(() => generateGroup.querySelectorAll('.track-select').length === 3, 'new tracks visible while settings collapsed');
        result.separateGenerateRemoved = !generateGroup.querySelector('.generate-tracks');
        generateGroup.querySelector('button[aria-label="Expand Chords randomization settings"]').click();
        await waitFor(() => generateGroup.querySelector('.policy-controls'), 'expanded rules');
        result.countAfterExpand = countInput.value;
        await waitFor(() => generateGroup.querySelectorAll('.track-select').length === 3, 'Generate N adds tracks');
        result.generatedChords = JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')).tracks.filter(track => track.role === 'chords').length;
        generateGroup.querySelector('button[aria-label="Lock reroll for C3"]').click();
        countInput.value = '0'; countInput.dispatchEvent(new Event('change', { bubbles: true }));
        await waitFor(() => countInput.value === '1' && generateGroup.querySelectorAll('.track-select').length === 1, 'locked count floor');
        result.lockedCountTrack = JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')).tracks.find(track => track.role === 'chords').id;
        const arpGroup = document.querySelector('.track-group.arpeggio');
        const readProject = () => JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));
        const beforeInstrumentRules = JSON.stringify(readProject().tracks);
        arpGroup.querySelector('.group-select').click();
        await waitFor(() => arpGroup.querySelector('.policy-controls'), 'arpeggio expanded by selection');
        arpGroup.querySelector('button[aria-label="Randomize instruments"]').click();
        arpGroup.querySelector('button[aria-label="One instrument"]').click();
        const poolSelect = arpGroup.querySelector('select[aria-label="Instrument pool"]');
        poolSelect.value = 'MELODY'; poolSelect.dispatchEvent(new Event('change', { bubbles: true }));
        await waitFor(() => readProject().instrumentGenerationPolicies?.arpeggio?.pool === 'MELODY', 'instrument policy');
        result.instrumentRulesPreserveTracks = JSON.stringify(readProject().tracks) === beforeInstrumentRules;
        result.instrumentPolicy = readProject().instrumentGenerationPolicies.arpeggio;
        const arpTotal = arpGroup.querySelector('input[aria-label="Arps track count"]');
        arpTotal.value = '3'; arpTotal.dispatchEvent(new Event('change', { bubbles: true }));
        await waitFor(() => arpGroup.querySelectorAll('.track-select').length === 3, 'new instrument tracks');
        const arpRows = [...arpGroup.querySelectorAll('.track-row-card')];
        const lockedInstrument = arpRows[2].querySelector('.track-instrument');
        lockedInstrument.value = '127'; lockedInstrument.dispatchEvent(new Event('change', { bubbles: true }));
        const lastArp = readProject().tracks.filter(track => track.role === 'arpeggio')[2];
        arpRows[2].querySelector('button[aria-label="Lock reroll for ' + lastArp.name + '"]').click();
        await waitFor(() => arpRows[2].querySelector('button[aria-label="Reroll ' + lastArp.name + '"]').disabled, 'whole-track lock');
        const beforeInstrumentRoll = readProject();
        arpGroup.querySelector('button[aria-label="Reroll Arps tracks"]').click();
        await waitFor(() => readProject().trackRerollCounts.arpeggio > beforeInstrumentRoll.trackRerollCounts.arpeggio, 'combined roll');
        const rolledArps = readProject().tracks.filter(track => track.role === 'arpeggio');
        result.sharedArpInstrument = rolledArps[0].mix.program === rolledArps[1].mix.program;
        result.lockedArpInstrument = rolledArps[2].mix.program;
        result.lockedTrackPreserved = JSON.stringify(rolledArps[2])
          === JSON.stringify(beforeInstrumentRoll.tracks.find(track => track.id === lastArp.id));
        result.instrumentButtonsRemoved = !document.querySelector('.instrument-reroll-button, button[aria-label^="Lock instrument for"], button[aria-label^="Unlock instrument for"], button[aria-label^="Reroll instrument for"]');
        document.querySelector('.track-group.drums .track-select').click();
        await waitFor(() => document.querySelector('.track-name-field input')?.value === readTrack('drums').name, 'original snare inspector');
        [...document.querySelectorAll('.track-actions button')].find(button => button.textContent === 'Edit notes').click();
        await waitFor(() => [...document.querySelectorAll('.score-display-header button')].some(button => button.textContent.trim() === 'Cancel'), 'all-drum phrase editor');
        const drumLabels = [...document.querySelectorAll('.track-group.drums .track-instrument')]
          .map(select => select.selectedOptions[0].textContent);
        result.allDrumsEditor = await waitFor(() => document.querySelector('#inspector-title')?.textContent.trim().endsWith('Drums notes')
          && drumLabels.every(label => [...document.querySelectorAll('vc-score-canvas .drum-label')].some(row => row.title === label.toUpperCase())), 'all drum pitches in the score editor');
        [...document.querySelectorAll('.score-display-header button')].find(button => button.textContent.trim() === 'Cancel').click();
        await waitFor(() => !document.querySelector('.score-display-header')
          && !document.querySelector('.track-group.drums .group-select').disabled, 'close drum editor');
        const drumGroup = document.querySelector('.track-group.drums');
        drumGroup.querySelector('.group-select').click();
        await waitFor(() => drumGroup.querySelector('.policy-controls'), 'drums expanded by selection');
        const previousDrums = JSON.stringify(readProject().tracks);
        for (const [label,key] of [['Dynamic / Ghosts %','dynamicChance'],['Overrandomize %','overrandomizeChance'],['Follow Melody %','melodyChance']]) {
          const input = drumGroup.querySelector('input[aria-label="Reroll ' + label + '"]');
          if (!input) throw new Error('Missing drum policy: ' + label);
          input.value = '100'; input.dispatchEvent(new Event('change',{bubbles:true}));
          await waitFor(() => readProject().trackGenerationPolicies?.drums?.[key] === 100, label + ' policy commit');
          if (document.querySelector('#part-inspector input[aria-label="Reroll ' + label + '"]')
            || document.querySelector('.part-settings-panel input[aria-label="Reroll ' + label + '"]')) throw new Error('Policy outside Tracks header');
        }
        if (JSON.stringify(readProject().tracks) !== previousDrums) throw new Error('Drum policy edited existing parts');
        drumGroup.querySelector('button[aria-label="Randomize instruments"]').click();
        const drumCount = drumGroup.querySelector('input[aria-label="Drums track count"]');
        drumCount.value = '5'; drumCount.dispatchEvent(new Event('change', { bubbles: true }));
        await waitFor(() => drumGroup.querySelectorAll('.track-select').length === 5, 'generated drum kit');
        const beforeKitRoll = readProject().trackRerollCounts.drums;
        drumGroup.querySelector('button[aria-label="Reroll Drums tracks"]').click();
        await waitFor(() => readProject().trackRerollCounts.drums > beforeKitRoll, 'drum kit reroll');
        const kit = readProject().tracks.filter(track => track.role === 'drums');
        if (!kit.every(track => track.generatorSettings.ghosts === true)
          || !kit.filter(track => ![35,36,38,40].includes(track.generatorSettings.pitch)).every(track => track.generatorSettings.rhythm === 'melody'))
          throw new Error('Overrandomized drum policy not applied');
        await waitFor(() => [...drumGroup.querySelectorAll('.track-instrument')].every((picker, index) => Number(picker.value) === kit[index].generatorSettings.pitch), 'drum kit picker rendering');
        result.coreDrumKit = { kicks: kit.filter(track => [35,36].includes(track.generatorSettings.pitch)).length,
          snares: kit.filter(track => [38,40].includes(track.generatorSettings.pitch)).length,
          channels: kit.every(track => track.midiChannel === 10),
          pickersSynced: [...drumGroup.querySelectorAll('.track-instrument')].every((picker, index) => Number(picker.value) === kit[index].generatorSettings.pitch) };
        result.initiallyCollapsed = initiallyCollapsed;
        result.compactControls = [...document.querySelectorAll('.part-control.compact-control')].map(control => {
          const code = control.querySelector('.control-name, .toggle-button');
          const status = control.querySelector('.knob-status .inherit-snowflake, .toggle-status .inherit-snowflake');
          return { code: code.textContent.trim(), width: control.getBoundingClientRect().width,
            toggleHeight: control.querySelector('.toggle-button')?.getBoundingClientRect().height ?? null,
            tooltip: control.title, hasInheritanceStatus: !!status };
        });
        result.scoreCanvasPresent = !!document.querySelector('vc-score-canvas canvas');
        await fetch('/result', { method: 'POST', body: JSON.stringify(result) });
      } catch (error) {
        await fetch('/result', { method: 'POST', body: JSON.stringify({ error: error.message, stack: error.stack, diagnostic: { hits: [...document.querySelectorAll('input[aria-label="Hits"]')].map(input => input.value), role: document.querySelector('#part-inspector vc-part-settings-editor')?.getAttribute('data-control-role'), policy: JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'))?.trackGenerationPolicies?.arpeggio, track: JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'))?.tracks.find(track => track.role === 'arpeggio').generatorSettings } }) });
      }
    })();
  </script>`;
  let resolve, reject;
  const result = new Promise((done, fail) => { resolve = done; reject = fail; });
  result.catch(() => {}); // CDP setup can fail before the result is awaited.
  const server = createBrowserServer({ root, html: index.replace('</body>', script + '</body>'), resolve, reject });
  await listen(server);
  const profile = fs.mkdtempSync(path.resolve(__dirname, '../.angular/rhythm-picker-browser-'));
  const child = launchBrowser(browser, ['--headless', '--disable-gpu', '--no-first-run', '--disable-background-networking',
    '--disable-background-timer-throttling', '--disable-renderer-backgrounding', '--no-default-browser-check',
    '--user-data-dir=' + profile, 'http://127.0.0.1:' + server.address().port + '/'], { stdio: ['ignore', 'ignore', 'pipe'] });
  const watchdog = watchBrowser(child, reject, 30000);
  try {
    const actual = await result;
    assert.equal(actual.error, undefined, JSON.stringify(actual));
    assert.deepEqual(actual.initialSelection, ['33', '1']);
    assert.equal(actual.inlineActions.length, 5);
    assert.deepEqual(actual.instrumentDefaultChecks, [true, true, true, true, true]);
    for (const actions of actual.inlineActions) {
      assert.deepEqual(actions, { symbols: ['M', 'S', '⍟', '↻'], actionsBeforeInstrument: true, oneLine: true, fits: true });
    }
    assert.deepEqual(actual.inlineIdentity, { program: 0, channel: 5, bassProgram: 33, bassChannel: 1,
      selectionPreserved: true, noInspectorIdentity: true, noG: true, hiddenArrow: true });
    assert.deepEqual(actual.melodySelection, ['73', '4']);
    assert.deepEqual(actual.recreatedSelection, ['73', '4']);
    assert.equal(actual.initialDrumPitch, '36');
    assert.equal(actual.bassJoinInitial, 'NOJOIN');
    assert.deepEqual(actual.bassJoinOptions, ['NOJOIN', 'EXPAND', 'JOIN']);
    assert.equal(actual.bassJoinInspectorOnly, true);
    assert.equal(actual.bassJoinSaved, 'JOIN');
    assert.equal(actual.bassJoinRetained, 'JOIN');
    assert.equal(actual.rerollPolicyLeavesSettings, true);
    assert.equal(actual.rerollRuleCopies, 1);
    assert.equal(actual.rerollSettings.hitsPerPattern, 16);
    assert.equal(actual.rerollSettings.exceptionChance, 0);
    assert.equal(actual.rerollSettings.noteLengthMultiplier, 100);
    assert.equal(actual.rerollId, 'track-arpeggio-1');
    assert.deepEqual(actual.rerollDisplayedHits, ['16']);
    assert.equal(actual.rerollAllLocked, true);
    assert.deepEqual(actual.otherRoleRerolls, ['bass','chords','drums','melody'].map(role => ({ role, unchanged: true, synced: true, changed: true })));
    assert.equal(actual.generatedChords, 3);
    assert.equal(actual.collapsedGenerate, '3');
    assert.equal(actual.separateGenerateRemoved, true);
    assert.equal(actual.lockedCountTrack, 'track-chords-3');
    assert.equal(actual.instrumentRulesPreserveTracks, true);
    assert.deepEqual(actual.instrumentPolicy, { randomize: true, pool: 'MELODY', sameInstrument: true });
    assert.equal(actual.sharedArpInstrument, true);
    assert.equal(actual.lockedArpInstrument, 127);
    assert.equal(actual.lockedTrackPreserved, true);
    assert.equal(actual.instrumentButtonsRemoved, true);
    assert.deepEqual(actual.coreDrumKit, { kicks: 2, snares: 1, channels: true, pickersSynced: true });
    assert.equal(actual.countAfterExpand, '3');
    assert.equal(actual.initiallyCollapsed, true);
    assert.equal(actual.scoreCanvasPresent, true);
    assert.ok(actual.compactControls.length > 0);
    assert.ok(actual.compactControls.every(control => [...control.code].length <= 5 && control.width <= 75 && control.tooltip && !control.hasInheritanceStatus), JSON.stringify(actual.compactControls));
    assert.ok(actual.compactControls.filter(control => control.toggleHeight !== null).every(control => control.toggleHeight <= 26));
    assert.ok(actual.generationMiniKnobs.length > 10);
    assert.ok(actual.generationMiniKnobs.every(knob => knob.type === 'range' && knob.width <= 48 && knob.diameter <= 28
      && [...knob.code].length <= 5 && Number.isFinite(Number(knob.value)) && knob.tooltip), JSON.stringify(actual.generationMiniKnobs));
    assert.equal(actual.inspectorPolicyRemoved, true);
    assert.equal(actual.bassColor, 'rgb(140, 169, 186)');
    assert.equal(actual.countNearName, true);
    assert.equal(actual.countBox, 'solid');
    for (const areas of actual.parameterAreas) {
      const pitchColors = areas.pitch.filter(Boolean);
      if (areas.role === 'drums') assert.deepEqual(pitchColors, []);
      else {
        assert.ok(pitchColors.length > 0, areas.role + ' has pitch controls');
        assert.ok(pitchColors.every(color => color === 'color-mix(in srgb, #7e70a6 70%, #f2f7ff)'), areas.role + ' pitch category color follows theme text');
      }
      assert.ok(areas.rhythm.length > 0);
      assert.ok(areas.rhythm.every(color => color === 'color-mix(in srgb, #418c91 70%, #f2f7ff)'), areas.role + ' rhythm category color follows theme text');
      assert.equal(new Set(areas.backgrounds).size, 1);
      assert.ok(areas.knobs.length > 0 && areas.knobs.every(Boolean), areas.role + ' rhythm knob colors');
    }
    assert.equal(new Set(actual.parameterAreas.map(areas => areas.backgrounds[0])).size, 1);
    assert.deepEqual(actual.sharedRoles, [
      { role: 'bass', hits: 5, seed: 0, delays: 2, grids: 1, pause: 0, split: 0, repeat: 0 },
      { role: 'arpeggio', hits: 5, seed: 0, delays: 2, grids: 1, pause: 1, split: 1, repeat: 1 },
    ]);
    assert.deepEqual(actual.drumRhythmOptions, [[...expected, 'melody']]);
    assert.deepEqual(actual.drumGridCounts, [4]);
    assert.deepEqual(actual.drumVelocityCounts, [4]);
    assert.equal(actual.drumChannelLocked, true);
    assert.equal(actual.drumSettings.pitch, 38);
    assert.equal(actual.drumSettings.rhythm, 'custom');
    assert.equal(actual.drumSettings.customPattern[0], 0);
    assert.equal(actual.drumSettings.useCustomVelocities, true);
    assert.equal(actual.drumPitch, '38');
    assert.equal(actual.drumName, 'Snare · 38');
    assert.equal(actual.allDrumsEditor, true);
    assert.deepEqual(actual.initialChords, ['C', 'G', 'Am', 'F']);
    assert.deepEqual(actual.options, [expected]);
    assert.deepEqual(actual.counts, [8]);
    assert.deepEqual(actual.selected, ['custom']);
    assert.equal(actual.rhythm, 'custom');
    assert.equal(actual.grid.length, 32);
    assert.deepEqual(actual.grid.slice(0, 8), [0, 1, 1, 1, 1, 1, 1, 1]);
    assert.deepEqual(actual.wheelRhythm, ['full']);
    assert.equal(actual.volumeBeforeRelease, 100);
    assert.equal(actual.mixDefaultsMuted, true);
    assert.equal(actual.roundedUnityMuted, true);
    assert.equal(actual.volumeScrollSteps, true);
    assert.equal(actual.tinyGainVisible, true);
    assert.equal(actual.panEndpointsAccent, true);
    assert.deepEqual(actual.attenuationAccent, actual.configuredAccent);
    assert.deepEqual(actual.boostAccent, actual.configuredAccent.map(channel => 255 - channel));
    assert.equal(actual.quickMix.volumePercent, 50);
    assert.equal(actual.quickMix.panPercent, 1);
    assert.equal(actual.quickMix.muted, false);
    assert.equal(actual.quickMix.solo, true);
    assert.equal(actual.globalAfterLive, true);
    assert.equal(actual.groupAfterName, true);
    assert.equal(actual.exclusiveMix, true);
    assert.equal(actual.panLabel, '1% R');
    assert.deepEqual(actual.miniWidths, [34, 34]);
    assert.equal(actual.volumeCenter, 'dB');
    assert.equal(actual.volumeAccessibleValue, '-6.0 dB');
    assert.deepEqual(actual.colors, actual.mixColors, 'track mix buttons use their semantic active colors');
    assert.notDeepEqual(actual.colors[0], actual.colors[1], 'mute and solo remain visually distinct');
    assert.equal(actual.controlsFit, true);
    assert.equal(actual.groupStillSelected, 'true');
    assert.deepEqual(actual.velocityCounts, [8]);
    assert.equal(actual.velocityBeforeCommit, undefined);
    assert.equal(actual.storedBeforeCommit, 79);
    assert.equal(actual.customVelocities.length, 32);
    assert.deepEqual(actual.customVelocities.slice(0, 3), [22, 80, 79]);
    assert.deepEqual(actual.retainedVelocities, actual.customVelocities);
    assert.equal(actual.randomBoundsHidden, true);
    assert.equal(actual.velocitiesEnabled, true);
    assert.deepEqual(actual.melodyInitialTargets, ['0, 2, 2, 4']);
    assert.deepEqual(actual.melody.chordNoteChoices, [0, 4, -2]);
    assert.deepEqual(actual.melody.melodyPatternOffsets, [1, -1, 2]);
    assert.equal(actual.melody.transpose, 7);
    assert.equal(actual.melodyHasSpeed, 1);
    assert.equal(actual.melodyHasAccent, 1);
  } finally {
    await closeBrowser({ child, server, watchdog });
  }
});
