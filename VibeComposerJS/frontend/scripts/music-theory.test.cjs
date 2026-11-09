const assert = require('node:assert/strict');
const test = require('node:test');
const { loadMusic } = require('./music-loader.cjs');
const { chordPitches, chordKeyOverlap, chordRomanNumeral, progressionChords, CHORD_SPICES, KEYS } = loadMusic('harmony.ts');
const { SCALE_MODES, scaleNotes, convertScalePitch } = loadMusic('scales.ts');

test('every named chord spells its defining thirds, sevenths and extensions correctly', () => {
  // Independent C-root note spellings; compound extensions retain their octave.
  const spellings = {
    '': ['C','E','G'], m: ['C','Eb','G'], maj7: ['C','E','G','B'], m7: ['C','Eb','G','Bb'],
    '7': ['C','E','G','Bb'], mM7: ['C','Eb','G','B'], sus2: ['C','D','G'], sus4: ['C','F','G'],
    sus7: ['C','F','G','Bb'], maj6: ['C','E','G','A'], m6: ['C','Eb','G','A'],
    maj9: ['C','E','G','B','D+'], m9: ['C','Eb','G','Bb','D+'],
    maj13: ['C','E','G','B','D+','A+'], m13: ['C','Eb','G','Bb','D+','A+'],
    aug: ['C','E','G#'], dim: ['C','Eb','Gb'],
    add9: ['C','E','G','D+'], add11: ['C','E','G','F+'], '6/9': ['C','E','G','A','D+'],
    'maj7#11': ['C','E','G','B','F#+'], 'maj9#11': ['C','E','G','B','D+','F#+'],
    madd9: ['C','Eb','G','D+'], madd11: ['C','Eb','G','F+'], 'm6/9': ['C','Eb','G','A','D+'],
    m11: ['C','Eb','G','Bb','D+','F+'], mM9: ['C','Eb','G','B','D+'], m7b5: ['C','Eb','Gb','Bb'],
    '9': ['C','E','G','Bb','D+'], '11': ['C','G','Bb','D+','F+'], '13': ['C','E','G','Bb','D+','A+'],
    '7b5': ['C','E','Gb','Bb'], '7#5': ['C','E','G#','Bb'], '7b9': ['C','E','G','Bb','Db+'],
    '7#9': ['C','E','G','Bb','D#+'], '7#11': ['C','E','G','Bb','F#+'], '7b13': ['C','E','G','Bb','Ab+'],
    '9sus4': ['C','F','G','Bb','D+'], dim7: ['C','Eb','Gb','Bbb'],
  };
  const pitches = { C:60, D:62, Eb:63, E:64, F:65, Gb:66, G:67, 'G#':68, A:69, Bbb:69, Bb:70, B:71,
    'Db+':73, 'D+':74, 'D#+':75, 'F+':77, 'F#+':78, 'Ab+':80, 'A+':81 };
  assert.deepEqual(Object.keys(spellings).sort(), [...CHORD_SPICES].sort());
  for (const spice of CHORD_SPICES) for (let root = 0; root < 12; root++) {
    assert.deepEqual(chordPitches({ root, spice, inversion: 0 }, 60), spellings[spice].map(note => pitches[note] + root));
  }
  assert.deepEqual(chordPitches({ root:0, spice:'m9', inversion:1 },60), [63,67,70,72,74]);
  assert.equal(chordRomanNumeral({ root:0, spice:'m9', inversion:0 }, 'C'), 'i');
  assert.equal(chordKeyOverlap({ root:0, spice:'m9', inversion:0 }, 'C', 'dorian').ratio, 1);
});

test('standard scales have their actual pitch collections without repeated padding or the octave', () => {
  const expected = {
    major:[0,2,4,5,7,9,11], dorian:[0,2,3,5,7,9,10], phrygian:[0,1,3,5,7,8,10],
    lydian:[0,2,4,6,7,9,11], mixolydian:[0,2,4,5,7,9,10], 'natural-minor':[0,2,3,5,7,8,10],
    locrian:[0,1,3,5,6,8,10], blues:[0,3,5,6,7,10], 'harmonic-minor':[0,2,3,5,7,8,11],
    'harmonic-major':[0,2,4,5,7,8,11], 'double-harmonic':[0,1,4,5,7,8,11],
    'hungarian-minor':[0,2,3,6,7,8,11], 'pentatonic-major':[0,2,4,7,9], 'pentatonic-minor':[0,3,5,7,10],
    indian:[0,1,5,7,8],
  };
  for (const [scale, notes] of Object.entries(expected)) assert.deepEqual(scaleNotes(scale), notes, scale);
  for (const scale of SCALE_MODES) {
    const notes = scaleNotes(scale);
    assert.equal(notes[0], 0);
    assert.ok(notes.every((note,i) => Number.isInteger(note) && note < 12 && (!i || note > notes[i-1])), scale);
    for (const key of KEYS) {
      const tonic = KEYS.indexOf(key);
      for (const chord of progressionChords(key, scale, [1,2,3,4,5,6,7])) {
        const sounding = chordPitches(chord,60);
        assert.ok(sounding.every(p => notes.includes((p - tonic) % 12)), `${key} ${scale}`);
        assert.equal(new Set(sounding.map(p => p % 12)).size, sounding.length, `${key} ${scale}: no duplicated triad tones`);
      }
    }
  }
  assert.deepEqual(chordPitches(progressionChords('C','locrian',[1])[0],60), [60,63,66]);
  assert.deepEqual(chordPitches(progressionChords('C','pentatonic-minor',[1])[0],60), [60,63,67]);
});

test('conversion between five, six and seven note scales preserves finite in-scale pitches and octave crossings', () => {
  for (const fromMode of SCALE_MODES) for (const toMode of SCALE_MODES) {
    const from = scaleNotes(fromMode), to = scaleNotes(toMode);
    for (let octave = 0; octave < 10; octave++) for (const pitch of from) {
      const converted = convertScalePitch(octave * 12 + pitch, from, to);
      assert.ok(Number.isInteger(converted) && to.includes(converted % 12));
      assert.equal(Math.floor(converted / 12), octave);
    }
  }
  assert.equal(convertScalePitch(71, scaleNotes('major'), scaleNotes('pentatonic-major'), true), 69);
  assert.equal(convertScalePitch(71, scaleNotes('pentatonic-minor'), scaleNotes('pentatonic-major'), true), 72);
});

test('manual phrase mode changes between different scale sizes remain valid and undo atomically', () => {
  const { fixture } = require('./support/project-fixture.cjs');
  for (const scale of ['pentatonic-major', 'pentatonic-minor', 'blues', 'indian']) {
    const { service } = fixture();
    const track = service.project().tracks.find(track => track.role === 'melody');
    const notes = [60,62,64,65,67,69,71].map((midi,i) => ({ id: `note-${i}`, midi, startBeat:i,
      durationBeats:0.5, velocity:90 }));
    service.updateTrackPhrase(track.id, notes);
    const previous = service.exportProjectJson();
    service.updateSettings({ scale });
    const converted = service.project().tracks.find(item => item.id === track.id).editedPhrase;
    assert.equal(converted.length, notes.length);
    assert.ok(converted.every(note => Number.isInteger(note.midi) && scaleNotes(scale).includes(note.midi % 12)), scale);
    assert.deepEqual(converted.map(({midi,...note}) => note), notes.map(({midi,...note}) => note));
    const saved = service.exportProjectJson();
    service.undo(); assert.equal(service.exportProjectJson(), previous);
    service.redo(); assert.equal(service.exportProjectJson(), saved);
    assert.equal(service.importProjectJson(saved), true);
    assert.deepEqual(service.project().tracks.find(item => item.id === track.id).editedPhrase, converted);
  }
});
