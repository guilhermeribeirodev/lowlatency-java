package io.example.ll.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

/**
 * Single-threaded, selector-based TCP echo server: the skeleton of a reactor.
 *
 * <p>Low-latency choices: TCP_NODELAY (no Nagle), selectNow() busy-poll instead of blocking
 * select(), one reusable direct buffer, and the loop runs on one thread so there is no
 * locking and no cross-thread hand-off between read and respond.
 */
public final class TcpEchoServer implements Runnable, AutoCloseable {

    private final Selector selector;
    private final ServerSocketChannel server;
    private final ByteBuffer buf = ByteBuffer.allocateDirect(64 * 1024);
    private volatile boolean running = true;

    public TcpEchoServer(InetSocketAddress bind) throws IOException {
        selector = Selector.open();
        server = ServerSocketChannel.open();
        server.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        server.bind(bind);
        server.configureBlocking(false);
        server.register(selector, SelectionKey.OP_ACCEPT);
    }

    public int localPort() throws IOException {
        return ((InetSocketAddress) server.getLocalAddress()).getPort();
    }

    @Override
    public void run() {
        try {
            while (running) {
                if (selector.selectNow() == 0) {
                    Thread.onSpinWait();
                    continue;
                }
                Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                while (it.hasNext()) {
                    SelectionKey key = it.next();
                    it.remove();
                    if (!key.isValid()) continue;
                    if (key.isAcceptable()) accept();
                    else if (key.isReadable()) echo(key);
                }
            }
        } catch (IOException e) {
            if (running) throw new java.io.UncheckedIOException(e);
        }
    }

    private void accept() throws IOException {
        SocketChannel ch = server.accept();
        if (ch == null) return;
        ch.configureBlocking(false);
        ch.setOption(StandardSocketOptions.TCP_NODELAY, true);
        ch.register(selector, SelectionKey.OP_READ);
    }

    private void echo(SelectionKey key) throws IOException {
        SocketChannel ch = (SocketChannel) key.channel();
        buf.clear();
        int n;
        try {
            n = ch.read(buf);
        } catch (IOException e) {
            key.cancel();
            ch.close();
            return;
        }
        if (n < 0) {
            key.cancel();
            ch.close();
            return;
        }
        buf.flip();
        while (buf.hasRemaining()) {
            if (ch.write(buf) == 0) Thread.onSpinWait(); // production: register OP_WRITE and queue
        }
    }

    @Override
    public void close() throws IOException {
        running = false;
        selector.wakeup();
        server.close();
        selector.close();
    }
}
