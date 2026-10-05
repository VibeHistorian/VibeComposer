package main

import (
	"embed"
	"io/fs"
	"log"

	"github.com/wailsapp/wails/v2"
	"github.com/wailsapp/wails/v2/pkg/options"
	"github.com/wailsapp/wails/v2/pkg/options/assetserver"
)

//go:embed all:frontend/dist/browser
var frontendAssets embed.FS

func main() {
	app := NewApp()
	webAssets, err := fs.Sub(frontendAssets, "frontend/dist/browser")
	if err != nil {
		log.Fatal(err)
	}
	err = wails.Run(&options.App{
		Title:     "VibeComposer",
		Width:     1440,
		Height:    960,
		MinWidth:  960,
		MinHeight: 640,
		AssetServer: &assetserver.Options{
			Assets: webAssets,
		},
		Bind: []interface{}{app},
	})
	if err != nil {
		log.Fatal(err)
	}
}
