/*
 * @(#) PatternRow.java
 *
 * Created on 28.04.2006 by Daniel Becker
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

package de.quippy.javamod.multimedia.mod.loader.pattern;

import de.quippy.javamod.multimedia.mod.loader.Module;


/**
 * @author Daniel Becker
 * @since 28.04.2006
 */
public class PatternRow {

    protected final Module parentMod;
    protected final Pattern parentPattern;
    protected PatternElement[] patternElements;
    protected boolean rowPlayed;

    /**
     * Constructor for PatternRow
     */
    public PatternRow(Module parentMod, Pattern parentPattern, int channels) {
        super();
        patternElements = new PatternElement[channels];
        this.parentMod = parentMod;
        this.parentPattern = parentPattern;
        resetRowPlayed();
    }

    /**
     * @return
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        addToStringBuilder(sb);
        return sb.toString();
    }

    /**
     * Add to patternRow string representation to a StringBuilder
     *
     * @param sb
     * @since 22.12.2023
     */
    public void addToStringBuilder(StringBuilder sb) {
        for (PatternElement patternElement : patternElements) {
            if (patternElement != null) patternElement.addToStringBuilder(sb);
            sb.append("|");
        }
    }

    /**
     * Set this row to have nChannels channels
     *
     * @param channels
     * @since 24.11.2023
     */
    public void setToChannels(int patternIndex, int row, int channels) {
        PatternElement[] oldPatternElements = patternElements;
        patternElements = new PatternElement[channels];
        for (int channel = 0; channel < channels; channel++) {
            if (channel < oldPatternElements.length && oldPatternElements[channel] != null)
                patternElements[channel] = oldPatternElements[channel];
            else
                parentPattern.parentPatternContainer.createPatternElement(patternIndex, row, channel);
        }
    }

    /**
     * @return the parentMod
     */
    public Module getParentMod() {
        return parentMod;
    }

    /**
     * @return the parentPattern
     */
    public Pattern getParentPattern() {
        return parentPattern;
    }

    /**
     * @return
     * @since 27.11.2023
     */
    public int getChannels() {
        return (patternElements != null) ? patternElements.length : 0;
    }

    /**
     * @since 23.08.2008
     */
    public void resetRowPlayed() {
        rowPlayed = false;
    }

    /**
     * @since 23.08.2008
     */
    public void setRowPlayed() {
        rowPlayed = true;
    }

    /**
     * @return
     * @since 23.08.2008
     */
    public boolean isRowPlayed() {
        return rowPlayed;
    }

    /**
     * @return Returns the patternElements.
     */
    public PatternElement[] getPatternElements() {
        return patternElements;
    }

    /**
     * @return Returns the patternElements.
     */
    public PatternElement getPatternElement(int channel) {
        return patternElements[channel];
    }

    /**
     * @param patternElement The patternElements to set.
     */
    public void setPatternElement(PatternElement[] patternElement) {
        this.patternElements = patternElement;
    }

    /**
     * @param patternElement The patternElements to set.
     */
    public void setPatternElement(int channel, PatternElement patternElement) {
        this.patternElements[channel] = patternElement;
    }
}
