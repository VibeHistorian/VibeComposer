# VibeComposer 2.3
## Interactive Music Generator

![VibeComposer Simple UI Preview](https://i.imgur.com/O53OLLq.png)

#### Arranged Track Examples (v2.3): 
- [Good Night](https://soundcloud.com/vibehistorian/good-night)
- [Winter Tale](https://soundcloud.com/vibehistorian/winter-tale)
- [Glowing Apart](https://soundcloud.com/vibehistorian/glowing-apart)
- [Anti Hero](https://soundcloud.com/vibehistorian/anti-hero)

#### How to run it:
1. [Download and install JRE - Java Runtime Environment (at least ver. 1.8.0)](https://java.com/en/download/)
2. [Download the latest release (.jar)](https://github.com/VibeHistorian/VibeComposer/releases/download/v2.3-beta/VibeComposer-2.3-beta-JAR.jar) (other formats available under Releases (right sidebar))
3. Double-click to run
  > Note: (Windows-only) If double-clicking the .jar doesn't work: download VibeComposerLauncher.bat from Releases, place it in the same folder as the .jar and run that instead.
  > If you run into any issues, feel free to contact me (email/discord below).

#### How to use it:
1. Click the big orange 'Compose' button to compose a new song preview.

    -  Make any adjustments you want, then click 'Regenerate' to hear the new changes. Some knobs/comboboxes react instantly without needing to Regenerate (for convenience).

    -  If you like what you hear, you can enable Arrangement by checking 'ARRANGE' and clicking 'Regenerate' again, 
     which creates a fully 'arranged' track with varying energy levels and new variations.

2. Drag'n'Drop into your DAW, or just save it for later.


**Note:** if no sound is coming out but the playback slider is moving (the song appears to be playing), try unchecking 'MIDI Transmitter Mode" and clicking 'Regenerate', or changing the MIDI device to something else.

**Note 2:** if you want direct playback into your DAW, install a virtual MIDI cable (loopMIDI is recommended) 
    and then it can be selected as one of the MIDI transmitter devices in the dropdown menu.
    
#### Building requirements:
1. [Apache Maven](https://maven.apache.org/guides/getting-started/maven-in-five-minutes.html)
2. [JDK 1.8 or later](https://jdk.java.net/)
3. Unzip downloaded code, run command "mvn clean compile assembly:single package" in the directory with pom.xml
	-- this should create 2 jars and 1 wrapped exe

#### Arrangement examples made with earlier versions of VibeComposer: 
- https://soundcloud.com/vibehistorian/a-world-beyond
- https://soundcloud.com/vibehistorian/darker
- https://soundcloud.com/vibehistorian/dew-drop
- https://soundcloud.com/vibehistorian/the-spirit-molecule
- https://soundcloud.com/vibehistorian/space-journey
- https://soundcloud.com/vibehistorian/anaesthesia-blues
- https://soundcloud.com/vibehistorian/discovery

#### License: GPL3
    
##### <div>Arrangement transition icons made by <a href="https://www.freepik.com" title="Freepik">Freepik</a> from <a href="https://www.flaticon.com/" title="Flaticon">www.flaticon.com</a></div>
	
#### Contact: vibehistorian@gmail.com / Discord: VibeHistorian#3955
