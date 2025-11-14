package com.jecs.renderer;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

/**
 * Abstraction for a Vulkan buffer and its associated memory.
 *
 * Handles buffer creation, memory allocation, and data transfer.
 * Supports vertex buffers, index buffers, uniform buffers, and more.
 *
 * Example usage:
 * <pre>
 * // Create vertex buffer
 * Buffer vertexBuffer = new Buffer.Builder(device, physicalDevice)
 *     .setSize(vertices.remaining())
 *     .setUsage(VK_BUFFER_USAGE_VERTEX_BUFFER_BIT)
 *     .setProperties(VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT)
 *     .build();
 *
 * // Upload data
 * vertexBuffer.upload(vertices);
 *
 * // Later:
 * vertexBuffer.cleanup();
 * </pre>
 *
 * @author JECS Engine
 * @version 1.0
 */
public class Buffer {

    private final VkDevice device;
    private long buffer;
    private long memory;
    private final long size;

    /**
     * Create a buffer.
     *
     * @param device the Vulkan device
     * @param buffer the buffer handle
     * @param memory the memory handle
     * @param size the buffer size in bytes
     */
    private Buffer(VkDevice device, long buffer, long memory, long size) {
        this.device = device;
        this.buffer = buffer;
        this.memory = memory;
        this.size = size;
    }

    /**
     * Get the Vulkan buffer handle.
     *
     * @return the buffer handle
     */
    public long getHandle() {
        return buffer;
    }

    /**
     * Get the buffer memory handle.
     *
     * @return the memory handle
     */
    public long getMemory() {
        return memory;
    }

    /**
     * Get the buffer size in bytes.
     *
     * @return the size in bytes
     */
    public long getSize() {
        return size;
    }

    /**
     * Upload data to the buffer.
     *
     * This maps the buffer memory, copies the data, and unmaps.
     * The buffer must be host-visible for this to work.
     *
     * @param data the data to upload
     * @throws RuntimeException if mapping fails
     */
    public void upload(ByteBuffer data) {
        try (MemoryStack stack = stackPush()) {
            PointerBuffer pData = stack.mallocPointer(1);

            if (vkMapMemory(device, memory, 0, size, 0, pData) != VK_SUCCESS) {
                throw new RuntimeException("Failed to map buffer memory");
            }

            ByteBuffer mappedMemory = pData.getByteBuffer(0, (int) size);
            mappedMemory.put(data);
            data.rewind();

            vkUnmapMemory(device, memory);
        }
    }

    /**
     * Map the buffer memory for reading/writing.
     *
     * Remember to call unmap() when done.
     *
     * @return ByteBuffer view of the mapped memory
     * @throws RuntimeException if mapping fails
     */
    public ByteBuffer map() {
        try (MemoryStack stack = stackPush()) {
            PointerBuffer pData = stack.mallocPointer(1);

            if (vkMapMemory(device, memory, 0, size, 0, pData) != VK_SUCCESS) {
                throw new RuntimeException("Failed to map buffer memory");
            }

            return pData.getByteBuffer(0, (int) size);
        }
    }

    /**
     * Unmap the buffer memory.
     */
    public void unmap() {
        vkUnmapMemory(device, memory);
    }

    /**
     * Destroy the buffer and free its memory.
     */
    public void cleanup() {
        if (buffer != VK_NULL_HANDLE) {
            vkDestroyBuffer(device, buffer, null);
            buffer = VK_NULL_HANDLE;
        }
        if (memory != VK_NULL_HANDLE) {
            vkFreeMemory(device, memory, null);
            memory = VK_NULL_HANDLE;
        }
    }

    /**
     * Builder for creating buffers with a fluent API.
     */
    public static class Builder {
        private final VkDevice device;
        private final VkPhysicalDevice physicalDevice;
        private long size;
        private int usage;
        private int memoryProperties;

        /**
         * Create a new buffer builder.
         *
         * @param device the Vulkan device
         * @param physicalDevice the physical device (for memory type selection)
         */
        public Builder(VkDevice device, VkPhysicalDevice physicalDevice) {
            this.device = device;
            this.physicalDevice = physicalDevice;
        }

        /**
         * Set the buffer size in bytes.
         *
         * @param size the size in bytes
         * @return this builder
         */
        public Builder setSize(long size) {
            this.size = size;
            return this;
        }

        /**
         * Set the buffer usage flags.
         *
         * @param usage usage flags (e.g., VK_BUFFER_USAGE_VERTEX_BUFFER_BIT)
         * @return this builder
         */
        public Builder setUsage(int usage) {
            this.usage = usage;
            return this;
        }

        /**
         * Set the memory property flags.
         *
         * @param properties memory properties (e.g., VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT)
         * @return this builder
         */
        public Builder setProperties(int properties) {
            this.memoryProperties = properties;
            return this;
        }

        /**
         * Build the buffer.
         *
         * @return the created buffer
         * @throws RuntimeException if creation fails
         */
        public Buffer build() {
            try (MemoryStack stack = stackPush()) {
                // Create buffer
                VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack);
                bufferInfo.sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO);
                bufferInfo.size(size);
                bufferInfo.usage(usage);
                bufferInfo.sharingMode(VK_SHARING_MODE_EXCLUSIVE);

                LongBuffer pBuffer = stack.mallocLong(1);
                if (vkCreateBuffer(device, bufferInfo, null, pBuffer) != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create buffer");
                }
                long buffer = pBuffer.get(0);

                // Get memory requirements
                VkMemoryRequirements memRequirements = VkMemoryRequirements.calloc(stack);
                vkGetBufferMemoryRequirements(device, buffer, memRequirements);

                // Allocate memory
                VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack);
                allocInfo.sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);
                allocInfo.allocationSize(memRequirements.size());
                allocInfo.memoryTypeIndex(findMemoryType(
                    memRequirements.memoryTypeBits(),
                    memoryProperties,
                    stack
                ));

                LongBuffer pMemory = stack.mallocLong(1);
                if (vkAllocateMemory(device, allocInfo, null, pMemory) != VK_SUCCESS) {
                    vkDestroyBuffer(device, buffer, null);
                    throw new RuntimeException("Failed to allocate buffer memory");
                }
                long memory = pMemory.get(0);

                // Bind buffer to memory
                if (vkBindBufferMemory(device, buffer, memory, 0) != VK_SUCCESS) {
                    vkDestroyBuffer(device, buffer, null);
                    vkFreeMemory(device, memory, null);
                    throw new RuntimeException("Failed to bind buffer memory");
                }

                return new Buffer(device, buffer, memory, size);
            }
        }

        /**
         * Find a suitable memory type for the buffer.
         *
         * @param typeFilter the memory type bits from memory requirements
         * @param properties the desired memory properties
         * @param stack the memory stack
         * @return the memory type index
         * @throws RuntimeException if no suitable memory type is found
         */
        private int findMemoryType(int typeFilter, int properties, MemoryStack stack) {
            VkPhysicalDeviceMemoryProperties memProperties = VkPhysicalDeviceMemoryProperties.calloc(stack);
            vkGetPhysicalDeviceMemoryProperties(physicalDevice, memProperties);

            for (int i = 0; i < memProperties.memoryTypeCount(); i++) {
                if ((typeFilter & (1 << i)) != 0 &&
                    (memProperties.memoryTypes(i).propertyFlags() & properties) == properties) {
                    return i;
                }
            }

            throw new RuntimeException("Failed to find suitable memory type");
        }
    }
}
