//go:build !bindings

package main

import (
	"log"
	"runtime"

	"github.com/wailsapp/wails/v2"
	"github.com/wailsapp/wails/v2/pkg/options"
	"golang.org/x/sys/windows"
)

const (
	whCBT             = 5
	hcbtActivate      = 5
	wmNCDestroy       = 0x0082
	wmSysCommand      = 0x0112
	scKeyMenu         = 0xF100
	systemCommandMask = 0xFFF0
	altMenuSubclassID = 1
)

func runDesktop(appOptions *options.App) error {
	if appOptions.Menu != nil {
		return wails.Run(appOptions)
	}

	runtime.LockOSThread()
	defer runtime.UnlockOSThread()

	windowClass := "wailsWindow"
	if appOptions.Windows != nil && appOptions.Windows.WindowClassName != "" {
		windowClass = appOptions.Windows.WindowClassName
	}

	user32 := windows.NewLazySystemDLL("user32.dll")
	setWindowsHook := user32.NewProc("SetWindowsHookExW")
	callNextHook := user32.NewProc("CallNextHookEx")
	unhookWindowsHook := user32.NewProc("UnhookWindowsHookEx")

	comctl32 := windows.NewLazySystemDLL("comctl32.dll")
	setWindowSubclass := comctl32.NewProc("SetWindowSubclass")
	if err := setWindowSubclass.Find(); err != nil {
		log.Printf("Could not load Windows Alt menu filter support: %v", err)
		return wails.Run(appOptions)
	}
	removeWindowSubclass := comctl32.NewProc("RemoveWindowSubclass")
	defSubclassProc := comctl32.NewProc("DefSubclassProc")
	var subclass uintptr
	subclass = windows.NewCallback(func(hwnd uintptr, message uint32, wParam, lParam, id, _ uintptr) uintptr {
		// Bare Alt and F10 can ask Windows to focus the menu even in a
		// window with no menu bar. Leave character commands such as Alt+Space alone.
		if message == wmSysCommand && wParam&systemCommandMask == scKeyMenu && lParam == 0 {
			return 0
		}
		if message == wmNCDestroy {
			removeWindowSubclass.Call(hwnd, subclass, id)
		}
		result, _, _ := defSubclassProc.Call(hwnd, uintptr(message), wParam, lParam)
		return result
	})

	// Wails exposes no HWND callback. Find its main window on first activation,
	// while running on the window's own thread as SetWindowSubclass requires.
	var hook uintptr
	stopWatching := func() {
		if hook != 0 {
			if removed, _, _ := unhookWindowsHook.Call(hook); removed != 0 {
				hook = 0
			}
		}
	}
	watchActivation := windows.NewCallback(func(code int32, wParam, lParam uintptr) uintptr {
		if code == hcbtActivate {
			var className [256]uint16
			count, _ := windows.GetClassName(windows.HWND(wParam), &className[0], int32(len(className)))
			if windows.UTF16ToString(className[:count]) == windowClass {
				if installed, _, _ := setWindowSubclass.Call(wParam, subclass, altMenuSubclassID, 0); installed == 0 {
					log.Print("Could not install Windows Alt menu filter")
				}
				stopWatching()
			}
		}
		result, _, _ := callNextHook.Call(0, uintptr(code), wParam, lParam)
		return result
	})
	var err error
	hook, _, err = setWindowsHook.Call(whCBT, watchActivation, 0, uintptr(windows.GetCurrentThreadId()))
	if hook == 0 {
		log.Printf("Could not watch for the Wails window to install the Windows Alt menu filter: %v", err)
	}
	defer stopWatching()
	return wails.Run(appOptions)
}
