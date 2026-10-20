const assert = require('node:assert/strict');
const test = require('node:test');
const { loadMusic } = require('./music-loader.cjs');
const { generateMelody } = loadMusic('melody-generator.ts');
const { generateHarmony } = loadMusic('contextual-harmony.ts');
const { SCALE_MODES } = loadMusic('scales.ts');
const { mapChordNotes } = loadMusic('section-context.ts');

test('contextual results reuse musical inputs and isolate callers while invalidating changed settings', () => {
  const { JavaRandom } = loadMusic('java-random.ts');
  const { generateContextualMelody } = loadMusic('contextual-melody.ts');
  const next = JavaRandom.prototype.next;
  let draws = 0;
  JavaRandom.prototype.next = function (...args) { draws++; return next.apply(this, args); };
  try {
    const args = [9876543210101n, 'C', 'major', [1, 5, 6, 4], { algorithm: 'block' }, 0,
      { contextualMelody: true, targetDirection: 'ANY' }];
    const first = generateContextualMelody(...args);
    const expected = structuredClone(first);
    assert.ok(draws > 0);
    first.notes[0].midi = -1;
    first.pattern[0].midi = -1;
    first.skeleton[0][0].midi = -1;
    draws = 0;
    const equivalent = args.map((arg, index) => index === 0 ? arg : structuredClone(arg));
    assert.deepEqual(generateContextualMelody(...equivalent), expected);
    assert.equal(draws, 0, 'equal inputs in fresh project objects reuse the planned melody');
    generateContextualMelody(...args.slice(0, 6), { ...args[6], rangeMax: 68 });
    assert.ok(draws > 0, 'changed musical inputs rerun search');
    // Eviction must bound retention without changing the result when an old input is reused.
    for (let offset = 1; offset <= 65; offset++) generateContextualMelody(...args.slice(0, 5), offset, args[6]);
    draws = 0;
    assert.deepEqual(generateContextualMelody(...args), expected);
    assert.ok(draws > 0, 'older entries are evicted');
  } finally { JavaRandom.prototype.next = next; }
});

test('section presence toggles reuse contextual search for melody and dependent tracks', () => {
  const { DEFAULT_PROJECT } = loadMusic('../project/project-defaults.ts');
  const { layOutTrackPhrase, phraseForTrack } = loadMusic('phrase.ts');
  const contextual = loadMusic('contextual-melody.ts');
  const { JavaRandom } = loadMusic('java-random.ts');
  const next = JavaRandom.prototype.next, generate = contextual.generateContextualMelody;
  let draws = 0, calls = 0, contextualDraws = 0;
  JavaRandom.prototype.next = function (...args) { draws++; return next.apply(this, args); };
  contextual.generateContextualMelody = (...args) => {
    calls++;
    const before = draws, result = generate(...args);
    contextualDraws += draws - before;
    return result;
  };
  try {
    const project = { ...DEFAULT_PROJECT, seed: '814927310', melodyGlobal: { contextualMelody: true },
      tracks: DEFAULT_PROJECT.tracks.map(track => track.role === 'arpeggio' || track.role === 'drums'
        ? { ...track, generatorSettings: { ...track.generatorSettings, rhythm: 'melody' } } : track) };
    const render = project => project.tracks.map(track => ({
      count: phraseForTrack(project, track).length, notes: layOutTrackPhrase(project, track, true),
    }));
    const original = render(project);
    assert.ok(contextualDraws > 0);
    calls = contextualDraws = 0;
    for (const present of [false, true, false, true]) {
      const changed = { ...project, arrangement: project.arrangement.map((section, index) => index === 0
        ? { ...section, trackPresence: Object.fromEntries(project.tracks.map(track => [track.id, present])) } : section) };
      render(changed);
    }
    assert.ok(calls > 0, 'new layouts request contextual melodies');
    assert.equal(contextualDraws, 0, 'presence changes never rerun contextual search');
    const restored = { ...project, arrangement: project.arrangement.map(section => ({ ...section })) };
    assert.deepEqual(render(restored), original);
    assert.equal(contextualDraws, 0);
  } finally {
    JavaRandom.prototype.next = next;
    contextual.generateContextualMelody = generate;
  }
});

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
