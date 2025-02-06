/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package de.quippy.opl;

import uk.co.omgdrv.simplevgm.fm.MdFmProvider;


/**
 * Opl3Provider.
 * <p>
 * this class is YMF262 compatible.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-02-05 nsano initial version <br>
 */
public class YmF262Provider implements MdFmProvider {

    private final OPL3 ymf262 = new OPL3();

    private final int[] outBuffer = new int[4];

    @Override
    public void reset() {
        for (int register = 0; register < 256; register++) {
            ymf262.write(0, register, 0);
            ymf262.write(1, register, 0);
        }
        ymf262.write(1, 5, 1);
    }

    @Override
    public void init(int clock, int rate) {
    }

    @Override
    public void update(int[] buffer, int offset, int end) {
//logger.log(Level.TRACE, "update: " + offset + ", " + end);
        int L = 0x100;
        int y = (end + (L - 1)) / L;
        for (int x = 0; x < y; x++) {
            int z = x < y - 1 ? L : end % L;
            for (int i = 0; i < z; i++) {
                int p = offset * 2 + i * 2;
                buffer[p] = buffer[p + 1] = 0;
                ymf262.read(outBuffer, 1);
                for (int j = 0; j < 4; j++)
                    buffer[p + (j & 1)] += outBuffer[j];
//logger.log(Level.TRACE, "update[" + p + "]: " + buffer[p] + ", " + buffer[p + 1]);
            }
            offset += z;
        }
    }

    private int address;

    @Override
    public void writePort(int addr, int data) {
//logger.log(Level.TRACE, "write: " + addr + ", " + data);
        switch (addr) {
            case 0, 2 -> { this.address = data; }
            case 1, 3 -> { ymf262.write((addr & 2) << 7, address, data); }
        }
    }
}
