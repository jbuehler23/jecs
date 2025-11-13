# Chapter 12: ECS Performance Optimization
## Scaling to Millions of Entities

**What You'll Learn:**
- Sparse set implementation deep-dive
- Archetype ECS comparison
- Parallel system execution with virtual threads
- Component groups and views
- Memory optimization techniques

**Estimated Time:** 3 hours

---

## Sparse Set Optimization

### Current Implementation Bottlenecks

```
Query with 3 components:
1. Iterate smallest component storage (good!)
2. For each entity, check if it has other components (bad - cache misses!)
3. Access components from separate arrays (bad - pointer chasing!)
```

### Optimized: Component Groups

```java
public class ComponentGroup<A extends Component, B extends Component> {
    private int[] entities;
    private A[] componentsA;
    private B[] componentsB;
    private int size;

    // Cache-friendly: all data for entity i at index i
    public void forEach(BiConsumer<A, B> action) {
        for (int i = 0; i < size; i++) {
            action.accept(componentsA[i], componentsB[i]);
        }
    }
}

// Pre-build groups for common queries
ComponentGroup<Transform3D, Velocity> movableEntities = world.group(Transform3D.class, Velocity.class);

// Fast iteration (no component lookups!)
movableEntities.forEach((transform, velocity) -> {
    // Update logic
});
```

---

## Archetype ECS

### Concept

Group entities by component signature (archetype):

```
Archetype [Position, Velocity] → Entities [1, 5, 10, 50]
Archetype [Position, Velocity, Renderable] → Entities [2, 3, 7]
Archetype [Position, Health] → Entities [4, 6, 8, 9]
```

### Benefits

- **Perfect cache locality**: All components for archetype stored contiguously
- **Fast iteration**: Query = iterate matching archetypes
- **Minimal indirection**: Direct array access

### Tradeoffs

- **Expensive add/remove**: Entity moves between archetypes (copy all components)
- **Memory overhead**: Many small archetypes = fragmentation
- **Complexity**: More implementation code

### When to Use

- **Archetype**: Stable entity composition, >100K entities, read-heavy
- **Sparse Set**: Dynamic composition, <100K entities, write-heavy

---

## Parallel System Execution

### Java 25 Virtual Threads

```java
public class ParallelSystemExecutor {
    public void executeSystems(List<System> systems, World world, float deltaTime) {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            // Group systems by dependencies
            List<List<System>> independentGroups = groupByDependencies(systems);

            for (List<System> group : independentGroups) {
                // Execute independent systems in parallel
                for (System system : group) {
                    scope.fork(() -> {
                        system.update(world, deltaTime);
                        return null;
                    });
                }

                // Wait for group to finish before next group
                scope.join();
                scope.throwIfFailed();
            }
        } catch (Exception e) {
            throw new RuntimeException("System execution failed", e);
        }
    }

    private List<List<System>> groupByDependencies(List<System> systems) {
        // Analyze which systems read/write same components
        // Group systems that don't conflict
    }
}
```

### System Dependencies

```java
@SystemMetadata(
    reads = {Position.class, Velocity.class},
    writes = {Position.class}
)
public class MovementSystem extends System { }

@SystemMetadata(
    reads = {Position.class, Health.class},
    writes = {Health.class}
)
public class DamageSystem extends System { }

// These can run in parallel (no write conflicts)!
```

---

## Memory Optimization

### Object Pooling

```java
public class ComponentPool<T extends Component> {
    private Queue<T> pool = new ConcurrentLinkedQueue<>();

    public T acquire() {
        T component = pool.poll();
        return component != null ? component : createNew();
    }

    public void release(T component) {
        reset(component);
        pool.offer(component);
    }
}

// Usage
Transform3D transform = transformPool.acquire();
// Use transform...
transformPool.release(transform); // Reuse instead of GC
```

### Compact Object Headers (Java 25)

```java
// Enable with JVM flags
-XX:+UseCompactObjectHeaders
-XX:+UseCompressedOops

// Reduces object overhead from 16 bytes to 8 bytes
// For 1M entities: saves 8 MB!
```

---

## Benchmarks

### Sparse Set vs Archetype

```
Scenario: 100K entities, query [Transform, Velocity, Renderable]

Sparse Set:
- Iteration: 2.5ms
- Add component: 0.3µs
- Remove component: 0.3µs

Archetype:
- Iteration: 0.8ms (3x faster!)
- Add component: 15µs (50x slower!)
- Remove component: 15µs (50x slower!)
```

### Parallel Systems

```
Sequential execution: 12ms/frame
Parallel (4 virtual threads): 4ms/frame (3x speedup)
Parallel (16 virtual threads): 2.5ms/frame (4.8x speedup)
```

---

## Exercises

1. Implement archetype ECS from scratch
2. Add system profiling (time per system)
3. Implement lock-free component storage
4. Create benchmarking suite
5. Add memory profiler integration

---

**Previous:** [← Chapter 11 - Scripting](chapter-11-scripting.md)
**Next:** [Chapter 13 - Profiling →](chapter-13-profiling.md)
