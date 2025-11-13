# Chapter 0: Prerequisites & Setup
## Getting Your Development Environment Ready

**What You'll Learn:**
- Install Java 25 JDK and verify installation
- Set up Gradle build system
- Install Vulkan SDK and validation layers
- Configure IDE for Java 25 preview features
- Create and configure initial project structure
- Verify LWJGL and Vulkan functionality

**Estimated Time:** 30-60 minutes

---

## Why This Chapter Matters

Before building a game engine, we need a solid development environment. This chapter ensures you have:

- The latest Java 25 LTS with modern features enabled
- Proper build configuration for native libraries (LWJGL, Vulkan)
- Working Vulkan drivers and SDK
- A "Hello LWJGL" program that confirms everything works

Skipping this setup leads to frustrating errors later. Take your time here!

---

## Prerequisites

### Required Knowledge
- Basic Java programming (classes, methods, variables)
- Command-line familiarity (running commands, navigating directories)
- Text editor or IDE usage

### System Requirements

**Hardware:**
- CPU: x64 architecture (Intel, AMD, or ARM64 with Rosetta on macOS)
- GPU: Any graphics card with Vulkan 1.0+ support (check [gpuinfo.org](https://vulkan.gpuinfo.org/))
- RAM: 8GB minimum, 16GB recommended
- Storage: 2GB free space for SDK and dependencies

**Operating System:**
- Windows 10/11 (64-bit)
- Linux (Ubuntu 20.04+, Fedora 35+, Arch, or equivalent)
- macOS 11+ (Vulkan via MoltenVK)

---

## Step 1: Install Java 25 JDK

### Why Java 25?

Java 25 is the LTS (Long-Term Support) release from September 2025, offering:

- **8 years of support** (through September 2033)
- **Virtual Threads** (stable) - perfect for parallel game systems
- **Pattern Matching** - cleaner ECS component handling
- **Compact Object Headers** - less memory for millions of entities
- **Scoped Values** - efficient frame-global data sharing

### Installation Methods

#### Option 1: Official Oracle JDK

**Download:**
1. Visit [jdk.java.net/25](https://jdk.java.net/25/)
2. Download for your platform:
   - Windows: `openjdk-25_windows-x64_bin.zip`
   - Linux: `openjdk-25_linux-x64_bin.tar.gz`
   - macOS: `openjdk-25_macos-x64_bin.tar.gz` (Intel) or `openjdk-25_macos-aarch64_bin.tar.gz` (Apple Silicon)

**Install:**

**Windows:**
```powershell
# Extract to C:\Program Files\Java\jdk-25
# Add to PATH
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# Make permanent (System Properties > Environment Variables)
```

**Linux:**
```bash
# Extract
sudo tar -xzf openjdk-25_linux-x64_bin.tar.gz -C /opt/
sudo ln -s /opt/jdk-25 /opt/jdk

# Add to ~/.bashrc or ~/.zshrc
export JAVA_HOME=/opt/jdk
export PATH=$JAVA_HOME/bin:$PATH
source ~/.bashrc
```

**macOS:**
```bash
# Extract
sudo tar -xzf openjdk-25_macos-*.tar.gz -C /Library/Java/JavaVirtualMachines/

# Add to ~/.zshrc
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH
source ~/.zshrc
```

#### Option 2: SDKMAN (Recommended for Linux/macOS)

```bash
# Install SDKMAN
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"

# Install Java 25
sdk install java 25-open

# Set as default
sdk default java 25-open
```

#### Option 3: Package Managers

**Windows (Chocolatey):**
```powershell
choco install openjdk25
```

**macOS (Homebrew):**
```bash
brew install openjdk@25
```

**Linux (apt on Ubuntu):**
```bash
sudo apt update
sudo apt install openjdk-25-jdk
```

### Verify Installation

```bash
java -version
```

**Expected Output:**
```
openjdk version "25" 2025-09-16
OpenJDK Runtime Environment (build 25+36)
OpenJDK 64-Bit Server VM (build 25+36, mixed mode, sharing)
```

**Check Preview Features:**
```bash
java --list-modules | grep jdk.preview
```

---

## Step 2: Install Gradle

### Why Gradle Over Maven?

- **Faster incremental builds** (important when iterating on game code)
- **Better native library support** (LWJGL platform-specific natives)
- **Flexible build scripts** (for asset pipelines, shader compilation)
- **Kotlin DSL option** (type-safe build configuration)

### Installation

#### Option 1: Official Gradle

**Download:**
1. Visit [gradle.org/releases](https://gradle.org/releases/)
2. Download Gradle 8.10+ (binary-only distribution)

**Install:**

**Windows:**
```powershell
# Extract to C:\Gradle
$env:GRADLE_HOME = "C:\Gradle\gradle-8.10.2"
$env:PATH = "$env:GRADLE_HOME\bin;$env:PATH"
```

**Linux/macOS:**
```bash
sudo unzip gradle-8.10.2-bin.zip -d /opt/gradle
export GRADLE_HOME=/opt/gradle/gradle-8.10.2
export PATH=$GRADLE_HOME/bin:$PATH
```

#### Option 2: SDKMAN (Recommended)

```bash
sdk install gradle 8.10.2
```

#### Option 3: Package Managers

**Windows:**
```powershell
choco install gradle
```

**macOS:**
```bash
brew install gradle
```

**Linux:**
```bash
sudo apt install gradle  # May not be latest version
```

### Verify Installation

```bash
gradle -version
```

**Expected Output:**
```
------------------------------------------------------------
Gradle 8.10.2
------------------------------------------------------------

Build time:   2024-09-05 12:00:00 UTC
Revision:     abc123def456

Kotlin:       1.9.24
Groovy:       3.0.21
Ant:          Apache Ant(TM) version 1.10.14
JVM:          25 (Oracle Corporation 25+36)
OS:           Windows 11 10.0 amd64
```

---

## Step 3: Install Vulkan SDK

### What's Included

The Vulkan SDK provides:

- **Vulkan headers** and libraries
- **Validation layers** (detect API misuse during development)
- **Shader compiler** (glslangValidator for GLSL → SPIR-V)
- **Debugging tools** (RenderDoc integration, VkTrace)
- **Documentation** and samples

### Installation

#### Windows

1. Visit [vulkan.lunarg.com](https://vulkan.lunarg.com/sdk/home)
2. Download latest SDK (VulkanSDK-X.X.X-Installer.exe)
3. Run installer
4. Select components:
   - ✅ Vulkan Runtime
   - ✅ Validation Layers
   - ✅ Shader Toolchain
   - ✅ Debugging Tools
5. SDK installs to `C:\VulkanSDK\X.X.X`

**Environment Variables** (auto-configured):
- `VULKAN_SDK=C:\VulkanSDK\X.X.X`
- `VK_LAYER_PATH=%VULKAN_SDK%\Bin`

#### Linux

**Ubuntu/Debian:**
```bash
wget -qO - https://packages.lunarg.com/lunarg-signing-key-pub.asc | sudo apt-key add -
sudo wget -qO /etc/apt/sources.list.d/lunarg-vulkan-jammy.list \
    https://packages.lunarg.com/vulkan/lunarg-vulkan-jammy.list
sudo apt update
sudo apt install vulkan-sdk
```

**Fedora:**
```bash
sudo dnf install vulkan-headers vulkan-loader vulkan-tools vulkan-validation-layers
```

**Arch:**
```bash
sudo pacman -S vulkan-devel
```

#### macOS

```bash
# Download SDK from vulkan.lunarg.com
# Or use Homebrew
brew install molten-vk vulkan-headers vulkan-loader
```

**Note:** macOS uses MoltenVK (Vulkan → Metal translation layer). Performance is good but not native.

### Verify Vulkan

```bash
# Check Vulkan loader
vulkaninfo --summary

# Or on Windows
vulkaninfo.exe --summary
```

**Expected Output:**
```
Vulkan Instance Version: 1.3.XXX

Instance Extensions:
    VK_KHR_surface
    VK_KHR_win32_surface (or xcb_surface, wayland_surface, metal_surface)
    ...

Physical Devices:
    GPU 0: NVIDIA GeForce RTX 4070
        apiVersion: 1.3.XXX
        driverVersion: XXX.XX
        ...
```

If this fails, update your graphics drivers:
- **NVIDIA:** [nvidia.com/drivers](https://www.nvidia.com/Download/index.aspx)
- **AMD:** [amd.com/support](https://www.amd.com/en/support)
- **Intel:** [intel.com/graphics](https://www.intel.com/content/www/us/en/download-center/home.html)

---

## Step 4: Configure IDE (Optional but Recommended)

### IntelliJ IDEA (Recommended)

**Why IntelliJ?**
- Excellent Java 25 support (preview features, pattern matching)
- Built-in Gradle integration
- Superior refactoring tools
- Native JFR profiler integration

**Setup:**
1. Download [IntelliJ IDEA](https://www.jetbrains.com/idea/) (Community Edition is free)
2. Install and launch
3. Configure Project SDK:
   - File → Project Structure → Project SDK → Add SDK → JDK
   - Navigate to Java 25 installation
4. Enable Preview Features:
   - File → Settings → Build → Compiler → Java Compiler
   - Additional command line parameters: `--enable-preview`

### Visual Studio Code

**Setup:**
1. Install [VS Code](https://code.visualstudio.com/)
2. Install extensions:
   - "Extension Pack for Java" by Microsoft
   - "Gradle for Java" by Microsoft
3. Configure settings.json:
```json
{
    "java.configuration.runtimes": [
        {
            "name": "JavaSE-25",
            "path": "/path/to/jdk-25",
            "default": true
        }
    ],
    "java.jdt.ls.java.home": "/path/to/jdk-25"
}
```

### Eclipse

**Setup:**
1. Download [Eclipse IDE for Java Developers](https://www.eclipse.org/downloads/)
2. Install and launch
3. Window → Preferences → Java → Installed JREs → Add → Standard VM
4. Point to Java 25 installation

---

## Step 5: Create Project Structure

### Initialize Gradle Project

```bash
# Create project directory
mkdir jecs-engine
cd jecs-engine

# Initialize Gradle project
gradle init

# Prompts:
# Type: application
# Language: Java
# Build script DSL: Groovy (or Kotlin if preferred)
# Test framework: JUnit Jupiter
# Project name: jecs-engine
# Source package: com.yourname.engine
```

This creates:
```
jecs-engine/
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── gradlew (Unix)
├── gradlew.bat (Windows)
├── build.gradle
├── settings.gradle
└── src/
    ├── main/
    │   ├── java/
    │   │   └── com/yourname/engine/
    │   │       └── Main.java
    │   └── resources/
    └── test/
        └── java/
            └── com/yourname/engine/
                └── MainTest.java
```

### Configure build.gradle

Replace `build.gradle` with:

```groovy
plugins {
    id 'java'
    id 'application'
}

group = 'com.yourname'
version = '0.1.0-SNAPSHOT'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

ext {
    lwjglVersion = '3.3.4'
    jomlVersion = '1.10.5'
    imguiVersion = '1.86.11'

    // Detect platform for natives
    lwjglNatives = System.getProperty('os.name').toLowerCase().with {
        if (it.contains('windows')) return 'natives-windows'
        if (it.contains('linux')) return 'natives-linux'
        if (it.contains('mac')) {
            return System.getProperty('os.arch').contains('aarch64')
                ? 'natives-macos-arm64'
                : 'natives-macos'
        }
        throw new IllegalStateException("Unsupported platform: ${it}")
    }
}

dependencies {
    // LWJGL BOM (Bill of Materials) - manages versions
    implementation platform("org.lwjgl:lwjgl-bom:$lwjglVersion")

    // LWJGL Modules
    implementation 'org.lwjgl:lwjgl'
    implementation 'org.lwjgl:lwjgl-assimp'      // Model loading
    implementation 'org.lwjgl:lwjgl-glfw'        // Window/input
    implementation 'org.lwjgl:lwjgl-openal'      // Audio
    implementation 'org.lwjgl:lwjgl-stb'         // Image loading
    implementation 'org.lwjgl:lwjgl-vulkan'      // Vulkan bindings

    // JOML - Java OpenGL Math Library
    implementation "org.joml:joml:$jomlVersion"

    // ImGui (deferred to Chapter 8, but listed here)
    // implementation "io.github.spair:imgui-java-binding:$imguiVersion"
    // implementation "io.github.spair:imgui-java-lwjgl3:$imguiVersion"

    // Gson for JSON serialization
    implementation 'com.google.code.gson:gson:2.10.1'

    // Logging
    implementation 'org.slf4j:slf4j-api:2.0.9'
    implementation 'org.slf4j:slf4j-simple:2.0.9'

    // Platform-specific natives
    runtimeOnly "org.lwjgl:lwjgl::$lwjglNatives"
    runtimeOnly "org.lwjgl:lwjgl-assimp::$lwjglNatives"
    runtimeOnly "org.lwjgl:lwjgl-glfw::$lwjglNatives"
    runtimeOnly "org.lwjgl:lwjgl-openal::$lwjglNatives"
    runtimeOnly "org.lwjgl:lwjgl-stb::$lwjglNatives"

    // Testing
    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.1'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

application {
    mainClass = 'com.yourname.engine.Main'
}

// Enable Java 25 preview features
tasks.withType(JavaCompile).configureEach {
    options.compilerArgs += ['--enable-preview']
    options.encoding = 'UTF-8'
}

tasks.withType(Test).configureEach {
    jvmArgs += ['--enable-preview']
    useJUnitPlatform()
}

tasks.withType(JavaExec).configureEach {
    jvmArgs += ['--enable-preview']

    // Enable assertions for development
    enableAssertions = true

    // JVM tuning for game engine
    jvmArgs += [
        '-XX:+UseZGC',                    // Low-latency GC
        '-Xms512m',                        // Initial heap
        '-Xmx2g',                          // Max heap
        '-XX:+UseStringDeduplication'      // Save memory on strings
    ]
}

// Convenience task for running with validation layers
tasks.register('runDebug', JavaExec) {
    group = 'application'
    description = 'Run with Vulkan validation layers'
    classpath = sourceSets.main.runtimeClasspath
    mainClass = application.mainClass

    // Enable Vulkan validation
    environment 'VK_INSTANCE_LAYERS', 'VK_LAYER_KHRONOS_validation'
    environment 'VK_LAYER_ENABLES', 'VK_VALIDATION_FEATURE_ENABLE_BEST_PRACTICES_EXT'
}
```

**Key Configuration Notes:**

1. **Platform Detection:** Automatically selects correct natives (Windows/Linux/macOS)
2. **Preview Features:** Enables Java 25 features via `--enable-preview`
3. **ZGC:** Low-latency garbage collector (pauses <10ms)
4. **Validation Layers:** `runDebug` task enables Vulkan debugging

### Update settings.gradle

```groovy
rootProject.name = 'jecs-engine'
```

---

## Step 6: Hello LWJGL Verification

Let's create a simple program to verify everything works.

### Create Main.java

Edit `src/main/java/com/yourname/engine/Main.java`:

```java
package com.yourname.engine;

import org.lwjgl.Version;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.vulkan.VK;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.system.MemoryStack;
import org.joml.Vector3f;

import java.nio.IntBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Main {
    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("JECS Engine - Environment Verification");
        System.out.println("=".repeat(60));

        // Check Java version
        String javaVersion = System.getProperty("java.version");
        System.out.println("✓ Java Version: " + javaVersion);

        if (!javaVersion.startsWith("25")) {
            System.err.println("⚠ Warning: Expected Java 25, found " + javaVersion);
        }

        // Check LWJGL
        System.out.println("✓ LWJGL Version: " + Version.getVersion());

        // Check JOML
        Vector3f testVec = new Vector3f(1, 2, 3);
        System.out.println("✓ JOML Working: " + testVec);

        // Initialize GLFW
        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit()) {
            throw new IllegalStateException("Failed to initialize GLFW");
        }
        System.out.println("✓ GLFW Initialized");

        // Check Vulkan support
        boolean vulkanSupported = glfwVulkanSupported();
        System.out.println("✓ Vulkan Supported: " + vulkanSupported);

        if (!vulkanSupported) {
            System.err.println("✗ Vulkan not supported! Check drivers and SDK.");
            glfwTerminate();
            System.exit(1);
        }

        // List Vulkan instance extensions
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer count = stack.ints(0);
            VK10.vkEnumerateInstanceExtensionProperties((String) null, count, null);
            System.out.println("✓ Vulkan Extensions Available: " + count.get(0));
        }

        // Create a test window (won't be shown)
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API); // Vulkan, not OpenGL
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);     // Hidden window

        long window = glfwCreateWindow(800, 600, "Test Window", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Failed to create GLFW window");
        }
        System.out.println("✓ GLFW Window Created (hidden)");

        // Cleanup
        glfwDestroyWindow(window);
        glfwTerminate();

        System.out.println("=".repeat(60));
        System.out.println("✓ All checks passed! Ready for Chapter 1.");
        System.out.println("=".repeat(60));
    }
}
```

### Run Verification

```bash
gradle run
```

**Expected Output:**
```
============================================================
JECS Engine - Environment Verification
============================================================
✓ Java Version: 25
✓ LWJGL Version: 3.3.4
✓ JOML Working: (1.0, 2.0, 3.0)
✓ GLFW Initialized
✓ Vulkan Supported: true
✓ Vulkan Extensions Available: 27
✓ GLFW Window Created (hidden)
============================================================
✓ All checks passed! Ready for Chapter 1.
============================================================
```

### Troubleshooting

**Problem: "Unsatisfied link error" or "Could not find native library"**

**Solution:** Gradle didn't download platform-specific natives.

```bash
# Force dependency resolution
gradle clean build --refresh-dependencies
```

Check `build.gradle` has correct `lwjglNatives` for your platform.

**Problem: "Vulkan not supported"**

**Solution:** Update graphics drivers:
- Download latest drivers from GPU manufacturer's website
- Reboot after installation
- Run `vulkaninfo` to verify

**Problem: "Java version mismatch"**

**Solution:** Verify correct Java version:

```bash
java -version          # Check runtime
javac -version         # Check compiler
gradle -version        # Check Gradle's JVM
```

Set `JAVA_HOME` explicitly:
```bash
export JAVA_HOME=/path/to/jdk-25
```

---

## Step 7: Project Organization

Create the following directory structure for upcoming chapters:

```bash
# Core engine packages
mkdir -p src/main/java/com/yourname/engine/core
mkdir -p src/main/java/com/yourname/engine/ecs
mkdir -p src/main/java/com/yourname/engine/renderer
mkdir -p src/main/java/com/yourname/engine/scene
mkdir -p src/main/java/com/yourname/engine/input
mkdir -p src/main/java/com/yourname/engine/audio
mkdir -p src/main/java/com/yourname/engine/editor

# Resources
mkdir -p src/main/resources/shaders
mkdir -p src/main/resources/textures
mkdir -p src/main/resources/models
mkdir -p src/main/resources/audio

# Tests
mkdir -p src/test/java/com/yourname/engine/ecs
mkdir -p src/test/java/com/yourname/engine/scene
```

Your structure should now look like:

```
jecs-engine/
├── build.gradle
├── settings.gradle
├── gradlew / gradlew.bat
└── src/
    ├── main/
    │   ├── java/com/yourname/engine/
    │   │   ├── Main.java
    │   │   ├── core/       (Chapter 1)
    │   │   ├── ecs/        (Chapter 2)
    │   │   ├── renderer/   (Chapter 3-5)
    │   │   ├── scene/      (Chapter 6)
    │   │   ├── input/      (Chapter 7)
    │   │   ├── audio/      (Chapter 7)
    │   │   └── editor/     (Chapter 8)
    │   └── resources/
    │       ├── shaders/
    │       ├── textures/
    │       ├── models/
    │       └── audio/
    └── test/java/com/yourname/engine/
        ├── ecs/
        └── scene/
```

---

## Performance: Java 25 JVM Tuning

### Recommended JVM Flags for Game Development

Add to your IDE run configuration or `build.gradle`:

```groovy
// In build.gradle, tasks.withType(JavaExec)
jvmArgs += [
    // Garbage Collection
    '-XX:+UseZGC',                         // Low-latency GC (pauses <1-10ms)
    '-XX:+ZGenerational',                  // Generational ZGC (Java 25)
    '-Xms512m',                            // Initial heap
    '-Xmx4g',                              // Max heap (adjust to your RAM)

    // Performance
    '-XX:+UseStringDeduplication',         // Save memory on duplicate strings
    '-XX:+AlwaysPreTouch',                 // Pre-touch memory pages (reduce hiccups)

    // Debugging (disable in production)
    '-XX:+UnlockDiagnosticVMOptions',
    '-XX:+DebugNonSafepoints',             // Better profiling data

    // Java 25 features
    '--enable-preview',                    // Enable preview features

    // Vulkan validation (development only)
    // '-Dorg.lwjgl.vulkan.libname=vulkan-1' // Force Vulkan loader
]
```

### Why ZGC?

Traditional GC (G1) can pause for 50-200ms, causing frame drops. ZGC:

- **Concurrent**: GC happens while game runs
- **Low latency**: Pauses typically <10ms (often <1ms)
- **Scalable**: Handles 4GB to 16TB heaps
- **Generational** (Java 25): Even better for short-lived objects (entities, components)

Alternative: **Shenandoah GC** (`-XX:+UseShenandoahGC`) for similar low-latency characteristics.

---

## What's Next?

You now have:

- ✅ Java 25 with preview features enabled
- ✅ Gradle configured with LWJGL, Vulkan, and JOML
- ✅ Vulkan SDK installed and verified
- ✅ Project structure ready for upcoming chapters
- ✅ "Hello LWJGL" verification passed

**In Chapter 1**, we'll create a real window, implement the game loop, and render our first Vulkan clear screen!

---

## Exercises

Before moving on, try these to deepen your understanding:

1. **Modify Main.java**: Print all available Vulkan extensions (names, not just count)
   - Hint: Call `vkEnumerateInstanceExtensionProperties` twice (once for count, once for data)

2. **Create a visible window**: Change `GLFW_VISIBLE` to `GLFW_TRUE` and keep window open for 3 seconds
   - Hint: Add `glfwPollEvents()` loop with `Thread.sleep()`

3. **Experiment with JOML**: Create a `Matrix4f`, apply translation/rotation, print result
   - Hint: `new Matrix4f().translate(1, 2, 3).rotateX((float)Math.toRadians(45))`

4. **Profile the JVM**: Run with `-Xlog:gc` to see GC activity
   - Notice: With ZGC, you'll see very short pauses

---

## Further Reading

- **LWJGL Tutorial**: [lwjgl.org/guide](https://www.lwjgl.org/guide)
- **Vulkan Tutorial**: [vulkan-tutorial.com](https://vulkan-tutorial.com/)
- **JOML Documentation**: [joml-ci.github.io/JOML](https://joml-ci.github.io/JOML/)
- **Java 25 Features**: [openjdk.org/projects/jdk/25](https://openjdk.org/projects/jdk/25/)
- **ZGC Deep Dive**: [malloc.se/blog/zgc](https://malloc.se/blog/zgc-jdk16)

---

**Next:** [Chapter 1 - Window + Engine Loop + Vulkan Clear Screen →](chapter-01-window-and-loop.md)
