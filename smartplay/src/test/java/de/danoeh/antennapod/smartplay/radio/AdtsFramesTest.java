package de.danoeh.antennapod.smartplay.radio;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

public class AdtsFramesTest {
    private static byte[] adts(int payload, boolean checksum) {
        int header = checksum ? 9 : 7;
        int length = header + payload;
        byte[] frame = new byte[length];
        frame[0] = (byte) 0xFF;
        frame[1] = (byte) (checksum ? 0xF0 : 0xF1);
        frame[2] = 0x4C;
        frame[3] = (byte) (0x80 | ((length >> 11) & 0x03));
        frame[4] = (byte) ((length >> 3) & 0xFF);
        frame[5] = (byte) (((length & 0x07) << 5) | 0x1F);
        frame[6] = (byte) 0xFC;
        for (int i = header; i < length; i++) {
            frame[i] = (byte) (i * 7);
        }
        return frame;
    }

    @Test
    public void bareFrameIsKeptWhole() {
        byte[] bare = {0x21, 0x10, 0x05, 0x40};
        List<int[]> frames = AdtsFrames.payloads(bare, bare.length);
        assertEquals(1, frames.size());
        assertArrayEquals(new int[] {0, 4}, frames.get(0));
    }

    @Test
    public void headerIsStripped() {
        byte[] frame = adts(300, false);
        List<int[]> frames = AdtsFrames.payloads(frame, frame.length);
        assertEquals(1, frames.size());
        assertArrayEquals(new int[] {7, 300}, frames.get(0));
    }

    @Test
    public void checksumHeaderIsStripped() {
        byte[] frame = adts(200, true);
        assertArrayEquals(new int[] {9, 200}, AdtsFrames.payloads(frame, frame.length).get(0));
    }

    @Test
    public void severalFramesAreSplit() {
        byte[] a = adts(100, false);
        byte[] b = adts(150, false);
        byte[] both = new byte[a.length + b.length];
        System.arraycopy(a, 0, both, 0, a.length);
        System.arraycopy(b, 0, both, a.length, b.length);
        List<int[]> frames = AdtsFrames.payloads(both, both.length);
        assertEquals(2, frames.size());
        assertArrayEquals(new int[] {7, 100}, frames.get(0));
        assertArrayEquals(new int[] {a.length + 7, 150}, frames.get(1));
    }

    @Test
    public void brokenLengthIsLeftAlone() {
        byte[] frame = adts(100, false);
        List<int[]> frames = AdtsFrames.payloads(frame, frame.length - 10);
        assertArrayEquals(new int[] {0, frame.length - 10}, frames.get(0));
    }
}
