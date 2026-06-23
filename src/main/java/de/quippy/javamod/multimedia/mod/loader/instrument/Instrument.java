/*
 * @(#) Instrument.java
 *
 * Created on 19.06.2006 by Daniel Becker
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

package de.quippy.javamod.multimedia.mod.loader.instrument;

import de.quippy.javamod.multimedia.mod.ModConstants;
import de.quippy.javamod.multimedia.mod.midi.ModMidiMixer;


/**
 * @author Daniel Becker
 * @since 19.06.2006
 */
public class Instrument {

    public int[] sampleIndex;
    public int[] noteIndex;

    public String name;

    public String dosFileName;
    public int duplicateNoteCheck = -1;
    public int duplicateNoteAction = -1;
    public int NNA = -1;
    public int pitchPanSeparation = -1;
    public int pitchPanCenter = 0;
    public int globalVolume = 128;
    public boolean setPanning = false;
    public int defaultPanning = 128;
    public int randomVolumeVariation = -1;
    public int randomPanningVariation = -1;
    public int randomResonanceVariation = -1;
    public int randomCutOffVariation = -1;
    // Either 0x7F / 0 for off or both 0 (no change)
    public int initialFilterCutoff = 0;
    public int initialFilterResonance = 0;

    // The envelopes
    public Envelope volumeEnvelope = null;
    public Envelope panningEnvelope = null;
    public Envelope pitchEnvelope = null;

    public int volumeFadeOut = -1;

    // Midi and Plugin stuff
    /** MIDI Bank (1...16384). 0 = Don't send. */
    public int midiBank = 0;
    /** MIDI Program (1...128). 0 = Don't send. */
    public int midiProgram = 0;
    /** MIDI Channel (1...16). 0 = Don't send. 17 = Mapped (Send to tracker channel modulo 16). */
    public int midiChannel = 0;
    /** MIDI Pitch Wheel Depth and CMD_FINETUNE depth in semitones */
    public int pitchWheelDepth = 2;
    /** Plugin Number - we do not support MPT standard plugins yet */
    public int mixPlugIn = 0;
    /** XM: mute samples in channel (only midi device) */
    public boolean xm_muteComputer = false;
    /** true, if this instrument has valid midi data (you don't say!) */
    public boolean hasValidMidiData = false;
    /** XM: use Midi */
    public boolean xm_enableMidi = false;
    /** extended Instrument OpenModPlug PVEH */
    public int pluginVelocityHandling = ModMidiMixer.PLUGIN_VELOCITYHANDLING_CHANNEL;
    /** extended Instrument OpenModPlug PVOH */
    public int pluginVolumeHandling = ModMidiMixer.PLUGIN_VOLUMEHANDLING_IGNORE;

    // OMPT
    /** ys of volRamping up, -1 || 0 == use default */
    public int volRampUp = -1;
    /** resampling - we support -1: default: 0:none, 1: linear, 2: cubic, 3&>:Windowed FIR */
    public int resampling = -1;
    /** MPT seems to have supported the muting of instruments. Is not written anymore */
    public boolean mute = false;

    // MadTracker
    public int filterMode = ModConstants.FLTMODE_UNCHANGED;

    public int getSampleIndex(int noteIndex) {
        if (sampleIndex == null) return -1;
        return this.sampleIndex[noteIndex] - 1;
    }

    public int getNoteIndex(int noteIndex) {
        if (this.noteIndex == null) return noteIndex;
        return this.noteIndex[noteIndex];
    }

    public boolean hasValidMidiChannel() {
        return midiChannel >= 1 && midiChannel <= 18;
    }

    public boolean hasValidMidiBank() {
        return midiBank >= 1 && midiBank <= 16384;
    }

    public boolean hasValidMidiProgram() {
        return midiProgram >= 1 && midiProgram <= 128;
    }

    @Override
    public String toString() {
        return name;
    }
}
