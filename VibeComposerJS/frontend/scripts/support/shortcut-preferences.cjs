const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');
function load(file, dependencies) {
  const source = fs.readFileSync(path.join(__dirname, '../../src/app/core/ui', file + '.ts'), 'utf8');
  const compiled = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, experimentalDecorators: true } }).outputText;
  const module = { exports: {} };
  new Function('require', 'module', 'exports', compiled)(name => dependencies[name] ?? {}, module, module.exports);
  return module.exports;
}
const shortcuts = load('shortcuts', {});
const { UiPreferencesService } = load('ui-preferences.service', { './shortcuts': shortcuts, '@angular/core': { Injectable: () => type => type } });
function shortcutPreferences() {
  const preferences = Object.create(UiPreferencesService.prototype);
  let bindings = shortcuts.defaultShortcuts();
  preferences.shortcuts = () => bindings;
  preferences.capturingShortcut = () => false;
  preferences.setShortcut = (id, binding) => { bindings = { ...bindings, [id]: binding ? [binding] : [] }; };
  return preferences;
}
module.exports = { shortcutPreferences, ...shortcuts };
