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
import javax.sound.sampled.AudioFileFormat;

import de.quippy.javamod.multimedia.MultimediaContainer;

import static java.lang.System.getLogger;


/**
 * FileFormatTypes used by the mod audio decoder.
 *
 * TODO sub type for each
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 241122 nsano initial version <br>
 */
public class ModFileFormatType extends AudioFileFormat.Type {

    private static final Logger logger = getLogger(ModFileFormatType.class.getName());

    /**
     * Constructs a file type.
     *
     * @param name      the name of the emulator audio File Format.
     * @param extension the file extension for this emulator audio File Format.
     */
    public ModFileFormatType(String name, String extension) {
        super(name, extension);
    }

    private static final List<ModFileFormatType> types = new ArrayList<>();

    static {
        for (var fileFormat : ServiceLoader.load(MultimediaContainer.class)) {
            if (fileFormat.getType() != null) {
                types.add((ModFileFormatType) fileFormat.getType());
            }
        }
    }

    public static ModFileFormatType valueOf(String name) {
        return types.stream().filter(t -> name.equalsIgnoreCase(t.toString())).findFirst().orElseThrow();
    }
}
