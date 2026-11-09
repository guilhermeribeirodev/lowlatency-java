package io.example.ll.core;

/**
 * Idle strategies for polling loops. Trade CPU for latency explicitly.
 * Busy-spin gives the lowest latency but burns a core: pair with CPU isolation (see scripts/).
 */
public enum IdleStrategy {
    BUSY_SPIN {
        @Override public void idle(int workCount) {
            if (workCount == 0) Thread.onSpinWait();
        }
    },
    YIELDING {
        @Override public void idle(int workCount) {
            if (workCount == 0) Thread.yield();
        }
    },
    BACKOFF {
        @Override public void idle(int workCount) {
            if (workCount == 0) java.util.concurrent.locks.LockSupport.parkNanos(1_000);
        }
    };

    public abstract void idle(int workCount);
}
