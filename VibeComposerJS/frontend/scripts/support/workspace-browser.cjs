// These functions run in the production browser, through a script or CDP.
function storageRequest(mode, perform, storeName = 'workspaces') {
  return new Promise((resolve, reject) => {
    const open = indexedDB.open('vibecomposer', 2);
    open.onupgradeneeded = event => {
      if (event.oldVersion < 1) open.result.createObjectStore('workspaces');
      open.result.createObjectStore('settings');
    };
    open.onerror = () => reject(open.error);
    open.onsuccess = () => {
      const database = open.result;
      const transaction = database.transaction(storeName, mode);
      const request = perform(transaction.objectStore(storeName));
      transaction.oncomplete = () => { database.close(); resolve(request.result); };
      transaction.onabort = () => { database.close(); reject(transaction.error); };
    };
  });
}

const readWorkspace = `() => (${storageRequest})('readonly', store => store.get('current'))`;
const clearWorkspace = `() => {
  dispatchEvent(new Event('pagehide'));
  return (${storageRequest})('readwrite', store => store.delete('current'));
}`;
const readSessionProject = `() => {
  const project = JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));
  if (project) delete project.workspaceRecovery;
  return project;
}`;

const readSetting = `key => (${storageRequest})('readonly', store => store.get(key), 'settings')`;
const writeSetting = `(key, value) => (${storageRequest})('readwrite', store => store.put(value, key), 'settings')`;

// Workflow assertions often compare snapshots synchronously. Observe the actual
// committed IndexedDB reads/writes, including the initial reads after reload.
function observeSettings() {
  const values = new Map();
  const get = IDBObjectStore.prototype.get;
  const put = IDBObjectStore.prototype.put;
  IDBObjectStore.prototype.get = function(key) {
    const request = get.call(this, key);
    if (this.name === 'settings') {
      this.transaction.addEventListener('complete', () => values.set(key, JSON.stringify(request.result)));
    }
    return request;
  };
  IDBObjectStore.prototype.put = function(value, key) {
    const request = put.call(this, value, key);
    if (this.name === 'settings') {
      this.transaction.addEventListener('complete', () => values.set(key, JSON.stringify(value)));
    }
    return request;
  };
  return key => values.get(key) ?? null;
}

module.exports = { readWorkspace, clearWorkspace, readSessionProject, readSetting, writeSetting,
  observeSettings: `const storedSetting = (${observeSettings})();` };
