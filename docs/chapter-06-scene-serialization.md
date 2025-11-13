# Chapter 6: Scene Serialization & Prefabs
## Saving and Loading Your Game World

**What You'll Learn:**
- Scene serialization to JSON format
- Component serialization strategies (deep dive)
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
- Hot-reload scenes without restart

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
- Designers can't tweak values

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

**Professional engine comparison:**

| Engine | Format | Human Readable | Merge Friendly |
|--------|--------|----------------|----------------|
| Unity | YAML | Yes | Yes |
| Unreal | Binary (UAsset) | No | No |
| Godot | TSCN (custom text) | Yes | Yes |
| **JECS** | JSON | Yes | Yes |

**Why JSON?**

✅ **Human readable:** Open in any text editor
✅ **Git friendly:** Easy diffs, merge conflicts visible
✅ **Language agnostic:** Use in tools, editors, scripts
✅ **Fast enough:** Parsing ~10MB/s (acceptable for scenes)
✅ **No compilation:** Edit and reload instantly

**Downsides:**

❌ **Larger files:** 3-5x bigger than binary
❌ **Slower parsing:** 5-10x slower than binary
❌ **No schema validation:** Typos cause runtime errors

**When to use binary:**
- Large worlds (>100,000 entities)
- Network replication (bandwidth critical)
- Obfuscation (prevent modding)

---

## Concepts: Deep vs Shallow Copy

Understanding cloning is essential for prefabs.

### Shallow Copy

```java
class Transform3D {
    Vector3f position;  // Reference to Vector3f object
}

// Shallow copy
Transform3D original = new Transform3D();
original.position = new Vector3f(10, 0, 0);

Transform3D copy = new Transform3D();
copy.position = original.position; // ❌ Same object reference!

// Modify copy
copy.position.x = 20;

// Original changed too!
System.out.println(original.position.x); // 20 (unexpected!)
```

**Memory diagram:**
```
original.position ───┐
                     ↓
                 [Vector3f(20, 0, 0)]
                     ↑
copy.position ───────┘
```

**Result:** Modifying one affects the other (shared reference).

### Deep Copy

```java
// Deep copy
Transform3D copy = new Transform3D();
copy.position = new Vector3f(original.position); // ✅ New object!

// Modify copy
copy.position.x = 20;

// Original unchanged
System.out.println(original.position.x); // 10 (expected!)
```

**Memory diagram:**
```
original.position ─→ [Vector3f(10, 0, 0)]

copy.position ─────→ [Vector3f(20, 0, 0)]
```

**Result:** Independent copies (separate objects).

### Implementing Deep Copy

**Method 1: Manual (fastest, verbose)**

```java
public Transform3D clone() {
    return new Transform3D(
        new Vector3f(this.position),
        new Quaternionf(this.rotation),
        new Vector3f(this.scale)
    );
}
```

**Pros:**
- Explicit (you control what's cloned)
- Fast (no reflection)

**Cons:**
- Boilerplate for every component
- Easy to forget fields

**Method 2: Serialization (elegant, slower)**

```java
public Transform3D clone() {
    // Serialize to JSON
    JsonElement json = serializer.serialize(this);
    // Deserialize to new object
    return serializer.deserialize(json);
}
```

**Pros:**
- Automatic deep copy
- Works for any component with serializer

**Cons:**
- 10x slower than manual
- Hidden allocations (JSON objects)

**Method 3: Reflection (automatic, slowest)**

```java
public <T extends Component> T clone(T original) {
    T copy = (T) original.getClass().newInstance();
    for (Field field : original.getClass().getDeclaredFields()) {
        field.setAccessible(true);
        Object value = field.get(original);
        if (value instanceof Cloneable) {
            field.set(copy, ((Cloneable) value).clone());
        } else {
            field.set(copy, value); // Shallow copy primitives
        }
    }
    return copy;
}
```

**Pros:**
- Zero boilerplate

**Cons:**
- 50x slower (reflection overhead)
- Can't detect all reference types
- Security issues (accessing private fields)

**We use Method 2** (serialization) for prefabs:
- Fast enough for loading (not per-frame)
- Automatic (no manual cloning code)
- Consistent with save/load

---

## Asset References: By Name vs By ID

How do you reference a mesh in serialized data?

### Option 1: By Name (Simple, Fragile)

```json
{
  "MeshRenderer": {
    "mesh": "spaceship_01"
  }
}
```

**Pros:**
- Human readable
- Easy to edit manually

**Cons:**
- Rename breaks references
- No validation (typos cause runtime errors)
- Namespace collisions (two "cube" meshes?)

### Option 2: By Path (Better, Verbose)

```json
{
  "MeshRenderer": {
    "mesh": "assets/models/enemies/spaceship_01.obj"
  }
}
```

**Pros:**
- Unique (path is unique)
- Explicit (know exact file)

**Cons:**
- Verbose
- Moving files breaks references

### Option 3: By GUID (Professional, Complex)

```json
{
  "MeshRenderer": {
    "mesh": "guid://7b4f9a3e-2c1d-4e6a-9b3f-1a2c3d4e5f6a"
  }
}
```

**Pros:**
- Unique (globally unique ID)
- Rename-safe (GUID never changes)
- Professional (Unity, Unreal use this)

**Cons:**
- Not human readable
- Requires asset database
- More complex implementation

**Professional asset management:**

```
Asset Database:
  GUID → File Path mapping

spaceship_01.obj:
  GUID: 7b4f9a3e-2c1d-4e6a-9b3f-1a2c3d4e5f6a
  Path: assets/models/enemies/spaceship_01.obj
  Type: Mesh

When loading:
  1. Read GUID from JSON
  2. Lookup path in database
  3. Load asset from path
  4. Cache in memory

When renaming:
  1. Update database (GUID → new path)
  2. References still work!
```

**Unity example:**

```yaml
# Spaceship.prefab
GameObject:
  m_Component:
  - component: {fileID: 4, guid: 7b4f9a3e2c1d4e6a9b3f1a2c3d4e5f6a, type: 3}
  #                         ↑ GUID references MeshFilter component
```

**We use paths** for this tutorial (simpler), but note that professional engines use GUIDs.

---

## Circular References Problem

Consider this scenario:

```java
class Player implements Component {
    public Entity target; // Reference to another entity
}

class Enemy implements Component {
    public Entity target; // Reference to player
}

// Create circular reference
Entity player = world.createEntity();
Entity enemy = world.createEntity();

Player playerComp = new Player();
playerComp.target = enemy; // Player targets enemy

Enemy enemyComp = new Enemy();
enemyComp.target = player; // Enemy targets player

world.addComponent(player, playerComp);
world.addComponent(enemy, enemyComp);
```

**Serialization problem:**

```json
{
  "entities": [
    {
      "id": 1,
      "components": {
        "Player": {
          "target": {  ← Serialize enemy (entity 2)
            "id": 2,
            "components": {
              "Enemy": {
                "target": {  ← Serialize player (entity 1)
                  "id": 1,
                  "components": {  ← Infinite recursion!
                    ...
```

**Solution 1: Entity IDs**

```json
{
  "entities": [
    {
      "id": 1,
      "components": {
        "Player": {
          "target_id": 2  ← Reference by ID
        }
      }
    },
    {
      "id": 2,
      "components": {
        "Enemy": {
          "target_id": 1
        }
      }
    }
  ]
}
```

**On load:**
1. Create all entities (IDs 1, 2)
2. Deserialize components
3. Resolve references: `target_id: 2` → lookup entity 2

**Solution 2: Two-pass loading**

```java
// Pass 1: Create all entities and components
for (entityJson : entitiesArray) {
    Entity entity = world.createEntity();
    entityIdMap.put(entityJson.get("id"), entity);

    // Load components (but not references)
    loadComponentsExceptReferences(entity, entityJson);
}

// Pass 2: Resolve entity references
for (entityJson : entitiesArray) {
    Entity entity = entityIdMap.get(entityJson.get("id"));
    resolveEntityReferences(entity, entityJson, entityIdMap);
}
```

**Professional approach (Unity):**

Unity uses **File IDs** within a scene and **GUIDs** between scenes:

```yaml
# In same scene file
Player:
  m_Target: {fileID: 2}  ← Local reference

# Across scene files
Player:
  m_Target: {fileID: 2, guid: 7b4f9a3e..., type: 2}
  #                     ↑ Scene GUID + local ID
```

**We avoid entity references** in serialized data (simpler). Use tags instead:

```json
{
  "Player": {
    "target_tag": "Enemy"  ← Find by tag at runtime
  }
}
```

Runtime code:
```java
// Find target by tag
Entity target = world.query(EnemyTag.class).findFirst();
player.target = target;
```

---

## Implementation

### Step 1: Component Serialization Interface

Create `src/main/java/com/yourname/engine/serialization/ComponentSerializer.java`:

```java
package com.yourname.engine.serialization;

import com.google.gson.*;
import com.yourname.engine.ecs.Component;

/**
 * Interface for serializing/deserializing components.
 *
 * <h2>Why Interface?</h2>
 * <p>Each component type needs custom serialization logic:
 * - Transform3D: 3 vectors (position, rotation, scale)
 * - MeshRenderer: Asset reference (mesh name)
 * - Health: Two integers (current, max)
 *
 * <p>Interface allows each component to define its own format.
 *
 * <h2>Alternative: Reflection</h2>
 * <p>Could use reflection to auto-serialize all fields:
 * <pre>
 * for (Field field : component.getClass().getDeclaredFields()) {
 *     json.addProperty(field.getName(), field.get(component));
 * }
 * </pre>
 *
 * <p>Pros: No boilerplate
 * <p>Cons:
 * - Exposes private fields (security issue)
 * - No control over format (can't use compact arrays)
 * - Doesn't handle asset references
 * - 10x slower (reflection overhead)
 *
 * <p>We use explicit serializers for control and performance.
 */
public interface ComponentSerializer<T extends Component> {

    /**
     * Serialize component to JSON.
     *
     * <p>Return format is flexible:
     * - JsonObject for complex data (Transform3D)
     * - JsonPrimitive for simple values (integers, strings)
     * - JsonArray for collections
     *
     * @param component Component to serialize
     * @return JSON representation
     */
    JsonElement serialize(T component);

    /**
     * Deserialize component from JSON.
     *
     * <p>IMPORTANT: Create new instance (deep copy).
     * Don't modify existing components!
     *
     * @param json JSON data
     * @return New component instance
     */
    T deserialize(JsonElement json);

    /**
     * Get the component type this serializer handles.
     *
     * <p>Used for registry lookup:
     * <pre>
     * ComponentSerializer serializer = registry.get(Transform3D.class);
     * </pre>
     */
    Class<T> getComponentType();
}
```

---

### Step 2: Serializer Registry

Create `src/main/java/com/yourname/engine/serialization/SerializerRegistry.java`:

```java
package com.yourname.engine.serialization;

import com.yourname.engine.ecs.Component;
import java.util.*;

/**
 * Registry of component serializers.
 *
 * <h2>Why Static Registry?</h2>
 * <p>Global registry allows:
 * - Access from anywhere (no dependency injection)
 * - Compile-time type safety (generics)
 * - Zero overhead (no lookups)
 *
 * <h2>Thread Safety</h2>
 * <p>Registration happens at startup (single-threaded).
 * Lookups are read-only (thread-safe).
 * No synchronization needed.
 *
 * <h2>Alternative: Dependency Injection</h2>
 * <pre>
 * class SceneSerializer {
 *     private Map<Class, ComponentSerializer> serializers;
 *
 *     public SceneSerializer(ComponentSerializer... serializers) {
 *         for (var s : serializers) {
 *             this.serializers.put(s.getComponentType(), s);
 *         }
 *     }
 * }
 * </pre>
 *
 * <p>Pros: Testable (mock serializers)
 * <p>Cons: Boilerplate (pass everywhere)
 *
 * <p>We use static for simplicity.
 */
public class SerializerRegistry {

    private static final Map<Class<? extends Component>, ComponentSerializer<?>> serializers = new HashMap<>();

    /**
     * Register a serializer for a component type.
     *
     * <h2>Registration Order</h2>
     * <p>Register serializers at engine startup:
     * <pre>
     * public void init() {
     *     SerializerRegistry.register(new Transform3DSerializer());
     *     SerializerRegistry.register(new MeshRendererSerializer());
     *     // ... rest of init
     * }
     * </pre>
     *
     * <h2>Duplicate Registration</h2>
     * <p>If the same component type is registered twice,
     * the second serializer overwrites the first.
     * Useful for hot-reload (replace serializer logic).
     *
     * @param serializer Serializer to register
     */
    public static <T extends Component> void register(ComponentSerializer<T> serializer) {
        serializers.put(serializer.getComponentType(), serializer);
    }

    /**
     * Get serializer for a component type.
     *
     * <h2>Type Safety</h2>
     * <p>Generics ensure compile-time correctness:
     * <pre>
     * ComponentSerializer<Transform3D> serializer =
     *     SerializerRegistry.get(Transform3D.class);
     *
     * Transform3D transform = serializer.deserialize(json);
     * // Type matches! No cast needed.
     * </pre>
     *
     * @throws IllegalArgumentException if no serializer registered
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
     *
     * <p>Use before calling get() to avoid exceptions:
     * <pre>
     * if (SerializerRegistry.hasSerializer(componentType)) {
     *     var serializer = SerializerRegistry.get(componentType);
     *     // ... use serializer
     * } else {
     *     System.err.println("No serializer for " + componentType);
     * }
     * </pre>
     */
    public static boolean hasSerializer(Class<? extends Component> componentType) {
        return serializers.containsKey(componentType);
    }

    /**
     * Get all registered component types.
     *
     * <p>Used for scene serialization (iterate all possible components):
     * <pre>
     * for (Class<? extends Component> type : SerializerRegistry.getRegisteredTypes()) {
     *     Component component = world.getComponent(entity, type);
     *     if (component != null) {
     *         JsonElement json = SerializerRegistry.get(type).serialize(component);
     *         // ... save to file
     *     }
     * }
     * </pre>
     */
    public static Set<Class<? extends Component>> getRegisteredTypes() {
        return serializers.keySet();
    }
}
```

---

### Step 3: Common Serializers

Create `src/main/java/com/yourname/engine/serialization/serializers/Transform3DSerializer.java`:

```java
package com.yourname.engine.serialization.serializers;

import com.google.gson.*;
import com.yourname.engine.components.Transform3D;
import com.yourname.engine.serialization.ComponentSerializer;
import org.joml.*;

/**
 * Serializer for Transform3D component.
 *
 * <h2>Format Choice: Arrays vs Objects</h2>
 *
 * <p><b>Option A: Arrays (compact)</b>
 * <pre>
 * "position": [10, 0, 5]
 * </pre>
 *
 * <p><b>Option B: Objects (verbose)</b>
 * <pre>
 * "position": { "x": 10, "y": 0, "z": 5 }
 * </pre>
 *
 * <p>We use arrays for:
 * - Smaller file size (3x less JSON)
 * - Faster parsing (fewer objects)
 * - Common in 3D formats (OBJ, glTF use arrays)
 *
 * <h2>Quaternion Serialization</h2>
 * <p>Store as [x, y, z, w] instead of Euler angles (x, y, z):
 * - No gimbal lock
 * - Faster interpolation (SLERP)
 * - Same format as glTF, FBX
 *
 * <p>Euler angles would be human-readable but cause issues:
 * <pre>
 * Rotation (45°, 0°, 0°) in Euler
 *   ↓
 * Rotate 45° around X → gimbal lock if Y = 90°
 * </pre>
 */
public class Transform3DSerializer implements ComponentSerializer<Transform3D> {

    @Override
    public JsonElement serialize(Transform3D transform) {
        JsonObject json = new JsonObject();

        // Position (compact array)
        JsonArray position = new JsonArray();
        position.add(transform.position.x);
        position.add(transform.position.y);
        position.add(transform.position.z);
        json.add("position", position);

        // Rotation (quaternion, not Euler!)
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

        // Rotation (quaternion)
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
import com.yourname.game.components.Health;
import com.yourname.engine.serialization.ComponentSerializer;

/**
 * Serializer for Health component.
 *
 * <h2>Simple Components</h2>
 * <p>Health has only two fields (current, max).
 * Simple object format is sufficient.
 *
 * <h2>Optional Fields</h2>
 * <p>Could add default values for missing fields:
 * <pre>
 * int current = obj.has("current") ? obj.get("current").getAsInt() : 100;
 * int max = obj.has("max") ? obj.get("max").getAsInt() : 100;
 * </pre>
 *
 * <p>Allows backward compatibility if we add new fields later.
 */
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

/**
 * Serializer for MeshRenderer component.
 *
 * <h2>Asset Reference Problem</h2>
 * <p>MeshRenderer contains a Mesh object (large, complex).
 * Can't serialize the mesh itself (would duplicate data).
 *
 * <p>Solution: Store mesh NAME, not mesh DATA.
 *
 * <h2>Simple Asset Management (This Tutorial)</h2>
 * <pre>
 * "mesh": "cube"  ← Name
 *
 * On load:
 *   if (name == "cube") return Mesh.createCube();
 * </pre>
 *
 * <h2>Professional Asset Management</h2>
 * <pre>
 * "mesh": "assets/models/spaceship.obj"  ← Path
 *
 * On load:
 *   return AssetManager.load(path);
 *     ↓
 *   Check cache: already loaded?
 *     Yes → return cached mesh
 *     No  → load from file, cache, return
 * </pre>
 *
 * <h2>Asset Database (Unity-style)</h2>
 * <pre>
 * "mesh": "guid://7b4f9a3e-2c1d-4e6a-9b3f-1a2c3d4e5f6a"
 *
 * On load:
 *   GUID → lookup in database → path
 *   Path → AssetManager.load()
 * </pre>
 *
 * <p>Benefits:
 * - Rename-safe (GUID never changes)
 * - Cross-platform (absolute paths work)
 * - Database can track dependencies
 */
public class MeshRendererSerializer implements ComponentSerializer<MeshRenderer> {

    @Override
    public JsonElement serialize(MeshRenderer meshRenderer) {
        JsonObject json = new JsonObject();

        // Mesh reference (by name)
        if (meshRenderer.mesh != null) {
            json.addProperty("mesh", getMeshName(meshRenderer.mesh));
        }

        // Color (tint)
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

    /**
     * Get mesh name for serialization.
     *
     * <p>HACK: Compare object references to identify mesh.
     * In production, use mesh.getName() or AssetDatabase.
     *
     * <p>Problem: createCube() creates new mesh each time!
     * <pre>
     * Mesh cube1 = Mesh.createCube();
     * Mesh cube2 = Mesh.createCube();
     * cube1 == cube2  ← false! Different objects
     * </pre>
     *
     * <p>Solution: Cache meshes (singleton pattern):
     * <pre>
     * private static Mesh cachedCube = null;
     * public static Mesh createCube() {
     *     if (cachedCube == null) {
     *         cachedCube = new Mesh(...);
     *     }
     *     return cachedCube;
     * }
     * </pre>
     */
    private String getMeshName(Mesh mesh) {
        // Simple name mapping (in production, use asset manager)
        // This is a hack for tutorial purposes!
        if (mesh == Mesh.createCube()) return "cube";
        if (mesh == Mesh.createPyramid()) return "pyramid";
        if (mesh == Mesh.createSphere(8)) return "sphere";
        return "cube"; // default
    }

    /**
     * Load mesh by name.
     *
     * <p>In production, use AssetManager:
     * <pre>
     * return AssetManager.getInstance().loadMesh(name);
     * </pre>
     */
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

### Step 4: Scene Serializer

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
 *
 * <h2>Scene Format</h2>
 * <pre>
 * {
 *   "version": "1.0",
 *   "name": "Level 1",
 *   "entities": [
 *     {
 *       "id": 123,
 *       "components": {
 *         "Transform3D": { ... },
 *         "MeshRenderer": { ... }
 *       }
 *     }
 *   ]
 * }
 * </pre>
 *
 * <h2>Versioning Strategy</h2>
 * <p>Version field enables migration:
 * <pre>
 * if (version == "1.0") {
 *     // Old format: "position" was object
 *     Vector3f pos = new Vector3f(
 *         obj.get("position").getAsJsonObject().get("x").getAsFloat(),
 *         ...
 *     );
 * } else if (version == "2.0") {
 *     // New format: "position" is array
 *     JsonArray arr = obj.get("position").getAsJsonArray();
 *     Vector3f pos = new Vector3f(arr.get(0).getAsFloat(), ...);
 * }
 * </pre>
 *
 * <h2>Partial Loading</h2>
 * <p>Could support loading only specific entities:
 * <pre>
 * // Load only entities with tag "Enemy"
 * loadScene(world, "level.json", entity -> {
 *     return entity.has("EnemyTag");
 * });
 * </pre>
 */
public class SceneSerializer {

    private Gson gson;

    public SceneSerializer() {
        this.gson = new GsonBuilder()
            .setPrettyPrinting()  // Human-readable formatting
            .create();
    }

    /**
     * Save scene to JSON file.
     *
     * <h2>Save Process</h2>
     * <pre>
     * 1. Collect all entities (iterate World)
     * 2. For each entity:
     *    - For each component:
     *      - Serialize to JSON
     *      - Add to entity JSON
     * 3. Write JSON to file
     * </pre>
     *
     * <h2>What NOT to Save</h2>
     * <p>Some components shouldn't be saved:
     * - Transient state (velocity, input buffers)
     * - Runtime data (cached matrices, render handles)
     * - Editor-only (gizmos, debug visualization)
     *
     * <p>Use annotation to mark:
     * <pre>
     * &#64;NotSerialized
     * public class DebugGizmo implements Component { }
     * </pre>
     *
     * @param world World to save
     * @param filePath Output file path
     * @throws IOException if write fails
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
     *
     * <h2>Load Process</h2>
     * <pre>
     * 1. Read JSON from file
     * 2. Validate version (migration if needed)
     * 3. For each entity in JSON:
     *    - Create new entity
     *    - For each component:
     *      - Deserialize from JSON
     *      - Add to entity
     * </pre>
     *
     * <h2>Additive vs Replace</h2>
     * <p>Current: Additive (keeps existing entities)
     * <p>Could support replace mode:
     * <pre>
     * public void loadScene(World world, String path, boolean replace) {
     *     if (replace) {
     *         world.clear(); // Destroy all entities first
     *     }
     *     // ... load entities
     * }
     * </pre>
     *
     * @param world World to load into
     * @param filePath Input file path
     * @throws IOException if read fails
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

    /**
     * Serialize single entity to JSON.
     *
     * <p>Iterates all registered component types.
     * If entity has component, serialize it.
     */
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

    /**
     * Deserialize single entity from JSON.
     *
     * <p>Creates new entity and adds components.
     *
     * <h2>Error Handling</h2>
     * <p>If component deserialization fails:
     * - Print error (don't crash)
     * - Skip component (continue with others)
     * - Result: Partially loaded entity
     *
     * <p>Strict mode alternative:
     * <pre>
     * if (strict && deserializationFailed) {
     *     throw new RuntimeException("Failed to load entity");
     * }
     * </pre>
     */
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

    /**
     * Get all entities in world.
     *
     * <p>Problem: World doesn't have getAllEntities() method.
     * Solution: Query for common component (Transform3D).
     *
     * <p>Limitation: Entities without Transform3D won't be saved.
     *
     * <p>Better approach: World.getAllEntities()
     * <pre>
     * public Set<Entity> getAllEntities() {
     *     Set<Entity> entities = new HashSet<>();
     *     for (ComponentStorage storage : allStorages) {
     *         entities.addAll(storage.getEntities());
     *     }
     *     return entities;
     * }
     * </pre>
     */
    private Set<Entity> getAllEntities(World world) {
        Set<Entity> entities = new HashSet<>();

        // Query for entities with Transform3D (most entities have this)
        world.query(Transform3D.class).forEach(entityView -> {
            entities.add(entityView.getEntity());
        });

        // Also check for 2D entities
        try {
            // Use reflection to access Transform2D if it exists
            Class<?> transform2DClass = Class.forName("com.yourname.game.components.Transform2D");
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

    /**
     * Find component type by simple name.
     *
     * <p>Maps "Transform3D" → Transform3D.class
     *
     * <p>Uses SerializerRegistry (only registered types).
     */
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

### Step 5: Prefab System

Create `src/main/java/com/yourname/engine/prefab/Prefab.java`:

```java
package com.yourname.engine.prefab;

import com.google.gson.JsonElement;
import com.yourname.engine.ecs.*;
import com.yourname.engine.serialization.*;
import java.util.*;

/**
 * Prefab: Reusable entity template.
 *
 * <h2>What are Prefabs?</h2>
 * <p>Prefabs are templates for creating entities:
 * - Define once (JSON file)
 * - Instantiate many times (in code)
 * - Each instance is independent copy
 *
 * <h2>Example Use Cases</h2>
 * <ul>
 *   <li>Enemy types (fast/slow, weak/strong)</li>
 *   <li>Projectiles (bullet, rocket, laser)</li>
 *   <li>Power-ups (health, ammo, shield)</li>
 *   <li>Obstacles (crate, barrel, wall)</li>
 * </ul>
 *
 * <h2>Prefab vs Scene</h2>
 * <p>Scene: Collection of entities (entire level)
 * <p>Prefab: Single entity template (one enemy type)
 *
 * <h2>Professional Engines</h2>
 *
 * <p><b>Unity:</b>
 * <pre>
 * Prefab = .prefab file (YAML)
 * Instantiate:
 *   GameObject enemy = Instantiate(enemyPrefab);
 *   enemy.transform.position = spawnPoint;
 * </pre>
 *
 * <p><b>Unreal:</b>
 * <pre>
 * Blueprint = visual prefab (graph-based)
 * Instantiate:
 *   AActor* Enemy = GetWorld()->SpawnActor<AEnemy>(EnemyClass);
 * </pre>
 *
 * <p><b>Godot:</b>
 * <pre>
 * Scene = prefab (.tscn file)
 * Instantiate:
 *   var enemy = preload("res://Enemy.tscn").instance()
 * </pre>
 *
 * <h2>Our Approach</h2>
 * <p>JSON file + deep copy via serialization.
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
     *
     * <p>Only ONE component per type (HashMap enforces this).
     *
     * @param component Component to add
     */
    public <T extends Component> void addComponent(T component) {
        components.put(component.getClass(), component);
    }

    /**
     * Instantiate this prefab as a new entity.
     *
     * <h2>Deep Copy Process</h2>
     * <pre>
     * 1. Create new entity
     * 2. For each component in prefab:
     *    - Clone component (deep copy)
     *    - Add clone to new entity
     * 3. Return new entity
     * </pre>
     *
     * <h2>Why Deep Copy?</h2>
     * <p>Shallow copy would share references:
     * <pre>
     * Prefab:
     *   transform.position = (0, 0, 0)
     *
     * Instance 1:
     *   transform.position = prefab.transform.position  ← Same object!
     *
     * Instance 1 moves to (10, 0, 0)
     *   ↓
     * Prefab position changed! (10, 0, 0)
     *   ↓
     * Instance 2 spawns at (10, 0, 0) instead of (0, 0, 0)!
     * </pre>
     *
     * <p>Deep copy ensures independence:
     * <pre>
     * Instance 1: transform = new Transform3D(0, 0, 0)
     * Instance 2: transform = new Transform3D(0, 0, 0)
     *             ↑ Different objects
     * </pre>
     *
     * @param world World to create entity in
     * @return New entity instance
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
     *
     * <h2>Cloning via Serialization</h2>
     * <p>Process:
     * <pre>
     * component → serialize → JSON → deserialize → clone
     *
     * Transform3D original:
     *   position = (10, 0, 5)
     *   ↓ serialize
     * JSON: { "position": [10, 0, 5], ... }
     *   ↓ deserialize
     * Transform3D clone:
     *   position = new Vector3f(10, 0, 5)  ← New object!
     * </pre>
     *
     * <h2>Performance</h2>
     * <p>Cost: ~10 microseconds per component
     * <p>Example: Spawning 100 enemies with 5 components each
     * <pre>
     * 100 entities × 5 components × 10μs = 5000μs = 5ms
     * </pre>
     *
     * <p>Acceptable for spawning (not per-frame).
     *
     * <h2>Alternative: Manual Clone</h2>
     * <pre>
     * public Transform3D clone() {
     *     return new Transform3D(
     *         new Vector3f(this.position),
     *         new Quaternionf(this.rotation),
     *         new Vector3f(this.scale)
     *     );
     * }
     * </pre>
     *
     * <p>Pros: 10x faster
     * <p>Cons: Boilerplate for every component
     *
     * @param original Component to clone
     * @return Deep copy
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

---

## Common Issues and Solutions

### Issue 1: "No serializer registered for X"

**Error:**
```
IllegalArgumentException: No serializer registered for: Transform3D
```

**Cause:** Forgot to register serializer at startup.

**Solution:**
```java
// In Engine.init():
SerializerRegistry.register(new Transform3DSerializer());
```

### Issue 2: JSON Parse Error

**Error:**
```
JsonSyntaxException: Expected BEGIN_ARRAY but was BEGIN_OBJECT
```

**Cause:** JSON format changed (array ↔ object).

**Solution:** Check serializer format matches JSON:
```java
// Serializer writes array
json.add("position", new JsonArray(...));

// JSON must have array
"position": [10, 0, 5]  ✅
"position": {"x": 10, "y": 0, "z": 5}  ❌
```

### Issue 3: Shared References After Load

**Symptom:** Modifying one entity affects another.

**Cause:** Shallow copy in deserialize().

**Solution:** Ensure deep copy:
```java
// ❌ Shallow copy
Vector3f position = prefab.position; // Same object!

// ✅ Deep copy
Vector3f position = new Vector3f(prefab.position); // New object!
```

---

## Further Reading

### Serialization
- [Gson User Guide](https://github.com/google/gson/blob/master/UserGuide.md)
- [JSON Specification](https://www.json.org/)
- [MessagePack](https://msgpack.org/) - Binary alternative to JSON

### Game Engine Formats
- [Unity YAML](https://docs.unity3d.com/Manual/FormatDescription.html)
- [Unreal Asset System](https://docs.unrealengine.com/5.0/en-US/assets-and-packages-in-unreal-engine/)
- [Godot TSCN Format](https://docs.godotengine.org/en/stable/contributing/development/file_formats/tscn.html)

---

## Exercises

1. **Add Versioning**
   - Add "version" field to JSON
   - Handle migration from v1.0 → v2.0

2. **Binary Serialization**
   - Implement binary format (DataOutputStream)
   - Compare file size and speed vs JSON

3. **Compressed Saves**
   - Use GZIP to compress JSON
   - Measure compression ratio

4. **Asset Database**
   - Implement GUID → Path mapping
   - Make mesh references rename-safe

5. **Prefab Variants**
   - Inherit from base prefab (enemy → fast enemy)
   - Override specific components

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
