const assert = require('node:assert/strict');
const test = require('node:test');
const { fixture } = require('../support/project-fixture.cjs');

test('arrangement behavior preferences persist immediately, transfer, and stay outside project history', () => {
  const { service, uiPreferences: prefs, uiStored, load } = fixture();
  const { UiPreferencesService } = load('core/ui/ui-preferences.service.ts');
  const before = service.exportProjectJson(), history = service.historyPosition();
  assert.equal(prefs.generateArrangementVariations(), false);
  assert.equal(prefs.rerollPresenceWithVariations(), false);
  for (const key of ['generateArrangementVariations', 'rerollPresenceWithVariations']) {
    prefs.setArrangementBehavior(key, true);
    assert.equal(JSON.parse(uiStored.get('vibecomposer.ui-preferences.v1'))[key], true);
  }
  const saved = prefs.exportJson();
  const reloaded = new UiPreferencesService();
  assert.equal(reloaded.generateArrangementVariations(), true);
  assert.equal(reloaded.rerollPresenceWithVariations(), true);
  for (const key of ['generateArrangementVariations', 'rerollPresenceWithVariations']) {
    assert.equal(prefs.importJson(JSON.stringify({ ...JSON.parse(saved), [key]: 'true' })), false);
    prefs.setArrangementBehavior(key, 'true');
    assert.equal(prefs.exportJson(), saved);
  }
  const older = JSON.parse(saved); delete older.generateArrangementVariations; delete older.rerollPresenceWithVariations;
  assert.equal(prefs.importJson(JSON.stringify(older)), true);
  assert.equal(prefs.generateArrangementVariations(), false);
  assert.equal(prefs.rerollPresenceWithVariations(), false);
  assert.equal(prefs.importJson(saved), true);
  assert.equal(service.exportProjectJson(), before);
  assert.equal(service.historyPosition(), history);
});

test('ghost note preference defaults on, persists, transfers and rejects invalid values atomically', () => {
  const { service, uiPreferences, load } = fixture();
  const { UiPreferencesService } = load('core/ui/ui-preferences.service.ts');
  const before = service.exportProjectJson(), history = service.historyPosition();
  assert.equal(uiPreferences.scoreGhostNotes(), true);
  uiPreferences.toggleScoreGhostNotes();
  assert.equal(new UiPreferencesService().scoreGhostNotes(), false);
  const saved = uiPreferences.exportJson();
  assert.equal(uiPreferences.importJson(saved), true);
  assert.equal(uiPreferences.scoreGhostNotes(), false);
  assert.equal(uiPreferences.importJson(JSON.stringify({ ...JSON.parse(saved), scoreGhostNotes: 'true' })), false);
  assert.equal(uiPreferences.exportJson(), saved);
  const previous = JSON.parse(saved); delete previous.scoreGhostNotes;
  assert.equal(uiPreferences.importJson(JSON.stringify(previous)), true);
  assert.equal(uiPreferences.scoreGhostNotes(), true);
  assert.equal(service.exportProjectJson(), before);
  assert.equal(service.historyPosition(), history);
});

test('score outlines persist independently of phrase ghosts', () => {
  const { uiPreferences: prefs, load } = fixture();
  const { UiPreferencesService } = load('core/ui/ui-preferences.service.ts');
  assert.equal(prefs.scoreOutlineOtherTracks(), false);
  prefs.toggleScoreOutlineOtherTracks();
  assert.equal(new UiPreferencesService().scoreOutlineOtherTracks(), true);
  assert.equal(prefs.scoreGhostNotes(), true);
  const saved = prefs.exportJson();
  assert.equal(prefs.importJson(JSON.stringify({ ...JSON.parse(saved), scoreOutlineOtherTracks: 'true' })), false);
  assert.equal(prefs.exportJson(), saved);
  prefs.toggleScoreOutlineOtherTracks();
  assert.equal(prefs.importJson(saved), true);
  assert.equal(prefs.scoreOutlineOtherTracks(), true);
  const older = JSON.parse(saved); delete older.scoreOutlineOtherTracks;
  assert.equal(prefs.importJson(JSON.stringify(older)), true);
  assert.equal(prefs.scoreOutlineOtherTracks(), false);
});

test('phrase ghosts preserve crossing note durations from other tracks', () => {
  const { service, workspace, ui, uiPreferences } = fixture();
  const [section] = service.project().arrangement;
  const bass = service.project().tracks.find(track => track.role === 'bass');
  const chords = service.project().tracks.find(track => track.role === 'chords');
  service.setSectionTrackPresence(0, chords.id, true);
  service.updateSectionTrackPhrase(section.id, chords.id, [
    { id: 'overlap', midi: 60, startBeat: 0, durationBeats: 4, velocity: 90 },
    { id: 'outside', midi: 64, startBeat: 5, durationBeats: 1, velocity: 90 },
  ]);
  ui.selectedTrackId.set(bass.id);
  workspace.editor = () => ({ editedTracks: () => [bass], editBounds: () => ({ startBeat: 0, endBeat: 2 }) });
  assert.deepEqual(workspace.ghostNotes(), [], 'overview has no ghosts');
  workspace.editing.set(true);
  workspace.editStartBeat.set(1); workspace.editPhraseLength.set(2);
  const ghosts = workspace.ghostNotes();
  assert.ok(ghosts.length > 0);
  assert.ok(ghosts.every(note => note.part !== bass.id && note.startBeat < 3 && note.startBeat + note.durationBeats > 1));
  const chordGhost = ghosts.find(note => note.part === chords.id);
  assert.equal(chordGhost.midi, 60);
  assert.equal(chordGhost.startBeat, 0);
  assert.equal(chordGhost.durationBeats, 4);
  uiPreferences.toggleScoreGhostNotes();
  assert.deepEqual(workspace.ghostNotes(), []);
});

test('drum mapping preferences persist and transfer without editing project notes or history', () => {
  const { service, load } = fixture();
  const { UiPreferencesService } = load('core/ui/ui-preferences.service.ts');
  const preferences = new UiPreferencesService();
  const project = service.exportProjectJson(), position = service.historyPosition();
  assert.equal(preferences.drumMapping(), 'auto');
  for (const mode of ['chromatic', 'general-midi', 'auto']) {
    preferences.setDrumMapping(mode);
    assert.equal(new UiPreferencesService().drumMapping(), mode);
    assert.equal(preferences.importJson(preferences.exportJson()), true);
    assert.equal(preferences.drumMapping(), mode);
  }
  const saved = preferences.exportJson();
  assert.equal(preferences.importJson(JSON.stringify({ ...JSON.parse(saved), drumMapping: 'unknown' })), false);
  assert.equal(preferences.exportJson(), saved, 'invalid mapping import is atomic');
  const withoutMapping = JSON.parse(saved); delete withoutMapping.drumMapping;
  preferences.setDrumMapping('chromatic');
  assert.equal(preferences.importJson(JSON.stringify(withoutMapping)), true);
  assert.equal(preferences.drumMapping(), 'auto');
  assert.equal(service.exportProjectJson(), project);
  assert.equal(service.historyPosition(), position);
});

test('manual Mode preference defaults on for existing preferences and persists explicit off through import/export', () => {
  const { uiPreferences, load } = fixture();
  const { decodeUiPreferences } = load('core/ui/ui-preferences.ts');
  const previous = JSON.parse(uiPreferences.exportJson()); delete previous.manualPhrasesFollowMode;
  assert.equal(decodeUiPreferences(previous).manualPhrasesFollowMode, true);
  assert.equal(decodeUiPreferences({ ...previous, manualPhrasesFollowMode: 'true' }), null);
  uiPreferences.toggleManualPhrasesFollowMode();
  const { UiPreferencesService } = load('core/ui/ui-preferences.service.ts');
  assert.equal(new UiPreferencesService().manualPhrasesFollowMode(), false);
  assert.equal(uiPreferences.importJson(JSON.stringify(previous)), true);
  assert.equal(uiPreferences.manualPhrasesFollowMode(), true);
});

test('section chance preferences gain melody defaults while retaining saved probabilities for other roles', () => {
  const { load } = fixture();
  const previousStorage = global.localStorage;
  const values = new Map([['vibecomposer.section-type-settings.v1', JSON.stringify({
    CHORUS1: { bass: 13, chords: 72, arpeggio: 19, drums: 81 },
  })]]);
  global.localStorage = { getItem: (key) => values.get(key) ?? null, setItem: (key, value) => values.set(key, value) };
  try {
    const { SectionTypeSettingsService } = load('core/project/section-type-settings.service.ts');
    const preferences = new SectionTypeSettingsService();
    assert.equal(preferences.chances().INTRO.melody, 20);
    assert.equal(preferences.chances().HALF_CHORUS.melody, 0);
    assert.equal(preferences.chances().CLIMAX.melody, 100);
    assert.equal(preferences.chances().CHORUS1.melody, 50);
    assert.equal(preferences.chances().CHORUS1.bass, 13);
    preferences.setChance('CHORUS1', 'melody', 87);
    assert.equal(new SectionTypeSettingsService().chances().CHORUS1.melody, 87);
    assert.equal(preferences.chances().CHORUS1.bass, 13);
  } finally { global.localStorage = previousStorage; }
});

test('Highlight audible filters score tracks and notes from Solo/Mute without changing the mix', () => {
  const { service, workspace, uiPreferences, flushEffects } = fixture();
  service.setRoleTrackCount('bass', 2);
  const tracks = service.project().tracks;
  const [bass, otherBass] = tracks.filter(track => track.role === 'bass');
  service.applyPhraseEdits(tracks.map(track => ({ trackId: track.id, notes: [
    { id: track.id + '-note', midi: track.role === 'drums' ? 36 : 60, startBeat: 0, durationBeats: 1, velocity: 90 },
  ] })));
  service.updateTrack(bass.id, { mix: { muted: true } });
  assert.ok(workspace.scoreTracks().some(track => track.id === bass.id), 'disabled preference shows muted tracks');
  uiPreferences.toggleScoreMixSync(); flushEffects();
  assert.ok(workspace.scoreTracks().every(track => track.id !== bass.id));
  assert.ok(workspace.canvasNotes().length > 0);
  assert.ok(workspace.canvasNotes().every(note => note.part !== bass.id));
  assert.ok(workspace.scoreTracks().some(track => track.id === otherBass.id), 'audibility is per track within a role');

  service.updateTrack(otherBass.id, { mix: { solo: true } });
  assert.deepEqual(workspace.scoreTracks().map(track => track.id), [otherBass.id]);
  assert.ok(workspace.canvasNotes().every(note => note.part === otherBass.id));
  const before = service.exportProjectJson();
  const history = JSON.stringify(service.historyEntries());
  workspace.toggleScoreRole('bass');
  assert.deepEqual(workspace.scoreTracks(), [], 'manual role filters still apply');
  assert.deepEqual(workspace.canvasNotes(), []);
  workspace.toggleScoreRole('bass');
  assert.deepEqual(workspace.scoreTracks().map(track => track.id), [otherBass.id]);
  workspace.toggleScoreRole('drums');
  workspace.toggleScoreRole('drums');
  const allRoles = [...workspace.displayedRoles()].sort();
  let prevented = 0;
  const middle = { button: 1, preventDefault() { prevented++; } };
  workspace.soloScoreRole(middle, 'bass');
  assert.deepEqual([...workspace.displayedRoles()], ['bass'], 'middle click solos a visible role');
  workspace.soloScoreRole(middle, 'drums');
  assert.deepEqual([...workspace.displayedRoles()], ['drums'], 'middle click solos a hidden role');
  workspace.soloScoreRole(middle, 'drums');
  assert.deepEqual([...workspace.displayedRoles()].sort(), allRoles, 'middle click on the sole role resets visibility');
  for (const role of allRoles) workspace.toggleScoreRole(role);
  workspace.soloScoreRole(middle, 'bass');
  assert.deepEqual([...workspace.displayedRoles()], ['bass'], 'middle click restores a role when all are hidden');
  workspace.soloScoreRole({ button: 2, preventDefault() { throw Error('unexpected prevention'); } }, 'drums');
  assert.deepEqual([...workspace.displayedRoles()], ['bass'], 'other auxiliary buttons leave visibility alone');
  workspace.soloScoreRole(middle, 'bass');
  assert.equal(prevented, 5);
  workspace.toggleScoreRole('drums');
  uiPreferences.toggleScoreMixSync(); flushEffects();
  assert.ok(workspace.scoreTracks().some(track => track.id === bass.id), 'disabling restores manual visibility');
  assert.ok(workspace.scoreTracks().every(track => track.role !== 'drums'));
  workspace.toggleScoreRole('drums');
  uiPreferences.toggleScoreMixSync(); flushEffects();
  assert.equal(service.exportProjectJson(), before, 'preference and display changes never change the mix');
  assert.equal(JSON.stringify(service.historyEntries()), history, 'display changes do not affect history');

  service.updateTrack(otherBass.id, { mix: { muted: true } });
  assert.ok(workspace.scoreTracks().length > 1, 'muting the last solo clears solo and restores other audible tracks');
  assert.ok(workspace.scoreTracks().every(track => track.id !== otherBass.id && track.id !== bass.id));
  service.updateTrack(otherBass.id, { mix: { muted: false, solo: false } });
  assert.ok(workspace.scoreTracks().length > 1, 'clearing solo restores other unmuted tracks');
  for (const track of tracks) service.updateTrack(track.id, { mix: { muted: true } });
  assert.deepEqual(workspace.scoreTracks(), [], 'all-muted mix produces an empty score');
  workspace.selectedTrackId.set(bass.id);
  workspace.editing.set(true);
  assert.deepEqual(workspace.scoreTracks().map(track => track.id), [bass.id], 'muted tracks remain editable');
});

test('randomize colors persists a complete palette without changing fonts, project or history', () => {
  const { service, uiPreferences: preferences, load } = fixture();
  const before = service.exportProjectJson(), position = service.historyPosition();
  preferences.setFont('ui', 'Verdana, sans-serif');
  preferences.setColorAdjustment('hueShift', 50);
  preferences.setColorAdjustment('brightness', -50);
  preferences.toggleResetAdjustments();
  preferences.randomizeAppearance('dark');
  const appearance = preferences.appearance();
  assert.equal(appearance.fonts.ui, 'Verdana, sans-serif');
  assert.deepEqual(appearance.colorAdjustments, { brightness: 0, hueShift: 0, saturation: 0 });
  assert.equal(new Set(Object.values(appearance.roleColors)).size, 5);
  const restored = new (load('core/ui/ui-preferences.service.ts').UiPreferencesService)();
  assert.deepEqual(restored.appearance(), appearance);
  const saved = preferences.exportJson();
  preferences.resetAppearance();
  assert.equal(preferences.importJson(saved), true);
  assert.deepEqual(preferences.appearance(), appearance);
  assert.equal(service.exportProjectJson(), before);
  assert.equal(service.historyPosition(), position);
});

test('theme randomization preserves adjustments by default and optionally resets them', () => {
  const { uiPreferences: preferences, load } = fixture();
  assert.equal(preferences.appearance().resetAdjustments, false);
  for (const mode of ['dark', 'light']) {
    preferences.setColorAdjustment('brightness', -25);
    preferences.setColorAdjustment('saturation', 30);
    preferences.setColorAdjustment('hueShift', 20);
    preferences.randomizeAppearance(mode);
    assert.deepEqual(preferences.appearance().colorAdjustments, { brightness: -25, saturation: 30, hueShift: 20 });
    const restored = new (load('core/ui/ui-preferences.service.ts').UiPreferencesService)();
    assert.equal(restored.appearance().resetAdjustments, false);
    assert.deepEqual(restored.appearance(), preferences.appearance());
  }
  const saved = preferences.exportJson();
  preferences.resetAppearance();
  assert.equal(preferences.importJson(saved), true);
  assert.equal(preferences.appearance().resetAdjustments, false);
  preferences.toggleResetAdjustments();
  preferences.randomizeAppearance('light');
  assert.deepEqual(preferences.appearance().colorAdjustments, { brightness: 0, saturation: 0, hueShift: 0 });
});

test('main theme preferences derive concrete canvas colors and preserve project state', () => {
  const { service, uiPreferences: preferences, load } = fixture();
  const { themeColorTokens, DEFAULT_THEME_COLORS } = load('core/ui/theme.ts');
  const before = service.exportProjectJson(), position = service.historyPosition();
  preferences.setThemeColor('accent', '#DD88EE');
  preferences.setThemeColor('surface', '#281838');
  preferences.setThemeColor('border', '#78618a');
  assert.equal(preferences.appearance().themeColors.accent, '#dd88ee');
  const saved = preferences.exportJson();
  const restored = new (load('core/ui/ui-preferences.service.ts').UiPreferencesService)();
  assert.deepEqual(restored.appearance(), preferences.appearance());
  const original = themeColorTokens(DEFAULT_THEME_COLORS);
  const changed = themeColorTokens(preferences.appearance().themeColors);
  for (const value of Object.values(changed)) assert.match(value, /^#[0-9a-f]{6}([0-9a-f]{2})?$/);
  for (const key of ['--surface-base', '--surface-button', '--border-default', '--grid-measure', '--accent-glow']) {
    assert.notEqual(changed[key], original[key], key);
  }
  for (const themeColors of [null, [], {}, { ...DEFAULT_THEME_COLORS, accent: 'red' }]) {
    assert.equal(preferences.importJson(JSON.stringify({ ...JSON.parse(saved), appearance: { ...preferences.appearance(), themeColors } })), false);
    assert.equal(preferences.exportJson(), saved);
  }
  preferences.setThemeColor('accent', 'invalid');
  assert.equal(preferences.exportJson(), saved);
  assert.equal(service.exportProjectJson(), before);
  assert.equal(service.historyPosition(), position);
  preferences.resetAppearance();
  assert.deepEqual(preferences.appearance().themeColors, DEFAULT_THEME_COLORS);
  const withoutTheme = JSON.parse(saved); delete withoutTheme.appearance.themeColors;
  assert.equal(preferences.importJson(JSON.stringify(withoutTheme)), true);
  assert.deepEqual(preferences.appearance().themeColors, DEFAULT_THEME_COLORS);
});

test('panel background preferences persist and blend selection toward the page background', () => {
  const { uiPreferences: preferences, load } = fixture();
  const { themeColorTokens, DEFAULT_THEME_COLORS } = load('core/ui/theme.ts');
  preferences.setThemeColor('inspector', '#211831');
  preferences.setThemeColor('transportDock', '#123456');
  preferences.setThemeColor('roleGroups', '#504060');
  preferences.setThemeColor('appHeading', '#304050');
  const tokens = themeColorTokens(preferences.appearance().themeColors);
  assert.equal(tokens['--surface-inspector'], '#211831');
  assert.equal(tokens['--surface-transport-dock'], '#123456');
  assert.equal(tokens['--surface-role-group'], '#504060');
  assert.equal(tokens['--surface-app-heading'], '#304050');
  assert.equal(tokens['--surface-role-group-selected'], '#3a314a');
  assert.equal(tokens['--surface-base'], DEFAULT_THEME_COLORS.surface);
  const saved = preferences.exportJson();
  const restored = new (load('core/ui/ui-preferences.service.ts').UiPreferencesService)();
  assert.deepEqual(restored.appearance(), preferences.appearance());
  for (const key of ['inspector', 'roleGroups', 'appHeading', 'transportDock']) {
    const invalid = JSON.parse(saved);
    invalid.appearance.themeColors[key] = 'invalid';
    assert.equal(preferences.importJson(JSON.stringify(invalid)), false);
    assert.equal(preferences.exportJson(), saved);
  }
  const omitted = JSON.parse(saved);
  delete omitted.appearance.themeColors.inspector;
  delete omitted.appearance.themeColors.roleGroups;
  delete omitted.appearance.themeColors.appHeading;
  assert.equal(preferences.importJson(JSON.stringify(omitted)), true);
  assert.equal(preferences.appearance().themeColors.inspector, DEFAULT_THEME_COLORS.inspector);
  assert.equal(preferences.appearance().themeColors.roleGroups, DEFAULT_THEME_COLORS.roleGroups);
  assert.equal(preferences.appearance().themeColors.appHeading, DEFAULT_THEME_COLORS.appHeading);
  preferences.resetAppearance();
  assert.deepEqual(preferences.appearance().themeColors, DEFAULT_THEME_COLORS);
});

test('appearance preferences persist, transfer atomically and derive every role variant', () => {
  const { service, uiPreferences: preferences, load } = fixture();
  const before = service.exportProjectJson(), position = service.historyPosition();
  const { roleColorTokens, defaultAppearance } = load('core/ui/appearance.ts');
  preferences.setFont('ui', 'Verdana, sans-serif');
  preferences.setFont('heading', 'Georgia, serif');
  preferences.setRoleColor('bass', '#8855CC');
  preferences.setRoleColor('drums', '#44bb99');
  const saved = preferences.exportJson();
  assert.equal(preferences.appearance().roleColors.bass, '#8855cc');
  const restored = new (load('core/ui/ui-preferences.service.ts').UiPreferencesService)();
  assert.deepEqual(restored.appearance(), preferences.appearance());
  assert.equal(service.exportProjectJson(), before);
  assert.equal(service.historyPosition(), position);
  for (const role of ['melody','bass','chords','arpeggio','drums']) {
    const original = roleColorTokens(role, defaultAppearance().roleColors[role]);
    const changed = roleColorTokens(role, '#8855cc');
    for (const key of Object.keys(original)) assert.notEqual(changed[key], original[key], key);
  }
  const drumTokens = roleColorTokens('drums', '#44bb99');
  assert.equal(drumTokens['--role-kick'], '#44bb99');
  assert.equal(drumTokens['--role-drum-surface'], drumTokens['--role-drums-surface']);
  assert.notEqual(drumTokens['--role-snare'], drumTokens['--role-kick']);
  for (const appearance of [null, {}, { ...JSON.parse(saved).appearance, fonts: { ui: 'unknown' } },
      { ...JSON.parse(saved).appearance, roleColors: { ...JSON.parse(saved).appearance.roleColors, bass: 'red' } }]) {
    assert.equal(preferences.importJson(JSON.stringify({ ...JSON.parse(saved), appearance })), false);
    assert.equal(preferences.exportJson(), saved);
  }
  preferences.setRoleColor('bass', 'invalid'); preferences.setFont('ui', 'unknown');
  assert.equal(preferences.exportJson(), saved);
  preferences.resetAppearance();
  assert.deepEqual(preferences.appearance(), defaultAppearance());
  assert.equal(preferences.importJson(saved), true);
  assert.equal(preferences.exportJson(), saved);
  const withoutAppearance = JSON.parse(saved); delete withoutAppearance.appearance;
  assert.equal(preferences.importJson(JSON.stringify(withoutAppearance)), true);
  assert.deepEqual(preferences.appearance(), defaultAppearance());
});

test('score chord grid defaults off and persists and transfers without changing project history', () => {
  const { service, uiPreferences: preferences, load } = fixture();
  const before = service.exportProjectJson(), position = service.historyPosition();
  assert.equal(preferences.scoreChordGrid(), false);
  const defaults = preferences.exportJson();
  preferences.toggleScoreChordGrid();
  assert.equal(preferences.scoreChordGrid(), true);
  const enabled = preferences.exportJson();
  const restored = new (load('core/ui/ui-preferences.service.ts').UiPreferencesService)();
  assert.equal(restored.scoreChordGrid(), true);
  assert.equal(preferences.importJson(defaults), true); assert.equal(preferences.scoreChordGrid(), false);
  assert.equal(preferences.importJson(enabled), true); assert.equal(preferences.scoreChordGrid(), true);
  assert.equal(preferences.importJson(JSON.stringify({ ...JSON.parse(enabled), scoreChordGrid: 'true' })), false);
  assert.equal(preferences.exportJson(), enabled);
  const missing = JSON.parse(defaults); delete missing.scoreChordGrid;
  assert.equal(preferences.importJson(JSON.stringify(missing)), true); assert.equal(preferences.scoreChordGrid(), false);
  assert.equal(service.exportProjectJson(), before); assert.equal(service.historyPosition(), position);
});

test('UI preferences customize per-role placement without changing project, history, or fixed order', () => {
  const { service, uiPreferences: preferences, uiStored, load } = fixture();
  service.flushPersistence();
  const before = service.exportProjectJson(), position = service.historyPosition();
  const defaults = preferences.exportJson();
  preferences.beginQuickSettings();
  preferences.moveControl('bass', 'velocityMin');
  preferences.moveControl('bass', 'velocityMin');
  preferences.moveControl('bass', 'feedbackCount');
  assert.ok(preferences.quickSettings().bass.includes('feedbackCount'));
  assert.equal(preferences.quickSettings().chords.includes('feedbackCount'), false);
  assert.deepEqual(preferences.quickSettings().bass.filter(key => ['velocityMin','velocityMax'].includes(key)), ['velocityMin','velocityMax']);
  assert.equal(preferences.exportJson(), defaults, 'draft is not exported until saved');
  preferences.saveQuickSettings();
  assert.equal(service.exportProjectJson(), before);
  assert.equal(service.historyPosition(), position);
  assert.ok(uiStored.has('vibecomposer.ui-preferences.v1'));
  assert.ok(uiStored.has('vibecomposer.project-tabs.v1'));
  const restored = new (load('core/ui/ui-preferences.service.ts').UiPreferencesService)();
  assert.deepEqual(restored.preferences(), preferences.preferences());
  assert.equal(service.importProjectJson(before), true);
  assert.ok(preferences.quickSettings().bass.includes('feedbackCount'), 'project loading retains UI choices');
  const importedProject = service.exportProjectJson();
  assert.equal(preferences.importJson(defaults), true);
  assert.equal(service.exportProjectJson(), importedProject, 'UI import retains musical data');
  for (const invalid of [before, '{}', '{', JSON.stringify({ ...JSON.parse(defaults), compactHistory: 'true' }),
      JSON.stringify({ ...JSON.parse(defaults), quickSettings: { bass: ['unknown'] } })]) {
    assert.equal(preferences.importJson(invalid), false);
    assert.equal(preferences.exportJson(), defaults, 'invalid import is atomic');
  }
  preferences.beginQuickSettings();
  preferences.moveControl('bass', 'velocityMin');
  preferences.moveControl('chords', 'velocityMax');
  preferences.resetQuickSettings('bass');
  assert.deepEqual(preferences.quickSettings().bass, JSON.parse(defaults).quickSettings.bass);
  assert.equal(preferences.quickSettings().chords.includes('velocityMax'), true);
});

test('combined selectors move together and reset all local values in one undo step', () => {
  const { service, uiPreferences: preferences, load } = fixture();
  const Editor = load('features/part-settings/part-settings-editor.component.ts').PartSettingsEditorComponent;
  const editor = new Editor();
  editor.role.set('chords'); editor.local.set(true); editor.values.set({ rhythm: 'full' });
  const scope = { kind: 'section-track', sectionId: service.project().arrangement[0].id, trackId: 'track-chords-1' };
  for (const [selector, flip, value] of [['rhythm', 'patternFlip', 'tresillo'], ['chordSpanFill', 'fillFlip', 'ODD']]) {
    preferences.beginQuickSettings();
    preferences.moveControl('chords', selector);
    assert.equal(preferences.quickSettings().chords.includes(selector), false);
    assert.equal(preferences.quickSettings().chords.includes(flip), false);
    if (selector === 'rhythm') assert.equal(preferences.quickSettings().chords.includes('customPattern'), false);
    preferences.moveControl('chords', selector === 'rhythm' ? 'customPattern' : flip);
    assert.ok(preferences.quickSettings().chords.includes(selector));
    assert.ok(preferences.quickSettings().chords.includes(flip));
    preferences.saveQuickSettings();
    const control = editor.controls().find(control => control.key === selector);
    editor.overriddenKeys.set([flip]);
    assert.equal(editor.inheritanceFor(control), 'custom', 'flip-only override enables the shared reset');
    const keys = load('core/ui/ui-preferences.ts').combinedControlKeys(selector);
    if (selector === 'rhythm') {
      assert.ok(preferences.quickSettings().chords.includes('customPattern'));
      editor.overriddenKeys.set(['customPattern']);
      assert.equal(editor.inheritanceFor(control), 'custom', 'grid-only override enables the shared reset');
    }
    service.updatePartSettings(scope, { [selector]: value, [flip]: true, pauseChance: 12,
      ...(selector === 'rhythm' ? { customPattern: Array(32).fill(0) } : {}) });
    const before = service.exportProjectJson();
    let resetKeys;
    editor.inheritRequested.emit = value => { resetKeys = value; };
    editor.resetControl(control);
    assert.deepEqual(resetKeys, keys);
    service.resetPartSettings(scope, keys);
    const remaining = service.project().arrangement[0].trackPartOverrides[scope.trackId];
    assert.equal(remaining[selector], undefined); assert.equal(remaining[flip], undefined);
    if (selector === 'rhythm') assert.equal(remaining.customPattern, undefined);
    assert.equal(remaining.pauseChance, 12);
    const after = service.exportProjectJson();
    service.undo(); assert.equal(service.exportProjectJson(), before);
    service.redo(); assert.equal(service.exportProjectJson(), after);
  }
});

test('placement editing exposes dormant controls and partitions customized quick/inspector sets', () => {
  const { load, uiPreferences: preferences, settings } = fixture();
  const Editor = load('features/part-settings/part-settings-editor.component.ts').PartSettingsEditorComponent;
  const quick = new Editor(), inspector = new Editor();
  for (const editor of [quick, inspector]) { editor.role.set('bass'); editor.values.set({ rhythm: 'bass1' }); }
  quick.advanced.set(false); inspector.inspectorOnly.set(true);
  assert.equal(quick.controls().some(control => control.key === 'hitsPerPattern'), false);
  preferences.beginQuickSettings();
  assert.ok(quick.controls().some(control => control.key === 'hitsPerPattern'));
  preferences.moveControl('bass', 'feedbackCount'); preferences.moveControl('bass', 'transpose');
  const quickKeys = quick.controls().map(control => control.key), inspectorKeys = inspector.controls().map(control => control.key);
  assert.ok(quickKeys.includes('feedbackCount')); assert.ok(inspectorKeys.includes('transpose'));
  assert.equal(quickKeys.some(key => inspectorKeys.includes(key)), false);
  assert.deepEqual([...quickKeys, ...inspectorKeys].sort(), settings.PART_CONTROLS.bass.filter(control => !['patternFlip', 'fillFlip', 'customPattern', 'customDurations', 'customDurationChances', 'customDurationRandomWeighting'].includes(control.key)).map(control => control.key).sort());
  preferences.saveQuickSettings();
  assert.equal(quick.controls().some(control => control.key === 'hitsPerPattern'), false);
});

test('shortcut changes persist and transfer as UI preferences without changing project history', () => {
  const { service, uiPreferences: preferences, load } = fixture();
  const before = service.exportProjectJson(), undo = service.canUndo();
  const binding = { kind: 'mouse', input: '0', ctrl: true, shift: true, alt: true };
  preferences.setShortcut('reroll', binding);
  assert.equal(preferences.matchesShortcut('reroll', { button: 0, ctrlKey: true, shiftKey: true, altKey: true }), true);
  assert.equal(preferences.matchesShortcut('reroll', { button: 1, shiftKey: true }), false);
  const saved = preferences.exportJson();
  const restored = new (load('core/ui/ui-preferences.service.ts').UiPreferencesService)();
  assert.deepEqual(restored.shortcuts().reroll, [binding]);
  preferences.resetShortcuts();
  assert.equal(preferences.matchesShortcut('reroll', { button: 1, shiftKey: true }), true);
  assert.equal(preferences.importJson(saved), true);
  assert.deepEqual(preferences.shortcuts().reroll, [binding]);
  assert.equal(preferences.importJson(JSON.stringify({ ...JSON.parse(saved), shortcuts: { reroll: [null] } })), false);
  assert.equal(preferences.exportJson(), saved);
  preferences.setShortcut('reroll', null);
  assert.equal(preferences.shortcuts().reroll.length, 0);
  assert.equal(service.exportProjectJson(), before);
  assert.equal(service.canUndo(), undo);
});

test('progressive track coloring defaults on, persists, transfers and resets independently of project history', () => {
  const { service, uiPreferences, load } = fixture();
  const { UiPreferencesService } = load('core/ui/ui-preferences.service.ts');
  const before = service.exportProjectJson(), history = service.historyPosition();
  assert.equal(uiPreferences.appearance().progressiveTrackColoring, true);
  uiPreferences.toggleProgressiveTrackColoring();
  assert.equal(new UiPreferencesService().appearance().progressiveTrackColoring, false);
  const saved = uiPreferences.exportJson();
  uiPreferences.resetAppearance();
  assert.equal(uiPreferences.appearance().progressiveTrackColoring, true);
  assert.equal(uiPreferences.importJson(saved), true);
  assert.equal(uiPreferences.appearance().progressiveTrackColoring, false);
  const invalid = JSON.parse(saved); invalid.appearance.progressiveTrackColoring = 'false';
  assert.equal(uiPreferences.importJson(JSON.stringify(invalid)), false);
  assert.equal(uiPreferences.exportJson(), saved);
  const older = JSON.parse(saved); delete older.appearance.progressiveTrackColoring;
  assert.equal(uiPreferences.importJson(JSON.stringify(older)), true);
  assert.equal(uiPreferences.appearance().progressiveTrackColoring, true);
  assert.equal(service.exportProjectJson(), before);
  assert.equal(service.historyPosition(), history);
});

test('theme import changes only appearance and validates atomically', () => {
  const { uiPreferences: prefs } = fixture();
  const before = JSON.parse(prefs.exportJson());
  const imported = structuredClone(before);
  imported.appearance.roleColors.melody = '#123456';
  imported.compactHistory = !before.compactHistory;
  imported.settingsExplainersAsTooltips = false;
  assert.equal(prefs.importThemeJson(JSON.stringify(imported)), true);
  assert.deepEqual(JSON.parse(prefs.exportJson()), { ...before, appearance: imported.appearance });
  const saved = prefs.exportJson();
  imported.appearance.roleColors.melody = 'invalid';
  assert.equal(prefs.importThemeJson(JSON.stringify(imported)), false);
  assert.equal(prefs.exportJson(), saved);
  prefs.toggleSettingsExplainers();
  assert.equal(prefs.preferences().settingsExplainersAsTooltips, false);
  assert.equal(prefs.importJson(prefs.exportJson()), true);
  assert.equal(prefs.preferences().settingsExplainersAsTooltips, false);
});

test('MIDI PPQ defaults to 960 and persists only supported resolutions', () => {
  const { uiPreferences: prefs, load } = fixture();
  const { UiPreferencesService } = load('core/ui/ui-preferences.service.ts');
  assert.equal(prefs.midiPpq(), 960);
  prefs.setMidiPpq(1920);
  assert.equal(new UiPreferencesService().midiPpq(), 1920);
  const saved = prefs.exportJson();
  prefs.setMidiPpq(0);
  assert.equal(prefs.exportJson(), saved);
  assert.equal(prefs.importJson(JSON.stringify({ ...JSON.parse(saved), midiPpq: 123 })), false);
  const old = JSON.parse(saved); delete old.midiPpq;
  assert.equal(prefs.importJson(JSON.stringify(old)), true);
  assert.equal(prefs.midiPpq(), 960);
});


test('window width limits default to unlimited, persist, transfer and reject unsupported values', () => {
  const { service, uiPreferences: prefs, load } = fixture();
  const { UiPreferencesService } = load('core/ui/ui-preferences.service.ts');
  const before = service.exportProjectJson(), history = service.historyPosition();
  assert.equal(prefs.appearance().windowWidthLimit, 'none');
  for (const width of ['1440', '1600', '1920', '2560', '3840', 'none']) {
    prefs.setWindowWidthLimit(width);
    assert.equal(prefs.appearance().windowWidthLimit, width);
    assert.equal(new UiPreferencesService().appearance().windowWidthLimit, width);
    const saved = prefs.exportJson();
    assert.equal(prefs.importJson(saved), true);
    assert.equal(prefs.appearance().windowWidthLimit, width);
    for (const invalid of ['1280', '1920px', '', 1920, null]) {
      prefs.setWindowWidthLimit(invalid);
      assert.equal(prefs.exportJson(), saved);
      const candidate = JSON.parse(saved); candidate.appearance.windowWidthLimit = invalid;
      assert.equal(prefs.importJson(JSON.stringify(candidate)), false);
      assert.equal(prefs.exportJson(), saved);
    }
  }
  prefs.setWindowWidthLimit('2560');
  prefs.randomizeAppearance('dark');
  assert.equal(prefs.appearance().windowWidthLimit, '2560');
  const older = JSON.parse(prefs.exportJson()); delete older.appearance.windowWidthLimit;
  assert.equal(prefs.importJson(JSON.stringify(older)), true);
  assert.equal(prefs.appearance().windowWidthLimit, 'none');
  prefs.setWindowWidthLimit('1600'); prefs.resetAppearance();
  assert.equal(prefs.appearance().windowWidthLimit, 'none');
  assert.equal(service.exportProjectJson(), before);
  assert.equal(service.historyPosition(), history);
});

test('panel layout persists, transfers, defaults and stays outside project history', () => {
  const { service, uiPreferences: prefs, load } = fixture();
  const { UiPreferencesService } = load('core/ui/ui-preferences.service.ts');
  const before = service.exportProjectJson(), history = service.historyPosition();
  assert.equal(prefs.appearance().panelLayout, 'spacious');
  prefs.setPanelLayout('compact');
  assert.equal(new UiPreferencesService().appearance().panelLayout, 'compact');
  const saved = prefs.exportJson();
  for (const invalid of ['dense', '', null, 1]) {
    prefs.setPanelLayout(invalid);
    assert.equal(prefs.exportJson(), saved);
    const candidate = JSON.parse(saved); candidate.appearance.panelLayout = invalid;
    assert.equal(prefs.importJson(JSON.stringify(candidate)), false);
    assert.equal(prefs.exportJson(), saved);
  }
  prefs.setPanelLayout('spacious');
  assert.equal(prefs.importJson(saved), true);
  assert.equal(prefs.appearance().panelLayout, 'compact');
  prefs.randomizeAppearance('dark');
  assert.equal(prefs.appearance().panelLayout, 'compact');
  const older = JSON.parse(saved); delete older.appearance.panelLayout;
  assert.equal(prefs.importJson(JSON.stringify(older)), true);
  assert.equal(prefs.appearance().panelLayout, 'spacious');
  prefs.setPanelLayout('compact'); prefs.resetAppearance();
  assert.equal(prefs.appearance().panelLayout, 'spacious');
  assert.equal(service.exportProjectJson(), before);
  assert.equal(service.historyPosition(), history);
});

 test('applying adjustments stores the visible palette and persists neutral knobs', () => {
  for (const includeRoles of [false, true]) {
    const { uiPreferences: preferences, load } = fixture();
    const { adjustColor } = load('core/ui/appearance.ts');
    if (includeRoles) preferences.toggleHueShiftRoles();
    preferences.setColorAdjustment('brightness', -20);
    preferences.setColorAdjustment('saturation', 30);
    preferences.setColorAdjustment('hueShift', 25);
    const before = preferences.appearance();
    preferences.applyAdjustmentsToBaseColors();
    const after = preferences.appearance();
    for (const [key, color] of Object.entries(before.themeColors))
      assert.equal(after.themeColors[key], adjustColor(color, before.colorAdjustments));
    for (const [role, color] of Object.entries(before.roleColors))
      assert.equal(after.roleColors[role], adjustColor(color, { ...before.colorAdjustments, hueShift: includeRoles ? 25 : 0 }));
    assert.deepEqual(after.colorAdjustments, { brightness: 0, saturation: 0, hueShift: 0 });
    assert.deepEqual(new (load('core/ui/ui-preferences.service.ts').UiPreferencesService)().appearance(), after);
    preferences.applyAdjustmentsToBaseColors();
    assert.deepEqual(preferences.appearance(), after);
  }
});
test('note shapes persist independently of projects and validate atomically', () => {
  const { service, uiPreferences: prefs, load } = fixture();
  const { UiPreferencesService } = load('core/ui/ui-preferences.service.ts');
  const before = service.exportProjectJson();
  assert.equal(prefs.scoreNoteShape(), 'classic');
  for (const shape of ['classic', 'triangle', 'instrument', 'instrument-log']) {
    prefs.setScoreNoteShape(shape);
    assert.equal(new UiPreferencesService().scoreNoteShape(), shape);
    const saved = prefs.exportJson();
    prefs.setScoreNoteShape('invalid');
    assert.equal(prefs.exportJson(), saved);
    assert.equal(prefs.importJson(JSON.stringify({ ...JSON.parse(saved), scoreNoteShape: true })), false);
    assert.equal(prefs.exportJson(), saved);
    const older = JSON.parse(saved); delete older.scoreNoteShape;
    assert.equal(prefs.importJson(JSON.stringify(older)), true);
    assert.equal(prefs.scoreNoteShape(), 'classic');
    assert.equal(prefs.importJson(saved), true);
    assert.equal(prefs.scoreNoteShape(), shape);
  }
  assert.equal(service.exportProjectJson(), before);
});

test('harmony mode preference defaults to all, persists and validates independently of project history', () => {
  const { service, uiPreferences: prefs, load } = fixture();
  const { UiPreferencesService } = load('core/ui/ui-preferences.service.ts');
  const before = service.exportProjectJson(), history = service.historyPosition();
  assert.equal(prefs.harmonyFollowsMode(), 'all');
  for (const scope of ['off', 'global', 'all']) {
    prefs.setHarmonyFollowsMode(scope);
    assert.equal(new UiPreferencesService().harmonyFollowsMode(), scope);
  }
  const saved = prefs.exportJson();
  prefs.setHarmonyFollowsMode('invalid');
  assert.equal(prefs.exportJson(), saved);
  assert.equal(prefs.importJson(JSON.stringify({ ...JSON.parse(saved), harmonyFollowsMode: true })), false);
  const old = JSON.parse(saved); delete old.harmonyFollowsMode;
  assert.equal(prefs.importJson(JSON.stringify(old)), true);
  assert.equal(prefs.harmonyFollowsMode(), 'all');
  assert.equal(service.exportProjectJson(), before);
  assert.equal(service.historyPosition(), history);
});

test('mode edits reinterpret explicit global and section chords by scope in one undoable edit', () => {
  for (const scope of ['off', 'global', 'all']) {
    const { service, uiPreferences: prefs } = fixture();
    prefs.setHarmonyFollowsMode(scope);
    service.updateSettings({ key: 'C', scale: 'major' });
    service.setProgression([1,6,2,5]);
    const chords = [{ root:0, spice:'maj9', inversion:1 }, { root:9, spice:'m7', inversion:0 },
      { root:2, spice:'m7', inversion:0 }, { root:7, spice:'7', inversion:0 }];
    chords.forEach((chord,i) => service.setProgressionChord(i,chord));
    const section = service.project().arrangement[0];
    service.setSectionMusicalState(section.id, { customChords: chords, chordDegrees: [1,6,2,5] });
    const before = service.exportProjectJson();
    service.updateSettings({ scale: 'natural-minor' });
    const project = service.project();
    const expected = ['m9', 'maj7', 'm7b5', '7'];
    assert.deepEqual(project.progressionChords.map(c => c.spice), scope === 'off' ? chords.map(c => c.spice) : expected);
    assert.equal(project.progressionChords[1].root, scope === 'off' ? 9 : 8);
    assert.equal(project.progressionChords[0].inversion, 1);
    assert.deepEqual(project.arrangement[0].customChords.map(c => c.spice), scope === 'all' ? expected : chords.map(c => c.spice));
    service.undo();
    assert.equal(service.exportProjectJson(), before);
    service.redo();
    assert.equal(service.project().scale, 'natural-minor');
  }
});

test('All follows section mode edits and inherited modes while respecting explicit section modes', () => {
  const { service } = fixture();
  service.updateSettings({ key: 'C', scale: 'major' });
  const [section] = service.project().arrangement;
  service.setSectionMusicalState(section.id, { context: { scale: 'major' }, chordDegrees: [1],
    customChords: [{ root:0, spice:'maj7', inversion:0, explicitZeroInversion:true }] });
  service.updateSettings({ scale: 'natural-minor' });
  assert.equal(service.project().arrangement[0].customChords[0].spice, 'maj7');
  service.updateSectionContext(section.id, { scale: null });
  assert.equal(service.project().arrangement[0].customChords[0].spice, 'm7');
  assert.equal(service.project().arrangement[0].customChords[0].explicitZeroInversion, true);
});


test('local mode changes adapt inherited explicit global harmony without changing the global chords', () => {
  const { service } = fixture();
  service.updateSettings({ key: 'C', scale: 'major' });
  service.setProgression([1]);
  service.setProgressionChord(0, { root:0, spice:'maj9', inversion:-1 });
  const [section] = service.project().arrangement;
  service.updateSectionContext(section.id, { scale:'natural-minor' });
  assert.equal(service.project().progressionChords[0].spice, 'maj9');
  assert.equal(service.project().arrangement[0].customChords[0].spice, 'm9');
  assert.equal(service.project().arrangement[0].customChords[0].inversion, -1);
});
