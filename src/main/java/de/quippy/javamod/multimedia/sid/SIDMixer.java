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
import de.quippy.javamod.multimedia.sid.PlayerUtil.PcmSink;
import libsidplay.common.EventScheduler;
import libsidplay.common.SamplingRate;
import libsidplay.config.IConfig;
import libsidplay.sidtune.SidTune;
import sidplay.Player;
import sidplay.audio.Audio;
import sidplay.audio.AudioConfig;
import sidplay.audio.AudioDriver;
import sidplay.audio.JWAVDriver.JWAVStreamDriver;
import sidplay.audio.exceptions.SongEndException;
import sidplay.ini.IniConfig;
import sidplay.player.State;
import vavi.io.OutputEngine;

import static de.quippy.javamod.multimedia.sid.PlayerUtil.playerOpen;
import static java.lang.System.getLogger;
import static sidplay.player.State.OPEN;
import static sidplay.player.State.PAUSE;
import static sidplay.player.State.PLAY;
import static sidplay.player.State.QUIT;
import static sidplay.player.State.START;


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
     * @since 13.02.2012
     */
    @Override
    protected void seek(long milliseconds) {
        if (sidTune != null) {
            pausePlayback();
            songNumber = (int) (milliseconds / 1000L) + 1;
//            sidTune.selectSong(songNumber); // TODO impl
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
                try {
logger.log(Level.TRACE, "write: " + len + ", " + sidPlayer.stateProperty().get());
                    writeSampleDataToLine(b, off, len);
                } catch (Exception e) {
logger.log(Level.TRACE, "stream closed?: " + sidPlayer.stateProperty().get() + ", " + e);
                }
            }
        };

        try {
logger.log(Level.TRACE, "sampleRate: " + sampleRate);
            setAudioFormat(new AudioFormat(this.sampleRate, 16, 2, true, false));
            openAudioDevice();
            if (!isInitialized()) return;

            AudioDriver audioDriver = sidConfig.getAudioSection().getAudio().getAudioDriver();
logger.log(Level.TRACE, "audioDriver: " + audioDriver);
            if (!(audioDriver instanceof JWAVStreamDriver streamDriver)) {
                throw new IllegalStateException("unsupported audio driver: " + audioDriver);
            }

            streamDriver.setOut(os);

            sidPlayer.play(sidTune);
            while (sidPlayer.stateProperty().get() != State.PLAY) {
                try { Thread.sleep(10L); } catch (InterruptedException ex) { /* noop */ }
            }
logger.log(Level.DEBUG, "play: " + sidPlayer.stateProperty().get());

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
                        try { Thread.sleep(10L); } catch (InterruptedException ex) { /* noop */ }
                    }
                }
                if (isInSeeking()) {
                    setIsSeeking();
                    while (isInSeeking()) {
                        try { Thread.sleep(10L); } catch (InterruptedException ex) { /* noop */ }
                    }
                }
                try { Thread.sleep(10L); } catch (InterruptedException ex) { /* noop */ }
            } while (sidPlayer.stateProperty().get() == State.PLAY);
            if (sidPlayer.stateProperty().get() != State.PLAY) setHasFinished(); // Piece was played full
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

    /**
     * Single threaded decoding.
     * <p>
     * Unlike {@link #getOutputEngine()} this does not let {@link Player} run its own
     * "Player" thread, and audio is <em>pulled</em>, not pushed: nothing calls back into
     * an {@link AudioDriver} that performs I/O. {@link #initialize} opens the emulation on
     * the caller's thread; then each {@link OutputEngine#execute()} call performs the
     * three explicit steps of the engine contract:
     * <ol>
     * <li><b>advance</b> &mdash; clock the C64 until the reSID mixer has rendered a chunk,</li>
     * <li><b>get</b> the chunked 16-bit PCM out of {@link PcmSink},</li>
     * <li><b>write</b> that chunk to the target {@link OutputStream}.</li>
     * </ol>
     * {@link PcmSink} is a passive latch (the reSID mixer requires <em>some</em>
     * {@link AudioDriver} to render into); it does no I/O and is installed through the
     * public {@link Player#setAudioDriver(AudioDriver)} hook so the WAV/stream driver is
     * never used.
     */
    @Override
    public OutputEngine getOutputEngine() {
        return new OutputEngine() {

            /** target */
            private OutputStream out;

            /** passive PCM latch the reSID mixer renders into; pulled in execute() */
            private PcmSink sink;

            @Override
            public void initialize(OutputStream out) throws IOException {
                if (this.out != null) {
                    throw new IOException("Already initialized");
                }
                this.out = out;

                SIDMixer.this.initialize();
                int bufferSize = sampleRate;
                int byteBufferSize = (isStereo) ? bufferSize : bufferSize << 1;
                setSourceLineBufferSize(byteBufferSize);

                parentSIDContainer.nameChanged();
                setIsPlaying();

                if (getSeekPosition() > 0) seek(getSeekPosition());

                // The reSID mixer renders into an AudioDriver buffer; install a passive
                // latch (no I/O, no callbacks) so execute() can pull the samples itself
                // instead of the mixer pushing them out. Installed via the public hook,
                // so the WAV/stream driver is bypassed entirely.
                sink = new PcmSink();
                sidPlayer.setAudioDriver(sink);

                // Replicate Player#playerRunnable's start sequence on *this* thread
                // (no Player thread is started), so the emulation can be clocked from
                // execute(). The SID-chip insertion and mixer start happen through events
                // fired while clocking, exactly as in the real play() loop.
                sidPlayer.stopC64();
                sidPlayer.setTune(sidTune);
                sidPlayer.stateProperty().set(OPEN);
                playerOpen(sidPlayer, sidConfig, sidTune); // private Player#open() + playList
                sidPlayer.stateProperty().set(START);
                // Player#menuHook is a UI-only callback; nothing to do in batch mode.
                sidPlayer.stateProperty().set(PLAY);
            }

            @Override
            public void execute() throws IOException {
                if (out == null) {
                    throw new IOException("Not yet initialized");
                }
                try {
                    if (stopPositionIsReached()) setIsStopping();
                    if (isStopping()) {
                        sidPlayer.stateProperty().set(QUIT);
                        setIsStopped();
                        out.close();
                        return;
                    }

                    State state = sidPlayer.stateProperty().get();
                    if (state != PLAY && state != PAUSE) {
                        setHasFinished();
                        out.close();
                        return;
                    }

                    // 1. advance: clock the C64 until the reSID mixer has rendered a chunk
                    EventScheduler scheduler = sidPlayer.getC64().getEventScheduler();
                    if (state == PAUSE) {
                        scheduler.clockThreadSafeEvents();
                        Thread.sleep(250L);
                    } else {
                        while (sink.available() == 0 && sidPlayer.stateProperty().get() == PLAY) {
                            scheduler.clock();
                        }
                    }

                    // 2. get the chunked audio data, 3. write it to the output engine
                    byte[] chunk = sink.drain();
                    if (chunk.length > 0) {
                        out.write(chunk);
                    }

                    State after = sidPlayer.stateProperty().get();
                    if (after != PLAY && after != PAUSE) {
                        setHasFinished();
                        out.close();
                    }
                } catch (SongEndException e) {
                    sidPlayer.getTimer().end();
                    byte[] chunk = sink.drain(); // flush whatever was rendered before the end
                    if (chunk.length > 0) out.write(chunk);
                    setHasFinished();
                    out.close();
                } catch (Throwable e) {
                    logger.log(Level.ERROR, e.getMessage(), e);
                    out.close();
                }
            }

            @Override
            public void finish() throws IOException {
                PlayerUtil.playerClose(sidPlayer); // private Player#close(): teardown
                setIsStopped();
            }
        };
    }
}
