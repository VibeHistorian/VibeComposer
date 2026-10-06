import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { AudioPlaybackService } from '../core/audio/audio-playback.service';
import { ProjectService } from '../core/project/project.service';

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
}
