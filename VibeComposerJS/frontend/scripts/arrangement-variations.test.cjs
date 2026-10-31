const assert = require('node:assert/strict');
const test = require('node:test');
const { fixture } = require('./support/project-fixture.cjs');

function setup() {
  const f = fixture({ contextualMelody: false });
  const project = structuredClone(f.service.project());
  project.arrangement = ['INTRO', 'BUILDUP2', 'CLIMAX', 'OUTRO'].map((type, index) => ({
    id: `section-${index + 1}`, type, measures: 4,
    trackPresence: Object.fromEntries(project.tracks.map(t => [t.id, true])),
  }));
  return { ...f, project, engine: f.load('core/music/arrangement-variations.ts'),
    settings: f.load('core/music/part-settings.ts'), commands: f.load('core/project/project-arrangement-commands.ts') };
}

test('variation planning is exact-seed deterministic, bounded and independent of track traversal', () => {
  const { project, engine } = setup();
  for (let seed = 0n; seed < 30n; seed++) {
    const make = tracks => engine.planSectionVariations(tracks, project.arrangement, 1, 9223372036854775807n - seed);
    const result = make(project.tracks);
    assert.deepEqual(result, make(project.tracks));
    assert.deepEqual(result, make([...project.tracks].reverse()));
    assert.ok(result.choices.length <= 2);
    assert.equal(new Set(result.choices.map(c => c.trackId)).size, result.choices.length);
    assert.ok(result.choices.every(c => c.amount === 1));
    assert.ok(engine.planSectionVariations(project.tracks, project.arrangement, 0, seed).choices.every(c => c.amount === -1));
    assert.ok(engine.planSectionVariations(project.tracks, project.arrangement, 2, seed).choices.every(c =>
      c.amount === (['bass-weight', 'chord-space'].includes(c.kind) ? 1 : -1)), 'release can reinforce weight while reducing activity');
  }
  assert.deepEqual(engine.planSectionVariations(project.tracks, project.arrangement, 1, 1n, { chance: 0, maxChanges: 4 }).choices, []);
  assert.deepEqual(engine.planSectionVariations(project.tracks, project.arrangement, 1, 1n, { chance: 100, maxChanges: 0 }).choices, []);
});

test('planner excludes absent, locked, manual and explicitly customized targets', () => {
  const { project, engine } = setup();
  const melody = project.tracks.find(t => t.role === 'melody');
  const tracks = [{ ...melody, editedPhrase: [] }];
  const section = project.arrangement[1];
  assert.deepEqual(engine.planSectionVariations(tracks, [section], 0, 1n).choices, []);
  assert.ok(engine.planSectionVariations(tracks, [{ ...section, generatedTrackIds: [melody.id] }], 0, 1n).choices.length);
  for (const change of [{ trackPresence: {} }, { trackPhrases: { [melody.id]: [] } },
    { rolePartOverrides: { melody: { speed: 80, targetDirection: 'ASC' } } }]) {
    assert.deepEqual(engine.planSectionVariations([melody], [{ ...section, ...change }], 0, 1n).choices, []);
  }
  assert.deepEqual(engine.planSectionVariations([{ ...melody, rerollLocked: true }], [section], 0, 1n).choices, []);
});

test('resolution respects explicit precedence and incompatible rhythm edits', () => {
  const { project, settings } = setup();
  const arp = project.tracks.find(t => t.role === 'arpeggio');
  const track = { ...arp, generatorSettings: { ...arp.generatorSettings, rhythm: 'full', hitsPerPattern: 8 } };
  const section = { ...project.arrangement[1], variations: { seed: '1', policy: { chance: 100, maxChanges: 2 },
    choices: [{ trackId: arp.id, kind: 'arp-activity', amount: 1, enabled: true }] } };
  assert.equal(settings.resolvePartTrack(track, section).generatorSettings.hitsPerPattern, 16);
  assert.equal(settings.resolvePartTrack(track, { ...section, rolePartOverrides: { arpeggio: { hitsPerPattern: 4 } } }).generatorSettings.hitsPerPattern, 4);
  assert.equal(settings.resolvePartTrack(track, { ...section, rolePartOverrides: { arpeggio: { hitsPerPattern: 4 } },
    trackPartOverrides: { [arp.id]: { hitsPerPattern: 2 } } }).generatorSettings.hitsPerPattern, 2);
  assert.equal(settings.resolvePartTrack(track, { ...section, rolePartOverrides: { arpeggio: { rhythm: 'melody' } } }).generatorSettings.hitsPerPattern, 8);
  assert.equal(settings.resolvePartTrack(track, { ...section, trackPresence: {} }).generatorSettings.hitsPerPattern, 8);
});

test('variation commands persist, undo atomically, duplicate and prune deleted identities', () => {
  const { service, project, commands, stored } = setup();
  assert.equal(service.importProjectJson(JSON.stringify(project)), true);
  const before = service.exportProjectJson();
  assert.equal(service.randomizeArrangementVariations('section-2', undefined, 9223372036854775807n), 'changed');
  let saved = service.exportProjectJson();
  assert.equal(JSON.parse(stored.get('vibecomposer.project.v12')).arrangement[1].variations.seed, '9223372036854775807');
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.redo(); assert.equal(service.exportProjectJson(), saved);
  assert.equal(service.importProjectJson(saved), true);
  assert.deepEqual(JSON.parse(service.exportProjectJson()), JSON.parse(saved));
  saved = service.exportProjectJson();
  const s = service.project().arrangement[1], choice = s.variations.choices[0];
  assert.ok(choice);
  assert.equal(service.setArrangementVariationEnabled(s.id, choice.trackId, false), 'changed');
  assert.equal(service.project().arrangement[1].variations.choices[0].enabled, false);
  service.undo(); assert.equal(service.exportProjectJson(), saved);
  const copy = commands.duplicateSection(service.project(), 1).arrangement[2];
  assert.deepEqual(copy.variations, s.variations);
  assert.notEqual(copy.variations, s.variations);
  service.removeTrack(choice.trackId);
  assert.ok(service.project().arrangement.every(s => !s.variations?.choices.some(c => c.trackId === choice.trackId)));
  service.undo(); assert.equal(service.exportProjectJson(), saved);
  service.resetArrangementVariations(s.id);
  assert.equal(service.project().arrangement[1].variations, undefined);
  service.undo(); assert.equal(service.exportProjectJson(), saved);
  assert.equal(service.randomizeArrangementVariations('missing'), 'invalid');
  assert.equal(service.randomizeArrangementVariations(s.id, { chance: 101, maxChanges: 2 }), 'invalid');
});

test('invalid persisted variation identities, operations, seeds and budgets are rejected', () => {
  const { engine, project, service } = setup();
  const v = engine.planSectionVariations(project.tracks, project.arrangement, 1, 1n);
  assert.equal(engine.validSectionVariations(v, project.tracks), true);
  for (const patch of [{ seed: '9223372036854775808' }, { seed: '1.5' }, { policy: { chance: -1, maxChanges: 2 } },
    { choices: [v.choices[0], v.choices[0]] }, { choices: [{ ...v.choices[0], trackId: 'missing' }] },
    { choices: [{ ...v.choices[0], kind: 'unknown' }] }, { choices: [{ ...v.choices[0], amount: 99 }] }]) {
    assert.equal(engine.validSectionVariations({ ...v, ...patch }, project.tracks), false);
    const before = service.exportProjectJson();
    const warn = console.warn;
    const warnings = [];
    console.warn = (...args) => warnings.push(args);
    try {
      const candidate = { ...project, arrangement: [{ ...project.arrangement[1], variations: { ...v, ...patch } }] };
      assert.equal(service.importProjectJson(JSON.stringify(candidate)), false);
      assert.equal(service.exportProjectJson(), before);
      assert.match(JSON.stringify(warnings), /variations/);
    } finally { console.warn = warn; }
  }
});

test('shared phrase output and melody dependency rhythm consume variations while manual notes retain ownership', () => {
  const { project, phrase, settings } = setup();
  const melody = project.tracks.find(t => t.role === 'melody');
  const base = { ...melody, generatorSettings: { ...melody.generatorSettings, speed: 50 } };
  const section = { ...project.arrangement[1], variations: { seed: '1', policy: { chance: 100, maxChanges: 2 },
    choices: [{ trackId: base.id, kind: 'melody-speed', amount: 1, enabled: true }] } };
  const varied = { ...project, tracks: [base], arrangement: [section] };
  const explicit = { ...varied, arrangement: [{ ...section, variations: undefined, trackPartOverrides: { [base.id]: { speed: 65 } } }] };
  assert.deepEqual(phrase.layOutTrackPhrase(varied, base), phrase.layOutTrackPhrase(explicit, base));
  assert.deepEqual(phrase.sectionMelodyContext(varied, section), phrase.sectionMelodyContext(explicit, explicit.arrangement[0]));
  const original = { ...varied, arrangement: [{ ...section, variations: undefined }] };
  assert.notDeepEqual(phrase.layOutTrackPhrase(varied, base), phrase.layOutTrackPhrase(original, base));
  const manual = { ...base, editedPhrase: [{ id: 'manual', midi: 60, startBeat: 0, durationBeats: 1, velocity: 90 }] };
  assert.deepEqual(phrase.layOutTrackPhrase({ ...varied, tracks: [manual] }, manual),
    phrase.layOutTrackPhrase({ ...original, tracks: [manual] }, manual));
  const ranged = { ...base, phraseEdits: [{ startBeat: 0, endBeat: 4,
    notes: [{ id: 'range', midi: 62, startBeat: 0, durationBeats: 1, velocity: 80 }] }] };
  const rangeNotes = phrase.layOutTrackPhrase({ ...varied, tracks: [ranged] }, ranged);
  assert.deepEqual(rangeNotes.filter(n => n.startBeat < 4),
    phrase.layOutTrackPhrase({ ...original, tracks: [ranged] }, ranged).filter(n => n.startBeat < 4));
});
