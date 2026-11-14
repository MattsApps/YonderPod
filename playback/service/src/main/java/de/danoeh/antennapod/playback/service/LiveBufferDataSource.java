package de.danoeh.antennapod.playback.service;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSourceException;
import androidx.media3.datasource.DataSpec;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;

import de.danoeh.antennapod.smartplay.radio.RecordingCipher;

/**
 * Plays the live buffer: reads its chunks in order through the cipher (nothing decrypted is written), from
 * the moment in the URI onwards, waiting for more while the buffer is still recording. It never ends while
 * the buffer runs, like the live stream itself.
 */
@OptIn(markerClass = UnstableApi.class)
public class LiveBufferDataSource extends BaseDataSource {
    public static final String SCHEME = "yonderlive";

    @Nullable
    private Uri uri;
    @Nullable
    private LiveBuffer buffer;
    @Nullable
    private RecordingCipher.Reader reader;
    private long wanted;
    private long chunk = -1;
    private long offset;
    private boolean opened;

    public LiveBufferDataSource() {
        super(false);
    }

    /** A player address for the buffer, starting at the audio that arrived at time. */
    public static Uri uriFrom(long time) {
        return Uri.parse(SCHEME + "://buffer/" + time);
    }

    public static boolean isBuffer(@Nullable Uri address) {
        return address != null && SCHEME.equals(address.getScheme());
    }

    @Override
    public long open(@NonNull DataSpec dataSpec) throws IOException {
        uri = dataSpec.uri;
        transferInitializing(dataSpec);
        buffer = LiveBuffer.current();
        if (buffer == null || buffer.isStopped()) {
            throw new DataSourceException(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND);
        }
        try {
            wanted = Long.parseLong(dataSpec.uri.getLastPathSegment());
        } catch (NumberFormatException e) {
            throw new DataSourceException(e, PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND);
        }
        chunk = -1;
        offset = 0;
        opened = true;
        transferStarted(dataSpec);
        // A retry after an error reopens part-way through: skip what was already read.
        byte[] scratch = new byte[16 * 1024];
        long skip = dataSpec.position;
        while (skip > 0) {
            int read = next(scratch, 0, (int) Math.min(skip, scratch.length));
            if (read < 0) {
                throw new EOFException();
            }
            skip -= read;
        }
        return C.LENGTH_UNSET;
    }

    @Override
    public int read(@NonNull byte[] target, int targetOffset, int length) throws IOException {
        if (length == 0) {
            return 0;
        }
        int read = next(target, targetOffset, length);
        if (read < 0) {
            return C.RESULT_END_OF_INPUT;
        }
        bytesTransferred(read);
        return read;
    }

    private int next(byte[] target, int targetOffset, int length) throws IOException {
        LiveBuffer source = buffer;
        if (source == null) {
            return -1;
        }
        while (true) {
            if (source.isStopped()) {
                return -1;
            }
            if (chunk < 0) {
                long[] at = source.locate(wanted);
                if (at == null) {
                    pause();
                    continue;
                }
                chunk = at[0];
                offset = at[1];
            }
            if (reader == null) {
                File file = source.fileOf(chunk);
                if (file == null) {
                    long after = source.nextAfter(chunk);
                    if (after < 0) {
                        pause();
                        continue;
                    }
                    chunk = after;
                    offset = 0;
                    continue;
                }
                reader = LiveBuffer.open(file);
            }
            int read = reader.read(offset, target, targetOffset, length);
            if (read > 0) {
                offset += read;
                return read;
            }
            long after = source.nextAfter(chunk);
            if (after >= 0 && offset >= reader.length()) {
                // The writer has moved on, so this chunk is complete and all of it has been read.
                closeReader();
                chunk = after;
                offset = 0;
                continue;
            }
            if (after < 0) {
                pause();
            }
        }
    }

    private static void pause() throws InterruptedIOException {
        try {
            Thread.sleep(LiveBuffer.waitMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException();
        }
    }

    private void closeReader() throws IOException {
        if (reader != null) {
            try {
                reader.close();
            } finally {
                reader = null;
            }
        }
    }

    @Nullable
    @Override
    public Uri getUri() {
        return uri;
    }

    @Override
    public void close() throws IOException {
        uri = null;
        buffer = null;
        try {
            closeReader();
        } finally {
            if (opened) {
                opened = false;
                transferEnded();
            }
        }
    }
}
