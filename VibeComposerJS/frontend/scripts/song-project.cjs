const assert = require('node:assert/strict');
const fs = require('node:fs');
const { Midi } = require('@tonejs/midi');
const { loadMusic } = require('./music-loader.cjs');

const help = `Usage from frontend/
  node scripts/song-project.cjs template
  node scripts/song-project.cjs reference [melody|bass|chords|arpeggio|drums|variations]
  node scripts/song-project.cjs inspect FILE
  node scripts/song-project.cjs seeds FILE SECTION_ID TRACK_ID FIRST_SEED COUNT [--local]

template prints a native starter project. Edit its harmony, tracks and arrangement.
reference lists stored control keys, values, defaults and variation requirements.
inspect checks JSON round-trip, generated notes, MIDI and the effect of each variation.
seeds compares the current pattern seed and up to 100 signed 32-bit candidates.
By default candidates change the track base. --local writes a section-track override
in memory. Apply the chosen patternSeed to the same scope in your project.
Project commands print JSON to stdout and leave input files untouched.`;

function loadProject(file) {
  const { decodeProject } = loadMusic('../project/project-codec.ts');
  const { DEFAULT_SECTION_TYPE_CHANCES: chances } = loadMusic('../project/section-type-chances.ts');
  const random = () => 0.5;
  const raw = JSON.parse(fs.readFileSync(file, 'utf8').replace(/^\uFEFF/, ''));
  const project = decodeProject(raw, chances, random);
  if (!project) {
    const { projectLoadIssues } = loadMusic('../project/project-load-diagnostics.ts');
    const issues = projectLoadIssues(raw, chances, random);
    throw new Error([file + ' is not a valid project.', ...issues].join('\n'));
  }
  return { project, decode: value => decodeProject(value, chances, random) };
}

function template() {
  const { copyDefaultProject } = loadMusic('../project/project-defaults.ts');
  const { DEFAULT_SECTION_TYPE_CHANCES } = loadMusic('../project/section-type-chances.ts');
  const { formatProjectJson } = loadMusic('../project/project-json.ts');
  return formatProjectJson({
    ...copyDefaultProject(DEFAULT_SECTION_TYPE_CHANCES, () => 0.5),
    arrangementManual: true,
    randomizeTempoOnCompose: false,
    randomizeTransposeOnCompose: false,
    randomizeHarmonyOnCompose: false,
    randomizeArrangementOnCompose: false,
  });
}

function reference(role) {
  const { PART_CONTROLS, MANUAL_PARAMETER_TARGETS } = loadMusic('part-settings.ts');
  const { DEFAULT_TRACKS } = loadMusic('../project/project-defaults.ts');
  const { SECTION_TYPES } = loadMusic('../project/arrangement.model.ts');
  const { VARIATION_DEFINITIONS, variationStrength } = loadMusic('arrangement-variation-catalogue.ts');
  const { variationProtectedKeys } = loadMusic('arrangement-variations.ts');
  const variations = Object.entries(VARIATION_DEFINITIONS).map(([kind, definition]) => ({
    kind, ...definition, protectedKeys: variationProtectedKeys(kind),
    strengths: Object.fromEntries(definition.directions.map(amount => [amount, variationStrength(kind, amount) ?? null])),
  }));
  if (role === 'variations') return variations;
  if (role !== undefined && !Object.hasOwn(PART_CONTROLS, role)) throw new Error('Unknown role ' + role);
  return {
    sectionTypes: SECTION_TYPES,
    roles: Object.fromEntries(DEFAULT_TRACKS.filter(track => role === undefined || track.role === role).map(track => [
      track.role,
      {
        defaultTrack: track,
        controls: PART_CONTROLS[track.role].filter(control => !Object.hasOwn(MANUAL_PARAMETER_TARGETS, control.key)),
        variations: variations.filter(variation => variation.roles.includes(track.role)).map(variation => variation.kind),
      },
    ])),
  };
}

function ownedNotes(notes, sectionId) {
  return notes.filter(note => note.sectionId === sectionId).sort((a, b) => a.startBeat - b.startBeat || a.midi - b.midi);
}

function noteSummary(notes, startBeat, preview = 0) {
  const relative = beat => Number((beat - startBeat).toFixed(3));
  let low = Infinity;
  let high = -Infinity;
  for (const { midi } of notes) {
    low = Math.min(low, midi);
    high = Math.max(high, midi);
  }
  return {
    count: notes.length,
    midiRange: notes.length ? [low, high] : null,
    firstBeat: notes.length ? relative(notes[0].startBeat) : null,
    lastMidi: notes.at(-1)?.midi ?? null,
    ...(preview ? {
      notes: notes.slice(0, preview).map(note => [
        relative(note.startBeat), note.midi, Number(note.durationBeats.toFixed(3)), note.velocity,
      ]),
    } : {}),
  };
}

function noteFingerprint(notes) {
  return JSON.stringify(notes.map(note => [note.startBeat, note.midi, note.durationBeats, note.velocity]));
}

function inspect(file) {
  const { project, decode } = loadProject(file);
  const { formatProjectJson } = loadMusic('../project/project-json.ts');
  const { resolveProjectTimeline } = loadMusic('project-timeline.ts');
  const { sectionGenerationHarmony } = loadMusic('project-harmony.ts');
  const { phraseForTrack, resolvedTrackPhrase } = loadMusic('phrase.ts');
  const { inspectArrangementVariation } = loadMusic('arrangement-variation-inspection.ts');
  const { generateCompositionMidi } = loadMusic('midi-export.ts');
  assert.deepEqual(decode(JSON.parse(formatProjectJson(project))), project, 'Project export round-trip changed data');
  // The Open command checks each track's unarranged phrase as well as the full layout.
  for (const track of project.tracks) phraseForTrack(project, track);
  const timeline = resolveProjectTimeline(project);
  const midi = new Midi(generateCompositionMidi(project));
  return {
    name: project.name,
    bpm: project.tempoBpm,
    durationSeconds: Number(timeline.durationSeconds.toFixed(2)),
    midi: { tracks: midi.tracks.length, notes: midi.tracks.reduce((sum, track) => sum + track.notes.length, 0) },
    sections: project.arrangement.map((section, index) => {
      const { startBeat, endBeat } = timeline.sections[index];
      const harmony = sectionGenerationHarmony(project, section, 'chords');
      return {
        id: section.id, type: section.type, measures: section.measures, startBeat, endBeat,
        degrees: harmony.degrees, chords: harmony.customChords, chordBeats: harmony.durations,
        roleOverrides: Object.keys(section.rolePartOverrides ?? {}),
        trackOverrides: Object.keys(section.trackPartOverrides ?? {}),
        tracks: Object.fromEntries(project.tracks.filter(track => section.trackPresence[track.id]).map(track => [
          track.id, noteSummary(ownedNotes(timeline.notesByTrack[track.id], section.id), startBeat),
        ])),
        variations: (section.variations?.choices ?? []).map((choice, choiceIndex) => {
          const track = project.tracks.find(candidate => candidate.id === choice.trackId);
          const { status, detail } = inspectArrangementVariation(track, section, choice, project.melodyGlobal);
          const off = choice.enabled ? {
            ...project,
            arrangement: project.arrangement.with(index, {
              ...section,
              variations: {
                ...section.variations,
                choices: section.variations.choices.with(choiceIndex, { ...choice, enabled: false }),
              },
            }),
          } : project;
          return {
            track: track.id, kind: choice.kind, status, detail,
            changesNotes: noteFingerprint(ownedNotes(timeline.notesByTrack[track.id], section.id))
              !== noteFingerprint(ownedNotes(resolvedTrackPhrase(off, track), section.id)),
          };
        }),
      };
    }),
  };
}

function seeds(file, sectionId, trackId, firstText, countText, scope) {
  const first = Number(firstText);
  const count = Number(countText);
  if (!/^-?\d+$/.test(firstText) || !Number.isInteger(first) || first < -2147483648 ||
      first > 2147483647 || !/^\d+$/.test(countText) || count < 1 || count > 100 || first + count - 1 > 2147483647) {
    throw new Error('Use signed 32-bit seeds and a count from 1 to 100.');
  }
  if (scope !== undefined && scope !== '--local') throw new Error('Unknown option ' + scope);
  const { project } = loadProject(file);
  const { resolveProjectTimeline } = loadMusic('project-timeline.ts');
  const { resolvedTrackPhrase } = loadMusic('phrase.ts');
  const { resolvePartTrack } = loadMusic('part-settings.ts');
  const sectionIndex = project.arrangement.findIndex(section => section.id === sectionId);
  const trackIndex = project.tracks.findIndex(track => track.id === trackId);
  const section = project.arrangement[sectionIndex];
  const track = project.tracks[trackIndex];
  if (!section || !track) throw new Error('Use a section ID and track ID from inspect.');
  if (!section.trackPresence[trackId]) throw new Error('The selected track is absent in this section.');
  const local = scope === '--local';
  if (!local && [section.rolePartOverrides?.[track.role], section.trackPartOverrides?.[trackId]]
    .some(patch => patch?.patternSeed !== undefined)) {
    throw new Error('A section override owns patternSeed. Use --local to screen it.');
  }
  if (section.trackPhrases?.[trackId] !== undefined ||
      (track.editedPhrase !== undefined && !section.generatedTrackIds?.includes(trackId))) {
    throw new Error('A manual phrase owns this section and track.');
  }
  const timeline = resolveProjectTimeline(project);
  const startBeat = timeline.sections[sectionIndex].startBeat;
  const summarize = (candidate, voice) => noteSummary(ownedNotes(resolvedTrackPhrase(candidate, voice), sectionId), startBeat, 12);
  const currentSeed = resolvePartTrack(track, section, project.melodyGlobal).generatorSettings.patternSeed ?? 0;
  return {
    section: sectionId, track: trackId, scope: local ? 'section-track' : 'track',
    noteFields: ['beat', 'midi', 'durationBeats', 'velocity'],
    current: { seed: currentSeed, ...summarize(project, track) },
    candidates: Array.from({ length: count }, (_, index) => {
      const seed = first + index;
      const patchedTrack = local ? track : { ...track, generatorSettings: { ...track.generatorSettings, patternSeed: seed } };
      const candidate = local ? {
        ...project,
        arrangement: project.arrangement.with(sectionIndex, {
          ...section,
          trackPartOverrides: {
            ...section.trackPartOverrides,
            [trackId]: { ...section.trackPartOverrides?.[trackId], patternSeed: seed },
          },
        }),
      } : { ...project, tracks: project.tracks.with(trackIndex, patchedTrack) };
      return { seed, ...summarize(candidate, patchedTrack) };
    }),
  };
}

try {
  const [command, ...args] = process.argv.slice(2);
  if (!command || command === '--help') console.log(help);
  else if (command === 'template' && args.length === 0) console.log(template());
  else if (command === 'reference' && args.length <= 1) console.log(JSON.stringify(reference(...args), null, 2));
  else if (command === 'inspect' && args.length === 1) console.log(JSON.stringify(inspect(...args), null, 2));
  else if (command === 'seeds' && [5, 6].includes(args.length)) console.log(JSON.stringify(seeds(...args)));
  else throw new Error(help);
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}
