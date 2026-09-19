# VibeComposer Development Guidelines

## Build, Test, and Lint
This project uses Maven for build management and dependency handling.

- **Build the project:** `mvn clean install`
- **Compile the source:** `mvn compile`
- **Run the application:** The main entry point is `org.vibehistorian.vibecomposer.VibeComposerGUI`.

## High-level Architecture
VibeComposer is a Java Swing-based application for MIDI composition. The codebase is organized into the following primary areas:

- **UI Layer (`Panels`, `Popups`, `SwingUtils`):** Handles the user interface components. `SwingUtils` contains custom implementations for Swing behavior (e.g., custom menu handling, flashing components, and scroll adjustments).
- **Core Components (`Parts`, `Helpers`):** Bridges the gap between UI and logic. `Parts` contains specific logic units that are integrated into the UI, while `Helpers` contains shared utility methods.
- **Generation Engine (`MidiGenerator`, `MelodyGenerator`, `MelodyBlock`):** The core "intelligence" of the app, handling the algorithmic generation of melodies, chords, and rhythms.
- **Data & Constants (`Constants`, `Enums`, `SectionConfig`):** Defines the configuration, constants, and types used across the application.

## Key Conventions
- **OMNI Utility:** Use `OMNI.java` for common mathematical operations (clamping, interpolation) and color manipulations. It is the central hub for these utility functions.
- **Swing Customizations:** Custom UI behaviors (like the `flash` effect or custom menu highlights) are implemented via `SwingUtils`.
- **Package Structure:** Follow the `org.vibehistorian.vibecomposer` hierarchy. Logic should be separated from UI elements to ensure `Parts` can be reused or tested independently of the `Panels`.
- **Constants:** Use `Constants.java` for global constants and configuration values rather than hardcoding values in multiple files.
