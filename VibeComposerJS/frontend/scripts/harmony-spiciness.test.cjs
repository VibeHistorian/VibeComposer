const assert = require('node:assert/strict');
const test = require('node:test');
const { loadMusic } = require('./music-loader.cjs');
const { generateHarmony } = loadMusic('contextual-harmony.ts');
const { spiceHarmony } = loadMusic('harmony-spiciness.ts');
const { chordPitches, progressionChords, scaleNotes, SCALE_MODES } = loadMusic('harmony.ts');
const { fixture } = require('./support/project-fixture.cjs');

test('spice chance colors every strategy reproducibly without changing its progression or protected chords', () => {
  for (const generation of ['CURRENT', 'FUNCTIONAL', 'FIFTHS', 'POP_LOOP', 'MODAL', 'RANDOM']) {
    for (const scale of SCALE_MODES) {
      const args = [9223372036854775807n, 8, 'F', scale];
      const plain = generateHarmony(...args, { generation, spiciness: 0 });
      const spicy = generateHarmony(...args, { generation, spiciness: 100 });
      assert.deepEqual(spicy.degrees, plain.degrees);
      assert.deepEqual(spicy, generateHarmony(...args, { generation, spiciness: 100 }));
      assert.ok(spicy.chords.every(chord => chordPitches(chord, 0).every(Number.isInteger)));
      const basics = progressionChords('F', scale, plain.degrees, plain.chords);
      spicy.chords.forEach((chord, i) => assert.ok(new Set(chordPitches(chord, 0).map(p => p % 12)).size
        > new Set(chordPitches(basics[i], 0).map(p => p % 12)).size, `${generation} ${scale}: slot ${i} gains a color`));
      const lock = { root: 1, spice: 'sus4', inversion: -1, explicitZeroInversion: undefined };
      const locked = generateHarmony(...args, { generation, spiciness: 100 }, {}, [null, lock]);
      assert.deepEqual(locked.chords[1], lock);
    }
  }
});

test('chance controls incidence; modal colors stay in mode and dominants require fifth resolutions', () => {
  const degrees = [1, 4, 2, 5, 1, 6, 2, 5];
  const chords = progressionChords('C', 'major', degrees);
  let count = 0, total = 0, dominants = 0;
  for (let seed = 0n; seed < 200n; seed++) {
    const colored = spiceHarmony(seed, 'C', 'major', degrees, chords, { generation: 'FUNCTIONAL', spiciness: 30 });
    colored.forEach((chord, i) => {
      total++;
      if (chordPitches(chord, 0).length > 3) count++;
      if (chord.spice === '7') {
        dominants++;
        assert.equal((chords[(i + 1) % chords.length].root - chord.root + 12) % 12, 5);
      }
    });
  }
  assert.ok(count / total > 0.26 && count / total < 0.34);
  assert.ok(dominants > 0);
  for (const scale of ['major', 'natural-minor', 'dorian', 'mixolydian']) {
    const modal = generateHarmony(81293n, 16, 'C', scale, { generation: 'MODAL', spiciness: 100 });
    modal.chords.forEach(chord => chordPitches(chord, 0).forEach(pitch => assert.ok(scaleNotes(scale).includes(pitch % 12))));
  }
  const minor = spiceHarmony(1n, 'C', 'natural-minor', [1], progressionChords('C', 'natural-minor', [1]),
    { generation: 'MODAL', spiciness: 100 })[0];
  assert.ok(chordPitches(minor, 0).includes(3), 'minor extensions keep their minor third');
});

test('project spice policy validates, saves, undoes and drives global and contextual section rerolls', () => {
  const { service, load } = fixture();
  const before = service.project();
  service.updateHarmonySettings({ spiciness: 100, generation: 'FUNCTIONAL' });
  const settings = service.project();
  service.undo(); assert.deepEqual(service.project(), before);
  service.redo(); assert.deepEqual(service.project(), settings);
  for (const spiciness of [-1, 101, 1.5, NaN]) {
    service.updateHarmonySettings({ spiciness });
    assert.deepEqual(service.project(), settings);
  }
  const json = service.exportProjectJson();
  const { decodeProject } = load('core/project/project-codec.ts');
  const { DEFAULT_SECTION_TYPE_CHANCES } = load('core/project/section-type-chances.ts');
  assert.equal(decodeProject(JSON.parse(json), DEFAULT_SECTION_TYPE_CHANCES, () => 0.5).harmonySettings.spiciness, 100);
  service.updateSectionContext(settings.arrangement[0].id, { key: 'F', scale: 'natural-minor' });
  for (const sectionId of [undefined, settings.arrangement[0].id]) {
    const previous = service.project();
    service.generateProgression(sectionId, false, true);
    const project = service.project();
    const section = sectionId ? project.arrangement.find(section => section.id === sectionId) : undefined;
    const { sectionProject } = load('core/music/section-context.ts');
    const context = sectionProject(previous, section);
    const expected = generateHarmony(BigInt(previous.seed), previous.progression.length, context.key, context.scale,
      previous.harmonySettings, previous.melodyGlobal);
    assert.deepEqual(section ? section.customChords : project.progressionChords, expected.chords);
    service.undo(); assert.deepEqual(service.project(), previous);
  }
  const previous = service.project();
  service.generateProgression(undefined, true, true);
  for (const section of service.project().arrangement) {
    const { sectionProject } = load('core/music/section-context.ts');
    const context = sectionProject(previous, section);
    const expected = generateHarmony(BigInt(previous.seed), previous.progression.length, context.key, context.scale,
      previous.harmonySettings, previous.melodyGlobal);
    assert.deepEqual(section.customChords, expected.chords);
  }
  service.undo(); assert.deepEqual(service.project(), previous);
});

test('Compose shares the spice policy and stored colored harmony feeds dependent output', () => {
  const { service, load } = fixture();
  service.updateSettings({ randomizeArrangementOnCompose: false });
  service.updateHarmonySettings({ spiciness: 100, generation: 'FIFTHS' });
  assert.notEqual(service.generateComposition(), 'invalid');
  const project = service.project();
  const expected = generateHarmony(BigInt(project.seed), project.progression.length, project.key, project.scale,
    project.harmonySettings, project.melodyGlobal);
  assert.deepEqual(project.progressionChords, expected.chords);
  const { baseGenerationHarmony } = load('core/music/project-harmony.ts');
  assert.deepEqual(baseGenerationHarmony(project).soundingRoots,
    expected.chords.map(chord => chordPitches({ ...chord, inversion: 0 }, 60)));
  service.updateSettings({ randomizeHarmonyOnCompose: false });
  service.generateComposition();
  assert.deepEqual(service.project().progressionChords, expected.chords);
});

test('chordlet clicks use fresh seeds; Shift-click uses the exact project seed in every scope', () => {
  for (const scope of ['global', 'section', 'all-sections']) {
    const { service, load } = fixture();
    service.updateSettings({ seed: '9223372036854775807' });
    service.updateHarmonySettings({ generation: 'FUNCTIONAL', spiciness: 65 });
    const sectionId = service.project().arrangement[0].id;
    service.updateSectionContext(sectionId, { key: 'F', scale: 'natural-minor' });
    const editor = new (load('features/harmony/chordlet-editor.component.ts').ChordletEditorComponent)();
    if (scope === 'section') editor.section.set(service.project().arrangement[0]);
    editor.allSections.set(scope === 'all-sections');
    const randomSeeds = ['123456789', '-987654321'];
    let draws = 0;
    service.randomCompositionSeed = () => randomSeeds[draws++];
    const check = seed => {
      const project = service.project();
      assert.equal(project.seed, '9223372036854775807', 'rerolls leave the saved project seed alone');
      const sections = scope === 'global' ? [undefined] : scope === 'section'
        ? [project.arrangement.find(section => section.id === sectionId)] : project.arrangement;
      const { sectionProject } = load('core/music/section-context.ts');
      for (const section of sections) {
        const context = sectionProject(project, section);
        const degrees = section?.chordDegrees ?? project.progression;
        const expected = generateHarmony(BigInt(seed), degrees.length, context.key, context.scale,
          project.harmonySettings, project.melodyGlobal);
        assert.deepEqual(degrees, expected.degrees);
        assert.deepEqual(section ? section.customChords : project.progressionChords, expected.chords);
      }
    };
    const before = service.project();
    editor.generate({ shiftKey: false }); check(randomSeeds[0]);
    const after = service.project();
    service.undo(); assert.deepEqual(service.project(), before);
    service.redo(); assert.deepEqual(service.project(), after);
    editor.generate({ shiftKey: true }); check(before.seed);
    editor.generate({ shiftKey: true }); check(before.seed);
    assert.equal(draws, 1, 'seeded gestures draw no entropy');
    editor.generate({ shiftKey: false }); check(randomSeeds[1]);
    assert.equal(draws, 2, 'one fresh seed per click, including all-section clicks');
    if (scope === 'section') assert.deepEqual(service.project().arrangement.slice(1), before.arrangement.slice(1));
    if (scope !== 'global') assert.deepEqual(service.project().progression, before.progression);
    assert.deepEqual(service.project().tracks, before.tracks);
  }
});
