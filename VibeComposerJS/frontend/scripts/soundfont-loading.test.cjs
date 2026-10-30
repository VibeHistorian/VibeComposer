const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');
const { Sf2Bank } = require('./music-loader.cjs').loadMusic('../audio/sf2-resolver.ts');
const bytes = fs.readFileSync(path.join(__dirname, '../../Super_Nintendo_Unofficial_update.sf2'));
function fixture() {
  const signal = value => {
    const read = () => value;
    read.set = next => { value = next; };
    read.update = fn => { value = fn(value); };
    read.asReadonly = () => read;
    return read;
  };
  const module = { exports: {} };
  const source = fs.readFileSync(path.join(__dirname, '../src/app/core/audio/instrument-settings.service.ts'), 'utf8');
  const compiled = ts.transpileModule(source, {compilerOptions: {target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, experimentalDecorators: true}}).outputText;
  new Function('require','module','exports',compiled)(name => name === '@angular/core'
    ? {signal, computed: fn => fn, Injectable: () => type => type} : {Sf2Bank}, module, module.exports);
  return {service: new module.exports.InstrumentSettingsService(), max: module.exports.MAX_SOUNDFONT_BYTES};
}
const file = name => new File([bytes], name);
test('loaded soundfonts appear in transport options, select by stable identity and evict oldest on fourth success', async () => {
  const {service} = fixture();
  for (const name of ['one.sf2', 'two.sf2', 'three.sf2']) await service.load(file(name));
  const ids = service.loaded().map(b => b.id);
  service.select(ids[0]);
  await service.load(file('four.sf2'));
  assert.deepEqual(service.loaded().map(b => b.name), ['two.sf2','three.sf2','four.sf2']);
  assert.equal(service.options().length, 4);
  assert.equal(service.selectedBank(), service.loaded()[2].bank);
  assert.match(service.message(), /Unloaded one.sf2/);
  service.select(ids[0]);
  assert.equal(service.kind(), service.loaded()[2].id, 'evicted IDs cannot be selected');
  service.unload(service.kind());
  assert.equal(service.kind(), 'synth');
  assert.equal(service.selectedBank(), undefined);
});
test('250 MiB is accepted; oversized files are rejected before reading or evicting a bank', async () => {
  const {service,max} = fixture();let read = false;
  await service.load({name:'large.sf2',size:max+1,arrayBuffer:async()=>{read=true;throw Error('must not read');}});
  assert.equal(read,false);assert.equal(service.loaded().length,0);assert.match(service.message(),/250 MiB/);
  const data = bytes.buffer.slice(bytes.byteOffset,bytes.byteOffset+bytes.byteLength);
  await service.load({name:'boundary.sf2',size:max,arrayBuffer:async()=>data});
  assert.equal(service.loaded().length,1);
  assert.equal(service.loaded()[0].size,max);
});
test('bad files and read failures preserve all loaded banks and the selection', async () => {
  const {service} = fixture();
  for (const name of ['one.sf2','two.sf2','three.sf2']) await service.load(file(name));
  const previous = service.loaded(), selected = service.kind();
  await service.load(new File(['invalid'], 'broken.sf2'));
  assert.equal(service.loaded(),previous);assert.equal(service.kind(),selected);assert.match(service.message(),/Could not load/);
  await service.load({name:'unreadable.sf2',size:100,arrayBuffer:async()=>{throw Error('read failed');}});
  assert.equal(service.loaded(),previous);assert.equal(service.kind(),selected);assert.equal(service.busy(),false);
  await service.load(new File([bytes], 'wrong.wav'));
  assert.match(service.message(),/SF2/);assert.equal(service.loaded(),previous);
});
