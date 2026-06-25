/*
 * Copyright (c) 2024 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.sampled.mod;

import java.io.IOException;
import java.util.Map;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;

import de.quippy.javamod.mixer.Mixer;
import vavi.io.OutputEngineInputStream;


/**
 * Converts a Mod music BitStream into a PCM 16bits/sample audio stream.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 241122 nsano initial version <br>
 */
class Mod2PcmAudioInputStream extends AudioInputStream {

    /**
     * Constructor.
     *
     * @param sourceFormat the source format of this stream's audio data.
     * @param format the target format of this stream's audio data.
     * @param length the length in sample frames of the data in this stream.
     */
    public Mod2PcmAudioInputStream(AudioFormat sourceFormat, AudioFormat format, long length, Map<String, Object> props) throws IOException {
        super(new OutputEngineInputStream(((Mixer) sourceFormat.getProperty("mod")).getOutputEngine()), format, length);
    }
}
