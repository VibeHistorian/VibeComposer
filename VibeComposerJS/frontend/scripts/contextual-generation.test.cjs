const assert = require('node:assert/strict');
const test = require('node:test');
const { loadMusic } = require('./music-loader.cjs');
const { generateMelody } = loadMusic('melody-generator.ts');
const { generateHarmony } = loadMusic('contextual-harmony.ts');
const { SCALE_MODES } = loadMusic('scales.ts');
const { mapChordNotes } = loadMusic('section-context.ts');

test('bounded contextual search retains reachable loop endings across modes and harmony strategies', () => {
  for (const scale of SCALE_MODES) for (let seed = 1; seed <= 6; seed++) for (const ending of ['OPEN','CLOSED','LOOP']) {
    const harmony = generateHarmony(BigInt(seed),4,'C',scale,{ generation: ['FUNCTIONAL','FIFTHS','POP_LOOP','MODAL'][seed % 4] });
    const notes = generateMelody(BigInt(seed),'C',scale,harmony.degrees,{ algorithm: 'block' },0,undefined,
      { contextualMelody: true, ending },harmony.chords);
    assert.ok(notes.length, `${scale}, seed ${seed}, ${ending}`);
    if (ending === 'LOOP') assert.ok(Math.abs(notes.at(-1).midi - notes[0].midi) <= 10, `${scale}, seed ${seed}`);
  }
});

test('contextual phrase timing maps variable harmonic durations exactly once', () => {
  const durations = [2,3,0.5,5], degrees = [1,4,5,1];
  const notes = generateMelody(42n,'C','major',degrees,{ algorithm: 'block', speed: 30 },0,undefined,
    { contextualMelody: true, tension: 0, ending: 'CLOSED', phraseBars: 1, beatsPerBar: 3 },undefined,
    { roots: [], voicings: [], durations });
  const mapped = mapChordNotes(notes,durations);
  assert.ok(mapped.length);
  assert.equal(mapped.at(-1).midi % 12,0);
  for (const note of mapped) {
    assert.ok(note.startBeat >= 0 && note.startBeat < 10.5);
    assert.ok(note.durationBeats > 0 && note.startBeat + note.durationBeats <= 10.5);
  }
});
