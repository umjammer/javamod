/*
 * @(#) PlaylistDropListener.java
 *
 * Created on 08.03.2011 by Daniel Becker
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

package de.quippy.javamod.main.gui.tools;

import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.io.File;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import de.quippy.javamod.main.playlist.PlayList;
import de.quippy.javamod.multimedia.MultimediaContainerManager;
import de.quippy.javamod.system.Helpers;

import static java.lang.System.getLogger;


/**
 * @author Daniel Becker
 */
public class PlaylistDropListener extends DropTargetAdapter {

    private static final Logger logger = getLogger(PlaylistDropListener.class.getName());

    private final PlaylistDropListenerCallBack callBack;

    /**
     * @since 08.03.2011
     */
    public PlaylistDropListener(PlaylistDropListenerCallBack callBack) {
        this.callBack = callBack;
    }

    private static void fillWithPlayableFiles(List<URL> urls, File startDir) {
        String[] files = startDir.list((dir, name) -> {
            File fullFileName = new File(dir.getAbsolutePath() + File.separatorChar + name);
            if (fullFileName.isDirectory()) return true;
            try {
                return MultimediaContainerManager.getMultimediaContainerSingleton(fullFileName.toURI().toURL()) != null;
            } catch (Exception ex) {
                //NOOP;
            }
            return false;
        });
        for (String file : files) {
            File fullFileName = new File(startDir.getAbsolutePath() + File.separatorChar + file);
            if (fullFileName.isDirectory())
                fillWithPlayableFiles(urls, fullFileName);
            else {
                try {
                    urls.add(fullFileName.toURI().toURL());
                } catch (Exception ex) {
                    //NOOP;
                }
            }
        }
    }

    /**
     * @since 08.03.2011
     */
    @Override
    public void drop(DropTargetDropEvent dtde) {
        try {
            URL addToLastLoaded = null;
            List<?> files = Helpers.getDropData(dtde);
            if (files != null) {
                List<URL> urls = new ArrayList<>(files.size());

                for (int i = 0; i < files.size(); i++) {
                    String fileName = files.get(i).toString(); // can be files, can be strings...
                    File f = new File(fileName);
                    if (f.isDirectory()) {
                        fillWithPlayableFiles(urls, f);
                    } else {
                        URL url = f.toURI().toURL();
                        if (files.size() == 1) addToLastLoaded = url;
                        urls.add(url);
                    }
                }
                PlayList playList = PlayList.createNewListWithFiles(urls.toArray(URL[]::new), false, false);
                callBack.playlistReceived(dtde, playList, addToLastLoaded);
            }
        } catch (Exception ex) {
            logger.log(Level.ERROR, "[MainForm::DropListener]", ex);
        } finally {
            dtde.dropComplete(true);
        }
    }
}
