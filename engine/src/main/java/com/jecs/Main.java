package com.jecs;

import org.joml.Vector3f;
import org.lwjgl.Version;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;

import java.nio.IntBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWVulkan.glfwVulkanSupported;
import static org.lwjgl.system.MemoryUtil.NULL;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    static void main() {
        //TIP Press <shortcut actionId="ShowIntentionActions"/> with your caret at the highlighted text
        // to see how IntelliJ IDEA suggests fixing it.
        IO.println("=".repeat(60));
        IO.println("JECS Engine - Environment Verification");
        IO.println("=".repeat(60));

        //Check LWJGL
        IO.println("LWJGL Version: " + Version.getVersion());


        //Check JOML
        Vector3f testVec = new Vector3f(1, 2, 3);
        IO.println("JOML Test: " + testVec);

        //Init GLFW
        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit()) {
            throw new IllegalStateException("Failed to init GLFW");
        }

        // Check Vulkan support
        boolean vulkanSupported = glfwVulkanSupported();
        IO.println("Vulkan supported: " + vulkanSupported);

        if (!vulkanSupported) {
            IO.println("Vulkan not supported! Check drivers and SDK.");
            glfwTerminate();
            System.exit(1);
        }

        // List Vulkan instance extensions
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer count = stack.ints(0);
            VK10.vkEnumerateInstanceExtensionProperties((String) null, count, null);
            IO.println("Vulkan extensions available: " + count.get(0));
        }

        //Create a test window
        glfwDefaultWindowHints();
        //Vulkan, not OpenGL
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
        //Hide window
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);

        long window = glfwCreateWindow(800, 600, "Test Window", NULL, NULL);
        if (window == GLFW_PLATFORM_NULL) {
            throw new RuntimeException("Failed to create a GLFW window");
        }
        IO.println("GLFW Window created (hidden)");

        //Cleanup:
        glfwDestroyWindow(window);
        glfwTerminate();

        IO.println();
        IO.println("DONE!");

    }
}
