# Chapter 9: Advanced Rendering - PBR, Lighting, and Textures

## What You'll Learn

In this chapter, we'll build a **production-quality rendering system** with photorealistic lighting. You'll understand:

- **How the Vulkan graphics pipeline works** and why it's different from OpenGL
- **What descriptor sets are** and how they connect shaders to data
- **How SPIR-V compilation works** and why we use it
- **What Physically Based Rendering (PBR) is** and how the Cook-Torrance BRDF creates realistic materials
- **How professional engines handle lighting** (comparing to Unity, Unreal, Godot)
- **Why we separate vertex and fragment shaders** and what each stage does
- **How memory types work in Vulkan** (device-local vs host-visible)

By the end, you'll have rendering quality comparable to Unity and Unreal Engine!

---

## The Big Picture: Modern Graphics Pipelines

### OpenGL vs Vulkan: A Fundamental Shift

**OpenGL (the old way):**
```java
// OpenGL: Simple but opaque
glUseProgram(shader);
glBindTexture(GL_TEXTURE_2D, texture);
glDrawArrays(GL_TRIANGLES, 0, vertexCount);
```

**Problems with OpenGL:**
- **Hidden state machine**: Your draw call might behave differently based on 100+ pieces of hidden global state
- **Driver black box**: The driver does optimization you can't see or control
- **Limited parallelism**: Hard to record commands on multiple threads
- **Validation only in debug mode**: Errors discovered late

**Vulkan (the new way):**
```java
// Vulkan: Explicit control over everything
createPipeline(shaders, vertexFormat, blending, depthTest, ...);
recordCommandBuffer(pipeline, descriptorSets, vertexBuffers, ...);
submitCommandBuffer(queue);
```

**Benefits of Vulkan:**
- **Explicit state**: Everything is specified upfront in the pipeline
- **Precompiled pipelines**: Create once, use many times (fast!)
- **Multi-threaded command recording**: Scale across CPU cores
- **Validation layers**: Catch errors immediately during development

**Analogy:**
- **OpenGL**: Like giving vague instructions to a contractor who makes hidden decisions
- **Vulkan**: Like providing detailed blueprints - more work upfront, but total control and better performance

### The Vulkan Graphics Pipeline

The graphics pipeline is a **fixed sequence** of stages that transform 3D models into pixels:

```
┌─────────────────┐
│  Vertex Input   │ ← Load vertex data (position, normal, etc.)
└────────┬────────┘
         ↓
┌─────────────────┐
│ Vertex Shader   │ ← Transform vertices to clip space
└────────┬────────┘
         ↓
┌─────────────────┐
│ Rasterization   │ ← Convert triangles to pixels (fragments)
└────────┬────────┘
         ↓
┌─────────────────┐
│Fragment Shader  │ ← Calculate final pixel color (lighting, textures)
└────────┬────────┘
         ↓
┌─────────────────┐
│ Color Blending  │ ← Blend with existing framebuffer (alpha, additive, etc.)
└────────┬────────┘
         ↓
      Framebuffer
```

**Key Concept: The pipeline is immutable!**

Once created, you can't change individual settings. Want a different blend mode? Create a new pipeline. This seems restrictive, but it enables massive optimization:

- GPU drivers can optimize the entire pipeline as one unit
- Switching pipelines is a single command (no hidden state checks)
- Pipelines can be created in parallel on multiple threads

**Unity/Unreal Comparison:**

| Engine | Approach | Pipeline Creation |
|--------|----------|-------------------|
| **Unity** | Shader variants | Compiles combinations of keywords at build time |
| **Unreal** | Material editor | Generates shader code, creates pipelines on-demand |
| **JECS (Vulkan)** | Explicit | You create pipelines manually |

---

## Part 1: Graphics Pipeline Architecture

### Understanding SPIR-V: The Shader Intermediate Language

**What Is SPIR-V?**

SPIR-V (Standard Portable Intermediate Representation - Vulkan) is a **binary shader format** - the bytecode for GPU shaders.

**The Compilation Pipeline:**

```
┌────────────┐
│  GLSL Code │  ← Human-readable shader (pbr.vert)
└─────┬──────┘
      ↓ glslangValidator (compiler)
┌────────────┐
│ SPIR-V     │  ← Binary bytecode (vert.spv)
└─────┬──────┘
      ↓ Vulkan driver
┌────────────┐
│ GPU Code   │  ← Native GPU instructions
└────────────┘
```

**Why SPIR-V Instead of GLSL Text?**

1. **Platform independence**: Same SPIR-V runs on NVIDIA, AMD, Intel
2. **Faster loading**: No parsing/compilation at runtime
3. **Validation**: Errors caught at compile time, not runtime
4. **Optimization**: Tools can optimize SPIR-V before shipping

**Compile Your Shaders:**

```bash
glslangValidator -V shaders/pbr.vert -o shaders/vert.spv
glslangValidator -V shaders/pbr.frag -o shaders/frag.spv
```

**Unity Comparison:**
- **Unity**: Uses ShaderLab → generates HLSL → compiles to platform-specific bytecode
- **Unreal**: Uses HLSL → shader compiler → platform bytecode
- **JECS**: GLSL → SPIR-V → Vulkan drivers handle rest

---

### What Are Descriptor Sets?

**The Problem:** Shaders need data (uniforms, textures), but how do we bind it?

**OpenGL approach (implicit):**
```glsl
uniform mat4 model;        // Where does this data come from?
uniform sampler2D albedo;  // How do we set this?
```

**Vulkan approach (explicit):**
```glsl
layout(set = 0, binding = 0) uniform CameraUBO { mat4 view; mat4 projection; } camera;
layout(set = 0, binding = 4) uniform sampler2D albedoMap;
```

**Descriptor Sets Are Like Function Arguments**

Think of a shader as a function:

```java
// Shader function signature
void renderMesh(
    CameraUBO camera,        // binding 0
    ModelUBO model,          // binding 1
    MaterialUBO material,    // binding 2
    LightsUBO lights,        // binding 3
    Texture2D albedoMap,     // binding 4
    Texture2D normalMap,     // binding 5
    Texture2D metallicMap    // binding 6
);
```

The **descriptor set layout** is the function signature (defined once).
The **descriptor set** is the actual arguments you pass (can change per draw call).

**Why This Design?**

- **Performance**: GPU can prefetch all resources at once
- **Validation**: Driver knows exactly what data you need
- **Reusability**: Create layout once, use for many descriptor sets

**Professional Engine Comparison:**

| Engine | Descriptor Management |
|--------|----------------------|
| **Unity** | Automatic - shader reflection generates bindings |
| **Unreal** | Shader parameters - manual but high-level |
| **Godot** | RenderingDevice API - explicit like Vulkan |
| **JECS** | Manual descriptor sets - full control |

---

### VulkanPipeline.java - The Complete Pipeline

**What This Code Does:**

This class creates a complete graphics pipeline - the blueprint for how to render objects. It combines:
1. **Shaders** (vertex and fragment)
2. **Vertex format** (position, normal, texCoord, tangent)
3. **Rasterization settings** (face culling, polygon mode)
4. **Depth testing** (which pixels are in front?)
5. **Blending** (how to combine transparent colors)
6. **Descriptor layout** (what data the shaders need)

**Why This Is Complex:**

Modern GPUs are **parallel processing monsters**. To run efficiently, they need to know EVERYTHING about your rendering upfront. You can't change settings mid-frame like in OpenGL.

```java
package com.jecs.graphics.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.lwjgl.vulkan.VK10.*;

/**
 * Represents a complete Vulkan graphics pipeline with shaders,
 * vertex input, rasterization, and blending state.
 *
 * KEY CONCEPT: Pipelines are IMMUTABLE once created. To change settings
 * (like blend mode or shaders), you must create a new pipeline.
 *
 * This upfront cost enables massive GPU optimization!
 */
public class VulkanPipeline {

    private final VkDevice device;
    private long pipeline;
    private long pipelineLayout;
    private long descriptorSetLayout;

    // Shader modules (must be kept until pipeline is created)
    private long vertShaderModule;
    private long fragShaderModule;

    public VulkanPipeline(VkDevice device) {
        this.device = device;
    }

    /**
     * Creates a graphics pipeline for PBR rendering.
     *
     * WHAT THIS METHOD DOES:
     * 1. Loads compiled SPIR-V shaders from disk
     * 2. Defines vertex format (position, normal, texCoord, tangent)
     * 3. Configures rasterization (backface culling, solid fill)
     * 4. Sets up depth testing (closer objects occlude farther ones)
     * 5. Configures alpha blending (for transparent materials)
     * 6. Creates descriptor layout (bindings for uniforms/textures)
     * 7. Assembles everything into a pipeline object
     *
     * WHY SO MANY STEPS?
     * The GPU needs complete information to optimize. In OpenGL, the driver
     * guesses at your intent. In Vulkan, you specify everything explicitly.
     *
     * @param vertShaderPath Path to compiled SPIR-V vertex shader (vert.spv)
     * @param fragShaderPath Path to compiled SPIR-V fragment shader (frag.spv)
     * @param renderPass The render pass this pipeline will be used with
     * @param extent The viewport/scissor extent
     */
    public void create(String vertShaderPath, String fragShaderPath,
                      long renderPass, VkExtent2D extent) {
        try (MemoryStack stack = MemoryStack.stackPush()) {

            // STEP 1: Load and create shader modules
            // Shader modules wrap SPIR-V bytecode for Vulkan
            vertShaderModule = createShaderModule(vertShaderPath);
            fragShaderModule = createShaderModule(fragShaderPath);

            // STEP 2: Define shader stages
            // Each pipeline can have vertex, tessellation, geometry, fragment shaders
            // We're using the two most common: vertex and fragment
            VkPipelineShaderStageCreateInfo.Buffer shaderStages =
                VkPipelineShaderStageCreateInfo.calloc(2, stack);

            // Vertex shader stage
            // WHAT: Transforms each vertex from model space to clip space
            // WHEN: Runs once per vertex (e.g., 10,000 times for a 10K vertex mesh)
            VkPipelineShaderStageCreateInfo vertShaderStageInfo = shaderStages.get(0);
            vertShaderStageInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO);
            vertShaderStageInfo.stage(VK_SHADER_STAGE_VERTEX_BIT);
            vertShaderStageInfo.module(vertShaderModule);
            vertShaderStageInfo.pName(stack.UTF8("main")); // Entry point function name

            // Fragment shader stage
            // WHAT: Calculates final color for each pixel
            // WHEN: Runs once per pixel covered by rasterized triangles
            //       (e.g., millions of times per frame at 1920x1080!)
            VkPipelineShaderStageCreateInfo fragShaderStageInfo = shaderStages.get(1);
            fragShaderStageInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO);
            fragShaderStageInfo.stage(VK_SHADER_STAGE_FRAGMENT_BIT);
            fragShaderStageInfo.module(fragShaderModule);
            fragShaderStageInfo.pName(stack.UTF8("main"));

            // STEP 3: Vertex input state
            // Describes how vertex data is laid out in memory
            // See Vertex.java for detailed explanation of our vertex format
            VkVertexInputBindingDescription.Buffer bindingDescription =
                Vertex.getBindingDescription(stack);
            VkVertexInputAttributeDescription.Buffer attributeDescriptions =
                Vertex.getAttributeDescriptions(stack);

            VkPipelineVertexInputStateCreateInfo vertexInputInfo =
                VkPipelineVertexInputStateCreateInfo.calloc(stack);
            vertexInputInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO);
            vertexInputInfo.pVertexBindingDescriptions(bindingDescription);
            vertexInputInfo.pVertexAttributeDescriptions(attributeDescriptions);

            // STEP 4: Input assembly state
            // HOW: Defines how vertices form primitives
            // TRIANGLE_LIST: Every 3 vertices = 1 triangle (most common)
            // TRIANGLE_STRIP: Vertices 0,1,2 = tri1, then 1,2,3 = tri2 (saves memory)
            // POINT_LIST: Each vertex is a point (for particle systems)
            VkPipelineInputAssemblyStateCreateInfo inputAssembly =
                VkPipelineInputAssemblyStateCreateInfo.calloc(stack);
            inputAssembly.sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO);
            inputAssembly.topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);
            inputAssembly.primitiveRestartEnable(false); // Used with STRIP topologies

            // STEP 5: Viewport and scissor
            // VIEWPORT: Transforms clip space [-1,1] to screen space [0, width]x[0, height]
            // SCISSOR: Discards pixels outside a rectangle (optimization)
            VkViewport.Buffer viewport = VkViewport.calloc(1, stack);
            viewport.x(0.0f);
            viewport.y(0.0f);
            viewport.width(extent.width());
            viewport.height(extent.height());
            viewport.minDepth(0.0f);  // Near clipping plane (0 = closest)
            viewport.maxDepth(1.0f);  // Far clipping plane (1 = farthest)

            VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
            scissor.offset(VkOffset2D.calloc(stack).set(0, 0));
            scissor.extent(extent);

            VkPipelineViewportStateCreateInfo viewportState =
                VkPipelineViewportStateCreateInfo.calloc(stack);
            viewportState.sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO);
            viewportState.pViewports(viewport);
            viewportState.pScissors(scissor);

            // STEP 6: Rasterization state
            // HOW: Converts triangles into pixel fragments
            VkPipelineRasterizationStateCreateInfo rasterizer =
                VkPipelineRasterizationStateCreateInfo.calloc(stack);
            rasterizer.sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO);

            // depthClampEnable: Clamp depth instead of discarding (for shadow mapping)
            rasterizer.depthClampEnable(false);

            // rasterizerDiscardEnable: Skip rasterization entirely (for transform feedback)
            rasterizer.rasterizerDiscardEnable(false);

            // polygonMode: FILL (solid), LINE (wireframe), POINT (vertex dots)
            rasterizer.polygonMode(VK_POLYGON_MODE_FILL);
            rasterizer.lineWidth(1.0f);

            // cullMode: BACK (don't render back faces), FRONT, NONE
            // WHY CULL? Saves 50% of fragment shader work for closed meshes!
            rasterizer.cullMode(VK_CULL_MODE_BACK_BIT);

            // frontFace: Which winding order is "front"?
            // COUNTER_CLOCKWISE: Standard for most 3D modeling tools
            rasterizer.frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE);

            // depthBias: Offset depth values (prevents shadow acne)
            rasterizer.depthBiasEnable(false);

            // STEP 7: Multisampling (MSAA)
            // WHAT: Anti-aliasing technique that renders at higher resolution
            // WHY DISABLED? MSAA is expensive (4x more memory and bandwidth)
            // ALTERNATIVE: Post-process AA like FXAA or TAA
            VkPipelineMultisampleStateCreateInfo multisampling =
                VkPipelineMultisampleStateCreateInfo.calloc(stack);
            multisampling.sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO);
            multisampling.sampleShadingEnable(false);
            multisampling.rasterizationSamples(VK_SAMPLE_COUNT_1_BIT); // No MSAA

            // STEP 8: Depth and stencil testing
            // DEPTH TEST: Which pixel is closer to camera?
            // STENCIL TEST: Masking regions (for portals, mirrors, etc.)
            VkPipelineDepthStencilStateCreateInfo depthStencil =
                VkPipelineDepthStencilStateCreateInfo.calloc(stack);
            depthStencil.sType(VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO);

            // depthTestEnable: Compare depth values to determine visibility
            depthStencil.depthTestEnable(true);

            // depthWriteEnable: Write depth values to depth buffer
            // WHY: So subsequent objects know what's in front
            depthStencil.depthWriteEnable(true);

            // depthCompareOp: LESS means "closer wins"
            // LESS_OR_EQUAL would allow z-fighting on same plane
            depthStencil.depthCompareOp(VK_COMPARE_OP_LESS);

            depthStencil.depthBoundsTestEnable(false);
            depthStencil.stencilTestEnable(false); // Not using stencil for now

            // STEP 9: Color blending
            // HOW: Combine fragment shader output with existing framebuffer color
            VkPipelineColorBlendAttachmentState.Buffer colorBlendAttachment =
                VkPipelineColorBlendAttachmentState.calloc(1, stack);

            // colorWriteMask: Which channels to write (R, G, B, A)
            colorBlendAttachment.colorWriteMask(
                VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT |
                VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT);

            // blendEnable: Enable alpha blending
            colorBlendAttachment.blendEnable(true);

            // ALPHA BLENDING FORMULA:
            // finalColor = (srcColor * srcAlpha) + (dstColor * (1 - srcAlpha))
            //
            // EXAMPLE: Rendering 50% transparent red (1,0,0,0.5) over opaque blue (0,0,1,1)
            // finalColor = (1,0,0) * 0.5 + (0,0,1) * 0.5 = (0.5, 0, 0.5) = Purple!
            colorBlendAttachment.srcColorBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA);
            colorBlendAttachment.dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA);
            colorBlendAttachment.colorBlendOp(VK_BLEND_OP_ADD);

            // Alpha blending for alpha channel itself
            colorBlendAttachment.srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE);
            colorBlendAttachment.dstAlphaBlendFactor(VK_BLEND_FACTOR_ZERO);
            colorBlendAttachment.alphaBlendOp(VK_BLEND_OP_ADD);

            VkPipelineColorBlendStateCreateInfo colorBlending =
                VkPipelineColorBlendStateCreateInfo.calloc(stack);
            colorBlending.sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO);
            colorBlending.logicOpEnable(false); // Bitwise operations (rarely used)
            colorBlending.pAttachments(colorBlendAttachment);

            // STEP 10: Descriptor set layout
            // Defines what data (uniforms, textures) the shaders expect
            descriptorSetLayout = createDescriptorSetLayout();

            // STEP 11: Pipeline layout
            // Combines descriptor set layouts and push constants
            // PUSH CONSTANTS: Small amounts of data (<128 bytes) passed directly to shaders
            //                 Faster than uniforms for frequently changing data
            VkPipelineLayoutCreateInfo pipelineLayoutInfo =
                VkPipelineLayoutCreateInfo.calloc(stack);
            pipelineLayoutInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO);
            pipelineLayoutInfo.pSetLayouts(stack.longs(descriptorSetLayout));

            LongBuffer pPipelineLayout = stack.longs(VK_NULL_HANDLE);
            if (vkCreatePipelineLayout(device, pipelineLayoutInfo, null, pPipelineLayout) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create pipeline layout");
            }
            pipelineLayout = pPipelineLayout.get(0);

            // STEP 12: Finally, create the graphics pipeline!
            // Combines all previous state into one immutable pipeline object
            VkGraphicsPipelineCreateInfo.Buffer pipelineInfo =
                VkGraphicsPipelineCreateInfo.calloc(1, stack);
            pipelineInfo.sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO);
            pipelineInfo.pStages(shaderStages);
            pipelineInfo.pVertexInputState(vertexInputInfo);
            pipelineInfo.pInputAssemblyState(inputAssembly);
            pipelineInfo.pViewportState(viewportState);
            pipelineInfo.pRasterizationState(rasterizer);
            pipelineInfo.pMultisampleState(multisampling);
            pipelineInfo.pDepthStencilState(depthStencil);
            pipelineInfo.pColorBlendState(colorBlending);
            pipelineInfo.layout(pipelineLayout);
            pipelineInfo.renderPass(renderPass); // Must be compatible!
            pipelineInfo.subpass(0);
            pipelineInfo.basePipelineHandle(VK_NULL_HANDLE); // Used for pipeline derivatives

            LongBuffer pPipeline = stack.longs(VK_NULL_HANDLE);

            // NOTE: First parameter can be a pipeline cache for faster creation
            // Professional engines pre-warm the cache at startup
            if (vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, pipelineInfo, null, pPipeline) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create graphics pipeline");
            }
            pipeline = pPipeline.get(0);

            // Shader modules can be destroyed now - pipeline has compiled everything
            // The GPU driver has already translated SPIR-V to native GPU code
            vkDestroyShaderModule(device, vertShaderModule, null);
            vkDestroyShaderModule(device, fragShaderModule, null);

        } catch (Exception e) {
            throw new RuntimeException("Failed to create pipeline", e);
        }
    }

    /**
     * Creates a descriptor set layout for uniforms and textures.
     *
     * WHAT ARE DESCRIPTOR SETS?
     * Think of them as function parameters for your shader. Each "binding"
     * is one parameter. The shader code uses these bindings to access data.
     *
     * OUR LAYOUT:
     * Binding 0: Camera uniforms (view matrix, projection matrix, camera position)
     * Binding 1: Model matrix (transform from model space to world space)
     * Binding 2: Material properties (albedo, metallic, roughness, AO)
     * Binding 3: Lights (array of up to 16 lights)
     * Binding 4: Albedo texture (base color map)
     * Binding 5: Normal map (surface detail)
     * Binding 6: Metallic-Roughness texture (PBR properties)
     *
     * WHY SEPARATE BINDINGS?
     * Different bindings update at different frequencies:
     * - Camera (binding 0): Once per frame
     * - Model (binding 1): Once per mesh
     * - Material (bindings 2,4,5,6): Once per material
     * - Lights (binding 3): Once per frame
     *
     * By separating them, we only update what changed!
     */
    private long createDescriptorSetLayout() {
        try (MemoryStack stack = MemoryStack.stackPush()) {

            VkDescriptorSetLayoutBinding.Buffer bindings =
                VkDescriptorSetLayoutBinding.calloc(7, stack);

            // Binding 0: Camera uniforms (VP matrices)
            // STAGE: Vertex shader needs these to transform vertices to clip space
            VkDescriptorSetLayoutBinding cameraBinding = bindings.get(0);
            cameraBinding.binding(0);
            cameraBinding.descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);
            cameraBinding.descriptorCount(1);
            cameraBinding.stageFlags(VK_SHADER_STAGE_VERTEX_BIT);

            // Binding 1: Model matrix
            // STAGE: Vertex shader transforms model space → world space
            VkDescriptorSetLayoutBinding modelBinding = bindings.get(1);
            modelBinding.binding(1);
            modelBinding.descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);
            modelBinding.descriptorCount(1);
            modelBinding.stageFlags(VK_SHADER_STAGE_VERTEX_BIT);

            // Binding 2: Material properties
            // STAGE: Fragment shader uses these for PBR calculations
            VkDescriptorSetLayoutBinding materialBinding = bindings.get(2);
            materialBinding.binding(2);
            materialBinding.descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);
            materialBinding.descriptorCount(1);
            materialBinding.stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);

            // Binding 3: Lights
            // STAGE: Fragment shader iterates through lights for illumination
            VkDescriptorSetLayoutBinding lightsBinding = bindings.get(3);
            lightsBinding.binding(3);
            lightsBinding.descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);
            lightsBinding.descriptorCount(1);
            lightsBinding.stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);

            // Binding 4: Albedo texture
            // COMBINED_IMAGE_SAMPLER: Texture + sampling settings in one binding
            // WHY COMBINED? It's the most common case and slightly more efficient
            VkDescriptorSetLayoutBinding albedoBinding = bindings.get(4);
            albedoBinding.binding(4);
            albedoBinding.descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
            albedoBinding.descriptorCount(1);
            albedoBinding.stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);

            // Binding 5: Normal map
            // Adds surface detail without additional geometry
            VkDescriptorSetLayoutBinding normalBinding = bindings.get(5);
            normalBinding.binding(5);
            normalBinding.descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
            normalBinding.descriptorCount(1);
            normalBinding.stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);

            // Binding 6: Metallic-Roughness texture
            // Stores metallic in B channel, roughness in G channel
            VkDescriptorSetLayoutBinding metallicRoughnessBinding = bindings.get(6);
            metallicRoughnessBinding.binding(6);
            metallicRoughnessBinding.descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
            metallicRoughnessBinding.descriptorCount(1);
            metallicRoughnessBinding.stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);

            VkDescriptorSetLayoutCreateInfo layoutInfo =
                VkDescriptorSetLayoutCreateInfo.calloc(stack);
            layoutInfo.sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO);
            layoutInfo.pBindings(bindings);

            LongBuffer pDescriptorSetLayout = stack.longs(VK_NULL_HANDLE);
            if (vkCreateDescriptorSetLayout(device, layoutInfo, null, pDescriptorSetLayout) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create descriptor set layout");
            }

            return pDescriptorSetLayout.get(0);
        }
    }

    /**
     * Loads a SPIR-V shader file and creates a shader module.
     *
     * WHAT IS A SHADER MODULE?
     * A wrapper around compiled SPIR-V bytecode. The Vulkan driver will
     * translate SPIR-V to native GPU instructions when the pipeline is created.
     *
     * WHY SPIR-V?
     * - Platform-independent bytecode (like Java bytecode for GPUs)
     * - Validation happens at compile time (glslangValidator catches errors)
     * - Faster load times (no parsing of text)
     */
    private long createShaderModule(String shaderPath) throws IOException {
        byte[] shaderCode = Files.readAllBytes(Paths.get(shaderPath));

        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Vulkan expects SPIR-V as uint32 array (aligned to 4 bytes)
            ByteBuffer shaderBuffer = stack.malloc(shaderCode.length);
            shaderBuffer.put(shaderCode);
            shaderBuffer.flip(); // Prepare for reading

            VkShaderModuleCreateInfo createInfo = VkShaderModuleCreateInfo.calloc(stack);
            createInfo.sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO);
            createInfo.pCode(shaderBuffer.asIntBuffer());

            LongBuffer pShaderModule = stack.longs(VK_NULL_HANDLE);
            if (vkCreateShaderModule(device, createInfo, null, pShaderModule) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create shader module");
            }

            return pShaderModule.get(0);
        }
    }

    public void bind(VkCommandBuffer commandBuffer) {
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
    }

    public long getLayout() {
        return pipelineLayout;
    }

    public long getDescriptorSetLayout() {
        return descriptorSetLayout;
    }

    public void destroy() {
        if (pipeline != VK_NULL_HANDLE) {
            vkDestroyPipeline(device, pipeline, null);
        }
        if (pipelineLayout != VK_NULL_HANDLE) {
            vkDestroyPipelineLayout(device, pipelineLayout, null);
        }
        if (descriptorSetLayout != VK_NULL_HANDLE) {
            vkDestroyDescriptorSetLayout(device, descriptorSetLayout, null);
        }
    }
}
```

---

### Vertex.java - Defining the Vertex Format

**What Is a Vertex Format?**

A vertex is a **single point** in your 3D model. The vertex format defines what data is stored for each vertex.

**Our Vertex Format:**

```
┌─────────────┬────────┬────────┐
│ Attribute   │ Type   │ Bytes  │
├─────────────┼────────┼────────┤
│ Position    │ vec3   │ 12     │
│ Normal      │ vec3   │ 12     │
│ TexCoord    │ vec2   │ 8      │
│ Tangent     │ vec3   │ 12     │
└─────────────┴────────┴────────┘
Total: 44 bytes per vertex
```

**Why These Attributes?**

1. **Position**: Where the vertex is in 3D space (required!)
2. **Normal**: Direction the surface faces (for lighting)
3. **TexCoord**: UV coordinates for texture mapping
4. **Tangent**: Needed for normal mapping (TBN matrix)

**Memory Layout:**

```
Vertex 0: [pos_x][pos_y][pos_z][norm_x][norm_y][norm_z][u][v][tan_x][tan_y][tan_z]
Vertex 1: [pos_x][pos_y][pos_z][norm_x][norm_y][norm_z][u][v][tan_x][tan_y][tan_z]
...
```

This is called **interleaved vertex data** - all attributes for one vertex are contiguous in memory.

**Alternative: Separate Arrays (Non-interleaved)**
```
Positions: [v0_pos][v1_pos][v2_pos]...
Normals:   [v0_norm][v1_norm][v2_norm]...
TexCoords: [v0_uv][v1_uv][v2_uv]...
```

**Which Is Better?**
- **Interleaved**: Better cache locality (GPU loads all attributes together)
- **Separate**: Easier to update individual attributes
- **Industry standard**: Interleaved for static geometry

```java
package com.jecs.graphics.vulkan;

import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import static org.lwjgl.vulkan.VK10.*;

/**
 * Represents a single vertex with position, normal, texture coordinates,
 * and tangent for normal mapping.
 *
 * VERTEX FORMAT: Defines the structure of vertex data in memory.
 * GPU needs to know:
 * 1. How big is each vertex? (stride)
 * 2. What attributes does it have? (position, normal, etc.)
 * 3. Where is each attribute within the vertex? (offset)
 * 4. What data type is each attribute? (vec3 = 3 floats)
 */
public class Vertex {

    public Vector3f position;  // Where is this point in 3D space?
    public Vector3f normal;    // Which direction does the surface face?
    public Vector2f texCoord;  // What part of the texture maps to this point?
    public Vector3f tangent;   // Tangent vector for normal mapping (TBN matrix)

    public Vertex(Vector3f position, Vector3f normal, Vector2f texCoord, Vector3f tangent) {
        this.position = position;
        this.normal = normal;
        this.texCoord = texCoord;
        this.tangent = tangent;
    }

    /**
     * Returns the size of a vertex in bytes.
     *
     * CALCULATION:
     * - Position: vec3 = 3 floats = 3 * 4 bytes = 12 bytes
     * - Normal:   vec3 = 3 floats = 3 * 4 bytes = 12 bytes
     * - TexCoord: vec2 = 2 floats = 2 * 4 bytes = 8 bytes
     * - Tangent:  vec3 = 3 floats = 3 * 4 bytes = 12 bytes
     * Total: 44 bytes
     */
    public static int sizeof() {
        return 3 * 4 + 3 * 4 + 2 * 4 + 3 * 4;
    }

    /**
     * Describes how vertex data is bound to the pipeline.
     *
     * BINDING DESCRIPTION: Tells Vulkan:
     * - binding: Which vertex buffer to read from (we only use one: 0)
     * - stride: Bytes between consecutive vertices (44 bytes)
     * - inputRate: VERTEX = per-vertex data (vs INSTANCE = per-instance data)
     *
     * INSTANCED RENDERING:
     * If inputRate = VK_VERTEX_INPUT_RATE_INSTANCE, the attribute advances
     * once per instance instead of per vertex. Useful for rendering 1000s
     * of the same object (e.g., trees, grass).
     */
    public static VkVertexInputBindingDescription.Buffer getBindingDescription(MemoryStack stack) {
        VkVertexInputBindingDescription.Buffer bindingDescription =
            VkVertexInputBindingDescription.calloc(1, stack);

        bindingDescription.binding(0);             // Vertex buffer slot
        bindingDescription.stride(sizeof());       // 44 bytes between vertices
        bindingDescription.inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

        return bindingDescription;
    }

    /**
     * Describes the vertex attributes (position, normal, texCoord, tangent).
     *
     * ATTRIBUTE DESCRIPTIONS: Tell Vulkan:
     * - binding: Which vertex buffer (matches binding above)
     * - location: Maps to shader input location (layout(location = 0) in vec3 inPosition)
     * - format: Data type (R32G32B32_SFLOAT = 3x float32)
     * - offset: Bytes from start of vertex to this attribute
     *
     * MEMORY LAYOUT:
     *
     *   Vertex structure (44 bytes total):
     *   ┌───────────┬───────────┬───────────┬───────────┐
     *   │ Position  │  Normal   │ TexCoord  │  Tangent  │
     *   │  (12B)    │   (12B)   │   (8B)    │   (12B)   │
     *   └───────────┴───────────┴───────────┴───────────┘
     *   offset: 0       12          24          32
     *
     * SHADER MAPPING:
     * layout(location = 0) in vec3 inPosition;  ← Location 0 = Position
     * layout(location = 1) in vec3 inNormal;    ← Location 1 = Normal
     * layout(location = 2) in vec2 inTexCoord;  ← Location 2 = TexCoord
     * layout(location = 3) in vec3 inTangent;   ← Location 3 = Tangent
     */
    public static VkVertexInputAttributeDescription.Buffer getAttributeDescriptions(MemoryStack stack) {
        VkVertexInputAttributeDescription.Buffer attributeDescriptions =
            VkVertexInputAttributeDescription.calloc(4, stack);

        // Position (location = 0)
        VkVertexInputAttributeDescription posDescription = attributeDescriptions.get(0);
        posDescription.binding(0);
        posDescription.location(0);
        posDescription.format(VK_FORMAT_R32G32B32_SFLOAT); // 3 floats (x, y, z)
        posDescription.offset(0);                           // First attribute

        // Normal (location = 1)
        VkVertexInputAttributeDescription normalDescription = attributeDescriptions.get(1);
        normalDescription.binding(0);
        normalDescription.location(1);
        normalDescription.format(VK_FORMAT_R32G32B32_SFLOAT);
        normalDescription.offset(12); // After position (3 floats * 4 bytes)

        // TexCoord (location = 2)
        VkVertexInputAttributeDescription texCoordDescription = attributeDescriptions.get(2);
        texCoordDescription.binding(0);
        texCoordDescription.location(2);
        texCoordDescription.format(VK_FORMAT_R32G32_SFLOAT);   // 2 floats (u, v)
        texCoordDescription.offset(24); // After position + normal (6 floats * 4 bytes)

        // Tangent (location = 3)
        VkVertexInputAttributeDescription tangentDescription = attributeDescriptions.get(3);
        tangentDescription.binding(0);
        tangentDescription.location(3);
        tangentDescription.format(VK_FORMAT_R32G32B32_SFLOAT);
        tangentDescription.offset(32); // After position + normal + texCoord (8 floats * 4 bytes)

        return attributeDescriptions;
    }
}
```

---

## Part 2: Buffer Management

### Understanding Vulkan Memory Types

**Vulkan gives you explicit control over memory placement.** Different memory types have different trade-offs:

**Memory Types:**

1. **Device-Local Memory** (`DEVICE_LOCAL_BIT`)
   - **Where**: On GPU (VRAM)
   - **Speed**: FAST! (~500 GB/s bandwidth)
   - **Access**: CPU cannot read/write directly
   - **Use for**: Vertex buffers, index buffers, textures

2. **Host-Visible Memory** (`HOST_VISIBLE_BIT`)
   - **Where**: System RAM or special CPU-accessible GPU memory
   - **Speed**: Slow (~20 GB/s bandwidth)
   - **Access**: CPU can read/write with `vkMapMemory`
   - **Use for**: Uniform buffers, staging buffers

3. **Host-Coherent Memory** (`HOST_COHERENT_BIT`)
   - **What**: Automatically synchronized between CPU and GPU
   - **Alternative**: Manual flush/invalidate (faster but more work)
   - **Use for**: Frequently updated uniforms

**The Staging Buffer Pattern:**

Problem: Device-local memory is fastest, but CPU can't write to it directly.

Solution: Two-step upload:
```
1. CPU writes to staging buffer (host-visible)
2. GPU copies staging → device-local buffer
3. Delete staging buffer
```

**Diagram:**

```
┌─────────────┐     ┌──────────────┐     ┌─────────────┐
│   CPU RAM   │────>│   Staging    │────>│ Device-Local│
│             │write│   Buffer     │copy │  Buffer     │
│ [mesh data] │     │ (host-vis.)  │(GPU)│  (VRAM)     │
└─────────────┘     └──────────────┘     └─────────────┘
                                               │
                                               v
                                          Fast rendering!
```

**Why Not Always Use Host-Visible?**

Rendering 10,000 vertices from system RAM: ~0.5ms
Rendering 10,000 vertices from VRAM: ~0.05ms (10x faster!)

**Unity Comparison:**
- **Unity**: Hides this complexity - automatically uploads to GPU
- **Unreal**: Low-level control via `FRHICommandList`
- **JECS**: Explicit like Unreal - you control memory placement

---

### VulkanBuffer.java - Memory Management

**What This Code Does:**

This class manages Vulkan buffers - contiguous regions of GPU memory. It handles:
1. Creating buffers with specific usage (vertex, index, uniform)
2. Allocating memory of the right type (device-local vs host-visible)
3. Uploading data from CPU to GPU
4. Copying between buffers (staging → device-local)

```java
package com.jecs.graphics.vulkan;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;

/**
 * Manages Vulkan buffers (vertex, index, uniform).
 *
 * KEY CONCEPTS:
 * 1. BUFFER: A region of GPU memory with a specific usage (vertex data, uniforms, etc.)
 * 2. MEMORY: The actual allocation backing the buffer
 * 3. BINDING: Connecting a buffer to its memory allocation
 *
 * WORKFLOW:
 * 1. Create buffer object (defines size and usage)
 * 2. Query memory requirements (GPU tells you what it needs)
 * 3. Allocate memory (find compatible memory type)
 * 4. Bind buffer to memory
 */
public class VulkanBuffer {

    private final VkDevice device;
    private final VkPhysicalDevice physicalDevice;

    private long buffer;  // Buffer handle (what you use in draw commands)
    private long memory;  // Memory handle (the actual allocation)
    private long size;

    public VulkanBuffer(VkDevice device, VkPhysicalDevice physicalDevice) {
        this.device = device;
        this.physicalDevice = physicalDevice;
    }

    /**
     * Creates a buffer with the specified usage and memory properties.
     *
     * USAGE FLAGS:
     * - VERTEX_BUFFER: Stores vertex data (positions, normals, etc.)
     * - INDEX_BUFFER: Stores indices into vertex buffer
     * - UNIFORM_BUFFER: Stores shader uniforms (matrices, lights, etc.)
     * - TRANSFER_SRC: Can be source of copy operation
     * - TRANSFER_DST: Can be destination of copy operation
     *
     * MEMORY PROPERTY FLAGS:
     * - DEVICE_LOCAL: On GPU (fast, but CPU can't access)
     * - HOST_VISIBLE: CPU can map and write
     * - HOST_COHERENT: Automatically synced (no manual flush)
     *
     * COMMON COMBINATIONS:
     * 1. Vertex buffer: VERTEX_BUFFER | TRANSFER_DST, DEVICE_LOCAL
     * 2. Uniform buffer: UNIFORM_BUFFER, HOST_VISIBLE | HOST_COHERENT
     * 3. Staging buffer: TRANSFER_SRC, HOST_VISIBLE | HOST_COHERENT
     */
    public void create(long size, int usage, int memoryProperties) {
        this.size = size;

        try (MemoryStack stack = MemoryStack.stackPush()) {

            // STEP 1: Create buffer object
            // This doesn't allocate memory yet! It just defines what you want.
            VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack);
            bufferInfo.sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO);
            bufferInfo.size(size);
            bufferInfo.usage(usage);
            bufferInfo.sharingMode(VK_SHARING_MODE_EXCLUSIVE); // Only one queue uses it

            LongBuffer pBuffer = stack.longs(VK_NULL_HANDLE);
            if (vkCreateBuffer(device, bufferInfo, null, pBuffer) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create buffer");
            }
            buffer = pBuffer.get(0);

            // STEP 2: Get memory requirements
            // GPU tells us:
            // - size: How much memory we need (may be > requested size for alignment)
            // - alignment: Memory must be aligned (e.g., uniforms need 256-byte alignment)
            // - memoryTypeBits: Which memory types are compatible
            VkMemoryRequirements memRequirements = VkMemoryRequirements.malloc(stack);
            vkGetBufferMemoryRequirements(device, buffer, memRequirements);

            // STEP 3: Allocate memory
            VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack);
            allocInfo.sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);
            allocInfo.allocationSize(memRequirements.size());

            // Find a memory type that:
            // 1. GPU supports for this buffer (memoryTypeBits)
            // 2. Has the properties we want (DEVICE_LOCAL, HOST_VISIBLE, etc.)
            allocInfo.memoryTypeIndex(findMemoryType(
                memRequirements.memoryTypeBits(),
                memoryProperties
            ));

            LongBuffer pMemory = stack.longs(VK_NULL_HANDLE);
            if (vkAllocateMemory(device, allocInfo, null, pMemory) != VK_SUCCESS) {
                throw new RuntimeException("Failed to allocate buffer memory");
            }
            memory = pMemory.get(0);

            // STEP 4: Bind buffer to memory
            // Now the buffer handle points to actual memory!
            vkBindBufferMemory(device, buffer, memory, 0);
        }
    }

    /**
     * Maps the buffer memory and copies data to it.
     *
     * MAPPING: Creating a CPU pointer to GPU memory
     *
     * ONLY WORKS WITH HOST_VISIBLE memory!
     * Device-local memory cannot be mapped.
     *
     * WORKFLOW:
     * 1. vkMapMemory: Get CPU pointer to GPU memory
     * 2. memcpy: Copy data from CPU buffer to mapped memory
     * 3. vkUnmapMemory: Release the mapping
     *
     * NOTE: For HOST_COHERENT memory, changes are automatically visible to GPU.
     * For non-coherent memory, you need vkFlushMappedMemoryRanges.
     */
    public void upload(ByteBuffer data) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pData = stack.mallocPointer(1);

            // Map the memory (get CPU pointer)
            vkMapMemory(device, memory, 0, size, 0, pData);

            // Copy data
            ByteBuffer mappedMemory = pData.getByteBuffer(0, (int) size);
            mappedMemory.put(data);
            data.rewind(); // Reset position for potential reuse

            // Unmap (GPU can now access the data)
            vkUnmapMemory(device, memory);
        }
    }

    /**
     * Copies data from another buffer to this buffer.
     *
     * WHY: Device-local memory is fastest, but CPU can't write to it directly.
     * Solution: Upload to staging buffer (host-visible), then copy to device-local.
     *
     * WORKFLOW:
     * 1. Allocate a temporary command buffer
     * 2. Record copy command
     * 3. Submit to GPU queue
     * 4. Wait for completion
     * 5. Free command buffer
     *
     * NOTE: This is synchronous (blocks CPU). For better performance, use fences
     * and copy multiple buffers in one command buffer.
     */
    public void copyFrom(VulkanBuffer srcBuffer, VkCommandPool commandPool, VkQueue queue) {
        try (MemoryStack stack = MemoryStack.stackPush()) {

            // Allocate temporary command buffer
            VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc(stack);
            allocInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO);
            allocInfo.level(VK_COMMAND_BUFFER_LEVEL_PRIMARY);
            allocInfo.commandPool(commandPool);
            allocInfo.commandBufferCount(1);

            PointerBuffer pCommandBuffer = stack.mallocPointer(1);
            vkAllocateCommandBuffers(device, allocInfo, pCommandBuffer);
            VkCommandBuffer commandBuffer = new VkCommandBuffer(pCommandBuffer.get(0), device);

            // Begin recording
            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack);
            beginInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
            beginInfo.flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT); // Used once then freed

            vkBeginCommandBuffer(commandBuffer, beginInfo);

            // Copy command
            // GPU will perform this copy asynchronously when the command buffer executes
            VkBufferCopy.Buffer copyRegion = VkBufferCopy.calloc(1, stack);
            copyRegion.srcOffset(0);
            copyRegion.dstOffset(0);
            copyRegion.size(size);

            vkCmdCopyBuffer(commandBuffer, srcBuffer.getHandle(), buffer, copyRegion);

            vkEndCommandBuffer(commandBuffer);

            // Submit and wait
            // OPTIMIZATION: In production, batch multiple copies and use fences
            VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack);
            submitInfo.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO);
            submitInfo.pCommandBuffers(pCommandBuffer);

            vkQueueSubmit(queue, submitInfo, VK_NULL_HANDLE);
            vkQueueWaitIdle(queue); // Block until copy finishes

            // Clean up
            vkFreeCommandBuffers(device, commandPool, commandBuffer);
        }
    }

    /**
     * Finds a suitable memory type for the buffer.
     *
     * HOW IT WORKS:
     * GPU has multiple memory types (heaps), each with different properties.
     * Example GPU might have:
     *
     * Type 0: DEVICE_LOCAL (VRAM, fast, 8GB)
     * Type 1: HOST_VISIBLE | HOST_COHERENT (System RAM, slow, 16GB)
     * Type 2: DEVICE_LOCAL | HOST_VISIBLE (Resizable BAR, fast + mappable, 256MB)
     *
     * typeFilter: Bitmask of compatible types (from memoryRequirements.memoryTypeBits())
     * properties: What properties we need (DEVICE_LOCAL, HOST_VISIBLE, etc.)
     *
     * We iterate through types, checking:
     * 1. Is this type compatible? (typeFilter & (1 << i))
     * 2. Does it have the properties we want? ((flags & properties) == properties)
     */
    private int findMemoryType(int typeFilter, int properties) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceMemoryProperties memProperties = VkPhysicalDeviceMemoryProperties.malloc(stack);
            vkGetPhysicalDeviceMemoryProperties(physicalDevice, memProperties);

            for (int i = 0; i < memProperties.memoryTypeCount(); i++) {
                boolean typeSupported = (typeFilter & (1 << i)) != 0;
                boolean hasProperties = (memProperties.memoryTypes(i).propertyFlags() & properties) == properties;

                if (typeSupported && hasProperties) {
                    return i;
                }
            }

            throw new RuntimeException("Failed to find suitable memory type");
        }
    }

    public long getHandle() {
        return buffer;
    }

    public long getSize() {
        return size;
    }

    public void destroy() {
        if (buffer != VK_NULL_HANDLE) {
            vkDestroyBuffer(device, buffer, null);
        }
        if (memory != VK_NULL_HANDLE) {
            vkFreeMemory(device, memory, null);
        }
    }

    /**
     * Helper: Creates a staging buffer and uploads data, then copies to device-local buffer.
     *
     * THE STAGING BUFFER PATTERN:
     * This is the standard way to upload static data to GPU efficiently.
     *
     * WORKFLOW:
     * 1. Create staging buffer (HOST_VISIBLE | HOST_COHERENT)
     * 2. Map staging buffer memory
     * 3. Copy CPU data → staging buffer
     * 4. Unmap staging buffer
     * 5. Create device-local buffer (DEVICE_LOCAL)
     * 6. GPU copy: staging → device-local
     * 7. Destroy staging buffer
     *
     * WHY TWO BUFFERS?
     * - Staging: CPU can write, but slow for GPU to read
     * - Device-local: GPU can read fast, but CPU can't write
     *
     * PERFORMANCE:
     * - Upload 100MB mesh to staging: ~5ms
     * - GPU copy to device-local: ~2ms
     * - Total: ~7ms once
     * - Rendering benefit: 10x faster draw calls forever!
     */
    public static VulkanBuffer createDeviceLocalBuffer(VkDevice device, VkPhysicalDevice physicalDevice,
                                                      ByteBuffer data, int usage,
                                                      VkCommandPool commandPool, VkQueue queue) {
        long bufferSize = data.remaining();

        // Create staging buffer (host-visible for CPU writes)
        VulkanBuffer stagingBuffer = new VulkanBuffer(device, physicalDevice);
        stagingBuffer.create(bufferSize,
                           VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                           VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        stagingBuffer.upload(data);

        // Create device-local buffer (fast for GPU reads)
        VulkanBuffer deviceBuffer = new VulkanBuffer(device, physicalDevice);
        deviceBuffer.create(bufferSize,
                          usage | VK_BUFFER_USAGE_TRANSFER_DST_BIT, // Must support transfer
                          VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);

        // Copy from staging to device (GPU operation)
        deviceBuffer.copyFrom(stagingBuffer, commandPool, queue);

        // Clean up staging buffer (no longer needed)
        stagingBuffer.destroy();

        return deviceBuffer;
    }
}
```

---

## Part 3: PBR Shaders

### What Is Physically Based Rendering (PBR)?

**The Problem with Old Rendering:**

Before PBR, materials were "artistic guesses":
```glsl
color = ambient + diffuse * NdotL + specular * pow(NdotH, shininess);
```

**Problems:**
- Not energy-conserving (can output more light than input!)
- Materials look different in different lighting
- Artists tweak per-scene instead of creating reusable materials

**PBR Solution: Simulate Real Physics**

PBR materials behave like real-world materials. Key principles:

1. **Energy Conservation**: Reflected light ≤ incoming light
2. **Metallic Workflow**: Materials are either metal or insulator (dielectric)
3. **Roughness**: How smooth/rough the surface is
4. **Fresnel Effect**: Reflections are stronger at grazing angles

**PBR Material Parameters:**

| Parameter | Range | Meaning | Real-World Examples |
|-----------|-------|---------|---------------------|
| **Albedo** | Color | Base color (no lighting) | Red paint, wood grain |
| **Metallic** | 0-1 | Is this a metal? | 0=plastic, 1=gold |
| **Roughness** | 0-1 | Surface smoothness | 0=mirror, 1=matte |
| **AO** | 0-1 | Ambient occlusion | Shadows in crevices |

**Professional Engine Comparison:**

| Engine | Material System |
|--------|-----------------|
| **Unity** | Standard Shader (metallic workflow) |
| **Unreal** | Material Editor (node-based PBR) |
| **Godot** | SpatialMaterial (PBR properties) |
| **JECS** | Manual PBR shaders (full control) |

---

### The Cook-Torrance BRDF

**What Is a BRDF?**

**BRDF** = Bidirectional Reflectance Distribution Function

Fancy name for: "How does light reflect off this surface?"

**Input:**
- **L**: Light direction (where light comes from)
- **V**: View direction (where camera is looking)
- **N**: Surface normal (which way surface faces)

**Output:**
- **Color**: How bright this pixel is

**The Cook-Torrance Formula:**

```
f(L, V) = kD * (albedo / π) + kS * (D * G * F) / (4 * (N·L) * (N·V))

where:
  kD = diffuse contribution (1 - kS)
  kS = specular contribution (Fresnel)
  D = Normal Distribution Function (how rough?)
  G = Geometry Function (self-shadowing)
  F = Fresnel Function (edge reflections)
```

**Breaking It Down:**

1. **Diffuse Term**: `kD * albedo / π`
   - Base color of the material
   - Diffuse light scatters equally in all directions
   - Metals have no diffuse (all light is specular)

2. **Specular Term**: `(D * G * F) / (4 * (N·L) * (N·V))`
   - Reflections and highlights
   - Depends on roughness, viewing angle, light angle

**The Three Functions:**

```
┌─────────────────────────────────────────────────────┐
│ D - Normal Distribution Function (GGX)              │
│ What: How many microfacets face the halfway vector? │
│ Why: Rougher surfaces have wider highlights         │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│ G - Geometry Function (Smith's Schlick-GGX)         │
│ What: How much do microfacets shadow each other?    │
│ Why: Rough surfaces have more self-shadowing        │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│ F - Fresnel Function (Schlick Approximation)        │
│ What: How much light reflects vs refracts?          │
│ Why: Reflections are stronger at grazing angles     │
└─────────────────────────────────────────────────────┘
```

**Visual Example:**

```
Smooth Metal (roughness = 0.0, metallic = 1.0):
  ▪ Sharp, bright reflections
  ▪ No diffuse component (pure specular)

Rough Plastic (roughness = 0.8, metallic = 0.0):
  ▪ Soft, wide highlights
  ▪ Strong diffuse component
```

---

### pbr.vert - Vertex Shader

**What This Shader Does:**

Transforms vertices from **model space** → **world space** → **clip space** and passes data to the fragment shader.

**The Coordinate System Journey:**

```
Model Space        World Space         View Space       Clip Space       Screen Space
(mesh local)   →   (game world)    →   (camera)    →   (projected)   →  (pixels)

  ┌───┐           ┌───────────┐                       ┌─────────┐
  │ ● │    Model  │     ●     │  View     Projection  │    ●    │  Viewport
  │   │   Matrix  │           │  Matrix     Matrix    │         │  Transform
  └───┘ (rotate,  └───────────┘ (camera   (perspective)└─────────┘ (to screen
      scale, pos)              rotation)                          coordinates)
```

**Why Transform Multiple Times?**

- **Model space**: Easy to create (centered at origin)
- **World space**: Needed for lighting calculations
- **View space**: Camera is at origin (simplifies calculations)
- **Clip space**: GPU knows what's visible

**Create `shaders/pbr.vert`:**

```glsl
#version 450

// Input vertex attributes (from vertex buffer)
// location = 0 matches Vertex.java's first attribute
layout(location = 0) in vec3 inPosition;
layout(location = 1) in vec3 inNormal;
layout(location = 2) in vec2 inTexCoord;
layout(location = 3) in vec3 inTangent;

// Uniforms (set = 0 means first descriptor set)
// These are the same for all vertices in a draw call
layout(set = 0, binding = 0) uniform CameraUBO {
    mat4 view;         // Camera position and rotation
    mat4 projection;   // Perspective projection (makes distant objects smaller)
    vec3 viewPos;      // Camera position in world space
} camera;

layout(set = 0, binding = 1) uniform ModelUBO {
    mat4 model;        // Model's position, rotation, and scale
    mat4 normalMatrix; // transpose(inverse(model)) - for transforming normals
} modelData;

// Output to fragment shader (interpolated across triangle)
// INTERPOLATION: GPU automatically lerps these values per-pixel
// Example: Triangle with red vertex, green vertex, blue vertex
//          → Pixels in middle are mixtures of those colors
layout(location = 0) out vec3 fragPosition;   // World space position
layout(location = 1) out vec3 fragNormal;     // World space normal
layout(location = 2) out vec2 fragTexCoord;   // Texture coordinates (not interpolated)
layout(location = 3) out vec3 fragTangent;    // World space tangent
layout(location = 4) out vec3 fragBitangent;  // Computed from normal and tangent
layout(location = 5) out vec3 viewPosition;   // Camera position (same for all fragments)

void main() {
    // STEP 1: Transform position to world space
    // Model matrix converts from local mesh coordinates to world coordinates
    // Example: Model at (10, 5, 3) with vertex at (1, 0, 0)
    //          → World position = (11, 5, 3)
    vec4 worldPos = modelData.model * vec4(inPosition, 1.0);
    fragPosition = worldPos.xyz;

    // STEP 2: Transform normal and tangent to world space
    // WHY normalMatrix? Normals don't transform like positions!
    // Example: Non-uniform scale (2x width, 1x height) would stretch normal
    // Solution: normalMatrix = transpose(inverse(model))
    fragNormal = mat3(modelData.normalMatrix) * inNormal;
    fragTangent = mat3(modelData.normalMatrix) * inTangent;

    // STEP 3: Calculate bitangent for TBN matrix
    // TBN = Tangent-Bitangent-Normal matrix
    // Used to transform normal maps from tangent space to world space
    // Think of TBN as a coordinate system aligned with the surface
    fragBitangent = cross(fragNormal, fragTangent);

    // STEP 4: Pass through texture coordinates
    // These don't need transformation - they're already in [0,1] range
    fragTexCoord = inTexCoord;

    // STEP 5: Pass camera position for specular calculations
    // Fragment shader needs this to calculate view direction (V)
    viewPosition = camera.viewPos;

    // STEP 6: Final position in clip space
    // This is what GPU uses to determine where pixel appears on screen
    // Clip space: x, y, z in [-w, w] (w is typically 1.0 after projection)
    // GPU divides by w to get Normalized Device Coordinates [-1, 1]
    gl_Position = camera.projection * camera.view * worldPos;
}
```

---

### pbr.frag - Fragment Shader with PBR

**What This Shader Does:**

Calculates the **final color** of each pixel using PBR. This is where the magic happens!

**Execution Frequency:**

For a 1920x1080 scene:
- Vertex shader: Runs ~10,000 times (once per vertex)
- Fragment shader: Runs ~2,000,000 times (once per visible pixel!)

**Performance Tip:** Keep fragment shaders optimized!

**Create `shaders/pbr.frag`:**

```glsl
#version 450

// Input from vertex shader (automatically interpolated per-pixel)
layout(location = 0) in vec3 fragPosition;
layout(location = 1) in vec3 fragNormal;
layout(location = 2) in vec2 fragTexCoord;
layout(location = 3) in vec3 fragTangent;
layout(location = 4) in vec3 fragBitangent;
layout(location = 5) in vec3 viewPosition;

// Material properties (same for all pixels using this material)
layout(set = 0, binding = 2) uniform MaterialUBO {
    vec4 albedoColor;      // Base color (if no texture)
    float metallic;        // 0 = dielectric, 1 = metal
    float roughness;       // 0 = smooth mirror, 1 = rough matte
    float ao;              // Ambient occlusion (shadows in crevices)
    int useAlbedoMap;      // Boolean flags (GLSL doesn't have bool in UBOs)
    int useNormalMap;
    int useMetallicRoughnessMap;
} material;

// Lights (up to 16 lights)
#define MAX_LIGHTS 16

struct Light {
    vec4 position;      // xyz = position, w = type (0 = point, 1 = directional, 2 = spot)
    vec4 direction;     // xyz = direction (for directional/spot lights)
    vec4 color;         // xyz = color, w = intensity
    vec4 params;        // x = radius, y = inner cutoff, z = outer cutoff
};

layout(set = 0, binding = 3) uniform LightsUBO {
    Light lights[MAX_LIGHTS];
    int numLights;
} lightsData;

// Textures (combined image + sampler)
// WHY COMBINED? More convenient and slightly faster than separate
layout(set = 0, binding = 4) uniform sampler2D albedoMap;
layout(set = 0, binding = 5) uniform sampler2D normalMap;
layout(set = 0, binding = 6) uniform sampler2D metallicRoughnessMap;

// Output color (what gets written to the framebuffer)
layout(location = 0) out vec4 outColor;

const float PI = 3.14159265359;

// ============================================================================
// PBR FUNCTIONS
// ============================================================================

/**
 * Normal Distribution Function (GGX / Trowbridge-Reitz)
 *
 * WHAT: Statistically models how many microfacets are aligned with H
 * INPUT:
 * - N: Surface normal
 * - H: Halfway vector between L and V
 * - roughness: Surface roughness [0, 1]
 *
 * OUTPUT: Concentration of microfacets (higher = shinier highlight)
 *
 * ROUGHNESS EFFECT:
 * - roughness = 0.0: Sharp highlight (mirror)
 * - roughness = 0.5: Medium highlight
 * - roughness = 1.0: Wide, soft highlight (matte)
 */
float DistributionGGX(vec3 N, vec3 H, float roughness) {
    float a = roughness * roughness;   // Remap roughness to alpha
    float a2 = a * a;                  // Square for more intuitive artist control
    float NdotH = max(dot(N, H), 0.0);
    float NdotH2 = NdotH * NdotH;

    float nom = a2;
    float denom = (NdotH2 * (a2 - 1.0) + 1.0);
    denom = PI * denom * denom;

    return nom / denom;
}

/**
 * Geometry Function (Smith's Schlick-GGX)
 *
 * WHAT: Models self-shadowing and occlusion of microfacets
 *
 * PHYSICAL MEANING:
 * Rough surfaces have tiny bumps. These bumps can:
 * 1. Shadow each other (prevent light from reaching)
 * 2. Occlude each other (block reflected light)
 *
 * ROUGHNESS EFFECT:
 * - roughness = 0.0: No self-shadowing (smooth surface)
 * - roughness = 1.0: High self-shadowing (rough surface)
 */
float GeometrySchlickGGX(float NdotV, float roughness) {
    float r = (roughness + 1.0);
    float k = (r * r) / 8.0;  // Remapping for direct lighting

    float nom = NdotV;
    float denom = NdotV * (1.0 - k) + k;

    return nom / denom;
}

/**
 * Combined geometry function (considers both L and V)
 */
float GeometrySmith(vec3 N, vec3 V, vec3 L, float roughness) {
    float NdotV = max(dot(N, V), 0.0);
    float NdotL = max(dot(N, L), 0.0);
    float ggx2 = GeometrySchlickGGX(NdotV, roughness);
    float ggx1 = GeometrySchlickGGX(NdotL, roughness);

    return ggx1 * ggx2;
}

/**
 * Fresnel Function (Schlick Approximation)
 *
 * WHAT: How much light reflects vs refracts
 *
 * PHYSICAL EFFECT: Look at a pond
 * - Looking straight down: See through (transmission)
 * - Looking at grazing angle: See reflection (mirror-like)
 *
 * F0: Base reflectivity at normal incidence
 * - Dielectrics: ~0.04 (4% reflection)
 * - Metals: Use albedo color (colored reflections!)
 */
vec3 fresnelSchlick(float cosTheta, vec3 F0) {
    return F0 + (1.0 - F0) * pow(clamp(1.0 - cosTheta, 0.0, 1.0), 5.0);
}

/**
 * Get normal from normal map (tangent space → world space)
 *
 * NORMAL MAPPING: Adds surface detail without extra geometry
 *
 * HOW IT WORKS:
 * 1. Normal map stores normals in tangent space (surface-relative)
 * 2. TBN matrix transforms tangent space → world space
 * 3. Result: Detailed normals that react to lighting correctly
 *
 * TANGENT SPACE:
 * - T (tangent): Points along U texture axis
 * - B (bitangent): Points along V texture axis
 * - N (normal): Points perpendicular to surface
 */
vec3 getNormalFromMap() {
    if (material.useNormalMap == 0) {
        return normalize(fragNormal);
    }

    // Sample normal map (RGB values in [0, 1])
    vec3 tangentNormal = texture(normalMap, fragTexCoord).xyz * 2.0 - 1.0; // Remap to [-1, 1]

    // Build TBN matrix
    vec3 T = normalize(fragTangent);
    vec3 B = normalize(fragBitangent);
    vec3 N = normalize(fragNormal);
    mat3 TBN = mat3(T, B, N);

    // Transform tangent space normal to world space
    return normalize(TBN * tangentNormal);
}

// ============================================================================
// MAIN FUNCTION
// ============================================================================

void main() {
    // STEP 1: Sample material properties

    // Albedo (base color)
    // sRGB → Linear: Textures are stored in sRGB (gamma-encoded)
    // We need linear for lighting math, then convert back to sRGB at the end
    vec3 albedo = material.useAlbedoMap == 1
        ? pow(texture(albedoMap, fragTexCoord).rgb, vec3(2.2))  // Gamma decode
        : material.albedoColor.rgb;

    // Metallic and roughness
    float metallic = material.metallic;
    float roughness = material.roughness;

    if (material.useMetallicRoughnessMap == 1) {
        vec3 mrSample = texture(metallicRoughnessMap, fragTexCoord).rgb;
        metallic = mrSample.b;     // Blue channel = metallic
        roughness = mrSample.g;    // Green channel = roughness
    }

    float ao = material.ao;

    // STEP 2: Get surface normal (with optional normal mapping)
    vec3 N = getNormalFromMap();
    vec3 V = normalize(viewPosition - fragPosition);  // View direction

    // STEP 3: Calculate base reflectivity (F0)
    // DIELECTRICS: F0 ≈ 0.04 (4% reflection for most non-metals)
    // METALS: F0 = albedo (metals have colored reflections!)
    vec3 F0 = vec3(0.04);
    F0 = mix(F0, albedo, metallic);

    // STEP 4: Reflectance equation (Cook-Torrance BRDF)
    vec3 Lo = vec3(0.0);  // Outgoing radiance

    // Iterate over all lights
    for (int i = 0; i < lightsData.numLights; ++i) {
        Light light = lightsData.lights[i];

        vec3 L;              // Light direction
        float attenuation = 1.0;

        int lightType = int(light.position.w);

        // ========== LIGHT TYPE CALCULATIONS ==========

        if (lightType == 0) {
            // POINT LIGHT: Emits in all directions (like a light bulb)
            L = normalize(light.position.xyz - fragPosition);
            float distance = length(light.position.xyz - fragPosition);

            // Inverse square falloff (physically accurate!)
            // Light intensity = 1 / distance²
            attenuation = 1.0 / (distance * distance);

            // Optional: Hard radius cutoff (for performance)
            if (light.params.x > 0.0 && distance > light.params.x) {
                attenuation = 0.0;
            }

        } else if (lightType == 1) {
            // DIRECTIONAL LIGHT: Parallel rays (like the sun)
            // Direction is constant, no attenuation
            L = normalize(-light.direction.xyz);
            attenuation = 1.0;

        } else if (lightType == 2) {
            // SPOT LIGHT: Cone of light (like a flashlight)
            L = normalize(light.position.xyz - fragPosition);
            float distance = length(light.position.xyz - fragPosition);
            attenuation = 1.0 / (distance * distance);

            // Cone falloff (smooth transition at edges)
            float theta = dot(L, normalize(-light.direction.xyz));
            float epsilon = light.params.y - light.params.z;  // inner - outer cutoff
            float intensity = clamp((theta - light.params.z) / epsilon, 0.0, 1.0);
            attenuation *= intensity;
        }

        // Calculate radiance (light energy reaching this point)
        vec3 H = normalize(V + L);  // Halfway vector
        vec3 radiance = light.color.rgb * light.color.w * attenuation;

        // ========== COOK-TORRANCE BRDF ==========

        // D: Normal distribution (how rough?)
        float NDF = DistributionGGX(N, H, roughness);

        // G: Geometry function (self-shadowing)
        float G = GeometrySmith(N, V, L, roughness);

        // F: Fresnel (edge reflections)
        vec3 F = fresnelSchlick(max(dot(H, V), 0.0), F0);

        // Specular term
        vec3 numerator = NDF * G * F;
        float denominator = 4.0 * max(dot(N, V), 0.0) * max(dot(N, L), 0.0) + 0.0001;
        vec3 specular = numerator / denominator;

        // Energy conservation
        // kS = specular contribution (reflected light)
        // kD = diffuse contribution (refracted/scattered light)
        // kS + kD = 1.0 (energy conservation!)
        vec3 kS = F;
        vec3 kD = vec3(1.0) - kS;

        // Metals don't have diffuse lighting (all light is reflected)
        kD *= 1.0 - metallic;

        // Accumulate light contribution
        float NdotL = max(dot(N, L), 0.0);
        Lo += (kD * albedo / PI + specular) * radiance * NdotL;
    }

    // STEP 5: Add ambient lighting (simplified IBL)
    // In a full PBR system, this would be image-based lighting (IBL)
    // from an environment map. For now, we use a constant.
    vec3 ambient = vec3(0.03) * albedo * ao;
    vec3 color = ambient + Lo;

    // STEP 6: HDR tonemapping (Reinhard)
    // HDR: High Dynamic Range - values can exceed 1.0
    // Tonemapping: Compress HDR to [0, 1] for display
    // Reinhard: Simple but effective: x / (x + 1)
    color = color / (color + vec3(1.0));

    // STEP 7: Gamma correction (linear → sRGB)
    // Monitors expect sRGB (gamma-encoded) colors
    // gamma = 2.2 approximation of sRGB curve
    color = pow(color, vec3(1.0/2.2));

    outColor = vec4(color, 1.0);
}
```

**Compile the shaders:**

```bash
glslangValidator -V shaders/pbr.vert -o shaders/vert.spv
glslangValidator -V shaders/pbr.frag -o shaders/frag.spv
```

**Common Mistakes:**

1. **Forgetting gamma correction**: Colors look washed out
2. **No tonemapping**: Bright lights blow out to pure white
3. **Wrong normal transformation**: Lighting looks incorrect on scaled objects
4. **Missing energy conservation**: Materials too bright/dark

---

## Part 4: Material System

### Material.java - PBR Material Properties

**What Is a Material?**

A material defines **how a surface looks**. In PBR, materials are **physically plausible** - they behave like real-world materials.

**Unity Comparison:**

| Unity Material | JECS Material |
|----------------|---------------|
| Shader (Standard) | Hardcoded PBR shader |
| Albedo | albedoColor / albedoTexturePath |
| Metallic | metallic (0-1) |
| Smoothness | 1.0 - roughness |
| Normal Map | normalMapPath |
| Properties inspector | Manual Java class |

```java
package com.jecs.graphics;

import org.joml.Vector4f;

/**
 * Represents a PBR material with textures and properties.
 *
 * PBR WORKFLOW: The "Metallic-Roughness" workflow
 *
 * KEY PARAMETERS:
 * 1. ALBEDO: Base color (no lighting baked in!)
 * 2. METALLIC: 0 = dielectric (plastic, wood), 1 = metal (gold, steel)
 * 3. ROUGHNESS: 0 = smooth (mirror), 1 = rough (matte)
 * 4. AO: Ambient occlusion (shadows in crevices)
 *
 * TEXTURE PACKING:
 * - Albedo Map: RGB = color
 * - Normal Map: RGB = tangent-space normal
 * - Metallic-Roughness Map: R = unused, G = roughness, B = metallic
 *
 * WHY THIS PACKING? glTF standard (Khronos Group)
 * Used by: Unity, Unreal, Blender, Godot
 */
public class Material {

    // Base color (used if albedoTexture is null)
    public Vector4f albedoColor = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);

    // PBR properties
    public float metallic = 0.0f;   // 0 = dielectric, 1 = metal
    public float roughness = 0.5f;  // 0 = smooth mirror, 1 = rough matte
    public float ao = 1.0f;         // Ambient occlusion (1 = no occlusion)

    // Texture paths (null if not used)
    public String albedoTexturePath;
    public String normalMapPath;
    public String metallicRoughnessPath;

    // Loaded textures (set by renderer)
    // transient = don't serialize these handles
    public transient Long albedoTextureHandle;
    public transient Long normalMapHandle;
    public transient Long metallicRoughnessHandle;

    public Material() {}

    /**
     * Creates a material with a solid color.
     *
     * EXAMPLE MATERIALS:
     * - Gold: albedo=(1.0, 0.71, 0.29), metallic=1.0, roughness=0.2
     * - Plastic: albedo=(1.0, 0.0, 0.0), metallic=0.0, roughness=0.5
     * - Rubber: albedo=(0.1, 0.1, 0.1), metallic=0.0, roughness=0.9
     */
    public Material(Vector4f albedoColor, float metallic, float roughness) {
        this.albedoColor = albedoColor;
        this.metallic = metallic;
        this.roughness = roughness;
    }

    public boolean hasAlbedoTexture() {
        return albedoTexturePath != null;
    }

    public boolean hasNormalMap() {
        return normalMapPath != null;
    }

    public boolean hasMetallicRoughnessMap() {
        return metallicRoughnessPath != null;
    }
}
```

**Real-World Material Values:**

| Material | Albedo | Metallic | Roughness |
|----------|--------|----------|-----------|
| **Gold** | (1.0, 0.71, 0.29) | 1.0 | 0.2-0.4 |
| **Steel** | (0.77, 0.78, 0.78) | 1.0 | 0.3-0.6 |
| **Copper** | (0.95, 0.64, 0.54) | 1.0 | 0.2-0.5 |
| **Plastic (Red)** | (0.8, 0.1, 0.1) | 0.0 | 0.4-0.6 |
| **Wood** | (0.6, 0.4, 0.2) | 0.0 | 0.6-0.8 |
| **Rubber** | (0.1, 0.1, 0.1) | 0.0 | 0.8-1.0 |
| **Glass** | (1.0, 1.0, 1.0) | 0.0 | 0.0-0.1 |

**Texturing Best Practices:**

1. **Albedo**: No lighting! (should be flat if viewed under even lighting)
2. **Normal maps**: Purple-ish color (R=128, G=128, B=255)
3. **Roughness**: Varies across surface (scratches, wear)
4. **Metallic**: Usually binary (0 or 1), rarely in-between

---

## Part 5: Lighting System

### Understanding Light Types

**Three Light Types:**

```
┌─────────────────────────────────────────────────────┐
│ POINT LIGHT                                         │
│ Emits in all directions (light bulb)                │
│                                                     │
│         •  ← Light source                          │
│      ↗  ↑  ↖                                       │
│     ←   •   →   Rays emit radially                 │
│      ↙  ↓  ↘                                       │
│                                                     │
│ Attenuation: 1 / (distance²)                       │
│ Use for: Lamps, torches, explosions                │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│ DIRECTIONAL LIGHT                                   │
│ Parallel rays (sun, moon)                          │
│                                                     │
│   ↓   ↓   ↓   ↓   ↓   All rays parallel           │
│   ↓   ↓   ↓   ↓   ↓                               │
│   ↓   ↓   ↓   ↓   ↓                               │
│                                                     │
│ Attenuation: None (infinite distance)              │
│ Use for: Sun, moon, skylight                       │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│ SPOT LIGHT                                          │
│ Cone of light (flashlight, spotlight)              │
│                                                     │
│          •  ← Light source                         │
│          ↓\                                         │
│          ↓  \    Cone with                         │
│          ↓    \  inner/outer                       │
│          ↓      \ cutoff angles                    │
│                                                     │
│ Attenuation: 1/(dist²) × cone falloff              │
│ Use for: Flashlights, car headlights, stage lights │
└─────────────────────────────────────────────────────┘
```

---

### Light.java - ECS Component

**What This Component Represents:**

Each entity with a Light component emits light. The LightingSystem collects all lights and sends them to the shader.

```java
package com.jecs.components;

import com.jecs.ecs.Component;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Light component for the ECS.
 *
 * LIGHT TYPES EXPLAINED:
 *
 * POINT: Omnidirectional light source
 * - Position: Where the light is
 * - Radius: Max distance (optimization - lights don't affect beyond this)
 * - Attenuation: 1 / distance² (inverse square law - physically accurate!)
 *
 * DIRECTIONAL: Infinite parallel rays
 * - Direction: Which way light travels
 * - No position (sun is infinitely far away)
 * - No attenuation (all rays have same intensity)
 *
 * SPOT: Cone-shaped light
 * - Position: Where the light is
 * - Direction: Where cone points
 * - Cutoff angles: Inner (full brightness) and outer (fades to black)
 * - Attenuation: 1 / distance² × cone intensity
 */
public class Light implements Component {

    public enum Type {
        POINT,        // Type 0 in shader
        DIRECTIONAL,  // Type 1 in shader
        SPOT          // Type 2 in shader
    }

    public Type type = Type.POINT;

    // RGB color + intensity (w component)
    // Example: white light at 2x intensity = (1, 1, 1, 2)
    public Vector4f color = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);

    public Vector3f direction = new Vector3f(0.0f, -1.0f, 0.0f);  // For directional/spot

    // Point/spot light parameters
    public float radius = 10.0f;  // Maximum distance (0 = infinite)

    // Spot light parameters
    // Cutoff is stored as cosine for faster comparison in shader
    // cos(12.5°) ≈ 0.976 (inner - full brightness)
    // cos(17.5°) ≈ 0.954 (outer - fades to black)
    public float cutoff = (float) Math.cos(Math.toRadians(12.5));
    public float outerCutoff = (float) Math.cos(Math.toRadians(17.5));

    public Light() {}

    /**
     * Factory method: Create a point light
     *
     * USAGE EXAMPLES:
     * - Torch: radius=10, color=(1, 0.6, 0.2, 2) (orange, 2x intensity)
     * - Light bulb: radius=5, color=(1, 1, 1, 1) (white)
     * - Explosion: radius=20, color=(1, 0.5, 0, 10) (very bright!)
     */
    public static Light createPointLight(Vector4f color, float radius) {
        Light light = new Light();
        light.type = Type.POINT;
        light.color = color;
        light.radius = radius;
        return light;
    }

    /**
     * Factory method: Create a directional light
     *
     * USAGE EXAMPLES:
     * - Sun: direction=(0.5, -1, 0.3), color=(1, 1, 0.9, 1.5) (warm white)
     * - Moon: direction=(0, -1, 0), color=(0.7, 0.7, 1, 0.3) (blue, dim)
     */
    public static Light createDirectionalLight(Vector3f direction, Vector4f color) {
        Light light = new Light();
        light.type = Type.DIRECTIONAL;
        light.direction = new Vector3f(direction).normalize();
        light.color = color;
        return light;
    }

    /**
     * Factory method: Create a spot light
     *
     * USAGE EXAMPLES:
     * - Flashlight: cutoff=12.5°, outer=17.5°, color=(1, 1, 0.9, 1)
     * - Stage spotlight: cutoff=20°, outer=30°, color=(1, 1, 1, 5)
     */
    public static Light createSpotLight(Vector3f direction, Vector4f color,
                                       float cutoffDegrees, float outerCutoffDegrees) {
        Light light = new Light();
        light.type = Type.SPOT;
        light.direction = new Vector3f(direction).normalize();
        light.color = color;
        light.cutoff = (float) Math.cos(Math.toRadians(cutoffDegrees));
        light.outerCutoff = (float) Math.cos(Math.toRadians(outerCutoffDegrees));
        return light;
    }
}
```

---

### LightingSystem.java - Light Data Collection

**What This System Does:**

Collects all lights from the ECS and packs them into a format the shader can read.

**Performance Note:** We limit to 16 lights. Why?

- **GPU uniform size limits**: Some GPUs have 64KB uniform limits
- **Fragment shader cost**: Each light = 100+ shader instructions
- **Real-world usage**: Professional engines use 4-8 lights + light culling

**Advanced Technique: Light Culling**

Professional engines don't send all lights to the shader! They use:
- **Frustum culling**: Only lights visible to camera
- **Tile-based culling**: Divide screen into tiles, find lights per tile
- **Clustered lighting**: 3D grid, find lights per cluster

```java
package com.jecs.systems;

import com.jecs.components.Light;
import com.jecs.components.Transform3D;
import com.jecs.ecs.EntityView;
import com.jecs.ecs.System;
import com.jecs.ecs.World;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages lights and prepares data for shaders.
 *
 * WORKFLOW:
 * 1. update(): Collect all Light components from ECS
 * 2. Transform to LightData format (matches shader struct)
 * 3. writeLightData(): Pack into ByteBuffer for GPU upload
 *
 * WHY THIS SYSTEM?
 * - Decouples ECS from renderer (systems don't know about Vulkan)
 * - Prepares data in shader-friendly format
 * - Handles max light limit (16)
 */
public class LightingSystem extends System {

    /**
     * Light data in shader-friendly format.
     * Must match the GLSL struct EXACTLY (layout and alignment).
     *
     * MEMORY LAYOUT (std140 alignment):
     * - vec4 = 16 bytes (aligned to 16-byte boundary)
     * - Each LightData = 4 × 16 bytes = 64 bytes
     */
    public static class LightData {
        public Vector4f position = new Vector4f();  // xyz = position, w = type
        public Vector4f direction = new Vector4f(); // xyz = direction
        public Vector4f color = new Vector4f();     // xyz = color, w = intensity
        public Vector4f params = new Vector4f();    // x = radius, y = cutoff, z = outer cutoff
    }

    private final List<LightData> lightDataList = new ArrayList<>();
    private static final int MAX_LIGHTS = 16;

    @Override
    public void update(World world, float deltaTime) {
        lightDataList.clear();

        // Gather all lights (up to MAX_LIGHTS)
        world.query(Light.class, Transform3D.class).forEach(entity -> {
            if (lightDataList.size() >= MAX_LIGHTS) {
                return; // Skip if we've reached the limit
            }

            Light light = entity.get(Light.class);
            Transform3D transform = entity.get(Transform3D.class);

            LightData data = new LightData();

            // Position and type (packed into vec4.w)
            data.position.set(transform.position, light.type.ordinal());

            // Direction
            data.direction.set(light.direction, 0.0f);

            // Color and intensity
            data.color.set(light.color);

            // Parameters
            data.params.set(light.radius, light.cutoff, light.outerCutoff, 0.0f);

            lightDataList.add(data);
        });
    }

    /**
     * Writes light data to a ByteBuffer for uniform upload.
     *
     * LAYOUT (matches GLSL):
     * - 16 lights × 64 bytes = 1024 bytes
     * - 1 int (numLights) = 4 bytes
     * - Padding to 16-byte alignment = 12 bytes
     * Total: 1040 bytes
     *
     * WHY PADDING? GPU std140 layout requires:
     * - Scalars (int, float) aligned to 4 bytes
     * - vec2 aligned to 8 bytes
     * - vec3/vec4 aligned to 16 bytes
     * - Structs aligned to 16 bytes
     * - Arrays aligned to 16 bytes
     *
     * Our data ends at 1028 bytes (not 16-byte aligned), so we pad to 1040.
     */
    public void writeLightData(ByteBuffer buffer) {
        // Write all lights (active and padding)
        for (int i = 0; i < MAX_LIGHTS; i++) {
            if (i < lightDataList.size()) {
                LightData light = lightDataList.get(i);

                // Position (vec4 = 16 bytes)
                buffer.putFloat(light.position.x);
                buffer.putFloat(light.position.y);
                buffer.putFloat(light.position.z);
                buffer.putFloat(light.position.w);

                // Direction (vec4 = 16 bytes)
                buffer.putFloat(light.direction.x);
                buffer.putFloat(light.direction.y);
                buffer.putFloat(light.direction.z);
                buffer.putFloat(light.direction.w);

                // Color (vec4 = 16 bytes)
                buffer.putFloat(light.color.x);
                buffer.putFloat(light.color.y);
                buffer.putFloat(light.color.z);
                buffer.putFloat(light.color.w);

                // Params (vec4 = 16 bytes)
                buffer.putFloat(light.params.x);
                buffer.putFloat(light.params.y);
                buffer.putFloat(light.params.z);
                buffer.putFloat(light.params.w);
            } else {
                // Write zeros for unused lights
                for (int j = 0; j < 16; j++) {
                    buffer.putFloat(0.0f);
                }
            }
        }

        // Number of active lights (int = 4 bytes)
        buffer.putInt(lightDataList.size());

        // Padding to 16-byte alignment (12 bytes)
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.putInt(0);
    }

    public int getLightCount() {
        return lightDataList.size();
    }

    /**
     * Returns the size of the light data buffer.
     * Must match shader struct size!
     */
    public static int getLightDataBufferSize() {
        return 1040; // 1024 (lights) + 16 (count + padding)
    }
}
```

---

## Part 6: Putting It All Together

### PBRRendererDemo.java - Complete Demo

**What This Demo Shows:**

A grid of 25 spheres with varying metallic and roughness values, lit by:
- 1 directional light (sun)
- 4 colored point lights (red, green, blue, white)

**Visual Result:**

```
    Roughness →
M   0.0    0.25   0.5    0.75   1.0
e ┌─────┬─────┬─────┬─────┬─────┐
t 0.0│ ○ │ ○ │ ○ │ ○ │ ○ │ ← Dielectric (plastic)
a 0.25│ ○ │ ○ │ ○ │ ○ │ ○ │
l 0.5│ ○ │ ○ │ ○ │ ○ │ ○ │
l 0.75│ ○ │ ○ │ ○ │ ○ │ ○ │
i 1.0│ ○ │ ○ │ ○ │ ○ │ ○ │ ← Metal
c └─────┴─────┴─────┴─────┴─────┘
```

Top row: Smooth plastic → Rough plastic
Bottom row: Smooth metal → Rough metal

(Demo code omitted for brevity - see original chapter for full implementation)

---

## Summary

### What You've Built

In this chapter, you created a **professional-quality PBR renderer** with:

✅ **Complete Vulkan graphics pipeline** with explicit state management
✅ **SPIR-V shader compilation** for platform-independent shaders
✅ **Descriptor sets** for efficient uniform/texture binding
✅ **Vertex and index buffers** with staging buffer pattern
✅ **Physically Based Rendering** using Cook-Torrance BRDF
✅ **Three light types** (point, directional, spot)
✅ **Normal mapping** for surface detail
✅ **HDR rendering** with tonemapping and gamma correction

### Key Concepts Learned

**Graphics API Design:**
- OpenGL vs Vulkan trade-offs
- Immutable pipeline state
- Explicit memory management

**Rendering Theory:**
- PBR metallic-roughness workflow
- Cook-Torrance BRDF components
- Fresnel, geometry, and normal distribution functions

**Performance Techniques:**
- Staging buffers for fast GPU upload
- Device-local memory for rendering
- Light limiting and potential culling

### Professional Engine Comparison

| Feature | Unity | Unreal | JECS |
|---------|-------|--------|------|
| **Rendering API** | Scriptable Render Pipeline | RHI abstraction | Direct Vulkan |
| **Material System** | Shader Graph | Material Editor | Manual PBR |
| **Light Limits** | Real-time: ~8 | Movable: ~4 | 16 (configurable) |
| **PBR Model** | Standard Shader | Physical Material | Cook-Torrance |
| **Compilation** | On-demand | Background | Manual SPIR-V |

### What You Can Do Now

- **Create material libraries**: Wood, metal, plastic, rubber
- **Implement dynamic lighting**: Real-time day/night cycles
- **Add shadows**: Shadow mapping for directional/spot lights
- **Post-processing**: Bloom, SSAO, color grading
- **Optimize rendering**: Frustum culling, occlusion culling

### Next Steps

**Chapter 10: Physics System** - Add realistic collisions and rigidbodies!

**Future Enhancements:**
1. **Shadow mapping** (directional + spot lights)
2. **Image-based lighting (IBL)** (environment reflections)
3. **Cascaded shadow maps** (better outdoor shadows)
4. **Screen-space reflections** (SSR)
5. **Temporal anti-aliasing** (TAA)

**Performance Optimization Ideas:**
1. **Pipeline cache warming** (faster startup)
2. **Descriptor set pooling** (reduce allocations)
3. **Indirect drawing** (GPU-driven rendering)
4. **Mesh LOD system** (level of detail)

You now have rendering quality comparable to professional game engines! 🎨
