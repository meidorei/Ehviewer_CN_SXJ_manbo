package com.hippo.ehviewer.reader;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hippo.ehviewer.client.data.GalleryInfo;

import java.util.List;

public final class DownloadReadingQueue {

    public interface Availability {
        boolean isAvailable(long gid) throws Exception;
    }

    private DownloadReadingQueue() {
    }

    @NonNull
    public static long[] snapshot(@Nullable List<? extends GalleryInfo> list) {
        if (list == null || list.isEmpty()) {
            return new long[0];
        }
        long[] gids = new long[list.size()];
        for (int i = 0; i < list.size(); i++) {
            gids[i] = list.get(i).gid;
        }
        return gids;
    }

    public static int indexOf(@Nullable long[] gids, long gid) {
        if (gids == null) {
            return -1;
        }
        for (int i = 0; i < gids.length; i++) {
            if (gids[i] == gid) {
                return i;
            }
        }
        return -1;
    }

    public static int nextIndex(@Nullable long[] gids, int currentIndex) {
        if (gids == null || currentIndex < -1 || currentIndex + 1 >= gids.length) {
            return -1;
        }
        return currentIndex + 1;
    }

    public static int findNextAvailableIndex(@Nullable long[] gids, int currentIndex,
            @NonNull Availability availability) {
        for (int i = nextIndex(gids, currentIndex);
                gids != null && i >= 0 && i < gids.length;
                i++) {
            try {
                if (availability.isAvailable(gids[i])) {
                    return i;
                }
            } catch (Exception ignored) {
            }
        }
        return -1;
    }
}
