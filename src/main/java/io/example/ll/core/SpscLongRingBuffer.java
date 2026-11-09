package io.example.ll.core;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * Single-producer / single-consumer lock-free ring buffer of long values.
 *
 * <p>Design notes (the things to talk about in a latency review):
 * <ul>
 *   <li>Power-of-two capacity, so index wrap is a mask, not a modulo.</li>
 *   <li>Head and tail live on separate cache lines (manual padding) to avoid false sharing.</li>
 *   <li>Release/acquire ordering via VarHandle: no volatile full fences on the hot path.</li>
 *   <li>Each side caches the other side's counter and only re-reads it when it appears full/empty.</li>
 *   <li>Primitive storage: no boxing, no allocation, nothing for the GC to do.</li>
 * </ul>
 */
public final class SpscLongRingBuffer {

    private static final VarHandle HEAD;
    private static final VarHandle TAIL;

    static {
        try {
            var l = MethodHandles.lookup();
            HEAD = l.findVarHandle(Head.class, "value", long.class);
            TAIL = l.findVarHandle(Tail.class, "value", long.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // Padding classes: fields in a superclass are laid out before subclass fields.
    @SuppressWarnings("unused")
    private static class Pad0 { long p01, p02, p03, p04, p05, p06, p07, p08; }
    private static class Head extends Pad0 { volatile long value; long cachedTail; }
    @SuppressWarnings("unused")
    private static class Pad1 extends Head { long p11, p12, p13, p14, p15, p16, p17, p18; }
    private static class Tail extends Pad1 { volatile long value; long cachedHead; }

    private final long[] buffer;
    private final int mask;
    private final Head head = new Head(); // consumer-owned
    private final Tail tail = new Tail(); // producer-owned

    public SpscLongRingBuffer(int capacityPowerOfTwo) {
        if (Integer.bitCount(capacityPowerOfTwo) != 1) {
            throw new IllegalArgumentException("capacity must be a power of two");
        }
        this.buffer = new long[capacityPowerOfTwo];
        this.mask = capacityPowerOfTwo - 1;
    }

    /** Producer thread only. @return false if the buffer is full. */
    public boolean offer(long v) {
        final long t = (long) TAIL.getOpaque(tail);
        if (t - tail.cachedHead >= buffer.length) {
            tail.cachedHead = (long) HEAD.getAcquire(head);
            if (t - tail.cachedHead >= buffer.length) {
                return false;
            }
        }
        buffer[(int) t & mask] = v;
        TAIL.setRelease(tail, t + 1);
        return true;
    }

    /**
     * Consumer thread only.
     * @return number of elements drained (up to {@code limit}); handler is a primitive callback.
     */
    public int drain(LongConsumer handler, int limit) {
        final long h = (long) HEAD.getOpaque(head);
        long available = head.cachedTail - h;
        if (available <= 0) {
            head.cachedTail = (long) TAIL.getAcquire(tail);
            available = head.cachedTail - h;
            if (available <= 0) {
                return 0;
            }
        }
        final int n = (int) Math.min(available, limit);
        for (int i = 0; i < n; i++) {
            handler.accept(buffer[(int) (h + i) & mask]);
        }
        HEAD.setRelease(head, h + n);
        return n;
    }

    public int capacity() {
        return buffer.length;
    }

    /** Primitive-specialised callback to avoid boxing. */
    @FunctionalInterface
    public interface LongConsumer {
        void accept(long value);
    }
}
