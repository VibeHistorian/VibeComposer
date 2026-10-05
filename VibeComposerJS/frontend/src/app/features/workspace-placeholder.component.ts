import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';

@Component({
  selector: 'vc-workspace-placeholder',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="placeholder">
      <p class="eyebrow">WORKSPACE</p>
      <h1>{{ title }}</h1>
      <p>{{ description }}</p>
      <a routerLink="/create">Return to Create</a>
    </main>
  `,
  styles: [`
    :host { display: block; }
    .placeholder { min-height: 62vh; display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center; }
    .eyebrow { color: var(--accent); font: 9px 'DM Mono', monospace; letter-spacing: .12em; }
    h1 { margin: 8px 0; font-size: 38px; font-weight: 500; letter-spacing: -.06em; }
    p:not(.eyebrow) { color: var(--muted); font-size: 13px; }
    a { margin-top: 17px; color: var(--accent); font-size: 12px; text-decoration: none; }
  `],
})
export class WorkspacePlaceholderComponent {
  private readonly route = inject(ActivatedRoute);
  readonly title = this.route.snapshot.data['title'] as string;
  readonly description = this.route.snapshot.data['description'] as string;
}
