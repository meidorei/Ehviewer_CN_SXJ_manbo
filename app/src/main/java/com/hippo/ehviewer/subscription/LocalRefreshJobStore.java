package com.hippo.ehviewer.subscription;

import android.database.Cursor;

import com.hippo.ehviewer.EhDB;

/** Durable single-job cursor. Running rows are converted to PAUSED after process restart. */
public final class LocalRefreshJobStore {
    public static final String TYPE_FOLLOW = "FOLLOW";
    public static final String TYPE_BOOKMARK = "BOOKMARK";
    public static final String TYPE_BASELINE = "BASELINE";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_PAUSED = "PAUSED";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_PARTIAL = "PARTIAL";
    public static final String RESULT_FAILED = "FAILED";
    public static final String RESULT_CANCELLED = "CANCELLED";
    private static final String META_LAST_FOLLOW_SUCCESS = "last_follow_success";
    private static final String META_LAST_BOOKMARK_SUCCESS = "last_bookmark_success";
    private static final String META_ATTEMPT_TIME_SUFFIX = "_last_attempt_time";
    private static final String META_ATTEMPT_RESULT_SUFFIX = "_last_attempt_result";
    private static final String META_ATTEMPT_FAILURES_SUFFIX = "_last_attempt_failures";

    private LocalRefreshJobStore() {}

    public static void recoverInterruptedJob() {
        EhDB.getDatabase().execSQL(
                "UPDATE LOCAL_REFRESH_JOB SET STATUS=?,UPDATED_AT=? WHERE _id=1 AND STATUS=?",
                new Object[]{STATUS_PAUSED, System.currentTimeMillis(), STATUS_RUNNING});
        Snapshot previous = read();
        if (previous != null && getMeta(
                attemptKey(previous.type, META_ATTEMPT_TIME_SUFFIX)) == null
                && shouldRecordAttempt(previous, previous.status)) {
            writeAttempt(previous, previous.status, previous.updatedAt);
        }
        LocalBaselineQueue.resetRunning();
    }

    public static void start(String type, String method, int total, String host) {
        long now = System.currentTimeMillis();
        EhDB.getDatabase().execSQL("INSERT OR REPLACE INTO LOCAL_REFRESH_JOB" +
                        "(_id,JOB_TYPE,METHOD,STATUS,CURRENT_INDEX,TOTAL,PAGES,GALLERIES,CURRENT_KEY," +
                        "SOURCE_HOST,FAILURES,STARTED_AT,UPDATED_AT) VALUES(1,?,?,?,?,?,0,0,'',?,'',?,?)",
                new Object[]{type, method, STATUS_RUNNING, 0, total, host, now, now});
    }

    public static void progress(int index, int pages, int galleries, String key,
                                String failures) {
        EhDB.getDatabase().execSQL("UPDATE LOCAL_REFRESH_JOB SET CURRENT_INDEX=?,PAGES=?," +
                        "GALLERIES=?,CURRENT_KEY=?,FAILURES=?,UPDATED_AT=? WHERE _id=1",
                new Object[]{index, pages, galleries, key == null ? "" : key,
                        failures == null ? "" : failures, System.currentTimeMillis()});
    }

    public static void updateHost(String host) {
        EhDB.getDatabase().execSQL(
                "UPDATE LOCAL_REFRESH_JOB SET SOURCE_HOST=?,UPDATED_AT=? WHERE _id=1",
                new Object[]{host, System.currentTimeMillis()});
    }

    public static void finish(String status, boolean fullFollowSuccess) {
        long now = System.currentTimeMillis();
        Snapshot snapshot = read();
        EhDB.getDatabase().execSQL(
                "UPDATE LOCAL_REFRESH_JOB SET STATUS=?,UPDATED_AT=? WHERE _id=1",
                new Object[]{status, now});
        if (fullFollowSuccess) {
            putMeta(META_LAST_FOLLOW_SUCCESS, Long.toString(now));
        }
        if (STATUS_SUCCESS.equals(status) && snapshot != null
                && TYPE_BOOKMARK.equals(snapshot.type)
                && (snapshot.method == null || !snapshot.method.startsWith("SINGLE:"))) {
            putMeta(META_LAST_BOOKMARK_SUCCESS, Long.toString(now));
        }
        if (shouldRecordAttempt(snapshot, status)) {
            writeAttempt(snapshot, status, now);
        }
    }

    public static long lastFollowSuccess() {
        return readTimestampMeta(META_LAST_FOLLOW_SUCCESS);
    }

    public static long lastBookmarkSuccess() {
        return readTimestampMeta(META_LAST_BOOKMARK_SUCCESS);
    }

    private static long readTimestampMeta(String key) {
        String value = getMeta(key);
        if (value == null) return 0;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public static AttemptHistory lastAttempt(String type) {
        if (!TYPE_FOLLOW.equals(type) && !TYPE_BOOKMARK.equals(type)) return null;
        String timeValue = getMeta(attemptKey(type, META_ATTEMPT_TIME_SUFFIX));
        String result = getMeta(attemptKey(type, META_ATTEMPT_RESULT_SUFFIX));
        String failureValue = getMeta(attemptKey(type, META_ATTEMPT_FAILURES_SUFFIX));
        if (timeValue == null || result == null) return null;
        try {
            long time = Long.parseLong(timeValue);
            int failures = failureValue == null ? 0 : Integer.parseInt(failureValue);
            return time <= 0 ? null : new AttemptHistory(time, result, failures);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static boolean shouldRecordAttempt(Snapshot snapshot, String terminalStatus) {
        if (snapshot == null || STATUS_PAUSED.equals(terminalStatus)
                || STATUS_RUNNING.equals(terminalStatus)) {
            return false;
        }
        if (TYPE_FOLLOW.equals(snapshot.type)) return true;
        return TYPE_BOOKMARK.equals(snapshot.type)
                && (snapshot.method == null || !snapshot.method.startsWith("SINGLE:"));
    }

    static String deriveAttemptResult(Snapshot snapshot, String terminalStatus,
                                      int failures) {
        if (STATUS_CANCELLED.equals(terminalStatus)) return RESULT_CANCELLED;
        if (STATUS_SUCCESS.equals(terminalStatus) && failures == 0) return RESULT_SUCCESS;
        if (failures > 0 && snapshot != null && snapshot.index >= snapshot.total
                && failures < snapshot.total) {
            return RESULT_PARTIAL;
        }
        return RESULT_FAILED;
    }

    public static int failureCount(String failures) {
        if (failures == null || failures.trim().isEmpty()) return 0;
        int count = 0;
        for (String line : failures.split("\\n")) {
            if (!line.trim().isEmpty()) count++;
        }
        return count;
    }

    private static String attemptKey(String type, String suffix) {
        return type.toLowerCase(java.util.Locale.ROOT) + suffix;
    }

    private static void writeAttempt(Snapshot snapshot, String terminalStatus, long time) {
        int failures = failureCount(snapshot.failures);
        String result = deriveAttemptResult(snapshot, terminalStatus, failures);
        putMeta(attemptKey(snapshot.type, META_ATTEMPT_TIME_SUFFIX), Long.toString(time));
        putMeta(attemptKey(snapshot.type, META_ATTEMPT_RESULT_SUFFIX), result);
        putMeta(attemptKey(snapshot.type, META_ATTEMPT_FAILURES_SUFFIX),
                Integer.toString(failures));
    }

    public static Snapshot read() {
        try (Cursor cursor = EhDB.getDatabase().rawQuery(
                "SELECT JOB_TYPE,METHOD,STATUS,CURRENT_INDEX,TOTAL,PAGES,GALLERIES," +
                        "CURRENT_KEY,SOURCE_HOST,FAILURES,STARTED_AT,UPDATED_AT " +
                        "FROM LOCAL_REFRESH_JOB WHERE _id=1", null)) {
            if (!cursor.moveToFirst()) return null;
            return new Snapshot(cursor.getString(0), cursor.getString(1), cursor.getString(2),
                    cursor.getInt(3), cursor.getInt(4), cursor.getInt(5), cursor.getInt(6),
                    cursor.getString(7), cursor.getString(8), cursor.getString(9),
                    cursor.getLong(10), cursor.getLong(11));
        }
    }

    private static void putMeta(String key, String value) {
        EhDB.getDatabase().execSQL(
                "INSERT OR REPLACE INTO LOCAL_REFRESH_META(META_KEY,META_VALUE) VALUES(?,?)",
                new Object[]{key, value});
    }

    private static String getMeta(String key) {
        try (Cursor cursor = EhDB.getDatabase().rawQuery(
                "SELECT META_VALUE FROM LOCAL_REFRESH_META WHERE META_KEY=?",
                new String[]{key})) {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        }
    }

    public static final class Snapshot {
        public final String type;
        public final String method;
        public final String status;
        public final int index;
        public final int total;
        public final int pages;
        public final int galleries;
        public final String currentKey;
        public final String host;
        public final String failures;
        public final long startedAt;
        public final long updatedAt;

        Snapshot(String type, String method, String status, int index, int total, int pages,
                 int galleries, String currentKey, String host, String failures,
                 long startedAt, long updatedAt) {
            this.type = type;
            this.method = method;
            this.status = status;
            this.index = index;
            this.total = total;
            this.pages = pages;
            this.galleries = galleries;
            this.currentKey = currentKey;
            this.host = host;
            this.failures = failures;
            this.startedAt = startedAt;
            this.updatedAt = updatedAt;
        }
    }

    public static final class AttemptHistory {
        public final long time;
        public final String result;
        public final int failureCount;

        AttemptHistory(long time, String result, int failureCount) {
            this.time = time;
            this.result = result;
            this.failureCount = failureCount;
        }
    }
}
