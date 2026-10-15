import { Injectable, signal } from '@angular/core';
import type { CompositionProject } from '../project/project.model';
import { renderProjectAudio } from './audio-export';

@Injectable({ providedIn: 'root' })
export class AudioExportService {
  readonly busy = signal(false);
  readonly status = signal('');

  async export(project: CompositionProject): Promise<Blob> {
    if (this.busy()) throw new Error('An audio export is already running.');
    this.busy.set(true);
    this.status.set('Rendering audio…');
    try {
      // Let the busy state paint before preparing the offline graph.
      await new Promise<void>(resolve => setTimeout(resolve, 0));
      const buffer = await renderProjectAudio(project);
      this.status.set('Encoding audio…');
      return await this.encode(buffer);
    } finally {
      this.busy.set(false);
      this.status.set('');
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
