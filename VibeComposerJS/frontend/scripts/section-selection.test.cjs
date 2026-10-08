const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const ts = require('typescript');

function fixture() {
  const signal = (value) => Object.assign(() => value, { set: (next) => { value = next; } });
  const project = signal({ tracks: [{ id: 'track-bass-1', role: 'bass' }], arrangement: [
    { id: 'a', measures: 2 }, { id: 'b', measures: 4 }, { id: 'c', measures: 1 },
    { id: 'd', measures: 3 }, { id: 'e', measures: 2 },
  ] });
  const input = (value) => signal(value);
  input.required = () => signal(undefined);
  const viewChild = () => () => undefined;
  viewChild.required = () => () => undefined;
  const angular = { Injectable: () => (type) => type, Component: () => (type) => type,
    signal, computed: (read) => read, effect: () => {}, input, viewChild,
    output: () => ({ values: [], emit(value) { this.values.push(value); } }),
    ChangeDetectionStrategy: { OnPush: 0 },
  };
  const projects = { project };
  let ui;
  angular.inject = (type) => type === 'ui' ? ui : type === 'audio' ? {} : projects;
  const load = (file, dependencies = {}) => {
    const source = fs.readFileSync(path.join(__dirname, '../src/app/shared', file), 'utf8');
    const compiled = ts.transpileModule(source, { compilerOptions: {
      target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, experimentalDecorators: true,
    } }).outputText;
    const module = { exports: {} };
    const defaults = {
      '@angular/core': angular, '../core/project/project.service': { ProjectService: 'project' },
      '../core/audio/audio-playback.service': { AudioPlaybackService: 'audio' },
      './workspace-ui.service': { WorkspaceUiService: 'ui' },
      '../core/project/project.model': { ARRANGED_PARTS: ['melody', 'bass', 'chords', 'arpeggio', 'drums'], SECTION_TYPES: [],
        PART_GENERATION_AVAILABLE: { melody: false, bass: true, chords: true, arpeggio: true, drums: true },
        tracksInRoleOrder: (tracks) => [...tracks].sort((left, right) =>
          ['melody', 'bass', 'chords', 'arpeggio', 'drums'].indexOf(left.role) - ['melody', 'bass', 'chords', 'arpeggio', 'drums'].indexOf(right.role)) },
      '../core/music/harmony': { KEYS: [] },
    };
    new Function('require', 'module', 'exports', compiled)(
      (name) => dependencies[name] ?? defaults[name] ?? {}, module, module.exports);
    return module.exports;
  };
  const { WorkspaceUiService } = load('workspace-ui.service.ts');
  ui = new WorkspaceUiService();
  const { WorkspaceCanvasComponent } = load('workspace-canvas.component.ts');
  const workspace = new WorkspaceCanvasComponent();
  return { ui, project, workspace, load };
}

test('Ctrl-click fills intervening sections in either direction', () => {
  const { ui } = fixture();
  ui.selectSection('b');
  ui.selectSection('e', { ctrlKey: true });
  assert.deepEqual(ui.selectedSectionIds(), ['b', 'c', 'd', 'e']);
  ui.selectSection('a', { ctrlKey: true });
  assert.deepEqual(ui.selectedSectionIds(), ['a', 'b', 'c', 'd', 'e']);
});

test('Ctrl-click removing an interior section discards everything to its right', () => {
  const { ui } = fixture();
  ui.selectSection('a');
  ui.selectSection('e', { ctrlKey: true });
  ui.selectSection('c', { ctrlKey: true });
  assert.deepEqual(ui.selectedSectionIds(), ['a', 'b']);
  ui.selectSection('a', { ctrlKey: true });
  assert.deepEqual(ui.selectedSectionIds(), ['b'], 'removing the first endpoint leaves the rest');
  ui.selectSection('b', { ctrlKey: true });
  assert.deepEqual(ui.selectedSectionIds(), []);
});

test('Shift-click expands and shrinks around the original selection anchor', () => {
  const { ui } = fixture();
  ui.selectSection('c');
  ui.selectSection('e', { shiftKey: true });
  assert.deepEqual(ui.selectedSectionIds(), ['c', 'd', 'e']);
  ui.selectSection('b', { shiftKey: true });
  assert.deepEqual(ui.selectedSectionIds(), ['b', 'c']);
  ui.selectSection('d', { shiftKey: true });
  assert.deepEqual(ui.selectedSectionIds(), ['c', 'd']);
});

test('plain click replaces a range and clicking the lone selected section clears it', () => {
  const { ui } = fixture();
  ui.selectSection('a');
  ui.selectSection('c', { ctrlKey: true });
  ui.selectSection('b');
  assert.deepEqual(ui.selectedSectionIds(), ['b']);
  ui.selectSection('b');
  assert.deepEqual(ui.selectedSectionIds(), []);
  assert.equal(ui.selectedSectionId(), null);
  assert.equal(ui.sectionRange(), null);
  ui.selectSection('e', { shiftKey: true });
  assert.deepEqual(ui.selectedSectionIds(), ['e'], 'range selection with no anchor starts a new selection');
  ui.clearSectionSelection();
  assert.deepEqual(ui.selectedSectionIds(), []);
});

test('deletion, reordering, and imports cannot leave stale IDs or a gap', () => {
  const { ui, project } = fixture();
  ui.selectSection('b');
  ui.selectSection('c', { ctrlKey: true });
  const original = project();
  project.set({ ...original, arrangement: [original.arrangement[1], original.arrangement[4], original.arrangement[2]] });
  assert.deepEqual(ui.selectedSectionIds(), ['b', 'e', 'c']);
  project.set({ ...original, arrangement: original.arrangement.filter((section) => section.id !== 'b') });
  assert.deepEqual(ui.selectedSectionIds(), ['c']);
  project.set({ ...original, arrangement: [{ id: 'new', measures: 4 }] });
  assert.deepEqual(ui.selectedSectionIds(), []);
  assert.equal(ui.selectedSectionId(), null);
});

test('workspace score focus follows the selection range or the full arrangement when cleared', () => {
  const { ui, workspace, project } = fixture();
  assert.deepEqual(workspace.scoreSectionFocus(), { startBeat: 0, endBeat: 48 });
  workspace.selectOverviewSection('b', 'track-bass-1');
  assert.deepEqual(workspace.scoreSectionFocus(), { startBeat: 8, endBeat: 24 });
  workspace.selectOverviewSection('d', 'track-bass-1', { ctrlKey: true });
  assert.deepEqual(workspace.scoreSectionFocus(), { startBeat: 8, endBeat: 40 });
  project.set({ ...project(), arrangement: project().arrangement.map((section) =>
    section.id === 'b' ? { ...section, measures: 2 } : section) });
  assert.deepEqual(workspace.scoreSectionFocus(), { startBeat: 8, endBeat: 32 }, 'section length edits update the range');
  ui.clearSectionSelection();
  assert.deepEqual(workspace.scoreSectionFocus(), { startBeat: 0, endBeat: 40 });
});

test('cell and section-track contexts preserve the header range and score focus', () => {
  const { ui, workspace } = fixture();
  ui.selectSection('b');
  ui.selectSection('d', { shiftKey: true });
  const range = workspace.scoreSectionFocus();
  workspace.selectCell('e', 'bass');
  assert.deepEqual(ui.settingsTarget(), { kind: 'section-role', sectionId: 'e', role: 'bass' });
  assert.deepEqual(ui.selectedSectionIds(), ['b', 'c', 'd']);
  assert.deepEqual(workspace.scoreSectionFocus(), range);
  assert.equal(workspace.selectedSection(), null, 'range must not override the active cell inspector');
  workspace.selectSectionTrack('a', 'track-bass-1');
  assert.deepEqual(ui.settingsTarget(), { kind: 'section-track', sectionId: 'a', trackId: 'track-bass-1' });
  assert.deepEqual(workspace.scoreSectionFocus(), range);
  ui.selectSection('c');
  assert.equal(workspace.selectedSection().id, 'c');
  assert.equal(workspace.localPartTarget(), null);
});

test('global selection and cell selection do not create header ranges', () => {
  const { ui, workspace } = fixture();
  workspace.selectCell('b', 'bass');
  assert.deepEqual(ui.selectedSectionIds(), []);
  workspace.selectTrack('track-bass-1');
  assert.deepEqual(ui.settingsTarget(), { kind: 'global-track', trackId: 'track-bass-1' });
  workspace.selectRole('bass');
  assert.deepEqual(ui.settingsTarget(), { kind: 'global-role', role: 'bass' });
  assert.equal(ui.selectedRole(), 'bass');
  ui.clearSelection();
  assert.equal(ui.selectedRole(), null);
  assert.deepEqual(ui.selectedSectionIds(), []);
});

test('invalid or deleted part targets fall back safely without retaining a local scope', () => {
  const { ui, project } = fixture();
  ui.selectSectionTrack('b', 'missing-track');
  assert.equal(ui.settingsTarget().kind, 'global-track');
  ui.selectSectionTrack('b', 'track-bass-1');
  project.set({ ...project(), arrangement: project().arrangement.filter((section) => section.id !== 'b') });
  assert.deepEqual(ui.settingsTarget(), { kind: 'global-track', trackId: 'track-bass-1' });
});

test('score canvas fits an unequal section range and restores full-score zoom on clearing', () => {
  const { load, ui, workspace } = fixture();
  const { ScoreCanvasComponent } = load('score-canvas.component.ts');
  const score = new ScoreCanvasComponent();
  score.model = { tracks: [], notes: [], sections: [], totalBeats: 48, selectedTrackId: '', hiddenTrackIds: new Set() };
  score.app = { render() {}, canvas: { setAttribute() {} } };
  score.width = 800;
  score.height = 300;
  score.staticGraphics = { clear() {}, rect() { return this; }, fill() { return this; },
    moveTo() { return this; }, lineTo() { return this; }, stroke() { return this; } };
  score.colorClass = class { alpha = 1; toNumber() { return 0; } };
  global.document = { documentElement: {} };
  global.getComputedStyle = () => ({ getPropertyValue: () => '#000000' });
  ui.selectSection('b');
  ui.selectSection('d', { ctrlKey: true });
  score.pendingSectionFocus = workspace.scoreSectionFocus();
  score.drawStatic();
  assert.equal(score.horizontalScale, 48 / 32);
  assert.ok(Math.abs(score.scoreX(8)) < 1e-9);
  assert.ok(Math.abs(score.scoreX(40) - 800) < 1e-9);
  ui.clearSectionSelection();
  score.pendingSectionFocus = workspace.scoreSectionFocus();
  score.drawStatic();
  assert.equal(score.horizontalScale, 1);
  assert.equal(score.horizontalOffset, 0);
  assert.equal(score.scoreX(0), 0);
  assert.equal(score.scoreX(48), 800);
  ui.selectSection('a');
  ui.selectSection('e', { ctrlKey: true });
  score.pendingSectionFocus = workspace.scoreSectionFocus();
  score.drawStatic();
  score.horizontalScale = 5; // Manually zoom while every section is selected.
  score.horizontalOffset = 0.2;
  ui.clearSectionSelection();
  score.pendingSectionFocus = workspace.scoreSectionFocus();
  score.drawStatic();
  assert.equal(score.horizontalScale, 1, 'clearing a full-arrangement selection still refits');
  assert.equal(score.horizontalOffset, 0);
});

test('score time grid progressively reveals subdivisions with at least eight pixels between regular lines', () => {
  const { load } = fixture();
  const { ScoreCanvasComponent } = load('score-canvas.component.ts');
  const score = new ScoreCanvasComponent();
  score.model = { tracks: [], notes: [], sections: [], totalBeats: 512, selectedTrackId: '', hiddenTrackIds: new Set() };
  score.app = { render() {}, canvas: { setAttribute() {} } };
  score.width = 512;
  score.height = 300;
  const lines = [];
  let start, end;
  score.staticGraphics = { clear() { lines.length = 0; }, rect() { return this; }, fill() { return this; },
    moveTo(x, y) { start = [x, y]; return this; }, lineTo(x, y) { end = [x, y]; return this; },
    stroke(style) { lines.push({ start, end, color: style.color, alpha: style.alpha }); return this; } };
  score.colorClass = class { constructor(value) { this.value = value; } alpha = 1; toNumber() { return this.value; } };
  global.document = { documentElement: {} };
  global.getComputedStyle = () => ({ getPropertyValue: (name) => name });
  const gridLines = () => lines.filter((line) => line.color.startsWith('--grid-'));
  let previousAlpha = 0;
  for (const [scale, finest] of [[1, '--grid-measure'], [2, '--grid-measure'], [4, '--grid-beat'],
    [8, '--grid-beat'], [16, '--grid-eighth'], [32, '--grid-step'], [64, '--grid-step']]) {
    score.horizontalScale = scale;
    score.horizontalOffset = scale === 1 ? 0 : 0.137;
    score.drawStatic();
    const grid = gridLines();
    const measureAlpha = grid.find((line) => line.color === '--grid-measure').alpha;
    assert.ok(measureAlpha >= previousAlpha, 'the grid brightens progressively as finer divisions become visible');
    assert.ok(measureAlpha >= 0.22 && measureAlpha <= 1);
    if (scale < 32) assert.ok(measureAlpha < 1, 'coarser grids dim their main divisions');
    else assert.equal(measureAlpha, 1, 'full-detail grid retains its normal brightness');
    previousAlpha = measureAlpha;
    assert.ok(grid.some((line) => line.color === finest), `finest level at scale ${scale}`);
    const allowed = ['--grid-measure', '--grid-beat', '--grid-eighth', '--grid-step'];
    assert.ok(grid.every((line) => allowed.indexOf(line.color) <= allowed.indexOf(finest)));
    for (let index = 1; index < grid.length; index++) {
      assert.ok(grid[index].start[0] - grid[index - 1].start[0] >= 8);
    }
    for (const line of grid) {
      const beat = (line.start[0] - 0.5) / scale + score.horizontalOffset * 512;
      assert.ok(Math.abs(beat * 4 - Math.round(beat * 4)) <= 4 / scale + 1e-7, 'grid stays aligned with musical subdivisions while scrolling');
    }
  }
  score.horizontalScale = 1;
  score.horizontalOffset = 0;
  score.model = { ...score.model, sections: [{ startBeat: 3 }, { startBeat: 17 }] };
  score.drawStatic();
  assert.ok(gridLines().some((line) => line.start[0] === 3.5), 'section boundaries remain visible between coarse bar lines');
  assert.ok(gridLines().some((line) => line.start[0] === 17.5));
  assert.ok(gridLines().every((line) => line.alpha < 1), 'section markers also dim at overview scale');
});
