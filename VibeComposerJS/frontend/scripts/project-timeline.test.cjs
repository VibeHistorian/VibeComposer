const assert = require('node:assert/strict');
const test = require('node:test');
const { loadMusic } = require('./music-loader.cjs');
const { DEFAULT_PROJECT } = loadMusic('../project/project-defaults.ts');
const { resolveProjectTimeline, withTimelineNotes } = loadMusic('project-timeline.ts');
const { layOutTrackPhrase, resolvedTrackPhrase } = loadMusic('phrase.ts');
const { projectTempoMap, sectionDurationBeats } = loadMusic('section-context.ts');

test('one immutable timeline shares resolved notes, tempo and section/chord coordinates', () => {
  const project = { ...DEFAULT_PROJECT, arrangement: DEFAULT_PROJECT.arrangement.slice(0, 3).map((section, index) => ({
    ...section, measures: 3, customDurations: [1, 3],
    context: { beatDurationMultiplier: [.5, 1, 2][index], tempoBpm: [120, 60, 180][index] },
  })) };
  const timeline = resolveProjectTimeline(project);
  assert.equal(resolveProjectTimeline(project), timeline);
  assert.deepEqual(timeline.tempoMap, projectTempoMap(project));
  assert.deepEqual(timeline.sections.map(s => [s.startBeat, s.endBeat, s.startMeasure]), [[0, 2.5, 1], [2.5, 7.5, 4], [7.5, 17.5, 7]]);
  assert.deepEqual(timeline.sections[1].slots.map(s => [s.startBeat, s.endBeat, s.measure]), [[2.5, 3.5, 4], [3.5, 6.5, 5], [6.5, 7.5, 6]]);
  for (const track of project.tracks) {
    assert.deepEqual(timeline.notesByTrack[track.id], layOutTrackPhrase(project, track, true, true));
    assert.equal(timeline.notesByTrack[track.id], resolvedTrackPhrase(project, track), 'the timeline reuses canonical notes without allocating another copy');
  }
  for (const beat of [-2, 0, 2.5, 5, 7.5, 20]) assert.ok(Math.abs(timeline.secondsToBeat(timeline.beatToSeconds(beat)) - beat) < 1e-9);
  assert.ok(Object.isFrozen(timeline));
  assert.ok(Object.isFrozen(timeline.notesByTrack));
  for (const notes of Object.values(timeline.notesByTrack)) {
    assert.ok(Object.isFrozen(notes));
    for (const note of notes) assert.ok(Object.isFrozen(note));
  }
});

test('mix-only updates reuse the same notes and timing without resolving any phrases', () => {
  let calls = 0;
  const resolver = require('./support/timeline-fixture.cjs')(() => { calls++; return []; });
  const project = { ...DEFAULT_PROJECT };
  const original = resolver.resolveProjectTimeline(project);
  assert.equal(calls, project.tracks.length);
  const mixed = { ...project, tracks: project.tracks.map(track => ({ ...track, mix: { ...track.mix, muted: true, volumePercent: 7 } })) };
  assert.equal(resolver.resolveProjectTimeline(mixed), original);
  assert.equal(calls, project.tracks.length);
  assert.notEqual(resolver.resolveProjectTimeline({ ...mixed, tempoBpm: 90 }), original);
  assert.equal(calls, project.tracks.length * 2);
  assert.equal(resolver.resolveProjectTimeline(project), original, 'history reuses its cached immutable snapshot');
});

test('draft note timelines include pickups/tails without altering the cached song or section coordinates', () => {
  const project = { ...DEFAULT_PROJECT, tracks: [], arrangement: [{ ...DEFAULT_PROJECT.arrangement[0], measures: 1 }] };
  const base = resolveProjectTimeline(project);
  const notes = [{ id: 'pickup', midi: 60, startBeat: -2, durationBeats: 1, velocity: 100 },
    { id: 'tail', midi: 60, startBeat: 3, durationBeats: 4, velocity: 100 }];
  const draft = withTimelineNotes(base, { draft: notes });
  assert.equal(draft.sections, base.sections);
  assert.equal(draft.tempoMap, base.tempoMap);
  assert.deepEqual(draft.soundingBounds, { startBeat: -2, endBeat: 7 });
  assert.equal(draft.elapsedSeconds(-2), 0);
  assert.equal(draft.durationSeconds, draft.elapsedSeconds(7));
  notes[0].startBeat = -100;
  assert.equal(draft.notesByTrack.draft[0].startBeat, -2);
  assert.equal(resolveProjectTimeline(project), base);
  assert.deepEqual(base.soundingBounds, { startBeat: 0, endBeat: 4 });
});

test('empty projects retain a valid tempo conversion and zero duration', () => {
  const timeline = resolveProjectTimeline({ ...DEFAULT_PROJECT, tracks: [], arrangement: [] });
  assert.deepEqual(timeline.soundingBounds, { startBeat: 0, endBeat: 0 });
  assert.equal(timeline.durationSeconds, 0);
  assert.equal(timeline.secondsToBeat(timeline.beatToSeconds(-1)), -1);
});

test('fractional chord durations keep section and tempo boundaries exactly aligned', () => {
  const project = { ...DEFAULT_PROJECT, tracks: [], arrangement: Array.from({ length: 5 }, (_, index) => ({
    ...DEFAULT_PROJECT.arrangement[0], id: String(index), measures: 7, customDurations: [.3, .7, 1.1],
    context: { tempoBpm: 100 + index * 10, beatDurationMultiplier: [.5, 1, 2][index % 3] },
  })) };
  const timeline = resolveProjectTimeline(project);
  let beat = 0;
  timeline.sections.forEach((section, index) => {
    assert.equal(section.startBeat, beat);
    assert.equal(timeline.tempoMap[index].beat, beat);
    beat += sectionDurationBeats(project, project.arrangement[index]);
    assert.equal(section.endBeat, beat);
    assert.equal(section.slots.at(-1).endBeat, beat);
  });
  assert.equal(timeline.arrangementBounds.endBeat, beat);
});
