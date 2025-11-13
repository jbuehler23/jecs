# Chapter 1: Window, Engine Loop & Vulkan Clear Screen
## Building the Foundation with Complete Vulkan Implementation

**What You'll Learn:**
- Create a GLFW window with Vulkan support
- Implement a fixed-timestep game loop
- **Complete Vulkan initialization** (instance, device, swap chain) - no stubs!
- Record command buffers to clear the screen with cycling colors
- Present frames to the window
- Handle window resizing and cleanup

**What You'll Build:**
A window displaying a **rainbow clear screen** that cycles through colors - proof your Vulkan renderer works!

**Estimated Time:** 3-4 hours

**Prerequisites:** Chapter 0 completed successfully

---

## Introduction: The Engine Loop

Every game engine has a **main loop** that runs continuously at 60+ frames per second:

```
while (running) {
    1. Process input
    2. Update game logic (fixed timestep)
    3. Render frame
    4. Present to screen
}
```

This chapter focuses on steps 3-4 (rendering infrastructure). We'll build a complete, working Vulkan renderer from scratch.

### Why This Matters

The engine loop is the heartbeat of your game. A proper loop ensures:

- **Consistent physics**: Fixed timestep prevents speed variations
- **Smooth rendering**: Variable delta time for interpolation
- **Responsive input**: Poll events every frame
- **Frame rate independence**: Game runs same speed on any hardware

### Game Loop Patterns: A Deep Dive

There are three common game loop patterns. Understanding them is crucial for professional game development.

#### Pattern 1: Variable Timestep (Simple but Problematic)

```java
while (running) {
    float deltaTime = calculateDeltaTime(); // Could be 16ms, 32ms, or 8ms
    update(deltaTime);  // Physics depends on deltaTime
    render();
}
```

**Problem:** Physics becomes frame-rate dependent!

**Example:**
```
High-end PC (120 FPS): deltaTime = 8ms  → Bullet travels 8 units
Low-end PC (30 FPS):   deltaTime = 33ms → Bullet travels 33 units
```

The bullet moves **4x faster** on slower hardware! This is why old games (Fallout 76, Skyrim) had physics bugs at high framerates.

#### Pattern 2: Fixed Timestep (Deterministic, Professional)

```java
final double FIXED_DELTA = 1.0 / 60.0; // Always 16.67ms
double accumulator = 0.0;

while (running) {
    double frameTime = calculateDeltaTime();
    accumulator += frameTime;

    // Update physics in fixed steps
    while (accumulator >= FIXED_DELTA) {
        update(FIXED_DELTA);  // Always 16.67ms
        accumulator -= FIXED_DELTA;
    }

    // Render with interpolation
    float alpha = accumulator / FIXED_DELTA;
    render(alpha);  // Smooth interpolation
}
```

**Benefits:**
- Physics is **deterministic** (same results every time)
- Works identically on all hardware
- Network-friendly (lockstep multiplayer)
- Replay systems work perfectly

**Used by:**
- Unity (FixedUpdate runs at 50 Hz by default)
- Unreal (Substepping for physics)
- Source Engine (Valve's games)
- Rocket League (deterministic physics for replays)

#### Pattern 3: Semi-Fixed (Hybrid Approach)

```java
// Update at target rate, but don't spiral
final double TARGET_DELTA = 1.0 / 60.0;
final double MAX_DELTA = 0.25; // Cap at 250ms

while (running) {
    double deltaTime = Math.min(calculateDeltaTime(), MAX_DELTA);
    update(deltaTime);  // Capped variable timestep
    render();
}
```

**Use case:** Single-player games where determinism isn't critical.

**We're using Pattern 2** (fixed timestep with interpolation) because it's the industry standard for professional games.

---

## The "Spiral of Death" Problem

Imagine your game runs at 60 FPS (16.67ms per frame). Suddenly, a heavy operation takes 100ms:

```
Frame 1: frameTime = 100ms
accumulator = 100ms

Loop iteration 1: accumulator >= 16.67? Yes → update, accumulator = 83.33ms
Loop iteration 2: accumulator >= 16.67? Yes → update, accumulator = 66.66ms
Loop iteration 3: accumulator >= 16.67? Yes → update, accumulator = 49.99ms
Loop iteration 4: accumulator >= 16.67? Yes → update, accumulator = 33.32ms
Loop iteration 5: accumulator >= 16.67? Yes → update, accumulator = 16.65ms
Loop iteration 6: accumulator >= 16.67? No  → render
```

We just ran **6 update ticks** trying to catch up! If each update takes 10ms, that's 60ms of updates, which causes the **next frame** to be slow, causing **more catch-up**, creating a death spiral.

**Solution:** Cap the accumulator:

```java
final double MAX_ACCUMULATOR = 0.25; // 250ms max (15 frames behind)
deltaTime = Math.min(currentTime - lastTime, MAX_ACCUMULATOR);
```

Now we accept "slow motion" instead of freezing. The game slows down temporarily instead of locking up.

---

## Concepts: Vulkan Rendering Overview

### Traditional Graphics APIs (OpenGL)

```java
while (running) {
    glClear(GL_COLOR_BUFFER_BIT);
    glDrawArrays(...);
    SwapBuffers();
}
```

OpenGL is **imperative** and **immediate mode**: you issue commands directly to the GPU.

**OpenGL Driver's Hidden Work:**
- Validates state every draw call (expensive!)
- Manages memory implicitly (you don't see allocations)
- Synchronizes automatically (adds overhead)
- Batches commands in the driver (you have no control)

**Result:** 15-20% CPU overhead per frame just managing state.

### Vulkan Approach

```
// Setup (once)
Create instance, device, swap chain
Allocate command buffers
Record commands into buffers

// Loop
Acquire next swap chain image
Submit recorded command buffer
Present image
```

Vulkan is **explicit** and **deferred**: you record commands into buffers, then submit batches to the GPU.

**Vulkan Philosophy:**
- You manage **everything** (memory, synchronization, lifetimes)
- Driver does **minimal validation** in release builds
- Commands are **pre-recorded** and reused
- Multi-threading is **first-class** (record on multiple threads)

**Result:** 3-5% CPU overhead. 75% reduction compared to OpenGL!

### Vulkan vs OpenGL: Draw Call Performance

**OpenGL:**
```
Frame 1:
  glBindTexture(texture1)     ← Driver validates state
  glBindBuffer(vbo1)          ← Driver validates state
  glDrawArrays(...)           ← Driver submits to GPU
  glBindTexture(texture2)     ← Driver validates state
  glBindBuffer(vbo2)          ← Driver validates state
  glDrawArrays(...)           ← Driver submits to GPU

  Total CPU time: ~0.5ms for 10,000 draws
```

**Vulkan:**
```
Setup (once):
  Record commands to buffer  ← Pre-recorded, no runtime overhead

Frame 1:
  vkQueueSubmit(commandBuffer) ← Submit entire batch at once

  Total CPU time: ~0.05ms for 10,000 draws
```

**Real-world impact:**
- **OpenGL:** ~10,000 draw calls per frame max before CPU bottleneck
- **Vulkan:** ~100,000 draw calls per frame achievable

This is why Vulkan can render massive open-world games with millions of objects.

---

## Key Vulkan Objects

Understanding Vulkan's object hierarchy is essential. Here's the dependency graph:

```
VkInstance (Global Vulkan context)
    ↓
VkPhysicalDevice (Your GPU hardware)
    ↓
VkDevice (Logical interface to GPU)
    ↓
    ├─→ VkQueue (Command submission)
    │      ↓
    │   VkCommandPool (Command buffer allocator)
    │      ↓
    │   VkCommandBuffer (Recorded GPU commands)
    │
    └─→ VkSwapchain (Window images)
           ↓
        VkImage[] (Framebuffers for display)
```

**Object Explanations:**

### 1. VkInstance
The root object connecting to the Vulkan library.

**What it does:**
- Loads the Vulkan loader (DLL/SO)
- Enables extensions (debug utils, surface)
- Activates validation layers (error checking)

**Analogy:** Opening a connection to a database server.

**Code:**
```java
VkApplicationInfo appInfo = ...;
appInfo.apiVersion(VK_API_VERSION_1_0);

VkInstanceCreateInfo createInfo = ...;
createInfo.pApplicationInfo(appInfo);
createInfo.ppEnabledExtensionNames(extensions);

vkCreateInstance(createInfo, null, pInstance);
```

### 2. VkPhysicalDevice
Represents your actual GPU hardware.

**What it does:**
- Query GPU properties (name, memory, limits)
- Find queue families (graphics, compute, transfer)
- Check feature support (tessellation, geometry shaders)

**Selection criteria:**
```java
// Prefer discrete GPUs over integrated
if (properties.deviceType() == VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU) {
    score += 1000;
}

// More VRAM = better
score += properties.limits().maxImageDimension2D();
```

**We use:** First suitable GPU (simple for tutorials).

### 3. VkDevice
Logical device - your interface to the GPU.

**What it does:**
- Creates queues for command submission
- Allocates memory
- Creates resources (buffers, images, pipelines)

**Analogy:** A "handle" or "connection" to the GPU, like a file descriptor.

**Code:**
```java
VkDeviceCreateInfo createInfo = ...;
createInfo.pQueueCreateInfos(queueInfos);
createInfo.ppEnabledExtensionNames(deviceExtensions); // e.g., swapchain

vkCreateDevice(physicalDevice, createInfo, null, pDevice);
```

### 4. VkQueue
Command submission queue.

**Queue families:**
- **Graphics**: Rendering commands (draw, clear, blit)
- **Compute**: Compute shaders (particle systems, AI)
- **Transfer**: Memory operations (upload textures)

**Our setup:**
```java
// Find a queue family that supports:
// 1. Graphics operations (VK_QUEUE_GRAPHICS_BIT)
// 2. Presenting to our window surface
```

Many GPUs have a single queue family that does everything. High-end GPUs have separate families for parallelism.

### 5. VkSwapchain
Manages images for presenting to the window.

**Why it exists:**
Your monitor refreshes at 60 Hz (every 16.67ms). While the monitor displays frame N, you need to render frame N+1. **Double buffering** solves this:

```
CPU/GPU:  [Render Frame 1] [Render Frame 2] [Render Frame 3]
Monitor:  [Display Frame 0]  [Display Frame 1]  [Display Frame 2]
                   ↑                   ↑                   ↑
              Swap at VSync       Swap at VSync       Swap at VSync
```

**Swapchain manages:**
- 2-3 images (front buffer, back buffer, optionally triple buffering)
- Image format (B8G8R8A8_SRGB for color accuracy)
- Present mode (FIFO for vsync, MAILBOX for low latency)

### 6. VkCommandBuffer
Pre-recorded list of GPU commands.

**Why pre-record?**
- **Performance:** Record once, submit many times
- **Multi-threading:** Record on multiple CPU cores in parallel
- **Efficiency:** Driver optimizes the entire batch

**Our usage:**
```java
vkBeginCommandBuffer(commandBuffer, ...);
vkCmdPipelineBarrier(...)  // Transition image layout
vkCmdClearColorImage(...)  // Clear to rainbow color
vkCmdPipelineBarrier(...)  // Transition to present
vkEndCommandBuffer(commandBuffer);
```

Then submit:
```java
vkQueueSubmit(graphicsQueue, commandBuffer, fence);
```

### 7. VkSemaphore and VkFence
Synchronization primitives.

**VkSemaphore:** GPU-GPU synchronization
```
Semaphore: imageAvailable
  ↓
[Acquire Image] → Signal imageAvailable
  ↓
[Render Commands] wait on imageAvailable → Signal renderFinished
  ↓
[Present] wait on renderFinished
```

**VkFence:** GPU-CPU synchronization
```
Frame 1: Submit commands → Signal fence1
Frame 2: Wait on fence1 (ensure Frame 1 finished) → Submit commands → Signal fence2
```

**Why both?**
- Semaphores are lightweight (GPU-side only)
- Fences allow CPU to wait (synchronize game logic with rendering)

---

## Double Buffering vs Triple Buffering

Understanding buffering is critical for smooth rendering.

### Single Buffering (Don't Use)

```
Timeline:
Monitor: [Display Frame 1........................]
GPU:     [Render Frame 2] ← Tears! Writing while displaying
```

**Result:** Screen tearing (new frame appears mid-refresh).

### Double Buffering (VSync)

```
Frame buffers: Front, Back

Monitor: [Display Front (Frame 1)]  [Display Front (Frame 2)]
GPU:     [Render to Back] → Swap → [Render to Back] → Swap
              ↓ VSync wait          ↓ VSync wait
```

**Benefits:**
- No tearing
- Consistent 60 FPS

**Drawback:**
- Input latency (1-2 frames)
- If you miss VSync → 30 FPS

### Triple Buffering (Mailbox Mode)

```
Frame buffers: Display, Render, Queued

Monitor: [Display Frame 1]  [Display Frame 2]  [Display Frame 3]
GPU:     [Render Frame 2] → Queue
         [Render Frame 3] → Queue (replaces Frame 2)
         [Render Frame 4] → Queue (replaces Frame 3)
```

**Benefits:**
- Lower latency than double buffering
- GPU never stalls (always has a buffer to render to)

**Drawback:**
- Uses more VRAM
- Can render "wasted" frames (if GPU faster than monitor)

**Present modes in Vulkan:**
- `VK_PRESENT_MODE_FIFO_KHR`: Double buffering, vsync (guaranteed available)
- `VK_PRESENT_MODE_MAILBOX_KHR`: Triple buffering, low latency (prefer if available)
- `VK_PRESENT_MODE_IMMEDIATE_KHR`: No vsync, tearing (for benchmarks)

**Our code:**
```java
// Prefer mailbox (low latency), fallback to FIFO (vsync)
int chooseSwapPresentMode(List<Integer> availableModes) {
    return availableModes.stream()
        .filter(mode -> mode == VK_PRESENT_MODE_MAILBOX_KHR)
        .findFirst()
        .orElse(VK_PRESENT_MODE_FIFO_KHR);
}
```

---

## Architecture: Engine Structure

We'll create these classes:

```
core/
├── Application.java    (main loop, owns Window and Engine)
├── Window.java         (GLFW window wrapper)
├── Engine.java         (manages initialization, owns VulkanContext)
└── Time.java           (delta time, fixed timestep logic)

renderer/
└── VulkanContext.java  (COMPLETE Vulkan initialization and rendering)
```

**Separation of concerns:**
- `Application`: High-level loop and coordination
- `Window`: GLFW abstraction (platform-independent windowing)
- `Engine`: Subsystem management (renderer, ECS, audio)
- `VulkanContext`: **Full Vulkan implementation** (no stubs!)
- `Time`: Frame timing and fixed timestep accumulator

**Why separate Time into its own class?**

Compare this messy approach:
```java
// BAD: Time logic scattered everywhere
class Application {
    private double lastFrameTime = System.nanoTime() / 1e9;
    private double accumulator = 0.0;

    void run() {
        while (running) {
            double currentTime = System.nanoTime() / 1e9;
            double deltaTime = currentTime - lastFrameTime;
            lastFrameTime = currentTime;
            accumulator += deltaTime;

            while (accumulator >= 0.016666) {
                // Update logic
                accumulator -= 0.016666;
            }
        }
    }
}
```

With a clean abstraction:
```java
// GOOD: Time logic encapsulated
class Application {
    private Time time = new Time();

    void run() {
        while (running) {
            time.update();

            while (time.shouldFixedUpdate()) {
                // Update logic (clean!)
            }
        }
    }
}
```

**Benefits:**
- **Testable:** Mock Time for unit tests
- **Reusable:** Use Time in any game engine
- **Hot-reloadable:** Change Time.java and reload (with DCEVM)
- **Clear responsibility:** Time does one thing well

---

## Implementation

### Step 1: Time Management

Create `src/main/java/com/yourname/engine/core/Time.java`:

```java
package com.yourname.engine.core;

/**
 * Manages frame timing and fixed timestep logic.
 *
 * <p>Uses a fixed timestep for game logic (60 ticks/sec) and variable
 * rendering delta for smooth interpolation.
 *
 * <h2>Why Fixed Timestep?</h2>
 * <p>Variable timestep causes physics bugs:
 * <pre>
 * High FPS (120):  deltaTime = 8ms  → Bullet travels 8 units
 * Low FPS (30):    deltaTime = 33ms → Bullet travels 33 units
 * </pre>
 *
 * <p>Fixed timestep ensures deterministic physics:
 * <pre>
 * All platforms:   fixedDelta = 16.67ms → Bullet always travels 16.67 units
 * </pre>
 *
 * <h2>Accumulator Pattern</h2>
 * <p>The accumulator stores "leftover" time:
 * <pre>
 * Frame 1: Real delta = 18ms
 *   accumulator = 18ms
 *   Update once (consume 16.67ms) → accumulator = 1.33ms
 *
 * Frame 2: Real delta = 15ms
 *   accumulator = 1.33ms + 15ms = 16.33ms
 *   Update once (consume 16.67ms) → accumulator = -0.34ms (rounds to 0)
 * </pre>
 *
 * <p>This ensures updates run at exactly 60 Hz on average, regardless of frame rate.
 */
public class Time {
    // Fixed timestep for physics/logic (60 TPS = 16.67ms per tick)
    private static final double FIXED_TIMESTEP = 1.0 / 60.0;

    // Max accumulator value to prevent "spiral of death"
    // If game hitches for >250ms, we accept slow motion instead of freezing
    private static final double MAX_ACCUMULATOR = 0.25; // 15 frames worth

    private double lastFrameTime;
    private double accumulator;

    private double deltaTime;        // Time since last frame (variable)
    private double fixedDeltaTime;   // Fixed timestep (always 1/60)
    private double elapsedTime;      // Total time since start
    private int frameCount;

    public Time() {
        this.lastFrameTime = getCurrentTime();
        this.accumulator = 0.0;
        this.deltaTime = 0.0;
        this.fixedDeltaTime = FIXED_TIMESTEP;
        this.elapsedTime = 0.0;
        this.frameCount = 0;
    }

    /**
     * Call once per frame. Updates delta time and accumulator.
     *
     * <p>This method:
     * 1. Calculates time since last frame
     * 2. Clamps it to MAX_ACCUMULATOR (prevent spiral of death)
     * 3. Adds to accumulator (for fixed updates)
     * 4. Updates elapsed time and frame count
     */
    public void update() {
        double currentTime = getCurrentTime();

        // Calculate delta, clamped to prevent spiral of death
        deltaTime = Math.min(currentTime - lastFrameTime, MAX_ACCUMULATOR);
        lastFrameTime = currentTime;

        accumulator += deltaTime;
        elapsedTime += deltaTime;
        frameCount++;
    }

    /**
     * Check if we should run a fixed update tick.
     * Call in a while loop: while (time.shouldFixedUpdate()) { ... }
     *
     * <p>Returns true if accumulator has >= 16.67ms of time stored.
     * Consuming a tick subtracts 16.67ms from the accumulator.
     *
     * <p>Example usage:
     * <pre>
     * time.update();
     * while (time.shouldFixedUpdate()) {
     *     physics.step(time.getFixedDeltaTime());
     * }
     * </pre>
     */
    public boolean shouldFixedUpdate() {
        if (accumulator >= FIXED_TIMESTEP) {
            accumulator -= FIXED_TIMESTEP;
            return true;
        }
        return false;
    }

    /**
     * Get interpolation alpha for smooth rendering between fixed ticks.
     * Alpha = accumulator / fixedDeltaTime (0.0 to 1.0)
     *
     * <p>Use for interpolating entity positions:
     * <pre>
     * Vector3f renderPos = lerp(previousPos, currentPos, time.getAlpha());
     * </pre>
     *
     * <p>This prevents jitter when rendering at 144 FPS but updating physics at 60 Hz.
     *
     * <h2>Why Interpolation Matters</h2>
     * <p>Without interpolation (60 Hz updates, 144 Hz render):
     * <pre>
     * Physics ticks:  |-------|-------|-------|  (60 Hz)
     * Render frames:  ||||||||||||||||||||||||||  (144 Hz)
     *                    ↑       ↑       ↑
     *              Stutter!  Stutter!  Stutter!
     * </pre>
     *
     * <p>With interpolation:
     * <pre>
     * Physics ticks:  |-------|-------|-------|  (60 Hz)
     * Render frames:  ▁▂▃▄▅▆▇█▁▂▃▄▅▆▇█▁▂▃▄▅▆▇█  (Smooth gradient)
     * </pre>
     */
    public float getAlpha() {
        return (float) (accumulator / FIXED_TIMESTEP);
    }

    // Getters

    /**
     * Get delta time since last frame (variable, typically 8-33ms).
     * Use for rendering animations, camera movement, etc.
     *
     * <p>DO NOT use for physics! Use getFixedDeltaTime() instead.
     */
    public double getDeltaTime() { return deltaTime; }

    /**
     * Get fixed delta time (always 16.67ms = 1/60 second).
     * Use for physics updates, gameplay logic, etc.
     */
    public double getFixedDeltaTime() { return fixedDeltaTime; }

    /**
     * Get total elapsed time since engine start.
     * Useful for time-based effects (sin wave animations, etc.)
     */
    public double getElapsedTime() { return elapsedTime; }

    /**
     * Get total frame count since start.
     * Useful for debugging, profiling, frame pacing analysis.
     */
    public int getFrameCount() { return frameCount; }

    /**
     * Get current frames per second (based on last frame's delta time).
     * This is instantaneous FPS, not averaged.
     */
    public int getFPS() {
        return deltaTime > 0 ? (int) (1.0 / deltaTime) : 0;
    }

    /**
     * Get current time in seconds with nanosecond precision.
     *
     * <p>Uses System.nanoTime() instead of System.currentTimeMillis() because:
     * - nanoTime() is monotonic (never goes backwards, even if system clock changes)
     * - nanoTime() has higher precision (nanoseconds vs milliseconds)
     * - currentTimeMillis() can jump during NTP sync or daylight savings
     */
    private static double getCurrentTime() {
        return System.nanoTime() / 1_000_000_000.0; // Convert to seconds
    }
}
```

**Key Concepts Explained:**

#### Why 60 Hz for physics?

**Historical reasons:**
- CRT monitors refreshed at 59.94 Hz (NTSC) or 50 Hz (PAL)
- Early consoles (NES, SNES) tied physics to frame rate
- 60 Hz became the standard

**Modern reasons:**
- Good balance between responsiveness and performance
- Network tick rates often match (CS:GO = 64 Hz, Valorant = 128 Hz)
- Higher rates (120 Hz) waste CPU for minimal physics benefit
- Lower rates (30 Hz) feel laggy

**Can you use 120 Hz?** Yes! Change `FIXED_TIMESTEP = 1.0 / 120.0`. But:
- Uses 2x CPU for physics
- Harder to network (more packets)
- Diminishing returns (players can't perceive difference beyond 60-120 Hz)

#### The Interpolation Alpha Trick

**Problem:** Physics updates at 60 Hz, rendering at 144 Hz. Without interpolation:

```
Frame 1: Render position = (0, 0)
Frame 2: Render position = (0, 0)  ← Same! No physics update yet
Frame 3: Render position = (0, 0)  ← Still same!
Frame 4: Render position = (10, 0) ← Sudden jump! Physics updated
```

**Result:** Jittery motion (3 frames of stillness, 1 frame of jump).

**Solution:** Interpolate between previous and current physics state:

```java
// Store two positions
Vector3f previousPos = new Vector3f(0, 0, 0);
Vector3f currentPos = new Vector3f(10, 0, 0);

// Interpolate based on accumulator
float alpha = time.getAlpha(); // 0.0 to 1.0
Vector3f renderPos = previousPos.lerp(currentPos, alpha);

// Result: Smooth motion across all frames
Frame 1: alpha=0.0  → renderPos = (0, 0)   ← Start
Frame 2: alpha=0.33 → renderPos = (3.3, 0) ← 1/3 of the way
Frame 3: alpha=0.66 → renderPos = (6.6, 0) ← 2/3 of the way
Frame 4: alpha=0.0  → renderPos = (10, 0)  ← Physics updated, reset alpha
```

**Professional engines using this:**
- Source Engine (Valve's Half-Life 2, Portal)
- Unity (automatically interpolates Rigidbody positions)
- Unreal (FInterpTo functions)

---

### Step 2: Window Abstraction

Create `src/main/java/com/yourname/engine/core/Window.java`:

```java
package com.yourname.engine.core;

import org.lwjgl.glfw.*;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * GLFW window wrapper with Vulkan support.
 *
 * <p>GLFW (Graphics Library Framework) handles:
 * - Cross-platform window creation (Windows, macOS, Linux)
 * - Input events (keyboard, mouse, gamepad)
 * - Vulkan surface creation
 * - Monitor queries (resolution, refresh rate)
 *
 * <h2>Why GLFW over SDL or native APIs?</h2>
 *
 * <p><b>GLFW vs SDL:</b>
 * <ul>
 *   <li>GLFW: Lightweight (50 KB), Vulkan-first design, better Java bindings</li>
 *   <li>SDL: Feature-rich (audio, networking), larger (500 KB), OpenGL-centric</li>
 * </ul>
 *
 * <p><b>GLFW vs Native (Win32, Cocoa, X11):</b>
 * <ul>
 *   <li>Native: Maximum control, OS-specific features</li>
 *   <li>GLFW: Cross-platform, simple API, community-tested</li>
 * </ul>
 *
 * <p>We chose GLFW for simplicity and Vulkan compatibility.
 */
public class Window {
    private long handle;        // GLFW window handle (C pointer)
    private String title;
    private int width;
    private int height;
    private boolean resized;    // Flag for swap chain recreation

    // Callbacks (must be stored to prevent GC)
    private GLFWErrorCallback errorCallback;
    private GLFWWindowSizeCallback sizeCallback;
    private GLFWKeyCallback keyCallback;

    public Window(String title, int width, int height) {
        this.title = title;
        this.width = width;
        this.height = height;
        this.resized = false;

        init();
    }

    /**
     * Initialize GLFW and create window.
     *
     * <h2>GLFW Initialization Steps</h2>
     * <ol>
     *   <li>Set error callback (redirect errors to System.err)</li>
     *   <li>Initialize GLFW library (loads native DLL/SO)</li>
     *   <li>Configure window hints (no OpenGL, hidden initially)</li>
     *   <li>Create window</li>
     *   <li>Setup callbacks (resize, input)</li>
     *   <li>Center window on primary monitor</li>
     * </ol>
     */
    private void init() {
        // Set error callback BEFORE glfwInit()
        // This captures initialization errors too
        errorCallback = GLFWErrorCallback.createPrint(System.err).set();

        // Initialize GLFW
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        // Configure window hints
        glfwDefaultWindowHints(); // Reset to defaults first

        // CRITICAL: Tell GLFW we're using Vulkan, not OpenGL
        // Without this, GLFW creates an OpenGL context (wrong!)
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);

        // Start hidden (show after Vulkan initialization succeeds)
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);

        // Allow resizing (triggers swap chain recreation)
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        // Create window
        handle = glfwCreateWindow(width, height, title, NULL, NULL);
        if (handle == NULL) {
            throw new RuntimeException("Failed to create GLFW window");
        }

        // Setup resize callback
        // Called when user drags window edge or maximizes
        sizeCallback = GLFWWindowSizeCallback.create((window, w, h) -> {
            this.width = w;
            this.height = h;
            this.resized = true; // Signal to recreate swap chain
        });
        glfwSetWindowSizeCallback(handle, sizeCallback);

        // Setup key callback (ESC to close)
        keyCallback = GLFWKeyCallback.create((window, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
                glfwSetWindowShouldClose(window, true);
            }
        });
        glfwSetKeyCallback(handle, keyCallback);

        // Center window on primary monitor
        GLFWVidMode vidMode = glfwGetVideoMode(glfwGetPrimaryMonitor());
        if (vidMode != null) {
            glfwSetWindowPos(handle,
                (vidMode.width() - width) / 2,
                (vidMode.height() - height) / 2
            );
        }
    }

    /**
     * Show the window (call after Vulkan initialization succeeds).
     *
     * <p>We keep the window hidden during setup to avoid showing
     * a black/blank window if initialization fails.
     */
    public void show() {
        glfwShowWindow(handle);
    }

    /**
     * Poll input events (keyboard, mouse, window events).
     *
     * <p>Call this once per frame at the start of the loop.
     * GLFW batches events and delivers them here.
     *
     * <h2>Event Processing Flow</h2>
     * <pre>
     * OS: [User presses key]
     *   ↓
     * GLFW: Queues event in internal buffer
     *   ↓
     * glfwPollEvents(): Processes queue, calls our callbacks
     *   ↓
     * Our keyCallback: Handles the key press
     * </pre>
     */
    public void pollEvents() {
        glfwPollEvents();
    }

    /**
     * Check if window should close (user clicked X or pressed ESC).
     */
    public boolean shouldClose() {
        return glfwWindowShouldClose(handle);
    }

    /**
     * Check if window was resized since last frame.
     * Automatically resets the flag to false.
     *
     * <p>Use this to recreate the swap chain:
     * <pre>
     * if (window.wasResized()) {
     *     vulkanContext.recreateSwapChain();
     * }
     * </pre>
     */
    public boolean wasResized() {
        boolean result = resized;
        resized = false; // Reset flag
        return result;
    }

    /**
     * Cleanup window and GLFW resources.
     *
     * <p>CRITICAL: Callbacks must be freed explicitly!
     * LWJGL allocates native memory for callback trampolines.
     * If you don't free them, you leak native memory.
     *
     * <h2>Cleanup Order</h2>
     * <ol>
     *   <li>Free callbacks (native memory)</li>
     *   <li>Destroy window (releases OS resources)</li>
     *   <li>Terminate GLFW (unloads library)</li>
     * </ol>
     */
    public void destroy() {
        // Free callbacks (prevent native memory leak)
        if (keyCallback != null) keyCallback.free();
        if (sizeCallback != null) sizeCallback.free();
        if (errorCallback != null) errorCallback.free();

        // Destroy window
        if (handle != NULL) {
            glfwDestroyWindow(handle);
            handle = NULL;
        }

        // Terminate GLFW
        glfwTerminate();
    }

    // Getters
    public long getHandle() { return handle; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public String getTitle() { return title; }
}
```

**Why store callbacks as instance variables?**

**Incorrect (memory leak):**
```java
// BAD: Callback gets garbage collected!
glfwSetKeyCallback(handle, (window, key, scancode, action, mods) -> {
    // Handle key
});
// Lambda is now eligible for GC
// Later: Native code tries to call callback → CRASH!
```

**Correct:**
```java
// GOOD: Store callback to prevent GC
private GLFWKeyCallback keyCallback;

keyCallback = GLFWKeyCallback.create((window, key, ...) -> {
    // Handle key
});
glfwSetKeyCallback(handle, keyCallback);

// In cleanup:
keyCallback.free(); // Free native memory
```

**Why it matters:**
- GLFW stores a **native pointer** to the callback
- If Java GC collects the callback object, the pointer becomes invalid
- Next time GLFW calls it → segmentation fault (JVM crash)

---

### Step 3: Complete Vulkan Context

Now for the big one! Create `src/main/java/com/yourname/engine/renderer/VulkanContext.java` with **FULL IMPLEMENTATION**:

```java
package com.yourname.engine.renderer;

import com.yourname.engine.core.Window;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.*;

import static org.lwjgl.vulkan.EXTDebugUtils.*;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * Complete Vulkan context with instance, device, swap chain, and rendering.
 *
 * <p>This is a FULL implementation - no stubs!
 *
 * <h2>Vulkan Initialization Pipeline</h2>
 * <pre>
 * 1. createInstance()       → VkInstance (global Vulkan context)
 * 2. setupDebugMessenger()  → Validation layer callbacks
 * 3. createSurface()        → VkSurfaceKHR (window integration)
 * 4. pickPhysicalDevice()   → VkPhysicalDevice (select GPU)
 * 5. createLogicalDevice()  → VkDevice (interface to GPU)
 * 6. createSwapChain()      → VkSwapchainKHR (framebuffers)
 * 7. createCommandPool()    → VkCommandPool (command buffer allocator)
 * 8. createCommandBuffers() → VkCommandBuffer[] (pre-allocated)
 * 9. createSyncObjects()    → Semaphores and fences
 * </pre>
 *
 * <h2>Frame Rendering Pipeline</h2>
 * <pre>
 * renderFrame():
 *   1. vkWaitForFences()           → Wait for previous frame to finish
 *   2. vkAcquireNextImageKHR()     → Get next swapchain image
 *   3. recordCommandBuffer()       → Record GPU commands (clear screen)
 *   4. vkQueueSubmit()             → Submit commands to GPU
 *   5. vkQueuePresentKHR()         → Present image to screen
 * </pre>
 */
public class VulkanContext {
    // Enable validation layers in debug mode
    // Adds ~20% CPU overhead but catches errors early
    private static final boolean ENABLE_VALIDATION_LAYERS = true;

    // Khronos validation layer (ships with Vulkan SDK)
    private static final Set<String> VALIDATION_LAYERS = Set.of("VK_LAYER_KHRONOS_validation");

    // Required device extensions (swap chain for presenting to window)
    private static final Set<String> DEVICE_EXTENSIONS = Set.of(VK_KHR_SWAPCHAIN_EXTENSION_NAME);

    // Double buffering (2 frames in flight)
    // Frame 1 renders while Frame 0 displays
    private static final int MAX_FRAMES_IN_FLIGHT = 2;

    private Window window;

    // Vulkan objects
    private VkInstance instance;
    private long debugMessenger;
    private VkPhysicalDevice physicalDevice;
    private VkDevice device;
    private long surface;

    // Queues
    private VkQueue graphicsQueue;
    private VkQueue presentQueue;
    private int graphicsFamily = -1;
    private int presentFamily = -1;

    // Swap chain
    private long swapChain;
    private List<Long> swapChainImages;
    private int swapChainImageFormat;
    private VkExtent2D swapChainExtent;

    // Command buffers
    private long commandPool;
    private List<VkCommandBuffer> commandBuffers;

    // Synchronization
    private List<Long> imageAvailableSemaphores;
    private List<Long> renderFinishedSemaphores;
    private List<Long> inFlightFences;
    private int currentFrame = 0;

    public VulkanContext(Window window) {
        this.window = window;
    }

    /**
     * Initialize Vulkan: instance → device → swap chain → command buffers.
     */
    public void init() {
        createInstance();
        setupDebugMessenger();
        createSurface();
        pickPhysicalDevice();
        createLogicalDevice();
        createSwapChain();
        createCommandPool();
        createCommandBuffers();
        createSyncObjects();

        System.out.println("✓ Vulkan initialized successfully");
    }

    // ========== INSTANCE CREATION ==========

    /**
     * Create Vulkan instance (global context).
     *
     * <p>The instance:
     * - Loads the Vulkan loader (vulkan-1.dll on Windows)
     * - Enables extensions (debug utils, surface)
     * - Activates validation layers (error checking)
     *
     * <h2>Why Extensions?</h2>
     * <p>Vulkan core is minimal. Extensions add functionality:
     * <ul>
     *   <li>VK_KHR_surface: Window integration</li>
     *   <li>VK_KHR_win32_surface: Windows-specific (GLFW auto-detects)</li>
     *   <li>VK_EXT_debug_utils: Validation callbacks</li>
     * </ul>
     *
     * <p>Without extensions, Vulkan can only do offscreen rendering!
     */
    private void createInstance() {
        try (MemoryStack stack = stackPush()) {
            // Application info (metadata for drivers)
            // Some drivers optimize based on application name (e.g., "Unreal Engine")
            VkApplicationInfo appInfo = VkApplicationInfo.calloc(stack);
            appInfo.sType(VK_STRUCTURE_TYPE_APPLICATION_INFO);
            appInfo.pApplicationName(stack.UTF8Safe("JECS Engine"));
            appInfo.applicationVersion(VK_MAKE_VERSION(1, 0, 0));
            appInfo.pEngineName(stack.UTF8Safe("JECS"));
            appInfo.engineVersion(VK_MAKE_VERSION(1, 0, 0));
            appInfo.apiVersion(VK_API_VERSION_1_0);

            // Instance create info
            VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.calloc(stack);
            createInfo.sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO);
            createInfo.pApplicationInfo(appInfo);

            // Extensions (platform-specific, auto-detected by GLFW)
            PointerBuffer requiredExtensions = getRequiredExtensions(stack);
            createInfo.ppEnabledExtensionNames(requiredExtensions);

            // Validation layers (debug mode only)
            if (ENABLE_VALIDATION_LAYERS) {
                if (!checkValidationLayerSupport()) {
                    throw new RuntimeException("Validation layers requested but not available");
                }
                createInfo.ppEnabledLayerNames(asPointerBuffer(stack, VALIDATION_LAYERS));

                // Debug messenger for instance creation/destruction
                // (vkCreateInstance and vkDestroyInstance aren't covered by the main messenger)
                VkDebugUtilsMessengerCreateInfoEXT debugCreateInfo = VkDebugUtilsMessengerCreateInfoEXT.calloc(stack);
                populateDebugMessengerCreateInfo(debugCreateInfo);
                createInfo.pNext(debugCreateInfo.address());
            }

            // Create instance
            PointerBuffer pInstance = stack.mallocPointer(1);
            if (vkCreateInstance(createInfo, null, pInstance) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create Vulkan instance");
            }

            instance = new VkInstance(pInstance.get(0), createInfo);
            System.out.println("  → Vulkan instance created");
        }
    }

    /**
     * Get required Vulkan extensions from GLFW.
     *
     * <p>GLFW knows which extensions are needed for the current platform:
     * - Windows: VK_KHR_surface, VK_KHR_win32_surface
     * - Linux: VK_KHR_surface, VK_KHR_xlib_surface (or wayland)
     * - macOS: VK_KHR_surface, VK_MVK_macos_surface
     */
    private PointerBuffer getRequiredExtensions(MemoryStack stack) {
        PointerBuffer glfwExtensions = GLFWVulkan.glfwGetRequiredInstanceExtensions();
        if (glfwExtensions == null) {
            throw new RuntimeException("Failed to find GLFW required extensions");
        }

        // Add debug extension if validation enabled
        if (ENABLE_VALIDATION_LAYERS) {
            PointerBuffer extensions = stack.mallocPointer(glfwExtensions.capacity() + 1);
            extensions.put(glfwExtensions);
            extensions.put(stack.UTF8(VK_EXT_DEBUG_UTILS_EXTENSION_NAME));
            return extensions.rewind();
        }

        return glfwExtensions;
    }

    /**
     * Check if validation layers are available.
     *
     * <p>Validation layers are installed with the Vulkan SDK.
     * If missing, this method returns false.
     */
    private boolean checkValidationLayerSupport() {
        try (MemoryStack stack = stackPush()) {
            IntBuffer layerCount = stack.ints(0);
            vkEnumerateInstanceLayerProperties(layerCount, null);

            VkLayerProperties.Buffer availableLayers = VkLayerProperties.malloc(layerCount.get(0), stack);
            vkEnumerateInstanceLayerProperties(layerCount, availableLayers);

            Set<String> availableLayerNames = new HashSet<>();
            for (int i = 0; i < availableLayers.capacity(); i++) {
                availableLayerNames.add(availableLayers.get(i).layerNameString());
            }

            return availableLayerNames.containsAll(VALIDATION_LAYERS);
        }
    }

    // ========== DEBUG MESSENGER ==========

    /**
     * Setup debug messenger for validation layer output.
     *
     * <p>Without this, validation errors are printed to stdout (easy to miss).
     * With this, errors are formatted and sent to System.err.
     */
    private void setupDebugMessenger() {
        if (!ENABLE_VALIDATION_LAYERS) return;

        try (MemoryStack stack = stackPush()) {
            VkDebugUtilsMessengerCreateInfoEXT createInfo = VkDebugUtilsMessengerCreateInfoEXT.calloc(stack);
            populateDebugMessengerCreateInfo(createInfo);

            LongBuffer pDebugMessenger = stack.longs(VK_NULL_HANDLE);
            if (vkCreateDebugUtilsMessengerEXT(instance, createInfo, null, pDebugMessenger) != VK_SUCCESS) {
                throw new RuntimeException("Failed to set up debug messenger");
            }

            debugMessenger = pDebugMessenger.get(0);
            System.out.println("  → Debug messenger created");
        }
    }

    /**
     * Configure debug messenger callbacks.
     *
     * <p>Message severity:
     * - VERBOSE: Diagnostic info (very noisy)
     * - INFO: Informational messages
     * - WARNING: Potential issues (use deprecated features)
     * - ERROR: Invalid usage (bugs!)
     *
     * <p>We filter to WARNING and ERROR only.
     */
    private void populateDebugMessengerCreateInfo(VkDebugUtilsMessengerCreateInfoEXT createInfo) {
        createInfo.sType(VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT);

        // Filter to warnings and errors only
        createInfo.messageSeverity(VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT |
                                   VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT);

        // All message types
        createInfo.messageType(VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT |
                              VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT |
                              VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT);

        // Callback function
        createInfo.pfnUserCallback((messageSeverity, messageType, pCallbackData, pUserData) -> {
            VkDebugUtilsMessengerCallbackDataEXT callbackData = VkDebugUtilsMessengerCallbackDataEXT.create(pCallbackData);
            System.err.println("Validation layer: " + callbackData.pMessageString());
            return VK_FALSE; // Always return false (true aborts the call)
        });
    }

    // ========== SURFACE CREATION ==========

    /**
     * Create window surface (connects Vulkan to window system).
     *
     * <p>The surface is platform-specific:
     * - Windows: Creates a Win32 surface
     * - Linux: Creates X11 or Wayland surface
     * - macOS: Creates MoltenVK surface
     *
     * <p>GLFW handles all platform differences for us.
     */
    private void createSurface() {
        try (MemoryStack stack = stackPush()) {
            LongBuffer pSurface = stack.longs(VK_NULL_HANDLE);
            if (GLFWVulkan.glfwCreateWindowSurface(instance, window.getHandle(), null, pSurface) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create window surface");
            }
            surface = pSurface.get(0);
            System.out.println("  → Window surface created");
        }
    }

    // ========== PHYSICAL DEVICE SELECTION ==========

    /**
     * Select a GPU (physical device).
     *
     * <p>We pick the first suitable GPU. A production engine would:
     * 1. Score GPUs (discrete > integrated, VRAM amount, feature support)
     * 2. Let user choose from a list
     * 3. Save preference to config file
     */
    private void pickPhysicalDevice() {
        try (MemoryStack stack = stackPush()) {
            IntBuffer deviceCount = stack.ints(0);
            vkEnumeratePhysicalDevices(instance, deviceCount, null);

            if (deviceCount.get(0) == 0) {
                throw new RuntimeException("Failed to find GPUs with Vulkan support");
            }

            PointerBuffer ppPhysicalDevices = stack.mallocPointer(deviceCount.get(0));
            vkEnumeratePhysicalDevices(instance, deviceCount, ppPhysicalDevices);

            // Pick first suitable device
            for (int i = 0; i < ppPhysicalDevices.capacity(); i++) {
                VkPhysicalDevice device = new VkPhysicalDevice(ppPhysicalDevices.get(i), instance);
                if (isDeviceSuitable(device)) {
                    physicalDevice = device;

                    VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.malloc(stack);
                    vkGetPhysicalDeviceProperties(device, properties);
                    System.out.println("  → Selected GPU: " + properties.deviceNameString());
                    return;
                }
            }

            throw new RuntimeException("Failed to find a suitable GPU");
        }
    }

    /**
     * Check if a GPU is suitable for our needs.
     *
     * <p>Requirements:
     * 1. Has graphics and present queue families
     * 2. Supports swapchain extension
     * 3. Swapchain has at least one format and present mode
     */
    private boolean isDeviceSuitable(VkPhysicalDevice device) {
        QueueFamilyIndices indices = findQueueFamilies(device);
        boolean extensionsSupported = checkDeviceExtensionSupport(device);
        boolean swapChainAdequate = false;

        if (extensionsSupported) {
            SwapChainSupportDetails swapChainSupport = querySwapChainSupport(device);
            swapChainAdequate = !swapChainSupport.formats.isEmpty() &&
                               !swapChainSupport.presentModes.isEmpty();
        }

        return indices.isComplete() && extensionsSupported && swapChainAdequate;
    }

    /**
     * Find queue families that support graphics and present.
     *
     * <h2>Queue Families Explained</h2>
     * <p>GPUs have multiple queues organized into families:
     * - Graphics family: Rendering commands (draw, clear, blit)
     * - Compute family: Compute shaders (particle systems, physics)
     * - Transfer family: Memory operations (texture uploads)
     *
     * <p>Some families overlap (graphics family can do transfer).
     * We need:
     * 1. A family that supports graphics operations
     * 2. A family that can present to our surface
     *
     * <p>Usually they're the same family (index 0).
     */
    private QueueFamilyIndices findQueueFamilies(VkPhysicalDevice device) {
        QueueFamilyIndices indices = new QueueFamilyIndices();

        try (MemoryStack stack = stackPush()) {
            IntBuffer queueFamilyCount = stack.ints(0);
            vkGetPhysicalDeviceQueueFamilyProperties(device, queueFamilyCount, null);

            VkQueueFamilyProperties.Buffer queueFamilies =
                VkQueueFamilyProperties.malloc(queueFamilyCount.get(0), stack);
            vkGetPhysicalDeviceQueueFamilyProperties(device, queueFamilyCount, queueFamilies);

            IntBuffer presentSupport = stack.ints(VK_FALSE);

            for (int i = 0; i < queueFamilies.capacity(); i++) {
                // Check for graphics support
                if ((queueFamilies.get(i).queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0) {
                    indices.graphicsFamily = i;
                }

                // Check for present support
                vkGetPhysicalDeviceSurfaceSupportKHR(device, i, surface, presentSupport);
                if (presentSupport.get(0) == VK_TRUE) {
                    indices.presentFamily = i;
                }

                // Early exit if we found both
                if (indices.isComplete()) {
                    break;
                }
            }
        }

        return indices;
    }

    /**
     * Check if device supports required extensions (swapchain).
     */
    private boolean checkDeviceExtensionSupport(VkPhysicalDevice device) {
        try (MemoryStack stack = stackPush()) {
            IntBuffer extensionCount = stack.ints(0);
            vkEnumerateDeviceExtensionProperties(device, (String) null, extensionCount, null);

            VkExtensionProperties.Buffer availableExtensions =
                VkExtensionProperties.malloc(extensionCount.get(0), stack);
            vkEnumerateDeviceExtensionProperties(device, (String) null, extensionCount,
                availableExtensions);

            Set<String> requiredExtensions = new HashSet<>(DEVICE_EXTENSIONS);
            for (int i = 0; i < availableExtensions.capacity(); i++) {
                requiredExtensions.remove(availableExtensions.get(i).extensionNameString());
            }

            return requiredExtensions.isEmpty();
        }
    }

    // ========== LOGICAL DEVICE CREATION ==========

    /**
     * Create logical device (interface to GPU).
     *
     * <p>The logical device:
     * - Creates queues for command submission
     * - Enables device extensions (swapchain)
     * - Enables features (tessellation, geometry shaders, etc.)
     */
    private void createLogicalDevice() {
        try (MemoryStack stack = stackPush()) {
            QueueFamilyIndices indices = findQueueFamilies(physicalDevice);
            this.graphicsFamily = indices.graphicsFamily;
            this.presentFamily = indices.presentFamily;

            // Create queue infos (may be same family)
            Set<Integer> uniqueQueueFamilies = new HashSet<>(
                Arrays.asList(indices.graphicsFamily, indices.presentFamily)
            );

            VkDeviceQueueCreateInfo.Buffer queueCreateInfos =
                VkDeviceQueueCreateInfo.calloc(uniqueQueueFamilies.size(), stack);

            float[] queuePriority = {1.0f}; // Priority (0.0 to 1.0)
            int i = 0;
            for (Integer queueFamily : uniqueQueueFamilies) {
                VkDeviceQueueCreateInfo queueCreateInfo = queueCreateInfos.get(i++);
                queueCreateInfo.sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO);
                queueCreateInfo.queueFamilyIndex(queueFamily);
                queueCreateInfo.pQueuePriorities(stack.floats(queuePriority));
            }

            // Device features (none for now, add later for tessellation, etc.)
            VkPhysicalDeviceFeatures deviceFeatures = VkPhysicalDeviceFeatures.calloc(stack);

            // Device create info
            VkDeviceCreateInfo createInfo = VkDeviceCreateInfo.calloc(stack);
            createInfo.sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO);
            createInfo.pQueueCreateInfos(queueCreateInfos);
            createInfo.pEnabledFeatures(deviceFeatures);
            createInfo.ppEnabledExtensionNames(asPointerBuffer(stack, DEVICE_EXTENSIONS));

            // Enable validation layers for device (deprecated but harmless)
            if (ENABLE_VALIDATION_LAYERS) {
                createInfo.ppEnabledLayerNames(asPointerBuffer(stack, VALIDATION_LAYERS));
            }

            // Create device
            PointerBuffer pDevice = stack.pointers(VK_NULL_HANDLE);
            if (vkCreateDevice(physicalDevice, createInfo, null, pDevice) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create logical device");
            }

            device = new VkDevice(pDevice.get(0), physicalDevice, createInfo);

            // Get queue handles
            PointerBuffer pQueue = stack.pointers(VK_NULL_HANDLE);
            vkGetDeviceQueue(device, indices.graphicsFamily, 0, pQueue);
            graphicsQueue = new VkQueue(pQueue.get(0), device);

            vkGetDeviceQueue(device, indices.presentFamily, 0, pQueue);
            presentQueue = new VkQueue(pQueue.get(0), device);

            System.out.println("  → Logical device created");
        }
    }

    // ========== SWAP CHAIN CREATION ==========

    /**
     * Create swap chain (framebuffers for presenting to window).
     *
     * <p>Swap chain configuration:
     * - Format: B8G8R8A8_SRGB (8-bit RGBA with sRGB color space)
     * - Present mode: MAILBOX (triple buffering, low latency)
     * - Extent: Window size (or closest supported size)
     * - Image count: minImageCount + 1 (for triple buffering)
     */
    private void createSwapChain() {
        try (MemoryStack stack = stackPush()) {
            SwapChainSupportDetails swapChainSupport = querySwapChainSupport(physicalDevice);

            VkSurfaceFormatKHR surfaceFormat = chooseSwapSurfaceFormat(swapChainSupport.formats);
            int presentMode = chooseSwapPresentMode(swapChainSupport.presentModes);
            VkExtent2D extent = chooseSwapExtent(swapChainSupport.capabilities);

            // Image count (request one more than minimum for triple buffering)
            IntBuffer imageCount = stack.ints(swapChainSupport.capabilities.minImageCount() + 1);
            if (swapChainSupport.capabilities.maxImageCount() > 0 &&
                imageCount.get(0) > swapChainSupport.capabilities.maxImageCount()) {
                imageCount.put(0, swapChainSupport.capabilities.maxImageCount());
            }

            // Create info
            VkSwapchainCreateInfoKHR createInfo = VkSwapchainCreateInfoKHR.calloc(stack);
            createInfo.sType(VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR);
            createInfo.surface(surface);
            createInfo.minImageCount(imageCount.get(0));
            createInfo.imageFormat(surfaceFormat.format());
            createInfo.imageColorSpace(surfaceFormat.colorSpace());
            createInfo.imageExtent(extent);
            createInfo.imageArrayLayers(1); // 1 for non-VR, 2 for stereo

            // TRANSFER_DST_BIT allows vkCmdClearColorImage
            createInfo.imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT);

            // If graphics and present families differ, share images between them
            if (graphicsFamily != presentFamily) {
                createInfo.imageSharingMode(VK_SHARING_MODE_CONCURRENT);
                createInfo.pQueueFamilyIndices(stack.ints(graphicsFamily, presentFamily));
            } else {
                createInfo.imageSharingMode(VK_SHARING_MODE_EXCLUSIVE);
            }

            createInfo.preTransform(swapChainSupport.capabilities.currentTransform());
            createInfo.compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR); // No blending with desktop
            createInfo.presentMode(presentMode);
            createInfo.clipped(true); // Don't care about obscured pixels
            createInfo.oldSwapchain(VK_NULL_HANDLE); // For recreation (next chapter)

            // Create swap chain
            LongBuffer pSwapChain = stack.longs(VK_NULL_HANDLE);
            if (vkCreateSwapchainKHR(device, createInfo, null, pSwapChain) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create swap chain");
            }

            swapChain = pSwapChain.get(0);

            // Get swap chain images
            vkGetSwapchainImagesKHR(device, swapChain, imageCount, null);
            LongBuffer pSwapchainImages = stack.mallocLong(imageCount.get(0));
            vkGetSwapchainImagesKHR(device, swapChain, imageCount, pSwapchainImages);

            swapChainImages = new ArrayList<>(imageCount.get(0));
            for (int i = 0; i < pSwapchainImages.capacity(); i++) {
                swapChainImages.add(pSwapchainImages.get(i));
            }

            swapChainImageFormat = surfaceFormat.format();
            swapChainExtent = VkExtent2D.create().set(extent);

            System.out.println("  → Swap chain created (" + swapChainImages.size() + " images)");
        }
    }

    /**
     * Query swap chain support (formats, present modes, capabilities).
     */
    private SwapChainSupportDetails querySwapChainSupport(VkPhysicalDevice device) {
        try (MemoryStack stack = stackPush()) {
            SwapChainSupportDetails details = new SwapChainSupportDetails();

            // Capabilities
            details.capabilities = VkSurfaceCapabilitiesKHR.malloc(stack);
            vkGetPhysicalDeviceSurfaceCapabilitiesKHR(device, surface, details.capabilities);

            // Formats
            IntBuffer count = stack.ints(0);
            vkGetPhysicalDeviceSurfaceFormatsKHR(device, surface, count, null);
            if (count.get(0) != 0) {
                details.formats = new ArrayList<>(count.get(0));
                VkSurfaceFormatKHR.Buffer formats = VkSurfaceFormatKHR.malloc(count.get(0), stack);
                vkGetPhysicalDeviceSurfaceFormatsKHR(device, surface, count, formats);
                for (int i = 0; i < formats.capacity(); i++) {
                    details.formats.add(new VkSurfaceFormatKHR(formats.get(i)));
                }
            }

            // Present modes
            vkGetPhysicalDeviceSurfacePresentModesKHR(device, surface, count, null);
            if (count.get(0) != 0) {
                details.presentModes = new ArrayList<>(count.get(0));
                IntBuffer presentModes = stack.mallocInt(count.get(0));
                vkGetPhysicalDeviceSurfacePresentModesKHR(device, surface, count, presentModes);
                for (int i = 0; i < presentModes.capacity(); i++) {
                    details.presentModes.add(presentModes.get(i));
                }
            }

            return details;
        }
    }

    /**
     * Choose surface format (prefer B8G8R8A8_SRGB for color accuracy).
     *
     * <h2>Why sRGB?</h2>
     * <p>Monitors display in sRGB color space. If we render in linear RGB:
     * <pre>
     * Linear RGB (0.5, 0.5, 0.5) → Monitor displays as (0.73, 0.73, 0.73)
     * Result: Washed-out colors!
     * </pre>
     *
     * <p>With sRGB format, Vulkan auto-converts:
     * <pre>
     * Shader outputs linear (0.5, 0.5, 0.5)
     * → Vulkan converts to sRGB (0.73, 0.73, 0.73)
     * → Monitor displays correctly
     * </pre>
     */
    private VkSurfaceFormatKHR chooseSwapSurfaceFormat(List<VkSurfaceFormatKHR> availableFormats) {
        return availableFormats.stream()
            .filter(format -> format.format() == VK_FORMAT_B8G8R8A8_SRGB &&
                            format.colorSpace() == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR)
            .findFirst()
            .orElse(availableFormats.get(0)); // Fallback to first available
    }

    /**
     * Choose present mode (prefer MAILBOX for low latency, fallback to FIFO).
     *
     * <p>Present modes:
     * - IMMEDIATE: No vsync, tearing (for benchmarks)
     * - FIFO: Vsync, guaranteed available (double buffering)
     * - MAILBOX: Low latency vsync (triple buffering)
     * - FIFO_RELAXED: Vsync, but allows tearing if late
     */
    private int chooseSwapPresentMode(List<Integer> availablePresentModes) {
        return availablePresentModes.stream()
            .filter(mode -> mode == VK_PRESENT_MODE_MAILBOX_KHR)
            .findFirst()
            .orElse(VK_PRESENT_MODE_FIFO_KHR); // FIFO always available
    }

    /**
     * Choose swap extent (window size, clamped to GPU limits).
     */
    private VkExtent2D chooseSwapExtent(VkSurfaceCapabilitiesKHR capabilities) {
        // If currentExtent is not special value, use it
        if (capabilities.currentExtent().width() != 0xFFFFFFFF) {
            return capabilities.currentExtent();
        }

        // Otherwise, clamp window size to min/max
        VkExtent2D actualExtent = VkExtent2D.malloc().set(window.getWidth(), window.getHeight());

        VkExtent2D minExtent = capabilities.minImageExtent();
        VkExtent2D maxExtent = capabilities.maxImageExtent();

        actualExtent.width(Math.max(minExtent.width(),
            Math.min(maxExtent.width(), actualExtent.width())));
        actualExtent.height(Math.max(minExtent.height(),
            Math.min(maxExtent.height(), actualExtent.height())));

        return actualExtent;
    }

    // ========== COMMAND POOL AND BUFFERS ==========

    /**
     * Create command pool (allocator for command buffers).
     *
     * <p>Command pools:
     * - Allocate memory for command buffers
     * - Reset all buffers in the pool efficiently
     * - Thread-specific (one pool per thread for multi-threaded recording)
     *
     * <p>Flags:
     * - RESET_COMMAND_BUFFER_BIT: Allow individual buffer reset (we use this)
     * - TRANSIENT_BIT: Buffers are short-lived (rerecorded often)
     */
    private void createCommandPool() {
        try (MemoryStack stack = stackPush()) {
            VkCommandPoolCreateInfo poolInfo = VkCommandPoolCreateInfo.calloc(stack);
            poolInfo.sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO);
            poolInfo.flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);
            poolInfo.queueFamilyIndex(graphicsFamily);

            LongBuffer pCommandPool = stack.mallocLong(1);
            if (vkCreateCommandPool(device, poolInfo, null, pCommandPool) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create command pool");
            }

            commandPool = pCommandPool.get(0);
            System.out.println("  → Command pool created");
        }
    }

    /**
     * Allocate command buffers (one per frame in flight).
     *
     * <p>We allocate 2 buffers (MAX_FRAMES_IN_FLIGHT) for double buffering:
     * - Frame 0 uses commandBuffers[0]
     * - Frame 1 uses commandBuffers[1]
     * - Frame 2 uses commandBuffers[0] again (cyclic)
     */
    private void createCommandBuffers() {
        commandBuffers = new ArrayList<>(MAX_FRAMES_IN_FLIGHT);

        try (MemoryStack stack = stackPush()) {
            VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc(stack);
            allocInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO);
            allocInfo.commandPool(commandPool);
            allocInfo.level(VK_COMMAND_BUFFER_LEVEL_PRIMARY); // Primary = submitted directly
            allocInfo.commandBufferCount(MAX_FRAMES_IN_FLIGHT);

            PointerBuffer pCommandBuffers = stack.mallocPointer(MAX_FRAMES_IN_FLIGHT);
            if (vkAllocateCommandBuffers(device, allocInfo, pCommandBuffers) != VK_SUCCESS) {
                throw new RuntimeException("Failed to allocate command buffers");
            }

            for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
                commandBuffers.add(new VkCommandBuffer(pCommandBuffers.get(i), device));
            }

            System.out.println("  → Command buffers allocated (" + MAX_FRAMES_IN_FLIGHT + ")");
        }
    }

    // ========== SYNCHRONIZATION OBJECTS ==========

    /**
     * Create synchronization objects (semaphores and fences).
     *
     * <h2>Synchronization Strategy</h2>
     * <p>For each frame in flight, we need:
     * - imageAvailableSemaphore: GPU signals when swap chain image is ready
     * - renderFinishedSemaphore: GPU signals when rendering completes
     * - inFlightFence: CPU waits for previous frame to finish
     *
     * <p>Flow:
     * <pre>
     * CPU: vkAcquireNextImage → GPU signals imageAvailableSemaphore
     * GPU: Rendering waits on imageAvailableSemaphore
     * GPU: Rendering completes → signals renderFinishedSemaphore
     * GPU: Present waits on renderFinishedSemaphore
     * CPU: vkWaitForFences(inFlightFence) → wait for GPU to finish
     * </pre>
     */
    private void createSyncObjects() {
        imageAvailableSemaphores = new ArrayList<>(MAX_FRAMES_IN_FLIGHT);
        renderFinishedSemaphores = new ArrayList<>(MAX_FRAMES_IN_FLIGHT);
        inFlightFences = new ArrayList<>(MAX_FRAMES_IN_FLIGHT);

        try (MemoryStack stack = stackPush()) {
            VkSemaphoreCreateInfo semaphoreInfo = VkSemaphoreCreateInfo.calloc(stack);
            semaphoreInfo.sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);

            VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.calloc(stack);
            fenceInfo.sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);

            // SIGNALED_BIT: Start in signaled state (so first frame doesn't wait)
            fenceInfo.flags(VK_FENCE_CREATE_SIGNALED_BIT);

            LongBuffer pSemaphore = stack.mallocLong(1);
            LongBuffer pFence = stack.mallocLong(1);

            for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
                // Create two semaphores per frame
                if (vkCreateSemaphore(device, semaphoreInfo, null, pSemaphore) != VK_SUCCESS ||
                    vkCreateSemaphore(device, semaphoreInfo, null, pSemaphore) != VK_SUCCESS ||
                    vkCreateFence(device, fenceInfo, null, pFence) != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create synchronization objects");
                }

                imageAvailableSemaphores.add(pSemaphore.get(0));
                pSemaphore.rewind();
                renderFinishedSemaphores.add(pSemaphore.get(0));
                inFlightFences.add(pFence.get(0));
                pSemaphore.rewind();
                pFence.rewind();
            }

            System.out.println("  → Sync objects created");
        }
    }

    // ========== RENDERING ==========

    /**
     * Render a frame: acquire image → record commands → submit → present.
     *
     * <p>Returns false if swap chain needs recreation (window resized).
     *
     * <h2>Frame Rendering Pipeline</h2>
     * <pre>
     * 1. Wait for previous frame (fence)
     * 2. Acquire next swap chain image (semaphore signaled)
     * 3. Reset and record command buffer
     * 4. Submit commands to GPU (wait on acquire, signal render finished)
     * 5. Present image (wait on render finished)
     * 6. Advance to next frame
     * </pre>
     */
    public boolean renderFrame() {
        try (MemoryStack stack = stackPush()) {
            // Wait for previous frame to finish
            vkWaitForFences(device, inFlightFences.get(currentFrame), true, Long.MAX_VALUE);

            // Acquire next image
            IntBuffer pImageIndex = stack.mallocInt(1);
            int result = vkAcquireNextImageKHR(device, swapChain, Long.MAX_VALUE,
                imageAvailableSemaphores.get(currentFrame), VK_NULL_HANDLE, pImageIndex);

            // Check if swap chain needs recreation
            if (result == VK_ERROR_OUT_OF_DATE_KHR) {
                return false; // Caller should recreate swap chain
            } else if (result != VK_SUCCESS && result != VK_SUBOPTIMAL_KHR) {
                throw new RuntimeException("Failed to acquire swap chain image");
            }

            int imageIndex = pImageIndex.get(0);

            // Reset fence (will be signaled when this frame completes)
            vkResetFences(device, inFlightFences.get(currentFrame));

            // Record command buffer
            recordCommandBuffer(commandBuffers.get(currentFrame), imageIndex);

            // Submit command buffer
            VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack);
            submitInfo.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO);
            submitInfo.waitSemaphoreCount(1);
            submitInfo.pWaitSemaphores(stack.longs(imageAvailableSemaphores.get(currentFrame)));

            // Wait at COLOR_ATTACHMENT_OUTPUT stage (before color write)
            submitInfo.pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT));

            submitInfo.pCommandBuffers(stack.pointers(commandBuffers.get(currentFrame)));
            submitInfo.pSignalSemaphores(stack.longs(renderFinishedSemaphores.get(currentFrame)));

            if (vkQueueSubmit(graphicsQueue, submitInfo, inFlightFences.get(currentFrame)) != VK_SUCCESS) {
                throw new RuntimeException("Failed to submit draw command buffer");
            }

            // Present
            VkPresentInfoKHR presentInfo = VkPresentInfoKHR.calloc(stack);
            presentInfo.sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR);
            presentInfo.pWaitSemaphores(stack.longs(renderFinishedSemaphores.get(currentFrame)));
            presentInfo.swapchainCount(1);
            presentInfo.pSwapchains(stack.longs(swapChain));
            presentInfo.pImageIndices(stack.ints(imageIndex));

            result = vkQueuePresentKHR(presentQueue, presentInfo);

            // Check if swap chain is out of date or suboptimal
            if (result == VK_ERROR_OUT_OF_DATE_KHR || result == VK_SUBOPTIMAL_KHR) {
                return false; // Recreate swap chain
            } else if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to present swap chain image");
            }

            // Advance to next frame
            currentFrame = (currentFrame + 1) % MAX_FRAMES_IN_FLIGHT;
            return true;
        }
    }

    /**
     * Record commands to clear the screen with a cycling rainbow color.
     *
     * <h2>Command Buffer Recording</h2>
     * <p>Steps:
     * 1. Begin recording (ONE_TIME_SUBMIT: buffer is rerecorded every frame)
     * 2. Transition image from UNDEFINED to TRANSFER_DST (for clearing)
     * 3. Clear image with rainbow color (time-based sine wave)
     * 4. Transition image from TRANSFER_DST to PRESENT_SRC (for display)
     * 5. End recording
     *
     * <h2>Image Layout Transitions</h2>
     * <p>Vulkan images have layouts that optimize for specific operations:
     * - UNDEFINED: Initial state, contents don't matter
     * - TRANSFER_DST_OPTIMAL: Optimized for copy/clear operations
     * - PRESENT_SRC_KHR: Optimized for presenting to screen
     *
     * <p>We use pipeline barriers to transition between layouts.
     */
    private void recordCommandBuffer(VkCommandBuffer commandBuffer, int imageIndex) {
        try (MemoryStack stack = stackPush()) {
            // Begin recording
            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack);
            beginInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
            beginInfo.flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);

            vkResetCommandBuffer(commandBuffer, 0);
            if (vkBeginCommandBuffer(commandBuffer, beginInfo) != VK_SUCCESS) {
                throw new RuntimeException("Failed to begin recording command buffer");
            }

            // Transition image to TRANSFER_DST for clearing
            VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
            barrier.sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER);
            barrier.oldLayout(VK_IMAGE_LAYOUT_UNDEFINED); // Don't care about old contents
            barrier.newLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
            barrier.srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);
            barrier.dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);
            barrier.image(swapChainImages.get(imageIndex));
            barrier.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);
            barrier.subresourceRange().baseMipLevel(0);
            barrier.subresourceRange().levelCount(1);
            barrier.subresourceRange().baseArrayLayer(0);
            barrier.subresourceRange().layerCount(1);
            barrier.srcAccessMask(0); // No previous access
            barrier.dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT); // Transfer writes

            vkCmdPipelineBarrier(commandBuffer,
                VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,  // Before everything
                VK_PIPELINE_STAGE_TRANSFER_BIT,     // Transfer stage
                0, null, null, barrier);

            // Clear image with cycling rainbow color
            VkClearColorValue clearColor = VkClearColorValue.calloc(stack);
            float time = System.currentTimeMillis() / 1000.0f;

            // RGB channels cycle at different rates using sine waves
            clearColor.float32(0, (float) Math.abs(Math.sin(time * 0.5)));          // R
            clearColor.float32(1, (float) Math.abs(Math.sin(time * 0.7 + 2.0)));    // G
            clearColor.float32(2, (float) Math.abs(Math.sin(time * 0.9 + 4.0)));    // B
            clearColor.float32(3, 1.0f);                                              // A

            VkImageSubresourceRange range = VkImageSubresourceRange.calloc(stack);
            range.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);
            range.baseMipLevel(0);
            range.levelCount(1);
            range.baseArrayLayer(0);
            range.layerCount(1);

            vkCmdClearColorImage(commandBuffer, swapChainImages.get(imageIndex),
                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, clearColor, range);

            // Transition to PRESENT layout
            barrier.oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
            barrier.newLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
            barrier.srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
            barrier.dstAccessMask(0); // No access after this

            vkCmdPipelineBarrier(commandBuffer,
                VK_PIPELINE_STAGE_TRANSFER_BIT,         // Transfer stage
                VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,   // After everything
                0, null, null, barrier);

            // End recording
            if (vkEndCommandBuffer(commandBuffer) != VK_SUCCESS) {
                throw new RuntimeException("Failed to record command buffer");
            }
        }
    }

    // ========== SWAP CHAIN RECREATION ==========

    /**
     * Recreate swap chain (called on window resize).
     *
     * <p>Steps:
     * 1. Wait for device to finish (can't destroy in-use resources)
     * 2. Cleanup old swap chain
     * 3. Create new swap chain with new size
     * 4. Recreate command buffers (image count may have changed)
     */
    public void recreateSwapChain() {
        vkDeviceWaitIdle(device);

        cleanupSwapChain();

        createSwapChain();
        createCommandBuffers(); // Re-allocate for new image count

        System.out.println("✓ Swap chain recreated");
    }

    /**
     * Cleanup swap chain resources.
     */
    private void cleanupSwapChain() {
        vkDestroySwapchainKHR(device, swapChain, null);
    }

    // ========== CLEANUP ==========

    /**
     * Cleanup all Vulkan resources.
     *
     * <h2>Cleanup Order (CRITICAL!)</h2>
     * <p>Vulkan resources must be destroyed in reverse creation order:
     * 1. Wait for GPU to finish
     * 2. Destroy swap chain
     * 3. Destroy sync objects (semaphores, fences)
     * 4. Destroy command pool (frees command buffers)
     * 5. Destroy device
     * 6. Destroy surface
     * 7. Destroy debug messenger
     * 8. Destroy instance
     *
     * <p>Destroying out of order causes validation errors or crashes!
     */
    public void cleanup() {
        vkDeviceWaitIdle(device);

        cleanupSwapChain();

        // Sync objects
        for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
            vkDestroySemaphore(device, imageAvailableSemaphores.get(i), null);
            vkDestroySemaphore(device, renderFinishedSemaphores.get(i), null);
            vkDestroyFence(device, inFlightFences.get(i), null);
        }

        vkDestroyCommandPool(device, commandPool, null);
        vkDestroyDevice(device, null);
        vkDestroySurfaceKHR(instance, surface, null);

        if (ENABLE_VALIDATION_LAYERS) {
            vkDestroyDebugUtilsMessengerEXT(instance, debugMessenger, null);
        }

        vkDestroyInstance(instance, null);
    }

    // ========== HELPER CLASSES ==========

    private static class QueueFamilyIndices {
        Integer graphicsFamily;
        Integer presentFamily;

        boolean isComplete() {
            return graphicsFamily != null && presentFamily != null;
        }
    }

    private static class SwapChainSupportDetails {
        VkSurfaceCapabilitiesKHR capabilities;
        List<VkSurfaceFormatKHR> formats;
        List<Integer> presentModes;
    }

    // ========== UTILITY METHODS ==========

    /**
     * Convert Java Collection<String> to LWJGL PointerBuffer.
     */
    private static PointerBuffer asPointerBuffer(MemoryStack stack, Collection<String> collection) {
        PointerBuffer buffer = stack.mallocPointer(collection.size());
        collection.stream()
            .map(stack::UTF8)
            .forEach(buffer::put);
        return buffer.rewind();
    }

    // Extensions helper methods (load function pointers dynamically)

    private static int vkCreateDebugUtilsMessengerEXT(VkInstance instance,
                                                      VkDebugUtilsMessengerCreateInfoEXT createInfo,
                                                      VkAllocationCallbacks allocator,
                                                      LongBuffer pDebugMessenger) {
        long address = vkGetInstanceProcAddr(instance, "vkCreateDebugUtilsMessengerEXT");
        if (address == NULL) return VK_ERROR_EXTENSION_NOT_PRESENT;
        return callPPPI(instance.address(), createInfo.address(),
            memAddressSafe(allocator), memAddress(pDebugMessenger), address);
    }

    private static void vkDestroyDebugUtilsMessengerEXT(VkInstance instance,
                                                        long debugMessenger,
                                                        VkAllocationCallbacks allocator) {
        long address = vkGetInstanceProcAddr(instance, "vkDestroyDebugUtilsMessengerEXT");
        if (address != NULL) {
            callPJPV(instance.address(), debugMessenger, memAddressSafe(allocator), address);
        }
    }
}
```

**This is complete, working Vulkan code!** No stubs. ~1140 lines of production-ready initialization and rendering.

---

### Step 4: Engine Class

Create `src/main/java/com/yourname/engine/core/Engine.java`:

```java
package com.yourname.engine.core;

import com.yourname.engine.renderer.VulkanContext;

/**
 * Engine manages core subsystems (renderer, ECS, audio, etc.).
 *
 * <p>Responsibilities:
 * - Initialize subsystems in correct order
 * - Cleanup subsystems in reverse order
 * - Provide access to global systems
 */
public class Engine {
    private Window window;
    private VulkanContext vulkanContext;

    public Engine(Window window) {
        this.window = window;
    }

    /**
     * Initialize engine subsystems.
     *
     * <p>Initialization order matters!
     * 1. Vulkan (renderer)
     * 2. ECS (Chapter 2)
     * 3. Audio (Chapter 7)
     */
    public void init() {
        System.out.println("Initializing engine...");

        vulkanContext = new VulkanContext(window);
        vulkanContext.init();

        // Future subsystems:
        // ecsWorld.init();
        // audioEngine.init();

        System.out.println("✓ Engine initialized");
    }

    /**
     * Update engine subsystems (called every frame).
     */
    public void update(double deltaTime) {
        // Future: ECS update, audio update, etc.
    }

    /**
     * Render a frame.
     */
    public void render() {
        if (!vulkanContext.renderFrame()) {
            // Swap chain out of date, recreate
            vulkanContext.recreateSwapChain();
        }
    }

    /**
     * Handle window resize.
     */
    public void onResize() {
        vulkanContext.recreateSwapChain();
    }

    /**
     * Cleanup engine subsystems (reverse order of init).
     */
    public void cleanup() {
        vulkanContext.cleanup();
        // Future: ecsWorld.cleanup(), audioEngine.cleanup()
    }
}
```

---

### Step 5: Application Class

Create `src/main/java/com/yourname/engine/core/Application.java`:

```java
package com.yourname.engine.core;

/**
 * Main application class with game loop.
 *
 * <p>Implements the fixed-timestep game loop pattern:
 * <pre>
 * while (running) {
 *     time.update();
 *     while (time.shouldFixedUpdate()) {
 *         updateLogic(fixedDelta);
 *     }
 *     render(time.getAlpha());
 * }
 * </pre>
 */
public class Application {
    private Window window;
    private Engine engine;
    private Time time;

    private boolean running;

    public Application() {
        window = new Window("JECS Engine", 1280, 720);
        engine = new Engine(window);
        time = new Time();
        running = true;
    }

    /**
     * Initialize application.
     */
    public void init() {
        System.out.println("Starting JECS Engine...\n");

        engine.init();
        window.show();

        System.out.println("\nEntering main loop...");
        System.out.println("Press ESC to exit\n");
    }

    /**
     * Main game loop (fixed timestep).
     */
    public void run() {
        init();

        while (running && !window.shouldClose()) {
            // Poll input events
            window.pollEvents();

            // Update time
            time.update();

            // Fixed timestep updates
            while (time.shouldFixedUpdate()) {
                updateFixed(time.getFixedDeltaTime());
            }

            // Variable delta update (for camera, animations, etc.)
            updateVariable(time.getDeltaTime());

            // Render
            render();

            // Handle window resize
            if (window.wasResized()) {
                engine.onResize();
            }

            // Print FPS every second
            if (time.getFrameCount() % 60 == 0) {
                System.out.printf("FPS: %d | Frame: %d | Time: %.2fs%n",
                    time.getFPS(), time.getFrameCount(), time.getElapsedTime());
            }
        }

        cleanup();
    }

    /**
     * Fixed timestep update (physics, game logic).
     *
     * <p>Runs at exactly 60 Hz regardless of frame rate.
     * Use this for deterministic gameplay logic.
     */
    private void updateFixed(double fixedDelta) {
        // Future: ECS systems update here
        // physicsSystem.update(fixedDelta);
        // aiSystem.update(fixedDelta);
    }

    /**
     * Variable delta update (rendering, camera, animations).
     *
     * <p>Runs every frame with variable delta time.
     * Use this for non-gameplay updates (camera smoothing, particle effects).
     */
    private void updateVariable(double delta) {
        engine.update(delta);
    }

    /**
     * Render frame.
     */
    private void render() {
        engine.render();
    }

    /**
     * Cleanup resources.
     */
    private void cleanup() {
        System.out.println("\nShutting down...");
        engine.cleanup();
        window.destroy();
        System.out.println("✓ Goodbye!");
    }
}
```

---

### Step 6: Main Entry Point

Create `src/main/java/com/yourname/Main.java`:

```java
package com.yourname;

import com.yourname.engine.core.Application;

/**
 * Main entry point for JECS Engine.
 */
public class Main {
    public static void main(String[] args) {
        Application app = new Application();
        app.run();
    }
}
```

---

## Testing

### Build and Run

```bash
gradle run
```

**Expected Output (WORKING VERSION):**

```
Starting JECS Engine...

Initializing engine...
  → Vulkan instance created
  → Debug messenger created
  → Window surface created
  → Selected GPU: NVIDIA GeForce RTX 4070
  → Logical device created
  → Swap chain created (3 images)
  → Command pool created
  → Command buffers allocated (2)
  → Sync objects created
✓ Vulkan initialized successfully

✓ Engine initialized

Entering main loop...
Press ESC to exit

FPS: 60 | Frame: 60 | Time: 1.00s
FPS: 60 | Frame: 120 | Time: 2.00s
FPS: 60 | Frame: 180 | Time: 3.00s
...
```

You should see:
- **A window with cycling rainbow colors!** (Red → Purple → Blue → Green cycle)
- Smooth 60 FPS
- No validation errors
- Clean shutdown with ESC

### What We Built

We now have:
- ✅ Complete Vulkan instance with validation
- ✅ Working device selection
- ✅ Functional swap chain
- ✅ Command buffer recording
- ✅ Proper synchronization
- ✅ **Visible rainbow clear screen!**

---

## Common Issues and Solutions

### Issue 1: Validation Layer Not Found

**Error:**
```
Validation layers requested but not available
```

**Cause:** Vulkan SDK not installed or not in PATH.

**Solution:**
```bash
# Windows
set VULKAN_SDK=C:\VulkanSDK\1.3.xxx.x

# Linux
export VULKAN_SDK=/usr/local/VulkanSDK/1.3.xxx.x

# macOS
export VULKAN_SDK=/Users/YourName/VulkanSDK/1.3.xxx.x
```

### Issue 2: No Suitable GPU

**Error:**
```
Failed to find a suitable GPU
```

**Cause:** GPU doesn't support Vulkan or drivers are outdated.

**Solution:**
1. Update GPU drivers (NVIDIA/AMD/Intel)
2. Check GPU compatibility: https://vulkan.gpuinfo.org/

### Issue 3: Swap Chain Creation Fails

**Error:**
```
Failed to create swap chain
```

**Cause:** Window is minimized (width/height = 0).

**Solution:** Add window size check:
```java
// In createSwapChain():
if (window.getWidth() == 0 || window.getHeight() == 0) {
    throw new RuntimeException("Cannot create swap chain with zero-sized window");
}
```

### Issue 4: Screen Stays Black

**Possible causes:**
1. **Swap chain images not transitioning:** Check image layout transitions
2. **Command buffer not submitted:** Check vkQueueSubmit return value
3. **Synchronization deadlock:** Ensure fences start signaled

**Debug:**
```java
// Add logging
System.out.println("Image index: " + imageIndex);
System.out.println("Submit result: " + result);
```

---

## Performance Insights

### Frame Pacing

**Good frame pacing (consistent 16.67ms):**
```
Frame 1: 16.5ms
Frame 2: 16.8ms
Frame 3: 16.6ms
Frame 4: 16.7ms
Average: 16.65ms (60 FPS)
```

**Bad frame pacing (stuttering):**
```
Frame 1: 10ms
Frame 2: 10ms
Frame 3: 35ms  ← Stutter!
Frame 4: 10ms
Average: 16.25ms (61 FPS) ← Average looks good, but stutters!
```

**Causes of bad pacing:**
- Garbage collection spikes
- Windows Defender/antivirus scanning
- Background CPU tasks (Windows Update)
- VSync misalignment

**Solutions:**
- Use ZGC (Chapter 0 build.gradle)
- Disable Windows fullscreen optimization
- Use process priority (via Task Manager)

### CPU vs GPU Bound

**CPU bound symptoms:**
- High CPU usage (70-100%)
- Low GPU usage (20-40%)
- Lowering graphics settings doesn't help FPS

**GPU bound symptoms:**
- Low CPU usage (20-40%)
- High GPU usage (90-100%)
- Lowering resolution improves FPS

**Our rainbow clear is CPU bound** (command buffer recording overhead).
Chapter 9 (PBR rendering) will be GPU bound.

---

## Further Reading

### Official Vulkan Resources
- [Vulkan Tutorial](https://vulkan-tutorial.com/) - Excellent step-by-step guide
- [Vulkan Spec](https://www.khronos.org/registry/vulkan/specs/1.3/html/) - Official specification
- [GPU Info Database](https://vulkan.gpuinfo.org/) - Device capabilities lookup

### Game Loop Articles
- [Fix Your Timestep](https://gafferongames.com/post/fix_your_timestep/) - Classic article by Glenn Fiedler
- [Game Programming Patterns - Game Loop](https://gameprogrammingpatterns.com/game-loop.html)
- [Timestep and Performance](https://www.koonsolo.com/news/dewitters-gameloop/)

### LWJGL Resources
- [LWJGL Wiki](https://github.com/LWJGL/lwjgl3-wiki/wiki) - Java-specific Vulkan examples
- [LWJGL Javadoc](https://javadoc.lwjgl.org/) - Complete API reference

---

## Exercises

1. **Modify Clear Color**
   - Change the rainbow to a single color
   - Make it pulse (fade in/out) instead of cycle
   - Add user control (keys 1-9 for different colors)

2. **Experiment with Present Modes**
   - Change to IMMEDIATE mode (see tearing)
   - Compare FIFO vs MAILBOX latency
   - Measure frame times with different modes

3. **Frame Time Graph**
   - Store last 120 frame times
   - Print min/max/average every second
   - Detect stuttering (frame time > 2x average)

4. **Window Controls**
   - Add fullscreen toggle (F11)
   - Add window title FPS display
   - Pause rendering when minimized

---

## What's Next?

In **Chapter 2**, we'll implement the ECS (Entity Component System) core architecture! We'll build:
- Sparse set storage (cache-friendly)
- Component registration system
- Query API for systems
- Entity lifecycle (create, destroy, clone)

This will be the foundation for all gameplay code.

---

**Previous:** [← Chapter 0 - Prerequisites & Setup](chapter-00-prerequisites-setup.md)
**Next:** [Chapter 2 - ECS Core Architecture →](chapter-02-ecs-core.md)
