# Appendix C: ECS Theory & Alternatives
## Deep Dive into ECS Architectures

A comprehensive look at Entity-Component-System patterns, variations, and tradeoffs.

---

## ECS Variations

### 1. Sparse Set ECS (Our Implementation)

**Structure:**
- Sparse array: entity ID → dense index
- Dense arrays: packed component data

**Advantages:**
- O(1) add, remove, lookup
- Simple implementation
- Flexible entity composition

**Disadvantages:**
- Sparse array grows with max entity ID
- Not cache-optimal for iteration
- Gaps in memory

**When to use:** <100K entities, dynamic composition

---

### 2. Archetype ECS (Unity DOTS, Bevy)

**Structure:**
- Group entities by component signature (archetype)
- Each archetype stores components in contiguous arrays

**Example:**
```
Archetype [Position, Velocity]:
  entities: [1, 5, 10]
  positions: [p1, p5, p10]
  velocities: [v1, v5, v10]

Archetype [Position, Velocity, Renderable]:
  entities: [2, 7]
  positions: [p2, p7]
  velocities: [v2, v7]
  renderables: [r2, r7]
```

**Advantages:**
- **Perfect cache locality**: All components for query contiguous
- **Fast iteration**: Simply iterate archetype arrays
- **Minimal indirection**: Direct array indexing

**Disadvantages:**
- **Expensive structural changes**: Adding/removing component = move entity to new archetype
- **Archetype explosion**: Many unique combinations = many archetypes
- **Fragmentation**: Small archetypes waste memory

**When to use:** >100K entities, stable composition, read-heavy workloads

**Optimization:**
```java
// Cache archetype for frequent queries
Archetype movables = world.getArchetype(Position.class, Velocity.class);

// Fast iteration (no lookups)
for (int i = 0; i < movables.size(); i++) {
    Position pos = movables.getPositions()[i];
    Velocity vel = movables.getVelocities()[i];
    // Update...
}
```

---

### 3. Bitset ECS (Traditional)

**Structure:**
- Each entity has a bitset (bit per component type)
- Components stored in separate arrays indexed by entity ID

**Example:**
```
Entity 0: bitset 0b101 (has Position, Renderable)
Entity 1: bitset 0b110 (has Velocity, Renderable)

Position array: [p0, null, ...]
Velocity array: [null, v1, ...]
Renderable array: [r0, r1, ...]
```

**Advantages:**
- Simple queries: `entities.bitset & queryMask == queryMask`
- Predictable memory layout

**Disadvantages:**
- Wastes memory (nulls in arrays)
- Not cache-friendly
- Limited component types (e.g., 64 with long bitset)

**When to use:** Rarely (superseded by sparse sets and archetypes)

---

### 4. Slot Map (Handle-based)

**Structure:**
- Entities are handles (index + generation)
- Components stored in slot maps (versioned slots)

**Advantages:**
- Safe handles (detect use-after-free)
- Stable iteration (no reallocation)

**Disadvantages:**
- Indirection overhead
- More complex than sparse sets

**When to use:** Need stable references, frequent entity destruction

---

## Performance Comparison

### Iteration (1M entities, query [Transform, Velocity])

| Architecture | Time (ms) | Cache Misses | Notes |
|--------------|-----------|--------------|-------|
| Sparse Set   | 8.5       | High         | Need to check multiple storages |
| Archetype    | 2.1       | Low          | Contiguous data, perfect locality |
| Bitset       | 15.0      | Very High    | Many nulls, scattered data |

### Structural Changes (add/remove component, 1K operations)

| Architecture | Add (ms) | Remove (ms) | Notes |
|--------------|----------|-------------|-------|
| Sparse Set   | 0.3      | 0.3         | Fast, no entity moves |
| Archetype    | 12.0     | 12.0        | Expensive, copy all components |
| Bitset       | 0.5      | 0.5         | Just update bitset + array |

---

## Hybrid Approaches

### Sparse Set + Component Groups

Combine sparse set flexibility with archetype-like iteration:

```java
// Sparse sets for flexibility
world.addComponent(entity, new Position(0, 0, 0));

// Pre-built groups for hot queries
ComponentGroup<Position, Velocity> movables = world.group(Position.class, Velocity.class);

// Fast iteration (cached)
movables.forEach((pos, vel) -> {
    // O(n) with perfect locality
});

// Group auto-updates when components change
world.addComponent(entity, new Velocity(1, 0, 0)); // Adds to group
world.removeComponent(entity, Velocity.class);     // Removes from group
```

**Best of both worlds!**

---

## ECS vs Traditional OOP

### OOP Approach

```java
class GameObject {
    Vector3f position;
    void update(float dt) { }
}

class Enemy extends GameObject {
    int health;
    void update(float dt) { super.update(dt); /* AI logic */ }
}

class FlyingEnemy extends Enemy {
    float altitude;
    void update(float dt) { super.update(dt); /* flying logic */ }
}

// Problem: What if FlyingEnemy also needs to swim?
// → Multiple inheritance not allowed in Java
// → Composition with interfaces? Verbose and inflexible
```

### ECS Approach

```java
// Entity = ID
int enemy = world.createEntity();

// Add behaviors as components
world.addComponent(enemy, new Position(0, 0, 0));
world.addComponent(enemy, new Health(100, 100));
world.addComponent(enemy, new AIComponent());
world.addComponent(enemy, new FlyingComponent());
world.addComponent(enemy, new SwimmingComponent()); // No problem!

// Systems operate on any entity with required components
FlyingSystem.update(world, dt);    // Operates on entities with FlyingComponent
SwimmingSystem.update(world, dt);  // Operates on entities with SwimmingComponent
```

---

## Common Pitfalls

### 1. Fat Components

**Bad:**
```java
public class PlayerComponent {
    String name;
    int health, maxHealth;
    float stamina, maxStamina;
    int gold, experience, level;
    Inventory inventory;
    QuestLog quests;
    // ... 50 more fields
}
```

**Good:**
```java
public record Name(String value) implements Component { }
public record Health(int current, int max) implements Component { }
public record Stamina(float current, float max) implements Component { }
public record Currency(int gold, int gems) implements Component { }
public record Experience(int current, int level) implements Component { }
```

**Why:** Small components = better cache usage, mix-and-match

---

### 2. Logic in Components

**Bad:**
```java
public class Transform implements Component {
    float x, y, z;

    public void moveTowards(Transform target, float speed) {
        // NO! Logic belongs in systems
    }
}
```

**Good:**
```java
public record Transform(float x, float y, float z) implements Component { }

public class MovementSystem extends System {
    public void update(World world, float dt) {
        // Logic here
    }
}
```

---

### 3. System Communication via Globals

**Bad:**
```java
public class PlayerSystem {
    public static Player currentPlayer; // Global state

    public void update(World world, float dt) {
        currentPlayer = findPlayer(world);
    }
}

public class CameraSystem {
    public void update(World world, float dt) {
        // Tightly coupled to PlayerSystem
        followPlayer(PlayerSystem.currentPlayer);
    }
}
```

**Good:**
```java
// Use components or events
world.addComponent(cameraEntity, new FollowTarget(playerEntity));

// Or event system
world.emit(new PlayerMovedEvent(player, newPosition));
```

---

## ECS in the Wild

### Unity DOTS

- Archetype-based
- Job system for parallelism
- Burst compiler (LLVM) for speed
- ~10x faster than classic Unity

### Bevy (Rust)

- Archetype-based
- Schedules for system ordering
- Parallel by default (Rust's safety)

### Unreal Mass Entity

- Hybrid approach
- Fragments (components) + Processors (systems)
- Used for large crowds (thousands of NPCs)

### Overwatch (Blizzard)

- Custom ECS
- Deterministic networking
- Handles 12 players × 60 updates/sec

---

## When NOT to Use ECS

- **Small projects**: ECS overhead not worth it
- **UI-heavy games**: Traditional OOP better for hierarchies
- **Narrative games**: Few entities, complex interactions
- **Prototyping**: ECS adds structure cost upfront

**Alternative:** Hybrid approach

- Core gameplay: ECS (player, enemies, projectiles)
- UI: Traditional OOP (button hierarchies)
- Narrative: Event-driven scripting

---

## Further Reading

- **ECS FAQ**: [github.com/SanderMertens/ecs-faq](https://github.com/SanderMertens/ecs-faq)
- **Data-Oriented Design**: [dataorienteddesign.com/dodbook](http://www.dataorienteddesign.com/dodbook/)
- **Unity DOTS**: [docs.unity3d.com/Packages/com.unity.entities@latest](https://docs.unity3d.com/Packages/com.unity.entities@latest)
- **Overwatch ECS**: [youtube.com/watch?v=W3aieHjyNvw](https://www.youtube.com/watch?v=W3aieHjyNvw)
- **EnTT**: [github.com/skypjack/entt](https://github.com/skypjack/entt)

---

**[Back to README](README.md)**
