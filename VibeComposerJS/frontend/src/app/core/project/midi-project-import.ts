import { isDrumPitch } from '../music/drum-instruments';
import { GM_INSTRUMENTS } from '../music/gm-instruments';
import { readMidiProjectSource, type MidiSourceNote, type MidiSourceTrack } from '../music/midi-project-source';
import type { ArrangedPart } from '../music/part-role';
import type { PhraseNote } from '../music/phrase-note';
import type { ArrangementSection } from './arrangement.model';
import { planMidiArrangement } from './midi-project-arrangement';
import { inferMidiHarmony } from './midi-project-harmony';
import { DEFAULT_PROJECT, DEFAULT_TRACKS } from './project-defaults';
import { reconcileDrumKit } from './project-drum-kit';
import { MAX_LOCAL_PHRASE_END_BEAT, MAX_PHRASE_NOTES } from './project-limits';
import type { CompositionProject } from './project.model';
import type { CompositionTrack } from './track.model';

export interface MidiProjectImport {
  readonly project: CompositionProject;
  readonly warnings: readonly string[];
}

/** The analysis supplies editable roles and harmony. The MIDI performance lives in local phrases. */
export function importMidiProject(buffer: ArrayBuffer, filename: string): MidiProjectImport {
  const source = readMidiProjectSource(buffer);
  const { ppq, warnings } = source;
  const { tracks, sections, endTick } = planMidiArrangement(source);
  const starts = sections.map(section => section.boundaries[0]);
  let phraseOverflow = false, longTails = false;
  const parts = tracks.flatMap(track => {
    const local = sections.map(() => [] as MidiSourceNote[]);
    let section = 0;
    for (const note of track.notes) {
      if (note.tick >= endTick) break;
      while (note.tick >= starts[section + 1]) section++;
      if (local[section].length === MAX_PHRASE_NOTES) { phraseOverflow = true; continue; }
      const localMax = starts[section] + MAX_LOCAL_PHRASE_END_BEAT * ppq;
      longTails ||= localMax < Math.min(note.endTick, endTick);
      const end = Math.min(note.endTick, endTick, localMax);
      local[section].push(note.endTick === end ? note : { ...note, endTick: end });
    }
    const notes = local.flat();
    return notes.length ? [{ source: track, role: inferRole(track, notes, ppq), notes, local }] : [];
  });
  if (!parts.length) throw new Error('There are no playable notes within the project’s arrangement limits.');
  if (phraseOverflow) warnings.push(`Some dense phrases were limited to ${MAX_PHRASE_NOTES} notes per track and section.`);
  if (longTails) warnings.push('Some unusually long notes were shortened to fit project phrases.');

  const measureStarts = sections.flatMap(section => section.boundaries.slice(0, -1));
  const harmony = inferMidiHarmony(parts, [...measureStarts, endTick], source.keys, ppq, warnings);
  const sourceChannels = new Set(parts.map(part => part.source.channel));
  const freeChannels = Array.from({ length: 16 }, (_, index) => index + 1)
    .filter(channel => channel !== 10 && !sourceChannels.has(channel));
  const routing = new Map<number, Map<string, number>>();
  const roleCounts = new Map<ArrangedPart, number>();
  let crowdedChannels = false;
  const compositionTracks: CompositionTrack[] = parts.map(part => {
    const { source: track, role } = part;
    const number = (roleCounts.get(role) ?? 0) + 1;
    roleCounts.set(role, number);
    const template = DEFAULT_TRACKS.find(candidate => candidate.role === role)!;
    let midiChannel = track.channel;
    if (role !== 'drums') {
      const settings = `${track.program}/${track.volume}/${track.pan}`;
      let mixes = routing.get(track.channel);
      if (!mixes) { mixes = new Map(); routing.set(track.channel, mixes); }
      const assigned = mixes.get(settings);
      if (assigned !== undefined) midiChannel = assigned;
      else {
        if (mixes.size) {
          midiChannel = freeChannels.shift() ?? track.channel;
          crowdedChannels ||= midiChannel === track.channel;
        }
        mixes.set(settings, midiChannel);
      }
    }
    const siblings = parts.filter(other => other.source.index === track.index);
    const patchName = role === 'drums' ? 'Drums' : GM_INSTRUMENTS[track.program].name;
    const base = track.name || track.instrumentName || patchName;
    const sameChannel = siblings.filter(other => other.source.channel === track.channel).length;
    const suffix = sameChannel > 1 && base !== patchName ? ` · ${patchName}` : siblings.length > 1 ? ` · Ch ${track.channel}` : '';
    const name = role === 'drums' ? base.slice(0, 48).trimEnd() : base.slice(0, 48 - suffix.length).trimEnd() + suffix;
    const mix = { ...template.mix, program: track.program, volumePercent: track.volume * 100 / 127,
      panPercent: track.pan === 64 ? 0 : Math.round(track.pan * 200 / 127 - 100) };
    const id = `track-${role}-${number}`;
    if (template.role === 'drums') {
      const pitch = isDrumPitch(track.drumPitch) ? track.drumPitch : template.generatorSettings.pitch;
      return { ...template, id, name, midiChannel, mix, drumPan: 'fixed', drumSlotPitch: track.drumSlotPitch,
        generatorSettings: { ...template.generatorSettings, pitch } };
    }
    return { ...template, id, name, midiChannel, mix };
  });
  if (crowdedChannels) warnings.push('Some instruments share MIDI output channels.');

  const firstBpm = sections[0].bpm;
  const tempoBpm = Math.min(240, firstBpm);
  const tails = parts.map(() => 0);
  let measureOffset = 0, noteNumber = 0;
  const arrangement: ArrangementSection[] = sections.map(({ boundaries, type, bpm }, sectionIndex) => {
    const start = boundaries[0];
    const durations = boundaries.slice(1).map((tick, index) => (tick - boundaries[index]) / ppq);
    const chords = harmony.chords.slice(measureOffset, measureOffset + durations.length);
    measureOffset += durations.length;
    const trackPresence: Record<string, boolean> = {};
    const trackPhrases: Record<string, PhraseNote[]> = {};
    parts.forEach(({ role, local }, index) => {
      const { id } = compositionTracks[index];
      const notes = local[sectionIndex];
      trackPresence[id] = notes.length > 0 || tails[index] > start;
      trackPhrases[id] = notes.map(note => {
        tails[index] = Math.max(tails[index], note.endTick);
        const startBeat = (note.tick - start) / ppq;
        const durationBeats = Math.min((note.endTick - note.tick) / ppq, MAX_LOCAL_PHRASE_END_BEAT - startBeat);
        return { id: `midi-note-${++noteNumber}`, midi: role === 'drums' ? note.midi : note.midi - harmony.transpose,
          startBeat, durationBeats, velocity: note.velocity };
      });
    });
    return {
      id: `section-${sectionIndex + 1}`, type, measures: durations.length, trackPresence, trackPhrases,
      chordDegrees: chords.map(chord => chord.degree),
      ...(chords.some(chord => chord.custom) ? { customChords: chords.map(chord => chord.custom) } : {}),
      ...(durations.some(duration => duration !== 4) ? { customDurations: durations } : {}),
      ...(bpm !== tempoBpm ? { context: { tempoBpm: bpm } } : {}),
    };
  });
  const primary = arrangement.find(section => compositionTracks.some(track =>
    track.role !== 'drums' && section.trackPresence[track.id])) ?? arrangement[0];
  const name = (source.name || filename.replace(/\.(mid|midi)$/i, '').trim() || 'Imported MIDI').slice(0, 80);
  const project: CompositionProject = { ...DEFAULT_PROJECT, name, tempoBpm, arrangementManual: true, arrangement,
    tracks: compositionTracks, scale: harmony.scale, transposeSemitones: harmony.transpose,
    progression: primary.chordDegrees!, progressionChords: primary.customChords, drumSlots: undefined };
  return { project: reconcileDrumKit(project)!, warnings };
}

function inferRole(track: MidiSourceTrack, notes: readonly MidiSourceNote[], ppq: number): ArrangedPart {
  if (track.channel === 10) return 'drums';
  const label = `${track.name} ${track.instrumentName}`.replace(/[_-]/g, ' ');
  if (/\b(arp|arps|arpeggio|arpeggiator)\b/i.test(label)) return 'arpeggio';
  if (/\b(bass|subbass|contrabass)\b/i.test(label) || track.program >= 32 && track.program <= 39) return 'bass';
  if (/\b(melody|lead|solo|vocals?)\b/i.test(label)) return 'melody';
  if (/\b(chords?|pads?|harmony|accomp(?:animent)?)\b/i.test(label) || track.program >= 88 && track.program <= 95) return 'chords';

  let overlap = 0, latestEnd = -Infinity, pitchSum = 0, shortSteps = 0;
  for (const [index, note] of notes.entries()) {
    if (note.tick < latestEnd) overlap++;
    latestEnd = Math.max(note.endTick, latestEnd);
    pitchSum += note.midi;
    const gap = note.tick - (notes[index - 1]?.tick ?? -Infinity);
    if (gap > 0 && gap <= ppq * 0.55) shortSteps++;
  }
  if (overlap && overlap / notes.length >= 0.25) return 'chords';
  if (notes.length >= 3 && pitchSum / notes.length <= 47) return 'bass';
  if (notes.length >= 8 && shortSteps >= (notes.length - 1) * 0.75) {
    for (const period of [3, 4, 6, 8]) {
      if (notes.length < period * 2 || new Set(notes.slice(0, period).map(note => note.midi)).size < 3) continue;
      let repeats = 0;
      for (let index = period; index < notes.length; index++) if (notes[index].midi === notes[index - period].midi) repeats++;
      if (repeats >= (notes.length - period) * 0.75) return 'arpeggio';
    }
  }
  return 'melody';
}
