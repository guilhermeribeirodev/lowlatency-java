package io.example.ll.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

/**
 * Non-blocking UDP receiver that busy-polls a single DatagramChannel.
 *
 * <p>Low-latency choices: a pre-allocated direct ByteBuffer (no per-packet allocation, no
 * extra copy into the heap), non-blocking mode so the thread never parks in the kernel,
 * and a large SO_RCVBUF to absorb bursts. For kernel-bypass (Onload, DPDK) the same
 * structure applies, only the poll call changes.
 */
public final class UdpReceiver implements Runnable, AutoCloseable {

    /** Called on the receive thread with a buffer positioned at the payload. Must not retain it. */
    @FunctionalInterface
    public interface PacketHandler {
        void onPacket(ByteBuffer payload, long receiveNanos);
    }

    private final DatagramChannel channel;
    private final ByteBuffer buf = ByteBuffer.allocateDirect(2048);
    private final PacketHandler handler;
    private volatile boolean running = true;

    public UdpReceiver(InetSocketAddress bind, PacketHandler handler) throws IOException {
        this.channel = DatagramChannel.open();
        this.channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        this.channel.setOption(StandardSocketOptions.SO_RCVBUF, 4 * 1024 * 1024);
        this.channel.bind(bind);
        this.channel.configureBlocking(false);
        this.handler = handler;
    }

    public int localPort() throws IOException {
        return ((InetSocketAddress) channel.getLocalAddress()).getPort();
    }

    @Override
    public void run() {
        try {
            while (running) {
                buf.clear();
                if (channel.receive(buf) == null) {
                    Thread.onSpinWait();
                    continue;
                }
                long now = System.nanoTime();
                buf.flip();
                handler.onPacket(buf, now);
            }
        } catch (IOException e) {
            if (running) throw new java.io.UncheckedIOException(e);
        }
    }

    @Override
    public void close() throws IOException {
        running = false;
        channel.close();
    }
}
