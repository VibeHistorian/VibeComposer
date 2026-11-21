const assert = require('node:assert/strict');
const test = require('node:test');
const { fixture } = require('./support/project-fixture.cjs');

test('quick preset menus group current libraries and load with the remembered replace preference', () => {
  const { service, workspace, uiPreferences, load } = fixture();
  const { UiPreferencesService } = load('core/ui/ui-preferences.service.ts');
  assert.equal(uiPreferences.replacePresetTracks(), true);
  const original = service.exportProjectJson();
  const user = { name: 'My bass', role: 'bass', tracks: service.project().tracks.filter(track => track.role === 'bass') };
  workspace.refreshQuickPresets();
  assert.deepEqual(workspace.presetContextItems('bass').map(item => item.label), ['Factory']);
  localStorage.setItem('vibecomposer.track-presets', JSON.stringify([user]));
  workspace.refreshQuickPresets();
  const menu = workspace.presetContextItems('bass');
  assert.deepEqual(menu.map(item => item.label), ['Factory', 'User']);
  assert.equal(workspace.presetContextItems('drums').length, 1);
  assert.equal(menu[0].children.length, 24);
  assert.ok(menu[0].children.every(item => !/Built-in|Factory/.test(item.label)));
  uiPreferences.setReplacePresetTracks(false);
  assert.equal(new UiPreferencesService().replacePresetTracks(), false);
  workspace.loadQuickPreset('bass', { id: menu[1].children[0].id, x: 0, y: 0 });
  assert.equal(service.project().tracks.filter(track => track.role === 'bass').length, 2);
  service.undo(); assert.equal(service.exportProjectJson(), original);
  for (const savedReplace of [true, false]) {
    uiPreferences.setReplacePresetTracks(savedReplace);
    workspace.refreshQuickPresets({ shiftKey: true });
    workspace.loadQuickPreset('bass', { id: menu[1].children[0].id, x: 0, y: 0 });
    assert.equal(service.project().tracks.filter(track => track.role === 'bass').length, savedReplace ? 2 : 1);
    assert.equal(uiPreferences.replacePresetTracks(), savedReplace, 'Shift opening never changes the saved preference');
    service.undo();
    assert.equal(service.exportProjectJson(), original);
  }
  workspace.refreshQuickPresets({ shiftKey: false });
  uiPreferences.setReplacePresetTracks(true);
  workspace.loadQuickPreset('bass', { id: menu[0].children[0].id, x: 0, y: 0 });
  assert.equal(service.project().tracks.filter(track => track.role === 'bass').length, 2);
  service.undo(); assert.equal(service.exportProjectJson(), original);
  const saved = uiPreferences.exportJson();
  assert.equal(uiPreferences.importJson(JSON.stringify({ ...JSON.parse(saved), replacePresetTracks: 'false' })), false);
  const older = JSON.parse(saved); delete older.replacePresetTracks;
  assert.equal(uiPreferences.importJson(JSON.stringify(older)), true);
  assert.equal(uiPreferences.replacePresetTracks(), true);
  localStorage.setItem('vibecomposer.track-presets', '{broken');
  workspace.refreshQuickPresets();
  assert.deepEqual(workspace.presetContextItems('bass').map(item => item.label), ['Factory']);
});

test('factory library covers 24 complete styles with valid, deterministic generated layers', () => {
  const { service, load } = fixture();
  const { BUILTIN_TRACK_PRESETS: library } = load('core/project/builtin-track-presets.ts');
  const { decodeTrackPresets } = load('core/project/track-presets.ts');
  const { generateTrackPhrase } = load('core/music/phrase.ts');
  const { CLASSIC_DRUM_INSTRUMENTS, isDrumPitch, MAX_DRUM_SOUNDS } = load('core/music/drum-instruments.ts');
  const classicPitches = new Set(CLASSIC_DRUM_INSTRUMENTS.map(instrument => instrument.midi));
  assert.equal(library.length, 120);
  assert.ok(decodeTrackPresets(JSON.parse(JSON.stringify(library))));
  const names = new Set(library.map(preset => preset.name));
  assert.equal(names.size, 24);
  const original = JSON.stringify(library);
  for (const name of names) {
    const style = library.filter(preset => preset.name === name);
    assert.equal(new Set(style.map(preset => preset.role)).size, 5);
    const before = service.exportProjectJson();
    for (const preset of style) {
      const minimum = preset.role === 'drums' ? 6 : preset.role === 'arpeggio' ? 3 : 2;
      assert.ok(preset.tracks.length >= minimum, `${name}: ${preset.role} needs ${minimum} layers`);
      assert.equal(new Set(preset.tracks.map(track => track.id)).size, preset.tracks.length);
      if (preset.role === 'drums') {
        const pitches = preset.tracks.map(track => track.generatorSettings.pitch);
        assert.ok(pitches.every(isDrumPitch), `${name}: all voices belong to the drum catalogue`);
        assert.equal(new Set(pitches).size, pitches.length, `${name}: distinct percussion voices`);
        assert.ok(pitches.length <= MAX_DRUM_SOUNDS);
        assert.ok(pitches.some(pitch => !classicPitches.has(pitch)), `${name}: expanded GM percussion`);
      }
      assert.equal(service.loadTrackPreset(preset.role, preset.tracks, true), 'changed', name);
      for (const track of service.project().tracks.filter(track => track.role === preset.role)) {
        assert.equal(track.editedPhrase, undefined);
        assert.equal(track.phraseEdits, undefined);
        const notes = generateTrackPhrase(service.project(), track);
        assert.ok(notes.length, `${name}: ${track.name} must sound`);
        assert.deepEqual(generateTrackPhrase(service.project(), track), notes);
        assert.ok(notes.every(note => Number.isFinite(note.startBeat) && note.durationBeats > 0
          && note.midi >= 0 && note.midi <= 127 && note.velocity >= 1 && note.velocity <= 127));
        if (track.role === 'drums') {
          assert.ok(notes.every(note => note.midi === track.generatorSettings.pitch), `${name}: GM pitch survives loading and generation`);
          assert.ok(Number.isInteger(service.project().drumSlots[String(track.generatorSettings.pitch)]));
        }
      }
    }
    for (let index = 0; index < 5; index++) service.undo();
    assert.equal(service.exportProjectJson(), before);
  }
  assert.equal(JSON.stringify(library), original, 'loads never mutate factory data');
});

test('factory styles follow exact project seeds with musical variation and stable drum anchors', () => {
  const { service, load } = fixture();
  const { BUILTIN_TRACK_PRESETS: library } = load('core/project/builtin-track-presets.ts');
  const { generateTrackPhrase } = load('core/music/phrase.ts');
  const { resolvedTrackPhrase } = load('core/music/phrase.ts');
  const seeds = ['1', '17', '42', '12345', '271828', '987654321', '9007199254740993', '-9223372036854775808'];
  const shape = notes => notes.map(({ midi, startBeat, durationBeats }) => [midi, startBeat, durationBeats]);
  for (const preset of library) {
    const tracks = preset.tracks.map((track, index) => ({ ...track, id: `track-${preset.role}-${index + 1}` }));
    const takes = seeds.map(seed => {
      const project = { ...service.project(), seed, tracks };
      return tracks.map(track => {
        assert.equal(track.generatorSettings.patternSeed, 0, 'factory voices follow project reseeding');
        let notes;
        assert.doesNotThrow(() => { notes = generateTrackPhrase(project, track); }, `${preset.name}: ${track.name} at ${seed}`);
        assert.deepEqual(generateTrackPhrase(project, track), notes);
        assert.ok(notes.length || track.generatorSettings.pauseChance > 0,
          `${preset.name}: only voices with seeded pauses may rest at seed ${seed}`);
        assert.ok(notes.every(note => Number.isFinite(note.startBeat) && note.startBeat >= 0
          && Number.isFinite(note.durationBeats) && note.durationBeats > 0
          && note.midi >= 0 && note.midi <= 127 && note.velocity >= 1 && note.velocity <= 127));
        assert.doesNotThrow(() => resolvedTrackPhrase(project, track), `${preset.name}: section generation at ${seed}`);
        return notes;
      });
    });
    assert.ok(new Set(takes.map(take => JSON.stringify(take))).size > 1, `${preset.name}: ${preset.role} responds to seed`);
    if (preset.role === 'melody' || preset.role === 'arpeggio' || preset.role === 'chords') {
      assert.ok(new Set(takes.map(take => JSON.stringify(take.map(shape)))).size > 1,
        `${preset.name}: ${preset.role} varies beyond velocity`);
    }
    if (preset.role === 'drums') {
      for (const index of [0, 1]) {
        assert.equal(tracks[index].generatorSettings.pauseChance ?? 0, 0);
        assert.equal(tracks[index].generatorSettings.exceptionChance ?? 0, 0);
        for (const take of takes.slice(1)) {
          assert.equal(take[index].length, takes[0][index].length);
          take[index].forEach((note, hit) => assert.ok(Math.abs(note.startBeat - takes[0][index][hit].startBeat) < 0.01,
            'kick/backbeat retain their grid within the humanize amount'));
        }
      }
      assert.ok(new Set(takes.map(take => JSON.stringify(shape(take[2])))).size > 1, `${preset.name}: hats vary`);
    }
  }
});

test('factory support voices and melody controls are distinct across styles', () => {
  const { load } = fixture();
  const { BUILTIN_TRACK_PRESETS: library } = load('core/project/builtin-track-presets.ts');
  for (const role of ['melody', 'bass', 'chords', 'arpeggio']) {
    const signatures = library.filter(preset => preset.role === role)
      .map(preset => JSON.stringify(preset.tracks.map(track => track.generatorSettings)));
    assert.equal(new Set(signatures).size, 24, `${role}: every style has its own musical settings`);
  }
});

test('factory Techno stays compact on minor harmony and dedicated replies leave room around lead attacks', () => {
  const { service, load } = fixture();
  const { BUILTIN_TRACK_PRESETS: library } = load('core/project/builtin-track-presets.ts');
  const { generateTrackPhrase } = load('core/music/phrase.ts');
  for (const style of ['Techno', 'Lo-fi Hip Hop', 'Reggae', 'Blues Shuffle', 'Samba', 'Swing Jazz', 'Disco']) {
    const preset = library.find(item => item.role === 'melody' && item.name.startsWith(style + ' ·'));
    const tracks = preset.tracks.map((track, index) => ({ ...track, id: `track-melody-${index + 1}` }));
    const seeds = Array.from({ length: style === 'Techno' ? 64 : 16 }, (_, i) => String(Math.imul(i + 1, 0x9e3779b1)));
    for (const seed of seeds) {
      const project = { ...service.project(), seed, scale: 'natural-minor', progression: [1, 6, 3, 7], tracks };
      const [lead, reply] = tracks.map(track => generateTrackPhrase(project, track));
      assert.ok(lead.length && reply.length, `${style}: both voices sound at ${seed}`);
      assert.ok(reply.every(note => note.startBeat >= 8), `${style}: reply enters in the second half`);
      if (style === 'Techno') {
        const pitches = lead.map(note => note.midi);
        assert.ok(Math.max(...pitches) - Math.min(...pitches) <= 12, 'wider feasible leaps retain a compact register');
      } else {
        assert.ok(reply.every(note => lead.every(other => Math.abs(note.startBeat - other.startBeat) > 1e-5)),
          `${style}: reply attacks in the gaps`);
      }
    }
  }
});

test('generic rhythm controls approach Reggae, Funk and Dub Factory lead rhythms across seeds', () => {
  const { service, load } = fixture();
  const { BUILTIN_TRACK_PRESETS: library } = load('core/project/builtin-track-presets.ts');
  const { generateTrackPhrase } = load('core/music/phrase.ts');
  const profiles = {
    Reggae: { entryPlacement: 100, rhythmicGrouping: 65, pauseChance: 16, syncopation: 30 },
    Funk: {},
    Dub: { entryPlacement: 35, rhythmicGrouping: 75, speed: -25, pauseChance: 52, noteLengthMultiplier: 30 },
  };
  const authoredFunk = { rhythm: 'durations', customDurationRandomWeighting: true,
    customDurations: [[0.25, -0.25, 0.5, -0.5, 0.5, 1, -1], [-0.75, 0.25, -0.5, 0.5, -1, 0.5, 0.5],
      ...Array.from({ length: 8 }, () => [])],
    customDurationChances: [75, 25, ...Array(8).fill(0)] };
  const samples = 48;
  for (const [style, profile] of Object.entries(profiles)) {
    const preset = library.find(item => item.role === 'melody' && item.name.startsWith(style + ' ·'));
    const sample = overrides => {
      const track = { ...preset.tracks[0], id: 'track-melody-1',
        generatorSettings: { ...preset.tracks[0].generatorSettings, ...overrides } };
      let attacks = 0, offbeats = 0, sounding = 0, downbeatEntries = 0, closePairs = 0, longGaps = 0;
      const rhythms = new Set();
      for (let i = 1; i <= samples; i++) {
        const project = { ...service.project(), seed: String(Math.imul(i, 0x9e3779b1)), tracks: [track] };
        const notes = generateTrackPhrase(project, track);
        assert.ok(notes.length > 0);
        rhythms.add(notes.map(note => note.startBeat).join(','));
        attacks += notes.length;
        downbeatEntries += notes.filter(note => note.startBeat === 0 || note.startBeat === 8).length;
        for (const [index, note] of notes.entries()) {
          sounding += note.durationBeats;
          offbeats += Number(note.startBeat % 1 !== 0);
          const previous = notes[index - 1];
          if (!previous) continue;
          closePairs += Number(note.startBeat - previous.startBeat <= 0.65);
          longGaps += Number(note.startBeat - previous.startBeat - previous.durationBeats >= 1.5);
        }
      }
      return { density: attacks / (4 * samples), offbeats: offbeats / attacks, sounding: sounding / (16 * samples),
        downbeatEntries, closePairs: closePairs / (attacks - samples), longGaps, diversity: rhythms.size };
    };
    const authored = sample(style === 'Funk' ? authoredFunk : {}), automatic = sample({ rhythm: 'melody1' });
    const tuned = sample({ rhythm: 'melody1', ...profile });
    for (const [metric, tolerance] of [['density', 0.45], ['offbeats', 0.07], ['sounding', 0.04]]) {
      assert.ok(Math.abs(tuned[metric] - authored[metric]) < tolerance, `${style}: approaches authored ${metric}`);
    }
    assert.ok(tuned.diversity > authored.diversity);
    if (style === 'Reggae') assert.equal(tuned.downbeatEntries, authored.downbeatEntries);
    if (style === 'Funk') {
      const ungrouped = sample({ rhythm: 'melody1', ...profile, rhythmicGrouping: 0 });
      const middle = sample({ rhythm: 'melody1', ...profile, rhythmicGrouping: 50 });
      assert.ok(ungrouped.closePairs < middle.closePairs && middle.closePairs < tuned.closePairs);
      assert.ok(Math.abs(tuned.closePairs - authored.closePairs) < Math.abs(ungrouped.closePairs - authored.closePairs));
    }
    if (style === 'Dub') assert.ok(automatic.longGaps < tuned.longGaps && tuned.longGaps < authored.longGaps);
  }
});

test('factory generated leads repeat short seeded motifs and keep authored replies', () => {
  const { service, load } = fixture();
  const { BUILTIN_TRACK_PRESETS: library } = load('core/project/builtin-track-presets.ts');
  const { generateTrackPhrase } = load('core/music/phrase.ts');
  const styles = ['Deep House', 'Techno', 'Funk', 'Trap', 'Pop Punk'];
  const generated = library.filter(preset => preset.role === 'melody' && preset.tracks[0].generatorSettings.rhythm === 'melody1');
  assert.deepEqual(generated.map(preset => preset.name.split(' · ')[0]), styles);
  for (const preset of generated) {
    const [lead, reply] = preset.tracks.map((track, index) => ({ ...track, id: `track-melody-${index + 1}` }));
    assert.equal(lead.generatorSettings.motifLength, 1);
    assert.equal(reply.generatorSettings.rhythm, 'durations');
    const neutral = { ...lead, generatorSettings: { ...lead.generatorSettings, motifLength: 0, rhythmicReuse: 0, variationLocation: 0 } };
    let motifDifference = 0, neutralDifference = 0;
    const rhythms = new Set();
    const barDifference = notes => {
      const bar = start => new Set(notes.filter(note => note.startBeat >= start && note.startBeat < start + 4)
        .map(note => Math.round((note.startBeat - start) * 960)));
      return bar(0).symmetricDifference(bar(4)).size;
    };
    for (const [scale, progression] of [['major', [1, 5, 6, 4]], ['natural-minor', [1, 6, 3, 7]]]) {
      for (let seed = 1; seed <= 24; seed++) {
        const project = { ...service.project(), scale, progression, seed: String(Math.imul(seed, 0x9e3779b1)), tracks: [lead, reply] };
        const notes = generateTrackPhrase(project, lead);
        assert.ok(notes.length);
        rhythms.add(notes.map(note => note.startBeat).join(','));
        motifDifference += barDifference(notes);
        neutralDifference += barDifference(generateTrackPhrase(project, neutral));
      }
    }
    assert.ok(rhythms.size > 8, `${preset.name}: different seeds produce different rhythms`);
    assert.ok(motifDifference < neutralDifference, `${preset.name}: the second bar recalls the first`);
  }
});

test('factory multi-bar drum grids put kick turnarounds and fills on later bars at the intended subdivision', () => {
  const { load } = fixture();
  const { BUILTIN_TRACK_PRESETS: library } = load('core/project/builtin-track-presets.ts');
  const { generateDrumPart } = load('core/music/drum-generator.ts');
  const cases = [
    ['Funk', 36, [[0, 6, 8, 14], [0, 6, 8, 13, 14]], 16],
    ['Trap', 36, [[0, 6, 10, 15], [0, 6, 10, 14, 15]], 16],
    ['UK Garage', 36, [[0, 10], [6, 10, 14]], 16],
    ['Pop Punk', 50, [[12], [12, 13, 14, 15]], 16],
    ['Blues Shuffle', 45, [[10], [8, 10, 11]], 12],
    ['Cinematic Pulse', 57, [[4], [], [], [6, 7]], 8],
  ];
  for (const [style, pitch, bars, steps] of cases) {
    const preset = library.find(item => item.role === 'drums' && item.name.startsWith(style + ' ·'));
    const settings = preset.tracks.find(track => track.generatorSettings.pitch === pitch).generatorSettings;
    const span = bars.length;
    assert.equal(settings.chordSpan, span);
    assert.equal(settings.hitsPerPattern, span * steps);
    if (steps === 12) assert.equal(settings.swingPercent, 50);
    for (const seed of [1n, 17n, 9007199254740993n]) {
      const hits = generateDrumPart(seed, span * 2 + 1, settings);
      for (let index = 0; index < span * 2 + 1; index++) {
        assert.deepEqual(hits.filter(hit => hit.barIndex === index).map(hit => hit.step % steps), bars[index % span]);
      }
      assert.deepEqual(generateDrumPart(seed, 1, settings).map(hit => hit.step), bars[0]);
    }
  }
});

test('preset replacement preserves locked tracks and occupied slots, cleans surplus references and undoes once', () => {
  const { service } = fixture();
  const lockedId = service.project().tracks.find(track => track.role === 'bass').id;
  service.setTrackRerollLock(lockedId, true);
  const replacedId = service.addTrack('bass');
  const preset = structuredClone(service.project().tracks.filter(track => track.id === replacedId));
  preset[0].generatorSettings.noteVariation = 55;
  preset[0].mix = { program: 39, volumePercent: 72, panPercent: -15, muted: false, solo: true };
  const removedId = service.addTrack('bass');
  service.updateTrack(replacedId, { midiChannel: 12, mix: { muted: true } });
  service.updatePartSettings({ kind: 'section-track', sectionId: service.project().arrangement[0].id, trackId: replacedId }, { noteVariation: 61 });
  service.updatePartSettings({ kind: 'section-track', sectionId: service.project().arrangement[0].id, trackId: removedId }, { noteVariation: 71 });
  const before = service.exportProjectJson();
  const locked = service.project().tracks.find(track => track.id === lockedId);
  assert.equal(service.loadTrackPreset('bass', preset, true), 'changed');
  const after = service.exportProjectJson();
  const tracks = service.project().tracks.filter(track => track.role === 'bass');
  assert.equal(tracks.length, 2);
  assert.deepEqual(tracks.find(track => track.id === lockedId), locked);
  const replaced = tracks.find(track => track.id === replacedId);
  assert.equal(replaced.generatorSettings.noteVariation, 55);
  assert.equal(replaced.midiChannel, 12);
  assert.deepEqual(replaced.mix, { ...preset[0].mix, muted: true, solo: false });
  assert.equal(service.project().arrangement[0].trackPartOverrides[replacedId].noteVariation, 61);
  assert.ok(!tracks.some(track => track.id === removedId));
  for (const section of service.project().arrangement) {
    assert.ok(!Object.hasOwn(section.trackPresence, removedId));
    assert.ok(!Object.hasOwn(section.trackPartOverrides, removedId));
    assert.equal(typeof section.trackPresence[tracks.find(track => track.id !== lockedId).id], 'boolean');
  }
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.redo(); assert.equal(service.exportProjectJson(), after);
  assert.deepEqual(preset[0].id, replacedId, 'loading does not mutate the library');
});

test('saving and replacing six drum tracks preserves the complete project and never rerolls presence', () => {
  const { service, load } = fixture();
  service.setRoleTrackCount('drums', 6);
  const project = JSON.parse(service.exportProjectJson());
  const drumIds = project.tracks.filter(track => track.role === 'drums').map(track => track.id);
  project.arrangement.forEach((section, index) => {
    section.trackPresence.drums = drumIds.flatMap((_, track) => (index + track) % 3 === 0 ? [track + 1] : []);
    section.trackPartOverrides = { ...section.trackPartOverrides, [drumIds[0]]: { hitsPerPattern: 8 } };
    section.trackPhrases = { ...section.trackPhrases, [drumIds[1]]: [] };
    section.generatedTrackIds = [drumIds[2]];
  });
  assert.equal(service.importProjectJson(JSON.stringify(project)), true);
  const before = service.exportProjectJson();
  const saved = JSON.parse(JSON.stringify(service.project().tracks.filter(track => track.role === 'drums')));
  const { loadTrackPreset } = load('core/project/track-presets.ts');
  const candidate = loadTrackPreset(service.project(), 'drums', saved, true, () => {
    assert.fail('existing drum slots must not request new presence');
  });
  assert.deepEqual(candidate, service.project());
  assert.equal(service.loadTrackPreset('drums', saved, true), 'unchanged');
  assert.equal(service.exportProjectJson(), before);
});

test('preset append allocates independent identities and rejects invalid batches atomically', () => {
  const { service } = fixture();
  const tracks = structuredClone(service.project().tracks.filter(track => track.role === 'drums'));
  const beforeTracks = service.project().tracks;
  assert.equal(service.loadTrackPreset('drums', tracks, false), 'changed');
  const next = service.project().tracks;
  assert.equal(next.length, beforeTracks.length + tracks.length);
  assert.deepEqual(next.slice(0, beforeTracks.length), beforeTracks);
  assert.equal(new Set(next.map(track => track.id)).size, next.length);
  assert.equal(next.at(-1).midiChannel, 10);
  const before = service.exportProjectJson();
  for (const invalid of [[], [{ ...tracks[0], role: 'bass' }], [{ ...tracks[0], mix: {} }], null]) {
    assert.equal(service.loadTrackPreset('drums', invalid, true), 'invalid');
    assert.equal(service.exportProjectJson(), before);
  }
  const tooMany = Array.from({ length: 64 }, (_, index) => ({ ...tracks[0], id: 'preset-' + index }));
  assert.equal(service.loadTrackPreset('drums', tooMany, false), 'invalid');
  assert.equal(service.exportProjectJson(), before);
});

test('appending a melodic preset assigns successive free channels and skips percussion', () => {
  const { service } = fixture();
  const before = service.project().tracks;
  const bass = before.find(track => track.role === 'bass');
  const preset = Array.from({ length: 7 }, (_, index) => ({ ...bass, id: `preset-bass-${index}` }));
  assert.equal(service.loadTrackPreset('bass', preset, false), 'changed');
  const after = service.project().tracks;
  assert.deepEqual(after.slice(0, before.length), before);
  assert.deepEqual(after.slice(before.length).map(track => track.midiChannel), [5, 6, 7, 8, 9, 11, 12]);
});

test('all-role presets allocate channels freed by removals across roles', () => {
  const { service } = fixture();
  const before = service.project().tracks;
  const bass = before.find(track => track.role === 'bass');
  const chords = before.find(track => track.role === 'chords');
  const preset = [...before.filter(track => track.role !== 'arpeggio'),
    { ...bass, id: 'preset-bass-extra' }, { ...chords, id: 'preset-chords-extra' }];
  assert.equal(service.loadTrackPreset('all', preset, true), 'changed');
  const after = service.project().tracks;
  assert.ok(after.every(track => track.role !== 'arpeggio'));
  assert.deepEqual(after.filter(track => before.some(original => original.id === track.id)).map(track => track.midiChannel), [4, 1, 2, 10]);
  assert.deepEqual(after.filter(track => !before.some(original => original.id === track.id)).map(track => track.midiChannel), [3, 5]);
});

test('preset JSON round trips all roles and rejects mixed roles, duplicate names and bad track data', () => {
  const { service, load } = fixture();
  const { decodeTrackPresets } = load('core/project/track-presets.ts');
  const library = service.project().tracks.map(track => ({ name: 'Shared name', role: track.role, tracks: [track] }));
  assert.deepEqual(decodeTrackPresets(JSON.parse(JSON.stringify(library))), decodeTrackPresets(library));
  for (const invalid of [{}, [null], [{ ...library[0], tracks: [] }], [...library, library[0]],
    [{ ...library[0], name: ' ' }], [{ ...library[0], tracks: [library[1].tracks[0]] }]]) {
    assert.equal(decodeTrackPresets(invalid), undefined);
  }
});
