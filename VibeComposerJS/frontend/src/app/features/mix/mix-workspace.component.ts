import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { generateCompositionMidi } from '../../core/music/midi-export';
import { phraseForProject } from '../../core/music/phrase';
import type { ArrangedPart } from '../../core/project/project.model';
import { ProjectService } from '../../core/project/project.service';

@Component({
  selector: 'vc-mix-workspace',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './mix-workspace.component.html',
  styleUrl: './mix-workspace.component.css',
})
export class MixWorkspaceComponent {
  readonly projects = inject(ProjectService);
  readonly project = this.projects.project;
  readonly parts: ReadonlyArray<{ key: ArrangedPart; label: string; color: string; description: string }> = [
    { key: 'bass', label: 'Bass', color: 'bass', description: 'Low end and pulse' },
    { key: 'chords', label: 'Chords', color: 'chords', description: 'Harmony and sustain' },
    { key: 'arpeggio', label: 'Arpeggio', color: 'arpeggio', description: 'Movement and detail' },
    { key: 'drums', label: 'Drums', color: 'drums', description: 'Percussion channel' },
  ];
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
  readonly hasSolo = computed(() => this.parts.some((part) => this.project().mix[part.key].solo));
  readonly audibleCount = computed(() => this.parts.filter((part) => this.isAudible(part.key)).length);
  readonly totalPhraseNotes = computed(() => this.parts.reduce(
    (sum, part) => sum + phraseForProject(this.project(), part.key).length, 0,
  ));

  noteCount(part: ArrangedPart): number {
    return phraseForProject(this.project(), part).length;
  }

  isAudible(part: ArrangedPart): boolean {
    const settings = this.project().mix[part];
    return !settings.muted && (!this.hasSolo() || settings.solo);
  }

  updateProgram(part: ArrangedPart, event: Event): void {
    if (part === 'drums') return;
    const program = Number((event.target as HTMLSelectElement).value);
    if (this.instruments.some((instrument) => instrument.program === program)) {
      this.projects.updateMixSettings(part, { program });
    }
  }

  updateVolume(part: ArrangedPart, event: Event): void {
    const volumePercent = Number((event.target as HTMLInputElement).value);
    if (Number.isInteger(volumePercent) && volumePercent >= 0 && volumePercent <= 100) {
      this.projects.updateMixSettings(part, { volumePercent });
    }
  }

  updatePan(part: ArrangedPart, event: Event): void {
    const panPercent = Number((event.target as HTMLInputElement).value);
    if (Number.isInteger(panPercent) && panPercent >= -100 && panPercent <= 100) {
      this.projects.updateMixSettings(part, { panPercent });
    }
  }

  toggle(part: ArrangedPart, setting: 'muted' | 'solo'): void {
    const current = this.project().mix[part][setting];
    this.projects.updateMixSettings(part, { [setting]: !current });
  }

  instrumentName(program: number): string {
    return this.instruments.find((instrument) => instrument.program === program)?.name ?? `GM patch ${program + 1}`;
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
