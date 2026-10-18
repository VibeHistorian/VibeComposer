const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const ts = require('typescript');

function fixture(request) {
  const signal = value => {
    const read = () => value;
    read.set = next => { value = next; };
    read.asReadonly = () => read;
    return read;
  };
  const angular = { signal, computed: read => read, Injectable: () => type => type };
  const source = fs.readFileSync(path.join(__dirname, '../src/app/core/audio/midi-output.service.ts'), 'utf8');
  const compiled = ts.transpileModule(source, { compilerOptions: {
    target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, experimentalDecorators: true,
  } }).outputText;
  const module = { exports: {} };
  new Function('require', 'module', 'exports', 'navigator', compiled)(() => angular, module, module.exports,
    request ? { requestMIDIAccess: request } : {});
  return new module.exports.MidiOutputService();
}

function port(id) {
  return { id, name: `Synth ${id}`, state: 'connected', messages: [], cleared: 0, closed: 0,
    async open() {}, async close() { this.closed++; }, clear() { this.cleared++; },
    send(data, timestamp) { this.messages.push({ data, timestamp }); },
  };
}

test('permission is requested on demand without SysEx, ports update, selection opens and disconnect resets routing', async () => {
  const one = port('one'), two = port('two');
  let changed, calls = 0;
  const service = fixture(async options => {
    calls++;
    assert.deepEqual(options, { sysex: false });
    return { outputs: new Map([[one.id, one], [two.id, two]]),
      addEventListener(type, callback) { assert.equal(type, 'statechange'); changed = callback; },
      removeEventListener() {},
    };
  });
  assert.equal(calls, 0);
  assert.equal(service.label(), 'MIDI Port...');
  await service.refresh();
  await service.refresh();
  assert.equal(calls, 1, 'reuse granted access');
  assert.equal(service.outputs().length, 2);
  await service.select('one');
  assert.equal(service.label(), 'Synth one');
  const track = { midiChannel: 3, role: 'bass', mix: { program: 32, volumePercent: 80, panPercent: 0 } };
  service.schedule(track, 48, 100, 1000, .5);
  assert.deepEqual(one.messages, [
    { data: [0xc2, 32], timestamp: undefined },
    { data: [0xb2, 7, 102], timestamp: undefined },
    { data: [0xb2, 10, 64], timestamp: undefined },
    { data: [0x92, 48, 100], timestamp: 1000 },
    { data: [0x82, 48, 0], timestamp: 1500 },
  ]);
  await service.select('two');
  assert.equal(one.cleared, 1);
  assert.equal(one.closed, 1);
  assert.deepEqual(one.messages.slice(-3).map(message => message.data), [[0xb2, 64, 0], [0xb2, 123, 0], [0xb2, 120, 0]]);
  two.state = 'disconnected'; changed();
  assert.equal(service.outputs().length, 1);
  assert.equal(service.selectedOutput(), null);
  assert.match(service.error(), /disconnected/);
  service.ngOnDestroy();
});

test('denial and unsupported browsers report actionable status; retry works and failed opens never select a device', async () => {
  let deny = true;
  const output = port('one');
  const service = fixture(async () => {
    if (deny) throw new DOMException('Denied', 'NotAllowedError');
    return { outputs: new Map([[output.id, output]]), addEventListener() {}, removeEventListener() {} };
  });
  await service.refresh();
  assert.match(service.error(), /site settings/);
  assert.equal(service.busy(), false);
  deny = false;
  await service.refresh();
  assert.equal(service.error(), '');
  output.open = async () => { throw Error('Busy'); };
  await service.select('one');
  assert.equal(service.selectedOutput(), null);
  assert.match(service.error(), /Could not open/);
  service.ngOnDestroy();
  const unsupported = fixture();
  await unsupported.refresh();
  assert.match(unsupported.error(), /unavailable|HTTPS/);
  unsupported.ngOnDestroy();
});
