# Appendix A: Vulkan Fundamentals
## Deep Dive into the Vulkan API

This appendix provides in-depth explanations of Vulkan concepts referenced throughout the tutorial.

---

## Vulkan Architecture

### Instance

The **VkInstance** is your application's connection to the Vulkan library.

```java
// Create instance
VkApplicationInfo appInfo = VkApplicationInfo.calloc()
    .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
    .pApplicationName(stack.UTF8("My Game"))
    .applicationVersion(VK_MAKE_VERSION(1, 0, 0))
    .pEngineName(stack.UTF8("JECS Engine"))
    .engineVersion(VK_MAKE_VERSION(1, 0, 0))
    .apiVersion(VK_API_VERSION_1_3);

VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.calloc()
    .sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
    .pApplicationInfo(appInfo)
    .ppEnabledExtensionNames(extensions)
    .ppEnabledLayerNames(validationLayers);

PointerBuffer pInstance = stack.mallocPointer(1);
vkCreateInstance(createInfo, null, pInstance);
VkInstance instance = new VkInstance(pInstance.get(0), createInfo);
```

**Key Extensions:**
- `VK_KHR_surface`: Window surface support
- `VK_KHR_win32_surface` / `VK_KHR_xcb_surface`: Platform-specific surfaces
- `VK_EXT_debug_utils`: Validation layer messages

---

## Physical & Logical Devices

### Physical Device

Represents a GPU. Query capabilities, select best device.

```java
IntBuffer deviceCount = stack.ints(0);
vkEnumeratePhysicalDevices(instance, deviceCount, null);

PointerBuffer ppPhysicalDevices = stack.mallocPointer(deviceCount.get(0));
vkEnumeratePhysicalDevices(instance, deviceCount, ppPhysicalDevices);

for (int i = 0; i < ppPhysicalDevices.capacity(); i++) {
    VkPhysicalDevice device = new VkPhysicalDevice(ppPhysicalDevices.get(i), instance);

    VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.malloc();
    vkGetPhysicalDeviceProperties(device, properties);

    System.out.println("GPU: " + properties.deviceNameString());
    // Score device based on features, select best
}
```

### Logical Device

Interface to the GPU. Create queues, allocate resources.

```java
VkDeviceQueueCreateInfo queueCreateInfo = VkDeviceQueueCreateInfo.calloc()
    .sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
    .queueFamilyIndex(graphicsFamilyIndex)
    .pQueuePriorities(stack.floats(1.0f));

VkDeviceCreateInfo createInfo = VkDeviceCreateInfo.calloc()
    .sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
    .pQueueCreateInfos(queueCreateInfo)
    .pEnabledFeatures(deviceFeatures)
    .ppEnabledExtensionNames(stack.pointers(stack.UTF8(VK_KHR_SWAPCHAIN_EXTENSION_NAME)));

PointerBuffer pDevice = stack.mallocPointer(1);
vkCreateDevice(physicalDevice, createInfo, null, pDevice);
VkDevice device = new VkDevice(pDevice.get(0), physicalDevice, createInfo);
```

---

## Swap Chain

Ring buffer of images presented to the window.

**Double buffering:** 2 images (render to back while front displays)
**Triple buffering:** 3 images (smoother, more latency)

```java
VkSwapchainCreateInfoKHR createInfo = VkSwapchainCreateInfoKHR.calloc()
    .sType(VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR)
    .surface(surface)
    .minImageCount(imageCount)
    .imageFormat(surfaceFormat.format())
    .imageColorSpace(surfaceFormat.colorSpace())
    .imageExtent(extent)
    .imageArrayLayers(1)
    .imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
    .imageSharingMode(VK_SHARING_MODE_EXCLUSIVE)
    .preTransform(capabilities.currentTransform())
    .compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
    .presentMode(presentMode)
    .clipped(true);

LongBuffer pSwapChain = stack.longs(VK_NULL_HANDLE);
vkCreateSwapchainKHR(device, createInfo, null, pSwapChain);
```

---

## Render Passes

Describes rendering operations (attachments, subpasses, dependencies).

```java
VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.calloc(1);
attachments.get(0)
    .format(swapChainImageFormat)
    .samples(VK_SAMPLE_COUNT_1_BIT)
    .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
    .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
    .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
    .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
    .finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);

VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1);
subpass.get(0)
    .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
    .colorAttachmentCount(1)
    .pColorAttachments(colorAttachmentRef);

VkRenderPassCreateInfo renderPassInfo = VkRenderPassCreateInfo.calloc()
    .sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
    .pAttachments(attachments)
    .pSubpasses(subpass)
    .pDependencies(dependency);

LongBuffer pRenderPass = stack.longs(VK_NULL_HANDLE);
vkCreateRenderPass(device, renderPassInfo, null, pRenderPass);
```

---

## Graphics Pipeline

Configures all rendering stages (shaders, rasterization, blending).

**Stages:**
1. Vertex Input
2. Input Assembly
3. Vertex Shader
4. Tessellation (optional)
5. Geometry Shader (optional)
6. Rasterization
7. Fragment Shader
8. Color Blending
9. Framebuffer

```java
VkGraphicsPipelineCreateInfo.Buffer pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1);
pipelineInfo.get(0)
    .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
    .pStages(shaderStages)
    .pVertexInputState(vertexInputInfo)
    .pInputAssemblyState(inputAssembly)
    .pViewportState(viewportState)
    .pRasterizationState(rasterizer)
    .pMultisampleState(multisampling)
    .pDepthStencilState(depthStencil)
    .pColorBlendState(colorBlending)
    .layout(pipelineLayout)
    .renderPass(renderPass)
    .subpass(0);

LongBuffer pPipeline = stack.longs(VK_NULL_HANDLE);
vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, pipelineInfo, null, pPipeline);
```

---

## Command Buffers

Record GPU commands, submit to queues for execution.

```java
// Allocate
VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc()
    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
    .commandPool(commandPool)
    .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
    .commandBufferCount(1);

PointerBuffer pCommandBuffer = stack.mallocPointer(1);
vkAllocateCommandBuffers(device, allocInfo, pCommandBuffer);
VkCommandBuffer commandBuffer = new VkCommandBuffer(pCommandBuffer.get(0), device);

// Record
VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc()
    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);

vkBeginCommandBuffer(commandBuffer, beginInfo);
// ... vkCmdBindPipeline, vkCmdDraw, etc. ...
vkEndCommandBuffer(commandBuffer);

// Submit
VkSubmitInfo submitInfo = VkSubmitInfo.calloc()
    .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
    .pCommandBuffers(stack.pointers(commandBuffer));

vkQueueSubmit(graphicsQueue, submitInfo, fence);
```

---

## Synchronization

### Semaphores

GPU-GPU sync (signal when done, wait before starting).

```java
VkSemaphoreCreateInfo semaphoreInfo = VkSemaphoreCreateInfo.calloc()
    .sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);

LongBuffer pSemaphore = stack.longs(VK_NULL_HANDLE);
vkCreateSemaphore(device, semaphoreInfo, null, pSemaphore);
```

### Fences

CPU-GPU sync (wait for GPU to finish before proceeding).

```java
VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.calloc()
    .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)
    .flags(VK_FENCE_CREATE_SIGNALED_BIT); // Start signaled

LongBuffer pFence = stack.longs(VK_NULL_HANDLE);
vkCreateFence(device, fenceInfo, null, pFence);

// Wait for fence
vkWaitForFences(device, pFence, true, UINT64_MAX);
vkResetFences(device, pFence);
```

---

## Memory Management

Vulkan requires explicit memory allocation for buffers/images.

```java
// Create buffer
VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc()
    .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
    .size(1024 * 1024) // 1 MB
    .usage(VK_BUFFER_USAGE_VERTEX_BUFFER_BIT)
    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);

LongBuffer pBuffer = stack.longs(VK_NULL_HANDLE);
vkCreateBuffer(device, bufferInfo, null, pBuffer);

// Query memory requirements
VkMemoryRequirements memRequirements = VkMemoryRequirements.malloc();
vkGetBufferMemoryRequirements(device, pBuffer.get(0), memRequirements);

// Allocate memory
VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc()
    .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
    .allocationSize(memRequirements.size())
    .memoryTypeIndex(findMemoryType(memRequirements.memoryTypeBits(),
        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT));

LongBuffer pMemory = stack.longs(VK_NULL_HANDLE);
vkAllocateMemory(device, allocInfo, null, pMemory);

// Bind buffer to memory
vkBindBufferMemory(device, pBuffer.get(0), pMemory.get(0), 0);
```

**Best Practice:** Use VMA (Vulkan Memory Allocator) library for production.

---

## Descriptor Sets

Bind resources (buffers, textures) to shaders.

```java
// Descriptor pool
VkDescriptorPoolSize.Buffer poolSize = VkDescriptorPoolSize.calloc(1);
poolSize.get(0)
    .type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
    .descriptorCount(10);

VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc()
    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
    .pPoolSizes(poolSize)
    .maxSets(10);

LongBuffer pDescriptorPool = stack.longs(VK_NULL_HANDLE);
vkCreateDescriptorPool(device, poolInfo, null, pDescriptorPool);

// Allocate descriptor set
VkDescriptorSetAllocateInfo allocInfo = VkDescriptorSetAllocateInfo.calloc()
    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
    .descriptorPool(pDescriptorPool.get(0))
    .pSetLayouts(stack.longs(descriptorSetLayout));

LongBuffer pDescriptorSet = stack.longs(VK_NULL_HANDLE);
vkAllocateDescriptorSets(device, allocInfo, pDescriptorSet);

// Update descriptor set
VkWriteDescriptorSet.Buffer descriptorWrite = VkWriteDescriptorSet.calloc(1);
descriptorWrite.get(0)
    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
    .dstSet(pDescriptorSet.get(0))
    .dstBinding(0)
    .dstArrayElement(0)
    .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
    .descriptorCount(1)
    .pBufferInfo(bufferInfo);

vkUpdateDescriptorSets(device, descriptorWrite, null);
```

---

## Further Reading

- **Vulkan Specification**: [khronos.org/vulkan](https://www.khronos.org/vulkan/)
- **Vulkan Tutorial**: [vulkan-tutorial.com](https://vulkan-tutorial.com/)
- **Vulkan Guide**: [github.com/KhronosGroup/Vulkan-Guide](https://github.com/KhronosGroup/Vulkan-Guide)
- **LWJGL Vulkan Demos**: [github.com/LWJGL/lwjgl3-demos/tree/main/src/org/lwjgl/demo/vulkan](https://github.com/LWJGL/lwjgl3-demos)

---

**[Back to README](README.md)**
