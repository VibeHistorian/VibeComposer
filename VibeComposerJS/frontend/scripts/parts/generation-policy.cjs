const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const { Midi } = require('@tonejs/midi');
const { fixture, projectData } = require('../support/project-fixture.cjs');
const { assertRejectedProjectImport } = require('../support/project-import-assertions.cjs');

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
  const { TransportDockComponent } = load('features/transport/transport-dock.component.ts');
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
  const { samePhraseInputs } = require('../music-loader.cjs').loadMusic('phrase-inputs.ts');
  assert.equal(samePhraseInputs(original, service.project()), true);
  service.updateTrackGeneratorSettings('track-bass-1', { transpose: 12 });
  workspace.scoreNotes();
  assert.ok(generations > count, 'musical settings still regenerate notes');
  assert.equal(samePhraseInputs(original, service.project()), false);
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
    assertRejectedProjectImport(service, JSON.stringify({ ...JSON.parse(saved), [field]: 'false' }));
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
  const source=service.project().tracks.find(track => track.role === 'arpeggio').generatorSettings; let custom=0,melody=0;
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
  random.nextInt(15);
  random.nextInt(2);
  assert.equal(Object.hasOwn(next, 'maxNoteExceptions'), false);
  assert.equal(Object.hasOwn(next, 'noteExceptionChance'), false);
  random.nextInt(50); // Keep the following Java creation draws aligned.
  random.nextInt(6); // Primary fill is forced to ALL after this draw.
  assert.equal(next.fillPauses, random.nextBoolean());
  assert.equal(Object.hasOwn(next, 'leadChordsChance'), false);
  assert.equal(Object.hasOwn(next, 'doubledRhythmChance'), false);
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
  service.setSectionTrackPresence(0, 'track-melody-1', true);
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
    assertRejectedProjectImport(service, JSON.stringify(imported));
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
  service.updatePartSettings({ kind: 'global-role', role: 'melody' }, { targetDirection: 'DESC' });
  const inputs = { targetNoteVariation: 6, useDirectionsFromProgression: true };
  assert.equal(service.updateMelodyGlobal(inputs), 'changed');
  assert.deepEqual(service.project().tracks.filter(track => track.role !== 'melody'), initial.tracks.filter(track => track.role !== 'melody'));
  assert.deepEqual(phrase.generateTrackPhrase(service.project(), track(primary)), original);
  const before = service.exportProjectJson(), arrangement = service.project().arrangement;
  const { chooseMelodyTargets } = load('core/music/melody-targets.ts');
  const targetSeed = ((track(primary).generatorSettings.patternSeed || Number(BigInt.asIntN(32, BigInt(service.project().seed)))) + 1) | 0;
  const expected = chooseMelodyTargets(targetSeed, service.project().progression, inputs, 'C', 'major', undefined, track(primary).generatorSettings);
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
  service.updatePartSettings({ kind: 'global-role', role: 'melody' }, { targetDirection: 'ANY' });
  service.updateMelodyGlobal({ targetNoteVariation: 1, useDirectionsFromProgression: false });
  assert.equal(service.generateComposition('regenerate'), 'changed');
  assert.deepEqual(track(primary).generatorSettings.chordNoteChoices,
    chooseMelodyTargets(targetSeed, service.project().progression, service.project().melodyGlobal, 'C', 'major', undefined, track(primary).generatorSettings));
});

test('P5e forced outline follows the first stored Melody and only copies the production outline fields', () => {
  const { service, phrase } = fixture();
  for (const role of ['melody', 'bass', 'chords', 'arpeggio', 'drums']) service.updateTrackGenerationPolicy(role, { onCompose: false });
  service.updateSettings({ randomizeTransposeOnCompose: false, randomizeTempoOnCompose: false });
  const primary = 'track-melody-1', second = service.duplicateTrack(primary), third = service.duplicateTrack(second);
  const sourceFields = { chordNoteChoices: [1, -2, 4], melodyPatternOffsets: [1, -1, 2], speed: 0,
    maxBlockChange: 2, blockJump: 3, splitChance: 40 };
  service.updatePartSettings({ kind: 'global-track', trackId: primary }, sourceFields);
  service.updatePartSettings({ kind: 'global-track', trackId: second }, { speed: 100, startNoteChance: 0,
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
  assert.equal(service.project().tracks.find(track => track.role === 'bass').generatorSettings.noteVariation, 100);
  const second = service.addTrack('bass'), sectionId = service.project().arrangement[0].id;
  service.updatePartSettings({ kind: 'section-track', sectionId, trackId: second }, { noteVariation: 90 });
  service.setTrackRerollLock(second, true);
  const previous = service.exportProjectJson();
  service.rerollRoleTracks('bass');
  assert.ok(service.project().tracks.find(track => track.role === 'bass').generatorSettings.noteVariation <= 50);
  assert.equal(service.project().tracks.find(track => track.id === second).generatorSettings.noteVariation, 100);
  assert.equal(service.project().arrangement[0].trackPartOverrides[second].noteVariation, 90);
  const after = service.exportProjectJson(); service.undo(); assert.equal(service.exportProjectJson(), previous);
  service.rerollRoleTracks('bass'); assert.equal(service.exportProjectJson(), after);
  service.generateRoleTracks('bass', 3);
  assert.ok(service.project().tracks.slice(-3).every(track => track.generatorSettings.noteVariation <= 50));
});

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
  const expected = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'fixtures/role-policy.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  for (const sample of expected.melodyPatterns) assert.deepEqual(core.chooseMelodyPattern(sample.seed), sample.pattern);
  for (const sample of expected.bass) {
    const source = { ...service.project().tracks.find(track => track.role === 'bass'), generatorSettings: { ...service.project().tracks.find(track => track.role === 'bass').generatorSettings, chordSpan: sample.span } };
    const [actual] = core.rerollRoleSettings(BigInt(sample.seed), 'bass', {}, [source], [sample.order]);
    for (const [key, value] of Object.entries(sample.values)) {
      if (key === 'javaRhythm') assert.equal(actual.rhythm, names[value]);
      else assert.deepEqual(actual[key], key === 'velocityMax' ? Math.max(value - 1, sample.values.velocityMin) : value);
    }
  }
  [36,38,42,53,60].forEach((pitch, family) => {
    const seen = new Set();
    for (let seed = 0; seed < 100; seed++) {
      const source = { ...service.project().tracks.find(track => track.role === 'drums'), generatorSettings: { ...service.project().tracks.find(track => track.role === 'drums').generatorSettings, pitch } };
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

test('role-specific policies preserve opt-outs and share only eligible seeds and swing', () => {
  const { service, load } = fixture();
  const core = load('core/music/track-generation.ts');
  const tracks = role => service.project().tracks.filter(track => track.role === role);
  const source = { ...tracks('chords')[0], generatorSettings: { ...service.project().tracks.find(track => track.role === 'chords').generatorSettings,
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
  for (const track of tracks('melody')) assert.deepEqual(track.generatorSettings.melodyPatternOffsets, service.project().tracks.find(track => track.role === 'melody').generatorSettings.melodyPatternOffsets);
  const drums = [42, 60, 38].map(pitch => ({ ...tracks('drums')[0], generatorSettings: { ...service.project().tracks.find(track => track.role === 'drums').generatorSettings, pitch } }));
  const generated = core.rerollRoleSettings(42n, 'drums', { ...core.DEFAULT_TRACK_POLICIES.drums, maxSwing: 50, randomOffset: true, hitsMultiplier: '2' }, drums, [1,2,3]);
  assert.equal(generated[0].swingPercent, generated[1].swingPercent); assert.equal(generated[0].offset, generated[1].offset);
  assert.equal(generated[2].swingPercent, 50);
  assert.ok(generated.every(track => track.hitsPerPattern >= 1 && track.hitsPerPattern <= 32));
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
  const source = service.project().tracks.find(track => track.role === 'arpeggio').generatorSettings;
  for (const seed of [42n, -2147483648n, 9007199254740993n, 9223372036854775807n]) {
    assert.deepEqual(core.rerollArpeggioSettings(seed, core.DEFAULT_ARPEGGIO_POLICY, [source, source]),
      core.rerollArpeggioSettings(seed, core.DEFAULT_ARPEGGIO_POLICY, [source, source]));
  }
  service.updateSettings({ seed: '9007199254740993' });
  const before = service.exportProjectJson(); service.rerollArpeggioTracks(); const first = service.project().tracks.find(track => track.role === 'arpeggio').generatorSettings;
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.rerollArpeggioTracks(); assert.deepEqual(service.project().tracks.find(track => track.role === 'arpeggio').generatorSettings, first);
  service.rerollArpeggioTracks(); assert.equal(service.project().trackRerollCounts.arpeggio, 2);
  assert.notDeepEqual(service.project().tracks.find(track => track.role === 'arpeggio').generatorSettings, first);
  const saved = service.exportProjectJson();
  service.rerollArpeggioTracks(); const third = service.project().tracks.find(track => track.role === 'arpeggio').generatorSettings;
  assert.equal(service.importProjectJson(saved), true); service.rerollArpeggioTracks();
  assert.deepEqual(service.project().tracks.find(track => track.role === 'arpeggio').generatorSettings, third);
});

test('Arpeggio policy preserves opted-out fields, shared counts and no-op history', () => {
  const { service, load } = fixture();
  const core = load('core/music/track-generation.ts');
  const source = { ...service.project().tracks.find(track => track.role === 'arpeggio').generatorSettings, rhythm: 'custom', customPattern: Array(32).fill(1),
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
  const nextPattern = core.rerollArpeggioSettings(nextSeed, service.project().trackGenerationPolicies.arpeggio, [service.project().tracks.find(track => track.role === 'arpeggio').generatorSettings])[0].pattern;
  service.updatePartSettings({ kind: 'global-role', role: 'arpeggio' }, { pattern: nextPattern });
  const nextRotation = core.rerollArpeggioSettings(nextSeed, service.project().trackGenerationPolicies.arpeggio, [service.project().tracks.find(track => track.role === 'arpeggio').generatorSettings])[0].arpPatternRotate;
  service.updatePartSettings({ kind: 'global-role', role: 'arpeggio' }, { arpPatternRotate: nextRotation });
  service.updateSettings({ name: 'redo' }); service.undo();
  const before = service.exportProjectJson();
  assert.equal(service.rerollArpeggioTracks(), 'unchanged'); assert.equal(service.exportProjectJson(), before); assert.equal(service.canRedo(), true);
});

test('melody has explicit type zero and generated tracks, with stable existing role seeds and channels', () => {
  const { service, ui, workspace, phrase, load } = fixture();
  const { ARRANGED_PARTS, PART_TYPES } = load('core/music/part-role.ts');
  assert.deepEqual(ARRANGED_PARTS.map((role) => PART_TYPES[role]), [0, 1, 2, 3, 4]);
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
  saved.tracks = saved.tracks.filter((track) => track.role !== 'melody');
  for (const section of saved.arrangement) section.trackPresence.melody = [];
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
  const { DEFAULT_TRACKS } = load('core/project/project-defaults.ts');
  const { ARRANGED_PARTS } = load('core/music/part-role.ts');
  for (const role of ARRANGED_PARTS) {
    const track = service.project().tracks.find((candidate) => candidate.role === role);
    const defaults = DEFAULT_TRACKS.find(track => track.role === role).generatorSettings;
    service.updateTrackGeneratorSettings(track.id, { velocityMin: 1 });
    service.updateTrack(track.id, { mix: { volumePercent: 23, panPercent: 45 } });
    service.removeTrack(track.id);
    assert.equal(service.project().tracks.some((candidate) => candidate.role === role), false);
    const id = service.addTrack(role);
    const added = service.project().tracks.find((candidate) => candidate.id === id);
    assert.equal(added.role, role);
    assert.deepEqual(added.generatorSettings, defaults);
    assert.deepEqual(added.mix, DEFAULT_TRACKS.find(track => track.role === role).mix);
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
