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
 let now=0;const listeners=new Map();const session={phraseBeat:signal(null),countInFlash:signal(false)};
 function signal(value){return Object.assign(()=>value,{set:next=>{value=next;}});}
 const prefs={capturingShortcut:()=>false,matchesShortcut:()=>false,preferences:()=>({recordingCountIn:countIn,recordingTick:false,keyboardNoteInput:true})};
 const dependencies={ElementRef:{nativeElement:{}},ProjectService:{project:()=>({tempoBpm:120})},UiPreferencesService:prefs,MidiInputService:{ listen(listener){this.listener=listener;return()=>{this.listener=undefined;};}, ownsInput(listener){return this.listener===listener;} },LiveRecordingService:session};
 const angular={ Component:()=>t=>t,ElementRef:{name:'ElementRef'},input:signal,output:()=>({values:[],emit(v){this.values.push(v);}}),computed:read=>read,ChangeDetectionStrategy:{},signal,
  EventEmitter:class { values=[];emit(v){this.values.push(v);} },inject:token=>dependencies[token?.name] };
 const modules={'@angular/core':angular,'../../core/audio/live-recording':helpers,'../../shared/keyboard-shortcuts':{isEditingText:()=>false}};
 for(const [name,file] of [['ProjectService','../../core/project/project.service'],['UiPreferencesService','../../core/ui/ui-preferences.service'],['MidiInputService','../../core/audio/midi-input.service'],['LiveRecordingService','../../core/audio/live-recording.service']])modules[file]={[name]:{name}};
 const globals={performance:{now:()=>now},window:{addEventListener:(k,v)=>listeners.set(k,v),removeEventListener:k=>listeners.delete(k)},
  requestAnimationFrame:()=>1,cancelAnimationFrame(){},setInterval:()=>1,clearInterval(){},AudioContext:class{resume(){return Promise.resolve();}close(){return Promise.resolve();}}};
 const Component=load('features/recording/live-recording.component.ts',modules,globals).LiveRecordingComponent;
 const component=new Component();component.notes.set(true);
 // Audio behavior is independently covered by playback tests; this clock tests capture gating.
 const scheduledTick=component.tick;component.tick=()=>{};
 return {component,session,listeners,tick:scheduledTick,advance(ms){now=ms;component.advance();}};
}
test('four beat count-in gates notes and cursors, and cancelling it leaves no recording gesture', () => {
 const f=recordingFixture(),c=f.component;c.toggle();
 assert.equal(c.countingIn(),4);assert.equal(c.began.values.length,0);
 c.input({id:'key',midi:60,velocity:90,down:true});assert.equal(c.noteInput.values.length,0);
 f.advance(1490);assert.equal(c.countingIn(),2);assert.equal(c.cursor.values.length,0);
 f.advance(2000);assert.equal(c.countingIn(),0);assert.equal(c.began.values.length,1);
 c.input({id:'key',midi:60,velocity:90,down:true});f.advance(2300);
 assert.ok(c.heldNotes.values.at(-1)[0].end>0);assert.equal(c.noteInput.values.length,1);
 c.stop();assert.equal(c.noteInput.values.at(-1).down,false);assert.equal(f.session.phraseBeat(),null);c.ngOnDestroy();assert.equal(f.listeners.size,0);
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
   const shift=label.label.startsWith('⇧') || /^[A-Z]$/.test(label.label),key=label.label.replace('⇧','');
   const code=/\d/.test(key)?'Digit'+key:'Key'+key.toUpperCase();
   assert.equal(label.midi,helpers.keyboardMidi(code,shift,scale));
   if(!shift)assert.ok(scale.includes(label.midi%12));
  }
 }
 assert.equal(helpers.keyboardMidi('Digit3',false),40,'disabled matching retains C-major white keys');
});
test('count-in, cursor wrapping and accent boundaries follow six-beat and fractional loops', () => {
 const f=recordingFixture(),c=f.component;c.beats.set(16);c.measureBeats.set(6);c.toggle();assert.equal(c.countingIn(),6);
 f.advance(2000);assert.equal(c.countingIn(),2);assert.equal(c.began.values.length,0);
 f.advance(3000);assert.equal(c.began.values.length,1);f.advance(11500);
 assert.ok(Math.abs(c.beat()-1)<1e-7);assert.equal(c.displayBeat(),2);assert.equal(c.beatCount(),16);c.stop();
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
test('recording highlights all held notes instead of normal selection, including loop crossings', () => {
 const f=fixture(),editor=new (f.load('features/edit/edit-workspace.component.ts').EditWorkspaceComponent)();editor.ngOnInit();
 let running=true;editor.recorder=()=>({running:()=>running});editor.phraseLength.set(4);editor.selectedNoteIds.set(['previous']);
 assert.deepEqual(editor.canvasSelectedNoteIds(),[],'silence does not highlight the previously recorded note');
 const a={id:'a',start:0,end:.5,midi:60,velocity:90},b={id:'b',start:3.8,end:4.3,midi:64,velocity:90};
 editor.updateHeldNotes([a,b]);assert.deepEqual(editor.canvasSelectedNoteIds(),['recording-a-0','recording-b-1']);
 assert.ok(!editor.canvasSelectedNoteIds().includes('recording-b-0'),'earlier loop segment is not sounding now');
 editor.updateHeldNotes([b]);assert.deepEqual(editor.canvasSelectedNoteIds(),['recording-b-1']);
 editor.updateHeldNotes([]);assert.deepEqual(editor.canvasSelectedNoteIds(),[]);
 assert.deepEqual(editor.selectedNoteIds(),['previous'],'live highlighting does not replace the editing selection');
 editor.finishRecording();running=false;assert.deepEqual(editor.canvasSelectedNoteIds(),['previous']);
});
test('keyboard labels use letter case and hide shifted aliases without changing valid input', () => {
 const labels=helpers.keyboardLabels();
 assert.deepEqual(labels.filter(l=>l.midi===53).map(l=>l.label),['q']);
 assert.deepEqual(labels.filter(l=>l.midi===54).map(l=>l.label),['Q']);
 assert.deepEqual(labels.filter(l=>l.midi===60).map(l=>l.label),['t']);
 assert.equal(helpers.keyboardMidi('KeyR',true),helpers.keyboardMidi('KeyT',false),'hidden uppercase R remains a valid alias for t');
 assert.ok(!labels.some(l=>/^⇧[a-z]$/i.test(l.label)));
});
test('idle keyboard and MIDI audition emit sound events and never create recording spans', () => {
 const f=recordingFixture(false),c=f.component;c.ngOnInit();
 const key={code:'KeyQ',shiftKey:false,preventDefault(){},stopImmediatePropagation(){}};
 c.keydown(key);assert.equal(c.noteInput.values.at(-1).midi,53);assert.equal(c.noteInput.values.at(-1).down,true);
 c.input({id:'midi',midi:108,velocity:110,down:true});
 c.keyup(key);assert.equal(c.noteInput.values.at(-1).down,false);
 assert.equal(c.span.values.length,0);assert.equal(c.began.values.length,0);assert.equal(c.cursor.values.length,0);
 assert.ok(c.heldNotes.values.every(notes=>notes.length===0),'idle audition does not draw phrase notes');
 c.stop();assert.equal(c.noteInput.values.at(-1).id,'midi');assert.equal(c.noteInput.values.at(-1).down,false);
 assert.equal(c.span.values.length,0);c.ngOnDestroy();assert.equal(f.listeners.size,0);
});
test('audition-to-record transitions release previews and record only new input', () => {
 const f=recordingFixture(false),c=f.component;c.ngOnInit();
 c.input({id:'idle',midi:60,velocity:90,down:true});c.toggle();assert.equal(c.span.values.length,0);
 f.advance(2000);c.input({id:'recorded',midi:64,velocity:90,down:true});f.advance(2500);c.stop();
 assert.equal(c.span.values.length,1);assert.equal(c.span.values[0].midi,64);
 c.input({id:'idle-again',midi:67,velocity:90,down:true});c.input({id:'idle-again',midi:67,velocity:0,down:false});
 assert.equal(c.span.values.length,1);c.ngOnDestroy();
});
test('idle audition highlights retain shared pitches until every source releases and leave draft/history untouched', () => {
 const f=fixture(),editor=new (f.load('features/edit/edit-workspace.component.ts').EditWorkspaceComponent)();editor.ngOnInit();
 let releases=0;editor.audio={liveAudition:()=>()=>releases++};
 const before=JSON.stringify(editor.draftNotes());
 editor.previewInput({id:'a',midi:60,velocity:90,down:true});editor.previewInput({id:'b',midi:60,velocity:90,down:true});
 assert.deepEqual(editor.soundingPitches(),[60]);
 editor.previewInput({id:'a',midi:60,velocity:0,down:false});assert.deepEqual(editor.soundingPitches(),[60]);
 editor.previewInput({id:'b',midi:60,velocity:0,down:false});assert.deepEqual(editor.soundingPitches(),[]);
 assert.equal(releases,2);assert.equal(JSON.stringify(editor.draftNotes()),before);assert.equal(editor.canUndo(),false);
});
test('MIDI input ownership restores the live underlying editor and never restores a destroyed subscriber', () => {
 const service=midiFixture();const a=[],b=[];const one=n=>a.push(n),two=n=>b.push(n);
 const stopOne=service.listen(one);const stopTwo=service.listen(two);
 assert.equal(a.at(-1).id,'*');assert.equal(service.ownsInput(two),true);
 stopTwo();assert.equal(service.ownsInput(one),true);service.dispatch({id:'test',midi:60,velocity:90,down:true});assert.equal(a.at(-1).id,'test');
 const stopOther=service.listen(two);stopOne();stopOther();assert.equal(service.ownsInput(one),false);
});
test('dense keyboard labels alternate stable columns and fit without vertical overlap', () => {
 const {keyboardLabelLayout}=load('features/score/score-keyboard-labels.ts');
 const labels=new Map(Array.from({length:61},(_,i)=>[96-i,'x']));
 const rows=[...labels.keys()].map((midi,i)=>({midi,y:i*4,height:4,drum:false}));
 const dense=keyboardLabelLayout(rows,labels);assert.deepEqual([...new Set(dense.map(l=>l.column))],[0,1]);
 for(const column of [0,1]) {const lane=dense.filter(l=>l.column===column);for(let i=1;i<lane.length;i++)assert.ok(lane[i-1].y+lane[i-1].fontSize<lane[i].y);}
 const scrolled=keyboardLabelLayout(rows.slice(7),labels);assert.equal(scrolled[0].column,dense[7].column);
 const spacious=keyboardLabelLayout(rows.map((row,i)=>({...row,y:i*14,height:14})),labels);assert.ok(spacious.every(l=>l.column===0&&l.fontSize===9));
});
test('x/X release works without physical codes and a fresh press recovers a lost release', () => {
 const f=recordingFixture(false),c=f.component;c.ngOnInit();
 const event=(key,code,shiftKey=false)=>({key,code,shiftKey,preventDefault(){},stopImmediatePropagation(){}});
 for(const shift of [false,true])for(let i=0;i<2;i++){
  c.keydown(event(shift?'X':'x','KeyX',shift));c.keyup(event(shift?'X':'x','',shift));assert.equal(c.held.size,0);
 }
 c.keydown(event('x','KeyX'));c.keydown(event('x','KeyX'));assert.equal(c.noteInput.values.at(-2).down,false);assert.equal(c.noteInput.values.at(-1).down,true);
 c.ngOnDestroy();
});
test('voice-release errors cannot retain x highlights or block re-audition', () => {
 const f=fixture(),editor=new (f.load('features/edit/edit-workspace.component.ts').EditWorkspaceComponent)();editor.ngOnInit();
 let starts=0;editor.audio={liveAudition:()=>{starts++;return()=>{throw Error('disposed voice');};}};
 for(let i=0;i<2;i++){
  editor.previewInput({id:'KeyX',midi:88,velocity:90,down:true});
  assert.doesNotThrow(()=>editor.previewInput({id:'KeyX',midi:88,velocity:0,down:false}));assert.deepEqual(editor.soundingPitches(),[]);
 }
 assert.equal(starts,2);assert.doesNotThrow(()=>editor.finishRecording());
});
test('backing-track preference defaults off and persists independently of the project', () => {
 const f=fixture(),before=JSON.stringify(f.service.project());assert.equal(f.uiPreferences.preferences().recordingOtherTracks,false);
 f.uiPreferences.setRecordingOption('recordingOtherTracks',true);assert.equal(JSON.parse(f.uiPreferences.exportJson()).recordingOtherTracks,true);
 assert.equal(JSON.stringify(f.service.project()),before);
});
test('selected-note action shortcuts bypass audition while unbound keys still audition', () => {
 const f=fixture(),editor=new (f.load('features/edit/edit-workspace.component.ts').EditWorkspaceComponent)();editor.ngOnInit();
 const {shortcutPreferences}=require('./support/shortcut-preferences.cjs');editor.preferences=shortcutPreferences();
 const event={key:'T',code:'KeyT',shiftKey:true,preventDefault(){this.prevented=true;},stopImmediatePropagation(){this.stopped=true;}};
 assert.equal(editor.preferActionShortcut(event),false);
 editor.selectedNoteIds.set(['selected']);assert.equal(editor.preferActionShortcut(event),true);
 assert.equal(editor.preferActionShortcut({...event,key:'q',code:'KeyQ',shiftKey:false}),false);
 const fixtureInput=recordingFixture(false),c=fixtureInput.component;c.ngOnInit();c.preferActionShortcut.set(editor.preferActionShortcut);
 c.keydown(event);assert.equal(c.noteInput.values.length,0);assert.equal(event.prevented,undefined,'editing handler must receive the event');
 editor.selectedNoteIds.set([]);c.keydown(event);assert.equal(c.noteInput.values.length,1);assert.equal(event.prevented,true);
 editor.selectedNoteIds.set(['selected']);c.keyup(event);assert.equal(c.noteInput.values.at(-1).down,false,'changing selection does not strand a held key');
 c.ngOnDestroy();
});
test('live recording reserves shifted note keys even when selected-note actions match', () => {
 const f=recordingFixture(false),c=f.component;c.ngOnInit();c.preferActionShortcut.set(()=>true);
 const event={key:'T',code:'KeyT',shiftKey:true,preventDefault(){this.prevented=true;},stopImmediatePropagation(){this.stopped=true;}};
 c.keydown(event);assert.equal(c.noteInput.values.length,0,'idle selection prefers the action');
 c.toggle();f.advance(2000);c.keydown(event);assert.equal(c.noteInput.values.at(-1).midi,61);assert.equal(c.noteInput.values.at(-1).down,true);
 assert.equal(event.prevented,true);assert.equal(event.stopped,true);f.advance(2250);c.keyup(event);
 assert.equal(c.span.values.length,1);assert.equal(c.span.values[0].midi,61);
 c.stop();const count=c.noteInput.values.length;event.prevented=false;c.keydown(event);
 assert.equal(c.noteInput.values.length,count,'after recording stops, action priority returns');assert.equal(event.prevented,false);c.ngOnDestroy();
});

 test('armed count-in loops until the first note and records that note at the positioned beat', () => {
  const f=recordingFixture(),c=f.component;
  c.armed=()=>true;c.beats.set(16);c.startBeat.set(7);c.toggle();
  f.advance(21500);assert.equal(c.waiting(),true);assert.equal(c.began.values.length,0);assert.ok(c.countingIn()>0);
  c.input({id:'first',midi:64,velocity:113,down:true});
  assert.equal(c.waiting(),false);assert.equal(c.began.values.length,1);assert.equal(f.session.phraseBeat(),7);
  f.advance(22000);c.input({id:'first',midi:64,velocity:0,down:false});c.stop();
  assert.equal(c.span.values.length,1);assert.equal(c.span.values[0].start,7);assert.equal(c.span.values[0].end,8);assert.equal(c.span.values[0].velocity,113);
 });
 test('short measures count at least two beats and multi-measure phrases count only one measure',()=>{
  for(const [measure,expected] of [[.5,2],[3.5,3.5],[4,4]]){
   const {component:c}=recordingFixture();c.beats.set(32);c.measureBeats.set(measure);c.toggle();assert.equal(c.countingIn(),expected);c.stop();
  }
 });
 test('a phrase recording batch takes one undo and redo; cancellation removes only the last batch',()=>{
  const f=fixture(),e=new (f.load('features/edit/edit-workspace.component.ts').EditWorkspaceComponent)();e.ngOnInit();
  const before=JSON.stringify(e.draftNotes());
  e.beginRecording();e.recordSpan({start:0,end:.5,midi:60,velocity:90});e.recordSpan({start:1,end:1.5,midi:64,velocity:90});e.finishRecording();
  const after=JSON.stringify(e.draftNotes());assert.notEqual(after,before);e.undo();assert.equal(JSON.stringify(e.draftNotes()),before);e.redo();assert.equal(JSON.stringify(e.draftNotes()),after);
  e.beginRecording();e.recordSpan({start:2,end:2.5,midi:67,velocity:90});e.finishRecording();e.cancelRecording();assert.equal(JSON.stringify(e.draftNotes()),after);
 });

test('phrase recording mode persists and Shift backtick matches the cancel shortcut',()=>{
 const f=fixture();assert.equal(f.uiPreferences.preferences().recordingMode,'count-in');
 f.uiPreferences.setRecordingMode('armed');const saved=JSON.parse(f.uiPreferences.exportJson());assert.equal(saved.recordingMode,'armed');
 assert.equal(f.uiPreferences.importJson(JSON.stringify({...saved,recordingMode:'invalid'})),false);
 assert.equal(f.uiPreferences.matchesShortcut('cancelPhraseRecording',{code:'Backquote',key:'~',shiftKey:true}),true);
});

test('count-in positions the playhead at the capture point and rejects out-of-region armed positions',()=>{
 for(const [armed,start,expected] of [[false,7,0],[true,7,7],[true,-1,0],[true,16,0],[true,19,0]]){
  const f=recordingFixture(),c=f.component,seeks=[];c.armed=()=>armed;c.beats.set(16);c.startBeat.set(start);
  c.playback={pause(){},seek(project,beat){seeks.push(beat);}};
  c.backingContext.set({project:{},startBeat:20,trackIds:[]});c.toggle();
  assert.equal(f.session.phraseBeat(),expected);assert.deepEqual(seeks,[20+expected]);c.stop();
 }
});
test('count-in flashes follow scheduled audio time for 100ms and recording accents repeat each measure',()=>{
 const f=recordingFixture(),c=f.component;c.beats.set(16);c.toggle();
 const beeps=[];const audio={currentTime:0,destination:{},close(){return Promise.resolve();},
 createOscillator(){const oscillator={frequency:{value:0},connect(){},start(at){beeps.push({at,frequency:this.frequency.value});},stop(){}};return oscillator;},
 createGain(){return {gain:{setValueAtTime(){},exponentialRampToValueAtTime(){}},connect(){}};}};
 c.audio=audio;c.preferences.preferences=()=>({recordingTick:true});
 f.tick();f.advance(0);assert.equal(f.session.countInFlash(),true);
 audio.currentTime=.101;f.advance(101);assert.equal(f.session.countInFlash(),false);
 audio.currentTime=.45;f.advance(450);f.tick();assert.equal(f.session.countInFlash(),false,'lookahead does not flash early');
 audio.currentTime=.5;f.advance(500);assert.equal(f.session.countInFlash(),true);
 for(let ms=1000;ms<=4500;ms+=500){audio.currentTime=ms/1000;f.advance(ms);f.tick();}
 assert.deepEqual(beeps.filter(beep=>beep.frequency===1200).map(beep=>beep.at),[0,2,4]);
 assert.equal(f.session.countInFlash(),false,'recording ticks do not flash');c.stop();assert.equal(f.session.countInFlash(),false);
});
test('free recording preserves onset and release, splits loops, and Lock Grid opts into quantization',()=>{
 const f=fixture(),e=new (f.load('features/edit/edit-workspace.component.ts').EditWorkspaceComponent)();e.ngOnInit();
 assert.equal(f.uiPreferences.preferences().recordingLockGrid,false);e.phraseLength.set(4);
 e.updateHeldNotes([{id:'free',start:.31,end:.83,midi:60,velocity:90}]);assert.ok(Math.abs(e.heldRecordingNotes()[0].startBeat-.31)<1e-9);
 e.recordSpan({start:.31,end:.83,midi:60,velocity:90});let last=e.draftNotes().at(-1);assert.ok(Math.abs(last.startBeat-.31)<1e-9);assert.ok(Math.abs(last.durationBeats-.52)<1e-9);
 const segments=helpers.recordedSegments({start:3.93,end:4.27,midi:60,velocity:90},4,null);
 assert.ok(Math.abs(segments.reduce((sum,n)=>sum+n.end-n.start,0)-.34)<1e-9);assert.equal(segments.length,2);
 f.uiPreferences.setRecordingOption('recordingLockGrid',true);e.recordSpan({start:.31,end:.83,midi:64,velocity:90});last=e.draftNotes().at(-1);assert.equal(last.startBeat,.25);assert.equal(last.durationBeats,.5);
 assert.equal(JSON.parse(f.uiPreferences.exportJson()).recordingLockGrid,true);
});

test('very short measure count-in accents stay aligned through the minimum two-beat boundary',()=>{
 const f=recordingFixture(),c=f.component;c.armed=()=>true;c.measureBeats.set(.75);c.toggle();
 const beeps=[];const audio={currentTime:0,destination:{},close(){return Promise.resolve();},
 createOscillator(){return {frequency:{value:0},connect(){},start(at){beeps.push([at,this.frequency.value]);},stop(){}};},
 createGain(){return {gain:{setValueAtTime(){},exponentialRampToValueAtTime(){}},connect(){}};}};
 c.audio=audio;
 for(const ms of [0,350,700,950,1050]){audio.currentTime=ms/1000;f.advance(ms);f.tick();}
 assert.equal(beeps.length,4);for(let i=0;i<4;i++){assert.ok(Math.abs(beeps[i][0]-[0,.375,.75,1][i])<1e-9);assert.equal(beeps[i][1],1200);}
 c.stop();
});

test('Discard on Loop defaults on, persists, and rejects invalid preferences',()=>{
 const f=fixture();assert.equal(f.uiPreferences.preferences().recordingDiscardOnLoop,true);
 f.uiPreferences.setRecordingOption('recordingDiscardOnLoop',false);const saved=JSON.parse(f.uiPreferences.exportJson());assert.equal(saved.recordingDiscardOnLoop,false);
 assert.equal(f.uiPreferences.importJson(JSON.stringify({...saved,recordingDiscardOnLoop:'false'})),false);
 delete saved.recordingDiscardOnLoop;assert.equal(f.uiPreferences.importJson(JSON.stringify(saved)),true);assert.equal(f.uiPreferences.preferences().recordingDiscardOnLoop,true);
});
test('loop grace expiry discards only earlier passes, including during silence, and keeps one undo step',()=>{
 const f=fixture(),e=new (f.load('features/edit/edit-workspace.component.ts').EditWorkspaceComponent)();e.ngOnInit();e.phraseLength.set(4);
 e.recordSpan({start:0,end:.3,midi:59,velocity:90});const before=JSON.stringify(e.draftNotes());
 e.beginRecording();e.recordSpan({start:1,end:1.5,midi:60,velocity:90});assert.notEqual(JSON.stringify(e.draftNotes()),before);
 e.recordCursor({beat:4});assert.notEqual(JSON.stringify(e.draftNotes()),before,'restart retains the previous pass');
 e.recordingMeasureBeats=()=>1;e.recordCursor({beat:5});assert.equal(JSON.stringify(e.draftNotes()),before,'grace expiry clears the previous pass');
 e.recordSpan({start:4.2,end:4.7,midi:64,velocity:90});e.recordCursor({beat:4.8});e.finishRecording();
 const after=JSON.stringify(e.draftNotes());assert.notEqual(after,before);e.undo();assert.equal(JSON.stringify(e.draftNotes()),before);e.redo();assert.equal(JSON.stringify(e.draftNotes()),after);
 e.cancelRecording();assert.equal(JSON.stringify(e.draftNotes()),before);
});
test('discarding a loop trims held previews and late releases to the latest pass without resurrecting older passes',()=>{
 const f=fixture(),e=new (f.load('features/edit/edit-workspace.component.ts').EditWorkspaceComponent)();e.ngOnInit();e.phraseLength.set(4);
 const before=JSON.stringify(e.draftNotes());e.recordingMeasureBeats=()=>.25;e.beginRecording();e.recordSpan({start:.2,end:.7,midi:60,velocity:90});
 const held={id:'held',start:3.8,end:8.4,midi:67,velocity:105};e.updateHeldNotes([held]);
 assert.equal(JSON.stringify(e.draftNotes()),before);assert.equal(e.heldRecordingNotes().length,1);assert.equal(e.heldRecordingNotes()[0].startBeat,0);
 assert.ok(Math.abs(e.heldRecordingNotes()[0].durationBeats-.4)<1e-9);
 e.recordSpan(held);const note=e.draftNotes().at(-1);assert.equal(note.startBeat,0);assert.ok(Math.abs(note.durationBeats-.4)<1e-9);assert.equal(note.velocity,105);
 e.recordSpan({start:3.9,end:4,midi:64,velocity:90});assert.equal(e.draftNotes().at(-1).id,note.id,'late older releases cannot resurrect a discarded pass');
 e.recordCursor({beat:12.25});e.finishRecording();assert.equal(JSON.stringify(e.draftNotes()),before);
});
test('turning Discard on Loop off layers every pass, including held notes across the loop',()=>{
 const f=fixture(),e=new (f.load('features/edit/edit-workspace.component.ts').EditWorkspaceComponent)();e.ngOnInit();e.phraseLength.set(4);
 f.uiPreferences.setRecordingOption('recordingDiscardOnLoop',false);const before=e.draftNotes().length;e.beginRecording();
 e.recordSpan({start:.2,end:.7,midi:60,velocity:90});e.recordCursor({beat:4});e.recordSpan({start:3.8,end:4.3,midi:64,velocity:90});e.finishRecording();
 assert.equal(e.draftNotes().length,before+3);e.undo();assert.equal(e.draftNotes().length,before);
});

test('grid-locked held notes start at zero after a fractional loop discards the earlier pass',()=>{
 const f=fixture(),e=new (f.load('features/edit/edit-workspace.component.ts').EditWorkspaceComponent)();e.ngOnInit();e.phraseLength.set(3.5);e.recordingMeasureBeats=()=>.25;e.setGrid('1');
 f.uiPreferences.setRecordingOption('recordingLockGrid',true);const before=e.draftNotes().length;e.beginRecording();
 const held={id:'held',start:3.4,end:3.8,midi:67,velocity:90};e.updateHeldNotes([held]);
 assert.equal(e.heldRecordingNotes().length,1);assert.equal(e.heldRecordingNotes()[0].startBeat,0);
 e.recordSpan(held);assert.equal(e.draftNotes().length,before+1);assert.equal(e.draftNotes().at(-1).startBeat,0);
 e.finishRecording();e.undo();assert.equal(e.draftNotes().length,before);
});

test('one-measure grace retains late releases and new-pass notes, then discards only the previous pass',()=>{
 const f=fixture(),e=new (f.load('features/edit/edit-workspace.component.ts').EditWorkspaceComponent)();e.ngOnInit();e.phraseLength.set(16);
 const baseline=e.draftNotes().map(n=>n.id);e.beginRecording();e.recordSpan({start:15.8,end:16.2,midi:65,velocity:98});
 const old=e.draftNotes().at(-2),fresh=e.draftNotes().at(-1);
 assert.ok(old.startBeat>15);assert.equal(fresh.startBeat,0);
 e.recordSpan({start:17,end:17.5,midi:67,velocity:90});const fresh2=e.draftNotes().at(-1);
 e.recordCursor({beat:19.999});assert.ok(e.draftNotes().some(n=>n.id===old.id),'previous pass survives until a full measure has elapsed');
 e.recordCursor({beat:20});assert.deepEqual(e.draftNotes().map(n=>n.id),[...baseline,fresh.id,fresh2.id]);
 e.finishRecording();e.undo();assert.deepEqual(e.draftNotes().map(n=>n.id),baseline);
});
test('stopping within the grace period preserves the previous pass as one undo step',()=>{
 const f=fixture(),e=new (f.load('features/edit/edit-workspace.component.ts').EditWorkspaceComponent)();e.ngOnInit();e.phraseLength.set(16);
 const before=JSON.stringify(e.draftNotes());e.beginRecording();e.recordSpan({start:15,end:15.5,midi:60,velocity:90});e.recordCursor({beat:16.1});e.finishRecording();
 const after=JSON.stringify(e.draftNotes());assert.notEqual(after,before);e.undo();assert.equal(JSON.stringify(e.draftNotes()),before);e.redo();assert.equal(JSON.stringify(e.draftNotes()),after);e.cancelRecording();assert.equal(JSON.stringify(e.draftNotes()),before);
});
test('short loops retain a full measure of leeway even when another restart occurs',()=>{
 const f=fixture(),e=new (f.load('features/edit/edit-workspace.component.ts').EditWorkspaceComponent)();e.ngOnInit();e.phraseLength.set(2);
 const before=e.draftNotes().length;e.beginRecording();e.recordSpan({start:1,end:1.3,midi:60,velocity:90});const first=e.draftNotes().at(-1).id;
 e.recordSpan({start:2.2,end:2.6,midi:64,velocity:90});const second=e.draftNotes().at(-1).id;
 e.recordCursor({beat:5.99});assert.equal(e.draftNotes().length,before+2);
 e.recordCursor({beat:6});assert.ok(!e.draftNotes().some(n=>n.id===first));assert.ok(e.draftNotes().some(n=>n.id===second));
 e.recordCursor({beat:8});assert.equal(e.draftNotes().length,before);
});
