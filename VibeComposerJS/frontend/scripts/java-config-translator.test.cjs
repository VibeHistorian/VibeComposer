const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const { translate } = require('./translate-java-config.cjs');
const { loadMusic } = require('./music-loader.cjs');
const source = fs.readFileSync(path.join(__dirname, '../GENERATOR_REFACTOR_TEST_GUICONFIG.xml-config'), 'utf8');
const node = (name, content) => `<${name}>${content}</${name}>`;
const part = (fields = '', role = 'melody', order = 1) => `<${role === 'arpeggio' ? 'arp' : role === 'chords' ? 'chord' : role === 'drums' ? 'drum' : role}Parts order="${order}">${fields}</${role === 'arpeggio' ? 'arp' : role === 'chords' ? 'chord' : role === 'drums' ? 'drum' : role}Parts>`;
const config = (sections, extra = '', seed = '42') => `<GUIConfig randomSeed="${seed}"><scaleMode>IONIAN</scaleMode><customChordsEnabled>true</customChordsEnabled><customChords>C,G</customChords><actualArrangement>${sections}</actualArrangement>${extra}</GUIConfig>`;
const section = (fields = '', type = 'PREVIEW') => `<sections type="${type}">${fields}</sections>`;
const library = (name, notes, applied = true, role = 0, order = 1) => `<patternMap part="${role}"><partOrderPatternMap><entry><key>${order}</key><value><namedNotesMap><entry><key>${name}</key><value><applied>${applied}</applied>${notes}</value></entry></namedNotesMap></value></entry></partOrderPatternMap></patternMap>`;
const ref = (name, role = 0, order = 1) => `<pattern><usedPatternMap><entry><key>${order}</key><value><part>${role}</part><partOrder>${order}</partOrder><name>${name}</name></value></entry></usedPatternMap></pattern>`;
const note = (pitch, rv, duration, offset = 0, dynamic = 80) => `<phraseNote><pitch>${pitch}</pitch><rv>${rv}</rv><duration>${duration}</duration><offset>${offset}</offset><dynamic>${dynamic}</dynamic></phraseNote>`;

test('Java order offset is preserved independently of display order and pattern seed', () => {
  const { project } = translate(config(section(),part('<orderOffset>7</orderOffset><patternSeed>123</patternSeed>','arpeggio',3)));
  const arp = project.tracks.find(track => track.id === 'track-arpeggio-3');
  assert.equal(arp.orderOffset,7);
  assert.equal(arp.generatorSettings.patternSeed,123);
  const fallback = translate(config(section(),part('<patternSeed>123</patternSeed>','arpeggio',3))).project;
  assert.equal(fallback.tracks.find(track => track.id === 'track-arpeggio-3').orderOffset,3);
});

test('contextual melody fields and planned harmony strategy translate from Java config', () => {
  const fields = { contextualMelody: true, melodyRangeMin: 55, melodyRangeMax: 79, melodyRangeRandomizeOnCompose: true,
    melodyTension: 65, melodyEnding: 'CLOSED', melodyStyle: 'MODAL', melodyForm: 'SENTENCE',
    melodyChromaticApproaches: true, melodyPhraseBars: 3, melodyBeatsPerBar: 3, progressionStrategy: 'FIFTHS' };
  const extra = Object.entries(fields).map(([key,value]) => node(key,value)).join('');
  const { project, diagnostics } = translate(config(section(),extra));
  assert.deepEqual(project.melodyGlobal, { contextualMelody: true, rangeRandomizeOnCompose: true,
    ending: 'CLOSED', form: 'SENTENCE', style: 'MODAL', chromaticApproaches: true, phraseBars: 3, beatsPerBar: 3 });
  assert.equal(project.harmonySettings.generation,'FIFTHS');
  assert.ok(!diagnostics.some(d => Object.keys(fields).some(key => d.path.includes('.'+key+'['))));
  assert.equal(translate(config(section())).project.harmonySettings.generation,'CURRENT');
});

test('real Java XML translates actual sections, supported settings, presence and independent local phrases', () => {
  const result = translate(source, 'Java parity fixture'), { project } = result;
  assert.equal(project.tempoBpm, 33); assert.equal(project.scale, 'mixolydian');
  assert.equal(project.seed, '-2129510774'); assert.equal(project.transposeSemitones, 2);
  assert.equal(project.tracks.length, 18); assert.equal(project.arrangement.length, 5);
  assert.deepEqual(project.arrangement.map(section => section.type), ['INTRO','CLIMAX','INTRO','CLIMAX','CLIMAX']);
  // Enabled custom values win over stale generated caches; Skip N−1 merges the last two slots.
  assert.deepEqual(project.arrangement.map(section => section.customDurations), [[2,2,4],[4,4,4,2,2],[.5],[4,4,4,4],[4,4,4,4]]);
  assert.deepEqual(project.arrangement.map(section => section.measures), [3,5,1,4,4]);
  assert.equal(project.arrangement[0].trackPresence['track-melody-2'], true);
  assert.equal(project.arrangement[0].trackPresence['track-melody-1'], false);
  assert.equal(project.arrangement[1].trackPresence['track-drums-1'], true, 'global drumsEnable is deliberately ignored');
  assert.equal(project.arrangement[0].context, undefined, 'inactive saved key change does not become active');
  assert.equal(project.tracks.find(track=>track.id==='track-bass-1').generatorSettings.rhythm,'bass1');
  assert.equal(project.tracks.find(track=>track.id==='track-chords-1').generatorSettings.noteLengthPercent,73);
  assert.equal(project.tracks.find(track=>track.id==='track-arpeggio-2').generatorSettings.pattern,'frog-down');
  assert.equal(project.melodyGlobal.forceFirstOutline,true); assert.equal(project.tracks.find(track => track.role === 'melody').generatorSettings.targetDirection,'ANY');
  assert.deepEqual(project.melodyGlobal.blockChangePreference,[0,7,4,3,1,2,5,6]);
  assert.equal(result.savedPhraseCount,9);
  assert.ok(project.arrangement[4].trackPhrases['track-melody-1'].length);
  assert.notDeepEqual(project.arrangement[1].trackPhrases['track-melody-1'],project.arrangement[4].trackPhrases['track-melody-1']);
  assert.equal(project.arrangement[2].trackPhrases,undefined);
  assert.ok(result.diagnostics.some(item=>item.code==='missing-pattern'));
  assert.ok(result.diagnostics.some(item=>item.code==='unsupported-value' && item.path.endsWith('arpPatternCustom')));
  assert.ok(!result.diagnostics.some(item=>/^GUIConfig\.arrangement\[/.test(item.path)));
  assert.ok(loadMusic('midi-export.ts').generateCompositionMidi(project).length>100);
});

test('Java BPM translation preserves 30 and 33 and clamps values below the minimum', () => {
  for (const [bpm, expected] of [[30, 30], [33, 33], [29, 30]]) {
    const result = translate(config(section(), part() + node('bpm', bpm)));
    assert.equal(result.project.tempoBpm, expected);
    assert.equal(result.diagnostics.some(item => item.path === 'GUIConfig.bpm' && item.code === 'adjusted'), bpm < 30);
  }
});

test('rhythm values advance across rests and zero-rhythm notes; offsets, scale, articulation and feedback are baked once', () => {
  const xml=config(section('<measures>2</measures><customDurationsEnabled>true</customDurationsEnabled><customDurations>2,2</customDurations>'+ref('Hand edit')),
    part('<transpose>12</transpose><noteLengthMultiplier>150</noteLengthMultiplier><feedbackCount>1</feedbackCount><feedbackDuration>500</feedbackDuration>')
    +library('Hand edit',note(71,0,.5,.25)+note(-2147483648,1,1)+note(60,1,.4)))
    .replace('IONIAN','MIXOLYDIAN').replace('<scaleMode>','<transpose>2</transpose><bpm>33</bpm><scaleMode>');
  const {project}=translate(xml), notes=project.arrangement[0].trackPhrases['track-melody-1'];
  assert.equal(project.arrangement[0].measures,4);
  assert.deepEqual(notes.map(note=>[note.midi,note.startBeat,note.durationBeats]),[[82,1,3],[82,1.5,3],[72,4,2.4000000000000004],[72,4.5,2.4000000000000004]]);
  const laid=loadMusic('phrase.ts').layOutTrackPhrase(project,project.tracks[0]);
  assert.deepEqual(laid.map(note=>note.midi),[84,84,74,74], 'global transpose stays live');
  assert.equal(laid.length,4,'local phrases never loop through global phrase slots or receive feedback twice');
});

test('stale generated duration caches never halve imported phrase timing or change quarter-note BPM', () => {
  const {project}=translate(source.replace('<bpm>33.0</bpm>','<bpm>40.0</bpm>'));
  const context=loadMusic('section-context.ts');
  const second=project.arrangement[1];
  assert.equal(context.sectionDurationBeats(project,second),16,'active global durations total 16 beats, not the cached 8');
  const notes=second.trackPhrases['track-melody-1'];
  assert.equal(notes[0].startBeat,.5);
  assert.equal(notes[1].startBeat,1.5,'one beat between notes, matching the Java MIDI rather than the former half beat');
  const midi=new (require('@tonejs/midi').Midi)(loadMusic('midi-export.ts').generateCompositionMidi(project));
  assert.equal(midi.header.tempos[0].bpm,40);
  const melody=midi.tracks.find(track=>track.name==='M1');
  assert.ok(Math.abs(melody.notes[1].time-melody.notes[0].time-1.5)<1e-8,'one beat at 40 BPM is 1.5 seconds in exported MIDI');
  const map=context.projectTempoMap(project);
  assert.equal(context.beatToSeconds(map,1),1.5,'playback uses the same beat duration');
  assert.equal(project.arrangement.reduce((sum,section)=>sum+context.sectionDurationBeats(project,section),0),56.5);
});

test('global duration multipliers apply once; section durations and fallback generated caches are already beats', () => {
  const global=config(section(),part())+' ';
  const doubled=translate(global.replace('</GUIConfig>','<beatDurationMultiplierIndex>2</beatDurationMultiplierIndex></GUIConfig>')).project;
  assert.deepEqual(doubled.arrangement[0].customDurations,[8,8]);
  const literal=translate(config(section('<customDurationsEnabled>true</customDurationsEnabled><customDurations>1,3</customDurations>'),
    part()+'<beatDurationMultiplierIndex>2</beatDurationMultiplierIndex>')).project;
  assert.deepEqual(literal.arrangement[0].customDurations,[1,3]);
  const fallback=translate(config(section('<generatedSectionBeatDurations>4</generatedSectionBeatDurations><generatedSectionBeatDurations>8</generatedSectionBeatDurations>'),
    part()+'<beatDurationMultiplierIndex>2</beatDurationMultiplierIndex>').replace('<customChordsEnabled>true</customChordsEnabled>','<customChordsEnabled>false</customChordsEnabled>')).project;
  assert.deepEqual(fallback.arrangement[0].customDurations,[4,8]);
});

test('named references use their own role/order, fall back to Java base patterns, and NONE forces generation', () => {
  const notes=note(60,2,.5)+note(-2147483648,2,2);
  const xml=config(section(ref('absent'),'PREVIEW')+section(ref('NONE'),'PREVIEW')+section(ref('unapplied'),'VERSE1'),
    part()+library('MAIN',notes)+library('VERSE',note(62,4,1))+library('unapplied',note(99,4,1),false));
  const {project,savedPhraseCount}=translate(xml);
  assert.equal(savedPhraseCount,2);
  assert.equal(project.arrangement[0].trackPhrases['track-melody-1'][0].midi,60);
  assert.equal(project.arrangement[1].trackPhrases,undefined);
  assert.equal(project.arrangement[2].trackPhrases['track-melody-1'][0].midi,62);
});

test('section settings, tempo, duration multiplier and key-change activation retain their proper scope', () => {
  const fields='<customDurationsEnabled>true</customDurationsEnabled><customDurations>1,3</customDurations>'
    +'<secConfig><sectionBpm>90</sectionBpm><sectionSwingOverride>60</sectionSwingOverride><beatDurationMultiplierIndex>2</beatDurationMultiplierIndex><customKeyChange>3</customKeyChange><customScale>DORIAN</customScale><customKeyChangeType>2</customKeyChangeType></secConfig>'
    +'<sectionVariations>0 0 0 0 1</sectionVariations>'+part('<speed>77</speed><transpose>7</transpose><instrument>99</instrument>');
  const xml=config(section(fields)+section(''),part('<speed>15</speed><instrument>70</instrument>'));
  const {project}=translate(xml);
  assert.equal(project.tracks[0].mix.program,70);
  assert.equal(project.tracks[0].generatorSettings.speed,15);
  assert.equal(project.arrangement[0].trackPartOverrides['track-melody-1'].speed,77);
  const equal=translate(config(section(part('<speed>15</speed>')),part('<speed>15</speed>'))).project;
  assert.equal(equal.arrangement[0].trackPartOverrides['track-melody-1'].speed,15,'explicit equal-value snapshots stay independent');
  assert.deepEqual(project.arrangement[0].customDurations,[1,3], 'Java explicit section durations are literal beats, not multiplied again');
  assert.deepEqual(project.arrangement[0].context,{tempoBpm:90,swingPercent:60});
  assert.deepEqual(project.arrangement[1].context,{scale:'dorian',keyChange:3});
});

test('copied pattern references resolve their source role/order and materialize independent target phrases', () => {
  const empty='<pattern><usedPatternMap/></pattern>';
  const copied=`<pattern part="0"><usedPatternMap><entry><key>1</key><value><part>0</part><partOrder>2</partOrder><name>Shared</name></value></entry></usedPatternMap></pattern>`;
  const maps=empty+empty+copied+empty+empty;
  const xml=config(section(maps)+section(maps),part('', 'chords')+library('Shared',note(60,4,1),true,0,2));
  const {project}=translate(xml);
  const first=project.arrangement[0].trackPhrases['track-chords-1'];
  assert.equal(first[0].midi,60);
  assert.notStrictEqual(first,project.arrangement[1].trackPhrases['track-chords-1']);
  assert.notEqual(first[0].id,project.arrangement[1].trackPhrases['track-chords-1'][0].id);
});

test('nil part lists do not manufacture tracks and unselected library patterns are reported', () => {
  const {project,diagnostics}=translate(config(section('<drumParts xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:nil="true"/>'),
    part()+library('Unused',note(60,4,1))));
  assert.deepEqual(project.tracks.map(track=>track.role),['melody']);
  assert.ok(diagnostics.some(item=>item.code==='unused-pattern'));
});

test('saved alternate harmony and global swing overrides transfer without enabling generation outlines', () => {
  const xml=config(section('<displayAlternateChords>true</displayAlternateChords><customChords>Dm,Am</customChords>'),
    part('<swingPercent>50</swingPercent>')+'<globalSwingOverride>65</globalSwingOverride>');
  const {project}=translate(xml);
  assert.deepEqual(project.arrangement[0].customChords,[{root:2,spice:'m',inversion:0},{root:9,spice:'m',inversion:0}]);
  assert.equal(project.tracks[0].generatorSettings.swingPercent,65);
});

test('outline, global enables and section instrument identity never influence the translation; long seeds are exact', () => {
  const xml=config(section(),part(),'-9223372036854775808');
  const result=translate(xml);
  assert.equal(result.project.seed,'-9223372036854775808');
  assert.deepEqual(translate(xml.replace('</GUIConfig>','<arrangement><sections type="INVALID"><measures>999</measures></sections></arrangement><melodyEnable>false</melodyEnable><soundbankName>ignored.sf2</soundbankName><humanizeNotes>500</humanizeNotes></GUIConfig>')),result);
});

test('malformed/unsafe XML, missing actual structure, invalid numbers and unsupported modes fail clearly', () => {
  for(const xml of ['<GUIConfig>','<wrong/>','<!DOCTYPE GUIConfig [<!ENTITY x "bad">]><GUIConfig/>',
    '<GUIConfig><arrangement><sections/></arrangement></GUIConfig>',config(section(),part(), '9223372036854775808'),
    config(section(),part()).replace('IONIAN','BBORIAN'),config(section('<measures>99</measures>'),part()),
    config(section(),part('<speed>NaN</speed>'))]) assert.throws(()=>translate(xml),xml);
});
