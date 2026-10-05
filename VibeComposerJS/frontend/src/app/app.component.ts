import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { getAppInfo } from './core/wails/wails-api';
import type { AppInfo } from './core/wails/wails-api';
import { ProjectService } from './core/project/project.service';

@Component({
  selector: 'vc-root',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.component.html',
})
export class AppComponent implements OnInit {
  readonly appInfo = signal<AppInfo | null>(null);
  private readonly projectService = inject(ProjectService);
  readonly project = this.projectService.project;

  async ngOnInit(): Promise<void> {
    try {
      this.appInfo.set(await getAppInfo());
    } catch (error: unknown) {
      console.error('Could not connect to the Wails application bridge.', error);
    }
  }
}
