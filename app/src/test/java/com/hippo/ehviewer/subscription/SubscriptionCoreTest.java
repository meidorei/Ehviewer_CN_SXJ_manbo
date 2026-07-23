package com.hippo.ehviewer.subscription;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;

public class SubscriptionCoreTest {
    @Test public void gidSerializationIsStableAndUnique() {
        HashSet<Long> gids = new HashSet<>(Arrays.asList(9L, 2L, 9L, 5L));
        assertEquals("2,5,9", SubscriptionRepository.serializeGids(gids));
        assertEquals(gids, SubscriptionRepository.parseGids("9,2,5,2"));
    }

    @Test public void boundaryHandlesSameSecondGidsAndUnknownTime() {
        FeedBoundary boundary = new FeedBoundary(100, new HashSet<>(Arrays.asList(1L, 2L)));
        assertTrue(boundary.isNew(101, 1));
        assertTrue(boundary.isNew(100, 3));
        assertFalse(boundary.isNew(100, 2));
        assertTrue(boundary.isFirstOld(99, 8));
        assertFalse(boundary.isNew(0, 8));
    }

    @Test public void querySignatureSeparatesActuallyAppliedPolicy() {
        assertNotEquals(QuerySignatureFactory.create("foo", false),
                QuerySignatureFactory.create("foo", true));
        assertEquals(QuerySignatureFactory.create("foo", true),
                QuerySignatureFactory.create("foo", true));
    }

    @Test public void syncAggregateAndPerTagSeenCheckpointsNeverCollide() {
        FeedSourceContext aggregate = new FeedSourceContext(
                FeedSourceContext.Type.SUBSCRIPTION_AGGREGATE, "watched", "q");
        FeedSourceContext tag = new FeedSourceContext(
                FeedSourceContext.Type.SUBSCRIPTION_TAG, " Artist:A ", "q");
        CheckpointKey sync = FeedCheckpointKeys.subscriptionSync("account", aggregate);
        CheckpointKey aggregateSeen = FeedCheckpointKeys.seen("account", aggregate);
        CheckpointKey tagSeen = FeedCheckpointKeys.seen("account", tag);
        assertNotEquals(sync.sourceType, aggregateSeen.sourceType);
        assertNotEquals(sync.sourceType, tagSeen.sourceType);
        assertNotEquals(aggregateSeen.sourceType, tagSeen.sourceType);
        assertEquals("artist:a", tagSeen.sourceKey);
    }
}
