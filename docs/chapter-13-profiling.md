# Chapter 13: Profiling & Performance - Measuring and Optimizing

## What You'll Learn

"**Premature optimization is the root of all evil**" - but **informed optimization** is essential! In this final chapter, we'll build tools to:

- **Measure CPU time** - Frame profiler with hierarchical sections
- **Measure GPU time** - Vulkan timestamp queries
- **Track memory usage** - Heap allocation and GC pressure
- **Visualize performance** - Real-time graphs and flamegraphs
- **Identify bottlenecks** - Find the slow parts of your code
- **Optimize strategically** - Focus on what matters

By the end, you'll have professional-grade profiling tools to maintain **60 FPS** even in complex games.

---

## The Big Picture: Why Profile?

###"My Game Runs Slow" - Now What?

**Without profiling:**
```
"The game is laggy..."
  → Is it the physics? Rendering? Scripts? Memory? WHO KNOWS?!
  → Random changes: "Let me reduce particle count..."
  → Still slow? Try something else...
  → Waste hours on wrong optimizations
```

**With profiling:**
```
"The game is laggy..."
  → Profile shows: Physics = 12ms, Rendering = 2ms
  → AH! Physics is the bottleneck (72% of frame time)
  → Focus optimization on physics
  → 30 minutes later: Physics = 3ms, game runs smooth!
```

**Profiling tells you WHERE to optimize, so you don't waste time guessing!**

---

### The 60 FPS Budget

**Target:** 60 frames per second = 16.67ms per frame

```
┌─────────────────────────────────────────────┐
│  Frame Budget: 16.67ms                      │
├─────────────────────────────────────────────┤
│  Update:        3ms    ████                 │
│  Physics:       4ms    ██████               │
│  Rendering:     6ms    ███████████          │
│  Scripts:       2ms    ███                  │
│  Audio:         0.5ms  █                    │
│  ────────────────────────────────────────   │
│  Total:        15.5ms  ✓ Within budget!    │
└─────────────────────────────────────────────┘
```

**If ANY system exceeds 16.67ms, you drop frames!**

```
Frame 1:  16.5ms  ✓ 60 FPS
Frame 2:  20.0ms  ✗ Frame drop! (only 50 FPS)
Frame 3:  15.0ms  ✓ 60 FPS
```

**One slow frame = visible stutter. Profiling finds these.**

---

### CPU vs GPU Profiling

**Two Separate Pipelines:**

```
┌────────────────┐          ┌────────────────┐
│  CPU (Java)    │          │  GPU (Vulkan)  │
│                │          │                │
│  Update        │          │                │
│  Physics       │────────→ │  Shadow Pass   │
│  Scripts       │ Commands │  Main Pass     │
│  Cull/Batch    │          │  Post Process  │
└────────────────┘          └────────────────┘
     ↓ CPU Time                  ↓ GPU Time
```

**The Bottleneck Determines FPS:**

| Scenario | CPU Time | GPU Time | Bottleneck | FPS |
|----------|----------|----------|------------|-----|
| **CPU-bound** | 20ms | 10ms | CPU | 50 FPS (20ms limit) |
| **GPU-bound** | 10ms | 20ms | GPU | 50 FPS (20ms limit) |
| **Balanced** | 15ms | 15ms | Both | 60+ FPS ✓ |

**You need BOTH CPU and GPU profiling to find the real bottleneck!**

---

### Professional Engine Comparisons

| Engine | CPU Profiler | GPU Profiler | Memory Profiler |
|--------|--------------|--------------|-----------------|
| **Unity** | Built-in (Profiler window) | Frame Debugger | Memory Profiler |
| **Unreal** | Stat Unit, Stat FPS | RenderDoc integration | Stat Memory |
| **Godot** | Monitor (FPS, memory) | Limited | Monitor panel |
| **JECS** | Custom hierarchical | Vulkan timestamps | Java MXBean |

We're building Unity-level profiling!

---

## Part 1: CPU Frame Profiler

### Understanding Hierarchical Profiling

**What Is Hierarchical Profiling?**

```
Frame (16ms total)
├─ Update (3ms)
│  ├─ Input (0.5ms)
│  └─ Scripts (2.5ms)
│     ├─ Enemy AI (1.5ms)  ← SLOW!
│     └─ Player Script (1ms)
├─ Physics (4ms)
│  ├─ Broad Phase (1ms)
│  └─ Narrow Phase (3ms)
└─ Rendering (6ms)
   ├─ Shadow Pass (2ms)
   └─ Main Pass (4ms)
```

**Benefits:**
- **See the hierarchy**: "Scripts are slow, specifically Enemy AI"
- **Drill down**: Find exact slow function
- **Compare**: "Shadow Pass takes 2ms, is that normal?"

**Alternative: Flat Profiling (less useful)**

```
Enemy AI: 1.5ms
Player Script: 1ms
Broad Phase: 1ms
Narrow Phase: 3ms
Shadow Pass: 2ms
Main Pass: 4ms
```

Problem: Can't see relationships! Is Enemy AI part of Scripts or Physics?

---

### How System.nanoTime() Works

**What is nanoTime()?**

```java
long start = System.nanoTime();  // e.g., 1,234,567,890,000 ns
// ... do work ...
long end = System.nanoTime();    // e.g., 1,234,569,000,000 ns
long elapsed = end - start;      // 1,110,000 ns = 1.11ms
```

**Precision:**
- **Resolution**: 1 nanosecond (0.000001 ms)
- **Accuracy**: ~10-100 nanoseconds (varies by OS)
- **Overhead**: ~25 nanoseconds per call

**Why Not currentTimeMillis()?**
- currentTimeMillis() = wall clock time (can jump with NTP adjustments)
- nanoTime() = monotonic clock (never goes backwards, perfect for timing)

---

### Profiler.java - Implementation

**What This Code Does:**

Creates a hierarchical profiler using begin/end pairs to measure sections of code.

```java
package com.jecs.profiling;

import java.util.*;

/**
 * Hierarchical frame profiler for measuring CPU time.
 *
 * KEY CONCEPTS:
 * - ThreadLocal: Each thread gets its own profiler state
 * - Section stack: Tracks nested sections (hierarchy)
 * - Statistics map: Accumulates timing data per section
 *
 * USAGE PATTERN:
 * ```java
 * Profiler.begin("Update");
 *   Profiler.begin("Physics");
 *   physicsSystem.update();
 *   Profiler.end("Physics");
 * Profiler.end("Update");
 * ```
 *
 * WHY THREADLOCAL?
 * Prevents race conditions in multithreaded code. Each thread
 * maintains its own section stack independently.
 *
 * PERFORMANCE:
 * - begin() cost: ~50 nanoseconds (stack push + time capture)
 * - end() cost: ~100 nanoseconds (stack pop + statistics update)
 * - 100 sections per frame = ~15 microseconds overhead (negligible!)
 */
public class Profiler {

    // Thread-local state (each thread has its own stack)
    // WHY? If Thread A calls begin("Physics") and Thread B calls begin("Rendering"),
    // they shouldn't interfere with each other's stacks!
    private static final ThreadLocal<ProfilerState> state =
        ThreadLocal.withInitial(ProfilerState::new);

    // Global statistics map (synchronized for thread safety)
    private static final Map<String, ProfileSection> sections =
        Collections.synchronizedMap(new HashMap<>());

    private static boolean enabled = true;

    /**
     * Begins a profiling section.
     *
     * WHAT HAPPENS:
     * 1. Capture current time (nanoTime)
     * 2. Create ProfileSection with name, depth, and start time
     * 3. Push section onto thread-local stack
     * 4. Increment depth counter
     *
     * THREAD SAFETY:
     * ThreadLocal ensures each thread has its own state.
     * No locks needed here - very fast!
     */
    public static void begin(String name) {
        if (!enabled) return;

        ProfilerState s = state.get();

        long startTime = System.nanoTime();  // ~25ns overhead

        ProfileSection section = new ProfileSection(name, s.currentDepth, startTime);
        s.sectionStack.push(section);  // ~20ns
        s.currentDepth++;
    }

    /**
     * Ends a profiling section.
     *
     * WHAT HAPPENS:
     * 1. Capture current time
     * 2. Pop section from stack
     * 3. Validate name matches (catch mismatched begin/end)
     * 4. Calculate elapsed time
     * 5. Update statistics (synchronized)
     *
     * STATISTICS TRACKED:
     * - totalTime: Sum of all calls
     * - callCount: How many times called
     * - maxTime: Slowest call
     * - minTime: Fastest call
     *
     * WHY SYNCHRONIZED?
     * Multiple threads might update the same section name simultaneously.
     * synchronized(sections) prevents corruption.
     */
    public static void end(String name) {
        if (!enabled) return;

        long endTime = System.nanoTime();
        ProfilerState s = state.get();

        if (s.sectionStack.isEmpty()) {
            System.err.println("Profiler.end() called without matching begin() for: " + name);
            return;
        }

        ProfileSection section = s.sectionStack.pop();

        // Validation: Catch bugs like begin("A") → end("B")
        if (!section.name.equals(name)) {
            System.err.println("Profiler mismatch: expected '" + section.name + "', got '" + name + "'");
        }

        s.currentDepth--;

        long elapsed = endTime - section.startTime;

        // Update global statistics (thread-safe)
        synchronized (sections) {
            ProfileSection stats = sections.get(name);

            if (stats == null) {
                stats = new ProfileSection(name, section.depth, 0);
                sections.put(name, stats);
            }

            // Accumulate statistics
            stats.totalTime += elapsed;
            stats.callCount++;
            stats.maxTime = Math.max(stats.maxTime, elapsed);
            stats.minTime = Math.min(stats.minTime, elapsed);
        }
    }

    /**
     * Auto-closeable section for try-with-resources.
     *
     * MODERN JAVA PATTERN:
     * ```java
     * try (var scope = Profiler.scope("Physics")) {
     *     physicsSystem.update();
     * }  // Automatically calls end("Physics")
     * ```
     *
     * BENEFITS:
     * - Can't forget to call end()
     * - Guaranteed cleanup (even if exception thrown)
     * - Cleaner code (less nesting)
     */
    public static ProfileScope scope(String name) {
        begin(name);
        return new ProfileScope(name);
    }

    /**
     * Prints profiling results.
     *
     * OUTPUT FORMAT:
     * ```
     * === Frame Profiling ===
     * Update: 3.456ms avg (10.368ms total, 3 calls, min=3.123ms, max=3.789ms)
     *   Physics: 4.123ms avg (4.123ms total, 1 call, min=4.123ms, max=4.123ms)
     *   Scripts: 2.456ms avg (4.912ms total, 2 calls, min=2.234ms, max=2.678ms)
     * ```
     *
     * SORTING:
     * Results are sorted by total time (slowest first).
     * Makes it easy to spot bottlenecks!
     */
    public static void printFrame() {
        if (sections.isEmpty()) return;

        System.out.println("=== Frame Profiling ===");

        // Sort by total time (descending)
        // WHY? Put slowest sections at the top (easier to spot problems)
        List<ProfileSection> sorted = new ArrayList<>(sections.values());
        sorted.sort(Comparator.comparingLong(s -> -s.totalTime));

        for (ProfileSection section : sorted) {
            // Convert nanoseconds → milliseconds
            double avgMs = (section.totalTime / (double) section.callCount) / 1_000_000.0;
            double totalMs = section.totalTime / 1_000_000.0;
            double maxMs = section.maxTime / 1_000_000.0;
            double minMs = section.minTime / 1_000_000.0;

            // Indentation shows hierarchy
            String indent = "  ".repeat(section.depth);

            System.out.printf("%s%s: %.3fms avg (%.3fms total, %d calls, min=%.3fms, max=%.3fms)%n",
                indent, section.name, avgMs, totalMs, section.callCount, minMs, maxMs);
        }

        System.out.println();
    }

    /**
     * Clears profiling data (call once per frame).
     *
     * WHY CLEAR?
     * Profiling data accumulates. If we don't clear, frame 100's
     * data includes frames 1-99, making averages meaningless.
     *
     * WHEN TO CALL:
     * After printFrame(), before next frame starts.
     */
    public static void clear() {
        synchronized (sections) {
            sections.clear();
        }
    }

    /**
     * Gets a snapshot of current profiling data.
     *
     * USE CASE:
     * For ImGui overlay showing real-time profiling.
     * Creates a copy to avoid concurrent modification.
     */
    public static Map<String, ProfileSection> getSnapshot() {
        synchronized (sections) {
            return new HashMap<>(sections);
        }
    }

    public static void setEnabled(boolean enabled) {
        Profiler.enabled = enabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    // Thread-local state (per-thread)
    private static class ProfilerState {
        final Deque<ProfileSection> sectionStack = new ArrayDeque<>();
        int currentDepth = 0;
    }

    // Profiling section data
    public static class ProfileSection {
        public String name;
        public int depth;           // Hierarchy level (0 = root, 1 = child, etc.)
        public long startTime;      // nanoTime when begin() was called
        public long totalTime;      // Accumulated time across all calls
        public long callCount;      // How many times this section was called
        public long maxTime = Long.MIN_VALUE;  // Slowest call
        public long minTime = Long.MAX_VALUE;  // Fastest call

        public ProfileSection(String name, int depth, long startTime) {
            this.name = name;
            this.depth = depth;
            this.startTime = startTime;
        }
    }

    // Auto-closeable wrapper for try-with-resources
    public static class ProfileScope implements AutoCloseable {
        private final String name;

        public ProfileScope(String name) {
            this.name = name;
        }

        @Override
        public void close() {
            Profiler.end(name);
        }
    }
}
```

---

## Part 2: GPU Profiler

### Understanding GPU Timestamps

**The Problem:**

```java
// This measures CPU time, NOT GPU time!
long start = System.nanoTime();
vkCmdDraw(commandBuffer, ...);  // Records command (CPU work)
long end = System.nanoTime();
// Time: ~1 microsecond (command recording)
// But GPU hasn't executed yet! Real GPU time might be 5ms!
```

**The Solution: GPU Timestamps**

```
CPU submits commands → GPU executes later

Command Buffer:
  vkCmdWriteTimestamp("Shadow Start")  ← GPU writes time when it reaches this
  vkCmdDraw(shadows...)
  vkCmdWriteTimestamp("Shadow End")    ← GPU writes time here
  vkCmdDraw(main scene...)
  vkCmdWriteTimestamp("Main End")

Later, CPU reads timestamps:
  Shadow time = Shadow End - Shadow Start
```

**Key Insight:**
- CPU commands execute in microseconds
- GPU commands execute in milliseconds
- Timestamps let us measure actual GPU execution time

---

### Timestamp Precision

**GPU timestamp period:**

```java
VkPhysicalDeviceProperties properties = ...;
float timestampPeriod = properties.limits().timestampPeriod();
// Example: 1.0 ns (NVIDIA)
// Example: 52.08 ns (Some AMD cards)
```

**Calculating actual time:**

```java
long startTicks = 1000;
long endTicks = 3000;
long deltaTicks = 2000;

float timeNs = deltaTicks * timestampPeriod;  // 2000 * 1.0 = 2000 ns
float timeMs = timeNs / 1_000_000.0;          // 0.002 ms
```

**Professional Tool Comparison:**

| Tool | How It Works |
|------|--------------|
| **RenderDoc** | Injects into Vulkan driver, captures ALL commands |
| **NSight** (NVIDIA) | GPU hardware counters, cycle-accurate |
| **Radeon GPU Profiler** (AMD) | Hardware performance counters |
| **JECS** | Simple timestamp queries (portable, works everywhere) |

---

### VulkanProfiler.java - Implementation

(Code omitted for brevity - see original chapter for full implementation with enhanced inline comments)

---

## Part 3: Memory Profiler

### Understanding Java Memory

**Java Heap Layout:**

```
┌─────────────────────────────────────────────┐
│           Java Heap (Max: 2GB)              │
├─────────────────────────────────────────────┤
│  Used: 512MB                                │
│  ┌──────────────────────────────────────┐  │
│  │  Objects (Entity, Component, etc.)   │  │
│  │  Arrays (float[], int[], etc.)       │  │
│  │  String data                         │  │
│  └──────────────────────────────────────┘  │
│  Free: 1.5GB                                │
└─────────────────────────────────────────────┘
```

**Memory Regions:**

| Region | What | Size |
|--------|------|------|
| **Used** | Currently allocated objects | Variable |
| **Committed** | Memory reserved from OS | Variable |
| **Max** | `-Xmx` setting | Fixed at startup |

**Garbage Collection:**

```
Before GC: Used = 1GB
  → GC runs (pauses game for 20ms!)
After GC: Used = 300MB (freed 700MB of garbage)
```

**Problem:** Frequent GC = stuttering!

---

### GC Impact on Frame Time

**Example Scenario:**

```
Frame 1:  15ms  ✓
Frame 2:  16ms  ✓
Frame 3:  35ms  ✗ GC pause! (20ms GC + 15ms normal)
Frame 4:  15ms  ✓
```

**Visible as:**
- Stutter/hitch every few seconds
- Dropped frames
- Input lag

**Solution:** Profile memory to find allocation hotspots!

---

## Part 4: Identifying Bottlenecks

### The 80/20 Rule of Optimization

**Pareto Principle:**

80% of frame time is spent in 20% of code.

**Example:**

```
Total frame time: 20ms

Profiler output:
  Physics:       12ms  (60% of frame!)  ← OPTIMIZE THIS!
  Rendering:      5ms  (25%)
  Scripts:        2ms  (10%)
  Audio:          1ms  (5%)
```

**Focus optimization on Physics** = 6x impact vs optimizing Audio!

---

### CPU vs GPU Bottleneck Detection

**Scenario 1: CPU-Bound**

```
CPU Time: 20ms
GPU Time: 10ms
FPS: 50 (limited by 20ms CPU)

SOLUTION:
- Reduce entity queries
- Use job system for parallelism
- Cache calculations
- Reduce script overhead
```

**Scenario 2: GPU-Bound**

```
CPU Time: 10ms
GPU Time: 20ms
FPS: 50 (limited by 20ms GPU)

SOLUTION:
- Batch draw calls
- Reduce shader complexity
- Lower resolution
- Use LOD (Level of Detail)
```

**Scenario 3: Balanced (Ideal)**

```
CPU Time: 14ms
GPU Time: 14ms
FPS: 60+ ✓

RESULT: Both under 16.67ms budget - no optimization needed!
```

---

### Memory Bottleneck Detection

**Signs of Memory Problems:**

```
Frame 1-59:  15ms  ✓
Frame 60:    35ms  ✗ GC spike!
Frame 61-119: 15ms  ✓
Frame 120:   35ms  ✗ GC spike!
```

**Diagnosis:**

```bash
$ java -XX:+PrintGCDetails MyGame

[GC 300MB->100MB, 0.020 secs]  ← 20ms pause!
[GC 300MB->100MB, 0.018 secs]  ← Happens every 2 seconds
```

**Cause:** Allocating 200MB per second → GC every 60 frames

**Solution:** Find allocation hotspots with profiler!

---

## Part 5: Common Performance Pitfalls

### Pitfall 1: Object Allocation in Hot Loops

```java
// BAD: Allocates 10,000 Vector3f per frame!
// At 60 FPS: 600,000 allocations/second = constant GC
for (Entity entity : entities) {  // 10,000 entities
    Vector3f temp = new Vector3f();  // NEW ALLOCATION!
    temp.set(1, 0, 0);
    transform.position.add(temp);
}
// GC impact: ~15ms every 30 frames

// GOOD: Reuse single temp vector
Vector3f temp = new Vector3f();
for (Entity entity : entities) {
    temp.set(1, 0, 0);
    transform.position.add(temp);
}
// GC impact: 0ms (no allocations!)
```

**Performance gain:** 15ms → 0ms every 30 frames!

---

### Pitfall 2: String Concatenation

```java
// BAD: Creates 10,000 temporary strings!
for (Entity entity : entities) {
    String debug = "Entity " + entity.id() + " at " + transform.position;
    // Under the hood: StringBuilder allocation, 3 string allocations!
}

// GOOD: Use StringBuilder reuse
StringBuilder sb = new StringBuilder(64);
for (Entity entity : entities) {
    sb.setLength(0);  // Clear
    sb.append("Entity ").append(entity.id()).append(" at ").append(transform.position);
    String debug = sb.toString();
}
```

---

### Pitfall 3: Boxing/Unboxing

```java
// BAD: Integer boxing (creates objects!)
List<Integer> ids = new ArrayList<>();
for (Entity entity : entities) {
    ids.add(entity.id());  // int → Integer (allocates!)
}

// GOOD: Use primitive arrays
int[] ids = new int[entities.size()];
for (int i = 0; i < entities.size(); i++) {
    ids[i] = entities.get(i).id();  // No allocation
}
```

**Performance:**
- BAD: 10,000 Integer objects = ~400KB = GC pressure
- GOOD: int array = 40KB = zero GC

---

## Summary & Next Steps

Congratulations! You've completed the **JECS Game Engine Tutorial Series**! 🎉

### What You've Built

**Complete Game Engine (~15,000+ lines):**
- ✅ Vulkan rendering (2D + 3D)
- ✅ ECS architecture (100K+ entities at 60 FPS)
- ✅ Scene serialization
- ✅ Input & audio systems
- ✅ ImGui editor
- ✅ PBR rendering with lighting
- ✅ Physics simulation
- ✅ Lua scripting
- ✅ Multithreaded job system
- ✅ **Professional profiling tools**

### Key Concepts Learned

**Profiling:**
- CPU profiling (hierarchical sections)
- GPU profiling (Vulkan timestamps)
- Memory profiling (heap + GC tracking)

**Bottleneck Identification:**
- 80/20 rule (focus on what matters)
- CPU vs GPU detection
- Memory allocation hotspots

**Optimization Strategies:**
- Object pooling
- Primitive arrays
- Avoiding allocations
- Parallel processing

### Professional Engine Comparison

| Feature | Unity | Unreal | JECS |
|---------|-------|--------|------|
| **CPU Profiler** | Profiler window | Stat Unit | Hierarchical |
| **GPU Profiler** | Frame Debugger | Stat GPU | Timestamps |
| **Memory Profiler** | Memory Profiler | Stat Memory | MXBean |
| **Real-time UI** | Built-in | Built-in | ImGui overlay |

### Performance Achievements

**Your engine can now:**
- Handle 100,000+ entities at 60 FPS
- Process 50M+ entities/second
- Maintain <2ms frame times (500 FPS capable!)
- Profile CPU, GPU, and memory
- Identify and fix bottlenecks

### Next Steps

1. **Build a Complete Game** - Use all systems together
2. **Optimize Further** - GPU-driven rendering, compute shaders
3. **Share Your Work** - GitHub, blog posts, YouTube tutorials
4. **Study Professional Engines** - Godot, Bevy, Flax
5. **Join Communities** - r/gamedev, Discord servers, forums

---

## Final Thoughts

You now have the knowledge to:
- ✅ Build game engines from scratch
- ✅ Work with modern graphics APIs (Vulkan)
- ✅ Design high-performance architectures
- ✅ Optimize for 100K+ entities at 60 FPS
- ✅ Profile and measure performance
- ✅ Create complete, playable games

**Remember:**
- Measure before optimizing
- Focus on bottlenecks (80/20 rule)
- Learn from others' code
- Share your knowledge

**Keep building, keep learning, and have fun creating games!** 🚀

---

**Previous:** [← Chapter 12 - ECS Optimization](chapter-12-ecs-optimization.md)
