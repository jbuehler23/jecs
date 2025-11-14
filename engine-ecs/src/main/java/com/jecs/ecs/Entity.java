package com.jecs.ecs;

import java.util.Objects;

/**
 * Represents an entity in the ECS system.
 *
 * An entity is simply a unique identifier that groups related components together.
 * Entities themselves contain no data or behavior - they are just IDs.
 *
 * Implementation uses a simple integer ID with generation counter for recycling.
 *
 * @author JECS Engine
 * @version 1.0
 */
public final class Entity {

    /**
     * Special value representing a null/invalid entity.
     */
    public static final Entity NULL = new Entity(-1, 0);

    private final int id;
    private final int generation;

    /**
     * Create a new entity with the given ID and generation.
     *
     * @param id the unique entity ID
     * @param generation the generation counter (for ID recycling)
     */
    Entity(int id, int generation) {
        this.id = id;
        this.generation = generation;
    }

    /**
     * Get the entity's unique ID.
     *
     * @return the entity ID
     */
    public int getId() {
        return id;
    }

    /**
     * Get the entity's generation counter.
     *
     * The generation counter is incremented each time an entity ID is recycled,
     * allowing detection of stale entity references.
     *
     * @return the generation counter
     */
    public int getGeneration() {
        return generation;
    }

    /**
     * Check if this entity is valid (not NULL).
     *
     * @return true if this entity is valid
     */
    public boolean isValid() {
        return this.id >= 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Entity)) return false;
        Entity other = (Entity) obj;
        return this.id == other.id && this.generation == other.generation;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, generation);
    }

    @Override
    public String toString() {
        if (!isValid()) {
            return "Entity.NULL";
        }
        return "Entity{id=" + id + ", gen=" + generation + "}";
    }
}
