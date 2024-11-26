/*
 * @(#) OPL3Sequence.java
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
 */

package de.quippy.javamod.multimedia.opl3.sequencer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;

import de.quippy.javamod.io.RandomAccessInputStream;
import de.quippy.javamod.io.RandomAccessInputStreamImpl;
import de.quippy.javamod.io.SpiModfileInputStream;
import de.quippy.javamod.multimedia.opl3.emu.EmuOPL;
import de.quippy.javamod.multimedia.opl3.emu.EmuOPL.OplType;
import de.quippy.javamod.multimedia.opl3.emu.EmuOPL.Version;
import de.quippy.javamod.system.Helpers;


/**
 * @author Daniel Becker
 * @since 03.08.2020
 */
public abstract class OPL3Sequence {

    /**
     * Constructor for OPL3Sequence
     */
    public OPL3Sequence() {
    }

    /**
     * @param fileName opl file name
     * @return suitable sequencer
     */
    public static OPL3Sequence createOPL3Sequence(String fileName, String bnkFileName) throws IOException {
        return createOPL3Sequence(new File(fileName), new File(bnkFileName));
    }

    /**
     * @param file opl file
     * @return suitable sequencer
     */
    public static OPL3Sequence createOPL3Sequence(File file, File bnkFile) throws IOException {
        if (file == null || bnkFile == null) return null;
        return createOPL3Sequence(file.toURI().toURL(), bnkFile.toURI().toURL());
    }

    /**
     * @param url opl url
     * @return suitable sequencer
     * @throws IOException unsupported opl3
     */
    public static OPL3Sequence createOPL3Sequence(URL url, URL bnkURL) throws IOException {
        OPL3Sequence newSequence = getOPL3SequenceInstanceFor(url);
        if (newSequence != null) {
            newSequence.initExtra(bnkURL);
            RandomAccessInputStream inputStream = null;
            try {
                inputStream = new RandomAccessInputStreamImpl(url);
                newSequence.setURL(url);
                newSequence.readOPL3Sequence(inputStream);
            } finally {
                if (inputStream != null) try {
                    inputStream.close();
                } catch (Exception ex) { /* logger.log(Level.ERROR, "IGNORED", ex); */ }
            }
        } else
            throw new IOException("Unsupported OPL3 Sequence");
        return newSequence;
    }

    /**
     * for javax.sound.spi
     * @param stream opl stream
     * @return suitable sequencer
     * @throws IOException unsupported opl3
     * @since 3.9.7
     */
    public static OPL3Sequence createOPL3Sequence(InputStream stream, URL bnkURL) throws IOException {
        OPL3Sequence newSequence = getOPL3SequenceInstanceFor(stream);
        if (newSequence != null) {
            newSequence.initExtra(bnkURL);
            RandomAccessInputStream inputStream = null;
            try {
                inputStream = new SpiModfileInputStream(stream);
                newSequence.readOPL3Sequence(inputStream);
            } finally {
                if (inputStream != null) try {
                    inputStream.close();
                } catch (Exception ex) { /* logger.log(Level.ERROR, "IGNORED", ex); */ }
            }
        } else
            throw new IOException("Unsupported OPL3 Sequence");
        return newSequence;
    }

    /** most sequence doesn't need this, so not abstract but blank */
    protected void initExtra(URL bnkURL) throws IOException {
    }

    /**
     * This central method will know of all available sequence-types - hardcoded
     * We do not use a factory like we did with the mods. Highly flexible,
     * but sometimes a pain in the ass...
     *
     * @param url opl sequence url
     * @return suitable sequencer, nullable
     * @since 03.08.2020
     */
    private static OPL3Sequence getOPL3SequenceInstanceFor(URL url) {
        String extension = Helpers.getExtensionFromURL(url).toUpperCase();
        return sequences.stream().map(Provider::get).filter(s -> s.isSupportedExtension(extension)).findFirst().orElse(null);
    }

    /**
     * Gets suitable sequence by an extension
     * @throws java.util.NoSuchElementException no suitable sequence found
     */
    public static OPL3Sequence getOPL3SequenceInstanceFor(InputStream stream) {
        return sequences.stream().map(Provider::get).filter(s -> s.isSupported(stream)).findFirst().orElseThrow();
    }

    /** Gets suitable sequence by a stream */
    protected abstract boolean isSupportedExtension(String extension);

    /** must implement mark/reset inside this method */
    protected abstract boolean isSupported(InputStream stream);

    /**
     * @param opl set opl emulator
     * @since 03.08.2020
     */
    protected static void resetOPL(EmuOPL opl) {
        opl.resetOPL();
    }

    /**
     * @return length in [msec]
     * @since 03.08.2020
     */
    public long getLengthInMilliseconds() {
        double ms = 0d;
        EmuOPL measureOPL = EmuOPL.createInstance(Version.OPL3, 49712, getOPLType());
        initialize(measureOPL);
        while (updateToOPL(measureOPL) && ms < 60d * 60d * 1000d)
            ms += 1000d / getRefresh();
        return (long) (ms + 2000.5d);
    }

    /**
     * @param inputStream opl sequence input stream
     * @throws IOException when an error occurs
     * @since 03.08.2020
     */
    protected abstract void readOPL3Sequence(RandomAccessInputStream inputStream) throws IOException;

    /**
     * @param url opl sequence url
     * @since 03.08.2020
     */
    public abstract void setURL(URL url);

    /**
     * @param opl opl emulator
     * @return success or not
     * @since 03.08.2020
     */
    public abstract boolean updateToOPL(EmuOPL opl);

    /**
     * @param opl opl emulator
     * @since 03.08.2020
     */
    public abstract void initialize(EmuOPL opl);

    /**
     * @return refresh
     * @since 03.08.2020
     */
    public abstract double getRefresh();

    /**
     * @return song name
     * @since 03.08.2020
     */
    public abstract String getSongName();

    /**
     * @return author
     * @since 03.08.2020
     */
    public abstract String getAuthor();

    /**
     * @return description
     * @since 03.08.2020
     */
    public abstract String getDescription();

    /**
     * @return type name
     * @since 03.08.2020
     */
    public abstract String getTypeName();

    /**
     * Return the needed oplType: OPL2, DUAL_OPL2 or OPL3
     *
     * @return opl type
     * @since 16.08.2020
     */
    public abstract OplType getOPLType();

    /** spi for opl3 sequence */
    private static final ServiceLoader<OPL3Sequence> sequences;

    static {
        sequences = ServiceLoader.load(OPL3Sequence.class);
    }
}
