const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

// Cache immutable compiled text only. Evaluated exports, signals, services and storage
// belong to each fixture, including modules monkey-patched by individual tests.
const compiledModules = new Map();

function fixture({ compactHistory = false, dialogData = null, reactivity } = {}) {
  const cache = new Map();
  const effects = [];
  // Tests that exercise effect-driven behavior flush callbacks after changing state.
  const flushEffects = () => { for (const callback of effects) callback(); };
  const signal = reactivity?.signal ?? ((value) => {
    const read = () => value;
    read.set = (next) => { value = next; };
    read.update = updater => { value = updater(value); };
    read.asReadonly = () => read;
    return read;
  });
  const input = (value) => signal(value);
  input.required = () => signal(undefined);
  let service, ui, uiPreferences;
  const angular = { Injectable: () => (type) => type, Component: () => (type) => type, Directive: () => (type) => type,
    HostListener: () => () => {}, Injector: class Injector {}, afterNextRender: () => {}, Input: () => () => {}, Output: () => () => {}, EventEmitter: class { values = []; emit(value) { this.values.push(value); } },
    signal, computed: reactivity?.computed ?? ((read) => read), effect: callback => { effects.push(callback); }, untracked: read => read(), input,
    output: () => ({ emit() {} }), ElementRef: class ElementRef {}, ChangeDetectionStrategy: { OnPush: 0 },
    viewChild: Object.assign(() => () => undefined, { required: () => () => undefined }),
    inject: (type) => type === 'sequencer-data' ? dialogData ?? { scope: { kind: 'global-track', trackId: 'track-chords-1' }, role: 'chords', velocities: false, melodyHits: 16 } : type?.name === 'ProjectService' ? service : type?.name === 'WorkspaceUiService' ? ui
      : type?.name === 'AudioPlaybackService' ? {} : type?.name === 'MidiOutputService' ? { drumMapping: () => 'general-midi', drumLabel: pitch => load('core/music/drum-instruments.ts').drumInstrumentLabel(pitch, 'general-midi') }
      : type?.name === 'UiPreferencesService' ? uiPreferences : preferences,
  };
  const root = path.join(__dirname, '../../src/app');
  function load(file) {
    const absolute = path.resolve(root, file);
    // Workspace state is real; child rendering components are covered by the Angular build and overview tests.
    if (absolute.endsWith('.component.ts') && !['workspace-canvas.component.ts', 'transport-dock.component.ts', 'score-canvas.component.ts', 'edit-workspace.component.ts', 'mix-buttons.component.ts', 'part-settings-editor.component.ts', 'pattern-sequencer.component.ts', 'melody-durations-dialog.component.ts', 'compact-knob.component.ts', 'melody-global-settings.component.ts', 'harmony-settings.component.ts', 'section-context-editor.component.ts']
      .some((name) => absolute.endsWith(name))) return {};
    if (cache.has(absolute)) return cache.get(absolute).exports;
    const module = { exports: {} };
    cache.set(absolute, module);
    let compiled = compiledModules.get(absolute);
    if (compiled === undefined) {
      compiled = ts.transpileModule(fs.readFileSync(absolute, 'utf8'), { compilerOptions: {
        target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, experimentalDecorators: true,
      } }).outputText;
      compiledModules.set(absolute, compiled);
    }
    new Function('require', 'module', 'exports', compiled)((name) => name === '@angular/core' ? angular
      : name === '@angular/cdk/dialog' ? { Dialog: class Dialog {}, DialogModule: class {}, DialogRef: class {}, DIALOG_DATA: 'sequencer-data' }
      : name === '@angular/common' ? { TitleCasePipe: class {} }
      : name.startsWith('.') ? load(path.resolve(path.dirname(absolute), name + '.ts')) : require(name), module, module.exports);
    return module.exports;
  }
  const stored = new Map();
  global.sessionStorage = { getItem: (key) => stored.get(key) ?? null, setItem: (key, value) => stored.set(key, value) };
  const sectionTypes = load('core/project/section-type-settings.service.ts');
  const preferences = { chances: () => sectionTypes.DEFAULT_SECTION_TYPE_CHANCES };
  const uiStored = new Map();
  global.localStorage = { getItem: key => uiStored.get(key) ?? null, setItem: (key, value) => uiStored.set(key, value) };
  uiPreferences = new (load('core/ui/ui-preferences.service.ts').UiPreferencesService)();
  const { ProjectService } = load('core/project/project.service.ts');
  service = new ProjectService();
  // Most workflows assert one undo entry per edit; compact-history tests use the app default.
  if (!compactHistory) service.toggleCompactHistory();
  const { WorkspaceUiService } = load('core/ui/workspace-ui.service.ts');
  ui = new WorkspaceUiService();
  const { WorkspaceCanvasComponent } = load('features/workspace/workspace-canvas.component.ts');
  const workspace = new WorkspaceCanvasComponent();
  const phrase = load('core/music/phrase.ts');
  const settings = load('core/music/part-settings.ts');
  const midi = load('core/music/midi-export.ts');
  return { service, ui, workspace, phrase, settings, midi, stored, load, uiPreferences, uiStored, flushEffects };
}

function projectData(json) {
  const project = JSON.parse(json);
  for (const section of project.arrangement) {
    section.rolePartOverrides ??= {};
    section.trackPartOverrides ??= {};
  }
  return project;
}

module.exports = { fixture, projectData };
