## New in Version 3.9.4.1 HotFix
* FIX: IT MidiMacros are dismissed as of version recognition. However, that was
       wrongly implemented and now MidiMacros are mostly always deleted.

## New in Version 3.9.4
* NEW: Supporting STX (ScreamTracker Music Interface Kit)
* NEW: moved all STM, S3M, IT, ModPlug specific coding into ScreamTrackerMixer
* NEW: PatternImagePanel now knows about unused (muted) channels with IT - with
       S3Ms I still throw them out (can be changed by a "compile switch").
* NEW: also XMs, when saved with ModPlug (not when compatibility mode), get
       rainbow colored headers in pattern dialog
* NEW: Same with S3Ms when OPL3 is needed
* NEW: recognizing MPTM "sample on disc" to output an error. I do not support
       that, and I don't want to support all the sample sources MPTM can load.
* NEW: AdLib Instruments loaded for MPTP and S3M plus display in sample dialog
* FIX: Finished - (except those tests that require OPL):
       Enhanced ScreamTracker 2 compatibility by fixing everything to make these
       test MODs work: https://wiki.openmpt.org/Development:_Test_Cases/S3M
       (many, many changes...!)
       Remarks to the loop tests. They are now played, as ST3 (V3.21) does it.
       The loops run differently when you mute channels in ST3, because then the
       SB0 and SBx commands of the muted channels are not executed. JavaMod on
       the other hand does not simulated that. If you mute a channel, effects
       are still executed. Only channels that were saved as muted are ignored.
* FIX: Bugs after finishing MODs and XMs. I broke XMs without instruments.
       And the overflow check at preparePortaToNoteEffect was checking a period
       against an index.
* FIX: XM GlobalVolSlide only on Tick Zero.
* FIX: XM porta2Note with new instrument - really ignore it. Was now dead code.
* FIX: XM and low notes - removed security checks, FT2 does not do them.
       However, we simulate the uint8 note value to get negative wrap around.
* FIX: XM show correct frequency and transposed note in sample dialog
* FIX: XM only in modplug mode support extended XM effects >0xE2x
* FIX: XM ignores sample offset, if no note is present
* FIX: IT, STM, S3M jumpLoops
* FIX: IT, STM, S3M vibrato depths fixed
* FIX: IT Global Tempo Slide on ticks>0 and not (and not only) on tick 0
* FIX: IT moving noteCut to a method: NNA/DNA must also call that. *ARGH!*
* FIX: IT recognizing different SavedWithTracker names
* FIX: S3M Loading: recognize different trackers now, ModPlug recognition
       optimized
* FIX: S3M reading song flags now correctly
* FIX: S3M if AmigaLimits is now active - set correct borders
* FIX: S3M calculation of periods was too precise (cannot believe I say that)
* FIX: S3M double clean up of pattern removed
* FIX: Empty S3Ms are better loaded now
* FIX: STM special speed set (also added ST2Tempo for S3M, if set in song flags,
       but did not check)
* FIX: STM is mono (as is STX)
* FIX: STM / S3M Arpeggios were one half note off
* FIX: STM Zero Effects - no effect memo
* FIX: STM unsupported effects: you can edit beyond arpeggio effect, but are not
       implemented in ScreamTracker 2.21
* FIX: STM recognition optimized (false positive STX and some Schism)
* FIX: Reworked proceedToNextRow - patternFineDelay plus PatternBreak failed
* FIX: PatternImagePanel: no IndexArrayOutOfBounds with initial currentIndex=-1
       (went unnoticed - was just catched and "walked off")

## New in Version 3.9.3
* FIX: Finished:
       Enhanced FastTracker 2 compatibility by fixing everything to make these
       test MODs work: https://wiki.openmpt.org/Development:_Test_Cases/XM
       (many, many changes...!)
* FIX: When fixing (Fine) VolSlides for XMs: MODs do not have effect memory with
       fine volume slides
* FIX: Note Delays with XMs did not work correctly anymore, due to other fixes.
       Refixed...
* FIX: XM: During Note Delays, tick effects (volume column) need to continue
* FIX: Note delay overhaul as well for ProTracker, when we are at it.
* FIX: S3Ms reset volume with only instrument given
* FIX: Fixed IT ((Extra) Fine) porta up/down. Additionally moved some effects
       to be processed after vol column effects (arpeggio / vibrato / Tremolo)
* FIX: the information dialogs Pattern, Instrument and Sample stayed closed when
       de-iconifying JavaMod. That is because we use "dispose" to hide JavaMod
       in the task bar with Linux KDE when minimizing to system tray.
       That is not a good idea, as the visible status of those is always false
       then and cannot be restored
* FIX: renamed effect "retrig note + volume slide" to "multi retrig note"
* FIX: Show >0xFF rows in pattern display

## New in Version 3.9.2
* FIX: Continue:
       Enhanced FastTracker 2 compatibility by fixing everything to make these
       test MODs work: https://wiki.openmpt.org/Development:_Test_Cases/XM
       (many, many changes...!)
* FIX: When loading XMs create empty patterns (the player can handle this, the
       pattern display however does not like null rows or null elements
* FIX: Fix of 3.9 to 3.9.1 introduced bug of envelope fade out
* FIX: AutoVibrato with XMs was broken - also with IT (copy bug from vibrato)
* FIX: Update check did not update the date of last check
* FIX: VolumeSlides and FineVolSlides plus volume column volume slides

## New in Version 3.9.1
* NEW: Colorful pattern display with previous and next pattern displayed in dim
       colors. Following pattern optimized, drawn completely manually and with
       full clipping of unseen parts to gain speed.
       Effects are displayed in different colors per category, like MPT does it
       During playback, you can select a pattern for seeking into the piece
* NEW: Moved the UpdateThread of ModPatternDialog into a separate class and
       moved wiring into ModContainer
* NEW: added also an editor bar to the pattern display to move with cursor keys
       and page up/down, home/end, ... plus modifier keys.
       Press ESC to leave editor mode. (The editor mode is not a real editor!)
* NEW: A double click on the instrument column will open instrument/sample
       dialog for display
* NEW: Zoom for instrument and sample display
* NEW: Overhaul of most of the graphical implementations
* NEW: removed test classes from project
* NEW: Added a headless mode for command line usage. That way no GUI elements
       are created.
       (Explanation: So far we used the ConfigPanels to store the current config
       of a mixer. Now this is done locally and only transported to/from the
       GUIs, if they are present)
* NEW: Added ModPlug Tracker MPTM-Files for loading. However, not all MPTM-
       effects are yet supported (there are not that many missing though)
       See next list:
* NEW: added support for OMPT special "Extension Effect" feature
* NEW: added OMPT extended song properties (not all yet!)
* NEW: added OMPT extended instrument properties (by far not all yet!)
* NEW: added OMPT cue points
* NEW: added OMPT tempo modes CLASSIC, ALTERNATIVE and MODERN plus TempSwing
* NEW: added OMPT pattern names / channel names / channel coloring
* NEW: added OMPT 127 Channel support
* NEW: added OMPT Kaiser interpolation and WindowedFIR with low pass filter
* NEW: (BETA, UNTESTED) added support for XM version <0x0104, but could not test
       as I am missing old FT2 XMs for that
* NEW: more sanity checks in XM loading. Had found some very corrupted ones
* NEW: Added ADPCM decoding for MODs, XMs, S3M and ITs - however, I am very sure
       that nobody is using this encoder anymore
* NEW: added a simulation of the E00/E01 filter effect with MODs and XMs (we re-
       use the resonance low pass filter of ITs here...). If I ever implement
       an A500/A1200 Paula filter we would use that one to get a more realistic
       emulation
* NEW: added FunkIt! (EFx effect) for mod files. Is that really used?!
* NEW: added an info dialog for SID tunes (and fixed NULL-Pointer exception with
       non existent info panels as well - there are none now however...)
* NEW: added support for ITs in sample mode only (flags bit 2 not set)
* FIX: setting FineTune via effect now works also in MOD/XM
* FIX: XM: reading periods from tables instead of on the fly calculations
* FIX: Enhanced ProTracker 1/2 compatibility by fixing everything to make these
       test MODs work: https://wiki.openmpt.org/Development:_Test_Cases/MOD
       (many, many changes...! Let's just say tempo and sample setting is very
       different now - and many other things)
* FIX: Enhanced FastTracker 2 compatibility by fixing everything to make these
       test MODs work: https://wiki.openmpt.org/Development:_Test_Cases/XM
       (many, many changes...!)
* FIX: Vibrato, Panbrello, Tremolo fixed for MOD, XM, S3M and IT
* FIX: Overhaul of automatic volume ramping for new instruments. Is now only
       done when a new tick starts (only on new row did not do the trick!) and
       considers now the target mixing buffer size.
       I don't do that like 8bitbubsy: add a new channel with a volume ramp
       down for the "leaving" sample and do a ramp up with the new one. Did not
       work for me with synthesis.mod from Rymix - still clicks...
* FIX: As now volume ramping does work and we also have the smooth OMPT ramping
       over one whole tick, we *must* deactivate that, if a volume is set
* FIX: no silence at beginning of play back anymore - starts instantly now
* FIX: added tempo memory for IT tempo slides
* FIX: S3M ignores illegal pattern break commands
* FIX: (Smooth) Midi Macros for XMs fixed
* FIX: Smooth midi macros initial value (lastZxxParam) must be 0x7F not 0
* FIX: Effects Parameter Extension and smooth midi macros are swapped between
       XMs and ITs
* FIX: PatternFrameDelays add up. After PatternDelay, PatternFrameDelays must be
       restored for ITs / S3M. With XMs we also need to process tick effects,
       not row effects. But fine effects (played on tick zero) must be played as
       well. Fixed for all XMs, ITs, S3M, MPT
* FIX: NoteDelay need to do row effects with EFG memory
* FIX: Registering the Mixers at the ModPatternDialog resulted in an exception
       when JavaMod was started on the command line. Also fixed with Headless
* FIX: Renamed "Wide Stereo Mix" to "Surround Mix", as that is what is really
       done
* FIX: Loading and displaying of a play list is now much faster
* FIX: CommandLine did not read parameter "buffer size" correctly
* FIX: remove effect names when play back stopped
* FIX: muting a channel will not stop its rendering anymore
* FIX: Playback in Pattern- and SampleDialog must respect finetune settings
* FIX: APE files in 8Bit were set to signed samples - which is wrong
* FIX: MP3 ICY Streams with no song name meta tag yet send will identify as
       "Streaming" and not pick a default name from the URL
* FIX: On MacOS "mode.getRefreshRate" will return "REFRESHRATE_UNKNOWN" - which
       is 0. Thanks to MasterFlomaster1 for finding this.

## New in Version 3.8
* NEW: Implemented an automatic update check every 30 days. *Disable* it in the
       "Help" menu.
* NEW: In the sample and instrument dialog the sample and instrument can now be
       played. In the instrument dialog select a note in the mapping to change
       the note played. In the sample dialog there is a drop down box for that
* NEW: Added a zoom feature in the sample dialogs
* NEW: Save location, size and visible status of pattern, sample and instrument
       dialogs. Buttons now alternate visibility of dialogs
* NEW: Updated the MOD info dialog to present information more clearly
* NEW: Pattern display now with IT effect identifiers (were always MOD/XMs)
* NEW: Speed up pattern display by implementing a very basic toHex-function that
       fits for our purpose - and by reusing the StringBuilder instance.
* NEW: Effect display in pattern dialog are now buttons so mute / unmute works
       there, too - enlarges the clickable area
* NEW: if a channel is surround, the peek meter in the pattern dialog will show
       )))CH((( instead of (((CH)))
* NEW: When seeking in a song, mute status is remembered
* NEW: OpenModPlug allows to enter effect "W" with FT2 XMs, it is however empty.
       To avoid loading errors regarding unsupported effects, it is also
       "supported" as empty with JavaMod
* NEW: His Master of Noise: StarTrecker with ID FEST: have some special FineTune
       setting (negate it and divide by two)
* NEW: (internally) RandomAccessStream lost "readFully"-methods
* NEW: added some tool tip texts
* FIX: Arpeggio in MODs were not displayed, because effect byte is zero.
       However, if effect OPs are set it's an arpeggio
* FIX: Spinners of sample / instrument dialog were - sometimes, in
       unreproducible scenarios - not reseted to index 0
* FIX: Updated container configuration management. All entries were copied and
       stayed in .javamod.properties - even very old ones, that are not
       supported anymore.
       Also changed name of XMas decoration property, so please enable again
* FIX: The Tremolo effect was very imprecise. Furthermore, Tremolo with ITs is
       already on first tick
* FIX: The Panbrello effect was added on the current panning set. That is wrong.
       We now save either a panning effect value or the instrument / sample
       panning and work the effect on that value.
* FIX: Panning with IT/S3M supports also Fine Panning
* INFO:Modern trackers like Schism or OMPT share a memory with VolumeSlide
       and FineVolSlide with XMs - like IT does - and support weird effect
       combinations, e.g. start with EAx or EBx for a FineVolSlide up/down, and
       continue with A00. I added the support, but flagged it to false.
       FT2 does not do it like that, so we don't either.
* FIX: Support empty pattern in S3M (like already in IT) and do not load garbage
* FIX: VolRamp code was a complete mess. Now we support 5ms super smooth volRamp
       for XMs and others get at least 950ys. Yet, no difference between
       volRampUp or VolRampDown
* FIX: RandomVolumeVariation was wrongly calculated. Based on instrument global
       Volume - which in most cases is 64 - and now on instrument default volume
* FIX: Optimized the "getLengthInMilliseconds" to avoid double measurement (one
       after loading for the seekbar and one from the playlist for retrieving
       song infos)
* FIX: PatternBreak at end of Song: do a loop and start at given row, do a
       fade-out if wished. Do not do so, if infinite loops are to be ignored AND
       don't do is as well if loop song is not checked (the latter is new).

## New in Version 3.7.3
* NEW: Supporting additional Protracker type mods
* FIX: Tremolo fixed, added FT2-bug - speed and depth for XM and IT
* FIX: sample delta loading with special cases

## New in Version 3.7.2
* FIX: Sample- and instrument dialog - iterating through the instruments was
       broken
* NEW: Added some sanity checks with mod loading.
* NEW: Added Mod-Files with ID !PM! - it is a ProTracker mod with delta saved
       samples. However, I have no idea what tracker created those.

## New in Version 3.7.1
* FIX: Error if window manager does not support tray icon. So really check for
       tray icon support. Would end up in a hard error, JavaMod will not start!

## New in Version 3.7
* NEW  XMAS SPECIAL:
       Enjoy some light bulbs on your desktop. You can enable it in the view
       menu. Select the screen, you want to decorate and select the effects and
       speed per screen.
       Remark: it depends on the desktop render engine how transparent windows
       are rendered and if a "click through" works. On Windows this works really
       flawlessly, but on KDE it is either flickery or with OpenGL render
       pipeline the bulbs are somewhat half transparent.
* NEW: Follow song in pattern dialog is now fun to watch:
       * The arrangement is now scrolling to the activated pattern.
       * In the pattern dialog horizontal scrolling by user is not reset by
         caret anymore.
       * no editing the caret (mark text) via mouse by user
       * Rows and channels are fixed for scrolling - and added some color (yes,
         a bit more gray)
       * channel markers are buttons for muting and have a context menu to
         select solo, mute and un-mute per channel
       * resetting to normal mute will regard muted channels with ITs (if the
         author wanted them to be muted)
       * Added sample / panning representation in channel buttons based on
         samples / volumes and panning in channel
       * Coding of colored version was deleted... It is too slow anyways.
       * Follow song can be switched off - however, this will only stop
         automatic scrolling and pattern display. If the currently watched
         pattern is played, the caret will fly by (though being of lightest
         gray)
       * display of current volume column effect and effect column effect
       * No FollowSong while exporting - except for when doing play back while
         exporting
       * upper left corner shows pattern number
* NEW: Pattern/Sample/Instrument dialogs will disappear when a file other than
       a mod (e.g. mp3) is played - and reappear when a mod is played again
* NEW: To make pattern sample/instrument data (hex values) fit to sample and
       instrument dialog index, changed display there to hex as well
* NEW: In the instrument dialog you can now hop over to the sample mapped. Just
       click in the mapping rows on the sample of choice.
* FIX: Look&Feel menu will now show active Look&Feel set
* FIX: for the FIX of the FIX...
       With PortaToNote we did things a bit too easy regarding an instrument
       set. (BTW: The behavior of FT2, IT2.14, Schism and ModPlug is totally
       different here, which values for volume and panning reset to use.)
       Three things are now done:
       * if the current instrument is a long player (i.e. has loops), we will
         continue using that. If no instrument is playing, we will use the new
         given instrument and switch to it
       * but also safe the instrument set. Following notes (without instrument)
         will use exactly that new one.
       * reset panning and volume to the instrument chosen from above
       Check with Airborn.xm and Anthem.mod
* FIX: PatternBreak at end of Song: do a loop and start at given row, do a
       fade-out if wished. Do not do so, if infinite loops are to be ignored.
       (fixed spelling in GUI btw...)
* FIX: XMs: if we have a note cut, we have to store the zero volume, so a new
       note without an instrument set will not be audible.
* FIX: Empty patterns with XMs and ITs are now recognized. Default empty pattern
       is set for display (64 default rows with ITs)
* FIX: After loading Impulse Tracker mods reduce Patterns to real amount of
       channels (we reserved 64 channels in advance for filling)

## New in Version 3.6
* NEW: Shuffle play list moved from context menu to a separate button, so that
       the function is found.
* NEW: Save play list moved from context menu to a separate button, so that
       the function is found.
* NEW: Repeat play list is now a button with small icon, not a JCheckBox
* NEW: Upgrade to Java 20 compatibility (new URL(..) is deprecated)
* NEW: Follow a tracker song in pattern view. I also implemented a colored
       version. Did work and looked good, but is way too slow!
* FIX: The SourceLine now has the same amount of bytes like the rendering
       buffer, which is set with mod play back config.
       Previously the SourceLine had a default amount of bytes that did not fit
       the rendering buffer. Made the mixer block and wait.
       However, you now might need to increase your milliseconds set!
* FIX: Envelope::sanitize also needs to limit the nPoints and endPoint values to
       maximum possible index of the arrays of positions and values. Plus moved
       fix of XM positions MSB set into sanitize function.
* FIX: fixing the FIX of 3.4 (SongName) - getSongInfosFor(URL) should NEVER
       alter the container singleton. Changed that in 3.4 for MIDIs using
       "getSongName". Result: after adding a piece to the play list, this would
       not play when double clicked.
* FIX: Samples are now displayed without gap to the right (only visible with
       small samples), color of loop was adjusted, is not swallowed by border
* FIX: BasicModMixer::fitIntoLoops ping pong loops were calculated a bit off as
       XMs need that differently to ITs...
       (AGAiN - FairStars MP3 Recorderkg.XM vs. TIMEAGO.IT)
* FIX: IT Compatibility: Ensure that there is no pan swing, panbrello, panning
       envelopes, etc. applied on surround channels.
* FIX: If looping is selected and forced on a song, we need to do a complete
       reset on all and everything.

## New in Version 3.5
* FIX: PortaToNote: if an instrument is set, that was ignored, as FT2.09 does it.
       Fix for the fix: (re-)set a NEW instrument, but do nothing when instrument
       does not change.
* FIX: Powerpacked MODs were not correctly read at all circumstances
* FIX: CommandLine: missing break statement. Setting volume fell through to
       default, which throws an exception
* FIX: CommandLine stayed in an endless loop even after piece finished
* FIX: CommandLine did not end in case of a RuntimeException
* FIX: CommandLine: supports 32 Bit now, as GUI does
* FIX: Error in ModConfigPanel::setLoopValue defaulted loop to 0
* FIX: setting balance and volume did not work as source line is not present
       first. Re-set later was unintentionally ignored
* FIX: reading # of Samples at XMs as unsigned word, 0xFFFF will not be
       interpreted as -1. That is bad
* FIX: If a mod is looping, time code is reset

## New in Version 3.4
* NEW: Added Farandole (*.FAR) support (no idea, why... Maybe, because I could).
       FAR-Files are mapped to S3M, makes things easy. However, slides are not
       perfect, yet
* NEW: Added MultiTrakker support (*.MTM), because I have some of those. So why
       not? They are mapped to ProTracker / XM Modules
* FIX: added NULL-Pointer checks and clearing sample/instrument/pattern-dialogs,
       because Farandole in S3M leaves patterns and samples empty
* FIX: All multimedia files relying on MultimediaContainer::getSongNameFromURL
       need the URL updated in the BaseContainer - is now done.
       Flaw was probably only visible with MIDIs
* FIX: SIDMixer must not implemented setMillisecondposition. Wav-Export of File
       does not work.
* FIX: RandomAccessFile wants to read beyond end of file
* FIX: Exporting to wave would leave a thread running when encountering an
       exception
* FIX: PortaToNote: if an instrument is set, that was ignored, as FT2.09 does it.
       Fixed it, as new trackers also do not ignore it, and new compositions
       relay on that.
* FIX: Playlist - as we use a HTML Table for representation we need to masquerade
       all HTML special characters - not only spaces

## New in Version 3.3
* IMPORTANT: as JDK 8 LTS support will be over in two years (security) and
       normal support is already over, JavaMod is now using JDK 17 (LTS).
       As the compile level changed, you *must* use JDK 17 to run JavaMod!
* NEW: Replaced Wide Stereo with a real virtual surround
* FIX: Legacy ModPlug Tracker (1.17RC2 and lower) files have correct
       volume now. I did things too easy!
* FIX: iconify and deIconify fixed so that with tray icon entry in task bar
       is gone
* FIX: MP3: added "Info" tag to "Xing" tag to read as CBR

## New in Version 3.2
* FIX: Sustain-Loop and normal Loop not correctly differed in
       BasicModMixer::fitIntoLoops. Resulted in a Devision by Zero, if
       sustain loop present, but no normal loop
* FIX: refactoring of BasicModMixer::fitIntoLoops to be more precise, especially
       with interpolation magic at loops. Plus ping pong loops optimized
* FIX: Impulse Tracker Mods, saved by OpenModPlug, were often identified as
       legacy Modplug Tracker files with then wrong settings in global
       volume and preamp. New ModPlug-Songs were played fare to silent then
* FIX: End of envelopes now correctly identified, count of active NNAs, that
       are not active anymore, does not explode anymore
* FIX: volume column effects for IT and XM re-factored, many fixes on
       ImpulseTracker
* FIX: s3m load volume column as panning, if above 128
* FIX: also stm and s3m (with IT Compatmode OFF) share Porta memories now (like
       IT did already)
* FIX: Calculating steps for sample data iteration exceeded integer for high
       sample rates at base frequency (ImpulseTracker). Switched to long
* FIX: loading of volume envelopes with old instruments (CmwT<0x200).
       Did not skip 200 bytes of pre calculated volume envelope data. Is now
       loaded, but not used (we could also skip those...)
* FIX: Log.debug (instead of Log.info) for missing effects
* FIX: When a MOD was playing, changing MOD sample rate, channels, bits also
       changed current audio line, even though e.g. mp3 is playing. Not healthy
       for play back if the global line is cut away during playing
       (BTW: same for SIDs)
* FIX: Instrument set, but no note - with XMs/ITs lookup in mapping resulted in
       Exception
* FIX: Iconified and deiconified with system tray should now work with Linux
       (KDE) as well
* FIX: Icons (Tray and GUI) keep aspect ratio now
* NEW: added Fine Midi Macros
* NEW: added MidiMacros to XM
* NEW: added sanitize of envelope data, optimized envelope processing
* NEW: Loading is much faster now. Provided RandomAccessStreamImpl with a buffer
       so reading and seeking is done on the buffer. Using a buffer of 8K
       currently.
* NEW: Screen refresh rate is now considered to define refresh FPS of LED
       Scroller, peak meter, update panel, etc.
* NEW: MegaBass implementation changed to the last one of Olivier Lapicque from
       ModPlug Tracker. I like that mega bass :)
       Doing so we moved also other DSP modifications to the new separate
       class ModDSP
* NEW: added DC removal and made it configurable
* NEW: added additional command line parameters

## New in Version 3.1
* FIX: Pattern/Sample/Instrument dialogs should only be created, if parent
       JDialog is present. Otherwise these will never get destroyed
* INITIAL GITHUB RELEASE

## New in Version 3.0
* FIX: IT set global volume: normalize to 0x80 for *non*-IT Mods
* FIX: MP3 info panel: Label "Duration" is "Bit Rate"
* FIX: Detailed progress bar while export did not restart properly
* FIX: Exported MP3, FLAC, WAV,... in chunks
       * did not stop exactly at end position but at end of chunk
       * MP3: counting samples now, recalculating milliseconds from that
         (previously added milliseconds send to buffer, which is not accurate)
* FIX: Export file name generation allowed illegal characters
* UNFIX FIX: pattern break with row index stays in last pattern now, but only if
       "ignore loop" is not checked
* FIX: Playlist numbering - amount of leading zeros
* FIX: Display samples without SampleInterpolationLookAheads
* FIX: SampleInterpolationLookAhead with stereo samples

## New in Version 2.9
* FIX: saving a relativized path into a playlist failed on Linux. Here a file
       path is case sensitive!
* FIX: some ROL songs did not load properly
* FIX: Skipping at end of ROL leaded to mixed up mixers
* FIX: loading a playlist of ROLs, last file in list was played, not the
       highlighted one
* FIX: Technical song info for FLAC
* FIX: APE TAG in Footer - loading failed due to wrong file pointer calculation
* FIX: Pattern Delay and Tick Delay for IT fixed - no other effects are replayed
       in this combination. Schism 17.it did not test that
* FIX: pattern break with row index stays in last pattern now, but only if
       "ignore loop" is not checked
* FIX: Loading XMs with no samples for instruments, reset defaults then
* FIX: Many wrong settings with S7x Effects - those need to be temporary!
       (Blue Flame.IT -- thanks for the hint, David)
* Added technical song info for MP3 and APE
* Optimized the ParentDialog setting for the mod info panel
* Supporting OPL2, Dual OPL2 and OPL3 in DRO correctly now. Do not try OPL3 DRO
  with OPL2 Emulation - you will receive an error
* Dual OPL2 and OPL3 work with Virtual Stereo now, no virtual stereo for mono
  OPL2
* supporting ProTracker STK file now - are like STM

## New in Version 2.8
* fixed NullPointer when deleting whole playlist and adding new entries
* Clean Up:
  moved all mod constants from class Helper to new class ModConstants.
  Unused conversion methods in class Helper were documented out
* ModFileInputStream and RandomAccessStream cleaned up
* added OPL3 support
  - added OPL3.java from Robson Cozendey
  - moved it to "de.quippy.opl3" package inside the project
  - enabled reuse by removing statics - subclasses receive "their" own OPL3
    instance
  - added switch statement "case off" in getEnvelope
  - Volume of TomTom, Snare, Cymbals, high hats and base drum raised
    (dirty hack! Needs introspection!)
* added/removed FMOPL 0.37a from Tatsuyuki Satoh (Java-Port)
  - updated to FMOPL 0.72 from Jarek Burczynski (& Tatsuyuki Satoh)
	from the M.A.M.E Project
  - no Y8950 support (yet)
  - no call back support for listeners - not needed here
* added support for rol, laa, cmf, dro, sci by migration of mid.cpp, dro[2].cpp
  and rol.cpp from adplug project + effekter.c from own (very old) ROL project

## New in Version 2.7.1
* The OPL3 support was already implemented - but as pure beta yet not activated,
  Release because:
* fixed: nasty NullPointerException when pattern, sample and instrument dialog
  have never been opened yet and switching back from other media file then mod.
  They get initialized then to get the loaded information data, but the
  ModInfoPanel is not yet added to its JFrame so: no RootPane at that moment.

## New in Version 2.7
* added second detail progress bar with wav export or playlist copy
* fixed update of playlist for songname and duration blocking whole UI
  (Things in EventQueue.invokeLater should't take long... learned something)
* fixed possible null-pointer when closing stream
* fixed possible null-pointer in wave display at stream end
* fixed note fade and key off, when mod has only samples
* fixed creation of two AudioProcessors. One is enough! This resulted in a
  NullPointerException as the outputbuffer was deleted - one Thread was informed,
  the second did not know about this and ran into a nullpointer...
* fixed loading error in S3M / IT parapointers loaded as shorts - results in
  negative seeking values in big songs (>0x8000 is negative as short - and
  stays negative when converted to integer)
* fixed Protracker / XM Fine VolSlide - forgot to check borders
  (was there once, was gone... Oo)
* fixed and optimized NNA: using a silent channel per default is not a good
  idea. Now also considering instruments being far beyond vol envelope end.
  Copied channel is stopped now.
* fixed / optimized seeking and time measurement of mod files. GUI loading is
  much faster now, because time measurement is faster.
  This has a drawback: seeking without rendering samples will keep sample and
  envelope pointers at default. That can cause unwanted artifacts - but none
  were heard yet.
* fixed swingVolume for IT: uses instrument global volume as reference. MPT uses
  the current channel volume
* added resonance and cut off swing, but yet MPT extended instruments are not
  loaded - so far this is dead code
* stereo samples with S3Ms are supported now
* recognition of openModPlug and ModPlug for IT-Mods added
* PreAmp and attenuation optimized - considering OMPT
* added song restart for XMs and ProTracker inclusive sanity checks
* added "Loop song" switch to avoid looping with ProTracker and XM Mods due to
  song restart
* Effects-Panel has new switch: useGaplessAudio (I gave up!)
  Linux bug with AudioOutput: if the playback buffer drains (either because of
  "drain" or "flush" or if it runs dry) next input is crippled. See try in
  fixing this in comments below (V2.6)
  This means loading of next piece should never take longer than the buffer has
  sound to play. Use high buffers (>200ms) to avoid this.
  Alternatively deactivate the gapless audio in effect panel
* added display dialogs for mods: pattern data, samples and instruments
  They are reachable by pressing the buttons in the mod info dialog box

## New in Version 2.6
* fixed icon size in tray icon
* save selected SA-Meter visual style
* fixed a playback issue if hardware buffers are smaller then the mixing buffer
* fixed (hopefully) swing gui errors from playlist
* fix for KDE Bugs in Drag&Drop with wrongly encoded URLs
* fix with GaplessAudioStream - it's now really gapless.
* Fix for issue with JDK >1.8 (tested JDK11, JDK14) on Linux: re-using a
  SourceDataLine after line.flush() or line.drain() results in crippled sound.
  Environment: OpenJDK(11|14) on OpenSuse 15.(1|2) with PulseAudio. Does not
  occure under windows
* Midi playback: added support for SF2 SoundFont files
* fixed loading of S3Ms with adlib instruments (do not try to load sampledata!!)
* added a sound hardware information box
* enabled 32-Bit mixing with mods - output 32 bit possible (only if
  sound hardware supports it - is not checked in advance)
* integrated dithering and noise-shift for reduction in bit depth - primary
  noticeable with 8Bit playback
* vast improvement / speed up on time measurement (length of song) - still not
  optimal yet (da geht noch was!)
* linear table support XM and especially in ITs now fully implemented
  (porta 2 note, porta,(extra) fine porta, autovibrato, vibrato)
* added New Note Actions for IT (YES!) This was not easy in Java as
  copying a channel by re-assigning pointers like in C is not possible here
  We need to copy all relevant data to a separate channel.
* Hence also Duplicate Checks added
* Therefore needed to redevelop all volume envelope handling and to support
  note_cut, note_fade and key_off. Now completely working for IT and XMs
  with all differences
* Plus rewrite of envelope looping code (differ between IT and XM)
* Resonance Filter is now working for cut off frequencies and resonance values.
  On and Off works as intended
* introduced Z00-Z7F and Z80-ZFF (cutOff and resonance set) for ITs
  Therefore midi makros are now introduced (loaded or set to default)
  No further midi support yet
* Added pitch pan separation
* added loading of song texts from ITs and XMs
* added global volume, mixing volume, panning, random panning/volume variation
* rewrote note and instrument settings completely. FT2.09 && IT2.14 compatible
  (and Protracker & Scream Tracker compatible as well - of course)
* Mixing does not need to complete sampling a whole tick anymore. We mix till
  buffers are full and continue at that point
* Stereo sample support added
* Player_Abuse_Tests (https://github.com/schismtracker/schismtracker/wiki/Player-abuse-tests)
  are all working now --> 100% IT2.14 compatible. At least with those.
* Looping samples needed some re-write (had mistakes with pingpong anyways)
* Interpolation optimized with real effort - however it seems, that the old
  Laissez-faire way was quite enough...
* added Period borders for S3M (Amiga-Limits)
* fixed glissando
* several other small bug fixes in playback

## New in Version 2.5
* fixed Volume Slide in s3m impulse- and fasttracker - boy what a mess!
* added *2/3 table for Qxy in Fasttracker Mods
* Retrig note memory reseted to zero with Q00 instead of keeping memory
* Retrig starts on tick Zero!
* fixed an issue with SID output settings virtual stereo and 44.1MHz
* changing virtual stereo during playback is fixed

## New in Version 2.4
* radio header encoding is translated by ISO-8859-1 and not UTF-8
* radio stream description is now displayed as well
* new "Export selected files to wav" - is usefull if e.g. a cue sheet with one
  flac file is played and the single songs are needed (come as wav then)
  If no files are selected, the whole playlist is exported
* copy files in playlist order moved here
* Export to wav is renamed to Export while playing and intended for
  radio streams
* corrected time index with mod files. Estimation of duration is more correct
  now
* switched repositories from CVS to SVN

## New in Version 2.3
* now also https radio streams are supported
* initially wrong positioned icy-metaint points in streams are now supported
* relative file names in playlists at saving time to location of playlist

## New in Version 2.3
* Pattern Pos Jump did not work if infinite loops are disabled
  Furthermore Pattern Jump Position set is channel specific
* Streaming of OGG Streams works - sort of...
* Saving radio / Internet stream playlists works now
* "All Playable files" is on top of selection now
* TextAreaFont and DialogFont are not set as statics any more to prevent errors
  when UI is not used and needed in server like environments (headless)
* Optimized load of URLs from playlists - no "re-location" for http-files - these
  are always absolute
* introducing HttpResource for web-Radio - this supports also 302, moved

## New in Version 2.2
* added internal mod buffer length in config dialog
* Fixed a bug in ID3v2 Tag decoding with Strings and missing encoding
* MP3 playback optimized, dropping zero padded samples
* Fixed a Bug in central URL creation routine
* Fixed playlist write conversion error (ISO-8859-1 - not UTF-8)
* Added m3u8 for UTF-8 Playlists
* reading pls files can handle empty lines and "NumberOfEntries" at end of file
* drag&drop into playlist calculation of index fixed
* Midi: a bug in playback was fixed. (simulation of played samples,
  correct close of all sound devices)
* added support for SQSH packed mod files
* fixed drawing issues in peak meters
* added clipping to Graphical EQ calculations
* removing illegal characters from Strings (<0x20)
* updating streaming of mp3 radio streams

## New in Version 2.1
* copy all files in a playlist to a certain directory - in playlist order
  sometimes useful when creating music collections on USB sticks
* deletion of elements in the playlists resets index
* changed to UTF-8 - encoding for playlists is set to ISO-8859-1
* dropped files into the playlist were not playable anymore - fixed
* fixed mouse consume event when double clicks center sliders
* fixed a runtime exception with corrupt mp3s
* fixed a bug (AGAIN *grrr*) with privileged access to "user.home" in applets
* changed logger
* "Code too large" Error with NetBeans and IWave6581 and IWave8580
  --> Eclipse uses its own compiler and will not create that error
  --> JavaC and NetBeans nevertheless will
  --> arrays are now loaded as a resource from files
* SampleOffset now plays only with a note given

## New in Version 2.0
* Needs Java 6 now!!!
* Tray Icon support added (--> JDK6)
* Seeking speedup with FLAC, APE and wav
* XM/IT:
  * added HighSampleOffset
  * added panbrello (was read with IT, effect never build...)
  * added fine pattern delay (# of ticks) to XM
    and fixed a potential bug here...
  * fixed IT_AMIGA_TABLE: one octave too high
  * fixed Pattern Delay effects
* Playists:
  * Saving playlist as m3u as default works now
  * still some problems with the caret when an entry was selected or moved
  * added Support for CUE-Sheets (read and write)
* PitchShift: overSampling and FrameSize are editable
* Flac uses vorbis comment now
* MP3 info panel switches to ID3v2-Tab if id3v2-Tag exists
* fixed the scrolly-problem, that chars "pop up" rightmost but not invisible
* fixed a problem with converting local files to URLs
* fixed a null byte flaw with C++ Strings and Unicode
* fixed a thread problem when starting javamod ui with a playlist as a parameter
* fixed a problem with converting absolute path entries in playlists to
  a relative path regarding the saving place of the playlist
* fixed a problem with alt-tab usage that only popped up the playlist or
  info panel but not the main dialog
* fixed a potential bug with comparing two URLs (does a dns lookup)

## New in Version 1.9.4.7
* Export of whole playlist: if a playlist is present, all files of this list
  will be exported
* reorganized the audio processor for DSP-visual effects. Samples only need to
  be send to the SoundOutputStream - audio processor converts by itself
* visual: added more colors to the meters
* DSP: added the graphics equalizer
* DSP: added the pitch shift (change the pitch without tempo change)
* Modplayer: loops can now be skipped (ignored, fadeout, keep endless loop)
* Jump pattern effect did not work... (it has never so far actually...)
* CAUTION: CHANGED COMMANDLINE AND APPLET VALUE FOR -l PARAMETER TO 0, 1, 2

## New in Version 1.9.4.6
* many minor errors
* SID Config:
  - Filter setting had no affect
  - added a scrollpane to enable scrolling
  - all elements can be changed during playback
  - changing virtual stereo does work
* play and stop with SIDs does not automaticly increase song number anymore
* changed listeners of checkboxes from ChangeListeners to ItemListeners
* fixed several bugs with the playlist gui
  - Editing entries produced encoded URL-Strings
  - Entries not found were set to NULL
  - saving playlists with NULL-Entries did not work
  - synchronizing methods to get parallel changes handled
  - active element is always visable now
  - moving entries in large lists works now
  - marking and demarking (i.e. ctrl-A) does not move the visiable rect anymore

## New in Version 1.9.4.5
* added Playlist Repeat for GUI, commandline and applet. Use parameter "j" to set state
* Config Panel is now using a tab panel to make all configs accessible
* sid has a config panel now

## New in Version 1.9.4.4
* Applets loading of Mods did not work any more
* Applets create security exception with Java 6 - only optimization of error handling
* initial Volume with applets or command line will work
* balance is now also set after loading (in GUI, like volume)
* midis with capturing will stop (draining a line from which is never read will lead into an endless loop!)

## New in Version 1.9.4.3
* The applet JavaModApplet_OldSchoolDemo moved to a separate project
* added public methods to the applet to allow applet control via javascript
  see also the applet test page on quippy.de
* saving a new playlist did not work anymore
* export to wave did not change name to lokal file pattern (utf-8)
* Adding files into a cleaned playlist created a false index beeing too big
* Calculation the length of wave files which need conversion did not work
* key support in playlists added
* OGG did not work any more - correcting CRC determination back to original
* saving Playlist changes filenames relativ to playlist location
* Dropping a folder into playlist will collect all Files getting found from there on
* working on MIDI capturing - hope it helps...

## New in Version 1.9.4.2
* after correcting the file and url handling with applets, the local
  files and playlists did not work any more... fixed
  --> NEVER HTML-encode your playlists
  --> it was found out that with streaming files to the applet
      the webserver needs to provide a MIME-Type
* new Applet: de.quippy.javamod.main.applet.JavaModApplet_OldSchoolDemo - try it out ;)
* reconstructing Applet class hierarchy

## New in Version 1.9.4.1
* File-Handling with applets is somewhat difficult. The algorithem guessing the
  correct filename or url was improved. Relative URL / local files are allowed now
  and tried to be found using the URL of the playlist.
* masking of illegal character in URLs (like white spaces) is done automatically
* mp3-streaming was recognized through HTTP / against FILE protocol. That is not
  correct - streaming is now recognized by contentlength==-1 (this might
  also fail though...)

## New in Version 1.9.4.0
* After installing ape support, FLAC as the more widly spreaded codec needs
  to be supported as well
* Updated About-Dialog, mentioning all decoders and versions used
* Implemented ZIP-Support by reorganising the IO classes
* ZIP-Files are handled as playlists
* A Path like "C:\Dir\Zipfile.zip\path\to\file.mp3 is now supported
* Bug fix with XMs/ITs using empty noteIndex or SampleIndex-Arrays
* GUI for PlayList implemented
  - Droping files into playlist (at a position)
  - moving files around
  - reading file infos through thread (updating lists)
  - saving pls or m3u
  - editing of entries
* Everything is handled through a playlist (even single files)
* enabled multi file selection in open dialog
* #EXTINF and PLS: Infos are now read
* Several BugFixes (due to findbug)
* Version history dialog has now correct size again
* Drag&Drop
  - supports filelists as a playlist now
  - Dropped Playlists are "unpacked"
  - even combinations of Files and Playlists can be dropped, result is a new Playlist
  - added Dropspace into the playlist
* implemented a GaplessSoundOutputStream so pieces with same output quality will
  get rendered directly into the same line - this results in gapless rendering
  as appreciated with e.g. audio books

## New in Version 1.9.3.5
* as stumbling over an ape decoder I decided to implement that too
  APE-Decoding is slow - very slow! You will see while seeking.
* added command line option for the GUI - can now instantly load a file or
  playlist file

## New in Version 1.9.3.4
* improvement of midi playback
* export to wav for midi implemented (port selection is implemented)
  --> this all will only work with one sound card installed or lucky selections
  --> full beta state!
* added support for RMI-Files (RIFF Midi)
* setup dialog will now refresh between changes at playback
* playback while wav-export is supported (usefull with streams) - the user will
  now be asked if playback during wav export is wished
* added OGG / Vorbis-Support (fully implemented...)
* fixed error with unloadable files - they are now skipped in playlist
* fixed that "all playable files" also contains playlists

## New in Version 1.9.3.3
* Bugfix with PitchEnvelopes: are used as filter- or pitchenvelopes. Reading
  flag now.
* Reconstructing volume handling
* added volume ramping
* Bug: songarrangement contains illegal pattern num - are now dropped during
  loading of modules

## New in Version 1.9.3.2
* Bugfix with XMs:
  - Envelopes need to be processed at new rows
  - and the fade out volume hits only with active envelope; otherwise note cut
* Applet got the seekbar included

## New in Version 1.9.3.1
* Bugfix when loading certain STMs - ID was unknown. Fix with a fall back
  strategy now - loading with all known loaders - the one who can load without
  error winns - if no loader can load, mod is corrupt.

## New in Version 1.9.3
* Moving packages for modplayback to de.quippy.javamod.mod.*
* Bugfixing of code inserted with 1.9.2
  - Problem with NoteDelay after insertion of wrong code
  - bad sound with instruments reaching volume 0 (VolSlide)

## New in Version 1.9.2
* super.paintComponent() in MeterPanelBase is not necessary - speedup gui repaint
* generating local file names from playlist files improved
* Threads are now final
* SerialVersionUID inserted where needed
* Non used Variables or methods commented
* MIDIs are now seekable
* MP3s are now seekable
* Faster Seeking of Mods enabled
* WAV-Playback - Seekbar fixed
* WAV-Playback - Peek Display synchronized
* After reading a non existent Wave-File, the SeekBarPanel ran into an Exception
* Peeks: ArrayIndexOutOfBounds fixed
* With ITs:
  * Loading of compressed IT-Samples did not alwayes work - fixed
  * Added Envelope Processing (AutoVibrato/Volume/Panning) for IT-Mods
  * NoteCut and NoteOff are Different - Now differing (S3Ms and ITs)
  * Resonance-Filter included
  * NNA prepared - but big bug - so far disabled
  * maxChannelCount with ITs was 1 less
  * and a lot more with ITs

## New in Version 1.9.1
* PowerPacker 2.0-Files are now supported
* Mod-Loader is selected by header
* file extension for mods are only needed to differ between mods and mp3s or others
* WOW-Mods are now reckognized correctly
* preceeding "extension" (mod.modfile) are loaded correctly
* IT-Instrument decompression "EOF-Exception" fixed
* IT Instruments Looping fixed
* Version-Checking now realy checks version
* Version-History now in javamod_version.txt
* Displaying version history as menu or if version is newer

## New in Version 1.9
* SID-Support
* displaying time code of mods (can take some time with ITs or long pieces)
* jump in wavs and mods
* no jumping in mp3s :(
