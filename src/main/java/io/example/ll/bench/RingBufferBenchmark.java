package io.example.ll.bench;

import io.example.ll.core.SpscLongRingBuffer;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Single-thread offer+drain cost compared with a JDK blocking queue. Run:
 *   mvn -q package -DskipTests && java -jar target/benchmarks.jar -prof gc
 * Add -prof perfasm (Linux perf) or -prof async:libPath=... for deeper analysis.
 */
@State(Scope.Thread)
@BenchmarkMode({Mode.AverageTime})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgsAppend = {"-XX:+UseParallelGC", "-Xms512m", "-Xmx512m", "-XX:+AlwaysPreTouch"})
public class RingBufferBenchmark {

    private SpscLongRingBuffer ring;
    private ArrayBlockingQueue<Long> abq;
    private long sink;

    @Setup
    public void setup() {
        ring = new SpscLongRingBuffer(1024);
        abq = new ArrayBlockingQueue<>(1024);
    }

    @Benchmark
    public long spscRing() {
        ring.offer(42L);
        ring.drain(v -> sink += v, 1);
        return sink;
    }

    @Benchmark
    public long arrayBlockingQueue() {
        abq.offer(42L);          // boxes outside the Long cache range -> allocation (see -prof gc)
        Long v = abq.poll();
        return v == null ? 0 : v;
    }
}
