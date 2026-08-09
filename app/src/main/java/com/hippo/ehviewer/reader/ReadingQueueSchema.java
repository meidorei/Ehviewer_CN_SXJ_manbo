package com.hippo.ehviewer.reader;

import android.database.sqlite.SQLiteDatabase;

import org.greenrobot.greendao.database.Database;

/** Additive schema owned by the local reading queue. */
public final class ReadingQueueSchema {
    public static final String TABLE = "READING_QUEUE";

    private static final String CREATE_TABLE =
            "CREATE TABLE IF NOT EXISTS \"READING_QUEUE\" (" +
                    "\"GID\" INTEGER PRIMARY KEY NOT NULL," +
                    "\"QUEUE_ORDER\" INTEGER NOT NULL)";
    private static final String CREATE_ORDER_INDEX =
            "CREATE UNIQUE INDEX IF NOT EXISTS \"IDX_READING_QUEUE_ORDER\" ON " +
                    "\"READING_QUEUE\" (\"QUEUE_ORDER\")";

    private ReadingQueueSchema() {}

    public static void createTables(Database db) {
        db.execSQL(CREATE_TABLE);
        db.execSQL(CREATE_ORDER_INDEX);
    }

    public static void createTables(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE);
        db.execSQL(CREATE_ORDER_INDEX);
    }

    public static void upgradeToV12(SQLiteDatabase db) {
        createTables(db);
    }

    public static void dropTables(Database db) {
        db.execSQL("DROP TABLE IF EXISTS \"READING_QUEUE\"");
    }
}
