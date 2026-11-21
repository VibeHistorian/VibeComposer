// These functions run in the production browser, through a script or CDP.
function workspaceRequest(mode, perform) {
  return new Promise((resolve, reject) => {
    const open = indexedDB.open('vibecomposer', 1);
    open.onupgradeneeded = () => open.result.createObjectStore('workspaces');
    open.onerror = () => reject(open.error);
    open.onsuccess = () => {
      const database = open.result;
      const transaction = database.transaction('workspaces', mode);
      const request = perform(transaction.objectStore('workspaces'));
      transaction.oncomplete = () => { database.close(); resolve(request.result); };
      transaction.onabort = () => { database.close(); reject(transaction.error); };
    };
  });
}

const readWorkspace = `() => (${workspaceRequest})('readonly', store => store.get('current'))`;
const clearWorkspace = `() => {
  dispatchEvent(new Event('pagehide'));
  return (${workspaceRequest})('readwrite', store => store.delete('current'));
}`;
const readSessionProject = `() => {
  const project = JSON.parse(sessionStorage.getItem('vibecomposer.project.v12'));
  if (project) delete project.workspaceRecovery;
  return project;
}`;

module.exports = { readWorkspace, clearWorkspace, readSessionProject };
