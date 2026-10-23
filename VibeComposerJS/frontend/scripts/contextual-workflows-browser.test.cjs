const { findBrowser, connectToPage, createBrowserServer, listen, launchBrowser, closeBrowser, watchBrowser, requireProductionBuild } = require('./support/browser-harness.cjs');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const { DEFAULT_TRACK_POLICIES } = require('./music-loader.cjs').loadMusic('track-generation.ts');
const browser = findBrowser();

for (const width of [1440, 780]) test(`production contextual workflows with 30 tracks at ${width}px`, {
  skip: browser ? false : 'Chromium unavailable',
}, async () => {
  const root = requireProductionBuild();
  const index = fs.readFileSync(path.join(root, 'index.html'), 'utf8');
  const script = `<script>(async () => {
    const wait = async (read, label) => {
      for (let i = 0; i < 300; i++) { const value = read(); if (value) return value; await new Promise(r => setTimeout(r, 50)); }
      throw new Error('Timed out: ' + label);
    };
    const read = () => JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));
    const change = changeControl;
    const reset = async input => {
      await new Promise(done => requestAnimationFrame(() => requestAnimationFrame(done)));
      input.dispatchEvent(new MouseEvent('contextmenu', { bubbles: true, cancelable: true, button: 2 }));
    };
    try {
      await wait(() => innerWidth === ${width}, 'exact viewport');
      const seed = await wait(() => document.querySelector('[aria-label="Generation seed"]'), 'seed');
      // This workflow checks individual history entries, so opt out of compaction.
      (await wait(() => [...document.querySelectorAll('.project-tools button')].find(button => button.getAttribute('aria-label') === 'Settings'), 'settings button')).click();
      const settings = await wait(() => document.querySelector('vc-settings-dialog'), 'settings');
      const category = await wait(() => [...settings.querySelectorAll('.settings-sidebar button')].find(button => button.textContent.trim() === 'Project'), 'history category');
      await wait(() => settings.contains(document.activeElement), 'settings focus');
      const bounds = settings.getBoundingClientRect();
      if (bounds.left < 0 || bounds.right > innerWidth || bounds.top < 0 || bounds.bottom > innerHeight) throw new Error('settings outside viewport');
      [...settings.querySelectorAll('.settings-sidebar button')].find(button => button.textContent.trim() === 'Section Types').click();
      await wait(() => settings.querySelector('vc-section-type-settings'), 'section types');
      if (!settings.querySelector('vc-section-type-settings input[type="range"]')) throw new Error('section type editor missing');
      category.click();
      const compact = await wait(() => settings.querySelector('input[type="checkbox"]'), 'compact preference');
      if (compact.checked) compact.click();
      settings.querySelector('[aria-label="Close settings"]').click();
      await wait(() => !document.querySelector('vc-settings-dialog'), 'settings closed');
      if (!sessionStorage.getItem('contextual-fixture')) {
        await change(seed, '9007199254740993');
        const project = await wait(read, 'saved project');
        // Keep the authored fixture stable until this workflow explicitly enables each reroll.
        // Fresh projects now enable On Compose, which would otherwise replace the drum grids.
        project.trackGenerationPolicies = ${JSON.stringify(DEFAULT_TRACK_POLICIES)};
        for (const role of ['bass', 'chords', 'arpeggio', 'drums']) project.trackGenerationPolicies[role].onCompose = false;
        project.randomizeTransposeOnCompose = false;
        project.randomizeTempoOnCompose = false;
        for (const role of ['melody','bass','chords','arpeggio','drums']) {
          const base = project.tracks.find(track => track.role === role);
          for (let i = 2; i <= 6; i++) project.tracks.push({ ...base, id: 'track-' + role + '-' + i, name: base.name[0] + i,
            generatorSettings: { ...base.generatorSettings, ...(role === 'bass' && i === 2 ? { transpose: 12 } : {}) } });
          for (const section of project.arrangement) section.trackPresence[role] = project.tracks
            .filter(track => track.role === role).map((_, index) => section.trackPresence[role][index] ?? false);
        }
        project.arrangement[0].rolePartOverrides = { bass: { noteVariation: 35 } };
        project.arrangement[0].trackPartOverrides = { 'track-bass-2': { noteVariation: 45 } };
        sessionStorage.setItem('vibecomposer.project.v12', JSON.stringify(project));
        sessionStorage.setItem('contextual-fixture', 'ready'); location.reload(); return;
      }
      await wait(() => document.querySelectorAll('.track-line').length === 30, '30 track rows');
      // Persist the decoded fixture before comparing a policy-only action.
      await change(seed, '9007199254740995');
      await wait(() => read().seed === '9007199254740995', 'canonical project');
      const result = { width: innerWidth };
      result.createRemoved = !document.querySelector('.primary-harmony') && !document.querySelector('.track-browser [aria-label="Generation seed"]');
      result.seedInDock = !!seed.closest('.transport-dock');
      result.noKeySelector = !document.querySelector('vc-select[aria-label="Key"]');
      const transpose = document.querySelector('.transport-dock [aria-label="Transpose in semitones"]');
      await change(transpose, '7');
      await wait(() => read().transposeSemitones === 7 && document.querySelector('.transport-project span').title.includes('G MAJOR'), 'project key label');
      result.transposeKey = transpose.type === 'range' && !transpose.closest('vc-compact-knob').querySelector('.knob-caption'); await change(transpose, '0');
      if (innerWidth > 1050) {
        const dock = document.querySelector('.transport-dock').getBoundingClientRect();
        const score = document.querySelector('.score-panel').getBoundingClientRect();
        if (score.bottom > dock.top || dock.bottom > innerHeight) throw new Error('transport dock overlaps the score or extends outside the viewport');
      }
      const tempo = document.querySelector('.transport-dock [aria-label="Tempo in beats per minute"]');
      await change(tempo, '149'); await wait(() => read().tempoBpm === 149, 'tempo knob');
      result.tempoKnob = tempo.type === 'range'; await change(tempo, '128');
      await wait(() => read().tempoBpm === 128, 'tempo reset');
      const historyButton = [...document.querySelectorAll('.project-tools button')].find(button => button.getAttribute('aria-label') === 'History');
      historyButton.focus();
      historyButton.click();
      const history = await wait(() => document.querySelector('vc-history-dialog'), 'history popup');
      await wait(() => history.contains(document.activeElement), 'history keyboard focus');
      const entries = history.querySelectorAll('.history-entry');
      if (!history.querySelector('.history-entry.current').textContent.includes('149 → 128')) throw new Error('missing abbreviated history diff');
      entries[1].click();
      await wait(() => read().tempoBpm === 149 && history.querySelector('.future'), 'history undo jump');
      history.querySelector('.future').click();
      await wait(() => read().tempoBpm === 128, 'history redo jump');
      history.querySelector('[aria-label="Close history"]').click();
      await wait(() => !document.querySelector('vc-history-dialog') && document.activeElement === historyButton, 'history closes and restores focus');
      const mode = document.querySelector('.transport-dock [aria-label="Mode"]');
      const modeRow = mode.closest('.mode-control'), modeGroup = mode.closest('.mode-seed');
      const modeBounds = mode.getBoundingClientRect(), rowBounds = modeRow.getBoundingClientRect();
      const labelBounds = modeRow.querySelector('span').getBoundingClientRect(), groupBounds = modeGroup.getBoundingClientRect();
      const modeGap = parseFloat(getComputedStyle(modeRow).columnGap);
      // Mode flexes into the space left by its label inside the shared Mode/Seed column.
      result.modeSizing = { width: modeBounds.width, available: rowBounds.right - labelBounds.right - modeGap };
      result.modeFitsRow = getComputedStyle(mode).appearance === 'none' && modeBounds.width > 0
        && Math.abs(modeBounds.left - labelBounds.right - modeGap) <= 1
        && Math.abs(modeBounds.right - rowBounds.right) <= 1
        && modeBounds.left >= groupBounds.left - 1 && modeBounds.right <= groupBounds.right + 1;
      await change(mode, 'natural-minor'); await wait(() => read().scale === 'natural-minor', 'mode change');
      if((await selectOptions(mode)).length!==17) throw new Error('missing Java modes');
      await change(mode,'mixolydian'); await wait(()=>read().scale==='mixolydian'&&document.querySelector('.transport-project span').title.includes('MIXOLYDIAN'),'Mixolydian mode');
      await change(mode,'hungarian-minor'); await wait(()=>read().scale==='hungarian-minor','Hungarian minor mode');
      await change(mode, 'major'); await wait(() => read().scale === 'major' && read().transposeSemitones === 0, 'mode restore');
      const chordlet = () => document.querySelector('.chordlet-label');
      chordlet().dispatchEvent(new MouseEvent('contextmenu', { bubbles: true, cancelable: true, button: 2 }));
      const rootMenu = await wait(() => document.querySelector('[role="menu"][aria-label="Chord root"]'), 'root menu');
      result.rootMenuFits = rootMenu.getBoundingClientRect().left >= 0 && rootMenu.getBoundingClientRect().right <= innerWidth
        && rootMenu.getBoundingClientRect().top >= 0 && rootMenu.getBoundingClientRect().bottom <= innerHeight;
      const rootButtons=[...rootMenu.querySelectorAll('button')];
      result.rootColors=new Set(rootButtons.map(button=>getComputedStyle(button).backgroundColor)).size===12
        && rootButtons[0].style.getPropertyValue('--chord-hue')===chordlet().parentElement.style.getPropertyValue('--chord-hue');
      [...rootMenu.querySelectorAll('button')].find(button => button.textContent === 'F♯').click();
      await wait(() => read().progressionChords?.[0]?.root === 6, 'root selection');
      chordlet().dispatchEvent(new PointerEvent('pointerdown', { bubbles: true, cancelable: true, button: 1 }));
      const spiceMenu = await wait(() => document.querySelector('[role="menu"][aria-label="Chord spice"]'), 'spice menu');
      const spiceButton=name=>[...spiceMenu.querySelectorAll('button')].find(button=>button.textContent===name);
      result.spiceColors=Number(spiceButton('Major').dataset.keyOverlap)===0
        && Number(spiceButton('dim').dataset.keyOverlap)===2/3
        && getComputedStyle(spiceButton('Major')).backgroundColor!==getComputedStyle(spiceButton('dim')).backgroundColor;
      await wait(() => spiceMenu.contains(document.activeElement), 'spice menu keyboard focus');
      const beforeArrow = document.activeElement;
      beforeArrow.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true, cancelable: true, key: 'ArrowRight' }));
      result.menuKeyboard = spiceMenu.contains(document.activeElement) && document.activeElement !== beforeArrow;
      [...spiceMenu.querySelectorAll('button')].find(button => button.textContent === 'sus4').click();
      await wait(() => read().progressionChords[0].spice === 'sus4', 'spice selection');
      chordlet().dispatchEvent(new WheelEvent('wheel', { bubbles: true, cancelable: true, deltaY: -100 }));
      await wait(() => read().progressionChords[0].inversion === 1, 'inversion wheel');
      chordlet().dispatchEvent(new WheelEvent('wheel', { bubbles: true, cancelable: true, deltaY: -100, shiftKey: true }));
      await wait(() => read().progressionChords[0].root === 7, 'root wheel');
      chordlet().dispatchEvent(new WheelEvent('wheel', { bubbles: true, cancelable: true, deltaY: -100, ctrlKey: true }));
      await wait(() => read().progressionChords[0].spice === 'sus7', 'spice wheel');
      const degree = read().progression[0];
      chordlet().dispatchEvent(new KeyboardEvent('keydown', { bubbles: true, cancelable: true, key: 'ArrowRight', altKey: true }));
      await wait(() => read().progressionChords[1]?.spice === 'sus7' && read().progression[1] === degree, 'keyboard move');
      document.querySelector('[aria-label="Undo"]').click();
      await wait(() => read().progressionChords[0]?.spice === 'sus7', 'move undo');
      document.querySelector('[aria-label="Add chord"]').click();
      await wait(() => read().progression.length === 5, 'chord add');
      (await wait(() => document.querySelector('[aria-label="Remove chord 5"]'), 'fifth chord rendered')).click();
      await wait(() => read().progression.length === 4, 'chord remove');
      result.chordletGestures = true;
      const beforeRules = JSON.stringify(read().tracks);
      const melody = document.querySelector('.track-group.melody');
      melody.querySelector('.group-select').click();
      (await wait(() => melody.querySelector('[aria-label="On Regenerate"]'), 'melody policies')).click();
      await wait(() => read().trackGenerationPolicies?.melody?.onRegenerate, 'regenerate rule');
      result.rulesPreserveTracks = beforeRules === JSON.stringify(read().tracks);
      document.querySelector('[aria-label="Regenerate"]').click();
      await wait(() => read().tracks[0].generatorSettings.patternSeed, 'regenerate seed');
      result.regenerated = read().tracks.filter(track => track.role === 'melody').every(track => Number.isInteger(track.generatorSettings.patternSeed));
      document.querySelector('.track-group.arpeggio .group-select').click();
      (await wait(() => document.querySelector('.track-group.arpeggio [aria-label="On Compose"]'), 'arp policies')).click();
      const beforeCompose = JSON.stringify(read());
      document.querySelector('[aria-label="Compose"]').click();
      await wait(() => JSON.stringify(read()) !== beforeCompose, 'compose');
      result.composed = read().trackRerollCounts.arpeggio === 1;
      // Traverse the canvas's real keyboard targets, then activate the first Bass cell.
      const canvas = document.querySelector('.arrangement-panel canvas[tabindex]');
      canvas.focus();
      for (let i = 0; i < 100; i++) {
        canvas.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
        await new Promise(r => requestAnimationFrame(r));
        if (canvas.getAttribute('aria-label').startsWith('Select Bass settings in INTRO')) break;
      }
      canvas.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
      const copy = await wait(() => [...document.querySelectorAll('vc-part-scope-actions button')].find(button => button.textContent.trim() === 'Copy to Globals'), 'local cell actions');
      result.keyboardCell = document.querySelector('.part-context-label').textContent.includes('Bass');
      result.mixed = [...document.querySelectorAll('.part-settings-panel [aria-label]')].some(element => element.getAttribute('aria-valuetext')?.includes('Mixed') || element.placeholder === 'Mixed');
      const resetButton = document.querySelector('vc-part-scope-actions [aria-label="Reset to inherited"]');
      if (resetButton.disabled || resetButton.classList.contains('values-match')) throw new Error('differing cell reset should be emphasized');
      const resetBackground = getComputedStyle(resetButton).backgroundColor;
      // Quick inheritance interrupts the bottom border and leaves titles their full width.
      await new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)));
      const quickMarkers = [...document.querySelectorAll('.part-settings-content vc-part-settings-editor .inherit-snowflake')];
      if (!quickMarkers.length) throw new Error('local controls need inheritance markers');
      for (const marker of quickMarkers) {
        const control = marker.closest('.part-control');
        const box = marker.getBoundingClientRect(), controlBox = control.getBoundingClientRect();
        if (Math.abs((box.top + box.bottom) / 2 - controlBox.bottom) > 2) throw new Error('inheritance marker must sit on the bottom border: ' + control.dataset.controlKey);
        if (Math.abs((box.left + box.right - controlBox.left - controlBox.right) / 2) > 1) throw new Error('quick inheritance marker must be centered: ' + control.dataset.controlKey);
        const border = control.querySelector('.quick-inheritance-border');
        if (!border || getComputedStyle(border).maskImage === 'none') throw new Error('quick inheritance needs a bottom border gap');
        const label = control.querySelector('.control-name');
        if (label && label.scrollWidth > label.clientWidth) throw new Error('quick title must fit in full: ' + control.dataset.controlKey);
        if (control.querySelectorAll('.inherit-snowflake').length !== 1) throw new Error('joined controls need one shared inheritance marker');
      }
      const previous = read(); copy.click();
      await wait(() => read().tracks.find(track => track.id === 'track-bass-1').generatorSettings.noteVariation === 35, 'global copy');
      const after = read();
      result.copied = after.tracks.filter(track => track.role === 'bass').map(track => track.generatorSettings.noteVariation);
      result.localRetained = JSON.stringify(after.arrangement) === JSON.stringify(previous.arrangement);
      result.noopDisabled = await wait(() => copy.disabled, 'copy no-op disabled');
      await wait(() => resetButton.classList.contains('values-match'), 'matching reset is neutral');
      if (resetButton.disabled || getComputedStyle(resetButton).backgroundColor === resetBackground) throw new Error('matching reset should remain available with neutral styling');
      document.querySelector('[aria-label="Undo"]').click();
      await wait(() => read().tracks.find(track => track.id === 'track-bass-1').generatorSettings.noteVariation === 20, 'copy undo');
      await wait(() => !resetButton.classList.contains('values-match'), 'undo restores reset emphasis');
      const redo = await wait(() => { const button = document.querySelector('[aria-label="Redo"]'); return button && !button.disabled ? button : null; }, 'redo enabled');
      redo.click();
      await wait(() => read().tracks.find(track => track.id === 'track-bass-1').generatorSettings.noteVariation === 35, 'copy redo');
      await wait(() => resetButton.classList.contains('values-match'), 'redo restores neutral reset');
      document.querySelector('.quick-settings-actions .part-inspector-link').click();
      await wait(() => document.querySelector('.placement-target'), 'quick customization');
      result.quickCustomization = document.querySelector('.workspace-canvas').classList.contains('quick-layout-editing');
      [...document.querySelectorAll('.quick-settings-actions button')].find(button => button.textContent.trim() === 'Save quick settings').click();
      await wait(() => !document.querySelector('.placement-target'), 'quick customization saved');
      document.querySelector('.track-group.chords .group-select').click();
      await wait(() => document.querySelector('#part-inspector [data-control-role="chords"]'), 'chord inspector');
      const join = await wait(() => document.querySelector('#part-inspector [aria-label="JOIN"]'), 'chord join');
      await change(join, 'EXPAND');
      await wait(() => read().tracks.find(track => track.role === 'chords').generatorSettings.patternJoinMode === 'EXPAND', 'chord join commit');
      const strum = await wait(() => document.querySelector('.part-settings-panel [aria-label="Strum"]'), 'chord strum');
      await change(strum, '333');
      await change(document.querySelector('.part-settings-panel [aria-label="S.ARC"]'), 'ARP_D');
      await change(document.querySelector('#part-inspector [aria-label="Transition chance"]'), '100');
      await change(document.querySelector('#part-inspector [aria-label="Transition split"]'), '750');
      await change(document.querySelector('.part-settings-panel [aria-label="Strum pause"]'), '20');
      await wait(() => read().tracks.find(track => track.role === 'chords').generatorSettings.strum === 333 && read().tracks.find(track => track.role === 'chords').generatorSettings.strumPauseChance === 20, 'chord articulation commit');
      await wait(() => document.querySelector('.part-settings-panel [aria-label="Strum"]').value === '333'
        && document.querySelector('.part-settings-panel [aria-label="S.ARC"]').getAttribute("value") === 'ARP_D', 'chord editors synchronized');
      result.chordEditors = document.querySelector('.part-settings-panel [aria-label="Strum"]').value === '333'
        && document.querySelector('.part-settings-panel [aria-label="S.ARC"]').getAttribute("value") === 'ARP_D'
        && read().tracks.find(track => track.role === 'chords').generatorSettings.patternJoinMode === 'EXPAND' && read().tracks.find(track => track.role === 'chords').generatorSettings.transitionChance === 100 && read().tracks.find(track => track.role === 'chords').generatorSettings.transitionSplit === 750;
      const chords = document.querySelector('.track-group.chords');
      const tracksBeforePolicy = JSON.stringify(read().tracks);
      await change(chords.querySelector('[aria-label="Reroll Struminess %"]'), '100');
      await change(chords.querySelector('[aria-label="Reroll Max transition %"]'), '0');
      await change(chords.querySelector('[aria-label="Reroll Max strum pause %"]'), '0');
      await change(chords.querySelector('[aria-label="Reroll Expand chance %"]'), '100');
      result.chordPolicyOnly = JSON.stringify(read().tracks) === tracksBeforePolicy;
      chords.querySelector('[aria-label="On Compose"]').click();
      document.querySelector('[aria-label="Compose"]').click();
      await wait(() => read().trackRerollCounts?.chords === 1, 'chord automatic reroll');
      result.chordPolicies = read().tracks.filter(track => track.role === 'chords').every(track =>
        track.generatorSettings.strumType.startsWith('ARP') && track.generatorSettings.transitionChance === 0
        && track.generatorSettings.strumPauseChance === 0 && track.generatorSettings.patternJoinMode === 'EXPAND');
      document.querySelector('.track-group.arpeggio .group-select').click();
      const direction = await wait(() => document.querySelector('.part-settings-panel [aria-label="DIRECTION"]'), 'arp direction');
      await change(direction, 'custom');
      const customDirection = await wait(() => document.querySelector('#part-inspector [aria-label="Custom direction"]'), 'custom direction editor');
      await change(customDirection, '-1, 0, 3, 1');
      await change(document.querySelector('.part-settings-panel [aria-label="Rotate"]'), '3');
      await change(document.querySelector('#part-inspector [aria-label="Contour"]'), '0, 2, -1');
      document.querySelector('#part-inspector [aria-label="Chord-relative contour"]').click();
      document.querySelector('#part-inspector [aria-label="Octave adjustments"]').click();
      if (document.querySelector('#part-inspector [aria-label="Correct by melody"]')) throw new Error('Removed arpeggio correction control is visible');
      await wait(() => read().tracks.find(track => track.role === 'arpeggio').generatorSettings.arpPatternRotate === 3, 'arp musical settings');
      await wait(() => document.querySelector('.part-settings-panel [aria-label="Rotate"]').value === '3', 'arp editors synchronized');
      result.arpMusical = read().tracks.filter(track => track.role === 'arpeggio').every(track =>
        track.generatorSettings.pattern === 'custom' && track.generatorSettings.arpPatternCustom.join(',') === '-1,0,3,1'
        && track.generatorSettings.arpContour.join(',') === '0,2,-1' && track.generatorSettings.useOctaveAdjustments)
        && document.querySelector('.part-settings-panel [aria-label="Rotate"]').value === '3';
      await change(document.querySelector('#part-inspector [aria-label="Contour"]'), '');
      await wait(() => read().tracks.find(track => track.role === 'arpeggio').generatorSettings.arpContour.length === 0, 'clear contour');
      await change(document.querySelector('.part-settings-panel [aria-label="PATTERN"]'), 'melody');
      await wait(() => read().tracks.find(track => track.role === 'arpeggio').generatorSettings.rhythm === 'melody', 'arp melody rhythm');
      await wait(() => !document.querySelector('.part-settings-panel [aria-label="Hits"]')
        && document.querySelector('.part-settings-panel [aria-label="PATTERN"]').getAttribute("value") === 'melody', 'arp melody editors synchronized');
      result.arpMelody = !document.querySelector('.part-settings-panel [aria-label="Hits"]')
        && document.querySelector('.part-settings-panel [aria-label="PATTERN"]').getAttribute("value") === 'melody';
      document.querySelector('.track-group.drums .group-select').click();
      const ghosts = await wait(() => document.querySelector('.part-settings-panel [aria-label="Ghosts"]'), 'drum ghosts');
      ghosts.click();
      await change(document.querySelector('#part-inspector [aria-label="Humanize"]'), '100');
      await wait(() => read().tracks.find(track => track.role === 'drums').generatorSettings.ghosts && read().tracks.find(track => track.role === 'drums').generatorSettings.humanize === 100, 'drum musical settings');
      document.querySelector('.part-settings-panel [aria-label="Edit rhythm pattern"]').click();
      const multiply = await wait(() => {
        const select = document.querySelector('vc-pattern-sequencer [aria-label="Multiply Hits"]');
        return select && select.getAttribute("aria-disabled") !== "true" ? select : null;
      }, 'enabled popup hit multiplier');
      await change(multiply, '2');
      await wait(() => read().tracks.find(track => track.role === 'drums').generatorSettings.hitsPerPattern === 8, 'multiply hits');
      await wait(() => document.querySelector('.part-settings-panel [aria-label="Hits"]').value === '8'
        && document.querySelector('.part-settings-panel [aria-label="Ghosts"]').getAttribute('aria-pressed') === 'true', 'drum editors synchronized');
      result.drumMusical = read().tracks.filter(track => track.role === 'drums').every(track =>
        track.generatorSettings.hitsPerPattern === 8 && track.generatorSettings.ghosts && track.generatorSettings.humanize === 100)
        && document.querySelector('.part-settings-panel [aria-label="Ghosts"]').getAttribute('aria-pressed') === 'true'
        && document.querySelector('.part-settings-panel [aria-label="Hits"]').value === '8' && multiply.getAttribute("value") === '';
      document.querySelector('[aria-label="Close pattern sequencer"]').click();
      document.querySelector('[aria-label="Undo"]').click();
      await wait(() => read().tracks.find(track => track.role === 'drums').generatorSettings.hitsPerPattern === 4, 'multiply undo');
      (await wait(() => { const button = document.querySelector('[aria-label="Redo"]'); return button && !button.disabled ? button : null; }, 'multiply redo enabled')).click();
      await wait(() => read().tracks.find(track => track.role === 'drums').generatorSettings.hitsPerPattern === 8, 'multiply redo');
      await change(document.querySelector('.part-settings-panel [aria-label="PATTERN"]'), 'melody');
      document.querySelector('.part-settings-panel [aria-label="Edit rhythm pattern"]').click();
      await wait(() => document.querySelector('vc-pattern-sequencer [aria-label="Multiply Hits"]')?.getAttribute('aria-disabled') === 'true', 'melody hit action disabled');
      result.drumMelody = document.querySelector('vc-pattern-sequencer [aria-label="Multiply Hits"]').getAttribute('aria-disabled') === 'true'
        && !document.querySelector('.part-settings-panel [aria-label="Hits"]');
      document.querySelector('[aria-label="Close pattern sequencer"]').click();
      document.querySelector('.track-group.melody .group-select').click();
      await wait(() => document.querySelector('#part-inspector [data-control-role="melody"]'), 'melody inspector');
      for (const key of ['doubledRhythmChance', 'leadChordsChance', 'maxNoteExceptions', 'noteExceptionChance']) {
        if (document.querySelector('[data-control-key="' + key + '"]')) throw new Error('Removed melody control remains: ' + key);
      }
      const extended = [ ['Split long', 'splitChance', 60],
        ['Start note', 'startNoteChance', 0] ];
      for (const [label, key, value] of extended) {
        await change(await wait(() => document.querySelector('#part-inspector [aria-label="' + label + '"]'), label), String(value));
        await wait(() => read().tracks.find(track => track.role === 'melody').generatorSettings[key] === value, label + ' commit');
      }
      result.melodyExtended = read().tracks.filter(track => track.role === 'melody').every(track =>
        extended.every(([, key, value]) => track.generatorSettings[key] === value));
      document.querySelector('[aria-label="Undo"]').click();
      await wait(() => read().tracks.find(track => track.role === 'melody').generatorSettings.startNoteChance === undefined, 'Start note undo');
      (await wait(() => { const button = document.querySelector('[aria-label="Redo"]'); return button && !button.disabled ? button : null; }, 'Start note redo enabled')).click();
      await wait(() => read().tracks.find(track => track.role === 'melody').generatorSettings.startNoteChance === 0, 'Start note redo');
      if (document.querySelector('#part-inspector vc-melody-global-settings')) throw new Error('Global melody controls remain in inspector');
      document.querySelector('.track-group.melody .track-select').click();
      await new Promise(done => requestAnimationFrame(() => requestAnimationFrame(done)));
      const selectedMelody = () => read().tracks.find(track => track.role === 'melody');
      const otherTracks = () => JSON.stringify(read().tracks.filter(track => track.id !== selectedMelody().id));
      const untouched = otherTracks();
      const trackControl = label => document.querySelector('#part-inspector [aria-label="' + label + '"]');
      await wait(() => trackControl('Target direction'), 'track controls');
      trackControl('Fill pauses per chord').click();
      await wait(() => selectedMelody().generatorSettings.fillPausesPerChord === true, 'track pause filling');
      await change(trackControl('Max direction changes'), '6');
      await wait(() => selectedMelody().generatorSettings.maxDirectionChanges === 6, 'track direction budget');
      document.querySelector('[aria-label="Undo"]').click();
      await wait(() => selectedMelody().generatorSettings.maxDirectionChanges === undefined, 'track budget undo');
      (await wait(() => { const button = document.querySelector('[aria-label="Redo"]'); return button && !button.disabled ? button : null; }, 'redo enabled')).click();
      await wait(() => selectedMelody().generatorSettings.maxDirectionChanges === 6, 'track budget redo');
      await change(trackControl('New blocks'), '0');
      await wait(() => selectedMelody().generatorSettings.newBlocksChance === 0, 'track catalogue chance');
      await reset(trackControl('New blocks'));
      await wait(() => selectedMelody().generatorSettings.newBlocksChance === 25, 'track catalogue default');
      trackControl('Emphasize key').click();
      await wait(() => selectedMelody().generatorSettings.emphasizeKey === true, 'track emphasis');
      result.flipEmphasis = otherTracks() === untouched;
      await change(trackControl('CHRD%'), '70');
      await wait(() => selectedMelody().generatorSettings.chordNoteTarget === 70, 'track chord target');
      await reset(trackControl('CHRD%'));
      await wait(() => selectedMelody().generatorSettings.chordNoteTarget === 0, 'track chord target reset');
      result.chordRepairs = otherTracks() === untouched;
      await change(trackControl('TON%'), '70'); await change(trackControl('MODE%'), '60');
      await wait(() => selectedMelody().generatorSettings.tonicNoteTarget === 70 && selectedMelody().generatorSettings.modeNoteTarget === 60, 'track tonal targets');
      await reset(trackControl('TON%')); await reset(trackControl('MODE%'));
      await wait(() => selectedMelody().generatorSettings.tonicNoteTarget === 0 && selectedMelody().generatorSettings.modeNoteTarget === 0, 'track tonal resets');
      result.tonalTargets = otherTracks() === untouched;
      for (const direction of ['ANY','DESC','ASC']) {
        await change(trackControl('Target direction'), direction);
        await wait(() => selectedMelody().generatorSettings.targetDirection === direction, 'track target direction');
      }
      const projectTracks = JSON.stringify(read().tracks);
      [...document.querySelectorAll('button')].find(button => button.getAttribute('aria-label') === 'Settings').click();
      const dialog = await wait(() => document.querySelector('vc-settings-dialog'), 'Melody settings dialog');
      (await wait(() => [...dialog.querySelectorAll('.settings-sidebar button')].find(button => button.textContent.trim() === 'Melody'), 'Melody settings category')).click();
      const globals = await wait(() => dialog.querySelector('vc-melody-global-settings'), 'project melody settings');
      for (const label of ['Target direction','TON%','MODE%','CHRD%','Emphasize key','Max direction changes','New blocks','Fill pauses per chord']) {
        if (globals.querySelector('[aria-label="' + label + '"]')) throw new Error('Track control in project settings: ' + label);
      }
      const effect = globals.querySelector('[aria-label="Pattern effect"]');
      for (const value of [0,1,2]) {
        await change(effect, String(value)); await wait(() => read().melodyGlobal?.patternEffect === value, 'project pattern effect');
      }
      const families = ['Scaley','Neighbory','Arpy','Chordy','Wavy','Interval','Note'];
      for (const [index,family] of families.entries()) {
        await change(globals.querySelector('[aria-label="' + family + ' weight"]'), String(20 + index * 10));
        await wait(() => read().melodyGlobal?.blockTypeWeights?.[index] === 20 + index * 10, 'project catalogue weights');
        await new Promise(done => requestAnimationFrame(() => requestAnimationFrame(done)));
      }
      await wait(() => JSON.stringify(read().melodyGlobal?.blockTypeWeights) === '[20,30,40,50,60,70,80]', 'independent catalogue weights');
      const preference = globals.querySelector('[aria-label="Block change preference"]');
      globals.querySelector('[aria-label="Default block change order"]').click();
      await wait(() => read().melodyGlobal?.blockChangePreference?.length === 8, 'ranked block changes');
      const validPreference = JSON.stringify(read().melodyGlobal);
      await change(preference, '0,1,2,3,4,5,6,6');
      await wait(() => globals.querySelector('[role="alert"]'), 'invalid preference alert');
      result.catalogue = JSON.stringify(read().melodyGlobal) === validPreference;
      globals.querySelector('[aria-label="Uniform block changes"]').click();
      await wait(() => read().melodyGlobal?.blockChangePreference?.length === 0, 'uniform block changes');
      await reset(globals.querySelector('[aria-label="Arpy weight"]'));
      await wait(() => read().melodyGlobal?.blockTypeWeights?.[2] === 100, 'family default');
      const avoid = globals.querySelector('[aria-label="AVOID"]');
      await change(avoid, '2'); await wait(() => read().melodyGlobal?.replaceAvoidNotes === 2, 'project Avoid');
      await reset(avoid); await wait(() => read().melodyGlobal?.replaceAvoidNotes === 0, 'project Avoid reset');
      const blockMode = globals.querySelector('[aria-label="Block target mode"]');
      await change(blockMode,'1'); await wait(() => read().melodyGlobal?.blockTargetMode === 1, 'project target mode');
      await reset(blockMode); await wait(() => read().melodyGlobal?.blockTargetMode === 2, 'project target mode reset');
      result.targetModes = JSON.stringify(read().tracks) === projectTracks;
      globals.querySelector('[aria-label="Melody pattern flip"]').click();
      await wait(() => read().melodyGlobal?.patternFlip === true, 'project melody grid flip');
      await change(globals.querySelector('[aria-label="Target variation"]'), '6');
      await wait(() => read().melodyGlobal?.targetNoteVariation === 6, 'project variation');
      globals.querySelector('[aria-label="Directions from progression"]').click();
      await wait(() => read().melodyGlobal?.useDirectionsFromProgression === true, 'project progression directions');
      globals.querySelector('[aria-label="Force first outline"]').click();
      await wait(() => read().melodyGlobal?.forceFirstOutline === true, 'project force outline');
      result.targetPreparation = JSON.stringify(read().tracks) === projectTracks;
      result.catalogue &&= JSON.stringify(read().tracks) === projectTracks;
      result.defaultResets = selectedMelody().generatorSettings.newBlocksChance === 25 && read().melodyGlobal.patternEffect === 2;
      dialog.querySelector('[aria-label="Close settings"]').click();
      await wait(() => !document.querySelector('vc-settings-dialog'), 'close melody settings');
      const beforePreparation = JSON.stringify(read().tracks);
      document.querySelector('[aria-label="Compose"]').click();
      await wait(() => JSON.stringify(read().tracks) !== beforePreparation, 'prepared outline Compose');
      const members = read().tracks.filter(track => track.role === 'melody');
      result.targetPreparation &&= members.every(track => track.rerollLocked || JSON.stringify(track.generatorSettings.chordNoteChoices) === JSON.stringify(members[0].generatorSettings.chordNoteChoices));
      document.querySelector('[aria-label="Undo"]').click();
      await wait(() => JSON.stringify(read().tracks) === beforePreparation, 'preparation undo');
      result.melodyGlobal = !Object.hasOwn(read().melodyGlobal, 'targetDirection') && !Object.hasOwn(read().melodyGlobal, 'newBlocksChance')
        && members.every(track => !Object.hasOwn(track.generatorSettings, 'blockTargetMode') && !Object.hasOwn(track.generatorSettings, 'forceFirstOutline'));
      await new Promise(done => requestAnimationFrame(() => requestAnimationFrame(done)));
      const fits = element => { const box = element.getBoundingClientRect(); return box.left >= -1 && box.right <= innerWidth + 1; };
      result.actionsFit = [...document.querySelectorAll('.scope-actions button, .transport-actions button, .track-inline-select')].every(fits);
      result.trackRowsFit = [...document.querySelectorAll('.track-line')].every(row => {
        const bounds = row.getBoundingClientRect();
        return [...row.children].every(child => { const box = child.getBoundingClientRect(); return box.left >= bounds.left - 1 && box.right <= bounds.right + 1 && box.width >= 8; });
      });
      result.groupNamesVisible = [...document.querySelectorAll('.group-select strong')].every(label => label.clientWidth > 0 && label.scrollWidth <= label.clientWidth + 1);
      result.pageFits = document.documentElement.scrollWidth <= innerWidth + 1;
      if (!result.pageFits) result.overflow = { width: document.documentElement.scrollWidth,
        elements: [...document.querySelectorAll('body *')].map(element => ({ tag: element.tagName, class: element.className,
          right: element.getBoundingClientRect().right, width: element.getBoundingClientRect().width }))
          .filter(element => element.right > innerWidth + 1).slice(-12) };
      result.trackScroll = document.querySelector('.track-groups').scrollHeight > document.querySelector('.track-groups').clientHeight || document.documentElement.scrollHeight > innerHeight;
      await fetch('/result', { method: 'POST', body: JSON.stringify(result) });
    } catch(error) { await fetch('/result', { method: 'POST', body: JSON.stringify({ error: error.message, context: document.querySelector('.part-context-label')?.textContent,
      stack: error.stack, canvas: document.querySelector('.arrangement-panel canvas[tabindex]')?.getAttribute('aria-label') }) }); }
  })();</script>`;
  new (require('node:vm').Script)(script.slice('<script>'.length, -'</script>'.length));
  let resolve, reject;
  const result = new Promise((done, fail) => { resolve = done; reject = fail; });
  result.catch(() => {}); // CDP setup can fail before the result is awaited.
  result.catch(() => {}); // Startup failure can precede awaiting the browser result.
  const server = createBrowserServer({ root, html: index.replace('</body>', script + '</body>'), resolve, reject });
  await listen(server);
  const profile = fs.mkdtempSync(path.resolve(__dirname, '../.angular/contextual-browser-'));
  const child = launchBrowser(browser, ['--headless', '--disable-gpu', '--no-first-run', '--disable-background-networking',
    '--disable-background-timer-throttling', '--disable-renderer-backgrounding', '--no-default-browser-check',
    '--remote-debugging-port=0', '--window-size=' + width + ',1100', '--user-data-dir=' + profile, 'http://127.0.0.1:' + server.address().port + '/'], { stdio: ['ignore', 'ignore', 'pipe'] });
  const watchdog = watchBrowser(child, reject, 45000);
  let socket;
  try {
    const connection = await connectToPage(child);
    socket = connection.socket;
    const command = connection.command;
    await command('Emulation.setDeviceMetricsOverride', { width, height: 1100, deviceScaleFactor: 1, mobile: false });
    const actual = await result;
    assert.equal(actual.error, undefined, JSON.stringify(actual));
    // Exercise an actual right-button contextmenu, not just the synthetic workflow events.
    await command('Runtime.evaluate', { expression: `(() => {
      const control = document.querySelector('[aria-label="New blocks"]');
      control.scrollIntoView({ block: 'center' }); await changeControl(control, '90');
    })()` });
    await new Promise(done => setTimeout(done, 100));
    const resetPoint = await command('Runtime.evaluate', { expression: `(() => {
      const box = document.querySelector('[aria-label="New blocks"]').getBoundingClientRect();
      return { x: box.left + box.width / 2, y: box.top + box.height / 2 };
    })()`, returnByValue: true });
    await command('Input.dispatchMouseEvent', { type: 'mousePressed', ...resetPoint.result.value, button: 'right', clickCount: 1 });
    await command('Input.dispatchMouseEvent', { type: 'mouseReleased', ...resetPoint.result.value, button: 'right', clickCount: 1 });
    const resetValue = await command('Runtime.evaluate', { expression: `JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')).tracks.find(track => track.role === 'melody').generatorSettings.newBlocksChance`, returnByValue: true });
    assert.equal(resetValue.result.value, 25, 'real right-click resets New blocks');
    // Real left-button pointer capture/reorder through CDP, committing exactly once.
    const snapshot = await command('Runtime.evaluate', { awaitPromise: true, expression: `(async () => {
      const buttons = [...document.querySelectorAll('.chordlet-label')];
      buttons[0].scrollIntoView({ block: 'center', inline: 'start' });
      await new Promise(done => requestAnimationFrame(() => requestAnimationFrame(done)));
      const box = buttons[0].getBoundingClientRect(), clip = buttons[0].closest('.chordlet-list').getBoundingClientRect();
      const point = { x: (Math.max(box.left, clip.left) + Math.min(box.right, clip.right)) / 2, y: box.top + box.height / 2 };
      return { point, project: JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')) };
    })()`, returnByValue: true });
    const beforeDrag = snapshot.result.value;
    await command('Input.dispatchMouseEvent', { type: 'mousePressed', ...beforeDrag.point, button: 'left', clickCount: 1 });
    // Cross the drag threshold before scrolling: revealing the next chord can put
    // its center at the same screen coordinates as the source in a narrow strip.
    await command('Input.dispatchMouseEvent', { type: 'mouseMoved', x: beforeDrag.point.x + 6, y: beforeDrag.point.y, button: 'left', buttons: 1 });
    const dragging = await command('Runtime.evaluate', { expression: `document.querySelector('.chordlet.dragging')?.dataset.chordIndex`, returnByValue: true });
    assert.equal(dragging.result.value, '0', 'real pointer movement starts the chord drag');
    // The desktop chord strip can show only one chord at a time. Reveal the destination
    // while the source retains pointer capture, and aim inside the strip's clipped bounds.
    const destination = await command('Runtime.evaluate', { awaitPromise: true, returnByValue: true, expression: `(async () => {
      const button = document.querySelectorAll('.chordlet-label')[1];
      button.scrollIntoView({ block: 'nearest', inline: 'center' });
      await new Promise(done => requestAnimationFrame(() => requestAnimationFrame(done)));
      const box = button.getBoundingClientRect(), clip = button.closest('.chordlet-list').getBoundingClientRect();
      const point = { x: (Math.max(box.left, clip.left) + Math.min(box.right, clip.right)) / 2, y: box.top + box.height / 2 };
      return { point, target: document.elementFromPoint(point.x, point.y)?.closest('[data-chord-index]')?.dataset.chordIndex };
    })()` });
    assert.equal(destination.result.value.target, '1', 'drag destination is the second chord');
    await command('Input.dispatchMouseEvent', { type: 'mouseMoved', ...destination.result.value.point, button: 'left', buttons: 1 });
    await command('Input.dispatchMouseEvent', { type: 'mouseReleased', ...destination.result.value.point, button: 'left', clickCount: 1 });
    await new Promise(done => setTimeout(done, 100));
    const moved = await command('Runtime.evaluate', { expression: `JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'))`, returnByValue: true });
    assert.equal(moved.result.value.progression[1], beforeDrag.project.progression[0]);
    assert.deepEqual(moved.result.value.progressionChords[1], beforeDrag.project.progressionChords[0]);
    const screenshot = await command('Page.captureScreenshot', { format: 'png' });
    fs.writeFileSync(path.resolve(__dirname, '../.angular/contextual-' + width + '.png'), Buffer.from(screenshot.data, 'base64'));
    const settingsView = await command('Runtime.evaluate', { awaitPromise: true, returnByValue: true, expression: `(async () => {
      const wait = async read => { for (let i=0;i<100;i++) { const value=read(); if(value) return value; await new Promise(r=>setTimeout(r,20)); } throw new Error('Melody settings unavailable'); };
      document.querySelector('[aria-label="Settings"]').click();
      const dialog = await wait(() => document.querySelector('vc-settings-dialog'));
      (await wait(() => [...dialog.querySelectorAll('.settings-sidebar button')].find(button => button.textContent.trim() === 'Melody'))).click();
      const controls = await wait(() => dialog.querySelector('vc-melody-global-settings'));
      await new Promise(done => requestAnimationFrame(() => requestAnimationFrame(done)));
      controls.scrollIntoView({ block: 'start' });
      return { inSettings: true, inspectorGlobals: !!document.querySelector('#part-inspector vc-melody-global-settings'),
        fits: controls.scrollWidth <= controls.clientWidth + 1 };
    })()` });
    assert.equal(settingsView.exceptionDetails, undefined, JSON.stringify(settingsView));
    assert.deepEqual(settingsView.result.value, { inSettings: true, inspectorGlobals: false, fits: true });
    const globalScreenshot = await command('Page.captureScreenshot', { format: 'png' });
    fs.writeFileSync(path.resolve(__dirname, '../.angular/contextual-melody-global-' + width + '.png'), Buffer.from(globalScreenshot.data, 'base64'));
    await command('Runtime.evaluate', { expression: `document.querySelector('[aria-label="Block target mode"]').closest('fieldset').scrollIntoView({ block: 'center' })` });
    const targetModeScreenshot = await command('Page.captureScreenshot', { format: 'png' });
    fs.writeFileSync(path.resolve(__dirname, '../.angular/contextual-melody-target-modes-' + width + '.png'), Buffer.from(targetModeScreenshot.data, 'base64'));
    await command('Runtime.evaluate', { expression: `document.querySelector('.catalogue-area').scrollIntoView({ block: 'center' })` });
    const catalogueScreenshot = await command('Page.captureScreenshot', { format: 'png' });
    fs.writeFileSync(path.resolve(__dirname, '../.angular/contextual-melody-catalogue-' + width + '.png'), Buffer.from(catalogueScreenshot.data, 'base64'));
    await command('Runtime.evaluate', { expression: `document.querySelector('vc-settings-dialog [aria-label="Close settings"]').click()` });
    await new Promise(done => setTimeout(done, 100));
    await command('Runtime.evaluate', { expression: `document.querySelector('.track-group.arpeggio .group-select').click()` });
    await new Promise(done => setTimeout(done, 100));
    await command('Runtime.evaluate', { expression: `document.querySelector('#part-inspector').scrollIntoView({ block: 'start' })` });
    const arpScreenshot = await command('Page.captureScreenshot', { format: 'png' });
    fs.writeFileSync(path.resolve(__dirname, '../.angular/contextual-arp-' + width + '.png'), Buffer.from(arpScreenshot.data, 'base64'));
    const harmonyResult = await command('Runtime.evaluate', { awaitPromise: true, returnByValue: true, expression: `(async () => {
      const wait = async (read, label) => { for (let i = 0; i < 200; i++) { const value = read(); if (value) return value; await new Promise(r => setTimeout(r,20)); } throw new Error(label); };
      const read = () => JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));
      const change = changeControl;
      await wait(()=>document.querySelector('.part-panel-toggle').getAttribute('aria-label').includes('Global Arps settings'),'global role panel title');
      if (document.querySelector('#part-inspector [aria-label="Flatten voicing"]') || document.querySelector('#part-inspector details summary')?.textContent.includes('Global harmony')) throw new Error('global harmony still in inspector');
      document.querySelector('.project-tools [aria-label="Settings"]').click();
      const harmony = await wait(() => document.querySelector('vc-settings-dialog'),'harmony settings dialog');
      (await wait(() => harmony.querySelector('[aria-label="Behavior General"]'),'settings categories rendered')).click();
      await new Promise(r => requestAnimationFrame(r));
      if (harmony.querySelector('[aria-label="Harmony generation"]')) throw new Error('harmony generation still in General');
      harmony.querySelector('[aria-label="Behavior Harmony"]').click();
      await wait(() => harmony.querySelector('[aria-label="Flatten voicing"]'),'harmony settings page');
      if (harmony.querySelector('details, vc-compact-knob, vc-compact-toggle')) throw new Error('harmony should use standard settings fields');
      await change(harmony.querySelector('[aria-label="Flatten voicing"]'),'37');
      await wait(() => read().harmonySettings?.flattenVoicingChance === 37,'flatten commit');
      document.querySelector('[aria-label="Undo"]').click();
      await wait(() => read().harmonySettings?.flattenVoicingChance !== 37,'flatten undo');
      (await wait(() => { const button=document.querySelector('[aria-label="Redo"]'); return button && !button.disabled ? button : null; },'flatten redo enabled')).click();
      await wait(() => read().harmonySettings?.flattenVoicingChance === 37,'flatten redo');
      await wait(() => harmony.querySelector('[aria-label="Flatten voicing"]').value === '37','flatten redo rendered');
      await change(harmony.querySelector('[aria-label="Flatten voicing"]'),'100');
      await wait(() => read().harmonySettings?.flattenVoicingChance === 100,'flatten reset');
      harmony.querySelector('[aria-label="Flatten large chords"]').click();
      harmony.querySelector('[aria-label="Progressive voicing"]').click();
      await wait(() => read().harmonySettings?.flattenLargeChords && read().harmonySettings?.progressiveVoicing,'global voicing toggles');
      harmony.querySelector('[aria-label="Close settings"]').click();
      const canvas = document.querySelector('.arrangement-panel canvas[tabindex]');
      canvas.focus();
      // Walk real focus targets until a section header is reached.
      for (let i=0;i<300;i++) {
        canvas.dispatchEvent(new KeyboardEvent('keydown',{key:'ArrowRight',bubbles:true}));
        await new Promise(r => requestAnimationFrame(r));
        const label = canvas.getAttribute('aria-label');
        if (!label.startsWith('Select ') && label.includes(', bars ')) break;
      }
      canvas.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',bubbles:true}));
      const use = await wait(() => document.querySelector('[aria-label="Use Melody harmony"]'),'section harmony control');
      await wait(()=>document.querySelector('.part-panel-toggle').getAttribute('aria-label').includes('Quick access settings'),'section-only panel title');
      if ([...document.querySelectorAll('.part-settings-content > .part-inheritance')].some(label=>['Global part settings','Inherited from global tracks'].includes(label.textContent.trim()))) throw new Error('duplicate part panel title');
      use.click(); await wait(() => read().arrangement.some(section => section.useMelodyHarmony),'section harmony commit');
      document.querySelector('[aria-label="Swap Melody"]').click();
      await wait(() => read().arrangement.some(section => section.swapMelody),'swap melody commit');
      const first = read().arrangement.find(section => section.useMelodyHarmony);
      const unchanged = JSON.stringify(read().arrangement.filter(section => section.id !== first.id));
      document.querySelector('[aria-label="Undo"]').click();
      await wait(() => !read().arrangement.find(section => section.id === first.id).swapMelody,'swap undo');
      (await wait(() => { const button=document.querySelector('[aria-label="Redo"]'); return button && !button.disabled ? button : null; },'swap redo enabled')).click();
      await wait(() => read().arrangement.find(section => section.id === first.id).swapMelody,'swap redo');
      const editor = document.querySelector('vc-harmony-settings');
      editor.scrollIntoView({block:'center'});
      const section = read().arrangement.find(section => section.id === first.id);
      return { section: section.useMelodyHarmony && section.swapMelody, global: read().harmonySettings.flattenVoicingChance===100,
        source: editor.textContent.includes('generated base Melody'), independent: unchanged===JSON.stringify(read().arrangement.filter(section => section.id !== first.id)),
        fits: editor.getBoundingClientRect().right <= innerWidth && editor.getBoundingClientRect().left >= 0 };
    })()` });
    assert.equal(harmonyResult.exceptionDetails, undefined, JSON.stringify(harmonyResult));
    for (const [key,value] of Object.entries(harmonyResult.result.value)) assert.equal(value,true,'harmony '+key);
    const harmonyScreenshot = await command('Page.captureScreenshot', { format: 'png' });
    fs.writeFileSync(path.resolve(__dirname, '../.angular/contextual-harmony-' + width + '.png'), Buffer.from(harmonyScreenshot.data, 'base64'));
    const sectionResult = await command('Runtime.evaluate', { awaitPromise: true, returnByValue: true, expression: `(async () => {
      const wait=async(read,label)=>{for(let i=0;i<200;i++){const value=read();if(value)return value;await new Promise(r=>setTimeout(r,20));}throw new Error(label);};
      const read=()=>JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));
      const editor=await wait(()=>document.querySelector('vc-section-context-editor'),'section context editor');
      const id=editor.querySelector('vc-chordlet-editor').getAttribute('data-section-id');
      const section=()=>read().arrangement.find(section=>section.id===id);
      const primary=JSON.stringify([read().progression,read().progressionChords]);
      const other=JSON.stringify(read().arrangement.filter(section=>section.id!==id));
      const tracks=JSON.stringify(read().tracks);
      const change = changeControl;
      await change(editor.querySelector('input[min="30"]'),'87');
      await wait(()=>section().context?.tempoBpm===87,'section BPM');
      await change(editor.querySelector('input[min="0"]'),'67');
      await wait(()=>section().context?.swingPercent===67,'section swing');
      await change(editor.querySelector('[aria-label="Chord 1 duration in beats"]'),'2');
      await wait(()=>section().customDurations?.[0]===2,'section duration');
      document.querySelector('[aria-label="Undo"]').click();
      await wait(()=>!section().customDurations,'duration undo');
      (await wait(()=>{const button=document.querySelector('[aria-label="Redo"]');return button&&!button.disabled?button:null;},'duration redo enabled')).click();
      await wait(()=>section().customDurations?.[0]===2,'duration redo');
      const chord=editor.querySelector('.chordlet-label');
      chord.dispatchEvent(new MouseEvent('contextmenu',{bubbles:true,cancelable:true,button:2}));
      const menu=await wait(()=>editor.querySelector('[aria-label="Chord root"]'),'section chord menu');
      [...menu.querySelectorAll('button')].find(button=>button.textContent==='D').click();
      await wait(()=>section().customChords?.[0]?.root===2,'section chord edit');
      await wait(()=>editor.querySelector('.chordlet-label').textContent.startsWith('D'),'section chord render');
      editor.querySelector('.chordlet-label').dispatchEvent(new WheelEvent('wheel',{deltaY:-1,bubbles:true,cancelable:true}));
      await wait(()=>section().customChords?.[0]?.inversion===1,'section inversion');
      await change(editor.querySelector('vc-select'),'G');
      if((await selectOptions(editor.querySelectorAll('vc-select')[1])).length!==18) throw new Error('missing section modes');
      await change(editor.querySelectorAll('vc-select')[1],'mixolydian');
      await wait(()=>section().context?.scale==='mixolydian'&&read().scale==='major','local Mixolydian override');
      await change(editor.querySelectorAll('vc-select')[1],'natural-minor');
      await change(editor.querySelector('input[min="-24"]'),'2');
      await wait(()=>section().context?.key==='G'&&section().context?.scale==='natural-minor'&&section().context?.keyChange===2,'section key context');
      await wait(()=>editor.querySelector('.chordlet-label').textContent.startsWith('E'),'section key render');
      editor.querySelector('.chordlet-label').dispatchEvent(new MouseEvent('contextmenu',{bubbles:true,cancelable:true,button:2}));
      const sectionRoots=await wait(()=>editor.querySelector('[aria-label="Chord root"]'),'section root colors');
      const tonic=[...sectionRoots.querySelectorAll('button')].find(button=>button.textContent==='A');
      const sectionRootColors=tonic.style.getPropertyValue('--chord-hue')==='0'
        && editor.querySelector('.chordlet').style.getPropertyValue('--chord-hue')==='210';
      editor.querySelector('.chordlet-label').dispatchEvent(new PointerEvent('pointerdown',{bubbles:true,cancelable:true,button:1}));
      const sectionSpices=await wait(()=>editor.querySelector('[aria-label="Chord spice"]'),'section spice colors');
      const spiceChoice=name=>[...sectionSpices.querySelectorAll('button')].find(button=>button.textContent===name);
      const sectionSpiceColors=Number(spiceChoice('m').dataset.keyOverlap)===1
        && Number(spiceChoice('maj7').dataset.keyOverlap)===.5 && spiceChoice('m').title.includes('A Natural minor');
      sectionSpices.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}));
      await change(document.querySelector('.transport-dock [aria-label="Transpose in semitones"]'),'7');
      await wait(()=>read().transposeSemitones===7,'global transpose with section key');
      await wait(()=>editor.querySelector('.chordlet-label').textContent.startsWith('B'),'section transpose render');
      editor.querySelector('.chordlet-label').dispatchEvent(new PointerEvent('pointerdown',{bubbles:true,cancelable:true,button:1}));
      const transposed=await wait(()=>editor.querySelector('[aria-label="Chord spice"] button[title*="E Natural minor"]'),'transposed section palette');
      const transposeColors=editor.querySelector('.chordlet').style.getPropertyValue('--chord-hue')==='210'
        && Number([...editor.querySelectorAll('.spice-choice')].find(button=>button.textContent==='m').dataset.keyOverlap)===1;
      transposed.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}));
      await change(document.querySelector('.transport-dock [aria-label="Transpose in semitones"]'),'0');
      await wait(()=>read().transposeSemitones===0,'restore global transpose');
      editor.scrollIntoView({block:'center'});
      return {sectionRootColors,sectionSpiceColors,transposeColors,tempo:section().context.tempoBpm===87, durations:section().customDurations[0]===2,
        primary:primary===JSON.stringify([read().progression,read().progressionChords]), tracks:tracks===JSON.stringify(read().tracks),
        others:other===JSON.stringify(read().arrangement.filter(section=>section.id!==id)),
        fits:editor.getBoundingClientRect().left>=0&&editor.getBoundingClientRect().right<=innerWidth&&document.documentElement.scrollWidth<=innerWidth+1
          && [...editor.querySelectorAll('input,vc-select,.context-field button')].every(control=>control.getBoundingClientRect().right<=editor.getBoundingClientRect().right+1)};
    })()` });
    assert.equal(sectionResult.exceptionDetails,undefined,JSON.stringify(sectionResult));
    for(const [key,value] of Object.entries(sectionResult.result.value)) assert.equal(value,true,'P5f '+key);
    const sectionScreenshot=await command('Page.captureScreenshot',{format:'png'});
    fs.writeFileSync(path.resolve(__dirname,'../.angular/contextual-section-'+width+'.png'),Buffer.from(sectionScreenshot.data,'base64'));
    for (const kind of ['root','spice']) {
      await command('Runtime.evaluate',{awaitPromise:true,expression: `(async()=>{
        const chord=document.querySelector('vc-section-context-editor .chordlet-label');
        chord.scrollIntoView({block:'center'});
        chord.dispatchEvent(${kind === 'root' ? "new MouseEvent('contextmenu',{bubbles:true,cancelable:true,button:2})" : "new PointerEvent('pointerdown',{bubbles:true,cancelable:true,button:1})"});
        await new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)));
      })()`});
      const menuScreenshot=await command('Page.captureScreenshot',{format:'png'});
      fs.writeFileSync(path.resolve(__dirname,'../.angular/contextual-chord-'+kind+'-'+width+'.png'),Buffer.from(menuScreenshot.data,'base64'));
      await command('Runtime.evaluate',{expression:"document.querySelector('vc-section-context-editor .chordlet-menu').dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}))"});
    }
    const placementResult = await command('Runtime.evaluate', { awaitPromise: true, returnByValue: true, expression: `(async () => {
      const wait=async(read,label)=>{for(let i=0;i<200;i++){const value=read();if(value)return value;await new Promise(r=>setTimeout(r,20));}throw new Error(label);};
      const read=()=>JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));
      const columns=[...document.querySelectorAll('.section-presence-role')];
      const roles=['melody','bass','chords','arpeggio','drums'];
      const grouped=columns.length===5 && columns.every((column,i)=>column.dataset.controlRole===roles[i]
        && column.querySelectorAll('vc-compact-toggle').length===read().tracks.filter(track=>track.role===roles[i]).length);
      const compact=columns.every(column=>Math.abs(column.getBoundingClientRect().top-columns[0].getBoundingClientRect().top)<1)
        && document.querySelector('.section-presence-columns').getBoundingClientRect().height<230;
      const removed=!document.querySelector('.inspector-arrangement-add');
      const add=document.querySelector('[aria-label="Add arrangement section"]');
      add.scrollIntoView({block:'center'}); add.click();
      const menu=await wait(()=>document.querySelector('[aria-label="Section choices"]'),'section picker');
      await wait(()=>menu.contains(document.activeElement),'picker focus');
      const bounds=menu.getBoundingClientRect();
      const fits=bounds.left>=0 && bounds.right<=innerWidth && bounds.top>=0 && bounds.bottom<=innerHeight;
      const choices=menu.querySelectorAll('button').length===14;
      menu.dispatchEvent(new KeyboardEvent('keydown',{key:'ArrowRight',bubbles:true,cancelable:true}));
      const keyboard=document.activeElement===menu.querySelectorAll('button')[1];
      menu.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true,cancelable:true}));
      await wait(()=>!document.querySelector('.section-picker'),'picker escape');
      const focusReturned=document.activeElement===add;
      const before=read().arrangement.length;
      add.click();
      const reopened=await wait(()=>document.querySelector('.section-picker'),'picker reopened');
      [...reopened.querySelectorAll('button')].find(button=>button.textContent.trim()==='OUTRO').click();
      await wait(()=>read().arrangement.length===before+1,'section added');
      const added=read().arrangement.some(section=>section.type==='OUTRO');
      document.querySelector('[aria-label="Undo"]').click();
      await wait(()=>read().arrangement.length===before,'add undo');
      const canvas=document.querySelector('vc-arrangement-overview canvas[tabindex]');
      canvas.focus(); canvas.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true,cancelable:true}));
      return {grouped,compact,removed,fits,choices,keyboard,focusReturned,added,
        noCanvasBorder:getComputedStyle(canvas).outlineStyle==='none'};
    })()` });
    assert.equal(placementResult.exceptionDetails,undefined,JSON.stringify(placementResult));
    for(const [key,value] of Object.entries(placementResult.result.value)) assert.equal(value,true,'placement '+key);
    const javaXml = fs.readFileSync(path.resolve(__dirname, '../GENERATOR_REFACTOR_TEST_GUICONFIG.xml-config'), 'utf8');
    const xmlImport = await command('Runtime.evaluate', { awaitPromise: true, returnByValue: true, expression: `(async () => {
      const wait = async read => { for(let i=0;i<150;i++){const value=read();if(value)return value;await new Promise(resolve=>setTimeout(resolve,20));} throw new Error('XML import timed out'); };
      const input=document.querySelector('input[type="file"][accept*=".xml-config"]');
      const files=new DataTransfer(); files.items.add(new File([${JSON.stringify(javaXml)}],'Java import.xml-config',{type:'application/xml'}));
      input.files=files.files; input.dispatchEvent(new Event('change',{bubbles:true}));
      const read=()=>JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));
      const loaded=await wait(()=>read().name==='Java import'&&read());
      const before=JSON.stringify(loaded), bad=new DataTransfer();
      bad.items.add(new File(['<GUIConfig>'],'broken.xml-config',{type:'application/xml'}));
      input.files=bad.files; input.dispatchEvent(new Event('change',{bubbles:true}));
      await wait(()=>document.querySelector('.project-message')?.textContent.includes('Could not import:'));
      return {tracks:loaded.tracks.length,sections:loaded.arrangement.length,bpm:loaded.tempoBpm,scale:loaded.scale,
        localNotes:loaded.arrangement[4].trackPhrases['track-melody-1'].length,
        failedImportPreservesState:before===JSON.stringify(read())};
    })()` });
    assert.equal(xmlImport.exceptionDetails,undefined,JSON.stringify(xmlImport));
    assert.deepEqual(xmlImport.result.value,{tracks:18,sections:5,bpm:33,scale:'mixolydian',localNotes:16,failedImportPreservesState:true});
    assert.equal(actual.error, undefined, JSON.stringify(actual));
    assert.equal(actual.width, width);
    assert.deepEqual(actual.copied, [35,45,35,35,35,35]);
    for (const key of ['rulesPreserveTracks','regenerated','composed','keyboardCell','mixed','localRetained','noopDisabled','quickCustomization',
      'createRemoved','seedInDock','noKeySelector','transposeKey','tempoKnob','modeFitsRow','rootMenuFits','rootColors','spiceColors','menuKeyboard','chordletGestures',
      'chordEditors','chordPolicyOnly','chordPolicies','arpMusical','arpMelody','drumMusical','drumMelody','melodyExtended','melodyGlobal','catalogue','defaultResets','flipEmphasis','chordRepairs','tonalTargets','targetModes','targetPreparation','actionsFit','trackRowsFit','groupNamesVisible','pageFits','trackScroll']) assert.equal(actual[key], true, key + ': ' + JSON.stringify(actual));
  } finally {
    await closeBrowser({ child, server, socket, watchdog });
  }
});
