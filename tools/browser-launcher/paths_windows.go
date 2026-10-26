package main

import (
	"fmt"
	"path/filepath"
	"runtime"
	"syscall"
	"unsafe"
)

// Resolve the current account through Windows, rather than trusting caller-
// supplied LOCALAPPDATA/USERPROFILE variables to choose the broker's storage.
func initializeUserPaths() error {
	runtime.LockOSThread()
	defer runtime.UnlockOSThread()
	ole := syscall.NewLazyDLL("ole32.dll")
	hr, _, _ := ole.NewProc("CoInitializeEx").Call(0, 2 /* COINIT_APARTMENTTHREADED */)
	if int32(hr) >= 0 {
		defer ole.NewProc("CoUninitialize").Call()
	} else if uint32(hr) != 0x80010106 { // RPC_E_CHANGED_MODE: COM is already initialized.
		return fmt.Errorf("initialize known folders: HRESULT 0x%08x", uint32(hr))
	}
	process, err := syscall.GetCurrentProcess()
	if err != nil {
		return err
	}
	var token syscall.Token
	if err := syscall.OpenProcessToken(process, syscall.TOKEN_QUERY|syscall.TOKEN_IMPERSONATE, &token); err != nil {
		return err
	}
	defer token.Close()
	localID := syscall.GUID{Data1: 0xf1b32785, Data2: 0x6fba, Data3: 0x4fcf, Data4: [8]byte{0x9d, 0x55, 0x7b, 0x8e, 0x7f, 0x15, 0x70, 0x91}}
	profileID := syscall.GUID{Data1: 0x5e6c858f, Data2: 0x0e22, Data3: 0x4760, Data4: [8]byte{0x9a, 0xfe, 0xea, 0x33, 0x17, 0xb6, 0x71, 0x73}}
	localAppData, err := knownFolder(&localID, token)
	if err != nil {
		return err
	}
	profile, err := knownFolder(&profileID, token)
	if err != nil {
		return err
	}
	runtimeRoot = filepath.Join(localAppData, "VibeComposerBrowserLauncher")
	connectionFile = filepath.Join(runtimeRoot, "connection.json")
	userProfile = profile
	return nil
}

func knownFolder(id *syscall.GUID, token syscall.Token) (string, error) {
	var raw *uint16
	hr, _, _ := syscall.NewLazyDLL("shell32.dll").NewProc("SHGetKnownFolderPath").Call(
		uintptr(unsafe.Pointer(id)), 0, uintptr(token), uintptr(unsafe.Pointer(&raw)))
	if raw != nil {
		defer syscall.NewLazyDLL("ole32.dll").NewProc("CoTaskMemFree").Call(uintptr(unsafe.Pointer(raw)))
	}
	if int32(hr) < 0 || raw == nil {
		return "", fmt.Errorf("resolve Windows known folder: HRESULT 0x%08x", uint32(hr))
	}
	var chars []uint16
	for i := 0; i < 32768; i++ {
		ch := *(*uint16)(unsafe.Add(unsafe.Pointer(raw), i*2))
		if ch == 0 {
			path := syscall.UTF16ToString(chars)
			if !filepath.IsAbs(path) {
				return "", fmt.Errorf("Windows returned a non-absolute known folder")
			}
			return path, nil
		}
		chars = append(chars, ch)
	}
	return "", fmt.Errorf("Windows known folder exceeds the path length limit")
}
