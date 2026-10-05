import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { getDiatonicChords, KEYS } from '../../core/music/harmony';
import type { DiatonicChord, ScaleMode } from '../../core/music/harmony';
import { ProjectService } from '../../core/project/project.service';

@Component({
  selector: 'vc-create-workspace',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './create-workspace.component.html',
  styleUrl: './create-workspace.component.css',
})
export class CreateWorkspaceComponent {
  readonly projects = inject(ProjectService);
  readonly project = this.projects.project;
  readonly keys = KEYS;
  readonly chords = computed(() => getDiatonicChords(this.project().key, this.project().scale));

  chordFor(degree: number): DiatonicChord | undefined {
    return this.chords().find((chord) => chord.degree === degree);
  }

  changeChord(index: number, event: Event): void {
    const degree = Number(this.valueFrom(event));
    this.projects.setChordDegree(index, degree);
  }

  updateName(event: Event): void {
    this.projects.updateSettings({ name: this.valueFrom(event).trim() || 'Untitled composition' });
  }

  updateKey(event: Event): void {
    this.projects.updateSettings({ key: this.valueFrom(event) });
  }

  updateScale(event: Event): void {
    const scale = this.valueFrom(event);
    if (scale === 'major' || scale === 'natural-minor') {
      this.projects.updateSettings({ scale: scale as ScaleMode });
    }
  }

  updateTempo(event: Event): void {
    const tempo = Number(this.valueFrom(event));
    if (Number.isInteger(tempo) && tempo >= 40 && tempo <= 240) {
      this.projects.updateSettings({ tempoBpm: tempo });
    } else {
      (event.target as HTMLInputElement).value = String(this.project().tempoBpm);
    }
  }

  updateSeed(event: Event): void {
    const seed = this.valueFrom(event).trim();
    if (/^-?\d+$/.test(seed)) {
      const value = BigInt(seed);
      if (value >= -(1n << 63n) && value <= (1n << 63n) - 1n) {
        this.projects.updateSettings({ seed: value.toString() });
        return;
      }
    }
    (event.target as HTMLInputElement).value = this.project().seed;
  }

  private valueFrom(event: Event): string {
    return (event.target as HTMLInputElement | HTMLSelectElement).value;
  }
}
