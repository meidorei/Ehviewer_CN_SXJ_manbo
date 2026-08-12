package com.hippo.ehviewer.subscription;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.hippo.ehviewer.client.data.GalleryInfo;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

public class BookmarkScanAccumulatorTest {
    @Test public void baselineStopsAfterFirstPageWithoutReportingUpdates() {
        BookmarkScanAccumulator accumulator = new BookmarkScanAccumulator(FeedBoundary.EMPTY);

        assertFalse(accumulator.addPage(galleries(gallery(120, 3), gallery(119, 2)), true));
        BookmarkScanResult result = accumulator.finish();

        assertEquals(BookmarkScanResult.EndReason.BASELINE, result.endReason);
        assertFalse(result.boundaryProven);
        assertTrue(result.newGids.isEmpty());
        assertEquals(120, result.top.time);
        assertEquals(1, result.pages);
        assertEquals(2, result.galleryCount);
    }

    @Test public void continuesAcrossPagesUntilTimestampOlderThanBoundary() {
        BookmarkScanAccumulator accumulator = new BookmarkScanAccumulator(boundary(100, 1));

        assertTrue(accumulator.addPage(galleries(gallery(120, 4), gallery(110, 3)), true));
        assertFalse(accumulator.addPage(galleries(gallery(100, 1), gallery(99, 2)), true));
        BookmarkScanResult result = accumulator.finish();

        assertEquals(BookmarkScanResult.EndReason.BOUNDARY, result.endReason);
        assertTrue(result.boundaryProven);
        assertEquals(Arrays.asList(4L, 3L), result.newGids);
        assertEquals(2, result.pages);
        assertEquals(4, result.galleryCount);
    }

    @Test public void knownSameSecondGidDoesNotStopBeforeNewSameSecondGid() {
        BookmarkScanAccumulator accumulator = new BookmarkScanAccumulator(boundary(100, 1));

        assertTrue(accumulator.addPage(galleries(gallery(100, 1), gallery(100, 2)), true));
        assertFalse(accumulator.addPage(galleries(gallery(99, 3)), false));
        BookmarkScanResult result = accumulator.finish();

        assertEquals(Collections.singletonList(2L), result.newGids);
        assertTrue(result.boundaryProven);
    }

    @Test public void listEndProvesBoundaryEvenWhenOldGidDisappeared() {
        BookmarkScanAccumulator accumulator = new BookmarkScanAccumulator(boundary(100, 1));

        assertFalse(accumulator.addPage(galleries(gallery(120, 4), gallery(110, 3)), false));
        BookmarkScanResult result = accumulator.finish();

        assertEquals(BookmarkScanResult.EndReason.LIST_END, result.endReason);
        assertTrue(result.boundaryProven);
        assertEquals(Arrays.asList(4L, 3L), result.newGids);
    }

    @Test public void duplicateGidsAreReturnedOnlyOnceButWorkCountStaysExact() {
        BookmarkScanAccumulator accumulator = new BookmarkScanAccumulator(boundary(100, 1));

        assertTrue(accumulator.addPage(galleries(gallery(120, 4), gallery(110, 3)), true));
        assertFalse(accumulator.addPage(galleries(gallery(110, 3), gallery(99, 2)), false));
        BookmarkScanResult result = accumulator.finish();

        assertEquals(Arrays.asList(4L, 3L), result.newGids);
        assertEquals(4, result.galleryCount);
    }

    @Test public void emptyListEndProducesNoDeltaAndNoTop() {
        BookmarkScanAccumulator accumulator = new BookmarkScanAccumulator(boundary(100, 1));

        assertFalse(accumulator.addPage(Collections.emptyList(), false));
        BookmarkScanResult result = accumulator.finish();

        assertEquals(BookmarkScanResult.EndReason.LIST_END, result.endReason);
        assertTrue(result.boundaryProven);
        assertTrue(result.newGids.isEmpty());
        assertTrue(result.top.isEmpty());
        assertEquals(1, result.pages);
        assertEquals(0, result.galleryCount);
    }

    @Test(expected = IllegalArgumentException.class)
    public void missingTimestampRejectsWholeScan() {
        BookmarkScanAccumulator accumulator = new BookmarkScanAccumulator(boundary(100, 1));
        accumulator.addPage(Collections.singletonList(gallery(0, 9)), false);
    }

    @Test public void commitDiffKeepsNewSameSecondGidAfterKnownOne() {
        List<Long> gids = LocalFollowRepository.collectNewGids(boundary(100, 1),
                galleries(gallery(100, 1), gallery(100, 2), gallery(100, 2), gallery(99, 3)));

        assertEquals(Collections.singletonList(2L), gids);
    }

    private static FeedBoundary boundary(long time, long... gids) {
        LinkedHashSet<Long> values = new LinkedHashSet<>();
        for (long gid : gids) values.add(gid);
        return new FeedBoundary(time, values);
    }

    private static List<GalleryInfo> galleries(GalleryInfo... values) {
        return Arrays.asList(values);
    }

    private static GalleryInfo gallery(long time, long gid) {
        GalleryInfo gallery = new GalleryInfo();
        gallery.gid = gid;
        gallery.postedTimestamp = time;
        return gallery;
    }
}
