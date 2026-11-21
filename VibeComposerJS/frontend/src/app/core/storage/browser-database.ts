import { Injectable } from '@angular/core';

export type BrowserStore = 'workspaces' | 'settings';

@Injectable({ providedIn: 'root' })
export class BrowserDatabase {
  private connection?: IDBDatabase;

  async initialize(): Promise<void> {
    try {
      this.connection = await new Promise<IDBDatabase>((resolve, reject) => {
        const request = globalThis.indexedDB.open('vibecomposer', 2);
        let blocked = false;
        request.onupgradeneeded = event => {
          if (event.oldVersion < 1) request.result.createObjectStore('workspaces');
          request.result.createObjectStore('settings');
        };
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
          reject(new Error('Another window is blocking the VibeComposer database.'));
        };
      });
    } catch (error) {
      console.warn('Could not open the VibeComposer database.', error);
    }
  }

  read(store: BrowserStore, key: string): Promise<unknown> {
    return this.transaction(store, 'readonly', records => records.get(key));
  }

  async write(store: BrowserStore, key: string, value: object): Promise<void> {
    await this.transaction(store, 'readwrite', records => records.put(value, key));
  }

  /** The read and resulting write are serialized with other windows' writes to this store. */
  update<T extends object>(store: BrowserStore, key: string, change: (value: unknown) => T): Promise<T> {
    return new Promise((resolve, reject) => {
      const transaction = this.start(store, 'readwrite');
      const records = transaction.objectStore(store);
      const request = records.get(key);
      let next: T;
      transaction.oncomplete = () => resolve(next);
      transaction.onabort = () => reject(transaction.error);
      request.onsuccess = () => {
        try {
          next = change(request.result);
          records.put(next, key);
          transaction.commit?.();
        } catch (error) {
          transaction.abort();
          reject(error);
        }
      };
    });
  }

  private start(store: BrowserStore, mode: IDBTransactionMode): IDBTransaction {
    if (!this.connection) throw new Error('The VibeComposer database is unavailable.');
    return this.connection.transaction(store, mode);
  }

  private transaction(store: BrowserStore, mode: IDBTransactionMode,
    perform: (records: IDBObjectStore) => IDBRequest): Promise<unknown> {
    return new Promise((resolve, reject) => {
      const transaction = this.start(store, mode);
      const request = perform(transaction.objectStore(store));
      transaction.oncomplete = () => resolve(request.result);
      transaction.onabort = () => reject(transaction.error);
      if (mode === 'readwrite') transaction.commit?.();
    });
  }
}
