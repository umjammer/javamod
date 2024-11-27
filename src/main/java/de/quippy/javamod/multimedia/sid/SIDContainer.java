/*
 * @(#) SIDContainer.java
 *
 * Created on 04.10.2009 by Daniel Becker
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

package de.quippy.javamod.multimedia.sid;

import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URL;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import javax.swing.JPanel;

import de.quippy.javamod.io.SpiModfileInputStream;
import de.quippy.javamod.mixer.Mixer;
import de.quippy.javamod.multimedia.MultimediaContainer;
import de.quippy.javamod.multimedia.MultimediaContainerEvent;
import de.quippy.javamod.multimedia.MultimediaContainerManager;
import de.quippy.javamod.multimedia.SpiMultimediaContainer;
import libsidplay.sidtune.SidTune;
import libsidplay.sidtune.SidTuneError;
import libsidplay.sidtune.SidTuneInfo;

import static java.lang.System.getLogger;


/**
 * @author Daniel Becker
 * @since 04.10.2009
 */
public class SIDContainer extends MultimediaContainer implements SpiMultimediaContainer {

    private static final Logger logger = getLogger(SIDContainer.class.getName());

    /** these are copied from libsidplay.components.sidtune.defaultFileNameExt */
    private static final String[] SIDFILEEXTENSION = {
            // Preferred default file extension for single-file sidtunes
            // or sidtune description files in SIDPLAY INFOFILE format.
            "sid",
            // Common file extension for single-file sidtunes due to SIDPLAY/DOS
            // displaying files *.DAT in its file selector by default.
            // Originally this was intended to be the extension of the raw data
            // file
            // of two-file sidtunes in SIDPLAY INFOFILE format.
            "dat",
            // Extension of Amiga Workbench tooltype icon info files, which
            // have been cut to MS-DOS file name length (8.3).
            "inf"
    };
    public static final String PROPERTY_SID_FREQUENCY = "javamod.player.sid.frequency";
    public static final String PROPERTY_SID_MODEL = "javamod.player.sid.sidmodel";
    public static final String PROPERTY_SID_OPTIMIZATION = "javamod.player.sid.optimization";
    public static final String PROPERTY_SID_USEFILTER = "javamod.player.sid.usesidfilter";
    public static final String PROPERTY_SID_VIRTUALSTEREO = "javamod.player.sid.virtualstrereo";
    // GUI Constants
    public static final String DEFAULT_SAMPLERATE = "44100";
    public static final String DEFAULT_SIDMODEL = "0";
    public static final String DEFAULT_OPTIMIZATION = "1";
    public static final String DEFAULT_USEFILTER = "true";
    public static final String DEFAULT_VIRTUALSTEREO = "false";

    public static final String[] SAMPLERATE = {
            "8000", "11025", "16000", "22050", "33075", DEFAULT_SAMPLERATE, "48000", "96000"
    };
    public static final String[] SIDMODELS = {
            "best", "SID 6581 (old model)", "SID 8580 (new model)"
    };

    private Properties currentProps = null;

    private SidTune sidTune;
    private SIDMixer currentMixer;
    private SIDConfigPanel sidConfigPanel;
    private SIDInfoPanel sidInfoPanel;

    @Override
    public void setFileURL(URL sidFileUrl) {
        super.setFileURL(sidFileUrl);
        sidTune = loadSidTune(sidFileUrl);
        if (!MultimediaContainerManager.isHeadlessMode())
            ((SIDInfoPanel) getInfoPanel()).fillInfoPanelWith(getFileURL(), sidTune);
    }

    @Override
    public boolean isSupported(InputStream stream) {
        try {
            stream.mark(SpiModfileInputStream.MAX_BUFFER_SIZE);
            SidTune sidTune = SidTune.load("spi.stream", stream);
logger.log(Level.DEBUG, "sidTune: " + sidTune);
            return true;
        } catch (IOException | SidTuneError e) {
logger.log(Level.TRACE, e.getMessage(), e);
            return false;
        } finally {
            try {
                stream.reset();
            } catch (IOException e) {
logger.log(Level.TRACE, e);
            }
        }
    }

    /**
     * for javax.sound.spi
     * @since 3.9.7
     */
    @Override
    public void setInputStream(InputStream stream) throws IOException {
        try {
            sidTune = SidTune.load("spi.stream", stream); // TODO name
        } catch (SidTuneError e) {
logger.log(Level.DEBUG, e.getMessage(), e);
            throw new IOException(e);
        }
    }

    @Override
    public String getSongName() {
        if (sidTune != null)
            return getShortDescriptionFrom(sidTune);
        else
            return super.getSongName();
    }

    @Override
    public Map<String, Object> getSongInfosFor(URL url) {
        Map<String, Object> result = new HashMap<>();
        String songName = MultimediaContainerManager.getSongNameFromURL(url);
        long duration = -1;
        try {
            SidTune sidTune = loadSidTune(url);
            songName = getShortDescriptionFrom(sidTune);
            duration = sidTune.getInfo().getSongs() * 1000L;
        } catch (Throwable ignored) {
        }
        result.put("songName", songName);
        result.put("duration", duration);
        return result;
    }

    public void nameChanged() {
        if (!MultimediaContainerManager.isHeadlessMode())
            ((SIDInfoPanel) getInfoPanel()).fillInfoPanelWith(getFileURL(), sidTune);
        fireMultimediaContainerEvent(new MultimediaContainerEvent(this, MultimediaContainerEvent.SONG_NAME_CHANGED_OLD_INVALID, getSongName()));
    }

    @Override
    public boolean canExport() {
        return true;
    }

    /**
     * @param sidFileURL a sid url
     * @return a SIDTune
     * @since 11.10.2009
     */
    private static SidTune loadSidTune(URL sidFileURL) {
        try {
            return SidTune.load(Path.of(sidFileURL.toURI()).toFile());
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * @param sidTune a sid tune
     * @return short description
     * @since 12.02.2011
     */
    private static String getShortDescriptionFrom(SidTune sidTune) {
        SidTuneInfo info = sidTune.getInfo();
        String[] infoString = info.getInfoString().toArray(new String[0]);
        return infoString[0] + " [" + infoString[1] + "] " + info.getCurrentSong() + '/' + info.getSongs() + " (" + infoString[2] + ')';
    }

    @Override
    public Mixer createNewMixer() {
        configurationSave(currentProps); // fill with default values

        int frequency = Integer.parseInt(currentProps.getProperty(PROPERTY_SID_FREQUENCY, DEFAULT_SAMPLERATE));
        int sidModel = Integer.parseInt(currentProps.getProperty(PROPERTY_SID_MODEL, DEFAULT_SIDMODEL));
        int optimization = Integer.parseInt(currentProps.getProperty(PROPERTY_SID_OPTIMIZATION, DEFAULT_OPTIMIZATION));
        boolean useSIDFilter = Boolean.parseBoolean(currentProps.getProperty(PROPERTY_SID_USEFILTER, DEFAULT_USEFILTER));
        boolean isStereo = Boolean.parseBoolean(currentProps.getProperty(PROPERTY_SID_VIRTUALSTEREO, DEFAULT_VIRTUALSTEREO));

        currentMixer = new SIDMixer(sidTune, this, frequency, sidModel, optimization, useSIDFilter, isStereo);
        return currentMixer;
    }

    public SIDMixer getCurrentMixer() {
        return currentMixer;
    }

    @Override
    public void configurationChanged(Properties newProps) {
        if (currentProps == null) currentProps = new Properties();
        currentProps.setProperty(PROPERTY_SID_FREQUENCY, newProps.getProperty(PROPERTY_SID_FREQUENCY, DEFAULT_SAMPLERATE));
        currentProps.setProperty(PROPERTY_SID_MODEL, newProps.getProperty(PROPERTY_SID_MODEL, DEFAULT_SIDMODEL));
        currentProps.setProperty(PROPERTY_SID_USEFILTER, newProps.getProperty(PROPERTY_SID_USEFILTER, DEFAULT_USEFILTER));
        currentProps.setProperty(PROPERTY_SID_VIRTUALSTEREO, newProps.getProperty(PROPERTY_SID_VIRTUALSTEREO, DEFAULT_VIRTUALSTEREO));

        if (!MultimediaContainerManager.isHeadlessMode()) {
            SIDConfigPanel configPanel = (SIDConfigPanel) getConfigPanel();
            configPanel.configurationChanged(newProps);
        }
    }

    @Override
    public void configurationSave(Properties props) {
        if (currentProps == null) currentProps = new Properties();
        if (!MultimediaContainerManager.isHeadlessMode()) {
            SIDConfigPanel configPanel = (SIDConfigPanel) getConfigPanel();
            configPanel.configurationSave(currentProps);
        }

        if (props != null) {
            props.setProperty(PROPERTY_SID_FREQUENCY, (currentProps != null) ? currentProps.getProperty(PROPERTY_SID_FREQUENCY, DEFAULT_SAMPLERATE) : DEFAULT_SAMPLERATE);
            props.setProperty(PROPERTY_SID_MODEL, (currentProps != null) ? currentProps.getProperty(PROPERTY_SID_MODEL, DEFAULT_SIDMODEL) : DEFAULT_SIDMODEL);
            props.setProperty(PROPERTY_SID_USEFILTER, (currentProps != null) ? currentProps.getProperty(PROPERTY_SID_USEFILTER, DEFAULT_USEFILTER) : DEFAULT_USEFILTER);
            props.setProperty(PROPERTY_SID_VIRTUALSTEREO, (currentProps != null) ? currentProps.getProperty(PROPERTY_SID_VIRTUALSTEREO, DEFAULT_VIRTUALSTEREO) : DEFAULT_VIRTUALSTEREO);
        }
    }

    @Override
    public JPanel getInfoPanel() {
        if (sidInfoPanel == null) {
            sidInfoPanel = new SIDInfoPanel();
            sidInfoPanel.setParentContainer(this);
        }
        return sidInfoPanel;
    }

    @Override
    public JPanel getConfigPanel() {
        if (sidConfigPanel == null) {
            sidConfigPanel = new SIDConfigPanel();
            sidConfigPanel.setParentContainer(this);
        }
        return sidConfigPanel;
    }

    @Override
    public String[] getFileExtensionList() {
        return SIDFILEEXTENSION;
    }

    @Override
    public String getName() {
        return "SID-File";
    }

    @Override
    public void cleanUp() {
    }
}
