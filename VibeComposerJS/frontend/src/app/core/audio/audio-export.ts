import { protectedAudioOutput } from './output-limiter';
import { resolveProjectTimeline } from '../music/project-timeline';
import type { CompositionProject } from '../project/project.model';
import { mixEnabled } from './mix-values';
import { createInstrumentEngine } from './create-instrument-engine';
import type { Sf2Bank } from './sf2-resolver';
import type { InstrumentEngineKind } from './instrument-engine';
import { glidesByChannel } from '../music/phrase-glides';
import { createChannelGlideAudio, disposeChannelGlideAudio, scheduleGlideParam } from './channel-glide-audio';
import { planVoices, type VoiceNote } from '../music/track-voicing';
import { scheduleVoice } from './schedule-voice';

const SAMPLE_RATE = 48000;
// Pinned GM timbres include 11s releases, multiplied by TinySynth's 3.5 ratio.
// Reserve enough render space, then trim to the actual scheduled voice endings.
const MAX_RELEASE_TAIL_SECONDS = 40;

export interface AudioExportRegion { readonly startBeat: number; readonly endBeat: number; }

/** Render the arrangement or a bounded region with the playback instruments, tempo map and mix. */
export async function renderProjectAudio(project: CompositionProject, onProgress?: (percent: number) => void, kind: InstrumentEngineKind = 'synth', bank?: Sf2Bank, region?: AudioExportRegion,
  normalize = false, calibrationProgress?: (message: string) => void): Promise<AudioBuffer> {
  if (!globalThis.OfflineAudioContext) throw new Error('This browser does not support offline audio export.');
  if (region && (!Number.isFinite(region.startBeat) || !Number.isFinite(region.endBeat) || region.endBeat <= region.startBeat)) {
    throw new Error('The audio export region is invalid.');
  }
  const timeline = resolveProjectTimeline(project);
  const anySolo = project.tracks.some(track => track.mix.solo);
  const phrases = project.tracks.map(track => {
    const notes: readonly VoiceNote[] = planVoices(timeline.notesByTrack[track.id], track, timeline.beatToSeconds);
    return { track, notes: region ? notes.filter(note => note.startBeat < region.endBeat
      && note.startBeat + note.durationBeats > region.startBeat).map(note => ({ ...note,
        startBeat: Math.max(region.startBeat, note.startBeat),
        durationBeats: Math.min(region.endBeat, note.startBeat + note.durationBeats) - Math.max(region.startBeat, note.startBeat),
      })) : notes };
  });
  const tracks = phrases.filter(({ track }) => mixEnabled(track.mix, anySolo) && track.mix.volumePercent > 0);
  if (!tracks.some(({ notes }) => notes.some(note => note.velocity > 0))) {
    throw new Error('There are no audible notes to export.');
  }
  const { startBeat, endBeat } = region ?? timeline.soundingBounds;
  const offsetSeconds = -timeline.beatToSeconds(startBeat);
  const songSeconds = timeline.beatToSeconds(endBeat) + offsetSeconds;
  const length = Math.ceil((songSeconds + (region ? 0 : MAX_RELEASE_TAIL_SECONDS)) * SAMPLE_RATE);
  const context = new OfflineAudioContext(2, length, SAMPLE_RATE);
  const synth = await createInstrumentEngine(context, kind, bank, normalize, calibrationProgress);
  const master = context.createGain();
  master.gain.value = 0.8 * (project.masterVolumePercent ?? 100) / 100;
  master.connect(protectedAudioOutput(context));
  const nodes: AudioNode[] = [master];
  const glides = glidesByChannel(project.tracks, timeline.glidesByTrack);
  const channels = createChannelGlideAudio(context, master, glides);
  try {
    for (const [channel, { pitch, volume }] of channels) {
      const curves = glides.get(channel)!;
      if (pitch) scheduleGlideParam(pitch.offset, 'pitch', curves.pitch, startBeat, endBeat, timeline.tempoMap, offsetSeconds);
      if (volume) scheduleGlideParam(volume.gain, 'volume', curves.volume, startBeat, endBeat, timeline.tempoMap, offsetSeconds);
      // Release tails beyond the song end play at the channel's default settings.
      pitch?.offset.setValueAtTime(0, songSeconds);
      volume?.gain.setValueAtTime(1, songSeconds);
    }
    let scheduled = 0;
    let lastStop = songSeconds;
    for (const { track, notes } of tracks) {
      const gain = context.createGain(), pan = context.createStereoPanner();
      gain.gain.value = track.mix.volumePercent / 100;
      pan.pan.value = track.mix.panPercent / 100;
      gain.connect(pan);
      const channel = channels.get(track.midiChannel);
      pan.connect(channel?.volume ?? master);
      nodes.push(gain, pan);
      for (const note of notes) {
        const start = timeline.beatToSeconds(note.startBeat) + offsetSeconds;
        const duration = Math.max(note.pitchChanges ? 0 : 0.035, timeline.beatToSeconds(note.startBeat + note.durationBeats) + offsetSeconds - start);
        const cut = note.cutBeat === undefined ? Infinity : timeline.beatToSeconds(note.cutBeat) + offsetSeconds;
        for (const voice of scheduleVoice(synth, gain, track.mix.program, track.role === 'drums',
          note, start, duration, timeline.beatToSeconds(note.startBeat), cut, channel?.pitch)) {
          voice.onEnded(() => voice.dispose());
          lastStop = Math.max(lastStop, voice.stopTime);
        }
        // Building large arrangements also yields to input and paint.
        if (++scheduled % 128 === 0) await new Promise<void>(resolve => setTimeout(resolve, 0));
      }
    }
    await synth.flush();
    // Offline rendering has no progress event. Briefly suspend at known audio
    // positions, report completed work, then immediately resume off the UI thread.
    // Firefox lacks these methods; render uninterrupted and report completion there.
    const canReportProgress = onProgress && typeof context.suspend === 'function' && typeof context.resume === 'function';
    const checkpoints = canReportProgress ? Array.from({ length: 19 }, (_, index) => {
      const percent = (index + 1) * 5;
      return context.suspend(context.length / context.sampleRate * percent / 100).then(async () => {
        onProgress(percent);
        await context.resume();
      });
    }) : [];
    const [rendered] = await Promise.all([context.startRendering(), ...checkpoints]);
    const trimmed = context.createBuffer(2, Math.min(length, Math.ceil((lastStop + 0.01) * SAMPLE_RATE)), SAMPLE_RATE);
    for (let channel = 0; channel < 2; channel++) {
      trimmed.copyToChannel(rendered.getChannelData(channel).subarray(0, trimmed.length), channel);
    }
    onProgress?.(100);
    return trimmed;
  } finally {
    synth.dispose();
    disposeChannelGlideAudio(channels);
    for (const node of nodes) node.disconnect();
  }
}
