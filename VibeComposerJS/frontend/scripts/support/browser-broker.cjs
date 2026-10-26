const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { EventEmitter } = require('node:events');

const brokerBrowser = 'vibecomposer-protected-browser';
const connectionFile = path.join(process.env.LOCALAPPDATA || path.join(os.homedir(), 'AppData', 'Local'),
  'VibeComposerBrowserLauncher', 'connection.json');

function launchBrokerBrowser(args) {
  const child = new EventEmitter();
  child.stderr = new EventEmitter();
  child.exitCode = child.signalCode = null;
  child.testStderr = '';
  child.testBroker = true;
  child.on('error', error => { child.testLaunchError = error; });
  let connection;
  let id;
  let poll;
  let closing = false;
  child.brokerRequest = async (route, body, timeout = 22000) => {
    const response = await fetch(connection.url + route, {
      method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + connection.token },
      body: JSON.stringify(body), signal: AbortSignal.timeout(timeout),
    });
    if (!response.ok) throw new Error(`Browser broker ${route} (${response.status}): ${await response.text()}`);
    return response.json();
  };
  child.testBrokerReady = (async () => {
    try {
      connection = JSON.parse(fs.readFileSync(connectionFile, 'utf8'));
      if (connection.url !== 'http://127.0.0.1:43187' || !/^[a-f0-9]{64}$/.test(connection.token)) {
        throw new Error('Invalid protected browser connection file');
      }
      ({ id } = await child.brokerRequest('/launch', { args }));
      const check = async () => {
        if (closing) return;
        try {
          const status = await child.brokerRequest('/status', { id }, 3000);
          child.testStderr = status.stderr;
          for (const event of status.events ?? []) {
            child.testBrokerSocket?.dispatchEvent(new MessageEvent('message', { data: JSON.stringify(event) }));
          }
          if (status.exited) throw new Error('Chromium exited: ' + status.stderr);
          if (!closing) poll = setTimeout(check, 500);
        } catch (error) {
          if (!closing) { child.exitCode = 1; child.emit('error', error); child.emit('exit', 1, null); }
        }
      };
      poll = setTimeout(check, 500);
      return id;
    } catch (error) {
      error.message = 'Start the protected BrowserLauncher.exe serve outside the sandbox, then run tests inside the sandbox with VIBE_BROWSER_BROKER=1.\n' + error.message;
      child.testLaunchError = error;
      child.emit('error', error);
      throw error;
    }
  })();
  // Result-posting suites do not explicitly await a protocol connection.
  child.testBrokerReady.catch(() => {});
  child.closeBroker = async () => {
    closing = true;
    clearTimeout(poll);
    try { await child.testBrokerReady; } catch { return; }
    await child.brokerRequest('/close', { id });
    child.exitCode = 0;
  };
  return child;
}

async function connectToBrokerPage(child) {
  const id = await child.testBrokerReady;
  const socket = new EventTarget();
  socket.close = () => { child.testBrokerSocket = undefined; };
  child.testBrokerSocket = socket;
  return {
    socket,
    command: (method, params = {}) => child.brokerRequest('/command', { id, method, params }),
  };
}

async function readBrowserDownload(child, name) {
  if (!name || /[\\/:]/.test(name) || name === '.' || name === '..') throw new Error('Invalid browser download name');
  for (let i = 0; i < 100; i++) {
    try {
      if (child.testBroker) {
        const id = await child.testBrokerReady;
        const { data } = await child.brokerRequest('/download', { id, name });
        return Buffer.from(data, 'base64');
      }
      return fs.readFileSync(require('node:path').join(child.testProfile, 'downloads', name));
    } catch (error) {
      if (child.testBroker ? !error.message.includes('(404)') : error.code !== 'ENOENT') throw error;
    }
    await new Promise(resolve => setTimeout(resolve, 50));
  }
  throw new Error('Browser download did not complete: ' + name);
}

module.exports = { brokerBrowser, connectionFile, launchBrokerBrowser, connectToBrokerPage, readBrowserDownload };
