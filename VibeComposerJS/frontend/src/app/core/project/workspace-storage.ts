import { inject, Injectable } from '@angular/core';
import { BrowserDatabase } from '../storage/browser-database';

const KEY = 'current';
const LEGACY_KEY = 'vibecomposer.project-tabs.v1';

@Injectable({ providedIn: 'root' })
export class WorkspaceStorage {
  private readonly database = inject(BrowserDatabase);
  private initialWorkspace: unknown;
  private legacySource?: string;

  async initialize(): Promise<void> {
    try {
      this.initialWorkspace = await this.database.read('workspaces', KEY);
      if (this.initialWorkspace !== undefined) return;
    } catch (error) {
      console.warn('Could not read the workspace database.', error);
    }
    try {
      const raw = globalThis.localStorage?.getItem(LEGACY_KEY);
      if (raw) {
        this.initialWorkspace = JSON.parse(raw);
        this.legacySource = raw;
      }
    } catch { /* The session copy can still be restored. */ }
  }

  takeInitialWorkspace(): unknown {
    const workspace = this.initialWorkspace;
    this.initialWorkspace = undefined;
    return workspace;
  }

  async write(workspace: object): Promise<void> {
    await this.database.write('workspaces', KEY, workspace);
    if (this.legacySource !== undefined) {
      try {
        if (globalThis.localStorage.getItem(LEGACY_KEY) === this.legacySource) {
          globalThis.localStorage.removeItem(LEGACY_KEY);
        }
      } catch { /* The migrated IndexedDB copy is already committed. */ }
      this.legacySource = undefined;
    }
  }
}
