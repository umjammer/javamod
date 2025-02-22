/*
 * @(#) XMMod.java
 *
 * Created on 26.05.2006 by Daniel Becker
 *
 *-----------------------------------------------------------------------
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 3 of the License, or
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
import de.quippy.javamod.multimedia.mod.midi.MidiMacros;


/**
 * @author Daniel Becker
 * @since 26.05.2006
 */
public class XMMod extends ProTrackerMod {

    private static final String[] MODFILEEXTENSION = {
            "xm"
    };

    private int version;
    private int headerSize;
    private int flag;
    private String songMessage;
    private MidiMacros midiMacros;

    @Override
    public String[] getFileExtensionList() {
        return MODFILEEXTENSION;
    }

    @Override
    public int getFrequencyTable() {
        return ((songFlags & ModConstants.SONG_LINEARSLIDES) != 0) ? ModConstants.XM_LINEAR_TABLE : ModConstants.XM_AMIGA_TABLE;
    }

    @Override
    public MidiMacros getMidiConfig() {
        return midiMacros;
    }

    @Override
    public String getSongMessage() {
        return songMessage;
    }

    /**
     * @return always true for XMs
     */
    @Override
    public boolean getFT2Tremolo() {
        return true;
    }

    @Override
    public boolean getModSpeedIsTicks() {
        return false;
    }

    /**
     * @return true, if this is a FastTracker mod, false if this is not clear
     */
    @Override
    public boolean checkLoadingPossible(ModfileInputStream inputStream) throws IOException {
        String xmID = inputStream.readString(17);
        inputStream.seek(0);
        return isXMMod(xmID);
    }

    /**
     * 17 bytes
     * @since 3.9.6
     */
    @Override
    public boolean checkLoadingPossible(InputStream inputStream) throws IOException {
        DataInput di = new DataInputStream(inputStream);
        byte[] xmID = new byte[17];
        di.readFully(xmID);
        return isXMMod(new String(xmID));
    }

    /**
     * @param currentElement
     * @param inputStream
     * @throws IOException
     * @since 26.05.2006
     */
    private static void setIntoPatternElement(RandomAccessInputStream inputStream, PatternElement currentElement) throws IOException {
        int flags = inputStream.read();
        if ((flags & 0x80) == 0) { // is not packed
            flags = 0xff; // read all
            inputStream.skipBack(1); // and push back the note
        }
        int noteIndex = ((flags & 0x01) != 0) ? inputStream.read() : 0;
        int instrument = ((flags & 0x02) != 0) ? inputStream.read() : 0;
        int volume = ((flags & 0x04) != 0) ? inputStream.read() : 0;
        int effect = ((flags & 0x08) != 0) ? inputStream.read() : 0;
        int effectOp = ((flags & 0x10) != 0) ? inputStream.read() : 0;

        // sanitize all
        if (noteIndex == 97) // Key Off!
            noteIndex = ModConstants.KEY_OFF;
        else if (noteIndex < 0 || noteIndex > 97)
            noteIndex = ModConstants.NO_NOTE;
        currentElement.setNoteIndex(noteIndex);
        currentElement.setPeriod((noteIndex == ModConstants.NO_NOTE) ? 0 :
                (noteIndex == ModConstants.KEY_OFF) ? ModConstants.KEY_OFF :
                        ModConstants.noteValues[noteIndex - 1]);

        if (instrument == 0xff) instrument = 0;
        currentElement.setInstrument(instrument);

        if (volume != 0) {
            if (volume >= 0x10 && volume <= 0x50) {
                currentElement.setVolumeEffect(1);
                currentElement.setVolumeEffectOp(volume - 0x10);
            } else {
                currentElement.setVolumeEffect((volume >> 4) - 0x4);
                currentElement.setVolumeEffectOp(volume & 0x0F);
            }
        }

        currentElement.setEffect(effect);
        currentElement.setEffectOp(effectOp);
    }

    /**
     * To support Versions below 0104 we need a separate method to load at a
     * different place.
     *
     * @param inputStream
     * @throws IOException
     * @since 23.01.2024
     */
    private void readXMPattern(RandomAccessInputStream inputStream) throws IOException {
        PatternContainer patternContainer = new PatternContainer(this, getNPattern());
        for (int pattNum = 0; pattNum < getNPattern(); pattNum++) {
            long LSEEK = inputStream.getFilePointer();
            int patternHeaderSize = inputStream.readIntelDWord();

//			We ignore the packing type - as everybody does...
            inputStream.skip(1);

            int rows = (version == 0x0102) ? inputStream.read() + 1 : inputStream.readIntelUnsignedWord();
            if (rows == 0)
                rows = 64;
            else {
                int MaxPatternSize = ((getModType() & (ModConstants.MODTYPE_MPT | ModConstants.MODTYPE_OMPT)) != 0) ? 1024 : 256;
                if (rows > MaxPatternSize) rows = MaxPatternSize;
            }

            int packedPatternDataSize = inputStream.readIntelUnsignedWord();
            if (packedPatternDataSize == 0) {
                patternContainer.createPattern(pattNum, rows, getNChannels());
                for (int row = 0; row < rows; row++) {
                    for (int channel = 0; channel < getNChannels(); channel++) {
                        patternContainer.createPatternElement(pattNum, row, channel);
                    }
                }
                continue;
            }

            inputStream.seek(LSEEK + patternHeaderSize);

            // Stop reading, if either end of file or packed pattern size is reached
            long endPos = inputStream.getFilePointer() + packedPatternDataSize;
            if (endPos > inputStream.length()) endPos = inputStream.length();

            patternContainer.createPattern(pattNum, rows);
            for (int row = 0; row < rows; row++) {
                patternContainer.createPatternRow(pattNum, row, getNChannels());
                for (int channel = 0; channel < getNChannels(); channel++) {
                    PatternElement currentElement = patternContainer.createPatternElement(pattNum, row, channel);
                    if (inputStream.getFilePointer() < endPos) setIntoPatternElement(inputStream, currentElement);
                }
            }
            // With some corrupted XMs with flipped bits we will not load all pattern data.
            // Most XM loaders load the compressed pattern data into a separate buffer, we don't
            // so we need to seek...
            if (inputStream.getFilePointer() != endPos) {
//                final long dif = endPos - inputStream.getFilePointer();
//                logger.log(Level.INFO, "Read not enough bytes (" + dif + ") in pattern " + pattNum);
                setTrackerName(getTrackerName() + " (corrupt!)");
                inputStream.seek(endPos);
            }
        }
        setPatternContainer(patternContainer);
    }

    /**
     * @param inputStream
     * @param instrumentContainer
     * @param anzSamples
     * @param sampleOffsetIndex
     * @throws IOException
     * @since 23.01.2024
     */
    private void readXMSampleData(RandomAccessInputStream inputStream, InstrumentsContainer instrumentContainer, int anzSamples, int sampleOffsetIndex) throws IOException {
        for (int samIndex = 0; samIndex < anzSamples; samIndex++) {
            Sample current = instrumentContainer.getSample(samIndex + sampleOffsetIndex);
            readSampleData(current, inputStream);
        }
    }

    /**
     * Get the ModType
     *
     * @param kennung
     * @return
     */
    private static boolean isXMMod(String kennung) {
        if (kennung.equalsIgnoreCase("Extended Module: ")) return true;
        return false;
    }

    /**
     * This is only for some fun - to be honest. The C4-Period is never used, just displayed.
     *
     * @param sample
     * @param useTable
     * @return
     * @since 26.07.2024
     */
    private static int getPeriod2Hz(Sample sample, int useTable) {
        if (sample == null) return -1;

        int note = (4 * 12) + sample.transpose;
        if (note < 0) return -1;
        if (note >= (10 * 12) - 1) return -1;
        int C4Period = ((note << 4) + ((sample.fineTune >> 3) + 16));

        switch (useTable) {
            case ModConstants.XM_AMIGA_TABLE:
                return (ModConstants.BASEFREQUENCY * 1712) / (ModConstants.FT2_amigaPeriods[C4Period] & 0xffFF);
            case ModConstants.XM_LINEAR_TABLE:
                int period = ModConstants.FT2_linearPeriods[C4Period] & 0xffFF;
                // Original FT2 method with doubles - is a bit more precise in rounding
//                int invPeriodDouble = ((12 * 192 * 4) - C4Period) & 0xffFF; // 12 octaves * (12 * 16 * 4) LUT entries = 9216, add 767 for rounding
//                int quotientDouble = invPeriodDouble / (12 * 16 * 4);
//                int remainderDouble = invPeriodDouble % (12 * 16 * 4);
//                double logValue = (ModConstants.BASEFREQUENCY * 256d) * Math.pow(2d, (double) remainderDouble / (4d * 12d * 16d));
//                double frequencyDouble = logValue * (1d / Math.pow(2d, (double) ((14 - quotientDouble) & 0x1F)));

                int invPeriod = ((12 * 192 * 4) + 767 - period) & 0xffFF; // 12 octaves * (12 * 16 * 4) LUT entries = 9216, add 767 for rounding
                int quotient = invPeriod / (12 * 16 * 4);
                int remainder = period % (12 * 16 * 4);
                return ModConstants.lintab[remainder] >> (((14 - quotient) & 0x1F) - 2); // values are 4 times bigger in FT2
        }
        return -1;
    }

    @Override
    protected void loadModFileInternal(RandomAccessInputStream inputStream) throws IOException {
        setBaseVolume(ModConstants.MAXGLOBALVOLUME);
        setMixingPreAmp(ModConstants.MIN_MIXING_PREAMP);

        // XM-ID:
        setModID(inputStream.readString(17));
        if (!isXMMod(getModID())) throw new IOException("Unsupported XM Module!");

        // Songname
        setSongName(inputStream.readString(20));
        // 0x1A:
        inputStream.skip(1);

        // Trackername
        String trackerName = inputStream.readString(20);

        // Version
        version = inputStream.readIntelUnsignedWord();

        long LSEEK = inputStream.getFilePointer();
        // Header Size
        headerSize = inputStream.readIntelDWord();

        // lets start with some version / tracker guessing
        setModType(ModConstants.MODTYPE_XM);
        setTrackerName(trackerName.trim());
        if (trackerName.startsWith("FastTracker v2.00") && headerSize == 276) {
            int highVersion = (version >> 8) & 0xff;
            setTrackerName("FastTracker II V" + ModConstants.getAsHex(highVersion, (highVersion > 0x0f) ? 2 : 1) + "." + ModConstants.getAsHex(version & 0xff, 2));
            if (!trackerName.endsWith("   "))
                setTrackerName(getTrackerName() + " (generic)");
        } else if (trackerName.equals("FastTracker v 2.00  ")) {
            setTrackerName("ModPlug Tracker V1.0");
            setModType(getModType() | ModConstants.MODTYPE_MPT);
        } else if (trackerName.startsWith("OpenMPT")) {
            setModType(getModType() | ModConstants.MODTYPE_OMPT);
        } else if (trackerName.startsWith("*Converted ")) {
            setTrackerName("DigiTracker");
        }

        // OrderNum:
        setSongLength(inputStream.readIntelUnsignedWord());

        //SongRestart
        int songRestart = inputStream.readIntelUnsignedWord();
        setSongRestart((songRestart > getSongLength()) ? 0 : songRestart);

        // NChannels
        setNChannels(inputStream.readIntelUnsignedWord());

        // NPattern
        setNPattern(inputStream.readIntelUnsignedWord());

        // Instruments
        setNInstruments(inputStream.readIntelUnsignedWord());

        // a Flag
        flag = inputStream.readIntelUnsignedWord();
        if ((flag & 0x0001) != 0) songFlags |= ModConstants.SONG_LINEARSLIDES;
        if ((flag & 0x1000) != 0) songFlags |= ModConstants.SONG_EXFILTERRANGE;
        songFlags |= ModConstants.SONG_ISSTEREO;

        // Tempo
        setTempo(inputStream.readIntelUnsignedWord());

        // BPMSpeed
        setBPMSpeed(inputStream.readIntelUnsignedWord());

        // always space for 256 pattern...
        // ModPlug used to allow marker pattern like in IT
        //  255 = "---", End of song marker
        //  254 = "+++", Skip to next order
        allocArrangement(256);
        int[] arrangement = getArrangement();
        for (int i = 0; i < 256; i++) arrangement[i] = inputStream.read();

        inputStream.seek(LSEEK + headerSize);

        // Read the patternData
        if (version >= 0x0104) readXMPattern(inputStream);

        InstrumentsContainer instrumentContainer = new InstrumentsContainer(this, getNInstruments(), 0);
        setInstrumentContainer(instrumentContainer);

        int sampleOffsetIndex = 0;
        // Read the instrument data
        for (int ins = 0; ins < getNInstruments(); ins++) {
            int vibratoType = 0;
            int vibratoSweep = 0;
            int vibratoDepth = 0;
            int vibratoRate = 0;

            LSEEK = inputStream.getFilePointer();

            Instrument currentIns = new Instrument();

            // Default for values from IT
            currentIns.setGlobalVolume(128);
            currentIns.setPanning(false);
            currentIns.setDefaultPan(128);
            currentIns.setPitchPanSeparation(-1);
            currentIns.setNNA(-1);
            currentIns.setInitialFilterCutoff(-1);
            currentIns.setInitialFilterResonance(-1);
            currentIns.setRandomPanningVariation(-1);

            int instrumentHeaderSize = inputStream.readIntelDWord();
            currentIns.setName(inputStream.readString(22));
            /*final int insType = */
            inputStream.read();
            int anzSamples = inputStream.readIntelWord();

            int[] sampleIndex = new int[96];
            int[] noteIndex = new int[96];
            currentIns.setIndexArray(sampleIndex);
            currentIns.setNoteArray(noteIndex);
            if (anzSamples <= 0) { // if no samples, at least set to defaults
                for (int i = 0; i < 96; i++) {
                    sampleIndex[i] = 0;
                    noteIndex[i] = 0x80 | i;
                }
            } else {
                setNSamples(getNSamples() + anzSamples);
                /*final int sampleHeaderSize = */
                inputStream.readIntelDWord();

                for (int i = 0; i < 96; i++) {
                    sampleIndex[i] = inputStream.read() + sampleOffsetIndex + 1;
                    noteIndex[i] = i;
                }

                int[] volumeEnvelopePosition = new int[12];
                int[] volumeEnvelopeValue = new int[12];
                for (int i = 0; i < 12; i++) {
                    volumeEnvelopePosition[i] = inputStream.readIntelUnsignedWord();
                    volumeEnvelopeValue[i] = inputStream.readIntelUnsignedWord();
                }
                Envelope volumeEnvelope = new Envelope(EnvelopeType.volume);
                volumeEnvelope.setPositions(volumeEnvelopePosition);
                volumeEnvelope.setValue(volumeEnvelopeValue);
                currentIns.setVolumeEnvelope(volumeEnvelope);

                int[] panningEnvelopePosition = new int[12];
                int[] panningEnvelopeValue = new int[12];
                for (int i = 0; i < 12; i++) {
                    panningEnvelopePosition[i] = inputStream.readIntelUnsignedWord();
                    panningEnvelopeValue[i] = inputStream.readIntelUnsignedWord();
                }
                Envelope panningEnvelope = new Envelope(EnvelopeType.panning);
                panningEnvelope.setPositions(panningEnvelopePosition);
                panningEnvelope.setValue(panningEnvelopeValue);
                currentIns.setPanningEnvelope(panningEnvelope);

                volumeEnvelope.setNPoints(inputStream.read());
                panningEnvelope.setNPoints(inputStream.read());

                volumeEnvelope.setSustainPoint(inputStream.read());
                volumeEnvelope.setLoopStartPoint(inputStream.read());
                volumeEnvelope.setLoopEndPoint(inputStream.read());

                panningEnvelope.setSustainPoint(inputStream.read());
                panningEnvelope.setLoopStartPoint(inputStream.read());
                panningEnvelope.setLoopEndPoint(inputStream.read());

                volumeEnvelope.setXMType(inputStream.read());
                panningEnvelope.setXMType(inputStream.read());

                volumeEnvelope.sanitize(64);
                panningEnvelope.sanitize(64);

                vibratoType = inputStream.read();
                vibratoSweep = inputStream.read();
                vibratoDepth = inputStream.read();
                vibratoRate = inputStream.read();

                currentIns.setVolumeFadeOut(inputStream.readIntelUnsignedWord());

                // Reserved TODO: read Midi Data instead
                inputStream.skip(2);
            }
            inputStream.seek(LSEEK + instrumentHeaderSize);

            instrumentContainer.reallocSampleSpace(getNSamples());
            for (int samIndex = 0; samIndex < anzSamples; samIndex++) {
                Sample current = new Sample();

                current.setVibratoType(vibratoType);
                current.setVibratoSweep(vibratoSweep);
                current.setVibratoDepth(vibratoDepth);
                current.setVibratoRate(vibratoRate);

                // Length
                current.setLength(inputStream.readIntelDWord());

                // Repeat start and stop
                int repeatStart = inputStream.readIntelDWord();
                int repeatLength = inputStream.readIntelDWord();
                int repeatStop = repeatStart + repeatLength;

                // volume 64 is maximum
                int vol = inputStream.read() & 0x7F;
                current.setVolume((vol > 64) ? 64 : vol);
                current.setGlobalVolume(ModConstants.MAXSAMPLEVOLUME);

                // finetune Value>0x7F means negative
                int fine = inputStream.read();
                fine = (fine > 0x7F) ? fine - 0x100 : fine;
                current.setFineTune(fine);

                current.setFlags(inputStream.read());
                int loopType = 0;
                if ((current.flags & 0x03) != 0) loopType |= ModConstants.LOOP_ON;
                if ((current.flags & 0x02) != 0) loopType |= ModConstants.LOOP_IS_PINGPONG;
                current.setLoopType(loopType);

                int sampleLoadingFlags = 0;
                if ((current.flags & 0x10) != 0) {
                    sampleLoadingFlags |= ModConstants.SM_16BIT;
                    current.length >>= 1;
                    repeatStart >>= 1;
                    repeatStop >>= 1;
                }
                if ((current.flags & 0x20) != 0) {
                    sampleLoadingFlags |= ModConstants.SM_STEREO; // this is new, not standard. Support is easy, so why not!
                    current.length >>= 1;
                    repeatStart >>= 1;
                    repeatStop >>= 1;
                }
                current.setStereo((sampleLoadingFlags & ModConstants.SM_STEREO) != 0);

                current.setLoopStart(repeatStart);
                current.setLoopStop(repeatStop);
                current.setLoopLength(repeatStop - repeatStart);

                // Defaults for non-existent SustainLoop
                current.setSustainLoopStart(0);
                current.setSustainLoopStop(0);
                current.setSustainLoopLength(0);

                // Panning 0..255
                current.setPanning(true);
                current.setDefaultPanning(inputStream.read());

                // Transpose -128..127
                int transpose = inputStream.read();
                current.setTranspose((transpose > 0x7F) ? transpose - 0x100 : transpose);

                current.setBaseFrequency(getPeriod2Hz(current, getFrequencyTable()));

                // Reserved
                current.XM_reserved = inputStream.read();

                // Interpreting the loaded flags
                if (current.XM_reserved == 0xAD && (current.flags & (0x10 | 0x20)) == 0) { // ModPlug ADPCM compression
                    sampleLoadingFlags |= ModConstants.SM_ADPCM;
                    setTrackerName(getTrackerName() + " (ADPCM packed)");
                } else
                    sampleLoadingFlags |= ModConstants.SM_PCMD; // XM save in deltas

                current.setSampleType(sampleLoadingFlags);

                // Samplename
                current.setName(inputStream.readString(22));

                instrumentContainer.setSample(samIndex + sampleOffsetIndex, current);
            }

            if (version >= 0x0104) readXMSampleData(inputStream, instrumentContainer, anzSamples, sampleOffsetIndex);
            instrumentContainer.setInstrument(ins, currentIns);

            sampleOffsetIndex += anzSamples;
        }

        if (version < 0x0104) {
            readXMPattern(inputStream);
            readXMSampleData(inputStream, instrumentContainer, sampleOffsetIndex, 0);
        }

        // Remove marker pattern (supported with OpenModPlug in some versions)
        cleanUpArrangement();

        midiMacros = new MidiMacros();
        boolean hasMidiConfig = false;
        boolean hasExtraInstrumentInfos = false;
        boolean hasExtraSongProperties = false;
        while (inputStream.getFilePointer() + 8 < inputStream.length()) {
            int marker = inputStream.readIntelDWord();
            if (marker == 0x4D505458) { // MPTX - ModPlugExtraInstrumentInfo
                inputStream.skipBack(4);
                hasExtraInstrumentInfos = loadExtendedInstrumentProperties(inputStream);
            } else if (marker == 0x4D505453) { // MPTS - ModPlugExtraSongInfo
                inputStream.skipBack(4);
                hasExtraSongProperties = loadExtendedSongProperties(inputStream, true);
            } else {
                int len = inputStream.readIntelDWord();
                if (marker == 0x74786574) { // 'text'
                    if (len < inputStream.getLength()) songMessage = inputStream.readString(len);
                } else if (marker == 0x4944494D) { // 'MIDI'
                    // read the MidiMacros
                    if (len == MidiMacros.SIZE_OF_SCTUCT && len < inputStream.getLength()) {
                        midiMacros.loadFrom(inputStream);
                        hasMidiConfig = true;
                    }
                } else {
                    // Skip it
                    if (len < inputStream.getLength())
                        inputStream.skip(len);
                    else
                        break; // something bad happend...
                }
            }
        }

        boolean isMPT = (getModType() & (ModConstants.MODTYPE_MPT | ModConstants.MODTYPE_OMPT)) != 0;
        if (hasExtraInstrumentInfos || hasExtraSongProperties) {
            if (!isMPT) {
                setModType(getModType() | ModConstants.MODTYPE_OMPT);
                isMPT = true;
            }
            if (getPatternContainer().getChannelColors() == null)
                getPatternContainer().createMPTMDefaultRainbowColors();
        }
        if (isMPT && !hasExtraInstrumentInfos && !hasExtraSongProperties) {
            setModType(getModType() & ~(ModConstants.MODTYPE_MPT | ModConstants.MODTYPE_OMPT));
            isMPT = false;
            setTrackerName(getTrackerName() + ModConstants.COMPAT_MODE);
        }
        // Classic FT2: delete midi macros, Zxx effects are illegal there
        if (!hasMidiConfig && !isMPT)
            midiMacros.clearZxxMacros();

        // With OpenModPlug Files we create default channel colors if none are set
        if (isMPT && getPatternContainer().getChannelColors() == null)
            getPatternContainer().createMPTMDefaultRainbowColors();
    }
}
