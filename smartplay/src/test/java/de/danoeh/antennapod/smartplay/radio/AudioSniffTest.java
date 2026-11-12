package de.danoeh.antennapod.smartplay.radio;

import static org.junit.Assert.assertEquals;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class AudioSniffTest {
    private static String sniff(byte[] data) {
        return AudioSniff.mimeType(data, data.length);
    }

    @Test
    public void transportStream() {
        byte[] data = new byte[400];
        data[0] = 0x47;
        data[188] = 0x47;
        assertEquals("video/mp2t", sniff(data));
    }

    @Test
    public void aacAndMp3FrameHeaders() {
        assertEquals("audio/aac", sniff(new byte[] {(byte) 0xFF, (byte) 0xF1, 0x50, (byte) 0x80}));
        assertEquals("audio/mpeg", sniff(new byte[] {(byte) 0xFF, (byte) 0xFB, (byte) 0x90, 0x64}));
    }

    @Test
    public void mp3AfterAnId3Tag() {
        byte[] data = new byte[40];
        System.arraycopy("ID3".getBytes(StandardCharsets.US_ASCII), 0, data, 0, 3);
        data[9] = 10; // a 10-byte tag
        data[20] = (byte) 0xFF;
        data[21] = (byte) 0xFB;
        assertEquals("audio/mpeg", sniff(data));
    }

    @Test
    public void fragmentedMp4AndOthers() {
        byte[] mp4 = new byte[16];
        System.arraycopy("ftyp".getBytes(StandardCharsets.US_ASCII), 0, mp4, 4, 4);
        assertEquals("audio/mp4", sniff(mp4));
        assertEquals("audio/ogg", sniff("OggS....".getBytes(StandardCharsets.US_ASCII)));
        assertEquals("audio/flac", sniff("fLaC....".getBytes(StandardCharsets.US_ASCII)));
    }
}
