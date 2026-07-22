package com.hippo.ehviewer.subscription;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.hippo.ehviewer.client.data.GalleryInfo;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

public class SubscriptionUpdateCalculatorTest {
    private static GalleryInfo gallery(long gid, long time, String... tags) {
        GalleryInfo value = new GalleryInfo();
        value.gid = gid;
        value.postedTimestamp = time;
        value.simpleTags = tags;
        return value;
    }

    @Test public void firstRunCreatesZeroBaseline() {
        SubscriptionUpdateCalculator.Outcome result = SubscriptionUpdateCalculator.calculate(
                FeedBoundary.EMPTY, Arrays.asList(gallery(2, 20, "artist:a"), gallery(1, 19, "artist:a")),
                Collections.singleton("artist:a"), false);
        assertTrue(result.complete);
        assertTrue(result.baselineOnly);
        assertEquals("0", result.states.get("artist:a").displayCount());
    }

    @Test public void sameSecondBoundaryAndMultipleTagsAreCounted() {
        FeedBoundary old = new FeedBoundary(10, Collections.singleton(1L));
        SubscriptionUpdateCalculator.Outcome result = SubscriptionUpdateCalculator.calculate(old,
                Arrays.asList(gallery(3, 11, "artist:a", "group:b"),
                        gallery(2, 10, "artist:a"), gallery(1, 10, "artist:a")),
                new HashSet<>(Arrays.asList("artist:a", "group:b")), false);
        assertTrue(result.complete);
        assertEquals(2, result.states.get("artist:a").count);
        assertEquals(1, result.states.get("group:b").count);
    }

    @Test public void incompleteGdataNeverCommits() {
        GalleryInfo invalid = gallery(3, 0, "artist:a");
        SubscriptionUpdateCalculator.Outcome result = SubscriptionUpdateCalculator.calculate(
                new FeedBoundary(10, Collections.singleton(1L)), Collections.singletonList(invalid),
                Collections.singleton("artist:a"), true);
        assertFalse(result.complete);
    }
}
