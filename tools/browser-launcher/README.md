# Protected browser launcher

This Windows launcher separates headless Chromium from sandboxed Node tests.
Only the installed native executable is pre-approved outside the Codex sandbox.
It does not run Node, pnpm, PowerShell, repository scripts, or caller-selected
executables. Source in this directory is editable for review; the installed copy
under `C:\Program Files\VibeComposerBrowserLauncher` requires Windows administrator
rights to replace, delete, or change permissions. Running it requires no admin
rights, and it refuses an elevated administrator token.

## Build and install

From this directory, with Go installed:

```powershell
$env:GOCACHE = "$PWD\build\go-cache"
go test ./...
go build -trimpath -o build/BrowserLauncher.exe .
Get-FileHash build/BrowserLauncher.exe -Algorithm SHA256
```

After reviewing the source and binary hash, run the following **once in an
administrator PowerShell 7**. Pass the exact hash printed above:

```powershell
./install.ps1 -ExpectedSha256 '<reviewed SHA256>'
```

The installer grants Administrators/SYSTEM full control and Users read/execute,
sets Administrators as owner, and removes inherited permissions on both the
installation directory and executable. It verifies the bytes before writing and
the installed hash afterward. Subsequent updates require the same admin install;
editing/rebuilding repository source never changes the approved executable.

Replace `%USERPROFILE%\.codex\rules\default.rules` (or `%CODEX_HOME%\rules\default.rules`
when using a custom Codex home) with the reviewed
[`default.rules`](default.rules), keeping a backup outside the active `rules/`
directory. Restart the agent host so it reloads rules. The replacement contains
one allow rule, for the installed launcher's `serve` command. Existing project
Git restrictions in `.codex/rules/git.rules` continue to apply. Reads, builds,
functional tests and browser test JavaScript use normal workspace sandbox access;
they receive no outside-sandbox allowances. Builds that need downloads or cache
writes outside permitted roots can still fail and need explicit approval or
workspace-local cache configuration.

Codex `allow` rules authorize commands outside its sandbox; they do not confine
code inside approved scripts. See [official rules documentation](https://learn.chatgpt.com/docs/agent-configuration/rules).
`AGENTS.md` is guidance, not the security boundary. The boundary is the host
sandbox for Node plus the Windows ACL on the installed executable. Rules do not
grant Windows administrator elevation. The host must support launching the broker
outside the agent sandbox and local communication between it and sandboxed tests.
An effective `approval_policy = "never"` can prevent the outside-sandbox launch;
the IDE/host's effective approval settings, rather than just this repository's
config, determine that behavior.

## Run

Start the installed broker from a normal terminal, or through the host's narrow
outside-sandbox approval:

```powershell
& 'C:\Program Files\VibeComposerBrowserLauncher\BrowserLauncher.exe' serve
```

Keep that foreground command alive while running tests. In the **sandboxed**
frontend terminal:

```powershell
$env:VIBE_BROWSER_BROKER = '1'
pnpm run test:browser
```

The environment setting opts into the broker; there is no automatic fallback to
unsandboxed Node or direct Chromium execution if the broker fails. Existing
direct browser execution remains available for regular user-terminal use when the
environment variable is absent. The documented aggregate runs suites sequentially.
For multiple focused files, use `node --test --test-concurrency=1 ...`.

For a focused integration/security check, run from the repository root inside
the sandbox with the broker already running:

```powershell
node --test tools/browser-launcher/verify.cjs
```

Stop the broker with Ctrl+C. It also stops after 30 minutes. Each browser session
expires after three minutes; explicit suite cleanup normally closes it much sooner.
A Windows kill-on-close job contains the entire process tree, so stopping/killing
the broker also stops its Chromium descendants. Interrupted sessions can leave
disposable profile files under the runtime directory; remove those only when no
broker is running. No executable code is loaded from that directory.

## Interface and restrictions

- Fixed executable choices: Chrome/Edge in their standard `Program Files` paths.
- Fixed loopback broker address: `127.0.0.1:43187`. A per-run random capability
  token is stored in `%LOCALAPPDATA%\VibeComposerBrowserLauncher\connection.json`.
  The launcher resolves the current user's folders through the Windows known-folder
  API; it does not trust environment overrides for its own storage. The Node client
  locates the file using `LOCALAPPDATA`, with a home-directory fallback.
  The client rejects a connection file pointing anywhere else. Browser-origin
  requests, unknown JSON fields, extra command-line arguments, and unauthenticated
  requests are rejected. No CORS access is enabled.
- Browser arguments are allowlisted. Caller profile paths are ignored, debug-port
  requests are replaced with private inherited pipes, and sandbox-disabling,
  extension, arbitrary executable and proxy options are rejected.
- Profiles and downloads are created by the broker outside repository-editable
  storage, separately for each launch. Repository `Preferences` are never loaded.
  Download inspection returns only a named regular file from that session's
  downloads directory; tests save their own screenshots inside `.angular/`.
- The broker exposes the page commands used by these suites: evaluation,
  screenshots, reloads, input, and viewport/touch emulation. It does not expose a
  raw debugging socket, arbitrary browser sessions, host file-upload commands,
  filesystem IO, browser-level commands, or caller-selected download directories.
- Browser-wide request interception permits only the chosen fixture origin and
  inline/blob resources, including requests from dedicated workers (which do not
  implement their own Fetch protocol domain). Supported worker/iframe targets
  receive child-target controls before resuming; other new targets are closed.
  A broker-owned rejecting proxy covers
  HTTP(S)/WebSocket destinations outside the fixture origin, including other
  loopback ports. QUIC/non-proxied WebRTC UDP and external DNS resolution are
  disabled as additional restrictions. Chromium's own sandbox stays enabled.
- No shell or repository code runs in the broker. Chromium gets a small fixed
  environment and a working directory under its installation, not the repo.

This is a reduced-capability browser bridge, not a VM for hostile web content.
Chromium and Windows runtime resources remain trusted, and a browser vulnerability
could cross this boundary. The capability token is available to sandboxed tests
by design; anyone who can read it can exercise the same restricted interface.
Changing the repository test adapter cannot grant additional broker capabilities.
Codex workspace access also is not automatically a repo-only read policy: protect
unrelated secrets separately in the host's permission profile if needed.

User-specific storage resolves automatically for the account running the broker.
The `%NAME%` notation above is Windows environment-variable syntax; in PowerShell,
use `$env:LOCALAPPDATA`, `$env:USERPROFILE`, or `$env:CODEX_HOME` instead.
Changing the fixed address, system installation paths, executable choices, command
allowlist, or protocol methods requires reviewing, rebuilding and reinstalling the
protected executable. Approval rules retain the exact installed executable path.
