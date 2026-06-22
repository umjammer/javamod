/*
 * @(#) PeekMeterComponent.java
 *
 * Created on 30.10.2025 by Daniel Becker
 *
 *-----------------------------------------------------------------------
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.io.Serial;
import javax.swing.JComponent;


/**
 * @author Daniel Becker
 * @since 30.10.2025
 */
public class PeekMeterComponent extends JComponent {

    @Serial
    private static final long serialVersionUID = -3051678250074031407L;
    private static final int MIDI_COLOR_START = 8;

    private int peekLeft, peekRight;
    private boolean isSurround, isMidiAdlib;

    private Color[] peekMeterColors;

    /**
     * Constructor for PeekMeterComponent
     */
    public PeekMeterComponent() {
        super();
        initialize();
    }

    private void initialize() {
        peekMeterColors = new Color[16];
//        for (int i = 0; i < 8; i++) {
//            int r = i * 255 / 8;
//            int g = 255 - r;
//            peekMeterColors[i] = new Color(r, g, 0);
//        }
        for (int i = 0; i < 8; i++) {
            peekMeterColors[i] = (i < 4) ? new Color(0x00, 0xC8, 0x00) : (i < 6) ? new Color(0xFF, 0xC8, 0x00) : new Color(0xE1, 0x00, 0x00);
            peekMeterColors[i + MIDI_COLOR_START] = (i < 4) ? new Color(0x18, 0x96, 0xE1) : (i < 6) ? new Color(0xFF, 0xC8, 0x00) : new Color(0xE1, 0x00, 0x00);
        }
    }

    /**
     * @param peekLeft   a value between 0 and 7
     * @param peekRight  a value between 0 and 7
     * @param isSurround is surrounding or not
     * @since 30.10.2025
     */
    public void setMeterValues(int peekLeft, int peekRight, boolean isSurround, boolean isMidiAdlib) {
        // do we have to repaint?
        if (this.peekLeft == peekLeft && this.peekRight == peekRight &&
                this.isSurround == isSurround && this.isMidiAdlib == isMidiAdlib) return;
        // yes, we need to repaint
        this.peekLeft = peekLeft;
        this.peekRight = peekRight;
        this.isSurround = isSurround;
        this.isMidiAdlib = isMidiAdlib;
        repaint();
    }

    /**
     * @param g the graphics
     * @param x start x
     * @param y start y
     * @param width x size
     * @param height y size
     * @param clipping the cripping area
     * @since 30.10.2025
     */
    private static void fillRectWithClipping(Graphics2D g, int x, int y, int width, int height, Rectangle clipping) {
        Rectangle fillMe = new Rectangle(x, y, width, height);
        if (clipping != null) fillMe = clipping.intersection(fillMe);
        if (fillMe.width > 0 || fillMe.height > 0) g.fillRect(fillMe.x, fillMe.y, fillMe.width, fillMe.height);
    }

    /**
     * @param g the graphics
     * @since 30.10.2025
     */
    private void drawMeter(Graphics2D g) {
        // let's clean up and fill with background
        Dimension d = this.getSize();
        g.setColor(getBackground());
        g.fillRect(0, 0, d.width, d.height);

        // get dimensions
        int middle = d.width >> 1;
        int barWidth = middle >> 3;
        int height = (d.height < 3) ? 1 : d.height - 2;

        // with surround, we will draw "backwards" towards center
        // just so we have some kind of indication
        int xLeft, xRight, addLeft, addRight;
        if (!isSurround) {
            xLeft = middle - barWidth;
            xRight = middle + 1;
            addLeft = -barWidth;
            addRight = barWidth;
        } else {
            xLeft = middle - (barWidth * 8);
            xRight = middle + 1 + (barWidth * 7);
            addLeft = barWidth;
            addRight = -barWidth;
        }

        // draw the rectangles with clipping considered
        Rectangle clipping = g.getClipBounds();
        for (int i = 0, c = (isMidiAdlib) ? MIDI_COLOR_START : 0; i < 8; i++, c++) {
            if (i < peekLeft) {
                g.setColor(peekMeterColors[c]);
                fillRectWithClipping(g, xLeft, 1, barWidth - 1, height, clipping);
            }
            xLeft += addLeft;
            if (i < peekRight) {
                g.setColor(peekMeterColors[c]);
                fillRectWithClipping(g, xRight, 1, barWidth - 1, height, clipping);
            }
            xRight += addRight;
        }
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D gfx = (Graphics2D) g.create();
        try {
            drawMeter(gfx);
        } finally {
            gfx.dispose();
        }
    }
}
