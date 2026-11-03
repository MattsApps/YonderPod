package de.danoeh.antennapod.smartplay.app;

import de.danoeh.antennapod.smartplay.worlds.SwipeFeel;

public final class SwipeDirection {
    public static final int UNDECIDED = 0;
    public static final int VERTICAL = 1;
    public static final int HORIZONTAL = 2;

    private SwipeDirection() {
    }

    public static int classify(float dx, float dy, int slop) {
        if (Math.hypot(dx, dy) <= slop) {
            return UNDECIDED;
        }
        double fromVertical = Math.toDegrees(Math.atan2(Math.abs(dx), Math.abs(dy)));
        return fromVertical <= SwipeFeel.VERTICAL_DEGREES ? VERTICAL : HORIZONTAL;
    }
}
