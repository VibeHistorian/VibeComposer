const assert = require('node:assert/strict');
const test = require('node:test');
const { loadMusic } = require('./music-loader.cjs');
const { decodeProject, isProject } = loadMusic('../project/project-codec.ts');
const { formatProjectJson } = loadMusic('../project/project-json.ts');
const { copyDefaultProject } = loadMusic('../project/project-defaults.ts');
const { projectLoadIssues } = loadMusic('../project/project-load-diagnostics.ts');
const { DEFAULT_SECTION_TYPE_CHANCES } = loadMusic('../project/section-type-chances.ts');
const commands = loadMusic('../project/project-arrangement-commands.ts');

function freeze(value) {
  if (value && typeof value === 'object') {
    Object.values(value).forEach(freeze);
    Object.freeze(value);
  }
  return value;
}

test('compact project exports preserve JSON data and keep numeric arrays and manual notes inline', () => {
  const input = copyDefaultProject(DEFAULT_SECTION_TYPE_CHANCES, () => 0.5);
  input.name = 'Quotes " and brackets [1, 2] and newline\nwith backslash \\';
  input.seed = '9223372036854775807';
  const notes = [
    { id: 'n"1', midi: 48, startBeat: 0, durationBeats: 0.5, velocity: 90 },
    { id: 'n2', midi: 55, startBeat: 0.5, durationBeats: 1, velocity: 85 },
  ];
  input.editedPhrases = { bass: notes };
  const track = input.tracks.find(track => track.role === 'bass');
  track.editedPhrase = notes;
  input.arrangement[0].trackPhrases = { [track.id]: notes };
  input.arrangement[0].rolePartOverrides = {};
  input.arrangement[0].generatedTrackIds = [];
  input.randomizeTempoOnCompose = undefined;
  const originalJson = JSON.stringify(input);
  const exported = formatProjectJson(freeze(input));
  assert.deepEqual(JSON.parse(exported).tracks, JSON.parse(originalJson).tracks);
  assert.equal(JSON.stringify(input), originalJson);
  assert.ok(exported.includes(`"progression": [${input.progression.join(', ')}]`));
  assert.ok(exported.includes('"generatedTrackIds": []'));
  assert.ok(exported.includes('"rolePartOverrides": {}'));
  const noteLines = exported.split('\n').filter(line => line.includes('"startBeat":'));
  assert.equal(noteLines.length, 6, 'all three phrase scopes contain one line per note');
  for (const line of noteLines) {
    const note = JSON.parse(line.trim().replace(/,$/, ''));
    assert.deepEqual(note, notes.find(item => item.id === note.id));
  }
  const decoded = decodeProject(JSON.parse(exported), DEFAULT_SECTION_TYPE_CHANCES, () => 0.5);
  assert.ok(decoded);
  assert.deepEqual(decoded, decodeProject(JSON.parse(originalJson), DEFAULT_SECTION_TYPE_CHANCES, () => 0.5));
  assert.deepEqual(decoded.tracks.find(item => item.id === track.id).editedPhrase, notes);
});

test('project decoding works without Angular or storage and preserves import defaults and exact seeds', () => {
  const input = copyDefaultProject(DEFAULT_SECTION_TYPE_CHANCES, () => 0.5);
  input.seed = '9223372036854775807';
  input.schemaVersion = 'informational';
  // Optional metadata is defaulted at the import boundary.
  delete input.transposeSemitones;
  freeze(input);

  assert.equal(isProject(input, DEFAULT_SECTION_TYPE_CHANCES, () => 0.5), true);
  const decoded = decodeProject(input, DEFAULT_SECTION_TYPE_CHANCES, () => 0.5);
  assert.ok(decoded);
  assert.equal(decoded.seed, '9223372036854775807');
  assert.equal(decoded.transposeSemitones, 0);
  assert.equal(decoded.tracks.find(track => track.role === 'melody').generatorSettings.algorithm, 'block');
  assert.equal(isProject(decoded, DEFAULT_SECTION_TYPE_CHANCES, () => 0.5), true);
  assert.equal(input.transposeSemitones, undefined);
});

test('decoded local phrases and settings are detached from input and reject dangling references', () => {
  const input = copyDefaultProject(DEFAULT_SECTION_TYPE_CHANCES, () => 0.5);
  const trackId = 'track-bass-1';
  input.arrangement[0].rolePartOverrides = { bass: { transpose: 12 } };
  input.arrangement[0].trackPartOverrides = { [trackId]: { transpose: 0 } };
  input.arrangement[0].trackPhrases = {
    [trackId]: [{ id: 'manual', midi: 48, startBeat: 0, durationBeats: 1, velocity: 90 }],
  };
  const decoded = decodeProject(freeze(input), DEFAULT_SECTION_TYPE_CHANCES, () => 0.5);
  assert.ok(decoded);
  decoded.arrangement[0].trackPhrases[trackId][0].midi = 60;
  decoded.arrangement[0].rolePartOverrides.bass.transpose = -12;
  assert.equal(input.arrangement[0].trackPhrases[trackId][0].midi, 48);
  assert.equal(input.arrangement[0].rolePartOverrides.bass.transpose, 12);

  decoded.arrangement[0].trackPhrases.missing = decoded.arrangement[0].trackPhrases[trackId];
  assert.equal(decodeProject(decoded, DEFAULT_SECTION_TYPE_CHANCES, () => 0.5), undefined);
  assert.equal(isProject(decoded, DEFAULT_SECTION_TYPE_CHANCES, () => 0.5), false);
});

test('decoding uses supplied section preferences while preserving explicitly stored presence', () => {
  const input = copyDefaultProject(DEFAULT_SECTION_TYPE_CHANCES, () => 0.5);
  const chances = Object.fromEntries(Object.keys(DEFAULT_SECTION_TYPE_CHANCES).map(type => [type,
    { melody: 100, bass: 0, chords: 100, arpeggio: 0, drums: 100 },
  ]));
  delete input.arrangement[0].trackPresence;
  input.arrangement[1].trackPresence = Object.fromEntries(input.tracks.map(track => [track.id, false]));
  const decoded = decodeProject(freeze(input), chances, () => 0.5);
  assert.ok(decoded);
  for (const track of decoded.tracks) {
    assert.equal(decoded.arrangement[0].trackPresence[track.id], chances[decoded.arrangement[0].type][track.role] === 100);
    assert.equal(decoded.arrangement[1].trackPresence[track.id], false);
  }
});

test('rejected-project diagnostics return field messages without mutating input', () => {
  const input = copyDefaultProject(DEFAULT_SECTION_TYPE_CHANCES, () => 0.5);
  input.tempoBpm = 0;
  input.seed = '9223372036854775808';
  assert.equal(decodeProject(input, DEFAULT_SECTION_TYPE_CHANCES, () => 0.5), undefined);
  const issues = projectLoadIssues(freeze(input), DEFAULT_SECTION_TYPE_CHANCES, () => 0.5);
  assert.ok(issues.some(issue => issue.startsWith('project.tempoBpm:')));
  assert.ok(issues.some(issue => issue.startsWith('project.seed:')));
});

test('arrangement candidates preserve immutable ownership and can be validated before committing', () => {
  const input = copyDefaultProject(DEFAULT_SECTION_TYPE_CHANCES, () => 0.5);
  input.arrangement[0].trackPhrases = {
    'track-bass-1': [{ id: 'manual', midi: 48, startBeat: 0, durationBeats: 1, velocity: 90 }],
  };
  const duplicated = commands.duplicateSection(freeze(input), 0);
  assert.ok(duplicated);
  assert.equal(isProject(duplicated, DEFAULT_SECTION_TYPE_CHANCES, () => 0.5), true);
  assert.equal(duplicated.arrangement.length, input.arrangement.length + 1);
  assert.notEqual(duplicated.arrangement[1].id, input.arrangement[0].id);
  duplicated.arrangement[1].trackPhrases['track-bass-1'][0].midi = 60;
  assert.equal(input.arrangement[0].trackPhrases['track-bass-1'][0].midi, 48);

  const invalid = commands.updateSection(input, 0, { measures: 33 });
  assert.ok(invalid);
  assert.equal(isProject(invalid, DEFAULT_SECTION_TYPE_CHANCES, () => 0.5), false);
  assert.equal(commands.removeSection(input, -1), undefined);
});

test('energy-aware forms are seeded, bounded, resolve anticipation and retain layers on rises', () => {
  const { generateArrangementPlan } = loadMusic('arrangement-plan.ts');
  const { resolveArrangementIntent } = loadMusic('arrangement-intent.ts');
  const forms = new Set();
  for (let seed = 0n; seed < 80n; seed++) {
    const project = copyDefaultProject(DEFAULT_SECTION_TYPE_CHANCES, () => .5);
    project.seed = String(9223372036854775807n - seed);
    const result = commands.randomizeArrangement(project, DEFAULT_SECTION_TYPE_CHANCES);
    assert.deepEqual(result, commands.randomizeArrangement(project, DEFAULT_SECTION_TYPE_CHANCES));
    assert.ok(['INTRO', 'HALF_CHORUS', 'CHORUS2'].includes(result.arrangement[0].type));
    assert.equal(result.arrangement.at(-1).type, 'OUTRO');
    assert.ok(result.arrangement.length <= 32);
    assert.ok(result.arrangement.reduce((n, section) => n + section.measures, 0) <= 128);
    assert.ok(result.arrangement.some(section => section.type === 'CLIMAX'));
    forms.add(result.arrangement.map(section => section.type).join(','));
    for (let index = 0; index < result.arrangement.length; index++) {
      const section = result.arrangement[index];
      const intent = resolveArrangementIntent(result.arrangement, index);
      assert.ok(Object.values(section.trackPresence).some(Boolean));
      if (section.type === 'BUILDUP2') {
        assert.ok(intent.tension > intent.energy);
        assert.equal(resolveArrangementIntent(result.arrangement, index + 1).motion, 'release');
      }
      if (intent.energy >= .65) {
        for (const role of ['bass', 'drums']) {
          assert.ok(project.tracks.some(track => track.role === role && section.trackPresence[track.id]));
        }
      }
      if (index && intent.motion === 'rise') {
        const previous = result.arrangement[index - 1];
        for (const track of project.tracks) {
          if (previous.trackPresence[track.id]) assert.ok(section.trackPresence[track.id]);
        }
      }
    }
    assert.ok(decodeProject(JSON.parse(formatProjectJson(result)), DEFAULT_SECTION_TYPE_CHANCES, () => .5));
    assert.deepEqual(generateArrangementPlan(BigInt(project.seed)), generateArrangementPlan(BigInt(project.seed)));
  }
  assert.ok(forms.size > 10);
});

test('context orchestration handles exclusions, empty projects, and independent tracks', () => {
  const { generateContextualPresence } = loadMusic('arrangement-intent.ts');
  const project = copyDefaultProject(DEFAULT_SECTION_TYPE_CHANCES, () => .5);
  const sections = ['INTRO', 'BUILDUP1', 'BUILDUP2', 'CLIMAX', 'OUTRO'].map(type => ({ type }));
  assert.deepEqual(generateContextualPresence([], sections, DEFAULT_SECTION_TYPE_CHANCES, () => .5), sections.map(() => ({})));
  const excluded = Object.fromEntries(Object.entries(DEFAULT_SECTION_TYPE_CHANCES).map(([type, weights]) =>
    [type, Object.fromEntries(Object.keys(weights).map(role => [role, 0]))]));
  assert.ok(generateContextualPresence(project.tracks, sections, excluded, () => .5)
    .every(presence => Object.values(presence).every(value => !value)));
  const tracks = Array.from({ length: 30 }, (_, i) => ({ ...project.tracks[i % 5], id: `independent-${i}` }));
  const presence = generateContextualPresence(tracks, sections, DEFAULT_SECTION_TYPE_CHANCES, () => .5);
  assert.ok(Object.values(presence[0]).filter(Boolean).length < Object.values(presence[3]).filter(Boolean).length);
  assert.equal(Object.values(presence[3]).filter(Boolean).length, 30);
  assert.ok(Object.values(presence[4]).filter(Boolean).length < Object.values(presence[3]).filter(Boolean).length);
});

test('local orchestration follows actual preceding presence and leaves other sections untouched', () => {
  const { generateContextualPresence } = loadMusic('arrangement-intent.ts');
  const project = copyDefaultProject(DEFAULT_SECTION_TYPE_CHANCES, () => .5);
  const prior = Object.fromEntries(project.tracks.map(track => [track.id, track.role === 'arpeggio']));
  const untouched = Object.fromEntries(project.tracks.map(track => [track.id, false]));
  const sections = [
    { type: 'VERSE1', trackPresence: prior },
    { type: 'BUILDUP1', trackPresence: untouched },
    { type: 'CLIMAX', trackPresence: untouched },
  ];
  const presence = generateContextualPresence(project.tracks, sections, DEFAULT_SECTION_TYPE_CHANCES, () => .5, 1);
  assert.strictEqual(presence[0], prior);
  assert.strictEqual(presence[2], untouched);
  assert.equal(presence[1]['track-arpeggio-1'], true);
});


test('seeded openings include quiet introductions and high-energy hooks', () => {
  const { generateArrangementPlan } = loadMusic('arrangement-plan.ts');
  const openings = new Set();
  for (let seed = 0n; seed < 200n; seed++) {
    const plan = generateArrangementPlan(seed);
    openings.add(plan[0].type);
    assert.equal(plan[1].type, 'VERSE1', 'opening settles into the first cycle');
    assert.deepEqual(plan, generateArrangementPlan(seed));
  }
  assert.deepEqual([...openings].sort(), ['CHORUS2', 'HALF_CHORUS', 'INTRO']);
});
