package com.hippo.ehviewer.subscription;

/** Namespaces sync and local-seen checkpoints without changing the existing table schema. */
public final class FeedCheckpointKeys {
    private FeedCheckpointKeys() {}

    public static CheckpointKey seen(String accountKey, FeedSourceContext context) {
        String type;
        if (context.type == FeedSourceContext.Type.SUBSCRIPTION_AGGREGATE) {
            type = "SUBSCRIPTION_AGGREGATE_SEEN";
        } else if (context.type == FeedSourceContext.Type.SUBSCRIPTION_TAG) {
            type = "SUBSCRIPTION_TAG_SEEN";
        } else {
            type = context.type.name();
        }
        String source = context.type == FeedSourceContext.Type.SUBSCRIPTION_TAG
                ? SubscriptionRepository.normalizeTagName(context.sourceKey) : context.sourceKey;
        return new CheckpointKey(accountKey, type, source, context.querySignature);
    }

    public static CheckpointKey subscriptionSync(String accountKey, FeedSourceContext context) {
        return new CheckpointKey(accountKey, FeedSourceContext.Type.SUBSCRIPTION_AGGREGATE.name(),
                "watched", context.querySignature);
    }
}
