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
