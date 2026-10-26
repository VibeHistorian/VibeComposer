import { type ArrangedPart, ARRANGED_PARTS } from '../music/part-role';
import type { CompositionTrack, MixChannelSettings } from './track.model';
import type { PhraseNote } from '../music/phrase-note';
import { normalizeVolumePercent, validVolumePercent } from '../audio/mix-values';
import { INSTRUMENT_POOLS } from '../music/instrument-generation';
import { decodeMelody, decodeBass, decodeChords, decodeArpeggio, decodeDrums } from './project-part-decoder';

export function decodeTracks(value: unknown): CompositionTrack[] | undefined {
  if (!Array.isArray(value) || value.length > 64) return undefined;
  const tracks: CompositionTrack[] = [];
  const ids = new Set<string>();
  for (const raw of value) {
    if (!raw || typeof raw !== 'object') return undefined;
    const track = raw as Partial<CompositionTrack> & { generatorSettings?: unknown; mix?: unknown; editedPhrase?: unknown };
    if (typeof track.id !== 'string' || !/^[a-zA-Z0-9_-]{1,64}$/.test(track.id) || ids.has(track.id)
        || typeof track.name !== 'string' || !track.name.trim() || track.name.length > 48
        || !Number.isInteger(track.midiChannel) || (track.midiChannel ?? 0) < 1 || (track.midiChannel ?? 17) > 16) return undefined;
    const mix = decodeTrackMix(track.mix);
    if (track.orderOffset !== undefined && (!Number.isInteger(track.orderOffset)
      || track.orderOffset < -2147483648 || track.orderOffset > 2147483647)) return undefined;
    const editedPhrase = track.editedPhrase === undefined ? undefined
      : isPhrase(track.editedPhrase) ? track.editedPhrase.map((note) => ({ ...note })) : null;
    if (!mix || editedPhrase === null || (track.rerollLocked !== undefined && typeof track.rerollLocked !== 'boolean')
      || (track.instrumentLocked !== undefined && typeof track.instrumentLocked !== 'boolean')) return undefined;
    if (track.instrumentPool !== undefined && (typeof track.instrumentPool !== 'string' || track.role === 'drums' || track.instrumentPool === 'DRUM'
      || !Object.hasOwn(INSTRUMENT_POOLS, track.instrumentPool)
      || !INSTRUMENT_POOLS[track.instrumentPool]?.includes(mix.program))) return undefined;
    let decoded: CompositionTrack | undefined;
    switch (track.role) {
      case 'melody': {
        const generatorSettings = decodeMelody(track.generatorSettings);
        if (generatorSettings) decoded = { id: track.id, role: 'melody', name: track.name.trim(), midiChannel: track.midiChannel!, mix, generatorSettings, editedPhrase };
        break;
      }
      case 'bass': {
        const generatorSettings = decodeBass(track.generatorSettings);
        if (generatorSettings) decoded = { id: track.id, role: 'bass', name: track.name.trim(), midiChannel: track.midiChannel!, mix, generatorSettings, editedPhrase };
        break;
      }
      case 'chords': {
        const generatorSettings = decodeChords(track.generatorSettings);
        if (generatorSettings) decoded = { id: track.id, role: 'chords', name: track.name.trim(), midiChannel: track.midiChannel!, mix, generatorSettings, editedPhrase };
        break;
      }
      case 'arpeggio': {
        const generatorSettings = decodeArpeggio(track.generatorSettings);
        if (generatorSettings) decoded = { id: track.id, role: 'arpeggio', name: track.name.trim(), midiChannel: track.midiChannel!, mix, generatorSettings, editedPhrase };
        break;
      }
      case 'drums': {
        const generatorSettings = decodeDrums(track.generatorSettings);
        if (generatorSettings && track.midiChannel === 10) decoded = { id: track.id, role: 'drums', name: track.name.trim(), midiChannel: 10, mix, generatorSettings, editedPhrase };
        break;
      }
    }
    if (!decoded) return undefined;
    if (track.orderOffset !== undefined) decoded = { ...decoded, orderOffset: track.orderOffset };
    if (track.rerollLocked !== undefined) decoded = { ...decoded, rerollLocked: track.rerollLocked };
    if (track.instrumentLocked !== undefined) decoded = { ...decoded, instrumentLocked: track.instrumentLocked };
    if (track.instrumentPool !== undefined) decoded = { ...decoded, instrumentPool: track.instrumentPool };
    ids.add(decoded.id);
    tracks.push(decoded);
  }
  return tracks;
}

function decodeTrackMix(value: unknown): MixChannelSettings | undefined {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return undefined;
  const settings = value as Partial<MixChannelSettings>;
  if (!Number.isInteger(settings.program) || (settings.program ?? -1) < 0 || (settings.program ?? 128) > 127
      || !validVolumePercent(settings.volumePercent)
      || !Number.isInteger(settings.panPercent) || (settings.panPercent ?? -101) < -100 || (settings.panPercent ?? 101) > 100
      || typeof settings.muted !== 'boolean' || typeof settings.solo !== 'boolean') return undefined;
  return { program: settings.program!, volumePercent: normalizeVolumePercent(settings.volumePercent!), panPercent: settings.panPercent!, muted: settings.muted, solo: settings.solo };
}

export function decodeEditedPhrases(value: unknown): Partial<Record<ArrangedPart, PhraseNote[]>> | undefined {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return undefined;
  }
  const phrases = value as Partial<Record<ArrangedPart, unknown>>;
  if (Object.keys(phrases).some((part) => !ARRANGED_PARTS.includes(part as ArrangedPart))) {
    return undefined;
  }
  const decoded: Partial<Record<ArrangedPart, PhraseNote[]>> = {};
  for (const part of ARRANGED_PARTS) {
    const notes = phrases[part];
    if (notes === undefined) {
      continue;
    }
    if (!Array.isArray(notes) || !isPhrase(notes)) {
      return undefined;
    }
    decoded[part] = notes.map((note) => ({ ...note }));
  }
  return decoded;
}

export function isPhrase(value: unknown, maximumBeat = 132, minimumBeat = -4): value is readonly PhraseNote[] {
  if (!Array.isArray(value) || value.length > 2048) {
    return false;
  }
  const ids = new Set<string>();
  for (const candidate of value) {
    if (!candidate || typeof candidate !== 'object') {
      return false;
    }
    const note = candidate as Partial<PhraseNote>;
    if (typeof note.id !== 'string' || note.id.length < 1 || note.id.length > 120 || ids.has(note.id)
        || !Number.isInteger(note.midi) || (note.midi ?? -1) < 0 || (note.midi ?? 128) > 127
        || typeof note.startBeat !== 'number' || !Number.isFinite(note.startBeat)
        || note.startBeat < minimumBeat || note.startBeat >= maximumBeat
        || typeof note.durationBeats !== 'number' || !Number.isFinite(note.durationBeats)
        || note.durationBeats <= 0 || note.durationBeats > maximumBeat - note.startBeat
        || !Number.isInteger(note.velocity) || (note.velocity ?? 0) < 1 || (note.velocity ?? 128) > 127) {
      return false;
    }
    ids.add(note.id);
  }
  return true;
}
