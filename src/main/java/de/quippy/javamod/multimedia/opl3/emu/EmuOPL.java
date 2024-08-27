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

package de.quippy.javamod.multimedia.opl3.emu;

/**
 * @author Daniel Becker
 * @since 08.08.2020
 */
public abstract class EmuOPL {

    public enum Version {FMOPL_072_YM3526, FMOPL_072_YM3812, OPL3}

    public static final String[] versionNames = {
            "YM3526 (OPL2) V0.72  by Jarek Burczynski",
            "YM3812 (OPL2) V0.72  by Jarek Burczynski",
            "YMF262 (OPL3) V1.0.6 by Robson Cozendey"
    };

    public enum OplType {OPL2, DUAL_OPL2, OPL3}

    public static final String[] oplTypeString = {
            "OPL2", "Dual OPL2", "OPL3"
    };

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

    public static int getIndexForVersion(Version ver) {
        return switch (ver) {
            case FMOPL_072_YM3526 -> 0;
            case FMOPL_072_YM3812 -> 1;
            case OPL3 -> 2;
        };
    }

    public static OplType getOPLTypeForIndex(int index) {
        return switch (index) {
            case 0 -> OplType.OPL2;
            case 1 -> OplType.DUAL_OPL2;
            case 2 -> OplType.OPL3;
            default -> null;
        };
    }

    public static int getIndexForOPLType(OplType OPLType) {
        return switch (OPLType) {
            case OPL2 -> 0;
            case DUAL_OPL2 -> 1;
            case OPL3 -> 2;
        };
    }

    public static Version getVersionForIndex(int index) {
        return switch (index) {
            case 0 -> Version.FMOPL_072_YM3526;
            case 1 -> Version.FMOPL_072_YM3812;
            case 2 -> Version.OPL3;
            default -> null;
        };
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
