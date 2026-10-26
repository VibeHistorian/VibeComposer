// BrowserLauncher is a deliberately narrow bridge out of the agent sandbox.
// It never executes repository code, shells, or a caller-selected executable.
package main

import (
	"bufio"
	"bytes"
	"crypto/rand"
	"crypto/subtle"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"syscall"
	"time"
	"unsafe"
)

const address = "127.0.0.1:43187"

var runtimeRoot, connectionFile, userProfile string

const maxBody = 16 << 20

var browsers = []string{
	`C:\Program Files\Google\Chrome\Application\chrome.exe`,
	`C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe`,
}

var allowedFlags = map[string]bool{
	"--headless": true, "--disable-gpu": true, "--no-first-run": true,
	"--disable-background-networking": true, "--disable-component-update": true,
	"--disable-sync": true, "--disable-background-timer-throttling": true,
	"--disable-renderer-backgrounding": true, "--no-default-browser-check": true,
	"--autoplay-policy=no-user-gesture-required": true,
	// The caller's port is never opened. The broker uses inherited private pipes.
	"--remote-debugging-port=0": true,
}
var windowFlag = regexp.MustCompile(`^--window-size=([0-9]{3,4}),([0-9]{3,4})$`)
var scaleFlag = regexp.MustCompile(`^--force-device-scale-factor=(1|1\.25|1\.5)$`)

// These commands operate only on the broker's attached page. No Target, Browser,
// IO, DOM file-upload, navigation, arbitrary-session, or download-path commands.
var allowedMethods = map[string]bool{
	"Runtime.evaluate": true, "Page.enable": true, "Page.reload": true,
	"Page.captureScreenshot": true, "Input.dispatchMouseEvent": true,
	"Input.dispatchKeyEvent": true, "Input.dispatchTouchEvent": true,
	"Emulation.setDeviceMetricsOverride": true, "Emulation.setTouchEmulationEnabled": true,
	"Emulation.setPageScaleFactor": true,
}

func randomID() string {
	b := make([]byte, 32)
	if _, err := rand.Read(b); err != nil {
		panic(err)
	}
	return hex.EncodeToString(b)
}

func validateArgs(args []string) ([]string, string, error) {
	if len(args) > 24 {
		return nil, "", errors.New("too many browser arguments")
	}
	var flags []string
	var origin string
	for _, arg := range args {
		switch {
		case allowedFlags[arg]:
			if arg != "--remote-debugging-port=0" && arg != "--headless" {
				flags = append(flags, arg)
			}
		case windowFlag.MatchString(arg):
			m := windowFlag.FindStringSubmatch(arg)
			w, _ := strconv.Atoi(m[1])
			h, _ := strconv.Atoi(m[2])
			if w < 700 || w > 3840 || h < 300 || h > 2160 {
				return nil, "", errors.New("viewport outside supported bounds")
			}
			flags = append(flags, arg)
		case scaleFlag.MatchString(arg):
			flags = append(flags, arg)
		case strings.HasPrefix(arg, "--user-data-dir="):
			// Never open the caller's profile or read its Preferences/extensions.
			// The client keeps this path solely for its own sandboxed artifacts.
		case strings.HasPrefix(arg, "http://127.0.0.1:"):
			u, err := url.Parse(arg)
			if err != nil || u.Scheme != "http" || u.Hostname() != "127.0.0.1" || u.User != nil || u.RawQuery != "" || u.Fragment != "" || (u.Path != "" && u.Path != "/") {
				return nil, "", errors.New("only a loopback fixture root URL is allowed")
			}
			port, err := strconv.Atoi(u.Port())
			if err != nil || port < 1024 || port > 65535 || u.Port() != strconv.Itoa(port) || u.Host == address || origin != "" {
				return nil, "", errors.New("invalid fixture port or duplicate URL")
			}
			origin = "http://" + u.Host
		default:
			return nil, "", fmt.Errorf("browser argument is not allowed: %s", arg)
		}
	}
	if origin == "" {
		return nil, "", errors.New("a loopback fixture URL is required")
	}
	return flags, origin, nil
}

func allowedRequest(raw, origin string) bool {
	if raw == "about:blank" || strings.HasPrefix(raw, "data:") || strings.HasPrefix(raw, "blob:"+origin+"/") {
		return true
	}
	u, err := url.Parse(raw)
	return err == nil && u.User == nil && u.Scheme == "http" && "http://"+u.Host == origin
}

// CDP uses NUL-delimited JSON over inherited Windows handles, not a debug port.
type protocol struct {
	writer  io.WriteCloser
	mu      sync.Mutex
	next    int
	pending map[int]chan message
	done    chan struct{}
	events  chan message
}
type message struct {
	ID      int             `json:"id"`
	Method  string          `json:"method"`
	Session string          `json:"sessionId"`
	Params  json.RawMessage `json:"params"`
	Result  json.RawMessage `json:"result"`
	Error   json.RawMessage `json:"error"`
}

func newProtocol(reader io.ReadCloser, writer io.WriteCloser) *protocol {
	p := &protocol{writer: writer, pending: map[int]chan message{}, done: make(chan struct{}), events: make(chan message, 256)}
	go func() {
		defer close(p.done)
		defer reader.Close()
		r := bufio.NewReader(reader)
		for {
			b, err := r.ReadBytes(0)
			if err != nil {
				return
			}
			var m message
			if len(b) > maxBody || json.Unmarshal(b[:len(b)-1], &m) != nil {
				return
			}
			if m.ID != 0 {
				p.mu.Lock()
				ch := p.pending[m.ID]
				p.mu.Unlock()
				if ch != nil {
					ch <- m
				}
			} else {
				select {
				case p.events <- m:
				case <-time.After(5 * time.Second):
					return
				}
			}
		}
	}()
	return p
}

func (p *protocol) call(method string, params any, session string) (json.RawMessage, error) {
	ch := make(chan message, 1)
	p.mu.Lock()
	p.next++
	id := p.next
	p.pending[id] = ch
	request := map[string]any{"id": id, "method": method, "params": params}
	if session != "" {
		request["sessionId"] = session
	}
	b, err := json.Marshal(request)
	if err == nil {
		_, err = p.writer.Write(append(b, 0))
	}
	p.mu.Unlock()
	defer func() { p.mu.Lock(); delete(p.pending, id); p.mu.Unlock() }()
	if err != nil {
		return nil, err
	}
	select {
	case m := <-ch:
		if len(m.Error) != 0 {
			return nil, fmt.Errorf("CDP %s: %s", method, m.Error)
		}
		return m.Result, nil
	case <-p.done:
		return nil, errors.New("Chromium debugging pipe closed")
	case <-time.After(20 * time.Second):
		return nil, fmt.Errorf("CDP timeout: %s", method)
	}
}

type session struct {
	cmd     *exec.Cmd
	cdp     *protocol
	page    string
	profile string
	origin  string
	done    chan struct{}
	once    sync.Once
	stderr  lockedBuffer
	eventMu sync.Mutex
	events  []message
	proxy   *http.Server
}
type lockedBuffer struct {
	mu   sync.Mutex
	data []byte
}

func (b *lockedBuffer) Write(p []byte) (int, error) {
	b.mu.Lock()
	defer b.mu.Unlock()
	b.data = append(b.data, p...)
	if len(b.data) > 4000 {
		b.data = b.data[len(b.data)-4000:]
	}
	return len(p), nil
}
func (b *lockedBuffer) String() string { b.mu.Lock(); defer b.mu.Unlock(); return string(b.data) }

func startBrowser(args []string) (_ *session, failure error) {
	flags, origin, err := validateArgs(args)
	if err != nil {
		return nil, err
	}
	var browser string
	for _, candidate := range browsers {
		if info, e := os.Stat(candidate); e == nil && info.Mode().IsRegular() {
			browser = candidate
			break
		}
	}
	if browser == "" {
		return nil, errors.New("install Chrome or Edge in Program Files")
	}
	profile, err := os.MkdirTemp(runtimeRoot, "profile-")
	if err != nil {
		return nil, err
	}
	s := &session{profile: profile, origin: origin, done: make(chan struct{})}
	defer func() {
		if failure != nil {
			s.close()
		}
	}()
	proxyListener, err := net.Listen("tcp4", "127.0.0.1:0")
	if err != nil {
		return nil, err
	}
	s.proxy = &http.Server{Handler: http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.Error(w, "external browser networking is disabled", http.StatusForbidden)
	}), ReadHeaderTimeout: 3 * time.Second, IdleTimeout: 3 * time.Second}
	go func() { _ = s.proxy.Serve(proxyListener) }()
	if err = os.MkdirAll(filepath.Join(profile, "Default"), 0700); err != nil {
		return nil, err
	}
	if err = os.Mkdir(filepath.Join(profile, "downloads"), 0700); err != nil {
		return nil, err
	}
	prefs, _ := json.Marshal(map[string]any{"download": map[string]any{"default_directory": filepath.Join(profile, "downloads"), "prompt_for_download": false}})
	if err = os.WriteFile(filepath.Join(profile, "Default", "Preferences"), prefs, 0600); err != nil {
		return nil, err
	}
	childRead, parentWrite, err := os.Pipe()
	if err != nil {
		return nil, err
	}
	defer childRead.Close()
	parentRead, childWrite, err := os.Pipe()
	if err != nil {
		parentWrite.Close()
		return nil, err
	}
	defer childWrite.Close()
	for _, f := range []*os.File{parentRead, parentWrite} {
		if err = syscall.SetHandleInformation(syscall.Handle(f.Fd()), syscall.HANDLE_FLAG_INHERIT, 0); err != nil {
			parentRead.Close()
			parentWrite.Close()
			return nil, err
		}
	}
	flags = append(flags, "--headless", "--disable-extensions", "--disable-sync", "--disable-background-networking", "--disable-component-update", "--no-first-run", "--no-default-browser-check",
		"--remote-debugging-pipe", fmt.Sprintf("--remote-debugging-io-pipes=%d,%d", childRead.Fd(), childWrite.Fd()), "--user-data-dir="+profile,
		// Every origin except this fixture is sent to our rejecting proxy, also
		// covering WebSocket handshakes, which Fetch interception doesn't cover.
		"--proxy-server=http://"+proxyListener.Addr().String(), "--proxy-bypass-list=<-loopback>;"+strings.TrimPrefix(origin, "http://"),
		"--disable-quic", "--force-webrtc-ip-handling-policy=disable_non_proxied_udp",
		"--host-resolver-rules=MAP * ~NOTFOUND, EXCLUDE 127.0.0.1", "about:blank")
	s.cmd = exec.Command(browser, flags...)
	s.cmd.Dir = filepath.Dir(browser)
	s.cmd.Env = []string{`SystemRoot=C:\Windows`, `WINDIR=C:\Windows`, "TEMP=" + profile, "TMP=" + profile,
		"USERPROFILE=" + userProfile, "LOCALAPPDATA=" + runtimeRoot, "APPDATA=" + profile}
	s.cmd.Stderr = &s.stderr
	s.cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true, AdditionalInheritedHandles: []syscall.Handle{syscall.Handle(childRead.Fd()), syscall.Handle(childWrite.Fd())}}
	if err = s.cmd.Start(); err != nil {
		parentRead.Close()
		parentWrite.Close()
		return nil, err
	}
	s.cdp = newProtocol(parentRead, parentWrite)
	go func() { _ = s.cmd.Wait(); close(s.done) }()
	go s.handleEvents()
	var targets struct {
		Infos []struct {
			ID   string `json:"targetId"`
			Type string `json:"type"`
		} `json:"targetInfos"`
	}
	for deadline := time.Now().Add(10 * time.Second); time.Now().Before(deadline); {
		raw, e := s.cdp.call("Target.getTargets", map[string]any{}, "")
		if e != nil {
			return nil, e
		}
		if e = json.Unmarshal(raw, &targets); e != nil {
			return nil, e
		}
		for _, target := range targets.Infos {
			if target.Type != "page" {
				continue
			}
			raw, e = s.cdp.call("Target.attachToTarget", map[string]any{"targetId": target.ID, "flatten": true}, "")
			if e != nil {
				return nil, e
			}
			var attached struct {
				ID string `json:"sessionId"`
			}
			if e = json.Unmarshal(raw, &attached); e != nil {
				return nil, e
			}
			s.page = attached.ID
			break
		}
		if s.page != "" {
			break
		}
		time.Sleep(25 * time.Millisecond)
	}
	if s.page == "" {
		return nil, errors.New("Chromium page unavailable")
	}
	// Dedicated worker targets do not implement the Fetch domain. A single
	// browser-level interceptor covers pages AND their worker loader factories.
	if _, err = s.cdp.call("Fetch.enable", map[string]any{"patterns": []any{map[string]any{"urlPattern": "*", "requestStage": "Request"}}}, ""); err != nil {
		return nil, err
	}
	if err = s.configurePage(s.page); err != nil {
		return nil, err
	}
	if _, err = s.cdp.call("Browser.setDownloadBehavior", map[string]any{"behavior": "allow", "downloadPath": filepath.Join(profile, "downloads")}, ""); err != nil {
		return nil, err
	}
	if _, err = s.cdp.call("Page.bringToFront", map[string]any{}, s.page); err != nil {
		return nil, err
	}
	navigation, err := s.cdp.call("Page.navigate", map[string]any{"url": origin + "/"}, s.page)
	if err != nil {
		return nil, err
	}
	var navigationResult struct {
		Error string `json:"errorText"`
	}
	if err := json.Unmarshal(navigation, &navigationResult); err != nil {
		return nil, err
	}
	if navigationResult.Error != "" {
		return nil, fmt.Errorf("fixture navigation failed: %s", navigationResult.Error)
	}
	go func() {
		select {
		case <-s.done:
		case <-time.After(3 * time.Minute):
			s.close()
		}
	}()
	return s, nil
}

func (s *session) configurePage(id string) error {
	_, err := s.cdp.call("Target.setAutoAttach", map[string]any{"autoAttach": true, "waitForDebuggerOnStart": true, "flatten": true}, id)
	if err != nil {
		return fmt.Errorf("enable child-target interception: %w", err)
	}
	return nil
}

func (s *session) handleEvents() {
	for {
		select {
		case <-s.cdp.done:
			return
		case event := <-s.cdp.events:
			if event.Method == "Page.loadEventFired" {
				s.eventMu.Lock()
				if len(s.events) < 100 {
					s.events = append(s.events, event)
				}
				s.eventMu.Unlock()
			}
			// Each event runs independently: awaiting a Fetch response must not
			// prevent handling worker attachment needed by that response.
			go func() {
				switch event.Method {
				case "Fetch.requestPaused":
					var p struct {
						ID      string `json:"requestId"`
						Request struct {
							URL string `json:"url"`
						} `json:"request"`
					}
					if json.Unmarshal(event.Params, &p) != nil {
						return
					}
					method := "Fetch.failRequest"
					params := map[string]any{"requestId": p.ID, "errorReason": "BlockedByClient"}
					if allowedRequest(p.Request.URL, s.origin) {
						method = "Fetch.continueRequest"
						delete(params, "errorReason")
					}
					_, _ = s.cdp.call(method, params, event.Session)
				case "Target.attachedToTarget":
					var p struct {
						ID   string `json:"sessionId"`
						Info struct {
							ID   string `json:"targetId"`
							Type string `json:"type"`
							URL  string `json:"url"`
						} `json:"targetInfo"`
						Waiting bool `json:"waitingForDebugger"`
					}
					if json.Unmarshal(event.Params, &p) != nil || !p.Waiting {
						return
					}
					if (p.Info.Type == "worker" || p.Info.Type == "iframe") && allowedRequest(p.Info.URL, s.origin) {
						if err := s.configurePage(p.ID); err == nil {
							_, _ = s.cdp.call("Runtime.runIfWaitingForDebugger", map[string]any{}, p.ID)
							return
						} else {
							fmt.Fprintln(&s.stderr, "Target setup failed:", err)
						}
					}
					_, _ = s.cdp.call("Target.closeTarget", map[string]any{"targetId": p.Info.ID}, "")
				}
			}()
		}
	}
}

func (s *session) close() {
	s.once.Do(func() {
		if s.proxy != nil {
			_ = s.proxy.Close()
		}
		if s.cmd != nil && s.cmd.Process != nil {
			select {
			case <-s.done:
			default:
				if s.cdp != nil {
					go func() { _, _ = s.cdp.call("Browser.close", map[string]any{}, "") }()
				}
				select {
				case <-s.done:
				case <-time.After(2 * time.Second):
					_ = s.cmd.Process.Kill()
				}
			}
			<-s.done
		}
		if s.cdp != nil {
			s.cdp.writer.Close()
		}
		// Chromium descendants may take a moment to release profile files.
		for i := 0; i < 20; i++ {
			if os.RemoveAll(s.profile) == nil {
				return
			}
			time.Sleep(100 * time.Millisecond)
		}
		fmt.Fprintln(os.Stderr, "profile cleanup incomplete:", s.profile)
	})
}

type broker struct {
	token     string
	mu        sync.Mutex
	sessions  map[string]*session
	launching int
}

func decode(w http.ResponseWriter, r *http.Request, v any) error {
	d := json.NewDecoder(http.MaxBytesReader(w, r.Body, maxBody))
	d.DisallowUnknownFields()
	if err := d.Decode(v); err != nil {
		return err
	}
	if d.Decode(new(any)) != io.EOF {
		return errors.New("expected one JSON value")
	}
	return nil
}
func reply(w http.ResponseWriter, value any) {
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(value)
}

func (b *broker) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Cache-Control", "no-store")
	if r.Host != address || r.Header.Get("Origin") != "" || r.Header.Get("Sec-Fetch-Site") != "" ||
		subtle.ConstantTimeCompare([]byte(r.Header.Get("Authorization")), []byte("Bearer "+b.token)) != 1 {
		http.Error(w, "forbidden", http.StatusForbidden)
		return
	}
	if r.Method != "POST" || r.Header.Get("Content-Type") != "application/json" {
		http.Error(w, "POST application/json required", 405)
		return
	}
	if r.URL.Path == "/launch" {
		var req struct {
			Args []string `json:"args"`
		}
		if err := decode(w, r, &req); err != nil {
			http.Error(w, err.Error(), 400)
			return
		}
		if _, _, err := validateArgs(req.Args); err != nil {
			http.Error(w, err.Error(), 400)
			return
		}
		b.mu.Lock()
		if b.launching+len(b.sessions) >= 2 {
			b.mu.Unlock()
			http.Error(w, "browser capacity reached", 409)
			return
		}
		b.launching++
		b.mu.Unlock()
		s, err := startBrowser(req.Args)
		b.mu.Lock()
		b.launching--
		if err != nil {
			b.mu.Unlock()
			http.Error(w, err.Error(), 500)
			return
		}
		id := randomID()
		b.sessions[id] = s
		b.mu.Unlock()
		go func() { <-s.done; s.close(); b.mu.Lock(); delete(b.sessions, id); b.mu.Unlock() }()
		reply(w, map[string]any{"id": id})
		return
	}
	var req struct {
		ID     string          `json:"id"`
		Method string          `json:"method,omitempty"`
		Params json.RawMessage `json:"params,omitempty"`
		Name   string          `json:"name,omitempty"`
	}
	if err := decode(w, r, &req); err != nil {
		http.Error(w, err.Error(), 400)
		return
	}
	b.mu.Lock()
	s := b.sessions[req.ID]
	b.mu.Unlock()
	if s == nil {
		http.Error(w, "unknown or expired browser session", 404)
		return
	}
	switch r.URL.Path {
	case "/close":
		s.close()
		b.mu.Lock()
		delete(b.sessions, req.ID)
		b.mu.Unlock()
		reply(w, map[string]bool{"ok": true})
	case "/status":
		s.eventMu.Lock()
		events := s.events
		s.events = nil
		s.eventMu.Unlock()
		select {
		case <-s.done:
			reply(w, map[string]any{"exited": true, "stderr": s.stderr.String(), "events": events})
		default:
			reply(w, map[string]any{"exited": false, "stderr": s.stderr.String(), "events": events})
		}
	case "/command":
		if !allowedMethods[req.Method] {
			http.Error(w, "CDP method is not allowed", 403)
			return
		}
		params := req.Params
		if len(params) == 0 {
			params = json.RawMessage(`{}`)
		}
		if !bytes.HasPrefix(bytes.TrimSpace(params), []byte("{")) {
			http.Error(w, "params must be an object", 400)
			return
		}
		result, err := s.cdp.call(req.Method, params, s.page)
		if err != nil {
			http.Error(w, err.Error()+"\n"+s.stderr.String(), 500)
			return
		}
		reply(w, result)
	case "/download":
		// Read only a completed regular download from the broker-created profile.
		if req.Name == "" || filepath.Base(req.Name) != req.Name || strings.ContainsAny(req.Name, `\/:`) || strings.HasSuffix(req.Name, ".crdownload") {
			http.Error(w, "invalid download name", 400)
			return
		}
		file := filepath.Join(s.profile, "downloads", req.Name)
		info, err := os.Lstat(file)
		if err != nil {
			http.Error(w, "download unavailable", 404)
			return
		}
		if !info.Mode().IsRegular() || info.Size() > maxBody {
			http.Error(w, "invalid download", 400)
			return
		}
		data, err := os.ReadFile(file)
		if err != nil {
			http.Error(w, "download unavailable", 404)
			return
		}
		reply(w, map[string]any{"data": data})
	default:
		http.Error(w, "unknown endpoint", 404)
	}
}

func serve() error {
	// Never run the browser with an elevated administrator token.
	var token syscall.Token
	process, err := syscall.GetCurrentProcess()
	if err != nil {
		return err
	}
	if err := syscall.OpenProcessToken(process, syscall.TOKEN_QUERY, &token); err != nil {
		return err
	}
	defer token.Close()
	var elevated uint32
	var returned uint32
	if err := syscall.GetTokenInformation(token, 20 /* TokenElevation */, (*byte)(unsafe.Pointer(&elevated)), 4, &returned); err != nil {
		return err
	}
	if elevated != 0 {
		return errors.New("run serve from a normal terminal, not an administrator terminal")
	}
	if err := initializeUserPaths(); err != nil {
		return err
	}
	if err := containProcessTree(process); err != nil {
		return err
	}
	if err := os.MkdirAll(runtimeRoot, 0700); err != nil {
		return err
	}
	listener, err := net.Listen("tcp4", address)
	if err != nil {
		return err
	}
	defer listener.Close()
	b := &broker{token: randomID(), sessions: map[string]*session{}}
	connection, _ := json.Marshal(map[string]any{"url": "http://" + address, "token": b.token, "pid": os.Getpid()})
	if err = os.WriteFile(connectionFile, connection, 0600); err != nil {
		return err
	}
	defer os.Remove(connectionFile)
	server := &http.Server{Handler: b, ReadHeaderTimeout: 5 * time.Second, ReadTimeout: 30 * time.Second, WriteTimeout: 35 * time.Second, IdleTimeout: 30 * time.Second, MaxHeaderBytes: 8192}
	time.AfterFunc(30*time.Minute, func() { _ = server.Close() })
	fmt.Println("Browser launcher ready at", address, "(30 minute lifetime)")
	err = server.Serve(listener)
	b.mu.Lock()
	remaining := make([]*session, 0, len(b.sessions))
	for _, s := range b.sessions {
		remaining = append(remaining, s)
	}
	b.mu.Unlock()
	for _, s := range remaining {
		s.close()
	}
	if errors.Is(err, http.ErrServerClosed) {
		return nil
	}
	return err
}

func main() {
	if len(os.Args) != 2 || os.Args[1] != "serve" {
		fmt.Fprintln(os.Stderr, "Usage: BrowserLauncher.exe serve (no additional arguments)")
		os.Exit(2)
	}
	if err := serve(); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}
