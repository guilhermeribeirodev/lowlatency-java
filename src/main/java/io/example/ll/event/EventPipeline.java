package io.example.ll.event;

import io.example.ll.core.IdleStrategy;
import io.example.ll.core.SpscLongRingBuffer;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Minimal event-driven pipeline: one producer thread publishes events, one consumer thread
 * polls the ring buffer and invokes a handler. Events are encoded as longs (e.g. a price/size
 * pair packed into 64 bits, or an index into a pre-allocated slab) to keep the hot path
 * allocation-free.
 *
 * <p>Scale out by running several of these (one per symbol/partition) rather than sharing a
 * queue between many producers: single-writer designs beat contended CAS loops.
 */
public final class EventPipeline implements AutoCloseable {

    private final SpscLongRingBuffer ring;
    private final SpscLongRingBuffer.LongConsumer handler;
    private final IdleStrategy idle;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Thread consumer;

    public EventPipeline(int capacity, IdleStrategy idle, SpscLongRingBuffer.LongConsumer handler) {
        this.ring = new SpscLongRingBuffer(capacity);
        this.idle = idle;
        this.handler = handler;
        this.consumer = new Thread(this::run, "pipeline-consumer");
        this.consumer.setDaemon(true);
    }

    public void start() {
        consumer.start();
    }

    /** Producer side. Spins on back-pressure; swap for a drop/metrics policy if loss is acceptable. */
    public void publish(long event) {
        while (!ring.offer(event)) {
            Thread.onSpinWait();
        }
    }

    public boolean tryPublish(long event) {
        return ring.offer(event);
    }

    private void run() {
        while (running.get()) {
            int n = ring.drain(handler, 256);
            idle.idle(n);
        }
        ring.drain(handler, Integer.MAX_VALUE); // final flush
    }

    @Override
    public void close() throws InterruptedException {
        running.set(false);
        consumer.join(1_000);
    }
}
