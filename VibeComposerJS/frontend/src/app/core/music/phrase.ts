import type { ArrangedPart, CompositionProject, CompositionTrack, PhraseNote } from '../project/project.model';
import { shouldGenerateTrackInSection } from './arrangement-generator';
import { generateArpeggio } from './arpeggio-generator';
import { generateBassline } from './bass-generator';
import { generateChordPart } from './chord-generator';
import { generateDrumPart } from './drum-generator';
import { resolvePartTrack } from './part-settings';

/** Convert the seeded generators to note-level events shared by the editor and MIDI exporter. */
export function generatePhrase(project: CompositionProject, part: ArrangedPart): PhraseNote[] {
  const seed = BigInt(project.seed);
  switch (part) {
    case 'bass':
      return generateBassline(seed, project.key, project.scale, project.progression,
        project.bass.rhythm, project.bass.noteVariation, project.bass)
        .map((note, index) => ({ id: `bass-${index}`, midi: note.midi, startBeat: note.startBeat,
          durationBeats: note.durationBeats, velocity: note.velocity }));
    case 'chords':
      return generateChordPart(seed, project.key, project.scale, project.progression, project.chords)
        .flatMap((hit, hitIndex) => hit.pitches.map((midi, pitchIndex) => ({
          id: `chords-${hitIndex}-${pitchIndex}`, midi, startBeat: hit.startBeat,
          durationBeats: hit.durationBeats, velocity: hit.velocity,
        })));
    case 'arpeggio':
      return generateArpeggio(seed, project.key, project.scale, project.progression, project.arpeggio)
        .map((note, index) => ({ id: `arpeggio-${index}`, midi: note.midi, startBeat: note.startBeat,
          durationBeats: note.durationBeats, velocity: note.velocity }));
    case 'drums':
      return generateDrumPart(seed, project.progression.length, project.drums)
        .map((hit, index) => ({ id: `drums-${index}`, midi: hit.midi, startBeat: hit.startBeat,
          durationBeats: hit.durationBeats, velocity: hit.velocity }));
  }
}

/** Return saved note edits when present, otherwise build the phrase from project settings. */
export function phraseForProject(project: CompositionProject, part: ArrangedPart): readonly PhraseNote[] {
  return project.editedPhrases[part] ?? generatePhrase(project, part);
}

/** Generate a phrase from one independent track while keeping the generator library role-based. */
export function generateTrackPhrase(project: CompositionProject, track: CompositionTrack): PhraseNote[] {
  const trackProject = withTrackSeed(project, track);
  const editedPhrases = { ...trackProject.editedPhrases, [track.role]: undefined };
  let notes: PhraseNote[];
  switch (track.role) {
    case 'bass':
      notes = generatePhrase({ ...trackProject, bass: track.generatorSettings, editedPhrases }, 'bass'); break;
    case 'chords':
      notes = generatePhrase({ ...trackProject, chords: track.generatorSettings, editedPhrases }, 'chords'); break;
    case 'arpeggio':
      notes = generatePhrase({ ...trackProject, arpeggio: track.generatorSettings, editedPhrases }, 'arpeggio'); break;
    case 'drums':
      notes = generatePhrase({ ...trackProject, drums: track.generatorSettings, editedPhrases }, 'drums'); break;
  }
  const length = track.generatorSettings.noteLengthMultiplier ?? 100;
  const transpose = track.role === 'drums' ? 0 : track.generatorSettings.transpose ?? 0;
  return notes.map((note) => ({ ...note, durationBeats: note.durationBeats * length / 100,
    midi: Math.max(0, Math.min(127, note.midi + transpose)) }));
}

export function phraseForTrack(project: CompositionProject, track: CompositionTrack): readonly PhraseNote[] {
  return track.editedPhrase ?? generateTrackPhrase(project, track);
}

/** Add the legacy BassPart octave interval as a quieter upper octave. */
export function withBassOctaveInterval(notes: readonly PhraseNote[], enabled: boolean): PhraseNote[] {
  if (!enabled) return [...notes];
  return [
    ...notes,
    ...notes.filter((note) => note.midi <= 115).map((note) => ({
      ...note,
      id: `${note.id}-octave`,
      midi: note.midi + 12,
      velocity: Math.max(1, note.velocity - 15),
    })),
  ];
}

/** Repeat a phrase through arrangement sections where its part enters. */
export function layOutPhrase(
  project: CompositionProject,
  part: ArrangedPart,
  phrase: readonly PhraseNote[],
): PhraseNote[] {
  const result: PhraseNote[] = [];
  let arrangementBeat = 0;
  for (const section of project.arrangement) {
    const partEnters = project.tracks.some((track) => track.role === part && shouldGenerateTrackInSection(section, track));
    const hasEditedPhrase = project.editedPhrases[part] !== undefined;
    const progression = section.chordDegrees && !hasEditedPhrase ? section.chordDegrees : project.progression;
    const sectionPhrase = section.chordDegrees && !hasEditedPhrase
      ? generatePhrase({ ...project, progression }, part)
      : phrase;
    for (let measure = 0; measure < section.measures; measure++) {
      if (partEnters) {
        const sourceMeasure = measure % progression.length;
        const sourceStartBeat = sourceMeasure * 4;
        const sourceEndBeat = sourceStartBeat + 4;
        for (const event of sectionPhrase) {
          if (event.startBeat >= sourceStartBeat && event.startBeat < sourceEndBeat) {
            result.push({ ...event, startBeat: arrangementBeat + event.startBeat - sourceStartBeat });
          }
        }
      }
      arrangementBeat += 4;
    }
  }
  return part === 'bass' ? withBassOctaveInterval(result, project.bass.octaveInterval) : result;
}

/** Repeat a track phrase across sections using the same seeded section-entry rules as its role. */
export function layOutTrackPhrase(project: CompositionProject, track: CompositionTrack): PhraseNote[] {
  const phrase = phraseForTrack(project, track);
  const result: PhraseNote[] = [];
  let arrangementBeat = 0;
  for (const section of project.arrangement) {
    const partEnters = shouldGenerateTrackInSection(section, track);
    const effectiveTrack = resolvePartTrack(track, section);
    const progression = section.chordDegrees && track.editedPhrase === undefined
      ? section.chordDegrees : project.progression;
    const generated = track.editedPhrase === undefined
      ? generateTrackPhrase({ ...project, progression }, effectiveTrack) : phrase;
    const sectionPhrase = effectiveTrack.role === 'bass' && track.editedPhrase === undefined
      ? withBassOctaveInterval(generated, effectiveTrack.generatorSettings.octaveInterval) : generated;
    for (let measure = 0; measure < section.measures; measure++) {
      if (partEnters) {
        const sourceMeasure = measure % progression.length;
        const sourceStartBeat = sourceMeasure * 4;
        const sourceEndBeat = sourceStartBeat + 4;
        for (const note of sectionPhrase) {
          if (note.startBeat >= sourceStartBeat && note.startBeat < sourceEndBeat) {
            result.push({ ...note, startBeat: arrangementBeat + note.startBeat - sourceStartBeat });
          }
        }
      }
      arrangementBeat += 4;
    }
  }
  const notes = result;
  if (track.role === 'drums') return notes;
  const transpose = project.transposeSemitones ?? 0;
  return transpose === 0 ? notes : notes.map((note) => ({
    ...note,
    midi: Math.max(0, Math.min(127, note.midi + transpose)),
  }));
}

function withTrackSeed(project: CompositionProject, track: CompositionTrack): CompositionProject {
  if (track.id === `track-${track.role}-1`) return project;
  let hash = 0xcbf29ce484222325n;
  for (let index = 0; index < track.id.length; index++) {
    hash ^= BigInt(track.id.charCodeAt(index));
    hash = BigInt.asUintN(64, hash * 0x100000001b3n);
  }
  const seed = BigInt.asIntN(64, BigInt(project.seed) ^ hash).toString();
  return { ...project, seed };
}
