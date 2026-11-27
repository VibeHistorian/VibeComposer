const assert = require('node:assert/strict');
const test = require('node:test');
const { projectData } = require('../support/project-fixture.cjs');
const { assertStyleSignatures, drums, drumsFixture } = require('../support/drum-kit-reroll-fixture.cjs');

const isKick = midi => midi === 35 || midi === 36;
const attacksNear = (a, b) => Math.abs(a.startBeat - b.startBeat) < .02;

test('every factory style favors audible signature hits while seeded rerolls still vary the kit', t => assertStyleSignatures(t));

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
