package com.jecs.renderer;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

/**
 * Abstraction for a Vulkan shader module.
 *
 * Handles loading SPIR-V bytecode and creating shader modules.
 *
 * Example usage:
 * <pre>
 * Shader vertShader = Shader.load(device, "shaders/vert.spv", VK_SHADER_STAGE_VERTEX_BIT);
 * Shader fragShader = Shader.load(device, "shaders/frag.spv", VK_SHADER_STAGE_FRAGMENT_BIT);
 *
 * // Use in pipeline creation...
 *
 * vertShader.cleanup();
 * fragShader.cleanup();
 * </pre>
 *
 * @author JECS Engine
 * @version 1.0
 */
public class Shader {

    private final VkDevice device;
    private long module;
    private final int stage;
    private final String entryPoint;

    /**
     * Create a shader from a module handle.
     *
     * @param device the Vulkan device
     * @param module the shader module handle
     * @param stage the shader stage (e.g., VK_SHADER_STAGE_VERTEX_BIT)
     * @param entryPoint the entry point function name (usually "main")
     */
    private Shader(VkDevice device, long module, int stage, String entryPoint) {
        this.device = device;
        this.module = module;
        this.stage = stage;
        this.entryPoint = entryPoint;
    }

    /**
     * Load a shader from a SPIR-V file.
     *
     * @param device the Vulkan device
     * @param path the path to the SPIR-V file
     * @param stage the shader stage (e.g., VK_SHADER_STAGE_VERTEX_BIT)
     * @return the loaded shader
     * @throws RuntimeException if loading or creation fails
     */
    public static Shader load(VkDevice device, String path, int stage) {
        return load(device, path, stage, "main");
    }

    /**
     * Load a shader from a SPIR-V file with a custom entry point.
     *
     * @param device the Vulkan device
     * @param path the path to the SPIR-V file
     * @param stage the shader stage (e.g., VK_SHADER_STAGE_VERTEX_BIT)
     * @param entryPoint the entry point function name
     * @return the loaded shader
     * @throws RuntimeException if loading or creation fails
     */
    public static Shader load(VkDevice device, String path, int stage, String entryPoint) {
        try {
            byte[] code = Files.readAllBytes(Paths.get(path));
            return loadFromBytes(device, code, stage, entryPoint);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load shader: " + path, e);
        }
    }

    /**
     * Load a shader from SPIR-V bytecode.
     *
     * @param device the Vulkan device
     * @param code the SPIR-V bytecode
     * @param stage the shader stage
     * @param entryPoint the entry point function name
     * @return the loaded shader
     * @throws RuntimeException if creation fails
     */
    public static Shader loadFromBytes(VkDevice device, byte[] code, int stage, String entryPoint) {
        try (MemoryStack stack = stackPush()) {
            // SPIR-V code must be in a ByteBuffer
            ByteBuffer codeBuffer = stack.malloc(code.length);
            codeBuffer.put(code);
            codeBuffer.flip();

            VkShaderModuleCreateInfo createInfo = VkShaderModuleCreateInfo.calloc(stack);
            createInfo.sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO);
            createInfo.pCode(codeBuffer);

            LongBuffer pShaderModule = stack.mallocLong(1);
            if (vkCreateShaderModule(device, createInfo, null, pShaderModule) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create shader module");
            }

            return new Shader(device, pShaderModule.get(0), stage, entryPoint);
        }
    }

    /**
     * Get the Vulkan shader module handle.
     *
     * @return the shader module handle
     */
    public long getModule() {
        return module;
    }

    /**
     * Get the shader stage.
     *
     * @return the shader stage (e.g., VK_SHADER_STAGE_VERTEX_BIT)
     */
    public int getStage() {
        return stage;
    }

    /**
     * Get the entry point function name.
     *
     * @return the entry point name
     */
    public String getEntryPoint() {
        return entryPoint;
    }

    /**
     * Create a pipeline shader stage create info for this shader.
     *
     * This is a convenience method for pipeline creation.
     *
     * @param stack the memory stack to allocate from
     * @return the shader stage create info
     */
    public VkPipelineShaderStageCreateInfo getStageCreateInfo(MemoryStack stack) {
        VkPipelineShaderStageCreateInfo shaderStage = VkPipelineShaderStageCreateInfo.calloc(stack);
        shaderStage.sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO);
        shaderStage.stage(stage);
        shaderStage.module(module);
        shaderStage.pName(stack.UTF8(entryPoint));
        return shaderStage;
    }

    /**
     * Destroy the shader module.
     */
    public void cleanup() {
        if (module != VK_NULL_HANDLE) {
            vkDestroyShaderModule(device, module, null);
            module = VK_NULL_HANDLE;
        }
    }
}
