# Chapter 6: Scene Serialization & Prefabs
## Saving and Loading Your Game World

**What You'll Learn:**
- Scene serialization to JSON format
- Component serialization strategies
- Prefab system for reusable entity templates
- Asset reference management
- Save/load game states
- Scene management for multiple levels

**What You'll Build:**
A complete save/load system that lets you:
- Save entire game scenes to JSON files
- Load scenes and restore all entities
- Create prefabs for enemies, power-ups, etc.
- Build levels using prefabs in JSON

**Estimated Time:** 3-4 hours

**Prerequisites:** Chapters 1-5 completed

---

## Introduction: Why Serialization?

Right now, our game entities are created in code. Every time you run the game, you write:

```java
Entity enemy = world.createEntity();
world.addComponent(enemy, new Transform3D(...));
world.addComponent(enemy, new MeshRenderer(...));
// etc.
```

**Problems:**
- Level design requires coding
- Can't save player progress
- Can't create level editor
- Testing is slow (restart entire game)

**Solution: Serialization**

```json
{
  "name": "Level 1",
  "entities": [
    {
      "name": "Enemy",
      "components": {
        "Transform3D": { "position": [10, 0, 5] },
        "MeshRenderer": { "mesh": "cube", "color": [1, 0, 0, 1] },
        "Health": { "current": 50, "max": 50 }
      }
    }
  ]
}
```

Save this JSON → Load it → Entire level recreated!

---

## Step 1: Component Serialization Interface

Create `src/main/java/com/yourname/engine/serialization/ComponentSerializer.java`:

```java
package com.yourname.engine.serialization;

import com.google.gson.*;
import com.yourname.engine.ecs.Component;

/**
 * Interface for serializing/deserializing components.
 */
public interface ComponentSerializer<T extends Component> {

    /**
     * Serialize component to JSON.
     */
    JsonElement serialize(T component);

    /**
     * Deserialize component from JSON.
     */
    T deserialize(JsonElement json);

    /**
     * Get the component type this serializer handles.
     */
    Class<T> getComponentType();
}
```

---

## Step 2: Serializer Registry

Create `src/main/java/com/yourname/engine/serialization/SerializerRegistry.java`:

```java
package com.yourname.engine.serialization;

import com.yourname.engine.ecs.Component;
import java.util.*;

/**
 * Registry of component serializers.
 */
public class SerializerRegistry {

    private static final Map<Class<? extends Component>, ComponentSerializer<?>> serializers = new HashMap<>();

    /**
     * Register a serializer for a component type.
     */
    public static <T extends Component> void register(ComponentSerializer<T> serializer) {
        serializers.put(serializer.getComponentType(), serializer);
    }

    /**
     * Get serializer for a component type.
     */
    @SuppressWarnings("unchecked")
    public static <T extends Component> ComponentSerializer<T> get(Class<T> componentType) {
        ComponentSerializer<T> serializer = (ComponentSerializer<T>) serializers.get(componentType);
        if (serializer == null) {
            throw new IllegalArgumentException("No serializer registered for: " + componentType.getName());
        }
        return serializer;
    }

    /**
     * Check if a component type has a serializer.
     */
    public static boolean hasSerializer(Class<? extends Component> componentType) {
        return serializers.containsKey(componentType);
    }

    /**
     * Get all registered component types.
     */
    public static Set<Class<? extends Component>> getRegisteredTypes() {
        return serializers.keySet();
    }
}
```

---

## Step 3: Common Serializers

Create `src/main/java/com/yourname/engine/serialization/serializers/Transform3DSerializer.java`:

```java
package com.yourname.engine.serialization.serializers;

import com.google.gson.*;
import com.yourname.engine.components.Transform3D;
import com.yourname.engine.serialization.ComponentSerializer;
import org.joml.*;

/**
 * Serializer for Transform3D component.
 */
public class Transform3DSerializer implements ComponentSerializer<Transform3D> {

    @Override
    public JsonElement serialize(Transform3D transform) {
        JsonObject json = new JsonObject();

        // Position
        JsonArray position = new JsonArray();
        position.add(transform.position.x);
        position.add(transform.position.y);
        position.add(transform.position.z);
        json.add("position", position);

        // Rotation (quaternion)
        JsonArray rotation = new JsonArray();
        rotation.add(transform.rotation.x);
        rotation.add(transform.rotation.y);
        rotation.add(transform.rotation.z);
        rotation.add(transform.rotation.w);
        json.add("rotation", rotation);

        // Scale
        JsonArray scale = new JsonArray();
        scale.add(transform.scale.x);
        scale.add(transform.scale.y);
        scale.add(transform.scale.z);
        json.add("scale", scale);

        return json;
    }

    @Override
    public Transform3D deserialize(JsonElement json) {
        JsonObject obj = json.getAsJsonObject();

        // Position
        JsonArray posArray = obj.getAsJsonArray("position");
        Vector3f position = new Vector3f(
            posArray.get(0).getAsFloat(),
            posArray.get(1).getAsFloat(),
            posArray.get(2).getAsFloat()
        );

        // Rotation
        JsonArray rotArray = obj.getAsJsonArray("rotation");
        Quaternionf rotation = new Quaternionf(
            rotArray.get(0).getAsFloat(),
            rotArray.get(1).getAsFloat(),
            rotArray.get(2).getAsFloat(),
            rotArray.get(3).getAsFloat()
        );

        // Scale
        JsonArray scaleArray = obj.getAsJsonArray("scale");
        Vector3f scale = new Vector3f(
            scaleArray.get(0).getAsFloat(),
            scaleArray.get(1).getAsFloat(),
            scaleArray.get(2).getAsFloat()
        );

        return new Transform3D(position, rotation, scale);
    }

    @Override
    public Class<Transform3D> getComponentType() {
        return Transform3D.class;
    }
}
```

Create `src/main/java/com/yourname/engine/serialization/serializers/HealthSerializer.java`:

```java
package com.yourname.engine.serialization.serializers;

import com.google.gson.*;
import com.yourname.game.Components.Health;
import com.yourname.engine.serialization.ComponentSerializer;

public class HealthSerializer implements ComponentSerializer<Health> {

    @Override
    public JsonElement serialize(Health health) {
        JsonObject json = new JsonObject();
        json.addProperty("current", health.current);
        json.addProperty("max", health.max);
        return json;
    }

    @Override
    public Health deserialize(JsonElement json) {
        JsonObject obj = json.getAsJsonObject();
        return new Health(
            obj.get("current").getAsInt(),
            obj.get("max").getAsInt()
        );
    }

    @Override
    public Class<Health> getComponentType() {
        return Health.class;
    }
}
```

Create `src/main/java/com/yourname/engine/serialization/serializers/MeshRendererSerializer.java`:

```java
package com.yourname.engine.serialization.serializers;

import com.google.gson.*;
import com.yourname.engine.components.MeshRenderer;
import com.yourname.engine.renderer.Mesh;
import com.yourname.engine.serialization.ComponentSerializer;

public class MeshRendererSerializer implements ComponentSerializer<MeshRenderer> {

    @Override
    public JsonElement serialize(MeshRenderer meshRenderer) {
        JsonObject json = new JsonObject();

        // Mesh reference (by name)
        if (meshRenderer.mesh != null) {
            json.addProperty("mesh", getMeshName(meshRenderer.mesh));
        }

        // Color
        JsonArray color = new JsonArray();
        color.add(meshRenderer.colorR);
        color.add(meshRenderer.colorG);
        color.add(meshRenderer.colorB);
        color.add(meshRenderer.colorA);
        json.add("color", color);

        return json;
    }

    @Override
    public MeshRenderer deserialize(JsonElement json) {
        JsonObject obj = json.getAsJsonObject();

        // Load mesh by name
        String meshName = obj.get("mesh").getAsString();
        Mesh mesh = loadMesh(meshName);

        // Color
        JsonArray colorArray = obj.getAsJsonArray("color");
        float r = colorArray.get(0).getAsFloat();
        float g = colorArray.get(1).getAsFloat();
        float b = colorArray.get(2).getAsFloat();
        float a = colorArray.get(3).getAsFloat();

        return new MeshRenderer(mesh, r, g, b, a);
    }

    @Override
    public Class<MeshRenderer> getComponentType() {
        return MeshRenderer.class;
    }

    private String getMeshName(Mesh mesh) {
        // Simple name mapping (in production, use asset manager)
        if (mesh == Mesh.createCube()) return "cube";
        if (mesh == Mesh.createPyramid()) return "pyramid";
        if (mesh == Mesh.createSphere(8)) return "sphere";
        return "cube"; // default
    }

    private Mesh loadMesh(String name) {
        return switch (name) {
            case "cube" -> Mesh.createCube();
            case "pyramid" -> Mesh.createPyramid();
            case "sphere" -> Mesh.createSphere(8);
            default -> Mesh.createCube();
        };
    }
}
```

---

## Step 4: Scene Serializer

Create `src/main/java/com/yourname/engine/serialization/SceneSerializer.java`:

```java
package com.yourname.engine.serialization;

import com.google.gson.*;
import com.yourname.engine.ecs.*;
import com.yourname.engine.components.*;
import java.io.*;
import java.util.*;

/**
 * Serializes/deserializes entire scenes (World with all entities).
 */
public class SceneSerializer {

    private Gson gson;

    public SceneSerializer() {
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();
    }

    /**
     * Save scene to JSON file.
     */
    public void saveScene(World world, String filePath) throws IOException {
        JsonObject sceneJson = new JsonObject();

        // Scene metadata
        sceneJson.addProperty("version", "1.0");
        sceneJson.addProperty("name", "Scene");

        // Serialize all entities
        JsonArray entitiesArray = new JsonArray();

        // Get all entities (iterate through all component storages)
        Set<Entity> allEntities = getAllEntities(world);

        for (Entity entity : allEntities) {
            JsonObject entityJson = serializeEntity(world, entity);
            if (entityJson != null) {
                entitiesArray.add(entityJson);
            }
        }

        sceneJson.add("entities", entitiesArray);

        // Write to file
        try (FileWriter writer = new FileWriter(filePath)) {
            gson.toJson(sceneJson, writer);
        }

        System.out.println("✓ Scene saved: " + filePath);
        System.out.println("  Entities: " + allEntities.size());
    }

    /**
     * Load scene from JSON file.
     */
    public void loadScene(World world, String filePath) throws IOException {
        // Clear existing world
        // (In production, you might want to keep some entities)

        // Read JSON
        JsonObject sceneJson;
        try (FileReader reader = new FileReader(filePath)) {
            sceneJson = gson.fromJson(reader, JsonObject.class);
        }

        // Load entities
        JsonArray entitiesArray = sceneJson.getAsJsonArray("entities");
        int loadedCount = 0;

        for (JsonElement entityElement : entitiesArray) {
            JsonObject entityJson = entityElement.getAsJsonObject();
            Entity entity = deserializeEntity(world, entityJson);
            if (entity != null) {
                loadedCount++;
            }
        }

        System.out.println("✓ Scene loaded: " + filePath);
        System.out.println("  Entities: " + loadedCount);
    }

    private JsonObject serializeEntity(World world, Entity entity) {
        if (!world.isValid(entity)) return null;

        JsonObject entityJson = new JsonObject();
        entityJson.addProperty("id", entity.id());

        // Serialize all components
        JsonObject componentsJson = new JsonObject();

        for (Class<? extends Component> componentType : SerializerRegistry.getRegisteredTypes()) {
            Component component = world.getComponent(entity, componentType);
            if (component != null) {
                ComponentSerializer serializer = SerializerRegistry.get(componentType);
                JsonElement componentJson = serializer.serialize(component);
                componentsJson.add(componentType.getSimpleName(), componentJson);
            }
        }

        entityJson.add("components", componentsJson);

        return entityJson;
    }

    private Entity deserializeEntity(World world, JsonObject entityJson) {
        Entity entity = world.createEntity();

        JsonObject componentsJson = entityJson.getAsJsonObject("components");

        for (String componentName : componentsJson.keySet()) {
            try {
                // Find component type by name
                Class<? extends Component> componentType = findComponentType(componentName);
                if (componentType == null) {
                    System.err.println("Unknown component type: " + componentName);
                    continue;
                }

                // Deserialize component
                ComponentSerializer serializer = SerializerRegistry.get(componentType);
                JsonElement componentJson = componentsJson.get(componentName);
                Component component = serializer.deserialize(componentJson);

                // Add to entity
                world.addComponent(entity, component);

            } catch (Exception e) {
                System.err.println("Failed to deserialize component: " + componentName);
                e.printStackTrace();
            }
        }

        return entity;
    }

    private Set<Entity> getAllEntities(World world) {
        Set<Entity> entities = new HashSet<>();

        // Query for entities with Transform3D (most entities have this)
        world.query(Transform3D.class).forEach(entityView -> {
            entities.add(entityView.getEntity());
        });

        // Also check for 2D entities
        try {
            // Use reflection to access Transform2D if it exists
            Class<?> transform2DClass = Class.forName("com.yourname.game.Components$Transform2D");
            if (Component.class.isAssignableFrom(transform2DClass)) {
                @SuppressWarnings("unchecked")
                Class<? extends Component> componentClass = (Class<? extends Component>) transform2DClass;
                world.query(componentClass).forEach(entityView -> {
                    entities.add(entityView.getEntity());
                });
            }
        } catch (ClassNotFoundException e) {
            // Transform2D not available, skip
        }

        return entities;
    }

    private Class<? extends Component> findComponentType(String simpleName) {
        for (Class<? extends Component> type : SerializerRegistry.getRegisteredTypes()) {
            if (type.getSimpleName().equals(simpleName)) {
                return type;
            }
        }
        return null;
    }
}
```

---

## Step 5: Prefab System

Create `src/main/java/com/yourname/engine/prefab/Prefab.java`:

```java
package com.yourname.engine.prefab;

import com.yourname.engine.ecs.*;
import java.util.*;

/**
 * Prefab: Reusable entity template.
 */
public class Prefab {

    private String name;
    private Map<Class<? extends Component>, Component> components;

    public Prefab(String name) {
        this.name = name;
        this.components = new HashMap<>();
    }

    /**
     * Add a component to this prefab.
     */
    public <T extends Component> void addComponent(T component) {
        components.put(component.getClass(), component);
    }

    /**
     * Instantiate this prefab as a new entity.
     */
    public Entity instantiate(World world) {
        Entity entity = world.createEntity();

        // Clone and add all components
        for (Component component : components.values()) {
            Component clonedComponent = cloneComponent(component);
            world.addComponent(entity, clonedComponent);
        }

        return entity;
    }

    /**
     * Clone a component (deep copy).
     */
    private Component cloneComponent(Component original) {
        // Use serialization for deep cloning
        try {
            ComponentSerializer serializer = SerializerRegistry.get(original.getClass());
            JsonElement json = serializer.serialize(original);
            return serializer.deserialize(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to clone component: " + original.getClass(), e);
        }
    }

    public String getName() {
        return name;
    }
}
```

Create `src/main/java/com/yourname/engine/prefab/PrefabManager.java`:

```java
package com.yourname.engine.prefab;

import com.google.gson.*;
import com.yourname.engine.ecs.Component;
import com.yourname.engine.serialization.*;
import java.io.*;
import java.util.*;

/**
 * Manages loading and storing prefabs.
 */
public class PrefabManager {

    private Map<String, Prefab> prefabs = new HashMap<>();
    private Gson gson;

    public PrefabManager() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    /**
     * Load prefab from JSON file.
     */
    public Prefab loadPrefab(String filePath) throws IOException {
        JsonObject prefabJson;
        try (FileReader reader = new FileReader(filePath)) {
            prefabJson = gson.fromJson(reader, JsonObject.class);
        }

        String name = prefabJson.get("name").getAsString();
        Prefab prefab = new Prefab(name);

        // Load components
        JsonObject componentsJson = prefabJson.getAsJsonObject("components");
        for (String componentName : componentsJson.keySet()) {
            Class<? extends Component> componentType = findComponentType(componentName);
            if (componentType != null) {
                ComponentSerializer serializer = SerializerRegistry.get(componentType);
                Component component = serializer.deserialize(componentsJson.get(componentName));
                prefab.addComponent(component);
            }
        }

        prefabs.put(name, prefab);
        System.out.println("✓ Prefab loaded: " + name);

        return prefab;
    }

    /**
     * Save prefab to JSON file.
     */
    public void savePrefab(Prefab prefab, String filePath) throws IOException {
        JsonObject prefabJson = new JsonObject();
        prefabJson.addProperty("name", prefab.getName());

        JsonObject componentsJson = new JsonObject();
        // Serialize components (implementation omitted for brevity)

        prefabJson.add("components", componentsJson);

        try (FileWriter writer = new FileWriter(filePath)) {
            gson.toJson(prefabJson, writer);
        }

        System.out.println("✓ Prefab saved: " + filePath);
    }

    /**
     * Get a loaded prefab by name.
     */
    public Prefab getPrefab(String name) {
        return prefabs.get(name);
    }

    private Class<? extends Component> findComponentType(String simpleName) {
        for (Class<? extends Component> type : SerializerRegistry.getRegisteredTypes()) {
            if (type.getSimpleName().equals(simpleName)) {
                return type;
            }
        }
        return null;
    }
}
```

---

## Step 6: Initialize Serializers

Update `Engine.java` to register serializers on startup:

```java
public void init() {
    System.out.println("=== Initializing Engine ===\n");

    // Register component serializers
    registerSerializers();

    // ... rest of init ...
}

private void registerSerializers() {
    SerializerRegistry.register(new Transform3DSerializer());
    SerializerRegistry.register(new HealthSerializer());
    SerializerRegistry.register(new MeshRendererSerializer());
    // Add more serializers as needed

    System.out.println("✓ Component serializers registered");
}
```

---

## Step 7: Save/Load Example

Create `src/test/java/com/yourname/engine/serialization/SerializationTest.java`:

```java
package com.yourname.engine.serialization;

import com.yourname.engine.core.Engine;
import com.yourname.engine.ecs.*;
import com.yourname.engine.components.*;
import com.yourname.game.Components.*;
import org.joml.*;

public class SerializationTest {

    public static void main(String[] args) {
        System.out.println("=== Serialization Test ===\n");

        // Initialize engine
        Engine engine = new Engine();
        engine.init();

        World world = engine.getWorld();

        // Create test scene
        System.out.println("Creating test scene...");
        createTestScene(world);

        // Save scene
        try {
            SceneSerializer serializer = new SceneSerializer();
            serializer.saveScene(world, "test_scene.json");
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Clear world
        System.out.println("\nClearing world...");
        // (World clearing not implemented yet - would destroy all entities)

        // Load scene
        try {
            SceneSerializer serializer = new SceneSerializer();
            serializer.loadScene(world, "test_scene.json");
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Verify loaded entities
        System.out.println("\nVerifying loaded entities:");
        int count = 0;
        for (EntityView entity : world.query(Transform3D.class)) {
            Transform3D transform = entity.get(Transform3D.class);
            System.out.printf("  Entity %d: pos=(%.1f, %.1f, %.1f)\n",
                entity.getEntity().id(),
                transform.position.x,
                transform.position.y,
                transform.position.z);
            count++;
        }
        System.out.println("Total entities: " + count);

        engine.cleanup();
    }

    private static void createTestScene(World world) {
        // Create player
        Entity player = world.createEntity();
        world.addComponent(player, new Transform3D(
            new Vector3f(0, 0, 0),
            new Quaternionf(),
            new Vector3f(1, 1, 1)
        ));
        world.addComponent(player, new MeshRenderer(Mesh.createPyramid(), 0, 1, 1, 1));
        world.addComponent(player, new Health(100, 100));

        // Create enemies
        for (int i = 0; i < 5; i++) {
            Entity enemy = world.createEntity();
            world.addComponent(enemy, new Transform3D(
                new Vector3f(i * 5, 0, 10),
                new Quaternionf(),
                new Vector3f(1, 1, 1)
            ));
            world.addComponent(enemy, new MeshRenderer(Mesh.createCube(), 1, 0, 0, 1));
            world.addComponent(enemy, new Health(50, 50));
        }

        System.out.println("✓ Test scene created (6 entities)");
    }
}
```

---

## Step 8: Prefab Example

Create example prefab file `prefabs/enemy.json`:

```json
{
  "name": "StandardEnemy",
  "components": {
    "Transform3D": {
      "position": [0, 0, 0],
      "rotation": [0, 0, 0, 1],
      "scale": [1, 1, 1]
    },
    "MeshRenderer": {
      "mesh": "cube",
      "color": [1, 0, 0, 1]
    },
    "Health": {
      "current": 50,
      "max": 50
    },
    "CircleBounds": {
      "radius": 1.0
    }
  }
}
```

Usage in game:

```java
// Load prefab
PrefabManager prefabManager = new PrefabManager();
Prefab enemyPrefab = prefabManager.loadPrefab("prefabs/enemy.json");

// Spawn 10 enemies
for (int i = 0; i < 10; i++) {
    Entity enemy = enemyPrefab.instantiate(world);

    // Customize instance
    Transform3D transform = world.getComponent(enemy, Transform3D.class);
    transform.position.set(
        (float) (Math.random() * 50 - 25),
        0,
        (float) (Math.random() * 50 - 25)
    );
    transform.markDirty();
}
```

---

## What We've Achieved

**Complete Serialization System:**

- ✅ Component serialization interface
- ✅ Serializer registry for extensibility
- ✅ Common serializers (Transform3D, Health, MeshRenderer)
- ✅ Scene save/load to JSON
- ✅ Prefab system for reusable templates
- ✅ Asset reference management

**Benefits:**

- **Level design**: Create levels in JSON files
- **Save/load**: Persist game state
- **Rapid iteration**: Tweak values without recompiling
- **Prefabs**: Reusable enemy/item templates
- **Editor-ready**: Foundation for level editor (Chapter 8)

---

## Exercises

1. **Add more serializers**: Velocity3D, CircleBounds, all tag components
2. **Scene Manager**: Load/unload multiple scenes
3. **Compressed saves**: Use GZIP for smaller file sizes
4. **Versioning**: Handle old save files after format changes
5. **Binary format**: Implement binary serialization for performance

---

## What's Next?

In **Chapter 7**, we'll:

- Add **input system** (keyboard, mouse, gamepad)
- Implement **audio system** (OpenAL for 3D sound)
- Create **action mapping** (rebindable controls)
- Add sound effects to our game!

---

**Previous:** [← Chapter 5 - 3D Meshes](chapter-05-3d-meshes.md)
**Next:** [Chapter 7 - Input & Audio →](chapter-07-input-audio.md)
