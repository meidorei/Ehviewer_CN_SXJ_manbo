package com.hippo.ehviewer.subscription;

import com.hippo.ehviewer.client.data.GalleryInfo;

import java.util.List;

/** Boundary rules for a newest-first shared scan. */
public final class GlobalScanPolicy {
    private GlobalScanPolicy() {}

    /**
     * Same-second entries must all be scanned. The cursor is covered only after the feed moves
     * to an older timestamp, preventing a newly indexed same-second GID from being skipped.
     */
    public static boolean hasPassedCursor(FeedBoundary cursor, List<GalleryInfo> galleries) {
        if (cursor == null || cursor.isEmpty() || galleries == null) return false;
        for (GalleryInfo gallery : galleries) {
            if (gallery != null && gallery.postedTimestamp > 0
                    && gallery.postedTimestamp < cursor.time) {
                return true;
            }
        }
        return false;
    }

    /**
     * An item whose own cursor predates the shared cursor cannot be updated safely from the
     * shared scan alone: that scan intentionally stops before reaching the item's boundary.
     */
    public static boolean requiresItemBridge(
            FeedBoundary itemCursor, FeedBoundary sharedCursor) {
        return itemCursor != null && sharedCursor != null
                && !itemCursor.isEmpty() && !sharedCursor.isEmpty()
                && itemCursor.time < sharedCursor.time;
    }

    /**
     * Per-item work started by a global scan must finish at the same shared boundary. Ordinary
     * standalone item checks keep their own query top by passing an empty shared boundary.
     */
    public static FeedBoundary fallbackCommitBoundary(
            FeedBoundary itemTop, FeedBoundary sharedTop) {
        if (sharedTop != null && !sharedTop.isEmpty()) return sharedTop;
        return itemTop == null ? FeedBoundary.EMPTY : itemTop;
    }
}
