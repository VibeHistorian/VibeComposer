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
    effect() {}, inject: () => ({ appearance: () => 'dark', scoreChordGrid: () => false,
      drumLabel: pitch => require('./music-loader.cjs').loadMusic('drum-instruments.ts').drumInstrumentLabel(pitch, 'general-midi') }), input, viewChild, signal: initial => { let value = initial; const read = () => value; read.set = next => value = next; return read; }, output: () => ({ values: [], emit(value) { this.values.push(value); } }) };
  const source = fs.readFileSync(path.join(__dirname, '../src/app/shared/score-canvas.component.ts'), 'utf8');
  const compiled = ts.transpileModule(source, { compilerOptions: {
    target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, experimentalDecorators: true,
  } }).outputText;
  const module = { exports: {} };
  const helperModule = { exports: {} };
  const helperSource = ts.transpileModule(fs.readFileSync(path.join(__dirname, '../src/app/core/music/phrase-edit.ts'), 'utf8'),
    { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } }).outputText;
  new Function('module', 'exports', helperSource)(helperModule, helperModule.exports);
  new Function('require', 'module', 'exports', compiled)(name => name.includes('phrase-edit') ? helperModule.exports
    : name.includes('drum-instruments') ? require('./music-loader.cjs').loadMusic('drum-instruments.ts') : angular, module, module.exports);
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

test('optional chord grid draws chord colors at half the previous opacity only when enabled', () => {
  const { component: c } = fixture();
  c.horizontalOffset = 0; c.horizontalScale = 1;
  c.model = { tracks: [{ id: 'bass', color: 'bass' }], notes: [], sections: [], totalBeats: 8, selectedTrackId: 'bass', hiddenTrackIds: new Set() };
  c.chordBoundaries = () => [{ beat: 2, hue: 60 }, { beat: 6, hue: 150 }, { beat: 16, hue: 0 }];
  const strokes = [], tints = [];
  c.colorClass = class { alpha = 1; constructor(value) { tints.push(value); } toNumber() { return 123; } };
  let x;
  c.staticGraphics = { clear() { strokes.length = 0; }, rect() { return this; }, roundRect() { return this; },
    moveTo(left) { x = left; return this; }, lineTo() { return this; }, fill() { return this; },
    stroke(style) { strokes.push({ x, ...style }); return this; } };
  global.getComputedStyle = () => ({ getPropertyValue: key => key === '--chordlet-foreground-tone' ? '70% 84%' : '#32d7f4' });
  c.drawStatic();
  assert.equal(strokes.some(stroke => stroke.alpha === .175), false);
  c.preferences.scoreChordGrid = () => true;
  c.drawStatic();
  assert.deepEqual(strokes.filter(stroke => stroke.alpha === .175).map(stroke => stroke.x), [200.5, 600.5]);
  assert.ok(tints.includes('hsl(60 70% 84%)')); assert.ok(tints.includes('hsl(150 70% 84%)'));
  c.preferences.scoreChordGrid = () => false;
  c.drawStatic();
  assert.equal(strokes.some(stroke => stroke.alpha === .175), false);
});

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

test('middle press on an overview note requests its track context without opening through normal selection', () => {
  const { component } = fixture();
  const note = { id: 'note', part: 'bass', color: 'bass', midi: 48, velocity: 100 };
  const hit = { note, x: 390, y: 15, width: 20, height: 10 };
  component.hitAreas = [hit]; component.hitColumnCount = 9; component.hitAreaBuckets.set(4, [hit]);
  let prevented = false;
  const event = { button: 1, clientX: 300, clientY: 20, pointerId: 1,
    preventDefault() { prevented = true; }, stopPropagation() {} };
  component.onPointerDown(event);
  assert.equal(prevented, true);
  assert.deepEqual(component.noteTrackSelected.values, [note]);
  component.onPointerUp(event); component.onAuxClick(event);
  assert.deepEqual(component.noteSelected.values, []);
  assert.deepEqual(component.applyRequested.values, []);
  component.onPointerDown({ ...event, clientX: 330 });
  assert.deepEqual(component.noteTrackSelected.values, [note], 'empty space does not select a track');
  component.editable = () => true;
  component.model.notes = [note];
  component.onPointerDown(event);
  assert.deepEqual(component.noteTrackSelected.values, [note], 'editor middle press retains its editing gesture');
  assert.equal(component.drag.mode, 'velocity');
});

test('note hover immediately shows pitch, velocity, custom track order and source, and clears on exit', () => {
  const { component, canvas } = fixture();
  const note = { id: 'note', part: 'bass', trackName: 'Deep bass', trackOrder: 2, color: 'bass', midi: 61, velocity: 103, phraseSource: 'section-edited' };
  const hit = { note, x: 390, y: 15, width: 20, height: 10 };
  component.hitAreas = [hit]; component.hitColumnCount = 9; component.hitAreaBuckets.set(4, [hit]);
  component.onPointerMove({ clientX: 300, clientY: 20 });
  assert.deepEqual(component.noteTooltip(), { track: 'Deep bass (#2)', color: 'var(--role-bass)', pitch: 'C♯4 [61]', velocity: 103,
    source: 'Section edited phrase', x: 414, y: 34 });
  assert.equal(canvas.title, undefined);
  component.onPointerMove({ clientX: 330, clientY: 20 });
  assert.equal(component.noteTooltip(), null);
  component.showNoteTooltip({ ...hit, note: { ...note, trackName: 'B2', midi: 0, phraseSource: 'global-generated' } }, 799, 299);
  assert.equal(component.noteTooltip().track, 'B2');
  assert.equal(component.noteTooltip().pitch, 'C-1 [0]');
  assert.ok(component.noteTooltip().x + 240 <= component.width);
  assert.ok(component.noteTooltip().y + 76 <= component.height);
  component.onPointerLeave();
  assert.equal(component.noteTooltip(), null);
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

test('playhead preserves approximate light intensity across adjacent columns in a zoomed viewport', () => {
  const { component, strokes } = fixture();
  for (const x of [400, 400.25, 400.5, 400.75, 401]) {
    component.playhead = (component.horizontalOffset + x / (component.width * component.horizontalScale)) * 100;
    component.drawDynamic();
    assert.ok(Math.abs(strokes.reduce((sum, stroke) => sum + Math.pow(stroke.alpha, 2.2), 0) - 1) < 1e-9);
    const center = strokes.reduce((sum, stroke) => sum + (stroke.x - .5) * Math.pow(stroke.alpha, 2.2), 0);
    assert.ok(Math.abs(center - x) < 1e-9);
    assert.ok(strokes.every(stroke => stroke.width === 1));
  }
  component.playhead = 50;
  component.drawDynamic();
  assert.equal(strokes[0].x, 799.5);
  assert.equal(strokes[0].alpha, 1);
});

test('edit drags stay within the phrase and publish a draft change only on release', () => {
  const { component } = fixture();
  component.editable = () => true;
  component.editBounds = () => ({ startBeat: 20, endBeat: 24 });
  const note = { id: 'drag', part: 'bass', color: 'bass', midi: 48, velocity: 100, startBeat: 20, durationBeats: 1 };
  const hit = { note, x: 390, y: 15, width: 25, height: 10 };
  component.hitAreas = [hit]; component.hitColumnCount = 9; component.hitAreaBuckets.set(4, [hit]);
  component.model.notes = [note];
  component.onPointerDown({ preventDefault() {}, button: 0, clientX: 300, clientY: 20, pointerId: 1 });
  component.onPointerMove({ clientX: 700, clientY: 20, pointerId: 1 });
  assert.deepEqual(component.noteChanged.values, []);
  component.onPointerUp({ pointerId: 1 });
  assert.equal(component.noteChanged.values.length, 1);
  assert.equal(component.noteChanged.values[0].startBeat, 23);
  assert.equal(component.noteChanged.values[0].midi, 48);
});

test('middle click applies only when press and release are both empty', () => {
  const { component } = fixture();
  const event = { button: 1, clientX: 300, clientY: 20, pointerId: 1, preventDefault() {}, stopPropagation() {} };
  component.onAuxClick(event); assert.deepEqual(component.applyRequested.values, []);
  component.editable = () => true;
  component.onAuxClick(event); assert.deepEqual(component.applyRequested.values, [], 'release without a press is ignored');
  component.onPointerDown(event); component.onPointerUp(event); component.onAuxClick(event);
  assert.equal(component.applyRequested.values.length, 1);
  const note = { id: 'a', color: 'bass', startBeat: 20, durationBeats: 1, midi: 48, velocity: 90 };
  const hit = { note, x: 390, y: 15, width: 25, height: 10 };
  component.model.notes = [note];
  component.hitAreas = [hit]; component.hitColumnCount = 9; component.hitAreaBuckets.set(4, [hit]);
  component.onPointerDown(event);
  const empty = { ...event, clientX: 330, clientY: 20 };
  component.onPointerMove(empty); component.onPointerUp(empty); component.onAuxClick(empty);
  assert.equal(component.applyRequested.values.length, 1, 'note-to-empty drag cannot apply');
  component.onPointerDown(empty); component.onPointerUp(event); component.onAuxClick(event);
  assert.equal(component.applyRequested.values.length, 1, 'empty-to-note cannot apply');
  component.onPointerDown(empty); component.onPointerCancel(); component.onAuxClick(empty);
  assert.equal(component.applyRequested.values.length, 1, 'cancelled gestures cannot apply');
  component.onPointerDown(empty); component.onPointerUp({ ...empty, clientX: 1000 }); component.onAuxClick(empty);
  assert.equal(component.applyRequested.values.length, 1, 'release outside canvas cannot apply');
});

test('playhead glow follows the actual note duration independently of the enlarged hit target', () => {
  const { component, fills } = fixture();
  component.hitAreas = [{ note: { color: 'bass', startBeat: 20, durationBeats: 1 }, x: 195, y: 30, width: 60, height: 6 }];
  component.playhead = 20.5 / 64 * 100; component.drawDynamic(); assert.equal(fills.length, 1);
  component.playhead = 21 / 64 * 100; component.drawDynamic(); assert.equal(fills.length, 1, 'ending notes stop glowing at their true boundary');
});

test('playhead glow fades with the fraction played for both short and long notes', () => {
  const { component, strokes, fills } = fixture();
  for (const durationBeats of [.25, 4]) {
    component.hitAreas = [{ note: { color: 'bass', startBeat: 20, durationBeats }, x: 195, y: 30, width: 60, height: 6 }];
    for (const progress of [0, .25, .5, .75, .99]) {
      fills.length = 0;
      component.playhead = (20 + progress * durationBeats) / 64 * 100;
      component.drawDynamic();
      const glowStroke = strokes.find(stroke => stroke.width === 3);
      assert.ok(Math.abs(glowStroke.alpha - .325 * (1 - progress)) < 1e-10);
      assert.equal(fills.length, 1);
      assert.ok(Math.abs(fills[0].alpha - .4 * (1 - progress)) < 1e-10);
    }
    for (const beat of [19, 20 + durationBeats]) {
      fills.length = 0;
      component.playhead = beat / 64 * 100;
      component.drawDynamic();
      assert.equal(fills.length, 0);
      assert.ok(strokes.every(stroke => stroke.width !== 3));
    }
  }
});

function editFixture() {
  const value = fixture();
  const c = value.component;
  c.editable = () => true;
  c.editBounds = () => ({ startBeat: 20, endBeat: 28 });
  c.pitchMin = 40; c.pitchMax = 72;
  const notes = [
    { id: 'a', part: 'bass', color: 'bass', midi: 48, velocity: 80, startBeat: 20, durationBeats: 1 },
    { id: 'b', part: 'bass', color: 'bass', midi: 52, velocity: 100, startBeat: 22, durationBeats: 2 },
  ];
  c.model.notes = notes;
  c.selectedNoteIds = () => ['a', 'b'];
  c.hitAreas = notes.map((note, i) => ({ note, x: 390 + i * 100, y: 15, width: 25, height: 10 }));
  c.hitColumnCount = 9;
  c.hitAreaBuckets.set(4, [c.hitAreas[0]]); c.hitAreaBuckets.set(5, [c.hitAreas[1]]);
  value.canvas.focus = () => {};
  c.dynamicGraphics.rect = function() { return this; };
  return { ...value, notes };
}
const pointer = (x, y, extra = {}) => ({ preventDefault() {}, clientX: x, clientY: y, pointerId: 1, button: 0, ...extra });

test('group moves preserve spacing at the phrase boundary and commit once', () => {
  const { component: c } = editFixture();
  c.onPointerDown(pointer(300, 20)); c.onPointerMove(pointer(700, 20));
  assert.equal(c.notesChanged.values.length, 0);
  c.onPointerUp(pointer(700, 20));
  const result = c.notesChanged.values[0];
  assert.equal(result.duplicate, false);
  assert.deepEqual(result.notes.map(n => n.startBeat), [24, 26]);
  assert.deepEqual(result.notes.map(n => n.durationBeats), [1, 2]);
});

test('Ctrl drag duplicates selected notes without altering the originals', () => {
  const { component: c, notes } = editFixture();
  c.onPointerDown(pointer(300, 20, { ctrlKey: true }));
  c.onPointerMove(pointer(325, 20)); c.onPointerUp(pointer(325, 20));
  assert.equal(c.notesChanged.values[0].duplicate, true);
  assert.deepEqual(c.notesChanged.values[0].notes.map(n => n.startBeat), [21, 23]);
  assert.deepEqual(notes.map(n => n.startBeat), [20, 22]);
});

test('Shift move bypasses the grid and locks pitch; cancellation publishes no edit', () => {
  const { component: c } = editFixture();
  c.onPointerDown(pointer(300, 20));
  c.onPointerMove(pointer(303, 40, { shiftKey: true }));
  assert.ok(Math.abs(c.drag.current.startBeat - 20.12) < 1e-10);
  assert.equal(c.drag.current.midi, 48);
  c.onPointerCancel(); assert.equal(c.notesChanged.values.length, 0);
});

test('middle drag adjusts selection velocity within MIDI bounds without changing timing', () => {
  const { component: c } = editFixture();
  c.onPointerDown(pointer(300, 20, { button: 1 }));
  c.onPointerMove(pointer(300, -100, { button: 1 }));
  c.onPointerUp(pointer(300, -100, { button: 1 }));
  assert.deepEqual(c.notesChanged.values[0].notes.map(n => n.velocity), [107, 127]);
  assert.deepEqual(c.notesChanged.values[0].notes.map(n => n.startBeat), [20, 22]);
});

test('left edge trims selection starts while retaining each note end', () => {
  const { component: c } = editFixture();
  c.onPointerDown(pointer(296, 20)); c.onPointerMove(pointer(308.5, 20));
  c.onPointerUp(pointer(308.5, 20));
  assert.deepEqual(c.notesChanged.values[0].notes.map(n => [n.startBeat, n.durationBeats]), [[20.5, .5], [22.5, 1.5]]);
});

test('right box selects notes; Alt right click requests a grid split', () => {
  const { component: c } = editFixture();
  c.onPointerDown(pointer(290, 15, { button: 2 }));
  c.onPointerMove(pointer(365, 25, { button: 2 }));
  c.onPointerUp(pointer(365, 25, { button: 2 }));
  assert.deepEqual(c.selectionChanged.values[0], ['a', 'b']);
  c.onPointerDown(pointer(300, 20, { button: 2, altKey: true }));
  c.onPointerUp(pointer(300, 20, { button: 2, altKey: true }));
  assert.deepEqual(c.splitRequested.values[0], { id: 'a', beat: 24 });
});

function phraseEditLibrary() {
  const source = fs.readFileSync(path.join(__dirname, '../src/app/core/music/phrase-edit.ts'), 'utf8');
  const compiled = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } }).outputText;
  const module = { exports: {} };
  new Function('module', 'exports', compiled)(module, module.exports);
  return module.exports;
}

test('phrase history retains restoration intent, ignores no-op edits and discards a redo branch', () => {
  const { PhraseEditHistory } = phraseEditLibrary();
  const history = new PhraseEditHistory();
  const note = { id: 'a', midi: 60, velocity: 90, startBeat: 0, durationBeats: 1 };
  history.reset({ notes: [note], selection: [], restore: false });
  history.record({ notes: [note], selection: ['a'], restore: false });
  assert.equal(history.canUndo, false);
  history.record({ notes: [{ ...note, midi: 72 }], selection: ['a'], restore: false });
  history.record({ notes: [note], selection: [], restore: true });
  assert.equal(history.undo().notes[0].midi, 72);
  assert.equal(history.redo().restore, true);
  history.undo();
  history.record({ notes: [], selection: [], restore: false });
  assert.equal(history.canRedo, false);
  assert.equal(history.undo().notes[0].midi, 72);
  const initial = history.undo(); initial.notes[0].midi = 0;
  assert.equal(history.redo().notes[0].midi, 72, 'returned snapshots cannot mutate history');
});

test('group pitch and duration changes respect MIDI limits and shortest selected note', () => {
  const { dragPhraseNotes } = phraseEditLibrary();
  const notes = [
    { id: 'a', midi: 2, velocity: 90, startBeat: 0, durationBeats: .5 },
    { id: 'b', midi: 125, velocity: 110, startBeat: 2, durationBeats: 1 },
  ];
  assert.deepEqual(dragPhraseNotes(notes, 'pitch', 0, 12, 0, 0, 4).map(n => n.midi), [4, 127]);
  assert.deepEqual(dragPhraseNotes(notes, 'pitch', 0, -12, 0, 0, 4).map(n => n.midi), [0, 123]);
  const shortened = dragPhraseNotes(notes, 'duration', -10, 0, 0, 0, 4);
  assert.ok(Math.abs(shortened[0].durationBeats - .05) < 1e-10);
  assert.ok(Math.abs(shortened[1].durationBeats - .55) < 1e-10);
});

test('scale snapping stays within MIDI bounds and respects shifted scale pitch classes', () => {
  const { snapMidiToScale } = phraseEditLibrary();
  const cMajor = [0, 2, 4, 5, 7, 9, 11];
  assert.equal(snapMidiToScale(61, cMajor), 60);
  assert.equal(snapMidiToScale(61, [1, 3, 5, 6, 8, 10, 0]), 61);
  assert.equal(snapMidiToScale(61, []), 61);
  assert.equal(snapMidiToScale(0, [11]), 11);
  assert.equal(snapMidiToScale(127, [8]), 116);
});

test('phrase change detection ignores ordering and IDs while detecting musical edits', () => {
  const { phraseNotesEqual } = phraseEditLibrary();
  const notes = [{ id: 'a', midi: 60, velocity: 90, startBeat: 0, durationBeats: 1 },
    { id: 'b', midi: 64, velocity: 90, startBeat: 1, durationBeats: 1 }];
  assert.equal(phraseNotesEqual(notes, [...notes].reverse().map(n => ({ ...n, id: 'different-' + n.id }))), true);
  assert.equal(phraseNotesEqual(notes, notes.map(n => ({ ...n, velocity: 100 }))), false);
  assert.equal(phraseNotesEqual(notes, notes.slice(1)), false);
});

test('scale snapping constrains pitch drags and can be disabled for chromatic edits', () => {
  const { component: c, notes } = editFixture();
  c.scalePitchClasses = () => [0, 2, 4, 5, 7, 9, 11];
  c.model.notes = [notes[0]]; c.selectedNoteIds = () => ['a'];
  c.onPointerDown(pointer(300, 20));
  c.onPointerMove(pointer(300, 20 - c.laneHeight / 2));
  assert.equal(c.drag.current.midi, 48, 'C sharp snaps to C');
  c.snapToScale = () => false;
  c.onPointerMove(pointer(300, 20 - c.laneHeight / 2));
  assert.equal(c.drag.current.midi, 49);
});

test('drag audition follows each snapped cyan preview immediately, including returning to the original pitch', () => {
  const { component: c, notes } = editFixture();
  c.harmonicHeight = 264;
  c.scalePitchClasses = () => [0, 2, 4, 5, 7, 9, 11];
  c.model.notes = [notes[0]];
  c.selectedNoteIds = () => ['a'];
  c.playhead = 0;
  const shapes = [];
  c.dynamicGraphics.roundRect = function(x, y, width, height) { shapes.push({ x, y, width, height }); return this; };
  // Grab below the note center; the canvas is displayed at half its rendering size.
  c.onPointerDown(pointer(300, 22));
  const move = (semitones, expectedPitch, shouldAudition) => {
    const before = c.auditionRequested.values.length;
    c.lastAudition = performance.now(); // Every move happens inside the former throttle window.
    shapes.length = 0;
    c.onPointerMove(pointer(300, 22 - semitones * c.laneHeight / 2));
    assert.equal(c.drag.current.midi, expectedPitch);
    const cyan = shapes.find(shape => shape.height === c.laneHeight * .75);
    assert.ok(cyan);
    const drawnPitch = c.pitchMax + .5 - (cyan.y + cyan.height / 2 + c.verticalOffset) / c.laneHeight;
    assert.equal(drawnPitch, expectedPitch, 'cyan preview uses the snapped pitch');
    assert.equal(c.auditionRequested.values.length, before + Number(shouldAudition));
    assert.equal(c.auditionRequested.values.at(-1)[0].midi, expectedPitch, 'audio uses the cyan preview pitch');
  };
  move(1.6, 50, true);
  move(2.2, 50, false);
  move(3.6, 52, true);
  move(0, 48, true);
  move(-1.6, 45, true);
  c.onPointerUp(pointer(300, 30));
  assert.equal(c.noteChanged.values[0].midi, 45, 'the committed pitch also matches');
});

test('drum-only editing renders every unselected drum note at visible row-based thickness', () => {
  const { component: c } = fixture();
  c.editable = () => true; c.velocityVisible = () => true;
  c.horizontalOffset = 0; c.horizontalScale = 1;
  const tracks = [82, 60, 40, 36, 35].map((drumPitch, index) => ({ id: 'drum-' + index, color: 'drums', drumPitch }));
  const notes = tracks.flatMap((track, index) => [0, 2, 4].map(beat => ({ id: track.id + '-' + beat, part: track.id,
    color: 'drums', midi: track.drumPitch, velocity: 90, startBeat: beat + index * .125, durationBeats: .125, opacity: 1 })));
  c.model = { tracks, notes, sections: [], totalBeats: 8, selectedTrackId: tracks[3].id, hiddenTrackIds: new Set() };
  const shapes = [];
  c.staticGraphics = { clear() { shapes.length = 0; }, rect() { return this; }, moveTo() { return this; },
    lineTo() { return this; }, fill() { return this; }, stroke() { return this; },
    roundRect(x, y, width, height) { shapes.push({ x, y, width, height }); return this; } };
  c.drawStatic();
  assert.ok(c.harmonicHeight > 0, 'space above the drums remains available');
  assert.equal(c.gutterRows().filter(row => row.drum).length, 5);
  assert.equal(shapes.length, notes.length, 'all drum tracks draw before any selection or click');
  assert.ok(shapes.every(shape => Math.abs(shape.height - c.drumHeight / tracks.length * .75) < 1e-9), 'notes fill 75% of their drum row');
  assert.equal(c.hitAreas.length, notes.length, 'all rendered drum notes remain editable');
  c.model.selectedTrackId = tracks[0].id; c.drawStatic();
  assert.ok(shapes.every(shape => Math.abs(shape.height - c.drumHeight / tracks.length * .75) < 1e-9), 'changing active tracks preserves row-based note height');
  for (const editable of [false, true]) {
    c.editable = () => editable;
    for (const height of [180, 600, 1000]) {
      c.height = height;
      c.model.tracks = tracks;
      c.drawStatic();
      const drumRows = c.gutterRows().filter(row => row.drum);
      assert.ok(drumRows.every(row => row.height <= 14), 'drums remain compact');
      assert.equal(drumRows.at(-1).y + drumRows.at(-1).height, c.scoreHeight, 'drums sit above velocity at the bottom');
      c.model.tracks = [...tracks, { id: 'bass', color: 'bass' }];
      c.drawStatic();
      assert.deepEqual(c.gutterRows().filter(row => row.drum), drumRows, 'drum layout matches a score with other roles');
    }
  }
});

test('note fills respect track selection and editor opacity while selected-track glow ignores velocity', () => {
  const { component: c } = fixture();
  c.horizontalOffset = 0; c.horizontalScale = 1;
  c.model = { tracks: [{ id: 'selected', color: 'drums', drumPitch: 36 }, { id: 'other', color: 'drums', drumPitch: 38 }],
    notes: ['selected', 'other'].flatMap(part => [0, 127].map((velocity, index) => ({ id: part + index,
      part, color: 'drums', midi: part === 'selected' ? 36 : 38, velocity, startBeat: 4 + index * 4,
      durationBeats: 1, opacity: .5 + velocity / 127 * .5 }))),
    sections: [], totalBeats: 16, selectedTrackId: 'selected', hiddenTrackIds: new Set() };
  const notes = [], glow = [];
  let shape;
  c.staticGraphics = { clear() { notes.length = 0; glow.length = 0; },
    rect(x, y, width, height) { shape = { type: 'rect', x, width }; return this; },
    roundRect() { shape = { type: 'note' }; return this; },
    moveTo() { return this; }, lineTo() { return this; }, stroke() { return this; },
    fill(style) {
      if (shape?.type === 'note') notes.push(style.alpha);
      else if (shape?.x === c.scoreX(4) - 6 || shape?.x === c.scoreX(8) - 6) glow.push(style.alpha);
      return this;
    } };
  for (const editable of [false, true]) {
    c.editable = () => editable;
    c.model.notes = c.model.notes.map(note => ({ ...note,
      opacity: editable ? .5 + note.velocity / 127 * .5 : .4 + note.velocity / 127 * .6 }));
    c.drawStatic();
    assert.deepEqual(notes, editable ? [.5, 1, .3, .3] : [.4, 1, .3, .8]);
    assert.equal(glow.length, 2);
    assert.equal(glow[0], glow[1], 'both velocities keep the same selected-track glow');
  }
});

test('selected track glow fades outward over 5px and stays centered in short and tall score canvases', () => {
  const { component: c } = fixture();
  c.horizontalOffset = 0; c.horizontalScale = 1;
  c.model = { tracks: [{ id: 'bass', color: 'bass' }],
    notes: [12, 48, 96].map((midi, index) => ({ id: String(index), part: 'bass', color: 'bass',
      midi, velocity: 90, startBeat: 4 + index * 4, durationBeats: 1, opacity: 1 })),
    sections: [], totalBeats: 16, selectedTrackId: 'bass', hiddenTrackIds: new Set() };
  const rectangles = [], notes = [];
  c.staticGraphics = { clear() { rectangles.length = 0; notes.length = 0; },
    rect(x, y, width, height) { rectangles.push({ x, y, width, height }); return this; },
    roundRect(x, y, width, height) { notes.push({ x, y, width, height }); return this; },
    moveTo() { return this; }, lineTo() { return this; },
    fill(style) { if (rectangles.length) rectangles.at(-1).alpha = style.alpha; return this; }, stroke() { return this; } };
  for (const height of [50, 100, 600]) {
    c.height = height; c.drawStatic();
    const note = notes.find(shape => shape.x === c.scoreX(8));
    assert.ok(Math.abs(note.height - c.laneHeight * .75) < 1e-9, 'notes fill 75% of their pitch row');
    let previousAlpha = 0;
    for (let radius = 5; radius >= 1; radius--) {
      const glow = rectangles.find(shape => shape.x === note.x - radius && shape.width === note.width + radius * 2);
      assert.ok(glow, 'selected track has a glow at radius ' + radius);
      assert.ok(Math.abs(glow.y + glow.height / 2 - note.y - note.height / 2) < 1e-9,
        'glow stays centered at canvas height ' + height);
      assert.ok(Math.abs(glow.height - note.height - radius * 2) < 1e-9, 'glow keeps equal padding around the note');
      assert.ok(glow.alpha > previousAlpha, 'glow layers become stronger toward the note');
      previousAlpha = glow.alpha;
    }
  }
});

test('opening and resizing a phrase editor keeps its full pitch range visible', () => {
  const { component: c } = fixture();
  c.editable = () => true; c.velocityVisible = () => true;
  c.pendingEditPitchFocus = true;
  c.model = { tracks: [{ id: 'bass', color: 'bass' }],
    notes: [36, 48, 60].map((midi, index) => ({ id: String(index), part: 'bass', color: 'bass',
      midi, velocity: 90, startBeat: 20 + index, durationBeats: 1, opacity: 1 })),
    sections: [], totalBeats: 64, selectedTrackId: 'bass', hiddenTrackIds: new Set() };
  c.staticGraphics = { clear() {}, rect() { return this; }, moveTo() { return this; }, lineTo() { return this; },
    fill() { return this; }, stroke() { return this; }, roundRect() { return this; } };
  let hostHeight = 300;
  c.rendererHost = () => ({ nativeElement: { clientWidth: 800, get clientHeight() { return hostHeight; } } });
  c.app.renderer = { resize() {} };
  const phrase = c.model.notes;
  c.notesReady = () => false;
  c.model.notes = [];
  c.drawStatic();
  assert.equal(c.pendingEditPitchFocus, true, 'an unloaded draft must not consume the initial pitch fit');
  c.model.notes = phrase;
  c.notesReady = () => true;
  c.drawStatic();
  assert.equal(c.pendingEditPitchFocus, false, 'the loaded draft consumes the initial pitch fit once');
  for (const height of [190, 440, 300]) {
    hostHeight = height;
    c.resizeRenderer();
    const pitches = c.gutterRows().map(row => row.midi);
    assert.ok(pitches.includes(62) && pitches.includes(34), 'the padded phrase range stays visible at height ' + height);
    assert.equal(c.hitAreas.length, 3, 'all phrase notes remain visible');
  }
});

test('Alt-wheel reveals pitches outside the fitted phrase and allows adding notes there', () => {
  const { component: c } = fixture();
  c.velocityVisible = () => true;
  c.editable = () => true; c.snapToScale = () => false;
  c.pendingEditPitchFocus = true;
  c.model = { tracks: [{ id: 'bass', color: 'bass' }, { id: 'drum', color: 'drums', drumPitch: 36 }],
    notes: [{ id: 'a', part: 'bass', color: 'bass', midi: 48, velocity: 90, startBeat: 20, durationBeats: 1, opacity: 1 }],
    sections: [], totalBeats: 64, selectedTrackId: 'bass', hiddenTrackIds: new Set() };
  c.staticGraphics = { clear() {}, rect() { return this; }, moveTo() { return this; }, lineTo() { return this; },
    fill() { return this; }, stroke() { return this; }, roundRect() { return this; } };
  c.drawStatic();
  assert.deepEqual(c.gutterRows().filter(row => !row.drum).map(row => row.midi), [50, 49, 48, 47, 46]);
  const cursorY = c.harmonicHeight / 2;
  const anchor = c.pitchMax - (c.verticalOffset + cursorY) / c.laneHeight;
  const wheel = { preventDefault() {}, stopPropagation() {}, altKey: true, deltaMode: 0,
    deltaX: 0, deltaY: Math.log(2) / .002, clientX: 200, clientY: 10 + cursorY / 2 };
  c.onCanvasWheel(wheel);
  assert.ok(Math.abs(c.verticalScale - 128 / 10) < 1e-10);
  assert.ok(Math.abs(c.pitchMax - (c.verticalOffset + cursorY) / c.laneHeight - anchor) < 1e-10,
    'the pitch under the cursor stays anchored even with pinned drum rows');
  const higher = c.gutterRows().find(row => !row.drum && row.midi > 50);
  const lower = c.gutterRows().find(row => !row.drum && row.midi < 46);
  assert.ok(higher && lower, 'zooming out exposes pitches on both sides of the phrase');
  c.onDoubleClick(pointer(300, 10 + (higher.y + higher.height / 2) / 2));
  assert.deepEqual(c.noteAdded.values, [{ midi: higher.midi, startBeat: 24 }]);
  c.drawStatic();
  assert.ok(c.gutterRows().some(row => row.midi === higher.midi), 'redrawing keeps the expanded pitch viewport');
  c.onCanvasWheel({ ...wheel, deltaY: 10000 });
  assert.equal(c.verticalScale, 1);
  assert.equal(c.verticalOffset, 0);
  assert.deepEqual(c.gutterRows().filter(row => !row.drum).map(row => row.midi),
    Array.from({ length: 128 }, (_, i) => 127 - i));
  c.onCanvasWheel({ ...wheel, deltaY: -10000 });
  assert.equal(c.laneHeight, 50);
});

test('pitch rows cap at 50px during fitting, repeated zooming and resizing in overview and edit mode', () => {
  const { component: c } = fixture();
  c.model = { tracks: [{ id: 'bass', color: 'bass' }],
    notes: [{ id: 'a', part: 'bass', color: 'bass', midi: 60, velocity: 90, startBeat: 20, durationBeats: 1, opacity: 1 }],
    sections: [], totalBeats: 64, selectedTrackId: 'bass', hiddenTrackIds: new Set() };
  c.staticGraphics = { clear() {}, rect() { return this; }, moveTo() { return this; }, lineTo() { return this; },
    fill() { return this; }, stroke() { return this; }, roundRect() { return this; } };
  const wheel = { preventDefault() {}, stopPropagation() {}, altKey: true, deltaMode: 0,
    deltaX: 0, deltaY: -10000, clientX: 200, clientY: 60 };
  for (const editable of [false, true]) {
    c.editable = () => editable;
    c.height = 600; c.verticalScale = 1; c.pendingEditPitchFocus = editable;
    c.drawStatic();
    assert.equal(c.laneHeight, 50, 'initial phrase fit respects the cap');
    c.onCanvasWheel(wheel);
    const offset = c.verticalOffset;
    c.onCanvasWheel(wheel);
    assert.equal(c.laneHeight, 50);
    assert.equal(c.verticalOffset, offset, 'zooming at the cap does not move the viewport');
    c.height = 1000; c.drawStatic(true);
    assert.equal(c.laneHeight, 50, 'growing the canvas cannot exceed the cap');
    assert.ok(c.gutterRows().every(row => row.height <= 50));
    assert.equal(c.contentHeight, c.laneHeight * (c.pitchMax - c.pitchMin + 1), 'scroll range follows capped rows');
    c.onCanvasWheel({ ...wheel, deltaY: 10000 });
    assert.equal(c.verticalScale, 1, 'zooming out still reaches the full pitch range');
  }
});
