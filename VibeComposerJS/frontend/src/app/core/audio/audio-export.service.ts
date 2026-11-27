import { InstrumentSettingsService } from './instrument-settings.service';
import { inject, Injectable, signal } from '@angular/core';
import { ARRANGED_PARTS } from '../music/part-role';
import { safeExportName } from '../project/export-filename';
import type { CompositionProject } from '../project/project.model';
import { prepareProjectAudio, renderPreparedAudio, type AudioExportRegion, type AudioExportTrack } from './audio-export';

export type AudioExportMode = 'mix' | 'role-stems' | 'individual-stems';

@Injectable({ providedIn: 'root' })
export class AudioExportService {
  private readonly instruments = inject(InstrumentSettingsService);
  readonly busy = signal(false);
  readonly status = signal('');
  readonly progress = signal(0);

  async export(project: CompositionProject, region?: AudioExportRegion, mode: AudioExportMode = 'mix'): Promise<{ blob: Blob; warnings: string[] }> {
    if (this.busy()) throw new Error('An audio export is already running.');
    const { kind, bank } = this.instruments.resolve(project.soundbankName);
    const normalize = this.instruments.normalization();
    this.busy.set(true);
    this.progress.set(0);
    this.status.set('Rendering audio: 0%...');
    try {
      // Let the busy state paint before preparing the offline graph.
      await new Promise<void>(resolve => setTimeout(resolve, 0));
      const warnings: string[] = [];
      const prepared = prepareProjectAudio(project, region);
      const render = async (tracks: readonly AudioExportTrack[], index = 0, count = 1, stem?: string) => {
        const label = stem ? ` (stem ${index + 1} of ${count})` : '';
        const onProgress = (percent: number) => {
          const total = Math.floor((index * 100 + percent) / count);
          this.progress.set(total);
          this.status.set(`Rendering audio: ${total}%${label}...`);
        };
        onProgress(0);
        const buffer = await renderPreparedAudio(prepared, tracks, {
          kind, bank, normalize, onProgress,
          calibrationProgress: message => this.status.set(message),
          onWarning: warning => warnings.push(stem ? `${stem}: ${warning}` : warning),
        });
        return this.encode(buffer);
      };
      if (mode === 'mix') return { blob: await render(prepared.tracks), warnings };

      const stems = mode === 'role-stems' ? ARRANGED_PARTS.flatMap(role => {
        const tracks = prepared.tracks.filter(({ track }) => track.role === role);
        return tracks.length ? [{ name: role[0].toUpperCase() + role.slice(1), tracks }] : [];
      }) : prepared.tracks.map(item => {
        const number = String(project.tracks.indexOf(item.track) + 1).padStart(2, '0');
        return { name: `${number} - ${safeExportName(item.track.name) || 'Track'}`, tracks: [item] };
      });
      const { BlobReader, BlobWriter, ZipWriter } = await import('@zip.js/zip.js/lib/zip-core-custom.js');
      // Opus is already compressed. Store each file as soon as its audio is encoded.
      const archive = new ZipWriter(new BlobWriter('application/zip'), { level: 0, useWebWorkers: false });
      for (const [index, { name, tracks }] of stems.entries()) {
        const blob = await render(tracks, index, stems.length, name);
        await archive.add(`${name}.opus`, new BlobReader(blob));
      }
      this.status.set('Packaging stems...');
      return { blob: await archive.close(), warnings };
    } finally {
      this.busy.set(false);
      this.status.set('');
      this.progress.set(0);
    }
  }

  private encode(buffer: AudioBuffer): Promise<Blob> {
    return new Promise((resolve, reject) => {
      const worker = new Worker(new URL('./opus-encoder.worker', import.meta.url), { type: 'module' });
      const finish = () => worker.terminate();
      worker.onmessage = ({ data }: MessageEvent<{ blob?: Blob; error?: string }>) => {
        finish();
        if (data.blob) resolve(data.blob);
        else reject(new Error(data.error ?? 'Audio encoding failed.'));
      };
      worker.onerror = () => { finish(); reject(new Error('The audio encoder could not run.')); };
      worker.onmessageerror = () => { finish(); reject(new Error('The audio encoder returned an invalid result.')); };
      try {
        const channels = Array.from({ length: buffer.numberOfChannels }, (_, index) => buffer.getChannelData(index));
        worker.postMessage({ channels, sampleRate: buffer.sampleRate }, channels.map(channel => channel.buffer));
      } catch (error) { finish(); reject(error); }
    });
  }
}
