const assert = require('node:assert/strict');

// Capture only the synchronous operation expected to reject an import. Other
// warnings remain visible, and the diagnostic-specific suite checks exact paths.
function assertRejectedProjectImport(service, json, message) {
  const warnings = [];
  const originalWarn = console.warn;
  console.warn = (...args) => {
    warnings.push(args);
    const [heading, issues] = args;
    const expected = args.length === 2 && heading === 'Could not load project from imported JSON.'
      && Array.isArray(issues) && issues.length > 0
      && issues.every(issue => typeof issue === 'string' && /^project(?:\.[\w.\[\]-]+)?: .+/.test(issue));
    if (!expected) originalWarn(...args);
  };
  try {
    assert.equal(service.importProjectJson(json), false, message);
  } finally {
    console.warn = originalWarn;
  }
  assert.equal(warnings.length, 1, message ?? 'a rejected import reports one diagnostic');
  const [heading, issues] = warnings[0];
  assert.equal(heading, 'Could not load project from imported JSON.');
  assert.ok(Array.isArray(issues) && issues.length > 0, 'invalid values report property-path diagnostics');
  for (const issue of issues) assert.match(issue, /^project(?:\.[\w.\[\]-]+)?: .+/);
}

module.exports = { assertRejectedProjectImport };
