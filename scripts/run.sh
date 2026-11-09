#!/usr/bin/env bash
# Starting-point JVM flags for a latency-sensitive service. Measure before and after every change.
# Usage: scripts/run.sh [main args...]
set -euo pipefail
cd "$(dirname "$0")/.."

JAR_CP="target/classes:$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout 2>/dev/null || true)"

JVM_FLAGS=(
  # --- Heap: fixed size, pre-touched, so no resize pauses and no page faults at runtime
  -Xms2g -Xmx2g
  -XX:+AlwaysPreTouch
  # --- GC: pick ONE. ZGC for sub-ms pauses (generational by default on JDK 23+; on JDK 21 add
  #     -XX:+ZGenerational); Parallel/Epsilon for zero-GC hot paths
  -XX:+UseZGC
  # -XX:+UseParallelGC
  # -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC   # only if the hot path truly allocates nothing
  # --- JIT: compile early, avoid deopt storms from lazy class loading
  -XX:-UseCounterDecay
  -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints   # accurate profiler attribution
  # --- Safepoints / pauses: log anything over 1ms
  -Xlog:safepoint*=info:file=logs/safepoint.log:time,uptime
  -Xlog:gc*=info:file=logs/gc.log:time,uptime,level,tags
  # --- Memory: transparent huge pages if the OS is set up for it (see README)
  # -XX:+UseTransparentHugePages
  # --- Misc
  -XX:+PerfDisableSharedMem      # avoids /tmp/hsperfdata mmap stalls
  --enable-native-access=ALL-UNNAMED
)

mkdir -p logs
# Optional CPU pinning on Linux: taskset -c 2,3 (combine with isolcpus=2,3 nohz_full=2,3 kernel args)
exec ${PIN_CMD:-} java "${JVM_FLAGS[@]}" -cp "$JAR_CP" io.example.ll.Main "$@"
