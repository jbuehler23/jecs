# Chapter 0: Prerequisites & Setup
## Getting Your Development Environment Ready

**What You'll Learn:**
- Why we chose Java 25, Gradle, and Vulkan (over alternatives)
- How native library loading works (LWJGL platform detection)
- Setting up a production-ready build configuration
- Verifying Vulkan drivers and validation layers
- Understanding JVM garbage collection for games
- Creating a robust project structure

**Estimated Time:** 30-60 minutes

---

## Introduction: Why This Chapter Matters

**The Foundation Principle:**

Every game engine starts with a solid development environment. This chapter isn't about "installing software" - it's about understanding **why** we make specific technical choices and **how** modern Java game development works.

**What Makes This Different:**

Unlike tutorials that say "just install this," we'll explain:
- Why Java 25 over Java 17 or 21 (specific features we'll use)
- Why Gradle over Maven (build performance, native libraries)
- Why Vulkan over OpenGL (explicit state, performance)
- How native library loading works (platform detection, JNI)
- How to tune the JVM for real-time game performance

**Time Investment:**

Spending 60 minutes here saves **hours of debugging later**. Common setup mistakes:
- Wrong Java version → "Pattern matching doesn't work"
- Missing Vulkan SDK → "VK_ERROR_INCOMPATIBLE_DRIVER"
- Incorrect natives → "UnsatisfiedLinkError"
- Poor JVM tuning → 100ms GC pauses during gameplay

Let's avoid these!

---

## Prerequisites

### Required Knowledge

**Minimum Java Experience:**
- Classes and objects (inheritance, interfaces)
- Methods and variables (static vs instance)
- Basic generics (`List<String>`, `Map<K,V>`)
- Exception handling (try-catch)

**Don't Need to Know:**
- Advanced Java features (we'll learn as we go)
- Graphics programming (starts in Chapter 1)
- Game development (that's what we're learning!)

**Command-Line Basics:**
```bash
# You should be comfortable with:
cd directory/        # Navigate
ls / dir             # List files
mkdir folder         # Create directory
./gradlew build      # Run commands
```

### System Requirements

**Hardware Minimum:**

| Component | Minimum | Recommended | Why? |
|-----------|---------|-------------|------|
| **CPU** | x64 dual-core | x64 quad-core+ | Parallel compilation, ECS systems |
| **GPU** | Vulkan 1.0 support | Vulkan 1.2+ | Must support compute shaders |
| **RAM** | 8GB | 16GB+ | JVM heap, native memory, IDE |
| **Storage** | 2GB free | SSD with 10GB | SDK, dependencies, build cache |

**Check GPU Support:**
1. Visit [vulkan.gpuinfo.org](https://vulkan.gpuinfo.org/)
2. Search for your GPU model
3. Verify "Vulkan Support: Yes"

**Common GPUs:**
- ✅ NVIDIA: GeForce GTX 600+ (Kepler), RTX series
- ✅ AMD: Radeon HD 7000+ (GCN), RX series
- ✅ Intel: HD Graphics 4000+ (Ivy Bridge), Iris, Arc
- ⚠️ Apple: Needs MoltenVK (Vulkan→Metal translation)

**Operating Systems:**

| OS | Version | Notes |
|----|---------|-------|
| **Windows** | 10/11 (64-bit) | Best Vulkan support, easiest setup |
| **Linux** | Ubuntu 20.04+, Fedora 35+, Arch | Good Vulkan support, lightweight |
| **macOS** | 11+ (Big Sur) | Requires MoltenVK, performance penalty |

**macOS Limitations:**
- No native Vulkan (uses Metal underneath)
- ~10-20% performance loss from translation
- Some Vulkan features unavailable
- Alternative: Use Linux VM or dual-boot for development

---

## Understanding Our Tech Stack

Before installing, let's understand **why** we chose each tool.

### Java 25 vs Java 17/21

**Why Not Java 17 (LTS)?**

Java 17 is stable but lacks modern features we need:

| Feature | Java 17 | Java 25 | Why We Need It |
|---------|---------|---------|----------------|
| **Virtual Threads** | Preview | Stable | Parallel ECS systems without thread pools |
| **Pattern Matching** | Limited | Full | Clean component type checking |
| **Compact Object Headers** | No | Yes | Save 8 bytes per entity (8M entities = 64MB saved!) |
| **Scoped Values** | No | Yes | Frame-global data without ThreadLocal overhead |
| **Generational ZGC** | No | Yes | <1ms GC pauses for game engines |

**Real Example - Virtual Threads:**

```java
// Java 17 - Complex thread pool management
ExecutorService pool = Executors.newFixedThreadPool(8);
Future<?>[] futures = new Future[entities.length];
for (int i = 0; i < entities.length; i++) {
    final int index = i;
    futures[i] = pool.submit(() -> processEntity(entities[index]));
}
for (Future<?> f : futures) f.get(); // Wait for all

// Java 25 - Virtual threads (lightweight, millions possible)
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    for (Entity entity : entities) {
        executor.submit(() -> processEntity(entity));
    }
} // Auto-waits for all threads
```

**Performance Impact:**
- Thread pool: 8 threads max, context switch overhead
- Virtual threads: 100,000+ threads, minimal overhead

**Why Not Java 21 (LTS)?**

Java 21 is excellent, but Java 25:
- Generational ZGC (50% less GC time)
- Improved virtual thread performance
- Final pattern matching syntax
- Better compact headers

**Upgrade Path:**
Start with Java 25. When LTS support matters (production), migrate to Java 27 (next LTS, September 2027).

---

### Gradle vs Maven

**Why Gradle Over Maven?**

| Feature | Maven | Gradle | Why It Matters |
|---------|-------|--------|----------------|
| **Build Speed** | 45s | 5s | Incremental compilation, build cache |
| **Native Libraries** | Manual | Automatic | LWJGL platform detection |
| **Flexibility** | XML config | Groovy/Kotlin DSL | Custom tasks for shaders, assets |
| **Dependency Management** | Good | Excellent | Version resolution, platform variants |

**Real Example - Native Library Loading:**

```groovy
// Gradle automatically detects platform:
ext {
    lwjglNatives = System.getProperty('os.name').toLowerCase().with {
        if (it.contains('windows')) return 'natives-windows'
        if (it.contains('linux')) return 'natives-linux'
        if (it.contains('mac')) {
            return System.getProperty('os.arch').contains('aarch64')
                ? 'natives-macos-arm64'
                : 'natives-macos'
        }
    }
}

dependencies {
    runtimeOnly "org.lwjgl:lwjgl::$lwjglNatives"
    runtimeOnly "org.lwjgl:lwjgl-glfw::$lwjglNatives"
    // ... automatically downloads correct .dll/.so/.dylib
}
```

**Maven Equivalent:** ~50 lines of XML with manual platform profiles.

**Build Cache Example:**

```bash
# First build (cold cache):
gradle build  # 45 seconds

# Change one file:
gradle build  # 2 seconds (incremental)

# No changes:
gradle build  # 0.5 seconds (up-to-date check)
```

**Maven:** Rebuilds more aggressively, ~10-15 seconds even with no changes.

---

### Vulkan vs OpenGL/DirectX

**Why Vulkan?**

**The Fundamental Difference:**

```
OpenGL (Implicit State Machine):
─────────────────────────────────
glBindTexture(texture);        ← Hidden state change
glDrawArrays(...);             ← Hidden validation
// Driver does tons of work you don't see

Vulkan (Explicit Everything):
─────────────────────────────────
vkCmdBindPipeline(...);        ← You manage state
vkCmdBindDescriptorSets(...);  ← You specify resources
vkCmdDraw(...);                ← You synchronize
// You control EVERYTHING
```

**Performance Comparison:**

| Metric | OpenGL | Vulkan | Reason |
|--------|--------|--------|--------|
| **CPU Overhead** | 15-20% | 3-5% | No driver translation layer |
| **Draw Calls** | ~10K/frame | ~100K/frame | Command buffers, batching |
| **Multi-Threading** | Limited | Full | Explicit command buffer recording |
| **Memory Control** | Hidden | Explicit | Manage device/host memory |

**Real-World Example (Doom 2016):**

- OpenGL: 100K objects → 20 FPS (CPU bottleneck)
- Vulkan: 100K objects → 60 FPS (GPU bottleneck, as it should be)

**Why Not DirectX 12?**

DirectX 12 is similar to Vulkan but:
- ✗ Windows-only (Vulkan: Windows/Linux/macOS/Android)
- ✗ Closed ecosystem (Vulkan: Open standard)
- ✓ Slightly better Windows performance (5-10%)

For cross-platform engines, Vulkan wins.

**The Trade-Off:**

```
OpenGL:
✓ Easy to learn (driver handles complexity)
✓ Quick prototyping
✗ Performance ceiling
✗ Hidden bottlenecks

Vulkan:
✓ Maximum performance
✓ Predictable behavior
✓ Full control
✗ Verbose (5-10x more code)
✗ Steeper learning curve
```

**Our Approach:** Abstract Vulkan complexity into engine systems (Chapters 3-5), then enjoy the performance benefits!

---

## Step 1: Install Java 25 JDK

### Why JDK (Not JRE)?

**JDK** = Java Development Kit (compiler + runtime)
**JRE** = Java Runtime Environment (runtime only)

**You need JDK because:**
- `javac` compiler (build Java source)
- `jar` tool (package applications)
- `jdeps` (dependency analysis)
- Development libraries

### Installation Methods

#### Option 1: Official Oracle JDK (Recommended)

**Download:**
1. Visit [jdk.java.net/25](https://jdk.java.net/25/)
2. Select your platform:
   - **Windows**: `openjdk-25_windows-x64_bin.zip`
   - **Linux**: `openjdk-25_linux-x64_bin.tar.gz`
   - **macOS (Intel)**: `openjdk-25_macos-x64_bin.tar.gz`
   - **macOS (Apple Silicon)**: `openjdk-25_macos-aarch64_bin.tar.gz`

**Install:**

**Windows:**
```powershell
# Extract ZIP to C:\Program Files\Java\jdk-25
# (Right-click → Extract All)

# Set environment variables (PowerShell as Admin):
[Environment]::SetEnvironmentVariable(
    "JAVA_HOME",
    "C:\Program Files\Java\jdk-25",
    "Machine"
)

# Add to PATH:
$path = [Environment]::GetEnvironmentVariable("PATH", "Machine")
$newPath = "C:\Program Files\Java\jdk-25\bin;$path"
[Environment]::SetEnvironmentVariable("PATH", $newPath, "Machine")

# Restart PowerShell to apply changes
```

**Alternative (GUI):**
1. Right-click "This PC" → Properties
2. Advanced system settings → Environment Variables
3. System variables → New:
   - Name: `JAVA_HOME`
   - Value: `C:\Program Files\Java\jdk-25`
4. Edit `Path` → Add: `%JAVA_HOME%\bin`

**Linux:**
```bash
# Extract
sudo tar -xzf openjdk-25_linux-x64_bin.tar.gz -C /opt/
sudo mv /opt/jdk-25* /opt/jdk-25

# Create symlink for easy updates
sudo ln -s /opt/jdk-25 /opt/jdk

# Add to ~/.bashrc (or ~/.zshrc for Zsh)
echo 'export JAVA_HOME=/opt/jdk' >> ~/.bashrc
echo 'export PATH=$JAVA_HOME/bin:$PATH' >> ~/.bashrc
source ~/.bashrc
```

**macOS:**
```bash
# Extract
sudo tar -xzf openjdk-25_macos-*.tar.gz -C /Library/Java/JavaVirtualMachines/

# Rename for clarity
cd /Library/Java/JavaVirtualMachines/
sudo mv jdk-25*.jdk jdk-25.jdk

# Add to ~/.zshrc (macOS uses Zsh by default)
echo 'export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home' >> ~/.zshrc
echo 'export PATH=$JAVA_HOME/bin:$PATH' >> ~/.zshrc
source ~/.zshrc
```

**Why /Library/Java on macOS?**
- Standard location for Java installations
- macOS automatically scans this directory
- Compatible with `/usr/libexec/java_home` tool

#### Option 2: SDKMAN (Recommended for Linux/macOS)

**SDKMAN** = Software Development Kit Manager (like `apt` for Java tools)

**Install SDKMAN:**
```bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
```

**Install Java 25:**
```bash
# List available Java versions
sdk list java

# Install Java 25 (OpenJDK)
sdk install java 25-open

# Set as default
sdk default java 25-open

# Verify
java -version
```

**Why SDKMAN?**
- Manage multiple Java versions (`sdk use java 17-tem`)
- Install Gradle, Maven, Kotlin with one command
- Automatic PATH management
- Easy version switching

#### Option 3: Package Managers

**Windows (Chocolatey):**
```powershell
# Install Chocolatey first (see chocolatey.org)
choco install openjdk25
```

**macOS (Homebrew):**
```bash
brew install openjdk@25

# Symlink for system integration
sudo ln -sfn $(brew --prefix)/opt/openjdk@25/libexec/openjdk.jdk \
    /Library/Java/JavaVirtualMachines/openjdk-25.jdk
```

**Linux (apt on Ubuntu 24.04+):**
```bash
sudo apt update
sudo apt install openjdk-25-jdk
```

**Note:** Package manager versions may lag behind official releases.

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

**Check Compiler:**
```bash
javac -version
```

**Expected:** `javac 25`

**Check JAVA_HOME:**
```bash
# Linux/macOS:
echo $JAVA_HOME

# Windows (PowerShell):
echo $env:JAVA_HOME
```

**Expected:** Path to your JDK installation.

**Verify Preview Features:**
```bash
java --list-modules | grep jdk.preview
```

**Expected:** No output (preview features are built-in, not modules).

**Test Preview Features:**
```bash
# Create test file
echo 'public class Test { public static void main(String[] args) { System.out.println("Java 25 works!"); } }' > Test.java

# Compile with preview
javac --enable-preview --release 25 Test.java

# Run
java --enable-preview Test

# Expected: "Java 25 works!"
```

---

## Step 2: Install Gradle

### Understanding Gradle

**What Gradle Does:**

```
Your Java Files          Gradle Build Process         Runnable Application
──────────────          ────────────────────         ────────────────────
Main.java               1. Resolve dependencies      jecs-engine.jar
Window.java          →  2. Compile Java source    →  + LWJGL natives
ECS.java                3. Run tests                 + Resources
...                     4. Package JAR               + Run scripts
```

**Why Build Tools Matter:**

Without Gradle:
```bash
# Manual compilation (nightmare!)
javac -cp "lwjgl.jar;lwjgl-glfw.jar;joml.jar" src/Main.java
javac -cp "lwjgl.jar;lwjgl-glfw.jar;joml.jar" src/Window.java
# ... repeat for 100+ files
java -cp "bin;lwjgl.jar;lwjgl-glfw.jar;joml.jar;natives-windows/*" Main
```

With Gradle:
```bash
gradle run  # That's it!
```

### Installation Methods

#### Option 1: Official Gradle

**Download:**
1. Visit [gradle.org/releases](https://gradle.org/releases/)
2. Download **Binary-only** distribution (Gradle 8.10+)

**Install:**

**Windows:**
```powershell
# Extract to C:\Gradle
# Set environment variable:
[Environment]::SetEnvironmentVariable(
    "GRADLE_HOME",
    "C:\Gradle\gradle-8.10.2",
    "Machine"
)

# Add to PATH:
$path = [Environment]::GetEnvironmentVariable("PATH", "Machine")
$newPath = "C:\Gradle\gradle-8.10.2\bin;$path"
[Environment]::SetEnvironmentVariable("PATH", $newPath, "Machine")
```

**Linux/macOS:**
```bash
# Extract
sudo mkdir /opt/gradle
sudo unzip -d /opt/gradle gradle-8.10.2-bin.zip

# Add to ~/.bashrc or ~/.zshrc
export GRADLE_HOME=/opt/gradle/gradle-8.10.2
export PATH=$GRADLE_HOME/bin:$PATH
```

#### Option 2: SDKMAN (Best for Linux/macOS)

```bash
sdk install gradle 8.10.2
sdk default gradle 8.10.2
```

#### Option 3: Package Managers

**Windows (Chocolatey):**
```powershell
choco install gradle
```

**macOS (Homebrew):**
```bash
brew install gradle
```

**Linux (apt):**
```bash
sudo apt install gradle
# Note: May install older version (7.x)
```

#### Option 4: Gradle Wrapper (Best for Projects)

**Don't install Gradle at all!** Use the Gradle Wrapper (`gradlew`):

```bash
# Initialize project with wrapper
gradle init

# Now use wrapper (downloads Gradle automatically)
./gradlew build    # Linux/macOS
gradlew.bat build  # Windows
```

**Why Wrapper?**
- Ensures everyone uses same Gradle version
- No manual installation needed
- Project-specific Gradle version
- Committed to Git (reproducible builds)

**How It Works:**

```
gradle/wrapper/
├── gradle-wrapper.jar       ← Downloads Gradle on first run
└── gradle-wrapper.properties ← Specifies Gradle version

distributionUrl=https\://services.gradle.org/distributions/gradle-8.10.2-bin.zip
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

**Key Points:**
- ✓ JVM: 25 (using correct Java version)
- ✓ OS detected correctly

**Test Build:**
```bash
# Create test project
mkdir test-gradle && cd test-gradle
gradle init --type java-application

# Build
gradle build

# Run
gradle run
```

**Expected:** "Hello World!" output.

---

## Step 3: Install Vulkan SDK

### Understanding Vulkan Components

**What the SDK Provides:**

```
Vulkan SDK
├── Vulkan Loader (vulkan-1.dll / libvulkan.so)
│   ↓ Dispatches calls to driver
├── Validation Layers (VK_LAYER_KHRONOS_validation)
│   ↓ Development-time error checking
├── Shader Compiler (glslangValidator)
│   ↓ GLSL → SPIR-V bytecode
├── Debugging Tools (RenderDoc, Vulkan Configurator)
│   ↓ Frame capture, performance analysis
└── Headers + Libraries
    ↓ C/C++ includes (LWJGL generates Java bindings)
```

**Why Each Component Matters:**

| Component | Purpose | When Used |
|-----------|---------|-----------|
| **Vulkan Loader** | Routes API calls to GPU driver | Runtime (always) |
| **Validation Layers** | Catches API misuse, memory leaks | Development only |
| **glslangValidator** | Compiles shaders offline | Build time |
| **RenderDoc** | Captures frames for debugging | Debugging |

**Validation Layers Example:**

```java
// Your code:
vkCreateBuffer(device, null, null, pBuffer);  // BUG: null createInfo!

// Without validation: Silent crash or corruption
// With validation:
// ✗ ERROR: vkCreateBuffer: pCreateInfo must not be NULL
// ✗ VUID-vkCreateBuffer-pCreateInfo-parameter
```

### Installation

#### Windows

1. Visit [vulkan.lunarg.com/sdk/home](https://vulkan.lunarg.com/sdk/home)
2. Download latest SDK installer (e.g., `VulkanSDK-1.3.280.0-Installer.exe`)
3. Run installer
4. Select components:
   - ✅ **Vulkan SDK Core** (required)
   - ✅ **Volk Meta Loader** (optional, not needed for Java)
   - ✅ **Validation Layers** (required for development)
   - ✅ **Shader Toolchain** (glslangValidator, spirv-tools)
   - ✅ **Debugging Tools** (RenderDoc, optional but useful)
   - ⬜ **Vulkan Memory Allocator** (C++ only)
   - ⬜ **Samples** (optional, C++ code)

5. Installation path: `C:\VulkanSDK\1.3.280.0`

**Environment Variables (auto-configured):**
```powershell
# Verify:
echo $env:VULKAN_SDK           # C:\VulkanSDK\1.3.280.0
echo $env:VK_LAYER_PATH        # C:\VulkanSDK\1.3.280.0\Bin
```

**Manual Configuration (if needed):**
```powershell
[Environment]::SetEnvironmentVariable(
    "VULKAN_SDK",
    "C:\VulkanSDK\1.3.280.0",
    "Machine"
)
[Environment]::SetEnvironmentVariable(
    "VK_LAYER_PATH",
    "C:\VulkanSDK\1.3.280.0\Bin",
    "Machine"
)
```

#### Linux

**Ubuntu/Debian:**
```bash
# Add LunarG repository
wget -qO - https://packages.lunarg.com/lunarg-signing-key-pub.asc | sudo apt-key add -
sudo wget -qO /etc/apt/sources.list.d/lunarg-vulkan-jammy.list \
    https://packages.lunarg.com/vulkan/lunarg-vulkan-jammy.list

# Install SDK
sudo apt update
sudo apt install vulkan-sdk
```

**Fedora:**
```bash
sudo dnf install vulkan-headers vulkan-loader vulkan-tools vulkan-validation-layers mesa-vulkan-drivers
```

**Arch Linux:**
```bash
sudo pacman -S vulkan-devel vulkan-tools vulkan-validation-layers
```

**Environment Variables:**
```bash
# Add to ~/.bashrc
export VULKAN_SDK=/usr
export VK_LAYER_PATH=/usr/share/vulkan/explicit_layer.d
```

#### macOS

**Download SDK:**
1. Visit [vulkan.lunarg.com](https://vulkan.lunarg.com/sdk/home)
2. Download macOS SDK (.dmg file)
3. Open .dmg and run installer

**Or Homebrew:**
```bash
brew install molten-vk vulkan-headers vulkan-loader vulkan-tools
```

**Environment Variables:**
```bash
# Add to ~/.zshrc
export VULKAN_SDK=/usr/local
export VK_ICD_FILENAMES=/usr/local/share/vulkan/icd.d/MoltenVK_icd.json
export VK_LAYER_PATH=/usr/local/share/vulkan/explicit_layer.d
```

**MoltenVK Note:**

macOS doesn't have native Vulkan. MoltenVK translates Vulkan → Metal:

```
Your Vulkan Code
    ↓
LWJGL Bindings
    ↓
MoltenVK (translation layer)
    ↓
Apple Metal API
    ↓
GPU
```

**Performance Impact:**
- Translation overhead: ~10-20% slower than native Metal
- Some Vulkan features unsupported (geometry shaders, tessellation)
- Still faster than OpenGL on macOS!

### Verify Vulkan

**Check Vulkan Loader:**
```bash
vulkaninfo --summary
```

**Expected Output:**
```
Vulkan Instance Version: 1.3.280

Instance Extensions:
    VK_KHR_surface
    VK_KHR_win32_surface        (Windows)
    VK_KHR_xcb_surface          (Linux X11)
    VK_KHR_wayland_surface      (Linux Wayland)
    VK_MVK_macos_surface        (macOS)
    VK_EXT_debug_utils
    VK_EXT_debug_report
    ...

Physical Devices:
    GPU 0: NVIDIA GeForce RTX 4070
        apiVersion: 1.3.280
        driverVersion: 546.12.0.0
        vendorID: 0x10de (NVIDIA)
        deviceID: 0x2786
        deviceType: DISCRETE_GPU
```

**Troubleshooting:**

**Problem: "vulkaninfo: command not found"**
```bash
# Windows: Add to PATH
$env:PATH += ";C:\VulkanSDK\1.3.280.0\Bin"

# Linux: Reinstall or check PATH
which vulkaninfo  # Should show /usr/bin/vulkaninfo
```

**Problem: "No Vulkan devices found"**
1. Update graphics drivers:
   - **NVIDIA**: [nvidia.com/drivers](https://www.nvidia.com/Download/index.aspx)
   - **AMD**: [amd.com/support](https://www.amd.com/en/support)
   - **Intel**: [intel.com/graphics-drivers](https://www.intel.com/content/www/us/en/download/785597/intel-arc-iris-xe-graphics-windows.html)
2. Reboot after driver update
3. Run `vulkaninfo` again

**Problem: "Validation layers not found"**
```bash
# Set environment variable
export VK_LAYER_PATH=/path/to/vulkan/sdk/etc/vulkan/explicit_layer.d

# Verify
vkconfig  # Opens Vulkan Configurator (GUI)
```

---

## Step 4: Configure IDE (Optional but Recommended)

### Why Use an IDE?

**IDE vs Text Editor:**

| Feature | Text Editor | IDE |
|---------|-------------|-----|
| **Autocomplete** | Basic | Intelligent (knows ECS types) |
| **Refactoring** | Manual | Automated (rename, extract method) |
| **Debugging** | GDB/JDB | Integrated (breakpoints, watches) |
| **Build Integration** | Terminal | One-click |
| **Profiling** | External tools | Built-in JFR profiler |

**Real Example - Refactoring:**

Text Editor:
```
1. Find all occurrences of "Transform2D"
2. Replace with "Transform3D"
3. Fix broken imports manually
4. Fix broken method calls manually
5. Hope you didn't miss any
```

IDE (IntelliJ):
```
1. Right-click class → Refactor → Rename
2. Type "Transform3D"
3. Done (all references updated)
```

### IntelliJ IDEA (Recommended)

**Why IntelliJ?**
- Best Java 25 support (preview features work out-of-box)
- Superior refactoring (extract component, inline method)
- Built-in profiler (Java Flight Recorder integration)
- Excellent Gradle integration
- Free Community Edition

**Setup:**

1. Download [IntelliJ IDEA Community](https://www.jetbrains.com/idea/download/)
2. Install and launch
3. Open project:
   - File → Open → Select `jecs-engine` directory
   - IntelliJ auto-detects Gradle project
4. Configure Java 25:
   - File → Project Structure (Ctrl+Alt+Shift+S)
   - Project → SDK → Add SDK → Download JDK
   - Select: Version 25, Vendor: Oracle OpenJDK
   - Apply
5. Enable Preview Features:
   - File → Settings → Build, Execution, Deployment → Compiler → Java Compiler
   - Additional command line parameters: `--enable-preview`
   - Per-module bytecode version: 25
6. Configure Run Configuration:
   - Run → Edit Configurations
   - Application → Add New (+)
   - Main class: `com.yourname.engine.Main`
   - VM options: `--enable-preview`
   - Environment variables:
     - `VK_INSTANCE_LAYERS=VK_LAYER_KHRONOS_validation` (for debugging)

**Useful Plugins:**
- **Rainbow Brackets**: Colorize nested brackets (helpful for ECS queries)
- **Key Promoter X**: Learn keyboard shortcuts
- **GitToolBox**: Enhanced Git integration

### Visual Studio Code

**Setup:**

1. Install [VS Code](https://code.visualstudio.com/)
2. Install extensions:
   - **Extension Pack for Java** (Microsoft) - Includes:
     - Language Support for Java (RedHat)
     - Debugger for Java
     - Test Runner for Java
     - Maven/Gradle support
   - **Gradle for Java** (Microsoft)
3. Configure Java:
   - Open Command Palette (Ctrl+Shift+P)
   - "Java: Configure Java Runtime"
   - Select Java 25
4. Configure settings.json:
   ```json
   {
       "java.configuration.runtimes": [
           {
               "name": "JavaSE-25",
               "path": "/path/to/jdk-25",
               "default": true
           }
       ],
       "java.jdt.ls.java.home": "/path/to/jdk-25",
       "java.compile.nullAnalysis.mode": "automatic",
       "java.project.sourcePaths": ["src/main/java"],
       "java.project.outputPath": "build/classes",
       "files.exclude": {
           "**/.gradle": true,
           "**/build": true
       }
   }
   ```

**Launch Configuration (.vscode/launch.json):**
```json
{
    "version": "0.2.0",
    "configurations": [
        {
            "type": "java",
            "name": "Launch JECS Engine",
            "request": "launch",
            "mainClass": "com.yourname.engine.Main",
            "projectName": "jecs-engine",
            "vmArgs": "--enable-preview",
            "env": {
                "VK_INSTANCE_LAYERS": "VK_LAYER_KHRONOS_validation"
            }
        }
    ]
}
```

### Eclipse (Alternative)

**Setup:**

1. Download [Eclipse IDE for Java Developers](https://www.eclipse.org/downloads/)
2. Install and launch
3. Import Gradle project:
   - File → Import → Gradle → Existing Gradle Project
   - Select `jecs-engine` directory
4. Configure JDK:
   - Window → Preferences → Java → Installed JREs
   - Add → Standard VM → Browse to Java 25
   - Set as default

**Preview Features:**
- Project → Properties → Java Compiler
- Enable preview features: Check "Enable preview features for Java 25"

---

## Step 5: Create Project Structure

### Initialize Gradle Project

**Create Directory:**
```bash
mkdir jecs-engine
cd jecs-engine
```

**Initialize with Gradle:**
```bash
gradle init

# Interactive prompts:
# 1. Select type of build: 2 (application)
# 2. Select implementation language: 3 (Java)
# 3. Select build script DSL: 1 (Groovy)
# 4. Select test framework: 4 (JUnit Jupiter)
# 5. Project name: jecs-engine
# 6. Source package: com.yourname.engine
# 7. Target Java version: 25
# 8. Generate build using new APIs: yes
```

**Generated Structure:**
```
jecs-engine/
├── gradle/                      ← Gradle wrapper files
│   └── wrapper/
│       ├── gradle-wrapper.jar   ← Wrapper executable
│       └── gradle-wrapper.properties ← Gradle version config
├── gradlew                      ← Wrapper script (Unix)
├── gradlew.bat                  ← Wrapper script (Windows)
├── build.gradle                 ← Build configuration
├── settings.gradle              ← Project settings
└── src/
    ├── main/
    │   ├── java/
    │   │   └── com/yourname/engine/
    │   │       └── Main.java
    │   └── resources/           ← Assets (shaders, textures, etc.)
    └── test/
        └── java/
            └── com/yourname/engine/
                └── MainTest.java
```

### Configure build.gradle

**Replace generated `build.gradle` with:**

```groovy
plugins {
    id 'java'
    id 'application'
}

group = 'com.yourname'
version = '0.1.0-SNAPSHOT'

// Java 25 configuration
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

// Dependency versions
ext {
    lwjglVersion = '3.3.4'         // LWJGL (native bindings)
    jomlVersion = '1.10.5'         // Math library
    imguiVersion = '1.86.11'       // ImGui (Chapter 8)
    gsonVersion = '2.10.1'         // JSON serialization

    /**
     * Platform Detection for Native Libraries
     *
     * LWJGL provides platform-specific natives (.dll, .so, .dylib).
     * Gradle must download the correct variant for your OS.
     *
     * Detection logic:
     * - Windows → natives-windows
     * - Linux → natives-linux
     * - macOS (Intel) → natives-macos
     * - macOS (ARM) → natives-macos-arm64
     */
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
    /**
     * LWJGL BOM (Bill of Materials)
     *
     * WHY BOM?
     * LWJGL has 20+ modules that must use the same version.
     * BOM ensures version consistency automatically.
     *
     * Without BOM:
     *   implementation 'org.lwjgl:lwjgl:3.3.4'
     *   implementation 'org.lwjgl:lwjgl-glfw:3.3.4'  ← Must match!
     *   implementation 'org.lwjgl:lwjgl-vulkan:3.3.4' ← Must match!
     *
     * With BOM:
     *   implementation platform('org.lwjgl:lwjgl-bom:3.3.4')
     *   implementation 'org.lwjgl:lwjgl'              ← Version auto-set
     *   implementation 'org.lwjgl:lwjgl-glfw'         ← Version auto-set
     */
    implementation platform("org.lwjgl:lwjgl-bom:$lwjglVersion")

    /**
     * LWJGL Core Modules
     *
     * Each module provides bindings to native libraries:
     * - lwjgl:          Core (memory management, callbacks)
     * - lwjgl-assimp:   Model loading (OBJ, FBX, GLTF)
     * - lwjgl-glfw:     Window/input (cross-platform)
     * - lwjgl-openal:   3D audio
     * - lwjgl-stb:      Image loading (PNG, JPEG, TGA)
     * - lwjgl-vulkan:   Vulkan graphics API
     */
    implementation 'org.lwjgl:lwjgl'
    implementation 'org.lwjgl:lwjgl-assimp'
    implementation 'org.lwjgl:lwjgl-glfw'
    implementation 'org.lwjgl:lwjgl-openal'
    implementation 'org.lwjgl:lwjgl-stb'
    implementation 'org.lwjgl:lwjgl-vulkan'

    /**
     * JOML - Java OpenGL Math Library
     *
     * Provides:
     * - Vector2f, Vector3f, Vector4f (positions, directions)
     * - Matrix4f (transformations: translate, rotate, scale)
     * - Quaternionf (rotations without gimbal lock)
     *
     * Alternative: Apache Commons Math (too heavy, 2MB)
     * JOML: Lightweight (200KB), game-optimized
     */
    implementation "org.joml:joml:$jomlVersion"

    /**
     * ImGui - Immediate Mode GUI
     *
     * Used in Chapter 8 for editor UI (hierarchy, inspector, etc.)
     * Deferred until needed to keep initial dependencies minimal.
     */
    // implementation "io.github.spair:imgui-java-binding:$imguiVersion"
    // implementation "io.github.spair:imgui-java-lwjgl3:$imguiVersion"

    /**
     * Gson - JSON Serialization
     *
     * Used in Chapter 6 for scene save/load.
     * Alternative: Jackson (more features, heavier)
     * Gson: Simple, lightweight, perfect for game saves.
     */
    implementation "com.google.code.gson:gson:$gsonVersion"

    /**
     * Logging
     *
     * SLF4J provides logging abstraction.
     * slf4j-simple: Minimal implementation (stdout only).
     *
     * Alternatives:
     * - Logback (full-featured, config files)
     * - Log4j2 (enterprise, complex)
     *
     * For games: slf4j-simple is enough during development.
     */
    implementation 'org.slf4j:slf4j-api:2.0.9'
    implementation 'org.slf4j:slf4j-simple:2.0.9'

    /**
     * Platform-Specific Natives
     *
     * LWJGL Java code (JARs) calls native libraries (.dll/.so/.dylib).
     * These runtimeOnly dependencies download the natives.
     *
     * HOW IT WORKS:
     * 1. Gradle detects platform (Windows/Linux/macOS)
     * 2. Downloads correct natives (e.g., lwjgl-glfw-natives-windows.jar)
     * 3. LWJGL extracts natives to temp directory at runtime
     * 4. JNI loads natives (e.g., glfw3.dll)
     *
     * Classifier: ::$lwjglNatives
     * - Empty artifact name → inherit from BOM
     * - Classifier → platform-specific variant
     */
    runtimeOnly "org.lwjgl:lwjgl::$lwjglNatives"
    runtimeOnly "org.lwjgl:lwjgl-assimp::$lwjglNatives"
    runtimeOnly "org.lwjgl:lwjgl-glfw::$lwjglNatives"
    runtimeOnly "org.lwjgl:lwjgl-openal::$lwjglNatives"
    runtimeOnly "org.lwjgl:lwjgl-stb::$lwjglNatives"

    /**
     * Testing
     *
     * JUnit 5 (Jupiter) for unit tests.
     * Includes: assertions, parameterized tests, test lifecycle.
     */
    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.1'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

/**
 * Application Main Class
 *
 * Used by `gradle run` task.
 */
application {
    mainClass = 'com.yourname.engine.Main'
}

/**
 * Java Compilation Configuration
 *
 * --enable-preview: Use Java 25 preview features
 * encoding: UTF-8 for cross-platform compatibility
 */
tasks.withType(JavaCompile).configureEach {
    options.compilerArgs += ['--enable-preview']
    options.encoding = 'UTF-8'
}

/**
 * Test Configuration
 */
tasks.withType(Test).configureEach {
    jvmArgs += ['--enable-preview']
    useJUnitPlatform()
}

/**
 * Runtime Configuration
 *
 * Applied to `gradle run` and custom JavaExec tasks.
 */
tasks.withType(JavaExec).configureEach {
    jvmArgs += ['--enable-preview']

    // Enable assertions (catch bugs early)
    enableAssertions = true

    /**
     * JVM Tuning for Game Engines
     *
     * -XX:+UseZGC: ZGC (Z Garbage Collector)
     *   - Concurrent GC (runs while game is running)
     *   - Low latency (pauses <10ms, often <1ms)
     *   - Scalable (handles 4GB to 16TB heaps)
     *
     * -XX:+ZGenerational: Generational ZGC (Java 25)
     *   - Separates short-lived (entities) vs long-lived (systems) objects
     *   - 50% less GC overhead for typical workloads
     *
     * -Xms512m: Initial heap size
     *   - Pre-allocate memory (avoid resize pauses)
     *
     * -Xmx2g: Maximum heap size
     *   - Adjust based on available RAM
     *   - Game engine: 2-4GB typical
     *
     * -XX:+UseStringDeduplication: Deduplicate strings
     *   - Many entities have duplicate strings ("Enemy", "Player")
     *   - Saves memory without performance cost
     */
    jvmArgs += [
        '-XX:+UseZGC',
        '-XX:+ZGenerational',
        '-Xms512m',
        '-Xmx2g',
        '-XX:+UseStringDeduplication'
    ]
}

/**
 * Custom Task: Run with Vulkan Validation
 *
 * Usage: gradle runDebug
 *
 * Enables Vulkan validation layers (development only).
 * Catches API misuse, memory leaks, performance issues.
 *
 * Environment variables:
 * - VK_INSTANCE_LAYERS: Enable validation layer
 * - VK_LAYER_ENABLES: Enable best practices warnings
 */
tasks.register('runDebug', JavaExec) {
    group = 'application'
    description = 'Run with Vulkan validation layers (development)'
    classpath = sourceSets.main.runtimeClasspath
    mainClass = application.mainClass

    jvmArgs += ['--enable-preview']
    enableAssertions = true

    // Vulkan validation layers
    environment 'VK_INSTANCE_LAYERS', 'VK_LAYER_KHRONOS_validation'
    environment 'VK_LAYER_ENABLES', 'VK_VALIDATION_FEATURE_ENABLE_BEST_PRACTICES_EXT'

    // Verbose LWJGL (debug native library loading)
    // environment 'LWJGL_DEBUG', 'true'

    // Show GC activity (optional)
    // jvmArgs += ['-Xlog:gc']
}

/**
 * Build Performance: Incremental Compilation
 *
 * Gradle recompiles only changed files (not entire project).
 * Dramatically speeds up iterative development.
 */
tasks.withType(JavaCompile) {
    options.incremental = true
    options.fork = true  // Compile in separate JVM (better isolation)
}
```

**Understanding This Configuration:**

**Dependency Resolution:**
```
1. Gradle reads build.gradle
2. Downloads dependencies from Maven Central
3. Resolves transitive dependencies (LWJGL → JNI bindings)
4. Downloads platform-specific natives
5. Stores in ~/.gradle/caches/ (shared across projects)
```

**Native Library Loading:**
```
Runtime:
1. LWJGL extracts natives to temp directory
   (e.g., C:\Users\You\AppData\Local\Temp\lwjgl-<user>)
2. Sets java.library.path to temp directory
3. Calls System.loadLibrary("glfw3")
4. JVM loads glfw3.dll from temp directory
```

### Update settings.gradle

```groovy
rootProject.name = 'jecs-engine'

/**
 * Build Cache Configuration
 *
 * Gradle can cache build outputs and reuse them.
 * Speeds up builds when switching branches or rebuilding.
 *
 * Local cache: ~/.gradle/caches/build-cache-1
 */
buildCache {
    local {
        enabled = true
    }
}
```

---

## Step 6: Hello LWJGL Verification

Let's create a comprehensive verification program.

### Create Main.java

Edit `src/main/java/com/yourname/engine/Main.java`:

```java
package com.yourname.engine;

import org.lwjgl.Version;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.system.MemoryStack;
import org.joml.Vector3f;
import org.joml.Matrix4f;

import java.nio.IntBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Environment verification program.
 *
 * This checks that all dependencies are correctly installed:
 * - Java 25 with preview features
 * - LWJGL and platform-specific natives
 * - JOML math library
 * - GLFW window system
 * - Vulkan support and SDK
 *
 * If all checks pass, you're ready for Chapter 1!
 */
public class Main {
    public static void main(String[] args) {
        printHeader("JECS Engine - Environment Verification");

        checkJavaVersion();
        checkLWJGL();
        checkJOML();
        checkGLFW();
        checkVulkan();
        createTestWindow();

        printHeader("✓ All checks passed! Ready for Chapter 1.");
    }

    /**
     * Check Java version (must be 25+).
     */
    private static void checkJavaVersion() {
        String version = System.getProperty("java.version");
        String vm = System.getProperty("java.vm.name");

        System.out.printf("✓ Java Version: %s (%s)%n", version, vm);

        if (!version.startsWith("25")) {
            System.err.printf("⚠ Warning: Expected Java 25, found %s%n", version);
            System.err.println("  Some features may not work correctly.");
        }

        // Check preview features enabled
        try {
            // This will fail if --enable-preview not set
            Class.forName("java.lang.runtime.SwitchBootstraps");
            System.out.println("✓ Preview Features: Enabled");
        } catch (ClassNotFoundException e) {
            System.err.println("✗ Preview Features: Not enabled");
            System.err.println("  Add --enable-preview to JVM arguments");
        }
    }

    /**
     * Check LWJGL installation.
     */
    private static void checkLWJGL() {
        String lwjglVersion = Version.getVersion();
        System.out.printf("✓ LWJGL Version: %s%n", lwjglVersion);

        // Check native library path
        String libPath = System.getProperty("java.library.path");
        System.out.printf("✓ Native Library Path: %s%n",
            libPath.length() > 60 ? libPath.substring(0, 57) + "..." : libPath);
    }

    /**
     * Check JOML (math library).
     */
    private static void checkJOML() {
        // Test vector operations
        Vector3f vec = new Vector3f(1, 2, 3);
        vec.normalize();

        // Test matrix operations
        Matrix4f mat = new Matrix4f()
            .identity()
            .translate(10, 20, 30)
            .rotateY((float) Math.toRadians(45));

        System.out.printf("✓ JOML Working: Vec%s, Mat4f operations OK%n", vec);
    }

    /**
     * Initialize GLFW (window system).
     */
    private static void checkGLFW() {
        // Set error callback
        GLFWErrorCallback.createPrint(System.err).set();

        // Initialize GLFW
        if (!glfwInit()) {
            throw new IllegalStateException("Failed to initialize GLFW");
        }

        System.out.println("✓ GLFW Initialized");

        // Check GLFW version
        System.out.printf("✓ GLFW Version: %s%n", glfwGetVersionString());
    }

    /**
     * Check Vulkan support.
     */
    private static void checkVulkan() {
        // Check if Vulkan is supported by GLFW
        boolean vulkanSupported = glfwVulkanSupported();
        System.out.printf("✓ Vulkan Supported: %s%n", vulkanSupported);

        if (!vulkanSupported) {
            System.err.println("✗ Vulkan not supported!");
            System.err.println("  Possible causes:");
            System.err.println("  1. Graphics drivers outdated");
            System.err.println("  2. Vulkan SDK not installed");
            System.err.println("  3. GPU doesn't support Vulkan");
            System.err.println();
            System.err.println("  Solutions:");
            System.err.println("  - Update graphics drivers from manufacturer website");
            System.err.println("  - Install Vulkan SDK from vulkan.lunarg.com");
            System.err.println("  - Check GPU support at vulkan.gpuinfo.org");

            glfwTerminate();
            System.exit(1);
        }

        // Query Vulkan instance extensions
        try (MemoryStack stack = stackPush()) {
            IntBuffer count = stack.ints(0);
            VK10.vkEnumerateInstanceExtensionProperties((String) null, count, null);
            int extensionCount = count.get(0);

            System.out.printf("✓ Vulkan Instance Extensions: %d available%n", extensionCount);

            if (extensionCount == 0) {
                System.err.println("⚠ Warning: No Vulkan extensions found");
                System.err.println("  This may indicate Vulkan loader issues");
            }
        }

        // Check for validation layers (development)
        try (MemoryStack stack = stackPush()) {
            IntBuffer count = stack.ints(0);
            VK10.vkEnumerateInstanceLayerProperties(count, null);
            int layerCount = count.get(0);

            System.out.printf("✓ Vulkan Validation Layers: %d available%n", layerCount);

            if (layerCount == 0) {
                System.err.println("⚠ Warning: No validation layers found");
                System.err.println("  Install Vulkan SDK for development tools");
            }
        }
    }

    /**
     * Create a test window (verifies full GLFW+Vulkan stack).
     */
    private static void createTestWindow() {
        // Configure window for Vulkan (not OpenGL)
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API); // No OpenGL context
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);     // Hidden window (test only)

        // Create window
        long window = glfwCreateWindow(800, 600, "JECS Engine Test", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Failed to create GLFW window");
        }

        System.out.println("✓ GLFW Window Created (hidden test window)");

        // Get window size (verify GLFW callback system)
        try (MemoryStack stack = stackPush()) {
            IntBuffer width = stack.ints(0);
            IntBuffer height = stack.ints(0);
            glfwGetWindowSize(window, width, height);

            System.out.printf("✓ Window Size: %dx%d%n", width.get(0), height.get(0));
        }

        // Cleanup
        glfwDestroyWindow(window);
        glfwTerminate();

        System.out.println("✓ Cleanup Complete");
    }

    /**
     * Print formatted header.
     */
    private static void printHeader(String message) {
        String line = "=".repeat(60);
        System.out.println(line);
        System.out.println(message);
        System.out.println(line);
    }
}
```

### Run Verification

```bash
# Using Gradle wrapper (recommended)
./gradlew run      # Linux/macOS
gradlew.bat run    # Windows

# Or direct Gradle (if installed globally)
gradle run
```

**Expected Output:**
```
============================================================
JECS Engine - Environment Verification
============================================================
✓ Java Version: 25 (OpenJDK 64-Bit Server VM)
✓ Preview Features: Enabled
✓ LWJGL Version: 3.3.4
✓ Native Library Path: C:\Users\You\AppData\Local\Temp\lwjgl-...
✓ JOML Working: Vec(0.267, 0.535, 0.802), Mat4f operations OK
✓ GLFW Initialized
✓ GLFW Version: 3.3.9 Win32 WGL Null EGL OSMesa
✓ Vulkan Supported: true
✓ Vulkan Instance Extensions: 27 available
✓ Vulkan Validation Layers: 8 available
✓ GLFW Window Created (hidden test window)
✓ Window Size: 800x600
✓ Cleanup Complete
============================================================
✓ All checks passed! Ready for Chapter 1.
============================================================
```

### Troubleshooting

**Problem 1: "UnsatisfiedLinkError: no lwjglXXX in java.library.path"**

**Cause:** Native libraries not downloaded or extracted.

**Solution:**
```bash
# Force dependency re-download
./gradlew clean build --refresh-dependencies

# Verify natives downloaded
ls ~/.gradle/caches/modules-2/files-2.1/org.lwjgl/

# Should see directories like:
# lwjgl-glfw-natives-windows/
# lwjgl-glfw-natives-linux/
# lwjgl-glfw-natives-macos/
```

**Problem 2: "Vulkan not supported: false"**

**Cause:** Outdated graphics drivers or missing Vulkan SDK.

**Solution:**
```bash
# 1. Update drivers (restart after)
# NVIDIA: nvidia.com/drivers
# AMD: amd.com/support
# Intel: intel.com/graphics-drivers

# 2. Verify Vulkan loader
vulkaninfo --summary

# 3. Check environment variables
echo $VULKAN_SDK           # Should show SDK path
echo $VK_LAYER_PATH        # Should show layer path
```

**Problem 3: "Java version mismatch"**

**Cause:** Wrong Java version in PATH or Gradle using different JDK.

**Solution:**
```bash
# Check which Java Gradle uses
./gradlew -version

# Should show:
# JVM: 25 (Oracle Corporation 25+36)

# If wrong, set JAVA_HOME explicitly
export JAVA_HOME=/path/to/jdk-25   # Linux/macOS
$env:JAVA_HOME = "C:\...\jdk-25"   # Windows

# Or configure in gradle.properties
echo "org.gradle.java.home=/path/to/jdk-25" > gradle.properties
```

**Problem 4: "Preview features not enabled"**

**Cause:** Missing `--enable-preview` flag.

**Solution:**
Check `build.gradle` has:
```groovy
tasks.withType(JavaCompile).configureEach {
    options.compilerArgs += ['--enable-preview']
}

tasks.withType(JavaExec).configureEach {
    jvmArgs += ['--enable-preview']
}
```

Rebuild:
```bash
./gradlew clean build
./gradlew run
```

---

## Step 7: Project Organization

### Create Directory Structure

```bash
# Create core engine packages
mkdir -p src/main/java/com/yourname/engine/core
mkdir -p src/main/java/com/yourname/engine/ecs
mkdir -p src/main/java/com/yourname/engine/renderer
mkdir -p src/main/java/com/yourname/engine/scene
mkdir -p src/main/java/com/yourname/engine/input
mkdir -p src/main/java/com/yourname/engine/audio
mkdir -p src/main/java/com/yourname/engine/editor

# Create resource directories
mkdir -p src/main/resources/shaders
mkdir -p src/main/resources/textures
mkdir -p src/main/resources/models
mkdir -p src/main/resources/audio
mkdir -p src/main/resources/scenes

# Create test directories
mkdir -p src/test/java/com/yourname/engine/ecs
mkdir -p src/test/java/com/yourname/engine/scene
```

### Final Project Structure

```
jecs-engine/
├── build.gradle                 ← Build configuration
├── settings.gradle              ← Project settings
├── gradlew / gradlew.bat        ← Gradle wrapper scripts
├── gradle/wrapper/              ← Wrapper JARs
│   ├── gradle-wrapper.jar
│   └── gradle-wrapper.properties
│
└── src/
    ├── main/
    │   ├── java/com/yourname/engine/
    │   │   ├── Main.java        ← Entry point
    │   │   ├── core/            ← Chapter 1 (Engine, Application, Window)
    │   │   ├── ecs/             ← Chapter 2 (Entity, Component, System, World)
    │   │   ├── renderer/        ← Chapters 3-5, 9 (Vulkan, Meshes, PBR)
    │   │   ├── scene/           ← Chapter 6 (Serialization, Prefabs)
    │   │   ├── input/           ← Chapter 7 (Input system)
    │   │   ├── audio/           ← Chapter 7 (OpenAL)
    │   │   └── editor/          ← Chapter 8 (ImGui panels)
    │   │
    │   └── resources/
    │       ├── shaders/         ← GLSL vertex/fragment shaders
    │       ├── textures/        ← PNG, JPEG images
    │       ├── models/          ← OBJ, GLTF meshes
    │       ├── audio/           ← WAV, OGG sounds
    │       └── scenes/          ← JSON scene files
    │
    └── test/
        └── java/com/yourname/engine/
            ├── ecs/             ← ECS unit tests
            └── scene/           ← Serialization tests
```

**Package Naming Convention:**

```
com.yourname.engine       ← Reverse domain (com.github.yourname)
    .core                 ← Engine infrastructure (loop, lifecycle)
    .ecs                  ← Entity-Component-System
    .renderer             ← Graphics (Vulkan, pipelines, meshes)
        .vulkan           ← Vulkan-specific (instance, device, swapchain)
    .scene                ← Scene graph, serialization
    .input                ← Input handling (keyboard, mouse, gamepad)
    .audio                ← 3D audio (OpenAL)
    .editor               ← Editor UI (ImGui panels)
```

---

## Performance: Java 25 JVM Tuning

### Understanding Garbage Collection

**The Problem with Traditional GC:**

```
Traditional GC (G1):
────────────────────────────────
Frame 1: Render 16.6ms  ✓ 60 FPS
Frame 2: Render 16.6ms  ✓ 60 FPS
Frame 3: Render 16.6ms + GC 150ms = 166.6ms  ✗ 6 FPS (dropped frames!)
Frame 4: Render 16.6ms  ✓ 60 FPS

Result: Periodic stutters (user notices!)
```

**ZGC Solution:**

```
ZGC (Generational):
────────────────────────────────
Frame 1: Render 16.6ms + Concurrent GC (background) = 16.6ms  ✓ 60 FPS
Frame 2: Render 16.6ms + Concurrent GC (background) = 16.6ms  ✓ 60 FPS
Frame 3: Render 16.6ms + GC pause 0.5ms = 17.1ms  ✓ 58 FPS (barely noticeable)
Frame 4: Render 16.6ms + Concurrent GC (background) = 16.6ms  ✓ 60 FPS

Result: Smooth gameplay, no stutters!
```

### Recommended JVM Flags

**Development (already in build.gradle):**
```groovy
jvmArgs += [
    '-XX:+UseZGC',                  // Enable ZGC
    '-XX:+ZGenerational',           // Generational mode (Java 25)
    '-Xms512m',                     // Initial heap
    '-Xmx2g',                       // Max heap
    '-XX:+UseStringDeduplication',  // Save memory
]
```

**Production (add these for release builds):**
```groovy
jvmArgs += [
    '-XX:+UseZGC',
    '-XX:+ZGenerational',
    '-Xms2g',                       // Pre-allocate more (avoid resizing)
    '-Xmx4g',                       // Larger heap for big scenes
    '-XX:+AlwaysPreTouch',          // Touch all memory upfront (no allocation pauses)
    '-XX:ConcGCThreads=2',          // Concurrent GC threads (tune to CPU cores)
    '-XX:ParallelGCThreads=4',      // Parallel GC threads
    '-XX:+UnlockExperimentalVMOptions',
    '-XX:ZCollectionInterval=30',   // Force GC every 30 seconds (predictable)
]
```

**Profiling (when debugging performance):**
```groovy
jvmArgs += [
    '-Xlog:gc*:file=gc.log',        // Log GC activity to file
    '-XX:+UnlockDiagnosticVMOptions',
    '-XX:+DebugNonSafepoints',      // Better profiler data
    '-XX:+PrintCompilation',         // Show JIT compilation
]
```

### Alternative: Shenandoah GC

**For comparison:**
```groovy
jvmArgs += [
    '-XX:+UseShenandoahGC',         // Alternative low-latency GC
    '-Xms512m',
    '-Xmx2g',
]
```

**ZGC vs Shenandoah:**

| Feature | ZGC | Shenandoah |
|---------|-----|------------|
| **Pause Time** | <1ms (typical) | <10ms (typical) |
| **Throughput** | ~5% overhead | ~10% overhead |
| **Memory** | 4GB - 16TB | 512MB - 16TB |
| **Best For** | Low-latency games | General-purpose |

**Recommendation:** Use ZGC for game engines (lower pause times matter for frame consistency).

---

## What's Next?

### Checklist

Before moving to Chapter 1, verify:

- ✅ Java 25 installed (`java -version`)
- ✅ Gradle installed (`gradle -version`)
- ✅ Vulkan SDK installed (`vulkaninfo --summary`)
- ✅ IDE configured (IntelliJ, VS Code, or Eclipse)
- ✅ Project created (`jecs-engine` directory)
- ✅ Dependencies resolved (`./gradlew build`)
- ✅ Verification passed (`./gradlew run` shows all checks passing)

### Coming Up in Chapter 1

In **Chapter 1: Window & Engine Loop**, we'll:

1. Create the **Engine class** (main game loop)
2. Create the **Application class** (user-facing interface)
3. Initialize **GLFW window** with proper error handling
4. Implement **game loop** with fixed timestep
5. Initialize **Vulkan** and clear screen to color
6. Add **FPS counter** and frame timing
7. Handle **window events** (close, resize)

**Goal:** A running engine that displays a colored window at 60 FPS with Vulkan!

---

## Exercises

To solidify your understanding, try these before Chapter 1:

### Exercise 1: Dependency Exploration

Modify `build.gradle` to print all resolved dependencies:

```groovy
tasks.register('printDependencies') {
    doLast {
        configurations.runtimeClasspath.each {
            println it.name
        }
    }
}
```

Run: `./gradlew printDependencies`

**Expected:** List of ~50 JARs including:
- `lwjgl-3.3.4.jar`
- `lwjgl-glfw-3.3.4.jar`
- `lwjgl-glfw-3.3.4-natives-windows.jar` (or linux/macos)
- `joml-1.10.5.jar`
- `gson-2.10.1.jar`

### Exercise 2: Native Library Inspection

Add this to `Main.java`:

```java
System.out.println("\nNative Library Extraction:");
String tempDir = System.getProperty("java.io.tmpdir");
File lwjglDir = new File(tempDir).listFiles((dir, name) ->
    name.startsWith("lwjgl"))[0];

if (lwjglDir != null) {
    System.out.println("LWJGL natives: " + lwjglDir.getAbsolutePath());
    for (File file : lwjglDir.listFiles()) {
        System.out.println("  - " + file.getName());
    }
}
```

**Expected:** List of extracted natives (e.g., `glfw3.dll`, `OpenAL.dll`).

### Exercise 3: JOML Performance Test

Create a benchmark:

```java
public static void benchmarkJOML() {
    System.out.println("\nJOML Matrix Benchmark:");

    int iterations = 1_000_000;
    Matrix4f mat = new Matrix4f();

    long start = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
        mat.identity()
            .translate(i, i, i)
            .rotateY((float) Math.toRadians(45))
            .scale(2, 2, 2);
    }
    long end = System.nanoTime();

    double ms = (end - start) / 1_000_000.0;
    System.out.printf("1M matrix ops: %.2fms (%.0f ops/ms)%n",
        ms, iterations / ms);
}
```

**Expected:** ~50-100ms (10K-20K matrix operations per millisecond).

### Exercise 4: Vulkan Extension Query

List all Vulkan extensions with names:

```java
try (MemoryStack stack = stackPush()) {
    IntBuffer count = stack.ints(0);
    VK10.vkEnumerateInstanceExtensionProperties((String) null, count, null);

    VkExtensionProperties.Buffer extensions = VkExtensionProperties.malloc(count.get(0), stack);
    VK10.vkEnumerateInstanceExtensionProperties((String) null, count, extensions);

    System.out.println("\nVulkan Extensions:");
    for (int i = 0; i < extensions.capacity(); i++) {
        VkExtensionProperties ext = extensions.get(i);
        System.out.printf("  - %s (v%d)%n",
            ext.extensionNameString(),
            ext.specVersion());
    }
}
```

**Expected:** List including `VK_KHR_surface`, `VK_KHR_win32_surface`, etc.

---

## Further Reading

### Essential Resources

- **LWJGL Guide**: [lwjgl.org/guide](https://www.lwjgl.org/guide)
- **Vulkan Tutorial**: [vulkan-tutorial.com](https://vulkan-tutorial.com/)
- **JOML Documentation**: [joml-ci.github.io/JOML](https://joml-ci.github.io/JOML/)
- **Java 25 Release Notes**: [openjdk.org/projects/jdk/25](https://openjdk.org/projects/jdk/25/)

### Deep Dives

- **ZGC Internals**: [malloc.se/blog/zgc](https://malloc.se/blog/zgc-jdk16)
- **Gradle Performance**: [docs.gradle.org/current/userguide/performance.html](https://docs.gradle.org/current/userguide/performance.html)
- **Vulkan Validation**: [lunarg.com/vulkan-validation-layers](https://www.lunarg.com/vulkan-sdk/)

### Community

- **LWJGL Forums**: [forum.lwjgl.org](https://forum.lwjgl.org/)
- **Vulkan Discord**: [discord.gg/vulkan](https://discord.gg/vulkan)
- **r/gamedev**: [reddit.com/r/gamedev](https://www.reddit.com/r/gamedev/)

---

**Next:** [Chapter 1 - Window + Engine Loop + Vulkan Clear Screen →](chapter-01-window-and-loop.md)
