import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import type { ArrangedPart, ArrangementSection, PhraseNote } from '../core/project/project.model';
import { ARRANGED_PARTS } from '../core/project/project.model';
import { AudioPlaybackService } from '../core/audio/audio-playback.service';
import { layOutPhrase, phraseForProject } from '../core/music/phrase';
import { shouldGeneratePartInSection } from '../core/music/arrangement-generator';
import { ProjectService } from '../core/project/project.service';

interface TrackRow {
  readonly id: ArrangedPart;
  readonly name: string;
  readonly color: string;
  readonly midiChannel: number;
  readonly noteCount: number;
  readonly muted: boolean;
  readonly solo: boolean;
}

interface TimelineSection {
  readonly section: ArrangementSection;
  readonly startBeat: number;
  readonly startMeasure: number;
  readonly widthPercent: number;
}

interface ScoreNote extends PhraseNote {
  readonly part: ArrangedPart;
  readonly color: string;
  readonly top: number;
  readonly opacity: number;
}

const PARTS: ReadonlyArray<{ id: ArrangedPart; name: string; color: string; midiChannel: number }> = [
  { id: 'bass', name: 'Bass', color: 'bass', midiChannel: 1 },
  { id: 'chords', name: 'Chords', color: 'chords', midiChannel: 2 },
  { id: 'arpeggio', name: 'Arpeggio', color: 'arpeggio', midiChannel: 3 },
  { id: 'drums', name: 'Drums', color: 'drums', midiChannel: 10 },
];

@Component({
  selector: 'vc-workspace-canvas',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './workspace-canvas.component.html',
  styleUrl: './workspace-canvas.component.css',
})
export class WorkspaceCanvasComponent {
  readonly projects = inject(ProjectService);
  readonly playback = inject(AudioPlaybackService);
  readonly project = this.projects.project;
  readonly selectedPart = signal<ArrangedPart>('bass');
  readonly selectedSectionId = signal<string | null>(null);
  readonly visibleParts = signal(new Set<ArrangedPart>(ARRANGED_PARTS));
  readonly collapsedParts = signal(new Set<ArrangedPart>());

  readonly totalMeasures = computed(() => this.project().arrangement.reduce((sum, section) => sum + section.measures, 0));
  readonly totalBeats = computed(() => this.totalMeasures() * 4);
  readonly playheadPercent = computed(() => this.totalBeats() > 0
    ? Math.min(100, Math.max(0, this.playback.beat() / this.totalBeats() * 100)) : 0);
  readonly timelineSections = computed<TimelineSection[]>(() => {
    let beat = 0;
    let measure = 1;
    const measures = this.totalMeasures();
    return this.project().arrangement.map((section) => {
      const item = {
        section,
        startBeat: beat,
        startMeasure: measure,
        widthPercent: section.measures / measures * 100,
      };
      beat += section.measures * 4;
      measure += section.measures;
      return item;
    });
  });
  readonly selectedSection = computed(() => this.project().arrangement.find((section) => section.id === this.selectedSectionId()) ?? null);
  readonly tracks = computed<TrackRow[]>(() => {
    const project = this.project();
    return PARTS.map((part) => ({
      ...part,
      noteCount: phraseForProject(project, part.id).length,
      muted: project.mix[part.id].muted,
      solo: project.mix[part.id].solo,
    }));
  });
  readonly scoreNotes = computed<ScoreNote[]>(() => {
    const project = this.project();
    const allNotes = PARTS.flatMap((part) => layOutPhrase(project, part.id, phraseForProject(project, part.id))
      .map((note) => ({ ...note, part: part.id, color: part.color })));
    if (allNotes.length === 0) return [];
    const minPitch = Math.min(...allNotes.map((note) => note.midi));
    const maxPitch = Math.max(...allNotes.map((note) => note.midi));
    const pitchSpan = Math.max(12, maxPitch - minPitch);
    return allNotes.map((note) => ({
      ...note,
      top: (maxPitch - note.midi) / pitchSpan * 160,
      opacity: 0.28 + note.velocity / 127 * 0.72,
    }));
  });
  readonly selectedTrack = computed(() => this.tracks().find((track) => track.id === this.selectedPart())!);

  selectTrack(part: ArrangedPart): void {
    this.selectedPart.set(part);
    this.selectedSectionId.set(null);
  }

  selectSection(section: ArrangementSection, part: ArrangedPart): void {
    this.selectedSectionId.set(section.id);
    this.selectedPart.set(part);
  }

  toggleVisibility(part: ArrangedPart): void {
    const next = new Set(this.visibleParts());
    if (next.has(part)) next.delete(part);
    else next.add(part);
    this.visibleParts.set(next);
  }

  toggleCollapsed(part: ArrangedPart): void {
    const next = new Set(this.collapsedParts());
    if (next.has(part)) next.delete(part);
    else next.add(part);
    this.collapsedParts.set(next);
  }

  toggleMix(part: ArrangedPart, setting: 'muted' | 'solo'): void {
    const current = this.project().mix[part][setting];
    this.projects.updateMixSettings(part, { [setting]: !current });
    this.playback.updateMix(this.project());
  }

  sectionPlays(section: ArrangementSection, part: ArrangedPart): boolean {
    return shouldGeneratePartInSection(BigInt(this.project().seed), section, part);
  }

  noteLeft(note: ScoreNote): number {
    return note.startBeat / this.totalBeats() * 100;
  }

  noteWidth(note: ScoreNote): number {
    return Math.max(0.12, note.durationBeats / this.totalBeats() * 100);
  }

  sectionLabel(type: string): string {
    return type.replaceAll('_', ' ');
  }
}
