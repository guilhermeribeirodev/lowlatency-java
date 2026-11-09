package io.example.ll.lowlevel;

import java.lang.reflect.Field;
import sun.misc.Unsafe;

/**
 * Off-heap memory via sun.misc.Unsafe: allocation-free, GC-invisible storage, e.g. for
 * order books, journals or shared-memory IPC.
 *
 * <p>Caveats worth documenting in a real project:
 * <ul>
 *   <li>Unsafe memory-access methods are deprecated for removal (JEP 471/498); the long-term
 *       replacement is the Foreign Function &amp; Memory API ({@code MemorySegment}).</li>
 *   <li>No bounds checks: a wrong offset corrupts the heap or crashes the JVM.</li>
 *   <li>You own the lifecycle: always pair {@link #allocate} with {@link #free}.</li>
 * </ul>
 */
@SuppressWarnings({"removal", "deprecation"})
public final class UnsafeMemory implements AutoCloseable {

    private static final Unsafe U;

    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            U = (Unsafe) f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final long address;
    private final long size;
    private boolean freed;

    private UnsafeMemory(long address, long size) {
        this.address = address;
        this.size = size;
    }

    public static UnsafeMemory allocate(long bytes) {
        long addr = U.allocateMemory(bytes);
        U.setMemory(addr, bytes, (byte) 0);
        return new UnsafeMemory(addr, bytes);
    }

    public void putLong(long offset, long value) {
        check(offset, Long.BYTES);
        U.putLong(address + offset, value);
    }

    public long getLong(long offset) {
        check(offset, Long.BYTES);
        return U.getLong(address + offset);
    }

    /** Ordered (release) write: cheaper than volatile, sufficient for single-writer publication. */
    public void putLongRelease(long offset, long value) {
        check(offset, Long.BYTES);
        U.putOrderedLong(null, address + offset, value);
    }

    public long getLongVolatile(long offset) {
        check(offset, Long.BYTES);
        return U.getLongVolatile(null, address + offset);
    }

    public long size() {
        return size;
    }

    private void check(long offset, int len) {
        // Keep a cheap guard even though Unsafe has none; remove behind an assert flag if profiling says so.
        if (freed || offset < 0 || offset + len > size) {
            throw new IndexOutOfBoundsException("offset=" + offset + " size=" + size + " freed=" + freed);
        }
    }

    public void free() {
        if (!freed) {
            freed = true;
            U.freeMemory(address);
        }
    }

    @Override
    public void close() {
        free();
    }
}
