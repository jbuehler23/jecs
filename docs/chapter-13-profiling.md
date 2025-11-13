# Chapter 13: Profiling & Performance Tuning
## Shipping a Fast, Polished Engine

**What You'll Learn:**
- Java Flight Recorder (JFR) integration
- Frame time profiling
- Memory profiling
- GPU profiling with Vulkan query pools
- Optimization best practices

**Estimated Time:** 2-3 hours

---

## Java Flight Recorder (JFR)

### Setup

```bash
# Run with JFR enabled
java -XX:StartFlightRecording=filename=recording.jfr,duration=60s -jar engine.jar

# Analyze with JDK Mission Control
jmc recording.jfr
```

### Custom Events

```java
@Name("com.yourname.engine.FrameRender")
@Label("Frame Render")
@Category("Engine")
public class FrameRenderEvent extends Event {
    @Label("Frame Number")
    public int frameNumber;

    @Label("Duration")
    public long durationNanos;
}

// Usage
FrameRenderEvent event = new FrameRenderEvent();
event.begin();
// ... render frame ...
event.frameNumber = frameCount;
event.durationNanos = System.nanoTime() - startTime;
event.commit();
```

---

## Frame Time Profiler

```java
public class FrameProfiler {
    private Map<String, Long> sectionTimes = new LinkedHashMap<>();
    private long lastMark;

    public void beginFrame() {
        sectionTimes.clear();
        lastMark = System.nanoTime();
    }

    public void mark(String section) {
        long now = System.nanoTime();
        sectionTimes.put(section, now - lastMark);
        lastMark = now;
    }

    public void endFrame() {
        // Print frame breakdown
        System.out.println("=== Frame Breakdown ===");
        long total = 0;
        for (Map.Entry<String, Long> entry : sectionTimes.entrySet()) {
            long micros = entry.getValue() / 1000;
            total += entry.getValue();
            System.out.printf("%20s: %6d µs (%.1f%%)\n",
                entry.getKey(), micros, (entry.getValue() / (double)total) * 100);
        }
        System.out.printf("%20s: %6d µs\n", "TOTAL", total / 1000);
    }
}

// Usage
profiler.beginFrame();
pollInput();
profiler.mark("Input");

updateSystems(deltaTime);
profiler.mark("Systems");

renderFrame();
profiler.mark("Render");

profiler.endFrame();
```

**Output:**
```
=== Frame Breakdown ===
               Input:     50 µs (0.3%)
             Systems:   8500 µs (51.2%)
              Render:   8000 µs (48.2%)
               TOTAL:  16600 µs
```

---

## Memory Profiling

### Heap Analysis

```bash
# Enable heap dumps on OOM
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/tmp/heap.hprof

# Analyze with Eclipse MAT or VisualVM
```

### GC Logging

```bash
# Java 25 GC logging
-Xlog:gc*:file=gc.log:time,level,tags

# Monitor GC pauses
-XX:+PrintGCDetails
-XX:+PrintGCDateStamps
```

### Allocation Profiler

```java
public class AllocationTracker {
    private long lastHeapUsed;

    public void checkpoint(String label) {
        Runtime runtime = Runtime.getRuntime();
        long heapUsed = runtime.totalMemory() - runtime.freeMemory();
        long allocated = heapUsed - lastHeapUsed;

        System.out.printf("%s: %.2f MB allocated\n", label, allocated / 1_048_576.0);
        lastHeapUsed = heapUsed;
    }
}

// Usage
tracker.checkpoint("Start");
createEntities(10000);
tracker.checkpoint("After entity creation"); // → 5.2 MB allocated
```

---

## GPU Profiling

### Vulkan Query Pools

```java
// Create query pool
VkQueryPoolCreateInfo queryPoolInfo = VkQueryPoolCreateInfo.calloc();
queryPoolInfo.sType(VK_STRUCTURE_TYPE_QUERY_POOL_CREATE_INFO);
queryPoolInfo.queryType(VK_QUERY_TYPE_TIMESTAMP);
queryPoolInfo.queryCount(32);

long queryPool;
vkCreateQueryPool(device, queryPoolInfo, null, pQueryPool);

// Record timestamps
vkCmdResetQueryPool(commandBuffer, queryPool, 0, 32);
vkCmdWriteTimestamp(commandBuffer, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, queryPool, 0);
// ... render pass ...
vkCmdWriteTimestamp(commandBuffer, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, queryPool, 1);

// Read results
long[] timestamps = new long[2];
vkGetQueryPoolResults(device, queryPool, 0, 2, timestamps, VK_QUERY_RESULT_64_BIT | VK_QUERY_RESULT_WAIT_BIT);

float gpuTime = (timestamps[1] - timestamps[0]) * timestampPeriod / 1_000_000.0f; // ms
System.out.printf("GPU render time: %.2f ms\n", gpuTime);
```

---

## Optimization Checklist

### CPU Optimization

- [ ] Profile hot paths with JFR
- [ ] Minimize allocations in game loop
- [ ] Use object pools for frequent allocations
- [ ] Enable compact object headers
- [ ] Use primitive collections (IntArrayList vs ArrayList<Integer>)
- [ ] Parallelize independent systems
- [ ] Cache frequently accessed components

### GPU Optimization

- [ ] Batch draw calls (sprite batching, instancing)
- [ ] Minimize state changes (sort by material)
- [ ] Use texture atlases
- [ ] Implement frustum culling
- [ ] Use LOD (level of detail) for distant objects
- [ ] Optimize shaders (avoid branching, use swizzling)
- [ ] Profile with Vulkan timestamps

### Memory Optimization

- [ ] Monitor heap growth with JFR
- [ ] Tune GC (ZGC for low latency)
- [ ] Use direct buffers for GPU data
- [ ] Release unused assets
- [ ] Implement asset streaming
- [ ] Compress textures (BC7, ASTC)

---

## Shipping Checklist

- [ ] Remove debug logging from hot paths
- [ ] Disable validation layers
- [ ] Enable compiler optimizations (`-O3`, LTO)
- [ ] Strip debug symbols
- [ ] Bundle JRE with jlink (custom runtime)
- [ ] Add crash reporting
- [ ] Test on target hardware
- [ ] Measure 0.1%, 1%, average FPS

---

## Exercises

1. Integrate async-profiler for production profiling
2. Add telemetry (send metrics to dashboard)
3. Implement dynamic LOD system
4. Create automated performance regression tests
5. Build CI/CD pipeline with performance benchmarks

---

**Previous:** [← Chapter 12 - ECS Optimization](chapter-12-ecs-optimization.md)
**Next:** [Appendix A - Vulkan Fundamentals →](appendix-a-vulkan-fundamentals.md)
