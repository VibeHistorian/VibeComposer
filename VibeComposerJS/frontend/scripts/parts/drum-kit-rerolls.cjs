const assert = require('node:assert/strict');
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
