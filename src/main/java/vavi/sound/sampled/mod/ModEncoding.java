/*
 * Copyright (c) 2024 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.sampled.mod;

import java.lang.System.Logger;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import javax.sound.sampled.AudioFormat;

import de.quippy.javamod.multimedia.MultimediaContainer;

import static java.lang.System.getLogger;


/**
 * Encodings used by the mod audio decoder.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 241122 nsano initial version <br>
 */
public class ModEncoding extends AudioFormat.Encoding {

    private static final Logger logger = getLogger(ModEncoding.class.getName());

    /**
     * Constructs a new encoding.
     *
     * @param name Name of the mod audio encoding.
     */
    public ModEncoding(String name) {
        super(name);
    }


    public static final List<ModEncoding> encodings = new ArrayList<>();

    static {
        for (var fileFormat : ServiceLoader.load(MultimediaContainer.class)) {
            if (fileFormat.getEncoding() != null) {
                encodings.add((ModEncoding) fileFormat.getEncoding());
            }
        }
    }

    public static ModEncoding valueOf(String name) {
        return encodings.stream().filter(e -> name.equalsIgnoreCase(e.toString())).findFirst().orElseThrow();
    }
}
