const assert = require('node:assert/strict');
const { createHash } = require('node:crypto');
const test = require('node:test');
const { Midi } = require('@tonejs/midi');
const { fixture, projectData } = require('../support/project-fixture.cjs');

const drums = project => project.tracks.filter(track => track.role === 'drums');
const isKick = midi => midi === 35 || midi === 36;
const attacksNear = (a, b) => Math.abs(a.startBeat - b.startBeat) < .02;

function drumsFixture(count = 6) {
  const app = fixture();
  const { service } = app;
  service.updateSettings({ seed: '9007199254740993', tempoBpm: 112 });
  assert.equal(service.setRoleTrackCount('drums', count), 'changed');
  return app;
}

function seededKitProject(load) {
  const { DEFAULT_PROJECT } = load('core/project/project-defaults.ts');
  const drum = drums(DEFAULT_PROJECT)[0];
  const kit = [36, 37, 42, 53, 44, 60].map((pitch, index) => ({ ...drum,
    id: `track-drums-${index + 1}`, name: `D${index + 1}`, generatorSettings: { ...drum.generatorSettings, pitch },
  }));
  const tracks = [...DEFAULT_PROJECT.tracks.filter(track => track.role !== 'drums'), ...kit];
  const trackPresence = Object.fromEntries(tracks.map(track => [track.id, true]));
  return { ...DEFAULT_PROJECT, seed: '9007199254740993', tempoBpm: 112, tracks, arrangement: [
    { id: 'kit-verse', type: 'VERSE1', measures: 4, trackPresence },
    { id: 'kit-bridge', type: 'BREAKDOWN', measures: 2,
      trackPresence: { ...trackPresence, [kit[4].id]: false, [kit[5].id]: false } },
  ] };
}

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

function hasAccents(layers, pitches, beats) {
  return layers.some(layer => pitches.includes(layer.pitch) && beats.every(beat => layer.hits.some(hit =>
    Math.abs(hit.startBeat - beat) < .04 && hit.velocity >= 45)));
}

test('every factory style favors audible signature hits while seeded rerolls still vary the kit', async (t) => {
  const { service, phrase, load } = fixture();
  const { DRUM_REROLL_STYLE_OPTIONS } = load('core/music/drum-reroll-styles.ts');
  const { BUILTIN_TRACK_PRESETS } = load('core/project/builtin-track-presets.ts');
  const factoryStyles = BUILTIN_TRACK_PRESETS.filter(preset => preset.role === 'drums').map(preset => preset.name.split(' · ')[0]);
  const source = JSON.stringify(seededKitProject(load));
  const kicks = [35, 36], snares = [37, 38, 39, 40];
  const quarters = [0, 1, 2, 3], offbeats = [.5, 1.5, 2.5, 3.5];
  const eighths = [0, .5, 1, 1.5, 2, 2.5, 3, 3.5];
  const closedHats = [42, 44];
  const signatures = {
    'deep-house': layers => hasAccents(layers, kicks, quarters) && hasAccents(layers, [42, 44, 69, 82], offbeats),
    trap: layers => hasAccents(layers, snares, [2]) && !hasAccents(layers, snares, [1]) && !hasAccents(layers, snares, [3]),
    reggae: layers => hasAccents(layers, kicks, [2]) && hasAccents(layers, [37], [2]) && !hasAccents(layers, kicks, [0]),
    'swing-jazz': layers => hasAccents(layers, [51, 53, 59], [1, 5 / 3, 3, 11 / 3]),
    dnb: layers => hasAccents(layers, kicks, [0, 2.5]) && hasAccents(layers, snares, [1, 3]),
    funk: layers => hasAccents(layers, kicks, [0, 1.5, 2, 3.5]) && hasAccents(layers, snares, [1.12, 3.12]),
    trance: layers => hasAccents(layers, kicks, quarters) && hasAccents(layers, [46], offbeats),
    samba: layers => hasAccents(layers, [37], [.5, 1.25, 2.5, 3.25]) && hasAccents(layers, [70, 82], [0, .5, 1, 1.5, 2, 2.5, 3, 3.5]),
    techno: layers => hasAccents(layers, kicks, quarters) && hasAccents(layers, closedHats, [.75, 2.75]),
    synthwave: layers => hasAccents(layers, kicks, [0, 2, 2.5]) && hasAccents(layers, [40], [1, 3]),
    disco: layers => hasAccents(layers, kicks, quarters) && hasAccents(layers, closedHats, [.25, 1.25, 2.25, 3.25]),
    'boom-bap': layers => hasAccents(layers, kicks, [0, 2, 2.75]) && hasAccents(layers, snares, [1.1, 3.1]),
    'lo-fi-hip-hop': layers => hasAccents(layers, kicks, [0, 2, 3.5]) && hasAccents(layers, [37], [1.08, 3.08]),
    'uk-garage': layers => hasAccents(layers, kicks, [0, 1.5, 2.5]) && hasAccents(layers, [38, 39], [1.12, 3.12])
      && hasAccents(layers, closedHats, [1.75, 3.75]),
    dubstep: layers => hasAccents(layers, kicks, [0, 3.5]) && hasAccents(layers, [38, 40], [2])
      && !hasAccents(layers, snares, [1]) && !hasAccents(layers, snares, [3]),
    dub: layers => hasAccents(layers, kicks, [2]) && !hasAccents(layers, kicks, [0])
      && hasAccents(layers, [37], [2]) && hasAccents(layers, [44, 82], offbeats),
    afrobeat: layers => hasAccents(layers, kicks, [0, 1.5, 2, 3.5]) && hasAccents(layers, [42, 70, 82], [.75, 1.25, 2.75, 3.25]),
    'bossa-nova': layers => hasAccents(layers, [37], [.75, 2.5, 3.25]) && hasAccents(layers, [44, 69, 82], eighths),
    'blues-shuffle': layers => hasAccents(layers, snares, [1, 3]) && hasAccents(layers, [42, 44, 51, 53], [2 / 3, 5 / 3, 8 / 3, 11 / 3]),
    'indie-rock': layers => hasAccents(layers, kicks, [0, 2, 2.5]) && hasAccents(layers, [38, 37], [1, 3])
      && hasAccents(layers, closedHats, eighths),
    'pop-punk': layers => hasAccents(layers, kicks, [0, 1.5, 2, 3.5]) && hasAccents(layers, [38, 40], [1, 3])
      && hasAccents(layers, closedHats, eighths),
    'acoustic-folk': layers => hasAccents(layers, kicks, [0, 2]) && hasAccents(layers, [37], [1, 3])
      && hasAccents(layers, [54, 70, 82], quarters),
    ambient: layers => hasAccents(layers, kicks, [0]) && !hasAccents(layers, kicks, [2])
      && hasAccents(layers, [37, 38], [3]) && hasAccents(layers, [42, 44, 82], [1.5, 3.5]),
    'cinematic-pulse': layers => hasAccents(layers, kicks, [0, 2, 3.5]) && hasAccents(layers, [38, 40], [3])
      && hasAccents(layers, [41, 47, 50], [1.5, 3.5]),
  };
  const namedOptions = DRUM_REROLL_STYLE_OPTIONS.filter(option => option.value !== 'any');
  assert.deepEqual(namedOptions.map(({ value }) => value), Object.keys(signatures));
  assert.deepEqual(namedOptions.map(({ label }) => label === 'DnB' ? 'Drum and Bass' : label).sort(), factoryStyles.sort());
  const rolls = 24;
  const sample = style => {
    assert.equal(service.importProjectJson(source), true);
    assert.equal(service.updateTrackGenerationPolicy('drums', { rerollStyle: style }), 'changed');
    const outcomes = [];
    for (let roll = 0; roll < rolls; roll++) {
      assert.equal(service.rerollRoleTracks('drums'), 'changed');
      const project = service.project();
      outcomes.push(drums(project).map(track => ({
        pitch: track.generatorSettings.pitch,
        hits: phrase.resolvedTrackPhrase(project, track).filter(hit => hit.startBeat < 4),
      })));
    }
    return outcomes;
  };
  const neutral = sample('any');
  for (const [style, matches] of Object.entries(signatures)) await t.test(style, t => {
    const outcomes = sample(style);
    const styledMatches = outcomes.filter(matches).length;
    const neutralMatches = neutral.filter(matches).length;
    const variants = new Set(outcomes.map(layers => JSON.stringify(layers.map(layer => [layer.pitch,
      layer.hits.map(hit => hit.startBeat.toFixed(2))]).sort(([a], [b]) => a - b))));
    t.diagnostic(`${style}: ${styledMatches}/${rolls} signature matches, Any ${neutralMatches}/${rolls}, ${variants.size} distinct kits`);
    assert.ok(styledMatches >= 20 && styledMatches >= neutralMatches + 12, style);
    assert.ok(variants.size >= 16, style);
  });
});

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
  assert.ok(hasAccents([{ pitch: kick.generatorSettings.pitch, hits }], [36], [0, .5, 1, 1.5]));
});

test('seeded Kit rerolls reduce loud low-end collisions and duplicate sounds while retaining groove and kit variety', (t) => {
  const { service, phrase, uiPreferences } = drumsFixture();
  const input = projectData(service.exportProjectJson());
  for (const section of input.arrangement) section.trackPresence.drums = [1, 2, 3, 4, 5, 6];
  const before = JSON.stringify(input);
  const compare = (mode, density) => {
    uiPreferences.setDrumRerollMode(mode);
    assert.equal(service.importProjectJson(before), true);
    service.updateTrackGenerationPolicy('drums', { density });
    const rolls = 128, rhythms = new Set(), sounds = new Set();
    let collided = 0, loudLowPairs = 0, repeats = 0, attacks = 0;
    const salientLow = note => [35, 36, 41, 43, 45].includes(note.midi) && note.velocity >= 70;
    for (let roll = 0; roll < rolls; roll++) {
      assert.equal(service.rerollRoleTracks('drums'), 'changed');
      const project = service.project(), tracks = drums(project);
      const pitches = tracks.map(track => track.generatorSettings.pitch);
      const notes = tracks.map(track => phrase.phraseForTrack(project, track));
      assert.ok(notes.every(layer => layer.length));
      const kickLayers = notes.filter(layer => isKick(layer[0].midi));
      collided += Number(kickLayers.some((layer, j) => kickLayers.slice(j + 1)
        .some(other => layer.some(a => other.some(b => attacksNear(a, b))))));
      for (const [index, layer] of notes.entries()) for (const other of notes.slice(index + 1)) {
        for (const a of layer.filter(salientLow)) loudLowPairs += other.filter(b => salientLow(b) && attacksNear(a, b)).length;
      }
      repeats += Number(new Set(pitches).size !== pitches.length);
      attacks += notes.reduce((sum, layer) => sum + layer.length, 0) / project.progression.length;
      rhythms.add(notes.map(layer => layer.map(note => note.startBeat.toFixed(3)).join(',')).sort().join('|'));
      sounds.add([...pitches].sort().join(','));
      if (mode === 'kit') for (const [index, pitch] of pitches.entries()) {
        if (isKick(pitch)) assert.equal(tracks[index].mix.panPercent, 0);
      }
    }
    return { collided, loudLowPairs, repeats, hitsPerBar: attacks / rolls, rhythms: rhythms.size, sounds: sounds.size };
  };
  const legacy = compare('legacy', 45);
  const kit = compare('kit', 70);
  t.diagnostic(JSON.stringify({ legacy, kit }));
  assert.ok(Math.abs(kit.hitsPerBar - legacy.hitsPerBar) < 3, JSON.stringify({ legacy, kit }));
  assert.equal(kit.collided, 0);
  assert.ok(kit.loudLowPairs < legacy.loudLowPairs / 10);
  assert.ok(kit.repeats < legacy.repeats / 4);
  assert.ok(kit.rhythms >= 112 && kit.sounds >= 64);
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
