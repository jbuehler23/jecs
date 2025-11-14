package com.jecs.renderer;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;

/**
 * Abstraction for a Vulkan render pass.
 *
 * A render pass describes the structure of rendering operations including:
 * - Attachments (color, depth, stencil)
 * - Subpasses and their dependencies
 * - Load/store operations
 *
 * Example usage:
 * <pre>
 * RenderPass renderPass = new RenderPass.Builder(device)
 *     .addColorAttachment(swapChainFormat, VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)
 *     .addSubpassDependency()
 *     .build();
 *
 * // Later:
 * renderPass.cleanup();
 * </pre>
 *
 * @author JECS Engine
 * @version 1.0
 */
public class RenderPass {

    private final VkDevice device;
    private long handle;

    /**
     * Create a render pass from a handle.
     *
     * @param device the Vulkan device
     * @param handle the render pass handle
     */
    private RenderPass(VkDevice device, long handle) {
        this.device = device;
        this.handle = handle;
    }

    /**
     * Get the Vulkan render pass handle.
     *
     * @return the render pass handle
     */
    public long getHandle() {
        return handle;
    }

    /**
     * Destroy the render pass.
     */
    public void cleanup() {
        if (handle != VK_NULL_HANDLE) {
            vkDestroyRenderPass(device, handle, null);
            handle = VK_NULL_HANDLE;
        }
    }

    /**
     * Builder for creating render passes with a fluent API.
     */
    public static class Builder {
        private final VkDevice device;
        private int colorAttachmentFormat;
        private int colorFinalLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
        private int loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;
        private int storeOp = VK_ATTACHMENT_STORE_OP_STORE;
        private boolean addDependency = true;

        /**
         * Create a new render pass builder.
         *
         * @param device the Vulkan device
         */
        public Builder(VkDevice device) {
            this.device = device;
        }

        /**
         * Add a color attachment to the render pass.
         *
         * @param format the image format (e.g., VK_FORMAT_B8G8R8A8_SRGB)
         * @param finalLayout the final image layout after rendering
         * @return this builder
         */
        public Builder addColorAttachment(int format, int finalLayout) {
            this.colorAttachmentFormat = format;
            this.colorFinalLayout = finalLayout;
            return this;
        }

        /**
         * Set the load operation for the color attachment.
         *
         * @param loadOp the load operation (e.g., VK_ATTACHMENT_LOAD_OP_CLEAR)
         * @return this builder
         */
        public Builder setLoadOp(int loadOp) {
            this.loadOp = loadOp;
            return this;
        }

        /**
         * Set the store operation for the color attachment.
         *
         * @param storeOp the store operation (e.g., VK_ATTACHMENT_STORE_OP_STORE)
         * @return this builder
         */
        public Builder setStoreOp(int storeOp) {
            this.storeOp = storeOp;
            return this;
        }

        /**
         * Enable or disable the default subpass dependency.
         *
         * The default dependency synchronizes color attachment writes
         * from external operations to the first subpass.
         *
         * @param add true to add dependency, false to skip
         * @return this builder
         */
        public Builder addSubpassDependency(boolean add) {
            this.addDependency = add;
            return this;
        }

        /**
         * Build the render pass.
         *
         * @return the created render pass
         * @throws RuntimeException if creation fails
         */
        public RenderPass build() {
            try (MemoryStack stack = stackPush()) {
                // Color attachment
                VkAttachmentDescription.Buffer colorAttachment = VkAttachmentDescription.calloc(1, stack);
                colorAttachment.get(0)
                    .format(colorAttachmentFormat)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .loadOp(loadOp)
                    .storeOp(storeOp)
                    .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .finalLayout(colorFinalLayout);

                // Attachment reference
                VkAttachmentReference.Buffer colorAttachmentRef = VkAttachmentReference.calloc(1, stack);
                colorAttachmentRef.get(0)
                    .attachment(0)
                    .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

                // Subpass
                VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack);
                subpass.get(0)
                    .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                    .colorAttachmentCount(1)
                    .pColorAttachments(colorAttachmentRef);

                // Render pass create info
                VkRenderPassCreateInfo renderPassInfo = VkRenderPassCreateInfo.calloc(stack);
                renderPassInfo.sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO);
                renderPassInfo.pAttachments(colorAttachment);
                renderPassInfo.pSubpasses(subpass);

                // Optional subpass dependency
                if (addDependency) {
                    VkSubpassDependency.Buffer dependency = VkSubpassDependency.calloc(1, stack);
                    dependency.get(0)
                        .srcSubpass(VK_SUBPASS_EXTERNAL)
                        .dstSubpass(0)
                        .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                        .srcAccessMask(0)
                        .dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                        .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);

                    renderPassInfo.pDependencies(dependency);
                }

                // Create render pass
                LongBuffer pRenderPass = stack.mallocLong(1);
                if (vkCreateRenderPass(device, renderPassInfo, null, pRenderPass) != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create render pass");
                }

                return new RenderPass(device, pRenderPass.get(0));
            }
        }
    }
}
