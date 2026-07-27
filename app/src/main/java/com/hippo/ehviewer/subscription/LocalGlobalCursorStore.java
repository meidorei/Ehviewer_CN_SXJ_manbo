package com.hippo.ehviewer.subscription;

import android.database.Cursor;

import com.hippo.ehviewer.EhDB;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Durable newest-first cursor shared by one update type within an account/site source. */
public final class LocalGlobalCursorStore {
    public static final String TYPE_FOLLOW = "FOLLOW";
    public static final String TYPE_BOOKMARK = "BOOKMARK";
    static final String READ_SQL =
            "SELECT \"CURRENT_TIME\",\"CURRENT_GIDS\" FROM \"LOCAL_GLOBAL_CURSOR\" " +
                    "WHERE \"ACCOUNT_KEY\"=? AND \"JOB_TYPE\"=? AND \"QUERY_SIGNATURE\"=?";

    private LocalGlobalCursorStore() {}

    public static FeedBoundary read(String accountKey, String jobType, String signature) {
        try (Cursor cursor = EhDB.getDatabase().rawQuery(
                READ_SQL,
                new String[]{accountKey, jobType, signature})) {
            if (!cursor.moveToFirst()) return FeedBoundary.EMPTY;
            return new FeedBoundary(cursor.getLong(0),
                    SubscriptionRepository.parseGids(cursor.getString(1)));
        }
    }

    public static void write(String accountKey, String jobType, String signature,
                             FeedBoundary boundary) {
        if (boundary == null || boundary.isEmpty()) return;
        EhDB.getDatabase().execSQL(
                "INSERT OR REPLACE INTO \"LOCAL_GLOBAL_CURSOR\"" +
                        "(\"ACCOUNT_KEY\",\"JOB_TYPE\",\"QUERY_SIGNATURE\",\"CURRENT_TIME\"," +
                        "\"CURRENT_GIDS\",\"UPDATED_AT\") " +
                        "VALUES(?,?,?,?,?,?)",
                new Object[]{accountKey, jobType, signature, boundary.time,
                        SubscriptionRepository.serializeGids(boundary.gids),
                        System.currentTimeMillis()});
    }

    /** Uses the oldest existing item cursor for the one-time v10-to-v11 transition. */
    public static FeedBoundary oldest(Map<?, FeedCheckpoint> checkpoints) {
        long oldest = Long.MAX_VALUE;
        Set<Long> gids = new LinkedHashSet<>();
        for (FeedCheckpoint checkpoint : checkpoints.values()) {
            if (checkpoint == null || checkpoint.current.isEmpty()) continue;
            if (checkpoint.current.time < oldest) {
                oldest = checkpoint.current.time;
                gids.clear();
                gids.addAll(checkpoint.current.gids);
            } else if (checkpoint.current.time == oldest) {
                gids.addAll(checkpoint.current.gids);
            }
        }
        return oldest == Long.MAX_VALUE ? FeedBoundary.EMPTY : new FeedBoundary(oldest, gids);
    }

    public static void deleteForItemRemoval(String jobType) {
        EhDB.getDatabase().execSQL(
                "DELETE FROM LOCAL_GLOBAL_CURSOR WHERE JOB_TYPE=?",
                new Object[]{jobType});
    }
}
