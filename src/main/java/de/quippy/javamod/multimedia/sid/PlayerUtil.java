/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package de.quippy.javamod.multimedia.sid;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import libsidplay.common.CPUClock;
import libsidplay.common.EventScheduler;
import libsidplay.config.IAudioSection;
import libsidplay.config.IConfig;
import libsidplay.sidtune.SidTune;
import sidplay.Player;
import sidplay.audio.AudioConfig;
import sidplay.audio.AudioDriver;
import sidplay.player.PlayList;


/**
 * PlayerUtil.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-06-30 nsano initial version <br>
 */
final class PlayerUtil {

    private static final Logger logger = System.getLogger(PlayerUtil.class.getName());

    /**
     * Invokes {@code sidplay.Player#open()} on the current thread. That method and the
     * {@code playList} field it dereferences are private, and {@link Player} is a
     * published dependency, so they are reached reflectively. Everything else in the
     * start/clock/teardown sequence uses public API.
     */
    static void playerOpen(Player sidPlayer, IConfig sidConfig, SidTune sidTune) throws IOException {
        try {
            Field playListField = Player.class.getDeclaredField("playList");
            playListField.setAccessible(true);
            playListField.set(sidPlayer, new PlayList(sidConfig, sidTune, false));

            Method open = Player.class.getDeclaredMethod("open");
            open.setAccessible(true);
            open.invoke(sidPlayer);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            throw new IOException(cause != null ? cause : e);
        } catch (ReflectiveOperationException e) {
            throw new IOException(e);
        }
    }

    /** Invokes the private {@code sidplay.Player#close()} reflectively. */
    static void playerClose(Player sidPlayer) {
        try {
            Method close = Player.class.getDeclaredMethod("close");
            close.setAccessible(true);
            close.invoke(sidPlayer);
        } catch (ReflectiveOperationException e) {
            logger.log(Level.ERROR, "[SIDMixer] close", e);
        }
    }

    /**
     * Passive {@link AudioDriver} acting as a pure sample latch. The reSID mixer requires
     * <em>some</em> {@link AudioDriver} to render into and signals a full buffer by calling
     * {@link #write()}; here that only stashes the rendered little-endian 16-bit PCM into
     * an internal accumulator &mdash; it performs no I/O and no callbacks. The decoding
     * loop pulls the accumulated bytes with {@link #available()} / {@link #drain()}.
     */
    static class PcmSink implements AudioDriver {

        private ByteBuffer sampleBuffer;
        /** rendered PCM not yet pulled */
        private byte[] pending = new byte[0];
        private int pendingLen;

        @Override
        public void open(IAudioSection audioSection, String recordingFilename, CPUClock cpuClock, EventScheduler context) {
            AudioConfig cfg = new AudioConfig(audioSection);
            sampleBuffer = ByteBuffer.allocate(cfg.getChunkFrames() * Short.BYTES * cfg.getChannels())
                    .order(ByteOrder.LITTLE_ENDIAN);
        }

        /** Called by the reSID mixer when {@link #sampleBuffer} is full: just stash it. */
        @Override
        public void write() {
            int len = sampleBuffer.position();
            if (pendingLen + len > pending.length) {
                pending = Arrays.copyOf(pending, Math.max(pendingLen + len, pending.length * 2));
            }
            System.arraycopy(sampleBuffer.array(), 0, pending, pendingLen, len);
            pendingLen += len;
        }

        /** Number of rendered PCM bytes waiting to be pulled. */
        int available() {
            return pendingLen;
        }

        /** Removes and returns all rendered PCM bytes accumulated so far. */
        byte[] drain() {
            byte[] chunk = Arrays.copyOf(pending, pendingLen);
            pendingLen = 0;
            return chunk;
        }

        @Override
        public void close() {
        }

        @Override
        public ByteBuffer buffer() {
            return sampleBuffer;
        }

        @Override
        public boolean isRecording() {
            return false;
        }
    }
}
