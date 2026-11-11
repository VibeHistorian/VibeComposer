import { DOCUMENT } from '@angular/common';
import { installPointerButtonFocus } from './shared/pointer-button-focus';
import { afterNextRender, ChangeDetectionStrategy, Component, DestroyRef, ElementRef, effect, untracked, inject, signal, viewChild } from '@angular/core';
import { Dialog, DialogModule } from '@angular/cdk/dialog';
import { CdkMenuModule } from '@angular/cdk/menu';
import { AudioExportService } from './core/audio/audio-export.service';
import { AudioPlaybackService } from './core/audio/audio-playback.service';
import { MidiOutputService } from './core/audio/midi-output.service';
import { generateCompositionMidi } from './core/music/midi-export';
import { downloadProjectFile } from './core/project/download-file';
import { exportFilename } from './core/project/export-filename';
import { UiPreferencesService } from './core/ui/ui-preferences.service';
import { HistoryDialogComponent } from './features/history/history-dialog.component';
import { ProjectService } from './core/project/project.service';
import { SettingsDialogComponent } from './features/settings/settings-dialog.component';
import { TransportDockComponent } from './features/transport/transport-dock.component';
import { WorkspaceCanvasComponent } from './features/workspace/workspace-canvas.component';
import { WorkspaceUiService } from './core/ui/workspace-ui.service';
import { translateJavaConfig } from './core/project/java-config-translator';
import { isEditingText, usesSpaceKey } from './shared/keyboard-shortcuts';

@Component({
  selector: 'vc-root',
  imports: [DialogModule, CdkMenuModule, TransportDockComponent, WorkspaceCanvasComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.component.html',
  styleUrl: './app.component.css',
  host: { '(document:keydown)': 'historyKeydown($event)' },
})
export class AppComponent {
  constructor() {
    const destroyRef = inject(DestroyRef);
    destroyRef.onDestroy(installPointerButtonFocus(inject(DOCUMENT)));
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

  historyKeydown(event: KeyboardEvent): void {
    if (this.preferences.matchesShortcut('playback', event) && !event.defaultPrevented && !event.isComposing
      && !isEditingText(event) && (event.key !== ' ' || !usesSpaceKey(event))) {
      event.preventDefault();
      event.stopPropagation();
      if (!event.repeat) void this.playback.toggle(this.project());
      return;
    }
    if (event.defaultPrevented || event.isComposing || isEditingText(event)) return;
    const redo = this.preferences.matchesShortcut('redo', event);
    if (!redo && !this.preferences.matchesShortcut('undo', event)) return;

    // Phrase history owns the shortcut for the entire editing session, even when empty.
    const workspace = this.workspace();
    const history = workspace?.editing() ? workspace.editor() : this.projects;
    if (redo) history?.redo();
    else history?.undo();
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

  async exportAudio(regionOnly = false): Promise<void> {
    if (this.audioExport.busy()) return;
    const region = regionOnly ? this.workspaceUi.sectionRange() ?? undefined : undefined;
    if (regionOnly && !region) return;
    const project = this.project();
    this.projectMessage.set('');
    try {
      const blob = await this.audioExport.export(project, region);
      downloadProjectFile(blob, exportFilename(project, this.preferences.exportNameFields()), 'opus');
      this.projectMessage.set('Audio exported.');
    } catch (error) {
      this.projectMessage.set(error instanceof Error ? `Could not export audio: ${error.message}` : 'Could not export audio.');
    }
  }

  async importProject(event: Event): Promise<void> {
    const input = event.currentTarget as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;

    try {
      const contents = await file.text();
      const translation = contents.trimStart().startsWith('<')
        ? translateJavaConfig(contents, { name: file.name.replace(/\.(xml-config|xml)$/i, '') }) : undefined;
      const valid = this.projects.importProjectJson(translation ? JSON.stringify(translation.project) : contents);
      if (translation?.diagnostics.length) console.info('Java XML translation notes:', translation.diagnostics);
      this.projectMessage.set(valid ? `Loaded ${this.project().name}.${translation?.diagnostics.length
        ? ` ${translation.diagnostics.length} translation notes (see console).` : ''}` : 'That project file is invalid or unsupported.');
    } catch (error) {
      console.warn(`Could not read project file "${file.name}".`, error);
      this.projectMessage.set(error instanceof Error ? `Could not import: ${error.message}` : 'Could not read that project file.');
    }
  }
}
