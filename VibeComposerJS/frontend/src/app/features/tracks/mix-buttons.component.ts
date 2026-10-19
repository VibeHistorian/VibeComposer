import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { ProjectService } from '../../core/project/project.service';
import { AudioPlaybackService } from '../../core/audio/audio-playback.service';

@Component({
  selector: 'vc-mix-buttons',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './mix-buttons.component.html',
  styleUrl: './mix-buttons.component.css',
})
export class MixButtonsComponent {
  readonly trackIds = input.required<readonly string[]>();
  readonly label = input.required<string>();
  readonly allowExclusive = input(true);
  private readonly projects = inject(ProjectService);
  private readonly playback = inject(AudioPlaybackService);
  private readonly tracks = computed(() => {
    const ids = new Set(this.trackIds());
    return this.projects.project().tracks.filter(track => ids.has(track.id));
  });
  readonly empty = computed(() => !this.tracks().length);

  state(setting: 'muted' | 'solo'): 'true' | 'false' | 'mixed' {
    const tracks = this.tracks();
    const count = tracks.filter(track => track.mix[setting]).length;
    return count === 0 ? 'false' : count === tracks.length ? 'true' : 'mixed';
  }

  toggle(setting: 'muted' | 'solo'): void {
    this.projects.toggleTrackMix(this.trackIds(), setting);
    this.playback.updateMix(this.projects.project());
  }

  exclusiveSolo(event: MouseEvent): void {
    if (event.button !== 1 || !this.allowExclusive()) return;
    event.preventDefault();
    event.stopPropagation();
    this.projects.toggleTrackMix(this.trackIds(), 'solo', true);
    this.playback.updateMix(this.projects.project());
  }

  preventMiddleScroll(event: MouseEvent): void {
    if (event.button === 1 && this.allowExclusive()) event.preventDefault();
  }
}
