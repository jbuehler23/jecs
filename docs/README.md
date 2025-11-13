# Java Game Engine Tutorial Series
## Building a 2D/3D Game Engine with Java 25, ECS, and Vulkan

Welcome to the comprehensive tutorial series for building your own game engine from scratch! This series will guide you through creating a modern, high-performance game engine using cutting-edge Java 25 features, Entity-Component-System (ECS) architecture, and the Vulkan graphics API.

## What You'll Build

By the end of this series, you'll have created:

- **A fully functional 2D/3D game engine** with modern rendering capabilities
- **Entity-Component-System architecture** for flexible, data-oriented game logic
- **Vulkan-based renderer** with 2D sprite batching and 3D mesh rendering
- **Professional game editor** with hierarchy, inspector, and viewport panels
- **Complete game development workflow** including scene serialization, prefabs, input, and audio
- **Performance-optimized systems** leveraging Java 25's virtual threads and compact object headers

## Target Audience

This tutorial series is designed for:

- **Intermediate Java developers** comfortable with OOP, generics, and basic data structures
- Developers **new to graphics programming** who want to learn Vulkan and game engine architecture
- **Game developers** interested in understanding how engines work under the hood
- Engineers looking to explore **ECS architecture** and data-oriented design

### Prerequisites

**Required Knowledge:**
- Java fundamentals (classes, interfaces, generics, collections)
- Basic understanding of linear algebra (vectors, matrices)
- Familiarity with build tools (Gradle recommended)

**Helpful But Not Required:**
- Graphics programming experience (OpenGL, DirectX, Vulkan)
- Game development experience
- Understanding of design patterns

## Why Java 25?

Java 25 (LTS, released September 2025) brings powerful features perfect for game engine development:

- **Virtual Threads**: Lightweight concurrency for parallel systems, asset loading, and physics
- **Pattern Matching**: Cleaner component type checking and system dispatching
- **Compact Object Headers**: Reduced memory overhead for millions of entities/components
- **Scoped Values**: Efficient sharing of frame-global data across systems
- **Structured Concurrency**: Safe parallel task execution with proper lifecycle management

Combined with mature libraries like LWJGL 3.x and JOML, Java 25 provides an excellent platform for high-performance game development.

## Why Vulkan?

Vulkan offers:

- **Explicit control** over GPU resources and synchronization
- **Multi-threaded command buffer generation** for better CPU utilization
- **Lower driver overhead** compared to OpenGL
- **Modern API** designed for current and future hardware
- **Cross-platform** support (Windows, Linux, macOS via MoltenVK, Android)

While verbose, Vulkan teaches fundamental graphics concepts applicable to any modern API (DirectX 12, Metal).

## Why ECS (Entity-Component-System)?

ECS architecture provides:

- **Composition over inheritance**: Build complex entities from simple components
- **Data-oriented design**: Cache-friendly iteration over component arrays
- **Flexibility**: Add/remove behaviors without changing class hierarchies
- **Performance**: Separate data from logic for better optimization
- **Scalability**: Handle thousands of entities with parallel system execution

Used by Unity DOTS, Unreal's Mass Entity, Bevy, and many modern engines.

## Tutorial Structure

### Core Chapters (Foundation)

- **[Chapter 0: Prerequisites & Setup](chapter-00-prerequisites-setup.md)**
  - Environment setup, dependencies, "Hello LWJGL" verification

- **[Chapter 1: Window + Loop + Vulkan Clear](chapter-01-window-and-loop.md)**
  - GLFW window creation, game loop, Vulkan initialization, clear screen

- **[Chapter 2: ECS Core Architecture](chapter-02-ecs-core.md)**
  - World, entities, components, systems, and queries

- **[Chapter 3: Renderer Abstraction](chapter-03-renderer-abstraction.md)**
  - Abstract renderer interface, VulkanRenderer implementation stub

- **[Chapter 4: 2D Sprites + Camera](chapter-04-2d-sprites.md)**
  - Texture loading, sprite batching, 2D camera, ECS-driven rendering

- **[Chapter 5: 3D Meshes + Camera](chapter-05-3d-meshes.md)**
  - 3D transforms, perspective camera, mesh rendering, simple lighting

- **[Chapter 6: Scene Serialization & Prefabs](chapter-06-scene-serialization.md)**
  - Save/load scenes, prefab system, asset management

- **[Chapter 7: Input & Audio Systems](chapter-07-input-audio.md)**
  - Input manager, event system, OpenAL audio integration

- **[Chapter 8: ImGui Editor](chapter-08-editor-basics.md)**
  - Editor UI, hierarchy panel, inspector, viewport, play/pause

### Advanced Chapters (Polish & Optimization)

- **[Chapter 9: Advanced Rendering](chapter-09-advanced-rendering.md)**
  - PBR materials, shadow mapping, post-processing effects

- **[Chapter 10: Physics Integration](chapter-10-physics.md)**
  - Rigidbody dynamics, collision detection, raycasting

- **[Chapter 11: Scripting System](chapter-11-scripting.md)**
  - Script components, hot-reload, engine API access

- **[Chapter 12: ECS Performance Optimization](chapter-12-ecs-optimization.md)**
  - Sparse sets, archetype patterns, parallel systems with virtual threads

- **[Chapter 13: Profiling & Performance](chapter-13-profiling.md)**
  - Java Flight Recorder, frame time analysis, optimization techniques

### Appendices (Reference Material)

- **[Appendix A: Vulkan Fundamentals](appendix-a-vulkan-fundamentals.md)**
  - In-depth Vulkan concepts, pipeline stages, synchronization

- **[Appendix B: Linear Algebra for Games](appendix-b-linear-algebra.md)**
  - Vectors, matrices, quaternions, transformations

- **[Appendix C: ECS Theory & Patterns](appendix-c-ecs-theory.md)**
  - ECS variants, performance characteristics, design tradeoffs

- **[Appendix D: Resources & References](appendix-d-resources.md)**
  - Further reading, community resources, open-source engines

## Getting Started

### 1. Install Prerequisites

**Java 25 JDK:**
```bash
# Download from https://jdk.java.net/25/
# Or use SDKMAN
sdk install java 25-open
```

**Gradle 8.x:**
```bash
# Download from https://gradle.org/install/
# Or use SDKMAN
sdk install gradle 8.10.2
```

**Vulkan SDK:**
- Download from https://vulkan.lunarg.com/
- Includes validation layers, tools, and documentation

### 2. Set Up Project Structure

```bash
# Create project directory
mkdir my-game-engine
cd my-game-engine

# Initialize Gradle project
gradle init --type java-application --dsl groovy --java-version 25
```

### 3. Configure Dependencies

Create or modify `build.gradle`:

```groovy
plugins {
    id 'java'
    id 'application'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

ext {
    lwjglVersion = "3.3.4"
    jomlVersion = "1.10.5"
    imguiVersion = "1.86.11"

    // Platform-specific natives
    lwjglNatives = "natives-windows" // or natives-linux, natives-macos
}

dependencies {
    // LWJGL Core + Modules
    implementation platform("org.lwjgl:lwjgl-bom:$lwjglVersion")

    implementation "org.lwjgl:lwjgl"
    implementation "org.lwjgl:lwjgl-assimp"
    implementation "org.lwjgl:lwjgl-glfw"
    implementation "org.lwjgl:lwjgl-openal"
    implementation "org.lwjgl:lwjgl-stb"
    implementation "org.lwjgl:lwjgl-vulkan"

    // JOML Math Library
    implementation "org.joml:joml:$jomlVersion"

    // ImGui (for editor)
    implementation "io.github.spair:imgui-java-binding:$imguiVersion"
    implementation "io.github.spair:imgui-java-lwjgl3:$imguiVersion"
    implementation "io.github.spair:imgui-java-natives-windows:$imguiVersion" // platform-specific

    // JSON Serialization
    implementation "com.google.code.gson:gson:2.10.1"

    // Logging
    implementation "org.slf4j:slf4j-api:2.0.9"
    implementation "org.slf4j:slf4j-simple:2.0.9"

    // Native libraries (platform-specific)
    runtimeOnly "org.lwjgl:lwjgl::$lwjglNatives"
    runtimeOnly "org.lwjgl:lwjgl-assimp::$lwjglNatives"
    runtimeOnly "org.lwjgl:lwjgl-glfw::$lwjglNatives"
    runtimeOnly "org.lwjgl:lwjgl-openal::$lwjglNatives"
    runtimeOnly "org.lwjgl:lwjgl-stb::$lwjglNatives"

    // Testing
    testImplementation "org.junit.jupiter:junit-jupiter:5.10.1"
}

application {
    mainClass = 'com.yourname.engine.Main'
}

tasks.named('test') {
    useJUnitPlatform()
}

// Enable preview features for Java 25
tasks.withType(JavaCompile) {
    options.compilerArgs += ['--enable-preview']
}

tasks.withType(Test) {
    jvmArgs += ['--enable-preview']
}

tasks.withType(JavaExec) {
    jvmArgs += ['--enable-preview']
}
```

### 4. Verify Installation

Create `src/main/java/com/yourname/engine/Main.java`:

```java
package com.yourname.engine;

import org.lwjgl.*;
import org.lwjgl.glfw.*;
import org.lwjgl.system.*;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.*;

public class Main {
    public static void main(String[] args) {
        System.out.println("LWJGL Version: " + Version.getVersion());
        System.out.println("Java Version: " + System.getProperty("java.version"));

        // Initialize GLFW
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        System.out.println("GLFW initialized successfully!");
        System.out.println("Vulkan supported: " + glfwVulkanSupported());

        glfwTerminate();
        System.out.println("\nSetup verified! Ready to start Chapter 1.");
    }
}
```

Run:
```bash
gradle run
```

Expected output:
```
LWJGL Version: 3.3.4
Java Version: 25
GLFW initialized successfully!
Vulkan supported: true

Setup verified! Ready to start Chapter 1.
```

## Project Structure

Throughout the tutorials, we'll build this structure:

```
my-game-engine/
├── build.gradle
├── settings.gradle
├── src/
│   ├── main/
│   │   ├── java/com/yourname/engine/
│   │   │   ├── core/
│   │   │   │   ├── Application.java
│   │   │   │   ├── Engine.java
│   │   │   │   ├── Window.java
│   │   │   │   └── Time.java
│   │   │   ├── ecs/
│   │   │   │   ├── World.java
│   │   │   │   ├── Entity.java
│   │   │   │   ├── Component.java
│   │   │   │   ├── System.java
│   │   │   │   └── Query.java
│   │   │   ├── renderer/
│   │   │   │   ├── Renderer.java
│   │   │   │   ├── VulkanRenderer.java
│   │   │   │   ├── Mesh.java
│   │   │   │   ├── Texture.java
│   │   │   │   ├── Shader.java
│   │   │   │   └── Material.java
│   │   │   ├── scene/
│   │   │   │   ├── Scene.java
│   │   │   │   ├── Prefab.java
│   │   │   │   └── SceneSerializer.java
│   │   │   ├── input/
│   │   │   │   ├── InputManager.java
│   │   │   │   ├── KeyCode.java
│   │   │   │   └── MouseButton.java
│   │   │   ├── audio/
│   │   │   │   ├── AudioEngine.java
│   │   │   │   ├── AudioSource.java
│   │   │   │   └── AudioListener.java
│   │   │   └── editor/
│   │   │       ├── EditorLayer.java
│   │   │       ├── HierarchyPanel.java
│   │   │       ├── InspectorPanel.java
│   │   │       └── ViewportPanel.java
│   │   └── resources/
│   │       ├── shaders/
│   │       │   ├── sprite.vert.glsl
│   │       │   ├── sprite.frag.glsl
│   │       │   ├── mesh.vert.glsl
│   │       │   └── mesh.frag.glsl
│   │       ├── textures/
│   │       └── models/
│   └── test/
│       └── java/com/yourname/engine/
│           ├── ecs/
│           │   └── WorldTest.java
│           └── scene/
│               └── SerializationTest.java
└── docs/
    └── (this tutorial series)
```

## Learning Path

### Recommended Path (Sequential)
Follow chapters 0-8 in order to build a solid foundation, then explore chapters 9-13 based on your interests.

### Fast Track (Experienced Developers)
- Skim Chapter 0 (verify setup)
- Read Chapters 1-2 carefully (foundation)
- Implement Chapters 3-8 (core features)
- Jump to specific advanced topics as needed

### Theory-First Path
1. Read Appendix C (ECS Theory)
2. Read Appendix A (Vulkan Fundamentals)
3. Follow Chapters 0-8
4. Deep-dive into Chapters 9-13

## Chapter Format

Each chapter follows this structure:

1. **Introduction** - What you'll learn and why it matters
2. **Concepts** - Theory and architectural overview
3. **Prerequisites** - What you need from previous chapters
4. **Implementation** - Step-by-step code with explanations
5. **Testing** - Verify your implementation works
6. **Performance Notes** - Optimization tips and Java 25 features
7. **Exercises** - Extend the feature on your own
8. **Further Reading** - Deep-dive resources
9. **Next Steps** - Preview of the next chapter

## Code Style & Conventions

Throughout the tutorials, we follow these conventions:

**Naming:**
- Classes: `PascalCase` (Entity, VulkanRenderer)
- Methods: `camelCase` (createEntity, beginFrame)
- Constants: `UPPER_SNAKE_CASE` (MAX_ENTITIES, WINDOW_WIDTH)
- Packages: lowercase (core, ecs, renderer)

**Java 25 Features:**
- Use `record` for immutable data (components, config)
- Use `sealed` interfaces for closed hierarchies
- Use pattern matching for type checks
- Leverage virtual threads for concurrent systems

**Comments:**
- Javadoc for public APIs
- Inline comments for complex logic
- TODO comments for future improvements

## Performance Philosophy

This engine prioritizes:

1. **Clarity First**: Readable code over premature optimization
2. **Measure, Then Optimize**: Profile before optimizing
3. **GC-Friendly**: Minimize allocations in hot paths (game loop)
4. **Data-Oriented**: Structure data for cache efficiency
5. **Parallel When Possible**: Leverage virtual threads for concurrent systems

## Getting Help

If you get stuck:

1. **Check the Code**: Each chapter includes complete, working code
2. **Review Prerequisites**: Ensure previous chapters are working
3. **Read Appendices**: In-depth explanations of complex topics
4. **Search Issues**: Common problems often have solutions documented
5. **Ask the Community**: Forums, Discord, Stack Overflow

## Contributing

Found an error or improvement? Contributions welcome:

1. Report issues for bugs or unclear explanations
2. Submit pull requests with fixes
3. Share your implementations and extensions
4. Help other learners in discussions

## License

This tutorial series is released under MIT License. Code samples are provided as-is for educational purposes. You're free to use them in personal or commercial projects.

The game engine you build is **yours** - use it however you like!

## Acknowledgments

This tutorial series builds upon the excellent work of:

- **Vulkan Tutorial** by Alexander Overvoorde
- **LWJGL Documentation** and community
- **Game Programming Patterns** by Robert Nystrom
- **Entity-Component-System** research and implementations
- The broader game development community

## Ready to Begin?

Head over to **[Chapter 0: Prerequisites & Setup](chapter-00-prerequisites-setup.md)** to verify your environment and start building!

---

**Happy Engine Building!** Remember: The journey of building a game engine is as valuable as the destination. Take your time, experiment, and enjoy the learning process.
