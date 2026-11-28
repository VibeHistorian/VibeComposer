import { parseMidi, type MidiEvent } from 'midi-file';
import { midiFileStructure } from './midi-file-structure';
import type { NoteHarmony } from './midi-import-options';

export interface MidiPoint<T> { readonly tick: number; readonly value: T; }
interface ChannelPoint<T> extends MidiPoint<T> { readonly order: number; }
export interface MidiSourceNote {
  readonly tick: number;
  readonly endTick: number;
  readonly midi: number;
  readonly velocity: number;
}

export interface MidiSourceTrack {
  readonly index: number;
  readonly name: string;
  readonly instrumentName: string;
  readonly channel: number;
  readonly program: number;
  readonly volume: number;
  readonly pan: number;
  readonly notes: readonly MidiSourceNote[];
}

interface ChannelControls {
  readonly program: ChannelPoint<number>[];
  readonly volume: ChannelPoint<number>[];
  readonly pan: ChannelPoint<number>[];
  readonly pedal: ChannelPoint<boolean>[];
}

interface OpenNote {
  readonly tick: number;
  readonly order: number;
  readonly midi: number;
  readonly velocity: number;
  endTick?: number;
  endOrder?: number;
}
interface MidiChannel extends ChannelControls { readonly notes: OpenNote[]; }
interface PhysicalTrack {
  readonly index: number;
  readonly name: string;
  readonly instrumentName: string;
  readonly channels: ReadonlyMap<number, MidiChannel>;
  readonly endTick: number;
}

export interface MidiProjectSource {
  readonly ppq: number;
  readonly name: string;
  readonly endTick: number;
  readonly tracks: readonly MidiSourceTrack[];
  readonly tempos: MidiPoint<number>[];
  readonly meters: MidiPoint<number>[];
  readonly keys: MidiPoint<NoteHarmony>[];
  readonly markers: MidiPoint<string>[];
  readonly warnings: string[];
}

/** Return the last change at or before a tick. Changes at the same tick follow file order. */
export function midiPointIndex<T>(points: readonly (MidiPoint<T> & { readonly order?: number })[], tick: number, order = Infinity): number {
  let low = 0, high = points.length;
  while (low < high) {
    const middle = (low + high) >>> 1;
    const point = points[middle];
    if (point.tick < tick || point.tick === tick && (point.order ?? 0) <= order) low = middle + 1;
    else high = middle;
  }
  return low - 1;
}

function cleanText(text: string): string {
  // Standard MIDI text may be legacy single byte or UTF-8, depending on the writer.
  const bytes = Uint8Array.from(text.slice(0, 512), char => char.charCodeAt(0));
  const utf8 = new TextDecoder().decode(bytes);
  return (utf8.includes('\ufffd') ? text.slice(0, 512) : utf8).replace(/[\x00-\x1f\x7f]/g, ' ').trim();
}

// The parser uses ArrayLike.slice() for meta and system payloads. Reject invalid
// spans so a malformed event length cannot rewind or read past its track.
class CheckedMidiBytes extends Uint8Array {
  override slice(start = 0, end = this.length): Uint8Array<ArrayBuffer> {
    if (start < 0 || end < start || end > this.length) throw new RangeError('Invalid MIDI event length.');
    return super.slice(start, end);
  }
}

function eventsForTrack(bytes: Uint8Array<ArrayBuffer>, index: number): MidiEvent[] {
  try {
    return parseMidi(new CheckedMidiBytes(bytes.buffer, bytes.byteOffset, bytes.length)).tracks[0];
  } catch {
    throw new Error(`MIDI track ${index + 1} contains unreadable events.`);
  }
}

/** Keep physical track identity and MIDI channel together, with setup shared only when unambiguous. */
export function readMidiProjectSource(buffer: ArrayBuffer): MidiProjectSource {
  const { ppq, format, trackCount, singleTrackFiles } = midiFileStructure(buffer);
  if (format !== 0 && format !== 1) throw new Error('Use a format 0 or 1 MIDI file with a shared song timeline.');
  if (format === 0 && trackCount !== 1) throw new Error('A format 0 MIDI file must contain one track.');
  const tempos: MidiPoint<number>[] = [], meters: MidiPoint<number>[] = [];
  const keys: MidiPoint<NoteHarmony>[] = [], markers: MidiPoint<string>[] = [];
  const physicalTracks: PhysicalTrack[] = [];
  const warnings: string[] = [];
  let skippedAutomation = false, incompleteNotes = 0, invalidMetadata = false;
  for (const bytes of singleTrackFiles()) {
    const index = physicalTracks.length;
    const events = eventsForTrack(bytes, index);
    let tick = 0, name = '', instrumentName = '';
    const channels = new Map<number, MidiChannel>();
    const pending = new Map<number, { notes: OpenNote[]; next: number }>();
    for (const [order, event] of events.entries()) {
      if (!Number.isInteger(event.deltaTime) || event.deltaTime < 0 || event.deltaTime > 0x0fffffff) {
        throw new Error(`MIDI track ${index + 1} contains invalid event timing.`);
      }
      tick += event.deltaTime;
      if (event.type === 'endOfTrack') break;
      if ('channel' in event) {
        const channel = event.channel + 1;
        let part = channels.get(channel);
        if (!part) {
          part = { notes: [], program: [], volume: [], pan: [], pedal: [] };
          channels.set(channel, part);
        }
        if (event.type === 'noteOn' || event.type === 'noteOff') {
          const { noteNumber, velocity } = event;
          if (![noteNumber, velocity].every(value => Number.isInteger(value) && value >= 0 && value <= 127)) {
            incompleteNotes++;
            continue;
          }
          const key = channel * 128 + noteNumber;
          let active = pending.get(key);
          if (event.type === 'noteOn' && velocity > 0) {
            if (!active) { active = { notes: [], next: 0 }; pending.set(key, active); }
            const note = { tick, order, midi: noteNumber, velocity };
            active.notes.push(note);
            part.notes.push(note);
          } else if (active) {
            const note = active.notes[active.next++];
            note.endTick = tick;
            note.endOrder = order;
            if (active.next === active.notes.length) pending.delete(key);
          } else {
            incompleteNotes++;
          }
        } else if (event.type === 'programChange' && Number.isInteger(event.programNumber) && event.programNumber <= 127) {
          part.program.push({ tick, order, value: event.programNumber });
        } else if (event.type === 'controller' && Number.isInteger(event.value) && event.value <= 127) {
          const { controllerType, value } = event;
          if (controllerType === 7) part.volume.push({ tick, order, value });
          else if (controllerType === 10) part.pan.push({ tick, order, value });
          else if (controllerType === 64 && channel !== 10) {
            const down = value >= 64;
            if ((part.pedal.at(-1)?.value ?? false) !== down) part.pedal.push({ tick, order, value: down });
          } else if (!((controllerType === 0 || controllerType === 32) && value === 0) && !(controllerType === 11 && value === 127)) {
            skippedAutomation = true;
          }
        } else if (event.type !== 'pitchBend' || event.value !== 0) skippedAutomation = true;
      } else {
        switch (event.type) {
          case 'trackName': name ||= cleanText(event.text); break;
          case 'instrumentName': instrumentName ||= cleanText(event.text); break;
          case 'setTempo':
            if (event.microsecondsPerBeat > 0) tempos.push({ tick, value: 60_000_000 / event.microsecondsPerBeat });
            else invalidMetadata = true;
            break;
          case 'timeSignature':
            meters.push({ tick, value: event.numerator * 4 / event.denominator });
            break;
          case 'keySignature':
            if (event.key >= -7 && event.key <= 7 && (event.scale === 0 || event.scale === 1)) {
              keys.push({ tick, value: { tonic: (event.key * 7 + event.scale * 9 + 60) % 12,
                scale: event.scale ? 'natural-minor' : 'major' } });
            } else invalidMetadata = true;
            break;
          case 'marker': {
            const label = cleanText(event.text);
            if (label) markers.push({ tick, value: label });
            break;
          }
          case 'sysEx':
          case 'endSysEx': skippedAutomation = true; break;
          case 'portPrefix': skippedAutomation ||= event.port !== 0; break;
        }
      }
    }
    physicalTracks.push({ index, channels, name, instrumentName, endTick: tick });
  }

  const providers = Array.from({ length: 17 }, () => [] as MidiChannel[]);
  for (const track of physicalTracks) for (const [channel, data] of track.channels) providers[channel].push(data);
  const shared = providers.map(parts => ({
    program: uniqueControls(parts, 'program'), volume: uniqueControls(parts, 'volume'),
    pan: uniqueControls(parts, 'pan'), pedal: uniqueControls(parts, 'pedal'),
  }));
  const tracks: MidiSourceTrack[] = [];
  let endTick = 0, variedMix = false;
  for (const track of physicalTracks) for (const [channel, data] of track.channels) {
    const valueAtAttack = (key: 'program' | 'volume' | 'pan', note: OpenNote, fallback: number) => {
      const own = data[key];
      const points = own.length ? own : shared[channel][key];
      return points[midiPointIndex(points, note.tick, own.length ? note.order : undefined)]?.value ?? fallback;
    };
    const pedal = data.pedal.length ? data.pedal : shared[channel].pedal;
    const patches = new Map<number, { volume: number; pan: number; notes: MidiSourceNote[] }>();
    for (const note of data.notes) {
      if (note.endTick === undefined || note.endTick <= note.tick) { incompleteNotes++; continue; }
      const pedalIndex = midiPointIndex(pedal, note.endTick, data.pedal.length ? note.endOrder : undefined);
      const release = pedal[pedalIndex]?.value ? pedal[pedalIndex + 1]?.tick ?? track.endTick : note.endTick;
      const program = channel === 10 ? 0 : valueAtAttack('program', note, 0);
      const volume = valueAtAttack('volume', note, 127);
      const pan = valueAtAttack('pan', note, 64);
      const end = Math.max(note.endTick, release);
      let patch = patches.get(program);
      if (!patch) {
        patch = { volume, pan, notes: [] };
        patches.set(program, patch);
      } else if (patch.pan !== pan || patch.volume !== volume) {
        variedMix = true;
        // A channel that starts silent may later contain the performance we want to keep.
        patch.volume ||= volume;
      }
      patch.notes.push({ tick: note.tick, midi: note.midi, velocity: note.velocity, endTick: end });
      endTick = Math.max(endTick, end, track.endTick);
    }
    const first = data.notes[0];
    if (first && patches.size) for (const [key, fallback] of [['volume', 127], ['pan', 64]] as const) {
      const initial = valueAtAttack(key, first, fallback);
      const points = data[key].length ? data[key] : shared[channel][key];
      variedMix ||= points.some(point => point.tick > first.tick && point.tick < track.endTick && point.value !== initial);
    }
    for (const [program, patch] of patches) {
      tracks.push({ index: track.index, name: track.name, instrumentName: track.instrumentName, channel, program, ...patch });
    }
  }
  if (!tracks.length) throw new Error('The MIDI file contains no playable notes.');
  if (incompleteNotes) warnings.push(`Skipped ${incompleteNotes} unmatched, invalid or zero-length note events.`);
  if (invalidMetadata) warnings.push('Skipped invalid tempo or key settings.');
  if (variedMix) warnings.push('Volume and pan changes were reduced to track settings.');
  if (skippedAutomation) warnings.push('Some MIDI automation or bank settings were skipped.');
  const first = physicalTracks[0];
  const name = tracks.some(track => track.index === 0) || /^(conductor|tempo( track| map)?|untitled)$/i.test(first.name) ? '' : first.name;
  const byTick = (a: MidiPoint<unknown>, b: MidiPoint<unknown>) => a.tick - b.tick;
  return { ppq, name, endTick, tracks, warnings, tempos: tempos.sort(byTick), meters: meters.sort(byTick),
    keys: keys.sort(byTick), markers: markers.sort(byTick) };
}

function uniqueControls<K extends keyof ChannelControls>(providers: readonly MidiChannel[], key: K): ChannelControls[K] {
  const explicit = providers.filter(channel => channel[key].length);
  return explicit.length === 1 ? explicit[0][key] : [] as ChannelControls[K];
}
