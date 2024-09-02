/*
 * @(#) SIDMixer.java
 *
 * Created on 04.10.2009 by Daniel Becker
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

package de.quippy.javamod.multimedia.sid;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.NoSuchElementException;
import javax.sound.sampled.AudioFormat;

import de.quippy.javamod.mixer.BasicMixer;
import libsidplay.common.SamplingRate;
import libsidplay.config.IConfig;
import libsidplay.sidtune.SidTune;
import sidplay.Player;
import sidplay.audio.Audio;
import sidplay.audio.AudioConfig;
import sidplay.audio.AudioDriver;
import sidplay.audio.JWAVDriver.JWAVStreamDriver;
import sidplay.ini.IniConfig;

import static java.lang.System.getLogger;


/**
 * @author Daniel Becker
 * @since 04.10.2009
 */
public class SIDMixer extends BasicMixer {

    private static final Logger logger = getLogger(SIDMixer.class.getName());

    private Player sidPlayer;
    private final SidTune sidTune;

    private byte[] output;

    private int sampleRate;
    private int sidModel;
    private int optimization;
    private final boolean sidFilter;
    private boolean isStereo;

    private static final int MULTIPLIER_SHIFT = 4;
    private static final int MULTIPLIER_VALUE = 1 << MULTIPLIER_SHIFT;

    private int multiplier;
    private int songNumber;

    private final SIDContainer parentSIDContainer;

    /**
     * Constructor for SIDMixer
     */
    public SIDMixer(SidTune sidTune, SIDContainer parent, int sampleRate, int sidModel, int optimization, boolean sidFilter, boolean isStereo) {
        super();
        this.sidTune = sidTune;
        if (sidTune != null) {
            songNumber = sidTune.getInfo().getCurrentSong();
            if (songNumber == 0) songNumber = 1;
        } else
            songNumber = 1;
        this.parentSIDContainer = parent;
        this.optimization = optimization;
logger.log(Level.DEBUG, "player sampleRate: " + sampleRate);
        this.sampleRate = sampleRate;
        this.sidModel = sidModel;
        this.sidFilter = sidFilter;
        this.isStereo = isStereo;
    }

    private IConfig sidConfig;

    private void initialize() {
        try {
            sidConfig = new IniConfig();
            sidConfig.getAudioSection().setAudio(Audio.STREAM);
            setSamplingRateToSidPlayer();
            // TODO song length
//            sidTune.selectSong(songNumber);
            sidPlayer = new Player(sidConfig);
            sidPlayer.setTune(sidTune);
            // sid chip raw writing
//            sidPlayer.addSidListener((i, b) -> logger.log(Level.DEBUG, "SIDListener: " + i + ": " + b));

            multiplier = MULTIPLIER_VALUE;
        } catch (Exception ex) {
            logger.log(Level.ERROR, "[SIDMixer]", ex);
        }
    }

    private void setSamplingRateToSidPlayer() {
        SamplingRate samplingRate;
        try {
            samplingRate = SamplingRate.getByFrequency(this.sampleRate);
        } catch (NoSuchElementException e) {
            samplingRate = SamplingRate.LOW;
        }
logger.log(Level.DEBUG, "sid sampleRate: " + sampleRate);
        sidConfig.getAudioSection().setSamplingRate(samplingRate);
    }

    public void setSampleRate(int newSampleRate) {
        int oldSampleRate = sampleRate;

        boolean wasPaused = isPaused();
        boolean wasPlaying = isPlaying();
        if (wasPlaying && !wasPaused) pausePlayback();

        sampleRate = newSampleRate;
        if (wasPlaying) {
            setAudioFormat(new AudioFormat(sampleRate, 16, 2, true, false));
            openAudioDevice();
            if (!isInitialized()) {
                sampleRate = oldSampleRate;
                setAudioFormat(new AudioFormat(sampleRate, 16, 2, true, false));
                openAudioDevice();
            } else {
                setSamplingRateToSidPlayer();
            }

            if (!wasPaused) pausePlayback();
        }
    }

    public void setSIDModel(int newSidModel) {
        boolean wasPlaying = !isPaused();
        if (wasPlaying) pausePlayback();

        sidModel = newSidModel;
        // TODO impl
//        sidConfig.sidModel = (sidModel == 0) ? ISID2Types.sid2_model_t.SID2_MODEL_CORRECT : ((sidModel == 1) ? ISID2Types.sid2_model_t.SID2_MOS6581 : ISID2Types.sid2_model_t.SID2_MOS8580);

        if (wasPlaying) pausePlayback();
    }

    public void setOptimization(int newOptimization) {
        boolean wasPlaying = !isPaused();
        if (wasPlaying) pausePlayback();

        optimization = newOptimization;
//        sidConfig.optimisation = (byte) optimization; // TODO impl

        if (wasPlaying) pausePlayback();
    }

    public void setUseSIDFilter(boolean useSIDFilter) {
        boolean wasPlaying = !isPaused();
        if (wasPlaying) pausePlayback();

        sidConfig.getEmulationSection().setFilter(useSIDFilter);

        if (wasPlaying) pausePlayback();
    }

    public void setVirtualStereo(boolean newIsStereo) {
        boolean wasPlaying = !isPaused();
        if (wasPlaying) pausePlayback();

        isStereo = newIsStereo;
//        IConfig sidConfig = sidPlayer.config();
        // TODO impl
//        sidConfig.getEmulationSection().setStereoMode(StereoMode.STEREO);
//        emulateStereo = isStereo;
//        sidConfig.playback = (isStereo ? ISID2Types.sid2_playback_t.sid2_stereo : ISID2Types.sid2_playback_t.sid2_mono);

        if (wasPlaying) pausePlayback();
    }

    @Override
    public boolean isSeekSupported() {
        return true;
    }

    @Override
    public long getLengthInMilliseconds() {
        return (sidTune != null) ? sidTune.getInfo().getSongs() * 1000 : 0;
    }

    @Override
    public long getMillisecondPosition() {
        return songNumber * 1000L;
    }

    @Override
    public int getChannelCount() {
        if (sidPlayer != null) {
            return new AudioConfig(sidConfig.getAudioSection()).getChannels(); // TODO which channel?
        }
        return 0;
    }

    @Override
    public int getCurrentKBperSecond() {
        return (getChannelCount() * 16 * sampleRate) / 1000;
    }

    @Override
    public int getCurrentSampleRate() {
        return sampleRate;
    }

    /**
     * @param milliseconds
     * @see de.quippy.javamod.mixer.BasicMixer#seek(long)
     * @since 13.02.2012
     */
    @Override
    protected void seek(long milliseconds) {
        if (sidTune != null) {
            pausePlayback();
            songNumber = (int) (milliseconds / 1000L) + 1;
//			sidTune.selectSong(songNumber); // TODO impl
            sidPlayer.play(sidTune);
            parentSIDContainer.nameChanged();
            pausePlayback();
        }
    }

    private byte[] getOutputBuffer(int length) {
        if (output == null || output.length < length) output = new byte[length];
        return output;
    }

    @Override
    public void startPlayback() {
        initialize();
        int bufferSize = sampleRate;
        int byteBufferSize = (isStereo) ? bufferSize : bufferSize << 1;
        setSourceLineBufferSize(byteBufferSize);

        parentSIDContainer.nameChanged();
        setIsPlaying();

        if (getSeekPosition() > 0) seek(getSeekPosition());

        OutputStream os = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                byte[] buf = new byte[] {(byte) b};
                write(buf, 0, 1);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
logger.log(Level.TRACE, "write: " + len + ", " + sidPlayer.stateProperty().get());
                writeSampleDataToLine(b, off, len);
            }
        };

        try {
logger.log(Level.TRACE, "sampleRate: " + sampleRate);
            setAudioFormat(new AudioFormat(this.sampleRate, 16, 2, true, false));
            openAudioDevice();
            if (!isInitialized()) return;

            boolean finished = false;

            AudioDriver audioDriver = sidConfig.getAudioSection().getAudio().getAudioDriver();
logger.log(Level.TRACE, "audioDriver: " + audioDriver);
            if (!(audioDriver instanceof JWAVStreamDriver streamDriver)) {
                throw new IllegalStateException("unsupported audio driver: " + audioDriver);
            }

            streamDriver.setOut(os);

            sidPlayer.play(sidTune);
logger.log(Level.DEBUG, "play");

            do {
                if (stopPositionIsReached()) setIsStopping();

                if (isStopping()) {
                    setIsStopped();
                    break;
                }
                if (isPausing()) {
                    sidPlayer.pauseContinue();
                    setIsPaused();
                    while (isPaused()) {
                        try {
                            Thread.sleep(10L);
                        } catch (InterruptedException ex) { /* noop */ }
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
            } while (!finished);
            if (finished) setHasFinished(); // Piece was played full
        } catch(Throwable ex) {
            throw new RuntimeException(ex);
        } finally {
            try {
                os.close();
            } catch (IOException e) {
                logger.log(Level.ERROR, e);
            }
            sidPlayer.quit();
            setIsStopped();
            closeAudioDevice();
logger.log(Level.DEBUG, "exit startPlayback");
        }
    }
}
