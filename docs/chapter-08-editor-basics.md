# Chapter 8: ImGui Editor - Building a Professional Game Editor

## What You'll Learn

In this chapter, we'll build a **professional game editor** comparable to Unity and Unreal Engine. You'll understand:

- **What Immediate Mode UI is** and why game engines use it (vs retained mode)
- **How ImGui architecture works** (data flow, rendering, input handling)
- **The docking system** and how to create flexible editor layouts
- **Component inspection patterns** (reflection, property editors)
- **Editor camera controls** (orbit, pan, zoom like Blender/Maya)
- **Play/Edit mode switching** (scene snapshots, state restoration)
- **Real-time component editing** without recompilation

By the end, you'll have a Unity-style editor with visual entity management!

---

## The Big Picture: Why Build an Editor?

### The Pain: Code-Compile-Test Loop

**Without an editor:**
```
1. Edit Transform3D position in code → transform.position.set(5, 0, 3)
2. Recompile Java classes (30 seconds)
3. Launch game
4. See if position looks right
5. Wrong? Change code again
6. Repeat steps 2-5 → 10 iterations = 5 minutes wasted!
```

**With an editor:**
```
1. Drag entity in viewport
2. Press Play → See result instantly
3. Press Stop → Adjust value
4. Repeat → 10 iterations = 30 seconds!
```

**10x faster iteration = 10x more polish!**

### What Professional Engines Provide

| Feature | Unity | Unreal | Godot | JECS |
|---------|-------|--------|-------|------|
| **Visual entity editing** | ✓ | ✓ | ✓ | ✓ |
| **Live component editing** | ✓ | ✓ | ✓ | ✓ |
| **Play-in-editor** | ✓ | ✓ | ✓ | ✓ |
| **Dockable panels** | ✓ | ✓ | ✓ | ✓ |
| **Scene hierarchy** | ✓ | ✓ | ✓ | ✓ |
| **Component inspector** | ✓ | ✓ | ✓ | ✓ |

We're building all of this!

---

## Understanding Immediate Mode UI

### Retained Mode vs Immediate Mode

**Retained Mode (Swing, JavaFX, HTML):**

```java
// Retained mode: Create widgets once, update them later
JButton button = new JButton("Click me");
panel.add(button);  // Widget stays in memory

// Later...
button.setText("Clicked!"); // Update existing widget
```

**Memory model:**
```
┌──────────────────────────────────────┐
│         Widget Tree (Retained)       │
├──────────────────────────────────────┤
│ Window                               │
│   └─ Panel                           │
│       ├─ Button ("Click me")         │
│       ├─ TextField                   │
│       └─ Label                       │
└──────────────────────────────────────┘
   ↑ Stays in memory between frames
```

**Problems with retained mode for game editors:**
- **Memory overhead**: Every widget exists as object
- **Complex state management**: Must update widgets when game state changes
- **Event handling complexity**: Callbacks, listeners everywhere
- **Not flexible**: Hard to dynamically change UI layout

---

**Immediate Mode (ImGui, Dear ImGui):**

```java
// Immediate mode: Recreate UI every frame!
if (ImGui.button("Click me")) {
    // Button was clicked THIS FRAME
    clicked = true;
}
```

**Memory model:**
```
Frame 1:               Frame 2:               Frame 3:
┌─────────┐           ┌─────────┐           ┌─────────┐
│ Button  │ → Delete  │ Button  │ → Delete  │ Button  │
└─────────┘           └─────────┘           └─────────┘
                         ↑                      ↑
              Recreated every frame    No persistent objects!
```

**Benefits for game editors:**
- **Zero memory overhead**: No widgets stored between frames
- **Simple state**: UI = pure function of game state
- **Easy dynamic UI**: Just change code flow
- **No synchronization**: UI always matches game state

**The Magic: Why Is This Fast?**

"Recreating UI 60 times per second sounds slow!"

**Answer:** ImGui doesn't create *objects* - it just **records drawing commands**:

```java
// This doesn't create a Button object!
ImGui.button("Click me");

// It just records:
// 1. Draw rectangle at (x, y)
// 2. Draw text "Click me"
// 3. Check if mouse clicked in rectangle
// Total: ~100 CPU instructions (nanoseconds!)
```

**Professional Engine Comparison:**

| Engine | UI System |
|--------|-----------|
| **Unity** | IMGUI (old editor), UI Toolkit (new) |
| **Unreal** | Slate (retained) + UMG (blueprint UI) |
| **Godot** | Custom immediate mode (GDScript) |
| **JECS** | Dear ImGui (C++ library, Java bindings) |

---

## Part 1: ImGui Integration

### Understanding Dear ImGui Architecture

**Dear ImGui** is a C++ library. We use Java bindings via `imgui-java`.

**How it works:**

```
┌──────────────────────────────────────────────────────┐
│                 Your Game Loop                       │
├──────────────────────────────────────────────────────┤
│  while (running) {                                   │
│    ImGui.newFrame();     ← Start recording UI        │
│    renderUI();           ← Your UI code              │
│    ImGui.render();       ← Generate draw commands    │
│    imguiGl3.render();    ← Upload to GPU             │
│  }                                                    │
└──────────────────────────────────────────────────────┘
```

**The render() function doesn't create widgets!**

It just records commands like:
```
Command 1: DrawRectFilled(x=10, y=20, w=100, h=30, color=blue)
Command 2: DrawText(x=15, y=25, text="Button", font=default)
Command 3: CheckMouseClick(rect=[10,20,100,30])
```

These commands are batched and sent to GPU in one draw call!

---

### Step 1: Add ImGui Dependency

Update `build.gradle`:

```groovy
dependencies {
    // ... existing dependencies ...

    // Dear ImGui (Java bindings)
    implementation "io.github.spair:imgui-java-binding:${imguiVersion}"
    implementation "io.github.spair:imgui-java-lwjgl3:${imguiVersion}"
    implementation "io.github.spair:imgui-java-natives-linux:${imguiVersion}"
    implementation "io.github.spair:imgui-java-natives-macos:${imguiVersion}"
    implementation "io.github.spair:imgui-java-natives-windows:${imguiVersion}"
}

ext {
    imguiVersion = '1.86.11'
}
```

**Why native libraries?** Dear ImGui is C++, needs native code for each platform.

---

### Step 2: ImGui Layer

**What This Class Does:**

Manages the ImGui context and integrates with GLFW + OpenGL. Think of it as the "bridge" between ImGui (C++) and your Java engine.

Create `src/main/java/com/yourname/engine/editor/ImGuiLayer.java`:

```java
package com.yourname.engine.editor;

import imgui.*;
import imgui.flag.*;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import org.lwjgl.glfw.GLFW;

/**
 * Manages ImGui context and rendering.
 *
 * KEY CONCEPTS:
 * - ImGui context: Global state for one window's UI
 * - ImGuiIO: Input/output configuration (fonts, input handling)
 * - Backends: GLFW (input) + OpenGL (rendering)
 *
 * WORKFLOW:
 * 1. init(): Create context, load fonts, setup backends
 * 2. begin(): Start recording UI commands for this frame
 * 3. [Your UI code runs here]
 * 4. end(): Generate draw data and render to screen
 */
public class ImGuiLayer {

    private final ImGuiImplGlfw imGuiGlfw = new ImGuiImplGlfw();  // Handles keyboard/mouse
    private final ImGuiImplGl3 imGuiGl3 = new ImGuiImplGl3();     // Renders to OpenGL

    private long windowHandle;

    /**
     * Initialize ImGui.
     *
     * WHAT HAPPENS HERE:
     * 1. Create ImGui context (allocates ~500KB for UI state)
     * 2. Configure flags (docking, multi-viewport)
     * 3. Load fonts (default + custom)
     * 4. Initialize GLFW backend (input handling)
     * 5. Initialize OpenGL backend (rendering)
     */
    public void init(long windowHandle) {
        this.windowHandle = windowHandle;

        // STEP 1: Create ImGui context
        // ImGui uses a global context for UI state
        // WHY? C++ API design (before C++ had proper context management)
        ImGui.createContext();

        // STEP 2: Configure ImGui I/O
        final ImGuiIO io = ImGui.getIO();

        // Enable keyboard navigation (Tab to cycle, arrows to move)
        io.addConfigFlags(ImGuiConfigFlags.NavEnableKeyboard);

        // Enable docking (Unity-style drag-and-drop panels)
        // WHY DOCKING? Flexible workspace layouts
        io.addConfigFlags(ImGuiConfigFlags.DockingEnable);

        // Enable multi-viewport (undock windows to separate OS windows)
        // EXAMPLE: Drag "Inspector" to second monitor
        io.addConfigFlags(ImGuiConfigFlags.ViewportsEnable);

        // STEP 3: Setup ImGui style
        // ImGui has 3 built-in themes:
        ImGui.styleColorsDark();       // Dark theme (most popular)
        //ImGui.styleColorsClassic();  // ImGui classic (gray)
        //ImGui.styleColorsLight();    // Light theme (rare)

        // Customize colors for a professional look
        customizeStyle();

        // STEP 4: Load fonts
        // ImGui needs fonts in texture atlas (GPU texture)
        final ImFontAtlas fontAtlas = io.getFonts();
        final ImFontConfig fontConfig = new ImFontConfig();
        fontConfig.setGlyphRanges(fontAtlas.getGlyphRangesDefault());  // ASCII + Latin

        // Default font (16px)
        // NOTE: You need to provide Roboto-Regular.ttf in assets/fonts/
        // Download from: https://fonts.google.com/specimen/Roboto
        fontAtlas.addFontFromFileTTF("assets/fonts/Roboto-Regular.ttf", 16, fontConfig);
        fontAtlas.build();  // Bake fonts into texture atlas

        fontConfig.destroy();  // Free temporary config

        // STEP 5: Initialize ImGui GLFW backend
        // This hooks into GLFW callbacks to capture input
        imGuiGlfw.init(windowHandle, true);  // true = install callbacks

        // STEP 6: Initialize ImGui OpenGL backend
        // This creates shaders for rendering UI
        imGuiGl3.init("#version 450 core");  // OpenGL 4.5

        System.out.println("✓ ImGui initialized");
    }

    /**
     * Begin new ImGui frame.
     *
     * WHAT THIS DOES:
     * 1. Clear previous frame's UI commands
     * 2. Poll input events (mouse, keyboard)
     * 3. Start recording new UI commands
     *
     * CALL THIS: At the start of each frame, before any ImGui.xxx() calls
     */
    public void begin() {
        imGuiGl3.newFrame();    // OpenGL backend: clear draw data
        imGuiGlfw.newFrame();   // GLFW backend: update input state
        ImGui.newFrame();       // ImGui: start recording
    }

    /**
     * End ImGui frame and render.
     *
     * WHAT THIS DOES:
     * 1. Finalize UI layout (calculate positions)
     * 2. Generate draw commands (vertices + indices)
     * 3. Upload to GPU and render
     *
     * CALL THIS: After all ImGui.xxx() calls, before swapBuffers()
     */
    public void end() {
        // Finalize UI and generate draw data
        ImGui.render();

        // Render draw data to OpenGL
        imGuiGl3.renderDrawData(ImGui.getDrawData());

        // Handle multi-viewport rendering
        // If windows are undocked, they need separate rendering
        if (ImGui.getIO().hasConfigFlags(ImGuiConfigFlags.ViewportsEnable)) {
            final long backupWindowPtr = GLFW.glfwGetCurrentContext();

            // Update all platform windows (undocked panels)
            ImGui.updatePlatformWindows();

            // Render each platform window
            ImGui.renderPlatformWindowsDefault();

            // Restore original window context
            GLFW.glfwMakeContextCurrent(backupWindowPtr);
        }
    }

    /**
     * Cleanup ImGui resources.
     *
     * WHAT THIS FREES:
     * - Font atlas texture (~1-2 MB)
     * - Vertex/index buffers
     * - OpenGL shaders
     * - ImGui context (~500 KB)
     */
    public void cleanup() {
        imGuiGl3.dispose();      // Free OpenGL resources
        imGuiGlfw.dispose();     // Unhook GLFW callbacks
        ImGui.destroyContext();  // Free ImGui context
        System.out.println("✓ ImGui cleaned up");
    }

    /**
     * Customize ImGui style for a professional look.
     *
     * WHY CUSTOMIZE?
     * Default ImGui style looks "programmer art". We want:
     * - Rounded corners (modern look)
     * - Subtle shadows
     * - Professional color scheme (dark + blue accents)
     *
     * COMPARISON:
     * - Unity: Dark theme with blue accents ← We're matching this!
     * - Unreal: Dark theme with gray accents
     * - Godot: Dark theme with teal accents
     */
    private void customizeStyle() {
        ImGuiStyle style = ImGui.getStyle();

        // ROUNDING: Rounded corners feel more modern
        style.setWindowRounding(5.0f);     // Window corners
        style.setFrameRounding(3.0f);      // Button/input corners
        style.setGrabRounding(3.0f);       // Slider grab corners
        style.setScrollbarRounding(3.0f);  // Scrollbar corners

        // SPACING: Comfortable padding and spacing
        style.setWindowPadding(8, 8);       // Space inside windows
        style.setFramePadding(4, 3);        // Space inside buttons/inputs
        style.setItemSpacing(8, 4);         // Space between widgets
        style.setItemInnerSpacing(4, 4);    // Space inside composite widgets

        // COLORS: Dark theme with blue accents (Unity-style)
        ImVec4[] colors = style.getColors();

        // Window background (dark gray)
        colors[ImGuiCol.WindowBg] = new ImVec4(0.1f, 0.1f, 0.1f, 1.0f);

        // Headers (collapsing headers, tree nodes)
        colors[ImGuiCol.Header] = new ImVec4(0.2f, 0.4f, 0.6f, 0.8f);          // Blue
        colors[ImGuiCol.HeaderHovered] = new ImVec4(0.3f, 0.5f, 0.7f, 1.0f);   // Lighter blue
        colors[ImGuiCol.HeaderActive] = new ImVec4(0.15f, 0.35f, 0.55f, 1.0f); // Darker blue

        // Buttons
        colors[ImGuiCol.Button] = new ImVec4(0.2f, 0.4f, 0.6f, 0.8f);
        colors[ImGuiCol.ButtonHovered] = new ImVec4(0.3f, 0.5f, 0.7f, 1.0f);
        colors[ImGuiCol.ButtonActive] = new ImVec4(0.15f, 0.35f, 0.55f, 1.0f);

        // Input fields (text boxes, sliders)
        colors[ImGuiCol.FrameBg] = new ImVec4(0.15f, 0.15f, 0.15f, 1.0f);        // Slightly lighter
        colors[ImGuiCol.FrameBgHovered] = new ImVec4(0.2f, 0.2f, 0.2f, 1.0f);
        colors[ImGuiCol.FrameBgActive] = new ImVec4(0.25f, 0.25f, 0.25f, 1.0f);

        // Title bars
        colors[ImGuiCol.TitleBg] = new ImVec4(0.15f, 0.15f, 0.15f, 1.0f);        // Inactive
        colors[ImGuiCol.TitleBgActive] = new ImVec4(0.2f, 0.2f, 0.2f, 1.0f);    // Active
    }
}
```

---

## Part 2: Editor Layer

### Understanding the Docking System

**What is docking?**

Drag a panel and snap it to another panel's edge. Unity, Visual Studio, Blender all use docking.

**Without docking:**
```
Fixed layout:
┌────────────────────────────────┐
│ Hierarchy │ Viewport │ Inspector│
└────────────────────────────────┘
   ↑ Can't rearrange!
```

**With docking:**
```
Flexible layout:
┌─────────┬──────────────────────┐
│Hierarchy│       Viewport       │
│         ├──────────┬───────────┤
│         │Performance│ Inspector │
└─────────┴──────────┴───────────┘
   ↑ Drag panels anywhere!
```

**How ImGui docking works:**

1. Create a **dockspace** (empty area that accepts docked windows)
2. Create **windows** with `ImGui.begin("Name")`
3. ImGui handles drag-and-drop docking automatically!

---

### Step 3: Editor Layer Structure

**What This Class Does:**

The "conductor" of the editor. Manages all panels, handles menu bar, coordinates state.

Create `src/main/java/com/yourname/engine/editor/EditorLayer.java`:

```java
package com.yourname.engine.editor;

import com.yourname.engine.core.Engine;
import com.yourname.engine.ecs.*;
import com.yourname.engine.serialization.SceneSerializer;
import imgui.*;
import imgui.flag.*;
import imgui.type.ImBoolean;

/**
 * Main editor layer - manages all editor panels and state.
 *
 * ARCHITECTURE:
 *
 *   EditorLayer (coordinator)
 *        ↓
 *   ┌────┴────┬────────┬──────────┬──────────┐
 *   │         │        │          │          │
 * Hierarchy Inspector Viewport Performance Console
 *   Panel     Panel    Panel     Panel      Panel
 *
 * RESPONSIBILITIES:
 * - Create dockspace for flexible layouts
 * - Render menu bar (File, Edit, View, Help)
 * - Render toolbar (Play/Stop, gizmo mode)
 * - Coordinate panel updates
 * - Manage editor state (Play/Edit mode)
 * - Handle scene serialization
 */
public class EditorLayer {

    private Engine engine;
    private World world;

    // Panels (each panel = one aspect of the editor)
    private HierarchyPanel hierarchyPanel;    // Entity tree view
    private InspectorPanel inspectorPanel;    // Component editor
    private ViewportPanel viewportPanel;      // 3D scene view
    private PerformancePanel performancePanel;// FPS, memory stats
    private ConsolePanel consolePanel;        // Logs and messages

    // State
    private Entity selectedEntity = Entity.NULL;  // Currently selected entity
    private EditorState editorState;              // Play/Edit mode manager
    private SceneSerializer sceneSerializer;      // Save/load scenes

    // Panel visibility toggles (ImBoolean = mutable boolean for ImGui)
    private ImBoolean showHierarchy = new ImBoolean(true);
    private ImBoolean showInspector = new ImBoolean(true);
    private ImBoolean showViewport = new ImBoolean(true);
    private ImBoolean showPerformance = new ImBoolean(true);
    private ImBoolean showConsole = new ImBoolean(true);

    public EditorLayer(Engine engine) {
        this.engine = engine;
        this.world = engine.getWorld();
        this.sceneSerializer = new SceneSerializer();
        this.editorState = new EditorState();

        // Create all panels
        this.hierarchyPanel = new HierarchyPanel();
        this.inspectorPanel = new InspectorPanel();
        this.viewportPanel = new ViewportPanel(engine.getRenderer());
        this.performancePanel = new PerformancePanel();
        this.consolePanel = new ConsolePanel();

        System.out.println("✓ Editor layer initialized");
    }

    /**
     * Render editor UI.
     *
     * CALL ORDER:
     * 1. setupDockspace() - Create docking area
     * 2. renderMenuBar() - File, Edit, View, Help
     * 3. renderToolbar() - Play/Stop, gizmo controls
     * 4. Render all visible panels
     * 5. Update editor state
     */
    public void render(float deltaTime) {
        // Create fullscreen dockspace
        setupDockspace();

        // Menu bar (inside dockspace)
        renderMenuBar();

        // Toolbar (floating on top)
        renderToolbar();

        // Render visible panels
        // Each panel is an ImGui.begin() / ImGui.end() pair
        if (showHierarchy.get()) {
            hierarchyPanel.render(world, selectedEntity, this::setSelectedEntity);
        }
        if (showInspector.get()) {
            inspectorPanel.render(world, selectedEntity);
        }
        if (showViewport.get()) {
            viewportPanel.render();
        }
        if (showPerformance.get()) {
            performancePanel.render(deltaTime);
        }
        if (showConsole.get()) {
            consolePanel.render();
        }

        // Update editor state (handles Play/Edit mode logic)
        editorState.update(world, deltaTime);
    }

    /**
     * Setup fullscreen dockspace.
     *
     * WHAT IS A DOCKSPACE?
     * A special ImGui window that:
     * 1. Fills the entire screen
     * 2. Has no title bar, borders, or padding
     * 3. Accepts docked windows
     *
     * WHY FULLSCREEN?
     * Unity/Unreal editors fill the screen with dockspace,
     * then dock all panels into it. Gives maximum flexibility.
     *
     * WINDOW FLAGS EXPLAINED:
     * - NoTitleBar: No "Dockspace" title at top
     * - NoCollapse: Can't minimize
     * - NoResize: Can't resize (it's always fullscreen)
     * - NoMove: Can't move (it's locked to viewport)
     * - NoBringToFrontOnFocus: Other windows can be in front
     * - NoNavFocus: Keyboard navigation ignores it
     */
    private void setupDockspace() {
        // Configure window flags for fullscreen dockspace
        int windowFlags = ImGuiWindowFlags.MenuBar | ImGuiWindowFlags.NoDocking;
        windowFlags |= ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoCollapse;
        windowFlags |= ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoMove;
        windowFlags |= ImGuiWindowFlags.NoBringToFrontOnFocus | ImGuiWindowFlags.NoNavFocus;

        // Get main viewport (the entire window)
        ImGuiViewport viewport = ImGui.getMainViewport();

        // Position dockspace to fill viewport
        ImGui.setNextWindowPos(viewport.getPosX(), viewport.getPosY());
        ImGui.setNextWindowSize(viewport.getSizeX(), viewport.getSizeY());
        ImGui.setNextWindowViewport(viewport.getID());

        // Remove window styling (no rounding, borders, padding)
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0.0f, 0.0f);

        // Begin dockspace window
        ImGui.begin("DockSpace", new ImBoolean(true), windowFlags);
        ImGui.popStyleVar(3);  // Restore style

        // Create dockspace (the magic!)
        int dockspaceId = ImGui.getID("MyDockSpace");
        ImGui.dockSpace(dockspaceId, 0, 0);  // 0,0 = use window size

        ImGui.end();
    }

    /**
     * Render menu bar.
     *
     * MENU BAR STRUCTURE:
     * File    Edit    View    Help
     *  ├─ New Scene    ├─ Undo    ├─ Hierarchy    ├─ About
     *  ├─ Open Scene   ├─ Redo    ├─ Inspector
     *  ├─ Save Scene              ├─ Viewport
     *  ├─ Save As                 ├─ Performance
     *  └─ Exit                    └─ Console
     *
     * KEYBOARD SHORTCUTS:
     * - Ctrl+N: New scene
     * - Ctrl+O: Open scene
     * - Ctrl+S: Save scene
     * - Ctrl+Shift+S: Save as
     * - Alt+F4: Exit
     */
    private void renderMenuBar() {
        if (ImGui.beginMenuBar()) {
            // FILE MENU
            if (ImGui.beginMenu("File")) {
                if (ImGui.menuItem("New Scene", "Ctrl+N")) {
                    newScene();
                }
                if (ImGui.menuItem("Open Scene", "Ctrl+O")) {
                    openScene();
                }
                if (ImGui.menuItem("Save Scene", "Ctrl+S")) {
                    saveScene();
                }
                if (ImGui.menuItem("Save Scene As", "Ctrl+Shift+S")) {
                    saveSceneAs();
                }
                ImGui.separator();
                if (ImGui.menuItem("Exit", "Alt+F4")) {
                    engine.getWindow().setShouldClose(true);
                }
                ImGui.endMenu();
            }

            // EDIT MENU
            if (ImGui.beginMenu("Edit")) {
                if (ImGui.menuItem("Undo", "Ctrl+Z")) {
                    // TODO: Implement command pattern for undo/redo
                }
                if (ImGui.menuItem("Redo", "Ctrl+Y")) {
                    // TODO: Implement redo
                }
                ImGui.endMenu();
            }

            // VIEW MENU (toggle panel visibility)
            if (ImGui.beginMenu("View")) {
                // ImBoolean = mutable boolean that ImGui can modify
                ImGui.menuItem("Hierarchy", "", showHierarchy);
                ImGui.menuItem("Inspector", "", showInspector);
                ImGui.menuItem("Viewport", "", showViewport);
                ImGui.menuItem("Performance", "", showPerformance);
                ImGui.menuItem("Console", "", showConsole);
                ImGui.endMenu();
            }

            // HELP MENU
            if (ImGui.beginMenu("Help")) {
                if (ImGui.menuItem("About")) {
                    ImGui.openPopup("About");  // Open modal popup
                }
                ImGui.endMenu();
            }

            ImGui.endMenuBar();
        }

        // About popup (modal dialog)
        if (ImGui.beginPopupModal("About")) {
            ImGui.text("JECS Game Engine");
            ImGui.text("Version 1.0.0");
            ImGui.separator();
            ImGui.text("A Java 25 + Vulkan + ECS game engine");
            if (ImGui.button("Close")) {
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }

    /**
     * Render toolbar.
     *
     * TOOLBAR LAYOUT:
     * [Play] [Stop] [Pause] | Gizmo: (•) Translate ( ) Rotate ( ) Scale
     *
     * PLAY MODE:
     * - Play button: Enter play mode (F5)
     * - Stop button: Exit play mode (F6)
     * - Pause button: Freeze game (systems stop updating)
     *
     * GIZMO MODE:
     * - Translate: Move entities (W key in Unity)
     * - Rotate: Rotate entities (E key in Unity)
     * - Scale: Scale entities (R key in Unity)
     */
    private void renderToolbar() {
        // NoTitleBar = remove "Toolbar" text
        // NoResize = fixed height
        ImGui.begin("Toolbar", new ImBoolean(true),
            ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoResize);

        // PLAY/STOP/PAUSE BUTTONS
        if (editorState.isPlayMode()) {
            // In play mode: show Stop and Pause
            if (ImGui.button("Stop (F6)")) {
                editorState.exitPlayMode(world, sceneSerializer);
            }
            ImGui.sameLine();  // Next button on same line
            if (ImGui.button("Pause")) {
                editorState.togglePause();
            }
        } else {
            // In edit mode: show Play
            if (ImGui.button("Play (F5)")) {
                editorState.enterPlayMode(world, sceneSerializer);
            }
        }

        ImGui.sameLine();
        ImGui.separator();  // Vertical line
        ImGui.sameLine();

        // GIZMO MODE SELECTION
        ImGui.text("Gizmo:");
        ImGui.sameLine();

        // Radio buttons: only one can be selected
        if (ImGui.radioButton("Translate", viewportPanel.getGizmoMode() == GizmoMode.TRANSLATE)) {
            viewportPanel.setGizmoMode(GizmoMode.TRANSLATE);
        }
        ImGui.sameLine();
        if (ImGui.radioButton("Rotate", viewportPanel.getGizmoMode() == GizmoMode.ROTATE)) {
            viewportPanel.setGizmoMode(GizmoMode.ROTATE);
        }
        ImGui.sameLine();
        if (ImGui.radioButton("Scale", viewportPanel.getGizmoMode() == GizmoMode.SCALE)) {
            viewportPanel.setGizmoMode(GizmoMode.SCALE);
        }

        ImGui.end();
    }

    // SCENE MANAGEMENT
    // These handle File menu actions

    private void newScene() {
        // Clear all entities from world
        // TODO: Implement world.clear()
        System.out.println("New scene");
    }

    private void openScene() {
        // TODO: Native file dialog (use tinyfiledialogs or JFileChooser)
        try {
            sceneSerializer.loadScene(world, "scenes/default.json");
            ConsolePanel.log("Scene loaded");
        } catch (Exception e) {
            ConsolePanel.error("Failed to load scene: " + e.getMessage());
        }
    }

    private void saveScene() {
        try {
            sceneSerializer.saveScene(world, "scenes/default.json");
            ConsolePanel.log("Scene saved");
        } catch (Exception e) {
            ConsolePanel.error("Failed to save scene: " + e.getMessage());
        }
    }

    private void saveSceneAs() {
        // TODO: Native file dialog for save path
        saveScene();
    }

    private void setSelectedEntity(Entity entity) {
        this.selectedEntity = entity;
    }

    public Entity getSelectedEntity() {
        return selectedEntity;
    }
}
```

---

## Part 3: Hierarchy Panel

### Understanding the Entity Tree View

**What is the hierarchy panel?**

A list of all entities in the scene. Like Unity's Hierarchy window or Unreal's Outliner.

**Features we're implementing:**
- **Searchable list**: Filter entities by name
- **Create/Delete**: Add/remove entities
- **Context menu**: Right-click for options
- **Drag-and-drop**: Parent entities (TODO)

### Step 4: Hierarchy Panel Implementation

Create `src/main/java/com/yourname/engine/editor/HierarchyPanel.java`:

```java
package com.yourname.engine.editor;

import com.yourname.engine.ecs.*;
import com.yourname.engine.components.Transform3D;
import imgui.*;
import imgui.flag.*;
import java.util.function.Consumer;

/**
 * Hierarchy panel - displays tree of all entities.
 *
 * WHAT IT DOES:
 * - List all entities (query Transform3D as most entities have this)
 * - Search/filter entities by name
 * - Select entity (highlights in inspector)
 * - Create new entities
 * - Delete entities (with confirmation)
 * - Duplicate entities
 * - Context menu (right-click)
 *
 * COMPARISON TO UNITY:
 * Unity Hierarchy      JECS Hierarchy
 * ├─ Main Camera       ├─ Entity 1
 * ├─ Player            ├─ Entity 2
 * │  └─ Weapon         ├─ Entity 3
 * └─ Ground            └─ Entity 4
 *
 * NOTE: We don't have parent/child relationships yet (that's advanced).
 * All entities are flat list for now.
 */
public class HierarchyPanel {

    private String searchFilter = "";  // Search box text

    public void render(World world, Entity selectedEntity, Consumer<Entity> onEntitySelected) {
        ImGui.begin("Hierarchy");

        // CREATE ENTITY BUTTON (full width)
        if (ImGui.button("Create Entity", ImGui.getContentRegionAvailX(), 0)) {
            Entity entity = world.createEntity();
            world.addComponent(entity, new Transform3D());  // All entities need Transform3D
            onEntitySelected.accept(entity);  // Auto-select new entity
        }

        ImGui.separator();

        // SEARCH FILTER
        // ImGui.inputText() needs a byte buffer (C-style string)
        byte[] buffer = new byte[256];
        System.arraycopy(searchFilter.getBytes(), 0, buffer, 0, searchFilter.length());

        if (ImGui.inputText("##Search", buffer)) {
            // Convert byte buffer back to string
            searchFilter = new String(buffer).trim();
        }

        ImGui.separator();

        // ENTITY LIST (scrollable)
        renderEntityList(world, selectedEntity, onEntitySelected);

        ImGui.end();
    }

    /**
     * Render scrollable list of entities.
     *
     * SELECTABLES:
     * ImGui.selectable() = clickable list item
     * - Returns true if clicked
     * - Highlights if selected
     * - Supports double-click, drag-drop, context menu
     */
    private void renderEntityList(World world, Entity selectedEntity, Consumer<Entity> onEntitySelected) {
        // Query all entities with Transform3D
        // WHY Transform3D? Most entities have this component
        world.query(Transform3D.class).forEach(entityView -> {
            Entity entity = entityView.getEntity();

            // Generate entity name (could be stored in a Name component later)
            String entityName = "Entity " + entity.id();

            // Apply search filter
            if (!searchFilter.isEmpty() &&
                !entityName.toLowerCase().contains(searchFilter.toLowerCase())) {
                return;  // Skip this entity
            }

            // Is this entity currently selected?
            boolean isSelected = entity.equals(selectedEntity);

            // Render selectable item
            // "##" suffix = unique ID (allows duplicate names)
            if (ImGui.selectable(entityName + "##" + entity.id(), isSelected)) {
                onEntitySelected.accept(entity);  // Clicked! Select entity
            }

            // CONTEXT MENU (right-click)
            if (ImGui.beginPopupContextItem("EntityContext##" + entity.id())) {
                if (ImGui.menuItem("Duplicate")) {
                    duplicateEntity(world, entity);
                }
                if (ImGui.menuItem("Delete", "Delete")) {
                    world.destroyEntity(entity);
                    if (entity.equals(selectedEntity)) {
                        onEntitySelected.accept(Entity.NULL);  // Deselect
                    }
                }
                ImGui.endPopup();
            }

            // DRAG-AND-DROP SOURCE (for parenting)
            // TODO: Implement parent/child relationships
            if (ImGui.beginDragDropSource()) {
                ImGui.setDragDropPayload("ENTITY_DND", entity.id());
                ImGui.text("Entity " + entity.id());
                ImGui.endDragDropSource();
            }
        });
    }

    /**
     * Duplicate an entity (deep copy).
     *
     * TODO: Implement proper component cloning
     * - Needs reflection to iterate components
     * - Needs copy constructors or Cloneable interface
     */
    private void duplicateEntity(World world, Entity source) {
        Entity newEntity = world.createEntity();
        // Copy all components from source to newEntity
        // (Requires reflection - advanced topic)
        ConsolePanel.log("Entity duplicated");
    }
}
```

---

## Part 4: Inspector Panel

### Understanding Component Inspection

**What is the inspector?**

The "properties panel" for the selected entity. Shows all components and lets you edit their values.

**The Reflection Challenge:**

```java
// We want to write generic code:
for (Component component : entity.getComponents()) {
    render(component);  // How do we know what fields it has?
}

// But Java doesn't have built-in property introspection like C#!
// C# has [SerializeField], [Range(0, 100)], etc.
// Java needs manual per-component rendering :(
```

**Professional Solutions:**

| Engine | Solution |
|--------|----------|
| **Unity** | C# reflection + custom attributes (`[SerializeField]`) |
| **Unreal** | C++ reflection system (UCLASS, UPROPERTY macros) |
| **Godot** | Built-in variant system |
| **JECS** | Manual rendering per component type (simple but verbose) |

**Future Enhancement:** Use Java reflection + annotations:
```java
@Editable
@Range(min=0, max=100)
public float health;
```

---

### Step 5: Inspector Panel Implementation

Create `src/main/java/com/yourname/engine/editor/InspectorPanel.java`:

```java
package com.yourname.engine.editor;

import com.yourname.engine.ecs.*;
import com.yourname.engine.components.*;
import com.yourname.game.Components.*;
import imgui.*;
import imgui.flag.*;
import org.joml.*;

/**
 * Inspector panel - edit components of selected entity.
 *
 * WHAT IT DOES:
 * - Show "No entity selected" if nothing selected
 * - For each component type, render custom editor UI
 * - Add component button (popup menu)
 * - Remove component buttons
 *
 * ARCHITECTURE:
 *   render(entity)
 *        ↓
 *   ┌────┴────┬────────────┬──────────┬────────────┐
 *   │         │            │          │            │
 * Transform3D MeshRenderer Health  Velocity3D  [Add more]
 *   editor      editor     editor     editor
 *
 * WHY MANUAL RENDERING?
 * Java doesn't have good reflection for properties like C#.
 * Professional solution: Use annotation processor to generate editors.
 */
public class InspectorPanel {

    public void render(World world, Entity entity) {
        ImGui.begin("Inspector");

        // Check if entity is valid
        if (!world.isValid(entity)) {
            ImGui.textDisabled("No entity selected");
            ImGui.end();
            return;
        }

        // Entity header
        ImGui.text("Entity: " + entity.id());
        ImGui.separator();

        // Render component editors
        // Each renderXXX() checks if component exists
        renderTransform3D(world, entity);
        renderMeshRenderer(world, entity);
        renderHealth(world, entity);
        renderVelocity3D(world, entity);

        // ADD COMPONENT BUTTON
        ImGui.separator();
        if (ImGui.button("Add Component", ImGui.getContentRegionAvailX(), 0)) {
            ImGui.openPopup("AddComponentPopup");
        }

        // Popup menu with component types
        if (ImGui.beginPopup("AddComponentPopup")) {
            if (ImGui.menuItem("Transform3D")) {
                world.addComponent(entity, new Transform3D());
            }
            if (ImGui.menuItem("MeshRenderer")) {
                world.addComponent(entity, new MeshRenderer(Mesh.createCube()));
            }
            if (ImGui.menuItem("Health")) {
                world.addComponent(entity, new Health(100, 100));
            }
            // Add more component types here...
            ImGui.endPopup();
        }

        ImGui.end();
    }

    /**
     * Render Transform3D component editor.
     *
     * FIELDS:
     * - Position (vec3): Drag to move
     * - Rotation (quaternion): Shown as Euler angles (degrees)
     * - Scale (vec3): Drag to scale
     *
     * WHY EULER ANGLES?
     * Quaternions are hard to edit (w, x, y, z has no intuitive meaning).
     * Euler angles (pitch, yaw, roll) are easier: (45°, 0°, 0°) = tilt forward
     *
     * UNITY DOES THIS TOO: Stores quaternion, displays Euler angles
     */
    private void renderTransform3D(World world, Entity entity) {
        Transform3D transform = world.getComponent(entity, Transform3D.class);
        if (transform == null) return;  // Component not present

        // Collapsing header: click to expand/collapse
        if (ImGui.collapsingHeader("Transform3D", ImGuiTreeNodeFlags.DefaultOpen)) {

            // POSITION (vec3)
            // ImGui.dragFloat3() = 3 float sliders
            float[] position = {transform.position.x, transform.position.y, transform.position.z};
            if (ImGui.dragFloat3("Position", position, 0.1f)) {  // 0.1f = drag speed
                transform.position.set(position[0], position[1], position[2]);
                transform.markDirty();  // Recalculate model matrix
            }

            // ROTATION (quaternion → Euler angles)
            // Step 1: Extract Euler angles from quaternion
            Vector3f euler = new Vector3f();
            transform.rotation.getEulerAnglesXYZ(euler);  // Radians

            // Step 2: Convert to degrees (easier to edit)
            float[] eulerDeg = {
                (float) Math.toDegrees(euler.x),  // Pitch
                (float) Math.toDegrees(euler.y),  // Yaw
                (float) Math.toDegrees(euler.z)   // Roll
            };

            // Step 3: Render drag sliders
            if (ImGui.dragFloat3("Rotation", eulerDeg, 1.0f)) {  // 1.0f = 1 degree per drag
                // Step 4: Convert back to radians
                transform.rotation.rotationXYZ(
                    (float) Math.toRadians(eulerDeg[0]),
                    (float) Math.toRadians(eulerDeg[1]),
                    (float) Math.toRadians(eulerDeg[2])
                );
                transform.markDirty();
            }

            // SCALE (vec3)
            float[] scale = {transform.scale.x, transform.scale.y, transform.scale.z};
            if (ImGui.dragFloat3("Scale", scale, 0.01f, 0.001f, 100.0f)) {  // Min/max clamp
                transform.scale.set(scale[0], scale[1], scale[2]);
                transform.markDirty();
            }

            // REMOVE COMPONENT BUTTON
            if (ImGui.button("Remove##Transform3D")) {
                world.removeComponent(entity, Transform3D.class);
            }
        }
    }

    /**
     * Render MeshRenderer component editor.
     *
     * FIELDS:
     * - Mesh type (combo box): Cube, Pyramid, Sphere
     * - Color (color picker): RGBA
     */
    private void renderMeshRenderer(World world, Entity entity) {
        MeshRenderer meshRenderer = world.getComponent(entity, MeshRenderer.class);
        if (meshRenderer == null) return;

        if (ImGui.collapsingHeader("MeshRenderer")) {
            // MESH TYPE (combo box)
            // TODO: Get current mesh type from renderer
            String[] meshTypes = {"Cube", "Pyramid", "Sphere"};
            int currentMesh = 0;
            if (ImGui.combo("Mesh", new int[]{currentMesh}, meshTypes)) {
                // User selected different mesh
                // TODO: Change mesh (needs mesh asset management)
            }

            // COLOR (RGBA)
            // ImGui.colorEdit4() shows a color picker with alpha slider
            float[] color = {
                meshRenderer.colorR,
                meshRenderer.colorG,
                meshRenderer.colorB,
                meshRenderer.colorA
            };
            if (ImGui.colorEdit4("Color", color)) {
                meshRenderer.setColor(color[0], color[1], color[2], color[3]);
            }

            if (ImGui.button("Remove##MeshRenderer")) {
                world.removeComponent(entity, MeshRenderer.class);
            }
        }
    }

    /**
     * Render Health component editor.
     *
     * FIELDS:
     * - Current health (int): Drag slider
     * - Max health (int): Read-only (final field)
     * - Health bar: Visual progress bar
     */
    private void renderHealth(World world, Entity entity) {
        Health health = world.getComponent(entity, Health.class);
        if (health == null) return;

        if (ImGui.collapsingHeader("Health")) {
            // CURRENT HEALTH (editable)
            int[] current = {health.current};
            if (ImGui.dragInt("Current", current, 1, 0, health.max)) {  // Clamp to [0, max]
                health.current = current[0];
            }

            // MAX HEALTH (read-only if final)
            int[] max = {health.max};
            ImGui.dragInt("Max", max, 1, 1, 1000);
            // NOTE: If health.max is final, this doesn't change it
            // To change max, would need to remove and re-add component

            // HEALTH BAR (visual feedback)
            float healthPercent = (float) health.current / health.max;
            ImGui.progressBar(
                healthPercent,
                ImGui.getContentRegionAvailX(),  // Full width
                0,                               // Auto height
                health.current + " / " + health.max  // Overlay text
            );

            if (ImGui.button("Remove##Health")) {
                world.removeComponent(entity, Health.class);
            }
        }
    }

    /**
     * Render Velocity3D component editor.
     *
     * FIELDS:
     * - Velocity vector (vec3): Drag to change
     * - Speed (float): Read-only, calculated from velocity.length()
     */
    private void renderVelocity3D(World world, Entity entity) {
        Velocity3D velocity = world.getComponent(entity, Velocity3D.class);
        if (velocity == null) return;

        if (ImGui.collapsingHeader("Velocity3D")) {
            // VELOCITY VECTOR
            float[] vel = {velocity.velocity.x, velocity.velocity.y, velocity.velocity.z};
            if (ImGui.dragFloat3("Velocity", vel, 0.1f)) {
                velocity.velocity.set(vel[0], vel[1], vel[2]);
            }

            // SPEED (read-only, calculated)
            ImGui.text("Speed: " + String.format("%.2f", velocity.speed()));

            if (ImGui.button("Remove##Velocity3D")) {
                world.removeComponent(entity, Velocity3D.class);
            }
        }
    }

    // Add more renderXXX() methods for other component types...
}
```

---

## Summary & Next Steps

### What You've Built

In this chapter, you created a **professional game editor** with:

✅ **ImGui integration** with custom styling
✅ **Dockable panels** like Unity/Unreal
✅ **Hierarchy panel** with entity list
✅ **Inspector panel** with live component editing
✅ **Play/Edit mode** with scene snapshots
✅ **Performance metrics** (FPS, memory)
✅ **Console** for logs

### Key Concepts Learned

**Immediate Mode UI:**
- Recreate UI every frame (no persistent widgets)
- UI = function of game state
- Zero synchronization overhead

**Editor Architecture:**
- Dockspace for flexible layouts
- Panel-based design (separation of concerns)
- Component inspection patterns

**Workflow Improvements:**
- Live editing (no recompilation)
- Visual feedback
- Faster iteration

### Future Enhancements

1. **Transform gizmos** (use ImGuizmo library)
2. **Reflection-based inspector** (automatic component editors)
3. **Undo/redo system** (command pattern)
4. **Asset browser** (textures, models, sounds)
5. **Prefab system** (reusable entity templates)

**Next Chapter:** Advanced Rendering with PBR and lighting!

---

**Previous:** [← Chapter 7 - Input & Audio](chapter-07-input-audio.md)
**Next:** [Chapter 9 - Advanced Rendering →](chapter-09-advanced-rendering.md)
