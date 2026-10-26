package main

import (
	"bytes"
	"net/http/httptest"
	"os"
	"strings"
	"testing"
)

func TestLaunchBoundary(t *testing.T) {
	valid := []string{"--headless", "--window-size=1440,1100", "--force-device-scale-factor=1.25", "--remote-debugging-port=0", `--user-data-dir=D:\untrusted-profile`, "http://127.0.0.1:54000/"}
	flags, origin, err := validateArgs(valid)
	if err != nil || origin != "http://127.0.0.1:54000" {
		t.Fatalf("valid launch: %s %v", origin, err)
	}
	for _, flag := range flags {
		if strings.Contains(flag, "user-data-dir") || strings.Contains(flag, "debugging") {
			t.Fatalf("untrusted profile/debugging option forwarded: %s", flag)
		}
	}
	for _, arg := range []string{"--no-sandbox", "--disable-gpu-sandbox", "--load-extension=C:/evil", "--renderer-cmd-prefix=pwsh.exe", "--browser-subprocess-path=C:/evil.exe", "--remote-debugging-port=9222", "--proxy-server=evil", "--window-size=600,1100", "--force-device-scale-factor=100", "https://example.com", "file:///C:/secret", "http://127.0.0.1:80", "http://127.0.0.1:43187", "http://127.0.0.1:54000@evil/", "http://127.0.0.1:54000/?x=1"} {
		_, _, err := validateArgs([]string{arg, "http://127.0.0.1:54000/"})
		if err == nil {
			t.Errorf("accepted hostile argument: %s", arg)
		}
	}
	if _, _, err := validateArgs([]string{"--headless"}); err == nil {
		t.Fatal("missing fixture URL accepted")
	}
}

func TestRequestBoundary(t *testing.T) {
	origin := "http://127.0.0.1:54000"
	for _, raw := range []string{origin + "/main.js", "blob:" + origin + "/uuid", "data:application/wasm;base64,AAA", "about:blank"} {
		if !allowedRequest(raw, origin) {
			t.Errorf("blocked fixture resource %s", raw)
		}
	}
	for _, raw := range []string{"file:///C:/secret", "http://127.0.0.1:1234", origin + ".evil/main.js", "https://example.com", "http://user@127.0.0.1:54000/", "blob:https://evil/uuid", "chrome://settings"} {
		if allowedRequest(raw, origin) {
			t.Errorf("allowed outside resource %s", raw)
		}
	}
}

func TestBrokerRejectsUnauthorizedAndDangerousCommands(t *testing.T) {
	b := &broker{token: "test-token", sessions: map[string]*session{"owned": {}}}
	for _, tc := range []struct {
		path, body, auth, origin, host string
		want                           int
	}{
		{"/launch", `{}`, "", "", address, 403},
		{"/launch", `{}`, "test-token", "http://evil", address, 403},
		{"/launch", `{}`, "test-token", "", "evil", 403},
		{"/launch", `{"args":[],"executable":"pwsh.exe"}`, "test-token", "", address, 400},
		{"/launch", `{"args":[]} {}`, "test-token", "", address, 400},
		{"/launch", `{"args":["--no-sandbox","http://127.0.0.1:54000"]}`, "test-token", "", address, 400},
		{"/command", `{"id":"owned","method":"DOM.setFileInputFiles","params":{"files":["C:/secret"]}}`, "test-token", "", address, 403},
		{"/command", `{"id":"owned","method":"Browser.setDownloadBehavior"}`, "test-token", "", address, 403},
		{"/command", `{"id":"owned","method":"Page.navigate"}`, "test-token", "", address, 403},
		{"/command", `{"id":"owned","method":"Target.createTarget"}`, "test-token", "", address, 403},
		{"/command", `{"id":"owned","method":"Runtime.evaluate","sessionId":"other"}`, "test-token", "", address, 400},
		{"/download", `{"id":"owned","name":"../secret"}`, "test-token", "", address, 400},
		{"/download", `{"id":"owned","name":"file:stream"}`, "test-token", "", address, 400},
		{"/status", `{"id":"other"}`, "test-token", "", address, 404},
	} {
		r := httptest.NewRequest("POST", "http://"+tc.host+tc.path, bytes.NewBufferString(tc.body))
		r.Header.Set("Content-Type", "application/json")
		if tc.auth != "" {
			r.Header.Set("Authorization", "Bearer "+tc.auth)
		}
		if tc.origin != "" {
			r.Header.Set("Origin", tc.origin)
		}
		w := httptest.NewRecorder()
		b.ServeHTTP(w, r)
		if w.Code != tc.want {
			t.Errorf("%s %s: got %d want %d: %s", tc.path, tc.body, w.Code, tc.want, w.Body.String())
		}
	}
}

// Run under the broker's normal Windows account, which has registered folders.
func TestKnownFoldersIgnoreEnvironmentOverrides(t *testing.T) {
	if err := initializeUserPaths(); err != nil {
		t.Fatal(err)
	}
	wantRoot, wantProfile, wantConnection := runtimeRoot, userProfile, connectionFile
	t.Setenv("LOCALAPPDATA", `C:\untrusted-caller\local`)
	t.Setenv("USERPROFILE", `C:\untrusted-caller\profile`)
	if err := initializeUserPaths(); err != nil {
		t.Fatal(err)
	}
	if runtimeRoot != wantRoot || userProfile != wantProfile || connectionFile != wantConnection {
		t.Fatal("caller environment overrides redirected the broker's Windows known folders")
	}
}

// Opt in only when explicitly running outside the Codex sandbox, after review.
func TestBrowserIntegration(t *testing.T) {
	if os.Getenv("VIBE_LAUNCHER_INTEGRATION") != "1" {
		t.Skip("requires a real headless Chromium outside the agent sandbox")
	}
	if err := initializeUserPaths(); err != nil {
		t.Fatal(err)
	}
	if err := os.MkdirAll(runtimeRoot, 0700); err != nil {
		t.Fatal(err)
	}
	fixture := httptest.NewServer(nil)
	defer fixture.Close()
	s, err := startBrowser([]string{"--headless", "--disable-gpu", fixture.URL})
	if err != nil {
		t.Fatal(err)
	}
	defer s.close()
	raw, err := s.cdp.call("Runtime.evaluate", map[string]any{"expression": "1+2", "returnByValue": true}, s.page)
	if err != nil || !strings.Contains(string(raw), `"value":3`) {
		t.Fatalf("evaluate: %s %v", raw, err)
	}
}
