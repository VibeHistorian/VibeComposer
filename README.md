# VibeComposer 2
## Customizable Music Generator

![VibeComposer Advanced_UI Preview](https://i.imgur.com/9P4g2i6.png)

#### Arranged Track Examples (v2.0): 
- https://soundcloud.com/vibehistorian/legend
- https://soundcloud.com/vibehistorian/the-spirit-molecule



#### How to run it (on Windows):
1. Make sure you have installed the Java Runtime Environment 1.8.0 - https://java.com/en/download/
2. Download the executable from the latest release https://github.com/VibeHistorian/VibeComposer/releases/tag/v2.0-beta
3. Run/double-click the executable

#### How to run it (on Linux/Mac):
1. Same thing as Windows, but use the JAR (.jar) file instead of the executable (.exe) file.
2. If step 1 doesn't work, it might be necessary to open a terminal in the location where the .jar was downloaded, and use command: "java -jar VibeComposer-v2.0-beta-JAR.jar"
3. In case it still doesn't work, feel free to contact me (email/discord below).

#### How to use it:
1. Click 'Compose' to hear a preview of the song (one measure loop).

    -  Make any adjustments you want, then click 'Regenerate' to hear the new changes. Some knobs/comboboxes react instantly without needing to Regenerate.

    -  If you like what you hear, you can enable Arrangement by checking 'ARRANGE' and clicking 'Regenerate' again, 
     which creates a fully 'arranged' track with varying energy levels and new variations.

2. Drag'n'Drop into your DAW, or just save it for later.


**Note:** if no sound is coming out but the playback slider is moving (the song appears to be playing), try unchecking 'MIDI Transmitter Mode" and clicking 'Regenerate', or changing the MIDI device to something else.

**Note2:** if you want direct playback into your DAW, install a virtual MIDI cable (loopMIDI is recommended) 
    and then it can be selected as one of the MIDI transmitter devices in the dropdown menu.
    
#### Building requirements:
1. Apache Maven https://maven.apache.org/guides/getting-started/maven-in-five-minutes.html
2. JDK 1.8 or later https://jdk.java.net/
3. Unzip downloaded code, run command "mvn clean compile assembly:single package" in the directory with pom.xml
	-- this should create 2 jars and 1 exe

#### Arrangement examples made with earlier versions of VibeComposer: 
- https://soundcloud.com/vibehistorian/space-journey
- https://soundcloud.com/vibehistorian/anaesthesia-blues
- https://soundcloud.com/vibehistorian/avalanche
- https://soundcloud.com/vibehistorian/discovery

#### License: GPL3
    
##### <div>Arrangement transition icons made by <a href="https://www.freepik.com" title="Freepik">Freepik</a> from <a href="https://www.flaticon.com/" title="Flaticon">www.flaticon.com</a></div>
	
#### Contact: vibehistorian@gmail.com / Discord: VibeHistorian#3955
