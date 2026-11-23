package com.crispprep.reviews.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitFilterTest {

    @Test
    void blocksAfterTheLimitAndResetsNextMinute() {
        RateLimitFilter f = new RateLimitFilter();
        RateLimitFilter.Rule r = new RateLimitFilter.Rule("t", 3);
        assertTrue(f.allow(r, "1.2.3.4", 100));
        assertTrue(f.allow(r, "1.2.3.4", 100));
        assertTrue(f.allow(r, "1.2.3.4", 100));
        assertFalse(f.allow(r, "1.2.3.4", 100));
        assertTrue(f.allow(r, "5.6.7.8", 100));    // other people are not affected
        assertTrue(f.allow(r, "1.2.3.4", 101));    // new minute, new window
    }
}
