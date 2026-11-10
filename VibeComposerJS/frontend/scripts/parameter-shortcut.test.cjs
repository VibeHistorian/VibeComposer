const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const ts = require('typescript');

function fixture() {
  const listeners = new Map(), emitted = [];
  class Element {
    constructor(key = 'velocityMin', combined = false) { this.dataset = { parameterKey: key, parameterCombined: String(combined) }; }
    closest(selector) { return selector === '[disabled]' ? null : this; }
  }
  const root = { contains: () => true,
    addEventListener(type, handler, capture) { assert.equal(capture, true); listeners.set(type, handler); },
    removeEventListener(type, handler, capture) { assert.equal(listeners.get(type), handler); assert.equal(capture, true); listeners.delete(type); } };
  let section = false;
  let binding = { button: 2, shiftKey: true, ctrlKey: false, altKey: false };
  const preferences = { matchesShortcut(id, event) {
    assert.equal(id, section ? 'controlUnfreeze' : 'controlFreeze');
    return Object.entries(binding).every(([key, value]) => (event[key] ?? false) === value);
  } };
  const input = initial => { let value = initial; const read = () => value; read.set = next => value = next; return read; };
  const angular = { Directive: () => type => type, ElementRef: 'element',
    inject: token => token === 'element' ? { nativeElement: root } : preferences,
    input, output: () => ({ emit: value => emitted.push(value) }) };
  const filename = path.resolve(__dirname, '../src/app/features/part-settings/parameter-shortcut.directive.ts');
  const compiled = ts.transpileModule(fs.readFileSync(filename, 'utf8'), { compilerOptions: {
    target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, experimentalDecorators: true,
  } }).outputText;
  const module = { exports: {} };
  new Function('require', 'module', 'exports', 'Element', compiled)(name => name === '@angular/core' ? angular : { UiPreferencesService: 'preferences' }, module, module.exports, Element);
  const directive = new module.exports.ParameterShortcutDirective();
  const target = new Element();
  const send = (type, options = {}) => {
    const event = { type, target, button: 2, shiftKey: true, ctrlKey: false, altKey: false,
      prevented: false, stopped: false, preventDefault() { this.prevented = true; }, stopImmediatePropagation() { this.stopped = true; }, ...options };
    listeners.get(type)?.(event);
    return event;
  };
  return { directive, emitted, send, target, listeners, section: () => { section = true; directive.section.set(true); }, remap: value => binding = value };
}

test('parameter gesture consumes native follow-up events and runs once per pointer gesture', () => {
  const f = fixture();
  for (const type of ['pointerdown', 'mousedown', 'contextmenu', 'auxclick']) {
    const event = f.send(type); assert.equal(event.prevented, true); assert.equal(event.stopped, true);
  }
  assert.deepEqual(f.emitted, [{ key: 'velocityMin', combined: false }]);
  f.send('pointerdown'); f.send('contextmenu');
  assert.equal(f.emitted.length, 2);
});

test('remapped primary and middle mouse gestures suppress editing while ordinary mouse inputs remain available', () => {
  const f = fixture();
  assert.equal(f.send('pointerdown', { shiftKey: false }).prevented, false);
  for (const button of [0, 1]) {
    f.remap({ button, ctrlKey: true, shiftKey: false, altKey: true });
    const options = { button, ctrlKey: true, shiftKey: false, altKey: true };
    assert.equal(f.send('pointerdown', options).stopped, true);
    f.send('mousedown', options); f.send(button === 0 ? 'click' : 'auxclick', options);
  }
  assert.equal(f.emitted.length, 2);
});

test('standalone context-menu gestures repeat, combined frames retain their targeting, and teardown removes capture listeners', () => {
  const f = fixture();
  f.target.dataset.parameterCombined = 'true';
  f.send('contextmenu'); f.send('contextmenu');
  assert.deepEqual(f.emitted, [{ key: 'velocityMin', combined: true }, { key: 'velocityMin', combined: true }]);
  f.directive.enabled.set(false);
  assert.equal(f.send('pointerdown').prevented, false);
  assert.equal(f.emitted.length, 2);
  f.directive.ngOnDestroy(); assert.equal(f.listeners.size, 0);
});

test('section controls use their independent unfreeze binding', () => {
  const f = fixture(); f.section();
  f.remap({ button: 1, ctrlKey: true, shiftKey: false, altKey: false });
  assert.equal(f.send('pointerdown').prevented, false);
  assert.equal(f.send('pointerdown', { button: 1, ctrlKey: true, shiftKey: false }).prevented, true);
  assert.equal(f.emitted.length, 1);
});
