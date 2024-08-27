/*
 * @(#) SoundOutputStream.java
 *
 * Created on 02.10.2010 by Daniel Becker
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
import javax.sound.sampled.AudioFormat;

import de.quippy.javamod.io.wav.WaveFile;
import de.quippy.javamod.mixer.dsp.AudioProcessor;


/**
 * @author Daniel Becker
 * This Interface describes a soundoutput stream for playback
 * @since 02.10.2010
 */
public interface SoundOutputStream {

    void open();

    void close();

    void closeAllDevices();

    boolean isInitialized();

    void startLine(boolean flushOrDrain);

    void stopLine(boolean flushOrDrain);

    void flushLine();

    void drainLine();

    int getLineBufferSize();

    void writeSampleData(byte[] samples, int start, int length);

    void setInternalFramePosition(long newPosition);

    long getFramePosition();

    void setVolume(float gain);

    void setBalance(float balance);

    void setAudioProcessor(AudioProcessor audioProcessor);

    void setExportFile(File exportFile);

    void setWaveExportFile(WaveFile waveExportFile);

    void setPlayDuringExport(boolean playDuringExport);

    void setKeepSilent(boolean keepSilent);

    void changeAudioFormatTo(AudioFormat newFormat);

    void changeAudioFormatTo(AudioFormat newFormat, int newSourceLineBufferSize);

    void setSourceLineBufferSize(int newSourceLineBufferSize);

    AudioFormat getAudioFormat();

    boolean matches(SoundOutputStream otherStream);
}
