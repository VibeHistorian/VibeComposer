const assert = require('node:assert/strict');
const test = require('node:test');
const { Midi } = require('@tonejs/midi');
const { importMidiPhrase, readMidiTrack } = require('./music-loader.cjs').loadMusic('midi-import.ts');
const { midiDurationError, midiScaleChoices, fitImportedMidi } = require('./music-loader.cjs').loadMusic('midi-import-options.ts');
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
