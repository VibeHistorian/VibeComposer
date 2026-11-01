import { DEFAULT_MIDI_PPQ } from '../music/midi-resolution';
export function musicalTime(beats: number, difference = false, ppq = DEFAULT_MIDI_PPQ): string {
  const ticks = Math.round((difference ? Math.abs(beats) : beats) * ppq);
  const whole = Math.floor(ticks / ppq);
  const bar = Math.floor(whole / 4) + (difference ? 0 : 1);
  const beat = ((whole % 4 + 4) % 4) + (difference ? 0 : 1);
  return (difference ? (beats < 0 ? '-' : '+') : '') + String(bar).padStart(2, '0') + ':' + beat + '.' + String((ticks % ppq + ppq) % ppq).padStart(3, '0');
}
export function elapsedTime(seconds: number): string {
  const ms = Math.max(0, Math.round(seconds * 1000));
  return String(Math.floor(ms / 60000)).padStart(2, '0') + ':' + String(Math.floor(ms / 1000) % 60).padStart(2, '0') + '.' + String(ms % 1000).padStart(3, '0');
}
