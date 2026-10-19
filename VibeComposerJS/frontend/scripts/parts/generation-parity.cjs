const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const { Midi } = require('@tonejs/midi');
const { fixture } = require('../support/project-fixture.cjs');
const { assertRejectedProjectImport } = require('../support/project-import-assertions.cjs');

test('arpeggio register matches all production Java pitches in the saved bug configuration and exported MIDI', () => {
  const { service, phrase, midi } = fixture();
  const reference = JSON.parse(fs.readFileSync(path.join(__dirname, '..','fixtures/arpeggio-saved-config.java.json'),'utf8'));
  assert.equal(service.importProjectJson(JSON.stringify(reference.project)),true);
  const project = service.project(), exported = new Midi(midi.generateCompositionMidi(project));
  let compared = 0;
  for (const track of project.tracks.filter(track => track.role === 'arpeggio')) {
    const expected = reference.expectedArpeggioPitches[track.id];
    assert.deepEqual(phrase.layOutTrackPhrase(project,track).map(note => note.midi),expected,track.id);
    assert.deepEqual(exported.tracks.find(item => item.name === track.name).notes.map(note => note.midi),expected,`${track.id} MIDI`);
    compared += expected.length;
  }
  assert.equal(compared,54);
});

test('contextual melodies match production Java pitches in the saved bug configuration and exported MIDI', () => {
  const { service, phrase, midi } = fixture();
  const reference = JSON.parse(fs.readFileSync(path.join(__dirname, '..','fixtures/arpeggio-saved-config.java.json'),'utf8'));
  assert.equal(service.importProjectJson(JSON.stringify(reference.project)),true);
  const project = service.project(), exported = new Midi(midi.generateCompositionMidi(project));
  for (const track of project.tracks.filter(track => track.role === 'melody')) {
    const expected = reference.expectedMelodyPitches[track.id];
    assert.deepEqual(phrase.layOutTrackPhrase(project,track).map(note => note.midi),expected,track.id);
    assert.deepEqual(exported.tracks.find(item => item.name === track.name)?.notes.map(note => note.midi) ?? [],expected,`${track.id} MIDI`);
  }
});

test('canonical and realized arpeggio harmony use the Java role register before track transpose', () => {
  const { load } = fixture();
  const { generateArpeggio } = load('core/music/arpeggio-generator.ts');
  const settings = { pattern: 'up', rate: 'eighth', octaves: 1, rhythm: 'full', hitsPerPattern: 4,
    exceptionChance: 0, pauseChance: 0, swingPercent: 50, useOctaveAdjustments: false };
  const harmony = { roots: [[60,64,67]], voicings: [[60,64,67]] };
  const generated = generateArpeggio(42n,'C','major',[1],settings,undefined,undefined,harmony);
  assert.deepEqual(generated.map(note => note.midi),[36,40,43,36]);
  assert.deepEqual(generateArpeggio(42n,'C','major',[1],settings,undefined,undefined,
    { ...harmony, soundingVoicings: [[60,64,67]] }),generated);
  const contour = generateArpeggio(42n,'C','major',[1],{ ...settings, arpContour: [0] },undefined,undefined,harmony);
  assert.deepEqual(contour.map(note => note.midi),[60,40,43,36],'contour attacks receive the same -24 Java role transpose');
});

test('random arpeggios repeat Java\'s eight-index vocabulary before shuffling long melody grids', () => {
  const { load } = fixture();
  const { arpeggioPattern } = load('core/music/arpeggio-pattern.ts');
  const { JavaRandom } = load('core/music/java-random.ts');
  // Captured from production ArpPhraseGenerator.generateArpMap with the debugger:
  // patternSeed -2129510774, part offset 30000, order offset 3, random stream +1.
  const pattern = arpeggioPattern('random',64,3,1,0,undefined,new JavaRandom(-2129480770));
  assert.deepEqual(pattern.slice(0,10),[7,7,2,7,0,1,2,5,3,6]);
  for (let index = 0; index < 8; index++) assert.equal(pattern.filter(value => value === index).length,8);
});

test('harmony strategies are seeded, distinct, and Random chooses a concrete strategy', () => {
  const { load, service } = fixture();
  const { generateHarmony, HARMONY_GENERATIONS } = load('core/music/contextual-harmony.ts');
  const { generateDiatonicProgression } = load('core/music/progression-generator.ts');
  const seed = 9223372036854775807n;
  assert.deepEqual(generateHarmony(seed, 4, 'C', 'major').degrees, generateDiatonicProgression(seed,4));
  const results = HARMONY_GENERATIONS.slice(0,5).map(({ value }) => generateHarmony(seed,8,'C','natural-minor',{ generation: value }));
  assert.ok(new Set(results.map(r => r.degrees.join(','))).size >= 4);
  for (const { value } of HARMONY_GENERATIONS) {
    const result = generateHarmony(seed,8,'C','natural-minor',{ generation: value });
    assert.deepEqual(result,generateHarmony(seed,8,'C','natural-minor',{ generation: value }));
    assert.equal(result.degrees.length,8);
    if (value === 'RANDOM') assert.ok(results.some(r => JSON.stringify(r) === JSON.stringify(result)));
  }
  const fifths = generateHarmony(123n,7,'C','major',{ generation: 'FIFTHS' }).degrees;
  assert.ok(fifths.slice(1).every((d,i) => d === (fifths[i] - 1 + 3) % 7 + 1));
  const fixed = { root: 1, spice: 'm', inversion: 1 };
  const locked = generateHarmony(123n,4,'C','major',{ generation: 'FUNCTIONAL' },{},[fixed,null,null,fixed]);
  assert.deepEqual(locked.chords[0],fixed); assert.deepEqual(locked.chords[3],fixed);
  service.updateHarmonySettings({ generation: 'FUNCTIONAL' });
  const before = service.exportProjectJson(); service.generateProgression();
  assert.ok(service.project().progressionChords); service.undo(); assert.equal(service.exportProjectJson(),before);
});

test('contextual melody respects sounding harmony, range, ending, and ignores obsolete repair settings', () => {
  const { load } = fixture();
  const { generateMelody } = load('core/music/melody-generator.ts');
  const { chordPitches, progressionChords } = load('core/music/harmony.ts');
  const settings = { algorithm: 'block', speed: 30, maxBlockChange: 7, blockJump: 1 };
  const globals = { contextualMelody: true, rangeMin: 60, rangeMax: 84, tension: 0, ending: 'CLOSED' };
  for (const key of ['C','D']) for (const scale of ['major','natural-minor','dorian']) {
    const progression = [1,4,5,1], chords = progressionChords(key,scale,progression);
    const notes = generateMelody(123n,key,scale,progression,settings,0,undefined,globals);
    assert.ok(notes.length > 0);
    assert.deepEqual(notes,generateMelody(123n,key,scale,progression,settings,0,undefined,globals));
    assert.equal(notes.at(-1).midi % 12, key === 'C' ? 0 : 2);
    notes.forEach((n,i) => {
      assert.ok(n.midi >= 60 + (key === 'C' ? 0 : 2) && n.midi <= 84 + (key === 'C' ? 0 : 2));
      assert.ok(n.durationBeats > 0 && n.startBeat + n.durationBeats <= 16);
      const classes = chordPitches(chords[Math.floor(n.startBeat/4)],0).map(p => p % 12);
      assert.ok(classes.includes(n.midi % 12));
      if (i) assert.ok(Math.abs(n.midi - notes[i-1].midi) <= 10);
    });
    const ignored = { ...globals, tonicNoteTarget: 100, chordNoteTarget: 100, modeNoteTarget: 100, replaceAvoidNotes: 2, arpySurprises: true };
    assert.deepEqual(notes,generateMelody(123n,key,scale,progression,{ ...settings, doubledRhythmChance: 100, noteExceptionChance: 100, maxNoteExceptions: 4, fillPauses: true },0,undefined,ignored));
  }
});

test('Java zero-origin modes retain exact degrees, target notes and modal chord pitches', () => {
  const { load } = fixture(), scales = load('core/music/scales.ts'), harmony = load('core/music/harmony.ts');
  const java = fs.readFileSync(path.join(__dirname, '..', '../../../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/MidiUtils.java'), 'utf8');
  const definitions = Object.fromEntries([...java.matchAll(/(\w+)_SCALE\s*=\s*\{([^}]+)\}/g)]
    .map(match => [match[1], match[2].split(',').map(Number)]));
  const names = { major:'MAJOR',dorian:'DORIAN',phrygian:'PHRYGIAN',lydian:'LYDIAN',mixolydian:'MIXOLYDIAN',
    'natural-minor':'AEOLIAN',locrian:'LOCRIAN',blues:'BLUES','harmonic-minor':'HARMONIC_MINOR',turkish:'TURKISH',
    indian:'INDIAN','harmonic-major':'HARMONIC_MAJOR',whiskey:'WHISKEY','double-harmonic':'DOUBLE_HARM_MINOR',
    'pentatonic-major':'PENTA_MAJOR','pentatonic-minor':'PENTA_MINOR','hungarian-minor':'HUNGARIAN_MINOR' };
  assert.equal(scales.SCALE_MODES.length,17);
  for (const [mode, name] of Object.entries(names)) {
    assert.deepEqual(scales.scaleNotes(mode), definitions[name], mode);
    const target = Number(java.match(new RegExp('\\w+\\(Scales\\.'+name+'_SCALE, (-?\\d+)\\)'))[1]);
    assert.equal(scales.modeTargetPitch(mode),target < 0 ? -1 : definitions.MAJOR[target],mode);
    for (let degree=0;degree<7;degree++) {
      const expected=[0,2,4].map(step=>60+definitions[name][(degree+step)%7]+12*Math.floor((degree+step)/7));
      const chord=harmony.progressionChords('C',mode,[degree+1])[0];
      assert.deepEqual(harmony.chordPitches(chord,60),expected.map(n=>n-12*Math.floor((expected[0]-60)/12)),mode+' chord '+degree);
    }
  }
  assert.deepEqual(harmony.getDiatonicChords('C','mixolydian').map(chord=>chord.symbol),['C','Dm','E°','F','Gm','Am','B♭']);
  for(const excluded of ['bborian','ebolian','abrygian','dbocrian','gbfs','MIXOLYDIAN','unknown','__proto__']) assert.equal(scales.isScaleMode(excluded),false);
});

test('All supported modes generate every role, export MIDI and round-trip section overrides and exact unusual chords', () => {
  const {service,load}=fixture(), scales=load('core/music/scales.ts'), phrase=load('core/music/phrase.ts');
  const harmony=load('core/music/harmony.ts'), midi=load('core/music/midi-export.ts');
  for(const scale of scales.SCALE_MODES) {
    service.updateSettings({scale});
    const id=service.project().arrangement[0].id;
    assert.notEqual(service.updateSectionContext(id,{scale}),'invalid');
    const project=service.project();
    for(const track of project.tracks) {
      const notes=phrase.generateTrackPhrase(project,track);
      assert.ok(notes.length,scale+' '+track.role);
      assert.ok(notes.every(note=>Number.isInteger(note.midi)&&note.midi>=0&&note.midi<=127&&Number.isFinite(note.startBeat)),scale);
    }
    const bytes=midi.generateCompositionMidi(project);
    assert.ok(bytes.length>0);
    const json=service.exportProjectJson();
    assert.equal(service.importProjectJson(json),true,scale);
    assert.equal(service.project().scale,scale);
    assert.equal(service.project().arrangement[0].context.scale,scale);
    assert.deepEqual(midi.generateCompositionMidi(service.project()),bytes,scale);
  }
  const unusual=harmony.progressionChords('C','indian',[1])[0];
  assert.deepEqual(unusual.intervals,[0,1,5]);
  const saved=JSON.parse(service.exportProjectJson()); saved.progressionChords=saved.progression.map(()=>unusual);
  assert.equal(service.importProjectJson(JSON.stringify(saved)),true);
  assert.deepEqual(service.project().progressionChords[0],unusual);
  saved.scale='bborian'; assertRejectedProjectImport(service, JSON.stringify(saved));
});

test('P6 extended Arpeggio directions match 1536 extracted production Java branches and following draws', () => {
  const { load } = fixture(), core = load('core/music/track-generation.ts');
  const { JavaRandom } = load('core/music/java-random.ts');
  const cases = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'fixtures/extended-creation.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  const names = { RANDOM:'random',UP:'up',DOWN:'down',UPDOWN:'up-down',DOWNUP:'down-up',FROG_U:'frog-up',FROG_D:'frog-down' };
  let random;
  assert.equal(cases.length,1536);
  for (const sample of cases) {
    if (sample.index === 0) random = new JavaRandom(BigInt(sample.seed));
    const result = core.chooseArpeggioDirection(random,sample.voices,2);
    assert.deepEqual(result,{pattern:names[sample.direction],arpPatternRotate:sample.rotation});
    assert.equal(random.nextLong().toString(),sample.next);
  }
});

test('P5e inference weighting and voicing flattening match production Java consumers', () => {
  const { load } = fixture();
  const { inferMelodyChordDegrees, INFERENCE_CHORD_NAMES } = load('core/music/melody-chord-inference.ts');
  const { flattenProgression } = load('core/music/harmony-voicing.ts');
  const data = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'fixtures/harmony-inference.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(data.weighting.length, 256); assert.equal(data.voicing.length, 192);
  for (const entry of data.weighting) assert.deepEqual(inferMelodyChordDegrees(entry.skeleton, entry.durations).map(degree => INFERENCE_CHORD_NAMES[degree - 1]), entry.names);
  for (const entry of data.voicing) {
    const before = JSON.stringify(entry);
    assert.deepEqual(flattenProgression(entry.chords, entry.roots, BigInt(entry.seed), entry.settings, entry.skipped), entry.output);
    assert.equal(JSON.stringify(entry), before, 'root and original voicing arrays remain independent');
  }
});

test('P5e base skeleton, alternate roots/voicings and complete swapped Melody phrases match Java', () => {
  const { load } = fixture();
  const { generateMelody } = load('core/music/melody-generator.ts');
  const { inferMelodyChordDegrees, INFERENCE_CHORD_NAMES } = load('core/music/melody-chord-inference.ts');
  const { baseGenerationHarmony, projectMelodyHarmony } = load('core/music/project-harmony.ts');
  const { DEFAULT_PROJECT } = load('core/project/project.model.ts');
  const data = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'fixtures/harmony-inference.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(data.phrases.length, 96);
  for (const entry of data.phrases) {
    const settings = { algorithm: 'block', speed: [-100,0,50,100][entry.sample % 4], chordNoteChoices: [0,2,2,4], melodyPatternOffsets: [1,2,1,3],
      patternFlexible: true, maxBlockChange: 7, blockJump: 1, doubledRhythmChance: entry.sample % 2 === 0 ? 0 : 40,
      maxNoteExceptions: 2, noteExceptionChance: 30, transpose: [0,5,7,-12][entry.sample % 4], splitChance: 0, leadChordsChance: 0, startNoteChance: 100,
      pauseChance: 0, fillPauses: false, velocityMin: 80, velocityMax: 105, swingPercent: 50 };
    const globalSettings = { patternEffect: entry.sample % 3, newBlocksChance: entry.sample % 2 === 0 ? 25 : 100 };
    const project = { ...DEFAULT_PROJECT, key: 'C', scale: 'major', seed: entry.seed, progression: [1,5,6,4], progressionChords: undefined,
      melodyGlobal: globalSettings, tracks: [{ ...DEFAULT_PROJECT.tracks.find(track => track.role === 'melody'), generatorSettings: settings }] };
    const base = baseGenerationHarmony(project);
    let skeleton;
    generateMelody(BigInt(entry.seed), 'C', 'major', project.progression, settings, 0, undefined, globalSettings, undefined, base, notes => { skeleton = notes; });
    assert.deepEqual(skeleton, entry.skeleton, 'skeleton ' + entry.seed + '/' + entry.sample);
    const names = inferMelodyChordDegrees(skeleton).map(degree => INFERENCE_CHORD_NAMES[degree - 1]);
    names[0] = 'C'; names[3] = 'F'; assert.equal(names.join(','), entry.alternate);
    const inferred = projectMelodyHarmony(project);
    assert.equal(inferred.available, true);
    assert.deepEqual(inferred.harmony.roots, entry.roots);
    assert.deepEqual(inferred.harmony.voicings, entry.voicings);
    [0,100].forEach((offset,index) => {
      const notes = generateMelody(BigInt(entry.seed), 'C', 'major', inferred.harmony.degrees, settings, offset, undefined,
        globalSettings, undefined, inferred.harmony);
      assert.equal(notes.length, entry.notes[index].length);
      notes.forEach((note,i) => {
        const expected = entry.notes[index][i];
        assert.equal(note.midi, expected[0]); assert.ok(Math.abs(note.startBeat - expected[1]) < 1e-8);
        assert.ok(Math.abs(note.durationBeats - expected[2]) < 1e-8, `${entry.seed}/${entry.sample}/${offset}/${i}: duration ${note.durationBeats} vs ${expected[2]}`); assert.equal(note.velocity, expected[3]);
      });
    });
  }
});

test('P5e sounding harmony preserves chromatic Chordlets across keys/modes while canonical voicing keeps independent roots', () => {
  const { service, load, phrase, midi } = fixture();
  const { chordPitches, progressionChords, getPitchClass } = load('core/music/harmony.ts');
  const { baseGenerationHarmony, projectMelodyHarmony } = load('core/music/project-harmony.ts');
  service.setProgression([1,5,6,4]);
  service.updateHarmonySettings({ flattenVoicingChance:0 });
  const id = service.project().arrangement[0].id;
  service.project().tracks.forEach(track => service.setSectionTrackPresence(0,track.id,true));
  for (const key of ['C','D','F♯','A♭']) for (const scale of ['major','natural-minor']) {
    service.updateSettings({ key,scale });
    const custom = { root:(getPitchClass(key)+4)%12,spice:'maj7',inversion:1 };
    service.setProgressionChord(0,custom);
    const harmony = baseGenerationHarmony(service.project());
    const definitions = progressionChords(key,scale,service.project().progression,service.project().progressionChords);
    assert.deepEqual(harmony.soundingRoots,definitions.map(chord => chordPitches({ ...chord,inversion:0 },60)));
    assert.deepEqual(harmony.soundingVoicings,definitions.map(chord => chordPitches(chord,60)));
    const roots = harmony.soundingRoots;
    service.updateHarmonySettings({ flattenVoicingChance:100 });
    assert.deepEqual(baseGenerationHarmony(service.project()).soundingRoots,roots);
    assert.deepEqual(baseGenerationHarmony(service.project()).soundingVoicings[0],chordPitches(custom,60));
    const inference = projectMelodyHarmony(service.project());
    assert.deepEqual(inference.harmony.soundingRoots[0],roots[0]);
    assert.deepEqual(inference.harmony.soundingRoots[3],roots[3]);
    service.updateSectionHarmony(id,{ useMelodyHarmony:true,swapMelody:true });
    const exported = new Midi(midi.generateCompositionMidi(service.project()));
    for (const track of service.project().tracks) {
      const expected = phrase.layOutTrackPhrase(service.project(),track);
      const actual = exported.tracks.find(item => item.name===track.name).notes;
      assert.equal(expected.length,actual.length);
      expected.forEach((note,index) => assert.equal(actual[index].midi,note.midi));
    }
    service.updateHarmonySettings({ flattenVoicingChance:0 });
  }
});

test('P5 melody dependency grids match production onset buckets with rests and boundary offsets', () => {
  const { load } = fixture();
  const { melodyContext } = load('core/music/melody-context.ts');
  const cases = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'fixtures/melody-context.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(cases.length, 64);
  for (const entry of cases) assert.deepEqual(melodyContext(entry.notes, entry.chordCount).pitches, entry.pitches);
});

test('P5e Melody pattern flip matches 64 production Java onset maps and retains empty sources', () => {
  const { load } = fixture(), { melodyContext } = load('core/music/melody-context.ts');
  const cases = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'fixtures/melody-flip.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(cases.length, 64);
  for (const entry of cases) assert.deepEqual(melodyContext(entry.notes, entry.chordCount, true).pitches, entry.pitches);
  assert.deepEqual(melodyContext([], 1, true).pitches, [Array(16).fill(0)]);
  assert.deepEqual(melodyContext([{ midi: -1, startBeat: 0 }], 1, true).pitches, [Array(16).fill(1)]);
});

test('P5e tonal repairs protect chord starts, retain original counts and make best-effort corrections', () => {
  const { load } = fixture(), { applyMelodyTonalTargets } = load('core/music/melody-tonal-targets.ts');
  const run = (pitches, scale, tonic, mode, offset = 0) => {
    const notes = pitches.map(chord => chord.map(midi => ({ midi })));
    applyMelodyTonalTargets(notes, scale, tonic, mode, offset);
    return notes.map(chord => chord.map(note => note.midi));
  };
  assert.deepEqual(run([[62, 62, 71], [62, 62, 71]], 'major', 100, 0), [[62, 62, 72], [62, 62, 72]]);
  assert.deepEqual(run([[62, 62, 71]], 'major', 100, 0, 100), [[62, 62, 71]]);
  // Existing targets consume the deficit, so percentages are requests rather than guaranteed quotas.
  assert.deepEqual(run([[64, 60, 62, 64]], 'major', 0, 75), [[64, 60, 60, 64]]);
  // The original histogram still requires two repairs after tonic repair; the new tonic consumes one.
  assert.deepEqual(run([[64, 62, 71, 64]], 'major', 100, 50), [[64, 60, 72, 64]]);
  assert.deepEqual(run([[60, 71, 67, -1]], 'natural-minor', 0, 100), [[60, 69, 69, -1]]);
  assert.deepEqual(run([[], [60, -1]], 'major', 100, 100), [[], [60, -1]]);
});

test('P5e chord repairs retain original order, voicing ties, safe notes and the independent avoid stream', () => {
  const { load } = fixture();
  const { applyMelodyChordTargets, replaceMelodyAvoidNotes, melodyRepairChords } = load('core/music/melody-chord-targets.ts');
  const notes = pitches => pitches.map(chord => chord.map(midi => ({ midi })));
  const pitches = chords => chords.map(chord => chord.map(note => note.midi));
  const original = notes([[61, 62, 71], [65, 69, 71, 62, 65, -1]]);
  applyMelodyChordTargets(original, [[67, 60, 64]], 100);
  assert.deepEqual(pitches(original), [[60, 60, 67], [64, 67, 67, 60, 65, -1]], 'per-chord budget can leave the final deficit unmet');
  const ties = notes([[62, 62]]);
  applyMelodyChordTargets(ties, [[64, 60]], 100);
  assert.deepEqual(pitches(ties), [[64, 64]], 'nearest pitch is not circular; first voicing entry wins a tie');
  const safe = notes([[60, 61, 64, 67, -1, 65, 68]]);
  replaceMelodyAvoidNotes(safe, [[60, 64, 67]], 42, 1);
  assert.deepEqual(pitches(safe), [[60, 60, 64, 67, -1, 64, 60]]);
  const again = notes([[60, 61, 64, 67, -1, 65, 68]]);
  replaceMelodyAvoidNotes(again, [[60, 64, 67]], 42, 1);
  assert.deepEqual(pitches(again), pitches(safe));
  for (const key of ['A♭', 'F♯', 'C♯', 'E♭']) assert.deepEqual(
    melodyRepairChords(key, 'natural-minor', [1,2,3,4,5,6,7]).map(chord => chord.map(pitch => pitch % 12)),
    melodyRepairChords('C', 'major', [1,2,3,4,5,6,7]).map(chord => chord.map(pitch => pitch % 12)));
});

test('P5d Ghosts, melody rhythm and humanize match complete production Java drum phrases', () => {
  const { load } = fixture();
  const { generateDrumPart } = load('core/music/drum-generator.ts');
  const cases = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'fixtures/drum-advanced.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(cases.length, 192);
  for (const [index, entry] of cases.entries()) {
    const actual = generateDrumPart(BigInt(entry.seed), entry.barCount, entry.settings, entry.melody);
    assert.equal(actual.length, entry.notes.length, `case ${index} ${JSON.stringify(entry.settings)}`);
    actual.forEach((note, position) => {
      const expected = entry.notes[position];
      assert.equal(note.midi, expected[0], `case ${index} note ${position} pitch`);
      assert.equal(note.velocity, expected[3], `case ${index} note ${position} velocity`);
      assert.ok(Math.abs(note.startBeat - expected[1]) < 1e-9, `case ${index} note ${position} onset ${note.startBeat} vs ${expected[1]}`);
      assert.ok(Math.abs(note.durationBeats - expected[2]) < 1e-9, `case ${index} note ${position} duration ${note.durationBeats} vs ${expected[2]}`);
    });
  }
});

test('P5c Arpeggio direction, rotation, contour, octave and melody consumers match complete Java phrases', () => {
  const { load } = fixture();
  const { generateArpeggio } = load('core/music/arpeggio-generator.ts');
  const cases = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'fixtures/arpeggio-core.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(cases.length, 256);
  for (const [index, entry] of cases.entries()) {
    const actual = generateArpeggio(BigInt(entry.seed), entry.key, entry.scale, entry.progression, entry.settings, undefined, entry.melody);
    assert.equal(actual.length, entry.notes.length, `case ${index} ${JSON.stringify(entry.settings)}`);
    actual.forEach((note, position) => {
      const expected = entry.notes[position];
      assert.equal(note.midi, expected[0], `case ${index} note ${position} pitch`);
      assert.equal(note.velocity, expected[3], `case ${index} note ${position} velocity`);
      assert.ok(Math.abs(note.startBeat - expected[1]) < 1e-9, `case ${index} note ${position} onset ${note.startBeat} vs ${expected[1]}`);
      assert.ok(Math.abs(note.durationBeats - expected[2]) < 1e-9, `case ${index} note ${position} duration ${note.durationBeats} vs ${expected[2]}`);
    });
  }
});

test('Chordlet roots, spice and inversions match all 2244 production Java voicings', () => {
  const { load } = fixture(), harmony = load('core/music/harmony.ts');
  const cases = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'fixtures/chordlet-harmony.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(cases.length, 2244);
  for (const sample of cases) assert.deepEqual(harmony.chordPitches(sample, 60), sample.notes, JSON.stringify(sample));
  assert.equal(harmony.effectiveKey('C', -1), 'B');
  assert.equal(harmony.effectiveKey('F♯', 8), 'D');
  assert.equal(harmony.effectiveKey('B', -24), 'B');
});

test('Chordlet colors follow relative roots and spice overlap uses the actual notes in the active scale', () => {
  const { load } = fixture(), harmony = load('core/music/harmony.ts');
  const chord = (root, spice, inversion = 0) => ({ root, spice, inversion });
  assert.equal(harmony.chordRootHue(7, 'C'), 210);
  assert.equal(harmony.chordRootHue(9, 'D'), 210);
  assert.equal(harmony.chordRootHue(1, 'F♯'), 210);
  assert.deepEqual(harmony.chordKeyOverlap(chord(0, 'maj7'), 'C', 'major'), { matching: 4, total: 4, ratio: 1 });
  assert.deepEqual(harmony.chordKeyOverlap(chord(0, '7'), 'C', 'major'), { matching: 3, total: 4, ratio: .75 });
  assert.deepEqual(harmony.chordKeyOverlap(chord(0, 'm'), 'C', 'major'), { matching: 2, total: 3, ratio: 2/3 });
  assert.deepEqual(harmony.chordKeyOverlap(chord(6, ''), 'C', 'major'), { matching: 0, total: 3, ratio: 0 });
  assert.deepEqual(harmony.chordKeyOverlap(chord(4, 'm', -5), 'A', 'natural-minor'), { matching: 3, total: 3, ratio: 1 });
  assert.deepEqual(harmony.chordKeyOverlap(chord(4, 'maj7'), 'A', 'natural-minor'), { matching: 2, total: 4, ratio: .5 });
  for (const spice of harmony.CHORD_SPICES) {
    const original = harmony.chordKeyOverlap(chord(4, spice), 'A', 'natural-minor');
    assert.deepEqual(harmony.chordKeyOverlap(chord(11, spice, 5), 'E', 'natural-minor'), original, spice);
  }
});

test('chord joins, transitions and per-tone strums match 288 production Java phrases', () => {
  const { load } = fixture();
  const { generateChordPart } = load('core/music/chord-generator.ts');
  const cases = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'fixtures/chord-core.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(cases.length, 288);
  const sort = notes => notes.sort((a, b) => a[1] - b[1] || a[0] - b[0]);
  for (const [caseIndex, sample] of cases.entries()) {
    const hits = generateChordPart(BigInt(sample.seed), sample.key, sample.scale, sample.progression, sample.settings);
    const actual = sort(hits.flatMap(hit => hit.pitches.map((pitch, index) => [pitch,
      hit.startBeat + (hit.pitchOffsets?.[index] ?? 0), hit.pitchDurations?.[index] ?? hit.durationBeats, hit.velocity])));
    const expected = sort(sample.notes);
    assert.equal(actual.length, expected.length, `case ${caseIndex} count`);
    actual.forEach((note, index) => {
      const label = `case ${caseIndex}, note ${index}`;
      assert.equal(note[0], expected[index][0], `${label} pitch`);
      assert.ok(Math.abs(note[1] - expected[index][1]) < 1e-10, `${label} onset ${note[1]} vs ${expected[index][1]}`);
      assert.ok(Math.abs(note[2] - expected[index][2]) < 1e-10, `${label} duration ${note[2]} vs ${expected[index][2]}`);
      assert.equal(note[3], expected[index][3], `${label} velocity`);
    });
  }
});

test('Melody automatic targets match production Java for the supported diatonic target path', () => {
  const { load } = fixture();
  const { chooseMelodyTargets } = load('core/music/melody-targets.ts');
  const oracle = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'fixtures/melody-targets.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(oracle.length, 128);
  for (const sample of oracle) assert.deepEqual(chooseMelodyTargets(sample.seed, sample.progression), sample.targets);
});

test('P5e all target modes match 20736 production Java preparation and starting-note cases', () => {
  const { load } = fixture();
  const { chooseMelodyTargets, startingMelodyTarget } = load('core/music/melody-targets.ts');
  const { melodyRootChords } = load('core/music/melody-chord-targets.ts');
  const cases = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'fixtures/melody-target-modes.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(cases.length, 20736);
  let recovered = 0;
  for (const [index, entry] of cases.entries()) {
    const targets = chooseMelodyTargets(entry.seed, entry.progression, entry.globalSettings, entry.key, entry.scale, entry.customChords);
    assert.deepEqual(targets, entry.targets, `target case ${index}`);
    const roots = melodyRootChords(entry.key, entry.scale, entry.progression, entry.customChords);
    entry.starting.forEach((expected, chord) => {
      const actual = startingMelodyTarget(roots, targets, chord, entry.globalSettings.blockTargetMode);
      if (expected !== null) assert.equal(actual, expected, `start case ${index}, chord ${chord}`);
      else { recovered++; assert.ok(Number.isInteger(actual) && actual >= 0 && actual <= 74); }
    });
  }
  assert.ok(recovered > 0, 'oracle covers Java null-pitch recovery');
});

test('P5e target index boundaries retain Java octave rules and recover extreme valid lists', () => {
  const { service, phrase, load } = fixture();
  const { startingMelodyTarget, chooseMelodyTargets } = load('core/music/melody-targets.ts');
  assert.equal(startingMelodyTarget([[60,64,67]], [-3], 0, 0), 21, 'negative exact multiple uses Java extra lower octave');
  assert.equal(startingMelodyTarget([[60,64,67]], [-24], 0, 0), 2, 'invalid low index recovers to the nearest valid index toward zero');
  assert.equal(startingMelodyTarget([[60,64,67]], [24], 0, 0), 72, 'invalid high index recovers to the nearest valid index toward zero');
  assert.equal(startingMelodyTarget([[60,64,67], [67,71,74]], [0,2], 2, 1), 35, 'root-relative final next target wraps');
  assert.equal(startingMelodyTarget([[60,64,67], [67,71,74]], [0,2], 2, 2), 37, 'MIDI-relative final next target retains the last choice');
  for (const mode of [0,1,2]) {
    service.updateMelodyGlobal({ blockTargetMode: mode });
    service.updatePartSettings({ kind: 'global-track', trackId: 'track-melody-1' }, { chordNoteChoices: [-24,24] });
    const notes = phrase.phraseForTrack(service.project(), service.project().tracks[0]);
    assert.ok(notes.length > 0 && notes.every(note => Number.isInteger(note.midi) && note.midi >= 0 && note.midi <= 127));
  }
  assert.throws(() => chooseMelodyTargets(42, [1], {}, 'invalid'), RangeError);
  assert.throws(() => chooseMelodyTargets(42, [1], { blockTargetMode: 3 }), RangeError);
});

test('P5e extended target directions and variation match 4608 production Java cases', () => {
  const { load } = fixture();
  const { chooseMelodyTargets } = load('core/music/melody-targets.ts');
  const oracle = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'fixtures/melody-targets-extended.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(oracle.length, 4608);
  for (const [index, sample] of oracle.entries()) assert.deepEqual(chooseMelodyTargets(sample.seed, sample.progression, sample.globalSettings), sample.targets, `case ${index}`);
});

test('P5e catalogue selection matches 4500 production Java shapes and subsequent random draws', () => {
  const { load } = fixture();
  const { chooseCatalogueBlock } = load('core/music/melody-catalogue.ts'), { JavaRandom } = load('core/music/java-random.ts');
  const oracle = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'fixtures/melody-catalogue.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(oracle.length, 4500);
  for (const [index, entry] of oracle.entries()) {
    const random = new JavaRandom(BigInt(entry.seed));
    const result = chooseCatalogueBlock(entry.change, entry.jump, random, entry.length ?? undefined,
      entry.variance, entry.directions, entry.preference, entry.weights);
    assert.deepEqual(result.notes, entry.notes, `case ${index} notes`);
    assert.equal(result.type, entry.type, `case ${index} type`);
    assert.equal(random.nextInt(), entry.nextInt, `case ${index} stream position`);
  }
});

test('P5e empty preferred split-pitch ranges retain the in-range B without throwing', () => {
  const { load } = fixture(), { expandMelody } = load('core/music/melody-expansion.ts');
  let retained = false;
  for (let seed = 0; seed < 16; seed++) {
    const skeleton = [[{ midi: 70, rhythm: 2, duration: 1.8, velocity: 80, offset: 0 },
      { midi: 71, rhythm: 2, duration: 1.8, velocity: 80, offset: 0 }]];
    const notes = expandMelody(skeleton, { algorithm: 'block', splitChance: 100, accents: 0 }, seed, 0, 80, 105).flat();
    assert.ok(notes.every(note => Number.isInteger(note.midi) && Number.isFinite(note.duration)));
    assert.equal(notes.reduce((sum, note) => sum + note.rhythm, 0), 4);
    retained ||= notes[1].midi === 71;
  }
  assert.equal(retained, true);
});

test('drum pitch selection matches 480 cases extracted verbatim from production DrumGUI', () => {
  const { load } = fixture();
  const { chooseDrumPitches } = load('core/music/drum-reroll.ts');
  const { JavaRandom } = load('core/music/java-random.ts');
  const oracle = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'fixtures/drum-reroll.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(oracle.pitches.length, 480);
  for (const sample of oracle.pitches) {
    assert.deepEqual(chooseDrumPitches(new JavaRandom(BigInt(sample.seed)), sample.count, sample.remaining, sample.onlyAdd), sample.result);
  }
});

test('instrument pool catalogues retain production Java membership and order', () => {
  const { load } = fixture();
  const { INSTRUMENT_POOLS } = load('core/music/instrument-generation.ts');
  const java = fs.readFileSync(path.resolve(__dirname, '..', '../../../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/InstUtils.java'), 'utf8');
  for (const [pool, symbol] of Object.entries({ ALL: 'ALL', MELODY: 'MELODY', BASS: 'BASS', CHORD: 'CHORD', PLUCK: 'PLUCK', LONG_PAD: 'LONG', DRUM: 'DRUM' })) {
    const declaration = java.match(new RegExp(symbol + '_INST_NAMES\\s*=\\s*\\{([\\s\\S]*?)\\};'))[1];
    assert.deepEqual(INSTRUMENT_POOLS[pool], [...declaration.matchAll(/"(\d+):/g)].map(match => Number(match[1])));
  }
});

for (const role of ['melody', 'bass', 'chords', 'arpeggio', 'drums']) {
  test(`${role} instrument-only reroll preserves ownership, locks, exact replay and MIDI identity`, () => {
    const { service, load, midi } = fixture();
    const { INSTRUMENT_POOLS, DEFAULT_INSTRUMENT_POLICIES } = load('core/music/instrument-generation.ts');
    const id = `track-${role}-1`, second = service.addTrack(role), locked = service.addTrack(role);
    const track = id => service.project().tracks.find(item => item.id === id);
    service.updateSettings({ seed: '9007199254740993' });
    service.setTrackInstrumentLock(second, true); service.setTrackRerollLock(locked, true);
    service.updateTrackPhrase(id, [{ id: 'manual', midi: role === 'drums' ? 36 : 60, startBeat: 0, durationBeats: 1, velocity: 90 }]);
    service.setSectionTrackPresence(0, id, true); // Ensure MIDI has a note event from which its parser reads the channel.
    service.freezePartSettings({ kind: 'section-track', sectionId: service.project().arrangement[0].id, trackId: id });
    const before = service.exportProjectJson(), previous = track(id), arrangement = service.project().arrangement;
    const previousSecond = track(second), previousLocked = track(locked);
    const result = service.rerollRoleInstruments(role);
    assert.equal(result, 'changed');
    const after = service.exportProjectJson();
    assert.strictEqual(track(second), previousSecond);
    assert.strictEqual(track(locked), previousLocked);
    assert.strictEqual(service.project().arrangement, arrangement);
    for (const key of ['id', 'name', 'midiChannel', 'editedPhrase']) assert.deepEqual(track(id)[key], previous[key]);
    assert.deepEqual({ ...track(id).mix, program: previous.mix.program }, previous.mix);
    assert.deepEqual({ ...track(id).generatorSettings, ...(role === 'drums' ? { pitch: previous.generatorSettings.pitch } : {}) }, previous.generatorSettings);
    const pool = DEFAULT_INSTRUMENT_POLICIES[role].pool;
    const choices = pool === 'AUTO' ? [...INSTRUMENT_POOLS.CHORD, ...INSTRUMENT_POOLS.PLUCK] : INSTRUMENT_POOLS[pool];
    assert.ok(choices.includes(role === 'drums' ? track(id).generatorSettings.pitch : track(id).mix.program));
    const exported = new Midi(midi.generateCompositionMidi(service.project())).tracks.find(item => item.name === previous.name);
    assert.equal(exported.channel, previous.midiChannel - 1);
    if (role !== 'drums') assert.equal(exported.instrument.number, track(id).mix.program);
    assert.equal(exported.notes[0].midi, previous.editedPhrase[0].midi);
    service.undo(); assert.equal(service.exportProjectJson(), before);
    assert.equal(service.rerollRoleInstruments(role), 'changed'); assert.equal(service.exportProjectJson(), after);
    service.undo(); service.redo(); assert.equal(service.exportProjectJson(), after);
    assert.equal(service.importProjectJson(after), true);
    const restored = new (load('core/project/project.service.ts').ProjectService)();
    assert.deepEqual(restored.project().tracks, service.project().tracks);
    assert.equal(restored.rerollRoleInstruments(role), service.rerollRoleInstruments(role));
    assert.deepEqual(restored.project(), service.project());
  });
}

for (const role of ['bass', 'chords', 'drums', 'melody']) {
  test(`${role} rerolls preserve identity, manual notes, locks, local patches and exact replay`, () => {
    const { service, phrase, settings, midi, workspace, ui, load } = fixture();
    service.updateInstrumentPolicy(role, { randomize: false });
    const primaryId = `track-${role}-1`, manualId = service.addTrack(role), lockedId = service.addTrack(role);
    const track = id => service.project().tracks.find(track => track.id === id);
    service.updateSettings({ seed: '9007199254740993' });
    const section = service.project().arrangement[0];
    service.updatePartSettings({ kind: 'section-role', sectionId: section.id, role }, { velocityMin: 45, velocityMax: 95 });
    service.freezePartSettings({ kind: 'section-track', sectionId: section.id, trackId: primaryId });
    const effective = settings.resolvePartTrack(track(primaryId), service.project().arrangement[0]);
    const manual = [{ id: 'manual', midi: role === 'drums' ? 36 : 60, startBeat: 0, durationBeats: 1, velocity: 90 }];
    service.updateTrackPhrase(manualId, manual);
    service.setTrackRerollLock(lockedId, true);
    const before = service.exportProjectJson(), others = service.project().tracks.filter(track => track.role !== role);
    const locked = track(lockedId), arrangement = service.project().arrangement;
    assert.equal(service.rerollRoleTracks(role), 'changed');
    const after = service.exportProjectJson();
    assert.equal(service.project().trackRerollCounts[role], 1);
    assert.deepEqual(service.project().tracks.filter(track => track.role !== role), others);
    assert.strictEqual(track(lockedId), locked); assert.strictEqual(service.project().arrangement, arrangement);
    assert.deepEqual(phrase.phraseForTrack(service.project(), track(manualId)), manual);
    for (const id of [primaryId, manualId]) {
      const previous = JSON.parse(before).tracks.find(track => track.id === id);
      for (const key of ['id', 'name', 'mix', 'midiChannel', 'editedPhrase']) assert.deepEqual(track(id)[key], previous[key]);
      if (role === 'drums') assert.equal(track(id).generatorSettings.pitch, previous.generatorSettings.pitch);
    }
    assert.deepEqual(settings.resolvePartTrack(track(primaryId), service.project().arrangement[0]).generatorSettings, effective.generatorSettings);
    ui.selectTrack(primaryId);
    const field = role === 'melody' ? 'speed' : role === 'drums' ? 'hitsPerPattern' : 'velocityMax';
    assert.equal(workspace.partValues()[field], track(primaryId).generatorSettings[field]);
    service.undo(); assert.equal(service.exportProjectJson(), before);
    assert.equal(service.rerollRoleTracks(role), 'changed'); assert.equal(service.exportProjectJson(), after);
    service.undo(); service.redo(); assert.equal(service.exportProjectJson(), after);
    assert.equal(service.importProjectJson(service.exportProjectJson()), true);
    const restored = new (load('core/project/project.service.ts').ProjectService)();
    assert.equal(service.rerollRoleTracks(role, primaryId), 'changed');
    assert.equal(restored.rerollRoleTracks(role, primaryId), 'changed');
    assert.deepEqual(JSON.parse(restored.exportProjectJson()), JSON.parse(service.exportProjectJson()));
    assert.equal(service.importProjectJson(service.exportProjectJson()), true);
    const parsed = new Midi(midi.generateCompositionMidi(service.project()));
    assert.equal(parsed.tracks.find(item => item.name === track(primaryId).name).notes.length,
      phrase.layOutTrackPhrase(service.project(), track(primaryId)).length);
    const policies = service.project().trackGenerationPolicies, counts = service.project().trackRerollCounts;
    const bases = service.project().tracks.map(track => track.generatorSettings);
    service.randomizeArrangementPresence();
    assert.deepEqual(service.project().tracks.map(track => track.generatorSettings), bases);
    assert.deepEqual(service.project().trackGenerationPolicies, policies); assert.deepEqual(service.project().trackRerollCounts, counts);
  });

  test(`${role} conflicts and unavailable reroll targets preserve history and counters`, () => {
    const { service } = fixture();
    const id = `track-${role}-1`, sectionId = service.project().arrangement[0].id;
    service.updatePartSettings({ kind: 'global-role', role }, { velocityMax: 127 });
    service.updatePartSettings({ kind: 'section-role', sectionId, role }, { velocityMin: 120 });
    service.updateSettings({ name: 'redo' }); service.undo(); const before = service.exportProjectJson();
    assert.equal(service.rerollRoleTracks(role), 'invalid');
    assert.equal(service.exportProjectJson(), before); assert.equal(service.canRedo(), true);
    assert.equal(service.rerollRoleTracks(role, 'deleted'), 'unchanged');
    assert.equal(service.rerollRoleTracks(role, 'track-arpeggio-1'), 'unchanged');
    service.setTrackRerollLock(id, true); const locked = service.exportProjectJson();
    assert.equal(service.rerollRoleTracks(role), 'unchanged'); assert.equal(service.exportProjectJson(), locked);
  });
}

test('Arpeggio hit choices and fill weights match Java policy helper evidence', () => {
  const { load } = fixture();
  const { JavaRandom } = load('core/music/java-random.ts');
  const core = load('core/music/track-generation.ts');
  const expected = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'fixtures/arpeggio-policy.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.deepEqual(Array.from({ length: 100 }, (_, index) => core.weightedChordFill(index)), expected.fills);
  for (const sample of expected.hits) {
    const random = new JavaRandom(BigInt(sample.seed));
    assert.deepEqual(Array.from({ length: 32 }, () => core.chooseArpeggioHits(random,
      { ...core.DEFAULT_ARPEGGIO_POLICY, powerOfTwo: sample.powerOfTwo })), sample.hits);
  }
});

test('bass join modes match 432 complete production Java phrases', () => {
  const { load } = fixture();
  const { generateBassline } = load('core/music/bass-generator.ts');
  const cases = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'fixtures/bass-core.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(cases.length, 432);
  for (const [caseIndex, sample] of cases.entries()) {
    const actual = generateBassline(BigInt(sample.seed), sample.key, sample.scale, sample.progression,
      sample.settings.rhythm, sample.settings.noteVariation, sample.settings);
    assert.equal(actual.length, sample.notes.length, `case ${caseIndex} note count`);
    actual.forEach((note, index) => {
      const expected = sample.notes[index], label = `case ${caseIndex}, note ${index}`;
      assert.equal(note.midi, expected[0], `${label} pitch`);
      assert.ok(Math.abs(note.startBeat - expected[1]) < 1e-10, `${label} onset ${note.startBeat} vs ${expected[1]}`);
      assert.ok(Math.abs(note.durationBeats - expected[2]) < 1e-10, `${label} duration ${note.durationBeats} vs ${expected[2]}`);
      assert.equal(note.velocity, expected[3], `${label} velocity`);
    });
  }
});

test('shared drum controls match 96 complete Java phrases with spans, pauses, split hits and extreme swing', () => {
  const { load } = fixture();
  const { generateDrumPart } = load('core/music/drum-generator.ts');
  const cases = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'fixtures/drum-shared.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(cases.length, 96);
  for (const [caseIndex, sample] of cases.entries()) {
    const actual = generateDrumPart(BigInt(sample.seed), sample.barCount, sample.settings);
    assert.equal(actual.length, sample.notes.length, `case ${caseIndex} note count`);
    actual.forEach((note, index) => {
      const expected = sample.notes[index], label = `case ${caseIndex}, note ${index}`;
      assert.equal(note.midi, expected[0], label);
      assert.ok(Math.abs(note.startBeat - expected[1]) < 1e-10, `${label} onset ${note.startBeat} vs ${expected[1]}`);
      assert.ok(Math.abs(note.durationBeats - expected[2]) < 1e-10, `${label} duration`);
      assert.equal(note.velocity, expected[3], `${label} velocity`);
    });
  }
});

test('span/repeat slicing, custom velocities, voice expansion and feedback match 578 production Java helper cases', () => {
  const { load } = fixture();
  const processing = load('core/music/part-processing.ts');
  const cases = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'fixtures/shared-part.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(cases.length, 578);
  for (const [index, sample] of cases.entries()) {
    if (sample.kind === 'span') {
      assert.deepEqual(processing.spannedPattern(sample.settings, sample.chordIndex, sample.arp), sample.pattern, `pattern ${index}`);
      assert.deepEqual(processing.spannedVelocities(sample.settings, sample.chordIndex, sample.arp), sample.velocities, `velocities ${index}`);
    } else if (sample.kind === 'voices') {
      assert.deepEqual(processing.expandedVoices(sample.pitches, sample.settings), sample.result, `voices ${index}`);
    } else {
      const actual = processing.applyPartTiming([{ id: 'source', midi: 60, startBeat: 12, durationBeats: 0.75, velocity: 90 }], sample.settings)
        .map(note => [note.midi, note.startBeat, note.durationBeats, note.velocity]);
      const sort = notes => notes.sort((a, b) => a[1] - b[1] || a[3] - b[3]);
      assert.deepEqual(sort(actual), sort(sample.notes), `feedback ${index}`);
    }
  }
});

test('every applicable shared control has a role consumer and genuine Java exceptions remain excluded', () => {
  const { service, settings, phrase, load } = fixture();
  const keys = role => settings.PART_CONTROLS[role].map(control => control.key);
  for (const role of ['melody', 'bass', 'chords', 'arpeggio', 'drums']) {
    for (const key of ['patternSeed', 'offset', 'feedbackCount', 'feedbackDuration', 'feedbackVol']) assert.ok(keys(role).includes(key));
    assert.equal(keys(role).includes('generationEnabled'), false);
    assert.equal(keys(role).includes('accents'), role === 'melody');
    assert.equal(keys(role).includes('patternRepeat'), role === 'arpeggio');
    assert.equal(keys(role).includes('exceptionChance'), ['arpeggio', 'drums'].includes(role));
    assert.equal(keys(role).includes('pauseChance'), role !== 'bass');
    assert.equal(keys(role).includes('swingPercent'), role !== 'bass');
    assert.equal(keys(role).includes('stretchEnabled'), ['chords', 'arpeggio'].includes(role));
    const track = service.project().tracks.find(track => track.role === role);
    const generate = patch => phrase.generateTrackPhrase(service.project(), { ...track, generatorSettings: { ...track.generatorSettings, ...patch } });
    assert.ok(generate({}).length > 0);
    assert.deepEqual(generate({ patternSeed: 123 }), phrase.generateTrackPhrase({ ...service.project(), seed: '123' }, track));
    assert.notDeepEqual(generate({ patternSeed: 123 }), generate({}));
    const delayed = generate({ offset: 250, feedbackCount: 2, feedbackDuration: 750, feedbackVol: 65 });
    assert.equal(delayed.length, generate({}).length * 3);
    assert.ok(delayed.every(note => note.startBeat >= 0.25));
  }
  for (const role of ['bass', 'chords', 'arpeggio', 'drums']) {
    const track = service.project().tracks.find(track => track.role === role);
    const generate = patch => phrase.generateTrackPhrase(service.project(), { ...track,
      generatorSettings: { ...track.generatorSettings, rhythm: 'full', hitsPerPattern: 4, ...patch } });
    const full = generate({});
    assert.equal(full.length, (role === 'chords' ? 3 : 1) * 16);
    assert.equal(generate({ chordSpan: 2 }).length, full.length / 2);
    assert.deepEqual(generate({ patternFlip: true }), []);
    assert.deepEqual(generate({ useCustomVelocities: true, customVelocities: Array(32).fill(0) }), []);
    assert.ok(generate({ useCustomVelocities: true, customVelocities: Array(32).fill(101) }).every(note => note.velocity === 101));
    if (role !== 'bass') {
      assert.deepEqual(generate({ pauseChance: 100 }), []);
      assert.notDeepEqual(generate({ swingPercent: 66 }), full);
      assert.deepEqual(generate({ hitsPerPattern: 3, swingPercent: 66 }), generate({ hitsPerPattern: 3 }));
    }
    if (role === 'arpeggio' || role === 'drums') assert.equal(generate({ exceptionChance: 100 }).length, full.length * 2);
    if (role === 'chords') assert.equal(generate({ stretchEnabled: true, chordNotesStretch: 6 }).length, full.length * 2);
    if (role === 'arpeggio') {
      assert.equal(generate({ patternRepeat: 2 }).length, full.length * 2);
      assert.notDeepEqual(generate({ stretchEnabled: true, chordNotesStretch: 2 }), full);
    }
  }
  const { PartSettingsEditorComponent } = load('shared/part-settings-editor.component.ts');
  const editor = new PartSettingsEditorComponent(); editor.role.set('bass');
  editor.values.set({ ...settings.settingsValues(service.project().bass, 'bass'), useCustomVelocities: true });
  assert.equal(editor.controls().some(control => control.key === 'hitsPerPattern'), false);
  assert.equal(editor.controls().some(control => control.key === 'velocityMin'), true);
});

test('single-pitch drums match 96 complete production Java phrases at fixed seeds', () => {
  const { load } = fixture();
  const { generateDrumPart } = load('core/music/drum-generator.ts');
  const cases = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'fixtures/drum-core.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(cases.length, 96);
  for (const [caseIndex, sample] of cases.entries()) {
    const actual = generateDrumPart(BigInt(sample.seed), sample.barCount, sample.settings);
    assert.equal(actual.length, sample.notes.length, `case ${caseIndex} note count`);
    actual.forEach((note, index) => {
      const expected = sample.notes[index], label = `case ${caseIndex}, note ${index}`;
      assert.equal(note.midi, expected[0], `${label} pitch`);
      assert.ok(Math.abs(note.startBeat - expected[1]) < 1e-10, `${label} onset: ${note.startBeat} vs ${expected[1]}`);
      assert.ok(Math.abs(note.durationBeats - expected[2]) < 1e-10, `${label} duration: ${note.durationBeats} vs ${expected[2]}`);
      assert.equal(note.velocity, expected[3], `${label} dynamics`);
    });
  }
});

for (const [name, count] of [['core', 80], ['extended', 192], ['global', 192], ['effects', 1152], ['prepared-targets', 192], ['catalogue-phrases', 1536], ['emphasis', 1152], ['tonal', 1920], ['harmony', 2304], ['modes', 1152]]) test(`current block melody matches ${count} complete production Java ${name} phrases at fixed seeds`, () => {
  const { load } = fixture();
  const { generateMelody } = load('core/music/melody-generator.ts');
  const cases = JSON.parse(fs.readFileSync(path.join(__dirname, '..', `fixtures/melody-${name}.java.json`), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(cases.length, count);
  for (const [caseIndex, sample] of cases.entries()) {
    if (name === 'prepared-targets') {
      const { chooseMelodyTargets } = load('core/music/melody-targets.ts');
      assert.deepEqual(chooseMelodyTargets(sample.targetSeed, sample.targetProgression, sample.globalSettings), sample.settings.chordNoteChoices, `case ${caseIndex} prepared targets`);
    }
    const progression = ['harmony', 'modes'].includes(name) ? sample.targetProgression : Array(sample.chordCount).fill(1);
    if (name === 'modes' && sample.prepared) assert.deepEqual(load('core/music/melody-targets.ts').chooseMelodyTargets(
      sample.targetSeed, progression, sample.globalSettings, sample.key, sample.scale, sample.customChords), sample.settings.chordNoteChoices);
    const actual = generateMelody(BigInt(sample.seed), sample.key, sample.scale, progression, sample.settings, sample.notesSeedOffset, undefined, { newBlocksChance: 100, ...sample.globalSettings }, sample.customChords);
    assert.equal(actual.length, sample.notes.length, `case ${caseIndex} note count`);
    actual.forEach((note, index) => {
      const expected = sample.notes[index], label = `case ${caseIndex}, note ${index}`;
      assert.equal(note.midi, expected[0], `${label} pitch`);
      assert.ok(Math.abs(note.startBeat - expected[1]) < 1e-10, `${label} onset: ${note.startBeat} vs ${expected[1]}`);
      assert.ok(Math.abs(note.durationBeats - expected[2]) < 1e-10, `${label} duration: ${note.durationBeats} vs ${expected[2]}`);
      assert.equal(note.velocity, expected[3], `${label} dynamics`);
    });
  }
});

test('P5e Start note delays feed same-section melody dependency grids before swing', () => {
  const { service, phrase } = fixture();
  const section = service.project().arrangement[1];
  const before = phrase.sectionMelodyContext(service.project(), section);
  service.updatePartSettings({ kind: 'section-track', sectionId: section.id, trackId: 'track-melody-1' },
    { speed: -100, startNoteChance: 0, doubledRhythmChance: 100, splitChance: 100 });
  const after = phrase.sectionMelodyContext(service.project(), service.project().arrangement[1]);
  assert.notDeepEqual(after, before);
  assert.equal(service.project().arrangement[1].trackPresence['track-melody-1'], section.trackPresence['track-melody-1']);
});

test('custom melody durations preserve note/rest boundaries in both generators and Flip complements them', () => {
  const { load } = fixture();
  const { generateMelody } = load('core/music/melody-generator.ts');
  const rows = [[1, -0.5, 0.5, 1, -1], ...Array.from({ length: 9 }, () => [])];
  const settings = { algorithm: 'block', rhythm: 'custom', customMelodyDurations: rows };
  for (const contextualMelody of [false, true]) for (const patternFlip of [false, true]) {
    const notes = generateMelody(42n, 'C', 'major', [1, 5, 6, 4], { ...settings, patternFlip }, 0, undefined, { contextualMelody });
    const starts = patternFlip ? [1, 3] : [0, 1.5, 2];
    assert.deepEqual(notes.map(note => note.startBeat), [0, 4, 8, 12].flatMap(chord => starts.map(start => chord + start)), `${contextualMelody}/${patternFlip}`);
    assert.ok(notes.every(note => note.midi >= 0 && note.velocity > 0));
    const again = generateMelody(42n, 'C', 'major', [1, 5, 6, 4], { ...settings, patternFlip }, 0, undefined, { contextualMelody });
    assert.deepEqual(again, notes);
    const fast = generateMelody(42n, 'C', 'major', [1, 5, 6, 4], { ...settings, patternFlip, speed: 100 }, 0, undefined, { contextualMelody });
    assert.deepEqual(fast.map(note => note.startBeat), notes.map(note => note.startBeat));
  }
  const normal = { algorithm: 'block', speed: 37 };
  assert.deepEqual(generateMelody(9n, 'C', 'major', [1, 5], normal), generateMelody(9n, 'C', 'major', [1, 5], { ...normal, rhythm: 'melody1', patternFlip: true, customMelodyDurations: rows }));
});

test('custom melody lanes pad, cycle by block identity, weight deterministically and validate every selectable lane', () => {
  const { load } = fixture();
  const { melodyDurationBlocks, melodyDurationError, paddedMelodyDurations } = load('core/music/melody-durations.ts');
  const rows = [[1, -1], [], [0.5, -0.5, 1, -2], ...Array.from({ length: 7 }, () => [])];
  const settings = { algorithm: 'block', rhythm: 'custom', customMelodyDurations: rows };
  assert.deepEqual(paddedMelodyDurations(rows[0]), [1, -1, -2]);
  assert.deepEqual(melodyDurationBlocks(settings, 42, 0, 0).flat(), [1, -1, -2]);
  assert.deepEqual(melodyDurationBlocks(settings, 42, 0, 1).flat(), rows[2]);
  const chances = [0, 0, 100, ...Array(7).fill(0)];
  for (let seed = 0; seed < 20; seed++) assert.deepEqual(melodyDurationBlocks({ ...settings, customDurationRandomWeighting: true, customDurationChances: chances }, seed, 0).flat(), rows[2]);
  assert.match(melodyDurationError({ ...settings, customDurationRandomWeighting: true, customDurationChances: Array(10).fill(0) }), /positive chance/);
  assert.match(melodyDurationError({ ...settings, customMelodyDurations: [[-4], ...rows.slice(1)] }), /Lane 1.*only pauses/);
  assert.equal(melodyDurationError({ ...settings, customDurationRandomWeighting: true, customDurationChances: chances, customMelodyDurations: [[-4], ...rows.slice(1)] }), null);
  assert.match(melodyDurationError({ ...settings, customMelodyDurations: [[4], ...Array.from({ length: 9 }, () => [])], patternFlip: true }), /after Flip/);
});

test('all fill masks match the actual Java enum, including odd halves, flipped silence and repeated slots', () => {
  const { load } = fixture();
  const { chordSpanFillMask, CHORD_SPAN_FILLS } = load('core/music/chord-span-fill.ts');
  const expected = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'fixtures/chord-span-fill.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(Object.keys(expected).length, 264);
  for (const [key, mask] of Object.entries(expected)) {
    const [fill, length, flipped] = key.split('/');
    assert.deepEqual(chordSpanFillMask(Number(length), fill, flipped === 'true'), mask, key);
  }
  assert.equal(CHORD_SPAN_FILLS.length, 12);
  assert.deepEqual(chordSpanFillMask(5, 'HALF1'), [1, 1, 0, 0, 0]);
  assert.deepEqual(chordSpanFillMask(5, 'HALF2'), [0, 0, 1, 1, 1]);
  assert.deepEqual(chordSpanFillMask(3, 'ODD'), [0, 1, 0]);
  assert.deepEqual(chordSpanFillMask(3, 'EVEN'), [1, 0, 1]);
  for (const args of [[33], [-1], [1.5], [3, 'UNKNOWN'], [3, 'ALL', 1]]) {
    assert.throws(() => chordSpanFillMask(...args), RangeError);
  }
});

test('fill produces rests in every supported generator and preserves role-specific random consumption', () => {
  const { service, load } = fixture();
  const seed = 9223372036854775807n;
  const progression = [1, 1, 1, 1, 1];
  const { generateBassline } = load('core/music/bass-generator.ts');
  const { generateChordPart } = load('core/music/chord-generator.ts');
  const { generateArpeggio } = load('core/music/arpeggio-generator.ts');
  const { generateDrumPart } = load('core/music/drum-generator.ts');
  const calls = {
    bass: (settings) => generateBassline(seed, 'C', 'major', progression, 'full', 100, settings),
    chords: (settings) => generateChordPart(seed, 'C', 'major', progression, settings),
    arpeggio: (settings) => generateArpeggio(seed, 'C', 'major', progression, { ...settings, pattern: 'random' }),
    drums: (settings) => generateDrumPart(seed, progression.length, settings),
  };
  for (const [role, generate] of Object.entries(calls)) {
    const settings = service.project()[role];
    const baseline = generate(settings);
    assert.deepEqual(generate({ ...settings, chordSpanFill: 'ALL', fillFlip: false }), baseline, role + ' defaults');
    assert.deepEqual(generate({ ...settings, chordSpanFill: 'ALL', fillFlip: true }), [], role + ' silence');
    const filled = generate({ ...settings, chordSpanFill: 'ODD' });
    const slot = (event) => event.chordIndex ?? event.barIndex;
    assert.ok(filled.length > 0);
    assert.ok(filled.every((event) => slot(event) === 1 || slot(event) === 3), role);
    if (role !== 'bass') assert.deepEqual(filled, baseline.filter((event) => slot(event) % 2 === 1), role + ' rests keep streams');
    else {
      const strip = (event) => ({ midi: event.midi, duration: event.durationBeats, velocity: event.velocity });
      assert.deepEqual(filled.filter((event) => slot(event) === 1).map(strip),
        baseline.filter((event) => slot(event) === 0).map(strip), 'bass skips dynamics/variation for silent chords');
    }
  }
});

test('static rhythm rotation matches Java before truncation, including short and non-eight-multiple grids', () => {
  const { load } = fixture();
  const { rhythmPatternMask } = load('core/music/rhythm-pattern.ts');
  const expected = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'fixtures/rhythm-pattern.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(Object.keys(expected).length, 192);
  for (const [key, mask] of Object.entries(expected)) {
    const [pattern, hits, shift] = key.split('/');
    assert.deepEqual(rhythmPatternMask(pattern, Number(hits), Number(shift)), mask, key);
    assert.deepEqual(rhythmPatternMask(pattern, Number(hits), Number(shift), true), mask.map((value) => 1 - value), key + ' flip');
  }
  assert.deepEqual(rhythmPatternMask('ALT', 3, 1), [0, 1, 0], 'rotate the padded eight cells rather than a three-cell slice');
  assert.deepEqual(rhythmPatternMask('ONESIX', 8), [1, 0, 0, 0, 0, 1, 0, 0]);
  for (const args of [['CUSTOM', 8], ['FULL', 0], ['FULL', 33], ['ALT', 1.5], ['ALT', 8, -1], ['ALT', 8, 9], ['ALT', 8, 0, 1]]) {
    assert.throws(() => rhythmPatternMask(...args), RangeError);
  }
});

test('chord rhythm hits, shift and flip affect note onsets/durations while retaining every grid-step velocity draw', () => {
  const { service, load } = fixture();
  const { generateChordPart } = load('core/music/chord-generator.ts');
  const { JavaRandom } = load('core/music/java-random.ts');
  const seed = 9223372036854775807n;
  const settings = service.project().chords;
  const generate = (patch) => generateChordPart(seed, 'C', 'major', [1, 5], { ...settings, ...patch });
  const baseline = generate({});
  assert.deepEqual(generate({ hitsPerPattern: 8, patternShift: 0, patternFlip: false }), baseline);
  const expectedMasks = {
    full: [1, 1, 1, 1, 1, 1, 1, 1], half: [1, 0, 1, 0, 1, 0, 1, 0],
    tresillo: [1, 0, 0, 1, 0, 0, 1, 0], sparse: [1, 0, 0, 0, 1, 0, 0, 0], single: [1, 0, 0, 0, 0, 0, 0, 0],
  };
  for (const [rhythm, mask] of Object.entries(expectedMasks)) {
    const notes = generate({ rhythm });
    assert.deepEqual(notes.map((note) => note.startBeat), [0, 4].flatMap((start) => mask.flatMap((value, index) => value ? [start + index / 2] : [])));
    assert.ok(notes.every((note) => note.durationBeats === 0.5));
  }
  assert.deepEqual(generate({ rhythm: 'one-six' }).map((note) => note.startBeat), [0, 2.5, 4, 6.5]);
  const shifted = generate({ rhythm: 'half', hitsPerPattern: 3, patternShift: 1, noteLengthPercent: 75 });
  assert.deepEqual(shifted.map((note) => note.startBeat), [4 / 3, 4 + 4 / 3]);
  assert.ok(shifted.every((note) => note.durationBeats === 1));
  const random = new JavaRandom(BigInt.asIntN(32, BigInt.asIntN(32, seed) + 20001n));
  random.nextInt(21);
  assert.equal(shifted[0].velocity, random.nextInt(21) + 69, 'rest at step 0 consumes its velocity draw');
  const dense = generate({ hitsPerPattern: 32 });
  assert.equal(dense.length, 64);
  assert.equal(dense.at(-1).startBeat, 7.875);
  assert.ok(dense.every((note) => note.durationBeats === 0.125));
  assert.deepEqual(generate({ patternFlip: true }), []);
  assert.deepEqual(generate({ rhythm: 'half', patternFlip: true, chordSpanFill: 'EVEN' }).map((note) => note.startBeat), [0.5, 1.5, 2.5, 3.5]);
});

test('Euclidean grids match production Java for all supported hits, pulse counts, shifts and flipped complements', () => {
  const { load } = fixture();
  const { euclideanPatternMask } = load('core/music/rhythm-pattern.ts');
  const fixtures = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'fixtures/euclidean-pattern.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(Object.keys(fixtures).length, 5040);
  for (const [key, expected] of Object.entries(fixtures)) {
    const args = key.split('/').map(Number);
    assert.deepEqual(euclideanPatternMask(...args), expected, key);
    assert.deepEqual(euclideanPatternMask(...args, true), expected.map((value) => 1 - value), key + ' flipped');
    assert.equal(expected.length, args[0]);
  }
  for (const args of [[0, 0], [33, 4], [8, 9], [8, -1], [8, 1.5], [8, 3, 9], [8, 3, 0, 1]]) {
    assert.throws(() => euclideanPatternMask(...args), RangeError);
  }
});

test('Euclidean chord generation uses shifted/flipped subdivisions and preserves velocity draws for rests', () => {
  const { service, load } = fixture();
  const { generateChordPart } = load('core/music/chord-generator.ts');
  const { chordRhythmMask } = load('core/music/rhythm-pattern.ts');
  const { JavaRandom } = load('core/music/java-random.ts');
  const seed = 9223372036854775807n;
  const base = service.project().chords;
  const settings = { ...base, rhythm: 'euclid', hitsPerPattern: 8, euclideanPulses: 3, patternShift: 1, noteLengthPercent: 75 };
  const mask = chordRhythmMask(settings);
  const notes = generateChordPart(seed, 'C', 'major', [1, 5], settings);
  assert.deepEqual(notes.map((note) => note.startBeat), [0, 4].flatMap((start) => mask.flatMap((value, slot) => value ? [start + slot / 2] : [])));
  assert.ok(notes.every((note) => note.durationBeats === 0.375));
  const random = new JavaRandom(BigInt.asIntN(32, BigInt.asIntN(32, seed) + 20001n));
  const velocities = Array.from({ length: 8 }, () => random.nextInt(21) + 69);
  assert.deepEqual(notes.filter((note) => note.chordIndex === 0).map((note) => note.velocity), velocities.filter((_, slot) => mask[slot]));
  const flipped = generateChordPart(seed, 'C', 'major', [1, 5], { ...settings, patternFlip: true, chordSpanFill: 'EVEN' });
  assert.deepEqual(flipped.map((note) => note.startBeat), mask.flatMap((value, slot) => value ? [] : [slot / 2]));
  assert.deepEqual(generateChordPart(seed, 'C', 'major', [1], { ...settings, euclideanPulses: 0 }), []);
  assert.equal(generateChordPart(seed, 'C', 'major', [1], { ...settings, euclideanPulses: 0, patternFlip: true }).length, 8);
  assert.deepEqual(chordRhythmMask({ ...settings, hitsPerPattern: 3, euclideanPulses: 32 }), [1, 1, 1]);
  assert.equal(generateChordPart(seed, 'C', 'major', [1], { ...settings, hitsPerPattern: 3, euclideanPulses: 32 }).length, 3);
  assert.deepEqual(generateChordPart(seed, 'C', 'major', [1], { ...base, euclideanPulses: 7 }),
    generateChordPart(seed, 'C', 'major', [1], base), 'Pulses has no effect on static rhythms');
});

test('rhythm catalogue options agree with every scope, import decoder and supported generator', () => {
  const { load, service, settings } = fixture();
  const catalogue = load('core/music/rhythm-patterns.ts');
  const { chordRhythmMask } = load('core/music/rhythm-pattern.ts');
  const { generateBassline } = load('core/music/bass-generator.ts');
  const { generateChordPart } = load('core/music/chord-generator.ts');
  const chordOptions = settings.PART_CONTROLS.chords.find(control => control.key === 'rhythm').options;
  const bassOptions = settings.PART_CONTROLS.bass.find(control => control.key === 'rhythm').options;
  assert.strictEqual(chordOptions, catalogue.RHYTHM_PATTERNS);
  assert.strictEqual(bassOptions, catalogue.BASS_RHYTHMS);
  assert.ok(chordOptions.includes('custom'));
  assert.equal(new Set(chordOptions).size, chordOptions.length);
  const sectionId = service.project().arrangement[1].id;
  for (const [role, options] of [['chords', chordOptions], ['bass', bassOptions]]) {
    const trackId = `track-${role}-1`;
    const scopes = [{ kind: 'global-role', role }, { kind: 'global-track', trackId },
      { kind: 'section-role', sectionId, role }, { kind: 'section-track', sectionId, trackId }];
    for (const rhythm of options) {
      for (const scope of scopes) {
        service.updatePartSettings(scope, { rhythm });
        const track = service.project().tracks.find(track => track.id === trackId);
        const section = scope.kind.startsWith('section') ? service.project().arrangement[1] : undefined;
        const effective = settings.resolvePartTrack(track, section).generatorSettings;
        assert.equal(effective.rhythm, rhythm);
        if (role === 'chords') {
          assert.doesNotThrow(() => generateChordPart(42n, 'C', 'major', [1], effective));
          assert.equal(chordRhythmMask(effective).length, effective.hitsPerPattern ?? 8);
        } else {
          assert.doesNotThrow(() => generateBassline(42n, 'C', 'major', [1], rhythm, effective.noteVariation, effective));
        }
        assert.equal(service.importProjectJson(service.exportProjectJson()), true);
      }
    }
  }
});

test('velocity patterns match Java stored subdivision selection and retain zero, independent of shift/flip', () => {
  const { load } = fixture();
  const { chordVelocityPattern } = load('core/music/velocity-pattern.ts');
  const fixtures = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'fixtures/velocity-pattern.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(Object.keys(fixtures).length, 84);
  for (const [key, expected] of Object.entries(fixtures)) {
    const [sample, hits, shift] = key.split('/').map(Number);
    const grid = Array.from({ length: 32 }, (_, index) => sample === 0 ? 0 : sample === 1 ? 127
      : sample === 2 ? index * 7 % 128 : index % 2 === 0 ? 1 : 79);
    for (const patternFlip of [false, true]) {
      assert.deepEqual(chordVelocityPattern({ useCustomVelocities: true, customVelocities: grid,
        hitsPerPattern: hits, patternShift: shift, patternFlip }), expected, key);
    }
  }
  assert.deepEqual(chordVelocityPattern({ useCustomVelocities: true, hitsPerPattern: 3 }), [79, 79, 79]);
  assert.equal(chordVelocityPattern({ customVelocities: Array(32).fill(127) }), undefined);
});

test('custom velocities replace dynamics without changing rhythm timing and zero hits remain silent', () => {
  const { service, load } = fixture();
  const { generateChordPart } = load('core/music/chord-generator.ts');
  const { chordRhythmMask } = load('core/music/rhythm-pattern.ts');
  const base = service.project().chords;
  const generate = (patch = {}) => generateChordPart(42n, 'C', 'major', [1, 4, 5], { ...base, ...patch });
  const original = generate();
  assert.deepEqual(generate({ useCustomVelocities: false, customVelocities: Array(32).fill(0) }), original);
  assert.deepEqual(generate({ customVelocities: Array(32).fill(127) }), original);
  const grid = Array.from({ length: 32 }, (_, index) => index % 3 === 0 ? 0 : index * 7 % 128);
  const patch = { rhythm: 'euclid', hitsPerPattern: 5, euclideanPulses: 3, patternShift: 1, patternFlip: true,
    useCustomVelocities: true, customVelocities: grid, noteLengthPercent: 75, velocityMin: 110, velocityMax: 110 };
  const mask = chordRhythmMask(patch);
  const notes = generate(patch);
  assert.ok(notes.length > 0);
  assert.ok(notes.every(note => note.velocity < 110), 'custom values are not clamped to random velocity bounds');
  for (const note of notes) {
    const subdivision = Math.round((note.startBeat % 4) / (4 / 5));
    assert.equal(mask[subdivision], 1);
    assert.equal(note.velocity, grid[subdivision]);
    assert.ok(Math.abs(note.durationBeats - 4 / 5 * .75) < 1e-12);
  }
  assert.deepEqual(generate({ ...patch, customVelocities: Array(32).fill(0) }), []);
  assert.deepEqual(generate({ ...patch, chordSpanFill: 'ALL', fillFlip: true }), []);
  assert.deepEqual(generate({ useCustomVelocities: true }).map(note => note.velocity), original.map(() => 79));
});

test('custom masks match 1152 production Java list rotations and flipped complements', () => {
  const { load } = fixture();
  const { customPatternMask, chordRhythmMask } = load('core/music/rhythm-pattern.ts');
  const fixtures = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'fixtures/custom-pattern.java.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(Object.keys(fixtures).length, 1152);
  for (const [key, expected] of Object.entries(fixtures)) {
    const [sample, hits, shift] = key.split('/').map(Number);
    const grid = Array.from({ length: 32 }, (_, slot) => sample === 0 ? 0 : sample === 1 ? 1
      : sample === 2 ? +(slot % 5 === 0) : +((slot * 7 + Math.floor(slot / 3)) % 11 < 4));
    assert.deepEqual(customPatternMask(grid, hits, shift), expected, key);
    assert.deepEqual(customPatternMask(grid, hits, shift, true), expected.map((value) => 1 - value), key + ' flip');
  }
  assert.deepEqual(chordRhythmMask({ rhythm: 'custom', hitsPerPattern: 3 }), [1, 1, 1]);
});
