package de.danoeh.antennapod.playback.service.internal;

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
import de.danoeh.antennapod.smartplay.radio.RecordingCipher;
import de.danoeh.antennapod.smartplay.radio.RecordingKeys;

import java.io.File;
import java.io.IOException;

@OptIn(markerClass = UnstableApi.class)
public class RecordingDataSource extends BaseDataSource {
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
