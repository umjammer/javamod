/*
 * Copyright (c) 2024 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;

import de.quippy.javamod.io.SoundOutputStreamImpl;
import de.quippy.javamod.main.JavaMod;
import de.quippy.javamod.main.gui.PlayThread;
import de.quippy.javamod.mixer.Mixer;
import de.quippy.javamod.multimedia.MultimediaContainer;
import de.quippy.javamod.multimedia.MultimediaContainerManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import vavi.util.Debug;
import vavi.util.properties.annotation.Property;
import vavi.util.properties.annotation.PropsEntity;


/**
 * TestCase.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2024-08-27 nsano initial version <br>
 */
@PropsEntity(url = "file:local.properties")
@EnabledIf("localPropertiesExists")
class TestCase {

    static boolean localPropertiesExists() {
        return Files.exists(Paths.get("local.properties"));
    }

    @Property(name = "vavi.test.volume")
    float volume = 0.2f;

    @Property
    String mod;

    @BeforeEach
    void setup() throws Exception {
        if (localPropertiesExists()) {
            PropsEntity.Util.bind(this);
        }
Debug.println("volume: " + volume);
    }

    @Test
    @DisplayName("gui")
    void test1() throws Exception {
        JavaMod.main(new String[] {mod});

        CountDownLatch cdl = new CountDownLatch(1);
        cdl.await();
    }

    @Test
    @DisplayName("headless")
    void test2() throws Exception {
        MultimediaContainerManager.setIsHeadlessMode(true);
        MultimediaContainer container = MultimediaContainerManager.getMultimediaContainer(mod);
        Mixer mixer = container.createNewMixer();
        mixer.setSoundOutputStream(new SoundOutputStreamImpl());
        mixer.setVolume(volume);

        CountDownLatch cdl = new CountDownLatch(1);

        PlayThread playerThread = new PlayThread(mixer, thread -> { // TODO event not worked well
Debug.println(thread);
            if (thread.getCurrentMixer().hasFinished()) {
                cdl.countDown();
            }
        }); // TODO extract player from thread
        playerThread.start();

        cdl.await();
    }
}
