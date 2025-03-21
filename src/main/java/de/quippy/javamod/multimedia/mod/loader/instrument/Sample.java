/*
 * @(#) Sample.java
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

package de.quippy.javamod.multimedia.mod.loader.instrument;

import de.quippy.javamod.multimedia.mod.ModConstants;
import de.quippy.javamod.multimedia.mod.mixer.interpolation.CubicSpline;
import de.quippy.javamod.multimedia.mod.mixer.interpolation.Kaiser;
import de.quippy.javamod.multimedia.mod.mixer.interpolation.WindowedFIR;
import de.quippy.javamod.system.Helpers;


/**
 * Used to store the Instruments
 *
 * @author Daniel Becker
 * @since 21.04.2006
 */
public class Sample {

    /** Name of the sample */
    public String name;
    /** full length (already *2 --> Mod-Format) */
    public int length;
    /** normalized loading flags (signed, unsigned, 8-Bit, compressed, ...) */
    public int sampleType;
    /** Finetuning -8..+8 */
    public int fineTune;
    /** Basisvolume */
    public int volume;
    /** # of the loop start (already *2 --> Mod-Fomat) */
    public int loopStart;
    /** # of the loop end   (already *2 --> Mod-Fomat) */
    public int loopStop;
    /** length of the loop */
    public int loopLength;
    /** 0: no Looping, 1: normal, 2: Sustain, 4: pingpong 8: Sustain pingpong */
    public int loopType;
    /** PatternNote + transpose */
    public int transpose;
    /** BaseFrequency */
    public int baseFrequency;
    /** true, if this is a stereo-sample */
    public boolean isStereo;

    //S3M:
    /** always 1 for a sample, 1-7 AdLib (2:Melody 3:Basedrum 4:Snare 5:Tom 6:Cym 7:HiHat) */
    public int type;
    /** DOS File-Name */
    public String dosFileName;
    /** flag: 1:Looping sample 2:Stereo 4:16Bit-Sample... */
    public int flags;

    // XM
    /** set the panning */
    public boolean setPanning;
    /** default Panning */
    public int defaultPanning;
    /** Vibrato Type */
    public int vibratoType;
    /** Vibrato Sweep */
    public int vibratoSweep;
    /** Vibrato Depth */
    public int vibratoDepth;
    /** Vibrato Rate */
    public int vibratoRate;
    /** reserved, but some magic with 0xAD and SM_ADPCM4... */
    public int XM_reserved;

    // IT
    /** SustainLoopStart */
    public int sustainLoopStart;
    /** SustainLoopEnd */
    public int sustainLoopStop;
    /** SustainLoop Length */
    public int sustainLoopLength;
    /** Flag for Instrument Save */
    public int flag_CvT;
    /** GlobalVolume */
    public int globalVolume;

    // Interpolation Magic
    private int interpolationStopLoop;
    private int interpolationStopSustain;
    private int interpolationStartLoop;
    private int interpolationStartSustain;

    // If this is adlib...
    public byte[] adLib_Instrument;

    // MPT specific cue points
    private int[] cues;
    public static final int MAX_CUES = 9;

    public static final int INTERPOLATION_LOOK_AHEAD = 16;

    // The sample data, already converted to signed 32 bit (always)
    // 8Bit: 0..127,128-255; 16Bit: -32768..0..+32767
    public long[] sampleL;
    public long[] sampleR;

    /**
     * Constructor for Sample
     */
    public Sample() {
        super();
        isStereo = false;
    }

    /**
     * Allocate the sample data inclusive interpolation look ahead buffers
     *
     * @since 03.07.2020
     */
    public void allocSampleData() {
        int alloc = length + ((1 + 1 + 4 + 4 + 4 + 4) * INTERPOLATION_LOOK_AHEAD);
        sampleL = new long[alloc];
        if (isStereo) sampleR = new long[alloc];
        else sampleR = null;
    }

    /**
     * Fits the loop-data given in instruments loaded
     * These values are often not correct
     * Furthermore we add sample data for interpolation
     *
     * @param modType
     * @since 27.08.2006
     */
    public void fixSampleLoops(int modType) {
        if (sampleL == null || length == 0) {
            loopType = loopLength = loopStop = loopStart =
                    sustainLoopLength = sustainLoopStart = sustainLoopStop = 0;
            return;
        }
        // A sample point index greater than the array index
        // needs to be allowed (! >=)
        if (loopStop > length) loopStop = length;
        if (loopStart < 0) loopStart = 0;
        loopLength = loopStop - loopStart;

        if (sustainLoopStop > length) sustainLoopStop = length;
        if (sustainLoopStart < 0) sustainLoopStart = 0;
        sustainLoopLength = sustainLoopStop - sustainLoopStart;

        // Kill invalid loops
        // with protracker, a loopsize of 2 is considered invalid
        if (((modType & ModConstants.MODTYPE_MOD) != 0 && loopStart + 2 > loopStop) ||
                loopStart > loopStop ||
                loopLength <= 0) {
            loopStart = loopStop = 0;
            loopType &= ~ModConstants.LOOP_ON;
        }
        if (sustainLoopStart > sustainLoopStop ||
                sustainLoopLength <= 0) {
            sustainLoopStart = sustainLoopStop = 0;
            loopType &= ~ModConstants.LOOP_SUSTAIN_ON;
        }

        addInterpolationLookAheadData();
    }

    /**
     * We copy now for a loop - for short Loops we need to simulate it
     *
     * @param startIndex
     * @param length
     * @param isPingPong
     * @since 03.07.2020
     */
    private void addInterpolationLookAheadDataLoop(int startIndex, int length, int sourceIndex, boolean isForward, boolean isPingPong, boolean forLoopEnd) {
        int numSamples = 2 * INTERPOLATION_LOOK_AHEAD + ((isForward && forLoopEnd) || (!isForward && !forLoopEnd) ? 1 : 0);
        int destIndex = startIndex + (2 * INTERPOLATION_LOOK_AHEAD) - (forLoopEnd ? 1 : 0);
        int readPosition = (forLoopEnd) ? length - 1 : 0;
        int writeIncrement = isForward ? 1 : -1;
        int readIncrement = writeIncrement;

        for (int i = 0; i < numSamples; i++) {
            sampleL[destIndex] = sampleL[sourceIndex + readPosition];
            if (sampleR != null) sampleR[destIndex] = sampleR[sourceIndex + readPosition];
            destIndex += writeIncrement;

            if (readPosition == length - 1 && readIncrement > 0) {
                if (isPingPong) {
                    readIncrement = -1;
                    //if (readPosition>0) readPosition--;
                } else
                    readPosition = 0;
            } else if (readPosition == 0 && readIncrement < 0) {
                if (isPingPong) {
                    readIncrement = 1;
                    //readPosition++;
                } else
                    readPosition = length - 1;
            } else
                readPosition += readIncrement;
        }
    }

    /**
     * @since 03.07.2020
     */
    private void addInterpolationLookAheadData() {
        // At the end, we want to have
        // [PRE | sample data | POST | 4x endLoop | 4x endSustain]

        final int startSampleData = INTERPOLATION_LOOK_AHEAD;
        int afterSampleData = startSampleData + length;
        interpolationStopLoop = afterSampleData + INTERPOLATION_LOOK_AHEAD;
        interpolationStopSustain = interpolationStopLoop + (4 * INTERPOLATION_LOOK_AHEAD);
        interpolationStartLoop = interpolationStopSustain + (4 * INTERPOLATION_LOOK_AHEAD);
        interpolationStartSustain = interpolationStartLoop + (4 * INTERPOLATION_LOOK_AHEAD);

        // First move sampleData out of the way, as it is loaded at index 0
        for (int pos = length - 1; pos >= 0; pos--) {
            sampleL[startSampleData + pos] = sampleL[pos];
            if (sampleR != null) sampleR[startSampleData + pos] = sampleR[pos];
        }

        // now add sample data in PRE and POST
        for (int pos = 0; pos < INTERPOLATION_LOOK_AHEAD; pos++) {
            sampleL[afterSampleData + pos] = sampleL[afterSampleData - 1];
            if (sampleR != null) sampleR[afterSampleData + pos] = sampleR[afterSampleData - 1];
            sampleL[pos] = sampleL[startSampleData];
            if (sampleR != null) sampleR[pos] = sampleR[startSampleData];

        }

        if ((loopType & ModConstants.LOOP_ON) != 0) {
            addInterpolationLookAheadDataLoop(interpolationStopLoop, loopLength, loopStart + INTERPOLATION_LOOK_AHEAD, true, (loopType & ModConstants.LOOP_IS_PINGPONG) != 0, true);
            addInterpolationLookAheadDataLoop(interpolationStopLoop, loopLength, loopStart + INTERPOLATION_LOOK_AHEAD, false, (loopType & ModConstants.LOOP_IS_PINGPONG) != 0, true);
            addInterpolationLookAheadDataLoop(interpolationStartLoop, loopLength, loopStart + INTERPOLATION_LOOK_AHEAD, true, (loopType & ModConstants.LOOP_IS_PINGPONG) != 0, false);
            addInterpolationLookAheadDataLoop(interpolationStartLoop, loopLength, loopStart + INTERPOLATION_LOOK_AHEAD, false, (loopType & ModConstants.LOOP_IS_PINGPONG) != 0, false);
        }
        if ((loopType & ModConstants.LOOP_SUSTAIN_ON) != 0) {
            addInterpolationLookAheadDataLoop(interpolationStopSustain, sustainLoopLength, sustainLoopStart + INTERPOLATION_LOOK_AHEAD, true, (loopType & ModConstants.LOOP_IS_PINGPONG) != 0, true);
            addInterpolationLookAheadDataLoop(interpolationStopSustain, sustainLoopLength, sustainLoopStart + INTERPOLATION_LOOK_AHEAD, false, (loopType & ModConstants.LOOP_IS_PINGPONG) != 0, true);
            addInterpolationLookAheadDataLoop(interpolationStartSustain, sustainLoopLength, sustainLoopStart + INTERPOLATION_LOOK_AHEAD, true, (loopType & ModConstants.LOOP_IS_PINGPONG) != 0, false);
            addInterpolationLookAheadDataLoop(interpolationStartSustain, sustainLoopLength, sustainLoopStart + INTERPOLATION_LOOK_AHEAD, false, (loopType & ModConstants.LOOP_IS_PINGPONG) != 0, false);
        }
    }

    /**
     * returns a new index into samples if currentSamplePos
     * is too near loop end or loop start
     *
     * @param currentSamplePos
     * @return
     * @since 03.07.2020
     */
    public int getSustainLoopMagic(int currentSamplePos, boolean inLoop) {
        if (currentSamplePos + INTERPOLATION_LOOK_AHEAD >= sustainLoopStop) // approaching sustainLoopStop?
            return interpolationStopSustain - sustainLoopStop + (INTERPOLATION_LOOK_AHEAD << 1);
        else if (currentSamplePos - INTERPOLATION_LOOK_AHEAD <= sustainLoopStart && inLoop) // approaching/leaving sustainLoopStart?
            return interpolationStartSustain - sustainLoopStart + (INTERPOLATION_LOOK_AHEAD << 1);
        else
            return 0;
    }

    /**
     * returns a new index into samples if currentSamplePos
     * is too near loop end or loop start
     *
     * @param currentSamplePos
     * @return
     * @since 03.07.2020
     */
    public int getLoopMagic(int currentSamplePos, boolean inLoop) {
        if (currentSamplePos + INTERPOLATION_LOOK_AHEAD >= loopStop)  // approaching loopStop?
            return interpolationStopLoop - loopStop + (INTERPOLATION_LOOK_AHEAD << 1);
        else if (currentSamplePos - INTERPOLATION_LOOK_AHEAD <= loopStart && inLoop) // approaching/leaving LoopStart?
            return interpolationStartLoop - loopStart + (INTERPOLATION_LOOK_AHEAD << 1);
        else
            return 0;
    }

    /**
     * @return true, if this sample as any sample data. That is, if at least
     * the left buffer (mono sample) is not null and has a length>0
     * @since 12.03.2024
     */
    public boolean hasSampleData() {
        return (sampleL != null && sampleL.length > 0);
    }

    /**
     * @return
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        String bf = ((name == null) ? Helpers.EMPTY_STING : name) + '(' +
                getSampleTypeString() + ", " +
                "fineTune:" + fineTune + ", " +
                "transpose:" + transpose + ", " +
                "baseFrequency:" + baseFrequency + ", " +
                "volume:" + volume + ", " +
                "set panning:" + setPanning + ", " +
                "panning:" + defaultPanning + ", " +
                "loopStart:" + loopStart + ", " +
                "loopStop:" + loopStop + ", " +
                "loopLength:" + loopLength + ", " +
                "SustainloopStart:" + sustainLoopStart + ", " +
                "SustainloopStop:" + sustainLoopStop + ", " +
                "SustainloopLength:" + sustainLoopLength + ')';

        return bf;
    }

    public String toShortString() {
        return this.name;
    }

    /**
     * @return a String representing of the loading factors
     * @since 31.07.2020
     */
    public String getSampleTypeString() {
        if (adLib_Instrument != null) return "OPL Instrument";

        String bf = ((sampleType & ModConstants.SM_16BIT) != 0 ? "16-Bit" : "8-Bit") + ", " +
                ((sampleType & ModConstants.SM_BigEndian) != 0 ? "big" : "little") + " endian, " +
                ((sampleType & ModConstants.SM_PCMU) != 0 ? "unsigned" : "signed") + ", " +
                ((sampleType & ModConstants.SM_PCMD) != 0 ? "delta packed" :
                        (sampleType & ModConstants.SM_IT214) != 0 ? "IT V2.14 packed" :
                                (sampleType & ModConstants.SM_IT215) != 0 ? "IT V2.15 packed" :
                                        (sampleType & ModConstants.SM_ADPCM) != 0 ? "ADPCM packed" :
                                                "unpacked") +
                ", " +
                ((sampleType & ModConstants.SM_STEREO) != 0 ? "stereo" : "mono") + ", " +
                "length: " + length;
        return bf;
    }

    /**
     * Does the linear interpolation with the next sample
     *
     * @param result
     * @param currentSamplePos
     * @param currentTuningPos
     * @param isBackwards
     * @since 06.06.2006
     */
    private void getLinearInterpolated(long[] result, int currentSamplePos, int currentTuningPos, boolean isBackwards) {
        long s1 = sampleL[currentSamplePos] << ModConstants.SAMPLE_SHIFT;
        long s2 =
                (isBackwards) ?
                        sampleL[currentSamplePos - 1] << ModConstants.SAMPLE_SHIFT
                        :
                        sampleL[currentSamplePos + 1] << ModConstants.SAMPLE_SHIFT;
        result[0] = (s1 + (((s2 - s1) * ((long) currentTuningPos)) >> ModConstants.SHIFT)) >> ModConstants.SAMPLE_SHIFT;

        if (sampleR != null) {
            s1 = sampleR[currentSamplePos] << ModConstants.SAMPLE_SHIFT;
            s2 =
                    (isBackwards) ?
                            sampleR[currentSamplePos - 1] << ModConstants.SAMPLE_SHIFT
                            :
                            sampleR[currentSamplePos + 1] << ModConstants.SAMPLE_SHIFT;
            result[1] = (s1 + (((s2 - s1) * ((long) currentTuningPos)) >> ModConstants.SHIFT)) >> ModConstants.SAMPLE_SHIFT;
        } else
            result[1] = result[0];
    }

    /**
     * does cubic interpolation with the next sample
     *
     * @param result
     * @param currentSamplePos
     * @param currentTuningPos
     * @param isBackwards
     * @since 06.06.2006
     */
    private void getCubicInterpolated(long[] result, int currentSamplePos, int currentTuningPos, boolean isBackwards) {
        int poslo = (currentTuningPos >> CubicSpline.SPLINE_FRACSHIFT) & CubicSpline.SPLINE_FRACMASK;

        long v1 =
                (isBackwards) ?
                        ((long) CubicSpline.lut[poslo] * sampleL[currentSamplePos + 1]) +
                                ((long) CubicSpline.lut[poslo + 1] * sampleL[currentSamplePos]) +
                                ((long) CubicSpline.lut[poslo + 2] * sampleL[currentSamplePos - 1]) +
                                ((long) CubicSpline.lut[poslo + 3] * sampleL[currentSamplePos - 2])
                        :
                        ((long) CubicSpline.lut[poslo] * sampleL[currentSamplePos - 1]) +
                                ((long) CubicSpline.lut[poslo + 1] * sampleL[currentSamplePos]) +
                                ((long) CubicSpline.lut[poslo + 2] * sampleL[currentSamplePos + 1]) +
                                ((long) CubicSpline.lut[poslo + 3] * sampleL[currentSamplePos + 2]);
        result[0] = v1 >> CubicSpline.SPLINE_QUANTBITS;

        if (sampleR != null) {
            v1 =
                    (isBackwards) ?
                            ((long) CubicSpline.lut[poslo] * sampleR[currentSamplePos + 1]) +
                                    ((long) CubicSpline.lut[poslo + 1] * sampleR[currentSamplePos]) +
                                    ((long) CubicSpline.lut[poslo + 2] * sampleR[currentSamplePos - 1]) +
                                    ((long) CubicSpline.lut[poslo + 3] * sampleR[currentSamplePos - 2])
                            :
                            ((long) CubicSpline.lut[poslo] * sampleR[currentSamplePos - 1]) +
                                    ((long) CubicSpline.lut[poslo + 1] * sampleR[currentSamplePos]) +
                                    ((long) CubicSpline.lut[poslo + 2] * sampleR[currentSamplePos + 1]) +
                                    ((long) CubicSpline.lut[poslo + 3] * sampleR[currentSamplePos + 2]);
            result[1] = v1 >> CubicSpline.SPLINE_QUANTBITS;
        } else
            result[1] = result[0];
    }

    /**
     * does a Kaiser Window interpolation with the next sample
     *
     * @param result
     * @param currentSamplePos
     * @param currentTuningPos
     * @param isBackwards
     * @since 21.02.2024
     */
    private void getKaiserInterpolated(long[] result, int currentIncrement, int currentSamplePos, int currentTuningPos, boolean isBackwards) {
        int poslo = ((currentTuningPos >> Kaiser.SINC_FRACSHIFT) & Kaiser.SINC_MASK) * Kaiser.SINC_WIDTH;
        // Why MPT does this and where this specific borders come from - beyond my knowledge - but, well...
        int[] sinc = (currentIncrement > Kaiser.gDownsample2x_Limit) ? Kaiser.gDownsample2x :
                (currentIncrement > Kaiser.gDownsample13x_Limit) ? Kaiser.gDownsample13x :
                        Kaiser.gKaiserSinc;

        long v1 =
                (isBackwards) ?
                        ((long) sinc[poslo] * sampleL[currentSamplePos + 3]) +
                                ((long) sinc[poslo + 1] * sampleL[currentSamplePos + 2]) +
                                ((long) sinc[poslo + 2] * sampleL[currentSamplePos + 1]) +
                                ((long) sinc[poslo + 3] * sampleL[currentSamplePos]) +
                                ((long) sinc[poslo + 4] * sampleL[currentSamplePos - 1]) +
                                ((long) sinc[poslo + 5] * sampleL[currentSamplePos - 2]) +
                                ((long) sinc[poslo + 6] * sampleL[currentSamplePos - 3]) +
                                ((long) sinc[poslo + 7] * sampleL[currentSamplePos - 4])
                        :
                        ((long) sinc[poslo] * sampleL[currentSamplePos - 3]) +
                                ((long) sinc[poslo + 1] * sampleL[currentSamplePos - 2]) +
                                ((long) sinc[poslo + 2] * sampleL[currentSamplePos - 1]) +
                                ((long) sinc[poslo + 3] * sampleL[currentSamplePos]) +
                                ((long) sinc[poslo + 4] * sampleL[currentSamplePos + 1]) +
                                ((long) sinc[poslo + 5] * sampleL[currentSamplePos + 2]) +
                                ((long) sinc[poslo + 6] * sampleL[currentSamplePos + 3]) +
                                ((long) sinc[poslo + 7] * sampleL[currentSamplePos + 4]);
        result[0] = v1 >> Kaiser.SINC_QUANTSHIFT;

        if (sampleR != null) {
            v1 =
                    (isBackwards) ?
                            ((long) sinc[poslo] * sampleR[currentSamplePos + 3]) +
                                    ((long) sinc[poslo + 1] * sampleR[currentSamplePos + 2]) +
                                    ((long) sinc[poslo + 2] * sampleR[currentSamplePos + 1]) +
                                    ((long) sinc[poslo + 3] * sampleR[currentSamplePos]) +
                                    ((long) sinc[poslo + 4] * sampleR[currentSamplePos - 1]) +
                                    ((long) sinc[poslo + 5] * sampleR[currentSamplePos - 2]) +
                                    ((long) sinc[poslo + 6] * sampleR[currentSamplePos - 3]) +
                                    ((long) sinc[poslo + 7] * sampleR[currentSamplePos - 4])
                            :
                            ((long) sinc[poslo] * sampleR[currentSamplePos - 3]) +
                                    ((long) sinc[poslo + 1] * sampleR[currentSamplePos - 2]) +
                                    ((long) sinc[poslo + 2] * sampleR[currentSamplePos - 1]) +
                                    ((long) sinc[poslo + 3] * sampleR[currentSamplePos]) +
                                    ((long) sinc[poslo + 4] * sampleR[currentSamplePos + 1]) +
                                    ((long) sinc[poslo + 5] * sampleR[currentSamplePos + 2]) +
                                    ((long) sinc[poslo + 6] * sampleR[currentSamplePos + 3]) +
                                    ((long) sinc[poslo + 7] * sampleR[currentSamplePos + 4]);
            result[1] = v1 >> Kaiser.SINC_QUANTSHIFT;
        } else
            result[1] = result[0];
    }

    /**
     * does a windowed fir interpolation with the next sample
     *
     * @param currentTuningPos
     * @return
     * @since 21.02.2024
     */
    private void getFIRInterpolated(long[] result, int currentSamplePos, int currentTuningPos, boolean isBackwards) {
        int poslo = ((currentTuningPos + WindowedFIR.WFIR_FRACHALVE) >> WindowedFIR.WFIR_FRACSHIFT) & WindowedFIR.WFIR_FRACMASK;

        long v1 =
                (isBackwards) ?
                        ((long) WindowedFIR.lut[poslo] * sampleL[currentSamplePos + 3]) +
                                ((long) WindowedFIR.lut[poslo + 1] * sampleL[currentSamplePos + 2]) +
                                ((long) WindowedFIR.lut[poslo + 2] * sampleL[currentSamplePos + 1]) +
                                ((long) WindowedFIR.lut[poslo + 3] * sampleL[currentSamplePos])
                        :
                        ((long) WindowedFIR.lut[poslo] * sampleL[currentSamplePos - 3]) +
                                ((long) WindowedFIR.lut[poslo + 1] * sampleL[currentSamplePos - 2]) +
                                ((long) WindowedFIR.lut[poslo + 2] * sampleL[currentSamplePos - 1]) +
                                ((long) WindowedFIR.lut[poslo + 3] * sampleL[currentSamplePos]);
        long v2 =
                (isBackwards) ?
                        ((long) WindowedFIR.lut[poslo + 4] * sampleL[currentSamplePos - 1]) +
                                ((long) WindowedFIR.lut[poslo + 5] * sampleL[currentSamplePos - 2]) +
                                ((long) WindowedFIR.lut[poslo + 6] * sampleL[currentSamplePos - 3]) +
                                ((long) WindowedFIR.lut[poslo + 7] * sampleL[currentSamplePos - 4])
                        :
                        ((long) WindowedFIR.lut[poslo + 4] * sampleL[currentSamplePos + 1]) +
                                ((long) WindowedFIR.lut[poslo + 5] * sampleL[currentSamplePos + 2]) +
                                ((long) WindowedFIR.lut[poslo + 6] * sampleL[currentSamplePos + 3]) +
                                ((long) WindowedFIR.lut[poslo + 7] * sampleL[currentSamplePos + 4]);
        result[0] = (v1 >> 1) + (v2 >> 1) >> (WindowedFIR.WFIR_QUANTBITS - 1);

        if (sampleR != null) {
            v1 =
                    (isBackwards) ?
                            ((long) WindowedFIR.lut[poslo] * sampleR[currentSamplePos + 3]) +
                                    ((long) WindowedFIR.lut[poslo + 1] * sampleR[currentSamplePos + 2]) +
                                    ((long) WindowedFIR.lut[poslo + 2] * sampleR[currentSamplePos + 1]) +
                                    ((long) WindowedFIR.lut[poslo + 3] * sampleR[currentSamplePos])
                            :
                            ((long) WindowedFIR.lut[poslo] * sampleR[currentSamplePos - 3]) +
                                    ((long) WindowedFIR.lut[poslo + 1] * sampleR[currentSamplePos - 2]) +
                                    ((long) WindowedFIR.lut[poslo + 2] * sampleR[currentSamplePos - 1]) +
                                    ((long) WindowedFIR.lut[poslo + 3] * sampleR[currentSamplePos]);
            v2 =
                    (isBackwards) ?
                            ((long) WindowedFIR.lut[poslo + 4] * sampleR[currentSamplePos - 1]) +
                                    ((long) WindowedFIR.lut[poslo + 5] * sampleR[currentSamplePos - 2]) +
                                    ((long) WindowedFIR.lut[poslo + 6] * sampleR[currentSamplePos - 3]) +
                                    ((long) WindowedFIR.lut[poslo + 7] * sampleR[currentSamplePos - 4])
                            :
                            ((long) WindowedFIR.lut[poslo + 4] * sampleR[currentSamplePos + 1]) +
                                    ((long) WindowedFIR.lut[poslo + 5] * sampleR[currentSamplePos + 2]) +
                                    ((long) WindowedFIR.lut[poslo + 6] * sampleR[currentSamplePos + 3]) +
                                    ((long) WindowedFIR.lut[poslo + 7] * sampleR[currentSamplePos + 4]);
            result[1] = (v1 >> 1) + (v2 >> 1) >> (WindowedFIR.WFIR_QUANTBITS - 1);
        } else
            result[1] = result[0];
    }

    /**
     * Update 14.06.2020 (too late): with bidi Loops, interpolation direction
     * lookahead must change.
     *
     * @return Returns the sample using desired interpolation.
     * @since 15.06.2006
     */
    public void getInterpolatedSample(long[] result, int doISP, int currentIncrement, int currentSamplePos, int currentTuningPos, boolean isBackwards, int interpolationMagic) {
        // Shit happens... indeed! Test is <=length because for XM PingPong we run into our added sample data (ridiculous, but that's how it is...)
        if (currentIncrement > 0 && hasSampleData()/* && currentSamplePos<=length*/) {
            int sampleIndex = currentSamplePos + ((interpolationMagic == 0) ? INTERPOLATION_LOOK_AHEAD : interpolationMagic);
            // Now return correct sample
            switch (doISP) {
                case ModConstants.INTERPOLATION_NONE:
                    result[0] = sampleL[sampleIndex];
                    result[1] = (sampleR != null) ? sampleR[sampleIndex] : result[0];
                    break;
                case ModConstants.INTERPOLATION_LINEAR:
                    getLinearInterpolated(result, sampleIndex, currentTuningPos, isBackwards);
                    break;
                case ModConstants.INTERPOLATION_CUBIC:
                    getCubicInterpolated(result, sampleIndex, currentTuningPos, isBackwards);
                    break;
                case ModConstants.INTERPOLATION_KAISER:
                    getKaiserInterpolated(result, currentIncrement, sampleIndex, currentTuningPos, isBackwards);
                    break;
                default:
                case ModConstants.INTERPOLATION_WINDOWSFIR:
                    getFIRInterpolated(result, sampleIndex, currentTuningPos, isBackwards);
                    break;
            }
        } else
            result[0] = result[1] = 0;
    }

    /**
     * @param baseFrequency The baseFrequency to set.
     */
    public void setBaseFrequency(int baseFrequency) {
        this.baseFrequency = baseFrequency;
    }

    /**
     * @param dosFileName The dosFileName to set.
     */
    public void setDosFileName(String dosFileName) {
        this.dosFileName = dosFileName;
    }

    /**
     * @param sampleType the sampleType to set
     */
    public void setSampleType(int sampleType) {
        this.sampleType = sampleType;
    }

    /**
     * @param fineTune The fineTune to set.
     */
    public void setFineTune(int fineTune) {
        this.fineTune = fineTune;
    }

    /**
     * @param newFlags The flags to set.
     */
    public void setFlags(int newFlags) {
        flags = newFlags;
    }

    /**
     * @param length The length to set.
     */
    public void setLength(int length) {
        this.length = length;
    }

    /**
     * @param loopType The loopType to set.
     */
    public void setLoopType(int loopType) {
        this.loopType = loopType;
    }

    /**
     * @param name The name to set.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @param loopLength The loopLength to set.
     */
    public void setLoopLength(int loopLength) {
        this.loopLength = loopLength;
    }

    /**
     * @param loopStart The loopStart to set.
     */
    public void setLoopStart(int loopStart) {
        this.loopStart = loopStart;
    }

    /**
     * @param loopEnd The loopStop to set.
     */
    public void setLoopStop(int loopEnd) {
        this.loopStop = loopEnd;
    }

    /**
     * @param sustainLoopStart the sustainLoopStart to set
     */
    public void setSustainLoopStart(int sustainLoopStart) {
        this.sustainLoopStart = sustainLoopStart;
    }

    /**
     * @param sustainLoopStop the sustainLoopEnd to set
     */
    public void setSustainLoopStop(int sustainLoopStop) {
        this.sustainLoopStop = sustainLoopStop;
    }

    /**
     * @param sustainLoopLength the sustainLoopLength to set
     */
    public void setSustainLoopLength(int sustainLoopLength) {
        this.sustainLoopLength = sustainLoopLength;
    }

    /**
     * @param sample The sample to set.
     */
    public void setSampleL(long[] sample) {
        this.sampleL = sample;
    }

    /**
     * @param sample The sample to set.
     */
    public void setSampleR(long[] sample) {
        this.sampleR = sample;
    }

    /**
     * @param transpose The transpose to set.
     */
    public void setTranspose(int transpose) {
        this.transpose = transpose;
    }

    /**
     * @param type The type to set.
     */
    public void setType(int type) {
        this.type = type;
    }

    /**
     * @param volume The volume to set.
     */
    public void setVolume(int volume) {
        this.volume = volume;
    }

    /**
     * @param newDefaultPanning The panning to set.
     */
    public void setDefaultPanning(int newDefaultPanning) {
        this.defaultPanning = newDefaultPanning;
    }

    public void setPanning(boolean newSetPanning) {
        setPanning = newSetPanning;
    }

    /**
     * @param isStereo the isStereo to set
     */
    public void setStereo(boolean isStereo) {
        this.isStereo = isStereo;
    }

    /**
     * @param flag_CvT the cvT to set
     */
    public void setCvT(int flag_CvT) {
        this.flag_CvT = flag_CvT;
    }

    /**
     * @param vibratoDepth The vibratoDepth to set.
     */
    public void setVibratoDepth(int vibratoDepth) {
        this.vibratoDepth = vibratoDepth;
    }

    /**
     * @param vibratoRate The vibratoRate to set.
     */
    public void setVibratoRate(int vibratoRate) {
        this.vibratoRate = vibratoRate;
    }

    /**
     * @param vibratoSweep The vibratoSweep to set.
     */
    public void setVibratoSweep(int vibratoSweep) {
        this.vibratoSweep = vibratoSweep;
    }

    /**
     * @param vibratoType The vibratoType to set.
     */
    public void setVibratoType(int vibratoType) {
        this.vibratoType = vibratoType;
    }

    /**
     * @param globalVolume the globalVolume to set
     */
    public void setGlobalVolume(int globalVolume) {
        this.globalVolume = globalVolume;
    }

    /**
     * @return the cues
     */
    public int[] getCues() {
        return cues;
    }

    /**
     * @param newCues the cues to set
     */
    public void setCues(int[] newCues) {
        cues = newCues;
    }

    // not needed those (yet!)

//    public boolean hasCuePoints() {
//        if (cues != null) {
//            for (int i = 0; i < cues.length; i++)
//                if (cues[i] < length) return true;
//        }
//        return false;
//    }
//
//    public boolean hasCustomCuePoints() {
//        if (cues != null) {
//            for (int i = 0; i < cues.length; i++) {
//                final int defaultPoint = (i + 1) << 11;
//                if (cues[i] != defaultPoint && (cues[i] < length || defaultPoint < length)) return true;
//            }
//        }
//        return false;
//    }
//
//    public boolean setDefaultCuePoints() {
//        if (cues == null) cues = new int[MAX_CUES];
//        for (int i = 0; i < cues.length; i++) {
//            final int defaultPoint = (i + 1) << 11;
//            if (defaultPoint < length) cues[i] = defaultPoint;
//            else cues[i] = length;
//        }
//        return false;
//    }
//
//    public boolean set16BitCuePoints() {
//        if (cues == null) cues = new int[MAX_CUES];
//        for (int i = 0; i < cues.length; i++) {
//            final int defaultPoint = (i + 1) << 16;
//            if (defaultPoint < length) cues[i] = defaultPoint;
//            else cues[i] = length;
//        }
//        return false;
//    }

    public boolean getAdlibAmplitudeVibrato(int cm) {
        return ((adLib_Instrument[0 + cm] >> 7) & 0x01) > 0;
    }

    public boolean getAdlibFrequencyVibrato(int cm) {
        return ((adLib_Instrument[0 + cm] >> 6) & 0x01) > 0;
    }

    public boolean getAdlibSustainSound(int cm) {
        return ((adLib_Instrument[0 + cm] >> 5) & 0x01) > 0;
    }

    public boolean getAdlibEnvelopeScaling(int cm) {
        return ((adLib_Instrument[0 + cm] >> 4) & 0x01) > 0;
    }

    public int getAdlibFrequencyMultiplier(int cm) {
        return adLib_Instrument[0 + cm] & 0x0F;
    }

    public int getAdlibKeyScaleLevel(int cm) {
        return (adLib_Instrument[2 + cm] >> 6) & 0x03;
    }

    public int getAdlibVolumeLevel(int cm) {
        return adLib_Instrument[2 + cm] & 0x3F;
    }

    public int getAdlibAttackRate(int cm) {
        return (adLib_Instrument[4 + cm] >> 4) & 0x0F;
    }

    public int getAdlibDecaykRate(int cm) {
        return adLib_Instrument[4 + cm] & 0x0F;
    }

    public int getAdlibSustainLevel(int cm) {
        return (adLib_Instrument[6 + cm] >> 4) & 0x0F;
    }

    public int getAdlibReleaseRate(int cm) {
        return adLib_Instrument[6 + cm] & 0x0F;
    }

    public int getAdlibWaveSelect(int cm) {
        return adLib_Instrument[8 + cm] & 0x07;
    }

    public int getAdlibModulationFeedback() {
        return (adLib_Instrument[10] >> 1) & 0x7;
    }

    public boolean getAdlibAdditiveSynthesis() {
        return (adLib_Instrument[10] & 0x01) > 0;
    }
}
