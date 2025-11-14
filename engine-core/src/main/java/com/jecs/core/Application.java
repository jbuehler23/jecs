package com.jecs.core;

/**
 * Base application interface for games.
 *
 * Implement this interface to create a game. The Engine will call
 * these methods at appropriate times in the game loop.
 *
 * Lifecycle:
 * 1. init() - Once at startup
 * 2. processInput() - Every frame before physics
 * 3. fixedUpdate() - 60 Hz for physics
 * 4. update() - Every frame for rendering logic
 * 5. render() - Every frame for drawing
 * 6. cleanup() - Once at shutdown
 *
 * Example:
 *   public class MyGame implements Application {
 *       public void init(Window window, Time time) {
 *           // Load resources, create entities
 *       }
 *       public void fixedUpdate(float dt) {
 *           // Physics simulation
 *       }
 *       public void render(float alpha) {
 *           // Draw everything
 *       }
 *       // ... other methods ...
 *   }
 *
 * Then run with:
 *   Engine engine = new Engine(new MyGame());
 *   engine.run();
 *
 * @see Engine
 */
public interface Application {

    /**
     * Initialize application resources.
     *
     * Called once at startup after window is created.
     * Load assets, create entities, setup renderer here.
     *
     * @param window window instance
     * @param time time manager instance
     */
    void init(Window window, Time time);

    /**
     * Process input.
     *
     * Called every frame before physics updates.
     * Query keyboard/mouse state and update input-driven logic.
     *
     * Example:
     *   if (input.isKeyDown(GLFW_KEY_W)) {
     *       player.moveForward();
     *   }
     */
    void processInput();

    /**
     * Fixed timestep update for physics.
     *
     * Called at 60 Hz (16.67ms) for deterministic physics.
     * Update velocities, run collision detection, etc.
     *
     * May be called multiple times per frame if game is running fast,
     * or skipped if game is running slow (with spiral of death protection).
     *
     * @param dt fixed delta time (always 1/60 = 0.01666...)
     */
    void fixedUpdate(float dt);

    /**
     * Variable timestep update for rendering logic.
     *
     * Called once per frame for rendering-related updates.
     * Update camera, animations, UI, etc.
     *
     * @param dt delta time since last frame (variable)
     */
    void update(float dt);

    /**
     * Render the game.
     *
     * Called once per frame to draw everything.
     *
     * @param alpha interpolation factor (0.0 to 1.0) for smooth rendering
     *              between physics ticks. Use to interpolate positions:
     *              renderPos = lerp(previousPos, currentPos, alpha)
     */
    void render(float alpha);

    /**
     * Cleanup application resources.
     *
     * Called once at shutdown before window is destroyed.
     * Free resources, cleanup Vulkan objects, etc.
     */
    void cleanup();

    /**
     * Get window width.
     *
     * @return desired window width in pixels
     */
    default int getWindowWidth() {
        return 800;
    }

    /**
     * Get window height.
     *
     * @return desired window height in pixels
     */
    default int getWindowHeight() {
        return 600;
    }

    /**
     * Get window title.
     *
     * @return window title string
     */
    default String getWindowTitle() {
        return "JECS Game";
    }
}
