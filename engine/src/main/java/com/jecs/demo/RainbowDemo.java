package com.jecs.demo;

import com.jecs.core.Application;
import com.jecs.core.Engine;
import com.jecs.core.Time;
import com.jecs.core.Window;
import com.jecs.renderer.VulkanContext;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.List;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;

/**
 * Rainbow clear screen demo.
 *
 * Demonstrates:
 * - Complete Vulkan initialization
 * - Fixed timestep game loop
 * - Command buffer recording
 * - Animated clear color (rainbow effect)
 *
 * This is the "Hello World" of our engine, proving that:
 * - Window creation works
 * - Vulkan is properly initialized
 * - Game loop runs smoothly
 * - Frame timing is correct
 *
 * Expected result: Smooth rainbow color animation at 60 FPS
 */
public class RainbowDemo implements Application {

    private VulkanContext vulkan;
    private long renderPass;
    private long[] framebuffers;

    private Time time;
    private float colorTime = 0.0f;

    @Override
    public void init(Window window, Time time) {
        this.time = time;

        // Initialize Vulkan
        vulkan = new VulkanContext();
        vulkan.init(window);

        // Create render pass
        createRenderPass();

        // Create framebuffers
        createFramebuffers();

        System.out.println("✓ RainbowDemo initialized\n");
    }

    @Override
    public void processInput() {
        // No input needed for rainbow demo
    }

    @Override
    public void fixedUpdate(float dt) {
        // Update color animation time
        colorTime += dt;
        if (colorTime > 6.28f) {  // 2π for full color cycle
            colorTime = 0.0f;
        }
    }

    @Override
    public void update(float dt) {
        // No variable update logic needed
    }

    @Override
    public void render(float alpha) {
        // Begin frame
        int imageIndex = vulkan.beginFrame();
        if (imageIndex < 0) {
            // Swap chain needs recreation (e.g., window was resized)
            recreateSwapChain();
            return;
        }

        // Record command buffer
        VkCommandBuffer commandBuffer = vulkan.getCurrentCommandBuffer();
        recordCommandBuffer(commandBuffer, imageIndex);

        // Submit command buffer
        vulkan.submitCommandBuffer(commandBuffer);

        // End frame and present
        if (vulkan.endFrame(imageIndex)) {
            recreateSwapChain();
        }
    }

    @Override
    public void cleanup() {
        vulkan.waitIdle();

        // Cleanup framebuffers
        for (long framebuffer : framebuffers) {
            vkDestroyFramebuffer(vulkan.getDevice(), framebuffer, null);
        }

        // Cleanup render pass
        vkDestroyRenderPass(vulkan.getDevice(), renderPass, null);

        // Cleanup Vulkan
        vulkan.cleanup();

        System.out.println("✓ RainbowDemo cleaned up");
    }

    @Override
    public String getWindowTitle() {
        return "JECS - Rainbow Demo";
    }

    /**
     * Create render pass for clear screen.
     */
    private void createRenderPass() {
        try (MemoryStack stack = stackPush()) {
            // Color attachment
            VkAttachmentDescription.Buffer colorAttachment = VkAttachmentDescription.calloc(1, stack);
            colorAttachment.get(0)
                .format(vulkan.getSwapChainImageFormat())
                .samples(VK_SAMPLE_COUNT_1_BIT)
                .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                .finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);

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

            // Subpass dependency
            VkSubpassDependency.Buffer dependency = VkSubpassDependency.calloc(1, stack);
            dependency.get(0)
                .srcSubpass(VK_SUBPASS_EXTERNAL)
                .dstSubpass(0)
                .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                .srcAccessMask(0)
                .dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);

            // Render pass create info
            VkRenderPassCreateInfo renderPassInfo = VkRenderPassCreateInfo.calloc(stack);
            renderPassInfo.sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO);
            renderPassInfo.pAttachments(colorAttachment);
            renderPassInfo.pSubpasses(subpass);
            renderPassInfo.pDependencies(dependency);

            LongBuffer pRenderPass = stack.mallocLong(1);
            if (vkCreateRenderPass(vulkan.getDevice(), renderPassInfo, null, pRenderPass) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create render pass");
            }

            renderPass = pRenderPass.get(0);
            System.out.println("✓ Render pass created");
        }
    }

    /**
     * Create framebuffers for each swap chain image.
     */
    private void createFramebuffers() {
        try (MemoryStack stack = stackPush()) {
            List<Long> imageViews = vulkan.getSwapChainImageViews();
            framebuffers = new long[imageViews.size()];

            VkExtent2D extent = vulkan.getSwapChainExtent();

            for (int i = 0; i < imageViews.size(); i++) {
                LongBuffer attachments = stack.longs(imageViews.get(i));

                VkFramebufferCreateInfo framebufferInfo = VkFramebufferCreateInfo.calloc(stack);
                framebufferInfo.sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO);
                framebufferInfo.renderPass(renderPass);
                framebufferInfo.pAttachments(attachments);
                framebufferInfo.width(extent.width());
                framebufferInfo.height(extent.height());
                framebufferInfo.layers(1);

                LongBuffer pFramebuffer = stack.mallocLong(1);
                if (vkCreateFramebuffer(vulkan.getDevice(), framebufferInfo, null, pFramebuffer) != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create framebuffer");
                }

                framebuffers[i] = pFramebuffer.get(0);
            }

            System.out.println("✓ Framebuffers created (" + framebuffers.length + " framebuffers)");
        }
    }

    /**
     * Record command buffer for rendering.
     */
    private void recordCommandBuffer(VkCommandBuffer commandBuffer, int imageIndex) {
        try (MemoryStack stack = stackPush()) {
            // Begin command buffer
            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack);
            beginInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);

            if (vkBeginCommandBuffer(commandBuffer, beginInfo) != VK_SUCCESS) {
                throw new RuntimeException("Failed to begin recording command buffer");
            }

            // Calculate rainbow color (HSV to RGB with phase shift)
            float r = (float) Math.sin(colorTime) * 0.5f + 0.5f;
            float g = (float) Math.sin(colorTime + 2.09f) * 0.5f + 0.5f;
            float b = (float) Math.sin(colorTime + 4.18f) * 0.5f + 0.5f;

            // Clear values
            VkClearValue.Buffer clearValues = VkClearValue.calloc(1, stack);
            VkClearColorValue clearColor = clearValues.get(0).color();
            clearColor.float32(0, r);
            clearColor.float32(1, g);
            clearColor.float32(2, b);
            clearColor.float32(3, 1.0f);

            // Begin render pass
            VkRenderPassBeginInfo renderPassInfo = VkRenderPassBeginInfo.calloc(stack);
            renderPassInfo.sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO);
            renderPassInfo.renderPass(renderPass);
            renderPassInfo.framebuffer(framebuffers[imageIndex]);

            VkRect2D renderArea = VkRect2D.calloc(stack);
            renderArea.offset(it -> it.set(0, 0));
            renderArea.extent(vulkan.getSwapChainExtent());
            renderPassInfo.renderArea(renderArea);
            renderPassInfo.pClearValues(clearValues);

            vkCmdBeginRenderPass(commandBuffer, renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);

            // No drawing - just clear screen

            vkCmdEndRenderPass(commandBuffer);

            // End command buffer
            if (vkEndCommandBuffer(commandBuffer) != VK_SUCCESS) {
                throw new RuntimeException("Failed to record command buffer");
            }
        }
    }

    /**
     * Recreate swap chain (e.g., after window resize).
     */
    private void recreateSwapChain() {
        vulkan.waitIdle();
        // TODO: Implement swap chain recreation
        System.out.println("Swap chain recreation needed (not yet implemented)");
    }

    /**
     * Main entry point.
     */
    public static void main(String[] args) {
        Engine engine = new Engine(new RainbowDemo());
        engine.run();
    }
}
