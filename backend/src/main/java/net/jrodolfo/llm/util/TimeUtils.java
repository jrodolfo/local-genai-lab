package net.jrodolfo.llm.util;

import java.util.concurrent.TimeUnit;

public final class TimeUtils {

    private TimeUtils() {
    }

    public static long elapsedMillis(long startedAtNanos) {
        return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos));
    }
}
