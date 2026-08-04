package com.hippo.ehviewer.subscription;

import com.hippo.ehviewer.client.data.GalleryInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure accumulation and stopping rules for a newest-first bookmark scan. */
final class BookmarkScanAccumulator {
    private final FeedBoundary oldBoundary;
    private final Map<Long, GalleryInfo> unique = new LinkedHashMap<>();
    private FeedBoundary top = FeedBoundary.EMPTY;
    private int pages;
    private int galleryCount;
    private BookmarkScanResult.EndReason endReason;
    private boolean boundaryProven;

    BookmarkScanAccumulator(FeedBoundary oldBoundary) {
        this.oldBoundary = oldBoundary == null ? FeedBoundary.EMPTY : oldBoundary;
    }

    boolean addPage(List<GalleryInfo> galleries, boolean hasNextPage) {
        if (endReason != null) throw new IllegalStateException("scan already complete");
        List<GalleryInfo> safe = galleries == null ? new ArrayList<>() : galleries;
        pages++;
        galleryCount += safe.size();
        if (top.isEmpty() && !safe.isEmpty()) {
            top = LocalFollowRepository.boundaryOf(safe);
        }
        for (GalleryInfo gallery : safe) {
            if (gallery == null || gallery.postedTimestamp <= 0) {
                throw new IllegalArgumentException("gallery timestamp unavailable");
            }
            unique.putIfAbsent(gallery.gid, gallery);
        }

        if (oldBoundary.isEmpty()) {
            endReason = BookmarkScanResult.EndReason.BASELINE;
            return false;
        }
        if (GlobalScanPolicy.hasPassedCursor(oldBoundary, safe)) {
            boundaryProven = true;
            endReason = BookmarkScanResult.EndReason.BOUNDARY;
            return false;
        }
        if (safe.isEmpty() || !hasNextPage) {
            boundaryProven = true;
            endReason = BookmarkScanResult.EndReason.LIST_END;
            return false;
        }
        return true;
    }

    BookmarkScanResult finish() {
        if (endReason == null) throw new IllegalStateException("scan is incomplete");
        return new BookmarkScanResult(new ArrayList<>(unique.values()), top,
                boundaryProven, pages, galleryCount, endReason);
    }
}
