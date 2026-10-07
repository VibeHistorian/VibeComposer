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

test('track toggles take precedence over section cells and preserve presence semantics', () => {
  const { component } = fixture();
  const toggle = component.hits.find((area) => area.key === 'toggle:intro:bass-1');
  const area = component.hitAt({ clientX: toggle.x + 8, clientY: toggle.y + 8 });
  assert.equal(area.key, toggle.key);
  area.activate();
  assert.deepEqual(component.partToggled.values.at(-1), { sectionId: 'intro', trackId: 'bass-1', present: false });
  component.hits.find((candidate) => candidate.key === 'heading:verse').activate();
  assert.deepEqual(component.sectionSelected.values.at(-1), { sectionId: 'verse', trackId: 'bass-1' });
  component.hits.find((candidate) => candidate.key === 'group:chords').activate();
  assert.equal(component.groupSelected.values.at(-1), 'chords');
});
