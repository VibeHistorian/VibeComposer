const assert = require('node:assert/strict');
const test = require('node:test');
const { Midi } = require('@tonejs/midi');
const { writeMidi } = require('midi-file');
const { loadMusic } = require('./music-loader.cjs');
const { importMidiPhrase, readMidiTrack } = loadMusic('midi-import.ts');
const { midiDurationError, midiScaleChoices, fitImportedMidi } = loadMusic('midi-import-options.ts');
const { importMidiProject } = loadMusic('../project/midi-project-import.ts');
const { decodeProject } = loadMusic('../project/project-codec.ts');
const { DEFAULT_SECTION_TYPE_CHANCES } = loadMusic('../project/section-type-chances.ts');
const { formatProjectJson } = loadMusic('../project/project-json.ts');
const { resolveProjectTimeline } = loadMusic('project-timeline.ts');
const { generateCompositionMidi } = loadMusic('midi-export.ts');
const buffer = bytes => Uint8Array.from(bytes).buffer;

test('MIDI import skips conductor metadata, uses only the first note track, preserves beats across source tempos', () => {
  const midi = new Midi();
  midi.header.setTempo(75);
  midi.addTrack().addNote({ midi: 60, ticks: 240, durationTicks: 480, velocity: 100 / 127 });
  midi.addTrack().addNote({ midi: 84, ticks: 0, durationTicks: 480 });
  const notes = importMidiPhrase(buffer(midi.toArray()), 16);
  assert.deepEqual(notes.map(({ id, ...note }) => note), [{ midi: 60, startBeat: .5, durationBeats: 1, velocity: 100 }]);
});

test('MIDI import retains multiple channels belonging to the same physical format-0 track', () => {
  const events = [0, 0x90, 60, 100, 0, 0x91, 64, 90, 96, 0x80, 60, 0, 0, 0x81, 64, 0, 0, 0xff, 0x2f, 0];
  const bytes = [77,84,104,100,0,0,0,6,0,0,0,1,0,96,77,84,114,107,0,0,0,events.length,...events];
  assert.deepEqual(importMidiPhrase(buffer(bytes), 4).map(note => note.midi), [60, 64]);
});

test('MIDI import clips crossing notes, excludes later notes and bounds note count without moving the start', () => {
  const midi = new Midi(), track = midi.addTrack();
  track.addNote({ midi: 60, ticks: 480, durationTicks: 1920 });
  track.addNote({ midi: 64, ticks: 1920, durationTicks: 480 });
  const notes = importMidiPhrase(buffer(midi.toArray()), 4);
  assert.equal(notes.length, 1);
  assert.equal(notes[0].startBeat, 1);
  assert.equal(notes[0].durationBeats, 3);
  for (let i = 0; i < 2100; i++) track.addNote({ midi: 70, ticks: i, durationTicks: 1 });
  assert.equal(importMidiPhrase(buffer(midi.toArray()), 128).length, 2048);
  assert.equal(importMidiPhrase(buffer(midi.toArray()), 128, 3).length, 3);
});

test('invalid, oversized, truncated and SMPTE MIDI is rejected without notes', () => {
  assert.throws(() => importMidiPhrase(buffer([1,2,3]), 16));
  assert.throws(() => importMidiPhrase(new ArrayBuffer(8 * 1024 * 1024 + 1), 16));
  const midi = new Midi(); midi.addTrack().addNote({ midi: 60, ticks: 0, durationTicks: 480 });
  const bytes = midi.toArray();
  assert.throws(() => importMidiPhrase(buffer(bytes.slice(0, -1)), 16));
  bytes[12] = 0xe7;
  assert.throws(() => importMidiPhrase(buffer(bytes), 16), /time format/);
});

test('raw MIDI length and late notes survive parsing so rescaling happens before trimming', () => {
  const midi = new Midi(), track = midi.addTrack();
  track.addNote({ midi: 60, ticks: 0, durationTicks: 480 });
  track.addNote({ midi: 64, ticks: 20 * 480, durationTicks: 4 * 480 });
  const imported = readMidiTrack(buffer(midi.toArray()));
  assert.equal(imported.durationBeats, 24);
  assert.equal(imported.notes[1].startBeat, 20);
  const defaultNotes = fitImportedMidi(imported, 16);
  assert.equal(defaultNotes.length, 1);
  const scaled = fitImportedMidi(imported, 16, '12');
  assert.equal(scaled.length, 2);
  assert.equal(scaled[0].durationBeats, .5);
  assert.equal(scaled[1].startBeat, 10);
  assert.equal(scaled[1].durationBeats, 2);
  assert.deepEqual(imported.notes.map(note => note.startBeat), [0, 20], 'choices never mutate the parsed track');
});

test('rescaling is optional and requires a finite positive duration strictly below the phrase length', () => {
  for (const text of ['', ' ', '8', '15.99', '.5']) assert.equal(midiDurationError(text, 16), null, text);
  for (const text of ['0', '-1', '16', '17', 'NaN', 'Infinity', 'eight']) assert.ok(midiDurationError(text, 16), text);
  assert.throws(() => fitImportedMidi({ notes: [], durationBeats: 24 }, 16, '16'));
});

test('source scale translations fit the destination while keeping original pitches as the default', () => {
  const notes = [62,64,66,67,69,71,73,74].map((midi, i) => ({ id: String(i), midi, startBeat: i, durationBeats: 1, velocity: 90 }));
  const choices = midiScaleChoices(notes, 0, 'major');
  assert.ok(choices.length > 1 && choices.length <= 8);
  assert.ok(choices.some(choice => choice.label.startsWith('D Major')));
  for (const choice of choices) for (const pitch of choice.pitches) {
    assert.ok(pitch >= 0 && pitch <= 127);
    assert.ok([0,2,4,5,7,9,11].includes(pitch % 12));
  }
  const track = { notes, durationBeats: 8 };
  assert.deepEqual(fitImportedMidi(track, 16), notes);
  const translated = fitImportedMidi(track, 16, '', choices.find(choice => choice.label.startsWith('D Major')).pitches);
  assert.deepEqual(translated.map(note => note.midi), [60,62,64,65,67,69,71,72]);
  assert.deepEqual(midiScaleChoices(translated, 0, 'major'), []);
  const minor = midiScaleChoices(notes, 9, 'natural-minor');
  assert.ok(minor.length);
  for (const choice of minor) for (const pitch of choice.pitches) assert.ok([9,11,0,2,4,5,7].includes(pitch % 12));
});

const event = (tick, type, fields = {}) => ({ tick, type, ...fields });
const name = text => event(0, 'trackName', { text });
const program = (tick, channel, programNumber) => event(tick, 'programChange', { channel, programNumber });
const control = (tick, channel, controllerType, value) => event(tick, 'controller', { channel, controllerType, value });
const note = (tick, end, noteNumber, channel = 0, velocity = 96) => [
  event(tick, 'noteOn', { channel, noteNumber, velocity }), event(end, 'noteOff', { channel, noteNumber, velocity: 0 }),
];
const smf = (tracks, format = 1, ppq = 480) => buffer(writeMidi({
  header: { format, numTracks: tracks.length, ticksPerBeat: ppq },
  tracks: tracks.map(track => {
    let previous = 0;
    return track.toSorted((a, b) => a.tick - b.tick).map(({ tick, ...data }) => {
      const deltaTime = tick - previous;
      previous = tick;
      return { ...data, deltaTime };
    }).concat({ type: 'endOfTrack', deltaTime: 0 });
  }),
}, { running: true, useByte9ForNoteOff: true }));

test('project import retains performance and maps conductor timing, section markers, roles and mix into native fields', () => {
  const bytes = smf([
    [name('Overture'), event(0, 'keySignature', { key: 2, scale: 0 }), event(0, 'marker', { text: 'Intro' }),
      event(0, 'timeSignature', { numerator: 3, denominator: 4, metronome: 24, thirtyseconds: 8 }),
      event(2400, 'setTempo', { microsecondsPerBeat: 666667 }), event(2880, 'marker', { text: 'Chorus 2' }),
      event(4320, 'timeSignature', { numerator: 4, denominator: 4, metronome: 24, thirtyseconds: 8 })],
    [program(0, 0, 33), control(0, 0, 7, 95), control(0, 0, 10, 32)],
    [name('Electric Bass'), ...note(240, 840, 38)],
    [name('Piano'), ...[62, 66, 69].flatMap(pitch => note(0, 1440, pitch, 1))],
    [name('Lead'), program(0, 2, 73), ...note(1920, 3840, 74, 2, 101)],
    [name('Kit'), ...note(0, 120, 36, 9), ...note(4320, 4440, 42, 9)],
  ]);
  const { project, warnings } = importMidiProject(bytes, 'score.mid');
  assert.deepEqual(warnings, []);
  assert.equal(project.name, 'Overture');
  assert.equal(project.transposeSemitones, 2);
  assert.equal(project.scale, 'major');
  assert.deepEqual(project.tracks.map(track => [track.name, track.role, track.midiChannel, track.mix.program]), [
    ['Electric Bass', 'bass', 1, 33], ['Piano', 'chords', 2, 0], ['Lead', 'melody', 3, 73],
    ['Kit · Kick', 'drums', 10, 0], ['Kit · Closed hi-hat', 'drums', 10, 0],
  ]);
  assert.equal(project.tracks[0].mix.volumePercent, 95 * 100 / 127);
  assert.equal(project.tracks[0].mix.panPercent, -50);
  assert.deepEqual(project.arrangement.map(section => section.type), ['INTRO', 'VERSE1', 'CHORUS2']);
  const restored = decodeProject(JSON.parse(formatProjectJson(project)), DEFAULT_SECTION_TYPE_CHANCES, () => 0.5);
  assert.ok(restored, 'import survives the native project codec');
  const timeline = resolveProjectTimeline(restored);
  assert.deepEqual(timeline.sections.flatMap(section => section.slots.map(slot => slot.endBeat - slot.startBeat)), [3, 2, 1, 3, 4]);
  assert.deepEqual(timeline.tempoMap.map(({ beat, bpm }) => [beat, bpm]), [[0, 120], [5, 90]]);
  const [bass, chords, lead, kick, hat] = restored.tracks.map(track => track.id);
  const sounding = track => timeline.notesByTrack[track].map(note => [note.midi, note.startBeat, note.durationBeats, note.velocity]);
  assert.deepEqual(sounding(bass), [[38, 0.5, 1.25, 96]]);
  assert.deepEqual(sounding(chords), [62, 66, 69].map(pitch => [pitch, 0, 3, 96]));
  assert.deepEqual(sounding(lead), [[74, 4, 4, 101]], 'held note crosses two sections without retriggering');
  assert.deepEqual(sounding(kick), [[36, 0, 0.25, 96]]);
  assert.deepEqual(sounding(hat), [[42, 9, 0.25, 96]]);
  assert.equal(restored.arrangement[0].trackPresence[hat], false);
  assert.equal(restored.arrangement[2].trackPresence[kick], false);
  assert.ok(restored.arrangement[2].trackPresence[lead]);
  assert.equal(restored.arrangement[2].trackPresence[bass], false);
  assert.deepEqual(restored.arrangement[2].trackPhrases[lead], []);
  assert.equal(timeline.notesByTrack[lead][0].phraseSource, 'section-edited');
  const exported = new Midi(generateCompositionMidi(restored));
  const [exportedLead] = exported.tracks.find(track => track.name === 'Lead').notes;
  assert.deepEqual([exportedLead.midi, exportedLead.ticks / exported.header.ppq, exportedLead.durationTicks / exported.header.ppq], [74, 4, 4]);
});

test('project import separates format-0 channels and programs, respecting same-tick event order and pedal releases', () => {
  const bytes = smf([[
    ...note(0, 240, 60, 0, 101), program(0, 0, 73), ...note(0, 480, 67, 0, 102),
    control(120, 0, 64, 127), control(720, 0, 64, 0),
    program(0, 2, 33), ...note(0, 240, 36, 2, 103),
  ]], 0);
  const { project } = importMidiProject(bytes, 'Performance.MIDI');
  assert.equal(project.name, 'Performance');
  const timeline = resolveProjectTimeline(project);
  assert.deepEqual(project.tracks.map(track => [track.mix.program, track.midiChannel]), [[0, 1], [73, 2], [33, 3]]);
  assert.deepEqual(project.tracks.map(track => timeline.notesByTrack[track.id].map(({ midi, durationBeats, velocity }) =>
    [midi, durationBeats, velocity])), [[[60, 1.5, 101]], [[67, 1.5, 102]], [[36, 0.5, 103]]]);
});

test('project import infers tonal harmony when metadata is absent and keeps extreme original pitches', () => {
  const progression = [[60, 64, 67], [60, 65, 69], [60, 64, 67]];
  const bytes = smf([progression.flatMap((notes, bar) =>
    notes.flatMap(pitch => note(bar * 1920, (bar + 1) * 1920, pitch)))]);
  const { project } = importMidiProject(bytes, 'Harmony.mid');
  assert.equal(project.scale, 'major');
  assert.equal(project.transposeSemitones, 0);
  assert.deepEqual(project.progression, [1, 4, 1]);
  const extremes = importMidiProject(smf([[event(0, 'keySignature', { key: 1, scale: 0 }), ...note(0, 480, 0), ...note(480, 960, 127)]]), 'Range.mid');
  const track = extremes.project.tracks[0];
  assert.deepEqual(resolveProjectTimeline(extremes.project).notesByTrack[track.id].map(note => note.midi), [0, 127]);
  assert.ok(extremes.warnings.some(warning => warning.includes('full MIDI range')));
});

test('project import splits dense phrases at measures and reports capacity-related omissions', () => {
  const dense = smf([Array.from({ length: 2100 }, (_, index) => {
    const tick = index + (index >= 1050 ? 870 : 0);
    return note(tick, tick + 1, 60);
  }).flat()]);
  const result = importMidiProject(dense, 'Dense.mid');
  assert.deepEqual(result.warnings, []);
  assert.deepEqual(result.project.arrangement.map(section =>
    section.trackPhrases[result.project.tracks[0].id].length), [1050, 1050]);

  const limited = smf([
    [...note(0, 520 * 480, 64), ...note(520 * 480, 521 * 480, 67)],
    Array.from({ length: 18 }, (_, index) => note(index * 10, index * 10 + 5, 53 - index, 9)).flat(),
  ]);
  const { project: imported, warnings } = importMidiProject(limited, 'Limits.mid');
  const project = decodeProject(JSON.parse(formatProjectJson(imported)), DEFAULT_SECTION_TYPE_CHANCES, () => 0.5);
  assert.ok(project, 'the overflow kit survives a native project round trip');
  const timeline = resolveProjectTimeline(project);
  assert.equal(timeline.arrangementBounds.endBeat, 512);
  assert.equal(timeline.notesByTrack[project.tracks[0].id].length, 1);
  assert.equal(timeline.notesByTrack[project.tracks[0].id][0].durationBeats, 512);
  assert.equal(Object.keys(project.drumSlots).length, 16);
  const drums = project.tracks.filter(track => track.role === 'drums');
  assert.equal(drums.length, 16);
  assert.deepEqual(drums.slice(0, 15).map(track => timeline.notesByTrack[track.id].map(note => note.midi)),
    Array.from({ length: 15 }, (_, index) => [36 + index]));
  const overflow = drums.at(-1);
  assert.equal(overflow.drumSlotPitch, 51);
  assert.deepEqual(timeline.notesByTrack[overflow.id].map(note => note.midi), [53, 52, 51]);
  const drumExport = mapping => new Midi(generateCompositionMidi(project, mapping)).tracks.find(track => track.name === overflow.name).notes.map(note => note.midi);
  assert.deepEqual(drumExport('general-midi'), [53, 52, 51]);
  assert.deepEqual(drumExport('chromatic'), Array(3).fill(project.drumSlots[51]));
  assert.ok(warnings.some(warning => warning.includes('128 measures')));
  assert.ok(warnings.some(warning => warning.includes('sounds 16 onward')));
});

test('project import reports missing notes, unsupported timing and truncated chunks', () => {
  assert.throws(() => importMidiProject(smf([[name('Only metadata')]]), 'Empty.mid'), /no playable notes/);
  assert.throws(() => importMidiProject(smf([note(0, 480, 60)], 2), 'Patterns.mid'), /format 0 or 1/);
  const bytes = smf([note(0, 480, 60), note(0, 480, 64)]);
  assert.throws(() => importMidiProject(bytes.slice(0, -1), 'Incomplete.mid'), /incomplete/);
  new DataView(bytes).setUint16(12, 0xe728);
  assert.throws(() => importMidiProject(bytes, 'SMPTE.mid'), /beat-based MIDI timing/);
});
