const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const ts = require('typescript');

function fixture() {
  const input = value => () => value;
  input.required = () => () => undefined;
  const viewChild = () => () => undefined;
  viewChild.required = viewChild;
  const angular = { Component: () => type => type, ChangeDetectionStrategy: { OnPush: 0 },
    effect() {}, input, viewChild, signal: initial => { let value = initial; const read = () => value; read.set = next => value = next; return read; }, output: () => ({ values: [], emit(value) { this.values.push(value); } }) };
  const source = fs.readFileSync(path.join(__dirname, '../src/app/shared/score-canvas.component.ts'), 'utf8');
  const compiled = ts.transpileModule(source, { compilerOptions: {
    target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, experimentalDecorators: true,
  } }).outputText;
  const module = { exports: {} };
  new Function('require', 'module', 'exports', compiled)(() => angular, module, module.exports);
  const component = new module.exports.ScoreCanvasComponent();
  component.width = 800;
  component.height = 300;
  component.model = { totalBeats: 64, tracks: [{ id: 'bass', name: 'Bass' }] };
  component.horizontalOffset = .25;
  component.horizontalScale = 4;
  const canvas = { setPointerCapture() {}, releasePointerCapture() {}, hasPointerCapture: () => true, style: {}, setAttribute() {}, getBoundingClientRect: () => ({ left: 100, top: 10, width: 400, height: 150 }) };
  component.app = { canvas, render() {} };
  const strokes = [];
  const fills = [];
  component.colorClass = class { alpha = 1; toNumber() { return 123; } };
  component.dynamicGraphics = { clear() { strokes.length = 0; }, moveTo(x, y) { this.x = x; return this; },
    lineTo() { return this; }, roundRect() { return this; },
    fill(style) { fills.push(style); return this; },
    stroke(style) { strokes.push({ x: this.x, ...style }); return this; } };
  global.document = { documentElement: {} };
  global.getComputedStyle = () => ({ getPropertyValue: () => '#32d7f4' });
  return { component, canvas, strokes, fills };
}

test('empty score clicks seek through the zoomed and panned viewport, including scaled canvas bounds', () => {
  const { component } = fixture();
  component.onCanvasClick({ button: 0, clientX: 300, clientY: 20 });
  assert.deepEqual(component.seekRequested.values, [24]);
  assert.deepEqual(component.trackSelected.values, [component.model.tracks[0]]);
  assert.deepEqual(component.noteSelected.values, []);
  component.onCanvasClick({ button: 1, clientX: 300, clientY: 20 });
  assert.equal(component.seekRequested.values.length, 1);
});

test('clicking a note emits only note selection even in a zoomed viewport', () => {
  const { component } = fixture();
  const note = { id: 'note', part: 'bass', color: 'bass', midi: 48, velocity: 100 };
  const hit = { note, x: 390, y: 15, width: 20, height: 10 };
  component.hitAreas = [hit];
  component.hitColumnCount = 9;
  component.hitAreaBuckets.set(4, [hit]);
  component.onCanvasClick({ button: 0, clientX: 300, clientY: 20 });
  assert.deepEqual(component.noteSelected.values, [note]);
  assert.deepEqual(component.seekRequested.values, []);
  assert.deepEqual(component.trackSelected.values, []);
});

test('the mouse guide is fainter than the playhead and disappears on pointer leave', () => {
  const { component, strokes } = fixture();
  component.playhead = 37.5;
  component.onPointerMove({ clientX: 250, clientY: 20 });
  assert.equal(strokes[0].x, 300.5);
  assert.equal(strokes[0].alpha, .25);
  assert.equal(strokes[1].x, 400.5);
  assert.equal(strokes[1].alpha, 1);
  component.onPointerLeave();
  assert.equal(strokes.length, 1);
  assert.equal(strokes[0].alpha, 1);
});

test('edit drags stay within the phrase and publish a draft change only on release', () => {
  const { component } = fixture();
  component.editable = () => true;
  component.editBounds = () => ({ startBeat: 20, endBeat: 24 });
  const note = { id: 'drag', part: 'bass', color: 'bass', midi: 48, velocity: 100, startBeat: 20, durationBeats: 1 };
  const hit = { note, x: 390, y: 15, width: 25, height: 10 };
  component.hitAreas = [hit]; component.hitColumnCount = 9; component.hitAreaBuckets.set(4, [hit]);
  component.onPointerDown({ button: 0, clientX: 300, clientY: 20, pointerId: 1 });
  component.onPointerMove({ clientX: 700, clientY: 20, pointerId: 1 });
  assert.deepEqual(component.noteChanged.values, []);
  component.onPointerUp({ pointerId: 1 });
  assert.equal(component.noteChanged.values.length, 1);
  assert.equal(component.noteChanged.values[0].startBeat, 23);
  assert.equal(component.noteChanged.values[0].midi, 48);
});

test('middle click applies only in edit mode and outside notes', () => {
  const { component } = fixture();
  const event = { button: 1, clientX: 300, clientY: 20, preventDefault() {} };
  component.onAuxClick(event); assert.deepEqual(component.applyRequested.values, []);
  component.editable = () => true;
  component.onAuxClick(event); assert.equal(component.applyRequested.values.length, 1);
  const hit = { note: {}, x: 390, y: 15, width: 25, height: 10 };
  component.hitAreas = [hit]; component.hitColumnCount = 9; component.hitAreaBuckets.set(4, [hit]);
  component.onAuxClick(event); assert.equal(component.applyRequested.values.length, 1);
});

test('playhead glow follows the actual note duration independently of the enlarged hit target', () => {
  const { component, fills } = fixture();
  component.hitAreas = [{ note: { color: 'bass', startBeat: 20, durationBeats: 1 }, x: 195, y: 30, width: 60, height: 6 }];
  component.playhead = 20.5 / 64 * 100; component.drawDynamic(); assert.equal(fills.length, 1);
  component.playhead = 21 / 64 * 100; component.drawDynamic(); assert.equal(fills.length, 1, 'ending notes stop glowing at their true boundary');
});
