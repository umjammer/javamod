/*
 * @(#) GaplessSoundOutputStreamImpl.java
 *
 * Created on 25.02.2011 by Daniel Becker
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
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import javax.sound.sampled.AudioFormat;

import de.quippy.javamod.mixer.dsp.AudioProcessor;

import static java.lang.System.getLogger;


/**
 * @author Daniel Becker
 * @since 25.02.2011
 */
public class GaplessSoundOutputStreamImpl extends SoundOutputStreamImpl {

    private static final Logger logger = getLogger(GaplessSoundOutputStreamImpl.class.getName());

    public GaplessSoundOutputStreamImpl() {
        super();
    }

    /**
     * @param audioFormat
     * @param audioProcessor
     * @param exportFile
     * @param playDuringExport
     * @param keepSilent
     * @since 25.02.2011
     */
    public GaplessSoundOutputStreamImpl(AudioFormat audioFormat, AudioProcessor audioProcessor, File exportFile, boolean playDuringExport, boolean keepSilent) {
        super(audioFormat, audioProcessor, exportFile, playDuringExport, keepSilent);
    }

    /**
     * This method will only create a new line if
     * a) an AudioFormat is set
     * and
     * b) no line is open
     * c) or the already open Line is not matching the audio format needed
     * After creating or reusing the line, status "open" and "running" are ensured
     *
     * @see de.quippy.javamod.io.SoundOutputStreamImpl#openSourceLine()
     * @since 27.02.2011
     */
    @Override
    protected synchronized void openSourceLine() {
        try {
            if (audioFormat != null && (sourceLine == null || (sourceLine != null && !sourceLine.getFormat().matches(audioFormat)))) {
                super.openSourceLine();
            } else if (sourceLine != null) {
                if (!sourceLine.isOpen()) sourceLine.open();
                if (!sourceLine.isRunning()) sourceLine.start();
            }
        } catch (Exception ex) {
            sourceLine = null;
            logger.log(Level.ERROR, "Error occurred when opening audio device", ex);
        }
    }

    /**
     * @since 27.02.2011
     */
    @Override
    public synchronized void open() {
        close();
        if (playDuringExport || exportFile == null)
            openSourceLine();
        else
            openAudioProcessor(); // open AudioProcessor (DSP-Effects) when only exporting
        openExportFile();
    }

    /**
     * @since 27.02.2011
     */
    @Override
    public synchronized void close() {
        // close Processor, when it was opened only for export
        if (!playDuringExport && exportFile != null) closeAudioProcessor();
        closeExportFile();
    }

    /**
     * This method is needed to close all devices as the gapless
     * stream does a close on the line only, if audio formats don't match
     *
     * @since 27.02.2011
     */
    @Override
    public synchronized void closeAllDevices() {
        super.close();
    }

    /**
     * @since 25.02.2011
     */
    @Override
    public synchronized void changeAudioFormatTo(AudioFormat newAudioFormat) {
        if (audioFormat == null || !audioFormat.matches(newAudioFormat)) {
            super.changeAudioFormatTo(newAudioFormat);
        }
    }
}
