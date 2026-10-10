import { ChangeDetectionStrategy, Component, computed, effect, inject, untracked, signal } from '@angular/core';
import { AudioPlaybackService } from '../core/audio/audio-playback.service';
import { ProjectService } from '../core/project/project.service';
import { WorkspaceUiService } from './workspace-ui.service';
import { effectiveKey } from '../core/music/harmony';
import { WheelSelectDirective } from './wheel-select.directive';
import { MixButtonsComponent } from './mix-buttons.component';
import { CompactKnobComponent } from './compact-knob.component';
import { ChordletEditorComponent } from './chordlet-editor.component';

@Component({
  selector: 'vc-transport-dock',
  imports: [WheelSelectDirective, MixButtonsComponent, CompactKnobComponent, ChordletEditorComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './transport-dock.component.html',
  styleUrl: './transport-dock.component.css',
})
export class TransportDockComponent {
  readonly playback = inject(AudioPlaybackService);
  private readonly projects = inject(ProjectService);
  readonly workspaceUi = inject(WorkspaceUiService);
  readonly project = this.projects.project;
  readonly key = computed(() => effectiveKey(this.project().key, this.project().transposeSemitones ?? 0));
  private readonly generationFeedback = signal<{ project: unknown; message: string } | null>(null);
  readonly generationMessage = computed(() => this.generationFeedback()?.project === this.project() ? this.generationFeedback()?.message : null);

  generate(event: 'compose' | 'regenerate'): void {
    const result = this.projects.generateComposition(event);
    this.generationFeedback.set({ project: this.project(), message: result === 'invalid'
      ? 'No settings changed. Generation conflicts with local settings.'
      : result === 'unchanged' ? 'Settings already match the generation rules.' : `${event === 'compose' ? 'Compose' : 'Regenerate'} settings applied.` });
  }
  readonly trackIds = computed(() => this.project().tracks.map(track => track.id));
  private readonly liveReloadEffect = effect(() => {
    const project = this.project();
    const live = this.playback.liveEnabled();
    // Playback ticks must not trigger regeneration or subscribe this effect to position/state.
    if (live) untracked(() => this.playback.reload(project));
  });
  private readonly loopRangeEffect = effect(() => {
    const range = this.workspaceUi.sectionRange();
    untracked(() => this.playback.setLoopRange(range));
  });
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

  toggleLive(): void {
    this.playback.toggleLive();
  }

  updateScale(event: Event): void {
    const scale = (event.target as HTMLSelectElement).value;
    if (scale === 'major' || scale === 'natural-minor') this.projects.updateSettings({ scale });
  }

  updateTempo(tempo: number): void {
    if (Number.isInteger(tempo) && tempo >= 40 && tempo <= 240) this.projects.updateSettings({ tempoBpm: tempo });
  }

  updateTranspose(transpose: number): void {
    if (Number.isInteger(transpose) && transpose >= -24 && transpose <= 24) this.projects.updateSettings({ transposeSemitones: transpose });
  }
  updateSeed(event: Event): void {
    const input = event.target as HTMLInputElement, seed = input.value.trim();
    if (/^-?\d+$/.test(seed)) {
      const value = BigInt(seed);
      if (value >= -(1n << 63n) && value <= (1n << 63n) - 1n) this.projects.updateSettings({ seed: value.toString() });
    }
    input.value = this.project().seed;
  }
}
