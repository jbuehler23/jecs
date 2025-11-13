# Chapter 7: Input & Audio Systems
## Making Games Feel Alive

**What You'll Learn:**
- Complete input system (keyboard, mouse, gamepad)
- Input mapping and action bindings
- OpenAL audio engine integration
- 3D positional audio
- Audio components and systems for ECS
- Sound effects and music playback

**What You'll Build:**
- Polished input manager with action mapping
- Spatial 3D audio system
- Add sound effects to our flight combat game
- Music system with playlist support

**Estimated Time:** 3-4 hours

**Prerequisites:** Chapters 1-6 completed

---

## Introduction: Bringing Games to Life

Our flight combat game works, but it feels empty:
- No feedback when shooting
- No engine sounds
- No explosions
- Silent collisions

**Games are 50% audio!** Let's fix that.

---

## Part 1: Input System

### Step 1: Input Manager

Create `src/main/java/com/yourname/engine/input/InputManager.java`:

```java
package com.yourname.engine.input;

import org.lwjgl.glfw.GLFW;
import static org.lwjgl.glfw.GLFW.*;

/**
 * Centralized input manager for keyboard, mouse, and gamepad.
 */
public class InputManager {

    // Keyboard state
    private boolean[] keys = new boolean[GLFW_KEY_LAST + 1];
    private boolean[] keysLastFrame = new boolean[GLFW_KEY_LAST + 1];

    // Mouse state
    private boolean[] mouseButtons = new boolean[GLFW_MOUSE_BUTTON_LAST + 1];
    private boolean[] mouseButtonsLastFrame = new boolean[GLFW_MOUSE_BUTTON_LAST + 1];
    private double mouseX, mouseY;
    private double lastMouseX, lastMouseY;
    private double mouseDeltaX, mouseDeltaY;
    private double scrollX, scrollY;

    /**
     * Update input state (call once per frame).
     */
    public void update() {
        // Save last frame state
        System.arraycopy(keys, 0, keysLastFrame, 0, keys.length);
        System.arraycopy(mouseButtons, 0, mouseButtonsLastFrame, 0, mouseButtons.length);

        // Update mouse delta
        mouseDeltaX = mouseX - lastMouseX;
        mouseDeltaY = mouseY - lastMouseY;
        lastMouseX = mouseX;
        lastMouseY = mouseY;

        // Reset scroll
        scrollX = 0;
        scrollY = 0;
    }

    // === Keyboard ===

    /**
     * Check if key is currently pressed.
     */
    public boolean isKeyDown(int keyCode) {
        if (keyCode < 0 || keyCode >= keys.length) return false;
        return keys[keyCode];
    }

    /**
     * Check if key was just pressed this frame.
     */
    public boolean isKeyJustPressed(int keyCode) {
        if (keyCode < 0 || keyCode >= keys.length) return false;
        return keys[keyCode] && !keysLastFrame[keyCode];
    }

    /**
     * Check if key was just released this frame.
     */
    public boolean isKeyJustReleased(int keyCode) {
        if (keyCode < 0 || keyCode >= keys.length) return false;
        return !keys[keyCode] && keysLastFrame[keyCode];
    }

    // === Mouse ===

    /**
     * Check if mouse button is currently pressed.
     */
    public boolean isMouseButtonDown(int button) {
        if (button < 0 || button >= mouseButtons.length) return false;
        return mouseButtons[button];
    }

    /**
     * Check if mouse button was just pressed this frame.
     */
    public boolean isMouseButtonJustPressed(int button) {
        if (button < 0 || button >= mouseButtons.length) return false;
        return mouseButtons[button] && !mouseButtonsLastFrame[button];
    }

    /**
     * Check if mouse button was just released this frame.
     */
    public boolean isMouseButtonJustReleased(int button) {
        if (button < 0 || button >= mouseButtons.length) return false;
        return !mouseButtons[button] && mouseButtonsLastFrame[button];
    }

    /**
     * Get mouse position.
     */
    public double getMouseX() { return mouseX; }
    public double getMouseY() { return mouseY; }

    /**
     * Get mouse delta (movement since last frame).
     */
    public double getMouseDeltaX() { return mouseDeltaX; }
    public double getMouseDeltaY() { return mouseDeltaY; }

    /**
     * Get scroll delta.
     */
    public double getScrollX() { return scrollX; }
    public double getScrollY() { return scrollY; }

    // === Internal update methods (called by GLFW callbacks) ===

    void setKeyState(int keyCode, boolean pressed) {
        if (keyCode >= 0 && keyCode < keys.length) {
            keys[keyCode] = pressed;
        }
    }

    void setMouseButtonState(int button, boolean pressed) {
        if (button >= 0 && button < mouseButtons.length) {
            mouseButtons[button] = pressed;
        }
    }

    void setMousePosition(double x, double y) {
        this.mouseX = x;
        this.mouseY = y;
    }

    void addScroll(double xoffset, double yoffset) {
        this.scrollX += xoffset;
        this.scrollY += yoffset;
    }
}
```

### Step 2: Input Action Mapping

Create `src/main/java/com/yourname/engine/input/InputAction.java`:

```java
package com.yourname.engine.input;

import java.util.*;

/**
 * Maps logical actions (e.g., "jump", "shoot") to physical inputs.
 */
public class InputAction {

    private String name;
    private List<Integer> keys = new ArrayList<>();
    private List<Integer> mouseButtons = new ArrayList<>();

    public InputAction(String name) {
        this.name = name;
    }

    /**
     * Bind a keyboard key to this action.
     */
    public InputAction bindKey(int keyCode) {
        keys.add(keyCode);
        return this;
    }

    /**
     * Bind a mouse button to this action.
     */
    public InputAction bindMouseButton(int button) {
        mouseButtons.add(button);
        return this;
    }

    /**
     * Check if this action is currently active.
     */
    public boolean isActive(InputManager input) {
        for (int key : keys) {
            if (input.isKeyDown(key)) return true;
        }
        for (int button : mouseButtons) {
            if (input.isMouseButtonDown(button)) return true;
        }
        return false;
    }

    /**
     * Check if this action was just activated this frame.
     */
    public boolean wasJustActivated(InputManager input) {
        for (int key : keys) {
            if (input.isKeyJustPressed(key)) return true;
        }
        for (int button : mouseButtons) {
            if (input.isMouseButtonJustPressed(button)) return true;
        }
        return false;
    }

    public String getName() {
        return name;
    }
}
```

Create `src/main/java/com/yourname/engine/input/InputMap.java`:

```java
package com.yourname.engine.input;

import java.util.*;

/**
 * Collection of input actions.
 */
public class InputMap {

    private Map<String, InputAction> actions = new HashMap<>();

    /**
     * Create and register an action.
     */
    public InputAction createAction(String name) {
        InputAction action = new InputAction(name);
        actions.put(name, action);
        return action;
    }

    /**
     * Get an action by name.
     */
    public InputAction getAction(String name) {
        return actions.get(name);
    }

    /**
     * Check if an action is active.
     */
    public boolean isActionActive(String name, InputManager input) {
        InputAction action = actions.get(name);
        return action != null && action.isActive(input);
    }

    /**
     * Check if an action was just activated.
     */
    public boolean wasActionJustActivated(String name, InputManager input) {
        InputAction action = actions.get(name);
        return action != null && action.wasJustActivated(input);
    }
}
```

### Step 3: Setup Input Callbacks

Update `Window.java` to register callbacks:

```java
public class Window {
    // ... existing code ...

    private InputManager inputManager;

    public void create() {
        // ... existing window creation ...

        // Setup input callbacks
        setupInputCallbacks();
    }

    private void setupInputCallbacks() {
        // Keyboard callback
        glfwSetKeyCallback(handle, (window, key, scancode, action, mods) -> {
            if (inputManager != null) {
                if (action == GLFW_PRESS) {
                    inputManager.setKeyState(key, true);
                } else if (action == GLFW_RELEASE) {
                    inputManager.setKeyState(key, false);
                }
            }
        });

        // Mouse button callback
        glfwSetMouseButtonCallback(handle, (window, button, action, mods) -> {
            if (inputManager != null) {
                if (action == GLFW_PRESS) {
                    inputManager.setMouseButtonState(button, true);
                } else if (action == GLFW_RELEASE) {
                    inputManager.setMouseButtonState(button, false);
                }
            }
        });

        // Mouse position callback
        glfwSetCursorPosCallback(handle, (window, xpos, ypos) -> {
            if (inputManager != null) {
                inputManager.setMousePosition(xpos, ypos);
            }
        });

        // Scroll callback
        glfwSetScrollCallback(handle, (window, xoffset, yoffset) -> {
            if (inputManager != null) {
                inputManager.addScroll(xoffset, yoffset);
            }
        });
    }

    public void setInputManager(InputManager inputManager) {
        this.inputManager = inputManager;
    }
}
```

---

## Part 2: Audio System

### Step 1: Audio Engine (OpenAL)

First, add OpenAL to `build.gradle`:

```groovy
dependencies {
    // ... existing dependencies ...
    implementation "org.lwjgl:lwjgl-openal:$lwjglVersion"
    runtimeOnly "org.lwjgl:lwjgl-openal:$lwjglVersion:$lwjglNatives"
}
```

Create `src/main/java/com/yourname/engine/audio/AudioEngine.java`:

```java
package com.yourname.engine.audio;

import org.lwjgl.openal.*;
import org.lwjgl.system.MemoryStack;
import java.nio.IntBuffer;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.system.MemoryStack.stackPush;

/**
 * OpenAL audio engine.
 */
public class AudioEngine {

    private long device;
    private long context;

    /**
     * Initialize audio engine.
     */
    public void init() {
        System.out.println("\n=== Initializing Audio Engine ===\n");

        // Open default audio device
        device = alcOpenDevice((CharSequence) null);
        if (device == 0) {
            throw new RuntimeException("Failed to open audio device");
        }

        // Create audio context
        context = alcCreateContext(device, (IntBuffer) null);
        if (context == 0) {
            throw new RuntimeException("Failed to create audio context");
        }

        alcMakeContextCurrent(context);

        // Initialize AL capabilities
        ALCCapabilities alcCaps = ALC.createCapabilities(device);
        ALCapabilities alCaps = AL.createCapabilities(alcCaps);

        System.out.println("✓ Audio engine initialized");
        System.out.println("  Device: " + alcGetString(device, ALC_DEVICE_SPECIFIER));
        System.out.println("  Vendor: " + alGetString(AL_VENDOR));
        System.out.println("  Version: " + alGetString(AL_VERSION));
        System.out.println("  Renderer: " + alGetString(AL_RENDERER));
    }

    /**
     * Set listener position (camera/player position).
     */
    public void setListenerPosition(float x, float y, float z) {
        alListener3f(AL_POSITION, x, y, z);
    }

    /**
     * Set listener velocity.
     */
    public void setListenerVelocity(float x, float y, float z) {
        alListener3f(AL_VELOCITY, x, y, z);
    }

    /**
     * Set listener orientation (forward and up vectors).
     */
    public void setListenerOrientation(float fx, float fy, float fz, float ux, float uy, float uz) {
        try (MemoryStack stack = stackPush()) {
            alListenerfv(AL_ORIENTATION, stack.floats(fx, fy, fz, ux, uy, uz));
        }
    }

    /**
     * Set master volume (0.0 to 1.0).
     */
    public void setMasterVolume(float volume) {
        alListenerf(AL_GAIN, volume);
    }

    /**
     * Cleanup audio engine.
     */
    public void cleanup() {
        alcDestroyContext(context);
        alcCloseDevice(device);
        System.out.println("✓ Audio engine cleaned up");
    }
}
```

### Step 2: Audio Buffer (Load Sound Files)

Create `src/main/java/com/yourname/engine/audio/AudioBuffer.java`:

```java
package com.yourname.engine.audio;

import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.IntBuffer;
import java.nio.ShortBuffer;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.stb.STBVorbis.*;
import static org.lwjgl.system.MemoryStack.stackPush;

/**
 * Audio buffer (loaded sound data).
 */
public class AudioBuffer {

    private int bufferId;

    /**
     * Load audio from OGG file.
     */
    public static AudioBuffer loadOgg(String filePath) {
        try (MemoryStack stack = stackPush()) {
            IntBuffer channelsBuffer = stack.mallocInt(1);
            IntBuffer sampleRateBuffer = stack.mallocInt(1);

            // Load OGG file
            ShortBuffer rawAudioBuffer = stb_vorbis_decode_filename(
                filePath,
                channelsBuffer,
                sampleRateBuffer
            );

            if (rawAudioBuffer == null) {
                throw new RuntimeException("Failed to load audio: " + filePath);
            }

            int channels = channelsBuffer.get(0);
            int sampleRate = sampleRateBuffer.get(0);

            // Determine format
            int format = (channels == 1) ? AL_FORMAT_MONO16 : AL_FORMAT_STEREO16;

            // Create OpenAL buffer
            int bufferId = alGenBuffers();
            alBufferData(bufferId, format, rawAudioBuffer, sampleRate);

            // Free decoded data
            MemoryUtil.memFree(rawAudioBuffer);

            AudioBuffer buffer = new AudioBuffer();
            buffer.bufferId = bufferId;

            System.out.println("✓ Audio loaded: " + filePath +
                " (" + channels + " channels, " + sampleRate + " Hz)");

            return buffer;
        }
    }

    /**
     * Get OpenAL buffer ID.
     */
    public int getBufferId() {
        return bufferId;
    }

    /**
     * Cleanup buffer.
     */
    public void cleanup() {
        alDeleteBuffers(bufferId);
    }
}
```

### Step 3: Audio Source (Plays Sounds)

Create `src/main/java/com/yourname/engine/audio/AudioSource.java`:

```java
package com.yourname.engine.audio;

import static org.lwjgl.openal.AL10.*;

/**
 * Audio source (plays a sound at a position).
 */
public class AudioSource {

    private int sourceId;

    public AudioSource() {
        this.sourceId = alGenSources();
    }

    /**
     * Set the audio buffer to play.
     */
    public void setBuffer(AudioBuffer buffer) {
        alSourcei(sourceId, AL_BUFFER, buffer.getBufferId());
    }

    /**
     * Set source position in 3D space.
     */
    public void setPosition(float x, float y, float z) {
        alSource3f(sourceId, AL_POSITION, x, y, z);
    }

    /**
     * Set source velocity.
     */
    public void setVelocity(float x, float y, float z) {
        alSource3f(sourceId, AL_VELOCITY, x, y, z);
    }

    /**
     * Set volume (0.0 to 1.0).
     */
    public void setVolume(float volume) {
        alSourcef(sourceId, AL_GAIN, volume);
    }

    /**
     * Set pitch (1.0 = normal speed).
     */
    public void setPitch(float pitch) {
        alSourcef(sourceId, AL_PITCH, pitch);
    }

    /**
     * Set whether sound should loop.
     */
    public void setLooping(boolean loop) {
        alSourcei(sourceId, AL_LOOPING, loop ? AL_TRUE : AL_FALSE);
    }

    /**
     * Set rolloff factor (how quickly sound attenuates with distance).
     */
    public void setRolloff(float rolloff) {
        alSourcef(sourceId, AL_ROLLOFF_FACTOR, rolloff);
    }

    /**
     * Set reference distance (distance at which sound is heard at full volume).
     */
    public void setReferenceDistance(float distance) {
        alSourcef(sourceId, AL_REFERENCE_DISTANCE, distance);
    }

    /**
     * Play sound.
     */
    public void play() {
        alSourcePlay(sourceId);
    }

    /**
     * Pause sound.
     */
    public void pause() {
        alSourcePause(sourceId);
    }

    /**
     * Stop sound.
     */
    public void stop() {
        alSourceStop(sourceId);
    }

    /**
     * Check if sound is playing.
     */
    public boolean isPlaying() {
        return alGetSourcei(sourceId, AL_SOURCE_STATE) == AL_PLAYING;
    }

    /**
     * Cleanup source.
     */
    public void cleanup() {
        stop();
        alDeleteSources(sourceId);
    }
}
```

### Step 4: Audio Component (ECS Integration)

Create `src/main/java/com/yourname/engine/audio/AudioComponent.java`:

```java
package com.yourname.engine.audio;

import com.yourname.engine.ecs.Component;

/**
 * Component for playing audio at an entity's position.
 */
public class AudioComponent implements Component {

    public AudioBuffer buffer;
    public AudioSource source;
    public boolean playOnAwake = true;
    public boolean loop = false;
    public float volume = 1.0f;
    public float pitch = 1.0f;

    public AudioComponent(AudioBuffer buffer) {
        this.buffer = buffer;
        this.source = new AudioSource();
        this.source.setBuffer(buffer);
    }

    public void play() {
        if (source != null) {
            source.setVolume(volume);
            source.setPitch(pitch);
            source.setLooping(loop);
            source.play();
        }
    }

    public void stop() {
        if (source != null) {
            source.stop();
        }
    }

    public boolean isPlaying() {
        return source != null && source.isPlaying();
    }

    public void cleanup() {
        if (source != null) {
            source.cleanup();
        }
    }
}
```

### Step 5: Audio System (Updates 3D Positions)

Create `src/main/java/com/yourname/engine/audio/AudioSystem.java`:

```java
package com.yourname.engine.audio;

import com.yourname.engine.components.Transform3D;
import com.yourname.engine.ecs.*;
import com.yourname.engine.renderer.Camera3D;

/**
 * Updates audio source positions to match entity transforms.
 */
public class AudioSystem extends System {

    private AudioEngine audioEngine;
    private Camera3D camera;

    public AudioSystem(AudioEngine audioEngine, Camera3D camera) {
        this.audioEngine = audioEngine;
        this.camera = camera;
    }

    @Override
    public void update(World world, float deltaTime) {
        // Update listener position (camera/player)
        if (camera != null) {
            audioEngine.setListenerPosition(
                camera.getPosition().x,
                camera.getPosition().y,
                camera.getPosition().z
            );

            audioEngine.setListenerOrientation(
                camera.getForward().x,
                camera.getForward().y,
                camera.getForward().z,
                camera.getUp().x,
                camera.getUp().y,
                camera.getUp().z
            );
        }

        // Update audio sources
        world.query(Transform3D.class, AudioComponent.class).forEach(entity -> {
            Transform3D transform = entity.get(Transform3D.class);
            AudioComponent audio = entity.get(AudioComponent.class);

            // Update source position
            if (audio.source != null) {
                audio.source.setPosition(
                    transform.position.x,
                    transform.position.y,
                    transform.position.z
                );

                // Auto-play if configured
                if (audio.playOnAwake && !audio.isPlaying()) {
                    audio.play();
                }
            }
        });
    }
}
```

---

## Step 6: Add Sound to Flight Combat Game

Update `FlightCombatGame.java` to add audio:

```java
public class FlightCombatGame {

    private AudioEngine audioEngine;
    private AudioBuffer shootSound;
    private AudioBuffer explosionSound;
    private AudioBuffer engineSound;
    private AudioBuffer musicBuffer;
    private AudioSource musicSource;

    private void init() {
        // ... existing init ...

        // Initialize audio
        audioEngine = new AudioEngine();
        audioEngine.init();
        audioEngine.setMasterVolume(0.7f);

        // Load sound effects
        try {
            shootSound = AudioBuffer.loadOgg("assets/audio/shoot.ogg");
            explosionSound = AudioBuffer.loadOgg("assets/audio/explosion.ogg");
            engineSound = AudioBuffer.loadOgg("assets/audio/engine.ogg");
        } catch (Exception e) {
            System.err.println("Warning: Failed to load audio files");
            e.printStackTrace();
        }

        // Play background music
        try {
            musicBuffer = AudioBuffer.loadOgg("assets/audio/music.ogg");
            musicSource = new AudioSource();
            musicSource.setBuffer(musicBuffer);
            musicSource.setLooping(true);
            musicSource.setVolume(0.3f);
            musicSource.play();
        } catch (Exception e) {
            System.err.println("Warning: Failed to load music");
        }

        // Add audio system
        world.addSystem(new AudioSystem(audioEngine, camera));
    }

    private void shootProjectile(Vector3f position, Vector3f direction) {
        // ... existing code ...

        // Play shoot sound
        if (shootSound != null) {
            AudioSource shootSource = new AudioSource();
            shootSource.setBuffer(shootSound);
            shootSource.setPosition(position.x, position.y, position.z);
            shootSource.setVolume(0.5f);
            shootSource.play();
        }
    }

    // When enemy dies, play explosion sound
    private void onEnemyDestroyed(Entity enemy) {
        Transform3D transform = world.getComponent(enemy, Transform3D.class);
        if (transform != null && explosionSound != null) {
            AudioSource explosionSource = new AudioSource();
            explosionSource.setBuffer(explosionSound);
            explosionSource.setPosition(
                transform.position.x,
                transform.position.y,
                transform.position.z
            );
            explosionSource.setVolume(0.8f);
            explosionSource.play();
        }
    }

    private void cleanup() {
        // Cleanup audio
        if (musicSource != null) musicSource.cleanup();
        if (shootSound != null) shootSound.cleanup();
        if (explosionSound != null) explosionSound.cleanup();
        if (engineSound != null) engineSound.cleanup();
        if (musicBuffer != null) musicBuffer.cleanup();
        audioEngine.cleanup();

        engine.cleanup();
    }
}
```

---

## What We've Achieved

**Complete Input System:**

- ✅ Input manager with keyboard, mouse support
- ✅ Frame-based input tracking (pressed, just pressed, released)
- ✅ Input action mapping for rebindable controls
- ✅ GLFW callback integration

**Complete Audio System:**

- ✅ OpenAL audio engine
- ✅ 3D positional audio
- ✅ Audio buffer loading (OGG support via STB)
- ✅ Audio sources with volume, pitch, looping
- ✅ ECS audio components and systems
- ✅ Background music and sound effects

**Game Feel Improvements:**

- Shooting sounds
- Explosion effects
- Background music
- Spatial 3D audio (sounds come from entity positions)

---

## Exercises

1. **Add more sounds**: Engine hum, hit sounds, UI beeps
2. **Gamepad support**: Add GLFW joystick input
3. **Audio pools**: Reuse AudioSource objects for performance
4. **Music system**: Playlist, crossfade, intensity layers
5. **Doppler effect**: Implement pitch shift based on velocity

---

## What's Next?

In **Chapter 8**, we'll:

- Add **Dear ImGui editor** for level editing
- Create **entity inspector** to tweak components
- Build **scene hierarchy view**
- Add **performance profiler** overlay

---

**Previous:** [← Chapter 6 - Scene Serialization](chapter-06-scene-serialization.md)
**Next:** [Chapter 8 - ImGui Editor →](chapter-08-editor.md)
