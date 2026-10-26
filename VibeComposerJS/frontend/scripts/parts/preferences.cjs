const assert = require('node:assert/strict');
const test = require('node:test');
const { fixture } = require('../support/project-fixture.cjs');

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
  preferences.setThemeColor('roleGroups', '#504060');
  const tokens = themeColorTokens(preferences.appearance().themeColors);
  assert.equal(tokens['--surface-inspector'], '#211831');
  assert.equal(tokens['--surface-role-group'], '#504060');
  assert.equal(tokens['--surface-role-group-selected'], '#3a314a');
  assert.equal(tokens['--surface-base'], DEFAULT_THEME_COLORS.surface);
  const saved = preferences.exportJson();
  const restored = new (load('core/ui/ui-preferences.service.ts').UiPreferencesService)();
  assert.deepEqual(restored.appearance(), preferences.appearance());
  for (const key of ['inspector', 'roleGroups']) {
    const invalid = JSON.parse(saved);
    invalid.appearance.themeColors[key] = 'invalid';
    assert.equal(preferences.importJson(JSON.stringify(invalid)), false);
    assert.equal(preferences.exportJson(), saved);
  }
  const omitted = JSON.parse(saved);
  delete omitted.appearance.themeColors.inspector;
  delete omitted.appearance.themeColors.roleGroups;
  assert.equal(preferences.importJson(JSON.stringify(omitted)), true);
  assert.equal(preferences.appearance().themeColors.inspector, DEFAULT_THEME_COLORS.inspector);
  assert.equal(preferences.appearance().themeColors.roleGroups, DEFAULT_THEME_COLORS.roleGroups);
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
  assert.equal(uiStored.size, 1);
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
