/*
 * Copyright (c) 2024 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.sampled.mod;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import javax.sound.sampled.AudioFormat;

import de.quippy.javamod.io.SoundOutputStream;
import de.quippy.javamod.io.wav.WaveFile;
import de.quippy.javamod.mixer.dsp.AudioProcessor;


/**
 * The audio data queue from a mod mixer.
 * <p>
 * this class is made by very cutting corners way.
 * it's better to extract {@link de.quippy.javamod.mixer.Mixer#startPlayback} method.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2024-11-25 nsano initial version <br>
 */
public class ModSoundOutputStream implements SoundOutputStream {

    /** audio queue */
    private final BlockingDeque<byte[]> deque = new LinkedBlockingDeque<>();

    @Override
    public void open() {
    }

    @Override
    public void close() {
    }

    @Override
    public void closeAllDevices() {
    }

    @Override
    public boolean isInitialized() {
        return true;
    }

    @Override
    public void startLine(boolean flushOrDrain) {
    }

    @Override
    public void stopLine(boolean flushOrDrain) {
    }

    @Override
    public void flushLine() {
    }

    @Override
    public void drainLine() {
    }

    @Override
    public int getLineBufferSize() {
        return 8192;
    }

    @Override
    public void writeSampleData(byte[] samples, int start, int length) {
        deque.offer(Arrays.copyOfRange(samples, start, start + length));
//logger.log(Level.TRACE, "write: " + length + ", deque: " + deque.size() /* + "\n" + StringUtil.getDump(samples, 64) */);
    }

    /** pull datum in the queue */
    public byte[] readSampleData() throws InterruptedException {
        return deque.take();
    }

    /** if the queue has not filled enough, {@link #hasFinished} is raised before starting */
    public boolean hasStarted() {
//logger.log(Level.TRACE, "hasStarted::deque: " + deque.size());
        return deque.size() > 100; // TODO magic 100
    }

    /** means no more data in the queue */
    public boolean hasFinished() {
//logger.log(Level.TRACE, "hasFinished::deque: " + deque.size());
        return deque.isEmpty();
    }

    @Override
    public void setInternalFramePosition(long newPosition) {
    }

    @Override
    public long getFramePosition() {
        return 0;
    }

    @Override
    public void setVolume(float gain) {
    }

    @Override
    public void setBalance(float balance) {
    }

    @Override
    public void setAudioProcessor(AudioProcessor audioProcessor) {
    }

    @Override
    public void setExportFile(File exportFile) {
    }

    @Override
    public void setWaveExportFile(WaveFile waveExportFile) {
    }

    @Override
    public void setPlayDuringExport(boolean playDuringExport) {
    }

    @Override
    public void setKeepSilent(boolean keepSilent) {
    }

    @Override
    public void changeAudioFormatTo(AudioFormat newFormat) {
    }

    @Override
    public void changeAudioFormatTo(AudioFormat newFormat, int newSourceLineBufferSize) {
    }

    @Override
    public void setSourceLineBufferSize(int newSourceLineBufferSize) {
    }

    @Override
    public AudioFormat getAudioFormat() {
        return null;
    }

    @Override
    public boolean matches(SoundOutputStream otherStream) {
        return true;
    }
}
