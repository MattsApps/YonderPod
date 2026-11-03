package de.danoeh.antennapod.smartplay.worlds;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SwipeFeelTest {
    private static final float SCREEN = 2000;
    private static final float DENSITY = 2.5f;

    @Test
    public void aFifthOfTheScreenIsEnough() {
        assertTrue(SwipeFeel.isEnough(400, 0, SCREEN, DENSITY));
        assertFalse(SwipeFeel.isEnough(399, 0, SCREEN, DENSITY));
    }

    @Test
    public void aShortQuickFlickIsEnough() {
        assertTrue(SwipeFeel.isEnough(50, 2000, SCREEN, DENSITY));
    }

    @Test
    public void aTinyFlickIsNot() {
        assertFalse(SwipeFeel.isEnough(20, 5000, SCREEN, DENSITY));
    }

    @Test
    public void aSlowShortDragIsNot() {
        assertFalse(SwipeFeel.isEnough(200, 300, SCREEN, DENSITY));
    }

    @Test
    public void theWrongWayIsNever() {
        assertFalse(SwipeFeel.isEnough(-500, 9000, SCREEN, DENSITY));
        assertFalse(SwipeFeel.isEnough(0, 9000, SCREEN, DENSITY));
    }
}
