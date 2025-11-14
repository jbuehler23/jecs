package com.jecs.renderer;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

/**
 * Abstraction for a Vulkan image and its associated image view and memory.
 *
 * Handles image creation, memory allocation, and image view creation.
 * Supports textures, framebuffers, depth buffers, and more.
 *
 * Example usage:
 * <pre>
 * // Create texture image
 * Image texture = new Image.Builder(device, physicalDevice)
 *     .setDimensions(width, height)
 *     .setFormat(VK_FORMAT_R8G8B8A8_SRGB)
 *     .setUsage(VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT)
 *     .setTiling(VK_IMAGE_TILING_OPTIMAL)
 *     .setProperties(VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)
 *     .build();
 *
 * // Create image view
 * texture.createView(VK_IMAGE_ASPECT_COLOR_BIT);
 *
 * // Later:
 * texture.cleanup();
 * </pre>
 *
 * @author JECS Engine
 * @version 1.0
 */
public class Image {

    private final VkDevice device;
    private long image;
    private long memory;
    private long view;
    private final int format;
    private final int width;
    private final int height;

    /**
     * Create an image.
     *
     * @param device the Vulkan device
     * @param image the image handle
     * @param memory the memory handle (can be VK_NULL_HANDLE for swap chain images)
     * @param format the image format
     * @param width the image width
     * @param height the image height
     */
    private Image(VkDevice device, long image, long memory, int format, int width, int height) {
        this.device = device;
        this.image = image;
        this.memory = memory;
        this.format = format;
        this.width = width;
        this.height = height;
        this.view = VK_NULL_HANDLE;
    }

    /**
     * Create an image from an existing image handle (e.g., swap chain image).
     *
     * @param device the Vulkan device
     * @param image the image handle
     * @param format the image format
     * @param width the image width
     * @param height the image height
     * @return the image wrapper
     */
    public static Image fromHandle(VkDevice device, long image, int format, int width, int height) {
        return new Image(device, image, VK_NULL_HANDLE, format, width, height);
    }

    /**
     * Get the Vulkan image handle.
     *
     * @return the image handle
     */
    public long getHandle() {
        return image;
    }

    /**
     * Get the image memory handle.
     *
     * @return the memory handle (VK_NULL_HANDLE if not owned)
     */
    public long getMemory() {
        return memory;
    }

    /**
     * Get the image view handle.
     *
     * @return the image view handle (VK_NULL_HANDLE if not created)
     */
    public long getView() {
        return view;
    }

    /**
     * Get the image format.
     *
     * @return the image format
     */
    public int getFormat() {
        return format;
    }

    /**
     * Get the image width.
     *
     * @return the width in pixels
     */
    public int getWidth() {
        return width;
    }

    /**
     * Get the image height.
     *
     * @return the height in pixels
     */
    public int getHeight() {
        return height;
    }

    /**
     * Create an image view for this image.
     *
     * @param aspectMask the aspect mask (e.g., VK_IMAGE_ASPECT_COLOR_BIT)
     * @throws RuntimeException if creation fails
     */
    public void createView(int aspectMask) {
        try (MemoryStack stack = stackPush()) {
            VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack);
            viewInfo.sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO);
            viewInfo.image(image);
            viewInfo.viewType(VK_IMAGE_VIEW_TYPE_2D);
            viewInfo.format(format);

            viewInfo.components()
                .r(VK_COMPONENT_SWIZZLE_IDENTITY)
                .g(VK_COMPONENT_SWIZZLE_IDENTITY)
                .b(VK_COMPONENT_SWIZZLE_IDENTITY)
                .a(VK_COMPONENT_SWIZZLE_IDENTITY);

            viewInfo.subresourceRange()
                .aspectMask(aspectMask)
                .baseMipLevel(0)
                .levelCount(1)
                .baseArrayLayer(0)
                .layerCount(1);

            LongBuffer pImageView = stack.mallocLong(1);
            if (vkCreateImageView(device, viewInfo, null, pImageView) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create image view");
            }

            view = pImageView.get(0);
        }
    }

    /**
     * Destroy the image, its view, and free its memory.
     *
     * Note: For swap chain images, only the view is destroyed.
     */
    public void cleanup() {
        if (view != VK_NULL_HANDLE) {
            vkDestroyImageView(device, view, null);
            view = VK_NULL_HANDLE;
        }
        // Only destroy image and memory if we own them (not swap chain images)
        if (memory != VK_NULL_HANDLE) {
            if (image != VK_NULL_HANDLE) {
                vkDestroyImage(device, image, null);
                image = VK_NULL_HANDLE;
            }
            vkFreeMemory(device, memory, null);
            memory = VK_NULL_HANDLE;
        }
    }

    /**
     * Builder for creating images with a fluent API.
     */
    public static class Builder {
        private final VkDevice device;
        private final VkPhysicalDevice physicalDevice;
        private int width;
        private int height;
        private int format = VK_FORMAT_R8G8B8A8_SRGB;
        private int tiling = VK_IMAGE_TILING_OPTIMAL;
        private int usage;
        private int memoryProperties = VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;

        /**
         * Create a new image builder.
         *
         * @param device the Vulkan device
         * @param physicalDevice the physical device (for memory type selection)
         */
        public Builder(VkDevice device, VkPhysicalDevice physicalDevice) {
            this.device = device;
            this.physicalDevice = physicalDevice;
        }

        /**
         * Set the image dimensions.
         *
         * @param width the width in pixels
         * @param height the height in pixels
         * @return this builder
         */
        public Builder setDimensions(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        /**
         * Set the image format.
         *
         * @param format the image format (e.g., VK_FORMAT_R8G8B8A8_SRGB)
         * @return this builder
         */
        public Builder setFormat(int format) {
            this.format = format;
            return this;
        }

        /**
         * Set the image tiling mode.
         *
         * @param tiling the tiling mode (VK_IMAGE_TILING_OPTIMAL or LINEAR)
         * @return this builder
         */
        public Builder setTiling(int tiling) {
            this.tiling = tiling;
            return this;
        }

        /**
         * Set the image usage flags.
         *
         * @param usage usage flags (e.g., VK_IMAGE_USAGE_SAMPLED_BIT)
         * @return this builder
         */
        public Builder setUsage(int usage) {
            this.usage = usage;
            return this;
        }

        /**
         * Set the memory property flags.
         *
         * @param properties memory properties (e.g., VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)
         * @return this builder
         */
        public Builder setProperties(int properties) {
            this.memoryProperties = properties;
            return this;
        }

        /**
         * Build the image.
         *
         * @return the created image
         * @throws RuntimeException if creation fails
         */
        public Image build() {
            try (MemoryStack stack = stackPush()) {
                // Create image
                VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack);
                imageInfo.sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO);
                imageInfo.imageType(VK_IMAGE_TYPE_2D);
                imageInfo.extent().width(width).height(height).depth(1);
                imageInfo.mipLevels(1);
                imageInfo.arrayLayers(1);
                imageInfo.format(format);
                imageInfo.tiling(tiling);
                imageInfo.initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
                imageInfo.usage(usage);
                imageInfo.sharingMode(VK_SHARING_MODE_EXCLUSIVE);
                imageInfo.samples(VK_SAMPLE_COUNT_1_BIT);

                LongBuffer pImage = stack.mallocLong(1);
                if (vkCreateImage(device, imageInfo, null, pImage) != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create image");
                }
                long image = pImage.get(0);

                // Get memory requirements
                VkMemoryRequirements memRequirements = VkMemoryRequirements.calloc(stack);
                vkGetImageMemoryRequirements(device, image, memRequirements);

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
                    vkDestroyImage(device, image, null);
                    throw new RuntimeException("Failed to allocate image memory");
                }
                long memory = pMemory.get(0);

                // Bind image to memory
                if (vkBindImageMemory(device, image, memory, 0) != VK_SUCCESS) {
                    vkDestroyImage(device, image, null);
                    vkFreeMemory(device, memory, null);
                    throw new RuntimeException("Failed to bind image memory");
                }

                return new Image(device, image, memory, format, width, height);
            }
        }

        /**
         * Find a suitable memory type for the image.
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
