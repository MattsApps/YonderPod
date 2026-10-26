package de.danoeh.antennapod.storage.preferences;

public enum ButtonPress {
    SINGLE(ButtonAction.PLAY_PAUSE),
    DOUBLE(ButtonAction.SKIP_FORWARD),
    TRIPLE(ButtonAction.SKIP_BACK),
    LONG(ButtonAction.NEXT_EPISODE),
    NEXT(ButtonAction.SKIP_FORWARD),
    PREVIOUS(ButtonAction.SKIP_BACK);

    public final ButtonAction defaultAction;

    ButtonPress(ButtonAction defaultAction) {
        this.defaultAction = defaultAction;
    }
}
