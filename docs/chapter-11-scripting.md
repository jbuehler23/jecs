# Chapter 11: Scripting System
## Extending Your Engine with Scripts

**What You'll Learn:**
- Scripting architecture options (GraalVM JS, Java hot-reload)
- Script component and lifecycle
- Engine API exposure
- Hot-reloading for rapid iteration

**Estimated Time:** 3 hours

---

## Architecture Options

### Option 1: GraalVM JavaScript

```java
public class ScriptEngine {
    private Context jsContext;

    public void init() {
        jsContext = Context.newBuilder("js")
            .allowAllAccess(true)
            .build();

        // Expose engine API to scripts
        jsContext.getBindings("js").putMember("world", world);
        jsContext.getBindings("js").putMember("input", inputManager);
    }

    public void executeScript(String scriptSource) {
        jsContext.eval("js", scriptSource);
    }
}
```

**Example Script (JavaScript):**

```javascript
// player_controller.js
function onUpdate(entity, deltaTime) {
    let transform = world.getComponent(entity, "Transform3D");
    let velocity = world.getComponent(entity, "Velocity");

    if (input.isKeyDown("W")) {
        velocity.z += 10 * deltaTime;
    }

    if (input.isKeyJustPressed("Space")) {
        velocity.y = 5.0;
        console.log("Jump!");
    }
}
```

### Option 2: Java Hot-Reload (JVM Agents)

```java
public interface ScriptBehavior {
    void onCreate(Entity entity, World world);
    void onUpdate(Entity entity, World world, float deltaTime);
    void onDestroy(Entity entity, World world);
}

// User script (recompiled on change)
public class PlayerController implements ScriptBehavior {
    @Override
    public void onUpdate(Entity entity, World world, float deltaTime) {
        // Java code with full type safety
    }
}
```

---

## Script Component

```java
public record Script(
    String scriptPath,
    Object scriptInstance, // Cached compiled script
    Map<String, Object> publicVariables
) implements Component { }
```

## Script System

```java
public class ScriptSystem extends System {
    private ScriptEngine scriptEngine;
    private FileWatcher fileWatcher; // Watch for script changes

    @Override
    public void update(World world, float deltaTime) {
        // Reload changed scripts
        reloadModifiedScripts();

        // Execute all scripts
        world.query(Script.class).forEach(entity -> {
            Script script = entity.get(Script.class);
            scriptEngine.callFunction(script, "onUpdate", entity, deltaTime);
        });
    }

    private void reloadModifiedScripts() {
        for (String changedScript : fileWatcher.getModifiedFiles()) {
            System.out.println("Reloading script: " + changedScript);
            // Recompile and hot-swap
        }
    }
}
```

---

## Engine API Exposure

```java
// Wrapper for safe script access
public class ScriptAPI {
    private World world;
    private InputManager input;

    // Entity methods
    public void setPosition(Entity entity, float x, float y, float z) {
        Transform3D transform = world.getComponent(entity, Transform3D.class);
        if (transform != null) {
            world.addComponent(entity, new Transform3D(
                new Vector3f(x, y, z),
                transform.rotation(),
                transform.scale()
            ));
        }
    }

    // Input methods
    public boolean isKeyDown(String key) {
        return input.isKeyDown(getKeyCode(key));
    }

    // Physics methods
    public void applyForce(Entity entity, float x, float y, float z) {
        // ...
    }
}
```

---

## Exercises

1. Add Lua scripting via LuaJ
2. Implement script debugging (breakpoints, step through)
3. Add script profiling
4. Create visual scripting (node-based)
5. Implement script sandboxing (security)

---

**Previous:** [← Chapter 10 - Physics](chapter-10-physics.md)
**Next:** [Chapter 12 - ECS Optimization →](chapter-12-ecs-optimization.md)
