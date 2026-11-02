package de.danoeh.antennapod.smartplay.worlds;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class WorldPages {
    public static final int BOOKS = 0;
    public static final int PODCASTS = 1;
    public static final int RADIO = 2;
    public static final int LEFT_SHELF = 3;
    public static final int RIGHT_SHELF = 4;
    public static final int NONE = -1;

    private final List<Integer> pages;
    private final int leftApps;
    private final int rightApps;

    public WorldPages(boolean showBooks, boolean showRadio, int leftApps, int rightApps) {
        this.leftApps = leftApps;
        this.rightApps = rightApps;
        List<Integer> list = new ArrayList<>();
        if (leftApps > 0) {
            list.add(LEFT_SHELF);
        }
        if (showBooks) {
            list.add(BOOKS);
        }
        list.add(PODCASTS);
        if (showRadio) {
            list.add(RADIO);
        }
        if (rightApps > 0) {
            list.add(RIGHT_SHELF);
        }
        pages = Collections.unmodifiableList(list);
    }

    public int size() {
        return pages.size();
    }

    public int pageAt(int position) {
        return position >= 0 && position < pages.size() ? pages.get(position) : NONE;
    }

    public int positionOf(int page) {
        int position = pages.indexOf(page);
        return position >= 0 ? position : pages.indexOf(PODCASTS);
    }

    public boolean contains(int page) {
        return pages.contains(page);
    }

    public int leftOf(int page) {
        return pageAt(pages.indexOf(page) - 1);
    }

    public int rightOf(int page) {
        int position = pages.indexOf(page);
        return position < 0 ? NONE : pageAt(position + 1);
    }

    public static boolean isShelf(int page) {
        return page == LEFT_SHELF || page == RIGHT_SHELF;
    }

    public static boolean isWorld(int page) {
        return page == BOOKS || page == PODCASTS || page == RADIO;
    }

    public int appsOn(int shelf) {
        if (shelf == LEFT_SHELF) {
            return leftApps;
        }
        return shelf == RIGHT_SHELF ? rightApps : 0;
    }

    public boolean opensDirectly(int shelf) {
        return isShelf(shelf) && appsOn(shelf) == 1;
    }

    public int worldBeside(int shelf) {
        if (shelf == LEFT_SHELF) {
            return rightOf(shelf);
        }
        return shelf == RIGHT_SHELF ? leftOf(shelf) : shelf;
    }

    public boolean sameAs(WorldPages other) {
        return other != null && pages.equals(other.pages) && leftApps == other.leftApps
                && rightApps == other.rightApps;
    }
}
