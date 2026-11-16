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
test('duration recording captures silence and ignores the manual drawing tool, including short taps', () => {
  const f=fixture(); const editor=new (f.load('features/part-settings/durations-dialog.component.ts').DurationsDialogComponent)();
  editor.tool.set('rest'); editor.editRow(0,'4'); editor.beginRecording();
  editor.recordCursor({beat:1,held:false});
  assert.deepEqual(editor.rows()[0],[-1,3]);
  editor.recordCursor({beat:1,held:true}); editor.recordCursor({beat:1.1,held:false});
  editor.recordCursor({beat:2,held:false});
  assert.deepEqual(editor.rows()[0],[-1,.25,-.75,2]);
  editor.recordCursor({beat:2,held:true}); editor.recordCursor({beat:3,held:false}); editor.recordCursor({beat:4,held:false});
  assert.deepEqual(editor.rows()[0],[-1,.25,-.75,1,-1]);
  editor.recordCursor({beat:5,held:false});
  assert.ok(editor.rows()[0][0]<0,'silence in a later loop replaces the prior pass');
});
test('provisional held-note segments grow continuously before quantized release', () => {
 const span={start:.31,end:.34,midi:90,velocity:99};
 const short=helpers.recordedSegments(span,4,.25,true);
 const long=helpers.recordedSegments({...span,end:.83},4,.25,true);
 assert.equal(short[0].start,.25); assert.equal(short[0].end,.34); assert.equal(long[0].end,.83);
 assert.equal(helpers.recordedSegments({...span,start:3.8,end:4.3},4,.25,true).length,2);
});
function recordingFixture(countIn = true) {
 let now=0;const listeners=new Map();const session={phraseBeat:signal(null)};
 function signal(value){return Object.assign(()=>value,{set:next=>{value=next;}});}
 const prefs={preferences:()=>({recordingCountIn:countIn,recordingTick:false,keyboardNoteInput:true})};
 const dependencies={ProjectService:{project:()=>({tempoBpm:120})},UiPreferencesService:prefs,MidiInputService:{},LiveRecordingService:session};
 const angular={ Component:()=>t=>t,input:signal,output:()=>({values:[],emit(v){this.values.push(v);}}),computed:read=>read,ChangeDetectionStrategy:{},signal,
  EventEmitter:class { values=[];emit(v){this.values.push(v);} },inject:token=>dependencies[token.name] };
 const modules={'@angular/core':angular,'../../core/audio/live-recording':helpers,'../../shared/keyboard-shortcuts':{isEditingText:()=>false}};
 for(const [name,file] of [['ProjectService','../../core/project/project.service'],['UiPreferencesService','../../core/ui/ui-preferences.service'],['MidiInputService','../../core/audio/midi-input.service'],['LiveRecordingService','../../core/audio/live-recording.service']])modules[file]={[name]:{name}};
 const globals={performance:{now:()=>now},window:{addEventListener:(k,v)=>listeners.set(k,v),removeEventListener:k=>listeners.delete(k)},
  requestAnimationFrame:()=>1,cancelAnimationFrame(){},setInterval:()=>1,clearInterval(){},AudioContext:class{resume(){return Promise.resolve();}close(){return Promise.resolve();}}};
 const Component=load('features/recording/live-recording.component.ts',modules,globals).LiveRecordingComponent;
 const component=new Component();component.notes.set(true);
 // Audio behavior is independently covered by playback tests; this clock tests capture gating.
 component.tick=()=>{};
 return {component,session,listeners,advance(ms){now=ms;component.advance();}};
}
test('four beat count-in gates notes and cursors, and cancelling it leaves no recording gesture', () => {
 const f=recordingFixture(),c=f.component;c.toggle();
 assert.equal(c.countingIn(),4);assert.equal(c.began.values.length,0);
 c.input({id:'key',midi:60,velocity:90,down:true});assert.equal(c.noteInput.values.length,0);
 f.advance(1490);assert.equal(c.countingIn(),2);assert.equal(c.cursor.values.length,0);
 f.advance(2000);assert.equal(c.countingIn(),0);assert.equal(c.began.values.length,1);
 c.input({id:'key',midi:60,velocity:90,down:true});f.advance(2300);
 assert.ok(c.heldNotes.values.at(-1)[0].end>0);assert.equal(c.noteInput.values.length,1);
 c.stop();assert.equal(c.noteInput.values.at(-1).down,false);assert.equal(f.session.phraseBeat(),null);assert.equal(f.listeners.size,0);
 const g=recordingFixture();g.component.toggle();g.component.stop();assert.equal(g.component.began.values.length,0);assert.equal(g.component.ended.values.length,0);
});
test('held phrase notes are visible and growing without changing the draft until release', () => {
 const f=fixture(),editor=new (f.load('features/edit/edit-workspace.component.ts').EditWorkspaceComponent)();editor.ngOnInit();
 const before=JSON.stringify(editor.draftNotes());const span={id:'key',start:.2,end:.4,midi:100,velocity:90};
 editor.updateHeldNotes([span]);const first=editor.heldRecordingNotes()[0];
 assert.equal(first.midi,100);assert.ok(editor.canvasNotes().some(n=>n.id===first.id));
 editor.updateHeldNotes([{...span,end:1.3}]);assert.ok(editor.heldRecordingNotes()[0].durationBeats>first.durationBeats);
 assert.equal(JSON.stringify(editor.draftNotes()),before);
 editor.recordSpan({...span,end:1.3});editor.updateHeldNotes([]);assert.ok(editor.draftNotes().some(n=>n.midi===100));
 editor.finishRecording();assert.equal(editor.heldRecordingNotes().length,0);
});
test('scale-matched keyboard input follows degrees and labels, including wrapped keys and Shift', () => {
 const minor=[2,4,5,7,9,10,0]; // D natural minor, shared scale/key pitch classes.
 assert.deepEqual(helpers.NOTE_CODES.slice(0,8).map(code=>helpers.keyboardMidi(code,false,minor)),[38,40,41,43,45,46,48,50]);
 for(const scale of [[0,2,4,5,7,9,11],minor,[11,1,3,4,6,8,10]]) {
  for(const label of helpers.keyboardLabels(scale)) {
   const shift=label.label.startsWith('⇧'),key=label.label.replace('⇧','');
   const code=/\d/.test(key)?'Digit'+key:'Key'+key;
   assert.equal(label.midi,helpers.keyboardMidi(code,shift,scale));
   if(!shift)assert.ok(scale.includes(label.midi%12));
  }
 }
 assert.equal(helpers.keyboardMidi('Digit3',false),40,'disabled matching retains C-major white keys');
});
test('count-in, cursor wrapping and accent boundaries follow six-beat and fractional loops', () => {
 const f=recordingFixture(),c=f.component;c.beats.set(6);c.toggle();assert.equal(c.countingIn(),6);
 f.advance(2000);assert.equal(c.countingIn(),2);assert.equal(c.began.values.length,0);
 f.advance(3000);assert.equal(c.began.values.length,1);f.advance(6500);
 assert.ok(Math.abs(c.beat()-1)<1e-7);assert.equal(c.displayBeat(),2);assert.equal(c.beatCount(),6);c.stop();
 const ticks=[];let beat=-3.5;for(let i=0;i<10;i++){ticks.push(beat);beat=helpers.nextRecordingTick(beat,3.5);}
 assert.deepEqual(ticks,[-3.5,-2.5,-1.5,-.5,0,1,2,3,3.5,4.5]);
 assert.equal(helpers.recordingPosition(3.5,3.5),0);assert.equal(helpers.recordingTickAtOrAfter(3.4,3.5),3.5);
});
test('keyboard scale matching defaults on, persists and rejects invalid preference values', () => {
 const f=fixture();assert.equal(f.uiPreferences.preferences().keyboardMatchScale,true);
 f.uiPreferences.setRecordingOption('keyboardMatchScale',false);
 const json=f.uiPreferences.exportJson();assert.equal(JSON.parse(json).keyboardMatchScale,false);
 assert.equal(f.uiPreferences.importJson(JSON.stringify({...JSON.parse(json),keyboardMatchScale:'false'})),false);
 assert.equal(f.uiPreferences.preferences().keyboardMatchScale,false);
});
test('keyboard tonic preserves signed project transpose and octave shifts', () => {
 const bMajor=[11,1,3,4,6,8,10];
 assert.equal(helpers.keyboardMidi('Digit1',false,bMajor,35),35,'C2 transposed down one semitone is B1');
 assert.equal(helpers.keyboardMidi('Digit1',false,[0,2,4,5,7,9,11],48),48,'an octave of project transpose is retained');
 assert.equal(helpers.keyboardLabels(bMajor,35).find(l=>l.label==='1').midi,35);
 const f=fixture(),editor=new (f.load('features/edit/edit-workspace.component.ts').EditWorkspaceComponent)();
 f.ui.selectedTrackId.set(f.service.project().tracks.find(t=>t.role==='drums').id);
 assert.deepEqual(editor.keyboardScale(),[],'drum notes bypass pitched scale matching, like scale snapping');
});
