package com.hippo.ehviewer.subscription;

import com.hippo.ehviewer.client.data.GalleryInfo;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure aggregation logic; networking and persistence deliberately live elsewhere. */
public final class SubscriptionUpdateCalculator {
    public static final class Outcome {
        public final boolean complete;
        public final boolean baselineOnly;
        public final FeedBoundary newBoundary;
        public final Map<String, TagUpdateState> states;
        public final String error;

        Outcome(boolean complete, boolean baselineOnly, FeedBoundary boundary,
                Map<String, TagUpdateState> states, String error) {
            this.complete = complete;
            this.baselineOnly = baselineOnly;
            this.newBoundary = boundary;
            this.states = states;
            this.error = error;
        }
    }

    private SubscriptionUpdateCalculator() {}

    public static Outcome calculate(FeedBoundary oldBoundary, List<GalleryInfo> galleries,
                                    Set<String> subscribedTags, boolean pageLimitReached) {
        long checkedAt = System.currentTimeMillis();
        Map<String, Integer> counts = new HashMap<>();
        for (String tag : subscribedTags) counts.put(SubscriptionRepository.normalizeTagName(tag), 0);
        if (galleries.isEmpty()) {
            return new Outcome(false, oldBoundary.isEmpty(), FeedBoundary.EMPTY,
                    Collections.emptyMap(), "empty response");
        }

        long newest = 0;
        Set<Long> newestGids = new HashSet<>();
        Set<Long> seen = new HashSet<>();
        boolean foundBoundary = oldBoundary.isEmpty();
        for (GalleryInfo gallery : galleries) {
            if (!seen.add(gallery.gid)) continue;
            if (gallery.postedTimestamp <= 0 || gallery.simpleTags == null) {
                return new Outcome(false, false, FeedBoundary.EMPTY,
                        Collections.emptyMap(), "incomplete gdata");
            }
            if (gallery.postedTimestamp > newest) {
                newest = gallery.postedTimestamp;
                newestGids.clear();
                newestGids.add(gallery.gid);
            } else if (gallery.postedTimestamp == newest) {
                newestGids.add(gallery.gid);
            }
            if (!oldBoundary.isEmpty() && oldBoundary.isFirstOld(gallery.postedTimestamp, gallery.gid)) {
                foundBoundary = true;
                break;
            }
            if (!oldBoundary.isEmpty()) {
                for (String raw : gallery.simpleTags) {
                    String tag = SubscriptionRepository.normalizeTagName(raw);
                    if (counts.containsKey(tag)) counts.put(tag, counts.get(tag) + 1);
                }
            }
        }

        FeedBoundary next = new FeedBoundary(newest, newestGids);
        Map<String, TagUpdateState> states = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            TagUpdateState.State state = foundBoundary ? TagUpdateState.State.EXACT
                    : entry.getValue() > 0 ? TagUpdateState.State.LOWER_BOUND : TagUpdateState.State.UNKNOWN;
            if (oldBoundary.isEmpty()) state = TagUpdateState.State.EXACT;
            states.put(entry.getKey(), new TagUpdateState(entry.getKey(), entry.getValue(), state, checkedAt));
        }
        boolean validEnd = foundBoundary || pageLimitReached;
        return new Outcome(validEnd, oldBoundary.isEmpty(), next,
                Collections.unmodifiableMap(states), validEnd ? "" : "boundary not reached");
    }
}
