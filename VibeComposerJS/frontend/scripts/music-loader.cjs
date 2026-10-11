const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');
const cache = new Map();
function loadMusic(name) {
  const file = path.resolve(__dirname, '../src/app/core/music', name);
  if (cache.has(file)) return cache.get(file);
  const module = { exports: {} }; cache.set(file, module.exports);
  const compiled = ts.transpileModule(fs.readFileSync(file, 'utf8'), { compilerOptions: {
    target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS,
  } }).outputText;
  new Function('require', 'module', 'exports', compiled)(dependency => dependency.startsWith('.')
    ? loadMusic(path.relative(path.resolve(__dirname, '../src/app/core/music'), path.resolve(path.dirname(file), dependency + '.ts')))
    : require(dependency), module, module.exports);
  cache.set(file, module.exports); return module.exports;
}
module.exports = { loadMusic };
