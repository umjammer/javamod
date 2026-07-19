/*
 * Load_mo3.cpp
 * ------------
 * Purpose: MO3 module loader.
 * Notes  : (currently none)
 * Authors: Johannes Schultz / OpenMPT Devs
 *          Based on documentation and the decompression routines from the
 *          open-source UNMO3 project (https://github.com/lclevy/unmo3).
 *          The modified decompression code has been relicensed to the BSD
 *          license with permission from Laurent Clévy.
 * The OpenMPT source code is released under the BSD license. Read LICENSE for more details.
 */

package de.quippy.javamod.multimedia.mod.loader.tracker;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;

import de.quippy.javamod.io.ModfileInputStream;
import de.quippy.javamod.io.RandomAccessInputStream;
import de.quippy.javamod.multimedia.mod.ModConstants;
import de.quippy.javamod.multimedia.mod.loader.Module;
import de.quippy.javamod.multimedia.mod.loader.instrument.Envelope;
import de.quippy.javamod.multimedia.mod.loader.instrument.Instrument;
import de.quippy.javamod.multimedia.mod.loader.instrument.InstrumentsContainer;
import de.quippy.javamod.multimedia.mod.loader.instrument.Sample;
import de.quippy.javamod.multimedia.mod.loader.pattern.PatternContainer;
import de.quippy.javamod.multimedia.mod.loader.pattern.PatternElement;
import de.quippy.javamod.multimedia.mod.midi.MidiMacros;
import de.quippy.javamod.multimedia.mod.mixer.BasicModMixer;
import de.quippy.javamod.multimedia.mod.mixer.ProTrackerMixer;
import de.quippy.javamod.multimedia.mod.mixer.ScreamTrackerMixer;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import static java.lang.System.getLogger;


/**
 * MO3 Compressed Modules Tracker Loader.
 * Ported from OpenMPT soundlib Load_mo3.cpp and Laurent Levy's unmo3.c.
 * 
 * @author Johannes Schultz / OpenMPT Devs
 * @since 10.07.2026
 */
public class MO3TrackerMod extends Module {

    private static final Logger logger = getLogger(MO3TrackerMod.class.getName());

    private static final String[] MODFILEEXTENSION = { "mo3" };

    private static final int[] autovibit2xm = new int[] { 0, 3, 1, 4, 2, 0, 0, 0 };

    /** Effect params of the IT volume column tone portamento values 0..9 (from OpenMPT Tables.cpp) */
    private static final int[] ImpulseTrackerPortaVolCmd = { 0x00, 0x01, 0x04, 0x08, 0x10, 0x20, 0x40, 0x60, 0x80, 0xFF };

    /**
     * Maps MO3 pattern commands to javamod XM/MOD effect numbers (raw XM effect
     * values: 0='0' arpeggio ... 33='X' extra fine porta, 34='Y' panbrello,
     * 35=Zxx midi macro, 36=smooth midi macro, 38=parameter extension).
     * 0xFF = no effect.
     */
    private static final int[] XM_EFF_MAP = {
        0xFF, 0xFF, 0xFF, 0,    1,    2,    3,    4,    5,    6,    7,    8,    9,    10,   11,   12,
        13,   14,   15,   29,   0xFF, 0xFF, 16,   17,   20,   21,   25,   0xFF, 27,   33,   33,   0xFF,
        0xFF, 15,   10,   2,    1,    29,   27,   0xFF, 0xFF, 0xFF, 25,   0xFF, 15,   17,   34,   35,
        0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 38,   36,   0xFF, 0xFF, 0xFF
    };

    /**
     * Maps MO3 pattern commands to javamod IT/S3M effect numbers (1='A' speed
     * ... 26='Z' midi macro, 27=parameter extension, 28=smooth midi macro,
     * 29=delay cut, 30=finetune, 31=smooth finetune). 0xFF = no effect.
     */
    private static final int[] IT_EFF_MAP = {
        0xFF, 0xFF, 0xFF, 10,   6,    5,    7,    8,    12,   11,   18,   24,   15,   4,    2,    0xFF,
        3,    19,   20,   9,    0xFF, 0xFF, 22,   23,   0xFF, 0xFF, 16,   0xFF, 17,   0xFF, 0xFF, 0xFF,
        0xFF, 1,    4,    5,    6,    9,    17,   21,   13,   14,   16,   19,   20,   23,   25,   26,
        0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 27,   28,   29,   30,   31
    };

    private static final int XM_XFINEPORTAUPDOWN = 33;
    private static final int XM_SPEED_OR_TEMPO = 15;
    private static final int IT_SPEED = 1;
    private static final int IT_TEMPO = 20;
    private static final int IT_TONEPORTAMENTO = 7;
    private static final int IT_VIBRATO = 8;
    private static final int IT_TONEPORTAVOL = 12;
    private static final int IT_VIBRATOVOL = 11;
    private static final int IT_VOLUMESLIDE = 4;

    private int originalType;
    private String songMessage;
    private MidiMacros midiConfig;
    private int panningSeparation;

    @Override
    public String[] getFileExtensionList() {
        return MODFILEEXTENSION;
    }

    @Override
    public boolean checkLoadingPossible(ModfileInputStream inputStream) throws IOException {
        byte[] magic = new byte[3];
        inputStream.read(magic);
        inputStream.seek(0);
        return magic[0] == 'M' && magic[1] == 'O' && magic[2] == '3';
    }

    @Override
    public boolean checkLoadingPossible(InputStream inputStream) throws IOException {
        inputStream.mark(4);
        byte[] magic = new byte[3];
        int read = inputStream.read(magic);
        inputStream.reset();
        return read == 3 && magic[0] == 'M' && magic[1] == 'O' && magic[2] == '3';
    }

    @Override
    public BasicModMixer getModMixer(int sampleRate, int doISP, int doAmigaEmulation, int doNoLoops, int maxNNAChannels) {
        if (originalType == ModConstants.MODTYPE_IT || originalType == ModConstants.MODTYPE_S3M) {
            return new ScreamTrackerMixer(this, sampleRate, doISP, doAmigaEmulation, doNoLoops, maxNNAChannels);
        } else {
            return new ProTrackerMixer(this, sampleRate, doISP, doAmigaEmulation, doNoLoops, maxNNAChannels);
        }
    }

    @Override
    public int getPanningSeparation() {
        return panningValue != null ? panningSeparation : 128;
    }

    @Override
    public int getPanningValue(int channel) {
        return panningValue[channel];
    }

    @Override
    public int getChannelVolume(int channel) {
        return channelVolume[channel];
    }

    @Override
    public int getFrequencyTable() {
        if (originalType == ModConstants.MODTYPE_XM) {
            return ((songFlags & ModConstants.SONG_LINEARSLIDES) != 0) ? ModConstants.XM_LINEAR_TABLE : ModConstants.XM_AMIGA_TABLE;
        } else if (originalType == ModConstants.MODTYPE_IT) {
            return ((songFlags & ModConstants.SONG_LINEARSLIDES) != 0) ? ModConstants.IT_LINEAR_TABLE : ModConstants.IT_AMIGA_TABLE;
        } else if (originalType == ModConstants.MODTYPE_S3M) {
            return ModConstants.STM_S3M_TABLE;
        } else {
            return ModConstants.AMIGA_TABLE;
        }
    }

    @Override
    public String getSongMessage() {
        return songMessage;
    }

    @Override
    public MidiMacros getMidiConfig() {
        return midiConfig;
    }

    @Override
    public boolean getFT2Tremolo() {
        return originalType == ModConstants.MODTYPE_XM;
    }

    @Override
    public boolean getModSpeedIsTicks() {
        return false;
    }

    @Override
    public boolean supportsAmigaFilter() {
        return originalType == ModConstants.MODTYPE_MOD;
    }

    @Override
    protected void loadModFileInternal(RandomAccessInputStream inputStream) throws IOException {
        byte[] magicBytes = new byte[3];
        readFully(inputStream, magicBytes);
        if (magicBytes[0] != 'M' || magicBytes[1] != 'O' || magicBytes[2] != '3') {
            throw new IOException("Invalid MO3 magic header");
        }
        int version = inputStream.readByte() & 0xFF;
        int musicSize = (int) inputStream.readIntelDWord();

        int headerOffset = 8;
        int compressedSize = -1;
        if (version >= 5) {
            compressedSize = (int) inputStream.readIntelDWord();
            headerOffset = 12;
        }

        // Read compressed music chunk
        byte[] compressedMusic;
        if (version >= 5) {
            compressedMusic = new byte[compressedSize];
            readFully(inputStream, compressedMusic);
        } else {
            int remaining = (int) (inputStream.getLength() - inputStream.getFilePointer());
            compressedMusic = new byte[remaining];
            readFully(inputStream, compressedMusic);
        }

        // Decompress music chunk
        int[] unpackOffset = { 0 };
        byte[] musicData = MO3Decompressor.unpack(compressedMusic, unpackOffset, musicSize);
        int musicDataCompressedSize = unpackOffset[0];
        MemoryReader reader = new MemoryReader(musicData);

        String songName = reader.readNullString();
        String songMessage = reader.readNullString();

        setSongName(songName);
        this.songMessage = songMessage;

        // MO3FileHeader
        int numChannels = reader.readByte();
        int numOrders = reader.readWord();
        int restartPos = reader.readWord();
        int numPatterns = reader.readWord();
        int numTracks = reader.readWord();
        int numInstruments = reader.readWord();
        int numSamples = reader.readWord();
        int defaultSpeed = reader.readByte();
        int defaultTempo = reader.readByte();
        int flags = reader.readDWord();
        int globalVol = reader.readByte();
        int panSeparation = reader.readByte();
        int sampleVolume = (byte) reader.readByte(); // signed!

        byte[] chnVolume = reader.readBytes(64);
        byte[] chnPan = reader.readBytes(64);
        byte[] sfxMacros = reader.readBytes(16);
        byte[][] fixedMacros = new byte[128][2];
        for (int i = 0; i < 128; i++) {
            fixedMacros[i][0] = (byte) reader.readByte();
            fixedMacros[i][1] = (byte) reader.readByte();
        }

        setNChannels(numChannels);
        setSongLength(numOrders);
        setSongRestart(restartPos);
        setNPattern(numPatterns);
        setNInstruments(numInstruments);
        setNSamples(numSamples);
        setTempo(defaultSpeed != 0 ? defaultSpeed : 6);
        setBPMSpeed(defaultTempo != 0 ? defaultTempo : 125);
        this.panningSeparation = panSeparation;

        boolean tempIT = (flags & 0x0100) != 0;
        boolean tempS3M = (flags & 0x0002) != 0;
        boolean tempMOD = (flags & 0x0080) != 0;
        boolean tempMTM = (flags & 0x0008) != 0;

        if (tempIT) {
            originalType = ModConstants.MODTYPE_IT;
            setTrackerName("Impulse Tracker (MO3)");
        } else if (tempS3M) {
            originalType = ModConstants.MODTYPE_S3M;
            setTrackerName("Scream Tracker 3 (MO3)");
        } else if (tempMOD) {
            originalType = ModConstants.MODTYPE_MOD;
            setTrackerName("Generic MOD (MO3)");
        } else if (tempMTM) {
            originalType = ModConstants.MODTYPE_MOD;
            setTrackerName("MultiTracker (MO3)");
        } else {
            originalType = ModConstants.MODTYPE_XM;
            setTrackerName("FastTracker 2 (MO3)");
        }
        setModType(originalType);

        boolean isIT = (originalType == ModConstants.MODTYPE_IT);
        boolean isS3M = (originalType == ModConstants.MODTYPE_S3M);
        boolean isMOD = (originalType == ModConstants.MODTYPE_MOD) && tempMOD;
        boolean isMTM = (originalType == ModConstants.MODTYPE_MOD) && tempMTM && !tempMOD;
        boolean isXM = (originalType == ModConstants.MODTYPE_XM);

        songFlags = 0;
        if ((flags & 0x0001) != 0) {
            songFlags |= ModConstants.SONG_LINEARSLIDES;
        }
        if (isS3M) {
            if ((flags & 0x0010) != 0) {
                songFlags |= ModConstants.SONG_AMIGALIMITS;
            }
            if ((flags & 0x0004) != 0) {
                songFlags |= ModConstants.SONG_FASTVOLSLIDES;
            }
        }
        if (isIT) {
            if ((flags & 0x0800) == 0) {
                songFlags |= ModConstants.SONG_ITOLDEFFECTS;
            }
            if ((flags & 0x0400) == 0) {
                songFlags |= ModConstants.SONG_ITCOMPATMODE;
            }
        }
        if ((flags & 0x200000) != 0) {
            songFlags |= ModConstants.SONG_EXFILTERRANGE;
        }
        songFlags |= ModConstants.SONG_ISSTEREO;
        // MO3 knows an "IT sample only" mode (like ITs themselves do)
        boolean isSampleMode = !isXM && (flags & 0x0200) == 0;
        if (!isSampleMode && numInstruments > 0) {
            songFlags |= ModConstants.SONG_USEINSTRUMENTS;
        }

        // javamod baseVolume is 0..128 (not 0..256 like OpenMPT)
        if (isIT) {
            setBaseVolume(Math.min(globalVol, 128));
        } else if (isS3M) {
            setBaseVolume(Math.min(globalVol, 64) << 1);
        } else {
            setBaseVolume(ModConstants.MAXGLOBALVOLUME);
        }

        int preamp;
        if (sampleVolume < 0) {
            preamp = sampleVolume + 52;
        } else {
            preamp = (int) Math.round(Math.exp(sampleVolume * 3.1 / 20.0)) + 51;
        }
        setMixingPreAmp(preamp);

        channelVolume = new int[numChannels];
        panningValue = new int[numChannels];
        for (int i = 0; i < numChannels; i++) {
            if (isIT) {
                channelVolume[i] = Math.min(chnVolume[i] & 0xFF, 64);
            } else {
                channelVolume[i] = 64;
            }
            if (!isXM) {
                int pan = chnPan[i] & 0xFF;
                if (pan == 127) {
                    panningValue[i] = ModConstants.CHANNEL_IS_SURROUND;
                } else if (pan == 255) {
                    panningValue[i] = 256;
                } else {
                    panningValue[i] = pan;
                }
            } else {
                panningValue[i] = ModConstants.PANNING_CENTER;
            }
        }

        // MIDI macros
        boolean anyMacros = false;
        for (int i = 0; i < 16; i++) {
            if (sfxMacros[i] != 0) anyMacros = true;
        }
        for (int i = 0; i < 128; i++) {
            if (fixedMacros[i][1] != 0) anyMacros = true;
        }
        if (anyMacros) {
            MO3MidiMacros macroCfg = new MO3MidiMacros();
            for (int i = 0; i < 16; i++) {
                if (sfxMacros[i] != 0) {
                    macroCfg.setMidiSFXExt(i, String.format("F0F0%02Xz", (sfxMacros[i] & 0xFF) - 1));
                } else {
                    macroCfg.setMidiSFXExt(i, "");
                }
            }
            for (int i = 0; i < 128; i++) {
                if (fixedMacros[i][1] != 0) {
                    macroCfg.setMidiZXXExt(i, String.format("F0F0%02X%02X", (fixedMacros[i][1] & 0xFF) - 1, fixedMacros[i][0] & 0xFF));
                } else {
                    macroCfg.setMidiZXXExt(i, "");
                }
            }
            midiConfig = macroCfg;
        } else {
            midiConfig = new MidiMacros(); // default macros, like the IT loader does
        }

        // Orders
        allocArrangement(numOrders);
        int[] arrangement = getArrangement();
        boolean hasOrderSeparators = !isMOD && !isXM;
        for (int i = 0; i < numOrders; i++) {
            int order = reader.readByte();
            if (hasOrderSeparators) {
                if (order == 0xFF) {
                    arrangement[i] = ModConstants.INVALID_PAT_INDEX;
                } else if (order == 0xFE) {
                    arrangement[i] = ModConstants.IGNORE_PAT_INDEX;
                } else {
                    arrangement[i] = order;
                }
            } else {
                if (order == 0xFF) {
                    arrangement[i] = ModConstants.INVALID_PAT_INDEX;
                } else {
                    arrangement[i] = order;
                }
            }
        }

        // Track assignments
        int[][] patternToTrack = new int[numPatterns][numChannels];
        for (int pat = 0; pat < numPatterns; pat++) {
            for (int chn = 0; chn < numChannels; chn++) {
                patternToTrack[pat][chn] = reader.readWord();
            }
        }

        int[] patLength = new int[numPatterns];
        int maxPatLength = 64;
        for (int pat = 0; pat < numPatterns; pat++) {
            patLength[pat] = reader.readWord();
            if (patLength[pat] > maxPatLength) {
                maxPatLength = patLength[pat];
            }
        }

        byte[][] tracks = new byte[numTracks][];
        for (int t = 0; t < numTracks; t++) {
            int len = reader.readDWord();
            tracks[t] = reader.readBytes(len);
        }

        // Patterns
        PatternContainer patternContainer = new PatternContainer(this, numPatterns);
        setPatternContainer(patternContainer);
        for (int pat = 0; pat < numPatterns; pat++) {
            patternContainer.createPattern(pat, patLength[pat], numChannels);
        }
        // javamod note indices: IT = itNote+1, S3M includes the +12 octave offset
        // like OpenMPT, but XM/MOD do NOT (XMMod stores the raw XM note 1..96)
        int noteOffset = ModConstants.NOTE_MIN;
        if (isMTM) {
            noteOffset = 13 + ModConstants.NOTE_MIN;
        } else if (isS3M) {
            noteOffset = 12 + ModConstants.NOTE_MIN;
        }

        for (int pat = 0; pat < numPatterns; pat++) {
            int numRows = patLength[pat];
            for (int chn = 0; chn < numChannels; chn++) {
                int trackIndex = patternToTrack[pat][chn];
                if (trackIndex >= numTracks) continue;
                byte[] trackData = tracks[trackIndex];
                int trackPtr = 0;
                int row = 0;
                while (row < numRows) {
                    if (trackPtr >= trackData.length) break;
                    int b = trackData[trackPtr++] & 0xFF;
                    if (b == 0) break;

                    int numCommands = b & 0x0F;
                    int rep = b >> 4;

                    int noteIndex = 0;
                    int period = 0;
                    int instrument = 0;
                    int volumeEffect = 0;
                    int volumeEffectOp = 0;
                    int effect = 0;
                    int effectOp = 0;
                    int lastCmd0 = -1;
                    int lastCmd1 = -1;

                    for (int c = 0; c < numCommands; c++) {
                        if (trackPtr + 1 >= trackData.length) break;
                        int cmd0 = trackData[trackPtr++] & 0xFF;
                        int cmd1 = trackData[trackPtr++] & 0xFF;
                        lastCmd0 = cmd0;
                        lastCmd1 = cmd1;

                        switch (cmd0) {
                            case 0x01: {
                                int noteVal = cmd1;
                                if (noteVal < 120) {
                                    noteIndex = noteVal + noteOffset;
                                    period = (noteIndex - 1 < ModConstants.noteValues.length) ? ModConstants.noteValues[noteIndex - 1] : 0;
                                } else if (noteVal == 0xFF) {
                                    noteIndex = ModConstants.KEY_OFF;
                                    period = ModConstants.KEY_OFF;
                                } else if (noteVal == 0xFE) {
                                    noteIndex = ModConstants.NOTE_CUT;
                                    period = ModConstants.NOTE_CUT;
                                } else {
                                    noteIndex = ModConstants.NOTE_FADE;
                                    period = ModConstants.NOTE_FADE;
                                }
                                break;
                            }
                            case 0x02:
                                instrument = cmd1 + 1;
                                break;
                            case 0x06:
                                if (volumeEffect == 0 && isXM && (cmd1 & 0x0F) == 0) {
                                    volumeEffect = 11;
                                    volumeEffectOp = cmd1 >> 4;
                                } else if (volumeEffect == 0 && isIT) {
                                    int lookup = -1;
                                    for (int i = 0; i < 10; i++) {
                                        if (ImpulseTrackerPortaVolCmd[i] == cmd1) {
                                            lookup = i;
                                            break;
                                        }
                                    }
                                    if (lookup != -1) {
                                        volumeEffect = 11;
                                        volumeEffectOp = lookup;
                                    } else {
                                        effect = (isXM || isMOD || isMTM) ? XM_EFF_MAP[cmd0] : IT_EFF_MAP[cmd0];
                                        effectOp = cmd1;
                                    }
                                } else {
                                    effect = (isXM || isMOD || isMTM) ? XM_EFF_MAP[cmd0] : IT_EFF_MAP[cmd0];
                                    effectOp = cmd1;
                                }
                                break;
                            case 0x07:
                                if (volumeEffect == 0 && isIT && cmd1 < 10) {
                                    volumeEffect = 7;
                                    volumeEffectOp = cmd1;
                                } else {
                                    effect = (isXM || isMOD || isMTM) ? XM_EFF_MAP[cmd0] : IT_EFF_MAP[cmd0];
                                    effectOp = cmd1;
                                }
                                break;
                            case 0x0B:
                                if (volumeEffect == 0) {
                                    if (isIT && cmd1 == 0xFF) {
                                        volumeEffect = 8;
                                        volumeEffectOp = 64;
                                    } else if ((isIT && (cmd1 & 0x03) == 0) || (isXM && (cmd1 & 0x0F) == 0)) {
                                        volumeEffect = 8;
                                        volumeEffectOp = cmd1 / 4;
                                    } else {
                                        effect = (isXM || isMOD || isMTM) ? XM_EFF_MAP[cmd0] : IT_EFF_MAP[cmd0];
                                        effectOp = cmd1;
                                    }
                                } else {
                                    effect = (isXM || isMOD || isMTM) ? XM_EFF_MAP[cmd0] : IT_EFF_MAP[cmd0];
                                    effectOp = cmd1;
                                }
                                break;
                            case 0x0F:
                                if (!isMOD && volumeEffect == 0 && cmd1 <= 64) {
                                    volumeEffect = 1;
                                    volumeEffectOp = cmd1;
                                } else {
                                    effect = (isXM || isMOD || isMTM) ? XM_EFF_MAP[cmd0] : IT_EFF_MAP[cmd0];
                                    effectOp = cmd1;
                                }
                                break;
                            case 0x10:
                                effect = (isXM || isMOD || isMTM) ? XM_EFF_MAP[cmd0] : IT_EFF_MAP[cmd0];
                                effectOp = cmd1;
                                if (!isIT) { // BCD-encoded in MOD/XM/S3M/MTM!
                                    effectOp = ((effectOp >> 4) * 10) + (effectOp & 0x0F);
                                }
                                break;
                            case 0x12:
                                if (cmd1 < 0x20) {
                                    effect = (isXM || isMOD || isMTM) ? XM_SPEED_OR_TEMPO : IT_SPEED;
                                } else {
                                    effect = (isXM || isMOD || isMTM) ? XM_SPEED_OR_TEMPO : IT_TEMPO;
                                }
                                effectOp = cmd1;
                                break;
                            case 0x14:
                                if ((cmd1 & 0xF0) != 0) {
                                    volumeEffect = 3;
                                    volumeEffectOp = cmd1 >> 4;
                                } else {
                                    volumeEffect = 2;
                                    volumeEffectOp = cmd1 & 0x0F;
                                }
                                break;
                            case 0x15:
                                if ((cmd1 & 0xF0) != 0) {
                                    volumeEffect = 5;
                                    volumeEffectOp = cmd1 >> 4;
                                } else {
                                    volumeEffect = 4;
                                    volumeEffectOp = cmd1 & 0x0F;
                                }
                                break;
                            case 0x1B:
                                if ((cmd1 & 0xF0) != 0) {
                                    volumeEffect = 10;
                                    volumeEffectOp = cmd1 >> 4;
                                } else {
                                    volumeEffect = 9;
                                    volumeEffectOp = cmd1 & 0x0F;
                                }
                                break;
                            case 0x1D:
                                effect = XM_XFINEPORTAUPDOWN;
                                effectOp = 0x10 | cmd1;
                                break;
                            case 0x1E:
                                effect = XM_XFINEPORTAUPDOWN;
                                effectOp = 0x20 | cmd1;
                                break;
                            case 0x1F:
                                volumeEffect = 6;
                                volumeEffectOp = cmd1;
                                break;
                            case 0x20:
                                volumeEffect = 7;
                                volumeEffectOp = cmd1;
                                break;
                            case 0x22:
                                if (effect == IT_TONEPORTAMENTO) {
                                    effect = IT_TONEPORTAVOL;
                                } else if (effect == IT_VIBRATO) {
                                    effect = IT_VIBRATOVOL;
                                } else {
                                    effect = IT_VOLUMESLIDE;
                                }
                                effectOp = cmd1;
                                break;
                            case 0x30:
                                volumeEffectOp = cmd1 % 10;
                                if (cmd1 < 10) {
                                    volumeEffect = 5;
                                } else if (cmd1 < 20) {
                                    volumeEffect = 4;
                                } else if (cmd1 < 30) {
                                    volumeEffect = 3;
                                } else if (cmd1 < 40) {
                                    volumeEffect = 2;
                                }
                                break;
                            case 0x31:
                                volumeEffect = 12;
                                volumeEffectOp = cmd1;
                                break;
                            case 0x32:
                                volumeEffect = 13;
                                volumeEffectOp = cmd1;
                                break;
                            case 0x34:
                                if (cmd1 >= 223 && cmd1 <= 232) { // sample offset
                                    volumeEffect = 14;
                                    volumeEffectOp = cmd1 - 223;
                                }
                                break;
                            default:
                                if (cmd0 < XM_EFF_MAP.length) {
                                    effect = (isXM || isMOD || isMTM) ? XM_EFF_MAP[cmd0] : IT_EFF_MAP[cmd0];
                                    effectOp = cmd1;
                                }
                                break;
                        }
                    }

                    if (effect == 0xFF) { // effect not supported in target format
                        effect = 0;
                        effectOp = 0;
                    }
                    int targetRow = Math.min(row + rep, numRows);
                    while (row < targetRow) {
                        PatternElement element = patternContainer.createPatternElement(pat, row, chn);
                        element.setNoteIndex(noteIndex);
                        element.setPeriod(period);
                        element.setInstrument(instrument);
                        element.setVolumeEffect(volumeEffect);
                        element.setVolumeEffectOp(volumeEffectOp);

                        element.setEffect(effect);
                        element.setEffectOp(effectOp);
                        row++;
                    }
                }
            }
        }
        patternContainer.setChannelActiveStatus(panningValue);

        // Instruments
        InstrumentsContainer instrumentContainer = new InstrumentsContainer(this, numInstruments, numSamples);
        setInstrumentContainer(instrumentContainer);

        int[][] instrVibrato = new int[numInstruments][4];

        for (int ins = 0; ins < numInstruments; ins++) {
            if (isSampleMode) {
                while (reader.readByte() != 0);
                if (version >= 5) {
                    while (reader.readByte() != 0);
                }
                reader.skip(826);
                continue;
            }

            String insName = reader.readNullString();
            String insFilename = "";
            if (version >= 5) {
                insFilename = reader.readNullString();
            }

            int insFlags = reader.readDWord();
            int[][] sampleMap = new int[120][2];
            for (int i = 0; i < 120; i++) {
                sampleMap[i][0] = reader.readWord();
                sampleMap[i][1] = reader.readWord();
            }

            // volEnv
            int volEnvFlags = reader.readByte();
            int volEnvNodes = reader.readByte();
            int volEnvSustainStart = reader.readByte();
            int volEnvSustainEnd = reader.readByte();
            int volEnvLoopStart = reader.readByte();
            int volEnvLoopEnd = reader.readByte();
            int[][] volEnvPoints = new int[25][2];
            for (int i = 0; i < 25; i++) {
                volEnvPoints[i][0] = reader.readWord();
                volEnvPoints[i][1] = reader.readWord();
            }

            // panEnv
            int panEnvFlags = reader.readByte();
            int panEnvNodes = reader.readByte();
            int panEnvSustainStart = reader.readByte();
            int panEnvSustainEnd = reader.readByte();
            int panEnvLoopStart = reader.readByte();
            int panEnvLoopEnd = reader.readByte();
            int[][] panEnvPoints = new int[25][2];
            for (int i = 0; i < 25; i++) {
                panEnvPoints[i][0] = reader.readWord();
                panEnvPoints[i][1] = reader.readWord();
            }

            // pitchEnv
            int pitchEnvFlags = reader.readByte();
            int pitchEnvNodes = reader.readByte();
            int pitchEnvSustainStart = reader.readByte();
            int pitchEnvSustainEnd = reader.readByte();
            int pitchEnvLoopStart = reader.readByte();
            int pitchEnvLoopEnd = reader.readByte();
            int[][] pitchEnvPoints = new int[25][2];
            for (int i = 0; i < 25; i++) {
                pitchEnvPoints[i][0] = reader.readWord();
                pitchEnvPoints[i][1] = reader.readWord();
            }

            int vibType = reader.readByte();
            int vibSweep = reader.readByte();
            int vibDepth = reader.readByte();
            int vibRate = reader.readByte();

            int fadeOut = reader.readWord();
            int midiChannel = reader.readByte();
            int midiBank = reader.readByte();
            int midiPatch = reader.readByte();
            int midiBend = reader.readByte();
            int globalVolIns = reader.readByte();
            int panningIns = reader.readWord();
            int nna = reader.readByte();
            int pps = reader.readByte();
            int ppc = reader.readByte();
            int dct = reader.readByte();
            int dca = reader.readByte();
            int volSwing = reader.readWord();
            int panSwing = reader.readWord();
            int cutoff = reader.readByte();
            int resonance = reader.readByte();

            if (isXM) {
                instrVibrato[ins][0] = vibType;
                instrVibrato[ins][1] = vibSweep;
                instrVibrato[ins][2] = vibDepth;
                instrVibrato[ins][3] = vibRate;
            }

            Instrument target = new Instrument();
            target.name = insName;
            target.dosFileName = insFilename;

            target.sampleIndex = new int[120];
            target.noteIndex = new int[120];
            if (isXM) {
                // like XMMod: index is the raw XM note (0-based), noteIndex is 0-based
                for (int i = 0; i < 96; i++) {
                    target.sampleIndex[i] = sampleMap[i][1] + 1;
                    target.noteIndex[i] = i;
                }
            } else {
                // like ImpulseTrackerMod: noteIndex holds the raw (0-based) IT note
                for (int i = 0; i < 120; i++) {
                    target.noteIndex[i] = sampleMap[i][0];
                    target.sampleIndex[i] = sampleMap[i][1] + 1;
                }
            }

            target.volumeEnvelope = convertEnvelope(volEnvFlags, volEnvNodes, volEnvSustainStart, volEnvSustainEnd, volEnvLoopStart, volEnvLoopEnd, volEnvPoints, Envelope.EnvelopeType.volume, isXM, 0);
            target.panningEnvelope = convertEnvelope(panEnvFlags, panEnvNodes, panEnvSustainStart, panEnvSustainEnd, panEnvLoopStart, panEnvLoopEnd, panEnvPoints, Envelope.EnvelopeType.panning, isXM, 0);
            target.pitchEnvelope = convertEnvelope(pitchEnvFlags, pitchEnvNodes, pitchEnvSustainStart, pitchEnvSustainEnd, pitchEnvLoopStart, pitchEnvLoopEnd, pitchEnvPoints, Envelope.EnvelopeType.pitch, isXM, 5);

            target.volumeFadeOut = fadeOut;
            if (midiChannel >= 128) {
                target.mixPlugIn = midiChannel - 127;
            } else if (midiChannel < 17 && (insFlags & 1) != 0) {
                target.midiChannel = midiChannel + 1;
            } else if (midiChannel > 0 && midiChannel < 17) {
                target.midiChannel = midiChannel + 1;
            }
            if (target.midiChannel > 0) {
                if (isXM) {
                    target.midiProgram = midiPatch + 1;
                } else {
                    if (midiBank < 128) target.midiBank = midiBank + 1;
                    if (midiPatch < 128) target.midiProgram = midiPatch + 1;
                }
                target.pitchWheelDepth = midiBend;
                target.hasValidMidiData = true;
            }

            if (isIT) { // javamod instrument globalVolume is 0..128, like the IT file value
                target.globalVolume = Math.min(globalVolIns, 128);
            }

            if (panningIns <= 256) {
                target.defaultPanning = panningIns;
                target.setPanning = true;
            }

            target.NNA = nna;
            target.pitchPanSeparation = pps;
            target.pitchPanCenter = ppc;
            target.duplicateNoteCheck = dct;
            target.duplicateNoteAction = dca;
            target.randomVolumeVariation = Math.min(volSwing, 100);
            target.randomPanningVariation = Math.min(panSwing, 256) / 4;

            // keep the 0x80 "enabled" bit - the mixer checks for it
            target.initialFilterCutoff = cutoff;
            target.initialFilterResonance = resonance;

            instrumentContainer.setInstrument(ins, target);
        }

        if (isSampleMode) {
            setNInstruments(0);
        }

        // Samples
        class MO3SampleHeader {
            String name;
            String filename;
            int freqFinetune;
            int transpose;
            int defaultVolume;
            int panning;
            int length;
            int loopStart;
            int loopEnd;
            int flags;
            int vibType;
            int vibSweep;
            int vibDepth;
            int vibRate;
            int globalVol;
            int sustainStart;
            int sustainEnd;
            int compressedSize;
            int encoderDelay;
            int sharedOggHeader;
        }

        MO3SampleHeader[] sampleHeaders = new MO3SampleHeader[numSamples];
        for (int smp = 0; smp < numSamples; smp++) {
            MO3SampleHeader sh = new MO3SampleHeader();
            sh.name = reader.readNullString();
            if (version >= 5) {
                sh.filename = reader.readNullString();
            } else {
                sh.filename = "";
            }

            sh.freqFinetune = reader.readDWord();
            sh.transpose = (byte) reader.readByte();
            sh.defaultVolume = reader.readByte();
            sh.panning = reader.readWord();
            sh.length = reader.readDWord();
            sh.loopStart = reader.readDWord();
            sh.loopEnd = reader.readDWord();
            sh.flags = reader.readWord();
            sh.vibType = reader.readByte();
            sh.vibSweep = reader.readByte();
            sh.vibDepth = reader.readByte();
            sh.vibRate = reader.readByte();
            sh.globalVol = reader.readByte();
            sh.sustainStart = reader.readDWord();
            sh.sustainEnd = reader.readDWord();
            sh.compressedSize = reader.readDWord();
            sh.encoderDelay = reader.readWord();

            if (version >= 5 && (sh.flags & 0xF000) == 0x7000) {
                sh.sharedOggHeader = (short) reader.readWord();
            } else {
                sh.sharedOggHeader = 0;
            }
            sampleHeaders[smp] = sh;
        }

        if (isXM) {
            for (int ins = 0; ins < numInstruments; ins++) {
                Instrument instrument = instrumentContainer.getInstrument(ins);
                if (instrument != null && instrument.sampleIndex != null) {
                    for (int key = 0; key < 120; key++) {
                        int smpIdx = instrument.sampleIndex[key] - 1;
                        if (smpIdx >= 0 && smpIdx < numSamples) {
                            Sample sample = instrumentContainer.getSample(smpIdx);
                            if (sample != null) {
                                sample.vibratoType = instrVibrato[ins][0];
                                sample.vibratoSweep = instrVibrato[ins][1];
                                sample.vibratoDepth = instrVibrato[ins][2];
                                sample.vibratoRate = instrVibrato[ins][3];
                            }
                        }
                    }
                }
            }
        }

        // Plugin data - we cannot use it, but need to skip it to stay aligned
        if ((flags & 0x100000) != 0 && reader.ptr < musicSize) {
            int pluginFlags = reader.readByte();
            if ((pluginFlags & 1) != 0) { // channel plugins
                reader.skip(numChannels << 2);
            }
            while (reader.ptr < musicSize) {
                int plug = reader.readByte();
                if (plug == 0) break;
                int len = reader.readDWord();
                if (len < 0 || reader.ptr + len > musicSize) break;
                reader.skip(len);
            }
        }

        // Subchunks reading
        int cwtv = 0;
        while (reader.ptr + 8 <= musicSize) {
            int id = reader.readDWord();
            int len = reader.readDWord();
            if (len < 0 || reader.ptr + len > musicSize) break;
            byte[] chunkData = reader.readBytes(len);

            if (id == 0x53524556) { // MagicLE("VERS"): tracker magic bytes (depending on format)
                MemoryReader subReader = new MemoryReader(chunkData);
                if ((isIT || isS3M) && len >= 2) {
                    cwtv = subReader.readWord();
                }
            } else if (id == 0x54504D4F) { // "OMPT"
                MemoryReader subReader = new MemoryReader(chunkData);
                if (subReader.ptr + 4 <= len) {
                    int subId = subReader.readDWord();
                    if (subId == 0x4D414E50) { // "PNAM"
                        int nameLen = subReader.readDWord();
                        byte[] nameBytes = subReader.readBytes(nameLen);
                        int namedPats = Math.min(nameLen / 32, numPatterns);
                        String[] patNames = new String[numPatterns];
                        for (int pat = 0; pat < namedPats; pat++) {
                            patNames[pat] = new String(nameBytes, pat * 32, 32, StandardCharsets.US_ASCII).trim();
                        }
                        patternContainer.setPatternNames(patNames);
                    }
                    // Read extended properties using subchunks
                    try {
                        ByteArrayRandomAccessInputStream subStream = new ByteArrayRandomAccessInputStream(chunkData);
                        loadExtendedInstrumentProperties(subStream);
                        subStream.seek(0);
                        loadExtendedSongProperties(subStream, true);
                    } catch (Exception e) {
                        logger.log(Level.ERROR, "Error loading MPTM sub-extensions", e);
                    }
                }
            }
        }

        // Ignore MIDI data in files made with IT older than version 2.14 and old ST3 versions
        if (midiConfig != null &&
                ((isIT && cwtv >= 0x0100 && cwtv < 0x0214)
                || (isS3M && cwtv >= 0x3100 && cwtv < 0x3214)
                || (isS3M && cwtv >= 0x1300 && cwtv < 0x1320))) {
            midiConfig.clearZxxMacros();
        }

        // Seek to the start of sample data
        if (version < 5) {
            inputStream.seek(headerOffset + musicDataCompressedSize);
        } else {
            inputStream.seek(12 + compressedSize);
        }

        byte[][] sampleCompressedData = new byte[numSamples][];

        boolean frequencyIsHertz = (version >= 5 || (flags & 0x0001) == 0);

        // Pass 1: Load uncompressed sample data and read compressed sample chunks
        for (int smp = 0; smp < numSamples; smp++) {
            Sample sample = new Sample();
            MO3SampleHeader sh = sampleHeaders[smp];
            sample.name = sh.name;
            sample.dosFileName = sh.filename;
            sample.sampleLength = sh.length;
            sample.byteLength = sh.length;

            if (isIT || isS3M) {
                if (frequencyIsHertz) {
                    sample.baseFrequency = sh.freqFinetune;
                } else {
                    sample.baseFrequency = (int) Math.round(8363.0 * Math.pow(2.0, (double) (sh.freqFinetune + 1408) / 1536.0));
                }
            } else {
                sample.fineTune = sh.freqFinetune & 0xFF;
                if (!isMTM) {
                    sample.fineTune -= 128;
                }
                sample.transpose = sh.transpose;
            }

            sample.volume = Math.min(sh.defaultVolume, 64);
            if (sh.panning <= 256) {
                sample.defaultPanning = sh.panning;
                sample.setPanning = true;
            }
            sample.loopStart = sh.loopStart;
            sample.loopStop = sh.loopEnd;
            sample.loopLength = sh.loopEnd - sh.loopStart;

            if ((sh.flags & 0x10) != 0) {
                sample.loopType |= ModConstants.LOOP_ON;
                if ((sh.flags & 0x20) != 0) {
                    sample.loopType |= ModConstants.LOOP_IS_PINGPONG;
                }
            }
            if ((sh.flags & 0x100) != 0) {
                sample.loopType |= ModConstants.LOOP_SUSTAIN_ON;
                sample.sustainLoopStart = sh.sustainStart;
                sample.sustainLoopStop = sh.sustainEnd;
                sample.sustainLoopLength = sh.sustainEnd - sh.sustainStart;
                if ((sh.flags & 0x200) != 0) {
                    sample.loopType |= ModConstants.LOOP_SUSTAIN_IS_PINGPONG;
                }
            }
            sample.isStereo = (sh.flags & 0x400) != 0;

            sample.vibratoType = autovibit2xm[sh.vibType & 7];
            sample.vibratoSweep = sh.vibSweep;
            sample.vibratoDepth = sh.vibDepth;
            sample.vibratoRate = sh.vibRate;

            // javamod expects 64 for formats without sample global volume (default is 0 = silence!)
            sample.globalVolume = isIT ? Math.min(sh.globalVol, 64) : ModConstants.MAXSAMPLEVOLUME;

            int sampleType = 0;
            if ((sh.flags & 0x01) != 0) {
                sampleType |= ModConstants.SM_16BIT;
            }
            if ((sh.flags & 0x400) != 0) {
                sampleType |= ModConstants.SM_STEREO;
            }
            sample.sampleType = sampleType;

            instrumentContainer.setSample(smp, sample);

            if ((sh.flags & 0xF000) == 0 && sh.compressedSize == 0) {
                // Uncompressed sample
                if (sh.length > 0) {
                    readSampleData(sample, inputStream);
                }
            } else if (sh.compressedSize > 0) {
                byte[] compData = new byte[sh.compressedSize];
                readFully(inputStream, compData);
                sampleCompressedData[smp] = compData;
            }
        }

        // Pass 2: Decompress sample data
        for (int smp = 0; smp < numSamples; smp++) {
            Sample sample = instrumentContainer.getSample(smp);
            MO3SampleHeader sh = sampleHeaders[smp];

            if (sh.compressedSize < 0) {
                int sourceIdx = smp + sh.compressedSize;
                if (sourceIdx >= 0 && sourceIdx < numSamples) {
                    Sample source = instrumentContainer.getSample(sourceIdx);
                    sample.sampleL = source.sampleL;
                    sample.sampleR = source.sampleR;
                    sample.sampleLength = source.sampleLength;
                    sample.byteLength = source.byteLength;
                }
                continue;
            }

            if (sh.length <= 0 || sh.compressedSize <= 0) continue;

            byte[] compressedData = sampleCompressedData[smp];
            boolean is16Bit = (sh.flags & 0x01) != 0;
            int compression = sh.flags & 0xF000;

            if (compression == 0x2000 || compression == 0x4000) { // smpDeltaCompression / smpDeltaPrediction
                boolean prediction = compression == 0x4000;
                sample.allocSampleData();
                int[] offset = { 0 };
                try {
                    if (is16Bit) {
                        MO3Decompressor.unpackDelta16(compressedData, offset, sample.sampleL, sample.sampleR, sample.sampleLength, sample.isStereo ? 2 : 1, prediction);
                    } else {
                        MO3Decompressor.unpackDelta8(compressedData, offset, sample.sampleL, sample.sampleR, sample.sampleLength, sample.isStereo ? 2 : 1, prediction);
                    }
                } catch (ArrayIndexOutOfBoundsException e) {
                    logger.log(Level.ERROR, "Truncated delta compressed sample: " + sample.name, e);
                }
            } else if (compression == 0x1000) { // smpCompressionMPEG (MP3)
                try {
                    decodeMp3Sample(compressedData, sample, sh.encoderDelay & 0xFFFF);
                } catch (Exception e) {
                    logger.log(Level.ERROR, "Failed to decode MP3 sample: " + sample.name, e);
                }
            } else if (compression == 0x3000 || compression == 0x7000) { // smpCompressionOgg or smpSharedOgg
                try {
                    if (compression == 0x7000) {
                        int sharedIdx = smp + sh.sharedOggHeader;
                        byte[] headerData = sampleCompressedData[sharedIdx];
                        int headerSize = sh.encoderDelay & 0xFFFF;
                        decodeOggSample(compressedData, headerData, headerSize, sample);
                    } else {
                        decodeOggSample(compressedData, null, 0, sample);
                    }
                } catch (Exception e) {
                    logger.log(Level.ERROR, "Failed to decode Ogg Vorbis sample: " + sample.name, e);
                }
            }
            sample.fixSampleLoops(getModType());
        }

        removeEndOfArrangement();
    }

    private static Envelope convertEnvelope(int flags, int numNodes, int sustainStart, int sustainEnd, int loopStart, int loopEnd, int[][] points, Envelope.EnvelopeType envType, boolean isXM, int envShift) {
        Envelope env = new Envelope(envType);
        env.on = (flags & 0x01) != 0;
        env.sustain = (flags & 0x02) != 0;
        env.loop = (flags & 0x04) != 0;
        env.filter = (flags & 0x10) != 0;
        env.carry = (flags & 0x20) != 0;
        env.xm_style = isXM;

        int nodes = Math.min(numNodes & 0xFF, 25);
        env.setNumberOfPoints(nodes);
        env.positions = new int[nodes];
        env.value = new int[nodes];
        env.sustainStartPoint = sustainStart & 0xFF;
        env.sustainEndPoint = isXM ? env.sustainStartPoint : (sustainEnd & 0xFF);
        env.loopStartPoint = loopStart & 0xFF;
        env.loopEndPoint = loopEnd & 0xFF;

        for (int ev = 0; ev < nodes; ev++) {
            env.positions[ev] = (short) points[ev][0]; // points are int16
            if (ev > 0 && env.positions[ev] < env.positions[ev - 1]) {
                env.positions[ev] = env.positions[ev - 1] + 1;
            }
            int val = ((short) points[ev][1]) >> envShift;
            if (val < 0) val = 0;
            if (val > 64) val = 64;
            env.value[ev] = val;
        }
        env.sanitize(64);
        return env;
    }

    /**
     * @param encoderDelay number of bytes of decoded output to ignore (see OpenMPT).
     *        The LAME info frame - if any - is decoded to (usually silent) PCM by
     *        JLayer, which OpenMPT accounts for in this delay value as well.
     */
    private static void decodeMp3Sample(byte[] mp3Data, Sample sample, int encoderDelay) throws Exception {
        java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(mp3Data);
        Bitstream bitstream = new Bitstream(bais);
        javazoom.jl.decoder.Decoder decoder = new javazoom.jl.decoder.Decoder();
        sample.allocSampleData();

        int channels = sample.isStereo ? 2 : 1;
        int skipSamples = encoderDelay / (2 * channels); // decoded output is 16 bit
        int destLIdx = 0;
        int destRIdx = 0;

        Header h;
        while ((h = bitstream.readFrame()) != null) {
            SampleBuffer sb = (SampleBuffer) decoder.decodeFrame(h, bitstream);
            short[] buffer = sb.getBuffer();
            int len = sb.getBufferLength();

            int outChannels = decoder.getOutputChannels();
            if (outChannels == 2) {
                for (int i = 0; i < len; i += 2) {
                    if (skipSamples > 0) {
                        skipSamples--;
                        continue;
                    }
                    if (destLIdx < sample.sampleLength) {
                        sample.sampleL[destLIdx++] = ModConstants.promoteSigned16BitToSigned32Bit(buffer[i]);
                    }
                    if (channels == 2 && destRIdx < sample.sampleLength) {
                        sample.sampleR[destRIdx++] = ModConstants.promoteSigned16BitToSigned32Bit(buffer[i + 1]);
                    }
                }
            } else {
                for (int i = 0; i < len; i++) {
                    if (skipSamples > 0) {
                        skipSamples--;
                        continue;
                    }
                    if (destLIdx < sample.sampleLength) {
                        sample.sampleL[destLIdx++] = ModConstants.promoteSigned16BitToSigned32Bit(buffer[i]);
                        if (channels == 2 && destRIdx < sample.sampleLength) {
                            sample.sampleR[destRIdx++] = ModConstants.promoteSigned16BitToSigned32Bit(buffer[i]);
                        }
                    }
                }
            }
            bitstream.closeFrame();
        }
    }

    private static void decodeOggSample(byte[] oggData, byte[] headerData, int headerSize, Sample sample) throws Exception {
        byte[] mergedData = oggData;
        if (headerSize > 0 && headerData != null) {
            int headerSerial = getOggPageSerial(headerData, 0);
            int dataSerial = getOggPageSerial(oggData, 0);
            if (headerSerial != dataSerial) {
                byte[] headerCopy = new byte[headerSize];
                System.arraycopy(headerData, 0, headerCopy, 0, headerSize);
                int ptr = 0;
                while (ptr < headerSize) {
                    if (ptr + 26 > headerSize) break;
                    if (headerCopy[ptr] == 'O' && headerCopy[ptr + 1] == 'g' && headerCopy[ptr + 2] == 'g' && headerCopy[ptr + 3] == 'S') {
                        headerCopy[ptr + 14] = (byte) (dataSerial & 0xFF);
                        headerCopy[ptr + 15] = (byte) ((dataSerial >> 8) & 0xFF);
                        headerCopy[ptr + 16] = (byte) ((dataSerial >> 16) & 0xFF);
                        headerCopy[ptr + 17] = (byte) ((dataSerial >> 24) & 0xFF);
                        headerCopy[ptr + 22] = 0;
                        headerCopy[ptr + 23] = 0;
                        headerCopy[ptr + 24] = 0;
                        headerCopy[ptr + 25] = 0;

                        int nsegs = headerCopy[ptr + 26] & 0xFF;
                        int pageLen = 27 + nsegs;
                        for (int i = 0; i < nsegs; i++) {
                            pageLen += headerCopy[ptr + 27 + i] & 0xFF;
                        }
                        int crc = oggCrc(headerCopy, ptr, pageLen);
                        headerCopy[ptr + 22] = (byte) (crc & 0xFF);
                        headerCopy[ptr + 23] = (byte) ((crc >> 8) & 0xFF);
                        headerCopy[ptr + 24] = (byte) ((crc >> 16) & 0xFF);
                        headerCopy[ptr + 25] = (byte) ((crc >> 24) & 0xFF);

                        ptr += pageLen;
                    } else {
                        ptr++;
                    }
                }
                mergedData = new byte[headerSize + oggData.length];
                System.arraycopy(headerCopy, 0, mergedData, 0, headerSize);
                System.arraycopy(oggData, 0, mergedData, headerSize, oggData.length);
            } else {
                mergedData = new byte[headerSize + oggData.length];
                System.arraycopy(headerData, 0, mergedData, 0, headerSize);
                System.arraycopy(oggData, 0, mergedData, headerSize, oggData.length);
            }
        }

        java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(mergedData);
        com.jcraft.jogg.SyncState oggSyncState = new com.jcraft.jogg.SyncState();
        com.jcraft.jogg.StreamState oggStreamState = new com.jcraft.jogg.StreamState();
        com.jcraft.jogg.Page oggPage = new com.jcraft.jogg.Page();
        com.jcraft.jogg.Packet oggPacket = new com.jcraft.jogg.Packet();

        com.jcraft.jorbis.Info vorbisInfo = new com.jcraft.jorbis.Info();
        com.jcraft.jorbis.Comment vorbisComment = new com.jcraft.jorbis.Comment();
        com.jcraft.jorbis.DspState vorbisDSPState = new com.jcraft.jorbis.DspState();
        com.jcraft.jorbis.Block vorbisBlock = new com.jcraft.jorbis.Block(vorbisDSPState);

        oggSyncState.init();

        byte[] buffer = new byte[4096];
        boolean eos = false;
        int state = 0;

        sample.allocSampleData();
        int destLIdx = 0;
        int destRIdx = 0;
        int channels = sample.isStereo ? 2 : 1;

        while (!eos) {
            int index = oggSyncState.buffer(4096);
            int bytes = bais.read(oggSyncState.data, index, 4096);
            if (bytes <= 0) {
                oggSyncState.wrote(0);
                eos = true;
                break;
            }
            oggSyncState.wrote(bytes);

            while (oggSyncState.pageout(oggPage) == 1) {
                if (state == 0) {
                    oggStreamState.init(oggPage.serialno());
                    oggStreamState.reset();
                    if (oggStreamState.pagein(oggPage) < 0) {
                        throw new IOException("Error reading first page of Ogg bitstream data");
                    }
                    if (oggStreamState.packetout(oggPacket) != 1) {
                        throw new IOException("Error reading initial header packet");
                    }
                    vorbisInfo.init();
                    vorbisComment.init();
                    if (vorbisInfo.synthesis_headerin(vorbisComment, oggPacket) < 0) {
                        throw new IOException("This Ogg bitstream does not contain Vorbis audio data");
                    }

                    int i = 0;
                    while (i < 2) {
                        int result = oggSyncState.pageout(oggPage);
                        if (result == 0) {
                            index = oggSyncState.buffer(4096);
                            bytes = bais.read(oggSyncState.data, index, 4096);
                            if (bytes <= 0) {
                                throw new IOException("End of file before finding all Vorbis headers");
                            }
                            oggSyncState.wrote(bytes);
                            continue;
                        }
                        oggStreamState.pagein(oggPage);
                        while (i < 2) {
                            result = oggStreamState.packetout(oggPacket);
                            if (result == 0) break;
                            vorbisInfo.synthesis_headerin(vorbisComment, oggPacket);
                            i++;
                        }
                    }
                    vorbisDSPState.synthesis_init(vorbisInfo);
                    vorbisBlock.init(vorbisDSPState);
                    state = 1;
                } else {
                    if (oggStreamState.pagein(oggPage) < 0) {
                        continue;
                    }
                    while (oggStreamState.packetout(oggPacket) == 1) {
                        if (vorbisBlock.synthesis(oggPacket) == 0) {
                            vorbisDSPState.synthesis_blockin(vorbisBlock);
                        }

                        float[][][] pcmFloat = new float[1][][];
                        int[] pcmIndex = new int[vorbisInfo.channels];
                        int samples;
                        while ((samples = vorbisDSPState.synthesis_pcmout(pcmFloat, pcmIndex)) > 0) {
                            for (int s = 0; s < samples; s++) {
                                float valL = pcmFloat[0][0][pcmIndex[0] + s];
                                int valueL = (int) (valL * 32767);
                                if (valueL > 32767) valueL = 32767;
                                else if (valueL < -32768) valueL = -32768;

                                if (destLIdx < sample.sampleLength) {
                                    sample.sampleL[destLIdx++] = ModConstants.promoteSigned16BitToSigned32Bit((short) valueL);
                                }

                                if (vorbisInfo.channels > 1) {
                                    float valR = pcmFloat[0][1][pcmIndex[1] + s];
                                    int valueR = (int) (valR * 32767);
                                    if (valueR > 32767) valueR = 32767;
                                    else if (valueR < -32768) valueR = -32768;

                                    if (destRIdx < sample.sampleLength) {
                                        sample.sampleR[destRIdx++] = ModConstants.promoteSigned16BitToSigned32Bit((short) valueR);
                                    }
                                } else if (channels == 2) {
                                    if (destRIdx < sample.sampleLength) {
                                        sample.sampleR[destRIdx++] = ModConstants.promoteSigned16BitToSigned32Bit((short) valueL);
                                    }
                                }
                            }
                            vorbisDSPState.synthesis_read(samples);
                        }
                    }
                    if (oggPage.eos() != 0) {
                        eos = true;
                    }
                }
            }
        }
        oggStreamState.clear();
        vorbisBlock.clear();
        vorbisDSPState.clear();
        vorbisInfo.clear();
    }

    private static int getOggPageSerial(byte[] data, int offset) {
        if (data == null || data.length < offset + 18) return 0;
        return (data[offset + 14] & 0xFF) | ((data[offset + 15] & 0xFF) << 8) | ((data[offset + 16] & 0xFF) << 16) | ((data[offset + 17] & 0xFF) << 24);
    }

    private static final int[] CRC_LOOKUP = new int[256];
    static {
        for (int i = 0; i < 256; i++) {
            int r = i << 24;
            for (int j = 0; j < 8; j++) {
                if ((r & 0x80000000) != 0) {
                    r = (r << 1) ^ 0x04c11db7;
                } else {
                    r <<= 1;
                }
            }
            CRC_LOOKUP[i] = r;
        }
    }

    private static int oggCrc(byte[] data, int offset, int length) {
        int crc = 0;
        for (int i = 0; i < length; i++) {
            crc = (crc << 8) ^ CRC_LOOKUP[((crc >>> 24) ^ (data[offset + i] & 0xFF)) & 0xFF];
        }
        return crc;
    }

    private static class MemoryReader {
        byte[] data;
        int ptr = 0;

        MemoryReader(byte[] data) {
            this.data = data;
        }

        int readByte() {
            return data[ptr++] & 0xFF;
        }

        int readWord() {
            int val = (data[ptr] & 0xFF) | ((data[ptr + 1] & 0xFF) << 8);
            ptr += 2;
            return val;
        }

        int readDWord() {
            int val = (data[ptr] & 0xFF) | ((data[ptr + 1] & 0xFF) << 8) | ((data[ptr + 2] & 0xFF) << 16) | ((data[ptr + 3] & 0xFF) << 24);
            ptr += 4;
            return val;
        }

        String readNullString() {
            int start = ptr;
            while (ptr < data.length && data[ptr] != 0) {
                ptr++;
            }
            String s = new String(data, start, ptr - start, StandardCharsets.US_ASCII);
            if (ptr < data.length) ptr++;
            return s;
        }

        void skip(int bytes) {
            ptr += bytes;
        }

        byte[] readBytes(int length) {
            byte[] dest = new byte[length];
            System.arraycopy(data, ptr, dest, 0, length);
            ptr += length;
            return dest;
        }
    }

    private static class ByteArrayRandomAccessInputStream implements RandomAccessInputStream {
        private final byte[] buf;
        private int pos;
        private int markPos;

        public ByteArrayRandomAccessInputStream(byte[] buf) {
            this.buf = buf;
            this.pos = 0;
            this.markPos = 0;
        }

        @Override
        public File getFile() {
            return null;
        }

        @Override
        public int available() throws IOException {
            return buf.length - pos;
        }

        @Override
        public void close() throws IOException {
        }

        @Override
        public void mark(int readlimit) {
            markPos = pos;
        }

        @Override
        public boolean markSupported() {
            return true;
        }

        @Override
        public int read() throws IOException {
            if (pos >= buf.length) return -1;
            return buf[pos++] & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (pos >= buf.length) return -1;
            int count = Math.min(len, buf.length - pos);
            System.arraycopy(buf, pos, b, off, count);
            pos += count;
            return count;
        }

        @Override
        public void reset() throws IOException {
            pos = markPos;
        }

        @Override
        public long skip(long n) throws IOException {
            int count = (int) Math.min(n, buf.length - pos);
            pos += count;
            return count;
        }

        @Override
        public long getFilePointer() throws IOException {
            return pos;
        }

        @Override
        public void seek(long pos) throws IOException {
            this.pos = (int) Math.max(0, Math.min(pos, buf.length));
        }

        @Override
        public long length() throws IOException {
            return buf.length;
        }
    }

    private static class MO3MidiMacros extends MidiMacros {
        private final String[] mySFX = new String[16];
        private final String[] myZXX = new String[128];

        public void setMidiSFXExt(int index, String value) {
            mySFX[index] = value;
        }

        public void setMidiZXXExt(int index, String value) {
            myZXX[index] = value;
        }

        @Override
        public String getMidiSFXExt(int index) {
            return mySFX[index];
        }

        @Override
        public String getMidiZXXExt(int index) {
            return myZXX[index];
        }

        @Override
        public void clearZxxMacros() {
            super.clearZxxMacros();
            // is null when called from the super constructor
            if (myZXX != null) java.util.Arrays.fill(myZXX, "");
        }
    }

    private static void readFully(RandomAccessInputStream in, byte[] b) throws IOException {
        int read = 0;
        while (read < b.length) {
            int r = in.read(b, read, b.length - read);
            if (r == -1) throw new java.io.EOFException();
            read += r;
        }
    }
}
