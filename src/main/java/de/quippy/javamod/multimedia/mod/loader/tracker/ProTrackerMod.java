/*
 * @(#) ProTrackerMod.java
 *
 * Created on 21.04.2006 by Daniel Becker
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

import java.io.IOException;

import de.quippy.javamod.io.ModfileInputStream;
import de.quippy.javamod.multimedia.mod.ModConstants;
import de.quippy.javamod.multimedia.mod.loader.Module;
import de.quippy.javamod.multimedia.mod.loader.instrument.InstrumentsContainer;
import de.quippy.javamod.multimedia.mod.loader.instrument.Sample;
import de.quippy.javamod.multimedia.mod.loader.pattern.PatternContainer;
import de.quippy.javamod.multimedia.mod.loader.pattern.PatternElement;
import de.quippy.javamod.multimedia.mod.midi.MidiMacros;
import de.quippy.javamod.multimedia.mod.mixer.BasicModMixer;
import de.quippy.javamod.multimedia.mod.mixer.ProTrackerMixer;
import de.quippy.javamod.system.Helpers;


/**
 * @author Daniel Becker
 * @since 21.04.2006
 */
public class ProTrackerMod extends Module {

    private static final String[] MODFILEEXTENSION = {
            "stk", "nst", "mod", "wow"
    };

    private boolean isAmigaLike;            // Protracker like AMIGA mods. Others are played in XM-Mode
    private boolean isDeltaPacked;
    private boolean isStarTrekker;
    private boolean isNoiseTracker;            // No pattern breaks with noise tracker
    private boolean isGenericMultiChannel;
    private boolean isMdKd;
    private boolean modSpeedIsTicks;        // changes playing behavior to set always speed (ticks), never BPM
    //	private boolean swapBytes;				// For .DTM files from Apocalypse Abyss, where the first 2108 bytes are swapped - we do not support that yet!
    private boolean ft2Tremolos;            // Tremolo Ramp Down Waveform behavior change for some mods (FT2 style)

//    /**
//     * Not yet used - let's see, if we need that once...
//     *
//     * @return the isAmigaLike
//     */
//    public boolean isAmigaLike() {
//        return isAmigaLike;
//    }

    @Override
    public String[] getFileExtensionList() {
        return MODFILEEXTENSION;
    }

    @Override
    public BasicModMixer getModMixer(int sampleRate, int doISP, int doNoLoops, int maxNNAChannels) {
        return new ProTrackerMixer(this, sampleRate, doISP, doNoLoops, maxNNAChannels);
    }

    @Override
    public int getPanningSeparation() {
        return 128;
    }

    @Override
    public int getPanningValue(int channel) {
        if ((channel % 3) != 0)
            return ModConstants.OLD_PANNING_RIGHT;
        else
            return ModConstants.OLD_PANNING_LEFT;
    }

    @Override
    public int getChannelVolume(int channel) {
        return 64;
    }

    @Override
    public int getFrequencyTable() {
        return (isAmigaLike) ? ModConstants.AMIGA_TABLE : ModConstants.XM_AMIGA_TABLE;
    }

    @Override
    public String getSongMessage() {
        return null;
    }

    @Override
    public MidiMacros getMidiConfig() {
        return null;
    }

    @Override
    public boolean getFT2Tremolo() {
        return ft2Tremolos;
    }

    @Override
    public boolean getModSpeedIsTicks() {
        return modSpeedIsTicks;
    }

    @Override
    public boolean checkLoadingPossible(ModfileInputStream inputStream) throws IOException {
        inputStream.seek(1080);
        String modID = inputStream.readString(4);
        inputStream.seek(1080);
        int magicNumber = inputStream.readMotorolaDWord();
        inputStream.seek(0);
        return modID.equals("M.K.") || modID.equals("M!K!") || modID.equals("PATT") || modID.equals("NSMS") || modID.equals("LARD") ||
                modID.equals("M&K!") || modID.equals("FEST") || modID.equals("N.T.") ||
                modID.equals("OKTA") || modID.equals("OCTA") ||
                modID.equals("CD81") || modID.equals("CD61") ||
                magicNumber == 0x4D000000 || magicNumber == 0x38000000 ||
                modID.startsWith("FA0") ||
                modID.startsWith("FLT") || modID.startsWith("EX0") ||
                modID.endsWith("CHN") ||
                modID.endsWith("CH") || modID.endsWith("CN") ||
                modID.startsWith("TDZ") ||
                modID.equals(".M.K") ||
                modID.equals("WARD") ||
                modID.equals("!PM!");
    }

    /**
     * @param modID
     * @param magicNumber
     * @return
     * @since 21.04.2006
     */
    private String getModType(String modID, int magicNumber) {
        songFlags = ModConstants.SONG_AMIGALIMITS;
        songFlags |= ModConstants.SONG_ISSTEREO;

        isAmigaLike = false;
        isDeltaPacked = false;
        isNoiseTracker = false;
        isStarTrekker = false;
        isMdKd = false;
        modSpeedIsTicks = false;
//        swapBytes = false;
        isGenericMultiChannel = false;

        if (modID.length() == 4) {
            setNSamples(31);
            switch (modID) {
                case "M.K.", "M!K!", "PATT", "NSMS", "LARD" -> {
                    isAmigaLike = true;
                    isMdKd = modID.equals("M.K.");
                    setNChannels(4);
                    return "ProTracker or compatible (" + modID + ")";
                }
                case "M&K!", "FEST", "N.T." -> {
                    isAmigaLike = true;
                    isNoiseTracker = true;
                    modSpeedIsTicks = true;
                    setNChannels(4);
                    return "NoiseTracker (" + modID + ")";
                }
                case "OKTA", "OCTA" -> {
                    setNChannels(8);
                    return "Oktalyzer (" + modID + ")";
                }
                case "CD81", "CD61" -> {
                    setNChannels(Integer.parseInt(Character.toString(modID.charAt(2))));
                    return "Oktalyzer (Atari " + modID + ")";
                }
            }
            if (magicNumber == 0x4D000000 || magicNumber == 0x38000000) {
                isDeltaPacked = true;
                if (modID.charAt(0) == '8') setNChannels(8);
                else setNChannels(4);
                return "Inconexia demo (" + modID + ")";
            }
            if (modID.startsWith("FA0")) {
                setNChannels(Integer.parseInt(Character.toString(modID.charAt(3))));
                return "Digital Tracker (Atari Falcon " + modID + ")";
            }
            if (modID.startsWith("FLT") || modID.startsWith("EX0")) {
                isStarTrekker = true;
                modSpeedIsTicks = true;
                setNChannels(Integer.parseInt(Character.toString(modID.charAt(3))));
                return "Startrekker (" + modID + ")";
            }
            if (modID.endsWith("CHN")) {
                isGenericMultiChannel = true;
                setNChannels(Integer.parseInt(Character.toString(modID.charAt(0))));
                return "Generic MOD compatible Tracker (" + modID + ")";
            }
            if (modID.endsWith("CH") || modID.endsWith("CN")) {
                isGenericMultiChannel = true;
                setNChannels(Integer.parseInt(modID.substring(0, 2)));
                return "Generic MOD compatible Tracker (" + modID + ")";
            }
            if (modID.equals("WARD")) {
                isGenericMultiChannel = true;
                setNChannels(8);
                return "Generic MOD compatible Tracker (" + modID + ")";
            }
            if (modID.startsWith("TDZ")) {
                setNChannels(Integer.parseInt(Character.toString(modID.charAt(3))));
                return "TakeTracker (" + modID + ")";
            }
//            if (modID.equals(".M.K")) {
//                setNChannels(4);
//                swapBytes = true;
//                return "Game Apocalypse Abyss (.M.K)";
//            }
            if (modID.equals("!PM!")) { // 14.12.2023: Someone came up with these..
                isDeltaPacked = true;
                setNChannels(4);
                return "Unknown Tracker (" + modID + ")";
            }
        }

        // Noise Tracker 15 samples 4 channels has no magic ID, so it's the rest...
        isAmigaLike = true;
        isNoiseTracker = true;
        setNSamples(15);
        setNChannels(4);
        setModID("NONE");
        return "NoiseTracker (no ID)";
    }

    /**
     * To support ADPCM packed samples we cannot just add up all sample lengths,
     * as packed samples show their unpacked length (for loop fitting)
     * We will read backwards through the mod and look out for ADPCM packed
     * samples by reading the magic word. If found, we adjust the length for reading
     * so the calculatePatternCount can rely on the physical data.
     * On that way we already set the loading flags.
     *
     * @param inputStream
     * @return
     * @throws IOException
     * @since 23.01.2024
     */
    private int calculateSampleDataCount(ModfileInputStream inputStream) throws IOException {
        long reSeek = inputStream.getFilePointer();
        int fullSampleLength = 0;
        long seek = inputStream.getLength();
        for (int i = getNSamples() - 1; i >= 0; i--) {
            Sample current = getInstrumentContainer().getSample(i);
            int sampleLength = current.length;
            if (isDeltaPacked) { // this is only one MOD Type - and those are never with ADPCM
                current.setSampleType(ModConstants.SM_PCMD);
            } else {
                boolean isADPCM = false;
                int ADPCMLength = ((sampleLength + 1) >> 1) + 16 + 5; //shorten length and add header and magic
                if (sampleLength > 0) { // we can agree, that zero length samples are never ADPCM packed...
                    inputStream.seek(seek - ADPCMLength);
                    byte[] magic = new byte[5];
                    inputStream.read(magic, 0, 5);
                    String ADPCMMagic = Helpers.retrieveAsString(magic, 0, 5);
                    if (ADPCMMagic.equals("ADPCM")) isADPCM = true;
                }
                if (isADPCM) {
                    current.setSampleType(ModConstants.SM_ADPCM);
                    sampleLength = ADPCMLength;
                } else {
                    current.setSampleType(ModConstants.SM_PCMS);
                }
            }
            seek -= sampleLength;
            fullSampleLength += 30 + sampleLength;
        }
        inputStream.seek(reSeek);

        return fullSampleLength;
    }

    /**
     * Many mod files are too short or too long.
     * Here we try to find out about this, as the real
     * saved count of pattern is not saved anywhere.
     *
     * @param fileSize
     * @return
     */
    private int calculatePatternCount(int fileSize, int fullSampleLength) {
        int headerLen = 150;                    // Name+SongLen+CIAA+SongArrangement
        if (getNSamples() > 15) headerLen += 4;    // Kennung

        int spaceForPattern = fileSize - headerLen - fullSampleLength;

        // Lets find out about the highest Patternnumber used
        // in the song arrangement
        int maxPatternNumber = 0;
        for (int i = 0; i < getSongLength(); i++) {
            int patternNumber = getArrangement()[i];
            if (patternNumber > maxPatternNumber && patternNumber < 0x80)
                maxPatternNumber = getArrangement()[i];
        }
        maxPatternNumber++; // Highest number becomes highest count

        // It could be the WOW-Format:
        if (isMdKd) {
            // so check for 8 channels:
            int totalPatternBytes = maxPatternNumber * (64 * 4 * 8);
            // This mod has 8 channels! --> WOW
            if (totalPatternBytes == spaceForPattern) {
                isAmigaLike = true;
                setNChannels(8);
                setTrackerName("Grave Composer (" + getModID() + ")");
            }
        }

        int bytesPerPattern = 64 * 4 * getNChannels();
        int patternCount = spaceForPattern / bytesPerPattern;
        int bytesLeft = spaceForPattern % bytesPerPattern;
        setNPattern(patternCount);

        if (bytesLeft > 0) { // It does not fit!
            if (maxPatternNumber > patternCount) {
                // The modfile is too short. The highest pattern is reaching into
                // the sampledata, but it has to be read!
                bytesLeft -= bytesPerPattern;
                setNPattern(maxPatternNumber + 1);
            } else {
                // The modfile is too long. Sometimes this happens if composer
                // add additional data to the modfile.
                bytesLeft += (getNPattern() - maxPatternNumber) * bytesPerPattern;
                setNPattern(maxPatternNumber);
            }

            return bytesLeft;
        }

        return 0;
    }

    /**
     * Create the new Pattern element
     *
     * @param pattNum
     * @param row
     * @param channel
     * @param note
     * @return
     */
    private void createNewPatternElement(PatternContainer patternContainer, int pattNum, int row, int channel, int note) {
        PatternElement pe = patternContainer.createPatternElement(pattNum, row, channel);

        if (getNSamples() > 15) {
            pe.setInstrument((((note & 0xF0000000) >> 24) | ((note & 0xF000) >> 12)) & getNSamples()); // & 0x1F
            pe.setPeriod((note & 0x0FFF0000) >> 16);
        } else {
            pe.setInstrument(((note & 0xF000) >> 12) & getNSamples()); // &0x0F
            pe.setPeriod((note & 0xffFF0000) >> 16);
        }

        if (pe.getPeriod() < 14 || pe.getPeriod() > 6848)
            pe.setPeriod(0);
        else if (pe.getPeriod() > 0) {
            int noteIndex = ModConstants.getNoteIndexForPeriod(pe.getPeriod());
            if (noteIndex > 0)
                pe.setNoteIndex(noteIndex + 1);
            else
                pe.setPeriod(0);
        }

        pe.setEffect((note & 0xF00) >> 8);
        pe.setEffectOp(note & 0xff);

        if (pe.getEffect() == 0x0C && pe.getEffectOp() > 64) pe.setEffectOp(64);
        if (isStarTrekker) {
            if (pe.getEffect() == 0x0E) {
                // No support for StarTrekker assembly macros
                pe.setEffect(0);
                pe.setEffectOp(0);
            } else if (pe.getEffect() == 0x0F && pe.getEffectOp() > 0x1F) {
                // StarTrekker caps speed at 31 ticks per row
                pe.setEffectOp(0x1F);
            }
        }
        if (isNoiseTracker && pe.getEffect() == 0x0D) {
            // No pattern break operator in NoiseTracker
            pe.setEffectOp(0);
        }
    }

    @Override
    protected void loadModFileInternal(ModfileInputStream inputStream) throws IOException {
        inputStream.seek(1080);
        setModID(inputStream.readString(4));
        inputStream.seek(1080);
        int magicNumber = inputStream.readMotorolaDWord();
        inputStream.seek(0);
        setTrackerName(getModType(getModID(), magicNumber)); // sets isAmigaLike
        // if is not amiga like, playback is done as if this is an XM
        // isMOD is only for Protracker like mods!!!
        setModType((isAmigaLike) ? ModConstants.MODTYPE_MOD : ModConstants.MODTYPE_XM);

        ft2Tremolos = (isGenericMultiChannel || isMdKd); // ProTracker and FastTracker do both have this bug
        boolean isFLT8 = isStarTrekker && getNChannels() == 8;
        boolean isHMNT = getModID().equals("M&K!") || getModID().equals("FEST");

        setTempo(6);
        setBPMSpeed(125);
        setBaseVolume(ModConstants.MAXGLOBALVOLUME);
        int preAmp = ModConstants.MAX_MIXING_PREAMP / getNChannels();
        setMixingPreAmp((preAmp < ModConstants.MIN_MIXING_PREAMP) ? ModConstants.MIN_MIXING_PREAMP : (preAmp > 0x80) ? 0x80 : preAmp);

        setSongName(inputStream.readString(20));

        setNInstruments(getNSamples());
        InstrumentsContainer instrumentContainer = new InstrumentsContainer(this, 0, getNSamples());
        setInstrumentContainer(instrumentContainer);
        for (int i = 0; i < getNSamples(); i++) {
            Sample current = new Sample();
            // Samplename
            current.setName(inputStream.readString(22));
            current.setStereo(false); // Default

            // Length
            current.setLength(inputStream.readMotorolaUnsignedWord() << 1);

            int fine = 0;
            if (isHMNT) // His Masters Noise - Noise Tracker (FEST) - different FineTune setting...
                fine = (-((int) inputStream.readByte())) >> 1;
            else {
                fine = inputStream.read() & 0xF;
                // finetune Value is a two's complement based on four bits
                fine = fine > 7 ? fine - 16 : fine;
            }
            current.setFineTune((isAmigaLike) ? fine : fine << 4); // if not amiga like, we use XM_AMIGA_TABLE - finetune is -128-+127 then
            // BaseFrequenzy from Table: FineTune is -8...+7
            current.setBaseFrequency(ModConstants.IT_fineTuneTable[fine + 8]);
            current.setTranspose(0);

            if (current.length > 65535) isNoiseTracker = false;

            // volume 64 is maximum
            int vol = inputStream.read() & 0x7F;
            current.setVolume((vol > 64) ? 64 : vol);
            current.setGlobalVolume(ModConstants.MAXSAMPLEVOLUME);

            // Repeat start and stop
            int repeatStart = inputStream.readMotorolaUnsignedWord() << 1;
            int repeatLength = inputStream.readMotorolaUnsignedWord() << 1;
            int repeatStop = repeatStart + repeatLength;

            if (current.length < 4) current.length = 0;
            if (current.length > 0) {
                if (repeatStart > current.length) repeatStart = current.length - 1;
                if (repeatStop > current.length) repeatStop = current.length;
                if (repeatStart >= repeatStop || repeatStop <= 8 || (repeatStop - repeatStart) <= 4) {
                    repeatStart = repeatStop = 0;
                    current.setLoopType(0);
                }
                if (repeatStart < repeatStop)
                    current.setLoopType(ModConstants.LOOP_ON);
            } else
                current.setLoopType(0);

            current.setLoopStart(repeatStart);
            current.setLoopStop(repeatStop);
            current.setLoopLength(repeatStop - repeatStart);

            // Defaults for non-existent SustainLoop
            current.setSustainLoopStart(0);
            current.setSustainLoopStop(0);
            current.setSustainLoopLength(0);

            // Defaults!
            current.setPanning(false);
            current.setDefaultPanning(128);

            instrumentContainer.setSample(i, current);
        }

        // count of pattern in arrangement
        setSongLength(inputStream.read());
        // good old CIAA? Seems to be a restart position.
        // is often 0x7F(old CIAA) or 0x78 (120BPM)
        // we check later
        setSongRestart(inputStream.read());

        // always space for 128 pattern...
        allocArrangement(128);
        for (int i = 0; i < 128; i++) {
            int pattNum = inputStream.read();
            getArrangement()[i] = (isFLT8) ? pattNum >> 1 : pattNum; // FLT8 has only even order items, so divide by two.
        }

        // order sanity check
        int realOrder = getSongLength();
        if (realOrder > 128)
            realOrder = 128;
        else if (realOrder == 0) {
            realOrder = 128;
            while (realOrder > 0 && getArrangement()[realOrder - 1] == 0) realOrder--;
        }
        setSongLength(realOrder);

        // Song restart sanity check
        if (getSongRestart() > realOrder || ((getSongRestart() == 0x78 || getSongRestart() == 0x7F) && getNChannels() == 4))
            setSongRestart(0);

        // skip ModID, if not NoiseTracker:
        if (getNSamples() > 15) inputStream.skip(4);
        // Digital Tracker MODs contain four bytes (00 40 00 00) right after the magic bytes which don't seem to do anything special.
        if (getModID().startsWith("FA0")) inputStream.skip(4);

        // Read the pattern data
        // First, we will read backwards through the mod to detect ADPCM packed samples. While on the way
        // we already set the loading flags accordingly
        int fullSampleLength = calculateSampleDataCount(inputStream);
        // now lets find out how many patterns we have / would really fit
        int bytesLeft = calculatePatternCount((int) inputStream.getLength(), fullSampleLength); // Get the amount of pattern and keep "bytesLeft" in mind!

        PatternContainer patternContainer = new PatternContainer(this, getNPattern(), 64, getNChannels());
        setPatternContainer(patternContainer);
        for (int pattNum = 0; pattNum < getNPattern(); pattNum++) {
            if (isFLT8) { // StarTrekker 8 channel is slightly different to read
                for (int row = 0; row < 64; row++) {
                    for (int channel = 0; channel < 4; channel++) {
                        int value = inputStream.readMotorolaDWord();
                        createNewPatternElement(patternContainer, pattNum, row, channel, value);
                    }
                }
                for (int row = 0; row < 64; row++) {
                    for (int channel = 4; channel < 8; channel++) {
                        int value = inputStream.readMotorolaDWord();
                        createNewPatternElement(patternContainer, pattNum, row, channel, value);
                    }
                }
            } else {
                for (int row = 0; row < 64; row++) {
                    for (int channel = 0; channel < getNChannels(); channel++) {
                        int value = inputStream.readMotorolaDWord();
                        createNewPatternElement(patternContainer, pattNum, row, channel, value);
                    }
                }
            }
        }
        // Sample data: If the mod file was too short, we need to recalculate:
        if (bytesLeft < 0) {
            setTrackerName(getTrackerName() + " (too short for " + (-bytesLeft) + " bytes)");
            int calcSamplePos = (int) inputStream.getLength() - fullSampleLength;
            // do this only, if needed!
            if (calcSamplePos < inputStream.getFilePointer()) inputStream.seek(calcSamplePos);
        } else if (bytesLeft > 0) {
            setTrackerName(getTrackerName() + " (too long for " + bytesLeft + " bytes)");
        }

        for (int i = 0; i < getNSamples(); i++) {
            Sample current = getInstrumentContainer().getSample(i);
            if ((current.sampleType & ModConstants.SM_ADPCM) != 0) inputStream.skip(5);
            readSampleData(current, inputStream);
        }
        cleanUpArrangement();
    }
}
