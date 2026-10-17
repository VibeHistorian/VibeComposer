const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const ts = require('typescript');

class Element {
  constructor(tag, editable = false, type = 'text') { this.tag = tag; this.isContentEditable = editable; this.type = type; }
  matches(selector) {
    return ['input', 'textarea', 'select'].includes(this.tag)
      && !(this.tag === 'input' && this.type === 'range' && selector.includes(':not([type="range"])'));
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
  app.projects = history('project');
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

test('preserves native shortcuts in text fields, selects and inherited or shadow contenteditable regions', () => {
  const f = fixture();
  for (const editing of [false, true]) {
    if (editing) f.edit();
    for (const control of [new Element('input'), new Element('textarea'), new Element('select'), new Element('span', true)]) {
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
