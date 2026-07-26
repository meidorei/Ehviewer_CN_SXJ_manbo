package com.hippo.ehviewer.subscription;

import android.database.sqlite.SQLiteDatabase;

import org.greenrobot.greendao.database.Database;

/** Incremental schema owned by the follow-update feature. */
public final class SubscriptionSchema {
    private static final String[] SQL = {
            "CREATE TABLE IF NOT EXISTS \"LOCAL_FOLLOW_TAG\" (" +
                    "\"TAG_NAME\" TEXT PRIMARY KEY NOT NULL," +
                    "\"ADDED_AT\" INTEGER NOT NULL," +
                    "\"LAST_CHECKED_AT\" INTEGER NOT NULL DEFAULT 0)",
            "CREATE TABLE IF NOT EXISTS \"SUBSCRIPTION_TAG_CACHE\" (" +
                    "\"_id\" INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "\"ACCOUNT_KEY\" TEXT NOT NULL," +
                    "\"TAG_NAME\" TEXT NOT NULL," +
                    "\"SERVER_TAG_ID\" TEXT NOT NULL DEFAULT ''," +
                    "\"WATCHED\" INTEGER NOT NULL," +
                    "\"HIDDEN\" INTEGER NOT NULL," +
                    "\"COLOR\" TEXT NOT NULL DEFAULT ''," +
                    "\"WEIGHT\" INTEGER NOT NULL DEFAULT 0," +
                    "\"FIRST_SEEN_AT\" INTEGER NOT NULL," +
                    "\"SYNCED_AT\" INTEGER NOT NULL," +
                    "UNIQUE(\"ACCOUNT_KEY\",\"TAG_NAME\"))",
            "CREATE INDEX IF NOT EXISTS \"IDX_SUBSCRIPTION_TAG_ACCOUNT\" ON " +
                    "\"SUBSCRIPTION_TAG_CACHE\" (\"ACCOUNT_KEY\")",
            "CREATE TABLE IF NOT EXISTS \"FEED_CHECKPOINT\" (" +
                    "\"_id\" INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "\"ACCOUNT_KEY\" TEXT NOT NULL," +
                    "\"SOURCE_TYPE\" TEXT NOT NULL," +
                    "\"SOURCE_KEY\" TEXT NOT NULL," +
                    "\"QUERY_SIGNATURE\" TEXT NOT NULL," +
                    "\"PREVIOUS_TIME\" INTEGER NOT NULL DEFAULT 0," +
                    "\"CURRENT_TIME\" INTEGER NOT NULL DEFAULT 0," +
                    "\"PREVIOUS_GIDS\" TEXT NOT NULL DEFAULT ''," +
                    "\"CURRENT_GIDS\" TEXT NOT NULL DEFAULT ''," +
                    "\"UPDATED_AT\" INTEGER NOT NULL," +
                    "UNIQUE(\"ACCOUNT_KEY\",\"SOURCE_TYPE\",\"SOURCE_KEY\",\"QUERY_SIGNATURE\"))",
            "CREATE TABLE IF NOT EXISTS \"SUBSCRIPTION_TAG_UPDATE_STATE\" (" +
                    "\"_id\" INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "\"ACCOUNT_KEY\" TEXT NOT NULL," +
                    "\"TAG_NAME\" TEXT NOT NULL," +
                    "\"QUERY_SIGNATURE\" TEXT NOT NULL," +
                    "\"COUNT\" INTEGER NOT NULL DEFAULT 0," +
                    "\"COUNT_STATE\" TEXT NOT NULL," +
                    "\"CHECKED_AT\" INTEGER NOT NULL," +
                    "UNIQUE(\"ACCOUNT_KEY\",\"TAG_NAME\",\"QUERY_SIGNATURE\"))",
            "CREATE TABLE IF NOT EXISTS \"LOCAL_UPDATE_STATE\" (" +
                    "\"SOURCE_TYPE\" TEXT NOT NULL," +
                    "\"SOURCE_KEY\" TEXT NOT NULL," +
                    "\"QUERY_SIGNATURE\" TEXT NOT NULL," +
                    "\"COUNT\" INTEGER NOT NULL DEFAULT 0," +
                    "\"COUNT_STATE\" TEXT NOT NULL DEFAULT 'EXACT'," +
                    "\"CHECKED_AT\" INTEGER NOT NULL DEFAULT 0," +
                    "\"ERROR\" TEXT NOT NULL DEFAULT ''," +
                    "PRIMARY KEY(\"SOURCE_TYPE\",\"SOURCE_KEY\",\"QUERY_SIGNATURE\"))",
            "CREATE TABLE IF NOT EXISTS \"LOCAL_UNREAD_GALLERY\" (" +
                    "\"SOURCE_TYPE\" TEXT NOT NULL," +
                    "\"SOURCE_KEY\" TEXT NOT NULL," +
                    "\"GID\" INTEGER NOT NULL," +
                    "PRIMARY KEY(\"SOURCE_TYPE\",\"SOURCE_KEY\",\"GID\"))",
            "CREATE TABLE IF NOT EXISTS \"LOCAL_REFRESH_JOB\" (" +
                    "\"_id\" INTEGER PRIMARY KEY CHECK(\"_id\"=1)," +
                    "\"JOB_TYPE\" TEXT NOT NULL," +
                    "\"METHOD\" TEXT NOT NULL," +
                    "\"STATUS\" TEXT NOT NULL," +
                    "\"CURRENT_INDEX\" INTEGER NOT NULL DEFAULT 0," +
                    "\"TOTAL\" INTEGER NOT NULL DEFAULT 0," +
                    "\"PAGES\" INTEGER NOT NULL DEFAULT 0," +
                    "\"GALLERIES\" INTEGER NOT NULL DEFAULT 0," +
                    "\"CURRENT_KEY\" TEXT NOT NULL DEFAULT ''," +
                    "\"SOURCE_HOST\" TEXT NOT NULL DEFAULT ''," +
                    "\"FAILURES\" TEXT NOT NULL DEFAULT ''," +
                    "\"STARTED_AT\" INTEGER NOT NULL DEFAULT 0," +
                    "\"UPDATED_AT\" INTEGER NOT NULL DEFAULT 0)",
            "CREATE TABLE IF NOT EXISTS \"LOCAL_REFRESH_META\" (" +
                    "\"META_KEY\" TEXT PRIMARY KEY NOT NULL," +
                    "\"META_VALUE\" TEXT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS \"LOCAL_BASELINE_QUEUE\" (" +
                    "\"_id\" INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "\"BATCH_KEY\" TEXT NOT NULL," +
                    "\"SOURCE_TYPE\" TEXT NOT NULL," +
                    "\"SOURCE_KEY\" TEXT NOT NULL," +
                    "\"QUERY_SIGNATURE\" TEXT NOT NULL," +
                    "\"METHOD\" TEXT NOT NULL," +
                    "\"ADDED_AT\" INTEGER NOT NULL," +
                    "\"STATUS\" TEXT NOT NULL DEFAULT 'PENDING'," +
                    "\"ERROR\" TEXT NOT NULL DEFAULT ''," +
                    "UNIQUE(\"SOURCE_TYPE\",\"SOURCE_KEY\"))",
            "CREATE INDEX IF NOT EXISTS \"IDX_LOCAL_BASELINE_PENDING\" ON " +
                    "\"LOCAL_BASELINE_QUEUE\" (\"STATUS\",\"_id\")"
    };

    private static final String[] DROP_SQL = {
            "DROP TABLE IF EXISTS \"LOCAL_BASELINE_QUEUE\"",
            "DROP TABLE IF EXISTS \"LOCAL_REFRESH_META\"",
            "DROP TABLE IF EXISTS \"LOCAL_REFRESH_JOB\"",
            "DROP TABLE IF EXISTS \"LOCAL_UNREAD_GALLERY\"",
            "DROP TABLE IF EXISTS \"LOCAL_UPDATE_STATE\"",
            "DROP TABLE IF EXISTS \"SUBSCRIPTION_TAG_UPDATE_STATE\"",
            "DROP TABLE IF EXISTS \"FEED_CHECKPOINT\"",
            "DROP TABLE IF EXISTS \"SUBSCRIPTION_TAG_CACHE\"",
            "DROP TABLE IF EXISTS \"LOCAL_FOLLOW_TAG\""
    };

    private SubscriptionSchema() {}

    public static void createTables(Database db) {
        for (String sql : SQL) db.execSQL(sql);
    }

    public static void createTables(SQLiteDatabase db) {
        for (String sql : SQL) db.execSQL(sql);
    }

    public static void dropTables(Database db) {
        for (String sql : DROP_SQL) db.execSQL(sql);
    }
}
