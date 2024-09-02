/*
 * @(#)MultimediaContainerManager.java
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

import java.io.File;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;
import javax.sound.sampled.UnsupportedAudioFileException;

import de.quippy.javamod.system.Helpers;

import static java.lang.System.getLogger;


/**
 * @author Daniel Becker
 * @since 12.10.2007
 */
public class MultimediaContainerManager {

    private static final Logger logger = getLogger(MultimediaContainerManager.class.getName());

    private static Map<String, MultimediaContainer> fileExtensionMap;
    private static Iterable<MultimediaContainer> containers;
    private static boolean headlessMode = true;

    static {
        for (MultimediaContainer container : getContainers()) {
            String[] extensions = container.getFileExtensionList();
            for (String extension : extensions) getFileExtensionMap().put(extension, container);
        }
    }

    /**
     * @since 12.10.2007
     */
    private MultimediaContainerManager() {
    }

    /**
     * To avoid instantiating any dialogs if on command line
     *
     * @param isHeadless
     * @since 15.01.2024
     */
    public static void setIsHeadlessMode(boolean isHeadless) {
        headlessMode = isHeadless;
    }

    public static boolean isHeadlessMode() {
        return headlessMode;
    }

    public static Map<String, MultimediaContainer> getFileExtensionMap() {
        if (fileExtensionMap == null)
            fileExtensionMap = new HashMap<>();

        return fileExtensionMap;
    }

    public static Iterable<MultimediaContainer> getContainers() {
        if (containers == null)
            containers = ServiceLoader.load(MultimediaContainer.class);
        return containers;
    }

    public static void getContainerConfigs(Properties intoProps) {
        Iterable<MultimediaContainer> listeners = getContainers();
        for (MultimediaContainer listener : listeners) listener.configurationSave(intoProps);
    }

    public static void configureContainer(Properties fromProps) {
        Iterable<MultimediaContainer> listeners = getContainers();
        for (MultimediaContainer listener : listeners) listener.configurationChanged(fromProps);
    }

    public static void cleanUpAllContainers() {
        Iterable<MultimediaContainer> containers = getContainers();
        for (MultimediaContainer container : containers) {
            container.cleanUp();
        }
        getFileExtensionMap().clear();
    }

    public static void updateLookAndFeel() {
        Iterable<MultimediaContainer> listeners = getContainers();
        for (MultimediaContainer listener : listeners) listener.updateLookAndFeel();
    }

    public static String[] getSupportedFileExtensions() {
        Set<String> keys = getFileExtensionMap().keySet();
        String[] result = new String[keys.size()];
        return keys.toArray(result);
    }

    public static Map<String, String[]> getSupportedFileExtensionsPerContainer() {
        Iterable<MultimediaContainer> listeners = getContainers();
        Map<String, String[]> result = new HashMap<>();
        for (MultimediaContainer listener : listeners) result.put(listener.getName(), listener.getFileExtensionList());
        return result;
    }

    public static MultimediaContainer getMultimediaContainerForType(String type) throws UnsupportedAudioFileException {
        MultimediaContainer container = getFileExtensionMap().get(type.toLowerCase());
        if (container == null)
            throw new UnsupportedAudioFileException(type);
        else
            return container;
    }

    public static MultimediaContainer getMultimediaContainerSingleton(URL url) throws UnsupportedAudioFileException {
        String fileName = url.getPath();

        // we default to mp3 with wrong extensions
logger.log(Level.DEBUG, getFileExtensionMap());
        MultimediaContainer baseContainer = getFileExtensionMap().get(Helpers.getExtensionFrom(fileName));
        if (baseContainer == null)
            baseContainer = getFileExtensionMap().get(Helpers.getPreceedingExtensionFrom(fileName));
        if (baseContainer == null) { // no extensions found?!
            if (Helpers.isFile(url))
                throw new UnsupportedAudioFileException(fileName); // in Filemode we are ready now
            else
                baseContainer = getFileExtensionMap().get("mp3"); // otherwise we try a streaming protocol!
        }

        return baseContainer;
    }

    /**
     * Will use getMultimediaContainerSingleton to retrieve the basic singleton
     * and then create an instance by getInstance on that singleton
     * This will also update the info panels, if getInstance is overridden.
     *
     * @param url The URL of the file to load
     * @return {@link MultimediaContainer}
     * @throws UnsupportedAudioFileException
     * @since 15.01.2024
     */
    public static MultimediaContainer getMultimediaContainer(URL url) throws UnsupportedAudioFileException {
        MultimediaContainer baseContainer = getMultimediaContainerSingleton(url);
        MultimediaContainer container = baseContainer.getInstance(url);
        if (container == null)
            throw new UnsupportedAudioFileException(url.getPath());
        else
            return container;
    }

    public static MultimediaContainer getMultimediaContainer(URI uri) throws MalformedURLException, UnsupportedAudioFileException {
        return getMultimediaContainer(uri.toURL());
    }

    public static MultimediaContainer getMultimediaContainer(File file) throws MalformedURLException, UnsupportedAudioFileException {
        return getMultimediaContainer(file.toURI());
    }

    public static MultimediaContainer getMultimediaContainer(String fileName) throws MalformedURLException, UnsupportedAudioFileException {
        return getMultimediaContainer(new File(fileName));
    }

    public static void addMultimediaContainerEventListener(MultimediaContainerEventListener listener) {
        Iterable<MultimediaContainer> containers = getContainers();
        for (MultimediaContainer container : containers) container.addListener(listener);
    }

    public static void removeMultimediaContainerEventListener(MultimediaContainerEventListener listener) {
        Iterable<MultimediaContainer> containers = getContainers();
        for (MultimediaContainer container : containers) container.removeListener(listener);
    }

    public static String getSongNameFromURL(URL url) {
        if (url == null) return Helpers.EMPTY_STING;

        String result = Helpers.createStringFomURL(url);
        int lastSlash = result.lastIndexOf('/');
        int dot = result.lastIndexOf('.');
        if (dot == -1 || dot < lastSlash) dot = result.length();
        return result.substring(lastSlash + 1, dot);
    }

    public static String getSongNameFromFile(File fileName) {
        if (fileName == null) return Helpers.EMPTY_STING;

        String result = fileName.getAbsolutePath();
        int lastSlash = result.lastIndexOf(File.separatorChar);
        int dot = result.lastIndexOf('.');
        if (dot == -1 || dot < lastSlash) dot = result.length();
        return result.substring(lastSlash + 1, dot);
    }

    /**
     * This method will only do (!)localy(!) what is needed to pick up
     * the song name String at [0] and time in milliseconds as Long at [1]
     *
     * @param url the local source
     * @return info map
     * @since 12.02.2011
     */
    public static Object[] getSongInfosFor(URL url) {
        try {
            MultimediaContainer container = getMultimediaContainerSingleton(url);
            if (container != null) return container.getSongInfosFor(url);
        } catch (UnsupportedAudioFileException ex) {
            logger.log(Level.TRACE, "IGNORED", ex);
        }
        return new Object[] {getSongNameFromURL(url) + " UNSUPPORTED FILE", (long) -1};
    }
}
