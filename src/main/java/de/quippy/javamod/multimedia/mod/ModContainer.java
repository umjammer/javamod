/*
 * @(#)ModContainer.java
 *
 * Created on 12.10.2007 by Daniel Becker
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

package de.quippy.javamod.multimedia.mod;

import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import javax.swing.JPanel;

import de.quippy.javamod.io.SpiModfileInputStream;
import de.quippy.javamod.mixer.Mixer;
import de.quippy.javamod.multimedia.MultimediaContainer;
import de.quippy.javamod.multimedia.MultimediaContainerManager;
import de.quippy.javamod.multimedia.SpiMultimediaContainer;
import de.quippy.javamod.multimedia.mod.gui.ModInstrumentDialog;
import de.quippy.javamod.multimedia.mod.gui.ModPatternDialog;
import de.quippy.javamod.multimedia.mod.gui.ModSampleDialog;
import de.quippy.javamod.multimedia.mod.gui.SongUpdater;
import de.quippy.javamod.multimedia.mod.loader.Module;
import de.quippy.javamod.multimedia.mod.loader.ModuleFactory;
import de.quippy.javamod.system.Helpers;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.getLogger;


/**
 * @author Daniel Becker
 * @since 12.10.2007
 */
public class ModContainer extends MultimediaContainer implements SpiMultimediaContainer {

    private static final Logger logger = getLogger(ModContainer.class.getName());

    public static final String PROPERTY_PLAYER_BITSPERSAMPLE = "javamod.player.bitspersample";
    public static final String PROPERTY_PLAYER_STEREO = "javamod.player.stereo";
    public static final String PROPERTY_PLAYER_FREQUENCY = "javamod.player.frequency";
    public static final String PROPERTY_PLAYER_MSBUFFERSIZE = "javamod.player.msbuffersize";
    public static final String PROPERTY_PLAYER_ISP = "javamod.player.ISP";
    public static final String PROPERTY_PLAYER_WIDESTEREOMIX = "javamod.player.widestereomix";
    public static final String PROPERTY_PLAYER_NOISEREDUCTION = "javamod.player.noisereduction";
    public static final String PROPERTY_PLAYER_MEGABASS = "javamod.player.megabass";
    public static final String PROPERTY_PLAYER_DCREMOVAL = "javamod.player.dcremoval";
    public static final String PROPERTY_PLAYER_NOLOOPS = "javamod.player.noloops";
    public static final String PROPERTY_PLAYER_MAXNNACHANNELS = "javamod.player.max_nna_channels";
    public static final String PROPERTY_PLAYER_DITHERFILTER = "javamod.player.ditherfilter";
    public static final String PROPERTY_PLAYER_DITHERTYPE = "javamod.player.dithertype";
    public static final String PROPERTY_PLAYER_DITHERBYPASS = "javamod.player.ditherbypass";

    private static final String PROPERTY_PATTERN_POS = "javamod.player.position.patterns";
    private static final String PROPERTY_PATTERN_SIZE = "javamod.player.size.patterns";
    private static final String PROPERTY_PATTERN_VISABLE = "javamod.player.open.patterns";
    private static final String PROPERTY_SAMPLE_POS = "javamod.player.position.samples";
    private static final String PROPERTY_SAMPLE_SIZE = "javamod.player.size.samples";
    private static final String PROPERTY_SAMPLE_VISABLE = "javamod.player.open.samples";
    private static final String PROPERTY_INSTRUMENT_POS = "javamod.player.position.instruments";
    private static final String PROPERTY_INSTRUMENT_SIZE = "javamod.player.size.instruments";
    private static final String PROPERTY_INSTRUMENT_VISABLE = "javamod.player.open.instruments";

    // GUI Constants

    public static final String DEFAULT_BITSPERSAMPLE = "16";
    public static final String DEFAULT_CHANNEL = "2";
    public static final String DEFAULT_SAMPLERATE = "48000";
    public static final String DEFAULT_MSBUFFERSIZE = "30";
    public static final String DEFAULT_WIDESTEREOMIX = "false";
    public static final String DEFAULT_NOISEREDUCTION = "false";
    public static final String DEFAULT_MEGABASS = "true";
    public static final String DEFAULT_DCREMOVAL = "true";
    public static final String DEFAULT_NOLOOPS = "1";
    public static final String DEFAULT_MAXNNACHANNELS = "200";
    public static final String DEFAULT_INTERPOLATION_INDEX = "4"; // Integer.toString(ModConstants.INTERPOLATION_WINDOWSFIR);
    public static final String DEFAULT_DITHERFILTER = "4";
    public static final String DEFAULT_DITHERTYPE = "2";
    public static final String DEFAULT_DITHERBYPASS = "false";
    protected static final String[] SAMPLERATE = {
            "8000", "11025", "16000", "22050", "33075", "44100", DEFAULT_SAMPLERATE, "96000", "192000"
    };
    protected static final String[] CHANNELS = {
            "1", DEFAULT_CHANNEL
    };
    protected static final String[] BITSPERSAMPLE = {
            "8", DEFAULT_BITSPERSAMPLE, "24", "32"
    };
    protected static final String[] INTERPOLATION = {
            "None", "Linear", "Cubic", "Kaiser", "Windowed FIR"
    };
    protected static final String[] BUFFERSIZE = {
            "10", "15", "20", "25", DEFAULT_MSBUFFERSIZE, "40", "50", "75", "100", "150", "175", "200", "250", "500", "750"
    };
    protected static final String[] MAX_NNA_CHANNELS = {
            "25", "50", "75", "100", "125", "150", "175", DEFAULT_MAXNNACHANNELS, "225", "250", "275", "300", "325", "350", "375", "400", "1000"
    };

    private Properties currentProps = null;

    private Module currentMod;
    private ModMixer currentMixer;
    private ModInfoPanel modInfoPanel;
    private ModConfigPanel modConfigPanel;
    private SongUpdater songUpdater;

    @Override
    public void setFileURL(URL url) {
        super.setFileURL(url);
        try {
            // loading a new mod - so deregister this mixer and unwire listeners
            if (songUpdater != null) unwireListeners();

            currentMod = ModuleFactory.getInstance(url);
            if (!MultimediaContainerManager.isHeadlessMode())
                ((ModInfoPanel) getInfoPanel()).fillInfoPanelWith(currentMod);
        } catch (IOException ex) {
            currentMod = null;
            logger.log(Level.ERROR, "[ModContainer] Failed with loading of " + url.toString(), ex);
        }
    }

    @Override
    public boolean isSupported(InputStream stream) {
        try {
            Module mod = ModuleFactory.getModuleFromStream(stream);
logger.log(DEBUG, "mod: " + mod.getClass().getName());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * for javax.sound.spi
     * @since 3.9.6
     */
    @Override
    public void setInputStream(InputStream stream) throws IOException {
        currentMod = ModuleFactory.getModuleFromStream(stream);
        currentMod.loadModFile(new SpiModfileInputStream(stream));
    }

    @Override
    public String getSongName() {
        if (currentMod != null) {
            String songName = currentMod.getSongName();
            if (songName != null && !songName.trim().isEmpty())
                return songName;
        }
        return super.getSongName();
    }

    /**
     * @return gentleman agreement: Object[] { String::songname, Long::duration }
     */
    @Override
    public Map<String, Object> getSongInfosFor(URL url) {
        Map<String, Object> result = new HashMap<>();
        String songName = MultimediaContainerManager.getSongNameFromURL(url);
        long duration = -1;
        try {
            Module theMod = ModuleFactory.getInstance(url);
            String modSongName = theMod.getSongName();
            if (modSongName != null && !modSongName.trim().isEmpty()) songName = modSongName;
            // try to re-use an existing mixer, if its one for the same mod.
            // The "ModMixer::getLengthInMilliseconds" is synchronized to avoid double entry
            ModMixer theMixer = getCurrentMixer();
            if (theMixer == null || !theMixer.getMod().getFileName().equals(theMod.getFileName())) {
                int loopValue = Integer.parseInt((currentProps != null) ? currentProps.getProperty(PROPERTY_PLAYER_NOLOOPS, DEFAULT_NOLOOPS) : DEFAULT_NOLOOPS);
                if (loopValue == ModConstants.PLAYER_LOOP_DEACTIVATED) loopValue = ModConstants.PLAYER_LOOP_IGNORE;
                theMixer = new ModMixer(theMod, 8, 1, 22050, 0, false, false, false, false, loopValue, 0, 500, 0, 0, true);
            }
            duration = theMixer.getLengthInMilliseconds();
        } catch (Throwable ex) {
            /* NOOP */
        }
        result.put("songName", songName);
        result.put("duration", duration);
        return result;
    }

    @Override
    public boolean canExport() {
        return true;
    }

    /**
     * @since 13.10.2007
     */
    @Override
    public JPanel getInfoPanel() {
        if (modInfoPanel == null) {
            modInfoPanel = new ModInfoPanel();
            modInfoPanel.setParentContainer(this);
        }
        return modInfoPanel;
    }

    @Override
    public JPanel getConfigPanel() {
        if (modConfigPanel == null) {
            modConfigPanel = new ModConfigPanel();
            modConfigPanel.setParentContainer(this);
        }
        return modConfigPanel;
    }

    /**
     * @since 12.10.2007
     */
    @Override
    public String[] getFileExtensionList() {
        return ModuleFactory.getSupportedFileExtensions();
    }

    @Override
    public String getName() {
        return "Mod-File";
    }

    @Override
    public void configurationChanged(Properties newProps) {
        if (currentProps == null) currentProps = new Properties();
        currentProps.setProperty(PROPERTY_PLAYER_FREQUENCY, newProps.getProperty(PROPERTY_PLAYER_FREQUENCY, DEFAULT_SAMPLERATE));
        currentProps.setProperty(PROPERTY_PLAYER_MSBUFFERSIZE, newProps.getProperty(PROPERTY_PLAYER_MSBUFFERSIZE, DEFAULT_MSBUFFERSIZE));
        currentProps.setProperty(PROPERTY_PLAYER_BITSPERSAMPLE, newProps.getProperty(PROPERTY_PLAYER_BITSPERSAMPLE, DEFAULT_BITSPERSAMPLE));
        currentProps.setProperty(PROPERTY_PLAYER_STEREO, newProps.getProperty(PROPERTY_PLAYER_STEREO, DEFAULT_CHANNEL));
        currentProps.setProperty(PROPERTY_PLAYER_ISP, newProps.getProperty(PROPERTY_PLAYER_ISP, DEFAULT_INTERPOLATION_INDEX));
        currentProps.setProperty(PROPERTY_PLAYER_WIDESTEREOMIX, newProps.getProperty(PROPERTY_PLAYER_WIDESTEREOMIX, DEFAULT_WIDESTEREOMIX));
        currentProps.setProperty(PROPERTY_PLAYER_NOISEREDUCTION, newProps.getProperty(PROPERTY_PLAYER_NOISEREDUCTION, DEFAULT_NOISEREDUCTION));
        currentProps.setProperty(PROPERTY_PLAYER_MEGABASS, newProps.getProperty(PROPERTY_PLAYER_MEGABASS, DEFAULT_MEGABASS));
        currentProps.setProperty(PROPERTY_PLAYER_MEGABASS, newProps.getProperty(PROPERTY_PLAYER_DCREMOVAL, DEFAULT_DCREMOVAL));
        currentProps.setProperty(PROPERTY_PLAYER_NOLOOPS, newProps.getProperty(PROPERTY_PLAYER_NOLOOPS, DEFAULT_NOLOOPS));
        currentProps.setProperty(PROPERTY_PLAYER_MAXNNACHANNELS, newProps.getProperty(PROPERTY_PLAYER_MAXNNACHANNELS, DEFAULT_MAXNNACHANNELS));
        currentProps.setProperty(PROPERTY_PLAYER_DITHERFILTER, newProps.getProperty(PROPERTY_PLAYER_DITHERFILTER, DEFAULT_DITHERFILTER));
        currentProps.setProperty(PROPERTY_PLAYER_DITHERTYPE, newProps.getProperty(PROPERTY_PLAYER_DITHERTYPE, DEFAULT_DITHERTYPE));
        currentProps.setProperty(PROPERTY_PLAYER_DITHERBYPASS, newProps.getProperty(PROPERTY_PLAYER_DITHERBYPASS, DEFAULT_DITHERBYPASS));

        if (!MultimediaContainerManager.isHeadlessMode()) {
            ModConfigPanel configPanel = (ModConfigPanel) getConfigPanel();
            configPanel.configurationChanged(newProps);

            // Info Dialog sizes and locations
            ModInfoPanel infoPanel = (ModInfoPanel) getInfoPanel();
            infoPanel.setPatternDialogLocation(Helpers.getPointFromString(newProps.getProperty(PROPERTY_PATTERN_POS, "-1x-1")));
            infoPanel.setPatternDialogSize(Helpers.getDimensionFromString(newProps.getProperty(PROPERTY_PATTERN_SIZE, "640x480")));
            infoPanel.setPatternDialogVisible(Boolean.parseBoolean(newProps.getProperty(PROPERTY_PATTERN_VISABLE, "false")));
            infoPanel.setSampleDialogLocation(Helpers.getPointFromString(newProps.getProperty(PROPERTY_SAMPLE_POS, "-1x-1")));
            infoPanel.setSampleDialogSize(Helpers.getDimensionFromString(newProps.getProperty(PROPERTY_SAMPLE_SIZE, "640x480")));
            infoPanel.setSampleDialogVisible(Boolean.parseBoolean(newProps.getProperty(PROPERTY_SAMPLE_VISABLE, "false")));
            infoPanel.setInstrumentDialogLocation(Helpers.getPointFromString(newProps.getProperty(PROPERTY_INSTRUMENT_POS, "-1x-1")));
            infoPanel.setInstrumentDialogSize(Helpers.getDimensionFromString(newProps.getProperty(PROPERTY_INSTRUMENT_SIZE, "640x480")));
            infoPanel.setInstrumentDialogVisible(Boolean.parseBoolean(newProps.getProperty(PROPERTY_INSTRUMENT_VISABLE, "false")));
        }
    }

    /**
     * Get the values from the GUI and store them into the main Properties
     *
     * @since 13.10.2007
     */
    @Override
    public void configurationSave(Properties props) {
        if (currentProps == null) currentProps = new Properties();
        if (!MultimediaContainerManager.isHeadlessMode()) {
            ModConfigPanel configPanel = (ModConfigPanel) getConfigPanel();
            configPanel.configurationSave(currentProps);

            // Info Dialog sizes and locations
            ModPatternDialog patternDialog = ((ModInfoPanel) getInfoPanel()).getModPatternDialog();
            props.setProperty(PROPERTY_PATTERN_POS, Helpers.getStringFromPoint(patternDialog.getLocation()));
            props.setProperty(PROPERTY_PATTERN_SIZE, Helpers.getStringFromDimension(patternDialog.getSize()));
            props.setProperty(PROPERTY_PATTERN_VISABLE, Boolean.toString(((ModInfoPanel) getInfoPanel()).getModPatternDialogsVisible()));
            ModSampleDialog sampleDialog = ((ModInfoPanel) getInfoPanel()).getModSampleDialog();
            props.setProperty(PROPERTY_SAMPLE_POS, Helpers.getStringFromPoint(sampleDialog.getLocation()));
            props.setProperty(PROPERTY_SAMPLE_SIZE, Helpers.getStringFromDimension(sampleDialog.getSize()));
            props.setProperty(PROPERTY_SAMPLE_VISABLE, Boolean.toString(((ModInfoPanel) getInfoPanel()).getModSampleDialogsVisible()));
            ModInstrumentDialog instrumentDialog = ((ModInfoPanel) getInfoPanel()).getModInstrumentDialog();
            props.setProperty(PROPERTY_INSTRUMENT_POS, Helpers.getStringFromPoint(instrumentDialog.getLocation()));
            props.setProperty(PROPERTY_INSTRUMENT_SIZE, Helpers.getStringFromDimension(instrumentDialog.getSize()));
            props.setProperty(PROPERTY_INSTRUMENT_VISABLE, Boolean.toString(((ModInfoPanel) getInfoPanel()).getModInstrumentDialogsVisible()));
        }

        if (props != null) {
            props.setProperty(PROPERTY_PLAYER_FREQUENCY, currentProps.getProperty(PROPERTY_PLAYER_FREQUENCY, DEFAULT_SAMPLERATE));
            props.setProperty(PROPERTY_PLAYER_MSBUFFERSIZE, currentProps.getProperty(PROPERTY_PLAYER_MSBUFFERSIZE, DEFAULT_MSBUFFERSIZE));
            props.setProperty(PROPERTY_PLAYER_BITSPERSAMPLE, currentProps.getProperty(PROPERTY_PLAYER_BITSPERSAMPLE, DEFAULT_BITSPERSAMPLE));
            props.setProperty(PROPERTY_PLAYER_STEREO, currentProps.getProperty(PROPERTY_PLAYER_STEREO, DEFAULT_CHANNEL));
            props.setProperty(PROPERTY_PLAYER_ISP, currentProps.getProperty(PROPERTY_PLAYER_ISP, DEFAULT_INTERPOLATION_INDEX));
            props.setProperty(PROPERTY_PLAYER_WIDESTEREOMIX, currentProps.getProperty(PROPERTY_PLAYER_WIDESTEREOMIX, DEFAULT_WIDESTEREOMIX));
            props.setProperty(PROPERTY_PLAYER_NOISEREDUCTION, currentProps.getProperty(PROPERTY_PLAYER_NOISEREDUCTION, DEFAULT_NOISEREDUCTION));
            props.setProperty(PROPERTY_PLAYER_MEGABASS, currentProps.getProperty(PROPERTY_PLAYER_MEGABASS, DEFAULT_MEGABASS));
            props.setProperty(PROPERTY_PLAYER_DCREMOVAL, currentProps.getProperty(PROPERTY_PLAYER_DCREMOVAL, DEFAULT_DCREMOVAL));
            props.setProperty(PROPERTY_PLAYER_NOLOOPS, currentProps.getProperty(PROPERTY_PLAYER_NOLOOPS, DEFAULT_NOLOOPS));
            props.setProperty(PROPERTY_PLAYER_MAXNNACHANNELS, currentProps.getProperty(PROPERTY_PLAYER_MAXNNACHANNELS, DEFAULT_MAXNNACHANNELS));
            props.setProperty(PROPERTY_PLAYER_DITHERFILTER, currentProps.getProperty(PROPERTY_PLAYER_DITHERFILTER, DEFAULT_DITHERFILTER));
            props.setProperty(PROPERTY_PLAYER_DITHERTYPE, currentProps.getProperty(PROPERTY_PLAYER_DITHERTYPE, DEFAULT_DITHERTYPE));
            props.setProperty(PROPERTY_PLAYER_DITHERBYPASS, currentProps.getProperty(PROPERTY_PLAYER_DITHERBYPASS, DEFAULT_DITHERBYPASS));
        }
    }

    /**
     * Public, because is used in ModInstrumentDialog and ModSampleDialog for play back of instruments / samples
     *
     * @return
     * @since 16.03.2024
     */
    public ModMixer createNewMixer0() {
        if (currentMod == null) return null; // you cannot get a mixer without a mod loaded

        configurationSave(currentProps);

        int frequency = Integer.parseInt(currentProps.getProperty(PROPERTY_PLAYER_FREQUENCY, DEFAULT_SAMPLERATE));
        int bitsPerSample = Integer.parseInt(currentProps.getProperty(PROPERTY_PLAYER_BITSPERSAMPLE, DEFAULT_BITSPERSAMPLE));
        int channels = Integer.parseInt(currentProps.getProperty(PROPERTY_PLAYER_STEREO, DEFAULT_CHANNEL));
        int isp = Integer.parseInt(currentProps.getProperty(PROPERTY_PLAYER_ISP, DEFAULT_INTERPOLATION_INDEX));
        boolean wideStereoMix = Boolean.parseBoolean(currentProps.getProperty(PROPERTY_PLAYER_WIDESTEREOMIX, DEFAULT_WIDESTEREOMIX));
        boolean noiseReduction = Boolean.parseBoolean(currentProps.getProperty(PROPERTY_PLAYER_NOISEREDUCTION, DEFAULT_NOISEREDUCTION));
        boolean megaBass = Boolean.parseBoolean(currentProps.getProperty(PROPERTY_PLAYER_MEGABASS, DEFAULT_MEGABASS));
        boolean dcRemoval = Boolean.parseBoolean(currentProps.getProperty(PROPERTY_PLAYER_DCREMOVAL, DEFAULT_DCREMOVAL));
        int loopValue = Integer.parseInt(currentProps.getProperty(PROPERTY_PLAYER_NOLOOPS, DEFAULT_NOLOOPS));
        int maxNNAChannels = Integer.parseInt(currentProps.getProperty(PROPERTY_PLAYER_MAXNNACHANNELS, DEFAULT_MAXNNACHANNELS));
        int msBufferSize = Integer.parseInt(currentProps.getProperty(PROPERTY_PLAYER_MSBUFFERSIZE, DEFAULT_MSBUFFERSIZE));
        int ditherFilter = Integer.parseInt(currentProps.getProperty(PROPERTY_PLAYER_DITHERFILTER, DEFAULT_DITHERFILTER));
        int ditherType = Integer.parseInt(currentProps.getProperty(PROPERTY_PLAYER_DITHERTYPE, DEFAULT_DITHERTYPE));
        boolean ditherByPass = Boolean.parseBoolean(currentProps.getProperty(PROPERTY_PLAYER_DITHERBYPASS, DEFAULT_DITHERBYPASS));
        return new ModMixer(currentMod, bitsPerSample, channels, frequency, isp, wideStereoMix, noiseReduction, megaBass, dcRemoval, loopValue, maxNNAChannels, msBufferSize, ditherFilter, ditherType, ditherByPass);
    }

    /**
     * Will create a new mixer for the currently loaded mod.
     *
     * @return
     * @since 12.10.2007
     * @see de.quippy.javamod.multimedia.MultimediaContainer#createNewMixer()
     */
    @Override
    public Mixer createNewMixer() {
        unwireListeners();

        currentMixer = createNewMixer0();
        if (currentMixer == null) return null;

        wireListeners();

        return currentMixer;
    }

    /**
     * @since 11.11.2023
     */
    private void unwireListeners() {
        if (songUpdater != null) {
            songUpdater.stopUpdateThread();
            if (currentMixer != null) currentMixer.getModMixer().deregisterUpdateListener(songUpdater);
            if (modInfoPanel != null) songUpdater.deregisterUpdateListener(modInfoPanel.getModPatternDialog());
            songUpdater = null;
        }
        if (modInfoPanel != null) modInfoPanel.getModPatternDialog().setMixer(null);
    }

    /**
     * @since 11.11.2023
     */
    private void wireListeners() {
        if (modInfoPanel != null && currentMixer != null) {
            songUpdater = new SongUpdater();
            currentMixer.getModMixer().registerUpdateListener(songUpdater);
            songUpdater.registerUpdateListener(modInfoPanel.getModPatternDialog());
            modInfoPanel.getModPatternDialog().setMixer(currentMixer);
            songUpdater.startUpdateThread();
        }
    }

    /**
     * @since 14.10.2007
     */
    public ModMixer getCurrentMixer() {
        return currentMixer;
    }

    public Module getCurrentMod() {
        return currentMod;
    }

    @Override
    public void cleanUp() {
        unwireListeners();
    }
}
