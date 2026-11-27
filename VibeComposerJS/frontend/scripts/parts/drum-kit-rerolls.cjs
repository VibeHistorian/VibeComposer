const assert = require('node:assert/strict');
const { createHash } = require('node:crypto');
const test = require('node:test');
const { Midi } = require('@tonejs/midi');
const { fixture, projectData } = require('../support/project-fixture.cjs');
const { assertStyleSignatures, drums, drumsFixture, hasAccents, seededKitProject } = require('../support/drum-kit-reroll-fixture.cjs');

test('Kit is the default and Legacy is a saved application preference', () => {
  const { load, uiPreferences } = fixture();
  const { decodeUiPreferences } = load('core/ui/ui-preferences.ts');
  const { UiPreferencesService } = load('core/ui/ui-preferences.service.ts');
  assert.equal(uiPreferences.drumRerollMode(), 'kit');
  assert.equal(decodeUiPreferences({ ...uiPreferences.preferences(), drumRerollMode: undefined }).drumRerollMode, 'kit');
  assert.equal(decodeUiPreferences({ ...uiPreferences.preferences(), drumRerollMode: 'unknown' }), null);
  uiPreferences.setDrumRerollMode('legacy');
  assert.equal(new UiPreferencesService().drumRerollMode(), 'legacy');
});

test('Any and older policies replay the original Kit draws for global, local and instrument-only rerolls', () => {
  const { load } = fixture();
  const { rerollDrumKit } = load('core/project/project-drum-reroll.ts');
  const { DEFAULT_TRACK_POLICIES } = load('core/music/track-generation.ts');
  const { DEFAULT_INSTRUMENT_POLICIES } = load('core/music/instrument-generation.ts');
  const project = seededKitProject(load);
  const ids = drums(project).map(track => track.id);
  const scopes = [{}, { sectionId: project.arrangement[0].id }, { instrumentsOnly: true, forceInstruments: true }];
  const plans = policy => [0n, 42n, 9007199254740993n, -9223372036854775808n].flatMap(seed => scopes.map(scope =>
    rerollDrumKit(project, ids, seed, policy, DEFAULT_INSTRUMENT_POLICIES.drums, scope)));
  const original = plans(DEFAULT_TRACK_POLICIES.drums);
  assert.deepEqual(plans({ ...DEFAULT_TRACK_POLICIES.drums, rerollStyle: 'any' }), original);
  // Captured before adding style, covering pitches, full patterns and velocities, and auto pan.
  assert.equal(createHash('sha256').update(JSON.stringify(original)).digest('hex'),
    '3641e900963913dbfc30dbc7cada119e6840ac8a2257bebd5a45928034d6dbc8');
});

test('representative factory styles favor audible signature hits while seeded rerolls still vary the kit',
  t => assertStyleSignatures(t, ['deep-house', 'reggae', 'swing-jazz']));

test('Kit style selection is historical project policy and Legacy ignores it', () => {
  const { service, uiPreferences } = drumsFixture();
  const original = service.project();
  const unstyled = service.exportProjectJson();
  assert.equal(service.updateTrackGenerationPolicy('drums', { rerollStyle: 'unknown' }), 'invalid');
  assert.equal(service.updateTrackGenerationPolicy('bass', { rerollStyle: 'trap' }), 'invalid');
  assert.equal(service.updateTrackGenerationPolicy('drums', { rerollStyle: 'trap' }), 'changed');
  assert.equal(service.project().tracks, original.tracks);
  assert.equal(service.project().trackRerollCounts, original.trackRerollCounts);
  const styled = service.exportProjectJson();
  service.undo();
  assert.equal(service.exportProjectJson(), unstyled);
  service.redo();
  assert.equal(service.exportProjectJson(), styled);
  assert.equal(service.importProjectJson(styled), true);
  assert.equal(service.project().trackGenerationPolicies.drums.rerollStyle, 'trap');
  assert.equal(service.rerollRoleTracks('drums'), 'changed');
  const generated = service.exportProjectJson();
  assert.equal(service.importProjectJson(styled), true);
  assert.equal(service.rerollRoleTracks('drums'), 'changed');
  assert.deepEqual(projectData(service.exportProjectJson()), projectData(generated));

  uiPreferences.setDrumRerollMode('legacy');
  assert.equal(service.importProjectJson(styled), true);
  assert.equal(service.rerollRoleTracks('drums'), 'changed');
  const legacy = drums(service.project());
  assert.equal(service.importProjectJson(unstyled), true);
  assert.equal(service.rerollRoleTracks('drums'), 'changed');
  assert.deepEqual(drums(service.project()), legacy);
});

test('a styled section reroll fits its section and retains authored swing and notes', () => {
  const { service, phrase, load } = fixture();
  const project = seededKitProject(load);
  const [kick, manual, ...remaining] = drums(project);
  const section = project.arrangement[0];
  for (const track of remaining) section.trackPresence[track.id] = false;
  section.context = { swingPercent: 60, beatDurationMultiplier: .5 };
  section.trackPartOverrides = { [kick.id]: { swingPercent: 50 } };
  section.trackPhrases = { [manual.id]: [{ id: 'rim', midi: 37, startBeat: .5, durationBeats: .12, velocity: 75 }] };
  assert.equal(service.importProjectJson(JSON.stringify(project)), true);
  assert.equal(service.updateTrackGenerationPolicy('drums', { rerollStyle: 'deep-house' }), 'changed');
  const before = service.project();
  const target = { kind: 'section-track', sectionId: section.id, trackId: kick.id };
  assert.equal(service.changeOverviewTracks(target, 'reroll'), 'changed');
  const after = service.project();
  assert.equal(after.tracks, before.tracks);
  assert.equal(after.arrangement[1], before.arrangement[1]);
  assert.deepEqual(after.arrangement[0].trackPhrases, before.arrangement[0].trackPhrases);
  assert.equal(after.arrangement[0].trackPartOverrides[kick.id].swingPercent, 50);
  assert.equal(after.arrangement[0].trackPartOverrides[kick.id].pitch, undefined);
  const hits = phrase.resolvedTrackPhrase(after, drums(after)[0]);
  assert.ok(hasAccents([{ pitch: kick.generatorSettings.pitch, hits }], [36], [0, 1, 2, 3]));
});

test('Kit respects track, instrument, parameter and pan locks, manual notes, scoped overrides and undo', () => {
  const { service, phrase } = drumsFixture();
  const [whole, instrument, parameter, pan, manual, sectionTrack] = drums(service.project()).map(track => track.id);
  const get = id => service.project().tracks.find(track => track.id === id);
  const section = service.project().arrangement[0];
  service.setTrackRerollLock(whole, true);
  service.updateTrackGeneratorSettings(instrument, { pitch: 82 });
  service.setTrackInstrumentLock(instrument, true);
  service.updateTrackGeneratorSettings(parameter, { velocityMin: 110, velocityMax: 118, swingPercent: 58 });
  service.setParameterLocks({ kind: 'global-track', trackId: parameter }, ['velocityMin', 'swingPercent'], true);
  service.updateTrack(pan, { mix: { panPercent: -37 } });
  service.updateTrackPhrase(manual, [{ id: 'manual-kick', midi: 35, startBeat: 0, durationBeats: .3, velocity: 100 }]);
  service.updateTrackGeneratorSettings(sectionTrack, { velocityMax: 127 });
  service.updatePartSettings({ kind: 'section-track', sectionId: section.id, trackId: sectionTrack },
    { velocityMin: 117, pauseChance: 16 });
  assert.equal(service.project().arrangement[0].trackPartOverrides[sectionTrack].pauseChance, 16);
  const before = service.exportProjectJson(), frozen = get(whole), edited = get(manual);
  const arrangement = service.project().arrangement;
  assert.equal(service.rerollRoleTracks('drums'), 'changed');
  const after = service.exportProjectJson();
  assert.equal(get(whole), frozen);
  assert.equal(get(instrument).generatorSettings.pitch, 82);
  assert.equal(get(parameter).generatorSettings.velocityMin, 110);
  assert.equal(get(parameter).generatorSettings.swingPercent, 58);
  assert.ok(get(sectionTrack).generatorSettings.velocityMax >= 117, 'global candidate fits an inherited section velocity bound');
  const constrainedNotes = phrase.phraseForTrack(service.project(), get(parameter));
  assert.ok(constrainedNotes.length && constrainedNotes.every(note => note.velocity >= 110));
  assert.equal(get(pan).mix.panPercent, -37);
  assert.equal(get(manual), edited);
  assert.equal(service.project().arrangement, arrangement);
  service.undo();
  assert.equal(service.exportProjectJson(), before);
  service.redo();
  assert.equal(service.exportProjectJson(), after);
  assert.equal(service.importProjectJson(before), true);
  assert.equal(service.rerollRoleTracks('drums'), 'changed');
  assert.deepEqual(projectData(service.exportProjectJson()), projectData(after));
  const bases = service.project().tracks;
  assert.equal(service.changeOverviewTracks({ kind: 'section-role', role: 'drums', sectionId: section.id }, 'reroll'), 'changed');
  assert.equal(service.project().tracks, bases);
  const local = service.project().arrangement[0].trackPartOverrides;
  assert.equal(local[whole], undefined);
  assert.equal(local[manual], undefined);
  assert.equal(local[parameter]?.pitch, undefined);
  assert.ok(!Object.hasOwn(local[parameter] ?? {}, 'velocityMin') || local[parameter].velocityMin === 110);
});

test('Kit uses pool, width, local scope and instrument-only permissions independently', () => {
  const { service, midi, load } = drumsFixture();
  const { INSTRUMENT_POOLS } = load('core/music/instrument-generation.ts');
  service.updateInstrumentPolicy('drums', { pool: 'DRUM_HAND' });
  service.updateTrackGenerationPolicy('drums', { stereoWidth: 0, density: 70, ring: 90, experiment: 60, layering: 65 });
  for (const key of ['density', 'stereoWidth', 'experiment', 'layering', 'ring']) {
    for (const value of [-1, 101, 22.5, '50']) assert.equal(service.updateTrackGenerationPolicy('drums', { [key]: value }), 'invalid');
  }
  assert.equal(service.rerollRoleTracks('drums'), 'changed');
  assert.ok(drums(service.project()).every(track => INSTRUMENT_POOLS.DRUM_HAND.includes(track.generatorSettings.pitch) && track.mix.panPercent === 0));
  const saved = service.exportProjectJson();
  assert.equal(service.importProjectJson(saved), true);
  const before = drums(service.project());
  service.updateTrackGenerationPolicy('drums', { rerollStyle: 'trance' });
  service.updateInstrumentPolicy('drums', { pool: 'DRUM_ACOUSTIC', randomize: false });
  assert.equal(service.rerollRoleInstruments('drums'), 'changed');
  const changed = drums(service.project());
  changed.forEach((track, index) => {
    const { pitch, ...settings } = track.generatorSettings;
    const { pitch: previous, ...original } = before[index].generatorSettings;
    assert.deepEqual(settings, original);
    assert.equal(track.mix, before[index].mix);
    assert.ok(INSTRUMENT_POOLS.DRUM_ACOUSTIC.includes(pitch));
  });
  service.updateTrackGenerationPolicy('drums', { stereoWidth: 100, patterns: false, fills: false, maxSwing: 0 });
  assert.equal(service.rerollRoleTracks('drums'), 'changed');
  assert.deepEqual(drums(service.project()).map(track => track.generatorSettings.pitch), changed.map(track => track.generatorSettings.pitch));
  assert.ok(drums(service.project()).some(track => track.mix.panPercent !== 0));
  const output = new Midi(midi.generateCompositionMidi(service.project()));
  const pan = output.tracks.filter(track => track.channel === 9).flatMap(track => track.controlChanges[10] ?? []);
  assert.deepEqual(pan.map(cc => Math.round(cc.value * 127)), [64]);
});
