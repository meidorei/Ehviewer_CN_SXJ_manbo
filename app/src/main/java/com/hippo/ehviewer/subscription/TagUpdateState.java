package com.hippo.ehviewer.subscription;

public final class TagUpdateState {
    public enum State { EXACT, LOWER_BOUND, UNKNOWN }

    public final String tagName;
    public final int count;
    public final State state;
    public final long checkedAt;

    public TagUpdateState(String tagName, int count, State state, long checkedAt) {
        this.tagName = SubscriptionRepository.normalizeTagName(tagName);
        this.count = Math.max(0, count);
        this.state = state;
        this.checkedAt = checkedAt;
    }

    public String displayCount() {
        if (state == State.UNKNOWN) return "?";
        return Integer.toString(count) + (state == State.LOWER_BOUND ? "+" : "");
    }
}
