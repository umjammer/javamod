/*
 * Copyright (c) 2024 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Predicate;

import de.quippy.javamod.io.SoundOutputStreamImpl;
import de.quippy.javamod.main.JavaMod;
import de.quippy.javamod.main.gui.PlayThread;
import de.quippy.javamod.mixer.Mixer;
import de.quippy.javamod.multimedia.MultimediaContainer;
import de.quippy.javamod.multimedia.MultimediaContainerManager;
import vavi.util.Debug;
import vavi.util.properties.annotation.Property;
import vavi.util.properties.annotation.PropsEntity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static vavix.util.DelayedWorker.later;


/**
 * TestCase.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2024-08-27 nsano initial version <br>
 */
@PropsEntity(url = "file:local.properties")
class TestCase {

    static boolean localPropertiesExists() {
        return Files.exists(Paths.get("local.properties"));
    }

    @Property(name = "vavi.test.volume")
    float volume = 0.2f;

    @Property
    String mod = "src/test/resources/test.mod";

    @Property
    String dir;

    @Property
    String ext;

    static boolean onIde = System.getProperty("vavi.test", "").equals("ide");
    static long time = onIde ? 1000 * 1000 : 9 * 1000;

    @BeforeEach
    void setup() throws Exception {
        if (localPropertiesExists()) {
            PropsEntity.Util.bind(this);
        }

        System.setProperty("libgme.endless", "false" /*String.valueOf(onIde)*/);

Debug.println("volume: " + volume + ", vgm.endless: " + System.getProperty("libgme.endless"));
    }

    @Test
    @DisplayName("gui")
    @EnabledIfSystemProperty(named = "vavi.ide", matches = "ide")
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

        PlayThread playerThread = new PlayThread(mixer, thread -> { // TODO event does not work well
Debug.println("event: " + thread);
            if (thread.getCurrentMixer().hasFinished()) {
                cdl.countDown();
            }
        });
        playerThread.start();

        if (!onIde) later(time, cdl::countDown);
        cdl.await();
    }

    @Test
    @DisplayName("headless and thread-less")
    @EnabledIfSystemProperty(named = "vavi.test", matches = "ide")
    void test3() throws Exception {
        MultimediaContainerManager.setIsHeadlessMode(true);
        MultimediaContainer container = MultimediaContainerManager.getMultimediaContainer(mod);
        Mixer mixer = container.createNewMixer();
        mixer.setSoundOutputStream(new SoundOutputStreamImpl());
        mixer.setVolume(volume);
Debug.println("sampleRate: " + mixer.getCurrentSampleRate() + ", channels: " + mixer.getChannelCount());
        mixer.startPlayback();
    }

    /**
     * @param dir separated by ';'
     * @param ext separated by ','
     */
    static List<Path> listFilesUnderDirFilteredByExt(String dir, String ext) {
//Debug.println("dir: " + dir);
//Debug.println("ext: " + ext);
        Predicate<Path> x = p -> Arrays.stream(ext.split(",")).anyMatch(e -> p.getFileName().toString().toLowerCase().endsWith(e));
        return Arrays.stream(dir.split(File.pathSeparator)).flatMap(d -> {
            try {
                return Files.walk(Paths.get(d)).filter(x);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }).toList();
    }

    @Test
    @DisplayName("play random one in the dir filtered by ext")
    @EnabledIfSystemProperty(named = "vavi.test", matches = "ide")
    void test4() throws Exception {
        listFilesUnderDirFilteredByExt(dir, ext).stream()
                .sorted((a, b) -> Math.random() < 0.5 ? -1 : 1)
                .forEach(p -> {
Debug.print(p);
            try {
                MultimediaContainerManager.setIsHeadlessMode(true);
                MultimediaContainer container = MultimediaContainerManager.getMultimediaContainer(p.toString());
                Mixer mixer = container.createNewMixer();
                mixer.setSoundOutputStream(new SoundOutputStreamImpl());
                mixer.setVolume(volume);
                mixer.startPlayback();
            } catch (Exception _) {
            }
        });
    }
}
