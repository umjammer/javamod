/*
 * Copyright (c) 2024 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.sampled.mod;

import java.util.Arrays;
import javax.sound.sampled.AudioFormat;

import vavi.util.Debug;


/**
 * Encodings used by the mod audio decoder.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 241122 nsano initial version <br>
 */
public class ModEncoding extends AudioFormat.Encoding {

    public static final ModEncoding STK = new ModEncoding("STK");
    public static final ModEncoding NST = new ModEncoding("NST");
    public static final ModEncoding MOD = new ModEncoding("MOD");
    public static final ModEncoding WOW = new ModEncoding("WOW");
    public static final ModEncoding XM = new ModEncoding("XM");
    public static final ModEncoding FAR = new ModEncoding("FAR");
    public static final ModEncoding MTM = new ModEncoding("MTM");
    public static final ModEncoding STM = new ModEncoding("STM");
    public static final ModEncoding STS = new ModEncoding("STS");
    public static final ModEncoding STX = new ModEncoding("STX");
    public static final ModEncoding S3M = new ModEncoding("S3M");
    public static final ModEncoding IT = new ModEncoding("IT");
    public static final ModEncoding MPTM = new ModEncoding("MPTM");
    public static final ModEncoding PowerPacker = new ModEncoding("PowerPacker");

    public static final ModEncoding ROL = new ModEncoding("ROL");
    public static final ModEncoding LAA = new ModEncoding("LAA");
    public static final ModEncoding CMF = new ModEncoding("CMF");
    public static final ModEncoding DRO = new ModEncoding("DRO");
    public static final ModEncoding SCI = new ModEncoding("SCI");

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
            STK, NST, MOD, WOW, XM, FAR, MTM, STM, STS, STX, S3M, IT, MPTM, PowerPacker,
            ROL, LAA, CMF, DRO, SCI,
            SID
    };

    public static ModEncoding valueOf(String name) {
Debug.println(name);
        return Arrays.stream(encodings).filter(e -> name.equalsIgnoreCase(e.toString())).findFirst().get();
    }
}
