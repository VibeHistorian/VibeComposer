import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { getAppInfo } from './core/wails/wails-api';
import type { AppInfo } from './core/wails/wails-api';
import { ProjectService } from './core/project/project.service';

@Component({
  selector: 'vc-root',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="app-frame">
      <header class="topbar">
        <a class="brand" routerLink="/create" aria-label="VibeComposer home">
          <span class="brand-mark" aria-hidden="true">V</span>
          <span>vibe<span class="brand-light">composer</span></span>
        </a>

        <nav class="workspace-nav" aria-label="Workspaces">
          <a routerLink="/create" routerLinkActive="active" ariaCurrentWhenActive="page">Create</a>
          <a routerLink="/arrange" routerLinkActive="active" ariaCurrentWhenActive="page">Arrange</a>
          <a routerLink="/edit" routerLinkActive="active" ariaCurrentWhenActive="page">Edit</a>
          <a routerLink="/mix" routerLinkActive="active" ariaCurrentWhenActive="page">Mix</a>
        </nav>

        <div class="header-summary">
          <span>{{ project().key }} {{ project().scale === 'major' ? 'major' : 'minor' }}</span>
          <span class="summary-divider" aria-hidden="true"></span>
          <span>{{ project().tempoBpm }} BPM</span>
          <span class="host-pill" [class.connected]="appInfo()">
            <span class="status-dot" aria-hidden="true"></span>
            {{ appInfo() ? 'DESKTOP' : 'BROWSER' }}
          </span>
        </div>
      </header>

      <router-outlet />

      <footer class="app-footer">
        <span>VIBECOMPOSER STUDIO <b>·</b> {{ project().name }}</span>
        <span>{{ appInfo() ? appInfo()!.version : 'SESSION SAVED IN THIS BROWSER' }}</span>
      </footer>
    </div>
  `,
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
