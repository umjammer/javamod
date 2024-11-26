/*
 * Copyright (c) 2024 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.sampled.mod;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFileFormat.Type;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioFormat.Encoding;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.sound.sampled.spi.AudioFileReader;

import de.quippy.javamod.mixer.Mixer;
import de.quippy.javamod.multimedia.SpiMultimediaContainer;

import static de.quippy.javamod.io.SpiModfileInputStream.MAX_BUFFER_SIZE;
import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.TRACE;
import static java.lang.System.getLogger;
import static javax.sound.sampled.AudioSystem.NOT_SPECIFIED;


/**
 * Provider for emulator audio file reading services. This implementation can parse
 * the format information from emulator audio file, and can produce audio input
 * streams from files of this type.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 241122 nsano initial version <br>
 */
public class ModAudioFileReader extends AudioFileReader {

    private static final Logger logger = getLogger(ModAudioFileReader.class.getName());

    private URI uri;

    @Override
    public AudioFileFormat getAudioFileFormat(File file) throws UnsupportedAudioFileException, IOException {
        try (InputStream inputStream = new BufferedInputStream(Files.newInputStream(file.toPath()), MAX_BUFFER_SIZE)) {
            uri = file.toURI();
            return getAudioFileFormat(inputStream, (int) file.length());
        }
    }

    @Override
    public AudioFileFormat getAudioFileFormat(URL url) throws UnsupportedAudioFileException, IOException {
        try (InputStream inputStream = new BufferedInputStream(url.openStream(), MAX_BUFFER_SIZE)) {
            try {
                uri = url.toURI();
            } catch (URISyntaxException ignore) {
            }
            return getAudioFileFormat(inputStream);
        }
    }

    @Override
    public AudioFileFormat getAudioFileFormat(InputStream stream) throws UnsupportedAudioFileException, IOException {
        return getAudioFileFormat(stream, NOT_SPECIFIED);
    }

    /**
     * Return the AudioFileFormat from the given InputStream. Implementation.
     *
     * @param bitStream input to decode, mark must be supported and required buffer size ({@link de.quippy.javamod.io.SpiModfileInputStream#MAX_BUFFER_SIZE})
     * @param mediaLength unused
     * @return an AudioInputStream object based on the audio file data contained
     * in the input stream.
     * @throws UnsupportedAudioFileException if the File does not point to a
     *                                       valid audio file data recognized by the system.
     * @throws IOException                   if an I/O exception occurs.
     */
    protected AudioFileFormat getAudioFileFormat(InputStream bitStream, int mediaLength) throws UnsupportedAudioFileException, IOException {
logger.log(DEBUG, "enter: available: " + bitStream.available() + ", " + bitStream);
        if (!bitStream.markSupported()) {
            throw new IllegalArgumentException("input stream not supported mark");
        }
        Mixer mixer;
        Encoding encoding;
        float samplingRate;
        int channels;
        try {
            mixer = SpiMultimediaContainer.factory(bitStream);
logger.log(DEBUG, "mixer: " + mixer);

            samplingRate = mixer.getCurrentSampleRate();
            int channelCount = mixer.getChannelCount();
logger.log(TRACE, "samplingRate: " + samplingRate + ", channels: " + channelCount);
            channels = channelCount == 0 ? 2 : channelCount;
            encoding = ModEncoding.valueOf(mixer.getClass().getSimpleName().replace("Mixer", ""));

        } catch (IllegalArgumentException | NoSuchElementException e) {
logger.log(DEBUG, "error exit: available: " + bitStream.available() + ", " + bitStream);
logger.log(TRACE, e.getMessage(), e);
            throw (UnsupportedAudioFileException) new UnsupportedAudioFileException().initCause(e);
        }
        Type type = ModFileFormatType.valueOf(mixer.getClass().getSimpleName().replace("Mixer", ""));
logger.log(TRACE, "type: " + type);
        Map<String, Object> props = new HashMap<>();
        props.put("mod", mixer);
        props.put("uri", uri);
        AudioFormat format = new AudioFormat(encoding, samplingRate, NOT_SPECIFIED, channels, NOT_SPECIFIED, NOT_SPECIFIED, false, props);
        return new AudioFileFormat(type, format, NOT_SPECIFIED);
    }

    @Override
    public AudioInputStream getAudioInputStream(File file) throws UnsupportedAudioFileException, IOException {
        InputStream inputStream = new BufferedInputStream(Files.newInputStream(file.toPath()), MAX_BUFFER_SIZE);
        try {
            return getAudioInputStream(inputStream, (int) file.length());
        } catch (UnsupportedAudioFileException | IOException e) {
            inputStream.close();
            throw e;
        }
    }

    @Override
    public AudioInputStream getAudioInputStream(URL url) throws UnsupportedAudioFileException, IOException {
        InputStream inputStream = new BufferedInputStream(url.openStream(), MAX_BUFFER_SIZE);
        try {
            return getAudioInputStream(inputStream);
        } catch (UnsupportedAudioFileException | IOException e) {
            inputStream.close();
            throw e;
        }
    }

    @Override
    public AudioInputStream getAudioInputStream(InputStream stream) throws UnsupportedAudioFileException, IOException {
        return getAudioInputStream(stream, NOT_SPECIFIED);
    }

    /**
     * Obtains an audio input stream from the input stream provided. The stream
     * must point to valid audio file data.
     *
     * @param inputStream the input stream from which the AudioInputStream
     *                    should be constructed.
     * @param mediaLength unused
     * @return an AudioInputStream object based on the audio file data contained
     * in the input stream.
     * @throws UnsupportedAudioFileException if the File does not point to a
     *                                       valid audio file data recognized by the system.
     * @throws IOException                   if an I/O exception occurs.
     */
    protected AudioInputStream getAudioInputStream(InputStream inputStream, int mediaLength) throws UnsupportedAudioFileException, IOException {
        AudioFileFormat audioFileFormat = getAudioFileFormat(inputStream, mediaLength);
        return new AudioInputStream(inputStream, audioFileFormat.getFormat(), audioFileFormat.getFrameLength());
    }
}
