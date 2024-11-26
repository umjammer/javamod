/*
 * @(#) RandomAccessInputStreamImpl.java
 *
 * Created on 31.12.2007 by Daniel Becker
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;

import de.quippy.javamod.system.Helpers;

import static java.lang.System.getLogger;


/**
 * This class maps the RandomAccessFile to an InputStream type of class.
 * You can also instantiate this class with a URL. If this URL is not of
 * protocol type "file://" the resource will get downloaded and written to
 * a tmp file. The tempfile will be deleted with calling of "close".
 * Furthermore this input stream will also handle zip compressed content
 * If nothing else works the fallback strategy is to use an internal fullFileCache.
 * This will be also used by PowerPacked Modes (see ModfileInputSteam)
 * <p>
 * Additionally, to speed up things, RandomAccessStreamImpl is provided with a
 * transparent buffer - changed on 18.01.22.
 *
 * @author Daniel Becker
 * @since 31.12.2007
 */
public class RandomAccessInputStreamImpl extends InputStream implements RandomAccessInputStream {

    private static final Logger logger = getLogger(RandomAccessInputStreamImpl.class.getName());

    private RandomAccessFile raFile = null;
    private File localFile = null;
    private File tmpFile = null;
    private int mark = 0;

    /* The readBuffer - 8K should be sufficient */
    private static final int STANDARD_LENGTH = 8192;
    /** the buffer */
    private byte[] randomAccessBuffer = null;
    /** end pointer, equals length / bytes read */
    private int randomAccessBuffer_endPointer = 0;
    /** the index into the buffer */
    private int randomAccessBuffer_readPointer = 0;
    /** position in file, start of buffer */
    private long randomAccessFilePosition = 0;
    /** the whole file length */
    private long randomAccessFileLength = 0;

    /* The fullFileCache */
    protected byte[] fullFileCache = null;
    protected int fullFileCache_readPointer = 0;
    protected int fullFileCache_length = 0;

    /**
     * Constructor for RandomAccessInputStreamImpl
     *
     * @param file file to access
     * @throws FileNotFoundException when the file not found
     */
    public RandomAccessInputStreamImpl(File file) throws IOException {
        if (!file.exists()) {
            file = unpackFromZIPFile(file.toURI().toURL());
        }
        openRandomAccessStream(localFile = file);
    }

    /**
     * Constructor for RandomAccessInputStreamImpl
     *
     * @param fileName file name to access
     * @throws FileNotFoundException when the file not found
     */
    public RandomAccessInputStreamImpl(String fileName) throws IOException {
        this(new File(fileName));
    }

    /** */
    public RandomAccessInputStreamImpl(URL fromUrl) throws IOException {
        if (Helpers.isFile(fromUrl)) {
            try {
                File file = new File(fromUrl.toURI());
                if (!file.exists()) {
                    file = unpackFromZIPFile(fromUrl);
                }
                openRandomAccessStream(localFile = file);
            } catch (URISyntaxException uriEx) {
                throw new MalformedURLException(uriEx.getMessage());
            }
        } else {
            InputStream inputStream = new FileOrPackedInputStream(fromUrl);

            try {
                tmpFile = copyFullStream(inputStream);
                try {
                    inputStream.close();
                } catch (IOException ex) { /* logger.log(Level.ERROR, "IGNORED", ex); */ }
                openRandomAccessStream(localFile = tmpFile);
            } catch (Throwable ex) {
                int size = inputStream.available();
                if (size < 1024) size = 1024;
                ByteArrayOutputStream out = new ByteArrayOutputStream(size);

                copyFullStream(inputStream, out);

                try {
                    inputStream.close();
                } catch (IOException e) { /* logger.log(Level.ERROR, "IGNORED", e); */ }
                out.close();

                fullFileCache = out.toByteArray();
                fullFileCache_length = fullFileCache.length;
                fullFileCache_readPointer = 0;
                raFile = null;
                localFile = null;
            }
        }
    }

    /**
     * Constructor for RandomAccessInputStreamImpl from a direct byte array
     *
     * @param fromByteArray source data
     * @throws IOException when an io error occurs
     */
    public RandomAccessInputStreamImpl(byte[] fromByteArray) throws IOException {
        fullFileCache = fromByteArray;
        fullFileCache_length = fullFileCache.length;
        fullFileCache_readPointer = 0;
        raFile = null;
        localFile = null;
    }

    /**
     * @param fromUrl source zip url
     * @return unpacked temporary file
     * @throws IOException when an io error occurs
     * @since 04.01.2011
     */
    private File unpackFromZIPFile(URL fromUrl) throws IOException {
        InputStream input = new FileOrPackedInputStream(fromUrl);
        return copyFullStream(input);
    }

    /**
     * @param input source
     * @return copied temporary file
     * @throws IOException when an io error occurs
     * @since 02.01.2011
     */
    private File copyFullStream(InputStream input) throws IOException {
        tmpFile = File.createTempFile("JavaMod", "ReadFile");
        tmpFile.deleteOnExit();

        FileOutputStream out = new FileOutputStream(tmpFile);
        copyFullStream(input, out);
        out.close();

        return tmpFile;
    }

    /**
     * @param inputStream source
     * @param out destination
     * @throws IOException when an io error occurs
     * @since 02.01.2008
     */
    private static void copyFullStream(InputStream inputStream, OutputStream out) throws IOException {
        byte[] input = new byte[STANDARD_LENGTH];
        int len;
        while ((len = inputStream.read(input, 0, STANDARD_LENGTH)) != -1) {
            out.write(input, 0, len);
        }
    }

    /**
     * Will return the local file this RandomAccessFile works on
     * or null using local fullFileCache and no file
     *
     * @return file to access
     * @since 09.01.2011
     */
    @Override
    public File getFile() {
        return localFile;
    }

    /**
     * @param theFile file to access
     * @throws IOException when an io error occurs
     * @since 18.01.2022
     */
    private void openRandomAccessStream(File theFile) throws IOException {
        raFile = new RandomAccessFile(theFile, "r");

        randomAccessBuffer = new byte[STANDARD_LENGTH];
        randomAccessFileLength = raFile.length();

        fillRandomAccessBuffer(0);
    }

    /**
     * @return end buffer pointer or -1 source file is not set
     * @throws IOException when an io error occurs
     * @since 18.01.2022
     */
    private int fillRandomAccessBuffer(long fromWhere) throws IOException {
        if (raFile != null) {
            if (fromWhere != raFile.getFilePointer()) raFile.seek(fromWhere);
            randomAccessFilePosition = raFile.getFilePointer();
            randomAccessBuffer_endPointer = raFile.read(randomAccessBuffer);
            randomAccessBuffer_readPointer = 0;
            return randomAccessBuffer_endPointer;
        }
        return -1;
    }

    /**
     * @param b buffer
     * @param off buffer offset
     * @param len read length
     * @return read count
     * @throws IOException when an io error occurs
     * @since 18.01.2022
     */
    private int readBytes_internal(byte[] b, int off, int len) throws IOException {
        if (randomAccessBuffer_endPointer < 0) return -1; // already at end of stream, nothing more to read!

        int read = len;
        while (read > 0 && randomAccessBuffer_endPointer >= 0) {
            int canRead = randomAccessBuffer_endPointer - randomAccessBuffer_readPointer;
            if (canRead > 0) {
                if (canRead > read) canRead = read;
                System.arraycopy(randomAccessBuffer, randomAccessBuffer_readPointer, b, off, canRead);
                randomAccessBuffer_readPointer += canRead;
                off += canRead;
                read -= canRead;
            }
            if (randomAccessBuffer_readPointer >= randomAccessBuffer_endPointer && read > 0) {
                fillRandomAccessBuffer(this.getFilePointer());
            }
        }
        return len - read;
    }

    /**
     * @return byte read
     * @throws IOException when an io error occurs
     * @since 18.01.2022
     */
    private int readByte_internal() throws IOException {
        if (randomAccessBuffer_readPointer >= randomAccessBuffer_endPointer) {
            int read = fillRandomAccessBuffer(this.getFilePointer());
            if (read == -1) return -1;
        }
        return ((int) randomAccessBuffer[randomAccessBuffer_readPointer++]) & 0xff;
    }

    @Override
    public int available() throws IOException {
        if (raFile != null)
            return (int) (randomAccessFileLength - this.getFilePointer());
        else
            return fullFileCache_length - fullFileCache_readPointer;
    }

    @Override
    public void close() throws IOException {
        if (raFile != null) raFile.close();
        super.close();
        if (tmpFile != null) {
            boolean ok = tmpFile.delete();
            if (!ok) logger.log(Level.ERROR, "Could not delete temporary file: " + tmpFile.getCanonicalPath());
        }

        raFile = null;
        tmpFile = null;
        randomAccessBuffer = null;
        randomAccessBuffer_endPointer = 0;
        randomAccessBuffer_readPointer = 0;
        randomAccessFilePosition = 0;
        randomAccessFileLength = 0;

        fullFileCache = null;
        fullFileCache_length = 0;
        fullFileCache_readPointer = 0;
    }

    @Override
    public synchronized void mark(int readlimit) {
        try {
            if (raFile != null)
                mark = (int) this.getFilePointer();
            else
                mark = fullFileCache_readPointer;
        } catch (IOException ex) {
        }
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    @Override
    public synchronized void reset() throws IOException {
        if (raFile != null)
            this.seek(mark);
        else
            fullFileCache_readPointer = mark;
    }

    @Override
    public long skip(long n) throws IOException {
        if (raFile != null) {
            if (n <= 0) return 0;
            long pos = randomAccessFilePosition + randomAccessBuffer_readPointer;
            long newpos = pos + n;
            if (newpos > randomAccessFileLength) newpos = randomAccessFileLength;
            this.seek(newpos);
            return newpos - pos;
        } else {
            if (n <= 0) return 0;
            int newpos = fullFileCache_readPointer + (int) n;
            if (newpos > fullFileCache_length) newpos = fullFileCache_length;
            int skipped = newpos - fullFileCache_readPointer;
            fullFileCache_readPointer = newpos;
            return skipped;
        }
    }

    @Override
    public long getFilePointer() throws IOException {
        if (raFile != null)
            return randomAccessFilePosition + randomAccessBuffer_readPointer;
        else
            return fullFileCache_readPointer;
    }

    @Override
    public void seek(long pos) throws IOException {
        if (raFile != null) {
            if (pos > (randomAccessFilePosition + randomAccessBuffer_endPointer) ||
                    pos < randomAccessFilePosition) {
                fillRandomAccessBuffer(pos);
            } else
                randomAccessBuffer_readPointer = (int) (pos - randomAccessFilePosition);
        } else
            fullFileCache_readPointer = (int) pos;
    }

    @Override
    public long length() throws IOException {
        if (raFile != null)
            return randomAccessFileLength;
        else
            return fullFileCache_length;
    }

    // ---- Core Read Methods

    @Override
    public int read() throws IOException {
        if (raFile != null)
            return readByte_internal();
        else
            return (fullFileCache_readPointer < fullFileCache_length) ? ((int) fullFileCache[fullFileCache_readPointer++]) & 0xff : -1;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (raFile != null)
            return readBytes_internal(b, off, len);
        else {
            if (b == null)
                throw new NullPointerException();
            if ((off < 0) || (off > b.length) || (len < 0) || ((off + len) > b.length) || ((off + len) < 0))
                throw new IndexOutOfBoundsException();
            if (fullFileCache_readPointer >= fullFileCache_length)
                return -1;
            if (fullFileCache_readPointer + len > fullFileCache_length)
                len = fullFileCache_length - fullFileCache_readPointer;
            if (len <= 0)
                return 0;
            System.arraycopy(fullFileCache, fullFileCache_readPointer, b, off, len);
            fullFileCache_readPointer += len;
            return len;
        }
    }
}
