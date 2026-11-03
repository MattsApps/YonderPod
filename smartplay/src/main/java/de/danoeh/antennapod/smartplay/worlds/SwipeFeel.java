package de.danoeh.antennapod.smartplay.worlds;

public final class SwipeFeel {
    public static final float DRAG_FRACTION = 0.2f;
    public static final float FLICK_DP_PER_SECOND = 700;
    public static final float FLICK_MIN_DP = 16;
    public static final int OPEN_MS = 260;
    public static final int CLOSE_MS = 200;
    public static final int SPRING_BACK_MS = 220;
    public static final float BACK_GESTURE_TRAVEL = 0.25f;
    public static final double VERTICAL_DEGREES = 60;
    public static final int EDGE_DP = 32;

    private SwipeFeel() {
    }

    public static boolean isEnough(float distancePx, float velocityPxPerSecond, float screenPx, float density) {
        if (distancePx <= 0) {
            return false;
        }
        if (distancePx >= screenPx * DRAG_FRACTION) {
            return true;
        }
        return velocityPxPerSecond >= FLICK_DP_PER_SECOND * density && distancePx >= FLICK_MIN_DP * density;
    }
}
