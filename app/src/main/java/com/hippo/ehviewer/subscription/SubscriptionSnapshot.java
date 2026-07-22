package com.hippo.ehviewer.subscription;

import com.hippo.ehviewer.client.data.userTag.UserTag;
import com.hippo.ehviewer.client.data.userTag.UserTagList;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** Process-local immutable O(1) tag lookup used by detail rendering. */
public final class SubscriptionSnapshot {
    private static volatile Set<String> subscribed = Collections.emptySet();
    private static final AtomicLong VERSION = new AtomicLong();

    private SubscriptionSnapshot() {}

    public static long replace(UserTagList list) {
        Set<String> next = new HashSet<>();
        if (list != null && list.userTags != null) {
            for (UserTag tag : list.userTags) {
                if (tag.watched && !tag.hidden) next.add(SubscriptionRepository.normalizeTagName(tag.tagName));
            }
        }
        subscribed = Collections.unmodifiableSet(next);
        return VERSION.incrementAndGet();
    }

    public static boolean contains(String rawTag) {
        return subscribed.contains(SubscriptionRepository.normalizeTagName(rawTag));
    }

    public static long version() { return VERSION.get(); }
}
