const assert = require('node:assert/strict');
const test = require('node:test');
const { fixture } = require('./support/project-fixture.cjs');

test('role copy freezes present tracks into a local phrase and enables the target in one undo', () => {
  const { service, phrase } = fixture();
  const p = JSON.parse(JSON.stringify(service.project()));
  const source = p.arrangement[1], target = p.arrangement[2], a = p.tracks.find(t => t.role === 'chords');
  const b = { ...a, id: 'copy-second', name: 'Second' }, absent = { ...a, id: 'copy-absent', name: 'Absent' };
  p.tracks.push(b, absent);
  for (const s of p.arrangement) s.trackPresence = { ...s.trackPresence, [b.id]: true, [absent.id]: false };
  source.trackPresence[a.id] = true;
  const note = (midi, startBeat) => ({ id: 'same', midi, startBeat, durationBeats: 2, velocity: 80 });
  source.trackPhrases = { [a.id]: [note(60, -.5)], [b.id]: [note(67, 1)], [absent.id]: [note(99, 0)] };
  target.trackPresence[a.id] = false;
  p.transposeSemitones = 5;
  assert.equal(service.importProjectJson(JSON.stringify(p)), true);
  const before = service.exportProjectJson(), originalSource = service.project().arrangement.find(s => s.id === source.id);
  service.copyArrangementPhrase({ source: { sectionId: source.id, role: 'chords' }, target: { sectionId: target.id, trackId: a.id } });
  const next = service.project(), saved = next.arrangement[2].trackPhrases[a.id];
  assert.deepEqual(saved.map(n => [n.midi, n.startBeat]), [[60, -.5], [67, 1]]);
  assert.equal(new Set(saved.map(n => n.id)).size, 2);
  assert.equal(next.arrangement[2].trackPresence[a.id], true);
  assert.equal(next.arrangement[1], originalSource);
  assert.deepEqual(phrase.layOutTrackPhrase(next, next.tracks.find(t => t.id === a.id), false, true)
    .filter(n => n.sectionId === target.id).map(n => n.midi), [65, 72]);
  const after = service.exportProjectJson();
  service.undo(); assert.equal(service.exportProjectJson(), before);
  service.redo(); assert.equal(service.exportProjectJson(), after);
});

test('individual copy leaves unrelated notes intact and rejects absent and self sources', () => {
  const { service } = fixture();
  const p = JSON.parse(JSON.stringify(service.project())), source = p.arrangement[0], target = p.arrangement[1], track = p.tracks[0];
  source.trackPresence[track.id] = true;
  source.trackPhrases = { [track.id]: [{ id: 'n', midi: 60, startBeat: 0, durationBeats: 1, velocity: 100 }] };
  service.importProjectJson(JSON.stringify(p));
  const before = service.exportProjectJson(), originalSource = service.project().arrangement.find(s => s.id === source.id);
  service.copyArrangementPhrase({ source: { sectionId: source.id, trackId: track.id }, target: { sectionId: source.id, trackId: track.id } });
  assert.equal(service.exportProjectJson(), before);
  service.copyArrangementPhrase({ source: { sectionId: source.id, trackId: track.id }, target: { sectionId: target.id, trackId: track.id } });
  assert.equal(service.project().arrangement[1].trackPhrases[track.id][0].midi, 60);
  service.undo(); assert.equal(service.exportProjectJson(), before);
  source.trackPresence[track.id] = false;
  service.importProjectJson(JSON.stringify(p));
  const absent = service.exportProjectJson();
  service.copyArrangementPhrase({ source: { sectionId: source.id, trackId: track.id }, target: { sectionId: target.id, trackId: track.id } });
  assert.equal(service.exportProjectJson(), absent);
});
