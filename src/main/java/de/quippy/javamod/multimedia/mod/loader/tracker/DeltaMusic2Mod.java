/*
 * MIT License
 *
 * Copyright (c) 2026 Thomas Neumann
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * Loader for "Delta Music 2.0" modules (.dm2, Amiga).
 * <p>
 * Delta Music 2.0 is a pure synthesis play routine: instruments are either
 * one of eight raw samples or a wave table run over 256 byte waveforms with
 * a 5 stage volume table, a 5 stage vibrato table, a permanent pitch bend
 * and freely definable 16 step arpeggios. The module is converted to
 * javamod's internal XM representation:
 * <ul>
 * <li>sample instruments become normal samples</li>
 * <li>synth instruments get their wave table run baked into the sample at
 *     the median period they are played at: the attack frames become the
 *     sample start, the steady state cycle a phase continuous loop; wave 0
 *     frames play the emulated noise generator</li>
 * <li>the volume table becomes an XM instrument volume envelope, the vibrato
 *     table an XM auto vibrato</li>
 * <li>the volume limit effect clips the envelope, which XM volume scaling
 *     cannot express, so limited notes play variant instruments with a
 *     clipped envelope (sharing the base instrument's sample)</li>
 * <li>a DM2 row lasts speed+1 ticks, so XM speed = DM2 speed + 1</li>
 * <li>persistent channel states (pitch bend rate, tone portamento, arpeggio,
 *     volume limit) are re-emitted as XM effects on every row while active;
 *     the 16 step arpeggios are tracked per tick and squeezed into the
 *     closest XM 0xy op per row</li>
 * </ul>
 * Ported from NostalgicPlayer (https://github.com/neumatho/NostalgicPlayer)
 *
 * @author Thomas Neumann
 * @since 12.07.2026
 */
public class DeltaMusic2Mod extends ProTrackerMod {

    private static final String[] MODFILEEXTENSION = {
            "dm2"
    };

    private static final int[] PAN_4 = {
            ModConstants.OLD_PANNING_LEFT, ModConstants.OLD_PANNING_RIGHT, ModConstants.OLD_PANNING_RIGHT, ModConstants.OLD_PANNING_LEFT
    };

    /** generated noise loop length for instruments playing waveform 0 (the noise generator) */
    private static final int NOISE_SAMPLE_LENGTH = 16384;

    private static class DM2Instrument {
        int number;
        int sampleLength;
        int repeatStart;
        int repeatLength;
        final byte[] volumeSpeed = new byte[5];
        final byte[] volumeLevel = new byte[5];
        final byte[] volumeSustain = new byte[5];
        final byte[] vibratoSpeed = new byte[5];
        final byte[] vibratoDelay = new byte[5];
        final byte[] vibratoSustain = new byte[5];
        int pitchBend; // signed: period delta subtracted per tick
        boolean isSample;
        int sampleNumber; // sample slot (&7) for samples, wave table delay for synth
        final byte[] table = new byte[48];
        byte[] sampleData;
    }

    /** the wave table run of a synth instrument: attack frames, then a repeating cycle */
    private static class WaveRun {
        final List<Integer> attack = new ArrayList<>();
        final List<Integer> cycle = new ArrayList<>();
    }

    /** persistent DM2 channel state tracked while converting the patterns */
    private static class ChannelState {
        int pitchBend;     // channel pitch bend rate set by effects 3/4 (period units per tick)
        int instPitchBend; // pitch bend rate of the current instrument
        int portamento;    // tone portamento speed set by effect 5 (0 = off)
        int[] arpeggio;    // effective note offset sequence of the current arpeggio
        int arpeggioTick;  // ticks since the last note trigger (the arpeggio position)
        int maxVolume = 63;
    }

    @Override
    public String[] getFileExtensionList() {
        return MODFILEEXTENSION;
    }

    @Override
    public int getPanningValue(int channel) {
        return PAN_4[channel & 3];
    }

    @Override
    public boolean checkLoadingPossible(ModfileInputStream inputStream) throws IOException {
        if (inputStream.getLength() < 3018) {
            return false;
        }
        inputStream.seek(3014); // 0xbc6
        String mark = inputStream.readString(4);
        inputStream.seek(0);
        return mark.equals(".FNL");
    }

    @Override
    public boolean checkLoadingPossible(InputStream inputStream) throws IOException {
        inputStream.mark(3018);
        DataInput di = new DataInputStream(inputStream);
        byte[] buf = new byte[3018];
        try {
            di.readFully(buf);
        } catch (IOException e) {
            try {
                inputStream.reset();
            } catch (IOException ignored) {
            }
            return false;
        }
        try {
            inputStream.reset();
        } catch (IOException ignored) {
        }
        String mark = new String(buf, 3014, 4);
        return mark.equals(".FNL");
    }

    @Override
    protected void loadModFileInternal(RandomAccessInputStream inputStream) throws IOException {
        if (inputStream.getLength() < 3018) {
            throw new IOException("File too short to be a Delta Music 2.0 module");
        }
        inputStream.seek(3014); // 0xbc6
        String mark = inputStream.readString(4);
        if (!mark.equals(".FNL")) {
            throw new IOException("Unsupported Delta Music 2.0 Module");
        }

        setModType(ModConstants.MODTYPE_XM); // Delta Music 2.0 is converted internally to XM
        setNChannels(4);
        setTrackerName("Delta Music 2.0");
        songFlags = ModConstants.SONG_ISSTEREO | ModConstants.SONG_USEINSTRUMENTS;

        // Read start speed - a DM2 row lasts speed+1 ticks
        inputStream.seek(3003); // 0xbbb
        int startSpeed = inputStream.read();
        setTempo((startSpeed & 0x0F) + 1);
        setBPMSpeed(125);
        setBaseVolume(ModConstants.MAXGLOBALVOLUME);
        int preAmp = ModConstants.MAX_MIXING_PREAMP / getNChannels();
        setMixingPreAmp((preAmp < ModConstants.MIN_MIXING_PREAMP) ? ModConstants.MIN_MIXING_PREAMP : Math.min(preAmp, 0x80));

        // Read arpeggios
        inputStream.seek(3018); // 0xbca
        byte[][] arpeggios = new byte[64][16];
        int[][] arpeggioSequences = new int[64][];
        for (int i = 0; i < 64; i++) {
            inputStream.read(arpeggios[i], 0, 16);
            arpeggioSequences[i] = computeArpeggioSequence(arpeggios[i]);
        }

        // Read tracks metadata
        inputStream.seek(4042); // 0xfca
        int[] trackLoopPositions = new int[4];
        int[] trackLengths = new int[4]; // in track entries (each is 2 bytes)
        for (int i = 0; i < 4; i++) {
            trackLoopPositions[i] = inputStream.readMotorolaWord() & 0xFFFF;
            trackLengths[i] = (inputStream.readMotorolaWord() & 0xFFFF) / 2;
        }

        // Read track entries for each channel
        byte[][] blockNumbers = new byte[4][];
        byte[][] transposes = new byte[4][];
        for (int i = 0; i < 4; i++) {
            blockNumbers[i] = new byte[trackLengths[i]];
            transposes[i] = new byte[trackLengths[i]];
            for (int j = 0; j < trackLengths[i]; j++) {
                blockNumbers[i][j] = (byte) inputStream.read();
                transposes[i][j] = (byte) inputStream.read();
            }
        }

        // Read blocks
        long blocksLength = inputStream.readMotorolaDWord();
        int numBlocks = (int) (blocksLength / 64);
        byte[][][] blocks = new byte[numBlocks][16][4]; // 16 lines, each 4 bytes
        for (int i = 0; i < numBlocks; i++) {
            for (int j = 0; j < 16; j++) {
                blocks[i][j][0] = (byte) inputStream.read(); // note
                blocks[i][j][1] = (byte) inputStream.read(); // instrument
                blocks[i][j][2] = (byte) inputStream.read(); // effect
                blocks[i][j][3] = (byte) inputStream.read(); // effectArg
            }
        }

        // Read instrument offsets
        int[] instrumentOffsets = new int[128];
        instrumentOffsets[0] = 0;
        for (int i = 1; i < 128; i++) {
            instrumentOffsets[i] = inputStream.readMotorolaWord() & 0xFFFF;
        }

        int breakOffset = inputStream.readMotorolaWord() & 0xFFFF;
        long instStartPos = inputStream.getFilePointer();

        List<DM2Instrument> dm2Instruments = new ArrayList<>();
        for (int i = 0; i < 128; i++) {
            if (instrumentOffsets[i] == breakOffset) {
                break;
            }
            inputStream.seek(instStartPos + instrumentOffsets[i]);

            DM2Instrument inst = new DM2Instrument();
            inst.number = i;
            inst.sampleLength = (inputStream.readMotorolaWord() & 0xFFFF) * 2;
            inst.repeatStart = inputStream.readMotorolaWord() & 0xFFFF;
            inst.repeatLength = (inputStream.readMotorolaWord() & 0xFFFF) * 2;
            if (inst.repeatStart + inst.repeatLength >= inst.sampleLength) {
                inst.repeatLength = inst.sampleLength - inst.repeatStart;
            }

            for (int j = 0; j < 5; j++) {
                inst.volumeSpeed[j] = (byte) inputStream.read();
                inst.volumeLevel[j] = (byte) inputStream.read();
                inst.volumeSustain[j] = (byte) inputStream.read();
            }

            for (int j = 0; j < 5; j++) {
                inst.vibratoSpeed[j] = (byte) inputStream.read();
                inst.vibratoDelay[j] = (byte) inputStream.read();
                inst.vibratoSustain[j] = (byte) inputStream.read();
            }

            inst.pitchBend = (short) inputStream.readMotorolaWord();
            inst.isSample = inputStream.read() == 0xFF;
            inst.sampleNumber = inputStream.read();
            inputStream.read(inst.table, 0, 48);

            dm2Instruments.add(inst);
        }

        // Seek to the end of the instrument block
        inputStream.seek(instStartPos + breakOffset);

        // Read waveforms
        long waveformsLength = inputStream.readMotorolaDWord() & 0xFFFFFFFFL;
        int numWaveforms = (int) (waveformsLength / 256);
        byte[][] waveforms = new byte[numWaveforms][256];
        for (int i = 0; i < numWaveforms; i++) {
            inputStream.read(waveforms[i], 0, 256);
        }

        // Skip 64 bytes of unknown data
        inputStream.skip(64);

        // Read sample offsets
        int[] sampleOffsets = new int[8];
        for (int i = 0; i < 8; i++) {
            sampleOffsets[i] = (int) (inputStream.readMotorolaDWord() & 0xFFFFFFFFL);
        }

        long samplesStartPos = inputStream.getFilePointer();
        for (DM2Instrument inst : dm2Instruments) {
            if (inst.isSample) {
                inputStream.seek(samplesStartPos + sampleOffsets[inst.sampleNumber & 0x7]);
                inst.sampleData = new byte[inst.sampleLength];
                inputStream.read(inst.sampleData, 0, inst.sampleLength);
            }
        }

        // Convert patterns and song arrangement
        int numPositions = 0;
        for (int i = 0; i < 4; i++) {
            numPositions = Math.max(numPositions, trackLengths[i]);
        }

        // Pre scan the song for the wave table bake rates and the volume
        // limited instrument variants
        SongScan scan = scanSong(dm2Instruments.size(), numPositions, trackLengths, trackLoopPositions,
                blockNumbers, transposes, blocks, numBlocks);

        // Convert instruments and samples - the volume limit variants share
        // the sample of their base instrument
        setNInstruments(dm2Instruments.size() + scan.capVariants.size());
        setNSamples(dm2Instruments.size());
        InstrumentsContainer instrumentContainer = new InstrumentsContainer(this, getNInstruments(), getNSamples());
        setInstrumentContainer(instrumentContainer);
        for (int i = 0; i < dm2Instruments.size(); i++) {
            createSample(i, dm2Instruments.get(i), waveforms, scan.referencePeriods[i]);
            createInstrument(i, i, dm2Instruments.get(i), 63);
        }
        for (Map.Entry<Integer, Integer> entry : scan.capVariants.entrySet()) {
            int baseInstrument = entry.getKey() >> 8;
            createInstrument(entry.getValue(), baseInstrument, dm2Instruments.get(baseInstrument), entry.getKey() & 0xFF);
        }

        setNPattern(numPositions);
        setSongLength(numPositions);
        setSongRestart(0);
        allocArrangement(numPositions);
        for (int p = 0; p < numPositions; p++) {
            getArrangement()[p] = p;
        }

        PatternContainer patternContainer = new PatternContainer(this, numPositions, 16, 4);
        setPatternContainer(patternContainer);

        ChannelState[] states = new ChannelState[4];
        for (int c = 0; c < 4; c++) {
            states[c] = new ChannelState();
            states[c].arpeggio = arpeggioSequences[0];
        }
        int currentSpeed = (startSpeed & 0x0F) + 1; // to track the arpeggio tick positions

        for (int p = 0; p < numPositions; p++) {
            for (int r = 0; r < 16; r++) {
                for (int c = 0; c < 4; c++) {
                    int trackIdx = getTrackIndex(c, p, trackLengths, trackLoopPositions);
                    if (trackIdx == -1) {
                        continue;
                    }

                    int blockNum = blockNumbers[c][trackIdx] & 0xFF;
                    int transpose = transposes[c][trackIdx];
                    if (blockNum >= numBlocks) {
                        continue;
                    }

                    byte[] line = blocks[blockNum][r];
                    int note = line[0] & 0xFF;
                    int instrument = line[1] & 0xFF;
                    int effect = line[2] & 0xFF;
                    int effectArg = line[3] & 0xFF;

                    PatternElement pe = patternContainer.createPatternElement(p, r, c);
                    ChannelState state = states[c];

                    // immediate effects use the effect slot directly, the persistent
                    // ones (3/4/5/6/8) only update the channel state
                    int newEffect = -1;
                    int newOp = 0;
                    boolean volumeSet = false;
                    switch (effect) {
                        case 1: // SetSpeed - a DM2 row lasts speed+1 ticks
                            newEffect = 0x0F;
                            newOp = (effectArg & 0x0F) + 1;
                            currentSpeed = newOp;
                            break;
                        case 2: // SetFilter (E0x: 0 = on, 1 = off)
                            newEffect = 0x0E;
                            newOp = (effectArg != 0) ? 0x00 : 0x01;
                            break;
                        case 3: // SetBendRateUp: period decreases by arg per tick
                            state.pitchBend = -effectArg;
                            break;
                        case 4: // SetBendRateDown: period increases by arg per tick
                            state.pitchBend = effectArg;
                            break;
                        case 5: // SetPortamento (0 = off)
                            state.portamento = effectArg;
                            break;
                        case 6: // SetVolume: limits the envelope volume from now on
                            state.maxVolume = effectArg & 0x3F;
                            volumeSet = true;
                            break;
                        case 7: // SetGlobalVolume
                            newEffect = 0x10;
                            newOp = effectArg & 0x3F;
                            break;
                        case 8: // SetArp - the arpeggio position is not reset
                            state.arpeggio = arpeggioSequences[effectArg & 0x3F];
                            break;
                        default:
                            break;
                    }

                    boolean hasNote = note != 0;
                    if (hasNote) {
                        int noteIndex = note + transpose;
                        if (noteIndex < 1) {
                            noteIndex = 1;
                        }
                        if (noteIndex > ModConstants.noteValues.length) {
                            noteIndex = ModConstants.noteValues.length;
                        }
                        pe.setNoteIndex(noteIndex);
                        pe.setPeriod(ModConstants.noteValues[noteIndex - 1]);
                        state.arpeggioTick = 0;

                        // DM2 instrument numbers are 0-based, javamod's are 1-based;
                        // volume limited notes play the clipped envelope variant
                        if (instrument < dm2Instruments.size()) {
                            Integer variant = (state.maxVolume < 63)
                                    ? scan.capVariants.get((instrument << 8) | state.maxVolume) : null;
                            pe.setInstrument(((variant != null) ? variant : instrument) + 1);
                            state.instPitchBend = dm2Instruments.get(instrument).pitchBend;
                        }
                    } else if (volumeSet) {
                        // a volume limit change in the middle of a note can only be
                        // approximated by scaling with the channel volume
                        pe.setVolumeEffect(0x01);
                        pe.setVolumeEffectOp((state.maxVolume << 6) / 63);
                    }

                    if (newEffect < 0) {
                        // persistent states, in the play routine's priority: tone
                        // portamento disables the arpeggio, pitch bend runs on top
                        int bendRate = state.pitchBend - state.instPitchBend;
                        if (state.portamento != 0) {
                            newEffect = 0x03;
                            newOp = state.portamento;
                        } else if (bendRate < 0) {
                            newEffect = 0x01;
                            newOp = Math.min(-bendRate, 0xFF);
                        } else if (bendRate > 0) {
                            newEffect = 0x02;
                            newOp = Math.min(bendRate, 0xFF);
                        } else {
                            int arpOp = getRowArpeggioOp(state);
                            if (arpOp != 0) {
                                newEffect = 0x00;
                                newOp = arpOp;
                            }
                        }
                    }

                    if (newEffect > 0 || newOp != 0) {
                        pe.setEffect(newEffect);
                        pe.setEffectOp(newOp);
                    }
                }

                for (ChannelState state : states) {
                    state.arpeggioTick += currentSpeed;
                }
            }
        }
    }

    private static int getTrackIndex(int channel, int position, int[] trackLengths, int[] trackLoopPositions) {
        int len = trackLengths[channel];
        if (len == 0) {
            return -1;
        }
        if (position < len) {
            return position;
        }
        int loopPos = trackLoopPositions[channel];
        if (loopPos >= len) {
            loopPos = 0;
        }
        int loopLen = len - loopPos;
        if (loopLen <= 0) {
            return loopPos;
        }
        return loopPos + ((position - loopPos) % loopLen);
    }

    /**
     * The effective note offset sequence of a DM2 arpeggio: it advances one
     * step per tick and a -128 entry restarts it, so it repeats over the
     * steps before the first -128 (or all 16).
     */
    private static int[] computeArpeggioSequence(byte[] arpeggio) {
        int length = 16;
        for (int k = 1; k < 16; k++) {
            if (arpeggio[k] == -128) {
                length = k;
                break;
            }
        }
        int[] sequence = new int[length];
        for (int k = 0; k < length; k++) {
            sequence[k] = (arpeggio[k] == -128) ? 0 : arpeggio[k];
        }
        return sequence;
    }

    /**
     * Squeeze the arpeggio steps this row covers into the XM 0xy form: XM
     * plays base, base+x, base+y per tick, so take the two offsets following
     * the row's arpeggio position. Offsets outside 0..15 (drum sweeps) are
     * clamped into the nibble.
     */
    private static int getRowArpeggioOp(ChannelState state) {
        int[] sequence = state.arpeggio;
        if (sequence.length == 0) {
            return 0;
        }
        int tick = state.arpeggioTick;
        int x = Math.max(0, Math.min(sequence[(tick + 1) % sequence.length], 15));
        int y = Math.max(0, Math.min(sequence[(tick + 2) % sequence.length], 15));
        return (x << 4) | y;
    }

    /**
     * Follow the wave table run of a synth instrument (0xff jumps included)
     * and split it into the attack frames and the repeating steady state cycle.
     * A halted run (a jump onto another jump) repeats its last frame.
     */
    private static WaveRun parseWaveTable(byte[] table) {
        WaveRun run = new WaveRun();
        boolean[] visited = new boolean[48];
        int[] frameCountAtVisit = new int[48];
        List<Integer> frames = new ArrayList<>();
        int pos = 0;
        int cycleStart = -1;
        while (cycleStart < 0) {
            if (visited[pos]) {
                cycleStart = frameCountAtVisit[pos];
                break;
            }
            visited[pos] = true;
            frameCountAtVisit[pos] = frames.size();
            int data = table[pos] & 0xFF;
            if (data == 0xFF) {
                pos = (table[(pos + 1) % 48] & 0xFF) % 48;
                data = table[pos] & 0xFF;
                if (data == 0xFF) { // jump onto another jump: halted on the last frame
                    cycleStart = Math.max(frames.size() - 1, 0);
                    break;
                }
                if (visited[pos]) {
                    cycleStart = frameCountAtVisit[pos];
                    break;
                }
                visited[pos] = true;
                frameCountAtVisit[pos] = frames.size();
            }
            frames.add(data);
            pos = (pos + 1) % 48;
        }
        run.attack.addAll(frames.subList(0, cycleStart));
        run.cycle.addAll(frames.subList(cycleStart, frames.size()));
        return run;
    }

    /** what the pre scan of the song found out about the instruments' usage */
    private static class SongScan {
        /** median period each instrument is played at (the wave table bake rate) */
        int[] referencePeriods;
        /** each (instrument << 8 | volume limit) combination played, mapped to its variant instrument index */
        final Map<Integer, Integer> capVariants = new LinkedHashMap<>();
    }

    /**
     * Scan the whole song once in playback order for per instrument note
     * statistics and all (instrument, volume limit) combinations: DM2 clips
     * the envelope volume at the limit, which XM cannot, so limited notes get
     * variant instruments with a clipped envelope.
     */
    private static SongScan scanSong(int numInstruments, int numPositions, int[] trackLengths, int[] trackLoopPositions,
                                     byte[][] blockNumbers, byte[][] transposes, byte[][][] blocks, int numBlocks) {
        SongScan scan = new SongScan();
        List<List<Integer>> periods = new ArrayList<>();
        for (int i = 0; i < numInstruments; i++) {
            periods.add(new ArrayList<>());
        }
        for (int c = 0; c < 4; c++) {
            int cap = 63;
            for (int p = 0; p < numPositions; p++) {
                int trackIdx = getTrackIndex(c, p, trackLengths, trackLoopPositions);
                if (trackIdx == -1) {
                    continue;
                }
                int blockNum = blockNumbers[c][trackIdx] & 0xFF;
                if (blockNum >= numBlocks) {
                    continue;
                }
                for (int r = 0; r < 16; r++) {
                    byte[] line = blocks[blockNum][r];
                    int note = line[0] & 0xFF;
                    int instrument = line[1] & 0xFF;
                    if ((line[2] & 0xFF) == 6) {
                        cap = line[3] & 0x3F;
                    }
                    if (note == 0 || instrument >= numInstruments) {
                        continue;
                    }
                    int noteIndex = Math.max(1, Math.min(note + transposes[c][trackIdx], ModConstants.noteValues.length));
                    periods.get(instrument).add(ModConstants.noteValues[noteIndex - 1]);
                    if (cap < 63) {
                        scan.capVariants.computeIfAbsent((instrument << 8) | cap,
                                k -> numInstruments + scan.capVariants.size());
                    }
                }
            }
        }
        scan.referencePeriods = new int[numInstruments];
        for (int i = 0; i < numInstruments; i++) {
            List<Integer> list = periods.get(i);
            if (list.isEmpty()) {
                scan.referencePeriods[i] = 428;
            } else {
                list.sort(null);
                scan.referencePeriods[i] = list.get(list.size() / 2);
            }
        }
        return scan;
    }

    /**
     * Bake the wave table run of a synth instrument into sample data at the
     * playback rate of the given period: the attack frames become the sample
     * start, the steady state cycle a phase continuous loop. Frames of
     * waveform 0 play the noise generator.
     * The Paula position is kept across frame switches, so the wave phase runs
     * through, and the loop is sized to a multiple of the wave length to stay
     * seamless. Returns {@code {loopStart, loopLength}} in {@code buffer}.
     */
    private static byte[] bakeWaveRun(WaveRun run, DM2Instrument dm2Ins, byte[][] waveforms, int refPeriod, int[] loopOut) {
        int waveLen = Math.min(dm2Ins.sampleLength, 256);
        int stepTicks = (dm2Ins.sampleNumber & 0xFF) + 1; // ticks one frame lasts
        double bytesPerTick = 3546895.0 / (Math.max(refPeriod, 113) * 50.0);

        byte[] noise = generateNoise(NOISE_SAMPLE_LENGTH);
        boolean cycleIsAllNoise = true;
        for (int frame : run.cycle) {
            if (frame != 0 && frame < waveforms.length) {
                cycleIsAllNoise = false;
                break;
            }
        }

        // attack part, one frame per stepTicks ticks
        int attackTicks = run.attack.size() * stepTicks;
        int attackBytes = (int) Math.round(attackTicks * bytesPerTick);
        if (attackBytes > 49152) { // overly long runs: keep the steady part only
            attackTicks = attackBytes = 0;
        }

        int loopBytes;
        boolean singleFrameCycle = run.cycle.stream().distinct().count() <= 1;
        if (cycleIsAllNoise) {
            loopBytes = NOISE_SAMPLE_LENGTH;
        } else if (singleFrameCycle) {
            loopBytes = waveLen; // static frame: one seamless wave pass
        } else {
            int cycleTicks = run.cycle.size() * stepTicks;
            loopBytes = (int) Math.max(1, Math.round(cycleTicks * bytesPerTick / waveLen)) * waveLen;
            if (attackBytes + loopBytes > 131072) {
                loopBytes = waveLen; // too long to bake: fall back to the first frame
                singleFrameCycle = true;
            }
        }

        byte[] buffer = new byte[attackBytes + loopBytes];
        int pos = 0;      // Paula play position, runs through the wave mod waveLen
        int noisePos = 0;

        for (int tick = 0; tick < attackTicks; tick++) {
            int frame = run.attack.get(tick / stepTicks);
            int until = (int) Math.round((tick + 1) * bytesPerTick);
            for (; pos < until && pos < attackBytes; pos++) {
                buffer[pos] = sampleByte(frame, waveforms, noise, pos % waveLen, noisePos++);
            }
        }
        pos = attackBytes;

        if (cycleIsAllNoise) {
            System.arraycopy(noise, 0, buffer, attackBytes, loopBytes);
        } else if (singleFrameCycle) {
            int frame = run.cycle.isEmpty() ? 0 : run.cycle.get(0);
            for (int i = 0; i < loopBytes; i++, pos++) {
                buffer[pos] = sampleByte(frame, waveforms, noise, pos % waveLen, noisePos++);
            }
        } else {
            for (int tick = 0; pos < attackBytes + loopBytes; tick++) {
                int frame = run.cycle.get((tick / stepTicks) % run.cycle.size());
                int until = attackBytes + (int) Math.round((tick + 1) * bytesPerTick);
                for (; pos < until && pos < attackBytes + loopBytes; pos++) {
                    buffer[pos] = sampleByte(frame, waveforms, noise, pos % waveLen, noisePos++);
                }
            }
        }

        loopOut[0] = attackBytes;
        loopOut[1] = loopBytes;
        return buffer;
    }

    private static byte sampleByte(int frame, byte[][] waveforms, byte[] noise, int wavePos, int noisePos) {
        if (frame != 0 && frame < waveforms.length) {
            return waveforms[frame][wavePos];
        }
        return noise[noisePos % noise.length]; // waveform 0 (or missing): the noise generator
    }

    /** generate the noise the play routine renders into waveform 0 on every tick */
    private static byte[] generateNoise(int length) {
        byte[] noise = new byte[length];
        int value = 0;
        for (int i = 0; i < length; i += 4) {
            value = Integer.rotateLeft(value, 7);
            value += 0x6eca756d;
            value ^= 0x9e59a92b;
            noise[i] = (byte) value;
            noise[i + 1] = (byte) (value >> 8);
            noise[i + 2] = (byte) (value >> 16);
            noise[i + 3] = (byte) (value >> 24);
        }
        return noise;
    }

    private void createSample(int sampleIndex, DM2Instrument dm2Ins, byte[][] waveforms, int refPeriod) {
        Sample current = new Sample();
        current.name = "Instrument " + dm2Ins.number;

        byte[] srcData;
        int length, loopStart, loopLength;
        if (dm2Ins.isSample) {
            srcData = dm2Ins.sampleData;
            length = dm2Ins.sampleLength;
            loopStart = dm2Ins.repeatStart;
            loopLength = dm2Ins.repeatLength;
        } else if (dm2Ins.sampleLength > 0) {
            // synth: bake the wave table run into the sample
            int[] loop = new int[2];
            srcData = bakeWaveRun(parseWaveTable(dm2Ins.table), dm2Ins, waveforms, refPeriod, loop);
            length = srcData.length;
            loopStart = loop[0];
            loopLength = loop[1];
        } else {
            srcData = null;
            length = loopStart = loopLength = 0;
        }

        if (srcData == null || srcData.length == 0) {
            length = 0;
        } else if (length > srcData.length) {
            length = srcData.length;
        }

        current.byteLength = current.sampleLength = length;

        if (loopLength > 2 && length > 0) {
            if (loopStart < 0) {
                loopStart = 0;
            }
            int loopStop = loopStart + loopLength;
            if (loopStop > length) {
                loopStop = length;
            }
            if (loopStop - loopStart > 2) {
                current.loopStart = loopStart;
                current.loopStop = loopStop;
                current.loopLength = loopStop - loopStart;
                current.loopType = ModConstants.LOOP_ON;
            }
        }

        // vibrato table as XM auto vibrato: the steady stage (sustain 0xff)
        // is a triangle of amplitude speed*delay, one cycle every 2*delay ticks
        int stage = 0;
        int sweep = 0;
        while (stage < 4 && (dm2Ins.vibratoSustain[stage] & 0xFF) != 0xFF) {
            sweep += dm2Ins.vibratoSustain[stage] & 0xFF;
            stage++;
        }
        int vibSpeed = dm2Ins.vibratoSpeed[stage] & 0xFF;
        int vibDelay = dm2Ins.vibratoDelay[stage] & 0xFF;
        if (vibSpeed > 0 && vibDelay > 0) {
            current.vibratoType = 0; // sine
            current.vibratoSweep = Math.min(255, sweep);
            current.vibratoRate = Math.max(1, Math.min(255, 128 / vibDelay));
            current.vibratoDepth = Math.min(255, 2 * vibSpeed * vibDelay);
        }

        current.volume = 64;
        current.globalVolume = ModConstants.MAXSAMPLEVOLUME;
        current.fineTune = 0;
        current.baseFrequency = ModConstants.BASEFREQUENCY;
        current.transpose = 0;

        current.sustainLoopStart = 0;
        current.sustainLoopStop = 0;
        current.sustainLoopLength = 0;

        current.isStereo = false;
        current.setPanning = false;
        current.defaultPanning = 128;
        current.sampleType = ModConstants.SM_PCMS;

        if (length > 0) {
            current.allocSampleData();
            for (int s = 0; s < length; s++) {
                current.sampleL[s] = ModConstants.promoteSigned8BitToSigned32Bit(srcData[s]);
            }
            current.fixSampleLoops(getModType());
        }

        getInstrumentContainer().setSample(sampleIndex, current);
    }

    /**
     * @param cap volume limit (0..63) the envelope gets clipped at, 63 = none
     */
    private void createInstrument(int insIndex, int sampleIndexBase, DM2Instrument dm2Ins, int cap) {
        Instrument currentIns = new Instrument();
        currentIns.name = "Instrument " + dm2Ins.number + ((cap < 63) ? (" vol " + cap) : "");

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
            currentIns.sampleIndex[i] = sampleIndexBase + 1;
            currentIns.noteIndex[i] = i;
        }

        currentIns.volumeEnvelope = createVolumeEnvelope(dm2Ins, cap);
        currentIns.panningEnvelope = new Envelope(EnvelopeType.panning);
        currentIns.volumeFadeOut = 0;

        getInstrumentContainer().setInstrument(insIndex, currentIns);
    }

    /**
     * Build the XM volume envelope from the DM2 volume table: 5 stages, each
     * moving the volume (0..255) towards its level by its speed per tick and
     * then holding it for its sustain ticks. Speed 0 holds the reached volume
     * forever. The played volume is the table volume divided by four, clipped
     * at the channel's volume limit (the {@code cap}).
     */
    private Envelope createVolumeEnvelope(DM2Instrument dm2Ins, int cap) {
        Envelope envelope = new Envelope(EnvelopeType.volume);
        envelope.on = true;
        envelope.xm_style = true;
        envelope.sustain = false;
        envelope.loop = false;

        List<Integer> points = new ArrayList<>();
        List<Integer> values = new ArrayList<>();

        int tick = 0;
        int currentVol = 0;
        int capVol = (cap << 2) | 0x03; // the limit in raw 0..255 units

        points.add(0);
        values.add(0);

        for (int step = 0; step < 5 && tick < 32767; step++) {
            int target = dm2Ins.volumeLevel[step] & 0xFF;
            int speed = dm2Ins.volumeSpeed[step] & 0xFF;
            int sustain = dm2Ins.volumeSustain[step] & 0xFF;

            if (speed == 0) {
                break;
            }

            int move = Math.abs(target - currentVol);
            if (move > 0) {
                int ticks = (move + speed - 1) / speed;
                // the clipping at the limit kinks the ramp: add the crossing point
                int crossing = 0;
                if (currentVol < capVol && capVol < target) {
                    crossing = (capVol - currentVol + speed - 1) / speed;
                } else if (currentVol > capVol && capVol > target) {
                    crossing = (currentVol - capVol + speed - 1) / speed;
                }
                if (crossing > 0 && crossing < ticks) {
                    points.add(tick + crossing);
                    values.add(cap);
                }
                tick += ticks;
                currentVol = target;
            }

            points.add(tick);
            values.add(Math.min(currentVol >> 2, cap));

            if (sustain > 0) {
                tick += sustain;
                points.add(tick);
                values.add(Math.min(currentVol >> 2, cap));
            }
        }

        envelope.positions = new int[points.size()];
        envelope.value = new int[points.size()];
        for (int i = 0; i < points.size(); i++) {
            envelope.positions[i] = points.get(i);
            envelope.value[i] = values.get(i);
        }
        envelope.setNumberOfPoints(points.size());
        envelope.sanitize(64);

        return envelope;
    }
}
