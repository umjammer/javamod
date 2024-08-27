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

import java.io.File;
import java.io.IOException;


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

    int read(byte[] b) throws IOException;

    void reset() throws IOException;

    long skip(long n) throws IOException;

    // New functions
    long getFilePointer() throws IOException;

    void seek(long pos) throws IOException;

    byte readByte() throws IOException;

    long getLength() throws IOException;

    int skipBack(int n) throws IOException;

    // RandomAccessFile functions
    int skipBytes(int n) throws IOException;

    long length() throws IOException;

    boolean readBoolean() throws IOException;

    char readChar() throws IOException;

    short readShort() throws IOException;

    double readDouble() throws IOException;

    float readFloat() throws IOException;

    int readInt() throws IOException;

    String readLine() throws IOException;

    long readLong() throws IOException;

    int readUnsignedByte() throws IOException;

    int readUnsignedShort() throws IOException;

    String readUTF() throws IOException;
}
