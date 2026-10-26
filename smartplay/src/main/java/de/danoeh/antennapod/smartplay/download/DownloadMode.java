package de.danoeh.antennapod.smartplay.download;

public enum DownloadMode {
    MANUAL,
    LATEST,
    STREAM_LATEST,
    IN_ORDER,
    STREAM_IN_ORDER,
    BACKWARDS,
    IGNORE;

    public boolean downloads() {
        return this == LATEST || this == IN_ORDER || this == BACKWARDS;
    }

    public boolean streams() {
        return this == STREAM_LATEST || this == STREAM_IN_ORDER;
    }
}
