const assert = require('node:assert/strict');
const { fixture } = require('./project-fixture.cjs');

const drums = project => project.tracks.filter(track => track.role === 'drums');

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

function hasAccents(layers, pitches, beats) {
  return layers.some(layer => pitches.includes(layer.pitch) && beats.every(beat => layer.hits.some(hit =>
    Math.abs(hit.startBeat - beat) < .04 && hit.velocity >= 45)));
}

async function assertStyleSignatures(t, styles) {
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
  for (const style of styles ?? Object.keys(signatures)) await t.test(style, t => {
    const matches = signatures[style];
    const outcomes = sample(style);
    const styledMatches = outcomes.filter(matches).length;
    const neutralMatches = neutral.filter(matches).length;
    const variants = new Set(outcomes.map(layers => JSON.stringify(layers.map(layer => [layer.pitch,
      layer.hits.map(hit => hit.startBeat.toFixed(2))]).sort(([a], [b]) => a - b))));
    t.diagnostic(`${style}: ${styledMatches}/${rolls} signature matches, Any ${neutralMatches}/${rolls}, ${variants.size} distinct kits`);
    assert.ok(styledMatches >= 20 && styledMatches >= neutralMatches + 12, style);
    assert.ok(variants.size >= 16, style);
  });
}

module.exports = { assertStyleSignatures, drums, drumsFixture, hasAccents, seededKitProject };
