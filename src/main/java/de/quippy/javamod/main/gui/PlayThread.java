/*
 * @(#) PlayThread.java
 *
 * Created on 18.05.2008 by Daniel Becker
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

package de.quippy.javamod.main.gui;

import java.io.Serial;
import java.io.Serializable;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.StringJoiner;

import de.quippy.javamod.mixer.Mixer;

import static java.lang.System.getLogger;


/**
 * @author Daniel Becker
 * @since 18.05.2008
 */
public final class PlayThread extends Thread implements Serializable {

    private static final Logger logger = getLogger(PlayThread.class.getName());

    @Serial
    private static final long serialVersionUID = 3546401588411431506L;

    private transient final Mixer currentMixer;
    private final PlayThreadEventListener listener;
    private volatile boolean isRunning;
    private volatile boolean finishedNormally;

    public PlayThread(Mixer currentMixer, PlayThreadEventListener listener) {
        if (currentMixer == null) throw new IllegalArgumentException("Provided Mixer was NULL");
        this.currentMixer = currentMixer;
        this.isRunning = false;
        this.finishedNormally = false;
        this.listener = listener;
        this.setName("PlayThread");
        this.setDaemon(true);
        try {
            this.setPriority(Thread.MAX_PRIORITY);
        } catch (SecurityException ex) { /* NOOP */ }
    }

    private void informListener() {
        listener.playThreadEventOccurred(this);
    }

    public void stopPlayback() {
        if (isRunning) {
            this.currentMixer.stopPlayback();
            while (isRunning) {
                try {
                    Thread.sleep(10L);
                } catch (InterruptedException ex) { /* NOOP */ }
            }
        }
    }

    public void pausePlayback() {
        if (isRunning) this.currentMixer.pausePlayback();
        informListener();
    }

    public Mixer getCurrentMixer() {
        return currentMixer;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public boolean getHasFinishedNormally() {
        return finishedNormally;
    }

    @Override
    public void run() {
        this.isRunning = true;
        this.finishedNormally = false;
        informListener();
        try {
            getCurrentMixer().startPlayback();
        } catch (Throwable ex) {
            logger.log(Level.ERROR, "[PlayThread::run]", ex);
        }
        this.isRunning = false;
        this.finishedNormally = getCurrentMixer().hasFinished();
        informListener();
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", PlayThread.class.getSimpleName() + "[", "]")
                .add("isRunning=" + isRunning)
                .add("finishedNormally=" + finishedNormally)
                .add("currentMixer=" + currentMixer)
                .toString();
    }
}
