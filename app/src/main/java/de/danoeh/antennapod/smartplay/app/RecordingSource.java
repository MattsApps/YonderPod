package de.danoeh.antennapod.smartplay.app;

import android.media.MediaDataSource;

import java.io.IOException;

import de.danoeh.antennapod.smartplay.radio.RecordingCipher;

/** Lets Android's media readers read an encrypted recording, decrypting as they go (nothing is written). */
final class RecordingSource extends MediaDataSource {
    private final RecordingCipher.Reader reader;

    RecordingSource(RecordingCipher.Reader reader) {
        this.reader = reader;
    }

    @Override
    public int readAt(long position, byte[] buffer, int offset, int size) throws IOException {
        return reader.read(position, buffer, offset, size);
    }

    @Override
    public long getSize() throws IOException {
        return reader.length();
    }

    @Override
    public void close() {
        // The reader belongs to whoever opened it.
    }
}
