/*
 * Copyright (c) 2024 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package de.quippy.javamod.io;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;


/**
 * ModfileInputStream based on InputStream for javax.sound.spi.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2024-11-25 nsano initial version <br>
 * @since 3.9.6
 */
public class SpiModfileInputStream implements RandomAccessInputStream {

    public static final int MAX_BUFFER_SIZE = 20 * 1024 * 1024;

    private final InputStream stream;
    private int position;
    private final int total;

    /**
     * @param stream must be accepted mark size 20 MiB
     * @throws IllegalArgumentException if stream is not supported mark
     */
    public SpiModfileInputStream(InputStream stream) throws IOException {
        this.stream = stream;
        this.total = stream.available();
        this.position = 0;

        if (!stream.markSupported()) {
            throw new IllegalArgumentException("mark must be supported");
        }
        stream.mark(MAX_BUFFER_SIZE);
    }

    @Override
    public File getFile() {
        throw new UnsupportedOperationException("for spi not using file");
    }

    @Override
    public int available() throws IOException {
        return total - position;
    }

    @Override
    public void close() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void mark(int readlimit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    @Override
    public int read() throws IOException {
        int r = stream.read();
        if (r != -1) position += 1;
        return r;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int r = stream.read(b, off, len);
        if (r != -1) position += r;
        return r;
    }

    @Override
    public void reset() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public long skip(long n) throws IOException {
        long r = stream.skip(n);
        position += (int) r;
        return r;
    }

    @Override
    public long getFilePointer() throws IOException {
        return position;
    }

    @Override
    public void seek(long pos) throws IOException {
        stream.reset();
        stream.mark(MAX_BUFFER_SIZE);
        int l = 0;
        while (l < pos) {
            long r = skip(pos);
            l += (int) r;
        }
        position = (int) pos;
    }

    @Override
    public long length() throws IOException {
        return total;
    }
}
