/*
 * https://claude.ai/chat/df464c83-d2c8-44e2-a8f3-6eb7f2ffeff0
 */

package vavi.sound.sampled.mod;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import de.quippy.javamod.io.SpiModfileInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


/**
 * SpiModfileInputStreamTest.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2024/11/25 umjammer initial version <br>
 */
public class SpiModfileInputStreamTest {

    private byte[] testData;
    private SpiModfileInputStream is;
    private static final int TEST_DATA_SIZE = 1000;

    @BeforeEach
    public void setUp() throws IOException {
        // Prepare test data
        testData = new byte[TEST_DATA_SIZE];
        for (int i = 0; i < TEST_DATA_SIZE; i++) {
            testData[i] = (byte) (i % 256);
        }
        ByteArrayInputStream bis = new ByteArrayInputStream(testData);
        is = new SpiModfileInputStream(bis);
    }

    @Test
    public void testBasicReading() throws Exception {
        // Test single byte read
        assertEquals(testData[0] & 0xFF, is.read(), "First byte should match");

        // Test multi-byte read
        byte[] buffer = new byte[100];
        int bytesRead = is.read(buffer, 0, buffer.length);
        assertEquals(100, bytesRead, "Should read requested number of bytes");
        for (int i = 0; i < bytesRead; i++) {
            assertEquals(testData[i + 1], buffer[i], "Byte at position " + i + " should match");
        }
    }

    @Test
    public void testAvailableAndLength() throws IOException {
        assertEquals(TEST_DATA_SIZE, is.available(), "Initial available bytes should match total length");
        assertEquals(TEST_DATA_SIZE, is.length(), "Length should match test data size");

        // Read some data
        is.skip(100);
        assertEquals(TEST_DATA_SIZE - 100, is.available(), "Available should decrease after reading");
    }

    @Test
    public void testFilePointer() throws IOException {
        assertEquals(0, is.getFilePointer(), "Initial position should be 0");

        // Read some data
        is.skip(50);
        assertEquals(50, is.getFilePointer(), "Position should match bytes read");

        is.read();
        assertEquals(51, is.getFilePointer(), "Position should increase after reading one byte");
    }

    @Test
    public void testSeek() throws IOException {
        // Seek to position
        int seekPosition = 100;
        is.seek(seekPosition);
        assertEquals(seekPosition, is.getFilePointer(), "Position should increase after reading one byte");

        // Verify data after seeking
        int value = is.read();
        assertEquals(testData[seekPosition] & 0xFF, value, "Value after seek should match expected data");
    }

    @Test
    public void testSkip() throws IOException {
        long skipped = is.skip(50);
        assertEquals(50, skipped, "Should skip requested number of bytes");
        assertEquals(50, is.getFilePointer(), "Position should match skipped bytes");

        // Verify data after skipping
        int value = is.read();
        assertEquals(testData[50] & 0xFF, value, "Value after skip should match expected data");
    }

    @Test
    public void testConstructorWithNonMarkableStream() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> {
            // Create a non-markable stream
            InputStream nonMarkableStream = new InputStream() {
                @Override public int read() {
                    return 0;
                }

                @Override public boolean markSupported() {
                    return false;
                }
            };

            new SpiModfileInputStream(nonMarkableStream);
        });
    }
}