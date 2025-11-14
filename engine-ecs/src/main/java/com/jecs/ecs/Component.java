package com.jecs.ecs;

/**
 * Marker interface for all components in the ECS system.
 *
 * Components are pure data containers with no behavior. They hold the state
 * that entities possess, while systems provide the behavior that operates on
 * components.
 *
 * Example component:
 * <pre>
 * public class Position implements Component {
 *     public float x, y, z;
 *
 *     public Position(float x, float y, float z) {
 *         this.x = x;
 *         this.y = y;
 *         this.z = z;
 *     }
 * }
 * </pre>
 *
 * Best practices:
 * - Keep components simple and data-focused
 * - Avoid methods beyond getters/setters
 * - Use public fields for performance (data-oriented design)
 * - Prefer small, focused components over large "god" components
 *
 * @author JECS Engine
 * @version 1.0
 */
public interface Component {
    // Marker interface - no methods required
}
