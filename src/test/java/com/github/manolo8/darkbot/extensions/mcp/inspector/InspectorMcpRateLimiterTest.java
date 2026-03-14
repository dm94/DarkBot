package com.github.manolo8.darkbot.extensions.mcp.inspector;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class InspectorMcpRateLimiterTest {

    @Test
    public void testRateLimitByClient() {
        InspectorMcpRateLimiter limiter = new InspectorMcpRateLimiter(2, 5_000);

        Assertions.assertTrue(limiter.allow("127.0.0.1"));
        Assertions.assertTrue(limiter.allow("127.0.0.1"));
        Assertions.assertFalse(limiter.allow("127.0.0.1"));
        Assertions.assertTrue(limiter.allow("127.0.0.2"));
    }

    @Test
    public void testRateLimitWindowResets() throws Exception {
        InspectorMcpRateLimiter limiter = new InspectorMcpRateLimiter(1, 10);

        Assertions.assertTrue(limiter.allow("127.0.0.1"));
        Assertions.assertFalse(limiter.allow("127.0.0.1"));
        Thread.sleep(25);
        Assertions.assertTrue(limiter.allow("127.0.0.1"));
    }
}
