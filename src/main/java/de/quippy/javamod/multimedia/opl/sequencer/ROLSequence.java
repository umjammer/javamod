/*
 * @(#) ROLSequence.java
 *
 * Created on 03.08.2020 by Daniel Becker
 *
 * -----------------------------------------------------------------------
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * ----------------------------------------------------------------------
 *
 * Java port of ROL.CPP by OPLx for adplug project
 *
 * Java port and optimizations by Daniel Becker
 * - verified loading against my old Effector loading routine
 *   -> check for section start during load - some ROLs are corrupt!
 *   -> That the fillers contain the name of following section is undocumented
 *      but AdLib Composer wrote those
 * - volume calculation optimized
 * - unnecessary event checking during play removed.
 *   If there are no more events (array size exceeded), we find out
 *   without setting a marker
 * - play till real end, not only till last note (possible pitch event after
 *   last note would be ignored)
 * - no table for note index or octave needed. Lookup takes same time as
 *   calculation (index = note%12; octave = note/12)
 */

package de.quippy.javamod.multimedia.opl.sequencer;

import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.quippy.javamod.io.RandomAccessInputStream;
import de.quippy.javamod.io.RandomAccessInputStreamImpl;
import de.quippy.javamod.multimedia.MultimediaContainerManager;
import de.quippy.javamod.multimedia.opl.emu.EmuOPL;
import de.quippy.javamod.multimedia.opl.emu.EmuOPL.OplType;
import de.quippy.javamod.system.Helpers;
import vavi.io.LittleEndianDataInputStream;

import static java.lang.System.getLogger;


/**
 * @author Daniel Becker
 * @see "https://moddingwiki.shikadi.net/wiki/ROL_Format"
 * @since 03.08.2020
 */
public class ROLSequence extends OPL3Sequence {

    private static final Logger logger = getLogger(ROLSequence.class.getName());

    private static final int ROL_COMMENT_SIZE = 40;
//    private static final int ROL_UNUSED1_SIZE = 1;
    private static final int ROL_UNUSED2_SIZE = 90;
    private static final int ROL_FILLER0_SIZE = 38;
    private static final int ROL_FILLER1_SIZE = 15;
    private static final int ROL_FILLER_SIZE = 15;
    private static final int ROL_MAX_NAME_SIZE = 9;
    private static final int ROL_INSTRUMENT_EVENT_FILLER_SIZE = 3; // 1 for filler, 2 for unused
    private static final int ROL_BNK_SIGNATURE_SIZE = 6;

    // ----

    private static final int skMidPitch = 0x2000;
    private static final int skNrStepPitch = 25; // 25 steps within a half-tone for pitch bend
    private static final int skVersionMajor = 0;
    private static final int skVersionMinor = 4;
    private static final int skVolumeQualityShift = 7;
    private static final int skMaxVolume = (1 << skVolumeQualityShift);
    private static final int skMaxNotes = 96;
    private static final int skCarrierOpOffset = 3;
    private static final int skNumSemitonesInOctave = 12;

    // ----

    /** Test LSI / Enable waveform control */
    private static final int skOPL2_WaveCtrlBaseAddress = 0x01;
    /** Amp Mod / Vibrato / EG type / Key Scaling / Multiple */
    private static final int skOPL2_AaMultiBaseAddress = 0x20;
    /** Key scaling level / Operator output level */
    private static final int skOPL2_KSLTLBaseAddress = 0x40;
    /** Attack Rate / Decay Rate */
    private static final int skOPL2_ArDrBaseAddress = 0x60;
    /** Sustain Level / Release Rate */
    private static final int skOPL2_SlrrBaseAddress = 0x80;
    /** Frequency (low 8 bits) */
    private static final int skOPL2_FreqLoBaseAddress = 0xA0;
    /** Key On / Octave / Frequency (high 2 bits) */
    private static final int skOPL2_KeyOnFreqHiBaseAddress = 0xB0;
    /** AM depth / Vibrato depth / Rhythm control */
    private static final int skOPL2_AmVibRhythmBaseAddress = 0xBD;
    /** Feedback strength / Connection type */
    private static final int skOPL2_FeedConBaseAddress = 0xC0;
    /** Waveform select */
    private static final int skOPL2_WaveformBaseAddress = 0xE0;

    // ----

    private static final int skOPL2_EnableWaveformSelectMask = 0x20;
    private static final int skOPL2_KeyOnMask = 0x20;
    private static final int skOPL2_RhythmMask = 0x20;
    private static final int skOPL2_KSLMask = 0xC0;
    private static final int skOPL2_TLMask = 0x3F;
    private static final int skOPL2_TLMinLevel = 0x3F;
    private static final int skOPL2_FNumLSBMask = 0xff;
    private static final int skOPL2_FNumMSBMask = 0x03;
    private static final int skOPL2_FNumMSBShift = 0x08;
    private static final int skOPL2_BlockNumberShift = 0x02;

    // ----

    private String FILLER_NOTE_SECTION = "Voix"; // Effector uses "Notes" here - my bad!
    private static final String FILLER_EFFEKTER_SECTION = "Notes";
    private static final String FILLER_TIMBRE_SECTION = "Timbre";
    private static final String FILLER_VOLUME_SECTION = "Volume";
    private static final String FILLER_PITCH_SECTION = "Pitch";

    // ----

    private static final String EFFEKTER_MAGIC_STRING = "EFFEKTER"; // We can handle my own written effecter files.

    // ----

    /** Table below generated by initialize_fnum_table function (from Adlib Music SDK). */
    private static final int[] skFNumNotes /* [skNrStepPitch][skNumSemitonesInOctave] */ = {
            343, 364, 385, 408, 433, 459, 486, 515, 546, 579, 614, 650,
            344, 365, 387, 410, 434, 460, 488, 517, 548, 581, 615, 652,
            345, 365, 387, 410, 435, 461, 489, 518, 549, 582, 617, 653,
            346, 366, 388, 411, 436, 462, 490, 519, 550, 583, 618, 655,
            346, 367, 389, 412, 437, 463, 491, 520, 551, 584, 619, 657,
            347, 368, 390, 413, 438, 464, 492, 522, 553, 586, 621, 658,
            348, 369, 391, 415, 439, 466, 493, 523, 554, 587, 622, 660,
            349, 370, 392, 415, 440, 467, 495, 524, 556, 589, 624, 661,
            350, 371, 393, 416, 441, 468, 496, 525, 557, 590, 625, 663,
            351, 372, 394, 417, 442, 469, 497, 527, 558, 592, 627, 665,
            351, 372, 395, 418, 443, 470, 498, 528, 559, 593, 628, 666,
            352, 373, 396, 419, 444, 471, 499, 529, 561, 594, 630, 668,
            353, 374, 397, 420, 445, 472, 500, 530, 562, 596, 631, 669,
            354, 375, 398, 421, 447, 473, 502, 532, 564, 597, 633, 671,
            355, 376, 398, 422, 448, 474, 503, 533, 565, 599, 634, 672,
            356, 377, 399, 423, 449, 475, 504, 534, 566, 600, 636, 674,
            356, 378, 400, 424, 450, 477, 505, 535, 567, 601, 637, 675,
            357, 379, 401, 425, 451, 478, 506, 537, 569, 603, 639, 677,
            358, 379, 402, 426, 452, 479, 507, 538, 570, 604, 640, 679,
            359, 380, 403, 427, 453, 480, 509, 539, 571, 606, 642, 680,
            360, 381, 404, 428, 454, 481, 510, 540, 572, 607, 643, 682,
            360, 382, 405, 429, 455, 482, 511, 541, 574, 608, 645, 683,
            361, 383, 406, 430, 456, 483, 512, 543, 575, 610, 646, 685,
            362, 384, 407, 431, 457, 484, 513, 544, 577, 611, 648, 687,
            363, 385, 408, 432, 458, 485, 514, 545, 578, 612, 649, 688
    };

    // ----

    private static final int[] drum_op_table = {
            0x14, 0x12, 0x15, 0x11
    };
    private static final int[] op_table = {
            0x00, 0x01, 0x02, 0x08, 0x09, 0x0a, 0x10, 0x11, 0x12
    };

    // ----

    private static final int kSizeofDataRecord = 30;
    private static final int kMaxTickBeat = 60;
    private static final int kSilenceNote = -12;
    private static final int kNumMelodicVoices = 9;
    private static final int kNumPercussiveVoices = 11;
    private static final int kBassDrumChannel = 6;
    private static final int kSnareDrumChannel = 7;
    private static final int kTomtomChannel = 8;
    private static final int kTomTomNote = 24;
    private static final int kTomTomToSnare = 7; // 7 half-tones between voice 7 & 8
    private static final int kSnareNote = kTomTomNote + kTomTomToSnare;
    private static final double kDefaultUpdateTme = 18.2;

    // ----

    private static final class SRolHeader {

        private int versionMajor;
        private int versionMinor;
        private String comment;
        private int ticksPerBeat;
        private int beatsPerMeasure;
        private int editScaleY;
        private int editScaleX;
        private byte unused1;
        private byte mode;
        private final byte[] unused = new byte[ROL_UNUSED2_SIZE + ROL_FILLER0_SIZE + ROL_FILLER1_SIZE];
        private double basicTempo;

        private static SRolHeader readMe(RandomAccessInputStream inputStream) throws IOException {
            SRolHeader result = new SRolHeader();
            result.versionMajor = inputStream.readIntelWord();
            result.versionMinor = inputStream.readIntelWord();
            result.comment = inputStream.readString(ROL_COMMENT_SIZE);
            result.ticksPerBeat = inputStream.readIntelWord();
            if (result.ticksPerBeat > kMaxTickBeat) result.ticksPerBeat = kMaxTickBeat;
            result.beatsPerMeasure = inputStream.readIntelWord();
            result.editScaleX = inputStream.readIntelWord();
            result.editScaleY = inputStream.readIntelWord();
            result.unused1 = inputStream.readByte();
            result.mode = inputStream.readByte();
            inputStream.read(result.unused);
            result.basicTempo = inputStream.readIntelFloat();
            return result;
        }

        @Override
        public String toString() {
            String sb = "Version:" + versionMajor + '.' + versionMinor +
                    " Comment:" + comment +
                    " Ticks: " + ticksPerBeat + '/' + beatsPerMeasure +
                    " Edit scale: " + editScaleX + '/' + editScaleY +
                    " Mode: " + mode +
                    " Unused: [" + unused1 + ", " + Arrays.toString(unused) + ']';
            return sb;
        }
    }

    private static final class STempoEvent {

        private int time;
        private double multiplier;

        private static STempoEvent readMe(RandomAccessInputStream inputStream) throws IOException {
            STempoEvent event = new STempoEvent();
            event.time = inputStream.readIntelWord();
            event.multiplier = inputStream.readIntelFloat();
            if (event.multiplier < 0.01) event.multiplier = 0.01;
            else if (event.multiplier > 10.0) event.multiplier = 10.0;
            return event;
        }

        @Override
        public String toString() {
            return "{" + time + ", " + multiplier + "}";
        }
    }

    private static final class SNoteEvent {

        private int number;
        private int duration;

        private static SNoteEvent readMe(RandomAccessInputStream inputStream) throws IOException {
            SNoteEvent event = new SNoteEvent();
            event.number = inputStream.readIntelWord();
            event.duration = inputStream.readIntelWord();
            event.number += kSilenceNote; // adding -12
            return event;
        }

        @Override
        public String toString() {
            return "{" + number + ", " + duration + "}";
        }
    }

    private static final class SInstrumentEvent {

        private int time;
        private String name;
        private int insIndex;

        private static SInstrumentEvent readMe(RandomAccessInputStream inputStream) throws IOException {
            SInstrumentEvent event = new SInstrumentEvent();
            event.time = inputStream.readIntelWord();
            event.name = inputStream.readString(ROL_MAX_NAME_SIZE).toUpperCase();
            return event;
        }

        @Override
        public String toString() {
            return "{" + time + ", \"" + name + "\", " + insIndex + "}";
        }
    }

    private static final class SVolumeEvent {

        private int time;
        private double multiplier;

        private static SVolumeEvent readMe(RandomAccessInputStream inputStream) throws IOException {
            SVolumeEvent event = new SVolumeEvent();
            event.time = inputStream.readIntelWord();
            event.multiplier = inputStream.readIntelFloat();
            if (event.multiplier < 0) event.multiplier = 0;
            else if (event.multiplier > 1.0) event.multiplier = 1.0;
            return event;
        }

        @Override
        public String toString() {
            return "{" + time + ", " + multiplier + "}";
        }
    }

    private static final class SPitchEvent {

        private int time;
        private double variation;

        private static SPitchEvent readMe(RandomAccessInputStream inputStream) throws IOException {
            SPitchEvent event = new SPitchEvent();
            event.time = inputStream.readIntelWord();
            event.variation = inputStream.readIntelFloat();
            if (event.variation < 0) event.variation = 0;
            else if (event.variation > 2.0) event.variation = 2.0;
            return event;
        }

        @Override
        public String toString() {
            return "{" + time + ", " + variation + "}";
        }
    }

    private static final class CVoiceData {

        private List<SNoteEvent> noteEvents;
        private List<SInstrumentEvent> instrumentEvents;
        private List<SVolumeEvent> volumeEvents;
        private List<SPitchEvent> pitchEvents;

        private int mNoteDuration;
        private int currentNoteDuration;
        private int nextNoteEvent;
        private int nextInstrumentEvent;
        private int nextVolumeEvent;
        private int nextPitchEvent;

        private CVoiceData() {
            reset();
        }

        private void reset() {
            mNoteDuration =
                    currentNoteDuration =
                            nextNoteEvent =
                                    nextInstrumentEvent =
                                            nextVolumeEvent =
                                                    nextPitchEvent = 0;
        }
    }

    // ----

    private static final class SInstrumentName {

        private int index;
        private byte record_used;
        private String name;

        private static SInstrumentName readMe(RandomAccessInputStreamImpl inputStream) throws IOException {
            SInstrumentName result = new SInstrumentName();
            result.index = inputStream.readIntelWord();
            result.record_used = inputStream.readByte();
            result.name = inputStream.readString(ROL_MAX_NAME_SIZE).toUpperCase();
            return result;
        }

        @Override
        public String toString() {
            return index + ". " + name + "[" + (record_used != 0 ? "X" : " ") + "]";
        }
    }

    private static final class SBnkHeader {

        private int versionMajor;
        private int versionMinor;
        private String signature;
        private int numberOfListEntriesUsed;
        private int totalNumberOfListEntries;
        private long absOffsetOfNameList;
        private long absOffsetOfData;

        private List<SInstrumentName> ins_name_list;

        private static SBnkHeader readMe(RandomAccessInputStreamImpl inputStream) throws IOException {
            SBnkHeader result = new SBnkHeader();
            result.versionMajor = inputStream.read();
            result.versionMinor = inputStream.read();
            result.signature = inputStream.readString(ROL_BNK_SIGNATURE_SIZE);
            result.numberOfListEntriesUsed = inputStream.readIntelWord();
            result.totalNumberOfListEntries = inputStream.readIntelWord();
            result.absOffsetOfNameList = inputStream.readIntelDWord();
            result.absOffsetOfData = inputStream.readIntelDWord();
            result.ins_name_list = new ArrayList<>(result.totalNumberOfListEntries);

            inputStream.seek(result.absOffsetOfNameList);
            for (int i = 0; i < result.totalNumberOfListEntries; i++)
                result.ins_name_list.add(SInstrumentName.readMe(inputStream));

            return result;
        }

        @Override
        public String toString() {
            return signature + " V" + versionMajor + "." + versionMinor + " " + numberOfListEntriesUsed + " of " + totalNumberOfListEntries + " used";
        }
    }

    private static final class SFMOperator {

        private int keyScaleLevel;
        private int freqMultiplier;
        private int feedBack;
        private int attackRate;
        private int sustainLevel;
        private int sustainingSound;
        private int decayRate;
        private int releaseRate;
        private int outputLevel;
        private int amplitudeVibrato;
        private int frequencyVibrato;
        private int envelopeScaling;
        private int fmType;

        private static SFMOperator readMe(RandomAccessInputStream inputStream) throws IOException {
            SFMOperator result = new SFMOperator();
            result.keyScaleLevel = inputStream.read();
            result.freqMultiplier = inputStream.read();
            result.feedBack = inputStream.read();
            result.attackRate = inputStream.read();
            result.sustainLevel = inputStream.read();
            result.sustainingSound = inputStream.read();
            result.decayRate = inputStream.read();
            result.releaseRate = inputStream.read();
            result.outputLevel = inputStream.read();
            result.amplitudeVibrato = inputStream.read();
            result.frequencyVibrato = inputStream.read();
            result.envelopeScaling = inputStream.read();
            result.fmType = inputStream.read();
            return result;
        }
    }

    private static final class SOPL2Op {

        private int ammulti;
        private int ksltl;
        private int ardr;
        private int slrr;
        private int fbc;
        private int waveform;

        private static SOPL2Op readMe(RandomAccessInputStream inputStream) throws IOException {
            SFMOperator fm_op = SFMOperator.readMe(inputStream);
            SOPL2Op result = new SOPL2Op();
            result.ammulti = fm_op.amplitudeVibrato << 7 | fm_op.frequencyVibrato << 6 | fm_op.sustainingSound << 5 | fm_op.envelopeScaling << 4 | fm_op.freqMultiplier;
            result.ksltl = fm_op.keyScaleLevel << 6 | fm_op.outputLevel;
            result.ardr = fm_op.attackRate << 4 | fm_op.decayRate;
            result.slrr = fm_op.sustainLevel << 4 | fm_op.releaseRate;
            result.fbc = fm_op.feedBack << 1 | (fm_op.fmType ^ 1);
            return result;
        }

        @Override
        public String toString() {
            return ammulti + "/" + ksltl + "/" + ardr + "/" + slrr + "/" + fbc;
        }
    }

    private static final class SRolInstrument {

        private int mode;
        private int voice_number;
        private SOPL2Op modulator;
        private SOPL2Op carrier;

        private static SRolInstrument readMe(RandomAccessInputStream inputStream) throws IOException {
            SRolInstrument result = new SRolInstrument();
            result.mode = inputStream.read();
            result.voice_number = inputStream.read();
            result.modulator = SOPL2Op.readMe(inputStream);
            result.carrier = SOPL2Op.readMe(inputStream);
            result.modulator.waveform = inputStream.read();
            result.carrier.waveform = inputStream.read();
            return result;
        }

        @Override
        public String toString() {
            return mode + "/" + voice_number + " modulator[" + modulator.toString() + "] carrier[" + carrier.toString() + "]";
        }
    }

    private static final class SInstrument {

        private String name;
        private SRolInstrument instrument;

        @Override
        public String toString() {
            return name + " " + instrument.toString();
        }
    }

    // ----

    private SRolHeader mpROLHeader;
    private int mpOldFNumFreqPtr;
    private List<STempoEvent> mTempoEvents;
    private List<CVoiceData> mVoiceData;
    private final List<SInstrument> mInstrumentList;
    private final int[] mFNumFreqPtrList;
    private final int[] mHalfToneOffset;
    private final int[] mVolumeCache;
    private final int[] mKSLTLCache;
    private final int[] mNoteCache;
    private final int[] mKOnOctFNumCache;
    private final boolean[] mKeyOnCache;
    private double mRefresh;
    private long mOldPitchBendLength;
    private final int mPitchRangeStep;
    private int mNextTempoEvent;
    private int mCurrTick;
    private int mTimeOfLastNote;
    private int mOldHalfToneOffset;
    private int mAMVibRhythmCache;

    // ----

    private URL rolFile;
    private URL bnkFile;
//    private boolean comp_mode = false;

    /**
     * Constructor for ROLSequence
     */
    public ROLSequence() {
        super();
        mpROLHeader = null;
        mpOldFNumFreqPtr = 0;
        mTempoEvents = null;
        mVoiceData = null;
        mInstrumentList = new ArrayList<>();
        mFNumFreqPtrList = new int[kNumPercussiveVoices];
        mHalfToneOffset = new int[kNumPercussiveVoices];
        mVolumeCache = new int[kNumPercussiveVoices];
        mKSLTLCache = new int[kNumPercussiveVoices];
        mNoteCache = new int[kNumPercussiveVoices];
        mKOnOctFNumCache = new int[kNumMelodicVoices];
        mKeyOnCache = new boolean[kNumPercussiveVoices];
        mRefresh = kDefaultUpdateTme;
        mOldPitchBendLength = ~0;
        mPitchRangeStep = skNrStepPitch;
        mNextTempoEvent = 0;
        mCurrTick = 0;
        mTimeOfLastNote = 0;
        mOldHalfToneOffset = 0;
        mAMVibRhythmCache = 0;
    }

    @Override
    protected boolean isSupportedExtension(String extension) {
        return "ROL".equals(extension);
    }

    @Override
    protected boolean isSupported(InputStream stream) {
        LittleEndianDataInputStream dis = new LittleEndianDataInputStream(stream);
        try {
            dis.mark(8);

            int major = dis.readInt();
            int minor = dis.readInt();

logger.log(Level.DEBUG, "rol major(" + skVersionMajor + "): " + major + ", minor(" + skVersionMinor + "): " + minor);
            return major == skVersionMajor && minor == skVersionMinor;
        } catch (IOException e) {
logger.log(Level.WARNING, e.getMessage(), e);
            return false;
        } finally {
            try {
                dis.reset();
            } catch (IOException e) {
logger.log(Level.DEBUG, e.toString());
            }
        }
    }

    @Override
    protected void initExtra(URL bnkURL) throws IOException {
        if (bnkURL == null) throw new IOException("No bank file specified!");
        this.setBNKFile(bnkURL);
    }

    // ----

    @Override
    protected void readOPL3Sequence(RandomAccessInputStream inputStream) throws IOException {
        if (inputStream == null || inputStream.available() <= 0) return;

        mpROLHeader = SRolHeader.readMe(inputStream);
        if (mpROLHeader.versionMajor != skVersionMajor || mpROLHeader.versionMinor != skVersionMinor)
            throw new IOException("Unsupported ROL-File version V" + mpROLHeader.versionMajor + "." + mpROLHeader.versionMinor);

        // Effector uses "Notes" instead of "Voix" for next voice section
        if (mpROLHeader.comment.toUpperCase().contains(EFFEKTER_MAGIC_STRING))
            FILLER_NOTE_SECTION = FILLER_EFFEKTER_SECTION;

        loadTempoEvents(inputStream);
        loadloadVoiceData(inputStream);
    }

    // ----

    /**
     * Read ahead and check for occurrence of string check
     * This is to find new sections, if the ROL file lied
     * to us about the length of a section
     *
     * @param inputStream opl stream
     * @param check string to check
     * @return true, if section found. False otherwise
     * @throws IOException when an error occurs
     * @since 13.08.2020
     */
    private static boolean checkForEvent(RandomAccessInputStream inputStream, String check) throws IOException {
        long currentPosition = inputStream.getFilePointer();
        String compare = inputStream.readString(check.length());
        inputStream.seek(currentPosition);
        return compare.equalsIgnoreCase(check);
    }

    private void loadTempoEvents(RandomAccessInputStream inputStream) throws IOException {
        int numTempoEvents = inputStream.readIntelWord();

        mTempoEvents = new ArrayList<>(numTempoEvents);

        for (int i = 0; i < numTempoEvents && !checkForEvent(inputStream, FILLER_NOTE_SECTION); i++) {
            mTempoEvents.add(STempoEvent.readMe(inputStream));
        }
    }

    private void loadloadVoiceData(RandomAccessInputStream inputStream) throws IOException {
        if (bnkFile == null || !Helpers.urlExists(bnkFile)) throw new IOException("Bankfile not found");

        RandomAccessInputStreamImpl bnkInputStream = null;
        try {
            bnkInputStream = new RandomAccessInputStreamImpl(bnkFile);
            SBnkHeader bnkHeader = SBnkHeader.readMe(bnkInputStream);

            // In my old sources loading a ROL file there was a check for a comp_mode:
            // this ignores the mode value in the header and sets to fix kNumPercussiveVoices
            // if
            // a) we want this check (comp_mode == true)
            // b) "(AdLib file-mode)" can be found in the header comment
            // c) but not ".ROL"
//            if (comp_mode && mpROLHeader.comment.contains("(AdLib file-mode)") && mpROLHeader.comment.indexOf(".ROL") == -1)
//                comp_mode = false;
            int numVoices = /* (comp_mode)? kNumPercussiveVoices : */(mpROLHeader.mode != 0) ? kNumMelodicVoices : kNumPercussiveVoices;

            mVoiceData = new ArrayList<>(numVoices);
            for (int i = 0; i < numVoices; i++) {
                CVoiceData voice = new CVoiceData();

                inputStream.skip(ROL_FILLER_SIZE); // Voix
                loadNoteEvents(inputStream, voice);
                inputStream.skip(ROL_FILLER_SIZE); // Timbre
                loadInstrumentEvents(inputStream, voice, bnkInputStream, bnkHeader);
                inputStream.skip(ROL_FILLER_SIZE); // Volume
                loadVolumeEvents(inputStream, voice);
                inputStream.skip(ROL_FILLER_SIZE); // Pitch
                loadPitchEvents(inputStream, voice);

                mVoiceData.add(voice);
            }
        } finally {
            if (bnkInputStream != null) try {
                bnkInputStream.close();
            } catch (Exception ex) { /* NOOP */ }
        }
    }

    private void loadNoteEvents(RandomAccessInputStream inputStream, CVoiceData voice) throws IOException {
        List<SNoteEvent> noteEvents = voice.noteEvents = new ArrayList<>();

        int timeOfLastNote = inputStream.readIntelWord();
        int totalDuration = 0;

        while (totalDuration < timeOfLastNote && !checkForEvent(inputStream, FILLER_TIMBRE_SECTION)) {
            SNoteEvent event = SNoteEvent.readMe(inputStream);
            noteEvents.add(event);
            totalDuration += event.duration;
        }

        int newTimeOfLastNote = Math.min(totalDuration, timeOfLastNote);
        if (newTimeOfLastNote > mTimeOfLastNote) mTimeOfLastNote = newTimeOfLastNote;
    }

    private static void loadVolumeEvents(RandomAccessInputStream inputStream, CVoiceData voice) throws IOException {
        int numberOfVolumeEvents = inputStream.readIntelWord();

        voice.volumeEvents = new ArrayList<>(numberOfVolumeEvents);

        for (int i = 0; i < numberOfVolumeEvents && !checkForEvent(inputStream, FILLER_PITCH_SECTION); i++) {
            voice.volumeEvents.add(SVolumeEvent.readMe(inputStream));
        }
    }

    private void loadPitchEvents(RandomAccessInputStream inputStream, CVoiceData voice) throws IOException {
        int numberOfPitchEvents = inputStream.readIntelWord();

        voice.pitchEvents = new ArrayList<>(numberOfPitchEvents);

        for (int i = 0; i < numberOfPitchEvents && !checkForEvent(inputStream, FILLER_NOTE_SECTION); i++) {
            voice.pitchEvents.add(SPitchEvent.readMe(inputStream));
        }
    }

    private void loadInstrumentEvents(RandomAccessInputStream inputStream, CVoiceData voice, RandomAccessInputStreamImpl bnk_file, SBnkHeader bnk_header) throws IOException {
        int numberOfInstrumentEvents = inputStream.readIntelWord();

        voice.instrumentEvents = new ArrayList<>(numberOfInstrumentEvents);

        for (int i = 0; i < numberOfInstrumentEvents && !checkForEvent(inputStream, FILLER_VOLUME_SECTION); i++) {
            SInstrumentEvent event = SInstrumentEvent.readMe(inputStream);

            String event_name = event.name;
            event.insIndex = loadRolInstrument(bnk_file, bnk_header, event_name);

            voice.instrumentEvents.add(event);

            inputStream.skip(ROL_INSTRUMENT_EVENT_FILLER_SIZE);
        }
    }

    private int loadRolInstrument(RandomAccessInputStream bnkFile, SBnkHeader header, String name) throws IOException {
        List<SInstrumentName> ins_name_list = header.ins_name_list;

        int insIndex = getInstrumentIndex(name);

        if (insIndex != -1) return insIndex;

        SInstrument usedInstrument = new SInstrument();
        usedInstrument.name = name;

        SInstrumentName instrument = null;
        int size = ins_name_list.size();
        for (SInstrumentName ins : ins_name_list) {
            if (ins.name.equals(name)) {
                instrument = ins;
                break;
            }
        }

        if (instrument != null) {
            long seekOffs = header.absOffsetOfData + (instrument.index * kSizeofDataRecord);
            bnkFile.seek(seekOffs);
            usedInstrument.instrument = SRolInstrument.readMe(bnkFile);
        }

        mInstrumentList.add(usedInstrument);
        // index of newly added instrument
        return mInstrumentList.size() - 1;
    }

    private int getInstrumentIndex(String name) {
        int size = mInstrumentList.size();
        for (int index = 0; index < size; index++) {
            SInstrument instrument = mInstrumentList.get(index);
            if (instrument.name.equals(name)) return index;
        }
        return -1;
    }

    @Override
    public String getSongName() {
        return MultimediaContainerManager.getSongNameFromURL(rolFile);
    }

    @Override
    public String getAuthor() {
        return Helpers.EMPTY_STING;
    }

    @Override
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        if (mInstrumentList != null) {
            sb.append("Instruments used:").append('\n');
            for (SInstrument ins : mInstrumentList) {
                sb.append(ins.name).append('\n');
            }
        }
        return sb.toString();
    }

    @Override
    public String getTypeName() {
        if (mpROLHeader != null)
            return (mpROLHeader.comment.contains(EFFEKTER_MAGIC_STRING) ? "Effector V1.0 written" : "AdLib Composer written") + " AdLib ROL File V" + mpROLHeader.versionMajor + '.' + mpROLHeader.versionMinor;
        else
            return "AdLib ROL File (no ROL Header?!)";
    }

    @Override
    public OplType getOPLType() {
        return OplType.OPL2;
    }

    @Override
    public void setURL(URL url) {
        this.rolFile = url;
    }

    /**
     * @param bnkURL sound bank url
     * @since 05.08.2020
     */
    public void setBNKFile(URL bnkURL) {
        this.bnkFile = bnkURL;
    }

    // ----

    private void setRefresh(double multiplier) {
        mRefresh = ((double) mpROLHeader.ticksPerBeat * mpROLHeader.basicTempo * multiplier) / 60.0;
    }

    @Override
    public boolean updateToOPL(EmuOPL opl) {
        if ((mNextTempoEvent < mTempoEvents.size()) && (mTempoEvents.get(mNextTempoEvent).time == mCurrTick)) {
            setRefresh(mTempoEvents.get(mNextTempoEvent++).multiplier);
        }

        int anzVoices = mVoiceData.size();
        for (int voice = 0; voice < anzVoices; voice++)
            updateVoice(opl, voice, mVoiceData.get(voice));

        mCurrTick++;
        return mCurrTick <= mTimeOfLastNote;
    }

    private void updateVoice(EmuOPL opl, int voice, CVoiceData voiceData) {
        List<SInstrumentEvent> iEvents = voiceData.instrumentEvents;
        List<SVolumeEvent> vEvents = voiceData.volumeEvents;
        List<SNoteEvent> nEvents = voiceData.noteEvents;
        List<SPitchEvent> pEvents = voiceData.pitchEvents;

        if (voiceData.nextInstrumentEvent < iEvents.size()) {
            SInstrumentEvent instrumentEvent = iEvents.get(voiceData.nextInstrumentEvent);
            if (instrumentEvent.time == mCurrTick) {
                sendInsDataToChip(opl, voice, instrumentEvent.insIndex);
                voiceData.nextInstrumentEvent++;
            }
        }

        if (voiceData.nextVolumeEvent < vEvents.size()) {
            SVolumeEvent volumeEvent = vEvents.get(voiceData.nextVolumeEvent);
            if (volumeEvent.time == mCurrTick) {
                int volume = (int) (skMaxVolume * volumeEvent.multiplier);
                setVolume(opl, voice, volume);
                voiceData.nextVolumeEvent++; // move to next volume event
            }
        }

        if (voiceData.currentNoteDuration == voiceData.mNoteDuration) {
            if (voiceData.nextNoteEvent < nEvents.size()) {
                SNoteEvent noteEvent = nEvents.get(voiceData.nextNoteEvent);

                setNote(opl, voice, noteEvent.number);
                voiceData.currentNoteDuration = 0;
                voiceData.mNoteDuration = noteEvent.duration;
            } else {
                setNote(opl, voice, kSilenceNote);
            }
            voiceData.nextNoteEvent++;
        }
        voiceData.currentNoteDuration++;

        if (voiceData.nextPitchEvent < pEvents.size()) {
            SPitchEvent pitchEvent = pEvents.get(voiceData.nextPitchEvent);
            if (pitchEvent.time == mCurrTick) {
                setPitch(opl, voice, pitchEvent.variation);
                voiceData.nextPitchEvent++;
            }
        }
    }

    private void setNote(EmuOPL opl, int voice, int note) {
        if (voice < kBassDrumChannel || mpROLHeader.mode != 0) {
            setNoteMelodic(opl, voice, note);
        } else {
            setNotePercussive(opl, voice, note);
        }
    }

    private void setNotePercussive(EmuOPL opl, int voice, int note) {
        int channel_bit_mask = 1 << (4 - voice + kBassDrumChannel);

        mAMVibRhythmCache &= ~channel_bit_mask;
        opl.writeOPL2(skOPL2_AmVibRhythmBaseAddress, mAMVibRhythmCache);
        mKeyOnCache[voice] = false;

        if (note != kSilenceNote) {
            switch (voice) {
                case kTomtomChannel:
                    setFreq(opl, kTomtomChannel, note);
                    setFreq(opl, kSnareDrumChannel, note + kTomTomToSnare);
                    break;

                case kBassDrumChannel:
                    setFreq(opl, voice, note);
                    break;
                default:
                    // Does nothing
                    break;
            }

            mKeyOnCache[voice] = true;
            mAMVibRhythmCache |= channel_bit_mask;
            opl.writeOPL2(skOPL2_AmVibRhythmBaseAddress, mAMVibRhythmCache);
        }
    }

    private void setNoteMelodic(EmuOPL opl, int voice, int note) {
        opl.writeOPL2(skOPL2_KeyOnFreqHiBaseAddress + voice, mKOnOctFNumCache[voice] & ~skOPL2_KeyOnMask);
        mKeyOnCache[voice] = false;

        if (note != kSilenceNote) {
            setFreq(opl, voice, note, true);
        }
    }

    // From Adlib Music SDK's ADLIB.C ...
    private void changePitch(int voice, int pitchBend) {
        int pitchBendLength = (pitchBend - skMidPitch) * mPitchRangeStep;

        if (mOldPitchBendLength == pitchBendLength) {
            // Optimization ...
            mFNumFreqPtrList[voice] = mpOldFNumFreqPtr;
            mHalfToneOffset[voice] = mOldHalfToneOffset;
        } else {
            int pitchStepDir = pitchBendLength / skMidPitch;
            int delta;
            if (pitchStepDir < 0) {
                int pitchStepDown = skNrStepPitch - 1 - pitchStepDir;
                mOldHalfToneOffset = mHalfToneOffset[voice] = -(pitchStepDown / skNrStepPitch);
                delta = (pitchStepDown - skNrStepPitch + 1) % skNrStepPitch;
                if (delta != 0) {
                    delta = skNrStepPitch - delta;
                }
            } else {
                mOldHalfToneOffset = mHalfToneOffset[voice] = pitchStepDir / skNrStepPitch;
                delta = pitchStepDir % skNrStepPitch;
            }
            mpOldFNumFreqPtr = mFNumFreqPtrList[voice] = delta;
            mOldPitchBendLength = pitchBendLength;
        }
    }

    private void setPitch(EmuOPL opl, int voice, double variation) {
        if (voice < kBassDrumChannel || mpROLHeader.mode != 0) {
            int pitchBend = (variation == 1.0) ? skMidPitch : (int) ((0x3fff >> 1) * variation);
            changePitch(voice, pitchBend);
            setFreq(opl, voice, mNoteCache[voice], mKeyOnCache[voice]);
        }
    }

    private void setFreq(EmuOPL opl, int voice, int note) {
        setFreq(opl, voice, note, false);
    }

    private void setFreq(EmuOPL opl, int voice, int note, boolean keyOn) {
        int biasedNote = note + mHalfToneOffset[voice];
        if (biasedNote < 0) biasedNote = 0;
        else if (biasedNote >= skMaxNotes) biasedNote = skMaxNotes - 1;

        mNoteCache[voice] = note;
        mKeyOnCache[voice] = keyOn;

        int octave = biasedNote / skNumSemitonesInOctave;
        int noteIndex = biasedNote % skNumSemitonesInOctave;

        int frequency = skFNumNotes[mFNumFreqPtrList[voice] * skNumSemitonesInOctave + noteIndex];
        mKOnOctFNumCache[voice] = (octave << skOPL2_BlockNumberShift) | ((frequency >> skOPL2_FNumMSBShift) & skOPL2_FNumMSBMask);

        opl.writeOPL2(skOPL2_FreqLoBaseAddress + voice, frequency & skOPL2_FNumLSBMask);
        opl.writeOPL2(skOPL2_KeyOnFreqHiBaseAddress + voice, mKOnOctFNumCache[voice] | (keyOn ? skOPL2_KeyOnMask : 0x0));
    }

    private int getKSLTL(int voice) {
        int baseVolume = skOPL2_TLMinLevel - (mKSLTLCache[voice] & skOPL2_TLMask); // max amplitude from instrument setting
        int newVolume = ((baseVolume * mVolumeCache[voice]) + (1 << (skVolumeQualityShift - 1))) >> skVolumeQualityShift;
        // clamp it
        if (newVolume < 0) newVolume = 0;
        else if (newVolume > skOPL2_TLMinLevel) newVolume = skOPL2_TLMinLevel;
        // rebuild register output with old KSL plus new TL (logic vice verca: 0= maxvolume, 0x3F= minimum)
        return (mKSLTLCache[voice] & skOPL2_KSLMask) | ((skOPL2_TLMinLevel - newVolume) & 0x3F);
    }

    private void setVolume(EmuOPL opl, int voice, int volume) {
        int opOffset = (voice < kSnareDrumChannel || mpROLHeader.mode != 0) ? op_table[voice] + skCarrierOpOffset : drum_op_table[voice - kSnareDrumChannel];

        mVolumeCache[voice] = volume;

        opl.writeOPL2(skOPL2_KSLTLBaseAddress + opOffset, getKSLTL(voice));
    }

    private void sendInsDataToChip(EmuOPL opl, int voice, int ins_index) {
        SRolInstrument instrument = mInstrumentList.get(ins_index).instrument;

        sendOperator(opl, voice, instrument.modulator, instrument.carrier);
    }

    private void sendOperator(EmuOPL opl, int voice, SOPL2Op modulator, SOPL2Op carrier) {
        if (voice < kSnareDrumChannel || mpROLHeader.mode != 0) {
            int opOffset = op_table[voice];

            opl.writeOPL2(skOPL2_AaMultiBaseAddress + opOffset, modulator.ammulti);
            opl.writeOPL2(skOPL2_KSLTLBaseAddress + opOffset, modulator.ksltl);
            opl.writeOPL2(skOPL2_ArDrBaseAddress + opOffset, modulator.ardr);
            opl.writeOPL2(skOPL2_SlrrBaseAddress + opOffset, modulator.slrr);
            opl.writeOPL2(skOPL2_FeedConBaseAddress + voice, modulator.fbc);
            opl.writeOPL2(skOPL2_WaveformBaseAddress + opOffset, modulator.waveform);

            mKSLTLCache[voice] = carrier.ksltl;

            opl.writeOPL2(skOPL2_AaMultiBaseAddress + opOffset + skCarrierOpOffset, carrier.ammulti);
            opl.writeOPL2(skOPL2_KSLTLBaseAddress + opOffset + skCarrierOpOffset, getKSLTL(voice));
            opl.writeOPL2(skOPL2_ArDrBaseAddress + opOffset + skCarrierOpOffset, carrier.ardr);
            opl.writeOPL2(skOPL2_SlrrBaseAddress + opOffset + skCarrierOpOffset, carrier.slrr);
            opl.writeOPL2(skOPL2_WaveformBaseAddress + opOffset + skCarrierOpOffset, carrier.waveform);
        } else {
            int op_offset = drum_op_table[voice - kSnareDrumChannel];

            mKSLTLCache[voice] = modulator.ksltl;

            opl.writeOPL2(skOPL2_AaMultiBaseAddress + op_offset, modulator.ammulti);
            opl.writeOPL2(skOPL2_KSLTLBaseAddress + op_offset, getKSLTL(voice));
            opl.writeOPL2(skOPL2_ArDrBaseAddress + op_offset, modulator.ardr);
            opl.writeOPL2(skOPL2_SlrrBaseAddress + op_offset, modulator.slrr);
            opl.writeOPL2(skOPL2_WaveformBaseAddress + op_offset, modulator.waveform);
        }
    }

    @Override
    public void initialize(EmuOPL opl) {
        for (CVoiceData voice : mVoiceData)
            voice.reset();

        Arrays.fill(mHalfToneOffset, 0);
        Arrays.fill(mVolumeCache, skMaxVolume);
        Arrays.fill(mKSLTLCache, 0);
        Arrays.fill(mNoteCache, 0);
        Arrays.fill(mKOnOctFNumCache, 0);
        Arrays.fill(mKeyOnCache, false);

        mNextTempoEvent = 0;
        mCurrTick = 0;
        mAMVibRhythmCache = 0;

        resetOPL(opl);
        opl.writeOPL2(skOPL2_WaveCtrlBaseAddress, skOPL2_EnableWaveformSelectMask); // Enable waveform select

        if (mpROLHeader.mode == 0) {
            mAMVibRhythmCache = skOPL2_RhythmMask;
            opl.writeOPL2(skOPL2_AmVibRhythmBaseAddress, mAMVibRhythmCache); // Enable rhythm mode

            setFreq(opl, kTomtomChannel, kTomTomNote);
            setFreq(opl, kSnareDrumChannel, kSnareNote);
        }

        setRefresh(1.0f);
    }

    @Override
    public double getRefresh() {
        return mRefresh;
    }
}
