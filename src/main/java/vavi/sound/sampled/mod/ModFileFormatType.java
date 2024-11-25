/*
 * Copyright (c) 2024 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.sampled.mod;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import javax.sound.sampled.AudioFileFormat;

import static java.lang.System.getLogger;


/**
 * FileFormatTypes used by the mod audio decoder.
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

    public static final ModFileFormatType SID = new ModFileFormatType("SID", "sid");

    public static final ModFileFormatType ROL = new ModFileFormatType("ROL", "rol");
    public static final ModFileFormatType LAA = new ModFileFormatType("LAA", "laa");
    public static final ModFileFormatType CMF = new ModFileFormatType("CMF", "cmf");
    public static final ModFileFormatType DRO = new ModFileFormatType("DRO", "dro");
    public static final ModFileFormatType SCI = new ModFileFormatType("SCI", "sci");

    /**
     * Constructs a file type.
     *
     * @param name      the name of the emulator audio File Format.
     * @param extension the file extension for this emulator audio File Format.
     */
    private ModFileFormatType(String name, String extension) {
        super(name, extension);
    }

    private static final ModFileFormatType[] types = {MOD, SID, ROL, LAA, CMF, DRO, SCI};

    public static ModFileFormatType valueOf(String name, boolean isCompressed) {
logger.log(Level.TRACE, "name: " + name + ", isCompressed: " + isCompressed);
        return null; // TODO impl
    }
}
