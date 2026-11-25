import type { InstrumentEngine, InstrumentVoice } from './instrument-engine';
import { voicePitchAt, type VoiceNote } from '../music/track-voicing';
import { TONE_MODULATION_HZ, type InstrumentTone } from './instrument-tone';

/** Add per-note modulation after preset synthesis and alongside existing pitch automation. */
export function scheduleInstrumentNote(engine: InstrumentEngine, destination: AudioNode, program: number, percussion: boolean,
  midi: number, velocity: number, start: number, duration: number, pitchBend?: AudioNode, tone?: InstrumentTone): InstrumentVoice[] {
  const vibrato = tone?.vibratoCents ?? 0, tremolo = (tone?.tremoloPercent ?? 0) / 200;
  if (percussion || velocity <= 0 || (!vibrato && !tremolo)) {
    return engine.schedule(destination, program, percussion, midi, velocity, start, duration, pitchBend, percussion ? undefined : tone);
  }

  const context = engine.context;
  const lfo = context.createOscillator();
  lfo.frequency.value = TONE_MODULATION_HZ;
  const detune = vibrato ? context.createGain() : undefined;
  const pitchDepth = detune ? context.createGain() : undefined;
  if (detune && pitchDepth) {
    pitchDepth.gain.value = vibrato;
    lfo.connect(pitchDepth);
    pitchDepth.connect(detune);
    pitchBend?.connect(detune);
  }
  const amplitudeDepth = tremolo ? context.createGain() : undefined;
  if (amplitudeDepth) {
    amplitudeDepth.gain.value = tremolo;
    lfo.connect(amplitudeDepth);
  }
  lfo.start(start);
  const disconnect = () => {
    lfo.stop();
    lfo.disconnect();
    if (detune) {
      try { pitchBend?.disconnect(detune); } catch { /* The pitch route may already be disconnected. */ }
      detune.disconnect();
    }
    pitchDepth?.disconnect();
    amplitudeDepth?.disconnect();
  };
  let voices: InstrumentVoice[];
  try {
    voices = engine.schedule(destination, program, false, midi, velocity, start, duration, detune ?? pitchBend, tone, amplitudeDepth);
  } catch (error) { disconnect(); throw error; }
  let remaining = voices.length;
  if (!remaining) disconnect();
  return voices.map(voice => ({
    stopTime: voice.stopTime,
    stop: time => voice.stop(time),
    release: voice.release,
    onEnded: callback => voice.onEnded(callback),
    dispose: () => {
      try { voice.dispose(); } finally { if (--remaining === 0) disconnect(); }
    },
  }));
}

export function scheduleVoice(engine: InstrumentEngine, destination: AudioNode, program: number, percussion: boolean,
  note: VoiceNote, start: number, duration: number, songTime: number, cutTime: number, channelPitch?: AudioNode, tone?: InstrumentTone): InstrumentVoice[] {
  const changes = note.pitchChanges;
  if (!changes) return scheduleInstrumentNote(engine, destination, program, percussion, note.midi, note.velocity, start, duration, channelPitch, tone);

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
    voices = scheduleInstrumentNote(engine, gain, program, percussion, note.midi, note.velocity, start, duration, detune, tone);
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
    release: voice.release,
    onEnded: callback => voice.onEnded(callback),
    dispose: () => {
      voice.dispose();
      if (--remaining === 0) disconnect();
    },
  }));
}
