const assert = require('node:assert/strict');
const test = require('node:test');
const { loadMusic } = require('./music-loader.cjs');
const { generateMelody } = loadMusic('melody-generator.ts');
const { generateHarmony } = loadMusic('contextual-harmony.ts');
const { SCALE_MODES } = loadMusic('scales.ts');
const { mapChordNotes } = loadMusic('section-context.ts');

test('section-track swing overrides whole-section swing while other tracks inherit it', () => {
  const { DEFAULT_PROJECT } = loadMusic('../project/project-defaults.ts');
  const { layOutTrackPhrase } = loadMusic('phrase.ts');
  for (const original of DEFAULT_PROJECT.tracks.filter(track => track.role !== 'bass')) {
    const track = { ...original, generatorSettings: { ...original.generatorSettings,
      ...(original.role === 'melody' ? {} : { rhythm: 'full', hitsPerPattern: 8 }),
      pauseChance: 0, swingPercent: 55 } };
    const sibling = { ...track, id: `${track.id}-sibling` };
    const section = { ...DEFAULT_PROJECT.arrangement[0], measures: 4,
      trackPresence: { [track.id]: true, [sibling.id]: true }, context: { swingPercent: 70 } };
    const project = { ...DEFAULT_PROJECT, tracks: [track, sibling], arrangement: [section] };
    const inherited = layOutTrackPhrase(project, track);
    assert.ok(inherited.length > 0, track.role);
    for (const swingPercent of [0, 50, 60]) {
      const local = { ...project, arrangement: [{ ...section,
        trackPartOverrides: { [track.id]: { swingPercent } } }] };
      const expected = { ...project, arrangement: [{ ...section, context: { swingPercent } }] };
      const actual = layOutTrackPhrase(local, track);
      assert.deepEqual(actual, layOutTrackPhrase(expected, track), `${track.role}: local swing ${swingPercent}`);
      assert.notDeepEqual(actual, inherited, `${track.role}: local swing changes timing`);
      assert.deepEqual(layOutTrackPhrase(local, sibling), layOutTrackPhrase(project, sibling),
        `${track.role}: sibling keeps whole-section swing`);
    }
    const unrelated = { ...project, arrangement: [{ ...section,
      trackPartOverrides: { [track.id]: { pauseChance: 0 } } }] };
    assert.deepEqual(layOutTrackPhrase(unrelated, track), inherited,
      `${track.role}: unrelated override still inherits whole-section swing`);
    const noSectionSwing = { ...project, arrangement: [{ ...section, context: {} }] };
    assert.notDeepEqual(inherited, layOutTrackPhrase(noSectionSwing, track),
      `${track.role}: whole-section swing still overrides track base`);
  }
});

test('contextual melody consumes independent track ending, style and form', () => {
  const phrasing = { tension: 50, ending: 'CLOSED', style: 'JAZZ', form: 'SENTENCE' };
  const global = { contextualMelody: true };
  const args = [42n, 'C', 'major', [1, 5, 6, 1]];
  const track = generateMelody(...args, { algorithm: 'block', ...phrasing }, 0, undefined, global);
  const equivalent = generateMelody(...args, { algorithm: 'block', tension: 50 }, 0, undefined,
    { ...global, ending: 'CLOSED', style: 'JAZZ', form: 'SENTENCE' });
  assert.deepEqual(track, equivalent);
  const overridden = generateMelody(...args, { algorithm: 'block', ...phrasing }, 0, undefined,
    { ...global, ending: 'OPEN', style: 'MODAL', form: 'CONTINUOUS' });
  assert.deepEqual(overridden, track, 'track choices take precedence over project defaults');
  assert.equal(track.at(-1).midi % 12, 0, 'closed ending reaches tonic');
});

test('contextual results reuse musical inputs and isolate callers while invalidating changed settings', () => {
  const { JavaRandom } = loadMusic('java-random.ts');
  const { generateContextualMelody } = loadMusic('contextual-melody.ts');
  const next = JavaRandom.prototype.next;
  let draws = 0;
  JavaRandom.prototype.next = function (...args) { draws++; return next.apply(this, args); };
  try {
    const args = [9876543210101n, 'C', 'major', [1, 5, 6, 4], { algorithm: 'block' }, 0,
      { contextualMelody: true }];
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
    generateContextualMelody(...args.slice(0, 4), { ...args[4], rangeMax: 68 }, ...args.slice(5));
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
    // Rebaselined for corrected scale collections and the m9 third/seventh.
    sha256: 'b9b7dc4abe67945834211e29deade52b1e12bfda7cb4fe912a4a667713d2bc85', failures: 0,
  });
  assert.deepEqual(contextualDigest(120, i => ({
    syncopation: [0, 38, 100][i % 3], rhythmicReuse: [0, 67][i % 2],
    articulationContrast: [0, 44, 100][Math.floor(i / 3) % 3], velocityContrast: [0, 58, 100][Math.floor(i / 9) % 3],
  })), {
    // Captured with the original 0–100 controls.
    sha256: '27831313354dae3acad88570c44fa11a6581f42cf81bd2f2a6b0e5e280ac4f3a', failures: 0,
  });
});

test('contextual rhythm controls are neutral at zero and leave authored durations and block melody alone', () => {
  const generate = (settings, global) => {
    let pattern, skeleton;
    const notes = generateMelody(42n, 'C', 'natural-minor', [1, 6, 3, 7], { algorithm: 'block', ...settings },
      0, value => { pattern = value; }, global, undefined, undefined, value => { skeleton = value; });
    return { notes, pattern, skeleton };
  };
  assert.deepEqual(generate({ syncopation: 0, rhythmicReuse: 0, entryPlacement: 0, rhythmicGrouping: 0 }), generate({}));
  const active = { syncopation: 87, rhythmicReuse: 93, entryPlacement: 100, rhythmicGrouping: 100 };
  const authored = { rhythm: 'durations', customDurations: [[-0.5, 0.5, 1, -1, 1], ...Array.from({ length: 9 }, () => [])] };
  assert.deepEqual(generate({ ...authored, ...active }), generate(authored));
  assert.deepEqual(generate(active, { contextualMelody: false }), generate({}, { contextualMelody: false }));
});

test('entry placement delays more phrase starts while respecting rhythmic reuse and short closing phrases', () => {
  const strengths = [0, 50, 100], delayed = strengths.map(() => 0);
  for (let seed = 1n; seed <= 32n; seed++) {
    for (const [index, entryPlacement] of strengths.entries()) {
      const notes = generateMelody(seed, 'C', 'major', [1, 5, 6, 1], {
        algorithm: 'block', entryPlacement, rhythmicGrouping: 55, rhythmicReuse: 75, melodyPatternOffsets: [1], ending: 'CLOSED',
      }, 0, undefined, { phraseBars: 1 });
      for (const start of [0, 4, 8, 12]) {
        const first = notes.find(note => note.startBeat >= start && note.startBeat < start + 4);
        assert.ok([0, 0.5, 1.5].includes(first.startBeat - start));
        if (first.startBeat > start) delayed[index]++;
      }
      assert.equal(notes.at(-1).midi % 12, 0);
    }
  }
  assert.ok(delayed[0] === 0 && delayed[1] > 0 && delayed[1] < delayed[2] && delayed[2] === 128);
  const short = generateMelody(42n, 'C', 'major', [1], {
    algorithm: 'block', entryPlacement: 100, rhythmicGrouping: 100, ending: 'CLOSED',
  }, 0, undefined, {}, undefined, { roots: [], voicings: [], durations: [0.5] });
  assert.ok(short.length && short[0].startBeat === 0 && short.at(-1).midi % 12 === 0);
});

test('syncopation and rhythmic reuse shape seeded rhythm while preserving other controls', () => {
  const sample = (settings) => {
    const results = { attacks: 0, offbeats: 0, anticipations: 0, coreDifference: 0, tailDifference: 0 };
    for (let seed = 1n; seed <= 32n; seed++) {
      const notes = generateMelody(seed, 'C', 'natural-minor', [1, 1, 4, 5, 1, 6, 3, 7], {
        algorithm: 'block', pauseChance: 18, melodyPatternOffsets: [1], ...settings,
      }, 0, undefined, { phraseBars: 2 });
      results.attacks += notes.length;
      for (const note of notes) {
        if (note.startBeat % 1 === 0) continue;
        results.offbeats++;
        if (note.startBeat + note.durationBeats > Math.ceil(note.startBeat)) results.anticipations++;
      }
      for (const [start, end, key] of [[0, 7, 'coreDifference'], [7, 8, 'tailDifference']]) {
        const first = new Set(notes.filter(note => note.startBeat >= start && note.startBeat < end).map(note => note.startBeat));
        const second = new Set(notes.filter(note => note.startBeat >= 8 + start && note.startBeat < 8 + end).map(note => note.startBeat - 8));
        results[key] += first.symmetricDifference(second).size;
      }
    }
    return results;
  };
  const neutral = sample({});
  const syncGrounded = sample({ syncopation: -50 }), syncMostGrounded = sample({ syncopation: -100 });
  assert.equal(syncGrounded.attacks, neutral.attacks);
  assert.equal(syncMostGrounded.attacks, neutral.attacks);
  assert.ok(syncMostGrounded.offbeats < syncGrounded.offbeats && syncGrounded.offbeats < neutral.offbeats);
  const syncMiddle = sample({ syncopation: 50 }), syncHigh = sample({ syncopation: 100 });
  assert.equal(syncMiddle.attacks, neutral.attacks);
  assert.equal(syncHigh.attacks, neutral.attacks);
  assert.ok(syncMiddle.offbeats > neutral.offbeats && syncHigh.offbeats > syncMiddle.offbeats);
  assert.ok(syncMiddle.anticipations > neutral.anticipations && syncHigh.anticipations > syncMiddle.anticipations);

  const reuseMiddle = sample({ rhythmicReuse: 50 }), reuseHigh = sample({ rhythmicReuse: 100 });
  assert.ok(reuseMiddle.coreDifference < neutral.coreDifference * 0.8);
  assert.ok(reuseHigh.coreDifference < reuseMiddle.coreDifference * 0.5);
  assert.ok(reuseHigh.tailDifference > 0, 'recurring words retain ending variation');

  const active = { syncopation: 85, rhythmicReuse: 90 };
  const combined = sample(active);
  assert.ok(combined.offbeats > reuseHigh.offbeats && combined.coreDifference < neutral.coreDifference * 0.5);
  const slow = sample({ ...active, speed: -40 }), fast = sample({ ...active, speed: 95 });
  assert.ok(fast.attacks > slow.attacks * 1.5, 'Speed still changes density');
  assert.ok(sample({ ...active, pauseChance: 45 }).attacks < combined.attacks * 0.85, 'Pause still removes attacks');
  assert.ok(sample({ ...active, melodyPatternOffsets: [1, 2, 3, 4] }).coreDifference > combined.coreDifference * 2, 'Words still controls which rhythms recur');
  assert.ok(sample({ ...active, form: 'QUESTION_ANSWER' }).tailDifference < combined.tailDifference * 0.75, 'Form still shapes phrase endings');
});

test('rhythm shaping yields to the loop constraint when its rhythm cannot resolve', () => {
  const seed = 172n;
  const harmony = generateHarmony(seed, 8, 'C', 'lydian', { generation: 'MODAL' });
  const notes = generateMelody(seed, 'C', 'lydian', harmony.degrees, {
    algorithm: 'block', speed: 100, pauseChance: 70, melodyPatternOffsets: [1, 1, 2, 1],
    form: 'CONTINUOUS', ending: 'LOOP', syncopation: 100, rhythmicReuse: 100,
  }, 0, undefined, { phraseBars: 2 }, harmony.chords);
  assert.ok(notes.length > 1);
  assert.ok(Math.abs(notes.at(-1).midi - notes[0].midi) <= 10);
});

test('pitch reuse retains intervals across recurring words without owning rhythm or forcing pitches', () => {
  let neutralError = 0, mediumError = 0, strongError = 0;
  const intervalError = notes => notes.slice(1, 8).reduce((sum, note, index) =>
    sum + Math.abs(note.midi - notes[index].midi - (notes[index + 9].midi - notes[index + 8].midi)), 0);
  for (let seed = 1n; seed <= 24n; seed++) {
    const render = overrides => generateMelody(seed, 'C', 'major', [1, 5, 4, 1], {
      algorithm: 'block', melodyPatternOffsets: [1], rhythm: 'durations',
      customDurations: [[1, 1, 1, 1], ...Array.from({ length: 9 }, () => [])], ...overrides,
    });
    const original = render({}), medium = render({ pitchReuse: 50 }), strong = render({ pitchReuse: 100 });
    assert.deepEqual(render({ pitchReuse: 0 }), original);
    assert.deepEqual(render({ melodyPatternOffsets: [1, 2], pitchReuse: 100 }), render({ melodyPatternOffsets: [1, 2] }));
    for (const result of [medium, strong]) {
      const performance = notes => notes.map(({ startBeat, durationBeats, velocity }) => [startBeat, durationBeats, velocity]);
      assert.deepEqual(performance(result), performance(original));
      assert.ok(Math.abs(result.at(-1).midi - result[0].midi) <= 10, 'preserves loop closure');
    }
    neutralError += intervalError(original);
    mediumError += intervalError(medium);
    strongError += intervalError(strong);
  }
  assert.ok(mediumError < neutralError * 0.6 && strongError < mediumError);
});

test('contextual articulation and velocity contrast shape performance independently across seeds and settings', () => {
  const measures = [], reversedMeasures = [];
  for (let index = 0; index < 32; index++) {
    const seed = BigInt(index + 1), scale = ['major', 'natural-minor', 'dorian', 'lydian'][index % 4];
    const durations = index % 2 === 0 ? [4, 4, 4, 4] : [2, 3, 0.5, 5];
    const global = { phraseBars: 1 + index % 2, beatsPerBar: index % 2 === 0 ? 4 : 3 };
    const settings = {
      algorithm: 'block', form: index % 2 === 0 ? 'QUESTION_ANSWER' : 'SENTENCE',
      speed: [-30, 40, 90][index % 3], pauseChance: index % 3 * 12, syncopation: index % 2 * 75,
      rhythmicReuse: 85, melodyPatternOffsets: [1, 1, 2], splitChance: index % 2 * 50,
      accents: index % 2 * 100, velocityMin: 60 + index % 3 * 10, velocityMax: 110 + index % 3 * 3,
      swingPercent: [35, 50, 70][index % 3], noteLengthMultiplier: [45, 85, 125, 180][index % 4],
    };
    const render = (overrides = {}) => {
      let pattern, skeleton;
      const notes = generateMelody(seed, 'C', scale, [1, 5, 6, 4], { ...settings, ...overrides }, 0,
        value => { pattern = value; }, global, undefined, { roots: [], voicings: [], durations }, value => { skeleton = value; });
      return { notes: mapChordNotes(notes, durations), pattern, skeleton };
    };
    const base = render();
    assert.deepEqual(render({ articulationContrast: 0, velocityContrast: 0 }), base);
    const middle = render({ articulationContrast: 50, velocityContrast: 50 });
    const full = render({ articulationContrast: 100, velocityContrast: 100 });
    const inverted = render({ articulationContrast: -100, velocityContrast: -100 });
    const partiallyInverted = render({ articulationContrast: -50, velocityContrast: -50 });
    const gateOnly = render({ articulationContrast: 100 });
    const velocityOnly = render({ velocityContrast: 100 });
    const legacyAccent = render({ velocityContrast: 100, accents: 100 - settings.accents });
    const shorter = render({ articulationContrast: 100, noteLengthMultiplier: 25 });
    const flat = render({ velocityContrast: 100, velocityMin: 91, velocityMax: 91 });
    const { pattern, skeleton } = base;
    const identity = ({ id, midi, startBeat }) => [id, midi, startBeat];
    for (const shaped of [middle, full, inverted, partiallyInverted, gateOnly, velocityOnly, shorter, flat]) {
      assert.deepEqual(shaped.pattern, pattern);
      assert.deepEqual(shaped.skeleton, skeleton);
      assert.deepEqual(shaped.notes.map(identity), base.notes.map(identity));
    }
    const ends = durations.map((_, i) => durations.slice(0, i + 1).reduce((sum, beats) => sum + beats, 0));
    for (const [i, note] of full.notes.entries()) {
      const original = base.notes[i], next = full.notes[i + 1]?.startBeat ?? Infinity;
      const difference = note.durationBeats - original.durationBeats;
      const inverse = inverted.notes[i], inverseDifference = inverse.durationBeats - original.durationBeats;
      measures.push(difference);
      reversedMeasures.push(inverseDifference);
      for (const shaped of [note, inverse]) {
        assert.ok(shaped.durationBeats > 0 && shaped.startBeat + shaped.durationBeats <= ends.find(end => end > shaped.startBeat) + 1e-9);
        if (original.startBeat + original.durationBeats <= next + 1e-9) {
          assert.ok(shaped.startBeat + shaped.durationBeats <= next + 1e-9, 'articulation introduces no new overlap');
        }
        assert.ok(Number.isInteger(shaped.velocity) && shaped.velocity >= settings.velocityMin && shaped.velocity <= settings.velocityMax);
      }
      assert.ok(difference * inverseDifference <= 1e-9, 'negative articulation reverses the gate emphasis');
      assert.ok(Math.abs(partiallyInverted.notes[i].durationBeats - original.durationBeats) <= Math.abs(inverseDifference) + 1e-9);
      assert.ok(Math.abs(partiallyInverted.notes[i].velocity - original.velocity) <= Math.abs(inverse.velocity - original.velocity));
      assert.ok(Math.abs(note.velocity + inverse.velocity - settings.velocityMin - settings.velocityMax) <= 1);
      assert.ok(Math.abs(middle.notes[i].durationBeats - original.durationBeats) <= Math.abs(difference) + 1e-9);
      assert.ok(shorter.notes[i].durationBeats <= note.durationBeats + 1e-9, 'Note Length still sets the base gate');
      assert.ok(Math.abs(middle.notes[i].velocity - original.velocity) <= Math.abs(note.velocity - original.velocity));
      assert.equal(gateOnly.notes[i].velocity, original.velocity);
      assert.equal(velocityOnly.notes[i].durationBeats, original.durationBeats);
      assert.equal(legacyAccent.notes[i].velocity, note.velocity, 'full contrast replaces the saved arrival boost');
      assert.equal(flat.notes[i].velocity, 91);
    }
  }
  assert.ok(measures.filter(delta => delta > 0.01).length > 50, 'contrast lengthens landmarks');
  assert.ok(measures.filter(delta => delta < -0.01).length > 50, 'contrast shortens supporting notes');
  assert.ok(reversedMeasures.filter(delta => delta > 0.01).length > 50, 'negative contrast lengthens supporting notes');
  assert.ok(reversedMeasures.filter(delta => delta < -0.01).length > 50, 'negative contrast clips landmarks');
});

test('velocity contrast emphasizes offbeat entries in authored rhythms and legacy melody still uses Accent', () => {
  const authored = { algorithm: 'block', rhythm: 'durations', tension: 0, velocityMin: 60, velocityMax: 120,
    customDurations: [[-0.5, 0.25, -0.25, 0.25, 0.25, 0.5, 1, -1], ...Array.from({ length: 9 }, () => [])] };
  for (let seed = 1n; seed <= 16n; seed++) {
    const render = (settings, global) => generateMelody(seed, 'C', 'major', [1, 4, 5, 1], settings, 0, undefined, global);
    const notes = render({ ...authored, velocityContrast: 100, articulationContrast: 100 });
    assert.equal(notes.find(note => note.startBeat === 0.5).velocity, 102, 'offbeat entry after a rest');
    assert.equal(notes.find(note => note.startBeat === 1.25).velocity, 75, 'supporting offbeat after an attack');
    const inverted = render({ ...authored, velocityContrast: -100 });
    assert.equal(inverted.find(note => note.startBeat === 0.5).velocity, 78, 'offbeat entry recedes');
    assert.equal(inverted.find(note => note.startBeat === 1.25).velocity, 105, 'supporting offbeat comes forward');
    const legacy = { contextualMelody: false };
    const original = render(authored, legacy);
    assert.deepEqual(render({ ...authored, velocityContrast: 100, articulationContrast: 100 }, legacy), original);
  }
  const legacy = { contextualMelody: false };
  const renderAccent = accents => generateMelody(2n, 'C', 'major', [1, 4, 5, 1], { algorithm: 'block', accents }, 0, undefined, legacy);
  assert.notDeepEqual(renderAccent(0), renderAccent(100));
});

test('contextual defaults and seeded ranges use the lower octave', () => {
  const { DEFAULT_MELODY_TRACK } = loadMusic('melody-global-settings.ts');
  const { contextualRange } = loadMusic('contextual-randomizer.ts');
  assert.equal(DEFAULT_MELODY_TRACK.rangeMin, 48);
  assert.equal(DEFAULT_MELODY_TRACK.rangeMax, 72);
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
  const notes = generateMelody(42n,'C','major',degrees,{ algorithm: 'block', speed: 30, tension: 0 },0,undefined,
    { contextualMelody: true, ending: 'CLOSED', phraseBars: 1, beatsPerBar: 3 },undefined,
    { roots: [], voicings: [], durations });
  const mapped = mapChordNotes(notes,durations);
  assert.ok(mapped.length);
  assert.equal(mapped.at(-1).midi % 12,0);
  for (const note of mapped) {
    assert.ok(note.startBeat >= 0 && note.startBeat < 10.5);
    assert.ok(note.durationBeats > 0 && note.startBeat + note.durationBeats <= 10.5);
  }
});


test('manual scale intervals resolve dyads across roots and modes and feed both melody paths', () => {
  const { scaleIntervalChord, SCALE_INTERVALS, chordPitches, validProgressionChords } = loadMusic('harmony.ts');
  const base = { root: 0, spice: 'm', inversion: 2 };
  assert.deepEqual(SCALE_INTERVALS.map(d => scaleIntervalChord(base, d, 'C', 'major').intervals[1]), [2,4,5,7,9,11]);
  assert.deepEqual(SCALE_INTERVALS.map(d => scaleIntervalChord(base, d, 'C', 'natural-minor').intervals[1]), [2,3,5,7,8,10]);
  assert.deepEqual(scaleIntervalChord({ ...base, root: 11 }, 2, 'C', 'major').intervals, [0,1]);
  assert.deepEqual(scaleIntervalChord({ ...base, root: 2 }, 3, 'C', 'major').intervals, [0,3]);
  for (const scale of SCALE_MODES) for (let root = 0; root < 12; root++) for (const degree of SCALE_INTERVALS) {
    const chord = scaleIntervalChord({ ...base, root }, degree, 'C', scale);
    assert.ok(validProgressionChords([chord], 1));
    const pitches = chordPitches(chord, 60);
    assert.equal(pitches.length, 2);
    assert.ok(pitches[1] > pitches[0] && pitches[1] - pitches[0] < 12);
  }
  for (const degree of SCALE_INTERVALS) for (const contextualMelody of [false, true]) {
    const custom = Array.from({ length: 4 }, () => scaleIntervalChord(base, degree, 'C', 'major'));
    const { generateChordPart } = loadMusic('chord-generator.ts');
    const hits = generateChordPart(42n, 'C', 'major', [1,1,1,1], { noteLengthPercent: 100, voicing: 'close', rhythm: 'full', hitsPerPattern: 8 }, custom);
    assert.ok(hits.length);
    assert.ok(hits.every(hit => hit.pitches.length === 2));
    const notes = generateMelody(42n, 'C', 'major', [1,1,1,1], { algorithm: 'block' }, 0, undefined,
      { contextualMelody }, custom);
    assert.ok(notes.length);
    assert.ok(notes.every(n => Number.isFinite(n.midi)));
  }
});
