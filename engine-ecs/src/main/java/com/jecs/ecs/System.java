package com.jecs.ecs;

/**
 * Interface for all systems in the ECS architecture.
 *
 * Systems contain the behavior/logic that operates on entities with specific
 * components. They query the world for entities matching their requirements,
 * then process those entities each frame.
 *
 * Example system:
 * <pre>
 * public class MovementSystem implements System {
 *     {@literal @}Override
 *     public void update(World world, float deltaTime) {
 *         // Query all entities with Position and Velocity components
 *         for (Entity entity : world.query(Position.class, Velocity.class)) {
 *             Position pos = world.getComponent(entity, Position.class);
 *             Velocity vel = world.getComponent(entity, Velocity.class);
 *
 *             // Update position based on velocity
 *             pos.x += vel.x * deltaTime;
 *             pos.y += vel.y * deltaTime;
 *             pos.z += vel.z * deltaTime;
 *         }
 *     }
 * }
 * </pre>
 *
 * Best practices:
 * - Each system should have a single, focused responsibility
 * - Systems should be stateless when possible (query world each frame)
 * - Avoid system-to-system dependencies
 * - Use fixed update for physics, regular update for rendering
 *
 * @author JECS Engine
 * @version 1.0
 */
public interface System {

    /**
     * Update this system for the current frame.
     *
     * @param world the world containing all entities and components
     * @param deltaTime time since last update (in seconds)
     */
    void update(World world, float deltaTime);

    /**
     * Optional initialization method called when system is added to world.
     *
     * @param world the world this system belongs to
     */
    default void init(World world) {
        // Override if initialization is needed
    }

    /**
     * Optional cleanup method called when system is removed from world.
     *
     * @param world the world this system belonged to
     */
    default void cleanup(World world) {
        // Override if cleanup is needed
    }
}
