import { computed, inject, Injectable } from '@angular/core';
import { decodeTrackPresets, type TrackPreset } from '../project/track-presets';
import { SettingsStorage, TRACK_PRESETS_KEY } from './settings-storage';

@Injectable({ providedIn: 'root' })
export class TrackPresetStorage {
  private readonly storage = inject(SettingsStorage);
  readonly library = computed(() => this.decode(this.storage.read(TRACK_PRESETS_KEY)));

  update(change: (presets: TrackPreset[]) => TrackPreset[]): Promise<void> {
    return this.storage.updatePresets(value => change(this.decode(value) ?? []));
  }

  private decode(value: unknown): TrackPreset[] | undefined {
    return value === undefined ? [] : decodeTrackPresets(value);
  }
}
