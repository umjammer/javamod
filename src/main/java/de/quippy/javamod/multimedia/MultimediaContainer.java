/*
 * @(#)MultimediaContainer.java
 *
 * Created on 12.10.2007 by Daniel Becker
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
package de.quippy.javamod.multimedia;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Properties;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import de.quippy.javamod.mixer.Mixer;
import de.quippy.javamod.system.Helpers;

/**
 * @author: Daniel Becker
 * @since: 12.10.2007
 */
public abstract class MultimediaContainer
{
	private ArrayList<MultimediaContainerEventListener> listeners = new ArrayList<MultimediaContainerEventListener>();
	private URL fileURL = null;
	
	/**
	 * @since: 12.10.2007
	 */
	public MultimediaContainer()
	{
		super();
	}
	/**
	 * A default implementation. If you need a new instance,
	 * override this and do whatever is needed!
	 * @since 13.10.2007
	 * @return
	 */
	public MultimediaContainer getInstance(final URL url)
	{
		setFileURL(url);
		return this;
	}
	/**
	 * @since 13.10.2007
	 * @return
	 */
	public URL getFileURL()
	{
		return fileURL;
	}
	/**
	 * @since 19.12.2022
	 * @param url
	 */
	public void setFileURL(final URL url)
	{
		this.fileURL = url;
	}
	/**
	 * @since 23.12.2010
	 * @return a printable version of the URL
	 */
	public String getPrintableFileUrl()
	{
		return getPrintableFileUrl(getFileURL());
	}
	public String getPrintableFileUrl(final URL urlName)
	{
		if (urlName==null) return Helpers.EMPTY_STING;
		try
		{
			java.io.File f = new java.io.File(urlName.toURI());
			try
			{
				return f.getCanonicalPath();
			}
			catch (IOException ex)
			{
				return f.getAbsolutePath();
			}
		}
		catch (Throwable e)
		{
			return urlName.toExternalForm();
		}
	}
	public void updateLookAndFeel() 
	{
		JPanel infoPanel = getInfoPanel();
		JPanel configPanel = getConfigPanel();
		if (infoPanel!=null) SwingUtilities.updateComponentTreeUI(infoPanel); 
		if (configPanel!=null) SwingUtilities.updateComponentTreeUI(configPanel); 
	}
	public void addListener(final MultimediaContainerEventListener listener)
	{
		listeners.add(listener);
	}
	public void removeListener(final MultimediaContainerEventListener listener)
	{
		listeners.remove(listener);
	}
	protected void fireMultimediaContainerEvent(final MultimediaContainerEvent event)
	{
		for (int i=0; i<listeners.size(); i++)
			listeners.get(i).multimediaContainerEventOccured(event);
	}
	/**
	 * Return the name of the song
	 * @since 21.09.2008
	 * @return
	 */
	public String getSongName()
	{
		return MultimediaContainerManager.getSongNameFromURL(fileURL);
	}
	/**
	 * This method will only do (!)locally(!) what is needed to pick up
	 * the song name String at [0] and time in milliseconds as Long at [1]
	 * @since 12.02.2011
	 * @param url
	 * @return Object [] { String SongName, Long duration }
	 */
	public abstract Object [] getSongInfosFor(final URL url);
	/**
	 * Returns true if this mixers supports the export function
	 * @since 26.10.2007
	 * @return
	 */
	public abstract boolean canExport();
	/**
	 * Return the info dialog panel
	 * @since 13.10.2007
	 * @return
	 */
	public abstract JPanel getInfoPanel();
	/**
	 * Returns the config panel for this mixer
	 * @since 13.10.2007
	 * @return
	 */
	public abstract JPanel getConfigPanel();
	/**
	 * The file extensions this container is responsible for 
	 * @since: 12.10.2007
	 * @return
	 */
	public abstract String [] getFileExtensionList();
	/**
	 * A descriptive Name for e.g. a FileChooser
	 * @since 05.01.2008
	 * @return
	 */
	public abstract String getName();
	/**
	 * @since 13.10.2007
	 * @param newProps
	 */
	public abstract void configurationChanged(final Properties newProps);
	/**
	 * @since 14.10.2007
	 * @param props
	 */
	public abstract void configurationSave(final Properties props);
	/**
	 * Clean up
	 * @since 11.11.2023
	 */
	public abstract void cleanUp();
	/**
	 * Get the mixer of this container
	 * @since: 12.10.2007
	 * @return
	 */
	public abstract Mixer createNewMixer();
}
