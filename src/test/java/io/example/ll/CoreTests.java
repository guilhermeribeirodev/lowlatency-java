package io.example.ll;

import io.example.ll.core.IdleStrategy;
import io.example.ll.core.SpscLongRingBuffer;
import io.example.ll.event.EventPipeline;
import io.example.ll.lowlevel.UnsafeMemory;
import io.example.ll.net.TcpEchoServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class CoreTests {

    @Test
    void ringBufferRejectsNonPowerOfTwo() {
        assertThrows(IllegalArgumentException.class, () -> new SpscLongRingBuffer(100));
    }

    @Test
    void ringBufferReportsFullAndEmpty() {
        var rb = new SpscLongRingBuffer(4);
        for (int i = 0; i < 4; i++) assertTrue(rb.offer(i));
        assertFalse(rb.offer(99));
        long[] sum = {0};
        assertEquals(4, rb.drain(v -> sum[0] += v, 10));
        assertEquals(6, sum[0]);
        assertEquals(0, rb.drain(v -> fail(), 10));
    }

    @Test
    void ringBufferPreservesOrderAcrossThreads() throws Exception {
        final int n = 1_000_000;
        var rb = new SpscLongRingBuffer(1 << 12);
        Thread producer = new Thread(() -> {
            for (long i = 0; i < n; i++) while (!rb.offer(i)) Thread.onSpinWait();
        });
        long[] expected = {0};
        boolean[] ordered = {true};
        producer.start();
        while (expected[0] < n) {
            rb.drain(v -> {
                if (v != expected[0]++) ordered[0] = false;
            }, 128);
        }
        producer.join();
        assertTrue(ordered[0]);
    }

    @Test
    void pipelineDeliversAllEvents() throws Exception {
        final int n = 50_000;
        var count = new AtomicLong();
        var done = new CountDownLatch(1);
        try (var p = new EventPipeline(1 << 10, IdleStrategy.YIELDING, v -> {
            if (count.incrementAndGet() == n) done.countDown();
        })) {
            p.start();
            for (int i = 0; i < n; i++) p.publish(i);
            assertTrue(done.await(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void unsafeMemoryRoundTripsAndBoundsChecks() {
        try (var m = UnsafeMemory.allocate(64)) {
            m.putLong(8, 0xCAFEBABEL);
            assertEquals(0xCAFEBABEL, m.getLong(8));
            assertThrows(IndexOutOfBoundsException.class, () -> m.getLong(60));
        }
    }

    @Test
    void tcpEchoServerEchoes() throws Exception {
        try (var server = new TcpEchoServer(new InetSocketAddress("127.0.0.1", 0))) {
            Thread t = new Thread(server);
            t.setDaemon(true);
            t.start();
            try (SocketChannel c = SocketChannel.open(new InetSocketAddress("127.0.0.1", server.localPort()))) {
                ByteBuffer out = ByteBuffer.wrap("ping".getBytes());
                c.write(out);
                ByteBuffer in = ByteBuffer.allocate(4);
                while (in.hasRemaining()) assertTrue(c.read(in) >= 0);
                assertEquals("ping", new String(in.array()));
            }
        }
    }
}
