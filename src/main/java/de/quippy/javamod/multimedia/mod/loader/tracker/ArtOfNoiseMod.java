/*
 * MIT License
 *
 * Copyright (c) 2023 Thomas Neumann
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package de.quippy.javamod.multimedia.mod.loader.tracker;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

import de.quippy.javamod.io.ModfileInputStream;
import de.quippy.javamod.io.RandomAccessInputStream;
import de.quippy.javamod.multimedia.mod.ModConstants;
import de.quippy.javamod.multimedia.mod.loader.instrument.Envelope;
import de.quippy.javamod.multimedia.mod.loader.instrument.Envelope.EnvelopeType;
import de.quippy.javamod.multimedia.mod.loader.instrument.Instrument;
import de.quippy.javamod.multimedia.mod.loader.instrument.InstrumentsContainer;
import de.quippy.javamod.multimedia.mod.loader.instrument.Sample;
import de.quippy.javamod.multimedia.mod.loader.pattern.PatternContainer;
import de.quippy.javamod.multimedia.mod.loader.pattern.PatternElement;


/**
 * Loader for "Art Of Noise" modules (Amiga, by Bastian Spiegel).
 * <p>
 * AON is an IFF-like chunk format ("AON4" / "AON8" mark) with 4 or 8 voices,
 * ProTracker-like effects plus letter effects (G..X), 5 octaves, sample and
 * synthesis (wave table) instruments with a simple ADSR envelope.
 * <p>
 * The module is converted to javamod's internal XM representation:
 * <ul>
 * <li>sample instruments become normal samples</li>
 * <li>synth instruments become looped samples of their first wave frame
 *     (the wave table sequence itself is not emulated) plus auto vibrato</li>
 * <li>the ADSR envelope becomes an XM instrument volume envelope</li>
 * <li>AON letter effects are approximated with XM effect / volume column
 *     combinations, pure synth control effects (H/I/J/U) are dropped</li>
 * </ul>
 * Ported from NostalgicPlayer (https://github.com/neumatho/NostalgicPlayer)
 *
 * @author Thomas Neumann
 * @since 11.07.2026
 */
public class ArtOfNoiseMod extends ProTrackerMod {

    private static final String[] MODFILEEXTENSION = {
            "aon", "aon8"
    };

    /** AON notes are 1..60, note 1 = period 3424 = javamod noteIndex 13 (see ModConstants.noteValues) */
    private static final int NOTE_OFFSET = 12;

    /** fine volume slide nibbles of the L/M effects: 0..7 up, 8..15 = -8..-1 down */
    private static final int[] NIBBLE_TAB = {
            0, 1, 2, 3, 4, 5, 6, 7, -8, -7, -6, -5, -4, -3, -2, -1
    };

    private static final int[] PAN_4 = {
            ModConstants.OLD_PANNING_LEFT, ModConstants.OLD_PANNING_RIGHT, ModConstants.OLD_PANNING_RIGHT, ModConstants.OLD_PANNING_LEFT
    };
    private static final int[] PAN_8 = {
            ModConstants.OLD_PANNING_LEFT, ModConstants.OLD_PANNING_LEFT, ModConstants.OLD_PANNING_RIGHT, ModConstants.OLD_PANNING_RIGHT,
            ModConstants.OLD_PANNING_RIGHT, ModConstants.OLD_PANNING_RIGHT, ModConstants.OLD_PANNING_LEFT, ModConstants.OLD_PANNING_LEFT
    };

    private String songMessage;

    /** one parsed INST chunk entry - either sample (type 0) or synth (type 1) */
    private static class AONInstrument {

        String name = "";
        int type;
        int volume;
        int fineTune;
        int waveForm;

        // sample instrument (all in words)
        int startOffset;
        int length;
        int loopStart;
        int loopLength;

        // synth instrument
        int synthLength;        // words of one wave frame
        int vibParam;           // like effect 4xy: speed / depth
        int vibDelay;
        int vibWave;            // 0 = sine, 1 = ramp down, 2 = square, 3 = off
        int waveSpeed;
        int waveLength;         // number of frames of the wave table run
        int waveLoopStart;
        int waveLoopLength;
        int waveLoopControl;    // 0 = normal, 1 = backwards, 2 = ping-pong

        // ADSR envelope (values 0..127)
        int envStart;
        int envAdd;
        int envEnd;
        int envSub;
    }

    @Override
    public String[] getFileExtensionList() {
        return MODFILEEXTENSION;
    }

    @Override
    public int getPanningValue(int channel) {
        if (getNChannels() == 8)
            return PAN_8[channel & 7];
        else
            return PAN_4[channel & 3];
    }

    @Override
    public String getSongMessage() {
        return songMessage;
    }

    @Override
    public boolean checkLoadingPossible(ModfileInputStream inputStream) throws IOException {
        String mark = inputStream.readString(4);
        inputStream.seek(0);
        return isAONMod(mark);
    }

    /**
     * 4 bytes
     */
    @Override
    public boolean checkLoadingPossible(InputStream inputStream) throws IOException {
        DataInput di = new DataInputStream(inputStream);
        byte[] mark = new byte[4];
        di.readFully(mark);
        return isAONMod(new String(mark));
    }

    private static boolean isAONMod(String mark) {
        return mark.equals("AON4") || mark.equals("AON8");
    }

    /**
     * Convert one AON pattern entry effect to the internal XM representation.
     * Effects 0..F are ProTracker alike, letters G..X are AON specials that
     * get approximated with effect / volume column combinations.
     */
    private static void convertEffect(PatternElement pe, int effect, int arg, int arpIndex, byte[][] arpeggios) {
        int newEffect = 0;
        int newOp = 0;

        switch (effect) {
            case 0x00: // Arpeggio: either ProTracker style (arg != 0) or one of the 16 "professional" arpeggios
                if (arg != 0) {
                    newOp = arg;
                } else if (arpIndex != 0 && arpeggios != null) {
                    // arpeggio definition: first byte high nibble = count of note offsets, then the offset nibbles
                    byte[] arp = arpeggios[arpIndex];
                    int count = Math.min((arp[0] >> 4) & 0x0F, 7); // 4 bytes hold at most 7 offset nibbles
                    if (count > 0) {
                        int[] offsets = new int[count];
                        offsets[0] = arp[0] & 0x0F;
                        for (int i = 1; i < count; i++)
                            offsets[i] = (arp[(i + 1) >> 1] >> (((i & 1) != 0) ? 4 : 0)) & 0x0F;
                        // squeeze into the 0xy two-offset form
                        int x = offsets[(count > 1) ? 1 : 0];
                        int y = offsets[(count > 2) ? 2 : ((count > 1) ? 1 : 0)];
                        newOp = (x << 4) | y;
                    }
                }
                break;
            case 0x01: // Slide up
            case 0x02: // Slide down
            case 0x03: // Tone portamento
            case 0x04: // Vibrato
            case 0x05: // Tone portamento + volume slide
            case 0x06: // Vibrato + volume slide
            case 0x09: // Set sample offset
            case 0x0A: // Volume slide
            case 0x0B: // Position jump
                newEffect = effect;
                newOp = arg;
                break;
            case 0x0C: // Set volume
                newEffect = effect;
                newOp = Math.min(arg, 64);
                break;
            case 0x0D: // Pattern break (decimal parameter like ProTracker)
                newEffect = effect;
                newOp = arg;
                break;
            case 0x0E: // Extra effects
                switch (arg & 0xF0) {
                    case 0x00: // set filter
                    case 0x10: // fine slide up
                    case 0x20: // fine slide down
                    case 0x40: // set vibrato waveform (same values as ProTracker)
                    case 0x60: // pattern loop
                    case 0x90: // retrig note
                    case 0xA0: // fine volume slide up
                    case 0xB0: // fine volume slide down
                    case 0xC0: // note cut
                    case 0xD0: // note delay
                    case 0xE0: // pattern delay
                        newEffect = effect;
                        newOp = arg;
                        break;
                    case 0x50: // E5x: set loop point -> E60
                        newEffect = effect;
                        newOp = 0x60;
                        break;
                    default: // unknown sub effect
                        break;
                }
                break;
            case 0x0F: // Set speed (00-20) / tempo (21-C8) - speed 0x20 does not fit into XM speed range
                newEffect = effect;
                newOp = (arg == 0x20) ? 0x1F : arg;
                break;
            case 0x10: // Gxy: new volume 4+(x*4) after y frames - delay is dropped
                pe.setVolumeEffect(0x01);
                pe.setVolumeEffectOp(Math.min(((arg >> 4) & 0x0F) * 4 + 4, 64));
                break;
            case 0x14: // Kxx: set volume and continue vibrato
                pe.setVolumeEffect(0x01);
                pe.setVolumeEffectOp(Math.min(arg, 64));
                newEffect = 0x04;
                break;
            case 0x15: // Lxy: fine volume slide (x) and portamento up (y)
            case 0x16: // Mxy: fine volume slide (x) and portamento down (y)
                int nibble = NIBBLE_TAB[(arg >> 4) & 0x0F];
                if (nibble != 0) {
                    pe.setVolumeEffect((nibble > 0) ? 0x05 : 0x04); // fine vol slide up / down
                    pe.setVolumeEffectOp((nibble > 0) ? nibble : -nibble);
                }
                newEffect = (effect == 0x15) ? 0x01 : 0x02;
                newOp = arg & 0x0F;
                break;
            case 0x19: // Pxy: fine volume slide (x up, y down) and vibrato
            case 0x1C: // Sxy: fine volume slide (x up, y down) and tone portamento
                int up = (arg >> 4) & 0x0F;
                if (up != 0) {
                    pe.setVolumeEffect(0x05);
                    pe.setVolumeEffectOp(up);
                } else if ((arg & 0x0F) != 0) {
                    pe.setVolumeEffect(0x04);
                    pe.setVolumeEffectOp(arg & 0x0F);
                }
                newEffect = (effect == 0x19) ? 0x04 : 0x03;
                break;
            case 0x1A: // Qxy: portamento down (x*8) and volume slide down (y) - "synth drums"
                if ((arg & 0x0F) != 0) {
                    pe.setVolumeEffect(0x02); // volume slide down
                    pe.setVolumeEffectOp(arg & 0x0F);
                }
                newEffect = 0x02;
                newOp = Math.min(((arg >> 4) & 0x0F) << 3, 0xFF);
                break;
            case 0x1B: // Rxx: set volume and tone portamento
                pe.setVolumeEffect(0x01);
                pe.setVolumeEffectOp(Math.min(arg, 64));
                newEffect = 0x03;
                break;
            // 0x11 (H synth control), 0x12 (I wave table speed), 0x13 (J arpeggio speed),
            // 0x17 (N avoid noise), 0x18 (O oversize), 0x1D (T track volume),
            // 0x1E (U wave table mode), 0x21 (X external event): no equivalent, dropped
            default:
                break;
        }

        pe.setEffect(newEffect);
        pe.setEffectOp(newOp);
    }

    /**
     * Build the XM volume envelope from the AON ADSR values:
     * start at envStart, add envAdd per tick until 127, then subtract
     * envSub per tick down to envEnd and stay there.
     * With envAdd == 0 the replayer plays at constant full volume.
     */
    private static Envelope createVolumeEnvelope(AONInstrument aonIns) {
        Envelope envelope = new Envelope(EnvelopeType.volume);

        if (aonIns.envAdd != 0) {
            int attack = (aonIns.envStart >= 127) ? 0 : (127 - aonIns.envStart + aonIns.envAdd - 1) / aonIns.envAdd;
            boolean hasDecay = aonIns.envSub > 0 && aonIns.envEnd < 127;
            int decay = hasDecay ? (127 - aonIns.envEnd + aonIns.envSub - 1) / aonIns.envSub : 0;

            int nPoints = 1 + ((attack > 0) ? 1 : 0) + (hasDecay ? 1 : 0);
            int[] positions = new int[nPoints];
            int[] values = new int[nPoints];

            int point = 0;
            positions[point] = 0;
            values[point++] = (attack > 0) ? (aonIns.envStart + 1) >> 1 : 64;
            if (attack > 0) {
                positions[point] = attack;
                values[point++] = 64;
            }
            if (hasDecay) {
                positions[point] = attack + decay;
                values[point] = (aonIns.envEnd + 1) >> 1;
            }

            envelope.positions = positions;
            envelope.value = values;
            envelope.setNumberOfPoints(nPoints);
            envelope.on = true;
            envelope.xm_style = true;
            envelope.sanitize(64);
        }

        return envelope;
    }

    private void createSample(int sampleIndex, AONInstrument aonIns, byte[][] waveForms) {
        Sample current = new Sample();
        current.name = aonIns.name;

        byte[] wave = (aonIns.waveForm < waveForms.length) ? waveForms[aonIns.waveForm] : null;

        int startOffset, length, loopStart, loopLength;
        if (aonIns.type == 0) {
            startOffset = aonIns.startOffset << 1;
            length = aonIns.length << 1;
            loopStart = (aonIns.loopStart << 1) - startOffset;
            loopLength = (aonIns.loopLength > 1) ? aonIns.loopLength << 1 : 0;
        } else {
            // synth: loop the first frame of the wave table - the wave sequence itself is not emulated
            startOffset = 0;
            length = aonIns.synthLength << 1;
            loopStart = 0;
            loopLength = length;
        }

        if (wave == null || startOffset >= wave.length) {
            startOffset = length = 0;
        } else if (startOffset + length > wave.length) {
            length = wave.length - startOffset;
        }

        current.byteLength = current.sampleLength = length;

        if (loopLength > 1 && length > 0) {
            if (loopStart < 0) loopStart = 0;
            int loopStop = loopStart + loopLength;
            if (loopStop > length) loopStop = length;
            if (loopStop - loopStart > 1) {
                current.loopStart = loopStart;
                current.loopStop = loopStop;
                current.loopLength = loopStop - loopStart;
                current.loopType = ModConstants.LOOP_ON;
            }
        }

        // finetune -8..+7 like ProTracker, stored as 0..15 - XM units are 16 times finer
        int fine = aonIns.fineTune & 0x0F;
        if (fine > 7) fine -= 16;
        current.fineTune = fine << 4;
        current.baseFrequency = ModConstants.IT_fineTuneTable[fine + 8];
        current.transpose = 0;

        current.volume = Math.min(aonIns.volume, 64);
        current.globalVolume = ModConstants.MAXSAMPLEVOLUME;

        // synth vibrato as XM auto vibrato (AON: 0 = sine, 1 = ramp down, 2 = square, 3 = off)
        if (aonIns.type == 1 && aonIns.vibWave != 3 && aonIns.vibParam != 0) {
            current.vibratoType = switch (aonIns.vibWave) {
                case 1 -> 3;  // ramp down
                case 2 -> 1;  // square
                default -> 0; // sine
            };
            // AON steps a 32 entry table by speed, XM a 256 entry table by rate
            current.vibratoRate = ((aonIns.vibParam >> 4) & 0x0F) << 2;
            current.vibratoDepth = (aonIns.vibParam & 0x0F) << 3;
            current.vibratoSweep = aonIns.vibDelay;
        }

        // Defaults for non-existent SustainLoop
        current.sustainLoopStart = 0;
        current.sustainLoopStop = 0;
        current.sustainLoopLength = 0;

        current.isStereo = false;
        current.setPanning = false;
        current.defaultPanning = 128;
        current.sampleType = ModConstants.SM_PCMS;

        if (length > 0) {
            current.allocSampleData();
            for (int s = 0; s < length; s++)
                current.sampleL[s] = ModConstants.promoteSigned8BitToSigned32Bit(wave[startOffset + s]);
            current.fixSampleLoops(getModType());
        }

        getInstrumentContainer().setSample(sampleIndex, current);
    }

    private void createInstrument(int insIndex, AONInstrument aonIns) {
        Instrument currentIns = new Instrument();
        currentIns.name = aonIns.name;

        // Defaults for values from IT
        currentIns.globalVolume = 128;
        currentIns.setPanning = false;
        currentIns.defaultPanning = 128;
        currentIns.pitchPanSeparation = -1;
        currentIns.NNA = -1;
        currentIns.initialFilterCutoff = 0;
        currentIns.initialFilterResonance = 0;
        currentIns.randomPanningVariation = -1;

        currentIns.sampleIndex = new int[96];
        currentIns.noteIndex = new int[96];
        for (int i = 0; i < 96; i++) {
            currentIns.sampleIndex[i] = insIndex + 1;
            currentIns.noteIndex[i] = i;
        }

        currentIns.volumeEnvelope = createVolumeEnvelope(aonIns);
        currentIns.panningEnvelope = new Envelope(EnvelopeType.panning);
        currentIns.volumeFadeOut = 0;

        getInstrumentContainer().setInstrument(insIndex, currentIns);
    }

    @Override
    protected void loadModFileInternal(RandomAccessInputStream inputStream) throws IOException {
        setModID(inputStream.readString(4));
        if (!isAONMod(getModID())) throw new IOException("Unsupported AON Module");

        boolean is8Voices = getModID().equals("AON8");
        setNChannels(is8Voices ? 8 : 4);
        setModType(ModConstants.MODTYPE_XM); // AON is converted internally to XM
        setTrackerName("Art Of Noise" + (is8Voices ? " (8 voices)" : ""));
        songFlags = ModConstants.SONG_ISSTEREO;

        setTempo(6);
        setBPMSpeed(125);
        setBaseVolume(ModConstants.MAXGLOBALVOLUME);
        int preAmp = ModConstants.MAX_MIXING_PREAMP / getNChannels();
        setMixingPreAmp((preAmp < ModConstants.MIN_MIXING_PREAMP) ? ModConstants.MIN_MIXING_PREAMP : Math.min(preAmp, 0x80));

        // skip the rest of the 46 byte header
        inputStream.seek(46);

        int numPositions = 0;
        int restartPosition = 0;
        byte[][] arpeggios = null;
        byte[] positionList = null;
        byte[] patternData = null;
        AONInstrument[] aonInstruments = null;
        byte[][] waveForms = null;

        // read all IFF-like chunks (4 byte name, 4 byte big endian size)
        long fileLength = inputStream.getLength();
        while (inputStream.getFilePointer() + 8 <= fileLength) {
            String chunkName = inputStream.readString(4);
            int chunkSize = inputStream.readMotorolaDWord();
            long nextChunk = inputStream.getFilePointer() + chunkSize;
            if (chunkSize < 0 || nextChunk > fileLength) throw new IOException("Corrupt AON module: chunk " + chunkName + " exceeds file size");

            switch (chunkName) {
                case "NAME":
                    setSongName(inputStream.readString(chunkSize));
                    break;
                case "AUTH":
                    author = inputStream.readString(chunkSize);
                    break;
                case "RMRK":
                    if (chunkSize > 0) songMessage = inputStream.readString(chunkSize);
                    break;
                case "INFO":
                    version = inputStream.read();
                    numPositions = inputStream.read();
                    restartPosition = inputStream.read();
                    break;
                case "ARPG": // 16 arpeggios, 4 bytes each
                    arpeggios = new byte[16][4];
                    for (int i = 0; i < 16; i++) inputStream.read(arpeggios[i], 0, 4);
                    break;
                case "PLST":
                    positionList = new byte[chunkSize];
                    inputStream.read(positionList, 0, chunkSize);
                    break;
                case "PATT":
                    patternData = new byte[chunkSize];
                    inputStream.read(patternData, 0, chunkSize);
                    break;
                case "INST": {
                    int numInstruments = chunkSize >> 5; // 32 bytes each
                    aonInstruments = new AONInstrument[numInstruments];
                    for (int i = 0; i < numInstruments; i++) {
                        AONInstrument aonIns = new AONInstrument();
                        aonIns.type = inputStream.read();
                        aonIns.volume = inputStream.read();
                        aonIns.fineTune = inputStream.read();
                        aonIns.waveForm = inputStream.read();
                        if (aonIns.type == 0) { // sample
                            aonIns.startOffset = inputStream.readMotorolaDWord();
                            aonIns.length = inputStream.readMotorolaDWord();
                            aonIns.loopStart = inputStream.readMotorolaDWord();
                            aonIns.loopLength = inputStream.readMotorolaDWord();
                            inputStream.skip(8);
                        } else { // synth
                            aonIns.synthLength = inputStream.read();
                            inputStream.skip(5);
                            aonIns.vibParam = inputStream.read();
                            aonIns.vibDelay = inputStream.read();
                            aonIns.vibWave = inputStream.read();
                            aonIns.waveSpeed = inputStream.read();
                            aonIns.waveLength = inputStream.read();
                            aonIns.waveLoopStart = inputStream.read();
                            aonIns.waveLoopLength = inputStream.read();
                            aonIns.waveLoopControl = inputStream.read();
                            inputStream.skip(10);
                        }
                        aonIns.envStart = inputStream.read();
                        aonIns.envAdd = inputStream.read();
                        aonIns.envEnd = inputStream.read();
                        aonIns.envSub = inputStream.read();
                        aonInstruments[i] = aonIns;
                    }
                    break;
                }
                case "INAM": // instrument names - does not always exist
                    if (aonInstruments != null) {
                        for (int i = 0; i < aonInstruments.length && (i + 1) * 32 <= chunkSize; i++)
                            aonInstruments[i].name = inputStream.readString(32);
                    }
                    break;
                case "WLEN": {
                    int numWaveForms = chunkSize >> 2;
                    waveForms = new byte[numWaveForms][];
                    for (int i = 0; i < numWaveForms; i++) {
                        int length = inputStream.readMotorolaDWord();
                        waveForms[i] = new byte[Math.max(length, 0)];
                    }
                    break;
                }
                case "WAVE":
                    if (waveForms != null) {
                        for (byte[] waveForm : waveForms) {
                            if (waveForm.length > 0) inputStream.read(waveForm, 0, waveForm.length);
                        }
                    }
                    break;
                default: // unknown chunk - skip
                    break;
            }

            inputStream.seek(nextChunk);
        }

        if (numPositions == 0 || positionList == null || patternData == null || aonInstruments == null || waveForms == null || arpeggios == null)
            throw new IOException("Corrupt AON module: missing chunks");

        // song arrangement
        int bytesPerPattern = 4 * 64 * getNChannels();
        setNPattern(patternData.length / bytesPerPattern);
        if (numPositions > positionList.length) numPositions = positionList.length;
        setSongLength(numPositions);
        setSongRestart((restartPosition >= numPositions) ? 0 : restartPosition);
        allocArrangement(numPositions);
        for (int i = 0; i < numPositions; i++) {
            int patternNumber = positionList[i] & 0xFF;
            getArrangement()[i] = (patternNumber < getNPattern()) ? patternNumber : 0;
        }

        // instruments and samples
        setNInstruments(aonInstruments.length);
        setNSamples(aonInstruments.length);
        InstrumentsContainer instrumentContainer = new InstrumentsContainer(this, getNInstruments(), getNSamples());
        setInstrumentContainer(instrumentContainer);
        for (int i = 0; i < aonInstruments.length; i++) {
            createSample(i, aonInstruments[i], waveForms);
            createInstrument(i, aonInstruments[i]);
        }

        // patterns
        PatternContainer patternContainer = new PatternContainer(this, getNPattern(), 64, getNChannels());
        setPatternContainer(patternContainer);
        int index = 0;
        for (int pattNum = 0; pattNum < getNPattern(); pattNum++) {
            for (int row = 0; row < 64; row++) {
                for (int channel = 0; channel < getNChannels(); channel++) {
                    int b1 = patternData[index++] & 0xFF;
                    int b2 = patternData[index++] & 0xFF;
                    int b3 = patternData[index++] & 0xFF;
                    int b4 = patternData[index++] & 0xFF;

                    PatternElement currentElement = patternContainer.createPatternElement(pattNum, row, channel);

                    int note = b1 & 0x3F;
                    if (note > 0 && note <= 60) {
                        int noteIndex = note + NOTE_OFFSET;
                        currentElement.setNoteIndex(noteIndex);
                        currentElement.setPeriod(ModConstants.noteValues[noteIndex - 1]);
                    }

                    int instrument = b2 & 0x3F;
                    currentElement.setInstrument((instrument <= getNInstruments()) ? instrument : 0);

                    int arpIndex = ((b3 & 0xC0) >> 4) | ((b2 & 0xC0) >> 6);
                    convertEffect(currentElement, b3 & 0x3F, b4, arpIndex, arpeggios);
                }
            }
        }
    }
}
