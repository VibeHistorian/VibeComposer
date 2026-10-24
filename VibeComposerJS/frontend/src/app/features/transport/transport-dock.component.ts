import { isScaleMode } from '../../core/music/scales';
import { SCALE_MODES, SCALE_DEFINITIONS } from '../../core/music/scales';
import { afterNextRender, Injector, ChangeDetectionStrategy, Component, computed, effect, inject, untracked, signal } from '@angular/core';
import { AudioPlaybackService } from '../../core/audio/audio-playback.service';
import { ProjectService } from '../../core/project/project.service';
import { UiPreferencesService } from '../../core/ui/ui-preferences.service';
import { WorkspaceUiService } from '../../core/ui/workspace-ui.service';
import { effectiveKey } from '../../core/music/harmony';
import { SelectComponent } from '../../shared/select.component';
import { MixButtonsComponent } from '../tracks/mix-buttons.component';
import { CompactKnobComponent } from '../../shared/compact-knob.component';
import { ChordletEditorComponent } from '../harmony/chordlet-editor.component';

@Component({
  selector: 'vc-transport-dock',
  imports: [SelectComponent, MixButtonsComponent, CompactKnobComponent, ChordletEditorComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './transport-dock.component.html',
  styleUrl: './transport-dock.component.css',
})
export class TransportDockComponent {
  readonly scaleOptions = computed(() => this.scales.map(scale => ({ value: scale, label: this.scaleDefinitions[scale].label })));

  readonly scales = SCALE_MODES;
  readonly scaleDefinitions = SCALE_DEFINITIONS;
  readonly playback = inject(AudioPlaybackService);
  private readonly projects = inject(ProjectService);
  readonly workspaceUi = inject(WorkspaceUiService);
  readonly project = this.projects.project;
  readonly seedInput = this.projects.seedInput;
  private readonly preferences = inject(UiPreferencesService);
  private readonly injector = inject(Injector);

  toggleComposeRandomization(field: 'randomizeTempoOnCompose' | 'randomizeTransposeOnCompose'): void {
    this.projects.updateSettings({ [field]: this.project()[field] === false });
  }
  readonly key = computed(() => effectiveKey(this.project().key, this.project().transposeSemitones ?? 0));
  private readonly generationFeedback = signal<{ project: unknown; message: string } | null>(null);
  readonly generationMessage = computed(() => this.generationFeedback()?.project === this.project() ? this.generationFeedback()?.message : null);

  generate(event: 'compose' | 'regenerate'): void {
    const started = event === 'compose' ? performance.now() : undefined;
    const result = this.projects.generateComposition(event);
    if (event === 'compose' && result !== 'invalid') {
      if (this.project().arrangementPreview) {
        const sectionId = this.playback.previewArrangement(this.project());
        if (sectionId && this.playback.loopEnabled()) this.workspaceUi.setSectionSelection(sectionId);
      }
      else if (this.preferences.restartPlaybackOnCompose()) this.playback.restartAfterCompose(this.project());
    }
    this.generationFeedback.set({ project: this.project(), message: result === 'invalid'
      ? `${event === 'compose' ? 'Compose' : 'Regenerate'} cancelled. ${this.projects.generationError() ?? 'Generation conflicts with local settings.'} Previous arrangement kept.`
      : result === 'unchanged' ? 'Settings already match the generation rules.' : `${event === 'compose' ? 'Compose' : 'Regenerate'} settings applied.` });
    if (started !== undefined) {
      // Include Angular's update, canvas drawing and the browser's next paint.
      afterNextRender(() => requestAnimationFrame(() => setTimeout(() => {
        console.log(`[Compose] ${(performance.now() - started).toFixed(1)} ms`);
      }, 0)), { injector: this.injector });
    }
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
  readonly loopRegion = computed(() => {
    const duration = this.totalMeasures() * 4;
    if (!this.playback.loopEnabled() || duration <= 0) return null;
    const range = this.workspaceUi.sectionRange() ?? { startBeat: 0, endBeat: duration };
    return { left: range.startBeat / duration * 100, width: (range.endBeat - range.startBeat) / duration * 100 };
  });

  seek(event: MouseEvent): void {
    const bounds = (event.currentTarget as HTMLElement).getBoundingClientRect();
    if (bounds.width <= 0) return;
    this.playback.seek(this.project(), (event.clientX - bounds.left) / bounds.width * this.totalMeasures() * 4);
  }

  seekKey(event: KeyboardEvent): void {
    const duration = this.totalMeasures() * 4;
    const beat = event.key === 'Home' ? 0 : event.key === 'End' ? duration
      : event.key === 'ArrowLeft' ? this.playback.beat() - 1 : event.key === 'ArrowRight' ? this.playback.beat() + 1 : null;
    if (beat === null) return;
    event.preventDefault();
    this.playback.seek(this.project(), beat);
  }
  readonly progressPercent = computed(() => {
    const duration = this.totalMeasures() * 4;
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

  updateProjectName(event: Event): void {
    const input = event.target as HTMLTextAreaElement;
    this.projects.updateSettings({ name: input.value.trim() || 'Untitled composition' });
    input.value = this.project().name;
  }

  updateScale(selection: string): void {
    const scale = selection;
    if (isScaleMode(scale)) this.projects.updateSettings({ scale });
  }

  updateTempo(tempo: number): void {
    if (Number.isInteger(tempo) && tempo >= 30 && tempo <= 240) this.projects.updateSettings({ tempoBpm: tempo });
  }

  updateTranspose(transpose: number): void {
    if (Number.isInteger(transpose) && transpose >= -24 && transpose <= 24) this.projects.updateSettings({ transposeSemitones: transpose });
  }
  resetSeed(event: MouseEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.projects.updateSettings({ seed: '0' });
    (event.target as HTMLInputElement).value = this.seedInput();
  }

  seedMouseDown(event: MouseEvent): void {
    if (event.button === 1) event.preventDefault();
  }

  randomizeSeed(event: MouseEvent): void {
    if (event.button !== 1) return;
    event.preventDefault();
    event.stopPropagation();
    this.projects.randomizeSeed();
    (event.target as HTMLInputElement).value = this.seedInput();
  }

  updateSeed(event: Event): void {
    const input = event.target as HTMLInputElement, seed = input.value.trim();
    if (/^-?\d+$/.test(seed)) {
      const value = BigInt(seed);
      if (value >= -(1n << 63n) && value <= (1n << 63n) - 1n) this.projects.updateSettings({ seed: value.toString() });
    }
    input.value = this.seedInput();
  }
}
