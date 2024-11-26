/*
 * Copyright (c) 2024 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.sampled.mod;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;

import de.quippy.javamod.mixer.Mixer;
import vavi.io.OutputEngine;
import vavi.io.OutputEngineInputStream;

import static java.lang.System.getLogger;


/**
 * Converts a Mod music BitStream into a PCM 16bits/sample audio stream.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 241122 nsano initial version <br>
 */
class Mod2PcmAudioInputStream extends AudioInputStream {

    private static final Logger logger = getLogger(Mod2PcmAudioInputStream.class.getName());

    /**
     * Constructor.
     *
     * @param sourceFormat the source format of this stream's audio data.
     * @param format the target format of this stream's audio data.
     * @param length the length in sample frames of the data in this stream.
     */
    public Mod2PcmAudioInputStream(AudioFormat sourceFormat, AudioFormat format, long length, Map<String, Object> props) throws IOException {
        super(new OutputEngineInputStream(new ModOutputEngine((Mixer) sourceFormat.getProperty("mod"), props)), format, length);
    }

    /** */
    private static class ModOutputEngine implements OutputEngine {

        /** target */
        private OutputStream out;

        /** deque */
        final ModSoundOutputStream queue;

        /** TODO consider output engine's raison d'être */
        final ExecutorService es = Executors.newSingleThreadExecutor();

        /** */
        public ModOutputEngine(Mixer mixer, Map<String, Object> props) throws IOException {
logger.log(Level.DEBUG,"mod: " + mixer.getClass().getName());
            queue = new ModSoundOutputStream();
            mixer.setSoundOutputStream(queue);
            es.submit(mixer::startPlayback); // TODO *engine exists for not to use thread
        }

        @Override
        public void initialize(OutputStream out) throws IOException {
            if (this.out != null) {
                throw new IOException("Already initialized");
            } else {
                this.out = out;

                while (!queue.hasStarted()) { // wait until the deque fills up
                    Thread.yield();
                }
            }
        }

        @Override
        public void execute() throws IOException {
            if (out == null) {
                throw new IOException("Not yet initialized");
            } else {
                try {
                    if (!queue.hasFinished()) {
                        out.write(queue.readSampleData());
                    } else {
                        out.close();
                    }
                } catch (InterruptedException e) {
logger.log(Level.ERROR, e.getMessage(), e);
                    out.close();
                }
            }
        }

        @Override
        public void finish() throws IOException {
            es.shutdown();
        }
    }
}
