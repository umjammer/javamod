/*
 * @(#)MultimediaContainer.java
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

package de.quippy.javamod.multimedia;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import de.quippy.javamod.mixer.Mixer;
import de.quippy.javamod.system.Helpers;


/**
 * @author Daniel Becker
 * @since 12.10.2007
 */
public abstract class MultimediaContainer {

    private final List<MultimediaContainerEventListener> listeners = new ArrayList<>();
    private URL fileURL = null;

    /**
     * @since 12.10.2007
     */
    public MultimediaContainer() {
        super();
    }

    /**
     * A default implementation. If you need a new instance,
     * override this and do whatever is needed!
     *
     * @return
     * @since 13.10.2007
     */
    public MultimediaContainer getInstance(URL url) {
        setFileURL(url);
        return this;
    }

    /**
     * @return
     * @since 13.10.2007
     */
    public URL getFileURL() {
        return fileURL;
    }

    /**
     * @param url
     * @since 19.12.2022
     */
    public void setFileURL(URL url) {
        this.fileURL = url;
    }

    /**
     * @return a printable version of the URL
     * @since 23.12.2010
     */
    public String getPrintableFileUrl() {
        return getPrintableFileUrl(getFileURL());
    }

    public String getPrintableFileUrl(URL urlName) {
        if (urlName == null) return Helpers.EMPTY_STING;
        try {
            java.io.File f = new java.io.File(urlName.toURI());
            try {
                return f.getCanonicalPath();
            } catch (IOException ex) {
                return f.getAbsolutePath();
            }
        } catch (Throwable e) {
            return urlName.toExternalForm();
        }
    }

    public void updateLookAndFeel() {
        JPanel infoPanel = getInfoPanel();
        JPanel configPanel = getConfigPanel();
        if (infoPanel != null) SwingUtilities.updateComponentTreeUI(infoPanel);
        if (configPanel != null) SwingUtilities.updateComponentTreeUI(configPanel);
    }

    public void addListener(MultimediaContainerEventListener listener) {
        listeners.add(listener);
    }

    public void removeListener(MultimediaContainerEventListener listener) {
        listeners.remove(listener);
    }

    protected void fireMultimediaContainerEvent(MultimediaContainerEvent event) {
        for (MultimediaContainerEventListener listener : listeners) listener.multimediaContainerEventOccurred(event);
    }

    /**
     * Return the name of the song
     *
     * @return
     * @since 21.09.2008
     */
    public String getSongName() {
        return MultimediaContainerManager.getSongNameFromURL(fileURL);
    }

    /**
     * This method will only do (!)locally(!) what is needed to pick up
     * the song name String at [0] and time in milliseconds as Long at [1]
     *
     * @param url
     * @return Object [] { String SongName, Long duration }
     * @since 12.02.2011
     */
    public abstract Object[] getSongInfosFor(URL url);

    /**
     * Returns true if this mixers supports the export function
     *
     * @return
     * @since 26.10.2007
     */
    public abstract boolean canExport();

    /**
     * Return the info dialog panel
     *
     * @return
     * @since 13.10.2007
     */
    public abstract JPanel getInfoPanel();

    /**
     * Returns the config panel for this mixer
     *
     * @return
     * @since 13.10.2007
     */
    public abstract JPanel getConfigPanel();

    /**
     * The file extensions this container is responsible for
     *
     * @return
     * @since 12.10.2007
     */
    public abstract String[] getFileExtensionList();

    /**
     * A descriptive Name for e.g. a FileChooser
     *
     * @return
     * @since 05.01.2008
     */
    public abstract String getName();

    /**
     * @param newProps
     * @since 13.10.2007
     */
    public abstract void configurationChanged(Properties newProps);

    /**
     * @param props
     * @since 14.10.2007
     */
    public abstract void configurationSave(Properties props);

    /**
     * Clean up
     *
     * @since 11.11.2023
     */
    public abstract void cleanUp();

    /**
     * Get the mixer of this container
     *
     * @return
     * @since 12.10.2007
     */
    public abstract Mixer createNewMixer();
}
