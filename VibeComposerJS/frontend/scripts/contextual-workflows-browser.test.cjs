const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const { spawn } = require('node:child_process');
const test = require('node:test');
const browser = process.env.OVERVIEW_TEST_BROWSER || [
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', '/usr/bin/chromium',
].find(file => fs.existsSync(file));

for (const width of [1440, 780, 390]) test(`production contextual workflows with 30 tracks at ${width}px`, {
  skip: browser ? false : 'Chromium unavailable',
}, async () => {
  const root = path.resolve(__dirname, '../dist/browser');
  const index = fs.readFileSync(path.join(root, 'index.html'), 'utf8');
  const script = `<script>(async () => {
    const wait = async (read, label) => {
      for (let i = 0; i < 300; i++) { const value = read(); if (value) return value; await new Promise(r => setTimeout(r, 50)); }
      throw new Error('Timed out: ' + label);
    };
    const read = () => JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));
    const change = (input, value) => { input.value = value; input.dispatchEvent(new Event('change', { bubbles: true })); };
    const reset = input => input.dispatchEvent(new MouseEvent('contextmenu', { bubbles: true, cancelable: true, button: 2 }));
    try {
      await wait(() => innerWidth === ${width}, 'exact viewport');
      const seed = await wait(() => document.querySelector('[aria-label="Generation seed"]'), 'seed');
      if (!sessionStorage.getItem('contextual-fixture')) {
        change(seed, '9007199254740993');
        const project = await wait(read, 'saved project');
        for (const role of ['melody','bass','chords','arpeggio','drums']) {
          const base = project.tracks.find(track => track.role === role);
          for (let i = 2; i <= 6; i++) project.tracks.push({ ...base, id: 'track-' + role + '-' + i, name: base.name[0] + i,
            generatorSettings: { ...base.generatorSettings, ...(role === 'bass' && i === 2 ? { transpose: 12 } : {}) } });
        }
        project.arrangement[0].rolePartOverrides = { bass: { noteVariation: 35 } };
        project.arrangement[0].trackPartOverrides = { 'track-bass-2': { noteVariation: 45 } };
        sessionStorage.setItem('vibecomposer.project.v12', JSON.stringify(project));
        sessionStorage.setItem('contextual-fixture', 'ready'); location.reload(); return;
      }
      await wait(() => document.querySelectorAll('.track-line').length === 30, '30 track rows');
      // Persist the decoded fixture before comparing a policy-only action.
      change(seed, '9007199254740995');
      await wait(() => read().seed === '9007199254740995', 'canonical project');
      const result = { width: innerWidth };
      result.createRemoved = !document.querySelector('.primary-harmony') && !document.querySelector('.track-browser [aria-label="Generation seed"]');
      result.seedInDock = !!seed.closest('.transport-dock');
      result.noKeySelector = !document.querySelector('select[aria-label="Key"]');
      const transpose = document.querySelector('.transport-dock [aria-label="Transpose in semitones"]');
      change(transpose, '7');
      await wait(() => read().transposeSemitones === 7 && document.querySelector('.transport-project').textContent.includes('G MAJOR'), 'project key label');
      result.transposeKey = transpose.type === 'range' && !transpose.closest('vc-compact-knob').querySelector('.knob-caption'); change(transpose, '0');
      if (innerWidth > 1050) {
        const dock = document.querySelector('.transport-dock').getBoundingClientRect();
        if (getComputedStyle(document.querySelector('.app-frame')).paddingBottom !== '92px' || dock.top < innerHeight - 92) throw new Error('transport dock reserve');
      }
      const tempo = document.querySelector('.transport-dock [aria-label="Tempo in beats per minute"]');
      change(tempo, '149'); await wait(() => read().tempoBpm === 149, 'tempo knob');
      result.tempoKnob = tempo.type === 'range'; change(tempo, '128');
      const mode = document.querySelector('.transport-dock [aria-label="Mode"]');
      result.modeCompact = getComputedStyle(mode).appearance === 'none' && mode.getBoundingClientRect().width < 70;
      change(mode, 'natural-minor'); await wait(() => read().scale === 'natural-minor', 'mode change');
      change(mode, 'major'); await wait(() => read().scale === 'major' && read().transposeSemitones === 0, 'mode restore');
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
      const previous = read(); copy.click();
      await wait(() => read().tracks.find(track => track.id === 'track-bass-1').generatorSettings.noteVariation === 35, 'global copy');
      const after = read();
      result.copied = after.tracks.filter(track => track.role === 'bass').map(track => track.generatorSettings.noteVariation);
      result.localRetained = JSON.stringify(after.arrangement) === JSON.stringify(previous.arrangement);
      result.noopDisabled = await wait(() => copy.disabled, 'copy no-op disabled');
      document.querySelector('[aria-label="Undo"]').click();
      await wait(() => read().tracks.find(track => track.id === 'track-bass-1').generatorSettings.noteVariation === 20, 'copy undo');
      const redo = await wait(() => { const button = document.querySelector('[aria-label="Redo"]'); return button && !button.disabled ? button : null; }, 'redo enabled');
      redo.click();
      await wait(() => read().tracks.find(track => track.id === 'track-bass-1').generatorSettings.noteVariation === 35, 'copy redo');
      document.querySelector('.part-inspector-link').click();
      result.inspectorFocused = document.activeElement.id === 'part-inspector';
      document.querySelector('.track-group.chords .group-select').click();
      await wait(() => document.querySelector('#part-inspector [data-control-role="chords"]'), 'chord inspector');
      const join = await wait(() => document.querySelector('#part-inspector [aria-label="Pattern join"]'), 'chord join');
      change(join, 'EXPAND');
      await wait(() => read().chords.patternJoinMode === 'EXPAND', 'chord join commit');
      const strum = await wait(() => document.querySelector('.part-settings-panel [aria-label="Strum"]'), 'chord strum');
      change(strum, '333');
      change(document.querySelector('.part-settings-panel [aria-label="Strum type"]'), 'ARP_D');
      change(document.querySelector('#part-inspector [aria-label="Transition chance"]'), '100');
      change(document.querySelector('#part-inspector [aria-label="Transition split"]'), '750');
      change(document.querySelector('#part-inspector [aria-label="Strum pause"]'), '20');
      await wait(() => read().chords.strum === 333 && read().chords.strumPauseChance === 20, 'chord articulation commit');
      await wait(() => document.querySelector('#part-inspector [aria-label="Strum"]').value === '333'
        && document.querySelector('#part-inspector [aria-label="Strum type"]').value === 'ARP_D', 'chord editors synchronized');
      result.chordEditors = document.querySelector('#part-inspector [aria-label="Strum"]').value === '333'
        && document.querySelector('#part-inspector [aria-label="Strum type"]').value === 'ARP_D'
        && read().chords.patternJoinMode === 'EXPAND' && read().chords.transitionChance === 100 && read().chords.transitionSplit === 750;
      const chords = document.querySelector('.track-group.chords');
      const tracksBeforePolicy = JSON.stringify(read().tracks);
      change(chords.querySelector('[aria-label="Reroll Struminess %"]'), '100');
      change(chords.querySelector('[aria-label="Reroll Max transition %"]'), '0');
      change(chords.querySelector('[aria-label="Reroll Max strum pause %"]'), '0');
      change(chords.querySelector('[aria-label="Reroll Expand chance %"]'), '100');
      result.chordPolicyOnly = JSON.stringify(read().tracks) === tracksBeforePolicy;
      chords.querySelector('[aria-label="On Compose"]').click();
      document.querySelector('[aria-label="Compose"]').click();
      await wait(() => read().trackRerollCounts?.chords === 1, 'chord automatic reroll');
      result.chordPolicies = read().tracks.filter(track => track.role === 'chords').every(track =>
        track.generatorSettings.strumType.startsWith('ARP') && track.generatorSettings.transitionChance === 0
        && track.generatorSettings.strumPauseChance === 0 && track.generatorSettings.patternJoinMode === 'EXPAND');
      document.querySelector('.track-group.arpeggio .group-select').click();
      const direction = await wait(() => document.querySelector('.part-settings-panel [aria-label="Pitch direction"]'), 'arp direction');
      change(direction, 'custom');
      const customDirection = await wait(() => document.querySelector('#part-inspector [aria-label="Custom direction"]'), 'custom direction editor');
      change(customDirection, '-1, 0, 3, 1');
      change(document.querySelector('.part-settings-panel [aria-label="Rotate"]'), '3');
      change(document.querySelector('#part-inspector [aria-label="Contour"]'), '0, 2, -1');
      document.querySelector('#part-inspector [aria-label="Chord-relative contour"]').click();
      document.querySelector('#part-inspector [aria-label="Octave adjustments"]').click();
      document.querySelector('#part-inspector [aria-label="Correct by melody"]').click();
      await wait(() => read().arpeggio.arpPatternRotate === 3 && read().arpeggio.correctMelodyNotes, 'arp musical settings');
      await wait(() => document.querySelector('#part-inspector [aria-label="Rotate"]').value === '3', 'arp editors synchronized');
      result.arpMusical = read().tracks.filter(track => track.role === 'arpeggio').every(track =>
        track.generatorSettings.pattern === 'custom' && track.generatorSettings.arpPatternCustom.join(',') === '-1,0,3,1'
        && track.generatorSettings.arpContour.join(',') === '0,2,-1' && track.generatorSettings.useOctaveAdjustments)
        && document.querySelector('#part-inspector [aria-label="Rotate"]').value === '3';
      change(document.querySelector('#part-inspector [aria-label="Contour"]'), '');
      await wait(() => read().arpeggio.arpContour.length === 0, 'clear contour');
      change(document.querySelector('.part-settings-panel [aria-label="Rhythm pattern"]'), 'melody');
      await wait(() => read().arpeggio.rhythm === 'melody', 'arp melody rhythm');
      await wait(() => !document.querySelector('.part-settings-panel [aria-label="Hits"]')
        && document.querySelector('#part-inspector [aria-label="Rhythm pattern"]').value === 'melody', 'arp melody editors synchronized');
      result.arpMelody = !document.querySelector('.part-settings-panel [aria-label="Hits"]')
        && document.querySelector('#part-inspector [aria-label="Rhythm pattern"]').value === 'melody';
      document.querySelector('.track-group.drums .group-select').click();
      const ghosts = await wait(() => document.querySelector('.part-settings-panel [aria-label="Ghosts"]'), 'drum ghosts');
      ghosts.click();
      change(document.querySelector('#part-inspector [aria-label="Humanize"]'), '100');
      await wait(() => read().drums.ghosts && read().drums.humanize === 100, 'drum musical settings');
      const multiply = document.querySelector('.part-settings-panel [aria-label="Multiply Hits"]');
      change(multiply, '2');
      await wait(() => read().drums.hitsPerPattern === 8, 'multiply hits');
      await wait(() => document.querySelector('#part-inspector [aria-label="Hits"]').value === '8'
        && document.querySelector('#part-inspector [aria-label="Ghosts"]').getAttribute('aria-pressed') === 'true', 'drum editors synchronized');
      result.drumMusical = read().tracks.filter(track => track.role === 'drums').every(track =>
        track.generatorSettings.hitsPerPattern === 8 && track.generatorSettings.ghosts && track.generatorSettings.humanize === 100)
        && document.querySelector('#part-inspector [aria-label="Ghosts"]').getAttribute('aria-pressed') === 'true'
        && document.querySelector('#part-inspector [aria-label="Hits"]').value === '8' && multiply.value === '';
      document.querySelector('[aria-label="Undo"]').click();
      await wait(() => read().drums.hitsPerPattern === 4, 'multiply undo');
      (await wait(() => { const button = document.querySelector('[aria-label="Redo"]'); return button && !button.disabled ? button : null; }, 'multiply redo enabled')).click();
      await wait(() => read().drums.hitsPerPattern === 8, 'multiply redo');
      change(document.querySelector('.part-settings-panel [aria-label="Rhythm pattern"]'), 'melody');
      await wait(() => document.querySelector('#part-inspector [aria-label="Multiply Hits"]').disabled, 'melody hit action disabled');
      result.drumMelody = document.querySelector('.part-settings-panel [aria-label="Multiply Hits"]').disabled
        && !document.querySelector('.part-settings-panel [aria-label="Hits"]');
      document.querySelector('.track-group.melody .group-select').click();
      await wait(() => document.querySelector('#part-inspector [data-control-role="melody"]'), 'melody inspector');
      const extended = [ ['Doubled rhythm', 'doubledRhythmChance', 100], ['Max exceptions', 'maxNoteExceptions', 4],
        ['Note exceptions', 'noteExceptionChance', 70], ['Split long', 'splitChance', 60],
        ['Lead chords', 'leadChordsChance', 80], ['Start note', 'startNoteChance', 0] ];
      for (const [label, key, value] of extended) {
        change(await wait(() => document.querySelector('#part-inspector [aria-label="' + label + '"]'), label), String(value));
        await wait(() => read().melody[key] === value, label + ' commit');
      }
      result.melodyExtended = read().tracks.filter(track => track.role === 'melody').every(track =>
        extended.every(([, key, value]) => track.generatorSettings[key] === value));
      document.querySelector('[aria-label="Undo"]').click();
      await wait(() => read().melody.startNoteChance === undefined, 'Start note undo');
      (await wait(() => { const button = document.querySelector('[aria-label="Redo"]'); return button && !button.disabled ? button : null; }, 'Start note redo enabled')).click();
      await wait(() => read().melody.startNoteChance === 0, 'Start note redo');
      const globals = document.querySelector('vc-melody-global-settings');
      globals.querySelector('[aria-label="Single note exceptions"]').click();
      await wait(() => read().melodyGlobal?.singleNoteExceptions === true, 'single exceptions commit');
      globals.querySelector('[aria-label="Fill pauses per chord"]').click();
      await wait(() => read().melodyGlobal?.fillPausesPerChord === true, 'per chord fill commit');
      change(globals.querySelector('[aria-label="Max direction changes"]'), '6');
      await wait(() => read().melodyGlobal?.maxDirectionChanges === 6, 'direction budget commit');
      document.querySelector('[aria-label="Undo"]').click();
      await wait(() => read().melodyGlobal?.maxDirectionChanges === undefined, 'global direction undo');
      (await wait(() => { const button = document.querySelector('[aria-label="Redo"]'); return button && !button.disabled ? button : null; }, 'global redo enabled')).click();
      await wait(() => read().melodyGlobal?.maxDirectionChanges === 6, 'global direction redo');
      const effect = globals.querySelector('[aria-label="Pattern effect"]');
      await wait(() => effect.value === '2', 'pattern effect default');
      for (const value of [0, 1, 2]) {
        change(effect, String(value));
        await wait(() => read().melodyGlobal?.patternEffect === value, 'pattern effect commit');
      }
      globals.querySelector('[aria-label="Arpy surprises"]').click();
      await wait(() => read().melodyGlobal?.arpySurprises === true, 'arpy surprises commit');
      document.querySelector('[aria-label="Undo"]').click();
      await wait(() => read().melodyGlobal?.arpySurprises === undefined
        && globals.querySelector('[aria-label="Arpy surprises"]').getAttribute('aria-pressed') === 'false', 'arpy surprises undo');
      (await wait(() => { const button = document.querySelector('[aria-label="Redo"]'); return button && !button.disabled ? button : null; }, 'arpy redo enabled')).click();
      await wait(() => read().melodyGlobal?.arpySurprises === true, 'arpy surprises redo');
      const catalogueTracks = JSON.stringify(read().tracks);
      await wait(() => globals.querySelector('[aria-label="New blocks"]').value === '25', 'new blocks default');
      change(globals.querySelector('[aria-label="New blocks"]'), '0');
      await wait(() => read().melodyGlobal?.newBlocksChance === 0, 'catalogue selection commit');
      const families = ['Scaley', 'Neighbory', 'Arpy', 'Chordy', 'Wavy', 'Interval', 'Note'];
      for (const [index, family] of families.entries()) {
        change(globals.querySelector('[aria-label="' + family + ' weight"]'), String(20 + index * 10));
        await wait(() => read().melodyGlobal?.blockTypeWeights?.[index] === 20 + index * 10, family + ' weight commit');
        await new Promise(done => requestAnimationFrame(() => requestAnimationFrame(done)));
      }
      await wait(() => JSON.stringify(read().melodyGlobal?.blockTypeWeights) === '[20,30,40,50,60,70,80]', 'independent family weights');
      const preference = globals.querySelector('[aria-label="Block change preference"]');
      globals.querySelector('[aria-label="Default block change order"]').click();
      await wait(() => JSON.stringify(read().melodyGlobal?.blockChangePreference) === '[0,7,4,3,1,2,5,6]', 'default block order');
      const validPreference = JSON.stringify(read().melodyGlobal);
      change(preference, '0,1,2,3,4,5,6,6');
      await wait(() => globals.querySelector('[role="alert"]'), 'invalid preference alert');
      result.catalogue = JSON.stringify(read().melodyGlobal) === validPreference && preference.value === '0, 7, 4, 3, 1, 2, 5, 6';
      change(preference, '7,6,5,4,3,2,1,0');
      await wait(() => JSON.stringify(read().melodyGlobal?.blockChangePreference) === '[7,6,5,4,3,2,1,0]', 'ranked block order');
      globals.querySelector('[aria-label="Uniform block changes"]').click();
      await wait(() => read().melodyGlobal?.blockChangePreference?.length === 0, 'uniform block order');
      document.querySelector('[aria-label="Undo"]').click();
      await wait(() => JSON.stringify(read().melodyGlobal?.blockChangePreference) === '[7,6,5,4,3,2,1,0]', 'block preference undo');
      (await wait(() => { const button = document.querySelector('[aria-label="Redo"]'); return button && !button.disabled ? button : null; }, 'catalogue redo enabled')).click();
      await wait(() => read().melodyGlobal?.blockChangePreference?.length === 0, 'block preference redo');
      result.catalogue &&= JSON.stringify(read().tracks) === catalogueTracks && !globals.querySelector('[role="alert"]');
      reset(globals.querySelector('[aria-label="New blocks"]'));
      await wait(() => read().melodyGlobal?.newBlocksChance === 25, 'new blocks right-click default');
      reset(globals.querySelector('[aria-label="New blocks"]'));
      document.querySelector('[aria-label="Undo"]').click();
      await wait(() => read().melodyGlobal?.newBlocksChance === 0, 'reset undo and default no-op');
      (await wait(() => { const button = document.querySelector('[aria-label="Redo"]'); return button && !button.disabled ? button : null; }, 'reset redo enabled')).click();
      await wait(() => read().melodyGlobal?.newBlocksChance === 25, 'reset redo');
      reset(globals.querySelector('[aria-label="Arpy weight"]'));
      await wait(() => JSON.stringify(read().melodyGlobal?.blockTypeWeights) === '[20,30,100,50,60,70,80]', 'family default isolation');
      change(effect, '0'); await wait(() => read().melodyGlobal?.patternEffect === 0, 'effect before reset');
      await new Promise(done => requestAnimationFrame(() => requestAnimationFrame(done)));
      reset(effect); await wait(() => read().melodyGlobal?.patternEffect === 2, 'effect combobox default');
      change(tempo, '150'); await wait(() => read().tempoBpm === 150, 'tempo before reset');
      reset(tempo); await wait(() => read().tempoBpm === 120, 'tempo knob default');
      change(mode, 'natural-minor'); await wait(() => read().scale === 'natural-minor', 'mode before reset');
      reset(mode); await wait(() => read().scale === 'major', 'mode combobox default');
      const speed = globals.closest('#part-inspector').querySelector('[aria-label="Speed"]');
      change(speed, '80'); await wait(() => read().melody.speed === 80, 'part knob before reset');
      await new Promise(done => requestAnimationFrame(() => requestAnimationFrame(done)));
      reset(speed); await wait(() => read().melody.speed === 50, 'part knob default');
      result.defaultResets = read().melodyGlobal.newBlocksChance === 25 && read().melodyGlobal.patternEffect === 2
        && read().tempoBpm === 120 && read().scale === 'major' && read().melody.speed === 50;
      const flipNotes = JSON.stringify(read().tracks);
      globals.querySelector('[aria-label="Melody pattern flip"]').click();
      await wait(() => read().melodyGlobal?.patternFlip === true, 'Melody grid flip');
      globals.querySelector('[aria-label="Emphasize key"]').click();
      await wait(() => read().melodyGlobal?.emphasizeKey === true, 'key emphasis');
      result.flipEmphasis = JSON.stringify(read().tracks) === flipNotes;
      document.querySelector('[aria-label="Undo"]').click();
      await wait(() => read().melodyGlobal?.emphasizeKey === undefined, 'key emphasis undo');
      (await wait(() => { const button = document.querySelector('[aria-label="Redo"]'); return button && !button.disabled ? button : null; }, 'emphasis redo enabled')).click();
      await wait(() => read().melodyGlobal?.emphasizeKey === true, 'key emphasis redo');
      const chordTargetTracks = JSON.stringify(read().tracks);
      const chordTargetControl = globals.querySelector('[aria-label="Chord note target"]');
      const avoidControl = globals.querySelector('[aria-label="Replace avoid notes"]');
      await wait(() => chordTargetControl.value === '0' && avoidControl.value === '0', 'chord repair defaults');
      change(chordTargetControl, '70');
      await wait(() => read().melodyGlobal?.chordNoteTarget === 70, 'chord target commit');
      for (const level of [1, 2]) {
        change(avoidControl, String(level));
        await wait(() => read().melodyGlobal?.replaceAvoidNotes === level, 'avoid level commit');
        await new Promise(done => requestAnimationFrame(() => requestAnimationFrame(done)));
      }
      reset(chordTargetControl);
      await wait(() => read().melodyGlobal?.chordNoteTarget === 0 && read().melodyGlobal?.replaceAvoidNotes === 2, 'chord reset isolation');
      document.querySelector('[aria-label="Undo"]').click();
      await wait(() => read().melodyGlobal?.chordNoteTarget === 70, 'chord target undo');
      (await wait(() => { const button = document.querySelector('[aria-label="Redo"]'); return button && !button.disabled ? button : null; }, 'chord redo enabled')).click();
      await wait(() => read().melodyGlobal?.chordNoteTarget === 0, 'chord target redo');
      reset(avoidControl);
      await wait(() => read().melodyGlobal?.replaceAvoidNotes === 0, 'avoid default reset');
      result.chordRepairs = JSON.stringify(read().tracks) === chordTargetTracks;
      const tonalTracks = JSON.stringify(read().tracks);
      const tonicControl = globals.querySelector('[aria-label="Tonic note target"]');
      const modeControl = globals.querySelector('[aria-label="Mode note target"]');
      await wait(() => tonicControl.value === '0' && modeControl.value === '0', 'tonal target defaults');
      change(tonicControl, '70');
      await wait(() => read().melodyGlobal?.tonicNoteTarget === 70, 'tonic target commit');
      change(modeControl, '60');
      await wait(() => read().melodyGlobal?.modeNoteTarget === 60, 'mode target commit');
      await new Promise(done => requestAnimationFrame(() => requestAnimationFrame(done)));
      reset(tonicControl);
      await wait(() => read().melodyGlobal?.tonicNoteTarget === 0 && read().melodyGlobal?.modeNoteTarget === 60, 'tonic default isolation');
      document.querySelector('[aria-label="Undo"]').click();
      await wait(() => read().melodyGlobal?.tonicNoteTarget === 70, 'tonic target undo');
      (await wait(() => { const button = document.querySelector('[aria-label="Redo"]'); return button && !button.disabled ? button : null; }, 'tonic redo enabled')).click();
      await wait(() => read().melodyGlobal?.tonicNoteTarget === 0, 'tonic target redo');
      reset(modeControl);
      await wait(() => read().melodyGlobal?.modeNoteTarget === 0, 'mode default reset');
      result.tonalTargets = JSON.stringify(read().tracks) === tonalTracks;
      const modeTracks = JSON.stringify(read().tracks);
      const blockModeControl = globals.querySelector('[aria-label="Block target mode"]');
      await wait(() => blockModeControl.value === '2', 'block target mode default');
      for (const blockMode of [0, 1, 2, 1]) {
        change(blockModeControl, String(blockMode));
        await wait(() => read().melodyGlobal?.blockTargetMode === blockMode, 'block target mode commit');
        await new Promise(done => requestAnimationFrame(() => requestAnimationFrame(done)));
      }
      reset(blockModeControl);
      await wait(() => read().melodyGlobal?.blockTargetMode === 2, 'block target mode right-click default');
      document.querySelector('[aria-label="Undo"]').click();
      await wait(() => read().melodyGlobal?.blockTargetMode === 1, 'block target mode undo');
      (await wait(() => { const button = document.querySelector('[aria-label="Redo"]'); return button && !button.disabled ? button : null; }, 'block mode redo enabled')).click();
      await wait(() => read().melodyGlobal?.blockTargetMode === 2, 'block target mode redo');
      result.targetModes = JSON.stringify(read().tracks) === modeTracks;
      const targetsBefore = JSON.stringify(read().tracks);
      await wait(() => globals.querySelector('[aria-label="Target direction"]').value === 'ASC', 'target direction default');
      change(globals.querySelector('[aria-label="Target variation"]'), '6');
      await wait(() => read().melodyGlobal?.targetNoteVariation === 6, 'target variation commit');
      for (const direction of ['ANY', 'DESC', 'ASC']) {
        change(globals.querySelector('[aria-label="Target direction"]'), direction);
        await wait(() => read().melodyGlobal?.targetDirection === direction, 'target direction commit');
      }
      globals.querySelector('[aria-label="Directions from progression"]').click();
      await wait(() => read().melodyGlobal?.useDirectionsFromProgression === true, 'progression directions commit');
      globals.querySelector('[aria-label="Force first outline"]').click();
      await wait(() => read().melodyGlobal?.forceFirstOutline === true, 'force outline commit');
      result.targetPreparation = JSON.stringify(read().tracks) === targetsBefore;
      document.querySelector('[aria-label="Undo"]').click();
      await wait(() => read().melodyGlobal?.forceFirstOutline === undefined, 'force outline undo');
      (await wait(() => { const button = document.querySelector('[aria-label="Redo"]'); return button && !button.disabled ? button : null; }, 'outline redo enabled')).click();
      await wait(() => read().melodyGlobal?.forceFirstOutline === true, 'force outline redo');
      const beforePreparation = read();
      document.querySelector('[aria-label="Compose"]').click();
      await wait(() => JSON.stringify(read().tracks) !== targetsBefore, 'prepared outline Compose');
      const melodyMembers = read().tracks.filter(track => track.role === 'melody');
      const outlineFields = ['chordNoteChoices', 'melodyPatternOffsets', 'speed', 'maxBlockChange', 'blockJump',
        'maxNoteExceptions', 'doubledRhythmChance', 'splitChance', 'noteExceptionChance'];
      const outlineDefaults = { chordNoteChoices: [0,2,2,4], melodyPatternOffsets: [1,2,1,3], speed: 50,
        maxBlockChange: 7, blockJump: 1, maxNoteExceptions: 0, doubledRhythmChance: 0, splitChance: 0, noteExceptionChance: 0 };
      result.targetPreparation &&= melodyMembers.every(track => track.rerollLocked || outlineFields.every(key =>
        JSON.stringify(track.generatorSettings[key] ?? outlineDefaults[key]) === JSON.stringify(melodyMembers[0].generatorSettings[key] ?? outlineDefaults[key])));
      document.querySelector('[aria-label="Undo"]').click();
      await wait(() => JSON.stringify(read().tracks) === JSON.stringify(beforePreparation.tracks), 'prepared outline undo');
      result.melodyGlobal = read().tracks.filter(track => track.role === 'melody').every(track =>
        !Object.hasOwn(track.generatorSettings, 'singleNoteExceptions') && !Object.hasOwn(track.generatorSettings, 'maxDirectionChanges')
        && !Object.hasOwn(track.generatorSettings, 'patternEffect') && !Object.hasOwn(track.generatorSettings, 'arpySurprises')
        && !Object.hasOwn(track.generatorSettings, 'targetDirection') && !Object.hasOwn(track.generatorSettings, 'forceFirstOutline')
        && !Object.hasOwn(track.generatorSettings, 'blockTargetMode'));
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
  result.catch(() => {}); // Startup failure can precede awaiting the browser result.
  const server = http.createServer((request, response) => {
    if (request.url === '/result') {
      let body = ''; request.on('data', chunk => body += chunk);
      request.on('end', () => { response.end('OK'); try { resolve(JSON.parse(body)); } catch (error) { reject(error); } }); return;
    }
    if (request.url === '/') { response.setHeader('Content-Type', 'text/html'); response.end(index.replace('</body>', script + '</body>')); return; }
    const file = path.resolve(root, '.' + new URL(request.url, 'http://localhost').pathname);
    if (path.relative(root, file).startsWith('..') || !fs.existsSync(file) || !fs.statSync(file).isFile()) { response.writeHead(404); response.end(); return; }
    response.setHeader('Content-Type', file.endsWith('.js') ? 'text/javascript' : file.endsWith('.css') ? 'text/css' : 'application/octet-stream');
    fs.createReadStream(file).pipe(response);
  });
  await new Promise(done => server.listen(0, '127.0.0.1', done));
  const profile = fs.mkdtempSync(path.resolve(__dirname, '../.angular/contextual-browser-'));
  const child = spawn(browser, ['--headless', '--disable-gpu', '--no-first-run', '--disable-background-networking',
    '--disable-background-timer-throttling', '--disable-renderer-backgrounding', '--no-default-browser-check',
    '--remote-debugging-port=0', '--window-size=' + width + ',1000', '--user-data-dir=' + profile, 'http://127.0.0.1:' + server.address().port + '/'], { stdio: ['ignore', 'ignore', 'pipe'] });
  let errors = ''; child.stderr.on('data', chunk => errors = (errors + chunk).slice(-1000));
  child.on('error', reject); child.on('exit', code => reject(new Error('Chromium exited ' + code + ': ' + errors)));
  const timer = setTimeout(() => reject(new Error('Browser timed out: ' + errors)), 45000);
  let socket;
  try {
    const portFile = path.join(profile, 'DevToolsActivePort');
    let port;
    for (let i = 0; !port && i < 100; i++) {
      try { port = Number(fs.readFileSync(portFile, 'utf8').split('\n')[0]); }
      catch (error) { if (!['ENOENT','EBUSY','EPERM'].includes(error.code)) throw error; }
      if (!port) await new Promise(done => setTimeout(done, 50));
    }
    if (!port) throw new Error('Chromium debugging port unavailable: ' + errors);
    let target;
    for (let i = 0; !target && i < 100; i++) {
      target = (await (await fetch('http://127.0.0.1:' + port + '/json/list', { signal: AbortSignal.timeout(5000) })).json()).find(item => item.type === 'page');
      if (!target) await new Promise(done => setTimeout(done, 50));
    }
    socket = new WebSocket(target.webSocketDebuggerUrl);
    await new Promise((done, fail) => { socket.addEventListener('open', done, { once: true }); socket.addEventListener('error', fail, { once: true }); });
    let commandId = 0;
    const command = (method, params = {}) => new Promise((done, fail) => {
      const id = ++commandId;
      const listener = event => { const message = JSON.parse(event.data); if (message.id !== id) return;
        socket.removeEventListener('message', listener); message.error ? fail(new Error(JSON.stringify(message.error))) : done(message.result); };
      socket.addEventListener('message', listener); socket.send(JSON.stringify({ id, method, params }));
    });
    await command('Emulation.setDeviceMetricsOverride', { width, height: 1000, deviceScaleFactor: 1, mobile: false });
    const actual = await result;
    assert.equal(actual.error, undefined, JSON.stringify(actual));
    // Exercise an actual right-button contextmenu, not just the synthetic workflow events.
    await command('Runtime.evaluate', { expression: `(() => {
      const control = document.querySelector('[aria-label="New blocks"]');
      control.scrollIntoView({ block: 'center' }); control.value = '90';
      control.dispatchEvent(new Event('change', { bubbles: true }));
    })()` });
    await new Promise(done => setTimeout(done, 100));
    const resetPoint = await command('Runtime.evaluate', { expression: `(() => {
      const box = document.querySelector('[aria-label="New blocks"]').getBoundingClientRect();
      return { x: box.left + box.width / 2, y: box.top + box.height / 2 };
    })()`, returnByValue: true });
    await command('Input.dispatchMouseEvent', { type: 'mousePressed', ...resetPoint.result.value, button: 'right', clickCount: 1 });
    await command('Input.dispatchMouseEvent', { type: 'mouseReleased', ...resetPoint.result.value, button: 'right', clickCount: 1 });
    const resetValue = await command('Runtime.evaluate', { expression: `JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')).melodyGlobal.newBlocksChance`, returnByValue: true });
    assert.equal(resetValue.result.value, 25, 'real right-click resets New blocks');
    // Real left-button pointer capture/reorder through CDP, committing exactly once.
    const snapshot = await command('Runtime.evaluate', { expression: `(() => {
      const buttons = [...document.querySelectorAll('.chordlet-label')];
      const points = buttons.slice(0,2).map(button => { const b = button.getBoundingClientRect(); return { x: b.left + b.width / 2, y: b.top + b.height / 2 }; });
      return { points, project: JSON.parse(sessionStorage.getItem('vibecomposer.project.v12')) };
    })()`, returnByValue: true });
    const beforeDrag = snapshot.result.value;
    await command('Input.dispatchMouseEvent', { type: 'mousePressed', ...beforeDrag.points[0], button: 'left', clickCount: 1 });
    await command('Input.dispatchMouseEvent', { type: 'mouseMoved', ...beforeDrag.points[1], button: 'left', buttons: 1 });
    await command('Input.dispatchMouseEvent', { type: 'mouseReleased', ...beforeDrag.points[1], button: 'left', clickCount: 1 });
    await new Promise(done => setTimeout(done, 100));
    const moved = await command('Runtime.evaluate', { expression: `JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'))`, returnByValue: true });
    assert.equal(moved.result.value.progression[1], beforeDrag.project.progression[0]);
    assert.deepEqual(moved.result.value.progressionChords[1], beforeDrag.project.progressionChords[0]);
    const screenshot = await command('Page.captureScreenshot', { format: 'png' });
    fs.writeFileSync(path.resolve(__dirname, '../.angular/contextual-' + width + '.png'), Buffer.from(screenshot.data, 'base64'));
    await command('Runtime.evaluate', { expression: `document.querySelector('vc-melody-global-settings').scrollIntoView({ block: 'center' })` });
    const globalScreenshot = await command('Page.captureScreenshot', { format: 'png' });
    fs.writeFileSync(path.resolve(__dirname, '../.angular/contextual-melody-global-' + width + '.png'), Buffer.from(globalScreenshot.data, 'base64'));
    await command('Runtime.evaluate', { expression: `document.querySelector('[aria-label="Block target mode"]').closest('fieldset').scrollIntoView({ block: 'center' })` });
    const targetModeScreenshot = await command('Page.captureScreenshot', { format: 'png' });
    fs.writeFileSync(path.resolve(__dirname, '../.angular/contextual-melody-target-modes-' + width + '.png'), Buffer.from(targetModeScreenshot.data, 'base64'));
    await command('Runtime.evaluate', { expression: `document.querySelector('.catalogue-area').scrollIntoView({ block: 'center' })` });
    const catalogueScreenshot = await command('Page.captureScreenshot', { format: 'png' });
    fs.writeFileSync(path.resolve(__dirname, '../.angular/contextual-melody-catalogue-' + width + '.png'), Buffer.from(catalogueScreenshot.data, 'base64'));
    await command('Runtime.evaluate', { expression: `document.querySelector('.track-group.arpeggio .group-select').click()` });
    await new Promise(done => setTimeout(done, 100));
    await command('Runtime.evaluate', { expression: `document.querySelector('#part-inspector').scrollIntoView({ block: 'start' })` });
    const arpScreenshot = await command('Page.captureScreenshot', { format: 'png' });
    fs.writeFileSync(path.resolve(__dirname, '../.angular/contextual-arp-' + width + '.png'), Buffer.from(arpScreenshot.data, 'base64'));
    const harmonyResult = await command('Runtime.evaluate', { awaitPromise: true, returnByValue: true, expression: `(async () => {
      const wait = async (read, label) => { for (let i = 0; i < 200; i++) { const value = read(); if (value) return value; await new Promise(r => setTimeout(r,20)); } throw new Error(label); };
      const read = () => JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));
      const change = (element,value) => { element.value=value; element.dispatchEvent(new Event('change',{bubbles:true})); };
      const harmony = document.querySelector('vc-harmony-settings');
      await wait(()=>document.querySelector('.part-panel-toggle').textContent.includes('Global Arpeggio settings'),'global role panel title');
      harmony.querySelector('details').open=true;
      change(harmony.querySelector('[aria-label="Flatten voicing"]'),'37');
      await wait(() => read().harmonySettings?.flattenVoicingChance === 37,'flatten commit');
      document.querySelector('[aria-label="Undo"]').click();
      await wait(() => read().harmonySettings?.flattenVoicingChance !== 37,'flatten undo');
      (await wait(() => { const button=document.querySelector('[aria-label="Redo"]'); return button && !button.disabled ? button : null; },'flatten redo enabled')).click();
      await wait(() => read().harmonySettings?.flattenVoicingChance === 37,'flatten redo');
      await wait(() => harmony.querySelector('[aria-label="Flatten voicing"]').value === '37','flatten redo rendered');
      harmony.querySelector('[aria-label="Flatten voicing"]').dispatchEvent(new MouseEvent('contextmenu',{bubbles:true,cancelable:true,button:2}));
      await wait(() => read().harmonySettings?.flattenVoicingChance === 100,'flatten reset');
      harmony.querySelector('[aria-label="Flatten large chords"]').click();
      harmony.querySelector('[aria-label="Progressive voicing"]').click();
      await wait(() => read().harmonySettings?.flattenLargeChords && read().harmonySettings?.progressiveVoicing,'global voicing toggles');
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
      await wait(()=>document.querySelector('.part-panel-toggle').textContent.includes('Quick access settings'),'section-only panel title');
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
      const editor = document.querySelector('vc-harmony-settings'); editor.querySelector('details').open=true;
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
      const change=(input,value)=>{input.value=value;input.dispatchEvent(new Event('change',{bubbles:true}));};
      change(editor.querySelector('input[min="30"]'),'87');
      await wait(()=>section().context?.tempoBpm===87,'section BPM');
      change(editor.querySelector('input[min="0"]'),'67');
      await wait(()=>section().context?.swingPercent===67,'section swing');
      change(editor.querySelector('[aria-label="Chord 1 duration in beats"]'),'2');
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
      change(editor.querySelector('select'),'G');
      change(editor.querySelectorAll('select')[1],'natural-minor');
      change(editor.querySelector('input[min="-24"]'),'2');
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
        && Number(spiceChoice('maj7').dataset.keyOverlap)===.5 && spiceChoice('m').title.includes('A minor');
      sectionSpices.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}));
      change(document.querySelector('.transport-dock [aria-label="Transpose in semitones"]'),'7');
      await wait(()=>read().transposeSemitones===7,'global transpose with section key');
      await wait(()=>editor.querySelector('.chordlet-label').textContent.startsWith('B'),'section transpose render');
      editor.querySelector('.chordlet-label').dispatchEvent(new PointerEvent('pointerdown',{bubbles:true,cancelable:true,button:1}));
      const transposed=await wait(()=>editor.querySelector('[aria-label="Chord spice"] button[title*="E minor"]'),'transposed section palette');
      const transposeColors=editor.querySelector('.chordlet').style.getPropertyValue('--chord-hue')==='210'
        && Number([...editor.querySelectorAll('.spice-choice')].find(button=>button.textContent==='m').dataset.keyOverlap)===1;
      transposed.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}));
      change(document.querySelector('.transport-dock [aria-label="Transpose in semitones"]'),'0');
      await wait(()=>read().transposeSemitones===0,'restore global transpose');
      editor.scrollIntoView({block:'center'});
      return {sectionRootColors,sectionSpiceColors,transposeColors,tempo:section().context.tempoBpm===87, durations:section().customDurations[0]===2,
        primary:primary===JSON.stringify([read().progression,read().progressionChords]), tracks:tracks===JSON.stringify(read().tracks),
        others:other===JSON.stringify(read().arrangement.filter(section=>section.id!==id)),
        fits:editor.getBoundingClientRect().left>=0&&editor.getBoundingClientRect().right<=innerWidth&&document.documentElement.scrollWidth<=innerWidth+1
          && [...editor.querySelectorAll('input,select,.context-field button')].every(control=>control.getBoundingClientRect().right<=editor.getBoundingClientRect().right+1)};
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
    assert.equal(actual.error, undefined, JSON.stringify(actual));
    assert.equal(actual.width, width);
    assert.deepEqual(actual.copied, [35,45,35,35,35,35]);
    for (const key of ['rulesPreserveTracks','regenerated','composed','keyboardCell','mixed','localRetained','noopDisabled','inspectorFocused',
      'createRemoved','seedInDock','noKeySelector','transposeKey','tempoKnob','modeCompact','rootMenuFits','rootColors','spiceColors','menuKeyboard','chordletGestures',
      'chordEditors','chordPolicyOnly','chordPolicies','arpMusical','arpMelody','drumMusical','drumMelody','melodyExtended','melodyGlobal','catalogue','defaultResets','flipEmphasis','chordRepairs','tonalTargets','targetModes','targetPreparation','actionsFit','trackRowsFit','groupNamesVisible','pageFits','trackScroll']) assert.equal(actual[key], true, key + ': ' + JSON.stringify(actual));
  } finally {
    socket?.close();
    clearTimeout(timer); child.kill(); server.close();
    await new Promise(done => child.exitCode !== null || child.signalCode !== null ? done() : child.once('exit', done));
    await fs.promises.rm(profile, { recursive: true, force: true, maxRetries: 10, retryDelay: 150 });
  }
});
