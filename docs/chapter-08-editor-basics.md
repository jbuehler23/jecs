# Chapter 8: ImGui Editor
## Building a Professional Game Editor

**What You'll Learn:**
- Dear ImGui integration with LWJGL
- Editor architecture (panels, docking)
- Entity hierarchy panel
- Component inspector with reflection
- Viewport rendering
- Play/pause mode switching

**What You'll Build:**
A Unity-like editor for your game engine!

**Estimated Time:** 3-4 hours

---

## Introduction: Why an Editor?

**Without editor:** Edit code → Compile → Run → Test → Repeat (slow!)

**With editor:** Drag entities → Edit values → Play → Instant feedback (fast!)

Editors enable:
- **Rapid iteration**: Test ideas quickly
- **Non-programmer workflow**: Designers don't need code
- **Visual debugging**: See game state in real-time
- **Content creation**: Build levels, tweak parameters

---

## Architecture

### Editor Layers

```
EditorLayer (owns all panels)
├── HierarchyPanel (entity tree)
├── InspectorPanel (component editor)
├── ViewportPanel (scene rendering)
├── ConsolePanel (logs)
└── AssetBrowserPanel (project files)
```

### Play/Edit Modes

```
Edit Mode:
- Modify entities
- Save changes
- No game logic

Play Mode:
- Snapshot world state
- Run game systems
- Restore on stop
```

---

## ImGui Integration

### Setup

```java
public class ImGuiLayer {
    private long imguiContext;

    public void init(Window window) {
        imguiContext = ImGui.createContext();
        ImGui.setCurrentContext(imguiContext);

        ImGuiIO io = ImGui.getIO();
        io.setConfigFlags(ImGuiConfigFlags.NavEnableKeyboard |
                          ImGuiConfigFlags.DockingEnable |
                          ImGuiConfigFlags.ViewportsEnable);

        // Setup GLFW callbacks for ImGui
        ImGuiImplGlfw.init(window.getHandle(), true);

        // Setup Vulkan renderer for ImGui
        ImGuiImplVulkan.init(/* Vulkan params */);

        // Load fonts
        io.getFonts().addFontFromFileTTF("fonts/Roboto-Regular.ttf", 16);
    }

    public void beginFrame() {
        ImGuiImplVulkan.newFrame();
        ImGuiImplGlfw.newFrame();
        ImGui.newFrame();
    }

    public void endFrame() {
        ImGui.render();
        ImGuiImplVulkan.renderDrawData(ImGui.getDrawData());

        if (ImGui.getIO().hasConfigFlags(ImGuiConfigFlags.ViewportsEnable)) {
            ImGui.updatePlatformWindows();
            ImGui.renderPlatformWindowsDefault();
        }
    }
}
```

### Main Editor Window

```java
public class EditorLayer {
    private HierarchyPanel hierarchyPanel;
    private InspectorPanel inspectorPanel;
    private ViewportPanel viewportPanel;

    private World world;
    private Entity selectedEntity = Entity.NULL;

    public void onImGuiRender() {
        // Dockspace
        ImGui.dockSpaceOverViewport(ImGui.getMainViewport());

        // Menu bar
        if (ImGui.beginMainMenuBar()) {
            if (ImGui.beginMenu("File")) {
                if (ImGui.menuItem("New Scene")) { /* ... */ }
                if (ImGui.menuItem("Open Scene")) { /* ... */ }
                if (ImGui.menuItem("Save Scene")) { /* ... */ }
                ImGui.endMenu();
            }
            if (ImGui.beginMenu("Edit")) {
                if (ImGui.menuItem("Play", "F5")) { /* ... */ }
                if (ImGui.menuItem("Stop", "F6")) { /* ... */ }
                ImGui.endMenu();
            }
            ImGui.endMainMenuBar();
        }

        // Panels
        hierarchyPanel.render(world, selectedEntity);
        inspectorPanel.render(world, selectedEntity);
        viewportPanel.render();
    }
}
```

---

## Hierarchy Panel

```java
public class HierarchyPanel {
    public void render(World world, Entity selected) {
        ImGui.begin("Hierarchy");

        if (ImGui.button("Create Entity")) {
            world.createEntity();
        }

        ImGui.separator();

        // List all entities
        world.query(Name.class).forEach(entityView -> {
            Entity entity = entityView.getEntity();
            Name name = entityView.get(Name.class);

            boolean isSelected = entity.equals(selected);
            if (ImGui.selectable(name.value() + " ##" + entity.id(), isSelected)) {
                // Select entity
                setSelectedEntity(entity);
            }

            // Right-click context menu
            if (ImGui.beginPopupContextItem()) {
                if (ImGui.menuItem("Delete")) {
                    world.destroyEntity(entity);
                }
                if (ImGui.menuItem("Duplicate")) {
                    // Clone entity
                }
                ImGui.endPopup();
            }
        });

        ImGui.end();
    }
}
```

---

## Inspector Panel

```java
public class InspectorPanel {
    public void render(World world, Entity entity) {
        ImGui.begin("Inspector");

        if (!world.isValid(entity)) {
            ImGui.text("No entity selected");
            ImGui.end();
            return;
        }

        ImGui.text("Entity: " + entity.id());
        ImGui.separator();

        // Render each component
        renderComponent(world, entity, Transform3D.class, this::renderTransform3D);
        renderComponent(world, entity, MeshRenderer.class, this::renderMeshRenderer);

        // Add component button
        ImGui.separator();
        if (ImGui.button("Add Component")) {
            ImGui.openPopup("AddComponentPopup");
        }

        if (ImGui.beginPopup("AddComponentPopup")) {
            if (ImGui.menuItem("Transform3D")) {
                world.addComponent(entity, new Transform3D(/* defaults */));
            }
            if (ImGui.menuItem("MeshRenderer")) {
                world.addComponent(entity, new MeshRenderer(/* defaults */));
            }
            ImGui.endPopup();
        }

        ImGui.end();
    }

    private void renderTransform3D(World world, Entity entity, Transform3D transform) {
        float[] position = {transform.position().x, transform.position().y, transform.position().z};
        if (ImGui.dragFloat3("Position", position, 0.1f)) {
            world.addComponent(entity, new Transform3D(
                new Vector3f(position[0], position[1], position[2]),
                transform.rotation(),
                transform.scale()
            ));
        }

        // Rotation, scale...
    }

    private <T extends Component> void renderComponent(World world, Entity entity,
        Class<T> componentClass, TriConsumer<World, Entity, T> renderFunc) {

        T component = world.getComponent(entity, componentClass);
        if (component == null) return;

        if (ImGui.collapsingHeader(componentClass.getSimpleName())) {
            renderFunc.accept(world, entity, component);

            if (ImGui.button("Remove##" + componentClass.getSimpleName())) {
                world.removeComponent(entity, componentClass);
            }
        }
    }
}
```

---

## Viewport Panel

```java
public class ViewportPanel {
    private long framebuffer;    // Render scene to this
    private long textureId;      // ImGui displays this

    public void render() {
        ImGui.begin("Viewport");

        // Get viewport size
        ImVec2 viewportSize = ImGui.getContentRegionAvail();

        // Render scene to framebuffer at viewport size
        renderer.renderToFramebuffer(framebuffer, (int)viewportSize.x, (int)viewportSize.y);

        // Display framebuffer texture in ImGui
        ImGui.image(textureId, viewportSize.x, viewportSize.y);

        // Handle viewport input (mouse picking, camera control)
        if (ImGui.isWindowHovered()) {
            handleViewportInput();
        }

        ImGui.end();
    }

    private void handleViewportInput() {
        // Camera orbit with right mouse button
        if (ImGui.isMouseDown(ImGuiMouseButton.Right)) {
            ImVec2 mouseDelta = ImGui.getMouseDragDelta(ImGuiMouseButton.Right);
            camera.rotate(mouseDelta.x * 0.01f, mouseDelta.y * 0.01f);
            ImGui.resetMouseDragDelta(ImGuiMouseButton.Right);
        }

        // Zoom with scroll wheel
        float scroll = ImGui.getIO().getMouseWheel();
        if (scroll != 0) {
            camera.zoom(scroll * 0.5f);
        }
    }
}
```

---

## Play/Pause System

```java
public class EditorState {
    private enum Mode { EDIT, PLAY }
    private Mode currentMode = Mode.EDIT;

    private String savedScenePath = "temp_editor_scene.json";

    public void enterPlayMode(World world, SceneSerializer serializer) {
        // Save current scene state
        serializer.saveScene(world, savedScenePath);

        // Switch mode
        currentMode = Mode.PLAY;

        // Start game systems
        world.addSystem(new PhysicsSystem());
        world.addSystem(new GameplaySystem());
    }

    public void exitPlayMode(World world, SceneSerializer serializer) {
        // Stop game systems
        world.clearSystems();

        // Restore saved scene
        world.clear();
        serializer.loadScene(world, savedScenePath);

        // Switch mode
        currentMode = Mode.EDIT;
    }

    public boolean isPlayMode() {
        return currentMode == Mode.PLAY;
    }
}
```

### Play/Stop Buttons

```java
// In EditorLayer menu
if (ImGui.menuItem("Play", "F5", false, !editorState.isPlayMode())) {
    editorState.enterPlayMode(world, sceneSerializer);
}

if (ImGui.menuItem("Stop", "F6", false, editorState.isPlayMode())) {
    editorState.exitPlayMode(world, sceneSerializer);
}
```

---

## Example: Complete Editor

```java
public class Application {
    private EditorLayer editorLayer;

    public void run() {
        init();

        while (!window.shouldClose()) {
            // Update
            if (editorState.isPlayMode()) {
                world.update(deltaTime); // Run game systems
            }

            // Render game
            renderer.beginFrame();
            renderer.renderScene(world, camera);
            renderer.endFrame();

            // Render editor UI
            imguiLayer.beginFrame();
            editorLayer.onImGuiRender();
            imguiLayer.endFrame();
        }

        cleanup();
    }
}
```

---

## Exercises

1. Add gizmos (translate/rotate/scale manipulators)
2. Implement drag-and-drop from asset browser to viewport
3. Add undo/redo stack
4. Create custom component editors with annotations
5. Add scene camera vs game camera switching

---

**Previous:** [← Chapter 7 - Input & Audio](chapter-07-input-audio.md)
**Next:** [Chapter 9 - Advanced Rendering →](chapter-09-advanced-rendering.md)
