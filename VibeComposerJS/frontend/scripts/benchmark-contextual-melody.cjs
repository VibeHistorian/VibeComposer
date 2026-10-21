const { createHash } = require('node:crypto');
const { loadMusic } = require('./music-loader.cjs');
const { generateMelody } = loadMusic('melody-generator.ts');
const { SCALE_MODES } = loadMusic('scales.ts');

function contextualCase(index) {
  const settings = { algorithm: 'block', speed: [-20, 30, 80][index % 3], pauseChance: index % 4 * 10,
    splitChance: index % 3 * 20,
    melodyPatternOffsets: [1, -1, 2, 1], swingPercent: index % 2 ? 66 : 50 };
  if (index % 7 === 0) Object.assign(settings, { rhythm: 'durations',
    customDurations: [[1, -0.5, 0.5, 1, -1], ...Array.from({ length: 9 }, () => [])] });
  const global = { contextualMelody: true, ending: ['OPEN', 'CLOSED', 'LOOP'][index % 3],
    form: ['STATEMENT_VARIATION', 'QUESTION_ANSWER', 'SENTENCE', 'CONTINUOUS'][index % 4],
    style: ['TONAL', 'MODAL', 'JAZZ'][Math.floor(index / 3) % 3], tension: [0, 35, 100][Math.floor(index / 9) % 3],
    targetDirection: ['ANY', 'ASC', 'DESC'][Math.floor(index / 4) % 3],
    newBlocksChance: index % 3 * 50, chromaticApproaches: index % 2 === 0,
    phraseBars: index % 2 + 1, beatsPerBar: index % 5 === 0 ? 3 : 4,
    rangeMin: index % 11 === 0 ? 60 : 48, rangeMax: index % 11 === 0 ? 64 : 72,
    blockTypeWeights: index % 13 === 0 ? [0, 0, 0, 0, 0, 0, 0] : [100, 70, 40, 60, 80, 30, 20] };
  let pattern, skeleton;
  try {
    const notes = generateMelody(BigInt(index + 1), index % 2 ? 'F♯' : 'C', SCALE_MODES[index % SCALE_MODES.length],
      [1, 5, 6, 4], settings, index % 3, value => { pattern = value; }, global, undefined,
      index % 5 === 0 ? { roots: [], voicings: [], durations: [2, 3, 0.5, 5] } : undefined,
      value => { skeleton = value; });
    return { notes, pattern, skeleton };
  } catch (error) { return { error: error.message }; }
}

function contextualDigest(count = 120) {
  const hash = createHash('sha256');
  let failures = 0;
  for (let index = 0; index < count; index++) {
    const result = contextualCase(index);
    if (result.error) failures++;
    hash.update(JSON.stringify(result));
  }
  return { sha256: hash.digest('hex'), failures };
}

if (require.main === module) {
  for (let index = 0; index < 10; index++) contextualCase(index);
  const samples = [];
  let result;
  for (let repeat = 0; repeat < 3; repeat++) {
    const start = performance.now();
    result = contextualDigest();
    samples.push(Math.round(performance.now() - start));
  }
  console.log(JSON.stringify({ cases: 120, milliseconds: samples, ...result }, null, 2));
}
module.exports = { contextualCase, contextualDigest };
