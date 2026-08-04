package com.hippo.ehviewer.subscription;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Pure GID-based planning for clearing unread entries shared by saved searches. */
final class BookmarkUnreadClearPolicy {
    private BookmarkUnreadClearPolicy() {}

    static Map<String, Integer> remainingCounts(
            String sourceType, Set<Long> openedGids,
            Map<String, ? extends Collection<Long>> unreadBySource) {
        if (!LocalFollowRepository.SOURCE_BOOKMARK.equals(sourceType)
                || openedGids == null || openedGids.isEmpty()
                || unreadBySource == null || unreadBySource.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, ? extends Collection<Long>> entry
                : unreadBySource.entrySet()) {
            Collection<Long> unread = entry.getValue();
            if (unread == null || unread.isEmpty()) continue;
            int removed = 0;
            for (Long gid : unread) {
                if (openedGids.contains(gid)) removed++;
            }
            if (removed > 0) {
                result.put(entry.getKey(), Math.max(0, unread.size() - removed));
            }
        }
        return Collections.unmodifiableMap(result);
    }
}
