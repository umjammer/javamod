/*
 * @(#) OPL3Mixer.java
 *
 * Created on 03.08.2020 by Daniel Becker
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

package de.quippy.javamod.multimedia.opl;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import javax.sound.sampled.AudioFormat;

import de.quippy.javamod.mixer.BasicMixer;
import de.quippy.javamod.multimedia.opl.emu.EmuOPL;
import de.quippy.javamod.multimedia.opl.emu.EmuOPL.OplType;
import de.quippy.javamod.multimedia.opl.emu.EmuOPL.Version;
import de.quippy.javamod.multimedia.opl.sequencer.OPL3Sequence;

import static java.lang.System.getLogger;


/**
 * @author Daniel Becker
 * @since 03.08.2020
 */
public class OPL3Mixer extends BasicMixer {

    private static final Logger logger = getLogger(OPL3Mixer.class.getName());

    private static final int ANZ_CHANNELS = 2;
    private static final int BITS_PER_SAMPLE = 16;
    private static final int MS_BUFFER_SIZE = 200;
    private static final int COOL_DOWN = 2; // 2 seconds of cool down for OPL
    private static final int RAMP_DOWN_SHIFT = 14;
    private static final int RAMP_DOWN_START = 1 << RAMP_DOWN_SHIFT;

    // Wide Stereo Vars
    private boolean doVirtualStereo;
    private int maxWideStereo;
    private long[] wideLBuffer;
    private long[] wideRBuffer;
    private int readPointer;
    private int writePointer;

    private final OPL3Sequence opl3Sequence;
    private EmuOPL opl;

    private byte[] buffer;
    private int bufferSize;
    private long samplesWritten = 0;
    private int rampDownVolume;

    private final float sampleRate;
    private final Version OPLVersion;

    /**
     * Constructor for OPL3Mixer
     */
    public OPL3Mixer(Version OPLVersion, float sampleRate, OPL3Sequence opl3Sequence, boolean doVirtualStereo) {
        super();
        this.OPLVersion = OPLVersion;
        this.sampleRate = sampleRate;
        this.opl3Sequence = opl3Sequence;
        this.doVirtualStereo = doVirtualStereo;
    }

    protected void initialize() {
        opl = EmuOPL.createInstance(OPLVersion, sampleRate, opl3Sequence.getOPLType());
logger.log(Level.DEBUG, "opl: " + opl.getClass().getName() + ", " + OPLVersion);

        bufferSize = (int) ((MS_BUFFER_SIZE * ANZ_CHANNELS * sampleRate + 500) / 1000);
        while ((bufferSize % 4) != 0) bufferSize++;
        buffer = new byte[bufferSize];
        setSourceLineBufferSize(bufferSize);
        rampDownVolume = RAMP_DOWN_START;

        // initialize the wide stereo mix
        maxWideStereo = (int) sampleRate / 50;
        wideLBuffer = new long[maxWideStereo];
        wideRBuffer = new long[maxWideStereo];
        readPointer = 0;
        writePointer = maxWideStereo - 1;

        opl3Sequence.initialize(opl);

        setAudioFormat(new AudioFormat(sampleRate, BITS_PER_SAMPLE, ANZ_CHANNELS, true, false));
    }

    @Override
    public boolean isSeekSupported() {
        return true;
    }

    @Override
    protected void seek(long milliseconds) {
        double ms = 0d;
        opl3Sequence.initialize(opl);
        while (ms < milliseconds && opl3Sequence.updateToOPL(opl))
            ms += 1000d / opl3Sequence.getRefresh();
        samplesWritten = (long) ((ms * sampleRate / 1000d) + 0.5d);
    }

    @Override
    public long getLengthInMilliseconds() {
        return opl3Sequence.getLengthInMilliseconds();
    }

    @Override
    public long getMillisecondPosition() {
        return samplesWritten * 1000L / (long) sampleRate;
    }

    @Override
    public int getChannelCount() {
        return ANZ_CHANNELS;
    }

    @Override
    public int getCurrentKBperSecond() {
        return (int) ((ANZ_CHANNELS * BITS_PER_SAMPLE * sampleRate) / 1000L);
    }

    @Override
    public int getCurrentSampleRate() {
        return (int) sampleRate;
    }

    /**
     * @param doVirtualStereo
     * @since 14.08.2020
     */
    public void setDoVirtualStereoMix(boolean doVirtualStereo) {
        this.doVirtualStereo = doVirtualStereo;
    }

    @Override
    public void startPlayback() {
        initialize();
        int[] fromOPL3 = new int[2];
        samplesWritten = 0;
        int bufferIndex = 0;

        setIsPlaying();

        if (getSeekPosition() > 0) seek(getSeekPosition());

        try {
            openAudioDevice();
            if (!isInitialized()) return;

            boolean finished = false;
            while (!finished) {
                boolean newData = opl3Sequence.updateToOPL(opl);

                double refresh = (newData) ? 1.0d / opl3Sequence.getRefresh() : (double) COOL_DOWN;
                int samples = (int) (((double) sampleRate * refresh) + 0.5);
                if (hasStopPosition()) {
                    long bytesToWrite = getSamplesToWriteLeft();
                    if ((long) (samples) > bytesToWrite) samples = (int) bytesToWrite;
                }
                for (int s = 0; s < samples; s++) {
                    opl.read(fromOPL3);
                    int samplel = fromOPL3[0];
                    int sampler = fromOPL3[1];
                    fromOPL3[0] = fromOPL3[1] = 0;

                    // WideStereo Mixing - but only with stereo
                    if (doVirtualStereo && opl.getOPLType() != OplType.OPL2) {
                        wideLBuffer[writePointer] = samplel;
                        wideRBuffer[writePointer++] = sampler;
                        if (writePointer >= maxWideStereo) writePointer = 0;

                        sampler += (int) (wideLBuffer[readPointer] >> 1);
                        samplel += (int) (wideRBuffer[readPointer++] >> 1);
                        if (readPointer >= maxWideStereo) readPointer = 0;
                    }

                    // let's do a fast ramp down at the end, to avoid clicking
                    if (!newData && samples - s <= RAMP_DOWN_START) {
                        samplel = (samplel * rampDownVolume) >> RAMP_DOWN_SHIFT;
                        sampler = (sampler * rampDownVolume) >> RAMP_DOWN_SHIFT;
                        rampDownVolume--;
                        if (rampDownVolume <= 0) rampDownVolume = 0;
                    }

                    // Clipping - always a good idea (sample is 32bit (int), but 16 bit is border):
                    if (samplel > 0x0000_7FFF) samplel = 0x0000_7FFF;
                    else if (samplel < 0xffFF_8000) samplel = 0xffFF_8000;
                    if (sampler > 0x0000_7FFF) sampler = 0x0000_7FFF;
                    else if (sampler < 0xffFF_8000) sampler = 0xffFF_8000;

                    buffer[bufferIndex] = (byte) (samplel & 0xff);
                    buffer[bufferIndex + 1] = (byte) ((samplel >> 8) & 0xff);
                    buffer[bufferIndex + 2] = (byte) (sampler & 0xff);
                    buffer[bufferIndex + 3] = (byte) ((sampler >> 8) & 0xff);
                    samplesWritten++;
                    bufferIndex += 4;
                    if (bufferIndex >= bufferSize) {
                        writeSampleDataToLine(buffer, 0, bufferIndex - 4);
                        bufferIndex = 0;
                    }
                    if (isStopping() || isPausing() || isInSeeking()) break;
                }

                if (!newData && !isStopping()) { // if no new Data, we are ready
                    // finish off the buffer, if something is left
                    bufferIndex -= 4;
                    if (bufferIndex > 0) {
                        writeSampleDataToLine(buffer, 0, bufferIndex);
                    }
                    finished = true;
                }

                if (stopPositionIsReached()) setIsStopping();

                if (isStopping()) {
                    setIsStopped();
                    break;
                }
                if (isPausing()) {
                    setIsPaused();
                    while (isPaused()) {
                        try {
                            Thread.sleep(10L);
                        } catch (InterruptedException ex) { /*noop*/ }
                    }
                }
                if (isInSeeking()) {
                    setIsSeeking();
                    while (isInSeeking()) {
                        try {
                            Thread.sleep(10L);
                        } catch (InterruptedException ex) { /*noop*/ }
                    }
                }
            }
            if (finished) setHasFinished();
        } catch (Throwable ex) {
            throw new RuntimeException(ex);
        } finally {
            setIsStopped();
            closeAudioDevice();
        }
    }
}
