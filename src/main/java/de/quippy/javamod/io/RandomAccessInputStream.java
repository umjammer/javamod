/*
 * @(#) RandomAccessInputStream.java
 *
 * Created on 10.09.2009 by Daniel Becker
 *
 *-----------------------------------------------------------------------
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 3 of the License, or
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

package de.quippy.javamod.io;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.UTFDataFormatException;

import de.quippy.javamod.system.Helpers;


/**
 * Interface for the RandomAccessInputStream that is used by ModfileInputStream
 *
 * @author Daniel Becker
 * @since 10.09.2009
 */
public interface RandomAccessInputStream {

    File getFile();

    // InputStream functions - normally implemented due to extending from InputStream

    int available() throws IOException;

    void close() throws IOException;

    void mark(int readlimit);

    boolean markSupported();

    int read() throws IOException;

    int read(byte[] b, int off, int len) throws IOException;

    default int read(byte[] b) throws IOException {
        if (b != null) return read(b, 0, b.length);
        else throw new NullPointerException("Buffer is null");
    }

    void reset() throws IOException;

    long skip(long n) throws IOException;

    // New functions

    long getFilePointer() throws IOException;

    void seek(long pos) throws IOException;

    default long getLength() throws IOException {
        return length();
    }

    default int skipBack(int n) throws IOException {
        long currentPos = getFilePointer();
        seek(currentPos - n);
        return (int) (currentPos - getFilePointer());
    }

    // RandomAccessFile functions

    default int skipBytes(int n) throws IOException {
        return (int) skip(n);
    }

    long length() throws IOException;

    // Read & conversion Methods

    default byte readByte() throws IOException {
        return (byte) this.read();
    }

    default boolean readBoolean() throws IOException {
        int ch = this.read();
        if (ch < 0) throw new EOFException();
        return (ch != 0);
    }

    default char readChar() throws IOException {
        int ch1 = this.read();
        int ch2 = this.read();
        if ((ch1 | ch2) < 0) throw new EOFException();
        return (char) ((ch1 << 8) | (ch2));
    }

    default short readShort() throws IOException {
        int ch1 = this.read();
        int ch2 = this.read();
        if ((ch1 | ch2) < 0) throw new EOFException();
        return (short) ((ch1 << 8) | (ch2));
    }

    default double readDouble() throws IOException {
        return Double.longBitsToDouble(this.readLong());
    }

    default float readFloat() throws IOException {
        return Float.intBitsToFloat(this.readInt());
    }

    default int readInt() throws IOException {
        int ch1 = this.read();
        int ch2 = this.read();
        int ch3 = this.read();
        int ch4 = this.read();
        if ((ch1 | ch2 | ch3 | ch4) < 0) throw new EOFException();
        return ((ch1 << 24) | (ch2 << 16) | (ch3 << 8) | (ch4));
    }

    default String readLine() throws IOException {
        StringBuilder input = new StringBuilder();
        int c = -1;
        boolean eol = false;

        while (!eol) {
            switch (c = read()) {
                case -1:
                case '\n':
                    eol = true;
                    break;
                case '\r':
                    eol = true;
                    long cur = getFilePointer();
                    if ((read()) != '\n') seek(cur);
                    break;
                default:
                    input.append((char) c);
                    break;
            }
        }

        if ((c == -1) && (input.isEmpty())) {
            return null;
        }
        return input.toString();
    }

    default long readLong() throws IOException {
        return ((long) (readInt()) << 32) + (readInt() & 0xffff_ffffL);
    }

    default int readUnsignedByte() throws IOException {
        int ch = this.read();
        if (ch < 0) throw new EOFException();
        return ch;
    }

    default int readUnsignedShort() throws IOException {
        int ch1 = this.read();
        int ch2 = this.read();
        if ((ch1 | ch2) < 0) throw new EOFException();
        return (ch1 << 8) | (ch2);
    }

    default String readUTF() throws IOException {
        int utflen = this.readUnsignedShort();
        byte[] bytearr = new byte[utflen];
        char[] chararr = new char[utflen];

        int c, char2, char3;
        int count = 0;
        int chararr_count = 0;

        read(bytearr, 0, utflen);

        while (count < utflen) {
            c = (int) bytearr[count] & 0xff;
            if (c > 127) break;
            count++;
            chararr[chararr_count++] = (char) c;
        }

        while (count < utflen) {
            c = (int) bytearr[count] & 0xff;
            switch (c >> 4) {
                case 0:
                case 1:
                case 2:
                case 3:
                case 4:
                case 5:
                case 6:
                case 7:
                    /* 0xxxxxxx */
                    count++;
                    chararr[chararr_count++] = (char) c;
                    break;
                case 12:
                case 13:
                    /* 110x xxxx 10xx xxxx */
                    count += 2;
                    if (count > utflen) throw new UTFDataFormatException("malformed input: partial character at end");
                    char2 = bytearr[count - 1];
                    if ((char2 & 0xC0) != 0x80)
                        throw new UTFDataFormatException("malformed input around byte " + count);
                    chararr[chararr_count++] = (char) (((c & 0x1F) << 6) | (char2 & 0x3F));
                    break;
                case 14:
                    /* 1110 xxxx 10xx xxxx 10xx xxxx */
                    count += 3;
                    if (count > utflen) throw new UTFDataFormatException("malformed input: partial character at end");
                    char2 = bytearr[count - 2];
                    char3 = bytearr[count - 1];
                    if (((char2 & 0xC0) != 0x80) || ((char3 & 0xC0) != 0x80))
                        throw new UTFDataFormatException("malformed input around byte " + (count - 1));
                    chararr[chararr_count++] = (char) (((c & 0x0F) << 12) | ((char2 & 0x3F) << 6) | ((char3 & 0x3F) << 0));
                    break;
                default:
                    /* 10xx xxxx, 1111 xxxx */
                    throw new UTFDataFormatException("malformed input around byte " + count);
            }
        }
        // The number of chars produced may be less than utflen
        return new String(chararr, 0, chararr_count);
    }

    // added service methods for conversion

    /**
     * @param strLength
     * @return a String
     * @throws IOException when an io error occurs
     * @since 31.12.2007
     */
    default String readString(int strLength) throws IOException {
        byte[] buffer = new byte[strLength];
        int read = read(buffer, 0, strLength);
        return Helpers.retrieveAsString(buffer, 0, read);
    }

    /**
     * @return
     * @throws IOException when an io error occurs
     * @since 05.08.2020
     */
    default float readIntelFloat() throws IOException {
        return Float.intBitsToFloat(readIntelDWord());
    }

    /**
     * @return
     * @throws IOException when an io error occurs
     * @since 05.08.2020
     */
    default double readIntelDouble() throws IOException {
        return Double.longBitsToDouble(readIntelLong());
    }

    /**
     * @since 31.12.2007
     */
    default int readMotorolaUnsignedWord() throws IOException {
        return (((readByte() & 0xff) << 8) | (readByte() & 0xff)) & 0xffff;
    }

    /**
     * @since 31.12.2007
     */
    default int readIntelUnsignedWord() throws IOException {
        return ((readByte() & 0xff) | ((readByte() & 0xff) << 8)) & 0xffff;
    }

    /**
     * @since 31.12.2007
     */
    default short readMotorolaWord() throws IOException {
        return (short) ((((readByte() & 0xff) << 8) | (readByte() & 0xff)) & 0xffff);
    }

    /**
     * @since 31.12.2007
     */
    default short readIntelWord() throws IOException {
        return (short) (((readByte() & 0xff) | ((readByte() & 0xff) << 8)) & 0xffff);
    }

    /**
     * @since 31.12.2007
     */
    default int readMotorolaDWord() throws IOException {
        return ((readByte() & 0xff) << 24) | ((readByte() & 0xff) << 16) | ((readByte() & 0xff) << 8) | (readByte() & 0xff);
    }

    /**
     * @since 31.12.2007
     */
    default int readIntelDWord() throws IOException {
        return (readByte() & 0xff) | ((readByte() & 0xff) << 8) | ((readByte() & 0xff) << 16) | ((readByte() & 0xff) << 24);
    }

    /**
     * @return
     * @throws IOException when an io error occurs
     * @since 05.08.2020
     */
    default long readMotorolaLong() throws IOException {
        return (((long) readMotorolaDWord()) << 32) | (((long) readMotorolaDWord()) & 0xffff_ffff);
    }

    /**
     * @return
     * @throws IOException when an io error occurs
     * @since 05.08.2020
     */
    default long readIntelLong() throws IOException {
        return (((long) readIntelDWord()) & 0xffff_ffff) | (((long) readIntelDWord()) << 32);
    }

    /**
     * Will read size bytes from a stream and convert that to an integer value of
     * type byte (1), short (2), int (4), long(8).
     * Sizes bigger than 8 will be ignored, but "size" bytes will be skipped.
     *
     * @param size
     * @return
     * @throws IOException when an io error occurs
     * @since 03.02.2024
     */
    default long readMotorolaBytes(int size) throws IOException {
        long result = 0;
        if (size != 0) {
            int readBytes = (size > 8) ? 8 : size;
            for (int i = 0; i < readBytes; i++)
                result = (result << 8) | (read() & 0xff);
            skip(size - readBytes);
        }
        return result;
    }

    /**
     * Will read size bytes from a stream and convert that to an integer value of
     * type byte (1), short (2), int (4), long(8).
     * Sizes bigger than 8 will be ignored, but "size" bytes will be skipped.
     *
     * @param size
     * @return
     * @throws IOException when an io error occurs
     * @since 03.02.2024
     */
    default long readIntelBytes(int size) throws IOException {
        long result = 0;
        if (size != 0) {
            int readBytes = (size > 8) ? 8 : size;
            int shift = 0;
            for (int i = 0; i < readBytes; i++) {
                result |= (read() << shift);
                shift += 8;
            }
            skip(size - readBytes);
        }
        return result;
    }
}
