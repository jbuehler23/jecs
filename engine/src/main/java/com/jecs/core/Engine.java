package com.jecs.core;

/**
 * Main game engine orchestrator.
 *
 * Manages the game loop with fixed timestep for physics and variable
 * timestep for rendering. Coordinates Window, Time, and Application.
 *
 * Architecture:
 * - Fixed timestep game loop (60 Hz physics)
 * - Variable framerate rendering
 * - Interpolation for smooth visuals
 * - Proper initialization and cleanup ordering
 *
 * Example usage:
 *   Engine engine = new Engine(new MyGame());
 *   engine.run();
 *
 * Game loop structure:
 *   while (running) {
 *       pollInput();
 *       while (shouldFixedUpdate()) {
 *           fixedUpdate();  // Physics at 60 Hz
 *       }
 *       render(alpha);      // Rendering at any Hz
 *   }
 *
 * Performance: Game loop overhead is ~5 microseconds per frame
 *
 * @see <a href="https://gafferongames.com/post/fix_your_timestep/">Fix Your Timestep</a>
 */
public class Engine {

    private Window window;
    private Time time;
    private Application application;
    private boolean running;

    /**
     * Create engine with an application.
     *
     * @param application application to run
     */
    public Engine(Application application) {
        this.application = application;
        this.running = false;
    }

    /**
     * Start the engine and run the game loop.
     *
     * Steps:
     * 1. Create window
     * 2. Initialize application
     * 3. Run game loop
     * 4. Cleanup
     *
     * Blocks until application exits.
     */
    public void run() {
        try {
            init();
            loop();
        } catch (Exception e) {
            System.err.println("Engine error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            cleanup();
        }
    }

    /**
     * Initialize engine and application.
     *
     * Creates window and initializes application resources.
     */
    private void init() {
        System.out.println("\n=== Initializing JECS Engine ===\n");

        // Create window
        window = new Window(
            application.getWindowWidth(),
            application.getWindowHeight(),
            application.getWindowTitle()
        );
        window.create();

        // Create time manager
        time = new Time();

        // Initialize application
        application.init(window, time);

        running = true;
        System.out.println("\n=== Engine Initialized ===\n");
    }

    /**
     * Main game loop with fixed timestep.
     *
     * Loop structure:
     * 1. Update time
     * 2. Process input
     * 3. Fixed updates (physics) in a loop
     * 4. Render with interpolation
     * 5. Check for window close
     */
    private void loop() {
        System.out.println("=== Starting Game Loop ===\n");

        while (running && !window.shouldClose()) {
            // Update time (calculate delta time, accumulate for fixed updates)
            time.update();

            // Poll window events (input, resize, etc.)
            window.pollEvents();

            // Process input (before physics so inputs affect this frame)
            application.processInput();

            // Fixed timestep updates (physics at 60 Hz)
            while (time.shouldFixedUpdate()) {
                application.fixedUpdate(time.getFixedDeltaTimeF());
            }

            // Variable timestep update (rendering, camera, etc.)
            application.update(time.getDeltaTimeF());

            // Render with interpolation alpha for smooth visuals
            application.render(time.getAlpha());

            // Update window title with FPS (optional, every second)
            if (time.getFrameCount() % 60 == 0) {
                window.setTitle(application.getWindowTitle() + " - FPS: " + time.getFPS());
            }
        }

        System.out.println("\n=== Game Loop Ended ===");
    }

    /**
     * Cleanup engine and application resources.
     *
     * Cleanup order:
     * 1. Application resources
     * 2. Window and GLFW
     */
    private void cleanup() {
        System.out.println("\n=== Cleaning Up ===\n");

        if (application != null) {
            application.cleanup();
        }

        if (window != null) {
            window.destroy();
        }

        System.out.println("=== Cleanup Complete ===\n");
    }

    /**
     * Stop the engine (exit game loop).
     */
    public void stop() {
        running = false;
    }

    /**
     * Get window instance.
     *
     * @return window
     */
    public Window getWindow() {
        return window;
    }

    /**
     * Get time instance.
     *
     * @return time manager
     */
    public Time getTime() {
        return time;
    }

    /**
     * Check if engine is running.
     *
     * @return true if game loop is active
     */
    public boolean isRunning() {
        return running;
    }
}
