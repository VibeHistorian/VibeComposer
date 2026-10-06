import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { AudioPlaybackService } from '../core/audio/audio-playback.service';
import { ProjectService } from '../core/project/project.service';
import { KEYS } from '../core/music/harmony';

@Component({
  selector: 'vc-transport-dock',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './transport-dock.component.html',
  styleUrl: './transport-dock.component.css',
})
export class TransportDockComponent {
  readonly playback = inject(AudioPlaybackService);
  private readonly projects = inject(ProjectService);
  readonly project = this.projects.project;
  readonly keys = KEYS;
  readonly totalMeasures = computed(() => this.project().arrangement.reduce((sum, section) => sum + section.measures, 0));
  readonly progressPercent = computed(() => {
    const duration = this.playback.durationBeats();
    return duration > 0 ? Math.min(100, this.playback.beat() / duration * 100) : 0;
  });
  readonly positionText = computed(() => {
    const duration = this.playback.durationBeats();
    const beat = this.playback.beat();
    if (duration > 0 && beat >= duration) return 'END';
    const measure = Math.floor(beat / 4) + 1;
    const beatInMeasure = Math.floor(beat % 4) + 1;
    return `BAR ${String(measure).padStart(2, '0')} · BEAT ${beatInMeasure}`;
  });

  togglePlayback(): void {
    void this.playback.toggle(this.project());
  }

  stop(): void {
    this.playback.stop();
  }

  toggleLoop(): void {
    this.playback.toggleLoop();
  }

  updateKey(event: Event): void {
    const key = (event.target as HTMLSelectElement).value;
    if (KEYS.includes(key)) this.projects.updateSettings({ key });
  }

  updateScale(event: Event): void {
    const scale = (event.target as HTMLSelectElement).value;
    if (scale === 'major' || scale === 'natural-minor') this.projects.updateSettings({ scale });
  }

  updateTempo(event: Event): void {
    const tempo = Number((event.target as HTMLInputElement).value);
    if (Number.isInteger(tempo) && tempo >= 40 && tempo <= 240) this.projects.updateSettings({ tempoBpm: tempo });
  }

  updateTranspose(event: Event): void {
    const transpose = Number((event.target as HTMLInputElement).value);
    if (Number.isInteger(transpose) && transpose >= -24 && transpose <= 24) this.projects.updateSettings({ transposeSemitones: transpose });
  }
}
