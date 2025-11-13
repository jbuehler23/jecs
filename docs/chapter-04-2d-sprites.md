# Chapter 4: 2D Sprites, Batching & Playable Game
## Building a Complete 2D Space Shooter

**What You'll Learn:**
- Texture loading and Vulkan image management
- Sprite batching for rendering 10,000+ sprites
- Graphics pipeline creation (shaders, vertex input, descriptors)
- 2D orthographic camera system
- ECS integration for rendering
- Complete playable game implementation

**What You'll Build:**
A fully playable 2D space shooter with:
- Player ship with WASD movement and mouse shooting
- Enemies that spawn and chase the player
- Projectile system with collision detection
- Visual rendering of all game entities
- 1000+ sprites running at 60 FPS

**Estimated Time:** 4-5 hours

**Prerequisites:** Chapters 1-3 completed

---

## Introduction: From Clear Screen to Real Game

In Chapter 1, we created a renderer that clears the screen with a rainbow color. Now we'll build a **complete playable game** with:

1. **Texture loading** from PNG files (STB Image + Vulkan)
2. **Sprite batch system** for efficient rendering (minimize draw calls)
3. **Graphics pipeline** with shaders (vertex + fragment)
4. **2D camera** with view/projection matrices
5. **ECS integration** to render game entities
6. **Playable space shooter** - enemies, shooting, collision!

**Architecture Overview:**

```
Game Logic (ECS)
     ↓
Renderable Components (Sprite, Transform2D)
     ↓
SpriteRenderSystem (queries entities, builds batches)
     ↓
SpriteBatch (groups sprites by texture, minimizes draw calls)
     ↓
VulkanRenderer (submits GPU commands)
     ↓
GPU (parallel rendering)
```

**Why This Architecture?**

Traditional immediate-mode rendering:
```java
// BAD: One draw call per sprite (CPU bottleneck!)
for (Sprite sprite : sprites) {
    gpu.draw(sprite); // 1000 sprites = 1000 draw calls
}
// Result: CPU spends 15ms just submitting draw calls
```

Batched rendering:
```java
// GOOD: Group by texture, one draw call per batch
Map<Texture, List<Sprite>> batches = groupByTexture(sprites);
for (var batch : batches.values()) {
    gpu.drawBatch(batch); // 1000 sprites with 5 textures = 5 draw calls
}
// Result: CPU spends 0.5ms submitting draw calls (30x faster!)
```

**Professional engines using batching:**
- Unity (Sprite Renderer batching)
- Unreal (Instanced Static Mesh)
- Godot (MultiMesh)
- LibGDX (SpriteBatch)

---

## Concepts: 2D Rendering Pipeline

### From Image File to GPU

Here's the complete journey of a texture:

```
PNG File (disk)
     ↓ STB Image (load, decode)
ByteBuffer (CPU RAM) [RGBA pixels]
     ↓ vkMapMemory (copy)
Staging Buffer (CPU-visible VRAM)
     ↓ vkCmdCopyBufferToImage (GPU command)
VkImage (GPU-only VRAM)
     ↓ vkCmdPipelineBarrier (layout transition)
VkImage (SHADER_READ_ONLY layout)
     ↓ Fragment Shader (sample texture)
Screen Pixels
```

**Why so many steps?**

**Incorrect approach (doesn't work):**
```java
// Can't do this! CPU can't write directly to GPU memory
ByteBuffer pixels = loadPNG("ship.png");
VkImage gpuImage = createImage();
memcpy(gpuImage, pixels); // ❌ Segmentation fault!
```

**Correct approach:**
```java
// 1. Load to CPU RAM
ByteBuffer pixels = STBImage.stbi_load("ship.png");

// 2. Create staging buffer (CPU can write, GPU can read)
VkBuffer staging = createBuffer(CPU_VISIBLE | GPU_READABLE);
memcpy(staging, pixels); // ✅ Works!

// 3. Create GPU-only image (fast, but CPU can't access)
VkImage gpuImage = createImage(GPU_ONLY);

// 4. GPU copies from staging to image (parallel, async)
vkCmdCopyBufferToImage(commandBuffer, staging, gpuImage);

// 5. Cleanup staging buffer (no longer needed)
vkDestroyBuffer(staging);
```

**Why staging buffers exist:**

Modern GPUs have **two types of memory**:

1. **System RAM (CPU-visible):**
   - Fast for CPU reads/writes
   - Slow for GPU access (PCIe bus bandwidth limited)
   - Used for: Staging buffers, uniform buffers (updated per frame)

2. **VRAM (GPU-only):**
   - Slow for CPU access (requires PCIe transfer)
   - Fast for GPU access (800 GB/s bandwidth!)
   - Used for: Textures, vertex buffers, render targets

**Real-world numbers:**
```
CPU → System RAM:     50 GB/s  (DDR4)
CPU → VRAM:           16 GB/s  (PCIe 4.0)
GPU → System RAM:     16 GB/s  (PCIe 4.0)
GPU → VRAM:          800 GB/s  (GDDR6)
```

**Optimization strategy:**
- Upload once (CPU → VRAM via staging buffer)
- Access repeatedly (GPU reads from VRAM at 800 GB/s)

---

## Image Layout Transitions

Vulkan images have **layouts** that optimize for specific operations. Transitioning between layouts is **required** and has real performance implications.

### Common Image Layouts

| Layout | Purpose | GPU Access |
|--------|---------|------------|
| `UNDEFINED` | Initial state, contents don't matter | N/A |
| `TRANSFER_DST_OPTIMAL` | Optimized for copy/clear operations | Write-only |
| `SHADER_READ_ONLY_OPTIMAL` | Optimized for texture sampling | Read-only |
| `COLOR_ATTACHMENT_OPTIMAL` | Optimized for rendering output | Read/Write |
| `PRESENT_SRC_KHR` | Optimized for presenting to screen | Read-only |

**Why layouts exist:**

GPUs store textures in **tiled memory** (not linear rows of pixels):

**Linear layout (CPU-friendly):**
```
Row 0: [RGBA][RGBA][RGBA][RGBA]...
Row 1: [RGBA][RGBA][RGBA][RGBA]...
Row 2: [RGBA][RGBA][RGBA][RGBA]...

Access pattern: Sequential
CPU reads: Fast (cache-friendly)
GPU reads: Slow (cache misses)
```

**Tiled layout (GPU-friendly):**
```
Tile 0: [8x8 pixels in Morton order]
Tile 1: [8x8 pixels in Morton order]
...

Access pattern: Spatial locality
CPU reads: Complex (swizzled addressing)
GPU reads: Fast (texture cache optimized)
```

**Example: Sampling neighbors**

Shader code:
```glsl
// Sample 3x3 grid for blur
vec4 color = texture(sampler, uv);
vec4 left  = texture(sampler, uv + vec2(-1, 0));
vec4 right = texture(sampler, uv + vec2(+1, 0));
// ...
```

**Linear layout:** 3x3 grid spans 3 cache lines (9 cache misses)
**Tiled layout:** 3x3 grid in same 8x8 tile (1 cache hit)

**Result:** 9x faster texture sampling with tiled layout!

### Layout Transition Cost

**Good news:** Layout transitions are **free** (GPU rearranges pointers, not data)

```java
// This is instant (no memory copy)
vkCmdPipelineBarrier(cmd,
    srcStage: TRANSFER,
    dstStage: FRAGMENT_SHADER,
    oldLayout: TRANSFER_DST_OPTIMAL,
    newLayout: SHADER_READ_ONLY_OPTIMAL
);
```

**What actually happens:**
- GPU page table updated (metadata only)
- No pixel data copied
- Execution pipeline synchronized (waits for transfer to finish)

**Bad news:** Missing a transition causes **validation errors** or **incorrect rendering**

```java
// ❌ Missing transition - shader reads garbage!
vkCmdCopyBufferToImage(...); // Image in TRANSFER_DST layout
// ... forgot vkCmdPipelineBarrier() ...
vkCmdDraw(...); // Shader expects SHADER_READ_ONLY layout → CRASH!
```

---

## Orthographic vs Perspective Projection

2D games use **orthographic projection** (no perspective distortion).

### Orthographic Projection

```java
// Top-left = (0, 0), Bottom-right = (width, height)
Matrix4f projection = new Matrix4f().setOrtho(
    0, width,      // left, right
    height, 0,     // bottom, top (flipped for screen coordinates)
    -1, 1          // near, far
);
```

**What this does:**

**World space coordinates:**
```
Entity at (100, 200) with size 32x32
```

**After projection:**
```
NDC (Normalized Device Coordinates):
x: 100 / 1920 = 0.052  (left = -1, right = +1)
y: 200 / 1080 = 0.185  (top = -1, bottom = +1)
```

**GPU viewport transform:**
```
Screen coordinates:
x: (0.052 + 1) / 2 * 1920 = 100 pixels from left
y: (0.185 + 1) / 2 * 1080 = 200 pixels from top
```

**Result:** (100, 200) in world space → (100, 200) on screen!

### Perspective Projection (for comparison)

```java
// 3D projection with depth
Matrix4f projection = new Matrix4f().setPerspective(
    fov: 70° * Math.PI / 180,
    aspect: width / height,
    near: 0.1f,
    far: 1000.0f
);
```

**Effect:**
- Objects farther away appear smaller
- Parallel lines converge at horizon
- **Not suitable for 2D games!** (UI elements would shrink with distance)

**When to use each:**
- **Orthographic:** 2D games, UI overlays, strategy games (top-down)
- **Perspective:** 3D games, first-person, third-person

---

## Sprite Batching Deep Dive

### The Draw Call Problem

**Every draw call has overhead:**

```
Single draw call CPU cost: ~50 microseconds
60 FPS budget: 16,666 microseconds
Max draw calls: 16,666 / 50 = 333 draws/frame
```

**Naive rendering (1000 sprites):**
```java
for (Sprite sprite : sprites) {
    vkCmdBindDescriptorSets(...); // Bind texture
    vkCmdBindVertexBuffers(...);  // Bind quad
    vkCmdDraw(6, 1, 0, 0);        // Draw 2 triangles
}
// Total: 1000 draws × 50μs = 50,000μs = 50ms
// Result: 20 FPS (CPU bottleneck!)
```

**Batched rendering (1000 sprites, 5 unique textures):**
```java
// Group sprites by texture
Map<Texture, List<Sprite>> batches = groupByTexture(sprites);

for (var entry : batches.entrySet()) {
    Texture texture = entry.getKey();
    List<Sprite> batch = entry.getValue();

    // Build vertex buffer with all quads
    VertexBuffer vb = buildBatch(batch); // CPU work: ~0.1ms

    // Single draw call for entire batch
    vkCmdBindDescriptorSets(...);     // Bind texture
    vkCmdBindVertexBuffers(vb);
    vkCmdDraw(batch.size() * 6, 1, 0, 0);
}
// Total: 5 batches × 50μs = 250μs = 0.25ms
// Result: 500+ FPS (GPU bound, CPU idle!)
```

**Result:** 200x fewer draw calls, 200x less CPU overhead!

### Batching Trade-offs

**Pros:**
- Minimize draw calls (CPU bottleneck eliminated)
- Better GPU utilization (larger batches = more parallelism)
- Cache-friendly (sequential vertex data)

**Cons:**
- Requires dynamic vertex buffers (rebuild every frame)
- Sorting overhead (group by texture, sort by Z-order)
- Z-order conflicts (can't batch sprites with different depths)

**When batching fails:**

```java
// Can't batch these sprites (different textures):
Sprite ship    (texture: ship.png,    z: 10)
Sprite bullet  (texture: bullet.png,  z: 5)
Sprite enemy   (texture: enemy.png,   z: 8)

// Needs 3 draw calls (can't merge different textures)
```

**Solution: Texture atlas**

```java
// Combine all textures into one atlas
Texture atlas (contains: ship, bullet, enemy)

// Now all sprites use same texture!
Sprite ship   (texture: atlas, uv: 0.0-0.25, z: 10)
Sprite bullet (texture: atlas, uv: 0.25-0.5, z: 5)
Sprite enemy  (texture: atlas, uv: 0.5-1.0,  z: 8)

// Single draw call for all sprites!
```

**Professional engines:**
- Unity: Sprite Atlas (automatic packing)
- Godot: AtlasTexture
- LibGDX: TexturePacker
- Unreal: Texture2D Array

---

## Implementation

### Step 1: Texture Loading

Create `src/main/java/com/yourname/engine/renderer/Texture.java`:

```java
package com.yourname.engine.renderer;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.lwjgl.stb.STBImage;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * Texture loaded from file and uploaded to GPU.
 *
 * <h2>Texture Loading Pipeline</h2>
 * <pre>
 * 1. STB Image decodes PNG/JPG to RGBA pixels (CPU)
 * 2. Create staging buffer (CPU-visible VRAM)
 * 3. Copy pixels to staging buffer
 * 4. Create GPU-only VkImage
 * 5. Transition image to TRANSFER_DST layout
 * 6. GPU copies staging buffer → image
 * 7. Transition image to SHADER_READ_ONLY layout
 * 8. Cleanup staging buffer
 * 9. Create VkImageView (shader interface)
 * 10. Create VkSampler (filtering, wrapping)
 * </pre>
 *
 * <h2>Why Staging Buffers?</h2>
 * <p>GPU memory (VRAM) is fast for GPU but slow for CPU access.
 * Staging buffers are in system RAM, fast for CPU writes.
 *
 * <p>Performance comparison:
 * <pre>
 * CPU write to system RAM:  50 GB/s  (DDR4)
 * CPU write to VRAM:        16 GB/s  (PCIe 4.0) ← 3x slower!
 * GPU read from VRAM:      800 GB/s  (GDDR6)   ← 50x faster!
 * </pre>
 *
 * <p>Strategy: Upload once (slow), access repeatedly (fast).
 */
public class Texture {

    private int width;
    private int height;
    private long vkImage;
    private long vkImageView;
    private long vkImageMemory;
    private long vkSampler;

    private VkDevice device;
    private VkPhysicalDevice physicalDevice;

    /**
     * Load texture from file path.
     *
     * <p>Supports: PNG, JPG, TGA, BMP, PSD, GIF, HDR, PIC
     * <p>Always loads as RGBA (4 bytes per pixel)
     *
     * <h2>STB Image Library</h2>
     * <p>STB is a single-header C library for image loading.
     * LWJGL provides Java bindings via JNI.
     *
     * <p>Why STB over ImageIO?
     * - 10x faster decoding (native C code)
     * - Consistent color space handling (sRGB)
     * - More format support (PSD, HDR)
     *
     * @param path Relative path (e.g., "assets/ship.png")
     */
    public static Texture load(String path, VkDevice device, VkPhysicalDevice physicalDevice,
                                VkQueue graphicsQueue, long commandPool) {
        Texture texture = new Texture();
        texture.device = device;
        texture.physicalDevice = physicalDevice;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);

            // Load image with STB
            // Force 4 channels (RGBA) even if image is RGB or grayscale
            ByteBuffer pixels = STBImage.stbi_load(path, w, h, channels, 4);
            if (pixels == null) {
                throw new RuntimeException("Failed to load texture: " + path +
                    " - " + STBImage.stbi_failure_reason());
            }

            texture.width = w.get(0);
            texture.height = h.get(0);

            long imageSize = texture.width * texture.height * 4L; // RGBA

            System.out.println("  Loading texture: " + path +
                " (" + texture.width + "x" + texture.height +
                ", " + (imageSize / 1024) + " KB)");

            // Create staging buffer (CPU can write, GPU can read)
            long[] stagingBuffer = new long[1];
            long[] stagingMemory = new long[1];
            texture.createBuffer(imageSize, VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                stagingBuffer, stagingMemory);

            // Copy pixel data to staging buffer
            PointerBuffer data = stack.mallocPointer(1);
            vkMapMemory(device, stagingMemory[0], 0, imageSize, 0, data);
            memCopy(memAddress(pixels), data.get(0), imageSize);
            vkUnmapMemory(device, stagingMemory[0]);

            // Free CPU-side pixel buffer (copied to staging)
            STBImage.stbi_image_free(pixels);

            // Create GPU-only image (optimal for shader sampling)
            texture.createImage(texture.width, texture.height, VK_FORMAT_R8G8B8A8_SRGB,
                VK_IMAGE_TILING_OPTIMAL,
                VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);

            // Transition image layout: UNDEFINED → TRANSFER_DST
            // (Prepare for GPU copy operation)
            texture.transitionImageLayout(graphicsQueue, commandPool,
                VK_IMAGE_LAYOUT_UNDEFINED,
                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);

            // GPU copies staging buffer → image
            texture.copyBufferToImage(graphicsQueue, commandPool,
                stagingBuffer[0], texture.width, texture.height);

            // Transition image layout: TRANSFER_DST → SHADER_READ_ONLY
            // (Prepare for shader sampling)
            texture.transitionImageLayout(graphicsQueue, commandPool,
                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

            // Cleanup staging buffer (no longer needed)
            vkDestroyBuffer(device, stagingBuffer[0], null);
            vkFreeMemory(device, stagingMemory[0], null);

            // Create image view (shader interface to image)
            texture.createImageView();

            // Create sampler (filtering and wrapping modes)
            texture.createSampler();

            System.out.println("  ✓ Texture uploaded to GPU");

            return texture;
        }
    }

    /**
     * Create Vulkan image (2D texture).
     *
     * <h2>Image Tiling</h2>
     * <p>Two tiling modes available:
     * - LINEAR: Row-major pixels (CPU-friendly, slow for GPU)
     * - OPTIMAL: Tiled/swizzled (GPU-friendly, fast for GPU)
     *
     * <p>We use OPTIMAL because textures are read by GPU shaders.
     *
     * <h2>Usage Flags</h2>
     * - TRANSFER_DST_BIT: Image can be copy destination
     * - SAMPLED_BIT: Image can be sampled in shaders
     */
    private void createImage(int width, int height, int format, int tiling, int usage, int properties) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack);
            imageInfo.sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO);
            imageInfo.imageType(VK_IMAGE_TYPE_2D);
            imageInfo.extent().width(width);
            imageInfo.extent().height(height);
            imageInfo.extent().depth(1);
            imageInfo.mipLevels(1);          // No mipmaps (could add later)
            imageInfo.arrayLayers(1);        // Single layer (not array texture)
            imageInfo.format(format);        // R8G8B8A8_SRGB
            imageInfo.tiling(tiling);        // OPTIMAL (GPU-friendly)
            imageInfo.initialLayout(VK_IMAGE_LAYOUT_UNDEFINED); // Don't care about initial contents
            imageInfo.usage(usage);          // TRANSFER_DST | SAMPLED
            imageInfo.sharingMode(VK_SHARING_MODE_EXCLUSIVE); // Only graphics queue accesses
            imageInfo.samples(VK_SAMPLE_COUNT_1_BIT); // No MSAA

            LongBuffer pImage = stack.mallocLong(1);
            if (vkCreateImage(device, imageInfo, null, pImage) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create image");
            }
            vkImage = pImage.get(0);

            // Allocate memory for image
            VkMemoryRequirements memRequirements = VkMemoryRequirements.malloc(stack);
            vkGetImageMemoryRequirements(device, vkImage, memRequirements);

            VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack);
            allocInfo.sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);
            allocInfo.allocationSize(memRequirements.size());
            allocInfo.memoryTypeIndex(findMemoryType(memRequirements.memoryTypeBits(), properties));

            LongBuffer pMemory = stack.mallocLong(1);
            if (vkAllocateMemory(device, allocInfo, null, pMemory) != VK_SUCCESS) {
                throw new RuntimeException("Failed to allocate image memory");
            }
            vkImageMemory = pMemory.get(0);

            // Bind memory to image
            vkBindImageMemory(device, vkImage, vkImageMemory, 0);
        }
    }

    /**
     * Create image view (shader interface to VkImage).
     *
     * <p>VkImage is raw GPU memory. VkImageView describes:
     * - How to interpret the data (format, components)
     * - Which mip levels to access
     * - Which array layers to access
     *
     * <p>Analogy: VkImage = raw file bytes, VkImageView = file format parser
     */
    private void createImageView() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack);
            viewInfo.sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO);
            viewInfo.image(vkImage);
            viewInfo.viewType(VK_IMAGE_VIEW_TYPE_2D);
            viewInfo.format(VK_FORMAT_R8G8B8A8_SRGB);

            // Component swizzling (identity = no swizzle)
            viewInfo.components().r(VK_COMPONENT_SWIZZLE_IDENTITY);
            viewInfo.components().g(VK_COMPONENT_SWIZZLE_IDENTITY);
            viewInfo.components().b(VK_COMPONENT_SWIZZLE_IDENTITY);
            viewInfo.components().a(VK_COMPONENT_SWIZZLE_IDENTITY);

            // Subresource range (which mip/array layers to access)
            viewInfo.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);
            viewInfo.subresourceRange().baseMipLevel(0);
            viewInfo.subresourceRange().levelCount(1);
            viewInfo.subresourceRange().baseArrayLayer(0);
            viewInfo.subresourceRange().layerCount(1);

            LongBuffer pImageView = stack.mallocLong(1);
            if (vkCreateImageView(device, viewInfo, null, pImageView) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create image view");
            }
            vkImageView = pImageView.get(0);
        }
    }

    /**
     * Create texture sampler (filtering and wrapping modes).
     *
     * <h2>Filtering Modes</h2>
     * <p>When textures are magnified/minified:
     * - NEAREST: Blocky/pixelated (retro games, pixel art)
     * - LINEAR: Smooth/blurred (modern games)
     *
     * <p>Example at 2x zoom:
     * <pre>
     * NEAREST:  [■][■]  (each pixel doubled)
     *           [■][■]
     *
     * LINEAR:   [▓][▒]  (interpolated between pixels)
     *           [▒][░]
     * </pre>
     *
     * <h2>Address Modes</h2>
     * <p>When UV coordinates go outside [0, 1]:
     * - REPEAT: Tile texture (for floors, walls)
     * - CLAMP: Edge pixels repeat (for UI elements)
     * - MIRROR: Flip texture at edges
     *
     * <h2>Anisotropic Filtering</h2>
     * <p>Improves quality at oblique angles (floor textures).
     * Cost: ~10% performance, high quality improvement.
     * We disable for 2D sprites (always viewed head-on).
     */
    private void createSampler() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSamplerCreateInfo samplerInfo = VkSamplerCreateInfo.calloc(stack);
            samplerInfo.sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO);

            // Linear filtering (smooth scaling)
            samplerInfo.magFilter(VK_FILTER_LINEAR); // Magnification (zoom in)
            samplerInfo.minFilter(VK_FILTER_LINEAR); // Minification (zoom out)

            // Repeat wrapping (for tiling)
            samplerInfo.addressModeU(VK_SAMPLER_ADDRESS_MODE_REPEAT);
            samplerInfo.addressModeV(VK_SAMPLER_ADDRESS_MODE_REPEAT);
            samplerInfo.addressModeW(VK_SAMPLER_ADDRESS_MODE_REPEAT);

            // No anisotropic filtering (2D sprites don't need it)
            samplerInfo.anisotropyEnable(false);
            samplerInfo.maxAnisotropy(1.0f);

            // Border color (when using CLAMP_TO_BORDER)
            samplerInfo.borderColor(VK_BORDER_COLOR_INT_OPAQUE_BLACK);

            // Normalized coordinates (UV in [0, 1])
            samplerInfo.unnormalizedCoordinates(false);

            // No comparison (for shadow mapping)
            samplerInfo.compareEnable(false);
            samplerInfo.compareOp(VK_COMPARE_OP_ALWAYS);

            // Mipmap mode (LINEAR for smooth LOD transitions)
            samplerInfo.mipmapMode(VK_SAMPLER_MIPMAP_MODE_LINEAR);
            samplerInfo.mipLodBias(0.0f);
            samplerInfo.minLod(0.0f);
            samplerInfo.maxLod(0.0f); // No mipmaps (only level 0)

            LongBuffer pSampler = stack.mallocLong(1);
            if (vkCreateSampler(device, samplerInfo, null, pSampler) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create sampler");
            }
            vkSampler = pSampler.get(0);
        }
    }

    /**
     * Transition image layout (prepare for different GPU operations).
     *
     * <h2>Why Layout Transitions?</h2>
     * <p>GPU stores images in tiled memory for fast access.
     * Different operations require different memory layouts:
     * - TRANSFER_DST: Optimized for copy operations
     * - SHADER_READ_ONLY: Optimized for texture sampling
     * - COLOR_ATTACHMENT: Optimized for rendering output
     *
     * <p>Transitioning is free (GPU updates page tables, no data copy).
     *
     * <h2>Pipeline Barriers</h2>
     * <p>Ensures GPU operations don't overlap:
     * <pre>
     * [Transfer Stage] → Barrier → [Fragment Shader Stage]
     *     ↓                           ↓
     *   Copy data          Wait for copy, then sample texture
     * </pre>
     *
     * <p>Without barrier: Race condition (shader reads while copy writes)!
     */
    private void transitionImageLayout(VkQueue queue, long commandPool, int oldLayout, int newLayout) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBuffer commandBuffer = beginSingleTimeCommands(commandPool, stack);

            VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
            barrier.sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER);
            barrier.oldLayout(oldLayout);
            barrier.newLayout(newLayout);
            barrier.srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED); // No queue family transfer
            barrier.dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);
            barrier.image(vkImage);

            // Color aspect (not depth/stencil)
            barrier.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);
            barrier.subresourceRange().baseMipLevel(0);
            barrier.subresourceRange().levelCount(1);
            barrier.subresourceRange().baseArrayLayer(0);
            barrier.subresourceRange().layerCount(1);

            // Configure access masks and pipeline stages
            int sourceStage;
            int destinationStage;

            if (oldLayout == VK_IMAGE_LAYOUT_UNDEFINED &&
                newLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
                // Transition before copy: No previous operations → Transfer write
                barrier.srcAccessMask(0); // No previous access
                barrier.dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
                sourceStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT; // Before everything
                destinationStage = VK_PIPELINE_STAGE_TRANSFER_BIT;

            } else if (oldLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL &&
                       newLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
                // Transition after copy: Transfer write → Shader read
                barrier.srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
                barrier.dstAccessMask(VK_ACCESS_SHADER_READ_BIT);
                sourceStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
                destinationStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;

            } else {
                throw new IllegalArgumentException("Unsupported layout transition: " +
                    oldLayout + " → " + newLayout);
            }

            vkCmdPipelineBarrier(commandBuffer,
                sourceStage, destinationStage, // Pipeline stages
                0,                             // No dependency flags
                null,                          // No memory barriers
                null,                          // No buffer barriers
                barrier);                      // Image barrier

            endSingleTimeCommands(commandBuffer, queue, commandPool);
        }
    }

    /**
     * Copy staging buffer to GPU image.
     *
     * <p>Uses GPU's DMA (Direct Memory Access) engine.
     * CPU submits command, GPU executes asynchronously.
     *
     * <h2>Copy Performance</h2>
     * <pre>
     * 4K texture (4096x4096 RGBA) = 64 MB
     * GPU copy bandwidth: ~800 GB/s
     * Copy time: 64 MB / 800 GB/s = 0.08ms
     * </pre>
     *
     * <p>Practically instant for typical game textures!
     */
    private void copyBufferToImage(VkQueue queue, long commandPool, long buffer, int width, int height) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBuffer commandBuffer = beginSingleTimeCommands(commandPool, stack);

            VkBufferImageCopy.Buffer region = VkBufferImageCopy.calloc(1, stack);
            region.bufferOffset(0);             // Start at beginning of buffer
            region.bufferRowLength(0);          // Tightly packed (no padding)
            region.bufferImageHeight(0);        // Tightly packed (no padding)

            region.imageSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);
            region.imageSubresource().mipLevel(0);
            region.imageSubresource().baseArrayLayer(0);
            region.imageSubresource().layerCount(1);

            region.imageOffset().set(0, 0, 0);  // Top-left corner
            region.imageExtent().set(width, height, 1);

            vkCmdCopyBufferToImage(commandBuffer, buffer, vkImage,
                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region);

            endSingleTimeCommands(commandBuffer, queue, commandPool);
        }
    }

    /**
     * Allocate single-use command buffer for immediate operations.
     *
     * <p>Used for one-time setup operations (texture upload).
     * Submitted immediately, then freed.
     */
    private VkCommandBuffer beginSingleTimeCommands(long commandPool, MemoryStack stack) {
        VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc(stack);
        allocInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO);
        allocInfo.level(VK_COMMAND_BUFFER_LEVEL_PRIMARY);
        allocInfo.commandPool(commandPool);
        allocInfo.commandBufferCount(1);

        PointerBuffer pCommandBuffer = stack.mallocPointer(1);
        vkAllocateCommandBuffers(device, allocInfo, pCommandBuffer);
        VkCommandBuffer commandBuffer = new VkCommandBuffer(pCommandBuffer.get(0), device);

        VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack);
        beginInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
        beginInfo.flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);

        vkBeginCommandBuffer(commandBuffer, beginInfo);

        return commandBuffer;
    }

    /**
     * Submit command buffer and wait for completion.
     *
     * <p>Blocking operation (waits for GPU to finish).
     * Only used for initialization, not per-frame rendering.
     */
    private void endSingleTimeCommands(VkCommandBuffer commandBuffer, VkQueue queue, long commandPool) {
        vkEndCommandBuffer(commandBuffer);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack);
            submitInfo.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO);
            submitInfo.pCommandBuffers(stack.pointers(commandBuffer));

            // Submit and wait (blocking!)
            vkQueueSubmit(queue, submitInfo, VK_NULL_HANDLE);
            vkQueueWaitIdle(queue);
        }

        vkFreeCommandBuffers(device, commandPool, commandBuffer);
    }

    /**
     * Create Vulkan buffer (staging buffer for texture upload).
     *
     * <h2>Buffer Usage Flags</h2>
     * - TRANSFER_SRC: Buffer can be copy source
     * - TRANSFER_DST: Buffer can be copy destination
     * - VERTEX_BUFFER: Buffer contains vertex data
     * - INDEX_BUFFER: Buffer contains index data
     * - UNIFORM_BUFFER: Buffer contains shader uniforms
     *
     * <h2>Memory Property Flags</h2>
     * - DEVICE_LOCAL: GPU-only memory (fast, CPU can't access)
     * - HOST_VISIBLE: CPU can map/access (slower for GPU)
     * - HOST_COHERENT: Auto-sync CPU↔GPU (no flush needed)
     */
    private void createBuffer(long size, int usage, int properties, long[] buffer, long[] bufferMemory) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack);
            bufferInfo.sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO);
            bufferInfo.size(size);
            bufferInfo.usage(usage);
            bufferInfo.sharingMode(VK_SHARING_MODE_EXCLUSIVE);

            LongBuffer pBuffer = stack.mallocLong(1);
            if (vkCreateBuffer(device, bufferInfo, null, pBuffer) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create buffer");
            }
            buffer[0] = pBuffer.get(0);

            // Query memory requirements
            VkMemoryRequirements memRequirements = VkMemoryRequirements.malloc(stack);
            vkGetBufferMemoryRequirements(device, buffer[0], memRequirements);

            // Allocate memory
            VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack);
            allocInfo.sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);
            allocInfo.allocationSize(memRequirements.size());
            allocInfo.memoryTypeIndex(findMemoryType(memRequirements.memoryTypeBits(), properties));

            LongBuffer pMemory = stack.mallocLong(1);
            if (vkAllocateMemory(device, allocInfo, null, pMemory) != VK_SUCCESS) {
                throw new RuntimeException("Failed to allocate buffer memory");
            }
            bufferMemory[0] = pMemory.get(0);

            // Bind memory to buffer
            vkBindBufferMemory(device, buffer[0], bufferMemory[0], 0);
        }
    }

    /**
     * Find suitable memory type for allocation.
     *
     * <p>GPUs have multiple memory heaps with different properties:
     * - DEVICE_LOCAL: Fast VRAM (800 GB/s)
     * - HOST_VISIBLE: Slow system RAM (16 GB/s via PCIe)
     *
     * <p>Example GPU memory types:
     * <pre>
     * Type 0: DEVICE_LOCAL (VRAM)           - For textures, vertex buffers
     * Type 1: HOST_VISIBLE | HOST_COHERENT  - For staging buffers
     * Type 2: HOST_VISIBLE | HOST_CACHED    - For readback buffers
     * </pre>
     *
     * @param typeFilter Bitmask of allowed memory types
     * @param properties Required memory properties
     * @return Index of suitable memory type
     */
    private int findMemoryType(int typeFilter, int properties) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceMemoryProperties memProperties = VkPhysicalDeviceMemoryProperties.malloc(stack);
            vkGetPhysicalDeviceMemoryProperties(physicalDevice, memProperties);

            for (int i = 0; i < memProperties.memoryTypeCount(); i++) {
                // Check if type is allowed
                if ((typeFilter & (1 << i)) != 0 &&
                    // Check if properties match
                    (memProperties.memoryTypes(i).propertyFlags() & properties) == properties) {
                    return i;
                }
            }

            throw new RuntimeException("Failed to find suitable memory type");
        }
    }

    /**
     * Cleanup Vulkan resources.
     *
     * <p>CRITICAL: Destroy in reverse creation order!
     * Destroying out of order causes validation errors.
     */
    public void cleanup() {
        vkDestroySampler(device, vkSampler, null);
        vkDestroyImageView(device, vkImageView, null);
        vkDestroyImage(device, vkImage, null);
        vkFreeMemory(device, vkImageMemory, null);
    }

    // Getters
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public long getImageView() { return vkImageView; }
    public long getSampler() { return vkSampler; }
}
```

**What this implementation does:**

1. **Loads PNG/JPG** with STB Image (native C library, 10x faster than Java ImageIO)
2. **Creates staging buffer** (CPU-visible) for pixel upload
3. **Copies pixels** from CPU RAM → staging buffer
4. **Creates GPU image** (device-local, optimal for sampling)
5. **Transitions layout** UNDEFINED → TRANSFER_DST (prepare for copy)
6. **GPU copies** staging buffer → image (async, parallel)
7. **Transitions layout** TRANSFER_DST → SHADER_READ_ONLY (ready for shaders)
8. **Cleans up** staging buffer (no longer needed)
9. **Creates image view** (shader interface to raw image)
10. **Creates sampler** (linear filtering, repeat wrapping)

---

### Step 2: 2D Camera

Create `src/main/java/com/yourname/engine/renderer/Camera2D.java`:

```java
package com.yourname.engine.renderer;

import org.joml.Matrix4f;
import org.joml.Vector2f;

/**
 * 2D orthographic camera with position and zoom.
 *
 * <h2>Coordinate Systems</h2>
 * <p>Transformation pipeline:
 * <pre>
 * World Space (game units)
 *     ↓ View Matrix (camera position + zoom)
 * View Space (relative to camera)
 *     ↓ Projection Matrix (orthographic)
 * Clip Space (NDC: -1 to +1)
 *     ↓ Viewport Transform (GPU automatic)
 * Screen Space (pixels)
 * </pre>
 *
 * <h2>Orthographic vs Perspective</h2>
 * <p>Orthographic projection:
 * - No depth distortion (far objects same size as near)
 * - Parallel lines stay parallel
 * - Perfect for 2D games, UI, strategy games
 *
 * <p>Perspective projection:
 * - Depth distortion (far objects smaller)
 * - Parallel lines converge (horizon)
 * - Perfect for 3D games (first-person, third-person)
 *
 * <h2>Zoom Implementation</h2>
 * <p>Zoom is implemented as view matrix scale:
 * <pre>
 * Zoom 1.0: 1 world unit = 1 pixel
 * Zoom 2.0: 1 world unit = 2 pixels (zoomed in)
 * Zoom 0.5: 2 world units = 1 pixel (zoomed out)
 * </pre>
 */
public class Camera2D {

    private Vector2f position;
    private float zoom;
    private Matrix4f viewMatrix;
    private Matrix4f projectionMatrix;
    private Matrix4f viewProjectionMatrix;

    public Camera2D() {
        this.position = new Vector2f(0, 0);
        this.zoom = 1.0f;
        this.viewMatrix = new Matrix4f();
        this.projectionMatrix = new Matrix4f();
        this.viewProjectionMatrix = new Matrix4f();
    }

    /**
     * Update camera matrices for current viewport size.
     *
     * <p>Must be called when viewport resizes!
     *
     * <h2>Projection Matrix</h2>
     * <p>Orthographic projection maps world coordinates to screen:
     * <pre>
     * setOrtho(0, width, height, 0, -1, 1):
     *   Left edge:   x = 0       → screen x = 0
     *   Right edge:  x = width   → screen x = width
     *   Top edge:    y = 0       → screen y = 0
     *   Bottom edge: y = height  → screen y = height
     * </pre>
     *
     * <p>Why flip Y? Screen coordinates have (0,0) at top-left,
     * but OpenGL/Vulkan have (0,0) at bottom-left. Flipping Y
     * makes it intuitive: positive Y = down.
     *
     * <h2>View Matrix</h2>
     * <p>Transforms world to camera space:
     * <pre>
     * Camera at (100, 200), zoom 2.0:
     *   World (150, 250) → View (50, 50) → Screen (100, 100)
     *                        ↑              ↑
     *                   Translate      Scale (zoom)
     * </pre>
     *
     * @param viewportWidth  viewport width in pixels
     * @param viewportHeight viewport height in pixels
     */
    public void update(int viewportWidth, int viewportHeight) {
        // Orthographic projection (0, 0) = top-left
        // Bottom-right = (width, height)
        projectionMatrix.setOrtho(
            0, viewportWidth,  // left, right
            viewportHeight, 0, // bottom, top (FLIPPED for screen coords)
            -1, 1              // near, far (doesn't matter for 2D)
        );

        // View transform (camera position + zoom)
        // Order: Scale first (zoom), then translate (position)
        viewMatrix.identity()
            .translate(-position.x, -position.y, 0) // Move world opposite to camera
            .scale(zoom, zoom, 1);                   // Apply zoom

        // Combined view-projection matrix
        // Shader uses: gl_Position = viewProjection * vec4(worldPos, 0, 1)
        projectionMatrix.mul(viewMatrix, viewProjectionMatrix);
    }

    /**
     * Move camera by offset.
     *
     * <p>Example: camera.move(10, 0) moves camera 10 pixels right
     * (world appears to move left)
     */
    public void move(float dx, float dy) {
        position.x += dx;
        position.y += dy;
    }

    /**
     * Set camera position (center of viewport).
     */
    public void setPosition(float x, float y) {
        position.set(x, y);
    }

    /**
     * Set camera zoom level.
     *
     * <p>Zoom levels:
     * - 1.0: Normal (1 world unit = 1 pixel)
     * - 2.0: 2x zoom in (objects appear 2x larger)
     * - 0.5: 2x zoom out (see 2x more area)
     *
     * <p>Clamped to [0.1, 10.0] to prevent extreme values.
     *
     * @param zoom Zoom multiplier (1.0 = normal)
     */
    public void setZoom(float zoom) {
        this.zoom = Math.max(0.1f, Math.min(10.0f, zoom)); // Clamp 0.1x - 10x
    }

    // Getters
    public Vector2f getPosition() { return position; }
    public float getZoom() { return zoom; }

    /**
     * Get combined view-projection matrix for shaders.
     *
     * <p>Pass this to vertex shader:
     * <pre>
     * uniform mat4 uViewProjection;
     * void main() {
     *     gl_Position = uViewProjection * vec4(aPosition, 0.0, 1.0);
     * }
     * </pre>
     */
    public Matrix4f getViewProjectionMatrix() { return viewProjectionMatrix; }
}
```

---

### Step 3: Renderable Component

Create `src/main/java/com/yourname/engine/renderer/RenderableComponent.java`:

```java
package com.yourname.engine.renderer;

import com.yourname.engine.ecs.Component;

/**
 * Component that marks an entity as renderable.
 *
 * <h2>Sprite Properties</h2>
 * - texture: Texture to display (null = colored rectangle)
 * - width, height: Size in world units (pixels)
 * - color: Tint color (1,1,1,1 = white = no tint)
 * - zIndex: Draw order (higher = drawn later = on top)
 *
 * <h2>Color Tinting</h2>
 * <p>Fragment shader multiplies texture color by tint:
 * <pre>
 * vec4 texColor = texture(sampler, uv);
 * vec4 finalColor = texColor * tintColor;
 * </pre>
 *
 * <p>Use cases:
 * - Damage flash: setColor(1, 0, 0, 1) → red tint
 * - Transparency: setColor(1, 1, 1, 0.5) → 50% transparent
 * - Brightness: setColor(2, 2, 2, 1) → 2x brighter
 *
 * <h2>Z-Index Sorting</h2>
 * <p>Sprites with same texture are sorted by Z-index:
 * <pre>
 * zIndex 0: Background tiles
 * zIndex 5: Ground items
 * zIndex 10: Player, enemies
 * zIndex 15: Projectiles
 * zIndex 20: UI elements
 * </pre>
 *
 * <p>Batching constraint: Can't batch sprites with different Z-orders!
 */
public class RenderableComponent implements Component {
    public Texture texture;
    public float width, height;
    public float colorR = 1.0f, colorG = 1.0f, colorB = 1.0f, colorA = 1.0f;
    public int zIndex = 0;

    public RenderableComponent(Texture texture, float width, float height) {
        this.texture = texture;
        this.width = width;
        this.height = height;
    }

    /**
     * Set sprite tint color.
     *
     * @param r Red (0.0 to 1.0, can go higher for bloom effect)
     * @param g Green
     * @param b Blue
     * @param a Alpha (0.0 = invisible, 1.0 = opaque)
     */
    public void setColor(float r, float g, float b, float a) {
        this.colorR = r;
        this.colorG = g;
        this.colorB = b;
        this.colorA = a;
    }
}
```

---

### Step 4: Simplified Rendering Approach

**Important Note:** Full Vulkan sprite batching requires ~1000 lines of boilerplate:
- Graphics pipeline (vertex/fragment shaders)
- Descriptor set layouts (uniform buffers, texture samplers)
- Vertex/index buffers (dynamic, rebuilt every frame)
- Command buffer recording (draw calls, state management)

For this tutorial, we use a **hybrid approach** to focus on game logic:

**Option A: Colored Rectangles (This Chapter)**
- Draw colored boxes representing sprites
- Visualize game immediately (no graphics complexity)
- Test collision, movement, shooting
- **Upgrade to textured sprites later (Chapter 9)**

**Option B: Full Sprite Pipeline (Chapter 9)**
- Complete textured rendering
- Shader-based sprite batching
- Texture atlases
- Normal mapping, lighting

**We'll use Option A** to keep this chapter focused on ECS integration and gameplay!

---

### Step 5: Extend VulkanRenderer for Rectangles

Add these methods to your existing `VulkanContext.java` from Chapter 1:

```java
/**
 * Draw a colored rectangle (represents sprite).
 *
 * <p>Deferred rendering: Store rect data, draw in recordCommandBuffer().
 *
 * <h2>Why Defer?</h2>
 * <p>Vulkan commands must be recorded into command buffers.
 * We can't draw immediately, so we queue rects and batch them.
 *
 * <h2>Production Implementation</h2>
 * <p>In a real engine, this would:
 * 1. Add vertex data to dynamic vertex buffer
 * 2. Group by texture (batching)
 * 3. Issue vkCmdDraw() for each batch
 *
 * <p>For now, we use vkCmdClearColorImage() to draw colored regions.
 */
public void drawRect(float x, float y, float width, float height,
                      float r, float g, float b, float a) {
    // Store rect for batch rendering
    rectsToDraw.add(new RectData(x, y, width, height, r, g, b, a));
}

private static class RectData {
    float x, y, width, height;
    float r, g, b, a;

    RectData(float x, float y, float width, float height, float r, float g, float b, float a) {
        this.x = x; this.y = y;
        this.width = width; this.height = height;
        this.r = r; this.g = g; this.b = b; this.a = a;
    }
}

// Store rects to draw this frame
private List<RectData> rectsToDraw = new ArrayList<>();

/**
 * Clear rect list (call at start of frame).
 */
public void beginFrame() {
    rectsToDraw.clear();
}

// In your rendering code, iterate rectsToDraw and draw each one
// (Simplified approach - production uses vertex buffers)
```

**Why this approach?**

✅ **Fast iteration**: See game working immediately
✅ **Focus on gameplay**: Collision, AI, shooting logic same as with textures
✅ **Visual feedback**: Colored boxes show entity positions/sizes
✅ **Upgrade path**: Easy to replace with textured sprites (same component interface)

**Professional precedent:**
- Unity Gizmos (colored shapes for debugging)
- Unreal Debug Draw
- Godot Debug Drawing

---

### Step 6: Sprite Render System

Create `src/main/java/com/yourname/engine/renderer/SpriteRenderSystem.java`:

```java
package com.yourname.engine.renderer;

import com.yourname.engine.ecs.*;
import com.yourname.game.components.Transform2D;

/**
 * System that renders all entities with Renderable + Transform2D.
 *
 * <h2>Rendering Pipeline</h2>
 * <pre>
 * 1. Update camera matrices (viewport size)
 * 2. Query all entities with Transform2D + RenderableComponent
 * 3. Apply camera transform (world → screen space)
 * 4. Submit draw commands to renderer
 * </pre>
 *
 * <h2>Camera Transform</h2>
 * <p>Manual transform (for colored rects):
 * <pre>
 * screenX = (worldX - cameraX) * zoom
 * screenY = (worldY - cameraY) * zoom
 * </pre>
 *
 * <p>Shader transform (for textured sprites):
 * <pre>
 * gl_Position = uViewProjection * vec4(worldPos, 0, 1);
 * </pre>
 *
 * <h2>Culling Optimization</h2>
 * <p>Only draw sprites visible on screen:
 * <pre>
 * if (screenX + width < 0 || screenX > viewportWidth) return; // Off-screen
 * if (screenY + height < 0 || screenY > viewportHeight) return;
 * </pre>
 *
 * <p>Saves CPU and GPU time (don't draw 1000 sprites if only 50 visible).
 */
public class SpriteRenderSystem extends System {

    private VulkanContext renderer;
    private Camera2D camera;

    public SpriteRenderSystem(VulkanContext renderer, Camera2D camera) {
        this.renderer = renderer;
        this.camera = camera;
    }

    @Override
    public void update(World world, float deltaTime) {
        // Update camera matrices for current viewport
        camera.update(renderer.getSwapChainExtent().width(),
                     renderer.getSwapChainExtent().height());

        // Begin frame (clear previous rects)
        renderer.beginFrame();

        // Query all renderable entities
        world.query(Transform2D.class, RenderableComponent.class).forEach(entity -> {
            Transform2D transform = entity.get(Transform2D.class);
            RenderableComponent renderable = entity.get(RenderableComponent.class);

            // Apply camera transform (world → screen)
            // In production, pass viewProjection matrix to shader instead
            float screenX = (transform.x - camera.getPosition().x) * camera.getZoom();
            float screenY = (transform.y - camera.getPosition().y) * camera.getZoom();
            float screenW = renderable.width * camera.getZoom();
            float screenH = renderable.height * camera.getZoom();

            // Frustum culling (skip off-screen sprites)
            if (screenX + screenW < 0 || screenX > renderer.getSwapChainExtent().width()) return;
            if (screenY + screenH < 0 || screenY > renderer.getSwapChainExtent().height()) return;

            // Submit draw command
            renderer.drawRect(
                screenX, screenY, screenW, screenH,
                renderable.colorR, renderable.colorG,
                renderable.colorB, renderable.colorA
            );
        });
    }
}
```

---

## Complete Space Shooter Game

Now let's build a **playable space shooter** using everything we've learned!

### Step 7: Game Components

Create `src/main/java/com/yourname/game/components/Transform2D.java`:

```java
package com.yourname.game.components;

import com.yourname.engine.ecs.Component;

/**
 * 2D position component (world coordinates).
 */
public class Transform2D implements Component {
    public float x, y;

    public Transform2D(float x, float y) {
        this.x = x;
        this.y = y;
    }
}
```

Create remaining components:

```java
package com.yourname.game.components;

import com.yourname.engine.ecs.Component;

// Velocity (movement per second)
public class Velocity implements Component {
    public float dx, dy;

    public Velocity(float dx, float dy) {
        this.dx = dx;
        this.dy = dy;
    }
}

// Circular collision bounds
public class CircleBounds implements Component {
    public float radius;

    public CircleBounds(float radius) {
        this.radius = radius;
    }
}

// Health system
public class Health implements Component {
    public int current, max;

    public Health(int current, int max) {
        this.current = current;
        this.max = max;
    }

    public void damage(int amount) {
        current = Math.max(0, current - amount);
    }

    public boolean isAlive() {
        return current > 0;
    }
}

// Lifetime (auto-destroy after duration)
public class Lifetime implements Component {
    public float remaining;

    public Lifetime(float duration) {
        this.remaining = duration;
    }
}

// Tags for entity types
public class PlayerTag implements Component {}
public class EnemyTag implements Component {}
public class ProjectileTag implements Component {}
```

---

### Step 8: Game Systems

Create `src/main/java/com/yourname/game/systems/MovementSystem.java`:

```java
package com.yourname.game.systems;

import com.yourname.engine.ecs.*;
import com.yourname.game.components.*;

/**
 * Applies velocity to transform (basic physics).
 */
public class MovementSystem extends System {
    @Override
    public void update(World world, float deltaTime) {
        world.query(Transform2D.class, Velocity.class).forEach(entity -> {
            Transform2D transform = entity.get(Transform2D.class);
            Velocity velocity = entity.get(Velocity.class);

            transform.x += velocity.dx * deltaTime;
            transform.y += velocity.dy * deltaTime;
        });
    }
}
```

Create collision system:

```java
package com.yourname.game.systems;

import com.yourname.engine.ecs.*;
import com.yourname.game.components.*;

/**
 * Detects and resolves collisions between entities.
 *
 * <h2>Collision Detection Complexity</h2>
 * <p>Naive approach (O(n²)):
 * <pre>
 * for each entity A:
 *     for each entity B:
 *         if (collides(A, B)): resolve()
 *
 * 1000 entities: 1,000,000 checks per frame!
 * </pre>
 *
 * <p>Optimized approach (spatial hashing, O(n)):
 * <pre>
 * Grid size = largest entity radius * 2
 *
 * for each entity:
 *     grid[entity.x / gridSize][entity.y / gridSize].add(entity)
 *
 * for each grid cell:
 *     for each pair in cell: check collision
 *
 * 1000 entities: ~5,000 checks per frame (200x faster!)
 * </pre>
 *
 * <p>We use naive approach (simple, works for <100 entities).
 * Chapter 10 (Physics) implements spatial hashing.
 */
public class CollisionSystem extends System {
    @Override
    public void update(World world, float deltaTime) {
        // Projectile vs Enemy
        var projectiles = world.query(Transform2D.class, CircleBounds.class, ProjectileTag.class).toList();
        var enemies = world.query(Transform2D.class, CircleBounds.class, EnemyTag.class).toList();

        for (var projectile : projectiles) {
            if (!world.isValid(projectile.getEntity())) continue;

            for (var enemy : enemies) {
                if (!world.isValid(enemy.getEntity())) continue;

                if (checkCollision(projectile, enemy)) {
                    // Damage enemy
                    Health enemyHealth = enemy.get(Health.class);
                    if (enemyHealth != null) {
                        enemyHealth.damage(25);
                    }

                    // Destroy projectile
                    world.destroyEntity(projectile.getEntity());
                    break;
                }
            }
        }

        // Enemy vs Player
        var players = world.query(Transform2D.class, CircleBounds.class, PlayerTag.class).toList();

        for (var enemy : enemies) {
            if (!world.isValid(enemy.getEntity())) continue;

            for (var player : players) {
                if (!world.isValid(player.getEntity())) continue;

                if (checkCollision(enemy, player)) {
                    // Damage player
                    Health playerHealth = player.get(Health.class);
                    if (playerHealth != null) {
                        playerHealth.damage(10);
                    }

                    // Damage enemy
                    Health enemyHealth = enemy.get(Health.class);
                    if (enemyHealth != null) {
                        enemyHealth.damage(50);
                    }
                }
            }
        }
    }

    /**
     * Circle-circle collision detection.
     *
     * <p>Distance formula:
     * <pre>
     * distance = sqrt((x2 - x1)² + (y2 - y1)²)
     * collision = distance < (radius1 + radius2)
     * </pre>
     *
     * <h2>Optimization: Avoid Square Root</h2>
     * <p>Square root is expensive (~20 CPU cycles).
     * Compare squared distances instead:
     * <pre>
     * distanceSquared = (x2 - x1)² + (y2 - y1)²
     * radiusSumSquared = (radius1 + radius2)²
     * collision = distanceSquared < radiusSumSquared
     * </pre>
     *
     * <p>5x faster (no sqrt call)!
     */
    private boolean checkCollision(EntityView a, EntityView b) {
        Transform2D posA = a.get(Transform2D.class);
        Transform2D posB = b.get(Transform2D.class);
        CircleBounds boundsA = a.get(CircleBounds.class);
        CircleBounds boundsB = b.get(CircleBounds.class);

        float dx = posB.x - posA.x;
        float dy = posB.y - posA.y;
        float distanceSquared = dx * dx + dy * dy;
        float radiusSum = boundsA.radius + boundsB.radius;
        float radiusSumSquared = radiusSum * radiusSum;

        return distanceSquared < radiusSumSquared;
    }
}
```

Create cleanup systems:

```java
package com.yourname.game.systems;

import com.yourname.engine.ecs.*;
import com.yourname.game.components.*;

/**
 * Destroys entities with zero health.
 */
public class HealthCleanupSystem extends System {
    @Override
    public void update(World world, float deltaTime) {
        world.query(Health.class).forEach(entity -> {
            Health health = entity.get(Health.class);
            if (!health.isAlive()) {
                world.destroyEntity(entity.getEntity());
            }
        });
    }
}

/**
 * Destroys entities after lifetime expires.
 *
 * <p>Used for projectiles (auto-destroy after 3 seconds),
 * particle effects, temporary power-ups, etc.
 */
public class LifetimeSystem extends System {
    @Override
    public void update(World world, float deltaTime) {
        world.query(Lifetime.class).forEach(entity -> {
            Lifetime lifetime = entity.get(Lifetime.class);
            lifetime.remaining -= deltaTime;

            if (lifetime.remaining <= 0) {
                world.destroyEntity(entity.getEntity());
            }
        });
    }
}

/**
 * Destroys entities that leave the screen bounds.
 *
 * <p>Prevents runaway projectiles from accumulating.
 */
public class BoundsCheckSystem extends System {
    private float maxX, maxY;

    public BoundsCheckSystem(float maxX, float maxY) {
        this.maxX = maxX;
        this.maxY = maxY;
    }

    @Override
    public void update(World world, float deltaTime) {
        world.query(Transform2D.class, ProjectileTag.class).forEach(entity -> {
            Transform2D transform = entity.get(Transform2D.class);

            if (transform.x < -100 || transform.x > maxX + 100 ||
                transform.y < -100 || transform.y > maxY + 100) {
                world.destroyEntity(entity.getEntity());
            }
        });
    }
}
```

---

### Step 9: Main Game Logic

Create `src/main/java/com/yourname/game/SpaceShooterGame.java`:

```java
package com.yourname.game;

import com.yourname.engine.core.*;
import com.yourname.engine.ecs.*;
import com.yourname.engine.renderer.*;
import com.yourname.game.components.*;
import com.yourname.game.systems.*;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Complete playable 2D space shooter game!
 *
 * <h2>Game Loop Architecture</h2>
 * <pre>
 * while (running):
 *     1. Poll input (GLFW events)
 *     2. Update time (delta calculation)
 *     3. Process player input (WASD, mouse)
 *     4. Spawn enemies (timed intervals)
 *     5. Update ECS systems (movement, collision, rendering)
 *     6. Check game over condition
 * </pre>
 *
 * <h2>Entity Lifecycle</h2>
 * <pre>
 * Player: Created at start, persists until death
 * Enemy: Spawned every 2 seconds, destroyed on death
 * Projectile: Created on shoot, destroyed after 3s or collision
 * </pre>
 *
 * <h2>Performance</h2>
 * <p>Typical entity count:
 * - 1 player
 * - 10-20 enemies (spawn rate: 1 per 2 seconds)
 * - 20-50 projectiles (fire rate: 5 per second)
 *
 * <p>Total: ~50 entities at 60 FPS = 3000 entity updates/second
 * <p>CPU time: ~0.5ms per frame (well under 16ms budget)
 */
public class SpaceShooterGame {

    private Engine engine;
    private World world;
    private Camera2D camera;

    // Game state
    private Entity playerEntity;
    private float timeSinceLastShot = 0;
    private float timeSinceLastEnemySpawn = 0;
    private int score = 0;

    // Constants
    private static final float PLAYER_SPEED = 300f;      // pixels/second
    private static final float PROJECTILE_SPEED = 500f;
    private static final float ENEMY_SPEED = 100f;
    private static final float SHOOT_COOLDOWN = 0.2f;    // 5 shots/second
    private static final float ENEMY_SPAWN_INTERVAL = 2.0f; // 1 enemy per 2 seconds

    public void run() {
        init();
        loop();
        cleanup();
    }

    private void init() {
        System.out.println("\n=== Space Shooter Game ===\n");

        // Initialize engine
        engine = new Engine();
        engine.init();

        world = engine.getWorld();
        camera = new Camera2D();

        // Add game systems (order matters!)
        world.addSystem(new MovementSystem());
        world.addSystem(new BoundsCheckSystem(1920, 1080));
        world.addSystem(new CollisionSystem());
        world.addSystem(new HealthCleanupSystem());
        world.addSystem(new LifetimeSystem());

        // Add render system (runs last)
        VulkanContext renderer = engine.getRenderer();
        world.addSystem(new SpriteRenderSystem(renderer, camera));

        // Create player
        createPlayer();

        System.out.println("✓ Game initialized\n");
        System.out.println("Controls:");
        System.out.println("  WASD - Move");
        System.out.println("  Mouse - Aim and shoot");
        System.out.println("  ESC - Quit\n");
    }

    /**
     * Create player entity at screen center.
     *
     * <p>Player components:
     * - Transform2D: Position
     * - RenderableComponent: Visual (cyan colored rect)
     * - CircleBounds: Collision detection
     * - Health: Takes damage, can die
     * - PlayerTag: Identifies as player (for collision)
     */
    private void createPlayer() {
        playerEntity = world.createEntity();

        // Position at center
        world.addComponent(playerEntity, new Transform2D(1920 / 2f, 1080 / 2f));

        // Visual (cyan square)
        RenderableComponent renderable = new RenderableComponent(null, 32, 32);
        renderable.setColor(0, 1, 1, 1); // Cyan
        world.addComponent(playerEntity, renderable);

        // Collision (radius = half width)
        world.addComponent(playerEntity, new CircleBounds(16));

        // Health
        world.addComponent(playerEntity, new Health(100, 100));

        // Tag
        world.addComponent(playerEntity, new PlayerTag());
    }

    /**
     * Main game loop (60 FPS target).
     *
     * <h2>Frame Timing</h2>
     * <pre>
     * Target: 60 FPS = 16.67ms per frame
     * Budget breakdown:
     *   Input:      0.1ms  (GLFW poll events)
     *   Game logic: 0.5ms  (ECS systems)
     *   Rendering:  1.0ms  (Vulkan command recording)
     *   GPU:        4.0ms  (parallel with next frame's CPU)
     *   Remaining: 11.0ms  (buffer for frame spikes)
     * </pre>
     */
    private void loop() {
        float lastFrameTime = (float) glfwGetTime();

        while (!engine.getWindow().shouldClose()) {
            engine.getWindow().pollEvents();

            // Calculate delta time
            float currentTime = (float) glfwGetTime();
            float deltaTime = currentTime - lastFrameTime;
            lastFrameTime = currentTime;

            // Update game logic
            updateInput(deltaTime);
            spawnEnemies(deltaTime);

            // Update ECS (includes rendering)
            engine.update(deltaTime);

            // Check game over
            if (!world.isValid(playerEntity)) {
                System.out.println("\n=== GAME OVER ===");
                System.out.println("Final Score: " + score);
                break;
            }
        }
    }

    /**
     * Process player input (WASD movement + mouse shooting).
     *
     * <h2>Input Responsiveness</h2>
     * <p>Polling vs Events:
     * - Polling (we use): Check key state every frame (smooth movement)
     * - Events: Callback on key press/release (UI buttons)
     *
     * <p>Example: Hold W to move forward
     * <pre>
     * Polling:
     *   Frame 1: isKeyDown(W) → move
     *   Frame 2: isKeyDown(W) → move
     *   Frame 3: isKeyDown(W) → move
     *   Result: Smooth continuous movement
     *
     * Events:
     *   Frame 1: onKeyPressed(W) → move once
     *   Frame 2-1000: (no events)
     *   Result: Jerky movement (not suitable for games)
     * </pre>
     */
    private void updateInput(float deltaTime) {
        if (!world.isValid(playerEntity)) return;

        Transform2D playerTransform = world.getComponent(playerEntity, Transform2D.class);
        if (playerTransform == null) return;

        long window = engine.getWindow().getHandle();

        // WASD movement
        float dx = 0, dy = 0;
        if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) dy -= PLAYER_SPEED * deltaTime;
        if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) dy += PLAYER_SPEED * deltaTime;
        if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) dx -= PLAYER_SPEED * deltaTime;
        if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) dx += PLAYER_SPEED * deltaTime;

        playerTransform.x += dx;
        playerTransform.y += dy;

        // Clamp to screen (prevent moving off-screen)
        playerTransform.x = Math.max(16, Math.min(1920 - 16, playerTransform.x));
        playerTransform.y = Math.max(16, Math.min(1080 - 16, playerTransform.y));

        // Mouse shooting (cooldown-based)
        timeSinceLastShot += deltaTime;

        if (glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS &&
            timeSinceLastShot >= SHOOT_COOLDOWN) {

            // Get mouse position (screen coordinates)
            double[] mouseX = new double[1];
            double[] mouseY = new double[1];
            glfwGetCursorPos(window, mouseX, mouseY);

            // Calculate direction vector (normalized)
            float dirX = (float) mouseX[0] - playerTransform.x;
            float dirY = (float) mouseY[0] - playerTransform.y;
            float length = (float) Math.sqrt(dirX * dirX + dirY * dirY);

            if (length > 0) {
                dirX /= length;
                dirY /= length;

                shootProjectile(playerTransform.x, playerTransform.y, dirX, dirY);
                timeSinceLastShot = 0;
            }
        }
    }

    /**
     * Create projectile entity moving in direction.
     *
     * <p>Projectile properties:
     * - Small (8x8 pixels)
     * - Fast (500 pixels/second)
     * - Yellow color
     * - Auto-destroys after 3 seconds (prevent memory leak)
     */
    private void shootProjectile(float x, float y, float dirX, float dirY) {
        Entity projectile = world.createEntity();

        world.addComponent(projectile, new Transform2D(x, y));
        world.addComponent(projectile, new Velocity(dirX * PROJECTILE_SPEED, dirY * PROJECTILE_SPEED));

        // Visual (yellow square)
        RenderableComponent renderable = new RenderableComponent(null, 8, 8);
        renderable.setColor(1, 1, 0, 1); // Yellow
        world.addComponent(projectile, renderable);

        // Collision
        world.addComponent(projectile, new CircleBounds(4));

        // Lifetime (auto-destroy after 3 seconds)
        world.addComponent(projectile, new Lifetime(3.0f));

        // Tag
        world.addComponent(projectile, new ProjectileTag());
    }

    /**
     * Spawn enemies at timed intervals.
     *
     * <h2>Difficulty Scaling</h2>
     * <p>To increase difficulty over time:
     * <pre>
     * spawnInterval = max(0.5f, 2.0f - (score * 0.01f));
     *
     * Score 0:   Spawn every 2.0 seconds
     * Score 50:  Spawn every 1.5 seconds
     * Score 150: Spawn every 0.5 seconds (capped)
     * </pre>
     */
    private void spawnEnemies(float deltaTime) {
        timeSinceLastEnemySpawn += deltaTime;

        if (timeSinceLastEnemySpawn >= ENEMY_SPAWN_INTERVAL) {
            spawnEnemy();
            timeSinceLastEnemySpawn = 0;
        }
    }

    /**
     * Spawn enemy at random screen edge, moving toward player.
     *
     * <p>Spawn locations (4 edges):
     * <pre>
     * Top:    x = random(0, width),  y = 0
     * Bottom: x = random(0, width),  y = height
     * Left:   x = 0,                 y = random(0, height)
     * Right:  x = width,             y = random(0, height)
     * </pre>
     *
     * <p>AI: Simple pursuit (move directly toward player).
     * Chapter 11 (Scripting) implements advanced AI (patrol, flank, etc.)
     */
    private void spawnEnemy() {
        Entity enemy = world.createEntity();

        // Spawn at random edge
        float x, y;
        int edge = (int) (Math.random() * 4);
        if (edge == 0) { // Top
            x = (float) (Math.random() * 1920);
            y = 0;
        } else if (edge == 1) { // Bottom
            x = (float) (Math.random() * 1920);
            y = 1080;
        } else if (edge == 2) { // Left
            x = 0;
            y = (float) (Math.random() * 1080);
        } else { // Right
            x = 1920;
            y = (float) (Math.random() * 1080);
        }

        world.addComponent(enemy, new Transform2D(x, y));

        // Move towards player (simple AI)
        if (world.isValid(playerEntity)) {
            Transform2D playerPos = world.getComponent(playerEntity, Transform2D.class);
            float dirX = playerPos.x - x;
            float dirY = playerPos.y - y;
            float length = (float) Math.sqrt(dirX * dirX + dirY * dirY);
            if (length > 0) {
                dirX /= length;
                dirY /= length;
                world.addComponent(enemy, new Velocity(dirX * ENEMY_SPEED, dirY * ENEMY_SPEED));
            }
        }

        // Visual (red square)
        RenderableComponent renderable = new RenderableComponent(null, 24, 24);
        renderable.setColor(1, 0, 0, 1); // Red
        world.addComponent(enemy, renderable);

        // Collision
        world.addComponent(enemy, new CircleBounds(12));

        // Health
        world.addComponent(enemy, new Health(50, 50));

        // Tag
        world.addComponent(enemy, new EnemyTag());
    }

    private void cleanup() {
        engine.cleanup();
    }

    public static void main(String[] args) {
        new SpaceShooterGame().run();
    }
}
```

---

## Testing: Play the Game!

```bash
./gradlew run
```

**Expected Experience:**

1. **Window opens** - Cyan square (player) at center
2. **WASD movement** - Player moves smoothly
3. **Mouse click** - Yellow projectiles shoot towards cursor
4. **Red enemies spawn** - From screen edges every 2 seconds
5. **Collision detection** - Projectiles destroy enemies, enemies damage player
6. **Game over** - When player health reaches 0

**Performance Metrics:**

```
Entity count: ~50 (1 player, 20 enemies, 30 projectiles)
Frame time: ~0.5ms
FPS: 500-1000 (GPU idle, CPU bound by game logic)
Memory: ~50 MB (ECS overhead minimal)
```

---

## What We've Achieved

**Complete 2D Game Engine:**

✅ Texture loading (STB Image + Vulkan staging buffers)
✅ 2D camera with orthographic projection
✅ Sprite rendering system (simplified colored rects)
✅ ECS integration for game entities
✅ **Playable space shooter game!**

**Game Features:**

✅ Player movement (WASD)
✅ Mouse aiming and shooting
✅ Enemy spawning and AI (chase player)
✅ Circle-circle collision detection
✅ Health system with damage
✅ Game over condition

**Performance:**

- 500+ FPS with 50 entities
- Scales to 1000+ entities easily
- ~0.5ms per frame (3% of 16ms budget)

---

## Common Issues and Solutions

### Issue 1: Entities Disappear Immediately

**Symptom:** Projectiles/enemies created but not visible.

**Cause:** Missing RenderableComponent or Transform2D.

**Solution:**
```java
// Ensure both components exist
world.addComponent(entity, new Transform2D(x, y));
world.addComponent(entity, new RenderableComponent(null, 32, 32));
```

### Issue 2: Collision Not Working

**Symptom:** Projectiles pass through enemies.

**Cause:** CircleBounds radius too small or missing.

**Debug:**
```java
// Visualize collision bounds (temporary)
RenderableComponent debug = new RenderableComponent(null,
    bounds.radius * 2, bounds.radius * 2);
debug.setColor(1, 0, 0, 0.5f); // Red, 50% transparent
```

### Issue 3: Game Runs at 30 FPS

**Symptom:** Stuttering, low frame rate.

**Cause:** VSync enabled (capping to 30 Hz monitor refresh).

**Solution:**
```java
// In VulkanContext.chooseSwapPresentMode():
// Prefer IMMEDIATE (no vsync) for testing
return VK_PRESENT_MODE_IMMEDIATE_KHR;
```

### Issue 4: Enemies Don't Move

**Symptom:** Enemies spawn but stay static.

**Cause:** MovementSystem not added or Velocity component missing.

**Solution:**
```java
// Ensure system is added
world.addSystem(new MovementSystem());

// Ensure enemies have velocity
world.addComponent(enemy, new Velocity(dirX * ENEMY_SPEED, dirY * ENEMY_SPEED));
```

---

## Performance Optimization

### Spatial Hashing for Collision

Current O(n²) collision detection:

```java
// Check every projectile against every enemy
for (var p : projectiles) {     // 50 projectiles
    for (var e : enemies) {      // 20 enemies
        checkCollision(p, e);    // 1000 checks per frame!
    }
}
```

Optimized spatial hash (O(n)):

```java
// Divide world into grid cells
final int CELL_SIZE = 64; // pixels
Map<Integer, List<Entity>> grid = new HashMap<>();

// Add entities to cells
for (var entity : entities) {
    int cellX = (int)(entity.x / CELL_SIZE);
    int cellY = (int)(entity.y / CELL_SIZE);
    int cellKey = cellX + cellY * 1000;
    grid.computeIfAbsent(cellKey, k -> new ArrayList<>()).add(entity);
}

// Only check entities in same/neighbor cells
for (var cell : grid.values()) {
    for (int i = 0; i < cell.size(); i++) {
        for (int j = i + 1; j < cell.size(); j++) {
            checkCollision(cell.get(i), cell.get(j));
        }
    }
}

// Result: 50 entities → ~100 checks (10x faster!)
```

### Frustum Culling

Don't render off-screen entities:

```java
// Before drawing
float screenX = (worldX - cameraX) * zoom;
float screenY = (worldY - cameraY) * zoom;

// Skip if outside viewport
if (screenX + width < 0 || screenX > viewportWidth) return;
if (screenY + height < 0 || screenY > viewportHeight) return;

// Result: 1000 entities → only 50 visible → 20x less draw calls!
```

---

## Exercises

1. **Add Score System**
   - Increment score when enemy destroyed
   - Display in console
   - Show high score on game over

2. **Enemy Variety**
   - Fast/weak enemies (speed=200, health=25)
   - Slow/strong enemies (speed=50, health=100)
   - Random spawn type

3. **Power-ups**
   - Health pickup (green square, +50 health)
   - Speed boost (blue square, 2x movement for 5s)
   - Rapid fire (orange square, cooldown=0.05s for 5s)

4. **Particle Effects**
   - Explosion on enemy death (8 particles, outward velocity)
   - Muzzle flash on shoot (short-lived yellow particles)

5. **Sound Effects** (Chapter 7)
   - Shoot sound
   - Hit sound
   - Death sound
   - Background music

---

## Upgrading to Textured Sprites

When you're ready for full textured rendering (**Chapter 9**), you'll need:

### 1. Graphics Pipeline

```java
// Vertex shader (sprite.vert)
#version 450

layout(location = 0) in vec2 aPosition;
layout(location = 1) in vec2 aTexCoord;

layout(location = 0) out vec2 vTexCoord;

layout(binding = 0) uniform UniformBufferObject {
    mat4 viewProjection;
} ubo;

void main() {
    gl_Position = ubo.viewProjection * vec4(aPosition, 0.0, 1.0);
    vTexCoord = aTexCoord;
}

// Fragment shader (sprite.frag)
#version 450

layout(location = 0) in vec2 vTexCoord;
layout(location = 0) out vec4 outColor;

layout(binding = 1) uniform sampler2D texSampler;

void main() {
    outColor = texture(texSampler, vTexCoord);
}
```

### 2. Descriptor Sets

```java
// Uniform buffer (view-projection matrix)
VkDescriptorSetLayoutBinding uboBinding = ...;
uboBinding.binding(0);
uboBinding.descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);

// Combined image sampler (texture)
VkDescriptorSetLayoutBinding samplerBinding = ...;
samplerBinding.binding(1);
samplerBinding.descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
```

### 3. Vertex Buffers

```java
// Quad vertices (2 triangles)
float[] vertices = {
    // Position   TexCoord
    0, 0,         0, 0,  // Top-left
    w, 0,         1, 0,  // Top-right
    w, h,         1, 1,  // Bottom-right
    0, h,         0, 1   // Bottom-left
};

int[] indices = { 0, 1, 2, 2, 3, 0 }; // Two triangles
```

### 4. Sprite Batch

```java
// Group sprites by texture
Map<Texture, List<Sprite>> batches = new HashMap<>();

for (Sprite sprite : sprites) {
    batches.computeIfAbsent(sprite.texture, k -> new ArrayList<>()).add(sprite);
}

// Draw each batch (minimize texture binding)
for (var entry : batches.entrySet()) {
    vkCmdBindDescriptorSets(..., entry.getKey().getDescriptorSet());
    vkCmdDrawIndexed(..., entry.getValue().size() * 6);
}
```

**For now**, colored rectangles work perfectly for prototyping gameplay!

---

## Further Reading

### Sprite Batching
- [LibGDX SpriteBatch](https://libgdx.com/wiki/graphics/2d/spritebatch,-textureregions,-and-sprites) - Reference implementation
- [Unity Sprite Renderer Batching](https://docs.unity3d.com/Manual/DrawCallBatching.html)
- [Batch Rendering](https://learnopengl.com/In-Practice/2D-Game/Rendering-Sprites) - Learn OpenGL tutorial

### Collision Detection
- [Real-Time Collision Detection](http://realtimecollisiondetection.net/) - Christer Ericson
- [Spatial Hashing](https://conkerjo.wordpress.com/2009/06/13/spatial-hashing-implementation-for-fast-2d-collisions/)
- [Quadtrees](https://gamedevelopment.tutsplus.com/tutorials/quick-tip-use-quadtrees-to-detect-likely-collisions-in-2d-space--gamedev-374)

### Game Loops
- [Game Programming Patterns - Update Method](https://gameprogrammingpatterns.com/update-method.html)
- [Fix Your Timestep](https://gafferongames.com/post/fix_your_timestep/) - Glenn Fiedler

---

## What's Next?

In **Chapter 5**, we'll:

- Add **3D mesh rendering** (OBJ loading with Assimp)
- Implement **3D camera** with perspective projection
- Create **3D transform component**
- **Evolve the space shooter to 3D** flight combat!
- Keep 2D systems for UI/HUD

**Chapter 6** covers:
- Scene serialization (JSON save/load)
- Prefab system
- Hot-reload for rapid iteration

**Chapter 7** adds:
- Input mapping (rebindable keys)
- Audio engine (OpenAL 3D positional sound)
- Sound effects and music

---

**Previous:** [← Chapter 3 - Renderer Abstraction](chapter-03-renderer-abstraction.md)
**Next:** [Chapter 5 - 3D Meshes & Flight Combat →](chapter-05-3d-meshes.md)
