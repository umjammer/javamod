/*
 * @(#) OGGContainer.java
 *
 * Created on 01.11.2010 by Daniel Becker
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

package de.quippy.javamod.multimedia.ogg;

import java.net.URL;
import java.util.Properties;
import javax.swing.JPanel;

import de.quippy.javamod.mixer.Mixer;
import de.quippy.javamod.multimedia.MultimediaContainer;
import de.quippy.javamod.multimedia.MultimediaContainerManager;
import de.quippy.javamod.multimedia.ogg.metadata.OggMetaData;


/**
 * @author Daniel Becker
 * @since 01.11.2010
 */
public class OGGContainer extends MultimediaContainer {

    private static final String[] OGGFILEEXTENSION = {
            "ogg", "oga"
    };
    private OGGInfoPanel oggInfoPanel;
    private OggMetaData oggMetaData = null;

    @Override
    public MultimediaContainer getInstance(URL url) {
        MultimediaContainer result = super.getInstance(url);
        oggMetaData = new OggMetaData(url);
        if (!MultimediaContainerManager.isHeadlessMode())
            ((OGGInfoPanel) getInfoPanel()).fillInfoPanelWith(oggMetaData, getPrintableFileUrl());
        return result;
    }

    @Override
    public String getSongName() {
        if (oggMetaData != null)
            return oggMetaData.getShortDescription();
        else
            return super.getSongName();
    }

    @Override
    public Object[] getSongInfosFor(URL url) {
        String songName = MultimediaContainerManager.getSongNameFromURL(url);
        long duration = -1;
        try {
            OggMetaData metaData = new OggMetaData(url);
            songName = metaData.getShortDescription();
            duration = metaData.getLengthInMilliseconds();
        } catch (Throwable ex) {
        }
        return new Object[] {songName, duration};
    }

    @Override
    public Mixer createNewMixer() {
        return new OGGMixer(getFileURL(), oggMetaData.getLengthInMilliseconds());
    }

    @Override
    public boolean canExport() {
        return true;
    }

    @Override
    public String[] getFileExtensionList() {
        return OGGFILEEXTENSION;
    }

    @Override
    public String getName() {
        return "ogg/vorbis-File";
    }

    @Override
    public JPanel getConfigPanel() {
        return null;
    }

    @Override
    public JPanel getInfoPanel() {
        if (oggInfoPanel == null) {
            oggInfoPanel = new OGGInfoPanel();
            oggInfoPanel.setParentContainer(this);
        }
        return oggInfoPanel;
    }

    @Override
    public void configurationChanged(Properties newProps) {
    }

    @Override
    public void configurationSave(Properties props) {
    }

    @Override
    public void cleanUp() {
    }
}
