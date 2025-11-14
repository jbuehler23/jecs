package com.jecs.core;

import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * GLFW window management.
 *
 * Handles window creation, event polling, and lifecycle management.
 * Provides window properties like size, title, and close status.
 *
 * Architecture:
 * - GLFW for cross-platform windowing
 * - Vulkan surface creation support
 * - Input callback registration
 * - Framebuffer resize callbacks
 *
 * Example usage:
 *   Window window = new Window(800, 600, "My Game");
 *   window.create();
 *   while (!window.shouldClose()) {
 *       window.pollEvents();
 *       // ... render ...
 *   }
 *   window.destroy();
 *
 * Performance: pollEvents() costs ~10 microseconds when no events
 *
 * @see <a href="https://www.glfw.org/documentation.html">GLFW Documentation</a>
 */
public class Window {

    private long handle;  // GLFW window handle
    private String title;
    private int width;
    private int height;
    private boolean resizable;
    private boolean framebufferResized;

    // Callbacks
    private FramebufferSizeCallback framebufferSizeCallback;

    /**
     * Create window specification (not yet created).
     *
     * Call create() to actually create the window.
     *
     * @param width window width in pixels
     * @param height window height in pixels
     * @param title window title
     */
    public Window(int width, int height, String title) {
        this.width = width;
        this.height = height;
        this.title = title;
        this.resizable = true;
        this.framebufferResized = false;
    }

    /**
     * Create the GLFW window.
     *
     * Steps:
     * 1. Initialize GLFW
     * 2. Configure window hints (Vulkan, no OpenGL)
     * 3. Create window
     * 4. Setup callbacks
     * 5. Center window on screen
     *
     * Cost: ~50 milliseconds (one-time startup)
     *
     * @throws RuntimeException if window creation fails
     */
    public void create() {
        // Setup error callback
        GLFWErrorCallback.createPrint(System.err).set();

        // Initialize GLFW
        if (!glfwInit()) {
            throw new RuntimeException("Failed to initialize GLFW");
        }

        // Configure GLFW window hints
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);  // No OpenGL context (we're using Vulkan)
        glfwWindowHint(GLFW_RESIZABLE, resizable ? GLFW_TRUE : GLFW_FALSE);
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);  // Hidden until we're ready

        // Create window
        handle = glfwCreateWindow(width, height, title, NULL, NULL);
        if (handle == NULL) {
            throw new RuntimeException("Failed to create GLFW window");
        }

        // Setup framebuffer resize callback
        glfwSetFramebufferSizeCallback(handle, (window, w, h) -> {
            this.width = w;
            this.height = h;
            this.framebufferResized = true;
            if (framebufferSizeCallback != null) {
                framebufferSizeCallback.invoke(w, h);
            }
        });

        // Center window on screen
        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);

            glfwGetWindowSize(handle, pWidth, pHeight);

            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            if (vidmode != null) {
                glfwSetWindowPos(
                    handle,
                    (vidmode.width() - pWidth.get(0)) / 2,
                    (vidmode.height() - pHeight.get(0)) / 2
                );
            }
        }

        // Make window visible
        glfwShowWindow(handle);

        System.out.println("✓ Window created: " + width + "x" + height + " - " + title);
    }

    /**
     * Poll for window events (keyboard, mouse, resize, etc.).
     *
     * Call once per frame at start of game loop.
     *
     * Cost: ~10 microseconds when no events
     */
    public void pollEvents() {
        glfwPollEvents();
    }

    /**
     * Check if window should close (user pressed X or Alt+F4).
     *
     * @return true if window should close
     */
    public boolean shouldClose() {
        return glfwWindowShouldClose(handle);
    }

    /**
     * Request window to close.
     *
     * Sets the close flag, but doesn't actually close the window.
     * Check shouldClose() to detect this.
     */
    public void setShouldClose(boolean shouldClose) {
        glfwSetWindowShouldClose(handle, shouldClose);
    }

    /**
     * Destroy window and cleanup GLFW.
     *
     * Call this when shutting down the application.
     */
    public void destroy() {
        glfwDestroyWindow(handle);
        glfwTerminate();
        glfwSetErrorCallback(null).free();
        System.out.println("✓ Window destroyed");
    }

    /**
     * Check if framebuffer was resized.
     *
     * Returns true once after resize, then resets to false.
     * Use this to recreate swap chain in Vulkan.
     *
     * @return true if framebuffer was resized since last check
     */
    public boolean wasResized() {
        boolean resized = framebufferResized;
        framebufferResized = false;
        return resized;
    }

    /**
     * Set framebuffer size callback.
     *
     * Called when window is resized.
     *
     * @param callback callback to invoke on resize
     */
    public void setFramebufferSizeCallback(FramebufferSizeCallback callback) {
        this.framebufferSizeCallback = callback;
    }

    /**
     * Get GLFW window handle.
     *
     * Used for Vulkan surface creation and input callbacks.
     *
     * @return GLFW window handle
     */
    public long getHandle() {
        return handle;
    }

    /**
     * Get window width in pixels.
     *
     * @return window width
     */
    public int getWidth() {
        return width;
    }

    /**
     * Get window height in pixels.
     *
     * @return window height
     */
    public int getHeight() {
        return height;
    }

    /**
     * Get window title.
     *
     * @return window title
     */
    public String getTitle() {
        return title;
    }

    /**
     * Set window title.
     *
     * @param title new window title
     */
    public void setTitle(String title) {
        this.title = title;
        glfwSetWindowTitle(handle, title);
    }

    /**
     * Check if window is resizable.
     *
     * @return true if window can be resized
     */
    public boolean isResizable() {
        return resizable;
    }

    /**
     * Set window resizable.
     *
     * Must be called before create().
     *
     * @param resizable true to allow resizing
     */
    public void setResizable(boolean resizable) {
        this.resizable = resizable;
    }

    /**
     * Get window aspect ratio.
     *
     * @return width / height
     */
    public float getAspectRatio() {
        return (float) width / (float) height;
    }

    /**
     * Wait for events (blocks until event received).
     *
     * Use this when application is minimized to save CPU.
     */
    public void waitEvents() {
        glfwWaitEvents();
    }

    /**
     * Framebuffer size callback interface.
     */
    @FunctionalInterface
    public interface FramebufferSizeCallback {
        void invoke(int width, int height);
    }
}
