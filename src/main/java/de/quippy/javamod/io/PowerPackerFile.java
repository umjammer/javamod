/*
 * @(#) PowerPackerFile.java
 *
 * Created on 06.01.2010 by Daniel Becker
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

import java.io.IOException;


/**
 * This class will decompress the input from any inputStream into an internal
 * buffer with the powerpacker algorithem and give access to this buffer
 * as an RandomAccessInputStream
 *
 * @author Olivier Lapicque &lt;olivierl@jps.net&gt;
 * @author Daniel Becker
 * @since 06.01.2010
 * @see "https://github.com/USBhost/MX_FFmpeg/blob/2d04e5e816ea9f820bd406d21810a4d416f487b7/ffmpeg/JNI/libmodplug/src/mmcmp.cpp"
 */
public class PowerPackerFile {

    private final byte[] buffer;

    /**
     * Will read n bits from a file
     *
     * @author Daniel Becker
     * @since 06.01.2010
     */
    private static class BitBuffer {

        private final RandomAccessInputStream source;
        private int filePointer;
        private int bitCount;
        private int bitBuffer;

        public BitBuffer(RandomAccessInputStream source, int filePointer) {
            this.source = source;
            this.filePointer = filePointer;
            bitCount = 0;
            bitBuffer = 0;
        }

        public int getBits(int n) throws IOException {
            int result = 0;

            for (int i = 0; i < n; i++) {
                if (bitCount == 0) {
                    bitCount = 8;
                    if (filePointer > 0) filePointer--;
                    source.seek(filePointer);
                    bitBuffer = source.read();
                }
                result = (result << 1) | (bitBuffer & 1);
                bitBuffer >>= 1;
                bitCount--;
            }
            return result;
        }
    }

    /**
     * Constructor for PowerPackerInputStream
     */
    public PowerPackerFile(RandomAccessInputStream input) throws IOException {
        buffer = readAndUnpack(input);
    }

    /**
     * @return
     * @since 04.01.2011
     */
    public byte[] getBuffer() {
        return buffer;
    }

    /**
     * Will check for a power packer file
     *
     * @param input
     * @return true if this file is a powerpacker file
     * @throws IOException
     * @since 04.01.2011
     */
    public static boolean isPowerPacker(RandomAccessInputStream input) throws IOException {
        long pos = input.getFilePointer();
        input.seek(0);
        int PP20ID = input.read() << 24 | input.read() << 16 | input.read() << 8 | input.read();
        input.seek(pos);
        return PP20ID == 0x50503230;
    }

    /**
     * Will unpack powerpacker 2.0 packed contend while reading from the packed Stream
     * and unpacking into memory
     *
     * @param source
     * @param buffer
     * @throws IOException
     * @since 06.01.2010
     */
    private static void pp20DoUnpack(RandomAccessInputStream source, int srcLen, byte[] buffer, int dstLen) throws IOException {
        BitBuffer bitBuffer = new BitBuffer(source, srcLen - 4);
        source.seek(srcLen - 1);
        int skip = source.read();
        bitBuffer.getBits(skip);
        int nBytesLeft = dstLen;
        while (nBytesLeft > 0) {
            if (bitBuffer.getBits(1) == 0) {
                int n = 1;
                while (n <= nBytesLeft) {
                    int code = bitBuffer.getBits(2);
                    n += code;
                    if (code != 3) break;
                }
                if (n > nBytesLeft) n = nBytesLeft;
                for (int i = 0; i < n; i++) {
                    buffer[--nBytesLeft] = (byte) (bitBuffer.getBits(8) & 0xff);
                }
                if (nBytesLeft == 0) break;
            }

            int n = bitBuffer.getBits(2) + 1;
            source.seek(n + 3);
            int nbits = source.read();
            int nofs;
            if (n == 4) {
                nofs = bitBuffer.getBits((bitBuffer.getBits(1) != 0) ? nbits : 7);
                while (n <= nBytesLeft) {
                    int code = bitBuffer.getBits(3);
                    n += code;
                    if (code != 7) break;
                }
            } else {
                nofs = bitBuffer.getBits(nbits);
            }

            if (n > nBytesLeft) n = nBytesLeft;
            for (int i = 0; i <= n; i++) {
                buffer[nBytesLeft - 1] = (nBytesLeft + nofs < dstLen) ? buffer[nBytesLeft + nofs] : 0;
                if ((--nBytesLeft) == 0) break;
            }
        }
    }

    private static byte[] readAndUnpack(RandomAccessInputStream source) throws IOException {
        source.seek(0); // Just in case...
        int PP20ID = source.read() << 24 | source.read() << 16 | source.read() << 8 | source.read();
        int srcLen = (int) source.getLength();
        if (srcLen < 256 || PP20ID != 0x50503230) throw new IOException("Not a powerpacker file!");
        // Destination Length at the end of file:
        source.seek(srcLen - 4);
        int destLen = source.read() << 16 | source.read() << 8 | source.read();
        if (destLen < 512 || destLen > 0x400000 || destLen > (srcLen << 3))
            throw new IOException("Length of " + srcLen + " is not supported!");
        byte[] dstBuffer = new byte[destLen];
        pp20DoUnpack(source, srcLen, dstBuffer, destLen);
//        // Debug - write buffer to disc
//        try {
//            File f = new File("test.mod");
//            FileOutputStream outputStream = new FileOutputStream(f);
//            outputStream.write(dstBuffer);
//            outputStream.close();
//        } catch (Exception ex) {
//        }

        return dstBuffer;
    }
}
