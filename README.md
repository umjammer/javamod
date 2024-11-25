[![Release](https://jitpack.io/v/umjammer/javamod.svg)](https://jitpack.io/#umjammer/javamod)
[![Java CI](https://github.com/umjammer/javamod/actions/workflows/maven.yml/badge.svg)](https://github.com/umjammer/javamod/actions/workflows/maven.yml)
[![CodeQL](https://github.com/umjammer/javamod/actions/workflows/codeql-analysis.yml/badge.svg)](https://github.com/umjammer/javamod/actions/workflows/codeql-analysis.yml)
![Java](https://img.shields.io/badge/Java-17-b07219)

# JavaMod

Java MOD Player

- mavenized
- made libraries outsourced as much as possible  

| player | subtype                                                                     | status | spi             | library                                              | comment                    |
|--------|-----------------------------------------------------------------------------|--------|-----------------|------------------------------------------------------|----------------------------|
| mod    | STK, NST, MOD, WOW, XM, FAR, MTM, STM, STS, STX, S3M, IT, MPTM, PowerPacker | ✅      | ✅               | this                                                 |                            |
| opl    | ROL, LAA, CMF, DRO, SCI                                                     | ✅      | TBD             | this                                                 | opl3 class is duplicated   |
| sid    | SID                                                                         | ✅      | TBD             | [JSIDPlay2](https://github.com/umjammer/JSIDPlay2)   |                            |
| vgm    | VGM, GBC, NSF, SPC                                                          | ✅      | ✅<sup>[1]</sup> | [libgme](https://github.com/umjammer/vavi-sound-emu) | gbc,nsf,spc are not tested |

<sub>[1] implemented in [vavi-sound-emu](https://github.com/umjammer/vavi-sound-emu)</sub>

## Install

 * [maven](https://jitpack.io/#umjammer/javamod)

## Usage

```java
  AudioInputStream modAis = AudioSystem.getAudioInputStream(new BufferedInputStream(Files.newInputStream(mod), MAX_BUFFER_SIZE));
  AudioFormat inFormat = sourceAis.getFormat();
  AudioFormat outFormat = new AudioFormat(inFormat.getSampleRate(), 16, inFormat.getChannels(), true, inFormat.isBigEndian());
  AudioInputStream pcmAis = AudioSystem.getAudioInputStream(outFormat, modAis);
  SourceDataLine line = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, pcmAis.getFormat()));
  line.open(pcmAis.getFormat());
  line.start();
  byte[] buffer = new byte[line.getBufferSize()];
  int bytesRead;
  while ((bytesRead = pcmAis.read(buffer)) != -1) {
    line.write(buffer, 0, bytesRead);
  }
  line.drain();
```

## References

* https://github.com/quippy-git/javamod
* https://github.com/PotcFdk/JSIDPlay2 → [patched](https://github.com/umjammer/JSIDPlay2)
* https://modarchive.org/ (mod download)

## TODO

* sid mixer details
* extract graphic equalizer ui
* extract led scroll ui
* ~~java sound spi~~ sid, opl

---

# [Original](https://github.com/quippy-git/javamod)

JavaMod - a java based multimedia player for Protracker, Fast Tracker, 
Impulse Tracker, Scream Tracker and other mod files plus
SID, MP3, WAV, OGG, APE, FLAC, MIDI, AdLib ROL-Files (OPL), ...
See the supported file types for a complete list.

This is the original JavaMod project from Daniel "Quippy" Becker.

Download the JAR or the source code and compile for yourself. A double click
on the jar-file should start it. If not, to manually start the player in GUI
mode open a command line (CMD or Shell) and enter:
   java -jar ./javamod.jar
To start the command line version enter:
   java -cp ./javamod.jar de.quippy.javamod.main.CommandLine MODFILE
   Without any parameters you will receive a help screen.

On Linux consider starting with OpenGL render pipeline activated:
   java -Dsun.java2d.opengl=true -jar ./javamod.jar

## Remarks to 3.9.x version updates
With JavaMod versions 4.0 to 5.0 I want to integrate Midi and AdLib support.
However, before starting that I want to have all test mods of Schism and
Open ModPlug Tracker to work. We finished MOD, XM, STM and S3M with this version
and a whole lot of other stuff as well. So I decided to release new versions
with minor version number updates to have you participate in these changes!

## Download of compiled version and source code
* https://javamod.de/javamod.php
* https://quippy.de/mod.php
* https://sourceforge.net/projects/javamod/
* https://github.com/quippy-git/javamod

## Supported file types:
* Mods (STK, NST, MOD, WOW, XM, FAR, MTM, STM, STS, STX, S3M, IT, MPTM, PowerPacker)
* OPL2/3 (ROL, LAA, CMF, DRO, SCI)
* WAV, AU, AIFF
* MIDI (MID, RMF, RMI) with SF2 soundfont files
* MP3 (Files and Streams)
* FLAC
* APE (ape, apl, mac)
* OGG/Vorbis (ogg, oga)
* SID (very basic support. Use JSidPlay2 or Vice SID Player)
* Playlists PLS, M3U, M3U8, ZIP, CUE

## Technical info:
Code Compliance Level: JDK 17
Build with openJDK 17.0.11
 
## Third-party libraries
JavaMod incorporates modified versions of the following libraries:

* jflac (http://jflac.sourceforge.net/)
* jlayer (https://github.com/wkpark/JLayer (was http://www.javazoom.net/javalayer/javalayer.html))
* jmac (http://jmac.sourceforge.net/)
* jorbis (http://www.jcraft.com/jorbis/)
* sidplay2 (http://sidplay2.sourceforge.net/)
* OPL3 (https://opl3.cozendey.com/)
* FMOPL (https://github.com/mamedev/mame - was removed from mame 03/2021)

## Known issues:
* With PulseAudio: 
  * gapless audio streams do not work if SourceLine Buffers drain out
  * scrambled sound (especially with PipeWire)
* With KDE:
  * JDialogs, when set visible, will not come to front
* Tray Icon: mouse wheel (volume control) & keyboard shortcuts do not work

## Planned:
* finish loading of OMPT extended instrument / song data / mixer data
* reading at least Midi Config with XMs / ITs
* VSTiVolume, SamplePreAmp, MixLevels - look, what OMPT has to say
* check for further missing MPTM Effects like Reverb and Surround commands
* optimize recognition of different trackers - for whatever that is worth it
* Quad Speaker mixing (rear speakers)
* + LongList:
  * Midi and AdLib/OPL with Mods
  * WavPack and MusePack support
  * MO3 support
  * read from 7z archives
