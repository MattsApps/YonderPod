package de.danoeh.antennapod.smartplay.radio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class Mp4AudioWriterTest {
    private static final byte[] CONFIG = {0x12, 0x10}; // AAC-LC, 44.1 kHz, stereo

    private static Mp4AudioWriter frames(int count, int size) {
        Mp4AudioWriter writer = new Mp4AudioWriter(44100, 2, CONFIG);
        for (int i = 0; i < count; i++) {
            writer.addSample(size + (i % 7), Math.round(i * 1024 * 1_000_000d / 44100));
        }
        return writer;
    }

    private static int find(byte[] data, String type) {
        byte[] wanted = type.getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i + 4 <= data.length; i++) {
            if (data[i] == wanted[0] && data[i + 1] == wanted[1] && data[i + 2] == wanted[2]
                    && data[i + 3] == wanted[3]) {
                return i - 4;
            }
        }
        return -1;
    }

    @Test
    public void indexComesFirstAndPointsAtTheAudio() throws IOException {
        Mp4AudioWriter writer = frames(2500, 300);
        byte[] header = writer.header();
        ByteBuffer buffer = ByteBuffer.wrap(header);
        assertEquals("ftyp", new String(header, 4, 4, StandardCharsets.US_ASCII));
        int ftypSize = buffer.getInt(0);
        assertEquals("moov", new String(header, ftypSize + 4, 4, StandardCharsets.US_ASCII));
        int moovSize = buffer.getInt(ftypSize);
        int mdat = ftypSize + moovSize;
        assertEquals(header.length - 8, mdat);
        assertEquals("mdat", new String(header, mdat + 4, 4, StandardCharsets.US_ASCII));
        assertEquals(writer.getAudioBytes() + 8, buffer.getInt(mdat) & 0xFFFFFFFFL);

        int stco = find(header, "stco");
        assertEquals(3, buffer.getInt(stco + 12)); // 2500 frames in chunks of 1000
        assertEquals(header.length, buffer.getInt(stco + 16));
        long second = header.length;
        for (int i = 0; i < 1000; i++) {
            second += writer.getSampleSize(i);
        }
        assertEquals(second, buffer.getInt(stco + 20) & 0xFFFFFFFFL);

        int stsc = find(header, "stsc");
        assertEquals(2, buffer.getInt(stsc + 12));
        assertEquals(3, buffer.getInt(stsc + 28));
        assertEquals(500, buffer.getInt(stsc + 32));

        int stsz = find(header, "stsz");
        assertEquals(2500, buffer.getInt(stsz + 16));
        assertEquals(300, buffer.getInt(stsz + 20));
    }

    @Test
    public void framesPlayStraightThroughAcrossAJumpInTheStreamClock() {
        Mp4AudioWriter writer = new Mp4AudioWriter(48000, 2, CONFIG);
        long time = 0;
        for (int i = 0; i < 100; i++) {
            if (i == 50) {
                time += 3_600_000_000L; // a reconnect
            }
            if (i == 70) {
                time -= 10_000_000L; // the clock goes backwards
            }
            writer.addSample(200, time);
            time += Math.round(1024 * 1_000_000d / 48000);
        }
        for (int duration : writer.durations()) {
            assertEquals(1024, duration);
        }
    }

    @Test
    public void durationIsTheAudiosLength() throws IOException {
        Mp4AudioWriter writer = frames(44100 * 60 / 1024, 300); // a minute
        byte[] header = writer.header();
        int mvhd = find(header, "mvhd");
        int ms = ByteBuffer.wrap(header).getInt(mvhd + 24);
        assertTrue("duration " + ms, Math.abs(ms - 60_000) < 50);
    }

    @Test(expected = IOException.class)
    public void noFramesIsAnError() throws IOException {
        new Mp4AudioWriter(44100, 2, CONFIG).header();
    }

    @Test
    public void aWholeFileCanBeBuilt() throws IOException {
        Mp4AudioWriter writer = frames(10, 100);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(writer.header());
        for (int i = 0; i < writer.getSampleCount(); i++) {
            out.write(new byte[writer.getSampleSize(i)]);
        }
        assertEquals(writer.header().length + writer.getAudioBytes(), out.size());
    }
}
