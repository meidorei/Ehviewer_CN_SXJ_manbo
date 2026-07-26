package com.hippo.ehviewer.subscription;

import android.database.Cursor;

import com.hippo.ehviewer.EhDB;

import org.greenrobot.greendao.database.Database;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.dao.QuickSearch;

/** Device-global local follows. Server Watched tags never enter this repository. */
public final class LocalFollowRepository {
    public static final String SOURCE_FOLLOW = "LOCAL_FOLLOW";
    public static final String SOURCE_BOOKMARK = "QUICK_SEARCH";
    public static final String CHECKPOINT_FOLLOW = "LOCAL_FOLLOW_SYNC";
    public static final String CHECKPOINT_BOOKMARK = "BOOKMARK_SYNC";
    public static final String FIXED_CHINESE_SIGNATURE =
            QuerySignatureFactory.create("language:chinese$", true);
    private static final LocalFollowRepository INSTANCE = new LocalFollowRepository();

    private LocalFollowRepository() {}

    public static LocalFollowRepository getInstance() {
        return INSTANCE;
    }

    public List<String> getAll() {
        List<String> result = new ArrayList<>();
        try (Cursor cursor = EhDB.getDatabase().rawQuery(
                "SELECT TAG_NAME FROM LOCAL_FOLLOW_TAG ORDER BY TAG_NAME", null)) {
            while (cursor.moveToNext()) result.add(cursor.getString(0));
        }
        return Collections.unmodifiableList(result);
    }

    public Set<String> getSet() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(getAll()));
    }

    public boolean contains(String rawTag) {
        String tag = SubscriptionRepository.normalizeTagName(rawTag);
        if (tag.isEmpty()) return false;
        try (Cursor cursor = EhDB.getDatabase().rawQuery(
                "SELECT 1 FROM LOCAL_FOLLOW_TAG WHERE TAG_NAME=? LIMIT 1",
                new String[]{tag})) {
            return cursor.moveToFirst();
        }
    }

    public boolean add(String rawTag) {
        String tag = SubscriptionRepository.normalizeTagName(rawTag);
        if (!LocalFollowJson.isValidStandardTag(tag)) return false;
        Database db = EhDB.getDatabase();
        long now = System.currentTimeMillis();
        boolean inserted = false;
        db.beginTransaction();
        try {
            db.execSQL("INSERT INTO LOCAL_FOLLOW_TAG(TAG_NAME,ADDED_AT,LAST_CHECKED_AT) VALUES(?,?,0)",
                    new Object[]{tag, now});
            initializeFollowBaseline(tag, now, LocalBaselineQueue.METHOD_FOLLOW_TAG,
                    batchKey("follow", now));
            db.setTransactionSuccessful();
            inserted = true;
        } catch (RuntimeException duplicate) {
            inserted = false;
        } finally {
            db.endTransaction();
        }
        if (inserted) SubscriptionSnapshot.refreshFromDatabase();
        return inserted;
    }

    public void remove(String rawTag) {
        String tag = SubscriptionRepository.normalizeTagName(rawTag);
        if (tag.isEmpty()) return;
        Database db = EhDB.getDatabase();
        db.beginTransaction();
        try {
            db.execSQL("DELETE FROM LOCAL_FOLLOW_TAG WHERE TAG_NAME=?", new Object[]{tag});
            db.execSQL("DELETE FROM LOCAL_UPDATE_STATE WHERE SOURCE_TYPE=? AND SOURCE_KEY=?",
                    new Object[]{SOURCE_FOLLOW, tag});
            db.execSQL("DELETE FROM LOCAL_UNREAD_GALLERY WHERE SOURCE_TYPE=? AND SOURCE_KEY=?",
                    new Object[]{SOURCE_FOLLOW, tag});
            db.execSQL("DELETE FROM FEED_CHECKPOINT WHERE " +
                            "(SOURCE_TYPE LIKE 'LOCAL_FOLLOW%' OR SOURCE_TYPE='SUBSCRIPTION_TAG_SEEN') " +
                            "AND SOURCE_KEY=?",
                    new Object[]{tag});
            LocalBaselineQueue.delete(SOURCE_FOLLOW, tag);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        SubscriptionSnapshot.refreshFromDatabase();
    }

    public ImportResult importTags(Set<String> incoming, boolean replace) {
        Database db = EhDB.getDatabase();
        Set<String> existing = new LinkedHashSet<>(getAll());
        int before = existing.size();
        Set<String> added = new LinkedHashSet<>(incoming);
        added.removeAll(existing);
        db.beginTransaction();
        try {
            if (replace) {
                for (String old : existing) {
                    if (!incoming.contains(old)) {
                        db.execSQL("DELETE FROM LOCAL_FOLLOW_TAG WHERE TAG_NAME=?",
                                new Object[]{old});
                        db.execSQL("DELETE FROM LOCAL_UPDATE_STATE WHERE SOURCE_TYPE=? AND SOURCE_KEY=?",
                                new Object[]{SOURCE_FOLLOW, old});
                        db.execSQL("DELETE FROM LOCAL_UNREAD_GALLERY WHERE SOURCE_TYPE=? AND SOURCE_KEY=?",
                                new Object[]{SOURCE_FOLLOW, old});
                        db.execSQL("DELETE FROM FEED_CHECKPOINT WHERE " +
                                        "(SOURCE_TYPE LIKE 'LOCAL_FOLLOW%' OR " +
                                        "SOURCE_TYPE='SUBSCRIPTION_TAG_SEEN') AND SOURCE_KEY=?",
                                new Object[]{old});
                        LocalBaselineQueue.delete(SOURCE_FOLLOW, old);
                    }
                }
            }
            long now = System.currentTimeMillis();
            String batch = batchKey("follow-import", now);
            for (String tag : added) {
                db.execSQL("INSERT INTO LOCAL_FOLLOW_TAG(TAG_NAME,ADDED_AT,LAST_CHECKED_AT) VALUES(?,?,0)",
                        new Object[]{tag, now});
                initializeFollowBaseline(tag, now, LocalBaselineQueue.METHOD_FOLLOW_GLOBAL,
                        batch);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        SubscriptionSnapshot.refreshFromDatabase();
        int after = getAll().size();
        return new ImportResult(before, after, replace, added.size());
    }

    /**
     * Initializes rows inserted by database restore without touching retained tags.
     * The caller may already own the surrounding database transaction.
     */
    public void initializeImportedFollowTags(Collection<String> tags, long addedAt) {
        if (tags == null || tags.isEmpty()) return;
        String batch = batchKey("follow-restore", addedAt);
        for (String raw : tags) {
            String tag = SubscriptionRepository.normalizeTagName(raw);
            if (!tag.isEmpty()) {
                initializeFollowBaseline(tag, addedAt,
                        LocalBaselineQueue.METHOD_FOLLOW_GLOBAL, batch);
            }
        }
    }

    public void initializeBookmark(QuickSearch search, long addedAt) {
        BookmarkUpdatePolicy.Result policy = BookmarkUpdatePolicy.resolve(search);
        if (search == null || search.id == null || policy.signature.isEmpty()) return;
        String key = Long.toString(search.id);
        if (!policy.supported) {
            markError(SOURCE_BOOKMARK, key, policy.signature, policy.error);
            return;
        }
        writeProvisionalState(SOURCE_BOOKMARK, key, policy.signature, addedAt);
        LocalBaselineQueue.enqueue(batchKey("bookmark", addedAt), SOURCE_BOOKMARK, key,
                policy.signature, LocalBaselineQueue.METHOD_BOOKMARK, addedAt);
    }

    private void initializeFollowBaseline(String tag, long addedAt, String method,
                                          String batch) {
        writeProvisionalState(SOURCE_FOLLOW, tag, FIXED_CHINESE_SIGNATURE, addedAt);
        LocalBaselineQueue.enqueue(batch, SOURCE_FOLLOW, tag, FIXED_CHINESE_SIGNATURE,
                method, addedAt);
    }

    private static String batchKey(String prefix, long now) {
        return prefix + '-' + now + '-' + System.nanoTime();
    }

    public TagUpdateState readState(String sourceType, String sourceKey, String signature) {
        try (Cursor cursor = EhDB.getDatabase().rawQuery(
                "SELECT COUNT,COUNT_STATE,CHECKED_AT FROM LOCAL_UPDATE_STATE " +
                        "WHERE SOURCE_TYPE=? AND SOURCE_KEY=? AND QUERY_SIGNATURE=?",
                new String[]{sourceType, sourceKey, signature})) {
            if (!cursor.moveToFirst()) {
                return new TagUpdateState(sourceKey, 0, TagUpdateState.State.EXACT, 0);
            }
            return new TagUpdateState(sourceKey, cursor.getInt(0),
                    TagUpdateState.State.valueOf(cursor.getString(1)), cursor.getLong(2));
        }
    }

    public int countCapped(String sourceType) {
        try (Cursor cursor = EhDB.getDatabase().rawQuery(
                "SELECT COUNT(*) FROM LOCAL_UPDATE_STATE WHERE SOURCE_TYPE=? " +
                        "AND COUNT_STATE='LOWER_BOUND'",
                new String[]{sourceType})) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    public void writeState(String sourceType, String sourceKey, String signature,
                           int count, TagUpdateState.State state, String error) {
        writeStateAt(sourceType, sourceKey, signature, count, state, error,
                System.currentTimeMillis());
    }

    public void writeProvisionalState(String sourceType, String sourceKey, String signature,
                                      long addedAt) {
        writeStateAt(sourceType, sourceKey, signature, 0, TagUpdateState.State.EXACT, "",
                addedAt);
    }

    private void writeStateAt(String sourceType, String sourceKey, String signature,
                              int count, TagUpdateState.State state, String error,
                              long checkedAt) {
        EhDB.getDatabase().execSQL(
                "INSERT OR REPLACE INTO LOCAL_UPDATE_STATE " +
                        "(SOURCE_TYPE,SOURCE_KEY,QUERY_SIGNATURE,COUNT,COUNT_STATE,CHECKED_AT,ERROR) " +
                        "VALUES(?,?,?,?,?,?,?)",
                new Object[]{sourceType, sourceKey, signature, count, state.name(),
                        checkedAt, error == null ? "" : error});
    }

    public void clearState(String sourceType, String sourceKey) {
        Database db = EhDB.getDatabase();
        db.beginTransaction();
        try {
            db.execSQL("UPDATE LOCAL_UPDATE_STATE SET COUNT=0,COUNT_STATE='EXACT',ERROR='',CHECKED_AT=? " +
                            "WHERE SOURCE_TYPE=? AND SOURCE_KEY=?",
                    new Object[]{System.currentTimeMillis(), sourceType, sourceKey});
            db.execSQL("DELETE FROM LOCAL_UNREAD_GALLERY WHERE SOURCE_TYPE=? AND SOURCE_KEY=?",
                    new Object[]{sourceType, sourceKey});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void deleteBookmarkState(long id) {
        String key = Long.toString(id);
        Database db = EhDB.getDatabase();
        db.beginTransaction();
        try {
            db.execSQL("DELETE FROM LOCAL_UPDATE_STATE WHERE SOURCE_TYPE=? AND SOURCE_KEY=?",
                    new Object[]{SOURCE_BOOKMARK, key});
            db.execSQL("DELETE FROM LOCAL_UNREAD_GALLERY WHERE SOURCE_TYPE=? AND SOURCE_KEY=?",
                    new Object[]{SOURCE_BOOKMARK, key});
            db.execSQL("DELETE FROM FEED_CHECKPOINT WHERE SOURCE_TYPE IN " +
                            "('BOOKMARK_SYNC','QUICK_SEARCH') AND SOURCE_KEY=?",
                    new Object[]{key});
            LocalBaselineQueue.delete(SOURCE_BOOKMARK, key);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * A bookmark rename keeps its signature and state. Any query change gets a clean
     * zero baseline and cannot inherit unread GIDs or cursors from the former query.
     */
    public void prepareBookmarkSignature(String sourceKey, String signature) {
        if (sourceKey == null || signature == null || signature.isEmpty()) return;
        Database db = EhDB.getDatabase();
        boolean changed;
        try (Cursor cursor = db.rawQuery(
                "SELECT 1 FROM LOCAL_UPDATE_STATE WHERE SOURCE_TYPE=? AND SOURCE_KEY=? " +
                        "AND QUERY_SIGNATURE<>? LIMIT 1",
                new String[]{SOURCE_BOOKMARK, sourceKey, signature})) {
            changed = cursor.moveToFirst();
        }
        if (!changed) return;
        db.beginTransaction();
        try {
            db.execSQL("DELETE FROM LOCAL_UPDATE_STATE WHERE SOURCE_TYPE=? AND SOURCE_KEY=?",
                    new Object[]{SOURCE_BOOKMARK, sourceKey});
            db.execSQL("DELETE FROM LOCAL_UNREAD_GALLERY WHERE SOURCE_TYPE=? AND SOURCE_KEY=?",
                    new Object[]{SOURCE_BOOKMARK, sourceKey});
            db.execSQL("DELETE FROM FEED_CHECKPOINT WHERE SOURCE_TYPE=? " +
                            "AND SOURCE_KEY=?",
                    new Object[]{CHECKPOINT_BOOKMARK, sourceKey});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Commits one newest-first page atomically. Missing boundaries become a capped 20+ result,
     * while a first source visit only establishes a zero baseline.
     */
    public TagUpdateState commitPage(String sourceType, String sourceKey, String signature,
                                     CheckpointKey checkpointKey, List<GalleryInfo> galleries,
                                     boolean forceCapped) {
        return commitPage(sourceType, sourceKey, signature, checkpointKey, galleries,
                forceCapped, false);
    }

    public TagUpdateState commitPage(String sourceType, String sourceKey, String signature,
                                     CheckpointKey checkpointKey, List<GalleryInfo> galleries,
                                     boolean forceCapped, boolean boundaryProven) {
        if (galleries == null || galleries.isEmpty()) {
            throw new IllegalArgumentException("empty gallery page");
        }
        return commitPrepared(sourceType, sourceKey, signature, checkpointKey,
                boundaryOf(galleries), galleries, forceCapped, boundaryProven);
    }

    /**
     * Commits matches found in a shared chronological scan while advancing the item's cursor to
     * the common scan top. The match list may be empty when the old boundary was still covered.
     */
    public TagUpdateState commitGlobalScan(
            String sourceType, String sourceKey, String signature,
            CheckpointKey checkpointKey, FeedBoundary scanTop,
            List<GalleryInfo> matchingGalleries) {
        if (scanTop == null || scanTop.isEmpty()) {
            throw new IllegalArgumentException("empty global scan boundary");
        }
        return commitPrepared(sourceType, sourceKey, signature, checkpointKey, scanTop,
                matchingGalleries == null ? Collections.emptyList() : matchingGalleries,
                false, true);
    }

    private TagUpdateState commitPrepared(
            String sourceType, String sourceKey, String signature,
            CheckpointKey checkpointKey, FeedBoundary newest,
            List<GalleryInfo> matchingGalleries, boolean forceCapped,
            boolean boundaryProven) {
        FeedCheckpoint old = SubscriptionRepository.getInstance().readCheckpoint(checkpointKey);
        boolean baseline = old.current.isEmpty();
        boolean reached = baseline || boundaryProven;
        List<Long> newGids = new ArrayList<>();
        if (!baseline) {
            for (GalleryInfo gallery : matchingGalleries) {
                if (gallery.postedTimestamp <= 0) {
                    throw new IllegalArgumentException("gallery timestamp unavailable");
                }
                if (old.current.isFirstOld(gallery.postedTimestamp, gallery.gid)) {
                    reached = true;
                    break;
                }
                if (old.current.isNew(gallery.postedTimestamp, gallery.gid)) {
                    newGids.add(gallery.gid);
                }
            }
        }
        boolean capped = !baseline && (forceCapped || !reached);
        Database db = EhDB.getDatabase();
        db.beginTransaction();
        try {
            SubscriptionRepository.getInstance().advanceCheckpoint(checkpointKey, newest);
            if (!baseline) {
                for (Long gid : newGids) {
                    db.execSQL("INSERT OR IGNORE INTO LOCAL_UNREAD_GALLERY" +
                                    "(SOURCE_TYPE,SOURCE_KEY,GID) VALUES(?,?,?)",
                            new Object[]{sourceType, sourceKey, gid});
                }
                db.execSQL("DELETE FROM LOCAL_UNREAD_GALLERY WHERE SOURCE_TYPE=? AND SOURCE_KEY=? " +
                                "AND GID NOT IN (SELECT GID FROM LOCAL_UNREAD_GALLERY " +
                                "WHERE SOURCE_TYPE=? AND SOURCE_KEY=? LIMIT 21)",
                        new Object[]{sourceType, sourceKey, sourceType, sourceKey});
            }
            int count = 0;
            try (Cursor cursor = db.rawQuery(
                    "SELECT COUNT(*) FROM LOCAL_UNREAD_GALLERY WHERE SOURCE_TYPE=? AND SOURCE_KEY=?",
                    new String[]{sourceType, sourceKey})) {
                if (cursor.moveToFirst()) count = cursor.getInt(0);
            }
            TagUpdateState current = readState(sourceType, sourceKey, signature);
            capped = capped || current.state == TagUpdateState.State.LOWER_BOUND
                    || count > TagUpdateState.DISPLAY_CAP;
            writeState(sourceType, sourceKey, signature,
                    baseline ? current.count : count,
                    capped ? TagUpdateState.State.LOWER_BOUND : TagUpdateState.State.EXACT, "");
            db.execSQL("UPDATE LOCAL_FOLLOW_TAG SET LAST_CHECKED_AT=? WHERE TAG_NAME=?",
                    new Object[]{System.currentTimeMillis(), sourceKey});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return readState(sourceType, sourceKey, signature);
    }

    public void establishBaseline(String sourceType, String sourceKey, String signature,
                                  CheckpointKey checkpointKey, FeedBoundary boundary) {
        if (boundary == null || boundary.isEmpty()) return;
        Database db = EhDB.getDatabase();
        db.beginTransaction();
        try {
            SubscriptionRepository.getInstance().establishCheckpoint(checkpointKey, boundary);
            TagUpdateState current = readState(sourceType, sourceKey, signature);
            if (current.checkedAt == 0) {
                writeState(sourceType, sourceKey, signature, 0,
                        TagUpdateState.State.EXACT, "");
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void ensureProvisionalCheckpoint(String sourceType, String sourceKey,
                                            String signature, CheckpointKey checkpointKey,
                                            long addedAt) {
        FeedCheckpoint checkpoint = SubscriptionRepository.getInstance()
                .readCheckpoint(checkpointKey);
        if (checkpoint.current.isEmpty()) {
            establishBaseline(sourceType, sourceKey, signature, checkpointKey,
                    BaselineBoundaryPolicy.provisional(addedAt));
        }
    }

    public void markError(String sourceType, String sourceKey, String signature, String error) {
        TagUpdateState current = readState(sourceType, sourceKey, signature);
        writeStateAt(sourceType, sourceKey, signature, current.count, current.state, error,
                current.checkedAt == 0 ? System.currentTimeMillis() : current.checkedAt);
    }

    public void markCheckedWithoutChanges(String sourceType, String sourceKey, String signature) {
        TagUpdateState current = readState(sourceType, sourceKey, signature);
        writeState(sourceType, sourceKey, signature, current.count, current.state, "");
    }

    public static FeedBoundary boundaryOf(List<GalleryInfo> galleries) {
        if (galleries == null || galleries.isEmpty()) return FeedBoundary.EMPTY;
        long newest = galleries.get(0).postedTimestamp;
        Set<Long> gids = new LinkedHashSet<>();
        for (GalleryInfo gallery : galleries) {
            if (gallery.postedTimestamp != newest) break;
            gids.add(gallery.gid);
        }
        return newest <= 0 ? FeedBoundary.EMPTY : new FeedBoundary(newest, gids);
    }

    public static final class ImportResult {
        public final int before;
        public final int after;
        public final boolean replaced;
        public final int added;

        ImportResult(int before, int after, boolean replaced, int added) {
            this.before = before;
            this.after = after;
            this.replaced = replaced;
            this.added = added;
        }
    }
}
