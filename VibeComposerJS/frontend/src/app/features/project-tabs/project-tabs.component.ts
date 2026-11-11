import { afterRenderEffect, ChangeDetectionStrategy, Component, ElementRef, inject, input, signal, viewChild } from '@angular/core';
import { ProjectService } from '../../core/project/project.service';
import { UiPreferencesService } from '../../core/ui/ui-preferences.service';
import { ContextMenuComponent, type ContextMenuAction } from '../../shared/context-menu.component';
import { downloadProjectFile } from '../../core/project/download-file';
import { exportFilename } from '../../core/project/export-filename';
import { formatProjectJson } from '../../core/project/project-json';

@Component({
  selector: 'vc-project-tabs',
  imports: [ContextMenuComponent],
  templateUrl: './project-tabs.component.html',
  styleUrl: './project-tabs.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProjectTabsComponent {
  readonly projects = inject(ProjectService);
  readonly preferences = inject(UiPreferencesService);
  readonly disabled = input(false);
  readonly renamingId = signal<string | null>(null);
  readonly draftName = signal('');
  private readonly nameInput = viewChild<ElementRef<HTMLInputElement>>('nameInput');
  private readonly focusName = afterRenderEffect(() => {
    const input = this.nameInput()?.nativeElement;
    if (input) { input.focus(); input.select(); }
  });
  readonly items = [
    { id: 'rename', label: 'Rename Project' },
    { id: 'remove', label: 'Remove Project' },
    { id: 'export', label: 'Export Project' },
    { id: 'duplicate', label: 'Duplicate Project' },
  ];
  activate(id: string): void {
    if (this.disabled()) return;
    if (this.projects.activeProjectTabId() === id) this.rename(id);
    else this.projects.switchProjectTab(id);
  }
  rename(id: string): void {
    const project = this.projects.projectForTab(id);
    if (!project || this.disabled()) return;
    this.draftName.set(project.name);
    this.renamingId.set(id);
  }
  finishRename(): void {
    const id = this.renamingId();
    if (!id) return;
    this.renamingId.set(null);
    if (!this.disabled()) this.projects.renameProjectTab(id, this.draftName());
  }
  cancelRename(event: Event): void {
    event.preventDefault(); event.stopPropagation();
    this.renamingId.set(null);
  }
  action(id: string, action: ContextMenuAction): void {
    if (this.disabled()) return;
    if (action.id === 'rename') this.rename(id);
    else if (action.id === 'remove') this.projects.removeProjectTab(id);
    else if (action.id === 'duplicate') this.projects.duplicateProjectTab(id);
    else if (action.id === 'export') {
      const project = this.projects.projectForTab(id);
      if (project) downloadProjectFile(new Blob([formatProjectJson(project)], { type: 'application/json' }),
        exportFilename(project, this.preferences.exportNameFields()), 'vibe.json');
    }
  }
}
