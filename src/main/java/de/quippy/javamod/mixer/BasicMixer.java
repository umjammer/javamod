/*
 * @(#) BasicMixer.java
 *
 * Created on 30.12.2007 by Daniel Becker
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
package de.quippy.javamod.mixer;

import de.quippy.javamod.system.Log;


/**
 * @author Daniel Becker
 * @since 30.12.2007
 */
public abstract class BasicMixer extends Mixer
{
	private static final int ISNOTHING = 0;
	private static final int ISDOING = 1;
	private static final int ISDONE = 2;
	
	private volatile int paused;
	private volatile int stopped;
	private volatile int seeking;
	private volatile boolean hasFinished;
	private long seekPosition;
	private long stopPosition;

	/**
	 * Constructor for BasicMixer
	 */
	public BasicMixer()
	{
		super();
		setIsStopped();
		seekPosition = 0;
		stopPosition = -1;
		hasFinished = false;
	}

	/**
	 * @return
	 * @see de.quippy.javamod.mixer.Mixer#isPaused()
	 */
	@Override
	public boolean isPaused()
	{
		return paused==ISDONE;
	}
	/**
	 * @return
	 * @see de.quippy.javamod.mixer.Mixer#isPausing()
	 */
	@Override
	public boolean isPausing()
	{
		return paused==ISDOING;
	}
	/**
	 * @return
	 * @see de.quippy.javamod.mixer.Mixer#isStopped()
	 */
	@Override
	public boolean isStopped()
	{
		return stopped==ISDONE;
	}
	/**
	 * @return
	 * @see de.quippy.javamod.mixer.Mixer#isStopping()
	 */
	@Override
	public boolean isStopping()
	{
		return stopped==ISDOING;
	}
	/**
	 * @return
	 * @see de.quippy.javamod.mixer.Mixer#isNotPausingNorPaused()
	 */
	@Override
	public boolean isNotPausingNorPaused()
	{
		return paused==ISNOTHING;
	}
	/**
	 * @return
	 * @see de.quippy.javamod.mixer.Mixer#isNotStoppingNorStopped()
	 */
	@Override
	public boolean isNotStoppingNorStopped()
	{
		return stopped==ISNOTHING;
	}
	/**
	 * @return
	 * @see de.quippy.javamod.mixer.Mixer#isPlaying()
	 */
	@Override
	public boolean isPlaying()
	{
		return !isStopped();
	}
	/**
	 * @return
	 * @see de.quippy.javamod.mixer.Mixer#isFinished()
	 */
	@Override
	public boolean hasFinished()
	{
		return hasFinished;
	}
	protected void setIsPausing()
	{
		paused = ISDOING; 
		stopped = ISNOTHING;
		seeking = ISNOTHING;
	}
	protected void setIsPaused()
	{
		paused = ISDONE; 
		stopped = ISNOTHING;
		seeking = ISNOTHING;
	}
	protected void setIsStopping()
	{
		paused = ISNOTHING; 
		stopped = ISDOING;
		seeking = ISNOTHING;
	}
	protected void setIsStopped()
	{
		paused = ISNOTHING; 
		stopped = ISDONE;
		seeking = ISNOTHING;
	}
	protected void setIsPlaying()
	{
		paused = ISNOTHING; 
		stopped = ISNOTHING;
		seeking = ISNOTHING;
	}
	protected void setHasFinished()
	{
		hasFinished = true;
	}
	/**
	 * @param milliseconds
	 * @since 13.02.2012
	 */
	protected abstract void seek(long milliseconds);
	/**
	 * @return
	 * @since 13.02.2012
	 */
	protected long getSeekPosition()
	{
		return seekPosition;
	}
	public boolean isNotSeeking()
	{
		return seeking == ISNOTHING;
	}
	public boolean isInSeeking()
	{
		return seeking != ISNOTHING;
	}
	public boolean isSeeking()
	{
		return seeking == ISDOING;
	}
	public void setIsSeeking()
	{
		seeking = ISDONE;
	}
	/**
	 * @param milliseconds
	 * @see de.quippy.javamod.mixer.Mixer#setMillisecondPosition(long)
	 */
	@Override
	public void setMillisecondPosition(long milliseconds)
	{
		if (!isPlaying())
			seekPosition = milliseconds;
		else
		if (isNotSeeking())
		{
			try
			{
				// This triggers the playback routine in the respective Mixers to
				// stop playback and prepare seeking
				// ONLY difference is with midi, as there is no separate digital data send
				seeking = ISDOING;
				while (isSeeking()) try { Thread.sleep(10L); } catch (InterruptedException ex) { /*NOOP */ }
				stopLine(true);
				seek(milliseconds);
			}
			catch (Exception ex)
			{
				Log.error("BasicMixer::setMillisecondPosition", ex);
			}
			finally
			{
				startLine(false);
				seeking = ISNOTHING;
			}
		}
	}
	/**
	 * Stops the playback.
	 * Will wait until stop is done
	 * @since 22.06.2006
	 */
	@Override
	public void stopPlayback()
	{
		if (isNotStoppingNorStopped())
		{
			setIsStopping();
			while (!isStopped()) try { Thread.sleep(10L); } catch (InterruptedException ex) { /*noop*/ }
			stopLine(true);
		}
	}
	/**
	 * Halts / restarts the playback
	 * Will wait until playback halted
	 * @since 22.06.2006
	 */
	@Override
	public void pausePlayback()
	{
		if (isNotPausingNorPaused() && isNotStoppingNorStopped())
		{
			setIsPausing();
			while (!isPaused() && !isStopped()) try { Thread.sleep(10L); } catch (InterruptedException ex) { /*noop*/ }
			stopLine(true);
		}
		else
		if (isPaused())
		{
			startLine(false);
			setIsPlaying();
		}
	}
	/**
	 * @since 09.11.2019
	 * @return true, if a stop time code is set (>-1)
	 */
	public boolean hasStopPosition()
	{
		return stopPosition>-1;
	}
	/**
	 * @since 09.11.2019
	 * @return the current time code
	 */
	public long getStopPosition()
	{
		return stopPosition;
	}
	/**
	 * @since 09.11.2019
	 * @return true, if current time code is greater or equal to stop time code
	 */
	public boolean stopPositionIsReached()
	{
		return (hasStopPosition() && getMillisecondPosition()>=getStopPosition());
	}
	/**
	 * @since 15.12.2020
	 * @return the amount of milliseconds still to write or -1 if no stop position is set
	 */
	public long getMillisecondsToWriteLeft()
	{
		if (hasStopPosition())
			return getStopPosition() - getMillisecondPosition();
		else
			return -1;
	}
	/**
	 * @since 15.12.2020
	 * @return the amount of samples to write till stop position is reached or -1 if no stop position is set
	 */
	public long getSamplesToWriteLeft()
	{
		if (hasStopPosition())
			return getMillisecondsToWriteLeft() * (long)getCurrentSampleRate() / 1000L;
		else
			return -1;
	}
	/**
	 * @param milliseconds
	 * @see de.quippy.javamod.mixer.Mixer#setStopMillisecondPosition(long)
	 */
	@Override
	public void setStopMillisecondPosition(long milliseconds)
	{
		if (!isPlaying()) stopPosition = milliseconds;
	}
}
