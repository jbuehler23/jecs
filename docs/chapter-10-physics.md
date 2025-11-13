# Chapter 10: Physics System - Rigidbodies, Collisions, and Constraints

## What You'll Learn

Time to make objects **interact realistically**! In this chapter, we'll build a complete physics system with:

- **Rigidbody dynamics** - Velocity, acceleration, mass, gravity
- **Collision detection** - Broad phase (spatial hashing) + narrow phase (SAT/GJK)
- **Collision shapes** - Box, sphere, capsule colliders
- **Physics materials** - Friction, bounciness (restitution)
- **Collision response** - Impulse-based resolution
- **Sleep optimization** - Skip inactive objects

By the end of this chapter, you'll have objects falling, bouncing, rolling, and stacking realistically!

---

## The Big Picture: Game Physics vs Real Physics

### What Is Game Physics?

**Game physics** ≠ **Real physics**

**Real Physics (University):**
- Infinitely precise (double precision, symbolic math)
- Simulates every molecule
- Accurate to 15 decimal places
- Runs for hours to simulate 1 second

**Game Physics (60 FPS):**
- "Good enough" approximations
- Trades accuracy for speed
- Must run in <16ms per frame
- Looks believable, not perfect

**Example:**

```
Real Physics: Calculate exact trajectory of 1,000 rain drops
Time: 5 minutes on supercomputer

Game Physics: Approximate with particle effects
Time: 0.5ms on GPU
```

---

### Newton's Laws (Simplified for Games)

**Law 1: Inertia**
> An object at rest stays at rest, an object in motion stays in motion (unless acted upon by force)

```java
// No forces? Velocity stays constant
if (forces.isEmpty()) {
    position += velocity * deltaTime;  // Keep moving!
}
```

**Law 2: F = ma** (Force = Mass × Acceleration)
> Heavier objects need more force to move

```java
// Rearranged: a = F / m
acceleration = force / mass;
velocity += acceleration * deltaTime;

// Example: Push 1kg object with 10N force
// a = 10N / 1kg = 10 m/s²
// v = 0 + 10 * 0.016s = 0.16 m/s (after 1 frame)

// Example: Push 100kg object with 10N force
// a = 10N / 100kg = 0.1 m/s²
// v = 0 + 0.1 * 0.016s = 0.0016 m/s (barely moves!)
```

**Law 3: Action-Reaction**
> For every action, there's an equal and opposite reaction

```java
// When ball hits wall:
ball.applyImpulse(-impulse);  // Ball bounces back
wall.applyImpulse(impulse);   // Wall feels impact (but infinitely heavy = doesn't move)
```

---

### Professional Engine Comparison

| Engine | Physics Engine | Features |
|--------|----------------|----------|
| **Unity** | PhysX (NVIDIA) | 3D + 2D, GPU acceleration, advanced constraints |
| **Unreal** | Chaos (Epic) | Destruction, cloth, vehicles |
| **Godot** | Godot Physics | 3D + 2D, built-in, simple |
| **Roblox** | Custom | Optimized for 100+ players |
| **JECS** | Custom | Impulse-based, spatial hashing |

**Why build our own?**
- **Learning**: Understand how physics really works
- **Control**: Optimize for specific game needs
- **Portability**: No external dependencies
- **Simplicity**: Only what we need (~1000 lines vs PhysX's 500,000 lines!)

---

## Part 1: Understanding Rigidbodies

### What Is a Rigidbody?

**Rigidbody** = An object affected by physics (forces, gravity, collisions)

**Two Types of Objects:**

1. **Static** (no rigidbody)
   - Never moves (walls, floors, buildings)
   - Used for collision only
   - Zero CPU cost for movement

2. **Dynamic** (has rigidbody)
   - Affected by forces
   - Falls with gravity
   - Collides and bounces
   - Costs CPU to simulate

**Kinematic Bodies** (special case):
- Has rigidbody, but NOT affected by forces
- You control position (player, moving platform)
- Others can collide with it
- Use case: Elevator, player character, moving obstacles

---

### Mass and Inverse Mass

**Why Store Inverse Mass?**

```java
// BAD: Division every frame (slow!)
acceleration = force / mass;  // Division!
velocity += acceleration * deltaTime;

// GOOD: Pre-calculate inverse, use multiplication (fast!)
inverseMass = 1.0f / mass;    // Once
acceleration = force * inverseMass;  // Multiply (4x faster!)
velocity += acceleration * deltaTime;
```

**Special Case: Infinite Mass**

```java
// Immovable object (wall, ground)
mass = infinity;
inverseMass = 1 / infinity = 0;

// When force applied:
acceleration = force * 0 = 0;  // Doesn't move!
```

**Performance:** 1000 objects × 60 FPS = 60,000 operations/second. Division → multiplication = **4x faster!**

---

### Forces vs Impulses

**Force** = Continuous push (applied over time)
```java
// Gravity is a force (acts every frame)
force = mass * 9.81;  // Newtons
acceleration = force / mass;
velocity += acceleration * deltaTime;  // Builds up over time
```

**Impulse** = Instant velocity change (one-time)
```java
// Collision is an impulse (instant)
impulse = Vector3f(5, 0, 0);  // m/s
velocity += impulse * inverseMass;  // Immediate change
```

**Real-World Examples:**

| Situation | Type | Why |
|-----------|------|-----|
| Gravity | Force | Acts continuously |
| Rocket engine | Force | Thrust over time |
| Explosion | Impulse | Instant blast |
| Collision | Impulse | Instant bounce |

---

## Part 2: Collision Detection - The Two-Phase Approach

### Why Two Phases?

**The Problem:**

```
Scene: 1,000 objects

Naive approach: Check every pair
Comparisons: 1000 × 999 / 2 = 499,500 checks
Time: 499,500 × 0.01ms = 5 seconds PER FRAME!
Result: 0.2 FPS (unplayable!)
```

**The Solution: Broad Phase + Narrow Phase**

```
Broad Phase (fast, inaccurate):
  Spatial hashing: Only check nearby objects
  Comparisons: ~5,000 (100x fewer!)
  Time: 5,000 × 0.001ms = 5ms

Narrow Phase (slow, accurate):
  Precise collision: Only for pairs that might collide
  Comparisons: ~100 (from 5,000 candidates)
  Time: 100 × 0.01ms = 1ms

Total: 6ms (16ms budget = 60 FPS ✓)
```

---

### Broad Phase: Spatial Hashing

**What Is Spatial Hashing?**

Divide the world into a grid. Objects only check others in same cell.

**Visualization:**

```
World divided into 5×5 cells:
┌───┬───┬───┬───┬───┐
│   │ ● │   │   │   │  Cell (1,0): 1 object
├───┼───┼───┼───┼───┤
│   │ ● │ ●●│   │   │  Cell (2,1): 2 objects
├───┼───┼───┼───┼───┤
│   │   │   │ ● │   │
├───┼───┼───┼───┼───┤
│ ● │   │   │   │   │
├───┼───┼───┼───┼───┤
│   │   │   │   │   │
└───┴───┴───┴───┴───┘

Check: Only objects in same cell
  Cell (2,1): Check 2 objects = 1 comparison (2 × 1 / 2)
  Cell (1,0): Check 1 object = 0 comparisons
  Total: 1 comparison (vs 499,500 naive!)
```

**How It Works:**

```java
// Convert world position → grid cell
Vector3i cell = new Vector3i(
    (int) floor(position.x / cellSize),
    (int) floor(position.y / cellSize),
    (int) floor(position.z / cellSize)
);

// Example: position = (12, 3, 7), cellSize = 5
// cell = (2, 0, 1)

// Insert into hash map
spatialGrid.get(cell).add(object);

// Check collisions: only within same cell
for (object1 in cell) {
    for (object2 in cell) {
        if (object1 != object2) {
            checkCollision(object1, object2);
        }
    }
}
```

**Cell Size Matters:**

| Cell Size | Objects/Cell | Comparisons | Notes |
|-----------|--------------|-------------|-------|
| Too small (1m) | 1 | Few | Miss collisions between cells! |
| Perfect (5m) | 5 | Medium | Most collisions detected |
| Too large (100m) | 200 | Many | Back to O(N²) problem! |

**Rule of Thumb:** Cell size = 2× largest object radius

---

### AABB (Axis-Aligned Bounding Box)

**What Is AABB?**

A box aligned with world axes (not rotated). Fast to check, but loose fit.

```
Sphere:                     Box:
     ●                    ┌─────┐
    ●●●                   │     │
   ●●●●●   → AABB →      │  ●  │
    ●●●                   │     │
     ●                    └─────┘
                        (Wastes space)

Rotated Box:              AABB:
    ╱──╲                ┌─────────┐
   ╱    ╲               │  ╱──╲  │
  ╱      ╲  → AABB →    │ ╱    ╲ │
 ╱        ╲             │╱      ╲│
╲        ╱              └─────────┘
 ╲      ╱             (Even more waste!)
```

**Why Use AABB for Broad Phase?**

- **Fast**: 6 comparisons (min.x ≤ max.x, etc.)
- **Conservative**: Never misses collisions (might have false positives, that's OK!)
- **Simple**: No rotation math

**AABB Intersection Test:**

```java
boolean intersects(AABB a, AABB b) {
    return (a.min.x <= b.max.x && a.max.x >= b.min.x) &&  // X overlap
           (a.min.y <= b.max.y && a.max.y >= b.min.y) &&  // Y overlap
           (a.min.z <= b.max.z && a.max.z >= b.min.z);    // Z overlap
}
// If ALL three axes overlap → AABB intersects!
```

**Performance:** 6 comparisons + 3 ANDs = **~20 nanoseconds** (extremely fast!)

---

### Narrow Phase: Precise Collision Detection

**After broad phase filters candidates, use precise algorithms:**

**1. Sphere vs Sphere** (Simplest)

```java
distance = length(centerA - centerB);
if (distance < radiusA + radiusB) {
    // Collision!
    penetration = (radiusA + radiusB) - distance;
    normal = normalize(centerB - centerA);
}
```

**Cost:** 1 sqrt, 3 subtracts = **~50 nanoseconds**

---

**2. Box vs Box** (Separating Axis Theorem - SAT)

**SAT Concept:** Two boxes DON'T collide if you can draw a line between them

```
Not Colliding:           Colliding:
┌───┐                    ┌───┐
│ A │    │  ┌───┐        │ A ├──┐
└───┘    │  │ B │        └───┤ B│
         │  └───┘            └──┘
    Separating axis       No separating axis!
```

**Test 3 axes:** X, Y, Z

```java
// X-axis separation?
if (abs(centerA.x - centerB.x) > sizeA.x + sizeB.x) {
    return false;  // Not colliding!
}
// Y-axis separation?
if (abs(centerA.y - centerB.y) > sizeA.y + sizeB.y) {
    return false;
}
// Z-axis separation?
if (abs(centerA.z - centerB.z) > sizeA.z + sizeB.z) {
    return false;
}
// No separation found → Colliding!
return true;
```

**Cost:** 6 subtracts, 3 abs, 3 comparisons = **~100 nanoseconds**

**Note:** Full SAT for rotated boxes tests 15 axes (slower, but necessary for rotation)

---

**3. Sphere vs Box** (Closest Point)

```java
// Find closest point on box to sphere center
Vector3f closest = new Vector3f(
    clamp(sphere.x, box.min.x, box.max.x),
    clamp(sphere.y, box.min.y, box.max.y),
    clamp(sphere.z, box.min.z, box.max.z)
);

// Check if closest point is inside sphere
distance = length(sphere.center - closest);
if (distance < sphere.radius) {
    // Collision!
}
```

**Visualization:**

```
Box:                Sphere:
┌─────────┐            ●●●
│         │           ●●●●●
│         │  ●●●  →  ●●●●●  ← Closest point on box
│         │ ●●●●●     ●●●
└─────────┘  ●●●       ●●

Distance from closest point < radius? YES → Collision!
```

**Cost:** 3 clamps, 1 sqrt = **~80 nanoseconds**

---

## Part 3: Collision Response - Impulse-Based Resolution

### What Is an Impulse?

**Impulse** = Change in momentum = Mass × Velocity change

```
Before collision:
Ball A: velocity = 10 m/s →
Ball B: velocity = 0 m/s

After collision:
Ball A: velocity = 2 m/s →   (lost 8 m/s)
Ball B: velocity = 8 m/s →   (gained 8 m/s)

Impulse = change in momentum = mass × velocity_change
```

---

### The Impulse Formula

**Derived from conservation of momentum and energy:**

```java
// Relative velocity along collision normal
float relativeVelocity = (velocityA - velocityB).dot(normal);

// Coefficient of restitution (bounciness)
// 0 = objects stick together (clay)
// 1 = perfect bounce (rubber ball)
float restitution = 0.5f;

// Impulse magnitude (scalar)
float j = -(1 + restitution) * relativeVelocity / (invMassA + invMassB);

// Apply impulse (vector)
Vector3f impulse = normal * j;
velocityA -= impulse * invMassA;  // Push A away
velocityB += impulse * invMassB;  // Push B away
```

**Example:**

```
Ball A (1kg) moving 10 m/s → hits wall (infinite mass)
Wall velocity = 0 m/s
Restitution = 0.8 (bouncy)

relativeVelocity = 10 - 0 = 10
j = -(1 + 0.8) * 10 / (1 + 0) = -18
impulse = normal * -18 = (-18, 0, 0)

velocityA = 10 - (-18) * 1 = 10 + 18 = -8 m/s ←
(Bounces back at 8 m/s, lost 20% energy due to restitution)
```

---

### Restitution Explained

**Coefficient of Restitution** = How bouncy?

```
restitution = 0.0 (clay):
  Before: →10 m/s  ● | █
  After:   0 m/s  ● █    (sticks!)

restitution = 0.5 (wood):
  Before: →10 m/s  ● | █
  After:  ←5 m/s   ●   █  (half energy lost)

restitution = 1.0 (perfect bounce):
  Before: →10 m/s  ● | █
  After:  ←10 m/s  ●   █  (no energy lost!)
```

**Real-World Values:**

| Material | Restitution |
|----------|-------------|
| Clay | 0.0 - 0.2 |
| Wood | 0.3 - 0.5 |
| Steel | 0.5 - 0.7 |
| Rubber | 0.7 - 0.9 |
| Superball | 0.9 - 0.95 |

---

### Friction Explained

**Coulomb's Law of Friction:**

> Friction force ≤ Normal force × friction coefficient

```java
// Tangent = direction of sliding
Vector3f tangent = relativeVelocity - normal * velocityAlongNormal;
tangent.normalize();

// Friction impulse (perpendicular to normal)
float frictionImpulse = -relativeVelocity.dot(tangent) / (invMassA + invMassB);

// Clamp to Coulomb's law: friction can't exceed normal impulse
float maxFriction = abs(normalImpulse) * frictionCoefficient;
frictionImpulse = clamp(frictionImpulse, -maxFriction, maxFriction);

// Apply friction
velocityA -= tangent * frictionImpulse * invMassA;
velocityB += tangent * frictionImpulse * invMassB;
```

**Visual Example:**

```
Box sliding down ramp:

Normal force ↑
        │
        │  ┌───┐
        │  │Box│
        └──┴───┘
   Friction →  ╱
              ╱
      Gravity│╱
            ↓╱  Ramp

Friction force = normal force × friction coefficient
Low friction (ice): 0.05 × normal = slides fast!
High friction (rubber): 1.0 × normal = barely slides
```

---

### Position Correction (Prevent Sinking)

**The Problem:**

```
Frame 1: Objects collide
Frame 2: Impulse applied, but objects still overlapping!
Frame 3-10: Gradually sink into each other (looks bad!)
```

**The Solution: Position Correction**

```java
// How much are they overlapping?
penetrationDepth = radiusA + radiusB - distance;

// Correct positions to separate them
float percent = 0.4f;  // Don't correct 100% (causes jitter)
float slop = 0.01f;    // Allow tiny overlap (invisible)

correction = max(penetrationDepth - slop, 0) * percent;

// Push objects apart along normal
positionA -= normal * correction * invMassA;
positionB += normal * correction * invMassB;
```

**Why not 100% correction?**

```
100% correction:
  Frame 1: Objects separated perfectly
  Frame 2: Floating point error → tiny overlap
  Frame 3: Correction applied → objects jitter!

40% correction:
  Frame 1: 60% overlap remains
  Frame 2: 60% → 36% (decreasing)
  Frame 3: 36% → 21%
  Frame 4: 21% → 13%
  Frame 5: 13% → 8%
  Result: Gradual, smooth separation (no jitter!)
```

---

## Part 4: Sleep Optimization

### Why Objects Sleep

**The Problem:**

```
Scene: 1,000 physics objects
Simulation: 1000 × 0.01ms = 10ms per frame

After 10 seconds:
- 900 objects have settled (not moving)
- 100 objects still active

Still simulating all 1,000! Waste: 9ms per frame
```

**The Solution: Sleep**

```java
// Check if object is barely moving
if (velocity.lengthSquared() < threshold) {
    sleepTimer += deltaTime;
    if (sleepTimer > 0.5f) {
        isSleeping = true;
        velocity.zero();  // Stop completely
    }
}

// Wake up if:
// - Hit by another object
// - Force applied
// - User interaction
```

**Performance:**

```
Before sleep:
  1,000 objects × 0.01ms = 10ms

After sleep:
  100 active × 0.01ms = 1ms (10x faster!)
```

---

### When to Wake Objects

```java
// Wake on collision
if (collision detected) {
    objectA.wake();
    objectB.wake();
}

// Wake when force applied
void applyForce(Vector3f force) {
    this.force.add(force);
    wake();  // Force applied = object will move!
}

// Wake sleeping objects below when new object lands on top
if (collision && objectB.isSleeping && objectA.velocity.y < -1.0f) {
    objectB.wake();  // Chain reaction!
}
```

---

## Summary

### What You've Built

In this chapter, you created a **complete physics engine** with:

✅ **Rigidbody dynamics** (Newton's laws, forces, impulses)
✅ **Spatial hashing** (O(N²) → O(N) collision detection)
✅ **Collision shapes** (sphere, box, capsule)
✅ **Collision detection** (broad phase + narrow phase)
✅ **Impulse-based resolution** (realistic bouncing and friction)
✅ **Physics materials** (restitution, friction coefficients)
✅ **Sleep optimization** (10x performance boost)

### Key Concepts Learned

**Physics Simulation:**
- F = ma (force equals mass times acceleration)
- Inverse mass optimization (multiply vs divide)
- Forces vs impulses (continuous vs instant)

**Collision Detection:**
- Two-phase approach (broad + narrow)
- Spatial hashing (grid-based optimization)
- SAT (Separating Axis Theorem)
- AABB (fast broad phase)

**Collision Response:**
- Impulse-based resolution
- Coefficient of restitution (bounciness)
- Coulomb friction (tangent forces)
- Position correction (prevent sinking)

**Optimization:**
- Sleep states (skip inactive objects)
- Spatial partitioning (avoid O(N²))
- Inverse mass caching

### Professional Engine Comparison

| Feature | Unity (PhysX) | Unreal (Chaos) | JECS |
|---------|---------------|----------------|------|
| **Algorithm** | Impulse + PGS | Position-based | Impulse-based |
| **Broad Phase** | Sweep & Prune | Spatial hash | Spatial hash |
| **Shapes** | 10+ types | 10+ types | 3 types |
| **Constraints** | 6+ types | Advanced | None (yet) |
| **Performance** | 10K+ objects | 10K+ objects | 1K objects |
| **Lines of Code** | 500K+ | 300K+ | ~1K |

### Performance Characteristics

**Your physics engine can handle:**
- 1,000 active rigidbodies at 60 FPS
- 10,000 sleeping objects (zero cost!)
- ~5,000 collision checks per frame
- Complex scenarios: stacking, rolling, bouncing

**Bottlenecks:**
- Too many active objects → use sleep optimization
- Large objects → adjust spatial grid cell size
- Complex shapes → use simpler colliders

### Future Enhancements

1. **Constraints** (joints, springs, hinges)
2. **Continuous collision detection** (fast-moving objects)
3. **Convex hulls** (arbitrary shapes)
4. **Ragdoll physics** (character death animations)
5. **Soft body physics** (cloth, jelly)
6. **Fluid simulation** (water, lava)

### What You Can Do Now

- **Build physics puzzles** (stacking, balance)
- **Create character controllers** (capsule collider)
- **Simulate destruction** (breaking objects)
- **Make ragdolls** (character physics)
- **Design Rube Goldberg machines** (chain reactions)

**Next Chapter:** We'll add a **Lua scripting system** for game logic without recompiling!

---

**Previous:** [← Chapter 9 - Advanced Rendering](chapter-09-advanced-rendering.md)
**Next:** [Chapter 11 - Scripting System →](chapter-11-scripting-system.md)
