const test = require('node:test');
const assert = require('node:assert/strict');
const { Midi } = require('@tonejs/midi');
const { fixture } = require('./support/project-fixture.cjs');

test('output processing is deterministic, bounded, preserves silence and section ownership', () => {
  const f = fixture(), p = f.service.project();
  const process = f.load('core/music/output-processing.ts').processOutputNotes;
  const section = p.arrangement[0];
  const project = { ...p, humanization: { timingBeats: .1, durationBeats: .2 }, arrangement: [
    { ...section, context: { velocityDb: -6 } }, { ...section, id: 'other', context: { velocityDb: 12 } } ] };
  const notes = [0, 1, 64, 127].flatMap(velocity => [section.id, 'other'].map(sectionId =>
    ({ id: String(velocity), midi: 60, velocity, startBeat: 0, durationBeats: .01, sectionId })));
  const result = process(project, 'track', notes);
  assert.deepEqual(result, process(project, 'track', notes));
  assert.notDeepEqual(result, process(project, 'another', notes));
  result.forEach((note, i) => {
    assert.ok(Math.abs(note.startBeat) <= .1);
    assert.ok(note.durationBeats >= 1 / 960 && note.durationBeats <= .21);
    assert.equal(note.sectionId, notes[i].sectionId);
    assert.equal(note.velocity, notes[i].velocity === 0 ? 0 : Math.max(1, Math.min(127,
      Math.round(notes[i].velocity * 10 ** ((i % 2 ? 12 : -6) / 20)))));
  });
  assert.equal(notes[0].startBeat, 0);
});

test('manual arranged notes share scaling, humanization and MIDI output; padding preserves role order', () => {
  const f = fixture(), p = f.service.project(), source = p.tracks[0], section = p.arrangement[0];
  const project = { ...p, humanization: { timingBeats: .05, durationBeats: .03 },
    tracks: [source], midiPadding: { melody: 3, bass: 2, drums: 1 }, arrangement: [
      { ...section, trackPresence: { [source.id]: true }, context: { velocityDb: -6 },
        trackPhrases: { [source.id]: [{ id: 'manual', midi: 60, startBeat: 1, durationBeats: 1, velocity: 100 }] } } ] };
  const notes = f.phrase.layOutTrackPhrase(project, source);
  assert.equal(notes[0].velocity, 50);
  assert.deepEqual(notes, f.phrase.layOutTrackPhrase(project, source));
  const midi = new Midi(f.midi.generateCompositionMidi(project));
  assert.equal(midi.tracks.length, 6);
  assert.equal(midi.tracks[0].notes.length, 1);
  assert.equal(Math.round(midi.tracks[0].notes[0].velocity * 127), 50);
  assert.equal(midi.tracks[0].notes[0].ticks, Math.round(notes[0].startBeat * midi.header.ppq));
  assert.ok(midi.tracks.slice(1).every(track => track.notes.length === 0));
  assert.match(midi.tracks[3].name, /bass padding/);
  const over = new Midi(f.midi.generateCompositionMidi({ ...project, midiPadding: { melody: 0 } }));
  assert.equal(over.tracks.length, 1);
});

test('output settings validate and round trip; BPM bounds are preferences independent of project history', () => {
  const f = fixture();
  f.service.updateSettings({ humanization: { timingBeats: .05, durationBeats: .1 }, midiPadding: { bass: 3 } });
  const saved = f.service.exportProjectJson();
  assert.ok(saved.includes('timingBeats'));
  const p = f.service.project();
  const valid = f.load('core/music/output-processing.ts').validOutputSettings;
  assert.ok(valid(p));
  assert.equal(valid({ humanization: { timingBeats: NaN, durationBeats: 0 } }), false);
  assert.equal(valid({ midiPadding: { bass: -1 } }), false);
  const history = f.service.historyEntries().length;
  f.uiPreferences.setComposeBpmBound('composeBpmMax', 140);
  f.uiPreferences.setComposeBpmBound('composeBpmMin', 140);
  assert.equal(f.service.historyEntries().length, history);
  assert.equal(f.service.project().tempoBpm, p.tempoBpm);
  assert.ok(f.uiPreferences.importJson(f.uiPreferences.exportJson()));
  assert.equal(f.uiPreferences.preferences().composeBpmMin, 140);
  f.uiPreferences.setComposeBpmBound('composeBpmMax', 139);
  assert.equal(f.uiPreferences.preferences().composeBpmMax, 140);
});

test('Compose uses equal inclusive bounds and preserves disabled explicit tempo', () => {
  const f = fixture();
  f.uiPreferences.setComposeBpmBound('composeBpmMax', 140);
  f.uiPreferences.setComposeBpmBound('composeBpmMin', 140);
  assert.equal(f.service.generateComposition('compose'), 'changed');
  assert.equal(f.service.project().tempoBpm, 140);
  f.service.updateSettings({ tempoBpm: 95, randomizeTempoOnCompose: false });
  f.service.generateComposition('compose');
  assert.equal(f.service.project().tempoBpm, 95);
});
