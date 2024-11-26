/*
 * Copyright (c) 2024 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package de.quippy.javamod.multimedia;

import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;

import de.quippy.javamod.mixer.Mixer;

import static java.lang.System.getLogger;


/**
 * SpiMultimediaContainer.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2024-11-26 nsano initial version <br>
 * @since 3.9.7
 */
public interface SpiMultimediaContainer {

    Logger logger = getLogger(SpiMultimediaContainer.class.getName());

    /**
     * must be implement mark/reset inside this method
     * @param stream mark mast be supported
     */
    boolean isSupported(InputStream stream);

    /**
     * for javax.sound.spi
     */
    void setInputStream(InputStream stream) throws IOException;

    /**
     * Get the mixer of this container
     */
    Mixer createNewMixer();

    /** spi */
    ServiceLoader<SpiMultimediaContainer> containers = ServiceLoader.load(SpiMultimediaContainer.class);

    /**
     * factory.
     * @throws java.util.NoSuchElementException when suitable container is not found
     */
    static Mixer factory(InputStream stream) throws IOException {
        SpiMultimediaContainer container = containers.stream().map(Provider::get).filter(c -> c.isSupported(stream)).findFirst().orElseThrow();
logger.log(Level.DEBUG, "container: " + container.getClass().getName());
        container.setInputStream(stream);
        return container.createNewMixer();
    }
}
