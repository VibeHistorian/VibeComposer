import { resolveProjectTimeline } from './project-timeline';
import { resolvedDrumSlots } from '../project/project-drum-kit';
import type { DrumSlots } from './drum-instruments';
import { DEFAULT_MIDI_PPQ, validMidiPpq } from './midi-resolution';
import { Midi } from '@tonejs/midi';
import { drumChannelPan, midiPanValue, mixEnabled, withDrumChannelPan, withMasterVolume } from '../audio/mix-values';
import type { CompositionProject } from '../project/project.model';
import type { CompositionTrack } from '../project/track.model';
import type { PhraseNote } from './phrase-note';
import { ARRANGED_PARTS, tracksInRoleOrder } from './part-role';
import { mapDrumPitch, type DrumMapping } from './drum-instruments';
import { parseMidi, writeMidi, type MidiEvent } from 'midi-file';
import { GLIDE_LANES, glideNeutral, glidesByChannel, type PhraseGlides } from './phrase-glides';
import { MIDI_EXPRESSION_CC, midiGlideEvents, midiGlideValue, PITCH_BEND_SENSITIVITY } from './midi-glides';

/** Export the current editor draft in phrase-relative beats, regardless of mute/solo state.
 * Prepend pickup time and preserve tails. Notes already contain their sounding pitches. */
export function generatePhraseMidi(name: string, tempoBpm: number,
  tracks: readonly { track: CompositionTrack; notes: readonly PhraseNote[]; glides?: PhraseGlides }[],
  _phraseBeats: number, drumMapping: DrumMapping = 'general-midi', ppq = DEFAULT_MIDI_PPQ, drumSlots?: DrumSlots): Uint8Array {
  const midi = new Midi();
  if (!validMidiPpq(ppq)) throw new Error('Unsupported MIDI PPQ');
  midi.header.fromJSON({ ...midi.header.toJSON(), ppq });
  const glides = glidesByChannel(tracks.map(({ track }) => track),
    Object.fromEntries(tracks.flatMap(({ track, glides }) => glides ? [[track.id, glides] as const] : [])));
  const offset = -earliestGlideBeat(glides, tracks.reduce((start, { notes }) =>
    notes.reduce((minimum, note) => Math.min(minimum, note.startBeat), start), 0));
  midi.name = name;
  midi.header.setTempo(tempoBpm);
  const channelTracks = new Map<number, number>();
  const kitPan = drumChannelPan(tracks.map(({ track }) => track));
  let wroteKitPan = false;
  for (const { track: source, notes } of tracks) {
    const track = midi.addTrack();
    if (!channelTracks.has(source.midiChannel)) channelTracks.set(source.midiChannel, midi.tracks.length);
    track.name = source.name;
    track.channel = source.midiChannel - 1;
    const sharedPan = source.role === 'drums' && kitPan !== undefined;
    configureMix(track, withDrumChannelPan(source, kitPan), false, !sharedPan || !wroteKitPan);
    if (sharedPan) wroteKitPan = true;
    for (const note of notes) {
      const start = note.startBeat + offset;
      const end = start + note.durationBeats;
      if (end <= start) continue;
      track.addNote({
        midi: source.role === 'drums' ? mapDrumPitch(note.midi, drumMapping, drumSlots) : note.midi,
        ticks: Math.round(start * midi.header.ppq),
        durationTicks: Math.max(1, Math.round(end * midi.header.ppq) - Math.round(start * midi.header.ppq)),
        velocity: note.velocity / 127,
      });
    }
  }
  return writeGlides(midi, channelTracks, glides, offset, beat => beat * 60 / tempoBpm, seconds => seconds * tempoBpm / 60);
}

/** Serialize every independent instrument track with its assigned channel and mix controls. */
export function generateCompositionMidi(project: CompositionProject, drumMapping: DrumMapping = 'general-midi', ppq = DEFAULT_MIDI_PPQ): Uint8Array {
  const drumSlots = resolvedDrumSlots(project);
  const midi = new Midi();
  if (!validMidiPpq(ppq)) throw new Error('Unsupported MIDI PPQ');
  midi.header.fromJSON({ ...midi.header.toJSON(), ppq });
  midi.name = project.name;
  const timeline = resolveProjectTimeline(project);
  const phrases = timeline.notesByTrack;
  const glides = glidesByChannel(project.tracks, timeline.glidesByTrack);
  const offset = -earliestGlideBeat(glides, timeline.soundingBounds.startBeat);
  midi.header.tempos = timeline.tempoMap.map(point => ({ ticks: (point.beat + offset) * midi.header.ppq, bpm: point.bpm }));
  if (offset > 0) midi.header.tempos.unshift({ ticks: 0, bpm: midi.header.tempos[0].bpm });
  midi.header.update();
  const anySolo = project.tracks.some((track) => track.mix.solo);
  const kitPan = drumChannelPan(project.tracks);
  const channelTracks = new Map<number, number>();
  let wroteKitPan = false;

  for (const role of ARRANGED_PARTS) {
    const members = tracksInRoleOrder(project.tracks).filter(track => track.role === role);
    for (const compositionTrack of members) {
      const track = midi.addTrack();
      if (!channelTracks.has(compositionTrack.midiChannel)) channelTracks.set(compositionTrack.midiChannel, midi.tracks.length);
      track.name = compositionTrack.name;
      track.channel = compositionTrack.midiChannel - 1;
      const sharedPan = role === 'drums' && kitPan !== undefined;
      const included = configureMix(track, withDrumChannelPan(withMasterVolume(compositionTrack, project.masterVolumePercent), kitPan),
        anySolo, !sharedPan || !wroteKitPan);
      if (sharedPan) wroteKitPan = true;
      if (!included) continue;
      for (const note of phrases[compositionTrack.id]) {
        track.addNote({
          midi: compositionTrack.role === 'drums' ? mapDrumPitch(note.midi, drumMapping, drumSlots) : note.midi,
          ticks: Math.round((note.startBeat + offset) * midi.header.ppq),
          durationTicks: Math.max(1, Math.round(note.durationBeats * midi.header.ppq)),
          velocity: note.velocity / 127,
        });
      }
    }
    for (let index = members.length; index < (project.midiPadding?.[role] ?? 0); index++) {
      const filler = midi.addTrack();
      filler.name = `${role} padding ${index + 1}`;
      filler.channel = role === 'drums' ? 9 : 0;
    }
  }
  return writeGlides(midi, channelTracks, glides, offset, timeline.beatToSeconds, timeline.secondsToBeat);
}

function earliestGlideBeat(channels: ReadonlyMap<number, PhraseGlides>, original: number): number {
  for (const curves of channels.values()) {
    original = Math.min(original, curves.pitch[0]?.startBeat ?? Infinity, curves.volume[0]?.startBeat ?? Infinity);
  }
  return original;
}

/** Tone's MIDI writer groups CCs by number. Merge the automation after serialization
 * so RPN selection/data/nulling and the first note at a controller change stay in order. */
function writeGlides(midi: Midi, channelTracks: ReadonlyMap<number, number>, glides: ReadonlyMap<number, PhraseGlides>, offset: number,
  secondsAt: (beat: number) => number, beatAt: (seconds: number) => number): Uint8Array {
  const bytes = midi.toArray();
  if (!glides.size) return bytes;
  const file = parseMidi(bytes);
  for (const [channelNumber, curves] of glides) {
    const channel = channelNumber - 1;
    const index = channelTracks.get(channelNumber);
    if (index === undefined) continue;
    const events: { tick: number; priority: number; event: MidiEvent }[] = [];
    const cc = (tick: number, controllerType: number, value: number) => events.push({
      tick, priority: 1, event: { type: 'controller', deltaTime: 0, channel, controllerType, value },
    });
    if (curves.pitch.length) for (const [controller, value] of PITCH_BEND_SENSITIVITY) cc(0, controller, value);
    for (const lane of GLIDE_LANES) {
      const segments = curves[lane];
      if (!segments.length) continue;
      const end = segments.at(-1)!.endBeat;
      const tickAt = (beat: number) => Math.round((beat + offset) * midi.header.ppq);
      const neutral = midiGlideValue(lane, glideNeutral(lane));
      const values = new Map<number, number>([[0, neutral]]);
      midiGlideEvents(lane, segments, segments[0].startBeat, end, secondsAt, beatAt,
        (seconds, value) => values.set(tickAt(beatAt(seconds)), value));
      values.set(tickAt(end), neutral);
      for (const [tick, value] of values) {
        if (lane === 'volume') cc(tick, MIDI_EXPRESSION_CC, value);
        else events.push({ tick, priority: 1, event: { type: 'pitchBend', deltaTime: 0, channel, value } });
      }
    }
    let tick = 0;
    for (const event of file.tracks[index]) {
      tick += event.deltaTime;
      if (event.type === 'endOfTrack') continue;
      const priority = event.type === 'noteOn' || event.type === 'noteOff' ? 2 : 0;
      events.push({ tick, priority, event });
    }
    events.sort((left, right) => left.tick - right.tick || left.priority - right.priority);
    let previous = 0;
    file.tracks[index] = events.map(({ tick, event }) => {
      const deltaTime = tick - previous;
      previous = tick;
      return { ...event, deltaTime };
    });
    file.tracks[index].push({ type: 'endOfTrack', deltaTime: Math.max(0, tick - previous), meta: true });
  }
  return new Uint8Array(writeMidi(file));
}

function configureMix(track: ReturnType<Midi['addTrack']>, compositionTrack: CompositionTrack, anySolo: boolean, writePan: boolean): boolean {
  const { mix } = compositionTrack;
  if (compositionTrack.role !== 'drums') track.instrument.number = mix.program;
  track.addCC({ number: 7, time: 0, value: Math.max(0, Math.min(1, mix.volumePercent / 100)) });
  if (writePan) track.addCC({ number: 10, time: 0, value: midiPanValue(mix.panPercent) / 127 });
  return mixEnabled(mix, anySolo);
}
