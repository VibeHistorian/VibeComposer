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

test('inspection explains actual precedence, ownership, bounds and mode eligibility', () => {
  const { project, load } = setup();
  const { inspectArrangementVariation: inspect } = load('core/music/arrangement-variation-inspection.ts');
  const track = project.tracks.find(t => t.role === 'melody');
  const section = project.arrangement[1];
  const choice = { trackId: track.id, kind: 'melody-speed', amount: 1, enabled: true };
  assert.equal(inspect(track, section, choice).status, 'active');
  assert.equal(inspect(track, section, { ...choice, enabled: false }).status, 'disabled');
  assert.equal(inspect(track, { ...section, trackPresence: {} }, choice).status, 'absent');
  assert.equal(inspect({ ...track, editedPhrase: [] }, section, choice).status, 'manual');
  assert.equal(inspect(track, { ...section, trackPhrases: { [track.id]: [] } }, choice).status, 'manual');
  assert.equal(inspect({ ...track, generatorSettings: { ...track.generatorSettings, rhythm: 'durations' } }, section, choice).status, 'incompatible');
  assert.equal(inspect({ ...track, generatorSettings: { ...track.generatorSettings, speed: 100 } }, section, choice).status, 'unchanged');
  const overridden = inspect(track, { ...section, rolePartOverrides: { melody: { speed: 70 } }, trackPartOverrides: { [track.id]: { speed: 80 } } }, choice);
  assert.equal(overridden.status, 'overridden');
  assert.equal(overridden.changes[0].effective, 80);
  assert.equal(overridden.changes[0].source, 'Section track override');
});

test('draft rerolls and resets preserve structure, presence and choices outside a role/track scope', () => {
  const { project, load, engine } = setup();
  const drafts = load('core/project/arrangement-variation-draft.ts');
  const section = project.arrangement[1], melody = project.tracks.find(t => t.role === 'melody');
  section.variations = engine.planSectionVariations(project.tracks, project.arrangement, 1, 1n, { chance: 100, maxChanges: 4 });
  const before = structuredClone(project);
  const scope = { sectionId: section.id, trackId: melody.id };
  const initial = drafts.initialVariationDraft(project, scope);
  const others = initial[section.id].choices.filter(c => c.trackId !== melody.id);
  const draft = drafts.rerollVariationDraft(project, scope, initial, 2n);
  assert.deepEqual(draft[section.id].choices.filter(c => c.trackId !== melody.id), others);
  assert.ok(draft[section.id].choices.length <= 4);
  assert.deepEqual(project, before, 'draft work never mutates the project');
  const reset = drafts.resetVariationDraft(project, scope, draft);
  assert.deepEqual(reset[section.id].choices, others);
  const roleScope = { sectionId: section.id, role: 'bass' };
  const bassIds = new Set(project.tracks.filter(track => track.role === 'bass').map(track => track.id));
  const roleDraft = drafts.rerollVariationDraft(project, roleScope, initial, 4n);
  assert.deepEqual(roleDraft[section.id].choices.filter(choice => !bassIds.has(choice.trackId)),
    initial[section.id].choices.filter(choice => !bassIds.has(choice.trackId)));
  const whole = drafts.rerollVariationDraft(project, {}, drafts.initialVariationDraft(project, {}), 3n);
  assert.equal(Object.keys(whole).length, project.arrangement.length);
  assert.deepEqual(project, before);
});

test('draft Apply commits once, preserves non-variation state, rejects stale drafts and restores history', () => {
  const { project, service, load } = setup();
  service.importProjectJson(JSON.stringify(project));
  const expected = service.project(), before = service.exportProjectJson();
  const drafts = load('core/project/arrangement-variation-draft.ts');
  const draft = drafts.rerollVariationDraft(expected, {}, drafts.initialVariationDraft(expected, {}), 5n);
  assert.equal(service.exportProjectJson(), before);
  assert.equal(service.applyArrangementVariationDraft(expected, draft), 'changed');
  assert.deepEqual(service.project().arrangement.map(({ variations, ...rest }) => rest), expected.arrangement.map(({ variations, ...rest }) => rest));
  const saved = service.exportProjectJson();
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.redo(); assert.equal(service.exportProjectJson(), saved);
  assert.equal(service.applyArrangementVariationDraft(expected, draft), 'invalid');
  assert.equal(service.exportProjectJson(), saved);
  assert.equal(service.applyArrangementVariationDraft(service.project(), { missing: undefined }), 'invalid');
  const invalid = structuredClone(draft);
  invalid['section-2'].choices[0].amount = 99;
  assert.equal(service.applyArrangementVariationDraft(service.project(), invalid), 'invalid');
  assert.equal(service.exportProjectJson(), saved);
});

test('dialog keeps reroll, toggle and reset local until Apply, and rejects stale Apply', () => {
  const { service, load } = fixture({ contextualMelody: false, dialogData: {} });
  const { ArrangementVariationsDialogComponent: Dialog } = load('features/arrangement/arrangement-variations-dialog.component.ts');
  const dialog = new Dialog();
  let closed = false;
  dialog.dialog = { close() { closed = true; } };
  const before = service.exportProjectJson();
  dialog.reroll();
  assert.equal(service.exportProjectJson(), before);
  const row = dialog.rows().find(row => row.choices.length);
  assert.ok(row);
  const choice = row.choices[0].inspection.choice;
  dialog.toggle(row.section.id, choice.trackId, { target: { checked: false } });
  assert.equal(dialog.draft()[row.section.id].choices.find(c => c.trackId === choice.trackId).enabled, false);
  assert.equal(service.exportProjectJson(), before);
  dialog.reset(); assert.ok(Object.values(dialog.draft()).every(value => value === undefined));
  dialog.reroll(); dialog.apply();
  assert.equal(closed, true);
  assert.notEqual(service.exportProjectJson(), before);
  service.undo(); assert.equal(service.exportProjectJson(), before);
  const staleDialog = new Dialog();
  staleDialog.dialog = { close() { throw new Error('Stale dialog must remain open'); } };
  staleDialog.reroll(); service.updateSettings({ name: 'Project changed' });
  const changed = service.exportProjectJson();
  staleDialog.apply(); assert.equal(service.exportProjectJson(), changed);
});

test('automatic variation policy is optional, validated, detached and persisted without erasing choices', () => {
  const { service, project, load } = setup();
  service.importProjectJson(JSON.stringify(project));
  service.randomizeArrangementVariations(undefined, undefined, 7n);
  const choices = structuredClone(service.project().arrangement.map(s => s.variations));
  service.updateSettings({ arrangementVariationGeneration: { enabled: true, policy: { chance: 65, maxChanges: 3 } } });
  const saved = service.exportProjectJson();
  service.updateSettings({ arrangementVariationGeneration: { enabled: false, policy: { chance: 65, maxChanges: 3 } } });
  assert.deepEqual(service.project().arrangement.map(s => s.variations), choices);
  service.undo(); assert.equal(service.exportProjectJson(), saved);
  assert.equal(service.importProjectJson(saved), true);
  assert.deepEqual(service.project().arrangementVariationGeneration, { enabled: true, policy: { chance: 65, maxChanges: 3 } });
  const codec = load('core/project/project-codec.ts'), chances = load('core/project/section-type-chances.ts').DEFAULT_SECTION_TYPE_CHANCES;
  const input = JSON.parse(saved), decoded = codec.decodeProject(input, chances, () => .5);
  input.arrangementVariationGeneration.policy.chance = 99;
  assert.equal(decoded.arrangementVariationGeneration.policy.chance, 65);
  for (const invalid of [null, [], { enabled: 'yes', policy: { chance: 100, maxChanges: 2 } },
    { enabled: true, policy: { chance: 101, maxChanges: 2 } }, { enabled: true, policy: { chance: 100, maxChanges: 5 } },
    { enabled: true, policy: { chance: 100, maxChanges: 2 }, unknown: true }]) {
    assert.equal(codec.decodeProject({ ...project, arrangementVariationGeneration: invalid }, chances, () => .5), undefined);
    assert.equal(codec.isProject({ ...project, arrangementVariationGeneration: invalid }, chances, () => .5), false);
  }
});

test('new arrangement and insertion generate optional variations without shifting structure, presence or track streams', () => {
  const { project, commands, load } = setup();
  const chances = load('core/project/section-type-chances.ts').DEFAULT_SECTION_TYPE_CHANCES;
  const enabled = { ...project, arrangementVariationGeneration: { enabled: true, policy: { chance: 100, maxChanges: 3 } } };
  const plain = commands.randomizeArrangement(project, chances, 9223372036854775807n);
  const varied = commands.randomizeArrangement(enabled, chances, 9223372036854775807n);
  assert.deepEqual(varied.arrangement.map(({ variations, ...section }) => section), plain.arrangement);
  assert.equal(varied.tracks, project.tracks);
  assert.ok(varied.arrangement.every(s => s.variations && s.variations.policy.maxChanges === 3));
  assert.ok(varied.arrangement.some(s => s.variations.choices.length));
  assert.ok(plain.arrangement.every(s => s.variations === undefined));
  const inserted = commands.addSection(enabled, 'BUILDUP2', 0, chances, () => .5);
  assert.ok(inserted.arrangement[1].variations);
  assert.deepEqual(inserted.arrangement.filter((_, i) => i !== 1), project.arrangement);
  assert.equal(commands.addSection(project, 'BUILDUP2', 0, chances, () => .5).arrangement[1].variations, undefined);
  const without = commands.randomizeArrangement({ ...varied, arrangementVariationGeneration: { ...enabled.arrangementVariationGeneration, enabled: false } }, chances, 9n);
  assert.ok(without.arrangement.every(s => s.variations === undefined));
});

test('Compose respects automatic/manual arrangement policy while Regenerate and presence-only rerolls retain choices', () => {
  const { service, project, load } = setup();
  const defaults = load('core/music/track-generation.ts').DEFAULT_TRACK_POLICIES;
  project.trackGenerationPolicies = Object.fromEntries(Object.entries(defaults).map(([role, policy]) => [role,
    { ...policy, onCompose: false, ...(role === 'melody' ? { patternsOnCompose: false, targetsOnCompose: false, onRegenerate: false } : {}) }]));
  project.randomizeHarmonyOnCompose = false; project.randomizeTempoOnCompose = false; project.randomizeTransposeOnCompose = false;
  project.randomizeArrangementOnCompose = true; project.arrangementManual = true;
  project.arrangementVariationGeneration = { enabled: true, policy: { chance: 100, maxChanges: 2 } };
  assert.equal(service.importProjectJson(JSON.stringify(project)), true);
  const before = service.exportProjectJson();
  assert.equal(service.generateComposition('compose'), 'changed');
  assert.deepEqual(service.project().arrangement.map(s => [s.id, s.type, s.measures]), project.arrangement.map(s => [s.id, s.type, s.measures]));
  assert.ok(service.project().arrangement.every(s => s.variations));
  const choices = structuredClone(service.project().arrangement.map(s => s.variations));
  const composed = service.exportProjectJson();
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.redo(); assert.equal(service.exportProjectJson(), composed);
  service.generateComposition('regenerate');
  assert.deepEqual(service.project().arrangement.map(s => s.variations), choices);
  service.randomizeArrangementPresence(); assert.deepEqual(service.project().arrangement.map(s => s.variations), choices);
  service.updateSettings({ arrangementVariationGeneration: { enabled: false, policy: { chance: 100, maxChanges: 2 } } });
  service.generateComposition('compose'); assert.deepEqual(service.project().arrangement.map(s => s.variations), choices);
  service.updateSettings({ arrangementManual: false }); service.generateComposition('compose');
  assert.ok(service.project().arrangement.every(s => s.variations === undefined));
  service.updateSettings({ arrangementVariationGeneration: { enabled: true, policy: { chance: 100, maxChanges: 2 } } });
  service.generateComposition('compose'); assert.ok(service.project().arrangement.every(s => s.variations));
});

test('presence and variations are planned together, committed atomically and reject incomplete presence drafts', () => {
  const { project, service, load, phrase, midi } = setup();
  service.importProjectJson(JSON.stringify(project));
  const drafts = load('core/project/arrangement-variation-draft.ts');
  const chances = load('core/project/section-type-chances.ts').DEFAULT_SECTION_TYPE_CHANCES;
  const expected = service.project(), before = service.exportProjectJson();
  const presence = drafts.rerollVariationPresence(expected, chances, 123n);
  assert.deepEqual(presence, drafts.rerollVariationPresence(expected, chances, 123n));
  const candidate = drafts.projectWithVariationPresence(expected, presence);
  const variations = drafts.rerollVariationDraft(candidate, {}, drafts.initialVariationDraft(expected, {}), 123n);
  assert.ok(candidate.arrangement.some(s => JSON.stringify(s.trackPresence) !== JSON.stringify(expected.arrangement.find(p => p.id === s.id).trackPresence)));
  assert.ok(candidate.arrangement.every(s => variations[s.id].choices.every(c => s.trackPresence[c.trackId])));
  assert.equal(service.exportProjectJson(), before);
  assert.equal(service.applyArrangementVariationDraft(expected, variations, { presence }), 'changed');
  const resolvedCandidate = { ...candidate, arrangement: candidate.arrangement.map(s => ({ ...s, variations: variations[s.id] })) };
  for (const track of expected.tracks) assert.deepEqual(phrase.layOutTrackPhrase(service.project(), track), phrase.layOutTrackPhrase(resolvedCandidate, track));
  assert.deepEqual(midi.generateCompositionMidi(service.project()), midi.generateCompositionMidi(resolvedCandidate));
  const after = service.exportProjectJson();
  assert.deepEqual(service.project().arrangement.map(({ trackPresence, variations, ...rest }) => rest),
    expected.arrangement.map(({ trackPresence, variations, ...rest }) => rest));
  service.undo(); assert.equal(service.exportProjectJson(), before);
  assert.equal(service.applyArrangementVariationDraft(service.project(), variations, { presence: {} }), 'invalid');
  const incomplete = structuredClone(presence); delete incomplete['section-1'][expected.tracks[0].id];
  assert.equal(service.applyArrangementVariationDraft(service.project(), variations, { presence: incomplete }), 'invalid');
  assert.equal(service.exportProjectJson(), before);
  service.redo(); assert.equal(service.exportProjectJson(), after);
});
