# JECS - Java Entity-Component-System Game Engine

A modular, Vulkan-powered game engine built in Java using LWJGL 3.

**Status:** Phase 1 Complete - Multi-Module Architecture Implemented
**Version:** 1.0-SNAPSHOT

---

## Features

✅ **Modular Architecture** - Independent engine subsystems
✅ **Vulkan Rendering** - Modern graphics API
✅ **Fixed Timestep Game Loop** - Smooth 60 Hz physics
✅ **ECS Architecture** *(Phase 2)* - Data-oriented design
✅ **2D/3D Rendering** *(Phases 4-5)* - Sprite and mesh rendering
✅ **Physics & Audio** *(Phases 7,10)* - Full game engine features

---

## Quick Start

### Prerequisites

- **Java 11+** (JDK 21 recommended)
- **Vulkan SDK** with validation layers
- **Gradle 9.2+** (wrapper included)

### Build and Run

```bash
# Clone repository
cd C:/Dev/Workspace/jecs

# Build all modules
./gradlew build

# Run rainbow demo
./gradlew :demos:rainbow-demo:run
```

**Expected:** Window opens with animated rainbow colors cycling smoothly.

---

## Project Structure

```
jecs/
├── engine-core/              # Core engine (Window, Time, Engine)
├── engine-renderer/          # Vulkan rendering abstraction
├── engine-ecs/              # Entity-Component-System (Phase 2)
├── engine-2d/               # 2D rendering (Phase 4)
├── engine-3d/               # 3D rendering (Phase 5)
├── engine-input/            # Input system (Phase 7)
├── engine-audio/            # Audio system (Phase 7)
├── engine-physics/          # Physics (Phase 10)
├── engine-editor/           # ImGui editor (Phase 8)
├── engine-scripting/        # Scripting (Phase 11)
└── demos/
    ├── rainbow-demo/        # ✅ Phase 1 - Minimal Vulkan demo
    ├── space-shooter/       # Phase 4 - 2D game
    └── flight-combat/       # Phase 5 - 3D game
```

---

## Module Architecture

JECS is designed so games can depend on only the modules they need:

### Minimal 2D Game
```gradle
dependencies {
    implementation project(':engine-core')
    implementation project(':engine-ecs')
    implementation project(':engine-2d')
    implementation project(':engine-input')
}
```

### Full-Featured 3D Game
```gradle
dependencies {
    implementation project(':engine-core')
    implementation project(':engine-ecs')
    implementation project(':engine-3d')
    implementation project(':engine-input')
    implementation project(':engine-audio')
    implementation project(':engine-physics')
}
```

See [ARCHITECTURE.md](ARCHITECTURE.md) for detailed module documentation.

---

## Documentation

- **[ARCHITECTURE.md](ARCHITECTURE.md)** - Multi-module design and dependencies
- **[README_BUILD.md](README_BUILD.md)** - Build instructions (in engine-core/)
- **[IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md)** - Phase 1 implementation details (in engine-core/)
- **[PHASE_1_SUMMARY.md](PHASE_1_SUMMARY.md)** - Phase 1 quick reference (in engine-core/)

---

## Development Roadmap

### ✅ Phase 1: Foundation (Complete)
- Core engine loop
- Vulkan initialization
- RainbowDemo

### 🔄 Phase 2: ECS Core (In Progress)
- World, Entity, Component, System
- Sparse set storage
- Unit tests

### ⏳ Phase 3: Renderer Abstraction
- Modular Vulkan classes
- Fix semaphore synchronization

### ⏳ Phase 4: 2D Rendering
- Textures, sprites, batching
- **SpaceShooterGame demo** 🎮

### ⏳ Phase 5: 3D Rendering
- Meshes, camera, materials
- **FlightCombatGame demo** 🎮

### ⏳ Phases 6-14
- Scene serialization (6)
- Input & Audio (7)
- ImGui editor (8)
- Advanced rendering - PBR, lighting (9)
- Physics (10)
- Scripting (11)
- ECS optimization (12)
- Profiling (13)
- Polish & testing (14)

---

## Technology Stack

| Component | Technology |
|-----------|------------|
| **Language** | Java 11+ |
| **Graphics API** | Vulkan 1.4 |
| **Windowing** | GLFW 3.3.4 |
| **Math** | JOML 1.10.5 |
| **Audio** | OpenAL (Phase 7) |
| **UI** | ImGui (Phase 8) |
| **Build System** | Gradle 9.2 |

---

## Example Code

### Hello World Game

```java
package com.example;

import com.jecs.core.*;

public class HelloGame implements Application {
    @Override
    public void init(Window window, Time time) {
        System.out.println("Game initialized!");
    }

    @Override
    public void fixedUpdate(float dt) {
        // Physics at 60 Hz
    }

    @Override
    public void render(float alpha) {
        // Rendering with interpolation
    }

    @Override
    public void cleanup() {
        System.out.println("Game cleaned up!");
    }

    public static void main(String[] args) {
        Engine engine = new Engine(new HelloGame());
        engine.run();
    }
}
```

---

## Building Your Game

1. Create a new Gradle project
2. Add JECS modules as dependencies
3. Implement the `Application` interface
4. Run with `Engine.run()`

See [demos/rainbow-demo/](demos/rainbow-demo/) for a complete example.

---

## Contributing

JECS is currently in active development (Phase 2). Contributions welcome after Phase 3!

---

## Known Issues

- **Semaphore synchronization warnings** - Will be fixed in Phase 3
- **No swap chain recreation** - Window resize not yet implemented
- See [IMPLEMENTATION_STATUS.md](engine-core/IMPLEMENTATION_STATUS.md) for full list

---

## License

MIT License (see LICENSE file)

---

## Credits

Built with:
- [LWJGL](https://www.lwjgl.org/) - Java bindings for native libraries
- [JOML](https://joml-ci.github.io/JOML/) - Java OpenGL Math Library
- [Vulkan Tutorial](https://vulkan-tutorial.com/) - Vulkan reference

---

**Ready to build games?** Check out the [demos/](demos/) folder for examples!
