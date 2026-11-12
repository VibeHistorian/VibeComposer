const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');
const { loadMusic } = require('../music-loader.cjs');
const compiled = ts.transpileModule(fs.readFileSync(path.join(__dirname, '../../src/app/core/music/project-timeline.ts'), 'utf8'), {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS },
}).outputText;

// Exercise the real resolver while retaining each consumer fixture's phrase stand-in.
module.exports = function timelineFixture(layOutTrackPhrase) {
  const module = { exports: {} };
  new Function('require', 'module', 'exports', compiled)(name => name === './phrase'
    ? { resolvedTrackPhrase: (project, track) => Object.freeze(layOutTrackPhrase(project, track).map(note => Object.freeze({ ...note }))) }
    : loadMusic(name.slice(2) + '.ts'), module, module.exports);
  return module.exports;
};
