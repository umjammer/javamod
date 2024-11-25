/*
 * @(#) ScreamTrackerMixer.java
 *
 * Created on 07.05.2006 by Daniel Becker
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

package de.quippy.javamod.multimedia.mod.mixer;

import java.util.concurrent.atomic.AtomicInteger;

import de.quippy.javamod.multimedia.mod.ModConstants;
import de.quippy.javamod.multimedia.mod.loader.Module;
import de.quippy.javamod.multimedia.mod.loader.instrument.Envelope;
import de.quippy.javamod.multimedia.mod.loader.instrument.Instrument;
import de.quippy.javamod.multimedia.mod.loader.instrument.Sample;
import de.quippy.javamod.multimedia.mod.loader.pattern.PatternElement;
import de.quippy.javamod.multimedia.mod.midi.MidiMacros;


/**
 * This is the scream tracker mixing routine with all special mixing
 * on typical scream tracker events
 *
 * @author Daniel Becker
 * @since 07.05.2006
 */
public class ScreamTrackerMixer extends BasicModMixer {

    protected boolean isNotITCompatMode = false;
    protected boolean is_S3M_GUS = false;

    /**
     * Constructor for ScreamTrackerMixer
     *
     * @param mod
     * @param sampleRate
     * @param doISP
     */
    public ScreamTrackerMixer(Module mod, int sampleRate, int doISP, int doNoLoops, int maxNNAChannels) {
        super(mod, sampleRate, doISP, doNoLoops, maxNNAChannels);
        isNotITCompatMode = (mod.getSongFlags() & ModConstants.SONG_ITCOMPATMODE) == 0;
        is_S3M_GUS = (mod.getSongFlags() & ModConstants.SONG_S3M_GUS) != 0;
    }

    /**
     * Sets the borders for Portas
     *
     * @param aktMemo
     * @see de.quippy.javamod.multimedia.mod.mixer.BasicModMixer#setPeriodBorders(de.quippy.javamod.multimedia.mod.mixer.BasicModMixer.ChannelMemory)
     * @since 17.06.2010
     */
    @Override
    protected void setPeriodBorders(ChannelMemory aktMemo) {
        if ((mod.getSongFlags() & ModConstants.SONG_AMIGALIMITS) != 0) { // IT/S3M Amiga Limit flag
            aktMemo.portaStepUpEnd = 113 << ModConstants.PERIOD_SHIFT;
            aktMemo.portaStepDownEnd = 856 << ModConstants.PERIOD_SHIFT;
        } else if (isS3M) {
            aktMemo.portaStepUpEnd = 0x40 << (ModConstants.PERIOD_SHIFT - 2);
            aktMemo.portaStepDownEnd = 0x7FFF << (ModConstants.PERIOD_SHIFT - 2);
        } else {
            // For IT no limits... But no wrap around with 32Bit either :)
            aktMemo.portaStepUpEnd = 0;
            aktMemo.portaStepDownEnd = 0x00FF_FFFF; // Short.MAX_VALUE<<ModConstants.PERIOD_SHIFT <- this is signed 0x7ffff then
        }
    }

    /**
     * @param channel
     * @param aktMemo
     * @see de.quippy.javamod.multimedia.mod.mixer.BasicModMixer#initializeMixer(int, de.quippy.javamod.multimedia.mod.mixer.BasicModMixer.ChannelMemory)
     */
    @Override
    protected void initializeMixer(int channel, ChannelMemory aktMemo) {
        aktMemo.muteWasITforced = aktMemo.muted = ((mod.getPanningValue(channel) & ModConstants.CHANNEL_IS_MUTED) != 0);
        aktMemo.doSurround = (mod.getPanningValue(channel) == ModConstants.CHANNEL_IS_SURROUND);
        setPeriodBorders(aktMemo);
    }

    /**
     * @param aktMemo
     * @param period
     * @return
     * @see de.quippy.javamod.multimedia.mod.mixer.BasicModMixer#getFineTunePeriod(de.quippy.javamod.multimedia.mod.mixer.BasicModMixer.ChannelMemory, int)
     */
    @Override
    protected int getFineTunePeriod(ChannelMemory aktMemo, int period) {
        int noteIndex = period - 1; // Period is only a note index now. No period - easier lookup
        switch (frequencyTableType) {
            case ModConstants.STM_S3M_TABLE:
            case ModConstants.IT_AMIGA_TABLE:
                int s3mNote = ModConstants.FreqS3MTable[noteIndex % 12];
                int s3mOctave = noteIndex / 12;
                // If I do not do it like this, it is too precise - and limits do not work
                return (int) ((long) ModConstants.BASEFREQUENCY * ((long) s3mNote << 5) / ((long) aktMemo.currentFinetuneFrequency << s3mOctave)) << (ModConstants.PERIOD_SHIFT - 2);

            case ModConstants.IT_LINEAR_TABLE:
                return (ModConstants.FreqS3MTable[noteIndex % 12] << 7) >> (noteIndex / 12);

            default:
                return super.getFineTunePeriod(aktMemo, period);
        }
    }

    /**
     * @param aktMemo
     * @param newPeriod
     * @see de.quippy.javamod.multimedia.mod.mixer.BasicModMixer#setNewPlayerTuningFor(de.quippy.javamod.multimedia.mod.mixer.BasicModMixer.ChannelMemory, int)
     */
    @Override
    protected void setNewPlayerTuningFor(ChannelMemory aktMemo, int newPeriod) {
        aktMemo.currentNotePeriodSet = newPeriod;

        if (newPeriod <= 0) {
            aktMemo.currentTuning = 0;
            return;
        }

        switch (frequencyTableType) {
            case ModConstants.IT_LINEAR_TABLE:
                long itTuning = (((((long) ModConstants.BASEPERIOD) << ModConstants.PERIOD_SHIFT) * (long) aktMemo.currentFinetuneFrequency) << ModConstants.SHIFT) / (long) sampleRate;
                aktMemo.currentTuning = (int) (itTuning / (long) newPeriod);
                return;
            case ModConstants.STM_S3M_TABLE:
            case ModConstants.IT_AMIGA_TABLE:
                if (isS3M) {
                    if (newPeriod > aktMemo.portaStepDownEnd) {
                        aktMemo.currentTuning = globalTuning / aktMemo.portaStepDownEnd;
                        if (!is_S3M_GUS)
                            aktMemo.currentNotePeriod = aktMemo.currentNotePeriodSet = aktMemo.portaStepDownEnd;
                    } else if (newPeriod <= 0)
                        aktMemo.currentTuning = 0;
                    else
                        aktMemo.currentTuning = globalTuning / ((newPeriod < aktMemo.portaStepUpEnd) ? aktMemo.portaStepUpEnd : newPeriod);
                } else
                    aktMemo.currentTuning = globalTuning / ((newPeriod > aktMemo.portaStepDownEnd) ? aktMemo.portaStepDownEnd : (newPeriod < aktMemo.portaStepUpEnd) ? aktMemo.portaStepUpEnd : newPeriod);
                return;
            default:
                super.setNewPlayerTuningFor(aktMemo, newPeriod);
        }
    }

    @Override
    protected int calculateExtendedValue(ChannelMemory aktMemo, AtomicInteger extendedRowsUsed) {
        if (extendedRowsUsed != null) extendedRowsUsed.set(0);
        int val = aktMemo.assignedEffectParam;
        if (!isIT) return val;

        int row = currentRow;
        int lookAheadRows = 4;
        switch (aktMemo.assignedEffect) {
            case 0x0F:    // sample offset
                // 24 bit command
                lookAheadRows = 2;
                break;
            case 0x14:    // Tempo
            case 0x02:    // Pattern position jump
            case 0x03:    // Pattern Break
                // 16 bit command
                lookAheadRows = 1;
                break;
            default:
                return val;
        }

        int rowsLeft = currentPattern.getRowCount() - currentRow - 1;
        if (lookAheadRows > rowsLeft) lookAheadRows = rowsLeft;
        int rowsUsed = 0;
        while (lookAheadRows > 0) {
            row++;
            lookAheadRows--;
            PatternElement patternElement = currentPattern.getPatternRow(row).getPatternElement(aktMemo.channelNumber);
            if (patternElement.getEffect() != 0x1B) break;
            rowsUsed++;
            val = (val << 8) | (patternElement.getEffectOp() & 0xff);
        }
        if (extendedRowsUsed != null) extendedRowsUsed.set(rowsUsed);
        return val;
    }

    /**
     * perform the duplicate note checks, if any are defined
     *
     * @param aktMemo
     * @since 08.07.2020
     */
    protected void doDNA(ChannelMemory aktMemo) {
        Instrument instr = aktMemo.assignedInstrument;
        if (instr == null) return;

        // we can save the time, if no duplicate action is set
        if (instr.duplicateNoteCheck == ModConstants.DCT_NONE) return;

        int channelNumber = aktMemo.channelNumber;
        for (int c = channelNumber; c < maxChannels; c++) {
            // Only apply to background channels, or the same pattern channel
            if (c != channelNumber && c < mod.getNChannels())
                continue;

            ChannelMemory currentNNAChannel = channelMemory[c];
            if (!isChannelActive(currentNNAChannel)) continue;

            if (currentNNAChannel.channelNumber == channelNumber) {
                boolean applyDNA = false;
                // Check the Check
                switch (instr.duplicateNoteCheck) {
                    case ModConstants.DCT_NONE:
                        // this was checked earlier - but to be complete here...
                        break;
                    case ModConstants.DCT_NOTE:
                        int note = aktMemo.assignedNoteIndex;
                        // ** With other players, the noteindex of instrument mapping
                        // ** might count!! Would be this:
                        //final int note = inst.getNoteIndex(aktMemo.assignedNoteIndex-1)+1;
                        // *********
                        if (note > 0 &&
                                note == currentNNAChannel.assignedNoteIndex &&
                                instr == currentNNAChannel.assignedInstrument)
                            applyDNA = true;
                        break;
                    case ModConstants.DCT_SAMPLE:
                        Sample sample = aktMemo.currentSample;
                        if (sample != null &&
                                sample == currentNNAChannel.currentSample && // this compares only pointer. Should work, as samples exist only once!
                                instr == currentNNAChannel.assignedInstrument) // IT: also same instrument
                            applyDNA = true;
                        break;
                    case ModConstants.DCT_INSTRUMENT:
                        if (instr == currentNNAChannel.assignedInstrument)
                            applyDNA = true;
                        break;
                    case ModConstants.DCT_PLUGIN:
                        // TODO: Unsupported
                        break;
                }

                if (applyDNA) {
                    // We have a match!
                    switch (instr.duplicateNoteAction) {
                        case ModConstants.DNA_CUT:    // CUT: note volume to zero
                            doNoteCut(currentNNAChannel);
                            break;
                        case ModConstants.DNA_FADE:        // fade: fade out with fixed values
                            initNoteFade(currentNNAChannel);
                            break;
                        case ModConstants.DNA_OFF:        // OFF: fade out with instrument fade out value
                            doKeyOff(currentNNAChannel);
                            break;
                    }
                }
            }
        }
    }

    /**
     * @param aktMemo
     * @param NNA
     * @since 11.06.2020
     */
    protected void doNNA(ChannelMemory aktMemo, int NNA) {
        switch (NNA) {
            case ModConstants.NNA_CONTINUE:    // continue: let the music play
                break;
            case ModConstants.NNA_CUT:        // CUT: note volume to zero
                doNoteCut(aktMemo);
                break;
            case ModConstants.NNA_FADE:        // fade: fade out with fixed values
                initNoteFade(aktMemo);
                break;
            case ModConstants.NNA_OFF:        // OFF: fade out with instrument fade out value
                doKeyOff(aktMemo);
                break;
        }
    }

    /**
     * @param aktMemo
     * @param NNA
     * @since 11.06.2020
     */
    protected void doNNANew(ChannelMemory aktMemo, int NNA) {
        ChannelMemory newChannel = null;
        int lowVol = ModConstants.MAXCHANNELVOLUME;
        int envPos = 0;
        // Pick a Channel with lowest volume or silence
        for (int c = mod.getNChannels(); c < maxChannels; c++) {
            ChannelMemory memo = channelMemory[c];
            if (!isChannelActive(memo)) {
                newChannel = memo;
                break;
            }

            // to find the channel with the lowest volume,
            // add left and right target volumes but add the current
            // channelVolume so temporary silent channels are not killed
            // (left and right volume are shifted by 12+6 bit, so channel volume
            //  has space in the lower part)
            // additionally we also consider channels being far beyond their endpoint
            int currentVolume = (memo.actVolumeLeft + memo.actVolumeRight) | memo.channelVolume;
            if ((currentVolume < lowVol) || (currentVolume == lowVol && memo.volEnvTick > envPos)) {
                envPos = memo.volEnvTick;
                lowVol = currentVolume;
                newChannel = memo;
            }
        }

        if (newChannel != null) {
            newChannel.setUpFrom(aktMemo);
            doDNA(aktMemo);
            doNNA(newChannel, NNA);
            // stop the current channel - it is copied
            aktMemo.instrumentFinished = true;
        }
    }

    /**
     * @param aktMemo
     * @param NNA
     * @since 11.06.2020
     */
    protected void doNNAforAllof(ChannelMemory aktMemo, int NNA) {
        int channelNumber = aktMemo.channelNumber;
        for (int c = mod.getNChannels(); c < maxChannels; c++) {
            ChannelMemory currentNNAChannel = channelMemory[c];
            if (!isChannelActive(currentNNAChannel)) continue;
            if (currentNNAChannel.channelNumber == channelNumber)
                doNNA(currentNNAChannel, NNA);
        }
    }

    /**
     * @param aktMemo
     * @since 29.03.2010
     */
    protected void doNNAAutoInstrument(ChannelMemory aktMemo) {
        if (!isChannelActive(aktMemo) || aktMemo.muted || aktMemo.noteCut) return;

        Instrument currentInstrument = aktMemo.assignedInstrument;
        if (currentInstrument != null) {
            // NNA_CUT is default for instruments with no NNA
            // so do not copy this to a new channel for just finishing
            // it off then.
            if (currentInstrument.NNA != ModConstants.NNA_CUT) {
                int nna;
                if (aktMemo.tempNNAAction > -1) {
                    nna = aktMemo.tempNNAAction;
                    aktMemo.tempNNAAction = -1;
                } else
                    nna = currentInstrument.NNA;

                doNNANew(aktMemo, nna);
            }
        }
    }

    /**
     * @param aktMemo
     * @since 10.07.2024
     */
    protected void doNoteCut(ChannelMemory aktMemo) {
        aktMemo.noteCut = true;
        //currentVolume = 0;
        // Schism sets tuning=0 and deletes the last period
        setNewPlayerTuningFor(aktMemo, aktMemo.currentNotePeriod = 0);
        // that would be our way:
        //aktMemo.instrumentFinished = true;
        aktMemo.doFastVolRamp = true;
    }

    /**
     * @param aktMemo
     * @since 26.07.2024
     */
    protected void doKeyOff(ChannelMemory aktMemo) {
        aktMemo.keyOff = true;
    }

    /**
     * Check if effect is a fine slide - then only on first tick!
     *
     * @param slideValue
     * @return
     * @since 04.04.2020
     */
    protected static boolean isFineSlide(int slideValue) {
        return ((slideValue >> 4) == 0xF && (slideValue & 0xF) != 0x0) ||
                ((slideValue >> 4) != 0x0 && (slideValue & 0xF) == 0xF);
    }

    /**
     * To not over and over again implement the same algorithm, this method
     * will return a -value or a value. Just add (or substract) it
     *
     * @param effectOp
     * @return
     * @since 22.12.2023
     */
    protected int getFineSlideValue(int effectOp) {
        int x = (effectOp >> 4) & 0x0F;
        int y = effectOp & 0x0F;

        if (isSTM) { // No fine slide with STMs, lower nibble has precedence
            if (y != 0) return -y;
            return x;
        }

        // 0xff can be fine slide up 15 or down 15. Per convention it is
        // fine up 15, so we test fine up first.
        if (y == 0xF) { // Fine Slide Up or normal slide down 15 (0x0F)
            if (x != 0)
                return x;
            else
                return -15;
        } else if (x == 0xF) { // Fine Slide down or normal slide up
            if (y != 0)
                return -y;
            else
                return 15;
        } else if (y != 0) {
            if (!isIT || x == 0) return -y;
        } else if (x != 0) {
            if (!isIT || y == 0) return x;
        }
        // Having OP with x and y set (like 15 or 84) is not supported with IT and does nothing
        return 0;
    }

    /**
     * Is only called for ImpulseTracker now. Fast- and Pro-Tracker are handled
     * completely differently
     *
     * @param aktMemo
     * @since 14.06.2020
     */
    protected void resetForNewSample(ChannelMemory aktMemo) {
        resetInstrumentPointers(aktMemo, true);
        resetFineTune(aktMemo, aktMemo.currentSample);
        resetEnvelopes(aktMemo);
        resetAutoVibrato(aktMemo, aktMemo.currentSample);
        aktMemo.doFastVolRamp = true;
    }

    /**
     * TODO: Clean up this mess - it is now only for STM, S3M, IT and MPTM
     *
     * @param aktMemo
     * @see de.quippy.javamod.multimedia.mod.mixer.BasicModMixer#setNewInstrumentAndPeriod(de.quippy.javamod.multimedia.mod.mixer.BasicModMixer.ChannelMemory)
     * @since 14.07.2024
     */
    @Override
    protected void setNewInstrumentAndPeriod(ChannelMemory aktMemo) {
        PatternElement element = aktMemo.currentElement;
//		final boolean isNoteDelay = isNoteDelayEffect(aktMemo.currentAssignedEffect, aktMemo.currentAssignedEffectParam);
        boolean isKeyOff = element.getPeriod() == ModConstants.KEY_OFF || element.getNoteIndex() == ModConstants.KEY_OFF;
        boolean isNewNote = hasNewNote(element);
        boolean isPortaToNoteEffect = isPortaToNoteEffect(aktMemo.currentAssignedEffect, aktMemo.currentAssignedEffectParam, aktMemo.currentAssignedVolumeEffect, aktMemo.currentAssignedVolumeEffectOp, aktMemo.currentAssignedNotePeriod);

        // Do Instrument default NNA
        if (isIT && isNewNote &&
                !isPortaToNoteEffect &&
                !isNNAEffect(aktMemo.currentAssignedEffect, aktMemo.currentAssignedEffectParam)) { // New Note Action
            doNNAAutoInstrument(aktMemo);
        }

        // copy last seen values from pattern - only effect values first
        aktMemo.assignedEffect = aktMemo.currentAssignedEffect;
        aktMemo.assignedEffectParam = aktMemo.currentAssignedEffectParam;
        aktMemo.assignedVolumeEffect = aktMemo.currentAssignedVolumeEffect;
        aktMemo.assignedVolumeEffectOp = aktMemo.currentAssignedVolumeEffectOp;
        aktMemo.assignedNotePeriod = aktMemo.currentAssignedNotePeriod;
        aktMemo.assignedNoteIndex = aktMemo.currentAssignedNoteIndex;
        aktMemo.assignedSample = (aktMemo.currentAssignedInstrument != null) ?
                ((aktMemo.assignedNoteIndex > 0) ? // but only if we also have a note index, if not, ignore it!
                        mod.getInstrumentContainer().getSample(aktMemo.currentAssignedInstrument.getSampleIndex(aktMemo.assignedNoteIndex - 1))
                        : null) // Instrument set without a note - so no mapping to sample possible!
                : mod.getInstrumentContainer().getSample(aktMemo.currentAssignedInstrumentIndex - 1);

        if (aktMemo.assignedEffect != 0x11)
            aktMemo.retrigCount = -1; // Effect Retrigger Note: indicating, that a retrigger is not continuing

        boolean hasInstrument = element.getInstrument() > 0 && aktMemo.assignedSample != null;
        if (hasInstrument) { // At this point we reset volume and panning for IT, STM, S3M
            if (isPortaToNoteEffect) { // Sample/Instrument change at Porta2Note needs special handling
                if (isS3M) { // set new sample volume, if sample is different, not null (already checked) and has samples
                    if (aktMemo.assignedSample.length > 0)
                        resetVolumeAndPanning(aktMemo, aktMemo.currentAssignedInstrument, aktMemo.assignedSample);
                    else {
                        aktMemo.currentAssignedInstrumentIndex = aktMemo.assignedInstrumentIndex;
                        aktMemo.currentAssignedInstrument = aktMemo.assignedInstrument;
                        if (aktMemo.currentSample != null) aktMemo.assignedSample = aktMemo.currentSample;
                        hasInstrument = false;
                    }
                } else if (isSTM) { // Ignore sample change
                    aktMemo.currentAssignedInstrumentIndex = aktMemo.assignedInstrumentIndex;
                    aktMemo.currentAssignedInstrument = aktMemo.assignedInstrument;
                    if (aktMemo.currentSample != null) aktMemo.assignedSample = aktMemo.currentSample;
                    hasInstrument = false;
                } else if (isIT) {
                    if (aktMemo.currentSample != aktMemo.assignedSample) { // set sample - but also perform porta2note
                        aktMemo.currentSample = aktMemo.assignedSample;
                        resetForNewSample(aktMemo);
                    } else {
                        // Old Instrument but new Sample (what a swap)
                        resetVolumeAndPanning(aktMemo, aktMemo.assignedInstrument, aktMemo.assignedSample);
                        resetEnvelopes(aktMemo, aktMemo.assignedInstrument);
                    }
                }
            } else { // only new Instrument, no Porta2Note: reset only volume and panning for now
                resetVolumeAndPanning(aktMemo, aktMemo.currentAssignedInstrument, aktMemo.assignedSample);
            }
        }

        // Now safe those instruments for later re-use
        aktMemo.assignedInstrumentIndex = aktMemo.currentAssignedInstrumentIndex;
        aktMemo.assignedInstrument = aktMemo.currentAssignedInstrument;

        // Key Off, Note Cut, Note Fade or Period / noteIndex to set?
        if (isKeyOff) {
            doKeyOff(aktMemo);
        } else if (element.getPeriod() == ModConstants.NOTE_CUT || element.getNoteIndex() == ModConstants.NOTE_CUT) {
            doNoteCut(aktMemo);
        } else if (element.getPeriod() == ModConstants.NOTE_FADE || element.getNoteIndex() == ModConstants.NOTE_FADE) {
            initNoteFade(aktMemo);
        } else if ((isNewNote ||                                            // if there is a note, we need to calc the new tuning and activate a previous set instrument
                hasInstrument) &&                                        // but with Scream Tracker like mods, the old note value is used, if an instrument is set
                (!isPortaToNoteEffect || aktMemo.instrumentFinished)    // but ignore this if porta to note, except when the instrument finished
        ) {
            int savedNoteIndex = aktMemo.assignedNoteIndex; // save the noteIndex - if it is changed by an instrument, we use that one to generate the period, but set it back then
            boolean useFilter = !globalFilterMode;
            boolean newInstrumentWasSet = false;

            // because of sample offset (S3M recall old offset), reset to zero, if sample is set.
            if (isS3M && hasInstrument) aktMemo.prevSampleOffset = 0;

            // We have an instrument/sample assigned, so there was (once) an instrument set!
            if (aktMemo.assignedInstrument != null || aktMemo.assignedInstrumentIndex > 0) {
                // now the correct note index from the mapping table, if we have an instrument and a valid note index
                // the sample was already read before
                if (aktMemo.assignedInstrument != null && aktMemo.assignedNoteIndex > 0) {
                    aktMemo.assignedNoteIndex = aktMemo.assignedInstrument.getNoteIndex(aktMemo.assignedNoteIndex - 1) + 1;
                    // Now set filters from instrument for IT
                    if (isIT) useFilter = setFilterAndRandomVariations(aktMemo, aktMemo.assignedInstrument, useFilter);
                }

                if (aktMemo.assignedSample != null) {
                    // Reset all pointers, if it's a new one...
                    // or with IT: play sample even without note - but not only if it is
                    // a new one but the same, and it's finished / silent...
                    if (aktMemo.currentSample != aktMemo.assignedSample) {
                        // Now activate new Instrument...
                        aktMemo.currentSample = aktMemo.assignedSample;
                        //aktMemo.assignedSample = null;
                        resetForNewSample(aktMemo);
                        newInstrumentWasSet = true;
                    }
                    // With Scream Tracker this has to be checked! Always!
                    // IT-MODS (and derivates) reset here, because a sample set is relevant (see below)
                    if (aktMemo.instrumentFinished || isNewNote) {
                        aktMemo.noteCut = aktMemo.keyOff = aktMemo.noteFade = false;
                        aktMemo.tempVolEnv = aktMemo.tempPanEnv = aktMemo.tempPitchEnv = -1;
                        resetInstrumentPointers(aktMemo, false);
                        resetEnvelopes(aktMemo);
                        resetAutoVibrato(aktMemo, aktMemo.currentSample);
                    }
                }
            }

            if (!isPortaToNoteEffect ||
                    (isPortaToNoteEffect && newInstrumentWasSet)) { // With IT, if a new sample is set, ignore porta to note-->set it
                // Now set the player Tuning and reset some things in advance.
                // normally we are here, because a note was set in the pattern.
                // Except for IT-MODs - then we are here, because either note or
                // instrument were set. If no note value was set, the old
                // note value is to be used.
                // However, we do not reset the instrument here - the reset was
                // already done above - so this is here for all sane players :)
                if (isNewNote || newInstrumentWasSet) {
                    setNewPlayerTuningFor(aktMemo, aktMemo.currentNotePeriod = getFineTunePeriod(aktMemo));
                    // With S3Ms (STMs?) the port2target is the last seen note value
                    if (isS3M || isSTM) aktMemo.portaTargetNotePeriod = aktMemo.currentNotePeriod;
                }
                // and set the resonance, settings were stored above in instr. value copy
                if ((/*aktMemo.resonance>0 || */aktMemo.cutOff < 0x7F) && useFilter)
                    setupChannelFilter(aktMemo, true, 256);
                if (isNewNote && !isPortaToNoteEffect)
                    reset_VibTremPan_TablePositions(aktMemo); // IT resets vibrato table position with a new note (and only that position)
            }
            // write back, if noteIndex was changed by instrument note mapping
            aktMemo.assignedNoteIndex = savedNoteIndex;
        }
    }

    /**
     * Set the effect memory of non parameter effect at S3M
     *
     * @param aktMemo
     * @param param
     * @since 02.08.2024
     */
    private void setS3MParameterMemory(ChannelMemory aktMemo, int param) {
        aktMemo.volumSlideValue = param;                        // Dxy / Kxy / Lxy
        aktMemo.portaStepUp = param;                            // Exx / Fxx
        aktMemo.portaStepDown = param;                            // Exx / Fxx
        aktMemo.tremorOntimeSet = (param >> 4) & 0xF;                // Ixy
        aktMemo.tremorOfftimeSet = (param & 0xF);
        if ((mod.getSongFlags() & ModConstants.SONG_ITOLDEFFECTS) != 0) {
            aktMemo.tremorOntimeSet++;
            aktMemo.tremorOfftimeSet++;
        }
        aktMemo.arpeggioParam = param;                            // Jxy
        aktMemo.retrigMemo = param & 0xF;                            // Qxy
        aktMemo.retrigVolSlide = (param >> 4) & 0xF;
        if ((param >> 4) != 0) aktMemo.tremoloStep = param >> 4;        // Rxy
        if ((param & 0xF) != 0) aktMemo.tremoloAmplitude = param & 0xF;
        aktMemo.S_Effect_Memory = param;                        // Sxy
        if (isNotITCompatMode) aktMemo.IT_EFG = param;            // when not IT Compat Mode!
    }

    /**
     * @param aktMemo
     * @see de.quippy.javamod.multimedia.mod.mixer.BasicModMixer#doRowEffects(de.quippy.javamod.multimedia.mod.mixer.BasicModMixer.ChannelMemory)
     */
    @Override
    protected void doRowEffects(ChannelMemory aktMemo) {
        if (aktMemo.tremorWasActive) {
            aktMemo.currentVolume = aktMemo.currentInstrumentVolume;
            aktMemo.tremorWasActive = false;
            aktMemo.doFastVolRamp = true;
        }

        if (aktMemo.assignedEffect == 0) return;

        Instrument ins = aktMemo.assignedInstrument;
        PatternElement element = aktMemo.currentElement;

        if (isS3M && aktMemo.assignedEffectParam != 0)
            setS3MParameterMemory(aktMemo, aktMemo.assignedEffectParam);

        switch (aktMemo.assignedEffect) {
            case 0x00:            // no effect, only effect OP is set
                break;
            case 0x01:            // SET SPEED
                if ((mod.getSongFlags() & ModConstants.SONG_ST2TEMPO) != 0) {
                    int newTempo = aktMemo.assignedEffectParam;
                    if (isSTM) {
                        if (newTempo == 0) break;
                        if ((mod.getVersion() & 0x0F) < 21) // set Tempo needs correction, depending on stm version.
                            newTempo = ((newTempo / 10) << 4) + (newTempo % 10);
                    }
                    currentTick = currentTempo = ((newTempo >> 4) != 0 ? newTempo >> 4 : 1);
                    currentBPM = ModConstants.convertST2tempo(newTempo);
                } else {
                    currentTick = currentTempo = aktMemo.assignedEffectParam;
                }
                break;
            case 0x02:            // Pattern position jump
                patternBreakPatternIndex = calculateExtendedValue(aktMemo, null);
                patternBreakRowIndex = 0;
                patternBreakSet = true;
                break;
            case 0x03:            // Pattern break
                if (!(isS3M && aktMemo.assignedEffectParam > 64)) { // ST3 ignores illegal pattern breaks
                    patternBreakRowIndex = calculateExtendedValue(aktMemo, null);
                    patternBreakSet = true;
                }
                break;
            case 0x04:            // Volume Slide
                if (aktMemo.assignedEffectParam != 0) aktMemo.volumSlideValue = aktMemo.assignedEffectParam;
                // Fine Volume Up/Down and FastSlides
                if (isFineSlide(aktMemo.volumSlideValue) || (mod.getSongFlags() & ModConstants.SONG_FASTVOLSLIDES) != 0)
                    doVolumeSlideEffect(aktMemo);
                break;
            case 0x05:            // Porta Down
                if (aktMemo.assignedEffectParam != 0) {
                    aktMemo.portaStepDown = aktMemo.assignedEffectParam;
                    if (isNotITCompatMode) aktMemo.IT_EFG = aktMemo.portaStepDown;
                }
                doPortaDown(aktMemo, true, false);
                break;
            case 0x06:            // Porta Up
                if (aktMemo.assignedEffectParam != 0) {
                    aktMemo.portaStepUp = aktMemo.assignedEffectParam;
                    if (isNotITCompatMode) aktMemo.IT_EFG = aktMemo.portaStepUp;
                }
                doPortaUp(aktMemo, true, false);
                break;
            case 0x07:            // Porta To Note
                if (hasNewNote(element)) aktMemo.portaTargetNotePeriod = getFineTunePeriod(aktMemo);
                if (aktMemo.assignedEffectParam != 0) {
                    aktMemo.portaNoteStep = aktMemo.assignedEffectParam;
                    if (isNotITCompatMode) aktMemo.IT_EFG = aktMemo.portaNoteStep;
                }
                break;
            case 0x08:            // Vibrato
                if (isSTM && aktMemo.assignedEffectParam == 0) break; // Tick Zero effect
                if ((aktMemo.assignedEffectParam >> 4) != 0) aktMemo.vibratoStep = aktMemo.assignedEffectParam >> 4;
                if ((aktMemo.assignedEffectParam & 0xF) != 0)
                    aktMemo.vibratoAmplitude = (aktMemo.assignedEffectParam & 0xF) << 2;
                aktMemo.vibratoOn = true;
                doVibratoEffect(aktMemo, false);
                break;
            case 0x09:            // Tremor
                if (aktMemo.assignedEffectParam != 0) {
                    aktMemo.currentInstrumentVolume = aktMemo.currentVolume;
                    aktMemo.tremorOntimeSet = (aktMemo.assignedEffectParam >> 4);
                    aktMemo.tremorOfftimeSet = (aktMemo.assignedEffectParam & 0xF);
                    if ((mod.getSongFlags() & ModConstants.SONG_ITOLDEFFECTS) != 0) {
                        aktMemo.tremorOntimeSet++;
                        aktMemo.tremorOfftimeSet++;
                    }
                }
                if (aktMemo.tremorOntimeSet == 0) aktMemo.tremorOntimeSet = 1;
                if (aktMemo.tremorOfftimeSet == 0) aktMemo.tremorOfftimeSet = 1;
                doTremorEffect(aktMemo);
                break;
            case 0x0A:            // Arpeggio
                if (aktMemo.assignedEffectParam != 0) aktMemo.arpeggioParam = aktMemo.assignedEffectParam;
                if (aktMemo.assignedNoteIndex != 0) {
                    if (isSTM || isS3M) {
                        aktMemo.arpeggioNote[0] = getFineTunePeriod(aktMemo);
                        aktMemo.arpeggioNote[1] = getFineTunePeriod(aktMemo, aktMemo.assignedNoteIndex + (aktMemo.arpeggioParam >> 4));
                        aktMemo.arpeggioNote[2] = getFineTunePeriod(aktMemo, aktMemo.assignedNoteIndex + (aktMemo.arpeggioParam & 0xF));
                    } else
                        aktMemo.arpeggioNote[0] = aktMemo.currentNotePeriod;

                    aktMemo.arpeggioIndex = 0;
                }
                break;
            case 0x0B:            // Vibrato + Volume Slide
                aktMemo.vibratoOn = true;
                doVibratoEffect(aktMemo, false);
                // Fine Volume Up/Down and FastSlides
                if (aktMemo.assignedEffectParam != 0) aktMemo.volumSlideValue = aktMemo.assignedEffectParam;
                if (isFineSlide(aktMemo.volumSlideValue) || (mod.getSongFlags() & ModConstants.SONG_FASTVOLSLIDES) != 0)
                    doVolumeSlideEffect(aktMemo);
                break;
            case 0x0C:            // Porta To Note + VolumeSlide
                if (hasNewNote(element)) aktMemo.portaTargetNotePeriod = getFineTunePeriod(aktMemo);
                // Fine Volume Up/Down and FastSlides
                if (aktMemo.assignedEffectParam != 0) aktMemo.volumSlideValue = aktMemo.assignedEffectParam;
                if (isFineSlide(aktMemo.volumSlideValue) || (mod.getSongFlags() & ModConstants.SONG_FASTVOLSLIDES) != 0)
                    doVolumeSlideEffect(aktMemo);
                break;
            case 0x0D:            // Set Channel Volume
                aktMemo.channelVolume = aktMemo.assignedEffectParam;
                if (aktMemo.channelVolume > ModConstants.MAXSAMPLEVOLUME)
                    aktMemo.channelVolume = ModConstants.MAXSAMPLEVOLUME;
                break;
            case 0x0E:            // Channel Volume Slide
                if (aktMemo.assignedEffectParam != 0) aktMemo.channelVolumeSlideValue = aktMemo.assignedEffectParam;
                // Fine Volume Up/Down and FastSlides
                if (isFineSlide(aktMemo.channelVolumeSlideValue) || (mod.getSongFlags() & ModConstants.SONG_FASTVOLSLIDES) != 0)
                    doChannelVolumeSlideEffect(aktMemo);
                break;
            case 0x0F:            // Sample Offset
                AtomicInteger rowsUsed = new AtomicInteger(0);
                int newSampleOffset = calculateExtendedValue(aktMemo, rowsUsed);
                if (newSampleOffset != 0) {
                    if (rowsUsed.get() == 0) { // old behavior
                        aktMemo.sampleOffset = aktMemo.highSampleOffset << 16 | newSampleOffset << 8;
//						aktMemo.highSampleOffset = 0; // TODO: set zero after usage?!
                    } else
                        aktMemo.sampleOffset = newSampleOffset;
                }
                aktMemo.prevSampleOffset = aktMemo.sampleOffset;
                doSampleOffsetEffect(aktMemo, element);
                break;
            case 0x10:            // Panning Slide
                if (aktMemo.assignedEffectParam != 0) aktMemo.panningSlideValue = aktMemo.assignedEffectParam;
                if (isFineSlide(aktMemo.panningSlideValue))
                    doPanningSlideEffect(aktMemo);
                break;
            case 0x11:            // Retrig Note
                if ((aktMemo.assignedEffectParam & 0xF) != 0) {
                    aktMemo.retrigMemo = aktMemo.assignedEffectParam & 0xF;
                    aktMemo.retrigVolSlide = aktMemo.assignedEffectParam >> 4;
                }
                doRetrigNote(aktMemo, aktMemo.retrigCount != -1); // with retrigCount we indicate a continues retrigger. If that is the case, retrigger also on Tick Zero. It is reset in setNewInstrumentAndPeriod
                aktMemo.retrigCount = 0; // != -1
                break;
            case 0x12:            // Tremolo
                if ((aktMemo.assignedEffectParam >> 4) != 0) aktMemo.tremoloStep = aktMemo.assignedEffectParam >> 4;
                if ((aktMemo.assignedEffectParam & 0xF) != 0)
                    aktMemo.tremoloAmplitude = aktMemo.assignedEffectParam & 0xF;
                aktMemo.tremoloOn = true;
                doTremoloEffect(aktMemo);
                break;
            case 0x13:            // Extended
                int effectParam = (aktMemo.assignedEffectParam == 0) ? aktMemo.S_Effect_Memory : (aktMemo.S_Effect_Memory = aktMemo.assignedEffectParam);
                int effectOpEx = effectParam & 0x0F;
                switch (effectParam >> 4) {
                    case 0x1:    // Glissando
                        aktMemo.glissando = effectOpEx != 0;
                        break;
                    case 0x2:    // Set FineTune
                        aktMemo.currentFineTune = ModConstants.IT_fineTuneTable[effectOpEx];
                        aktMemo.currentFinetuneFrequency = ModConstants.IT_fineTuneTable[effectOpEx];
                        setNewPlayerTuningFor(aktMemo, getFineTunePeriod(aktMemo));
                        break;
                    case 0x3:    // Set Vibrato Type
                        aktMemo.vibratoType = effectOpEx & 0x3;
                        aktMemo.vibratoNoRetrig = ((effectOpEx & 0x04) != 0);
                        break;
                    case 0x4:    // Set Tremolo Type
                        aktMemo.tremoloType = effectOpEx & 0x3;
                        aktMemo.tremoloNoRetrig = ((effectOpEx & 0x04) != 0);
                        break;
                    case 0x5:    // Set Panbrello Type
                        aktMemo.panbrelloType = effectOpEx & 0x3;
                        aktMemo.panbrelloNoRetrig = ((effectOpEx & 0x04) != 0);
                        break;
                    case 0x6:    // Pattern Delay Frame
                        if (!isIT && !isModPlug) break; // only IT or ModPlug Mods
                        patternTicksDelayCount += effectOpEx; // those add up
                        break;
                    case 0x7:    // set NNA and others
                        if (!isIT && !isModPlug) break; // only IT or ModPlug Mods
                        switch (effectOpEx) {
                            case 0x0: // Note Cut all NNAs of this channel
                                doNNAforAllof(aktMemo, ModConstants.NNA_CUT);
                                break;
                            case 0x1: // Note Off all NNAs of this channel
                                doNNAforAllof(aktMemo, ModConstants.NNA_OFF);
                                break;
                            case 0x2: // Note Fade all NNAs of this channel
                                doNNAforAllof(aktMemo, ModConstants.NNA_FADE);
                                break;
                            case 0x3: // NNA Cut
                                aktMemo.tempNNAAction = ModConstants.NNA_CUT;
                                break;
                            case 0x4: // NNA Continue
                                aktMemo.tempNNAAction = ModConstants.NNA_CONTINUE;
                                break;
                            case 0x5: // NNA Off
                                aktMemo.tempNNAAction = ModConstants.NNA_OFF;
                                break;
                            case 0x6: // NNA Fade
                                aktMemo.tempNNAAction = ModConstants.NNA_FADE;
                                break;
                            case 0x7: // Volume Envelope off
                                if (ins != null) {
                                    Envelope volEnv = ins.volumeEnvelope;
                                    if (volEnv != null) aktMemo.tempVolEnv = 0;
                                }
                                break;
                            case 0x8: // Volume Envelope On
                                if (ins != null) {
                                    Envelope volEnv = ins.volumeEnvelope;
                                    if (volEnv != null) aktMemo.tempVolEnv = 1;
                                }
                                break;
                            case 0x9: // Panning Envelope off
                                if (ins != null) {
                                    Envelope panEnv = ins.panningEnvelope;
                                    if (panEnv != null) aktMemo.tempPanEnv = 0;
                                }
                                break;
                            case 0xA: // Panning Envelope On
                                if (ins != null) {
                                    Envelope panEnv = ins.panningEnvelope;
                                    if (panEnv != null) aktMemo.tempPanEnv = 1;
                                }
                                break;
                            case 0xB: // Pitch Envelope off
                                if (ins != null) {
                                    Envelope pitEnv = ins.pitchEnvelope;
                                    if (pitEnv != null) aktMemo.tempPitchEnv = 0;
                                }
                                break;
                            case 0xC: // Pitch Envelope On
                                if (ins != null) {
                                    Envelope pitEnv = ins.pitchEnvelope;
                                    if (pitEnv != null) aktMemo.tempPitchEnv = 1;
                                }
                                break;
                        }
                        break;
                    case 0x8:    // Fine Panning
                        doPanning(aktMemo, effectOpEx, ModConstants.PanBits.Pan4Bit);
                        break;
                    case 0x9:    // Sound Control
                        if (!isIT && !isModPlug) break; // only IT or ModPlug Mods
                        switch (effectOpEx) {
                            case 0x0: // Disable surround for the current channel
                                aktMemo.doSurround = false;
                                break;
                            case 0x1: //  Enable surround for the current channel. Note that a panning effect will automatically detective the surround, unless the 4-way (Quad) surround mode has been activated with the S9B effect.
                                aktMemo.doSurround = true;
                                break;
                            // MPT Effects only
//                            case 0x8: // Disable reverb for this channel
//                                break;
//                            case 0x9: // Force reverb for this channel
//                                break;
//                            case 0xA: // Select mono surround mode (center channel). This is the default
//                                break;
//                            case 0xB: // Select quad surround mode: this allows you to pan in the rear channels, especially useful for 4-speakers playback. Note that S9A and S9B do not activate the surround for the current channel, it is a global setting that will affect the behavior of the surround for all channels. You can enable or disable the surround for individual channels by using the S90 and S91 effects. In quad surround mode, the channel surround will stay active until explicitly disabled by an S90 effect
//                                break;
                            case 0xC: // Select global filter mode (IT compatibility). This is the default, when resonant filters are enabled with a Zxx effect, they will stay active until explicitly disabled by setting the cutoff frequency to the maximum (Z7F), and the resonance to the minimum (Z80).
                                globalFilterMode = false;
                                break;
                            case 0xD: // Select local filter mode (MPT beta compatibility): when this mode is selected, the resonant filter will only affect the current note. It will be deactivated when a new note is being played.
                                globalFilterMode = true;
                                break;
                            case 0xE: // Play forward. You may use this to temporarily force the direction of a bidirectional loop to go forward.
                                aktMemo.isForwardDirection = true;
                                break;
                            case 0xF: // Play backward. The current instrument will be played backwards, or it will temporarily set the direction of a loop to go backward.
                                if (aktMemo.currentSample != null && aktMemo.currentSamplePos == 0 && aktMemo.currentSample.length > 0 &&
                                        (hasNewNote(element) || (aktMemo.currentSample.loopType & ModConstants.LOOP_ON) != 0)) {
                                    aktMemo.currentSamplePos = aktMemo.currentSample.length - 1;
                                    aktMemo.currentTuningPos = 0;
                                }
                                aktMemo.isForwardDirection = false;
                                break;
                        }
                        break;
                    case 0xA:    // set High Offset / S3M: Stereo Control - whatever that was, not supported by Scream Tracker 3
                        if (!isIT && !isModPlug) break; // only IT or ModPlug Mods
                        aktMemo.highSampleOffset = effectOpEx;
                        break;
                    case 0xB:    // JumpLoop
                        // S3M: this is such an idiocracy way of doing it
                        // S3M has a central var for all of this - obviously
                        ChannelMemory aktMemoTmp = (isS3M) ? channelMemory[0] : aktMemo;
                        if (effectOpEx == 0) {
                            if (!isS3M || aktMemoTmp.jumpLoopPatternRow == -1) // ST3 does not overwrite a row set...
                                aktMemoTmp.jumpLoopPatternRow = currentRow;
                        } else {
                            if (isS3M && patternJumpSet)
                                break; // obviously other SBx events on the same row will not be executed, only the leftmost one

                            if (aktMemoTmp.jumpLoopRepeatCount == -1) {
                                aktMemoTmp.jumpLoopRepeatCount = effectOpEx;
                                if (aktMemoTmp.jumpLoopPatternRow == -1) // if not set, pattern start is default!
                                    aktMemoTmp.jumpLoopPatternRow = (aktMemoTmp.jumpLoopITLastRow == -1) ? mod.getSongRestart() : aktMemoTmp.jumpLoopITLastRow;
                            }

                            if (aktMemoTmp.jumpLoopRepeatCount > 0) {
                                aktMemoTmp.jumpLoopRepeatCount--;
                                patternJumpRowIndex = aktMemoTmp.jumpLoopPatternRow;
                                patternJumpSet = true;
                            } else {
                                aktMemoTmp.jumpLoopPatternRow =
                                        aktMemoTmp.jumpLoopRepeatCount = -1;
                                // remember last position behind SBx, for next SBx without target set
                                aktMemoTmp.jumpLoopITLastRow = currentRow + 1;
                                if (isS3M) {
                                    // plus for S3M prevent other SBx in the same row by forcing to next row.
                                    patternJumpRowIndex = aktMemoTmp.jumpLoopITLastRow;
                                    patternJumpSet = true;
                                }
                            }
                        }
                        break;
                    case 0xC:    // Note Cut
                        if (aktMemo.noteCutCount < 0) {
                            if (effectOpEx != 0) aktMemo.noteCutCount = effectOpEx;
                            if (aktMemo.noteCutCount == 0) {
                                if (isIT) aktMemo.noteCutCount = 1;
                                else if (isS3M) aktMemo.noteCutCount = -1;
                            }
                        }
                        break;
                    case 0xD:    // Note Delay
                        if (aktMemo.noteDelayCount < 0) { // is done in BasicModMixer::doRowEvents
                            if (isIT && effectOpEx == 0)
                                aktMemo.noteDelayCount = 1;
                            else if (isS3M && effectOpEx == 0)
                                aktMemo.noteDelayCount = -1;
                            else
                                aktMemo.noteDelayCount = effectOpEx;
                        }
                        // Note-Delays are handled centrally in "doRowAndTickEvents"
                        break;
                    case 0xE:    // Pattern Delay
                        if (patternDelayCount < 0 && effectOpEx != 0) patternDelayCount = effectOpEx;
                        break;
                    case 0xF:    // Set Active Macro (s3m: Funk Repeat, not implemented in Scream Tracker 3)
                        if (isIT) aktMemo.activeMidiMacro = aktMemo.assignedEffectParam & 0x7F;
                        break;
                    default:
                        //logger.log(Level.DEBUG, "Unknown Extended Effect: Effect:%02X Op:%02X in [Pattern:%03d: Row:%03d Channel:%03d]".formatted(Integer.valueOf(aktMemo.effect), Integer.valueOf(aktMemo.effectParam), Integer.valueOf(currentPatternIndex), Integer.valueOf(currentRow), Integer.valueOf(aktMemo.channelNumber+1)));
                        break;
                }
                break;
            case 0x14:            // set Tempo
                int newTempo = calculateExtendedValue(aktMemo, null);
                if (isIT || isModPlug) {
                    if (newTempo != 0)
                        aktMemo.oldTempoParameter = newTempo;
                    else
                        newTempo = aktMemo.oldTempoParameter;
                }
                if (newTempo > 0x20) currentBPM = newTempo;
                if (isModPlug && currentBPM > 0x200) currentBPM = 0x200; // 512 for MPT ITex
                else if (currentBPM > 0xff) currentBPM = 0xff;
                break;
            case 0x15:            // Fine Vibrato
                // This effect is identical to the vibrato, but has a 4x smaller amplitude (more precise).
                if ((aktMemo.assignedEffectParam >> 4) != 0) aktMemo.vibratoStep = aktMemo.assignedEffectParam >> 4;
                if ((aktMemo.assignedEffectParam & 0xF) != 0) {
                    aktMemo.vibratoAmplitude = aktMemo.assignedEffectParam & 0xF;
                    // s3m: do not distinguish in memory, is done in doVibratoEffect
                    if (isS3M) aktMemo.vibratoAmplitude <<= 2;
                }
                aktMemo.vibratoOn = true;
                doVibratoEffect(aktMemo, true);
                break;
            case 0x16:            // Set Global Volume
                if (aktMemo.assignedEffectParam <= 0x80) {
                    globalVolume = aktMemo.assignedEffectParam;
                    // normalize to 0x80 for others except IT
                    if (!isIT) {
                        globalVolume <<= 1;
                        if (globalVolume > ModConstants.MAXGLOBALVOLUME) globalVolume = ModConstants.MAXGLOBALVOLUME;
                    }
                }
                break;
            case 0x17:            // Global Volume Slide
                if (aktMemo.assignedEffectParam != 0) aktMemo.globalVolumSlideValue = aktMemo.assignedEffectParam;
                if (isFineSlide(aktMemo.globalVolumSlideValue))
                    doGlobalVolumeSlideEffect(aktMemo);
                break;
            case 0x18:            // Set Panning
                doPanning(aktMemo, aktMemo.assignedEffectParam, ModConstants.PanBits.Pan8Bit);
                break;
            case 0x19:            // Panbrello
                if ((aktMemo.assignedEffectParam >> 4) != 0) aktMemo.panbrelloStep = aktMemo.assignedEffectParam >> 4;
                if ((aktMemo.assignedEffectParam & 0xF) != 0)
                    aktMemo.panbrelloAmplitude = aktMemo.assignedEffectParam & 0xF;
                aktMemo.panbrelloOn = true;
                doPirandelloEffect(aktMemo);
                break;
            case 0x1A:            // Midi Macro
                MidiMacros macro = mod.getMidiConfig();
                if (macro != null) {
                    if (aktMemo.assignedEffectParam < 0x80)
                        processMIDIMacro(aktMemo, false, macro.getMidiSFXExt(aktMemo.activeMidiMacro), aktMemo.assignedEffectParam);
                    else
                        processMIDIMacro(aktMemo, false, macro.getMidiZXXExt(aktMemo.assignedEffectParam & 0x7F), 0);
                }
                break;
            case 0x1B:            // Parameter Extension
                // OMPT Specific, done as a look ahead, so just break here
                break;
            case 0x1C:            // Smooth Midi Macro
                MidiMacros smoothMacro = mod.getMidiConfig();
                if (smoothMacro != null) {
                    if (aktMemo.assignedEffectParam < 0x80)
                        processMIDIMacro(aktMemo, true, smoothMacro.getMidiSFXExt(aktMemo.activeMidiMacro), aktMemo.assignedEffectParam);
                    else
                        processMIDIMacro(aktMemo, true, smoothMacro.getMidiZXXExt(aktMemo.assignedEffectParam & 0x7F), 0);
                }
                break;
            default:
                //logger.log(Level.DEBUG, "Unknown Effect: Effect:%02X Op:%02X in [Pattern:%03d: Row:%03d Channel:%03d]".formatted(Integer.valueOf(aktMemo.effect), Integer.valueOf(aktMemo.effectParam), Integer.valueOf(currentPatternIndex), Integer.valueOf(currentRow), Integer.valueOf(aktMemo.channelNumber+1)));
                break;
        }
    }

    /**
     * @param aktMemo
     * @param slide
     * @since 28.03.2024
     */
    private void doFreqSlide(ChannelMemory aktMemo, int slide) {
        if (frequencyTableType == ModConstants.IT_LINEAR_TABLE) {
            int slideIndex = (slide < 0) ? -slide : slide;
            if (slideIndex > 255) slideIndex = 255;
            long oldPeriod = aktMemo.currentNotePeriod;
            if (slide < 0) {
                aktMemo.currentNotePeriod = (int) ((aktMemo.currentNotePeriod * ((long) ModConstants.LinearSlideUpTable[slideIndex])) >> ModConstants.HALFTONE_SHIFT);
                if (oldPeriod == aktMemo.currentNotePeriod) aktMemo.currentNotePeriod--;
            } else {
                aktMemo.currentNotePeriod = (int) ((aktMemo.currentNotePeriod * ((long) ModConstants.LinearSlideDownTable[slideIndex])) >> ModConstants.HALFTONE_SHIFT);
                if (oldPeriod == aktMemo.currentNotePeriod) aktMemo.currentNotePeriod++;
            }
        } else
            aktMemo.currentNotePeriod += slide << ModConstants.PERIOD_SHIFT;

        setNewPlayerTuningFor(aktMemo);
    }

    /**
     * Different
     *
     * @param aktMemo
     * @param slide
     * @since 28.03.2024
     */
    private void doExtraFineSlide(ChannelMemory aktMemo, int slide) {
        if (frequencyTableType == ModConstants.IT_LINEAR_TABLE) {
            int slideIndex = ((slide < 0) ? -slide : slide) & 0x0F;
            if (slide < 0) {
                aktMemo.currentNotePeriod = (int) ((aktMemo.currentNotePeriod * ((long) ModConstants.FineLinearSlideUpTable[slideIndex])) >> ModConstants.HALFTONE_SHIFT);
            } else {
                aktMemo.currentNotePeriod = (int) ((aktMemo.currentNotePeriod * ((long) ModConstants.FineLinearSlideDownTable[slideIndex])) >> ModConstants.HALFTONE_SHIFT);
            }
        } else
            aktMemo.currentNotePeriod += slide << (ModConstants.PERIOD_SHIFT - 2);

        setNewPlayerTuningFor(aktMemo);
    }

    /**
     * @param aktMemo
     * @param slide
     * @since 28.03.2024
     */
    private void doFineSlide(ChannelMemory aktMemo, int slide) {
        if (frequencyTableType == ModConstants.IT_LINEAR_TABLE) {
            int slideIndex = ((slide < 0) ? -slide : slide) & 0x0F;
            if (slide < 0) {
                aktMemo.currentNotePeriod = (int) ((aktMemo.currentNotePeriod * ((long) ModConstants.LinearSlideUpTable[slideIndex])) >> ModConstants.HALFTONE_SHIFT);
            } else {
                aktMemo.currentNotePeriod = (int) ((aktMemo.currentNotePeriod * ((long) ModConstants.LinearSlideDownTable[slideIndex])) >> ModConstants.HALFTONE_SHIFT);
            }
        } else
            aktMemo.currentNotePeriod += slide << ModConstants.PERIOD_SHIFT;

        setNewPlayerTuningFor(aktMemo);
    }

    /**
     * Convenient Method for the Porta Up Effect
     *
     * @param aktMemo
     * @since 08.06.2020
     */
    private void doPortaUp(ChannelMemory aktMemo, boolean firstTick, boolean inVolColum) {
        int indicatorPortaUp = aktMemo.portaStepUp & 0xF0;
        if (inVolColum)
            doFreqSlide(aktMemo, -aktMemo.portaStepUp);
        else {
            switch (indicatorPortaUp) {
                case 0xE0:
                    if (firstTick) doExtraFineSlide(aktMemo, -(aktMemo.portaStepUp & 0x0F));
                    break;
                case 0xF0:
                    if (firstTick) doFineSlide(aktMemo, -(aktMemo.portaStepUp & 0x0F));
                    break;
                default:
                    if (!firstTick) doFreqSlide(aktMemo, -aktMemo.portaStepUp);
                    break;
            }
        }
    }

    /**
     * Convenient Method for the Porta Down Effect
     *
     * @param aktMemo
     * @since 08.06.2020
     */
    private void doPortaDown(ChannelMemory aktMemo, boolean firstTick, boolean inVolColum) {
        int indicatorPortaUp = aktMemo.portaStepDown & 0xF0;
        if (inVolColum)
            doFreqSlide(aktMemo, aktMemo.portaStepDown);
        else {
            switch (indicatorPortaUp) {
                case 0xE0:
                    if (firstTick) doExtraFineSlide(aktMemo, aktMemo.portaStepDown & 0x0F);
                    break;
                case 0xF0:
                    if (firstTick) doFineSlide(aktMemo, aktMemo.portaStepDown & 0x0F);
                    break;
                default:
                    if (!firstTick) doFreqSlide(aktMemo, aktMemo.portaStepDown);
                    break;
            }
        }
    }

    /**
     * Convenient Method for the Porta to note Effect
     *
     * @param aktMemo
     */
    private void doPortalNoteEffect(ChannelMemory aktMemo) {
        if (aktMemo.portaTargetNotePeriod != aktMemo.currentNotePeriod && aktMemo.portaTargetNotePeriod != -1) {
            if (aktMemo.portaTargetNotePeriod < aktMemo.currentNotePeriod) {
                doFreqSlide(aktMemo, -aktMemo.portaNoteStep);
                if (aktMemo.currentNotePeriod <= aktMemo.portaTargetNotePeriod) {
                    aktMemo.currentNotePeriod = aktMemo.portaTargetNotePeriod;
                    aktMemo.portaTargetNotePeriod = -1;
                }
            } else {
                doFreqSlide(aktMemo, aktMemo.portaNoteStep);
                if (aktMemo.currentNotePeriod >= aktMemo.portaTargetNotePeriod) {
                    aktMemo.currentNotePeriod = aktMemo.portaTargetNotePeriod;
                    aktMemo.portaTargetNotePeriod = -1;
                }
            }
            if (aktMemo.glissando)
                setNewPlayerTuningFor(aktMemo, getRoundedPeriod(aktMemo, aktMemo.currentNotePeriod >> ModConstants.PERIOD_SHIFT) << ModConstants.PERIOD_SHIFT);
            else
                setNewPlayerTuningFor(aktMemo);
        }
    }

    /**
     * returns values in the range of -64..64
     *
     * @param type
     * @param position
     * @return
     * @since 29.06.2020
     */
    private int getVibratoDelta(int type, int position) {
        position &= 0xff;
        return switch (type & 0x03) {
            default -> //Sinus
                    ModConstants.ITSinusTable[position];
            case 1 -> // Ramp Down / Sawtooth
                    ModConstants.ITRampDownTable[position];
            case 2 -> // Squarewave
                    ModConstants.ITSquareTable[position];
            case 3 -> // random
                    (int) (128 * swinger.nextDouble() - 0x40);
        };
    }

    @Override
    protected void doAutoVibratoEffect(ChannelMemory aktMemo, Sample currentSample, int currentPeriod) {
        if (currentSample.vibratoRate == 0) return;
        int maxDepth = currentSample.vibratoDepth << 8;
        int periodAdd = 0;

//        if (config.ITVibratoTremoloPanbrello) {

        // Schism / OpenMPT implementation adopted
        int position = aktMemo.autoVibratoTablePos & 0xff;
        // sweep = rate<<2, rate = speed, depth = depth
        int depth = aktMemo.autoVibratoAmplitude;
        depth += currentSample.vibratoSweep & 0xff;
        if (depth > maxDepth) depth = maxDepth;
        aktMemo.autoVibratoAmplitude = depth;
        depth >>= 8;

        aktMemo.autoVibratoTablePos += currentSample.vibratoRate;
        periodAdd = switch (currentSample.vibratoType & 0x07) {
            default -> ModConstants.ITSinusTable[position];        // Sine
            case 1 -> position < 128 ? 0x40 : 0;                        // Square
            case 2 -> ((position + 1) >> 1) - 0x40;                    // Ramp Up
            case 3 -> 0x40 - ((position + 1) >> 1);                    // Ramp Down
            case 4 -> (int) (128 * swinger.nextDouble() - 0x40);    // Random
        };
        periodAdd = (periodAdd * depth) >> 6;

        int[] linearSlideTable = (periodAdd < 0) ? ModConstants.LinearSlideUpTable : ModConstants.LinearSlideDownTable;
        int[] fineLinearSlideTable = (periodAdd < 0) ? ModConstants.FineLinearSlideUpTable : ModConstants.FineLinearSlideDownTable;
        int slideIndex = (periodAdd < 0) ? -periodAdd : periodAdd;
        if (slideIndex < 16)
            periodAdd = ((int) ((currentPeriod * (long) fineLinearSlideTable[slideIndex]) >> ModConstants.HALFTONE_SHIFT)) - currentPeriod;
        else
            periodAdd = ((int) ((currentPeriod * (long) linearSlideTable[slideIndex >> 2]) >> ModConstants.HALFTONE_SHIFT)) - currentPeriod;

        setNewPlayerTuningFor(aktMemo, currentPeriod - periodAdd);

//        } else { // ModPlug does this quit differently, but is only used if set via config - we need to read that TODO: read config for this!
//
//            aktMemo.autoVibratoAmplitude += currentSample.vibratoSweep << 1;
//            if (aktMemo.autoVibratoAmplitude > maxDepth) aktMemo.autoVibratoAmplitude = maxDepth;
//
//            aktMemo.autoVibratoTablePos += currentSample.vibratoRate;
//            switch (currentSample.vibratoType & 0x07) {
//                default:
//                case 0:
//                    periodAdd = -ModConstants.ITSinusTable[aktMemo.autoVibratoTablePos & 0xff];        // Sine
//                    break;
//                case 1:
//                    periodAdd = (aktMemo.autoVibratoTablePos & 0x80) != 0 ? 0x40 : -0x40;                    // Square
//                    break;
//                case 2:
//                    periodAdd = ((0x40 + (aktMemo.autoVibratoTablePos >> 1)) & 0x7F) - 0x40;            // Ramp Up
//                    break;
//                case 3:
//                    periodAdd = ((0x40 - (aktMemo.autoVibratoTablePos >> 1)) & 0x7F) - 0x40;            // Ramp Down
//                    break;
//                case 4:
//                    periodAdd = ModConstants.ModRandomTable[aktMemo.autoVibratoTablePos & 0x3F];    // Random
//                    break;
//            }
//            int n = (periodAdd * aktMemo.autoVibratoAmplitude) >> 8;
//
//            int[] linearSlideTable;
//            if (n < 0) {
//                n = -n;
//                linearSlideTable = ModConstants.LinearSlideUpTable;
//            } else
//                linearSlideTable = ModConstants.LinearSlideDownTable;
//            final int n1 = n >> 8;
//            final long df1 = linearSlideTable[n1];
//            final long df2 = linearSlideTable[n1 + 1];
//            n >>= 2;
//            setNewPlayerTuningFor(aktMemo, (int) ((currentPeriod * (df1 + (((df2 - df1) * ((long) n & 0x3F)) >> 6))) >> ModConstants.HALFTONE_SHIFT));
//        }
    }

    /**
     * Convenient Method for the vibrato effect
     *
     * @param aktMemo
     */
    protected void doVibratoEffect(ChannelMemory aktMemo, boolean doFineVibrato) {
        boolean isTick0 = currentTick == currentTempo;
        boolean oldITEffects = (mod.getSongFlags() & ModConstants.SONG_ITOLDEFFECTS) != 0;

        int vibPos = aktMemo.vibratoTablePos & 0xff;
        int periodAdd = getVibratoDelta(aktMemo.vibratoType, vibPos);

        int vdepth = 6;
//        if (config.ITVibratoTremoloPanbrello) { // TODO: Config for IT / MPTP files - we need to read that.

        if (isIT && oldITEffects) { // With old effects two times deeper and reversed
            vdepth = 5;
            periodAdd = -periodAdd;
        }
        if (isS3M || isSTM) {
            if ((mod.getSongFlags() & ModConstants.SONG_ST2VIBRATO) != 0)
                vdepth = 5;
            // with s3m vibrato types are equal in effect memory - fine slide is done here...
            if (isS3M && doFineVibrato)
                vdepth += 2; // same result as periodAdd>>=2;
        }

        periodAdd = (periodAdd * aktMemo.vibratoAmplitude) >> vdepth; // more or less the same as "/ (1 << attenuation)" :)

        if (frequencyTableType == ModConstants.IT_LINEAR_TABLE) {
            int slideIndex = (periodAdd < 0) ? -periodAdd : periodAdd;
            if (slideIndex > (255 << 2)) slideIndex = (255 << 2);
            long period = aktMemo.currentNotePeriod;

            // Formula: ((period * table[index / 4]) - period) + ((period * fineTable[index % 4]) - period)
            if (periodAdd < 0) {
                periodAdd = (int) (((period * ((long) ModConstants.LinearSlideUpTable[slideIndex >> 2])) >> ModConstants.HALFTONE_SHIFT) - period);
                if ((slideIndex & 0x03) != 0)
                    periodAdd += (int) (((period * ((long) ModConstants.FineLinearSlideUpTable[slideIndex & 0x3])) >> ModConstants.HALFTONE_SHIFT) - period);
            } else {
                periodAdd = (int) (((period * ((long) ModConstants.LinearSlideDownTable[slideIndex >> 2])) >> ModConstants.HALFTONE_SHIFT) - period);
                if ((slideIndex & 0x03) != 0)
                    periodAdd += (int) (((period * ((long) ModConstants.FineLinearSlideDownTable[slideIndex & 0x3])) >> ModConstants.HALFTONE_SHIFT) - period);
            }
            setNewPlayerTuningFor(aktMemo, aktMemo.currentNotePeriod - periodAdd);
        } else
            setNewPlayerTuningFor(aktMemo, aktMemo.currentNotePeriod - (periodAdd << 2));

        if (!isTick0 || (isIT && !oldITEffects)) aktMemo.vibratoTablePos = (vibPos + (aktMemo.vibratoStep << 2)) & 0xff;
    }

    /**
     * Convenient Method for the panbrello effect
     *
     * @param aktMemo
     */
    protected void doPirandelloEffect(ChannelMemory aktMemo) {
        int pDelta = getVibratoDelta(aktMemo.panbrelloType, aktMemo.panbrelloTablePos);
        // IT has a more precise table from 0..64
        // With s3m and stm we need values from 0..256
        // but we shift only one (0..128) because the back shift is only 3, not 4 (see at XM)
        if (!isIT) pDelta <<= 1;

        if (isIT && aktMemo.panbrelloType == 3) { // Random type
            // IT compatibility: Sample-and-hold style random panbrello (tremolo and vibrato don't use this mechanism in IT)
            if (aktMemo.panbrelloTablePos == 0 || aktMemo.panbrelloTablePos >= aktMemo.panbrelloStep) {
                aktMemo.panbrelloTablePos = 0;
                aktMemo.panbrelloRandomMemory = pDelta;
            }
            aktMemo.panbrelloTablePos++;
            pDelta = aktMemo.panbrelloRandomMemory;
        } else
            aktMemo.panbrelloTablePos += aktMemo.panbrelloStep;

        int newPanning = aktMemo.currentInstrumentPanning + (((pDelta * aktMemo.panbrelloAmplitude) + 2) >> 3); // +2: round me at bit 1
        aktMemo.panning = (newPanning < 0) ? 0 : ((newPanning > 256) ? 256 : newPanning);
        aktMemo.doFastVolRamp = true;
    }

    /**
     * Convenient Method for the tremolo effect
     *
     * @param aktMemo
     */
    protected void doTremoloEffect(ChannelMemory aktMemo) {
        boolean isTick0 = currentTick == currentTempo;
        boolean oldITEffects = (mod.getSongFlags() & ModConstants.SONG_ITOLDEFFECTS) != 0;
        // What the... ITs do not reset the tremolo table pos, when not set to use oldITEffects
        if (isTick0 && hasNewNote(aktMemo.currentElement) && !aktMemo.tremoloNoRetrig && (!isIT || oldITEffects))
            aktMemo.tremoloTablePos = 0;

        if (aktMemo.currentVolume > 0 || isIT) {
            int delta = getVibratoDelta(aktMemo.tremoloType, aktMemo.tremoloTablePos);
            aktMemo.currentVolume = aktMemo.currentInstrumentVolume + ((delta * aktMemo.tremoloAmplitude) >> 5); // normally >>6 because -64..+64
            if (aktMemo.currentVolume > ModConstants.MAX_SAMPLE_VOL)
                aktMemo.currentVolume = ModConstants.MAX_SAMPLE_VOL;
            else if (aktMemo.currentVolume < ModConstants.MIN_SAMPLE_VOL)
                aktMemo.currentVolume = ModConstants.MIN_SAMPLE_VOL;
            aktMemo.doFastVolRamp = true;
        }
        if (!isTick0 || (isIT && !oldITEffects)) aktMemo.tremoloTablePos += aktMemo.tremoloStep << 2;
    }

    /**
     * The tremor effect
     *
     * @param aktMemo
     */
    protected void doTremorEffect(ChannelMemory aktMemo) {
        aktMemo.tremorWasActive = true;
        // if both are not set, set to current values
        // see also commented reset after offtime reached
        if (aktMemo.tremorOntime <= 0 && aktMemo.tremorOfftime <= 0) {
            aktMemo.tremorOntime = aktMemo.tremorOntimeSet;
            aktMemo.tremorOfftime = aktMemo.tremorOfftimeSet;
        }

        if (aktMemo.tremorOntime > 0) {
            aktMemo.tremorOntime--;
            // set Offtime to current value set.
            if (aktMemo.tremorOntime <= 0) aktMemo.tremorOfftime = aktMemo.tremorOfftimeSet;
            aktMemo.currentVolume = aktMemo.currentInstrumentVolume;
            aktMemo.doFastVolRamp = true;
        } else if (aktMemo.tremorOfftime > 0) {
            aktMemo.tremorOfftime--;
            // asynchronous! - in next row new values for ontime/offtime can be set
            // we need to take a look into next row first, so don't do this...
            //if (aktMemo.tremorOfftime<=0) aktMemo.tremorOntime = aktMemo.tremorOntimeSet;
            aktMemo.currentVolume = 0;
            aktMemo.doFastVolRamp = true;
        }
    }

    /**
     * Will validate the new sampleOffset and do the S3M magic
     *
     * @param aktMemo
     * @param newSampleOffset
     * @return
     * @since 05.08.2024
     */
    protected int validateNewSampleOffset(ChannelMemory aktMemo, int newSampleOffset) {
        Sample sample = aktMemo.currentSample;
        boolean hasLoop = (sample.loopType & ModConstants.LOOP_ON) != 0;
        int length = hasLoop ? sample.loopStop : sample.length;

        if (newSampleOffset >= length) {
            if (isS3M) {
                // ST3 Compatibility: Don't play note if offset is beyond sample length (non-looped samples only)
                // else do offset wrap-around - does this in GUS mode, not in SoundBlaster mode
                if (!hasLoop || (mod.getSongFlags() & ModConstants.SONG_S3M_GUS) == 0)
                    return length - 1;
                else
                    return ((newSampleOffset - sample.loopStart) % sample.loopLength) + sample.loopStart;
            } else if (isIT) {
                if ((mod.getSongFlags() & ModConstants.SONG_ITOLDEFFECTS) != 0) // Old Effects
                    return length - 1;
                else
                    return 0; // reset to start
            } else {
                if (hasLoop)
                    return sample.loopStart;
                else
                    return length - 1;
            }
        }
        return newSampleOffset;
    }

    /**
     * @param aktMemo
     * @param element
     * @since 03.07.2020
     */
    protected void doSampleOffsetEffect(ChannelMemory aktMemo, PatternElement element) {
        if (hasNoNote(element) || aktMemo.currentSample == null || aktMemo.sampleOffset == -1) return;

        // IT compatibility: If this note is not mapped to a sample, ignore it.
        // It is questionable, if this check is needed - aktMemo.currentSample should already be null...
        // BTW: aktMemo.assignedSample is null then, too
//        if (isIT) {
//            if (aktMemo.currentAssignedInstrument != null) {
//                final int sampleIndex = aktMemo.currentAssignedInstrument.getSampleIndex(aktMemo.assignedNoteIndex - 1);
//                if (sampleIndex <= 0 || sampleIndex > mod.getNSamples()) return;
//            }
//        }

        aktMemo.currentSamplePos = validateNewSampleOffset(aktMemo, aktMemo.sampleOffset);
    }

    /**
     * With IT Mods arpeggios refer to the current pitch (currentNotePeriod)
     * and cannot be calculated in advance. We need to do that here, when
     * it is needed. Formula is (currentNotePeriod / 2^(halftone/12))
     *
     * @param aktMemo
     * @since 06.06.2020
     */
    protected void doArpeggioEffect(ChannelMemory aktMemo) {
        aktMemo.arpeggioIndex = (aktMemo.arpeggioIndex + 1) % 3;
        int nextNotePeriod = 0;
        if (isIT) {
            if (aktMemo.arpeggioIndex == 0)
                nextNotePeriod = aktMemo.currentNotePeriod;
            else {
                long factor = ModConstants.halfToneTab[(aktMemo.arpeggioIndex == 1) ? (aktMemo.arpeggioParam >> 4) : (aktMemo.arpeggioParam & 0xF)];
                nextNotePeriod = (int) ((((long) aktMemo.currentNotePeriod) * factor) >> ModConstants.HALFTONE_SHIFT);
            }
        } else {
            nextNotePeriod = aktMemo.arpeggioNote[aktMemo.arpeggioIndex];
        }
        if (nextNotePeriod != 0) setNewPlayerTuningFor(aktMemo, nextNotePeriod);
    }

    /**
     * Convenient Method for the VolumeSlide Effect
     *
     * @param aktMemo
     */
    protected void doVolumeSlideEffect(ChannelMemory aktMemo) {
        aktMemo.currentVolume += getFineSlideValue(aktMemo.volumSlideValue);
        if (aktMemo.currentVolume > ModConstants.MAX_SAMPLE_VOL) aktMemo.currentVolume = ModConstants.MAX_SAMPLE_VOL;
        else if (aktMemo.currentVolume < ModConstants.MIN_SAMPLE_VOL)
            aktMemo.currentVolume = ModConstants.MIN_SAMPLE_VOL;
        aktMemo.currentInstrumentVolume = aktMemo.currentVolume;
        aktMemo.doFastVolRamp = true;
    }

    /**
     * Same as the volumeSlide, but affects the channel volume
     *
     * @param aktMemo
     * @since 21.06.2006
     */
    protected void doChannelVolumeSlideEffect(ChannelMemory aktMemo) {
        aktMemo.channelVolume += getFineSlideValue(aktMemo.channelVolumeSlideValue);
        if (aktMemo.channelVolume > ModConstants.MAXSAMPLEVOLUME) aktMemo.channelVolume = ModConstants.MAXSAMPLEVOLUME;
        else if (aktMemo.channelVolume < 0) aktMemo.channelVolume = 0;
    }

    /**
     * Convenient Method for the panning slide Effect
     *
     * @param aktMemo
     */
    protected void doPanningSlideEffect(ChannelMemory aktMemo) {
        aktMemo.doSurround = false;
        aktMemo.panning -= getFineSlideValue(aktMemo.panningSlideValue) << 2;
        if (aktMemo.panning < 0) aktMemo.panning = 0;
        else if (aktMemo.panning > 256) aktMemo.panning = 256;
        aktMemo.currentInstrumentPanning = aktMemo.panning; // IT stays on panning value and pans around that one
        aktMemo.doFastVolRamp = true;
    }

    /**
     * Convenient Method for the Global VolumeSlideEffect
     *
     * @param aktMemo
     * @since 21.06.2006
     */
    protected void doGlobalVolumeSlideEffect(ChannelMemory aktMemo) {
        // Consider "Fine Global Slide" 0xFx || 0xxF
        if ((aktMemo.globalVolumSlideValue & 0x0F) == 0x0F && (aktMemo.globalVolumSlideValue & 0xF0) != 0) {
            int param = aktMemo.globalVolumSlideValue >> 4;
            if (!isIT) param <<= 1;
            globalVolume += param;
        } else if ((aktMemo.globalVolumSlideValue & 0xF0) == 0xF0 && (aktMemo.globalVolumSlideValue & 0x0F) != 0) {
            int param = aktMemo.globalVolumSlideValue & 0xF;
            if (!isIT) param <<= 1;
            globalVolume -= param;
        } else if ((aktMemo.globalVolumSlideValue & 0xF0) != 0) {
            int param = aktMemo.globalVolumSlideValue >> 4;
            if (!isIT) param <<= 1;
            globalVolume += param;
        } else if ((aktMemo.globalVolumSlideValue & 0x0F) != 0) {
            int param = aktMemo.globalVolumSlideValue & 0xF;
            if (!isIT) param <<= 1;
            globalVolume -= param;
        }

        if (globalVolume > ModConstants.MAXGLOBALVOLUME) globalVolume = ModConstants.MAXGLOBALVOLUME;
        else if (globalVolume < 0) globalVolume = 0;
    }

    /**
     * Re-Triggers the note and does volume slide
     *
     * @param aktMemo
     * @since 04.04.2020
     */
    protected void doRetrigNote(ChannelMemory aktMemo, boolean inTick) {
        if (((currentTempo - currentTick) % aktMemo.retrigMemo) == 0 && inTick) {
            aktMemo.retrigCount = aktMemo.retrigMemo;

            resetInstrumentPointers(aktMemo, true);

            if (aktMemo.retrigVolSlide > 0) {
                switch (aktMemo.retrigVolSlide) {
                    case 0x1:
                        aktMemo.currentVolume--;
                        break;
                    case 0x2:
                        aktMemo.currentVolume -= 2;
                        break;
                    case 0x3:
                        aktMemo.currentVolume -= 4;
                        break;
                    case 0x4:
                        aktMemo.currentVolume -= 8;
                        break;
                    case 0x5:
                        aktMemo.currentVolume -= 16;
                        break;
                    case 0x6:
                        aktMemo.currentVolume = (aktMemo.currentVolume << 1) / 3;
                        break;
                    case 0x7:
                        aktMemo.currentVolume >>= 1;
                        break;
                    case 0x8: /* No volume change */
                        break;
                    case 0x9:
                        aktMemo.currentVolume++;
                        break;
                    case 0xA:
                        aktMemo.currentVolume += 2;
                        break;
                    case 0xB:
                        aktMemo.currentVolume += 4;
                        break;
                    case 0xC:
                        aktMemo.currentVolume += 8;
                        break;
                    case 0xD:
                        aktMemo.currentVolume += 16;
                        break;
                    case 0xE:
                        aktMemo.currentVolume = (aktMemo.currentVolume * 3) >> 1;
                        break;
                    case 0xF:
                        aktMemo.currentVolume <<= 1;
                        break;
                }
                if (aktMemo.currentVolume > ModConstants.MAX_SAMPLE_VOL)
                    aktMemo.currentVolume = ModConstants.MAX_SAMPLE_VOL;
                else if (aktMemo.currentVolume < ModConstants.MIN_SAMPLE_VOL)
                    aktMemo.currentVolume = ModConstants.MIN_SAMPLE_VOL;
                aktMemo.currentInstrumentVolume = aktMemo.currentVolume;
                aktMemo.doFastVolRamp = true;
            }
        }
    }

    /**
     * @param aktMemo
     * @see de.quippy.javamod.multimedia.mod.mixer.BasicModMixer#doTickEffects(de.quippy.javamod.multimedia.mod.mixer.BasicModMixer.ChannelMemory)
     */
    @Override
    protected void doTickEffects(ChannelMemory aktMemo) {
        if (aktMemo.assignedEffect == 0) return;

        switch (aktMemo.assignedEffect) {
            case 0x04:        // VolumeSlide, BUT Fine Slide only on first Tick
                if (isSTM && aktMemo.assignedEffectParam == 0) break;
                if (patternDelayCount > 0 && currentTick == currentTempo) doRowEffects(aktMemo);
                else {
                    if (!isFineSlide(aktMemo.volumSlideValue))
                        doVolumeSlideEffect(aktMemo);
                }
                break;
            case 0x05:            // Porta Down
                if (isSTM && aktMemo.assignedEffectParam == 0) break; // pick up target note!
                if (patternDelayCount > 0 && currentTick == currentTempo) doRowEffects(aktMemo);
                else {
                    doPortaDown(aktMemo, false, false);
                }
                break;
            case 0x06:            // Porta Up
                if (isSTM && aktMemo.assignedEffectParam == 0) break; // pick up target note!
                if (patternDelayCount > 0 && currentTick == currentTempo) doRowEffects(aktMemo);
                else {
                    doPortaUp(aktMemo, false, false);
                }
                break;
            case 0x07:            // Porta to Note
                if (isSTM && aktMemo.assignedEffectParam == 0) break; // pick up target note!
                doPortalNoteEffect(aktMemo);
                break;
            case 0x08:            // Vibrato
                if (isSTM && aktMemo.assignedEffectParam == 0) break; // pick up target note!
                doVibratoEffect(aktMemo, false);
                break;
            case 0x09:            // Tremor
                doTremorEffect(aktMemo);
                break;
            case 0x0A:            // Arpeggio
                if (isSTM && aktMemo.assignedEffectParam == 0) break; // pick up target note!
                doArpeggioEffect(aktMemo);
                break;
            case 0x0B:            // Vibrato + VolumeSlide
                if (isSTM && aktMemo.assignedEffectParam == 0) break; // pick up target note!
                if (patternDelayCount > 0 && currentTick == currentTempo) doRowEffects(aktMemo);
                else {
                    doVibratoEffect(aktMemo, false);
                    if (!isFineSlide(aktMemo.volumSlideValue))
                        doVolumeSlideEffect(aktMemo);
                }
                break;
            case 0x0C:            // Porta to Note + VolumeSlide
                if (isSTM && aktMemo.assignedEffectParam == 0) break; // pick up target note!
                if (patternDelayCount > 0 && currentTick == currentTempo) doRowEffects(aktMemo);
                else {
                    doPortalNoteEffect(aktMemo);
                    if (!isFineSlide(aktMemo.volumSlideValue))
                        doVolumeSlideEffect(aktMemo);
                }
                break;
            case 0x0E:            // Channel Volume Slide, if *NOT* Fine Slide
                if (patternDelayCount > 0 && currentTick == currentTempo) doRowEffects(aktMemo);
                else {
                    if (!isFineSlide(aktMemo.channelVolumeSlideValue))
                        doChannelVolumeSlideEffect(aktMemo);
                }
                break;
            case 0x10:            // Panning Slide
                if (patternDelayCount > 0 && currentTick == currentTempo) doRowEffects(aktMemo);
                else {
                    if (!isFineSlide(aktMemo.panningSlideValue))
                        doPanningSlideEffect(aktMemo);
                }
                break;
            case 0x11:            // Retrig Note
                doRetrigNote(aktMemo, true);
                break;
            case 0x12:            // Tremolo
                doTremoloEffect(aktMemo);
                break;
            case 0x13:            // Extended
                int effectParam = (aktMemo.assignedEffectParam == 0) ? aktMemo.S_Effect_Memory : aktMemo.assignedEffectParam;
                switch (effectParam >> 4) {
                    case 0x8:    // Fine Panning
                        if (patternDelayCount > 0 && currentTick == currentTempo) doRowEffects(aktMemo);
                        break;
                    case 0xC:    // Note Cut
                        if (aktMemo.noteCutCount > 0) {
                            aktMemo.noteCutCount--;
                            if (aktMemo.noteCutCount <= 0) {
                                aktMemo.noteCutCount = -1;
                                doNoteCut(aktMemo);
                                aktMemo.doFastVolRamp = true;
                            }
                        }
                        break;
                    case 0xD:    // Note Delay
                        // we do this globally!
                        break;
                }
                break;
            case 0x14:            // Set Speed
                int newTempo = aktMemo.oldTempoParameter;
                if ((newTempo & 0xF0) == 0x00) {   // 0x0X
                    currentBPM -= newTempo & 0xF;
                    if (currentBPM < 0x20) currentBPM = 0x20;
                } else if ((newTempo & 0xF0) == 0x10) {   // 0x1X
                    currentBPM += newTempo & 0xF;
                    if (isModPlug && currentBPM > 0x200) currentBPM = 0x200; // 512 for MPT ITex
                    else if (currentBPM > 0xff) currentBPM = 0xff;
                }
                break;
            case 0x15:            // Fine Vibrato
                doVibratoEffect(aktMemo, true);
                break;
            case 0x17:            // Global Volume Slide
                if (!isFineSlide(aktMemo.globalVolumSlideValue))
                    doGlobalVolumeSlideEffect(aktMemo);
                break;
            case 0x19:            // Panbrello
                doPirandelloEffect(aktMemo);
                break;
            case 0x1C:            // Smooth Midi Macro
                MidiMacros smoothMacro = mod.getMidiConfig();
                if (smoothMacro != null) {
                    if (aktMemo.assignedEffectParam < 0x80)
                        processMIDIMacro(aktMemo, true, smoothMacro.getMidiSFXExt(aktMemo.activeMidiMacro), aktMemo.assignedEffectParam);
                    else
                        processMIDIMacro(aktMemo, true, smoothMacro.getMidiZXXExt(aktMemo.assignedEffectParam & 0x7F), 0);
                }
                break;
        }
    }

    /**
     * @param aktMemo
     * @see de.quippy.javamod.multimedia.mod.mixer.BasicModMixer#doVolumeColumnRowEffect(de.quippy.javamod.multimedia.mod.mixer.BasicModMixer.ChannelMemory)
     */
    @Override
    protected void doVolumeColumnRowEffect(ChannelMemory aktMemo) {
        if (aktMemo.assignedVolumeEffect == 0) return;

        switch (aktMemo.assignedVolumeEffect) {
            case 0x01: // Set Volume
                aktMemo.currentVolume = aktMemo.assignedVolumeEffectOp;
                if (aktMemo.currentVolume > ModConstants.MAX_SAMPLE_VOL)
                    aktMemo.currentVolume = ModConstants.MAX_SAMPLE_VOL;
                else if (aktMemo.currentVolume < ModConstants.MIN_SAMPLE_VOL)
                    aktMemo.currentVolume = ModConstants.MIN_SAMPLE_VOL;
                aktMemo.currentInstrumentVolume = aktMemo.currentVolume;
                aktMemo.doFastVolRamp = true;
                break;
            case 0x02: // Volslide down
                if (aktMemo.assignedVolumeEffectOp != 0) aktMemo.volumSlideValue = aktMemo.assignedVolumeEffectOp & 0xF;
                if ((mod.getSongFlags() & ModConstants.SONG_FASTVOLSLIDES) != 0)
                    doVolumeSlideEffect(aktMemo);
                break;
            case 0x03: // Volslide up
                if (aktMemo.assignedVolumeEffectOp != 0)
                    aktMemo.volumSlideValue = (aktMemo.assignedVolumeEffectOp << 4) & 0xF0;
                if ((mod.getSongFlags() & ModConstants.SONG_FASTVOLSLIDES) != 0)
                    doVolumeSlideEffect(aktMemo);
                break;
            case 0x04: // Fine Volslide down
                if (aktMemo.assignedVolumeEffectOp != 0)
                    aktMemo.volumSlideValue = (aktMemo.assignedVolumeEffectOp & 0xF) | 0xF0;
                doVolumeSlideEffect(aktMemo);
                break;
            case 0x05: // Fine Volslide up
                if (aktMemo.assignedVolumeEffectOp != 0)
                    aktMemo.volumSlideValue = ((aktMemo.assignedVolumeEffectOp << 4) & 0xF0) | 0x0F;
                doVolumeSlideEffect(aktMemo);
                break;
            case 0x06: // vibrato speed - does not activate... // only ModPlug Version <= 1.17.02.54 did this...
                aktMemo.vibratoStep = aktMemo.assignedVolumeEffectOp;
                break;
            case 0x07: // vibrato depth and enable
                if (aktMemo.assignedVolumeEffectOp != 0) aktMemo.vibratoAmplitude = aktMemo.assignedVolumeEffectOp << 2;
                aktMemo.vibratoOn = true;
                doVibratoEffect(aktMemo, false);
                break;
            case 0x08: // Set Panning
                doPanning(aktMemo, aktMemo.assignedVolumeEffectOp, ModConstants.PanBits.Pan6Bit);
                break;
            case 0x0B: // Tone Porta
                PatternElement element = aktMemo.currentElement;
                if (hasNewNote(element)) aktMemo.portaTargetNotePeriod = getFineTunePeriod(aktMemo);
                if (aktMemo.assignedVolumeEffectOp != 0) {
                    int index = (aktMemo.assignedVolumeEffectOp > 9) ? 9 : aktMemo.assignedVolumeEffectOp & 0x0F;
                    aktMemo.portaNoteStep = ModConstants.IT_VolColumnPortaNoteSpeedTranslation[index];
                    if (isNotITCompatMode) aktMemo.IT_EFG = aktMemo.portaNoteStep;
                }
                break;
            case 0x0C: // Porta Down
                if (aktMemo.assignedVolumeEffectOp != 0) {
                    aktMemo.portaStepDown = aktMemo.assignedVolumeEffectOp << 2;
                    if (isNotITCompatMode) aktMemo.IT_EFG = aktMemo.portaStepDown;
                }
                break;
            case 0x0D: // Porta Up
                if (aktMemo.assignedVolumeEffectOp != 0) {
                    aktMemo.portaStepUp = aktMemo.assignedVolumeEffectOp << 2;
                    if (isNotITCompatMode) aktMemo.IT_EFG = aktMemo.portaStepUp;
                }
                break;
            case 0x0E: // Sample Cues - MPT specific
                Sample sample = aktMemo.currentSample;
                if (sample != null) {
                    int[] cues = sample.getCues();
                    if (cues != null && aktMemo.assignedVolumeEffectOp <= cues.length) {
                        if (aktMemo.assignedVolumeEffectOp != 0)
                            aktMemo.sampleOffset = cues[aktMemo.assignedVolumeEffectOp - 1];
                        doSampleOffsetEffect(aktMemo, aktMemo.currentElement);
                    }
                }
                break;
            default:
                //logger.log(Level.DEBUG, "Unknown Volume Effect: Effect:%02X Op:%02X in [Pattern:%03d: Row:%03d Channel:%03d]".formatted(Integer.valueOf(aktMemo.volumeEffect), Integer.valueOf(aktMemo.volumeEffectOp), Integer.valueOf(currentPatternIndex), Integer.valueOf(currentRow), Integer.valueOf(aktMemo.channelNumber+1)));
                break;
        }
    }

    /**
     * @param aktMemo
     * @see de.quippy.javamod.multimedia.mod.mixer.BasicModMixer#doVolumeColumnTickEffect(de.quippy.javamod.multimedia.mod.mixer.BasicModMixer.ChannelMemory)
     */
    @Override
    protected void doVolumeColumnTickEffect(ChannelMemory aktMemo) {
        if (aktMemo.assignedVolumeEffect == 0) return;

        switch (aktMemo.assignedVolumeEffect) {
            case 0x02: // Volslide down
            case 0x03: // Volslide up
                doVolumeSlideEffect(aktMemo);
                break;
            case 0x04: // Fine Volslide down
            case 0x05: // Fine Volslide up
                if (patternDelayCount > 0 && currentTick == currentTempo) doVolumeColumnRowEffect(aktMemo);
                break;
            case 0x07: // vibrato speed
                doVibratoEffect(aktMemo, false);
                break;
            case 0x09: // Panning Slide Left
            case 0x0A: // Panning Slide Right
                doPanningSlideEffect(aktMemo);
                break;
            case 0x0B: // Tone Porta
                doPortalNoteEffect(aktMemo);
                break;
            case 0x0C: // Porta Down
                doPortaDown(aktMemo, false, true);
                break;
            case 0x0D: // Porta Up
                doPortaUp(aktMemo, false, true);
                break;
        }
    }

    @Override
    protected boolean isNoteDelayEffect(int effect, int effectParam) {
        boolean isEffect = effect == 0x13 && (effectParam >> 4) == 0x0D;
        if (isS3M && isEffect && (effectParam & 0xF) == 0) return false;
        return isEffect;
    }

    @Override
    protected boolean isPatternFramesDelayEffect(int effect, int effectParam) {
        return effect == 0x13 && (effectParam >> 4) == 0x06;
    }

    @Override
    protected boolean isPortaToNoteEffect(int effect, int effectParam, int volEffect, int volEffectParam, int notePeriod) {
        return ((effect == 0x07 || effect == 0x0C) || volEffect == 0x0B) && notePeriod != 0;
    }

    @Override
    protected boolean isSampleOffsetEffect(int effect) {
        return effect == 0x0F;
    }

    @Override
    protected boolean isKeyOffEffect(int effect, int effectParam) {
        return false;
    }

    @Override
    protected boolean isNNAEffect(int effect, int effectParam) {
        return effect == 0x13 && (effectParam >> 4) == 0x7 && (effectParam & 0xF) <= 0x6;
    }

    @Override
    protected int getEffectOpMemory(ChannelMemory aktMemo, int effect, int effectParam) {
        if (effect == 0x13 && effectParam == 0) return aktMemo.S_Effect_Memory;
        return effectParam;
    }

    /**
     * Some effects in IT need to be done after the tick effects
     *
     * @param effect
     * @return
     * @since 06.07.2024
     */
    private boolean isAfterEffect(int effect) {
        return switch (effect) { // Vibrato
            // Tremor
            // Arpeggio
            case 0x08, 0x09, 0x0A, 0x0B -> // Vibrato + VolSlide
                    isIT;
            default -> false;
        };
    }

    /**
     * @param aktMemo
     * @see de.quippy.javamod.multimedia.mod.mixer.BasicModMixer#processTickEffects(de.quippy.javamod.multimedia.mod.mixer.BasicModMixer.ChannelMemory)
     */
    @Override
    protected void processTickEffects(ChannelMemory aktMemo) {
        if (isS3M) {
            if (aktMemo.muteWasITforced/* || aktMemo.muted*/) return; // no effects in muted channels with S3Ms
            if (!isModPlug && aktMemo.assignedEffect > 0x16) return; // Effects not implemented in S3Ms
        }
        if (isSTM && aktMemo.assignedEffect > 0x0A)
            return; // even though these effects can be edited, they have no effect in ScreamTracker 2.2

        boolean isAfterEffect = (isIT) ? isAfterEffect(aktMemo.assignedEffect) : false;
        if (!isAfterEffect) doTickEffects(aktMemo);
        doVolumeColumnTickEffect(aktMemo);
        if (isAfterEffect) doTickEffects(aktMemo);
    }

    /**
     * @param aktMemo
     * @see de.quippy.javamod.multimedia.mod.mixer.BasicModMixer#processEffects(de.quippy.javamod.multimedia.mod.mixer.BasicModMixer.ChannelMemory)
     */
    @Override
    protected void processEffects(ChannelMemory aktMemo) {
        if (isS3M) {
            if (aktMemo.muteWasITforced/* || aktMemo.muted*/) return; // no effects in muted channels with S3Ms
            if (!isModPlug && aktMemo.assignedEffect > 0x16) return; // Effects not implemented in S3Ms
        }
        if (isSTM && aktMemo.assignedEffect > 0x0A)
            return; // even though these effects can be edited, they have no effect in ScreamTracker 2.2

        // shared Effect memory EFG is sharing information only on tick 0!
        // we cannot share during effects. Only with IT Compat Mode off!
        // *** IT Compat Off means, old stm, s3m ... ***
        if (isNotITCompatMode)
            aktMemo.portaStepDown = aktMemo.portaStepUp = aktMemo.portaNoteStep = aktMemo.IT_EFG;

        boolean isAfterEffect = (isIT) ? isAfterEffect(aktMemo.assignedEffect) : false;
        if (!isAfterEffect) doRowEffects(aktMemo);
        doVolumeColumnRowEffect(aktMemo);
        if (isAfterEffect) doRowEffects(aktMemo);
    }
}
