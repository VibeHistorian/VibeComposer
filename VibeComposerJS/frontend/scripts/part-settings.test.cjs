// Load behavior suites in one process so they share compiled TypeScript text.
// Keep fixtures sequential: they install their own browser-storage stand-ins.
require('./parts/test-support.cjs');
require('./parts/imports-validation.cjs');
require('./parts/history.cjs');
require('./parts/manual-phrases.cjs');
require('./parts/preferences.cjs');
require('./parts-generation.test.cjs');
require('./parts/scoped-settings.cjs');
