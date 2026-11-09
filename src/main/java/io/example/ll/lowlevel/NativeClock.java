package io.example.ll.lowlevel;

import jnr.ffi.LibraryLoader;
import jnr.ffi.Pointer;
import jnr.ffi.Runtime;
import jnr.ffi.Struct;

/**
 * Calling native libc from Java without writing any C, using JNR-FFI.
 *
 * <p>Demonstrates two typical uses: a cheap syscall wrapper ({@code getpid}) and a struct-based
 * call ({@code clock_gettime(CLOCK_MONOTONIC_RAW)}), reusing one preallocated struct so the
 * call path allocates nothing.
 *
 * <p>Linux/macOS only. For new code on JDK 22+, compare against java.lang.foreign (FFM API),
 * which gives similar performance without a third-party dependency.
 */
public final class NativeClock {

    public interface LibC {
        int getpid();
        int clock_gettime(int clockId, Pointer timespec);
    }

    static final class Timespec extends Struct {
        final SignedLong tvSec = new SignedLong();
        final SignedLong tvNsec = new SignedLong();
        Timespec(Runtime rt) { super(rt); }
    }

    private static final int CLOCK_MONOTONIC_RAW = 4; // Linux value

    private final LibC libc = LibraryLoader.create(LibC.class).load("c");
    private final Timespec ts = new Timespec(Runtime.getRuntime(libc));

    public int pid() {
        return libc.getpid();
    }

    /** Monotonic nanoseconds straight from the kernel clock. Single-threaded use only (shared struct). */
    public long monotonicRawNanos() {
        if (libc.clock_gettime(CLOCK_MONOTONIC_RAW, Struct.getMemory(ts)) != 0) {
            throw new IllegalStateException("clock_gettime failed");
        }
        return ts.tvSec.get() * 1_000_000_000L + ts.tvNsec.get();
    }
}
