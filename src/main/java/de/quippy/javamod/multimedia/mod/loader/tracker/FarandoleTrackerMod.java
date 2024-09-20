/*
 * @(#) FarandoleTrackerMod.java
 *
 * Created on 13.08.2022 by Daniel Becker
 *
 *-----------------------------------------------------------------------
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *----------------------------------------------------------------------
 */

package de.quippy.javamod.multimedia.mod.loader.tracker;

import java.io.IOException;

import de.quippy.javamod.io.ModfileInputStream;
import de.quippy.javamod.multimedia.mod.ModConstants;
import de.quippy.javamod.multimedia.mod.loader.instrument.InstrumentsContainer;
import de.quippy.javamod.multimedia.mod.loader.instrument.Sample;
import de.quippy.javamod.multimedia.mod.loader.pattern.PatternContainer;
import de.quippy.javamod.multimedia.mod.loader.pattern.PatternElement;
import de.quippy.javamod.multimedia.mod.midi.MidiMacros;
import de.quippy.javamod.multimedia.mod.mixer.BasicModMixer;
import de.quippy.javamod.multimedia.mod.mixer.ScreamTrackerMixer;


/**
 * @author Daniel Becker
 * @since 13.08.2022
 */
public class FarandoleTrackerMod extends ScreamTrackerMod {

    private static final String[] MODFILEEXTENSION = {
            "far"
    };

    private static final int FARFILEMAGIC = 0xFE524146;
    private static final int BREAK_ROW_INVALID = -1;

    private String songMessage;

    /**
     * @return
     * @see de.quippy.javamod.multimedia.mod.loader.Module#getFileExtensionList()
     */
    @Override
    public String[] getFileExtensionList() {
        return MODFILEEXTENSION;
    }

    /**
     * We load Farandole Mods as S3M
     */
    @Override
    public BasicModMixer getModMixer(int sampleRate, int doISP, int doNoLoops, int maxNNAChannels) {
        return new ScreamTrackerMixer(this, sampleRate, doISP, doNoLoops, maxNNAChannels);
    }

    @Override
    public int getPanningSeparation() {
        return 128;
    }

    @Override
    public int getPanningValue(int channel) {
        return panningValue[channel];
    }

    @Override
    public int getChannelVolume(int channel) {
        return 64;
    }

    @Override
    public int getFrequencyTable() {
        return ModConstants.STM_S3M_TABLE;
    }

    @Override
    public String getSongMessage() {
        return songMessage;
    }

    @Override
    public MidiMacros getMidiConfig() {
        return null;
    }

    @Override
    public boolean checkLoadingPossible(ModfileInputStream inputStream) throws IOException {
        int id = inputStream.readIntelDWord();
        inputStream.seek(0);
        return id == FARFILEMAGIC;
    }

    /**
     * Internal Routine for reading and converting a pattern entry
     *
     * @since 13.08.2022
     */
    private void setPattern(int pattNum, int patternSize, PatternContainer patternContainer, ModfileInputStream inputStream) throws IOException {
        //final int rows = (patternSizes[pattNum] - 2) / (16 * 4); // documentation: 16 Channels, 4 bytes each
        int rows = (patternSize - 2) >> 6;

        // read length in rows - is interpreted as pattern row break:
        int breakRow = inputStream.read();
        inputStream.skip(1); // Tempo for pattern, - Unsupported, use not recommended
        if (breakRow > 0 && breakRow < (rows - 2))
            breakRow++;
        else
            breakRow = BREAK_ROW_INVALID;
        patternContainer.createPattern(pattNum, rows);
        for (int row = 0; row < rows; row++) {
            // create the PatternRow
            patternContainer.createPatternRow(pattNum, row, getNChannels());
            // now read the data and set the pattern row:
            for (int channel = 0; channel < getNChannels(); channel++) {
                PatternElement currentElement = patternContainer.createPatternElement(pattNum, row, channel);

                // now read in:
                int note = inputStream.read(); // 0 - 72
                int inst = inputStream.read();
                int vol = inputStream.read(); // 0 - 16
                int eff = inputStream.read();

                if (note > 0 && note < 72) {
                    int noteIndex = note + 48;
                    if (noteIndex < ModConstants.noteValues.length) {
                        currentElement.setNoteIndex(noteIndex);
                        currentElement.setPeriod(ModConstants.noteValues[noteIndex]);
                    }
                }

                currentElement.setInstrument(inst + 1);

                if (note > 0 || vol > 0) {
                    currentElement.setVolumeEffect(0x01); // Default setVolume effect
                    currentElement.setVolumeEffectOp((vol > 16) ? 64 : (vol - 1) << 2); // max 64 instead 16
                }

                int effect = eff >> 4;
                int effectOp = eff & 0x0F;

                if (effect == 0x09) { // special treatment!
                    currentElement.setVolumeEffect(0x01); // Default setVolume effect
                    currentElement.setVolumeEffectOp((effectOp + 1) << 2); // max 64 instead 15
                    effect = effectOp = 0;
                }

                // Translation:
                switch (effect) {
                    case 0x01: // Porta Up
                        effect = 0x06;
                        effectOp |= 0xF0; // fine porta
                        break;
                    case 0x02: // Porta Down
                        effect = 0x05;
                        effectOp |= 0xF0;
                        break;
                    case 0x03: // Porta To Note
                        effect = 0x07;
                        effectOp <<= 2;
                        break;
                    case 0x04: // Retrig
                        effect = 0x11;
                        effectOp = (6 / (1 + effectOp)) + 1; // ugh?
                        break;
                    case 0x05: // set Vibrato Depth
                        effect = 0x08;
                        break;
                    case 0x06: // Vibrato Speed
                        effect = 0x08;
                        effectOp <<= 4;
                        break;
                    case 0x07: // Volume Slide Up
                        effect = 0x04;
                        effectOp <<= 4;
                        break;
                    case 0x08: // Volume Slide Down
                        effect = 0x04;
                        break;
//                    case 0x0A: // Port to Volume
//                        break;
                    case 0x0B:// set Balance
                        effect = 0x13;
                        effectOp |= 0x80; // set Fine Panning
                        break;
                    case 0x0D: // Fine Tempo Down
                        break;
                    case 0x0E: // Fine Tempo Up
                        break;
                    case 0x0F:
                        effect = 0x01;
                        break;
                }
                currentElement.setEffect(effect);
                currentElement.setEffectOp(effectOp);
            }
        }
    }

    private void readSampleData(int sampleIndex, ModfileInputStream inputStream) throws IOException {
        Sample current = new Sample();
        current.setName(inputStream.readString(32));

        // Length
        int length = inputStream.readIntelDWord();

        // finetune Value>7 means negative 8..15= -8..-1
        /* final int fine = */ inputStream.read(); // shall we use this?!
        current.setFineTune(0);
        current.setBaseFrequency(ModConstants.BASEFREQUENCY);

        // volume
        int vol = inputStream.read();
        current.setVolume((vol > 64) ? 64 : vol);

        // Repeat start and stop
        int repeatStart = inputStream.readIntelDWord();
        int repeatStop = inputStream.readIntelDWord();

        // Flags
        int sampleType = inputStream.read();
        int loopType = inputStream.read();

        if (current.length > 0) {
            if (repeatStart > current.length) repeatStart = current.length - 1;
            if (repeatStop > current.length) repeatStop = current.length;
            if (repeatStop <= repeatStart) repeatStart = repeatStop = 0;
        }

        if ((sampleType & 0x01) != 0) { // 16Bit:
            length >>= 1;
            repeatStart >>= 1;
            repeatStop >>= 1;
        }

        current.setLength(length);
        current.setLoopStart(repeatStart);
        current.setLoopStop(repeatStop);
        current.setLoopLength(repeatStop - repeatStart);
        if ((loopType & 8) != 0 && repeatStop > repeatStart)
            current.setLoopType(ModConstants.LOOP_ON);

        // Defaults for non-existent SustainLoop
        current.setSustainLoopStart(0);
        current.setSustainLoopStop(0);
        current.setSustainLoopLength(0);

        // Defaults!
        current.setStereo(false);
        current.setGlobalVolume(ModConstants.MAXSAMPLEVOLUME);
        current.setTranspose(0);
        current.setPanning(false);
        current.setDefaultPanning(128);

        // SampleData
        int flags = ModConstants.SM_PCMS;
        if ((sampleType & 0x01) != 0) flags |= ModConstants.SM_16BIT;
        current.setSampleType(flags);
        readSampleData(current, inputStream);

        getInstrumentContainer().setSample(sampleIndex, current);
    }

    @Override
    protected void loadModFileInternal(ModfileInputStream inputStream) throws IOException {
        setModType(ModConstants.MODTYPE_S3M); // Farandole is converted internally to s3m
        setNChannels(16);
        setBaseVolume(ModConstants.MAXGLOBALVOLUME);
        int preAmp = ModConstants.MAX_MIXING_PREAMP / getNChannels();
        setMixingPreAmp((preAmp < ModConstants.MIN_MIXING_PREAMP) ? ModConstants.MIN_MIXING_PREAMP : (preAmp > 0x80) ? 0x80 : preAmp);
        songFlags = ModConstants.SONG_ISSTEREO;

        // ModID
        int modID = inputStream.readIntelDWord();

        // Songname
        setSongName(inputStream.readString(40));

        // EOF from header should be 0x0D0A1A --> so old DOS Type command would stop here...
        int eof = (inputStream.readByte() << 16) | (inputStream.readByte() << 8) | inputStream.readByte();

        // Header Length
        int headerLength = inputStream.readIntelUnsignedWord();

        // check if header is valid...
        if (modID != FARFILEMAGIC || eof != 0x000D0A1A || headerLength < 98)
            throw new IOException("Unsupported Farandole MOD");

        // Composer Version
        version = inputStream.read();

        // onOff
        byte[] onOff = new byte[16];
        inputStream.read(onOff, 0, 16);

        // skip Editing State of Composer:
        inputStream.skip(9);

        // Tempo & BPM
        setTempo(inputStream.read());
        setBPMSpeed(80); // default BPM

        // Panning values:
        usePanningValues = true;
        panningValue = new int[16];
        for (int ch = 0; ch < 16; ch++) {
            int readByte = inputStream.read();
            panningValue[ch] = ((readByte & 0x0F) << 4) + 8;
        }

        // skip Pattern state
        inputStream.skip(4);

        // Message Length
        int messageLength = inputStream.readIntelUnsignedWord();
        if (messageLength > 0) {
            String message = inputStream.readString(messageLength);
            int start = 0;
            int rest = 132;
            int rows = message.length() / rest;
            StringBuilder b = new StringBuilder(messageLength + rows); // length plus "\n"
            for (int i = 0; i < rows; i++) {
                b.append(message, start, start + rest);
                b.append('\n');
                start += rest;
                if ((start + rest) > message.length()) rest = message.length() - start;
            }
            songMessage = b.toString();
        }

        setModID("FAR");
        setTrackerName("Farandole Composer V" + ((version >> 4) & 0x0F) + '.' + (version & 0x0F));

        // now for the pattern order
        allocArrangement(256);
        for (int i = 0; i < 256; i++) getArrangement()[i] = inputStream.read();
        int numPatterns = inputStream.read(); // obviously this is a lie - so we allocate always all 256 and skip loading of unused
        numPatterns = 256;
        setNPattern(numPatterns);
        int numOrders = inputStream.read();
        int restartPos = inputStream.read();
        int[] patternSizes = new int[256];
        for (int i = 0; i < patternSizes.length; i++) patternSizes[i] = inputStream.readIntelUnsignedWord();
        setSongRestart(restartPos);
        setSongLength(numOrders);

        // now skip to patterns
        inputStream.seek(headerLength);

        PatternContainer patternContainer = new PatternContainer(this, getNPattern());
        setPatternContainer(patternContainer);
        for (int pattNum = 0; pattNum < getNPattern(); pattNum++) {
            if (patternSizes[pattNum] == 0) {
                // We need an empty pattern - player is not supporting "NULL" patterns...
                patternContainer.createPattern(pattNum, 0);
                continue; // Empty pattern, nothing to do
            }
            // Create the Pattern
            setPattern(pattNum, patternSizes[pattNum], patternContainer, inputStream);
        }

        byte[] sampleMap = new byte[8];
        inputStream.read(sampleMap, 0, 8);
        // 64 Instruments max (8 bytes) if a bit is set, the instrument is stored!
        setNSamples(64);
        InstrumentsContainer instrumentContainer = new InstrumentsContainer(this, 0, getNSamples());
        this.setInstrumentContainer(instrumentContainer);
        for (int instIndex = 0; instIndex < 64; instIndex++) {
            if ((sampleMap[instIndex >> 3] & (1 << (instIndex & 0x07))) != 0) {
                readSampleData(instIndex, inputStream);
            }
        }
    }
}
