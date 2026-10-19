const assert = require('node:assert/strict');
const test = require('node:test');
const { Midi } = require('@tonejs/midi');
const { importMidiPhrase } = require('./music-loader.cjs').loadMusic('midi-import.ts');
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
