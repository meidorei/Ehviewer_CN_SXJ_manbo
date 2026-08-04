package com.hippo.ehviewer.subscription;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FeedBoundaryDecorationTest {
    @Test
    public void markerStyleUsesEmphasizedDimensionsWithoutChangingSpacing() {
        assertEquals(4f, FeedBoundaryDecoration.lineWidthPx(2f), 0f);
        assertEquals(42f, FeedBoundaryDecoration.textSizePx(3f), 0f);
        assertEquals(64, FeedBoundaryDecoration.markerHeightPx(2f));
    }
}
