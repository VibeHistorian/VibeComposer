const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const ts = require('typescript');
const { Midi } = require('@tonejs/midi');

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
    { trackRerollCounts: { bass: 0 } }, { trackGenerationPolicies: { drums: {} } }]) {
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
  service.rerollArpeggioTracks(); service.updateSettings({ name: 'redo' }); service.undo();
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
  editor.advanced.set(true); editor.values.set({ rhythm: 'alternating' });
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
  for (const role of ['melody', 'chords', 'arpeggio', 'drums']) assert.equal(settings.decodePartPatch(role, { patternJoinMode: 'JOIN' }), undefined);
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
    for (const key of ['generationEnabled', 'patternSeed', 'offset', 'feedbackCount', 'feedbackDuration', 'feedbackVol']) assert.ok(keys(role).includes(key));
    assert.equal(keys(role).includes('accents'), role === 'melody');
    assert.equal(keys(role).includes('patternRepeat'), role === 'arpeggio');
    assert.equal(keys(role).includes('exceptionChance'), ['arpeggio', 'drums'].includes(role));
    assert.equal(keys(role).includes('pauseChance'), role !== 'bass');
    assert.equal(keys(role).includes('swingPercent'), role !== 'bass');
    assert.equal(keys(role).includes('stretchEnabled'), ['chords', 'arpeggio'].includes(role));
    const track = service.project().tracks.find(track => track.role === role);
    const generate = patch => phrase.generateTrackPhrase(service.project(), { ...track, generatorSettings: { ...track.generatorSettings, ...patch } });
    assert.deepEqual(generate({ generationEnabled: false }), []);
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

test('all-role scoped timing, seeds and generation enable survive history, snapshots, JSON and manual replacements', () => {
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
    service.updatePartSettings(part, { generationEnabled: false });
    assert.ok(service.project().arrangement[0].trackPresence[id]);
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
    service.updatePartSettings(part, { generationEnabled: false, feedbackCount: 5 });
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
    const invalid = [{ generationEnabled: 0 }, { patternSeed: 2147483648 }, { patternSeed: '42' }, { offset: -1001 },
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
  const rows = workspace.scoreTracks(), drumRow = rows.find(row => row.color === 'drums');
  assert.equal(rows.length, 5);
  assert.equal(drumRow.name, 'Drums');
  assert.deepEqual(drumRow.trackIds, [kick, snare, otherKick]);
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
    assert.ok(drumHits.every(area => area.trackIndex === 4 && area.note.part === snare));
    const area = drumHits[0];
    let selected;
    score.noteSelected.emit = note => { selected = note; workspace.openNoteEditor(note); };
    score.onCanvasClick({ clientX: area.x + 1, clientY: area.y + area.height / 2 });
    assert.equal(selected.part, snare);
    assert.equal(workspace.selectedTrackId(), snare);
    assert.equal(workspace.editingNoteId(), selected.id);
    score.model.hiddenTrackIds = new Set(); score.drawStatic();
    assert.ok(score.hitAreas.filter(area => area.note.color === 'drums').every(area => area.trackIndex === 4));
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

test('drum identities are independent, named, fixed to channel 10 and preserved by history and manual editing', () => {
  const { service, phrase, workspace, ui, load, settings } = fixture();
  const first = 'track-drums-1', track = id => service.project().tracks.find(track => track.id === id);
  assert.equal(track(first).generatorSettings.pitch, 36);
  assert.ok(phrase.phraseForTrack(service.project(), track(first)).every(note => note.midi === 36));
  const before = service.exportProjectJson();
  service.updateTrackGeneratorSettings(first, { pitch: 38 });
  assert.equal(workspace.instrumentName(track(first)), 'Snare');
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
  assert.deepEqual(editor.drumVoices(), [{ midi: 39, label: 'Clap' }, { midi: 42, label: 'Closed hi-hat' }]);
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

test('current block melody matches 80 complete production Java phrases at fixed seeds', () => {
  const { load } = fixture();
  const { generateMelody } = load('core/music/melody-generator.ts');
  const cases = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/melody-core.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(cases.length, 80);
  for (const [caseIndex, sample] of cases.entries()) {
    const progression = Array(sample.chordCount).fill(1);
    const actual = generateMelody(BigInt(sample.seed), sample.key, sample.scale, progression, sample.settings, sample.notesSeedOffset);
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
    velocityMin: 100, velocityMax: 100, accents: 0, noteLengthMultiplier: 50, transpose: 12 });
  const after = phrase.layOutTrackPhrase(service.project(), track());
  const start = service.project().arrangement[0].measures * 4, end = start + service.project().arrangement[1].measures * 4;
  const outside = notes => notes.filter(note => note.startBeat < start || note.startBeat >= end);
  assert.deepEqual(outside(after), outside(before));
  assert.notDeepEqual(after, before);
  assert.ok(after.filter(note => note.startBeat >= start && note.startBeat < end).every(note => note.velocity === 100));
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
  const invalid = [{ speed: 101 }, { speed: -101 }, { chordNoteChoices: [] }, { chordNoteChoices: [15] },
    { melodyPatternOffsets: Array(33).fill(1) }, { melodyPatternOffsets: [1.5] }, { patternSeed: 2147483648 },
    { patternFlexible: 1 }, { velocityMin: 106 }, { splitChance: 50 }, { accents: -1 }];
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
  assert.deepEqual(phrase.phraseForTrack(service.project(), track), editor.draftNotes());
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

function fixture() {
  const cache = new Map();
  const signal = (value) => {
    const read = () => value;
    read.set = (next) => { value = next; };
    read.asReadonly = () => read;
    return read;
  };
  const input = (value) => signal(value);
  input.required = () => signal(undefined);
  let service, ui;
  const angular = { Injectable: () => (type) => type, Component: () => (type) => type, Directive: () => (type) => type,
    Input: () => () => {}, Output: () => () => {}, EventEmitter: class { values = []; emit(value) { this.values.push(value); } },
    signal, computed: (read) => read, effect: () => {}, input,
    output: () => ({ emit() {} }), ElementRef: class ElementRef {}, ChangeDetectionStrategy: { OnPush: 0 },
    viewChild: Object.assign(() => () => undefined, { required: () => () => undefined }),
    inject: (type) => type.name === 'ProjectService' ? service : type.name === 'WorkspaceUiService' ? ui
      : type.name === 'AudioPlaybackService' ? {} : preferences,
  };
  const root = path.join(__dirname, '../src/app');
  function load(file) {
    const absolute = path.resolve(root, file);
    // Workspace state is real; child rendering components are covered by the Angular build and overview tests.
    if (absolute.endsWith('.component.ts') && !['workspace-canvas.component.ts', 'score-canvas.component.ts', 'edit-workspace.component.ts', 'mix-workspace.component.ts', 'part-settings-editor.component.ts', 'compact-knob.component.ts']
      .some((name) => absolute.endsWith(name))) return {};
    if (cache.has(absolute)) return cache.get(absolute).exports;
    const module = { exports: {} };
    cache.set(absolute, module);
    const compiled = ts.transpileModule(fs.readFileSync(absolute, 'utf8'), { compilerOptions: {
      target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, experimentalDecorators: true,
    } }).outputText;
    new Function('require', 'module', 'exports', compiled)((name) => name === '@angular/core' ? angular
      : name === '@angular/common' ? { TitleCasePipe: class {} }
      : name.startsWith('.') ? load(path.resolve(path.dirname(absolute), name + '.ts')) : require(name), module, module.exports);
    return module.exports;
  }
  const stored = new Map();
  global.sessionStorage = { getItem: (key) => stored.get(key) ?? null, setItem: (key, value) => stored.set(key, value) };
  const sectionTypes = load('core/project/section-type-settings.service.ts');
  const preferences = { chances: () => sectionTypes.DEFAULT_SECTION_TYPE_CHANCES };
  const { ProjectService } = load('core/project/project.service.ts');
  service = new ProjectService();
  const { WorkspaceUiService } = load('shared/workspace-ui.service.ts');
  ui = new WorkspaceUiService();
  const { WorkspaceCanvasComponent } = load('shared/workspace-canvas.component.ts');
  const workspace = new WorkspaceCanvasComponent();
  const phrase = load('core/music/phrase.ts');
  const settings = load('core/music/part-settings.ts');
  const midi = load('core/music/midi-export.ts');
  return { service, ui, workspace, phrase, settings, midi, stored, load };
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
  const draft = [...editor.draftNotes()];
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
  const { MixWorkspaceComponent } = load('features/mix/mix-workspace.component.ts');
  const mixer = new MixWorkspaceComponent();
  assert.equal(mixer.tracks()[0].role, 'melody');
  assert.equal(mixer.noteCount(id), draft.length);
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
  const random = new JavaRandom(BigInt.asIntN(32, BigInt.asIntN(32, seed) + 20000n));
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
  const random = new JavaRandom(BigInt.asIntN(32, BigInt.asIntN(32, seed) + 20000n));
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
  editor.values.set({ ...editor.values(), rhythm: 'alternating' });
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

test('velocity grids preview during slider gestures, commit once, support wheel, and reject stale/mixed edits', () => {
  const { service, load, settings } = fixture();
  const { PartSettingsEditorComponent } = load('shared/part-settings-editor.component.ts');
  const editor = new PartSettingsEditorComponent();
  editor.role.set('chords');
  const scope = { kind: 'section-track', sectionId: service.project().arrangement[1].id, trackId: 'track-chords-1' };
  editor.values.set({ ...settings.settingsValues(service.project().chords, 'chords'), useCustomVelocities: true });
  const commits = [];
  editor.settingsChanged.emit = change => { commits.push(change); service.updatePartSettings(scope, { [change.key]: change.value }); };
  assert.equal(editor.controls().some(control => control.key === 'customVelocities'), true);
  assert.equal(editor.controls().some(control => control.key === 'velocityMin'), false);
  const before = service.exportProjectJson();
  const input = { value: '25' }; const event = { target: input };
  editor.previewVelocity(1, event);
  input.value = '110'; editor.previewVelocity(1, event);
  assert.equal(editor.velocitySlots()[1], 110);
  assert.equal(service.exportProjectJson(), before);
  editor.commitVelocity(1, event);
  assert.equal(commits.length, 1);
  assert.equal(commits[0].value[1], 110);
  assert.equal(commits[0].value.length, 32);
  service.undo(); assert.equal(service.exportProjectJson(), before);
  input.value = '0'; editor.previewVelocity(2, event); editor.cancelVelocity();
  assert.equal(editor.velocitySlots()[2], 79);
  assert.equal(commits.length, 1);
  editor.previewVelocity(2, event);
  editor.values.set({ ...editor.values(), hitsPerPattern: 5 });
  editor.commitVelocity(2, event);
  assert.equal(commits.length, 1);
  editor.wheelVelocity(3, { deltaY: -1, shiftKey: true, preventDefault() {}, stopPropagation() {} });
  assert.equal(commits[1].value[3], 80);
  editor.values.set({ ...editor.values(), customVelocities: null });
  assert.equal(editor.velocitySlots(), null);
  editor.previewVelocity(1, event); assert.equal(editor.velocityDraft(), null);
  editor.useDefaultVelocities(); assert.deepEqual(commits[2].value, Array(32).fill(79));
  editor.values.set({ ...editor.values(), useCustomVelocities: false });
  assert.equal(editor.controls().some(control => control.key === 'customVelocities'), false);
  assert.equal(editor.controls().some(control => control.key === 'velocityMin'), true);
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

test('custom painting previews until release, commits once, supports keyboard and cancels stale gestures', () => {
  const { service, settings, load } = fixture();
  const { PartSettingsEditorComponent } = load('shared/part-settings-editor.component.ts');
  const editor = new PartSettingsEditorComponent();
  const trackId = 'track-chords-1';
  const scope = { kind: 'section-track', sectionId: service.project().arrangement[1].id, trackId };
  service.updatePartSettings(scope, { rhythm: 'custom', patternShift: 3, patternFlip: true });
  const base = settings.settingsValues(service.project().tracks.find((track) => track.id === trackId).generatorSettings, 'chords');
  editor.role.set('chords');
  editor.values.set({ ...base, rhythm: 'custom', patternShift: 3, patternFlip: true });
  const commits = [];
  editor.settingsChanged.emit = (change) => { commits.push(change); service.updatePartSettings(scope, { [change.key]: change.value }); };
  const button = { focus() {}, setPointerCapture() {} };
  const before = service.exportProjectJson();
  editor.startPainting({ button: 0, pointerId: 9, target: button, currentTarget: button, preventDefault() {} }, 0);
  assert.equal(commits.length, 0);
  assert.equal(editor.rhythmPreview()[0], 1);
  const slot = { dataset: { rhythmSlot: '2' } };
  const grid = { ownerDocument: { elementFromPoint: () => ({ closest: () => slot }) }, contains: () => true };
  editor.movePainting({ pointerId: 9, currentTarget: grid, clientX: 1, clientY: 1 });
  assert.equal(editor.rhythmPreview()[2], 1);
  assert.equal(service.exportProjectJson(), before);
  editor.finishPainting({ pointerId: 9 });
  assert.equal(commits.length, 1);
  assert.equal(commits[0].value[29], 0, 'unshift and unflip visible subdivision 1');
  assert.equal(commits[0].value[30], 0, 'paint intervening slots when pointer events skip a subdivision');
  assert.equal(commits[0].value[31], 0);
  service.undo();
  assert.equal(service.exportProjectJson(), before, 'whole paint gesture is one history entry');
  service.redo();
  editor.values.set({ ...editor.values(), customPattern: commits[0].value });
  editor.slotClick({ detail: 1 }, 1);
  assert.equal(commits.length, 1, 'synthetic pointer click after release cannot double-toggle');
  editor.slotClick({ detail: 0 }, 1);
  assert.equal(commits.length, 2, 'keyboard/assistive activation toggles the slot');
  editor.startPainting({ button: 0, pointerId: 10, target: button, currentTarget: button, preventDefault() {} }, 0);
  editor.cancelPainting();
  editor.finishPainting({ pointerId: 10 });
  assert.equal(commits.length, 2);
  editor.startPainting({ button: 0, pointerId: 11, target: button, currentTarget: button, preventDefault() {} }, 0);
  editor.values.set({ ...editor.values(), hitsPerPattern: 3 });
  editor.finishPainting({ pointerId: 11 });
  assert.equal(commits.length, 2, 'context/project change rejects pending painting');
  editor.values.set({ ...editor.values(), customPattern: null });
  assert.equal(editor.rhythmPreview(), null, 'mixed grid never picks a representative track');
  editor.useFullGrid();
  assert.deepEqual(commits[2].value, Array(32).fill(1));
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
