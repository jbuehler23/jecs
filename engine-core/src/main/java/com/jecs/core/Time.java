package com.jecs.core;

/**
 * Time management for fixed timestep game loop.
 *
 * Handles delta time calculation, fixed timestep physics updates,
 * and interpolation alpha for smooth rendering.
 *
 * Architecture:
 * - Fixed timestep (60 Hz) for physics determinism
 * - Variable timestep for rendering smoothness
 * - Accumulator pattern to avoid spiral of death
 * - Interpolation alpha for sub-frame rendering
 *
 * Example usage:
 *   Time time = new Time();
 *   while (running) {
 *       time.update();
 *       while (time.shouldFixedUpdate()) {
 *           physicsUpdate(time.getFixedDeltaTime());
 *       }
 *       render(time.getAlpha());
 *   }
 *
 * Performance: ~1 microsecond per update()
 *
 * @see <a href="https://gafferongames.com/post/fix_your_timestep/">Fix Your Timestep</a>
 */
public class Time {

    // Fixed timestep for physics (60 Hz = 16.67ms per frame)
    private static final double FIXED_TIMESTEP = 1.0 / 60.0;

    // Max accumulator to prevent spiral of death
    // 250ms = 15 frames behind (game will slow down rather than freeze)
    private static final double MAX_ACCUMULATOR = 0.25;

    // Current time tracking
    private double currentTime;
    private double lastTime;
    private double deltaTime;
    private double accumulator;

    // Frame counting
    private long frameCount;
    private double fpsTimer;
    private int fpsCounter;
    private int fps;

    /**
     * Create time manager.
     *
     * Initializes time to current system time.
     */
    public Time() {
        this.currentTime = getCurrentTimeSeconds();
        this.lastTime = currentTime;
        this.deltaTime = 0.0;
        this.accumulator = 0.0;
        this.frameCount = 0;
        this.fpsTimer = 0.0;
        this.fpsCounter = 0;
        this.fps = 0;
    }

    /**
     * Update time tracking (call once per frame at start of game loop).
     *
     * Steps:
     * 1. Calculate delta time since last frame
     * 2. Cap delta time to prevent spiral of death
     * 3. Accumulate time for fixed updates
     * 4. Update FPS counter
     *
     * Cost: ~1 microsecond
     */
    public void update() {
        currentTime = getCurrentTimeSeconds();
        deltaTime = currentTime - lastTime;
        lastTime = currentTime;

        // Cap delta time to prevent spiral of death
        // If game freezes for 1 second, don't try to catch up!
        if (deltaTime > MAX_ACCUMULATOR) {
            deltaTime = MAX_ACCUMULATOR;
        }

        // Accumulate time for fixed updates
        accumulator += deltaTime;

        // FPS counting
        frameCount++;
        fpsCounter++;
        fpsTimer += deltaTime;
        if (fpsTimer >= 1.0) {
            fps = fpsCounter;
            fpsCounter = 0;
            fpsTimer = 0.0;
        }
    }

    /**
     * Check if a fixed update should occur.
     *
     * Call this in a loop until it returns false:
     *   while (time.shouldFixedUpdate()) {
     *       physicsUpdate();
     *   }
     *
     * Consumes one fixed timestep from accumulator.
     *
     * @return true if fixed update should run
     */
    public boolean shouldFixedUpdate() {
        if (accumulator >= FIXED_TIMESTEP) {
            accumulator -= FIXED_TIMESTEP;
            return true;
        }
        return false;
    }

    /**
     * Get interpolation alpha for smooth rendering between physics ticks.
     *
     * Alpha is in range [0, 1]:
     * - 0.0 = just finished a physics tick
     * - 1.0 = about to do next physics tick
     *
     * Use for interpolating positions:
     *   renderPosition = lerp(previousPosition, currentPosition, alpha)
     *
     * This eliminates "jittery" rendering when physics runs at 60 Hz
     * but rendering runs at 144 Hz.
     *
     * @return interpolation alpha (0.0 to 1.0)
     */
    public float getAlpha() {
        return (float) (accumulator / FIXED_TIMESTEP);
    }

    /**
     * Get delta time since last frame (variable, for rendering).
     *
     * Use for camera movement, UI animations, etc.
     * DO NOT use for physics (use fixed timestep instead).
     *
     * @return delta time in seconds
     */
    public double getDeltaTime() {
        return deltaTime;
    }

    /**
     * Get delta time as float (convenience method).
     *
     * @return delta time in seconds
     */
    public float getDeltaTimeF() {
        return (float) deltaTime;
    }

    /**
     * Get fixed timestep (constant 1/60 second).
     *
     * Use for physics updates:
     *   velocity += acceleration * time.getFixedDeltaTime();
     *
     * @return fixed timestep (0.0166... seconds)
     */
    public double getFixedDeltaTime() {
        return FIXED_TIMESTEP;
    }

    /**
     * Get fixed timestep as float (convenience method).
     *
     * @return fixed timestep in seconds
     */
    public float getFixedDeltaTimeF() {
        return (float) FIXED_TIMESTEP;
    }

    /**
     * Get total time since Time creation.
     *
     * @return elapsed time in seconds
     */
    public double getElapsedTime() {
        return currentTime;
    }

    /**
     * Get current FPS (updated once per second).
     *
     * @return frames per second
     */
    public int getFPS() {
        return fps;
    }

    /**
     * Get total frame count since start.
     *
     * @return number of frames rendered
     */
    public long getFrameCount() {
        return frameCount;
    }

    /**
     * Get current time in seconds (high precision).
     *
     * Uses System.nanoTime() for monotonic, high-resolution timing.
     *
     * @return current time in seconds
     */
    private double getCurrentTimeSeconds() {
        return System.nanoTime() / 1_000_000_000.0;
    }
}
