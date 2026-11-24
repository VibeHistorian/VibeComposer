import type { InstrumentEngine, InstrumentVoice } from './instrument-engine';
import { voicePitchAt, type VoiceNote } from '../music/track-voicing';

export function scheduleVoice(engine: InstrumentEngine, destination: AudioNode, program: number, percussion: boolean,
  note: VoiceNote, start: number, duration: number, songTime: number, cutTime: number, channelPitch?: AudioNode): InstrumentVoice[] {
  const changes = note.pitchChanges;
  if (!changes) return engine.schedule(destination, program, percussion, note.midi, note.velocity, start, duration, channelPitch);

  const context = engine.context;
  const end = songTime + duration;
  const cents = (pitch: number) => (pitch - note.midi) * 100;
  const gain = context.createGain();
  gain.connect(destination);
  const pitch = context.createConstantSource();
  const startIndex = changes.reduce((last, change, index) => change.time <= songTime ? index : last, 0);
  for (let index = startIndex; index < changes.length; index++) {
    const change = changes[index];
    if (change.time >= end) break;
    const begin = Math.max(change.time, songTime);
    const stop = Math.min(end, change.end, changes[index + 1]?.time ?? Infinity);
    pitch.offset.setValueAtTime(cents(voicePitchAt(change, begin)), start + begin - songTime);
    if (stop > begin) pitch.offset.linearRampToValueAtTime(cents(voicePitchAt(change, stop)), start + stop - songTime);
  }
  const detune = context.createGain();
  pitch.connect(detune);
  channelPitch?.connect(detune);
  pitch.start(start);
  const disconnect = () => {
    pitch.stop();
    pitch.disconnect();
    try { channelPitch?.disconnect(detune); } catch { /* The channel may already be disconnected. */ }
    detune.disconnect();
    gain.disconnect();
  };
  let voices: InstrumentVoice[];
  try {
    voices = engine.schedule(gain, program, percussion, note.midi, note.velocity, start, duration, detune);
  } catch (error) { disconnect(); throw error; }
  let remaining = voices.length;
  if (!remaining) disconnect();
  if (voices.some(voice => voice.stopTime > cutTime)) {
    gain.gain.setValueAtTime(1, Math.max(start, cutTime - .003));
    gain.gain.linearRampToValueAtTime(0, cutTime);
    // SF2 stop triggers its envelope and may honor an already scheduled release.
    // The gain ramp sets the audible cutoff for both engines.
  }
  return voices.map(voice => ({
    stopTime: Math.min(voice.stopTime, cutTime),
    stop: time => voice.stop(time),
    onEnded: callback => voice.onEnded(callback),
    dispose: () => {
      voice.dispose();
      if (--remaining === 0) disconnect();
    },
  }));
}
