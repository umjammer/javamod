/*
 * Copyright (c) 2024 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.sampled.mod;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;

import davaguine.jmac.spi.APEEncoding;
import de.quippy.javamod.mixer.Mixer;
import de.quippy.javamod.multimedia.MultimediaContainer;
import de.quippy.javamod.multimedia.MultimediaContainerManager;
import de.quippy.javamod.multimedia.mod.ModContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vavi.util.Debug;
import vavi.util.properties.annotation.Property;
import vavi.util.properties.annotation.PropsEntity;

import static de.quippy.javamod.io.SpiModfileInputStream.MAX_BUFFER_SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vavi.sound.SoundUtil.volume;
import static vavix.util.DelayedWorker.later;


/**
 * TestCase.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2024/11/24 umjammer initial version <br>
 */
@PropsEntity(url = "file:local.properties")
class TestCase {

    static boolean localPropertiesExists() {
        return Files.exists(Paths.get("local.properties"));
    }

    @BeforeEach
    void setup() throws Exception {
        if (localPropertiesExists()) {
            PropsEntity.Util.bind(this);
        }
Debug.println("volume: " + volume);
    }

    static boolean onIde = System.getProperty("vavi.test", "").equals("ide");
    static long time = onIde ? 1000 * 1000 : 9 * 1000;

    @Property(name = "vavi.test.volume")
    double volume = 0.2;

    @Property
    String mod = "src/test/resources/test.mod";

    @Test
    @Disabled("for creating proto type")
    @DisplayName("output to stream")
    void testP0() throws Exception {
        MultimediaContainerManager.setIsHeadlessMode(true);
        MultimediaContainer container = MultimediaContainerManager.getMultimediaContainer(mod);
        Mixer mixer = container.createNewMixer();
        mixer.setSoundOutputStream(new ModSoundOutputStream());
        mixer.startPlayback();
    }

    @Test
    @Disabled("for creating proto type")
    @DisplayName("container direct")
    void testP1() throws Exception {
        ModContainer container = new ModContainer();
        container.setInputStream(new BufferedInputStream(Files.newInputStream(Path.of(mod)), MAX_BUFFER_SIZE));
        Mixer mixer = container.createNewMixer();
        mixer.setSoundOutputStream(new ModSoundOutputStream());
        mixer.startPlayback();
    }

    @Test
    @DisplayName("directly")
    void test0() throws Exception {

        Path path = Paths.get(mod);
        AudioInputStream sourceAis = new ModAudioFileReader().getAudioInputStream(new BufferedInputStream(Files.newInputStream(path), MAX_BUFFER_SIZE));

        AudioFormat inAudioFormat = sourceAis.getFormat();
Debug.println("IN: " + inAudioFormat);
        AudioFormat outAudioFormat = new AudioFormat(
                inAudioFormat.getSampleRate(),
                16,
                inAudioFormat.getChannels(),
                true,
                inAudioFormat.isBigEndian());
Debug.println("OUT: " + outAudioFormat);

        assertTrue(AudioSystem.isConversionSupported(outAudioFormat, inAudioFormat));

        AudioInputStream pcmAis = new ModFormatConversionProvider().getAudioInputStream(outAudioFormat, sourceAis);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, pcmAis.getFormat());
        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
        line.open(pcmAis.getFormat());
        line.addLineListener(ev -> Debug.println(ev.getType()));
        line.start();

        volume(line, volume);

        byte[] buf = new byte[line.getBufferSize()];
        while (!later(time).come()) {
            int r = pcmAis.read(buf, 0, buf.length);
            if (r < 0) {
                break;
            }
            line.write(buf, 0, r);
        }
        line.drain();
        line.stop();
        line.close();
    }

    @Test
    @DisplayName("as spi")
    void test1() throws Exception {

        Path path = Paths.get(mod);
        AudioInputStream sourceAis = AudioSystem.getAudioInputStream(new BufferedInputStream(Files.newInputStream(path), MAX_BUFFER_SIZE));

        AudioFormat inAudioFormat = sourceAis.getFormat();
Debug.println("IN: " + inAudioFormat);
        AudioFormat outAudioFormat = new AudioFormat(
                inAudioFormat.getSampleRate(),
                16,
                inAudioFormat.getChannels(),
                true,
                inAudioFormat.isBigEndian());
Debug.println("OUT: " + outAudioFormat);

        assertTrue(AudioSystem.isConversionSupported(outAudioFormat, inAudioFormat));

        AudioInputStream pcmAis = AudioSystem.getAudioInputStream(outAudioFormat, sourceAis);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, pcmAis.getFormat());
        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
        line.open(pcmAis.getFormat());
        line.addLineListener(ev -> Debug.println(ev.getType()));
        line.start();

        volume(line, volume);

        byte[] buf = new byte[line.getBufferSize()];
        while (!later(time).come()) {
            int r = pcmAis.read(buf, 0, buf.length);
            if (r < 0) {
                break;
            }
            line.write(buf, 0, r);
        }
        line.drain();
        line.stop();
        line.close();
    }

    @Test
    @DisplayName("another input type 2")
    void test2() throws Exception {
        URL url = Paths.get(mod).toUri().toURL();
        AudioInputStream ais = AudioSystem.getAudioInputStream(url);
        assertEquals(ModEncoding.MOD, ais.getFormat().getEncoding());
    }

    @Test
    @DisplayName("another input type 3")
    void test3() throws Exception {
        File file = Paths.get(mod).toFile();
        AudioInputStream ais = AudioSystem.getAudioInputStream(file);
        assertEquals(ModEncoding.MOD, ais.getFormat().getEncoding());
    }

    @Test
    @DisplayName("when unsupported file coming")
    void test5() throws Exception {
        InputStream is = TestCase.class.getResourceAsStream("/test.caf");
        int available = is.available();
        UnsupportedAudioFileException e = assertThrows(UnsupportedAudioFileException.class, () -> AudioSystem.getAudioInputStream(is));
Debug.println(e.getMessage() + ", " + is);
Debug.printStackTrace(e);
        assertEquals(available, is.available()); // spi must not consume input stream even one byte
    }

    @Test
    @DisplayName("clip")
    void test4() throws Exception {

        AudioInputStream ais = AudioSystem.getAudioInputStream(Paths.get(mod).toFile());
        AudioFormat inFormat = ais.getFormat();
Debug.println(ais.getFormat());

        Clip clip = AudioSystem.getClip();
        CountDownLatch cdl = new CountDownLatch(1);
        clip.addLineListener(ev -> {
Debug.println(ev.getType());
            if (ev.getType() == LineEvent.Type.STOP)
                cdl.countDown();
        });
        clip.open(AudioSystem.getAudioInputStream(new AudioFormat(inFormat.getSampleRate(), 16, inFormat.getChannels(), true, inFormat.isBigEndian()), ais));
        volume(clip, volume);
        clip.start();
        if (!onIde) {
            Thread.sleep(time);
            clip.stop();
Debug.println("Interrupt");
        } else {
            cdl.await();
        }
        clip.drain();
        clip.stop();
        clip.close();
    }
}
