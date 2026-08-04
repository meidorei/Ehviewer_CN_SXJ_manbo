package com.hippo.ehviewer.subscription;

import com.hippo.ehviewer.client.data.GalleryInfo;

import java.util.Collections;
import java.util.List;

/** Immutable result of one complete newest-first bookmark scan. */
final class BookmarkScanResult {
    enum EndReason { BASELINE, BOUNDARY, LIST_END }

    final List<GalleryInfo> galleries;
    final FeedBoundary top;
    final boolean boundaryProven;
    final int pages;
    final int galleryCount;
    final EndReason endReason;

    BookmarkScanResult(List<GalleryInfo> galleries, FeedBoundary top,
                       boolean boundaryProven, int pages, int galleryCount,
                       EndReason endReason) {
        this.galleries = Collections.unmodifiableList(galleries);
        this.top = top == null ? FeedBoundary.EMPTY : top;
        this.boundaryProven = boundaryProven;
        this.pages = pages;
        this.galleryCount = galleryCount;
        this.endReason = endReason;
    }
}
