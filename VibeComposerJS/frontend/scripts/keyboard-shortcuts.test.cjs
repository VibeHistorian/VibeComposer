const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const ts = require('typescript');

class Element {
  constructor(tag, editable = false, type = 'text', role = '') { this.tag = tag; this.isContentEditable = editable; this.type = type; this.role = role; }
  matches(selector) {
    return (['input', 'textarea', 'select'].includes(this.tag)
      && !(this.tag === 'input' && this.type === 'range' && selector.includes(':not([type="range"])')))
      || (this.tag === 'button' && selector.split(', ').includes('button'))
      || (this.tag === 'summary' && selector.includes('summary'))
      || (this.role && selector.includes('[role="' + this.role + '"]'));
  }
}

function load(file, dependencies = {}) {
  const source = fs.readFileSync(path.join(__dirname, '../src/app', file), 'utf8');
  const compiled = ts.transpileModule(source, { compilerOptions: {
    target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, experimentalDecorators: true,
  } }).outputText;
  const module = { exports: {} };
  new Function('require', 'module', 'exports', 'HTMLElement', compiled)(name => dependencies[name] ?? {}, module, module.exports, Element);
  return module.exports;
}

const helpers = load('shared/keyboard-shortcuts.ts');
const { AppComponent } = load('app.component.ts', {
  '@angular/core': { Component: () => type => type, ChangeDetectionStrategy: {} },
  './shared/keyboard-shortcuts': helpers,
});

function fixture() {
  const calls = [];
  const app = Object.create(AppComponent.prototype);
  const history = name => ({ undo: () => calls.push(name + ':undo'), redo: () => calls.push(name + ':redo') });
  app.preferences = require('./support/shortcut-preferences.cjs').shortcutPreferences();
  app.projects = history('project');
  app.project = () => ({ name: 'Song' });
  app.playback = { toggle: project => calls.push('playback:' + project.name) };
  let editing = false;
  let editor = history('phrase');
  app.workspace = () => ({ editing: () => editing, editor: () => editor });
  const press = (key, options = {}) => {
    const event = { key, ctrlKey: true, defaultPrevented: false, composedPath: () => [],
      preventDefault() { this.defaultPrevented = true; }, stopPropagation() {}, ...options };
    app.historyKeydown(event);
    return event;
  };
  return { calls, press, edit: () => { editing = true; }, removeEditor: () => { editor = undefined; } };
}

test('routes Ctrl Z, Ctrl Y and Ctrl Shift Z to global or phrase history exactly once', () => {
  const f = fixture();
  for (const editing of [false, true]) {
    if (editing) f.edit();
    assert.equal(f.press('z').defaultPrevented, true);
    assert.equal(f.press('y').defaultPrevented, true);
    assert.equal(f.press('Z', { shiftKey: true }).defaultPrevented, true);
  }
  assert.deepEqual(f.calls, ['project:undo', 'project:redo', 'project:redo', 'phrase:undo', 'phrase:redo', 'phrase:redo']);
  f.removeEditor();
  f.press('z');
  assert.equal(f.calls.length, 6, 'never falls back to project history during phrase editing');
});

test('focused knob range inputs route undo and redo to the active history', () => {
  const f = fixture();
  const knob = new Element('input', false, 'range');
  for (const editing of [false, true]) {
    if (editing) f.edit();
    for (const [key, options] of [['z', {}], ['y', {}], ['Z', { shiftKey: true }], ['z', { ctrlKey: false, metaKey: true }]]) {
      assert.equal(f.press(key, { ...options, composedPath: () => [knob, new Element('div')] }).defaultPrevented, true);
    }
  }
  assert.deepEqual(f.calls, ['project:undo', 'project:redo', 'project:redo', 'project:undo',
    'phrase:undo', 'phrase:redo', 'phrase:redo', 'phrase:undo']);
});

test('preserves editing shortcuts in text fields, comboboxes, listboxes and inherited or shadow contenteditable regions', () => {
  const f = fixture();
  for (const editing of [false, true]) {
    if (editing) f.edit();
    for (const control of [new Element('input'), new Element('textarea'), new Element('select'),
      new Element('button', false, 'button', 'combobox'), new Element('div', false, '', 'listbox'), new Element('span', true)]) {
      for (const [key, shiftKey] of [['z', false], ['y', false], ['Z', true]]) {
        assert.equal(f.press(key, { shiftKey, composedPath: () => [control, new Element('div')] }).defaultPrevented, false);
      }
    }
  }
  assert.deepEqual(f.calls, []);
});

test('ignores unrelated, handled, composing, and Alt shortcuts; supports Command on macOS', () => {
  const f = fixture();
  for (const options of [{ ctrlKey: false }, { altKey: true }, { isComposing: true }, { defaultPrevented: true }]) f.press('z', options);
  f.press('a');
  assert.deepEqual(f.calls, []);
  f.press('z', { ctrlKey: false, metaKey: true });
  assert.deepEqual(f.calls, ['project:undo']);
});

test('Space toggles global playback in either workspace and prevents scrolling and repeat toggles', () => {
  const f = fixture();
  for (const editing of [false, true]) {
    if (editing) f.edit();
    assert.equal(f.press(' ', { ctrlKey: false }).defaultPrevented, true);
    assert.equal(f.press(' ', { ctrlKey: false, repeat: true }).defaultPrevented, true);
  }
  assert.deepEqual(f.calls, ['playback:Song', 'playback:Song']);
});

test('Space preserves text entry, native control activation, composition and modified shortcuts', () => {
  const f = fixture();
  for (const control of [new Element('input'), new Element('textarea'), new Element('select'),
    new Element('button'), new Element('summary'), new Element('span', true),
    ...['combobox', 'listbox', 'button', 'checkbox', 'radio', 'switch', 'menuitem', 'tab', 'treeitem'].map(role => new Element('div', false, '', role))]) {
    assert.equal(f.press(' ', { ctrlKey: false, composedPath: () => [new Element('span'), control] }).defaultPrevented, false);
  }
  for (const options of [{ ctrlKey: true }, { metaKey: true }, { altKey: true }, { shiftKey: true }, { isComposing: true }, { defaultPrevented: true }]) {
    f.press(' ', { ctrlKey: false, ...options });
  }
  assert.deepEqual(f.calls, []);
});

const shortcutSupport = require('./support/shortcut-preferences.cjs');
test('custom shortcuts replace defaults, match exact modifiers and preserve active history ownership', () => {
  const app = Object.create(AppComponent.prototype);
  app.preferences = shortcutSupport.shortcutPreferences();
  const calls = [];
  app.projects = { undo: () => calls.push('undo'), redo: () => calls.push('redo') };
  app.workspace = () => undefined;
  app.project = () => ({});
  app.playback = { toggle: () => calls.push('play') };
  app.preferences.setShortcut('undo', { kind: 'key', input: 'u', ctrl: false, shift: true, alt: true });
  const press = (key, extras = {}) => app.historyKeydown({ key, composedPath: () => [], preventDefault() {}, stopPropagation() {}, ...extras });
  press('z', { ctrlKey: true });
  press('u', { shiftKey: true });
  assert.deepEqual(calls, []);
  press('U', { shiftKey: true, altKey: true });
  assert.deepEqual(calls, ['undo']);
});
test('capture normalization and preference decoding cover keys, mouse, wheel and malformed imports', () => {
  const { shortcutFromEvent, decodeShortcuts, defaultShortcuts } = shortcutSupport;
  assert.deepEqual(shortcutFromEvent({ button: 2, ctrlKey: true, shiftKey: true, altKey: true }),
    { kind: 'mouse', input: '2', ctrl: true, shift: true, alt: true });
  assert.equal(shortcutFromEvent({ key: 'Control', ctrlKey: true }), null);
  assert.equal(shortcutFromEvent({ deltaY: -4, altKey: true }).kind, 'wheel');
  assert.equal(shortcutFromEvent({ key: 'Z', metaKey: true }).ctrl, true);
  assert.deepEqual(decodeShortcuts(undefined), defaultShortcuts());
  assert.equal(decodeShortcuts({ zoomTime: [{ kind: 'key', input: 'x', ctrl: false, shift: false, alt: false }] }), null);
  assert.equal(decodeShortcuts({ undo: [null] }), null);
  assert.equal(decodeShortcuts({ freeGrid: [{ kind: 'modifier', input: 'held', ctrl: false, shift: false, alt: false }] }), null);
});

test('pointer actions release focus for global Space while keyboard and composite controls retain it', async () => {
  const { installPointerButtonFocus } = load('shared/pointer-button-focus.ts');
  const listeners = new Map();
  const document = { activeElement: null,
    addEventListener: (name, callback) => listeners.set(name, callback),
    removeEventListener: name => listeners.delete(name) };
  const control = (tag, composite = false, type = 'button') => {
    const element = new Element(tag, false, type);
    element.closest = () => composite ? element : null;
    element.blur = () => { document.activeElement = null; };
    return element;
  };
  const cleanup = installPointerButtonFocus(document);
  const button = control('button');
  document.activeElement = button;
  listeners.get('focusin')();
  assert.equal(document.activeElement, button, 'initial keyboard focus stays');
  listeners.get('pointerdown')();
  listeners.get('click')();
  await Promise.resolve();
  assert.equal(document.activeElement, null, 'click releases already-focused action');
  const f = fixture();
  f.press(' ', { ctrlKey: false, composedPath: () => [] });
  assert.deepEqual(f.calls, ['playback:Song']);
  for (const element of [button, control('input', false, 'checkbox')]) {
    document.activeElement = element;
    listeners.get('focusin')();
    assert.equal(document.activeElement, null, 'pointer-focused actions release');
  }
  listeners.get('keydown')();
  document.activeElement = button;
  listeners.get('focusin')();
  assert.equal(document.activeElement, button, 'Tab focus stays');
  listeners.get('pointerdown')();
  const menuButton = control('button', true);
  document.activeElement = menuButton;
  listeners.get('focusin')();
  assert.equal(document.activeElement, menuButton, 'composite widget focus stays');
  listeners.get('click')();
  document.activeElement = button;
  await Promise.resolve();
  assert.equal(document.activeElement, null, 'focus restored by click handlers is released');
  cleanup();
  assert.equal(listeners.size, 0);
});

test('unchanged saved phrase defaults adopt the new gestures while custom mappings survive', () => {
  const { defaultShortcuts, decodeShortcuts } = load('core/ui/shortcuts.ts');
  const old = defaultShortcuts();
  const mouse = (ctrl=false,shift=false) => ({ kind:'mouse', input:'0', ctrl, shift, alt:false });
  old.addNote=[mouse()]; old.seek=[mouse(false,true)]; old.shapePitch=[mouse(true)];
  const decoded=decodeShortcuts(old), defaults=defaultShortcuts();
  for(const id of ['addNote','seek','shapePitch']) assert.deepEqual(decoded[id],defaults[id]);
  old.seek=[{...mouse(),input:'1'}];
  assert.deepEqual(decodeShortcuts(old).seek,old.seek);
  assert.deepEqual(decodeShortcuts(old).addNote,old.addNote);
});


test('parameter lock shortcut defaults to Shift right mouse, fills older preferences and accepts remapping', () => {
  const { defaultShortcuts, decodeShortcuts, SHORTCUTS, SHORTCUT_PAGES } = load('core/ui/shortcuts.ts');
  const defaults = defaultShortcuts();
  assert.deepEqual(defaults.controlFreeze, [{ kind: 'mouse', input: '2', ctrl: false, shift: true, alt: false }]);
  delete defaults.controlFreeze;
  assert.deepEqual(decodeShortcuts(defaults).controlFreeze, defaultShortcuts().controlFreeze);
  const remapped = [{ kind: 'mouse', input: '1', ctrl: true, shift: false, alt: false }];
  assert.deepEqual(decodeShortcuts({ controlFreeze: remapped }).controlFreeze, remapped);
  assert.equal(decodeShortcuts({ controlFreeze: [{ kind: 'wheel', input: 'scroll', ctrl: false, shift: false, alt: false }] }), null);
  const action = SHORTCUTS.find(action => action.id === 'controlFreeze');
  assert.ok(SHORTCUT_PAGES.find(page => page.id === 'shortcuts-general').groups.includes(action.group));
});

test('legacy grouped bindings migrate once and explicit split bindings survive saving', () => {
  const { defaultShortcuts, decodeShortcuts } = shortcutSupport; const old = defaultShortcuts();
  for (const id of ['addNoteFree', 'moveNote', 'trimEnd', 'deleteNote', 'duplicateNotes', 'applyPhrase', 'controlUnfreeze', 'retainPitch', 'freeSplitGrid']) delete old[id];
  old.addNote.push({ kind: 'mouse', input: '0', ctrl: false, shift: true, alt: false });
  old.seek = [{ kind: 'mouse', input: '3', ctrl: false, shift: false, alt: false }];
  old.dragVelocity = []; old.controlFreeze = [];
  const migrated = decodeShortcuts(old);
  assert.deepEqual(migrated.addNote, defaultShortcuts().addNote); assert.deepEqual(migrated.addNoteFree, defaultShortcuts().addNoteFree);
  assert.equal(migrated.moveNote, undefined); assert.equal(migrated.trimEnd, undefined);
  assert.deepEqual(migrated.applyPhrase, defaultShortcuts().applyPhrase); assert.deepEqual(migrated.controlUnfreeze, []);
  migrated.addNoteFree = []; migrated.duplicateNotes = [];
  assert.deepEqual(decodeShortcuts(JSON.parse(JSON.stringify(migrated))), migrated);
});

test('separate targets can share defaults while competing operations still conflict', () => {
  const { canShareShortcut } = shortcutSupport;
  for (const [a, b] of [['boxSelect','deleteNote'], ['controlFreeze','controlUnfreeze'], ['dragVelocity','applyPhrase'], ['freeGrid','retainPitch']]) {
    assert.equal(canShareShortcut(a,b), true); assert.equal(canShareShortcut(b,a), true);
  }
  assert.equal(canShareShortcut('addNote','addNoteFree'), false);
  assert.equal(canShareShortcut('deleteNote','splitNote'), false);
});

test('context overlap compares every constrained axis and treats omitted axes as wildcards', () => {
  const { shortcutContextsOverlap: overlaps } = shortcutSupport;
  const pairs = [
    [{}, { surface:['score'], mode:['phrase-editor'] }, true],
    [{surface:['knob','dropdown']}, {surface:['knob'], scope:['section']}, true],
    [{surface:['score']}, {surface:['overview']}, false],
    [{surface:['knob'], scope:['global']}, {surface:['knob'], scope:['section']}, false],
    [{target:['note-body'], selection:['selected']}, {target:['note-body']}, true],
    [{target:['note-body'], selection:['selected']}, {target:['note-body'], selection:['unselected']}, false],
    [{surface:['score'], mode:['arrangement']}, {surface:['score'], mode:['phrase-editor']}, false],
  ];
  for (const [a,b,expected] of pairs) { assert.equal(overlaps(a,b),expected); assert.equal(overlaps(b,a),expected); }
});
test('context alternatives, global actions and cross-page conflicts use one policy', () => {
  const { canShareShortcut, shortcutsConflict, SHORTCUTS, SHORTCUT_CONTEXTS, defaultShortcuts } = shortcutSupport;
  for (const action of SHORTCUTS) {
    assert.ok(SHORTCUT_CONTEXTS[action.id].length > 0, action.id);
    assert.equal(canShareShortcut(action.id, action.id), false);
    for (const other of SHORTCUTS) assert.equal(canShareShortcut(action.id,other.id),canShareShortcut(other.id,action.id));
  }
  assert.equal(canShareShortcut('shapePitch','duplicateNotes'), false, 'drawing can target selected notes too');
  assert.equal(canShareShortcut('shapePitch','dragPitch'), false, 'one overlapping alternative is sufficient');
  assert.equal(canShareShortcut('seek','boxSelect'), false, 'seek claims the pointer before a drag can begin');
  assert.equal(canShareShortcut('controlFineWheel','controlWheel'), false, 'knobs are part of the broader control context');
  assert.equal(canShareShortcut('presence','duplicateSection'), true, 'cell and header targets are distinct');
  assert.equal(canShareShortcut('reroll','duplicateSection'), false, 'section reroll overlaps the header target');
  const bindings = defaultShortcuts(); const candidate = bindings.undo[0];
  bindings.presence = [candidate];
  assert.equal(shortcutsConflict('undo','presence',bindings,candidate), true, 'global overlaps arrangement regardless of settings page');
  assert.equal(shortcutsConflict('controlFreeze','controlUnfreeze',bindings,bindings.controlUnfreeze[0]), false);
  assert.equal(shortcutsConflict('addNote','addNoteFree',bindings,bindings.addNoteFree[0]), true);
  assert.equal(shortcutsConflict('undo','undo',bindings,candidate), false, 'replacing the same action is allowed');
});

test('all shared default bindings are justified by disjoint activation contexts', () => {
  const { SHORTCUTS, defaultShortcuts, shortcutsConflict } = shortcutSupport;
  const defaults = defaultShortcuts();
  for (const action of SHORTCUTS) for (const other of SHORTCUTS) for (const binding of defaults[action.id]) {
    assert.equal(shortcutsConflict(action.id, other.id, defaults, binding), false, action.id+' / '+other.id);
  }
});


test('legacy phrase drag preferences migrate to modifiers without restoring redundant actions', () => {
  const { decodeShortcuts, defaultShortcuts } = shortcutSupport;
  const mouse = (ctrl=false, shift=false, alt=false) => ({ kind:'mouse', input:'0', ctrl, shift, alt });
  const migrated = decodeShortcuts({ moveNote: [], trimEnd: [], freeSplitGrid: [],
    trimStart: [mouse(false,false,true), mouse(), mouse(true), mouse(false,true)] });
  assert.deepEqual(migrated.trimStart, defaultShortcuts().trimStart);
  for (const id of ['moveNote','trimEnd','freeSplitGrid']) assert.equal(id in migrated, false);
  const custom = decodeShortcuts({ trimStart: [mouse(true,false,true)] });
  assert.deepEqual(custom.trimStart, [{ kind:'mouse', input:'0', ctrl:true, shift:false, alt:true }]);
  assert.deepEqual(decodeShortcuts(custom), custom);
  assert.deepEqual(decodeShortcuts({ trimStart: [] }).trimStart, []);
});


test('fixed gesture buttons survive preference migration and reject recording other buttons', () => {
  const {SHORTCUTS, fixedMouseButton, acceptsShortcut, defaultShortcuts, decodeShortcuts} = shortcutSupport;
  const defaults=defaultShortcuts();
  assert.deepEqual(decodeShortcuts(defaults), defaults);
  for(const action of SHORTCUTS) {
    const button=fixedMouseButton(action.id); if(button===undefined) continue;
    assert.equal(acceptsShortcut(action,{...defaults[action.id][0],input:String((button+1)%3)}),false);
    const custom={kind:'mouse',input:'4',ctrl:true,shift:true,alt:true};
    const migrated=decodeShortcuts({...defaults,[action.id]:[custom]});
    assert.deepEqual(migrated[action.id],[{...custom,input:String(button)}],action.id);
    assert.deepEqual(decodeShortcuts(migrated),migrated);
  }
});
