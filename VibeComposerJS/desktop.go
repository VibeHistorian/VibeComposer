//go:build !windows || bindings

package main

import (
	"github.com/wailsapp/wails/v2"
	"github.com/wailsapp/wails/v2/pkg/options"
)

func runDesktop(appOptions *options.App) error {
	return wails.Run(appOptions)
}
