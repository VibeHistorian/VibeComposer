import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { AudioPlaybackService } from '../../core/audio/audio-playback.service';
import { generateCompositionMidi } from '../../core/music/midi-export';
import { phraseForTrack } from '../../core/music/phrase';
import type { CompositionTrack } from '../../core/project/project.model';
import { ProjectService } from '../../core/project/project.service';

@Component({
  selector: 'vc-mix-workspace',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './mix-workspace.component.html',
  styleUrl: './mix-workspace.component.css',
})
export class MixWorkspaceComponent {
  readonly projects = inject(ProjectService);
  readonly playback = inject(AudioPlaybackService);
  readonly project = this.projects.project;
  readonly channels = Array.from({ length: 16 }, (_, index) => index + 1);
  readonly instruments: ReadonlyArray<{ program: number; name: string }> = [
    { program: 0, name: 'Acoustic Grand Piano' },
    { program: 4, name: 'Electric Piano' },
    { program: 10, name: 'Music Box' },
    { program: 11, name: 'Vibraphone' },
    { program: 24, name: 'Nylon Guitar' },
    { program: 25, name: 'Steel Guitar' },
    { program: 32, name: 'Acoustic Bass' },
    { program: 33, name: 'Electric Bass' },
    { program: 38, name: 'Synth Bass' },
    { program: 40, name: 'Violin' },
    { program: 48, name: 'Strings' },
    { program: 56, name: 'Trumpet' },
    { program: 65, name: 'Alto Sax' },
    { program: 73, name: 'Flute' },
    { program: 80, name: 'Square Lead' },
    { program: 88, name: 'Warm Pad' },
  ];
  readonly hasSolo = computed(() => this.project().tracks.some((track) => track.mix.solo));
  readonly audibleCount = computed(() => this.project().tracks.filter((track) => this.isAudible(track.id)).length);
  readonly totalPhraseNotes = computed(() => this.project().tracks.reduce(
    (sum, track) => sum + phraseForTrack(this.project(), track).length, 0,
  ));

  noteCount(trackId: string): number {
    const track = this.project().tracks.find((candidate) => candidate.id === trackId);
    return track ? phraseForTrack(this.project(), track).length : 0;
  }

  isAudible(trackId: string): boolean {
    const settings = this.project().tracks.find((track) => track.id === trackId)?.mix;
    if (!settings) return false;
    return !settings.muted && (!this.hasSolo() || settings.solo);
  }

  hasChannelCollision(trackId: string): boolean {
    const selected = this.track(trackId);
    return selected !== undefined && this.project().tracks.some((track) => track.id !== trackId && track.midiChannel === selected.midiChannel);
  }

  roleName(track: CompositionTrack): string {
    return track.role === 'arpeggio' ? 'Arpeggio' : track.role[0].toUpperCase() + track.role.slice(1);
  }

  updateProgram(trackId: string, event: Event): void {
    if (this.track(trackId)?.role === 'drums') return;
    const program = Number((event.target as HTMLSelectElement).value);
    if (this.instruments.some((instrument) => instrument.program === program)) {
      this.projects.updateTrack(trackId, { mix: { program } });
      this.playback.updateMix(this.project());
    }
  }

  updateVolume(trackId: string, event: Event): void {
    const volumePercent = Number((event.target as HTMLInputElement).value);
    if (Number.isInteger(volumePercent) && volumePercent >= 0 && volumePercent <= 100) {
      this.projects.updateTrack(trackId, { mix: { volumePercent } });
      this.playback.updateMix(this.project());
    }
  }

  updatePan(trackId: string, event: Event): void {
    const panPercent = Number((event.target as HTMLInputElement).value);
    if (Number.isInteger(panPercent) && panPercent >= -100 && panPercent <= 100) {
      this.projects.updateTrack(trackId, { mix: { panPercent } });
      this.playback.updateMix(this.project());
    }
  }

  toggle(trackId: string, setting: 'muted' | 'solo'): void {
    const current = this.track(trackId)?.mix[setting];
    if (current === undefined) return;
    this.projects.updateTrack(trackId, { mix: { [setting]: !current } });
    this.playback.updateMix(this.project());
  }

  instrumentName(program: number): string {
    return this.instruments.find((instrument) => instrument.program === program)?.name ?? `GM patch ${program + 1}`;
  }

  updateChannel(trackId: string, event: Event): void {
    const channel = Number((event.target as HTMLSelectElement).value);
    if (Number.isInteger(channel)) this.projects.updateTrack(trackId, { midiChannel: channel });
  }

  private track(trackId: string): CompositionTrack | undefined {
    return this.project().tracks.find((candidate) => candidate.id === trackId);
  }

  panName(value: number): string {
    if (value === 0) return 'Center';
    return `${Math.abs(value)}% ${value < 0 ? 'Left' : 'Right'}`;
  }

  exportMidi(): void {
    const project = this.project();
    const bytes = generateCompositionMidi(project);
    const fileData = bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) as ArrayBuffer;
    const file = new Blob([fileData], { type: 'audio/midi' });
    const objectUrl = URL.createObjectURL(file);
    const link = document.createElement('a');
    const fileName = project.name.replace(/[<>:"/\\|?*\u0000-\u001f]/g, '-').trim().slice(0, 80) || 'VibeComposer';
    link.href = objectUrl;
    link.download = `${fileName}.mid`;
    document.body.append(link);
    link.click();
    link.remove();
    window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1000);
  }
}
