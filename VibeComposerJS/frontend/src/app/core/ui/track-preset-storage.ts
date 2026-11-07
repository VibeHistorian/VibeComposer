import { decodeTrackPresets, type TrackPreset } from '../project/track-presets';

export const TRACK_PRESET_STORAGE_KEY = 'vibecomposer.track-presets';

export function readTrackPresetLibrary(): TrackPreset[] | undefined {
  try {
    const saved = localStorage.getItem(TRACK_PRESET_STORAGE_KEY);
    return saved ? decodeTrackPresets(JSON.parse(saved)) : [];
  } catch { return undefined; }
}
