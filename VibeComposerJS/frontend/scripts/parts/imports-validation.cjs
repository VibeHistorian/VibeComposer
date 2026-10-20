const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const { fixture } = require('../support/project-fixture.cjs');
const { assertRejectedProjectImport } = require('../support/project-import-assertions.cjs');

test('track-only projects preserve MIDI and session state through role edits, rerolls and reload', () => {
  const { service, stored, load } = fixture();
  const { ARRANGED_PARTS } = load('core/music/part-role.ts');
  const { generateCompositionMidi } = load('core/music/midi-export.ts');
  const legacyKeys = [...ARRANGED_PARTS, 'mix'];
  const assertTrackOnly = project => {
    for (const key of legacyKeys) assert.equal(Object.hasOwn(project, key), false, key);
  };
  assertTrackOnly(service.project());
  for (const role of ARRANGED_PARTS) {
    service.updatePartSettings({ kind: 'global-role', role }, { velocityMin: 1 });
    service.rerollRoleInstruments(role);
    service.rerollRoleTracks(role);
    assertTrackOnly(service.project());
  }
  service.toggleTrackMix(['track-bass-1'], 'solo');
  const expected = generateCompositionMidi(service.project());
  const saved = service.exportProjectJson();
  const input = JSON.parse(saved);
  for (const key of legacyKeys) input[key] = { obsolete: 'ignored' };
  assert.equal(service.importProjectJson(JSON.stringify(input)), true);
  assertTrackOnly(service.project());
  assert.deepEqual(generateCompositionMidi(service.project()), expected);
  assertTrackOnly(JSON.parse(service.exportProjectJson()));
  assertTrackOnly(JSON.parse(stored.get('vibecomposer.project.v12')));
  const restored = new (load('core/project/project.service.ts').ProjectService)();
  assertTrackOnly(restored.project());
  assert.deepEqual(generateCompositionMidi(restored.project()), expected);
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
    assertRejectedProjectImport(service, JSON.stringify(saved));
  }
});

test('Java XML translation passes the real project decoder, round-trips and produces identical MIDI after reload', () => {
  const {service,load}=fixture(), {parseXml}=require('../translate-java-config.cjs');
  const source=fs.readFileSync(path.join(__dirname, '..','../GENERATOR_REFACTOR_TEST_GUICONFIG.xml-config'),'utf8');
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
  assertRejectedProjectImport(service, JSON.stringify(invalid));
  assert.equal(service.project().tempoBpm, 33);
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
    assertRejectedProjectImport(service, JSON.stringify(project)); assert.equal(service.exportProjectJson(),saved);
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
    assertRejectedProjectImport(service, JSON.stringify(imported));
  }
  for (const patch of [{ useMelodyHarmony:1 },{ swapMelody:'true' },{ unknown:false },null,[]]) {
    assert.equal(service.updateSectionHarmony(id,patch),'invalid');
  }
  assert.equal(service.updateSectionHarmony('deleted',{ swapMelody:true }),'invalid');
  for (const key of ['useMelodyHarmony','swapMelody']) {
    const imported = JSON.parse(saved); imported.arrangement[0][key]='true';
    assertRejectedProjectImport(service, JSON.stringify(imported));
  }
  assert.equal(service.exportProjectJson(),saved); assert.equal(service.canRedo(),true);
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
    assertRejectedProjectImport(service, JSON.stringify(data));
  }
  for (const pair of [[-1,0],[0,32],[NaN,0],[0,1.5],[0,0]]) service.moveProgressionChord(...pair);
  for (const custom of [[], [null], {}, [null,null,null,null,null]]) {
    const data = JSON.parse(before); data.progressionChords = custom;
    assertRejectedProjectImport(service, JSON.stringify(data));
  }
  assert.equal(service.exportProjectJson(), before); assert.equal(service.canRedo(), true);
});

test('chord articulation rejects malformed imports and wrong-role controls without losing redo', () => {
  const { service, settings } = fixture();
  service.updateSettings({ name: 'redo' }); service.undo();
  const saved = service.exportProjectJson();
  for (const patch of [{ strum: -1 }, { strum: 2001 }, { strum: 1.5 }, { strumType: 'UP' }, { strumPauseChance: 101 },
    { transitionChance: null }, { transitionSplit: 1001 }, { patternJoinMode: 'expand' }]) {
    service.updatePartSettings({ kind: 'global-role', role: 'chords' }, patch);
    for (const location of ['track', 'section-role', 'section-track']) {
      const data = JSON.parse(saved);

      if (location === 'track') Object.assign(data.tracks.find(track => track.role === 'chords').generatorSettings, patch);
      if (location === 'section-role') data.arrangement[0].rolePartOverrides = { chords: patch };
      if (location === 'section-track') data.arrangement[0].trackPartOverrides = { 'track-chords-1': patch };
      assertRejectedProjectImport(service, JSON.stringify(data));
    }
  }
  for (const role of ['melody', 'bass', 'arpeggio', 'drums']) {
    assert.equal(settings.decodePartPatch(role, { strum: 100 }), undefined);
    assert.equal(settings.decodePartPatch(role, { transitionSplit: 625 }), undefined);
  }
  assert.equal(service.exportProjectJson(), saved); assert.equal(service.canRedo(), true);
});

test('automatic policy import accepts omission and rejects invalid or unknown fields', () => {
  const { service } = fixture();
  for (const role of ['melody', 'bass', 'chords', 'arpeggio', 'drums']) {
    assert.equal(service.updateTrackGenerationPolicy(role, { onCompose: false }), 'changed');
    const data = JSON.parse(service.exportProjectJson());
    data.trackGenerationPolicies[role].onCompose = 'true';
    assertRejectedProjectImport(service, JSON.stringify(data));
  }
  assert.equal(service.updateTrackGenerationPolicy('bass', { patterns: true }), 'invalid');
  assert.equal(service.updateTrackGenerationPolicy('drums', { onRegenerate: true }), 'invalid');
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
    assertRejectedProjectImport(service, JSON.stringify(project));
  }
  for (const value of [null, [], { unknown: {} }]) {
    const project = JSON.parse(snapshot); project.instrumentGenerationPolicies = value;
    assertRejectedProjectImport(service, JSON.stringify(project));
  }
  const project = JSON.parse(snapshot); project.tracks[0].instrumentLocked = 'yes';
  assertRejectedProjectImport(service, JSON.stringify(project));
  for (const pool of [['MELODY'], 'AUTO', 'DRUM', 'unknown']) {
    const project = JSON.parse(snapshot); project.tracks[0].instrumentPool = pool;
    assertRejectedProjectImport(service, JSON.stringify(project));
  }
  assert.equal(service.exportProjectJson(), snapshot); assert.equal(service.canRedo(), true);
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
    assertRejectedProjectImport(service, JSON.stringify(project));
  }
  for (const role of ['bass', 'chords', 'drums', 'melody']) {
    for (const value of [-1, 2147483648, '1', null]) {
      const project = JSON.parse(before); project.trackRerollCounts = { [role]: value };
      assertRejectedProjectImport(service, JSON.stringify(project));
    }
  }
  assert.equal(service.exportProjectJson(), before); assert.equal(service.canRedo(), true);
});

test('Arpeggio reroll rejects invalid rules and effective conflicts atomically, retaining redo on locks and stale targets', () => {
  const { service, load } = fixture();
  const core = load('core/music/track-generation.ts');
  service.updateSettings({ name: 'redo' }); service.undo(); const before = service.exportProjectJson();
  for (const patch of [{ fixedHits: 0 }, { maxSplit: 101 }, { randomHits: 1 }, { lengthMin: 101, lengthMax: 100 },
    { velocityMin: 100, velocityMax: 90 }, { voices: 2 }, { voicesMode: 'random' }, { maxRepeat: 0 }, { extra: true }]) {
    assert.equal(service.updateArpeggioPolicy(patch), 'invalid');
    const project = JSON.parse(before); project.trackGenerationPolicies = { arpeggio: { ...core.DEFAULT_ARPEGGIO_POLICY, ...patch } };
    assertRejectedProjectImport(service, JSON.stringify(project));
  }
  for (const metadata of [{ trackRerollCounts: { arpeggio: -1 } }, { trackRerollCounts: { arpeggio: '1' } },
    { trackRerollCounts: { unknown: 0 } }, { trackGenerationPolicies: { drums: {} } }]) {
    assertRejectedProjectImport(service, JSON.stringify({ ...JSON.parse(before), ...metadata }));
  }
  const invalidLock = JSON.parse(before); invalidLock.tracks[0].rerollLocked = 1;
  assertRejectedProjectImport(service, JSON.stringify(invalidLock));
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
    for (const location of ['track', 'section-role', 'section-track']) {
      const project = JSON.parse(saved);
      if (location === 'track') Object.assign(project.tracks.find(track => track.role === 'bass').generatorSettings, patch);
      if (location === 'section-role') project.arrangement[0].rolePartOverrides = { bass: patch };
      if (location === 'section-track') project.arrangement[0].trackPartOverrides = { 'track-bass-1': patch };
      assertRejectedProjectImport(service, JSON.stringify(project));
    }
  }
  for (const role of ['melody', 'arpeggio', 'drums']) assert.equal(settings.decodePartPatch(role, { patternJoinMode: 'JOIN' }), undefined);
  assert.equal(service.exportProjectJson(), saved); assert.equal(service.canRedo(), true);
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
      assertRejectedProjectImport(service, JSON.stringify(project), `${role} ${JSON.stringify(patch)}`);
    }
  }
  assert.equal(service.exportProjectJson(), before); assert.equal(service.canRedo(), true);
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
    assertRejectedProjectImport(service, JSON.stringify(project));
    assert.throws(() => generateDrumPart(42n, 4, { ...service.project().tracks.find(track => track.role === 'drums').generatorSettings, ...patch }), RangeError);
  }
  for (const scope of [{ kind: 'global-role', role: 'drums' }, { kind: 'global-track', trackId: first },
    { kind: 'section-role', sectionId, role: 'drums' }, { kind: 'section-track', sectionId, trackId: first }]) {
    for (const patch of [...invalid, { pitch: 38 }]) service.updatePartSettings(scope, patch);
  }
  const obsolete = JSON.parse(before); obsolete.tracks.find(track => track.role === 'drums').generatorSettings = { groove: 'rock', swingPercent: 50 };
  assertRejectedProjectImport(service, JSON.stringify(obsolete));
  const wrongChannel = JSON.parse(before); wrongChannel.tracks.find(track => track.id === first).midiChannel = 1;
  assertRejectedProjectImport(service, JSON.stringify(wrongChannel));
  assert.equal(service.exportProjectJson(), before); assert.equal(service.canRedo(), true);
  assert.throws(() => generateDrumPart(9007199254740992, 4, service.project().tracks.find(track => track.role === 'drums').generatorSettings), RangeError);
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
    assertRejectedProjectImport(service, JSON.stringify(saved));
    assert.throws(() => generateMelody(42n, 'C', 'major', [1], { algorithm: 'block', ...patch }), RangeError);
  }
  assert.equal(service.canRedo(), true);
  assert.throws(() => generateMelody(9007199254740992, 'C', 'major', [1], { algorithm: 'block' }), RangeError);
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
    invalid.tracks.find(track => track.role === 'drums').generatorSettings.obsoleteGroove = 'rock';
    invalid.tracks[0].generatorSettings.unknownControl = 1;
    invalid.tracks.find(track => track.role === 'bass').mix.panPercent = 200;
    assert.equal(service.importProjectJson(JSON.stringify(invalid)), false);
    assert.equal(service.exportProjectJson(), before);
    assert.equal(warnings.length, 1);
    const details = warnings[0][1].join('\n');
    assert.match(details, /project\.name: missing required property/);
    assert.match(details, /project\.tempoBpm: expected an integer from 30 to 240/);
    assert.match(details, /project\.tracks\[4\]\.generatorSettings\.obsoleteGroove: unknown property/);
    assert.match(details, /project\.tracks\[0\]\.generatorSettings\.unknownControl: unknown property/);
    assert.match(details, /project\.tracks\[1\]\.mix\.panPercent/);

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
    assertRejectedProjectImport(service, JSON.stringify(imported));
  }
  const orphan = JSON.parse(initial);
  orphan.arrangement[1].trackPartOverrides = { missing: { transpose: 12 } };
  assertRejectedProjectImport(service, JSON.stringify(orphan));
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
    assertRejectedProjectImport(service, JSON.stringify(saved));
    assert.equal(service.exportProjectJson(), before);
  }
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
    assertRejectedProjectImport(service, JSON.stringify(invalid));
    assert.equal(service.importProjectJson(before), true);
    assert.equal(service.project().tracks.find((track) => track.id === trackId).generatorSettings.chordSpanFill, undefined);
  }
  const before = service.exportProjectJson();
  service.updatePartSettings({ kind: 'section-role', sectionId, role: 'melody' }, { chordSpanFill: 'ODD' });
  assert.equal(service.project().arrangement[0].rolePartOverrides.melody.chordSpanFill, 'ODD');
  service.undo();
  assert.equal(service.exportProjectJson(), before);
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
    assertRejectedProjectImport(service, JSON.stringify(project));
  }
  assert.equal(service.importProjectJson(before), true);
  assert.equal(service.project().tracks.find((track) => track.id === trackId).generatorSettings.hitsPerPattern, undefined);
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
    assertRejectedProjectImport(service, JSON.stringify(project));
  }
  for (const scope of scopes) service.updatePartSettings(scope, { useCustomVelocities: 1 });
  for (const role of ['melody']) {
    service.updatePartSettings({ kind: 'section-role', sectionId, role }, { useCustomVelocities: true, customVelocities: Array(32).fill(80) });
  }
  assert.equal(service.exportProjectJson(), before);
  assert.equal(service.canRedo(), true);
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
    assertRejectedProjectImport(service, JSON.stringify(project));
  }
  for (const role of ['melody']) {
    service.updatePartSettings({ kind: 'section-role', sectionId, role }, { customPattern: Array(32).fill(1) });
  }
  assert.equal(service.exportProjectJson(), before);
  assert.equal(service.canRedo(), true);
});
