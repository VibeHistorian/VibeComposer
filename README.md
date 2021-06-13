# VibeComposer
#### Tool for generating MIDI scores using GM (General MIDI) instruments

How to run it (on Windows):
1. Make sure you have installed the Java Runtime Environment 1.8.0 - https://java.com/en/download/
2. Download the executable from the latest release https://github.com/VibeHistorian/VibeComposer/releases/latest
3. Run the executable
(Note: Linux/Mac users might be able to run it using the .jar instead of the .exe file)

How to use it:
1. If a MIDI device called 'Gervill' is visible, you can click 'Compose' - this is the default Windows MIDI device for output.
2. If such a device isn't visible, uncheck "MIDI transmitter mode" and click 'Compose', which will hopefully pick up some correct default MIDI device
3. If you want to output the MIDI notes into your DAW instead, install a virtual MIDI cable (loopMIDI is recommended) 
    and then it can be selected as one of the MIDI transmitter devices in the dropdown menu
    
For everything else, refer to the (OLD) user manual: https://github.com/VibeHistorian/VibeComposer/blob/development_master/midimasterpiece/VibeComposer_UserManual.pdf

How it should look:

![VibeComposer Simple UI](https://i.imgur.com/TueQWIt.png)
