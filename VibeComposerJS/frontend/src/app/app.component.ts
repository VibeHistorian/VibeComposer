import { ChangeDetectionStrategy, Component, inject, signal, viewChild } from '@angular/core';
import { Dialog, DialogModule } from '@angular/cdk/dialog';
import { HistoryDialogComponent } from './shared/history-dialog.component';
import { ProjectService } from './core/project/project.service';
import { SettingsDialogComponent } from './features/settings/settings-dialog.component';
import { TransportDockComponent } from './shared/transport-dock.component';
import { WorkspaceCanvasComponent } from './shared/workspace-canvas.component';
import { WorkspaceUiService } from './shared/workspace-ui.service';
import { translateJavaConfig } from './core/project/java-config-translator';
import { isEditingText } from './shared/keyboard-shortcuts';

@Component({
  selector: 'vc-root',
  imports: [DialogModule, TransportDockComponent, WorkspaceCanvasComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.component.html',
  host: { '(document:keydown)': 'historyKeydown($event)' },
})
export class AppComponent {
  readonly projectMessage = signal('');
  readonly projects = inject(ProjectService);
  readonly workspaceUi = inject(WorkspaceUiService);
  readonly project = this.projects.project;
  private readonly dialogs = inject(Dialog);
  private readonly workspace = viewChild(WorkspaceCanvasComponent);

  historyKeydown(event: KeyboardEvent): void {
    if (event.defaultPrevented || event.isComposing || event.altKey || !(event.ctrlKey || event.metaKey)
      || isEditingText(event)) return;
    const key = event.key.toLowerCase();
    if (key !== 'z' && key !== 'y') return;

    // Phrase history owns the shortcut for the entire editing session, even when empty.
    const workspace = this.workspace();
    const history = workspace?.editing() ? workspace.editor() : this.projects;
    if (key === 'y' || event.shiftKey) history?.redo();
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
    const objectUrl = URL.createObjectURL(blob);
    const link = document.createElement('a');
    const fileName = project.name.replace(/[<>:"/\\|?*\u0000-\u001f]/g, '-').trim().slice(0, 80) || 'VibeComposer';
    link.href = objectUrl;
    link.download = `${fileName}.vibecomposer.json`;
    document.body.append(link);
    link.click();
    link.remove();
    window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1000);
    this.projectMessage.set('Project settings exported.');
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
