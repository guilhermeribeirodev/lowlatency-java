package io.example.ll.lowlevel;

/**
 * JNI skeleton. Build the native side with {@code native/build.sh}, then run with
 * {@code -Djava.library.path=native/build}.
 *
 * <p>When JNI is justified: wrapping an existing C/C++ library, or one hot call that needs a
 * CPU instruction/API the JVM does not expose. Each JNI crossing costs tens of nanoseconds and
 * blocks some JIT optimisations, so batch work per call. Prefer JNR-FFI or the FFM API when you
 * only need to call a C function.
 */
public final class JniExample {

    private static final boolean LOADED;

    static {
        boolean ok;
        try {
            System.loadLibrary("llnative");
            ok = true;
        } catch (UnsatisfiedLinkError e) {
            ok = false; // native lib not built: callers fall back to the Java path
        }
        LOADED = ok;
    }

    public static boolean isAvailable() {
        return LOADED;
    }

    /** Implemented in native/ll_native.c. */
    public static native long rdtsc();

    private JniExample() {}
}
