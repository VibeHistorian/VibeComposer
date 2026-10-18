const assert = require('node:assert/strict');
const test = require('node:test');
const { loadMusic } = require('./music-loader.cjs');
const { generateMelody } = loadMusic('melody-generator.ts');
const { generateHarmony } = loadMusic('contextual-harmony.ts');
const { SCALE_MODES } = loadMusic('scales.ts');
const { mapChordNotes } = loadMusic('section-context.ts');

test('contextual search preserves exact notes, patterns and skeletons across the optimization corpus', () => {
  const { contextualDigest } = require('./benchmark-contextual-melody.cjs');
  assert.deepEqual(contextualDigest(), {
    sha256: 'c56b72fa80f8ad10d54defe44e6bf986d7e6978ca3e5c5e58fdc0a64b8b54498', failures: 0,
  });
});

test('contextual defaults and seeded ranges use the lower octave', () => {
  const { DEFAULT_MELODY_GLOBAL } = loadMusic('melody-global-settings.ts');
  const { contextualRange } = loadMusic('contextual-randomizer.ts');
  assert.equal(DEFAULT_MELODY_GLOBAL.rangeMin, 48);
  assert.equal(DEFAULT_MELODY_GLOBAL.rangeMax, 72);
  for (let seed = 0n; seed < 64n; seed++) {
    const range = contextualRange(seed);
    assert.ok(range.rangeMin >= 43 && range.rangeMax <= 76);
    assert.ok([12,14,16,19,24].includes(range.rangeMax - range.rangeMin));
  }
});

test('melody rerolls use balanced octaves and always give Melody #1 an unflipped ALL fill', () => {
  const { rerollRoleSettings, DEFAULT_TRACK_POLICIES } = loadMusic('track-generation.ts');
  const track = { role: 'melody', generatorSettings: { algorithm: 'block', chordSpanFill: 'ODD', fillFlip: true } };
  const secondaryFills = new Set();
  for (let seed = 0n; seed < 64n; seed++) {
    const settings = rerollRoleSettings(seed, 'melody', DEFAULT_TRACK_POLICIES.melody, [track,track,track], [1,2,3]);
    assert.deepEqual(settings.map(s => s.transpose), [12,0,-12]);
    assert.equal(settings[0].chordSpanFill, 'ALL');
    assert.ok(settings.every(s => !s.fillFlip));
    secondaryFills.add(settings[1].chordSpanFill);
  }
  assert.ok(secondaryFills.size > 1);
  const [secondary] = rerollRoleSettings(42n, 'melody', DEFAULT_TRACK_POLICIES.melody, [track], [3]);
  assert.equal(secondary.transpose, -12, 'stable track order survives locked or skipped siblings');
});

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
