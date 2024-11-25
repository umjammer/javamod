/*
 * @(#) PatternContainer.java
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

import java.awt.Color;

import de.quippy.javamod.multimedia.mod.ModConstants;
import de.quippy.javamod.multimedia.mod.loader.Module;


/**
 * @author Daniel Becker
 * @since 28.04.2006
 */
public class PatternContainer {

    protected final Module parentMod;
    protected Pattern[] patterns;

    // MPTP specific information
    protected String[] channelNames;
    protected Color[] channelColors;
    protected boolean[] channelIsActive;

    /**
     * Constructor for PatternContainer
     */
    public PatternContainer(Module newParentMod, int anzPattern) {
        super();
        patterns = new Pattern[anzPattern];
        parentMod = newParentMod;
    }

    public PatternContainer(Module parentMod, int anzPattern, int rows) {
        this(parentMod, anzPattern);
        for (int i = 0; i < anzPattern; i++) createPattern(i, rows);
    }

    public PatternContainer(Module parentMod, int anzPattern, int rows, int channels) {
        this(parentMod, anzPattern);
        for (int i = 0; i < anzPattern; i++) createPattern(i, rows, channels);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < patterns.length; i++) {
            sb.append(i).append(". Pattern:\n");
            if (patterns[i] != null) sb.append(patterns[i].toString());
            sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * Set this PatternContainer to have Patterns with nChannels channels afterward
     *
     * @param nChannels
     * @since 24.11.2023
     */
    public void setToChannels(int nChannels) {
        for (int i = 0; i < patterns.length; i++) {
            if (patterns[i] != null) patterns[i].setToChannels(i, nChannels);
        }
    }

    /**
     * @return the parentMod
     */
    public Module getParentMod() {
        return parentMod;
    }

    public int getChannels() {
        return (patterns != null && patterns.length > 0 && patterns[0] != null) ? patterns[0].getChannels() : 0;
    }

    /**
     * @since 23.08.2008
     */
    public void resetRowsPlayed() {
        for (Pattern pattern : patterns) if (pattern != null) pattern.resetRowsPlayed();
    }

    /**
     * @return Returns the patterns.
     */
    public Pattern[] getPattern() {
        return patterns;
    }

    /**
     * @return Returns the pattern.
     */
    public Pattern getPattern(int patternIndex) {
        return patterns[patternIndex];
    }

    /**
     * @return Returns the pattern.
     */
    public PatternRow getPatternRow(int patternIndex, int row) {
        return (patterns[patternIndex] != null) ? patterns[patternIndex].getPatternRow(row) : null;
    }

    /**
     * @return Returns the pattern.
     */
    public PatternElement getPatternElement(int patternIndex, int row, int channel) {
        return (patterns[patternIndex] != null) ? patterns[patternIndex].getPatternElement(row, channel) : null;
    }

    /**
     * @param newPatterns The pattern to set.
     */
    public void setPatterns(Pattern[] newPatterns) {
        patterns = newPatterns;
    }

    /**
     * @param newPattern The patterns to set.
     */
    public void setPattern(int patternIndex, Pattern newPattern) {
        patterns[patternIndex] = newPattern;
    }

    public Pattern createPattern(int patternIndex, int rows) {
        Pattern newPattern = new Pattern(parentMod, this, rows);
        patterns[patternIndex] = newPattern;
        return newPattern;
    }

    public Pattern createPattern(int patternIndex, int rows, int channels) {
        Pattern newPattern = new Pattern(parentMod, this, rows, channels);
        patterns[patternIndex] = newPattern;
        return newPattern;
    }

    /**
     * @param patternRow The patterns to set.
     */
    public void setPatternRow(int patternIndex, int row, PatternRow patternRow) {
        patterns[patternIndex].setPatternRow(row, patternRow);
    }

    public PatternRow createPatternRow(int patternIndex, int row, int channels) {
        Pattern currentPattern = getPattern(patternIndex);
        currentPattern.setPatternRow(row, new PatternRow(parentMod, currentPattern, channels));
        return getPatternRow(patternIndex, row);
    }

    /**
     * @param patternElement The patterns to set.
     */
    public void setPatternElement(int patternIndex, int row, int channel, PatternElement patternElement) {
        patterns[patternIndex].setPatternElement(row, channel, patternElement);
    }

    public PatternElement createPatternElement(int patternIndex, int row, int channel) {
        PatternRow currentPatternRow = getPatternRow(patternIndex, row);
        boolean isImpulseTracker = (parentMod.getModType() & ModConstants.MODTYPE_IMPULSETRACKER) != 0;
        PatternElement newElement = (isImpulseTracker) ? new PatternElementIT(parentMod, currentPatternRow, patternIndex, row, channel) : new PatternElementXM(parentMod, currentPatternRow, patternIndex, row, channel);
        currentPatternRow.setPatternElement(channel, newElement);
        return newElement;
    }

    /**
     * @param patternElement The patterns to set.
     */
    public void setPatternElement(PatternElement patternElement) {
        patterns[patternElement.getPatternIndex()].setPatternElement(patternElement.getRow(), patternElement.getChannel(), patternElement);
    }

    /**
     * Copies the Channel Names, if any
     *
     * @param chnNames
     * @since 06.02.2024
     */
    public void setChannelNames(String[] chnNames) {
        if (chnNames == null) return;

        int anzChannels = (patterns != null && patterns[0] != null) ? patterns[0].getChannels() : chnNames.length;
        channelNames = new String[anzChannels];
        for (int c = 0; c < anzChannels; c++) {
            channelNames[c] = (c < chnNames.length) ? chnNames[c] : null;
        }
    }

    /**
     * @return
     * @since 06.02.2024
     */
    public String[] getChannelNames() {
        return channelNames;
    }

    /**
     * @param channel
     * @return
     * @since 06.02.2024
     */
    public String getChannelName(int channel) {
        if (channelNames != null && channel < channelNames.length) return channelNames[channel];
        return null;
    }

    /**
     * We use the panningValues to identify muted channels
     * This is only for display
     *
     * @param panningValues
     * @since 19.07.2024
     */
    public void setChannelActiveStatus(int[] panningValues) {
        if (panningValues == null) return;

        int anzChannels = (patterns != null && patterns[0] != null) ? patterns[0].getChannels() : panningValues.length;
        channelIsActive = new boolean[anzChannels];
        for (int c = 0; c < anzChannels; c++) {
            channelIsActive[c] = (c < panningValues.length) ? ((panningValues[c] & ModConstants.CHANNEL_IS_MUTED) == 0) : false;
        }
    }

    /**
     * @return
     * @since 19.07.2024
     */
    public boolean[] getChannelIsActive() {
        return channelIsActive;
    }

    /**
     * @param channel
     * @return
     * @since 19.07.2024
     */
    public boolean getIsChannelActive(int channel) {
        if (channelIsActive != null && channel < channelIsActive.length) return channelIsActive[channel];
        return true;
    }

    /**
     * Copies the Channel Names, if any
     *
     * @param chnColors
     * @since 07.02.2024
     */
    public void setChannelColor(Color[] chnColors) {
        int anzChannels = (patterns != null && patterns[0] != null) ? patterns[0].getChannels() : chnColors.length;
        channelColors = new Color[anzChannels];
        for (int c = 0; c < anzChannels; c++) {
            channelColors[c] = (c < chnColors.length) ? chnColors[c] : null;
        }
    }

    /**
     * @return
     * @since 07.02.2024
     */
    public Color[] getChannelColors() {
        return channelColors;
    }

    /**
     * @param channel
     * @return
     * @since 07.02.2024
     */
    public Color getChannelColor(int channel) {
        if (channelColors != null && channel < channelColors.length) return channelColors[channel];
        return null;
    }

    /**
     * Of course we do not check if "rainbow colors" was selected in the ModPlug setup (we can't)
     * so we just assume the default "random". This is transformed from CModDoc::SetDefaultChannelColors
     * from OpenModPlug
     *
     * @since 07.02.2024
     */
    private static final boolean rainbow = false; // assumed as default for now - could be a configuration sometime...

    public void createMPTMDefaultRainbowColors() {
        channelColors = new Color[getChannels()];
        int numGroups = 0;
        if (rainbow) {
            for (int c = 1; c < channelColors.length; c++)
                if (channelNames == null || channelNames[c] == null || channelNames[c].isEmpty() || !channelNames[c].equals(channelNames[c - 1]))
                    numGroups++;
        }
        double hueFactor = (rainbow) ? (1.5d * Math.PI) / (double) ((numGroups > 1) ? numGroups - 1 : 1) : 1000d;  // Three quarters of the color wheel, red to purple
        for (int c = 0, group = 0; c < channelColors.length; c++) {
            if (c > 0 && (channelNames == null || channelNames[c] == null || channelNames[c].isEmpty() || !channelNames[c].equals(channelNames[c - 1])))
                group++;
            double hue = group * hueFactor;    // 0...2pi
            final double saturation = 0.3d;    // 0...2/3
            final double brightness = 1.2d;    // 0...4/3
            int r = (int) Math.min(brightness * (1 + saturation * (Math.cos(hue) - 1.0)) * 255d, 255d);
            int g = (int) Math.min(brightness * (1 + saturation * (Math.cos(hue - 2.09439) - 1.0)) * 255d, 255d);
            int b = (int) Math.min(brightness * (1 + saturation * (Math.cos(hue + 2.09439) - 1.0)) * 255d, 255d);
            channelColors[c] = new Color(r, g, b);
        }
    }
}
