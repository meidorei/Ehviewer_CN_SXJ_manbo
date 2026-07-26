package com.hippo.ehviewer.subscription;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LocalRefreshJobStoreTest {
    @Test
    public void onlyFullFollowAndBookmarkJobsRecordAttempts() {
        assertTrue(LocalRefreshJobStore.shouldRecordAttempt(
                snapshot(LocalRefreshJobStore.TYPE_FOLLOW, "GLOBAL", 10, 10, ""),
                LocalRefreshJobStore.STATUS_SUCCESS));
        assertTrue(LocalRefreshJobStore.shouldRecordAttempt(
                snapshot(LocalRefreshJobStore.TYPE_BOOKMARK, "FIRST_PAGE", 10, 10, ""),
                LocalRefreshJobStore.STATUS_CANCELLED));
        assertFalse(LocalRefreshJobStore.shouldRecordAttempt(
                snapshot(LocalRefreshJobStore.TYPE_BOOKMARK, "SINGLE:7", 1, 1, ""),
                LocalRefreshJobStore.STATUS_SUCCESS));
        assertFalse(LocalRefreshJobStore.shouldRecordAttempt(
                snapshot(LocalRefreshJobStore.TYPE_BASELINE, "AUTO", 5, 5, ""),
                LocalRefreshJobStore.STATUS_SUCCESS));
        assertFalse(LocalRefreshJobStore.shouldRecordAttempt(
                snapshot(LocalRefreshJobStore.TYPE_FOLLOW, "TAGS", 3, 10, ""),
                LocalRefreshJobStore.STATUS_PAUSED));
    }

    @Test
    public void attemptResultDistinguishesSuccessPartialFailureAndStop() {
        LocalRefreshJobStore.Snapshot success =
                snapshot(LocalRefreshJobStore.TYPE_FOLLOW, "GLOBAL", 10, 10, "");
        assertEquals(LocalRefreshJobStore.RESULT_SUCCESS,
                LocalRefreshJobStore.deriveAttemptResult(success,
                        LocalRefreshJobStore.STATUS_SUCCESS, 0));

        LocalRefreshJobStore.Snapshot partial =
                snapshot(LocalRefreshJobStore.TYPE_BOOKMARK, "FIRST_PAGE",
                        10, 10, "one\ntwo");
        assertEquals(LocalRefreshJobStore.RESULT_PARTIAL,
                LocalRefreshJobStore.deriveAttemptResult(partial,
                        LocalRefreshJobStore.STATUS_FAILED, 2));

        LocalRefreshJobStore.Snapshot failed =
                snapshot(LocalRefreshJobStore.TYPE_BOOKMARK, "FIRST_PAGE",
                        3, 10, "fatal");
        assertEquals(LocalRefreshJobStore.RESULT_FAILED,
                LocalRefreshJobStore.deriveAttemptResult(failed,
                        LocalRefreshJobStore.STATUS_FAILED, 1));
        assertEquals(LocalRefreshJobStore.RESULT_CANCELLED,
                LocalRefreshJobStore.deriveAttemptResult(failed,
                        LocalRefreshJobStore.STATUS_CANCELLED, 1));
    }

    @Test
    public void failureCountIgnoresEmptyLines() {
        assertEquals(0, LocalRefreshJobStore.failureCount(""));
        assertEquals(0, LocalRefreshJobStore.failureCount(null));
        assertEquals(2, LocalRefreshJobStore.failureCount("one\n\n two "));
    }

    private static LocalRefreshJobStore.Snapshot snapshot(
            String type, String method, int index, int total, String failures) {
        return new LocalRefreshJobStore.Snapshot(type, method,
                LocalRefreshJobStore.STATUS_RUNNING, index, total,
                0, 0, "", "", failures, 1L, 1L);
    }
}
