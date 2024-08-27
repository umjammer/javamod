/*
 * @(#) MeterPanelBase.java
 *
 * Created on 01.01.2008 by Daniel Becker
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

package de.quippy.javamod.main.gui.components;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.Transparency;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.image.BufferedImage;
import java.io.Serial;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import javax.swing.border.Border;

import static java.lang.System.getLogger;


/**
 * @author Daniel Becker
 * @since 01.01.2008
 */
public abstract class MeterPanelBase extends ThreadUpdatePanel {

    private static final Logger logger = getLogger(MeterPanelBase.class.getName());

    @Serial
    private static final long serialVersionUID = -7284099301353768209L;

    protected volatile int myTop;
    protected volatile int myLeft;
    protected volatile int myWidth;
    protected volatile int myHeight;

    private BufferedImage imageBuffer;

    /**
     * Constructor for MeterPanelBase
     */
    public MeterPanelBase(int desiredFPS) {
        super(desiredFPS);
        prepareComponentListener();
    }

    /**
     * @since 06.10.2007
     */
    private void prepareComponentListener() {
        addComponentListener(new ComponentListener() {
            @Override
            public void componentHidden(ComponentEvent e) {
            }

            @Override
            public void componentMoved(ComponentEvent e) {
            }

            @Override
            public void componentShown(ComponentEvent e) {
            }

            @Override
            public void componentResized(ComponentEvent e) {
                internalComponentWasResized();
            }
        });
    }

    /**
     * Is called when the component is resized
     */
    protected synchronized void internalComponentWasResized() {
        Border b = this.getBorder();
        Insets inset = (b == null) ? new Insets(0, 0, 0, 0) : b.getBorderInsets(this);
        myTop = inset.top;
        myLeft = inset.left;
        myWidth = this.getWidth() - inset.left - inset.right;
        myHeight = this.getHeight() - inset.top - inset.bottom;

        imageBuffer = null;

        if (myWidth > 0 && myHeight > 0) componentWasResized(0, 0, myWidth, myHeight);
    }

    protected synchronized BufferedImage getDoubleBuffer(int myWidth, int myHeight) {
        if (imageBuffer == null && myWidth > 0 && myHeight > 0) {
            GraphicsConfiguration graConf = getGraphicsConfiguration();
            if (graConf != null) imageBuffer = graConf.createCompatibleImage(myWidth, myHeight, Transparency.OPAQUE);
        }
        return imageBuffer;
    }

    /**
     * @since 06.10.2007
     */
    @Override
    protected synchronized void doThreadUpdate() {
        BufferedImage buffer = getDoubleBuffer(myWidth, myHeight);
        if (buffer != null) {
            Graphics2D gfx = (Graphics2D) buffer.getGraphics();
            try {
                drawMeter(gfx, 0, 0, myWidth, myHeight);
                repaint(myTop, myLeft, myWidth, myHeight);
            } catch (Exception ex) {
                logger.log(Level.ERROR, "[MeterPanelBase]:", ex);
            } finally {
                gfx.dispose();
            }
        }
    }

    /**
     * @param g
     * @see javax.swing.JComponent#paintComponent(java.awt.Graphics)
     */
    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        BufferedImage buffer = getDoubleBuffer(myWidth, myHeight);
        if (buffer != null && g != null) {
            Graphics2D gfx = (Graphics2D) g.create();
            try {
                gfx.drawImage(buffer, myLeft, myTop, null);
            } finally {
                gfx.dispose();
            }
        }
    }

    /**
     * Draws the meter
     *
     * @param g
     * @since 01.01.2008
     */
    protected abstract void drawMeter(Graphics2D g, int newTop, int newLeft, int newWidth, int newHeight);

    /**
     * Will be called from "internalComponentWasResized
     * to signal a resize event
     *
     * @since 01.01.2008
     */
    protected abstract void componentWasResized(int newTop, int newLeft, int newWidth, int newHeight);
}
