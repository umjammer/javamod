/*
 * @(#) DROSequence.java
 *
 * Created on 03.08.2020 by Daniel Becker
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
 *
 * As a proof of concept this was taken from dro.cpp and dro2.cpp
 * of the adplug project and ported to java.
 * Corrections and additions by to work with OPL3.java
 * 2008 Robson Cozendey
 * 2020 Daniel Becker
 */

package de.quippy.javamod.multimedia.opl.sequencer;

import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URL;
import java.util.Arrays;

import de.quippy.javamod.io.RandomAccessInputStream;
import de.quippy.javamod.multimedia.MultimediaContainerManager;
import de.quippy.javamod.multimedia.mod.ModConstants;
import de.quippy.javamod.multimedia.opl.emu.EmuOPL;
import de.quippy.javamod.multimedia.opl.emu.EmuOPL.OplType;
import de.quippy.javamod.system.Helpers;
import vavi.io.LittleEndianDataInputStream;

import static java.lang.System.getLogger;


/**
 * @author Daniel Becker
 * @since 03.08.2020
 */
public class DROSequence extends OPL3Sequence {

    private static final Logger logger = getLogger(DROSequence.class.getName());

    private URL url;
    private int[] data = null;

    private int version;
    private boolean isOldVersion;
    private String magic;
    private long lengthInMilliseconds;
    private int length;
    private OplType oplType;
    private int cmdDelayL;
    private int cmdDelayH;
    private int conversionTableLen;
    private int[] conversionTable;

    private String title;
    private String author;
    private String description;

    private int delay;
    private int pos;
    private int bank;

    protected static final String MAGIC = "DBRAWOPL";

    /**
     * Constructor for DROSequence
     */
    public DROSequence() {
        super();
    }

    @Override
    protected boolean isSupportedExtension(String extension) {
        return "DRO".equals(extension);
    }

    @Override
    protected boolean isSupported(InputStream stream) {
        LittleEndianDataInputStream dis = new LittleEndianDataInputStream(stream);
        try {
            dis.mark(12);

            byte[] id = new byte[8];
            dis.readFully(id);
            if (!Arrays.equals(MAGIC.getBytes(), id)) {
                return false;
            }

            int v = dis.readInt() & 0xffff;
logger.log(Level.DEBUG, "dro version: " + v);
            return v <= 2;
        } catch (IOException e) {
logger.log(Level.WARNING, e.getMessage(), e);
            return false;
        } finally {
            try {
                dis.reset();
            } catch (IOException e) {
logger.log(Level.DEBUG, e.toString());
            }
        }
    }

    @Override
    protected void readOPL3Sequence(RandomAccessInputStream inputStream) throws IOException {
        if (inputStream == null || inputStream.available() <= 0) return;

        byte[] magicBytes = new byte[8];
        inputStream.read(magicBytes, 0, 8);
        magic = Helpers.retrieveAsString(magicBytes, 0, 8);
        if (!magic.equals(MAGIC)) throw new IOException("Unsupported file type (unknown magic bytes)");

        version = inputStream.readIntelDWord();
logger.log(Level.DEBUG, "version: " + (version & 0xffff));
        if ((version & 0xffff) > 2) throw new IOException("Unsupported file type (unknown version " + version + ")");

        isOldVersion = ((version & 0xffff) < 2);

        if (!isOldVersion) {
            length = inputStream.readIntelDWord();
            length <<= 1;
            if (length <= 0 || length >= 1 << 30 || length > inputStream.available())
                throw new IOException("Unsupported file type (length read lied to us)");

            lengthInMilliseconds = inputStream.readIntelDWord();
            int OPLType = inputStream.read(); // OPL type (0 == OPL2, 1 == Dual OPL2, 2 == OPL3)
            oplType = EmuOPL.OplType.valueOf(OPLType);
            int format = inputStream.read();
            if (format != 0) throw new IOException("Unsupported file type (unknown format)");
            int compression = inputStream.read();
            if (compression != 0) throw new IOException("Unsupported file type (compression not supported)");
            cmdDelayL = inputStream.read();
            cmdDelayH = inputStream.read();
            conversionTableLen = inputStream.read();
            conversionTable = new int[conversionTableLen];
            for (int i = 0; i < conversionTableLen; i++)
                conversionTable[i] = inputStream.read();
        } else {
            lengthInMilliseconds = inputStream.readIntelDWord();
            length = inputStream.readIntelDWord();
            if (length < 3 || length > inputStream.available())
                throw new IOException("Unsupported file type (length read lied to us)");

            int OPLType = inputStream.read(); // OPL type (0 == OPL2, 1 == Dual OPL2, 2 == OPL3)
            oplType = EmuOPL.OplType.valueOf(OPLType);
            // constant values for cmdDelay
            cmdDelayL = 0x00;
            cmdDelayH = 0x01;
            // let's see if the next three bytes are zero...
            byte[] zero = new byte[3];
            inputStream.read(zero, 0, 3);
            if (zero[0] != 0 || zero[1] != 0 || zero[2] != 0) {
                // need these three bytes!
                inputStream.seek(inputStream.getFilePointer() - 3);
logger.log(Level.DEBUG, "not zero: " + Arrays.toString(zero));
            }
logger.log(Level.DEBUG, "mstotal: " + lengthInMilliseconds);
logger.log(Level.DEBUG, "length: " + length);
logger.log(Level.DEBUG, "oplType: " + oplType);
        }

        data = new int[length];
        for (int i = 0; i < length; i++) data[i] = inputStream.read();

        int tagSize = inputStream.available();
        if (tagSize >= 3) {
            byte[] tagMagic = new byte[2];
            inputStream.read(tagMagic, 0, 2);
            if (tagMagic[0] == (byte) 0xff && tagMagic[1] == (byte) 0xff) {
                for (int i = 0; i < 3; i++) { // three chunks
                    int what = inputStream.read();
                    if (what != -1) {
                        switch (what) {
                            case 0x1a:
                                title = inputStream.readString(40);
                                break;
                            case 0x1b:
                                author = inputStream.readString(40);
                                break;
                            case 0x1c:
                                description = inputStream.readString(1023);
                                break;
                        }
                    }
                }
            }
        }
    }

    @Override
    public String getSongName() {
        if (title != null && !title.isEmpty())
            return title;
        else
            return MultimediaContainerManager.getSongNameFromURL(url);
    }

    @Override
    public String getAuthor() {
        if (author != null && !author.isEmpty())
            return author;
        else
            return Helpers.EMPTY_STING;
    }

    private String getVersionString() {
        return "V" + (version & 0xffFF) + '.' + ((version >> 16) & 0xffFF);
    }

    @Override
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        if (description != null && !description.isEmpty()) sb.append(description).append("\n\nFile Informations:\n");
        sb.append("ID: ").append(magic).append('\n');
        sb.append("Version: ").append(getVersionString()).append('\n');
        sb.append("Length: ").append(length).append('\n');
        sb.append("length in ms (stored in file): ").append(Helpers.getTimeStringFromMilliseconds(lengthInMilliseconds)).append(" (").append(lengthInMilliseconds).append(")\n");
        sb.append("OPL Type: ").append(oplType.oplTypeString).append('\n');
        sb.append("Command delay small: 0x").append(ModConstants.getAsHex(cmdDelayL, 2)).append('\n');
        sb.append("Command delay high : 0x").append(ModConstants.getAsHex(cmdDelayH, 2)).append('\n');
        return sb.toString();
    }

    @Override
    public String getTypeName() {
        return "DOSBox Raw OPL File " + getVersionString();
    }

    @Override
    public void setURL(URL url) {
        this.url = url;
    }

    @Override
    public boolean updateToOPL(EmuOPL opl) {
        if (!isOldVersion) {
            while (pos < length) {
                int index = data[pos++] & 0xff;
                if (pos >= length) return false;
                int value = data[pos++] & 0xff;

                if (index == cmdDelayL) {
                    delay = value + 1;
                    return true;
                } else if (index == cmdDelayH) {
                    delay = (value + 1) << 8;
                    return true;
                } else {
                    bank = (index >> 7) & 0x01;
                    int reg = conversionTable[index & 0x7F] & 0xff;
                    if (oplType == OplType.OPL2)
                        opl.writeOPL2(reg, value);
                    else if (oplType == OplType.DUAL_OPL2) {
                        opl.writeDualOPL2(bank, reg, value);
                    } else
                        opl.writeOPL3(bank, reg, value);
                }
            }
        } else {
            while (pos < length) {
                int index = data[pos++] & 0xff;

                if (index == cmdDelayL) {
                    if (pos >= length) return false;
                    int value = data[pos++] & 0xff;
                    delay = value + 1;
                    return true;
                } else if (index == cmdDelayH) {
                    if (pos + 1 >= length) return false;
                    delay = (data[pos] | (data[pos + 1] << 8)) + 1;
                    pos += 2;
                    return true;
                } else if (index == 0x02 || index == 0x03) { // Bankswitch
                    bank = index - 0x02;
                } else {
                    if (index == 0x04) {
                        if (pos >= length) return false;
                        index = data[pos++];
                    }

                    if (pos >= length) return false;
                    int value = data[pos++] & 0xff;
logger.log(Level.TRACE, "%d, %d, %d, %02x".formatted(oplType.ordinal(), bank, index, value));
                    if (oplType == OplType.OPL2)
                        opl.writeOPL2(index, value);
                    else if (oplType == OplType.DUAL_OPL2) {
                        opl.writeDualOPL2(bank, index, value);
                    } else
                        opl.writeOPL3(bank, index, value);
                }
            }
        }
        return pos < length;
    }

    @Override
    public void initialize(EmuOPL opl) {
        pos = 0;
        delay = 0;
        bank = 0;
        resetOPL(opl);
    }

    @Override
    public double getRefresh() {
        if (delay != 0) return 1000d / (double) delay;
        else return 1000d;
    }

    @Override
    public OplType getOPLType() {
        return oplType;
    }
}
