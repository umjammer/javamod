/*
 * Copyright (c) 2024 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package de.quippy.javamod.multimedia.vgm;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import javax.sound.sampled.AudioFormat;

import de.quippy.javamod.mixer.BasicMixer;
import libgme.EmuPlayer.Engine;
import libgme.MusicEmu;
import libgme.VGMPlayer;

import static java.lang.System.getLogger;


/**
 * VGMMixer.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2024-09-03 nsano initial version <br>
 */
public class VGMMixer extends BasicMixer {

    private static final Logger logger = getLogger(VGMMixer.class.getName());

    private int sampleRate = 44100;

    private String file;

    VGMMixer(String file) {
        this.file = file;
    }

    @Override
    protected void seek(long milliseconds) {

    }

    static class StreamEngine implements Engine {

        private final OutputStream os;
        volatile boolean playing;
        MusicEmu emu;

        StreamEngine(OutputStream os) {
            this.os = os;
        }

        @Override
        public void run() {
            try {
                playing = true;
logger.log(Level.DEBUG, "START: playing: " + playing + ", trackEnd: " + emu.trackEnded());

                // play track until stop signal
                byte[] buf = new byte[8192];
                while (playing && !emu.trackEnded()) {
                    int count = emu.play(buf, buf.length / 2);
logger.log(Level.TRACE, "count: " + count + ", playing: " + playing);
                    os.write(buf, 0, count * 2);
                }

logger.log(Level.DEBUG, "STOP");
            } catch (Exception e) {
                logger.log(Level.ERROR, e.getMessage(), e);
            } finally {
                playing = false;
            }
        }

        @Override
        public void setEmu(MusicEmu emu) {
            this.emu = emu;
        }

        @Override public void init() {
        }

        @Override public void reset() {
        }

        @Override public void setVolume(double volume) {
        }

        @Override public void setSampleRate(int sampleRate) {

        }

        @Override
        public void stop() {
            setPlaying(false);
        }

        @Override
        public boolean isPlaying() {
            return playing;
        }

        @Override
        public void setPlaying(boolean playing) {
            this.playing = playing;
        }
    }

    @Override
    public void startPlayback() {
        setSourceLineBufferSize(sampleRate * 2);

        VGMPlayer player = new VGMPlayer(sampleRate);

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
logger.log(Level.TRACE, "write: " + len + ", " + player.isPlaying());
                writeSampleDataToLine(b, off, len);
            }
        };

        try {
logger.log(Level.TRACE, "sampleRate: " + sampleRate);
            setAudioFormat(new AudioFormat(this.sampleRate, 16, 2, true, true));
            openAudioDevice();
            if (!isInitialized()) return;

            boolean finished = false;

            StreamEngine engine = new StreamEngine(os);
            player.setEngine(engine);

            player.loadFile(file);
            player.setTrack(1);
            player.play();
logger.log(Level.DEBUG, "play");

            do {
                if (stopPositionIsReached()) setIsStopping();

                if (isStopping()) {
                    setIsStopped();
                    break;
                }
                if (isPausing()) {
                    player.pause();
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
            player.stop();
            setIsStopped();
            closeAudioDevice();
logger.log(Level.DEBUG, "exit startPlayback");
        }
    }

    @Override
    public boolean isSeekSupported() {
        return false;
    }

    @Override
    public long getLengthInMilliseconds() {
        return 0;
    }

    @Override
    public long getMillisecondPosition() {
        return 0;
    }

    @Override
    public int getChannelCount() {
        return 2;
    }

    @Override
    public int getCurrentKBperSecond() {
        return 0;
    }

    @Override
    public int getCurrentSampleRate() {
        return sampleRate;
    }
}
