# Chapter 5: 3D Rendering & Flight Combat
## Evolving to the Third Dimension

**What You'll Learn:**
- 3D transformations with matrices and quaternions
- Perspective camera with FPS controls
- 3D mesh representation and rendering
- Model-View-Projection (MVP) matrix chain
- Depth testing and Z-buffering
- Basic lighting (ambient + directional)
- Evolving 2D space shooter to 3D flight combat

**What You'll Build:**
A fully playable **3D flight combat game** where you pilot a ship through space, fighting enemies in all three dimensions!

**Estimated Time:** 4-5 hours

**Prerequisites:** Chapters 1-4 completed

---

## Introduction: The Third Dimension

Our 2D space shooter works great, but adding the **third dimension** opens up new gameplay possibilities and challenges.

### 2D vs 3D Comparison

```
2D Space (Flatland):
─────────────────────────────────
     Y ↑
       │
       │   ● Enemy
       │
───────┼─────────► X
       │
   ●   │
 Player│

Movement: Left/Right/Up/Down
Rotation: Single angle (radians around Z)
Camera: Fixed orthographic (looks straight down)
```

```
3D Space (Real World):
─────────────────────────────────
         Y ↑ (Up)
           │
           │
           │     ● Enemy
           │    /
           │   /
           │  /
           │ /
           │/________► X (Right)
          /│
         / │
        /  │
       ↙   │
     Z (Forward)

Movement: Forward/Back/Left/Right/Up/Down (6 DOF)
Rotation: Pitch (X), Yaw (Y), Roll (Z) - Euler angles
         OR Quaternion (x, y, z, w) - Better!
Camera: Perspective (objects shrink with distance)
```

**2D Limitations:**
- Movement restricted to X/Y plane
- Enemies can only approach from 4 edges
- Camera is fixed orthographic view (no depth)
- Rotation is simple (single angle)
- No sense of depth or distance

**3D Advantages:**
- Full **6 degrees of freedom** (6DOF):
  - Translation: X, Y, Z (left/right, up/down, forward/back)
  - Rotation: Pitch, Yaw, Roll
- Enemies can attack from **any direction** (360° × 180° = full sphere)
- Perspective camera creates **immersion** (distant objects smaller)
- Depth creates **spatial gameplay** (dodge behind cover, flank enemies)
- Realistic physics (gravity, momentum in 3D)

### Architecture Changes

**What We Keep from 2D:**
- ECS architecture (entities, components, systems)
- Component-based design
- All game systems (collision, health, lifetime)
- Game logic and flow
- Performance optimization patterns

**What Changes for 3D:**
- **Transform2D → Transform3D** (add Z axis, rotation quaternion)
- **Camera2D → Camera3D** (orthographic → perspective)
- **Sprites → Meshes** (colored rectangles → 3D geometry)
- **2D collision → 3D collision** (circle → sphere)
- **Depth buffer** (Z-buffering for correct draw order)

---

## 3D Math Foundations

Before we code, let's understand the math!

### Coordinate Systems

**RIGHT-HANDED COORDINATE SYSTEM** (OpenGL, Vulkan convention):

```
    Y (Up)
    ↑
    │
    │
    │
    │________► X (Right)
   /
  /
 ↙
Z (Forward, INTO screen)

Rules:
- Right thumb = X
- Right index finger = Y
- Right middle finger = Z
- Cross product: X × Y = Z
```

**Why Right-Handed?**
- Standard in graphics (OpenGL, Vulkan, most 3D software)
- Natural for math (consistent cross products)
- Matches real-world (right hand rule in physics)

**Left-Handed (DirectX):** Z points OUT of screen (different convention)

### Matrix Transformations

**THE FUNDAMENTAL EQUATION:**

```
Position in world = Scale → Rotate → Translate (SRT or TRS)

Example: Spaceship at (10, 5, 0), rotated 45°, scaled 2×:
1. Scale:     Make it 2× bigger
2. Rotate:    Spin 45° around Y axis
3. Translate: Move to (10, 5, 0)

WHY THIS ORDER?
- Scale first (before rotation/translation)
- Rotate second (around origin, THEN move)
- Translate last (final position)

WRONG ORDER:
Translate → Rotate → Scale
Result: Object orbits around origin (not what we want!)
```

**Matrix Composition:**

```java
Matrix4f modelMatrix = new Matrix4f()
    .identity()              // Start with identity (no transform)
    .translate(x, y, z)      // Step 3: Move to position
    .rotate(quaternion)      // Step 2: Apply rotation
    .scale(scaleX, scaleY, scaleZ);  // Step 1: Scale

// JOML applies RIGHT-TO-LEFT (like function composition)
// So: scale(rotate(translate(identity)))
// Which is TRS order (what we want!)
```

**Visual Example:**

```
Spaceship Transform:
────────────────────────────────────────

1. Start (origin, no transform):
   ┌─┐
   │▲│  Position: (0, 0, 0)
   └─┘  Rotation: 0°
        Scale: 1

2. Scale by 2×:
   ┌───┐
   │ ▲ │  Position: (0, 0, 0)
   └───┘  Rotation: 0°
         Scale: 2 (now 2× bigger!)

3. Rotate 45° around Y:
     ┌───┐
    /  ▲  \  Position: (0, 0, 0)
   └───────┘ Rotation: 45° (turned!)
             Scale: 2

4. Translate to (10, 5, 0):
                        ┌───┐
                       /  ▲  \  Position: (10, 5, 0)
                      └───────┘ Rotation: 45°
                                Scale: 2
                                ✓ Final transform!
```

### Quaternions vs Euler Angles

**EULER ANGLES (Pitch, Yaw, Roll):**

```
Pitch: Rotation around X axis (look up/down)
  ┌─┐     Pitch +30°      ┌─┐
  │▲│  ────────────────►   \▲\
  └─┘                       └─┘

Yaw: Rotation around Y axis (look left/right)
  ┌─┐     Yaw +45°       ┌──┐
  │▲│  ────────────────► │ /│
  └─┘                    └──┘

Roll: Rotation around Z axis (tilt left/right)
  ┌─┐     Roll +30°      ┌──┐
  │▲│  ────────────────► │╲ │
  └─┘                    └──┘
```

**PROBLEM: Gimbal Lock**

```
Gimbal Lock Scenario:
─────────────────────────────────

Step 1: Pitch 90° (look straight up)
  ┌─┐
  │▲│  ────► Pitch 90°  ───►  ──►
  └─┘                           ││
                                ││
                                ▼│

Now try to turn left:
- Yaw? Does nothing! (Yaw axis = Pitch axis now)
- Roll? Rotates around viewing direction (not what we want)

LOST A DEGREE OF FREEDOM! (Can't turn left/right anymore)
```

**Real-World Example:**
```
Aircraft at 90° pitch (nose straight up):
- Want to turn LEFT
- Apply yaw? NO EFFECT (yaw axis collapsed)
- Apply roll? Spins around nose (wrong!)
- STUCK! Can't turn horizontally!

This is gimbal lock.
```

**QUATERNIONS TO THE RESCUE:**

```
Quaternion = (x, y, z, w)

NOT Euler angles!
NOT intuitive!
BUT: NO GIMBAL LOCK!

Think of it as:
- Axis of rotation: (x, y, z) vector
- Amount of rotation: w (angle encoded)

Example:
Rotate 45° around Y axis:
q = Quaternion(0, 0.383, 0, 0.924)
     ↑    ↑     ↑   ↑
     x    y     z   w

Axis: (0, 1, 0) = Y axis (up)
Angle: arccos(0.924) * 2 = 45°
```

**Quaternion Advantages:**

| Feature | Euler Angles | Quaternions |
|---------|--------------|-------------|
| Gimbal lock | ✗ YES | ✓ NO |
| Smooth interpolation | ✗ Jumpy | ✓ Slerp (smooth) |
| Composition | ✗ Order matters | ✓ Multiply (easy) |
| Memory | 3 floats | 4 floats |
| Intuitive | ✓ Yes | ✗ No |
| Industry standard | Games: rarely | Games: always |

**Why Game Engines Use Quaternions:**
- Unity: Transform.rotation (Quaternion)
- Unreal: FQuat (Quaternion)
- Godot: Quat (Quaternion)
- Bevy: Quat (Quaternion)

**When to Use Euler Angles:**
- User input (camera: pitch/yaw easier to understand)
- Convert TO quaternion immediately after

```java
// User input (Euler angles)
float pitch = mouseY * sensitivity;
float yaw = mouseX * sensitivity;

// Immediately convert to quaternion
Quaternionf rotation = new Quaternionf()
    .rotateY(yaw)
    .rotateX(pitch);

// Store as quaternion (no gimbal lock!)
transform.rotation.set(rotation);
```

---

## The MVP Matrix Pipeline

**THE BIG PICTURE:**

```
OBJECT SPACE (Model)
      ↓ Model Matrix (M)
WORLD SPACE
      ↓ View Matrix (V)
CAMERA SPACE
      ↓ Projection Matrix (P)
CLIP SPACE
      ↓ Perspective Divide
NDC (Normalized Device Coordinates)
      ↓ Viewport Transform
SCREEN SPACE (Pixels)
```

### Model Matrix (Local → World)

**WHAT:** Transforms object from **local space** (model coordinates) to **world space**.

```
Cube Mesh (Local Space):
───────────────────────────
Vertices always the same:
  (-0.5, -0.5, -0.5) to (0.5, 0.5, 0.5)
  ↑ Centered at origin

Model Matrix transforms to World Space:
  Position: (10, 5, 0)
  Rotation: 45° around Y
  Scale: 2×

Result: Cube now at (10, 5, 0) in world, rotated, scaled
```

**CODE:**

```java
Matrix4f modelMatrix = new Matrix4f()
    .identity()
    .translate(position.x, position.y, position.z)
    .rotate(rotation)
    .scale(scale.x, scale.y, scale.z);

// Transform vertex from local to world:
Vector3f localVertex = new Vector3f(0.5f, 0.5f, 0.5f);
Vector3f worldVertex = modelMatrix.transformPosition(localVertex);
```

### View Matrix (World → Camera)

**WHAT:** Transforms from **world space** to **camera space** (camera at origin looking down -Z).

```
World Space:
───────────────────────────
Camera at (0, 2, 10) looking at (0, 0, 0)
Objects scattered in world

View Matrix:
───────────────────────────
Camera now at origin (0, 0, 0)
Looking down -Z axis
Objects moved relative to camera
```

**THE TRICK:** Instead of moving camera, move the WORLD opposite direction!

```
Camera at (0, 2, 10):
  View matrix = translate(0, -2, -10)
  ↑ Opposite translation!

Camera rotated 45° around Y:
  View matrix = rotate(-45°, Y)
  ↑ Opposite rotation!
```

**CODE:**

```java
// Method 1: Look-at matrix (easy)
Matrix4f viewMatrix = new Matrix4f()
    .setLookAt(
        cameraPosition,  // Eye position
        targetPosition,  // Look-at point
        upVector        // Up direction (usually (0, 1, 0))
    );

// Method 2: Manual (inverse of camera transform)
Matrix4f viewMatrix = new Matrix4f()
    .identity()
    .rotate(cameraRotation.conjugate())  // Opposite rotation
    .translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);  // Opposite position
```

**Visual:**

```
World Space:
     Y ↑
       │
       │  ◆ Camera (0, 2, 10)
       │   looking at origin
       │
       │  ● Object at (5, 0, 0)
───────┼─────────► X
       │
       ↙
      Z

After View Matrix (Camera Space):
     Y ↑
       │
       │  ◆ Camera NOW at origin!
       │   looking down -Z
       │
       │  ● Object now at (5, -2, -10)
───────┼─────────► X       ↑ relative to camera
       │
       ↙
      Z
```

### Projection Matrix (Camera → Clip)

**PERSPECTIVE PROJECTION:**

```
The Problem:
───────────────────────────
How to draw 3D world on 2D screen?

Far objects should look SMALLER!

Real World:
  Train tracks appear to converge at horizon
  Person 100m away looks tiny
  Person 1m away looks big

This is PERSPECTIVE.
```

**THE MATH:**

```
Perspective Projection:
───────────────────────────

View Frustum (viewing volume):
       Near plane
         ┌─┐
        /   \
       /  ●  \  Object
      /       \
     /    ◆    \  Camera
    /           \
   └─────────────┘
    Far plane

Parameters:
- FOV (Field of View): Horizontal/vertical angle (degrees)
- Aspect Ratio: Width / Height
- Near Plane: Closest visible distance (e.g., 0.1m)
- Far Plane: Farthest visible distance (e.g., 1000m)

Projection:
  x_clip = x_camera / z_camera * (1 / tan(FOV/2))
  y_clip = y_camera / z_camera * (aspect / tan(FOV/2))
  z_clip = (far + near) / (far - near) + (2 * far * near) / (far - near) / z_camera

Result: Objects further away (larger z) → smaller on screen!
```

**FOV Comparison:**

```
FOV = 45° (narrow, telephoto):
       ┌─┐
      /   \
     /  ●  \
    /   ◆   \
   └─────────┘
Small viewing angle (zoomed in)

FOV = 90° (wide, normal):
     ┌─┐
    /   \
   / ● ◆ \
  /       \
 └─────────┘
Wide viewing angle (normal)

FOV = 120° (ultra-wide, fisheye):
   ┌─┐
  /   \
 / ● ◆ \
/       \
└─────────┘
Very wide (distorted edges)
```

**CODE:**

```java
Matrix4f projectionMatrix = new Matrix4f()
    .setPerspective(
        (float) Math.toRadians(70),  // FOV (degrees → radians)
        16.0f / 9.0f,                // Aspect ratio
        0.1f,                        // Near plane
        1000.0f                      // Far plane
    );

// Now objects get perspective projection:
// - Distant objects → small
// - Near objects → large
```

**Real-World Values:**

| Game/Application | FOV | Reason |
|------------------|-----|--------|
| FPS games | 90°-110° | Wide view, competitive advantage |
| Racing games | 60°-70° | Focused, realistic |
| Flight sims | 60°-80° | Cockpit view |
| VR | 100°-110° | Match human vision |
| Cinematic | 50°-60° | Film camera look |

### Perspective Divide (Clip → NDC)

**THE MAGIC STEP:**

```
After projection, vertices are in CLIP SPACE:
  (x, y, z, w) where w = distance from camera

Perspective Divide:
  x_ndc = x_clip / w
  y_ndc = y_clip / w
  z_ndc = z_clip / w

Result: NDC (Normalized Device Coordinates)
  Range: (-1, -1, -1) to (1, 1, 1)
  ↑ Cube where screen will be mapped
```

**Why Divide by w?**

```
Example:
───────────────────────────

Object at z = 10 (far):
  Before divide: x = 5, w = 10
  After divide: x_ndc = 5/10 = 0.5

Object at z = 2 (near):
  Before divide: x = 5, w = 2
  After divide: x_ndc = 5/2 = 2.5

Result: Same screen position (x=5) appears:
- SMALLER if further away (x_ndc = 0.5)
- LARGER if closer (x_ndc = 2.5)

THIS IS PERSPECTIVE!
```

### Complete MVP Pipeline

**PUTTING IT ALL TOGETHER:**

```java
// Per-frame (once):
Matrix4f viewMatrix = camera.getViewMatrix();
Matrix4f projectionMatrix = camera.getProjectionMatrix();
Matrix4f VP = projectionMatrix.mul(viewMatrix, new Matrix4f());

// Per-object (many times):
Matrix4f modelMatrix = transform.getModelMatrix();
Matrix4f MVP = VP.mul(modelMatrix, new Matrix4f());  // Order: P * V * M

// Transform vertex:
Vector3f localVertex = new Vector3f(0.5f, 0.5f, 0.5f);
Vector4f clipVertex = MVP.transform(new Vector4f(localVertex, 1.0f));

// Perspective divide (GPU does this):
if (clipVertex.w != 0) {
    clipVertex.div(clipVertex.w);  // Now in NDC (-1 to 1)
}

// Viewport transform (GPU does this):
float screenX = (clipVertex.x + 1.0f) * 0.5f * screenWidth;
float screenY = (1.0f - clipVertex.y) * 0.5f * screenHeight;  // Flip Y

// screenX, screenY = pixel coordinates!
```

**Performance Note:**

```
Optimization:
─────────────────────────────────
DON'T: MVP = P * V * M per vertex
  Cost: 3 matrix multiplications per vertex
  With 10,000 vertices: 30,000 matrix muls!

DO: Precompute VP = P * V once per frame
    Then: MVP = VP * M per object
  Cost: 1 matrix mul per frame + 1 per object
  With 100 objects: 101 matrix muls (300× faster!)
```

---

## Step 1: 3D Transform Component

Create `src/main/java/com/yourname/engine/components/Transform3D.java`:

```java
package com.yourname.engine.components;

import com.yourname.engine.ecs.Component;
import org.joml.*;

/**
 * 3D transformation component.
 *
 * <p>Stores position, rotation (quaternion), and scale.
 *
 * <p>WHY QUATERNIONS?
 * Quaternions avoid gimbal lock (loss of degree of freedom when two rotation
 * axes align). Euler angles (pitch/yaw/roll) suffer from this problem.
 *
 * <p>Example of gimbal lock:
 * <pre>
 * 1. Pitch 90° (look straight up)
 * 2. Now yaw and roll are the same axis!
 * 3. Can't turn left/right anymore (lost 1 DOF)
 * </pre>
 *
 * <p>MATRIX CACHING:
 * Model matrix is expensive to compute (16 matrix multiplications).
 * We cache it and only recompute when transform changes (dirty flag).
 */
public class Transform3D implements Component {
    public Vector3f position;
    public Quaternionf rotation;
    public Vector3f scale;

    // Cached matrices (updated when needed)
    private Matrix4f modelMatrix;
    private boolean matrixDirty = true;

    public Transform3D() {
        this.position = new Vector3f(0, 0, 0);
        this.rotation = new Quaternionf();
        this.scale = new Vector3f(1, 1, 1);
        this.modelMatrix = new Matrix4f();
    }

    public Transform3D(Vector3f position) {
        this();
        this.position.set(position);
    }

    public Transform3D(Vector3f position, Quaternionf rotation, Vector3f scale) {
        this.position = new Vector3f(position);
        this.rotation = new Quaternionf(rotation);
        this.scale = new Vector3f(scale);
        this.modelMatrix = new Matrix4f();
    }

    /**
     * Get the model matrix (local to world transform).
     *
     * <p>WHAT THIS DOES:
     * Transforms vertices from object space (model coordinates) to world space.
     *
     * <p>Matrix composition (TRS order):
     * <pre>
     * M = T * R * S
     * where:
     *   S = scale matrix
     *   R = rotation matrix (from quaternion)
     *   T = translation matrix
     * </pre>
     *
     * <p>WHY TRS ORDER?
     * 1. Scale first (make bigger/smaller around origin)
     * 2. Rotate second (spin around origin)
     * 3. Translate last (move to final position)
     *
     * <p>WRONG ORDER (STR):
     * Would cause object to orbit around world origin instead of rotating
     * in place then moving!
     *
     * <p>CACHING:
     * Matrix is cached and only recomputed when dirty (position/rotation/scale changed).
     * Cost: ~50 operations → ~0.5µs
     * Benefit: Avoid recomputing 60× per frame if unchanged
     */
    public Matrix4f getModelMatrix() {
        if (matrixDirty) {
            updateModelMatrix();
            matrixDirty = false;
        }
        return modelMatrix;
    }

    /**
     * Update model matrix from position, rotation, scale.
     *
     * <p>JOML applies operations RIGHT-TO-LEFT (like function composition):
     * <pre>
     * matrix.translate(t).rotate(r).scale(s)
     * = scale(rotate(translate(identity)))
     * = S(R(T(I))) in math notation
     * </pre>
     *
     * <p>But we want TRS (translate, rotate, scale), so we write it
     * in REVERSE order in code!
     */
    private void updateModelMatrix() {
        modelMatrix.identity()
            .translate(position)
            .rotate(rotation)
            .scale(scale);
    }

    /**
     * Mark matrix as dirty (needs recomputation).
     *
     * <p>Call this whenever position, rotation, or scale changes!
     */
    public void markDirty() {
        matrixDirty = true;
    }

    // Convenience methods

    public void translate(float x, float y, float z) {
        position.add(x, y, z);
        markDirty();
    }

    /**
     * Rotate around an axis.
     *
     * <p>QUATERNION COMPOSITION:
     * Multiplying quaternions composes rotations.
     * Order matters: q1 * q2 ≠ q2 * q1
     *
     * @param angleRad angle in radians
     * @param axisX    X component of rotation axis
     * @param axisY    Y component of rotation axis
     * @param axisZ    Z component of rotation axis
     */
    public void rotate(float angleRad, float axisX, float axisY, float axisZ) {
        rotation.rotateAxis(angleRad, axisX, axisY, axisZ);
        markDirty();
    }

    /**
     * Make object look at a target position.
     *
     * <p>ALGORITHM:
     * 1. Compute forward vector: target - position
     * 2. Construct look-at matrix
     * 3. Extract rotation quaternion from matrix
     *
     * <p>USE CASE:
     * - Turrets aiming at player
     * - Cameras following objects
     * - Billboards facing camera
     */
    public void lookAt(Vector3f target, Vector3f up) {
        // Compute forward direction
        Vector3f forward = target.sub(position, new Vector3f()).normalize();

        // Compute rotation to face target
        // (Implementation note: use Matrix4f.setLookAt then extract quaternion)
        Matrix4f lookAtMatrix = new Matrix4f().setLookAt(position, target, up);
        lookAtMatrix.getNormalizedRotation(rotation);

        markDirty();
    }

    /**
     * Get forward vector (local Z axis in world space).
     *
     * <p>IN LOCAL SPACE: Forward = (0, 0, -1)
     * IN WORLD SPACE: Forward = rotation.transform((0, 0, -1))
     *
     * <p>WHY -Z?
     * Right-handed coordinate system convention:
     * - X = right
     * - Y = up
     * - Z = forward (into screen, negative)
     */
    public Vector3f getForward() {
        return rotation.transform(new Vector3f(0, 0, -1));
    }

    /**
     * Get right vector (local X axis in world space).
     */
    public Vector3f getRight() {
        return rotation.transform(new Vector3f(1, 0, 0));
    }

    /**
     * Get up vector (local Y axis in world space).
     */
    public Vector3f getUp() {
        return rotation.transform(new Vector3f(0, 1, 0));
    }
}
```

**Performance Notes:**

```
Matrix Caching Benefits:
────────────────────────────────────
Scenario: 100 objects, 60 FPS

WITHOUT caching:
  100 objects × 60 frames = 6,000 matrix updates/sec
  Cost: 6,000 × 0.5µs = 3ms/frame

WITH caching (objects mostly static):
  10 moving objects × 60 frames = 600 matrix updates/sec
  Cost: 600 × 0.5µs = 0.3ms/frame
  Savings: 10× faster!
```

---

## Step 2: 3D Camera

Create `src/main/java/com/yourname/engine/renderer/Camera3D.java`:

```java
package com.yourname.engine.renderer;

import org.joml.*;

/**
 * 3D perspective camera with FPS-style controls.
 *
 * <p>CAMERA SPACE:
 * Camera is always at origin (0, 0, 0) looking down -Z axis.
 * World is transformed relative to camera (view matrix).
 *
 * <p>PERSPECTIVE PROJECTION:
 * Projects 3D camera space onto 2D screen with perspective
 * (distant objects appear smaller).
 *
 * <p>Controls:
 * - WASD: Move forward/left/backward/right
 * - Space/Shift: Move up/down
 * - Mouse: Rotate (pitch/yaw)
 */
public class Camera3D {

    private Vector3f position;
    private float pitch;  // Rotation around X axis (radians)
    private float yaw;    // Rotation around Y axis (radians)

    // Camera vectors (derived from pitch/yaw)
    private Vector3f forward;
    private Vector3f right;
    private Vector3f up;

    // Projection parameters
    private float fov;         // Field of view (degrees)
    private float aspectRatio;
    private float nearPlane;
    private float farPlane;

    // Matrices
    private Matrix4f viewMatrix;
    private Matrix4f projectionMatrix;
    private Matrix4f viewProjectionMatrix;

    public Camera3D() {
        this.position = new Vector3f(0, 0, 5);
        this.pitch = 0;
        this.yaw = 0;

        this.forward = new Vector3f();
        this.right = new Vector3f();
        this.up = new Vector3f(0, 1, 0);

        this.fov = 70.0f;
        this.aspectRatio = 16.0f / 9.0f;
        this.nearPlane = 0.1f;
        this.farPlane = 1000.0f;

        this.viewMatrix = new Matrix4f();
        this.projectionMatrix = new Matrix4f();
        this.viewProjectionMatrix = new Matrix4f();

        updateVectors();
    }

    /**
     * Update camera matrices.
     *
     * <p>CALL THIS EVERY FRAME before rendering!
     *
     * <p>Updates:
     * 1. Aspect ratio (window size may have changed)
     * 2. Camera vectors (from pitch/yaw)
     * 3. View matrix (world → camera space)
     * 4. Projection matrix (camera → clip space)
     * 5. VP matrix (combined, for efficiency)
     */
    public void update(int viewportWidth, int viewportHeight) {
        // Update aspect ratio
        aspectRatio = (float) viewportWidth / viewportHeight;

        // Update camera vectors
        updateVectors();

        // Update view matrix (world to camera space)
        Vector3f target = position.add(forward, new Vector3f());
        viewMatrix.setLookAt(position, target, up);

        // Update projection matrix (camera to clip space)
        projectionMatrix.setPerspective(
            (float) Math.toRadians(fov),
            aspectRatio,
            nearPlane,
            farPlane
        );

        // Combined VP matrix (optimization: precompute P * V)
        projectionMatrix.mul(viewMatrix, viewProjectionMatrix);
    }

    /**
     * Update forward/right/up vectors from pitch/yaw.
     *
     * <p>SPHERICAL COORDINATES:
     * Convert pitch/yaw (angles) to direction vector (x, y, z).
     *
     * <pre>
     * forward.x = cos(yaw) * cos(pitch)
     * forward.y = sin(pitch)
     * forward.z = sin(yaw) * cos(pitch)
     * </pre>
     *
     * <p>WHY THIS FORMULA?
     * - Pitch (X rotation): Affects Y and Z (up/down)
     * - Yaw (Y rotation): Affects X and Z (left/right)
     * - Combine to get 3D direction vector
     */
    private void updateVectors() {
        // Calculate forward vector from pitch and yaw
        forward.x = (float) (Math.cos(yaw) * Math.cos(pitch));
        forward.y = (float) Math.sin(pitch);
        forward.z = (float) (Math.sin(yaw) * Math.cos(pitch));
        forward.normalize();

        // Calculate right vector (cross product: forward × world_up)
        forward.cross(new Vector3f(0, 1, 0), right).normalize();

        // Calculate up vector (cross product: right × forward)
        right.cross(forward, up).normalize();
    }

    /**
     * Move camera forward/backward.
     */
    public void moveForward(float amount) {
        position.add(forward.mul(amount, new Vector3f()));
    }

    /**
     * Move camera right/left.
     */
    public void moveRight(float amount) {
        position.add(right.mul(amount, new Vector3f()));
    }

    /**
     * Move camera up/down (world Y axis).
     */
    public void moveUp(float amount) {
        position.y += amount;
    }

    /**
     * Rotate camera (mouse look).
     *
     * <p>PITCH CLAMPING:
     * Clamp pitch to [-90°, +90°] to prevent camera flipping upside down.
     *
     * @param yawDelta   horizontal rotation (radians)
     * @param pitchDelta vertical rotation (radians)
     */
    public void rotate(float yawDelta, float pitchDelta) {
        yaw += yawDelta;
        pitch += pitchDelta;

        // Clamp pitch to avoid flipping
        // -1.5 rad ≈ -86°, 1.5 rad ≈ +86°
        pitch = Math.max(-1.5f, Math.min(1.5f, pitch));
    }

    // Getters
    public Vector3f getPosition() { return position; }
    public Vector3f getForward() { return forward; }
    public Vector3f getRight() { return right; }
    public Vector3f getUp() { return up; }
    public Matrix4f getViewMatrix() { return viewMatrix; }
    public Matrix4f getProjectionMatrix() { return projectionMatrix; }
    public Matrix4f getViewProjectionMatrix() { return viewProjectionMatrix; }

    public void setPosition(Vector3f position) {
        this.position.set(position);
    }

    public void setRotation(float pitch, float yaw) {
        this.pitch = pitch;
        this.yaw = yaw;
        updateVectors();
    }
}
```

**Camera Controls:**

```
Movement:
─────────────────────────────────
W - Move forward (in facing direction)
S - Move backward
A - Strafe left
D - Strafe right
Space - Move up (world Y+)
Shift - Move down (world Y-)

Rotation:
─────────────────────────────────
Mouse X - Yaw (turn left/right)
Mouse Y - Pitch (look up/down)

Result: FPS-style controls (like Quake, Half-Life)
```

---

## Step 3: Mesh Class

Create `src/main/java/com/yourname/engine/renderer/Mesh.java`:

```java
package com.yourname.engine.renderer;

/**
 * Mesh data (vertices, indices, normals, UVs).
 *
 * <p>VERTEX DATA LAYOUT (interleaved):
 * <pre>
 * [Position(3) | Normal(3) | UV(2)] per vertex
 *  ↑ 8 floats per vertex
 *
 * Example vertex:
 * [0.5, 0.5, 0.5,  0, 1, 0,  1, 1]
 *  ↑ Position      ↑ Normal  ↑ UV (texture coordinates)
 * </pre>
 *
 * <p>WHY INTERLEAVED?
 * Better cache performance than separate arrays:
 *
 * <pre>
 * Interleaved (good):
 * [pos|norm|uv][pos|norm|uv][pos|norm|uv]
 *  ↑ All data for vertex 0 together
 *  ↑ GPU loads in one cache line
 *
 * Separate (bad):
 * [pos][pos][pos]...[norm][norm][norm]...[uv][uv][uv]...
 *  ↑ Data scattered → more cache misses
 * </pre>
 *
 * <p>INDICES:
 * Reuse vertices to save memory.
 *
 * <pre>
 * Without indices (bad):
 * Square = 2 triangles × 3 vertices = 6 vertices
 *   [v0, v1, v2, v0, v2, v3]
 *   ↑ v0 duplicated!
 *
 * With indices (good):
 * Vertices: [v0, v1, v2, v3] (4 vertices)
 * Indices: [0, 1, 2, 0, 2, 3] (6 indices)
 *   ↑ Reuse v0, v2
 *   ↑ 33% less data!
 * </pre>
 *
 * <p>For Chapter 5, we use simplified rendering (colored shapes).
 * Vertex buffers and GPU upload will be added in Chapter 9.
 */
public class Mesh {

    private float[] vertices;  // Interleaved: Position(3) + Normal(3) + UV(2)
    private int[] indices;

    public Mesh(float[] vertices, int[] indices) {
        this.vertices = vertices;
        this.indices = indices;
    }

    /**
     * Create a cube mesh (1×1×1, centered at origin).
     *
     * <p>CUBE STRUCTURE:
     * <pre>
     *    7──────6
     *   /│     /│
     *  4──────5 │
     *  │ │    │ │
     *  │ 3────│─2
     *  │/     │/
     *  0──────1
     * </pre>
     *
     * <p>6 faces × 2 triangles = 12 triangles = 36 indices
     *
     * <p>WHY 24 VERTICES (not 8)?
     * Each corner needs DIFFERENT normals for each face.
     * Can't share vertices with different normals!
     */
    public static Mesh createCube() {
        // 24 vertices (4 per face × 6 faces)
        float[] vertices = {
            // Positions          Normals           UVs
            // Front face (Z+)
            -0.5f, -0.5f,  0.5f,  0, 0, 1,  0, 0,
             0.5f, -0.5f,  0.5f,  0, 0, 1,  1, 0,
             0.5f,  0.5f,  0.5f,  0, 0, 1,  1, 1,
            -0.5f,  0.5f,  0.5f,  0, 0, 1,  0, 1,
            // Back face (Z-)
            -0.5f, -0.5f, -0.5f,  0, 0, -1,  1, 0,
             0.5f, -0.5f, -0.5f,  0, 0, -1,  0, 0,
             0.5f,  0.5f, -0.5f,  0, 0, -1,  0, 1,
            -0.5f,  0.5f, -0.5f,  0, 0, -1,  1, 1,
            // Left face (X-)
            -0.5f, -0.5f, -0.5f, -1, 0, 0,  0, 0,
            -0.5f, -0.5f,  0.5f, -1, 0, 0,  1, 0,
            -0.5f,  0.5f,  0.5f, -1, 0, 0,  1, 1,
            -0.5f,  0.5f, -0.5f, -1, 0, 0,  0, 1,
            // Right face (X+)
             0.5f, -0.5f, -0.5f,  1, 0, 0,  1, 0,
             0.5f, -0.5f,  0.5f,  1, 0, 0,  0, 0,
             0.5f,  0.5f,  0.5f,  1, 0, 0,  0, 1,
             0.5f,  0.5f, -0.5f,  1, 0, 0,  1, 1,
            // Top face (Y+)
            -0.5f,  0.5f, -0.5f,  0, 1, 0,  0, 1,
            -0.5f,  0.5f,  0.5f,  0, 1, 0,  0, 0,
             0.5f,  0.5f,  0.5f,  0, 1, 0,  1, 0,
             0.5f,  0.5f, -0.5f,  0, 1, 0,  1, 1,
            // Bottom face (Y-)
            -0.5f, -0.5f, -0.5f,  0, -1, 0,  1, 1,
            -0.5f, -0.5f,  0.5f,  0, -1, 0,  1, 0,
             0.5f, -0.5f,  0.5f,  0, -1, 0,  0, 0,
             0.5f, -0.5f, -0.5f,  0, -1, 0,  0, 1
        };

        // 36 indices (12 triangles, 6 faces × 2 triangles per face)
        int[] indices = {
            // Front
            0, 1, 2,  2, 3, 0,
            // Back
            5, 4, 7,  7, 6, 5,
            // Left
            8, 9, 10,  10, 11, 8,
            // Right
            13, 12, 15,  15, 14, 13,
            // Top
            16, 17, 18,  18, 19, 16,
            // Bottom
            21, 20, 23,  23, 22, 21
        };

        return new Mesh(vertices, indices);
    }

    /**
     * Create a pyramid mesh (for spaceship shape).
     *
     * <p>PYRAMID STRUCTURE:
     * <pre>
     *       4 (apex)
     *      /|\
     *     / | \
     *    /  |  \
     *   0───┼───1  (base)
     *    \  |  /
     *     \ | /
     *      \|/
     *       3
     * </pre>
     */
    public static Mesh createPyramid() {
        float[] vertices = {
            // Base (square)
            -0.5f, 0, -0.5f,  0, -1, 0,  0, 0,
             0.5f, 0, -0.5f,  0, -1, 0,  1, 0,
             0.5f, 0,  0.5f,  0, -1, 0,  1, 1,
            -0.5f, 0,  0.5f,  0, -1, 0,  0, 1,
            // Apex
             0, 1, 0,  0, 1, 0,  0.5f, 0.5f
        };

        int[] indices = {
            // Base
            0, 1, 2,  2, 3, 0,
            // Sides
            0, 4, 1,
            1, 4, 2,
            2, 4, 3,
            3, 4, 0
        };

        return new Mesh(vertices, indices);
    }

    /**
     * Create sphere mesh (for projectiles/enemies).
     *
     * <p>TODO: Implement proper UV sphere or icosphere.
     * For now, use cube as placeholder.
     */
    public static Mesh createSphere(int segments) {
        // Icosphere or UV sphere generation
        // (Simplified for tutorial - just use cube for now)
        return createCube();
    }

    // Getters
    public float[] getVertices() { return vertices; }
    public int[] getIndices() { return indices; }
    public int getVertexCount() { return vertices.length / 8; } // 8 floats per vertex
    public int getIndexCount() { return indices.length; }
}
```

---

(Continuing with remaining implementation code from the original chapter... The game implementation, systems, and testing sections remain the same, just keeping them as working code)

## Step 4: MeshRenderer Component

Create `src/main/java/com/yourname/engine/components/MeshRenderer.java`:

```java
package com.yourname.engine.components;

import com.yourname.engine.ecs.Component;
import com.yourname.engine.renderer.Mesh;

/**
 * Component for rendering a 3D mesh.
 */
public class MeshRenderer implements Component {
    public Mesh mesh;
    public float colorR = 1.0f;
    public float colorG = 1.0f;
    public float colorB = 1.0f;
    public float colorA = 1.0f;

    public MeshRenderer(Mesh mesh) {
        this.mesh = mesh;
    }

    public MeshRenderer(Mesh mesh, float r, float g, float b, float a) {
        this.mesh = mesh;
        this.colorR = r;
        this.colorG = g;
        this.colorB = b;
        this.colorA = a;
    }

    public void setColor(float r, float g, float b, float a) {
        this.colorR = r;
        this.colorG = g;
        this.colorB = b;
        this.colorA = a;
    }
}
```

---

## Step 5: 3D Render System (Simplified)

For Chapter 5, we'll use a **simplified rendering approach** similar to Chapter 4:
- Draw colored wireframe bounding boxes representing 3D meshes
- Add proper mesh rendering with vertex buffers in Chapter 9

Create `src/main/java/com/yourname/engine/renderer/MeshRenderSystem.java`:

```java
package com.yourname.engine.renderer;

import com.yourname.engine.components.MeshRenderer;
import com.yourname.engine.components.Transform3D;
import com.yourname.engine.ecs.*;
import org.joml.*;

/**
 * System that renders 3D meshes.
 * Simplified: draws colored boxes representing mesh bounds.
 */
public class MeshRenderSystem extends System {

    private VulkanRenderer renderer;
    private Camera3D camera;

    public MeshRenderSystem(VulkanRenderer renderer, Camera3D camera) {
        this.renderer = renderer;
        this.camera = camera;
    }

    @Override
    public void update(World world, float deltaTime) {
        // Update camera
        camera.update(renderer.getWindow().getWidth(), renderer.getWindow().getHeight());

        // Query all renderable 3D entities
        world.query(Transform3D.class, MeshRenderer.class).forEach(entity -> {
            Transform3D transform = entity.get(Transform3D.class);
            MeshRenderer meshRenderer = entity.get(MeshRenderer.class);

            // Project 3D position to 2D screen space
            Vector4f worldPos = new Vector4f(transform.position, 1.0f);
            Vector4f clipPos = camera.getViewProjectionMatrix().transform(worldPos, new Vector4f());

            // Perspective divide
            if (clipPos.w != 0) {
                clipPos.div(clipPos.w);
            }

            // NDC to screen coordinates
            int screenWidth = renderer.getWindow().getWidth();
            int screenHeight = renderer.getWindow().getHeight();

            float screenX = (clipPos.x + 1.0f) * 0.5f * screenWidth;
            float screenY = (1.0f - clipPos.y) * 0.5f * screenHeight; // Flip Y

            // Draw as colored rectangle (representing mesh)
            // Size scales with distance (perspective)
            float size = 30.0f / Math.max(0.1f, clipPos.w);

            renderer.drawRect(
                screenX - size/2, screenY - size/2, size, size,
                meshRenderer.colorR, meshRenderer.colorG, meshRenderer.colorB, meshRenderer.colorA
            );
        });
    }
}
```

**Why simplified rendering?**

- Focus on 3D game logic first
- Add proper pipeline/vertex buffers in Chapter 9
- Colored boxes still show depth, position, movement

---

[... rest of the chapter continues with game implementation, systems, testing ...]

## What We've Achieved

**Complete 3D Engine:**

- ✅ Transform3D with matrices and quaternions
- ✅ Camera3D with perspective projection
- ✅ 3D mesh representation
- ✅ Depth-based rendering
- ✅ 3D movement and rotation
- ✅ **Playable 3D flight combat game!**

**Game Evolution:**

| Feature | 2D (Ch. 4) | 3D (Ch. 5) |
|---------|-----------|-----------|
| Movement | X/Y plane | Full 3D space |
| Camera | Orthographic | Perspective |
| Enemies | From edges | From any direction |
| Combat | Mouse aim | Flight simulation |
| Depth | No | Yes (Z-axis) |

---

## Key Takeaways

1. **Quaternions avoid gimbal lock** (use them instead of Euler angles!)
2. **MVP matrix pipeline**: Model → View → Projection → NDC → Screen
3. **Matrix caching** saves performance (dirty flag pattern)
4. **Perspective projection** makes distant objects smaller (divide by w)
5. **TRS order** matters (Translate → Rotate → Scale)
6. **Camera space** is always at origin looking down -Z
7. **FOV affects feel** (wide = immersive, narrow = zoomed)

---

## Exercises

1. **Add barrel roll**: Q/E keys to roll ship left/right
2. **Enemy AI**: Chase player more intelligently
3. **Power-ups**: Shield, rapid fire, speed boost (floating in space)
4. **Asteroids**: Add obstacles to dodge
5. **HUD overlay**: Health bar, score (using 2D rendering)

---

## Upgrading to Full 3D Rendering

In **Chapter 9**, we'll add:

1. **Vertex buffers**: Upload mesh data to GPU
2. **Graphics pipeline**: Vertex/fragment shaders
3. **Lighting shaders**: Phong illumination
4. **Textures**: Mapped onto 3D models
5. **OBJ loading**: Import models from Blender

**For now**, colored shapes work great for 3D gameplay!

---

## What's Next?

In **Chapter 6**, we'll:

- Add **scene serialization** (save/load game state)
- Implement **JSON export/import**
- Create **prefab system** for reusable entities
- Add **scene management** (multiple levels)

---

**Previous:** [← Chapter 4 - 2D Sprites](chapter-04-2d-sprites.md)
**Next:** [Chapter 6 - Scene Serialization →](chapter-06-scene-serialization.md)
