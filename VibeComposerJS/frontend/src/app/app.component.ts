import { ChangeDetectionStrategy, Component, inject, signal, viewChild } from '@angular/core';
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
      const bytes = new Uint8Array(generateCompositionMidi(project, this.midi.drumMapping()));
      downloadProjectFile(new Blob([bytes], { type: 'audio/midi' }), exportFilename(project, this.preferences.exportNameFields()), 'mid');
      this.projectMessage.set('MIDI exported.');
    } catch (error) {
      this.projectMessage.set(error instanceof Error ? `Could not export MIDI: ${error.message}` : 'Could not export MIDI.');
    }
  }

  async exportAudio(): Promise<void> {
    if (this.audioExport.busy()) return;
    const project = this.project();
    this.projectMessage.set('');
    try {
      const blob = await this.audioExport.export(project);
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
