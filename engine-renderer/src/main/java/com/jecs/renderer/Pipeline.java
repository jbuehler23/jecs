package com.jecs.renderer;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

/**
 * Abstraction for a Vulkan graphics pipeline.
 *
 * Handles pipeline creation with configurable shaders, vertex input,
 * rasterization, and other fixed-function stages.
 *
 * Example usage:
 * <pre>
 * Pipeline pipeline = new Pipeline.Builder(device, renderPass, extent)
 *     .addShader(vertShader)
 *     .addShader(fragShader)
 *     .setVertexInput(bindingDescriptions, attributeDescriptions)
 *     .setPrimitiveTopology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
 *     .build();
 *
 * // Use in command buffer...
 *
 * pipeline.cleanup();
 * </pre>
 *
 * @author JECS Engine
 * @version 1.0
 */
public class Pipeline {

    private final VkDevice device;
    private long pipeline;
    private long layout;

    /**
     * Create a pipeline.
     *
     * @param device the Vulkan device
     * @param pipeline the pipeline handle
     * @param layout the pipeline layout handle
     */
    private Pipeline(VkDevice device, long pipeline, long layout) {
        this.device = device;
        this.pipeline = pipeline;
        this.layout = layout;
    }

    /**
     * Get the Vulkan pipeline handle.
     *
     * @return the pipeline handle
     */
    public long getHandle() {
        return pipeline;
    }

    /**
     * Get the pipeline layout handle.
     *
     * @return the layout handle
     */
    public long getLayout() {
        return layout;
    }

    /**
     * Bind this pipeline to a command buffer.
     *
     * @param commandBuffer the command buffer
     */
    public void bind(VkCommandBuffer commandBuffer) {
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
    }

    /**
     * Destroy the pipeline and its layout.
     */
    public void cleanup() {
        if (pipeline != VK_NULL_HANDLE) {
            vkDestroyPipeline(device, pipeline, null);
            pipeline = VK_NULL_HANDLE;
        }
        if (layout != VK_NULL_HANDLE) {
            vkDestroyPipelineLayout(device, layout, null);
            layout = VK_NULL_HANDLE;
        }
    }

    /**
     * Builder for creating graphics pipelines with a fluent API.
     */
    public static class Builder {
        private final VkDevice device;
        private final long renderPass;
        private final VkExtent2D extent;
        private final List<Shader> shaders = new ArrayList<>();

        private VkVertexInputBindingDescription.Buffer bindingDescriptions;
        private VkVertexInputAttributeDescription.Buffer attributeDescriptions;
        private int topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
        private int polygonMode = VK_POLYGON_MODE_FILL;
        private int cullMode = VK_CULL_MODE_BACK_BIT;
        private int frontFace = VK_FRONT_FACE_CLOCKWISE;
        private boolean depthTestEnable = true;
        private boolean depthWriteEnable = true;

        /**
         * Create a new pipeline builder.
         *
         * @param device the Vulkan device
         * @param renderPass the render pass this pipeline will be used with
         * @param extent the viewport extent
         */
        public Builder(VkDevice device, long renderPass, VkExtent2D extent) {
            this.device = device;
            this.renderPass = renderPass;
            this.extent = extent;
        }

        /**
         * Add a shader stage to the pipeline.
         *
         * @param shader the shader to add
         * @return this builder
         */
        public Builder addShader(Shader shader) {
            shaders.add(shader);
            return this;
        }

        /**
         * Set the vertex input configuration.
         *
         * @param bindings vertex binding descriptions
         * @param attributes vertex attribute descriptions
         * @return this builder
         */
        public Builder setVertexInput(
            VkVertexInputBindingDescription.Buffer bindings,
            VkVertexInputAttributeDescription.Buffer attributes
        ) {
            this.bindingDescriptions = bindings;
            this.attributeDescriptions = attributes;
            return this;
        }

        /**
         * Set the primitive topology.
         *
         * @param topology the topology (e.g., VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
         * @return this builder
         */
        public Builder setPrimitiveTopology(int topology) {
            this.topology = topology;
            return this;
        }

        /**
         * Set the polygon rendering mode.
         *
         * @param mode the polygon mode (e.g., VK_POLYGON_MODE_FILL)
         * @return this builder
         */
        public Builder setPolygonMode(int mode) {
            this.polygonMode = mode;
            return this;
        }

        /**
         * Set the face culling mode.
         *
         * @param mode the cull mode (e.g., VK_CULL_MODE_BACK_BIT)
         * @return this builder
         */
        public Builder setCullMode(int mode) {
            this.cullMode = mode;
            return this;
        }

        /**
         * Set the front face winding order.
         *
         * @param face the front face (VK_FRONT_FACE_CLOCKWISE or COUNTER_CLOCKWISE)
         * @return this builder
         */
        public Builder setFrontFace(int face) {
            this.frontFace = face;
            return this;
        }

        /**
         * Enable or disable depth testing.
         *
         * @param enable true to enable depth testing
         * @return this builder
         */
        public Builder setDepthTest(boolean enable) {
            this.depthTestEnable = enable;
            return this;
        }

        /**
         * Enable or disable depth writes.
         *
         * @param enable true to enable depth writes
         * @return this builder
         */
        public Builder setDepthWrite(boolean enable) {
            this.depthWriteEnable = enable;
            return this;
        }

        /**
         * Build the graphics pipeline.
         *
         * @return the created pipeline
         * @throws RuntimeException if creation fails
         */
        public Pipeline build() {
            try (MemoryStack stack = stackPush()) {
                // Shader stages
                VkPipelineShaderStageCreateInfo.Buffer shaderStages =
                    VkPipelineShaderStageCreateInfo.calloc(shaders.size(), stack);
                for (int i = 0; i < shaders.size(); i++) {
                    shaderStages.put(i, shaders.get(i).getStageCreateInfo(stack));
                }

                // Vertex input
                VkPipelineVertexInputStateCreateInfo vertexInputInfo =
                    VkPipelineVertexInputStateCreateInfo.calloc(stack);
                vertexInputInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO);
                if (bindingDescriptions != null) {
                    vertexInputInfo.pVertexBindingDescriptions(bindingDescriptions);
                }
                if (attributeDescriptions != null) {
                    vertexInputInfo.pVertexAttributeDescriptions(attributeDescriptions);
                }

                // Input assembly
                VkPipelineInputAssemblyStateCreateInfo inputAssembly =
                    VkPipelineInputAssemblyStateCreateInfo.calloc(stack);
                inputAssembly.sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO);
                inputAssembly.topology(topology);
                inputAssembly.primitiveRestartEnable(false);

                // Viewport and scissor
                VkViewport.Buffer viewport = VkViewport.calloc(1, stack);
                viewport.x(0.0f);
                viewport.y(0.0f);
                viewport.width(extent.width());
                viewport.height(extent.height());
                viewport.minDepth(0.0f);
                viewport.maxDepth(1.0f);

                VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
                scissor.offset().set(0, 0);
                scissor.extent(extent);

                VkPipelineViewportStateCreateInfo viewportState =
                    VkPipelineViewportStateCreateInfo.calloc(stack);
                viewportState.sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO);
                viewportState.pViewports(viewport);
                viewportState.pScissors(scissor);

                // Rasterization
                VkPipelineRasterizationStateCreateInfo rasterizer =
                    VkPipelineRasterizationStateCreateInfo.calloc(stack);
                rasterizer.sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO);
                rasterizer.depthClampEnable(false);
                rasterizer.rasterizerDiscardEnable(false);
                rasterizer.polygonMode(polygonMode);
                rasterizer.lineWidth(1.0f);
                rasterizer.cullMode(cullMode);
                rasterizer.frontFace(frontFace);
                rasterizer.depthBiasEnable(false);

                // Multisampling
                VkPipelineMultisampleStateCreateInfo multisampling =
                    VkPipelineMultisampleStateCreateInfo.calloc(stack);
                multisampling.sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO);
                multisampling.sampleShadingEnable(false);
                multisampling.rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);

                // Depth and stencil
                VkPipelineDepthStencilStateCreateInfo depthStencil =
                    VkPipelineDepthStencilStateCreateInfo.calloc(stack);
                depthStencil.sType(VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO);
                depthStencil.depthTestEnable(depthTestEnable);
                depthStencil.depthWriteEnable(depthWriteEnable);
                depthStencil.depthCompareOp(VK_COMPARE_OP_LESS);
                depthStencil.depthBoundsTestEnable(false);
                depthStencil.stencilTestEnable(false);

                // Color blending
                VkPipelineColorBlendAttachmentState.Buffer colorBlendAttachment =
                    VkPipelineColorBlendAttachmentState.calloc(1, stack);
                colorBlendAttachment.colorWriteMask(
                    VK_COLOR_COMPONENT_R_BIT |
                    VK_COLOR_COMPONENT_G_BIT |
                    VK_COLOR_COMPONENT_B_BIT |
                    VK_COLOR_COMPONENT_A_BIT
                );
                colorBlendAttachment.blendEnable(false);

                VkPipelineColorBlendStateCreateInfo colorBlending =
                    VkPipelineColorBlendStateCreateInfo.calloc(stack);
                colorBlending.sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO);
                colorBlending.logicOpEnable(false);
                colorBlending.pAttachments(colorBlendAttachment);

                // Pipeline layout
                VkPipelineLayoutCreateInfo pipelineLayoutInfo =
                    VkPipelineLayoutCreateInfo.calloc(stack);
                pipelineLayoutInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO);

                LongBuffer pPipelineLayout = stack.mallocLong(1);
                if (vkCreatePipelineLayout(device, pipelineLayoutInfo, null, pPipelineLayout) != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create pipeline layout");
                }
                long layout = pPipelineLayout.get(0);

                // Graphics pipeline
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
                pipelineInfo.layout(layout);
                pipelineInfo.renderPass(renderPass);
                pipelineInfo.subpass(0);

                LongBuffer pPipeline = stack.mallocLong(1);
                if (vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, pipelineInfo, null, pPipeline) != VK_SUCCESS) {
                    vkDestroyPipelineLayout(device, layout, null);
                    throw new RuntimeException("Failed to create graphics pipeline");
                }

                return new Pipeline(device, pPipeline.get(0), layout);
            }
        }
    }
}
