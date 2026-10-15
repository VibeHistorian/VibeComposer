const fs = require('node:fs');
const path = require('node:path');
const { DOMParser } = require('@xmldom/xmldom');
const { loadMusic } = require('./music-loader.cjs');

function parseXml(xml) {
  return new DOMParser({ errorHandler: {
    warning: message => { throw new Error(message); },
    error: message => { throw new Error(message); },
    fatalError: message => { throw new Error(message); },
  } }).parseFromString(xml, 'application/xml');
}
function translate(xml, name) {
  return loadMusic('../project/java-config-translator.ts').translateJavaConfig(xml, { name, parseXml });
}
if (require.main === module) {
  const args = process.argv.slice(2), force = args.includes('--force');
  const [input, output, ...extra] = args.filter(arg => arg !== '--force');
  if (!input || extra.length) {
    console.error('Usage: npm run translate:java -- input.xml-config [output.vibecomposer.json] [--force]');
    process.exitCode = 1;
  } else {
    try {
      const name = path.basename(input).replace(/\.(?:xml-config|xml)$/i, '');
      const result = translate(fs.readFileSync(input, 'utf8'), name);
      const target = output || input.replace(/\.(?:xml-config|xml)$/i, '') + '.vibecomposer.json';
      if (path.resolve(target) === path.resolve(input)) throw new Error('Output must differ from the input XML.');
      const report = target + '.translation-report.json';
      if (!force && (fs.existsSync(target) || fs.existsSync(report))) throw new Error('Output/report already exists; use --force to replace it.');
      fs.writeFileSync(target, JSON.stringify(result.project, null, 2) + '\n', { flag: force ? 'w' : 'wx' });
      fs.writeFileSync(report, JSON.stringify({ input, tracks: result.project.tracks.length,
        sections: result.project.arrangement.length, savedPhrases: result.savedPhraseCount,
        diagnostics: result.diagnostics }, null, 2) + '\n');
      console.log(`Wrote ${target}: ${result.project.tracks.length} tracks, ${result.project.arrangement.length} sections, ${result.savedPhraseCount} saved section phrases.`);
      console.log(`${result.diagnostics.length} translation notes: ${report}`);
    } catch (error) { console.error(error.message); process.exitCode = 1; }
  }
}
module.exports = { translate, parseXml };
