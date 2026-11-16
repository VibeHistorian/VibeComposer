const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const ts = require('typescript');
function load(file, dependencies = {}, globals = {}) {
  const source = fs.readFileSync(path.join(__dirname, '../src/app', file), 'utf8');
  const code = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, experimentalDecorators: true } }).outputText;
  const module = { exports: {} };
  new Function('require','module','exports', ...Object.keys(globals), code)(name => dependencies[name] ?? {}, module, module.exports, ...Object.values(globals));
  return module.exports;
}
const helpers = load('core/audio/live-recording.ts');
test('full keyboard follows white notes continuously across rows; Shift raises exactly a semitone', () => {
  const expected = Array.from({length:36},(_,i)=>36 + Math.floor(i/7)*12 + [0,2,4,5,7,9,11][i%7]);
  assert.deepEqual(helpers.NOTE_CODES.map(code=>helpers.keyboardMidi(code,false)),expected);
  assert.deepEqual(helpers.NOTE_CODES.map(code=>helpers.keyboardMidi(code,true)),expected.map(n=>n+1));
  assert.equal(helpers.keyboardMidi('Space',false),null);
});
test('recording quantizes taps and splits held notes across loops without dropping duration', () => {
  const record=(start,end)=>helpers.recordedSegments({start,end,midi:61,velocity:104},4,.25);
  assert.deepEqual(record(.31,.32),[{start:.25,end:.5,midi:61,velocity:104}]);
  assert.deepEqual(record(3.8,4.6),[{start:3.75,end:4,midi:61,velocity:104},{start:0,end:.5,midi:61,velocity:104}]);
  assert.equal(record(0,12).reduce((sum,s)=>sum+s.end-s.start,0),12);
  assert.equal(helpers.recordingCell(3.99,4,16),15);
  assert.equal(helpers.recordingCell(4,4,16),0);
});
function midiFixture(request) {
  const signal = value => Object.assign(()=>value,{set:next=>{value=next;}});
  const angular = { signal, computed:read=>read, Injectable:()=>type=>type };
  return new (load('core/audio/midi-input.service.ts',{'@angular/core':angular},{navigator:{requestMIDIAccess:request}}).MidiInputService)();
}
test('MIDI requests permission on demand, filters note events, and releases held inputs on disconnect', async () => {
  let calls=0, change;
  const port={id:'p',name:'Keys',state:'connected'};
  const service=midiFixture(async options=>{calls++;assert.deepEqual(options,{sysex:false});return {inputs:new Map([['p',port]]),addEventListener:(_,fn)=>{change=fn;}};});
  assert.equal(calls,0);await service.refresh();assert.equal(calls,1);
  service.select('p');const events=[];service.listener=e=>events.push(e);
  for(const data of [[0x92,61,99],[0xb2,1,70],[0x92,61,0],[0x82,62,12]])port.onmidimessage({data});
  assert.deepEqual(events.map(e=>[e.id,e.down,e.velocity]),[['midi:2:61',true,99],['midi:2:61',false,0],['midi:2:62',false,12]]);
  port.state='disconnected';change();assert.equal(service.selected(),'');assert.equal(events.at(-1).id,'*');assert.equal(port.onmidimessage,null);
});
test('MIDI denial and unsupported browsers leave keyboard fallback available and permit retry', async () => {
  const service=midiFixture(async()=>{throw Error('Denied');}); await service.refresh();assert.equal(service.error(),'Denied');assert.equal(service.busy(),false);
  await service.refresh();assert.equal(service.error(),'Denied');
  const missing=midiFixture(undefined);await missing.refresh();assert.match(missing.error(),/keyboard recording/);
});
const { fixture } = require('./support/project-fixture.cjs');
test('live pattern holds fill crossed cells, remain a draft, and commit as one undo step', () => {
  const f=fixture();
  const { PatternSequencerComponent }=f.load('features/part-settings/pattern-sequencer.component.ts');
  const editor=new PatternSequencerComponent();
  const id=editor.rows()[0].id;
  f.service.updatePartSettings({kind:'global-track',trackId:id},{rhythm:'custom',hitsPerPattern:16,customPattern:Array(32).fill(0)});
  const before=JSON.stringify(f.service.project());
  editor.beginRecording();editor.recordSpan({start:.27,end:1.1,midi:60,velocity:90});
  assert.equal(JSON.stringify(f.service.project()),before);
  assert.deepEqual(editor.draft().pattern.slice(0,5),[0,1,1,1,0]);
  editor.endRecording();
  assert.deepEqual(f.service.project().tracks.find(t=>t.id===id).generatorSettings.customPattern.slice(0,5),[0,1,1,1,0]);
  f.service.undo();assert.equal(JSON.stringify(f.service.project()),before);
});
test('duration recording preserves pauses and separate attacks across a loop', () => {
  const f=fixture();
  const { DurationsDialogComponent }=f.load('features/part-settings/durations-dialog.component.ts');
  const editor=new DurationsDialogComponent(); editor.editRow(0,'');
  const before=JSON.stringify(f.service.project());
  editor.recordSpan({start:3.8,end:4.6,midi:60,velocity:90});
  assert.deepEqual(editor.rows()[0],[.5,-3.25,.25]);
  editor.recordSpan({start:4.6,end:4.9,midi:60,velocity:90});
  assert.deepEqual(editor.rows()[0],[.5,.5,-2.75,.25]);
  assert.equal(JSON.stringify(f.service.project()),before);
});
