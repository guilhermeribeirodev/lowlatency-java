#!/usr/bin/env bash
# Profiling cheat-sheet for a running JVM. Usage: scripts/profile.sh <pid> <mode>
# Modes: jfr | alloc | gc | threads | heap | async
set -euo pipefail
PID="${1:?pid}"; MODE="${2:?mode}"
OUT="profiles/$(date +%Y%m%d-%H%M%S)"; mkdir -p "$OUT"

case "$MODE" in
  jfr)      # Low-overhead continuous profiling: open the .jfr in JDK Mission Control
    jcmd "$PID" JFR.start name=ll settings=profile duration=60s filename="$OUT/recording.jfr"
    echo "writing $OUT/recording.jfr in 60s" ;;
  alloc)    # Who allocates? Look at jdk.ObjectAllocationSample in the JFR recording
    jcmd "$PID" JFR.start name=alloc settings=profile duration=60s filename="$OUT/alloc.jfr"
    echo "then: jfr print --events jdk.ObjectAllocationSample $OUT/alloc.jfr | head" ;;
  gc)       # Live GC view
    jstat -gcutil "$PID" 1000 ;;
  threads)  # Thread dump for lock contention / stuck threads
    jcmd "$PID" Thread.print > "$OUT/threads.txt" && echo "$OUT/threads.txt" ;;
  heap)     # Class histogram (cheap) - use GC.heap_dump only off-peak
    jcmd "$PID" GC.class_histogram > "$OUT/histogram.txt" && echo "$OUT/histogram.txt" ;;
  async)    # async-profiler (install separately): CPU flamegraph, no safepoint bias
    : "${ASYNC_PROFILER_HOME:?set ASYNC_PROFILER_HOME}"
    "$ASYNC_PROFILER_HOME/bin/asprof" -d 30 -e cpu -f "$OUT/cpu.html" "$PID"
    echo "$OUT/cpu.html" ;;
  *) echo "unknown mode" >&2; exit 1 ;;
esac
