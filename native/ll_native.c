#include <jni.h>
#include <stdint.h>

#if defined(__x86_64__) || defined(__i386__)
#include <x86intrin.h>
static inline uint64_t read_tsc(void) { return __rdtsc(); }
#elif defined(__aarch64__)
static inline uint64_t read_tsc(void) {
    uint64_t v;
    __asm__ volatile("mrs %0, cntvct_el0" : "=r"(v));
    return v;
}
#else
#error "unsupported architecture"
#endif

/* Matches: io.example.ll.lowlevel.JniExample.rdtsc() */
JNIEXPORT jlong JNICALL Java_io_example_ll_lowlevel_JniExample_rdtsc(JNIEnv *env, jclass cls) {
    return (jlong) read_tsc();
}
