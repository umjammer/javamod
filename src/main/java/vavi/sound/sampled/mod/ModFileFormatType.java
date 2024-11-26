/*
 * Copyright (c) 2024 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.sampled.mod;

import java.lang.System.Logger;
import java.util.Arrays;
import javax.sound.sampled.AudioFileFormat;

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
     * Specifies an emulator audio file.
     */
    public static final ModFileFormatType MOD = new ModFileFormatType("MOD", "stk,nst,mod,wow,xm,far,mtm,stm,sts,stx,s3m,it,mptm,powerpacker");

    /**
     * Specifies a sid audio file.
     */
    public static final ModFileFormatType SID = new ModFileFormatType("SID", "sid");

    /*
     * Specifies an opl3 audio file.
     */
    public static final ModFileFormatType OPL3 = new ModFileFormatType("OPL3", "rol,laa,cmf,dro,sci");

    /**
     * Constructs a file type.
     *
     * @param name      the name of the emulator audio File Format.
     * @param extension the file extension for this emulator audio File Format.
     */
    private ModFileFormatType(String name, String extension) {
        super(name, extension);
    }

    private static final ModFileFormatType[] types = {MOD, SID, OPL3};

    public static ModFileFormatType valueOf(String name) {
        return Arrays.stream(types).filter(t -> name.equalsIgnoreCase(t.toString())).findFirst().orElseThrow();
    }
}
