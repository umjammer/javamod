/*
 * Copyright (c) 2024 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.sampled.mod;

import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Stream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioFormat.Encoding;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.spi.FormatConversionProvider;

import static javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED;
import static javax.sound.sampled.AudioSystem.NOT_SPECIFIED;
import static vavi.sound.sampled.mod.ModEncoding.encodings;


/**
 * ModFormatConversionProvider.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 241122 nsano initial version <br>
 */
public class ModFormatConversionProvider extends FormatConversionProvider {

    @Override
    public Encoding[] getSourceEncodings() {
        return Stream.concat(Arrays.stream(encodings), Stream.of(PCM_SIGNED))
                .toArray(Encoding[]::new);
    }

    @Override
    public Encoding[] getTargetEncodings() {
        return Stream.concat(Arrays.stream(encodings), Stream.of(PCM_SIGNED))
                .toArray(Encoding[]::new);
        }

    @Override
    public Encoding[] getTargetEncodings(AudioFormat sourceFormat) {
        if (sourceFormat.getEncoding().equals(PCM_SIGNED)) {
            return Arrays.stream(encodings).toArray(Encoding[]::new);
        } else if (sourceFormat.getEncoding() instanceof ModEncoding) {
            return new Encoding[] {PCM_SIGNED};
        } else {
            return new Encoding[0];
        }
    }

    @Override
    public AudioFormat[] getTargetFormats(Encoding targetEncoding, AudioFormat sourceFormat) {
        if (sourceFormat.getEncoding().equals(PCM_SIGNED) &&
                targetEncoding instanceof ModEncoding) {
            if (sourceFormat.getChannels() > 2 ||
                    sourceFormat.getChannels() <= 0 ||
                    sourceFormat.isBigEndian()) {
                return new AudioFormat[0];
            } else {
                return new AudioFormat[] {
                        new AudioFormat(targetEncoding,
                                sourceFormat.getSampleRate(),
                                -1,           // sample size in bits
                                sourceFormat.getChannels(),
                                -1,                // frame size
                                -1,                          // frame rate
                                false)                       // little endian
                };
            }
        } else if (sourceFormat.getEncoding() instanceof ModEncoding && targetEncoding.equals(PCM_SIGNED)) {
            return new AudioFormat[] {
                    new AudioFormat(sourceFormat.getSampleRate(),
                            16,           // sample size in bits
                            sourceFormat.getChannels(),
                            true,                 // signed
                            false)                       // little endian (for PCM wav)
            };
        } else {
            return new AudioFormat[0];
        }
    }

    @Override
    public AudioInputStream getAudioInputStream(Encoding targetEncoding, AudioInputStream sourceStream) {
        if (isConversionSupported(targetEncoding, sourceStream.getFormat())) {
            AudioFormat[] formats = getTargetFormats(targetEncoding, sourceStream.getFormat());
            if (formats != null && formats.length > 0) {
                AudioFormat sourceFormat = sourceStream.getFormat();
                AudioFormat targetFormat = formats[0];
                if (sourceFormat.equals(targetFormat)) {
                    return sourceStream;
                } else if (sourceFormat.getEncoding() instanceof ModEncoding && targetFormat.getEncoding().equals(PCM_SIGNED)) {
                    try {
                        return new Mod2PcmAudioInputStream(sourceFormat, targetFormat, NOT_SPECIFIED, targetFormat.properties());
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                } else if (sourceFormat.getEncoding().equals(PCM_SIGNED) && targetFormat.getEncoding() instanceof ModEncoding) {
                    throw new IllegalArgumentException("unable to convert " + sourceFormat + " to " + targetFormat);
                } else {
                    throw new IllegalArgumentException("unable to convert " + sourceFormat + " to " + targetFormat.toString());
                }
            } else {
                throw new IllegalArgumentException("target format not found");
            }
        } else {
            throw new IllegalArgumentException("conversion not supported");
        }
    }

    @Override
    public AudioInputStream getAudioInputStream(AudioFormat targetFormat, AudioInputStream sourceStream) {
        if (isConversionSupported(targetFormat, sourceStream.getFormat())) {
            AudioFormat[] formats = getTargetFormats(targetFormat.getEncoding(), sourceStream.getFormat());
            if (formats != null && formats.length > 0) {
                AudioFormat sourceFormat = sourceStream.getFormat();
                if (sourceFormat.equals(targetFormat)) {
                    return sourceStream;
                } else if (sourceFormat.getEncoding() instanceof ModEncoding &&
                        targetFormat.getEncoding().equals(PCM_SIGNED)) {
                    try {
                        return new Mod2PcmAudioInputStream(sourceFormat, targetFormat, NOT_SPECIFIED, targetFormat.properties());
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                } else if (sourceFormat.getEncoding().equals(PCM_SIGNED) && targetFormat.getEncoding() instanceof ModEncoding) {
                    throw new IllegalArgumentException("unable to convert " + sourceFormat + " to " + targetFormat);
                } else {
                    throw new IllegalArgumentException("unable to convert " + sourceFormat + " to " + targetFormat);
                }
            } else {
                throw new IllegalArgumentException("target format not found");
            }
        } else {
            throw new IllegalArgumentException("conversion not supported");
        }
    }
}
