package de.danoeh.antennapod.smartplay.radio;

import java.util.ArrayList;
import java.util.List;

/**
 * Android's MediaExtractor can hand over AAC from an HLS stream with each frame's 7 or 9-byte ADTS header
 * still on, sometimes several frames at once. An MP4 holds only the bare frames, one per sample, so this
 * finds them. Anything that isn't cleanly ADTS is returned whole, unchanged.
 */
public final class AdtsFrames {
    private AdtsFrames() {
    }

    /** The bare AAC frames in data[0..size), as {offset, length} pairs. */
    public static List<int[]> payloads(byte[] data, int size) {
        List<int[]> frames = new ArrayList<>();
        int at = 0;
        while (at < size) {
            if (size - at < 7 || (data[at] & 0xFF) != 0xFF || (data[at + 1] & 0xF6) != 0xF0) {
                return whole(size);
            }
            boolean noChecksum = (data[at + 1] & 0x01) == 1;
            int header = noChecksum ? 7 : 9;
            int length = ((data[at + 3] & 0x03) << 11) | ((data[at + 4] & 0xFF) << 3)
                    | ((data[at + 5] & 0xE0) >> 5);
            if (length <= header || at + length > size || (data[at + 6] & 0x03) != 0) {
                return whole(size);
            }
            frames.add(new int[] {at + header, length - header});
            at += length;
        }
        return frames.isEmpty() ? whole(size) : frames;
    }

    private static List<int[]> whole(int size) {
        List<int[]> frames = new ArrayList<>();
        frames.add(new int[] {0, size});
        return frames;
    }
}
