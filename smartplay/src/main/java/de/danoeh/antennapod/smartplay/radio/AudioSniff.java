package de.danoeh.antennapod.smartplay.radio;

/**
 * Works out what kind of audio a recording holds from its first few kilobytes. A recording is finished
 * from what reached the file, sometimes after Yonder was closed part-way, so this is read from the audio
 * itself rather than from what the stream said it was.
 */
public final class AudioSniff {
    public static final int BYTES_NEEDED = 4096;

    private AudioSniff() {
    }

    public static String mimeType(byte[] data, int length) {
        int start = 0;
        if (length >= 10 && data[0] == 'I' && data[1] == 'D' && data[2] == '3') {
            int tag = ((data[6] & 0x7F) << 21) | ((data[7] & 0x7F) << 14) | ((data[8] & 0x7F) << 7) | (data[9] & 0x7F);
            start = 10 + tag;
            if (start + 2 > length) {
                return "audio/mpeg";
            }
        }
        if (start == 0) {
            if (length >= 189 && (data[0] & 0xFF) == 0x47 && (data[188] & 0xFF) == 0x47) {
                return "video/mp2t";
            }
            if (length >= 8 && (is(data, 4, "ftyp") || is(data, 4, "styp") || is(data, 4, "moof")
                    || is(data, 4, "moov"))) {
                return "audio/mp4";
            }
            if (length >= 4 && is(data, 0, "OggS")) {
                return "audio/ogg";
            }
            if (length >= 4 && is(data, 0, "fLaC")) {
                return "audio/flac";
            }
        }
        for (int i = start; i + 1 < length; i++) {
            if ((data[i] & 0xFF) == 0xFF && (data[i + 1] & 0xF0) == 0xF0) {
                // The layer bits are 00 for AAC (ADTS) and never 00 for MP3.
                return (data[i + 1] & 0x06) == 0 ? "audio/aac" : "audio/mpeg";
            }
        }
        return "audio/mpeg";
    }

    private static boolean is(byte[] data, int at, String text) {
        for (int i = 0; i < text.length(); i++) {
            if (data[at + i] != text.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
