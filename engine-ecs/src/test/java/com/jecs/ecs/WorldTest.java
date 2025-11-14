package com.jecs.ecs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the World class.
 */
class WorldTest {

    private World world;

    // Test components
    private static class Position implements Component {
        float x, y, z;

        Position(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static class Velocity implements Component {
        float dx, dy, dz;

        Velocity(float dx, float dy, float dz) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
        }
    }

    private static class Health implements Component {
        int hp;

        Health(int hp) {
            this.hp = hp;
        }
    }

    @BeforeEach
    void setUp() {
        world = new World();
    }

    // ==================== Entity Tests ====================

    @Test
    void testCreateEntity() {
        Entity entity = world.createEntity();

        assertNotNull(entity);
        assertTrue(entity.isValid());
        assertTrue(world.isAlive(entity));
        assertEquals(1, world.getEntityCount());
    }

    @Test
    void testCreateMultipleEntities() {
        Entity e1 = world.createEntity();
        Entity e2 = world.createEntity();
        Entity e3 = world.createEntity();

        assertTrue(world.isAlive(e1));
        assertTrue(world.isAlive(e2));
        assertTrue(world.isAlive(e3));
        assertEquals(3, world.getEntityCount());

        // Entities should have different IDs
        assertNotEquals(e1.getId(), e2.getId());
        assertNotEquals(e2.getId(), e3.getId());
    }

    @Test
    void testDestroyEntity() {
        Entity entity = world.createEntity();
        assertTrue(world.isAlive(entity));

        boolean destroyed = world.destroyEntity(entity);

        assertTrue(destroyed);
        assertFalse(world.isAlive(entity));
        assertEquals(0, world.getEntityCount());
    }

    @Test
    void testDestroyDeadEntity() {
        Entity entity = world.createEntity();
        world.destroyEntity(entity);

        // Destroying again should return false
        boolean destroyed = world.destroyEntity(entity);

        assertFalse(destroyed);
    }

    @Test
    void testEntityIdRecycling() {
        Entity e1 = world.createEntity();
        int originalId = e1.getId();
        int originalGen = e1.getGeneration();

        world.destroyEntity(e1);

        Entity e2 = world.createEntity();

        // Should reuse the same ID
        assertEquals(originalId, e2.getId());
        // But generation should be incremented
        assertEquals(originalGen + 1, e2.getGeneration());
    }

    @Test
    void testNullEntity() {
        assertFalse(Entity.NULL.isValid());
        assertFalse(world.isAlive(Entity.NULL));
    }

    // ==================== Component Tests ====================

    @Test
    void testAddComponent() {
        Entity entity = world.createEntity();
        Position pos = new Position(1, 2, 3);

        world.addComponent(entity, pos);

        assertTrue(world.hasComponent(entity, Position.class));
        assertEquals(pos, world.getComponent(entity, Position.class));
    }

    @Test
    void testAddMultipleComponents() {
        Entity entity = world.createEntity();

        world.addComponent(entity, new Position(1, 2, 3));
        world.addComponent(entity, new Velocity(0.5f, 0, 0));
        world.addComponent(entity, new Health(100));

        assertTrue(world.hasComponent(entity, Position.class));
        assertTrue(world.hasComponent(entity, Velocity.class));
        assertTrue(world.hasComponent(entity, Health.class));
    }

    @Test
    void testRemoveComponent() {
        Entity entity = world.createEntity();
        Position pos = new Position(1, 2, 3);
        world.addComponent(entity, pos);

        Position removed = world.removeComponent(entity, Position.class);

        assertEquals(pos, removed);
        assertFalse(world.hasComponent(entity, Position.class));
    }

    @Test
    void testGetComponent() {
        Entity entity = world.createEntity();
        Position pos = new Position(5, 10, 15);
        world.addComponent(entity, pos);

        Position retrieved = world.getComponent(entity, Position.class);

        assertNotNull(retrieved);
        assertEquals(5, retrieved.x);
        assertEquals(10, retrieved.y);
        assertEquals(15, retrieved.z);
    }

    @Test
    void testGetNonExistentComponent() {
        Entity entity = world.createEntity();

        Position pos = world.getComponent(entity, Position.class);

        assertNull(pos);
    }

    @Test
    void testAddComponentToDeadEntity() {
        Entity entity = world.createEntity();
        world.destroyEntity(entity);

        assertThrows(IllegalStateException.class, () -> {
            world.addComponent(entity, new Position(0, 0, 0));
        });
    }

    @Test
    void testComponentOverwrite() {
        Entity entity = world.createEntity();
        world.addComponent(entity, new Position(1, 1, 1));
        world.addComponent(entity, new Position(2, 2, 2));

        Position pos = world.getComponent(entity, Position.class);

        assertEquals(2, pos.x);
        assertEquals(2, pos.y);
        assertEquals(2, pos.z);
    }

    @Test
    void testDestroyEntityRemovesComponents() {
        Entity entity = world.createEntity();
        world.addComponent(entity, new Position(0, 0, 0));
        world.addComponent(entity, new Velocity(1, 1, 1));

        world.destroyEntity(entity);

        // Components should be gone
        Entity newEntity = world.createEntity(); // Might reuse ID
        assertFalse(world.hasComponent(newEntity, Position.class));
        assertFalse(world.hasComponent(newEntity, Velocity.class));
    }

    // ==================== Query Tests ====================

    @Test
    void testQueryNoFilter() {
        Entity e1 = world.createEntity();
        Entity e2 = world.createEntity();

        List<Entity> result = world.query();

        assertEquals(2, result.size());
        assertTrue(result.contains(e1));
        assertTrue(result.contains(e2));
    }

    @Test
    void testQuerySingleComponent() {
        Entity e1 = world.createEntity();
        world.addComponent(e1, new Position(0, 0, 0));

        Entity e2 = world.createEntity();
        world.addComponent(e2, new Position(1, 1, 1));

        Entity e3 = world.createEntity();
        world.addComponent(e3, new Velocity(1, 0, 0));

        List<Entity> result = world.query(Position.class);

        assertEquals(2, result.size());
        assertTrue(result.contains(e1));
        assertTrue(result.contains(e2));
        assertFalse(result.contains(e3));
    }

    @Test
    void testQueryMultipleComponents() {
        Entity e1 = world.createEntity();
        world.addComponent(e1, new Position(0, 0, 0));
        world.addComponent(e1, new Velocity(1, 0, 0));

        Entity e2 = world.createEntity();
        world.addComponent(e2, new Position(1, 1, 1));

        Entity e3 = world.createEntity();
        world.addComponent(e3, new Velocity(0, 1, 0));

        List<Entity> result = world.query(Position.class, Velocity.class);

        assertEquals(1, result.size());
        assertTrue(result.contains(e1));
    }

    @Test
    void testQueryEmptyResult() {
        world.createEntity();
        world.createEntity();

        List<Entity> result = world.query(Position.class);

        assertEquals(0, result.size());
    }

    // ==================== System Tests ====================

    private static class TestSystem implements System {
        int updateCount = 0;
        boolean initialized = false;
        boolean cleanedUp = false;

        @Override
        public void update(World world, float deltaTime) {
            updateCount++;
        }

        @Override
        public void init(World world) {
            initialized = true;
        }

        @Override
        public void cleanup(World world) {
            cleanedUp = true;
        }
    }

    @Test
    void testAddSystem() {
        TestSystem system = new TestSystem();

        world.addSystem(system);

        assertTrue(system.initialized);
    }

    @Test
    void testRemoveSystem() {
        TestSystem system = new TestSystem();
        world.addSystem(system);

        boolean removed = world.removeSystem(system);

        assertTrue(removed);
        assertTrue(system.cleanedUp);
    }

    @Test
    void testUpdateSystems() {
        TestSystem system1 = new TestSystem();
        TestSystem system2 = new TestSystem();

        world.addSystem(system1);
        world.addSystem(system2);

        world.update(0.016f);
        world.update(0.016f);

        assertEquals(2, system1.updateCount);
        assertEquals(2, system2.updateCount);
    }

    // ==================== Integration Tests ====================

    @Test
    void testFullWorkflow() {
        // Create entities
        Entity player = world.createEntity();
        world.addComponent(player, new Position(0, 0, 0));
        world.addComponent(player, new Velocity(1, 0, 0));
        world.addComponent(player, new Health(100));

        Entity enemy = world.createEntity();
        world.addComponent(enemy, new Position(10, 0, 0));
        world.addComponent(enemy, new Health(50));

        // Query
        List<Entity> movableEntities = world.query(Position.class, Velocity.class);
        assertEquals(1, movableEntities.size());

        List<Entity> healthyEntities = world.query(Health.class);
        assertEquals(2, healthyEntities.size());

        // Modify components
        Position playerPos = world.getComponent(player, Position.class);
        Velocity playerVel = world.getComponent(player, Velocity.class);
        playerPos.x += playerVel.dx;

        assertEquals(1, playerPos.x);

        // Remove component
        world.removeComponent(player, Velocity.class);
        movableEntities = world.query(Position.class, Velocity.class);
        assertEquals(0, movableEntities.size());

        // Destroy entity
        world.destroyEntity(enemy);
        healthyEntities = world.query(Health.class);
        assertEquals(1, healthyEntities.size());
    }

    @Test
    void testClear() {
        Entity e1 = world.createEntity();
        world.addComponent(e1, new Position(0, 0, 0));

        TestSystem system = new TestSystem();
        world.addSystem(system);

        world.clear();

        assertEquals(0, world.getEntityCount());
        assertTrue(system.cleanedUp);
    }
}
