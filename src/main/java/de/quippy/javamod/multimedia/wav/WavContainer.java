/*
 * @(#) WavContainer.java
 *
 * Created on 14.10.2007 by Daniel Becker
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

package de.quippy.javamod.multimedia.wav;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URL;
import java.util.Properties;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.swing.JPanel;

import de.quippy.javamod.io.FileOrPackedInputStream;
import de.quippy.javamod.mixer.Mixer;
import de.quippy.javamod.multimedia.MultimediaContainer;
import de.quippy.javamod.multimedia.MultimediaContainerManager;

import static java.lang.System.getLogger;


/**
 * @author Daniel Becker
 * @since 14.10.2007
 */
public class WavContainer extends MultimediaContainer {

    private static final Logger logger = getLogger(WavContainer.class.getName());

    private static final String[] wavefile_Extensions;

    private WavInfoPanel wavInfoPanel;
    private WavMixer currentMixer;

    /*
     * Will be executed during class load
     */
    static {
        AudioFileFormat.Type[] types = AudioSystem.getAudioFileTypes();
        wavefile_Extensions = new String[types.length];
        for (int i = 0; i < types.length; i++)
            wavefile_Extensions[i] = types[i].getExtension();
    }

    @Override
    public MultimediaContainer getInstance(URL waveFileUrl) {
        MultimediaContainer result = super.getInstance(waveFileUrl);
        AudioInputStream audioInputStream = null;
        try {
            audioInputStream = AudioSystem.getAudioInputStream(new FileOrPackedInputStream(waveFileUrl));
            if (!MultimediaContainerManager.isHeadlessMode())
                ((WavInfoPanel) getInfoPanel()).fillInfoPanelWith(audioInputStream, getSongName());
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            if (audioInputStream != null) try {
                audioInputStream.close();
            } catch (IOException ex) { logger.log(Level.TRACE, "IGNORED", ex); }
        }
        return result;
    }

    @Override
    public Object[] getSongInfosFor(URL url) {
        String songName = MultimediaContainerManager.getSongNameFromURL(url);
        long duration = -1;
        try {
            AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(new FileOrPackedInputStream(url));
            AudioFormat audioFormat = audioInputStream.getFormat();
            float frameRate = audioFormat.getFrameRate();
            if (frameRate != AudioSystem.NOT_SPECIFIED) {
                duration = (long) (((float) audioInputStream.getFrameLength() * 1000f / frameRate) + 0.5);
            } else {
                int channels = audioFormat.getChannels();
                int sampleSizeInBits = audioFormat.getSampleSizeInBits();
                int sampleSizeInBytes = sampleSizeInBits >> 3;
                int sampleRate = (int) audioFormat.getSampleRate();
                duration = ((long) audioInputStream.available() / ((long) sampleSizeInBytes) / (long) channels) * 1000L / (long) sampleRate;
            }
        } catch (Throwable ex) {
            logger.log(Level.TRACE, "IGNORED", ex);
        }
        return new Object[] {songName, duration};
    }

    @Override
    public boolean canExport() {
        return false;
    }

    @Override
    public JPanel getInfoPanel() {
        if (wavInfoPanel == null) {
            wavInfoPanel = new WavInfoPanel();
            wavInfoPanel.setParentContainer(this);
        }
        return wavInfoPanel;
    }

    @Override
    public JPanel getConfigPanel() {
        return null;
    }

    @Override
    public String[] getFileExtensionList() {
        return wavefile_Extensions;
    }

    @Override
    public String getName() {
        return "Wave-File";
    }

    @Override
    public void configurationChanged(Properties newProps) {
    }

    @Override
    public void configurationSave(Properties props) {
    }

    @Override
    public Mixer createNewMixer() {
        currentMixer = new WavMixer(getFileURL());
        return currentMixer;
    }

    @Override
    public void cleanUp() {
    }
}
