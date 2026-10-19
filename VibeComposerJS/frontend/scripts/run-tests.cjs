const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const { findBrowser, requireProductionBuild } = require('./support/browser-harness.cjs');

const [group, ...args] = process.argv.slice(2);
if (!['unit', 'browser', 'overview-full'].includes(group)) {
  throw new Error('Usage: node scripts/run-tests.cjs <unit|browser|overview-full> [--list|Node test options]');
}
const scripts = path.resolve(__dirname);
const files = fs.readdirSync(scripts).filter(name => {
  if (!name.endsWith('.test.cjs')) return false;
  if (group === 'overview-full') return name === 'arrangement-overview-browser.test.cjs';
  if (group === 'browser') return name.endsWith('-browser.test.cjs');
  // This entry point is already loaded by part-settings.test.cjs, in one process.
  return !name.endsWith('-browser.test.cjs') && name !== 'parts-generation.test.cjs';
}).sort();
if (args.includes('--list')) {
  for (const file of files) console.log('scripts/' + file);
} else {
  if (group === 'browser') requireProductionBuild();
  if (group !== 'unit' && !findBrowser()) throw new Error('Set OVERVIEW_TEST_BROWSER to a Chromium executable');
  const result = spawnSync(process.execPath,
    ['--test', '--test-concurrency=1', ...args, ...files.map(file => path.join(scripts, file))], {
      cwd: path.resolve(__dirname, '..'), stdio: 'inherit',
      env: { ...process.env, ...(group === 'overview-full' ? { OVERVIEW_TEST_FULL_MATRIX: '1' } : {}) },
    });
  if (result.error) throw result.error;
  process.exitCode = result.status ?? 1;
}
