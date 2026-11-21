import { Injectable } from '@angular/core';

const DATABASE = 'vibecomposer';
const STORE = 'workspaces';
const KEY = 'current';
const LEGACY_KEY = 'vibecomposer.project-tabs.v1';

function openDatabase(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = globalThis.indexedDB.open(DATABASE, 1);
    let blocked = false;
    request.onupgradeneeded = () => request.result.createObjectStore(STORE);
    request.onsuccess = () => {
      const database = request.result;
      if (blocked) database.close();
      else {
        database.onversionchange = () => database.close();
        resolve(database);
      }
    };
    request.onerror = () => reject(request.error);
    request.onblocked = () => {
      blocked = true;
      reject(new Error('Another window is blocking the workspace database.'));
    };
  });
}

@Injectable({ providedIn: 'root' })
export class WorkspaceStorage {
  private database?: IDBDatabase;
  private initialWorkspace: unknown;
  private legacySource?: string;

  async initialize(): Promise<void> {
    let database: IDBDatabase | undefined;
    try {
      database = await openDatabase();
      const connection = database;
      this.initialWorkspace = await new Promise<unknown>((resolve, reject) => {
        const transaction = connection.transaction(STORE);
        const request = transaction.objectStore(STORE).get(KEY);
        transaction.oncomplete = () => resolve(request.result);
        transaction.onabort = () => reject(transaction.error);
      });
      this.database = database;
      if (this.initialWorkspace !== undefined) return;
    } catch (error) {
      database?.close();
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
    const database = this.database;
    if (!database) throw new Error('The workspace database is unavailable.');
    await new Promise<void>((resolve, reject) => {
      const transaction = database.transaction(STORE, 'readwrite');
      transaction.objectStore(STORE).put(workspace, KEY);
      transaction.oncomplete = () => resolve();
      transaction.onabort = () => reject(transaction.error);
      transaction.commit?.();
    });
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
