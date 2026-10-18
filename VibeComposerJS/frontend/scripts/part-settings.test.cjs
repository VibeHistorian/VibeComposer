const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const ts = require('typescript');
const { Midi } = require('@tonejs/midi');

// Share immutable compiled text; each fixture still evaluates fresh modules and services.
const compiledModules = new Map();

test('failed contextual Compose keeps the rendered project, storage, history and playback and reports its cause', () => {
  const { service, workspace, load, stored } = fixture();
  service.updateSettings({ seed: '42' });
  service.updateMelodyGlobal({ contextualMelody: true, rangeMin: 48, rangeMax: 72, rangeRandomizeOnCompose: true });
  service.updateSettings({ name: 'Redo still available' });
  service.undo();
  const previous = service.project(), notes = workspace.scoreNotes();
  const history = JSON.stringify(service.historyEntries());
  const storage = [...stored];
  assert.equal(service.canRedo(), true);
  const ranges = load('core/music/contextual-randomizer.ts');
  const range = ranges.contextualRange;
  ranges.contextualRange = () => ({ rangeMin: 48, rangeMax: 48 });
  const { TransportDockComponent } = load('shared/transport-dock.component.ts');
  const transport = new TransportDockComponent();
  let restarts = 0;
  transport.playback.restartAfterCompose = () => { restarts++; };
  try {
    for (let attempt = 0; attempt < 2; attempt++) {
      transport.generate('compose');
      assert.equal(service.project(), previous);
      assert.match(service.generationError(), /No contextual melody satisfies/);
      assert.match(transport.generationMessage(), /Compose cancelled.*No contextual melody.*Previous arrangement kept/);
      assert.deepEqual(workspace.scoreNotes(), notes, 'later renders continue using the valid project');
      assert.equal(JSON.stringify(service.historyEntries()), history);
      assert.deepEqual([...stored], storage);
      assert.equal(service.canRedo(), true);
      assert.equal(restarts, 0);
    }
  } finally { ranges.contextualRange = range; }
  assert.equal(service.generateComposition('compose'), 'changed');
  assert.equal(service.generationError(), null, 'a successful attempt clears the old error');
  assert.equal(service.canRedo(), false);
  assert.doesNotThrow(() => workspace.scoreNotes());
});

test('section layout failures reject Compose and Regenerate before publishing a candidate', () => {
  const { service, phrase } = fixture();
  service.updateTrackGenerationPolicy('melody', { onRegenerate: true, rerollSeeds: true });
  const previous = service.project();
  const layout = phrase.layOutTrackPhrase;
  let attempts = 0;
  phrase.layOutTrackPhrase = () => { attempts++; throw new RangeError('Section melody cannot be generated.'); };
  try {
    for (const event of ['compose', 'regenerate']) {
      assert.equal(service.generateComposition(event), 'invalid');
      assert.equal(service.project(), previous);
      assert.equal(service.generationError(), 'Section melody cannot be generated.');
    }
    assert.equal(attempts, 2, 'both events validate section generation, beyond global note counts');
  } finally { phrase.layOutTrackPhrase = layout; }
});

test('track layouts share generation across consumers without sharing mutable note objects', () => {
  const { service, phrase, load } = fixture();
  service.project().arrangement.forEach((_, index) => service.setSectionTrackPresence(index, 'track-melody-1', true));
  const generator = load('core/music/melody-generator.ts');
  const generate = generator.generateMelody;
  let calls = 0;
  generator.generateMelody = (...args) => { calls++; return generate(...args); };
  const project = service.project();
  const track = project.tracks.find(track => track.role === 'melody');
  const score = phrase.layOutTrackPhrase(project, track, true);
  const firstCalls = calls;
  assert.ok(score.length > 0);
  assert.ok(firstCalls > 0);
  const overview = phrase.layOutTrackPhrase(project, track);
  const playback = phrase.layOutTrackPhrase(project, track);
  assert.equal(calls, firstCalls, 'other consumers reuse completed section generation');
  assert.deepEqual(overview, score.map(({ phraseSource, ...note }) => note));
  assert.deepEqual(playback, overview);
  overview[0].midi = 0;
  overview.pop();
  assert.deepEqual(phrase.layOutTrackPhrase(project, track), playback, 'caller mutations cannot corrupt cached notes');
  assert.deepEqual(phrase.layOutTrackPhrase(project, track, true), score, 'source annotations survive plain requests');
  service.updateTrackGeneratorSettings(track.id, { transpose: 12 });
  const updated = service.project();
  const changed = phrase.layOutTrackPhrase(updated, updated.tracks.find(item => item.id === track.id));
  assert.ok(calls > firstCalls);
  assert.notDeepEqual(changed, playback, 'new settings invalidate the layout');
  service.undo();
  assert.deepEqual(phrase.layOutTrackPhrase(service.project(), track), playback);
});

test('mix edits reuse score phrases and note counts with real Angular signal equality', async () => {
  const reactivity = await import('@angular/core');
  const { service, workspace, phrase } = fixture({ reactivity });
  let generations = 0;
  for (const name of ['phraseForTrack', 'layOutTrackPhrase']) {
    const original = phrase[name];
    phrase[name] = (...args) => { generations++; return original(...args); };
  }
  const notes = workspace.scoreNotes();
  workspace.tracks();
  const count = generations;
  assert.ok(count > 0);
  const original = service.project();
  for (const panPercent of [-50, -25, 0, 25, 50]) {
    service.updateTrack('track-bass-1', { mix: { panPercent, volumePercent: 150 } });
    assert.equal(workspace.tracks().find(track => track.id === 'track-bass-1').mix.panPercent, panPercent);
    assert.deepEqual(workspace.scoreNotes(), notes);
    assert.equal(generations, count, 'knob commits must not invoke note generators');
  }
  const { samePhraseInputs } = require('./music-loader.cjs').loadMusic('phrase-inputs.ts');
  assert.equal(samePhraseInputs(original, service.project()), true);
  service.updateTrackGeneratorSettings('track-bass-1', { transpose: 12 });
  workspace.scoreNotes();
  assert.ok(generations > count, 'musical settings still regenerate notes');
  assert.equal(samePhraseInputs(original, service.project()), false);
});

test('overview role presence clears mixed or full groups and fills empty groups in one undo step', () => {
  const { service } = fixture();
  service.duplicateTrack('track-bass-1');
  const tracks = service.project().tracks.filter(track => track.role === 'bass');
  assert.equal(tracks.length, 2);
  const sectionId = service.project().arrangement[0].id;
  for (const flags of [[true, false], [false, true], [true, true], [false, false]]) {
    tracks.forEach((track, index) => service.setSectionTrackPresence(0, track.id, flags[index]));
    const before = service.exportProjectJson();
    const section = service.project().arrangement[0];
    const otherSections = service.project().arrangement.slice(1);
    service.toggleSectionRolePresence(sectionId, 'bass');
    const updated = service.project().arrangement[0];
    for (const track of service.project().tracks) {
      assert.equal(updated.trackPresence[track.id], track.role === 'bass' ? !flags.some(Boolean) : section.trackPresence[track.id]);
    }
    assert.deepEqual(service.project().arrangement.slice(1), otherSections);
    const after = service.exportProjectJson();
    service.undo(); assert.equal(service.exportProjectJson(), before);
    service.redo(); assert.equal(service.exportProjectJson(), after);
  }
  const unchanged = service.project();
  service.toggleSectionRolePresence('missing-section', 'bass');
  assert.strictEqual(service.project(), unchanged);
});

test('harmony headers compare effective chords and durations and reuse transposed chordlet labels', () => {
  const { service } = fixture();
  const { sectionHarmonyDisplay, trackHasSectionHarmony } = require('./music-loader.cjs').loadMusic('harmony-display.ts');
  const project = { ...service.project(), key: 'C', scale: 'major', transposeSemitones: 2, progression: [1, 4], progressionChords: undefined };
  const section = { ...project.arrangement[0], measures: 2 };
  const inherited = sectionHarmonyDisplay(project, section);
  assert.deepEqual(inherited.chords.map(chord => chord.label), ['D', 'G']);
  assert.deepEqual(inherited.chords.map(chord => chord.hue), [0, 150]);
  assert.equal(inherited.chordsDiffer, false); assert.equal(inherited.durationsDiffer, false);
  const explicit = sectionHarmonyDisplay(project, { ...section, chordDegrees: [1, 4, 1, 4], customDurations: [4, 4, 4, 4] });
  assert.equal(explicit.chordsDiffer, false); assert.equal(explicit.durationsDiffer, false);
  const local = { ...section, chordDegrees: [2, 5], customDurations: [2, 6] };
  const display = sectionHarmonyDisplay(project, local);
  assert.deepEqual(display.chords.map(chord => chord.label), ['Em', 'A']);
  assert.deepEqual(display.durations, [2, 6]);
  assert.equal(display.chordsDiffer, true); assert.equal(display.durationsDiffer, true);
  const bass = project.tracks.find(track => track.role === 'bass'), drums = project.tracks.find(track => track.role === 'drums');
  assert.equal(trackHasSectionHarmony(project, bass, section), false);
  assert.equal(trackHasSectionHarmony(project, bass, local), true);
  assert.equal(trackHasSectionHarmony(project, drums, { ...section, chordDegrees: [2, 5] }), false);
  assert.equal(trackHasSectionHarmony(project, drums, local), true);
  assert.equal(trackHasSectionHarmony(project, bass, { ...section, context: { key: 'C', scale: 'major', tempoBpm: project.tempoBpm } }), false);
  assert.equal(trackHasSectionHarmony(project, bass, { ...section, context: { key: 'D' } }), true);
});

test('global generated notes mark differing section harmony without claiming section track settings', () => {
  const { service, phrase } = fixture();
  const project = service.project(), track = project.tracks.find(track => track.role === 'bass');
  const section = { ...project.arrangement[0], measures: 1, chordDegrees: [2], customDurations: [2], trackPresence: { [track.id]: true } };
  assert.equal(phrase.trackPhraseSource(track, section, project), 'global-section-harmony');
  assert.equal(phrase.trackPhraseSource(track, { ...section, trackPartOverrides: { [track.id]: { transpose: 12 } } }, project), 'section-generated');
  assert.equal(phrase.trackPhraseSource({ ...track, editedPhrase: [] }, section, project), 'global-edited');
  assert.equal(phrase.trackPhraseSource(track, { ...section, trackPhrases: { [track.id]: [] } }, project), 'section-edited');
  const notes = phrase.layOutTrackPhrase({ ...project, arrangement: [section] }, track, true);
  assert.ok(notes.length > 0); assert.ok(notes.every(note => note.phraseSource === 'global-section-harmony'));
});

test('score phrase sources follow local edits, forced generation and inherited global edits without changing layout output', () => {
  const { service, phrase } = fixture();
  const project = service.project(), track = project.tracks.find(track => track.role === 'bass');
  const note = { id: 'source-note', midi: 48, startBeat: 0, durationBeats: 1, velocity: 90 };
  const edited = { ...track, editedPhrase: [note] };
  const section = { ...project.arrangement[0], measures: 1, trackPresence: { [track.id]: true } };
  assert.equal(phrase.trackPhraseSource(track, section), 'global-generated');
  assert.equal(phrase.trackPhraseSource(edited, section), 'global-edited');
  assert.equal(phrase.trackPhraseSource(edited, { ...section, generatedTrackIds: [track.id] }), 'global-generated');
  assert.equal(phrase.trackPhraseSource(track, { ...section, trackPartOverrides: { [track.id]: { transpose: 12 } } }), 'section-generated');
  assert.equal(phrase.trackPhraseSource(track, { ...section, rolePartOverrides: { bass: { transpose: 12 } } }), 'section-generated');
  assert.equal(phrase.trackPhraseSource(edited, { ...section, generatedTrackIds: [track.id],
    trackPartOverrides: { [track.id]: { transpose: 12 } } }), 'section-generated');
  for (const context of [
    { context: { key: 'D', swingPercent: 60 } }, { chordDegrees: [1, 4] },
    { customChords: [null] }, { customDurations: [2] }, { useMelodyHarmony: true }, { swapMelody: true },
    { trackPartOverrides: { [track.id]: {} }, rolePartOverrides: { bass: {} } },
    { trackPartOverrides: { 'another-track': { transpose: 12 } }, rolePartOverrides: { melody: { transpose: 12 } } },
  ]) {
    assert.equal(phrase.trackPhraseSource(track, { ...section, ...context }), 'global-generated', JSON.stringify(context));
    assert.equal(phrase.trackPhraseSource(edited, { ...section, ...context }), 'global-edited', JSON.stringify(context));
  }
  assert.equal(phrase.trackPhraseSource(edited, { ...section, trackPhrases: { [track.id]: [note] }, generatedTrackIds: [track.id] }), 'section-edited');
  const p = { ...project, arrangement: [section, { ...section, id: 'local', trackPhrases: { [track.id]: [note] } }] };
  const labeled = phrase.layOutTrackPhrase(p, edited, true);
  assert.deepEqual(labeled.map(note => note.phraseSource), ['global-edited', 'section-edited']);
  assert.deepEqual(labeled.map(({ phraseSource, ...note }) => note), phrase.layOutTrackPhrase(p, edited));
});

test('arpeggio register matches all production Java pitches in the saved bug configuration and exported MIDI', () => {
  const { service, phrase, midi } = fixture();
  const reference = JSON.parse(fs.readFileSync(path.join(__dirname,'fixtures/arpeggio-saved-config.java.json'),'utf8'));
  assert.equal(service.importProjectJson(JSON.stringify(reference.project)),true);
  const project = service.project(), exported = new Midi(midi.generateCompositionMidi(project));
  let compared = 0;
  for (const track of project.tracks.filter(track => track.role === 'arpeggio')) {
    const expected = reference.expectedArpeggioPitches[track.id];
    assert.deepEqual(phrase.layOutTrackPhrase(project,track).map(note => note.midi),expected,track.id);
    assert.deepEqual(exported.tracks.find(item => item.name === track.name).notes.map(note => note.midi),expected,`${track.id} MIDI`);
    compared += expected.length;
  }
  assert.equal(compared,54);
});

test('contextual melodies match production Java pitches in the saved bug configuration and exported MIDI', () => {
  const { service, phrase, midi } = fixture();
  const reference = JSON.parse(fs.readFileSync(path.join(__dirname,'fixtures/arpeggio-saved-config.java.json'),'utf8'));
  assert.equal(service.importProjectJson(JSON.stringify(reference.project)),true);
  const project = service.project(), exported = new Midi(midi.generateCompositionMidi(project));
  for (const track of project.tracks.filter(track => track.role === 'melody')) {
    const expected = reference.expectedMelodyPitches[track.id];
    assert.deepEqual(phrase.layOutTrackPhrase(project,track).map(note => note.midi),expected,track.id);
    assert.deepEqual(exported.tracks.find(item => item.name === track.name)?.notes.map(note => note.midi) ?? [],expected,`${track.id} MIDI`);
  }
});

test('canonical and realized arpeggio harmony use the Java role register before track transpose', () => {
  const { load } = fixture();
  const { generateArpeggio } = load('core/music/arpeggio-generator.ts');
  const settings = { pattern: 'up', rate: 'eighth', octaves: 1, rhythm: 'full', hitsPerPattern: 4,
    exceptionChance: 0, pauseChance: 0, swingPercent: 50, useOctaveAdjustments: false };
  const harmony = { roots: [[60,64,67]], voicings: [[60,64,67]] };
  const generated = generateArpeggio(42n,'C','major',[1],settings,undefined,undefined,harmony);
  assert.deepEqual(generated.map(note => note.midi),[36,40,43,36]);
  assert.deepEqual(generateArpeggio(42n,'C','major',[1],settings,undefined,undefined,
    { ...harmony, soundingVoicings: [[60,64,67]] }),generated);
  const contour = generateArpeggio(42n,'C','major',[1],{ ...settings, arpContour: [0] },undefined,undefined,harmony);
  assert.deepEqual(contour.map(note => note.midi),[60,40,43,36],'contour attacks receive the same -24 Java role transpose');
});

test('saved Java order offsets reach arpeggio generation and survive project round trips', () => {
  const { service, load } = fixture();
  const { generateTrackPhrase } = load('core/music/phrase.ts');
  const project = service.project(), source = project.tracks.find(track => track.role === 'arpeggio');
  const settings = { ...source.generatorSettings, pattern: 'random', patternSeed: 12345, rhythm: 'full', hitsPerPattern: 8, pauseChance: 0, exceptionChance: 0 };
  const third = { ...source, orderOffset: 3, generatorSettings: settings };
  const shifted = { ...source, orderOffset: 1, generatorSettings: { ...settings, patternSeed: 12347 } };
  assert.deepEqual(generateTrackPhrase(project,third),generateTrackPhrase(project,shifted));
  assert.notDeepEqual(generateTrackPhrase(project,third),generateTrackPhrase(project,{ ...third, orderOffset: 1 }));
  const nativeThird = { ...source, id: 'track-arpeggio-3', generatorSettings: settings };
  const multi = { ...project, tracks: [...project.tracks, { ...source, id: 'track-arpeggio-2' }, nativeThird] };
  assert.deepEqual(generateTrackPhrase(multi,nativeThird),generateTrackPhrase(multi,{ ...nativeThird, orderOffset: 3 }));
  const saved = JSON.parse(service.exportProjectJson());
  saved.tracks.find(track => track.role === 'arpeggio').orderOffset = 3;
  assert.equal(service.importProjectJson(JSON.stringify(saved)),true);
  assert.equal(service.project().tracks.find(track => track.role === 'arpeggio').orderOffset,3);
  assert.equal(JSON.parse(service.exportProjectJson()).tracks.find(track => track.role === 'arpeggio').orderOffset,3);
  for (const orderOffset of [1.5,2147483648,'3']) {
    saved.tracks.find(track => track.role === 'arpeggio').orderOffset = orderOffset;
    assert.equal(service.importProjectJson(JSON.stringify(saved)),false);
  }
});

test('random arpeggios repeat Java\'s eight-index vocabulary before shuffling long melody grids', () => {
  const { load } = fixture();
  const { arpeggioPattern } = load('core/music/arpeggio-pattern.ts');
  const { JavaRandom } = load('core/music/java-random.ts');
  // Captured from production ArpPhraseGenerator.generateArpMap with the debugger:
  // patternSeed -2129510774, part offset 30000, order offset 3, random stream +1.
  const pattern = arpeggioPattern('random',64,3,1,0,undefined,new JavaRandom(-2129480770));
  assert.deepEqual(pattern.slice(0,10),[7,7,2,7,0,1,2,5,3,6]);
  for (let index = 0; index < 8; index++) assert.equal(pattern.filter(value => value === index).length,8);
});

test('contextual Melody settings persist, undo, and filter controls by algorithm', () => {
  const { service, load, uiPreferences } = fixture();
  const { DEFAULT_MELODY_GLOBAL } = load('core/music/melody-global-settings.ts');
  const { DEFAULT_HARMONY_SETTINGS } = load('core/music/harmony-voicing.ts');
  assert.equal(DEFAULT_MELODY_GLOBAL.contextualMelody, false);
  assert.equal(DEFAULT_HARMONY_SETTINGS.generation, 'CURRENT');
  const { PartSettingsEditorComponent } = load('shared/part-settings-editor.component.ts');
  const editor = new PartSettingsEditorComponent();
  editor.role.set('melody'); editor.values.set({});
  assert.ok(editor.controls().some(c => c.key === 'patternFlexible'));
  const before = service.exportProjectJson();
  assert.equal(service.updateMelodyGlobal({ contextualMelody: true, rangeMin: 62, rangeMax: 81, tension: 50,
    ending: 'CLOSED', style: 'JAZZ', form: 'QUESTION_ANSWER', phraseBars: 3, beatsPerBar: 3, chromaticApproaches: true }), 'changed');
  assert.ok(!editor.controls().some(c => c.key === 'patternFlexible'));
  assert.ok(editor.controls().some(c => c.key === 'speed'));
  uiPreferences.beginQuickSettings();
  assert.ok(editor.controls().some(c => c.key === 'patternFlexible'));
  uiPreferences.saveQuickSettings();
  const saved = service.exportProjectJson();
  service.undo(); assert.equal(service.exportProjectJson(), before);
  assert.equal(service.importProjectJson(saved), true);
  assert.deepEqual(projectData(service.exportProjectJson()), projectData(saved));
  assert.equal(service.updateMelodyGlobal({ rangeMin: 90 }), 'invalid');
  assert.equal(service.updateHarmonySettings({ generation: 'RANDOM' }), 'changed');
  const harmonySaved = service.exportProjectJson();
  assert.equal(service.importProjectJson(harmonySaved), true);
  assert.equal(service.project().harmonySettings.generation, 'RANDOM');
});

test('harmony strategies are seeded, distinct, and Random chooses a concrete strategy', () => {
  const { load, service } = fixture();
  const { generateHarmony, HARMONY_GENERATIONS } = load('core/music/contextual-harmony.ts');
  const { generateDiatonicProgression } = load('core/music/progression-generator.ts');
  const seed = 9223372036854775807n;
  assert.deepEqual(generateHarmony(seed, 4, 'C', 'major').degrees, generateDiatonicProgression(seed,4));
  const results = HARMONY_GENERATIONS.slice(0,5).map(({ value }) => generateHarmony(seed,8,'C','natural-minor',{ generation: value }));
  assert.ok(new Set(results.map(r => r.degrees.join(','))).size >= 4);
  for (const { value } of HARMONY_GENERATIONS) {
    const result = generateHarmony(seed,8,'C','natural-minor',{ generation: value });
    assert.deepEqual(result,generateHarmony(seed,8,'C','natural-minor',{ generation: value }));
    assert.equal(result.degrees.length,8);
    if (value === 'RANDOM') assert.ok(results.some(r => JSON.stringify(r) === JSON.stringify(result)));
  }
  const fifths = generateHarmony(123n,7,'C','major',{ generation: 'FIFTHS' }).degrees;
  assert.ok(fifths.slice(1).every((d,i) => d === (fifths[i] - 1 + 3) % 7 + 1));
  const fixed = { root: 1, spice: 'm', inversion: 1 };
  const locked = generateHarmony(123n,4,'C','major',{ generation: 'FUNCTIONAL' },{},[fixed,null,null,fixed]);
  assert.deepEqual(locked.chords[0],fixed); assert.deepEqual(locked.chords[3],fixed);
  service.updateHarmonySettings({ generation: 'FUNCTIONAL' });
  const before = service.exportProjectJson(); service.generateProgression();
  assert.ok(service.project().progressionChords); service.undo(); assert.equal(service.exportProjectJson(),before);
});

test('contextual range randomizes only on Compose and preserves disabled targets', () => {
  const { service, load } = fixture();
  const { contextualRange } = load('core/music/contextual-randomizer.ts');
  service.updateSettings({ seed: '9223372036854775807' });
  service.updateMelodyGlobal({ contextualMelody: true, rangeRandomizeOnCompose: true });
  service.updateTrackGenerationPolicy('melody', { targetsOnCompose: false });
  const targets = service.project().tracks.find(t => t.role === 'melody').generatorSettings.chordNoteChoices;
  service.generateComposition('compose');
  const range = contextualRange(9223372036854775807n);
  assert.equal(service.project().melodyGlobal.rangeMin,range.rangeMin);
  assert.equal(service.project().melodyGlobal.rangeMax,range.rangeMax);
  assert.deepEqual(service.project().tracks.find(t => t.role === 'melody').generatorSettings.chordNoteChoices,targets);
  service.generateComposition('regenerate');
  assert.equal(service.project().melodyGlobal.rangeMin,range.rangeMin);
  assert.equal(service.project().melodyGlobal.rangeMax,range.rangeMax);
});

test('contextual melody respects sounding harmony, range, ending, and ignores obsolete repair settings', () => {
  const { load } = fixture();
  const { generateMelody } = load('core/music/melody-generator.ts');
  const { chordPitches, progressionChords } = load('core/music/harmony.ts');
  const settings = { algorithm: 'block', speed: 30, maxBlockChange: 7, blockJump: 1 };
  const globals = { contextualMelody: true, rangeMin: 60, rangeMax: 84, tension: 0, ending: 'CLOSED' };
  for (const key of ['C','D']) for (const scale of ['major','natural-minor','dorian']) {
    const progression = [1,4,5,1], chords = progressionChords(key,scale,progression);
    const notes = generateMelody(123n,key,scale,progression,settings,0,undefined,globals);
    assert.ok(notes.length > 0);
    assert.deepEqual(notes,generateMelody(123n,key,scale,progression,settings,0,undefined,globals));
    assert.equal(notes.at(-1).midi % 12, key === 'C' ? 0 : 2);
    notes.forEach((n,i) => {
      assert.ok(n.midi >= 60 + (key === 'C' ? 0 : 2) && n.midi <= 84 + (key === 'C' ? 0 : 2));
      assert.ok(n.durationBeats > 0 && n.startBeat + n.durationBeats <= 16);
      const classes = chordPitches(chords[Math.floor(n.startBeat/4)],0).map(p => p % 12);
      assert.ok(classes.includes(n.midi % 12));
      if (i) assert.ok(Math.abs(n.midi - notes[i-1].midi) <= 10);
    });
    const ignored = { ...globals, tonicNoteTarget: 100, chordNoteTarget: 100, modeNoteTarget: 100, replaceAvoidNotes: 2, arpySurprises: true };
    assert.deepEqual(notes,generateMelody(123n,key,scale,progression,{ ...settings, doubledRhythmChance: 100, noteExceptionChance: 100, maxNoteExceptions: 4, fillPauses: true },0,undefined,ignored));
  }
});

test('zero seed keeps automatic input, saves the generated seed and loads it as explicit input', () => {
  const { service, load } = fixture();
  service.updateSettings({ seed: '0' });
  assert.equal(service.seedInput(), '0');
  assert.equal(service.generateComposition('compose'), 'changed');
  const first = service.project().seed;
  assert.notEqual(first, '0');
  assert.equal(service.seedInput(), '0');
  service.generateComposition('regenerate');
  assert.equal(service.project().seed, first);
  service.generateComposition('compose');
  const generated = service.project().seed;
  assert.notEqual(generated, first);
  const exported = service.exportProjectJson();
  const { ProjectService } = load('core/project/project.service.ts');
  assert.equal(new ProjectService().seedInput(), generated);
  assert.equal(service.importProjectJson(exported), true);
  assert.equal(service.seedInput(), generated);
  service.updateSettings({ seed: '9223372036854775807' });
  service.generateComposition('compose');
  assert.equal(service.project().seed, '9223372036854775807');
});

test('Compose stars default on, preserve disabled global values and round-trip independently', () => {
  const { service, load, workspace } = fixture();
  for (const role of ['melody', 'bass', 'chords', 'arpeggio', 'drums']) assert.equal(workspace.policyFor(role).onCompose, true);
  service.updateSettings({ seed: '123', tempoBpm: 200, transposeSemitones: 24 });
  const before = service.exportProjectJson();
  service.generateComposition('compose');
  assert.ok(service.project().tempoBpm >= 60 && service.project().tempoBpm <= 100);
  assert.ok(service.project().transposeSemitones >= -6 && service.project().transposeSemitones <= 5);
  const after = service.exportProjectJson();
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.generateComposition('compose'); assert.equal(service.exportProjectJson(), after);
  service.updateSettings({ tempoBpm: 200, transposeSemitones: 24, randomizeTempoOnCompose: false, randomizeTransposeOnCompose: false });
  service.generateComposition('compose');
  assert.equal(service.project().tempoBpm, 200);
  assert.equal(service.project().transposeSemitones, 24);
  const saved = service.exportProjectJson();
  assert.equal(service.importProjectJson(saved), true);
  assert.equal(service.project().randomizeTempoOnCompose, false);
  assert.equal(service.project().randomizeTransposeOnCompose, false);
  service.updateSettings({ randomizeTempoOnCompose: true });
  service.generateComposition('compose');
  assert.notEqual(service.project().tempoBpm, 200);
  assert.equal(service.project().transposeSemitones, 24);
  for (const field of ['randomizeTempoOnCompose', 'randomizeTransposeOnCompose']) {
    assert.equal(service.importProjectJson(JSON.stringify({ ...JSON.parse(saved), [field]: 'false' })), false);
  }
  const { UiPreferencesService } = load('core/ui/ui-preferences.service.ts');
  const preferences = new UiPreferencesService();
  assert.equal(preferences.restartPlaybackOnCompose(), true);
  preferences.toggleRestartPlaybackOnCompose();
  assert.equal(new UiPreferencesService().restartPlaybackOnCompose(), false);
  assert.equal(preferences.importJson(preferences.exportJson()), true);
  assert.equal(preferences.restartPlaybackOnCompose(), false);
});

test('instrument dropdown pool filtering follows policy and preserves the current instrument without editing music', () => {
  const { service, load, workspace } = fixture();
  const { INSTRUMENT_POOLS } = load('core/music/instrument-generation.ts');
  const { UiPreferencesService } = load('core/ui/ui-preferences.service.ts');
  const preferences = workspace.preferences;
  assert.equal(preferences.limitInstrumentsToPool(), false);
  const track = service.project().tracks.find(track => track.role === 'bass');
  service.updateTrack(track.id, { mix: { program: 127 } });
  assert.equal(workspace.trackInstrumentOptions()[track.id].length, 128);
  const before = service.exportProjectJson();
  preferences.toggleLimitInstrumentsToPool();
  assert.equal(service.exportProjectJson(), before);
  assert.equal(new UiPreferencesService().limitInstrumentsToPool(), true);
  for (const pool of ['ALL', 'MELODY', 'BASS', 'CHORD', 'PLUCK', 'LONG_PAD']) {
    service.updateInstrumentPolicy('bass', { pool });
    const options = workspace.trackInstrumentOptions()[track.id].map(instrument => instrument.program);
    assert.deepEqual(options, [...new Set([...INSTRUMENT_POOLS[pool], 127])].sort((a, b) => a - b));
    assert.equal(service.project().tracks.find(item => item.id === track.id).mix.program, 127);
  }
  const chords = service.project().tracks.find(track => track.role === 'chords');
  service.updateInstrumentPolicy('chords', { pool: 'AUTO' });
  assert.deepEqual(workspace.trackInstrumentOptions()[chords.id].map(instrument => instrument.program),
    [...new Set([...INSTRUMENT_POOLS.CHORD, ...INSTRUMENT_POOLS.PLUCK, chords.mix.program])].sort((a, b) => a - b));
  const json = preferences.exportJson();
  preferences.toggleLimitInstrumentsToPool();
  assert.equal(workspace.trackInstrumentOptions()[track.id].length, 128);
  assert.equal(preferences.importJson(json), true);
  assert.equal(preferences.limitInstrumentsToPool(), true);
  assert.equal(preferences.importJson(JSON.stringify({ ...JSON.parse(json), limitInstrumentsToPool: 'true' })), false);
  const missing = JSON.parse(json);
  delete missing.limitInstrumentsToPool;
  assert.equal(preferences.importJson(JSON.stringify(missing)), true);
  assert.equal(preferences.limitInstrumentsToPool(), false);
});

test('Java XML translation passes the real project decoder, round-trips and produces identical MIDI after reload', () => {
  const {service,load}=fixture(), {parseXml}=require('./translate-java-config.cjs');
  const source=fs.readFileSync(path.join(__dirname,'../GENERATOR_REFACTOR_TEST_GUICONFIG.xml-config'),'utf8');
  const result=load('core/project/java-config-translator.ts').translateJavaConfig(source,{parseXml,name:'Java XML parity'});
  assert.equal(service.importProjectJson(JSON.stringify(result.project)),true);
  const exported=service.exportProjectJson(), before=load('core/music/midi-export.ts').generateCompositionMidi(service.project());
  assert.equal(service.project().tracks.length,18);
  assert.equal(service.project().arrangement.length,5);
  assert.equal(service.importProjectJson(exported),true);
  assert.deepEqual(load('core/music/midi-export.ts').generateCompositionMidi(service.project()),before);
  assert.equal(service.exportProjectJson(),exported);
});

test('project BPM validation accepts 30 and 33, persists them and rejects values below 30', () => {
  const {service} = fixture();
  for (const tempoBpm of [30, 33]) {
    const project = JSON.parse(service.exportProjectJson());
    project.tempoBpm = tempoBpm;
    assert.equal(service.importProjectJson(JSON.stringify(project)), true);
    assert.equal(service.project().tempoBpm, tempoBpm);
    assert.equal(service.importProjectJson(service.exportProjectJson()), true);
    assert.equal(service.project().tempoBpm, tempoBpm);
  }
  const invalid = JSON.parse(service.exportProjectJson());
  invalid.tempoBpm = 29;
  assert.equal(service.importProjectJson(JSON.stringify(invalid)), false);
  assert.equal(service.project().tempoBpm, 33);
});

test('Java zero-origin modes retain exact degrees, target notes and modal chord pitches', () => {
  const { load } = fixture(), scales = load('core/music/scales.ts'), harmony = load('core/music/harmony.ts');
  const java = fs.readFileSync(path.join(__dirname, '../../../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/MidiUtils.java'), 'utf8');
  const definitions = Object.fromEntries([...java.matchAll(/(\w+)_SCALE\s*=\s*\{([^}]+)\}/g)]
    .map(match => [match[1], match[2].split(',').map(Number)]));
  const names = { major:'MAJOR',dorian:'DORIAN',phrygian:'PHRYGIAN',lydian:'LYDIAN',mixolydian:'MIXOLYDIAN',
    'natural-minor':'AEOLIAN',locrian:'LOCRIAN',blues:'BLUES','harmonic-minor':'HARMONIC_MINOR',turkish:'TURKISH',
    indian:'INDIAN','harmonic-major':'HARMONIC_MAJOR',whiskey:'WHISKEY','double-harmonic':'DOUBLE_HARM_MINOR',
    'pentatonic-major':'PENTA_MAJOR','pentatonic-minor':'PENTA_MINOR','hungarian-minor':'HUNGARIAN_MINOR' };
  assert.equal(scales.SCALE_MODES.length,17);
  for (const [mode, name] of Object.entries(names)) {
    assert.deepEqual(scales.scaleNotes(mode), definitions[name], mode);
    const target = Number(java.match(new RegExp('\\w+\\(Scales\\.'+name+'_SCALE, (-?\\d+)\\)'))[1]);
    assert.equal(scales.modeTargetPitch(mode),target < 0 ? -1 : definitions.MAJOR[target],mode);
    for (let degree=0;degree<7;degree++) {
      const expected=[0,2,4].map(step=>60+definitions[name][(degree+step)%7]+12*Math.floor((degree+step)/7));
      const chord=harmony.progressionChords('C',mode,[degree+1])[0];
      assert.deepEqual(harmony.chordPitches(chord,60),expected.map(n=>n-12*Math.floor((expected[0]-60)/12)),mode+' chord '+degree);
    }
  }
  assert.deepEqual(harmony.getDiatonicChords('C','mixolydian').map(chord=>chord.symbol),['C','Dm','E°','F','Gm','Am','B♭']);
  for(const excluded of ['bborian','ebolian','abrygian','dbocrian','gbfs','MIXOLYDIAN','unknown','__proto__']) assert.equal(scales.isScaleMode(excluded),false);
});

test('All supported modes generate every role, export MIDI and round-trip section overrides and exact unusual chords', () => {
  const {service,load}=fixture(), scales=load('core/music/scales.ts'), phrase=load('core/music/phrase.ts');
  const harmony=load('core/music/harmony.ts'), midi=load('core/music/midi-export.ts');
  for(const scale of scales.SCALE_MODES) {
    service.updateSettings({scale});
    const id=service.project().arrangement[0].id;
    assert.notEqual(service.updateSectionContext(id,{scale}),'invalid');
    const project=service.project();
    for(const track of project.tracks) {
      const notes=phrase.generateTrackPhrase(project,track);
      assert.ok(notes.length,scale+' '+track.role);
      assert.ok(notes.every(note=>Number.isInteger(note.midi)&&note.midi>=0&&note.midi<=127&&Number.isFinite(note.startBeat)),scale);
    }
    const bytes=midi.generateCompositionMidi(project);
    assert.ok(bytes.length>0);
    const json=service.exportProjectJson();
    assert.equal(service.importProjectJson(json),true,scale);
    assert.equal(service.project().scale,scale);
    assert.equal(service.project().arrangement[0].context.scale,scale);
    assert.deepEqual(midi.generateCompositionMidi(service.project()),bytes,scale);
  }
  const unusual=harmony.progressionChords('C','indian',[1])[0];
  assert.deepEqual(unusual.intervals,[0,1,5]);
  const saved=JSON.parse(service.exportProjectJson()); saved.progressionChords=saved.progression.map(()=>unusual);
  assert.equal(service.importProjectJson(JSON.stringify(saved)),true);
  assert.deepEqual(service.project().progressionChords[0],unusual);
  saved.scale='bborian'; assert.equal(service.importProjectJson(JSON.stringify(saved)),false);
});

test('P6 extended Arpeggio directions match 1536 extracted production Java branches and following draws', () => {
  const { load } = fixture(), core = load('core/music/track-generation.ts');
  const { JavaRandom } = load('core/music/java-random.ts');
  const cases = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/extended-creation.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  const names = { RANDOM:'random',UP:'up',DOWN:'down',UPDOWN:'up-down',DOWNUP:'down-up',FROG_U:'frog-up',FROG_D:'frog-down' };
  let random;
  assert.equal(cases.length,1536);
  for (const sample of cases) {
    if (sample.index === 0) random = new JavaRandom(BigInt(sample.seed));
    const result = core.chooseArpeggioDirection(random,sample.voices,2);
    assert.deepEqual(result,{pattern:names[sample.direction],arpPatternRotate:sample.rotation});
    assert.equal(random.nextLong().toString(),sample.next);
  }
});

test('P6 drum policies choose Ghosts, melody dependencies and overrandomized phrase-time pauses safely', () => {
  const { service, load } = fixture(), core = load('core/music/track-generation.ts');
  const source = service.project().tracks.find(track=>track.role==='drums');
  const track = pitch => ({...source,generatorSettings:{...source.generatorSettings,pitch}});
  const reroll = (seed,pitch,patch,totalTracks=1) => core.rerollRoleSettings(seed,'drums',
    {...core.DEFAULT_TRACK_POLICIES.drums,...patch},[track(pitch)],[1],{totalTracks})[0];
  for (const pitch of [42,60]) {
    const dynamic = reroll(42n,pitch,{dynamicChance:100,melodyChance:0});
    assert.equal(dynamic.ghosts,true);
    assert.equal(dynamic.pauseChance,0);
    const following = reroll(42n,pitch,{dynamicChance:100,melodyChance:100});
    assert.equal(following.rhythm,'melody'); assert.equal(following.ghosts,false);
    assert.ok(following.pauseChance>=20&&following.pauseChance<=40);
  }
  for (const pitch of [35,36,38,40,53]) assert.equal(reroll(42n,pitch,{dynamicChance:100,melodyChance:100}).ghosts,false);
  let denseGhosts=0, normalGhosts=0;
  for(let seed=0n;seed<100n;seed++) {
    normalGhosts += Number(reroll(seed,42,{dynamicChance:100,melodyChance:0}).ghosts);
    denseGhosts += Number(reroll(seed,42,{dynamicChance:100,melodyChance:0},12).ghosts);
    for(const multiplier of ['1/2','3/4','1','3/2','2']) for(const pitch of [36,42,60]) {
      const next=reroll(seed,pitch,{overrandomizeChance:100,melodyChance:100,dynamicChance:100,hitsMultiplier:multiplier});
      assert.ok(next.hitsPerPattern>=1&&next.hitsPerPattern<=32);
      assert.ok(next.velocityMin>=1&&next.velocityMax>=next.velocityMin);
      assert.ok(next.feedbackCount>=0&&next.feedbackCount<=2);
      assert.equal(next.ghosts,true);
      if(pitch!==36) assert.equal(next.rhythm,'melody');
      assert.ok(next.pauseChance>=0&&next.pauseChance<80);
    }
  }
  assert.equal(normalGhosts,100); assert.ok(denseGhosts>0&&denseGhosts<normalGhosts);
  const original={...track(42).generatorSettings,rhythm:'custom',pauseChance:71,ghosts:true,customPattern:Array(32).fill(1)};
  const [retained]=core.rerollRoleSettings(42n,'drums',{...core.DEFAULT_TRACK_POLICIES.drums,patterns:false,
    overrandomizeChance:100,dynamicChance:0,melodyChance:100},[{...source,generatorSettings:original}],[1]);
  for(const key of ['rhythm','pauseChance','ghosts','customPattern']) assert.deepEqual(retained[key],original[key]);
});

test('P6 drum rule-only edits, rerolls, Compose and additions preserve ownership, atomic history and MIDI replay', () => {
  const {service,load,phrase,midi}=fixture();
  const id='track-drums-1', sectionId=service.project().arrangement[0].id;
  service.updateSettings({seed:'9007199254740993'});
  service.updateTrackGeneratorSettings(id,{pitch:42}); service.setTrackInstrumentLock(id,true);
  service.updatePartSettings({kind:'section-track',sectionId,trackId:id},{pauseChance:12});
  assert.equal(service.project().arrangement[0].trackPartOverrides[id].pauseChance,12);
  service.updateTrackPhrase(id,[{id:'manual',midi:42,startBeat:0,durationBeats:.25,velocity:90}]);
  assert.equal(service.importProjectJson(service.exportProjectJson()),true);
  const track=service.project().tracks.find(track=>track.id===id), notes=phrase.layOutTrackPhrase(service.project(),track);
  const patch=JSON.stringify(service.project().arrangement);
  assert.equal(service.updateTrackGenerationPolicy('drums',{overrandomizeChance:100,melodyChance:100,dynamicChance:100,onCompose:true}),'changed');
  assert.strictEqual(service.project().tracks.find(track=>track.id===id),track);
  assert.deepEqual(phrase.layOutTrackPhrase(service.project(),track),notes);
  const before=service.exportProjectJson();
  assert.equal(service.rerollRoleTracks('drums'),'changed');
  const after=service.exportProjectJson(), bytes=midi.generateCompositionMidi(service.project());
  assert.equal(JSON.stringify(service.project().arrangement),patch);
  const next=service.project().tracks.find(track=>track.id===id);
  assert.equal(next.generatorSettings.pitch,42); assert.deepEqual(next.editedPhrase,track.editedPhrase);
  service.undo(); assert.equal(service.exportProjectJson(),before); service.redo(); assert.equal(service.exportProjectJson(),after);
  service.importProjectJson(before); service.rerollRoleTracks('drums'); assert.deepEqual(JSON.parse(service.exportProjectJson()),JSON.parse(after));
  assert.deepEqual(midi.generateCompositionMidi(service.project()),bytes);
  assert.deepEqual(new (load('core/project/project.service.ts').ProjectService)().project(),service.project());
  service.setTrackRerollLock(id,true); const locked=service.project().tracks.find(track=>track.id===id);
  service.generateComposition('compose'); assert.deepEqual(service.project().tracks.find(track=>track.id===id),locked);
  const prior=service.exportProjectJson(); service.setRoleTrackCount('drums',4);
  assert.equal(service.project().tracks.filter(track=>track.role==='drums').length,4);
  assert.deepEqual(service.project().tracks.find(track=>track.id===id),locked);
  service.undo(); assert.equal(service.exportProjectJson(),prior);
  for(const invalid of [{dynamicChance:-1},{melodyChance:101},{overrandomizeChance:1.5},{dynamicChance:'50'}])
    assert.equal(service.updateTrackGenerationPolicy('drums',invalid),'invalid');
});

test('P6 Arpeggio later weighting handles 64 tracks, retains shared counts and produces baked grids and melody rhythms', () => {
  const {service,load}=fixture(), core=load('core/music/track-generation.ts');
  const source=service.project().arpeggio; let custom=0,melody=0;
  for(let seed=0n;seed<64n;seed++) {
    const settings=core.rerollArpeggioSettings(seed,core.DEFAULT_ARPEGGIO_POLICY,Array(64).fill(source));
    assert.equal(new Set(settings.map(next=>next.hitsPerPattern)).size,1);
    assert.equal(settings.at(-1).chordSpanFill,'HALF2'); // Saturated weight 99.
    for(const next of settings) {
      assert.ok(next.arpPatternRotate>=0&&next.arpPatternRotate<=3);
      if(next.rhythm==='custom') { custom++;assert.equal(next.customPattern.length,32); }
      if(next.rhythm==='melody') melody++;
      assert.equal(next.pauseChance,0);
    }
  }
  assert.ok(custom>0&&melody>0);
  const melodic=service.project().tracks.find(track=>track.role==='melody');
  const [next]=core.rerollRoleSettings(42n,'melody',core.DEFAULT_TRACK_POLICIES.melody,[melodic],[1]);
  const {JavaRandom}=load('core/music/java-random.ts'), random=new JavaRandom(42);
  random.nextInt(25);random.nextInt(5);
  assert.equal(next.noteExceptionChance,10+random.nextInt(15));
  assert.equal(next.maxNoteExceptions,random.nextInt(2));assert.equal(next.leadChordsChance,random.nextInt(50));
});

test('P5f durations share chord boundaries across all roles, score ranges, tempo events and persisted history', () => {
  const { service, load, phrase, midi, ui, workspace, stored } = fixture();
  const timing = load('core/music/section-context.ts');
  const project = JSON.parse(service.exportProjectJson());
  project.tracks = project.tracks.map(track => track.role === 'arpeggio' || track.role === 'drums'
    ? { ...track,generatorSettings:{...track.generatorSettings,rhythm:'melody'} } : track);
  project.arrangement = project.arrangement.slice(0,2).map(section => ({ ...section, measures: 4,
    trackPresence: Object.fromEntries(project.tracks.map(track => [track.id, true])) }));
  assert.equal(service.importProjectJson(JSON.stringify(project)), true);
  const id = service.project().arrangement[0].id;
  const before = service.exportProjectJson();
  const baseline = Object.fromEntries(service.project().tracks.map(track => [track.id, phrase.layOutTrackPhrase(service.project(), track)]));
  assert.equal(service.setSectionMusicalState(id, { customDurations: [2,6,1,3], context: { tempoBpm: 90, beatDurationMultiplier: 2 } }), 'changed');
  const after = service.exportProjectJson();
  service.undo(); assert.equal(service.exportProjectJson(), before); service.redo(); assert.equal(service.exportProjectJson(), after);
  const current = service.project(), section = current.arrangement[0];
  assert.equal(timing.sectionDurationBeats(current, section), 24);
  assert.equal(workspace.totalBeats(), 40);
  ui.selectSection(id); assert.deepEqual(ui.sectionRange(), { startBeat: 0, endBeat: 24 });
  assert.equal(workspace.timelineSections()[1].startBeat, 24);
  for (const track of current.tracks) {
    const notes = phrase.layOutTrackPhrase(current, track);
    assert.deepEqual(notes.filter(note => note.startBeat < 24), timing.mapChordNotes(baseline[track.id].filter(note => note.startBeat < 16), [4,12,2,6]), track.role);
    assert.deepEqual(notes.filter(note => note.startBeat >= 24), baseline[track.id].filter(note => note.startBeat >= 16).map(note => ({ ...note, startBeat: note.startBeat + 8 })), track.role + ' following section');
  }
  const exported = new Midi(midi.generateCompositionMidi(current));
  assert.deepEqual(exported.header.tempos.map(event => [event.ticks / exported.header.ppq,Math.round(event.bpm)]), [[0,90],[24,current.tempoBpm]]);
  const map = timing.projectTempoMap(current);
  for (const track of current.tracks) {
    const notes = phrase.layOutTrackPhrase(current, track), output = exported.tracks.find(candidate => candidate.name === track.name).notes;
    for (const note of notes) {
      const matching = output.find(event => event.midi === note.midi && Math.abs(event.ticks / exported.header.ppq - note.startBeat) < 1 / exported.header.ppq);
      assert.ok(matching, track.role + ' MIDI onset');
      assert.ok(Math.abs(matching.time - timing.beatToSeconds(map,note.startBeat)) < 0.004);
    }
  }
  assert.equal(service.importProjectJson(after), true); assert.deepEqual(JSON.parse(service.exportProjectJson()), JSON.parse(after));
  const { ProjectService } = load('core/project/project.service.ts');
  const restored = new ProjectService(); assert.deepEqual(restored.project(),service.project());
  assert.equal(JSON.parse(stored.get('vibecomposer.project.v12')).arrangement[0].context.tempoBpm, 90);
});

test('P5f section Chordlets, timing and key edits preserve scopes, manual ownership and exact transposition', () => {
  const { service, load, phrase } = fixture();
  const project = JSON.parse(service.exportProjectJson());
  project.arrangement = project.arrangement.slice(0,2).map(section => ({ ...section, measures: 4,
    trackPresence: Object.fromEntries(project.tracks.map(track => [track.id,true])) }));
  assert.equal(service.importProjectJson(JSON.stringify(project)),true);
  const id = service.project().arrangement[0].id;
  const notes = role => phrase.layOutTrackPhrase(service.project(),service.project().tracks.find(track => track.role === role)).filter(note => note.startBeat < 16);
  const baseline = Object.fromEntries(['melody','bass','chords','arpeggio','drums'].map(role => [role,notes(role)]));
  assert.equal(service.updateSectionContext(id, { keyChange: -12 }), 'changed');
  for (const role of ['melody','bass','chords','arpeggio']) assert.deepEqual(notes(role),baseline[role].map(note => ({ ...note, midi: note.midi - 12 })),role);
  assert.deepEqual(notes('drums'),baseline.drums);
  const bases = JSON.stringify(service.project().tracks);
  service.updateSectionContext(id, { keyChange: null, swingPercent: 70, key: 'D', scale: 'natural-minor' });
  for (const role of ['melody','bass','chords','arpeggio']) assert.notDeepEqual(notes(role),baseline[role],role);
  assert.equal(JSON.stringify(service.project().tracks),bases);
  service.setSectionChord(id, 0, { root: 6, spice: 'maj7', inversion: 1 });
  const { sectionGenerationHarmony } = load('core/music/project-harmony.ts');
  const harmony = sectionGenerationHarmony(service.project(),service.project().arrangement[0],'chords');
  assert.equal(harmony.customChords[0].root,6);
  service.setSectionMusicalState(id,{ customDurations: [1,2,3,4] });
  service.editSectionChordList(id,'move',0,2);
  assert.deepEqual(service.project().arrangement[0].customDurations,[2,3,1,4]);
  assert.equal(service.project().arrangement[0].customChords[2].root,6);
  const state=service.exportProjectJson(); service.undo(); service.redo(); assert.equal(service.exportProjectJson(),state);
  const trackId='track-melody-1'; service.updateTrackPhrase(trackId,[{ id:'manual',midi:65,startBeat:0,durationBeats:1,velocity:90 }]);
  const manual=JSON.stringify(service.project().tracks.find(track=>track.id===trackId).editedPhrase);
  service.updateSectionContext(id,{ keyChange: 7 });
  assert.equal(JSON.stringify(service.project().tracks.find(track=>track.id===trackId).editedPhrase),manual);
  assert.equal(notes('melody')[0].midi,65);
});

test('P5f validates context and harmony atomically, restores inheritance, and isolates duplicated arrays', () => {
  const { service, load } = fixture(); const id=service.project().arrangement[0].id;
  for(const patch of [{ tempoBpm:0 },{ swingPercent:101 },{ keyChange:25 },{ scale:'unknown' },{ key:'H' },{ beatDurationMultiplier:3 },{ unknown:null }])
    assert.equal(service.updateSectionContext(id,patch),'invalid');
  service.setSectionMusicalState(id,{ customDurations:[2,6],context:{ tempoBpm:80,swingPercent:0,keyChange:0 } });
  const saved=service.exportProjectJson(); service.undo(); const before=service.exportProjectJson();
  assert.equal(service.updateSectionContext(id,{tempoBpm:null}), 'unchanged');
  assert.equal(service.canRedo(),true);
  assert.equal(service.setSectionMusicalState(id,{ customDurations:[NaN] }),'invalid'); assert.equal(service.exportProjectJson(),before);
  assert.equal(service.canRedo(),true); service.redo(); assert.equal(service.exportProjectJson(),saved);
  assert.equal(service.updateSectionContext(id,{tempoBpm:80}), 'unchanged');
  assert.equal(service.setSectionMusicalState(id,{customDurations:[2,6],context:{swingPercent:0,keyChange:0,tempoBpm:80}}),'unchanged');
  for(const field of [{ customDurations:[0] },{ customDurations:[1,2],customChords:[{root:0,spice:'',inversion:0}] },{ context:{tempoBpm:Infinity} },{context:{unknown:1}}]) {
    const project=JSON.parse(saved); Object.assign(project.arrangement[0],field);
    assert.equal(service.importProjectJson(JSON.stringify(project)),false); assert.equal(service.exportProjectJson(),saved);
  }
  service.duplicateSection(0); const [first,copy]=service.project().arrangement;
  assert.notEqual(first.customDurations,copy.customDurations); assert.notEqual(first.context,copy.context);
  service.updateSection(0,{measures:8}); assert.deepEqual(service.project().arrangement[0].customDurations,[2,6]);
  service.updateSectionContext(id,{tempoBpm:null,swingPercent:null,keyChange:null}); assert.equal(service.project().arrangement[0].context,undefined);
  const { baseGenerationHarmony } = load('core/music/project-harmony.ts');
  service.updateSectionContext(id,{keyChangeType:'two-five-one'});
  // Two slots cannot support an ending cadence; deterministic recovery retains harmony.
  assert.deepEqual(baseGenerationHarmony(service.project(),service.project().arrangement[0]).degrees,[1,5]);
  service.setSectionMusicalState(id,{customDurations:undefined});
  const cadence=baseGenerationHarmony(service.project(),service.project().arrangement[0]);
  assert.deepEqual(cadence.customChords.slice(-2),[{root:2,spice:'m',inversion:0},{root:7,spice:'7',inversion:0}]);
  service.setSectionChord(id,0,{root:6,spice:'maj7',inversion:0});
  const explicit=baseGenerationHarmony(service.project(),service.project().arrangement[0]);
  assert.equal(explicit.customChords[0].root,6);
  assert.deepEqual(explicit.customChords.slice(-2),cadence.customChords.slice(-2),'editing a chord freezes the displayed cadence');
});

test('P5e inference weighting and voicing flattening match production Java consumers', () => {
  const { load } = fixture();
  const { inferMelodyChordDegrees, INFERENCE_CHORD_NAMES } = load('core/music/melody-chord-inference.ts');
  const { flattenProgression } = load('core/music/harmony-voicing.ts');
  const data = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/harmony-inference.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(data.weighting.length, 256); assert.equal(data.voicing.length, 192);
  for (const entry of data.weighting) assert.deepEqual(inferMelodyChordDegrees(entry.skeleton, entry.durations).map(degree => INFERENCE_CHORD_NAMES[degree - 1]), entry.names);
  for (const entry of data.voicing) {
    const before = JSON.stringify(entry);
    assert.deepEqual(flattenProgression(entry.chords, entry.roots, BigInt(entry.seed), entry.settings, entry.skipped), entry.output);
    assert.equal(JSON.stringify(entry), before, 'root and original voicing arrays remain independent');
  }
});

test('P5e base skeleton, alternate roots/voicings and complete swapped Melody phrases match Java', () => {
  const { load } = fixture();
  const { generateMelody } = load('core/music/melody-generator.ts');
  const { inferMelodyChordDegrees, INFERENCE_CHORD_NAMES } = load('core/music/melody-chord-inference.ts');
  const { baseGenerationHarmony, projectMelodyHarmony } = load('core/music/project-harmony.ts');
  const { DEFAULT_PROJECT } = load('core/project/project.model.ts');
  const data = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/harmony-inference.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(data.phrases.length, 96);
  for (const entry of data.phrases) {
    const settings = { algorithm: 'block', speed: [-100,0,50,100][entry.sample % 4], chordNoteChoices: [0,2,2,4], melodyPatternOffsets: [1,2,1,3],
      patternFlexible: true, maxBlockChange: 7, blockJump: 1, doubledRhythmChance: entry.sample % 2 === 0 ? 0 : 40,
      maxNoteExceptions: 2, noteExceptionChance: 30, transpose: [0,5,7,-12][entry.sample % 4], splitChance: 0, leadChordsChance: 0, startNoteChance: 100,
      pauseChance: 0, fillPauses: false, velocityMin: 80, velocityMax: 105, swingPercent: 50 };
    const globalSettings = { patternEffect: entry.sample % 3, newBlocksChance: entry.sample % 2 === 0 ? 25 : 100 };
    const project = { ...DEFAULT_PROJECT, key: 'C', scale: 'major', seed: entry.seed, progression: [1,5,6,4], progressionChords: undefined,
      melodyGlobal: globalSettings, tracks: [{ ...DEFAULT_PROJECT.tracks.find(track => track.role === 'melody'), generatorSettings: settings }] };
    const base = baseGenerationHarmony(project);
    let skeleton;
    generateMelody(BigInt(entry.seed), 'C', 'major', project.progression, settings, 0, undefined, globalSettings, undefined, base, notes => { skeleton = notes; });
    assert.deepEqual(skeleton, entry.skeleton, 'skeleton ' + entry.seed + '/' + entry.sample);
    const names = inferMelodyChordDegrees(skeleton).map(degree => INFERENCE_CHORD_NAMES[degree - 1]);
    names[0] = 'C'; names[3] = 'F'; assert.equal(names.join(','), entry.alternate);
    const inferred = projectMelodyHarmony(project);
    assert.equal(inferred.available, true);
    assert.deepEqual(inferred.harmony.roots, entry.roots);
    assert.deepEqual(inferred.harmony.voicings, entry.voicings);
    [0,100].forEach((offset,index) => {
      const notes = generateMelody(BigInt(entry.seed), 'C', 'major', inferred.harmony.degrees, settings, offset, undefined,
        globalSettings, undefined, inferred.harmony);
      assert.equal(notes.length, entry.notes[index].length);
      notes.forEach((note,i) => {
        const expected = entry.notes[index][i];
        assert.equal(note.midi, expected[0]); assert.ok(Math.abs(note.startBeat - expected[1]) < 1e-8);
        assert.ok(Math.abs(note.durationBeats - expected[2]) < 1e-8, `${entry.seed}/${entry.sample}/${offset}/${i}: duration ${note.durationBeats} vs ${expected[2]}`); assert.equal(note.velocity, expected[3]);
      });
    });
  }
});

test('P5e section selections share harmony across roles, preserve sparse overrides and replay history/JSON/session/MIDI', () => {
  const { service, load, phrase, midi, workspace } = fixture();
  const { projectMelodyHarmony, sectionGenerationHarmony } = load('core/music/project-harmony.ts');
  service.updateSettings({ seed: '9007199254740993' }); service.setProgression([1,5,6,4]);
  const original = JSON.parse(service.exportProjectJson());
  original.arrangement = original.arrangement.slice(0,2).map(section => ({ ...section, measures: 4,
    trackPresence: Object.fromEntries(original.tracks.map(track => [track.id,true])) }));
  assert.equal(service.importProjectJson(JSON.stringify(original)), true);
  const sectionId = service.project().arrangement[0].id;
  const chordId = 'track-chords-1', melodyId = 'track-melody-1';
  service.updatePartSettings({ kind: 'section-role', sectionId, role: 'chords' }, { transpose: 12 });
  service.updatePartSettings({ kind: 'section-track', sectionId, trackId: melodyId }, { speed: 100 });
  service.updatePartSettings({ kind: 'global-track', trackId: melodyId }, { chordNoteChoices: [0,2,2,4], patternSeed: 42 });
  const inferred = projectMelodyHarmony(service.project());
  assert.equal(inferred.available, true); assert.equal(inferred.sourceTrackId, melodyId);
  assert.notDeepEqual(inferred.harmony.degrees, service.project().progression);
  const patchState = JSON.stringify(service.project().arrangement.map(section => [section.rolePartOverrides, section.trackPartOverrides]));
  const notes = role => phrase.layOutTrackPhrase(service.project(), service.project().tracks.find(track => track.role === role));
  const before = service.exportProjectJson(), beforeNotes = Object.fromEntries(['melody','bass','chords','arpeggio'].map(role => [role,notes(role)]));
  assert.equal(service.updateSectionHarmony(sectionId, { useMelodyHarmony: true }), 'changed');
  const selected = service.exportProjectJson(); service.undo(); assert.equal(service.exportProjectJson(),before);
  service.redo(); assert.equal(service.exportProjectJson(),selected);
  assert.equal(patchState, JSON.stringify(service.project().arrangement.map(section => [section.rolePartOverrides, section.trackPartOverrides])));
  for (const role of ['bass','chords','arpeggio']) {
    assert.notDeepEqual(notes(role).filter(note => note.startBeat < 16), beforeNotes[role].filter(note => note.startBeat < 16));
    assert.deepEqual(notes(role).filter(note => note.startBeat >= 16), beforeNotes[role].filter(note => note.startBeat >= 16));
    assert.deepEqual(sectionGenerationHarmony(service.project(),service.project().arrangement[0],role).roots, inferred.harmony.roots);
  }
  const allHarmonyNotes = notes('melody');
  assert.equal(service.updateSectionHarmony(sectionId, { useMelodyHarmony: false, swapMelody: true }), 'changed');
  assert.notDeepEqual(notes('melody'), allHarmonyNotes);
  for (const role of ['bass','chords','arpeggio']) assert.deepEqual(notes(role),beforeNotes[role]);
  assert.equal(sectionGenerationHarmony(service.project(),service.project().arrangement[0],'melody').melodySeedOffset,100);
  service.updateSectionHarmony(sectionId, { useMelodyHarmony: true });
  const { AudioPlaybackService } = load('core/audio/audio-playback.service.ts');
  const playback = new AudioPlaybackService();
  const schedule = playback.createSchedule(service.project());
  const score = workspace.scoreNotes();
  for (const role of ['melody','bass','chords','arpeggio','drums']) {
    const expected = notes(role), exported = new Midi(midi.generateCompositionMidi(service.project()));
    const source = service.project().tracks.find(track => track.role === role);
    const strip = note => [note.midi,note.startBeat,note.durationBeats,note.velocity];
    const compare = values => values.map(strip).sort((a,b) => a[1]-b[1] || a[0]-b[0]);
    assert.deepEqual(compare(schedule.filter(note => note.trackId === source.id)),compare(expected));
    assert.deepEqual(compare(score.filter(note => note.part === source.id)),compare(expected));
    const track = exported.tracks.find(track => track.name === source.name);
    assert.equal(track.notes.length,expected.length);
    expected.forEach((note,index) => {
      assert.equal(track.notes[index].midi,note.midi);
      assert.ok(Math.abs(track.notes[index].ticks/exported.header.ppq - note.startBeat) < 0.003);
      assert.ok(Math.abs(track.notes[index].durationTicks/exported.header.ppq - note.durationBeats) < 0.005);
    });
  }
  service.updateHarmonySettings({ flattenVoicingChance: 35, flattenLargeChords: true, progressiveVoicing: true });
  const saved = service.exportProjectJson(), expected = notes('melody');
  assert.equal(service.importProjectJson(saved),true); assert.deepEqual(notes('melody'),expected);
  const restored = new (load('core/project/project.service.ts').ProjectService)();
  assert.deepEqual(JSON.parse(restored.exportProjectJson()),JSON.parse(service.exportProjectJson()));
  assert.equal(restored.project().seed,'9007199254740993');
  assert.deepEqual(phrase.layOutTrackPhrase(restored.project(),restored.project().tracks.find(track => track.id === melodyId)),expected);
});

test('P5e inference keeps base source ownership and explicit section harmony ahead of alternate selections', () => {
  const { service, load, phrase } = fixture();
  const { projectMelodyHarmony, sectionGenerationHarmony, baseGenerationHarmony } = load('core/music/project-harmony.ts');
  service.setProgression([1,5,6,4]); const sectionId = service.project().arrangement[0].id, id = 'track-melody-1';
  const track = () => service.project().tracks.find(track => track.id === id);
  const before = projectMelodyHarmony(service.project()).harmony;
  service.updatePartSettings({ kind: 'section-track', sectionId, trackId: id }, { speed: 100, chordNoteChoices: [12,7,0,5] });
  service.setSectionTrackPresence(0,id,false);
  assert.deepEqual(projectMelodyHarmony(service.project()).harmony,before, 'local patches and absent source do not re-infer base harmony');
  service.updateMelodyGlobal({ tonicNoteTarget: 100, chordNoteTarget: 100, replaceAvoidNotes: 2, patternFlip: true });
  assert.deepEqual(projectMelodyHarmony(service.project()).harmony,before, 'later repairs/flip do not change skeleton inference');
  const manual = [{ id:'saved',midi:61,startBeat:0,durationBeats:3,velocity:95 }];
  service.updateTrackPhrase(id,manual);
  assert.equal(projectMelodyHarmony(service.project()).manualSource,true);
  assert.deepEqual(projectMelodyHarmony(service.project()).harmony,before);
  service.updateSectionHarmony(sectionId,{ useMelodyHarmony:true,swapMelody:true });
  service.setSectionTrackPresence(0,id,true);
  assert.deepEqual(phrase.phraseForTrack(service.project(),track()),manual);
  service.updateTrackPhrase(id,[]); assert.deepEqual(phrase.layOutTrackPhrase(service.project(),track()),[]);
  assert.deepEqual(projectMelodyHarmony(service.project()).harmony,before);
  service.setSectionChordDegree(0,1,2);
  const section = service.project().arrangement[0];
  assert.ok(section.chordDegrees);
  for (const role of ['melody','bass','chords','arpeggio','drums']) assert.deepEqual(sectionGenerationHarmony(service.project(),section,role),baseGenerationHarmony(service.project(),section));
  service.clearSectionChordOverrides(0); service.clearTrackPhrase(id);
  assert.equal(projectMelodyHarmony(service.project()).available,true);
  const secondId = service.addTrack('melody');
  assert.equal(projectMelodyHarmony(service.project()).sourceTrackId,id, 'first source never silently redirects');
  service.removeTrack(id);
  assert.equal(projectMelodyHarmony(service.project()).sourceTrackId,secondId);
  service.removeTrack(secondId); assert.equal(projectMelodyHarmony(service.project()).available,false);
  for (const progression of [[1],[1,5]]) {
    service.addTrack('melody'); service.setProgression(progression);
    assert.deepEqual(projectMelodyHarmony(service.project()).harmony.degrees,progression);
    assert.equal(projectMelodyHarmony(service.project()).harmony.roots.length,progression.length);
  }
});

test('matching explicit section chords share primary harmony checks in both editors and generation', () => {
  const { service, load } = fixture();
  const { sectionHasChordOverride } = load('core/music/section-context.ts');
  const { progressionChords } = load('core/music/harmony.ts');
  const { sectionGenerationHarmony } = load('core/music/project-harmony.ts');
  const harmonyEditor = new (load('shared/harmony-settings.component.ts').HarmonySettingsComponent)();
  const contextEditor = new (load('shared/section-context-editor.component.ts').SectionContextEditorComponent)();
  service.setProgression([1,5,6,4]);
  const id = service.project().arrangement[0].id;
  const section = () => service.project().arrangement[0];
  const check = expected => {
    harmonyEditor.section.set(section()); contextEditor.section.set(section());
    assert.equal(sectionHasChordOverride(service.project(), section()), expected);
    assert.equal(harmonyEditor.hasChordOverride(), expected);
    assert.equal(contextEditor.hasChordOverride(), expected);
  };
  check(false);
  service.setSectionMusicalState(id, { chordDegrees: [1,5,6,4] }); check(false);
  const project = service.project(), chords = progressionChords(project.key, project.scale, project.progression);
  service.setSectionMusicalState(id, { customChords: chords }); check(false);
  service.updateSectionHarmony(id, { useMelodyHarmony: true, swapMelody: true });
  assert.equal(sectionGenerationHarmony(service.project(), section(), 'melody').inferred, true);
  assert.equal(sectionGenerationHarmony(service.project(), section(), 'melody').melodySeedOffset, 100);
  assert.equal(sectionGenerationHarmony(service.project(), section(), 'bass').inferred, true);
  const saved = service.exportProjectJson();
  for (const changed of [{ ...chords[0], inversion: 1 }, { ...chords[0], spice: '7' }, { ...chords[0], root: (chords[0].root + 1) % 12 }]) {
    service.setSectionChord(id, 0, changed); check(true);
    assert.equal(sectionGenerationHarmony(service.project(), section(), 'melody').inferred, false);
    service.setSectionChord(id, 0, chords[0]); check(false);
  }
  assert.equal(service.importProjectJson(saved), true); check(false);
  assert.deepEqual(section().customChords, chords, 'matching explicit choices remain stored');
  service.setProgression([1,2,6,4]); check(true);
  service.undo(); check(false);
  service.setSectionMusicalState(id, { context: { keyChangeType: 'two-five-one' } });
  assert.equal(sectionGenerationHarmony(service.project(), section(), 'melody').inferred, false);
});

test('section chord comparison resolves custom primary chords, modes and duration-only repetition', () => {
  const { service, load } = fixture();
  const { sectionHasChordOverride } = load('core/music/section-context.ts');
  const { progressionChords } = load('core/music/harmony.ts');
  const project = { ...service.project(), key: 'D', scale: 'dorian', progression: [1,4,5] };
  const section = { ...project.arrangement[0], chordDegrees: [1,4,5], customChords: undefined };
  assert.equal(sectionHasChordOverride(project, section), false);
  const contextual = { ...section, context: { key: 'F', scale: 'natural-minor' } };
  assert.equal(sectionHasChordOverride(project, contextual), false);
  const customChords = progressionChords('F', 'natural-minor', project.progression);
  assert.equal(sectionHasChordOverride(project, { ...contextual, customChords }), false);
  assert.equal(sectionHasChordOverride(project, { ...contextual, customChords, customDurations: [2,4,8] }), false);
  assert.equal(sectionHasChordOverride(project, { ...section, customDurations: [2,4,8,2,4,8] }), false);
  assert.equal(sectionHasChordOverride(project, { ...section, chordDegrees: [1,4] }), true);
  const primary = { ...project, progressionChords: [{ root: 2, spice: '7', inversion: 1 }, null, null] };
  assert.equal(sectionHasChordOverride(primary, section), true, 'explicit degrees replace custom primary chords');
  assert.equal(sectionHasChordOverride(primary, { ...section, customChords: primary.progressionChords }), false);
  assert.equal(sectionHasChordOverride(primary, { ...section, chordDegrees: undefined, customChords: primary.progressionChords }), false);
});

test('P5e harmony commands validate atomically without losing redo and flattening preserves root/manual ownership', () => {
  const { service, load, phrase } = fixture();
  const { baseGenerationHarmony } = load('core/music/project-harmony.ts');
  service.setProgression([1,5,6,4]);
  const id = service.project().arrangement[0].id;
  const track = role => service.project().tracks.find(track => track.role === role);
  service.updateHarmonySettings({ flattenVoicingChance: 0 });
  const bass = phrase.generateTrackPhrase(service.project(),track('bass'));
  const arp = phrase.generateTrackPhrase(service.project(),track('arpeggio'));
  const roots = baseGenerationHarmony(service.project()).roots;
  service.updateHarmonySettings({ flattenVoicingChance: 100 });
  assert.deepEqual(phrase.generateTrackPhrase(service.project(),track('bass')),bass);
  assert.notDeepEqual(phrase.generateTrackPhrase(service.project(),track('arpeggio')),arp);
  assert.deepEqual(baseGenerationHarmony(service.project()).roots,roots);
  const manual = [{ id:'saved',midi:61,startBeat:0,durationBeats:1,velocity:95 }];
  service.updateTrackPhrase(track('chords').id,manual); service.updateHarmonySettings({ progressiveVoicing:true });
  assert.deepEqual(phrase.phraseForTrack(service.project(),track('chords')),manual);
  service.undo(); const saved = service.exportProjectJson();
  for (const patch of [{ flattenVoicingChance:-1 },{ flattenVoicingChance:101 },{ flattenVoicingChance:1.5 },{ flattenVoicingChance:'50' },
    { flattenLargeChords:1 },{ progressiveVoicing:'true' },{ unknown:true },[],null]) {
    assert.equal(service.updateHarmonySettings(patch),'invalid');
    const imported = JSON.parse(saved); imported.harmonySettings=patch;
    assert.equal(service.importProjectJson(JSON.stringify(imported)),false);
  }
  for (const patch of [{ useMelodyHarmony:1 },{ swapMelody:'true' },{ unknown:false },null,[]]) {
    assert.equal(service.updateSectionHarmony(id,patch),'invalid');
  }
  assert.equal(service.updateSectionHarmony('deleted',{ swapMelody:true }),'invalid');
  for (const key of ['useMelodyHarmony','swapMelody']) {
    const imported = JSON.parse(saved); imported.arrangement[0][key]='true';
    assert.equal(service.importProjectJson(JSON.stringify(imported)),false);
  }
  assert.equal(service.exportProjectJson(),saved); assert.equal(service.canRedo(),true);
});

test('P5e sounding harmony preserves chromatic Chordlets across keys/modes while canonical voicing keeps independent roots', () => {
  const { service, load, phrase, midi } = fixture();
  const { chordPitches, progressionChords, getPitchClass } = load('core/music/harmony.ts');
  const { baseGenerationHarmony, projectMelodyHarmony } = load('core/music/project-harmony.ts');
  service.setProgression([1,5,6,4]);
  service.updateHarmonySettings({ flattenVoicingChance:0 });
  const id = service.project().arrangement[0].id;
  service.project().tracks.forEach(track => service.setSectionTrackPresence(0,track.id,true));
  for (const key of ['C','D','F♯','A♭']) for (const scale of ['major','natural-minor']) {
    service.updateSettings({ key,scale });
    const custom = { root:(getPitchClass(key)+4)%12,spice:'maj7',inversion:1 };
    service.setProgressionChord(0,custom);
    const harmony = baseGenerationHarmony(service.project());
    const definitions = progressionChords(key,scale,service.project().progression,service.project().progressionChords);
    assert.deepEqual(harmony.soundingRoots,definitions.map(chord => chordPitches({ ...chord,inversion:0 },60)));
    assert.deepEqual(harmony.soundingVoicings,definitions.map(chord => chordPitches(chord,60)));
    const roots = harmony.soundingRoots;
    service.updateHarmonySettings({ flattenVoicingChance:100 });
    assert.deepEqual(baseGenerationHarmony(service.project()).soundingRoots,roots);
    assert.deepEqual(baseGenerationHarmony(service.project()).soundingVoicings[0],chordPitches(custom,60));
    const inference = projectMelodyHarmony(service.project());
    assert.deepEqual(inference.harmony.soundingRoots[0],roots[0]);
    assert.deepEqual(inference.harmony.soundingRoots[3],roots[3]);
    service.updateSectionHarmony(id,{ useMelodyHarmony:true,swapMelody:true });
    const exported = new Midi(midi.generateCompositionMidi(service.project()));
    for (const track of service.project().tracks) {
      const expected = phrase.layOutTrackPhrase(service.project(),track);
      const actual = exported.tracks.find(item => item.name===track.name).notes;
      assert.equal(expected.length,actual.length);
      expected.forEach((note,index) => assert.equal(actual[index].midi,note.midi));
    }
    service.updateHarmonySettings({ flattenVoicingChance:0 });
  }
});

test('P5 melody dependency grids match production onset buckets with rests and boundary offsets', () => {
  const { load } = fixture();
  const { melodyContext } = load('core/music/melody-context.ts');
  const cases = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/melody-context.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(cases.length, 64);
  for (const entry of cases) assert.deepEqual(melodyContext(entry.notes, entry.chordCount).pitches, entry.pitches);
});

test('P5e Melody pattern flip matches 64 production Java onset maps and retains empty sources', () => {
  const { load } = fixture(), { melodyContext } = load('core/music/melody-context.ts');
  const cases = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/melody-flip.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(cases.length, 64);
  for (const entry of cases) assert.deepEqual(melodyContext(entry.notes, entry.chordCount, true).pitches, entry.pitches);
  assert.deepEqual(melodyContext([], 1, true).pitches, [Array(16).fill(0)]);
  assert.deepEqual(melodyContext([{ midi: -1, startBeat: 0 }], 1, true).pitches, [Array(16).fill(1)]);
});

test('P5e Melody flip and key emphasis preserve ownership, update consumers and replay exported MIDI', () => {
  const { service, phrase, midi, load } = fixture();
  const id = 'track-melody-1', sectionId = service.project().arrangement[0].id;
  const track = role => service.project().tracks.find(item => item.role === role);
  service.setSectionTrackPresence(0, id, true);
  for (const role of ['drums', 'arpeggio']) {
    service.setSectionTrackPresence(0, track(role).id, true);
    service.updatePartSettings({ kind: 'global-track', trackId: track(role).id }, { rhythm: 'melody', swingPercent: 50 });
  }
  service.updatePartSettings({ kind: 'section-track', sectionId, trackId: id }, { speed: 50 });
  const notes = phrase.layOutTrackPhrase(service.project(), track('melody'));
  const context = phrase.sectionMelodyContext(service.project(), service.project().arrangement[0]);
  const drums = phrase.layOutTrackPhrase(service.project(), track('drums')), arp = phrase.layOutTrackPhrase(service.project(), track('arpeggio'));
  const before = service.exportProjectJson(), sections = service.project().arrangement;
  assert.equal(service.updateMelodyGlobal({ patternFlip: true }), 'changed');
  assert.deepEqual(phrase.layOutTrackPhrase(service.project(), track('melody')), notes);
  assert.deepEqual(phrase.sectionMelodyContext(service.project(), service.project().arrangement[0]).pitches,
    context.pitches.map(grid => grid.map(pitch => pitch > 0 ? 0 : 1)));
  assert.notDeepEqual(phrase.layOutTrackPhrase(service.project(), track('drums')), drums);
  assert.notDeepEqual(phrase.layOutTrackPhrase(service.project(), track('arpeggio')), arp);
  assert.deepEqual(service.project().arrangement, sections);
  const flipped = service.exportProjectJson();
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.redo(); assert.equal(service.exportProjectJson(), flipped);
  assert.equal(service.updateMelodyGlobal({ emphasizeKey: true }), 'changed');
  assert.notDeepEqual(phrase.layOutTrackPhrase(service.project(), track('melody')), notes);
  for (const role of ['melody', 'drums', 'arpeggio']) {
    const expected = phrase.layOutTrackPhrase(service.project(), track(role));
    const parsed = new Midi(midi.generateCompositionMidi(service.project()));
    const exported = parsed.tracks.find(item => item.name === track(role).name).notes;
    assert.equal(exported.length, expected.length);
    exported.forEach((note, index) => {
      assert.equal(note.midi, expected[index].midi);
      assert.ok(Math.abs(note.ticks / parsed.header.ppq - expected[index].startBeat) < 0.003);
    });
  }
  const saved = service.exportProjectJson(); service.updateSettings({ name: 'redo' }); service.undo();
  for (const key of ['patternFlip', 'emphasizeKey']) {
    assert.equal(service.updateMelodyGlobal({ [key]: 'true' }), 'invalid');
    const imported = JSON.parse(saved); imported.melodyGlobal[key] = 1;
    assert.equal(service.importProjectJson(JSON.stringify(imported)), false);
    for (const scope of [{ kind: 'global-role', role: 'melody' }, { kind: 'global-track', trackId: id },
      { kind: 'section-role', sectionId, role: 'melody' }, { kind: 'section-track', sectionId, trackId: id }]) {
      service.updatePartSettings(scope, { [key]: key === 'patternFlip' ? 'true' : true }); assert.equal(service.exportProjectJson(), saved);
    }
    assert.equal(service.canRedo(), true);
  }
  const { ProjectService } = load('core/project/project.service.ts');
  assert.deepEqual(projectData(new ProjectService().exportProjectJson()), projectData(saved));
  assert.equal(service.importProjectJson(saved), true);
  const manual = [{ id: 'flip-manual', midi: 62, startBeat: 1, durationBeats: 2, velocity: 90 }];
  service.updateTrackPhrase(id, manual);
  service.updateMelodyGlobal({ emphasizeKey: false });
  assert.deepEqual(phrase.phraseForTrack(service.project(), track('melody')), manual);
  assert.deepEqual(phrase.sectionMelodyContext(service.project()).pitches[0], Array.from({ length: 16 }, (_, slot) => slot === 4 ? 0 : 1));
  service.updateTrackPhrase(id, []);
  assert.deepEqual(phrase.sectionMelodyContext(service.project()).pitches.flat(), Array(service.project().progression.length * 16).fill(0));
  service.clearTrackPhrase(id);
  service.removeTrack(id);
  assert.equal(phrase.sectionMelodyContext(service.project()).available, false);
});

test('P5e tonal targets preserve global ownership, history, restore and MIDI', () => {
  const { service, phrase, midi, load } = fixture();
  const id = 'track-melody-1', sectionId = service.project().arrangement[0].id;
  const track = () => service.project().tracks.find(item => item.id === id);
  service.setSectionTrackPresence(0, id, true);
  service.updatePartSettings({ kind: 'section-track', sectionId, trackId: id }, { speed: 100 });
  const before = service.exportProjectJson(), patches = service.project().arrangement;
  const original = phrase.layOutTrackPhrase(service.project(), track());
  const originalContext = phrase.sectionMelodyContext(service.project(), service.project().arrangement[0]);
  assert.equal(service.updateMelodyGlobal({ tonicNoteTarget: 100 }), 'changed');
  assert.notDeepEqual(phrase.layOutTrackPhrase(service.project(), track()), original);
  service.undo(); assert.equal(service.exportProjectJson(), before);
  assert.equal(service.updateMelodyGlobal({ modeNoteTarget: 100 }), 'changed');
  assert.notDeepEqual(phrase.layOutTrackPhrase(service.project(), track()), original);
  assert.notDeepEqual(phrase.sectionMelodyContext(service.project(), service.project().arrangement[0]), originalContext);
  assert.equal(service.updateMelodyGlobal({ tonicNoteTarget: 70, modeNoteTarget: 60 }), 'changed');
  assert.deepEqual(service.project().arrangement, patches);
  let saved = service.exportProjectJson();
  const notes = phrase.layOutTrackPhrase(service.project(), track());
  const exported = new Midi(midi.generateCompositionMidi(service.project()));
  const output = exported.tracks.find(item => item.name === track().name).notes;
  assert.equal(output.length, notes.length);
  output.forEach((note, index) => {
    assert.equal(note.midi, notes[index].midi);
    assert.ok(Math.abs(note.ticks / exported.header.ppq - notes[index].startBeat) < 0.003);
    assert.ok(Math.abs(note.durationTicks / exported.header.ppq - notes[index].durationBeats) < 0.003);
  });
  service.undo(); service.redo(); assert.equal(service.exportProjectJson(), saved);
  const { ProjectService } = load('core/project/project.service.ts');
  assert.deepEqual(projectData(new ProjectService().exportProjectJson()), projectData(saved));
  assert.equal(service.importProjectJson(saved), true);
  saved = service.exportProjectJson();
  service.updateSettings({ name: 'redo' }); service.undo();
  for (const key of ['tonicNoteTarget', 'modeNoteTarget']) {
    assert.equal(service.updateMelodyGlobal({ [key]: service.project().melodyGlobal[key] }), 'unchanged');
    for (const value of [-1, 101, 1.5, '50', null]) {
      assert.equal(service.updateMelodyGlobal({ [key]: value }), 'invalid');
      const project = JSON.parse(saved); project.melodyGlobal[key] = value;
      assert.equal(service.importProjectJson(JSON.stringify(project)), false);
      assert.equal(service.exportProjectJson(), saved);
    }
    for (const scope of [{ kind: 'global-role', role: 'melody' }, { kind: 'global-track', trackId: id },
      { kind: 'section-role', sectionId, role: 'melody' }, { kind: 'section-track', sectionId, trackId: id }]) {
      service.updatePartSettings(scope, { [key]: 100 }); assert.equal(service.exportProjectJson(), saved);
    }
    assert.equal(service.canRedo(), true);
  }
  const manual = [{ id: 'tonal-manual', midi: 62, startBeat: 1, durationBeats: 2, velocity: 90 }];
  service.updateTrackPhrase(id, manual);
  service.updateMelodyGlobal({ tonicNoteTarget: 0, modeNoteTarget: 0 });
  assert.deepEqual(phrase.phraseForTrack(service.project(), track()), manual);
  service.clearTrackPhrase(id);
  assert.deepEqual(phrase.layOutTrackPhrase(service.project(), track()), original);
});

test('P5e tonal repairs protect chord starts, retain original counts and make best-effort corrections', () => {
  const { load } = fixture(), { applyMelodyTonalTargets } = load('core/music/melody-tonal-targets.ts');
  const run = (pitches, scale, tonic, mode, offset = 0) => {
    const notes = pitches.map(chord => chord.map(midi => ({ midi })));
    applyMelodyTonalTargets(notes, scale, tonic, mode, offset);
    return notes.map(chord => chord.map(note => note.midi));
  };
  assert.deepEqual(run([[62, 62, 71], [62, 62, 71]], 'major', 100, 0), [[62, 62, 72], [62, 62, 72]]);
  assert.deepEqual(run([[62, 62, 71]], 'major', 100, 0, 100), [[62, 62, 71]]);
  // Existing targets consume the deficit, so percentages are requests rather than guaranteed quotas.
  assert.deepEqual(run([[64, 60, 62, 64]], 'major', 0, 75), [[64, 60, 60, 64]]);
  // The original histogram still requires two repairs after tonic repair; the new tonic consumes one.
  assert.deepEqual(run([[64, 62, 71, 64]], 'major', 100, 50), [[64, 60, 72, 64]]);
  assert.deepEqual(run([[60, 71, 67, -1]], 'natural-minor', 0, 100), [[60, 69, 69, -1]]);
  assert.deepEqual(run([[], [60, -1]], 'major', 100, 100), [[], [60, -1]]);
});

test('P5e chord targeting and avoid notes preserve scoped harmony, history, manual ownership and MIDI', () => {
  const { service, phrase, midi, load } = fixture();
  const id = 'track-melody-1', sectionId = service.project().arrangement[0].id;
  const track = () => service.project().tracks.find(item => item.id === id);
  service.setSectionTrackPresence(0, id, true);
  const original = phrase.layOutTrackPhrase(service.project(), track());
  assert.equal(service.updateMelodyGlobal({ chordNoteTarget: 100 }), 'changed');
  assert.notDeepEqual(phrase.layOutTrackPhrase(service.project(), track()), original);
  service.undo();
  assert.equal(service.updateMelodyGlobal({ replaceAvoidNotes: 2 }), 'changed');
  assert.notDeepEqual(phrase.layOutTrackPhrase(service.project(), track()), original);
  service.updateMelodyGlobal({ chordNoteTarget: 100 });
  const diatonic = phrase.phraseForTrack(service.project(), track());
  service.setProgressionChord(0, { root: 1, spice: 'maj7', inversion: -2 });
  assert.notDeepEqual(phrase.phraseForTrack(service.project(), track()), diatonic);
  service.updateMelodyGlobal({ tonicNoteTarget: 40, modeNoteTarget: 60 });
  const globalContext = phrase.sectionMelodyContext(service.project());
  service.setSectionChordDegree(0, 0, 2);
  const localSection = service.project().arrangement[0];
  const localContext = phrase.sectionMelodyContext(service.project(), localSection);
  assert.notDeepEqual(localContext, globalContext);
  const { generateMelody } = load('core/music/melody-generator.ts');
  let expectedContext;
  generateMelody(BigInt(service.project().seed), service.project().key, service.project().scale,
    localSection.chordDegrees, track().generatorSettings, 0,
    notes => { expectedContext = load('core/music/melody-context.ts').melodyContext(notes, localSection.chordDegrees.length); },
    service.project().melodyGlobal);
  assert.deepEqual(localContext, expectedContext, 'section degree progression replaces global Chordlets for dependency generation');
  service.setProgressionChord(0, { root: 6, spice: 'dim', inversion: 3 });
  assert.deepEqual(phrase.sectionMelodyContext(service.project(), service.project().arrangement[0]), localContext);
  const patches = service.project().arrangement;
  service.updateMelodyGlobal({ chordNoteTarget: 70 });
  assert.deepEqual(service.project().arrangement, patches);
  let saved = service.exportProjectJson();
  const notes = phrase.layOutTrackPhrase(service.project(), track());
  const exported = new Midi(midi.generateCompositionMidi(service.project()));
  const output = exported.tracks.find(item => item.name === track().name).notes;
  assert.equal(output.length, notes.length);
  output.forEach((note, index) => {
    assert.equal(note.midi, notes[index].midi);
    assert.ok(Math.abs(note.ticks / exported.header.ppq - notes[index].startBeat) < 0.003);
    assert.ok(Math.abs(note.durationTicks / exported.header.ppq - notes[index].durationBeats) < 0.003);
  });
  service.undo(); service.redo(); assert.equal(service.exportProjectJson(), saved);
  assert.deepEqual(projectData(new (load('core/project/project.service.ts').ProjectService)().exportProjectJson()), projectData(saved));
  assert.equal(service.importProjectJson(saved), true); saved = service.exportProjectJson();
  service.updateSettings({ name: 'redo' }); service.undo();
  for (const [key, invalid] of [['chordNoteTarget', [-1, 101, 1.5, '50', null]], ['replaceAvoidNotes', [-1, 3, 0.5, '1', null]]]) {
    assert.equal(service.updateMelodyGlobal({ [key]: service.project().melodyGlobal[key] }), 'unchanged');
    for (const value of invalid) {
      assert.equal(service.updateMelodyGlobal({ [key]: value }), 'invalid');
      const project = JSON.parse(saved); project.melodyGlobal[key] = value;
      assert.equal(service.importProjectJson(JSON.stringify(project)), false);
      assert.equal(service.exportProjectJson(), saved);
    }
    for (const scope of [{ kind: 'global-role', role: 'melody' }, { kind: 'global-track', trackId: id },
      { kind: 'section-role', sectionId, role: 'melody' }, { kind: 'section-track', sectionId, trackId: id }]) {
      service.updatePartSettings(scope, { [key]: 1 }); assert.equal(service.exportProjectJson(), saved);
    }
    assert.equal(service.canRedo(), true);
  }
  const manual = [{ id: 'chord-manual', midi: 61, startBeat: 1, durationBeats: 2, velocity: 90 }];
  service.updateTrackPhrase(id, manual);
  service.updateMelodyGlobal({ chordNoteTarget: 0, replaceAvoidNotes: 0 });
  assert.deepEqual(phrase.phraseForTrack(service.project(), track()), manual);
  assert.deepEqual(phrase.sectionMelodyContext(service.project(), service.project().arrangement[0]).pitches,
    load('core/music/melody-context.ts').melodyContext(manual, localSection.chordDegrees.length).pitches);
});

test('P5e chord repairs retain original order, voicing ties, safe notes and the independent avoid stream', () => {
  const { load } = fixture();
  const { applyMelodyChordTargets, replaceMelodyAvoidNotes, melodyRepairChords } = load('core/music/melody-chord-targets.ts');
  const notes = pitches => pitches.map(chord => chord.map(midi => ({ midi })));
  const pitches = chords => chords.map(chord => chord.map(note => note.midi));
  const original = notes([[61, 62, 71], [65, 69, 71, 62, 65, -1]]);
  applyMelodyChordTargets(original, [[67, 60, 64]], 100);
  assert.deepEqual(pitches(original), [[60, 60, 67], [64, 67, 67, 60, 65, -1]], 'per-chord budget can leave the final deficit unmet');
  const ties = notes([[62, 62]]);
  applyMelodyChordTargets(ties, [[64, 60]], 100);
  assert.deepEqual(pitches(ties), [[64, 64]], 'nearest pitch is not circular; first voicing entry wins a tie');
  const safe = notes([[60, 61, 64, 67, -1, 65, 68]]);
  replaceMelodyAvoidNotes(safe, [[60, 64, 67]], 42, 1);
  assert.deepEqual(pitches(safe), [[60, 60, 64, 67, -1, 64, 60]]);
  const again = notes([[60, 61, 64, 67, -1, 65, 68]]);
  replaceMelodyAvoidNotes(again, [[60, 64, 67]], 42, 1);
  assert.deepEqual(pitches(again), pitches(safe));
  for (const key of ['A♭', 'F♯', 'C♯', 'E♭']) assert.deepEqual(
    melodyRepairChords(key, 'natural-minor', [1,2,3,4,5,6,7]).map(chord => chord.map(pitch => pitch % 12)),
    melodyRepairChords('C', 'major', [1,2,3,4,5,6,7]).map(chord => chord.map(pitch => pitch % 12)));
});

test('right-click knob resets use explicit defaults, cancel previews and respect disabled/no-op controls', () => {
  const { load, service } = fixture(), { CompactKnobComponent } = load('shared/compact-knob.component.ts');
  const knob = new CompactKnobComponent(); knob.minimum.set(0); knob.maximum.set(100); knob.defaultValue.set(25);
  knob.value.set(80); const commits = [], previews = [];
  knob.valueCommit.emit = value => { commits.push(value); service.updateMelodyGlobal({ newBlocksChance: value }); knob.value.set(value); };
  knob.valuePreview.emit = value => previews.push(value);
  let prevented = 0; const event = { preventDefault() { prevented++; }, stopPropagation() {} };
  knob.startDrag({ button: 0, pointerId: 1, clientY: 100, preventDefault() {}, stopPropagation() {}, currentTarget: { focus() {}, setPointerCapture() {} } });
  knob.moveDrag({ pointerId: 1, clientY: 90 }); assert.notEqual(knob.preview(), null);
  knob.resetDefault(event); assert.equal(knob.preview(), null); assert.deepEqual(commits, [25]);
  knob.finishDrag({ pointerId: 1 }); assert.deepEqual(commits, [25]);
  const saved = service.exportProjectJson(); service.updateSettings({ name: 'redo' }); service.undo();
  knob.resetDefault(event); assert.deepEqual(commits, [25]); assert.equal(service.canRedo(), true);
  knob.disabled.set(true); knob.value.set(90); knob.resetDefault(event); assert.deepEqual(commits, [25]);
  knob.disabled.set(false); knob.mixed.set(true); knob.value.set(25); knob.resetDefault(event); assert.deepEqual(commits, [25,25]);
  assert.equal(service.exportProjectJson(), saved); assert.equal(prevented, 4);
});

test('right-click combobox resets find enabled defaults and dispatch one ordinary change', () => {
  const { load } = fixture(), { WheelSelectDirective } = load('shared/wheel-select.directive.ts');
  const directive = new WheelSelectDirective(), previous = global.HTMLOptGroupElement;
  global.HTMLOptGroupElement = class {};
  try {
    const changes = [], select = { disabled: false, selectedIndex: 3, options: [
      { value: '', disabled: true }, { value: 'ASC', disabled: false }, { value: 'DESC', disabled: false }, { value: 'ANY', disabled: false },
    ], dispatchEvent(event) { changes.push(event.type); } };
    directive.element = { nativeElement: select }; directive.defaultValue.set('ASC');
    const event = { preventDefault() {}, stopPropagation() {} };
    directive.resetDefault(event); assert.equal(select.selectedIndex, 1); assert.deepEqual(changes, ['change']);
    directive.resetDefault(event); assert.deepEqual(changes, ['change']);
    select.disabled = true; select.selectedIndex = 2; directive.resetDefault(event); assert.equal(select.selectedIndex, 2);
    select.disabled = false; select.options[1].disabled = true; directive.resetDefault(event); assert.equal(select.selectedIndex, 2);
    directive.defaultValue.set('missing'); directive.resetDefault(event); assert.equal(select.selectedIndex, 2);
    directive.defaultValue.set(undefined); select.selectedIndex = 3; directive.resetDefault(event); assert.equal(select.selectedIndex, 2);
    assert.deepEqual(changes, ['change', 'change']);
  } finally { global.HTMLOptGroupElement = previous; }
});

test('P5d Ghosts, melody rhythm and humanize match complete production Java drum phrases', () => {
  const { load } = fixture();
  const { generateDrumPart } = load('core/music/drum-generator.ts');
  const cases = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/drum-advanced.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(cases.length, 192);
  for (const [index, entry] of cases.entries()) {
    const actual = generateDrumPart(BigInt(entry.seed), entry.barCount, entry.settings, entry.melody);
    assert.equal(actual.length, entry.notes.length, `case ${index} ${JSON.stringify(entry.settings)}`);
    actual.forEach((note, position) => {
      const expected = entry.notes[position];
      assert.equal(note.midi, expected[0], `case ${index} note ${position} pitch`);
      assert.equal(note.velocity, expected[3], `case ${index} note ${position} velocity`);
      assert.ok(Math.abs(note.startBeat - expected[1]) < 1e-9, `case ${index} note ${position} onset ${note.startBeat} vs ${expected[1]}`);
      assert.ok(Math.abs(note.durationBeats - expected[2]) < 1e-9, `case ${index} note ${position} duration ${note.durationBeats} vs ${expected[2]}`);
    });
  }
});

test('P5c Arpeggio direction, rotation, contour, octave and melody consumers match complete Java phrases', () => {
  const { load } = fixture();
  const { generateArpeggio } = load('core/music/arpeggio-generator.ts');
  const cases = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/arpeggio-core.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(cases.length, 256);
  for (const [index, entry] of cases.entries()) {
    const actual = generateArpeggio(BigInt(entry.seed), entry.key, entry.scale, entry.progression, entry.settings, undefined, entry.melody);
    assert.equal(actual.length, entry.notes.length, `case ${index} ${JSON.stringify(entry.settings)}`);
    actual.forEach((note, position) => {
      const expected = entry.notes[position];
      assert.equal(note.midi, expected[0], `case ${index} note ${position} pitch`);
      assert.equal(note.velocity, expected[3], `case ${index} note ${position} velocity`);
      assert.ok(Math.abs(note.startBeat - expected[1]) < 1e-9, `case ${index} note ${position} onset ${note.startBeat} vs ${expected[1]}`);
      assert.ok(Math.abs(note.durationBeats - expected[2]) < 1e-9, `case ${index} note ${position} duration ${note.durationBeats} vs ${expected[2]}`);
    });
  }
});

test('P5 dependencies use section melody settings, manual ownership and absent source presence through MIDI', () => {
  const { service, phrase, midi } = fixture();
  const project = JSON.parse(service.exportProjectJson());
  project.arrangement = project.arrangement.slice(0, 2).map(section => ({ ...section, measures: 4,
    trackPresence: { ...section.trackPresence, 'track-melody-1': false, 'track-drums-1': true, 'track-arpeggio-1': true } }));
  assert.equal(service.importProjectJson(JSON.stringify(project)), true);
  const first = service.project().arrangement[0], second = service.project().arrangement[1];
  service.updateTrackPhrase('track-melody-1', [
    { id: 'source-a', midi: 61, startBeat: 0, durationBeats: 1, velocity: 90 },
    { id: 'source-b', midi: 74, startBeat: 2, durationBeats: 1, velocity: 90 },
  ]);
  service.updatePartSettings({ kind: 'global-track', trackId: 'track-drums-1' }, { rhythm: 'melody', velocityMin: 80, velocityMax: 80, swingPercent: 50 });
  const drums = () => service.project().tracks.find(track => track.id === 'track-drums-1');
  const arp = () => service.project().tracks.find(track => track.id === 'track-arpeggio-1');
  assert.deepEqual(phrase.layOutTrackPhrase(service.project(), drums()).map(note => note.startBeat), [0, 2, 16, 18]);
  service.updatePartSettings({ kind: 'global-track', trackId: arp().id }, { pattern: 'up', rhythm: 'full', hitsPerPattern: 4,
    chordSpan: 1, patternRepeat: 1, exceptionChance: 0, useOctaveAdjustments: false, correctMelodyNotes: true });
  // Isolate semitone collision correction from the new progression-wide voicing pass.
  service.updateHarmonySettings({ flattenVoicingChance: 0 });
  assert.equal(phrase.generateTrackPhrase(service.project(), arp())[0].midi, 37);
  service.updateTrackPhrase('track-melody-1', [{ id: 'replacement', midi: 62, startBeat: 1, durationBeats: 1, velocity: 90 }]);
  assert.deepEqual(phrase.layOutTrackPhrase(service.project(), drums()).map(note => note.startBeat), [1, 17]);
  service.clearTrackPhrase('track-melody-1');
  const before = phrase.sectionMelodyContext(service.project(), first);
  const local = { kind: 'section-track', sectionId: first.id, trackId: 'track-melody-1' };
  service.updatePartSettings(local, { speed: 100, melodyPatternOffsets: [1, 2, 3, 4], pauseChance: 0 });
  const changed = phrase.sectionMelodyContext(service.project(), service.project().arrangement[0]);
  assert.notDeepEqual(changed, before);
  const unmodified = phrase.sectionMelodyContext(service.project(), service.project().arrangement[1]);
  assert.deepEqual(unmodified, phrase.sectionMelodyContext(service.project()));
  const gridStarts = changed.pitches.flat().flatMap((pitch, index) => pitch > 0 ? [index / 4] : []);
  const arranged = phrase.layOutTrackPhrase(service.project(), drums());
  assert.deepEqual(arranged.filter(note => note.startBeat < 16).map(note => note.startBeat), gridStarts);
  const exported = new Midi(midi.generateCompositionMidi(service.project()));
  const drumMidi = exported.tracks.find(track => track.name === drums().name);
  assert.equal(drumMidi.channel, 9); assert.equal(drumMidi.notes.length, arranged.length);
  arranged.forEach(note => assert.ok(drumMidi.notes.some(item => item.midi === note.midi && Math.abs(item.ticks / exported.header.ppq - note.startBeat) < 0.003)));
  service.setSectionTrackPresence(0, drums().id, false);
  assert.equal(phrase.layOutTrackPhrase(service.project(), drums()).some(note => note.startBeat < 16), false);
  assert.ok(phrase.layOutTrackPhrase(service.project(), drums()).some(note => note.startBeat >= 16));
  service.undo(); assert.deepEqual(phrase.layOutTrackPhrase(service.project(), drums()), arranged);
  service.updateTrackPhrase(drums().id, [{ id: 'manual-drum', midi: 38, startBeat: 0, durationBeats: 1, velocity: 100 }]);
  assert.equal(phrase.phraseForTrack(service.project(), drums())[0].id, 'manual-drum');
  assert.equal(service.importProjectJson(service.exportProjectJson()), true);
  service.removeTrack('track-melody-1');
  const noSource = phrase.generateTrackPhrase(service.project(), arp());
  service.updatePartSettings({ kind: 'global-track', trackId: arp().id }, { correctMelodyNotes: false });
  assert.deepEqual(phrase.generateTrackPhrase(service.project(), arp()), noSource, 'missing melody does not correct pitches toward an invented tonic');
});

test('P5c/P5d controls resolve mixed scopes, snapshots, independent arrays, resets and strict imports', () => {
  const { service, settings, load, stored } = fixture();
  const first = service.project().arrangement[0], second = service.project().arrangement[1];
  for (const [role, patch] of [
    ['arpeggio', { pattern: 'custom', arpPatternRotate: 3, arpPatternCustom: [-1, 0, 4], arpContour: [0, 2, -1],
      arpContourChordMode: true, useOctaveAdjustments: true, correctMelodyNotes: true }],
    ['drums', { ghosts: true, humanize: 100, rhythm: 'melody' }],
  ]) {
    const primary = `track-${role}-1`, duplicate = service.duplicateTrack(primary);
    const cell = { kind: 'section-role', sectionId: first.id, role };
    const local = { kind: 'section-track', sectionId: first.id, trackId: duplicate };
    service.updatePartSettings(cell, patch);
    const effective = () => settings.resolvePartTrack(service.project().tracks.find(track => track.id === duplicate), service.project().arrangement[0]).generatorSettings;
    for (const [key, value] of Object.entries(patch)) assert.deepEqual(effective()[key], value);
    const field = role === 'arpeggio' ? 'arpContour' : 'ghosts', exception = role === 'arpeggio' ? [] : false;
    service.updatePartSettings(local, { [field]: exception }); assert.deepEqual(effective()[field], exception);
    service.freezePartSettings(cell);
    service.applyPartSettingsToSections(cell, [second.id], 'effective');
    const source = service.project().arrangement[0].trackPartOverrides[duplicate];
    const copied = service.project().arrangement[1].trackPartOverrides[duplicate];
    assert.deepEqual(source, copied);
    if (role === 'arpeggio') assert.notEqual(source.arpPatternCustom, copied.arpPatternCustom);
    service.copyPartSettingsToGlobals(local);
    assert.deepEqual(service.project().tracks.find(track => track.id === duplicate).generatorSettings[field], exception);
    service.resetPartSettings(local, field); assert.deepEqual(effective()[field], patch[field]);
    const before = service.exportProjectJson(); service.undo(); service.redo(); assert.equal(service.exportProjectJson(), before);
  }
  const before = service.exportProjectJson();
  for (const [role, bad] of [['arpeggio', { arpContour: [1.5] }], ['arpeggio', { arpPatternCustom: [] }],
    ['arpeggio', { arpPatternRotate: 9 }], ['arpeggio', { correctMelodyNotes: 1 }], ['drums', { humanize: 101 }],
    ['drums', { ghosts: 1 }], ['bass', { rhythm: 'melody' }], ['chords', { rhythm: 'melody' }]]) {
    service.updatePartSettings({ kind: 'global-role', role }, bad);
    const project = JSON.parse(before); Object.assign(project.tracks.find(track => track.role === role).generatorSettings, bad);
    assert.equal(service.importProjectJson(JSON.stringify(project)), false, JSON.stringify(bad));
  }
  assert.equal(service.exportProjectJson(), before);
  assert.equal(service.importProjectJson(before), true);
  const { ProjectService } = load('core/project/project.service.ts');
  assert.equal(new ProjectService().exportProjectJson(), service.exportProjectJson());
  assert.ok(stored.get('vibecomposer.project.v12'));
});

test('P5d Multiply Hits transforms mixed drums atomically at global/local scopes and preserves ownership', () => {
  const { service, phrase } = fixture();
  const first = service.project().arrangement[0];
  const id = service.duplicateTrack('track-drums-1');
  service.updatePartSettings({ kind: 'global-track', trackId: id }, { hitsPerPattern: 8, rhythm: 'custom', patternShift: 1,
    customPattern: Array.from({ length: 32 }, (_, index) => index % 4 === 0 || index % 4 === 3 ? 1 : 0) });
  service.updateTrackPhrase(id, [{ id: 'manual', midi: 38, startBeat: 0, durationBeats: 1, velocity: 90 }]);
  const before = service.exportProjectJson();
  assert.equal(service.multiplyDrumHits({ kind: 'global-role', role: 'drums' }, 1.5), 'changed');
  const settings = service.project().tracks.filter(track => track.role === 'drums').map(track => track.generatorSettings);
  assert.deepEqual(settings.map(part => part.hitsPerPattern), [6, 12]);
  assert.equal(settings[1].patternShift, 0);
  assert.deepEqual(settings[1].customPattern.slice(0, 8), [1, 1, 0, 0, 1, 1, 0, 0]);
  assert.equal(phrase.phraseForTrack(service.project(), service.project().tracks.find(track => track.id === id))[0].id, 'manual');
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.redo();
  const local = { kind: 'section-role', role: 'drums', sectionId: first.id };
  service.updatePartSettings(local, { ghosts: true });
  const otherBefore = JSON.stringify(service.project().arrangement[1]);
  const globalsBefore = JSON.stringify(service.project().tracks);
  assert.equal(service.multiplyDrumHits(local, 0.5), 'changed');
  assert.equal(JSON.stringify(service.project().tracks), globalsBefore);
  assert.equal(JSON.stringify(service.project().arrangement[1]), otherBefore);
  assert.deepEqual(service.project().arrangement[0].rolePartOverrides.drums, { ghosts: true });
  assert.deepEqual(service.project().arrangement[0].trackPartOverrides['track-drums-1'], { hitsPerPattern: 3 });
  assert.deepEqual(service.project().arrangement[0].trackPartOverrides[id], { hitsPerPattern: 6 });
  service.updatePartSettings({ kind: 'section-track', trackId: id, sectionId: first.id }, { rhythm: 'melody' });
  const blocked = service.exportProjectJson();
  assert.equal(service.multiplyDrumHits(local, 2), 'invalid');
  assert.equal(service.multiplyDrumHits({ kind: 'section-role', role: 'drums', sectionId: 'deleted' }, 2), 'invalid');
  assert.equal(service.multiplyDrumHits({ kind: 'global-role', role: 'bass' }, 2), 'invalid');
  assert.equal(service.exportProjectJson(), blocked);
  assert.equal(service.importProjectJson(blocked), true);
});

test('P5 editor supports empty contour and separates pitch direction from melody rhythm and hit actions', () => {
  const { load } = fixture();
  const { PartSettingsEditorComponent } = load('shared/part-settings-editor.component.ts');
  const editor = new PartSettingsEditorComponent(); editor.role.set('arpeggio');
  editor.values.set({ pattern: 'custom', rhythm: 'melody', arpContour: [1] });
  assert.ok(editor.controls().some(control => control.key === 'arpPatternCustom'));
  assert.equal(editor.controls().some(control => control.key === 'hitsPerPattern'), false);
  assert.equal(editor.rhythmPreview(), undefined);
  let change; editor.settingsChanged.emit = value => change = value;
  const input = { value: '', setCustomValidity(value) { assert.equal(value, ''); } };
  editor.commitText(editor.controls().find(control => control.key === 'arpContour'), { target: input });
  assert.deepEqual(change, { key: 'arpContour', value: [] });
});

test('compact history groups same-control bursts while keeping live state, persistence and final redo', (t) => {
  let now = 0;
  t.mock.method(performance, 'now', () => now);
  const { service, stored } = fixture({ compactHistory: true });
  const before = service.exportProjectJson();
  assert.equal(service.compactHistory(), true);
  assert.equal(service.canUndo(), false, 'preference does not create project history');
  for (const tempoBpm of [130, 131, 132]) {
    now += 200;
    service.updateSettings({ tempoBpm });
    assert.equal(service.project().tempoBpm, tempoBpm);
    assert.equal(JSON.parse(stored.get('vibecomposer.project.v12')).tempoBpm, tempoBpm);
  }
  const after = service.exportProjectJson();
  service.undo(); assert.equal(service.exportProjectJson(), before); assert.equal(service.canUndo(), false);
  service.redo(); assert.equal(service.exportProjectJson(), after);
  service.updateSettings({ tempoBpm: 133 });
  service.undo(); assert.equal(service.exportProjectJson(), after, 'redo ends a burst');
  service.updateSettings({ tempoBpm: 134 });
  assert.equal(service.canRedo(), false, 'new edits clear the redo branch');
  service.undo(); assert.equal(service.exportProjectJson(), after, 'undo ends a burst');
});

test('compact history separates fields, scopes, delayed edits, actions and multi-control patches', (t) => {
  let now = 0;
  t.mock.method(performance, 'now', () => now);
  const { service } = fixture({ compactHistory: true });
  const track = { kind: 'global-track', trackId: 'track-chords-1' };
  const snapshots = [service.exportProjectJson()];
  const step = (edit) => { edit(); snapshots.push(service.exportProjectJson()); };
  step(() => {
    service.updatePartSettings(track, { strum: 100 });
    now += 100; service.updatePartSettings(track, { strum: 200 });
  });
  step(() => service.updatePartSettings(track, { pauseChance: 10 }));
  step(() => service.updatePartSettings({ kind: 'global-role', role: 'chords' }, { pauseChance: 20 }));
  step(() => service.updatePartSettings({ kind: 'section-track', sectionId: service.project().arrangement[0].id,
    trackId: 'track-chords-1' }, { pauseChance: 30 }));
  step(() => service.updateSettings({ tempoBpm: 130 }));
  now += 1000;
  step(() => service.updateSettings({ tempoBpm: 140 }));
  step(() => service.addChord());
  step(() => service.updateSettings({ tempoBpm: 150 }));
  step(() => service.updateSettings({ tempoBpm: 160, transposeSemitones: 1 }));
  step(() => service.updateSettings({ tempoBpm: 170, transposeSemitones: 2 }));
  for (let i = snapshots.length - 2; i >= 0; i--) {
    service.undo(); assert.equal(service.exportProjectJson(), snapshots[i], 'separate undo step ' + i);
  }
  assert.equal(service.canUndo(), false);
  for (const snapshot of snapshots.slice(1)) { service.redo(); assert.equal(service.exportProjectJson(), snapshot); }
});

test('compact history covers mixer and policies and switching it off preserves individual edits', (t) => {
  t.mock.method(performance, 'now', () => 0);
  const { service } = fixture({ compactHistory: true });
  const before = service.exportProjectJson();
  service.updateTrack('track-bass-1', { mix: { volumePercent: 90 } });
  service.updateTrack('track-bass-1', { mix: { volumePercent: 80 } });
  const mixed = service.exportProjectJson();
  assert.equal(service.updateTrackGenerationPolicy('arpeggio', { maxSplit: 10 }), 'changed');
  assert.equal(service.updateTrackGenerationPolicy('arpeggio', { maxSplit: 20 }), 'changed');
  service.undo(); assert.equal(service.exportProjectJson(), mixed);
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.toggleCompactHistory();
  service.updateSettings({ tempoBpm: 130 });
  service.updateSettings({ tempoBpm: 140 });
  service.undo(); assert.equal(service.project().tempoBpm, 130);
  service.toggleCompactHistory();
  service.updateSettings({ tempoBpm: 150 });
  service.undo(); assert.equal(service.project().tempoBpm, 130, 'toggling starts a fresh burst');
});

test('Chordlet roots, spice and inversions match all 2244 production Java voicings', () => {
  const { load } = fixture(), harmony = load('core/music/harmony.ts');
  const cases = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/chordlet-harmony.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(cases.length, 2244);
  for (const sample of cases) assert.deepEqual(harmony.chordPitches(sample, 60), sample.notes, JSON.stringify(sample));
  assert.equal(harmony.effectiveKey('C', -1), 'B');
  assert.equal(harmony.effectiveKey('F♯', 8), 'D');
  assert.equal(harmony.effectiveKey('B', -24), 'B');
});

test('Chordlet colors follow relative roots and spice overlap uses the actual notes in the active scale', () => {
  const { load } = fixture(), harmony = load('core/music/harmony.ts');
  const chord = (root, spice, inversion = 0) => ({ root, spice, inversion });
  assert.equal(harmony.chordRootHue(7, 'C'), 210);
  assert.equal(harmony.chordRootHue(9, 'D'), 210);
  assert.equal(harmony.chordRootHue(1, 'F♯'), 210);
  assert.deepEqual(harmony.chordKeyOverlap(chord(0, 'maj7'), 'C', 'major'), { matching: 4, total: 4, ratio: 1 });
  assert.deepEqual(harmony.chordKeyOverlap(chord(0, '7'), 'C', 'major'), { matching: 3, total: 4, ratio: .75 });
  assert.deepEqual(harmony.chordKeyOverlap(chord(0, 'm'), 'C', 'major'), { matching: 2, total: 3, ratio: 2/3 });
  assert.deepEqual(harmony.chordKeyOverlap(chord(6, ''), 'C', 'major'), { matching: 0, total: 3, ratio: 0 });
  assert.deepEqual(harmony.chordKeyOverlap(chord(4, 'm', -5), 'A', 'natural-minor'), { matching: 3, total: 3, ratio: 1 });
  assert.deepEqual(harmony.chordKeyOverlap(chord(4, 'maj7'), 'A', 'natural-minor'), { matching: 2, total: 4, ratio: .5 });
  for (const spice of harmony.CHORD_SPICES) {
    const original = harmony.chordKeyOverlap(chord(4, spice), 'A', 'natural-minor');
    assert.deepEqual(harmony.chordKeyOverlap(chord(11, spice, 5), 'E', 'natural-minor'), original, spice);
  }
});

test('Chordlet edits follow reorder/add/remove through history, persistence and pitched MIDI', () => {
  const { service, phrase, midi, load } = fixture();
  service.project().tracks.forEach(track => service.project().arrangement.forEach((_, index) => service.setSectionTrackPresence(index, track.id, true)));
  service.setProgression([1]);
  service.updatePartSettings({ kind: 'global-role', role: 'chords' }, { rhythm: 'single', voicing: 'close' });
  service.updatePartSettings({ kind: 'global-role', role: 'arpeggio' }, { pattern: 'up', stretchEnabled: false, octaves: 1 });
  service.updatePartSettings({ kind: 'global-role', role: 'bass' }, { rhythm: 'full', noteVariation: 0 });
  const chord = { root: 1, spice: '7', inversion: 1 };
  const before = service.exportProjectJson();
  service.setProgressionChord(0, chord);
  const after = service.exportProjectJson();
  const byRole = role => service.project().tracks.find(track => track.role === role);
  assert.deepEqual(phrase.phraseForTrack(service.project(), byRole('chords')).slice(0, 4).map(note => note.midi), [65,68,71,73]);
  assert.deepEqual(phrase.phraseForTrack(service.project(), byRole('arpeggio')).slice(0, 4).map(note => note.midi), [41,44,47,49]);
  assert.ok(phrase.phraseForTrack(service.project(), byRole('bass')).every(note => note.midi === 37));
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.redo(); assert.equal(service.exportProjectJson(), after);
  service.setSectionChordDegree(0, 0, 1);
  assert.ok(service.project().arrangement[0].chordDegrees, 'explicit local degree overrides a custom primary chord');
  assert.deepEqual(phrase.layOutTrackPhrase(service.project(), byRole('chords')).slice(0,3).map(note => note.midi), [60,64,67]);
  service.clearSectionChordOverrides(0);
  service.updateSettings({ transposeSemitones: 5, tempoBpm: 150 });
  const parsed = new Midi(midi.generateCompositionMidi(service.project()));
  assert.deepEqual(parsed.tracks.find(track => track.name === byRole('chords').name).notes.slice(0,4).map(note => note.midi).sort((a,b)=>a-b), [70,73,76,78]);
  const drums = phrase.layOutTrackPhrase(service.project(), byRole('drums'));
  service.updateSettings({ transposeSemitones: -5 });
  assert.deepEqual(phrase.layOutTrackPhrase(service.project(), byRole('drums')), drums);
  service.addChord(); service.addChord();
  service.setProgressionChord(2, { root: 7, spice: 'sus4', inversion: -2 });
  service.moveProgressionChord(0, 2);
  assert.equal(service.project().progressionChords[2].spice, '7');
  assert.equal(service.project().progressionChords[1].spice, 'sus4');
  service.removeChord(1); assert.equal(service.project().progressionChords.length, 2);
  const saved = service.exportProjectJson();
  assert.equal(service.importProjectJson(saved), true);
  assert.equal(new (load('core/project/project.service.ts').ProjectService)().project().progressionChords[1].root, 1);
  service.setChordDegree(1, 5); assert.equal(service.project().progressionChords[1], null);
  service.setProgression([1,6,4,5]); assert.equal(service.project().progressionChords, undefined);
});

test('Chordlet commands and imports reject malformed harmony atomically', () => {
  const { service } = fixture();
  service.updateSettings({ name: 'redo' }); service.undo();
  const before = service.exportProjectJson();
  for (const chord of [{ root: 12, spice: '', inversion: 0 }, { root: 1, spice: 'broken', inversion: 0 },
    { root: 0, spice: 'm', inversion: 6 }, { root: 0, spice: 'm', inversion: -6 },
    { root: 1.5, spice: '', inversion: 0 }, { root: 0, spice: '', inversion: 0, extra: true }]) {
    service.setProgressionChord(0, chord); assert.equal(service.exportProjectJson(), before);
    const data = JSON.parse(before); data.progressionChords = data.progression.map((_,i) => i === 0 ? chord : null);
    assert.equal(service.importProjectJson(JSON.stringify(data)), false);
  }
  for (const pair of [[-1,0],[0,32],[NaN,0],[0,1.5],[0,0]]) service.moveProgressionChord(...pair);
  for (const custom of [[], [null], {}, [null,null,null,null,null]]) {
    const data = JSON.parse(before); data.progressionChords = custom;
    assert.equal(service.importProjectJson(JSON.stringify(data)), false);
  }
  assert.equal(service.exportProjectJson(), before); assert.equal(service.canRedo(), true);
});

test('chord joins, transitions and per-tone strums match 288 production Java phrases', () => {
  const { load } = fixture();
  const { generateChordPart } = load('core/music/chord-generator.ts');
  const cases = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/chord-core.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(cases.length, 288);
  const sort = notes => notes.sort((a, b) => a[1] - b[1] || a[0] - b[0]);
  for (const [caseIndex, sample] of cases.entries()) {
    const hits = generateChordPart(BigInt(sample.seed), sample.key, sample.scale, sample.progression, sample.settings);
    const actual = sort(hits.flatMap(hit => hit.pitches.map((pitch, index) => [pitch,
      hit.startBeat + (hit.pitchOffsets?.[index] ?? 0), hit.pitchDurations?.[index] ?? hit.durationBeats, hit.velocity])));
    const expected = sort(sample.notes);
    assert.equal(actual.length, expected.length, `case ${caseIndex} count`);
    actual.forEach((note, index) => {
      const label = `case ${caseIndex}, note ${index}`;
      assert.equal(note[0], expected[index][0], `${label} pitch`);
      assert.ok(Math.abs(note[1] - expected[index][1]) < 1e-10, `${label} onset ${note[1]} vs ${expected[index][1]}`);
      assert.ok(Math.abs(note[2] - expected[index][2]) < 1e-10, `${label} duration ${note[2]} vs ${expected[index][2]}`);
      assert.equal(note[3], expected[index][3], `${label} velocity`);
    });
  }
});

test('chord articulation resolves scopes, mixed values, snapshots, manual ownership and MIDI', () => {
  const { service, settings, phrase, midi, ui, workspace, load } = fixture();
  const id = 'track-chords-1', track = () => service.project().tracks.find(track => track.id === id);
  const [first, second] = service.project().arrangement;
  const cell = { kind: 'section-role', sectionId: first.id, role: 'chords' };
  const part = { kind: 'section-track', sectionId: first.id, trackId: id };
  service.project().arrangement.forEach((_, index) => service.setSectionTrackPresence(index, id, true));
  service.updatePartSettings({ kind: 'global-role', role: 'chords' }, { rhythm: 'half', hitsPerPattern: 8 });
  const baseline = phrase.layOutTrackPhrase(service.project(), track());
  const patch = { patternJoinMode: 'EXPAND', transitionChance: 100, transitionSplit: 625, strum: 250,
    strumType: 'ARP_U', strumPauseChance: 0 };
  service.updatePartSettings(cell, patch);
  const notes = phrase.layOutTrackPhrase(service.project(), track());
  assert.notDeepEqual(notes.filter(note => note.startBeat < first.measures * 4), baseline.filter(note => note.startBeat < first.measures * 4));
  assert.deepEqual(notes.filter(note => note.startBeat >= first.measures * 4), baseline.filter(note => note.startBeat >= first.measures * 4));
  service.undo(); assert.deepEqual(phrase.layOutTrackPhrase(service.project(), track()), baseline);
  service.redo(); assert.deepEqual(phrase.layOutTrackPhrase(service.project(), track()), notes);
  const parsed = new Midi(midi.generateCompositionMidi(service.project()));
  const exported = parsed.tracks.find(candidate => candidate.name === track().name).notes;
  for (const note of notes) assert.ok(exported.some(candidate => candidate.midi === note.midi
    && Math.abs(candidate.ticks / parsed.header.ppq - note.startBeat) < 0.003
    && Math.abs(candidate.durationTicks / parsed.header.ppq - note.durationBeats) < 0.003));
  service.freezePartSettings(part);
  for (const [key, value] of Object.entries(patch)) assert.equal(service.project().arrangement[0].trackPartOverrides[id][key], value);
  service.applyPartSettingsToSections(part, [second.id], 'effective');
  service.updatePartSettings({ kind: 'global-track', trackId: id }, { strum: 1000, strumType: 'ARP_D' });
  assert.equal(settings.resolvePartTrack(track(), service.project().arrangement[0]).generatorSettings.strum, 250);
  const duplicate = service.duplicateTrack(id);
  service.updatePartSettings({ kind: 'section-track', sectionId: first.id, trackId: duplicate }, { strum: 500 });
  ui.selectCell(first.id, 'chords'); assert.equal(workspace.partValues().strum, null);
  assert.equal(service.copyPartSettingsToGlobals(cell), 'changed');
  assert.equal(track().generatorSettings.strum, 250);
  assert.equal(service.project().tracks.find(item => item.id === duplicate).generatorSettings.strum, 500);
  const saved = service.exportProjectJson(), beforeImport = phrase.layOutTrackPhrase(service.project(), track());
  assert.equal(service.importProjectJson(saved), true);
  assert.deepEqual(phrase.layOutTrackPhrase(service.project(), track()), beforeImport);
  const restored = new (load('core/project/project.service.ts').ProjectService)();
  assert.equal(restored.project().arrangement[0].trackPartOverrides[id].strumType, 'ARP_U');
  service.updateTrackPhrase(id, [{ id: 'manual', midi: 60, startBeat: 0, durationBeats: 1, velocity: 80 }]);
  service.updatePartSettings(part, { strumPauseChance: 100, transitionChance: 0 });
  assert.ok(phrase.layOutTrackPhrase(service.project(), track()).every(note => note.midi === 60 && note.durationBeats === 1));
});

test('chord articulation rejects malformed imports and wrong-role controls without losing redo', () => {
  const { service, settings } = fixture();
  service.updateSettings({ name: 'redo' }); service.undo();
  const saved = service.exportProjectJson();
  for (const patch of [{ strum: -1 }, { strum: 2001 }, { strum: 1.5 }, { strumType: 'UP' }, { strumPauseChance: 101 },
    { transitionChance: null }, { transitionSplit: 1001 }, { patternJoinMode: 'expand' }]) {
    service.updatePartSettings({ kind: 'global-role', role: 'chords' }, patch);
    for (const location of ['role', 'track', 'section-role', 'section-track']) {
      const data = JSON.parse(saved);
      if (location === 'role') Object.assign(data.chords, patch);
      if (location === 'track') Object.assign(data.tracks.find(track => track.role === 'chords').generatorSettings, patch);
      if (location === 'section-role') data.arrangement[0].rolePartOverrides = { chords: patch };
      if (location === 'section-track') data.arrangement[0].trackPartOverrides = { 'track-chords-1': patch };
      assert.equal(service.importProjectJson(JSON.stringify(data)), false);
    }
  }
  for (const role of ['melody', 'bass', 'arpeggio', 'drums']) {
    assert.equal(settings.decodePartPatch(role, { strum: 100 }), undefined);
    assert.equal(settings.decodePartPatch(role, { transitionSplit: 625 }), undefined);
  }
  assert.equal(service.exportProjectJson(), saved); assert.equal(service.canRedo(), true);
});

test('chord policies bias strums, bound transitions/pauses, enforce Expand and retain reroll ownership', () => {
  const { service, load } = fixture();
  const core = load('core/music/track-generation.ts'), { JavaRandom } = load('core/music/java-random.ts');
  const legacy = { ...core.DEFAULT_TRACK_POLICIES.chords };
  for (const key of Object.keys(core.DEFAULT_CHORD_POLICY_EXTENSION)) delete legacy[key];
  assert.ok(core.decodeTrackGenerationPolicy('chords', legacy), 'old policies remain readable');
  const categories = new Set();
  for (let seed = 0; seed < 200; seed++) {
    assert.ok(core.chooseChordStrum(new JavaRandom(seed), 0).type.startsWith('HUMAN'));
    assert.ok(core.chooseChordStrum(new JavaRandom(seed), 100).type.startsWith('ARP'));
    const neutral = core.chooseChordStrum(new JavaRandom(seed), 50).type;
    categories.add(neutral.startsWith('ARP') ? 'ARP' : neutral.startsWith('RAND') ? 'RAND' : 'HUMAN');
  }
  assert.equal(categories.size, 3);
  service.updateInstrumentPolicy('chords', { randomize: false });
  const source = service.project().tracks.find(track => track.role === 'chords');
  const policy = { ...core.DEFAULT_TRACK_POLICIES.chords, struminess: 100, maxSplit: 35, split: true,
    maxStrumPause: 20, expandChance: 100, patterns: false, voicesMode: 'NONE' };
  for (let seed = 0; seed < 100; seed++) {
    const [next] = core.rerollRoleSettings(BigInt(seed), 'chords', policy, [source], [1]);
    assert.ok(next.transitionChance >= 0 && next.transitionChance <= 35);
    assert.ok([625,750,875].includes(next.transitionSplit));
    assert.ok(next.strumPauseChance >= 0 && next.strumPauseChance <= 20);
    assert.equal(next.patternJoinMode, 'EXPAND'); assert.ok(next.strumType.startsWith('ARP'));
    const [disabled] = core.rerollRoleSettings(BigInt(seed), 'chords', { ...policy, strum: false, split: false, maxSplit: 0, expandChance: 0 }, [source], [1]);
    assert.equal(disabled.strum, 0); assert.equal(disabled.strumPauseChance, 0);
    assert.equal(disabled.transitionChance, 0); assert.equal(disabled.transitionSplit, 625); assert.equal(disabled.patternJoinMode, 'NOJOIN');
    const [pad] = core.rerollRoleSettings(BigInt(seed), 'chords', { ...policy, expandChance: 0 }, [{ ...source, instrumentPool: 'LONG_PAD' }], [1]);
    assert.equal(pad.patternJoinMode, 'EXPAND');
    const [dense] = core.rerollRoleSettings(BigInt(seed), 'chords', { ...policy, voicesMode: 'FIXED', voices: 6, voicesChance: 100 },
      [{ ...source, generatorSettings: { ...source.generatorSettings, rhythm: 'full' } }], [1]);
    assert.ok(dense.strum < 500, 'long strums halve for expanded voices then quarter for FULL');
  }
  service.addTrack('chords'); service.setTrackRerollLock('track-chords-2', true);
  const section = service.project().arrangement[0];
  service.updatePartSettings({ kind: 'section-track', sectionId: section.id, trackId: source.id }, { strum: 333 });
  service.updateTrackPhrase(source.id, [{ id: 'manual', midi: 60, startBeat: 0, durationBeats: 1, velocity: 80 }]);
  const beforeRules = service.project().tracks;
  service.updateTrackGenerationPolicy('chords', policy);
  assert.strictEqual(service.project().tracks, beforeRules);
  const before = service.exportProjectJson();
  assert.equal(service.rerollRoleTracks('chords'), 'changed');
  const after = service.exportProjectJson();
  assert.strictEqual(service.project().tracks.find(track => track.id === 'track-chords-2'), beforeRules.find(track => track.id === 'track-chords-2'));
  assert.deepEqual(service.project().tracks.find(track => track.id === source.id).editedPhrase, beforeRules.find(track => track.id === source.id).editedPhrase);
  assert.equal(service.project().arrangement[0].trackPartOverrides[source.id].strum, 333);
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.rerollRoleTracks('chords'); assert.equal(service.exportProjectJson(), after);
  assert.equal(service.importProjectJson(after), true);
  service.updateTrackGenerationPolicy('chords', { onCompose: true });
  assert.equal(service.generateComposition('compose'), 'changed');
  for (const patch of [{ struminess: 101 }, { maxSplit: -1 }, { split: 0 }, { maxStrumPause: 1.5 }, { expandChance: 101 }]) {
    assert.equal(service.updateTrackGenerationPolicy('chords', patch), 'invalid');
  }
});

test('automatic events are atomic, replayable and preserve locks, local settings and manual notes', () => {
  const { service, load, stored, midi } = fixture();
  service.updateSettings({ randomizeTransposeOnCompose: false });
  service.updateSettings({ seed: '9007199254740993' });
  service.addTrack('melody');
  service.addTrack('drums');
  service.setTrackRerollLock('track-melody-2', true);
  service.updateTrackPhrase('track-melody-1', [{ id: 'manual', midi: 72, startBeat: 0, durationBeats: 1, velocity: 90 }]);
  const scope = { kind: 'section-track', sectionId: service.project().arrangement[0].id, trackId: 'track-arpeggio-1' };
  service.freezePartSettings(scope);
  for (const role of ['melody', 'chords', 'arpeggio', 'drums']) service.updateTrackGenerationPolicy(role, { onCompose: true });
  const before = service.exportProjectJson(), arrangement = service.project().arrangement;
  const locked = service.project().tracks.find(track => track.id === 'track-melody-2');
  assert.equal(service.generateComposition('compose'), 'changed');
  const after = service.exportProjectJson();
  for (const role of ['melody','chords','arpeggio','drums']) assert.equal(service.project().trackRerollCounts[role], 1);
  assert.strictEqual(service.project().arrangement, arrangement);
  assert.strictEqual(service.project().tracks.find(track => track.id === locked.id), locked);
  assert.deepEqual(service.project().tracks[0].editedPhrase, [{ id: 'manual', midi: 72, startBeat: 0, durationBeats: 1, velocity: 90 }]);
  assert.equal(new Midi(midi.generateCompositionMidi(service.project())).tracks[0].notes[0].midi, 72);
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.redo(); assert.equal(service.exportProjectJson(), after);
  service.undo(); service.generateComposition('compose'); assert.equal(service.exportProjectJson(), after);
  assert.deepEqual(projectData(stored.get('vibecomposer.project.v12')), projectData(after));
  assert.equal(service.importProjectJson(after), true);
  const { ProjectService } = load('core/project/project.service.ts');
  assert.deepEqual(projectData(new ProjectService().exportProjectJson()), projectData(after));
});

test('Regenerate changes only enabled Melody seeds, patterns and targets, respecting shared seeds', () => {
  const { service } = fixture();
  service.addTrack('melody'); service.addTrack('melody');
  service.setTrackRerollLock('track-melody-3', true);
  service.updateTrackGenerationPolicy('melody', { onRegenerate: true, sameSeed: true });
  const before = service.project(), locked = before.tracks.find(track => track.id === 'track-melody-3');
  assert.equal(service.generateComposition('regenerate'), 'changed');
  const next = service.project(), members = next.tracks.filter(track => track.role === 'melody');
  assert.equal(members[0].generatorSettings.patternSeed, members[1].generatorSettings.patternSeed);
  assert.strictEqual(members[2], locked);
  for (const track of next.tracks.filter(track => track.role !== 'melody')) assert.strictEqual(track, before.tracks.find(item => item.id === track.id));
  for (const track of members.slice(0, 2)) {
    const old = before.tracks.find(item => item.id === track.id).generatorSettings;
    for (const [key, value] of Object.entries(old)) {
      if (!['patternSeed', 'melodyPatternOffsets', 'chordNoteChoices'].includes(key)) assert.deepEqual(track.generatorSettings[key], value);
    }
  }
  service.updateTrackGenerationPolicy('melody', { onRegenerate: false });
  const json = service.exportProjectJson();
  assert.equal(service.generateComposition('regenerate'), 'unchanged'); assert.equal(service.exportProjectJson(), json);
  service.updateTrackGenerationPolicy('melody', { onRegenerate: true, rerollSeeds: false, targetsOnCompose: false, patternsOnCompose: false });
  assert.equal(service.generateComposition('regenerate'), 'unchanged');
});

test('automatic batch failures and no-ops preserve state, counters and redo', () => {
  const { service } = fixture();
  service.updateTrackGenerationPolicy('arpeggio', { onCompose: true, velocityMin: 65 });
  const scope = { kind: 'section-track', sectionId: service.project().arrangement[0].id, trackId: 'track-arpeggio-1' };
  service.updateTrackGeneratorSettings('track-arpeggio-1', { velocityMin: 50 });
  service.updatePartSettings(scope, { velocityMax: 60 });
  service.updateTrackGenerationPolicy('chords', { onCompose: true });
  const before = service.exportProjectJson();
  service.updateSettings({ tempoBpm: 130 }); service.undo();
  assert.equal(service.generateComposition('compose'), 'invalid');
  assert.equal(service.exportProjectJson(), before); assert.equal(service.canRedo(), true);
  assert.equal(service.generateComposition('regenerate'), 'unchanged'); assert.equal(service.canRedo(), true);
  assert.equal(service.generateComposition('bad'), 'invalid');
});

test('automatic policy import accepts omission and rejects invalid or unknown fields', () => {
  const { service } = fixture();
  for (const role of ['melody', 'bass', 'chords', 'arpeggio', 'drums']) {
    assert.equal(service.updateTrackGenerationPolicy(role, { onCompose: false }), 'changed');
    const data = JSON.parse(service.exportProjectJson());
    data.trackGenerationPolicies[role].onCompose = 'true';
    assert.equal(service.importProjectJson(JSON.stringify(data)), false);
  }
  assert.equal(service.updateTrackGenerationPolicy('bass', { patterns: true }), 'invalid');
  assert.equal(service.updateTrackGenerationPolicy('drums', { onRegenerate: true }), 'invalid');
});

test('copy to globals preserves mixed cell members, track exceptions and local ownership with one history entry', () => {
  const { service, settings, load } = fixture();
  service.addTrack('bass');
  const sectionId = service.project().arrangement[0].id;
  const cell = { kind: 'section-role', sectionId, role: 'bass' };
  service.updatePartSettings(cell, { transpose: -12 });
  service.updatePartSettings({ kind: 'section-track', sectionId, trackId: 'track-bass-2' }, { transpose: 12, octaveInterval: true });
  service.updateTrackPhrase('track-bass-1', [{ id: 'manual', midi: 60, startBeat: 0, durationBeats: 1, velocity: 80 }]);
  service.setTrackRerollLock('track-bass-2', true);
  const before = service.exportProjectJson(), previous = service.project();
  assert.equal(service.partSettingsGlobalsWouldChange(cell), true);
  assert.equal(service.copyPartSettingsToGlobals(cell), 'changed');
  const next = service.project(), after = service.exportProjectJson();
  assert.strictEqual(next.arrangement, previous.arrangement);
  for (const track of previous.tracks) {
    const result = next.tracks.find(item => item.id === track.id);
    if (track.role !== 'bass') assert.strictEqual(result, track);
    else {
      assert.deepEqual(settings.settingsValues(result.generatorSettings, 'bass'), settings.settingsValues(settings.resolvePartTrack(track, previous.arrangement[0]).generatorSettings, 'bass'));
      for (const key of ['mix', 'editedPhrase', 'rerollLocked', 'midiChannel']) assert.deepEqual(result[key], track[key]);
    }
  }
  assert.equal(next.bass.transpose, -12);
  assert.equal(next.tracks.find(track => track.id === 'track-bass-2').generatorSettings.transpose, 12);
  assert.equal(service.partSettingsGlobalsWouldChange(cell), false);
  assert.equal(service.copyPartSettingsToGlobals(cell), 'unchanged');
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.redo(); assert.equal(service.exportProjectJson(), after);
  const { ProjectService } = load('core/project/project.service.ts');
  assert.deepEqual(projectData(new ProjectService().exportProjectJson()), projectData(after));
});

test('copy to globals rejects cross-section velocity conflicts atomically and stale targets safely', () => {
  const { service } = fixture();
  const [source, destination] = service.project().arrangement;
  const scope = { kind: 'section-track', sectionId: source.id, trackId: 'track-bass-1' };
  service.updatePartSettings(scope, { velocityMin: 80, velocityMax: 100 });
  service.updatePartSettings({ ...scope, sectionId: destination.id }, { velocityMax: 75 });
  const before = service.exportProjectJson();
  service.updateSettings({ tempoBpm: 131 }); service.undo();
  assert.equal(service.copyPartSettingsToGlobals(scope), 'invalid');
  assert.equal(service.exportProjectJson(), before); assert.equal(service.canRedo(), true);
  assert.equal(service.copyPartSettingsToGlobals({ ...scope, trackId: 'deleted' }), 'invalid');
  assert.equal(service.copyPartSettingsToGlobals({ ...scope, sectionId: 'deleted' }), 'invalid');
});

test('global copy ignores inherited defaults and a single-track copy leaves other mixed members intact', () => {
  const { service } = fixture();
  const sectionId = service.project().arrangement[0].id;
  for (const role of ['melody','bass','chords','arpeggio','drums']) {
    const cell = { kind: 'section-role', sectionId, role };
    assert.equal(service.partSettingsGlobalsWouldChange(cell), false);
    assert.equal(service.copyPartSettingsToGlobals(cell), 'unchanged');
  }
  service.addTrack('bass');
  const scope = { kind: 'section-track', sectionId, trackId: 'track-bass-2' };
  service.updatePartSettings({ kind: 'section-role', sectionId, role: 'bass' }, { noteVariation: 35 });
  service.updatePartSettings(scope, { noteVariation: 45 });
  const before = service.project();
  assert.equal(service.copyPartSettingsToGlobals(scope), 'changed');
  assert.strictEqual(service.project().tracks.find(track => track.id === 'track-bass-1'), before.tracks.find(track => track.id === 'track-bass-1'));
  assert.strictEqual(service.project().bass, before.bass);
  assert.equal(service.project().tracks.find(track => track.id === scope.trackId).generatorSettings.noteVariation, 45);
});

test('Melody automatic targets match production Java for the supported diatonic target path', () => {
  const { load } = fixture();
  const { chooseMelodyTargets } = load('core/music/melody-targets.ts');
  const oracle = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/melody-targets.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(oracle.length, 128);
  for (const sample of oracle) assert.deepEqual(chooseMelodyTargets(sample.seed, sample.progression), sample.targets);
});

test('P5e all target modes match 20736 production Java preparation and starting-note cases', () => {
  const { load } = fixture();
  const { chooseMelodyTargets, startingMelodyTarget } = load('core/music/melody-targets.ts');
  const { melodyRootChords } = load('core/music/melody-chord-targets.ts');
  const cases = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/melody-target-modes.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(cases.length, 20736);
  let recovered = 0;
  for (const [index, entry] of cases.entries()) {
    const targets = chooseMelodyTargets(entry.seed, entry.progression, entry.globalSettings, entry.key, entry.scale, entry.customChords);
    assert.deepEqual(targets, entry.targets, `target case ${index}`);
    const roots = melodyRootChords(entry.key, entry.scale, entry.progression, entry.customChords);
    entry.starting.forEach((expected, chord) => {
      const actual = startingMelodyTarget(roots, targets, chord, entry.globalSettings.blockTargetMode);
      if (expected !== null) assert.equal(actual, expected, `start case ${index}, chord ${chord}`);
      else { recovered++; assert.ok(Number.isInteger(actual) && actual >= 0 && actual <= 74); }
    });
  }
  assert.ok(recovered > 0, 'oracle covers Java null-pitch recovery');
});

test('P5e target mode edits reinterpret scoped notes and prepare Chordlet targets only on permitted events', () => {
  const { service, phrase, midi, load } = fixture();
  const id = 'track-melody-1', duplicate = service.duplicateTrack(id);
  const sectionId = service.project().arrangement[0].id;
  const track = target => service.project().tracks.find(item => item.id === target);
  service.setSectionTrackPresence(0, id, true);
  service.setTrackRerollLock(duplicate, true);
  service.setProgressionChord(0, { root: 6, spice: 'sus2', inversion: -3 });
  service.updatePartSettings({ kind: 'section-track', sectionId, trackId: id }, { chordNoteChoices: [-1, 1, 3] });
  const initial = service.project(), original = phrase.layOutTrackPhrase(initial, track(id));
  const context = phrase.sectionMelodyContext(initial, initial.arrangement[0]);
  const before = service.exportProjectJson();
  for (const mode of [0, 1]) {
    assert.equal(service.updateMelodyGlobal({ blockTargetMode: mode }), 'changed');
    assert.strictEqual(service.project().tracks, initial.tracks, 'mode edit retains target lists and track identities');
    assert.strictEqual(service.project().arrangement, initial.arrangement);
    assert.notDeepEqual(phrase.layOutTrackPhrase(service.project(), track(id)), original);
    assert.notDeepEqual(phrase.sectionMelodyContext(service.project(), service.project().arrangement[0]), context);
    const saved = service.exportProjectJson();
    service.undo(); service.redo(); assert.equal(service.exportProjectJson(), saved);
  }
  service.updateMelodyGlobal({ blockTargetMode: 2 });
  assert.deepEqual(phrase.layOutTrackPhrase(service.project(), track(id)), original);
  service.updateMelodyGlobal({ blockTargetMode: 1, useDirectionsFromProgression: true, targetNoteVariation: 6 });
  const { chooseMelodyTargets } = load('core/music/melody-targets.ts');
  service.updateTrackGenerationPolicy('melody', { targetsOnCompose: false, patternsOnCompose: false, rerollSeeds: false, onRegenerate: true });
  const existing = [...track(id).generatorSettings.chordNoteChoices ?? [0,2,2,4]];
  service.generateComposition('compose');
  assert.deepEqual(track(id).generatorSettings.chordNoteChoices ?? [0,2,2,4], existing);
  service.updateTrackGenerationPolicy('melody', { targetsOnCompose: true });
  const project = service.project(), locked = track(duplicate);
  const targetSeed = ((track(id).generatorSettings.patternSeed || Number(BigInt.asIntN(32, BigInt(project.seed)))) + 1) | 0;
  const expected = chooseMelodyTargets(targetSeed, project.progression, project.melodyGlobal, project.key, project.scale, project.progressionChords);
  assert.equal(service.generateComposition('compose'), 'changed');
  assert.deepEqual(track(id).generatorSettings.chordNoteChoices, expected);
  assert.strictEqual(track(duplicate), locked);
  assert.strictEqual(service.project().arrangement, project.arrangement);
  const prepared = service.exportProjectJson();
  service.undo(); service.generateComposition('compose'); assert.equal(service.exportProjectJson(), prepared);
  let saved = service.exportProjectJson();
  const notes = phrase.layOutTrackPhrase(service.project(), track(id));
  const exported = new Midi(midi.generateCompositionMidi(service.project()));
  const output = exported.tracks.find(item => item.name === track(id).name).notes;
  assert.equal(output.length, notes.length);
  output.forEach((note, index) => {
    assert.equal(note.midi, notes[index].midi);
    assert.ok(Math.abs(note.ticks / exported.header.ppq - notes[index].startBeat) < 0.003);
    assert.ok(Math.abs(note.durationTicks / exported.header.ppq - notes[index].durationBeats) < 0.003);
  });
  assert.deepEqual(projectData(new (load('core/project/project.service.ts').ProjectService)().exportProjectJson()), projectData(saved));
  assert.equal(service.importProjectJson(saved), true); saved = service.exportProjectJson();
  service.updateSettings({ name: 'redo' }); service.undo();
  assert.equal(service.updateMelodyGlobal({ blockTargetMode: 1 }), 'unchanged');
  for (const value of [-1, 3, 1.5, '1', null, true]) {
    assert.equal(service.updateMelodyGlobal({ blockTargetMode: value }), 'invalid');
    const imported = JSON.parse(saved); imported.melodyGlobal.blockTargetMode = value;
    assert.equal(service.importProjectJson(JSON.stringify(imported)), false);
    assert.equal(service.exportProjectJson(), saved);
    assert.equal(service.canRedo(), true);
  }
  for (const scope of [{ kind: 'global-role', role: 'melody' }, { kind: 'global-track', trackId: id },
    { kind: 'section-role', sectionId, role: 'melody' }, { kind: 'section-track', sectionId, trackId: id }]) {
    service.updatePartSettings(scope, { blockTargetMode: 0 }); assert.equal(service.exportProjectJson(), saved);
  }
  const manual = [{ id: 'mode-manual', midi: 61, startBeat: 1, durationBeats: 2, velocity: 90 }];
  service.updateTrackPhrase(id, manual); service.updateMelodyGlobal({ blockTargetMode: 0 });
  assert.deepEqual(phrase.phraseForTrack(service.project(), track(id)), manual);
  assert.notEqual(saved, before);
});

test('P5e target index boundaries retain Java octave rules and recover extreme valid lists', () => {
  const { service, phrase, load } = fixture();
  const { startingMelodyTarget, chooseMelodyTargets } = load('core/music/melody-targets.ts');
  assert.equal(startingMelodyTarget([[60,64,67]], [-3], 0, 0), 21, 'negative exact multiple uses Java extra lower octave');
  assert.equal(startingMelodyTarget([[60,64,67]], [-24], 0, 0), 2, 'invalid low index recovers to the nearest valid index toward zero');
  assert.equal(startingMelodyTarget([[60,64,67]], [24], 0, 0), 72, 'invalid high index recovers to the nearest valid index toward zero');
  assert.equal(startingMelodyTarget([[60,64,67], [67,71,74]], [0,2], 2, 1), 35, 'root-relative final next target wraps');
  assert.equal(startingMelodyTarget([[60,64,67], [67,71,74]], [0,2], 2, 2), 37, 'MIDI-relative final next target retains the last choice');
  for (const mode of [0,1,2]) {
    service.updateMelodyGlobal({ blockTargetMode: mode });
    service.updatePartSettings({ kind: 'global-track', trackId: 'track-melody-1' }, { chordNoteChoices: [-24,24] });
    const notes = phrase.phraseForTrack(service.project(), service.project().tracks[0]);
    assert.ok(notes.length > 0 && notes.every(note => Number.isInteger(note.midi) && note.midi >= 0 && note.midi <= 127));
  }
  assert.throws(() => chooseMelodyTargets(42, [1], {}, 'invalid'), RangeError);
  assert.throws(() => chooseMelodyTargets(42, [1], { blockTargetMode: 3 }), RangeError);
});

test('P5e extended target directions and variation match 4608 production Java cases', () => {
  const { load } = fixture();
  const { chooseMelodyTargets } = load('core/music/melody-targets.ts');
  const oracle = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/melody-targets-extended.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(oracle.length, 4608);
  for (const [index, sample] of oracle.entries()) assert.deepEqual(chooseMelodyTargets(sample.seed, sample.progression, sample.globalSettings), sample.targets, `case ${index}`);
});

test('P5e catalogue selection matches 4500 production Java shapes and subsequent random draws', () => {
  const { load } = fixture();
  const { chooseCatalogueBlock } = load('core/music/melody-catalogue.ts'), { JavaRandom } = load('core/music/java-random.ts');
  const oracle = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/melody-catalogue.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(oracle.length, 4500);
  for (const [index, entry] of oracle.entries()) {
    const random = new JavaRandom(BigInt(entry.seed));
    const result = chooseCatalogueBlock(entry.change, entry.jump, random, entry.length ?? undefined,
      entry.variance, entry.directions, entry.preference, entry.weights);
    assert.deepEqual(result.notes, entry.notes, `case ${index} notes`);
    assert.equal(result.type, entry.type, `case ${index} type`);
    assert.equal(random.nextInt(), entry.nextInt, `case ${index} stream position`);
  }
});

test('P5e target preparation preserves current notes until events, locks, local ownership and MIDI replay', () => {
  const { service, phrase, midi, load } = fixture();
  service.updateTrackGenerationPolicy('melody', { onCompose: false });
  const primary = 'track-melody-1', duplicate = service.duplicateTrack(primary);
  service.setTrackRerollLock(duplicate, true);
  service.updateSettings({ seed: '9007199254740993' });
  const track = id => service.project().tracks.find(item => item.id === id);
  service.setSectionTrackPresence(0, primary, true);
  const local = { kind: 'section-track', sectionId: service.project().arrangement[1].id, trackId: primary };
  service.freezePartSettings(local);
  const initial = service.project(), original = phrase.generateTrackPhrase(initial, track(primary));
  const inputs = { targetNoteVariation: 6, targetDirection: 'DESC', useDirectionsFromProgression: true };
  assert.equal(service.updateMelodyGlobal(inputs), 'changed');
  assert.strictEqual(service.project().tracks, initial.tracks);
  assert.deepEqual(phrase.generateTrackPhrase(service.project(), track(primary)), original);
  const before = service.exportProjectJson(), arrangement = service.project().arrangement;
  const { chooseMelodyTargets } = load('core/music/melody-targets.ts');
  const targetSeed = ((track(primary).generatorSettings.patternSeed || Number(BigInt.asIntN(32, BigInt(service.project().seed)))) + 1) | 0;
  const expected = chooseMelodyTargets(targetSeed, service.project().progression, inputs);
  const locked = track(duplicate);
  assert.equal(service.generateComposition('compose'), 'changed');
  assert.deepEqual(track(primary).generatorSettings.chordNoteChoices, expected);
  assert.strictEqual(track(duplicate), locked);
  assert.strictEqual(service.project().arrangement, arrangement);
  assert.notDeepEqual(phrase.generateTrackPhrase(service.project(), track(primary)), original);
  const after = service.exportProjectJson(), notes = phrase.layOutTrackPhrase(service.project(), track(primary));
  const exported = new Midi(midi.generateCompositionMidi(service.project()));
  const melody = exported.tracks.find(item => item.name === track(primary).name);
  assert.equal(melody.notes.length, notes.length);
  melody.notes.forEach((note, index) => {
    assert.equal(note.midi, notes[index].midi);
    assert.ok(Math.abs(note.ticks / exported.header.ppq - notes[index].startBeat) < 0.003);
  });
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.generateComposition('compose'); assert.equal(service.exportProjectJson(), after);
  assert.equal(service.importProjectJson(after), true);
  const { ProjectService } = load('core/project/project.service.ts');
  assert.deepEqual(projectData(new ProjectService().exportProjectJson()), projectData(after));
  service.updateTrackGenerationPolicy('melody', { targetsOnCompose: false, patternsOnCompose: false, rerollSeeds: false, onRegenerate: true });
  const disabled = service.exportProjectJson();
  assert.equal(service.generateComposition('regenerate'), 'unchanged');
  assert.equal(service.exportProjectJson(), disabled);
  service.updateTrackGenerationPolicy('melody', { targetsOnCompose: true });
  service.updateMelodyGlobal({ targetDirection: 'ANY', targetNoteVariation: 1, useDirectionsFromProgression: false });
  assert.equal(service.generateComposition('regenerate'), 'changed');
  assert.deepEqual(track(primary).generatorSettings.chordNoteChoices,
    chooseMelodyTargets(targetSeed, service.project().progression, service.project().melodyGlobal));
});

test('P5e forced outline follows the first stored Melody and only copies the production outline fields', () => {
  const { service, phrase } = fixture();
  for (const role of ['melody', 'bass', 'chords', 'arpeggio', 'drums']) service.updateTrackGenerationPolicy(role, { onCompose: false });
  service.updateSettings({ randomizeTransposeOnCompose: false, randomizeTempoOnCompose: false });
  const primary = 'track-melody-1', second = service.duplicateTrack(primary), third = service.duplicateTrack(second);
  const sourceFields = { chordNoteChoices: [1, -2, 4], melodyPatternOffsets: [1, -1, 2], speed: 0,
    maxBlockChange: 2, blockJump: 3, maxNoteExceptions: 4, doubledRhythmChance: 100, splitChance: 40, noteExceptionChance: 70 };
  service.updatePartSettings({ kind: 'global-track', trackId: primary }, sourceFields);
  service.updatePartSettings({ kind: 'global-track', trackId: second }, { speed: 100, leadChordsChance: 80, startNoteChance: 0,
    transpose: 7, patternSeed: 456, patternFlexible: false, pauseChance: 40, noteLengthMultiplier: 75 });
  service.setTrackRerollLock(primary, true); service.setTrackRerollLock(third, true);
  service.updateTrackPhrase(second, [{ id: 'manual-force', midi: 72, startBeat: 0, durationBeats: 1, velocity: 90 }]);
  service.updatePartSettings({ kind: 'section-track', sectionId: service.project().arrangement[0].id, trackId: second }, { speed: 50 });
  service.updateTrackGenerationPolicy('melody', { targetsOnCompose: false, patternsOnCompose: false, rerollSeeds: false });
  const members = service.project().tracks, arrangement = service.project().arrangement;
  assert.equal(service.updateMelodyGlobal({ forceFirstOutline: true }), 'changed');
  assert.strictEqual(service.project().tracks, members);
  const before = service.exportProjectJson();
  assert.equal(service.generateComposition('compose'), 'changed');
  const result = service.project().tracks.find(track => track.id === second);
  const previous = members.find(track => track.id === second);
  for (const [key, value] of Object.entries(sourceFields)) assert.deepEqual(result.generatorSettings[key], value);
  for (const [key, value] of Object.entries(previous.generatorSettings)) {
    if (!Object.hasOwn(sourceFields, key)) assert.deepEqual(result.generatorSettings[key], value, key);
  }
  assert.notStrictEqual(result.generatorSettings.chordNoteChoices, service.project().tracks.find(track => track.id === primary).generatorSettings.chordNoteChoices);
  for (const id of [primary, third]) assert.strictEqual(service.project().tracks.find(track => track.id === id), members.find(track => track.id === id));
  assert.strictEqual(service.project().arrangement, arrangement);
  assert.deepEqual(phrase.phraseForTrack(service.project(), result), previous.editedPhrase);
  const after = service.exportProjectJson();
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.redo(); assert.equal(service.exportProjectJson(), after);
  service.updateSettings({ name: 'redo' }); service.undo();
  assert.equal(service.generateComposition('compose'), 'unchanged');
  assert.equal(service.exportProjectJson(), after); assert.equal(service.canRedo(), true);
  assert.equal(service.generateComposition('regenerate'), 'unchanged', 'disabled event does not force outlines');
  service.removeTrack(primary);
  service.setTrackRerollLock(third, false);
  service.updatePartSettings({ kind: 'global-track', trackId: third }, { speed: 100 });
  assert.equal(service.generateComposition('compose'), 'changed');
  assert.equal(service.project().tracks.find(track => track.id === third).generatorSettings.speed, 0, 'next stored member becomes the source');
});

test('P5e empty preferred split-pitch ranges retain the in-range B without throwing', () => {
  const { load } = fixture(), { expandMelody } = load('core/music/melody-expansion.ts');
  let retained = false;
  for (let seed = 0; seed < 16; seed++) {
    const skeleton = [[{ midi: 70, rhythm: 2, duration: 1.8, velocity: 80, offset: 0 },
      { midi: 71, rhythm: 2, duration: 1.8, velocity: 80, offset: 0 }]];
    const notes = expandMelody(skeleton, { algorithm: 'block', splitChance: 100, accents: 0 }, seed, 0, 80, 105).flat();
    assert.ok(notes.every(note => Number.isInteger(note.midi) && Number.isFinite(note.duration)));
    assert.equal(notes.reduce((sum, note) => sum + note.rhythm, 0), 4);
    retained ||= notes[1].midi === 71;
  }
  assert.equal(retained, true);
});

test('history navigation preserves audition switches, redo branches and abbreviated diffs', () => {
  const { service } = fixture();
  const start = service.project().tempoBpm;
  const track = service.project().tracks[0];
  service.updateTrack(track.id, { mix: { muted: true } });
  assert.equal(service.canUndo(), false);
  service.updateSettings({ tempoBpm: 130 });
  service.updateSettings({ tempoBpm: 140 });
  assert.equal(service.historyEntries().length, 3);
  assert.ok(service.historyEntries()[2].changes.some(diff => diff.includes('Tempo Bpm: 130 → 140')));
  service.undo();
  service.toggleTrackMix([track.id], 'solo', true);
  assert.equal(service.canRedo(), true, 'audition does not discard redo');
  service.redo();
  assert.equal(service.project().tempoBpm, 140);
  assert.equal(service.project().tracks[0].mix.solo, true);
  assert.equal(service.project().tracks[0].mix.muted, false);
  service.goToHistory(0);
  assert.equal(service.project().tempoBpm, start);
  assert.equal(service.project().tracks[0].mix.solo, true);
  assert.equal(service.project().mix[track.role].solo, true);
  assert.equal(service.historyPosition(), 0);
  service.goToHistory(2);
  assert.equal(service.project().tempoBpm, 140);
  service.goToHistory(1);
  service.updateSettings({ tempoBpm: 150 });
  assert.equal(service.canRedo(), false);
  assert.equal(service.historyEntries().length, 3);
  service.goToHistory(-1); service.goToHistory(100); service.goToHistory(0.5);
  assert.equal(service.project().tempoBpm, 150);
  assert.ok(service.historyEntries().every(entry => entry.changes.every(diff => !/muted|solo/i.test(diff))));
  assert.equal(JSON.parse(service.exportProjectJson()).tracks[0].mix.solo, true);
});

test('mixed audition and volume patches undo only volume; compact history shows the final value', (t) => {
  t.mock.method(performance, 'now', () => 0);
  const { service } = fixture({ compactHistory: true });
  const track = service.project().tracks[0];
  const volume = track.mix.volumePercent;
  service.updateTrack(track.id, { mix: { muted: true, volumePercent: 42 } });
  service.undo();
  assert.equal(service.project().tracks[0].mix.volumePercent, volume);
  assert.equal(service.project().tracks[0].mix.muted, true);
  service.redo();
  assert.equal(service.project().tracks[0].mix.volumePercent, 42);
  service.updateSettings({ tempoBpm: 130 });
  service.toggleTrackMix([track.id], 'solo');
  service.updateSettings({ tempoBpm: 140 });
  assert.equal(service.historyEntries().length, 3);
  assert.ok(service.historyEntries()[2].changes.some(diff => diff.endsWith('→ 140')));
  service.goToHistory(0);
  assert.equal(service.project().tracks[0].mix.solo, true);
});

test('mute/solo controls clear the opposite state across track and legacy mix updates', () => {
  const { service } = fixture();
  const track = service.project().tracks[0];
  service.updateTrack(track.id, { mix: { muted: true } });
  service.updateTrack(track.id, { mix: { solo: true } });
  assert.equal(service.project().tracks[0].mix.muted, false);
  service.updateMixSettings(track.role, { muted: true });
  assert.equal(service.project().tracks[0].mix.solo, false);
  assert.deepEqual(service.project().mix[track.role], service.project().tracks[0].mix);
});

test('role/global mute and solo clear partial states without entering undo history', () => {
  const { service } = fixture();
  service.addTrack('bass');
  const ids = service.project().tracks.filter(track => track.role === 'bass').map(track => track.id);
  service.updateTrack(ids[0], { mix: { muted: true } });
  service.toggleTrackMix(ids, 'muted');
  assert.ok(service.project().tracks.filter(track => ids.includes(track.id)).every(track => !track.mix.muted));
  const position = service.historyPosition();
  service.toggleTrackMix(ids, 'solo');
  assert.ok(service.project().tracks.filter(track => ids.includes(track.id)).every(track => track.mix.solo && !track.mix.muted));
  assert.equal(service.historyPosition(), position);
  const all = service.project().tracks.map(track => track.id);
  service.toggleTrackMix(all, 'solo');
  assert.ok(service.project().tracks.every(track => !track.mix.solo));
  service.toggleTrackMix(all, 'muted');
  assert.ok(service.project().tracks.every(track => track.mix.muted && !track.mix.solo));
  service.toggleTrackMix(all, 'solo');
  assert.ok(service.project().tracks.every(track => track.mix.solo && !track.mix.muted));
  const unchanged = service.project();
  service.toggleTrackMix([], 'solo');
  assert.strictEqual(service.project(), unchanged);
});

test('middle-click solos only the selected track/group, unmutes it, and ignores other buttons', () => {
  const { service, load } = fixture();
  service.addTrack('bass');
  const bass = service.project().tracks.filter(track => track.role === 'bass').map(track => track.id);
  service.toggleTrackMix(service.project().tracks.map(track => track.id), 'muted');
  const { MixButtonsComponent } = load('shared/mix-buttons.component.ts');
  const buttons = new MixButtonsComponent();
  buttons.trackIds.set(bass); buttons.label.set('Bass tracks');
  let updates = 0, prevented = 0, stopped = 0;
  buttons.playback = { updateMix: () => updates++ };
  const click = button => ({ button, preventDefault: () => prevented++, stopPropagation: () => stopped++ });
  const before = service.exportProjectJson();
  buttons.exclusiveSolo(click(2)); assert.equal(service.exportProjectJson(), before);
  buttons.exclusiveSolo(click(1));
  assert.ok(service.project().tracks.every(track => track.mix.solo === bass.includes(track.id)));
  assert.ok(service.project().tracks.filter(track => bass.includes(track.id)).every(track => !track.mix.muted));
  assert.equal(buttons.state('solo'), 'true');
  buttons.trackIds.set([bass[0]]); buttons.exclusiveSolo(click(1));
  assert.deepEqual(service.project().tracks.filter(track => track.mix.solo).map(track => track.id), [bass[0]]);
  buttons.trackIds.set(bass); assert.equal(buttons.state('solo'), 'mixed');
  assert.equal(updates, 2); assert.equal(prevented, 2); assert.equal(stopped, 2);
  buttons.allowExclusive.set(false);
  const unchanged = service.project(); buttons.exclusiveSolo(click(1));
  assert.strictEqual(service.project(), unchanged);
});

test('drum pitch selection matches 480 cases extracted verbatim from production DrumGUI', () => {
  const { load } = fixture();
  const { chooseDrumPitches } = load('core/music/drum-reroll.ts');
  const { JavaRandom } = load('core/music/java-random.ts');
  const oracle = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/drum-reroll.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(oracle.pitches.length, 480);
  for (const sample of oracle.pitches) {
    assert.deepEqual(chooseDrumPitches(new JavaRandom(BigInt(sample.seed)), sample.count, sample.remaining, sample.onlyAdd), sample.result);
  }
});

test('drum group rerolls reserve a core kit, respect remaining drums, and retain patches/manual notes through MIDI and replay', () => {
  const { service, midi, load } = fixture();
  service.setRoleTrackCount('drums', 6);
  const tracks = () => service.project().tracks.filter(track => track.role === 'drums');
  service.updateTrackGeneratorSettings(tracks()[0].id, { pitch: 35 });
  service.setTrackRerollLock(tracks()[0].id, true);
  service.updateTrackGeneratorSettings(tracks()[1].id, { pitch: 40 });
  service.setTrackRerollLock(tracks()[1].id, true);
  const manualId = tracks()[2].id;
  service.updateTrackPhrase(manualId, [{ id: 'manual', midi: 82, startBeat: 0, durationBeats: 1, velocity: 90 }]);
  const sectionId = service.project().arrangement[0].id;
  service.freezePartSettings({ kind: 'section-track', sectionId, trackId: tracks()[3].id });
  const locked = tracks().slice(0, 2), arrangement = service.project().arrangement;
  const before = service.exportProjectJson();
  assert.equal(service.rerollRoleTracks('drums'), 'changed');
  assert.deepEqual(tracks().slice(0, 2), locked);
  assert.strictEqual(service.project().arrangement, arrangement);
  assert.ok(tracks().some(track => [35,36].includes(track.generatorSettings.pitch)));
  assert.ok(tracks().some(track => [38,40].includes(track.generatorSettings.pitch)));
  assert.ok(tracks().every(track => track.midiChannel === 10));
  const exported = new Midi(midi.generateCompositionMidi(service.project()));
  assert.equal(exported.tracks.find(track => track.name === tracks()[2].name).notes[0].midi, 82);
  const after = service.exportProjectJson(); service.undo(); assert.equal(service.exportProjectJson(), before);
  service.rerollRoleTracks('drums'); assert.equal(service.exportProjectJson(), after);
  assert.equal(service.importProjectJson(after), true);
  const restored = new (load('core/project/project.service.ts').ProjectService)();
  restored.rerollRoleTracks('drums'); service.rerollRoleTracks('drums'); assert.deepEqual(restored.project(), service.project());
  for (const track of tracks()) service.setTrackRerollLock(track.id, false);
  for (let roll = 0; roll < 30; roll++) {
    service.rerollRoleTracks('drums');
    const pitches = tracks().map(track => track.generatorSettings.pitch);
    assert.ok(pitches.includes(35) && pitches.includes(36));
    assert.equal(pitches.filter(pitch => [38,40].includes(pitch)).length, 1);
  }
  service.updateInstrumentPolicy('drums', { randomize: false });
  const pitches = tracks().map(track => track.generatorSettings.pitch);
  service.rerollRoleTracks('drums'); assert.deepEqual(tracks().map(track => track.generatorSettings.pitch), pitches);
});

test('drum additions fill missing core voices without modifying existing tracks and reuse existing swing', () => {
  const { service, load } = fixture();
  const core = load('core/music/track-generation.ts');
  service.setRoleTrackCount('drums', 0);
  service.generateRoleTracks('drums', 1);
  const first = service.project().tracks.find(track => track.role === 'drums');
  assert.ok([35,36].includes(first.generatorSettings.pitch));
  service.generateRoleTracks('drums', 1);
  const drums = service.project().tracks.filter(track => track.role === 'drums');
  assert.strictEqual(drums[0], first); assert.ok([38,40].includes(drums[1].generatorSettings.pitch));
  const source = { ...first, generatorSettings: { ...first.generatorSettings, pitch: 42 } };
  const context = { onlyAdd: true, totalTracks: 4, remaining: [{ ...source.generatorSettings, swingPercent: 67 }] };
  const [chosen] = core.rerollRoleSettings(42n, 'drums', core.DEFAULT_TRACK_POLICIES.drums, [source], [3], context);
  assert.equal(chosen.swingPercent, 67);
});

test('drum blueprint baking keeps one hit, stores growth as CUSTOM and applies family timing/dynamics', () => {
  const { service, load } = fixture();
  const core = load('core/music/track-generation.ts'), { bakeDrumPattern } = load('core/music/drum-reroll.ts');
  const { JavaRandom } = load('core/music/java-random.ts');
  const { partRhythmMask } = load('core/music/rhythm-pattern.ts');
  const source = service.project().tracks.find(track => track.role === 'drums');
  const paused = bakeDrumPattern(new JavaRandom(42n), { ...source.generatorSettings, rhythm: 'full', hitsPerPattern: 8, pauseChance: 100 });
  assert.equal(paused.pauseChance, 0); assert.equal(paused.rhythm, 'custom');
  assert.equal(partRhythmMask(paused).filter(Boolean).length, 1);
  const families = [[35,65,99,false], [37,50,74,false], [38,65,99,false], [42,40,74,true], [46,50,84,false], [53,50,84,false], [60,40,74,true]];
  let grown = false, sparseDense = false;
  for (let sample = 0n; sample < 120n; sample++) {
    const seed = BigInt.asIntN(64, sample * 0x9e3779b97f4a7c15n);
    for (const [pitch, min, max, swingable] of families) {
      const track = { ...source, generatorSettings: { ...source.generatorSettings, pitch, feedbackCount: 3, noteLengthMultiplier: 170 } };
      const policy = { ...core.DEFAULT_TRACK_POLICIES.drums, maxSwing: 0, randomOffset: true, melodyChance: 0 };
      const [chosen] = core.rerollRoleSettings(seed, 'drums', policy, [track], [1], { totalTracks: 12 });
      assert.equal(chosen.velocityMin, min); assert.equal(chosen.velocityMax, max);
      assert.equal(chosen.feedbackCount, 0); assert.equal(chosen.noteLengthMultiplier, 100);
      assert.equal(chosen.patternSeed, 0); assert.equal(chosen.pauseChance, 0); assert.equal(chosen.swingPercent, 50);
      if (!swingable) assert.equal(chosen.offset, 0);
      assert.ok(partRhythmMask(chosen).some(Boolean));
      if (pitch === 38 && chosen.rhythm === 'custom') grown = true;
      if (pitch === 42 && chosen.rhythm === 'half' && chosen.chordSpan === 2) sparseDense = true;
    }
  }
  assert.equal(grown, true); assert.equal(sparseDense, true);
});

test('main rerolls randomize instruments by default for every role and preserve explicit opt-outs', () => {
  for (const role of ['melody', 'bass', 'chords', 'arpeggio', 'drums']) {
    const { service, workspace, load } = fixture();
    const id = `track-${role}-1`;
    const track = () => service.project().tracks.find(item => item.id === id);
    assert.equal(workspace.instrumentPolicyFor(role).randomize, true);
    if (role === 'drums') service.updateTrackGeneratorSettings(id, { pitch: 82 });
    else service.updateTrack(id, { mix: { program: 127 } });
    const before = service.exportProjectJson();
    workspace.rerollRole(role, id);
    const value = role === 'drums' ? track().generatorSettings.pitch : track().mix.program;
    assert.notEqual(value, role === 'drums' ? 82 : 127);
    const after = service.exportProjectJson();
    service.undo(); assert.equal(service.exportProjectJson(), before);
    workspace.rerollRole(role, id); assert.equal(service.exportProjectJson(), after);
    service.updateInstrumentPolicy(role, { randomize: false });
    const program = track().mix.program, pitch = track().generatorSettings.pitch;
    workspace.rerollRole(role);
    assert.equal(track().mix.program, program);
    if (role === 'drums') assert.equal(track().generatorSettings.pitch, pitch);
    assert.equal(service.importProjectJson(service.exportProjectJson()), true);
    const restored = new (load('core/project/project.service.ts').ProjectService)();
    assert.equal(restored.project().instrumentGenerationPolicies[role].randomize, false);
  }
});

test('Arp direction covers every selection and Bass randomized variance stays within 0–50 without limiting manual edits', () => {
  const { service, load } = fixture();
  const core = load('core/music/track-generation.ts');
  const arp = service.project().tracks.find(track => track.role === 'arpeggio');
  const bass = service.project().tracks.find(track => track.role === 'bass');
  const directions = new Set(), variances = new Set();
  for (let sample = 0n; sample < 300n; sample++) {
    // Use the production counter stride; adjacent Java seeds correlate on their first draw.
    const seed = BigInt.asIntN(64, sample * 0x9e3779b97f4a7c15n);
    directions.add(core.rerollRoleSettings(seed, 'arpeggio', core.DEFAULT_TRACK_POLICIES.arpeggio, [arp], [1])[0].pattern);
    for (const order of [1, 2]) {
      const result = core.rerollRoleSettings(seed, 'bass', {}, [bass], [order])[0];
      assert.ok(Number.isInteger(result.noteVariation) && result.noteVariation >= 0 && result.noteVariation <= 50);
      variances.add(result.noteVariation);
    }
  }
  assert.deepEqual([...directions].sort(), ['down', 'down-up', 'frog-down', 'frog-up', 'random', 'up', 'up-down']);
  assert.ok(variances.has(0) && variances.has(50));
  service.updatePartSettings({ kind: 'global-role', role: 'bass' }, { noteVariation: 100 });
  assert.equal(service.project().bass.noteVariation, 100);
  const second = service.addTrack('bass'), sectionId = service.project().arrangement[0].id;
  service.updatePartSettings({ kind: 'section-track', sectionId, trackId: second }, { noteVariation: 90 });
  service.setTrackRerollLock(second, true);
  const previous = service.exportProjectJson();
  service.rerollRoleTracks('bass');
  assert.ok(service.project().bass.noteVariation <= 50);
  assert.equal(service.project().tracks.find(track => track.id === second).generatorSettings.noteVariation, 100);
  assert.equal(service.project().arrangement[0].trackPartOverrides[second].noteVariation, 90);
  const after = service.exportProjectJson(); service.undo(); assert.equal(service.exportProjectJson(), previous);
  service.rerollRoleTracks('bass'); assert.equal(service.exportProjectJson(), after);
  service.generateRoleTracks('bass', 3);
  assert.ok(service.project().tracks.slice(-3).every(track => track.generatorSettings.noteVariation <= 50));
});

test('instrument pool catalogues retain production Java membership and order', () => {
  const { load } = fixture();
  const { INSTRUMENT_POOLS } = load('core/music/instrument-generation.ts');
  const java = fs.readFileSync(path.resolve(__dirname, '../../../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/InstUtils.java'), 'utf8');
  for (const [pool, symbol] of Object.entries({ ALL: 'ALL', MELODY: 'MELODY', BASS: 'BASS', CHORD: 'CHORD', PLUCK: 'PLUCK', LONG_PAD: 'LONG', DRUM: 'DRUM' })) {
    const declaration = java.match(new RegExp(symbol + '_INST_NAMES\\s*=\\s*\\{([\\s\\S]*?)\\};'))[1];
    assert.deepEqual(INSTRUMENT_POOLS[pool], [...declaration.matchAll(/"(\d+):/g)].map(match => Number(match[1])));
  }
});

for (const role of ['melody', 'bass', 'chords', 'arpeggio', 'drums']) {
  test(`${role} instrument-only reroll preserves ownership, locks, exact replay and MIDI identity`, () => {
    const { service, load, midi } = fixture();
    const { INSTRUMENT_POOLS, DEFAULT_INSTRUMENT_POLICIES } = load('core/music/instrument-generation.ts');
    const id = `track-${role}-1`, second = service.addTrack(role), locked = service.addTrack(role);
    const track = id => service.project().tracks.find(item => item.id === id);
    service.updateSettings({ seed: '9007199254740993' });
    service.setTrackInstrumentLock(second, true); service.setTrackRerollLock(locked, true);
    service.updateTrackPhrase(id, [{ id: 'manual', midi: role === 'drums' ? 36 : 60, startBeat: 0, durationBeats: 1, velocity: 90 }]);
    service.setSectionTrackPresence(0, id, true); // Ensure MIDI has a note event from which its parser reads the channel.
    service.freezePartSettings({ kind: 'section-track', sectionId: service.project().arrangement[0].id, trackId: id });
    const before = service.exportProjectJson(), previous = track(id), arrangement = service.project().arrangement;
    const previousSecond = track(second), previousLocked = track(locked);
    const result = service.rerollRoleInstruments(role);
    assert.equal(result, 'changed');
    const after = service.exportProjectJson();
    assert.strictEqual(track(second), previousSecond);
    assert.strictEqual(track(locked), previousLocked);
    assert.strictEqual(service.project().arrangement, arrangement);
    for (const key of ['id', 'name', 'midiChannel', 'editedPhrase']) assert.deepEqual(track(id)[key], previous[key]);
    assert.deepEqual({ ...track(id).mix, program: previous.mix.program }, previous.mix);
    assert.deepEqual({ ...track(id).generatorSettings, ...(role === 'drums' ? { pitch: previous.generatorSettings.pitch } : {}) }, previous.generatorSettings);
    const pool = DEFAULT_INSTRUMENT_POLICIES[role].pool;
    const choices = pool === 'AUTO' ? [...INSTRUMENT_POOLS.CHORD, ...INSTRUMENT_POOLS.PLUCK] : INSTRUMENT_POOLS[pool];
    assert.ok(choices.includes(role === 'drums' ? track(id).generatorSettings.pitch : track(id).mix.program));
    const exported = new Midi(midi.generateCompositionMidi(service.project())).tracks.find(item => item.name === previous.name);
    assert.equal(exported.channel, previous.midiChannel - 1);
    if (role !== 'drums') assert.equal(exported.instrument.number, track(id).mix.program);
    assert.equal(exported.notes[0].midi, previous.editedPhrase[0].midi);
    service.undo(); assert.equal(service.exportProjectJson(), before);
    assert.equal(service.rerollRoleInstruments(role), 'changed'); assert.equal(service.exportProjectJson(), after);
    service.undo(); service.redo(); assert.equal(service.exportProjectJson(), after);
    assert.equal(service.importProjectJson(after), true);
    const restored = new (load('core/project/project.service.ts').ProjectService)();
    assert.deepEqual(restored.project().tracks, service.project().tracks);
    assert.equal(restored.rerollRoleInstruments(role), service.rerollRoleInstruments(role));
    assert.deepEqual(restored.project(), service.project());
  });
}

test('instrument permission is independent of parameter streams and shared instruments exclude locks', () => {
  const { service, load } = fixture();
  const core = load('core/music/instrument-generation.ts');
  const first = 'track-arpeggio-1', second = service.addTrack('arpeggio'), locked = service.addTrack('arpeggio');
  service.setTrackInstrumentLock(locked, true);
  const previous = service.project(), bases = previous.tracks.filter(track => track.role === 'arpeggio');
  assert.equal(service.updateInstrumentPolicy('arpeggio', { randomize: true, sameInstrument: true }), 'changed');
  assert.deepEqual(service.project().tracks, previous.tracks);
  service.rerollRoleTracks('arpeggio');
  const enabled = service.project().tracks.filter(track => track.role === 'arpeggio');
  assert.equal(enabled[0].mix.program, enabled[1].mix.program);
  assert.equal(enabled[2].mix.program, bases[2].mix.program);
  service.undo(); service.updateInstrumentPolicy('arpeggio', { randomize: false }); service.rerollRoleTracks('arpeggio');
  const disabled = service.project().tracks.filter(track => track.role === 'arpeggio');
  assert.deepEqual(disabled.map(track => track.generatorSettings), enabled.map(track => track.generatorSettings));
  assert.deepEqual(disabled.map(track => track.mix.program), bases.map(track => track.mix.program));
  service.updateInstrumentPolicy('arpeggio', { randomize: true });
  const existing = service.project().tracks;
  service.generateRoleTracks('arpeggio', 3);
  const created = service.project().tracks.slice(existing.length);
  assert.equal(new Set(created.map(track => track.mix.program)).size, 1);
  assert.ok(created.every(track => !track.instrumentLocked));
  assert.deepEqual(service.project().tracks.slice(0, existing.length), existing);
  for (const chance of [0, 100]) {
    const results = core.rerollInstruments(123n, 'chords', { ...core.DEFAULT_INSTRUMENT_POLICIES.chords, sustainChance: chance },
      [service.project().tracks.find(track => track.role === 'chords')], true);
    assert.ok(core.INSTRUMENT_POOLS[chance === 0 ? 'PLUCK' : 'CHORD'].includes(results[0].mix.program));
  }
  service.setTrackInstrumentLock(first, true); service.setTrackRerollLock(second, true);
  service.updateSettings({ name: 'redo' }); service.undo();
  const before = service.exportProjectJson();
  assert.equal(service.rerollRoleInstruments('arpeggio', first), 'unchanged');
  assert.equal(service.rerollRoleInstruments('arpeggio', 'deleted'), 'unchanged');
  assert.equal(service.exportProjectJson(), before); assert.equal(service.canRedo(), true);
});

test('instrument rules and locks strictly validate and persist without schema gates', () => {
  const { service, load } = fixture();
  const before = service.project();
  assert.equal(before.instrumentGenerationPolicies, undefined);
  for (const role of ['melody', 'bass', 'chords', 'arpeggio', 'drums']) service.updateInstrumentPolicy(role, { randomize: false });
  service.setTrackInstrumentLock('track-bass-1', true);
  const saved = service.exportProjectJson();
  assert.equal(service.importProjectJson(saved), true);
  assert.deepEqual(new (load('core/project/project.service.ts').ProjectService)().project(), service.project());
  service.updateSettings({ name: 'redo' }); service.undo();
  const snapshot = service.exportProjectJson();
  for (const [role, patch] of [['bass', { randomize: 1 }], ['drums', { pool: 'ALL' }], ['melody', { pool: 'DRUM' }],
    ['arpeggio', { sameInstrument: 'yes' }], ['bass', { sameInstrument: true }], ['chords', { sustainChance: 101 }], ['chords', { sustainChance: 1.5 }]]) {
    assert.equal(service.updateInstrumentPolicy(role, patch), 'invalid');
    const project = JSON.parse(snapshot); Object.assign(project.instrumentGenerationPolicies[role], patch);
    assert.equal(service.importProjectJson(JSON.stringify(project)), false);
  }
  for (const value of [null, [], { unknown: {} }]) {
    const project = JSON.parse(snapshot); project.instrumentGenerationPolicies = value;
    assert.equal(service.importProjectJson(JSON.stringify(project)), false);
  }
  const project = JSON.parse(snapshot); project.tracks[0].instrumentLocked = 'yes';
  assert.equal(service.importProjectJson(JSON.stringify(project)), false);
  for (const pool of [['MELODY'], 'AUTO', 'DRUM', 'unknown']) {
    const project = JSON.parse(snapshot); project.tracks[0].instrumentPool = pool;
    assert.equal(service.importProjectJson(JSON.stringify(project)), false);
  }
  assert.equal(service.exportProjectJson(), snapshot); assert.equal(service.canRedo(), true);
});

test('combined instrument and parameter rerolls reject effective conflicts without consuming history or counters', () => {
  const { service } = fixture();
  service.updateInstrumentPolicy('chords', { randomize: true });
  service.updatePartSettings({ kind: 'global-role', role: 'chords' }, { velocityMax: 127 });
  service.updatePartSettings({ kind: 'section-role', role: 'chords', sectionId: service.project().arrangement[0].id }, { velocityMin: 120 });
  service.updateSettings({ name: 'redo' }); service.undo();
  const before = service.exportProjectJson();
  assert.equal(service.rerollRoleTracks('chords'), 'invalid');
  assert.equal(service.exportProjectJson(), before); assert.equal(service.canRedo(), true);
});

test('chord pool identity drives creation branches and manual instrument edits release it', () => {
  const { service, load } = fixture();
  const instruments = load('core/music/instrument-generation.ts'), policies = load('core/music/track-generation.ts');
  const source = service.project().tracks.find(track => track.role === 'chords');
  const long = instruments.rerollInstruments(23n, 'chords', { ...instruments.DEFAULT_INSTRUMENT_POLICIES.chords, pool: 'LONG_PAD' }, [source], true)[0];
  assert.equal(long.instrumentPool, 'LONG_PAD');
  const settings = policies.rerollRoleSettings(23n, 'chords', policies.DEFAULT_TRACK_POLICIES.chords, [long], [1])[0];
  assert.equal(settings.rhythm, 'single'); assert.equal(settings.chordSpanFill, 'ALL');
  const pluck = { ...source, mix: { ...source.mix, program: 32 }, instrumentPool: 'PLUCK' };
  const chosen = policies.rerollRoleSettings(1n, 'chords', policies.DEFAULT_TRACK_POLICIES.chords, [pluck], [1])[0];
  assert.ok(chosen.noteLengthPercent >= 50 && chosen.noteLengthPercent <= 75);
  service.updateInstrumentPolicy('chords', { pool: 'LONG_PAD', randomize: true });
  assert.equal(service.rerollRoleTracks('chords'), 'changed');
  assert.equal(service.project().tracks.find(track => track.id === source.id).instrumentPool, 'LONG_PAD');
  service.updateTrack(source.id, { mix: { program: 127 } });
  assert.equal(service.project().tracks.find(track => track.id === source.id).instrumentPool, undefined);
  assert.equal(service.importProjectJson(service.exportProjectJson()), true);
  service.updateInstrumentPolicy('bass', { randomize: true });
  service.setTrackInstrumentLock('track-bass-1', true);
  const bass = service.project().tracks.find(track => track.role === 'bass');
  service.rerollRoleTracks('bass');
  const next = service.project().tracks.find(track => track.role === 'bass');
  assert.equal(next.mix.program, bass.mix.program); assert.notDeepEqual(next.generatorSettings, bass.generatorSettings);
});

test('role rerolls match production melody patterns, drum blueprint data and audited seeded BassGUI branches', () => {
  const { service, load } = fixture();
  const core = load('core/music/track-generation.ts');
  const { STATIC_RHYTHM_DEFINITIONS } = load('core/music/rhythm-patterns.ts');
  const names = Object.fromEntries(Object.entries(STATIC_RHYTHM_DEFINITIONS).map(([key, value]) => [value.javaName, key]));
  names.CUSTOM = 'custom';
  const expected = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/role-policy.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  for (const sample of expected.melodyPatterns) assert.deepEqual(core.chooseMelodyPattern(sample.seed), sample.pattern);
  for (const sample of expected.bass) {
    const source = { ...service.project().tracks.find(track => track.role === 'bass'), generatorSettings: { ...service.project().bass, chordSpan: sample.span } };
    const [actual] = core.rerollRoleSettings(BigInt(sample.seed), 'bass', {}, [source], [sample.order]);
    for (const [key, value] of Object.entries(sample.values)) {
      if (key === 'javaRhythm') assert.equal(actual.rhythm, names[value]);
      else assert.deepEqual(actual[key], key === 'velocityMax' ? Math.max(value - 1, sample.values.velocityMin) : value);
    }
  }
  [36,38,42,53,60].forEach((pitch, family) => {
    const seen = new Set();
    for (let seed = 0; seed < 100; seed++) {
      const source = { ...service.project().tracks.find(track => track.role === 'drums'), generatorSettings: { ...service.project().drums, pitch } };
      const [actual] = core.rerollRoleSettings(BigInt(seed), 'drums', { ...core.DEFAULT_TRACK_POLICIES.drums, shiftChance: 0, melodyChance: 0 }, [source], [1]);
      const tuple = [actual.rhythm, actual.hitsPerPattern, actual.chordSpan, actual.patternShift];
      const blueprints = expected.drums[family].map(([rhythm, ...values]) => [names[rhythm], ...values]);
      const candidates = blueprints.filter(candidate => candidate.slice(1).every((value, index) => value === tuple[index + 1])
        && (actual.rhythm === candidate[0] || actual.rhythm === 'custom'));
      assert.ok(candidates.length, 'baking retains the family blueprint hits/span/shift');
      candidates.forEach(candidate => seen.add(JSON.stringify(candidate)));
      if (actual.rhythm === 'custom') assert.equal(actual.customPattern.length, 32);
    }
    assert.equal(seen.size, new Set(expected.drums[family].map(tuple => JSON.stringify(tuple))).size, 'all blueprint variants exercised');
  });
});

test('other role rules leave musical values untouched and strictly validate persisted metadata', () => {
  const { service, load, phrase } = fixture();
  const core = load('core/music/track-generation.ts');
  for (const [role, patch] of Object.entries({ chords: { varyLength: false, voicesMode: 'FIXED', voices: 6 },
    drums: { maxSwing: 0, hitsMultiplier: '2' }, melody: { sameSeed: true, patterns: false } })) {
    const track = service.project().tracks.find(track => track.role === role);
    const before = phrase.layOutTrackPhrase(service.project(), track);
    assert.equal(service.updateTrackGenerationPolicy(role, patch), 'changed');
    assert.strictEqual(service.project().tracks.find(item => item.id === track.id), track);
    assert.deepEqual(phrase.layOutTrackPhrase(service.project(), track), before);
  }
  assert.equal(service.project().trackRerollCounts, undefined);
  const saved = service.exportProjectJson();
  assert.equal(service.importProjectJson(saved), true);
  const restored = new (load('core/project/project.service.ts').ProjectService)();
  assert.deepEqual(restored.project().trackGenerationPolicies, service.project().trackGenerationPolicies);
  service.updateSettings({ name: 'redo' }); service.undo();
  const before = service.exportProjectJson();
  for (const [role, patch] of [['bass', { patterns: true }], ['chords', { velocityMin: 100, velocityMax: 90 }],
    ['chords', { voices: 2 }], ['chords', { strum: 1 }], ['drums', { maxSwing: 51 }], ['drums', { hitsMultiplier: '3' }],
    ['drums', { ghosts: true }], ['melody', { sameSeed: 1 }], ['melody', { targets: true }]]) {
    assert.equal(service.updateTrackGenerationPolicy(role, patch), 'invalid');
    const project = JSON.parse(before);
    project.trackGenerationPolicies = { [role]: { ...core.DEFAULT_TRACK_POLICIES[role], ...patch } };
    assert.equal(service.importProjectJson(JSON.stringify(project)), false);
  }
  for (const role of ['bass', 'chords', 'drums', 'melody']) {
    for (const value of [-1, 2147483648, '1', null]) {
      const project = JSON.parse(before); project.trackRerollCounts = { [role]: value };
      assert.equal(service.importProjectJson(JSON.stringify(project)), false);
    }
  }
  assert.equal(service.exportProjectJson(), before); assert.equal(service.canRedo(), true);
});

for (const role of ['bass', 'chords', 'drums', 'melody']) {
  test(`${role} rerolls preserve identity, manual notes, locks, local patches and exact replay`, () => {
    const { service, phrase, settings, midi, workspace, ui, load } = fixture();
    service.updateInstrumentPolicy(role, { randomize: false });
    const primaryId = `track-${role}-1`, manualId = service.addTrack(role), lockedId = service.addTrack(role);
    const track = id => service.project().tracks.find(track => track.id === id);
    service.updateSettings({ seed: '9007199254740993' });
    const section = service.project().arrangement[0];
    service.updatePartSettings({ kind: 'section-role', sectionId: section.id, role }, { velocityMin: 45, velocityMax: 95 });
    service.freezePartSettings({ kind: 'section-track', sectionId: section.id, trackId: primaryId });
    const effective = settings.resolvePartTrack(track(primaryId), service.project().arrangement[0]);
    const manual = [{ id: 'manual', midi: role === 'drums' ? 36 : 60, startBeat: 0, durationBeats: 1, velocity: 90 }];
    service.updateTrackPhrase(manualId, manual);
    service.setTrackRerollLock(lockedId, true);
    const before = service.exportProjectJson(), others = service.project().tracks.filter(track => track.role !== role);
    const locked = track(lockedId), arrangement = service.project().arrangement;
    assert.equal(service.rerollRoleTracks(role), 'changed');
    const after = service.exportProjectJson();
    assert.equal(service.project().trackRerollCounts[role], 1);
    assert.deepEqual(service.project().tracks.filter(track => track.role !== role), others);
    assert.strictEqual(track(lockedId), locked); assert.strictEqual(service.project().arrangement, arrangement);
    assert.deepEqual(phrase.phraseForTrack(service.project(), track(manualId)), manual);
    for (const id of [primaryId, manualId]) {
      const previous = JSON.parse(before).tracks.find(track => track.id === id);
      for (const key of ['id', 'name', 'mix', 'midiChannel', 'editedPhrase']) assert.deepEqual(track(id)[key], previous[key]);
      if (role === 'drums') assert.equal(track(id).generatorSettings.pitch, previous.generatorSettings.pitch);
    }
    assert.deepEqual(settings.resolvePartTrack(track(primaryId), service.project().arrangement[0]).generatorSettings, effective.generatorSettings);
    ui.selectTrack(primaryId);
    const field = role === 'melody' ? 'speed' : role === 'drums' ? 'hitsPerPattern' : 'velocityMax';
    assert.equal(workspace.partValues()[field], track(primaryId).generatorSettings[field]);
    service.undo(); assert.equal(service.exportProjectJson(), before);
    assert.equal(service.rerollRoleTracks(role), 'changed'); assert.equal(service.exportProjectJson(), after);
    service.undo(); service.redo(); assert.equal(service.exportProjectJson(), after);
    assert.equal(service.importProjectJson(service.exportProjectJson()), true);
    const restored = new (load('core/project/project.service.ts').ProjectService)();
    assert.equal(service.rerollRoleTracks(role, primaryId), 'changed');
    assert.equal(restored.rerollRoleTracks(role, primaryId), 'changed');
    assert.deepEqual(JSON.parse(restored.exportProjectJson()), JSON.parse(service.exportProjectJson()));
    assert.equal(service.importProjectJson(service.exportProjectJson()), true);
    const parsed = new Midi(midi.generateCompositionMidi(service.project()));
    assert.equal(parsed.tracks.find(item => item.name === track(primaryId).name).notes.length,
      phrase.layOutTrackPhrase(service.project(), track(primaryId)).length);
    const policies = service.project().trackGenerationPolicies, counts = service.project().trackRerollCounts;
    const bases = service.project().tracks.map(track => track.generatorSettings);
    service.randomizeArrangementPresence();
    assert.deepEqual(service.project().tracks.map(track => track.generatorSettings), bases);
    assert.deepEqual(service.project().trackGenerationPolicies, policies); assert.deepEqual(service.project().trackRerollCounts, counts);
  });

  test(`${role} conflicts and unavailable reroll targets preserve history and counters`, () => {
    const { service } = fixture();
    const id = `track-${role}-1`, sectionId = service.project().arrangement[0].id;
    service.updatePartSettings({ kind: 'global-role', role }, { velocityMax: 127 });
    service.updatePartSettings({ kind: 'section-role', sectionId, role }, { velocityMin: 120 });
    service.updateSettings({ name: 'redo' }); service.undo(); const before = service.exportProjectJson();
    assert.equal(service.rerollRoleTracks(role), 'invalid');
    assert.equal(service.exportProjectJson(), before); assert.equal(service.canRedo(), true);
    assert.equal(service.rerollRoleTracks(role, 'deleted'), 'unchanged');
    assert.equal(service.rerollRoleTracks(role, 'track-arpeggio-1'), 'unchanged');
    service.setTrackRerollLock(id, true); const locked = service.exportProjectJson();
    assert.equal(service.rerollRoleTracks(role), 'unchanged'); assert.equal(service.exportProjectJson(), locked);
  });
}

test('role-specific policies preserve opt-outs and share only eligible seeds and swing', () => {
  const { service, load } = fixture();
  const core = load('core/music/track-generation.ts');
  const tracks = role => service.project().tracks.filter(track => track.role === role);
  const source = { ...tracks('chords')[0], generatorSettings: { ...service.project().chords,
    rhythm: 'custom', patternFlip: true, patternShift: 4, customPattern: Array(32).fill(1),
    useCustomVelocities: true, chordSpanFill: 'HALF1', fillFlip: true, transpose: 24, offset: 125, noteLengthPercent: 150 } };
  const policy = { ...core.DEFAULT_TRACK_POLICIES.chords, fills: false, patterns: false, varyLength: false, transpose: false, delay: false };
  const [result] = core.rerollRoleSettings(42n, 'chords', policy, [source], [1]);
  for (const key of ['rhythm','patternFlip','patternShift','customPattern','useCustomVelocities','chordSpanFill','fillFlip','transpose','offset','noteLengthPercent'])
    assert.deepEqual(result[key], source.generatorSettings[key]);
  service.addTrack('melody'); service.addTrack('melody');
  service.updateTrackGenerationPolicy('melody', { sameSeed: true, patterns: false });
  service.setTrackRerollLock(tracks('melody')[0].id, true);
  const lockedSeed = tracks('melody')[0].generatorSettings.patternSeed;
  service.rerollRoleTracks('melody');
  assert.equal(tracks('melody')[0].generatorSettings.patternSeed, lockedSeed);
  assert.equal(tracks('melody')[1].generatorSettings.patternSeed, tracks('melody')[2].generatorSettings.patternSeed);
  for (const track of tracks('melody')) assert.deepEqual(track.generatorSettings.melodyPatternOffsets, service.project().melody.melodyPatternOffsets);
  const drums = [42, 60, 38].map(pitch => ({ ...tracks('drums')[0], generatorSettings: { ...service.project().drums, pitch } }));
  const generated = core.rerollRoleSettings(42n, 'drums', { ...core.DEFAULT_TRACK_POLICIES.drums, maxSwing: 50, randomOffset: true, hitsMultiplier: '2' }, drums, [1,2,3]);
  assert.equal(generated[0].swingPercent, generated[1].swingPercent); assert.equal(generated[0].offset, generated[1].offset);
  assert.equal(generated[2].swingPercent, 50);
  assert.ok(generated.every(track => track.hitsPerPattern >= 1 && track.hitsPerPattern <= 32));
});

test('role totals shrink highest numbers first, preserve locks and references, and undo atomically', () => {
  const { service } = fixture();
  service.setRoleTrackCount('chords', 5);
  service.setTrackRerollLock('track-chords-4', true);
  const section = service.project().arrangement[0];
  service.updatePartSettings({ kind: 'section-track', sectionId: section.id, trackId: 'track-chords-5' }, { transpose: 12 });
  service.reorderTrack('track-chords-5', -1);
  const before = service.exportProjectJson();
  const locked = service.project().tracks.find(track => track.id === 'track-chords-4');
  assert.equal(service.setRoleTrackCount('chords', 3), 'changed');
  assert.deepEqual(service.project().tracks.filter(track => track.role === 'chords').map(track => track.id),
    ['track-chords-1', 'track-chords-2', 'track-chords-4']);
  for (const section of service.project().arrangement) {
    assert.equal('track-chords-5' in section.trackPresence, false);
    assert.equal('track-chords-5' in (section.trackPartOverrides ?? {}), false);
  }
  service.undo(); assert.equal(service.exportProjectJson(), before);
  assert.equal(service.canRedo(), true);
  assert.equal(service.setRoleTrackCount('chords', 5), 'unchanged');
  assert.equal(service.canRedo(), true);
  service.redo();
  assert.equal(service.setRoleTrackCount('chords', 0), 'changed');
  assert.deepEqual(service.project().tracks.filter(track => track.role === 'chords'), [locked]);
  const unchanged = service.exportProjectJson();
  assert.equal(service.setRoleTrackCount('chords', 0), 'unchanged');
  assert.equal(service.exportProjectJson(), unchanged);
  service.setRoleTrackCount('chords', 2);
  assert.equal(service.project().tracks.at(-1).id, 'track-chords-5');
});

test('role totals support empty projects, large increases and strict capacity without consuming redo', () => {
  const { service, load } = fixture();
  for (const role of ['melody', 'bass', 'chords', 'arpeggio', 'drums']) assert.equal(service.setRoleTrackCount(role, 0), 'changed');
  assert.equal(service.project().tracks.length, 0);
  const empty = service.exportProjectJson();
  assert.equal(service.importProjectJson(empty), true);
  const restored = new (load('core/project/project.service.ts').ProjectService)();
  assert.equal(restored.project().tracks.length, 0);
  assert.equal(service.setRoleTrackCount('drums', 64), 'changed');
  assert.equal(service.project().tracks.length, 64);
  assert.equal(service.setRoleTrackCount('bass', 1), 'invalid');
  service.undo(); assert.equal(service.project().tracks.length, 0);
  for (const count of [-1, 65, 1.5, NaN]) assert.equal(service.setRoleTrackCount('drums', count), 'invalid');
  assert.equal(service.setRoleTrackCount('drums', 0), 'unchanged');
  assert.equal(service.canRedo(), true);
});

test('generated tracks append atomically with one history entry for every role', () => {
  for (const role of ['melody', 'bass', 'chords', 'arpeggio', 'drums']) {
    const { service, load } = fixture();
    service.updateTrackPhrase(`track-${role}-1`, [{ id:'manual', midi:60, startBeat:0, durationBeats:1, velocity:80 }]);
    const before = service.exportProjectJson(), existing = service.project().tracks;
    assert.equal(service.generateRoleTracks(role, 3), 'changed');
    const created = service.project().tracks.slice(existing.length);
    assert.equal(created.length, 3); assert.equal(service.project().trackRerollCounts[role], 1);
    assert.deepEqual(service.project().tracks.slice(0, existing.length), existing);
    assert.ok(created.every(track => track.role === role && !track.editedPhrase && track.rerollLocked === false));
    assert.equal(new Set(service.project().tracks.map(track => track.id)).size, service.project().tracks.length);
    if (role === 'drums') assert.ok(created.every(track => track.midiChannel === 10));
    const after = service.exportProjectJson(); service.undo(); assert.equal(service.exportProjectJson(), before);
    service.redo(); assert.equal(service.exportProjectJson(), after);
    service.undo(); service.generateRoleTracks(role, 3); assert.equal(service.exportProjectJson(), after);
    assert.equal(service.importProjectJson(after), true);
    const restored = new (load('core/project/project.service.ts').ProjectService)();
    assert.deepEqual(restored.project(), service.project());
    service.updateSettings({ name: 'redo' }); service.undo(); const state = service.exportProjectJson();
    for (const count of [0, 65, 1.5, NaN]) assert.equal(service.generateRoleTracks(role, count), 'invalid');
    assert.equal(service.exportProjectJson(), state); assert.equal(service.canRedo(), true);
  }
});

test('generation handles empty roles, project capacity and no-op rerolls without consuming history', () => {
  const { service, load } = fixture();
  service.updateInstrumentPolicy('chords', { randomize: false });
  service.removeTrack('track-melody-1');
  assert.equal(service.generateRoleTracks('melody', 2), 'changed');
  assert.ok(service.project().tracks.some(track => track.id === 'track-melody-1'));
  service.updateTrackGenerationPolicy('chords', { fills: false, patterns: false, transpose: false, varyLength: false, delay: false,
    voicesMode: 'NONE', strum: false, maxSplit: 0, split: false, expandChance: 0 });
  service.rerollRoleTracks('chords'); service.updateSettings({ name: 'redo' }); service.undo();
  const before = service.exportProjectJson();
  assert.equal(service.rerollRoleTracks('chords'), 'unchanged');
  assert.equal(service.exportProjectJson(), before); assert.equal(service.canRedo(), true);
  const core = load('core/music/track-generation.ts');
  const source = service.project().tracks.find(track => track.role === 'bass');
  for (let seed = 0; seed < 1000; seed++) {
    const [settings] = core.rerollRoleSettings(BigInt(seed), 'bass', {}, [source], [1]);
    assert.ok(settings.velocityMin <= settings.velocityMax, 'Java independently chooses crossing Bass bounds; normalize safely without redrawing');
  }
  const project = JSON.parse(before), original = project.tracks[0];
  project.tracks = [...project.tracks, ...Array.from({ length: 63 - project.tracks.length }, (_, index) => ({
    ...original, id: 'capacity-' + index, name: 'Capacity ' + index,
  }))];
  assert.equal(service.importProjectJson(JSON.stringify(project)), true);
  service.updateSettings({ name: 'redo-capacity' }); service.undo(); const full = service.exportProjectJson();
  assert.equal(service.generateRoleTracks('drums', 2), 'invalid'); assert.equal(service.exportProjectJson(), full);
  assert.equal(service.canRedo(), true); assert.equal(service.generateRoleTracks('drums', 1), 'changed');
  assert.equal(service.project().tracks.length, 64);
});

test('Arpeggio hit choices and fill weights match Java policy helper evidence', () => {
  const { load } = fixture();
  const { JavaRandom } = load('core/music/java-random.ts');
  const core = load('core/music/track-generation.ts');
  const expected = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/arpeggio-policy.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.deepEqual(Array.from({ length: 100 }, (_, index) => core.weightedChordFill(index)), expected.fills);
  for (const sample of expected.hits) {
    const random = new JavaRandom(BigInt(sample.seed));
    assert.deepEqual(Array.from({ length: 32 }, () => core.chooseArpeggioHits(random,
      { ...core.DEFAULT_ARPEGGIO_POLICY, powerOfTwo: sample.powerOfTwo })), sample.hits);
  }
});

test('Arpeggio reroll policy edits leave part settings and notes untouched, with history and persistence', () => {
  const { service, phrase, load } = fixture();
  const track = () => service.project().tracks.find(track => track.role === 'arpeggio');
  const before = phrase.layOutTrackPhrase(service.project(), track()), settings = track().generatorSettings;
  const policy = { randomHits: false, fixedHits: 16, maxSplit: 90, lengthMin: 150, lengthMax: 150 };
  assert.equal(service.updateArpeggioPolicy(policy), 'changed');
  assert.deepEqual(track().generatorSettings, settings);
  assert.deepEqual(phrase.layOutTrackPhrase(service.project(), track()), before);
  assert.equal(service.project().trackRerollCounts, undefined);
  service.undo(); assert.equal(service.project().trackGenerationPolicies, undefined);
  service.redo(); assert.equal(service.project().trackGenerationPolicies.arpeggio.fixedHits, 16);
  const saved = service.exportProjectJson(); assert.equal(service.importProjectJson(saved), true);
  const restored = new (load('core/project/project.service.ts').ProjectService)();
  assert.deepEqual(restored.project().trackGenerationPolicies, service.project().trackGenerationPolicies);
  const reordered = JSON.parse(saved);
  reordered.trackGenerationPolicies.arpeggio = Object.fromEntries(Object.entries(reordered.trackGenerationPolicies.arpeggio).reverse());
  assert.equal(service.importProjectJson(JSON.stringify(reordered)), true);
  service.updateSettings({ name: 'redo' }); service.undo();
  const canonical = service.exportProjectJson();
  assert.equal(service.updateArpeggioPolicy(policy), 'unchanged');
  assert.equal(service.exportProjectJson(), canonical); assert.equal(service.canRedo(), true);
});

test('Arpeggio group rerolls preserve ownership, locks, local overrides and manual replacements in one undo action', () => {
  const { service, settings, phrase, midi, ui, workspace, load } = fixture();
  service.updateInstrumentPolicy('arpeggio', { randomize: false });
  const id = 'track-arpeggio-1', secondId = service.addTrack('arpeggio'), lockedId = service.addTrack('arpeggio');
  const track = target => service.project().tracks.find(track => track.id === target);
  const first = service.project().arrangement[0];
  const cell = { kind: 'section-role', sectionId: first.id, role: 'arpeggio' };
  service.updatePartSettings(cell, { hitsPerPattern: 3, noteLengthMultiplier: 125 });
  service.setTrackRerollLock(lockedId, true);
  const manual = [{ id: 'saved', midi: 65, startBeat: 0, durationBeats: 1, velocity: 90 }];
  service.updateTrackPhrase(secondId, manual);
  service.updateArpeggioPolicy({ randomHits: false, fixedHits: 16, maxSplit: 90, lengthMin: 150, lengthMax: 150,
    randomSpan: false, maxRepeat: 1, patterns: false, fills: false, transpose: false });
  const before = service.exportProjectJson(), locked = track(lockedId), others = service.project().tracks.filter(track => track.role !== 'arpeggio');
  const arrangement = service.project().arrangement;
  assert.equal(service.rerollArpeggioTracks(), 'changed');
  const after = service.exportProjectJson();
  assert.equal(service.project().trackRerollCounts.arpeggio, 1);
  assert.equal(track(id).generatorSettings.hitsPerPattern, 16);
  assert.equal(track(secondId).generatorSettings.hitsPerPattern, 16);
  assert.equal(track(id).generatorSettings.noteLengthMultiplier, 150);
  assert.ok(track(id).generatorSettings.exceptionChance <= 30, 'fast arp split cap is divided by three');
  assert.deepEqual(service.project().tracks.filter(track => track.role !== 'arpeggio'), others);
  assert.deepEqual(track(lockedId), locked); assert.strictEqual(service.project().arrangement, arrangement);
  assert.deepEqual(phrase.phraseForTrack(service.project(), track(secondId)), manual);
  assert.equal(settings.resolvePartTrack(track(id), service.project().arrangement[0]).generatorSettings.hitsPerPattern, 3);
  ui.selectTrack(id); assert.equal(workspace.partValues().hitsPerPattern, 16);
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.redo(); assert.equal(service.exportProjectJson(), after);
  for (const target of [id, secondId]) {
    const prev = JSON.parse(before).tracks.find(track => track.id === target);
    assert.deepEqual(track(target).mix, prev.mix); assert.equal(track(target).midiChannel, prev.midiChannel);
    assert.equal(track(target).id, prev.id); assert.equal(track(target).name, prev.name);
  }
  service.project().arrangement.forEach((_, index) => service.setSectionTrackPresence(index, id, true));
  const parsed = new Midi(midi.generateCompositionMidi(service.project()));
  assert.equal(parsed.tracks.find(exported => exported.name === track(id).name).notes.length,
    phrase.layOutTrackPhrase(service.project(), track(id)).length);
  const saved = service.exportProjectJson(); assert.equal(service.importProjectJson(saved), true);
  assert.equal(track(lockedId).rerollLocked, true);
  const restored = new (load('core/project/project.service.ts').ProjectService)();
  assert.deepEqual(restored.project().trackRerollCounts, service.project().trackRerollCounts);
  const bases = service.project().tracks.map(track => track.generatorSettings);
  const policies = service.project().trackGenerationPolicies, counts = service.project().trackRerollCounts;
  service.randomizeArrangementPresence();
  assert.deepEqual(service.project().tracks.map(track => track.generatorSettings), bases);
  assert.deepEqual(service.project().trackGenerationPolicies, policies);
  assert.deepEqual(service.project().trackRerollCounts, counts);
});

test('Arpeggio rerolls are deterministic across undo, exact long seeds and restored roll counts', () => {
  const { service, load } = fixture();
  const core = load('core/music/track-generation.ts');
  const source = service.project().arpeggio;
  for (const seed of [42n, -2147483648n, 9007199254740993n, 9223372036854775807n]) {
    assert.deepEqual(core.rerollArpeggioSettings(seed, core.DEFAULT_ARPEGGIO_POLICY, [source, source]),
      core.rerollArpeggioSettings(seed, core.DEFAULT_ARPEGGIO_POLICY, [source, source]));
  }
  service.updateSettings({ seed: '9007199254740993' });
  const before = service.exportProjectJson(); service.rerollArpeggioTracks(); const first = service.project().arpeggio;
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.rerollArpeggioTracks(); assert.deepEqual(service.project().arpeggio, first);
  service.rerollArpeggioTracks(); assert.equal(service.project().trackRerollCounts.arpeggio, 2);
  assert.notDeepEqual(service.project().arpeggio, first);
  const saved = service.exportProjectJson();
  service.rerollArpeggioTracks(); const third = service.project().arpeggio;
  assert.equal(service.importProjectJson(saved), true); service.rerollArpeggioTracks();
  assert.deepEqual(service.project().arpeggio, third);
});

test('Arpeggio reroll rejects invalid rules and effective conflicts atomically, retaining redo on locks and stale targets', () => {
  const { service, load } = fixture();
  const core = load('core/music/track-generation.ts');
  service.updateSettings({ name: 'redo' }); service.undo(); const before = service.exportProjectJson();
  for (const patch of [{ fixedHits: 0 }, { maxSplit: 101 }, { randomHits: 1 }, { lengthMin: 101, lengthMax: 100 },
    { velocityMin: 100, velocityMax: 90 }, { voices: 2 }, { voicesMode: 'random' }, { maxRepeat: 0 }, { extra: true }]) {
    assert.equal(service.updateArpeggioPolicy(patch), 'invalid');
    const project = JSON.parse(before); project.trackGenerationPolicies = { arpeggio: { ...core.DEFAULT_ARPEGGIO_POLICY, ...patch } };
    assert.equal(service.importProjectJson(JSON.stringify(project)), false);
  }
  for (const metadata of [{ trackRerollCounts: { arpeggio: -1 } }, { trackRerollCounts: { arpeggio: '1' } },
    { trackRerollCounts: { unknown: 0 } }, { trackGenerationPolicies: { drums: {} } }]) {
    assert.equal(service.importProjectJson(JSON.stringify({ ...JSON.parse(before), ...metadata })), false);
  }
  const invalidLock = JSON.parse(before); invalidLock.tracks[0].rerollLocked = 1;
  assert.equal(service.importProjectJson(JSON.stringify(invalidLock)), false);
  assert.equal(service.rerollArpeggioTracks('deleted'), 'unchanged');
  assert.equal(service.rerollArpeggioTracks('track-bass-1'), 'unchanged');
  assert.equal(service.exportProjectJson(), before); assert.equal(service.canRedo(), true);
  // Reroll candidate max is 89; local Min 100 with an inherited max makes the entire batch invalid.
  const first = service.project().arrangement[0];
  service.updatePartSettings({ kind: 'global-role', role: 'arpeggio' }, { velocityMax: 110 });
  service.updatePartSettings({ kind: 'section-role', sectionId: first.id, role: 'arpeggio' }, { velocityMin: 100 });
  service.updateSettings({ name: 'redo-conflict' }); service.undo();
  const conflict = service.exportProjectJson();
  assert.equal(service.rerollArpeggioTracks(), 'invalid'); assert.equal(service.exportProjectJson(), conflict); assert.equal(service.canRedo(), true);
  service.setTrackRerollLock('track-arpeggio-1', true); const locked = service.exportProjectJson();
  assert.equal(service.rerollArpeggioTracks(), 'unchanged'); assert.equal(service.exportProjectJson(), locked);
});

test('Arpeggio policy preserves opted-out fields, shared counts and no-op history', () => {
  const { service, load } = fixture();
  const core = load('core/music/track-generation.ts');
  const source = { ...service.project().arpeggio, rhythm: 'custom', customPattern: Array(32).fill(1),
    useCustomVelocities: true, customVelocities: Array(32).fill(77), patternFlip: true, patternShift: 3,
    chordSpanFill: 'HALF1', fillFlip: true, transpose: 24, chordSpan: 4 };
  const policy = { ...core.DEFAULT_ARPEGGIO_POLICY, randomHits: false, fixedHits: 32, randomSpan: false,
    patterns: false, fills: false, transpose: false };
  const result = core.rerollArpeggioSettings(42n, policy, [source, source]);
  for (const generated of result) {
    for (const key of ['rhythm', 'customPattern', 'customVelocities', 'useCustomVelocities', 'patternFlip', 'patternShift', 'chordSpanFill', 'fillFlip', 'transpose', 'chordSpan']) {
      assert.deepEqual(generated[key], source[key]);
    }
    assert.equal(generated.hitsPerPattern, 32); assert.equal(generated.patternRepeat, 1);
  }
  const shared = core.rerollArpeggioSettings(42n, core.DEFAULT_ARPEGGIO_POLICY, Array(12).fill(source));
  assert.equal(new Set(shared.map(settings => settings.hitsPerPattern)).size, 1);
  const separate = core.rerollArpeggioSettings(42n, { ...core.DEFAULT_ARPEGGIO_POLICY, sameHits: false }, Array(12).fill(source));
  assert.ok(new Set(separate.map(settings => settings.hitsPerPattern)).size > 1);
  service.updateArpeggioPolicy({ randomHits: false, fixedHits: 4, randomSpan: false, maxRepeat: 1,
    maxSplit: 0, lengthMin: 100, lengthMax: 100, patterns: false, fills: false, transpose: false, voicesMode: 'NONE' });
  service.updateInstrumentPolicy('arpeggio', { randomize: false });
  service.rerollArpeggioTracks();
  const nextSeed = service.trackRerollSeed(service.project(), 'arpeggio', service.project().trackRerollCounts.arpeggio);
  const nextPattern = core.rerollArpeggioSettings(nextSeed, service.project().trackGenerationPolicies.arpeggio, [service.project().arpeggio])[0].pattern;
  service.updatePartSettings({ kind: 'global-role', role: 'arpeggio' }, { pattern: nextPattern });
  const nextRotation = core.rerollArpeggioSettings(nextSeed, service.project().trackGenerationPolicies.arpeggio, [service.project().arpeggio])[0].arpPatternRotate;
  service.updatePartSettings({ kind: 'global-role', role: 'arpeggio' }, { arpPatternRotate: nextRotation });
  service.updateSettings({ name: 'redo' }); service.undo();
  const before = service.exportProjectJson();
  assert.equal(service.rerollArpeggioTracks(), 'unchanged'); assert.equal(service.exportProjectJson(), before); assert.equal(service.canRedo(), true);
});

test('bass join modes match 432 complete production Java phrases', () => {
  const { load } = fixture();
  const { generateBassline } = load('core/music/bass-generator.ts');
  const cases = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/bass-core.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(cases.length, 432);
  for (const [caseIndex, sample] of cases.entries()) {
    const actual = generateBassline(BigInt(sample.seed), sample.key, sample.scale, sample.progression,
      sample.settings.rhythm, sample.settings.noteVariation, sample.settings);
    assert.equal(actual.length, sample.notes.length, `case ${caseIndex} note count`);
    actual.forEach((note, index) => {
      const expected = sample.notes[index], label = `case ${caseIndex}, note ${index}`;
      assert.equal(note.midi, expected[0], `${label} pitch`);
      assert.ok(Math.abs(note.startBeat - expected[1]) < 1e-10, `${label} onset ${note.startBeat} vs ${expected[1]}`);
      assert.ok(Math.abs(note.durationBeats - expected[2]) < 1e-10, `${label} duration ${note.durationBeats} vs ${expected[2]}`);
      assert.equal(note.velocity, expected[3], `${label} velocity`);
    });
  }
});

test('bass join articulation resolves at all scopes, persists snapshots and exports sustained MIDI', () => {
  const { service, settings, phrase, midi, ui, workspace, load } = fixture();
  const id = 'track-bass-1', track = () => service.project().tracks.find(track => track.id === id);
  const [first, second] = service.project().arrangement;
  const global = { kind: 'global-track', trackId: id };
  const cell = { kind: 'section-role', sectionId: first.id, role: 'bass' };
  const part = { kind: 'section-track', sectionId: first.id, trackId: id };
  service.project().arrangement.forEach((_, index) => service.setSectionTrackPresence(index, id, true));
  service.updatePartSettings({ kind: 'global-role', role: 'bass' }, { rhythm: 'half', hitsPerPattern: 8, noteVariation: 100 });
  assert.equal(settings.settingsValues(track().generatorSettings, 'bass').patternJoinMode, 'NOJOIN');
  const subdivisions = phrase.layOutTrackPhrase(service.project(), track());
  service.updatePartSettings(cell, { patternJoinMode: 'EXPAND' });
  const expanded = phrase.layOutTrackPhrase(service.project(), track());
  assert.ok(expanded.some(note => note.startBeat < first.measures * 4 && note.durationBeats === 1));
  assert.deepEqual(expanded.filter(note => note.startBeat >= first.measures * 4), subdivisions.filter(note => note.startBeat >= first.measures * 4));
  service.undo(); assert.deepEqual(phrase.layOutTrackPhrase(service.project(), track()), subdivisions);
  service.redo(); assert.deepEqual(phrase.layOutTrackPhrase(service.project(), track()), expanded);
  service.updatePartSettings(part, { patternJoinMode: 'JOIN', rhythm: 'full' });
  const joined = phrase.layOutTrackPhrase(service.project(), track());
  assert.equal(joined.filter(note => note.startBeat < first.measures * 4).length, first.measures);
  assert.ok(joined.filter(note => note.startBeat < first.measures * 4).every(note => note.durationBeats === 4));
  const parsed = new Midi(midi.generateCompositionMidi(service.project()));
  const exported = parsed.tracks.find(candidate => candidate.name === track().name).notes;
  for (const note of joined) assert.ok(exported.some(candidate => candidate.midi === note.midi
    && Math.abs(candidate.ticks / parsed.header.ppq - note.startBeat) < 0.003
    && Math.abs(candidate.durationTicks / parsed.header.ppq - note.durationBeats) < 0.003));
  service.freezePartSettings(part);
  assert.equal(service.project().arrangement[0].trackPartOverrides[id].patternJoinMode, 'JOIN');
  service.applyPartSettingsToSections(part, [second.id], 'overrides');
  assert.equal(service.project().arrangement[1].trackPartOverrides[id].patternJoinMode, 'JOIN');
  service.applyPartSettingsToSections(part, [second.id], 'effective');
  service.updatePartSettings(global, { patternJoinMode: 'EXPAND' });
  assert.equal(settings.resolvePartTrack(track(), service.project().arrangement[0]).generatorSettings.patternJoinMode, 'JOIN');
  const duplicate = service.duplicateTrack(id);
  service.updatePartSettings({ kind: 'section-track', sectionId: first.id, trackId: duplicate }, { patternJoinMode: 'NOJOIN' });
  ui.selectCell(first.id, 'bass'); assert.equal(workspace.partValues().patternJoinMode, null);
  const saved = service.exportProjectJson(), beforeImport = phrase.layOutTrackPhrase(service.project(), track());
  assert.equal(service.importProjectJson(saved), true);
  assert.deepEqual(service.project().arrangement[0].trackPartOverrides, JSON.parse(saved).arrangement[0].trackPartOverrides);
  assert.deepEqual(phrase.layOutTrackPhrase(service.project(), track()), beforeImport);
  const restored = new (load('core/project/project.service.ts').ProjectService)();
  assert.equal(restored.project().arrangement[0].trackPartOverrides[id].patternJoinMode, 'JOIN');
  service.resetPartSettings(part, 'patternJoinMode');
  assert.equal(settings.resolvePartTrack(track(), service.project().arrangement[0]).generatorSettings.patternJoinMode, 'EXPAND');
  const manual = [{ id: 'manual-bass', midi: 40, startBeat: 0, durationBeats: 0.75, velocity: 77 }];
  service.updateTrackPhrase(id, manual); service.updatePartSettings(part, { patternJoinMode: 'JOIN', octaveInterval: true });
  assert.deepEqual(phrase.phraseForTrack(service.project(), track()), manual);
  assert.ok(phrase.layOutTrackPhrase(service.project(), track()).every(note => note.midi === 40 && note.durationBeats === 0.75));
  const { PartSettingsEditorComponent } = load('shared/part-settings-editor.component.ts');
  const editor = new PartSettingsEditorComponent(); editor.role.set('bass'); editor.values.set({ rhythm: 'full' });
  assert.ok(editor.controls().some(control => control.key === 'patternJoinMode'));
  editor.advanced.set(false); assert.equal(editor.controls().some(control => control.key === 'patternJoinMode'), false);
  editor.advanced.set(true); editor.values.set({ rhythm: 'bass1' });
  assert.equal(editor.controls().some(control => control.key === 'patternJoinMode'), false);
});

test('bass join validation rejects malformed and wrong-role patches without losing redo', () => {
  const { service, settings, load } = fixture();
  const { generateBassline } = load('core/music/bass-generator.ts');
  const omitted = generateBassline(42n, 'C', 'major', [1, 6, 4, 5], 'half', 100);
  assert.deepEqual(omitted, generateBassline(42n, 'C', 'major', [1, 6, 4, 5], 'half', 100, { patternJoinMode: 'NOJOIN' }));
  service.updateSettings({ name: 'redo' }); service.undo();
  const saved = service.exportProjectJson(), first = service.project().arrangement[0];
  for (const value of ['expand', 'LEGATO', false, 1, null]) {
    const patch = { patternJoinMode: value };
    for (const scope of [{ kind: 'global-role', role: 'bass' }, { kind: 'global-track', trackId: 'track-bass-1' },
      { kind: 'section-role', sectionId: first.id, role: 'bass' }, { kind: 'section-track', sectionId: first.id, trackId: 'track-bass-1' }]) {
      service.updatePartSettings(scope, patch);
    }
    assert.throws(() => generateBassline(42n, 'C', 'major', [1], 'full', 0, patch), RangeError);
    for (const location of ['track', 'role', 'section-role', 'section-track']) {
      const project = JSON.parse(saved);
      if (location === 'track') Object.assign(project.tracks.find(track => track.role === 'bass').generatorSettings, patch);
      if (location === 'role') Object.assign(project.bass, patch);
      if (location === 'section-role') project.arrangement[0].rolePartOverrides = { bass: patch };
      if (location === 'section-track') project.arrangement[0].trackPartOverrides = { 'track-bass-1': patch };
      assert.equal(service.importProjectJson(JSON.stringify(project)), false);
    }
  }
  for (const role of ['melody', 'arpeggio', 'drums']) assert.equal(settings.decodePartPatch(role, { patternJoinMode: 'JOIN' }), undefined);
  assert.equal(service.exportProjectJson(), saved); assert.equal(service.canRedo(), true);
});

test('shared drum controls match 96 complete Java phrases with spans, pauses, split hits and extreme swing', () => {
  const { load } = fixture();
  const { generateDrumPart } = load('core/music/drum-generator.ts');
  const cases = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/drum-shared.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(cases.length, 96);
  for (const [caseIndex, sample] of cases.entries()) {
    const actual = generateDrumPart(BigInt(sample.seed), sample.barCount, sample.settings);
    assert.equal(actual.length, sample.notes.length, `case ${caseIndex} note count`);
    actual.forEach((note, index) => {
      const expected = sample.notes[index], label = `case ${caseIndex}, note ${index}`;
      assert.equal(note.midi, expected[0], label);
      assert.ok(Math.abs(note.startBeat - expected[1]) < 1e-10, `${label} onset ${note.startBeat} vs ${expected[1]}`);
      assert.ok(Math.abs(note.durationBeats - expected[2]) < 1e-10, `${label} duration`);
      assert.equal(note.velocity, expected[3], `${label} velocity`);
    });
  }
});

test('span/repeat slicing, custom velocities, voice expansion and feedback match 578 production Java helper cases', () => {
  const { load } = fixture();
  const processing = load('core/music/part-processing.ts');
  const cases = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/shared-part.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(cases.length, 578);
  for (const [index, sample] of cases.entries()) {
    if (sample.kind === 'span') {
      assert.deepEqual(processing.spannedPattern(sample.settings, sample.chordIndex, sample.arp), sample.pattern, `pattern ${index}`);
      assert.deepEqual(processing.spannedVelocities(sample.settings, sample.chordIndex, sample.arp), sample.velocities, `velocities ${index}`);
    } else if (sample.kind === 'voices') {
      assert.deepEqual(processing.expandedVoices(sample.pitches, sample.settings), sample.result, `voices ${index}`);
    } else {
      const actual = processing.applyPartTiming([{ id: 'source', midi: 60, startBeat: 12, durationBeats: 0.75, velocity: 90 }], sample.settings)
        .map(note => [note.midi, note.startBeat, note.durationBeats, note.velocity]);
      const sort = notes => notes.sort((a, b) => a[1] - b[1] || a[3] - b[3]);
      assert.deepEqual(sort(actual), sort(sample.notes), `feedback ${index}`);
    }
  }
});

test('every applicable shared control has a role consumer and genuine Java exceptions remain excluded', () => {
  const { service, settings, phrase, load } = fixture();
  const keys = role => settings.PART_CONTROLS[role].map(control => control.key);
  for (const role of ['melody', 'bass', 'chords', 'arpeggio', 'drums']) {
    for (const key of ['patternSeed', 'offset', 'feedbackCount', 'feedbackDuration', 'feedbackVol']) assert.ok(keys(role).includes(key));
    assert.equal(keys(role).includes('generationEnabled'), false);
    assert.equal(keys(role).includes('accents'), role === 'melody');
    assert.equal(keys(role).includes('patternRepeat'), role === 'arpeggio');
    assert.equal(keys(role).includes('exceptionChance'), ['arpeggio', 'drums'].includes(role));
    assert.equal(keys(role).includes('pauseChance'), role !== 'bass');
    assert.equal(keys(role).includes('swingPercent'), role !== 'bass');
    assert.equal(keys(role).includes('stretchEnabled'), ['chords', 'arpeggio'].includes(role));
    const track = service.project().tracks.find(track => track.role === role);
    const generate = patch => phrase.generateTrackPhrase(service.project(), { ...track, generatorSettings: { ...track.generatorSettings, ...patch } });
    assert.ok(generate({}).length > 0);
    assert.deepEqual(generate({ patternSeed: 123 }), phrase.generateTrackPhrase({ ...service.project(), seed: '123' }, track));
    assert.notDeepEqual(generate({ patternSeed: 123 }), generate({}));
    const delayed = generate({ offset: 250, feedbackCount: 2, feedbackDuration: 750, feedbackVol: 65 });
    assert.equal(delayed.length, generate({}).length * 3);
    assert.ok(delayed.every(note => note.startBeat >= 0.25));
  }
  for (const role of ['bass', 'chords', 'arpeggio', 'drums']) {
    const track = service.project().tracks.find(track => track.role === role);
    const generate = patch => phrase.generateTrackPhrase(service.project(), { ...track,
      generatorSettings: { ...track.generatorSettings, rhythm: 'full', hitsPerPattern: 4, ...patch } });
    const full = generate({});
    assert.equal(full.length, (role === 'chords' ? 3 : 1) * 16);
    assert.equal(generate({ chordSpan: 2 }).length, full.length / 2);
    assert.deepEqual(generate({ patternFlip: true }), []);
    assert.deepEqual(generate({ useCustomVelocities: true, customVelocities: Array(32).fill(0) }), []);
    assert.ok(generate({ useCustomVelocities: true, customVelocities: Array(32).fill(101) }).every(note => note.velocity === 101));
    if (role !== 'bass') {
      assert.deepEqual(generate({ pauseChance: 100 }), []);
      assert.notDeepEqual(generate({ swingPercent: 66 }), full);
      assert.deepEqual(generate({ hitsPerPattern: 3, swingPercent: 66 }), generate({ hitsPerPattern: 3 }));
    }
    if (role === 'arpeggio' || role === 'drums') assert.equal(generate({ exceptionChance: 100 }).length, full.length * 2);
    if (role === 'chords') assert.equal(generate({ stretchEnabled: true, chordNotesStretch: 6 }).length, full.length * 2);
    if (role === 'arpeggio') {
      assert.equal(generate({ patternRepeat: 2 }).length, full.length * 2);
      assert.notDeepEqual(generate({ stretchEnabled: true, chordNotesStretch: 2 }), full);
    }
  }
  const { PartSettingsEditorComponent } = load('shared/part-settings-editor.component.ts');
  const editor = new PartSettingsEditorComponent(); editor.role.set('bass');
  editor.values.set({ ...settings.settingsValues(service.project().bass, 'bass'), useCustomVelocities: true });
  assert.equal(editor.controls().some(control => control.key === 'hitsPerPattern'), false);
  assert.equal(editor.controls().some(control => control.key === 'velocityMin'), true);
});

test('all-role scoped timing, seeds and arrangement presence survive history, snapshots, JSON and manual replacements', () => {
  const { service, settings, phrase, midi, ui, workspace } = fixture();
  for (const role of ['melody', 'bass', 'chords', 'arpeggio', 'drums']) {
    const id = `track-${role}-1`, track = () => service.project().tracks.find(track => track.id === id);
    service.project().arrangement.forEach((_, index) => service.setSectionTrackPresence(index, id, true));
    const [first, second] = service.project().arrangement;
    const cell = { kind: 'section-role', sectionId: first.id, role };
    const part = { kind: 'section-track', sectionId: first.id, trackId: id };
    service.updatePartSettings({ kind: 'global-role', role }, { patternSeed: 123 });
    service.updatePartSettings({ kind: 'global-track', trackId: id }, { feedbackVol: 80 });
    const before = phrase.layOutTrackPhrase(service.project(), track());
    service.updatePartSettings(cell, { offset: 250, feedbackCount: 1, feedbackDuration: 500 });
    service.updatePartSettings(part, { feedbackVol: 50 });
    const after = phrase.layOutTrackPhrase(service.project(), track());
    const end = first.measures * 4;
    assert.deepEqual(after.filter(note => note.startBeat >= end + 1), before.filter(note => note.startBeat >= end + 1));
    assert.equal(after.length - before.length, before.filter(note => note.startBeat < end).length,
      'feedback survives even when its onset crosses the section boundary');
    const exported = new Midi(midi.generateCompositionMidi(service.project()));
    const notes = exported.tracks.find(midiTrack => midiTrack.name === track().name).notes;
    assert.equal(notes.length, after.length);
    for (const note of after) assert.ok(notes.some(exportedNote => exportedNote.midi === note.midi
      && Math.abs(exportedNote.ticks / exported.header.ppq - note.startBeat) < 0.003
      && Math.abs(exportedNote.velocity * 127 - note.velocity) < 0.001));
    service.updateTrack(id, { mix: { muted: true } });
    assert.deepEqual(phrase.layOutTrackPhrase(service.project(), track()), after, 'mute leaves score notes intact');
    service.updateTrack(id, { mix: { solo: true } });
    assert.deepEqual(phrase.layOutTrackPhrase(service.project(), track()), after, 'solo leaves score notes intact');
    service.updateTrack(id, { mix: { solo: false } });
    service.setSectionTrackPresence(0, id, false);
    assert.equal(service.project().arrangement[0].trackPresence[id], false);
    assert.equal(phrase.layOutTrackPhrase(service.project(), track()).some(note => note.startBeat < end), false);
    service.undo(); assert.deepEqual(phrase.layOutTrackPhrase(service.project(), track()), after);
    service.redo(); service.undo();
    service.freezePartSettings(part);
    const frozen = service.project().arrangement[0].trackPartOverrides[id];
    assert.equal(frozen.feedbackCount, 1); assert.equal(frozen.feedbackVol, 50); assert.equal(frozen.patternSeed, 123);
    service.applyPartSettingsToSections(part, [second.id], 'effective');
    assert.deepEqual(service.project().arrangement[1].trackPartOverrides[id], frozen);
    const added = service.duplicateTrack(id);
    service.updatePartSettings({ kind: 'section-track', sectionId: first.id, trackId: added }, { offset: -250 });
    ui.selectCell(first.id, role); assert.equal(workspace.partValues().offset, null);
    assert.equal(service.importProjectJson(service.exportProjectJson()), true);
    const manual = [{ id: 'manual', midi: role === 'drums' ? 38 : 60, startBeat: 0, durationBeats: 1, velocity: 90 }];
    service.updateTrackPhrase(id, manual);
    service.updatePartSettings(part, { feedbackCount: 5 });
    assert.deepEqual(phrase.phraseForTrack(service.project(), track()), manual);
    assert.ok(phrase.layOutTrackPhrase(service.project(), track()).some(note => note.startBeat === 0));
    assert.equal(service.resetCellPartSettings(cell), 'changed');
    assert.equal(service.project().arrangement[0].rolePartOverrides?.[role], undefined);
  }
});

test('shared fields reject malformed and wrong-role values atomically at all scopes and on import', () => {
  const { service } = fixture();
  service.updateSettings({ name: 'redo' }); service.undo();
  const before = service.exportProjectJson(), first = service.project().arrangement[0];
  for (const role of ['melody', 'bass', 'chords', 'arpeggio', 'drums']) {
    const id = `track-${role}-1`;
    const scopes = [{ kind: 'global-role', role }, { kind: 'global-track', trackId: id },
      { kind: 'section-role', sectionId: first.id, role }, { kind: 'section-track', sectionId: first.id, trackId: id }];
    const invalid = [{ generationEnabled: false }, { generationEnabled: true }, { generationEnabled: 0 },
      { patternSeed: 2147483648 }, { patternSeed: '42' }, { offset: -1001 },
      { feedbackCount: 6 }, { feedbackDuration: 2001 }, { feedbackVol: 9 }, { feedbackVol: 151 },
      ...(role === 'melody' ? [{ chordSpan: 2 }] : [{ chordSpan: 0 }, { chordSpan: 5 }]),
      ...(role === 'bass' ? [{ pauseChance: 20 }, { swingPercent: 66 }] : [{ pauseChance: 101 }]),
      ...(role === 'arpeggio' ? [{ patternRepeat: 5 }] : [{ patternRepeat: 2 }])];
    for (const patch of invalid) {
      for (const scope of scopes) service.updatePartSettings(scope, patch);
      const project = JSON.parse(before); Object.assign(project.tracks.find(track => track.id === id).generatorSettings, patch);
      assert.equal(service.importProjectJson(JSON.stringify(project)), false, `${role} ${JSON.stringify(patch)}`);
    }
  }
  assert.equal(service.exportProjectJson(), before); assert.equal(service.canRedo(), true);
});

test('score combines drums on a shared pitch axis while preserving note ownership and individual visibility', () => {
  const { service, workspace, ui, load } = fixture();
  const kick = 'track-drums-1', snare = service.addTrack('drums'), otherKick = service.addTrack('drums');
  service.updateTrackGeneratorSettings(snare, { pitch: 38 });
  for (const id of [kick, snare, otherKick]) service.setSectionTrackPresence(1, id, true);
  const rows = workspace.scoreTracks(), drumRows = rows.filter(row => row.color === 'drums');
  const drumRow = { id: 'role:drums', name: 'Drums', color: 'drums', trackIds: [kick, snare, otherKick] };
  assert.equal(rows.length, 7);
  assert.deepEqual(drumRows.map(row => row.id), [kick, snare, otherKick]);
  assert.equal(workspace.arrangedTracks().length, 7, 'arrangement and independent tracks stay intact');
  const notes = workspace.scoreNotes();
  const kickNote = notes.find(note => note.part === kick), snareNote = notes.find(note => note.part === snare);
  assert.ok(kickNote.topPercent > snareNote.topPercent);
  assert.equal(notes.find(note => note.part === otherKick).topPercent, kickNote.topPercent);
  ui.selectTrack(snare); assert.equal(workspace.isScoreTrackSelected(drumRow), true);
  workspace.toggleVisibility(kick); assert.equal(workspace.isScoreTrackVisible(drumRow), true);
  workspace.toggleVisibility(snare); workspace.toggleVisibility(otherKick);
  assert.equal(workspace.isScoreTrackVisible(drumRow), false);
  workspace.toggleVisibility(snare);
  const { ScoreCanvasComponent } = load('shared/score-canvas.component.ts');
  const score = new ScoreCanvasComponent();
  score.model = { tracks: rows, notes, sections: [], totalBeats: workspace.totalBeats(),
    selectedTrackId: snare, hiddenTrackIds: workspace.hiddenTracks() };
  score.width = 800; score.height = 400;
  score.app = { render() {}, canvas: { style: {}, setAttribute() {}, getBoundingClientRect: () => ({ left: 0, top: 0, width: 800, height: 400 }) } };
  score.staticGraphics = { clear() {}, rect() { return this; }, fill() { return this; },
    moveTo() { return this; }, lineTo() { return this; }, stroke() { return this; }, roundRect() { return this; } };
  score.colorClass = class { alpha = 1; toNumber() { return 0; } };
  const savedStyle = global.getComputedStyle, savedDocument = global.document;
  global.getComputedStyle = () => ({ getPropertyValue: () => '#000000' });
  global.document = { documentElement: {} };
  try {
    score.drawStatic();
    const drumHits = score.hitAreas.filter(area => area.note.color === 'drums');
    assert.ok(drumHits.length > 0);
    assert.ok(drumHits.every(area => area.trackIndex === 1 && area.note.part === snare));
    const area = drumHits[0];
    let selected;
    score.noteSelected.emit = note => { selected = note; workspace.openNoteEditor(note); };
    score.onCanvasClick({ clientX: area.x + 1, clientY: area.y + area.height / 2 });
    assert.equal(selected.part, snare);
    assert.equal(workspace.selectedTrackId(), snare);
    assert.equal(workspace.editingNoteId(), selected.id);
    score.model.hiddenTrackIds = new Set(); score.drawStatic();
    assert.ok(score.hitAreas.filter(area => area.note.color === 'drums').every(area => area.trackIndex === 1));
  } finally { global.getComputedStyle = savedStyle; global.document = savedDocument; }
  for (const id of [kick, snare, otherKick]) service.removeTrack(id);
  assert.equal(workspace.scoreTracks().some(row => row.color === 'drums'), false);
});

test('single-pitch drums match 96 complete production Java phrases at fixed seeds', () => {
  const { load } = fixture();
  const { generateDrumPart } = load('core/music/drum-generator.ts');
  const cases = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/drum-core.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(cases.length, 96);
  for (const [caseIndex, sample] of cases.entries()) {
    const actual = generateDrumPart(BigInt(sample.seed), sample.barCount, sample.settings);
    assert.equal(actual.length, sample.notes.length, `case ${caseIndex} note count`);
    actual.forEach((note, index) => {
      const expected = sample.notes[index], label = `case ${caseIndex}, note ${index}`;
      assert.equal(note.midi, expected[0], `${label} pitch`);
      assert.ok(Math.abs(note.startBeat - expected[1]) < 1e-10, `${label} onset: ${note.startBeat} vs ${expected[1]}`);
      assert.ok(Math.abs(note.durationBeats - expected[2]) < 1e-10, `${label} duration: ${note.durationBeats} vs ${expected[2]}`);
      assert.equal(note.velocity, expected[3], `${label} dynamics`);
    });
  }
});

test('drum mapping preferences persist and transfer without editing project notes or history', () => {
  const { service, load } = fixture();
  const { UiPreferencesService } = load('core/ui/ui-preferences.service.ts');
  const preferences = new UiPreferencesService();
  const project = service.exportProjectJson(), position = service.historyPosition();
  assert.equal(preferences.drumMapping(), 'auto');
  for (const mode of ['chromatic', 'general-midi', 'auto']) {
    preferences.setDrumMapping(mode);
    assert.equal(new UiPreferencesService().drumMapping(), mode);
    assert.equal(preferences.importJson(preferences.exportJson()), true);
    assert.equal(preferences.drumMapping(), mode);
  }
  const saved = preferences.exportJson();
  assert.equal(preferences.importJson(JSON.stringify({ ...JSON.parse(saved), drumMapping: 'unknown' })), false);
  assert.equal(preferences.exportJson(), saved, 'invalid mapping import is atomic');
  const withoutMapping = JSON.parse(saved); delete withoutMapping.drumMapping;
  preferences.setDrumMapping('chromatic');
  assert.equal(preferences.importJson(JSON.stringify(withoutMapping)), true);
  assert.equal(preferences.drumMapping(), 'auto');
  assert.equal(service.exportProjectJson(), project);
  assert.equal(service.historyPosition(), position);
});

test('drum identities are independent, named, fixed to channel 10 and preserved by history and manual editing', () => {
  const { service, phrase, workspace, ui, load, settings } = fixture();
  const first = 'track-drums-1', track = id => service.project().tracks.find(track => track.id === id);
  assert.equal(track(first).generatorSettings.pitch, 36);
  assert.ok(phrase.phraseForTrack(service.project(), track(first)).every(note => note.midi === 36));
  const before = service.exportProjectJson();
  service.updateTrackGeneratorSettings(first, { pitch: 38 });
  assert.equal(workspace.instrumentName(track(first)), 'Snare · 38');
  assert.ok(phrase.phraseForTrack(service.project(), track(first)).every(note => note.midi === 38));
  const after = service.exportProjectJson();
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.redo(); assert.equal(service.exportProjectJson(), after);
  const manual = [{ id: 'manual', midi: 39, startBeat: 0, durationBeats: 0.2, velocity: 95 }];
  service.updateTrackPhrase(first, manual);
  service.updateTrackGeneratorSettings(first, { pitch: 42 });
  assert.deepEqual(phrase.phraseForTrack(service.project(), track(first)), manual);
  const added = service.addTrack('drums');
  assert.equal(track(added).generatorSettings.pitch, 36);
  assert.equal(track(added).editedPhrase, undefined);
  const duplicate = service.duplicateTrack(first);
  assert.equal(track(duplicate).generatorSettings.pitch, 42);
  assert.notStrictEqual(track(duplicate).editedPhrase, track(first).editedPhrase);
  assert.ok(service.project().tracks.filter(track => track.role === 'drums').every(track => track.midiChannel === 10));
  ui.selectTrack(first);
  assert.equal(workspace.hasChannelCollision(), false);
  const { EditWorkspaceComponent } = load('features/edit/edit-workspace.component.ts');
  const editor = new EditWorkspaceComponent(); editor.ngOnInit();
  assert.deepEqual(editor.drumVoices(), [{ midi: 36, label: 'Kick · 36' }, { midi: 39, label: 'Clap · 39' }, { midi: 42, label: 'Closed hi-hat · 42' }]);
  const saved = service.exportProjectJson();
  service.updateTrack(first, { midiChannel: 1 }); service.updateTrack(first, { mix: { program: 5 } });
  service.updateRoleGeneratorSettings('drums', { pitch: 38 });
  assert.equal(service.exportProjectJson(), saved);
  assert.equal(settings.PART_CONTROLS.drums.some(control => ['pitch', 'groove', 'transpose', 'accents'].includes(control.key)), false);
  assert.equal(service.importProjectJson(saved), true);
  const { ProjectService } = load('core/project/project.service.ts');
  assert.deepEqual(projectData(new ProjectService().exportProjectJson()), projectData(saved));
});

test('drum rhythm and dynamics resolve all scopes, mixed values, frozen snapshots and parsed MIDI without changing identity', () => {
  const { service, settings, ui, workspace, phrase, midi } = fixture();
  const first = 'track-drums-1', second = service.duplicateTrack(first);
  service.updateTrackGeneratorSettings(second, { pitch: 38 });
  const sectionId = service.project().arrangement[1].id;
  const cell = { kind: 'section-role', sectionId, role: 'drums' }, scope = { kind: 'section-track', sectionId, trackId: second };
  service.updatePartSettings({ kind: 'global-role', role: 'drums' }, { rhythm: 'half', hitsPerPattern: 8 });
  service.updatePartSettings({ kind: 'global-track', trackId: second }, { rhythm: 'sparse', hitsPerPattern: 16 });
  ui.selectRole('drums'); assert.equal(workspace.partValues().rhythm, null);
  service.updatePartSettings(cell, { rhythm: 'custom', hitsPerPattern: 8, customPattern: Array.from({ length: 32 }, (_, index) => +(index % 3 === 0)),
    useCustomVelocities: true, customVelocities: Array.from({ length: 32 }, (_, index) => index === 0 ? 0 : 100), swingPercent: 66, noteLengthMultiplier: 50 });
  service.updatePartSettings(scope, { hitsPerPattern: 4 });
  ui.selectCell(sectionId, 'drums'); assert.equal(workspace.partValues().hitsPerPattern, null);
  const before = service.exportProjectJson();
  assert.equal(service.freezePartSettings(cell), 'changed');
  const frozen = service.project().arrangement[1];
  assert.equal(frozen.trackPartOverrides[first].hitsPerPattern, 8);
  assert.equal(frozen.trackPartOverrides[second].hitsPerPattern, 4);
  assert.equal(frozen.trackPartOverrides[first].pitch, undefined);
  assert.equal(settings.resolvePartTrack(service.project().tracks.find(track => track.id === second), frozen).generatorSettings.pitch, 38);
  service.undo(); assert.equal(service.exportProjectJson(), before); service.redo();
  assert.equal(service.applyPartSettingsToSections(cell, [service.project().arrangement[2].id], 'effective'), 'changed');
  service.project().arrangement.forEach((_, index) => {
    service.setSectionTrackPresence(index, first, true); service.setSectionTrackPresence(index, second, true);
  });
  const exported = new Midi(midi.generateCompositionMidi(service.project()));
  for (const id of [first, second]) {
    const track = service.project().tracks.find(track => track.id === id);
    const notes = phrase.layOutTrackPhrase(service.project(), track);
    assert.ok(notes.length > 0 && notes.every(note => note.midi === track.generatorSettings.pitch));
    const midiTrack = exported.tracks.find(item => item.name === track.name);
    assert.equal(midiTrack.channel, 9); assert.equal(midiTrack.notes.length, notes.length);
    midiTrack.notes.forEach((note, index) => {
      assert.equal(note.midi, notes[index].midi);
      assert.ok(Math.abs(note.ticks / exported.header.ppq - notes[index].startBeat) < 0.003);
      assert.ok(Math.abs(note.durationTicks / exported.header.ppq - notes[index].durationBeats) < 0.003,
        `${id} note ${index}: ${note.durationTicks / exported.header.ppq} vs ${notes[index].durationBeats}`);
      assert.ok(Math.abs(note.velocity * 127 - notes[index].velocity) < 0.001);
    });
    assert.equal(workspace.scoreNotes().filter(note => note.part === id).length, notes.length);
  }
  assert.equal(service.importProjectJson(service.exportProjectJson()), true);
  assert.equal(service.resetCellPartSettings(cell), 'changed');
  assert.equal(service.project().tracks.find(track => track.id === second).generatorSettings.pitch, 38);
});

test('drums reject obsolete groove models, invalid identities and malformed musical fields atomically', () => {
  const { service, load } = fixture();
  const { generateDrumPart } = load('core/music/drum-generator.ts');
  const first = 'track-drums-1', sectionId = service.project().arrangement[0].id;
  service.updateSettings({ name: 'future' }); service.undo();
  const before = service.exportProjectJson();
  const invalid = [{ pitch: 0 }, { pitch: 36.5 }, { pitch: '36' }, { groove: 'rock' }, { velocityMin: 100 },
    { hitsPerPattern: 33 }, { patternShift: 9 }, { rhythm: 'melody1' }, { swingPercent: 101 },
    { customPattern: Array(32).fill(2) }, { customVelocities: Array(31).fill(80) }, { useCustomVelocities: 1 }];
  for (const patch of invalid) {
    service.updateTrackGeneratorSettings(first, patch);
    const project = JSON.parse(before);
    Object.assign(project.tracks.find(track => track.id === first).generatorSettings, patch);
    assert.equal(service.importProjectJson(JSON.stringify(project)), false);
    assert.throws(() => generateDrumPart(42n, 4, { ...service.project().drums, ...patch }), RangeError);
  }
  for (const scope of [{ kind: 'global-role', role: 'drums' }, { kind: 'global-track', trackId: first },
    { kind: 'section-role', sectionId, role: 'drums' }, { kind: 'section-track', sectionId, trackId: first }]) {
    for (const patch of [...invalid, { pitch: 38 }]) service.updatePartSettings(scope, patch);
  }
  const obsolete = JSON.parse(before); obsolete.drums = { groove: 'rock', swingPercent: 50 };
  assert.equal(service.importProjectJson(JSON.stringify(obsolete)), false);
  const wrongChannel = JSON.parse(before); wrongChannel.tracks.find(track => track.id === first).midiChannel = 1;
  assert.equal(service.importProjectJson(JSON.stringify(wrongChannel)), false);
  assert.equal(service.exportProjectJson(), before); assert.equal(service.canRedo(), true);
  assert.throws(() => generateDrumPart(9007199254740992, 4, service.project().drums), RangeError);
});

for (const [name, count] of [['core', 80], ['extended', 192], ['global', 192], ['effects', 1152], ['prepared-targets', 192], ['catalogue-phrases', 1536], ['emphasis', 1152], ['tonal', 1920], ['harmony', 2304], ['modes', 1152]]) test(`current block melody matches ${count} complete production Java ${name} phrases at fixed seeds`, () => {
  const { load } = fixture();
  const { generateMelody } = load('core/music/melody-generator.ts');
  const cases = JSON.parse(fs.readFileSync(path.join(__dirname, `fixtures/melody-${name}.java.json`), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(cases.length, count);
  for (const [caseIndex, sample] of cases.entries()) {
    if (name === 'prepared-targets') {
      const { chooseMelodyTargets } = load('core/music/melody-targets.ts');
      assert.deepEqual(chooseMelodyTargets(sample.targetSeed, sample.targetProgression, sample.globalSettings), sample.settings.chordNoteChoices, `case ${caseIndex} prepared targets`);
    }
    const progression = ['harmony', 'modes'].includes(name) ? sample.targetProgression : Array(sample.chordCount).fill(1);
    if (name === 'modes' && sample.prepared) assert.deepEqual(load('core/music/melody-targets.ts').chooseMelodyTargets(
      sample.targetSeed, progression, sample.globalSettings, sample.key, sample.scale, sample.customChords), sample.settings.chordNoteChoices);
    const actual = generateMelody(BigInt(sample.seed), sample.key, sample.scale, progression, sample.settings, sample.notesSeedOffset, undefined, { newBlocksChance: 100, ...sample.globalSettings }, sample.customChords);
    assert.equal(actual.length, sample.notes.length, `case ${caseIndex} note count`);
    actual.forEach((note, index) => {
      const expected = sample.notes[index], label = `case ${caseIndex}, note ${index}`;
      assert.equal(note.midi, expected[0], `${label} pitch`);
      assert.ok(Math.abs(note.startBeat - expected[1]) < 1e-10, `${label} onset: ${note.startBeat} vs ${expected[1]}`);
      assert.ok(Math.abs(note.durationBeats - expected[2]) < 1e-10, `${label} duration: ${note.durationBeats} vs ${expected[2]}`);
      assert.equal(note.velocity, expected[3], `${label} dynamics`);
    });
  }
});

test('P5e catalogue inputs preserve independent arrays, atomic history, scopes and manual ownership', () => {
  const { service, phrase, midi, load } = fixture();
  const id = 'track-melody-1', sectionId = service.project().arrangement[0].id;
  service.setSectionTrackPresence(0, id, true);
  service.updatePartSettings({ kind: 'section-track', sectionId, trackId: id }, { speed: 100 });
  const track = () => service.project().tracks.find(item => item.id === id);
  const tracks = service.project().tracks, sections = service.project().arrangement;
  const original = phrase.layOutTrackPhrase(service.project(), track()), before = service.exportProjectJson();
  const weights = [100, 20, 0, 50, 30, 100, 100], preference = [0, 7, 4, 3, 1, 2, 5, 6];
  assert.equal(service.updateMelodyGlobal({ newBlocksChance: 0, blockTypeWeights: weights, blockChangePreference: preference }), 'changed');
  const saved = service.exportProjectJson(), notes = phrase.layOutTrackPhrase(service.project(), track());
  assert.notDeepEqual(notes, original);
  assert.deepEqual(service.project().tracks, tracks); assert.deepEqual(service.project().arrangement, sections);
  weights[0] = 0; preference.reverse();
  assert.equal(service.exportProjectJson(), saved, 'caller arrays cannot mutate the committed project');
  const parsed = new Midi(midi.generateCompositionMidi(service.project()));
  const exported = parsed.tracks.find(item => item.name === track().name).notes;
  assert.equal(exported.length, notes.length);
  exported.forEach((note, index) => {
    assert.equal(note.midi, notes[index].midi);
    assert.ok(Math.abs(note.ticks / parsed.header.ppq - notes[index].startBeat) < 0.003);
    assert.ok(Math.abs(note.durationTicks / parsed.header.ppq - notes[index].durationBeats) < 0.003);
  });
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.redo(); assert.equal(service.exportProjectJson(), saved);
  service.updateSettings({ name: 'redo' }); service.undo();
  assert.equal(service.updateMelodyGlobal(JSON.parse(saved).melodyGlobal), 'unchanged');
  assert.equal(service.canRedo(), true);
  const { generateMelody } = load('core/music/melody-generator.ts');
  for (const patch of [{ newBlocksChance: -1 }, { newBlocksChance: 101 }, { newBlocksChance: 1.5 }, { newBlocksChance: '0' },
    { blockTypeWeights: null }, { blockTypeWeights: [100] }, { blockTypeWeights: [100,100,100,100,100,100,101] },
    { blockTypeWeights: [100,100,100,100,100,100,0.5] }, { blockTypeWeights: Array(7) },
    { blockChangePreference: [0] }, { blockChangePreference: [0,1,2,3,4,5,6,6] },
    { blockChangePreference: [0,1,2,3,4,5,6,8] }, { blockChangePreference: '0,7,4,3,1,2,5,6' }]) {
    assert.equal(service.updateMelodyGlobal(patch), 'invalid');
    assert.throws(() => generateMelody(42n, 'C', 'major', [1], {}, 0, undefined, patch), RangeError);
    const imported = JSON.parse(saved); imported.melodyGlobal = { ...imported.melodyGlobal, ...patch };
    assert.equal(service.importProjectJson(JSON.stringify(imported)), false);
    assert.equal(service.exportProjectJson(), saved); assert.equal(service.canRedo(), true);
  }
  for (const scope of [{ kind: 'global-role', role: 'melody' }, { kind: 'global-track', trackId: id },
    { kind: 'section-role', sectionId, role: 'melody' }, { kind: 'section-track', sectionId, trackId: id }]) {
    service.updatePartSettings(scope, { newBlocksChance: 0, blockTypeWeights: [0,0,0,0,0,0,0], blockChangePreference: [] });
    assert.equal(service.exportProjectJson(), saved, 'global catalogue inputs cannot enter part patches');
  }
  const { ProjectService } = load('core/project/project.service.ts');
  assert.deepEqual(projectData(new ProjectService().exportProjectJson()), projectData(saved));
  assert.equal(service.importProjectJson(saved), true);
  assert.deepEqual(phrase.layOutTrackPhrase(service.project(), track()), notes);
  const manual = [{ id: 'catalogue-manual', midi: 72, startBeat: 1, durationBeats: 1, velocity: 95 }];
  service.updateTrackPhrase(id, manual); service.updateMelodyGlobal({ newBlocksChance: 50, blockChangePreference: [] });
  assert.deepEqual(phrase.phraseForTrack(service.project(), track()), manual);
});

test('P5e catalogue preference editor rejects partial or duplicate lists without committing', () => {
  const { service, load } = fixture();
  const { MelodyGlobalSettingsComponent } = load('shared/melody-global-settings.component.ts');
  const editor = new MelodyGlobalSettingsComponent(), commits = [];
  editor.settingsChanged.emit = patch => { commits.push(patch); service.updateMelodyGlobal(patch); editor.values.set(service.project().melodyGlobal ?? {}); };
  editor.useBlockPreference(true);
  assert.deepEqual(service.project().melodyGlobal.blockChangePreference, [0,7,4,3,1,2,5,6]);
  for (const text of ['0,1', '0,1,2,3,4,5,6,6', '0,1,2,3,4,5,6,8', '0,1,2,3,4,5,6,7.0', '0,1,2,3,4,5,6,7,']) {
    const input = { value: text }; editor.changeBlockPreference({ target: input });
    assert.equal(commits.length, 1); assert.ok(editor.blockPreferenceError());
    assert.equal(input.value, '0, 7, 4, 3, 1, 2, 5, 6');
  }
  editor.changeBlockPreference({ target: { value: '7, 6,5,4,3,2,1,0' } });
  assert.deepEqual(service.project().melodyGlobal.blockChangePreference, [7,6,5,4,3,2,1,0]);
  assert.equal(editor.blockPreferenceError(), '');
  editor.useBlockPreference(false); assert.deepEqual(service.project().melodyGlobal.blockChangePreference, []);
  editor.changeBlockWeight(2, 40); assert.deepEqual(service.project().melodyGlobal.blockTypeWeights, [100,100,40,100,100,100,100]);
  editor.changeBlockPreference({ target: { value: '   ' } }); assert.deepEqual(commits.at(-1), { blockChangePreference: [] });
});

test('P5e pattern effects and arpy surprises update section dependencies and MIDI independently', () => {
  const { service, phrase, midi, load } = fixture();
  const cases = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/melody-effects.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  const sample = cases[1], project = JSON.parse(service.exportProjectJson());
  project.seed = sample.seed; project.progression = [1, 1, 1]; delete project.progressionChords;
  project.melody = sample.settings;
  project.tracks.find(track => track.role === 'melody').generatorSettings = sample.settings;
  project.arrangement = [{ ...project.arrangement[0], measures: 3, trackPresence: {
    'track-melody-1': true, 'track-drums-1': true, 'track-arpeggio-1': true,
  } }];
  assert.equal(service.importProjectJson(JSON.stringify(project)), true);
  service.updatePartSettings({ kind: 'global-track', trackId: 'track-drums-1' }, { rhythm: 'melody', swingPercent: 50 });
  service.updatePartSettings({ kind: 'global-track', trackId: 'track-arpeggio-1' }, { rhythm: 'melody', swingPercent: 50 });
  const track = role => service.project().tracks.find(track => track.role === role);
  const context = () => phrase.sectionMelodyContext(service.project(), service.project().arrangement[0]);
  const original = phrase.generateTrackPhrase(service.project(), track('melody'));
  const originalContext = context(), before = service.exportProjectJson();
  assert.equal(service.updateMelodyGlobal({ patternEffect: 0 }), 'changed');
  const rhythm = phrase.generateTrackPhrase(service.project(), track('melody'));
  assert.notDeepEqual(rhythm.map(note => note.midi), original.map(note => note.midi));
  assert.notDeepEqual(context(), originalContext);
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.redo(); assert.deepEqual(phrase.generateTrackPhrase(service.project(), track('melody')), rhythm);
  const rhythmContext = context();
  assert.equal(service.updateMelodyGlobal({ arpySurprises: true }), 'changed');
  assert.notDeepEqual(context(), rhythmContext);
  for (const role of ['melody', 'arpeggio', 'drums']) {
    const expected = phrase.layOutTrackPhrase(service.project(), track(role));
    const exported = new Midi(midi.generateCompositionMidi(service.project()));
    const notes = exported.tracks.find(item => item.name === track(role).name).notes;
    assert.equal(notes.length, expected.length);
    notes.forEach((note, index) => {
      assert.equal(note.midi, expected[index].midi);
      assert.ok(Math.abs(note.ticks / exported.header.ppq - expected[index].startBeat) < 0.003);
      assert.ok(Math.abs(note.durationTicks / exported.header.ppq - expected[index].durationBeats) < 0.003);
    });
  }
  const saved = service.exportProjectJson(), { generateMelody } = load('core/music/melody-generator.ts');
  service.updateSettings({ name: 'redo' }); service.undo();
  for (const patch of [{ patternEffect: 3 }, { patternEffect: null }, { arpySurprises: 'true' }]) {
    assert.equal(service.updateMelodyGlobal(patch), 'invalid');
    assert.throws(() => generateMelody(42n, 'C', 'major', [1], sample.settings, 0, undefined, patch), RangeError);
    assert.equal(service.exportProjectJson(), saved); assert.equal(service.canRedo(), true);
  }
});

test('P5e global Melody settings preserve scope ownership, replay, manual notes and exported output', () => {
  const { service, phrase, midi, load } = fixture();
  const id = 'track-melody-1', duplicate = service.duplicateTrack(id);
  const sectionId = service.project().arrangement[0].id;
  service.setSectionTrackPresence(0, id, true);
  service.setSectionTrackPresence(0, duplicate, true);
  service.updatePartSettings({ kind: 'global-role', role: 'melody' }, {
    maxNoteExceptions: 4, noteExceptionChance: 100, fillPauses: true, pauseChance: 100,
  });
  service.updatePartSettings({ kind: 'section-track', sectionId, trackId: id }, { speed: 100 });
  const before = service.exportProjectJson(), patches = service.project().arrangement;
  const track = () => service.project().tracks.find(track => track.id === id);
  const original = phrase.layOutTrackPhrase(service.project(), track());
  const global = { singleNoteExceptions: true, maxDirectionChanges: 6, fillPausesPerChord: true,
    patternEffect: 0, arpySurprises: true, targetNoteVariation: 6, targetDirection: 'DESC',
    useDirectionsFromProgression: true, forceFirstOutline: true };
  assert.equal(service.updateMelodyGlobal(global), 'changed');
  assert.deepEqual(service.project().arrangement, patches);
  assert.notDeepEqual(phrase.layOutTrackPhrase(service.project(), track()), original);
  let saved = service.exportProjectJson();
  const notes = phrase.layOutTrackPhrase(service.project(), track());
  const exported = new Midi(midi.generateCompositionMidi(service.project()));
  const melody = exported.tracks.find(item => item.name === track().name);
  assert.equal(melody.notes.length, notes.length);
  melody.notes.forEach((note, index) => {
    assert.equal(note.midi, notes[index].midi);
    assert.ok(Math.abs(note.ticks / exported.header.ppq - notes[index].startBeat) < 0.003);
    assert.ok(Math.abs(note.durationTicks / exported.header.ppq - notes[index].durationBeats) < 0.003);
  });
  service.undo(); assert.equal(service.exportProjectJson(), before);
  assert.equal(service.updateMelodyGlobal({ maxDirectionChanges: 7 }), 'invalid');
  assert.equal(service.canRedo(), true);
  service.redo(); assert.equal(service.exportProjectJson(), saved);
  assert.equal(service.updateMelodyGlobal(global), 'unchanged');
  const { ProjectService } = load('core/project/project.service.ts');
  assert.deepEqual(projectData(new ProjectService().exportProjectJson()), projectData(saved));
  assert.equal(service.importProjectJson(saved), true);
  saved = service.exportProjectJson();
  for (const malformed of [null, [], true, { unknown: true }, { singleNoteExceptions: 1 },
    { fillPausesPerChord: 'true' }, { maxDirectionChanges: 0 }, { maxDirectionChanges: 2.5 }, { maxDirectionChanges: 7 },
    { patternEffect: -1 }, { patternEffect: 3 }, { patternEffect: 1.5 }, { patternEffect: '0' }, { arpySurprises: 1 },
    { targetNoteVariation: 0 }, { targetNoteVariation: 7 }, { targetNoteVariation: 1.5 }, { targetNoteVariation: '3' },
    { targetDirection: 'up' }, { targetDirection: 1 }, { useDirectionsFromProgression: 1 }, { forceFirstOutline: 'true' }]) {
    const project = JSON.parse(saved); project.melodyGlobal = malformed;
    assert.equal(service.importProjectJson(JSON.stringify(project)), false);
    assert.equal(service.exportProjectJson(), saved);
  }
  for (const scope of [{ kind: 'global-role', role: 'melody' }, { kind: 'global-track', trackId: id },
    { kind: 'section-role', sectionId, role: 'melody' }, { kind: 'section-track', sectionId, trackId: id }]) {
    for (const patch of [{ singleNoteExceptions: true }, { patternEffect: 1 }, { arpySurprises: true },
      { targetNoteVariation: 2 }, { targetDirection: 'ANY' }, { useDirectionsFromProgression: false }, { forceFirstOutline: false }]) {
      service.updatePartSettings(scope, patch);
      assert.equal(service.exportProjectJson(), saved, 'globals cannot leak into part patches');
    }
  }
  const manual = [{ id: 'manual-global', midi: 72, startBeat: 1, durationBeats: 1, velocity: 95 }];
  service.updateTrackPhrase(id, manual);
  service.updateMelodyGlobal({ singleNoteExceptions: false, fillPausesPerChord: false, patternEffect: 1, arpySurprises: false });
  assert.deepEqual(phrase.phraseForTrack(service.project(), track()), manual);
  service.clearTrackPhrase(id);
  const source = phrase.sectionMelodyContext(service.project(), service.project().arrangement[0]);
  service.updateMelodyGlobal({ fillPausesPerChord: true });
  assert.notDeepEqual(phrase.sectionMelodyContext(service.project(), service.project().arrangement[0]), source);
});

test('melody scope resolution reaches parsed MIDI, isolates sections and survives history and restore', () => {
  const { service, phrase, midi, load, settings } = fixture();
  const trackId = 'track-melody-1', second = service.duplicateTrack(trackId);
  service.project().arrangement.forEach((_, index) => service.setSectionTrackPresence(index, trackId, true));
  const sectionId = service.project().arrangement[1].id;
  const scope = { kind: 'section-track', sectionId, trackId };
  const track = () => service.project().tracks.find((track) => track.id === trackId);
  const before = phrase.layOutTrackPhrase(service.project(), track());
  const savedBefore = service.exportProjectJson();
  service.updatePartSettings(scope, { speed: 100, chordNoteChoices: [0, 4, 1, 3], melodyPatternOffsets: [1, -1, 2, 1],
    doubledRhythmChance: 100, maxNoteExceptions: 4, noteExceptionChance: 100, splitChance: 100,
    leadChordsChance: 100, startNoteChance: 0,
    velocityMin: 100, velocityMax: 100, accents: 0, noteLengthMultiplier: 50, transpose: 12 });
  const after = phrase.layOutTrackPhrase(service.project(), track());
  const start = service.project().arrangement[0].measures * 4, end = start + service.project().arrangement[1].measures * 4;
  const outside = notes => notes.filter(note => note.startBeat < start || note.startBeat >= end);
  assert.deepEqual(outside(after), outside(before));
  assert.notDeepEqual(after, before);
  assert.ok(after.filter(note => note.startBeat >= start && note.startBeat < end).every(note => [100, 90, 80].includes(note.velocity)));
  const section = service.project().arrangement[1];
  assert.equal(settings.resolvePartTrack(service.project().tracks.find(track => track.id === second), section).generatorSettings.speed, undefined);
  const exported = new Midi(midi.generateCompositionMidi(service.project()));
  const notes = exported.tracks.find(track => track.name === 'M1').notes;
  assert.equal(notes.length, after.length);
  notes.forEach((note, index) => {
    assert.equal(note.midi, after[index].midi);
    assert.ok(Math.abs(note.ticks / exported.header.ppq - after[index].startBeat) < 0.003);
    assert.ok(Math.abs(note.durationTicks / exported.header.ppq - after[index].durationBeats) < 0.003);
    assert.ok(Math.abs(note.velocity * 127 - after[index].velocity) < 0.001);
  });
  const saved = service.exportProjectJson();
  service.undo(); assert.equal(service.exportProjectJson(), savedBefore);
  service.redo(); assert.equal(service.exportProjectJson(), saved);
  assert.equal(service.importProjectJson(saved), true);
  const { ProjectService } = load('core/project/project.service.ts');
  const restored = new ProjectService();
  assert.deepEqual(phrase.layOutTrackPhrase(restored.project(), restored.project().tracks.find(track => track.id === trackId)), after);
  const manual = [{ id: 'manual', midi: 72, startBeat: 0, durationBeats: 1, velocity: 90 }];
  service.updateTrackPhrase(trackId, manual);
  service.updatePartSettings(scope, { speed: -100 });
  assert.deepEqual(phrase.phraseForTrack(service.project(), track()), manual);
  service.clearTrackPhrase(trackId);
  assert.notDeepEqual(phrase.phraseForTrack(service.project(), track()), manual);
});

test('melody target/block arrays support mixed groups, snapshots, independent duplication and inherited reset', () => {
  const { service, ui, workspace } = fixture();
  const trackId = 'track-melody-1', second = service.duplicateTrack(trackId);
  const sectionId = service.project().arrangement[1].id;
  const cell = { kind: 'section-role', sectionId, role: 'melody' }, scope = { kind: 'section-track', sectionId, trackId: second };
  service.updatePartSettings({ kind: 'global-track', trackId: second }, { chordNoteChoices: [0, 4] });
  ui.selectCell(sectionId, 'melody');
  assert.equal(workspace.partValues().chordNoteChoices, null);
  service.updatePartSettings(cell, { chordNoteChoices: [1, 3], melodyPatternOffsets: [1, -1, 2], speed: 25 });
  service.updatePartSettings(scope, { speed: 80 });
  const before = service.exportProjectJson();
  assert.equal(service.freezePartSettings(cell), 'changed');
  assert.equal(service.project().arrangement[1].trackPartOverrides[second].speed, 80);
  assert.equal(service.project().arrangement[1].trackPartOverrides[trackId].speed, 25);
  assert.notStrictEqual(service.project().arrangement[1].trackPartOverrides[second].chordNoteChoices,
    service.project().arrangement[1].trackPartOverrides[trackId].chordNoteChoices);
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.updatePartSettings(cell, { chordNoteChoices: [0, 2, 2, 4] });
  assert.ok(service.project().arrangement[1].rolePartOverrides.melody.chordNoteChoices, 'mixed global bases cannot clear the cell');
  service.resetPartSettings(scope, 'speed');
  assert.equal(workspace.partValues().speed, 25);
  const duplicate = service.duplicateTrack(second);
  assert.notStrictEqual(service.project().tracks.find(track => track.id === duplicate).generatorSettings.chordNoteChoices,
    service.project().tracks.find(track => track.id === second).generatorSettings.chordNoteChoices);
});

test('melody imports and every scope reject malformed core inputs without losing redo', () => {
  const { service, load } = fixture();
  const { generateMelody } = load('core/music/melody-generator.ts');
  const sectionId = service.project().arrangement[1].id;
  service.updateSettings({ name: 'later' }); service.undo();
  const before = service.exportProjectJson();
  const invalid = [{ speed: 101 }, { speed: -101 }, { chordNoteChoices: [] }, { chordNoteChoices: [25] },
    { melodyPatternOffsets: Array(33).fill(1) }, { melodyPatternOffsets: [1.5] }, { patternSeed: 2147483648 },
    { patternFlexible: 1 }, { velocityMin: 106 }, { splitChance: 101 }, { accents: -1 },
    { doubledRhythmChance: -1 }, { doubledRhythmChance: '50' }, { maxNoteExceptions: 5 }, { maxNoteExceptions: 0.5 },
    { noteExceptionChance: 101 }, { leadChordsChance: null }, { startNoteChance: false }];
  for (const scope of [{ kind: 'global-role', role: 'melody' }, { kind: 'global-track', trackId: 'track-melody-1' },
    { kind: 'section-role', sectionId, role: 'melody' }, { kind: 'section-track', sectionId, trackId: 'track-melody-1' }]) {
    for (const patch of [...invalid, { transpose: 4 }]) {
      service.updatePartSettings(scope, patch);
      assert.equal(service.exportProjectJson(), before);
    }
  }
  for (const patch of invalid) {
    const saved = JSON.parse(before);
    saved.tracks[0].generatorSettings = { algorithm: 'block', ...patch };
    assert.equal(service.importProjectJson(JSON.stringify(saved)), false);
    assert.throws(() => generateMelody(42n, 'C', 'major', [1], { algorithm: 'block', ...patch }), RangeError);
  }
  assert.equal(service.canRedo(), true);
  assert.throws(() => generateMelody(9007199254740992, 'C', 'major', [1], { algorithm: 'block' }), RangeError);
});

test('P5e part controls resolve every scope, mixed values, copy/freeze and inherited resets', () => {
  const { service, settings, ui, workspace } = fixture();
  const trackId = 'track-melody-1', second = service.duplicateTrack(trackId);
  const sectionId = service.project().arrangement[1].id, destinationId = service.project().arrangement[2].id;
  const role = { kind: 'global-role', role: 'melody' }, global = { kind: 'global-track', trackId };
  const cell = { kind: 'section-role', sectionId, role: 'melody' }, local = { kind: 'section-track', sectionId, trackId };
  const patch = { doubledRhythmChance: 80, maxNoteExceptions: 3, noteExceptionChance: 60,
    splitChance: 70, leadChordsChance: 50, startNoteChance: 20 };
  const resolve = (id, section = sectionId) => settings.settingsValues(settings.resolvePartTrack(
    service.project().tracks.find(track => track.id === id), service.project().arrangement.find(item => item.id === section)).generatorSettings, 'melody');
  for (const scope of [role, global, cell, local]) {
    const before = service.exportProjectJson();
    service.updatePartSettings(scope, patch);
    for (const [key, value] of Object.entries(patch)) assert.equal(resolve(trackId)[key], value, `${scope.kind} ${key}`);
    const after = service.exportProjectJson();
    service.undo(); assert.equal(service.exportProjectJson(), before);
    service.redo(); assert.equal(service.exportProjectJson(), after);
    service.undo();
  }
  service.updatePartSettings(global, patch);
  ui.selectCell(sectionId, 'melody');
  for (const key of Object.keys(patch)) assert.equal(workspace.partValues()[key], null, `mixed ${key}`);
  service.updatePartSettings(cell, { splitChance: 30 });
  service.updatePartSettings(local, { splitChance: 100 });
  assert.equal(service.freezePartSettings(cell), 'changed');
  assert.equal(resolve(trackId).splitChance, 100); assert.equal(resolve(second).splitChance, 30);
  assert.equal(service.applyPartSettingsToSections(cell, [destinationId], 'effective'), 'changed');
  assert.equal(resolve(trackId, destinationId).splitChance, 100); assert.equal(resolve(second, destinationId).splitChance, 30);
  assert.equal(service.copyPartSettingsToGlobals(cell), 'changed');
  assert.equal(service.project().tracks.find(track => track.id === second).generatorSettings.splitChance, 30);
  service.resetPartSettings(local, 'splitChance');
  assert.equal(resolve(trackId).splitChance, 30);
  assert.equal(service.importProjectJson(service.exportProjectJson()), true);
  for (const role of ['bass', 'chords', 'arpeggio', 'drums']) assert.equal(settings.decodePartPatch(role, patch), undefined);
  const controls = settings.PART_CONTROLS.melody.filter(control => Object.hasOwn(patch, control.key));
  assert.equal(controls.length, 6); assert.ok(controls.every(control => control.advanced));
});

test('P5e Start note delays feed same-section melody dependency grids before swing', () => {
  const { service, phrase } = fixture();
  const section = service.project().arrangement[1];
  const before = phrase.sectionMelodyContext(service.project(), section);
  service.updatePartSettings({ kind: 'section-track', sectionId: section.id, trackId: 'track-melody-1' },
    { speed: -100, startNoteChance: 0, doubledRhythmChance: 100, splitChance: 100 });
  const after = phrase.sectionMelodyContext(service.project(), service.project().arrangement[1]);
  assert.notDeepEqual(after, before);
  assert.equal(service.project().arrangement[1].trackPresence['track-melody-1'], section.trackPresence['track-melody-1']);
});

test('melody integer-list editors commit once and reject invalid or stale drafts', () => {
  const { service, settings, load } = fixture();
  const { PartSettingsEditorComponent } = load('shared/part-settings-editor.component.ts');
  const editor = new PartSettingsEditorComponent();
  const scope = { kind: 'section-track', sectionId: service.project().arrangement[1].id, trackId: 'track-melody-1' };
  editor.role.set('melody'); editor.values.set(settings.settingsValues({ algorithm: 'block' }, 'melody'));
  const targets = settings.PART_CONTROLS.melody.find(control => control.key === 'chordNoteChoices');
  const commits = [];
  editor.settingsChanged.emit = change => { commits.push(change); service.updatePartSettings(scope, { [change.key]: change.value }); };
  const input = { value: '0, 4, -2', setCustomValidity(value) { this.error = value; }, reportValidity() {} };
  const before = service.exportProjectJson();
  editor.startTextEdit();
  assert.equal(service.exportProjectJson(), before);
  editor.commitText(targets, { target: input });
  assert.equal(commits.length, 1);
  assert.deepEqual(service.project().arrangement[1].trackPartOverrides[scope.trackId].chordNoteChoices, [0, 4, -2]);
  service.undo(); assert.equal(service.exportProjectJson(), before);
  input.value = '1,,2'; editor.startTextEdit(); editor.commitText(targets, { target: input });
  assert.ok(input.error); assert.equal(commits.length, 1);
  input.value = '1, 2'; editor.startTextEdit(); editor.values.set({ ...editor.values(), speed: 100 });
  editor.commitText(targets, { target: input });
  assert.equal(commits.length, 1);
  assert.equal(input.value, '0, 2, 2, 4');
  assert.equal(input.error, '');
});

test('melody transpose knobs use discrete 5/7 offsets for keyboard, wheel and drag', () => {
  const { load, settings } = fixture();
  const { CompactKnobComponent } = load('shared/compact-knob.component.ts');
  const knob = new CompactKnobComponent();
  knob.minimum.set(-36); knob.maximum.set(36); knob.value.set(0); knob.label.set('Transpose');
  knob.allowedValues.set(settings.MELODY_TRANSPOSES);
  const commits = []; knob.valueCommit.emit = value => { commits.push(value); knob.value.set(value); };
  const wheel = { deltaY: -1, shiftKey: true, preventDefault() {}, stopPropagation() {} };
  knob.onWheel(wheel); knob.onWheel(wheel);
  assert.deepEqual(commits, [5, 7]);
  knob.commitValue({ target: { value: String(settings.MELODY_TRANSPOSES.indexOf(12)) } });
  assert.equal(knob.value(), 12);
  const element = { focus() {}, setPointerCapture() {} };
  knob.startDrag({ button: 0, pointerId: 11, currentTarget: element, clientY: 100, preventDefault() {}, stopPropagation() {} });
  knob.moveDrag({ pointerId: 11, clientY: 87, shiftKey: false });
  assert.equal(commits.length, 3);
  assert.ok(settings.MELODY_TRANSPOSES.includes(knob.preview()));
  knob.finishDrag({ pointerId: 11 });
  assert.ok(settings.MELODY_TRANSPOSES.includes(knob.value()));
});

test('restoring generated melody in the note editor is a draft until Apply and is one undo action', () => {
  const { service, workspace, phrase, load } = fixture();
  const trackId = 'track-melody-1';
  const manual = [{ id: 'manual', midi: 72, startBeat: 0, durationBeats: 1, velocity: 90 }];
  service.updateTrackPhrase(trackId, manual);
  service.updatePartSettings({ kind: 'global-track', trackId }, { speed: 100 });
  workspace.openTrackEditor(trackId);
  const { EditWorkspaceComponent } = load('features/edit/edit-workspace.component.ts');
  const editor = new EditWorkspaceComponent(); editor.ngOnInit();
  const before = service.exportProjectJson();
  editor.restoreGenerated();
  assert.notDeepEqual(editor.draftNotes(), manual);
  assert.equal(service.exportProjectJson(), before);
  editor.applyChanges();
  const track = service.project().tracks.find(track => track.id === trackId);
  assert.equal(track.editedPhrase, undefined);
  assert.deepEqual(phrase.phraseForTrack(service.project(), track), editor.draftNotes().map(({ trackId, ...note }) => note));
  service.undo(); assert.equal(service.exportProjectJson(), before);
});

// The decoder materializes optional empty maps; compare musical data rather than property order/omission.
function projectData(json) {
  const project = JSON.parse(json);
  for (const section of project.arrangement) {
    section.rolePartOverrides ??= {};
    section.trackPartOverrides ??= {};
  }
  return project;
}

test('custom melody durations preserve note/rest boundaries in both generators and Flip complements them', () => {
  const { load } = fixture();
  const { generateMelody } = load('core/music/melody-generator.ts');
  const rows = [[1, -0.5, 0.5, 1, -1], ...Array.from({ length: 9 }, () => [])];
  const settings = { algorithm: 'block', rhythm: 'custom', customMelodyDurations: rows };
  for (const contextualMelody of [false, true]) for (const patternFlip of [false, true]) {
    const notes = generateMelody(42n, 'C', 'major', [1, 5, 6, 4], { ...settings, patternFlip }, 0, undefined, { contextualMelody });
    const starts = patternFlip ? [1, 3] : [0, 1.5, 2];
    assert.deepEqual(notes.map(note => note.startBeat), [0, 4, 8, 12].flatMap(chord => starts.map(start => chord + start)), `${contextualMelody}/${patternFlip}`);
    assert.ok(notes.every(note => note.midi >= 0 && note.velocity > 0));
    const again = generateMelody(42n, 'C', 'major', [1, 5, 6, 4], { ...settings, patternFlip }, 0, undefined, { contextualMelody });
    assert.deepEqual(again, notes);
    const fast = generateMelody(42n, 'C', 'major', [1, 5, 6, 4], { ...settings, patternFlip, speed: 100 }, 0, undefined, { contextualMelody });
    assert.deepEqual(fast.map(note => note.startBeat), notes.map(note => note.startBeat));
  }
  const normal = { algorithm: 'block', speed: 37 };
  assert.deepEqual(generateMelody(9n, 'C', 'major', [1, 5], normal), generateMelody(9n, 'C', 'major', [1, 5], { ...normal, rhythm: 'melody1', patternFlip: true, customMelodyDurations: rows }));
});

test('custom melody lanes pad, cycle by block identity, weight deterministically and validate every selectable lane', () => {
  const { load } = fixture();
  const { melodyDurationBlocks, melodyDurationError, paddedMelodyDurations } = load('core/music/melody-durations.ts');
  const rows = [[1, -1], [], [0.5, -0.5, 1, -2], ...Array.from({ length: 7 }, () => [])];
  const settings = { algorithm: 'block', rhythm: 'custom', customMelodyDurations: rows };
  assert.deepEqual(paddedMelodyDurations(rows[0]), [1, -1, -2]);
  assert.deepEqual(melodyDurationBlocks(settings, 42, 0, 0).flat(), [1, -1, -2]);
  assert.deepEqual(melodyDurationBlocks(settings, 42, 0, 1).flat(), rows[2]);
  const chances = [0, 0, 100, ...Array(7).fill(0)];
  for (let seed = 0; seed < 20; seed++) assert.deepEqual(melodyDurationBlocks({ ...settings, customDurationRandomWeighting: true, customDurationChances: chances }, seed, 0).flat(), rows[2]);
  assert.match(melodyDurationError({ ...settings, customDurationRandomWeighting: true, customDurationChances: Array(10).fill(0) }), /positive chance/);
  assert.match(melodyDurationError({ ...settings, customMelodyDurations: [[-4], ...rows.slice(1)] }), /Lane 1.*only pauses/);
  assert.equal(melodyDurationError({ ...settings, customDurationRandomWeighting: true, customDurationChances: chances, customMelodyDurations: [[-4], ...rows.slice(1)] }), null);
  assert.match(melodyDurationError({ ...settings, customMelodyDurations: [[4], ...Array.from({ length: 9 }, () => [])], patternFlip: true }), /after Flip/);
});

test('custom melody durations copy nested lanes, persist every scope, group inheritance and reject malformed imports', () => {
  const { service, load } = fixture();
  const sectionId = service.project().arrangement[0].id, trackId = 'track-melody-1';
  const rows = [[1, -1, 1, -1], ...Array.from({ length: 9 }, () => [])];
  const scope = { kind: 'section-track', sectionId, trackId };
  const patch = { rhythm: 'custom', patternFlip: true, customMelodyDurations: rows, customDurationChances: Array(10).fill(50), customDurationRandomWeighting: false };
  service.updatePartSettings(scope, patch);
  rows[0][0] = 2;
  assert.equal(service.project().arrangement[0].trackPartOverrides[trackId].customMelodyDurations[0][0], 1);
  const saved = service.exportProjectJson();
  assert.equal(service.importProjectJson(saved), true);
  assert.deepEqual(projectData(service.exportProjectJson()), projectData(saved));
  const keys = load('core/ui/ui-preferences.ts').combinedControlKeys('rhythm', 'melody');
  service.resetPartSettings(scope, keys);
  assert.equal(service.project().arrangement[0].trackPartOverrides[trackId], undefined);
  service.undo();
  assert.deepEqual(projectData(service.exportProjectJson()), projectData(saved));
  const { decodePartPatch, copyPartSettings, partValuesEqual } = load('core/music/part-settings.ts');
  const copy = copyPartSettings(patch);
  assert.ok(partValuesEqual(patch.customMelodyDurations, copy.customMelodyDurations));
  copy.customMelodyDurations[0][0] = 3;
  assert.equal(patch.customMelodyDurations[0][0], 2);
  for (const invalid of [[[0], ...rows.slice(1)], [[5], ...rows.slice(1)], [[NaN], ...rows.slice(1)], [[Infinity], ...rows.slice(1)], [[3, 2], ...rows.slice(1)], rows.slice(1)])
    assert.equal(decodePartPatch('melody', { customMelodyDurations: invalid }), undefined);
  assert.equal(decodePartPatch('melody', { customDurationChances: [50] }), undefined);
  assert.equal(decodePartPatch('bass', { customMelodyDurations: rows }), undefined);
});

test('duration popup dry runs all lanes against contextual range and harmony without changing project or history', () => {
  const { service, load } = fixture();
  const { validateMelodyDurationDraft } = load('core/music/melody-duration-validation.ts');
  const scope = { kind: 'global-track', trackId: 'track-melody-1' };
  const patch = { rhythm: 'custom', customMelodyDurations: [[1, -1, 1, -1], ...Array.from({ length: 9 }, () => [])] };
  const before = service.exportProjectJson(), history = service.historyPosition();
  assert.equal(validateMelodyDurationDraft(service.project(), scope, patch), null);
  const project = { ...service.project(), melodyGlobal: { contextualMelody: true, rangeMin: 60, rangeMax: 60 } };
  assert.match(validateMelodyDurationDraft(project, scope, patch), /No contextual melody/);
  assert.equal(service.exportProjectJson(), before); assert.equal(service.historyPosition(), history);
});

test('group melody duration edits preserve independent pitch settings and section exceptions', () => {
  const { service, load, uiPreferences: preferences } = fixture();
  const first = 'track-melody-1', second = service.duplicateTrack(first);
  const sectionId = service.project().arrangement[0].id;
  service.updatePartSettings({ kind: 'global-track', trackId: second }, { transpose: 12, patternSeed: 123 });
  service.updatePartSettings({ kind: 'section-track', sectionId, trackId: second }, { chordNoteChoices: [4, 0] });
  const patch = { rhythm: 'custom', customMelodyDurations: [[1, -1, 1, -1], ...Array.from({ length: 9 }, () => [])] };
  service.updatePartSettings({ kind: 'global-role', role: 'melody' }, patch);
  const tracks = service.project().tracks.filter(track => track.role === 'melody');
  assert.equal(tracks[0].generatorSettings.transpose, undefined);
  assert.equal(tracks[1].generatorSettings.transpose, 12);
  assert.equal(tracks[1].generatorSettings.patternSeed, 123);
  assert.deepEqual(service.project().arrangement[0].trackPartOverrides[second].chordNoteChoices, [4, 0]);
  preferences.beginQuickSettings();
  preferences.moveControl('melody', 'customMelodyDurations');
  for (const key of ['rhythm', 'patternFlip', 'customMelodyDurations', 'customDurationChances', 'customDurationRandomWeighting']) assert.equal(preferences.quickSettings().melody.includes(key), false);
  preferences.moveControl('melody', 'rhythm');
  for (const key of ['rhythm', 'patternFlip', 'customMelodyDurations', 'customDurationChances', 'customDurationRandomWeighting']) assert.equal(preferences.quickSettings().melody.includes(key), true);
});

test('melody duration drawing and validation stay in the draft, save once, and reject stale project state', async () => {
  const scope = { kind: 'global-track', trackId: 'track-melody-1' };
  const { service, load } = fixture({ dialogData: { scope } });
  const { MelodyDurationsDialogComponent } = load('shared/melody-durations-dialog.component.ts');
  const popup = new MelodyDurationsDialogComponent();
  let closed = false; popup.dialog.close = () => { closed = true; };
  const before = service.exportProjectJson(), position = service.historyPosition();
  const area = { getBoundingClientRect: () => ({ left: 0, width: 400 }), setPointerCapture() {} };
  const event = x => ({ button: 0, pointerId: 1, clientX: x, currentTarget: area, preventDefault() {} });
  popup.start(event(0), 1); popup.move(event(199));
  assert.deepEqual(popup.rows()[1], [2, -2]);
  popup.cancel(); assert.deepEqual(popup.rows()[1], []);
  popup.flipped.set(true); popup.start(event(0), 1); popup.move(event(199)); popup.finish();
  assert.deepEqual(popup.rows()[1], [-2, -2], 'drawing notes while flipped stores underlying pauses');
  popup.editRow(1, ''); popup.flipped.set(false);
  popup.editRow(0, '-4'); assert.match(popup.error(), /only pauses/); assert.equal(popup.canSave(), false);
  popup.editRow(0, '1, -1, 1, -1');
  popup.validate(); await new Promise(done => setTimeout(done, 5));
  assert.equal(popup.canSave(), true);
  assert.equal(service.exportProjectJson(), before); assert.equal(service.historyPosition(), position);
  popup.save(); assert.equal(closed, true); assert.equal(service.historyPosition(), position + 1);
  const stale = new MelodyDurationsDialogComponent();
  stale.editRow(0, '0.5, -0.5, 1, -2'); stale.validate();
  service.updateSettings({ name: 'Changed while editing durations' });
  await new Promise(done => setTimeout(done, 5));
  assert.equal(stale.stale(), true); assert.equal(stale.canSave(), false);
  const saved = service.exportProjectJson(); stale.save(); assert.equal(service.exportProjectJson(), saved);
});

test('failed project loads warn with property paths and preserve the current project', () => {
  const { service, stored, load } = fixture();
  const before = service.exportProjectJson();
  const warnings = [];
  const originalWarn = console.warn;
  console.warn = (...args) => warnings.push(args);
  try {
    const invalid = JSON.parse(before);
    delete invalid.name;
    invalid.tempoBpm = 300;
    invalid.drums.obsoleteGroove = 'rock';
    invalid.tracks[0].generatorSettings.unknownControl = 1;
    invalid.mix.bass.panPercent = 200;
    assert.equal(service.importProjectJson(JSON.stringify(invalid)), false);
    assert.equal(service.exportProjectJson(), before);
    assert.equal(warnings.length, 1);
    const details = warnings[0][1].join('\n');
    assert.match(details, /project\.name: missing required property/);
    assert.match(details, /project\.tempoBpm: expected an integer from 30 to 240/);
    assert.match(details, /project\.drums\.obsoleteGroove: unknown property/);
    assert.match(details, /project\.tracks\[0\]\.generatorSettings\.unknownControl: unknown property/);
    assert.match(details, /project\.mix\.bass\.panPercent/);

    warnings.length = 0;
    assert.equal(service.importProjectJson('{'), false);
    assert.equal(warnings.length, 1);
    assert.ok(warnings[0][1] instanceof SyntaxError);

    warnings.length = 0;
    stored.set('vibecomposer.project.v12', JSON.stringify(invalid));
    const { ProjectService } = load('core/project/project.service.ts');
    const restored = new ProjectService();
    assert.equal(warnings.length, 1);
    assert.match(warnings[0][0], /session storage \(vibecomposer\.project\.v12\)/);
    assert.match(warnings[0][1].join('\n'), /project\.name: missing required property/);
    assert.ok(restored.project().name);

    warnings.length = 0;
    const valid = JSON.parse(before);
    valid.schemaVersion = 999;
    assert.equal(service.importProjectJson(JSON.stringify(valid)), true);
    assert.equal(warnings.length, 0);
  } finally {
    console.warn = originalWarn;
  }
});

function fixture({ compactHistory = false, dialogData = null, reactivity } = {}) {
  const cache = new Map();
  const effects = [];
  // Tests that exercise effect-driven behavior flush callbacks after changing state.
  const flushEffects = () => { for (const callback of effects) callback(); };
  const signal = reactivity?.signal ?? ((value) => {
    const read = () => value;
    read.set = (next) => { value = next; };
    read.update = updater => { value = updater(value); };
    read.asReadonly = () => read;
    return read;
  });
  const input = (value) => signal(value);
  input.required = () => signal(undefined);
  let service, ui, uiPreferences;
  const angular = { Injectable: () => (type) => type, Component: () => (type) => type, Directive: () => (type) => type,
    HostListener: () => () => {}, Injector: class Injector {}, afterNextRender: () => {}, Input: () => () => {}, Output: () => () => {}, EventEmitter: class { values = []; emit(value) { this.values.push(value); } },
    signal, computed: reactivity?.computed ?? ((read) => read), effect: callback => { effects.push(callback); }, untracked: read => read(), input,
    output: () => ({ emit() {} }), ElementRef: class ElementRef {}, ChangeDetectionStrategy: { OnPush: 0 },
    viewChild: Object.assign(() => () => undefined, { required: () => () => undefined }),
    inject: (type) => type === 'sequencer-data' ? dialogData ?? { scope: { kind: 'global-track', trackId: 'track-chords-1' }, role: 'chords', velocities: false, melodyHits: 16 } : type?.name === 'ProjectService' ? service : type?.name === 'WorkspaceUiService' ? ui
      : type?.name === 'AudioPlaybackService' ? {} : type?.name === 'MidiOutputService' ? { drumMapping: () => 'general-midi', drumLabel: pitch => load('core/music/drum-instruments.ts').drumInstrumentLabel(pitch, 'general-midi') }
      : type?.name === 'UiPreferencesService' ? uiPreferences : preferences,
  };
  const root = path.join(__dirname, '../src/app');
  function load(file) {
    const absolute = path.resolve(root, file);
    // Workspace state is real; child rendering components are covered by the Angular build and overview tests.
    if (absolute.endsWith('.component.ts') && !['workspace-canvas.component.ts', 'transport-dock.component.ts', 'score-canvas.component.ts', 'edit-workspace.component.ts', 'mix-buttons.component.ts', 'part-settings-editor.component.ts', 'pattern-sequencer.component.ts', 'melody-durations-dialog.component.ts', 'compact-knob.component.ts', 'melody-global-settings.component.ts', 'harmony-settings.component.ts', 'section-context-editor.component.ts']
      .some((name) => absolute.endsWith(name))) return {};
    if (cache.has(absolute)) return cache.get(absolute).exports;
    const module = { exports: {} };
    cache.set(absolute, module);
    let compiled = compiledModules.get(absolute);
    if (compiled === undefined) {
      compiled = ts.transpileModule(fs.readFileSync(absolute, 'utf8'), { compilerOptions: {
        target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, experimentalDecorators: true,
      } }).outputText;
      compiledModules.set(absolute, compiled);
    }
    new Function('require', 'module', 'exports', compiled)((name) => name === '@angular/core' ? angular
      : name === '@angular/cdk/dialog' ? { Dialog: class Dialog {}, DialogModule: class {}, DialogRef: class {}, DIALOG_DATA: 'sequencer-data' }
      : name === '@angular/common' ? { TitleCasePipe: class {} }
      : name.startsWith('.') ? load(path.resolve(path.dirname(absolute), name + '.ts')) : require(name), module, module.exports);
    return module.exports;
  }
  const stored = new Map();
  global.sessionStorage = { getItem: (key) => stored.get(key) ?? null, setItem: (key, value) => stored.set(key, value) };
  const sectionTypes = load('core/project/section-type-settings.service.ts');
  const preferences = { chances: () => sectionTypes.DEFAULT_SECTION_TYPE_CHANCES };
  const uiStored = new Map();
  global.localStorage = { getItem: key => uiStored.get(key) ?? null, setItem: (key, value) => uiStored.set(key, value) };
  uiPreferences = new (load('core/ui/ui-preferences.service.ts').UiPreferencesService)();
  const { ProjectService } = load('core/project/project.service.ts');
  service = new ProjectService();
  // Most workflows assert one undo entry per edit; compact-history tests use the app default.
  if (!compactHistory) service.toggleCompactHistory();
  const { WorkspaceUiService } = load('shared/workspace-ui.service.ts');
  ui = new WorkspaceUiService();
  const { WorkspaceCanvasComponent } = load('shared/workspace-canvas.component.ts');
  const workspace = new WorkspaceCanvasComponent();
  const phrase = load('core/music/phrase.ts');
  const settings = load('core/music/part-settings.ts');
  const midi = load('core/music/midi-export.ts');
  return { service, ui, workspace, phrase, settings, midi, stored, load, uiPreferences, uiStored, flushEffects };
}

test('cell patches, individual exceptions, zero/false, and resets preserve inheritance', () => {
  const { service, settings } = fixture();
  const id = service.duplicateTrack('track-bass-1');
  const sectionId = service.project().arrangement[1].id;
  const cell = { kind: 'section-role', sectionId, role: 'bass' };
  const part = { kind: 'section-track', sectionId, trackId: id };
  service.updatePartSettings({ kind: 'global-track', trackId: id }, { transpose: 12, octaveInterval: true });
  service.updatePartSettings(cell, { transpose: -12, octaveInterval: false });
  service.updatePartSettings(part, { transpose: 0 });
  const effective = () => settings.resolvePartTrack(service.project().tracks.find((track) => track.id === id), service.project().arrangement[1]).generatorSettings;
  assert.equal(effective().transpose, 0);
  assert.equal(effective().octaveInterval, false);
  service.resetPartSettings(part, 'transpose');
  assert.equal(effective().transpose, -12);
  service.resetPartSettings(cell);
  assert.equal(effective().transpose, 12);
  assert.equal(effective().octaveInterval, true);
  assert.equal(service.project().tracks.find((track) => track.id === id).generatorSettings.transpose, 12);
});

test('a patch affects only its section while MIDI export uses the same resolved notes', () => {
  const { service, phrase, midi } = fixture();
  const trackId = 'track-chords-1';
  service.project().arrangement.forEach((_, index) => service.setSectionTrackPresence(index, trackId, true));
  const before = phrase.layOutTrackPhrase(service.project(), service.project().tracks.find((track) => track.id === trackId));
  const section = service.project().arrangement[1];
  service.updatePartSettings({ kind: 'section-track', sectionId: section.id, trackId },
    { transpose: 12, noteLengthPercent: 50, velocityMin: 110, velocityMax: 110 });
  const after = phrase.layOutTrackPhrase(service.project(), service.project().tracks.find((track) => track.id === trackId));
  const start = service.project().arrangement[0].measures * 4;
  const end = start + section.measures * 4;
  for (let index = 0; index < before.length; index++) {
    if (before[index].startBeat >= start && before[index].startBeat < end) {
      assert.equal(after[index].midi, before[index].midi + 12);
      assert.equal(after[index].durationBeats, before[index].durationBeats / 2);
      assert.equal(after[index].velocity, 110);
    } else assert.deepEqual(after[index], before[index]);
  }
  const exported = new Midi(midi.generateCompositionMidi(service.project()));
  const notes = exported.tracks.find((track) => track.name === 'C1').notes;
  assert.equal(notes.length, after.length);
  for (let index = 0; index < notes.length; index++) {
    assert.equal(notes[index].midi, after[index].midi);
    assert.ok(Math.abs(notes[index].ticks / exported.header.ppq - after[index].startBeat) < 0.003);
    assert.ok(Math.abs(notes[index].durationTicks / exported.header.ppq - after[index].durationBeats) < 0.003);
  }
});

test('mixed values are real aggregates and cell edits preserve track exceptions', () => {
  const { service, ui, workspace } = fixture();
  const id = service.duplicateTrack('track-chords-1');
  service.updatePartSettings({ kind: 'global-track', trackId: id }, { transpose: 12 });
  const sectionId = service.project().arrangement[1].id;
  ui.selectCell(sectionId, 'chords');
  assert.equal(workspace.partValues().transpose, null);
  service.updatePartSettings({ kind: 'section-track', sectionId, trackId: id }, { transpose: 24 });
  workspace.editPartSettings(workspace.partScope(), { key: 'transpose', value: -12 });
  assert.equal(workspace.partValues().transpose, null);
  assert.equal(workspace.trackExceptionCount(), 1);
  ui.selectSectionTrack(sectionId, id);
  assert.equal(workspace.partValues().transpose, 24);
  workspace.resetPartSettings(workspace.partScope());
  assert.equal(workspace.partValues().transpose, -12);
});

test('patches persist through history, JSON, presence rerolls, duplication, and track deletion', () => {
  const { service } = fixture();
  const sectionId = service.project().arrangement[1].id;
  const scope = { kind: 'section-track', sectionId, trackId: 'track-arpeggio-1' };
  service.updatePartSettings(scope, { transpose: 12, rate: 'sixteenth' });
  service.undo();
  assert.equal(service.project().arrangement[1].trackPartOverrides?.[scope.trackId], undefined);
  service.redo();
  const json = service.exportProjectJson();
  assert.equal(service.importProjectJson(json), true);
  assert.deepEqual(service.project().arrangement[1].trackPartOverrides[scope.trackId], { transpose: 12, rate: 'sixteenth' });
  service.randomizeArrangementPresence();
  assert.deepEqual(service.project().arrangement[1].trackPartOverrides[scope.trackId], { transpose: 12, rate: 'sixteenth' });
  const duplicate = service.duplicateTrack(scope.trackId);
  assert.deepEqual(service.project().arrangement[1].trackPartOverrides[duplicate], { transpose: 12, rate: 'sixteenth' });
  service.duplicateSection(1);
  assert.notEqual(service.project().arrangement[1].trackPartOverrides, service.project().arrangement[2].trackPartOverrides);
  service.removeTrack(duplicate);
  assert.equal(service.project().arrangement[1].trackPartOverrides[duplicate], undefined);
  assert.equal(service.importProjectJson(service.exportProjectJson()), true);
});

test('imports and mutations reject wrong-role fields, unknown tracks, and invalid effective ranges', () => {
  const { service } = fixture();
  const initial = service.exportProjectJson();
  const sectionId = service.project().arrangement[1].id;
  service.updatePartSettings({ kind: 'section-role', sectionId, role: 'bass' }, { voicing: 'open' });
  assert.equal(service.exportProjectJson(), initial);
  service.updatePartSettings({ kind: 'section-role', sectionId, role: 'bass' }, { velocityMin: 100 });
  assert.equal(service.exportProjectJson(), initial, 'effective min cannot exceed inherited max');
  for (const patch of [null, [], { rhythm: 'melody1' }, { octaveInterval: 0 }, { transpose: 99 }, { unknown: 1 }]) {
    const imported = JSON.parse(initial);
    imported.arrangement[1].rolePartOverrides = { bass: patch };
    assert.equal(service.importProjectJson(JSON.stringify(imported)), false);
  }
  const orphan = JSON.parse(initial);
  orphan.arrangement[1].trackPartOverrides = { missing: { transpose: 12 } };
  assert.equal(service.importProjectJson(JSON.stringify(orphan)), false);
});

test('manual note edits remain explicit replacements and are not discarded by generator patches', () => {
  const { service, phrase } = fixture();
  const id = 'track-bass-1';
  const manual = [{ id: 'manual', midi: 40, startBeat: 0, durationBeats: 1, velocity: 91 }];
  service.updateTrackPhrase(id, manual);
  service.setSectionTrackPresence(1, id, true);
  const track = () => service.project().tracks.find((candidate) => candidate.id === id);
  const before = phrase.layOutTrackPhrase(service.project(), track());
  service.updatePartSettings({ kind: 'section-track', sectionId: service.project().arrangement[1].id, trackId: id },
    { transpose: 24, octaveInterval: true, noteLengthMultiplier: 50 });
  assert.deepEqual(phrase.layOutTrackPhrase(service.project(), track()), before);
  assert.deepEqual(track().editedPhrase, manual);
});

test('invalid field resets preserve the valid patch and provide feedback', () => {
  const { service, ui, workspace } = fixture();
  const trackId = 'track-chords-1';
  service.updatePartSettings({ kind: 'global-track', trackId }, { velocityMin: 100, velocityMax: 110 });
  const sectionId = service.project().arrangement[1].id;
  const scope = { kind: 'section-track', sectionId, trackId };
  service.updatePartSettings(scope, { velocityMin: 90, velocityMax: 90 });
  ui.selectSectionTrack(sectionId, trackId);
  workspace.resetPartSettings(scope, 'velocityMin');
  assert.equal(workspace.partValues().velocityMin, 90);
  assert.ok(workspace.partEditError());
  workspace.resetPartSettings(scope);
  assert.equal(workspace.partValues().velocityMin, 100);
  assert.equal(workspace.partValues().velocityMax, 110);
  assert.equal(workspace.partEditError(), null);
});

test('secondary track seeds are applied once and layout matches the editor phrase', () => {
  const { service, phrase } = fixture();
  const id = service.duplicateTrack('track-bass-1');
  service.setSectionTrackPresence(0, id, true);
  const track = service.project().tracks.find((candidate) => candidate.id === id);
  const generated = phrase.generateTrackPhrase(service.project(), track).filter((note) => note.startBeat < 8);
  const arranged = phrase.layOutTrackPhrase(service.project(), track).filter((note) => note.startBeat < 8);
  assert.deepEqual(arranged, generated);
});

test('part transpose edits use octave steps at every scope and preserve readable saved values', () => {
  const { service, settings } = fixture();
  const sectionId = service.project().arrangement[1].id;
  for (const role of ['bass', 'chords', 'arpeggio']) {
    assert.equal(settings.PART_CONTROLS[role].find((control) => control.key === 'transpose').step, 12);
    const trackId = `track-${role}-1`;
    const scopes = [{ kind: 'global-role', role }, { kind: 'global-track', trackId },
      { kind: 'section-role', sectionId, role }, { kind: 'section-track', sectionId, trackId }];
    for (const scope of scopes) {
      for (const transpose of [-36, -24, -12, 0, 12, 24, 36]) {
        service.updatePartSettings(scope, { transpose });
        const track = service.project().tracks.find((candidate) => candidate.id === trackId);
        const section = 'sectionId' in scope ? service.project().arrangement[1] : undefined;
        assert.equal(settings.resolvePartTrack(track, section).generatorSettings.transpose, transpose);
      }
      const before = service.exportProjectJson();
      for (const transpose of [-37, -7, -5, 1, 5, 7, 13, 37]) service.updatePartSettings(scope, { transpose });
      assert.equal(service.exportProjectJson(), before);
      if ('sectionId' in scope) service.resetPartSettings(scope);
    }
  }
  const saved = JSON.parse(service.exportProjectJson());
  const trackId = 'track-bass-1';
  saved.tracks.find((track) => track.id === trackId).generatorSettings.transpose = 5;
  saved.arrangement[1].trackPartOverrides = { [trackId]: { transpose: 7 } };
  assert.equal(service.importProjectJson(JSON.stringify(saved)), true, 'old saved values are not discarded');
  assert.equal(service.project().tracks.find((track) => track.id === trackId).generatorSettings.transpose, 5);
  assert.equal(service.project().arrangement[1].trackPartOverrides[trackId].transpose, 7);
  service.updatePartSettings({ kind: 'section-track', sectionId, trackId }, { transpose: 12 });
  assert.equal(service.project().arrangement[1].trackPartOverrides[trackId].transpose, 12);
});

test('melody has explicit type zero and generated tracks, with stable existing role seeds and channels', () => {
  const { service, ui, workspace, phrase, load } = fixture();
  const model = load('core/project/project.model.ts');
  assert.deepEqual(model.ARRANGED_PARTS.map((role) => model.PART_TYPES[role]), [0, 1, 2, 3, 4]);
  assert.equal(workspace.arrangedTracks()[0].role, 'melody');
  const melody = service.project().tracks.find((track) => track.role === 'melody');
  assert.equal(melody.midiChannel, 4);
  assert.equal(service.project().tracks.find((track) => track.role === 'drums').midiChannel, 10);
  assert.ok(phrase.generateTrackPhrase(service.project(), melody).length > 0);
  ui.selectRole('melody');
  assert.equal(workspace.partGenerationAvailable(), true);
  assert.ok(workspace.editableScopes().length > 0);
  const existing = service.project().tracks.filter((track) => track.role !== 'melody');
  const notes = existing.map((track) => phrase.generateTrackPhrase(service.project(), track));
  const saved = JSON.parse(service.exportProjectJson());
  delete saved.melody; delete saved.mix.melody;
  saved.tracks = saved.tracks.filter((track) => track.role !== 'melody');
  for (const section of saved.arrangement) delete section.trackPresence[melody.id];
  assert.equal(service.importProjectJson(JSON.stringify(saved)), true);
  assert.deepEqual(JSON.parse(JSON.stringify(service.project().tracks)), existing, 'loading a four-role project preserves its track identities');
  for (let index = 0; index < existing.length; index++) {
    assert.deepEqual(phrase.generateTrackPhrase(service.project(), existing[index]), notes[index]);
  }
  assert.equal(workspace.partRole(), 'melody', 'an empty selected group must not borrow the bass context');
  const id = service.addTrack('melody');
  assert.equal(id, 'track-melody-1');
  assert.equal(service.project().tracks.find((track) => track.id === id).midiChannel, 4);
  assert.equal(workspace.arrangedTracks()[0].id, id);
});

test('empty roles can be recreated from their defaults, while the final project track is retained', () => {
  const { service, workspace, load } = fixture();
  const model = load('core/project/project.model.ts');
  for (const role of model.ARRANGED_PARTS) {
    const track = service.project().tracks.find((candidate) => candidate.role === role);
    const defaults = service.project()[role];
    service.removeTrack(track.id);
    assert.equal(service.project().tracks.some((candidate) => candidate.role === role), false);
    const id = service.addTrack(role);
    const added = service.project().tracks.find((candidate) => candidate.id === id);
    assert.equal(added.role, role);
    assert.deepEqual(added.generatorSettings, defaults);
    assert.equal(added.editedPhrase, undefined);
    assert.equal(added.midiChannel === 10, role === 'drums');
    for (const section of service.project().arrangement) assert.equal(typeof section.trackPresence[id], 'boolean');
  }
  for (const track of [...service.project().tracks].slice(0, -1)) service.removeTrack(track.id);
  const last = service.project().tracks[0];
  assert.equal(workspace.canRemoveTrack(last.id), false);
  const before = service.exportProjectJson();
  service.removeTrack(last.id);
  assert.equal(service.exportProjectJson(), before);
});

test('manual melody notes work through the editor, scoped presence, history, persistence, and parsed MIDI', () => {
  const { service, ui, workspace, phrase, midi, stored, load } = fixture();
  const id = 'track-melody-1';
  service.updateSettings({ seed: '9223372036854775807' });
  service.project().arrangement.forEach((_, index) => service.setSectionTrackPresence(index, id, index === 0));
  workspace.openTrackEditor(id);
  assert.equal(workspace.editing(), true);
  assert.equal(ui.selectedTrackId(), id);
  const { EditWorkspaceComponent } = load('features/edit/edit-workspace.component.ts');
  const editor = new EditWorkspaceComponent();
  editor.ngOnInit();
  assert.equal(editor.generationAvailable(), true);
  assert.equal(editor.tracks()[0].role, 'melody');
  const before = service.exportProjectJson();
  editor.addNote();
  assert.ok(editor.noteCount() > 0);
  assert.equal(service.exportProjectJson(), before, 'draft edits do not write project history');
  const draft = editor.notes().map(({ trackId, ...note }) => note);
  editor.applyChanges();
  workspace.closeEditor();
  const track = () => service.project().tracks.find((candidate) => candidate.id === id);
  assert.deepEqual(track().editedPhrase, draft);
  const laidOut = phrase.layOutTrackPhrase(service.project(), track());
  assert.ok(laidOut.length > 0);
  const exported = new Midi(midi.generateCompositionMidi(service.project()));
  assert.equal(exported.tracks[0].name, 'M1');
  assert.equal(exported.tracks[0].channel, 3);
  assert.equal(exported.tracks[0].instrument.number, 73);
  assert.equal(exported.tracks[0].notes[0].midi, draft[0].midi);
  assert.equal(exported.tracks[0].notes.length, laidOut.length);
  service.undo();
  assert.equal(service.exportProjectJson(), before, 'Apply writes exactly one history entry');
  service.redo();
  assert.deepEqual(track().editedPhrase, draft);
  const saved = service.exportProjectJson();
  assert.equal(service.importProjectJson(saved), true);
  const { ProjectService } = load('core/project/project.service.ts');
  const restored = new ProjectService();
  assert.deepEqual(restored.project(), service.project());
  assert.equal(restored.project().seed, '9223372036854775807');
  assert.ok([...stored.values()].some((value) => value.includes('track-melody-1')));
});

test('melody duplication keeps independent notes/mix and removing its final role member is undoable', () => {
  const { service, workspace, ui } = fixture();
  const id = 'track-melody-1';
  const notes = [{ id: 'melody-note', midi: 72, startBeat: 1, durationBeats: 0.5, velocity: 90 }];
  service.updateTrackPhrase(id, notes);
  service.updateTrack(id, { mix: { program: 40, panPercent: -20 } });
  const copy = service.duplicateTrack(id);
  const duplicate = service.project().tracks.find((track) => track.id === copy);
  assert.deepEqual(duplicate.editedPhrase, notes);
  assert.equal(duplicate.mix.program, 40);
  assert.notEqual(duplicate.midiChannel, 10);
  assert.notEqual(duplicate.midiChannel, 4);
  service.updateTrack(copy, { mix: { panPercent: 50 } });
  assert.equal(service.project().tracks.find((track) => track.id === id).mix.panPercent, -20);
  ui.selectTrack(copy);
  workspace.restoreTrackPhrase();
  assert.equal(service.project().tracks.find((track) => track.id === copy).editedPhrase, undefined);
  service.undo();
  service.removeTrack(copy);
  service.removeTrack(id);
  assert.equal(service.project().tracks.some((track) => track.role === 'melody'), false);
  assert.equal(ui.settingsTarget().kind, 'global-track');
  service.undo();
  assert.deepEqual(service.project().tracks.find((track) => track.id === id).editedPhrase, notes);
});

test('unsupported melody settings and legacy algorithms are rejected without changing the project', () => {
  const { service } = fixture();
  const sectionId = service.project().arrangement[0].id;
  const before = service.exportProjectJson();
  for (const scope of [{ kind: 'global-role', role: 'melody' }, { kind: 'global-track', trackId: 'track-melody-1' },
    { kind: 'section-role', sectionId, role: 'melody' }, { kind: 'section-track', sectionId, trackId: 'track-melody-1' }]) {
    service.updatePartSettings(scope, { transpose: 4 });
    assert.equal(service.exportProjectJson(), before);
  }
  for (const settings of [{ algorithm: 'legacy' }, {}, { algorithm: 'block', melodyLegacyMode: true }, { algorithm: 'block', speed: 101 }]) {
    const saved = JSON.parse(before);
    saved.tracks.find((track) => track.role === 'melody').generatorSettings = settings;
    assert.equal(service.importProjectJson(JSON.stringify(saved)), false);
    assert.equal(service.exportProjectJson(), before);
  }
});

test('freezing mixed cells preserves each effective track, including omitted defaults, with one undo step', () => {
  const { service, settings, phrase } = fixture();
  const first = 'track-bass-1';
  const second = service.duplicateTrack(first);
  const sectionId = service.project().arrangement[1].id;
  const cell = { kind: 'section-role', sectionId, role: 'bass' };
  service.updatePartSettings({ kind: 'global-track', trackId: second }, { transpose: 12, noteVariation: 80 });
  service.updatePartSettings(cell, { octaveInterval: false, velocityMin: 70 });
  service.updatePartSettings({ kind: 'section-track', sectionId, trackId: second }, { transpose: 24 });
  const original = service.exportProjectJson();
  const source = () => service.project().arrangement.find((section) => section.id === sectionId);
  const track = (id) => service.project().tracks.find((track) => track.id === id);
  const effective = (id) => settings.settingsValues(settings.resolvePartTrack(track(id), source()).generatorSettings, 'bass');
  const values = [effective(first), effective(second)];
  const notes = phrase.layOutTrackPhrase(service.project(), track(second));
  assert.equal(service.freezePartSettings(cell), 'changed');
  assert.deepEqual(source().trackPartOverrides[first], values[0]);
  assert.deepEqual(source().trackPartOverrides[second], values[1]);
  assert.notStrictEqual(source().trackPartOverrides[first], source().trackPartOverrides[second]);
  assert.deepEqual(phrase.layOutTrackPhrase(service.project(), track(second)), notes);
  service.undo();
  assert.equal(service.exportProjectJson(), original);
  service.redo();
  assert.equal(service.freezePartSettings(cell), 'unchanged');
  service.updatePartSettings({ kind: 'global-role', role: 'bass' }, { transpose: -12, noteVariation: 0, velocityMin: 75 });
  service.updatePartSettings(cell, { octaveInterval: true });
  assert.deepEqual([effective(first), effective(second)], values, 'snapshots shield every supported field');
  const third = service.addTrack('bass');
  assert.equal(effective(third).transpose, -12);
  assert.equal(effective(third).octaveInterval, true, 'new members keep inheriting the cell');
  const beforeReset = service.exportProjectJson();
  assert.equal(service.resetCellPartSettings(cell), 'changed');
  assert.equal(effective(first).transpose, -12);
  assert.equal(effective(first).velocityMin, 75);
  assert.equal(source().rolePartOverrides.bass, undefined);
  assert.equal(source().trackPartOverrides[first], undefined);
  service.undo();
  assert.equal(service.exportProjectJson(), beforeReset);
});

test('range override copying merges only its layer, preserves exceptions and presence, and is one undo step', () => {
  const { service, settings } = fixture();
  const first = 'track-bass-1';
  const second = service.duplicateTrack(first);
  const [source, destination, another] = service.project().arrangement;
  const cell = { kind: 'section-role', sectionId: source.id, role: 'bass' };
  service.updatePartSettings({ kind: 'global-role', role: 'bass' }, { transpose: 12, octaveInterval: true });
  service.updatePartSettings(cell, { transpose: 0, octaveInterval: false });
  service.updatePartSettings({ ...cell, sectionId: destination.id }, { transpose: 12, noteVariation: 75 });
  service.updatePartSettings({ kind: 'section-track', sectionId: destination.id, trackId: second }, { transpose: 24 });
  const before = service.exportProjectJson();
  const tracks = service.project().tracks;
  const presence = service.project().arrangement.map((section) => section.trackPresence);
  assert.equal(service.applyPartSettingsToSections(cell, [source.id, destination.id, another.id, destination.id], 'overrides'), 'changed');
  const sections = service.project().arrangement;
  assert.deepEqual(sections[1].rolePartOverrides.bass, { transpose: 0, noteVariation: 75, octaveInterval: false });
  assert.equal(settings.resolvePartTrack(tracks.find((track) => track.id === second), sections[1]).generatorSettings.transpose, 24);
  assert.strictEqual(service.project().tracks, tracks);
  assert.deepEqual(sections.map((section) => section.trackPresence), presence);
  assert.notStrictEqual(sections[0].rolePartOverrides.bass, sections[2].rolePartOverrides.bass);
  service.undo();
  assert.equal(service.exportProjectJson(), before);
  assert.equal(service.canRedo(), true);
  assert.equal(service.applyPartSettingsToSections(cell, [source.id], 'overrides'), 'unchanged');
  assert.equal(service.canRedo(), true, 'a no-op leaves redo intact');
  service.redo();
  const saved = service.exportProjectJson();
  assert.equal(service.importProjectJson(saved), true);
  assert.deepEqual(projectData(service.exportProjectJson()), projectData(saved));
  service.updatePartSettings({ ...cell, sectionId: another.id }, { transpose: -12 });
  assert.equal(service.project().arrangement[0].rolePartOverrides.bass.transpose, 0);
});

test('track copying distinguishes explicit overrides from effective snapshots and shares parsed MIDI output', () => {
  const { service, settings, phrase, midi, load } = fixture();
  const trackId = 'track-chords-1';
  const sections = service.project().arrangement;
  const source = { kind: 'section-track', sectionId: sections[0].id, trackId };
  const target = { ...source, sectionId: sections[1].id };
  service.project().arrangement.forEach((_, index) => service.setSectionTrackPresence(index, trackId, true));
  service.updatePartSettings({ kind: 'section-role', sectionId: source.sectionId, role: 'chords' }, { voicing: 'open', transpose: 12 });
  service.updatePartSettings(source, { noteLengthPercent: 50 });
  service.updatePartSettings(target, { transpose: -12, velocityMin: 110, velocityMax: 110 });
  assert.equal(service.applyPartSettingsToSections(source, [target.sectionId], 'overrides'), 'changed');
  assert.deepEqual(service.project().arrangement[1].trackPartOverrides[trackId],
    { transpose: -12, velocityMin: 110, velocityMax: 110, noteLengthPercent: 50 });
  const track = () => service.project().tracks.find((track) => track.id === trackId);
  const sourceValues = settings.settingsValues(settings.resolvePartTrack(track(), service.project().arrangement[0]).generatorSettings, 'chords');
  assert.equal(service.applyPartSettingsToSections(source, [target.sectionId], 'effective'), 'changed');
  assert.deepEqual(service.project().arrangement[1].trackPartOverrides[trackId], sourceValues);
  const notes = phrase.layOutTrackPhrase(service.project(), track());
  const exported = new Midi(midi.generateCompositionMidi(service.project())).tracks.find((track) => track.name === 'C1');
  assert.deepEqual(exported.notes.map((note) => note.midi), notes.map((note) => note.midi));
  const saved = service.exportProjectJson();
  const { ProjectService } = load('core/project/project.service.ts');
  assert.deepEqual(projectData(new ProjectService().exportProjectJson()), projectData(saved), 'snapshots survive session restore');
  service.resetPartSettings(target);
  assert.equal(settings.resolvePartTrack(track(), service.project().arrangement[1]).generatorSettings.transpose, undefined);
});

test('range conflicts and stale targets reject atomically without losing history or manual phrases', () => {
  const { service } = fixture();
  const trackId = 'track-bass-1';
  const sections = service.project().arrangement;
  const cell = { kind: 'section-role', sectionId: sections[0].id, role: 'bass' };
  service.updatePartSettings({ kind: 'global-track', trackId }, { velocityMax: 100 });
  service.updatePartSettings(cell, { velocityMin: 90 });
  service.updatePartSettings({ kind: 'section-track', sectionId: sections[1].id, trackId }, { velocityMax: 80 });
  service.updateTrackPhrase(trackId, [{ id: 'saved', midi: 48, startBeat: 0, durationBeats: 1, velocity: 80 }]);
  const before = service.exportProjectJson();
  const reference = service.project();
  assert.equal(service.applyPartSettingsToSections(cell, [sections[2].id, sections[1].id], 'overrides'), 'invalid');
  assert.strictEqual(service.project(), reference);
  assert.equal(service.applyPartSettingsToSections(cell, [sections[2].id, 'deleted'], 'effective'), 'invalid');
  assert.equal(service.freezePartSettings({ ...cell, sectionId: 'deleted' }), 'invalid');
  assert.equal(service.freezePartSettings({ kind: 'section-track', sectionId: cell.sectionId, trackId: 'deleted' }), 'invalid');
  assert.equal(service.exportProjectJson(), before);
  assert.equal(service.applyPartSettingsToSections(cell, [sections[1].id, sections[2].id], 'effective'), 'changed');
  assert.deepEqual(service.project().tracks.find((track) => track.id === trackId).editedPhrase,
    reference.tracks.find((track) => track.id === trackId).editedPhrase);
  service.undo();
  assert.equal(service.exportProjectJson(), before, 'a rejected action did not add a hidden history entry');
});

test('freezing imported semitone values retains their exact settings; empty and inherited scopes do not fabricate overrides', () => {
  const { service } = fixture();
  const saved = JSON.parse(service.exportProjectJson());
  const trackId = 'track-arpeggio-1';
  saved.tracks.find((track) => track.id === trackId).generatorSettings.transpose = 5;
  assert.equal(service.importProjectJson(JSON.stringify(saved)), true);
  const scope = { kind: 'section-track', sectionId: service.project().arrangement[0].id, trackId };
  assert.equal(service.applyPartSettingsToSections(scope, [service.project().arrangement[1].id], 'overrides'), 'unchanged');
  assert.equal(service.freezePartSettings(scope), 'changed');
  assert.equal(service.project().arrangement[0].trackPartOverrides[trackId].transpose, 5);
  assert.equal(service.importProjectJson(service.exportProjectJson()), true);
  service.removeTrack('track-drums-1');
  const empty = { kind: 'section-role', sectionId: scope.sectionId, role: 'drums' };
  const before = service.exportProjectJson();
  assert.equal(service.freezePartSettings(empty), 'invalid');
  assert.equal(service.resetCellPartSettings(empty), 'unchanged');
  assert.equal(service.exportProjectJson(), before);
});

test('workspace applies only to retained header destinations and reports the shared workflow result', () => {
  const { service, ui, workspace } = fixture();
  const sections = service.project().arrangement;
  ui.selectSection(sections[1].id);
  ui.selectSection(sections[2].id, { shiftKey: true });
  ui.selectCell(sections[0].id, 'chords');
  const selection = [...ui.selectedSectionIds()];
  assert.deepEqual(workspace.partDestinations().map((section) => section.id), selection);
  service.updatePartSettings(workspace.partScope(), { transpose: 12 });
  const before = service.exportProjectJson();
  workspace.runPartWorkflow('overrides');
  assert.match(workspace.partWorkflowMessage(), /2 selected section/);
  assert.deepEqual(ui.selectedSectionIds(), selection);
  assert.deepEqual(ui.settingsTarget(), { kind: 'section-role', sectionId: sections[0].id, role: 'chords' });
  service.undo();
  assert.equal(service.exportProjectJson(), before);
  assert.equal(workspace.partWorkflowMessage(), null, 'undo clears stale success feedback');
  workspace.runPartWorkflow('freeze');
  assert.match(workspace.partWorkflowMessage(), /frozen/);
  assert.equal(workspace.trackExceptionCount(), 1);
  workspace.runPartWorkflow('reset-cell');
  assert.equal(workspace.trackExceptionCount(), 0);
  assert.match(workspace.partWorkflowMessage(), /reset/);
  ui.selectCell(sections[1].id, 'chords');
  assert.equal(workspace.partDestinations().length, 1, 'the source is excluded even when inside the header range');
  assert.equal(workspace.partWorkflowMessage(), null);
});

test('copy previews follow stored patch changes without generation, history or property-order false positives', () => {
  const { service, ui, workspace } = fixture();
  const [source, destination, other] = service.project().arrangement;
  const scope = { kind: 'section-role', sectionId: source.id, role: 'bass' };
  ui.selectSection(destination.id);
  ui.selectCell(source.id, 'bass');
  const before = service.exportProjectJson();
  assert.deepEqual(workspace.partCopyAvailability(), { overrides: false, effective: true },
    'copy all still writes an explicit snapshot even when inherited effective values match');
  assert.equal(service.exportProjectJson(), before);
  assert.equal(service.canUndo(), false, 'preview is read-only');
  service.updatePartSettings(scope, { transpose: 12, octaveInterval: false });
  service.updatePartSettings({ ...scope, sectionId: destination.id }, { octaveInterval: false, transpose: 12 });
  assert.equal(workspace.partCopyAvailability().overrides, false);
  const same = service.project();
  assert.equal(service.applyPartSettingsToSections(scope, [destination.id], 'overrides'), 'unchanged');
  assert.strictEqual(service.project(), same, 'key ordering cannot add a bogus undo step');
  workspace.runPartWorkflow('effective');
  assert.deepEqual(workspace.partCopyAvailability(), { overrides: false, effective: false });
  service.undo();
  assert.equal(workspace.partCopyAvailability().effective, true);
  service.redo();
  assert.equal(workspace.partCopyAvailability().effective, false);
  service.updatePartSettings({ kind: 'section-track', sectionId: destination.id, trackId: 'track-bass-1' }, { transpose: 24 });
  assert.deepEqual(workspace.partCopyAvailability(), { overrides: false, effective: true });
  ui.selectSection(other.id);
  ui.selectCell(source.id, 'bass');
  assert.deepEqual(workspace.partCopyAvailability(), { overrides: true, effective: true });
  ui.clearSectionSelection();
  assert.deepEqual(workspace.partCopyAvailability(), { overrides: false, effective: false });
});

test('copy feedback is dismissible, clears for different destinations, and never displays a no-op checkmark', () => {
  const { service, ui, workspace } = fixture();
  const [source, destination, other] = service.project().arrangement;
  ui.selectSection(destination.id);
  ui.selectSectionTrack(source.id, 'track-chords-1');
  workspace.runPartWorkflow('overrides');
  assert.equal(workspace.partWorkflowMessage(), null);
  workspace.runPartWorkflow('effective');
  assert.match(workspace.partWorkflowMessage(), /applied/);
  workspace.dismissPartWorkflowFeedback();
  assert.equal(workspace.partWorkflowMessage(), null);
  service.undo();
  workspace.runPartWorkflow('effective');
  assert.match(workspace.partWorkflowMessage(), /applied/);
  ui.selectSection(other.id);
  ui.selectSectionTrack(source.id, 'track-chords-1');
  assert.equal(workspace.partWorkflowMessage(), null);
  workspace.runPartWorkflow('effective');
  assert.match(workspace.partWorkflowMessage(), /applied/);
  workspace.runPartWorkflow('effective');
  assert.equal(workspace.partWorkflowMessage(), null, 'a stale click or programmatic no-op clears feedback');
});

test('all fill masks match the actual Java enum, including odd halves, flipped silence and repeated slots', () => {
  const { load } = fixture();
  const { chordSpanFillMask, CHORD_SPAN_FILLS } = load('core/music/chord-span-fill.ts');
  const expected = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/chord-span-fill.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(Object.keys(expected).length, 264);
  for (const [key, mask] of Object.entries(expected)) {
    const [fill, length, flipped] = key.split('/');
    assert.deepEqual(chordSpanFillMask(Number(length), fill, flipped === 'true'), mask, key);
  }
  assert.equal(CHORD_SPAN_FILLS.length, 12);
  assert.deepEqual(chordSpanFillMask(5, 'HALF1'), [1, 1, 0, 0, 0]);
  assert.deepEqual(chordSpanFillMask(5, 'HALF2'), [0, 0, 1, 1, 1]);
  assert.deepEqual(chordSpanFillMask(3, 'ODD'), [0, 1, 0]);
  assert.deepEqual(chordSpanFillMask(3, 'EVEN'), [1, 0, 1]);
  for (const args of [[33], [-1], [1.5], [3, 'UNKNOWN'], [3, 'ALL', 1]]) {
    assert.throws(() => chordSpanFillMask(...args), RangeError);
  }
});

test('fill produces rests in every supported generator and preserves role-specific random consumption', () => {
  const { service, load } = fixture();
  const seed = 9223372036854775807n;
  const progression = [1, 1, 1, 1, 1];
  const { generateBassline } = load('core/music/bass-generator.ts');
  const { generateChordPart } = load('core/music/chord-generator.ts');
  const { generateArpeggio } = load('core/music/arpeggio-generator.ts');
  const { generateDrumPart } = load('core/music/drum-generator.ts');
  const calls = {
    bass: (settings) => generateBassline(seed, 'C', 'major', progression, 'full', 100, settings),
    chords: (settings) => generateChordPart(seed, 'C', 'major', progression, settings),
    arpeggio: (settings) => generateArpeggio(seed, 'C', 'major', progression, { ...settings, pattern: 'random' }),
    drums: (settings) => generateDrumPart(seed, progression.length, settings),
  };
  for (const [role, generate] of Object.entries(calls)) {
    const settings = service.project()[role];
    const baseline = generate(settings);
    assert.deepEqual(generate({ ...settings, chordSpanFill: 'ALL', fillFlip: false }), baseline, role + ' defaults');
    assert.deepEqual(generate({ ...settings, chordSpanFill: 'ALL', fillFlip: true }), [], role + ' silence');
    const filled = generate({ ...settings, chordSpanFill: 'ODD' });
    const slot = (event) => event.chordIndex ?? event.barIndex;
    assert.ok(filled.length > 0);
    assert.ok(filled.every((event) => slot(event) === 1 || slot(event) === 3), role);
    if (role !== 'bass') assert.deepEqual(filled, baseline.filter((event) => slot(event) % 2 === 1), role + ' rests keep streams');
    else {
      const strip = (event) => ({ midi: event.midi, duration: event.durationBeats, velocity: event.velocity });
      assert.deepEqual(filled.filter((event) => slot(event) === 1).map(strip),
        baseline.filter((event) => slot(event) === 0).map(strip), 'bass skips dynamics/variation for silent chords');
    }
  }
});

test('fill controls respect mixed scopes, track exceptions, flips and per-field inheritance', () => {
  const { service, ui, workspace, settings } = fixture();
  const first = 'track-arpeggio-1';
  const second = service.duplicateTrack(first);
  const sectionId = service.project().arrangement[1].id;
  service.updatePartSettings({ kind: 'global-track', trackId: second }, { chordSpanFill: 'EVEN' });
  ui.selectCell(sectionId, 'arpeggio');
  assert.equal(workspace.partValues().chordSpanFill, null);
  assert.equal(workspace.partValues().fillFlip, false);
  const cell = workspace.partScope();
  service.updatePartSettings(cell, { chordSpanFill: 'HALF1', fillFlip: true });
  service.updatePartSettings({ kind: 'section-track', sectionId, trackId: second }, { chordSpanFill: 'F23', fillFlip: false });
  assert.equal(workspace.partValues().chordSpanFill, null);
  assert.equal(workspace.partValues().fillFlip, null);
  service.resetPartSettings({ kind: 'section-track', sectionId, trackId: second }, 'fillFlip');
  assert.equal(workspace.partValues().fillFlip, true);
  const effective = (id) => settings.resolvePartTrack(service.project().tracks.find((track) => track.id === id),
    service.project().arrangement[1]).generatorSettings;
  assert.equal(effective(first).chordSpanFill, 'HALF1');
  assert.equal(effective(second).chordSpanFill, 'F23');
  service.resetPartSettings(cell);
  assert.equal(effective(first).chordSpanFill, undefined);
  assert.equal(effective(second).chordSpanFill, 'F23');
});

test('section fills repeat over the effective progression and reach score/preview/editor and MIDI without changing presence', () => {
  const { service, ui, workspace, phrase, midi } = fixture();
  const trackId = 'track-chords-1';
  const sectionId = service.project().arrangement[1].id;
  service.project().arrangement.forEach((_, index) => service.setSectionTrackPresence(index, trackId, true));
  service.updateSection(1, { measures: 8 });
  service.setSectionChordDegree(1, 0, 1);
  // The section override covers all eight measures; use an odd global progression separately below.
  const before = service.project();
  const track = () => service.project().tracks.find((track) => track.id === trackId);
  const base = phrase.layOutTrackPhrase(before, track());
  const source = { kind: 'section-track', sectionId, trackId };
  service.updatePartSettings(source, { chordSpanFill: 'ODD' });
  const start = before.arrangement[0].measures * 4;
  const end = start + before.arrangement[1].measures * 4;
  const notes = phrase.layOutTrackPhrase(service.project(), track());
  const expected = base.filter((note) => note.startBeat < start || note.startBeat >= end
    || Math.floor((note.startBeat - start) / 4) % 2 === 1);
  const withoutId = (notes) => notes.map(({ id, ...note }) => note);
  assert.deepEqual(withoutId(notes), withoutId(expected));
  assert.deepEqual(service.project().arrangement.map((section) => section.trackPresence), before.arrangement.map((section) => section.trackPresence));
  const exported = new Midi(midi.generateCompositionMidi(service.project()));
  const midiNotes = exported.tracks.find((track) => track.name === 'C1').notes;
  assert.equal(midiNotes.length, notes.length);
  midiNotes.forEach((note, index) => {
    assert.equal(note.midi, notes[index].midi);
    assert.ok(Math.abs(note.ticks / exported.header.ppq - notes[index].startBeat) < 0.003);
  });
  assert.equal(workspace.scoreNotes().filter((note) => note.part === trackId).length, notes.length);
  ui.selectSectionTrack(sectionId, trackId);
  assert.equal(workspace.partValues().chordSpanFill, 'ODD');
  service.clearSectionChordOverrides(1);
  service.setProgression([1, 5, 6]);
  service.updatePartSettings(source, { chordSpanFill: 'HALF2' });
  const local = phrase.layOutTrackPhrase(service.project(), track()).filter((note) => note.startBeat >= start && note.startBeat < end);
  assert.deepEqual([...new Set(local.map((note) => Math.floor((note.startBeat - start) / 4)))], [1, 2, 4, 5, 7]);
});

test('fill values survive copy/freeze, one-entry history, duplication and session/JSON; manual notes keep masking them', () => {
  const { service, phrase, load } = fixture();
  const trackId = 'track-drums-1';
  const [first, second] = service.project().arrangement;
  const source = { kind: 'section-track', sectionId: first.id, trackId };
  const before = service.exportProjectJson();
  service.updatePartSettings(source, { chordSpanFill: 'F34', fillFlip: true });
  service.undo();
  assert.equal(service.exportProjectJson(), before);
  service.redo();
  assert.equal(service.applyPartSettingsToSections(source, [second.id], 'overrides'), 'changed');
  assert.equal(service.project().arrangement[1].trackPartOverrides[trackId].fillFlip, true);
  assert.equal(service.freezePartSettings(source), 'changed');
  const copy = service.duplicateTrack(trackId);
  assert.equal(service.project().arrangement[0].trackPartOverrides[copy].chordSpanFill, 'F34');
  service.duplicateSection(0);
  assert.equal(service.project().arrangement[1].trackPartOverrides[trackId].chordSpanFill, 'F34');
  const saved = service.exportProjectJson();
  const { ProjectService } = load('core/project/project.service.ts');
  assert.deepEqual(projectData(new ProjectService().exportProjectJson()), projectData(saved));
  assert.equal(service.importProjectJson(saved), true);
  const manual = [{ id: 'manual', midi: 36, startBeat: 0, durationBeats: 0.5, velocity: 90 }];
  service.updateTrackPhrase(trackId, manual);
  service.updatePartSettings({ kind: 'global-track', trackId }, { chordSpanFill: 'ALL', fillFlip: true });
  assert.deepEqual(phrase.phraseForTrack(service.project(), service.project().tracks.find((track) => track.id === trackId)), manual);
});

test('invalid fills fail at every scope and on import; omitted fields keep older projects readable', () => {
  const { service } = fixture();
  const sectionId = service.project().arrangement[0].id;
  for (const role of ['bass', 'chords', 'arpeggio', 'drums']) {
    const trackId = service.project().tracks.find((track) => track.role === role).id;
    const before = service.exportProjectJson();
    const scopes = [{ kind: 'global-role', role }, { kind: 'global-track', trackId },
      { kind: 'section-role', sectionId, role }, { kind: 'section-track', sectionId, trackId }];
    for (const scope of scopes) {
      for (const patch of [{ chordSpanFill: 'half' }, { chordSpanFill: null }, { fillFlip: 1 }]) {
        service.updatePartSettings(scope, patch);
        assert.equal(service.exportProjectJson(), before);
      }
    }
    const invalid = JSON.parse(before);
    invalid.tracks.find((track) => track.id === trackId).generatorSettings.chordSpanFill = 'UNKNOWN';
    assert.equal(service.importProjectJson(JSON.stringify(invalid)), false);
    assert.equal(service.importProjectJson(before), true);
    assert.equal(service.project().tracks.find((track) => track.id === trackId).generatorSettings.chordSpanFill, undefined);
  }
  const before = service.exportProjectJson();
  service.updatePartSettings({ kind: 'section-role', sectionId, role: 'melody' }, { chordSpanFill: 'ODD' });
  assert.equal(service.project().arrangement[0].rolePartOverrides.melody.chordSpanFill, 'ODD');
  service.undo();
  assert.equal(service.exportProjectJson(), before);
});

test('manually restoring numeric, choice and checkbox defaults removes local overrides and customized UI', () => {
  const { service, ui, workspace, phrase } = fixture();
  const sectionId = service.project().arrangement[1].id;
  const trackId = 'track-chords-1';
  ui.selectSectionTrack(sectionId, trackId);
  const scope = workspace.partScope();
  const track = () => service.project().tracks.find((track) => track.id === trackId);
  const originalNotes = phrase.layOutTrackPhrase(service.project(), track());
  for (const [key, custom, inherited] of [['transpose', 12, 0], ['chordSpanFill', 'ODD', 'ALL'],
    ['fillFlip', true, false], ['velocityMin', 80, 69]]) {
    workspace.editPartSettings(scope, { key, value: custom });
    assert.deepEqual(workspace.overriddenKeys(), [key]);
    assert.equal(workspace.inheritanceLabel(), 'Customized track part');
    workspace.editPartSettings(scope, { key, value: inherited });
    assert.deepEqual(workspace.overriddenKeys(), []);
    assert.equal(workspace.inheritanceLabel(), 'Inherited from global tracks');
    assert.equal(workspace.partEditError(), null);
    assert.equal(service.project().arrangement[1].trackPartOverrides[trackId], undefined);
    service.undo();
    assert.equal(workspace.partValues()[key], custom);
    service.redo();
    assert.equal(workspace.partValues()[key], inherited);
  }
  assert.deepEqual(phrase.layOutTrackPhrase(service.project(), track()), originalNotes);
  const before = service.project();
  workspace.editPartSettings(scope, { key: 'transpose', value: 0 });
  assert.strictEqual(service.project(), before, 'choosing an already inherited value does not add history');
});

test('cell cleanup compares all bases while track cleanup inherits its cell, preserving unrelated exceptions', () => {
  const { service, ui, workspace } = fixture();
  const first = 'track-bass-1';
  const second = service.duplicateTrack(first);
  const sectionId = service.project().arrangement[1].id;
  const cell = { kind: 'section-role', sectionId, role: 'bass' };
  const part = { kind: 'section-track', sectionId, trackId: second };
  service.updatePartSettings({ kind: 'global-track', trackId: second }, { transpose: 12 });
  service.updatePartSettings(cell, { transpose: -12, chordSpanFill: 'ODD' });
  service.updatePartSettings(cell, { transpose: 0 });
  ui.selectCell(sectionId, 'bass');
  assert.ok(workspace.overriddenKeys().includes('transpose'), 'one matching base is insufficient in a mixed group');
  service.updatePartSettings(part, { transpose: 24, chordSpanFill: 'EVEN' });
  service.updatePartSettings(part, { transpose: 0 });
  ui.selectSectionTrack(sectionId, second);
  assert.deepEqual(workspace.overriddenKeys(), ['chordSpanFill'], 'track inherits cell transpose 0, rather than global 12');
  assert.equal(workspace.partValues().transpose, 0);
  service.updatePartSettings(part, { chordSpanFill: 'ODD' });
  assert.deepEqual(workspace.overriddenKeys(), []);
  assert.equal(workspace.inheritanceLabel(), 'Inherited from cell settings');
  service.updatePartSettings({ kind: 'global-track', trackId: second }, { transpose: 0 });
  service.updatePartSettings(cell, { transpose: 0 });
  ui.selectCell(sectionId, 'bass');
  assert.deepEqual(workspace.overriddenKeys(), ['chordSpanFill']);
  service.updatePartSettings(part, { transpose: 24 });
  service.updatePartSettings(cell, { chordSpanFill: 'ALL' });
  assert.deepEqual(workspace.overriddenKeys(), []);
  assert.equal(workspace.trackExceptionCount(), 1, 'clearing the role layer preserves individual track exceptions');
  const saved = service.exportProjectJson();
  assert.equal(service.importProjectJson(saved), true);
  assert.equal(workspace.trackExceptionCount(), 1);
});

test('manual cleanup does not normalize untouched frozen or copied snapshot fields', () => {
  const { service, settings } = fixture();
  const trackId = 'track-arpeggio-1';
  const [first, second] = service.project().arrangement;
  const scope = { kind: 'section-track', sectionId: first.id, trackId };
  service.freezePartSettings(scope);
  const snapshot = { ...service.project().arrangement[0].trackPartOverrides[trackId] };
  service.updatePartSettings(scope, { transpose: 12 });
  service.updatePartSettings(scope, { transpose: 0 });
  const restored = service.project().arrangement[0].trackPartOverrides[trackId];
  const { transpose, ...untouched } = snapshot;
  assert.deepEqual(restored, untouched, 'only the manually restored field resumes inheritance');
  assert.equal(service.applyPartSettingsToSections(scope, [second.id], 'effective'), 'changed');
  assert.deepEqual(service.project().arrangement[1].trackPartOverrides[trackId], snapshot);
  service.updatePartSettings({ kind: 'global-track', trackId }, { transpose: -12, hitsPerPattern: 16 });
  const track = service.project().tracks.find((track) => track.id === trackId);
  const local = settings.resolvePartTrack(track, service.project().arrangement[0]).generatorSettings;
  assert.equal(local.transpose, -12, 'manually restored transpose now follows global changes');
  assert.equal(local.hitsPerPattern, 8, 'untouched frozen fields remain explicit');
  assert.equal(settings.resolvePartTrack(track, service.project().arrangement[1]).generatorSettings.transpose, 0);
});

test('static rhythm rotation matches Java before truncation, including short and non-eight-multiple grids', () => {
  const { load } = fixture();
  const { rhythmPatternMask } = load('core/music/rhythm-pattern.ts');
  const expected = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/rhythm-pattern.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(Object.keys(expected).length, 192);
  for (const [key, mask] of Object.entries(expected)) {
    const [pattern, hits, shift] = key.split('/');
    assert.deepEqual(rhythmPatternMask(pattern, Number(hits), Number(shift)), mask, key);
    assert.deepEqual(rhythmPatternMask(pattern, Number(hits), Number(shift), true), mask.map((value) => 1 - value), key + ' flip');
  }
  assert.deepEqual(rhythmPatternMask('ALT', 3, 1), [0, 1, 0], 'rotate the padded eight cells rather than a three-cell slice');
  assert.deepEqual(rhythmPatternMask('ONESIX', 8), [1, 0, 0, 0, 0, 1, 0, 0]);
  for (const args of [['CUSTOM', 8], ['FULL', 0], ['FULL', 33], ['ALT', 1.5], ['ALT', 8, -1], ['ALT', 8, 9], ['ALT', 8, 0, 1]]) {
    assert.throws(() => rhythmPatternMask(...args), RangeError);
  }
});

test('chord rhythm hits, shift and flip affect note onsets/durations while retaining every grid-step velocity draw', () => {
  const { service, load } = fixture();
  const { generateChordPart } = load('core/music/chord-generator.ts');
  const { JavaRandom } = load('core/music/java-random.ts');
  const seed = 9223372036854775807n;
  const settings = service.project().chords;
  const generate = (patch) => generateChordPart(seed, 'C', 'major', [1, 5], { ...settings, ...patch });
  const baseline = generate({});
  assert.deepEqual(generate({ hitsPerPattern: 8, patternShift: 0, patternFlip: false }), baseline);
  const expectedMasks = {
    full: [1, 1, 1, 1, 1, 1, 1, 1], half: [1, 0, 1, 0, 1, 0, 1, 0],
    tresillo: [1, 0, 0, 1, 0, 0, 1, 0], sparse: [1, 0, 0, 0, 1, 0, 0, 0], single: [1, 0, 0, 0, 0, 0, 0, 0],
  };
  for (const [rhythm, mask] of Object.entries(expectedMasks)) {
    const notes = generate({ rhythm });
    assert.deepEqual(notes.map((note) => note.startBeat), [0, 4].flatMap((start) => mask.flatMap((value, index) => value ? [start + index / 2] : [])));
    assert.ok(notes.every((note) => note.durationBeats === 0.5));
  }
  assert.deepEqual(generate({ rhythm: 'one-six' }).map((note) => note.startBeat), [0, 2.5, 4, 6.5]);
  const shifted = generate({ rhythm: 'half', hitsPerPattern: 3, patternShift: 1, noteLengthPercent: 75 });
  assert.deepEqual(shifted.map((note) => note.startBeat), [4 / 3, 4 + 4 / 3]);
  assert.ok(shifted.every((note) => note.durationBeats === 1));
  const random = new JavaRandom(BigInt.asIntN(32, BigInt.asIntN(32, seed) + 20001n));
  random.nextInt(21);
  assert.equal(shifted[0].velocity, random.nextInt(21) + 69, 'rest at step 0 consumes its velocity draw');
  const dense = generate({ hitsPerPattern: 32 });
  assert.equal(dense.length, 64);
  assert.equal(dense.at(-1).startBeat, 7.875);
  assert.ok(dense.every((note) => note.durationBeats === 0.125));
  assert.deepEqual(generate({ patternFlip: true }), []);
  assert.deepEqual(generate({ rhythm: 'half', patternFlip: true, chordSpanFill: 'EVEN' }).map((note) => note.startBeat), [0.5, 1.5, 2.5, 3.5]);
});

test('chord rhythm controls resolve mixed groups and scoped exceptions, including manual return to inherited defaults', () => {
  const { service, ui, workspace } = fixture();
  const first = 'track-chords-1';
  const second = service.duplicateTrack(first);
  const sectionId = service.project().arrangement[1].id;
  service.updatePartSettings({ kind: 'global-track', trackId: second }, { hitsPerPattern: 16 });
  ui.selectCell(sectionId, 'chords');
  assert.equal(workspace.partValues().hitsPerPattern, null);
  service.updatePartSettings(workspace.partScope(), { hitsPerPattern: 3, patternShift: 1 });
  service.updatePartSettings({ kind: 'section-track', sectionId, trackId: second }, { hitsPerPattern: 5, patternFlip: true });
  assert.equal(workspace.partValues().hitsPerPattern, null);
  assert.equal(workspace.trackExceptionCount(), 1);
  ui.selectSectionTrack(sectionId, second);
  assert.equal(workspace.partValues().hitsPerPattern, 5);
  service.updatePartSettings(workspace.partScope(), { hitsPerPattern: 3, patternFlip: false });
  assert.deepEqual(workspace.overriddenKeys(), []);
  assert.equal(workspace.partValues().hitsPerPattern, 3);
  service.resetPartSettings({ kind: 'section-role', sectionId, role: 'chords' });
  assert.equal(workspace.partValues().hitsPerPattern, 16);
  assert.equal(workspace.partValues().patternShift, 0);
  ui.selectSectionTrack(sectionId, first);
  assert.equal(workspace.partValues().hitsPerPattern, 8);
});

test('chord rhythm controls share resolved score/MIDI notes and survive history, copy/freeze and session reload', () => {
  const { service, ui, workspace, phrase, midi, load } = fixture();
  const trackId = 'track-chords-1';
  const sectionId = service.project().arrangement[1].id;
  service.project().arrangement.forEach((_, index) => service.setSectionTrackPresence(index, trackId, true));
  const track = () => service.project().tracks.find((track) => track.id === trackId);
  const original = phrase.layOutTrackPhrase(service.project(), track());
  const scope = { kind: 'section-track', sectionId, trackId };
  const before = service.exportProjectJson();
  service.updatePartSettings(scope, { rhythm: 'one-six', hitsPerPattern: 5, patternShift: 3, patternFlip: true });
  service.undo();
  assert.equal(service.exportProjectJson(), before);
  service.redo();
  const notes = phrase.layOutTrackPhrase(service.project(), track());
  const start = service.project().arrangement[0].measures * 4;
  const end = start + service.project().arrangement[1].measures * 4;
  assert.deepEqual(notes.filter((note) => note.startBeat < start || note.startBeat >= end),
    original.filter((note) => note.startBeat < start || note.startBeat >= end));
  assert.notDeepEqual(notes.filter((note) => note.startBeat >= start && note.startBeat < end),
    original.filter((note) => note.startBeat >= start && note.startBeat < end));
  assert.equal(workspace.scoreNotes().filter((note) => note.part === trackId).length, notes.length);
  const exported = new Midi(midi.generateCompositionMidi(service.project()));
  const midiNotes = exported.tracks.find((track) => track.name === 'C1').notes;
  assert.equal(midiNotes.length, notes.length);
  midiNotes.forEach((note, index) => {
    assert.ok(Math.abs(note.ticks / exported.header.ppq - notes[index].startBeat) < 0.003);
    assert.ok(Math.abs(note.durationTicks / exported.header.ppq - notes[index].durationBeats) < 0.003);
  });
  assert.equal(service.freezePartSettings(scope), 'changed');
  const other = service.project().arrangement[2].id;
  assert.equal(service.applyPartSettingsToSections(scope, [other], 'effective'), 'changed');
  assert.equal(service.project().arrangement[2].trackPartOverrides[trackId].hitsPerPattern, 5);
  const copy = service.duplicateTrack(trackId);
  assert.equal(service.project().arrangement[1].trackPartOverrides[copy].patternShift, 3);
  const saved = service.exportProjectJson();
  assert.equal(service.importProjectJson(saved), true);
  const { ProjectService } = load('core/project/project.service.ts');
  assert.deepEqual(projectData(new ProjectService().exportProjectJson()), projectData(saved));
  ui.selectSectionTrack(sectionId, trackId);
  assert.equal(workspace.partValues().patternFlip, true);
  const manual = [{ id: 'manual', midi: 72, startBeat: 0, durationBeats: 1, velocity: 90 }];
  service.updateTrackPhrase(trackId, manual);
  assert.deepEqual(phrase.phraseForTrack(service.project(), track()), manual);
});

test('unsupported roles and invalid rhythm controls reject edits/imports without discarding older settings', () => {
  const { service } = fixture();
  const sectionId = service.project().arrangement[0].id;
  const trackId = 'track-chords-1';
  const before = service.exportProjectJson();
  const scopes = [{ kind: 'global-role', role: 'chords' }, { kind: 'global-track', trackId },
    { kind: 'section-role', sectionId, role: 'chords' }, { kind: 'section-track', sectionId, trackId }];
  const invalid = [{ hitsPerPattern: 0 }, { hitsPerPattern: 33 }, { hitsPerPattern: 1.5 },
    { patternShift: -1 }, { patternShift: 9 }, { patternFlip: 1 }, { rhythm: 'melody1' }, { rhythm: 'unknown' },
    { euclideanPulses: -1 }, { euclideanPulses: 33 }, { euclideanPulses: 1.5 }, { euclideanPulses: '4' }];
  for (const scope of scopes) for (const patch of invalid) {
    service.updatePartSettings(scope, patch);
    assert.equal(service.exportProjectJson(), before);
  }
  for (const role of ['melody']) {
    service.updatePartSettings({ kind: 'section-role', sectionId, role }, { hitsPerPattern: 16 });
    assert.equal(service.exportProjectJson(), before);
    service.updatePartSettings({ kind: 'section-role', sectionId, role }, { euclideanPulses: 4 });
    assert.equal(service.exportProjectJson(), before);
  }
  for (const patch of invalid) {
    const project = JSON.parse(before);
    Object.assign(project.tracks.find((track) => track.id === trackId).generatorSettings, patch);
    assert.equal(service.importProjectJson(JSON.stringify(project)), false);
  }
  assert.equal(service.importProjectJson(before), true);
  assert.equal(service.project().tracks.find((track) => track.id === trackId).generatorSettings.hitsPerPattern, undefined);
});

test('Euclidean grids match production Java for all supported hits, pulse counts, shifts and flipped complements', () => {
  const { load } = fixture();
  const { euclideanPatternMask } = load('core/music/rhythm-pattern.ts');
  const fixtures = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/euclidean-pattern.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(Object.keys(fixtures).length, 5040);
  for (const [key, expected] of Object.entries(fixtures)) {
    const args = key.split('/').map(Number);
    assert.deepEqual(euclideanPatternMask(...args), expected, key);
    assert.deepEqual(euclideanPatternMask(...args, true), expected.map((value) => 1 - value), key + ' flipped');
    assert.equal(expected.length, args[0]);
  }
  for (const args of [[0, 0], [33, 4], [8, 9], [8, -1], [8, 1.5], [8, 3, 9], [8, 3, 0, 1]]) {
    assert.throws(() => euclideanPatternMask(...args), RangeError);
  }
});

test('Euclidean chord generation uses shifted/flipped subdivisions and preserves velocity draws for rests', () => {
  const { service, load } = fixture();
  const { generateChordPart } = load('core/music/chord-generator.ts');
  const { chordRhythmMask } = load('core/music/rhythm-pattern.ts');
  const { JavaRandom } = load('core/music/java-random.ts');
  const seed = 9223372036854775807n;
  const base = service.project().chords;
  const settings = { ...base, rhythm: 'euclid', hitsPerPattern: 8, euclideanPulses: 3, patternShift: 1, noteLengthPercent: 75 };
  const mask = chordRhythmMask(settings);
  const notes = generateChordPart(seed, 'C', 'major', [1, 5], settings);
  assert.deepEqual(notes.map((note) => note.startBeat), [0, 4].flatMap((start) => mask.flatMap((value, slot) => value ? [start + slot / 2] : [])));
  assert.ok(notes.every((note) => note.durationBeats === 0.375));
  const random = new JavaRandom(BigInt.asIntN(32, BigInt.asIntN(32, seed) + 20001n));
  const velocities = Array.from({ length: 8 }, () => random.nextInt(21) + 69);
  assert.deepEqual(notes.filter((note) => note.chordIndex === 0).map((note) => note.velocity), velocities.filter((_, slot) => mask[slot]));
  const flipped = generateChordPart(seed, 'C', 'major', [1, 5], { ...settings, patternFlip: true, chordSpanFill: 'EVEN' });
  assert.deepEqual(flipped.map((note) => note.startBeat), mask.flatMap((value, slot) => value ? [] : [slot / 2]));
  assert.deepEqual(generateChordPart(seed, 'C', 'major', [1], { ...settings, euclideanPulses: 0 }), []);
  assert.equal(generateChordPart(seed, 'C', 'major', [1], { ...settings, euclideanPulses: 0, patternFlip: true }).length, 8);
  assert.deepEqual(chordRhythmMask({ ...settings, hitsPerPattern: 3, euclideanPulses: 32 }), [1, 1, 1]);
  assert.equal(generateChordPart(seed, 'C', 'major', [1], { ...settings, hitsPerPattern: 3, euclideanPulses: 32 }).length, 3);
  assert.deepEqual(generateChordPart(seed, 'C', 'major', [1], { ...base, euclideanPulses: 7 }),
    generateChordPart(seed, 'C', 'major', [1], base), 'Pulses has no effect on static rhythms');
});

test('Euclidean pulse edits retain mixed values and requested counts across scope inheritance and hit reductions', () => {
  const { service, ui, workspace } = fixture();
  const first = 'track-chords-1';
  const second = service.duplicateTrack(first);
  service.updatePartSettings({ kind: 'global-role', role: 'chords' }, { rhythm: 'euclid' });
  service.updatePartSettings({ kind: 'global-track', trackId: second }, { euclideanPulses: 6 });
  const sectionId = service.project().arrangement[1].id;
  const cell = { kind: 'section-role', sectionId, role: 'chords' };
  ui.selectCell(sectionId, 'chords');
  assert.equal(workspace.partValues().euclideanPulses, null);
  service.updatePartSettings(cell, { euclideanPulses: 5 });
  assert.equal(workspace.partValues().euclideanPulses, 5);
  const part = { kind: 'section-track', sectionId, trackId: first };
  service.updatePartSettings(part, { euclideanPulses: 7, hitsPerPattern: 3 });
  ui.selectSectionTrack(sectionId, first);
  assert.equal(workspace.partValues().euclideanPulses, 7, 'requested Pulses is retained even when Hits is smaller');
  service.updatePartSettings(part, { euclideanPulses: 5, hitsPerPattern: 8 });
  assert.deepEqual(workspace.overriddenKeys(), [], 'returning to inherited pulse count removes its override');
  service.resetPartSettings(cell);
  assert.equal(workspace.partValues().euclideanPulses, 4);
  ui.selectSectionTrack(sectionId, second);
  assert.equal(workspace.partValues().euclideanPulses, 6);
});

test('quick access and inspector partition musical controls across roles and rhythm modes', () => {
  const { service, load, settings } = fixture();
  const { PartSettingsEditorComponent } = load('shared/part-settings-editor.component.ts');
  const { partControlGroup } = load('shared/part-control-groups.ts');
  const quick = new PartSettingsEditorComponent(), inspector = new PartSettingsEditorComponent();
  quick.advanced.set(false);
  inspector.inspectorOnly.set(true);
  for (const role of ['melody', 'bass', 'chords', 'arpeggio', 'drums']) {
    for (const rhythm of ['full', 'euclid', 'custom', 'melody', 'bass1']) {
      const values = { ...settings.settingsValues(service.project()[role], role), rhythm,
        useCustomVelocities: true };
      const complete = new PartSettingsEditorComponent();
      for (const editor of [quick, inspector, complete]) {
        editor.role.set(role);
        editor.values.set(values);
      }
      const quickKeys = quick.controls().map(control => control.key);
      const inspectorKeys = inspector.controls().map(control => control.key);
      assert.equal(quickKeys.some(key => inspectorKeys.includes(key)), false);
      assert.deepEqual([...quickKeys, ...inspectorKeys].sort(), complete.controls().map(control => control.key).sort());
      for (const key of ['noteLengthMultiplier', 'noteLengthPercent', 'exceptionChance', 'offset']) {
        const inQuick = (role === 'bass' && key === 'noteLengthMultiplier')
          || (role === 'chords' && key === 'noteLengthPercent');
        assert.equal(quickKeys.includes(key), inQuick, `${role} ${key} quick placement`);
        if (settings.PART_CONTROLS[role].some(control => control.key === key)) {
          assert.equal(inspectorKeys.includes(key), !inQuick, `${role} ${key} inspector placement`);
        }
      }
      assert.equal(quick.controlGroups()[0].id, 'rhythm');
      assert.equal(quick.seedControl(), undefined);
      assert.equal(inspector.seedControl().key, 'patternSeed');
      assert.equal(inspector.controlGroups().some(group => group.controls.some(control => control.key === 'patternSeed')), false);
    }
  }
  for (const key of ['noteLengthMultiplier', 'noteLengthPercent', 'exceptionChance', 'swingPercent', 'offset', 'feedbackCount', 'feedbackDuration']) {
    assert.equal(partControlGroup(key), 'timing');
  }
  assert.equal(partControlGroup('hitsPerPattern'), 'rhythm');
});

test('the chord rhythm preview shares generation masks and shows mixed values only for active rhythm inputs', () => {
  const { service, load, settings } = fixture();
  const { PartSettingsEditorComponent } = load('shared/part-settings-editor.component.ts');
  const { chordRhythmMask } = load('core/music/rhythm-pattern.ts');
  const editor = new PartSettingsEditorComponent();
  editor.role.set('chords');
  const values = settings.settingsValues(service.project().chords, 'chords');
  editor.values.set(values);
  assert.deepEqual(editor.rhythmPreview(), chordRhythmMask(service.project().chords));
  assert.equal(editor.controls().some((control) => control.key === 'euclideanPulses'), false);
  editor.values.set({ ...values, rhythm: 'euclid', hitsPerPattern: 5, euclideanPulses: 2, patternShift: 1, patternFlip: true });
  assert.equal(editor.controls().some((control) => control.key === 'euclideanPulses'), true);
  assert.deepEqual(editor.rhythmPreview(), chordRhythmMask(editor.values()));
  editor.values.set({ ...editor.values(), euclideanPulses: null });
  assert.equal(editor.rhythmPreview(), null);
  assert.match(editor.rhythmPreviewLabel(), /Mixed/);
  editor.values.set({ ...editor.values(), rhythm: 'half' });
  assert.notEqual(editor.rhythmPreview(), null, 'mixed unused Pulses does not hide a static rhythm preview');
  editor.values.set({ ...editor.values(), rhythm: null });
  assert.equal(editor.rhythmPreview(), null);
  editor.role.set('bass');
  editor.values.set({ ...editor.values(), rhythm: 'bass1' });
  assert.equal(editor.rhythmPreview(), undefined);
});

test('Euclidean settings survive local generation, parsed MIDI, snapshots, duplication, undo and session restore', () => {
  const { service, phrase, midi, settings, load } = fixture();
  const trackId = 'track-chords-1';
  service.project().arrangement.forEach((_, index) => service.setSectionTrackPresence(index, trackId, true));
  const sectionId = service.project().arrangement[1].id;
  const scope = { kind: 'section-track', sectionId, trackId };
  const track = () => service.project().tracks.find((track) => track.id === trackId);
  const original = phrase.layOutTrackPhrase(service.project(), track());
  const before = service.exportProjectJson();
  service.updatePartSettings(scope, { rhythm: 'euclid', hitsPerPattern: 5, euclideanPulses: 2, patternShift: 1 });
  const after = phrase.layOutTrackPhrase(service.project(), track());
  const start = service.project().arrangement[0].measures * 4;
  const end = start + service.project().arrangement[1].measures * 4;
  const outside = (notes) => notes.filter((note) => note.startBeat < start || note.startBeat >= end);
  assert.deepEqual(outside(after), outside(original));
  assert.notDeepEqual(after, original);
  const exported = new Midi(midi.generateCompositionMidi(service.project()));
  const midiNotes = exported.tracks.find((track) => track.name === 'C1').notes;
  assert.equal(midiNotes.length, after.length);
  midiNotes.forEach((note, index) => {
    assert.ok(Math.abs(note.ticks / exported.header.ppq - after[index].startBeat) < 0.003);
    assert.ok(Math.abs(note.durationTicks / exported.header.ppq - after[index].durationBeats) < 0.003);
    assert.equal(note.midi, after[index].midi);
  });
  service.undo();
  assert.equal(service.exportProjectJson(), before);
  service.redo();
  assert.deepEqual(phrase.layOutTrackPhrase(service.project(), track()), after);
  assert.equal(service.freezePartSettings(scope), 'changed');
  assert.equal(service.freezePartSettings(scope), 'unchanged');
  const destination = service.project().arrangement[2].id;
  assert.equal(service.applyPartSettingsToSections(scope, [destination], 'effective'), 'changed');
  assert.equal(service.partSettingsCopyWouldChange(scope, [destination], 'effective'), false);
  assert.equal(service.project().arrangement[2].trackPartOverrides[trackId].euclideanPulses, 2);
  const copy = service.duplicateTrack(trackId);
  assert.equal(service.project().arrangement[1].trackPartOverrides[copy].euclideanPulses, 2);
  const saved = service.exportProjectJson();
  assert.equal(service.importProjectJson(saved), true);
  const { ProjectService } = load('core/project/project.service.ts');
  assert.deepEqual(projectData(new ProjectService().exportProjectJson()), projectData(saved));
  const importedTrack = service.project().tracks.find((track) => track.id === trackId);
  assert.equal(settings.resolvePartTrack(importedTrack, service.project().arrangement[1]).generatorSettings.euclideanPulses, 2);
  const manual = [{ id: 'manual', midi: 72, startBeat: 0, durationBeats: 1, velocity: 90 }];
  service.updateTrackPhrase(trackId, manual);
  assert.deepEqual(phrase.phraseForTrack(service.project(), track()), manual);
});

test('section chance preferences gain melody defaults while retaining saved probabilities for other roles', () => {
  const { load } = fixture();
  const previousStorage = global.localStorage;
  const values = new Map([['vibecomposer.section-type-settings.v1', JSON.stringify({
    CHORUS1: { bass: 13, chords: 72, arpeggio: 19, drums: 81 },
  })]]);
  global.localStorage = { getItem: (key) => values.get(key) ?? null, setItem: (key, value) => values.set(key, value) };
  try {
    const { SectionTypeSettingsService } = load('core/project/section-type-settings.service.ts');
    const preferences = new SectionTypeSettingsService();
    assert.equal(preferences.chances().INTRO.melody, 20);
    assert.equal(preferences.chances().HALF_CHORUS.melody, 0);
    assert.equal(preferences.chances().CLIMAX.melody, 100);
    assert.equal(preferences.chances().CHORUS1.melody, 50);
    assert.equal(preferences.chances().CHORUS1.bass, 13);
    preferences.setChance('CHORUS1', 'melody', 87);
    assert.equal(new SectionTypeSettingsService().chances().CHORUS1.melody, 87);
    assert.equal(preferences.chances().CHORUS1.bass, 13);
  } finally { global.localStorage = previousStorage; }
});

test('rhythm catalogue options agree with every scope, import decoder and supported generator', () => {
  const { load, service, settings } = fixture();
  const catalogue = load('core/music/rhythm-patterns.ts');
  const { chordRhythmMask } = load('core/music/rhythm-pattern.ts');
  const { generateBassline } = load('core/music/bass-generator.ts');
  const { generateChordPart } = load('core/music/chord-generator.ts');
  const chordOptions = settings.PART_CONTROLS.chords.find(control => control.key === 'rhythm').options;
  const bassOptions = settings.PART_CONTROLS.bass.find(control => control.key === 'rhythm').options;
  assert.strictEqual(chordOptions, catalogue.RHYTHM_PATTERNS);
  assert.strictEqual(bassOptions, catalogue.BASS_RHYTHMS);
  assert.ok(chordOptions.includes('custom'));
  assert.equal(new Set(chordOptions).size, chordOptions.length);
  const sectionId = service.project().arrangement[1].id;
  for (const [role, options] of [['chords', chordOptions], ['bass', bassOptions]]) {
    const trackId = `track-${role}-1`;
    const scopes = [{ kind: 'global-role', role }, { kind: 'global-track', trackId },
      { kind: 'section-role', sectionId, role }, { kind: 'section-track', sectionId, trackId }];
    for (const rhythm of options) {
      for (const scope of scopes) {
        service.updatePartSettings(scope, { rhythm });
        const track = service.project().tracks.find(track => track.id === trackId);
        const section = scope.kind.startsWith('section') ? service.project().arrangement[1] : undefined;
        const effective = settings.resolvePartTrack(track, section).generatorSettings;
        assert.equal(effective.rhythm, rhythm);
        if (role === 'chords') {
          assert.doesNotThrow(() => generateChordPart(42n, 'C', 'major', [1], effective));
          assert.equal(chordRhythmMask(effective).length, effective.hitsPerPattern ?? 8);
        } else {
          assert.doesNotThrow(() => generateBassline(42n, 'C', 'major', [1], rhythm, effective.noteVariation, effective));
        }
        assert.equal(service.importProjectJson(service.exportProjectJson()), true);
      }
    }
  }
});

test('knobs drag vertically, commit once, cancel safely, and scroll with stepped/fine bounds', () => {
  const { load } = fixture();
  const { CompactKnobComponent } = load('shared/compact-knob.component.ts');
  const knob = new CompactKnobComponent();
  knob.value.set(50); knob.minimum.set(0); knob.maximum.set(100); knob.label.set('Length');
  const commits = [], previews = [];
  knob.valueCommit.emit = value => commits.push(value);
  knob.valuePreview.emit = value => previews.push(value);
  const control = { focus() {}, setPointerCapture() {} };
  const event = (y, extra = {}) => ({ pointerId: 1, button: 0, clientY: y, clientX: 0,
    currentTarget: control, preventDefault() {}, stopPropagation() {}, shiftKey: false, ...extra });
  knob.startDrag(event(100));
  knob.moveDrag(event(100, { clientX: 200 }));
  assert.equal(knob.shownValue(), 50, 'horizontal movement cannot change the knob');
  knob.moveDrag(event(25));
  assert.equal(knob.shownValue(), 100);
  assert.deepEqual(commits, []);
  knob.finishDrag(event(25));
  assert.deepEqual(commits, [100]);
  knob.startDrag(event(100)); knob.moveDrag(event(175)); knob.cancelDrag();
  assert.equal(knob.shownValue(), 50);
  assert.equal(previews.at(-1), 50, 'cancel restores audio preview');
  knob.finishDrag(event(175));
  assert.deepEqual(commits, [100]);
  knob.startDrag(event(100)); knob.moveDrag(event(25)); knob.value.set(60); knob.finishDrag(event(25));
  assert.deepEqual(commits, [100], 'external changes cancel pending drag');
  knob.onWheel(event(0, { deltaY: -1 }));
  assert.equal(commits.at(-1), 65);
  knob.onWheel(event(0, { deltaY: 1, shiftKey: true }));
  assert.equal(commits.at(-1), 59);
  const count = commits.length;
  knob.onWheel(event(0, { deltaY: 1, ctrlKey: true }));
  assert.equal(commits.length, count, 'browser zoom stays available');
  knob.minimum.set(-36); knob.maximum.set(36); knob.step.set(12); knob.value.set(0);
  knob.onWheel(event(0, { deltaY: -1 }));
  assert.equal(commits.at(-1), 12, 'transpose respects octave steps');
  knob.value.set(36); knob.onWheel(event(0, { deltaY: -1 }));
  assert.equal(commits.at(-1), 12, 'bound does not create another history entry');
});

test('wheel selects skip disabled options, wrap, emit existing change events, and preserve zoom', () => {
  const { load } = fixture();
  const { WheelSelectDirective } = load('shared/wheel-select.directive.ts');
  const directive = new WheelSelectDirective();
  const previous = global.HTMLOptGroupElement;
  global.HTMLOptGroupElement = class {};
  try {
    const changes = [];
    const select = { selectedIndex: 1, disabled: false,
      options: [{ disabled: true }, { disabled: false }, { disabled: false }],
      dispatchEvent: event => changes.push(event.type) };
    directive.element = { nativeElement: select };
    const event = (deltaY, extra = {}) => ({ deltaY, preventDefault() {}, stopPropagation() {}, ...extra });
    directive.onWheel(event(1)); assert.equal(select.selectedIndex, 2);
    directive.onWheel(event(1)); assert.equal(select.selectedIndex, 1);
    directive.onWheel(event(-1)); assert.equal(select.selectedIndex, 2);
    assert.deepEqual(changes, ['change', 'change', 'change']);
    directive.onWheel(event(1, { ctrlKey: true })); assert.equal(changes.length, 3);
    select.disabled = true; directive.onWheel(event(1)); assert.equal(changes.length, 3);
  } finally { global.HTMLOptGroupElement = previous; }
});

test('quick mixer knobs format linear gain and pan, audition without history, and commit one track only', () => {
  const { load, service, workspace } = fixture();
  const { panLabel, volumeDecibels } = load('core/audio/mix-values.ts');
  assert.deepEqual([-100, -50, 0, 50, 100].map(panLabel), ['100% L', '50% L', 'C', '50% R', '100% R']);
  assert.deepEqual([100, 50, 1, 0].map(volumeDecibels), ['-0.0', '-6.0', '-40.0', '-Inf']);
  const heard = [];
  workspace.playback.updateMix = project => heard.push(project);
  const before = service.exportProjectJson();
  workspace.previewTrackMix('track-bass-1', 'panPercent', -50);
  assert.equal(service.exportProjectJson(), before);
  assert.equal(heard.at(-1).tracks.find(track => track.id === 'track-bass-1').mix.panPercent, -50);
  workspace.updateTrackMix('track-bass-1', 'panPercent', -50);
  assert.equal(service.project().tracks.find(track => track.id === 'track-bass-1').mix.panPercent, -50);
  assert.equal(service.project().tracks.find(track => track.id === 'track-chords-1').mix.panPercent, 0);
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.redo();
  workspace.updateTrackMix('track-bass-1', 'volumePercent', 50);
  assert.equal(service.project().mix.bass.volumePercent, 50);
  assert.equal(heard.at(-1).tracks.find(track => track.id === 'track-bass-1').mix.volumePercent, 50);
});

test('track gain supports +6 dB, fractional attenuation, history and project round trips', () => {
  const { load, service, workspace } = fixture();
  workspace.playback.updateMix = () => {};
  const { MAX_VOLUME_PERCENT, volumeDecibels } = load('core/audio/mix-values.ts');
  assert.equal(volumeDecibels(MAX_VOLUME_PERCENT), '+6.0');
  assert.equal(volumeDecibels(100 * 10 ** (-0.1 / 20)), '-0.1');
  workspace.updateTrackMix('track-bass-1', 'volumePercent', MAX_VOLUME_PERCENT);
  const saved = service.exportProjectJson();
  assert.equal(service.project().tracks.find(track => track.id === 'track-bass-1').mix.volumePercent, MAX_VOLUME_PERCENT);
  service.undo();
  assert.equal(service.project().mix.bass.volumePercent, 100);
  service.redo();
  assert.equal(service.exportProjectJson(), saved);
  assert.equal(service.importProjectJson(saved), true);
  const imported = service.exportProjectJson();
  for (const value of [MAX_VOLUME_PERCENT + 0.01, -0.1, NaN, Infinity]) {
    workspace.updateTrackMix('track-bass-1', 'volumePercent', value);
    assert.equal(service.exportProjectJson(), imported);
  }
});

test('volume rounds displayed zero to unity and scrolls by exact 1% or 10% steps', () => {
  const { load, service } = fixture();
  const { MAX_VOLUME_PERCENT, normalizeVolumePercent, volumeDecibels } = load('core/audio/mix-values.ts');
  const { CompactKnobComponent } = load('shared/compact-knob.component.ts');
  const knob = new CompactKnobComponent();
  knob.mini.set(true); knob.minimum.set(0); knob.maximum.set(MAX_VOLUME_PERCENT);
  knob.step.set(0.1); knob.valueFormat.set('decibels');
  const commits = [], previews = [];
  knob.valueCommit.emit = value => commits.push(value);
  knob.valuePreview.emit = value => previews.push(value);
  for (const value of [99.5, 100, 100.5]) {
    assert.equal(normalizeVolumePercent(value), 100);
    assert.equal(volumeDecibels(value), '-0.0');
    knob.value.set(value);
    assert.equal(knob.shownValue(), 100);
    assert.match(knob.mixAccent(), / 0%,/);
    knob.previewValue({ target: { value: String(value) } });
    assert.equal(previews.at(-1), 100);
    knob.commitValue({ target: { value: String(value) } });
    assert.equal(commits.at(-1), 100);
    service.updateTrack('track-bass-1', { mix: { volumePercent: value } });
    assert.equal(service.project().mix.bass.volumePercent, 100);
  }
  assert.equal(volumeDecibels(99.4), '-0.1');
  assert.equal(volumeDecibels(100.6), '+0.1');
  const wheel = (deltaY, shiftKey = false) => ({ deltaY, shiftKey, preventDefault() {}, stopPropagation() {} });
  for (const start of [50, 100, 150]) {
    knob.value.set(start);
    for (const shift of [false, true]) for (const direction of [-1, 1]) {
      knob.onWheel(wheel(-direction, shift));
      assert.equal(commits.at(-1), start + direction * (shift ? 1 : 10));
    }
  }
  knob.value.set(195); knob.onWheel(wheel(-1)); assert.equal(commits.at(-1), MAX_VOLUME_PERCENT);
  knob.value.set(5); knob.onWheel(wheel(1)); assert.equal(commits.at(-1), 0);
});

test('mixer accents mute at defaults, jump for tiny edits, reach both endpoints and invert only boosts', () => {
  const { load } = fixture();
  const { MAX_VOLUME_PERCENT } = load('core/audio/mix-values.ts');
  const { CompactKnobComponent } = load('shared/compact-knob.component.ts');
  const knob = new CompactKnobComponent();
  knob.mini.set(true); knob.minimum.set(-100); knob.maximum.set(100); knob.valueFormat.set('pan');
  knob.value.set(0);
  assert.match(knob.mixAccent(), / 0%,/);
  for (const value of [-100, 100]) { knob.value.set(value); assert.match(knob.mixAccent(), / 100%,/); }
  knob.value.set(1); assert.match(knob.mixAccent(), / 32.5%,/);
  knob.minimum.set(0); knob.maximum.set(MAX_VOLUME_PERCENT); knob.valueFormat.set('decibels');
  knob.value.set(100); assert.match(knob.mixAccent(), / 0%,/);
  knob.value.set(100 * 10 ** (-0.1 / 20));
  assert.ok(parseFloat(knob.mixAccent().match(/ ([\d.]+)%,/)[1]) > 25);
  assert.ok(!knob.mixAccent().includes('255 -'));
  knob.value.set(0); assert.match(knob.mixAccent(), / 100%,/);
  knob.value.set(MAX_VOLUME_PERCENT);
  assert.match(knob.mixAccent(), /255 - r/); assert.match(knob.mixAccent(), / 100%,/);
  knob.preview.set(100); assert.match(knob.mixAccent(), / 0%,/);
  knob.mini.set(false); assert.equal(knob.mixAccent(), null);
});

test('velocity patterns match Java stored subdivision selection and retain zero, independent of shift/flip', () => {
  const { load } = fixture();
  const { chordVelocityPattern } = load('core/music/velocity-pattern.ts');
  const fixtures = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/velocity-pattern.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(Object.keys(fixtures).length, 84);
  for (const [key, expected] of Object.entries(fixtures)) {
    const [sample, hits, shift] = key.split('/').map(Number);
    const grid = Array.from({ length: 32 }, (_, index) => sample === 0 ? 0 : sample === 1 ? 127
      : sample === 2 ? index * 7 % 128 : index % 2 === 0 ? 1 : 79);
    for (const patternFlip of [false, true]) {
      assert.deepEqual(chordVelocityPattern({ useCustomVelocities: true, customVelocities: grid,
        hitsPerPattern: hits, patternShift: shift, patternFlip }), expected, key);
    }
  }
  assert.deepEqual(chordVelocityPattern({ useCustomVelocities: true, hitsPerPattern: 3 }), [79, 79, 79]);
  assert.equal(chordVelocityPattern({ customVelocities: Array(32).fill(127) }), undefined);
});

test('custom velocities replace dynamics without changing rhythm timing and zero hits remain silent', () => {
  const { service, load } = fixture();
  const { generateChordPart } = load('core/music/chord-generator.ts');
  const { chordRhythmMask } = load('core/music/rhythm-pattern.ts');
  const base = service.project().chords;
  const generate = (patch = {}) => generateChordPart(42n, 'C', 'major', [1, 4, 5], { ...base, ...patch });
  const original = generate();
  assert.deepEqual(generate({ useCustomVelocities: false, customVelocities: Array(32).fill(0) }), original);
  assert.deepEqual(generate({ customVelocities: Array(32).fill(127) }), original);
  const grid = Array.from({ length: 32 }, (_, index) => index % 3 === 0 ? 0 : index * 7 % 128);
  const patch = { rhythm: 'euclid', hitsPerPattern: 5, euclideanPulses: 3, patternShift: 1, patternFlip: true,
    useCustomVelocities: true, customVelocities: grid, noteLengthPercent: 75, velocityMin: 110, velocityMax: 110 };
  const mask = chordRhythmMask(patch);
  const notes = generate(patch);
  assert.ok(notes.length > 0);
  assert.ok(notes.every(note => note.velocity < 110), 'custom values are not clamped to random velocity bounds');
  for (const note of notes) {
    const subdivision = Math.round((note.startBeat % 4) / (4 / 5));
    assert.equal(mask[subdivision], 1);
    assert.equal(note.velocity, grid[subdivision]);
    assert.ok(Math.abs(note.durationBeats - 4 / 5 * .75) < 1e-12);
  }
  assert.deepEqual(generate({ ...patch, customVelocities: Array(32).fill(0) }), []);
  assert.deepEqual(generate({ ...patch, chordSpanFill: 'ALL', fillFlip: true }), []);
  assert.deepEqual(generate({ useCustomVelocities: true }).map(note => note.velocity), original.map(() => 79));
});

test('velocity enable and arrays preserve mixed scopes, inherited resets, snapshots and hidden subdivisions', () => {
  const { service, ui, workspace, settings } = fixture();
  const trackId = 'track-chords-1';
  const second = service.duplicateTrack(trackId);
  const sectionId = service.project().arrangement[1].id;
  const cell = { kind: 'section-role', sectionId, role: 'chords' };
  const part = { kind: 'section-track', sectionId, trackId: second };
  const grid = Array.from({ length: 32 }, (_, index) => 30 + index);
  service.updatePartSettings({ kind: 'global-role', role: 'chords' }, { useCustomVelocities: true, customVelocities: grid });
  grid[0] = 0;
  assert.equal(service.project().chords.customVelocities[0], 30);
  const override = Array(32).fill(120);
  service.updatePartSettings(part, { customVelocities: override, useCustomVelocities: false });
  ui.selectCell(sectionId, 'chords');
  assert.equal(workspace.partValues().customVelocities, null);
  assert.equal(workspace.partValues().useCustomVelocities, null);
  service.updatePartSettings(cell, { customVelocities: Array(32).fill(60) });
  let effective = settings.resolvePartTrack(service.project().tracks.find(track => track.id === second), service.project().arrangement[1]).generatorSettings;
  assert.deepEqual(effective.customVelocities, override);
  assert.equal(effective.useCustomVelocities, false);
  service.updatePartSettings(part, { customVelocities: Array(32).fill(60), useCustomVelocities: true });
  assert.equal(service.project().arrangement[1].trackPartOverrides[second], undefined, 'same-content arrays and booleans restore inheritance');
  const source = { kind: 'section-track', sectionId, trackId };
  assert.equal(service.freezePartSettings(source), 'changed');
  assert.equal(service.freezePartSettings(source), 'unchanged');
  const destination = service.project().arrangement[2].id;
  assert.equal(service.applyPartSettingsToSections(source, [destination], 'effective'), 'changed');
  assert.equal(service.partSettingsCopyWouldChange(source, [destination], 'effective'), false);
  service.updatePartSettings(source, { hitsPerPattern: 3 });
  assert.equal(service.project().arrangement[1].trackPartOverrides[trackId].customVelocities.length, 32);
  service.updatePartSettings(source, { useCustomVelocities: false });
  assert.equal(service.project().arrangement[1].trackPartOverrides[trackId].customVelocities[31], 60);
  service.undo();
  assert.equal(service.project().arrangement[1].trackPartOverrides[trackId].useCustomVelocities, true);
  service.duplicateSection(1);
  const copy = service.duplicateTrack(trackId);
  assert.deepEqual(service.project().arrangement[1].trackPartOverrides[copy].customVelocities, Array(32).fill(60));
});

test('popup velocity previews commit once, preserve hidden cells and cancel stale gestures', () => {
  const { service, load } = fixture();
  const { PatternSequencerComponent } = load('shared/pattern-sequencer.component.ts');
  const editor = new PatternSequencerComponent();
  const before = service.exportProjectJson();
  const input = { value: '110' }, event = { target: input };
  editor.previewVelocity('track-chords-1', 1, event);
  assert.equal(editor.rows()[0].velocities[1], 110);
  assert.equal(service.exportProjectJson(), before);
  editor.commitVelocity('track-chords-1', 1, event);
  assert.equal(service.project().chords.customVelocities[1], 110);
  assert.equal(service.project().chords.customVelocities.length, 32);
  service.undo(); assert.equal(service.exportProjectJson(), before);
  editor.previewVelocity('track-chords-1', 2, event); editor.cancel();
  assert.equal(editor.rows()[0].velocities[2], 79);
  editor.previewVelocity('track-chords-1', 2, event);
  service.updateSettings({ tempoBpm: 111 });
  const changed = service.exportProjectJson();
  editor.commitVelocity('track-chords-1', 2, event);
  assert.equal(service.exportProjectJson(), changed);
  editor.wheelVelocity('track-chords-1', 3, { deltaY: -1, shiftKey: true, preventDefault() {}, stopPropagation() {} });
  assert.equal(service.project().chords.customVelocities[3], 80);
});

test('custom velocities survive section MIDI, history, session and JSON while manual notes keep masking them', () => {
  const { service, phrase, midi, load } = fixture();
  const trackId = 'track-chords-1';
  service.project().arrangement.forEach((_, index) => service.setSectionTrackPresence(index, trackId, true));
  const scope = { kind: 'section-track', sectionId: service.project().arrangement[1].id, trackId };
  const track = () => service.project().tracks.find(track => track.id === trackId);
  const original = phrase.layOutTrackPhrase(service.project(), track());
  const before = service.exportProjectJson();
  const grid = Array.from({ length: 32 }, (_, index) => index === 0 ? 0 : index % 2 === 0 ? 127 : 20);
  service.updatePartSettings(scope, { useCustomVelocities: true, customVelocities: grid });
  const after = phrase.layOutTrackPhrase(service.project(), track());
  const start = service.project().arrangement[0].measures * 4;
  const end = start + service.project().arrangement[1].measures * 4;
  const outside = notes => notes.filter(note => note.startBeat < start || note.startBeat >= end);
  assert.deepEqual(outside(after), outside(original));
  assert.notDeepEqual(after, original);
  const exported = new Midi(midi.generateCompositionMidi(service.project()));
  const notes = exported.tracks.find(track => track.name === 'C1').notes;
  assert.equal(notes.length, after.length);
  notes.forEach((note, index) => {
    assert.equal(Math.round(note.velocity * 127), after[index].velocity);
    assert.ok(Math.abs(note.ticks / exported.header.ppq - after[index].startBeat) < .003);
  });
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.redo(); assert.deepEqual(phrase.layOutTrackPhrase(service.project(), track()), after);
  const saved = service.exportProjectJson();
  assert.equal(service.importProjectJson(saved), true);
  const { ProjectService } = load('core/project/project.service.ts');
  assert.deepEqual(projectData(new ProjectService().exportProjectJson()), projectData(saved));
  const manual = [{ id: 'manual', midi: 72, startBeat: 0, durationBeats: 1, velocity: 90 }];
  service.updateTrackPhrase(trackId, manual);
  service.updatePartSettings(scope, { customVelocities: Array(32).fill(0) });
  assert.deepEqual(phrase.phraseForTrack(service.project(), track()), manual);
});

test('velocity grids reject invalid shape, values and unsupported roles at every scope/import', () => {
  const { service, settings, load } = fixture();
  const { chordVelocityPattern } = load('core/music/velocity-pattern.ts');
  const trackId = 'track-chords-1';
  const sectionId = service.project().arrangement[1].id;
  const scopes = [{ kind: 'global-role', role: 'chords' }, { kind: 'global-track', trackId },
    { kind: 'section-role', sectionId, role: 'chords' }, { kind: 'section-track', sectionId, trackId }];
  service.updateSettings({ name: 'Velocity test' }); service.undo();
  const before = service.exportProjectJson();
  const invalid = [null, [], Array(31).fill(80), Array(33).fill(80), Array(32).fill(-1), Array(32).fill(128),
    Array(32).fill(1.5), Array(32).fill('80'), Array(32).fill(true), Array(32)];
  for (const grid of invalid) {
    assert.equal(settings.decodePartPatch('chords', { customVelocities: grid }), undefined);
    assert.throws(() => chordVelocityPattern({ customVelocities: grid }), RangeError);
    for (const scope of scopes) service.updatePartSettings(scope, { customVelocities: grid });
    const project = JSON.parse(before);
    project.tracks.find(track => track.id === trackId).generatorSettings.customVelocities = grid;
    assert.equal(service.importProjectJson(JSON.stringify(project)), false);
  }
  for (const scope of scopes) service.updatePartSettings(scope, { useCustomVelocities: 1 });
  for (const role of ['melody']) {
    service.updatePartSettings({ kind: 'section-role', sectionId, role }, { useCustomVelocities: true, customVelocities: Array(32).fill(80) });
  }
  assert.equal(service.exportProjectJson(), before);
  assert.equal(service.canRedo(), true);
});

test('custom masks match 1152 production Java list rotations and flipped complements', () => {
  const { load } = fixture();
  const { customPatternMask, chordRhythmMask } = load('core/music/rhythm-pattern.ts');
  const fixtures = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/custom-pattern.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(Object.keys(fixtures).length, 1152);
  for (const [key, expected] of Object.entries(fixtures)) {
    const [sample, hits, shift] = key.split('/').map(Number);
    const grid = Array.from({ length: 32 }, (_, slot) => sample === 0 ? 0 : sample === 1 ? 1
      : sample === 2 ? +(slot % 5 === 0) : +((slot * 7 + Math.floor(slot / 3)) % 11 < 4));
    assert.deepEqual(customPatternMask(grid, hits, shift), expected, key);
    assert.deepEqual(customPatternMask(grid, hits, shift, true), expected.map((value) => 1 - value), key + ' flip');
  }
  assert.deepEqual(chordRhythmMask({ rhythm: 'custom', hitsPerPattern: 3 }), [1, 1, 1]);
});

test('custom grid arrays resolve by content, replace whole values, and restore inheritance without losing exceptions', () => {
  const { service, ui, workspace, settings } = fixture();
  const trackId = 'track-chords-1';
  const second = service.duplicateTrack(trackId);
  const sectionId = service.project().arrangement[1].id;
  const cell = { kind: 'section-role', sectionId, role: 'chords' };
  const part = { kind: 'section-track', sectionId, trackId: second };
  const base = Array.from({ length: 32 }, (_, index) => +(index % 3 === 0));
  service.updatePartSettings({ kind: 'global-role', role: 'chords' }, { rhythm: 'custom', customPattern: base });
  base[0] = 0;
  assert.equal(service.project().chords.customPattern[0], 1, 'caller array is copied');
  service.updatePartSettings({ kind: 'global-track', trackId: second }, { customPattern: [...service.project().chords.customPattern] });
  ui.selectCell(sectionId, 'chords');
  assert.ok(Array.isArray(workspace.partValues().customPattern), 'separate equal arrays are not Mixed');
  const exception = Array(32).fill(0); exception[31] = 1;
  service.updatePartSettings(part, { customPattern: exception });
  assert.equal(workspace.partValues().customPattern, null);
  const common = Array(32).fill(1); common[4] = 0;
  service.updatePartSettings(cell, { customPattern: common });
  const effective = () => settings.resolvePartTrack(service.project().tracks.find((track) => track.id === second), service.project().arrangement[1]).generatorSettings;
  assert.deepEqual(effective().customPattern, exception, 'track array replaces role array in its entirety');
  service.updatePartSettings(part, { customPattern: [...common] });
  assert.equal(service.project().arrangement[1].trackPartOverrides[second], undefined);
  assert.deepEqual(effective().customPattern, common);
  service.updatePartSettings(cell, { customPattern: [...service.project().chords.customPattern] });
  assert.equal(service.project().arrangement[1].rolePartOverrides.chords, undefined);
  assert.equal(service.freezePartSettings(part), 'changed');
  assert.equal(service.freezePartSettings(part), 'unchanged');
  const destination = service.project().arrangement[2].id;
  assert.equal(service.applyPartSettingsToSections(part, [destination], 'effective'), 'changed');
  assert.equal(service.partSettingsCopyWouldChange(part, [destination], 'effective'), false);
  const saved = service.exportProjectJson();
  const duplicate = service.duplicateTrack(second);
  service.updatePartSettings({ kind: 'section-track', sectionId, trackId: duplicate }, { customPattern: exception });
  assert.notDeepEqual(service.project().arrangement[1].trackPartOverrides[duplicate].customPattern,
    service.project().arrangement[1].trackPartOverrides[second].customPattern);
  service.undo(); service.undo();
  assert.equal(service.exportProjectJson(), saved);
});

test('custom chord grids retain seeded dynamics, hidden cells, section isolation and parsed MIDI across reload', () => {
  const { service, phrase, midi, load } = fixture();
  const { generateChordPart } = load('core/music/chord-generator.ts');
  const trackId = 'track-chords-1';
  service.project().arrangement.forEach((_, index) => service.setSectionTrackPresence(index, trackId, true));
  const track = () => service.project().tracks.find((track) => track.id === trackId);
  const base = track().generatorSettings;
  const grid = Array.from({ length: 32 }, (_, index) => +(index % 3 === 0));
  const full = generateChordPart(42n, 'C', 'major', [1], base);
  const sparse = generateChordPart(42n, 'C', 'major', [1], { ...base, rhythm: 'custom', customPattern: grid });
  assert.deepEqual(sparse, full.filter((_, index) => grid[index] > 0), 'rests still consume velocity draws');
  const original = phrase.layOutTrackPhrase(service.project(), track());
  const sectionId = service.project().arrangement[1].id;
  const scope = { kind: 'section-track', sectionId, trackId };
  service.updatePartSettings(scope, { rhythm: 'custom', customPattern: grid, hitsPerPattern: 5, patternShift: 1, patternFlip: true });
  const after = phrase.layOutTrackPhrase(service.project(), track());
  const start = service.project().arrangement[0].measures * 4;
  const end = start + service.project().arrangement[1].measures * 4;
  const outside = (notes) => notes.filter((note) => note.startBeat < start || note.startBeat >= end);
  assert.deepEqual(outside(after), outside(original));
  assert.notDeepEqual(after, original);
  const exported = new Midi(midi.generateCompositionMidi(service.project()));
  const notes = exported.tracks.find((track) => track.name === 'C1').notes;
  assert.equal(notes.length, after.length);
  notes.forEach((note, index) => {
    assert.equal(note.midi, after[index].midi);
    assert.ok(Math.abs(note.ticks / exported.header.ppq - after[index].startBeat) < 0.003);
    assert.ok(Math.abs(note.durationTicks / exported.header.ppq - after[index].durationBeats) < 0.003);
  });
  service.updatePartSettings(scope, { hitsPerPattern: 2 });
  assert.deepEqual(service.project().arrangement[1].trackPartOverrides[trackId].customPattern, grid);
  service.undo();
  assert.deepEqual(phrase.layOutTrackPhrase(service.project(), track()), after);
  const saved = service.exportProjectJson();
  assert.equal(service.importProjectJson(saved), true);
  const { ProjectService } = load('core/project/project.service.ts');
  const restored = new ProjectService();
  assert.deepEqual(projectData(restored.exportProjectJson()), projectData(saved));
  const manual = [{ id: 'manual', midi: 72, startBeat: 0, durationBeats: 1, velocity: 90 }];
  service.updateTrackPhrase(trackId, manual);
  service.updatePartSettings(scope, { customPattern: Array(32).fill(0) });
  assert.deepEqual(phrase.phraseForTrack(service.project(), track()), manual);
});

test('popup painting commits one gesture in audible coordinates and rejects stale project state', () => {
  const { service, load } = fixture();
  const { PatternSequencerComponent } = load('shared/pattern-sequencer.component.ts');
  service.updatePartSettings({ kind: 'global-track', trackId: 'track-chords-1' }, { rhythm: 'custom', patternShift: 3, patternFlip: true });
  const editor = new PatternSequencerComponent();
  const before = service.exportProjectJson();
  const button = { focus() {}, setPointerCapture() {} };
  editor.start({ button: 0, pointerId: 9, target: button, currentTarget: button, preventDefault() {} }, 'track-chords-1', 0);
  assert.equal(editor.rows()[0].pattern[0], 1);
  const slot = { dataset: { step: '2' } };
  const grid = { ownerDocument: { elementFromPoint: () => ({ closest: () => slot }) }, contains: () => true };
  editor.move({ pointerId: 9, currentTarget: grid, clientX: 1, clientY: 1 });
  assert.equal(service.exportProjectJson(), before);
  editor.finish({ pointerId: 9 });
  assert.deepEqual(service.project().chords.customPattern.slice(29), [0, 0, 0]);
  service.undo(); assert.equal(service.exportProjectJson(), before);
  editor.start({ button: 0, pointerId: 10, target: button, currentTarget: button, preventDefault() {} }, 'track-chords-1', 0);
  editor.cancel(); editor.finish({ pointerId: 10 });
  assert.equal(service.exportProjectJson(), before);
  editor.start({ button: 0, pointerId: 11, target: button, currentTarget: button, preventDefault() {} }, 'track-chords-1', 0);
  service.updateSettings({ tempoBpm: 111 });
  const changed = service.exportProjectJson();
  editor.finish({ pointerId: 11 }); assert.equal(service.exportProjectJson(), changed);
});

test('custom grids validate shape and binary slots at all scopes and imports without losing redo', () => {
  const { service, settings, load } = fixture();
  const trackId = 'track-chords-1';
  const sectionId = service.project().arrangement[1].id;
  service.updatePartSettings({ kind: 'global-track', trackId }, { rhythm: 'custom' });
  service.undo();
  const before = service.exportProjectJson();
  const invalid = [null, {}, [], Array(31).fill(1), Array(33).fill(1), Array(32).fill(2), Array(32).fill(true),
    Array(32).fill('1'), [...Array(31).fill(1), 0.5], Array(32)];
  const scopes = [{ kind: 'global-role', role: 'chords' }, { kind: 'global-track', trackId },
    { kind: 'section-role', sectionId, role: 'chords' }, { kind: 'section-track', sectionId, trackId }];
  const { customPatternMask } = load('core/music/rhythm-pattern.ts');
  for (const grid of invalid) {
    assert.equal(settings.decodePartPatch('chords', { customPattern: grid }), undefined);
    assert.throws(() => customPatternMask(grid, 8), RangeError);
    for (const scope of scopes) service.updatePartSettings(scope, { customPattern: grid });
    const project = JSON.parse(before);
    project.tracks.find((track) => track.id === trackId).generatorSettings.customPattern = grid;
    assert.equal(service.importProjectJson(JSON.stringify(project)), false);
  }
  for (const role of ['melody']) {
    service.updatePartSettings({ kind: 'section-role', sectionId, role }, { customPattern: Array(32).fill(1) });
  }
  assert.equal(service.exportProjectJson(), before);
  assert.equal(service.canRedo(), true);
});

test('section phrase edits retain independent ownership through export, playback layout, undo and reload', () => {
  const { service, phrase, midi, load } = fixture();
  const id = 'track-bass-1';
  const section = service.project().arrangement[1];
  service.setSectionTrackPresence(1, id, true);
  service.setSectionTrackPresence(2, id, true);
  const track = () => service.project().tracks.find(track => track.id === id);
  const before = service.exportProjectJson();
  const global = phrase.phraseForTrack(service.project(), track());
  const local = [{ id: 'local-edit', midi: 51, startBeat: 1.25, durationBeats: .7, velocity: 113 }];
  service.updateSectionTrackPhrase(section.id, id, local);
  const after = service.exportProjectJson();
  assert.deepEqual(phrase.phraseForTrack(service.project(), track()), global);
  assert.deepEqual(phrase.layOutTrackPhrase({ ...service.project(), arrangement: [service.project().arrangement[1]] }, track()), local);
  assert.ok(new Midi(midi.generateCompositionMidi(service.project())).tracks.find(track => track.name === 'B1').notes.some(note => note.midi === 51));
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.redo(); assert.equal(service.exportProjectJson(), after);
  assert.equal(service.importProjectJson(after), true);
  assert.deepEqual(service.project().arrangement[1].trackPhrases[id], local);
  const invalid = JSON.parse(after); invalid.arrangement[1].trackPhrases[id][0].midi = 128;
  assert.equal(service.importProjectJson(JSON.stringify(invalid)), false);
  const duplicate = service.duplicateTrack(id);
  service.removeTrack(id);
  assert.equal(service.importProjectJson(service.exportProjectJson()), true, 'deleting a track removes its section phrases');
});

test('contextual Local Apply preserves other phrase cycles and leaves the global track untouched', () => {
  const { service, ui, phrase, load } = fixture();
  const id = 'track-drums-1';
  const section = service.project().arrangement[1];
  service.setSectionTrackPresence(1, id, true);
  service.updateTrackPhrase(id, [{ id: 'kick', midi: 36, startBeat: 0, durationBeats: .2, velocity: 90 }]);
  ui.selectedTrackId.set(id);
  const track = service.project().tracks.find(track => track.id === id);
  const before = phrase.layOutTrackPhrase({ ...service.project(), arrangement: [service.project().arrangement[1]] }, track);
  const Editor = load('features/edit/edit-workspace.component.ts').EditWorkspaceComponent;
  const editor = new Editor();
  editor.sectionId = section.id;
  editor.phraseStartBeat = 0;
  editor.phraseLength = 4;
  editor.ngOnInit();
  const history = service.exportProjectJson();
  editor.selectNote('kick'); editor.updateSelected('velocity', { target: { value: '117' } });
  assert.equal(service.exportProjectJson(), history);
  editor.applyLocalChanges();
  assert.deepEqual(service.project().tracks.find(track => track.id === id).editedPhrase, track.editedPhrase);
  const after = phrase.layOutTrackPhrase({ ...service.project(), arrangement: [service.project().arrangement[1]] }, track);
  assert.equal(after.find(note => note.startBeat === 0).velocity, 117);
  assert.deepEqual(after.filter(note => note.startBeat >= 4).map(({ id, ...note }) => note), before.filter(note => note.startBeat >= 4).map(({ id, ...note }) => note));
  service.undo(); assert.equal(service.exportProjectJson(), history, 'Local Apply is one undo entry');
});

test('Highlight audible filters score tracks and notes from Solo/Mute without changing the mix', () => {
  const { service, workspace, uiPreferences, flushEffects } = fixture();
  service.setRoleTrackCount('bass', 2);
  const tracks = service.project().tracks;
  const [bass, otherBass] = tracks.filter(track => track.role === 'bass');
  service.applyPhraseEdits(tracks.map(track => ({ trackId: track.id, notes: [
    { id: track.id + '-note', midi: track.role === 'drums' ? 36 : 60, startBeat: 0, durationBeats: 1, velocity: 90 },
  ] })));
  service.updateTrack(bass.id, { mix: { muted: true } });
  assert.ok(workspace.scoreTracks().some(track => track.id === bass.id), 'disabled preference shows muted tracks');
  uiPreferences.toggleScoreMixSync(); flushEffects();
  assert.ok(workspace.scoreTracks().every(track => track.id !== bass.id));
  assert.ok(workspace.canvasNotes().length > 0);
  assert.ok(workspace.canvasNotes().every(note => note.part !== bass.id));
  assert.ok(workspace.scoreTracks().some(track => track.id === otherBass.id), 'audibility is per track within a role');

  service.updateTrack(otherBass.id, { mix: { solo: true } });
  assert.deepEqual(workspace.scoreTracks().map(track => track.id), [otherBass.id]);
  assert.ok(workspace.canvasNotes().every(note => note.part === otherBass.id));
  const before = service.exportProjectJson();
  const history = JSON.stringify(service.historyEntries());
  workspace.toggleScoreRole('bass');
  assert.deepEqual(workspace.scoreTracks(), [], 'manual role filters still apply');
  assert.deepEqual(workspace.canvasNotes(), []);
  workspace.toggleScoreRole('bass');
  assert.deepEqual(workspace.scoreTracks().map(track => track.id), [otherBass.id]);
  workspace.toggleScoreRole('drums');
  uiPreferences.toggleScoreMixSync(); flushEffects();
  assert.ok(workspace.scoreTracks().some(track => track.id === bass.id), 'disabling restores manual visibility');
  assert.ok(workspace.scoreTracks().every(track => track.role !== 'drums'));
  workspace.toggleScoreRole('drums');
  uiPreferences.toggleScoreMixSync(); flushEffects();
  assert.equal(service.exportProjectJson(), before, 'preference and display changes never change the mix');
  assert.equal(JSON.stringify(service.historyEntries()), history, 'display changes do not affect history');

  service.updateTrack(otherBass.id, { mix: { muted: true } });
  assert.ok(workspace.scoreTracks().length > 1, 'muting the last solo clears solo and restores other audible tracks');
  assert.ok(workspace.scoreTracks().every(track => track.id !== otherBass.id && track.id !== bass.id));
  service.updateTrack(otherBass.id, { mix: { muted: false, solo: false } });
  assert.ok(workspace.scoreTracks().length > 1, 'clearing solo restores other unmuted tracks');
  for (const track of tracks) service.updateTrack(track.id, { mix: { muted: true } });
  assert.deepEqual(workspace.scoreTracks(), [], 'all-muted mix produces an empty score');
  workspace.selectedTrackId.set(bass.id);
  workspace.editing.set(true);
  assert.deepEqual(workspace.scoreTracks().map(track => track.id), [bass.id], 'muted tracks remain editable');
});


test('multi-drum drafts keep ownership, smart-exit destinations and one atomic undo', () => {
  const { service, ui, load } = fixture();
  service.setRoleTrackCount('drums', 3);
  const tracks = service.project().tracks.filter(track => track.role === 'drums');
  const sectionId = service.project().arrangement[0].id;
  tracks.forEach((track, index) => service.updateTrackGeneratorSettings(track.id, { pitch: [36, 38, 42][index] }));
  const phrase = (midi, velocity) => [{ id: 'shared', midi, startBeat: 0, durationBeats: 1, velocity }];
  assert.equal(service.applyPhraseEdits([
    ...tracks.map((track, index) => ({ trackId: track.id, notes: phrase([36,38,42][index], 90) })),
    { trackId: tracks[1].id, sectionId, notes: phrase(38, 100) },
  ]), true);
  const before = service.exportProjectJson();
  const { EditWorkspaceComponent } = load('features/edit/edit-workspace.component.ts');
  ui.selectedTrackId.set(tracks[0].id);
  const editor = new EditWorkspaceComponent(); editor.sectionId = sectionId; editor.phraseLength = 16; editor.ngOnInit();
  assert.equal(editor.editedTracks().length, 3);
  assert.equal(new Set(editor.notes().map(note => note.id)).size, editor.notes().length, 'editor IDs are unique across tracks');
  assert.equal(editor.hasChanges(), false);
  editor.smartExit(); assert.equal(editor.cancelled.values.length, 1);
  assert.equal(service.exportProjectJson(), before, 'unchanged smart exit does not save');
  editor.selectAll();
  editor.replaceNotes(editor.notes().map(note => ({ ...note, velocity: note.velocity + 5 })));
  editor.undo(); assert.equal(editor.hasChanges(), false);
  editor.redo(); assert.equal(editor.hasChanges(), true);
  editor.smartExit(); assert.equal(editor.applied.values.length, 1);
  assert.equal(service.project().tracks.find(track => track.id === tracks[0].id).editedPhrase[0].velocity, 95);
  assert.equal(service.project().tracks.find(track => track.id === tracks[1].id).editedPhrase[0].velocity, 90);
  assert.equal(service.project().tracks.find(track => track.id === tracks[2].id).editedPhrase[0].velocity, 95);
  assert.equal(service.project().arrangement[0].trackPhrases[tracks[1].id][0].velocity, 105);
  service.undo(); assert.equal(service.exportProjectJson(), before, 'one project undo restores all drum phrases');
  const invalidBefore = service.exportProjectJson();
  assert.equal(service.applyPhraseEdits([{ trackId: tracks[0].id, notes: phrase(36, 110) },
    { trackId: tracks[2].id, notes: phrase(42, 200) }]), false);
  assert.equal(service.exportProjectJson(), invalidBefore, 'an invalid phrase aborts the whole batch');
});


test('drum pattern popup resolves every row in the selected section and edits only its track layer', () => {
  const { service, load } = fixture();
  service.setRoleTrackCount('drums', 3);
  const tracks = service.project().tracks.filter(track => track.role === 'drums');
  const sectionId = service.project().arrangement[1].id;
  service.updatePartSettings({ kind: 'section-role', sectionId, role: 'drums' }, { hitsPerPattern: 12 });
  const before = service.project().tracks;
  const { PatternSequencerComponent } = load('shared/pattern-sequencer.component.ts');
  const popup = new PatternSequencerComponent();
  popup.data.role = 'drums';
  popup.data.scope = { kind: 'section-track', sectionId, trackId: tracks[0].id };
  assert.equal(popup.rows().length, 3);
  assert.ok(popup.rows().every(row => row.hits === 12));
  popup.patch(tracks[1].id, { customVelocities: Array(32).fill(40), useCustomVelocities: true });
  assert.deepEqual(service.project().tracks, before);
  const section = service.project().arrangement[1];
  assert.equal(section.trackPartOverrides[tracks[0].id], undefined);
  assert.equal(section.trackPartOverrides[tracks[2].id], undefined);
  assert.deepEqual(section.trackPartOverrides[tracks[1].id].customVelocities, Array(32).fill(40));
  popup.inherit(tracks[1].id, 'customVelocities');
  assert.equal(service.project().arrangement[1].trackPartOverrides[tracks[1].id].customVelocities, undefined);
});

test('overview reset restores globals and generated notes locally, preserves siblings, and is one undo entry', () => {
  const { service, settings, phrase, load } = fixture();
  const trackId = 'track-bass-1', siblingId = service.duplicateTrack(trackId);
  const sectionId = service.project().arrangement[0].id;
  service.project().arrangement.forEach((_, index) => service.setSectionTrackPresence(index, trackId, true));
  const manual = [{ id: 'manual', midi: 1, startBeat: 0, durationBeats: 1, velocity: 90 }];
  service.updateTrackPhrase(trackId, manual);
  service.updateSectionTrackPhrase(sectionId, trackId, manual);
  service.updatePartSettings({ kind: 'section-role', sectionId, role: 'bass' }, { transpose: 12 });
  const before = service.exportProjectJson();
  const position = service.historyPosition();
  const scope = { kind: 'section-track', sectionId, trackId };
  assert.equal(service.changeOverviewTracks(scope, 'reset'), 'changed');
  assert.equal(service.historyPosition(), position + 1);
  const project = service.project(), section = project.arrangement[0];
  const track = project.tracks.find(t => t.id === trackId);
  assert.deepEqual(track.editedPhrase, manual);
  assert.equal(section.trackPhrases[trackId], undefined);
  assert.deepEqual(section.generatedTrackIds, [trackId]);
  assert.deepEqual(settings.resolvePartTrack(track, section).generatorSettings, track.generatorSettings);
  assert.equal(settings.resolvePartTrack(project.tracks.find(t => t.id === siblingId), section).generatorSettings.transpose, 12);
  const notes = phrase.layOutTrackPhrase(project, track);
  assert.ok(notes.some(n => n.startBeat < section.measures * 4 && n.midi !== 1));
  assert.ok(notes.some(n => n.startBeat >= section.measures * 4 && n.midi === 1));
  const after = service.exportProjectJson();
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.redo(); assert.equal(service.exportProjectJson(), after);
  assert.equal(service.changeOverviewTracks(scope, 'reset'), 'unchanged');
  assert.equal(service.exportProjectJson(), after);
  const { ProjectService } = load('core/project/project.service.ts');
  const restored = new ProjectService();
  assert.equal(restored.importProjectJson(after), true);
  assert.deepEqual(restored.project().arrangement[0].generatedTrackIds, [trackId]);
});

test('overview cell and section rerolls and resets stay local and undo atomically', () => {
  for (const kind of ['section-track', 'section-role', 'section']) {
    const { service } = fixture();
    const sectionId = service.project().arrangement[0].id;
    const scope = { kind, sectionId, ...(kind === 'section-track' ? { trackId: 'track-bass-1' } : kind === 'section-role' ? { role: 'bass' } : {}) };
    const before = service.exportProjectJson(), globals = JSON.stringify(service.project().tracks);
    const other = JSON.stringify(service.project().arrangement.slice(1));
    assert.equal(service.changeOverviewTracks(scope, 'reroll'), 'changed');
    const after = service.exportProjectJson();
    assert.equal(JSON.stringify(service.project().tracks), globals);
    assert.equal(JSON.stringify(service.project().arrangement.slice(1)), other);
    assert.ok(Object.keys(service.project().arrangement[0].trackPartOverrides).length);
    service.undo(); assert.equal(service.exportProjectJson(), before);
    service.redo(); assert.equal(service.exportProjectJson(), after);
    assert.equal(service.changeOverviewTracks(scope, 'reset'), 'changed');
    service.undo(); assert.equal(service.exportProjectJson(), after);
  }
});

test('overview header shortcuts edit the clicked section and undo duplication/removal individually', () => {
  const { service, workspace } = fixture();
  const clicked = service.project().arrangement[1].id;
  const before = service.exportProjectJson();
  workspace.duplicateSection(clicked);
  const duplicated = service.exportProjectJson();
  assert.equal(service.project().arrangement[2].type, service.project().arrangement[1].type);
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.redo(); assert.equal(service.exportProjectJson(), duplicated);
  workspace.removeSection(clicked);
  assert.ok(!service.project().arrangement.some(section => section.id === clicked));
  const removed = service.exportProjectJson();
  service.undo(); assert.equal(service.exportProjectJson(), duplicated);
  service.redo(); assert.equal(service.exportProjectJson(), removed);
});

test('overview section reset clears every role and manual phrase; rerolls preserve locks and manual notes', () => {
  const { service, workspace, ui } = fixture();
  const sectionId = service.project().arrangement[0].id;
  const manual = [{ id: 'manual', midi: 60, startBeat: 0, durationBeats: 1, velocity: 90 }];
  for (const track of service.project().tracks) {
    service.updateTrackPhrase(track.id, manual);
    service.updateSectionTrackPhrase(sectionId, track.id, manual);
    service.updatePartSettings({ kind: 'section-role', sectionId, role: track.role }, { transpose: 12 });
    service.updatePartSettings({ kind: 'section-track', sectionId, trackId: track.id }, { transpose: -12 });
  }
  const scope = { kind: 'section', sectionId };
  service.setTrackRerollLock('track-bass-1', true);
  const locked = JSON.stringify(service.project().arrangement[0].trackPartOverrides['track-bass-1']);
  assert.equal(service.changeOverviewTracks(scope, 'reroll'), 'changed');
  assert.equal(JSON.stringify(service.project().arrangement[0].trackPartOverrides['track-bass-1']), locked);
  assert.deepEqual(service.project().arrangement[0].trackPhrases['track-bass-1'], manual);
  const before = service.exportProjectJson();
  assert.equal(service.changeOverviewTracks(scope, 'reset'), 'changed');
  const section = service.project().arrangement[0];
  assert.deepEqual(section.rolePartOverrides, {});
  assert.deepEqual(section.trackPartOverrides, {});
  assert.deepEqual(section.trackPhrases, {});
  assert.deepEqual(new Set(section.generatedTrackIds), new Set(service.project().tracks.map(track => track.id)));
  ui.selectSectionTrack(sectionId, 'track-bass-1');
  assert.equal(workspace.manualTrackNames(), '');
  const after = service.exportProjectJson();
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.redo(); assert.equal(service.exportProjectJson(), after);
  service.duplicateSection(0);
  assert.deepEqual(service.project().arrangement[1].generatedTrackIds, section.generatedTrackIds);
  service.removeTrack('track-bass-1');
  assert.ok(service.project().arrangement.every(item => !item.generatedTrackIds?.includes('track-bass-1')));
});


test('main theme preferences derive concrete canvas colors and preserve project state', () => {
  const { service, uiPreferences: preferences, load } = fixture();
  const { themeColorTokens, DEFAULT_THEME_COLORS } = load('core/ui/theme.ts');
  const before = service.exportProjectJson(), position = service.historyPosition();
  preferences.setThemeColor('accent', '#DD88EE');
  preferences.setThemeColor('surface', '#281838');
  preferences.setThemeColor('border', '#78618a');
  assert.equal(preferences.appearance().themeColors.accent, '#dd88ee');
  const saved = preferences.exportJson();
  const restored = new (load('core/ui/ui-preferences.service.ts').UiPreferencesService)();
  assert.deepEqual(restored.appearance(), preferences.appearance());
  const original = themeColorTokens(DEFAULT_THEME_COLORS);
  const changed = themeColorTokens(preferences.appearance().themeColors);
  for (const value of Object.values(changed)) assert.match(value, /^#[0-9a-f]{6}([0-9a-f]{2})?$/);
  for (const key of ['--surface-base', '--surface-button', '--border-default', '--grid-measure', '--accent-glow']) {
    assert.notEqual(changed[key], original[key], key);
  }
  for (const themeColors of [null, [], {}, { ...DEFAULT_THEME_COLORS, accent: 'red' }]) {
    assert.equal(preferences.importJson(JSON.stringify({ ...JSON.parse(saved), appearance: { ...preferences.appearance(), themeColors } })), false);
    assert.equal(preferences.exportJson(), saved);
  }
  preferences.setThemeColor('accent', 'invalid');
  assert.equal(preferences.exportJson(), saved);
  assert.equal(service.exportProjectJson(), before);
  assert.equal(service.historyPosition(), position);
  preferences.resetAppearance();
  assert.deepEqual(preferences.appearance().themeColors, DEFAULT_THEME_COLORS);
  const withoutTheme = JSON.parse(saved); delete withoutTheme.appearance.themeColors;
  assert.equal(preferences.importJson(JSON.stringify(withoutTheme)), true);
  assert.deepEqual(preferences.appearance().themeColors, DEFAULT_THEME_COLORS);
});

test('panel background preferences persist and derive a darker selection independently of other surfaces', () => {
  const { uiPreferences: preferences, load } = fixture();
  const { themeColorTokens, DEFAULT_THEME_COLORS } = load('core/ui/theme.ts');
  preferences.setThemeColor('inspector', '#211831');
  preferences.setThemeColor('roleGroups', '#504060');
  const tokens = themeColorTokens(preferences.appearance().themeColors);
  assert.equal(tokens['--surface-inspector'], '#211831');
  assert.equal(tokens['--surface-role-group'], '#504060');
  assert.equal(tokens['--surface-role-group-selected'], '#382d43');
  assert.equal(tokens['--surface-base'], DEFAULT_THEME_COLORS.surface);
  const saved = preferences.exportJson();
  const restored = new (load('core/ui/ui-preferences.service.ts').UiPreferencesService)();
  assert.deepEqual(restored.appearance(), preferences.appearance());
  for (const key of ['inspector', 'roleGroups']) {
    const invalid = JSON.parse(saved);
    invalid.appearance.themeColors[key] = 'invalid';
    assert.equal(preferences.importJson(JSON.stringify(invalid)), false);
    assert.equal(preferences.exportJson(), saved);
  }
  const omitted = JSON.parse(saved);
  delete omitted.appearance.themeColors.inspector;
  delete omitted.appearance.themeColors.roleGroups;
  assert.equal(preferences.importJson(JSON.stringify(omitted)), true);
  assert.equal(preferences.appearance().themeColors.inspector, DEFAULT_THEME_COLORS.inspector);
  assert.equal(preferences.appearance().themeColors.roleGroups, DEFAULT_THEME_COLORS.roleGroups);
  preferences.resetAppearance();
  assert.deepEqual(preferences.appearance().themeColors, DEFAULT_THEME_COLORS);
});

test('appearance preferences persist, transfer atomically and derive every role variant', () => {
  const { service, uiPreferences: preferences, load } = fixture();
  const before = service.exportProjectJson(), position = service.historyPosition();
  const { roleColorTokens, defaultAppearance } = load('core/ui/appearance.ts');
  preferences.setFont('ui', 'Verdana, sans-serif');
  preferences.setFont('heading', 'Georgia, serif');
  preferences.setRoleColor('bass', '#8855CC');
  preferences.setRoleColor('drums', '#44bb99');
  const saved = preferences.exportJson();
  assert.equal(preferences.appearance().roleColors.bass, '#8855cc');
  const restored = new (load('core/ui/ui-preferences.service.ts').UiPreferencesService)();
  assert.deepEqual(restored.appearance(), preferences.appearance());
  assert.equal(service.exportProjectJson(), before);
  assert.equal(service.historyPosition(), position);
  for (const role of ['melody','bass','chords','arpeggio','drums']) {
    const original = roleColorTokens(role, defaultAppearance().roleColors[role]);
    const changed = roleColorTokens(role, '#8855cc');
    for (const key of Object.keys(original)) assert.notEqual(changed[key], original[key], key);
  }
  const drumTokens = roleColorTokens('drums', '#44bb99');
  assert.equal(drumTokens['--role-kick'], '#44bb99');
  assert.equal(drumTokens['--role-drum-surface'], drumTokens['--role-drums-surface']);
  assert.notEqual(drumTokens['--role-snare'], drumTokens['--role-kick']);
  for (const appearance of [null, {}, { ...JSON.parse(saved).appearance, fonts: { ui: 'unknown' } },
      { ...JSON.parse(saved).appearance, roleColors: { ...JSON.parse(saved).appearance.roleColors, bass: 'red' } }]) {
    assert.equal(preferences.importJson(JSON.stringify({ ...JSON.parse(saved), appearance })), false);
    assert.equal(preferences.exportJson(), saved);
  }
  preferences.setRoleColor('bass', 'invalid'); preferences.setFont('ui', 'unknown');
  assert.equal(preferences.exportJson(), saved);
  preferences.resetAppearance();
  assert.deepEqual(preferences.appearance(), defaultAppearance());
  assert.equal(preferences.importJson(saved), true);
  assert.equal(preferences.exportJson(), saved);
  const withoutAppearance = JSON.parse(saved); delete withoutAppearance.appearance;
  assert.equal(preferences.importJson(JSON.stringify(withoutAppearance)), true);
  assert.deepEqual(preferences.appearance(), defaultAppearance());
});

test('score chord grid defaults off and persists and transfers without changing project history', () => {
  const { service, uiPreferences: preferences, load } = fixture();
  const before = service.exportProjectJson(), position = service.historyPosition();
  assert.equal(preferences.scoreChordGrid(), false);
  const defaults = preferences.exportJson();
  preferences.toggleScoreChordGrid();
  assert.equal(preferences.scoreChordGrid(), true);
  const enabled = preferences.exportJson();
  const restored = new (load('core/ui/ui-preferences.service.ts').UiPreferencesService)();
  assert.equal(restored.scoreChordGrid(), true);
  assert.equal(preferences.importJson(defaults), true); assert.equal(preferences.scoreChordGrid(), false);
  assert.equal(preferences.importJson(enabled), true); assert.equal(preferences.scoreChordGrid(), true);
  assert.equal(preferences.importJson(JSON.stringify({ ...JSON.parse(enabled), scoreChordGrid: 'true' })), false);
  assert.equal(preferences.exportJson(), enabled);
  const missing = JSON.parse(defaults); delete missing.scoreChordGrid;
  assert.equal(preferences.importJson(JSON.stringify(missing)), true); assert.equal(preferences.scoreChordGrid(), false);
  assert.equal(service.exportProjectJson(), before); assert.equal(service.historyPosition(), position);
});

test('UI preferences customize per-role placement without changing project, history, or fixed order', () => {
  const { service, uiPreferences: preferences, uiStored, load } = fixture();
  const before = service.exportProjectJson(), position = service.historyPosition();
  const defaults = preferences.exportJson();
  preferences.beginQuickSettings();
  preferences.moveControl('bass', 'velocityMin');
  preferences.moveControl('bass', 'velocityMin');
  preferences.moveControl('bass', 'feedbackCount');
  assert.ok(preferences.quickSettings().bass.includes('feedbackCount'));
  assert.equal(preferences.quickSettings().chords.includes('feedbackCount'), false);
  assert.deepEqual(preferences.quickSettings().bass.filter(key => ['velocityMin','velocityMax'].includes(key)), ['velocityMin','velocityMax']);
  assert.equal(preferences.exportJson(), defaults, 'draft is not exported until saved');
  preferences.saveQuickSettings();
  assert.equal(service.exportProjectJson(), before);
  assert.equal(service.historyPosition(), position);
  assert.equal(uiStored.size, 1);
  const restored = new (load('core/ui/ui-preferences.service.ts').UiPreferencesService)();
  assert.deepEqual(restored.preferences(), preferences.preferences());
  assert.equal(service.importProjectJson(before), true);
  assert.ok(preferences.quickSettings().bass.includes('feedbackCount'), 'project loading retains UI choices');
  const importedProject = service.exportProjectJson();
  assert.equal(preferences.importJson(defaults), true);
  assert.equal(service.exportProjectJson(), importedProject, 'UI import retains musical data');
  for (const invalid of [before, '{}', '{', JSON.stringify({ ...JSON.parse(defaults), compactHistory: 'true' }),
      JSON.stringify({ ...JSON.parse(defaults), quickSettings: { bass: ['unknown'] } })]) {
    assert.equal(preferences.importJson(invalid), false);
    assert.equal(preferences.exportJson(), defaults, 'invalid import is atomic');
  }
  preferences.beginQuickSettings();
  preferences.moveControl('bass', 'velocityMin');
  preferences.moveControl('chords', 'velocityMax');
  preferences.resetQuickSettings('bass');
  assert.deepEqual(preferences.quickSettings().bass, JSON.parse(defaults).quickSettings.bass);
  assert.equal(preferences.quickSettings().chords.includes('velocityMax'), true);
});

test('combined selectors move together and reset all local values in one undo step', () => {
  const { service, uiPreferences: preferences, load } = fixture();
  const Editor = load('shared/part-settings-editor.component.ts').PartSettingsEditorComponent;
  const editor = new Editor();
  editor.role.set('chords'); editor.local.set(true); editor.values.set({ rhythm: 'full' });
  const scope = { kind: 'section-track', sectionId: service.project().arrangement[0].id, trackId: 'track-chords-1' };
  for (const [selector, flip, value] of [['rhythm', 'patternFlip', 'tresillo'], ['chordSpanFill', 'fillFlip', 'ODD']]) {
    preferences.beginQuickSettings();
    preferences.moveControl('chords', selector);
    assert.equal(preferences.quickSettings().chords.includes(selector), false);
    assert.equal(preferences.quickSettings().chords.includes(flip), false);
    if (selector === 'rhythm') assert.equal(preferences.quickSettings().chords.includes('customPattern'), false);
    preferences.moveControl('chords', selector === 'rhythm' ? 'customPattern' : flip);
    assert.ok(preferences.quickSettings().chords.includes(selector));
    assert.ok(preferences.quickSettings().chords.includes(flip));
    preferences.saveQuickSettings();
    const control = editor.controls().find(control => control.key === selector);
    editor.overriddenKeys.set([flip]);
    assert.equal(editor.inheritanceFor(control), 'custom', 'flip-only override enables the shared reset');
    const keys = load('core/ui/ui-preferences.ts').combinedControlKeys(selector);
    if (selector === 'rhythm') {
      assert.ok(preferences.quickSettings().chords.includes('customPattern'));
      editor.overriddenKeys.set(['customPattern']);
      assert.equal(editor.inheritanceFor(control), 'custom', 'grid-only override enables the shared reset');
    }
    service.updatePartSettings(scope, { [selector]: value, [flip]: true, pauseChance: 12,
      ...(selector === 'rhythm' ? { customPattern: Array(32).fill(0) } : {}) });
    const before = service.exportProjectJson();
    let resetKeys;
    editor.inheritRequested.emit = value => { resetKeys = value; };
    editor.resetControl(control);
    assert.deepEqual(resetKeys, keys);
    service.resetPartSettings(scope, keys);
    const remaining = service.project().arrangement[0].trackPartOverrides[scope.trackId];
    assert.equal(remaining[selector], undefined); assert.equal(remaining[flip], undefined);
    if (selector === 'rhythm') assert.equal(remaining.customPattern, undefined);
    assert.equal(remaining.pauseChance, 12);
    const after = service.exportProjectJson();
    service.undo(); assert.equal(service.exportProjectJson(), before);
    service.redo(); assert.equal(service.exportProjectJson(), after);
  }
});

test('placement editing exposes dormant controls and partitions customized quick/inspector sets', () => {
  const { load, uiPreferences: preferences, settings } = fixture();
  const Editor = load('shared/part-settings-editor.component.ts').PartSettingsEditorComponent;
  const quick = new Editor(), inspector = new Editor();
  for (const editor of [quick, inspector]) { editor.role.set('bass'); editor.values.set({ rhythm: 'bass1' }); }
  quick.advanced.set(false); inspector.inspectorOnly.set(true);
  assert.equal(quick.controls().some(control => control.key === 'hitsPerPattern'), false);
  preferences.beginQuickSettings();
  assert.ok(quick.controls().some(control => control.key === 'hitsPerPattern'));
  preferences.moveControl('bass', 'feedbackCount'); preferences.moveControl('bass', 'transpose');
  const quickKeys = quick.controls().map(control => control.key), inspectorKeys = inspector.controls().map(control => control.key);
  assert.ok(quickKeys.includes('feedbackCount')); assert.ok(inspectorKeys.includes('transpose'));
  assert.equal(quickKeys.some(key => inspectorKeys.includes(key)), false);
  assert.deepEqual([...quickKeys, ...inspectorKeys].sort(), settings.PART_CONTROLS.bass.filter(control => !['patternFlip', 'fillFlip', 'customPattern'].includes(control.key)).map(control => control.key).sort());
  preferences.saveQuickSettings();
  assert.equal(quick.controls().some(control => control.key === 'hitsPerPattern'), false);
});


test('drum sequencer resolves percussion names and multiplies only its selected scope', () => {
  const { service, load } = fixture();
  const first = 'track-drums-1', second = service.duplicateTrack(first);
  const sectionId = service.project().arrangement[0].id;
  const { PatternSequencerComponent } = load('shared/pattern-sequencer.component.ts');
  const { drumInstrumentLabel } = load('core/music/drum-instruments.ts');
  const popup = new PatternSequencerComponent();
  popup.data.role = 'drums';
  popup.data.scope = { kind: 'section-track', sectionId, trackId: first };
  for (const row of popup.rows()) {
    const track = service.project().tracks.find(track => track.id === row.id);
    assert.equal(row.instrument, drumInstrumentLabel(track.generatorSettings.pitch, 'general-midi'));
  }
  const before = service.project().tracks;
  const position = service.historyPosition();
  const select = { value: '2' };
  assert.equal(popup.canMultiplyHits(), true);
  popup.multiplyHits({ target: select });
  assert.equal(select.value, '');
  assert.equal(popup.rows().find(row => row.id === first).hits, 8);
  assert.equal(popup.rows().find(row => row.id === second).hits, 4);
  assert.deepEqual(service.project().tracks, before, 'section action preserves global drums');
  assert.equal(service.historyPosition(), position + 1, 'one undo entry');
  service.undo();
  assert.equal(popup.rows().find(row => row.id === first).hits, 4);
  popup.patch(first, { rhythm: 'melody' });
  assert.equal(popup.canMultiplyHits(), false);
});


test('Bass On Compose controls implicit rerolls, persists, and preserves locked tracks', () => {
  const { service } = fixture();
  service.updateTrackGenerationPolicy('bass', { onCompose: false });
  service.updateSettings({ seed: '123' });
  service.addTrack('bass');
  service.setTrackRerollLock('track-bass-2', true);
  const bass = () => JSON.parse(JSON.stringify(service.project().tracks.filter(track => track.role === 'bass')));
  const before = bass();
  service.generateComposition('compose');
  assert.deepEqual(bass(), before);
  assert.equal(service.updateTrackGenerationPolicy('bass', { onCompose: true }), 'changed');
  const enabled = service.exportProjectJson();
  assert.equal(service.importProjectJson(enabled), true);
  assert.equal(service.project().trackGenerationPolicies.bass.onCompose, true);
  assert.equal(service.generateComposition('compose'), 'changed');
  assert.equal(service.project().trackRerollCounts.bass, 1);
  assert.notDeepEqual(bass()[0].generatorSettings, before[0].generatorSettings);
  assert.deepEqual(bass()[1], before[1]);
  service.undo();
  assert.deepEqual(projectData(service.exportProjectJson()), projectData(enabled));
  service.updateTrackGenerationPolicy('bass', { onCompose: false });
  service.generateComposition('compose');
  assert.deepEqual(bass(), before);
  assert.equal(service.project().trackRerollCounts?.bass ?? 0, 0);
});
