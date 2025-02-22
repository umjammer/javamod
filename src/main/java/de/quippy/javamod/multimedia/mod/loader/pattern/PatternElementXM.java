/*
 * @(#) PatternElementXM.java
 *
 * Created on 09.01.2024 by Daniel Becker
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

package de.quippy.javamod.multimedia.mod.loader.pattern;

import de.quippy.javamod.multimedia.mod.ModConstants;
import de.quippy.javamod.multimedia.mod.loader.Module;
import de.quippy.javamod.system.Helpers;


/**
 * @author Daniel Becker
 * @since 09.01.2024
 */
public class PatternElementXM extends PatternElement {

    /**
     * Constructor for PatternElementXM
     *
     * @param parentMod
     * @param patternIndex
     * @param patternRow
     * @param channel
     */
    public PatternElementXM(Module parentMod, PatternRow parentPatternRow, int patternIndex, int patternRow, int channel) {
        super(parentMod, parentPatternRow, patternIndex, patternRow, channel);
    }

    /**
     * @return
     * @see de.quippy.javamod.multimedia.mod.loader.pattern.PatternElement#getEffectChar()
     */
    @Override
    public char getEffectChar() {
        if (effect <= 0x0F)
            return ModConstants.numbers[effect];
        else if (effect == 0x24)
            return '\\';
        else if (effect == 0x26)
            return '#';
        else
            return (char) ('G' + effect - 0x10);
    }

    /**
     * @return
     * @see de.quippy.javamod.multimedia.mod.loader.pattern.PatternElement#getEffectName()
     */
    @Override
    public String getEffectName() {
        switch (effect) {
            case 0x00:
                return (effectOp == 0) ? Helpers.EMPTY_STING : "Arpeggio";
            case 0x01:
                return "Porta Up";
            case 0x02:
                return "Porta Down";
            case 0x03:
                return "Porta To Note";
            case 0x04:
                return "Vibrato";
            case 0x05:
                return "PortaNote + VolSlide";
            case 0x06:
                return "Vibrato + VolSlide";
            case 0x07:
                return "Tremolo";
            case 0x08:
                return "Set Panning";
            case 0x09:
                return "Sample Offset";
            case 0x0A:
                return "Volume Slide";
            case 0x0B:
                return "Pattern Position Jump";
            case 0x0C:
                return "Set volume";
            case 0x0D:
                return "Pattern break";
            case 0x0E:
                int effectOpEx = effectOp & 0x0F;
                switch (effectOp >> 4) {
                    case 0x0:
                        return "Set filter";
                    case 0x1:
                        return "Fine Porta Up";
                    case 0x2:
                        return "Fine Porta Down";
                    case 0x3:
                        return "Glissando";
                    case 0x4:
                        return "Set Vibrato Type";
                    case 0x5:
                        return "Set FineTune";
                    case 0x6:
                        if (effectOpEx == 0) return "Jump Loop Set";
                        else return "Jump Loop";
                    case 0x7:
                        return "Set Tremolo Type";
                    case 0x8:
                        return ((parentMod.getModType() & ModConstants.MODTYPE_MOD) != 0) ? "Karplus Strong" : "Set Fine Panning";
                    case 0x9:
                        return "Retrig Note";
                    case 0xA:
                        return "Fine Volume Up";
                    case 0xB:
                        return "Fine Volume Down";
                    case 0xC:
                        return "Note Cut";
                    case 0xD:
                        return "Note Delay";
                    case 0xE:
                        return "Pattern Delay";
                    case 0xF:
                        return ((parentMod.getModType() & ModConstants.MODTYPE_XM) != 0) ? "Set MIDI Macro" : "Funk It!";
                }
                break;
            case 0x0F:
                return (effectOp > 31 && !parentMod.getModSpeedIsTicks()) ? "Set BPM" : "Set Speed";
            case 0x10:
                return "Set global volume";
            case 0x11:
                return "Global Volume Slide";
            case 0x14:
                return "Key off";
            case 0x15:
                return "Set Envelope Position";
            case 0x19:
                return "Panning Slide";
            case 0x1B:
                return "Multi Retrig";
            case 0x1D:
                return "Tremor";
            case 0x20:
                return "Empty";
            case 0x21: // Extended XM Effects
                switch (effectOp >> 4) {
                    case 0x1:
                        return "Extra Fine Porta Up";
                    case 0x2:
                        return "Extra Fine Porta Down";
                    case 0x5:
                        return "set Panbrello Waveform";
                    case 0x6:
                        return "Fine Pattern Delay";
                    case 0x9: // Sound Control
                        switch (effectOp & 0x0F) {
                            case 0x0:
                                return "No Surround";
                            case 0x1:
                                return "Enable Surround";
                            // MPT Effects only
                            case 0x8:
                                return "No Reverb";
                            case 0x9:
                                return "Enable Reverb";
                            case 0xA:
                                return "Mono Surround";
                            case 0xB:
                                return "Quad Surround";
                            // ----------------
                            case 0xC:
                                return "Global FilterMode Off";
                            case 0xD:
                                return "Global FilterMode On";
                            case 0xE:
                                return "Play Forward";
                            case 0xF:
                                return "Play Backwards";
                        }
                        break;
                    case 0xA:
                        return "Set High Offset";
                }
                break;
            case 0x22:
                return "Panbrello";
            case 0x23:
                return "Midi Macro";
            case 0x24:
                return "Smooth Midi Macro";
            case 0x26:
                return "Parameter Extension";
        }
        //logger.log(Level.ERROR, "Unknown: " + ModConstants.getAsHex(effect, 2) + "/" + ModConstants.getAsHex(effectOp, 2));
        return Helpers.EMPTY_STING;
    }

    /**
     * @return
     * @see de.quippy.javamod.multimedia.mod.loader.pattern.PatternElement#getEffectCategory()
     */
    @Override
    public int getEffectCategory() {
        switch (effect) {
            case 0x00:
                return (effectOp == 0) ? EFFECT_NORMAL : EFFECT_PITCH;
            case 0x01:
                return EFFECT_PITCH;
            case 0x02:
                return EFFECT_PITCH;
            case 0x03:
                return EFFECT_PITCH;
            case 0x04:
                return EFFECT_PITCH;
            case 0x05:
                return EFFECT_VOLUME;
            case 0x06:
                return EFFECT_VOLUME;
            case 0x07:
                return EFFECT_VOLUME;
            case 0x08:
                return EFFECT_PANNING;
            case 0x09:
                return EFFECT_NORMAL;
            case 0x0A:
                return EFFECT_VOLUME;
            case 0x0B:
                return EFFECT_GLOBAL;
            case 0x0C:
                return EFFECT_VOLUME;
            case 0x0D:
                return EFFECT_GLOBAL;
            case 0x0E:
                switch (effectOp >> 4) {
                    case 0x0:
                        return EFFECT_PITCH;
                    case 0x1:
                        return EFFECT_PITCH;
                    case 0x2:
                        return EFFECT_PITCH;
                    case 0x3:
                        return EFFECT_PITCH;
                    case 0x4:
                        return EFFECT_PITCH;
                    case 0x5:
                        return EFFECT_PITCH;
                    case 0x6:
                        return EFFECT_GLOBAL;
                    case 0x7:
                        return EFFECT_VOLUME;
                    case 0x8:
                        return ((parentMod.getModType() & ModConstants.MODTYPE_MOD) != 0) ? ((ModConstants.SUPPORT_E8x_EFFECT) ? EFFECT_UNKNOWN : EFFECT_PITCH) : EFFECT_PANNING;
                    case 0x9:
                        return EFFECT_NORMAL;
                    case 0xA:
                        return EFFECT_VOLUME;
                    case 0xB:
                        return EFFECT_VOLUME;
                    case 0xC:
                        return EFFECT_NORMAL;
                    case 0xD:
                        return EFFECT_NORMAL;
                    case 0xE:
                        return EFFECT_GLOBAL;
                    case 0xF:
                        return EFFECT_NORMAL;
                }
                break;
            case 0x0F:
                return EFFECT_GLOBAL;
            case 0x10:
                return EFFECT_GLOBAL;
            case 0x11:
                return EFFECT_GLOBAL;
            case 0x14:
                return EFFECT_NORMAL;
            case 0x15:
                return EFFECT_NORMAL;
            case 0x19:
                return EFFECT_PANNING;
            case 0x1B:
                return EFFECT_NORMAL;
            case 0x1D:
                return EFFECT_VOLUME;
            case 0x20:
                return EFFECT_UNKNOWN;
            case 0x21: // Extended XM Effects
                switch (effectOp >> 4) {
                    case 0x1:
                        return EFFECT_PITCH;
                    case 0x2:
                        return EFFECT_PITCH;
                    case 0x5:
                        return EFFECT_PANNING;
                    case 0x6:
                        return EFFECT_GLOBAL;
                    case 0x9: // Sound Control
                        switch (effectOp & 0x0F) {
                            case 0x0:
                                return EFFECT_PANNING;
                            case 0x1:
                                return EFFECT_PANNING;
                            case 0x8:
                                return EFFECT_PITCH;
                            case 0x9:
                                return EFFECT_PITCH;
                            case 0xA:
                                return EFFECT_PANNING;
                            case 0xB:
                                return EFFECT_PANNING;
                            case 0xC:
                                return EFFECT_GLOBAL;
                            case 0xD:
                                return EFFECT_GLOBAL;
                            case 0xE:
                                return EFFECT_NORMAL;
                            case 0xF:
                                return EFFECT_NORMAL;
                        }
                        break;
                    case 0xA:
                        return EFFECT_NORMAL;
                }
                break;
            case 0x22:
                return EFFECT_PANNING;
            case 0x23:
                return EFFECT_NORMAL;
            case 0x24:
                return EFFECT_NORMAL;
            case 0x26:
                return EFFECT_NORMAL;
        }
        return EFFECT_UNKNOWN;
    }

    /**
     * @return
     * @see de.quippy.javamod.multimedia.mod.loader.pattern.PatternElement#getVolumeColumEffectChar()
     */
    @Override
    public char getVolumeColumEffectChar() {
        return switch (volumeEffect) {
            case 0x01 -> 'v';
            case 0x02 -> 'd';
            case 0x03 -> 'c';
            case 0x04 -> 'b';
            case 0x05 -> 'a';
            case 0x06 -> 'u';
            case 0x07 -> 'h';
            case 0x08 -> 'p';
            case 0x09 -> 'l';
            case 0x0A -> 'r';
            case 0x0B -> 'g';
            default ->
//            case 0x0C:
//                return 'e';
//            case 0x0D:
//                return 'f';
                    '?';
        };
    }

    /**
     * @return
     * @see de.quippy.javamod.multimedia.mod.loader.pattern.PatternElement#getVolEffectName()
     */
    @Override
    public String getVolEffectName() {
        switch (volumeEffect) {
            case 0x00:
                return Helpers.EMPTY_STING;
            case 0x01:
                return "Set Volume";
            case 0x02:
                return "Volslide down";
            case 0x03:
                return "Volslide up";
            case 0x04:
                return "Fine Volslide Down";
            case 0x05:
                return "Fine Volslide Up";
            case 0x06:
                return "Set Vibrato Speed";
            case 0x07:
                if (volumeEffectOp != 0) return "Set Vibrato Depth";
                else return "Vibrato";
            case 0x08:
                return "Set Panning";
            case 0x09:
                return "Panning Slide Left";
            case 0x0A:
                return "Panning Slide Right";
            case 0x0B:
                return "Porta To Note";
//            case 0x0C:
//                return "Porta Down";
//            case 0x0D:
//                return "Porta Up";
        }
        //logger.log(Level.ERROR, "Unknown: " + ModConstants.getAsHex(assignedVolumeEffect, 2) + "/" + ModConstants.getAsHex(assignedVolumeEffectOp, 2));
        return Helpers.EMPTY_STING;
    }

    /**
     * @return
     * @see de.quippy.javamod.multimedia.mod.loader.pattern.PatternElement#getVolEffectCategory()
     */
    @Override
    public int getVolEffectCategory() {
        return switch (volumeEffect) {
            case 0x00 -> EFFECT_NORMAL;
            case 0x01 -> EFFECT_VOLUME;
            case 0x02 -> EFFECT_VOLUME;
            case 0x03 -> EFFECT_VOLUME;
            case 0x04 -> EFFECT_VOLUME;
            case 0x05 -> EFFECT_VOLUME;
            case 0x06 -> EFFECT_PITCH;
            case 0x07 -> EFFECT_PITCH;
            case 0x08 -> EFFECT_PANNING;
            case 0x09 -> EFFECT_PANNING;
            case 0x0A -> EFFECT_PANNING;
            case 0x0B -> EFFECT_PITCH;
            default ->
//            case 0x0C:
//                return EFFECT_UNKNOWN;
//            case 0x0D:
//                return EFFECT_UNKNOWN;
                    EFFECT_UNKNOWN;
        };
    }
}
