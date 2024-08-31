/*
 * @(#) CommandLine.java
 *
 * Created on 20.05.2006 by Daniel Becker
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

package de.quippy.javamod.main;

import java.io.File;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URL;
import java.util.Properties;

import de.quippy.javamod.main.gui.PlayThread;
import de.quippy.javamod.main.gui.PlayThreadEventListener;
import de.quippy.javamod.main.playlist.PlayList;
import de.quippy.javamod.mixer.Mixer;
import de.quippy.javamod.multimedia.MultimediaContainer;
import de.quippy.javamod.multimedia.MultimediaContainerManager;
import de.quippy.javamod.multimedia.mod.ModContainer;
import de.quippy.javamod.system.Helpers;

import static java.lang.System.getLogger;


/**
 * @author Daniel Becker
 * @since 20.05.2006
 * Commandline version:
 * -i+/-: do/don't Interpolation
 * -s+/-: Stereo/Mono
 * -w+/-: do/don't wide stereo mix
 * -n+/-: do/don't noise reduction
 * -m+/-: do/don't mega bass
 * -b8/16: #Bits per sample
 * -rx: use Samplerate x (8000/11025/22050/44100/96000... anything allowed, your soundhardware supports
 * -eWAVEFILE: export to wave file
 */
public class CommandLine extends JavaModMainBase implements PlayThreadEventListener {

    private static final Logger logger = getLogger(CommandLine.class.getName());

    private URL modFileName;
    private File wavFileName;
    private boolean shuffle;
    private boolean repeat;
    private float initialVolume = 1.0f;
    private MultimediaContainer currentContainer;
    private PlayThread playerThread = null;
    private PlayList currentPlayList = null;

    /**
     * Constructor for CommandLine
     */
    public CommandLine() {
        super(false);
    }

    /**
     * Show a help screen...
     */
    private static void showHelp() {
        System.err.println("java -jar ./javamod [-rx] [-b{8,16,24,32}] [-s{+,-}] [-i{+,-}] [-w{+,-}] [-n{+,-}] [-m{+,-}] [-d{+,-}] [-l{+,-}] [-ax] [-h{+,-}]");
        System.err.println("                    [-j{+,-}] [-v0.0-1.0] [-eWAVFILE] MODFILE\n");
        System.err.println("-rx        : use Samplerate x (8000/11025/22050/44100/96000...");
        System.err.println("                               anything your soundhardware supports)");
        System.err.println("-b8/16/24  : #Bits per sample");
        System.err.println("-s+/-      : Stereo/Mono");
        System.err.println("-i0/1/2/3  : interpolation: 0:none; 1:linear; 2:cubic spline; 3:fir interpolation");
        System.err.println("-tms       : ms of buffer size (30 is minimum)");
        System.err.println("-w+/-      : do/don't wide stereo mix");
        System.err.println("-n+/-      : do/don't noise reduction");
        System.err.println("-m+/-      : do/don't mega bass");
        System.err.println("-d+/-      : do/don't dc removal");
        System.err.println("-l0/1/2/4  : set loop handling: 0:original; 1:fade out; 2:ignore; 4:loop song (4 can be added to 0, 1 or 2)");
        System.err.println("-ax        : set max NNAs channels used");
        System.err.println("-h+/-      : do/don't shuffle playlists after loading");
        System.err.println("-j+/-      : do/don't repeat playlist");
        System.err.println("-v0.0-1.0  : set volume");
        System.err.println("-eWAVEFILE : export to wave file");
        System.err.println("Dithering  : no dithering settings in command line version");
    }

    /**
     * Will parse the parameters and set the internal values
     *
     * @param args
     */
    private void parseParameters(String[] args) {
        Properties props = new Properties();

        for (String arg : args) {
            if (arg.startsWith("-")) { // parameter:
                String op = arg.substring(2);
                switch (arg.toLowerCase().charAt(1)) {
                    case 'i':
                        props.setProperty(ModContainer.PROPERTY_PLAYER_ISP, Integer.toString(Integer.parseInt(op.substring(0, 1))));
                        break;
                    case 's':
                        props.setProperty(ModContainer.PROPERTY_PLAYER_STEREO, (op.charAt(0) == '+') ? "2" : "1");
                        break;
                    case 'w':
                        props.setProperty(ModContainer.PROPERTY_PLAYER_WIDESTEREOMIX, (op.charAt(0) == '+') ? "TRUE" : "FALSE");
                        break;
                    case 'n':
                        props.setProperty(ModContainer.PROPERTY_PLAYER_NOISEREDUCTION, (op.charAt(0) == '+') ? "TRUE" : "FALSE");
                        break;
                    case 'm':
                        props.setProperty(ModContainer.PROPERTY_PLAYER_MEGABASS, (op.charAt(0) == '+') ? "TRUE" : "FALSE");
                        break;
                    case 'd':
                        props.setProperty(ModContainer.PROPERTY_PLAYER_DCREMOVAL, (op.charAt(0) == '+') ? "TRUE" : "FALSE");
                        break;
                    case 'l':
                        props.setProperty(ModContainer.PROPERTY_PLAYER_NOLOOPS, Integer.toString(Integer.parseInt(op.substring(0, 1))));
                        break;
                    case 't':
                        props.setProperty(ModContainer.PROPERTY_PLAYER_MSBUFFERSIZE, Integer.toString(Integer.parseInt(op)));
                        break;
                    case 'h':
                        shuffle = op.charAt(0) == '+';
                        break;
                    case 'j':
                        repeat = op.charAt(0) == '+';
                        break;
                    case 'b':
                        int sampleSizeInBits = Integer.parseInt(op);
                        if (sampleSizeInBits != 8 && sampleSizeInBits != 16 && sampleSizeInBits != 24 && sampleSizeInBits != 32)
                            throw new RuntimeException("samplesize of " + sampleSizeInBits + " is not supported");
                        props.setProperty(ModContainer.PROPERTY_PLAYER_BITSPERSAMPLE, Integer.toString(sampleSizeInBits));
                        break;
                    case 'r':
                        props.setProperty(ModContainer.PROPERTY_PLAYER_FREQUENCY, Integer.toString(Integer.parseInt(op)));
                        break;
                    case 'a':
                        props.setProperty(ModContainer.PROPERTY_PLAYER_MAXNNACHANNELS, Integer.toString(Integer.parseInt(op)));
                        break;
                    case 'e':
                        wavFileName = new File(op);
                        break;
                    case 'v':
                        initialVolume = Float.parseFloat(op);
                        break;
                    default:
                        throw new RuntimeException("Unknown parameter: " + arg.charAt(1));
                }
            } else {
                String fileName = arg;
                modFileName = Helpers.createURLfromString(fileName);
                if (modFileName == null) {
                    logger.log(Level.ERROR, "This is not parsable: " + fileName);
                    System.exit(-1);
                }
            }
        }

        MultimediaContainerManager.configureContainer(props);
    }

    /**
     * @param thread
     * @see de.quippy.javamod.main.gui.PlayThreadEventListener#playThreadEventOccured(de.quippy.javamod.main.gui.PlayThread)
     */
    @Override
    public void playThreadEventOccured(PlayThread thread) {
        if (!thread.isRunning() && thread.getHasFinishedNormaly()) {
            if (currentPlayList != null && currentPlayList.next())
                loadMultimediaFile(currentPlayList.getCurrentEntry().getFile());
            else
                System.exit(0);
        }
    }

    /**
     * Plays the modfile with the current
     * parameters set
     */
    private void doStartPlaying() {
        if (currentContainer != null) {
            doStopPlaying();
            if (currentContainer instanceof ModContainer) {
                System.out.println(((ModContainer) currentContainer).getCurrentMod().toString());
            }
            Mixer mixer = createNewMixer();
            mixer.setExportFile(wavFileName);
            playerThread = new PlayThread(mixer, this);
            playerThread.start();
        }
    }

    /**
     * stop playback of a mod
     */
    private void doStopPlaying() {
        if (playerThread != null) {
            playerThread.stopPlayback();
            playerThread = null;
        }
    }

    /**
     * Creates a new Mixer for playback
     *
     * @return
     * @since 01.07.2006
     */
    private Mixer createNewMixer() {
        Mixer mixer = currentContainer.createNewMixer();
        if (mixer != null) {
            mixer.setVolume(initialVolume);
        }
        return mixer;
    }

    /**
     * @param mediaPLSFileURL
     * @since 14.09.2008
     */
    private void loadMultimediaOrPlayListFile(URL mediaPLSFileURL) {
        currentPlayList = null;
        try {
            if (PlayList.isPlaylistFile(mediaPLSFileURL)) {
                currentPlayList = PlayList.createFromFile(mediaPLSFileURL, shuffle, repeat);
                if (currentPlayList.next()) {
                    mediaPLSFileURL = currentPlayList.getCurrentEntry().getFile();
                }
            }

            if (mediaPLSFileURL != null) loadMultimediaFile(mediaPLSFileURL);
        } catch (Throwable ex) {
            logger.log(Level.ERROR, "[MainForm::loadMultimediaOrPlayListFile]", ex);
            currentPlayList = null;
        }
    }

    /**
     * load a mod file and display it
     *
     * @param mediaFileURL
     * @since 01.07.2006
     */
    private void loadMultimediaFile(URL mediaFileURL) {
        try {
            if (mediaFileURL != null) {
                MultimediaContainer newContainer = MultimediaContainerManager.getMultimediaContainer(mediaFileURL);
                if (newContainer != null) currentContainer = newContainer;
            }
        } catch (Throwable ex) {
            logger.log(Level.ERROR, "[MainForm::loadMultimediaFile] Loading of " + mediaFileURL + " failed!", ex);
            currentContainer = null;
        } finally {
            // if we are currently playing, start the current piece:
            if (playerThread != null) doStartPlaying();
        }
    }

    /**
     * @param args
     */
    public static void main(String[] args) {
        logger.log(Level.INFO, Helpers.FULLVERSION + " " + Helpers.COPYRIGHT + "\n");
        try {
            if (args.length == 0) {
                showHelp();
            } else {
                CommandLine me = new CommandLine();
                me.parseParameters(args);
                me.loadMultimediaOrPlayListFile(me.modFileName);
                me.doStartPlaying();
                while (!me.playerThread.getHasFinishedNormaly()) {
                    Thread.sleep(10L);
                    if (!me.playerThread.isRunning()) break;
                }
            }
        } catch (Exception ex) {
            logger.log(Level.ERROR, "Error occured:", ex);
            showHelp();
            System.exit(-1);
        } finally {
            MultimediaContainerManager.cleanUpAllContainers();
        }
    }
}
