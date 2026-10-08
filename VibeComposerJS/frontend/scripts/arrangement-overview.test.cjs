const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const ts = require('typescript');

// Exercise the component's real drawing and hit-testing code without a GPU or Angular bootstrap.
function fixture() {
  const refs = new Map();
  const signal = (value) => Object.assign(() => value, { set: (next) => { value = next; } });
  const input = (value) => signal(value);
  input.required = () => signal(undefined);
  const angular = {
    Component: () => (component) => component, ChangeDetectionStrategy: { OnPush: 0 },
    computed: (read) => read, effect: () => {}, input,
    output: () => ({ values: [], emit(value) { this.values.push(value); } }),
    viewChild: { required: (name) => () => ({ nativeElement: refs.get(name) }) },
  };
  const source = fs.readFileSync(path.join(__dirname, '../src/app/shared/arrangement-overview.component.ts'), 'utf8');
  const compiled = ts.transpileModule(source, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, experimentalDecorators: true },
  }).outputText;
  const module = { exports: {} };
  const dependencies = {
    '@angular/core': angular,
    '../core/project/project.model': { ARRANGED_PARTS: ['bass', 'chords', 'arpeggio', 'drums'] },
    '../core/music/arrangement-generator': { shouldGenerateTrackInSection: (section, track) => section.presentTracks.includes(track.id) },
    '../core/music/phrase': { layOutTrackPhrase: (project, track) => track.notes },
  };
  new Function('require', 'module', 'exports', compiled)((name) => dependencies[name], module, module.exports);
  const component = new module.exports.ArrangementOverviewComponent();
  const context = () => ({
    rectangles: [], clears: 0, fillStyle: '', globalAlpha: 1,
    setTransform() {}, clearRect() { this.clears++; },
    fillRect(...bounds) { this.rectangles.push({ bounds, color: this.fillStyle }); },
    save() {}, restore() {}, beginPath() {}, rect() {}, clip() {}, fillText() {},
    arc() {}, fill() {}, stroke() {}, strokeRect() {},
  });
  const base = context();
  const overlay = context();
  const canvas = (ctx) => ({ width: 0, height: 0, style: {}, getContext: () => ctx,
    getBoundingClientRect: () => ({ left: 0, top: 0, width: 800, height: 320 }), setAttribute() {} });
  refs.set('scoreCanvas', canvas(base)); refs.set('playheadCanvas', canvas(overlay));
  refs.set('scrollHost', { clientWidth: 800, clientHeight: 320, scrollLeft: 0, scrollTop: 0 });
  refs.set('scrollContent', { style: {} }); refs.set('viewport', { style: {} });
  global.document = { documentElement: {} };
  global.getComputedStyle = () => ({ getPropertyValue: (name) => name });
  global.devicePixelRatio = 2;
  global.requestAnimationFrame = () => 1;
  const track = { id: 'bass-1', role: 'bass', notes: [
    { id: 'n1', startBeat: 0, durationBeats: 1, midi: 48, velocity: 100 },
    { id: 'n2', startBeat: 8, durationBeats: 1, midi: 50, velocity: 100 },
    { id: 'n3', startBeat: 24, durationBeats: 1, midi: 52, velocity: 100 },
  ] };
  component.project.set({ tracks: [track], arrangement: [
    { id: 'intro', type: 'INTRO', measures: 2, presentTracks: [track.id] },
    { id: 'verse', type: 'VERSE1', measures: 4, presentTracks: [] },
    { id: 'outro', type: 'OUTRO', measures: 2, presentTracks: [track.id] },
  ] });
  component.selectedTrackId.set(track.id);
  component.ready = true;
  component.drawOverview();
  return { component, refs, base, overlay };
}

test('unequal section lengths share the exact beat mapping with notes and playback', () => {
  const { component, base, overlay } = fixture();
  const intro = component.hits.find((area) => area.key === 'heading:intro');
  const verse = component.hits.find((area) => area.key === 'heading:verse');
  assert.equal(verse.x, intro.x + intro.width);
  assert.equal(verse.width, intro.width * 2);
  const note = base.rectangles.find((rect) => rect.color === '--role-bass-strong' && rect.bounds[0] === verse.x && rect.bounds[3] === 2);
  assert.ok(note, 'note at beat 8 sits on the verse boundary');
  component.drawPlayhead(25); // Beat 8 of the 32-beat arrangement.
  assert.equal(overlay.rectangles.at(-1).bounds[0], verse.x);
});

test('scrolling preserves cursor alignment and keeps both canvases viewport-sized', () => {
  const { component, refs, overlay } = fixture();
  component.horizontalScale = 2;
  refs.get('scrollHost').scrollLeft = 60;
  component.drawOverview();
  component.drawPlayhead(25);
  assert.equal(overlay.rectangles.at(-1).bounds[0], component.beatX(8));
  for (const name of ['scoreCanvas', 'playheadCanvas']) {
    assert.equal(refs.get(name).width, 1600);
    assert.equal(refs.get(name).height, 640);
  }
  component.drawPlayhead(0);
  assert.equal(overlay.rectangles.at(-1).bounds[0], component.beatX(8), 'offscreen cursor is cleared rather than pinned to the labels');
});

test('the arrangement fits the available timeline width by default, including after resizing', () => {
  const { component, refs } = fixture();
  assert.equal(component.beatX(0), component.labelWidth);
  assert.equal(component.beatX(32), component.width);
  assert.equal(parseFloat(refs.get('scrollContent').style.width), refs.get('scrollHost').clientWidth);
  refs.get('scrollHost').clientWidth = 520;
  component.drawOverview();
  assert.equal(component.beatX(32), 520);
  assert.equal(refs.get('scrollHost').scrollLeft, 0);
});

test('Ctrl+wheel zoom anchors the cursor after scrolling and cancels browser zoom', () => {
  const { component, refs } = fixture();
  const host = refs.get('scrollHost');
  const cursorX = 400;
  const beatAtCursor = () => (host.scrollLeft + cursorX - component.labelWidth) / component.pixelsPerBeat;
  const initialBeat = beatAtCursor();
  const event = { ctrlKey: true, clientX: cursorX, deltaMode: 0, deltaY: -200, deltaX: 0,
    prevented: false, stopped: false,
    preventDefault() { this.prevented = true; }, stopPropagation() { this.stopped = true; } };
  component.onCanvasWheel(event);
  component.drawOverview();
  assert.equal(event.prevented, true);
  assert.equal(event.stopped, true);
  assert.ok(component.horizontalScale > 1);
  assert.ok(Math.abs(beatAtCursor() - initialBeat) < 1e-9);
  host.scrollLeft += 40;
  component.drawOverview();
  const scrolledBeat = beatAtCursor();
  component.onCanvasWheel(event);
  component.drawOverview();
  assert.ok(Math.abs(beatAtCursor() - scrolledBeat) < 1e-9);
  assert.equal(refs.get('scoreCanvas').width, 1600);
  assert.equal(refs.get('scoreCanvas').height, 640);
  component.onCanvasWheel({ ...event, deltaY: 100000 });
  component.drawOverview();
  assert.equal(component.horizontalScale, 1);
  assert.equal(host.scrollLeft, 0);
  assert.equal(component.beatX(32), component.width);
});

test('Alt, Shift, and unmodified wheel events keep their existing browser behavior', () => {
  const { component } = fixture();
  for (const keys of [{ altKey: true }, { shiftKey: true }, {}]) {
    let prevented = false;
    component.onCanvasWheel({ ...keys, ctrlKey: false, preventDefault() { prevented = true; } });
    assert.equal(prevented, false);
    assert.equal(component.horizontalScale, 1);
  }
});

test('playback updates clear only the overlay and do not rebuild note previews', () => {
  const { component, base, overlay } = fixture();
  const clears = base.clears;
  const overlayClears = overlay.clears;
  component.previewNotes = () => { throw new Error('Playback rebuilt previews'); };
  component.drawPlayhead(25);
  component.drawPlayhead(50);
  assert.equal(base.clears, clears);
  assert.equal(overlay.clears, overlayClears + 2);
});

test('track buttons select parts and middle-click toggles presence without selecting', () => {
  const { component } = fixture();
  const toggle = component.hits.find((area) => area.key === 'toggle:intro:bass-1');
  const area = component.hitAt({ clientX: toggle.x + 8, clientY: toggle.y + 8 });
  assert.equal(area.key, toggle.key);
  area.activate();
  assert.deepEqual(component.trackSelected.values.at(-1), { sectionId: 'intro', trackId: 'bass-1' });
  assert.equal(component.partToggled.values.length, 0);
  let prevented = false;
  component.onCanvasPointerdown({ button: 1, preventDefault() { prevented = true; } });
  assert.equal(prevented, true, 'middle-click autoscroll is prevented');
  component.onCanvasAuxclick({ button: 1, clientX: toggle.x + 8, clientY: toggle.y + 8, preventDefault() {} });
  assert.deepEqual(component.partToggled.values.at(-1), { sectionId: 'intro', trackId: 'bass-1', present: false });
  assert.equal(component.trackSelected.values.length, 1, 'toggle does not change settings selection');
  assert.equal(component.cellSelected.values.length, 0, 'track hit wins over cell hit');
  component.hits.find((candidate) => candidate.key === 'heading:verse').activate();
  assert.deepEqual(component.sectionSelected.values.at(-1), { sectionId: 'verse', trackId: 'bass-1' });
  component.hits.find((candidate) => candidate.key === 'group:chords').activate();
  assert.equal(component.groupSelected.values.at(-1), 'chords');
});

test('cell bodies select role settings while section selection is header-only', () => {
  const { component } = fixture();
  const cell = component.hits.find((area) => area.key === 'cell:verse:bass');
  component.onCanvasClick({ button: 0, clientX: cell.x + 5, clientY: cell.y + 5, ctrlKey: true });
  assert.deepEqual(component.cellSelected.values, [{ sectionId: 'verse', role: 'bass' }]);
  assert.equal(component.sectionSelected.values.length, 0);
  assert.equal(component.partToggled.values.length, 0);
  component.onCanvasAuxclick({ button: 1, clientX: cell.x + 5, clientY: cell.y + 5, preventDefault() {} });
  assert.equal(component.partToggled.values.length, 0, 'cell bodies cannot toggle presence');
});

test('absent parts select without including them and keyboard presence toggle is separate', () => {
  const { component } = fixture();
  const area = component.hits.find((hit) => hit.key === 'toggle:verse:bass-1');
  component.focusedKey = area.key;
  component.onCanvasKeydown({ key: 'Enter', preventDefault() {} });
  assert.deepEqual(component.trackSelected.values, [{ sectionId: 'verse', trackId: 'bass-1' }]);
  assert.equal(component.partToggled.values.length, 0);
  component.onCanvasKeydown({ key: 'i', preventDefault() {} });
  assert.deepEqual(component.partToggled.values, [{ sectionId: 'verse', trackId: 'bass-1', present: true }]);
  assert.equal(component.trackSelected.values.length, 1);
});

test('section hit areas forward Ctrl/Shift modifiers and Escape clears selection', () => {
  const { component } = fixture();
  const heading = component.hits.find((area) => area.key === 'heading:verse');
  component.onCanvasClick({ clientX: heading.x + 8, clientY: heading.y + 8,
    ctrlKey: true, metaKey: false, shiftKey: false });
  assert.deepEqual(component.sectionSelected.values.at(-1), {
    sectionId: 'verse', trackId: 'bass-1', ctrlKey: true, metaKey: false, shiftKey: false,
  });
  component.focusedKey = heading.key;
  component.onCanvasKeydown({ key: 'Enter', ctrlKey: false, metaKey: false, shiftKey: true, preventDefault() {} });
  assert.equal(component.sectionSelected.values.at(-1).shiftKey, true);
  component.onCanvasKeydown({ key: 'Escape', preventDefault() {} });
  assert.equal(component.selectionCleared.values.length, 1);
});

test('all selected section headers receive the selection highlight', () => {
  const { component, base } = fixture();
  component.selectedSectionIds.set(['intro', 'verse']);
  base.rectangles = [];
  component.drawOverview();
  const selectedHeaders = base.rectangles.filter((rect) => rect.color === '--surface-accent' && rect.bounds[1] === 0);
  assert.equal(selectedHeaders.length, 2);
});
