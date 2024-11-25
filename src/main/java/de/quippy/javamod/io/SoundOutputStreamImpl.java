/*
 * @(#) SoundOutputStreamImpl.java
 *
 * Created on 30.12.2007 by Daniel Becker
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
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;

import de.quippy.javamod.io.wav.WaveFile;
import de.quippy.javamod.mixer.dsp.AudioProcessor;
import de.quippy.javamod.system.Helpers;

import static java.lang.System.getLogger;


/**
 * This output stream will wrap audio lines and file-exports
 * so that the mixers do not have to think about it.
 *
 * @author Daniel Becker
 * @since 30.12.2007
 */
public class SoundOutputStreamImpl implements SoundOutputStream {

    private static final Logger logger = getLogger(SoundOutputStreamImpl.class.getName());

    protected AudioProcessor audioProcessor;
    protected AudioFormat audioFormat;
    protected File exportFile;

    protected float currentVolume;
    protected float currentBalance;

    protected SourceDataLine sourceLine;
    protected WaveFile waveExportFile;
    protected boolean playDuringExport;
    protected boolean keepSilent;
    protected int sourceLineBufferSize;

    public SoundOutputStreamImpl() {
        super();
    }

    /**
     * Constructor for SoundOutputStreamImpl
     *
     * @param audioFormat      the Format of delivered Audio
     * @param audioProcessor   the class of the audioProcessor - if any
     * @param exportFile       exportFile - the File to write to
     * @param playDuringExport if true, data will be sent to line and file
     * @param keepSilent       if true, 0 bytes will be sent to the line
     */
    public SoundOutputStreamImpl(AudioFormat audioFormat, AudioProcessor audioProcessor, File exportFile, boolean playDuringExport, boolean keepSilent) {
        this();
        this.audioFormat = audioFormat;
        this.audioProcessor = audioProcessor;
        this.exportFile = exportFile;
        this.playDuringExport = playDuringExport;
        this.keepSilent = keepSilent;
        this.sourceLineBufferSize = -1;
        this.currentVolume = 1.0f;
        this.currentBalance = 0.0f;
    }

    public SoundOutputStreamImpl(AudioFormat audioFormat, AudioProcessor audioProcessor, File exportFile, boolean playDuringExport, boolean keepSilent, int sourceLineBufferSize) {
        this(audioFormat, audioProcessor, exportFile, playDuringExport, keepSilent);
        this.sourceLineBufferSize = sourceLineBufferSize;
    }

    /**
     * @since 30.12.2007
     */
    protected synchronized void openSourceLine() {
        if (audioFormat != null) {
            try {
                closeSourceLine();
                closeAudioProcessor();
                DataLine.Info sourceLineInfo = new DataLine.Info(SourceDataLine.class, audioFormat);
                if (AudioSystem.isLineSupported(sourceLineInfo)) {
                    //sourceLineInfo.getFormats();
                    sourceLine = (SourceDataLine) AudioSystem.getLine(sourceLineInfo);
                    if (sourceLineBufferSize > 0)
                        sourceLine.open(audioFormat, sourceLineBufferSize);
                    else
                        sourceLine.open(audioFormat);
                    sourceLineBufferSize = sourceLine.getBufferSize();
                    sourceLine.start();
                    setVolume(currentVolume);
                    setBalance(currentBalance);
                    openAudioProcessor();
                } else
                    logger.log(Level.INFO, "Audio format is not supported");
            } catch (Exception ex) {
                sourceLine = null;
                logger.log(Level.ERROR, "Error occurred when opening audio device", ex);
            }
        }
    }

    /**
     * @since 30.12.2007
     */
    protected synchronized void openAudioProcessor() {
        if (audioProcessor != null) {
            if (sourceLine != null) {
                audioProcessor.initializeProcessor(sourceLine);
                audioProcessor.setUseInternalCounter(keepSilent);
            } else {
                audioProcessor.initializeProcessor(audioFormat);
                audioProcessor.setUseInternalCounter(true);
            }
        }
    }

    /**
     * @since 30.12.2007
     */
    protected synchronized void openExportFile() {
        if (exportFile != null) {
            waveExportFile = new WaveFile();
            int result = waveExportFile.openForWrite(exportFile, audioFormat);
            if (result != WaveFile.DDC_SUCCESS) {
                waveExportFile = null;
                logger.log(Level.ERROR, "Creation of export file was NOT successfull! " + exportFile.getAbsolutePath());
            }
        }
    }

    /**
     * @since 30.12.2007
     */
    protected synchronized void closeSourceLine() {
        if (sourceLine != null) {
            stopLine(true);
            // should be closed and null now
            if (sourceLine != null) {
                sourceLine.close();
                sourceLine = null;
            }
        }
    }

    /**
     * @since 30.12.2007
     */
    protected synchronized void closeAudioProcessor() {
        if (audioProcessor != null) audioProcessor.stop();
    }

    /**
     * @since 30.12.2007
     */
    protected synchronized void closeExportFile() {
        if (waveExportFile != null) waveExportFile.close();
    }

    /**
     * @since 30.12.2007
     */
    @Override
    public synchronized void open() {
        close();
        if (playDuringExport || exportFile == null) openSourceLine();
        openExportFile();
//        flushLine(); // might avoid "clutter" with PulseAudio, but did not do the trick!
    }

    /**
     * @since 30.12.2007
     */
    @Override
    public synchronized void close() {
        closeSourceLine();
        closeAudioProcessor();
        closeExportFile();
    }

    /**
     * @since 30.12.2007
     */
    @Override
    public synchronized void closeAllDevices() {
        close();
    }

    /**
     * @since 30.12.2007
     */
    @Override
    public synchronized boolean isInitialized() {
        return (sourceLine != null && sourceLine.isOpen()) || exportFile != null;
    }

    /**
     * @since 30.12.2007
     */
    @Override
    public synchronized void startLine(boolean flushOrDrain) {
        // if there is a line, flush or drain it
        if (sourceLine != null && flushOrDrain) {
            stopLine(flushOrDrain); // if running, drain or flush and close the line
        }
        // now start it - if sourceLine is null, open a new line
        // stopping sourceline will eventually make it null - so no ELSE here!
        if (sourceLine == null)
            openSourceLine();
        else
            sourceLine.start();
    }

    /**
     * @since 30.12.2007
     */
    @Override
    public synchronized void stopLine(boolean flushOrDrain) {
        if (sourceLine != null) {
            // play, whatever is left in the buffers. Caution! Will block, until everything is played
            if (flushOrDrain) {
                if (sourceLine.isOpen() && sourceLine.isRunning())
                    drainLine();
                else
                    flushLine();
            }
            sourceLine.stop();
            if (flushOrDrain) {
                sourceLine.close();
                sourceLine = null;
            }
        }
    }

    /**
     * Manually flush the line
     */
    @Override
    public void flushLine() {
        if (sourceLine != null) sourceLine.flush();
    }

    /**
     * BLOCKING Method!!!
     * Will play buffer, till empty
     * If a thread is still pumping data in or if the line is closed
     * draining will run forever - at least very long
     * No Check, if line is stopped, is done here!
     */
    @Override
    public void drainLine() {
        if (sourceLine != null) sourceLine.drain();
    }

    @Override
    public int getLineBufferSize() {
        if (sourceLine != null)
            return sourceLine.getBufferSize();
        else
            return -1;
    }

    /**
     * @param samples
     * @param start
     * @param length
     * @since 27.12.2011
     */
    protected synchronized void writeSampleDataInternally(byte[] samples, int start, int length) {
        if (sourceLine != null && !keepSilent) sourceLine.write(samples, start, length);
        if (waveExportFile != null) waveExportFile.writeSamples(samples, start, length);
    }

    @Override
    public synchronized void writeSampleData(byte[] samples, int start, int length) {
        if (audioProcessor != null) {
            int bytesToWrite = length;
            int startFrom = start;
            while (bytesToWrite > 0) {
                int anzSamples = audioProcessor.writeSampleData(samples, startFrom, bytesToWrite);
                writeSampleDataInternally(audioProcessor.getResultSampleBuffer(), 0, anzSamples);
                startFrom += anzSamples;
                bytesToWrite -= anzSamples;
            }
        } else
            writeSampleDataInternally(samples, start, length);
    }

    @Override
    public synchronized void setInternalFramePosition(long newFramePosition) {
        if (audioProcessor != null) audioProcessor.setInternalFramePosition(newFramePosition);
    }

    @Override
    public synchronized long getFramePosition() {
        if (audioProcessor != null) return audioProcessor.getFramePosition();
        else if (sourceLine != null) return sourceLine.getLongFramePosition();
        else
            return -1;
    }

    @Override
    public synchronized void setVolume(float gain) {
        currentVolume = gain;
        if (sourceLine != null && sourceLine.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl gainControl = (FloatControl) sourceLine.getControl(FloatControl.Type.MASTER_GAIN);
            float dB = (float) (Helpers.getDBValueFrom(gain));
            if (dB > gainControl.getMaximum()) dB = gainControl.getMaximum();
            else if (dB < gainControl.getMinimum()) dB = gainControl.getMinimum();
            gainControl.setValue(dB);
        }
    }

    @Override
    public synchronized void setBalance(float balance) {
        currentBalance = balance;
        if (sourceLine != null && sourceLine.isControlSupported(FloatControl.Type.BALANCE)) {
            FloatControl balanceControl = (FloatControl) sourceLine.getControl(FloatControl.Type.BALANCE);
            if (balance <= balanceControl.getMaximum() && balance >= balanceControl.getMinimum())
                balanceControl.setValue(balance);
        }
    }

    @Override
    public synchronized void setAudioProcessor(AudioProcessor audioProcessor) {
        this.audioProcessor = audioProcessor;
    }

    @Override
    public synchronized void setExportFile(File exportFile) {
        this.exportFile = exportFile;
    }

    @Override
    public synchronized void setWaveExportFile(WaveFile waveExportFile) {
        this.waveExportFile = waveExportFile;
    }

    @Override
    public synchronized void setPlayDuringExport(boolean playDuringExport) {
        this.playDuringExport = playDuringExport;
    }

    @Override
    public synchronized void setKeepSilent(boolean keepSilent) {
        this.keepSilent = keepSilent;
    }

    @Override
    public boolean matches(SoundOutputStream otherStream) {
        return getAudioFormat().matches(otherStream.getAudioFormat());
    }

    @Override
    public synchronized AudioFormat getAudioFormat() {
        return audioFormat;
    }

    @Override
    public synchronized void changeAudioFormatTo(AudioFormat newAudioFormat) {
        boolean reOpen = sourceLine != null && sourceLine.isOpen();
        close();
        audioFormat = newAudioFormat;
        if (reOpen) open();
    }

    @Override
    public synchronized void changeAudioFormatTo(AudioFormat newAudioFormat, int newSourceLineBufferSize) {
        sourceLineBufferSize = newSourceLineBufferSize;
        changeAudioFormatTo(newAudioFormat);
    }

    @Override
    public synchronized void setSourceLineBufferSize(int newSourceLineBufferSize) {
        sourceLineBufferSize = newSourceLineBufferSize;
        if (audioFormat != null) changeAudioFormatTo(audioFormat);
    }
}
