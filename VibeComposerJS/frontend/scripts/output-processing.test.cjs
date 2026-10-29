const test = require('node:test');
const assert = require('node:assert/strict');
const { Midi } = require('@tonejs/midi');
const { fixture } = require('./support/project-fixture.cjs');

test('output processing is deterministic, bounded, preserves silence and section ownership', () => {
  const f = fixture(), p = f.service.project();
  const process = f.load('core/music/output-processing.ts').processOutputNotes;
  const section = p.arrangement[0];
  const project = { ...p, humanization: { timingBeats: .1, durationBeats: .2 }, arrangement: [
    { ...section, context: { velocityPercent: { melody: 50 } } }, { ...section, id: 'other', context: { velocityPercent: { melody: 199 } } } ] };
  const notes = [0, 1, 64, 127].flatMap(velocity => [section.id, 'other'].map(sectionId =>
    ({ id: String(velocity), midi: 60, velocity, startBeat: 0, durationBeats: .01, sectionId })));
  const result = process(project, 'track', 'melody', notes);
  assert.deepEqual(result, process(project, 'track', 'melody', notes));
  assert.notDeepEqual(result, process(project, 'another', 'melody', notes));
  result.forEach((note, i) => {
    assert.ok(Math.abs(note.startBeat) <= .1);
    assert.ok(note.durationBeats >= 1 / 960 && note.durationBeats <= .21);
    assert.equal(note.sectionId, notes[i].sectionId);
    assert.equal(note.velocity, notes[i].velocity === 0 ? 0 : Math.max(1, Math.min(127,
      Math.round(notes[i].velocity * (i % 2 ? 199 : 50) / 100))));
  });
  assert.equal(notes[0].startBeat, 0);
});

test('manual arranged notes share scaling, humanization and MIDI output; padding preserves role order', () => {
  const f = fixture(), p = f.service.project(), source = p.tracks[0], section = p.arrangement[0];
  const project = { ...p, humanization: { timingBeats: .05, durationBeats: .03 },
    tracks: [source], midiPadding: { melody: 3, bass: 2, drums: 1 }, arrangement: [
      { ...section, trackPresence: { [source.id]: true }, context: { velocityPercent: { melody: 50 } },
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

test('section role velocity edits isolate roles, reject invalid maps and persist through history', () => {
  const f = fixture(), p = f.service.project(), section = p.arrangement[0];
  assert.equal(f.service.updateSectionContext(section.id, { velocityPercent: { melody: 50, bass: 199 } }), 'changed');
  const process = f.load('core/music/output-processing.ts').processOutputNotes;
  const note = [{ id: 'n', midi: 60, velocity: 64, startBeat: 1, durationBeats: 1, sectionId: section.id }];
  const project = f.service.project();
  assert.equal(process(project, 't', 'melody', note)[0].velocity, 32);
  assert.equal(process(project, 't', 'bass', note)[0].velocity, 127);
  assert.equal(process(project, 't', 'drums', note)[0].velocity, 64);
  const silent = { ...project, arrangement: [{ ...project.arrangement[0], context: { velocityPercent: { melody: 0 } } }] };
  assert.equal(process(silent, 't', 'melody', note)[0].velocity, 0);
  const valid = f.load('core/music/section-context.ts').validSectionContext;
  assert.equal(valid({ velocityPercent: -6 }), false);
  assert.equal(valid({ velocityPercent: { unknown: 0 } }), false);
  assert.equal(valid({ velocityPercent: { melody: 201 } }), false);
  assert.equal(valid({ velocityPercent: { melody: NaN } }), false);
  const codec = f.load('core/project/project-codec.ts');
  const chances = f.load('core/project/section-type-settings.service.ts').DEFAULT_SECTION_TYPE_CHANCES;
  const restored = codec.decodeProject(JSON.parse(f.service.exportProjectJson()), chances, () => .5);
  assert.deepEqual(restored.arrangement[0].context.velocityPercent, { melody: 50, bass: 199 });
});
