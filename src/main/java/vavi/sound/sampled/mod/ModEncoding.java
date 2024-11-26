/*
 * Copyright (c) 2024 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.sampled.mod;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Arrays;
import javax.sound.sampled.AudioFormat;

import static java.lang.System.getLogger;


/**
 * Encodings used by the mod audio decoder.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 241122 nsano initial version <br>
 */
public class ModEncoding extends AudioFormat.Encoding {

    private static final Logger logger = getLogger(ModEncoding.class.getName());

    public static final ModEncoding MOD = new ModEncoding("MOD");
    public static final ModEncoding OPL3 = new ModEncoding("OPL3");
    public static final ModEncoding SID = new ModEncoding("SID");

    /**
     * Constructs a new encoding.
     *
     * @param name Name of the mod audio encoding.
     */
    private ModEncoding(String name) {
        super(name);
    }

    static final ModEncoding[] encodings = {
            MOD, OPL3, SID
    };

    public static ModEncoding valueOf(String name) {
logger.log(Level.DEBUG, name);
        return Arrays.stream(encodings).filter(e -> name.equalsIgnoreCase(e.toString())).findFirst().get();
    }
}
