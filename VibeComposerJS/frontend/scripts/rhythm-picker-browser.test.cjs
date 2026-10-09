const assert = require('node:assert/strict');
const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');
const { spawn } = require('node:child_process');
const test = require('node:test');
const ts = require('typescript');

const browser = process.env.OVERVIEW_TEST_BROWSER || [
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  '/usr/bin/chromium', '/usr/bin/chromium-browser', '/usr/bin/google-chrome',
].find((file) => fs.existsSync(file));

test('production Angular controls initialize selects from settings and support rhythm, wheel and mix edits', {
  skip: browser ? false : 'Set OVERVIEW_TEST_BROWSER to a Chromium executable',
}, async () => {
  const root = path.resolve(__dirname, '../dist/browser');
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
        for (let attempt = 0; attempt < 300; attempt++) {
          const result = read(); if (result) return result;
          await new Promise(resolve => setTimeout(resolve, 50));
        }
        throw new Error('Angular UI did not reach expected state: ' + label);
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
        const initialChords = [...document.querySelectorAll('select[aria-label^="Chord "]')].map(select => select.value);
        document.querySelector('.track-group.melody .track-select').click();
        await waitFor(() => document.querySelector('.selection-kicker')?.textContent.includes('CHANNEL 04'), 'melody inspector');
        const melodySelection = [...document.querySelectorAll('.track-group.melody .track-inline-select')].map(select => select.value);
        document.querySelector('button[aria-label="Open mixer"]').click();
        const strips = await waitFor(() => {
          const items = [...document.querySelectorAll('.channel-strip')];
          return items.length === 5 ? items : null;
        }, 'initial mixer strips');
        const mixerSelections = strips.map(strip => [...strip.querySelectorAll('select')].map(select => select.value));
        document.querySelector('button[aria-label="Close mixer"]').click();
        const group = await waitFor(() => document.querySelector('.track-group.chords .group-select'));
        group.click();
        const pickers = await waitFor(() => {
          const result = [...document.querySelectorAll('select[aria-label="Rhythm pattern"]')];
          return result.length === 2 ? result : null;
        });
        const options = pickers.map(select => [...select.options].map(option => option.value));
        pickers[0].value = 'custom';
        pickers[0].dispatchEvent(new Event('change', { bubbles: true }));
        const grids = await waitFor(() => {
          const result = [...document.querySelectorAll('.custom-pattern-grid')];
          return result.length === 2 ? result : null;
        });
        const counts = grids.map(grid => grid.querySelectorAll('button').length);
        grids[0].querySelector('button').click();
        await waitFor(() => [...document.querySelectorAll('.custom-pattern-grid button:first-child')]
          .every(button => button.getAttribute('aria-pressed') === 'false'));
        const project = JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));
        const result = { inlineActions, inlineIdentity, initialSelection, melodySelection, mixerSelections, initialChords, options, counts, selected: pickers.map(select => select.value),
          rhythm: project.chords.rhythm, grid: project.chords.customPattern };
        const wheel = (element, deltaY, shiftKey = false) => element.dispatchEvent(
          new WheelEvent('wheel', { deltaY, shiftKey, bubbles: true, cancelable: true }));
        wheel(pickers[0], 100);
        await waitFor(() => pickers.every(select => select.value === 'full'));
        result.wheelRhythm = pickers.map(select => select.value);
        const track = document.querySelector('.track-group.chords .track-row-card');
        const pan = track.querySelector('input[aria-label="Pan C1"]');
        const volume = track.querySelector('input[aria-label="Volume C1"]');
        wheel(pan, -100, true);
        await waitFor(() => pan.value === '1');
        // Synthetic pointer events exercise Angular's actual handlers and signals;
        // unit coverage separately checks pointer capture and vertical-only movement.
        volume.setPointerCapture = () => {};
        const pointer = (type, y) => volume.dispatchEvent(new PointerEvent(type,
          { pointerId: 17, button: 0, clientY: y, bubbles: true, cancelable: true }));
        pointer('pointerdown', 100); pointer('pointermove', 175);
        await waitFor(() => volume.closest('.compact-knob').querySelector('output').textContent.trim() === '-6.0');
        result.volumeBeforeRelease = JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')).mix.chords.volumePercent;
        pointer('pointerup', 175);
        await waitFor(() => volume.value === '50');
        track.querySelector('.mute-toggle').click(); track.querySelector('.solo-toggle').click();
        await waitFor(() => track.querySelector('.mute-toggle').classList.contains('active')
          && track.querySelector('.solo-toggle').classList.contains('active'));
        result.quickMix = JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')).mix.chords;
        result.panLabel = pan.closest('.compact-knob').querySelector('output').textContent.trim();
        result.miniWidths = [pan, volume].map(input => input.closest('.compact-knob').getBoundingClientRect().width);
        result.volumeCenter = volume.closest('.compact-knob').querySelector('.knob-face > span').textContent.trim();
        result.volumeAccessibleValue = volume.getAttribute('aria-valuetext');
        result.colors = ['.mute-toggle', '.solo-toggle'].map(selector => getComputedStyle(track.querySelector(selector)).backgroundColor);
        const bounds = track.getBoundingClientRect();
        result.controlsFit = track.querySelector('.track-mix-controls').getBoundingClientRect().right <= bounds.right;
        result.groupStillSelected = group.getAttribute('aria-pressed');
        const velocitySwitches = [...document.querySelectorAll('input[aria-label="Custom velocities"]')];
        velocitySwitches[0].click();
        const velocityGrids = await waitFor(() => {
          const result = [...document.querySelectorAll('.velocity-pattern-grid')];
          return result.length === 2 ? result : null;
        }, 'velocity grids enabled');
        result.velocityCounts = velocityGrids.map(grid => grid.querySelectorAll('input').length);
        const velocity = velocityGrids[0].querySelector('input');
        velocity.value = '22'; velocity.dispatchEvent(new Event('input', { bubbles: true }));
        await waitFor(() => velocityGrids[0].querySelector('output').textContent.trim() === '22', 'velocity preview');
        result.velocityBeforeCommit = JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')).chords.customVelocities;
        result.inspectorBeforeCommit = velocityGrids[1].querySelector('input').value;
        velocity.dispatchEvent(new Event('change', { bubbles: true }));
        await waitFor(() => velocityGrids.every(grid => grid.querySelector('input').value === '22'), 'velocity commit reflected in both editors');
        const secondVelocity = velocityGrids[0].querySelectorAll('input')[1];
        wheel(secondVelocity, -100, true);
        await waitFor(() => secondVelocity.value === '80', 'velocity wheel update');
        result.customVelocities = JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')).chords.customVelocities;
        result.randomBoundsHidden = !document.querySelector('input[aria-label="Min velocity"]');
        velocitySwitches[0].click();
        await waitFor(() => !document.querySelector('.velocity-pattern-grid'), 'velocity grids disabled');
        result.retainedVelocities = JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')).chords.customVelocities;
        velocitySwitches[0].click();
        await waitFor(() => document.querySelector('.velocity-pattern-grid input')?.value === '22', 'velocity grids restored');
        result.velocitiesEnabled = JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')).chords.useCustomVelocities;
        document.querySelector('.track-group.melody .group-select').click();
        const targets = await waitFor(() => {
          const inputs = [...document.querySelectorAll('input[aria-label="Note targets"]')];
          return inputs.length === 2 ? inputs : null;
        }, 'melody minimum editors');
        result.melodyInitialTargets = targets.map(input => input.value);
        targets[0].focus(); targets[0].value = '0, 4, -2';
        targets[0].dispatchEvent(new Event('change', { bubbles: true }));
        await waitFor(() => targets.every(input => input.value === '0, 4, -2'), 'shared melody target commit');
        const structure = document.querySelector('input[aria-label="Block structure"]');
        structure.focus(); structure.value = '1, -1, 2';
        structure.dispatchEvent(new Event('change', { bubbles: true }));
        const transpose = document.querySelector('input[aria-label="Transpose"]');
        wheel(transpose, -100, true);
        await waitFor(() => transpose.getAttribute('aria-valuetext') === '5 st', 'melody discrete transpose');
        wheel(transpose, -100, true);
        await waitFor(() => transpose.getAttribute('aria-valuetext') === '7 st', 'melody next discrete transpose');
        result.melody = JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')).melody;
        result.melodyHasSpeed = document.querySelectorAll('input[aria-label="Speed"]').length;
        result.melodyHasAccent = document.querySelectorAll('input[aria-label="Accent"]').length;
        document.querySelector('.track-group.melody .track-select').click();
        await waitFor(() => document.querySelector('.selection-kicker')?.textContent.includes('CHANNEL 04'), 'melody track context');
        result.recreatedSelection = [...document.querySelectorAll('.track-group.melody .track-inline-select')].map(select => select.value);
        result.sharedRoles = [];
        for (const role of ['bass', 'arpeggio']) {
          document.querySelector('.track-group.' + role + ' .group-select').click();
          await waitFor(() => [...document.querySelectorAll('vc-part-settings-editor')]
            .every(editor => editor.getAttribute('data-control-role') === role), role + ' editor context');
          const rhythms = await waitFor(() => {
            const controls = [...document.querySelectorAll('select[aria-label="Rhythm pattern"]')];
            return controls.length === 2 ? controls : null;
          }, role + ' rhythm controls');
          rhythms[0].value = 'custom'; rhythms[0].dispatchEvent(new Event('change', { bubbles: true }));
          await waitFor(() => document.querySelectorAll('.custom-pattern-grid').length === 2, role + ' grids');
          const hits = document.querySelector('input[aria-label="Hits"]');
          hits.value = '5'; hits.dispatchEvent(new Event('change', { bubbles: true }));
          await waitFor(() => [...document.querySelectorAll('.custom-pattern-grid')].every(grid => grid.children.length === 5), role + ' Hits');
          const seeds = [...document.querySelectorAll('input[aria-label="Seed"]')];
          seeds[0].focus(); seeds[0].value = '-123'; seeds[0].dispatchEvent(new Event('change', { bubbles: true }));
          await waitFor(() => seeds.every(seed => seed.value === '-123'), role + ' seed sync');
          const delays = document.querySelector('input[aria-label="Delays"]');
          delays.value = '2'; delays.dispatchEvent(new Event('change', { bubbles: true }));
          await waitFor(() => [...document.querySelectorAll('input[aria-label="Delays"]')].every(input => input.value === '2'), role + ' feedback sync');
          document.querySelector('#part-inspector input[aria-label="Generate notes"]').click();
          await waitFor(() => [...document.querySelectorAll('input[aria-label="Generate notes"]')].every(input => !input.checked), role + ' generation toggle');
          document.querySelector('#part-inspector input[aria-label="Generate notes"]').click();
          await waitFor(() => [...document.querySelectorAll('input[aria-label="Generate notes"]')].every(input => input.checked), role + ' generation restored');
          [...document.querySelectorAll('button')].find(button => button.textContent.trim() === 'Clear seed').click();
          await waitFor(() => seeds.every(seed => seed.value === '0'), role + ' seed cleared');
          const state = JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'))[role];
          result.sharedRoles.push({ role, hits: state.hitsPerPattern, seed: state.patternSeed, delays: state.feedbackCount,
            grids: document.querySelectorAll('.custom-pattern-grid').length,
            pause: document.querySelectorAll('input[aria-label="Pause"]').length,
            split: document.querySelectorAll('input[aria-label="Split"]').length,
            repeat: document.querySelectorAll('input[aria-label="Repeat"]').length });
        }
        document.querySelector('.track-group.arpeggio .group-select').click();
        await waitFor(() => document.querySelector('#part-inspector vc-part-settings-editor[data-control-role="arpeggio"]'), 'Arpeggio musical inspector');
        const readArp = () => JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')).tracks.find(track => track.role === 'arpeggio');
        const beforePolicy = JSON.stringify(readArp().generatorSettings);
        const randomHits = [...document.querySelectorAll('input[aria-label="Reroll Random hits"]')];
        randomHits[0].click();
        await waitFor(() => !document.querySelector('input[aria-label="Reroll Arp hits"]').disabled, 'fixed hits enabled');
        const setPolicyNumber = async (label, value) => {
          const input = document.querySelector('input[aria-label="Reroll ' + label + '"]');
          input.value = String(value); input.dispatchEvent(new Event('change', { bubbles: true }));
          await waitFor(() => [...document.querySelectorAll('input[aria-label="Reroll ' + label + '"]')].every(input => input.value === String(value)), label);
        };
        await setPolicyNumber('Arp hits', 16);
        await setPolicyNumber('Max split %', 0);
        await setPolicyNumber('Min length %', 100);
        await setPolicyNumber('Max repeat', 1);
        document.querySelector('input[aria-label="Reroll Random span"]').click();
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
        result.rerollAllLocked = document.querySelector('button[aria-label="Reroll Arpeggio tracks"]').disabled;
        document.querySelector('button[aria-label="Unlock reroll for A1"]').click();
        await waitFor(() => !document.querySelector('button[aria-label="Reroll A1"]').disabled, 'reroll unlock');
        document.querySelector('.track-group.bass .group-select').click();
        const bassJoin = await waitFor(() => document.querySelector('select[aria-label="Pattern join"]'), 'bass advanced join');
        result.bassJoinInitial = bassJoin.value;
        result.bassJoinOptions = [...bassJoin.options].map(option => option.value);
        result.bassJoinInspectorOnly = document.querySelectorAll('select[aria-label="Pattern join"]').length === 1
          && !!bassJoin.closest('#part-inspector');
        bassJoin.value = 'JOIN'; bassJoin.dispatchEvent(new Event('change', { bubbles: true }));
        await waitFor(() => JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')).bass.patternJoinMode === 'JOIN', 'persisted bass join');
        result.bassJoinSaved = JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')).tracks.find(track => track.role === 'bass').generatorSettings.patternJoinMode;
        const bassRhythm = document.querySelector('select[aria-label="Rhythm pattern"]');
        bassRhythm.value = 'alternating'; bassRhythm.dispatchEvent(new Event('change', { bubbles: true }));
        await waitFor(() => !document.querySelector('select[aria-label="Pattern join"]'), 'alternating hides join');
        bassRhythm.value = 'custom'; bassRhythm.dispatchEvent(new Event('change', { bubbles: true }));
        result.bassJoinRetained = (await waitFor(() => document.querySelector('select[aria-label="Pattern join"]'), 'grid restores join')).value;
        document.querySelector('.track-group.drums .track-select').click();
        await waitFor(() => document.querySelector('#part-inspector vc-part-settings-editor[data-control-role="drums"]'), 'drum musical context');
        const drumPitch = await waitFor(() => document.querySelector('.track-group.drums .track-instrument'), 'drum pitch picker');
        result.initialDrumPitch = drumPitch.value;
        drumPitch.value = '38'; drumPitch.dispatchEvent(new Event('change', { bubbles: true }));
        await waitFor(() => document.querySelector('.track-group.drums .track-instrument').selectedOptions[0].textContent === 'Snare · 38', 'drum track name');
        const drumRhythms = [...document.querySelectorAll('select[aria-label="Rhythm pattern"]')];
        result.drumRhythmOptions = drumRhythms.map(select => [...select.options].map(option => option.value));
        drumRhythms[0].value = 'custom'; drumRhythms[0].dispatchEvent(new Event('change', { bubbles: true }));
        const drumGrids = await waitFor(() => {
          const grids = [...document.querySelectorAll('.custom-pattern-grid')];
          return grids.length === 2 ? grids : null;
        }, 'drum custom rhythm');
        result.drumGridCounts = drumGrids.map(grid => grid.querySelectorAll('button').length);
        drumGrids[0].querySelector('button').click();
        await waitFor(() => drumGrids.every(grid => grid.querySelector('button').getAttribute('aria-pressed') === 'false'), 'shared drum custom grid edit');
        const drumVelocities = [...document.querySelectorAll('input[aria-label="Custom velocities"]')];
        drumVelocities[0].click();
        await waitFor(() => document.querySelectorAll('.velocity-pattern-grid').length === 2, 'drum velocity grids');
        result.drumVelocityCounts = [...document.querySelectorAll('.velocity-pattern-grid')].map(grid => grid.querySelectorAll('input').length);
        result.drumChannelLocked = document.querySelector('.track-group.drums .track-channel').disabled;
        result.drumSettings = JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')).tracks.find(track => track.role === 'drums').generatorSettings;
        document.querySelector('button[aria-label="Open mixer"]').click();
        const mixerDrum = await waitFor(() => document.querySelector('.channel-strip select[aria-label="Percussion instrument D1"]'), 'mixer drum identity');
        result.mixerDrumPitch = mixerDrum.value;
        result.mixerDrumName = mixerDrum.selectedOptions[0].textContent;
        document.querySelector('button[aria-label="Close mixer"]').click();
        document.querySelector('button[aria-label="Add Drums track"]').click();
        await waitFor(() => document.querySelectorAll('.track-group.drums .track-select').length === 2, 'second drum track');
        document.querySelector('button[aria-label="Add Drums track"]').click();
        await waitFor(() => document.querySelectorAll('.track-group.drums .track-select').length === 3, 'third drum track');
        result.scoreRowLabels = [...document.querySelectorAll('.score-track-labels .score-track-label')].map(label => label.textContent.trim());
        result.selectedDrumRow = document.querySelector('.score-track-labels .score-track-label.selected')?.textContent.trim();
        result.otherRoleRerolls = [];
        for (const role of ['bass', 'chords', 'drums', 'melody']) {
          const group = document.querySelector('.track-group.' + role);
          group.querySelector('.group-select').click();
          await waitFor(() => document.querySelector('#part-inspector vc-part-settings-editor[data-control-role="' + role + '"]'), role + ' musical settings');
          const read = () => JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));
          const track = read().tracks.find(track => track.role === role);
          const before = JSON.stringify(track.generatorSettings);
          const checkbox = group.querySelector('.track-policy-header input[type="checkbox"]');
          let unchanged = true, synced = true;
          if (checkbox) {
            const label = checkbox.getAttribute('aria-label'); checkbox.click();
            const key = { chords: 'varyLength', drums: 'patterns', melody: 'rerollSeeds' }[role];
            await waitFor(() => read().trackGenerationPolicies[role][key] === checkbox.checked, role + ' stored policy');
            unchanged = JSON.stringify(read().tracks.find(item => item.id === track.id).generatorSettings) === before;
            synced = read().trackGenerationPolicies[role][key] === checkbox.checked;
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
        result.parameterAreas = [];
        for (const role of ['melody', 'bass', 'chords', 'arpeggio', 'drums']) {
          document.querySelector('.track-group.' + role + ' .group-select').click();
          await waitFor(() => document.querySelector('#part-inspector vc-part-settings-editor[data-control-role="' + role + '"]'), role + ' categories');
          const editors = [...document.querySelectorAll('vc-part-settings-editor')];
          const pitch = editors.map(editor => editor.querySelector('[data-control-category="pitch"]'));
          const rhythm = editors.map(editor => editor.querySelector('[data-control-category="rhythm"]'));
          result.parameterAreas.push({ role, pitch: pitch.map(area => area ? getComputedStyle(area).getPropertyValue('--accent-primary').trim() : null),
            rhythm: rhythm.map(area => getComputedStyle(area).getPropertyValue('--accent-primary').trim()),
            backgrounds: rhythm.map(area => getComputedStyle(area).backgroundColor),
            knobs: rhythm.map(area => getComputedStyle(area.querySelector('.knob-face')).backgroundImage.includes('86, 182, 188')) });
        }
        result.inspectorPolicyRemoved = !document.querySelector('#part-inspector vc-track-generation-policy');
        result.bassBlue = getComputedStyle(document.querySelector('.track-group.bass .role-dot')).backgroundColor;
        const countBox = document.querySelector('.track-group.bass .group-count');
        result.countNearName = countBox.getBoundingClientRect().left - countBox.previousElementSibling.getBoundingClientRect().right < 10;
        result.countBox = getComputedStyle(countBox).borderTopStyle;
        const generateGroup = document.querySelector('.track-group.chords');
        const countInput = generateGroup.querySelector('input[aria-label="Chords track count"]');
        generateGroup.querySelector('button[aria-label="Collapse Chords tracks"]').click();
        await waitFor(() => !generateGroup.querySelector('.policy-controls') && !generateGroup.querySelector('.track-select'), 'collapsed group');
        countInput.focus(); countInput.value = '3'; countInput.dispatchEvent(new Event('change', { bubbles: true }));
        await waitFor(() => JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')).tracks.filter(track => track.role === 'chords').length === 3, 'generate while collapsed');
        result.collapsedGenerate = countInput.value;
        result.separateGenerateRemoved = !generateGroup.querySelector('.generate-tracks');
        generateGroup.querySelector('button[aria-label="Expand Chords tracks"]').click();
        await waitFor(() => generateGroup.querySelector('.policy-controls'), 'expanded rules');
        result.countAfterExpand = countInput.value;
        await waitFor(() => generateGroup.querySelectorAll('.track-select').length === 3, 'Generate N adds tracks');
        result.generatedChords = JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')).tracks.filter(track => track.role === 'chords').length;
        generateGroup.querySelector('button[aria-label="Lock reroll for C3"]').click();
        countInput.value = '0'; countInput.dispatchEvent(new Event('change', { bubbles: true }));
        await waitFor(() => countInput.value === '1' && generateGroup.querySelectorAll('.track-select').length === 1, 'locked count floor');
        result.lockedCountTrack = JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')).tracks.find(track => track.role === 'chords').id;
        document.querySelector('.track-group.drums .track-select').click();
        await waitFor(() => document.querySelector('.selection-kicker')?.textContent.includes('CHANNEL 10'), 'original snare inspector');
        [...document.querySelectorAll('.track-actions button')].find(button => button.textContent === 'Edit notes').click();
        await waitFor(() => document.querySelector('.voice-label'), 'single-pitch drum editor');
        result.drumEditorVoices = [...document.querySelectorAll('.voice-label')].map(label => label.textContent);
        await fetch('/result', { method: 'POST', body: JSON.stringify(result) });
      } catch (error) {
        await fetch('/result', { method: 'POST', body: JSON.stringify({ error: error.message, diagnostic: { hits: [...document.querySelectorAll('input[aria-label="Hits"]')].map(input => input.value), role: document.querySelector('#part-inspector vc-part-settings-editor')?.getAttribute('data-control-role'), policy: JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'))?.trackGenerationPolicies?.arpeggio, track: JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'))?.arpeggio } }) });
      }
    })();
  </script>`;
  let resolve, reject;
  const result = new Promise((done, fail) => { resolve = done; reject = fail; });
  const server = http.createServer((request, response) => {
    if (request.url === '/result') {
      let body = '';
      request.on('data', chunk => { body += chunk; });
      request.on('end', () => {
        response.end('OK');
        try { resolve(JSON.parse(body)); } catch (error) { reject(error); }
      });
      return;
    }
    const pathname = new URL(request.url, 'http://localhost').pathname;
    if (pathname === '/') {
      response.setHeader('Content-Type', 'text/html');
      response.end(index.replace('</body>', script + '</body>'));
      return;
    }
    const file = path.resolve(root, '.' + pathname);
    if (path.relative(root, file).startsWith('..') || !fs.existsSync(file) || !fs.statSync(file).isFile()) {
      response.writeHead(404); response.end(); return;
    }
    response.setHeader('Content-Type', file.endsWith('.js') ? 'text/javascript'
      : file.endsWith('.css') ? 'text/css' : 'application/octet-stream');
    fs.createReadStream(file).pipe(response);
  });
  await new Promise(done => server.listen(0, '127.0.0.1', done));
  const profile = fs.mkdtempSync(path.resolve(__dirname, '../.angular/rhythm-picker-browser-'));
  const child = spawn(browser, ['--headless', '--disable-gpu', '--no-first-run', '--disable-background-networking',
    '--disable-background-timer-throttling', '--disable-renderer-backgrounding', '--no-default-browser-check',
    '--user-data-dir=' + profile, 'http://127.0.0.1:' + server.address().port + '/'], { stdio: ['ignore', 'ignore', 'pipe'] });
  let errors = '';
  child.stderr.on('data', chunk => { errors = (errors + chunk).slice(-2000); });
  child.on('error', reject);
  child.on('exit', code => reject(new Error('Chromium exited (' + code + '): ' + errors)));
  const timeout = setTimeout(() => reject(new Error('Chromium timed out: ' + errors)), 30000);
  try {
    const actual = await result;
    assert.equal(actual.error, undefined, JSON.stringify(actual));
    assert.deepEqual(actual.initialSelection, ['33', '1']);
    assert.equal(actual.inlineActions.length, 5);
    for (const actions of actual.inlineActions) {
      assert.deepEqual(actions, { symbols: ['⍟', '↻'], actionsBeforeInstrument: true, oneLine: true, fits: true });
    }
    assert.deepEqual(actual.inlineIdentity, { program: 0, channel: 5, bassProgram: 33, bassChannel: 1,
      selectionPreserved: true, noInspectorIdentity: true, noG: true, hiddenArrow: true });
    assert.deepEqual(actual.melodySelection, ['73', '4']);
    assert.deepEqual(actual.recreatedSelection, ['73', '4']);
    assert.deepEqual(actual.mixerSelections, [['73', '4'], ['33', '1'], ['0', '2'], ['11', '3'], ['36', '10']]);
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
    assert.deepEqual(actual.rerollDisplayedHits, ['16', '16']);
    assert.equal(actual.rerollAllLocked, true);
    assert.deepEqual(actual.otherRoleRerolls, ['bass','chords','drums','melody'].map(role => ({ role, unchanged: true, synced: true, changed: true })));
    assert.equal(actual.generatedChords, 3);
    assert.equal(actual.collapsedGenerate, '3');
    assert.equal(actual.separateGenerateRemoved, true);
    assert.equal(actual.lockedCountTrack, 'track-chords-3');
    assert.equal(actual.countAfterExpand, '3');
    assert.equal(actual.inspectorPolicyRemoved, true);
    assert.equal(actual.bassBlue, 'rgb(34, 167, 242)');
    assert.equal(actual.countNearName, true);
    assert.equal(actual.countBox, 'solid');
    for (const areas of actual.parameterAreas) {
      assert.deepEqual(areas.pitch, areas.role === 'drums' ? [null, null] : ['#a49ac9', '#a49ac9']);
      assert.deepEqual(areas.rhythm, ['#56b6bc', '#56b6bc']);
      assert.equal(areas.backgrounds[0], areas.backgrounds[1]);
      assert.deepEqual(areas.knobs, [true, true]);
    }
    assert.equal(new Set(actual.parameterAreas.map(areas => areas.backgrounds[0])).size, 1);
    assert.deepEqual(actual.sharedRoles, [
      { role: 'bass', hits: 5, seed: 0, delays: 2, grids: 2, pause: 0, split: 0, repeat: 0 },
      { role: 'arpeggio', hits: 5, seed: 0, delays: 2, grids: 2, pause: 2, split: 2, repeat: 2 },
    ]);
    assert.deepEqual(actual.drumRhythmOptions, [expected, expected]);
    assert.deepEqual(actual.drumGridCounts, [4, 4]);
    assert.deepEqual(actual.drumVelocityCounts, [4, 4]);
    assert.equal(actual.drumChannelLocked, true);
    assert.equal(actual.drumSettings.pitch, 38);
    assert.equal(actual.drumSettings.rhythm, 'custom');
    assert.equal(actual.drumSettings.customPattern[0], 0);
    assert.equal(actual.drumSettings.useCustomVelocities, true);
    assert.equal(actual.mixerDrumPitch, '38');
    assert.equal(actual.mixerDrumName, 'Snare · 38');
    assert.deepEqual(actual.scoreRowLabels, ['M1', 'B1', 'C1', 'A1', 'Drums']);
    assert.equal(actual.selectedDrumRow, 'Drums');
    assert.deepEqual(actual.drumEditorVoices, ['Snare']);
    assert.deepEqual(actual.initialChords, ['1', '5', '6', '4']);
    assert.deepEqual(actual.options, [expected, expected]);
    assert.deepEqual(actual.counts, [8, 8]);
    assert.deepEqual(actual.selected, ['custom', 'custom']);
    assert.equal(actual.rhythm, 'custom');
    assert.equal(actual.grid.length, 32);
    assert.deepEqual(actual.grid.slice(0, 8), [0, 1, 1, 1, 1, 1, 1, 1]);
    assert.deepEqual(actual.wheelRhythm, ['full', 'full']);
    assert.equal(actual.volumeBeforeRelease, 100);
    assert.equal(actual.quickMix.volumePercent, 50);
    assert.equal(actual.quickMix.panPercent, 1);
    assert.equal(actual.panLabel, '1% R');
    assert.deepEqual(actual.miniWidths, [34, 34]);
    assert.equal(actual.volumeCenter, 'dB');
    assert.equal(actual.volumeAccessibleValue, '-6.0 dB');
    assert.deepEqual(actual.colors, ['rgb(255, 255, 50)', 'rgb(145, 255, 40)']);
    assert.equal(actual.controlsFit, true);
    assert.equal(actual.groupStillSelected, 'true');
    assert.deepEqual(actual.velocityCounts, [8, 8]);
    assert.equal(actual.velocityBeforeCommit, undefined);
    assert.equal(actual.inspectorBeforeCommit, '79');
    assert.equal(actual.customVelocities.length, 32);
    assert.deepEqual(actual.customVelocities.slice(0, 3), [22, 80, 79]);
    assert.deepEqual(actual.retainedVelocities, actual.customVelocities);
    assert.equal(actual.randomBoundsHidden, true);
    assert.equal(actual.velocitiesEnabled, true);
    assert.deepEqual(actual.melodyInitialTargets, ['0, 2, 2, 4', '0, 2, 2, 4']);
    assert.deepEqual(actual.melody.chordNoteChoices, [0, 4, -2]);
    assert.deepEqual(actual.melody.melodyPatternOffsets, [1, -1, 2]);
    assert.equal(actual.melody.transpose, 7);
    assert.equal(actual.melodyHasSpeed, 2);
    assert.equal(actual.melodyHasAccent, 2);
  } finally {
    clearTimeout(timeout); child.kill(); server.close();
    // Remove only the fresh profile created by this test after Chromium exits.
    await new Promise(done => child.exitCode !== null ? done() : child.once('exit', done));
    fs.rmSync(profile, { recursive: true, force: true, maxRetries: 3, retryDelay: 100 });
  }
});
