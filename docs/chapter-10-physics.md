# Chapter 10: Physics Integration
## Collision Detection & Rigidbody Dynamics

**What You'll Learn:**
- Physics engine integration (JBullet or custom)
- Rigidbody component
- Collider shapes
- Collision detection and response
- Raycasting for picking

**Estimated Time:** 3 hours

---

## Physics Components

```java
public record Rigidbody(
    float mass,
    Vector3f velocity,
    Vector3f angularVelocity,
    boolean isKinematic,
    float drag,
    float angularDrag
) implements Component { }

public sealed interface Collider extends Component {
    record BoxCollider(Vector3f halfExtents) implements Collider { }
    record SphereCollider(float radius) implements Collider { }
    record CapsuleCollider(float radius, float height) implements Collider { }
}
```

## Physics System

```java
public class PhysicsSystem extends System {
    private List<CollisionPair> collisions = new ArrayList<>();

    @Override
    public void update(World world, float deltaTime) {
        // 1. Apply forces (gravity)
        applyGravity(world, deltaTime);

        // 2. Integrate velocity → position
        integrateMotion(world, deltaTime);

        // 3. Detect collisions
        detectCollisions(world);

        // 4. Resolve collisions
        resolveCollisions(world);
    }

    private void applyGravity(World world, float dt) {
        world.query(Rigidbody.class).forEach(entity -> {
            Rigidbody rb = entity.get(Rigidbody.class);
            if (!rb.isKinematic()) {
                rb.velocity().y -= 9.81f * dt;
            }
        });
    }
}
```

## Collision Detection

```java
private void detectCollisions(World world) {
    collisions.clear();

    // Broad phase: spatial partitioning (grid, octree, BVH)
    List<Entity> potentialPairs = broadPhase(world);

    // Narrow phase: exact collision tests
    for (int i = 0; i < potentialPairs.size(); i++) {
        for (int j = i + 1; j < potentialPairs.size(); j++) {
            if (testCollision(potentialPairs.get(i), potentialPairs.get(j))) {
                collisions.add(new CollisionPair(potentialPairs.get(i), potentialPairs.get(j)));
            }
        }
    }
}

private boolean testCollision(Entity a, Entity b) {
    // Sphere-sphere, box-box, sphere-box, etc.
    // Use SAT (Separating Axis Theorem) for boxes
}
```

## Raycasting

```java
public class PhysicsWorld {
    public RaycastHit raycast(Vector3f origin, Vector3f direction, float maxDistance) {
        RaycastHit closest = null;
        float closestDist = maxDistance;

        world.query(Collider.class, Transform3D.class).forEach(entity -> {
            Collider collider = entity.get(Collider.class);
            Transform3D transform = entity.get(Transform3D.class);

            float t = rayIntersect(origin, direction, collider, transform);
            if (t >= 0 && t < closestDist) {
                closest = new RaycastHit(entity, t, /* hit point, normal */);
                closestDist = t;
            }
        });

        return closest;
    }
}
```

---

## Exercises

1. Integrate JBullet for robust physics
2. Add trigger volumes (OnTriggerEnter/Exit events)
3. Implement character controller
4. Add joints (hinge, slider, spring)
5. Create physics materials (friction, bounciness)

---

**Previous:** [← Chapter 9 - Advanced Rendering](chapter-09-advanced-rendering.md)
**Next:** [Chapter 11 - Scripting →](chapter-11-scripting.md)
