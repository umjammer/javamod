/*
 * @(#) FLACContainer.java
 *
 * Created on 01.01.2011 by Daniel Becker
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

package de.quippy.javamod.multimedia.flac;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;
import javax.sound.sampled.AudioFormat;
import javax.swing.JPanel;

import de.quippy.javamod.io.FileOrPackedInputStream;
import de.quippy.javamod.mixer.Mixer;
import de.quippy.javamod.multimedia.MultimediaContainer;
import de.quippy.javamod.multimedia.MultimediaContainerManager;
import de.quippy.javamod.system.Helpers;
import org.kc7bfi.jflac.FLACDecoder;
import org.kc7bfi.jflac.metadata.VorbisComment;


/**
 * @author Daniel Becker
 * @since 01.01.2011
 */
public class FLACContainer extends MultimediaContainer {

    private static final String[] FLACFILEEXTENSION = {
            "flac"
    };
    private FLACInfoPanel flacInfoPanel;

    private VorbisComment vorbisComment;
    private long duration;

    /**
     * @return
     * @see de.quippy.javamod.multimedia.MultimediaContainer#canExport()
     */
    @Override
    public boolean canExport() {
        return true;
    }

    /**
     * @param url
     * @return
     * @see de.quippy.javamod.multimedia.MultimediaContainer#getInstance(java.net.URL)
     */
    @Override
    public MultimediaContainer getInstance(URL url) {
        MultimediaContainer result = super.getInstance(url);
        InputStream inputStream = null;
        try {
            inputStream = new FileOrPackedInputStream(url);
            FLACDecoder decoder = new FLACDecoder(inputStream);
            decoder.readMetadata();
            vorbisComment = decoder.getVorbisComment();
            AudioFormat audioFormat = decoder.getStreamInfo().getAudioFormat();
            long sampleRate = (long) audioFormat.getSampleRate();
            duration = decoder.getStreamInfo().getTotalSamples() * 1000L / sampleRate;
            if (!MultimediaContainerManager.isHeadlessMode())
                ((FLACInfoPanel) getInfoPanel()).fillInfoPanelWith(audioFormat, duration, Helpers.getFileNameFromURL(url), getSongName(), decoder.getVorbisComment());
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            if (inputStream != null) try {
                inputStream.close();
            } catch (IOException ex) { /* logger.log(Level.ERROR, "IGNORED", ex); */ }
        }
        return result;
    }

    private static String getSongName(VorbisComment vorbisComment, URL forURL) {
        if (vorbisComment != null) {
            try {
                String artist = vorbisComment.getArtist();
                String album = vorbisComment.getAlbum();
                String title = vorbisComment.getTitle();
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
        if (vorbisComment != null)
            return getSongName(vorbisComment, getFileURL());
        else
            return super.getSongName();
    }

    @Override
    public Mixer createNewMixer() {
        return new FLACMixer(getFileURL());
    }

    @Override
    public Object[] getSongInfosFor(URL url) {
        String songName = MultimediaContainerManager.getSongNameFromURL(url);
        long duration = -1;
        InputStream inputStream = null;
        try {
            inputStream = new FileOrPackedInputStream(url);
            FLACDecoder decoder = new FLACDecoder(inputStream);
            decoder.readMetadata();
            VorbisComment vorbisComment = decoder.getVorbisComment();
            songName = getSongName(vorbisComment, url);
            AudioFormat audioFormat = decoder.getStreamInfo().getAudioFormat();
            long sampleRate = (long) audioFormat.getSampleRate();
            duration = decoder.getStreamInfo().getTotalSamples() * 1000L / sampleRate;
        } catch (Throwable ex) {
        } finally {
            if (inputStream != null) try {
                inputStream.close();
            } catch (IOException ex) { /* logger.log(Level.ERROR, "IGNORED", ex); */ }
        }
        return new Object[] {songName, duration};
    }

    @Override
    public String getName() {
        return "FLAC-File";
    }

    @Override
    public String[] getFileExtensionList() {
        return FLACFILEEXTENSION;
    }

    @Override
    public JPanel getConfigPanel() {
        return null;
    }

    @Override
    public JPanel getInfoPanel() {
        if (flacInfoPanel == null) {
            flacInfoPanel = new FLACInfoPanel();
            flacInfoPanel.setParentContainer(this);
        }
        return flacInfoPanel;
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
