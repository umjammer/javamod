/*
 * @(#) EmuOPL.java
 *
 * Created on 08.08.2020 by Daniel Becker
 *
 *-----------------------------------------------------------------------
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *----------------------------------------------------------------------
 */

package de.quippy.javamod.multimedia.opl.emu;

import java.util.Arrays;


/**
 * @author Daniel Becker
 * @since 08.08.2020
 */
public abstract class EmuOPL {

    public enum Version {
        FMOPL_072_YM3526("YM3526 (OPL) V0.72  by Jarek Burczynski"),
        FMOPL_072_YM3812("YM3812 (OPL2) V0.72  by Jarek Burczynski"),
        OPL3("YMF262 (OPL3) V1.0.6 by Robson Cozendey");
        public final String versionName;

        Version(String versionName) {
            this.versionName = versionName;
        }

        public static String[] versionNames() {
            return Arrays.stream(values()).map(e -> e.versionName).toArray(String[]::new);
        }

        public static Version valueOf(int i) {
            if (0 <= i && i < values().length) return values()[i];
            else return null;
        }
    }

    public enum OplType {
        OPL2("OPL2"),
        DUAL_OPL2("Dual OPL2"),
        OPL3("OPL3");
        public final String oplTypeString;

        OplType(String oplTypeString) {
            this.oplTypeString = oplTypeString;
        }

        /** @return nullable */
        public static OplType valueOf(int i) {
            if (0 <= i && i < values().length) return values()[i];
            else return null;
        }
    }

    protected final float sampleRate;
    protected final Version ver;
    protected final OplType oplType;

    public static EmuOPL createInstance(Version ver, float sampleRate, OplType oplType) {
        return switch (ver) {
            case FMOPL_072_YM3526, FMOPL_072_YM3812 -> new EmuFMOPL_072(ver, sampleRate, oplType);
            case OPL3 -> new EmuOPL3(ver, sampleRate, oplType);
        };
    }

    /**
     * Constructor for EmuOPL
     */
    public EmuOPL(Version ver, float sampleRate, OplType oplType) {
        this.ver = ver;
        this.sampleRate = sampleRate;
        this.oplType = oplType;
    }

    /**
     * @return the sample rate
     * @since 16.08.2020
     */
    public float getSampleRate() {
        return sampleRate;
    }

    /**
     * @return the OPL CHIP type
     * @since 16.08.2020
     */
    public Version getVersion() {
        return ver;
    }

    /**
     * @return the oplType
     */
    public OplType getOPLType() {
        return oplType;
    }

    public abstract void resetOPL();

    public abstract void read(int[] buffer);

    public abstract void writeOPL2(int reg, int value);

    public abstract void writeDualOPL2(int bank, int reg, int value);

    public abstract void writeOPL3(int base, int reg, int value);
}
