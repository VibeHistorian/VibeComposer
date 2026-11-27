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

// Probe rendered hit regions, then leave the actual gesture to the caller.
function arrangementPoint(canvas, key) {
  const bounds = canvas.getBoundingClientRect();
  const left = Math.max(0, bounds.left), top = Math.max(0, bounds.top);
  const right = Math.min(innerWidth, bounds.right), bottom = Math.min(innerHeight, bounds.bottom);
  const matches = (x, y) => {
    canvas.dispatchEvent(new PointerEvent('pointermove', { clientX: x, clientY: y, bubbles: true }));
    return canvas.dataset.hoverTarget === key;
  };
  try {
    for (let y = Math.ceil(top) + 3; y < bottom - 2; y += 6) {
      for (let x = Math.ceil(left) + 3; x < right - 2; x += 6) {
        if (matches(x, y) && matches(x - 2, y - 2) && matches(x + 2, y + 2)
          && document.elementFromPoint(x, y) === canvas) return { x, y };
      }
    }
    throw new Error('Arrangement target is outside the visible canvas or missing: ' + key);
  } finally {
    canvas.dispatchEvent(new PointerEvent('pointerleave'));
  }
}

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

module.exports = { readWorkspace, clearWorkspace, readSessionProject, readSetting, writeSetting, arrangementPoint: arrangementPoint.toString(),
  observeSettings: `const storedSetting = (${observeSettings})();` };
