import { TooltipDirective } from './shared/tooltip.directive';
import { DOCUMENT } from '@angular/common';
import { installPointerActionFocus } from './shared/pointer-action-focus';
import { afterNextRender, ChangeDetectionStrategy, Component, DestroyRef, ElementRef, effect, untracked, inject, signal, viewChild } from '@angular/core';
import { Dialog, DialogModule } from '@angular/cdk/dialog';
import { CdkMenuModule } from '@angular/cdk/menu';
import { AudioExportService, type AudioExportMode } from './core/audio/audio-export.service';
import { AudioPlaybackService } from './core/audio/audio-playback.service';
import { MidiOutputService } from './core/audio/midi-output.service';
import { generateCompositionMidi } from './core/music/midi-export';
import { downloadProjectFile } from './core/project/download-file';
import { exportFilename } from './core/project/export-filename';
import { UiPreferencesService } from './core/ui/ui-preferences.service';
import { HostedContentService } from './core/ui/hosted-content.service';
import { HistoryDialogComponent } from './features/history/history-dialog.component';
import { ProjectService } from './core/project/project.service';
import { SettingsDialogComponent } from './features/settings/settings-dialog.component';
import { TransportDockComponent } from './features/transport/transport-dock.component';
import { WorkspaceCanvasComponent } from './features/workspace/workspace-canvas.component';
import { WorkspaceUiService } from './core/ui/workspace-ui.service';
import { translateJavaConfig } from './core/project/java-config-translator';
import { isEditingText, usesArrowKeys, usesSpaceKey } from './shared/keyboard-shortcuts';

@Component({
  selector: 'vc-root',
  imports: [TooltipDirective, DialogModule, CdkMenuModule, TransportDockComponent, WorkspaceCanvasComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.component.html',
  styleUrl: './app.component.css',
  host: { '(document:keydown)': 'globalKeydown($event)' },
})
export class AppComponent {
  constructor() {
    const destroyRef = inject(DestroyRef);
    destroyRef.onDestroy(installPointerActionFocus(inject(DOCUMENT)));
    destroyRef.onDestroy(() => this.fileOpen?.abort());
    const host = inject<ElementRef<HTMLElement>>(ElementRef);
    afterNextRender(() => {
      const frame = host.nativeElement.querySelector<HTMLElement>('.app-frame')!;
      const dock = frame.querySelector<HTMLElement>('.transport-dock')!;
      const updateHeight = () => frame.style.setProperty('--transport-height', `${dock.getBoundingClientRect().height}px`);
      const observer = new ResizeObserver(updateHeight);
      observer.observe(dock);
      updateHeight();
      destroyRef.onDestroy(() => observer.disconnect());
    });
  }
  readonly projectMessage = signal('');
  readonly openingProject = signal(false);
  readonly hosted = inject(HostedContentService);
  readonly audioExport = inject(AudioExportService);
  readonly playback = inject(AudioPlaybackService);
  readonly preferences = inject(UiPreferencesService);
  readonly midi = inject(MidiOutputService);
  readonly exportMenuPositions = [
    { originX: 'end', originY: 'top', overlayX: 'end', overlayY: 'bottom', offsetY: -6 },
    { originX: 'end', originY: 'bottom', overlayX: 'end', overlayY: 'top', offsetY: 6 },
  ] as const;
  readonly projects = inject(ProjectService);
  readonly workspaceUi = inject(WorkspaceUiService);
  readonly project = this.projects.project;
  private readonly dialogs = inject(Dialog);
  private readonly workspace = viewChild(WorkspaceCanvasComponent);
  private fileOpen?: AbortController;

  private lastProjectTab = this.projects.projectActivation();
  private readonly projectTabEffect = effect(() => {
    const id = this.projects.projectActivation();
    if (id === this.lastProjectTab) return;
    this.lastProjectTab = id;
    untracked(() => {
      if (this.workspace()?.editing()) this.workspace()?.closeEditor();
      this.playback.stop();
      this.workspaceUi.clearSelection();
    });
  });

  globalKeydown(event: KeyboardEvent): void {
    if (event.defaultPrevented || event.isComposing || isEditingText(event)) return;
    if (this.preferences.matchesShortcut('playback', event) && (event.key !== ' ' || !usesSpaceKey(event))) {
      event.preventDefault();
      event.stopPropagation();
      if (!event.repeat) void this.playback.toggle(this.project());
      return;
    }
    const redo = this.preferences.matchesShortcut('redo', event);
    if (redo || this.preferences.matchesShortcut('undo', event)) {
      // Phrase history owns the shortcut for the entire editing session, even when empty.
      const workspace = this.workspace();
      const history = workspace?.editing() ? workspace.editor() : this.projects;
      if (redo) history?.redo();
      else history?.undo();
      event.preventDefault();
      event.stopPropagation();
      return;
    }
    this.navigateWithArrow(event);
  }

  private navigateWithArrow(event: KeyboardEvent): void {
    const horizontal = event.key === 'ArrowLeft' || event.key === 'ArrowRight';
    if (!horizontal && event.key !== 'ArrowUp' && event.key !== 'ArrowDown') return;
    if (event.ctrlKey || event.metaKey || event.altKey || event.shiftKey || usesArrowKeys(event)
      || this.dialogs.openDialogs.length || this.preferences.capturingShortcut()) return;
    const workspace = this.workspace();
    if (!workspace) return;

    const direction = event.key === 'ArrowLeft' || event.key === 'ArrowUp' ? -1 : 1;
    if (horizontal) workspace.seekAdjacentBoundary(direction);
    else if (!workspace.editing()) this.workspaceUi.moveSettingsSelection(direction);
    event.preventDefault();
    event.stopPropagation();
  }

  openSettings(): void {
    this.dialogs.open(SettingsDialogComponent, { ariaLabelledBy: 'settings-title' });
  }

  openHistory(): void {
    this.dialogs.open(HistoryDialogComponent, { ariaLabelledBy: 'history-title' });
  }

  exportProject(): void {
    const project = this.project();
    const blob = new Blob([this.projects.exportProjectJson()], { type: 'application/json' });
    downloadProjectFile(blob, exportFilename(project, this.preferences.exportNameFields()), 'vibe.json');
    this.projectMessage.set('Project settings exported.');
  }

  exportMidi(): void {
    try {
      const project = this.project();
      const bytes = new Uint8Array(generateCompositionMidi(project, this.midi.drumMapping(), this.preferences.midiPpq()));
      downloadProjectFile(new Blob([bytes], { type: 'audio/midi' }), exportFilename(project, this.preferences.exportNameFields()), 'mid');
      this.projectMessage.set('MIDI exported.');
    } catch (error) {
      this.projectMessage.set(error instanceof Error ? `Could not export MIDI: ${error.message}` : 'Could not export MIDI.');
    }
  }

  async exportAudio(mode: AudioExportMode, regionOnly: boolean): Promise<void> {
    if (this.audioExport.busy()) return;
    const region = regionOnly ? this.workspaceUi.sectionRange() ?? undefined : undefined;
    if (regionOnly && !region) return;
    const project = this.project();
    this.projectMessage.set('');
    try {
      const { blob, warnings } = await this.audioExport.export(project, region, mode);
      const filename = exportFilename(project, this.preferences.exportNameFields());
      if (mode === 'mix') downloadProjectFile(blob, filename, 'opus');
      else downloadProjectFile(blob, `${filename}${regionOnly ? '-region' : ''}-${mode}`, 'zip');
      this.projectMessage.set(['Audio exported.', ...warnings].join(' '));
    } catch (error) {
      this.projectMessage.set(error instanceof Error ? `Could not export audio: ${error.message}` : 'Could not export audio.');
    }
  }

  async importProject(event: Event): Promise<void> {
    const input = event.currentTarget as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file || this.openingProject()) return;

    const controller = new AbortController();
    this.fileOpen = controller;
    this.openingProject.set(true);
    const activation = this.projects.projectActivation();
    try {
      const isMidi = /\.(mid|midi)$/i.test(file.name)
        || new TextDecoder().decode(await file.slice(0, 4).arrayBuffer()) === 'MThd';
      if (controller.signal.aborted) return;
      if (isMidi) {
        this.projectMessage.set('Reading MIDI…');
        const { readMidiProjectFile } = await import('./core/ui/midi-file-import');
        const { project, warnings } = await readMidiProjectFile(file, controller.signal);
        if (controller.signal.aborted) return;
        // A tab switched during conversion should never be the target of a replacement.
        const destination = activation === this.projects.projectActivation() ? 'preference' : 'new-tab';
        const valid = this.projects.importProject(project, destination);
        const tracks = project.tracks.length;
        this.projectMessage.set(valid ? [`Loaded ${project.name} from MIDI (${tracks} ${tracks === 1 ? 'track' : 'tracks'}).`, ...warnings].join(' ')
          : 'Could not open a project from that MIDI file.');
        return;
      }
      const contents = await file.text();
      if (controller.signal.aborted) return;
      const translation = contents.trimStart().startsWith('<')
        ? translateJavaConfig(contents, { name: file.name.replace(/\.xml$/i, '') }) : undefined;
      const valid = this.projects.importProjectJson(translation ? JSON.stringify(translation.project) : contents);
      if (translation?.diagnostics.length) console.info('Java XML translation notes:', translation.diagnostics);
      this.projectMessage.set(valid ? `Loaded ${this.project().name}.${translation?.diagnostics.length
        ? ` ${translation.diagnostics.length} translation notes (see console).` : ''}` : 'That project file is invalid or unsupported.');
    } catch (error) {
      if (controller.signal.aborted) return;
      console.warn(`Could not read project file "${file.name}".`, error);
      this.projectMessage.set(error instanceof Error ? `Could not import: ${error.message}` : 'Could not read that project file.');
    } finally {
      this.fileOpen = undefined;
      this.openingProject.set(false);
    }
  }
}
