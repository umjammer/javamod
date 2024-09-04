/*
 * @(#) APEContainer.java
 *
 * Created on 22.12.2010 by Daniel Becker
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

package de.quippy.javamod.multimedia.ape;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import javax.swing.JPanel;

import davaguine.jmac.decoder.IAPEDecompress;
import davaguine.jmac.info.APETag;
import davaguine.jmac.tools.File;
import de.quippy.javamod.mixer.Mixer;
import de.quippy.javamod.multimedia.MultimediaContainer;
import de.quippy.javamod.multimedia.MultimediaContainerManager;

import static java.lang.System.getLogger;


/**
 * @author Daniel Becker
 * @since 22.12.2010
 */
public class APEContainer extends MultimediaContainer {

    private static final Logger logger = getLogger(APEContainer.class.getName());

    private static final String[] APEFILEEXTENSION = {
            "ape", "apl", "mac"
    };
    private APEInfoPanel apeInfoPanel;

    private APETag idTag;

    @Override
    public boolean canExport() {
        return true;
    }

    @Override
    public MultimediaContainer getInstance(URL url) {
        MultimediaContainer result = super.getInstance(url);
        File apeFile = null;
        try {
            apeFile = File.createFile(url, "r");
            IAPEDecompress spAPEDecompress = IAPEDecompress.createAPEDecompress(apeFile);
            idTag = spAPEDecompress.getApeInfoTag();
            if (!MultimediaContainerManager.isHeadlessMode())
                ((APEInfoPanel) getInfoPanel()).fillInfoPanelWith(spAPEDecompress, getPrintableFileUrl(), getSongName());
        } catch (IOException ex) {
        } finally {
            if (apeFile != null) try {
                apeFile.close();
            } catch (IOException ex) { logger.log(Level.TRACE, "IGNORED", ex); }
        }
        return result;
    }

    @Override
    public Mixer createNewMixer() {
        return new APEMixer(getFileURL());
    }

    private static String getSongName(APETag idTag, URL forURL) {
        if (idTag != null) {
            try {
                String artist = idTag.getFieldString(APETag.APE_TAG_FIELD_ARTIST);
                String album = idTag.getFieldString(APETag.APE_TAG_FIELD_ALBUM);
                String title = idTag.getFieldString(APETag.APE_TAG_FIELD_TITLE);
                if (title == null || title.isEmpty()) title = MultimediaContainerManager.getSongNameFromURL(forURL);

                StringBuilder str = new StringBuilder();
                if (artist != null && !artist.isEmpty()) {
                    str.append(artist).append(" - ");
                }
                if (album != null && !album.isEmpty()) {
                    str.append(album).append(" - ");
                }
                return str.append(title).toString();
            } catch (Throwable ex) { // we can get the runtime exception "Unsupported Function"
            }
        }
        return MultimediaContainerManager.getSongNameFromURL(forURL);
    }

    @Override
    public String getSongName() {
        if (idTag != null)
            return getSongName(idTag, getFileURL());
        else
            return super.getSongName();
    }

    @Override
    public Map<String, Object> getSongInfosFor(URL url) {
        Map<String, Object> result = new HashMap<>();
        String songName = MultimediaContainerManager.getSongNameFromURL(url);
        long duration = -1;
        try {
            File apeFile = File.createFile(url, "r");
            IAPEDecompress spAPEDecompress = IAPEDecompress.createAPEDecompress(apeFile);
            APETag idTag = spAPEDecompress.getApeInfoTag();
            songName = getSongName(idTag, url);
            duration = spAPEDecompress.getApeInfoDecompressLengthMS();
        } catch (Throwable ex) {
        }
        result.put("songName", songName);
        result.put("duration", duration);
        return result;
    }

    @Override
    public JPanel getConfigPanel() {
        return null;
    }

    @Override
    public JPanel getInfoPanel() {
        if (apeInfoPanel == null) {
            apeInfoPanel = new APEInfoPanel();
            apeInfoPanel.setParentContainer(this);
        }
        return apeInfoPanel;
    }

    @Override
    public void configurationChanged(Properties newProps) {
    }

    @Override
    public void configurationSave(Properties props) {
    }

    @Override
    public String[] getFileExtensionList() {
        return APEFILEEXTENSION;
    }

    @Override
    public String getName() {
        return "APE-File";
    }

    @Override
    public void cleanUp() {
    }
}
