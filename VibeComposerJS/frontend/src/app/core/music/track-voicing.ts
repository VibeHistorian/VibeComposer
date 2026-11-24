import type { PhraseNote } from './phrase-note';

export const VOICE_MODES = ['poly', 'mono', 'legato'] as const;
export type VoiceMode = typeof VOICE_MODES[number];
export const MAX_GLIDE_MS = 2000;
export interface TrackVoicing { readonly voiceMode?: VoiceMode; readonly glideMs?: number; }

export function validTrackVoicing(value: TrackVoicing): boolean {
  return (value.voiceMode === undefined || VOICE_MODES.includes(value.voiceMode))
    && (value.glideMs === undefined || Number.isInteger(value.glideMs) && value.glideMs >= 0 && value.glideMs <= MAX_GLIDE_MS);
}

export interface VoicePitchChange {
  readonly time: number;
  readonly from: number;
  readonly to: number;
  readonly end: number;
}
export interface VoiceNote extends PhraseNote {
  readonly pitchChanges?: readonly VoicePitchChange[];
  readonly cutBeat?: number;
}

export function voicePitchAt(change: VoicePitchChange, time: number): number {
  if (time >= change.end) return change.to;
  if (time <= change.time) return change.from;
  return change.from + (change.to - change.from) * (time - change.time) / (change.end - change.time);
}

/** Last note wins, with the higher pitch winning simultaneous onsets. Releasing
 * the winner reveals a still-held note. Legato keeps the first voice in each overlap. */
export function planVoices<T extends PhraseNote>(notes: readonly T[], track: TrackVoicing,
  secondsAt: (beat: number) => number): readonly (T & VoiceNote)[] {
  if (!track.voiceMode || track.voiceMode === 'poly') return notes;
  const ordered = notes.filter(note => note.durationBeats > 0 && note.velocity > 0)
    .sort((a, b) => a.startBeat - b.startBeat || a.midi - b.midi);
  const events = ordered.flatMap((note, index) => [
    { beat: note.startBeat, index, on: true },
    { beat: note.startBeat + note.durationBeats, index, on: false },
  ]).sort((a, b) => a.beat - b.beat || Number(a.on) - Number(b.on) || a.index - b.index);
  const held = new Set<number>();
  const voices: (T & VoiceNote)[] = [];
  const glide = (track.glideMs ?? 0) / 1000;
  let winner: number | undefined, changes: VoicePitchChange[] = [];
  let lastPitch: number | undefined;
  let voice: T | undefined;
  const finish = (beat: number) => {
    if (!voice) return;
    voices.push({ ...voice, durationBeats: beat - voice.startBeat, pitchChanges: changes });
    voice = undefined;
  };
  for (let i = 0; i < events.length;) {
    const beat = events[i].beat;
    const time = secondsAt(beat);
    const previous = winner;
    while (i < events.length && events[i].beat === beat && !events[i].on) held.delete(events[i++].index);
    const overlaps = held.size > 0;
    while (i < events.length && events[i].beat === beat) held.add(events[i++].index);
    winner = undefined;
    for (const index of held) winner = index;
    if (previous === winner) continue;
    if (previous !== undefined) lastPitch = voicePitchAt(changes.at(-1)!, time);
    const join = track.voiceMode === 'legato' && voice && overlaps && winner !== undefined;
    if (!join) finish(beat);
    if (winner === undefined) continue;
    const note = ordered[winner];
    const from = glide > 0 && (join || track.voiceMode === 'mono') ? lastPitch ?? note.midi : note.midi;
    if (!join) {
      voice = { ...note, id: note.startBeat === beat ? note.id : `${note.id}@${beat}`, startBeat: beat };
      changes = [];
    }
    changes.push({ time, from, to: note.midi, end: from === note.midi ? time : time + glide });
  }
  return voices.map((voice, index) => ({ ...voice, cutBeat: voices[index + 1]?.startBeat }));
}

/** MIDI's time control is a normalized intent. Receivers choose their own time scale. */
export function midiVoicingControls(track: TrackVoicing): readonly (readonly [number, number])[] {
  const mode = track.voiceMode ?? 'poly', time = mode === 'poly' ? 0 : track.glideMs ?? 0;
  return [[mode === 'poly' ? 127 : 126, mode === 'poly' ? 0 : 1],
    [68, mode === 'legato' ? 127 : 0], [5, time ? Math.max(1, Math.round(time * 127 / MAX_GLIDE_MS)) : 0],
    [65, mode !== 'poly' && time > 0 ? 127 : 0]];
}
