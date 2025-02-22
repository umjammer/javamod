/*
 * @(#) ModuleFactory.java
 *
 * Created on 21.04.2006 by Daniel Becker
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

package de.quippy.javamod.multimedia.mod.loader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

import de.quippy.javamod.io.ModfileInputStream;

import static java.lang.System.getLogger;


/**
 * Returns the appropriate ModuleClass for the desired ModFile
 *
 * @author Daniel Becker
 * @since 21.04.2006
 */
public class ModuleFactory {

    private static final Logger logger = getLogger(ModuleFactory.class.getName());

    private static Map<String, Module> fileExtensionMap;
    private static ServiceLoader<Module> modules;

    static {
        for (Module mod : getModules()) {
            String[] extensions = mod.getFileExtensionList();
            for (String extension : extensions) getFileExtensionMap().put(extension, mod);
        }
    }

    /**
     * Constructor for ModuleFactory - This Class Is A Singleton
     */
    private ModuleFactory() {
        super();
    }

    /**
     * Lazy instantiation access method
     *
     * @since 04.01.2010
     */
    private static Map<String, Module> getFileExtensionMap() {
        if (fileExtensionMap == null)
            fileExtensionMap = new HashMap<>();

        return fileExtensionMap;
    }

    /**
     * Lazy instantiation access method
     *
     * @since 04.01.2010
     */
    private static ServiceLoader<Module> getModules() {
        if (modules == null) {
            modules = ServiceLoader.load(Module.class);
        }
        return modules;
    }

    public static String[] getSupportedFileExtensions() {
        Set<String> keys = getFileExtensionMap().keySet();
        String[] result = new String[keys.size()];
        return keys.toArray(result);
    }

    public static Module getModuleFromExtension(String extension) {
        return getFileExtensionMap().get(extension.toLowerCase());
    }

    /**
     * Finds the appropriate loader through the IDs
     *
     * @since 04.01.2010
     */
    private static Module getModuleFromStreamByID(ModfileInputStream input) {
        for (Module mod : getModules()) {
            try {
                if (mod.checkLoadingPossible(input)) return mod;
            } catch (IOException ex) {
                /* Ignoring */
            }
        }
        return null;
    }

    /**
     * factory for javax.sound.spi
     * @throws IllegalArgumentException no suitable module for the inout stream
     * @throws IllegalArgumentException mark must be supported
     * @since 3.9.6
     */
    public static Module getModuleFromStream(InputStream input) {
        if (!input.markSupported()) {
            throw new IllegalArgumentException("mark must be supported");
        }
        for (Module mod : getModules()) {
            try {
                input.mark(8192);
                if (mod.checkLoadingPossible(input)) return mod;
            } catch (IOException ex) {
                /* Ignoring */
            } finally {
                try {
                    input.reset();
                } catch (IOException e) {
logger.log(Level.TRACE, e);
                }
            }
        }
        throw new IllegalArgumentException("no suitable module for the input stream");
    }

    /**
     * Finds the appropriate loader through simply loading it!
     *
     * @since 13.06.2010
     */
    private static Module getModuleFromStream(ModfileInputStream input) {
        for (Module mod : getModules()) {
            try {
                mod.loadModFile(input);
                input.seek(0);
                return mod; // <-- here this loading was a success!
            } catch (Throwable ignore) {
            }
        }
        return null;
    }

    /**
     * Uses the File-Extension to find a suitable loader.
     *
     * @param fileName The Filename of the mod
     * @return null, if fails
     */
    public static Module getInstance(String fileName) throws IOException {
        return getInstance(new File(fileName));
    }

    /**
     * Uses the File-Extension to find a suitable loader.
     *
     * @param file The File-Instance of the modfile
     * @return null, if fails
     */
    public static Module getInstance(File file) throws IOException {
        return getInstance(file.toURI().toURL());
    }

    /**
     * Uses the File-Extension to find a suitable loader.
     *
     * @param url URL-Instance of the path to the modfile
     * @return null, if fails
     */
    public static Module getInstance(URL url) throws IOException {
        ModfileInputStream inputStream = null;
        try {
            inputStream = new ModfileInputStream(url);
            Module mod = getModuleFromStreamByID(inputStream);
            // If the header gives no infos, it's obviously a Noise Tracker file
            // So let's try all loaders
            if (mod != null) {
                mod.loadModFile(inputStream);
                return mod;
            } else {
                mod = getModuleFromStream(inputStream);
                if (mod != null)
                    return mod;
                else
                    throw new IOException("Unsupported MOD-Type: " + inputStream.getFileName());
            }
        } catch (Throwable ex) {
            throw new IOException("[ModuleFactory] Failed with loading of " + url.toString(), ex);
        } finally {
            if (inputStream != null) try {
                inputStream.close();
            } catch (IOException ex) { /* logger.log(Level.ERROR, "IGNORED", ex); */ }
        }
    }
}
