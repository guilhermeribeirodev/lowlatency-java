# lowlatency-java

Reference project template for a low-latency Core Java service. It is small on purpose: each
package maps to one responsibility from the role description, so you can read it top to bottom
in an hour and use it as a scaffold or interview talking-points sheet.

| Role requirement | Where it lives |
|---|---|
| High-performance, low-latency Core Java | `core/SpscLongRingBuffer`, `core/IdleStrategy` |
| JVM profiling, GC analysis, tuning | `scripts/run.sh` (flags + GC/safepoint logs), `scripts/profile.sh` (JFR, async-profiler, jstat) |
| Event-driven / reactive, high-frequency workloads | `event/EventPipeline` (single-writer ring buffer + polling consumer) |
| Concurrency, memory, data structures, algorithms | Padded cache lines, VarHandle acquire/release, primitive storage, `bench/RingBufferBenchmark` (JMH) |
| TCP/UDP, low-latency comms | `net/UdpReceiver` (non-blocking, direct buffer), `net/TcpEchoServer` (selector reactor, TCP_NODELAY) |
| JNI, Unsafe, JNR-FFI | `lowlevel/JniExample` + `native/`, `lowlevel/UnsafeMemory`, `lowlevel/NativeClock` |

## Requirements

- JDK 21+ (Linux or macOS), Maven 3.9+, a C compiler only if you build the JNI library.

## Quick start

```bash
mvn verify                              # compile + tests
mvn -q package -DskipTests              # also builds target/benchmarks.jar (shaded, JMH main)
java -jar target/benchmarks.jar -prof gc  # compare ring buffer vs ArrayBlockingQueue, with allocation rate
scripts/run.sh 500000                   # UDP -> ring -> handler demo with percentiles, tuned JVM flags, GC/safepoint logs
native/build.sh                         # optional: build JNI lib, then run with -Djava.library.path=native/build
```

## Layout

```
src/main/java/io/example/ll/
  core/      SpscLongRingBuffer, IdleStrategy
  event/     EventPipeline
  net/       UdpReceiver, TcpEchoServer
  lowlevel/  UnsafeMemory, NativeClock (JNR-FFI), JniExample
  util/      LatencyRecorder (HdrHistogram)
  bench/     RingBufferBenchmark (JMH)
  Main.java  end-to-end demo
src/test/java/...   CoreTests
native/      ll_native.c, build.sh
scripts/     run.sh, profile.sh
```

## Design principles to keep as you extend it

1. **Measure first.** JMH for micro-costs, HdrHistogram for end-to-end percentiles, JFR/async-profiler
   for where time goes. Report p99/p99.9/max, not averages. Use `recordCorrected` for open-loop load
   (coordinated omission).
2. **No allocation on the hot path.** Primitives, preallocated buffers, object pools or slabs. Verify with
   `-prof gc` in JMH and `jdk.ObjectAllocationSample` in JFR.
3. **Single writer principle.** One thread owns each piece of mutable state; communicate through
   ring buffers rather than locks. Scale by partitioning, not by sharing.
4. **Avoid false sharing and cache misses.** Pad contended fields, keep data contiguous (arrays of
   primitives over object graphs), size structures to cache.
5. **Take the JVM out of the way.** Fixed pre-touched heap, GC chosen deliberately (ZGC for short pauses,
   Parallel or Epsilon when nothing allocates), warm-up before taking traffic, safepoint log review.
6. **Take the OS out of the way.** Pin threads (`taskset`), isolate cores (`isolcpus`, `nohz_full`),
   disable CPU frequency scaling and C-states, consider huge pages, keep IRQs off the hot cores.
7. **Keep low-level code boxed in.** Unsafe, JNI and FFI go behind small interfaces with a safe fallback
   and tests. Unsafe memory-access methods are deprecated for removal, so plan the migration to the
   Foreign Function & Memory API (`java.lang.foreign`).

## Suggested next steps

- Replace `TcpEchoServer`'s spin-on-write with `OP_WRITE` and a per-connection output buffer.
- Add a multi-producer variant (e.g. a Disruptor-style sequencer) and benchmark contention.
- Add an off-heap journal on `UnsafeMemory` or `MemorySegment` with memory-mapped files for persistence.
- Add GC regression checks in CI: run `Main` with `-Xlog:gc` and fail on pauses above a threshold.
- Try Aeron or Chronicle Queue for production-grade messaging and compare with this hand-rolled version.

## Deploying to Azure (Terraform)

`terraform/` provisions a single Ubuntu 24.04 VM tuned for low latency: accelerated networking (SR-IOV),
Premium SSD, static public IP, NSG locked to your CIDRs, optional proximity placement group, kernel/network
sysctls, JDK 21, and a systemd unit (fixed heap, ZGC, GC/safepoint logs, optional CPU isolation and pinning).

```bash
az login
cd terraform
cp terraform.tfvars.example terraform.tfvars    # set subscription_id, your IP CIDRs, region, VM size
terraform init && terraform plan && terraform apply
cd .. && scripts/deploy-azure.sh azureuser@$(terraform -chdir=terraform output -raw public_ip)
scripts/deploy-azure.sh azureuser@<ip> bench    # optional: run JMH on the VM itself
terraform -chdir=terraform destroy              # stop paying when finished
```

Notes:
- `Main` is a self-contained loopback demo, so the service runs it and exits. Replace the `ExecStart` main class
  with your real service entry point (the shaded `benchmarks.jar` contains all your classes and dependencies).
- Benchmark on the VM, not your laptop: results differ by SKU, region and noisy neighbours. Try a few `vm_size`
  values and compare p99.9 and max, not only the median.
- `isolated_cpus = "2-3"` isolates those cores and pins the service to them (one reboot on first boot).
- Azure VMs are virtualised: you get no kernel bypass or PTP hardware timestamping. If you need those, look at
  dedicated hosts or bare-metal options in your region.
