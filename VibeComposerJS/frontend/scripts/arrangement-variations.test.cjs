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

test('open variation dialogs read presence behavior live without publishing preferences through Apply', () => {
  const { service, load, uiPreferences } = fixture({ contextualMelody: false, dialogData: {} });
  const Dialog = load('features/arrangement/arrangement-variations-dialog.component.ts').ArrangementVariationsDialogComponent;
  const dialog = new Dialog(), before = service.exportProjectJson();
  uiPreferences.setArrangementBehavior('rerollPresenceWithVariations', true);
  assert.equal(dialog.stale(), false, 'UI preference changes do not invalidate project drafts');
  dialog.reroll(); assert.ok(dialog.presenceDraft());
  assert.equal(service.exportProjectJson(), before);
  uiPreferences.setArrangementBehavior('rerollPresenceWithVariations', false);
  dialog.reroll(); assert.equal(dialog.presenceDraft(), undefined);
  assert.equal(service.exportProjectJson(), before);
});

test('musical variation policy is validated and detached while generation behavior stays in UI preferences', () => {
  const { service, project, load, uiPreferences } = setup();
  service.importProjectJson(JSON.stringify(project));
  service.updateSettings({ arrangementVariationPolicy: { chance: 65, maxChanges: 3 } });
  service.randomizeArrangementVariations(undefined, undefined, 7n);
  const saved = service.exportProjectJson(), position = service.historyPosition();
  uiPreferences.setArrangementBehavior('generateArrangementVariations', true);
  uiPreferences.setArrangementBehavior('rerollPresenceWithVariations', true);
  assert.equal(service.exportProjectJson(), saved);
  assert.equal(service.historyPosition(), position);
  assert.equal(service.importProjectJson(saved), true);
  assert.equal(uiPreferences.generateArrangementVariations(), true, 'project import never changes app behavior');
  const codec = load('core/project/project-codec.ts'), chances = load('core/project/section-type-chances.ts').DEFAULT_SECTION_TYPE_CHANCES;
  const input = JSON.parse(saved), decoded = codec.decodeProject(input, chances, () => .5);
  input.arrangementVariationPolicy.chance = 99;
  assert.equal(decoded.arrangementVariationPolicy.chance, 65);
  assert.equal(Object.hasOwn(decoded, 'arrangementVariationGeneration'), false);
  for (const invalid of [null, [], { chance: 101, maxChanges: 2 }, { chance: 100, maxChanges: 5 }, { chance: 100, maxChanges: 2, enabled: true }]) {
    assert.equal(codec.decodeProject({ ...project, arrangementVariationPolicy: invalid }, chances, () => .5), undefined);
    assert.equal(codec.isProject({ ...project, arrangementVariationPolicy: invalid }, chances, () => .5), false);
  }
});

test('new arrangement and insertion consume explicit behavior inputs without shifting structure, presence or track streams', () => {
  const { project, commands, load } = setup();
  const chances = load('core/project/section-type-chances.ts').DEFAULT_SECTION_TYPE_CHANCES;
  const configured = { ...project, arrangementVariationPolicy: { chance: 100, maxChanges: 3 } };
  const plain = commands.randomizeArrangement(configured, chances, 9223372036854775807n, false);
  const varied = commands.randomizeArrangement(configured, chances, 9223372036854775807n, true);
  assert.deepEqual(varied.arrangement.map(({ variations, ...section }) => section), plain.arrangement);
  assert.equal(varied.tracks, project.tracks);
  assert.ok(varied.arrangement.every(s => s.variations && s.variations.policy.maxChanges === 3));
  assert.ok(varied.arrangement.some(s => s.variations.choices.length));
  assert.ok(plain.arrangement.every(s => s.variations === undefined));
  const inserted = commands.addSection(configured, 'BUILDUP2', 0, chances, () => .5, true);
  assert.ok(inserted.arrangement[1].variations);
  assert.deepEqual(inserted.arrangement.filter((_, i) => i !== 1), project.arrangement);
  assert.equal(commands.addSection(configured, 'BUILDUP2', 0, chances, () => .5, false).arrangement[1].variations, undefined);
});

test('Compose, insertion and toolbar rerolls read UI behavior immediately while Regenerate retains choices', () => {
  const { service, project, load, uiPreferences } = setup();
  const defaults = load('core/music/track-generation.ts').DEFAULT_TRACK_POLICIES;
  project.trackGenerationPolicies = Object.fromEntries(Object.entries(defaults).map(([role, policy]) => [role,
    { ...policy, onCompose: false, ...(role === 'melody' ? { patternsOnCompose: false, targetsOnCompose: false, onRegenerate: false } : {}) }]));
  project.randomizeHarmonyOnCompose = false; project.randomizeTempoOnCompose = false; project.randomizeTransposeOnCompose = false;
  project.randomizeArrangementOnCompose = true; project.arrangementManual = true;
  project.arrangementVariationPolicy = { chance: 100, maxChanges: 2 };
  assert.equal(service.importProjectJson(JSON.stringify(project)), true);
  const before = service.exportProjectJson();
  uiPreferences.setArrangementBehavior('generateArrangementVariations', true);
  assert.equal(service.exportProjectJson(), before, 'no popup Apply needed and no project edits');
  assert.equal(service.generateComposition('compose'), 'changed');
  assert.deepEqual(service.project().arrangement.map(s => [s.id, s.type, s.measures]), project.arrangement.map(s => [s.id, s.type, s.measures]));
  assert.ok(service.project().arrangement.every(s => s.variations));
  const choices = structuredClone(service.project().arrangement.map(s => s.variations));
  const composed = service.exportProjectJson();
  service.undo(); assert.equal(service.exportProjectJson(), before);
  assert.equal(uiPreferences.generateArrangementVariations(), true, 'project undo preserves app behavior');
  service.redo(); assert.equal(service.exportProjectJson(), composed);
  service.generateComposition('regenerate'); assert.deepEqual(service.project().arrangement.map(s => s.variations), choices);
  service.randomizeArrangementPresence(); assert.deepEqual(service.project().arrangement.map(s => s.variations), choices);
  uiPreferences.setArrangementBehavior('generateArrangementVariations', false);
  service.generateComposition('compose'); assert.deepEqual(service.project().arrangement.map(s => s.variations), choices);
  service.updateSettings({ arrangementManual: false }); service.generateComposition('compose');
  assert.ok(service.project().arrangement.every(s => s.variations === undefined));
  uiPreferences.setArrangementBehavior('generateArrangementVariations', true);
  service.generateComposition('compose'); assert.ok(service.project().arrangement.every(s => s.variations));
  service.randomizeArrangement(); assert.ok(service.project().arrangement.every(s => s.variations));
  service.addSection('BUILDUP2', 0); assert.ok(service.project().arrangement[1].variations);
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

test('neighbor context distinguishes recovery from anticipation at the same verse energy', () => {
  const { project, load } = setup();
  const { resolveVariationContext } = load('core/music/arrangement-variation-context.ts');
  const sections = types => types.map((type, i) => ({ ...project.arrangement[0], id: `context-${i}`, type }));
  const recovering = resolveVariationContext(project.tracks, sections(['CLIMAX', 'VERSE1', 'CHILL']), 1);
  const anticipating = resolveVariationContext(project.tracks, sections(['INTRO', 'VERSE1', 'BUILDUP2']), 1);
  assert.equal(recovering.intent.energy, anticipating.intent.energy);
  assert.equal(recovering.gesture, 'thin');
  assert.equal(anticipating.gesture, 'build-pulse');
  assert.equal(resolveVariationContext(project.tracks, sections(['INTRO', 'VERSE2', 'CHORUS1']), 1).gesture, 'build-pulse',
    'a quarter-scale rise is not lost to floating-point rounding');
  assert.equal(resolveVariationContext(project.tracks, sections(['INTRO', 'VERSE1']), 0).gesture, 'establish');
  assert.equal(resolveVariationContext(project.tracks, sections(['BUILDUP2', 'CHORUS2', 'OUTRO']), 1).gesture, 'release-tension');
  assert.equal(resolveVariationContext(project.tracks, sections(['VERSE1', 'OUTRO']), 1).gesture, 'resolve');
});

test('anticipation uses one activity driver and leaves foundations stable over many seeds', () => {
  const { project, engine, load } = setup();
  const { isActivityVariation } = load('core/music/arrangement-variation-context.ts');
  const tracks = project.tracks.flatMap(track => Array.from({ length: 4 }, (_, i) => ({ ...track, id: `${track.id}-${i}`,
    generatorSettings: { ...track.generatorSettings, ...(track.role === 'melody' ? { targetDirection: 'ANY' }
      : track.role === 'arpeggio' ? { rhythm: 'full', hitsPerPattern: 8 } : track.role === 'drums' ? { pitch: 42, rhythm: 'full', pauseChance: 30 } : {}) } })));
  const sections = ['INTRO', 'VERSE1', 'BUILDUP2'].map((type, i) => ({ id: `driver-${i}`, type, measures: 4,
    trackPresence: Object.fromEntries(tracks.map(t => [t.id, true])) }));
  for (let seed = 0n; seed < 50n; seed++) {
    const plan = engine.planSectionVariations(tracks, sections, 1, seed, { chance: 100, maxChanges: 4 });
    assert.equal(plan.gesture, 'build-pulse');
    const activity = plan.choices.filter(c => isActivityVariation(c.kind));
    assert.equal(activity.length, 1);
    assert.ok(['arp-activity', 'drum-activity'].includes(activity[0].kind));
    assert.ok(plan.choices.every(c => c.kind !== 'bass-weight' && c.kind !== 'chord-space'));
    const roles = plan.choices.map(c => tracks.find(t => t.id === c.trackId).role);
    assert.equal(new Set(roles).size, roles.length);
  }
});

test('hook returns share eligible track and operation identities without section-ID seed coupling', () => {
  const { project, engine, load } = setup();
  const { resolveVariationContext } = load('core/music/arrangement-variation-context.ts');
  const tracks = project.tracks.flatMap(track => Array.from({ length: 3 }, (_, i) => ({ ...track, id: `${track.id}-${i}` })));
  const sections = ['INTRO', 'CHORUS1', 'VERSE1', 'CHORUS3', 'VERSE2', 'CLIMAX', 'OUTRO'].map((type, i) => ({
    id: `return-${i}`, type, measures: 4, trackPresence: Object.fromEntries(tracks.map(t => [t.id, true])),
  }));
  assert.equal(resolveVariationContext(tracks, sections, 3).occurrence, 1);
  const policy = { chance: 100, maxChanges: 3 };
  const anchors = choices => choices.filter(c => !['melody-register', 'arp-register'].includes(c.kind));
  for (let seed = 0n; seed < 40n; seed++) {
    const first = engine.planSectionVariations(tracks, sections, 1, seed, policy).choices;
    assert.equal(first.length, 2);
    assert.deepEqual(anchors(engine.planSectionVariations(tracks, sections, 3, seed, policy).choices), first);
    assert.deepEqual(anchors(engine.planSectionVariations(tracks, sections.map((s, i) => ({ ...s, id: `renamed-${i}` })), 5, seed, policy).choices), first);
    const missing = first.find(c => c.kind === 'bass-weight');
    const later = sections.map((s, i) => i === 3 ? { ...s, trackPresence: { ...s.trackPresence, [missing.trackId]: false } } : s);
    const adapted = engine.planSectionVariations(tracks, later, 3, seed, policy).choices;
    assert.ok(!adapted.some(c => c.trackId === missing.trackId));
    assert.deepEqual(adapted.find(c => c.kind === 'chord-space'), first.find(c => c.kind === 'chord-space'));
  }
});

test('solo quiet material keeps its pulse, short sections limit changes, and scoped rerolls use the full orchestration', () => {
  const { project, engine, load } = setup();
  const melody = project.tracks.find(t => t.role === 'melody');
  const sections = ['CLIMAX', 'VERSE1', 'CHILL'].map((type, i) => ({ id: `sparse-${i}`, type, measures: 4, trackPresence: { [melody.id]: true } }));
  assert.deepEqual(engine.planSectionVariations([melody], sections, 1, 1n).choices, []);
  const dense = sections.map(s => ({ ...s, trackPresence: Object.fromEntries(project.tracks.map(t => [t.id, true])) }));
  const scoped = engine.planSectionVariations([melody], dense, 1, 1n, { chance: 100, maxChanges: 4 }, { allTracks: project.tracks });
  assert.equal(scoped.choices[0].kind, 'melody-speed');
  assert.equal(scoped.choices[0].amount, -1);
  const short = dense.map((s, i) => i === 1 ? { ...s, customDurations: [1] } : s);
  assert.ok(engine.planSectionVariations(project.tracks, short, 1, 1n, { chance: 100, maxChanges: 4 }).choices.length <= 1);
  const long = short.map((s, i) => i === 1 ? { ...s, context: { beatDurationMultiplier: 2 } } : s);
  assert.ok(engine.planSectionVariations(project.tracks, long, 1, 1n, { chance: 100, maxChanges: 4 }).choices.length > 1);
  const anticipatory = dense.map((s, i) => ({ ...s, type: ['INTRO', 'VERSE1', 'BUILDUP2'][i] }));
  const retained = [{ trackId: project.tracks.find(t => t.role === 'arpeggio').id, kind: 'arp-activity', amount: 1, enabled: true }];
  assert.deepEqual(engine.planSectionVariations([melody], anticipatory, 1, 1n, { chance: 100, maxChanges: 4 },
    { allTracks: project.tracks, retainedChoices: retained }).choices, []);
});

test('realized purpose survives move/import while the dialog derives a context mismatch', () => {
  const { project, service, engine, load } = setup();
  project.arrangement = ['INTRO', 'VERSE1', 'BUILDUP2', 'CHORUS2', 'OUTRO'].map((type, i) => ({
    ...project.arrangement[0], id: `section-${i + 1}`, type,
  }));
  project.arrangement[1].variations = engine.planSectionVariations(project.tracks, project.arrangement, 1, 1n);
  assert.equal(project.arrangement[1].variations.gesture, 'build-pulse');
  assert.equal(service.importProjectJson(JSON.stringify(project)), true);
  const saved = structuredClone(service.project().arrangement[1].variations);
  service.moveSection(1, 1);
  assert.deepEqual(service.project().arrangement[2].variations, saved);
  const Dialog = load('features/arrangement/arrangement-variations-dialog.component.ts').ArrangementVariationsDialogComponent;
  const dialog = new Dialog();
  const row = dialog.rows().find(r => r.section.id === 'section-2');
  assert.equal(row.context.gesture, 'thin');
  assert.equal(row.gestureMismatch, true);
  assert.equal(engine.validSectionVariations({ ...saved, gesture: 'unknown' }, project.tracks), false);
  assert.equal(engine.validSectionVariations({ ...saved, gesture: 5 }, project.tracks), false);
  const legacy = structuredClone(saved); delete legacy.gesture;
  assert.equal(engine.validSectionVariations(legacy, project.tracks), true);
});

test('activity gestures reach the actual arp consumer without changing track identity or seed', () => {
  const { project, engine, phrase } = setup();
  const arp = project.tracks.find(t => t.role === 'arpeggio');
  const track = { ...arp, generatorSettings: { ...arp.generatorSettings, rhythm: 'full', hitsPerPattern: 8, pauseChance: 0 } };
  const presence = Object.fromEntries(project.tracks.map(t => [t.id, true]));
  const sections = types => types.map((type, i) => ({ ...project.arrangement[0], id: `consumer-${i}`, type, trackPresence: presence }));
  const build = engine.planSectionVariations([track], sections(['INTRO', 'VERSE1', 'BUILDUP2']), 1, 99n,
    { chance: 100, maxChanges: 2 }, { allTracks: project.tracks });
  const thin = engine.planSectionVariations([track], sections(['CLIMAX', 'VERSE1', 'CHILL']), 1, 99n,
    { chance: 100, maxChanges: 2 }, { allTracks: project.tracks });
  const generate = plan => phrase.generateTrackPhrase(project, { ...track,
    generatorSettings: { ...track.generatorSettings, ...engine.variationPatch(track, plan.choices[0]) } });
  assert.ok(generate(build).length > generate(thin).length);
  assert.equal(project.seed, '42');
  assert.equal(track.generatorSettings.hitsPerPattern, 8);
});

test('register operations preserve the audible phrase shape and rhythm while shifting one octave', () => {
  const { project, engine, phrase } = setup();
  for (const contextualMelody of [false, true]) for (const [key, scale] of [['C', 'major'], ['F♯', 'dorian'], ['B', 'natural-minor']]) {
    const p = { ...project, key, scale, melodyGlobal: { contextualMelody } };
    for (const role of ['melody', 'arpeggio']) {
      const track = project.tracks.find(t => t.role === role);
      const choice = { trackId: track.id, kind: role === 'melody' ? 'melody-register' : 'arp-register', amount: 1, enabled: true };
      const patch = engine.variationPatch(track, choice);
      assert.equal(patch.transpose, 12);
      const base = phrase.generateTrackPhrase(p, track);
      const lifted = phrase.generateTrackPhrase(p, { ...track, generatorSettings: { ...track.generatorSettings, ...patch } });
      assert.ok(base.length);
      assert.deepEqual(lifted, base.map(note => ({ ...note, midi: note.midi + 12 })), `${role}, ${key}, ${scale}, contextual=${contextualMelody}`);
      assert.ok(lifted.every(note => note.midi >= 0 && note.midi <= 127));
    }
  }
});

test('arp contours change selected pitches diatonically without rewriting rhythm or velocities', () => {
  const { project, engine, phrase, load } = setup();
  const arp = project.tracks.find(t => t.role === 'arpeggio');
  const track = { ...arp, generatorSettings: { ...arp.generatorSettings, rhythm: 'full', hitsPerPattern: 8, pauseChance: 0 } };
  for (const scale of ['major', 'dorian', 'natural-minor']) for (const amount of [-1, 1]) {
    const p = { ...project, scale };
    const choice = { trackId: track.id, kind: 'arp-contour', amount, enabled: true };
    const patch = engine.variationPatch(track, choice);
    assert.equal(patch.arpContourChordMode, true);
    const base = phrase.generateTrackPhrase(p, track);
    const varied = phrase.generateTrackPhrase(p, { ...track, generatorSettings: { ...track.generatorSettings, ...patch } });
    assert.deepEqual(varied.map(({ midi, ...note }) => note), base.map(({ midi, ...note }) => note));
    assert.notDeepEqual(varied, base);
    const attacks = varied.filter(note => note.startBeat < 4).filter((_, i) => i % 2 === 0).map(note => note.midi);
    assert.ok(amount > 0 ? attacks[0] < attacks[1] && attacks[1] < attacks[2] : attacks[0] > attacks[1] && attacks[1] > attacks[2]);
    const steps = load('core/music/scales.ts').scaleNotes(scale);
    assert.ok(attacks.every(pitch => steps.includes(pitch % 12)));
  }
});

test('new operations protect authored direction, explicit interpretation and declared register bounds', () => {
  const { project, engine, settings, load } = setup();
  const inspect = load('core/music/arrangement-variation-inspection.ts').inspectArrangementVariation;
  const arp = project.tracks.find(t => t.role === 'arpeggio'), melody = project.tracks.find(t => t.role === 'melody');
  const contour = { trackId: arp.id, kind: 'arp-contour', amount: 1, enabled: true };
  for (const patch of [{ arpContour: [0, 4, 1] }, { pattern: 'custom' }, { arpPatternCustom: [0, 1] },
    { rhythm: 'melody' }, { chordSpan: 2 }, { useOctaveAdjustments: true }, { rhythm: 'durations' }]) {
    assert.deepEqual(engine.variationPatch({ ...arp, generatorSettings: { ...arp.generatorSettings, ...patch } }, contour), {});
  }
  const section = { ...project.arrangement[1], rolePartOverrides: { arpeggio: { arpContourChordMode: false } },
    variations: { seed: '1', policy: { chance: 100, maxChanges: 2 }, choices: [contour] } };
  assert.equal(settings.resolvePartTrack(arp, section).generatorSettings.arpContour, undefined, 'never apply half a contour operation');
  assert.equal(inspect(arp, section, contour).status, 'overridden');
  const register = { trackId: melody.id, kind: 'melody-register', amount: 1, enabled: true };
  for (const patch of [{ transpose: 24 }, { rangeMax: 100 }, { chordNoteChoices: [24] }]) {
    assert.deepEqual(engine.variationPatch({ ...melody, generatorSettings: { ...melody.generatorSettings, ...patch } }, register), {});
  }
  const ranged = { ...project.arrangement[2], trackPartOverrides: { [melody.id]: { rangeMin: 48 } },
    variations: { seed: '1', policy: { chance: 100, maxChanges: 2 }, choices: [register] } };
  assert.equal(settings.resolvePartTrack(melody, ranged).generatorSettings.transpose, undefined);
  assert.equal(inspect(melody, ranged, register).status, 'overridden');
});

test('stronger hook returns reserve a lift without exceeding budgets or replacing anchor choices', () => {
  const { project, engine } = setup();
  const tracks = project.tracks.flatMap(track => Array.from({ length: 3 }, (_, i) => ({ ...track, id: `${track.id}-${i}` })));
  const sections = ['INTRO', 'CHORUS1', 'VERSE1', 'CHORUS3', 'CHORUS3', 'VERSE2', 'CLIMAX', 'CHORUS1', 'OUTRO'].map((type, i) => ({
    id: `lift-${i}`, type, measures: 4, trackPresence: Object.fromEntries(tracks.map(t => [t.id, true])),
  }));
  const isLift = c => ['melody-register', 'arp-register'].includes(c.kind);
  for (let seed = 0n; seed < 40n; seed++) {
    const first = engine.planSectionVariations(tracks, sections, 1, seed);
    assert.equal(first.choices.length, 1);
    assert.ok(!first.choices.some(isLift));
    let lift;
    for (const i of [3, 4, 6]) {
      const plan = engine.planSectionVariations(tracks, sections, i, seed);
      assert.equal(plan.choices.length, 2);
      assert.deepEqual(plan.choices.filter(c => !isLift(c)), first.choices);
      assert.equal(plan.choices.filter(isLift).length, 1);
      const current = plan.choices.find(isLift);
      if (lift) assert.deepEqual(current, lift); else lift = current;
      assert.equal(current.amount, 1);
      assert.equal(engine.variationPatch(tracks.find(t => t.id === current.trackId), current).transpose, 12, 'lift is relative to base, never stacked');
    }
    assert.ok(!engine.planSectionVariations(tracks, sections, 7, seed).choices.some(isLift), 'lower return does not keep climbing');
    assert.equal(engine.planSectionVariations(tracks, sections, 3, seed, { chance: 100, maxChanges: 1 }).choices.length, 1);
    assert.ok(!engine.planSectionVariations(tracks, sections, 3, seed, { chance: 100, maxChanges: 1 }).choices.some(isLift));
  }
});

test('custom/locked/manual lift targets keep anchor capacity and new choices persist through history and export', () => {
  const { project, service, engine, load, phrase, midi } = setup();
  project.arrangement = ['INTRO', 'CHORUS1', 'VERSE1', 'CLIMAX', 'OUTRO'].map((type, i) => ({ ...project.arrangement[0], id: `section-${i + 1}`, type }));
  const noLifts = project.tracks.map(track => ['melody', 'arpeggio'].includes(track.role) ? { ...track, rerollLocked: true } : track);
  assert.equal(engine.planSectionVariations(noLifts, project.arrangement, 1, 1n).choices.length, 2);
  assert.ok(!engine.planSectionVariations(noLifts, project.arrangement, 3, 1n).choices.some(c => c.kind.endsWith('-register')));
  const manual = project.tracks.map(track => ['melody', 'arpeggio'].includes(track.role) ? { ...track, editedPhrase: [] } : track);
  assert.equal(engine.planSectionVariations(manual, project.arrangement, 1, 1n).choices.length, 2);
  const authored = project.tracks.map(track => track.role === 'melody' ? { ...track, generatorSettings: { ...track.generatorSettings, rangeMax: 100 } }
    : track.role === 'arpeggio' ? { ...track, generatorSettings: { ...track.generatorSettings, arpContour: [0, 4] } } : track);
  assert.equal(engine.planSectionVariations(authored, project.arrangement, 1, 1n).choices.length, 2);
  assert.equal(service.importProjectJson(JSON.stringify(project)), true);
  const before = service.exportProjectJson();
  service.randomizeArrangementVariations(undefined, undefined, 1n);
  const saved = service.exportProjectJson(), notes = service.project().tracks.map(t => phrase.layOutTrackPhrase(service.project(), t));
  const bytes = midi.generateCompositionMidi(service.project());
  assert.ok(service.project().arrangement[3].variations.choices.some(c => c.kind.endsWith('-register')));
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.redo(); assert.equal(service.exportProjectJson(), saved);
  assert.equal(service.importProjectJson(saved), true);
  assert.deepEqual(service.project().tracks.map(t => phrase.layOutTrackPhrase(service.project(), t)), notes);
  assert.deepEqual(midi.generateCompositionMidi(service.project()), bytes);
});
