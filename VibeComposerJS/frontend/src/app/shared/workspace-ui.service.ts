import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class WorkspaceUiService {
  private readonly expanded = signal(false);
  readonly toolsOpen = this.expanded.asReadonly();

  openTools(): void {
    this.expanded.set(true);
  }

  toggleTools(): void {
    this.expanded.update((open) => !open);
  }
}
