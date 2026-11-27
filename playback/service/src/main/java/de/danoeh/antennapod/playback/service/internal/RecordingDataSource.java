package de.danoeh.antennapod.playback.service.internal;

import android.net.Uri;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSourceException;
import androidx.media3.datasource.DataSpec;
import de.danoeh.antennapod.playback.service.BuildConfig;
import de.danoeh.antennapod.smartplay.radio.RecordingCipher;
import de.danoeh.antennapod.smartplay.radio.RecordingKeys;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@OptIn(markerClass = UnstableApi.class)
public class RecordingDataSource extends BaseDataSource {
    private static final String TAG = "RecordingDataSource";
    @Nullable
    private RecordingCipher.Reader reader;
    @Nullable
    private Uri uri;
    private long position;
    private long remaining;
    private boolean opened;

    public RecordingDataSource() {
        super(false);
    }

    @Override
    public long open(@NonNull DataSpec dataSpec) throws IOException {
        uri = dataSpec.uri;
        transferInitializing(dataSpec);
        String path = dataSpec.uri.getPath();
        if (path == null) {
            throw new DataSourceException(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND);
        }
        try {
            reader = RecordingCipher.open(new File(path), RecordingKeys.get());
        } catch (IOException e) {
            throw new DataSourceException(e, PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
        }
        if (BuildConfig.DEBUG && dataSpec.position == 0) {
            Log.d(TAG, "Opened " + path + ": " + reader.length() + " bytes, " + describe(reader));
        }
        long available = reader.length() - dataSpec.position;
        if (available < 0) {
            throw new DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE);
        }
        position = dataSpec.position;
        remaining = dataSpec.length == C.LENGTH_UNSET ? available : Math.min(dataSpec.length, available);
        opened = true;
        transferStarted(dataSpec);
        return remaining;
    }

    /** The file's top-level MP4 boxes (type and size), or its first bytes if it isn't an MP4. No audio. */
    private static String describe(RecordingCipher.Reader reader) {
        StringBuilder text = new StringBuilder();
        try {
            byte[] start = new byte[16];
            int got = reader.read(0, start, 0, start.length);
            text.append("starts");
            for (int i = 0; i < Math.max(0, got); i++) {
                text.append(String.format(" %02x", start[i] & 0xFF));
            }
            long at = 0;
            byte[] header = new byte[8];
            for (int box = 0; box < 8 && at + 8 <= reader.length(); box++) {
                if (reader.read(at, header, 0, 8) < 8) {
                    break;
                }
                long size = ((header[0] & 0xFFL) << 24) | ((header[1] & 0xFFL) << 16)
                        | ((header[2] & 0xFFL) << 8) | (header[3] & 0xFFL);
                String type = new String(header, 4, 4, StandardCharsets.ISO_8859_1);
                text.append(" | ").append(type).append(' ').append(size).append(" at ").append(at);
                if ("mdat".equals(type)) {
                    byte[] first = new byte[12];
                    int read = reader.read(at + 8, first, 0, first.length);
                    text.append(", audio starts");
                    for (int i = 0; i < Math.max(0, read); i++) {
                        text.append(String.format(" %02x", first[i] & 0xFF));
                    }
                }
                if (size < 8) {
                    break;
                }
                at += size;
            }
        } catch (IOException e) {
            text.append(" (").append(e.getMessage()).append(')');
        }
        return text.toString();
    }

    @Override
    public int read(@NonNull byte[] buffer, int offset, int length) throws IOException {
        if (length == 0) {
            return 0;
        }
        if (remaining == 0 || reader == null) {
            return C.RESULT_END_OF_INPUT;
        }
        int read = reader.read(position, buffer, offset, (int) Math.min(length, remaining));
        if (read <= 0) {
            return C.RESULT_END_OF_INPUT;
        }
        position += read;
        remaining -= read;
        bytesTransferred(read);
        return read;
    }

    @Nullable
    @Override
    public Uri getUri() {
        return uri;
    }

    @Override
    public void close() throws IOException {
        uri = null;
        try {
            if (reader != null) {
                reader.close();
            }
        } finally {
            reader = null;
            if (opened) {
                opened = false;
                transferEnded();
            }
        }
    }
}
