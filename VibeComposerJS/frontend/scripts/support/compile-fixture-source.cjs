const fs = require('node:fs');
const ts = require('typescript');

// Keep compiled text for this process; callers evaluate it with fresh fixture state.
const plainSources = new Map();
const decoratedSources = new Map();

function compileFixtureSource(filename, { experimentalDecorators = false } = {}) {
  const cache = experimentalDecorators ? decoratedSources : plainSources;
  let compiled = cache.get(filename);
  if (compiled === undefined) {
    compiled = ts.transpileModule(fs.readFileSync(filename, 'utf8'), {
      compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, experimentalDecorators },
    }).outputText;
    cache.set(filename, compiled);
  }
  return compiled;
}

module.exports = { compileFixtureSource };
