/*
 * @(#) DSPEffect.java
 *
 * Created on 09.01.2012 by Daniel Becker
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

package de.quippy.javamod.mixer.dsp;

import javax.sound.sampled.AudioFormat;


/**
 * @author Daniel Becker
 */
public interface DSPEffect {

    void initialize(AudioFormat audioFormat, int sampleBufferLength);

    int doEffect(float[] buffer, int start, int length);

    void setIsActive(boolean active);

    boolean isActive();
}
