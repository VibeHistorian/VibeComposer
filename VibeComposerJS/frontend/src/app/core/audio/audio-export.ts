import type { CompositionProject } from '../project/project.model';
import { mixEnabled } from './mix-values';
import { layOutTrackPhrase } from '../music/phrase';
import { noteTimelineBounds } from '../music/phrase-bounds';
import { beatToSeconds, projectTempoMap, sectionDurationBeats } from '../music/section-context';
import { createInstrumentEngine } from './create-instrument-engine';
import type { Sf2Bank } from './sf2-resolver';
import type { InstrumentEngineKind } from './instrument-engine';

const SAMPLE_RATE = 48000;
// Pinned GM timbres include 11s releases, multiplied by TinySynth's 3.5 ratio.
// Reserve enough render space, then trim to the actual scheduled voice endings.
const MAX_RELEASE_TAIL_SECONDS = 40;

/** Render the full arrangement with the playback instruments, tempo map and mix. */
export async function renderProjectAudio(project: CompositionProject, onProgress?: (percent: number) => void, kind: InstrumentEngineKind = 'synth', bank?: Sf2Bank): Promise<AudioBuffer> {
  if (!globalThis.OfflineAudioContext) throw new Error('This browser does not support offline audio export.');
  const tempo = projectTempoMap(project);
  const anySolo = project.tracks.some(track => track.mix.solo);
  const phrases = project.tracks.map(track => ({ track, notes: layOutTrackPhrase(project, track) }));
  const tracks = phrases.filter(({ track }) => mixEnabled(track.mix, anySolo) && track.mix.volumePercent > 0);
  if (!tracks.some(({ notes }) => notes.some(note => note.velocity > 0))) {
    throw new Error('There are no audible notes to export.');
  }
  const { startBeat, endBeat } = noteTimelineBounds(phrases.flatMap(({ notes }) => notes),
    project.arrangement.reduce((sum, section) => sum + sectionDurationBeats(project, section), 0));
  const offsetSeconds = -beatToSeconds(tempo, startBeat);
  const songSeconds = beatToSeconds(tempo, endBeat) + offsetSeconds;
  const length = Math.ceil((songSeconds + MAX_RELEASE_TAIL_SECONDS) * SAMPLE_RATE);
  const context = new OfflineAudioContext(2, length, SAMPLE_RATE);
  const synth = await createInstrumentEngine(context, kind, bank);
  const master = context.createGain();
  master.gain.value = 0.8;
  master.connect(context.destination);
  const nodes: AudioNode[] = [master];
  try {
    let scheduled = 0;
    let lastStop = songSeconds;
    for (const { track, notes } of tracks) {
      const gain = context.createGain(), pan = context.createStereoPanner();
      gain.gain.value = track.mix.volumePercent / 100;
      pan.pan.value = track.mix.panPercent / 100;
      gain.connect(pan);
      pan.connect(master);
      nodes.push(gain, pan);
      for (const note of notes) {
        const start = beatToSeconds(tempo, note.startBeat) + offsetSeconds;
        const duration = Math.max(0.035, beatToSeconds(tempo, note.startBeat + note.durationBeats) + offsetSeconds - start);
        for (const voice of synth.schedule(gain, track.mix.program, track.role === 'drums',
          note.midi, note.velocity, start, duration)) {
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
    for (const node of nodes) node.disconnect();
  }
}
