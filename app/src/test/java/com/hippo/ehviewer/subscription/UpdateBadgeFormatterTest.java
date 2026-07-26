package com.hippo.ehviewer.subscription;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class UpdateBadgeFormatterTest {
    @Test public void neverCheckedHasNoBadge() {
        assertEquals("bookmark", UpdateBadgeFormatter.format("bookmark", null));
    }

    @Test public void zeroBaselineRemainsVisible() {
        assertEquals("0  ·  bookmark", UpdateBadgeFormatter.format("bookmark", "0"));
    }

    @Test public void countAndReasonRemainVisible() {
        assertEquals("20+  ●  bookmark", UpdateBadgeFormatter.format("bookmark", "20+"));
        assertEquals("1  ●  bookmark", UpdateBadgeFormatter.format("bookmark", "1"));
        assertEquals("语言条件冲突  ·  bookmark",
                UpdateBadgeFormatter.format("bookmark", "语言条件冲突"));
    }
}
