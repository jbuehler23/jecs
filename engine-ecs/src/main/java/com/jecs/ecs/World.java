package com.jecs.ecs;

import java.util.*;

/**
 * The World manages all entities, components, and systems in the ECS architecture.
 *
 * Features:
 * - Entity creation and destruction with ID recycling
 * - Component storage using sparse sets for cache-friendly iteration
 * - Component queries for systems
 * - System registration and execution
 *
 * Example usage:
 * <pre>
 * World world = new World();
 *
 * // Create entity
 * Entity player = world.createEntity();
 * world.addComponent(player, new Position(0, 0, 0));
 * world.addComponent(player, new Velocity(1, 0, 0));
 *
 * // Add system
 * world.addSystem(new MovementSystem());
 *
 * // Update each frame
 * world.update(deltaTime);
 * </pre>
 *
 * @author JECS Engine
 * @version 1.0
 */
public class World {

    // Entity management
    private int nextEntityId = 0;
    private final Queue<Integer> freeIds = new LinkedList<>();
    private final Map<Integer, Integer> entityGenerations = new HashMap<>();
    private final Set<Entity> aliveEntities = new HashSet<>();

    // Component storage: Map<ComponentType, Map<EntityId, Component>>
    private final Map<Class<? extends Component>, ComponentStorage<?>> componentStorages = new HashMap<>();

    // System management
    private final List<System> systems = new ArrayList<>();

    /**
     * Create a new empty world.
     */
    public World() {
    }

    // ==================== Entity Management ====================

    /**
     * Create a new entity.
     *
     * @return the newly created entity
     */
    public Entity createEntity() {
        int id;
        int generation;

        if (!freeIds.isEmpty()) {
            // Reuse freed ID
            id = freeIds.poll();
            generation = entityGenerations.get(id) + 1;
            entityGenerations.put(id, generation);
        } else {
            // Allocate new ID
            id = nextEntityId++;
            generation = 0;
            entityGenerations.put(id, generation);
        }

        Entity entity = new Entity(id, generation);
        aliveEntities.add(entity);
        return entity;
    }

    /**
     * Destroy an entity and all its components.
     *
     * @param entity the entity to destroy
     * @return true if entity was destroyed, false if it was already dead
     */
    public boolean destroyEntity(Entity entity) {
        if (!isAlive(entity)) {
            return false;
        }

        // Remove all components
        for (ComponentStorage<?> storage : componentStorages.values()) {
            storage.remove(entity.getId());
        }

        // Mark entity as dead
        aliveEntities.remove(entity);
        freeIds.offer(entity.getId());

        return true;
    }

    /**
     * Check if an entity is alive.
     *
     * @param entity the entity to check
     * @return true if entity exists and is alive
     */
    public boolean isAlive(Entity entity) {
        if (!entity.isValid()) {
            return false;
        }

        Integer currentGen = entityGenerations.get(entity.getId());
        return currentGen != null && currentGen == entity.getGeneration() && aliveEntities.contains(entity);
    }

    /**
     * Get all alive entities.
     *
     * @return unmodifiable set of all alive entities
     */
    public Set<Entity> getEntities() {
        return Collections.unmodifiableSet(aliveEntities);
    }

    /**
     * Get the number of alive entities.
     *
     * @return entity count
     */
    public int getEntityCount() {
        return aliveEntities.size();
    }

    // ==================== Component Management ====================

    /**
     * Add a component to an entity.
     *
     * @param entity the entity to add component to
     * @param component the component instance
     * @param <T> component type
     * @throws IllegalStateException if entity is not alive
     */
    public <T extends Component> void addComponent(Entity entity, T component) {
        if (!isAlive(entity)) {
            throw new IllegalStateException("Cannot add component to dead entity: " + entity);
        }

        @SuppressWarnings("unchecked")
        ComponentStorage<T> storage = (ComponentStorage<T>) componentStorages.computeIfAbsent(
            component.getClass(),
            k -> new ComponentStorage<>()
        );

        storage.add(entity.getId(), component);
    }

    /**
     * Remove a component from an entity.
     *
     * @param entity the entity to remove component from
     * @param componentType the component type to remove
     * @param <T> component type
     * @return the removed component, or null if entity didn't have it
     */
    public <T extends Component> T removeComponent(Entity entity, Class<T> componentType) {
        @SuppressWarnings("unchecked")
        ComponentStorage<T> storage = (ComponentStorage<T>) componentStorages.get(componentType);

        if (storage == null) {
            return null;
        }

        return storage.remove(entity.getId());
    }

    /**
     * Get a component from an entity.
     *
     * @param entity the entity to get component from
     * @param componentType the component type
     * @param <T> component type
     * @return the component instance, or null if entity doesn't have it
     */
    public <T extends Component> T getComponent(Entity entity, Class<T> componentType) {
        @SuppressWarnings("unchecked")
        ComponentStorage<T> storage = (ComponentStorage<T>) componentStorages.get(componentType);

        if (storage == null) {
            return null;
        }

        return storage.get(entity.getId());
    }

    /**
     * Check if an entity has a component.
     *
     * @param entity the entity to check
     * @param componentType the component type
     * @return true if entity has the component
     */
    public boolean hasComponent(Entity entity, Class<? extends Component> componentType) {
        ComponentStorage<?> storage = componentStorages.get(componentType);
        return storage != null && storage.has(entity.getId());
    }

    /**
     * Check if an entity has all specified components.
     *
     * @param entity the entity to check
     * @param componentTypes the component types to check for
     * @return true if entity has all components
     */
    @SafeVarargs
    public final boolean hasComponents(Entity entity, Class<? extends Component>... componentTypes) {
        for (Class<? extends Component> type : componentTypes) {
            if (!hasComponent(entity, type)) {
                return false;
            }
        }
        return true;
    }

    // ==================== Query System ====================

    /**
     * Query all entities that have all specified components.
     *
     * This is the primary way systems discover which entities to process.
     *
     * @param componentTypes the required component types
     * @return list of entities matching the query
     */
    @SafeVarargs
    public final List<Entity> query(Class<? extends Component>... componentTypes) {
        if (componentTypes.length == 0) {
            return new ArrayList<>(aliveEntities);
        }

        List<Entity> result = new ArrayList<>();

        for (Entity entity : aliveEntities) {
            if (hasComponents(entity, componentTypes)) {
                result.add(entity);
            }
        }

        return result;
    }

    // ==================== System Management ====================

    /**
     * Add a system to the world.
     *
     * The system's init() method will be called immediately.
     *
     * @param system the system to add
     */
    public void addSystem(System system) {
        systems.add(system);
        system.init(this);
    }

    /**
     * Remove a system from the world.
     *
     * The system's cleanup() method will be called before removal.
     *
     * @param system the system to remove
     * @return true if system was removed
     */
    public boolean removeSystem(System system) {
        if (systems.remove(system)) {
            system.cleanup(this);
            return true;
        }
        return false;
    }

    /**
     * Update all systems.
     *
     * Systems are updated in the order they were added.
     *
     * @param deltaTime time since last update (in seconds)
     */
    public void update(float deltaTime) {
        for (System system : systems) {
            system.update(this, deltaTime);
        }
    }

    /**
     * Clear the world, destroying all entities and removing all systems.
     */
    public void clear() {
        // Cleanup all systems
        for (System system : systems) {
            system.cleanup(this);
        }
        systems.clear();

        // Destroy all entities
        aliveEntities.clear();
        componentStorages.clear();
        freeIds.clear();
        entityGenerations.clear();
        nextEntityId = 0;
    }

    // ==================== Component Storage (Sparse Set) ====================

    /**
     * Internal storage for components of a specific type.
     *
     * Uses a sparse set for O(1) operations and cache-friendly iteration.
     *
     * @param <T> component type
     */
    private static class ComponentStorage<T extends Component> {
        // Sparse array: entityId -> denseIndex
        private final Map<Integer, Integer> sparse = new HashMap<>();

        // Dense array: components stored contiguously
        private final List<T> components = new ArrayList<>();

        // Dense array: entity IDs corresponding to components
        private final List<Integer> entities = new ArrayList<>();

        void add(int entityId, T component) {
            if (sparse.containsKey(entityId)) {
                // Update existing component
                int denseIndex = sparse.get(entityId);
                components.set(denseIndex, component);
            } else {
                // Add new component
                int denseIndex = components.size();
                sparse.put(entityId, denseIndex);
                components.add(component);
                entities.add(entityId);
            }
        }

        T remove(int entityId) {
            if (!sparse.containsKey(entityId)) {
                return null;
            }

            int denseIndex = sparse.get(entityId);
            T removed = components.get(denseIndex);

            // Swap with last element
            int lastIndex = components.size() - 1;
            if (denseIndex != lastIndex) {
                T lastComponent = components.get(lastIndex);
                int lastEntityId = entities.get(lastIndex);

                components.set(denseIndex, lastComponent);
                entities.set(denseIndex, lastEntityId);
                sparse.put(lastEntityId, denseIndex);
            }

            // Remove last element
            components.remove(lastIndex);
            entities.remove(lastIndex);
            sparse.remove(entityId);

            return removed;
        }

        T get(int entityId) {
            Integer denseIndex = sparse.get(entityId);
            return denseIndex != null ? components.get(denseIndex) : null;
        }

        boolean has(int entityId) {
            return sparse.containsKey(entityId);
        }
    }
}
