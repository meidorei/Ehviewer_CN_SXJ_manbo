package com.hippo.ehviewer.reader;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import com.hippo.ehviewer.client.data.GalleryInfo;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class DownloadReadingQueueTest {

    @Test
    public void snapshotPreservesCurrentListOrder() {
        assertArrayEquals(new long[]{30L, 10L, 20L}, DownloadReadingQueue.snapshot(
                Arrays.asList(info(30L), info(10L), info(20L))));
    }

    @Test
    public void queueFindsCurrentAndNextEntries() {
        long[] queue = {30L, 10L, 20L};
        assertEquals(1, DownloadReadingQueue.indexOf(queue, 10L));
        assertEquals(2, DownloadReadingQueue.nextIndex(queue, 1));
        assertEquals(-1, DownloadReadingQueue.nextIndex(queue, 2));
        assertEquals(-1, DownloadReadingQueue.indexOf(queue, 99L));
    }

    @Test
    public void emptyQueueIsSafe() {
        assertArrayEquals(new long[0], DownloadReadingQueue.snapshot(Collections.emptyList()));
        assertEquals(-1, DownloadReadingQueue.nextIndex(null, 0));
    }

    @Test
    public void unavailableAndFailingEntriesAreSkipped() {
        long[] queue = {10L, 20L, 30L, 40L};
        assertEquals(3, DownloadReadingQueue.findNextAvailableIndex(queue, 0, gid -> {
            if (gid == 20L) {
                throw new IllegalStateException("missing archive permission");
            }
            return gid == 40L;
        }));
    }

    @Test
    public void exhaustedQueueStopsAtEnd() {
        long[] queue = {10L, 20L, 30L};
        assertEquals(-1, DownloadReadingQueue.findNextAvailableIndex(
                queue, 0, gid -> false));
        assertEquals(-1, DownloadReadingQueue.findNextAvailableIndex(
                queue, 2, gid -> true));
    }

    private static GalleryInfo info(long gid) {
        GalleryInfo info = new GalleryInfo();
        info.gid = gid;
        return info;
    }
}
