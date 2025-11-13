# Appendix B: Linear Algebra for Games
## Math Refresher for 3D Graphics

Essential linear algebra concepts for game development.

---

## Vectors

### Vector3f - 3D Position/Direction

```java
Vector3f position = new Vector3f(10, 5, 0);  // x, y, z
Vector3f velocity = new Vector3f(1, 0, 0);   // moving right

// Operations
position.add(velocity);              // Move by velocity
position.sub(target);                // Direction from target
float distance = position.length();  // Distance from origin
position.normalize();                // Unit vector (length = 1)
```

### Dot Product

```java
float dot = v1.dot(v2);
// = v1.x * v2.x + v1.y * v2.y + v1.z * v2.z

// Uses:
// - Angle between vectors: cos(θ) = dot(a, b) / (|a| * |b|)
// - Projection: proj = dot(a, b) * b (if b is unit vector)
// - Check if facing: dot(forward, toTarget) > 0 (same direction)
```

**Example: Is enemy in front of player?**

```java
Vector3f playerForward = new Vector3f(0, 0, 1);
Vector3f toEnemy = enemy.position.sub(player.position, new Vector3f()).normalize();

if (playerForward.dot(toEnemy) > 0.5f) { // cos(60°) ≈ 0.5
    System.out.println("Enemy in front!");
}
```

### Cross Product

```java
Vector3f cross = v1.cross(v2, new Vector3f());
// Result is perpendicular to both v1 and v2

// Uses:
// - Calculate normal: cross(edge1, edge2)
// - Determine left/right: sign of cross product
// - Torque in physics: torque = radius × force
```

---

## Matrices

### Matrix4f - 4x4 Transformation Matrix

Represents translation, rotation, scale in a single matrix.

```java
Matrix4f transform = new Matrix4f()
    .translate(10, 5, 0)       // Move to (10, 5, 0)
    .rotateY((float)Math.toRadians(45))  // Rotate 45° around Y
    .scale(2, 2, 2);           // Double size
```

### Matrix Multiplication Order

**Important:** Matrices apply **right to left**!

```java
Matrix4f MVP = projection.mul(view).mul(model);
// Reads: model → view → projection
// Apply model transform, then view, then projection
```

### Common Matrices

**Identity (no transformation):**
```java
Matrix4f identity = new Matrix4f(); // Default
```

**Translation:**
```java
Matrix4f translation = new Matrix4f().translate(x, y, z);
```

**Rotation:**
```java
Matrix4f rotX = new Matrix4f().rotateX(angleRadians);
Matrix4f rotY = new Matrix4f().rotateY(angleRadians);
Matrix4f rotZ = new Matrix4f().rotateZ(angleRadians);

// Arbitrary axis
Matrix4f rot = new Matrix4f().rotate(angle, axisX, axisY, axisZ);
```

**Scale:**
```java
Matrix4f scale = new Matrix4f().scale(sx, sy, sz);
```

**Model Matrix (combine TRS):**
```java
Matrix4f model = new Matrix4f()
    .translate(position)
    .rotate(rotation)  // Use quaternion for rotation
    .scale(scale);
```

---

## Camera Matrices

### View Matrix (World → Camera Space)

```java
Matrix4f view = new Matrix4f().lookAt(
    cameraPos,    // Camera position
    targetPos,    // Look at point
    upVector      // Up direction (usually 0, 1, 0)
);
```

**FPS Camera:**
```java
Vector3f forward = new Vector3f(
    (float)(Math.cos(yaw) * Math.cos(pitch)),
    (float)Math.sin(pitch),
    (float)(Math.sin(yaw) * Math.cos(pitch))
).normalize();

Vector3f right = forward.cross(new Vector3f(0, 1, 0), new Vector3f()).normalize();
Vector3f up = right.cross(forward, new Vector3f());

Matrix4f view = new Matrix4f().lookAt(position, position.add(forward, new Vector3f()), up);
```

### Projection Matrix

**Perspective (3D):**
```java
Matrix4f projection = new Matrix4f().perspective(
    (float)Math.toRadians(fov),  // Field of view (e.g., 70°)
    aspectRatio,                  // width / height
    nearPlane,                    // e.g., 0.1
    farPlane                      // e.g., 1000.0
);
```

**Orthographic (2D, UI):**
```java
Matrix4f projection = new Matrix4f().ortho(
    left, right,   // e.g., 0, 1920
    bottom, top,   // e.g., 0, 1080
    near, far      // e.g., -1, 1
);
```

---

## Quaternions

Better than Euler angles (avoids gimbal lock).

### Quaternion Basics

```java
Quaternionf rotation = new Quaternionf().rotateY((float)Math.toRadians(45));

// Apply to vector
Vector3f rotated = rotation.transform(new Vector3f(1, 0, 0));

// Combine rotations (multiply)
Quaternionf combined = rot1.mul(rot2, new Quaternionf());

// Interpolate (smooth rotation)
Quaternionf result = rot1.slerp(rot2, t, new Quaternionf()); // t = 0..1
```

### Euler ↔ Quaternion

```java
// Euler to Quaternion
Quaternionf quat = new Quaternionf().rotateXYZ(pitch, yaw, roll);

// Quaternion to Euler (approximate)
Vector3f euler = quat.getEulerAnglesXYZ(new Vector3f());
```

---

## Common Operations

### Distance Between Points

```java
float distance = position1.distance(position2);

// Or manually
Vector3f diff = position2.sub(position1, new Vector3f());
float distance = diff.length();
```

### Lerp (Linear Interpolation)

```java
// Lerp between two values
float result = a + (b - a) * t;  // t = 0..1

// Lerp vectors
Vector3f result = a.lerp(b, t, new Vector3f());

// Smooth camera follow
camera.position.lerp(player.position, 0.1f * deltaTime);
```

### Normalize

```java
Vector3f direction = target.sub(origin, new Vector3f()).normalize();
// Length = 1, preserves direction
```

### Reflect

```java
// Reflect vector across normal (for bouncing)
Vector3f reflected = velocity.reflect(normal, new Vector3f());
```

---

## Coordinate Systems

### Right-Hand vs Left-Hand

**OpenGL/Vulkan (Right-Hand):**
- +X = right
- +Y = up
- +Z = towards camera (out of screen)

**DirectX (Left-Hand):**
- +X = right
- +Y = up
- +Z = into screen

JOML uses right-hand by default. Vulkan also uses right-hand.

### Local vs World Space

**Local:** Relative to object (e.g., "1 meter forward")
**World:** Absolute position (e.g., "(10, 5, 3)")

```java
// Transform local to world
Vector3f worldPos = modelMatrix.transformPosition(localPos);

// Transform world to local
Vector3f localPos = modelMatrix.invert(new Matrix4f()).transformPosition(worldPos);
```

---

## Performance Tips

### JOML Best Practices

**Reuse objects (avoid allocations):**
```java
// Bad (allocates every frame)
Vector3f result = new Vector3f();
position.add(velocity, result);

// Good (reuse)
private Vector3f tempVec = new Vector3f();
position.add(velocity, tempVec);
```

**Method chaining:**
```java
matrix.identity().translate(x, y, z).rotateY(angle).scale(2);
```

**Destination parameter:**
```java
// Most JOML methods have a "dest" overload
v1.add(v2, dest);  // Result stored in dest (no allocation)
```

---

## Further Reading

- **3D Math Primer**: [gamemath.com](http://gamemath.com/)
- **JOML Documentation**: [joml-ci.github.io/JOML](https://joml-ci.github.io/JOML/)
- **Quaternion Tutorial**: [youtube.com/watch?v=zjMuIxRvygQ](https://www.youtube.com/watch?v=zjMuIxRvygQ)

---

**[Back to README](README.md)**
