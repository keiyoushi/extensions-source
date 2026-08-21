package eu.kanade.tachiyomi.extension.all.stashapp;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class StashAppDateTest {
    @Test
    public void parsesUtcAndOffsetRfc3339Timestamps() {
        assertEquals(1_704_067_200_000L, StashAppKt.parseRFC3339Millis("2024-01-01T00:00:00Z"));
        assertEquals(1_704_067_200_000L, StashAppKt.parseRFC3339Millis("2024-01-01T02:00:00+02:00"));
    }

    @Test
    public void returnsZeroForMalformedTimestamp() {
        assertEquals(0L, StashAppKt.parseRFC3339Millis("not-a-timestamp"));
    }
}
