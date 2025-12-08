package com.jecs.engine;

import com.jecs.components.Component;

import java.util.ArrayList;
import java.util.List;

public class Entity {

    private static int ID_COUNTER = 0;
    private int uid = -1;
    private final String name;
    private final List<Component> components;
    public Transform transform;
    private final int zIndex;
    private boolean serialize = true;

    public Entity(String name, Transform transform, int zIndex) {
        this.name = name;
        this.zIndex = zIndex;
        this.components = new ArrayList<>();
        this.transform = transform;
        this.uid = ID_COUNTER++;
    }

    public <T extends Component> T getComponent(Class<T> componentClass) {
        for (Component c : components) {
            if (componentClass.isAssignableFrom(c.getClass())) {
                try {
                    return componentClass.cast(c);
                } catch (ClassCastException e) {
                    e.printStackTrace();
                    assert false : "Error: Casting component";
                }
            }
        }
        return null;
    }

    public <T extends Component> void removeComponent(Class<T> componentClass) {
        for (int i = 0; i < components.size(); i++) {
            Component c = components.get(i);
            if (componentClass.isAssignableFrom(c.getClass())) {
                components.remove(i);
                return;
            }
        }
    }

    public void addComponent(Component c) {
        c.generateId();
        this.components.add(c);
        c.entity = this;
    }

    public void update(float dt) {
        for (Component component : components) {
            component.update(dt);
        }
    }

    public void start() {
        for (Component component : components) {
            component.start();
        }
    }

    public String getName() {
        return name;
    }

    public int zIndex() {
        return zIndex;
    }

    public void imgui() {
        for (Component c : components) {
            c.imgui();
        }
    }

    public static void init(int maxId) {
        ID_COUNTER = maxId;
    }

    public int getUid() {
        return this.uid;
    }

    public List<Component> getAllComponents() {
        return this.components;
    }

    public void setNoSerialize() {
        this.serialize = false;
    }

    public boolean shouldSerialize() {
        return this.serialize;
    }
}
