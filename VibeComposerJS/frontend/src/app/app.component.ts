import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { getAppInfo } from './core/wails/wails-api';
import type { AppInfo } from './core/wails/wails-api';
import { ProjectService } from './core/project/project.service';
import { TransportDockComponent } from './shared/transport-dock.component';
import { WorkspaceCanvasComponent } from './shared/workspace-canvas.component';

@Component({
  selector: 'vc-root',
  imports: [TransportDockComponent, WorkspaceCanvasComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.component.html',
})
export class AppComponent implements OnInit {
  readonly appInfo = signal<AppInfo | null>(null);
  readonly projectMessage = signal('');
  private readonly projectService = inject(ProjectService);
  readonly project = this.projectService.project;

  async ngOnInit(): Promise<void> {
    try {
      this.appInfo.set(await getAppInfo());
    } catch (error: unknown) {
      console.error('Could not connect to the Wails application bridge.', error);
    }
  }

  exportProject(): void {
    const project = this.project();
    const blob = new Blob([this.projectService.exportProjectJson()], { type: 'application/json' });
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
      const valid = this.projectService.importProjectJson(await file.text());
      this.projectMessage.set(valid ? `Loaded ${this.project().name}.` : 'That project file is invalid or unsupported.');
    } catch {
      this.projectMessage.set('Could not read that project file.');
    }
  }
}
