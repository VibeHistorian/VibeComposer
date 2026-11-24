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
const RENDER_QUANTUM = 128;
const SCHEDULE_WINDOW_FRAMES = 4 * SAMPLE_RATE;
const MIN_NOTE_SECONDS = 0.035;
const END_PADDING_SECONDS = 0.01;
// Pinned GM timbres include 11s releases, multiplied by TinySynth's 3.5 ratio.
// Reserve enough render space, then trim to the actual scheduled voice endings.
const MAX_RELEASE_TAIL_SECONDS = 40;

export interface AudioExportRegion { readonly startBeat: number; readonly endBeat: number; }

/** Render the arrangement or a bounded region with the playback instruments, tempo map and mix. */
export async function renderProjectAudio(project: CompositionProject, onProgress?: (percent: number) => void, kind: InstrumentEngineKind = 'synth', bank?: Sf2Bank, region?: AudioExportRegion,
  normalize = false, calibrationProgress?: (message: string) => void, onWarning?: (message: string) => void): Promise<AudioBuffer> {
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
  // A short final note can be extended by the minimum duration before its release.
  const tail = region ? 0 : MAX_RELEASE_TAIL_SECONDS + MIN_NOTE_SECONDS + END_PADDING_SECONDS;
  const length = Math.ceil((songSeconds + tail) * SAMPLE_RATE);
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
    let nextEvent = 0;
    let lastStop = songSeconds;
    let lateNotes = 0;
    let maxLateness = 0;
    const events = tracks.flatMap(({ track, notes }) => {
      const gain = context.createGain(), pan = context.createStereoPanner();
      gain.gain.value = track.mix.volumePercent / 100;
      pan.pan.value = track.mix.panPercent / 100;
      gain.connect(pan);
      const channel = channels.get(track.midiChannel);
      pan.connect(channel?.volume ?? master);
      nodes.push(gain, pan);
      return notes.map(note => ({ track, note, gain, pitch: channel?.pitch,
        start: timeline.beatToSeconds(note.startBeat) + offsetSeconds }));
    });
    const canSuspend = typeof context.suspend === 'function' && typeof context.resume === 'function';
    if (canSuspend) events.sort((a, b) => a.start - b.start);

    const scheduleBefore = async (frame: number) => {
      const end = frame === length ? Infinity : frame / SAMPLE_RATE;
      for (; nextEvent < events.length; nextEvent++) {
        const { track, note, gain, pitch, start } = events[nextEvent];
        if (start >= end) break;
        const duration = Math.max(note.pitchChanges ? 0 : MIN_NOTE_SECONDS, timeline.beatToSeconds(note.startBeat + note.durationBeats) + offsetSeconds - start);
        const cut = note.cutBeat === undefined ? Infinity : timeline.beatToSeconds(note.cutBeat) + offsetSeconds;
        const lateness = context.currentTime - start;
        const voices = scheduleVoice(synth, gain, track.mix.program, track.role === 'drums',
          note, start, duration, timeline.beatToSeconds(note.startBeat), cut, pitch);
        if (voices.length && lateness >= 1 / SAMPLE_RATE) {
          lateNotes++;
          maxLateness = Math.max(maxLateness, lateness);
        }
        for (const voice of voices) {
          voice.onEnded(() => voice.dispose());
          lastStop = Math.max(lastStop, voice.stopTime);
        }
        // Building large arrangements also yields to input and paint.
        if ((nextEvent + 1) % 128 === 0) await new Promise<void>(resolve => setTimeout(resolve, 0));
      }
      await synth.flush();
    };

    // Future voices each add several connected nodes. Feed the same graph a
    // few seconds at a time so each render quantum visits far fewer idle nodes.
    // Keeping one context lets held notes, tails and automation span checkpoints.
    const checkpoints: { frame: number; percent?: number }[] = [];
    const progressStep = canSuspend && onProgress ? 5 : 100;
    const lastStart = canSuspend ? events.at(-1)?.start ?? 0 : 0;
    let frame = 0;
    for (let percent = progressStep; percent <= 100; percent += progressStep) {
      // Suspend at distinct render-quantum boundaries, even for very short regions.
      const boundary = percent === 100 ? length : Math.floor(length * percent / 100 / RENDER_QUANTUM) * RENDER_QUANTUM;
      if (boundary <= frame) continue;
      while (frame + SCHEDULE_WINDOW_FRAMES < boundary && (frame + SCHEDULE_WINDOW_FRAMES) / SAMPLE_RATE <= lastStart) {
        frame += SCHEDULE_WINDOW_FRAMES;
        checkpoints.push({ frame });
      }
      if (percent < 100) checkpoints.push({ frame: boundary, percent });
      frame = boundary;
    }

    const advance = async (index: number): Promise<void> => {
      const checkpoint = checkpoints[index];
      if (!checkpoint) return;
      await context.suspend(checkpoint.frame / SAMPLE_RATE);
      let following: Promise<void> | undefined;
      try {
        await scheduleBefore(checkpoints[index + 1]?.frame ?? length);
        if (checkpoint.percent !== undefined) onProgress?.(checkpoint.percent);
        following = advance(index + 1);
      } finally {
        await Promise.all([following, context.resume()]);
      }
    };
    // Browsers without suspend/resume pre-schedule the full arrangement.
    await scheduleBefore(checkpoints[0]?.frame ?? length);
    const advancing = advance(0);
    const [rendered] = await Promise.all([context.startRendering(), advancing]);
    const trimmed = context.createBuffer(2, Math.min(length, Math.ceil((lastStop + END_PADDING_SECONDS) * SAMPLE_RATE)), SAMPLE_RATE);
    for (let channel = 0; channel < 2; channel++) {
      trimmed.copyToChannel(rendered.getChannelData(channel).subarray(0, trimmed.length), channel);
    }
    if (lateNotes) {
      onWarning?.(`Timing may differ from playback. ${lateNotes} ${lateNotes === 1 ? 'note was' : 'notes were'} scheduled late (up to ${Math.ceil(maxLateness * 1000)} ms).`);
    }
    if (!region && lastStop > rendered.duration + 1 / SAMPLE_RATE) {
      onWarning?.('A release tail extended past the end of the render and may be clipped.');
    }
    onProgress?.(100);
    return trimmed;
  } finally {
    synth.dispose();
    disposeChannelGlideAudio(channels);
    for (const node of nodes) node.disconnect();
  }
}
