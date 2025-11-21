package com.jecs.engine;

public abstract class Component {

    public Entity entity = null;

    public abstract void update(float dt);

    public void start() {
        //NOOP
    }
}
