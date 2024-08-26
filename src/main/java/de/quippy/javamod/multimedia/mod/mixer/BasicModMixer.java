/*
 * @(#) BasicModMixer.java
 * 
 * Created on 30.04.2006 by Daniel Becker
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

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import de.quippy.javamod.multimedia.mod.ModConstants;
import de.quippy.javamod.multimedia.mod.gui.ModUpdateListener;
import de.quippy.javamod.multimedia.mod.gui.ModUpdateListener.PatternPositionInformation;
import de.quippy.javamod.multimedia.mod.gui.ModUpdateListener.PeekInformation;
import de.quippy.javamod.multimedia.mod.gui.ModUpdateListener.StatusInformation;
import de.quippy.javamod.multimedia.mod.loader.Module;
import de.quippy.javamod.multimedia.mod.loader.instrument.Envelope;
import de.quippy.javamod.multimedia.mod.loader.instrument.Instrument;
import de.quippy.javamod.multimedia.mod.loader.instrument.Sample;
import de.quippy.javamod.multimedia.mod.loader.pattern.Pattern;
import de.quippy.javamod.multimedia.mod.loader.pattern.PatternElement;
import de.quippy.javamod.multimedia.mod.loader.pattern.PatternRow;

/**
 * @author Daniel Becker
 * @since 30.04.2006
 */
public abstract class BasicModMixer
{
	public class ChannelMemory
	{
		public int channelNumber;
		public boolean muted, muteWasITforced;
		public boolean isNNA;
		
		public PatternElement currentElement;
		
		// These currents* are a fresh copy from the current pattern. Only needed as interim memory for NNA, PatternDelay and NoteDelay
		public int currentAssignedNotePeriod, currentAssignedNoteIndex, currentAssignedEffekt, currentAssignedEffektParam, currentAssignedVolumeEffekt, currentAssignedVolumeEffektOp, currentAssignedInstrumentIndex;
		public Instrument currentAssignedInstrument;

		// The assigned* are those from the pattern, when ready to be copied and processed
		// for instance: if no instrument was set in pattern, current* / assigend* instrument is used (as the last instrument set)
		public int assignedNotePeriod, assignedNoteIndex, assignedEffekt, assignedEffektParam, assignedVolumeEffekt, assignedVolumeEffektOp, assignedInstrumentIndex;
		public Instrument assignedInstrument;
		public Sample assignedSample;
		
		// currentNoteperiod and these down here are then the values to handle with
		public int currentNotePeriod, currentFinetuneFrequency;
		public int currentNotePeriodSet; // used to save the current note period set with "setNewPlayerTuningFor"
		public int currentFineTune, currentTranspose;
		public Sample currentSample;
		public int currentTuning, currentTuningPos, currentSamplePos, interpolationMagic;
		public boolean isForwardDirection;
		public int volEnvTick, panEnvTick, pitchEnvTick;
		public int volXMEnvPos, panXMEnvPos;
		public boolean instrumentFinished, keyOff, noteCut, noteFade;
		public int tempNNAAction, tempVolEnv, tempPanEnv, tempPitchEnv;
		public int keyOffCounter;
		
		public int currentVolume, currentInstrumentVolume, channelVolume, fadeOutVolume, panning, currentInstrumentPanning;
		public int actVolumeLeft, actVolumeRight, actRampVolLeft, actRampVolRight, deltaVolLeft, deltaVolRight;
		public boolean doFastVolRamp;
		public int channelVolumSlideValue;
		
		public boolean doSurround;
		
		public int autoVibratoTablePos, autoVibratoAmplitude, autoVibratoSweep;

		// Midi Macros
		public int activeMidiMacro;
		public int lastZxxParam;
		
		// Resonance Filter
		public boolean filterOn;
		public int filterMode;
		public int resonance, cutOff;
		public int swingVolume, swingPanning, swingResonance, swingCutOff;
		public long filter_A0, filter_B0, filter_B1, filter_HP;
		public long filter_Y1, filter_Y2, filter_Y3, filter_Y4;
		
		// The effect memories
		public boolean glissando;
		public int arpeggioIndex, arpeggioNote[], arpeggioParam;
		public int portaStepUp, portaStepUpEnd, portaStepDown, portaStepDownEnd;
		public int finePortaUp, finePortaDown, finePortaUpEx, finePortaDownEx; 
		public int portaNoteStep, portaTargetNotePeriod, portamentoDirection_PT_FT;
		public int volumSlideValue, globalVolumSlideValue;
		public int XMFineVolSlideUp, XMFineVolSlideDown;
		public int panningSlideValue;
		public int vibratoTablePos, vibratoStep, vibratoAmplitude, vibratoType;
		public boolean vibratoOn, vibratoVolOn, vibratoNoRetrig;
		public int tremoloTablePos, tremoloStep, tremoloAmplitude, tremoloType;
		public boolean tremoloOn, tremoloNoRetrig;
		public int panbrelloTablePos, panbrelloStep, panbrelloAmplitude, panbrelloType, panbrelloRandomMemory; // panbrelloRandomMemory only for IT
		public boolean panbrelloOn, panbrelloNoRetrig;
		public int tremorOntime, tremorOfftime, tremorOntimeSet, tremorOfftimeSet;
		public boolean tremorWasActive;
		public int retrigCount, retrigMemo, retrigVolSlide;
		public boolean FT2AllowRetriggerQuirk;
		public int sampleOffset, highSampleOffset, prevSampleOffset;
		public int oldTempoParameter; // IT has Tempo memory
		public int S_Effect_Memory; // IT specific S00 Memory
		public int IT_EFG; // IT specific: linked memory
		public int EFxSpeed, EFxDelay, EFxOffset; // MOD specific: invertLoop (trash the sample)
		
		public int jumpLoopPatternRow, jumpLoopRepeatCount, jumpLoopITLastRow;

		public int noteDelayCount, noteCutCount;
		
		// only needed for display
		public long bigSampleLeft, bigSampleRight;

		public ChannelMemory()
		{
			channelNumber = -1;
			currentInstrumentPanning = panning = 128; // 0-256, this is therefore center
			actRampVolLeft = 
			actRampVolRight =
			deltaVolLeft =
			deltaVolRight =
			currentVolume =
			currentInstrumentVolume =
			channelVolume = 
			channelVolumSlideValue = 0;
			doFastVolRamp = false;
			fadeOutVolume = ModConstants.MAXFADEOUTVOLUME;
			
			muted = muteWasITforced = false;
			assignedNotePeriod = currentNotePeriod = currentNotePeriodSet =    
			currentFinetuneFrequency = currentFineTune = 0;
			currentTuning = currentTuningPos = currentSamplePos = interpolationMagic = 0;
			isForwardDirection = true; 
			instrumentFinished = true;
			keyOffCounter = -1;
			noteCut = keyOff = noteFade = false;
			tempNNAAction = tempVolEnv = tempPanEnv = tempPitchEnv = -1;
	
			volEnvTick = panEnvTick = pitchEnvTick = 0;
			volXMEnvPos = panXMEnvPos = 0;
			swingVolume = swingPanning = swingResonance = swingCutOff = 0;
			
			arpeggioIndex = noteDelayCount = noteCutCount = -1;
			arpeggioParam = 0;
			arpeggioNote = new int[3];
			portaStepUp = portaStepDown = portaStepUpEnd = portaStepDownEnd = 0; 
			finePortaDown = finePortaUp = 0; 
			finePortaDownEx = finePortaUpEx = 0; 
			portaNoteStep = portamentoDirection_PT_FT = volumSlideValue = globalVolumSlideValue = 0;
			XMFineVolSlideUp = XMFineVolSlideDown = 0;
			portaTargetNotePeriod = -1;
			vibratoTablePos = vibratoStep = vibratoAmplitude = vibratoType = 0; 
			vibratoOn = vibratoNoRetrig = false;
			autoVibratoTablePos = autoVibratoAmplitude = 0;
			tremoloTablePos = tremoloStep = tremoloAmplitude = tremoloType = 0; 
			tremoloOn = tremoloNoRetrig = false;
			panbrelloTablePos = panbrelloStep = panbrelloAmplitude = panbrelloType = panbrelloRandomMemory = 0; 
			panbrelloOn = panbrelloNoRetrig = false;
			glissando=false;
			tremorOntime = tremorOfftime = tremorOntimeSet = tremorOfftimeSet = 0;
			tremorWasActive = false;
			retrigCount = retrigMemo = retrigVolSlide = sampleOffset = highSampleOffset = prevSampleOffset = 0;
			oldTempoParameter = S_Effect_Memory = IT_EFG = 0;
			EFxSpeed = EFxDelay = EFxOffset = 0;
			modSpeedSet = 0;
			
			doSurround = false;
			
			activeMidiMacro = 0;
			
			filterOn = false;
			filterMode = 0;
			resonance = 0;
			lastZxxParam = cutOff = 0x7F;
			filter_A0 = filter_B0 = filter_B1 = filter_HP = 0;
			filter_Y1 = filter_Y2 = filter_Y3 = filter_Y4 = 0;
			
			jumpLoopPatternRow = jumpLoopRepeatCount = jumpLoopITLastRow = -1;
		}
		/**
		 * Every possible way to create a 1:1 copy of ChannelMemory for NNA
		 * failed (Clone, Serializable, Reflection)
		 * However, this method is now generated via reflection by
		 * "de.quippy.javamod.test.TableGenerator.java"
		 * @since 11.06.2020
		 * @param fromMe
		 */
		protected void setUpFrom(final ChannelMemory fromMe)
		{
			channelNumber = fromMe.channelNumber;
			muted = fromMe.muted;
			muteWasITforced = fromMe.muteWasITforced;
			assignedInstrumentIndex = fromMe.assignedInstrumentIndex;
			assignedInstrument = fromMe.assignedInstrument;
			currentNotePeriod = fromMe.currentNotePeriod;
			currentFinetuneFrequency = fromMe.currentFinetuneFrequency;
			currentNotePeriodSet = fromMe.currentNotePeriodSet;
			currentFineTune = fromMe.currentFineTune;
			currentTranspose = fromMe.currentTranspose;
			currentSample = fromMe.currentSample;
			currentTuning = fromMe.currentTuning;
			currentTuningPos = fromMe.currentTuningPos;
			currentSamplePos = fromMe.currentSamplePos;
			interpolationMagic = fromMe.interpolationMagic;
			isForwardDirection = fromMe.isForwardDirection;
			volEnvTick = fromMe.volEnvTick;
			panEnvTick = fromMe.panEnvTick;
			pitchEnvTick = fromMe.pitchEnvTick;
			instrumentFinished = fromMe.instrumentFinished;
			keyOff = fromMe.keyOff;
			noteCut = fromMe.noteCut;
			noteFade = fromMe.noteFade;
			tempNNAAction = fromMe.tempNNAAction;
			tempVolEnv = fromMe.tempVolEnv;
			tempPanEnv = fromMe.tempPanEnv;
			tempPitchEnv = fromMe.tempPitchEnv;
			keyOffCounter = fromMe.keyOffCounter;
			currentVolume = fromMe.currentVolume;
			currentInstrumentVolume = fromMe.currentInstrumentVolume;
			channelVolume = fromMe.channelVolume;
			fadeOutVolume = fromMe.fadeOutVolume;
			panning = fromMe.panning;
			currentInstrumentPanning = fromMe.currentInstrumentPanning;
			actVolumeLeft = fromMe.actVolumeLeft;
			actVolumeRight = fromMe.actVolumeRight;
			actRampVolLeft = fromMe.actRampVolLeft;
			actRampVolRight = fromMe.actRampVolRight;
			deltaVolLeft = fromMe.deltaVolLeft;
			deltaVolRight = fromMe.deltaVolRight;
			channelVolumSlideValue = fromMe.channelVolumSlideValue;
			doSurround = fromMe.doSurround;
			autoVibratoTablePos = fromMe.autoVibratoTablePos;
			autoVibratoAmplitude = fromMe.autoVibratoAmplitude;
			autoVibratoSweep = fromMe.autoVibratoSweep;
			activeMidiMacro = fromMe.activeMidiMacro;
			lastZxxParam = fromMe.lastZxxParam;
			filterOn = fromMe.filterOn;
			filterMode = fromMe.filterMode;
			resonance = fromMe.resonance;
			cutOff = fromMe.cutOff;
			swingVolume = fromMe.swingVolume;
			swingPanning = fromMe.swingPanning;
			swingResonance = fromMe.swingResonance;
			swingCutOff = fromMe.swingCutOff;
			filter_A0 = fromMe.filter_A0;
			filter_B0 = fromMe.filter_B0;
			filter_B1 = fromMe.filter_B1;
			filter_HP = fromMe.filter_HP;
			filter_Y1 = fromMe.filter_Y1;
			filter_Y2 = fromMe.filter_Y2;
			filter_Y3 = fromMe.filter_Y3;
			filter_Y4 = fromMe.filter_Y4;
		}
		/**
		 * @return some infos
		 * @see java.lang.Object#toString()
		 */
		public String toString()
		{
			return  "Channel: "+channelNumber+(isNNA?"(NNA) ":" ")+
					((currentElement!=null)?currentElement.toString():"NONE")+
					" Note: "+ModConstants.getNoteNameForIndex(assignedNoteIndex)+
					" Volume: "+currentInstrumentVolume+
					" Instrument: \""+((assignedInstrument!=null)?assignedInstrument.toString():"NONE")+
					"\" Sample: \"" + ((assignedSample!=null)?assignedSample.toString():"NONE")+'\"';
		}
	}

	protected ChannelMemory [] channelMemory;
	protected int maxNNAChannels; // configured value: the complete amount of mixing channels
	protected int maxChannels;
	
	protected Random swinger;
	
	// out sample buffer for two stereo samples
	private final long samples[] = new long[2];

	// Global FilterMode:
	protected boolean globalFilterMode;
	
	// Player specifics
	protected int frequencyTableType; // XM and IT Mods support this! Look at the constants

	protected int currentTempo, currentBPM, modSpeedSet;
	protected int globalTuning;
	protected int globalVolume, masterVolume, extraAttenuation;
	protected boolean useGlobalPreAmp, useSoftPanning;
	protected int currentTick, currentRow, currentArrangement, currentPatternIndex;
	protected int samplesPerTick, bufferDiff;
	
	protected int pingPongDiffIT;
	protected int leftOverSamplesPerTick; // the amount of data left to finish mixing a tick
	protected long samplesMixed; // the whole amount of samples mixed - as a time index for events

	protected int patternDelayCount, patternTicksDelayCount;
	protected Pattern currentPattern;
	
	protected int patternBreakRowIndex;		// Pattern break row index
	protected int patternBreakPatternIndex; // Pattern break arrangement index
	protected boolean patternBreakSet;		// do a patternBreak
	protected int patternJumpRowIndex;		// Jump Loop row index
	protected boolean patternJumpSet;		// do a jump
	
	protected final Module mod;
	protected int sampleRate;
	protected int doISP; // 0: no ISP; 1:linear; 2:Cubic Spline; 3:Windowed FIR
	protected int doNoLoops; // activates infinite loop recognition
	
	protected boolean modFinished;
	
	// FadeOut
	protected boolean doLoopingGlobalFadeout; // means we are in a loop condition and do a fade out now. 0: deactivated, 1: fade out, 2: just ignore loop
	protected int loopingFadeOutValue;
	
	// RAMP volume interweaving
	protected long [] interweaveBufferLeft;
	protected long [] interweaveBufferRight;

	// The listeners for update events - so far only one known off
	private ArrayList<ModUpdateListener> listeners;
	private boolean fireUpdates = false;
	
	// What type of Mod is it?
	protected boolean isFastTrackerFamily, isScreamTrackerFamily, isMOD, isXM, isSTM, isS3M, isIT, isModPlug;

	/**
	 * Constructor for BasicModMixer
	 */
	public BasicModMixer(final Module mod, final int sampleRate, final int doISP, final int doNoLoops, final int maxNNAChannels)
	{
		super();
		this.mod = mod;
		this.sampleRate = sampleRate;
		this.doISP = doISP;
		this.doNoLoops = doNoLoops;
		this.maxNNAChannels = maxNNAChannels;

		interweaveBufferLeft = new long [ModConstants.INTERWEAVE_LEN];
		interweaveBufferRight = new long [ModConstants.INTERWEAVE_LEN];
		
		listeners = new ArrayList<ModUpdateListener>();
		
		initializeMixer(false); // do not inform listeners, as there are no listeners registered yet
	}
	/**
	 * BE SHURE TO STOP PLAYBACK! Changing this during playback may (will!)
	 * cause crappy playback!
	 * @since 09.07.2006
	 * @param newSampleRate
	 */
	public void changeSampleRate(final int newSampleRate)
	{
		sampleRate = newSampleRate;
		calculateSamplesPerTick();
		calculateGlobalTuning();
		for (int c=0; c<maxChannels; c++) setNewPlayerTuningFor(channelMemory[c]);
	}
	/**
	 * Changes the interpolation routine. This can be done at any time
	 * @since 09.07.2006
	 * @param newISP
	 */
	public void changeISP(final int newISP)
	{
		this.doISP = newISP;
	}
	/**
	 * Changes the interpolation routine. This can be done at any time
	 * @since 09.07.2006
	 * @param newISP
	 */
	public void changeDoNoLoops(final int newDoNoLoops)
	{
		this.doNoLoops = newDoNoLoops;
	}
	/**
	 * BE SHURE TO STOP PLAYBACK! Changing this during playback may (will!)
	 * cause crappy playback!
	 * @since 23.06.2020
	 * @param newMaxNNAChannels
	 */
	public void changeMaxNNAChannels(final int newMaxNNAChannels)
	{
		maxNNAChannels = newMaxNNAChannels;
		final int nChannels = mod.getNChannels();
		int newMaxChannels = nChannels;
		if (isIT)
			newMaxChannels += maxNNAChannels;
		if (newMaxChannels!=maxChannels)
		{
			ChannelMemory [] newChannelMemory = new ChannelMemory[newMaxChannels];
			for (int c=0; c<newMaxChannels; c++)
			{
				if (c<maxChannels)
				{
					newChannelMemory[c] = channelMemory[c];
				}
				else
				{
					newChannelMemory[c] = new ChannelMemory();
					newChannelMemory[c].isNNA = true; // must be the default in this case...
				}
			}
			channelMemory = newChannelMemory;
			maxChannels = newMaxChannels;
		}
	}
	
	/**
	 * Set the borders of max and min periods
	 * @since 07.03.2024
	 * @param aktMemo
	 */
	protected abstract void setPeriodBorders(final ChannelMemory aktMemo);
	/**
	 * Do own inits
	 * Especially do the init of the panning depending on ModType
	 * @return
	 */
	protected abstract void initializeMixer(final int channel, final ChannelMemory aktMemo);
	
	/**
	 * Call this first!
	 */
	public void initializeMixer(final boolean rememberMuteStatus)
	{
		modFinished = false;
		
		// to be a bit faster, we do some pre-calculations
		calculateGlobalTuning();
		
		// get boolean values once for faster checks
		isFastTrackerFamily=(mod.getModType()&ModConstants.MODTYPE_FASTTRACKER)!=0;
		isScreamTrackerFamily=(mod.getModType()&ModConstants.MODTYPE_IMPULSETRACKER)!=0;
		isMOD=(mod.getModType()&ModConstants.MODTYPE_MOD)!=0;
		isXM=(mod.getModType()&ModConstants.MODTYPE_XM)!=0;
		isSTM=(mod.getModType()&ModConstants.MODTYPE_STM)!=0;
		isModPlug=(mod.getModType()&(ModConstants.MODTYPE_MPT | ModConstants.MODTYPE_OMPT))!=0;
		isS3M=(mod.getModType()&ModConstants.MODTYPE_S3M)!=0;
		isIT=(mod.getModType()&ModConstants.MODTYPE_IT)!=0;
		
		// OMPT specific - if a resampling was set in the mod file, set it.
		// for whatever this is good...
		if (mod.getResampling()>-1) doISP = mod.getResampling();
		
		if (isIT) pingPongDiffIT = 1; else pingPongDiffIT = 0;

		// get Mod specific values
		frequencyTableType = mod.getFrequencyTable();
		currentTempo = mod.getTempo();
		currentBPM = mod.getBPMSpeed();

		// Set to first pattern
		currentTick = currentArrangement = currentRow = 0;
		currentPatternIndex = mod.getArrangement()[currentArrangement];
		currentPattern = mod.getPatternContainer().getPattern(currentPatternIndex);
		
		patternDelayCount = patternTicksDelayCount = 
		patternJumpRowIndex = patternBreakRowIndex = patternBreakPatternIndex = -1;
		patternJumpSet = patternBreakSet = false;
		
		modSpeedSet = 0;
		bufferDiff = 0;
		calculateSamplesPerTick();
		leftOverSamplesPerTick = 0;
		samplesMixed = 0;

		globalVolume = mod.getBaseVolume();
		globalFilterMode = false; // IT default: every note resets filter to current values set - flattens the filter envelope
		swinger = new Random();

		if ((mod.getModType()&ModConstants.MODTYPE_MPT)!=0) // is it Legacy MPT?
		{
			// Do global Pre-Amp - with legacy ModPlug Tracker this was used...
			// legacy: that is MPT <=1.17RC2
			int channels = mod.getNChannels();
			if (channels<1) channels = 1; 
			else 
			if (channels>31) channels = 31;

			// (Open)MPT uses 0x100 as maxBaseVolume, so original 0x80 maxBaseVolume
			// of IT, which JavaMod uses, needs to be doubled
			int realMasterVolume = mod.getBaseVolume()<<1;
			if (realMasterVolume > 0x80)
			{
				//Attenuate global pre-amp depending on number of channels
				realMasterVolume = 0x80 + (((realMasterVolume - 0x80) * (channels + 4)) >> 4);
			}
			masterVolume = (realMasterVolume * mod.getMixingPreAmp())>>6;
			// no DSP automatic gain control (AGC) switch with JavaMod, so only PreAmp version:
			masterVolume = (masterVolume << 7) / ModConstants.PreAmpTable[channels>>1];

			extraAttenuation = 4; // set extraAttenuation
			useGlobalPreAmp = true; // with preAmp PreAmpShift is 7, otherwise 8
			useSoftPanning = false;
		}
		else
		if ((mod.getModType()&ModConstants.MODTYPE_OMPT)!=0) // Open Modplug Tracker?
		{
			masterVolume = mod.getMixingPreAmp();
			extraAttenuation = 0;
			useGlobalPreAmp = false;
			useSoftPanning = isIT; // IT: true, FT2: false
		}
		else // default ProTracker, FT2, s3m, ...
		{
			masterVolume = mod.getMixingPreAmp();
			extraAttenuation = 1;
			useGlobalPreAmp = false;
			useSoftPanning = false;
		}
		
		// Reset all rows played to false
		mod.resetLoopRecognition();

		// Reset FadeOut
		doLoopingGlobalFadeout = false;
		loopingFadeOutValue = ModConstants.MAXFADEOUTVOLUME;
		
		// initialize every used channel
		final int nChannels = mod.getNChannels();
		maxChannels = nChannels;
		if (isIT) maxChannels += maxNNAChannels;
		
		// This is only for seeking. We remember the mute status of channels
		// as this will get reseted when all channels get recreated
		boolean [] muteStatus = null;
		if (channelMemory!=null)
		{
			muteStatus = new boolean[maxChannels];
			for (int c=0; c<maxChannels; c++)
			{
				if (channelMemory[c]!=null) muteStatus[c] = channelMemory[c].muted;
			}
		}
		channelMemory = new ChannelMemory[maxChannels];
		
		for (int c=0; c<maxChannels; c++)
		{
			final ChannelMemory aktMemo = (channelMemory[c] = new ChannelMemory());
			if (c<nChannels)
			{
				aktMemo.isNNA = false;
				aktMemo.channelNumber = c;
				// initialize with global default panning and volume values (get overridden by effect or by instrument/sample settings)
				aktMemo.currentInstrumentPanning = aktMemo.panning = mod.getPanningValue(c);
				aktMemo.channelVolume = mod.getChannelVolume(c);
				initializeMixer(c, aktMemo); // additional Mod specific initializations
			}
			else
			{
				aktMemo.isNNA = true;
				aktMemo.channelNumber = -1;
				//aktMemo.instrumentFinished = true;
			}
		}
		// and reset the mute status again
		if (muteStatus!=null)
		{
			for (int c=0; c<maxChannels; c++)
			{
				if (channelMemory[c]!=null) channelMemory[c].muted = muteStatus[c];
			}
		}
		resetJumpPositionSet();
	}
	/**
	 * Does only a forward seek, so starts from the beginning
	 * @since 25.07.2020
	 */
	public long seek(final long milliseconds)
	{
		long fullLength = 0;
		final boolean fireUpdateStatus = getFireUpdates();
		try
		{
			setFireUpdates(false);
			initializeMixer(true);
			long currentMilliseconds = 0;
			final long stopAt = 60L*60L*sampleRate; // Just in case...
			boolean finished = false;
			while (fullLength<stopAt && currentMilliseconds<milliseconds && !finished)
			{
				fullLength += samplesPerTick;
				currentMilliseconds = fullLength * 1000L / (long)sampleRate;
				finished = doRowAndTickEvents();
			}
			// Silence all and everything to avoid clicks and arbitrary sounds...
			for (int c=0; c<maxChannels; c++)
			{
				final ChannelMemory aktMemo = channelMemory[c];
				aktMemo.actVolumeLeft = aktMemo.actVolumeRight = aktMemo.currentVolume = 
				aktMemo.actRampVolLeft = aktMemo.actRampVolRight = 0;
			}
		}
		finally
		{
			setFireUpdates(fireUpdateStatus);
		}
		return fullLength;
	}
	/**
	 * @since 25.07.2020
	 * @return
	 */
	public synchronized long getLengthInMilliseconds()
	{
		// do we need to measure it or do we already have a length?
		if (mod.getLengthInMilliseconds()==-1)
		{
			final boolean fireUpdateStatus = getFireUpdates();
			try
			{
				setFireUpdates(false);
				int oldDoNoLoops = doNoLoops;
				int oldSampleRate = sampleRate;

				changeDoNoLoops(ModConstants.PLAYER_LOOP_FADEOUT);
				sampleRate = 44100;
				initializeMixer(false);

				final long [] msTimeIndex = mod.getMsTimeIndex(); 
				msTimeIndex[0] = 0;
				int arrangementIndex = currentArrangement;

				long fullLength = 0;
				boolean finished = false;
				final long stopAt = 60L*60L*sampleRate;
				while (fullLength<stopAt && !finished)
				{
					fullLength += samplesPerTick;
					finished = doRowAndTickEvents();
					if (currentArrangement!=arrangementIndex && currentArrangement<msTimeIndex.length)
						msTimeIndex[arrangementIndex=currentArrangement] = fullLength * 1000L / sampleRate;
				}
				mod.setLengthInMilliseconds(fullLength * 1000L / sampleRate);
				
				// revert changes
				sampleRate = oldSampleRate;
				changeDoNoLoops(oldDoNoLoops);
				initializeMixer(false);
			}
			finally
			{
				setFireUpdates(fireUpdateStatus);
			}
		}
		return mod.getLengthInMilliseconds();
	}
	/**
	 * Will create a long representing current
	 * positions. Form is as follows:<br>
	 * 0x1234 5678 9ABC DEF0:<br>
	 *   1234: currentArrangement position (>>48)<br>
	 *   5678: current Pattern Number (>>32)<br>
	 *   9ABC: current Row (>>16)<br>
	 *   DEF0: current tick<br>
	 * @since 30.03.2010
	 * @return
	 */
	public long getCurrentPatternPosition()
	{
		return ((long)(currentArrangement&0xFFFF)<<48) | ((long)(currentPatternIndex&0xFFFF)<<32) | ((long)(currentRow&0xFFFF)<<16) | ((long)(currentTempo - currentTick)&0xFFFF);
	}
	/**
	 * Will return all channels, that are active for rendering
	 * Also silenced channels will be counted, as the playback is
	 * still processing the active instrument / sample
	 * @since 30.03.2010
	 * @return
	 */
	public int getCurrentUsedChannels()
	{
		int result = 0;
		for (int i=0; i<maxChannels; i++)
		{
			final ChannelMemory aktMemo = channelMemory[i]; 
			if (isChannelActive(aktMemo)) result++;
		}
		return result;
	}
	/**
	 * @since 11.11.2023
	 * @return true, if mod playback is finished
	 */
	public boolean getModFinished()
	{
		return modFinished;
	}
	/**
	 * @since 07.03.2024
	 * @return
	 */
	public Module getMod()
	{
		return mod;
	}
	/**
	 * Calculate the samples needed to fill a tick.
	 * Modified in 2024 to support MPT tempo modes (MODERN or ALTERNATIVE)
	 */
	protected void calculateSamplesPerTick()
	{
		switch (mod.getTempoMode())
		{
			case ModConstants.TEMPOMODE_MODERN:
				double accurateBuffer = (double)sampleRate * (60.0d / ((double)currentBPM * (double)currentTempo * (double)mod.getRowsPerBeat()));
				final double [] tempoSwing = currentPattern.getTempoSwing();
				if (tempoSwing!=null && tempoSwing.length>0)
				{
					final double swingFactor = tempoSwing[currentRow % tempoSwing.length];
					accurateBuffer = accurateBuffer * swingFactor / (double)ModConstants.TEMPOSWING_UNITY; 
				}
				samplesPerTick = (int)(accurateBuffer);
				bufferDiff += accurateBuffer - samplesPerTick;
				if (bufferDiff>=1)
				{
					samplesPerTick++;
					bufferDiff--;
				}
				else
				if (bufferDiff<=-1)
				{
					samplesPerTick--;
					bufferDiff++;
				}
				break;
			case ModConstants.TEMPOMODE_ALTERNATIVE:
				samplesPerTick = sampleRate / currentBPM;
				break;
			case ModConstants.TEMPOMODE_CLASSIC:
				// The classic formula is (2.5*sampleRate)/bpm. We can modify that to
				// fit into integers, by either
				// - (25*sampleRate)/(bpm*10) or
				// - (5*sampleRate)/(bpm*2) or
				// - ((sampleRate*2) + (sampleRate/2)) / bpm
				// Interestingly both implementations
				// - (((sampleRate*5)<<7) / bpm)>>8 and
				// - ((sampleRate<<1) + (sampleRate>>1)) / bpm
				// result in the exact same values - no higher precision
//				samplesPerTick = ((sampleRate<<1) + (sampleRate>>1)) / currentBPM;
				samplesPerTick = ((((sampleRate*5)<<7) / currentBPM)+((1<<(8-1))-1))>>8; // +0x7F for rounding up/down
				break;
		}
		if (samplesPerTick<=0) samplesPerTick = 1;
	}
	/**
	 * For faster tuning calculations, this is pre-calculated 
	 */
	protected void calculateGlobalTuning()
	{
		this.globalTuning = (int)((((((long)ModConstants.BASEPERIOD)<<ModConstants.PERIOD_SHIFT) * ((long)ModConstants.BASEFREQUENCY))<<ModConstants.SHIFT) / ((long)sampleRate));
	}
	/**
	 * Retrieves a period value (see ModConstants.noteValues) shifted by 4 (*16)
	 * XM_LINEAR_TABLE and XM_AMIGA_TABLE is for XM-Mods,
	 * AMIGA_TABLE is for ProTrackerMods only (XM_AMIGA_TABLE is about the same though)
	 * With Mods the AMIGA_TABLE, IT_AMIGA_TABLE and XM_AMIGA_TABLE result in 
	 * the approximate same values, but to be purely compatible and correct,
	 * we use the protracker finetune period tables!
	 * The IT_AMIGA_TABLE is for STM, S3M and IT...
	 * Be careful: if XM_* is used, we expect a noteIndex (0..119), no period!
	 * @param aktMemo
	 * @param period or noteIndex
	 * @return
	 * @since 28.06.2024 moved to the respective Mixers (ProTrackerMixher and ScreamTrackerMixer)
	 */
	protected int getFineTunePeriod(final ChannelMemory aktMemo, final int period)
	{
		// Period is not a noteindex - this will never happen, but I once used it with protracker mods
		return (int)((long)ModConstants.BASEFREQUENCY * (long)period / (long)aktMemo.currentFinetuneFrequency);
	}
	/**
	 * Calls getFineTunePeriod(ChannelMemory, int Period) with the actual Period assigned.
	 * All Effects changing the period need to call this
	 * @param aktMemo
	 * @return
	 */
	protected int getFineTunePeriod(final ChannelMemory aktMemo)
	{
		if ((frequencyTableType & (ModConstants.AMIGA_TABLE | ModConstants.XM_AMIGA_TABLE | ModConstants.XM_LINEAR_TABLE))!=0)
			return (aktMemo.assignedNoteIndex==0)?0:getFineTunePeriod(aktMemo, aktMemo.assignedNoteIndex + aktMemo.currentTranspose);
		else
		if ((frequencyTableType & (ModConstants.IT_LINEAR_TABLE | ModConstants.IT_AMIGA_TABLE | ModConstants.STM_S3M_TABLE))!=0) 
			return (aktMemo.assignedNoteIndex==0)?0:getFineTunePeriod(aktMemo, aktMemo.assignedNoteIndex);
		else
			return 0;
	}
	/**
	 * This Method now takes the current Period (e.g. 856<<ModConstants.PERIOD_SHIFT) and calculates 
	 * the playerTuning to be used. I.e. a value like 2, which means every second sample in the
	 * current instrument is to be played. A value of 0.5 means, every sample is played twice.
	 * As we use int-values, this again is shiftet.
	 * MAKE SHURE that newPeriod is already the "getFineTunePeriod" value.
	 * @param aktMemo
	 * @param newPeriod
	 * @since 28.06.2024 moved to the respective Mixers (ProTrackerMixher and ScreamTrackerMixer)
	 */
	protected void setNewPlayerTuningFor(final ChannelMemory aktMemo, final int newPeriod)
	{
		aktMemo.currentNotePeriodSet = newPeriod;
		
		if (newPeriod<=0)
		{
			aktMemo.currentTuning = 0;
			return;
		}
		
		final int clampedPeriod = (newPeriod>aktMemo.portaStepDownEnd)?aktMemo.portaStepDownEnd:(newPeriod<aktMemo.portaStepUpEnd)?aktMemo.portaStepUpEnd:newPeriod;
		aktMemo.currentTuning = globalTuning / clampedPeriod;
	}
	/**
	 * Set the current tuning for the player
	 * @param aktMemo
	 */
	protected void setNewPlayerTuningFor(final ChannelMemory aktMemo)
	{
		setNewPlayerTuningFor(aktMemo, aktMemo.currentNotePeriod);
		// save for IT Arpeggios. Must be done here, not above, as above
		// service is used when not changed permanently through currentNotePeriod!
		if (isIT) aktMemo.arpeggioNote[0] = aktMemo.currentNotePeriod;
	}
	/**
	 * Get the period of the nearest halftone
	 * @param period
	 * @return
	 */
	protected int getRoundedPeriod(final ChannelMemory aktMemo, final int period)
	{
		if (isMOD)
		{
			final int i = ModConstants.getNoteIndexForPeriod(period);
			if (i>0)
			{
				final int diff1 = ModConstants.noteValues[i-1] - period;
				final int diff2 = period - ModConstants.noteValues[i];
				if (diff1<diff2) return ModConstants.noteValues[i-1];
			}
			return ModConstants.noteValues[i];
		}
		else
		{
			for (int i=1; i<180; i++)
			{
				final int checkPeriod = getFineTunePeriod(aktMemo, i)>>ModConstants.PERIOD_SHIFT;
				if (checkPeriod>0 && checkPeriod<=period)
					return checkPeriod;
			}
			return 0;
		}
	}
	/**
	 * Because of special notes like KEY_OFF, NOTE_CUT, NOTE_FADE this is *not*
	 * !hasNoNote()
	 * @since 11.03.2024
	 * @param element
	 * @return true, if the current Element has a note
	 */
	protected boolean hasNewNote(final PatternElement element)
	{
		return element!=null && (element.getPeriod()>ModConstants.NO_NOTE || element.getNoteIndex()>ModConstants.NO_NOTE);
	}
	/**
	 * Because of special notes like KEY_OFF, NOTE_CUT, NOTE_FADE this is *not*
	 * !hasNewNote()
	 * @since 15.03.2024
	 * @param element
	 * @return true, if the current Element has no note
	 */
	protected boolean hasNoNote(final PatternElement element)
	{
		return element!=null && (element.getPeriod()==ModConstants.NO_NOTE && element.getNoteIndex()==ModConstants.NO_NOTE);
	}
	/**
	 * Simple 2-poles resonant filter 
	 * @since 31.03.2010
	 * @param aktMemo
	 * @param bReset
	 * @param flt_modifier
	 */
	protected void setupChannelFilter(final ChannelMemory aktMemo, final boolean reset, final int envModifier)
	{
		final PatternElement element = aktMemo.currentElement;
		// Z7F (plus resonance==0) disables the filter, if set next to a note - otherwise not.
		if (aktMemo.cutOff>=0x7F && aktMemo.resonance==0 && hasNewNote(element)) 
			aktMemo.filterOn = false;
		else
		{
			int cutOff = (aktMemo.cutOff & 0x7F) + aktMemo.swingCutOff;
			cutOff = (cutOff * (envModifier + 256)) >> 8;
			if (cutOff<0) cutOff=0; else if (cutOff>0xFF) cutOff=0xFF;
			int resonance = (aktMemo.resonance & 0x7F) + aktMemo.swingResonance;
			if (resonance<0) resonance=0; else if (resonance>0xFF) resonance=0xFF;

			final double fac = (((mod.getSongFlags()&ModConstants.SONG_EXFILTERRANGE)!=0)? (128.0d / (20.0d * 256.0d)) : (128.0d / (24.0d * 256.0d)));
			double frequency = 110.0d * Math.pow(2.0d, (double)cutOff * fac + 0.25d);
			if (frequency < 120d) frequency = 120d;
			if (frequency > 20000d) frequency = 20000d;
			if (frequency > sampleRate>>1) frequency = sampleRate>>1;
			frequency *= 2.0d * Math.PI;
			
			final double dmpFac = ModConstants.ResonanceTable[resonance];
			double e, d;
			if ((mod.getSongFlags()&ModConstants.SONG_EXFILTERRANGE)==0)
			{
				final double r = ((double)sampleRate) / frequency;
				d = dmpFac * r + dmpFac - 1.0d;
				e = r * r;
			}
			else
			{
		        final double d_dmpFac = 2.0d * dmpFac;
				final double r = frequency / ((double)(sampleRate));
		        d = (1.0d - d_dmpFac) * r;
		        if (d > 2.0d) d = 2.0d;
		        d = (d_dmpFac - d) / r;
		        e = 1.0d / (r * r);
			}

			final double fg = 1.0d / (1.0d + d + e);
			final double fb0 = (d + e + e) / (1.0d + d + e);
			final double fb1 = -e / (1.0d + d + e);

			switch(aktMemo.filterMode)
			{
				case ModConstants.FLTMODE_HIGHPASS:
					aktMemo.filter_A0 = (long)((1.0d - fg) * ModConstants.FILTER_PRECISION);
					aktMemo.filter_B0 = (long)(fb0 * ModConstants.FILTER_PRECISION);
					aktMemo.filter_B1 = (long)(fb1 * ModConstants.FILTER_PRECISION);
					aktMemo.filter_HP = -1;
					break;
				case ModConstants.FLTMODE_BANDPASS:
				case ModConstants.FLTMODE_LOWPASS:
				default:
					aktMemo.filter_A0 = (long)(fg * ModConstants.FILTER_PRECISION);
					aktMemo.filter_B0 = (long)(fb0 * ModConstants.FILTER_PRECISION);
					aktMemo.filter_B1 = (long)(fb1 * ModConstants.FILTER_PRECISION);
					aktMemo.filter_HP = 0;
					if (aktMemo.filter_A0 == 0) aktMemo.filter_A0 = 1; // Prevent silence at low filter cutoff and very high sampling rate
					break;
			}

			if (reset) aktMemo.filter_Y1 = aktMemo.filter_Y2 = aktMemo.filter_Y3 = aktMemo.filter_Y4 = 0;
			
			aktMemo.filterOn = true;
		}
	}
	/**
	 * @since 05.07.2020
	 * @param buffer
	 */
	private void doResonance(final ChannelMemory aktMemo, final long buffer[])
	{
		long sampleAmp = buffer[0]<<ModConstants.FILTER_PREAMP_BITS; // with preAmp
		long fy = ((sampleAmp * aktMemo.filter_A0) + (aktMemo.filter_Y1 * aktMemo.filter_B0) + (aktMemo.filter_Y2 * aktMemo.filter_B1) + ModConstants.HALF_FILTER_PRECISION) >> ModConstants.FILTER_SHIFT_BITS;
		aktMemo.filter_Y2 = aktMemo.filter_Y1;
		aktMemo.filter_Y1 = fy - (sampleAmp & aktMemo.filter_HP);
		if (aktMemo.filter_Y1 < ModConstants.FILTER_CLIP_MIN) aktMemo.filter_Y1 = ModConstants.FILTER_CLIP_MIN;
		else if (aktMemo.filter_Y1 > ModConstants.FILTER_CLIP_MAX) aktMemo.filter_Y1 = ModConstants.FILTER_CLIP_MAX;
		buffer[0] = (fy + (1<<(ModConstants.FILTER_PREAMP_BITS-1))) >> ModConstants.FILTER_PREAMP_BITS;

		sampleAmp = buffer[1]<<ModConstants.FILTER_PREAMP_BITS; // with preAmp
		fy = ((sampleAmp * aktMemo.filter_A0) + (aktMemo.filter_Y3 * aktMemo.filter_B0) + (aktMemo.filter_Y4 * aktMemo.filter_B1) + ModConstants.HALF_FILTER_PRECISION) >> ModConstants.FILTER_SHIFT_BITS;
		aktMemo.filter_Y4 = aktMemo.filter_Y3;
		aktMemo.filter_Y3 = fy - (sampleAmp & aktMemo.filter_HP);
		if (aktMemo.filter_Y3 < ModConstants.FILTER_CLIP_MIN) aktMemo.filter_Y3 = ModConstants.FILTER_CLIP_MIN;
		else if (aktMemo.filter_Y3 > ModConstants.FILTER_CLIP_MAX) aktMemo.filter_Y3 = ModConstants.FILTER_CLIP_MAX;
		buffer[1] = (fy + (1<<(ModConstants.FILTER_PREAMP_BITS-1))) >> ModConstants.FILTER_PREAMP_BITS;
	}
	/**
	 * Do the effects of a row (tick==0). This is mostly the setting of effects
	 * @param aktMemo
	 */
	protected abstract void doRowEffects(final ChannelMemory aktMemo);
	/**
	 * Used to process the volume column (tick==0)
	 * @param aktMemo
	 */
	protected abstract void doVolumeColumnRowEffekt(final ChannelMemory aktMemo);
	/**
	 * call doRowEffects and doVolumeColumnRowEffekt in correct order
	 * @since 31.01.2024
	 * @param aktMemo
	 */
	protected abstract void processEffekts(final ChannelMemory aktMemo);
	/**
	 * Do the Effects during Ticks (tick!=0)
	 * @param aktMemo
	 */
	protected abstract void doTickEffekts(final ChannelMemory aktMemo);
	/**
	 * do the volume column tick effects (tick!=0)
	 * @param aktMemo
	 */
	protected abstract void doVolumeColumnTickEffekt(final ChannelMemory aktMemo);
	/**
	 * call doTickEffekts and doVolumeColumnTickEffekt in correct order
	 * @since 31.01.2024
	 * @param aktMemo
	 */
	protected abstract void processTickEffekts(final ChannelMemory aktMemo);
	/**
	 * Do the autovibrato
	 * @param aktMemo
	 * @param currentSample
	 * @param currentPeriod
	 * @return currentPeriod after alternation
	 */
	protected abstract void doAutoVibratoEffekt(final ChannelMemory aktMemo, final Sample currentSample, final int currentPeriod);
	/**
	 * Returns true, if the Effekt and EffektOp indicate a NoteDelayEffekt
	 * @param effekt
	 * @param effektOp
	 * @return
	 */
	protected abstract boolean isNoteDelayEffekt(final int effekt, final int effektParam);
	/**
	 * Return true, if the Effekt and EddektOp indicate a patternFramesDelayEffejt
	 * @param effekt
	 * @param assignedEffektParam
	 * @return 
	 */
	protected abstract boolean isPatternFramesDelayEffekt(final int effekt, final int effektParam);
	/**
	 * Returns true, if the Effekt and EffektOp indicate a PortaToNoteEffekt
	 * @param effekt
	 * @param effektOp
	 * @return
	 */
	protected abstract boolean isPortaToNoteEffekt(final int effekt, final int effektParam, final int volEffekt, final int volEffektParam, final int notePeriod);
	/**
	 * Return true, if the effekt and effektop indicate the sample offset effekt
	 * @since 19.06.2006
	 * @param effekt
	 * @param assignedEffektParam
	 * @return
	 */
	protected abstract boolean isSampleOffsetEffekt(final int effekt);
	/**
	 * Returns true, if the Effekt and EffektOp indicate a Note Off effect
	 * @param effekt
	 * @param effektOp
	 * @return
	 */
	protected abstract boolean isKeyOffEffekt(final int effekt, final int effektParam);
	/**
	 * Returns true, if an NNA-Effekt is set. Than, no default instrument NNA
	 * should be processed.
	 * @since 11.06.2020
	 * @param aktMemo
	 * @return
	 */
	protected abstract boolean isNNAEffekt(final int effekt, final int effektParam);
	/**
	 * if assignedEffektParam is 0 an effect memory will be returned - if any
	 * Otherwise will return assignedEffektParam
	 * This is basically for S00 IT Memory
	 * @since 28.06.2020
	 * @param effekt
	 * @param assignedEffektParam
	 * @return
	 */
	protected abstract int getEffektOpMemory(final ChannelMemory aktMemo, final int effekt, final int effektParam);
	/**
	 * The parameter extension works differently to other commands. It is evaluated
	 * via a look ahead and not while coming across it.
	 * This effect is an OMPT special and normally wouldn't be supported here.<br>
	 * However, OMPT allows this effect with IT and XM mods and saves them, if not in compatibility mode,
	 * so to support OMTP saved ITs and XMs we need to support this one.
	 * @since 17.01.2024
	 * @param aktMemo
	 * @return
	 */
	protected abstract int calculateExtendedValue(final ChannelMemory aktMemo, final AtomicInteger extendedRowsUsed);
	/**
	 * Set the new note period / frequency and instrument
	 * This moved to the respective player classes to not mix up all the different
	 * player variations between ProTracker, FastTracker, ScreamTracker, ImpulseTracker
	 * @since 11.06.2006
	 * @param aktMemo
	 */
	protected abstract void setNewInstrumentAndPeriod(final ChannelMemory aktMemo);
	/**
	 * @since 19.06.2020
	 * @param aktMemo
	 */
	protected void initNoteFade(final ChannelMemory aktMemo)
	{
		// do not reactivate a dead channel or reactivate a
		// running noteFade
		if (!aktMemo.noteFade && isChannelActive(aktMemo))
		{
			aktMemo.fadeOutVolume = ModConstants.MAXFADEOUTVOLUME;
			aktMemo.noteFade = true;
		}
	}
	/**
	 * The size of volume-ramping we intend to use
	 */
	private void calculateVolRampLen(final ChannelMemory aktMemo)
	{
		final int targetVolLeft = aktMemo.actVolumeLeft;
		final int targetVolRight = aktMemo.actVolumeRight;

		if (targetVolLeft!=aktMemo.actRampVolLeft || targetVolRight!=aktMemo.actRampVolRight)
		{
			final boolean rampUp = targetVolLeft>aktMemo.actRampVolLeft || targetVolRight>aktMemo.actRampVolRight;

			// FT2 XMs have a default smooth VolRamp of 5ms for fastVolRamp and one tick, if not 
			int rampLengthYS = (isXM)?5000:(rampUp)?ModConstants.VOLRAMPLEN_UP_YS:ModConstants.VOLRAMPLEN_DOWN_YS;
			final int defaultRampLen = (isXM && !aktMemo.doFastVolRamp)?samplesPerTick:(int)((long)sampleRate * (long)rampLengthYS / 1000000L);
			
			// Override default with settings in instruments, if any (only for ramp up!)
			boolean useCustom = false;
			if (rampUp && aktMemo.currentAssignedInstrument!=null && aktMemo.currentAssignedInstrument.volRampUp>0)
			{
				rampLengthYS = aktMemo.currentAssignedInstrument.volRampUp;
				useCustom = (rampLengthYS>0);
			}
			
			int volRampLen = defaultRampLen;
			// now calculate the ramp length in samples
			if (useCustom)
			{
				// MPT is missing a zero (100000) here since 2005(!), which I consider an error!
				// But as MTP is the only tracker using this feature, we do it like they do...
				volRampLen = (int)((long)sampleRate * (long)rampLengthYS / 100000L); //normally 1000000 for yS!
				if (volRampLen < 1) volRampLen = 1; // minimum of 1 samples
			}
			else
			if (!isXM && 
				(targetVolLeft>0 || targetVolRight>0) && 
				(aktMemo.actRampVolLeft>0 || aktMemo.actRampVolRight>0) &&
				!aktMemo.doFastVolRamp)
			{
				// OMPTs Extra-smooth ramping uses one tick for ramping
				volRampLen = samplesPerTick;
				if (volRampLen<defaultRampLen) volRampLen = defaultRampLen;
				else
				if (volRampLen>ModConstants.VOLRAMPLEN) volRampLen = ModConstants.VOLRAMPLEN; 
			}
			aktMemo.doFastVolRamp = false;

			// now set the volume steps to use
			if (targetVolLeft!=aktMemo.actRampVolLeft)
			{
				aktMemo.deltaVolLeft = (targetVolLeft - aktMemo.actRampVolLeft) / volRampLen;
				if (aktMemo.deltaVolLeft==0) aktMemo.actRampVolLeft=targetVolLeft;
			}
			else
				aktMemo.deltaVolLeft = 0;
			if (targetVolRight!=aktMemo.actRampVolRight)
			{
				aktMemo.deltaVolRight = (targetVolRight - aktMemo.actRampVolRight) / volRampLen;
				if (aktMemo.deltaVolRight==0) aktMemo.actRampVolRight=targetVolRight;
			}
			else
				aktMemo.deltaVolRight = 0;
		}
	}
	/**
	 * Processes the Envelopes
	 * This function now sets the volume - always!!
	 * @since 19.06.2006
	 * @param aktMemo
	 */
	protected void processEnvelopes(ChannelMemory aktMemo)
	{
		int currentVolume = aktMemo.currentVolume<<ModConstants.VOLUMESHIFT; // typically it's the sample volume or a volume set 0..64
		int currentPanning = aktMemo.panning;
		int currentPeriod = aktMemo.currentNotePeriodSet;
		
		// The adjustments on the periods will change currentNotePeriodSet when setting via "setNewPlayerTuningFor(..)"
		// That's bad in envelopes, because we want to "add on" here
		// and not on top of our self over and over again
		final int resetPeriodAfterEnvelopes = currentPeriod;

		final Sample sample = aktMemo.currentSample;
		int insVolume = (sample!=null)?sample.globalVolume<<1:ModConstants.MAXGLOBALVOLUME;	// max: 64, but make it equal to instrument volume (0..128)
		Envelope volumeEnv = null;
		Envelope panningEnv = null;
		final Instrument currentInstrument = aktMemo.assignedInstrument;
		if (currentInstrument!=null)
		{
			insVolume = (insVolume * currentInstrument.globalVolume) >> 7; // combine sample and instrument volume
		
			volumeEnv = currentInstrument.volumeEnvelope;
			if (volumeEnv!=null)
			{
				final boolean volEnvOn = (aktMemo.tempVolEnv!=-1)?aktMemo.tempVolEnv==1:volumeEnv.on;
				if (volEnvOn)
				{
					final long volPos = volumeEnv.updatePosition(aktMemo, aktMemo.volEnvTick, aktMemo.volXMEnvPos, true);
					aktMemo.volEnvTick = (int)(volPos&0xFFFFFFFF);
					aktMemo.volXMEnvPos = (int)(volPos>>32);
					int newVol = volumeEnv.getValueForPosition(aktMemo.volEnvTick, aktMemo.volXMEnvPos); // 0..512
					currentVolume = (currentVolume * newVol) >> 9;
				}
			}

			// set the panning envelope
			panningEnv = currentInstrument.panningEnvelope;
			if (panningEnv!=null)
			{
				final boolean panEnvOn = (aktMemo.tempPanEnv!=-1)?aktMemo.tempPanEnv==1:panningEnv.on;
				if (panEnvOn)
				{
					final long panPos = panningEnv.updatePosition(aktMemo, aktMemo.panEnvTick, aktMemo.panXMEnvPos, false);
					aktMemo.panEnvTick = (int)(panPos&0xFFFFFFFF);
					aktMemo.panXMEnvPos = (int)(panPos>>32);
					final int newPanValue = panningEnv.getValueForPosition(aktMemo.panEnvTick, aktMemo.panXMEnvPos) - 256; // result -256..256
					currentPanning += (newPanValue * ((currentPanning >= 128)?(256 - currentPanning):currentPanning)) >> 8;
				}
			}

			// Pitch / Pan separation
			// That is the "piano" effekt: lower keys to the left, higher keys to the right
			// arranged around a center note, that is supposed to be in the middle
			if (currentInstrument.pitchPanSeparation>0 && currentPeriod>0)
			{
				currentPanning += ((currentPeriod - ((currentInstrument.pitchPanCenter + 1)<<ModConstants.PERIOD_SHIFT)) * currentInstrument.pitchPanSeparation) >> 7; // / 8 + >>ModConstants.PERIOD_SHIFT for note period
			}
			
			final Envelope pitchEnv = currentInstrument.pitchEnvelope;
			if (pitchEnv!=null)
			{
				final boolean pitchEnvOn = (aktMemo.tempPitchEnv!=-1)?aktMemo.tempPitchEnv==1:pitchEnv.on;
				if (pitchEnvOn) // only IT...
				{
					aktMemo.pitchEnvTick = (int)(pitchEnv.updatePosition(aktMemo, aktMemo.pitchEnvTick, 0, false)&0xFFFFFFFF);
					int pitchValue = pitchEnv.getValueForPosition(aktMemo.pitchEnvTick, 0) - 256; // result -256..256
					if (pitchEnv.filter)
						setupChannelFilter(aktMemo, !aktMemo.filterOn, pitchValue);
					else
					{
						long newPitch = 0;
						if (pitchValue < 0)
						{
							pitchValue = -pitchValue;
							if (pitchValue > 255) pitchValue = 255;
							newPitch = (long)ModConstants.LinearSlideDownTable[pitchValue];
						} 
						else
						{
							if (pitchValue > 255) pitchValue = 255;
							newPitch = (long)ModConstants.LinearSlideUpTable[pitchValue];
						}
						currentPeriod = (int)((((long)currentPeriod)*newPitch)>>16);
					}
					setNewPlayerTuningFor(aktMemo, currentPeriod);
				}
			}
		}

		if (aktMemo.keyOff) initNoteFade(aktMemo);

		// Do the note fade
		// With XMs, a note fade without an instrument results in a note off, which is already done in doKeyOff()
		if (aktMemo.noteFade)
		{
			if (currentInstrument!=null/* && currentInstrument.volumeFadeOut>0*/)
			{
				aktMemo.fadeOutVolume -= (currentInstrument.volumeFadeOut<<1);
				if (aktMemo.fadeOutVolume<0) aktMemo.fadeOutVolume = 0;
				currentVolume = (currentVolume * aktMemo.fadeOutVolume) >> ModConstants.MAXFADEOUTVOLSHIFT;
			}
			else
				aktMemo.noteFade = false; // no instrument, no fade out - stop this calculation...
			
			// With IT a finished noteFade also sets the instrument as finished
			if (isIT && aktMemo.fadeOutVolume<=0 && isChannelActive(aktMemo))
			{
				aktMemo.instrumentFinished = true;
				if (aktMemo.isNNA) aktMemo.channelNumber = -1;
			}
		}

		// VolSwing - only if not silent
		if (currentVolume>0) currentVolume += aktMemo.swingVolume<<ModConstants.VOLUMESHIFT;
		 // Fade out initiated by recognized endless loop
		currentVolume = (currentVolume * loopingFadeOutValue) >> ModConstants.MAXFADEOUTVOLSHIFT;
		// Global Volumes
		currentVolume = (int)((((long)currentVolume * (long)globalVolume * (long)insVolume * (long)aktMemo.channelVolume) + (1<<(ModConstants.VOLUMESHIFT-1)) ) >> (7+7+6));
		// now for MasterVolume - which is SamplePreAmp, changed because of legacy MPT:
		currentVolume = (currentVolume * masterVolume) >> ((useGlobalPreAmp)?(ModConstants.PREAMP_SHIFT - 1):ModConstants.PREAMP_SHIFT);

		// Clipping Volume
		if (currentVolume>ModConstants.MAXCHANNELVOLUME) currentVolume=ModConstants.MAXCHANNELVOLUME;
		else
		if (currentVolume<ModConstants.MINCHANNELVOLUME) currentVolume=ModConstants.MINCHANNELVOLUME;

		currentPanning += aktMemo.swingPanning; // Random value -128..+128
		if (currentPanning<0) currentPanning=0;
		else
		if (currentPanning>256) currentPanning=256;
		
		int panSep = mod.getPanningSeparation();
		if (panSep<128) // skip calculation if not needed...
		{
			currentPanning -= 128;
			currentPanning = (currentPanning * panSep)>>7;
			currentPanning += 128;
		}
		
		// IT Compatibility: Ensure that there is no pan swing, panbrello, panning envelopes, etc. applied on surround channels.
		if (isIT && aktMemo.doSurround) currentPanning = 128;
		
		// save current target volume set
		// Update: actRampVol* is the current channel volume mixed. If the target volume was not
		// reached in one tick (what should not happen), we need to "ramp" from there.
//		aktMemo.actRampVolLeft = aktMemo.actVolumeLeft;
//		aktMemo.actRampVolRight = aktMemo.actVolumeRight;
		
		// calculate new channel volume depending on currentVolume and panning
		if (currentInstrument!=null && currentInstrument.mute) // maybe this is a way to implement MPTs mute setting
		{
			aktMemo.actVolumeLeft = aktMemo.actVolumeRight = 0;
		}
		else
		if ((mod.getSongFlags()&ModConstants.SONG_ISSTEREO)==0)
		{
			aktMemo.actVolumeLeft = aktMemo.actVolumeRight = currentVolume<<ModConstants.VOLRAMPLEN_FRAC;
		}
		else
		{
			if (isXM)
			{
				// From OpenMPT the following helpful hint:
				// FT2 uses square root panning. There is a 256-entry LUT for this,
				// but FT2's internal panning ranges from 0 to 255 only, meaning that
				// you can never truly achieve 100% right panning in FT2, only 100% left.
				if (currentPanning>255) currentPanning = 255;
				aktMemo.actVolumeLeft  = (currentVolume * ModConstants.XMPanningTable[256-currentPanning])>>16;
				aktMemo.actVolumeRight = (currentVolume * ModConstants.XMPanningTable[    currentPanning])>>16;
			}
			else
			if (useSoftPanning) // OpenModPlug has this.
			{
				if (currentPanning<128)
				{
					aktMemo.actVolumeLeft  = (currentVolume *            128)>>8; 
					aktMemo.actVolumeRight = (currentVolume * currentPanning)>>8; // max:256
				}
				else
				{
					aktMemo.actVolumeLeft  = (currentVolume * (256 - currentPanning))>>8; 
					aktMemo.actVolumeRight = (currentVolume *                    128)>>8; // max:256
				}
			}
			else
			{
				aktMemo.actVolumeLeft  = (currentVolume * (256 - currentPanning))>>8; 
				aktMemo.actVolumeRight = (currentVolume * (      currentPanning))>>8; // max:256
			}
			aktMemo.actVolumeLeft <<=ModConstants.VOLRAMPLEN_FRAC;
			aktMemo.actVolumeRight<<=ModConstants.VOLRAMPLEN_FRAC;
		}
		
		if (extraAttenuation>0) // for legacy MPT
		{
			aktMemo.actVolumeLeft >>= extraAttenuation;
			aktMemo.actVolumeRight>>= extraAttenuation;
		}
		
		// Surround on two channels (Dolby Pro Logic: make left&right out of phase)
		if (aktMemo.doSurround) aktMemo.actVolumeRight = -aktMemo.actVolumeRight; 

		// AutoVibrato
		if (aktMemo.currentSample!=null && aktMemo.currentSample.vibratoDepth>0 && currentPeriod>0)
			doAutoVibratoEffekt(aktMemo, aktMemo.currentSample, currentPeriod);
		
		// now for ramping to target volume
		calculateVolRampLen(aktMemo);

		// Reset this. That way, envelope period changes are only temporary 
		// addons but considers temporarily set vibrato and arpeggio effects
		aktMemo.currentNotePeriodSet = currentPeriod = resetPeriodAfterEnvelopes;
	}
	/**
	 * Central Service called from ScreamTracker and ProTracker Mixers
	 * for the Panning Set effects
	 * @param aktMemo
	 * @param param
	 * @param bits
	 */
	protected void doPanning(final ChannelMemory aktMemo, int param, final ModConstants.PanBits bits)
	{
		if (isMOD) return; // No panning set effect with ProTrackers - DMP played MODs with s3m effect logic. We don't do that!
		
		aktMemo.doSurround = false;
		if (bits == ModConstants.PanBits.Pan4Bit) // 0..15
		{
			if (param>15) param=15;
			if (isXM && !isModPlug)
				aktMemo.currentInstrumentPanning = aktMemo.panning = (param&0xF)<<4;
			else
				aktMemo.currentInstrumentPanning = aktMemo.panning = (((param&0xF)<<8) + 8 ) / 15;
		}
		else
		if (bits == ModConstants.PanBits.Pan6Bit) // 0..64
		{
			if (param>64) param=64;
			aktMemo.currentInstrumentPanning = aktMemo.panning = (param&0x7F)<<2;
		}
		else
		if (isS3M)
		{
			// This is special operation for S3M
			// ModConstants.PanBits.Pan8Bit now // 0..128
			if (param <= 0x80) // 7 Bit plus surround
			{
				aktMemo.currentInstrumentPanning = aktMemo.panning = param<<1;
			}
			else
			if (param==0xA4) // magic!
			{
				aktMemo.doSurround = true;
				aktMemo.currentInstrumentPanning = aktMemo.panning = 0x80;
			}
		}
		else
		{
			aktMemo.currentInstrumentPanning = aktMemo.panning = param&0xFF;
		}
		aktMemo.swingPanning = 0;
		aktMemo.doFastVolRamp = true; // panning should take place immediately - do not make it soft!
	}
	/**
	 * @since 17.01.2022
	 * @param aktMemo
	 * @param currentValue
	 * @param param
	 * @return
	 */
	protected float calculateSmoothParamChange(final ChannelMemory aktMemo, final float currentValue, final float param)
	{
		// currentTick is counted down from currentTempo (aka currentTicksPerRow) - so it is automatically "ticks left"
		if (currentTick > 1) // currentTick == 1 results in value of param - no need for calculation
			return currentValue + ((param - currentValue) / (float)currentTick);
		else
			return param;
	}
	/**
	 * @since 16.06.2020
	 * @param aktMemo
	 * @param isSmoothMidi
	 * @param midiMacro
	 * @param param
	 */
	protected void processMIDIMacro(final ChannelMemory aktMemo, final boolean isSmoothMidi, final String midiMacro, final int param)
	{
		if (midiMacro==null) return;
		final char[] midiMacroArray = midiMacro.toCharArray();
		if (midiMacroArray.length==0) return;
		
		int macroCommand =	((midiMacroArray[0]&0xFF)<<16) |
	    					((midiMacroArray[1]&0xFF)<<24) |
	    					((midiMacroArray[2]&0xFF)    ) |
	    					((midiMacroArray[3]&0xFF)<< 8);
		macroCommand &= 0x7F5F7F5F;
		
		if (macroCommand == 0x30463046) // internal code
		{
			int internalCode = -256;
			// Java supports Character.digit(char, radix) - but this will be slightly faster...
			// It's a bit of a risk because of unicode characters...
			//internalCode = (Character.digit(midiMacroArray[4], 16)<<4) | Character.digit(midiMacroArray[5], 16);
			if ((midiMacroArray[4] >= '0') && (midiMacroArray[4] <= '9')) internalCode  = (midiMacroArray[4] - '0') << 4;
			else
			if ((midiMacroArray[4] >= 'A') && (midiMacroArray[4] <= 'F')) internalCode  = (midiMacroArray[4] - 'A' + 0x0A) << 4;
			if ((midiMacroArray[5] >= '0') && (midiMacroArray[5] <= '9')) internalCode += (midiMacroArray[5] - '0');
			else
			if ((midiMacroArray[5] >= 'A') && (midiMacroArray[5] <= 'F')) internalCode += (midiMacroArray[5] - 'A' + 0x0A);
			
			if (internalCode >= 0)
			{
				char cData1 = midiMacroArray[6];
				int macroParam = 0;
				if ((cData1 == 'z') || (cData1 == 'Z'))
				{
					macroParam = param&0x7F;
					if (isSmoothMidi && aktMemo.lastZxxParam<0x80)
					{
						macroParam = (int)calculateSmoothParamChange(aktMemo, aktMemo.lastZxxParam, macroParam);
					}
					aktMemo.lastZxxParam = macroParam;
				}
				else
				{
					char cData2 = midiMacroArray[7];
					//macroParam = (Character.digit(cData1, 16)<<4) | Character.digit(cData2, 16);
					if ((cData1 >= '0') && (cData1 <= '9')) macroParam += (cData1 - '0') << 4; else
					if ((cData1 >= 'A') && (cData1 <= 'F')) macroParam += (cData1 - 'A' + 0x0A) << 4;
					if ((cData2 >= '0') && (cData2 <= '9')) macroParam += (cData2 - '0'); else
					if ((cData2 >= 'A') && (cData2 <= 'F')) macroParam += (cData2 - 'A' + 0x0A);
				}
				switch(internalCode) 
				{
					case 0x00: // F0.F0.00.xx: Set CutOff
						if (macroParam < 0x80) aktMemo.cutOff = macroParam;
						setupChannelFilter(aktMemo, !aktMemo.filterOn, 256);
						break;
					case 0x01: // F0.F0.01.xx: Set Resonance
						if (macroParam < 0x80) aktMemo.resonance = macroParam;
						setupChannelFilter(aktMemo, !aktMemo.filterOn, 256);
						break;
					case 0x02: // F0.F0.02.xx: Set filter mode
						if (macroParam < 0x20) aktMemo.filterMode = macroParam>>4;
						setupChannelFilter(aktMemo, !aktMemo.filterOn, 256);
						break;
				}
			}
		}
		else
		{
			// Forget about it for now
		}
	}
	/**
	 * This channel is active if 
	 * - it has a sample set
	 * - its tuning is not 0
	 * - its playing instrument has not finished yet
	 * - its channelNumber is not -1 (that is a free NNA)
	 * - Silence is not a factor - samples need to be rendered even if silent (XMs)
	 * @since 30.03.2010
	 * @param aktMemo
	 * @return
	 */
	protected boolean isChannelActive(final ChannelMemory aktMemo)
	{
		return (aktMemo!=null)?(!aktMemo.instrumentFinished && aktMemo.currentTuning!=0 && aktMemo.currentSample!=null && aktMemo.channelNumber!=-1):false;
	}
	/**
	 * Service method to reset envelope pointers when
	 * new instrument / sample is set.
	 * Considers the carry flag
	 * @since 21.03.2024
	 * @param aktMemo
	 * @param ins
	 */
	protected void resetEnvelopes(final ChannelMemory aktMemo, final Instrument ins)
	{
		if (ins!=null)
		{
			final Envelope volumeEnvelope = ins.volumeEnvelope;
			if (volumeEnvelope!=null && !volumeEnvelope.carry)
			{
				aktMemo.volEnvTick = volumeEnvelope.getInitPosition();
				aktMemo.volXMEnvPos = 0;
			}

			final Envelope panningEnvelope = ins.panningEnvelope;
			if (panningEnvelope!=null &&!panningEnvelope.carry)
			{
				aktMemo.panEnvTick = panningEnvelope.getInitPosition();
				aktMemo.panXMEnvPos = 0;
			}

			final Envelope pitchEnvelope = ins.pitchEnvelope;
			if (pitchEnvelope!=null &&!pitchEnvelope.carry) aktMemo.pitchEnvTick = pitchEnvelope.getInitPosition();
		}
	}
	/**
	 * Service method to reset envelope pointers when
	 * new instrument / sample is set.
	 * Considers the carry flag
	 * @since 19.06.2020
	 * @param aktMemo
	 */
	protected void resetEnvelopes(final ChannelMemory aktMemo)
	{
		resetEnvelopes(aktMemo, aktMemo.assignedInstrument);
	}
	/**
	 * Set all index values back to zero!
	 * Is for new notes or re-trigger a note
	 * @since 19.06.2006
	 * @param aktMemo
	 * @param forceS3MZero set to true at S3M re-trigger to not use sampleOffset Memo but zero in that case
	 */
	protected void resetInstrumentPointers(final ChannelMemory aktMemo, final boolean forceS3MZero)
	{
		aktMemo.EFxOffset =
		aktMemo.currentTuningPos = 
		aktMemo.interpolationMagic = 0; 
		aktMemo.isForwardDirection = true; 
		aktMemo.instrumentFinished = false;

		// special MOD sample offset handling
		if (isMOD && aktMemo.prevSampleOffset>0 && aktMemo.currentSample!=null)
		{
			final int max = aktMemo.currentSample.length-1;
			aktMemo.currentSamplePos = (aktMemo.prevSampleOffset>max)?max:aktMemo.prevSampleOffset;
		}
		else
		if (isS3M) // special S3M sample offset handling
		{
			final int offset = (!forceS3MZero)?((ScreamTrackerMixer)this).validateNewSampleOffset(aktMemo, aktMemo.prevSampleOffset):0;
			aktMemo.currentSamplePos = offset;
			// prevSampleOffset is set zero in setNewInstrumentAndPeriod with a new instrument
		}
		else
		{
			aktMemo.prevSampleOffset = 
			aktMemo.currentSamplePos = 0;
		}
	}
	/**
	 * @since 21.03.2024
	 * @param aktMemo
	 */
	protected void resetFineTune(final ChannelMemory aktMemo, final Sample currentSample)
	{
		if (currentSample!=null)
		{
			aktMemo.currentFinetuneFrequency = currentSample.baseFrequency;
			aktMemo.currentFineTune = currentSample.fineTune;
			aktMemo.currentTranspose = currentSample.transpose;
		}
	}
	/**
	 * Service method to reset Volume and Panning of current instrument / sample 
	 * @since 24.12.2023
	 * @param aktMemo
	 * @param newInstrument
	 * @param newSample
	 */
	protected void resetVolumeAndPanning(final ChannelMemory aktMemo, final Instrument newInstrument, final Sample newSample)
	{
		if (newInstrument!=null && newInstrument.setPanning)
		{
			aktMemo.currentInstrumentPanning = aktMemo.panning = newInstrument.defaultPanning;
			//aktMemo.muted = newInstrument.mute;
		}
		if (newSample!=null)
		{
			aktMemo.currentInstrumentVolume = aktMemo.currentVolume = newSample.volume;
			// Sample panning overrides instrument panning
			if (newSample.setPanning) aktMemo.currentInstrumentPanning = aktMemo.panning = newSample.defaultPanning; 
		}
		aktMemo.doFastVolRamp = true; // resetting the volume means some kind of "re-trigger" - do not make it soft!
	}
	/**
	 * reset table positions of vibrato, tremolo and panbrello - if allowed
	 * PT2(MOD) does this with a new note
	 * FT2(XM) does this with a new instrument
	 * ScreamTracker/ImpulseTracker only resets vibrato on newNote
	 * BTW: Panbrello is a MPT Extended XM effect. Hopefully they reset that one
	 * in the same way.
	 * @since 27.03.2024
	 * @param aktMemo
	 */
	protected void reset_VibTremPan_TablePositions(final ChannelMemory aktMemo)
	{
		if (!aktMemo.vibratoNoRetrig) aktMemo.vibratoTablePos = 0;
		if (!isScreamTrackerFamily && !aktMemo.tremoloNoRetrig) aktMemo.tremoloTablePos = 0;
		if (!isScreamTrackerFamily && !aktMemo.panbrelloNoRetrig) aktMemo.panbrelloTablePos = 0;
	}
	/**
	 * Reset Autovibrato for FastTracker
	 * @since 28.03.2024
	 * @param aktMemo
	 * @param sample
	 */
	protected void resetAutoVibrato(final ChannelMemory aktMemo, final Sample sample)
	{
		if (isXM && sample!=null)
		{
			if (sample.vibratoDepth>0)
			{
				aktMemo.autoVibratoTablePos = 0;
	
				if (aktMemo.autoVibratoSweep>0)
				{
					aktMemo.autoVibratoAmplitude=0;
					aktMemo.autoVibratoSweep = (sample.vibratoDepth<<8) / sample.vibratoSweep;
				}
				else
				{
					aktMemo.autoVibratoAmplitude = sample.vibratoDepth<<8;
					aktMemo.autoVibratoSweep = 0;
				}
			}
		}
		if (isScreamTrackerFamily)
		{
			aktMemo.autoVibratoTablePos = aktMemo.autoVibratoAmplitude = 0;
		}
	}
	/**
	 * Reset some effects that changed amplitude or volume or panning.
	 * This was abstract to reflect on certain continuing effects - but that is not the right way
	 * @param aktMemo
	 * @param currentElement
	 */
	protected void resetAllEffects(final ChannelMemory aktMemo, final PatternElement currentElement)
	{
//		// From playVoice in PT code - obviously not necessary with us
//		// because we have the same effect with the logic downwards
//		if (isMOD && hasNoNote(currentElement) && currentElement.getEffekt()==0 && currentElement.getEffektOp()==0)
//		{
//			setNewPlayerTuningFor(aktMemo);
//		}

		if (aktMemo.arpeggioIndex>=0)
		{
			aktMemo.arpeggioIndex=-1;
			if (isSTM) aktMemo.currentNotePeriod = aktMemo.currentNotePeriodSet;
			else
			if (isS3M)
			{
				if (currentElement.getEffekt()==0x05 || currentElement.getEffekt()==0x06)
					aktMemo.currentNotePeriod = aktMemo.currentNotePeriodSet;
			}
			else
			{
				final int baseNotePeriod = aktMemo.arpeggioNote[0];
				if (baseNotePeriod!=0)
				{
					setNewPlayerTuningFor(aktMemo, aktMemo.currentNotePeriod = baseNotePeriod);
				}
			}
		}

		if (aktMemo.vibratoOn || aktMemo.vibratoVolOn) // We have a vibrato for reset
		{
			// With FastTracker, do not reset volumeColumn vibrato freq (VibratoOn is only set with effect column)
			if ((!isXM && aktMemo.vibratoOn) || 
				 (isXM && aktMemo.vibratoOn && currentElement.getEffekt()!=4 && currentElement.getEffekt()!=6))
				setNewPlayerTuningFor(aktMemo);
			aktMemo.vibratoOn = false;
			aktMemo.vibratoVolOn = false; // only set with XMs
		}

		if (aktMemo.tremoloOn) // We have a tremolo for reset
		{
			aktMemo.tremoloOn = false;
			if (!isXM) aktMemo.currentVolume = aktMemo.currentInstrumentVolume;
			aktMemo.doFastVolRamp = true;
		}

		if (aktMemo.panbrelloOn) // We have a panbrello for reset
		{
			aktMemo.panbrelloOn = false;
			if (!isIT)
			{
				aktMemo.panning = aktMemo.currentInstrumentPanning;
				aktMemo.doFastVolRamp = true;
			}
		}
	}
	/**
	 * Will set the filters, if any - and return the filter-status set, for later use
	 * @since 29.12.2023
	 * @param aktMemo
	 * @param inst
	 */
	protected boolean setFilterAndRandomVariations(final ChannelMemory aktMemo, final Instrument inst, boolean useFilter)
	{
		// Set Resonance!
		if ((inst.initialFilterResonance & 0x80)!=0) { aktMemo.resonance = inst.initialFilterResonance & 0x7F; useFilter = true; }
		if ((inst.initialFilterCutoff & 0x80)!=0) { aktMemo.cutOff = inst.initialFilterCutoff & 0x7F; useFilter = true;}
		if (useFilter && inst.filterMode!=ModConstants.FLTMODE_UNCHANGED) aktMemo.filterMode = inst.filterMode;

		// first reset. This can be done safely here, because either IT-Mods have no instruments at all
		// or all samples are accessed through instruments. There is no mix
		aktMemo.swingVolume = aktMemo.swingPanning = aktMemo.swingResonance = aktMemo.swingCutOff = 0;
		
		// These values are added on top of their respective counterparts (channelvolume, panning, cutoff, resonance)
		// therefore the changes do not manipulate channel memories and restoring values are not necessary
		if (inst.randomVolumeVariation>=0)
		{
			// MPT uses the sample volume, IT use inst.globalVolume
			//aktMemo.swingVolume = (((((inst.randomVolumeVariation * (swinger.nextInt() % 0x80))>>6)+1) * inst.globalVolume / 199);
			aktMemo.swingVolume = (((((inst.randomVolumeVariation * (swinger.nextInt() % 0xFF))>>6)+1) * aktMemo.currentInstrumentVolume) / 199);
		}
		if (inst.randomPanningVariation>=0)
		{
			aktMemo.swingPanning = (int)((inst.randomPanningVariation<<2) * (swinger.nextInt() % 0x80)) >> 7;
		}
		// ModPlugTracker extended instruments.
		if (inst.randomResonanceVariation>=0)
		{
			aktMemo.swingResonance = (((int)(inst.randomResonanceVariation * (swinger.nextInt() % 0x80)) >> 7) * aktMemo.resonance + 1) >> 7;
		}
		if (inst.randomCutOffVariation>=0)
		{
			aktMemo.swingCutOff = (((int)(inst.randomCutOffVariation * (swinger.nextInt() % 0x80)) >> 7) * aktMemo.cutOff + 1) >> 7; 
		}
		
		return useFilter;
	}
	/**
	 * Do the row and volume effects inside a Tick (tick>0)
	 * On tick 0 simply call "doRowEffects"
	 * IT: first Row, then VolumeColumn
	 * Others: vice versa
	 * @since 18.09.2010
	 * @param aktMemo
	 */
	protected void processEffektsInTick(final ChannelMemory aktMemo)
	{
		if (!aktMemo.isNNA) // no effects for NNA Channel, only envelopes
		{
			// XM is weird: current Tick effects are performed during a note delay.
			// First vol column, than effect column (that is then the note delay, which is evaluated there)
			// if the note delay finishes, Note, Instrument and volume are set
			// plus *again* the volume column is executed, if it is a "set volume" or a "set panning"
			// So, what OMPT says to its test "VolColDelay.xm" is therefore not wrong, but also not
			// the whole truth.
			// For ProTracker and FastTracker, all is done in the tick effects, not centrally
			// here, like it used to be.
			if (!isFastTrackerFamily && aktMemo.noteDelayCount>0)
			{
				if (aktMemo.noteDelayCount>=currentTempo && isScreamTrackerFamily)
				{
					// illegal note delay - ignore it, do not copy new values
					// to do so, we replace with the values from assigned*
					aktMemo.noteDelayCount = -1;
					
					aktMemo.currentAssignedNotePeriod = aktMemo.assignedNotePeriod;
					aktMemo.currentAssignedNoteIndex = aktMemo.assignedNoteIndex;
					aktMemo.currentAssignedEffekt = aktMemo.assignedEffekt;
					aktMemo.currentAssignedEffektParam = aktMemo.assignedEffektParam; 
					aktMemo.currentAssignedVolumeEffekt = aktMemo.assignedVolumeEffekt; 
					aktMemo.currentAssignedVolumeEffektOp = aktMemo.assignedVolumeEffektOp;
					
					// Not fully empty with IT - instrument is remembered, so do not replace!
					if (!isIT)
					{
						aktMemo.currentAssignedInstrumentIndex = aktMemo.assignedInstrumentIndex;
						aktMemo.currentAssignedInstrument = aktMemo.assignedInstrument;
					}
				}
				else
				{
					aktMemo.noteDelayCount--;
					if (aktMemo.noteDelayCount<=0)
					{
						setNewInstrumentAndPeriod(aktMemo);
						doVolumeColumnRowEffekt(aktMemo);
						// finish NoteDelay
						aktMemo.noteDelayCount = -1;
					}
				}
			}
			else
			{
				processTickEffekts(aktMemo);
			}
		}
		processEnvelopes(aktMemo);
	}
	protected boolean isInfiniteLoop(final int currentArrangement, final PatternRow patternRow)
	{
		return (mod.isArrangementPositionPlayed(currentArrangement) && patternRow.isRowPlayed());
	}
	protected boolean isInfiniteLoop(final int currentArrangement, final int currentRow)
	{
		return isInfiniteLoop(currentArrangement, currentPattern.getPatternRow(currentRow));
	}
	/**
	 * Do the Events of a new Row!
	 * @return true, if finished! 
	 */
	protected void doRowEvents()
	{
		final PatternRow patternRow = currentPattern.getPatternRow(currentRow);
		if (patternRow==null) return;

		patternRow.setRowPlayed();

		// inform listeners, that we are in a new row!
		firePatternPositionUpdate(sampleRate, samplesMixed, getCurrentPatternPosition());

		for (int c=0; c<maxChannels; c++)
		{
			final ChannelMemory aktMemo = channelMemory[c];

			if (!aktMemo.isNNA) // no NNA Channel
			{
				// get pattern and channel memory data for current channel
				final PatternElement element = aktMemo.currentElement = patternRow.getPatternElement(c);

				// reset all effects on this channel
				resetAllEffects(aktMemo, element);
				
				if (element.getPeriod()>ModConstants.NO_NOTE) aktMemo.currentAssignedNotePeriod = element.getPeriod(); 
				if (element.getNoteIndex()>ModConstants.NO_NOTE) aktMemo.currentAssignedNoteIndex = element.getNoteIndex();

				aktMemo.currentAssignedEffekt = element.getEffekt();
				aktMemo.currentAssignedEffektParam = element.getEffektOp();
				aktMemo.currentAssignedVolumeEffekt = element.getVolumeEffekt();
				aktMemo.currentAssignedVolumeEffektOp = element.getVolumeEffektOp();
	
				if (element.getInstrument()>0)
				{
					aktMemo.currentAssignedInstrumentIndex = element.getInstrument();
					if (!isIT || (mod.getSongFlags()&ModConstants.SONG_USEINSTRUMENTS)!=0) // ITs know of a "sample only" mode - so do not look up instruments then
						aktMemo.currentAssignedInstrument = mod.getInstrumentContainer().getInstrument(aktMemo.currentAssignedInstrumentIndex-1);
				}

				// S00 effect memory with Impulse Tracker
				if (isIT)
				{
					if (aktMemo.currentAssignedEffekt!=0 && aktMemo.currentAssignedEffektParam == 0)
						aktMemo.currentAssignedEffektParam = getEffektOpMemory(aktMemo, aktMemo.currentAssignedEffekt, aktMemo.currentAssignedEffektParam);
				}

				// Now check for noteDelay effect and handle it accordingly
				// With ProTracker, Note delay is handled easy
				if (isMOD || !isNoteDelayEffekt(aktMemo.currentAssignedEffekt, aktMemo.currentAssignedEffektParam)) // If this is a noteDelay, we cannot call processEffekts
				{
					setNewInstrumentAndPeriod(aktMemo);
					processEffekts(aktMemo); // Tick 0
				}
				else
				{ // !isMOD && isNoteDelay
					if (isFastTrackerFamily)
					{
						aktMemo.assignedEffekt = aktMemo.currentAssignedEffekt;
						aktMemo.assignedEffektParam = aktMemo.currentAssignedEffektParam;
						aktMemo.assignedVolumeEffekt = aktMemo.currentAssignedVolumeEffekt;
						aktMemo.currentAssignedVolumeEffektOp = aktMemo.currentAssignedVolumeEffektOp;
						doRowEffects(aktMemo);
					}
					else
					{
						// In a NoteDelay things are special - we want to set the note delay as trackers want it.
						// But because setNewInstrumentAndPeriod was not yet called, no effects were copied as we are still on the old ones - and have to be!
						// We cannot call processEffekts as that would also do VolumeColumnRowEffects - and those are for later.
						// However, to avoid a double implementation and to call doRowEffekts then, we need to set the effekt/effektOp, call it,
						// and set the effect back. That way we can use the Tracker-specific implementations
						final int retEffekt = aktMemo.assignedEffekt;
						final int retEffektOp = aktMemo.assignedEffektParam;
						aktMemo.assignedEffekt = aktMemo.currentAssignedEffekt;
						aktMemo.assignedEffektParam = aktMemo.currentAssignedEffektParam;
						doRowEffects(aktMemo);
						aktMemo.assignedEffekt = retEffekt;
						aktMemo.assignedEffektParam = retEffektOp;
					}
				}
			}
			// With FastTracker, globalVolume is applied when it occurs.
			// That is, the envelopes are processed in this loop, not afterwards
			// in a whole, as seen below with Non-FastTracker
			if (isXM) processEnvelopes(channelMemory[c]);
		}
		// with Row Effects, first all rows effect parameter need to be
		// processed - then we can do the envelopes and volume effects
		// Otherwise global (volume) effects would not be considered correctly.
		// Except for FastTracker - there it is different (see above)
		if (!isXM)
		{
			for (int c=0; c<maxChannels; c++)
				processEnvelopes(channelMemory[c]);
		}
	}
	/**
	 * when stepping to a new Pattern - Position needs new set...
	 * During pattern transition: S3M resets
	 * @since 21.01.2014
	 */
	private void resetJumpPositionSet()
	{
		if (isS3M)
		{
//			for (int c=0; c<maxChannels; c++)
//				channelMemory[c].jumpLoopPatternRow = -1;
			channelMemory[0].jumpLoopPatternRow =
			channelMemory[0].jumpLoopITLastRow = -1;
		}
	}
	/**
	 * Will proceed to the next row, the next pattern or signal "end of song"
	 * Will also perform any pattern jumps and pattern breaks
	 * @since 24.07.2024
	 * @return true, if piece finished
	 */
	protected void proceedToNextRow()
	{
		if (patternJumpSet) // Perform the jump
		{
			patternJumpSet = false;
			currentRow = patternJumpRowIndex;
			// PT/FT uses pBreakPosition/pBreakPos for all: Jump Position set and Pattern breaks row set
			// So with these we synch patternBreakRowIndex and patternJumpRowIndex
			// However, PT resets patternJumpRowIndex to Zero - so we do that as well
			if (isMOD) patternBreakRowIndex = patternJumpRowIndex = 0;
		}
		else
			currentRow++; // and step to the next row...

		// now check for end of pattern and perform patternJumps, if any
		if (currentRow>=currentPattern.getRowCount() || patternBreakSet)
		{
			patternBreakSet = false;
			mod.setArrangementPositionPlayed(currentArrangement);

			final boolean ignoreLoop = (doNoLoops&ModConstants.PLAYER_LOOP_IGNORE)!=0;
			final boolean fadeOutLoop = (doNoLoops&ModConstants.PLAYER_LOOP_FADEOUT)!=0;
			final boolean loopSong = (doNoLoops&ModConstants.PLAYER_LOOP_LOOPSONG)!=0;
			if (patternBreakPatternIndex!=-1)
			{
				final boolean infiniteLoop = isInfiniteLoop(patternBreakPatternIndex, patternBreakRowIndex);
				if (infiniteLoop && ignoreLoop)
				{
					patternBreakRowIndex = patternBreakPatternIndex = -1;
					currentArrangement++;
				}
				else
				{
					currentArrangement = patternBreakPatternIndex;
				}
				patternBreakPatternIndex = -1;
				// and activate fadeout, if wished
				if (infiniteLoop && fadeOutLoop)
					doLoopingGlobalFadeout = true;
			}
			else
			{
				currentArrangement++;
			}
			
			if (patternBreakRowIndex!=-1)
			{
				// With XMs patternBreakRowIndex might be set from 
				// patternJumpRowIndex - FT2 uses only one var for this
				currentRow = patternBreakRowIndex;
				patternBreakRowIndex = -1;
			}
			else
			{
				currentRow = 0;
			}
			
			// sanity check FT2.09 style
			if (isXM && currentArrangement<mod.getSongLength())
			{
				final int patIndex = mod.getArrangement()[currentArrangement];
				final Pattern pat = mod.getPatternContainer().getPattern(patIndex);
				if (currentRow >= pat.getRowCount())
				{
					currentArrangement--;
					currentRow=0;
					// as this is the meaning of infinity:
					if (fadeOutLoop)
						doLoopingGlobalFadeout = true;
				}
			}
			
			// Currently this is only done for S3Ms
			resetJumpPositionSet();
			
			// End of song? Fetch new pattern if not...
			if (currentArrangement<mod.getSongLength())
			{
				currentPatternIndex = mod.getArrangement()[currentArrangement];
				currentPattern = mod.getPatternContainer().getPattern(currentPatternIndex);
			}
			else
			{
				if (!ignoreLoop && loopSong)
				{
					currentArrangement = mod.getSongRestart(); // can be -1 if not set
					if (currentArrangement < 0) currentArrangement = 0;
					currentPatternIndex = mod.getArrangement()[currentArrangement];
					currentPattern = mod.getPatternContainer().getPattern(currentPatternIndex);
					// as this is per definition an infinite loop, activate fadeout, if wished
					if ((doNoLoops&ModConstants.PLAYER_LOOP_FADEOUT)!=0)
						doLoopingGlobalFadeout = true;
					
				}
				else
				{
					currentPatternIndex = -1;
					currentPattern = null;
				}
			}
		}
	}
	/**
	 * Do the events during a Tick.
	 * @return true, if finished! 
	 */
	protected boolean doRowAndTickEvents()
	{
		// Global Fade Out because of recognized endless loop
		if (doLoopingGlobalFadeout)
		{
			loopingFadeOutValue-=ModConstants.FADEOUT_SUB;
			if (loopingFadeOutValue <= 0)
			{
				currentPatternIndex = -1;
				currentPattern = null;
				currentTick = 0;
				return true; // We did a fadeout and are finished now
			}
		}
		
		// ProTracker 1/2 had BPM sets centrally as first command in the Tick based loop.
		// In contrast, all other do it as the last command after RowEffects or directly
		// on each occurrence.
		// That however leads to speed changes on second Tick, not on first Tick. But if current
		// speed is 1, the BPM change is automatically a row later.
		if (isMOD && modSpeedSet>0)
		{
			currentBPM = modSpeedSet;
			modSpeedSet = 0;
			calculateSamplesPerTick();
		}
		
		if (patternTicksDelayCount>0) // Fine Pattern Delay in # ticks
		{
			for (int c=0; c<maxChannels; c++)
				processEffektsInTick(channelMemory[c]);
			patternTicksDelayCount--;
			return false;
		}
		
		currentTick--;
		if (currentTick>0)
		{
			// Do all Tickevents, 'cause we are in a Tick...
			for (int c=0; c<maxChannels; c++)
				processEffektsInTick(channelMemory[c]);
		}
		else
		{
			if (currentPattern==null) return true;
			
			currentTick = currentTempo;

			// if PatternDelay, do it and return
			if (patternDelayCount>0)
			{
				// Process effects
				for (int c=0; c<maxChannels; c++)
				{
					final ChannelMemory aktMemo = channelMemory[c];

					processEffektsInTick(aktMemo);

					// for IT and S3M (not STM!) re-set note delays, but do not call doRowEvents (is for tick=0 only)
					if (isIT || isS3M || isModPlug)
					{
						if (isNoteDelayEffekt(aktMemo.currentAssignedEffekt, aktMemo.currentAssignedEffektParam) && aktMemo.noteDelayCount<0)
						{
							final int effektOpEx = aktMemo.currentAssignedEffektParam&0x0F;
							
							if (isIT && effektOpEx==0)
								aktMemo.noteDelayCount = 1;
							else
								aktMemo.noteDelayCount = effektOpEx;
						}

						if (isPatternFramesDelayEffekt(aktMemo.currentAssignedEffekt, aktMemo.currentAssignedEffektParam))
						{
							patternTicksDelayCount += aktMemo.currentAssignedEffektParam&0x0F;
						}
					}
				}

				patternDelayCount--;
				if (patternDelayCount>0) return false;
				patternDelayCount = -1;
			}
			else
			{
				// Do the row events
				doRowEvents();
				if (patternDelayCount>0) return false;
			}
			proceedToNextRow();
		}

		// if not ProTracker, recalculate samplesPerTick here.
		// do this every(!) Tick with tempoMode "Modern" or on Tick zero for all others
		// currentPattern is null, if end was reached
		if (!isMOD && currentPattern!=null && (mod.getTempoMode()==ModConstants.TEMPOMODE_MODERN || currentTick==currentTempo))
			calculateSamplesPerTick();

		return (currentPatternIndex==-1 && currentTick<=0);
	}
	/**
	 * Add current speed to samplepos and
	 * fit currentSamplePos into loop values
	 * or signal Sample finished
	 * @since 18.06.2006
	 * @param aktMemo
	 */
	protected void fitIntoLoops(final ChannelMemory aktMemo)
	{
		final Sample sample = aktMemo.currentSample;
		if (sample.length<=0) return;

		aktMemo.currentTuningPos += aktMemo.currentTuning;
		if (aktMemo.currentTuningPos >= ModConstants.SHIFT_MAX)
		{
			final int addToSamplePos = aktMemo.currentTuningPos >> ModConstants.SHIFT;
			aktMemo.currentTuningPos &= ModConstants.SHIFT_MASK;

			// Set the start/end loop position to check against...
			int loopStart = 0;
			int loopEnd = sample.length;
			int loopLength = sample.length;
			int inLoop = 0;
			boolean interpolateLoop = false;
			
			if ((sample.loopType&ModConstants.LOOP_SUSTAIN_ON)!=0 && !aktMemo.keyOff) // Sustain Loop on?
			{
				loopStart = sample.sustainLoopStart;
				loopEnd = sample.sustainLoopStop;
				loopLength = sample.sustainLoopLength;
				inLoop = ModConstants.LOOP_SUSTAIN_ON;
			}
			else
			if ((sample.loopType&ModConstants.LOOP_ON)!=0) 
			{
				loopStart = sample.loopStart;
				loopEnd = sample.loopStop;
				loopLength = sample.loopLength;
				inLoop = ModConstants.LOOP_ON;
			}

			// If Forward direction:
			if (aktMemo.isForwardDirection)
			{
				aktMemo.currentSamplePos += addToSamplePos;
				
				// do we have an overrun of border?
				if (aktMemo.currentSamplePos >= loopEnd)
				{
					// In a mod file - if a new sample is set but not activated, activate now at end of loop
					// but do not set volume or finetune. That was set before.
					if (isMOD && aktMemo.assignedSample!=null && aktMemo.currentSample!=aktMemo.assignedSample)
					{
						aktMemo.currentSample = aktMemo.assignedSample;
						//aktMemo.assignedSample = null;
						aktMemo.prevSampleOffset = 0;
						// ProTracker always jumps to the loopStart and with empty loops these are 0-2 (mostly a silent part of the sample)
						// but we reset that to 0/0 and wouldn't loop in (0/2) anyways - so we jump at the sample end in that case to simulate that.
						aktMemo.currentSamplePos = ((sample.loopType&ModConstants.LOOP_ON)!=0)?aktMemo.currentSample.loopStart:aktMemo.currentSample.length-1;
						aktMemo.doFastVolRamp = true;
						return;
					}
					else
					// We need to check against a loop set - maybe a sustain loop is finished
					// but no normal loop is set:
					if (inLoop==0) // if no loop, loopEnd is sampleLength - we are finished.
					{
						aktMemo.instrumentFinished = true;
						aktMemo.interpolationMagic = 0;
						// if this is a NNA channel, free it
						if (aktMemo.isNNA) aktMemo.channelNumber = -1;
						return;
					}
					else
					{
						final int overShoot = (aktMemo.currentSamplePos - loopEnd) % loopLength;
						
						// check if loop, that was enabled, is a ping pong
						if ((inLoop == ModConstants.LOOP_ON && (sample.loopType & ModConstants.LOOP_IS_PINGPONG)!=0) ||
							(inLoop == ModConstants.LOOP_SUSTAIN_ON && (sample.loopType & ModConstants.LOOP_SUSTAIN_IS_PINGPONG)!=0))
						{
							aktMemo.isForwardDirection = false;
							aktMemo.currentSamplePos = loopEnd - overShoot - pingPongDiffIT;
						}
						else
						{
							aktMemo.currentSamplePos = loopStart + overShoot;
						}
						interpolateLoop = true;
					}
				}
			}
			else // Backwards in Ping Pong
			{
				aktMemo.currentSamplePos -= addToSamplePos;

				if (aktMemo.currentSamplePos < loopStart)
				{
					aktMemo.isForwardDirection = true;
					aktMemo.currentSamplePos = loopStart + ((loopStart - aktMemo.currentSamplePos) % loopLength);
				}
				interpolateLoop = true;
			}
			
			// after reposition of sample pointer, check for interpolation magic
			if (inLoop == ModConstants.LOOP_SUSTAIN_ON && !aktMemo.keyOff) // Sustain Loop on?
			{
				aktMemo.interpolationMagic = sample.getSustainLoopMagic(aktMemo.currentSamplePos, interpolateLoop);
			}
			else
			if (inLoop == ModConstants.LOOP_ON) 
			{
				aktMemo.interpolationMagic = sample.getLoopMagic(aktMemo.currentSamplePos, interpolateLoop);
			}
			else
				aktMemo.interpolationMagic = 0;
		}
	}
	/**
	 * Fill the buffers with channel data
	 * @since 18.06.2006
	 * @param leftBuffer
	 * @param rightBuffer
	 * @param startIndex
	 * @param amount
	 * @param aktMemo
	 */
	protected void mixChannelIntoBuffers(final long[] leftBuffer, final long[] rightBuffer, final int startIndex, final int endIndex, final ChannelMemory aktMemo)
	{
		for (int i=startIndex; i<endIndex; i++)
		{
			// Retrieve the sample data for this point (interpolated, if necessary)
			// the array "samples" is created with 2 elements per default
			// we will receive 2 long values even with mono samples
			final int doISPhere = (aktMemo.assignedInstrument!=null && aktMemo.assignedInstrument.resampling>-1)?aktMemo.assignedInstrument.resampling:doISP;
			aktMemo.currentSample.getInterpolatedSample(samples, doISPhere, aktMemo.currentTuning, aktMemo.currentSamplePos, aktMemo.currentTuningPos, !aktMemo.isForwardDirection, aktMemo.interpolationMagic);

			// Resonance Filters
			if (aktMemo.filterOn) doResonance(aktMemo, samples);

			// Testing, no Ramping
//			int volL = aktMemo.actRampVolLeft = aktMemo.actVolumeLeft;
//			int volR = aktMemo.actRampVolRight = aktMemo.actVolumeRight;

			// Volume Ramping
			int volL = aktMemo.actRampVolLeft;
			if (aktMemo.deltaVolLeft!=0)
			{
				if ((aktMemo.deltaVolLeft>0 && volL>=aktMemo.actVolumeLeft) ||
					(aktMemo.deltaVolLeft<0 && volL<=aktMemo.actVolumeLeft))
				{
					// Target reached
					volL = aktMemo.actRampVolLeft = aktMemo.actVolumeLeft;
					aktMemo.deltaVolLeft = 0;
				}
				else
					aktMemo.actRampVolLeft += aktMemo.deltaVolLeft;
			}
			int volR = aktMemo.actRampVolRight;
			if (aktMemo.deltaVolRight!=0)
			{
				if ((aktMemo.deltaVolRight>0 && volR>=aktMemo.actVolumeRight) ||
					(aktMemo.deltaVolRight<0 && volR<=aktMemo.actVolumeRight))
				{
					// Target reached
					volR = aktMemo.actRampVolRight = aktMemo.actVolumeRight;
					aktMemo.deltaVolRight = 0;
				}
				else
					aktMemo.actRampVolRight += aktMemo.deltaVolRight;
			}
			
			// do not store, if muted...
			if (!aktMemo.muted)
			{
				// Fit into volume for the two channels
				long sampleL = (samples[0]*volL)>>(ModConstants.MAXVOLUMESHIFT + ModConstants.VOLRAMPLEN_FRAC);
				long sampleR = (samples[1]*volR)>>(ModConstants.MAXVOLUMESHIFT + ModConstants.VOLRAMPLEN_FRAC);
	
				// and off you go
				leftBuffer [i] += sampleL;
				rightBuffer[i] += sampleR;

				// store the highest (absolute) sample for display in the ModPatternDialog
				if (sampleL<0) sampleL = -sampleL;
				if (sampleL>aktMemo.bigSampleLeft) aktMemo.bigSampleLeft = sampleL;
				if (sampleR<0) sampleR = -sampleR;
				if (sampleR>aktMemo.bigSampleRight) aktMemo.bigSampleRight = sampleR;
			}
			
			// Now next sample plus fit into loops - if any
			fitIntoLoops(aktMemo);

			if (aktMemo.instrumentFinished) break;
		}
	}
	/**
	 * Retrieves Sample Data without manipulating data that will change during
	 * this "look ahead"
	 * @since 18.06.2006
	 * @param leftBuffer
	 * @param rightBuffer
	 * @param aktMemo
	 */
//	private ChannelMemory memory = new ChannelMemory();
	private void fillRampDataIntoBuffers(final long[] leftBuffer, final long[] rightBuffer, final ChannelMemory aktMemo)
	{
		// Remember changeable values
		final long	  filter_Y1				= aktMemo.filter_Y1;
		final long	  filter_Y2				= aktMemo.filter_Y2;
		final long	  filter_Y3				= aktMemo.filter_Y3;
		final long	  filter_Y4				= aktMemo.filter_Y4;
		final int	  actRampVolLeft		= aktMemo.actRampVolLeft;
		final int	  actRampVolRight		= aktMemo.actRampVolRight;
		final int	  deltaVolLeft			= aktMemo.deltaVolLeft;
		final int	  deltaVolRight			= aktMemo.deltaVolRight;
		final boolean instrumentFinished	= aktMemo.instrumentFinished;
		final int	  currentTuningPos		= aktMemo.currentTuningPos;
		final int	  currentSamplePos		= aktMemo.currentSamplePos;
		final boolean isForwardDirection	= aktMemo.isForwardDirection;
		final int	  interpolationMagic	= aktMemo.interpolationMagic;
		final Sample  currentSample			= aktMemo.currentSample;
		final Sample  assignedSample		= aktMemo.assignedSample;
		aktMemo.assignedSample				= null; // no sample swap here!

//		memory.setUpFrom(aktMemo);
		mixChannelIntoBuffers(leftBuffer, rightBuffer, 0, ModConstants.INTERWEAVE_LEN, aktMemo);
//		aktMemo.setUpFrom(memory);

		// set them back
		aktMemo.filter_Y1			= filter_Y1;
		aktMemo.filter_Y2			= filter_Y2;
		aktMemo.filter_Y3			= filter_Y3;
		aktMemo.filter_Y4			= filter_Y4;
		aktMemo.actRampVolLeft		= actRampVolLeft;
		aktMemo.actRampVolRight		= actRampVolRight;
		aktMemo.deltaVolLeft		= deltaVolLeft;
		aktMemo.deltaVolRight		= deltaVolRight;
		aktMemo.instrumentFinished	= instrumentFinished;
		aktMemo.currentTuningPos	= currentTuningPos;
		aktMemo.currentSamplePos	= currentSamplePos;
		aktMemo.isForwardDirection	= isForwardDirection;
		aktMemo.interpolationMagic	= interpolationMagic;
		aktMemo.currentSample		= currentSample;
		aktMemo.assignedSample		= assignedSample;
	}
	/**
	 * Will mix #count 32bit signed samples in stereo into the two buffer.
	 * The buffers will contain 32Bit signed samples.
	 * @param leftBuffer
	 * @param rightBuffer
	 * @param bufferSize
	 * @return #of samples mixed, -1 if mixing finished
	 */
	public int mixIntoBuffer(final long[] leftBuffer, final long[] rightBuffer, final int bufferSize)
	{
		if (modFinished) return -1;
		
		int startIndex = 0; // we start at zero
		int endIndex = 0; // where to finish mixing

		// is there something left for interweaving?
		final int samplesAlreadMixed = samplesPerTick - leftOverSamplesPerTick;
		int interweaveStartIndex = (samplesAlreadMixed<ModConstants.INTERWEAVE_LEN)?samplesAlreadMixed:0;
		boolean interweave = interweaveStartIndex!=0;

		while (endIndex<bufferSize && !modFinished)
		{
			if (leftOverSamplesPerTick<=0)
			{
				// Stepping over a tick, so prepare interweaving ramp buffer
				for (int c=0; c<maxChannels; c++)
				{
					final ChannelMemory aktMemo = channelMemory[c];
					if (!aktMemo.instrumentFinished && aktMemo.currentSample!=null) fillRampDataIntoBuffers(interweaveBufferLeft, interweaveBufferRight, aktMemo);
				}
				interweaveStartIndex=0; interweave = true;
				// now do the events
				modFinished = doRowAndTickEvents();
				leftOverSamplesPerTick = samplesPerTick; // speed changes also change samplesPerTick - so reset after doTickEvents!
			}

			final int mixAmount = ((endIndex + leftOverSamplesPerTick)>=bufferSize)?bufferSize-endIndex:leftOverSamplesPerTick;
			
			endIndex += mixAmount;
			leftOverSamplesPerTick -= mixAmount;
			
			for (int c=0; c<maxChannels; c++)
			{
				final ChannelMemory aktMemo = channelMemory[c];

				aktMemo.bigSampleLeft = aktMemo.bigSampleRight = 0;
				// Mix this channel?
				if (isChannelActive(aktMemo)) mixChannelIntoBuffers(leftBuffer, rightBuffer, startIndex, endIndex, aktMemo);
				
				if (!aktMemo.isNNA)
				{
					// This is only for eye-candy
					final boolean setZero = aktMemo.instrumentFinished || globalVolume==0 || masterVolume==0; 
					final int sampleL = (setZero)?0:(int)(((((aktMemo.bigSampleLeft <<(7+((useGlobalPreAmp)?ModConstants.PREAMP_SHIFT-1:ModConstants.PREAMP_SHIFT))) / (long)globalVolume / (long)masterVolume)<<extraAttenuation)+0x8000000)>>28);
					final int sampleR = (setZero)?0:(int)(((((aktMemo.bigSampleRight<<(7+((useGlobalPreAmp)?ModConstants.PREAMP_SHIFT-1:ModConstants.PREAMP_SHIFT))) / (long)globalVolume / (long)masterVolume)<<extraAttenuation)+0x8000000)>>28);
					firePeekUpdate(sampleRate, samplesMixed, c, sampleL, sampleR, aktMemo.doSurround);
				}
			}

			// Now interweave with last ticks ramp buffer data
			if (interweave)
			{
				// check for space left in target buffer
				// if not, in the next round, interweaveStartIndex will be set accordingly
				final int interweaveBufferLen = (mixAmount<ModConstants.INTERWEAVE_LEN)?mixAmount:ModConstants.INTERWEAVE_LEN;
				for (int n=interweaveStartIndex; n<interweaveBufferLen; n++)
				{
					final long difFade = (long)(ModConstants.INTERWEAVE_LEN - n);
					final int bufferIndex = startIndex + n - interweaveStartIndex;
					
					leftBuffer [bufferIndex] = ((leftBuffer [bufferIndex] * (long)n) + (interweaveBufferLeft [n] * difFade))>>ModConstants.INTERWEAVE_FRAC;
					rightBuffer[bufferIndex] = ((rightBuffer[bufferIndex] * (long)n) + (interweaveBufferRight[n] * difFade))>>ModConstants.INTERWEAVE_FRAC;
					
					// And clear buffer, if nothing is added above... (all samples are silent)
					interweaveBufferLeft[n] = interweaveBufferRight[n] = 0;
				}
				interweave = false;
			}

			startIndex += mixAmount;
			samplesMixed += mixAmount;
		}

		return startIndex;
	}
	/**
	 * after a mute change, copy this to all their NNA channels
	 * @since 28.11.2023
	 */
	private void setNNAMuteStatus()
	{
		for (int c=mod.getNChannels(); c<maxChannels; c++)
		{
			final ChannelMemory aktMemo = channelMemory[c];
			if (aktMemo.channelNumber > -1) // aktive NNA-Channel
			{
				aktMemo.muted = channelMemory[aktMemo.channelNumber].muted;
			}
		}
	}
	/**
	 * Will mute/unmute a channel
	 * @since 27.11.2023
	 * @param channelNumber
	 */
	public void toggleMuteChannel(final int channelNumber)
	{
		if (channelNumber>=0 && channelNumber<=mod.getNChannels())
		{
			final ChannelMemory aktMemo = channelMemory[channelNumber];
			if (!aktMemo.muteWasITforced) aktMemo.muted = !aktMemo.muted;
			setNNAMuteStatus();
		}
	}
	/**
	 * Will mute/unmute a channel
	 * @since 16.03.2024
	 * @param channelNumber
	 */
	public void setMuteChannel(final int channelNumber, final boolean muted)
	{
		if (channelNumber>=0 && channelNumber<=mod.getNChannels())
		{
			final ChannelMemory aktMemo = channelMemory[channelNumber];
			if (!aktMemo.muteWasITforced) aktMemo.muted = muted;
			setNNAMuteStatus();
		}
	}
	/**
	 * Unmutes all Channels, except, if IT wanted this channel to be muted!
	 * @since 27.11.2023
	 */
	public void unMuteAll()
	{
		for (int c=0; c<mod.getNChannels(); c++)
		{
			final ChannelMemory aktMemo = channelMemory[c];
			aktMemo.muted = aktMemo.muteWasITforced;
		}
		setNNAMuteStatus();
	}
	/**
	 * All channels but this one will be muted
	 * @since 27.11.2023
	 * @param channelNumber
	 */
	public void makeChannelSolo(final int channelNumber)
	{
		if (channelNumber>=0 && channelNumber<=maxChannels)
		{
			for (int c=0; c<maxChannels; c++)
			{
				final ChannelMemory aktMemo = channelMemory[c];
				if (!aktMemo.muteWasITforced) aktMemo.muted = c!=channelNumber;
			}
			setNNAMuteStatus();
		}
	}
	/**
	 * @since 28.11.2023
	 * @return an array representing the mute status
	 */
	public boolean [] getMuteStatus()
	{
		final boolean [] mutedChannels = new boolean [maxChannels];
		for (int c=0; c<maxChannels; c++)
		{
			mutedChannels[c] = channelMemory[c].muted;
		}
		return mutedChannels;
	}
	/**
	 * @since 28.11.2023
	 * @param listener
	 */
	public void registerUpdateListener(final ModUpdateListener listener)
	{
		if (listeners!=null && !listeners.contains(listener)) listeners.add(listener);
	}
	/**
	 * @since 28.11.2023
	 * @param listener
	 */
	public void deregisterUpdateListener(final ModUpdateListener listener)
	{
		if (listeners!=null && listeners.contains(listener)) listeners.remove(listener);
	}
	/**
	 * @since 28.11.2023
	 * @param newFireUpdates
	 */
	public void setFireUpdates(final boolean newFireUpdates)
	{
		fireUpdates = newFireUpdates;
		fireInformationUpdate(fireUpdates);
	}
	/**
	 * @since 28.11.2023
	 * @return
	 */
	public boolean getFireUpdates()
	{
		return fireUpdates;
	}
	/**
	 * Pattern Update Position
	 * @since 28.11.2023
	 * @param sampleRate
	 * @param samplesMixed
	 * @param position
	 */
	public void firePatternPositionUpdate(final int sampleRate, final long samplesMixed, final long position)
	{
		if (listeners!=null && fireUpdates)
		{
			PatternPositionInformation information = new PatternPositionInformation(sampleRate, samplesMixed, position);
			for (ModUpdateListener listener : listeners)
			{
				listener.getPatternPositionInformation(information);
			}
		}
	}
	/**
	 * Volume at a certain position
	 * @since 28.11.2023
	 * @param sampleRate
	 * @param samplesMixed
	 * @param channel
	 * @param actPeekLeft
	 * @param actPeekRight
	 */
	public void firePeekUpdate(final int sampleRate, final long samplesMixed, final int channel, final int actPeekLeft, final int actPeekRight, final boolean isSurround)
	{
		if (listeners!=null && fireUpdates)
		{
			PeekInformation information = new PeekInformation(sampleRate, samplesMixed, channel, actPeekLeft, actPeekRight, isSurround);
			for (ModUpdateListener listener : listeners)
			{
				listener.getPeekInformation(information);
			}
		}
	}
	/**
	 * We started or stopped to fire updates.
	 * @since 28.11.2023
	 * @param newStatus
	 */
	public void fireInformationUpdate(final boolean newStatus)
	{
		if (listeners!=null)
		{
			StatusInformation information = new StatusInformation(newStatus);
			for (ModUpdateListener listener : listeners)
			{
				listener.getStatusInformation(information);
			}
		}
	}
}
