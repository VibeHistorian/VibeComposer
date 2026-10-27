import type { PhraseNote } from '../music/phrase-note';
import type { PhraseRangeEdit } from '../music/phrase-range-edit';
import type { InstrumentPool } from '../music/generation-policy.model';
import type { MelodySettings, BassSettings, ChordSettings, ArpeggioSettings, DrumSettings } from '../music/part-settings.model';

export interface MixChannelSettings {
  /** General MIDI program number, ignored by the percussion channel. */
  readonly program: number;
  /** Linear gain percentage: 100 is unity; maximum is +6 dB (about 199.526%). */
  readonly volumePercent: number;
  /** Stereo position from -100 (left) to 100 (right). */
  readonly panPercent: number;
  readonly muted: boolean;
  readonly solo: boolean;
}

interface TrackBase {
  readonly id: string;
  readonly name: string;
  /** Saved Java part-order offset; independent of track ID, display order and pattern seed. */
  readonly orderOffset?: number;
  /** MIDI channels are stored one-based to match the channel numbers shown in the UI. */
  readonly midiChannel: number;
  readonly mix: MixChannelSettings;
  readonly editedPhrase?: readonly PhraseNote[];
  /** Track-owned replacements layered over each section's resolved phrase. */
  readonly phraseEdits?: readonly PhraseRangeEdit[];
  readonly rerollLocked?: boolean;
  readonly instrumentLocked?: boolean;
  readonly instrumentPool?: Exclude<InstrumentPool, 'AUTO'>;
}

export type CompositionTrack =
  | (TrackBase & { readonly role: 'melody'; readonly generatorSettings: MelodySettings })
  | (TrackBase & { readonly role: 'bass'; readonly generatorSettings: BassSettings })
  | (TrackBase & { readonly role: 'chords'; readonly generatorSettings: ChordSettings })
  | (TrackBase & { readonly role: 'arpeggio'; readonly generatorSettings: ArpeggioSettings })
  | (TrackBase & { readonly role: 'drums'; readonly generatorSettings: DrumSettings });
