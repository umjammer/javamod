/*
 * @(#) EmuOPL3.java
 *
 * Created on 10.08.2020 by Daniel Becker
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

import de.quippy.opl3.OPL3;


/**
 * @author Daniel Becker
 * @since 10.08.2020
 */
public class EmuOPL3 extends EmuOPL {

    private final OPL3 opl3;
    private final int[] outBuffer;

    /**
     * Constructor for EmuOPL3
     *
     * @param ver
     * @param sampleRate
     */
    public EmuOPL3(Version ver, float sampleRate, OplType oplType) {
        super(ver, sampleRate, oplType);
        opl3 = new OPL3();
        outBuffer = new int[4];
    }

    @Override
    public void resetOPL() {
        for (int register = 0; register < 256; register++) {
            writeOPL3(0, register, 0);
            writeOPL3(1, register, 0);
        }
        if (oplType == OplType.OPL3)
            writeOPL3(1, 5, 1);
    }

    @Override
    public void read(int[] buffer) {
        buffer[0] = buffer[1] = 0;
        opl3.read(outBuffer, 1);
        for (int i = 0; i < 4; i++)
            buffer[i & 1] += outBuffer[i];
    }

    @Override
    public void writeOPL2(int reg, int value) {
        opl3.write(0, reg, value);
    }

    @Override
    public void writeDualOPL2(int bank, int reg, int value) {
        opl3.write(bank, reg, value);
    }

    @Override
    public void writeOPL3(int base, int reg, int value) {
        opl3.write(base, reg, value);
    }
}
