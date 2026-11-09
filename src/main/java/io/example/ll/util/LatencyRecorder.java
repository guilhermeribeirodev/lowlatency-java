package io.example.ll.util;

import org.HdrHistogram.Histogram;

import java.io.PrintStream;

/**
 * Records latencies in an HdrHistogram (constant-memory, no allocation per sample) and prints
 * percentiles. Use {@link #recordCorrected} for open-loop load to avoid coordinated omission.
 */
public final class LatencyRecorder {

    private final Histogram histogram = new Histogram(TimeUnitsNanos.ONE_MINUTE, 3);

    public void record(long nanos) {
        histogram.recordValue(Math.min(nanos, TimeUnitsNanos.ONE_MINUTE));
    }

    /** Compensates for stalls: if a sample exceeds the expected interval, backfills the missed samples. */
    public void recordCorrected(long nanos, long expectedIntervalNanos) {
        histogram.recordValueWithExpectedInterval(Math.min(nanos, TimeUnitsNanos.ONE_MINUTE), expectedIntervalNanos);
    }

    public void print(PrintStream out, String title) {
        out.printf("%s: n=%d min=%dns p50=%dns p99=%dns p99.9=%dns p99.99=%dns max=%dns%n",
                title,
                histogram.getTotalCount(),
                histogram.getMinValue(),
                histogram.getValueAtPercentile(50),
                histogram.getValueAtPercentile(99),
                histogram.getValueAtPercentile(99.9),
                histogram.getValueAtPercentile(99.99),
                histogram.getMaxValue());
    }

    public long p99() {
        return histogram.getValueAtPercentile(99);
    }

    private static final class TimeUnitsNanos {
        static final long ONE_MINUTE = 60_000_000_000L;
    }
}
