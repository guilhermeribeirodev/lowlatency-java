package io.example.ll;

import io.example.ll.core.IdleStrategy;
import io.example.ll.event.EventPipeline;
import io.example.ll.net.UdpReceiver;
import io.example.ll.util.LatencyRecorder;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

/**
 * End-to-end demo: UDP packets in -> receive thread stamps and publishes to a ring buffer ->
 * consumer thread computes latency. Each packet carries the sender's nanoTime (same JVM, so
 * the clocks are comparable); in a real system you would use hardware or PTP timestamps.
 *
 * Run: java -cp target/classes:... io.example.ll.Main [messages]
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        final int messages = args.length > 0 ? Integer.parseInt(args[0]) : 200_000;
        final LatencyRecorder recorder = new LatencyRecorder();
        final long[] seen = {0};

        try (EventPipeline pipeline = new EventPipeline(1 << 16, IdleStrategy.BUSY_SPIN, sentNanos -> {
            recorder.record(System.nanoTime() - sentNanos);
            seen[0]++;
        })) {
            pipeline.start();

            UdpReceiver receiver = new UdpReceiver(new InetSocketAddress("127.0.0.1", 0),
                    (payload, rxNanos) -> pipeline.publish(payload.getLong()));
            Thread rx = new Thread(receiver, "udp-rx");
            rx.setDaemon(true);
            rx.start();

            try (DatagramChannel tx = DatagramChannel.open()) {
                InetSocketAddress target = new InetSocketAddress("127.0.0.1", receiver.localPort());
                ByteBuffer out = ByteBuffer.allocateDirect(8);
                for (int i = 0; i < messages; i++) {
                    out.clear();
                    out.putLong(System.nanoTime()).flip();
                    tx.send(out, target);
                    if ((i & 0x3F) == 0) Thread.sleep(0, 50_000); // pace to avoid socket buffer drops
                }
            }

            long deadline = System.nanoTime() + 2_000_000_000L;
            while (seen[0] < messages && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            receiver.close();
            System.out.printf("received %d / %d%n", seen[0], messages);
        }
        recorder.print(System.out, "udp->ring->handler");
    }

    private Main() {}
}
