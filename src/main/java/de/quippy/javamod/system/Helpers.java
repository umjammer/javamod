/*
 * @(#) Helpers.java
 *
 * Created on 22.04.2006 by Daniel Becker
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

package de.quippy.javamod.system;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.DisplayMode;
import java.awt.Font;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;

import de.quippy.javamod.main.gui.components.ProgressDialog;
import de.quippy.javamod.main.gui.tools.FileChooserResult;
import de.quippy.javamod.main.gui.tools.PlaylistDropListener;

import static java.lang.System.getLogger;


/**
 * @author Daniel Becker
 * @since 22.04.2006
 */
public class Helpers {

    private static final Logger logger = getLogger(Helpers.class.getName());

    /**
     * Constructor for Helpers, not used!
     */
    private Helpers() {
        super();
    }

    /** Version Information */
    public static final String VERSION = "V3.9.4.1";
    public static final String PROGRAM = "Java Mod Player";
    public static final String FULLVERSION = PROGRAM + ' ' + VERSION;
    public static final String COPYRIGHT = "© by Daniel Becker since 2006";

    public static final String USER_AGENT = "JavaMod/" + VERSION;

    public static final String VERSION_URL = "https://quippy.de/modplayer/javamod_version.txt";
    public static final String JAVAMOD_URL = "https://quippy.de/modplayer/javamod.jar";

    /** Codepages used when reading mod files */
    public static final String CODING_GUI = "cp850";
    public static final String CODING_COMMANLINE = "cp1252";
    /** Default value - changes whether SWING or command line */
    public static String currentCoding = CODING_GUI;
    /** Codepages used when reading playlist files */
    public static final String CODING_M3U = "ISO-8859-1";
    /** Codepage used when reading from the web */
    public static final String CODING_HTTP = "UTF-8";
    /** Codepage used when reading from ICY Input-Streams */
    public static final String CODING_ICY = "ISO-8859-15";

    public static final String DEFAULTFONTPATH = "/de/quippy/javamod/main/gui/ressources/lucon.ttf";
    private static Font DIALOG_FONT = null;
    private static Font TEXTAREA_FONT = null;

    // This is needed only once...
    public static final String EMPTY_STING = "";

    /**
     * HomeDir is the property of the system value "user.home".
     * With Applets this value is unreadable (SecurityException)
     */
    public static String HOMEDIR;

    static {
        try {
            HOMEDIR = System.getProperty("user.home");
        } catch (Throwable ex1) {
            try {
                HOMEDIR = System.getProperty("java.io.tmpdir");
            } catch (Throwable ex2) {
                logger.log(Level.ERROR, "Could not set home dir", ex2);
                HOMEDIR = EMPTY_STING;
            }
        }
    }

    /**
     * Retrieve the current TextArea Font
     *
     * @return
     * @since 12.01.2016
     */
    public static Font getTextAreaFont() {
        if (TEXTAREA_FONT == null) {
            try {
                InputStream is = Helpers.class.getResourceAsStream(Helpers.DEFAULTFONTPATH);
                Font font = Font.createFont(Font.TRUETYPE_FONT, is);
                TEXTAREA_FONT = font.deriveFont(10.0f);
            } catch (Exception ex) {
                logger.log(Level.ERROR, "Could not load font!", ex);
                TEXTAREA_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 10);
            }
        }
        return TEXTAREA_FONT;
    }

    /**
     * Retrieve the current Dialog Font
     *
     * @return
     * @since 12.01.2016
     */
    public static Font getDialogFont() {
        if (DIALOG_FONT == null) {
            DIALOG_FONT = new Font(Font.DIALOG, Font.PLAIN, 10);
        }
        return DIALOG_FONT;
    }

    /**
     * It's ridiculous, but with the GUI we need cp850 but with
     * the commandline we need cp1252. Unbelievable...
     *
     * @param gui
     * @since 02.07.2006
     */
    public static void setCoding(boolean gui) {
        currentCoding = (gui) ? CODING_GUI : CODING_COMMANLINE;
    }

    /**
     * VU-Meter constants
     */
    public static final String SCROLLY_BLANKS = "     ";


    /* SERVICE METHODS -------------------------------------------------------*/

    /**
     * Loads a C-Type String. In C/C++ strings end with a NULL-byte. We search for it
     * and skip the rest. Furthermore we convert to UNICODE
     * By the way: sometimes the C-Strings are not terminated. We need to do that
     * ourself then!
     *
     * @param input
     * @param start
     * @param length
     * @return
     */
    public static String retrieveAsString(byte[] input, int start, int length) {
        return retrieveAsString(input, start, length, currentCoding);
    }

    /**
     * Loads a C-Type String. In C/C++ strings end with a NULL-byte. We search for it
     * and skip the rest. Furthermore we convert to UNICODE
     * By the way: sometimes the C-Strings are not terminated. We need to do that
     * ourself then!
     *
     * @param input
     * @param start
     * @param len
     * @param coding
     * @return
     */
    public static String retrieveAsString(byte[] input, int start, int len, String coding) {
        int length = len;
        if (length <= 0) return EMPTY_STING;
        // keep in bounds of the source array...
        if (start + length >= input.length) length = input.length - start;

        String str = null;
        if (coding != null) {
            try {
                str = new String(input, start, length, coding);
            } catch (UnsupportedEncodingException ex) {
                /* NOOP */
            }
        }

        if (str == null) str = new String(input, start, length);

        // find a null byte and delete it
        StringBuilder b = new StringBuilder(str);
        int index = 0;
        while (index < b.length() && b.charAt(index) != 0) {
            if (b.charAt(index) == '\r') {
                // check for '\r\n' or '\r'
                if (index + 1 < b.length() && b.charAt(index + 1) == '\n')
                    b.setCharAt(index, ' ');
                else
                    b.setCharAt(index, '\n');
            } else if (b.charAt(index) < 0x20) b.setCharAt(index, ' ');
            index++;
        }
        b.delete(index, b.length());
        return b.toString();
    }

    /**
     * Converts a string to an array of bytes with the length specified
     *
     * @param str    the string to convert
     * @param length the size of the byte array
     * @return the array of bytes converted from the string
     * @since 23.12.2008
     */
    public static byte[] getBytesFromString(String str, int length, String coding) {
        byte[] result = new byte[length];
        int len = str.length();
        if (len > length) len = length;
        try {
            System.arraycopy(str.getBytes(coding), 0, result, 0, len);
        } catch (UnsupportedEncodingException ex) {
            System.arraycopy(str.getBytes(), 0, result, 0, len);
        }
        return result;
    }

    /**
     * Search the data byte array for the first occurrence of the byte array pattern within given boundaries.
     * Implementation of the Knuth-Morris-Pratt Pattern Matching Algorithm
     *
     * @param data
     * @param start   First index in data
     * @param stop    Last index in data so that stop-start = length
     * @param pattern What is being searched. '*' can be used as wildcard for "ANY character"
     * @return
     */
    public static int indexOf(byte[] data, int start, int stop, byte[] pattern) {
        if (data == null || pattern == null) return -1;
        int[] failure = computeFailure(pattern);
        int j = 0;
        for (int i = start; i < stop; i++) {
            while (j > 0 && (pattern[j] != '*' && pattern[j] != data[i])) j = failure[j - 1];
            if (pattern[j] == '*' || pattern[j] == data[i]) j++;
            if (j == pattern.length) return i - pattern.length + 1;
        }
        return -1;
    }

    /**
     * Computes the failure function using a boot-strapping process,
     * where the pattern is matched against itself.
     */
    private static int[] computeFailure(byte[] pattern) {
        int[] failure = new int[pattern.length];

        int j = 0;
        for (int i = 1; i < pattern.length; i++) {
            while (j > 0 && pattern[j] != pattern[i]) j = failure[j - 1];
            if (pattern[j] == pattern[i]) j++;
            failure[i] = j;
        }

        return failure;
    }

    //*************** UI *************
    public static String getHTMLColorString(Color color) {
        String htmlColor = Integer.toHexString(color.getRGB());
        if (htmlColor.length() > 6) htmlColor = htmlColor.substring(htmlColor.length() - 6);
        return htmlColor;
    }

    private static final Insets DEFAULT_INSETS = new Insets(4, 4, 4, 4);
    public static final Insets NULL_INSETS = new Insets(0, 0, 0, 0);

    /**
     * @param gridx
     * @param gridy
     * @param gridheight
     * @param gridwidth
     * @param fill
     * @param anchor
     * @param weightx
     * @param weighty
     * @return
     * @since 22.06.2006
     */
    public static GridBagConstraints getGridBagConstraint(int gridx, int gridy, int gridheight, int gridwidth, int fill, int anchor, double weightx, double weighty) {
        return getGridBagConstraint(gridx, gridy, gridheight, gridwidth, fill, anchor, weightx, weighty, DEFAULT_INSETS);
    }

    /**
     * @param gridx
     * @param gridy
     * @param gridheight
     * @param gridwidth
     * @param fill
     * @param anchor
     * @param weightx
     * @param weighty
     * @param insets
     * @return
     * @since 22.07.2015
     */
    public static GridBagConstraints getGridBagConstraint(int gridx, int gridy, int gridheight, int gridwidth, int fill, int anchor, double weightx, double weighty, Insets insets) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = gridx;
        constraints.gridy = gridy;
        constraints.gridheight = gridheight;
        constraints.gridwidth = gridwidth;
        constraints.fill = fill;
        constraints.anchor = anchor;
        constraints.weightx = weightx;
        constraints.weighty = weighty;
        constraints.insets = insets;
        return constraints;
    }

    /**
     * Get the location for centered Dialog
     *
     * @param centerThis
     * @return
     * @since 22.06.2006
     */
    public static java.awt.Point getFrameCenteredLocation(java.awt.Component centerThis, Component parent) {
        java.awt.Dimension screenSize = (parent == null) ? java.awt.Toolkit.getDefaultToolkit().getScreenSize() : parent.getSize();

        int x = (screenSize.width - centerThis.getWidth()) >> 1;
        int y = (screenSize.height - centerThis.getHeight()) >> 1;

        if (parent != null) {
            x += parent.getX();
            y += parent.getY();
        }
        if (x < 0) x = 0;
        if (y < 0) y = 0;

        return new java.awt.Point(x, y);
    }

    /**
     * @param c
     * @return DisplayMode of graphical context of given component
     * @since 25.01.2022
     */
    public static DisplayMode getScreenInfoOf(Component c) {
        GraphicsConfiguration gc = c.getGraphicsConfiguration();
        if (gc != null) {
            GraphicsDevice gd = gc.getDevice();
            if (gd != null)
                return gd.getDisplayMode();
        }
        return null;
    }

    /**
     * @param c
     * @return array if GraphicsDevices
     * @since 25.01.2022
     */
    public static GraphicsDevice[] getScreenInfos(Component c) {
        GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        if (env != null)
            return env.getScreenDevices();
        return null;
    }

    /**
     * Register the droplistener to all components...
     *
     * @param list       ArrayList of resulting DropTarget-Classes
     * @param basePanel
     * @param myListener
     * @since 12.10.2007
     */
    public static void registerDropListener(List<DropTarget> list, Container basePanel, PlaylistDropListener myListener) {
        list.add(new DropTarget(basePanel, DnDConstants.ACTION_COPY_OR_MOVE, myListener));

        Component[] components = basePanel.getComponents();
        for (Component component : components) {
            if (component instanceof Container)
                registerDropListener(list, (Container) component, myListener);
            else
                list.add(new DropTarget(component, DnDConstants.ACTION_COPY_OR_MOVE, myListener));
        }
    }

    /**
     * @param bReader
     * @return
     * @since 11.07.2020
     */
    public static List<File> readLinesFromFlavor(BufferedReader bReader) {
        List<File> list = new ArrayList<>();
        try {
            String line;
            while ((line = bReader.readLine()) != null) {
                try {
                    // kde seems to append a 0 char to the end of the reader
                    if (line.charAt(0) == '\0') continue;

                    // Furthermore KDE get's confused with encoding the URL of
                    // some files. So we do it manually.
                    // Funny is that File.toURL() works fine!
                    String fileString = Helpers.createStringFromURLString(line);
                    if (fileString.startsWith("file://")) fileString = fileString.substring(7);

                    File file = new File(fileString);
                    list.add(file);
                } catch (Exception ex) {
                    logger.log(Level.ERROR, "Error with " + line + ": ", ex);
                }
            }
        } catch (IOException ex) {
            logger.log(Level.ERROR, "Helpers:readLinesFromFlavor", ex);
        }
        return list;
    }

    /**
     * Because of a KDE bug, preventing some files to be correctly
     * URL encoded, we need to do some things by hand
     * Therefore we first try to read the files ourselves and convert
     * back manually.
     * On Windows however, we only get a javaFileListFlavor
     *
     * @param dtde
     * @return
     * @since 11.07.2020
     */
    public static List<?> getDropData(DropTargetDropEvent dtde) {
        List<?> files = null;
        boolean handled = false;
        try {
            Transferable transferable = dtde.getTransferable();
            if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor) && (dtde.getDropAction() & DnDConstants.ACTION_COPY_OR_MOVE) != 0) {
                dtde.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);

                // Check, if we have a flavor to read manually from
                DataFlavor[] flavors = transferable.getTransferDataFlavors();
                for (int flv = 0; flv < flavors.length && !handled; flv++) {
                    DataFlavor flavor = flavors[flv];

                    // KDE BUG: check for flavors to manually read from
                    if (flavor.isRepresentationClassReader()) {
                        Reader reader = flavor.getReaderForText(transferable);
                        files = Helpers.readLinesFromFlavor(new BufferedReader(reader));
                        handled = true; // we are already satisfied
                    }
                }
                if (!handled) { // no readable flavor was found - use the javaFileListFlavor
                    Object userObject = transferable.getTransferData(DataFlavor.javaFileListFlavor);
                    if (userObject != null && userObject instanceof List<?>) {
                        files = ((List<?>) userObject);
                        handled = true;
                    }
                }
            } else
                dtde.rejectDrop();
        } catch (Throwable ex) {
            logger.log(Level.ERROR, "Helpers::handleDrop", ex);
        } finally {
            dtde.dropComplete(handled);
        }
        return files;
    }

    /**
     * Compares to URLs and returns true, if they are equal and OK. This is done via
     * the URI - as URLs do a domain name lookup which will block if no
     * DNS Lookup is possible
     *
     * @param url1
     * @param url2
     * @return
     * @since 01.04.2012
     */
    public static boolean isEqualURL(URL url1, URL url2) {
        if (url1 != null && url2 != null) {
            try {
                URI uri1 = url1.toURI();
                URI uri2 = url2.toURI();
                return uri1.equals(uri2);
            } catch (URISyntaxException ex) {
                /* NOOP */
            }
        }
        return false;
    }

    /**
     * @param file
     * @return
     * @since 14.02.2012
     */
    public static URL createURLfromFile(File file) {
        if (!file.exists()) {
            try {
                String path = file.getPath();
                StringBuilder b = new StringBuilder((File.separatorChar != '/') ? path.replace(File.separatorChar, '/') : path);
                if (file.isDirectory() && b.charAt(b.length() - 1) != '/') b.append('/');
                if (b.length() > 2 && b.charAt(0) == '/' && b.charAt(1) == '/') b.insert(0, "//");
                if (b.charAt(0) != '/') b.insert(0, "/");
                URI uri = new URI("file", null, b.toString(), null);
                return uri.toURL();
            } catch (URISyntaxException e) {
                // cannot happen...
            } catch (MalformedURLException ex) {
                // should not happen ;)
            }
        }
        try {
            return file.toURI().toURL();
        } catch (MalformedURLException ex) {
        }
        return null;
    }

    /**
     * @param urlLine
     * @return a URL in correct form
     * @since 01.05.2011
     */
    public static URL createURLfromString(String urlLine) {
        try {
            if (urlLine == null || urlLine.isEmpty()) return null;
            URI uri = new URI(urlLine);
            return uri.toURL();
        } catch (Exception ex) {
            return createURLfromFile(new File(urlLine));
        }
    }

    /**
     * @param url
     * @return a decoded version of the URL - cannot be reversed to an URI
     * @since 22.07.2015
     */
    public static String createStringFomURL(URL url) {
        if (url == null) return EMPTY_STING;
        return createStringFromURLString(url.toExternalForm());
    }

    /**
     * @param url
     * @return a decoded version of the URL type string
     * @since 22.07.2015
     */
    public static String createStringFromURLString(String url) {
        try {
            return URLDecoder.decode(url, CODING_HTTP);
        } catch (UnsupportedEncodingException ex) {
            logger.log(Level.ERROR, "Helpers::createStringRomURLString", ex);
        }
        return url;
    }

    /**
     * @param fileName
     * @return
     * @since 22.07.2015
     */
    public static String getFileNameFrom(String fileName) {
        return fileName.substring(fileName.lastIndexOf('/') + 1);
    }

    /**
     * @param url
     * @return
     * @since 22.07.2015
     */
    public static String getFileNameFromURL(URL url) {
        return getFileNameFrom(createStringFomURL(url));
    }

    /**
     * @param fileName
     * @return
     * @since 22.07.2015
     */
    public static String getExtensionFrom(String fileName) {
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    /**
     * @param url
     * @return
     * @since 22.07.2015
     */
    public static String getExtensionFromURL(URL url) {
        return getExtensionFrom(createStringFomURL(url));
    }

    /**
     * @param fileName
     * @return
     * @since 22.07.2015
     */
    public static String getPreceedingExtensionFrom(String fileName) {
        fileName = fileName.substring(fileName.lastIndexOf('\\') + 1);
        fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
        int dot = fileName.indexOf('.');
        if (dot > 0)
            return fileName.substring(0, dot).toLowerCase();
        else
            return Helpers.EMPTY_STING;
    }

    /**
     * @param url
     * @return
     * @since 22.07.2015
     */
    public static String getPreceedingExtensionFromURL(URL url) {
        return getPreceedingExtensionFrom(url.getPath());
    }

    /**
     * replace illegal characters in a filename with "_"
     * illegal characters :
     * : \ / * ? | < >
     *
     * @param name
     * @return
     */
    public static String sanitizeFilename(String name) {
        return name.replaceAll("[:\\\\/*?|<>]", "_");
    }

    /**
     * @param protocol
     * @return
     * @since 05.01.2018
     */
    public static boolean isHTTP(String protocol) {
        return protocol.equalsIgnoreCase("http") || protocol.equalsIgnoreCase("https");
    }

    /**
     * @param url
     * @return
     * @since 05.01.2018
     */
    public static boolean isHTTP(URL url) {
        return isHTTP(url.getProtocol());
    }

    /**
     * @param protocol
     * @return
     * @since 05.01.2018
     */
    public static boolean isFile(String protocol) {
        return protocol.equalsIgnoreCase("file");
    }

    /**
     * @param url
     * @return
     * @since 05.01.2018
     */
    public static boolean isFile(URL url) {
        return isFile(url.getProtocol());
    }

    /**
     * @param url
     * @param stayLocal
     * @return
     * @since 22.07.2015
     */
    public static String createLocalFileStringFromURL(URL url, boolean stayLocal) {
        String suggestedPath = createStringFomURL(url);

        if (url != null) {
            if (isFile(url)) {
                try {
                    suggestedPath = new File(url.toURI()).toString();
                } catch (URISyntaxException ex) {
                    logger.log(Level.ERROR, "Helpers::createLocalFileStringFromURL", ex);
                }
            } else if (!isHTTP(url) && stayLocal) {
                try {
                    suggestedPath = HOMEDIR + File.pathSeparator + getFileNameFromURL(url);
                } catch (SecurityException ex) {
                    logger.log(Level.ERROR, "Helpers::createLocalFileStringFromURL", ex);
                }
            }
        }
        return suggestedPath;
    }

    /**
     * @param url
     * @return
     * @since 22.07.2015
     */
    public static boolean urlExists(URL url) {
        if (url == null) return false;

        if (isFile(url)) {
            try {
                File f = new File(url.toURI());
                return f.exists();
            } catch (Throwable ex) {
            }
            return false;
        }
        try {
            InputStream in = null;
            try {
                in = url.openStream();
            } catch (Throwable ex) {
                return false;
            } finally {
                if (in != null) try {
                    in.close();
                } catch (Throwable e) { /*NOOP */}
            }
            return true;
        } catch (Throwable ex) {
        }
        return false;
    }

    /**
     * Checks first the File exists method - will try URL then
     *
     * @param url
     * @return
     * @since 01.05.2011
     */
    public static boolean urlExists(String url) {
        return urlExists(Helpers.createURLfromString(url));
    }

    /**
     * Creates a relativized path that is relativ to the given basepath.
     * For instance:
     * relToPath = C:\Path1\Path2
     * Path = C:\Path1\Path3\FILE
     * Return is "..\Path3\File"
     *
     * @param basePathName
     * @param inputFileName
     * @return
     * @since 05.01.2018
     */
    public static String createRelativePathForFile(String basePathName, String inputFileName) {
        try {
            Path basePath = Paths.get(basePathName);
            Path inputFilePath = Paths.get(inputFileName);
            return basePath.relativize(inputFilePath).toString();
        } catch (Throwable ex) {
            return inputFileName;
        }
    }

    /**
     * If the baseURL provided is not absolut this method will generate an absolute file path
     * based on the inputFileName string
     * This works only for protocol "file"!!
     *
     * @param baseURL
     * @param inputFileName
     * @return
     * @since 23.03.2011
     */
    public static URL createAbsolutePathForFile(URL baseURL, String inputFileName) {
        String fileName = inputFileName;
        URL fileURL = createURLfromString(inputFileName);
        if (!isFile(fileURL)) return fileURL;
        try {
            if (Helpers.urlExists(fileName))
                return fileURL;
            else {
                // If fileName is from a Windows/DOS System, replace separator
                fileName = fileName.replace('\\', '/');

                // Get the path portion of the URL - and decode URL type entries (like %20 for spaces)
                String path = Helpers.createStringFromURLString(baseURL.getPath());

                // now get rid of playlist file name
                int lastSlash = path.lastIndexOf('/');
                StringBuilder relPath = new StringBuilder(path.substring(0, lastSlash + 1));
                // and remove a possible starting slash
                if (fileName.charAt(0) == '/') fileName = fileName.substring(1);

                int iterations = 0;
                URL fullURL = Helpers.createURLfromString(relPath + fileName);
                while (fullURL != null && !urlExists(fullURL) && iterations < 256) {
                    relPath.append("../");
                    fullURL = Helpers.createURLfromString(relPath + fileName);
                    iterations++;
                }
                if (iterations < 256 && fullURL != null) {
                    try {
                        return (fullURL.toURI().normalize()).toURL();
                    } catch (URISyntaxException x) {
                        logger.log(Level.ERROR, "[createAbsolutePathForFile]", x);
                    }
                    return fullURL;
                } else {
                    logger.log(Level.INFO, "File not found: " + inputFileName + " in relation to " + baseURL);
                    return Helpers.createURLfromString(inputFileName);
                }
            }
        } catch (Throwable ex) {
            logger.log(Level.ERROR, "[createAbsolutePathForFile]", ex);
        }
        logger.log(Level.INFO, "Illegal filename specification: " + inputFileName + " in playlist " + baseURL);
        return null;
    }

    /**
     * Retrieves a java.io.File object via FileChooser
     *
     * @param parent             can be null. If not, the filechooser is centered to the component
     * @param showDir            can be null. Is the start directory to begin the search or a preselected file
     * @param action             a String for the "open File"-Button
     * @param filter             a FileChooserFilter
     * @param acceptAllFiles     show "All Files" or not
     * @param type               0=load-mode 1=save-mode
     * @param multiFileSelection true: multiple Files can be selected
     * @return
     * @since 01.07.2006
     * @since 23.03.2011
     */
    public static FileChooserResult selectFileNameFor(java.awt.Component parent, String showDir, String action, FileFilter[] filter, boolean acceptAllFiles, int type, boolean multiFileSelection, boolean directoryOnly) {
        String dir = (showDir == null) ? HOMEDIR : showDir;
        // Try to work with URL - map "dir" to a local File
        try {
            File f = new File(dir);
            dir = f.getCanonicalPath();
        } catch (Exception ex) {
            logger.log(Level.ERROR, "Helpers::selectFileNameFor", ex);
        }

        File theFile = new File(dir);
        File theDirectory = new File(dir);
        while (theDirectory != null && (!theDirectory.isDirectory() || !theDirectory.exists())) {
            theDirectory = theDirectory.getParentFile();
        }
        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser(theDirectory);
        if (filter != null) {
            chooser.setAcceptAllFileFilterUsed(false);
            for (int i = filter.length - 1; i >= 0; i--) // count downwards 'cause the last one is the default
                chooser.addChoosableFileFilter(filter[i]);
        }
        chooser.setAcceptAllFileFilterUsed(acceptAllFiles);
        if (!theFile.isDirectory()) chooser.setSelectedFile(theFile);
        chooser.setApproveButtonText(action);
        chooser.setMultiSelectionEnabled(multiFileSelection);
        chooser.setFileSelectionMode((directoryOnly) ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.FILES_ONLY);
        int result = (type == 0) ? chooser.showOpenDialog(parent) : chooser.showSaveDialog(parent);

        if (result == javax.swing.JFileChooser.APPROVE_OPTION) {
            File[] selectedFiles = (multiFileSelection) ? chooser.getSelectedFiles() : new File[] {chooser.getSelectedFile()};
            return new FileChooserResult(chooser.getFileFilter(), selectedFiles);
        } else
            return null;
    }

    /**
     * @param location
     * @return a string representing the point class
     * @since 05.01.2008
     */
    public static String getStringFromPoint(java.awt.Point location) {
        return String.valueOf((int) location.getX()) + 'x' + (int) location.getY();
    }

    /**
     * @param point
     * @return a Point class from the string
     * @since 05.01.2008
     */
    public static java.awt.Point getPointFromString(String point) {
        int xIndex = point.indexOf('x');
        String x = point.substring(0, xIndex);
        String y = point.substring(xIndex + 1);
        return new java.awt.Point(Integer.parseInt(x), Integer.parseInt(y));
    }

    /**
     * @param dimension
     * @return a string representing the point class
     * @since 05.01.2008
     */
    public static String getStringFromDimension(java.awt.Dimension dimension) {
        return String.valueOf((int) dimension.getWidth()) + 'x' + (int) dimension.getHeight();
    }

    /**
     * @param dimension
     * @return a Point class from the string
     * @since 05.01.2008
     */
    public static java.awt.Dimension getDimensionFromString(String dimension) {
        int xIndex = dimension.indexOf('x');
        String width = dimension.substring(0, xIndex);
        String height = dimension.substring(xIndex + 1);
        return new java.awt.Dimension(Integer.parseInt(width), Integer.parseInt(height));
    }

    /**
     * @param insets
     * @return
     * @since 12.07.2009
     */
    public static java.awt.Insets getInsetsFromString(String insets) {
        StringTokenizer tok = new StringTokenizer(insets, ",");
        int left = Integer.parseInt(tok.nextToken().trim());
        int top = Integer.parseInt(tok.nextToken().trim());
        int right = Integer.parseInt(tok.nextToken().trim());
        int bottom = Integer.parseInt(tok.nextToken().trim());
        return new Insets(top, left, bottom, right);
    }

    /**
     * @param color
     * @return
     * @since 12.07.2009
     */
    public static java.awt.Color getColorFromString(String color) {
        StringTokenizer tok = new StringTokenizer(color, ",");
        int r = Integer.parseInt(tok.nextToken().trim());
        int g = Integer.parseInt(tok.nextToken().trim());
        int b = Integer.parseInt(tok.nextToken().trim());
        return new Color(r, g, b);
    }

    /**
     * Convert from decimalValue to DeciBel
     *
     * @param dbValue
     * @return
     * @since 14.01.2012
     */
    public static double getDecimalValueFrom(double dbValue) {
        return Math.pow(10, dbValue / 20.0d);
    }

    /**
     * convert from DeciBel to decimalValue
     *
     * @param decimalValue
     * @return
     * @since 14.01.2012
     */
    public static double getDBValueFrom(double decimalValue) {
        return Math.log10(decimalValue) * 20.0d;
    }

    /**
     * @param millis
     * @return
     * @since 06.02.2011
     */
    public static String getTimeStringFromMilliseconds(long millis) {
        int sec = (int) ((millis / 1000L) % 60L);
        int min = (int) (millis / 60000L);
        StringBuilder sb = new StringBuilder();
        if (min < 10) sb.append(' ');
        sb.append(min).append(':');
        if (sec < 10) sb.append('0');
        sb.append(sec);
        return sb.toString();
    }

    /**
     * @param timeString
     * @return
     * @since 03.04.2011
     */
    public static long getMillisecondsFromTimeString(String timeString) {
        int minIndex = timeString.indexOf(':');
        int min = Integer.parseInt(timeString.substring(0, minIndex).trim());
        String secString = timeString.substring(minIndex + 1);
        int secIndex = secString.indexOf(':');
        if (secIndex == -1) secIndex = secString.length();
        int sec = Integer.parseInt(secString.substring(0, secIndex).trim());
        return (min * 60 + sec) * 1000L;
    }

    /**
     * Prints the info about all installed and available audio lines
     *
     * @since 09.07.2006
     */
    private static String AudioInfos = null;

    public static String getAudioInfos() {
        if (AudioInfos == null) {
            StringBuilder result = (new StringBuilder("Running on ")).append(System.getProperty("os.arch"));
            result.append("\nMixerInfo:\n");
            Mixer.Info[] mixerInfo = AudioSystem.getMixerInfo();
            for (Mixer.Info value : mixerInfo) {
                result.append("MixerInfo: ").append(value).append('\n');
                Mixer mixer = AudioSystem.getMixer(value);
                result.append("Mixer: ").append(mixer).append('\n');
                result.append("Devices for recording:\n");
                Line.Info[] targetLineInfos = mixer.getTargetLineInfo();
                if (targetLineInfos.length == 0)
                    result.append("\tNONE\n");
                else {
                    for (int j = 0; j < targetLineInfos.length; j++) {
                        result.append("\tTargetline(").append(j).append("): ").append(targetLineInfos[j]).append('\n');
                        if (targetLineInfos[j] instanceof DataLine.Info) {
                            AudioFormat[] audioFormats = ((DataLine.Info) targetLineInfos[j]).getFormats();
                            for (int u = 0; u < audioFormats.length; u++) {
                                result.append("\t\tAudioformat(").append(u).append("): ").append(audioFormats[u]);
                                DataLine.Info info = new DataLine.Info(TargetDataLine.class, audioFormats[u]);
                                if (!AudioSystem.isLineSupported(info)) {
                                    result.append(" --> NOT supported.\n");
                                } else {
                                    result.append(" --> supported.\n");
                                }
                            }
                        }
                    }
                }
                result.append("\nDevices for sound output:\n");
                Line.Info[] sourceLineInfos = mixer.getSourceLineInfo();
                if (sourceLineInfos.length == 0)
                    result.append("\tNONE\n");
                else {
                    for (int j = 0; j < sourceLineInfos.length; j++) {
                        result.append("\tSourceline(").append(j).append("): ").append(sourceLineInfos[j]).append('\n');
                        if (sourceLineInfos[j] instanceof DataLine.Info) {
                            AudioFormat[] audioFormats = ((DataLine.Info) sourceLineInfos[j]).getFormats();
                            for (int u = 0; u < audioFormats.length; u++) {
                                result.append("\t\tAudioformat(").append(u).append("): ").append(audioFormats[u]);
                                DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormats[u]);
                                if (!AudioSystem.isLineSupported(info)) {
                                    result.append(" --> NOT supported.\n");
                                } else {
                                    result.append(" --> supported.\n");
                                }
                            }
                        }
                    }
                }
                result.append("---------------------------------------------------------------------\n");
            }
            AudioInfos = result.toString();
        }
        return AudioInfos;
    }

    /**
     * Registers all Classes that should not load during playback
     * This is also for registering all supported "plugin" loaders at the factory
     *
     * @throws ClassNotFoundException
     * @since 26.12.2007
     */
    public static void registerAllClasses() {
        // Just load these in advance
        try {
            Class.forName("de.quippy.javamod.system.Helpers");
        } catch (ClassNotFoundException ex) {
            logger.log(Level.ERROR, "JavaModMainBase: a class moved!", ex);
        }

        // Interpolation Routines - doing pre-calculations through class loading
        try {
            Class.forName("de.quippy.javamod.multimedia.mod.mixer.interpolation.CubicSpline");
        } catch (ClassNotFoundException ex) {
            logger.log(Level.ERROR, "JavaModMainBase: a class moved!", ex);
        }
        try {
            Class.forName("de.quippy.javamod.multimedia.mod.mixer.interpolation.Kaiser");
        } catch (ClassNotFoundException ex) {
            logger.log(Level.ERROR, "JavaModMainBase: a class moved!", ex);
        }
        try {
            Class.forName("de.quippy.javamod.multimedia.mod.mixer.interpolation.WindowedFIR");
        } catch (ClassNotFoundException ex) {
            logger.log(Level.ERROR, "JavaModMainBase: a class moved!", ex);
        }

        // The following are essential for registration at the MultimediaContainerManager
        // ModContainer uses the ModFactory!!
        try {
            Class.forName("de.quippy.javamod.multimedia.mod.ModContainer");
        } catch (ClassNotFoundException ex) {
            logger.log(Level.INFO, "No MOD file support!");
        }
        try {
            Class.forName("de.quippy.javamod.multimedia.wav.WavContainer");
        } catch (ClassNotFoundException ex) {
            logger.log(Level.INFO, "No WAV file support!");
        }
        try {
            Class.forName("de.quippy.javamod.multimedia.mp3.MP3Container");
        } catch (ClassNotFoundException ex) {
            logger.log(Level.INFO, "No MP3 file support!");
        }
        try {
            Class.forName("de.quippy.javamod.multimedia.ogg.OGGContainer");
        } catch (ClassNotFoundException ex) {
            logger.log(Level.INFO, "No OGG file support!");
        }
        try {
            Class.forName("de.quippy.javamod.multimedia.ape.APEContainer");
        } catch (ClassNotFoundException ex) {
            logger.log(Level.INFO, "No APE file support!");
        }
        try {
            Class.forName("de.quippy.javamod.multimedia.flac.FLACContainer");
        } catch (ClassNotFoundException ex) {
            logger.log(Level.INFO, "No FLAC file support!");
        }
        try {
            Class.forName("de.quippy.javamod.multimedia.midi.MidiContainer");
        } catch (ClassNotFoundException ex) {
            logger.log(Level.INFO, "No MIDI file support!");
        }
        try {
            Class.forName("de.quippy.javamod.multimedia.opl3.OPL3Container");
        } catch (ClassNotFoundException ex) {
            logger.log(Level.INFO, "No OPL file support!");
        }

        // SID and SID WAVE Loading
        try {
            Class.forName("de.quippy.javamod.multimedia.sid.SIDContainer");
        } catch (ClassNotFoundException ex) {
            logger.log(Level.INFO, "No SID file support!");
        }
        try {
            Class.forName("de.quippy.sidplay.resid_builder.resid.Wave");
        } catch (ClassNotFoundException ex) {
            logger.log(Level.INFO, "No SID WAVE support!");
        }
    }

    /**
     * Opens a txt-File on the server containing the current populated
     * Version.
     * Compare to Helpers.VERSION
     *
     * @return Null, if check fails, or the current Version populated
     * @since 19.10.2008
     */
    public static String getCurrentServerVersion() {
        BufferedReader reader = null;
        try {
            URL version_url = createURLfromString(Helpers.VERSION_URL);
            reader = new BufferedReader(new InputStreamReader(version_url.openStream(), Helpers.CODING_HTTP));
            String version = reader.readLine();
            reader.close();
            reader = null;
            return version;
        } catch (Throwable ex) {
            /* NOOP */
        } finally {
            if (reader != null) try {
                reader.close();
            } catch (Exception ex) { /* logger.log(Level.ERROR, "IGNORED", ex); */ }
        }
        return null;
    }

    /**
     * Compares two version strings
     *
     * @param v1
     * @param v2
     * @return v1&gt;v2: 1; v1==v2: 0; v1&lt;v2: -1
     * @since 16.01.2010
     */
    public static int compareVersions(String v1, String v2) {
        if (v1 == null || v2 == null) return 0;

        if (v1.startsWith("V")) v1 = v1.substring(1);
        StringTokenizer t1 = new StringTokenizer(v1, ".");
        if (v2.startsWith("V")) v2 = v2.substring(1);
        StringTokenizer t2 = new StringTokenizer(v2, ".");
        while (t1.hasMoreTokens() && t2.hasMoreTokens()) {
            int subV1 = Integer.parseInt(t1.nextToken());
            int subV2 = Integer.parseInt(t2.nextToken());
            if (subV1 < subV2) return -1;
            if (subV1 > subV2) return 1;
        }
        if (t1.hasMoreTokens() && !t2.hasMoreTokens()) return 1;
        if (!t1.hasMoreTokens() && t2.hasMoreTokens()) return -1;
        return 0;
    }

    /**
     * @param destination
     * @param bar         A JProgressBar or null
     * @return
     * @since 19.10.2008
     */
    public static int downloadJavaMod(File destination, ProgressDialog bar) {
        try {
            URL javamod_url = createURLfromString(JAVAMOD_URL);
            return copyFromURL(javamod_url, destination, bar);
        } catch (Throwable ex) {
            logger.log(Level.ERROR, "CheckForUpdate failed", ex);
        }
        return -1;
    }

    /**
     * @param destination
     * @param fromURL
     * @since 22.04.2012
     */
    public static int copyFromURL(URL fromURL, File destination, ProgressDialog bar) {
        int copied = 0;
        InputStream in = null;
        OutputStream out = null;
        try {
            if (destination.createNewFile()) {
                URLConnection conn = fromURL.openConnection();
                if (bar != null) {
                    bar.setDetailMinimum(0);
                    bar.setDetailMaximum(conn.getContentLength());
                }
                in = new BufferedInputStream(conn.getInputStream());
                out = new BufferedOutputStream(new FileOutputStream(destination));
                byte[] input = new byte[8192];
                int len;
                while ((len = in.read(input, 0, 8192)) != -1) {
                    out.write(input, 0, len);
                    copied += len;
                    if (bar != null) bar.setDetailValue(copied);
                }
                out.flush();
            }
        } catch (Throwable ex) {
            logger.log(Level.ERROR, "CopyFromURL failed", ex);
        } finally {
            if (in != null) try {
                in.close();
            } catch (IOException ex) { /*NOOP*/ }
            if (out != null) try {
                out.close();
            } catch (IOException ex) { /*NOOP*/ }
        }
        return copied;
    }

    public static int limit(int value, int minValue, int maxValue) {
        return (value > maxValue) ? maxValue : ((value > minValue) ? minValue : value);
    }

    public static int limitMax(int value, int maxValue) {
        return (value > maxValue) ? maxValue : value;
    }

    public static long limit(long value, long minValue, long maxValue) {
        return (value > maxValue) ? maxValue : ((value > minValue) ? minValue : value);
    }

    public static long limitMax(long value, long maxValue) {
        return (value > maxValue) ? maxValue : value;
    }
}
