package com.hippo.ehviewer.subscription;

import android.database.sqlite.SQLiteDatabase;

import org.greenrobot.greendao.database.Database;

/** Incremental schema owned by the follow-update feature. */
public final class SubscriptionSchema {
    private static final String[] SQL = {
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
                    "UNIQUE(\"ACCOUNT_KEY\",\"TAG_NAME\",\"QUERY_SIGNATURE\"))"
    };

    private SubscriptionSchema() {}

    public static void createTables(Database db) {
        for (String sql : SQL) db.execSQL(sql);
    }

    public static void createTables(SQLiteDatabase db) {
        for (String sql : SQL) db.execSQL(sql);
    }
}
