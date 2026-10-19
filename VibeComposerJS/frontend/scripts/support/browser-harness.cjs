const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');
const { spawn } = require('node:child_process');

const frontend = path.resolve(__dirname, '../..');
const profileRoot = path.join(frontend, '.angular');

function findBrowser() {
  return process.env.OVERVIEW_TEST_BROWSER || [
    'C:/Program Files/Google/Chrome/Application/chrome.exe',
    'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    '/usr/bin/chromium', '/usr/bin/chromium-browser', '/usr/bin/google-chrome',
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
  ].find(file => fs.existsSync(file));
}

function requireProductionBuild(root = path.join(frontend, 'dist/browser')) {
  if (!fs.existsSync(path.join(root, 'index.html'))) {
    throw new Error('Production browser tests require dist/browser. Run npm run build from frontend/ first.');
  }
  return root;
}

// Fixture pages supply html alone; production pages also serve bundled assets.
function createBrowserServer({ root, html, resolve, reject }) {
  if (root) requireProductionBuild(root);
  return http.createServer(async (request, response) => {
    try {
      const pathname = new URL(request.url, 'http://localhost').pathname;
      if (pathname === '/result' && resolve) {
        let body = '';
        for await (const chunk of request) body += chunk;
        const result = JSON.parse(body);
        response.end('OK');
        resolve(result);
        return;
      }
      if (pathname === '/' && html !== undefined) {
        response.setHeader('Content-Type', 'text/html');
        response.end(html);
        return;
      }
      const file = root && path.resolve(root, '.' + decodeURIComponent(pathname === '/' ? '/index.html' : pathname));
      const relative = file && path.relative(root, file);
      if (!file || relative === '..' || relative.startsWith('..' + path.sep) || path.isAbsolute(relative)
        || !fs.existsSync(file) || !fs.statSync(file).isFile()) {
        response.writeHead(404); response.end(); return;
      }
      const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css',
        '.json': 'application/json', '.wasm': 'application/wasm', '.svg': 'image/svg+xml' };
      response.setHeader('Content-Type', types[path.extname(file)] || 'application/octet-stream');
      const stream = fs.createReadStream(file);
      stream.on('error', error => { response.destroy(error); reject?.(error); });
      stream.pipe(response);
    } catch (error) {
      response.writeHead(400); response.end(String(error)); reject?.(error);
    }
  });
}

async function listen(server) {
  await new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(0, '127.0.0.1', () => { server.off('error', reject); resolve(); });
  });
}

function launchBrowser(browser, args, options = {}) {
  fs.mkdirSync(profileRoot, { recursive: true });
  const supplied = args.find(arg => arg.startsWith('--user-data-dir='));
  const profile = supplied ? supplied.slice('--user-data-dir='.length)
    : fs.mkdtempSync(path.join(profileRoot, 'browser-harness-'));
  const child = spawn(browser, [...args, ...(!supplied ? ['--user-data-dir=' + profile] : [])],
    { stdio: ['ignore', 'ignore', 'pipe'], ...options });
  child.testProfile = profile;
  child.on('error', error => { child.testLaunchError = error; });
  return child;
}

async function connectToPage(child) {
  const deadline = Date.now() + 10000;
  let target;
  while (!target && Date.now() < deadline) {
    if (child.testLaunchError) throw child.testLaunchError;
    if (child.exitCode !== null || child.signalCode !== null) throw new Error('Chromium exited before debugging connected');
    try {
      const port = Number(fs.readFileSync(path.join(child.testProfile, 'DevToolsActivePort'), 'utf8').split('\n')[0]);
      target = (await (await fetch(`http://127.0.0.1:${port}/json/list`, {
        signal: AbortSignal.timeout(1000),
      })).json()).find(item => item.type === 'page');
    } catch (error) {
      if (!['ENOENT', 'EBUSY', 'EPERM'].includes(error.code) && !(error instanceof TypeError)
        && error.name !== 'TimeoutError') throw error;
    }
    if (!target) await new Promise(resolve => setTimeout(resolve, 50));
  }
  if (!target) throw new Error('Chromium debugging page unavailable after 10000ms');
  const socket = new WebSocket(target.webSocketDebuggerUrl);
  try {
    await new Promise((resolve, reject) => {
      const timer = setTimeout(() => { cleanup(); reject(new Error('Chromium debugging socket timed out')); }, 5000);
      const cleanup = () => { clearTimeout(timer); socket.removeEventListener('open', open); socket.removeEventListener('error', error); };
      const open = () => { cleanup(); resolve(); };
      const error = () => { cleanup(); reject(new Error('Chromium debugging socket failed')); };
      socket.addEventListener('open', open); socket.addEventListener('error', error);
    });
  } catch (error) { socket.close(); throw error; }
  let commandId = 0;
  const command = (method, params = {}) => new Promise((resolve, reject) => {
    const id = ++commandId;
    const cleanup = () => {
      clearTimeout(timer); socket.removeEventListener('message', message); socket.removeEventListener('close', closed);
    };
    const timer = setTimeout(() => { cleanup(); reject(new Error('CDP timeout: ' + method)); }, 20000);
    const closed = () => { cleanup(); reject(new Error('Chromium debugging socket closed: ' + method)); };
    const message = event => {
      const result = JSON.parse(event.data);
      if (result.id !== id) return;
      cleanup(); result.error ? reject(new Error(JSON.stringify(result.error))) : resolve(result.result);
    };
    socket.addEventListener('message', message); socket.addEventListener('close', closed);
    try { socket.send(JSON.stringify({ id, method, params })); }
    catch (error) { cleanup(); reject(error); }
  });
  return { socket, command };
}

function watchBrowser(child, reject, timeoutMs) {
  let errors = '';
  const onData = chunk => { errors = (errors + chunk).slice(-4000); };
  const onError = error => reject(error);
  const onExit = (code, signal) => reject(new Error(`Chromium exited (${code}, ${signal}): ${errors}`));
  child.stderr.on('data', onData);
  child.on('error', onError);
  child.on('exit', onExit);
  const timer = setTimeout(() => reject(new Error(`Chromium timed out after ${timeoutMs}ms: ${errors}`)), timeoutMs);
  return {
    stderr: () => errors,
    stop() {
      clearTimeout(timer);
      child.stderr.off('data', onData); child.off('error', onError); child.off('exit', onExit);
    },
  };
}

async function closeBrowser({ child, server, socket, watchdog }) {
  socket?.close();
  watchdog?.stop();
  const closed = new Promise((resolve, reject) => {
    server.close(error => error ? reject(error) : resolve());
    server.closeAllConnections();
  });
  if (child.exitCode === null && child.signalCode === null && child.pid) {
    const exited = new Promise(resolve => child.once('exit', resolve));
    child.kill();
    await exited;
  }
  await closed;
  const profile = path.resolve(child.testProfile);
  const relative = path.relative(profileRoot, profile);
  if (!relative || relative === '..' || relative.startsWith('..' + path.sep) || path.isAbsolute(relative)) {
    throw new Error('Browser profile outside test workspace: ' + profile);
  }
  await fs.promises.rm(profile, { recursive: true, force: true, maxRetries: 10, retryDelay: 200 });
}

module.exports = { findBrowser, requireProductionBuild, createBrowserServer, listen, launchBrowser, connectToPage, watchBrowser, closeBrowser };
