const { createHash } = require('node:crypto');
const { loadMusic } = require('./music-loader.cjs');
const { generateMelody } = loadMusic('melody-generator.ts');
const { layOutTrackPhrase } = loadMusic('phrase.ts');
const { DEFAULT_PROJECT } = loadMusic('../project/project-defaults.ts');

function measure(name, run) {
  for (let index = 0; index < 20; index++) run(index);
  const hash = createHash('sha256');
  const start = performance.now();
  for (let index = 0; index < 200; index++) hash.update(JSON.stringify(run(index)));
  return { name, milliseconds: Math.round(performance.now() - start), sha256: hash.digest('hex') };
}

console.log(JSON.stringify([
  measure('melody with pattern and skeleton', index => {
    let pattern, skeleton;
    const notes = generateMelody(BigInt(index + 1), 'C', 'major', [1, 5, 6, 4], DEFAULT_PROJECT.tracks.find(track => track.role === 'melody').generatorSettings,
      0, value => { pattern = value; }, { newBlocksChance: index % 3 * 50 }, undefined, undefined,
      value => { skeleton = value; });
    return { notes, pattern, skeleton };
  }),
  measure('score, overview and playback layouts', index => {
    const project = { ...DEFAULT_PROJECT, seed: String(index + 1) };
    const track = project.tracks.find(track => track.role === 'melody');
    return [layOutTrackPhrase(project, track, true), layOutTrackPhrase(project, track), layOutTrackPhrase(project, track)];
  }),
], null, 2));
